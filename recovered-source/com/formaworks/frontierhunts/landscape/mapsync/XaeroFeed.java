package com.formaworks.frontierhunts.landscape.mapsync;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import xaero.lib.client.config.ClientConfigManager;
import xaero.map.MapProcessor;
import xaero.map.MapWriter;
import xaero.map.WorldMap;
import xaero.map.WorldMapSession;
import xaero.map.biome.BiomeGetter;
import xaero.map.biome.BlockTintProvider;
import xaero.map.cache.BlockStateShortShapeCache;
import xaero.map.common.config.option.WorldMapProfiledConfigOptions;
import xaero.map.region.LeveledRegion;
import xaero.map.region.MapBlock;
import xaero.map.region.MapLayer;
import xaero.map.region.MapRegion;
import xaero.map.region.MapTile;
import xaero.map.region.MapTileChunk;
import xaero.map.region.MapUpdateFastConfig;
import xaero.map.region.OverlayManager;

final class XaeroFeed {
   static final int WROTE = 0;
   static final int LOADING = 1;
   static final int BUSY = 2;
   static final int UNAVAILABLE = 3;
   private static final int SURFACE = Integer.MAX_VALUE;
   private static MapBlock spare = new MapBlock();
   private static final MutableBlockPos POS3 = new MutableBlockPos();
   private static final Set<Long> REQUESTED = new HashSet<>();
   private static long lastRequest;
   private static Field BIOME_FIELD;

   private XaeroFeed() {
   }

   private static MapProcessor processor() {
      WorldMapSession var0 = WorldMapSession.getCurrentSession();
      return var0 == null ? null : var0.getMapProcessor();
   }

   static String sessionKey(ClientLevel var0) {
      MapProcessor var1 = processor();
      return var1 != null && writable(var1, var0) ? var1.getCurrentWorldId() + "|" + var1.getCurrentDimId() + "|" + var1.getCurrentMWId() : null;
   }

   private static boolean writable(MapProcessor var0, ClientLevel var1) {
      if (WorldMap.crashHandler.getCrashedBy() != null) {
         return false;
      } else if (var0.getWorld() == null || var0.getWorld() != var1 || var0.mainWorld != var1) {
         return false;
      } else if (var0.isWritingPaused() || var0.isWaitingForWorldUpdate()) {
         return false;
      } else if (!var0.getMapSaveLoad().isRegionDetectionComplete() || !var0.isCurrentMultiworldWritable()) {
         return false;
      } else if (var0.isCurrentMapLocked() || var0.getMapWorld() == null || var0.getMapWorld().isCacheOnlyMode()) {
         return false;
      } else if (var0.getCurrentWorldId() == null || var0.ignoreWorld(var1)) {
         return false;
      } else if (var0.getMapWorld().getCurrentDimension() == null) {
         return false;
      } else if (var0.getMapWorld().getCurrentDimension().isUsingWorldSave()) {
         return false;
      } else {
         return !WorldMap.INSTANCE.getConfigs().getClientConfigManager().getEffective(WorldMapProfiledConfigOptions.LOAD_NEW_CHUNKS)
            ? false
            : var1.dimension() == var0.getMapWorld().getCurrentDimensionId();
      }
   }

   static boolean regionKnown(int var0, int var1) {
      MapProcessor var2 = processor();
      if (var2 != null && var2.getMapWorld() != null && var2.getMapWorld().getCurrentDimension() != null) {
         MapLayer var3 = var2.getMapWorld().getCurrentDimension().getLayeredMapRegions().getLayer(Integer.MAX_VALUE);
         return var3 != null && (var3.getCompleteRegionDetection(var0, var1) != null || var3.getMapRegions().get(var0, var1, 0) != null);
      } else {
         return false;
      }
   }

   static void flush(int var0, int var1) {
      MapProcessor var2 = processor();
      if (var2 != null) {
         MapRegion var3 = var2.getLeafMapRegion(Integer.MAX_VALUE, var0, var1, false);
         if (var3 != null) {
            synchronized (var3) {
               if (var3.getLoadState() == 2 && var3.isBeingWritten()) {
                  var3.setLastSaveTime(System.currentTimeMillis() - 60000L);
               }
            }
         }
      }
   }

