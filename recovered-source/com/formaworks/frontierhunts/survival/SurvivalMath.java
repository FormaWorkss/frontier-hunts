package com.formaworks.frontierhunts.survival;

/**
 * [survival] Pure maths of Frontier Survival (no Minecraft types, unit-tested by tools/survival/harness).
 *
 * Units: the three nutrition meters run 0..100. Body heat H runs -100 (frozen) .. 0 (comfortable) .. +100 (heat stroke).
 * Air temperatures are degrees Celsius. Time is in ticks (24000 per Minecraft day).
 */
public final class SurvivalMath {
   private SurvivalMath() {
   }

   public static final long DAY = 24000L;

   // ============================================================================================ difficulty tuning

   /** Numbers per difficulty. OFF disables everything (the tuning is never consulted). */
   public enum Mode {
      OFF(0F, 1F, 1F, 1F, false, 0, 0, 0F, 0F, 0F, 1F),
      LIGHT(0.7F, 1F, 1F, 1F, false, 0, 0, 0.6F, 0F, 0F, 1F),
      BALANCED(1.0F, 0.8F, 0.95F, 1F, true, 600, 0, 1.0F, -88F, 80F, 0.25F),
      HARDCORE(1.25F, 0.5F, 0.75F, 0.8F, true, 200, 400, 1.3F, -80F, 50F, 0F);

      /** Multiplier on every nutrition drain. */
      public final float drain;
      /** Share of protein/fat a farm (domestic) food still gives. */
      public final float farm;
      /** Share of energy crops, bread and other farmed plants still give. */
      public final float crop;
      /** Share of fish nutrition. */
      public final float fish;
      /** Deficits slow you down and weaken you (LIGHT: buffs only). */
      public final boolean debuffs;
      /** Ticks between 1 HP of malnutrition damage when two meters are empty (0 = never). */
      public final int starveTwo;
      /** Ticks between 1 HP of malnutrition damage when one meter is empty (0 = never). */
      public final int starveOne;
      /** Multiplier on how fast the body loses heat. */
      public final float cold;
      /** Body heat at or below which freezing hurts (0 = never). */
      public final float freezeAt;
      /** Ticks between 1 HP of freezing damage (scaled by 100 / ticks at -100). */
      public final float freezeTicks;
      /** Natural healing kept while protein is empty. */
      public final float wastingRegen;

      Mode(float drain, float farm, float crop, float fish, boolean debuffs, int starveTwo, int starveOne, float cold, float freezeAt, float freezeTicks,
         float wastingRegen) {
         this.drain = drain;
         this.farm = farm;
         this.crop = crop;
         this.fish = fish;
         this.debuffs = debuffs;
         this.starveTwo = starveTwo;
         this.starveOne = starveOne;
         this.cold = cold;
         this.freezeAt = freezeAt;
         this.freezeTicks = freezeTicks;
         this.wastingRegen = wastingRegen;
      }

      public boolean on() {
         return this != OFF;
      }

      /** Malnutrition never takes the last half heart in BALANCED (like vanilla starvation on Normal). */
      public float starveFloor() {
         return this == HARDCORE ? 0F : 1F;
      }

      public static Mode byId(int id) {
         Mode[] v = values();
         return id >= 0 && id < v.length ? v[id] : OFF;
      }
   }

   // ============================================================================================ nutrition

   /** Meter levels (0..100). */
   public static final float LOW = 30F;
   public static final float EMPTY = 8F;

   /** Idle drain per Minecraft day at BALANCED, per meter (protein, fat, energy). */
   public static final float[] DAILY = {28F, 18F, 40F};
   /** Extra drain per point of vanilla exhaustion (sprinting, mining, fighting, jumping, healing). */
   public static final float[] PER_EXHAUSTION = {0.25F, 0.25F, 0.8F};

   /**
    * Drain of one meter over {@code seconds}: idle share + work (vanilla exhaustion spent) + cold (shivering burns fat and
    * energy) + heat (sweating costs energy).
    */
   public static float drain(int meter, float seconds, float exhaustion, float coldness, float hotness, float modeDrain, float configScale) {
      float idle = DAILY[meter] * seconds * 20F / DAY;
      float work = PER_EXHAUSTION[meter] * Math.max(0F, exhaustion);
      float shiver = switch (meter) {
         case 1 -> 0.030F;
         case 2 -> 0.045F;
         default -> 0.006F;
      } * clamp(coldness, 0F, 1F) * seconds;
      float sweat = meter == 2 ? 0.02F * clamp(hotness, 0F, 1F) * seconds : 0F;
      return (idle + work + shiver + sweat) * modeDrain * configScale;
   }

