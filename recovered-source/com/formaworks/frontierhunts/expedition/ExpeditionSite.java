package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.HuntConfig;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure.GenerationContext;
import net.minecraft.world.level.levelgen.structure.Structure.GenerationStub;
import net.minecraft.world.level.levelgen.structure.Structure.StructureSettings;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class ExpeditionSite extends Structure {
   public static final MapCodec<ExpeditionSite> CODEC = RecordCodecBuilder.mapCodec(
      var0 -> var0.group(settingsCodec(var0), ResourceLocation.CODEC.fieldOf("template").forGetter(var0x -> var0x.template)).apply(var0, ExpeditionSite::new)
   );
   private final ResourceLocation template;

   public ExpeditionSite(StructureSettings var1, ResourceLocation var2) {
      super(var1);
      this.template = var2;
   }

   public Optional<GenerationStub> findGenerationPoint(GenerationContext var1) {
      if (!(Boolean)HuntConfig.GENERATE_RANGER_CAMPS.get()) {
         return Optional.empty();
      } else {
         StructureTemplate var2 = var1.structureTemplateManager().getOrCreate(this.template);
         Vec3i var3 = var2.getSize();
         if (var3.getX() != 0 && var3.getZ() != 0) {
            Rotation var4 = Rotation.getRandom(var1.random());
            int var5 = var1.chunkPos().getMinBlockX();
            int var6 = var1.chunkPos().getMinBlockZ();
            BoundingBox var7 = var2.getBoundingBox(ExpeditionSite.Piece.settings(var4), new BlockPos(var5, 0, var6));
            int var8 = 9999;
            int var9 = -9999;

            for (int var10 = 0; var10 <= 4; var10++) {
               for (int var11 = 0; var11 <= 4; var11++) {
                  int var12 = var7.minX() + (var7.getXSpan() - 1) * var10 / 4;
                  int var13 = var7.minZ() + (var7.getZSpan() - 1) * var11 / 4;
                  int var14 = var1.chunkGenerator().getFirstFreeHeight(var12, var13, Types.OCEAN_FLOOR_WG, var1.heightAccessor(), var1.randomState());
                  NoiseColumn var15 = var1.chunkGenerator().getBaseColumn(var12, var13, var1.heightAccessor(), var1.randomState());
                  if (!var15.getBlock(var14 - 1).isSolid() || !var15.getBlock(var14).getFluidState().isEmpty()) {
                     return Optional.empty();
                  }

                  var8 = Math.min(var8, var14);
                  var9 = Math.max(var9, var14);
               }
            }

            if (var9 - var8 <= 2 && var9 + var3.getY() < var1.heightAccessor().getMaxBuildHeight()) {
               BlockPos var16 = new BlockPos(var5, var9 - 2, var6);
               return Optional.of(
                  new GenerationStub(var16, var4x -> var4x.addPiece(new ExpeditionSite.Piece(var1.structureTemplateManager(), var16, var4, this.template)))
               );
            } else {
               return Optional.empty();
            }
         } else {
            return Optional.empty();
         }
      }
   }

   public StructureType<?> type() {
      return (StructureType<?>)ExpeditionContent.SITE.get();
   }

   public static final class Piece extends TemplateStructurePiece {
      private List<BlockPos> clearance;

      public Piece(StructureTemplateManager var1, BlockPos var2, Rotation var3, ResourceLocation var4) {
         super((StructurePieceType)ExpeditionContent.PIECE.get(), 0, var1, var4, var4.toString(), settings(var3), var2);
      }

      public Piece(StructureTemplateManager var1, CompoundTag var2) {
         super((StructurePieceType)ExpeditionContent.PIECE.get(), var2, var1, var1x -> settings(Rotation.valueOf(var2.getString("Rotation"))));
      }

      private static StructurePlaceSettings settings(Rotation var0) {
         return new StructurePlaceSettings()
            .setRotation(var0)
            .setRotationPivot(new BlockPos(8, 0, 8))
            .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
            .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
      }

      public void postProcess(WorldGenLevel var1, StructureManager var2, ChunkGenerator var3, RandomSource var4, BoundingBox var5, ChunkPos var6, BlockPos var7) {
         if (this.clearance == null) {
            this.clearance = SettlementClearance.authored(this.templateName) ? SettlementClearance.footprint(this.template) : List.of();
         }

         SettlementClearance.clear(var1, this.clearance, this.templatePosition, this.placeSettings, var5);
         super.postProcess(var1, var2, var3, var4, var5, var6, var7);
      }

      protected void addAdditionalSaveData(StructurePieceSerializationContext var1, CompoundTag var2) {
         super.addAdditionalSaveData(var1, var2);
         var2.putString("Rotation", this.placeSettings.getRotation().name());
      }

      protected void handleDataMarker(String var1, BlockPos var2, ServerLevelAccessor var3, RandomSource var4, BoundingBox var5) {
      }
   }
}
