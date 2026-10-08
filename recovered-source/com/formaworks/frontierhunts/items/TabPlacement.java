package com.formaworks.frontierhunts.items;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * [items] Places late-registered items next to related entries of a Frontier Hunts creative tab instead of at the end.
 * Never throws: an item that is already listed is skipped (no duplicates), and when none of the anchors is in the
 * tab the item is simply appended.
 */
public final class TabPlacement {
   private TabPlacement() {
   }

   /** Inserts {@code item} right after the first anchor (frontierhunts item ids) that the tab already lists. */
   public static void after(BuildCreativeModeTabContentsEvent e, ItemLike item, String... anchors) {
      ItemStack stack = new ItemStack(item);
      if (stack.isEmpty() || e.getParentEntries().contains(stack) || e.getSearchEntries().contains(stack)) {
         return;
      }
      for (String id : anchors) {
         Item anchor = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("frontierhunts", id));
         if (anchor == Items.AIR) {
            continue;
         }
         ItemStack a = new ItemStack(anchor);
         boolean parent = e.getParentEntries().contains(a);
         boolean search = e.getSearchEntries().contains(a);
         if (!parent && !search) {
            continue;
         }
         if (parent) {
            e.insertAfter(a, stack, TabVisibility.PARENT_TAB_ONLY);
         } else {
            e.accept(stack, TabVisibility.PARENT_TAB_ONLY);
         }
         if (search) {
            e.insertAfter(a, stack, TabVisibility.SEARCH_TAB_ONLY);
         } else {
            e.accept(stack, TabVisibility.SEARCH_TAB_ONLY);
         }
         return;
      }
      e.accept(stack, TabVisibility.PARENT_AND_SEARCH_TABS);
   }
}
