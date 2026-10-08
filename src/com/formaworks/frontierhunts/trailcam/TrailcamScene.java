package com.formaworks.frontierhunts.trailcam;

import com.formaworks.frontierhunts.expedition.CameraRegistry;
import com.formaworks.frontierhunts.expedition.HuntingCalendar;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.season.SeasonClock;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import com.mojang.authlib.properties.Property;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Trail camera "scene" records: everything the owner's client needs to re-render the exact moment a camera fired
 * (lens pose, time of day to the tick, weather, moon, and each subject's entity type, synced entity data, pose,
 * animation phase and gear). Pure data + math; safe on both sides (no client classes).
 *
 * Scene NBT (format 1):
 * <pre>
 * v, uid, n (frame no.), t (game time), d (day time), rain, thunder, temp (deg C), moon, dim, cam (label),
 * lx ly lz yaw pitch (lens), ir (infrared night frame), mo dy yr (reserve calendar), subjects [..]
 * subject: type, k (0 animal, 1 player, 2 other), x y z, yr xr by hy, wp ws (walk anim), age, g (on ground),
 *          vx vy vz (motion per tick), w h eye, shine, data (packed synced entity data), eq [..], wt {..},
 *          lbl, key, traits, p {name, id, tex, sig, parts, left}
 * </pre>
 */
