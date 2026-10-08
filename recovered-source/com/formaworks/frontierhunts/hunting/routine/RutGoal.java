package com.formaworks.frontierhunts.hunting.routine;

import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** [routines] Priority-1 goal that moves a deer while it is fighting, tending/chasing, being chased or retreating. */
public final class RutGoal extends Goal {
   private final Whitetail deer;

   public RutGoal(Whitetail deer) {
      this.deer = deer;
      this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
   }

   @Override
   public boolean canUse() {
      return RutEngine.goalWanted(this.deer.routine(), this.deer.level().getGameTime());
   }

   @Override
   public boolean canContinueToUse() {
      return this.canUse();
   }

   @Override
   public boolean requiresUpdateEveryTick() {
      return true;
   }

   @Override
   public void stop() {
      RutEngine.goalStop(this.deer.routine());
   }

   @Override
   public void tick() {
      RutEngine.goalTick(this.deer.routine(), this.deer.level().getGameTime());
   }
}
