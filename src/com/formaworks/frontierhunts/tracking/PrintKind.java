package com.formaworks.frontierhunts.tracking;

import java.util.Locale;

/**
 * [tracking] Who made a print group. Ordinals are saved in TrailMark.kind and synced: append only, never reorder.
 * Sizes are real-world (metres); the client draws prints a little larger than life so they read at Minecraft scale.
 */
public enum PrintKind {
   DEER("Whitetail", "hoofprints", Art.DEER, Layout.PAIRED, 0.075F, 1.10F),
   ELK("Elk", "hoofprints", Art.ELK, Layout.PAIRED, 0.110F, 1.50F),
   MOOSE("Moose", "hoofprints", Art.MOOSE, Layout.PAIRED, 0.140F, 1.80F),
   BOOT("Hunter", "boot prints", Art.BOOT, Layout.BIPED, 0.290F, 1.40F),
   RABBIT("Rabbit", "hop tracks", Art.RABBIT_HIND, Layout.HOP, 0.080F, 0.95F),
   FOX("Red fox", "pawprints", Art.FOX, Layout.LINE, 0.055F, 0.70F),
   BISON("Bison", "hoofprints", Art.BISON, Layout.PAIRED, 0.130F, 1.60F),
   BOAR("Wild boar", "hoofprints", Art.BOAR, Layout.PAIRED, 0.065F, 0.80F),
   WOLF("Wolf", "pawprints", Art.CANID, Layout.LINE, 0.115F, 1.35F),
   COYOTE("Coyote", "pawprints", Art.CANID, Layout.LINE, 0.070F, 1.05F),
   COUGAR("Cougar", "pawprints", Art.FELINE, Layout.PAIRED, 0.090F, 1.10F),
   PANTHER("Panther", "pawprints", Art.FELINE, Layout.PAIRED, 0.085F, 1.00F),
   LION("Lion", "pawprints", Art.FELINE, Layout.PAIRED, 0.130F, 1.30F),
   CHEETAH("Cheetah", "pawprints", Art.FELINE, Layout.LINE, 0.080F, 1.20F),
   GRIZZLY("Grizzly", "bear tracks", Art.BEAR_HIND, Layout.BEAR, 0.270F, 1.40F),
   BLACK_BEAR("Black bear", "bear tracks", Art.BEAR_HIND, Layout.BEAR, 0.180F, 1.10F),
   POLAR_BEAR("Polar bear", "bear tracks", Art.BEAR_HIND, Layout.BEAR, 0.300F, 1.50F),
   GROUSE("Grouse", "bird tracks", Art.BIRD, Layout.BIRD, 0.055F, 0.50F),
   DUCK("Duck", "webbed tracks", Art.DUCK, Layout.BIRD, 0.065F, 0.50F),
   PRONGHORN("Pronghorn", "hoofprints", Art.DEER, Layout.PAIRED, 0.070F, 1.10F),
   HOUND("Tracking hound", "pawprints", Art.CANID, Layout.LINE, 0.085F, 1.10F);

   /** Atlas rows of textures/entity/track_prints.png */
   public enum Art {
      DEER, ELK, MOOSE, BISON, BOAR, CANID, FELINE, BEAR_FRONT, BEAR_HIND, BIRD, DUCK, RABBIT_HIND, RABBIT_FRONT, BOOT, FOX, BED;

      /** width / length of the print shape in its cell */
      public float aspect() {
         return switch (this) {
            case DEER -> 0.74F;
            case ELK -> 0.86F;
            case MOOSE -> 0.72F;
            case BISON -> 1.0F;
            case BOAR -> 0.9F;
            case CANID -> 0.8F;
            case FELINE -> 1.0F;
            case BEAR_FRONT -> 1.15F;
            case BEAR_HIND -> 0.56F;
            case BIRD -> 0.95F;
            case DUCK -> 1.0F;
            case RABBIT_HIND -> 0.42F;
            case RABBIT_FRONT -> 0.8F;
            case BOOT -> 0.38F;
            case FOX -> 0.8F;
            case BED -> 0.55F;
         };
      }
   }

