package com.formaworks.frontierhunts.season.client;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.state.BlockState;

/**
 * [1.2.0] Frost on the trees where it snows. Snow is no longer stacked on leaves as blocks (it showed as white squares
 * and tents floating in the crowns); instead the foliage itself is dusted white:
 * <ul>
 * <li>Minecraft-style leaves: their colour is drawn toward a cold white, more the colder it is (this class).</li>
 * <li>The realistic trees: the upward-facing, open sprays of the crown turn snow-white (client.tree.SeasonalFoliage).</li>
 * </ul>
 * "Cold" is the biome's temperature at that height, blended across biome borders the way grass colours are, so the
 * frost thins out toward the edge of the snow instead of stopping at a line.
 */
public final class FrostColors {
   private FrostColors() {
   }

   /** blends the biome temperature like a colour: red channel = (temperature + 1) * 100 */
   public static final ColorResolver TEMPERATURE = (biome, x, z) -> {
      int t = Math.max(0, Math.min(255, Math.round((biome.getBaseTemperature() + 1.0F) * 100.0F)));
      return t << 16 | t << 8 | t;
   };

   static volatile boolean registered;

   /**
    * 0 no frost .. 1 a white-frosted crown, at a block in the world being drawn: where it is cold enough to snow - the
    * biome at that height and, with seasons on, in winter (vanilla's own snow test, season and all). Three heights are
    * asked, so a crown at the snow line is frosted only partly and the frost fades down a mountainside.
    */
   public static float frost(BlockAndTintGetter level, BlockPos pos) {
      if (pos == null) {
         return 0.0F;
      }
      try {
         net.minecraft.client.multiplayer.ClientLevel world = net.minecraft.client.Minecraft.getInstance().level;
         if (world == null) {
            return 0.0F;
         }
         int cold = 0;
         for (int dy : new int[]{-6, 0, 6}) {
            BlockPos p = pos.offset(0, dy, 0);
            if (world.getBiome(p).value().coldEnoughToSnow(p)) {
               cold++;
            }
         }
         return cold / 3.0F;
      } catch (RuntimeException e) {
         return 0.0F;
      }
   }

   /** frost for a temperature: none above 0.2, full at or below -0.05 */
   public static float frostAt(float temperature) {
      float f = (0.2F - temperature) / 0.25F;
      f = Math.max(0.0F, Math.min(1.0F, f));
      return f * f * (3.0F - 2.0F * f);
   }

   /** a leaves colour handler with the frost on top */
   record Frosted(BlockColor inner) implements BlockColor {
      @Override
      public int getColor(BlockState state, BlockAndTintGetter level, BlockPos pos, int tint) {
         int base = this.inner == null ? -1 : this.inner.getColor(state, level, pos, tint);
         if (level == null || pos == null) {
            return base;
         }
         float f = frost(level, pos);
         if (f <= 0.0F) {
            return base;
         }
         float r = base == -1 ? 1 : (base >> 16 & 255) / 255F, g = base == -1 ? 1 : (base >> 8 & 255) / 255F, b = base == -1 ? 1 : (base & 255) / 255F;
         float k = 0.62F * f;
         // a dusting: the needles stay readable, the crown turns cold and pale
         return SeasonalColors.pack(r + (0.86F - r) * k, g + (0.90F - g) * k, b + (0.95F - b) * k);
      }
   }
}
