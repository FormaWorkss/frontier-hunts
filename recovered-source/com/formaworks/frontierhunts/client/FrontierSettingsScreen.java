package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.client.settings.SettingsLayout;
import com.formaworks.frontierhunts.client.sound.FrontierSoundMixer;
import com.formaworks.frontierhunts.landscape.AlpineGrassModels;
import com.formaworks.frontierhunts.sound.FrontierSoundCategory;
import com.formaworks.frontierhunts.sound.SoundMixConfig;
import com.mojang.math.Axis;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.EnumValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

/**
 * Frontier Hunts settings.
 *
 * <p>[gui] Ten pages (graphics preset, trees & world, wildlife, atmosphere & weather, seasons, effects, sound, interface &
 * HUD, gameplay aids, performance). Every option has a label, a one-line description, a short tooltip with its performance
 * cost, and a badge when it reloads chunks/textures (applied once when the screen closes), needs a restart, or belongs to
 * the world/server. Geometry comes from {@link SettingsLayout} (checked offline by tools/gui/LayoutDump for every window
 * size and GUI scale); every string drawn per frame is fitted and measured once and cached ({@link Txt}).</p>
 *
 * <p>Other features add options with one line in {@link #buildPage()}: {@code this.toggle(label, desc, value, help)},
 * {@code this.choice(...)}, {@code this.slider(...)}; the overloads with a {@link Cost} add the performance hint.</p>
 */
public final class FrontierSettingsScreen extends Screen {
   static final int PANEL = -267184107;
   static final int PANEL_EDGE = -14011603;
   static final int SIDEBAR = -267579121;
   static final int ROW = 16777215;
   static final int ROW_HOVER = 587202559;
   static final int ACCENT = -1858493;
   static final int ACCENT_SOFT = 1440982083;
   static final int TEXT = -922140;
   static final int TEXT_DIM = -6050908;
   static final int TEXT_MUTED = -9603473;
   static final int GOOD = -7943798;
   static final int TRACK = -13946066;
   private static final FrontierUi.Size BODY = FrontierUi.Size.BODY;
   private static final FrontierUi.Size SMALL = FrontierUi.Size.SMALL;
   private static final FrontierUi.Size STRONG = FrontierUi.Size.STRONG;
   private static final Duration TOOLTIP_DELAY = Duration.ofMillis(300L);
   private static FrontierSettingsScreen.Page lastPage = FrontierSettingsScreen.Page.PRESETS;

   private final Screen parent;
   private final FrontierSettingsScreen.Session session;
   private FrontierSettingsScreen.Page page = lastPage;
   private SettingsLayout L;
   private final List<FrontierSettingsScreen.Item> items = new ArrayList<>();
   private final List<FrontierSettingsScreen.Row> rows = new ArrayList<>();
   private final List<FrontierSettingsScreen.PresetCard> cards = new ArrayList<>();
   private int scroll;
   private int maxScroll;
   /** Scratch for row geometry (render thread only). */
   final int[] geom = new int[4];
   private boolean irisLoaded;
   // header / footer text, built once per init
   private final FrontierSettingsScreen.Txt brandA = new FrontierSettingsScreen.Txt();
   private final FrontierSettingsScreen.Txt brandB = new FrontierSettingsScreen.Txt();
   private final FrontierSettingsScreen.Txt titleTxt = new FrontierSettingsScreen.Txt();
   private final FrontierSettingsScreen.Txt subtitleTxt = new FrontierSettingsScreen.Txt();
   private final FrontierSettingsScreen.Txt pillOn = new FrontierSettingsScreen.Txt();
   private final FrontierSettingsScreen.Txt pillOff = new FrontierSettingsScreen.Txt();
   private final FrontierSettingsScreen.Txt capPreset = new FrontierSettingsScreen.Txt();
   private final FrontierSettingsScreen.Txt capLoad = new FrontierSettingsScreen.Txt();
   private final FrontierSettingsScreen.Txt capClose = new FrontierSettingsScreen.Txt();
   private final FrontierSettingsScreen.Txt footPreset = new FrontierSettingsScreen.Txt();
   private final FrontierSettingsScreen.Txt footNotice = new FrontierSettingsScreen.Txt();
   final FrontierSettingsScreen.Txt badgeRestart = new FrontierSettingsScreen.Txt();
   final FrontierSettingsScreen.Txt badgeReload = new FrontierSettingsScreen.Txt();
   final FrontierSettingsScreen.Txt badgeWorld = new FrontierSettingsScreen.Txt();
   final FrontierSettingsScreen.Txt badgeServer = new FrontierSettingsScreen.Txt();
   final FrontierSettingsScreen.Txt txtOn = new FrontierSettingsScreen.Txt();
   final FrontierSettingsScreen.Txt txtOff = new FrontierSettingsScreen.Txt();
   final FrontierSettingsScreen.Txt txtActive = new FrontierSettingsScreen.Txt();
   final FrontierSettingsScreen.Txt txtYes = new FrontierSettingsScreen.Txt();
   final FrontierSettingsScreen.Txt txtNo = new FrontierSettingsScreen.Txt();
   private int footVersion = -1;
   private int footCost;
   private boolean footCustom;
   private boolean footPending;

   public FrontierSettingsScreen(Screen parent) {
      super(Component.literal("Frontier Hunts Settings"));
      this.parent = parent;
      FrontierGraphics.migrate();
      this.session = new FrontierSettingsScreen.Session();
      try {
         HuntConfig.GraphicsPreset match = FrontierGraphics.matching();
         if (HuntConfig.GRAPHICS_PRESET.get() != match) {
            HuntConfig.GRAPHICS_PRESET.set(match);
            this.session.client = true;
         }
      } catch (RuntimeException var3) {
      }
   }

   // ------------------------------------------------------------------------------------------------ build

   /** Builds only the open page (page switches and resizes re-run this; nothing is rebuilt per frame). */
   protected void init() {
      this.items.clear();
      this.rows.clear();
      this.cards.clear();
      this.irisLoaded = ModList.get().isLoaded("iris");
      FrontierSettingsScreen.Page[] pages = FrontierSettingsScreen.Page.values();
      this.L = SettingsLayout.of(this.width, this.height, pages.length);
      SettingsLayout l = this.L;
      this.footVersion = -1;
      this.badgeRestart.set("RESTART", SMALL, Integer.MAX_VALUE);
      this.badgeReload.set("RELOAD", SMALL, Integer.MAX_VALUE);
      this.badgeWorld.set("WORLD", SMALL, Integer.MAX_VALUE);
      this.badgeServer.set("SERVER", SMALL, Integer.MAX_VALUE);
      this.txtOn.set("On", BODY, Integer.MAX_VALUE);
      this.txtOff.set("Off", BODY, Integer.MAX_VALUE);
      this.txtActive.set("ACTIVE", SMALL, Integer.MAX_VALUE);
      this.txtYes.set("Yes", STRONG, Integer.MAX_VALUE);
      this.txtNo.set("No", STRONG, Integer.MAX_VALUE);
      this.capPreset.set("PRESET", SMALL, Integer.MAX_VALUE);
      this.capLoad.set("GPU LOAD", SMALL, Integer.MAX_VALUE);
      this.capClose.set("ON CLOSE", SMALL, Integer.MAX_VALUE);
      this.pillOn.set("Shader pack on", SMALL, Integer.MAX_VALUE);
      this.pillOff.set("No shader pack", SMALL, Integer.MAX_VALUE);
      int brandW = Math.max(10, l.sideW - 26);
      this.brandA.set("FRONTIER", FrontierUi.Size.BRAND, brandW);
      this.brandB.set("HUNTS", STRONG, brandW);
      int titleW = l.showPill && !l.compact ? l.contentW - 104 : l.contentW;
      this.titleTxt.set(this.page.title, FrontierUi.Size.TITLE, titleW);
      this.subtitleTxt.set(this.page.subtitle, BODY, l.contentW);

      if (l.compact) {
         this.addRenderableWidget(new FrontierSettingsScreen.PageSelector(l.selX, l.selY, l.selW, l.selH));
      } else {
         int ty = l.tabY;
         for (FrontierSettingsScreen.Page p : pages) {
            this.addRenderableWidget(new FrontierSettingsScreen.Tab(l.tabX, ty, l.tabW, l.tabH, p));
            ty += l.tabStep;
         }
      }

      this.addRenderableWidget(new FrontierSettingsScreen.FooterButton(l.doneX, l.btnY, l.doneW, l.btnH, "Done", true, this::onClose));
      if (this.page != FrontierSettingsScreen.Page.PRESETS) {
         this.addRenderableWidget(
            new FrontierSettingsScreen.FooterButton(l.resetX, l.btnY, l.resetW, l.btnH, l.shortReset ? "Reset" : "Reset page", false, this::resetPage)
         );
      }

      this.buildPage();
      this.layoutContent();
   }

