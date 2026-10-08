package com.formaworks.frontierhunts.camps;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Blocks, items and block entities of the multiplayer camps workstream (self-registering, no FrontierHunts edits). */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class CampsContent {
   public static final DeferredHolder<Block, CampPostBlock> CAMP_POST = DeferredHolder.create(Registries.BLOCK, id("camp_post"));
   public static final DeferredHolder<Block, TrophyBoardBlock> TROPHY_BOARD = DeferredHolder.create(Registries.BLOCK, id("big_buck_board"));
   public static final DeferredHolder<Item, Item> CAMP_POST_ITEM = DeferredHolder.create(Registries.ITEM, id("camp_post"));
   public static final DeferredHolder<Item, Item> TROPHY_BOARD_ITEM = DeferredHolder.create(Registries.ITEM, id("big_buck_board"));
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CampPostBlockEntity>> CAMP_POST_BE = DeferredHolder.create(
      Registries.BLOCK_ENTITY_TYPE, id("camp_post")
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrophyBoardBlockEntity>> TROPHY_BOARD_BE = DeferredHolder.create(
      Registries.BLOCK_ENTITY_TYPE, id("big_buck_board")
   );

   private CampsContent() {
   }

   public static ResourceLocation id(String path) {
      return ResourceLocation.fromNamespaceAndPath("frontierhunts", path);
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      e.register(Registries.BLOCK, id("camp_post"), () -> new CampPostBlock(
         BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK)
      ));
      e.register(Registries.BLOCK, id("big_buck_board"), () -> new TrophyBoardBlock(
         BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.5F, 4.0F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK)
      ));
      e.register(Registries.ITEM, id("camp_post"), () -> new BlockItem(CAMP_POST.get(), new Item.Properties().stacksTo(16)));
      e.register(Registries.ITEM, id("big_buck_board"), () -> new BlockItem(TROPHY_BOARD.get(), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
      e.register(Registries.BLOCK_ENTITY_TYPE, id("camp_post"), () -> BlockEntityType.Builder.of(CampPostBlockEntity::new, CAMP_POST.get()).build(null));
      e.register(Registries.BLOCK_ENTITY_TYPE, id("big_buck_board"), () -> BlockEntityType.Builder.of(TrophyBoardBlockEntity::new, TROPHY_BOARD.get()).build(null));
   }

   @SubscribeEvent
   public static void creative(BuildCreativeModeTabContentsEvent e) {
      if (e.getTabKey().location().equals(id("field_equipment"))) {
         // [items] grouped with the other camp structures (after the game pole) instead of the end of the tab
         com.formaworks.frontierhunts.items.TabPlacement.after(e, (ItemLike)CAMP_POST_ITEM.get(), "game_pole", "tanning_rack", "contract_board");
         com.formaworks.frontierhunts.items.TabPlacement.after(e, (ItemLike)TROPHY_BOARD_ITEM.get(), "camp_post", "game_pole");
      }
   }
}
