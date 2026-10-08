package com.formaworks.frontierhunts.survival;

import com.formaworks.frontierhunts.season.SeasonClock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * [survival] Read-only API of Frontier Survival for other features (the journal / hunter ranks, the guide).
 *
 * Server side: {@link #stats(ServerPlayer)} and {@link #status(ServerPlayer)}. Client side (the local player):
 * {@link #clientStatus()}. Either side: difficulty, seasonal yields, food values and freshness of a stack.
 * Nothing here changes state.
 */
public final class SurvivalApi {
   private SurvivalApi() {
   }

   /** Current body and nutrition status. Meters 0..100, heat -100..100 (0 comfortable), temperatures deg C. */
   public record Status(
      SurvivalConfig.Difficulty difficulty, float protein, float fat, float energy, float bodyHeat, float coreTemperature, float feltTemperature,
      float insulation, float wetness, boolean huntersVigor, boolean sheltered, boolean nearFire
   ) {
   }

   /** Lifetime numbers a journal can show or rank on (kept through death). */
   public record Stats(
      int meals, int gameMeals, int fishMeals, int farmMeals, int spoiledEaten, int spoiledLost, int preserved, int frozenToDeath, int coldNights,
      float coldestBodyHeat, long secondsWithVigor, long secondsHungry
   ) {
      /** Share of meals that were wild game (0..1). */
      public float gameShare() {
         return this.meals <= 0 ? 0F : (float) this.gameMeals / this.meals;
      }
   }

   /** Seasonal yield hint: phase 0 fall plenty, 1 early winter, 2 lean late winter, 3 spring recovery, 4 summer. */
   public record SeasonYield(int phase, float condition, float meatMultiplier, int typicalDeerFat, float spawnDenial, String langKey) {
   }

   public static SurvivalConfig.Difficulty difficulty(Level level) {
      return level != null && level.isClientSide
         ? SurvivalConfig.Difficulty.values()[SurvivalSync.mode(true).ordinal()]
         : SurvivalConfig.difficulty();
   }

   public static boolean enabled(Level level) {
      return difficulty(level) != SurvivalConfig.Difficulty.OFF;
   }

   public static Status status(ServerPlayer p) {
      PlayerSurvival st = SurvivalService.state(p);
      long now = p.serverLevel().getGameTime();
      return new Status(
         SurvivalConfig.difficulty(), st.protein, st.fat, st.energy, st.heat, SurvivalMath.coreTemperature(st.heat), st.felt, st.insulation, st.wet,
         SurvivalMath.vigor(st.protein, st.fat, st.energy, st.lastGameMeal < 0L ? -1L : now - st.lastGameMeal), st.shelter.roofed(), st.heatBonus >= 3F
      );
   }

   /** The local player's status as last sent by the server (client only; zeros when not in a world). */
   public static Status clientStatus() {
      SurvivalNetwork.State s = SurvivalNetwork.clientState();
      return new Status(
         SurvivalConfig.Difficulty.values()[Math.min(3, s.mode())], s.protein(), s.fat(), s.energy(), s.heat(), SurvivalMath.coreTemperature(s.heat()),
         s.felt(), s.insulation(), s.wet(), s.has(SurvivalNetwork.State.F_VIGOR), s.has(SurvivalNetwork.State.F_SHELTER), s.has(SurvivalNetwork.State.F_FIRE)
      );
   }

   public static Stats stats(ServerPlayer p) {
      PlayerSurvival st = SurvivalService.state(p);
      return new Stats(
         st.meals, st.gameMeals, st.fishMeals, st.farmMeals, st.spoiledEaten, st.spoiledLost, st.preserved, st.freezes, st.coldNights, st.coldestBody,
         st.vigorSeconds, st.hungrySeconds
      );
   }

   public static SeasonYield seasonYield(Level level) {
      double y = SeasonClock.yearPosition(level);
      boolean lean = level != null && (level.isClientSide ? SurvivalSync.mode(true).on() : SurvivalConfig.leanSeasons());
      float c = lean ? SurvivalMath.condition(y) : 1F;
      int phase = SurvivalMath.yieldPhase(y);
      return new SeasonYield(
         phase, c, SurvivalMath.meatYield(c), SurvivalMath.fatYield(c, 84, 1F), lean ? SurvivalMath.spawnDenial(y) : 0F,
         "survival.frontierhunts.season." + phase
      );
   }

   /** Nutrition of a food on this side (null if it is no food). */
   public static FoodValues food(ItemStack stack, Level level) {
      return NutritionTable.get(stack, level != null && level.isClientSide);
   }

   /** 0 fresh .. 1 spoiled. */
   public static float spoilage(ItemStack stack, Level level) {
      return level == null ? 0F : Perishable.spoil(stack, level.getGameTime(), level.isClientSide);
   }

   /** Aim-sway multiplier for the local player from shivering and exhaustion (1 = steady). */
   public static float clientSway() {
      return com.formaworks.frontierhunts.campcook.MealBuffs.clientAim * clientSwaySurvival(); // [licence] Steady Aim camp meal
   }

   private static float clientSwaySurvival() {
      SurvivalNetwork.State s = SurvivalNetwork.clientState();
      if (s.mode() == 0 || s.has(SurvivalNetwork.State.F_EXEMPT)) {
         return 1F;
      }
      float sway = SurvivalMath.sway(s.has(SurvivalNetwork.State.F_TEMPERATURE) ? s.heat() : 0F, s.energy());
      return s.has(SurvivalNetwork.State.F_MITTENS) ? 1F + (sway - 1F) * 0.7F : sway;
   }

   /** Whether {@code p} (server) currently has Hunter's Vigor. */
   public static boolean vigor(Player p) {
      return p instanceof ServerPlayer sp && status(sp).huntersVigor();
   }
}
