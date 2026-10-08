package com.formaworks.frontierhunts.academy;

import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.tracking.TrackPrints;
import com.formaworks.frontierhunts.tracking.TrailMark;
import com.formaworks.frontierhunts.tracking.TrailStore;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * [academy] One course: how its plot is built (once per slot), where the hunter arrives, what is spawned for a session
 * and how the objectives are counted. Subclasses are small state machines driven from {@link TrainingService}.
 */
abstract class CourseKit {
   final Course course;

   CourseKit(Course course) {
      this.course = course;
   }

   static CourseKit of(Course c) {
      return switch (c) {
         case GLASSING -> new GlassingKit();
         case STALK -> new StalkKit();
         case RANGE -> new RangeKit();
         case TRACKING -> new TrackingKit();
         case DRESSING -> new DressingKit();
         case ARCHERY -> new ArcheryKit(); // [onboard]
      };
   }

   /** Bump when the plot layout changes: older plots are left alone and new slots are built. */
   abstract int version();

   abstract void build(Plot plot);

   /** Arrival: local x, dy, z, yaw, pitch. */
   abstract float[] arrival();

   abstract void begin(Session s, ServerLevel level);

   abstract void tick(Session s, ServerLevel level, ServerPlayer p);

   void inspected(Session s, ServerLevel level, ServerPlayer p, TrailMark m) {
   }

   void dressed(Session s, ServerLevel level, ServerPlayer p, Whitetail deer) {
   }

   void usedItem(Session s, ServerLevel level, ServerPlayer p, ItemStack st) {
   }

   void end(Session s, ServerLevel level) {
   }

   // ================================================================================================ helpers

   static String itemId(ItemStack st) {
      return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
   }

   static Wilderness.Wind wind(ServerLevel level) {
      return Wilderness.wind(level.getSeed(), level.getGameTime(), level.isRaining(), level.isThundering());
   }

   /** Spawns a whitetail from NBT (traits fixed so the lesson animal looks the part), tagged and tracked. */
   static Whitetail deer(Session s, ServerLevel level, double lx, double lz, float yaw, boolean ai, CompoundTag traits) {
      CompoundTag t = new CompoundTag();
      t.putString("id", "frontierhunts:whitetail");
      Vec3 w = s.world(lx, 0.0, lz);
      ListTag pos = new ListTag();
      pos.add(DoubleTag.valueOf(w.x));
      pos.add(DoubleTag.valueOf(w.y));
      pos.add(DoubleTag.valueOf(w.z));
      t.put("Pos", pos);
      ListTag rot = new ListTag();
      rot.add(FloatTag.valueOf(yaw));
      rot.add(FloatTag.valueOf(0.0F));
      t.put("Rotation", rot);
      t.putBoolean("NoAI", !ai);
      t.putBoolean("PersistenceRequired", true);
      ListTag tags = new ListTag();
      tags.add(StringTag.valueOf(Academy.TAG));
      t.put("Tags", tags);
      if (traits != null) {
         t.put("deer_traits", traits);
      }
      Entity e = EntityType.loadEntityRecursive(t, level, en -> en);
      if (!(e instanceof Whitetail deer)) {
         if (e != null) {
            e.discard();
         }
         return null;
      }
      deer.moveTo(w.x, w.y, w.z, yaw, 0.0F);
      deer.setYBodyRot(yaw);
      deer.setYHeadRot(yaw);
      if (!level.addFreshEntity(deer)) {
         return null;
      }
      s.entities.add(deer.getUUID());
      return deer;
   }

   static CompoundTag traits(boolean buck, int ageMonths, int frame, int rack, int seed, int coat) {
      CompoundTag t = new CompoundTag();
      t.putInt("schema", 2);
      t.putBoolean("buck", buck);
      t.putInt("age_months", ageMonths);
      t.putInt("frame", frame);
      t.putInt("condition", 80);
      t.putInt("rack_genes", rack);
      t.putInt("seed", seed);
      t.putInt("abnormal", 0);
      t.putInt("coat", coat);
      return t;
   }

   /** Lays the deer down where it stands (a recovered / downed animal for the lane or the dressing station). */
   static void down(ServerLevel level, Whitetail deer) {
      deer.hurt(level.damageSources().generic(), 1000.0F);
   }

   static Whitetail find(ServerLevel level, UUID id) {
      return id != null && level.getEntity(id) instanceof Whitetail w && w.isAlive() ? w : null;
   }

   static void remove(ServerLevel level, UUID id) {
      if (id != null) {
         Entity e = level.getEntity(id);
         if (e != null) {
            e.discard();
         }
      }
   }

