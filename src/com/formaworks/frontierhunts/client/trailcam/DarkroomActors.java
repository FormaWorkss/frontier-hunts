package com.formaworks.frontierhunts.client.trailcam;

import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.trailcam.TrailcamScene;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds the client-only stand-ins for a photo's subjects from its scene record. */
final class DarkroomActors {
   private static final Logger LOG = LoggerFactory.getLogger(DarkroomActors.class);
   private static final Map<String, Field> FIELDS = new HashMap<>();
   private static int nextId = -0x3A5C0000;

   private DarkroomActors() {
   }

   static void build(Minecraft mc, ClientLevel level, Darkroom.Job job) {
      ListTag subjects = job.scene.getList("subjects", 10);
      for (int i = 0; i < subjects.size(); i++) {
         CompoundTag s = subjects.getCompound(i);
         try {
            LivingEntity e = make(mc, level, s, job);
            if (e != null) {
               job.actors.add(e);
               job.tags.add(s);
            }
         } catch (RuntimeException ex) {
            LOG.warn("Trail camera: could not rebuild {} for a photo", s.getString("type"), ex);
         }
      }
   }

   private static LivingEntity make(Minecraft mc, ClientLevel level, CompoundTag s, Darkroom.Job job) {
      LivingEntity e;
      if (s.getByte("k") == 1 && s.contains("p", 10)) {
         e = player(mc, level, s.getCompound("p"), job);
      } else {
         Optional<EntityType<?>> type = EntityType.byString(s.getString("type"));
         Entity raw = type.isPresent() ? type.get().create(level) : null;
         if (!(raw instanceof LivingEntity le)) {
            return null;
         }
         e = le;
      }
      if (--nextId > -0x3A000000 || nextId < -0x3B000000) {
         nextId = -0x3A5C0000;
      }
      e.setId(nextId);
      applyData(level, e, s.getByteArray("data"));
      if (s.contains("eq", 9)) {
         ListTag eq = s.getList("eq", 10);
         for (int i = 0; i < eq.size(); i++) {
            CompoundTag it = eq.getCompound(i);
            try {
               EquipmentSlot slot = EquipmentSlot.byName(it.getString("s"));
               ItemStack.parse(level.registryAccess(), it.get("i")).ifPresent(stack -> e.setItemSlot(slot, stack));
            } catch (RuntimeException ex) {
               LOG.debug("Trail camera: gear not restored", ex);
            }
         }
      }
      hold(e, s);
      if (e instanceof Whitetail && s.contains("wt", 10)) {
         CompoundTag wt = s.getCompound("wt");
         setFloat(e, Whitetail.class, "travel", wt.getFloat("tr"));
         setFloat(e, Whitetail.class, "travelOld", wt.getFloat("tr"));
         setFloat(e, Whitetail.class, "motionSpeed", wt.getFloat("ms"));
         setFloat(e, Whitetail.class, "motionSpeedOld", wt.getFloat("ms"));
         setFloat(e, Whitetail.class, "grazing", wt.getFloat("gz"));
         setFloat(e, Whitetail.class, "grazingOld", wt.getFloat("gz"));
      }
      return e;
   }

   private static LivingEntity player(Minecraft mc, ClientLevel level, CompoundTag p, Darkroom.Job job) {
      UUID id = p.hasUUID("id") ? p.getUUID("id") : UUID.nameUUIDFromBytes(p.getString("name").getBytes());
      String name = p.getString("name");
      GameProfile profile = new GameProfile(id, name.isEmpty() || name.length() > 16 ? "Hunter" : name);
      if (p.contains("tex", 8)) {
         profile.getProperties().put("textures", new Property("textures", p.getString("tex"), p.contains("sig", 8) ? p.getString("sig") : null));
      }
      CompletableFuture<PlayerSkin> skin = null;
      PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(id);
      if (info == null && p.contains("tex", 8)) {
         // the photographed player is offline: fetch their skin from the recorded texture property
         skin = mc.getSkinManager().getOrLoad(profile);
         job.waits.add(skin);
      }
      return new TrailcamPlayer(level, profile, skin);
   }

