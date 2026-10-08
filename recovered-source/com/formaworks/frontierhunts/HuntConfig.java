package com.formaworks.frontierhunts;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.EnumValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

public final class HuntConfig {
   public static final ModConfigSpec SERVER;
   public static final ModConfigSpec CLIENT;
   public static final ModConfigSpec WORLDGEN;
   public static final BooleanValue GENERATE_RANGER_CAMPS;
   public static final BooleanValue GENERATE_HUNTING_FORESTS;
   public static final BooleanValue FORESTS_OUTSIDE_RESERVE;
   public static final IntValue FOREST_DENSITY;
   public static final IntValue FOLIAGE_DENSITY;
   public static final EnumValue<HuntConfig.Realism> REALISM;
   public static final IntValue SURVEY_REWARD;
   public static final IntValue MAX_DISCOVERIES;
   public static final IntValue SYNC_INTERVAL;
   public static final BooleanValue STARTER_JOURNAL;
   public static final BooleanValue CINEMATICS;
   public static final IntValue DAYS_PER_MONTH;
   public static final BooleanValue WHITETAILS_OUTSIDE_RESERVE;
   public static final EnumValue<HuntConfig.Quality> QUALITY;
   public static final BooleanValue SHOW_HUD;
   public static final BooleanValue REDUCED_MOTION;
   /** [1.1.5] comfort: how much the view is shaken (recoil kick, rapids, deep-snow lurch, shivering); 0 = steady */
   public static final IntValue CAMERA_SHAKE;
   /** [1.1.5] comfort: blood spray and mist on hits (blood trail sign on the ground is gameplay and always stays) */
   public static final EnumValue<HuntConfig.BloodEffects> BLOOD_EFFECTS;
   /** [1.1.6] outline your own tracking hound when it is away from you */
   public static final BooleanValue HOUND_OUTLINE;
   public static final BooleanValue CLASSIC_HANDBOOK;
   // [killcam] kill cam presentation settings (client)
   public static final EnumValue<com.formaworks.frontierhunts.killcam.KillCamMode> KILLCAM;
   public static final BooleanValue KILLCAM_XRAY;
   public static final EnumValue<com.formaworks.frontierhunts.killcam.KillCamLength> KILLCAM_LENGTH;
   public static final EnumValue<HuntConfig.GrassStyle> GRASS_STYLE;
   public static final EnumValue<HuntConfig.SnowStyle> SNOW_STYLE; // [1.1.2] Frontier snow
   public static final BooleanValue SNOW_STYLE_122; // [1.2.2] one-time move to the smooth snowpack
   public static final IntValue GRASS_HEIGHT_PERCENT;
   public static final IntValue GRASS_WIDTH_PERCENT;
   public static final EnumValue<HuntConfig.GraphicsPreset> GRAPHICS_PRESET;
   public static final EnumValue<HuntConfig.WorldLook> WORLD_LOOK;
   /** Retired smooth-terrain switch. Still defined so old config files parse; nothing reads it. */
   @Deprecated
   public static final EnumValue<HuntConfig.Terrain> TERRAIN;
   public static final ConfigValue<String>[] PRESET_RESOURCE_PACK = new ConfigValue[4];
   public static final ConfigValue<String>[] PRESET_SHADER_PACK = new ConfigValue[4];
   public static final EnumValue<HuntConfig.AnimalStyle> ANIMAL_STYLE;
   public static final EnumValue<HuntConfig.Detail> ANIMAL_DETAIL;
   public static final EnumValue<HuntConfig.EffectLevel> WATERFALL_DETAIL;
   public static final EnumValue<HuntConfig.GrassThickness> GRASS_THICKNESS;
   public static final BooleanValue AMBIENT_SOUNDS;
   public static final BooleanValue WILDLIFE_LIFE;
   public static final BooleanValue BREATH_VAPOR;
   public static final BooleanValue MOUNTAIN_SPINDRIFT;
   public static final IntValue AMBIENT_VOLUME;
   // [seasons] seasonal world (server, synced to clients) and falling leaves (client)
   public static final BooleanValue SEASONS;
   public static final BooleanValue SEASONAL_SNOW;
   public static final EnumValue<HuntConfig.Quality> LEAF_FALL;
   // [routines] animal routines, game trails, scent busts, hunting pressure and rut behaviour (server)
   public static final BooleanValue ROUTINES_ENABLED;
   public static final IntValue ROUTINE_HOME_RANGE;
   public static final ModConfigSpec.DoubleValue SCENT_BUST_THRESHOLD;
   public static final BooleanValue PRESSURE_ENABLED;
   public static final ModConfigSpec.DoubleValue PRESSURE_HALF_LIFE_DAYS;
   public static final ModConfigSpec.DoubleValue PRESSURE_SHOT_WEIGHT;
   public static final ModConfigSpec.DoubleValue PRESSURE_NIGHT_SHIFT;
   public static final ModConfigSpec.DoubleValue PRESSURE_RELOCATE;
   public static final BooleanValue RUT_FIGHTS;
   public static final BooleanValue RUT_CHASES;

