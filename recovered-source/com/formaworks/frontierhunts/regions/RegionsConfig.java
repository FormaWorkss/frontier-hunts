package com.formaworks.frontierhunts.regions;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * [regions] Arrival cards: the cinematic region / biome title card. Client options only, defined inside HuntConfig's client
 * spec (one hook line, see HuntConfig "[regions]") so they live in frontierhunts-client.toml. Getters are safe before the
 * config loads.
 */
public final class RegionsConfig {
   /** Which crossings show the card (joining a world always shows the region you are in unless Off). */
   public enum Mode {
      OFF,
      REGIONS,
      REGIONS_AND_BIOMES
   }

   public static ModConfigSpec.EnumValue<Mode> MODE;
   public static ModConfigSpec.IntValue DURATION;
   public static ModConfigSpec.IntValue REPEAT_MINUTES;

   private RegionsConfig() {
   }

   /** Called from HuntConfig's client builder inside push("arrivalCards"). */
   public static void client(ModConfigSpec.Builder b) {
      MODE = b.comment(
            "Cinematic title card when you arrive somewhere new. OFF, REGIONS (reserve regions such as Pinewood Forest, and joining a world) or REGIONS_AND_BIOMES (also every new biome)."
         )
         .defineEnum("mode", Mode.REGIONS_AND_BIOMES);
      DURATION = b.comment("How long an arrival card stays on screen, in seconds (fades included).").defineInRange("durationSeconds", 5, 3, 8);
      REPEAT_MINUTES = b.comment("The same biome is not announced again within this many minutes (entering a different region always is).")
         .defineInRange("repeatMinutes", 5, 1, 30);
   }

   public static Mode mode() {
      try {
         return MODE == null ? Mode.REGIONS_AND_BIOMES : MODE.get();
      } catch (Throwable t) {
         return Mode.REGIONS_AND_BIOMES;
      }
   }

   public static int durationTicks() {
      try {
         return (DURATION == null ? 5 : DURATION.get()) * 20;
      } catch (Throwable t) {
         return 100;
      }
   }

   public static long repeatTicks() {
      try {
         return (REPEAT_MINUTES == null ? 5 : REPEAT_MINUTES.get()) * 1200L;
      } catch (Throwable t) {
         return 6000L;
      }
   }
}
