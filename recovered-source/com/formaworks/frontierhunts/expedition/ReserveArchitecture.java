package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.camp.CampStationBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.neoforged.neoforge.registries.DeferredBlock;

public final class ReserveArchitecture {
   public static final Map<String, DeferredBlock<? extends Block>> BLOCKS = new LinkedHashMap<>();

   private static Properties wood() {
      return Properties.of().strength(2.5F).sound(SoundType.WOOD);
   }

   private static <T extends Block> DeferredBlock<T> add(String id, Supplier<T> factory) {
      DeferredBlock<T> b = ExpeditionContent.BLOCKS.register(id, factory);
      BLOCKS.put(id, b);
      HuntContent.ITEMS.registerSimpleBlockItem(b);
      return b;
   }

   public static void init() {
   }

   private ReserveArchitecture() {
   }

   static {
      for (String id : List.of("lookout_stair_rail", "lookout_deck_rail", "lookout_brace", "lookout_cross_brace")) {
         add(id, () -> new LookoutTimber(id, wood().noOcclusion()));
      }

      for (String id : List.of("pine_planks", "cedar_planks", "weathered_planks", "canvas_wall")) {
         add(id, () -> new Block(wood()));
      }

      for (String id : List.of("pine_log", "cedar_log", "aspen_log", "birch_log")) {
         add(id, () -> new RotatedPillarBlock(wood()));
      }

      for (String id : List.of("fieldstone", "reserve_granite", "roof_shingles", "forest_loam", "moss_floor")) {
         add(id, () -> new Block(Properties.of().strength(2.5F).sound(SoundType.STONE)));
      }

      add("pine_stairs", () -> new StairBlock(((Block)BLOCKS.get("pine_planks").get()).defaultBlockState(), wood()));
      add("roof_stairs", () -> new StairBlock(((Block)BLOCKS.get("roof_shingles").get()).defaultBlockState(), wood()));
      add("fieldstone_stairs", () -> new StairBlock(((Block)BLOCKS.get("fieldstone").get()).defaultBlockState(), wood()));
      add("pine_slab", () -> new SlabBlock(wood()));
      add("roof_slab", () -> new SlabBlock(wood()));
      add("pine_fence", () -> new FenceBlock(wood()));
      add("rope_ladder", () -> new LadderBlock(wood().noOcclusion()));
      add("cabin_door", () -> new DoorBlock(BlockSetType.SPRUCE, wood().noOcclusion()));
      add("camp_cot", () -> new ReserveCot(wood().noOcclusion()));
      add("lit_lodge_table", () -> new ReserveFurniture("lit_lodge_table", wood().noOcclusion().lightLevel(s -> 13)));
      add("timber_brace", () -> new ReserveFurniture("timber_brace", wood().noOcclusion()));
      add("timber_cross_brace", () -> new ReserveFurniture("timber_cross_brace", wood().noOcclusion()));
      add("timber_stair_guard", () -> new ReserveFurniture("timber_stair_guard", wood().noOcclusion()));
      add("tent_end_ridge", () -> new ReserveFurniture("tent_end_ridge", wood().noOcclusion()));
      add("stove_roof_flashing", () -> new ReserveFurniture("stove_roof_flashing", wood().strength(3.0F).sound(SoundType.METAL).noOcclusion()));
      add(
         "lodge_stove",
         () -> new CampStationBlock(
               true, wood().strength(3.0F).sound(SoundType.METAL).noOcclusion().lightLevel(s -> s.getValue(CampStationBlock.WORKING) ? 9 : 0)
            )
      );

      for (String id : List.of(
         "roof_slope_low",
         "roof_slope_high",
         "roof_gable_low",
         "roof_gable_high",
         "roof_ridge_low",
         "roof_ridge_high",
         "tent_slope",
         "tent_gable",
         "tent_ridge",
         "stove_flue",
         "ranger_window",
         "cabin_lantern",
         "lodge_chair",
         "lodge_table",
         "stacked_firewood",
         "trail_sign"
      )) {
         add(id, () -> new ReserveFurniture(id, wood().noOcclusion().lightLevel(s -> id.equals("cabin_lantern") ? 13 : (id.equals("lodge_stove") ? 9 : 0))));
      }
   }
}
