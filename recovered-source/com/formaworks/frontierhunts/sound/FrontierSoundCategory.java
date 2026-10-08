package com.formaworks.frontierhunts.sound;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * [gui] Volume groups for Frontier Hunts' own sounds (the "Sound" page of the Frontier settings screen).
 *
 * <p>Minecraft's own sliders only know its sound sources (Weather, Ambient, Players...), and the mod's sounds are spread
 * over those, so they cannot be balanced against each other. Every {@code frontierhunts:} sound event belongs to exactly
 * one of these groups; the client mixer multiplies its volume by {@link #MASTER} x its group at play time and on every
 * tick of a looping sound. Pure Java (no client classes) so the config can be declared from the common HuntConfig.</p>
 */
public enum FrontierSoundCategory {
   // [polish] condensed from nine sliders to six clear groups (calls joined wildlife, ambient nature joined weather, UI
   // joined the kill cam). Config keys of the kept groups are unchanged, so values players already chose stay.
   MASTER("master", "Frontier master", "Every Frontier Hunts sound at once",
      "Scales all of the groups below together. 0% silences every Frontier sound. Minecraft's own sliders still apply on top.",
      "amb_bird_chirp"),
   WILDLIFE("wildlife", "Wildlife & calls", "Deer, elk, wolves and your own calls",
      "Deer, elk and moose voices (alarm blows, snorts, stomps, grunts, bleats, bugles), wolf howls, and the calls you blow or rattle yourself: grunt tube, bleat can, rattling antlers, predator call, horse whistle.",
      "deer_snort"),
   FIREARMS("firearms", "Weapons", "Shots, reloads, bows, scope clicks",
      "The mod's guns and bows: shots and their distant echo, bolts, levers and slides, magazines, casings, bowstrings, arrow hits and scope clicks.",
      "rifle_shot"),
   WEATHER("weather", "Weather & ambience", "Wind, storms, birds, rivers, waterfalls",
      "Wind in the trees and on the ridges, gusts, blizzards, downpours and dust storms, songbirds, owls and coyotes, insects, creeks, rivers and waterfalls, brush and twigs underfoot.",
      "weather_gust_rush"),
   VEHICLES("vehicles", "Vehicles", "ATV engine and splashes, glider, fuel",
      "ATV engine, starter, stalls, water splashes and churn, fuel cans, gear straps, paraglider, parachute and wingsuit airflow.",
      "atv_stop"),
   KILLCAM("killcam", "Cinematic & UI", "Kill cam, menu and notification sounds",
      "The kill cam's slow-motion swell, bullet flight, heartbeat and impact, and Frontier's interface sounds: field-gear tab, low-fuel chime, glider variometer.",
      "killcam.heartbeat");

   /** [polish] Former groups, kept as names so code written against the nine-slider mixer still compiles. */
   public static final FrontierSoundCategory CALLS = WILDLIFE;
   public static final FrontierSoundCategory AMBIENT = WEATHER;
   public static final FrontierSoundCategory UI = KILLCAM;

   /** Config key (sound.&lt;key&gt;Volume). */
   public final String key;
   public final String label;
   public final String desc;
   public final String help;
   /** Short sound played after the slider is released: a frontierhunts path, or a full "namespace:path". */
   public final String preview;

   FrontierSoundCategory(String key, String label, String desc, String help, String preview) {
      this.key = key;
      this.label = label;
      this.desc = desc;
      this.help = help;
      this.preview = preview;
   }

   /** Explicit table for every frontierhunts sound event shipped today (sounds.json + all merge fragments). */
   private static final Map<String, FrontierSoundCategory> EXPLICIT = new HashMap<>();

   private static void put(FrontierSoundCategory c, String... paths) {
      for (String p : paths) {
         EXPLICIT.put(p, c);
      }
   }

   static {
      put(WILDLIFE, "deer_bleat", "deer_blow", "deer_grunt", "deer_snort", "deer_stomp", "deer_wheeze",
         "elk_bark", "elk_bugle", "elk_mew", "moose_call", "moose_grunt", "moose_threat",
         "wolf_howl", "wolf_chorus"); // [integ3] ecology: predator howls are wildlife voices
      put(WILDLIFE, "bear_huff", "bear_growl", "bison_bellow", "cougar_growl", "pronghorn_snort", "duck_quack", "grouse_drum"); // [calls] real wildlife voices
      put(WILDLIFE, "wingshot.grouse_flush", "wingshot.duck_takeoff", "wingshot.duck_wings", "wingshot.grouse_wings", "wingshot.splash_small",
         "wingshot.bird_hit", "wingshot.body_thud", "wingshot.body_splash", "wingshot.grouse_drum_a", "wingshot.grouse_drum_b"); // [wingshot] bird flight and hits
      put(KILLCAM, "wingshot.slowmo"); // [wingshot] the wing-shot moment
      put(CALLS, "antler_rattle", "bleat_call", "deer_call", "grunt_tube", "predator_call", "horse_whistle", "wind_puff");
      put(AMBIENT, "amb_bird_chirp", "amb_bird_song", "amb_coyote", "amb_creek", "amb_frog", "amb_hawk", "amb_loon", "amb_meadow",
         "amb_night", "amb_owl", "amb_pika", "amb_raven", "amb_song_chickadee", "amb_song_thrush", "amb_song_warbler", "amb_woodpecker",
         "cascade", "cascade_far", "cascade_foot", "falls_crash", "falls_roar", "falls_roar_far", "falls_trickle", "falls_underwater",
         "fish_splash", "wildlife_flush", "branch_crackle", "branch_snap", "brush_rustle");
      put(WEATHER, "amb_wind_high", "amb_wind_trees", "weather_blizzard_loop", "weather_dust_loop", "weather_gust_howl",
         "weather_gust_rush", "weather_rain_loop", "weather_wind_loop");
      put(VEHICLES, "atv_crank", "atv_engine", "atv_engine_high", "atv_sputter", "atv_stall", "atv_start", "atv_stop", "atv_whine",
         "fuel_pour", "jerry_can_fill", "rig_attach", "glider_fold", "glider_open", "glider_wind", "canopy_open", "wingsuit_flutter",
         "atv_water_splash", "atv_water_churn", "atv_water_spray", "atv_water_slosh", "atv_water_flood"); // [integ3] atv2 wading sounds
      put(KILLCAM, "killcam.flight", "killcam.heartbeat", "killcam.impact", "killcam.impact_arrow", "killcam.return",
         "killcam.slowmo_in", "killcam.thud", "killcam.xray");
      put(FIREARMS, "arrow_impact", "bow_release", "casing_brass", "casing_hull", "optic.zoom_click", "optic.zoom_stop",
         "rifle_dry", "rifle_lock", "rifle_mag_in", "rifle_mag_out", "rifle_pull", "rifle_push", "rifle_shot", "rifle_shot_far",
         "rifle_unlock");
      for (String gun : new String[]{"bait_launcher", "double_barrel", "field_pistol", "flare_gun", "lever_rifle", "pump_shotgun",
         "revolver", "semi_auto_rifle", "semi_auto_shotgun", "tranquilizer_rifle"}) {
         put(FIREARMS, gun + "_close", gun + "_dry", gun + "_load", gun + "_open", gun + "_shot", gun + "_shot_far",
            gun + "_shot_suppressed", "draw_" + gun);
      }
      put(FIREARMS, "draw_ridgeline_rifle");
      // [academy] (integ5) training-grounds cues: steel target rings with the shots, the busted snort with the deer,
      // the rest (arrival, departure, objective tick, page, course passed) are presentation sounds
      put(FIREARMS, "academy.ring", "academy.ring_gold");
      put(WILDLIFE, "academy.busted");
      put(KILLCAM, "academy.arrive", "academy.depart", "academy.page", "academy.passed", "academy.tick");
   }

   /**
    * The group of a frontierhunts sound event path, or null when nothing fits (then only {@link #MASTER} applies). Events
    * added later by other features fall back to name rules, so a new sound is never left at full volume unnoticed.
    */
   public static FrontierSoundCategory classify(String path) {
      if (path == null || path.isEmpty()) {
         return null;
      }
      FrontierSoundCategory c = EXPLICIT.get(path);
      return c != null ? c : guess(path.toLowerCase(Locale.ROOT));
   }

   private static boolean has(String p, String... parts) {
      for (String s : parts) {
         if (p.contains(s)) {
            return true;
         }
      }
      return false;
   }

   private static boolean starts(String p, String... parts) {
      for (String s : parts) {
         if (p.startsWith(s)) {
            return true;
         }
      }
      return false;
   }

   static FrontierSoundCategory guess(String p) {
      if (starts(p, "killcam", "cinematic")) {
         return KILLCAM;
      }
      if (starts(p, "ui.", "ui_", "gui", "menu", "journal", "notify", "toast") || has(p, "notification", "page_turn")) {
         return UI;
      }
      if (starts(p, "weather", "amb_wind", "wind_") && !p.equals("wind_puff") || has(p, "blizzard", "gust", "storm", "thunder", "gale", "downpour")) {
         return WEATHER;
      }
      if (starts(p, "atv", "glider", "wingsuit", "canopy", "fuel", "jerry", "rig_", "boat", "snowmobile", "truck", "vehicle")
         || has(p, "engine", "exhaust")) {
         return VEHICLES;
      }
      boolean animal = starts(p, "deer_", "elk_", "moose_", "whitetail", "buck_", "doe_", "bull_", "cow_", "bear_", "wolf_", "coyote_",
         "hound", "dog_", "turkey", "duck_", "goose", "boar", "pronghorn", "bighorn", "sheep_", "caribou", "bison", "fox_", "cougar");
      if (!animal && (p.endsWith("_call") || has(p, "grunt_tube", "rattle", "whistle", "decoy", "caller", "wind_puff", "wind_check"))) {
         return CALLS;
      }
      if (animal || has(p, "bugle", "bleat", "snort", "wheeze", "howl", "bay_", "_bark", "growl", "vocal")) {
         return WILDLIFE;
      }
      if (starts(p, "draw_", "optic", "scope", "casing", "arrow", "bow_", "crossbow", "mag_", "bolt_", "reload")
         || has(p, "_shot", "_dry", "_load", "_open", "_close", "rifle", "pistol", "shotgun", "revolver", "musket", "gun", "trigger",
            "suppress", "muzzle")) {
         return FIREARMS;
      }
      if (starts(p, "amb_", "cascade", "falls_", "creek", "river", "stream", "fish", "bird", "insect", "frog")
         || has(p, "leaf", "leaves", "branch", "brush", "twig", "rustle", "water", "flush", "song", "chirp", "cricket")) {
         return AMBIENT;
      }
      return null;
   }

   // ============================================================================================ [polish] default mix
   /**
    * Designed level of individual sound events relative to their recordings (a gain, 1 = as recorded), applied by the
    * mixer after Minecraft's own clamp, so it also holds for sounds played with a volume above 1 to reach further.
    * Measured against the rest of the mod and vanilla's levels (short-term loudness of the files, see docs/ws/polish.md):
    * the storm beds and gusts were 7-9 dB hotter than vanilla rain/mob sounds and the most common sounds (hunting rifle,
    * birdsong, ATV engine, elk calls) sat 3-5 dB above everything around them. With these trims nothing the mod plays
    * peaks above about -10 LUFS (short-term) at the source (waterfalls, close calls); most voices sit at -12 to -14, shots
    * keep their transient punch, and a full storm now sits under them instead of over everything.
    */
   private static final Map<String, Float> TRIM = new HashMap<>();

   private static void trim(float gain, String... paths) {
      for (String p : paths) {
         TRIM.put(p, gain);
      }
   }

   static {
      // weather & wind: the user found the gusts and wind far too loud
      trim(0.42F, "weather_blizzard_loop");
      trim(0.36F, "weather_wind_loop", "weather_gust_rush");
      trim(0.40F, "weather_gust_howl");
      trim(0.50F, "weather_rain_loop", "amb_wind_high", "amb_wind_trees");
      trim(0.45F, "weather_dust_loop");
      // ambience: close birdsong and waterfalls
      trim(0.60F, "amb_song_thrush", "amb_song_warbler", "amb_song_chickadee", "amb_bird_song");
      trim(0.75F, "amb_loon");
      trim(0.70F, "cascade");
      trim(0.80F, "amb_owl", "amb_raven", "cascade_far", "falls_crash", "brush_rustle");
      trim(0.85F, "amb_hawk");
      trim(0.60F, "cascade_foot");
      // wildlife & calls
      trim(0.55F, "elk_mew");
      trim(0.60F, "elk_bugle");
      trim(0.50F, "horse_whistle");
      trim(0.80F, "moose_grunt", "moose_threat", "bleat_call", "predator_call");
      trim(0.85F, "moose_call", "wolf_howl", "wolf_chorus");
      // weapons: loud, never ear-splitting; the hunting rifle and flare were 4-8 dB above the other guns
      trim(0.70F, "rifle_shot");
      trim(0.60F, "flare_gun_shot");
      trim(0.80F, "bait_launcher_shot");
      // vehicles: the engine drones under the rider for minutes at a time
      trim(0.75F, "atv_engine");
      trim(0.65F, "atv_engine_high");
      trim(0.80F, "atv_whine", "atv_water_spray", "atv_water_churn", "glider_wind");
      trim(0.85F, "wingsuit_flutter");
      // cinematic: played in your ear (no distance), so a little under the world
      trim(0.75F, "killcam.slowmo_in", "killcam.xray");
      trim(0.85F, "killcam.flight", "killcam.heartbeat", "killcam.impact", "killcam.impact_arrow", "killcam.thud", "killcam.return");
   }

   /** Designed level of a frontierhunts sound event (1 = as recorded). Later wind/gust events default to the bed level. */
   public static float trim(String path) {
      if (path == null) {
         return 1.0F;
      }
      Float t = TRIM.get(path);
      if (t != null) {
         return t;
      }
      String p = path.toLowerCase(Locale.ROOT);
      if (has(p, "gust", "blizzard", "gale")) {
         return 0.4F;
      }
      if (has(p, "wind") && !has(p, "wind_puff", "wind_check", "windup", "window")) {
         return 0.5F;
      }
      return 1.0F;
   }

   /**
    * Slider position (0-100 %) to gain. Loudness is heard logarithmically, so a linear slider does almost nothing over
    * its upper half and then drops off a cliff near 0. This audio taper makes 50% sound about half as loud (-10 dB),
    * 25% a quarter, and 0% exactly silent.
    */
   public static float gain(int percent) {
      if (percent <= 0) {
         return 0.0F;
      }
      if (percent >= 100) {
         return 1.0F;
      }
      return (float)Math.pow(percent / 100.0, 1.66);
   }
}