   static int write(ClientLevel var0, int var1, int var2, List<MapColumns.Decoded> var3, long var4) {
      MapProcessor var6 = processor();
      if (var6 == null) {
         return 3;
      } else {
         synchronized (var6.renderThreadPauseSync) {
            if (!writable(var6, var0)) {
               return 3;
            } else {
               MapRegion var8 = var6.getLeafMapRegion(Integer.MAX_VALUE, var1, var2, true);
               if (var8 == null) {
                  return 2;
               } else {
                  long var9 = MapSyncPayloads.key(var1, var2);
                  LeveledRegion var11 = var6.getMapSaveLoad().getNextToLoadByViewing();
                  boolean var12 = var11 != null && !var11.shouldAllowAnotherRegionToLoad();
                  synchronized (var8) {
                     if (var8.getLoadState() != 2) {
                        if (var8.isResting() && var8.canRequestReload_unsynced() && !REQUESTED.contains(var9)) {
                           long var23 = System.currentTimeMillis();
                           if (!var12 && var23 - lastRequest > 150L) {
                              var8.setBeingWritten(true);
                              var6.getMapSaveLoad().requestLoad(var8, "frontier preload", false);
                              REQUESTED.add(var9);
                              lastRequest = var23;
                           }
                        } else if (REQUESTED.contains(var9) && var8.canRequestReload_unsynced() && var8.getLoadState() != 1) {
                           REQUESTED.remove(var9);
                        }

                        return 1;
                     }

                     REQUESTED.remove(var9);
                  }

                  installBiomes(var6);
                  boolean var13 = false;

                  while (!var3.isEmpty() && System.nanoTime() < var4) {
                     MapColumns.Decoded var14 = (MapColumns.Decoded)var3.get(0);
                     int var15 = var14.chunkX >> 2;
                     int var16 = var14.chunkZ >> 2;
                     ArrayList var17 = new ArrayList(16);
                     Iterator var18 = var3.iterator();

                     while (var18.hasNext()) {
                        MapColumns.Decoded var19 = (MapColumns.Decoded)var18.next();
                        if (var19.chunkX >> 2 == var15 && var19.chunkZ >> 2 == var16) {
                           var17.add(var19);
                           var18.remove();
                        }
                     }

                     int var24 = var17.size();
                     int var25 = writeTileChunk(var6, var0, var8, var15, var16, var17, var4);
                     var3.addAll(var17);
                     if (var17.size() < var24) {
                        var13 = true;
                     }

                     if (var25 != 0) {
                        return var13 ? 0 : var25;
                     }
                  }

                  return var13 ? 0 : 2;
               }
            }
         }
      }
   }

