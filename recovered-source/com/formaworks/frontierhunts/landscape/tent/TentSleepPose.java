package com.formaworks.frontierhunts.landscape.tent;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Where a sleeper's body lies in a shelter, derived only from the bed block (which every client and the server
 * agree on), never from the sleeper's live position.
 *
 * <p>Vanilla parks a sleeper at the bed-cell centre, 0.6875 above the cell floor, and the server keeps it there
 * (move packets of a sleeping player are ignored). The local client however keeps simulating gravity on its own
 * player, and the compact tents have no collision under the bed roll, so the local copy drops onto the ground
 * and every effect that followed the live position (the lowered body render, the first-person camera) ended up
 * in the dirt. The origin returned here is the model origin of the lying body: its back rests on the pad /
 * sleeping bag / cot surface (origin = surface + 0.117 half body depth + a hair), centred on the pad, with the
 * head at the pillow end. Offsets per tent are in the compact tent contract ({@code sleep}, relative to the
 * vanilla sleeping position, north-facing) and were fitted against the tent meshes offline.
 */
public final class TentSleepPose {
   /** Vanilla {@code LivingEntity.setPosToBed} height above the bed cell floor. */
   public static final double VANILLA_Y = 0.6875;
   /** First-person eye: along the head direction from the body origin (over the face) ... */
   public static final double EYE_FORWARD = 0.12;
   /** ... and above the body origin (face surface is at +0.234, hat layer +0.264). */
   public static final double EYE_UP = 0.30;
   /** Lying on your back you look up at the ceiling, tilted towards your feet and the door. */
   public static final float LOOK_UP = 40.0F;
   /** The reserve camp cot's sheet sits at 6.8/16 instead of a vanilla mattress's 9/16. */
   static final double COT_DY = -0.1415;
   private static final ResourceLocation COT = FrontierHunts.id("camp_cot");
   private static final Map<Long, long[]> CAMPING = new HashMap<>();
   private static java.lang.ref.WeakReference<Level> campingLevel = new java.lang.ref.WeakReference<>(null);

   private TentSleepPose() {
   }

   /**
    * @param origin  model origin of the lying body (what vanilla puts at the bed-cell centre)
    * @param head    the direction the head points (the bed orientation)
    * @param shelter true inside a tent (the first-person camera is reframed), false for a cot elsewhere
    */
   public record Pose(Vec3 origin, Direction head, boolean shelter) {
   }

   public static Pose of(LivingEntity sleeper) {
      BlockPos bed = sleeper.getSleepingPos().orElse(null);
      return bed == null ? null : at(sleeper.level(), bed);
   }

   public static Pose at(Level level, BlockPos bed) {
      if (!level.isLoaded(bed)) {
         return null;
      }

      BlockState st = level.getBlockState(bed);
      if (st.getBlock() instanceof CompactTent tent) {
         if (st.getValue(CompactTent.PART) != tent.shape.bed()) {
            return null;
         }

         Vec3 off = tent.sleepOffset(st.getValue(CompactTent.FACING));
         return new Pose(
            new Vec3(bed.getX() + 0.5 + off.x, bed.getY() + VANILLA_Y + off.y, bed.getZ() + 0.5 + off.z), tent.getBedDirection(st, level, bed), true
         );
      }

      if (st.getBlock() instanceof BedBlock) {
         boolean cot = COT.equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()));
         boolean camping = insideCampingTent(level, bed);
         if (!cot && !camping) {
            return null;
         }

         return new Pose(new Vec3(bed.getX() + 0.5, bed.getY() + VANILLA_Y + (cot ? COT_DY : 0.0), bed.getZ() + 0.5), st.getValue(BedBlock.FACING), camping);
      }

      return null;
   }

   /** Beds standing on a camping tent's floor; the 245-block search is cached per bed for two seconds. */
   private static boolean insideCampingTent(Level level, BlockPos bed) {
      long now = level.getGameTime();
      synchronized (CAMPING) {
         if (campingLevel.get() != level || CAMPING.size() > 64) {
            CAMPING.clear();
            campingLevel = new java.lang.ref.WeakReference<>(level);
         }

         long[] c = CAMPING.get(bed.asLong());
         if (c != null && now >= c[0] && now - c[0] <= 40L) {
            return c[1] != 0L;
         }

         boolean inside = TentContent.campingOrigin(level, bed) != null;
         CAMPING.put(bed.asLong(), new long[]{now, inside ? 1L : 0L});
         return inside;
      }
   }
}
