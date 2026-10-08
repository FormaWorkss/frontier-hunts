package com.formaworks.frontierhunts.guide;

/** [guide] One-time contextual field notes (first encounters). Saved as bits; text under {@code guide.frontierhunts.tip.<key>.*}. */
public enum Tip {
   DEER_SPOTTED("deer", "frontierhunts:binoculars"),
   BLOOD("blood", "frontierhunts:hound_lead"),
   WINDED("winded", "frontierhunts:wind_checker"),
   BLIZZARD("blizzard", "minecraft:snowball"),
   SEASON("season", "minecraft:clock"),
   PREDATOR("predator", "minecraft:bone"),
   // [survival] Frontier Survival field notes (sent through FieldSchool.survivalTip)
   HUNGER("hunger", "frontierhunts:cooked_game"),
   COLD("cold", "frontierhunts:fur_hat"),
   SPOILED("spoiled", "frontierhunts:spoiled_meat"),
   STOCKUP("stockup", "frontierhunts:jerky");

   private static final Tip[] VALUES = values();
   public final String key;
   public final String icon;

   Tip(String key, String icon) {
      this.key = key;
      this.icon = icon;
   }

   public int bit() {
      return 1 << this.ordinal();
   }

   public static Tip byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : null;
   }

   public String lang(String part) {
      return "guide.frontierhunts.tip." + this.key + "." + part;
   }
}
