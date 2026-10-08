package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.landscape.AlpineGrassModels;
import com.formaworks.frontierhunts.landscape.AlpineLook;

/**
 * Graphics presets. [presets] There are two: Vanilla (stored as CLASSIC: plain Minecraft-looking animals, grass and trees)
 * and Ultra (realistic world and wildlife). The retired BALANCED and HIGH values are switched to Ultra on load
 * ({@link #migrate}), and so is the retired BLOCKY ("Minecraft+") animal style.
 */
public final class FrontierGraphics {
   private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger(); // [presets]
   private static boolean loggedBalanced, loggedBlocky; // [presets] log each migration once

   private FrontierGraphics() {
   }

   /** [presets] Vanilla for CLASSIC; Ultra for everything else (the retired BALANCED / HIGH, and CUSTOM's fallback). */
   public static FrontierGraphics.Bundle of(HuntConfig.GraphicsPreset preset) {
      return switch (preset) {
         case CLASSIC -> new FrontierGraphics.Bundle(
         HuntConfig.Quality.PERFORMANCE,
         HuntConfig.AnimalStyle.VANILLA,
         HuntConfig.Detail.LOW,
         HuntConfig.GrassStyle.VANILLA,
         100,
         100,
         HuntConfig.GrassThickness.LIGHT,
         HuntConfig.EffectLevel.LOW,
         false,
         false,
         false,
         HuntConfig.WorldLook.MINECRAFT,
         com.formaworks.frontierhunts.perf.PerfConfig.TreeDetail.PERFORMANCE // [perf]
      );
         default -> new FrontierGraphics.Bundle(
         HuntConfig.Quality.CINEMATIC,
         HuntConfig.AnimalStyle.REALISTIC,
         HuntConfig.Detail.ULTRA,
         HuntConfig.GrassStyle.FRONTIER,
         110,
         100,
         HuntConfig.GrassThickness.THICK,
         HuntConfig.EffectLevel.ULTRA,
         true,
         true,
         true,
         HuntConfig.WorldLook.REALISTIC,
         com.formaworks.frontierhunts.perf.PerfConfig.TreeDetail.ULTRA // [perf]
      );
      };
   }

   static FrontierGraphics.Bundle highBundle() {
      return new FrontierGraphics.Bundle(
         HuntConfig.Quality.CINEMATIC,
         HuntConfig.AnimalStyle.REALISTIC,
         HuntConfig.Detail.HIGH,
         HuntConfig.GrassStyle.FRONTIER,
         100,
         100,
         HuntConfig.GrassThickness.NORMAL,
         HuntConfig.EffectLevel.HIGH,
         true,
         true,
         true,
         HuntConfig.WorldLook.MINECRAFT,
         com.formaworks.frontierhunts.perf.PerfConfig.TreeDetail.BALANCED // [perf] (configs from before tree detail existed)
      );
   }

   static FrontierGraphics.Bundle withWorld(FrontierGraphics.Bundle b, HuntConfig.WorldLook w) {
      return new FrontierGraphics.Bundle(
         b.effects(),
         b.animals(),
         b.animalDetail(),
         b.grass(),
         b.grassHeight(),
         b.grassWidth(),
         b.grassThickness(),
         b.waterfalls(),
         b.wildlifeLife(),
         b.breath(),
         b.spindrift(),
         w,
         b.trees() // [perf]
      );
   }

   public static FrontierGraphics.Bundle current() {
      return new FrontierGraphics.Bundle(
         HuntConfig.QUALITY.get(),
         animalStyle(), // [presets] the retired BLOCKY reads as REALISTIC
         HuntConfig.ANIMAL_DETAIL.get(),
         HuntConfig.GRASS_STYLE.get(),
         HuntConfig.GRASS_HEIGHT_PERCENT.get(),
         HuntConfig.GRASS_WIDTH_PERCENT.get(),
         HuntConfig.GRASS_THICKNESS.get(),
         HuntConfig.WATERFALL_DETAIL.get(),
         HuntConfig.WILDLIFE_LIFE.get(),
         HuntConfig.BREATH_VAPOR.get(),
         HuntConfig.MOUNTAIN_SPINDRIFT.get(),
         HuntConfig.WORLD_LOOK.get(),
         com.formaworks.frontierhunts.perf.PerfConfig.treeDetail() // [perf]
      );
   }

