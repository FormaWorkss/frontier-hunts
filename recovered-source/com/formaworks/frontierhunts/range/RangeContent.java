package com.formaworks.frontierhunts.range;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.items.TabPlacement;
import com.formaworks.frontierhunts.outfitter.OutfitterItem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * [1.1.7] Registers the outfitter's counter exclusives ({@link OutfitterItem}) and the range targets
 * ({@link RangeTargetBlock}). None of them has a recipe: the counter is the only place to get them, and they are listed in
 * {@link ExpeditionContent#ITEMS} so the store can sell them.
 */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class RangeContent {
   /** store order: gear first, then the range */
   public static final List<String> GEAR = List.of(OutfitterItem.LAMP, OutfitterItem.STICKS, OutfitterItem.LURE, OutfitterItem.COFFEE,
      OutfitterItem.WARMERS, OutfitterItem.MILKWEED, OutfitterItem.TAPE);
   /** [1.1.8] store-only items registered elsewhere (the hound lead, HoundContent) */
   static final List<String> SOLD_ELSEWHERE = List.of("hound_lead");
   private static final Map<String, Function<BlockBehaviour.Properties, Block>> BLOCKS = new LinkedHashMap<>();

   static {
      BLOCKS.put("steel_gong", RangeTargetBlock.Gong::new);
      BLOCKS.put("steel_popper", RangeTargetBlock.Popper::new);
      BLOCKS.put("steel_deer_target", RangeTargetBlock.Silhouette::new);
      BLOCKS.put("steel_spinner", RangeTargetBlock.Spinner::new);
      BLOCKS.put("foam_deer_target", RangeTargetBlock.FoamDeer::new);
      BLOCKS.put("range_marker", RangeTargetBlock.Marker::new);
   }

   private RangeContent() {
   }

   public static List<String> targets() {
      return List.copyOf(BLOCKS.keySet());
   }

   /** puts the counter's items into the expedition item map (the store's lookup); idempotent. [1.1.8] the range targets
    *  are crafted now and no longer sold */
   public static void listForStore() {
      for (String id : GEAR) {
         ExpeditionContent.ITEMS.putIfAbsent(id, DeferredItem.createItem(FrontierHunts.id(id)));
      }
      for (String id : SOLD_ELSEWHERE) {
         ExpeditionContent.ITEMS.putIfAbsent(id, DeferredItem.createItem(FrontierHunts.id(id)));
      }
   }

   /** [1.1.8] the flagging-tape marker block, or null before registration */
   public static net.minecraft.world.level.block.state.BlockState flag() {
      Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(FrontierHunts.id("trail_flag"));
      return b == null || b == net.minecraft.world.level.block.Blocks.AIR ? null : b.defaultBlockState();
   }

   private static BlockBehaviour.Properties props(String id) {
      BlockBehaviour.Properties p = BlockBehaviour.Properties.of().noOcclusion().pushReaction(PushReaction.DESTROY);
      return switch (id) {
         case "foam_deer_target" -> p.mapColor(MapColor.COLOR_BROWN).strength(0.8F).sound(SoundType.WOOL);
         case "range_marker" -> p.mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD);
         default -> p.mapColor(MapColor.METAL).strength(2.0F, 6.0F).sound(SoundType.CHAIN);
      };
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      e.register(Registries.BLOCK, h -> {
         for (Map.Entry<String, Function<BlockBehaviour.Properties, Block>> b : BLOCKS.entrySet()) {
            h.register(FrontierHunts.id(b.getKey()), b.getValue().apply(props(b.getKey())));
         }
         h.register(FrontierHunts.id("trail_flag"), new com.formaworks.frontierhunts.outfitter.TrailFlagBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE).noCollission().instabreak().noOcclusion().sound(SoundType.WOOL).pushReaction(PushReaction.DESTROY)));
      });
      e.register(Registries.ITEM, h -> {
         for (String id : GEAR) {
            h.register(FrontierHunts.id(id), new OutfitterItem(id));
         }
         for (String id : BLOCKS.keySet()) {
            Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(FrontierHunts.id(id));
            h.register(FrontierHunts.id(id), new BlockItem(block, new Item.Properties().stacksTo(id.equals("range_marker") ? 64 : 16)) {
               @Override
               public void appendHoverText(net.minecraft.world.item.ItemStack st, TooltipContext ctx, List<net.minecraft.network.chat.Component> tip,
                  net.minecraft.world.item.TooltipFlag flag) {
                  tip.add(net.minecraft.network.chat.Component.translatable("block.frontierhunts." + id + ".desc").withStyle(net.minecraft.ChatFormatting.GRAY));
                                 }
            });
         }
      });
      listForStore();
   }

   @SubscribeEvent
   public static void creative(BuildCreativeModeTabContentsEvent e) {
      if (e.getTabKey() != HuntContent.GEAR_TAB) {
         return;
      }
      String anchor = "shooting_target";
      for (String id : BLOCKS.keySet()) {
         Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(FrontierHunts.id(id));
         TabPlacement.after(e, it, anchor, "shooting_target");
         anchor = id;
      }
      anchor = "wind_checker";
      for (String id : GEAR) {
         Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(FrontierHunts.id(id));
         TabPlacement.after(e, it, anchor, "wind_checker");
         anchor = id;
      }
   }
}
