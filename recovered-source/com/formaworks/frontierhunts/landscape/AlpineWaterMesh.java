package com.formaworks.frontierhunts.landscape;

/**
 * [1.2.8] Retired: this used to draw its own animated foam / whitewater sheets over running water and waterfalls (scanned on
 * every chunk load). Water is plain Minecraft water now, so nothing is scanned or drawn. The public methods stay so the
 * debug commands that print its counters still link.
 */
public final class AlpineWaterMesh {
   public AlpineWaterMesh() {
   }

   public static long renderedQuads() {
      return 0L;
   }

   public static int continuousStrips() {
      return 0;
   }

   public static String diagnostics() {
      return "water overlay retired (vanilla water)";
   }
}
