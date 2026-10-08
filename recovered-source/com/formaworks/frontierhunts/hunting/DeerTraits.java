package com.formaworks.frontierhunts.hunting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public record DeerTraits(GameSpecies species, boolean buck, int ageMonths, int frame, int condition, int rackGenes, int seed, int abnormal, int coat) {
   public static final int SCHEMA = 2;
   /** [1.1.6] the freaks' frame and antler genes (the wild herd stays at 180 or less) */
   public static final int FREAK_FRAME = 228, FREAK_GENES = 226;
   /** [1.1.6] how much a buck's or bull's antler genes pull its frame: big racks grow on big bodies */
   static float bodyRackPull(GameSpecies sp) {
      return sp == GameSpecies.MOOSE ? 0.25F : sp == GameSpecies.ELK ? 0.3F : 0.35F;
   }
   public static final float RIG_TO_TRUE_X = 0.98045F;
   public static final float RIG_TO_TRUE_Y = 0.75537F;
   public static final float RIG_TO_TRUE_Z = 0.91215F;
   public static final DeerTraits REFERENCE = new DeerTraits(GameSpecies.WHITETAIL, true, 54, 50, 75, 70, 0, 0, 60);
   public static final float GAME_SCALE_XZ = GameSpecies.WHITETAIL.gameScaleXZ;
   public static final float GAME_SCALE_Y = GameSpecies.WHITETAIL.gameScaleY;

   public DeerTraits(GameSpecies species, boolean buck, int ageMonths, int frame, int condition, int rackGenes, int seed, int abnormal, int coat) {
      if (species == null) {
         species = GameSpecies.WHITETAIL;
      }

      ageMonths = Mth.clamp(ageMonths, 12, 132);
      frame = Mth.clamp(frame, 0, FREAK_FRAME); // [1.1.6] the herd tops out at 180; above that only the freaks (freak.FreakQuest)
      condition = Mth.clamp(condition, 0, 100);
      rackGenes = Mth.clamp(rackGenes, 0, FREAK_GENES);
      abnormal = buck ? Mth.clamp(abnormal, 0, 40) : 0;
      coat = Mth.clamp(coat, 0, 99);
      this.species = species;
      this.buck = buck;
      this.ageMonths = ageMonths;
      this.frame = frame;
      this.condition = condition;
      this.rackGenes = rackGenes;
      this.seed = seed;
      this.abnormal = abnormal;
      this.coat = coat;
   }

   public DeerTraits(boolean var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8) {
      this(GameSpecies.WHITETAIL, var1, var2, var3, var4, var5, var6, var7, var8);
   }

   public DeerTraits(boolean var1, int var2, int var3, int var4, int var5, int var6) {
      this(var1, var2, var3, var4, var5, var6, legacyAbnormal(var1, var2, var5, var6), Math.floorMod(var6 ^ 1540483477, 100));
   }

   public static DeerTraits reference(GameSpecies var0) {
      return var0 == GameSpecies.WHITETAIL ? REFERENCE : new DeerTraits(var0, true, 54, 50, 75, 70, 0, 0, 60);
   }

   public DeerTraits withSpecies(GameSpecies var1) {
      return var1 == this.species
         ? this
         : new DeerTraits(var1, this.buck, this.ageMonths, this.frame, this.condition, this.rackGenes, this.seed, this.abnormal, this.coat);
   }

   private static int legacyAbnormal(boolean var0, int var1, int var2, int var3) {
      if (var0 && var1 >= 36 && var3 != 0) {
         int var4 = Math.floorMod(var3 * 31 + 7, 100);
         return var4 < 72
            ? 0
            : (var4 < 92 ? 1 + Math.floorMod(var3, 3) : (var4 < 98 ? 4 + Math.floorMod(var3, 6) : 10 + Math.floorMod(var3, 9) + (var2 > 120 ? 6 : 0)));
      } else {
         return 0;
      }
   }

   public static DeerTraits random(RandomSource var0) {
      return random(var0, var0.nextFloat() < 0.5F);
   }

   public static DeerTraits random(RandomSource var0, boolean var1) {
      return random(GameSpecies.WHITETAIL, var0, var1);
   }

   public static DeerTraits random(GameSpecies var0, RandomSource var1, boolean var2) {
      float var3 = var1.nextFloat();
      int var4 = var3 < 0.3F ? 12 + var1.nextInt(12) : (var3 < 0.72F ? 24 + var1.nextInt(24) : (var3 < 0.96F ? 48 + var1.nextInt(36) : 84 + var1.nextInt(37)));
      int var5 = (var1.nextInt(101) + var1.nextInt(101)) / 2;
      int var6 = (var1.nextInt(101) + var1.nextInt(101)) / 2;
      if (var4 >= 48 && var1.nextFloat() < (var2 ? 0.025F : 0.015F)) {
         var5 = 115 + var1.nextInt(66);
      }

      if (var2 && var4 >= 36 && var1.nextFloat() < 0.035F) {
         var6 = 105 + var1.nextInt(76);
      }

      int var7 = 0;
      if (var2) {
         float var8 = var1.nextFloat();
         if (var4 < 36) {
            var7 = var8 < 0.88F ? 0 : (var8 < 0.99F ? 1 + var1.nextInt(2) : 3 + var1.nextInt(3));
         } else {
            var7 = var8 < 0.7F
               ? 0
               : (var8 < 0.9F ? 1 + var1.nextInt(3) : (var8 < 0.97F ? 4 + var1.nextInt(6) : (var8 < 0.995F ? 10 + var1.nextInt(9) : 19 + var1.nextInt(16))));
            if (var7 >= 10) {
               var6 = Math.max(var6, 110 + var1.nextInt(60));
            }
         }
      }

      int var9 = var1.nextInt(100);
      if (var0.antlers != GameSpecies.Antlers.PROCEDURAL_WHITETAIL) {
         var7 = 0;
      }

      if (var0.meshAntlers() && var2) {
         var4 = Math.max(var4, 26 + var1.nextInt(10));
      }

      if (var2) { // [1.1.6] bigger antlers come on bigger bodies (and a poor rack on a lighter frame)
         var5 = Mth.clamp(var5 + Math.round(bodyRackPull(var0) * (var6 - 70)), 0, 180);
      }

      return new DeerTraits(var0, var2, var4, var5, 40 + var1.nextInt(61), var6, var1.nextInt(), var7, var9);
   }

   public DeerTraits withSex(boolean var1, RandomSource var2) {
      return var1 == this.buck ? this : random(this.species, var2, var1);
   }

   public boolean yearling() {
      return this.ageMonths < 24;
   }

   public String ageClass() {
      return this.ageMonths < 24 ? "Yearling" : (this.ageMonths < 48 ? "Young adult" : (this.ageMonths < 84 ? "Mature" : "Old"));
   }

   public String description() {
      return this.ageClass() + " " + this.species.sexName(this.buck);
   }

   public float maturity() {
      return Mth.clamp((float)(this.ageMonths - 12) / 42.0F, 0.0F, 1.0F);
   }

   public float naturalHeight() {
      return (this.buck ? 0.88F + 0.12F * this.maturity() : 0.83F + 0.085F * this.maturity()) * (1.0F + (float)(this.frame - 50) * 0.0014F);
   }

   public float naturalLength() {
      return (this.buck ? 0.83F + 0.17F * this.maturity() : 0.79F + 0.115F * this.maturity()) * (1.0F + (float)(this.frame - 50) * 0.0018F);
   }

   public float naturalWidth() {
      return (this.buck ? 0.77F + 0.23F * this.maturity() : 0.72F + 0.13F * this.maturity())
         * (1.0F + (float)(this.frame - 50) * 0.002F + (float)(this.condition - 75) * 0.0012F);
   }

   public float frameHeight() {
      return this.naturalHeight() * this.species.gameScaleY;
   }

   public float frameLength() {
      return this.naturalLength() * this.species.gameScaleXZ;
   }

   public float frameWidth() {
      return this.naturalWidth() * this.species.gameScaleXZ;
   }

   public float heightScale() {
      return this.frameHeight() * 0.75537F;
   }

   public float lengthScale() {
      return this.frameLength() * 0.91215F;
   }

   public float widthScale() {
      return this.frameWidth() * 0.98045F;
   }

   public float neckGirth() {
      if (this.species.meshAntlers()) {
         return this.buck ? 0.94F + 0.08F * this.maturity() + (float)(this.condition - 75) * 0.001F : 0.88F;
      } else {
         return !this.buck ? 0.8F : 0.86F + 0.2F * this.maturity() + (float)(this.condition - 75) * 0.0015F;
      }
   }

   public float headScale() {
      return this.buck ? 1.0F : 0.93F;
   }

   public int massKg() {
      return Math.round(this.species.referenceMassKg * this.naturalHeight() * this.naturalLength() * this.naturalWidth());
   }

   public int typicalPoints(boolean var1) {
      if (!this.buck) {
         return 0;
      } else if (this.species.meshAntlers()) {
         int var4 = this.species.behavior.points() / 2;
         float var3 = this.rackScale();
         return Math.max(2, Math.round((float)var4 * Math.min(1.0F, 0.45F + 0.6F * var3)));
      } else {
         int var2;
         if (this.yearling()) {
            var2 = this.rackGenes < 40 ? 1 : (this.rackGenes < 70 ? 2 : (this.rackGenes < 95 ? 3 : 4));
         } else if (this.ageMonths < 36) {
            var2 = this.rackGenes < 25 ? 2 : (this.rackGenes < 60 ? 3 : (this.rackGenes < 115 ? 4 : 5));
         } else {
            var2 = this.rackGenes < 20 ? 3 : (this.rackGenes < 50 ? 4 : (this.rackGenes < 110 ? 5 : (this.rackGenes < 150 ? 6 : 7)));
         }

         if (this.ageMonths > 100 && Math.floorMod(this.seed ^ 739982445, 3) == 0) {
            var2--;
         }

         if (this.seed != 0 && var2 > 2 && Math.floorMod(this.seed ^ (var1 ? 174913 : 403477), 17) == 0) {
            var2--;
         }

         return Math.max(1, var2);
      }
   }

   public int abnormalPoints(boolean var1) {
      if (this.buck && this.abnormal != 0) {
         int var2 = 30 + Math.floorMod(this.seed ^ 19088743, 41);
         int var3 = Math.round((float)(this.abnormal * var2) / 100.0F);
         return var1 ? this.abnormal - var3 : var3;
      } else {
         return 0;
      }
   }

   public int points(boolean var1) {
      return this.buck ? this.typicalPoints(var1) + this.abnormalPoints(var1) : 0;
   }

   public int totalPoints() {
      return this.points(false) + this.points(true);
   }

   public boolean nonTypical() {
      return this.abnormal >= 4;
   }

   public float rackScale() {
      if (!this.buck) {
         return 0.0F;
      } else {
         float var1 = this.yearling() ? 0.37F : (this.ageMonths < 36 ? 0.6F : (this.ageMonths < 48 ? 0.82F : 1.0F));
         return var1 * (1.0F + (float)(this.rackGenes - 70) * 0.0032F) * (this.ageMonths > 96 ? 0.94F : 1.0F);
      }
   }

   public float spreadScale() {
      return this.seed == 0 ? 1.0F : 1.0F + (float)(Math.floorMod(this.seed, 101) - 50) * 0.0022F;
   }

   public float rackHeightScale() {
      float var10000;
      if (this.seed == 0) {
         var10000 = 1.0F;
      } else {
         switch (Math.floorMod(this.seed, 4)) {
            case 1:
               var10000 = 1.14F;
               break;
            case 2:
               var10000 = 0.89F;
               break;
            default:
               var10000 = 1.0F;
         }
      }

      return var10000;
   }

   public float rackDepthScale() {
      float var10000;
      if (this.seed == 0) {
         var10000 = 1.0F;
      } else {
         switch (Math.floorMod(this.seed, 4)) {
            case 0:
               var10000 = 1.18F;
               break;
            case 1:
            default:
               var10000 = 1.0F;
               break;
            case 2:
               var10000 = 1.22F;
               break;
            case 3:
               var10000 = 0.86F;
         }
      }

      return var10000;
   }

   public float sideScale(boolean var1) {
      return this.seed == 0 ? 1.0F : 1.0F + (float)(Math.floorMod(this.seed ^ (var1 ? 98931 : 422185), 101) - 50) * 9.0E-4F;
   }

   public float rackMass() {
      return !this.buck ? 0.0F : (0.55F + 0.45F * this.maturity()) * (1.0F + (float)(this.rackGenes - 70) * 0.0025F) * (0.9F + (float)this.condition * 0.002F);
   }

   public boolean greyCoat() {
      // [seasons] the coat follows the season (each animal moults on its own date); random trait when seasons are off
      return com.formaworks.frontierhunts.season.SeasonCoats.grey(this.coat);
   }

   public float coatWarmth() {
      return this.seed == 0 ? 0.0F : (float)(Math.floorMod(this.seed ^ 182042, 1001) - 500) / 500.0F;
   }

   public float coatShade() {
      return this.seed == 0 ? 1.0F : 1.0F + (float)(Math.floorMod(this.seed ^ 476089, 101) - 50) * 0.0015F;
   }

   public boolean rackPartVisible(int var1) {
      return var1 == 0 || this.buck;
   }

   public int trophyScore() {
      if (!this.buck || this.species.antlers == GameSpecies.Antlers.NONE) {
         return 0;
      } else {
         return this.species.antlers == GameSpecies.Antlers.PROCEDURAL_WHITETAIL
            ? Math.round(AntlerDesign.of(this).grossScoreInches())
            : Math.round(300.0F * this.rackScale() * (0.85F + (float)this.rackGenes * 0.0021F));
      }
   }

   public String trophyGrade() {
      int var1 = this.trophyScore();
      if (this.buck && this.species.antlers != GameSpecies.Antlers.NONE) {
         float var2 = this.species.antlers == GameSpecies.Antlers.PROCEDURAL_WHITETAIL ? 1.0F : 2.0F;
         return (float)var1 >= 170.0F * var2 ? "Exceptional" : ((float)var1 >= 140.0F * var2 ? "Gold" : ((float)var1 >= 110.0F * var2 ? "Silver" : "Bronze"));
      } else {
         return "Antlerless";
      }
   }

   public int harvestValue() {
      return Math.max(4, Math.round((float)this.massKg() * 0.35F) + this.trophyScore() / 2);
   }

   public CompoundTag save() {
      CompoundTag var1 = new CompoundTag();
      var1.putInt("schema", 2);
      if (this.species != GameSpecies.WHITETAIL) {
         var1.putString("species", this.species.id);
      }

      var1.putBoolean("buck", this.buck);
      var1.putInt("age_months", this.ageMonths);
      var1.putInt("frame", this.frame);
      var1.putInt("condition", this.condition);
      var1.putInt("rack_genes", this.rackGenes);
      var1.putInt("seed", this.seed);
      var1.putInt("abnormal", this.abnormal);
      var1.putInt("coat", this.coat);
      return var1;
   }

   public static DeerTraits load(CompoundTag var0) {
      if (var0 == null) {
         return REFERENCE;
      } else {
         int var1 = var0.getInt("schema");
         if (var1 != 1) {
            if (var1 == 2) {
               GameSpecies var2 = var0.contains("species") ? GameSpecies.byId(var0.getString("species")) : GameSpecies.WHITETAIL;
               return var2 == null
                  ? REFERENCE
                  : new DeerTraits(
                     var2,
                     var0.getBoolean("buck"),
                     var0.getInt("age_months"),
                     var0.getInt("frame"),
                     var0.getInt("condition"),
                     var0.getInt("rack_genes"),
                     var0.getInt("seed"),
                     var0.getInt("abnormal"),
                     var0.getInt("coat")
                  );
            } else {
               return REFERENCE;
            }
         } else {
            return new DeerTraits(
               !var0.contains("buck") || var0.getBoolean("buck"),
               var0.getInt("age_months"),
               var0.getInt("frame"),
               var0.getInt("condition"),
               var0.getInt("rack_genes"),
               var0.getInt("seed")
            );
         }
      }
   }
}