   private HuntConfig() {
   }

   static {
      Builder var0 = new Builder();
      GENERATE_RANGER_CAMPS = var0.comment(
            "Add ranger camps in new eligible Overworld chunks. Restart before creating/exploring new terrain. Existing camps are retained."
         )
         .worldRestart()
         .define("generateRangerCamps", true);
      GENERATE_HUNTING_FORESTS = var0.comment(
            "Add tall pine/cedar trees in new eligible forest/taiga chunks. Existing chunks and terrain generators are preserved."
         )
         .worldRestart()
         .define("generateHuntingForests", true);
      FORESTS_OUTSIDE_RESERVE = var0.comment(
            "Also generate hunting trees in Overworlds without the Frontier reserve preset or frontierHunting rule. Restart required."
         )
         .worldRestart()
         .define("huntingForestsOutsideReserve", false);
      FOREST_DENSITY = var0.comment(
            "Average tree attempts per eligible chunk (1-6), out of six candidates; obstructions reduce successful placements. Restart required."
         )
         .worldRestart()
         .defineInRange("huntingForestDensity", 4, 1, 6);
      FOLIAGE_DENSITY = var0.comment(
            "How thick the forest floor generates, as a percentage. 100 is the intended look; drop it if you are chasing frames, raise it for a jungle. Applies to newly generated chunks only."
         )
         .defineInRange("forestFloorDensity", 100, 0, 300);
      WORLDGEN = var0.build();
      Builder var1 = new Builder();
      var1.push("reserve");
      STARTER_JOURNAL = var1.comment("Legacy, no effect: the Hunter's Journal is no longer an item. It lives inside the Frontier Handbook (J opens it). Any old journal a hunter gets is bound into their Handbook.") /* [onebook] */.define("issueJournalOnJoin", false); // [onboard2] was starterJournal=true: no journal on first join
      REALISM = var1.comment("Controls the amount of field assistance. Does not change terrain.").defineEnum("realism", HuntConfig.Realism.FIELD);
      SURVEY_REWARD = var1.comment("One-time Know Your Ground survey reward.").defineInRange("surveyRewardTokens", 25, 0, 10000);
      MAX_DISCOVERIES = var1.comment("Maximum unique biome records retained per hunter.").defineInRange("maxDiscoveries", 512, 3, 2048);
      SYNC_INTERVAL = var1.comment("Ticks between wilderness HUD snapshots.").defineInRange("syncIntervalTicks", 40, 20, 200);
      DAYS_PER_MONTH = var1.comment("Elapsed Minecraft days per reserve month. Calendar starts in September; daylight commands do not rewind it.")
         .defineInRange("daysPerReserveMonth", 7, 1, 60);
      WHITETAILS_OUTSIDE_RESERVE = var1.comment(
            "Let whitetail deer spawn naturally in ordinary Overworld forests, plains and meadows, not only in Frontier reserve worlds. Spawn eggs always work."
         )
         .define("whitetailsOutsideReserve", true);
      var1.pop();
      // [seasons]
      var1.push("seasons");
      SEASONS = var1.comment(
            "The world follows the reserve calendar: fall colour and leaf drop, bare winter forests, spring green-up, seasonal grass and deer/elk/moose coats. Off = the world looks the same all year (the calendar, rut and hunting seasons still run)."
         )
         .define("seasons", true);
      SEASONAL_SNOW = var1.comment(
            "Temperate biomes get snow instead of rain in winter; it settles (also under bare deciduous trees), lakes and rivers freeze, and it all melts again in spring. Deserts, jungles, savannas, badlands and oceans never get seasonal snow."
         )
         .define("seasonalSnow", true);
      var1.pop();
      com.formaworks.frontierhunts.perf.PerfConfig.server(var1); // [perf] wildlife AI throttling options
      com.formaworks.frontierhunts.ecology.EcologyConfig.server(var1); // [ecology] predation, kill sites, bone finds
      com.formaworks.frontierhunts.camload.CamLoadConfig.server(var1); // [camload] trail cameras keep their area loaded
      var1.push("weather"); com.formaworks.frontierhunts.weather.WeatherConfig.server(var1); var1.pop(); // [weather] seasonal weather events
      var1.push("atvFuel"); com.formaworks.frontierhunts.landscape.ride.rig.AtvFuelConfig.server(var1); var1.pop(); // [atvfuel] ATV gasoline
      var1.push("fieldSchool"); com.formaworks.frontierhunts.guide.GuideConfig.server(var1); var1.pop(); // [guide] new-player Field School
      var1.push("survival"); com.formaworks.frontierhunts.survival.SurvivalConfig.server(var1); var1.pop(); // [survival] Frontier Survival difficulty, spoilage, warmth, lean seasons
      var1.push("journal"); com.formaworks.frontierhunts.journal.JournalConfig.server(var1); var1.pop(); // [journal] hunter skills, ranks, journal
      var1.push("academy"); com.formaworks.frontierhunts.academy.AcademyConfig.server(var1); var1.pop(); // [academy] Ranger Academy training grounds
      var1.push("hunts"); com.formaworks.frontierhunts.hunts.HuntsConfig.server(var1); var1.pop(); // [hunts] species hunts, lures, milestone rewards
      var1.push("wingshot"); com.formaworks.frontierhunts.wingshot.WingshotConfig.server(var1); var1.pop(); // [wingshot] game-bird flight
      var1.push("licence"); com.formaworks.frontierhunts.licence.LicenceConfig.server(var1); var1.pop(); // [licence] hunting regulations: licences, tags, stamps
      // [routines] animal routines / hunting pressure tuning
      var1.push("herds"); com.formaworks.frontierhunts.hunting.herd.HerdConfig.server(var1); var1.pop(); // [herds] social groups of deer, elk and moose
      var1.push("wildlifeRoutines");
      ROUTINES_ENABLED = var1.comment("Deer, elk and moose keep a home range: a bedding thicket, a feeding area and water, joined by game trails they reuse. Off = the older free-roaming behaviour.")
         .define("routinesEnabled", true);
      ROUTINE_HOME_RANGE = var1.comment("Radius in blocks a whitetail herd searches for its bedding, feeding and water anchors (elk x1.5, moose x1.25).")
         .defineInRange("homeRangeBlocks", 64, 24, 160);
      SCENT_BUST_THRESHOLD = var1.comment("How strong a whiff of hunter scent makes a deer blow and bolt at once (lower = spookier). Weaker scent still builds alertness.")
         .defineInRange("scentBustThreshold", 0.35, 0.05, 2.0);
      PRESSURE_ENABLED = var1.comment("Animals remember hunting pressure per 64x64 block area: shots, hits, kills and busted animals push them nocturnal, make them warier and eventually move their home range.")
         .define("pressureEnabled", true);
      PRESSURE_HALF_LIFE_DAYS = var1.comment("In-game days for hunting pressure to fall by half.").defineInRange("pressureHalfLifeDays", 3.0, 0.25, 30.0);
      PRESSURE_SHOT_WEIGHT = var1.comment("Pressure added per gunshot (bow shots add a third; hits, kills and busted animals scale with it).")
         .defineInRange("pressurePerShot", 1.0, 0.0, 10.0);
      PRESSURE_NIGHT_SHIFT = var1.comment("How strongly pressure pushes daylight movement into the night (0 = never, 1 = fully nocturnal under heavy pressure).")
         .defineInRange("pressureNightShift", 1.0, 0.0, 1.0);
      PRESSURE_RELOCATE = var1.comment("Pressure an area needs before a herd abandons a bedding or feeding area there and moves its home range.")
         .defineInRange("pressureRelocateAt", 6.0, 1.0, 100.0);
      RUT_FIGHTS = var1.comment("Mature bucks and bulls that meet in the rut posture, circle and fight.").define("rutFights", true);
      RUT_CHASES = var1.comment("Does in estrus are trailed, chased and tended by bucks during the seeking and peak rut.").define("rutChases", true);
      var1.pop();
      SERVER = var1.build();
      Builder var2 = new Builder();
      var2.push("presentation");
      GRAPHICS_PRESET = var2.comment(
            // [presets] two presets: Vanilla (stored as CLASSIC) and Ultra; BALANCED and HIGH are retired values that load as ULTRA
            "Graphics preset. CLASSIC is the Vanilla preset: plain Minecraft-looking animals, grass and trees. ULTRA switches on realistic wildlife, the realistic world pack and the full effects. BALANCED and HIGH are retired values and are switched to ULTRA when the game loads. CUSTOM means you changed individual settings. Choosing a preset in the Frontier settings screen rewrites the settings below."
         )
         .defineEnum("graphicsPreset", HuntConfig.GraphicsPreset.ULTRA); // [presets] was BALANCED
      QUALITY = var2.comment("Cosmetic presentation budget; never changes server gameplay.").defineEnum("quality", HuntConfig.Quality.CINEMATIC); // [presets] Ultra default
      SHOW_HUD = var2.define("showWildernessHud", true);
      CINEMATICS = var2.comment("Optional, skippable hunt camera sequences. Movement, damage or Escape exits immediately.").define("bossCinematics", true);
      REDUCED_MOTION = var2.comment("Disables journal entrance motion and future optional cinematic motion.").define("reducedMotion", false);
      CAMERA_SHAKE = var2.comment("[1.1.5] Percent of camera shake: recoil kick, rapids tossing, deep-snow lurch, shivering. 0 keeps the view steady. Reduced motion also sets it to 0.")
         .defineInRange("cameraShake", 100, 0, 100);
      BLOOD_EFFECTS = var2.comment("[1.1.5] Blood spray and mist when an animal is hit: FULL, REDUCED (a third) or OFF. Blood trails on the ground stay, they are how you track.")
         .defineEnum("bloodEffects", HuntConfig.BloodEffects.FULL);
      HOUND_OUTLINE = var2.comment("[1.1.6] Outline your own tracking hound (blaze orange) when it is more than 4 blocks away, so you can follow it through brush.").define("houndOutline", true);
      CLASSIC_HANDBOOK = var2.comment("[1.1.7] The Handbook's 1.1.6 layout: every paragraph, finished tasks in full and the four books listed on Start here. Off (default) is the streamlined layout.").define("classicHandbook", false);
      // [killcam] slow-motion kill cam: OFF, LETHAL (every shot that drops the animal) or TROPHY (trophy-class animals only)
      KILLCAM = var2.comment("Slow-motion kill cam that follows a lethal bullet or arrow in. OFF, LETHAL (every shot that drops the animal) or TROPHY (trophy-class animals only). Any key or click skips it; Reduced motion turns it off.")
         .defineEnum("killCam", com.formaworks.frontierhunts.killcam.KillCamMode.LETHAL);
      KILLCAM_XRAY = var2.comment("Show the X-ray shot-placement moment in the kill cam.").define("killCamXray", true);
      KILLCAM_LENGTH = var2.comment("How long the kill cam lingers: SHORT, NORMAL or LONG.")
         .defineEnum("killCamLength", com.formaworks.frontierhunts.killcam.KillCamLength.NORMAL);
      var2.pop();
      var2.push("wildlife");
      ANIMAL_STYLE = var2.comment(
            "How the animals are drawn. VANILLA looks like a default Minecraft mob, REALISTIC is the full sculpted mesh with photographic fur. (BLOCKY is a retired value and loads as REALISTIC.) Cosmetic only - hit zones are identical."
         )
         .defineEnum("animalStyle", HuntConfig.AnimalStyle.REALISTIC); // [presets] was BLOCKY (retired with the Balanced preset)
      ANIMAL_DETAIL = var2.comment(
            "Realistic animals only: how far away animals keep their full-detail mesh, and the fur texture size (ULTRA uses 2048px fur)."
         )
         .defineEnum("animalDetail", HuntConfig.Detail.ULTRA); // [presets] Ultra default
      var2.pop();
      var2.push("world");
      WORLD_LOOK = var2.comment(
            "How the world itself is drawn. MINECRAFT keeps the normal blocky look. REALISTIC switches on Frontier's built-in realistic world pack: round tree trunks with real bark, bushy 3D leaves, photographic grass, dirt, stone, plants, flowers and sticks. Cosmetic only - blocks and hit boxes are unchanged."
         )
         .defineEnum("worldLook", HuntConfig.WorldLook.REALISTIC); // [presets] Ultra default
      TERRAIN = var2.comment(
            "Retired. Smooth terrain has been removed; the ground is always normal Minecraft blocks. This key is kept only so older config files still load, and any value is ignored."
         )
         .defineEnum("terrain", HuntConfig.Terrain.BLOCKS);
      GRASS_STYLE = var2.comment(
            "Meadow grass look. FRONTIER is the flowing Frontier sward, VANILLA draws ordinary Minecraft grass in its place, OFF hides it. Works with or without shaders."
         )
         .defineEnum("grassStyle", HuntConfig.GrassStyle.FRONTIER);
      GRASS_HEIGHT_PERCENT = var2.comment("How tall the meadow grass stands, in percent of normal.").defineInRange("grassHeightPercent", 110, 50, 150); // [presets] Ultra default
      GRASS_WIDTH_PERCENT = var2.comment("How wide each grass tuft spreads, in percent of normal.").defineInRange("grassWidthPercent", 100, 60, 150);
      GRASS_THICKNESS = var2.comment("How thick the meadow grass grows. THICK doubles the grass geometry and costs some frames.")
         .defineEnum("grassThickness", HuntConfig.GrassThickness.THICK); // [presets] Ultra default
      SNOW_STYLE = var2.comment(
            "How lying snow is drawn. FRONTIER is the realistic snowpack: one smooth deep blanket that drifts over steps and banks against walls, fine-grained snow that glints in the sun, boot and hoof prints and ploughed trenches. VANILLA draws ordinary Minecraft snow layers. Every preset uses FRONTIER (since 1.2.2); VANILLA can still be chosen here. Cosmetic only - wading and sinking work the same."
         )
         .defineEnum("snowStyle", HuntConfig.SnowStyle.FRONTIER); // [1.1.2]
      SNOW_STYLE_122 = var2.comment("Internal: set once 1.2.2 has moved this game to the smooth snowpack (switch snowStyle back to VANILLA afterwards if you want).")
         .define("snowStyleMoved122", false);
      WATERFALL_DETAIL = var2.comment("Waterfall sheets, spray, mist and pool splashes. OFF hides the extra waterfall visuals (the water itself stays).")
         .defineEnum("waterfallDetail", HuntConfig.EffectLevel.ULTRA); // [presets] Ultra default
      AMBIENT_SOUNDS = var2.comment("Wind in the trees, creeks, birds at dawn, owls and coyotes at night, insects in the meadows.")
         .define("ambientSounds", true);
      AMBIENT_VOLUME = var2.comment("Loudness of the wilderness ambience, in percent.").defineInRange("ambientVolume", 100, 0, 200);
      WILDLIFE_LIFE = var2.comment("Small birds that flush from cover, hawks and ravens overhead, fish rising on still water.").define("wildlifeLife", true);
      BREATH_VAPOR = var2.comment("Breath shows in the cold, for hunters and for the animals.").define("breathVapor", true);
      MOUNTAIN_SPINDRIFT = var2.comment("Snow blown off the high ridges and flurries up in the mountains.").define("mountainSpindrift", true);
      // [seasons]
      LEAF_FALL = var2.comment("Falling leaves in autumn forests. PERFORMANCE drifts a few, BALANCED is the intended look, CINEMATIC fills the air on a windy late-October day.")
         .defineEnum("leafFall", HuntConfig.Quality.BALANCED);
      var2.pop();
      var2.push("weather"); com.formaworks.frontierhunts.weather.WeatherConfig.client(var2); var2.pop(); // [weather] storm presentation
      var2.push("atvGrime"); com.formaworks.frontierhunts.landscape.ride.grime.AtvGrimeConfig.client(var2); var2.pop(); // [atvgrime] ATV mud/snow/water effects
      var2.push("fieldSchool"); com.formaworks.frontierhunts.guide.GuideConfig.client(var2); var2.pop(); // [guide] objective card + field notes
      var2.push("journal"); com.formaworks.frontierhunts.journal.JournalConfig.client(var2); var2.pop(); // [journal] XP ticker + toasts
      var2.push("sound"); com.formaworks.frontierhunts.sound.SoundMixConfig.client(var2); var2.pop(); // [gui] per-group volumes of the mod's own sounds
      var2.push("survival"); com.formaworks.frontierhunts.survival.SurvivalConfig.client(var2); var2.pop(); // [survival] survival HUD, units, frost
      var2.push("academy"); com.formaworks.frontierhunts.academy.AcademyConfig.client(var2); var2.pop(); // [academy] training card + cinematic beats
      var2.push("archery"); com.formaworks.frontierhunts.archery.BowConfig.client(var2); var2.pop(); // [bows] bow sights and aiming aids
      var2.push("arrivalCards"); com.formaworks.frontierhunts.regions.RegionsConfig.client(var2); var2.pop(); // [regions] region / biome arrival cards
      var2.push("wingshot"); com.formaworks.frontierhunts.wingshot.WingshotConfig.client(var2); var2.pop(); // [wingshot] bird hit effects, wing-shot slow motion
      var2.comment(
            "Packs switched on when you choose a preset. Resource pack: empty = leave your packs alone, otherwise the pack id (e.g. file/MyPack.zip). Shader pack: empty = leave Iris alone, OFF = shaders off, otherwise the shader pack file or folder name."
         )
         .push("presetPacks");
      String[] var3 = new String[]{"classic", "balanced", "high", "ultra"}; // [presets] "balanced"/"high" slots are retired (migrated into "ultra"), kept so old files load

      for (int var4 = 0; var4 < 4; var4++) {
         PRESET_RESOURCE_PACK[var4] = var2.define(var3[var4] + "ResourcePack", "");
         PRESET_SHADER_PACK[var4] = var2.define(var3[var4] + "ShaderPack", "");
      }

      var2.pop();
      com.formaworks.frontierhunts.perf.PerfConfig.client(var2); // [perf] tree detail distances, animal animation LOD
      CLIENT = var2.build();
   }

