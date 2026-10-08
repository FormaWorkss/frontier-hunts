package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.client.sound.FrontierSoundVolumes;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** [gui] Lets the Frontier sound mixer reach the SoundEngine to re-apply its volumes (see FrontierSoundEngineMixin). */
@Mixin(SoundManager.class)
public abstract class FrontierSoundManagerMixin implements FrontierSoundVolumes {
   @Shadow
   @Final
   private SoundEngine soundEngine;

   @Override
   public void frontierhunts$refreshVolumes() {
      if ((Object)this.soundEngine instanceof FrontierSoundVolumes volumes) {
         volumes.frontierhunts$refreshVolumes();
      }
   }
}
