package com.formaworks.frontierhunts.landscape.stand;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.Builder;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(
   modid = "frontierhunts",
   bus = Bus.MOD
)
public final class BowStandContent {
   public static final DeferredHolder<Block, BowStandBlock> BLOCK = DeferredHolder.create(Registries.BLOCK, FrontierHunts.id("bow_stand"));
   public static final DeferredHolder<Item, BlockItem> ITEM = DeferredHolder.create(Registries.ITEM, FrontierHunts.id("bow_stand"));
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BowStandBlockEntity>> ENTITY = DeferredHolder.create(
      Registries.BLOCK_ENTITY_TYPE, FrontierHunts.id("bow_stand")
   );

   private BowStandContent() {
   }

   @SubscribeEvent
   public static void register(RegisterEvent var0) {
      var0.register(
         Registries.BLOCK,
         FrontierHunts.id("bow_stand"),
         () -> new BowStandBlock(Properties.of().mapColor(MapColor.WOOD).strength(1.2F).sound(SoundType.WOOD).noOcclusion().ignitedByLava())
      );
      var0.register(Registries.ITEM, FrontierHunts.id("bow_stand"), () -> new BlockItem((Block)BLOCK.get(), new net.minecraft.world.item.Item.Properties()));
      var0.register(
         Registries.BLOCK_ENTITY_TYPE, FrontierHunts.id("bow_stand"), () -> Builder.of(BowStandBlockEntity::new, new Block[]{(Block)BLOCK.get()}).build(null)
      );
   }

   @SubscribeEvent
   public static void creative(BuildCreativeModeTabContentsEvent var0) {
      if (var0.getTabKey().location().equals(FrontierHunts.id("field_equipment"))) {
         var0.accept((ItemLike)ITEM.get());
      }
   }
}
