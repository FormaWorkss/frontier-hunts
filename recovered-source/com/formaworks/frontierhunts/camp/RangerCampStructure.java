package com.formaworks.frontierhunts.camp;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class RangerCampStructure extends Structure {
   public static final MapCodec<RangerCampStructure> CODEC = simpleCodec(RangerCampStructure::new);

   public RangerCampStructure(StructureSettings var1) {
      super(var1);
   }

   public Optional<GenerationStub> findGenerationPoint(GenerationContext var1) {
      if (!(Boolean)HuntConfig.GENERATE_RANGER_CAMPS.get()) {
         return Optional.empty();
      } else {
         int var2 = var1.chunkPos().getMinBlockX();
         int var3 = var1.chunkPos().getMinBlockZ();
         int var4 = Integer.MAX_VALUE;
         int var5 = Integer.MIN_VALUE;

         for (byte var6 = 0; var6 <= 16; var6 += 4) {
            for (byte var7 = 0; var7 <= 16; var7 += 4) {
               int var8 = var1.chunkGenerator().getFirstFreeHeight(var2 + var6, var3 + var7, Types.OCEAN_FLOOR_WG, var1.heightAccessor(), var1.randomState());
               NoiseColumn var9 = var1.chunkGenerator().getBaseColumn(var2 + var6, var3 + var7, var1.heightAccessor(), var1.randomState());
               BlockState var10 = var9.getBlock(var8 - 1);
               BlockState var11 = var9.getBlock(var8);
               if (!var10.isSolid() || !var10.getFluidState().isEmpty() || !var11.getFluidState().isEmpty()) {
                  return Optional.empty();
               }

               var4 = Math.min(var4, var8);
               var5 = Math.max(var5, var8);
            }
         }

         if (var5 - var4 <= 2 && var5 >= var1.heightAccessor().getMinBuildHeight() + 4 && var5 + 11 < var1.heightAccessor().getMaxBuildHeight()) {
            BlockPos var12 = new BlockPos(var2, var5 - 3, var3);
            Rotation var13 = Rotation.getRandom(var1.random());
            return Optional.of(
               new GenerationStub(
                  var12.above(3).offset(8, 0, 8), var3x -> var3x.addPiece(new RangerCampStructure.Piece(var1.structureTemplateManager(), var12, var13))
               )
            );
         } else {
            return Optional.empty();
         }
      }
   }

   public StructureType<?> type() {
      return (StructureType<?>)CampContent.CAMP.get();
   }

   public static final class Piece extends TemplateStructurePiece {
      public Piece(StructureTemplateManager var1, BlockPos var2, Rotation var3) {
         super((StructurePieceType)CampContent.CAMP_PIECE.get(), 0, var1, FrontierHunts.id("ranger_camp"), "frontierhunts:ranger_camp", settings(var3), var2);
      }

      public Piece(StructureTemplateManager var1, CompoundTag var2) {
         super((StructurePieceType)CampContent.CAMP_PIECE.get(), var2, var1, var1x -> settings(Rotation.valueOf(var2.getString("Rotation"))));
      }

      private static StructurePlaceSettings settings(Rotation var0) {
         return new StructurePlaceSettings()
            .setRotation(var0)
            .setRotationPivot(new BlockPos(8, 0, 8))
            .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
            .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
      }

      protected void addAdditionalSaveData(StructurePieceSerializationContext var1, CompoundTag var2) {
         super.addAdditionalSaveData(var1, var2);
         var2.putString("Rotation", this.placeSettings.getRotation().name());
      }

      protected void handleDataMarker(String var1, BlockPos var2, ServerLevelAccessor var3, RandomSource var4, BoundingBox var5) {
      }
   }
}