   /** Number of meters at or below EMPTY. */
   public static int empties(float protein, float fat, float energy) {
      return (protein <= EMPTY ? 1 : 0) + (fat <= EMPTY ? 1 : 0) + (energy <= EMPTY ? 1 : 0);
   }

   /** Vanilla exhaustion added per second so the hunger bar keeps pace with the meters (you can eat again). */
   public static final float METABOLISM = 0.05F;

   /** Hunter's Vigor: well fed AND a meal of wild game within the last day and a half. */
   public static boolean vigor(float protein, float fat, float energy, long sinceGameMeal) {
      return protein >= 65F && fat >= 55F && energy >= 50F && sinceGameMeal >= 0 && sinceGameMeal <= DAY * 3 / 2;
   }

   /**
    * Nutrition multiplier from how far a food has gone: full value until 60 % of its shelf life, then down to 70 %.
    */
   public static float freshnessValue(float spoil) {
      return spoil <= 0.6F ? 1F : 1F - 0.3F * clamp((spoil - 0.6F) / 0.4F, 0F, 1F);
   }

   // ============================================================================================ air temperature

   /**
    * Summer daytime air temperature (deg C) for a biome climate temperature (vanilla scale: frozen peaks -0.7, snowy plains
    * 0, taiga 0.25, plains 0.8, jungle 0.95, desert 2.0).
    */
   public static float summerTemperature(float biome) {
      float[] b = {-0.7F, -0.5F, 0.0F, 0.25F, 0.5F, 0.7F, 0.8F, 0.95F, 1.2F, 2.0F};
      float[] t = {-15F, -9F, -2F, 10F, 14F, 17F, 20F, 26F, 28F, 36F};
      if (biome <= b[0]) {
         return t[0] + (biome - b[0]) * 12F;
      }
      for (int i = 1; i < b.length; i++) {
         if (biome <= b[i]) {
            float f = (biome - b[i - 1]) / (b[i] - b[i - 1]);
            return t[i - 1] + (t[i] - t[i - 1]) * f;
         }
      }
      return Math.min(48F, t[t.length - 1] + (biome - 2F) * 6F);
   }

   /** 0 in mid July .. 1 in mid January (smooth). {@code yearPos} 0..12 months (0 = Jan 1). */
   public static float coldSeason(double yearPos) {
      return (float) ((1.0 - Math.cos((yearPos - 6.5) / 12.0 * Math.PI * 2.0)) * 0.5);
   }

   /** How much colder than summer it gets in mid winter, by biome kind. */
   public static float seasonalSwing(boolean seasonal, float biome) {
      if (seasonal) {
         return 22F;
      }
      return biome < 0.15F ? 10F : (biome >= 0.9F ? 6F : 12F);
   }

   /**
    * Day/night offset: coldest just before dawn, warmest mid afternoon. {@code dayTime} 0..24000 (0 = 6 am). Dry country
    * swings much further.
    */
   public static float diurnal(long dayTime, boolean dry) {
      double hours = (Math.floorMod(dayTime, DAY) / 1000.0 + 6.0) % 24.0;
      double c = Math.cos((hours - 15.0) / 24.0 * Math.PI * 2.0); // +1 at 15:00, -1 at 03:00
      return (float) (c * (dry ? 10.0 : 4.5));
   }

   /** Lapse rate above y=80: about -0.08 deg per block. */
   public static float altitude(int y) {
      return y > 80 ? -(y - 80) * 0.08F : 0F;
   }

   /** Air temperature before shelter, fire, wind and wetness. */
   public static float air(float biome, boolean seasonal, boolean seasonsOn, double yearPos, long dayTime, int y, boolean dry) {
      float t = summerTemperature(biome);
      if (seasonsOn) {
         t -= seasonalSwing(seasonal, biome) * coldSeason(yearPos);
      }
      return t + diurnal(dayTime, dry) + altitude(y);
   }

