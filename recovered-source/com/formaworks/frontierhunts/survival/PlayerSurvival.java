package com.formaworks.frontierhunts.survival;

import net.minecraft.nbt.CompoundTag;

/**
 * [survival] One player's survival state, server side. Persisted in the player's NeoForge persistent data under
 * {@code frontierhunts_survival}; lifetime stats survive death (copied on clone), the meters restart at a respawn level.
 */
public final class PlayerSurvival {
   public static final String KEY = "frontierhunts_survival";

   // meters
   public float protein = 80F;
   public float fat = 80F;
   public float energy = 80F;
   public float heat = 0F;
   public float wet = 0F;
   public long lastGameMeal = -1L;

   // live (not saved)
   float lastExhaustion = -1F;
   int starveTimer;
   int freezeTimer;
   int updates;
   float heatBonus;
   Thermal.Shelter shelter = Thermal.Shelter.OPEN;
   float air = 15F;
   float felt = 15F;
   float insulation = 1F;
   float windProof;
   float waterProof;
   boolean mittens;
   int store;
   float trend;
   float regen = 1F;
   int warned;
   long lastSent = Long.MIN_VALUE;
   int sentHash;
   boolean sleptWarm;
   double modMove = 0, modAttack = 0, modMine = 0, modDamage = 0, modHealth = 0;

   // lifetime stats (journal API)
   public int meals;
   public int gameMeals;
   public int fishMeals;
   public int farmMeals;
   public int spoiledEaten;
   public int spoiledLost;
   public int preserved;
   public int freezes;
   public int coldNights;
   public float coldestBody;
   public long vigorSeconds;
   public long hungrySeconds;

   public void load(CompoundTag t) {
      if (t.contains("protein")) {
         this.protein = clamp(t.getFloat("protein"));
         this.fat = clamp(t.getFloat("fat"));
         this.energy = clamp(t.getFloat("energy"));
         this.heat = Math.max(-100F, Math.min(100F, t.getFloat("heat")));
         this.wet = Math.max(0F, Math.min(1F, t.getFloat("wet")));
      }
      this.lastGameMeal = t.contains("lastGameMeal") ? t.getLong("lastGameMeal") : -1L;
      this.loadStats(t);
   }

   void loadStats(CompoundTag t) {
      this.meals = t.getInt("meals");
      this.gameMeals = t.getInt("gameMeals");
      this.fishMeals = t.getInt("fishMeals");
      this.farmMeals = t.getInt("farmMeals");
      this.spoiledEaten = t.getInt("spoiledEaten");
      this.spoiledLost = t.getInt("spoiledLost");
      this.preserved = t.getInt("preserved");
      this.freezes = t.getInt("freezes");
      this.coldNights = t.getInt("coldNights");
      this.coldestBody = t.getFloat("coldestBody");
      this.vigorSeconds = t.getLong("vigorSeconds");
      this.hungrySeconds = t.getLong("hungrySeconds");
   }

   public CompoundTag save() {
      CompoundTag t = new CompoundTag();
      t.putFloat("protein", this.protein);
      t.putFloat("fat", this.fat);
      t.putFloat("energy", this.energy);
      t.putFloat("heat", this.heat);
      t.putFloat("wet", this.wet);
      t.putLong("lastGameMeal", this.lastGameMeal);
      t.putInt("meals", this.meals);
      t.putInt("gameMeals", this.gameMeals);
      t.putInt("fishMeals", this.fishMeals);
      t.putInt("farmMeals", this.farmMeals);
      t.putInt("spoiledEaten", this.spoiledEaten);
      t.putInt("spoiledLost", this.spoiledLost);
      t.putInt("preserved", this.preserved);
      t.putInt("freezes", this.freezes);
      t.putInt("coldNights", this.coldNights);
      t.putFloat("coldestBody", this.coldestBody);
      t.putLong("vigorSeconds", this.vigorSeconds);
      t.putLong("hungrySeconds", this.hungrySeconds);
      return t;
   }

   /** After death: stats kept, body restarts fed enough to go hunting. */
   public void respawn() {
      this.protein = Math.max(this.protein, 60F);
      this.fat = Math.max(this.fat, 60F);
      this.energy = Math.max(this.energy, 70F);
      this.heat = 0F;
      this.wet = 0F;
      this.lastExhaustion = -1F;
      this.starveTimer = 0;
      this.freezeTimer = 0;
      this.lastGameMeal = -1L;
   }

   static float clamp(float v) {
      return Float.isFinite(v) ? Math.max(0F, Math.min(100F, v)) : 80F;
   }
}
