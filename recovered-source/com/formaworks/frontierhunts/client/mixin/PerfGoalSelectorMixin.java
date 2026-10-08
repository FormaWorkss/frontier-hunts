package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.perf.AiThrottle;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * [perf] On a tick {@link AiThrottle} marks as throttled, GoalSelector.tick skips choosing new goals and
 * only ticks the goals already running (exactly what tick() would have ticked after its selection pass).
 * Optional (require = 0).
 */
@Mixin(GoalSelector.class)
public abstract class PerfGoalSelectorMixin {
   @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
   private void frontierhunts$perfSkipSelection(CallbackInfo ci) {
      if (AiThrottle.skipGoalSelection()) {
         ((GoalSelector)(Object)this).tickRunningGoals(true);
         ci.cancel();
      }
   }
}
