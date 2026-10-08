package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class WildlifeLure extends Goal {
   private final PathfinderMob animal;
   private BlockPos source;
   private BlockPos destination;
   private int repath;

   public WildlifeLure(PathfinderMob var1) {
      this.animal = var1;
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
   }

   public static boolean calm(PathfinderMob var0) {
      if (!var0.isNoAi()
         && var0.isAlive()
         && var0.getTarget() == null
         && var0 instanceof Whitetail var1
         && !var1.downed()
         && !var1.sedated()
         && !var1.waking()
         && !var1.bleeding()
         && !var1.respondingToCall()
         && (double)var1.alertness() < 0.25) {
         return true;
      }

      return false;
   }

   public static boolean offer(PathfinderMob var0, BlockPos var1, BlockPos var2) {
      CompoundTag var3 = var0.getPersistentData();
      long var4 = var0.level().getGameTime();
      if (calm(var0) && var3.getLong("frontier_decoy_until") <= var4 && var0.level().hasChunkAt(var2)) {
         Path var6 = var0.getNavigation().createPath(var2, 1);
         if (var6 != null && var6.canReach()) {
            var3.putLong("frontier_decoy_source", var1.asLong());
            var3.putLong("frontier_decoy_destination", var2.asLong());
            var3.putLong("frontier_decoy_until", var4 + 220L);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean available() {
      if (calm(this.animal) && this.animal.getPersistentData().getLong("frontier_decoy_until") > this.animal.level().getGameTime()) {
         this.source = BlockPos.of(this.animal.getPersistentData().getLong("frontier_decoy_source"));
         if (!this.animal.level().hasChunkAt(this.source)) {
            return false;
         } else {
            BlockState var1 = this.animal.level().getBlockState(this.source);
            if (var1.getBlock() instanceof ScentDecoy var2 && (Boolean)var1.getValue(ScentDecoy.ACTIVE) && var2.accepts(this.animal)) {
               return true;
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public boolean canUse() {
      if (!this.available()) {
         return false;
      } else {
         this.destination = BlockPos.of(this.animal.getPersistentData().getLong("frontier_decoy_destination"));
         return this.animal.level().hasChunkAt(this.destination);
      }
   }

   public boolean canContinueToUse() {
      return this.available();
   }

   public void start() {
      this.animal.getNavigation().stop();
      this.repath = 0;
   }

   public void tick() {
      Vec3 var1 = Vec3.atCenterOf(this.source);
      if (this.animal.distanceToSqr(var1) < 9.0) {
         this.animal.getNavigation().stop();
         this.animal.getLookControl().setLookAt(var1.x, var1.y, var1.z, 5.0F, 5.0F);
      } else {
         if (--this.repath <= 0) {
            this.repath = 40;
            this.animal
               .getNavigation()
               .moveTo((double)this.destination.getX() + 0.5, (double)this.destination.getY(), (double)this.destination.getZ() + 0.5, 0.65);
         }

         Vec3 var2 = this.animal.getForward();
         this.animal.getLookControl().setLookAt(this.animal.getX() + var2.x * 4.0, this.animal.getEyeY(), this.animal.getZ() + var2.z * 4.0, 5.0F, 4.0F);
      }
   }

   public void stop() {
      this.animal.getNavigation().stop();
      this.animal.getPersistentData().remove("frontier_decoy_until");
   }
}