   /** The preset the current settings are (Vanilla, Ultra - possibly tuned for a weaker PC - or Custom). */
   public static HuntConfig.GraphicsPreset matching() {
      FrontierGraphics.Bundle now = current();
      // [presets] only the two presets; BALANCED / HIGH are retired
      if (matches(HuntConfig.GraphicsPreset.CLASSIC, now)) {
         return HuntConfig.GraphicsPreset.CLASSIC;
      }
      if (matches(HuntConfig.GraphicsPreset.ULTRA, now)) {
         return HuntConfig.GraphicsPreset.ULTRA;
      }
      return HuntConfig.GraphicsPreset.CUSTOM;
   }

   /**
    * [presets] Whether settings b give the preset's look. Ultra stays Ultra while its performance settings are lowered
    * (effects quality, realistic animal detail, grass thickness, waterfall detail, distant trees): they trade distance,
    * density and particle counts for frames, never the look, so a weaker PC can run Ultra without becoming Custom.
    * Vanilla ignores realistic animal detail and distant trees, which do nothing in its Minecraft world.
    */
   public static boolean matches(HuntConfig.GraphicsPreset preset, FrontierGraphics.Bundle b) {
      if (preset == HuntConfig.GraphicsPreset.CUSTOM) {
         return false;
      }
      return normalize(preset, b).equals(of(preset));
   }

   /** [presets] b with the settings the preset tolerates replaced by the preset's own values. */
   static FrontierGraphics.Bundle normalize(HuntConfig.GraphicsPreset preset, FrontierGraphics.Bundle b) {
      FrontierGraphics.Bundle w = of(preset);
      boolean ultra = preset != HuntConfig.GraphicsPreset.CLASSIC;
      return new FrontierGraphics.Bundle(
         ultra ? w.effects() : b.effects(),
         b.animals(),
         w.animalDetail(),
         b.grass(),
         b.grassHeight(),
         b.grassWidth(),
         ultra ? w.grassThickness() : b.grassThickness(),
         ultra ? w.waterfalls() : b.waterfalls(),
         b.wildlifeLife(),
         b.breath(),
         b.spindrift(),
         b.world(),
         w.trees()
      );
   }

   /**
    * Brings old settings up to date (on the first client tick and when the settings screen opens). [presets] The retired
    * Balanced preset (and the older High) becomes Ultra, and the retired BLOCKY ("Minecraft+") animal style becomes
    * Realistic; each is logged once.
    */
   public static void migrate() {
      try {
         HuntConfig.GraphicsPreset preset = HuntConfig.GRAPHICS_PRESET.get();
         migrateRetiredPacks(); // [presets]
         if (preset == HuntConfig.GraphicsPreset.BALANCED
            || preset == HuntConfig.GraphicsPreset.HIGH
            || current().equals(highBundle())) {
            if (preset == HuntConfig.GraphicsPreset.BALANCED && !loggedBalanced) { // [presets]
               loggedBalanced = true;
               LOG.info("Frontier Hunts: the Balanced graphics preset has been retired - switched to Ultra. On a weaker PC, lower the 'Tune Ultra' settings on the Graphics preset page (the preset stays Ultra).");
            }
            apply(HuntConfig.GraphicsPreset.ULTRA);
            return;
         }

         // [presets] a Custom setup with the retired Minecraft+ animals: realistic animals
         if (HuntConfig.ANIMAL_STYLE.get() == HuntConfig.AnimalStyle.BLOCKY) {
            if (!loggedBlocky) {
               loggedBlocky = true;
               LOG.info("Frontier Hunts: the Minecraft+ animal style has been retired with the Balanced preset - animals are drawn Realistic.");
            }
            HuntConfig.ANIMAL_STYLE.set(HuntConfig.AnimalStyle.REALISTIC);
            HuntConfig.GRAPHICS_PRESET.set(matching());
            save();
            return;
         }

         // Ultra configs written before Ultra included the realistic world
         FrontierGraphics.Bundle now = current();
         if (preset == HuntConfig.GraphicsPreset.ULTRA
            && now.world() == HuntConfig.WorldLook.MINECRAFT
            && matches(HuntConfig.GraphicsPreset.ULTRA, withWorld(now, HuntConfig.WorldLook.REALISTIC))) {
            HuntConfig.WORLD_LOOK.set(HuntConfig.WorldLook.REALISTIC);
            save();
         }
      } catch (RuntimeException var1) {
      }
   }

