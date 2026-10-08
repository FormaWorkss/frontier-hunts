package com.formaworks.frontierhunts.optics.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.client.ExpeditionOptics;
import com.formaworks.frontierhunts.client.HuntShaderCompat;
import com.formaworks.frontierhunts.optics.Glassing;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * [1.1.0] The client half of glassing: tells the server where the optic points (twice a second while it is up), keeps
 * the far animals it reports as stand-ins - real models of their kind, with their own coats and antlers, moving
 * smoothly between reports - and draws them only while you look through the optic, so the field of view comes alive
 * out to 640 blocks over the Distant Horizons terrain. The rangefinder ranges them, and past the loaded land it ranges
 * the far terrain itself.
 */
@EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT)
public final class GlassingClient {
   private GlassingClient() {
   }

   static final class Imp {
      final Entity e;
      Vec3 from, to;
      float yawFrom, yawTo, speed;
      long at;

      Imp(Entity e, Vec3 p, float yaw) {
         this.e = e;
         this.from = p;
         this.to = p;
         this.yawFrom = yaw;
         this.yawTo = yaw;
      }
   }

   static final Map<Integer, Imp> IMPS = new HashMap<>();
   static final Set<EntityType<?>> BROKEN = new HashSet<>();
   private static boolean wasOn;
   private static long lastRangeTick;
   private static double lastRange = Double.NaN;

   static {
      Glassing.Net.RECEIVER = GlassingClient::accept;
   }

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post e) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) {
         IMPS.clear();
         return;
      }
      boolean on = ExpeditionOptics.active();
      if (on && (!wasOn || mc.player.tickCount % 10 == 0)) {
         PacketDistributor.sendToServer(new Glassing.Glass(true, mc.player.getYRot(), mc.player.getXRot(), 8.0F));
      } else if (!on && wasOn) {
         PacketDistributor.sendToServer(new Glassing.Glass(false, 0.0F, 0.0F, 1.0F));
      }
      wasOn = on;
      long now = mc.level.getGameTime();
      for (Iterator<Imp> it = IMPS.values().iterator(); it.hasNext(); ) {
         Imp imp = it.next();
         if (now - imp.at > 60 || !on && now - imp.at > 20) {
            it.remove();
            continue;
         }
         imp.e.tickCount++;
         if (imp.e instanceof LivingEntity le) {
            le.walkAnimation.update(Math.min(1.0F, imp.speed * 4.0F), 0.4F);
         }
      }
   }

   @SubscribeEvent
   public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
      IMPS.clear();
      wasOn = false;
   }

   static void accept(Glassing.Far far) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) {
         return;
      }
      long now = level.getGameTime();
      Set<Integer> fresh = new HashSet<>();
      for (Glassing.Seen s : far.seen()) {
         if (level.getEntity(s.id()) != null) {
            IMPS.remove(s.id()); // already near enough to be a real entity on this client
            continue;
         }
         EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.byId(s.type());
         if (type == null || BROKEN.contains(type)) {
            continue;
         }
         Vec3 p = new Vec3(s.x(), s.y(), s.z());
         Imp imp = IMPS.get(s.id());
         if (imp == null || imp.e.getType() != type) {
            Entity e;
            try {
               e = type.create(level);
            } catch (RuntimeException ex) {
               BROKEN.add(type);
               continue;
            }
            if (e == null) {
               continue;
            }
            imp = new Imp(e, p, s.yaw());
            IMPS.put(s.id(), imp);
         } else {
            imp.from = current(imp, now, 0.0F);
            imp.yawFrom = yaw(imp, now, 0.0F);
            imp.to = p;
            imp.yawTo = s.yaw();
         }
         imp.speed = s.speed();
         imp.at = now;
         try {
            if (!s.data().isEmpty()) {
               imp.e.getEntityData().assignValues(s.data());
            }
         } catch (RuntimeException ex) {
            // looks the default
         }
         fresh.add(s.id());
      }
   }

   static Vec3 current(Imp imp, long now, float pt) {
      double t = Mth.clamp((now - imp.at + pt) / 10.0, 0.0, 1.0);
      return imp.from.lerp(imp.to, t);
   }

   static float yaw(Imp imp, long now, float pt) {
      float t = (float)Mth.clamp((now - imp.at + pt) / 10.0, 0.0, 1.0);
      return Mth.rotLerp(t, imp.yawFrom, imp.yawTo);
   }

   @SubscribeEvent
   public static void render(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || IMPS.isEmpty() || HuntShaderCompat.shadowPass()
         || !ExpeditionOptics.active()) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) {
         return;
      }
      float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
      long now = level.getGameTime();
      Vec3 cam = event.getCamera().getPosition();
      PoseStack ps = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      for (Imp imp : IMPS.values()) {
         Entity e = imp.e;
         if (BROKEN.contains(e.getType())) {
            continue;
         }
         Vec3 p = current(imp, now, pt);
         float yaw = yaw(imp, now, pt);
         e.setPos(p);
         e.xo = p.x;
         e.yo = p.y;
         e.zo = p.z;
         e.setYRot(yaw);
         e.yRotO = yaw;
         if (e instanceof LivingEntity le) {
            le.yBodyRot = yaw;
            le.yBodyRotO = yaw;
            le.yHeadRot = yaw;
            le.yHeadRotO = yaw;
         }
         AABB box = e.getBoundingBox();
         if (!event.getFrustum().isVisible(box)) {
            continue;
         }
         BlockPos bp = BlockPos.containing(p.x, p.y + 0.5, p.z);
         int light = level.hasChunkAt(bp) ? LevelRenderer.getLightColor(level, bp) : LightTexture.pack(0, 15);
         ps.pushPose();
         ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
         try {
            mc.getEntityRenderDispatcher().getRenderer(e).render(e, yaw, pt, ps, buffers, light);
         } catch (RuntimeException ex) {
            BROKEN.add(e.getType());
         }
         ps.popPose();
      }
      buffers.endBatch();
   }

   /** the rangefinder past the loaded land: a far animal in the beam, else the Distant Horizons terrain (4 times a second) */
   public static Optional<Double> farRange() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) {
         return Optional.empty();
      }
      Vec3 eye = mc.player.getEyePosition();
      Vec3 dir = mc.player.getLookAngle();
      Vec3 end = eye.add(dir.scale(Glassing.RANGE));
      double best = Double.MAX_VALUE;
      long now = mc.level.getGameTime();
      for (Imp imp : IMPS.values()) {
         Vec3 p = current(imp, now, 0.0F);
         AABB box = imp.e.getType().getDimensions().makeBoundingBox(p).inflate(0.4);
         Optional<Vec3> hit = box.clip(eye, end);
         if (hit.isPresent()) {
            best = Math.min(best, hit.get().distanceTo(eye));
         }
      }
      if (best < Double.MAX_VALUE) {
         return Optional.of(best);
      }
      if (now - lastRangeTick >= 5 || now < lastRangeTick) {
         lastRangeTick = now;
         lastRange = DhRange.range(eye, dir, 4096);
      }
      return Double.isNaN(lastRange) ? Optional.empty() : Optional.of(lastRange);
   }
}