   /** How the prints of one stride are arranged */
   public enum Layout {
      /** quadruped walking with hind prints registering on the fronts: one left, one right, staggered */
      PAIRED,
      /** narrow canid / fox / cheetah line: prints almost in a single file */
      LINE,
      /** bear: front and hind prints on each side */
      BEAR,
      /** hopping rabbit: two long hinds side by side ahead of two small fronts */
      HOP,
      /** birds walk: alternating three-toed prints */
      BIRD,
      /** people: left and right boots */
      BIPED
   }

   public final String label;
   public final String noun;
   public final Art art;
   public final Layout layout;
   /** real print length in metres at scale 1 */
   public final float length;
   /** real walking stride (one full gait cycle) in metres at scale 1 */
   public final float stride;

   PrintKind(String label, String noun, Art art, Layout layout, float length, float stride) {
      this.label = label;
      this.noun = noun;
      this.art = art;
      this.layout = layout;
      this.length = length;
      this.stride = stride;
   }

   public static PrintKind of(int ordinal) {
      PrintKind[] v = values();
      return ordinal >= 0 && ordinal < v.length ? v[ordinal] : DEER;
   }

   public boolean predator() {
      return switch (this) {
         case WOLF, COYOTE, COUGAR, PANTHER, LION, CHEETAH, GRIZZLY, BLACK_BEAR, POLAR_BEAR -> true;
         default -> false;
      };
   }

   /** Speed (blocks/tick) above which the gait reads as running for this animal. */
   public float runSpeed() {
      return switch (this.layout) {
         case BIRD -> 0.09F;
         case HOP -> 0.16F;
         case BIPED -> 0.2F;
         default -> 0.11F + this.stride * 0.035F;
      };
   }

   /**
    * What a tracker reads from the print size and stride: an estimate, from measurements only (it can be wrong,
    * like a real one).
    */
   public String estimate(float scale, float strideM, int activity) {
      float cm = this.length * scale * 100.0F;
      String size = String.format(Locale.ROOT, "%.1f cm print", cm);
      String who;
      float r = scale;
      switch (this) {
         case DEER -> who = cm < 5.8F ? "a fawn or yearling" : cm < 7.2F ? "doe-sized" : cm < 8.3F ? "big print, likely a buck" : "very big, a mature buck";
         case ELK -> who = cm < 9.5F ? "a calf or young cow" : cm < 11.5F ? "cow-sized" : "big and round, likely a bull";
         case MOOSE -> who = cm < 12.0F ? "a calf" : cm < 14.8F ? "a cow moose" : "huge, likely a bull";
         case PRONGHORN -> who = cm < 6.2F ? "a young pronghorn" : "an adult pronghorn";
         case BISON -> who = r < 0.92F ? "a cow or young bull" : "a heavy bull";
         case BOAR -> who = r < 0.9F ? "a young pig" : r > 1.08F ? "a big old boar" : "an adult hog";
         case WOLF -> who = cm < 10.0F ? "a young or female wolf" : "a big wolf";
         case COYOTE -> who = "coyote-sized, too small for a wolf";
         case FOX -> who = "fox-sized, dainty and in a line";
         case HOUND -> who = "dog-sized, claws showing";
         case GRIZZLY, BLACK_BEAR, POLAR_BEAR -> who = r < 0.85F ? "a young bear" : r > 1.1F ? "a big boar bear" : "an adult bear";
         case COUGAR, PANTHER, CHEETAH, LION -> who = r < 0.85F ? "a young cat" : "an adult cat, no claw marks";
         case BOOT -> who = "a hunter's boot";
         default -> who = r < 0.85F ? "a small one" : "an adult";
      }
      String gait = switch (activity) {
         case 2 -> this.layout == Layout.HOP ? "bounding" : (this.art == Art.DEER || this.art == Art.ELK || this.art == Art.MOOSE ? "running, hooves splayed" : "running");
         case 1 -> this.layout == Layout.HOP ? "hopping" : "walking";
         default -> "milling about";
      };
      String stride = strideM > 0.05F ? String.format(Locale.ROOT, " · %.1f m stride", strideM) : "";
      return size + " — " + who + " · " + gait + stride;
   }
}
