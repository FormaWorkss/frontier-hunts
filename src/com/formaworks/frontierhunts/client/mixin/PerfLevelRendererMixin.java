package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.perf.client.HitchLogger;
import com.formaworks.frontierhunts.perf.client.PerfStats;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * [perf2] Counters for the hitch logger: every chunk section marked dirty (any cause) and every whole-world renderer
 * reload. Priority 2000 so it is applied after Sodium's overwrite of setSectionDirty (the injection then lands in
 * Sodium's body). Both optional (require = 0): if another mod reshapes them, the counters simply stay at zero.
 */
@Mixin(value = LevelRenderer.class, priority = 2000)
public abstract class PerfLevelRendererMixin {
   @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"), require = 0)
   private void frontierhunts$perfSectionDirty(int x, int y, int z, boolean important, CallbackInfo ci) {
      PerfStats.vanillaSection();
   }

   @Inject(method = "allChanged()V", at = @At("HEAD"), require = 0)
   private void frontierhunts$perfAllChanged(CallbackInfo ci) {
      HitchLogger.allChanged();
   }
}