   /** Synced entity data, exactly as a tracking client would receive it. Bad values are skipped one by one. */
   private static void applyData(ClientLevel level, LivingEntity e, byte[] bytes) {
      if (bytes == null || bytes.length == 0) {
         return;
      }
      RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), level.registryAccess());
      try {
         while (buf.isReadable()) {
            int id = buf.readUnsignedByte();
            if (id == 255) {
               break;
            }
            SynchedEntityData.DataValue<?> v = SynchedEntityData.DataValue.read(buf, id);
            try {
               e.getEntityData().assignValues(List.of(v));
            } catch (RuntimeException ex) {
               LOG.debug("Trail camera: entity data {} not applied to {}", id, e, ex);
            }
         }
      } catch (RuntimeException ex) {
         LOG.debug("Trail camera: entity data of {} partly unreadable", e, ex);
      } finally {
         buf.release();
      }
   }

   /** Puts a stand-in exactly at its recorded place, facing and stride (re-applied every developing frame). */
   static void hold(LivingEntity e, CompoundTag s) {
      double x = s.getDouble("x");
      double y = s.getDouble("y");
      double z = s.getDouble("z");
      float yr = s.getFloat("yr");
      float xr = s.getFloat("xr");
      e.moveTo(x, y, z, yr, xr);
      e.yBodyRot = s.getFloat("by");
      e.yBodyRotO = e.yBodyRot;
      e.yHeadRot = s.getFloat("hy");
      e.yHeadRotO = e.yHeadRot;
      e.setOnGround(s.getBoolean("g"));
      e.setDeltaMovement(s.getFloat("vx"), s.getFloat("vy"), s.getFloat("vz"));
      e.tickCount = s.getInt("age");
      WalkAnimationState walk = e.walkAnimation;
      setFloat(walk, WalkAnimationState.class, "position", s.getFloat("wp"));
      setFloat(walk, WalkAnimationState.class, "speed", s.getFloat("ws"));
      setFloat(walk, WalkAnimationState.class, "speedOld", s.getFloat("ws"));
   }

   /**
    * Where the eyes are (for infrared eyeshine). The quadruped heads sit forward of the body box; grazing or bedded
    * animals carry them low. Only eyes that look toward the lens shine.
    */
   static void eyes(LivingEntity a, CompoundTag tag, Vec3 lens, float yaw, float pitch, TrailcamLook.Mark m) {
      float h = a.getBbHeight();
      float w = a.getBbWidth();
      float head = a.yHeadRot * Mth.DEG_TO_RAD;
      Vec3 dir = new Vec3(-Mth.sin(head), 0.0, Mth.cos(head));
      boolean low = false;
      float eyeY;
      float fwd;
      if (a instanceof Whitetail) {
         low = tag.getCompound("wt").getFloat("gz") > 0.5F;
         eyeY = low ? h * 0.3F : h * 0.9F;
         fwd = low ? h * 0.55F + 0.1F : h * 0.36F + 0.1F;
      } else if (a instanceof WildlifeMob wm) {
         int b = wm.behavior();
         low = b == WildlifeMob.FEED || b == WildlifeMob.REST;
         eyeY = b == WildlifeMob.REST ? h * 0.5F : (low ? h * 0.35F : h * 0.8F);
         fwd = h * 0.4F + w * 0.3F;
      } else {
         eyeY = a.getEyeHeight();
         fwd = w * 0.5F;
      }
      Vec3 centre = a.position().add(0.0, eyeY, 0.0).add(dir.scale(fwd));
      Vec3 toLens = lens.subtract(centre);
      double dist = toLens.length();
      Vec3 flat = new Vec3(toLens.x, 0.0, toLens.z).normalize();
      double facing = flat.dot(dir);
      if (facing < 0.25) {
         return;
      }
      float strength = (float)Math.min(1.0, (facing - 0.25) / 0.5) * (low ? 0.6F : 1.0F);
      m.shine *= strength;
      float sep = Mth.clamp(h * 0.075F, 0.04F, 0.16F);
      Vec3 side = new Vec3(dir.z, 0.0, -dir.x).scale(sep * 0.5);
      for (int k = -1; k <= 1; k += 2) {
         double[] q = TrailcamScene.project(lens, yaw, pitch, centre.add(side.scale(k)));
         if (q != null) {
            m.eyes.add(new float[]{(float)q[0], (float)q[1], (float)dist});
         }
      }
   }

   private static void setFloat(Object target, Class<?> owner, String name, float value) {
      String key = owner.getName() + "#" + name;
      Field f = FIELDS.get(key);
      if (f == null && !FIELDS.containsKey(key)) {
         try {
            f = owner.getDeclaredField(name);
            f.setAccessible(true);
         } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.debug("Trail camera: no field {}", key);
            f = null;
         }
         FIELDS.put(key, f);
      }
      if (f != null) {
         try {
            f.setFloat(target, value);
         } catch (ReflectiveOperationException | RuntimeException ignored) {
         }
      }
   }
}
