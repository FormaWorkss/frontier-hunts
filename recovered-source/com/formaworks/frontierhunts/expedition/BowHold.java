package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.hunting.HunterCover;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class BowHold {
   public static final int STEADY = 60;
   private static final float DECAY = 220.0F;

   public static float strain(LivingEntity var0, int var1) {
      if (var0 != null && var1 > 60) {
         int var2 = var0 instanceof Player var3 ? HunterCover.of(var3) : 0;
         return strain(var0, var1, var2);
      } else {
         return 0.0F;
      }
   }

   public static float strain(LivingEntity var0, int var1, int var2) {
      if (var0 == null) {
         return 0.0F;
      } else {
         float var3 = (float)Math.max(0, var1 - 60) / 220.0F;
         if (var3 <= 0.0F) {
            return 0.0F;
         } else {
            if (com.formaworks.frontierhunts.prone.Prone.isProne(var0)) {
               var3 *= 0.35F; // [bows] prone: elbows on the ground, the steadiest hold
            } else if (var0.isCrouching()) {
               var3 *= 0.45F;
            }

            if (var2 != 0) {
               var3 *= 0.62F;
            }

            if (var0 instanceof Player var4) {
               var3 *= com.formaworks.frontierhunts.progression.AssignmentService.fatigueMultiplier(var4); // [academy] Steady hold certification
               var3 *= com.formaworks.frontierhunts.campcook.MealBuffs.aim(var4); // [licence] Steady Aim camp meal
            }

            return Math.min(2.4F, var3);
         }
      }
   }

   public static float creep(float var0) {
      return Math.min(0.3F, var0 * 0.22F);
   }

   private BowHold() {
   }
}
