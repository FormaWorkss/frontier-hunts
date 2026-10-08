package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Rut;
import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class GameCalls {
   private static String name(Rut.Call var0) {
      return switch (var0) {
         case GRUNT -> "Grunt";
         case BLEAT -> "Bleat";
         case RATTLE -> "Rattling";
         case SNORT_WHEEZE -> "Snort-wheeze";
      };
   }

   public static String soundId(Rut.Call var0) {
      return switch (var0) {
         case GRUNT -> "grunt_tube";
         case BLEAT -> "bleat_call";
         case RATTLE -> "antler_rattle";
         case SNORT_WHEEZE -> "grunt_tube";
      };
   }

   public static GameCalls.Result blow(ServerPlayer var0, Rut.Call var1) {
      ServerLevel var2 = var0.serverLevel();
      HuntingCalendar.Date var3 = HuntingCalendar.date(var2);
      double var4 = Rut.carry(var1);
      SoundEvent var6 = EquipmentSounds.named(soundId(var1));
      if (var6 != null) {
         var2.playSound(
            null, var0.blockPosition(), var6, SoundSource.PLAYERS, var1 == Rut.Call.RATTLE ? 2.6F : 2.0F, var1 == Rut.Call.SNORT_WHEEZE ? 1.18F : 1.0F
         );
      }

      int var7 = 0;
      int var8 = 0;
      int var9 = 0;

      for (Whitetail var11 : var2.getEntitiesOfClass(
         Whitetail.class, var0.getBoundingBox().inflate(var4), var0x -> var0x.isAlive() && !var0x.downed() && !var0x.sedated()
      )) {
         double var12 = Math.sqrt(var11.distanceToSqr(var0));
         if (!(var12 > var4)) {
            DeerTraits var14 = var11.traits();
            Rut.Phase var15 = Rut.phase(var11.species(), var3);
            float var16 = Rut.answer(var1, var15, var14.buck(), var14.ageMonths() >= 36);
            var16 *= (float)Math.clamp(1.12 - var12 / var4, 0.0, 1.0);
            if (var11.alertness() > 0.5F) {
               var11.hear(var0.position(), var4 * 0.6, 0.5F);
               var9++;
            } else {
               boolean var17 = var1 == Rut.Call.RATTLE || var1 == Rut.Call.SNORT_WHEEZE;
               if (var1 == Rut.Call.SNORT_WHEEZE && var14.buck() && var14.ageMonths() < 36 && var15.active()) {
                  var11.hear(var0.position(), var4 * 0.5, 0.55F);
                  var9++;
               } else if (var16 > 0.18F && var11.approachCall(var0.position(), var16, var17)) {
                  var7++;
               } else {
                  var8++;
               }
            }
         }
      }

      return new GameCalls.Result(var7, var8, var9);
   }

   public static Rut.Phase localPhase(ServerPlayer var0) {
      return Rut.phase(GameSpecies.WHITETAIL, HuntingCalendar.date(var0.serverLevel()));
   }

   private GameCalls() {
   }

   public static record Result(int answered, int ignored, int spooked) {
      public String message(Rut.Call var1, Rut.Phase var2) {
         String var3 = var2.active() ? var2.title.toLowerCase(Locale.ROOT) : "off season";
         if (this.answered > 0) {
            return GameCalls.name(var1) + " · " + this.answered + (this.answered == 1 ? " animal is coming" : " animals are coming") + " · " + var3;
         } else if (this.spooked > 0) {
            return GameCalls.name(var1) + " · you bumped something · " + var3;
         } else {
            return this.ignored > 0 ? GameCalls.name(var1) + " · heard and ignored · " + var3 : GameCalls.name(var1) + " · nothing within earshot · " + var3;
         }
      }
   }
}
