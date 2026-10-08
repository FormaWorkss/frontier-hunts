package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.AttachmentSpec;
import com.formaworks.frontierhunts.expedition.Weapon;

final class FieldWeaponSockets {
   static double opticY(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE -> 0.094;
         case SEMI_AUTO_RIFLE -> 0.101;
         case PUMP_SHOTGUN -> 0.094;
         case DOUBLE_BARREL -> 0.09;
         case SEMI_AUTO_SHOTGUN -> 0.099;
         case REVOLVER -> 0.09;
         case FIELD_PISTOL -> 0.145;
         case TRANQUILIZER_RIFLE -> 0.091;
         case FLARE_GUN -> 0.076;
         case BAIT_LAUNCHER -> 0.104;
         default -> 0.0;
      };
   }

   static double opticZ(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE -> -0.006;
         case SEMI_AUTO_RIFLE -> -0.01;
         case PUMP_SHOTGUN -> 0.0;
         case DOUBLE_BARREL -> -0.05;
         case SEMI_AUTO_SHOTGUN -> 0.02;
         case REVOLVER -> -0.02;
         case FIELD_PISTOL -> -0.064;
         case TRANQUILIZER_RIFLE -> -0.01;
         case FLARE_GUN -> -0.01;
         case BAIT_LAUNCHER -> 0.05;
         default -> 0.0;
      };
   }

   static double ironY(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE -> 0.111;
         case SEMI_AUTO_RIFLE -> 0.1125;
         case PUMP_SHOTGUN -> 0.0975;
         case DOUBLE_BARREL -> 0.093;
         case SEMI_AUTO_SHOTGUN -> 0.1025;
         case REVOLVER -> 0.0925;
         case FIELD_PISTOL -> 0.153;
         case TRANQUILIZER_RIFLE -> 0.111;
         case FLARE_GUN -> 0.08;
         case BAIT_LAUNCHER -> 0.141;
         default -> 0.0;
      };
   }

   static double muzzleY(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE -> 0.067;
         case SEMI_AUTO_RIFLE -> 0.067;
         case PUMP_SHOTGUN -> 0.067;
         case DOUBLE_BARREL -> 0.067;
         case SEMI_AUTO_SHOTGUN -> 0.067;
         case REVOLVER -> 0.0685;
         case FIELD_PISTOL -> 0.117;
         case TRANQUILIZER_RIFLE -> 0.067;
         case FLARE_GUN -> 0.053;
         case BAIT_LAUNCHER -> 0.067;
         default -> 0.0;
      };
   }

   static double muzzleZ(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE -> -0.63;
         case SEMI_AUTO_RIFLE -> -0.575;
         case PUMP_SHOTGUN -> -0.69;
         case DOUBLE_BARREL -> -0.79;
         case SEMI_AUTO_SHOTGUN -> -0.742;
         case REVOLVER -> -0.212;
         case FIELD_PISTOL -> -0.18;
         case TRANQUILIZER_RIFLE -> -0.6;
         case FLARE_GUN -> -0.232;
         case BAIT_LAUNCHER -> -0.492;
         default -> 0.0;
      };
   }

   static double supportY(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE -> 0.027;
         case SEMI_AUTO_RIFLE -> 0.046;
         case PUMP_SHOTGUN -> 0.024;
         case DOUBLE_BARREL -> 0.031;
         case SEMI_AUTO_SHOTGUN -> 0.023;
         case REVOLVER -> -0.08;
         case FIELD_PISTOL -> -0.08;
         case TRANQUILIZER_RIFLE -> 0.029;
         case FLARE_GUN -> -0.08;
         case BAIT_LAUNCHER -> -0.022;
         default -> 0.0;
      };
   }

   /**
    * [guns3] Where a round goes in on a tube-fed gun (gun model space), plus how far to the side the round comes from:
    * {x, y, z, side}. Shotguns load from underneath, just ahead of the trigger guard; the lever rifle through the
    * loading gate on the right of the receiver.
    */
   static double[] loadingPort(Weapon w) {
      return switch (w) {
         case LEVER_RIFLE -> new double[]{0.021, 0.03, 0.03, 0.05};
         case SEMI_AUTO_SHOTGUN -> new double[]{0.0, 0.022, 0.018, 0.0};
         default -> new double[]{0.0, 0.006, 0.022, 0.0};
      };
   }

   static double supportZ(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE -> -0.26;
         case SEMI_AUTO_RIFLE -> -0.3;
         case PUMP_SHOTGUN -> -0.265;
         case DOUBLE_BARREL -> -0.3;
         case SEMI_AUTO_SHOTGUN -> -0.3;
         case REVOLVER -> 0.075;
         case FIELD_PISTOL -> 0.075;
         case TRANQUILIZER_RIFLE -> -0.22;
         case FLARE_GUN -> 0.075;
         case BAIT_LAUNCHER -> -0.26;
         default -> 0.0;
      };
   }

   static double leverY(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE -> -0.005;
         case SEMI_AUTO_RIFLE -> -0.01;
         case PUMP_SHOTGUN -> -0.01;
         case DOUBLE_BARREL -> -0.01;
         case SEMI_AUTO_SHOTGUN -> -0.01;
         case REVOLVER -> -0.01;
         case FIELD_PISTOL -> -0.01;
         case TRANQUILIZER_RIFLE -> -0.01;
         case FLARE_GUN -> -0.01;
         case BAIT_LAUNCHER -> -0.01;
         default -> 0.0;
      };
   }

   static double leverZ(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE -> 0.009;
         case SEMI_AUTO_RIFLE -> 0.103;
         case PUMP_SHOTGUN -> 0.103;
         case DOUBLE_BARREL -> 0.103;
         case SEMI_AUTO_SHOTGUN -> 0.103;
         case REVOLVER -> 0.103;
         case FIELD_PISTOL -> 0.103;
         case TRANQUILIZER_RIFLE -> 0.103;
         case FLARE_GUN -> 0.103;
         case BAIT_LAUNCHER -> 0.103;
         default -> 0.0;
      };
   }

   static double hammerY(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE -> 0.07;
         case SEMI_AUTO_RIFLE -> 0.077;
         case PUMP_SHOTGUN -> 0.077;
         case DOUBLE_BARREL -> 0.077;
         case SEMI_AUTO_SHOTGUN -> 0.077;
         case REVOLVER -> 0.078;
         case FIELD_PISTOL -> 0.124;
         case TRANQUILIZER_RIFLE -> 0.077;
         case FLARE_GUN -> 0.072;
         case BAIT_LAUNCHER -> 0.077;
         default -> 0.0;
      };
   }

   static double hammerZ(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE -> 0.118;
         case SEMI_AUTO_RIFLE -> 0.119;
         case PUMP_SHOTGUN -> 0.119;
         case DOUBLE_BARREL -> 0.119;
         case SEMI_AUTO_SHOTGUN -> 0.119;
         case REVOLVER -> 0.078;
         case FIELD_PISTOL -> 0.05;
         case TRANQUILIZER_RIFLE -> 0.119;
         case FLARE_GUN -> 0.072;
         case BAIT_LAUNCHER -> 0.119;
         default -> 0.0;
      };
   }

   static double triggerY(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE -> 0.006;
         case SEMI_AUTO_RIFLE -> 0.014;
         case PUMP_SHOTGUN -> 0.012;
         case DOUBLE_BARREL -> 0.008;
         case SEMI_AUTO_SHOTGUN -> 0.012;
         case REVOLVER -> 0.026;
         case FIELD_PISTOL -> 0.004;
         case TRANQUILIZER_RIFLE -> 0.03;
         case FLARE_GUN -> 0.018;
         case BAIT_LAUNCHER -> 0.026;
         default -> 0.0;
      };
   }

   static double triggerZ(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE -> 0.074;
         case SEMI_AUTO_RIFLE -> 0.07;
         case PUMP_SHOTGUN -> 0.074;
         case DOUBLE_BARREL -> 0.011;
         case SEMI_AUTO_SHOTGUN -> 0.074;
         case REVOLVER -> 0.026;
         case FIELD_PISTOL -> 0.021;
         case TRANQUILIZER_RIFLE -> 0.074;
         case FLARE_GUN -> 0.004;
         case BAIT_LAUNCHER -> 0.06;
         default -> 0.0;
      };
   }

   static double stockY(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE -> 0.072;
         case SEMI_AUTO_RIFLE -> 0.09;
         case PUMP_SHOTGUN -> 0.074;
         case DOUBLE_BARREL -> 0.068;
         case SEMI_AUTO_SHOTGUN -> 0.075;
         case REVOLVER -> 0.06;
         case FIELD_PISTOL -> 0.06;
         case TRANQUILIZER_RIFLE -> 0.072;
         case FLARE_GUN -> 0.06;
         case BAIT_LAUNCHER -> 0.08;
         default -> 0.0;
      };
   }

   /** [guns3] Length scale of the Steady Stock riser: the carbine stock's comb is shorter than the riser. */
   static float stockScale(Weapon w) {
      return w == Weapon.SEMI_AUTO_RIFLE ? 0.85F : 1.0F;
   }

   static double stockZ(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE -> 0.3;
         case SEMI_AUTO_RIFLE -> 0.31; // [guns3] centred on the short carbine comb
         case PUMP_SHOTGUN -> 0.3;
         case DOUBLE_BARREL -> 0.3;
         case SEMI_AUTO_SHOTGUN -> 0.33;
         case REVOLVER -> 0.3;
         case FIELD_PISTOL -> 0.3;
         case TRANQUILIZER_RIFLE -> 0.31;
         case FLARE_GUN -> 0.3;
         case BAIT_LAUNCHER -> 0.26;
         default -> 0.0;
      };
   }

   static double gripY(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE -> -0.13;
         case SEMI_AUTO_RIFLE -> -0.13;
         case PUMP_SHOTGUN -> -0.13;
         case DOUBLE_BARREL -> -0.13;
         case SEMI_AUTO_SHOTGUN -> -0.13;
         case REVOLVER -> -0.092;
         case FIELD_PISTOL -> -0.1;
         case TRANQUILIZER_RIFLE -> -0.13;
         case FLARE_GUN -> -0.105;
         case BAIT_LAUNCHER -> -0.13;
         default -> 0.0;
      };
   }

   static double gripZ(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE -> 0.18;
         case SEMI_AUTO_RIFLE -> 0.18;
         case PUMP_SHOTGUN -> 0.18;
         case DOUBLE_BARREL -> 0.18;
         case SEMI_AUTO_SHOTGUN -> 0.18;
         case REVOLVER -> 0.068;
         case FIELD_PISTOL -> 0.075;
         case TRANQUILIZER_RIFLE -> 0.18;
         case FLARE_GUN -> 0.07;
         case BAIT_LAUNCHER -> 0.18;
         default -> 0.0;
      };
   }

   static double mountLift(Weapon var0) {
      return var0 != Weapon.SEMI_AUTO_RIFLE && var0 != Weapon.FIELD_PISTOL ? 0.0065 : 0.0;
   }

   static boolean tactical(String var0) {
      return AttachmentSpec.unmagnified(var0) || var0.equals("two_power_prism");
   }

   static double opticalAxisY(Weapon var0, String var1) {
      double var2 = opticY(var0) + mountLift(var0);
      if (var1.equals("reflex_sight")) {
         return var2 + 0.027;
      } else if (var1.equals("two_power_prism")) {
         return var2 + 0.044;
      } else if (var1.equals("micro_red_dot")) {
         return var2 + 0.036;
      } else if (tactical(var1)) {
         return var2 + 0.035;
      } else {
         return var1.isEmpty() && var0 != Weapon.TRANQUILIZER_RIFLE ? ironY(var0) : var2 + 0.0505;
      }
   }

   static double eyeZ(Weapon var0, String var1) {
      boolean var2 = var0 == Weapon.FIELD_PISTOL || var0 == Weapon.REVOLVER || var0 == Weapon.FLARE_GUN;
      if (var2) {
         return var1.isEmpty() ? 0.58 : 0.48;
      } else {
         double var3 = opticZ(var0);
         if (var1.isEmpty() && var0 != Weapon.TRANQUILIZER_RIFLE) {
            return var0 == Weapon.SEMI_AUTO_RIFLE ? 0.27 : (var0 == Weapon.BAIT_LAUNCHER ? 0.22 : 0.2);
         } else if (AttachmentSpec.unmagnified(var1)) {
            return var3 - 0.012 + 0.16;
         } else {
            return var1.equals("two_power_prism") ? var3 - 0.012 + 0.048 + 0.09 : var3 + 0.02 + 0.181 + 0.085;
         }
      }
   }

   static double[] ejection(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE -> new double[]{0.0165, 0.077, -0.004, 0.05, 0.065, 0.01};
         case SEMI_AUTO_RIFLE -> new double[]{0.014, 0.074, 0.02, 0.085, 0.03, 0.03};
         case PUMP_SHOTGUN -> new double[]{0.017, 0.07, -0.01, 0.07, 0.04, 0.015};
         default -> null;
         case SEMI_AUTO_SHOTGUN -> new double[]{0.0175, 0.068, 0.0, 0.075, 0.035, 0.02};
         case FIELD_PISTOL -> new double[]{0.021, 0.13, -0.021, 0.06, 0.075, 0.02};
      };
   }

   private FieldWeaponSockets() {
   }
}
