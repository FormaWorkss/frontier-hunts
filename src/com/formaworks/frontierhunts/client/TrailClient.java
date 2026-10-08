package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.tracking.BloodTrail;
import com.formaworks.frontierhunts.tracking.PrintKind;
import com.formaworks.frontierhunts.tracking.TrailMark;
import com.formaworks.frontierhunts.tracking.TrailNetwork;
import com.formaworks.frontierhunts.tracking.TrailReading;
import com.formaworks.frontierhunts.tracking.TrailService;
import com.formaworks.frontierhunts.tracking.TrailSurfaces;
import com.formaworks.frontierhunts.tracking.hound.HoundContent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

/**
 * Client side of the readable trail. [tracking] Marks arrive incrementally; prints are drawn as soft relief decals
 * (a shadow and a lit-rim layer tinted by the ground: blue-grey in snow, dark in mud ...) that crossfade crisp ->
 * softened -> filled as they age; blood is tinted by wound type (bright heart red, frothy pink lungs, dark liver, gut
 * with stomach matter) and dries from wet red to brown.
 */
@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class TrailClient {
   private static final ResourceLocation BLOOD = FrontierHunts.id("textures/entity/blood_trail_v4.png");
   private static final ResourceLocation PRINTS = FrontierHunts.id("textures/entity/track_prints.png");
   /** prints are drawn a little larger than life so they read at Minecraft scale */
   private static final float READ = 1.35F;
   private static final float PCELL_U = 1.0F / 8.0F, PCELL_V = 1.0F / 16.0F;
   private static final float BCELL_U = 0.25F, BCELL_V = 0.2F;
   private static String dimension = "";
   private static final Map<UUID, TrailMark> MARKS = new LinkedHashMap<>();
   private static List<TrailMark> marks = List.of();
   private static long lastRequest = -100L;
   private static TrailMark inspected;
   private static long inspectedUntil;

   /** legacy full snapshot (no longer sent by the server) */
   public static void receive(TrailNetwork.Snapshot var0) {
      delta(new TrailNetwork.Delta(var0.dimension(), true, var0.marks(), List.of()));
   }

   public static void delta(TrailNetwork.Delta d) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || !mc.level.dimension().location().toString().equals(d.dimension())) {
         return;
      }
      if (d.reset() || !dimension.equals(d.dimension())) {
         MARKS.clear();
      }
      dimension = d.dimension();
      for (UUID id : d.removed()) {
         MARKS.remove(id);
      }
      for (TrailMark m : d.marks()) {
         MARKS.put(m.id(), m);
      }
      marks = List.copyOf(MARKS.values());
      PassageField.rebuild(marks, mc.level.getGameTime()); // [1.1.6] bent grass along animal paths
      if (inspected != null && !MARKS.containsKey(inspected.id())) {
         inspected = null;
      }
   }

   public static void confirm(TrailNetwork.Confirmed var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level != null) {
         inspected = MARKS.get(var0.id());
         inspectedUntil = var1.level.getGameTime() + 160L;
      }
   }

   public static List<TrailMark> visible() {
      return marks;
   }

   public static void clear() {
      dimension = "";
      MARKS.clear();
      marks = List.of();
      lastRequest = -100L;
      inspected = null;
      PassageField.clear();
   }

   @SubscribeEvent
   public static void logout(LoggingOut var0) {
      clear();
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      ClientLevel var1 = Minecraft.getInstance().level;
      if (var1 == null || !dimension.isEmpty() && !dimension.equals(var1.dimension().location().toString())) {
         clear();
      } else if (var1.getGameTime() % 100L == 0L && !marks.isEmpty()) {
         PassageField.rebuild(marks, var1.getGameTime()); // [1.1.6] grass slowly stands back up
      }
   }

   public static TrailMark target() {
      Minecraft var0 = Minecraft.getInstance();
      if (var0.level != null && var0.player != null && var0.screen == null && !var0.player.isSpectator()) {
         TrailMark best = null;
         double bd = Double.MAX_VALUE;
         Vec3 eye = var0.player.getEyePosition();
         for (TrailMark var2 : marks) {
            if (!var2.expired(var0.level.getGameTime()) && TrailService.aimedAt(var0.player, var2)) {
               double d = var2.position().distanceToSqr(eye);
               // blood wins over a print it lies on
               if (var2.blood()) {
                  d -= 0.05;
               }
               if (d < bd) {
                  bd = d;
                  best = var2;
               }
            }
         }
         return best;
      }
      return null;
   }

   private static boolean holdingLead() {
      Minecraft mc = Minecraft.getInstance();
      return mc.player != null
         && (mc.player.getMainHandItem().is(HoundContent.LEAD.get()) || mc.player.getOffhandItem().is(HoundContent.LEAD.get()));
   }

   @SubscribeEvent
   public static void use(InteractionKeyMappingTriggered var0) {
      if (var0.isUseItem()) {
         Minecraft var1 = Minecraft.getInstance();
         TrailMark var2 = target();
         if (var2 != null) {
            var0.setCanceled(true);
            var0.setSwingHand(false);
            if (var0.getHand() == InteractionHand.MAIN_HAND && var1.level.getGameTime() - lastRequest >= 5L) {
               lastRequest = var1.level.getGameTime();
               if (holdingLead()) {
                  PacketDistributor.sendToServer(new TrailNetwork.Track(var2.id()), new CustomPacketPayload[0]);
               }
               PacketDistributor.sendToServer(new TrailNetwork.Inspect(var2.id()), new CustomPacketPayload[0]);
            }
         }
      }
   }

   public static void hud(GuiGraphics var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level != null && var1.player != null && var1.screen == null && !var1.options.hideGui) {
         long now = var1.level.getGameTime();
         if (inspected != null && now < inspectedUntil && !inspected.expired(now)) {
            boolean skilled = FrontierClient.assignments != null && (FrontierClient.assignments.skills() & 4) != 0;
            String[] var15 = TrailReading.lines(inspected, now, var1.level.getDayTime(), skilled);
            int var7 = Math.min(360, var0.guiWidth() - 20);
            int var8 = (var0.guiWidth() - var7) / 2;
            int var9 = var0.guiHeight() - 104;
            var0.fill(var8, var9, var8 + var7, var9 + 48, -15326946);
            var0.fill(var8, var9, var8 + 2, var9 + 48, inspected.blood() ? -4051405 : -2508680);

            for (int var10 = 0; var10 < var15.length; var10++) {
               var0.drawString(
                  var1.font, var1.font.plainSubstrByWidth(var15[var10], var7 - 18), var8 + 9, var9 + 7 + var10 * 13, var10 == 0 ? -1783154 : -1448747, false
               );
            }
         } else {
            TrailMark var2 = target();
            if (var2 != null) {
               String var3 = var1.options.keyUse.getTranslatedKeyMessage().getString()
                  + (holdingLead() && !var2.animal().equals(TrailMark.UNKNOWN) ? " · Put the hound on this " + var2.kind() : " · Inspect " + var2.kind());
               int var4 = var1.font.width(var3) + 16;
               int var5 = (var0.guiWidth() - var4) / 2;
               int var6 = var0.guiHeight() / 2 + 26;
               var0.fill(var5, var6, var5 + var4, var6 + 18, -1072291554);
               var0.drawString(var1.font, var3, var5 + 8, var6 + 5, -1448747, false);
            }
         }
      }
   }

   // ------------------------------------------------------------------------------------------------ rendering

   @SubscribeEvent
   public static void render(RenderLevelStageEvent var0) {
      if (var0.getStage() != Stage.AFTER_ENTITIES) {
         return;
      }
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level == null || var1.player == null || marks.isEmpty()) {
         return;
      }
      BufferSource var2 = var1.renderBuffers().bufferSource();
      Vec3 cam = var0.getCamera().getPosition();
      PoseStack ps = var0.getPoseStack();
      long now = var1.level.getGameTime();
      List<TrailMark> shown = new ArrayList<>(marks.size());
      for (TrailMark m : marks) {
         if (TrailService.visibleTo(var1.player, m)
            && !m.expired(now)
            && m.position().distanceToSqr(cam) <= 784.0
            && var1.level.hasChunkAt(m.support())
            && !TrailSurfaces.floating(var1.level, m) // [1.1.6] its ground is gone: don't hang it in the air
            && var0.getFrustum().isVisible(new AABB(m.position(), m.position()).inflate(m.print() ? 0.9 : 0.46))) {
            shown.add(m);
         }
      }
      if (shown.isEmpty()) {
         return;
      }
      // ground relief first (prints, beds' matted grass), blood on top
      RenderType pt = RenderType.entityTranslucent(PRINTS);
      VertexConsumer pv = var2.getBuffer(pt);
      for (TrailMark m : shown) {
         if (m.print() || m.blood() && m.style() == TrailMark.BED) {
            prints(var1.level, ps, pv, cam, m, now);
         }
      }
      var2.endBatch(pt);
      RenderType bt = RenderType.entityTranslucent(BLOOD);
      VertexConsumer bv = var2.getBuffer(bt);
      for (TrailMark m : shown) {
         if (m.blood()) {
            blood(var1.level, ps, bv, cam, m, now);
         }
      }
      var2.endBatch(bt);
      // legacy clue meshes (droppings, fur ...) keep their mesh look
      RenderType mt = RenderType.entityCutoutNoCull(WhitetailRenderer.MATERIAL);
      VertexConsumer mv = var2.getBuffer(mt);
      for (TrailMark m : shown) {
         if (!m.blood() && m.style() >= 5 && !m.plantSign()) {
            int light = LevelRenderer.getLightColor(var1.level, BlockPos.containing(m.position()));
            ps.pushPose();
            ps.translate(m.position().x - cam.x, m.position().y - cam.y, m.position().z - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(180.0F - m.yaw()));
            legacy(ps, mv, light, m);
            ps.popPose();
         }
      }
      var2.endBatch(mt);
   }

   private static float distanceFade(TrailMark m, Vec3 cam) {
      double d = Math.sqrt(m.position().distanceToSqr(cam));
      return (float)Math.clamp((28.0 - d) / 5.0, 0.0, 1.0);
   }

   // ---------------------------------------------------------------- prints

   /** ground colours per surface: {shadow rgb, rim rgb, strength} */
   private static float[] ground(ClientLevel level, TrailMark m) {
      BlockState s = level.getBlockState(m.support());
      int surf = TrailSurfaces.printSurface(s);
      return switch (surf) {
         case TrailSurfaces.SNOW -> new float[]{0.60F, 0.67F, 0.82F, 1.0F, 1.0F, 1.0F, 1.0F};
         case TrailSurfaces.MUD -> new float[]{0.11F, 0.075F, 0.05F, 0.52F, 0.42F, 0.32F, 0.95F};
         case TrailSurfaces.SAND -> new float[]{0.55F, 0.47F, 0.33F, 1.0F, 0.96F, 0.84F, 0.85F};
         case TrailSurfaces.GRAVEL -> new float[]{0.24F, 0.24F, 0.23F, 0.72F, 0.72F, 0.7F, 0.55F};
         case TrailSurfaces.GRASS -> {
            int g = BiomeColors.getAverageGrassColor(level, m.support());
            float r = (g >> 16 & 255) / 255.0F, gg = (g >> 8 & 255) / 255.0F, b = (g & 255) / 255.0F;
            yield new float[]{r * 0.42F, gg * 0.42F, b * 0.42F, Math.min(1, r * 1.15F), Math.min(1, gg * 1.15F), Math.min(1, b * 1.15F), 0.6F};
         }
         default -> new float[]{0.15F, 0.10F, 0.065F, 0.58F, 0.46F, 0.34F, 0.9F};
      };
   }

   private static void prints(ClientLevel level, PoseStack ps, VertexConsumer vc, Vec3 cam, TrailMark m, long now) {
      float fade = distanceFade(m, cam);
      if (fade <= 0.0F) {
         return;
      }
      float[] g = ground(level, m);
      float f = m.wearFraction(now);
      // crisp -> softened -> filled, crossfaded, then gone
      float stage;
      float alpha;
      if (f < 0.05F) {
         stage = 0.0F;
         alpha = 1.0F;
      } else if (f < 0.25F) {
         stage = (f - 0.05F) / 0.2F;
         alpha = 1.0F;
      } else if (f < 0.6F) {
         stage = 1.0F + (f - 0.25F) / 0.35F;
         alpha = 1.0F;
      } else {
         stage = 2.0F;
         alpha = Math.max(0.0F, 1.0F - (f - 0.6F) / 0.4F);
      }
      alpha *= fade * g[6];
      if (alpha <= 0.01F) {
         return;
      }
      int light = LevelRenderer.getLightColor(level, BlockPos.containing(m.position()));
      double yaw = Math.toRadians(m.yaw());
      double fx = -Math.sin(yaw), fz = Math.cos(yaw);
      double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
      PoseStack.Pose pose = ps.last();
      double ox = m.position().x - cam.x, oy = m.position().y - cam.y, oz = m.position().z - cam.z;
      if (m.blood()) {
         // wound bed: a matted oval where the animal lay
         float len = Math.max(0.6F, m.radius() * 2.2F);
         quad(vc, pose, light, g, alpha * 0.9F, stage, PrintKind.Art.BED.ordinal(), ox, oy, oz, fx, fz, rx, rz, 0, 0, len, 0.0F, false);
         return;
      }
      PrintKind k = PrintKind.of(m.sign());
      float L = k.length * m.scale() * READ;
      float S = m.stride() > 0.05F ? Math.min(m.stride(), 4.0F) : k.stride * m.scale();
      boolean run = m.activity() == 2;
      int art = k.art.ordinal();
      long seed = m.id().getLeastSignificantBits();
      float jit = ((seed >>> 3) & 15L) / 15.0F - 0.5F;
      switch (k.layout) {
         case PAIRED -> {
            float gw = Math.max(L * 0.9F, 0.06F);
            if (!run) {
               print(vc, pose, light, g, alpha, stage, art, ox, oy, oz, fx, fz, rx, rz, -gw, -S * 0.25F, L, -0.1F + jit * 0.1F, false);
               print(vc, pose, light, g, alpha, stage, art, ox, oy, oz, fx, fz, rx, rz, gw, S * 0.25F, L, 0.1F + jit * 0.1F, true);
               // the hind foot registers a touch off the front print
               print(vc, pose, light, g, alpha * 0.55F, stage, art, ox, oy, oz, fx, fz, rx, rz, -gw + L * 0.08F, -S * 0.25F + L * 0.22F, L * 0.97F, -0.05F, false);
               print(vc, pose, light, g, alpha * 0.55F, stage, art, ox, oy, oz, fx, fz, rx, rz, gw - L * 0.06F, S * 0.25F + L * 0.2F, L * 0.97F, 0.05F, true);
            } else {
               // running / bounding group: hinds land ahead of the fronts, splayed wider
               print(vc, pose, light, g, alpha, stage, art, ox, oy, oz, fx, fz, rx, rz, -gw * 0.5F, -L * 1.4F, L * 1.1F, -0.05F, false);
               print(vc, pose, light, g, alpha, stage, art, ox, oy, oz, fx, fz, rx, rz, gw * 0.4F, -L * 2.8F, L * 1.1F, 0.08F, true);
               print(vc, pose, light, g, alpha, stage, art, ox, oy, oz, fx, fz, rx, rz, -gw * 1.4F, L * 1.3F, L * 1.15F, -0.28F, false);
               print(vc, pose, light, g, alpha, stage, art, ox, oy, oz, fx, fz, rx, rz, gw * 1.4F, L * 1.6F, L * 1.15F, 0.3F, true);
            }
         }
         case LINE -> {
            float gw = L * 0.35F;
            if (!run) {
               print(vc, pose, light, g, alpha, stage, art, ox, oy, oz, fx, fz, rx, rz, -gw, -S * 0.25F, L, -0.05F, false);
               print(vc, pose, light, g, alpha, stage, art, ox, oy, oz, fx, fz, rx, rz, gw, S * 0.25F, L, 0.05F, true);
            } else {
               for (int i = 0; i < 4; i++) {
                  float z = (i - 1.5F) * L * 1.5F;
                  print(vc, pose, light, g, alpha, stage, art, ox, oy, oz, fx, fz, rx, rz, (i % 2 == 0 ? -1 : 1) * gw * 0.8F, z, L, 0.0F, i % 2 == 1);
               }
            }
         }
         case BEAR -> {
            int front = PrintKind.Art.BEAR_FRONT.ordinal();
            float gw = L * 0.65F;
            float s = run ? S * 0.3F : S * 0.25F;
            print(vc, pose, light, g, alpha, stage, front, ox, oy, oz, fx, fz, rx, rz, -gw, -s - L * 0.36F, L * 0.62F, -0.2F, false);
            print(vc, pose, light, g, alpha, stage, art, ox, oy, oz, fx, fz, rx, rz, -gw * 0.95F, -s + L * 0.52F, L, -0.12F, false);
            print(vc, pose, light, g, alpha, stage, front, ox, oy, oz, fx, fz, rx, rz, gw, s - L * 0.36F, L * 0.62F, 0.2F, true);
            print(vc, pose, light, g, alpha, stage, art, ox, oy, oz, fx, fz, rx, rz, gw * 0.95F, s + L * 0.52F, L, 0.12F, true);
         }
         case HOP -> {
            int front = PrintKind.Art.RABBIT_FRONT.ordinal();
            print(vc, pose, light, g, alpha, stage, art, ox, oy, oz, fx, fz, rx, rz, -L * 0.42F, L * 0.55F, L, -0.1F, false);
            print(vc, pose, light, g, alpha, stage, art, ox, oy, oz, fx, fz, rx, rz, L * 0.42F, L * 0.62F, L, 0.1F, true);
            print(vc, pose, light, g, alpha, stage, front, ox, oy, oz, fx, fz, rx, rz, -L * 0.08F, -L * 0.5F, L * 0.5F, 0.0F, false);
            print(vc, pose, light, g, alpha, stage, front, ox, oy, oz, fx, fz, rx, rz, L * 0.06F, -L * 1.05F, L * 0.5F, 0.0F, true);
         }
         case BIRD -> {
            for (int i = 0; i < 4; i++) {
               float z = (i - 1.5F) * S * 0.25F;
               boolean right = i % 2 == 1;
               print(vc, pose, light, g, alpha, stage, art, ox, oy, oz, fx, fz, rx, rz, (right ? 1 : -1) * L * 0.4F, z, L, right ? -0.14F : 0.14F, right);
            }
         }
         case BIPED -> {
            float gw = 0.11F;
            print(vc, pose, light, g, alpha, stage, art, ox, oy, oz, fx, fz, rx, rz, -gw, -S * 0.25F, L / READ, -0.08F, false);
            print(vc, pose, light, g, alpha, stage, art, ox, oy, oz, fx, fz, rx, rz, gw, S * 0.25F, L / READ, 0.08F, true);
         }
      }
   }

   /** one print at mark-local (x right, z forward) with its own toe-out angle, crossfading two age stages */
   private static void print(
      VertexConsumer vc, PoseStack.Pose pose, int light, float[] g, float alpha, float stage, int art, double ox, double oy, double oz, double fx, double fz,
      double rx, double rz, float x, float z, float len, float turn, boolean mirror
   ) {
      double c = Math.cos(turn), s = Math.sin(turn);
      double pfx = fx * c + rx * s, pfz = fz * c + rz * s;
      double prx = rx * c - fx * s, prz = rz * c - fz * s;
      double cx = ox + rx * x + fx * z, cz = oz + rz * x + fz * z;
      quad(vc, pose, light, g, alpha, stage, art, cx, oy, cz, pfx, pfz, prx, prz, 0, 0, len, 0.0F, mirror);
   }

   private static void quad(
      VertexConsumer vc, PoseStack.Pose pose, int light, float[] g, float alpha, float stage, int art, double cx, double cy, double cz, double fx, double fz,
      double rx, double rz, float x, float z, float len, float unused, boolean mirror
   ) {
      // the print fills 1.6 of the cell's 2.0: the quad is 1.25x the print length
      double h = len * 0.625;
      int s0 = Math.min(2, (int)Math.floor(stage));
      float t = Math.min(1.0F, stage - s0);
      for (int layer = 0; layer < 2; layer++) {
         for (int k = 0; k < 2; k++) {
            int st = k == 0 ? s0 : Math.min(2, s0 + 1);
            float w = k == 0 ? 1.0F - t : t;
            if (w <= 0.02F || k == 1 && st == s0) {
               continue;
            }
            float a = alpha * w * (layer == 0 ? 0.92F : 0.8F);
            float r = layer == 0 ? g[0] : g[3], gg = layer == 0 ? g[1] : g[4], b = layer == 0 ? g[2] : g[5];
            float u0 = (st * 2 + layer) * PCELL_U, u1 = u0 + PCELL_U;
            if (mirror) {
               float tmp = u0;
               u0 = u1;
               u1 = tmp;
            }
            float v0 = art * PCELL_V, v1 = v0 + PCELL_V;
            double y = cy + layer * 0.0004;
            vtx(vc, pose, light, r, gg, b, a, cx - rx * h + fx * h, y, cz - rz * h + fz * h, u0, v0);
            vtx(vc, pose, light, r, gg, b, a, cx - rx * h - fx * h, y, cz - rz * h - fz * h, u0, v1);
            vtx(vc, pose, light, r, gg, b, a, cx + rx * h - fx * h, y, cz + rz * h - fz * h, u1, v1);
            vtx(vc, pose, light, r, gg, b, a, cx + rx * h + fx * h, y, cz + rz * h + fz * h, u1, v0);
         }
      }
   }

   private static void vtx(VertexConsumer vc, PoseStack.Pose pose, int light, float r, float g, float b, float a, double x, double y, double z, float u, float v) {
      vc.addVertex(pose, (float)x, (float)y, (float)z)
         .setColor(r, g, b, a)
         .setUv(u, v)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(light)
         .setNormal(pose, 0.0F, 1.0F, 0.0F);
   }

   // ---------------------------------------------------------------- blood

   private static final float[][] FRESH = {
      {0.72F, 0.06F, 0.05F}, {0.84F, 0.05F, 0.04F}, {0.92F, 0.24F, 0.26F}, {0.40F, 0.03F, 0.04F}, {0.34F, 0.07F, 0.05F}, {0.80F, 0.07F, 0.05F},
      {0.86F, 0.05F, 0.04F}
   };
   private static final float[][] DRY = {
      {0.30F, 0.12F, 0.08F}, {0.30F, 0.12F, 0.08F}, {0.42F, 0.20F, 0.17F}, {0.20F, 0.08F, 0.05F}, {0.22F, 0.10F, 0.06F}, {0.30F, 0.12F, 0.08F},
      {0.30F, 0.12F, 0.08F}
   };

   /** base cell, overlay cell (-1 none) */
   private static int[] cells(TrailMark m) {
      BloodTrail.BloodType t = BloodTrail.BloodType.of(m.sign());
      return switch (m.style()) {
         case TrailMark.IMPACT -> t == BloodTrail.BloodType.LUNG ? new int[]{5, 17} : new int[]{2, -1};
         case TrailMark.BRUSH -> switch (t) {
            case LUNG -> new int[]{13, 19};
            case HEART, ARTERIAL -> new int[]{5, -1};
            default -> new int[]{12, -1};
         };
         case TrailMark.DENSE -> switch (t) {
            case LUNG -> new int[]{6, 18};
            case GUT -> new int[]{8, 9};
            case ARTERIAL -> new int[]{14, -1};
            default -> new int[]{1, -1};
         };
         case TrailMark.POOL -> t == BloodTrail.BloodType.LUNG ? new int[]{3, 18} : new int[]{3, -1};
         case TrailMark.BED -> new int[]{7, t == BloodTrail.BloodType.GUT ? 9 : -1};
         default -> switch (t) {
            case LUNG -> new int[]{4, 16};
            case LIVER -> new int[]{10, -1};
            case GUT -> new int[]{8, 9};
            case MUSCLE -> new int[]{11, -1};
            case ARTERIAL -> new int[]{14, -1};
            default -> new int[]{0, -1};
         };
      };
   }

   private static void blood(ClientLevel level, PoseStack ps, VertexConsumer vc, Vec3 cam, TrailMark m, long now) {
      float fade = distanceFade(m, cam);
      if (fade <= 0.0F) {
         return;
      }
      int type = Math.clamp(m.sign(), 0, FRESH.length - 1);
      long age = m.age(now);
      float dry = 1.0F - (float)Math.exp(-age / 3000.0);
      // [1.1.7] blood-tracking lamp: within 20 m blood shows bright and true red, old drops included, day or night
      boolean lamp = com.formaworks.frontierhunts.outfitter.OutfitterItem.lamp(net.minecraft.client.Minecraft.getInstance().player) && m.position().distanceToSqr(cam) < 400.0;
      if (lamp) {
         dry *= 0.35F;
      }
      float[] a = FRESH[type], b = DRY[type];
      float r = a[0] + (b[0] - a[0]) * dry, g = a[1] + (b[1] - a[1]) * dry, bl = a[2] + (b[2] - a[2]) * dry;
      float f = m.wearFraction(now);
      float alpha = fade * (f < 0.7F ? 1.0F : Math.max(0.0F, 1.0F - (f - 0.7F) / 0.3F));
      if (alpha <= 0.01F) {
         return;
      }
      int light = LevelRenderer.getLightColor(level, BlockPos.containing(m.position()));
      light = FieldEntityLight.point(m.position().add(m.normal().scale(0.06)), light, true);
      if (lamp) {
         light = LightTexture.FULL_BRIGHT;
         alpha = Math.min(fade, alpha * 1.5F + 0.2F);
      }
      int[] cell = cells(m);
      ps.pushPose();
      ps.translate(m.position().x - cam.x, m.position().y - cam.y, m.position().z - cam.z);
      Vec3 n = m.normal();
      ps.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float)n.x, (float)n.y, (float)n.z));
      boolean directional = cell[0] == 11 || cell[0] == 14;
      int spin = (int)(m.id().getLeastSignificantBits() & 3L);
      if (m.face() == net.minecraft.core.Direction.UP && directional) {
         ps.mulPose(Axis.YP.rotationDegrees(180.0F - m.yaw()));
         spin = 0;
      }
      float rad = m.radius();
      float rw = m.style() == TrailMark.BRUSH ? rad * 0.64F : rad;
      bloodQuad(ps, vc, light, r, g, bl, alpha, cell[0], rw, rad, spin);
      if (cell[1] >= 0) {
         // froth pops within a couple of hours; stomach matter stays but darkens
         float oa = cell[1] == 9 ? alpha : alpha * Math.max(0.0F, 1.0F - age / 2400.0F);
         float dk = 1.0F - 0.55F * dry;
         if (oa > 0.01F) {
            ps.translate(0.0F, 0.0006F, 0.0F);
            bloodQuad(ps, vc, light, dk, dk, dk, oa, cell[1], rw, rad, spin);
         }
      }
      ps.popPose();
      onBlades(level, ps, vc, cam, m, light, r, g, bl, alpha, cell[0]);
   }

   /**
    * [1.1.8] Blood in grass doesn't only reach the soil: drops and smears also hang on the blades and stems around the
    * mark, at different heights and angles, so a trail through a meadow reads from standing height. Deterministic per
    * mark (no flicker), fewer for a pin drop, more for a heavy splash.
    */
   private static void onBlades(ClientLevel level, PoseStack ps, VertexConsumer vc, Vec3 cam, TrailMark m, int light, float r, float g, float bl,
      float alpha, int cell) {
      if (m.face() != net.minecraft.core.Direction.UP || alpha < 0.05F) {
         return;
      }
      BlockPos at = BlockPos.containing(m.position().x, m.position().y + 0.05, m.position().z);
      BlockState plant = level.getBlockState(at);
      if (!plant.is(com.formaworks.frontierhunts.tracking.TrailSurfaces.FOLIAGE) || plant.is(net.minecraft.world.level.block.Blocks.SNOW)) {
         plant = level.getBlockState(m.support());
         if (!plant.is(com.formaworks.frontierhunts.tracking.TrailSurfaces.FOLIAGE) || plant.is(net.minecraft.world.level.block.Blocks.SNOW)) {
            return;
         }
      }
      String path = plant.getBlock().builtInRegistryHolder().key().location().getPath();
      if (path.equals("forest_litter") || path.equals("forest_sticks")) {
         return;
      }
      // [1.1.9] blood soaks and splashes over the grass: smears on the canopy you see from above, and drops down the
      // blade sides; more and bigger for a heavy splash
      double top = Math.max(0.45, Math.min(1.1, com.formaworks.frontierhunts.tracking.TrailSurfaces.foliageTop(plant) * 1.1));
      long h = m.id().getLeastSignificantBits() ^ m.id().getMostSignificantBits() * 31L;
      int n = 5 + (int)Math.min(10.0F, m.radius() * 26.0F);
      for (int i = 0; i < n; i++) {
         h = h * 6364136223846793005L + 1442695040888963407L;
         double a = ((h >>> 11) & 1023) / 1023.0 * Math.PI * 2.0;
         double d = (0.1 + ((h >>> 21) & 255) / 255.0 * 0.9) * Math.max(0.16, m.radius() * 1.5);
         boolean canopy = ((h >>> 31) & 7) < 5;
         double y = canopy ? top * (0.62 + ((h >>> 29) & 255) / 255.0 * 0.38) : 0.06 + ((h >>> 29) & 255) / 255.0 * top * 0.6;
         float size = 0.045F + ((h >>> 37) & 63) / 63.0F * 0.07F;
         float tilt = canopy ? 12.0F + ((h >>> 43) & 31) : 58.0F + ((h >>> 43) & 31);
         ps.pushPose();
         ps.translate(m.position().x + Math.cos(a) * d - cam.x, m.position().y + y - cam.y, m.position().z + Math.sin(a) * d - cam.z);
         ps.mulPose(Axis.YP.rotationDegrees((float)Math.toDegrees(a) + ((h >>> 49) & 63)));
         ps.mulPose(Axis.XP.rotationDegrees(tilt));
         float fa = alpha * (canopy ? 0.95F : 0.85F);
         bloodQuad(ps, vc, light, r * 0.92F, g, bl, fa, cell, size, size * (canopy ? 1.3F : 1.9F), (int)(h >>> 60 & 3));
         ps.mulPose(Axis.XP.rotationDegrees(180.0F)); // the other face of the blade
         bloodQuad(ps, vc, light, r * 0.85F, g, bl, fa * 0.9F, cell, size, size * (canopy ? 1.3F : 1.9F), (int)(h >>> 60 & 3));
         ps.popPose();
      }
   }

   private static void bloodQuad(PoseStack ps, VertexConsumer vc, int light, float r, float g, float b, float a, int cell, float rw, float rad, int spin) {
      float cu = (cell % 4) * BCELL_U, cv = (cell / 4) * BCELL_V;
      float[][] pos = {{-rw, -rad}, {-rw, rad}, {rw, rad}, {rw, -rad}};
      float[][] uv = {{0.0F, 0.0F}, {0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, 0.0F}};
      PoseStack.Pose last = ps.last();
      for (int i = 0; i < 4; i++) {
         float[] p = pos[i];
         float[] t = uv[(i + spin) % 4];
         vc.addVertex(last.pose(), p[0], 0.0F, p[1])
            .setColor(r, g, b, a)
            .setUv(cu + t[0] * BCELL_U, cv + t[1] * BCELL_V)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(last, 0.0F, 1.0F, 0.0F);
      }
   }

   // ---------------------------------------------------------------- other clue meshes (unchanged look)

   private static final int[] SIDES = new int[]{-1, 1};

   private static void legacy(PoseStack var0, VertexConsumer var1, int var2, TrailMark var3) {
      if (var3.style() == 10) {
         HuntMesh.tube(var0, var1, var2, 5719605, -0.16, 0.006, -0.08, 0.005, 0.008, 0.025, 0.009, 0.006, 6);
         HuntMesh.tube(var0, var1, var2, 7364675, 0.028, 0.008, 0.01, 0.16, 0.006, 0.11, 0.007, 0.003, 6);
         HuntMesh.tube(var0, var1, var2, 12823160, -0.002, 0.009, 0.016, 0.013, 0.009, 0.03, 0.004, 0.001, 5);
         HuntMesh.tube(var0, var1, var2, 6640443, -0.065, 0.008, -0.025, -0.095, 0.008, 0.04, 0.004, 0.001, 5);
      } else if (var3.style() == 11) {
         long var18 = var3.effectiveAge(Minecraft.getInstance().level.getGameTime());
         int var22 = var18 < 400L ? 11184003 : (var18 < 1800L ? 8552033 : 5920582);
         for (int var25 = 0; var25 < 3; var25++) {
            HuntMesh.tube(var0, var1, var2, var22, -0.055 + (double)var25 * 0.045, 0.002, 0.09, -0.035 + (double)var25 * 0.045, 0.002, -0.075, 0.0025, 0.001, 4);
         }
      } else if (var3.style() == 5) {
         for (int var17 = 0; var17 < 7; var17++) {
            HuntMesh.ellipsoid(var0, var1, var2, 4800301, 1, Math.sin((double)var17 * 2.4) * 0.075, 0.01, Math.cos((double)var17 * 2.4) * 0.08, 0.014, 0.01, 0.025);
         }
      } else if (var3.style() == 6) {
         for (int var16 = 0; var16 < 13; var16++) {
            double var20 = Math.sin((double)var16 * 2.4) * 0.06;
            double var24 = Math.cos((double)var16 * 2.4) * 0.07;
            HuntMesh.tube(var0, var1, var2, 10193774, var20, 0.003, var24, var20 + 0.035, 0.005, var24 + 0.065, 0.0018, 5.0E-4, 5);
         }
      } else if (var3.style() == 7) {
         for (int var15 = -1; var15 <= 1; var15++) {
            HuntMesh.tube(var0, var1, var2, 7430218, (double)var15 * 0.025, 0.003, -0.12, (double)var15 * 0.028, 0.003, 0.12, 0.007, 0.003, 6);
         }
      } else if (var3.style() == 8) {
         for (int var14 = -1; var14 <= 1; var14++) {
            HuntMesh.ellipsoid(var0, var1, var2, 7496004, 1, (double)var14 * 0.035, 0.006, 0.0, 0.012, 0.006, 0.04);
         }
      } else if (var3.style() == 9) {
         for (int var23 : SIDES) {
            HuntMesh.ellipsoid(var0, var1, var2, 4669232, 1, (double)var23 * 0.085, 0.003, 0.0, 0.042, 0.003, 0.046);
            for (int var26 = 0; var26 < 4; var26++) {
               HuntMesh.ellipsoid(
                  var0, var1, var2, 4669232, 1, (double)var23 * 0.085 + ((double)var26 - 1.5) * 0.024, 0.004, -0.06 + Math.abs((double)var26 - 1.5) * 0.013, 0.011,
                  0.003, 0.015
               );
            }
         }
      }
   }

   private TrailClient() {
   }
}