   /**
    * [clothing] Share of the driven rain / snow chill ({@code Thermal.weatherCold}) that gets through the outfit: a shell
    * that keeps wind or water out keeps most of a blizzard's sting out too (plain clothes 1.0, insulated coveralls ~0.75,
    * a fur outfit ~0.5).
    */
   public static float weatherShield(float windProof, float waterProof) {
      return 1F - 0.6F * clamp(Math.max(windProof, waterProof), 0F, 0.95F);
   }

   /** Wind chill in degrees (positive number to subtract). */
   public static float windChill(float windSpeed, float exposure, float windProof) {
      return Math.max(0F, windSpeed) * 1.25F * clamp(exposure, 0F, 1F) * (1F - clamp(windProof, 0F, 0.95F));
   }

   // ============================================================================================ [shelter] shelter and fire

   /**
    * [shelter] Still air inside a shelter. Caves and cellars settle toward ground temperature (11 deg C) by how little sky
    * light reaches ({@code under} = 1 - sky/15, only under a roof); a roof, an enclosed room and above all a small tent
    * hold body heat: up to +2 / +6 / +9 deg C while it is cold out (fading out above 18 deg C, shelter is no oven in summer).
    * Inputs are the shared shelter detector's roof, enclosure and tent values (0..1).
    */
   public static float shelterAir(float air, float under, float roof, float enclosure, float tent) {
      float t = air + (11F - air) * clamp(under, 0F, 1F) * 0.85F * clamp(roof, 0F, 1F);
      // [1.2.5] a closed, roofed room (a house, a cabin, a tent) shuts the cold out: inside it the air sits well above
      // the frost outside, toward 12 deg C, before your body heat and any fire add to it
      if (t < 12F) {
         t += (12F - t) * 0.8F * clamp(roof, 0F, 1F) * clamp(Math.max(enclosure, tent), 0F, 1F);
      }
      float cold = clamp((18F - t) / 10F, 0F, 1F);
      return t + (2F * clamp(roof, 0F, 1F) + 6F * clamp(enclosure, 0F, 1F) + 9F * clamp(tent, 0F, 1F)) * cold;
   }

   /**
    * [shelter] A fire inside an enclosed space heats the room air toward 20 deg C (a campfire or a stove within a few blocks:
    * 90% of the way, whatever the storm outside; the first 3 degrees of "fire" - torches, lanterns - do not count); a
    * doorway or a missing wall lets that heat out.
    */
   public static float heatedAir(float still, float enclosure, float fire) {
      if (still >= 20F) {
         return still;
      }
      float k = clamp((fire - 3F) / 8F, 0F, 1F) * clamp(enclosure, 0F, 1F) * 0.9F; // a few torches light a room, they do not heat it
      return still + (20F - still) * k;
   }

   /** [shelter] Radiant warmth of nearby fires on the body; indoors part of it already went into the room air. */
   public static float radiant(float fire, float enclosure) {
      return Math.max(0F, fire) * (1F - 0.4F * clamp(enclosure, 0F, 1F));
   }

   /** [1.2.5] Dug in (a hole, a trench, a snow pit): the ground around you holds the air toward 6 deg C. */
   public static float dugInAir(float air) {
      return air < 6F ? air + (6F - air) * 0.65F : air;
   }

   /** [shelter] Blankets while asleep: bed / cot +10, hide bedroll +18, and a tent around you +4 more. */
   public static float sleepWarmth(boolean bedroll, boolean tent) {
      return (bedroll ? 18F : 10F) + (tent ? 4F : 0F);
   }

   // ============================================================================================ body heat

   /** Lowest air temperature that still feels comfortable with this much insulation (bare clothes = 1.0). */
   public static float comfortLow(float insulation) {
      return 15F - 5F * insulation;
   }

   /**
    * [clothing] Working hard in heavy clothes lowers the comfortable ceiling (deg C): sprinting ({@code work} 1) in a full
    * fur outfit (insulation ~6.8) takes ~8.6 deg off, so a bear coat gets hot on a mild day while you run and cools off
    * once you stop; light clothing (insulation <= 2) is unaffected. Gentle: heat builds at 0.025 per degree per second.
    */
   public static float exertion(float work, float insulation) {
      return clamp(work, 0F, 1F) * Math.min(9F, 1.8F * Math.max(0F, insulation - 2F));
   }

   /** Highest comfortable temperature; heavy clothing makes heat worse. */
   public static float comfortHigh(float insulation) {
      return 31F - 1.5F * Math.max(0F, insulation - 1F);
   }

