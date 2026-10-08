package com.formaworks.frontierhunts.wingshot;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * [wingshot] Game-bird flight and hit-effect settings. Server options live in HuntConfig's server spec (section
 * {@code wingshot}), client options in the client spec (section {@code wingshot}); one hook line each in HuntConfig.
 * Getters are safe before the config loads.
 */
public final class WingshotConfig {
   /** How much a hit bird shows: nothing extra, feathers and a little blood, or the full burst. */
   public enum HitEffects {
      OFF,
      NORMAL,
      GRAPHIC
   }

   public static ModConfigSpec.BooleanValue FLIGHT;
   public static ModConfigSpec.BooleanValue AMBIENT;
   public static ModConfigSpec.BooleanValue DRUMMING;
   public static ModConfigSpec.EnumValue<HitEffects> HIT_EFFECTS;
   public static ModConfigSpec.BooleanValue SLOW_MO;

   private WingshotConfig() {
   }

   public static void server(ModConfigSpec.Builder b) {
      FLIGHT = b.comment("Ducks and grouse really fly: puddle-duck jump take-offs, flocks in loose V/echelon, circling decoys with set wings, landing into the wind with cupped wings; grouse flush with a roar of wings, fly low through the trees and land running. Shot birds fall to the ground and are taken where they land. Off = the old short hop.")
         .define("realisticFlight", true);
      AMBIENT = b.comment("Now and then a flock of ducks near a player gets up on its own and flies to other water (most at dawn and dusk).")
         .define("ambientDuckFlights", true);
      DRUMMING = b.comment("Male ruffed grouse drum on fallen logs on spring and autumn mornings and evenings.")
         .define("grouseDrumming", true);
   }

   public static void client(ModConfigSpec.Builder b) {
      HIT_EFFECTS = b.comment("Bird hit effects: OFF (vanilla hurt only), NORMAL (a feather burst and a little blood), GRAPHIC (dozens of feathers in the bird's colours, a puff of down, blood mist and spatter on the ground, blocks and water).")
         .defineEnum("birdHitEffects", HitEffects.GRAPHIC);
      SLOW_MO = b.comment("A clean wing shot (a bird killed in the air with one shot) plays a brief slow-motion moment around the bird. Reduced motion turns it off.")
         .define("wingShotSlowMo", true);
   }

   public static boolean flight() {
      return bool(FLIGHT, true);
   }

   public static boolean ambient() {
      return flight() && bool(AMBIENT, true);
   }

   public static boolean drumming() {
      return bool(DRUMMING, true);
   }

   public static HitEffects hitEffects() {
      try {
         return HIT_EFFECTS == null ? HitEffects.GRAPHIC : HIT_EFFECTS.get();
      } catch (Throwable t) {
         return HitEffects.GRAPHIC;
      }
   }

   public static boolean slowMo() {
      return bool(SLOW_MO, true);
   }

   private static boolean bool(ModConfigSpec.BooleanValue v, boolean def) {
      try {
         return v == null ? def : v.get();
      } catch (Throwable t) {
         return def;
      }
   }
}
