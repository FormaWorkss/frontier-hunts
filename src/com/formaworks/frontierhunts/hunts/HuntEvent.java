package com.formaworks.frontierhunts.hunts;

/**
 * [hunts] One thing that happened in the field, reduced to plain data so the milestone rules ({@link HuntBook}) can be
 * checked offline. Built on the server from real gameplay by {@link HuntContext} / {@link HuntHooks}; built by hand in the
 * harness. No Minecraft classes here.
 */
public final class HuntEvent {
   /** What kind of event: an animal seen up close, flushed, glassed, caught on a trail camera, or taken (killed / recovered). */
   public enum Kind {
      SEEN,
      FLUSH,
      GLASS,
      PHOTO,
      TAKE
   }

   /** Weapon class of the shot that took the animal. */
   public enum Gun {
      NONE,
      BOW,
      RIFLE,
      SHOTGUN,
      HANDGUN,
      OTHER
   }

   // ---------------------------------------------------------------------------------------------- flags
   /** heart / lung hit on the final shot */
   public static final int CLEAN = 1;
   /** the animal had not noticed the hunter before the first shot */
   public static final int UNAWARE = 1 << 1;
   /** the animal came to this hunter's call (grunt / bleat / rattle / predator call / duck call) shortly before the shot */
   public static final int CALLED = 1 << 2;
   /** within range of a decoy spread (duck decoys / whitetail scent decoy) */
   public static final int DECOY = 1 << 3;
   /** came to this hunter's bait site */
   public static final int BAIT = 1 << 4;
   /** the hunter was sitting in a tree stand */
   public static final int STAND = 1 << 5;
   /** the hunter was inside a ground blind or tower blind */
   public static final int BLIND = 1 << 6;
   /** the hunter's tracking hound trailed or bayed this animal */
   public static final int HOUND = 1 << 7;
   /** the hunter glassed this animal (binoculars / rangefinder) shortly before the shot */
   public static final int GLASSED = 1 << 8;
   /** a bird taken on the wing (airborne when hit) */
   public static final int FLYING = 1 << 9;
   /** a dangerous animal charging the hunter when it was stopped */
   public static final int CHARGING = 1 << 10;
   /** the hunter was on foot (or in a stand), not riding */
   public static final int ON_FOOT = 1 << 11;
   /** the species' rut was on */
   public static final int RUT = 1 << 12;
   /** the animal stood on snow */
   public static final int SNOW = 1 << 13;
   /** open water close to the animal */
   public static final int WATER = 1 << 14;
   /** high country (above Y 100) */
   public static final int HIGH = 1 << 15;
   /** the hunter wore snow camouflage */
   public static final int SNOW_CAMO = 1 << 16;
   /** one hit, one clean kill */
   public static final int ONE_SHOT = 1 << 17;
   /** recovered by following a trail */
   public static final int RECOVERED = 1 << 18;
   /** a predator watched while it hunted (stalking, chasing, feeding on a kill) */
   public static final int HUNTING = 1 << 19;
   /** male (deer: buck / bull; only used where the sex is visible) */
   public static final int MALE = 1 << 20;

   public final Kind kind;
   public final String species;
   public final int flags;
   public final Gun gun;
   /** metres: shot distance (TAKE), glassing distance (GLASS), sighting distance (SEEN / FLUSH) */
   public final double distance;
   /** live weight in kg (TAKE) */
   public final double kg;
   /** antler points, both sides (deer TAKE) */
   public final int points;
   /** animals of the same species around it (incl. itself) */
   public final int herd;
   /** reserve calendar month 0 (Jan) .. 11 (Dec) */
   public final int month;
   /** time of day 0..23999 (0 = 6 AM, 6000 = noon, 12000 = 6 PM, 18000 = midnight) */
   public final int timeOfDay;
   /** game day number (bag limits) */
   public final long day;
   /** metres of trail followed to recover it */
   public final int trail;

   private HuntEvent(Builder b) {
      this.kind = b.kind;
      this.species = b.species;
      this.flags = b.flags;
      this.gun = b.gun;
      this.distance = Double.isFinite(b.distance) ? Math.max(0.0, b.distance) : 0.0;
      this.kg = Double.isFinite(b.kg) ? Math.max(0.0, b.kg) : 0.0;
      this.points = Math.max(0, b.points);
      this.herd = Math.max(0, b.herd);
      this.month = Math.floorMod(b.month, 12);
      this.timeOfDay = Math.floorMod(b.timeOfDay, 24000);
      this.day = b.day;
      this.trail = Math.max(0, b.trail);
   }

   public boolean has(int flag) {
      return (this.flags & flag) == flag;
   }

   public boolean any(int flagMask) {
      return (this.flags & flagMask) != 0;
   }

   /** Between dusk and dawn (about 5:30 PM to 7 AM). */
   public boolean dark() {
      return this.timeOfDay >= 11500 || this.timeOfDay < 1000;
   }

   /** Full night (about 7 PM to 5 AM). */
   public boolean night() {
      return this.timeOfDay >= 13000 && this.timeOfDay < 23000;
   }

   /** Broad daylight (about 7 AM to 5 PM). */
   public boolean daylight() {
      return this.timeOfDay >= 1000 && this.timeOfDay < 11000;
   }

   /** December, January or February: prime winter fur. */
   public boolean winter() {
      return this.month == 11 || this.month == 0 || this.month == 1;
   }

   public static Builder of(Kind kind, String species) {
      return new Builder(kind, species);
   }

   @Override
   public String toString() {
      StringBuilder b = new StringBuilder(this.kind.name()).append(' ').append(this.species);
      b.append(" flags=").append(Integer.toBinaryString(this.flags)).append(' ').append(this.gun).append(' ');
      b.append(Math.round(this.distance)).append("m ").append(Math.round(this.kg)).append("kg pts=").append(this.points);
      b.append(" herd=").append(this.herd).append(" month=").append(this.month).append(" tod=").append(this.timeOfDay);
      return b.toString();
   }

   public static final class Builder {
      private final Kind kind;
      private final String species;
      private int flags;
      private Gun gun = Gun.NONE;
      private double distance;
      private double kg;
      private int points;
      private int herd = 1;
      private int month = 9;
      private int timeOfDay = 1000;
      private long day;
      private int trail;

      Builder(Kind kind, String species) {
         if (kind == null || species == null) {
            throw new IllegalArgumentException("hunt event needs a kind and a species");
         }
         this.kind = kind;
         this.species = species;
      }

      public Builder flag(int f) {
         this.flags |= f;
         return this;
      }

      public Builder flag(int f, boolean on) {
         if (on) {
            this.flags |= f;
         }
         return this;
      }

      public Builder gun(Gun g) {
         this.gun = g == null ? Gun.NONE : g;
         return this;
      }

      public Builder distance(double d) {
         this.distance = d;
         return this;
      }

      public Builder kg(double v) {
         this.kg = v;
         return this;
      }

      public Builder points(int p) {
         this.points = p;
         return this;
      }

      public Builder herd(int h) {
         this.herd = h;
         return this;
      }

      public Builder month(int m) {
         this.month = m;
         return this;
      }

      public Builder time(int tod) {
         this.timeOfDay = tod;
         return this;
      }

      public Builder day(long d) {
         this.day = d;
         return this;
      }

      public Builder trail(int m) {
         this.trail = m;
         return this;
      }

      public HuntEvent build() {
         return new HuntEvent(this);
      }
   }
}
