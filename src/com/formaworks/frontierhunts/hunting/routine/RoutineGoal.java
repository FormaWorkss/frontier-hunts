package com.formaworks.frontierhunts.hunting.routine;

import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** [routines] Carries out the daily routine: walking game trails between anchors, feeding steps, drinking, bedding. */
public final class RoutineGoal extends Goal {
   private final Whitetail deer;

   public RoutineGoal(Whitetail deer) {
      this.deer = deer;
      this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
   }

   @Override
   public boolean canUse() {
      return !this.deer.level().isClientSide && this.deer.routine().goalCanUse();
   }

   @Override
   public boolean canContinueToUse() {
      return this.deer.routine().goalCanContinue();
   }

   @Override
   public void stop() {
      this.deer.routine().goalStop();
   }

   @Override
   public void tick() {
      this.deer.routine().goalTick();
   }
}
