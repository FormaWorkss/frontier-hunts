package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.client.sound.FrontierSoundMixer;
import com.formaworks.frontierhunts.client.sound.FrontierSoundVolumes;
import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * [gui] Frontier sound mixer hook. Scales the final volume of Frontier's own sounds by the Sound page sliders:
 * <ul>
 *   <li>when a sound starts ({@code play} -> {@code calculateVolume(float, SoundSource)}; the instance is the one after
 *       NeoForge's PlaySoundEvent, so wrappers such as the kill cam's ducking are covered),</li>
 *   <li>on every tick of a tickable sound and whenever Minecraft re-applies its own sliders
 *       ({@code calculateVolume(SoundInstance)}), so loops follow a slider live,</li>
 *   <li>on demand for everything playing ({@link FrontierSoundVolumes}).</li>
 * </ul>
 * Inject-only (no redirects), applied after Minecraft's clamp, so it composes with other sound mods.
 */
@Mixin(SoundEngine.class)
public abstract class FrontierSoundEngineMixin implements FrontierSoundVolumes {
   @Shadow
   @Final
   private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;
   @Shadow
   private boolean loaded;

   @Shadow
   private float calculateVolume(SoundInstance sound) {
      throw new AssertionError();
   }

   @Unique
   private SoundInstance frontierhunts$starting;

   @Inject(
      method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F")
   )
   private void frontierhunts$beforeStartVolume(SoundInstance sound, CallbackInfo ci) {
      this.frontierhunts$starting = sound;
   }

   @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("RETURN"))
   private void frontierhunts$afterPlay(SoundInstance sound, CallbackInfo ci) {
      this.frontierhunts$starting = null;
   }

   @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", at = @At("RETURN"), cancellable = true)
   private void frontierhunts$startVolume(float volume, SoundSource source, CallbackInfoReturnable<Float> cir) {
      SoundInstance s = this.frontierhunts$starting;
      if (s != null) {
         this.frontierhunts$starting = null;
         float f = FrontierSoundMixer.factor(s);
         if (f != 1.0F) {
            cir.setReturnValue(cir.getReturnValueF() * f);
         }
      }
   }

   @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
   private void frontierhunts$volume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
      float f = FrontierSoundMixer.factor(sound);
      if (f != 1.0F) {
         cir.setReturnValue(cir.getReturnValueF() * f);
      }
   }

   @Override
   public void frontierhunts$refreshVolumes() {
      if (!this.loaded) {
         return;
      }
      for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> e : this.instanceToChannel.entrySet()) {
         SoundInstance s = e.getKey();
         if (FrontierSoundMixer.category(s) != null) {
            float v = this.calculateVolume(s);
            e.getValue().execute(channel -> channel.setVolume(v));
         }
      }
   }
}