   /**
    * One step of body heat. {@code felt} is the felt air temperature, {@code work} 0..1 how hard the player is moving
    * (sprinting keeps you warm), {@code fuel} 0..1 how much energy the body has to burn.
    */
   public static float stepHeat(float heat, float felt, float insulation, float seconds, float coldMult, float work, float fuel) {
      return stepHeat(heat, felt, insulation, seconds, coldMult, work, fuel, 0F);
   }

   /**
    * [shelter] Same, with {@code shelter} 0..1 (the shared detector's enclosure): inside a tent or a closed room the body
    * loses heat up to 50% slower in cold air and rewarms up to 1.8x faster in comfortable air (out of the wind, the warmth
    * stays with you).
    */
   public static float stepHeat(float heat, float felt, float insulation, float seconds, float coldMult, float work, float fuel, float shelter) {
      float lo = comfortLow(insulation);
      float hi = comfortHigh(insulation) - exertion(work, insulation); // [clothing] hard work in heavy clothes
      float d;
      if (felt < lo) {
         d = -(lo - felt) * 0.022F * coldMult * (1F - 0.5F * clamp(shelter, 0F, 1F)); // [shelter] still air: slower heat loss
         d += 0.12F * work * fuel; // exercise makes heat
         if (heat > 0F) {
            d -= 0.4F; // shed stored heat first
         }
      } else if (felt > hi) {
         d = (felt - hi) * 0.025F;
         if (heat < 0F) {
            d += 0.6F;
         }
      } else {
         // comfortable: drift back to zero, faster the warmer the surroundings are relative to the comfort floor
         float rate = (0.35F + (felt - lo) * 0.03F) * (1F + 0.8F * clamp(shelter, 0F, 1F)); // [shelter] faster rewarm in shelter
         d = heat < 0F ? Math.min(-heat / seconds, rate) : -Math.min(heat / seconds, 0.5F);
      }
      if (fuel < 0.1F && felt < lo) {
         d -= 0.08F; // nothing left to burn
      }
      return clamp(heat + d * seconds, -100F, 100F);
   }

   /** Displayed core temperature in deg C. */
   public static float coreTemperature(float heat) {
      return 37.0F + heat * (heat < 0F ? 0.045F : 0.04F);
   }

   public static final float CHILLY = -30F;
   public static final float SHIVER = -50F;
   public static final float HYPOTHERMIA = -75F;
   public static final float HOT = 50F;
   public static final float HEATSTROKE = 80F;

   /** 0..1 coldness for drains/visuals (0 at CHILLY, 1 at -100). */
   public static float coldness(float heat) {
      return clamp((CHILLY - heat) / (100F + CHILLY), 0F, 1F);
   }

   public static float hotness(float heat) {
      return clamp((heat - 30F) / 70F, 0F, 1F);
   }

   /** Aim sway multiplier from shivering and tiredness. */
   public static float sway(float heat, float energy) {
      float s = 1F;
      if (heat <= CHILLY) {
         s += 0.25F + 2.0F * clamp((CHILLY - heat) / 45F, 0F, 1F);
      }
      if (energy <= LOW) {
         s += 0.35F * clamp((LOW - energy) / LOW, 0F, 1F);
      }
      return s;
   }

   // ============================================================================================ lean seasons

   /** Body condition of game by month (mid-month samples, Jan..Dec): fat in fall, lean by late winter. */
   private static final float[] CONDITION = {0.90F, 0.82F, 0.76F, 0.80F, 0.88F, 0.96F, 1.02F, 1.08F, 1.14F, 1.20F, 1.16F, 1.02F};

   /** Seasonal body condition 0.76 (lean, March) .. 1.20 (fattest, October). */
   public static float condition(double yearPos) {
      double p = ((yearPos - 0.5) % 12.0 + 12.0) % 12.0;
      int i = (int) Math.floor(p);
      float f = (float) (p - i);
      float a = CONDITION[i % 12], b = CONDITION[(i + 1) % 12];
      return a + (b - a) * f;
   }

   /** Condition of one animal: the season times its own condition trait (0..100, 50 average). */
   public static float animalCondition(double yearPos, int trait) {
      return condition(yearPos) * (0.85F + 0.3F * clamp(trait / 100F, 0F, 1F));
   }

   /** Meat yield multiplier 0.8 (lean) .. 1.12 (prime). */
   public static float meatYield(float condition) {
      return 0.8F + 0.32F * clamp((condition - 0.72F) / 0.5F, 0F, 1F);
   }

