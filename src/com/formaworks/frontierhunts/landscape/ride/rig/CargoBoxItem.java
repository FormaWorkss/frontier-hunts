package com.formaworks.frontierhunts.landscape.ride.rig;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * [atvfuel] Strap-on rear dry box for the ATV (27 slots). Use it on an ATV to fit it to the rear rack; unstrapping it
 * from the rack menu keeps everything inside (vanilla {@code minecraft:container} component, like a shulker box).
 * It can never be stored inside other container items, so contents cannot nest.
 */
public class CargoBoxItem extends Item {
   public CargoBoxItem(Item.Properties props) {
      super(props);
   }

   @Override
   public boolean canFitInsideContainerItems() {
      return false;
   }

   @Override
   public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
      ItemContainerContents c = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
      int stacks = 0;
      int shown = 0;
      for (ItemStack s : c.nonEmptyItems()) {
         stacks++;
         if (shown < 4) {
            shown++;
            lines.add(Component.translatable("container.shulkerBox.itemCount", s.getHoverName(), s.getCount()).withStyle(ChatFormatting.GRAY));
         }
      }
      if (stacks > shown) {
         lines.add(Component.translatable("container.shulkerBox.more", stacks - shown).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
      }
      if (stacks == 0) {
         lines.add(Component.translatable("item.frontierhunts.atv_cargo_box.tip_empty").withStyle(ChatFormatting.GRAY));
      }
      lines.add(Component.translatable("item.frontierhunts.atv_cargo_box.tip").withStyle(ChatFormatting.DARK_GRAY));
   }
}
