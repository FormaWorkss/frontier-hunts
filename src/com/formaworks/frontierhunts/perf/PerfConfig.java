package com.formaworks.frontierhunts.perf;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * [perf] Performance options. Declared here and built into HuntConfig's SERVER and CLIENT specs by one
 * hook line each (so they live in the same config files as everything else).
 */
public final class PerfConfig {
   /** Where realistic trees switch detail (see {@code TreeLod}): horizontal blocks from the camera. */
   public enum TreeDetail {
      /** full detail inside 24 blocks, flat cutouts beyond 64 */
      PERFORMANCE(24, 64, 4),
      /** full detail inside 32 blocks, flat cutouts beyond 96 */
      BALANCED(32, 96, 4),
      /** full detail inside 48 blocks, flat cutouts beyond 160 */
      ULTRA(48, 160, 6),
      /** full detail inside 64 blocks, never flat cutouts (the reduced 3D crown all the way out) */
      MAXIMUM(64, 0, 6);

      public final int near, impostor, rebuildsPerTick;

      TreeDetail(int near, int impostor, int rebuildsPerTick) {
         this.near = near;
         this.impostor = impostor;
         this.rebuildsPerTick = rebuildsPerTick;
      }
   }

   // server
   public static ModConfigSpec.BooleanValue AI_THROTTLE;
   public static ModConfigSpec.IntValue AI_FULL_RATE_DISTANCE;
   public static ModConfigSpec.IntValue AI_MINIMAL_DISTANCE;
   // client
   public static ModConfigSpec.EnumValue<TreeDetail> TREE_DETAIL;
   public static ModConfigSpec.BooleanValue ANIMAL_ANIMATION_LOD;
   /** [perf2] Client hitch logger (logs/frontierhunts-perf.log) and its threshold. */
   public static ModConfigSpec.BooleanValue HITCH_LOGGER;
   public static ModConfigSpec.IntValue HITCH_THRESHOLD_MS;
   /** [shaderperf] With a shader pack on: realistic trees one detail step lighter and thick grass drawn normal. */
   public static ModConfigSpec.BooleanValue SHADER_LIGHTER;

   private PerfConfig() {
   }

   /** Called from HuntConfig while it builds the SERVER spec. */
   public static void server(ModConfigSpec.Builder b) {
      b.push("performance");
      AI_THROTTLE = b.comment(
            "Let calm wildlife far from every player think less often (goal selection, perception, rut sign work). Animals that are hurt, bleeding, downed, alarmed or fleeing always run at full rate, blood trails and tracks are never thinned, and an animal returns to full rate as soon as a player comes near. Turn off to run every animal at full rate everywhere.")
         .define("wildlifeAiThrottle", true);
      AI_FULL_RATE_DISTANCE = b.comment("Wildlife within this many blocks of a player always thinks at full rate.")
         .defineInRange("wildlifeFullRateDistance", 48, 16, 256);
      AI_MINIMAL_DISTANCE = b.comment("Beyond this many blocks from every player, calm wildlife thinks at the lowest rate.")
         .defineInRange("wildlifeMinimalRateDistance", 128, 32, 512);
      b.pop();
   }

   /** Called from HuntConfig while it builds the CLIENT spec. */
   public static void client(ModConfigSpec.Builder b) {
      b.push("performance");
      TREE_DETAIL = b.comment(
            "Realistic world trees: how far out they keep full detail, and where they become flat cutout cards (a dozen quads instead of thousands). PERFORMANCE: full detail to 24 blocks, cutouts beyond 64. BALANCED (shown as Standard): 32 / 96. ULTRA: 48 / 160. MAXIMUM: 64, never cutouts. The Ultra preset sets ULTRA; lower it on a weaker PC and the preset stays Ultra. Works with any shader pack.")
         .defineEnum("treeDetail", TreeDetail.ULTRA); // [presets] Ultra preset default (was BALANCED)
      ANIMAL_ANIMATION_LOD = b.comment(
            "Animals that are far away or small on screen update their animation and skinning at a reduced rate (never close up, never through a scope). Off: every animal every frame.")
         .define("animalAnimationLod", true);
      // [perf2]
      HITCH_LOGGER = b.comment(
            "Diagnostics: record frames slower than hitchThresholdMs with what the mod was doing at the time (trees grown, sections rebuilt, animals skinned, garbage-collector pauses...) and write a summary every 10 seconds to logs/frontierhunts-perf.log. Also adds lines to the F3 screen. Leave off unless you are measuring; /frontierperf on|off toggles it until restart.")
         .define("hitchLogger", false);
      HITCH_THRESHOLD_MS = b.comment("Diagnostics: a frame longer than this many milliseconds counts as a hitch for the hitch logger.")
         .defineInRange("hitchThresholdMs", 25, 5, 1000);
      // [shaderperf]
      SHADER_LIGHTER = b.comment(
            "While an Iris shader pack is on, draw the realistic world one step lighter: trees use the next lower 'treeDetail' (Ultra draws as Standard, Maximum as Ultra, Standard as Performance) and Thick grass is drawn Normal. Shader packs draw the world twice or more (shadows + the scene), so this is the biggest frame-rate saver with shaders. Nothing changes without a shader pack. Turn off to keep your own settings under shaders.")
         .define("lighterWithShaderPacks", true);
      b.pop();
   }

   /** The configured tree detail (BALANCED before the config has loaded). */
   public static TreeDetail treeDetail() {
      try {
         return TREE_DETAIL == null ? TreeDetail.BALANCED : TREE_DETAIL.get();
      } catch (RuntimeException e) {
         return TreeDetail.BALANCED;
      }
   }

   public static boolean animationLod() {
      try {
         return ANIMAL_ANIMATION_LOD == null || ANIMAL_ANIMATION_LOD.get();
      } catch (RuntimeException e) {
         return true;
      }
   }

   /** [shaderperf] Whether the shader-pack step-down is wanted (true before the config has loaded). */
   public static boolean shaderLighter() {
      try {
         return SHADER_LIGHTER == null || SHADER_LIGHTER.get();
      } catch (RuntimeException e) {
         return true;
      }
   }

   /** [shaderperf] The tree detail one step lighter (Performance stays Performance). */
   public static TreeDetail lighter(TreeDetail d) {
      return switch (d) {
         case MAXIMUM -> TreeDetail.ULTRA;
         case ULTRA -> TreeDetail.BALANCED;
         case BALANCED, PERFORMANCE -> TreeDetail.PERFORMANCE;
      };
   }

   /** [perf2] */
   public static boolean hitchLogger() {
      try {
         return HITCH_LOGGER != null && HITCH_LOGGER.get();
      } catch (RuntimeException e) {
         return false;
      }
   }

   /** [perf2] */
   public static int hitchThresholdMs() {
      try {
         return HITCH_THRESHOLD_MS == null ? 25 : HITCH_THRESHOLD_MS.get();
      } catch (RuntimeException e) {
         return 25;
      }
   }

   public static boolean aiThrottle() {
      try {
         return AI_THROTTLE == null || AI_THROTTLE.get();
      } catch (RuntimeException e) {
         return true;
      }
   }

   public static int fullRateDistance() {
      try {
         return AI_FULL_RATE_DISTANCE == null ? 48 : AI_FULL_RATE_DISTANCE.get();
      } catch (RuntimeException e) {
         return 48;
      }
   }

   public static int minimalDistance() {
      try {
         return AI_MINIMAL_DISTANCE == null ? 128 : AI_MINIMAL_DISTANCE.get();
      } catch (RuntimeException e) {
         return 128;
      }
   }
}
