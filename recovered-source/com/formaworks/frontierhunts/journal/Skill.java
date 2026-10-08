package com.formaworks.frontierhunts.journal;

/**
 * [journal] The five hunter skills. Each one levels by doing (use-based XP, server-authoritative): clean kills train
 * Marksmanship, getting close unseen and calling train Stalking, reading sign and following blood train Tracking,
 * field-dressing trains Butchery, time and travel in the wild train Woodcraft. Ids are stable (saved by name).
 */
public enum Skill { // [ledger] icons are journal atlas refs (journal.client.JournalIcons)
   MARKSMANSHIP("marksmanship", "icon:marksmanship", 0xFFB0623C),
   STALKING("stalking", "icon:stalking", 0xFF5E7F4A),
   TRACKING("tracking", "icon:tracking", 0xFF8E3B32),
   BUTCHERY("butchery", "icon:butchery", 0xFF8C6A3C),
   WOODCRAFT("woodcraft", "icon:woodcraft", 0xFF3F6C78);

   public static final int MAX_LEVEL = 10;
   private static final Skill[] VALUES = values();

   public final String key;
   public final String icon;
   public final int color;

   Skill(String key, String icon, int color) {
      this.key = key;
      this.icon = icon;
      this.color = color;
   }

   public static Skill byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : null;
   }

   public static int count() {
      return VALUES.length;
   }

   /** Cumulative skill XP needed to reach {@code level} (0..10): 60, 160, 300, 480, 700, 960, 1260, 1600, 1980, 2400. */
   public static int xpFor(int level) {
      int n = Math.max(0, Math.min(MAX_LEVEL, level));
      return 20 * n * n + 40 * n;
   }

   public static int level(int xp) {
      int l = 0;
      while (l < MAX_LEVEL && xp >= xpFor(l + 1)) {
         l++;
      }
      return l;
   }

   public String lang(String part) {
      return "journal.frontierhunts.skill." + this.key + "." + part;
   }
}