   private void buildPage() {
      switch (this.page) {
         case PRESETS:
            this.add(new FrontierSettingsScreen.CardGrid());
            this.add(new FrontierSettingsScreen.PresetDetail());
            this.ultraTuning(); // [presets]
            this.section("Texture & shader packs per preset");
            this.add(new FrontierSettingsScreen.Status("Iris installed", () -> this.irisLoaded));
            this.add(new FrontierSettingsScreen.Status("Shader pack active right now", FrontierGraphics::shaderPackActive));
            this.shaderLighterToggle(); // [shaderperf]
            this.add(
               new FrontierSettingsScreen.Info(
                  "Frontier works with any shader pack - nothing extra to install. With a pack on, Frontier hands lighting, shadows, sky and fog to it; animals, blood and gear use Minecraft's own render types so every pack shades them."
               )
            );

            for (HuntConfig.GraphicsPreset preset : PRESETS) { // [presets]
               ConfigValue<String> tex = PresetPacks.resourceValue(preset);
               ConfigValue<String> sh = PresetPacks.shaderValue(preset);
               this.add(
                  new FrontierSettingsScreen.ListRow(
                     title(preset) + " textures",
                     "Your texture pack switched on with " + title(preset),
                     "Switched on whenever you pick the " + title(preset) + " preset; the pack of the preset you leave is switched off. Don't change = leave your packs alone.",
                     tex::get,
                     v -> {
                        tex.set(v);
                        this.clientChanged();
                     },
                     PresetPacks::resourcePackIds,
                     PresetPacks::resourcePackName,
                     true
                  )
               );
               this.add(
                  new FrontierSettingsScreen.ListRow(
                     title(preset) + " shaders",
                     "Shader pack used with " + title(preset),
                     "A shader pack from your shaderpacks folder (or Shaders off) for the " + title(preset) + " preset. Needs Iris. Don't change = leave Iris alone.",
                     sh::get,
                     v -> {
                        sh.set(v);
                        this.clientChanged();
                     },
                     PresetPacks::shaderPackNames,
                     PresetPacks::shaderPackName,
                     PresetPacks.irisPresent()
                  )
               );
            }
            break;
         case WORLD:
            this.section("World look");
            this.choice(
               "World look",
               "Minecraft blocks, or Frontier's realistic world",
               HuntConfig.WORLD_LOOK,
               HuntConfig.WorldLook.values(),
               v -> {
                  return switch (v) {
                     case MINECRAFT -> "Minecraft";
                     case REALISTIC -> "Realistic";
                  };
               },
               "Realistic: round trunks with real bark, bushy 3D leaves, photographic ground and plants. Blocks and hit boxes never change; works with any shader pack.",
               FrontierSettingsScreen.Cost.HIGH,
               FrontierSettingsScreen.Reload.TEXTURES
            );
            this.section("Meadow grass");
            this.choice(
               "Grass style",
               "Frontier sward, Minecraft grass, or hidden",
               HuntConfig.GRASS_STYLE,
               HuntConfig.GrassStyle.values(),
               v -> {
                  return switch (v) {
                     case FRONTIER -> "Frontier";
                     case VANILLA -> "Minecraft";
                     case OFF -> "Hidden";
                  };
               },
               "Frontier is the flowing meadow grass, Minecraft draws the plain short-grass model, Hidden removes it (cover still works).",
               FrontierSettingsScreen.Cost.MEDIUM,
               FrontierSettingsScreen.Reload.CHUNKS
            );
            this.slider(
               "Grass height",
               "How tall the meadow grass stands",
               HuntConfig.GRASS_HEIGHT_PERCENT,
               50,
               150,
               5,
               v -> v + "%",
               "100% is the intended look. Taller grass hides deer - and you.",
               FrontierSettingsScreen.Cost.LOW,
               FrontierSettingsScreen.Reload.CHUNKS
            );
            this.slider(
               "Grass width",
               "How wide each tuft spreads",
               HuntConfig.GRASS_WIDTH_PERCENT,
               60,
               150,
               5,
               v -> v + "%",
               "Wider tufts fill the meadow in with the same number of blades.",
               FrontierSettingsScreen.Cost.LOW,
               FrontierSettingsScreen.Reload.CHUNKS
            );
            this.choice(
               "Grass thickness",
               "Blades per block - the frame-rate setting",
               HuntConfig.GRASS_THICKNESS,
               HuntConfig.GrassThickness.values(),
               FrontierSettingsScreen::title,
               "Light halves the blades, Thick doubles them.",
               FrontierSettingsScreen.Cost.HIGH,
               FrontierSettingsScreen.Reload.CHUNKS
            );
            this.section("Snow"); // [1.1.2]
            this.choice(
               "Snow style",
               "Realistic snowpack or Minecraft snow",
               HuntConfig.SNOW_STYLE,
               HuntConfig.SnowStyle.values(),
               v -> {
                  return switch (v) {
                     case FRONTIER -> "Frontier";
                     case VANILLA -> "Minecraft";
                  };
               },
               "Frontier: one smooth, deep snow blanket that drifts over steps and banks against walls, fine snow that glints in the sun, and the prints and trenches you leave. On in Ultra; switch it on here in Vanilla too. Cosmetic only.",
               FrontierSettingsScreen.Cost.MEDIUM,
               FrontierSettingsScreen.Reload.CHUNKS
            );
            this.section("New terrain");
            this.worldSlider(
               "Forest floor density",
               "Litter, ferns and brush in new chunks",
               HuntConfig.FOLIAGE_DENSITY,
               0,
               300,
               10,
               v -> v + "%",
               "Applies to newly generated chunks only. 100% is the intended look.",
               FrontierSettingsScreen.Cost.MEDIUM
            );
            this.worldToggle(
               "Hunting forests",
               "Tall pine and cedar stands",
               HuntConfig.GENERATE_HUNTING_FORESTS,
               true,
               "Adds tall pines and cedars in new forest and taiga chunks. Existing chunks are never changed."
            );
            this.worldToggle(
               "Forests outside reserves", "Also in ordinary worlds", HuntConfig.FORESTS_OUTSIDE_RESERVE, true, "Also grow hunting forests in worlds that are not Frontier reserves."
            );
            this.worldSlider(
               "Forest density",
               "Tree attempts per chunk (of six)",
               HuntConfig.FOREST_DENSITY,
               1,
               6,
               1,
               v -> v + " / 6",
               "Average tree attempts per eligible new chunk.",
               FrontierSettingsScreen.Cost.LOW
            );
            this.worldToggle(
               "Ranger camps", "Furnished camps in new chunks", HuntConfig.GENERATE_RANGER_CAMPS, true, "Adds ranger camps in new eligible chunks. Existing camps are kept."
            );
            break;
         case WILDLIFE:
            this.choice(
               "Animal style",
               "Minecraft mobs or realistic animals",
               HuntConfig.ANIMAL_STYLE,
               new HuntConfig.AnimalStyle[]{HuntConfig.AnimalStyle.VANILLA, HuntConfig.AnimalStyle.REALISTIC}, // [presets] Minecraft+ retired
               v -> v == HuntConfig.AnimalStyle.VANILLA ? "Minecraft" : "Realistic",
               "Minecraft: box mobs, as in the Vanilla preset. Realistic: sculpted mesh with photographic fur, as in Ultra. Hit zones and rewards are identical.",
               FrontierSettingsScreen.Cost.VARIES,
               FrontierSettingsScreen.Reload.NONE
            );
            this.choice(
               "Realistic detail",
               "Full-detail distance and fur size",
               HuntConfig.ANIMAL_DETAIL,
               HuntConfig.Detail.values(),
               FrontierSettingsScreen::title,
               "Realistic style only. Ultra keeps every nearby animal at full detail with 2048px fur - the heaviest setting in the mod.",
               FrontierSettingsScreen.Cost.HIGH,
               FrontierSettingsScreen.Reload.NONE
            );
            this.toggle(
               "Small wildlife",
               "Flushing birds, hawks overhead, rising fish",
               HuntConfig.WILDLIFE_LIFE,
               "Songbirds bursting from brush as you pass, hawks and ravens overhead, fish dimpling still water.",
               FrontierSettingsScreen.Cost.LOW
            );
            this.choice(
               "Bird hit effects",
               "When a duck or grouse is hit",
               com.formaworks.frontierhunts.wingshot.WingshotConfig.HIT_EFFECTS,
               com.formaworks.frontierhunts.wingshot.WingshotConfig.HitEffects.values(),
               v -> switch (v) {
                  case OFF -> "Off";
                  case NORMAL -> "Normal";
                  case GRAPHIC -> "Graphic";
               },
               "Graphic: a burst of dozens of feathers in the bird's colours, down, blood mist and spatter on the ground, blocks and water. Normal: fewer feathers, a little blood. Cosmetic only.",
               FrontierSettingsScreen.Cost.LOW,
               FrontierSettingsScreen.Reload.NONE
            ); // [wingshot]
            this.toggle(
               "Wing-shot slow motion",
               "A clean wing shot slows time a moment",
               com.formaworks.frontierhunts.wingshot.WingshotConfig.SLOW_MO,
               "Drop a bird in the air with one shot and the feathers hang while it folds and falls, for about a second. Off with Reduced motion.",
               FrontierSettingsScreen.Cost.NONE
            ); // [wingshot]
            break;
         case ATMOSPHERE:
            this.toggle("Breath vapor", "Breath fogs in the cold", HuntConfig.BREATH_VAPOR, "Your breath and the animals' breath show in cold weather.", FrontierSettingsScreen.Cost.LOW);
            this.toggle(
               "Mountain spindrift", "Snow blown off the high ridges", HuntConfig.MOUNTAIN_SPINDRIFT, "Snow streaming off ridges and flurries up high.", FrontierSettingsScreen.Cost.LOW
            );
            this.section("Storms");
            this.serverToggle(
               "Seasonal weather",
               "Blizzards, storms, dust, fog, gales",
               com.formaworks.frontierhunts.weather.WeatherConfig.ENABLED,
               "Blizzards in snowy regions in winter, thunderstorms, desert dust storms, valley fog and fall wind storms. Off = vanilla weather only."
            );
            this.serverSlider(
               "Storm frequency",
               "How often weather events happen",
               com.formaworks.frontierhunts.weather.WeatherConfig.FREQUENCY,
               0,
               400,
               10,
               v -> v + "%",
               "100% is intended, 0% only forced events, 300% very stormy."
            );
            // [weather]
            this.choice(
               "Storm effects",
               "Snow streaks, veils, sand and rain",
               com.formaworks.frontierhunts.weather.WeatherConfig.QUALITY,
               com.formaworks.frontierhunts.weather.WeatherConfig.Quality.values(),
               FrontierSettingsScreen::level, // [presets]
               "How much a storm draws. Auto follows Effects quality; visibility in a storm is the same at every setting.",
               FrontierSettingsScreen.Cost.VARIES,
               FrontierSettingsScreen.Reload.NONE
            );
            this.toggle(
               "Storm screen layer",
               "Needed for whiteouts with shader packs",
               com.formaworks.frontierhunts.weather.WeatherConfig.OVERLAY,
               "Shader packs ignore Minecraft's fog; this layer washes out the horizon in blizzards and dust storms. Keep it on with shaders.",
               FrontierSettingsScreen.Cost.LOW
            ); // [weather]
            break;
         case SEASONS:
            this.serverToggle(
               "Seasons",
               "Fall colour, bare winters, spring green-up",
               HuntConfig.SEASONS,
               "The world follows the reserve calendar: leaf colour and drop, bare winter forests, seasonal grass and coats. Off = the same look all year."
            );
            this.serverToggle(
               "Seasonal snow",
               "Winter snow that settles and melts",
               HuntConfig.SEASONAL_SNOW,
               "Temperate biomes get snow instead of rain in winter; it settles, lakes freeze, and it all melts in spring."
            );
            this.choice(
               "Falling leaves",
               "Autumn leaves drifting down",
               HuntConfig.LEAF_FALL,
               HuntConfig.Quality.values(),
               FrontierSettingsScreen::level, // [presets]
               "Performance drifts a few leaves, Standard is the intended look, Cinematic fills the air on windy late-October days.",
               FrontierSettingsScreen.Cost.VARIES,
               FrontierSettingsScreen.Reload.NONE
            ); // [seasons]
            break;
         case EFFECTS:
            this.choice(
               "Effects quality",
               "Blood, flares, optics, field particles",
               HuntConfig.QUALITY,
               HuntConfig.Quality.values(),
               FrontierSettingsScreen::level, // [presets]
               "Particle counts and detail of blood, flares, scopes and the anatomy view. Hit detection and rewards are identical.",
               FrontierSettingsScreen.Cost.VARIES,
               FrontierSettingsScreen.Reload.NONE
            );
            this.choice(
               "Waterfall detail",
               "Sheets, spray, mist and pool splashes",
               HuntConfig.WATERFALL_DETAIL,
               HuntConfig.EffectLevel.values(),
               FrontierSettingsScreen::title,
               "Off hides the extra waterfall visuals (the water stays). Scales on top of Minecraft's Particles setting.",
               FrontierSettingsScreen.Cost.VARIES,
               FrontierSettingsScreen.Reload.NONE
            );
            this.section("ATV grime");
            this.toggle(
               "ATV screen splatter",
               "Mud, snow and water on your view",
               com.formaworks.frontierhunts.landscape.ride.grime.AtvGrimeConfig.SCREEN_SPLATTER,
               "First person on an ATV: mud, snow and water splash onto your view, then dry, melt or run off.",
               FrontierSettingsScreen.Cost.LOW
            ); // [atvgrime]
            this.toggle(
               "ATV wheel spray",
               "Mud clods, snow powder, water spray",
               com.formaworks.frontierhunts.landscape.ride.grime.AtvGrimeConfig.SPRAY,
               "Spray thrown from the wheels; the amount follows Effects quality and Minecraft's Particles setting.",
               FrontierSettingsScreen.Cost.LOW
            ); // [atvgrime]
            break;
         case SOUND:
            this.section("Volume");
            for (FrontierSoundCategory c : FrontierSoundCategory.values()) {
               this.soundSlider(c);
            }
            this.section("Sound layers");
            this.toggle(
               "Wilderness ambience",
               "Wind, creeks, birds, insects",
               HuntConfig.AMBIENT_SOUNDS,
               "Wind in the trees, creeks, birds at dawn, owls and coyotes at night, insects in the meadows.",
               FrontierSettingsScreen.Cost.LOW
            );
            this.toggle(
               "Storm sounds",
               "Blizzard roar, gusts, downpour, gale",
               com.formaworks.frontierhunts.weather.WeatherConfig.SOUNDS,
               "Storm sound beds and gusts arriving from upwind; they duck indoors.",
               FrontierSettingsScreen.Cost.NONE
            ); // [weather]
            break;
         case INTERFACE:
            this.toggle("Wilderness HUD", "Wind, time and season readout", HuntConfig.SHOW_HUD, "The small wilderness readout on screen.", FrontierSettingsScreen.Cost.NONE);
            this.toggle(
               "Hunt cinematics",
               "Optional, skippable hunt cameras",
               HuntConfig.CINEMATICS,
               "Movement, damage or Escape always exits a cinematic immediately.",
               FrontierSettingsScreen.Cost.NONE
            );
            this.toggle(
               "Reduced motion",
               "Calmer menus and cameras",
               HuntConfig.REDUCED_MOTION,
               "Turns off journal entrance motion, optional cinematic camera motion, camera shake and the kill cam.",
               FrontierSettingsScreen.Cost.NONE
            );
            this.section("Comfort"); // [1.1.5] motion and graphic-content controls in one place
            this.slider(
               "Camera shake",
               "Recoil kick, rapids, snow lurch, shivers",
               HuntConfig.CAMERA_SHAKE,
               0,
               100,
               10,
               v -> v == 0 ? "Off" : v + "%",
               "How much the view is thrown about: gun recoil, being tossed in rapids, lurching through deep snow and shivering in the cold. Off keeps the view steady; Reduced motion also turns it off. Aim is unaffected.",
               FrontierSettingsScreen.Cost.NONE,
               FrontierSettingsScreen.Reload.NONE
            );
            this.toggle(
               "Hound outline",
               "See your tracking dog through brush",
               HuntConfig.HOUND_OUTLINE,
               "Your own hound gets a blaze-orange outline once it is more than 4 blocks away, so you never lose it in tall grass or timber. Only you see it.",
               FrontierSettingsScreen.Cost.NONE
            ); // [1.1.6]
            this.toggle(
               "Classic Handbook",
               "The Handbook's older, fuller layout",
               HuntConfig.CLASSIC_HANDBOOK,
               "Off: the streamlined Handbook (the next step, your progress, one paragraph per step with Read more, finished tasks folded away). On: the 1.1.6 layout with everything shown at once.",
               FrontierSettingsScreen.Cost.NONE
            ); // [1.1.7]
            this.choice(
               "Blood effects",
               "Blood spray and drips on hits",
               HuntConfig.BLOOD_EFFECTS,
               HuntConfig.BloodEffects.values(),
               v -> switch (v) {
                  case FULL -> "Full";
                  case REDUCED -> "Reduced";
                  case OFF -> "Off";
               },
               "Reduced draws a third of the blood spray, Off none. Blood trail sign on the ground always stays: it is how you follow a wounded animal. Also see Kill cam X-ray (organ view) and Bird hit effects (Wildlife page).",
               FrontierSettingsScreen.Cost.NONE,
               FrontierSettingsScreen.Reload.NONE
            );
            this.section("Arrival cards"); // [regions] cinematic region / biome title card
            this.choice(
               "Arrival cards",
               "Region and biome title when you arrive",
               com.formaworks.frontierhunts.regions.RegionsConfig.MODE,
               com.formaworks.frontierhunts.regions.RegionsConfig.Mode.values(),
               m -> switch (m) {
                  case OFF -> "Off";
                  case REGIONS -> "Regions only";
                  case REGIONS_AND_BIOMES -> "Regions + biomes";
               },
               "The join card's cinematic title each time you enter a new region or biome. Never takes the camera; held back in menus, fights and cinematics.",
               FrontierSettingsScreen.Cost.NONE,
               FrontierSettingsScreen.Reload.NONE
            ); // [regions]
            this.slider(
               "Arrival card length",
               "How long the title stays up",
               com.formaworks.frontierhunts.regions.RegionsConfig.DURATION,
               3,
               8,
               1,
               v -> v + " s",
               "Seconds on screen, fades included.",
               FrontierSettingsScreen.Cost.NONE,
               FrontierSettingsScreen.Reload.NONE
            ); // [regions]
            this.slider(
               "Repeat after",
               "Same biome is not announced again sooner",
               com.formaworks.frontierhunts.regions.RegionsConfig.REPEAT_MINUTES,
               1,
               30,
               1,
               v -> v + " min",
               "A different region is always announced; the same biome waits this long.",
               FrontierSettingsScreen.Cost.NONE,
               FrontierSettingsScreen.Reload.NONE
            ); // [regions]
            this.section("Kill cam");
            this.choice(
               "Kill cam",
               "Slow-motion replay of lethal shots",
               HuntConfig.KILLCAM,
               com.formaworks.frontierhunts.killcam.KillCamMode.values(),
               m -> switch (m) {
                  case OFF -> "Off";
                  case LETHAL -> "Lethal shots";
                  case TROPHY -> "Trophy only";
               },
               "Follows a lethal bullet or arrow in. Any key or click skips it.",
               FrontierSettingsScreen.Cost.LOW,
               FrontierSettingsScreen.Reload.NONE
            ); // [killcam]
            this.toggle(
               "Kill cam X-ray",
               "Shot placement view",
               HuntConfig.KILLCAM_XRAY,
               "Organs and the wound channel for a moment after the hit.",
               FrontierSettingsScreen.Cost.NONE
            ); // [killcam]
            this.choice(
               "Kill cam length",
               "How long it lingers",
               HuntConfig.KILLCAM_LENGTH,
               com.formaworks.frontierhunts.killcam.KillCamLength.values(),
               l -> switch (l) {
                  case SHORT -> "Short";
                  case NORMAL -> "Normal";
                  case LONG -> "Long";
               },
               "Only the presentation length changes.",
               FrontierSettingsScreen.Cost.NONE,
               FrontierSettingsScreen.Reload.NONE
            ); // [killcam]
            this.section("Archery"); // [bows] bow sights and aiming aids
            this.toggle("Compound pin sight", "Pins set to the arrow's real flight", com.formaworks.frontierhunts.archery.BowConfig.PIN_SIGHT, "Compound bows get a multi-pin sight, the crossbow matching range dots. Hold a pin on an animal at that distance and the arrow lands there.", FrontierSettingsScreen.Cost.NONE); // [bows]
            this.choice("Pin distances", "Metres each pin and dot is set for", com.formaworks.frontierhunts.archery.BowConfig.PINS, com.formaworks.frontierhunts.archery.BowConfig.PinSet.values(), com.formaworks.frontierhunts.archery.BowConfig.PinSet::title, "Used by the compound pins and the crossbow dots.", FrontierSettingsScreen.Cost.NONE, FrontierSettingsScreen.Reload.NONE); // [bows] [archery2]
            this.toggle("Peep sight", "The ring you look through at full draw", com.formaworks.frontierhunts.archery.BowConfig.PEEP, "Compound bows: frames the pin housing like a real peep.", FrontierSettingsScreen.Cost.NONE); // [bows]
            this.toggle("Impact dot", "Recurves: where the arrow will strike", com.formaworks.frontierhunts.archery.BowConfig.TIP_REFERENCE, "Recurve and Field Recurve: at full draw a small dot replaces the crosshair, exactly where the arrow will hit at any distance. Put it on the spot and release.", FrontierSettingsScreen.Cost.NONE); // [bows] [archery2]
            this.toggle("Bow range readout", "With a rangefinder in your pack", com.formaworks.frontierhunts.archery.BowConfig.RANGE_READOUT, "At full draw, the distance to the animal under your sight.", FrontierSettingsScreen.Cost.NONE); // [bows]
            this.section(net.minecraft.client.resources.language.I18n.get("guide.frontierhunts.settings.section")); // [guide]
            this.toggle(
               net.minecraft.client.resources.language.I18n.get("guide.frontierhunts.settings.card"),
               net.minecraft.client.resources.language.I18n.get("guide.frontierhunts.settings.card_desc"),
               com.formaworks.frontierhunts.guide.GuideConfig.HUD_CARD,
               net.minecraft.client.resources.language.I18n.get("guide.frontierhunts.settings.card_help"),
               FrontierSettingsScreen.Cost.NONE
            ); // [guide]
            this.toggle(
               net.minecraft.client.resources.language.I18n.get("guide.frontierhunts.settings.tips"),
               net.minecraft.client.resources.language.I18n.get("guide.frontierhunts.settings.tips_desc"),
               com.formaworks.frontierhunts.guide.GuideConfig.CLIENT_TIPS,
               net.minecraft.client.resources.language.I18n.get("guide.frontierhunts.settings.tips_help"),
               FrontierSettingsScreen.Cost.NONE
            ); // [guide]
            this.section("Ranger Academy"); // [academy] training-grounds presentation (integ5)
            this.toggle(
               "Training card",
               "Objectives, timer and wind while training",
               com.formaworks.frontierhunts.academy.AcademyConfig.HUD,
               "The field card in the top-left corner while you are in the training grounds.",
               FrontierSettingsScreen.Cost.NONE
            ); // [academy]
            this.toggle(
               "Academy cinematics",
               "Title cards, fades and the pass moment",
               com.formaworks.frontierhunts.academy.AcademyConfig.CINEMATIC,
               "Arrival title card, departure and return fades and the course-passed moment.",
               FrontierSettingsScreen.Cost.NONE
            ); // [academy]
            this.section("Frontier survival"); // [integ4] survival HUD rows get their own heading
            this.toggle(
               "Survival HUD",
               "Nutrition bars and body temperature",
               com.formaworks.frontierhunts.survival.SurvivalConfig.HUD,
               "Protein, fat and energy above the hunger bar and the body-temperature gauge above the armour bar. Hidden anyway when Frontier survival is Off.",
               FrontierSettingsScreen.Cost.NONE
            ); // [survival]
            this.choice(
               "Temperature units",
               "Celsius or Fahrenheit",
               com.formaworks.frontierhunts.survival.SurvivalConfig.UNITS,
               com.formaworks.frontierhunts.survival.SurvivalConfig.Units.values(),
               FrontierSettingsScreen::title,
               "Units for the survival HUD and tooltips.",
               FrontierSettingsScreen.Cost.NONE,
               FrontierSettingsScreen.Reload.NONE
            ); // [survival]
            this.toggle(
               "Frost and shivers",
               "Frosted screen edges, trembling view",
               com.formaworks.frontierhunts.survival.SurvivalConfig.FROST,
               "While you are freezing, frost creeps in at the screen edges and the first-person view trembles a little (also off with Reduced motion).",
               FrontierSettingsScreen.Cost.NONE
            ); // [survival]
            this.section(net.minecraft.client.resources.language.I18n.get("journal.frontierhunts.settings.section")); // [journal]
            this.toggle(
               net.minecraft.client.resources.language.I18n.get("journal.frontierhunts.settings.toasts"),
               net.minecraft.client.resources.language.I18n.get("journal.frontierhunts.settings.toasts_desc"),
               com.formaworks.frontierhunts.journal.JournalConfig.TOASTS,
               net.minecraft.client.resources.language.I18n.get("journal.frontierhunts.settings.toasts_help"),
               FrontierSettingsScreen.Cost.NONE
            ); // [journal]
            this.toggle(
               net.minecraft.client.resources.language.I18n.get("journal.frontierhunts.settings.ticker"),
               net.minecraft.client.resources.language.I18n.get("journal.frontierhunts.settings.ticker_desc"),
               com.formaworks.frontierhunts.journal.JournalConfig.XP_TICKER,
               net.minecraft.client.resources.language.I18n.get("journal.frontierhunts.settings.ticker_help"),
               FrontierSettingsScreen.Cost.NONE
            ); // [journal]
            break;
         case AIDS:
            this.serverChoice(
               "Field assistance",
               "How forgiving deer are to your mistakes",
               HuntConfig.REALISM,
               HuntConfig.Realism.values(),
               FrontierSettingsScreen::title,
               "Assisted: animals notice you less. Field: the intended hunt. Expert: animals spook sooner."
            );
            this.serverToggle(
               "Hunting pressure",
               "Animals remember shots and busts",
               HuntConfig.PRESSURE_ENABLED,
               "Shots, kills and busted animals push deer nocturnal, make them warier and eventually move their range. Off = an easier hunt."
            );
            // [onebook] "Journal on first join" toggle removed: the Hunter's Journal is no longer an item (it lives in the Handbook)
            // [survival] Frontier Survival (world options)
            this.serverChoice(
               "Frontier survival",
               "Hunt to live: nutrition, warmth, spoilage",
               com.formaworks.frontierhunts.survival.SurvivalConfig.DIFFICULTY,
               com.formaworks.frontierhunts.survival.SurvivalConfig.Difficulty.values(),
               FrontierSettingsScreen::title,
               "Off: vanilla hunger only. Light: protein, fat, energy and body warmth are tracked, no health loss, wild game gives Hunter's Vigor. Balanced: a farm diet keeps you alive but weaker, neglect slowly costs health, winter cold can kill. Hardcore: farm food barely sustains, without game you starve."
            );
            this.serverToggle(
               "Meat spoils",
               "Raw and cooked meat keeps a few days",
               com.formaworks.frontierhunts.survival.SurvivalConfig.SPOILAGE,
               "Meat and fish go off over a few in-game days, faster in heat, slower in cold storage. Smoke, dry, salt or freeze it."
            );
            this.serverToggle(
               "Body temperature",
               "Cold, wind and wet against your clothes",
               com.formaworks.frontierhunts.survival.SurvivalConfig.TEMPERATURE,
               "Season, altitude, weather, wind and wetness against clothing, shelter and fire. Hides and furs keep you warm."
            );
            this.serverToggle(
               "Lean seasons",
               "Thin herds in late winter, fat game in fall",
               com.formaworks.frontierhunts.survival.SurvivalConfig.LEAN_SEASONS,
               "Late-winter animals are scarce and lean (less meat, no fat); fall animals are fattest. Stock up in the fall."
            );
            this.serverChoice( // [licence]
               "Hunting regulations",
               "Licences, tags, open seasons, bag limits",
               com.formaworks.frontierhunts.licence.LicenceConfig.MODE,
               com.formaworks.frontierhunts.licence.LicenceConfig.Mode.values(),
               FrontierSettingsScreen::title,
               "Off: hunt anything, anytime. Relaxed: carry a Hunting Licence, a tag for big game and a stamp for birds, in season; a warning, then small token fines. Strict: fines, strikes, half the meat and the trophy seized; 3 strikes suspend the licence for the season. Coyotes, wolves and boar never need one."
            );
            break;
         case PERFORMANCE:
            // [perf]
            this.choice(
               "Distant trees",
               "Where realistic trees become cutouts",
               com.formaworks.frontierhunts.perf.PerfConfig.TREE_DETAIL,
               com.formaworks.frontierhunts.perf.PerfConfig.TreeDetail.values(),
               FrontierSettingsScreen::level, // [presets]
               "Realistic world only. Full detail / cutouts: Performance 24 / 64 blocks, Standard 32 / 96, Ultra 48 / 160, Maximum 64 / never.",
               FrontierSettingsScreen.Cost.VARIES,
               FrontierSettingsScreen.Reload.NONE
            );
            this.shaderLighterToggle(); // [shaderperf]
            this.toggle(
               "Distant animal animation",
               "Far animals animate at a lower rate",
               com.formaworks.frontierhunts.perf.PerfConfig.ANIMAL_ANIMATION_LOD,
               "Far or small animals are posed 20-60 times a second instead of every frame; never in a scope. Turning it off costs frames.",
               FrontierSettingsScreen.Cost.NONE
            ); // [perf]
            this.section("Diagnostics");
            this.toggle(
               "Hitch logger",
               "Log slow frames to frontierhunts-perf.log",
               com.formaworks.frontierhunts.perf.PerfConfig.HITCH_LOGGER,
               "Records frames slower than the threshold with what the mod was doing. Leave off unless you are measuring.",
               FrontierSettingsScreen.Cost.LOW
            ); // [perf2]
            this.slider(
               "Hitch threshold",
               "A frame slower than this is a hitch",
               com.formaworks.frontierhunts.perf.PerfConfig.HITCH_THRESHOLD_MS,
               5,
               200,
               5,
               v -> v + " ms",
               "Only used by the hitch logger.",
               FrontierSettingsScreen.Cost.NONE,
               FrontierSettingsScreen.Reload.NONE
            ); // [perf2]
      }
   }

