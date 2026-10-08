package com.formaworks.frontierhunts.journal;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * [journal] Hunter's Journal, skills and ranks settings. Defined inside HuntConfig's server and client specs (one hook
 * line each, see HuntConfig "[journal]"). Getters are safe before the configs load.
 */
public final class JournalConfig {
   // server
   public static ModConfigSpec.BooleanValue SKILLS;
   public static ModConfigSpec.DoubleValue XP_MULTIPLIER;
   public static ModConfigSpec.DoubleValue PERK_STRENGTH;
   public static ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_PERKS;
   public static ModConfigSpec.BooleanValue COUNT_CREATIVE;
   public static ModConfigSpec.BooleanValue SEASON_SUMMARIES;
   public static ModConfigSpec.BooleanValue ANNOUNCE_RANKS;
   // client
   public static ModConfigSpec.BooleanValue XP_TICKER;
   public static ModConfigSpec.BooleanValue TOASTS;

   private JournalConfig() {
   }

   /** Called from HuntConfig's server builder inside push("journal"). */
   public static void server(ModConfigSpec.Builder b) {
      SKILLS = b.comment("Hunter skills: XP from clean kills, stalking, tracking, field dressing and time in the wild, with perks at skill levels 2/5/8. Off = the journal still tracks progress but no XP or perks.")
         .define("skills", true);
      XP_MULTIPLIER = b.comment("Multiplier for all skill XP (checklist rewards included).")
         .defineInRange("xpMultiplier", 1.0, 0.1, 10.0);
      PERK_STRENGTH = b.comment("Scales every perk's effect: 1 = as documented (e.g. Steady Hands -20 % drift), 0.5 = half as strong, 0 = perks do nothing.")
         .defineInRange("perkStrength", 1.0, 0.0, 2.0);
      DISABLED_PERKS = b.comment("Perks switched off on this server, by id: steady_hands, quick_settle, controlled_breath, soft_steps, low_profile, ghost, keen_eye, trail_sense, bloodhound, clean_cuts, quick_knife, master_skinner, trail_legs, provider, thick_skin.")
         .defineListAllowEmpty("disabledPerks", List.of(), o -> o instanceof String s && Perk.byKey(s) != null);
      COUNT_CREATIVE = b.comment("Count actions of creative-mode players (testing).")
         .define("countCreative", false);
      SEASON_SUMMARIES = b.comment("Write a season summary into each hunter's journal when the reserve season turns.")
         .define("seasonSummaries", true);
      ANNOUNCE_RANKS = b.comment("Tell everyone in chat when a hunter reaches a new rank.")
         .define("announceRanks", true);
   }

   /** Called from HuntConfig's client builder inside push("journal"). */
   public static void client(ModConfigSpec.Builder b) {
      XP_TICKER = b.comment("Show small '+40 XP · Marksmanship' notes above the hotbar when you earn skill XP.")
         .define("xpTicker", true);
      TOASTS = b.comment("Show toasts for new ranks, skill levels, perks and checklist entries.")
         .define("toasts", true);
   }

   static boolean bool(ModConfigSpec.BooleanValue v, boolean def) {
      try {
         return v == null ? def : v.get();
      } catch (Throwable t) {
         return def;
      }
   }

   static double num(ModConfigSpec.DoubleValue v, double def) {
      try {
         return v == null ? def : v.get();
      } catch (Throwable t) {
         return def;
      }
   }

   public static boolean skills() {
      return bool(SKILLS, true);
   }

   public static double xpMultiplier() {
      return num(XP_MULTIPLIER, 1.0);
   }

   public static double perkStrength() {
      return num(PERK_STRENGTH, 1.0);
   }

   public static boolean countCreative() {
      return bool(COUNT_CREATIVE, false);
   }

   public static boolean seasonSummaries() {
      return bool(SEASON_SUMMARIES, true);
   }

   public static boolean announceRanks() {
      return bool(ANNOUNCE_RANKS, true);
   }

   public static boolean xpTicker() {
      return bool(XP_TICKER, true);
   }

   public static boolean toasts() {
      return bool(TOASTS, true);
   }

   /** Bitmask of perks disabled by the server list. */
   public static int disabledMask() {
      int m = 0;
      try {
         if (DISABLED_PERKS != null) {
            for (String s : DISABLED_PERKS.get()) {
               Perk p = Perk.byKey(s);
               if (p != null) {
                  m |= p.bit();
               }
            }
         }
      } catch (Throwable ignored) {
      }
      return m;
   }
}
