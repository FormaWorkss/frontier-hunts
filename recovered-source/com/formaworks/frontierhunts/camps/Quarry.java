package com.formaworks.frontierhunts.camps;

import java.util.Locale;

/** Every animal the record book knows: the three antlered deer plus the 2026 wildlife species. */
public enum Quarry {
   WHITETAIL("whitetail", "Whitetail", "buck", "doe", Quarry.Kind.RACK, 0.0),
   ELK("elk", "Elk", "bull", "cow", Quarry.Kind.RACK, 0.0),
   MOOSE("moose", "Moose", "bull", "cow", Quarry.Kind.RACK, 0.0),
   BLACK_BEAR("black_bear", "Black bear", "boar", "sow", Quarry.Kind.BEAR, 125.0),
   GRIZZLY("grizzly", "Grizzly", "boar", "sow", Quarry.Kind.BEAR, 275.0),
   POLAR_BEAR("polar_bear", "Polar bear", "boar", "sow", Quarry.Kind.BEAR, 450.0),
   BOAR("boar", "Wild boar", "boar", "sow", Quarry.Kind.GAME, 88.0),
   BISON("bison", "Bison", "bull", "cow", Quarry.Kind.GAME, 700.0),
   PRONGHORN("pronghorn", "Pronghorn", "buck", "doe", Quarry.Kind.GAME, 52.0),
   COYOTE("coyote", "Coyote", "dog", "bitch", Quarry.Kind.PREDATOR, 14.0),
   WOLF("wolf", "Gray wolf", "dog", "bitch", Quarry.Kind.PREDATOR, 46.0),
   COUGAR("cougar", "Cougar", "tom", "queen", Quarry.Kind.PREDATOR, 62.0),
   LION("lion", "Lion", "male", "lioness", Quarry.Kind.PREDATOR, 180.0),
   PANTHER("panther", "Panther", "male", "female", Quarry.Kind.PREDATOR, 55.0),
   CHEETAH("cheetah", "Cheetah", "male", "female", Quarry.Kind.PREDATOR, 48.0),
   GROUSE("grouse", "Ruffed grouse", "bird", "bird", Quarry.Kind.BIRD, 0.6),
   DUCK("duck", "Mallard", "drake", "hen", Quarry.Kind.BIRD, 1.2);

   public final String id;
   public final String title;
   public final String male;
   public final String female;
   public final Quarry.Kind kind;
   /** Typical adult live weight in kg for species without a mass model of their own. */
   public final double baseKg;

   Quarry(String id, String title, String male, String female, Quarry.Kind kind, double baseKg) {
      this.id = id;
      this.title = title;
      this.male = male;
      this.female = female;
      this.kind = kind;
      this.baseKg = baseKg;
   }

   public boolean rack() {
      return this.kind == Quarry.Kind.RACK;
   }

   public static Quarry find(String id) {
      if (id == null) {
         return null;
      }
      String s = id.toLowerCase(Locale.ROOT);
      int colon = s.indexOf(':');
      if (colon >= 0) {
         s = s.substring(colon + 1);
      }
      for (Quarry q : values()) {
         if (q.id.equals(s)) {
            return q;
         }
      }
      return null;
   }

   /**
    * Deterministic weight for wildlife mobs (no mass model of their own): 80-122 % of the species' typical weight,
    * seeded by the animal's UUID so the same animal always weighs the same.
    */
   public double weightFor(long seed) {
      long h = seed * 0x9E3779B97F4A7C15L;
      h ^= h >>> 31;
      h *= 0xBF58476D1CE4E5B9L;
      h ^= h >>> 29;
      double a = (h & 0xFFFF) / 65535.0;
      double b = (h >>> 16 & 0xFFFF) / 65535.0;
      double c = (h >>> 32 & 0xFFFF) / 65535.0;
      double bell = (a + b + c) / 3.0; // 0..1, bell shaped around 0.5
      double w = this.baseKg * (0.8 + 0.42 * bell);
      return this.kind == Quarry.Kind.BIRD ? Math.round(w * 100.0) / 100.0 : Math.round(w * 10.0) / 10.0;
   }

   public enum Kind {
      RACK,
      BEAR,
      GAME,
      PREDATOR,
      BIRD
   }
}
