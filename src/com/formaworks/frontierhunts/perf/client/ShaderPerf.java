package com.formaworks.frontierhunts.perf.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.perf.PerfConfig;

/**
 * [shaderperf] The "lighter with shader packs" adjustment, in one place.
 *
 * <p>An Iris shader pack draws the world at least twice (the sun's shadow map, then the scene), and the realistic
 * world's cost is chunk geometry (3D trees, their crowns and the dressed forest floor), which both passes draw. While a
 * pack is on and {@code performance.lighterWithShaderPacks} is set (the default), the realistic trees use the next lower
 * tree detail and Thick pasture grass is drawn Normal. Without a pack nothing changes.
 *
 * <p>The shader state comes from {@code ShaderState.on}, which the client tick sets from Iris's public API by
 * reflection (no Iris: always false, never an error). Every method here is safe without Iris, Sodium or Oculus.
 */
public final class ShaderPerf {
   private ShaderPerf() {
   }

   /** A shader pack is on right now (Iris public API; false without Iris or with any other shader mod). */
   public static boolean packOn() {
      try {
         return com.formaworks.frontierhunts.client.ShaderState.on;
      } catch (Throwable t) {
         return false;
      }
   }

   /** The step-down applies now: the setting is on and a shader pack is in use. */
   public static boolean lighter() {
      return packOn() && PerfConfig.shaderLighter();
   }

   /** The tree detail to draw with: the configured one, one step lighter while {@link #lighter()}. */
   public static PerfConfig.TreeDetail treeDetail() {
      PerfConfig.TreeDetail d = PerfConfig.treeDetail();
      return lighter() ? PerfConfig.lighter(d) : d;
   }

   /** The pasture grass thickness to draw with: THICK reads as NORMAL while {@link #lighter()}. */
   public static HuntConfig.GrassThickness grassThickness(HuntConfig.GrassThickness configured) {
      return configured == HuntConfig.GrassThickness.THICK && lighter() ? HuntConfig.GrassThickness.NORMAL : configured;
   }

   /**
    * Called on the client thread when the shader pack is switched on or off, before the world is re-meshed for it, so
    * that re-mesh already uses the new tree bands and grass (instead of re-meshing everything twice).
    */
   public static void shaderPackChanged() {
      try {
         com.formaworks.frontierhunts.client.tree.TreeLod.followShaderPack();
      } catch (Throwable t) {
         // cosmetic: the 20-tick follow-up in TreeLod picks the change up anyway
      }
      try {
         com.formaworks.frontierhunts.landscape.AlpineGrassModels.followShaderPack();
      } catch (Throwable t) {
      }
   }

   /** One line for F3 and the hitch log header. */
   public static String line() {
      if (!packOn()) {
         return "shader pack off";
      }
      if (!PerfConfig.shaderLighter()) {
         return "shader pack on (lighter-with-shaders off)";
      }
      return "shader pack on: trees drawn " + treeDetail() + " (set " + PerfConfig.treeDetail() + ")";
   }
}
