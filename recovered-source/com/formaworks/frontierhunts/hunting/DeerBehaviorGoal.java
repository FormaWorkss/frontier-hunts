package com.formaworks.frontierhunts.hunting;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;

final class DeerBehaviorGoal extends Goal {
   private final Whitetail deer;

   DeerBehaviorGoal(Whitetail var1) {
      this.deer = var1;
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
   }

   private boolean owned() {
      if (this.deer.downed()) {
         return false;
      } else {
         int var1 = this.deer.behavior();
         return var1 != 0;
      }
   }

   public boolean canUse() {
      return this.owned();
   }

   public boolean canContinueToUse() {
      return this.owned();
   }

   public boolean isInterruptable() {
      return false;
   }

   public boolean requiresUpdateEveryTick() {
      return false;
   }
}