   private static int writeTileChunk(MapProcessor var0, ClientLevel var1, MapRegion var2, int var3, int var4, List<MapColumns.Decoded> var5, long var6) {
      int var8 = var3 & 7;
      int var9 = var4 & 7;
      MapWriter var10 = var0.getMapWriter();
      BlockStateShortShapeCache var11 = var0.getBlockStateShortShapeCache();
      MapUpdateFastConfig var12 = new MapUpdateFastConfig();
      ClientConfigManager var13 = WorldMap.INSTANCE.getConfigs().getClientConfigManager();
      boolean var14 = (Boolean)var13.getEffective(WorldMapProfiledConfigOptions.FLOWERS);
      int var15 = (Integer)var13.getEffective(WorldMapProfiledConfigOptions.CAVE_MODE_DEPTH);
      int var16 = var1.getMinBuildHeight();
      int var17 = var1.getMaxBuildHeight();
      Registry var18 = var0.worldBiomeRegistry;
      Registry var19 = var0.getWorldBlockRegistry();
      boolean var21 = false;
      synchronized (var2.writerThreadPauseSync) {
         if (var2.isWritingPaused()) {
            return 2;
         } else {
            MapTileChunk var20;
            synchronized (var2) {
               if (var2.getLoadState() != 2) {
                  return 1;
               }

               var2.registerVisit();
               if (!var2.isResting()) {
                  return 2;
               }

               var2.setBeingWritten(true);
               var20 = var2.getChunk(var8, var9);
               if (var20 == null) {
                  var20 = new MapTileChunk(var2, var3, var4);
                  var2.setChunk(var8, var9, var20);
                  var20.setLoadState((byte)2);
                  var2.setAllCachePrepared(false);
                  var21 = true;
               }

               if (!var2.isNormalMapData()) {
                  var2.getDim().getLayeredMapRegions().applyToEachLoadedLayer((var2x, var3x) -> {
                     if (var2x != var2.getCaveLayer()) {
                        MapRegion var4x = var0.getLeafMapRegion(var2x, var2.getRegionX(), var2.getRegionZ(), true);
                        if (var4x != null) {
                           var4x.setOutdatedWithOtherLayers(true);
                           var4x.setHasHadTerrain();
                        }
                     }
                  });
               }
            }

            if (var20.getLoadState() == 2 && !var20.getLeafTexture().shouldUpload()) {
               MapTileChunk var23 = var20.getNeighbourTileChunk(0, -1, var0, false);
               MapTileChunk var24 = var20.getNeighbourTileChunk(-1, -1, var0, false);
               MapTileChunk var25 = var20.getNeighbourTileChunk(-1, 0, var0, false);
               var5.sort(
                  (var0x, var1x) -> (var0x.chunkX & 3) != (var1x.chunkX & 3)
                        ? Integer.compare(var0x.chunkX & 3, var1x.chunkX & 3)
                        : Integer.compare(var0x.chunkZ & 3, var1x.chunkZ & 3)
               );
               MapTileChunk var26 = var9 < 7 ? var2.getChunk(var8, var9 + 1) : null;
               MapTileChunk var27 = var8 < 7 ? var2.getChunk(var8 + 1, var9) : null;
               boolean var28 = false;
               boolean var29 = false;
               Iterator var30 = var5.iterator();

               while (var30.hasNext() && System.nanoTime() < var6) {
                  MapColumns.Decoded var31 = (MapColumns.Decoded)var30.next();
                  var30.remove();
                  int var32 = var31.chunkX & 3;
                  int var33 = var31.chunkZ & 3;
                  if (!var1.getChunkSource().hasChunk(var31.chunkX, var31.chunkZ)) {
                     LevelChunk var34 = rebuild(var1, var31);
                     MapTile var35 = var20.getTile(var32, var33);
                     if (var35 == null) {
                        var35 = var0.getTilePool().get(var0.getCurrentDimension(), var31.chunkX, var31.chunkZ);
                        var20.setChanged(true);
                     }

                     int var36 = var10.getSectionBasedHeight(var34, 64);
                     XaeroFeed.FeedBiomes.active = var31;

                     try {
                        for (int var37 = 0; var37 < 16; var37++) {
                           for (int var38 = 0; var38 < 16; var38++) {
                              int var39 = var34.getHeight(Types.WORLD_SURFACE, var37, var38);
                              int var40 = var39 >= var16 ? var39 : var36;
                              if (var40 >= var17) {
                                 var40 = var17 - 1;
                              }

                              MapBlock var41 = var35.isLoaded() ? var35.getBlock(var37, var38) : null;
                              MapBlock var42 = spare;
                              var10.loadPixel(
                                 var1,
                                 var19,
                                 var42,
                                 var41,
                                 var34,
                                 var37,
                                 var38,
                                 var40,
                                 var16,
                                 false,
                                 false,
                                 var39,
                                 var35.wasWrittenOnce(),
                                 false,
                                 var18,
                                 var14,
                                 var16,
                                 POS3
                              );
                              var42.fixHeightType(var37, var38, var35, var20, var23, var24, var25, var42.getEffectiveHeight(var11, var12), true, var11, var12);
                              boolean var43 = var42.equalsSlopesExcluded(var41);
                              if (!var42.equals(var41, var43)) {
                                 var35.setBlock(var37, var38, var42);
                                 spare = var41 != null ? var41 : new MapBlock();
                                 if (!var43) {
                                    var20.setChanged(true);
                                 }
                              }
                           }
                        }
                     } finally {
                        XaeroFeed.FeedBiomes.active = null;
                     }

                     var35.setWorldInterpretationVersion(1);
                     if (var35.getWrittenCaveStart() != Integer.MAX_VALUE) {
                        var20.setChanged(true);
                     }

                     var35.setWrittenCave(Integer.MAX_VALUE, var15);
                     var20.setTile(var32, var33, var35, var11);
                     var35.setWrittenOnce(true);
                     var35.setLoaded(true);
                     MapTile var54 = var33 < 3 ? var20.getTile(var32, var33 + 1) : (var26 != null ? var26.getTile(var32, 0) : null);
                     if (var54 != null && var54.isLoaded()) {
                        for (int var55 = 0; var55 < 16; var55++) {
                           var54.getBlock(var55, 0).setSlopeUnknown(true);
                        }

                        if (var33 == 3) {
                           var28 = true;
                        }
                     }

                     MapTile var56 = var32 < 3 ? var20.getTile(var32 + 1, var33) : (var27 != null ? var27.getTile(0, var33) : null);
                     if (var56 != null && var56.isLoaded()) {
                        for (int var57 = 0; var57 < 16; var57++) {
                           var56.getBlock(0, var57).setSlopeUnknown(true);
                        }

                        if (var32 == 3) {
                           var29 = true;
                        }
                     }
                  }
               }

               if (var21) {
                  if (var20.includeInSave()) {
                     var20.setHasHadTerrain();
                  }

                  var0.getMapRegionHighlightsPreparer().prepare(var2, var8, var9, false);
                  if (!var20.includeInSave() && !var20.hasHighlightsIfUndiscovered()) {
                     var2.setChunk(var8, var9, null);
                     return 0;
                  }
               }

               if (var20.wasChanged()) {
                  BlockTintProvider var52 = var0.getWorldBlockTintProvider();
                  OverlayManager var53 = var0.getOverlayManager();
                  var20.updateBuffers(var0, var52, var53, false, var11, var12);
                  var20.setChanged(false);
               }

               if (var28 && var26 != null && var26.getLoadState() == 2) {
                  var26.setToUpdateBuffers(true);
               }

               if (var29 && var27 != null && var27.getLoadState() == 2) {
                  var27.setToUpdateBuffers(true);
               }

               return 0;
            } else {
               return 2;
            }
         }
      }
   }

