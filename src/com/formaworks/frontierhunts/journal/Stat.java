package com.formaworks.frontierhunts.journal;

import java.util.Set;

/**
 * [journal] Counter keys of a hunter's record. Counters are string keyed so other workstreams can add their own
 * through {@link JournalApi#count}; the ones in {@link #MAX} keep the best value instead of adding up.
 * Species counters are {@code sp.<id>.seen|photo|h}; owned/placed gear {@code gear.<key>} / {@code placed.<key>}.
 */
public final class Stat {
   // shooting
   public static final String SHOTS = "shots";
   public static final String HITS = "hits";
   public static final String VITAL_HITS = "vital_hits";
   public static final String KILLS = "kills";
   public static final String CLEAN_KILLS = "clean_kills";
   public static final String SHOT_SUM_DM = "shot_sum_dm";
   public static final String LONGEST_SHOT = "longest_shot";
   public static final String BOW_KILLS = "bow_kills";
   public static final String BOW_LONG = "bow_long";
   public static final String RIFLE_KILLS = "rifle_kills";
   public static final String PRONE_SHOTS = "prone_shots";
   // tracking / stalking
   public static final String HARVESTS = "harvests";
   public static final String RECOVERIES = "recoveries";
   public static final String LOST = "lost";
   public static final String TRAIL_M = "trail_m";
   public static final String LONGEST_TRAIL = "longest_trail";
   public static final String SIGNS = "signs";
   public static final String RUBS = "rubs";
   public static final String STALKS = "stalks";
   public static final String STALK_CLOSE = "stalk_close";
   public static final String CALLS = "calls";
   public static final String CALLED_KILLS = "called_kills";
   public static final String UNAWARE_KILLS = "unaware_kills";
   public static final String BEST_WT_SCORE = "best_wt_score";
   public static final String SPECIES_TAKEN = "species_taken";
   public static final String SPECIES_PHOTOGRAPHED = "species_photographed";
   public static final String PHOTOS = "photos";
   // world
   public static final String BIOMES = "biomes";
   public static final String BONES = "bones";
   public static final String SHEDS = "sheds";
   public static final String PREDATOR_SITES = "predator_sites";
   public static final String SEASONS = "seasons";
   public static final String BLIZZARDS = "blizzards";
   public static final String WINTER_MIN = "winter_min";
   public static final String WALK_M = "walk_m";
   public static final String RIDE_M = "ride_m";
   public static final String FIELD_S = "field_s";
   public static final String STAND_SITS = "stand_sits";
   public static final String ATV_RIDES = "atv_rides";
   // camp
   public static final String CAMP = "camp";
   public static final String CAMP_RANK = "camp_rank";
   public static final String GUIDED = "guided";
   public static final String BOARD = "board";
   public static final String EVENT_PODIUMS = "event_podiums";
   public static final String ASSISTS = "assists";
   public static final String PROVISIONS = "provisions";
   public static final String CONTRACTS = "contracts";
   public static final String CAMPAIGN = "campaign";
   public static final String FIELD_SCHOOL = "fieldschool";
   // survival
   public static final String EATEN = "eaten";
   // [integ4] Frontier Survival (driven by survival.SurvivalJournal)
   public static final String SURV_FURS = "surv_furs";
   public static final String SURV_PRESERVED = "surv_preserved";
   public static final String SURV_WINTER_NIGHTS = "surv_winter_nights";

   /** Counters that keep the best value seen (set-max) rather than accumulating. */
   public static final Set<String> MAX = Set.of(LONGEST_SHOT, LONGEST_TRAIL, BEST_WT_SCORE, BIOMES, CAMP, CAMP_RANK, PROVISIONS, CONTRACTS,
      CAMPAIGN, FIELD_SCHOOL, SEASONS, SPECIES_TAKEN, SPECIES_PHOTOGRAPHED);

   /** Counters that tick constantly (saved, but they do not trigger a resync of an open journal on their own). */
   public static final Set<String> QUIET = Set.of(FIELD_S, WALK_M, RIDE_M);

   private Stat() {
   }

   public static String species(String id, String part) {
      return "sp." + id + "." + part;
   }
}
