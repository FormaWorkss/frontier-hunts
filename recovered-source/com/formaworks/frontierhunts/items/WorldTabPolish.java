package com.formaworks.frontierhunts.items;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * [polish] Tidies the Frontier Hunts "World" creative tab.
 * <ul>
 *   <li>The old stepped "mossy stone" block (three stacked boxes) opened the tab. It is a worldgen helper that creeks,
 *       landmarks and tree roots still place, so it stays registered (worlds keep it, it still drops itself), but it is
 *       no longer offered in creative: the sculpted (mossy) river stones and boulders further down are the placeable
 *       rocks.</li>
 *   <li>The lookout railings, posts and braces were scattered at the head of the building blocks; they now sit with the
 *       pine fence and the timber braces: fence, deck rail, stair rail, stair guard, braces, cross braces.</li>
 * </ul>
 * Never throws: anything missing from the tab is skipped.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class WorldTabPolish {
   private static final ResourceLocation WORLD = ResourceLocation.fromNamespaceAndPath("frontierhunts", "world");
   private static final String[] HIDDEN = {"mossy_stone"};
   /** Placed one after another right after the pine fence. */
   private static final String[] RAILINGS = {
      "lookout_deck_rail", "lookout_stair_rail", "timber_stair_guard", "lookout_brace", "timber_brace", "lookout_cross_brace",
      "timber_cross_brace"
   };

   private WorldTabPolish() {
   }

   // LOW: after the tab's own list and the [ecology] insertions, before AlpineRegistration's LOWEST appends
   @SubscribeEvent(priority = EventPriority.LOW)
   public static void creative(BuildCreativeModeTabContentsEvent e) {
      if (!e.getTabKey().location().equals(WORLD)) {
         return;
      }
      try {
         for (String id : HIDDEN) {
            ItemStack s = stack(id);
            if (s != null && (e.getParentEntries().contains(s) || e.getSearchEntries().contains(s))) {
               e.remove(s, TabVisibility.PARENT_AND_SEARCH_TABS);
            }
         }
         String anchor = "pine_fence";
         for (String id : RAILINGS) {
            ItemStack s = stack(id);
            ItemStack a = stack(anchor);
            if (s == null || a == null || !e.getParentEntries().contains(s) || !e.getParentEntries().contains(a)) {
               continue;
            }
            e.remove(s, TabVisibility.PARENT_AND_SEARCH_TABS);
            TabPlacement.after(e, s.getItem(), anchor);
            anchor = id;
         }
      } catch (RuntimeException ex) {
         // a tab layout is cosmetic: never let it break the creative screen
      }
   }

   private static ItemStack stack(String id) {
      Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("frontierhunts", id));
      return item == Items.AIR ? null : new ItemStack(item);
   }
}
