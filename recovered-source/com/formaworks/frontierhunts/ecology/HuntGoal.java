package com.formaworks.frontierhunts.ecology;

import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * [ecology] Holds a predator's movement and look while it is part of a hunt (the hunt itself is ticked by
 * {@link PredationService}); when it is free, its canUse is where a hungry predator may decide to start one. Added to
 * every wildlife mob at priority 2 (after FloatGoal and the flee/defend response, before wandering); for prey species
 * it returns false at once.
 */
public final class HuntGoal extends Goal {
   private final WildlifeMob mob;
   /** 0 = not known yet (goals are registered before WildlifeMob sets its species), 1 predator, -1 not */
   private int predator;

   public HuntGoal(WildlifeMob mob) {
      this.mob = mob;
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
   }

   @Override
   public boolean canUse() {
      if (this.predator == 0 && this.mob.species != null) {
         this.predator = Predator.of(this.mob.species) != null ? 1 : -1;
      }
      if (this.predator != 1 || this.mob.level().isClientSide) {
         return false;
      }
      return PredationService.huntOf(this.mob) != null || PredationService.consider(this.mob);
   }

   @Override
   public boolean canContinueToUse() {
      Hunt h = PredationService.huntOf(this.mob);
      return h != null && !h.done() && PredationService.mayContinue(this.mob);
   }

   @Override
   public boolean isInterruptable() {
      return true;
   }

   @Override
   public void stop() {
      PredationService.drop(this.mob);
      if (this.mob.hunting()) {
         this.mob.ecoState(WildlifeMob.IDLE);
      }
   }
}