   /**
    * [shaderperf] "Lighter with shader packs": while an Iris pack is on, trees one detail step lighter and Thick grass drawn
    * Normal. Not part of the preset match (like the other performance toggles), so it never turns a preset Custom.
    */
   private void shaderLighterToggle() {
      this.toggle(
         "Lighter with shader packs",
         "Trees a step lighter while a pack is on",
         com.formaworks.frontierhunts.perf.PerfConfig.SHADER_LIGHTER,
         "Shader packs draw the world twice or more (sun shadows, then the scene). While one is on, realistic trees use the next lower Distant trees step (Ultra draws as Standard) and Thick grass draws Normal. Nothing changes without a shader pack. Off: your own settings under shaders too (costs frames).",
         FrontierSettingsScreen.Cost.NONE
      );
   }

   private void add(FrontierSettingsScreen.Item item) {
      this.items.add(item);
      if (item instanceof FrontierSettingsScreen.Row row) {
         this.rows.add(row);
      }
      if (item.interactive()) {
         this.addWidget(item);
      }
   }

   private void section(String label) {
      this.add(new FrontierSettingsScreen.Section(label));
   }

   // ------------------------------------------------------------------------------------------------ option helpers

   /** Marks a client option changed: the preset falls back to the matching one (or Custom); saved once on close. */
   void clientChanged() {
      this.session.client = true;
      this.session.version++;
      try {
         HuntConfig.GRAPHICS_PRESET.set(FrontierGraphics.matching());
      } catch (RuntimeException var2) {
      }
   }

   private void worldChanged() {
      this.session.world = true;
      this.session.version++;
   }

   private void serverChanged() {
      this.session.server = true;
      this.session.version++;
   }

   private <E extends Enum<E>> void choice(String label, String desc, EnumValue<E> value, E[] values, Function<E, String> name, String help) {
      this.choice(label, desc, value, values, name, help, FrontierSettingsScreen.Cost.UNSPECIFIED, FrontierSettingsScreen.Reload.NONE);
   }

