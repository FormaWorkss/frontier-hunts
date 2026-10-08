package com.formaworks.frontierhunts.camp;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.expedition.ReserveArchitecture;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.Builder;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Blocks;

public final class CampContent {
   public static final Blocks BLOCKS = DeferredRegister.createBlocks("frontierhunts");
   public static final DeferredBlock<CampStationBlock> SMOKEHOUSE = BLOCKS.register(
      "smokehouse",
      () -> new CampStationBlock(
            true, Properties.of().strength(3.0F).sound(SoundType.METAL).noOcclusion().lightLevel(var0 -> var0.getValue(CampStationBlock.WORKING) ? 5 : 0)
         )
   );
   public static final DeferredBlock<CampStationBlock> TANNING_RACK = BLOCKS.register(
      "tanning_rack", () -> new CampStationBlock(false, Properties.of().strength(2.0F).sound(SoundType.WOOD).noOcclusion())
   );
   public static final DeferredBlock<GamePole> GAME_POLE = BLOCKS.register(
      "game_pole", () -> new GamePole(Properties.of().strength(1.6F).sound(SoundType.WOOD).noOcclusion())
   );
   public static final DeferredBlock<ContractBoardBlock> CONTRACT_BOARD = BLOCKS.register(
      "contract_board", () -> new ContractBoardBlock(Properties.of().strength(2.0F).sound(SoundType.WOOD).noOcclusion())
   );
   private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "frontierhunts");
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CampStation>> STATION = ENTITIES.register(
      "camp_station",
      () -> Builder.of(
               CampStation::new, new Block[]{(Block)SMOKEHOUSE.get(), (Block)TANNING_RACK.get(), (Block)ReserveArchitecture.BLOCKS.get("lodge_stove").get()}
            )
            .build(null)
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GamePole.Rack>> GAME_RACK = ENTITIES.register(
      "game_pole", () -> Builder.of(GamePole.Rack::new, new Block[]{(Block)GAME_POLE.get()}).build(null)
   );
   private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, "frontierhunts");
   public static final DeferredHolder<MenuType<?>, MenuType<StationMenu>> SMOKE_MENU = MENUS.register(
      "smokehouse", () -> new MenuType((var0, var1) -> new StationMenu(var0, var1, true), FeatureFlags.DEFAULT_FLAGS)
   );
   public static final DeferredHolder<MenuType<?>, MenuType<StationMenu>> TAN_MENU = MENUS.register(
      "tanning_rack", () -> new MenuType((var0, var1) -> new StationMenu(var0, var1, false), FeatureFlags.DEFAULT_FLAGS)
   );
   public static final DeferredHolder<MenuType<?>, MenuType<ContractMenu>> CONTRACT_MENU = MENUS.register(
      "contracts", () -> new MenuType(ContractMenu::new, FeatureFlags.DEFAULT_FLAGS)
   );
   private static final DeferredRegister<StructureType<?>> STRUCTURES = DeferredRegister.create(Registries.STRUCTURE_TYPE, "frontierhunts");
   public static final DeferredHolder<StructureType<?>, StructureType<RangerCampStructure>> CAMP = STRUCTURES.register(
      "ranger_camp", () -> () -> RangerCampStructure.CODEC
   );
   private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, "frontierhunts");
   public static final DeferredHolder<StructurePieceType, StructurePieceType> CAMP_PIECE = PIECES.register(
      "ranger_camp", () -> (var0, var1) -> new RangerCampStructure.Piece(var0.structureTemplateManager(), var1)
   );

   public static void register(IEventBus var0) {
      BLOCKS.register(var0);
      ENTITIES.register(var0);
      MENUS.register(var0);
      STRUCTURES.register(var0);
      PIECES.register(var0);
   }

   private CampContent() {
   }

   static {
      HuntContent.ITEMS.registerSimpleBlockItem(SMOKEHOUSE);
      HuntContent.ITEMS.registerSimpleBlockItem(TANNING_RACK);
      HuntContent.ITEMS.registerSimpleBlockItem(CONTRACT_BOARD);
      HuntContent.ITEMS.registerSimpleBlockItem(GAME_POLE);
   }
}