   public static enum AnimalStyle {
      VANILLA,
      /** [presets] Retired with the Balanced preset: only kept so old config files parse; loads as REALISTIC. */
      @Deprecated
      BLOCKY,
      REALISTIC;
   }

   public static enum Detail {
      LOW,
      MEDIUM,
      HIGH,
      ULTRA;
   }

   public static enum EffectLevel {
      OFF,
      LOW,
      MEDIUM,
      HIGH,
      ULTRA;
   }

   /** [1.1.5] comfort: blood spray amount */
   public static enum BloodEffects {
      FULL,
      REDUCED,
      OFF;
   }

   /** [1.1.5] 0..1 multiplier for camera shake (0 with Reduced motion) */
   public static float shake() {
      try {
         return REDUCED_MOTION.get() ? 0.0F : CAMERA_SHAKE.get() / 100.0F;
      } catch (RuntimeException e) {
         return 1.0F; // config not loaded yet
      }
   }

   public static enum GraphicsPreset {
      /** The Vanilla preset (shown as "Vanilla"; the stored value stays CLASSIC for old config files). */
      CLASSIC,
      /** [presets] Retired: only kept so old config files parse; switched to ULTRA on load (FrontierGraphics.migrate). */
      @Deprecated
      BALANCED,
      /** Retired: switched to ULTRA on load. */
      @Deprecated
      HIGH,
      ULTRA,
      CUSTOM;
   }

   public static enum GrassHeight {
      SHORT,
      NORMAL,
      TALL;
   }

   public static enum GrassStyle {
      FRONTIER,
      VANILLA,
      OFF;
   }

   /** [1.1.2] lying snow: the realistic Frontier snowpack or plain Minecraft layers */
   public static enum SnowStyle {
      FRONTIER,
      VANILLA;
   }

   public static enum GrassThickness {
      LIGHT,
      NORMAL,
      THICK;
   }

   public static enum Quality {
      PERFORMANCE,
      BALANCED,
      CINEMATIC;
   }

   public static enum Realism {
      ASSISTED,
      FIELD,
      EXPERT;
   }

   /** Retired: values kept only so old "terrain" config entries still parse. Always treated as BLOCKS. */
   @Deprecated
   public static enum Terrain {
      BLOCKS,
      SMOOTH,
      FULL;
   }

   public static enum WorldLook {
      MINECRAFT,
      REALISTIC;
   }
}
