package com.formaworks.frontierhunts.livingworld;

import com.formaworks.frontierhunts.livingworld.plan.Executor;
import com.formaworks.frontierhunts.livingworld.plan.Plan;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * [livingworld] The one piece of a living-world site. It stores only the plan inputs (kind, seed, origin, rotation,
 * variant and the sampled natural heights); the plan is rebuilt deterministically and each chunk writes its own part.
 */
public final class LivingPiece extends StructurePiece {
   private final String kind;
   private final long seed;
   private final int ox;
   private final int oz;
   private final int rot;
   private final int variant;
   private final CompoundTag heights;
   private Plan plan;

   LivingPiece(String kind, long seed, int ox, int oz, int rot, int variant, McTerrain terrain, Plan plan) {
      super(LivingWorld.PIECE.get(), 0, box(plan));
      this.kind = kind;
      this.seed = seed;
      this.ox = ox;
      this.oz = oz;
      this.rot = rot;
      this.variant = variant;
      this.heights = terrain.save();
      this.plan = plan;
   }

   public LivingPiece(CompoundTag tag) {
      super(LivingWorld.PIECE.get(), tag);
      this.kind = tag.getString("Kind");
      this.seed = tag.getLong("Seed");
      this.ox = tag.getInt("OX");
      this.oz = tag.getInt("OZ");
      this.rot = tag.getInt("Rot");
      this.variant = tag.getInt("Variant");
      this.heights = tag.getCompound("Heights");
   }

   static BoundingBox box(Plan p) {
      return new BoundingBox(p.minX - 2, p.minY - 4, p.minZ - 2, p.maxX + 2, p.maxY + 40, p.maxZ + 2);
   }

   @Override
   protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
      tag.putString("Kind", this.kind);
      tag.putLong("Seed", this.seed);
      tag.putInt("OX", this.ox);
      tag.putInt("OZ", this.oz);
      tag.putInt("Rot", this.rot);
      tag.putInt("Variant", this.variant);
      tag.put("Heights", this.heights);
   }

   public String kind() {
      return this.kind;
   }

   private Plan plan(ChunkGenerator gen, WorldGenLevel level) {
      if (this.plan == null) {
         RandomState rs = level.getLevel().getChunkSource().randomState();
         McTerrain t = new McTerrain(gen, level, rs);
         t.load(this.heights);
         this.plan = LivingSite.plan(this.kind, t, this.seed, this.ox, this.oz, this.rot, this.variant);
      }
      return this.plan;
   }

   @Override
   public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator gen, RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot) {
      Plan p;
      try {
         p = this.plan(gen, level);
      } catch (RuntimeException ex) {
         LivingWorld.LOG.error("[Frontier Hunts] could not rebuild living-world site {} at {} {}", this.kind, this.ox, this.oz, ex);
         return;
      }
      if (p == null) {
         return;
      }
      try {
         Executor.run(p, new McWorld(level, box), box.minX(), box.minZ(), box.maxX(), box.maxZ());
         if (box.isInside(new BlockPos(this.ox, box.minY(), this.oz))) {
            LivingWorld.LOG.debug("[Frontier Hunts] living-world site {} ({}) at {} {} {}", this.kind, p.title, this.ox, p.cy, this.oz);
         }
      } catch (RuntimeException ex) {
         LivingWorld.LOG.error("[Frontier Hunts] living-world site {} failed in chunk {}", this.kind, chunk, ex);
      }
   }
}
