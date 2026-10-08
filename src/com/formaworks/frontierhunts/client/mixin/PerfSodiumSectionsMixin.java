package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.perf.client.PerfStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * [perf2] With Sodium every chunk-section rebuild request (block, light and chunk updates, ours) ends in
 * RenderSectionManager.scheduleRebuild; counting there gives the hitch logger the true "sections dirtied" figure
 * (Sodium overwrites the vanilla LevelRenderer paths). Optional twice over: @Pseudo (no Sodium, no mixin) and
 * require = 0 (another Sodium version without this method: the counter falls back to the LevelRenderer hook).
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class PerfSodiumSectionsMixin {
   @Inject(method = "scheduleRebuild(IIIZ)V", at = @At("HEAD"), require = 0, remap = false)
   private void frontierhunts$perfScheduleRebuild(int x, int y, int z, boolean important, CallbackInfo ci) {
      PerfStats.sodiumSection();
   }
}