   /** Fat trimmings for a carcass of {@code massKg}: none from a lean animal, plenty from a fall-fat one. */
   public static int fatYield(float condition, int massKg, float fatty) {
      float c = clamp((condition - 0.84F) / 0.36F, 0F, 1F);
      return Math.round(c * c * (float) Math.sqrt(Math.max(20, massKg) / 20F) * fatty);
   }

   /** Share of natural spawns turned away (thin herds in late winter, full in summer and fall). */
   public static float spawnDenial(double yearPos) {
      float c = condition(yearPos);
      return clamp((0.95F - c) / 0.19F, 0F, 1F) * 0.4F;
   }

   /** Season phase for hints: 0 fall plenty, 1 early winter, 2 lean late winter, 3 spring recovery, 4 summer. */
   public static int yieldPhase(double yearPos) {
      int m = (int) Math.floor(((yearPos % 12.0) + 12.0) % 12.0);
      if (m >= 8 && m <= 10) {
         return 0;
      }
      if (m == 11 || m == 0) {
         return 1;
      }
      if (m <= 2) {
         return 2;
      }
      return m <= 4 ? 3 : 4;
   }

   // ============================================================================================ spoilage

   /** Freshness store classes: how fast the place an item sits in spoils food. */
   public static final float[] STORE_RATE = {1.0F, 1.5F, 0.6F, 0.3F, 0.04F};
   public static final int AMBIENT = 0, WARM = 1, COOL = 2, COLD = 3, FROZEN = 4;

   public static int storeFor(float air) {
      if (air <= -4F) {
         return FROZEN;
      }
      if (air <= 2F) {
         return COLD;
      }
      if (air <= 10F) {
         return COOL;
      }
      return air >= 26F ? WARM : AMBIENT;
   }

   /** Preservation multipliers on shelf life: none, smoked, salt-cured. */
   public static final float[] CURE_SHELF = {1F, 4F, 5F};
   public static final int RAW = 0, SMOKED = 1, SALTED = 2;

   /** Bucket new freshness stamps to quarter-hours of game time so meat from one hunt stacks. */
   public static final long BUCKET = 6000L;
   /** Re-based stamps are snapped to one minute (always towards older). */
   public static final long SNAP = 1200L;

   public static long stamp(long now) {
      return Math.floorDiv(Math.max(0L, now), BUCKET) * BUCKET;
   }

   /** Spoil fraction 0 (fresh) .. 1 (spoiled). Shelf 0 = never spoils. */
   public static float spoil(long made, long now, int store, float shelfTicks) {
      if (shelfTicks <= 0F) {
         return 0F;
      }
      double age = Math.max(0L, now - made) * (double) STORE_RATE[clampStore(store)];
      return (float) Math.min(1.0, age / shelfTicks);
   }

   /** Time stamp that keeps the current spoil fraction when an item moves from one store to another. */
   public static long rebase(long made, long now, int from, int to) {
      if (from == to) {
         return made;
      }
      double age = Math.max(0L, now - made) * (double) STORE_RATE[clampStore(from)] / (double) STORE_RATE[clampStore(to)];
      long m = now - (long) Math.ceil(age);
      return Math.floorDiv(m, SNAP) * SNAP;
   }

   /** Ticks left until spoiled at the current store. */
   public static long ticksLeft(long made, long now, int store, float shelfTicks) {
      if (shelfTicks <= 0F) {
         return Long.MAX_VALUE;
      }
      double left = (1.0 - spoil(made, now, store, shelfTicks)) * shelfTicks / STORE_RATE[clampStore(store)];
      return (long) Math.max(0.0, left);
   }

   /**
    * Count-weighted average of two stamps (merging stacks): spoilage is conserved, never improved by more than the
    * average. Snapped towards older.
    */
   public static long merge(long madeA, int countA, long madeB, int countB) {
      long total = Math.max(1, countA + countB);
      double avg = ((double) madeA * countA + (double) madeB * countB) / total;
      return Math.floorDiv((long) Math.floor(avg), SNAP) * SNAP;
   }

   public static int clampStore(int s) {
      return s < 0 ? 0 : Math.min(s, STORE_RATE.length - 1);
   }

   public static float clamp(float v, float lo, float hi) {
      return v < lo ? lo : (v > hi ? hi : v);
   }
}
