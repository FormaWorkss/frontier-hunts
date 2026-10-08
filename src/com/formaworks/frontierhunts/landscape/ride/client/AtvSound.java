package com.formaworks.frontierhunts.landscape.ride.client;

import com.formaworks.frontierhunts.landscape.ride.Atv;
import com.formaworks.frontierhunts.landscape.ride.RideContent;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

final class AtvSound extends AbstractTickableSoundInstance {
   private final Atv atv;
   private final AtvSound.Layer layer;
   private float gain;

   private AtvSound(Atv var1, AtvSound.Layer var2, SoundEvent var3) {
      super(var3, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
      this.atv = var1;
      this.layer = var2;
      this.looping = true;
      this.delay = 0;
      this.volume = 0.01F;
      this.x = var1.getX();
      this.y = var1.getY();
      this.z = var1.getZ();
   }

   static AtvSound[] engine(Atv var0) {
      return new AtvSound[]{
         new AtvSound(var0, AtvSound.Layer.LOW, (SoundEvent)RideContent.SND_ATV_ENGINE.get()),
         new AtvSound(var0, AtvSound.Layer.HIGH, (SoundEvent)RideContent.SND_ATV_ENGINE_HIGH.get()),
         new AtvSound(var0, AtvSound.Layer.WHINE, (SoundEvent)RideContent.SND_ATV_WHINE.get())
      };
   }

   public void tick() {
      if (this.atv.isRemoved()) {
         this.stop();
      } else {
         boolean var1 = com.formaworks.frontierhunts.landscape.ride.rig.AtvFuel.running(this.atv); // [atvfuel] was isVehicle && !flooded
         float var2 = Mth.clamp(this.atv.rpm, 0.0F, 1.0F);
         float var3 = Math.max(0.0F, this.atv.throttleShown);
         float var4 = 1300.0F + 6100.0F * var2;
         float var5 = var1 ? 0.5F + 0.5F * var2 * (0.65F + 0.35F * var3) : 0.0F;
         float var6;
         float var7;
         switch (this.layer) {
            case LOW:
               var6 = var5 * (1.0F - Mth.clamp((var4 - 3600.0F) / 2000.0F, 0.0F, 1.0F) * 0.85F);
               var7 = var4 / 2400.0F;
               break;
            case HIGH:
               var6 = var5 * Mth.clamp((var4 - 3000.0F) / 2600.0F, 0.0F, 1.0F) * (0.75F + 0.25F * var3);
               var7 = var4 / 6000.0F;
               break;
            default:
               float var8 = (float)Mth.clamp(Math.abs(this.atv.shownSpeed) / 1.1, 0.0, 1.2);
               var6 = var1 ? var8 * (0.18F + 0.22F * var3) : 0.0F;
               var7 = 0.55F + var8 * 0.95F;
         }

         var6 *= com.formaworks.frontierhunts.landscape.ride.rig.AtvFuel.soundGain(this.atv); // [atvfuel] dips on fuel-starved misfires
         this.gain = this.gain + (var6 - this.gain) * (var6 > this.gain ? 0.3F : 0.1F);
         if (!var1 && this.gain < 0.01F) {
            this.stop();
         } else {
            this.volume = Math.max(0.001F, this.gain);
            this.pitch = Mth.clamp(var7, 0.5F, 2.0F);
            this.x = this.atv.getX();
            this.y = this.atv.getY() + 0.5;
            this.z = this.atv.getZ();
         }
      }
   }

   public boolean canStartSilent() {
      return true;
   }

   static enum Layer {
      LOW,
      HIGH,
      WHINE;
   }
}
