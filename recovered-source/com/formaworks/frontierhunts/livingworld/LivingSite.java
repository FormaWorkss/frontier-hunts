package com.formaworks.frontierhunts.livingworld;

import com.formaworks.frontierhunts.livingworld.plan.Ctx;
import com.formaworks.frontierhunts.livingworld.plan.Kind;
import com.formaworks.frontierhunts.livingworld.plan.Kinds;
import com.formaworks.frontierhunts.livingworld.plan.Plan;
import com.formaworks.frontierhunts.livingworld.plan.Rnd;
import com.formaworks.frontierhunts.livingworld.plan.Terrain;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * [livingworld] World-generated site. {@code kind} picks the builder ({@link Kinds}); {@code variant} forces a layout
 * (-1 = random); {@code avoid} lists structure sets whose potential sites within {@code avoid_chunks} chunks block this one
 * (other settlements, villages), so sites never stack on top of each other.
 */
public final class LivingSite extends Structure {
   public static final MapCodec<LivingSite> CODEC = RecordCodecBuilder.mapCodec(
      i -> i.group(
            settingsCodec(i),
            Codec.STRING.fieldOf("kind").forGetter(s -> s.kind),
            Codec.INT.optionalFieldOf("variant", -1).forGetter(s -> s.variant),
            ResourceLocation.CODEC.listOf().optionalFieldOf("avoid", List.of()).forGetter(s -> s.avoid),
            Codec.INT.optionalFieldOf("avoid_chunks", 4).forGetter(s -> s.avoidChunks)
         )
         .apply(i, LivingSite::new)
   );
   private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
   public final String kind;
   public final int variant;
   public final List<ResourceLocation> avoid;
   public final int avoidChunks;

   public LivingSite(StructureSettings settings, String kind, int variant, List<ResourceLocation> avoid, int avoidChunks) {
      super(settings);
      this.kind = kind;
      this.variant = variant;
      this.avoid = avoid;
      this.avoidChunks = avoidChunks;
   }

   @Override
   public StructureType<?> type() {
      return LivingWorld.SITE.get();
   }

   /** builds the plan for a site; null when the builder rejects the terrain or fails */
   public static Plan plan(String kindId, Terrain terrain, long seed, int ox, int oz, int rot, int variant) {
      Kind k = Kinds.get(kindId);
      if (k == null) {
         if (WARNED.add(kindId)) {
            LivingWorld.LOG.warn("[Frontier Hunts] unknown living-world site kind '{}'", kindId);
         }
         return null;
      }
      try {
         Ctx c = new Ctx(kindId, terrain, seed, ox, oz, rot, Math.floorMod(variant, k.variants.length));
         if (!k.quickCheck(c) || !k.build(c)) {
            return null;
         }
         com.formaworks.frontierhunts.livingworld.plan.Weather.apply(c); // [structures2] weathered materials
         c.plan.connect();
         c.plan.cx = ox;
         c.plan.cz = oz;
         return c.plan;
      } catch (RuntimeException ex) {
         if (WARNED.add(kindId + ":" + ex.getClass().getName())) {
            LivingWorld.LOG.error("[Frontier Hunts] living-world site '{}' failed to plan at {} {}", kindId, ox, oz, ex);
         }
         return null;
      }
   }

