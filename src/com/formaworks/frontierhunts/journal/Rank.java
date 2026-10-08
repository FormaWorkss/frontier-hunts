package com.formaworks.frontierhunts.journal;

/**
 * [journal] Hunter ranks by total journal XP (every skill's XP plus checklist rewards). Shown on the journal's home
 * page, announced with a toast and a field note when reached.
 */
public enum Rank { // [ledger] icons are journal atlas refs (journal.client.JournalIcons)
   GREENHORN("greenhorn", 0, "icon:rank_greenhorn"),
   WOODSMAN("woodsman", 400, "icon:rank_woodsman"),
   TRACKER("tracker", 1500, "icon:rank_tracker"),
   GUIDE("guide", 4000, "icon:rank_guide"),
   MASTER_HUNTER("master_hunter", 10000, "icon:rank_master"),
   LEGEND("legend", 22000, "icon:rank_legend");

   private static final Rank[] VALUES = values();

   public final String key;
   public final int xp;
   public final String icon;

   Rank(String key, int xp, String icon) {
      this.key = key;
      this.xp = xp;
      this.icon = icon;
   }

   public static Rank of(long total) {
      Rank r = GREENHORN;
      for (Rank k : VALUES) {
         if (total >= k.xp) {
            r = k;
         }
      }
      return r;
   }

   public Rank next() {
      return this.ordinal() + 1 < VALUES.length ? VALUES[this.ordinal() + 1] : null;
   }

   public static Rank byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : GREENHORN;
   }

   /** English name for server-side text (season summaries, status). */
   public String title() {
      return switch (this) {
         case GREENHORN -> "Greenhorn";
         case WOODSMAN -> "Woodsman";
         case TRACKER -> "Tracker";
         case GUIDE -> "Guide";
         case MASTER_HUNTER -> "Master Hunter";
         case LEGEND -> "Legend";
      };
   }

   public String lang() {
      return "journal.frontierhunts.rank." + this.key;
   }
}