public final class TrailcamScene {
   public static final int FORMAT = 1;
   /** Horizontal field of view of the trail camera lens (degrees). The photo is 16:9. */
   public static final float FOV_H = 70.0F;
   public static final float FOV_V = (float)Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(FOV_H / 2.0)) * 9.0 / 16.0));
   public static final int MAX_SUBJECTS = 6;
   private static final int MAX_DATA_BYTES = 6144;
   private static final int MAX_ITEM_BYTES = 1536;
   private static final Logger LOG = LoggerFactory.getLogger(TrailcamScene.class);

   private TrailcamScene() {
   }

   // ------------------------------------------------------------------------------------------------ lens geometry

   /** The lens sits on the front face of the camera block, a touch above centre (matches the live lens view). */
   public static Vec3 lens(BlockPos pos, Direction facing) {
      return Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.40)).add(0.0, 0.1, 0.0);
   }

   /**
    * Downward tilt (degrees, positive = down, Minecraft pitch) so the frame centre lands ~0.9 m above the ground about
    * 8 m out. A camera strapped high on a trunk looks down at the trail like a real one.
    */
   public static float aimPitch(Level level, Vec3 lens, Direction facing) {
      double sum = 0.0;
      int n = 0;
      for (int d = 5; d <= 11; d += 2) {
         int x = Mth.floor(lens.x + facing.getStepX() * d);
         int z = Mth.floor(lens.z + facing.getStepZ() * d);
         if (!level.hasChunkAt(new BlockPos(x, (int)lens.y, z))) {
            continue;
         }
         int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
         // a ridge or a wall right in front: don't aim into the sky over it, and don't aim through a cliff below
         top = Mth.clamp(top, (int)lens.y - 10, (int)lens.y + 4);
         sum += top;
         n++;
      }
      if (n == 0) {
         return 4.0F;
      }
      double target = sum / n + 0.9;
      float pitch = (float)Math.toDegrees(Math.atan2(lens.y - target, 8.0));
      return Mth.clamp(pitch, -8.0F, 22.0F);
   }

   public static Vec3 forward(float yaw, float pitch) {
      float y = yaw * Mth.DEG_TO_RAD;
      float p = pitch * Mth.DEG_TO_RAD;
      return new Vec3(-Mth.sin(y) * Mth.cos(p), -Mth.sin(p), Mth.cos(y) * Mth.cos(p));
   }

   public static Vec3 right(float yaw) {
      float y = yaw * Mth.DEG_TO_RAD;
      return new Vec3(-Mth.cos(y), 0.0, -Mth.sin(y));
   }

   /**
    * Projects a world point into the photo: returns {x, y, depth} with x,y in -1..1 (x right, y up), or null behind the
    * lens.
    */
   public static double[] project(Vec3 lens, float yaw, float pitch, Vec3 point) {
      Vec3 f = forward(yaw, pitch);
      Vec3 r = right(yaw);
      Vec3 u = r.cross(f);
      Vec3 v = point.subtract(lens);
      double z = v.dot(f);
      if (z < 0.05) {
         return null;
      }
      double tx = Math.tan(Math.toRadians(FOV_H / 2.0));
      double ty = Math.tan(Math.toRadians(FOV_V / 2.0));
      return new double[]{v.dot(r) / (z * tx), v.dot(u) / (z * ty), z};
   }

   /** True when an entity's body centre is inside the photo frame and within range. */
   public static boolean inFrame(Vec3 lens, float yaw, float pitch, LivingEntity e, double range) {
      Vec3 c = e.position().add(0.0, e.getBbHeight() * 0.55, 0.0);
      double dist = c.distanceTo(lens);
      if (dist > range || dist < 0.7) {
         return false;
      }
      double[] p = project(lens, yaw, pitch, c);
      return p != null && Math.abs(p[0]) <= 0.94 && p[1] >= -1.05 && p[1] <= 1.0;
   }

   /** Line of sight from the lens to the head or the middle of the body. */
   public static boolean visible(Level level, Vec3 lens, LivingEntity e) {
      Vec3[] targets = {
         e.position().add(0.0, e.getBbHeight() * 0.55, 0.0), e.position().add(0.0, e.getBbHeight() * 0.9, 0.0), e.getEyePosition()
      };
      for (Vec3 t : targets) {
         if (level.clip(new ClipContext(lens, t, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e)).getType() == HitResult.Type.MISS) {
            return true;
         }
      }
      return false;
   }

   /** Infrared at night / in deep shade, like the light sensor on a real camera (reads the air in front of the lens). */
   public static boolean dark(ServerLevel level, Vec3 lens, Direction facing) {
      Vec3 front = lens.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.7));
      return level.getMaxLocalRawBrightness(BlockPos.containing(front)) < 8;
   }

   // ------------------------------------------------------------------------------------------------ subjects

   /** What the PIR sensor reacts to: the mod's animals and players, and (optionally) vanilla mobs. */
   public static boolean subject(LivingEntity e) {
      if (!e.isAlive() || e.isInvisible() || e.isSpectator()) {
         return false;
      }
      if (e instanceof Whitetail w) {
         return !w.downed();
      }
      if (e instanceof WildlifeMob || e instanceof Player) {
         return true;
      }
      return TrailcamConfig.vanillaMobs() && e instanceof Mob && !(e instanceof ArmorStand);
   }

   // ------------------------------------------------------------------------------------------------ capture

   public static CompoundTag capture(
      ServerLevel level, CameraRegistry.Station station, Vec3 lens, float yaw, float pitch, boolean ir, List<LivingEntity> subjects, int frameNo
   ) {
      CompoundTag s = new CompoundTag();
      s.putInt("v", FORMAT);
      s.putLong("uid", level.getRandom().nextLong() ^ System.nanoTime() * 0x9E3779B97F4A7C15L);
      s.putInt("n", frameNo);
      s.putLong("t", level.getGameTime());
      s.putLong("d", level.getDayTime());
      s.putFloat("rain", level.getRainLevel(1.0F));
      s.putFloat("thunder", level.getThunderLevel(1.0F));
      s.putFloat("temp", temperature(level, lens));
      s.putInt("moon", level.getMoonPhase());
      s.putString("dim", level.dimension().location().toString());
      s.putString("cam", clip(station.label(), 40));
      s.putDouble("lx", lens.x);
      s.putDouble("ly", lens.y);
      s.putDouble("lz", lens.z);
      s.putFloat("yaw", yaw);
      s.putFloat("pitch", pitch);
      s.putBoolean("ir", ir);
      try {
         HuntingCalendar.Date date = HuntingCalendar.date(level);
         s.putInt("mo", date.month());
         s.putInt("dy", date.day());
         s.putInt("yr", (int)Math.min(99L, date.serial() / 12L + 1L));
      } catch (RuntimeException e) {
         LOG.debug("Trail camera: calendar unavailable", e);
      }
      ListTag list = new ListTag();
      for (LivingEntity e : subjects) {
         if (list.size() >= MAX_SUBJECTS) {
            break;
         }
         try {
            list.add(subject(level, e));
         } catch (RuntimeException ex) {
            LOG.warn("Trail camera could not record {}", e, ex);
         }
      }
      s.put("subjects", list);
      return s;
   }

   private static CompoundTag subject(ServerLevel level, LivingEntity e) {
      CompoundTag t = new CompoundTag();
      t.putString("type", BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
      t.putByte("k", (byte)(e instanceof Player ? 1 : (e instanceof Whitetail || e instanceof WildlifeMob ? 0 : 2)));
      t.putDouble("x", e.getX());
      t.putDouble("y", e.getY());
      t.putDouble("z", e.getZ());
      t.putFloat("yr", e.getYRot());
      t.putFloat("xr", e.getXRot());
      t.putFloat("by", e.yBodyRot);
      t.putFloat("hy", e.getYHeadRot());
      t.putFloat("wp", e.walkAnimation.position());
      t.putFloat("ws", e.walkAnimation.speed());
      t.putInt("age", e.tickCount);
      t.putBoolean("g", e.onGround());
      Vec3 m = e.position().subtract(e.xo, e.yo, e.zo);
      t.putFloat("vx", (float)m.x);
      t.putFloat("vy", (float)m.y);
      t.putFloat("vz", (float)m.z);
      t.putFloat("w", e.getBbWidth());
      t.putFloat("h", e.getBbHeight());
      t.putFloat("eye", e.getEyeHeight());
      t.putFloat("shine", eyeshine(e));
      byte[] data = packData(level, e);
      if (data != null) {
         t.putByteArray("data", data);
      }
      ListTag eq = new ListTag();
      for (EquipmentSlot slot : EquipmentSlot.values()) {
         ItemStack stack = e.getItemBySlot(slot);
         if (stack.isEmpty()) {
            continue;
         }
         CompoundTag it = new CompoundTag();
         it.putString("s", slot.getName());
         Tag item = stack.save(level.registryAccess());
         if (item instanceof CompoundTag ct && size(ct) > MAX_ITEM_BYTES) {
            // a heavily enchanted/renamed item: keep the look (id + count), drop the bulky components
            item = new ItemStack(stack.getItem(), stack.getCount()).save(level.registryAccess());
         }
         it.put("i", item);
         eq.add(it);
      }
      if (!eq.isEmpty()) {
         t.put("eq", eq);
      }
      if (e instanceof Whitetail w) {
         DeerTraits traits = w.traits().withSpecies(w.species());
         t.put("traits", traits.save());
         CompoundTag wt = new CompoundTag();
         wt.putFloat("tr", readFloat(w, "travel"));
         wt.putFloat("ms", readFloat(w, "motionSpeed"));
         wt.putFloat("gz", readFloat(w, "grazing"));
         t.put("wt", wt);
         t.putString("lbl", deerTitle(traits));
      } else if (e instanceof WildlifeMob wm) {
         t.putString("lbl", wildlifeTitle(wm.species));
      } else if (e instanceof Player p) {
         t.putString("lbl", clip(p.getGameProfile().getName(), 32));
      }
      t.putString("key", e.getType().getDescriptionId());
      if (e instanceof ServerPlayer p) {
         CompoundTag pt = new CompoundTag();
         pt.putString("name", clip(p.getGameProfile().getName(), 32));
         pt.putUUID("id", p.getUUID());
         for (Property prop : p.getGameProfile().getProperties().get("textures")) {
            if (prop.value() != null && prop.value().length() <= 4096) {
               pt.putString("tex", prop.value());
               if (prop.signature() != null && prop.signature().length() <= 2048) {
                  pt.putString("sig", prop.signature());
               }
            }
            break;
         }
         int parts = 0;
         for (PlayerModelPart part : PlayerModelPart.values()) {
            if (p.isModelPartShown(part)) {
               parts |= part.getMask();
            }
         }
         pt.putInt("parts", parts);
         pt.putBoolean("left", p.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT);
         t.put("p", pt);
      }
      return t;
   }

   /** Synced entity data exactly as the network would send it to a client that starts tracking the entity. */
   private static byte[] packData(ServerLevel level, LivingEntity e) {
      List<SynchedEntityData.DataValue<?>> values = e.getEntityData().getNonDefaultValues();
      if (values == null || values.isEmpty()) {
         return null;
      }
      RegistryFriendlyByteBuf out = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
      try {
         for (SynchedEntityData.DataValue<?> v : values) {
            RegistryFriendlyByteBuf one = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            try {
               v.write(one);
               if (one.readableBytes() > 4096 || out.readableBytes() + one.readableBytes() > MAX_DATA_BYTES - 1) {
                  continue; // an oversized value (e.g. a huge wound log) is skipped, the rest still renders
               }
               out.writeBytes(one);
            } catch (RuntimeException ex) {
               LOG.debug("Trail camera: entity data {} of {} not recorded", v.id(), e, ex);
            } finally {
               one.release();
            }
         }
         out.writeByte(255);
         byte[] bytes = new byte[out.readableBytes()];
         out.readBytes(bytes);
         return bytes;
      } finally {
         out.release();
      }
   }

   private static float readFloat(Object o, String name) {
      try {
         Field f = o.getClass().getDeclaredField(name);
         f.setAccessible(true);
         return f.getFloat(o);
      } catch (ReflectiveOperationException | RuntimeException e) {
         return 0.0F;
      }
   }

   private static int size(CompoundTag t) {
      return t.sizeInBytes();
   }

   /** How strongly the eyes throw back an infrared flash (tapetum lucidum). Humans and most birds barely do. */
   static float eyeshine(LivingEntity e) {
      if (e instanceof Player) {
         return 0.0F;
      }
      if (e instanceof Whitetail) {
         return 1.0F;
      }
      if (e instanceof WildlifeMob wm) {
         return switch (wm.species) {
            case GROUSE, DUCK -> 0.15F;
            case BOAR -> 0.55F;
            case BISON -> 0.7F;
            case PRONGHORN -> 0.9F;
            default -> 1.0F;
         };
      }
      return 0.6F;
   }

   public static String deerTitle(DeerTraits t) {
      GameSpecies sp = t.species();
      String name = sp == GameSpecies.WHITETAIL ? "Whitetail" : sp.title;
      return name + " " + sp.sexName(t.buck());
   }

   public static String deerDetail(DeerTraits t) {
      StringBuilder b = new StringBuilder(t.ageClass());
      if (t.buck() && t.totalPoints() > 0) {
         b.append(" · ").append(t.totalPoints()).append(" pt · score ").append(t.trophyScore());
      }
      b.append(" · ").append(t.massKg()).append(" kg");
      return b.toString();
   }

   public static String wildlifeTitle(WildlifeSpecies s) {
      String[] words = s.id.split("_");
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < words.length; i++) {
         if (i > 0) {
            b.append(' ');
         }
         String w = words[i];
         b.append(i == 0 ? Character.toUpperCase(w.charAt(0)) + w.substring(1) : w);
      }
      return b.toString();
   }

   /** Doe traits used as the legacy "traits" of a frame whose subject is not a deer (keeps hub trophy stats clean). */
   public static DeerTraits nonDeerTraits() {
      CompoundTag t = DeerTraits.REFERENCE.save();
      t.putBoolean("buck", false);
      t.putInt("rack_genes", 0);
      return DeerTraits.load(t);
   }

   // ------------------------------------------------------------------------------------------------ data strip

   /**
    * Air temperature at the lens in deg C: biome climate, reserve season (SeasonClock), time of day, rain and altitude.
    */
   public static float temperature(ServerLevel level, Vec3 lens) {
      BlockPos pos = BlockPos.containing(lens);
      Biome biome = level.getBiome(pos).value();
      float base = Mth.clamp(biome.getBaseTemperature(), -0.7F, 1.6F);
      float c = 3.0F + base * 20.0F;
      float winter;
      try {
         winter = SeasonClock.winterness(level);
      } catch (RuntimeException e) {
         winter = 0.3F;
      }
      c += 6.0F - 20.0F * winter;
      double hour = (level.getDayTime() % 24000L) / 1000.0 + 6.0;
      c += (float)(5.0 * Math.cos((hour - 15.0) / 24.0 * Math.PI * 2.0));
      c -= 2.5F * level.getRainLevel(1.0F);
      c -= (float)Mth.clamp((lens.y - 70.0) * 0.02, -2.0, 8.0);
      if (biome.coldEnoughToSnow(pos)) {
         c = Math.min(c, 1.0F);
      }
      return Mth.clamp(c, -40.0F, 50.0F);
   }

   public static String clip(String s, int max) {
      if (s == null) {
         return "";
      }
      return s.length() <= max ? s : s.substring(0, max);
   }

   /** "02:14 AM" from a day time. Minecraft day time 0 = 06:00. */
   public static String clock(long dayTime) {
      long t = Math.floorMod(dayTime, 24000L);
      int minutes = (int)((t * 1440L / 24000L + 360L) % 1440L);
      int h = minutes / 60;
      int m = minutes % 60;
      int h12 = h % 12 == 0 ? 12 : h % 12;
      return String.format(Locale.ROOT, "%02d:%02d %s", h12, m, h < 12 ? "AM" : "PM");
   }

   public static int hour(long dayTime) {
      return (int)(Math.floorMod(dayTime, 24000L) * 24L / 24000L + 6L) % 24;
   }

   /** 64-bit id for a photo, stable for the life of the scene (it travels with the camera's memory card). */
   public static long photoId(CompoundTag scene) {
      long h = scene.getLong("uid");
      h ^= scene.getLong("t") * 0xC2B2AE3D27D4EB4FL;
      h ^= (long)scene.getInt("n") * 0x165667B19E3779F9L;
      h ^= h >>> 29;
      h *= 0xBF58476D1CE4E5B9L;
      h ^= h >>> 32;
      return h;
   }

   static RandomSource random(long seed) {
      return RandomSource.create(seed);
   }
}
