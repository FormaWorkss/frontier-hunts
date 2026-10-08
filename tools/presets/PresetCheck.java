import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.HuntConfig.GraphicsPreset;
import com.formaworks.frontierhunts.client.FrontierGraphics;
import com.formaworks.frontierhunts.client.FrontierGraphics.Bundle;
import com.formaworks.frontierhunts.perf.PerfConfig;

/**
 * [presets] Offline check of the two-preset logic (tools/presets/run.sh): preset bundles, what Ultra / Vanilla tolerate,
 * and that the retired values resolve to Ultra. Pure logic only (no config files, no Minecraft).
 */
public final class PresetCheck {
   static int fails;

   static void check(boolean ok, String what) {
      System.out.println((ok ? "ok   " : "FAIL ") + what);
      if (!ok) fails++;
   }

   static Bundle with(Bundle b, HuntConfig.Quality q, HuntConfig.Detail d, HuntConfig.GrassThickness t, HuntConfig.EffectLevel w, PerfConfig.TreeDetail tr) {
      return new Bundle(q, b.animals(), d, b.grass(), b.grassHeight(), b.grassWidth(), t, w, b.wildlifeLife(), b.breath(), b.spindrift(), b.world(), tr);
   }

   public static void main(String[] a) {
      Bundle ultra = FrontierGraphics.of(GraphicsPreset.ULTRA), vanilla = FrontierGraphics.of(GraphicsPreset.CLASSIC);
      check(FrontierGraphics.of(GraphicsPreset.BALANCED).equals(ultra), "retired BALANCED resolves to the Ultra bundle");
      check(FrontierGraphics.of(GraphicsPreset.HIGH).equals(ultra), "retired HIGH resolves to the Ultra bundle");
      check(ultra.animals() == HuntConfig.AnimalStyle.REALISTIC && ultra.world() == HuntConfig.WorldLook.REALISTIC, "Ultra: realistic animals and world");
      check(ultra.animalDetail() == HuntConfig.Detail.ULTRA && ultra.trees() == PerfConfig.TreeDetail.ULTRA && ultra.effects() == HuntConfig.Quality.CINEMATIC
         && ultra.grassThickness() == HuntConfig.GrassThickness.THICK && ultra.waterfalls() == HuntConfig.EffectLevel.ULTRA, "Ultra: everything maxed (unchanged)");
      check(vanilla.animals() == HuntConfig.AnimalStyle.VANILLA && vanilla.grass() == HuntConfig.GrassStyle.VANILLA && vanilla.world() == HuntConfig.WorldLook.MINECRAFT
         && !vanilla.wildlifeLife() && !vanilla.breath() && !vanilla.spindrift(), "Vanilla: Minecraft mobs, grass and world, no extras");
      check(FrontierGraphics.matches(GraphicsPreset.ULTRA, ultra), "Ultra bundle matches Ultra");
      check(FrontierGraphics.matches(GraphicsPreset.CLASSIC, vanilla), "Vanilla bundle matches Vanilla");
      Bundle weak = with(ultra, HuntConfig.Quality.PERFORMANCE, HuntConfig.Detail.LOW, HuntConfig.GrassThickness.LIGHT, HuntConfig.EffectLevel.OFF, PerfConfig.TreeDetail.PERFORMANCE);
      check(FrontierGraphics.matches(GraphicsPreset.ULTRA, weak), "Ultra tuned all the way down is still Ultra");
      check(!FrontierGraphics.matches(GraphicsPreset.CLASSIC, weak), "...and not Vanilla");
      check(FrontierGraphics.cost(weak) < FrontierGraphics.cost(ultra), "tuning lowers the GPU load meter (" + FrontierGraphics.cost(weak) + " < " + FrontierGraphics.cost(ultra) + ")");
      Bundle ultraMcWorld = new Bundle(ultra.effects(), ultra.animals(), ultra.animalDetail(), ultra.grass(), ultra.grassHeight(), ultra.grassWidth(), ultra.grassThickness(),
         ultra.waterfalls(), ultra.wildlifeLife(), ultra.breath(), ultra.spindrift(), HuntConfig.WorldLook.MINECRAFT, ultra.trees());
      check(!FrontierGraphics.matches(GraphicsPreset.ULTRA, ultraMcWorld), "Ultra with the Minecraft world is Custom (a look change)");
      Bundle vanillaMcAnimalsReal = new Bundle(vanilla.effects(), HuntConfig.AnimalStyle.REALISTIC, vanilla.animalDetail(), vanilla.grass(), vanilla.grassHeight(), vanilla.grassWidth(),
         vanilla.grassThickness(), vanilla.waterfalls(), vanilla.wildlifeLife(), vanilla.breath(), vanilla.spindrift(), vanilla.world(), vanilla.trees());
      check(!FrontierGraphics.matches(GraphicsPreset.CLASSIC, vanillaMcAnimalsReal) && !FrontierGraphics.matches(GraphicsPreset.ULTRA, vanillaMcAnimalsReal), "Vanilla with realistic animals is Custom");
      Bundle vanillaTrees = with(vanilla, vanilla.effects(), HuntConfig.Detail.ULTRA, vanilla.grassThickness(), vanilla.waterfalls(), PerfConfig.TreeDetail.MAXIMUM);
      check(FrontierGraphics.matches(GraphicsPreset.CLASSIC, vanillaTrees), "Vanilla ignores realistic-only animal detail / tree distance");
      Bundle vanillaFx = with(vanilla, HuntConfig.Quality.CINEMATIC, vanilla.animalDetail(), vanilla.grassThickness(), vanilla.waterfalls(), vanilla.trees());
      check(!FrontierGraphics.matches(GraphicsPreset.CLASSIC, vanillaFx), "Vanilla with cinematic effects is Custom");
      check(!FrontierGraphics.matches(GraphicsPreset.CUSTOM, ultra), "CUSTOM never 'matches'");
      System.out.println(fails == 0 ? "PRESETS OK" : fails + " FAILED");
      System.exit(fails == 0 ? 0 : 1);
   }
}
