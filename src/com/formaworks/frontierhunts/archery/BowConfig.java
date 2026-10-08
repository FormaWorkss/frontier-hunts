package com.formaworks.frontierhunts.archery;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * [bows] Archery aiming aids (client). Defined inside HuntConfig's client spec (one hook line, see HuntConfig
 * "[bows]"), shown on the settings screen's Interface page. Getters are safe before the config loads.
 */
public final class BowConfig {
   public enum PinSet {
      PINS_10_20_30(10, 20, 30),
      PINS_20_30_40(20, 30, 40),
      PINS_20_30_40_50(20, 30, 40, 50),
      PINS_30_40_50_60(30, 40, 50, 60);

      public final int[] metres;

      PinSet(int... metres) {
         this.metres = metres;
      }

      public String title() {
         StringBuilder b = new StringBuilder();
         for (int i = 0; i < this.metres.length; i++) {
            b.append(i == 0 ? "" : " / ").append(this.metres[i]);
         }
         return b.append(" m").toString();
      }
   }

   public static ModConfigSpec.BooleanValue PIN_SIGHT;
   public static ModConfigSpec.EnumValue<PinSet> PINS;
   public static ModConfigSpec.BooleanValue PEEP;
   public static ModConfigSpec.BooleanValue TIP_REFERENCE;
   public static ModConfigSpec.BooleanValue GAP_MARKS;
   public static ModConfigSpec.BooleanValue RANGE_READOUT;

   private BowConfig() {
   }

   /** Called from HuntConfig's client builder inside push("archery"). */
   public static void client(ModConfigSpec.Builder b) {
      PIN_SIGHT = b.comment("Compound bows: a multi-pin sight, every pin calibrated to the arrow's real flight. The crossbow gets matching range dots.")
         .define("pinSight", true);
      PINS = b.comment("Distances (metres) the compound sight pins and crossbow dots are set for.").defineEnum("pins", PinSet.PINS_20_30_40);
      PEEP = b.comment("Compound bows: the peep sight ring you look through at full draw.").define("peep", true);
      TIP_REFERENCE = b.comment("Recurve and Field Recurve: at full draw a small dot shows exactly where the arrow will strike (its real flight through the world, any distance), in place of the crosshair.")
         .define("tipReference", true);
      GAP_MARKS = b.comment("Unused since the impact dot replaced the traditional gap marks (kept so old config files load).")
         .define("gapMarks", true);
      RANGE_READOUT = b.comment("With a rangefinder in your inventory: the distance to the animal under your sight while at full draw.")
         .define("rangeReadout", true);
   }

   public static boolean on(ModConfigSpec.BooleanValue v, boolean def) {
      try {
         return v == null ? def : v.get();
      } catch (Throwable t) {
         return def;
      }
   }

   public static int[] pins() {
      try {
         return PINS == null ? PinSet.PINS_20_30_40.metres : PINS.get().metres;
      } catch (Throwable t) {
         return PinSet.PINS_20_30_40.metres;
      }
   }
}
