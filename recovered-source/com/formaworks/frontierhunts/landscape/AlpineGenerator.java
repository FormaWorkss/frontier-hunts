package com.formaworks.frontierhunts.landscape;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.GenerationStep.Carving;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public final class AlpineGenerator extends ChunkGenerator {
   private static final boolean REVIEW = Boolean.getBoolean("frontier.alpineReview");
   public static final MapCodec<AlpineGenerator> CODEC = RecordCodecBuilder.mapCodec(
      var0 -> var0.group(
               AlpineBiomes.CODEC.fieldOf("biome_source").forGetter(var0x -> var0x.alpineBiomes),
               Codec.LONG.optionalFieldOf("terrain_seed", 0L).forGetter(var0x -> var0x.layout.seed()),
               Codec.intRange(1, 21).optionalFieldOf("terrain_version", 1).forGetter(var0x -> var0x.layout.version()),
               // [1.1.9] snow country below the big massifs: only worlds created with it (the presets turn it on)
               Codec.BOOL.optionalFieldOf("snow_country", false).forGetter(var0x -> var0x.snowCountry),
               net.minecraft.world.level.biome.Biome.CODEC.optionalFieldOf("snow_biome").forGetter(var0x -> var0x.snowBiome),
               // [1.2.0] the shape of snow country a world was made with (1.1.9 worlds: 1)
               Codec.intRange(1, 9).optionalFieldOf("snow_version", 1).forGetter(var0x -> var0x.snowVersion)
            )
            .apply(var0, AlpineGenerator::new)
   );
   private static final BlockState WATER = Blocks.WATER.defaultBlockState();
   private final AlpineBiomes alpineBiomes;
   private volatile AlpineLayout layout = new AlpineLayout(0L);
   private final boolean snowCountry;
   private final java.util.Optional<Holder<net.minecraft.world.level.biome.Biome>> snowBiome;
   private final int snowVersion;
   private volatile SnowCountry snow;
   private final AtomicBoolean reportedHeightMismatch = new AtomicBoolean();

   private void checkReviewHeight(LevelHeightAccessor var1) {
      if (REVIEW && var1.getHeight() != 1024 && this.reportedHeightMismatch.compareAndSet(false, true)) {
         LogUtils.getLogger()
            .warn(
               "FRONTIER_ALPINE_HEIGHT caller={} height={} minY={} seed={}",
               new Object[]{
                  Thread.currentThread().getName(), var1.getHeight(), var1.getMinBuildHeight(), this.layout.seed(), new Exception("Review caller trace")
               }
            );
      }
   }

   public AlpineGenerator(AlpineBiomes var1) {
      this(var1, 0L);
   }

   public AlpineGenerator(AlpineBiomes var1, long var2) {
      this(var1, var2, 1);
   }

   public AlpineGenerator(AlpineBiomes var1, long var2, int var4) {
      this(var1, var2, var4, false, java.util.Optional.empty(), 1);
   }

   public AlpineGenerator(AlpineBiomes var1, long var2, int var4, boolean snowCountry,
      java.util.Optional<Holder<net.minecraft.world.level.biome.Biome>> snowBiome, int snowVersion) {
      // [1.2.5] always the wrapping source (it answers /locate biome quickly and correctly); the extra biome only with
      // snow country
      super(new SnowBiomeSource(var1, snowCountry && snowBiome.isPresent() ? java.util.List.of(snowBiome.get()) : java.util.List.of()));
      this.alpineBiomes = var1;
      this.snowCountry = snowCountry;
      this.snowBiome = snowBiome;
      this.snowVersion = snowVersion;
      this.layout = new AlpineLayout(var2, var4);
      var1.initialize(this.layout);
      this.snow = this.makeSnow(this.layout);
      this.shareSnow();
   }

   /** [1.2.5] the biome source lays the same snow over its /locate answers as the chunks get */
   private void shareSnow() {
      if (this.getBiomeSource() instanceof SnowBiomeSource sbs) {
         sbs.snow = this.snow;
      }
   }

   private SnowCountry makeSnow(AlpineLayout l) {
      if (!this.snowCountry) {
         return null;
      }
      try {
         return SnowCountry.of(l, this.getBiomeSource().possibleBiomes(), this.snowVersion);
      } catch (RuntimeException e) {
         LogUtils.getLogger().warn("[FrontierHunts] snow country off: {}", e.toString());
         return null;
      }
   }

   public boolean snowCountry() {
      return this.snowCountry && this.snow != null;
   }

   public AlpineLayout layout() {
      return this.layout;
   }

   protected MapCodec<? extends ChunkGenerator> codec() {
      return CODEC;
   }

   public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> var1, RandomState var2, long var3) {
      if (REVIEW) {
         LogUtils.getLogger()
            .info("FRONTIER_ALPINE_STATE caller={} inputSeed={} storedSeed={}", new Object[]{Thread.currentThread().getName(), var3, this.layout.seed()});
      }

      AlpineLayout var5 = new AlpineLayout(var3, this.layout.version());
      this.layout = var5;
      this.alpineBiomes.initialize(var5);
      this.snow = this.makeSnow(var5);
      this.shareSnow();
      return super.createState(var1, var2, var3);
   }

   public int getMinY() {
      return -64;
   }

   public int getGenDepth() {
      return 1024;
   }

   public int getSeaLevel() {
      return this.layout.version() >= 3 ? 64 : 105;
   }

   public int getSpawnHeight(LevelHeightAccessor var1) {
      return 120;
   }

   public CompletableFuture<ChunkAccess> createBiomes(RandomState var1, Blender var2, StructureManager var3, ChunkAccess var4) {
      ArrayList<Holder<net.minecraft.world.level.biome.Biome>> var5 = new ArrayList<>(16);
      int var6 = var4.getPos().x * 4;
      int var7 = var4.getPos().z * 4;
      SnowCountry snow = this.snow;

      for (int var8 = 0; var8 < 4; var8++) {
         for (int var9 = 0; var9 < 4; var9++) {
            Holder<net.minecraft.world.level.biome.Biome> h = this.alpineBiomes.getNoiseBiome(var6 + var8, 0, var7 + var9, var1.sampler());
            if (snow != null) {
               h = snow.biome(h, var6 + var8, var7 + var9);
            }
            var5.add(h);
         }
      }

      var4.fillBiomesFromNoise((var1x, var2x, var3x, var4x) -> (Holder)var5.get(Math.floorMod(var1x, 4) * 4 + Math.floorMod(var3x, 4)), var1.sampler());
      return CompletableFuture.completedFuture(var4);
   }

   public CompletableFuture<ChunkAccess> fillFromNoise(Blender var1, RandomState var2, StructureManager var3, ChunkAccess var4) {
      AlpineLayout var5 = this.layout;
      return CompletableFuture.supplyAsync(
         Util.wrapThreadWithTaskName(
            "frontier_alpine_columns",
            () -> {
               int var2x = var4.getPos().getMinBlockX();
               int var3x = var4.getPos().getMinBlockZ();
               AlpineTile var4x = new AlpineTile(var5, var2x, var3x);
               boolean[] var5x = AlpineSolidSections.initialize(var4.getSections(), var4.getMinSection(), var4x);
               LevelChunkSection[] var6 = var4.getSections();

               for (LevelChunkSection var10 : var6) {
                  var10.acquire();
               }

               ArrayList<BlockPos> var27 = new ArrayList<>();
               boolean var25 = false /* VF: Semaphore variable */;

               try {
                  var25 = true;

                  for (int var28 = 0; var28 < 16; var28++) {
                     for (int var31 = 0; var31 < 16; var31++) {
                        int var34 = var2x + var28;
                        int var11 = var3x + var31;
                        AlpineLayout.Sample var12 = var4x.sample(var28, var31);
                        double var13 = var4x.slope(var28, var31);
                        AlpineColumn var15 = new AlpineColumn(var5, var12, var13, var34, var11, var4x.lowestNeighbour(var28, var31));
                        int var16 = Math.min(var4.getMaxBuildHeight() - 1, Math.max(var12.floor(), var12.water()));

                        for (int var17 = var4.getMinBuildHeight(); var17 <= var16; var17++) {
                           if (var5x[var4.getSectionIndex(var17)]) {
                              var17 |= 15;
                           } else {
                              BlockState var18 = var15.at(var17);
                              if (!var18.isAir()) {
                                 var6[var4.getSectionIndex(var17)].setBlockState(var28, var17 & 15, var31, var18, false);
                              }
                           }
                        }

                        if (AlpineFalls.spill(var12.site())
                           && var12.water() > var12.floor()
                           && (var5.version() < 18 || AlpineCascade.needsSpill(var5, var34, var11))) {
                           var27.add(new BlockPos(var34, var12.water(), var11));
                        }
                     }
                  }

                  // [gear20] vanilla caves
                  AlpineCaves.carve(var4, var4x, var5, var2);
                  var25 = false;
               } finally {
                  if (var25) {
                     for (LevelChunkSection var23 : var6) {
                        var23.release();
                     }
                  }
               }

               for (LevelChunkSection var36 : var6) {
                  var36.release();
               }

               for (BlockPos var33 : var27) {
                  var4.markPosForPostprocessing(var33);
               }

               Heightmap.primeHeightmaps(var4, EnumSet.of(Types.OCEAN_FLOOR_WG, Types.WORLD_SURFACE_WG));
               return var4;
            }
         ),
         Util.backgroundExecutor()
      );
   }

   public int getBaseHeight(int var1, int var2, Types var3, LevelHeightAccessor var4, RandomState var5) {
      this.checkReviewHeight(var4);
      AlpineLayout.Sample var6 = this.layout.sample((double)var1, (double)var2);
      int var7 = var6.floor();
      if (var3.isOpaque().test(WATER)) {
         var7 = Math.max(var7, var6.water());
      }

      return Math.max(var4.getMinBuildHeight(), Math.min(var4.getMaxBuildHeight(), var7 + 1));
   }

   public NoiseColumn getBaseColumn(int var1, int var2, LevelHeightAccessor var3, RandomState var4) {
      this.checkReviewHeight(var3);
      AlpineLayout var5 = this.layout;
      AlpineLayout.Sample var6 = var5.sample((double)var1, (double)var2);
      double var7 = var5.slope(var1, var2);
      AlpineColumn var9 = new AlpineColumn(var5, var6, var7, var1, var2);
      BlockState[] var10 = new BlockState[var3.getHeight()];

      for (int var11 = 0; var11 < var10.length; var11++) {
         var10[var11] = var9.at(var3.getMinBuildHeight() + var11);
      }

      return new NoiseColumn(var3.getMinBuildHeight(), var10);
   }

   public void buildSurface(WorldGenRegion var1, StructureManager var2, RandomState var3, ChunkAccess var4) {
   }

   public void applyCarvers(WorldGenRegion var1, long var2, RandomState var4, BiomeManager var5, StructureManager var6, ChunkAccess var7, Carving var8) {
   }

   public void spawnOriginalMobs(WorldGenRegion var1) {
      ChunkPos var2 = var1.getCenter();
      WorldgenRandom var3 = new WorldgenRandom(new LegacyRandomSource(var1.getSeed()));
      var3.setDecorationSeed(var1.getSeed(), var2.getMinBlockX(), var2.getMinBlockZ());
      BlockPos var4 = new BlockPos(var2.getMinBlockX() + 8, 120, var2.getMinBlockZ() + 8);
      NaturalSpawner.spawnMobsForChunkGeneration(var1, var1.getBiome(var4), var2, var3);
   }

   public void addDebugScreenInfo(List<String> var1, RandomState var2, BlockPos var3) {
      AlpineLayout.Sample var4 = this.layout.sample((double)var3.getX(), (double)var3.getZ());
      var1.add(String.format(Locale.ROOT, "Frontier alpine: ground %.1f | river %.1f | cover %.2f", var4.ground(), var4.riverLevel(), var4.forest()));
   }
}
