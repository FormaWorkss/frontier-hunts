package com.formaworks.frontierhunts.hunting;

import net.minecraft.util.Mth;

public record DeerWound(float impactDamage, int duration, float lossPerPulse, int reaction) {
   public static final int FLINCH = 1;
   public static final int BUCK = 2;
   public static final int STUMBLE = 3;

   public static DeerWound from(DeerAnatomy.Region var0, float var1) {
      float var2 = Mth.clamp(var1, 0.0F, 1.2F);

      return switch (var0) {
         // [integration] heart / double-lung hits drop on the spot again (no tracking death run), so these keep the
         // original duration (it only applies to a too-weak graze that does not drop the animal)
         case HEART, DOUBLE_LUNG -> new DeerWound(28.0F * var2, 1600, 0.85F * var2, 3);
         case CHEST, LUNG -> new DeerWound(9.0F * var2, 520, 0.95F * var2, 3);
         case SPINE -> new DeerWound(9.0F * var2, 180, 0.7F * var2, 3);
         case BRAIN -> new DeerWound(9.0F * var2, 110, 0.7F * var2, 3);
         case LIVER -> new DeerWound(10.0F * var2, 700 + Math.round((1.2F - var2) * 300.0F), 0.6F * var2, 2);
         case GUT -> new DeerWound(6.0F * var2, 3200 + Math.round((1.2F - var2) * 800.0F), 0.22F * var2, 2);
         case NECK -> new DeerWound(10.0F * var2, 420 + Math.round((1.2F - var2) * 200.0F), 0.9F * var2, 1);
         case HEAD -> new DeerWound(12.0F * var2, 700 + Math.round((1.2F - var2) * 300.0F), 0.45F * var2, 1);
         case SHOULDER -> new DeerWound(8.0F * var2, 1200, 0.2F * var2, 3);
         case BODY -> new DeerWound(6.0F * var2, 900, 0.18F * var2, 2);
         case LEG -> new DeerWound(4.0F * var2, 1200, 0.1F * var2, 3);
      };
   }

   public boolean fatal(DeerAnatomy.Region var1, float var2) {
      return var2 >= 0.12F && var1 != DeerAnatomy.Region.LEG && var1 != DeerAnatomy.Region.BODY && var1 != DeerAnatomy.Region.SHOULDER;
   }
}
