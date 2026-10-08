package com.formaworks.frontierhunts.sign.work;

import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * [deersign] Priority-1 goal that owns a buck's movement and head while he walks to a sign site and works it (see
 * {@link SignWork}). The routine, foraging and strolling goals wait; flight, bedding, calls and fights end it.
 */
public final class SignGoal extends Goal {
   private final Whitetail deer;

   public SignGoal(Whitetail deer) {
      this.deer = deer;
      this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
   }

   @Override
   public boolean canUse() {
      return !this.deer.level().isClientSide && SignWork.goalWanted(this.deer);
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
      SignWork.goalStop(this.deer);
   }

   @Override
   public void tick() {
      SignWork.tick(this.deer);
   }
}
