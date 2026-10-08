package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.perf.AiThrottle;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * [perf] Brackets Mob.serverAiStep (final, so no override is possible) so {@link AiThrottle} can skip
 * the goal-selection pass of calm Frontier wildlife far from every player. Runs on both sides; does
 * nothing for any other mob. Optional (require = 0): if another mod reshapes the method, throttling is
 * simply off.
 */
@Mixin(Mob.class)
public abstract class PerfMobAiMixin {
   @Inject(method = "serverAiStep", at = @At("HEAD"), require = 0)
   private void frontierhunts$perfEnter(CallbackInfo ci) {
      AiThrottle.enter((Mob)(Object)this);
   }

   @Inject(method = "serverAiStep", at = @At("RETURN"), require = 0)
   private void frontierhunts$perfExit(CallbackInfo ci) {
      AiThrottle.exit();
   }
}
