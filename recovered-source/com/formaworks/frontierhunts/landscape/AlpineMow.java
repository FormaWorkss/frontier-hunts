package com.formaworks.frontierhunts.landscape;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent.Load;
import net.neoforged.neoforge.event.level.LevelEvent.Unload;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class AlpineMow {
   private static final int CHUNKS_PER_TICK = 2;
   private static final Map<ServerLevel, AlpineMow.State> LEVELS = new WeakHashMap<>();

   private AlpineMow() {
   }

   private static AlpineGenerator generator(ServerLevel var0) {
      return var0.getChunkSource().getGenerator() instanceof AlpineGenerator var1 ? var1 : null;
   }

   @SubscribeEvent
   public static void loaded(Load var0) {
      if (var0.getLevel() instanceof ServerLevel var1 && generator(var1) != null) {
         long var10 = var0.getChunk().getPos().toLong();
         AlpineMow.State var4;
         synchronized (LEVELS) {
            var4 = LEVELS.computeIfAbsent(var1, var0x -> new AlpineMow.State());
         }

         synchronized (var4) {
            if (!var4.seen.add(var10)) {
               return;
            }

            var4.queue.enqueue(var10);
            return;
         }
      }
   }

   @SubscribeEvent
   public static void unloaded(Unload var0) {
      if (var0.getLevel() instanceof ServerLevel var1) {
         synchronized (LEVELS) {
            LEVELS.remove(var1);
         }
      }
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      if (var0.getLevel() instanceof ServerLevel var1) {
         AlpineMow.State var11;
         synchronized (LEVELS) {
            var11 = LEVELS.get(var1);
         }

         if (var11 != null) {
            AlpineGenerator var12 = generator(var1);
            if (var12 != null) {
               for (int var4 = 0; var4 < 2; var4++) {
                  long var5;
                  synchronized (var11) {
                     if (var11.queue.isEmpty()) {
                        return;
                     }

                     var5 = var11.queue.dequeueLong();
                  }

                  mow(var1, var12.layout(), new ChunkPos(var5));
               }
            }
         }
      }
   }

   private static void mow(ServerLevel var0, AlpineLayout var1, ChunkPos var2) {
      if (var0.hasChunk(var2.x, var2.z)) {
         LevelChunk var3 = var0.getChunk(var2.x, var2.z);
         Block var4 = (Block)AlpineRegistration.PASTURE.get();
         Block var5 = (Block)AlpineRegistration.TURF.get();
         boolean[] var6 = new boolean[16];
         boolean[] var7 = new boolean[16];

         for (int var8 = 0; var8 < 4; var8++) {
            for (int var9 = 0; var9 < 4; var9++) {
               int var10 = var2.getMinBlockX() + var8 * 4 + 2;
               int var11 = var2.getMinBlockZ() + var9 * 4 + 2;
               AlpineLayout.Sample var12 = var1.sample((double)var10, (double)var11);
               var6[var8 * 4 + var9] = AlpineVegetation.openField(var1, var10, var11, var12);
               int var13 = var3.getHeight(Types.WORLD_SURFACE, var8 * 4 + 2, var9 * 4 + 2);
               var7[var8 * 4 + var9] = var6[var8 * 4 + var9] && underTrees(var0, var10, var13, var11);
            }
         }

         MutableBlockPos var25 = new MutableBlockPos();

         for (int var26 = 0; var26 < 16; var26++) {
            for (int var27 = 0; var27 < 16; var27++) {
               int var28 = var2.getMinBlockX() + var26;
               int var29 = var2.getMinBlockZ() + var27;
               int var30 = (var26 >> 2) * 4 + (var27 >> 2);
               boolean var14 = var6[var30]
                  && !var7[var30]
                  && var3.getHeight(Types.MOTION_BLOCKING, var26, var27) == var3.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var26, var27);
               int var15 = var3.getHeight(Types.WORLD_SURFACE, var26, var27);
               var25.set(var28, var15, var29);
               BlockState var16 = var3.getBlockState(var25);
               if (!var16.is(var4) && !(var16.getBlock() instanceof DoublePlantBlock)) {
                  if (!var16.getCollisionShape(var0, var25).isEmpty() && !(var16.getBlock() instanceof AlpineTurf) || var16.getBlock() instanceof AlpineTurf) {
                     var25.setY(var15 + 1);
                  }
               } else {
                  var25.setY(var15 - 1);
                  if (!var3.getBlockState(var25).is(var4) && !(var3.getBlockState(var25).getBlock() instanceof DoublePlantBlock)) {
                     var25.setY(var15);
                  }
               }

               int var17 = var25.getY();
               BlockState var18 = var3.getBlockState(var25);
               BlockState var19 = var0.getBlockState(new BlockPos(var28, var17 - 1, var29));
               boolean var20 = var19.getBlock() instanceof AlpineTurf;
               boolean var21 = var19.is(Blocks.GRASS_BLOCK);
               if (var20 || var21) {
                  BlockPos var22 = new BlockPos(var28, var17, var29);
                  if (var18.is(var4)) {
                     if (!var14) {
                        var0.setBlock(var22, var20 ? Blocks.AIR.defaultBlockState() : AlpineVegetation.plainGrassState(var1, var28, var29), 2);
                     }
                  } else {
                     boolean var23 = var18.is(Blocks.SHORT_GRASS) || var18.is(Blocks.FERN);
                     if (var21 && (var18.isAir() || var23 || var18.is(BlockTags.SMALL_FLOWERS) || var18.is(Blocks.MOSS_CARPET))) {
                        int var24 = neighbourTurf(var0, var28, var17, var29, var5, var18.isAir() ? 3 : 2);
                        if (var24 > 0 && var0.getBlockState(var22.above()).isAir()) {
                           var0.setBlock(var22, (BlockState)var5.defaultBlockState().setValue(AlpineTurf.LAYERS, var24), 2);
                           placeSward(var0, var1, var22.above(), var28, var29, var24, var14);
                           continue;
                        }
                     }

                     if (!var20 || !var23 && !var18.isAir()) {
                        if (var21 && var23 && var14) {
                           placeSward(var0, var1, var22, var28, var29, 8, true);
                        }
                     } else {
                        int var31 = (Integer)var19.getValue(AlpineTurf.LAYERS);
                        placeSward(var0, var1, var22, var28, var29, var31, var14);
                     }
                  }
               }
            }
         }
      }
   }

   private static void placeSward(ServerLevel var0, AlpineLayout var1, BlockPos var2, int var3, int var4, int var5, boolean var6) {
      Block var7 = (Block)AlpineRegistration.PASTURE.get();
      int var8 = var5 >= 8 ? 0 : 8 - var5;
      double var9 = var1.variation(var3, var4, 8541L);
      if (var6) {
         int var11 = var9 < 0.22 ? 0 : (var9 < 0.62 ? 1 : 2);
         if (var11 > 1 && !var0.getBlockState(var2.above()).isAir()) {
            var11 = 1;
         }

         var0.setBlock(var2, (BlockState)((BlockState)var7.defaultBlockState().setValue(AlpinePasture.HEIGHT, var11)).setValue(AlpinePasture.SINK, var8), 2);
      } else if (var8 > 0) {
         var0.setBlock(
            var2,
            var9 < 0.5
               ? (BlockState)((BlockState)var7.defaultBlockState().setValue(AlpinePasture.HEIGHT, 0)).setValue(AlpinePasture.SINK, var8)
               : Blocks.AIR.defaultBlockState(),
            2
         );
      }
   }

   private static int neighbourTurf(ServerLevel var0, int var1, int var2, int var3, Block var4, int var5) {
      int var6 = 0;
      int var7 = 0;
      int[][] var8 = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

      for (int[] var12 : var8) {
         BlockPos var13 = new BlockPos(var1 + var12[0], var2, var3 + var12[1]);
         if (var0.hasChunkAt(var13)) {
            BlockState var14 = var0.getBlockState(var13);
            if (var14.is(var4)) {
               var6++;
               var7 += var14.getValue(AlpineTurf.LAYERS);
            }
         }
      }

      return var6 >= var5 ? Math.max(1, Math.min(7, Math.round((float)var7 / (float)var6))) : 0;
   }

   private static boolean underTrees(ServerLevel var0, int var1, int var2, int var3) {
      int var4 = 0;

      for (int var5 = 0; var5 < 8; var5++) {
         int var6 = var1 + (int)Math.round(Math.cos((double)var5 * Math.PI / 4.0) * 5.0);
         int var7 = var3 + (int)Math.round(Math.sin((double)var5 * Math.PI / 4.0) * 5.0);
         if (var0.hasChunkAt(new BlockPos(var6, var2, var7))) {
            int var8 = var0.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var6, var7);

            for (int var9 = 0; var9 <= 18; var9++) {
               BlockState var10 = var0.getBlockState(new BlockPos(var6, Math.max(var8 - 1, var2) + var9, var7));
               if (var10.is(BlockTags.LOGS) || var10.is(BlockTags.LEAVES)) {
                  var4++;
                  break;
               }
            }

            if (var4 >= 2) {
               return true;
            }
         }
      }

      return false;
   }

   private static final class State {
      final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
      final LongOpenHashSet seen = new LongOpenHashSet();
   }
}