   private static LevelChunk rebuild(ClientLevel var0, MapColumns.Decoded var1) {
      LevelChunk var2 = new LevelChunk(var0, new ChunkPos(var1.chunkX, var1.chunkZ));
      LevelChunkSection[] var3 = var2.getSections();
      int var4 = var0.getMinBuildHeight();
      int var5 = var0.getMaxBuildHeight();

      for (int var6 = 0; var6 < 256; var6++) {
         int var7 = var6 & 15;
         int var8 = var6 >> 4;
         byte var9 = var1.layers[var6];

         for (int var10 = 0; var10 < var9; var10++) {
            BlockState var11 = var1.palette[var1.state[var6][var10]];
            short var12 = var1.top[var6][var10];

            for (int var13 = 0; var13 < var1.run[var6][var10]; var13++) {
               int var14 = var12 - var13;
               if (var14 >= var4 && var14 < var5) {
                  int var15 = var14 - var4 >> 4;
                  if (var15 >= 0 && var15 < var3.length) {
                     var3[var15].setBlockState(var7, var14 & 15, var8, var11, false);
                  }
               }
            }
         }
      }

      Heightmap.primeHeightmaps(var2, EnumSet.of(Types.WORLD_SURFACE));
      return var2;
   }

   private static void installBiomes(MapProcessor var0) {
      MapWriter var1 = var0.getMapWriter();

      try {
         if (BIOME_FIELD == null) {
            BIOME_FIELD = MapWriter.class.getDeclaredField("biomeGetter");
            BIOME_FIELD.setAccessible(true);
         }

         Object var2 = BIOME_FIELD.get(var1);
         if (!(var2 instanceof XaeroFeed.FeedBiomes)) {
            BIOME_FIELD.set(var1, new XaeroFeed.FeedBiomes((BiomeGetter)var2));
         }
      } catch (ReflectiveOperationException var3) {
         throw new IllegalStateException("Xaero's World Map writer has changed", var3);
      }
   }

   static final class FeedBiomes extends BiomeGetter {
      static MapColumns.Decoded active;
      private final BiomeGetter original;

      FeedBiomes(BiomeGetter var1) {
         this.original = var1;
      }

      public ResourceKey<Biome> getBiome(Level var1, BlockPos var2, Registry<Biome> var3) {
         MapColumns.Decoded var4 = active;
         if (var4 != null && var2.getX() >> 4 == var4.chunkX && var2.getZ() >> 4 == var4.chunkZ && Minecraft.getInstance().isSameThread()) {
            Optional var5 = var1.registryAccess().registryOrThrow(Registries.BIOME).getHolder(var4.biomeAt(var2.getX(), var2.getZ()));
            if (var5.isPresent()) {
               Optional var6 = ((Reference)var5.get()).unwrapKey();
               if (var6.isPresent()) {
                  return (ResourceKey<Biome>)var6.get();
               }
            }
         }

         return this.original == null ? super.getBiome(var1, var2, var3) : this.original.getBiome(var1, var2, var3);
      }
   }
}
