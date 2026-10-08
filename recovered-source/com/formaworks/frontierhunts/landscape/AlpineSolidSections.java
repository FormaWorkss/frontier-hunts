package com.formaworks.frontierhunts.landscape;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainer.Strategy;

public final class AlpineSolidSections {
   private AlpineSolidSections() {
   }

   public static boolean[] initialize(LevelChunkSection[] var0, int var1, AlpineTile var2) {
      int var3 = Integer.MAX_VALUE;

      for (int var4 = 0; var4 < 16; var4++) {
         for (int var5 = 0; var5 < 16; var5++) {
            int var6 = Math.min(128, Math.max(5, (int)Math.ceil(var2.slope(var4, var5) * 8.0) + 2));
            AlpineLayout.Sample var7 = var2.sample(var4, var5);
            if (var7.scenic()) {
               var6 = Math.max(var6, 74);
            }

            if (AlpineFalls.hasCavity(var7.cavity())) {
               var3 = Math.min(var3, AlpineFalls.cavityBottom(var7.cavity()) - 1);
            }

            var3 = Math.min(var3, var7.floor() - var6 - 1);
         }
      }

      boolean[] var8 = new boolean[var0.length];

      for (int var9 = 0; var9 < var0.length; var9++) {
         int var10 = (var1 + var9) * 16;
         if (var10 >= 64 && var10 + 15 <= var3 && var0[var9].hasOnlyAir()) {
            PalettedContainer var11 = new PalettedContainer(Block.BLOCK_STATE_REGISTRY, Blocks.STONE.defaultBlockState(), Strategy.SECTION_STATES);
            var0[var9] = new LevelChunkSection(var11, var0[var9].getBiomes());
            var8[var9] = true;
         }
      }

      return var8;
   }
}