   /** Floating text (vanilla text display): distance boards and station signs. */
   static void label(Session s, ServerLevel level, double lx, double dy, double lz, String key, String fallback, float scale, int background, String billboard) {
      CompoundTag t = new CompoundTag();
      t.putString("id", "minecraft:text_display");
      Vec3 w = s.world(lx, dy, lz);
      ListTag pos = new ListTag();
      pos.add(DoubleTag.valueOf(w.x));
      pos.add(DoubleTag.valueOf(w.y));
      pos.add(DoubleTag.valueOf(w.z));
      t.put("Pos", pos);
      ListTag rot = new ListTag();
      rot.add(FloatTag.valueOf(180.0F));
      rot.add(FloatTag.valueOf(0.0F));
      t.put("Rotation", rot);
      ListTag tags = new ListTag();
      tags.add(StringTag.valueOf(Academy.TAG));
      t.put("Tags", tags);
      t.putString("text", "{\"translate\":\"" + key + "\",\"fallback\":\"" + fallback.replace("\"", "'") + "\",\"color\":\"#F3E9CF\"}");
      t.putString("billboard", billboard);
      t.putInt("background", background);
      t.putBoolean("shadow", true);
      t.putInt("line_width", 180);
      t.putString("alignment", "center");
      t.putFloat("view_range", 2.6F);
      CompoundTag tf = new CompoundTag();
      tf.put("translation", floats(0.0F, 0.0F, 0.0F));
      tf.put("left_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
      tf.put("scale", floats(scale, scale, scale));
      tf.put("right_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
      t.put("transformation", tf);
      CompoundTag light = new CompoundTag();
      light.putInt("sky", 15);
      light.putInt("block", 15);
      t.put("brightness", light);
      Entity e = EntityType.loadEntityRecursive(t, level, en -> en);
      if (e != null) {
         e.moveTo(w.x, w.y, w.z, 180.0F, 0.0F);
         if (level.addFreshEntity(e)) {
            s.entities.add(e.getUUID());
         }
      }
   }

   private static ListTag floats(float... v) {
      ListTag l = new ListTag();
      for (float f : v) {
         l.add(FloatTag.valueOf(f));
      }
      return l;
   }

   /** A blood mark lying on the ground block under (lx, lz). */
   static TrailMark blood(Session s, ServerLevel level, double lx, double lz, int style, float radius, int sign, UUID animal, String individual, long created,
      float yaw) {
      BlockPos support = s.block(Mth.floor(lx), -1, Mth.floor(lz));
      BlockState st = level.getBlockState(support);
      if (st.isAir() || !st.isFaceSturdy(level, support, Direction.UP)) {
         return null;
      }
      float r = Mth.clamp(radius, 0.05F, 0.45F);
      double fx = Mth.clamp(lx - Mth.floor(lx), r + 0.004, 1.0 - r - 0.004);
      double fz = Mth.clamp(lz - Mth.floor(lz), r + 0.004, 1.0 - r - 0.004);
      Vec3 pos = new Vec3(support.getX() + fx, support.getY() + 1.012, support.getZ() + fz);
      try {
         TrailMark m = new TrailMark(UUID.randomUUID(), animal, pos, support, yaw, Math.max(0L, created), 0, true, individual, 2, Direction.UP, style, r, sign, 0.0F,
            1.0F);
         if (TrailStore.get(level).add(m, level.getGameTime())) {
            s.marks.add(m.id());
            return m;
         }
      } catch (IllegalArgumentException ignored) {
      }
      return null;
   }

   static String describe(Entity e) {
      try {
         return TrackPrints.describe(e);
      } catch (RuntimeException ex) {
         return "Whitetail";
      }
   }

   /** Points the range flags across the wind (a flag streams with the wind, so its cloth faces across it). */
   static void flags(Session s, ServerLevel level) {
      if (s.banners.isEmpty()) {
         return;
      }
      Wilderness.Wind w = wind(level);
      double fx = -w.south(), fz = w.east();
      if (Math.abs(fx) + Math.abs(fz) < 1.0E-4) {
         return;
      }
      double deg = Math.toDegrees(Math.atan2(-fx, fz));
      int rot = Math.floorMod((int)Math.round(deg / 22.5), 16);
      for (BlockPos p : s.banners) {
         BlockState st = level.getBlockState(p);
         if (st.getBlock() instanceof BannerBlock && st.hasProperty(BannerBlock.ROTATION) && st.getValue(BannerBlock.ROTATION) != rot) {
            level.setBlock(p, st.setValue(BannerBlock.ROTATION, rot), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
         }
      }
   }

   /** Angle (degrees) between the player's look and the direction to a point. */
   static double lookAngle(ServerPlayer p, Vec3 target) {
      Vec3 eye = p.getEyePosition();
      Vec3 to = target.subtract(eye);
      double len = to.length();
      if (len < 1.0E-4) {
         return 0.0;
      }
      double dot = p.getLookAngle().dot(to.scale(1.0 / len));
      return Math.toDegrees(Math.acos(Mth.clamp(dot, -1.0, 1.0)));
   }

   /** Clear line of sight from the eye to a point (blocks only, foliage counts). */
   static boolean sight(ServerLevel level, ServerPlayer p, Vec3 target) {
      var hit = level.clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(), target, net.minecraft.world.level.ClipContext.Block.VISUAL,
         net.minecraft.world.level.ClipContext.Fluid.NONE, p));
      return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getLocation().distanceToSqr(target) < 0.5;
   }

   static double horizontal(Vec3 a, Vec3 b) {
      double dx = a.x - b.x, dz = a.z - b.z;
      return Math.sqrt(dx * dx + dz * dz);
   }

   static boolean usingItem(ServerPlayer p, String fragment) {
      return p.isUsingItem() && itemId(p.getUseItem()).contains(fragment);
   }
}
