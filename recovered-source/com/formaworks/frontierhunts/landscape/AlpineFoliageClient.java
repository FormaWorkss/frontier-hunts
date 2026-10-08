package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.HuntConfig;
import java.util.ArrayList;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT},
   bus = Bus.MOD
)
public final class AlpineFoliageClient {
   private static final String[] LEAVES = new String[]{
      "spruce_boughs",
      "fir_needles",
      "pine_needles",
      "aspen_leaves",
      "birch_leaves",
      "maple_leaves",
      "willow_leaves",
      "golden_aspen_leaves",
      "autumn_maple_leaves",
      "blue_spruce_boughs",
      "larch_needles"
   };

   @SubscribeEvent
   public static void colors(Block var0) {
      var0.register(
         (var0x, var1x, var2, var3) -> AlpineGrassModels.style() == HuntConfig.GrassStyle.VANILLA
               ? AlpineLook.biome(var1x != null && var2 != null ? BiomeColors.getAverageGrassColor(var1x, var2) : GrassColor.getDefaultColor())
               : tint(AlpineLook.biome(var1x != null && var2 != null ? BiomeColors.getAverageGrassColor(var1x, var2) : 7973195), var3),
         new net.minecraft.world.level.block.Block[]{(net.minecraft.world.level.block.Block)AlpineRegistration.PASTURE.get()}
      );
      var0.register(
         (var0x, var1x, var2, var3) -> AlpineLook.biome(
               var1x != null && var2 != null ? BiomeColors.getAverageGrassColor(var1x, var2) : GrassColor.getDefaultColor()
            ),
         new net.minecraft.world.level.block.Block[]{(net.minecraft.world.level.block.Block)AlpineRegistration.TURF.get()}
      );
      var0.register(
         (var0x, var1x, var2, var3) -> tint(AlpineLook.biome(var1x != null && var2 != null ? BiomeColors.getAverageFoliageColor(var1x, var2) : 6523715), var3),
         new net.minecraft.world.level.block.Block[]{(net.minecraft.world.level.block.Block)AlpineRegistration.OVERGROWTH.get()}
      );
      ArrayList var1 = new ArrayList();

      for (String var5 : LEAVES) {
         net.minecraft.world.level.block.Block var6 = AlpineRegistration.prop(var5);
         if (var6 != Blocks.AIR) {
            var1.add(var6);
         }
      }

      var0.register(
         (var0x, var1x, var2, var3) -> AlpineLook.times(leafTint(var2), AlpineLook.paint()), var1.toArray(new net.minecraft.world.level.block.Block[0])
      );
      ArrayList var8 = new ArrayList();

      for (String var12 : AlpineRegistration.THICKET_IDS) {
         net.minecraft.world.level.block.Block var7 = AlpineRegistration.prop(var12);
         if (var7 != Blocks.AIR) {
            var8.add(var7);
         }
      }

      var0.register((var0x, var1x, var2, var3) -> AlpineLook.paint(), var8.toArray(new net.minecraft.world.level.block.Block[0]));
      var0.register(
         (var0x, var1x, var2, var3) -> AlpineLook.biome(
               var1x != null && var2 != null ? BiomeColors.getAverageGrassColor(var1x, var2) : GrassColor.getDefaultColor()
            ),
         new net.minecraft.world.level.block.Block[]{Blocks.GRASS_BLOCK, Blocks.SHORT_GRASS, Blocks.FERN, Blocks.POTTED_FERN, Blocks.SUGAR_CANE}
      );
      var0.register(
         (var0x, var1x, var2, var3) -> AlpineLook.biome(
               var1x != null && var2 != null
                  ? BiomeColors.getAverageGrassColor(var1x, var0x.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER ? var2.below() : var2)
                  : GrassColor.getDefaultColor()
            ),
         new net.minecraft.world.level.block.Block[]{Blocks.TALL_GRASS, Blocks.LARGE_FERN}
      );
      var0.register(
         (var0x, var1x, var2, var3) -> AlpineLook.biome(
               var1x != null && var2 != null ? BiomeColors.getAverageFoliageColor(var1x, var2) : FoliageColor.getDefaultColor()
            ),
         new net.minecraft.world.level.block.Block[]{Blocks.VINE}
      );
   }

   static int leafTint(BlockPos var0) {
      if (var0 == null) {
         return 15791340;
      } else {
         int var1 = var0.getX();
         int var2 = var0.getY();
         int var3 = var0.getZ();
         double var4 = lattice(var1, var2, var3, 5, 4, 20903);
         double var6 = lattice(var1 + 913, var2, var3 - 377, 11, 9, 11325);
         double var8 = (double)hash(var1, var2, var3, 32586) / 65535.0;
         double var10 = 0.76 + 0.22 * var4 + (var8 - 0.5) * 0.05;
         double var12 = (var6 - 0.5) * 0.11 - 0.015;
         int var14 = (int)Math.min(255.0, 255.0 * var10 * (1.0 + var12));
         int var15 = (int)Math.min(255.0, 255.0 * var10 * (1.0 + var12 * 0.3));
         int var16 = (int)Math.min(255.0, 255.0 * var10 * (1.0 - var12));
         return var14 << 16 | var15 << 8 | var16;
      }
   }

   private static int hash(int var0, int var1, int var2, int var3) {
      int var4 = var0 * 73856093 ^ var1 * 19349663 ^ var2 * 83492791 ^ var3;
      var4 ^= var4 >>> 13;
      var4 *= 1540483477;
      var4 ^= var4 >>> 15;
      return var4 & 65535;
   }

   private static double lattice(int var0, int var1, int var2, int var3, int var4, int var5) {
      int var6 = Math.floorDiv(var0, var3);
      int var7 = Math.floorDiv(var1, var4);
      int var8 = Math.floorDiv(var2, var3);
      double var9 = fade((double)(var0 - var6 * var3) / (double)var3);
      double var11 = fade((double)(var1 - var7 * var4) / (double)var4);
      double var13 = fade((double)(var2 - var8 * var3) / (double)var3);
      double var15 = 0.0;

      for (int var17 = 0; var17 < 8; var17++) {
         int var18 = var17 & 1;
         int var19 = var17 >> 1 & 1;
         int var20 = var17 >> 2 & 1;
         double var21 = (var18 == 1 ? var9 : 1.0 - var9) * (var19 == 1 ? var11 : 1.0 - var11) * (var20 == 1 ? var13 : 1.0 - var13);
         var15 += var21 * (double)hash(var6 + var18, var7 + var19, var8 + var20, var5) / 65535.0;
      }

      return var15;
   }

   private static double fade(double var0) {
      return var0 * var0 * (3.0 - 2.0 * var0);
   }

   private static int tint(int var0, int var1) {
      if (var1 != 1) {
         return var0;
      } else {
         int var2 = ((var0 >> 16 & 0xFF) + 510) / 3;
         int var3 = ((var0 >> 8 & 0xFF) + 510) / 3;
         int var4 = ((var0 & 0xFF) + 510) / 3;
         return var2 << 16 | var3 << 8 | var4;
      }
   }
}
