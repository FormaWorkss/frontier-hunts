package com.formaworks.frontierhunts.tracking;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * [ecology] The readable sign a predator kill leaves, written into the tracking store: a matted, bloody scuffle where
 * the prey was caught, drag/scuffle scratches from there to the body, blood soaking out under it while it is fed on,
 * and tufts of the prey's hair whose inspection reads as the kill ("Wolf kill · whitetail doe · pack of 3"). The
 * predators' own prints come from {@link TrackPrints} as they mill about. Every loop here is a handful of marks.
 */
public final class KillSign {
   private KillSign() {
   }

   /** At the moment of the kill. {@code prey} must still be alive/in the world (used for blood type and position). */
   public static void killed(ServerLevel level, LivingEntity prey, Vec3 caught, Vec3 body, float yaw, String reading, boolean neck, RandomSource r) {
      if (!sign(level) || prey == null || !finite(caught) || !finite(body)) {
         return;
      }
      long now = level.getGameTime();
      BloodTrail.BloodType t = neck ? BloodTrail.BloodType.ARTERIAL : BloodTrail.BloodType.MUSCLE;
      // the struggle: a matted bed with blood where it went down, a spray around it
      TrailService.mark(prey, caught, TrailMark.BED, t);
      for (int i = 0; i < 3; i++) {
         double a = i * 2.399 + yaw * Mth.DEG_TO_RAD;
         TrailService.mark(prey, caught.add(Math.cos(a) * 0.6, 0.0, Math.sin(a) * 0.6), i == 0 ? TrailMark.IMPACT : TrailMark.DRIP, t);
      }
      // drag / scuffle line to the body (or a ring of scuffs when it dropped where it was caught)
      double d = Math.sqrt(caught.distanceToSqr(body));
      if (d > 1.2) {
         int n = Math.min(6, (int)Math.ceil(d / 0.9));
         float lineYaw = (float)Math.toDegrees(Math.atan2(-(body.x - caught.x), body.z - caught.z));
         for (int i = 1; i <= n; i++) {
            Vec3 p = caught.lerp(body, (double)i / (n + 1));
            surface(level, p, prey.getUUID(), lineYaw + (r.nextFloat() - 0.5F) * 20.0F, TrailMark.SCRATCH, 0.22F, reading, now);
         }
      } else {
         for (int i = 0; i < 3; i++) {
            double a = i * 2.1 + r.nextFloat();
            surface(level, caught.add(Math.cos(a) * 0.9, 0.0, Math.sin(a) * 0.9), prey.getUUID(), (float)Math.toDegrees(a), TrailMark.SCRATCH, 0.22F, reading, now);
         }
      }
      // tufts of hair pulled out in the fight: these read as the kill
      for (int i = 0; i < 3; i++) {
         double a = r.nextFloat() * Mth.TWO_PI;
         double rr = 0.4 + r.nextFloat() * 1.4;
         surface(level, caught.add(Math.cos(a) * rr, 0.0, Math.sin(a) * rr), prey.getUUID(), r.nextFloat() * 360.0F, TrailMark.FUR, 0.18F, reading, now);
      }
   }

   /** Small game taken whole: a few drops of blood and a pinch of fur or feathers where it was caught. */
   public static void smallKill(ServerLevel level, LivingEntity prey, Vec3 at, String reading, RandomSource r) {
      if (!sign(level) || prey == null || !finite(at)) {
         return;
      }
      TrailService.mark(prey, at, TrailMark.DRIP, BloodTrail.BloodType.MUSCLE);
      surface(level, at, prey.getUUID(), r.nextFloat() * 360.0F, TrailMark.FUR, 0.16F, reading, level.getGameTime());
   }

   /** While predators feed: blood soaking out under the body, now and then more hair. {@code feeder} stands at the body. */
   public static void feeding(ServerLevel level, LivingEntity feeder, Vec3 body, UUID carcass, String reading, RandomSource r, boolean fur) {
      if (!sign(level) || feeder == null || !finite(body)) {
         return;
      }
      double a = r.nextFloat() * Mth.TWO_PI;
      Vec3 p = body.add(Math.cos(a) * 0.5, 0.0, Math.sin(a) * 0.5);
      TrailService.mark(feeder, p, r.nextInt(3) == 0 ? TrailMark.POOL : TrailMark.DENSE, BloodTrail.BloodType.GUT);
      if (fur) {
         double b = r.nextFloat() * Mth.TWO_PI;
         surface(level, body.add(Math.cos(b) * 1.1, 0.0, Math.sin(b) * 1.1), carcass, r.nextFloat() * 360.0F, TrailMark.FUR, 0.18F, reading, level.getGameTime());
      }
   }

   /** blood follows the tracking rule; fur and scratches are stored too but only shown where prints are (hunting on) */
   private static boolean sign(ServerLevel level) {
      return level.getGameRules().getBoolean(com.formaworks.frontierhunts.HuntRules.TRACKING);
   }

   private static boolean finite(Vec3 v) {
      return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
   }

   /** a non-blood clue (fur, scratches) lying on the ground under {@code at}; skipped where there is no ground */
   private static void surface(ServerLevel level, Vec3 at, UUID animal, float yaw, int style, float radius, String reading, long now) {
      if (!finite(at)) {
         return;
      }
      BlockPos.MutableBlockPos m = BlockPos.containing(at.x, at.y + 0.6, at.z).mutable();
      for (int i = 0; i < 5; i++, m.move(Direction.DOWN)) {
         if (!level.isLoaded(m)) {
            return;
         }
         BlockState s = level.getBlockState(m);
         VoxelShape shape = s.getCollisionShape(level, m);
         if (shape.isEmpty()) {
            continue;
         }
         double top = shape.max(Direction.Axis.Y);
         if (top < 0.5 || top > 1.0 || !level.getFluidState(m.above()).isEmpty()) {
            return;
         }
         String who = reading == null ? "Predator kill" : reading.length() > 80 ? reading.substring(0, 80) : reading;
         try {
            TrailMark mark = new TrailMark(
               UUID.randomUUID(), animal == null ? TrailMark.UNKNOWN : animal, new Vec3(at.x, m.getY() + top + 0.012, at.z), m.immutable(),
               Mth.wrapDegrees(yaw), now, 0, false, who, 0, Direction.UP, style, Math.clamp(radius, 0.05F, 0.45F), 0, 0.0F, 1.0F
            );
            TrailStore.get(level).add(mark, now);
         } catch (IllegalArgumentException ignored) {
            // out-of-range values never make it into the store
         }
         return;
      }
   }
}