   private <E extends Enum<E>> void choice(
      String label,
      String desc,
      EnumValue<E> value,
      E[] values,
      Function<E, String> name,
      String help,
      FrontierSettingsScreen.Cost cost,
      FrontierSettingsScreen.Reload reload
   ) {
      this.add(new FrontierSettingsScreen.ChoiceRow<>(label, desc, help, cost, reload, value::get, v -> {
         value.set(v);
         this.clientChanged();
      }, value::getDefault, values, name));
   }

   private void toggle(String label, String desc, BooleanValue value, String help) {
      this.toggle(label, desc, value, help, FrontierSettingsScreen.Cost.UNSPECIFIED);
   }

   private void toggle(String label, String desc, BooleanValue value, String help, FrontierSettingsScreen.Cost cost) {
      this.add(new FrontierSettingsScreen.ToggleRow(label, desc, help, cost, FrontierSettingsScreen.Reload.NONE, value::get, v -> {
         value.set(v);
         this.clientChanged();
      }, value::getDefault));
   }

   private void slider(String label, String desc, IntValue value, int min, int max, int step, IntFunction<String> fmt, String help) {
      this.slider(label, desc, value, min, max, step, fmt, help, FrontierSettingsScreen.Cost.UNSPECIFIED, FrontierSettingsScreen.Reload.NONE);
   }

   private void slider(
      String label,
      String desc,
      IntValue value,
      int min,
      int max,
      int step,
      IntFunction<String> fmt,
      String help,
      FrontierSettingsScreen.Cost cost,
      FrontierSettingsScreen.Reload reload
   ) {
      this.add(new FrontierSettingsScreen.SliderRow(label, desc, help, cost, reload, value::get, v -> {
         value.set(v);
         this.clientChanged();
      }, value::getDefault, min, max, step, fmt));
   }

   private void soundSlider(FrontierSoundCategory c) {
      IntValue value = SoundMixConfig.value(c);
      if (value != null) {
         FrontierSettingsScreen.SliderRow row = new FrontierSettingsScreen.SliderRow(c.label, c.desc, c.help, FrontierSettingsScreen.Cost.NONE, FrontierSettingsScreen.Reload.NONE, value::get, v -> {
            value.set(v);
            this.clientChanged();
            FrontierSoundMixer.reload();
            FrontierSoundMixer.refreshPlaying();
         }, value::getDefault, 0, 100, 5, v -> v + "%");
         row.onCommit = () -> FrontierSoundMixer.preview(c);
         this.add(row);
      }
   }

   private void worldToggle(String label, String desc, BooleanValue value, boolean restart, String help) {
      this.add(
         new FrontierSettingsScreen.ToggleRow(
            label, desc, help, FrontierSettingsScreen.Cost.NONE, restart ? FrontierSettingsScreen.Reload.RESTART : FrontierSettingsScreen.Reload.NONE, value::getRaw, v -> {
               value.set(v);
               this.worldChanged();
            }, value::getDefault
         )
      );
   }

   private void worldSlider(String label, String desc, IntValue value, int min, int max, int step, IntFunction<String> fmt, String help) {
      this.worldSlider(label, desc, value, min, max, step, fmt, help, FrontierSettingsScreen.Cost.UNSPECIFIED);
   }

   private void worldSlider(String label, String desc, IntValue value, int min, int max, int step, IntFunction<String> fmt, String help, FrontierSettingsScreen.Cost cost) {
      FrontierSettingsScreen.Reload reload = value == HuntConfig.FOREST_DENSITY ? FrontierSettingsScreen.Reload.RESTART : FrontierSettingsScreen.Reload.NONE;
      this.add(new FrontierSettingsScreen.SliderRow(label, desc, help, cost, reload, value::getRaw, v -> {
         value.set(v);
         this.worldChanged();
      }, value::getDefault, min, max, step, fmt));
   }

   /** World (server config) options: editable by the host of a singleplayer/LAN world, read-only otherwise. */
   static boolean serverEditable() {
      try {
         return Minecraft.getInstance().hasSingleplayerServer() && HuntConfig.SERVER.isLoaded();
      } catch (RuntimeException var1) {
         return false;
      }
   }

   static <T> T serverGet(ConfigValue<T> value) {
      try {
         return value.get();
      } catch (RuntimeException var2) {
         return value.getDefault();
      }
   }

   private void serverToggle(String label, String desc, BooleanValue value, String help) {
      if (value != null) {
         FrontierSettingsScreen.ToggleRow row = new FrontierSettingsScreen.ToggleRow(label, desc, help, FrontierSettingsScreen.Cost.NONE, FrontierSettingsScreen.Reload.NONE, () -> serverGet(value), v -> {
            if (serverEditable()) {
               value.set(v);
               this.serverChanged();
            }
         }, value::getDefault);
         row.markServer();
         this.add(row);
      }
   }

   private void serverSlider(String label, String desc, IntValue value, int min, int max, int step, IntFunction<String> fmt, String help) {
      if (value != null) {
         FrontierSettingsScreen.SliderRow row = new FrontierSettingsScreen.SliderRow(label, desc, help, FrontierSettingsScreen.Cost.NONE, FrontierSettingsScreen.Reload.NONE, () -> serverGet(value), v -> {
            if (serverEditable()) {
               value.set(v);
               this.serverChanged();
            }
         }, value::getDefault, min, max, step, fmt);
         row.markServer();
         this.add(row);
      }
   }

   private <E extends Enum<E>> void serverChoice(String label, String desc, EnumValue<E> value, E[] values, Function<E, String> name, String help) {
      if (value != null) {
         FrontierSettingsScreen.ChoiceRow<E> row = new FrontierSettingsScreen.ChoiceRow<>(
            label, desc, help, FrontierSettingsScreen.Cost.NONE, FrontierSettingsScreen.Reload.NONE, () -> serverGet(value), v -> {
               if (serverEditable()) {
                  value.set(v);
                  this.serverChanged();
               }
            }, value::getDefault, values, name
         );
         row.markServer();
         this.add(row);
      }
   }

   static String title(Enum<?> e) {
      String n = e.name().toLowerCase(Locale.ROOT);
      return Character.toUpperCase(n.charAt(0)) + n.substring(1);
   }

   /** [presets] The two graphics presets, in card order. */
   static final HuntConfig.GraphicsPreset[] PRESETS = {HuntConfig.GraphicsPreset.CLASSIC, HuntConfig.GraphicsPreset.ULTRA};

   /** [presets] Preset names: CLASSIC is shown as "Vanilla" (the stored value stays CLASSIC so old config files load). */
   static String title(HuntConfig.GraphicsPreset p) {
      return switch (p) {
         case CLASSIC -> "Vanilla";
         case BALANCED, HIGH, ULTRA -> "Ultra"; // retired values are Ultra
         case CUSTOM -> "Custom";
      };
   }

   /**
    * [presets] Names of quality levels: the middle level (stored as BALANCED) reads "Standard", so no setting looks like
    * the retired Balanced preset.
    */
   static String level(Enum<?> e) {
      return "BALANCED".equals(e.name()) ? "Standard" : title(e);
   }

   /**
    * [presets] The performance settings Ultra tolerates (FrontierGraphics.matches): lowering them keeps the Ultra look
    * and the Ultra preset, so a weaker PC can run Ultra. Each is also on its own page.
    */
   private void ultraTuning() {
      this.section("Tune Ultra for your PC");
      this.add(
         new FrontierSettingsScreen.Info(
            "Frames dropping? Lower these and Ultra stays selected - they trade detail distance, density and particles for speed, never the look. Click the Ultra card again to max everything."
         )
      );
      this.choice(
         "Distant trees",
         "Where realistic trees become cutouts",
         com.formaworks.frontierhunts.perf.PerfConfig.TREE_DETAIL,
         com.formaworks.frontierhunts.perf.PerfConfig.TreeDetail.values(),
         FrontierSettingsScreen::level,
         "Full detail / cutouts: Performance 24 / 64 blocks, Standard 32 / 96, Ultra 48 / 160, Maximum 64 / never. The biggest frame-rate setting in the realistic world.",
         FrontierSettingsScreen.Cost.VARIES,
         FrontierSettingsScreen.Reload.NONE
      );
      this.choice(
         "Animal detail",
         "Full-detail distance and fur size",
         HuntConfig.ANIMAL_DETAIL,
         HuntConfig.Detail.values(),
         FrontierSettingsScreen::title,
         "How far animals keep their full sculpted mesh and how big their fur is (Ultra: 2048px). Lower it when a herd is on screen.",
         FrontierSettingsScreen.Cost.HIGH,
         FrontierSettingsScreen.Reload.NONE
      );
      this.choice(
         "Grass density",
         "Blades per block",
         HuntConfig.GRASS_THICKNESS,
         HuntConfig.GrassThickness.values(),
         FrontierSettingsScreen::title,
         "Light halves the meadow blades, Thick doubles them. Height and look stay the same.",
         FrontierSettingsScreen.Cost.HIGH,
         FrontierSettingsScreen.Reload.CHUNKS
      );
      this.choice(
         "Waterfall detail",
         "Sheets, spray, mist and pool splashes",
         HuntConfig.WATERFALL_DETAIL,
         HuntConfig.EffectLevel.values(),
         FrontierSettingsScreen::title,
         "Off hides the extra waterfall visuals (the water stays).",
         FrontierSettingsScreen.Cost.VARIES,
         FrontierSettingsScreen.Reload.NONE
      );
      this.choice(
         "Effects quality",
         "Blood, flares, optics, field particles",
         HuntConfig.QUALITY,
         HuntConfig.Quality.values(),
         FrontierSettingsScreen::level,
         "Particle counts and detail of blood, flares, scopes and the anatomy view. Hit detection and rewards are identical.",
         FrontierSettingsScreen.Cost.VARIES,
         FrontierSettingsScreen.Reload.NONE
      );
   }

   // ------------------------------------------------------------------------------------------------ layout / input

   private void layoutContent() {
      SettingsLayout l = this.L;
      int n = this.items.size();
      int[] heights = new int[n];
      for (int i = 0; i < n; i++) {
         heights[i] = this.items.get(i).prefHeight(l);
      }
      int[] ys = new int[n];
      int total = l.stack(heights, this.scroll, ys);
      this.maxScroll = l.maxScroll(total);
      if (this.scroll > this.maxScroll || this.scroll < 0) {
         this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll);
         total = l.stack(heights, this.scroll, ys);
      }

