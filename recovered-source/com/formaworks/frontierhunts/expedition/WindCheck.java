package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.hunting.HuntParticles;
import java.util.Locale;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class WindCheck {
   public static void puff(ServerPlayer var0) {
      ServerLevel var1 = var0.serverLevel();
      Wilderness.Wind var2 = Wilderness.wind(var1.getSeed(), var1.getGameTime(), var1.isRaining(), var1.isThundering());
      double var3 = thermal(var1);
      Vec3 var5 = var0.getEyePosition().add(var0.getLookAngle().scale(0.55)).subtract(0.0, 0.28, 0.0);
      var1.sendParticles((SimpleParticleType)HuntParticles.WIND_PUFF.get(), var5.x, var5.y, var5.z, 30, 0.1, 0.07, 0.1, 0.01);
      SoundEvent var6 = EquipmentSounds.named("wind_puff");
      if (var6 != null) {
         var1.playSound(null, var0.blockPosition(), var6, SoundSource.PLAYERS, 0.45F, 1.12F);
      }

      ExpeditionService.message(var0, readout(var2, var3, mask(var0, var1)));
   }

   public static double thermal(ServerLevel var0) {
      return Wilderness.thermal(var0.getDayTime(), var0.isRaining(), (float)var0.getSkyDarken() / 15.0F);
   }

   /** [clothing] the scent you put downwind: the one function every animal reads (carbon layer, spray, sweat, wet, perks) */
   public static double mask(ServerPlayer var0, ServerLevel var1) {
      return ScentControl.scentMultiplier(var0);
   }

   public static String readout(Wilderness.Wind var0, double var1, double var3) {
      return String.format(
         Locale.ROOT, "Wind %s at %.1f m/s · %s · scent %d%%", var0.directionTo(), var0.speed(), Wilderness.thermalNote(var1), Math.round(var3 * 100.0)
      );
   }

   private WindCheck() {
   }
}
