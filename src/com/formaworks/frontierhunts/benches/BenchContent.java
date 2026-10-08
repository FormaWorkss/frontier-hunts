package com.formaworks.frontierhunts.benches;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntContent;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * [benches] Registration of the three workbenches (blocks, items, menus) and the bench recipe type and serializers.
 * Self-registering (RegisterEvent on the mod bus); nothing in FrontierHunts.java. Also puts the three benches at the
 * head of the Frontier Hunts gear tab and takes the retired benches out of creative ({@link OldBenches}).
 */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class BenchContent {
   public static final Map<Bench, DeferredHolder<Block, BenchBlock>> BLOCKS = new EnumMap<>(Bench.class);
   public static final Map<Bench, DeferredHolder<Item, Item>> ITEMS = new EnumMap<>(Bench.class);
   public static final Map<Bench, DeferredHolder<net.minecraft.world.inventory.MenuType<?>, MenuType<BenchMenu>>> MENUS = new EnumMap<>(Bench.class);

   static {
      for (Bench b : Bench.values()) {
         BLOCKS.put(b, DeferredHolder.create(Registries.BLOCK, b.rl()));
         ITEMS.put(b, DeferredHolder.create(Registries.ITEM, b.rl()));
         MENUS.put(b, DeferredHolder.create(Registries.MENU, b.rl()));
      }
   }

   private BenchContent() {
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      for (Bench b : Bench.values()) {
         e.register(Registries.BLOCK, b.rl(), () -> new BenchBlock(b, BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .instrument(NoteBlockInstrument.BASS)
            .strength(2.5F)
            .sound(SoundType.WOOD)
            .noOcclusion()
            .ignitedByLava()
            .pushReaction(PushReaction.BLOCK)));
         e.register(Registries.ITEM, b.rl(), () -> new BenchItem(BLOCKS.get(b).get(), b));
         e.register(Registries.MENU, b.rl(), () -> new MenuType<>((id, inv) -> new BenchMenu(id, inv, b), FeatureFlags.DEFAULT_FLAGS));
      }
      e.register(Registries.RECIPE_TYPE, BenchRecipes.TYPE_ID, BenchRecipes::newType);
      e.register(Registries.RECIPE_SERIALIZER, BenchRecipes.SHAPED_ID, BenchRecipes.Shaped.Serializer::new);
      e.register(Registries.RECIPE_SERIALIZER, BenchRecipes.SHAPELESS_ID, BenchRecipes.Shapeless.Serializer::new);
   }

   /** The three benches open the gear tab, right after the Frontier Handbook; the retired ones leave creative. */
   @SubscribeEvent(priority = EventPriority.LOW)
   public static void creative(BuildCreativeModeTabContentsEvent e) {
      try {
         OldBenches.hideFromCreative(e);
         if (e.getTabKey() != HuntContent.GEAR_TAB) {
            return;
         }
         String anchor = "frontier_handbook";
         for (Bench b : Bench.values()) {
            com.formaworks.frontierhunts.items.TabPlacement.after(e, b.item(), anchor, "hunters_journal");
            anchor = b.id;
         }
      } catch (RuntimeException ex) {
         // a tab layout is cosmetic: never let it break the creative screen
      }
   }

   /** The bench item: places the two-wide bench and says in one line what it makes. */
   public static final class BenchItem extends BlockItem {
      public final Bench bench;

      BenchItem(Block block, Bench bench) {
         super(block, new Item.Properties());
         this.bench = bench;
      }

      @Override
      public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
         super.appendHoverText(stack, ctx, lines, flag);
         lines.add(Component.translatable("bench.frontierhunts." + this.bench.id + ".tip").withStyle(ChatFormatting.GRAY));
      }
   }
}
