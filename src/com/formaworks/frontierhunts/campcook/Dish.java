package com.formaworks.frontierhunts.campcook;

import java.util.Locale;

/**
 * [licence] The hunting-camp dishes: vanilla hunger/saturation, whether they are served in a bowl, and the camp-meal
 * buff with its duration. Protein / fat / energy and shelf life live in the survival food table
 * ({@code data/frontierhunts/survival/foods/camp_cooking.json}); recipes in {@code data/frontierhunts/recipe/campcook/}.
 */
public enum Dish {
   VENISON_STEW("venison_stew", 8, 0.75F, true, MealBuffs.Buff.WARMTH, 8),
   VENISON_CHILI("venison_chili", 8, 0.7F, true, MealBuffs.Buff.STAMINA, 6),
   BEAR_POT_ROAST("bear_pot_roast", 9, 0.85F, false, MealBuffs.Buff.WARMTH, 10),
   BACKSTRAP_MUSHROOMS("backstrap_mushrooms", 8, 0.9F, false, MealBuffs.Buff.STEADY, 6),
   FOWL_BERRY_ROAST("fowl_berry_roast", 7, 0.75F, false, MealBuffs.Buff.KEEN, 6),
   FOWL_WILD_RICE("fowl_wild_rice", 8, 0.75F, true, MealBuffs.Buff.QUIET, 6),
   HEART_LIVER_FRY("heart_liver_fry", 6, 0.8F, false, MealBuffs.Buff.HEARTY, 5),
   HUNTERS_BREAKFAST("hunters_breakfast", 10, 0.8F, false, MealBuffs.Buff.STEADY, 8),
   FISH_CHOWDER("fish_chowder", 7, 0.7F, true, MealBuffs.Buff.WARMTH, 5),
   CAMP_BANNOCK("camp_bannock", 5, 0.6F, false, MealBuffs.Buff.STAMINA, 3),
   HONEY_PEMMICAN("honey_pemmican", 7, 1.0F, false, MealBuffs.Buff.KEEN, 4),
   SMOKED_SAUSAGE("smoked_game_sausage", 5, 0.7F, false, MealBuffs.Buff.MASKED, 5);

   public final String id;
   public final int nutrition;
   public final float saturation;
   public final boolean bowl;
   public final MealBuffs.Buff buff;
   public final int minutes;

   Dish(String id, int nutrition, float saturation, boolean bowl, MealBuffs.Buff buff, int minutes) {
      this.id = id;
      this.nutrition = nutrition;
      this.saturation = saturation;
      this.bowl = bowl;
      this.buff = buff;
      this.minutes = minutes;
   }

   public int ticks() {
      return this.minutes * 60 * 20;
   }

   public String key() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public static Dish byId(String id) {
      for (Dish d : values()) {
         if (d.id.equals(id)) {
            return d;
         }
      }
      return null;
   }
}
