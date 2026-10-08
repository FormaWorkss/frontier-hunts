package com.formaworks.frontierhunts.guide;

/**
 * [guide] The Field School lessons, in teaching order. The ids are stable (saved as bits of a mask); the texts all
 * live in the lang file under {@code guide.frontierhunts.lesson.<key>.*}.
 */
public enum Lesson {
   WIND("wind", "frontierhunts:wind_checker", 1),
   SIGN("sign", "frontierhunts:frontier_handbook", 1), // [onebook] was the Hunter's Journal item
   GLASS("glass", "frontierhunts:binoculars", 1),
   STALK("stalk", "minecraft:leather_boots", 3),
   SHOT("shot", "frontierhunts:field_bow", 1),
   TRAIL("trail", "frontierhunts:hound_lead", 2),
   HARVEST("harvest", "frontierhunts:skinning_tool", 2),
   TIPS("tips", "frontierhunts:camp_post", 1);

   /** lessons 1..7 are the course; the field tips (8) are optional reading */
   public static final int REQUIRED_MASK = (1 << 7) - 1;
   public static final int ALL_MASK = (1 << 8) - 1;
   private static final Lesson[] VALUES = values();

   public final String key;
   public final String icon;
   /** progress units the HUD card shows (1 = a single action) */
   public final int goal;

   Lesson(String key, String icon, int goal) {
      this.key = key;
      this.icon = icon;
      this.goal = goal;
   }

   public int bit() {
      return 1 << this.ordinal();
   }

   public boolean optional() {
      return this == TIPS;
   }

   public boolean done(int mask) {
      return (mask & this.bit()) != 0;
   }

   public static Lesson byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : null;
   }

   public static int count() {
      return VALUES.length;
   }

   /** The first lesson not yet done (required ones first, then the optional tips), or null when everything is done. */
   public static Lesson current(int mask) {
      for (Lesson l : VALUES) {
         if (!l.done(mask)) {
            return l;
         }
      }
      return null;
   }

   public static int requiredDone(int mask) {
      return Integer.bitCount(mask & REQUIRED_MASK);
   }

   public static boolean graduated(int mask) {
      return (mask & REQUIRED_MASK) == REQUIRED_MASK;
   }

   public String lang(String part) {
      return "guide.frontierhunts.lesson." + this.key + "." + part;
   }
}