   @Override
   protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
      Kind k = Kinds.get(this.kind);
      if (k == null) {
         return Optional.empty();
      }
      ChunkPos cp = ctx.chunkPos();
      if (this.blocked(ctx, cp)) {
         return Optional.empty();
      }
      long seed = ctx.random().nextLong();
      int rot = ctx.random().nextInt(4);
      int variant = this.variant >= 0 ? this.variant : k.pickVariant(new Rnd(seed ^ 0x5EEDL));
      McTerrain terrain = new McTerrain(ctx.chunkGenerator(), ctx.heightAccessor(), ctx.randomState());
      // a few candidate spots around the chunk centre: the first with suitable ground wins
      int[][] tries = {{0, 0}, {9, 6}, {-8, -7}};
      for (int i = 0; i < tries.length; i++) {
         int ox = cp.getMiddleBlockX() + tries[i][0] + ctx.random().nextInt(5) - 2;
         int oz = cp.getMiddleBlockZ() + tries[i][1] + ctx.random().nextInt(5) - 2;
         int y = terrain.floor(ox, oz);
         if (!ctx.validBiome().test(ctx.biomeSource().getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(ox), net.minecraft.core.QuartPos.fromBlock(y),
            net.minecraft.core.QuartPos.fromBlock(oz), ctx.randomState().sampler()))) {
            continue;
         }
         Plan plan = plan(this.kind, terrain, seed + i, ox, oz, rot, variant);
         if (plan == null || plan.minY < ctx.heightAccessor().getMinBuildHeight() + 8 || plan.maxY + 40 > ctx.heightAccessor().getMaxBuildHeight()) {
            continue;
         }
         LivingPiece piece = new LivingPiece(this.kind, seed + i, ox, oz, rot, variant, terrain, plan);
         return Optional.of(new GenerationStub(new BlockPos(ox, plan.cy, oz), b -> b.addPiece(piece)));
      }
      return Optional.empty();
   }

   /** another settlement set has a potential site this close (random-spread sets only) */
   private boolean blocked(GenerationContext ctx, ChunkPos cp) {
      if (this.avoid.isEmpty()) {
         return false;
      }
      Registry<StructureSet> sets = ctx.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
      int r = this.avoidChunks;
      for (ResourceLocation id : this.avoid) {
         StructureSet set = sets.get(id);
         if (set == null || !(set.placement() instanceof RandomSpreadStructurePlacement rsp)) {
            continue;
         }
         int step = Math.max(1, rsp.spacing());
         int r0 = r;
         // [villages] village-scale sets: keep camps/stands well clear of a whole village. [gear20] by name too: the village
         // spacing went down to 28 in gear.19, so the old "spacing >= 40" test let stands and towers crowd the villages
         String path = id.getPath();
         // [1.1.2] camps and stands keep 20 chunks (320 blocks) from a Frontier village: never right beside one. (Only
         // our sparse village set: vanilla's dense village grid would rule out nearly all land at that radius.)
         boolean ours = id.getNamespace().equals("frontierstructures") && path.contains("village");
         r = ours ? Math.max(r0, 20) : rsp.spacing() >= 40 || path.contains("village") ? Math.max(r0, 7) : path.contains("lookout") ? Math.max(r0, 5) : r0;
         for (int dx = -r - step; dx <= r + step; dx += Math.max(1, step / 2)) {
            for (int dz = -r - step; dz <= r + step; dz += Math.max(1, step / 2)) {
               ChunkPos p = rsp.getPotentialStructureChunk(ctx.seed(), cp.x + dx, cp.z + dz);
               if (Math.abs(p.x - cp.x) <= r && Math.abs(p.z - cp.z) <= r && !(p.x == cp.x && p.z == cp.z && sameSet(sets, id))
                  && (!ours || passesFrequency(rsp, p, ctx.seed()))) {
                  return true;
               }
            }
         }
         r = r0;
      }
      return false;
   }

   /**
    * [1.1.2] whether a potential site also passes its set's frequency (the village set leaves cells empty at random):
    * an empty cell must not keep camps away
    */
   private static boolean passesFrequency(RandomSpreadStructurePlacement rsp, ChunkPos p, long seed) {
      return rsp.applyAdditionalChunkRestrictions(p.x, p.z, seed);
   }

   private boolean sameSet(Registry<StructureSet> sets, ResourceLocation id) {
      StructureSet set = sets.get(id);
      if (set == null) {
         return false;
      }
      for (StructureSet.StructureSelectionEntry e : set.structures()) {
         if (e.structure().value() == this) {
            return true;
         }
      }
      return false;
   }
}