      int top = l.contentTop();
      int bottom = l.contentBottom();
      for (int i = 0; i < n; i++) {
         FrontierSettingsScreen.Item item = this.items.get(i);
         item.place(l.contentX, ys[i], l.contentW, heights[i]);
         item.visible = item.getBottom() > top && item.getY() < bottom;
      }
   }

   /** Moves the content by dy without re-measuring (scrolling: no text is re-fitted). */
   private void scrollTo(int target) {
      int s = Mth.clamp(target, 0, this.maxScroll);
      if (s != this.scroll) {
         int dy = this.scroll - s;
         this.scroll = s;
         int top = this.L.contentTop();
         int bottom = this.L.contentBottom();
         for (FrontierSettingsScreen.Item item : this.items) {
            item.shift(dy);
            item.visible = item.getBottom() > top && item.getY() < bottom;
         }
      }
   }

   boolean inContent(double mx, double my) {
      SettingsLayout l = this.L;
      return mx >= (double)l.contentX - 4 && mx < (double)(l.contentX + l.contentW + 4) && my >= (double)l.contentTop() && my < (double)l.contentBottom();
   }

   private void open(FrontierSettingsScreen.Page p) {
      if (p != this.page) {
         this.page = p;
         lastPage = p;
         this.scroll = 0;
         this.rebuildWidgets();
      }
   }

   private void resetPage() {
      for (FrontierSettingsScreen.Row row : this.rows) {
         if (row.active) {
            row.reset();
         }
      }
   }

   @Override
   public void tick() {
      FrontierSoundMixer.tickPreview();
   }

   @Override
   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   @Override
   public void removed() {
      FrontierSoundMixer.stopPreview();
      this.session.commit();
      super.removed();
   }

   @Override
   public void setFocused(GuiEventListener listener) {
      super.setFocused(listener);
      if (listener instanceof AbstractWidget w && (listener instanceof FrontierSettingsScreen.Item || listener instanceof FrontierSettingsScreen.PresetCard)) {
         int top = this.L.contentTop() + 2;
         int bottom = this.L.contentBottom() - 2;
         if (w.getY() < top) {
            this.scrollTo(this.scroll - (top - w.getY()));
         } else if (w.getBottom() > bottom) {
            this.scrollTo(this.scroll + Math.min(w.getBottom() - bottom, w.getY() - top));
         }
      }
   }

   @Override
   public boolean mouseReleased(double mx, double my, int button) {
      for (FrontierSettingsScreen.Row row : this.rows) {
         if (row instanceof FrontierSettingsScreen.SliderRow slider) {
            slider.onRelease(mx, my);
         }
      }

      return super.mouseReleased(mx, my, button);
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      if (this.inContent(mx, my)) {
         this.scrollTo(this.scroll - (int)(sy * 24.0));
         return true;
      } else {
         return super.mouseScrolled(mx, my, sx, sy);
      }
   }

   @Override
   public boolean keyPressed(int key, int scan, int mods) {
      if (key == 266) { // page up
         this.scrollTo(this.scroll - Math.max(20, this.L.contentH - 24));
         return true;
      } else if (key == 267) { // page down
         this.scrollTo(this.scroll + Math.max(20, this.L.contentH - 24));
         return true;
      } else {
         return super.keyPressed(key, scan, mods);
      }
   }

   // ------------------------------------------------------------------------------------------------ render

   @Override
   public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
      super.renderBackground(g, mx, my, pt);
      SettingsLayout l = this.L;
      int px = l.px;
      int py = l.py;
      g.fillGradient(0, 0, this.width, this.height, 1426063360, -2013265920);
      FrontierUi.shadow(g, (float)px, (float)py, (float)l.pw, (float)l.ph, 10.0F, 8.0F);
      FrontierUi.outline(g, (float)px, (float)py, (float)l.pw, (float)l.ph, 10.0F, -13748173, PANEL);
      if (!l.compact) {
         FrontierUi.rect(g, (float)(px + 4), (float)(py + 4), (float)(l.sideW - 6), (float)(l.ph - 8), 8.0F, SIDEBAR);
         if (l.compactBrand) {
            FrontierUi.rect(g, (float)(px + 12), (float)(py + 9), 2.5F, 12.0F, 1.25F, ACCENT);
            this.brandA.draw(g, (float)(px + 19), (float)(py + 10), TEXT);
         } else {
            FrontierUi.rect(g, (float)(px + 12), (float)(py + 13), 2.5F, 21.0F, 1.25F, ACCENT);
            this.brandA.draw(g, (float)(px + 19), (float)(py + 12), TEXT);
            this.brandB.draw(g, (float)(px + 19), (float)(py + 25), ACCENT);
         }
         this.titleTxt.draw(g, (float)l.contentX, (float)l.titleY, TEXT);
      }

      if (l.subtitleY >= 0) {
         this.subtitleTxt.draw(g, (float)l.contentX, (float)l.subtitleY, TEXT_DIM);
      }

      if (l.showPill) {
         boolean shaders = FrontierGraphics.shaderPackActive();
         FrontierSettingsScreen.Txt pill = shaders ? this.pillOn : this.pillOff;
         int pillW = pill.w + 20;
         int pillX = l.pillRight - pillW;
         int pyy = l.compact ? l.selY + 1 : l.pillY;
         FrontierUi.rect(g, (float)pillX, (float)pyy, (float)pillW, 14.0F, 7.0F, shaders ? 864471434 : 536870911);
         FrontierUi.circle(g, (float)(pillX + 8), (float)(pyy + 7), 2.5F, shaders ? GOOD : TEXT_MUTED);
         pill.draw(g, (float)(pillX + 14), (float)pyy + 3.5F, shaders ? GOOD : TEXT_DIM);
      }

      g.fill(l.contentX, l.headerLineY, l.contentX + l.contentW, l.headerLineY + 1, PANEL_EDGE);
      g.fill(l.contentX, l.footLineY, l.contentX + l.contentW, l.footLineY + 1, PANEL_EDGE);
      this.renderFooter(g);
   }

   private void refreshFooter() {
      if (this.footVersion != this.session.version) {
         this.footVersion = this.session.version;
         HuntConfig.GraphicsPreset active = safePreset();
         this.footCustom = active == HuntConfig.GraphicsPreset.CUSTOM;
         this.footPreset.set(title(active), STRONG, 58);
         FrontierGraphics.Bundle b = safeBundle();
         this.footCost = b == null ? 0 : FrontierGraphics.cost(b);
         boolean chunks = this.session.chunkReload();
         boolean textures = this.session.textureReload();
         this.footPending = chunks || textures;
         String notice = textures && chunks ? "Textures + chunks reload" : (textures ? "Textures reload" : "Chunks rebuild");
         this.footNotice.set(notice, BODY, Math.max(10, this.L.footLeftW - 66));
      }
   }

   private void renderFooter(GuiGraphics g) {
      SettingsLayout l = this.L;
      if (l.footLeftW < 56) {
         return;
      }
      this.refreshFooter();
      int fy = l.btnY + (l.btnH - 18) / 2;
      int x = l.contentX;
      this.capPreset.draw(g, (float)x, (float)(fy + 1), TEXT_MUTED);
      this.footPreset.draw(g, (float)x, (float)(fy + 10), this.footCustom ? TEXT : ACCENT);
      int rest = l.footLeftW - 66;
      int mx = x + 66;
      if (this.footPending && rest >= 40) {
         int noticeW = Math.min(this.footNotice.w, rest);
         int meterRoom = rest - noticeW - 12;
         if (meterRoom >= 48) {
            int meterW = Math.min(110, meterRoom);
            this.capLoad.draw(g, (float)mx, (float)(fy + 1), TEXT_MUTED);
            meter(g, (float)mx, (float)(fy + 13), (float)meterW, 4.0F, (float)this.footCost / 100.0F);
            mx += meterW + 12;
         }
         FrontierUi.circle(g, (float)(mx + 2), (float)(fy + 4), 2.0F, ACCENT);
         this.capClose.draw(g, (float)(mx + 7), (float)(fy + 1), TEXT_MUTED);
         this.footNotice.draw(g, (float)mx, (float)(fy + 10), ACCENT);
      } else if (rest >= 48) {
         int meterW = Math.min(110, rest);
         this.capLoad.draw(g, (float)mx, (float)(fy + 1), TEXT_MUTED);
         meter(g, (float)mx, (float)(fy + 13), (float)meterW, 4.0F, (float)this.footCost / 100.0F);
      }
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float pt) {
      super.render(g, mx, my, pt);
      SettingsLayout l = this.L;
      int top = l.contentTop();
      int bottom = l.contentBottom();
      g.enableScissor(l.contentX - 4, top, l.contentX + l.contentW + 4, bottom);
      for (FrontierSettingsScreen.Item item : this.items) {
         if (item.visible) {
            item.render(g, mx, my, pt);
         }
      }
      g.disableScissor();
      if (this.maxScroll > 0) {
         int trackTop = top + 4;
         int trackH = l.contentH - 8;
         int barH = Math.max(18, trackH * trackH / (trackH + this.maxScroll));
         float barY = (float)trackTop + (float)(trackH - barH) * ((float)this.scroll / (float)this.maxScroll);
         float sx = (float)l.scrollbarX;
         FrontierUi.rect(g, sx, (float)trackTop, 2.5F, (float)trackH, 1.25F, 419430399);
         FrontierUi.rect(g, sx, barY, 2.5F, (float)barH, 1.25F, -1998347197);
      }
   }

   // ------------------------------------------------------------------------------------------------ preset text

   static String tagline(HuntConfig.GraphicsPreset p) {
      return switch (p) {
         case CLASSIC -> "plain Minecraft look, lightest on your PC";
         case BALANCED, HIGH, ULTRA -> "realistic world and wildlife, everything maxed"; // [presets] retired values are Ultra
         case CUSTOM -> "your own mix";
      };
   }

   static String shortTag(HuntConfig.GraphicsPreset p) {
      return switch (p) {
         case CLASSIC -> "Plain Minecraft";
         case BALANCED, HIGH, ULTRA -> "Realistic everything"; // [presets]
         case CUSTOM -> "Your own mix";
      };
   }

   static String[] details(HuntConfig.GraphicsPreset p) {
      return switch (p) {
         case CLASSIC -> new String[]{
         "Every animal looks like a Minecraft mob",
         "Plain Minecraft grass, blocks and trees",
         "Light waterfall spray, performance effects",
         "No flushing birds, breath or blowing snow"
      };
         case BALANCED, HIGH, ULTRA -> new String[]{ // [presets]
         "Realistic animals with 2048px fur, full detail far out",
         "Realistic world: round trees, real bark, bushy leaves, real ground and plants",
         "Thick, taller meadow grass, heaviest waterfall spray and effects",
         "Weaker PC? Lower the Tune Ultra settings below - it stays Ultra"
      };
         case CUSTOM -> new String[0];
      };
   }

   static HuntConfig.GraphicsPreset safePreset() {
      try {
         HuntConfig.GraphicsPreset p = (HuntConfig.GraphicsPreset)HuntConfig.GRAPHICS_PRESET.get();
         return p == HuntConfig.GraphicsPreset.BALANCED || p == HuntConfig.GraphicsPreset.HIGH ? HuntConfig.GraphicsPreset.ULTRA : p; // [presets]
      } catch (RuntimeException var1) {
         return HuntConfig.GraphicsPreset.ULTRA; // [presets]
      }
   }

   static FrontierGraphics.Bundle safeBundle() {
      try {
         return FrontierGraphics.current();
      } catch (RuntimeException var1) {
         return null;
      }
   }

   // ------------------------------------------------------------------------------------------------ drawing helpers

   static void meter(GuiGraphics g, float x, float y, float w, float h, float f) {
      FrontierUi.rect(g, x, y, w, h, h / 2.0F, TRACK);
      float fw = w * Mth.clamp(f, 0.0F, 1.0F);
      if (fw > h) {
         FrontierUi.rect(g, x, y, fw, h, h / 2.0F, loadColour(f));
      }
   }

   static int loadColour(float f) {
      float r;
      float gg;
      float b;
      if (f < 0.5F) {
         float t = f / 0.5F;
         r = Mth.lerp(t, 0.49F, 0.89F);
         gg = Mth.lerp(t, 0.78F, 0.64F);
         b = Mth.lerp(t, 0.5F, 0.26F);
      } else {
         float t = (f - 0.5F) / 0.5F;
         r = Mth.lerp(t, 0.89F, 0.88F);
         gg = Mth.lerp(t, 0.64F, 0.38F);
         b = Mth.lerp(t, 0.26F, 0.3F);
      }

      return 0xFF000000 | (int)(r * 255.0F) << 16 | (int)(gg * 255.0F) << 8 | (int)(b * 255.0F);
   }

   static int mix(int a, int b, float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      int aa = a >>> 24;
      int ar = a >> 16 & 0xFF;
      int ag = a >> 8 & 0xFF;
      int ab = a & 0xFF;
      int ba = b >>> 24;
      int br = b >> 16 & 0xFF;
      int bg = b >> 8 & 0xFF;
      int bb = b & 0xFF;
      return (int)Mth.lerp(t, (float)aa, (float)ba) << 24
         | (int)Mth.lerp(t, (float)ar, (float)br) << 16
         | (int)Mth.lerp(t, (float)ag, (float)bg) << 8
         | (int)Mth.lerp(t, (float)ab, (float)bb);
   }

   /** The rounded value selector (chevrons + centred value); value text is pre-fitted to w - 30. */
   static void selector(GuiGraphics g, float x, float cy, float w, FrontierSettingsScreen.Txt value, boolean enabled, boolean hovered, int mx, int valueColour) {
      float h = 17.0F;
      float y = cy - h / 2.0F;
      FrontierUi.rect(g, x, y, w, h, h / 2.0F, -14340823);
      if (enabled) {
         boolean hl = hovered && (float)mx < x + 16.0F && (float)mx >= x;
         boolean hr = hovered && (float)mx >= x + 16.0F;
         chevron(g, x + 8.0F, cy, -1, hl ? ACCENT : TEXT_MUTED);
         chevron(g, x + w - 8.0F, cy, 1, hr ? ACCENT : TEXT_MUTED);
      }

      value.center(g, x + w / 2.0F, y + (h - 8.0F) / 2.0F, valueColour);
   }

   static void chevron(GuiGraphics g, float cx, float cy, int dir, int colour) {
      g.pose().pushPose();
      g.pose().translate(cx, cy, 0.0F);
      if (dir < 0) {
         g.pose().mulPose(Axis.ZP.rotationDegrees(180.0F));
      }

      g.pose().pushPose();
      g.pose().mulPose(Axis.ZP.rotationDegrees(45.0F));
      FrontierUi.rect(g, -3.2F, -0.6F, 4.0F, 1.3F, 0.65F, colour);
      g.pose().popPose();
      g.pose().pushPose();
      g.pose().mulPose(Axis.ZP.rotationDegrees(-45.0F));
      FrontierUi.rect(g, -3.2F, -0.7F, 4.0F, 1.3F, 0.65F, colour);
      g.pose().popPose();
      g.pose().popPose();
   }

   // ================================================================================================ types

   /** Performance hint shown in an option's tooltip. */
   static enum Cost {
      UNSPECIFIED(null),
      NONE("Performance: no cost"),
      LOW("Performance: light"),
      MEDIUM("Performance: moderate"),
      HIGH("Performance: heavy - costs frames"),
      VARIES("Performance: rises with each step");

      final String line;

      private Cost(String line) {
         this.line = line;
      }
   }

   /** What changing an option reloads. Chunk and texture reloads are batched until the screen closes. */
   static enum Reload {
      NONE(null),
      CHUNKS("Rebuilds nearby chunks once, when you close this screen."),
      TEXTURES("Reloads textures (a few seconds) when you close this screen."),
      RESTART("Takes effect after restarting the world.");

      final String line;

      private Reload(String line) {
         this.line = line;
      }
   }

   static enum Page {
      PRESETS("Preset", "Graphics preset", "One click sets every look option"),
      WORLD("Trees & world", "Trees & world", "World look, meadow grass and new terrain"),
      WILDLIFE("Wildlife", "Wildlife", "How deer, elk and moose look; small life"),
      ATMOSPHERE("Atmosphere", "Atmosphere & weather", "Breath, blowing snow and storms"),
      SEASONS("Seasons", "Seasons", "Fall colour, winter snow, falling leaves"),
      EFFECTS("Effects", "Effects", "Particles, waterfalls and ATV grime"),
      SOUND("Sound", "Sound", "Volume of Frontier's own sounds"),
      INTERFACE("Interface", "Interface & HUD", "HUD, cameras, kill cam and comfort"),
      AIDS("Gameplay aids", "Gameplay aids", "How forgiving the hunt is"),
      PERFORMANCE("Performance", "Performance", "Level of detail and diagnostics");

      /** Sidebar label (short). */
      final String tab;
      final String title;
      final String subtitle;

      private Page(String tab, String title, String subtitle) {
         this.tab = tab;
         this.title = title;
         this.subtitle = subtitle;
      }
   }

   /**
    * Changes made while the screen is open. Options take effect at once in memory; the config files are written, chunks
    * rebuilt and packs switched once, when the screen closes.
    */
   static final class Session {
      boolean client;
      boolean world;
      boolean server;
      HuntConfig.GraphicsPreset packs;
      int version;
      private HuntConfig.GrassStyle grassStyle;
      private int grassHeight;
      private int grassWidth;
      private HuntConfig.GrassThickness grassThickness;
      private HuntConfig.WorldLook look;

      Session() {
         this.snapshot();

         // the old 0-200% "Ambient volume" moves into the Ambient nature slider (0-100%)
         try {
            int old = HuntConfig.AMBIENT_VOLUME.get();
            if (old != 100) {
               IntValue amb = SoundMixConfig.value(FrontierSoundCategory.AMBIENT);
               if (amb != null) {
                  amb.set(Mth.clamp(Math.round((float)amb.get() * (float)old / 100.0F), 0, 100));
               }
               HuntConfig.AMBIENT_VOLUME.set(100);
               FrontierSoundMixer.reload();
               this.client = true;
            }
         } catch (RuntimeException var2) {
         }
      }

      /** What is on screen / in the world now; reload notices compare against it. */
      private void snapshot() {
         try {
            this.grassStyle = HuntConfig.GRASS_STYLE.get();
            this.grassHeight = HuntConfig.GRASS_HEIGHT_PERCENT.get();
            this.grassWidth = HuntConfig.GRASS_WIDTH_PERCENT.get();
            this.grassThickness = HuntConfig.GRASS_THICKNESS.get();
            this.look = HuntConfig.WORLD_LOOK.get();
         } catch (RuntimeException var3) {
         }
      }

      boolean chunkReload() {
         try {
            return this.grassStyle != HuntConfig.GRASS_STYLE.get()
               || this.grassHeight != HuntConfig.GRASS_HEIGHT_PERCENT.get()
               || this.grassWidth != HuntConfig.GRASS_WIDTH_PERCENT.get()
               || this.grassThickness != HuntConfig.GRASS_THICKNESS.get();
         } catch (RuntimeException var2) {
            return false;
         }
      }

      boolean textureReload() {
         try {
            return this.packs != null || this.look != HuntConfig.WORLD_LOOK.get();
         } catch (RuntimeException var2) {
            return false;
         }
      }

      /** Writes the config files and runs the batched chunk / texture reloads (once; safe to call again). */
      void commit() {
         boolean textures = this.textureReload();
         if (this.client) {
            try {
               HuntConfig.CLIENT.save();
            } catch (RuntimeException var5) {
            }
         }
         if (this.world) {
            try {
               HuntConfig.WORLDGEN.save();
            } catch (RuntimeException var4) {
            }
         }
         if (this.server && FrontierSettingsScreen.serverEditable()) {
            try {
               HuntConfig.SERVER.save();
            } catch (RuntimeException var3) {
            }
         }
         AlpineGrassModels.apply(); // rebuilds chunks only when the grass actually changed
         com.formaworks.frontierhunts.season.client.SnowLook.apply(); // [1.1.2] same for the snow style
         if (this.packs != null) {
            PresetPacks.apply(this.packs); // switches resource packs (incl. the realistic world pack) in one reload
         }
         if (textures) {
            RealisticWorld.requestSync();
         }
         this.client = false;
         this.world = false;
         this.server = false;
         this.packs = null;
         this.version++;
         this.snapshot();
      }
   }

   /** A string fitted and measured once (re-fitted only when text, font or width change). */
   static final class Txt {
      private String src;
      private FrontierUi.Size size;
      private int maxW = Integer.MIN_VALUE;
      FormattedCharSequence seq = FormattedCharSequence.EMPTY;
      int w;

      FrontierSettingsScreen.Txt set(String s, FrontierUi.Size size, int maxW) {
         if (s == null) {
            s = "";
         }
         if (maxW == this.maxW && size == this.size && s.equals(this.src)) {
            return this;
         }
         this.src = s;
         this.size = size;
         this.maxW = maxW;
         String shown = maxW == Integer.MAX_VALUE ? s : FrontierUi.fit(s, Math.max(0, maxW), size);
         MutableComponent c = FrontierUi.c(shown, size);
         this.seq = c.getVisualOrderText();
         this.w = FrontierUi.font().width(this.seq);
         return this;
      }

      float draw(GuiGraphics g, float x, float y, int colour) {
         g.drawString(FrontierUi.font(), this.seq, x, y, colour, false);
         return x + (float)this.w;
      }

      void right(GuiGraphics g, float x, float y, int colour) {
         this.draw(g, x - (float)this.w, y, colour);
      }

      void center(GuiGraphics g, float x, float y, int colour) {
         this.draw(g, x - (float)this.w / 2.0F, y, colour);
      }
   }

   // ------------------------------------------------------------------------------------------------ content items

   /** Anything stacked in the content column. Not interactive unless {@link #interactive()}. */
   abstract class Item extends AbstractWidget {
      Item(int h) {
         super(0, 0, 10, h, Component.empty());
         this.setTooltipDelay(FrontierSettingsScreen.TOOLTIP_DELAY);
      }

      abstract int prefHeight(SettingsLayout l);

      boolean interactive() {
         return false;
      }

      void place(int x, int y, int w, int h) {
         this.setRectangle(w, h, x, y);
         this.placed();
      }

      void placed() {
      }

      void shift(int dy) {
         this.setY(this.getY() + dy);
         this.placed();
      }

      @Override
      public boolean isMouseOver(double mx, double my) {
         return super.isMouseOver(mx, my) && FrontierSettingsScreen.this.inContent(mx, my);
      }

      @Override
      protected boolean clicked(double mx, double my) {
         return super.clicked(mx, my) && FrontierSettingsScreen.this.inContent(mx, my);
      }

      @Override
      protected void updateWidgetNarration(NarrationElementOutput out) {
      }
   }

   final class Section extends FrontierSettingsScreen.Item {
      private final String label;
      private final FrontierSettingsScreen.Txt txt = new FrontierSettingsScreen.Txt();

      Section(String label) {
         super(16);
         this.label = label.toUpperCase(Locale.ROOT);
         this.active = false;
      }

      @Override
      int prefHeight(SettingsLayout l) {
         return 14;
      }

      @Override
      void placed() {
         this.txt.set(this.label, SMALL, this.width - 12);
      }

      @Override
      protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
         float end = this.txt.draw(g, (float)(this.getX() + 2), (float)(this.getY() + 4), ACCENT);
         int lx = (int)end + 6;
         if (lx < this.getX() + this.width) {
            g.fill(lx, this.getY() + 8, this.getX() + this.width, this.getY() + 9, PANEL_EDGE);
         }
      }
   }

   final class Info extends FrontierSettingsScreen.Item {
      private final String text;
      private List<FormattedCharSequence> lines = List.of();
      private int wrapW = -1;

      Info(String text) {
         super(20);
         this.text = text;
         this.active = false;
      }

      private void wrapTo(int w) {
         if (w != this.wrapW) {
            this.wrapW = w;
            this.lines = FrontierUi.font().split(FrontierUi.c(this.text, BODY), Math.max(20, w));
         }
      }

      @Override
      int prefHeight(SettingsLayout l) {
         this.wrapTo(l.contentW - 4);
         return this.lines.size() * FrontierUi.lineHeight(BODY) + 4;
      }

      @Override
      protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
         int y = this.getY() + 2;
         for (FormattedCharSequence line : this.lines) {
            g.drawString(FrontierUi.font(), line, this.getX() + 2, y, TEXT_DIM, false);
            y += FrontierUi.lineHeight(BODY);
         }
      }
   }

   final class Status extends FrontierSettingsScreen.Item {
      private final FrontierSettingsScreen.Txt txt = new FrontierSettingsScreen.Txt();
      private final String label;
      private final Supplier<Boolean> state;

      Status(String label, Supplier<Boolean> state) {
         super(18);
         this.label = label;
         this.state = state;
         this.active = false;
      }

      @Override
      int prefHeight(SettingsLayout l) {
         return 18;
      }

      @Override
      void placed() {
         this.txt.set(this.label, BODY, this.width - 60);
      }

      @Override
      protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
         int x = this.getX();
         int y = this.getY();
         boolean ok = Boolean.TRUE.equals(this.state.get());
         FrontierUi.rect(g, (float)x, (float)y, (float)this.width, 18.0F, 6.0F, 318767103);
         this.txt.draw(g, (float)(x + 9), (float)(y + 5), TEXT);
         FrontierUi.circle(g, (float)(x + this.width - 12), (float)(y + 9), 3.0F, ok ? GOOD : TEXT_MUTED);
         (ok ? FrontierSettingsScreen.this.txtYes : FrontierSettingsScreen.this.txtNo).right(g, (float)(x + this.width - 20), (float)(y + 5), ok ? GOOD : TEXT_MUTED);
      }
   }

   /** The preset cards, Vanilla and Ultra (separate widgets so each is clickable/focusable). */
   final class CardGrid extends FrontierSettingsScreen.Item {
      CardGrid() {
         super(100);
         this.active = false;
         for (HuntConfig.GraphicsPreset preset : PRESETS) { // [presets]
            FrontierSettingsScreen.PresetCard card = FrontierSettingsScreen.this.new PresetCard(preset);
            FrontierSettingsScreen.this.cards.add(card);
            FrontierSettingsScreen.this.addWidget(card);
         }
      }

      @Override
      int prefHeight(SettingsLayout l) {
         return l.cardGridHeight(FrontierSettingsScreen.this.cards.size());
      }

      @Override
      void placed() {
         SettingsLayout l = FrontierSettingsScreen.this.L;
         int cols = l.cardColumns();
         int cw = l.cardWidth();
         int ch = l.cardHeight();
         int top = l.contentTop();
         int bottom = l.contentBottom();
         List<FrontierSettingsScreen.PresetCard> cs = FrontierSettingsScreen.this.cards;
         for (int i = 0; i < cs.size(); i++) {
            FrontierSettingsScreen.PresetCard card = cs.get(i);
            card.setRectangle(cw, ch, this.getX() + i % cols * (cw + 6), this.getY() + i / cols * (ch + 6));
            card.layoutText();
            card.visible = card.getBottom() > top && card.getY() < bottom;
         }
      }

      @Override
      protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
         for (FrontierSettingsScreen.PresetCard card : FrontierSettingsScreen.this.cards) {
            if (card.visible) {
               card.render(g, mx, my, pt);
            }
         }
      }
   }

   /** What the hovered (or active) preset does. */
   final class PresetDetail extends FrontierSettingsScreen.Item {
      private final HuntConfig.GraphicsPreset[] shown = PRESETS; // [presets]
      private final FrontierSettingsScreen.Txt[] titles = new FrontierSettingsScreen.Txt[PRESETS.length];
      private final FrontierSettingsScreen.Txt[] tags = new FrontierSettingsScreen.Txt[PRESETS.length];
      private final FrontierSettingsScreen.Txt[][] lines = new FrontierSettingsScreen.Txt[PRESETS.length][4];
      private final FrontierSettingsScreen.Txt customTitle = new FrontierSettingsScreen.Txt();
      private List<FormattedCharSequence> customLines = List.of();
      private int fitW = -1;

      PresetDetail() {
         super(66);
         this.active = false;
         for (int i = 0; i < this.shown.length; i++) {
            this.titles[i] = new FrontierSettingsScreen.Txt();
            this.tags[i] = new FrontierSettingsScreen.Txt();
            for (int j = 0; j < 4; j++) {
               this.lines[i][j] = new FrontierSettingsScreen.Txt();
            }
         }
      }

      @Override
      int prefHeight(SettingsLayout l) {
         return 64;
      }

      @Override
      void placed() {
         int w = this.width;
         if (w == this.fitW) {
            return;
         }
         this.fitW = w;
         for (int i = 0; i < this.shown.length; i++) {
            HuntConfig.GraphicsPreset p = this.shown[i];
            this.titles[i].set(title(p), STRONG, w);
            this.tags[i].set("  " + tagline(p), BODY, Math.max(0, w - this.titles[i].w));
            String[] d = details(p);
            for (int j = 0; j < 4; j++) {
               this.lines[i][j].set(j < d.length ? d[j] : "", BODY, w - 10);
            }
         }
         this.customTitle.set("Custom settings", STRONG, w);
         this.customLines = FrontierUi.font()
            .split(
               FrontierUi.c("You have changed individual settings. Pick Vanilla or Ultra above to reset everything to that look, or keep tuning on the other pages.", BODY),
               Math.max(20, w)
            );
      }

      @Override
      protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
         int x = this.getX();
         int y = this.getY() + 4;
         HuntConfig.GraphicsPreset active = safePreset();
         HuntConfig.GraphicsPreset show = active == HuntConfig.GraphicsPreset.CUSTOM ? null : active;
         for (FrontierSettingsScreen.PresetCard card : FrontierSettingsScreen.this.cards) {
            if (card.isHovered()) {
               show = card.preset;
            }
         }
         int idx = -1;
         for (int i = 0; i < this.shown.length; i++) {
            if (this.shown[i] == show) {
               idx = i;
            }
         }
         if (idx < 0) {
            this.customTitle.draw(g, (float)x, (float)y, TEXT);
            int ly = y + 14;
            for (FormattedCharSequence line : this.customLines) {
               g.drawString(FrontierUi.font(), line, x, ly, TEXT_DIM, false);
               ly += FrontierUi.lineHeight(BODY);
            }
         } else {
            float ex = this.titles[idx].draw(g, (float)x, (float)y, ACCENT);
            this.tags[idx].draw(g, ex, (float)y, TEXT_DIM);
            int ly = y + 15;
            for (FrontierSettingsScreen.Txt line : this.lines[idx]) {
               if (line.w > 0) {
                  FrontierUi.circle(g, (float)(x + 3), (float)ly + 4.5F, 1.5F, ACCENT);
                  line.draw(g, (float)(x + 10), (float)ly, TEXT_DIM);
               }
               ly += 11;
            }
         }
      }
   }

   /** One option: label, one-line description, control; tooltip with help, performance cost and reload note. */
   abstract class Row extends FrontierSettingsScreen.Item {
      final String label;
      final String desc;
      final String help;
      final FrontierSettingsScreen.Cost cost;
      final FrontierSettingsScreen.Reload reload;
      boolean server;
      protected float glow;
      final FrontierSettingsScreen.Txt labelTxt = new FrontierSettingsScreen.Txt();
      final FrontierSettingsScreen.Txt descTxt = new FrontierSettingsScreen.Txt();
      int ctlX;
      int ctlW;
      int ctlCY;

      Row(String label, String desc, String help, FrontierSettingsScreen.Cost cost, FrontierSettingsScreen.Reload reload) {
         super(32);
         this.label = label;
         this.desc = desc;
         this.help = help;
         this.cost = cost;
         this.reload = reload;
         this.setMessage(Component.literal(label));
         this.buildTooltip(null);
      }

      private void buildTooltip(String extra) {
         MutableComponent t = Component.literal(this.label).withStyle(ChatFormatting.WHITE).append(Component.literal("\n" + this.help).withStyle(ChatFormatting.GRAY));
         if (this.cost.line != null) {
            t.append(Component.literal("\n" + this.cost.line).withStyle(ChatFormatting.DARK_AQUA));
         }
         if (this.reload.line != null) {
            t.append(Component.literal("\n" + this.reload.line).withStyle(ChatFormatting.GOLD));
         }
         if (extra != null) {
            t.append(Component.literal("\n" + extra).withStyle(ChatFormatting.GOLD));
         }
         this.setTooltip(Tooltip.create(t));
      }

      /** A world setting from the server config: editable only by the host of this world. */
      void markServer() {
         this.server = true;
         boolean editable = FrontierSettingsScreen.serverEditable();
         this.active = editable;
         this.buildTooltip(editable ? "Saved with this world (you are the host)." : "Set by the server or world host - open your own world to change it.");
      }

      @Override
      boolean interactive() {
         return true;
      }

      @Override
      int prefHeight(SettingsLayout l) {
         return l.rowH;
      }

      abstract void reset();

      abstract String valueText();

      boolean listControl() {
         return false;
      }

      FrontierSettingsScreen.Txt badge() {
         FrontierSettingsScreen screen = FrontierSettingsScreen.this;
         if (this.server) {
            return this.active ? screen.badgeWorld : screen.badgeServer;
         }
         return switch (this.reload) {
            case NONE -> null;
            case CHUNKS, TEXTURES -> screen.badgeReload;
            case RESTART -> screen.badgeRestart;
         };
      }

      @Override
      void placed() {
         FrontierSettingsScreen.Txt badge = this.badge();
         int badgeW = badge == null ? 0 : badge.w + 14;
         int[] g = FrontierSettingsScreen.this.geom;
         FrontierSettingsScreen.this.L.row(this.getX(), this.getY(), this.width, this.height, this.listControl(), g);
         this.ctlX = g[0];
         this.ctlW = g[1];
         this.ctlCY = g[2];
         int textW = g[3];
         this.labelTxt.set(this.label, STRONG, textW - badgeW);
         this.descTxt.set(this.desc, SMALL, textW);
         this.placedControl();
      }

      void placedControl() {
      }

      @Override
      protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
         int x = this.getX();
         int y = this.getY();
         this.glow = Mth.lerp(0.35F, this.glow, this.isHoveredOrFocused() && this.active ? 1.0F : 0.0F);
         FrontierUi.rect(g, (float)x, (float)y, (float)this.width, (float)this.height, 7.0F, FrontierSettingsScreen.mix(285212671, 486539263, this.glow));
         if (this.glow > 0.05F) {
            FrontierUi.rect(g, (float)(x + 1), (float)(y + 7), 2.5F, (float)(this.height - 14), 1.25F, FrontierSettingsScreen.mix(14918723, ACCENT, this.glow));
         }

         boolean stacked = FrontierSettingsScreen.this.L.stacked;
         float end = this.labelTxt.draw(g, (float)(x + 10), (float)(y + (stacked ? 5 : 6)), this.active ? TEXT : TEXT_DIM);
         FrontierSettingsScreen.Txt badge = this.badge();
         if (badge != null) {
            int bw = badge.w + 8;
            boolean warn = this.reload == FrontierSettingsScreen.Reload.CHUNKS || this.reload == FrontierSettingsScreen.Reload.TEXTURES;
            FrontierUi.rect(g, end + 5.0F, (float)y + (stacked ? 4.5F : 5.5F), (float)bw, 10.0F, 5.0F, this.server ? 0x3324A0C0 : (warn ? 0x40E0A040 : 870556739));
            badge.draw(g, end + 9.0F, (float)y + (stacked ? 6.5F : 7.5F), this.server ? 0xFF8FD3E8 : (warn ? 0xFFF0C060 : ACCENT));
         }

         if (!stacked) {
            this.descTxt.draw(g, (float)(x + 10), (float)(y + 19), TEXT_MUTED);
         }

         this.renderControl(g, mx, my);
      }

      abstract void renderControl(GuiGraphics g, int mx, int my);

      @Override
      protected void updateWidgetNarration(NarrationElementOutput out) {
         out.add(NarratedElementType.TITLE, Component.literal(this.label + ": " + this.valueText()));
      }
   }

   final class ChoiceRow<E extends Enum<E>> extends FrontierSettingsScreen.Row {
      private final Supplier<E> get;
      private final Consumer<E> set;
      private final Supplier<E> def;
      private final E[] values;
      private final Function<E, String> name;
      private final FrontierSettingsScreen.Txt[] valueTxt;

      ChoiceRow(
         String label,
         String desc,
         String help,
         FrontierSettingsScreen.Cost cost,
         FrontierSettingsScreen.Reload reload,
         Supplier<E> get,
         Consumer<E> set,
         Supplier<E> def,
         E[] values,
         Function<E, String> name
      ) {
         super(label, desc, help, cost, reload);
         this.get = get;
         this.set = set;
         this.def = def;
         this.values = values;
         this.name = name;
         this.valueTxt = new FrontierSettingsScreen.Txt[values.length];
         for (int i = 0; i < values.length; i++) {
            this.valueTxt[i] = new FrontierSettingsScreen.Txt();
         }
      }

      @Override
      void placedControl() {
         for (int i = 0; i < this.values.length; i++) {
            this.valueTxt[i].set(this.name.apply(this.values[i]), BODY, this.ctlW - 30);
         }
      }

      protected boolean isValidClickButton(int button) {
         return button == 0 || button == 1;
      }

      public void onClick(double mx, double my, int button) {
         boolean back = button == 1 || mx < (double)(this.ctlX + 16) && mx >= (double)this.ctlX;
         this.step(back ? -1 : 1);
      }

      public boolean keyPressed(int key, int scan, int mods) {
         if (key == 263 || key == 262) {
            if (this.active) {
               this.step(key == 263 ? -1 : 1);
            }
            return true;
         } else if (key == 257 || key == 335 || key == 32) {
            if (this.active) {
               this.step(1);
            }
            return true;
         } else {
            return super.keyPressed(key, scan, mods);
         }
      }

      private int current() {
         E v = this.get.get();
         if (v == null) {
            return 0;
         }
         // [presets] the position in the values shown (a row may offer a subset of the enum, e.g. Animal style)
         for (int i = 0; i < this.values.length; i++) {
            if (this.values[i] == v) {
               return i;
            }
         }
         return Math.min(v.ordinal(), this.values.length - 1);
      }

      private void step(int d) {
         this.set.accept(this.values[Math.floorMod(this.current() + d, this.values.length)]);
      }

      @Override
      void reset() {
         this.set.accept(this.def.get());
      }

      @Override
      String valueText() {
         return this.name.apply(this.values[Math.min(this.current(), this.values.length - 1)]);
      }

      @Override
      void renderControl(GuiGraphics g, int mx, int my) {
         int cur = Math.min(this.current(), this.values.length - 1);
         FrontierSettingsScreen.selector(
            g, (float)this.ctlX, (float)this.ctlCY, (float)this.ctlW, this.valueTxt[cur], this.active, this.isHovered(), mx, this.active ? TEXT : TEXT_DIM
         );
         if (!FrontierSettingsScreen.this.L.stacked) {
            int n = this.values.length;
            float gap = 5.5F;
            float sx = (float)this.ctlX + (float)this.ctlW / 2.0F - (float)(n - 1) * gap / 2.0F;
            float dy = (float)this.ctlCY + 11.0F;
            for (int i = 0; i < n; i++) {
               FrontierUi.circle(g, sx + (float)i * gap, dy, i == cur ? 1.4F : 1.1F, i == cur ? ACCENT : 1090519039);
            }
         }
      }
   }

   final class ListRow extends FrontierSettingsScreen.Row {
      private final Supplier<String> get;
      private final Consumer<String> set;
      private final Supplier<List<String>> options;
      private final Function<String, String> name;
      private final boolean enabled;
      private final FrontierSettingsScreen.Txt valueTxt = new FrontierSettingsScreen.Txt();
      private String shownValue;

      ListRow(String label, String desc, String help, Supplier<String> get, Consumer<String> set, Supplier<List<String>> options, Function<String, String> name, boolean enabled) {
         super(label, desc, help, FrontierSettingsScreen.Cost.UNSPECIFIED, FrontierSettingsScreen.Reload.NONE);
         this.get = get;
         this.set = set;
         this.options = options;
         this.name = name;
         this.enabled = enabled;
         this.active = enabled;
      }

      @Override
      boolean listControl() {
         return true;
      }

      @Override
      void placedControl() {
         this.shownValue = null;
      }

      protected boolean isValidClickButton(int button) {
         return button == 0 || button == 1;
      }

      public void onClick(double mx, double my, int button) {
         this.step(button != 1 && !(mx < (double)(this.ctlX + 16)) ? 1 : -1);
      }

      public boolean keyPressed(int key, int scan, int mods) {
         if (key != 263 && key != 262) {
            return super.keyPressed(key, scan, mods);
         } else {
            this.step(key == 263 ? -1 : 1);
            return true;
         }
      }

      private void step(int d) {
         List<String> opts = this.options.get();
         if (!opts.isEmpty()) {
            int i = opts.indexOf(this.get.get());
            this.set.accept(opts.get(Math.floorMod((i < 0 ? 0 : i) + d, opts.size())));
         }
      }

      @Override
      void reset() {
         this.set.accept("");
      }

      @Override
      String valueText() {
         return this.enabled ? this.name.apply(this.get.get()) : "Iris not installed";
      }

      @Override
      void renderControl(GuiGraphics g, int mx, int my) {
         String v = this.get.get();
         if (v == null) {
            v = "";
         }
         if (!v.equals(this.shownValue)) {
            this.shownValue = v;
            this.valueTxt.set(this.valueText(), BODY, this.ctlW - 30);
         }
         boolean keep = v.isEmpty();
         FrontierSettingsScreen.selector(
            g, (float)this.ctlX, (float)this.ctlCY, (float)this.ctlW, this.valueTxt, this.enabled, this.isHovered(), mx, this.enabled && !keep ? TEXT : TEXT_MUTED
         );
      }
   }

   final class SliderRow extends FrontierSettingsScreen.Row {
      private final IntSupplier get;
      private final IntConsumer set;
      private final IntSupplier def;
      private final int min;
      private final int max;
      private final int step;
      private final IntFunction<String> fmt;
      private boolean dragging;
      private int pending = Integer.MIN_VALUE;
      private final FrontierSettingsScreen.Txt valueTxt = new FrontierSettingsScreen.Txt();
      private int valueShown = Integer.MIN_VALUE;
      /** Run after the value was committed by the user (sound sliders play their preview). */
      Runnable onCommit;

      SliderRow(
         String label,
         String desc,
         String help,
         FrontierSettingsScreen.Cost cost,
         FrontierSettingsScreen.Reload reload,
         IntSupplier get,
         IntConsumer set,
         IntSupplier def,
         int min,
         int max,
         int step,
         IntFunction<String> fmt
      ) {
         super(label, desc, help, cost, reload);
         this.get = get;
         this.set = set;
         this.def = def;
         this.min = min;
         this.max = max;
         this.step = step;
         this.fmt = fmt;
      }

      private int trackX() {
         return SettingsLayout.sliderTrackX(this.ctlX);
      }

      private int trackW() {
         return SettingsLayout.sliderTrackW(this.ctlW);
      }

      private int valueAt(double mx) {
         double t = Mth.clamp((mx - (double)this.trackX()) / (double)this.trackW(), 0.0, 1.0);
         int v = (int)Math.round(((double)this.min + t * (double)(this.max - this.min)) / (double)this.step) * this.step;
         return Mth.clamp(v, this.min, this.max);
      }

      private int shown() {
         return this.pending != Integer.MIN_VALUE ? this.pending : this.get.getAsInt();
      }

      private void commit(int v) {
         if (v != this.get.getAsInt()) {
            this.set.accept(v);
         }
         if (this.onCommit != null) {
            this.onCommit.run();
         }
      }

      public void onClick(double mx, double my) {
         if (mx >= (double)(this.trackX() - 6) && (FrontierSettingsScreen.this.L.stacked || mx >= (double)this.ctlX - 2)) {
            this.dragging = true;
            this.pending = this.valueAt(mx);
         }
      }

      protected void onDrag(double mx, double my, double dx, double dy) {
         if (this.dragging) {
            this.pending = this.valueAt(mx);
         }
      }

      public void onRelease(double mx, double my) {
         if (this.dragging) {
            this.dragging = false;
            int v = this.pending;
            this.pending = Integer.MIN_VALUE;
            if (v != Integer.MIN_VALUE) {
               this.commit(v);
            }
         }
      }

      public boolean keyPressed(int key, int scan, int mods) {
         if (key != 263 && key != 262) {
            return super.keyPressed(key, scan, mods);
         } else {
            if (this.active) {
               this.commit(Mth.clamp(this.get.getAsInt() + (key == 263 ? -this.step : this.step), this.min, this.max));
            }
            return true;
         }
      }

      @Override
      void reset() {
         this.set.accept(this.def.getAsInt());
      }

      @Override
      String valueText() {
         return this.fmt.apply(this.shown());
      }

      @Override
      void renderControl(GuiGraphics g, int mx, int my) {
         int v = this.shown();
         if (v != this.valueShown) {
            this.valueShown = v;
            this.valueTxt.set(this.fmt.apply(v), STRONG, SettingsLayout.SLIDER_VALUE_W);
         }
         float tx = (float)this.trackX();
         float tw = (float)this.trackW();
         float ty = (float)this.ctlCY - 1.5F;
         float t = Mth.clamp((float)(v - this.min) / (float)(this.max - this.min), 0.0F, 1.0F);
         FrontierUi.rect(g, tx, ty, tw, 3.0F, 1.5F, -13616588);
         float fx = tx + tw * t;
         if (fx - tx > 2.0F) {
            FrontierUi.rect(g, tx, ty, fx - tx, 3.0F, 1.5F, this.active ? -3568844 : -10531808);
         }

         float dt = Mth.clamp((float)(this.def.getAsInt() - this.min) / (float)(this.max - this.min), 0.0F, 1.0F);
         FrontierUi.circle(g, tx + tw * dt, ty + 1.5F, 1.0F, 1728053247);
         boolean hot = this.active && (this.dragging || this.isHoveredOrFocused());
         float kr = hot ? 5.0F : 4.25F;
         FrontierUi.circle(g, fx, ty + 1.5F, kr + 0.75F, 855638016);
         FrontierUi.circle(g, fx, ty + 1.5F, kr, !this.active ? -7631989 : (hot ? -2333 : -1186608));
         this.valueTxt.right(g, (float)(this.ctlX + this.ctlW), (float)this.ctlCY - 4.0F, this.active ? TEXT : TEXT_DIM);
      }
   }

   final class ToggleRow extends FrontierSettingsScreen.Row {
      private final Supplier<Boolean> get;
      private final Consumer<Boolean> set;
      private final Supplier<Boolean> def;
      private float knob = -1.0F;

      ToggleRow(
         String label,
         String desc,
         String help,
         FrontierSettingsScreen.Cost cost,
         FrontierSettingsScreen.Reload reload,
         Supplier<Boolean> get,
         Consumer<Boolean> set,
         Supplier<Boolean> def
      ) {
         super(label, desc, help, cost, reload);
         this.get = get;
         this.set = set;
         this.def = def;
      }

      private boolean on() {
         return Boolean.TRUE.equals(this.get.get());
      }

      public void onClick(double mx, double my) {
         this.set.accept(!this.on());
      }

      public boolean keyPressed(int key, int scan, int mods) {
         if (key != 257 && key != 335 && key != 32 && key != 262 && key != 263) {
            return super.keyPressed(key, scan, mods);
         } else {
            if (this.active) {
               this.playDownSound(Minecraft.getInstance().getSoundManager());
               this.set.accept(key == 263 ? false : (key == 262 ? true : !this.on()));
            }
            return true;
         }
      }

      @Override
      void reset() {
         this.set.accept(this.def.get());
      }

      @Override
      String valueText() {
         return this.on() ? "On" : "Off";
      }

      @Override
      void renderControl(GuiGraphics g, int mx, int my) {
         boolean on = this.on();
         if (this.knob < 0.0F) {
            this.knob = on ? 1.0F : 0.0F;
         }

         this.knob = Mth.lerp(0.35F, this.knob, on ? 1.0F : 0.0F);
         float sw = 24.0F;
         float sh = 13.0F;
         float sx = (float)(this.ctlX + this.ctlW) - sw;
         float sy = (float)this.ctlCY - sh / 2.0F;
         FrontierUi.rect(g, sx, sy, sw, sh, sh / 2.0F, FrontierSettingsScreen.mix(-13616588, this.active ? -3568844 : -10531808, this.knob));
         float kr = sh / 2.0F - 1.75F;
         float kx = sx + sh / 2.0F + (sw - sh) * this.knob;
         FrontierUi.circle(g, kx, sy + sh / 2.0F, kr, FrontierSettingsScreen.mix(-5787479, -2333, this.knob));
         FrontierSettingsScreen screen = FrontierSettingsScreen.this;
         (on ? screen.txtOn : screen.txtOff).right(g, sx - 7.0F, (float)this.ctlCY - 4.0F, on && this.active ? TEXT : TEXT_MUTED);
      }
   }

   // ------------------------------------------------------------------------------------------------ chrome widgets

   static final class FooterButton extends AbstractWidget {
      private final boolean primary;
      private final Runnable action;
      private final FrontierSettingsScreen.Txt txt = new FrontierSettingsScreen.Txt();
      private float glow;

      FooterButton(int x, int y, int w, int h, String label, boolean primary, Runnable action) {
         super(x, y, w, h, Component.literal(label));
         this.primary = primary;
         this.action = action;
         this.txt.set(label, STRONG, w - 8);
      }

      public void onClick(double mx, double my) {
         this.action.run();
      }

      public boolean keyPressed(int key, int scan, int mods) {
         if (this.active && this.visible && (key == 257 || key == 335 || key == 32)) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            this.action.run();
            return true;
         }
         return false;
      }

      protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
         this.glow = Mth.lerp(0.35F, this.glow, this.isHoveredOrFocused() ? 1.0F : 0.0F);
         int base = this.primary ? -3568844 : 486539263;
         int hot = this.primary ? -1331121 : 872415231;
         FrontierUi.rect(g, (float)this.getX(), (float)this.getY(), (float)this.width, (float)this.height, (float)this.height / 2.0F, FrontierSettingsScreen.mix(base, hot, this.glow));
         this.txt.center(g, (float)this.getX() + (float)this.width / 2.0F, (float)this.getY() + (float)(this.height - 8) / 2.0F, this.primary ? -14805494 : TEXT);
      }

      protected void updateWidgetNarration(NarrationElementOutput out) {
         this.defaultButtonNarrationText(out);
      }
   }

   final class Tab extends AbstractWidget {
      final FrontierSettingsScreen.Page target;
      private final FrontierSettingsScreen.Txt normal = new FrontierSettingsScreen.Txt();
      private final FrontierSettingsScreen.Txt selected = new FrontierSettingsScreen.Txt();
      private float glow;

      Tab(int x, int y, int w, int h, FrontierSettingsScreen.Page target) {
         super(x, y, w, h, Component.literal(target.title));
         this.target = target;
         this.normal.set(target.tab, BODY, w - 14);
         this.selected.set(target.tab, STRONG, w - 14);
         this.setTooltip(Tooltip.create(Component.literal(target.title + ": " + target.subtitle)));
         this.setTooltipDelay(Duration.ofMillis(600L));
      }

      public void onClick(double mx, double my) {
         FrontierSettingsScreen.this.open(this.target);
      }

      public boolean keyPressed(int key, int scan, int mods) {
         if (key == 257 || key == 335 || key == 32) {
            FrontierSettingsScreen.this.open(this.target);
            return true;
         }
         return false;
      }

      protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
         boolean sel = FrontierSettingsScreen.this.page == this.target;
         this.glow = Mth.lerp(0.35F, this.glow, sel ? 1.0F : (this.isHoveredOrFocused() ? 0.55F : 0.0F));
         if (this.glow > 0.01F) {
            FrontierUi.rect(g, (float)this.getX(), (float)this.getY(), (float)this.width, (float)this.height, 6.0F, FrontierSettingsScreen.mix(16777215, sel ? 786670659 : 352321535, this.glow));
         }

         if (sel) {
            FrontierUi.rect(g, (float)(this.getX() + 1), (float)(this.getY() + 4), 2.5F, (float)(this.height - 8), 1.25F, ACCENT);
         }

         (sel ? this.selected : this.normal)
            .draw(g, (float)(this.getX() + 9), (float)this.getY() + (float)(this.height - 8) / 2.0F, sel ? TEXT : FrontierSettingsScreen.mix(TEXT_MUTED, TEXT_DIM, this.glow * 1.8F));
      }

      protected void updateWidgetNarration(NarrationElementOutput out) {
         this.defaultButtonNarrationText(out);
      }
   }

   /** Compact mode: "&lt; Page &gt;" in the header instead of the sidebar. */
   final class PageSelector extends AbstractWidget {
      private final FrontierSettingsScreen.Txt txt = new FrontierSettingsScreen.Txt();

      PageSelector(int x, int y, int w, int h) {
         super(x, y, w, h, Component.literal("Page"));
         this.txt.set(FrontierSettingsScreen.this.page.title, STRONG, w - 30);
      }

      protected boolean isValidClickButton(int button) {
         return button == 0 || button == 1;
      }

      public void onClick(double mx, double my, int button) {
         this.step(button == 1 || mx < (double)(this.getX() + 16) ? -1 : 1);
      }

      public boolean keyPressed(int key, int scan, int mods) {
         if (key == 263 || key == 262) {
            this.step(key == 263 ? -1 : 1);
            return true;
         }
         return false;
      }

      private void step(int d) {
         FrontierSettingsScreen.Page[] pages = FrontierSettingsScreen.Page.values();
         FrontierSettingsScreen.this.open(pages[Math.floorMod(FrontierSettingsScreen.this.page.ordinal() + d, pages.length)]);
      }

      protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
         FrontierSettingsScreen.selector(g, (float)this.getX(), (float)this.getY() + (float)this.height / 2.0F, (float)this.width, this.txt, true, this.isHovered(), mx, TEXT);
      }

      protected void updateWidgetNarration(NarrationElementOutput out) {
         out.add(NarratedElementType.TITLE, Component.literal("Settings page: " + FrontierSettingsScreen.this.page.title));
      }
   }

   final class PresetCard extends AbstractWidget {
      final HuntConfig.GraphicsPreset preset;
      final ResourceLocation art;
      private final float load;
      private final FrontierSettingsScreen.Txt titleTxt = new FrontierSettingsScreen.Txt();
      private final FrontierSettingsScreen.Txt tagTxt = new FrontierSettingsScreen.Txt();
      private float glow;
      private boolean filtered;

      PresetCard(HuntConfig.GraphicsPreset preset) {
         super(0, 0, 10, 10, Component.literal(FrontierSettingsScreen.title(preset)));
         this.preset = preset;
         this.art = FrontierHunts.id("textures/gui/settings/preset_" + preset.name().toLowerCase(Locale.ROOT) + ".png");
         this.load = (float)FrontierGraphics.cost(FrontierGraphics.of(preset)) / 100.0F;
         this.setTooltip(Tooltip.create(Component.literal(FrontierSettingsScreen.title(preset) + ": " + FrontierSettingsScreen.tagline(preset))));
         this.setTooltipDelay(FrontierSettingsScreen.TOOLTIP_DELAY);
      }

      void layoutText() {
         this.titleTxt.set(FrontierSettingsScreen.title(this.preset), STRONG, this.width - 16);
         this.tagTxt.set(FrontierSettingsScreen.shortTag(this.preset), SMALL, this.width - 16);
      }

      @Override
      public boolean isMouseOver(double mx, double my) {
         return super.isMouseOver(mx, my) && FrontierSettingsScreen.this.inContent(mx, my);
      }

      @Override
      protected boolean clicked(double mx, double my) {
         return super.clicked(mx, my) && FrontierSettingsScreen.this.inContent(mx, my);
      }

      public void onClick(double mx, double my) {
         this.choose();
      }

      public boolean keyPressed(int key, int scan, int mods) {
         if (key == 257 || key == 335 || key == 32) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            this.choose();
            return true;
         }
         return false;
      }

      private void choose() {
         FrontierGraphics.apply(this.preset, false);
         FrontierSettingsScreen screen = FrontierSettingsScreen.this;
         screen.session.client = true;
         screen.session.packs = this.preset;
         screen.session.version++;
      }

      protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
         boolean sel = FrontierSettingsScreen.safePreset() == this.preset;
         this.glow = Mth.lerp(0.3F, this.glow, this.isHoveredOrFocused() ? 1.0F : 0.0F);
         int x = this.getX();
         int y = this.getY();
         int w = this.width;
         int h = this.height;
         int ring = sel ? ACCENT : FrontierSettingsScreen.mix(-13945809, -10853026, this.glow);
         FrontierUi.outline(g, (float)x, (float)y, (float)w, (float)h, 8.0F, ring, -15130596);
         if (sel) {
            FrontierUi.outline(g, (float)x + 0.5F, (float)y + 0.5F, (float)(w - 1), (float)(h - 1), 7.5F, ACCENT, -15130596);
         }

         int artH = (int)((float)w * 0.5625F);
         if (!this.filtered) {
            this.filtered = true;
            try {
               com.formaworks.frontierhunts.artqa.client.GuiArtTexture.bind(this.art, true); // [artqa] mipmapped preset art (1024 px drawn ~120 GUI px wide)
            } catch (RuntimeException var15) {
            }
         }

         g.enableScissor(x + 2, y + 2, x + w - 2, y + artH);
         g.blit(this.art, x + 2, y + 2, w - 4, artH - 2, 0.0F, 0.0F, 1024, 576, 1024, 576);
         g.fillGradient(x + 2, y + artH - 16, x + w - 2, y + artH, 0, -1728053248);
         if (this.glow > 0.02F) {
            g.fillGradient(x + 2, y + 2, x + w - 2, y + artH, FrontierSettingsScreen.mix(16777215, 352321535, this.glow), 16777215);
         }
         g.disableScissor();
         if (sel) {
            FrontierSettingsScreen.Txt active = FrontierSettingsScreen.this.txtActive;
            int bw = active.w + 10;
            FrontierUi.rect(g, (float)(x + w - bw - 6), (float)(y + 6), (float)bw, 11.0F, 5.5F, -421288893);
            active.draw(g, (float)(x + w - bw - 1), (float)y + 8.5F, -14805494);
         }

         this.titleTxt.draw(g, (float)(x + 8), (float)(y + artH + 6), sel ? ACCENT : TEXT);
         this.tagTxt.draw(g, (float)(x + 8), (float)(y + artH + 18), TEXT_DIM);
         FrontierSettingsScreen.meter(g, (float)(x + 8), (float)(y + h - 10), (float)(w - 16), 3.0F, this.load);
      }

      protected void updateWidgetNarration(NarrationElementOutput out) {
         out.add(NarratedElementType.TITLE, Component.literal(FrontierSettingsScreen.title(this.preset) + " preset, " + FrontierSettingsScreen.tagline(this.preset)));
      }
   }
}