   /**
    * [presets] Texture / shader packs picked for the retired Balanced preset move to Ultra (when Ultra has none of its
    * own), so a Balanced player keeps the packs they chose; the retired slot is cleared either way.
    */
   private static void migrateRetiredPacks() {
      boolean changed = false;
      for (net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String>[] slots : java.util.List.of(HuntConfig.PRESET_RESOURCE_PACK, HuntConfig.PRESET_SHADER_PACK)) {
         if (slots[1] == null || slots[3] == null) {
            continue;
         }
         String old = slots[1].get();
         if (old != null && !old.isEmpty()) {
            String ultra = slots[3].get();
            if (ultra == null || ultra.isEmpty()) {
               slots[3].set(old);
            }
            slots[1].set("");
            changed = true;
         }
      }
      if (changed) {
         try {
            HuntConfig.CLIENT.save();
         } catch (RuntimeException e) {
         }
      }
   }

   public static void apply(HuntConfig.GraphicsPreset preset) {
      apply(preset, true);
   }

   /** [gui] commit=false only sets the values: the settings screen saves and reloads chunks/packs once, when it closes. */
   public static void apply(HuntConfig.GraphicsPreset preset, boolean commit) {
      if (preset == HuntConfig.GraphicsPreset.HIGH || preset == HuntConfig.GraphicsPreset.BALANCED) { // [presets] retired: Ultra
         preset = HuntConfig.GraphicsPreset.ULTRA;
      }

      if (preset != HuntConfig.GraphicsPreset.CUSTOM) {
         FrontierGraphics.Bundle b = of(preset);
         HuntConfig.QUALITY.set(b.effects());
         HuntConfig.ANIMAL_STYLE.set(b.animals());
         HuntConfig.ANIMAL_DETAIL.set(b.animalDetail());
         HuntConfig.GRASS_STYLE.set(b.grass());
         HuntConfig.GRASS_HEIGHT_PERCENT.set(Integer.valueOf(b.grassHeight()));
         HuntConfig.GRASS_WIDTH_PERCENT.set(Integer.valueOf(b.grassWidth()));
         HuntConfig.GRASS_THICKNESS.set(b.grassThickness());
         HuntConfig.WATERFALL_DETAIL.set(b.waterfalls());
         HuntConfig.WILDLIFE_LIFE.set(Boolean.valueOf(b.wildlifeLife()));
         HuntConfig.BREATH_VAPOR.set(Boolean.valueOf(b.breath()));
         HuntConfig.MOUNTAIN_SPINDRIFT.set(Boolean.valueOf(b.spindrift()));
         HuntConfig.WORLD_LOOK.set(b.world());
         com.formaworks.frontierhunts.perf.PerfConfig.TREE_DETAIL.set(b.trees()); // [perf]
         // [1.1.2] the snowpack follows the preset (it is not part of the preset match, so it can be switched on its own)
         // [1.2.2] every preset now draws the smooth snowpack: blocky layers you can walk up without jumping read wrong
         HuntConfig.SNOW_STYLE.set(HuntConfig.SnowStyle.FRONTIER);
         HuntConfig.GRAPHICS_PRESET.set(preset);
         if (commit) { // [gui]
            save();
         }
      }
   }

   public static void changed() {
      HuntConfig.GRAPHICS_PRESET.set(matching());
      save();
   }

   public static void save() {
      try {
         HuntConfig.CLIENT.save();
      } catch (RuntimeException var1) {
      }

      AlpineGrassModels.apply();
      com.formaworks.frontierhunts.season.client.SnowLook.apply(); // [1.1.2] rebuilds chunks when the snow style changed
      RealisticWorld.requestSync();
   }

