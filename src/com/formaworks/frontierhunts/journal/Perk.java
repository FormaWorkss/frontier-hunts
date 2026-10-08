package com.formaworks.frontierhunts.journal;

/**
 * [journal] Three perks per skill, unlocked automatically at skill levels 2, 5 and 8. Effects (scaled by the server's
 * {@code perkStrength}, see {@link HunterSkills}):
 * <pre>
 * Marksmanship  2 Steady Hands      rifle hold drift -20 %
 *               5 Quick Settle      the extra wobble right after raising the rifle is halved
 *               8 Controlled Breath breathing sway -35 %, drift a further -15 %
 * Stalking      2 Soft Steps        deer/elk/moose hear your movement 20 % less far, other game 10 %
 *               5 Low Profile       moving in the open is 15 % less noticeable
 *               8 Ghost             scent carries 25 % less, hearing a further -20 %
 * Tracking      2 Keen Eye          sign and blood show up 25 % farther away
 *               5 Trail Sense       inspecting blood or prints tells which way the animal went
 *               8 Bloodhound        inspecting fresh sign tells roughly how far ahead the animal is (≤128 m)
 * Butchery      2 Clean Cuts        +25 % venison (at least +1) from deer, elk and moose
 *               5 Quick Knife       field dressing 30 % faster
 *               8 Master Skinner    +1 hide and +1 venison quarter
 * Woodcraft     2 Trail Legs        15 % less hunger from moving outdoors
 *               5 Provider          wild game you eat restores +2 hunger and extra saturation
 *               8 Thick Skin        freezing builds 40 % slower (Survival: cold exposure -30 %)
 * </pre>
 */
public enum Perk {
   STEADY_HANDS(Skill.MARKSMANSHIP, 2, "steady_hands"),
   QUICK_SETTLE(Skill.MARKSMANSHIP, 5, "quick_settle"),
   CONTROLLED_BREATH(Skill.MARKSMANSHIP, 8, "controlled_breath"),
   SOFT_STEPS(Skill.STALKING, 2, "soft_steps"),
   LOW_PROFILE(Skill.STALKING, 5, "low_profile"),
   GHOST(Skill.STALKING, 8, "ghost"),
   KEEN_EYE(Skill.TRACKING, 2, "keen_eye"),
   TRAIL_SENSE(Skill.TRACKING, 5, "trail_sense"),
   BLOODHOUND(Skill.TRACKING, 8, "bloodhound"),
   CLEAN_CUTS(Skill.BUTCHERY, 2, "clean_cuts"),
   QUICK_KNIFE(Skill.BUTCHERY, 5, "quick_knife"),
   MASTER_SKINNER(Skill.BUTCHERY, 8, "master_skinner"),
   TRAIL_LEGS(Skill.WOODCRAFT, 2, "trail_legs"),
   PROVIDER(Skill.WOODCRAFT, 5, "provider"),
   THICK_SKIN(Skill.WOODCRAFT, 8, "thick_skin");

   private static final Perk[] VALUES = values();

   public final Skill skill;
   public final int level;
   public final String key;

   Perk(Skill skill, int level, String key) {
      this.skill = skill;
      this.level = level;
      this.key = key;
   }

   public int bit() {
      return 1 << this.ordinal();
   }

   public boolean in(int mask) {
      return (mask & this.bit()) != 0;
   }

   public static Perk byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : null;
   }

   public static Perk byKey(String key) {
      for (Perk p : VALUES) {
         if (p.key.equals(key)) {
            return p;
         }
      }
      return null;
   }

   /** Perks unlocked by these skill levels (before the server's disabled list is applied). */
   public static int unlocked(int[] levels) {
      int m = 0;
      for (Perk p : VALUES) {
         int s = p.skill.ordinal();
         if (s < levels.length && levels[s] >= p.level) {
            m |= p.bit();
         }
      }
      return m;
   }

   public String lang(String part) {
      return "journal.frontierhunts.perk." + this.key + "." + part;
   }
}