   /** [presets] The animal style to draw: VANILLA or REALISTIC (the retired BLOCKY, or an unreadable config, is REALISTIC). */
   public static HuntConfig.AnimalStyle animalStyle() {
      try {
         return HuntConfig.ANIMAL_STYLE.get() == HuntConfig.AnimalStyle.VANILLA ? HuntConfig.AnimalStyle.VANILLA : HuntConfig.AnimalStyle.REALISTIC;
      } catch (RuntimeException var1) {
         return HuntConfig.AnimalStyle.REALISTIC;
      }
   }

   public static boolean vanillaAnimals() {
      return animalStyle() == HuntConfig.AnimalStyle.VANILLA;
   }

   public static boolean realisticWorld() {
      try {
         return HuntConfig.WORLD_LOOK.get() == HuntConfig.WorldLook.REALISTIC;
      } catch (RuntimeException var1) {
         return false;
      }
   }

   /** Every animal that is not Vanilla-style is realistic ([presets] the Minecraft+ style is retired). */
   public static boolean realisticAnimals() {
      return animalStyle() == HuntConfig.AnimalStyle.REALISTIC;
   }

   /** [presets] The Minecraft+ (BLOCKY) animal style was retired with the Balanced preset: always false. */
   @Deprecated
   public static boolean stylizedAnimals() {
      return false;
   }

   public static int animalDetail() {
      try {
         return HuntConfig.ANIMAL_DETAIL.get().ordinal();
      } catch (RuntimeException var1) {
         return 1;
      }
   }

   public static double waterfallScale() {
      HuntConfig.EffectLevel level;
      try {
         level = HuntConfig.WATERFALL_DETAIL.get();
      } catch (RuntimeException var2) {
         return 1.0;
      }
      return switch (level) {
         case OFF -> 0.0;
         case LOW -> 0.45;
         case MEDIUM -> 1.0;
         case HIGH -> 1.3;
         case ULTRA -> 1.7;
      };
   }

   public static boolean shaderPackActive() {
      return AlpineLook.shaders();
   }

   public static int cost(FrontierGraphics.Bundle b) {
      double c = 8.0;
      c += (double)(b.effects().ordinal() * 7);
      if (b.animals() == HuntConfig.AnimalStyle.REALISTIC) {
         c += (double)(14 + b.animalDetail().ordinal() * 7);
      }

      if (b.grass() != HuntConfig.GrassStyle.OFF) {
         double grass = b.grass() == HuntConfig.GrassStyle.VANILLA ? 6.0 : 10.0;

         grass *= switch (b.grassThickness()) {
            case LIGHT -> 0.6;
            case NORMAL -> 1.0;
            case THICK -> 1.9;
         };
         grass *= Math.max(0.6, (double)b.grassHeight() / 100.0 * (double)b.grassWidth() / 100.0);
         c += grass;
      }

      c += (double)b.waterfalls().ordinal() * 3.5;
      if (b.wildlifeLife()) {
         c += 3.0;
      }

      if (b.breath()) {
         c++;
      }

      if (b.spindrift()) {
         c += 2.0;
      }

      if (b.world() == HuntConfig.WorldLook.REALISTIC) {
         c += 12.0;
         // [perf] how far trees stay 3D (cutout trees beyond that cost next to nothing)
         c += switch (b.trees()) {
            case PERFORMANCE -> -4.0;
            case BALANCED -> 0.0;
            case ULTRA -> 4.0;
            case MAXIMUM -> 10.0;
         };
      }

      return (int)Math.round(Math.min(100.0, Math.max(0.0, c)));
   }

   public static record Bundle(
      HuntConfig.Quality effects,
      HuntConfig.AnimalStyle animals,
      HuntConfig.Detail animalDetail,
      HuntConfig.GrassStyle grass,
      int grassHeight,
      int grassWidth,
      HuntConfig.GrassThickness grassThickness,
      HuntConfig.EffectLevel waterfalls,
      boolean wildlifeLife,
      boolean breath,
      boolean spindrift,
      HuntConfig.WorldLook world,
      com.formaworks.frontierhunts.perf.PerfConfig.TreeDetail trees // [perf] realistic-tree detail distances
   ) {
   }
}
