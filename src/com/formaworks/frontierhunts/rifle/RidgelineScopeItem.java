package com.formaworks.frontierhunts.rifle;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** [rifle] The Ridgeline bolt rifle's factory 3-9x hunting scope, as a part for the Attachment Workbench. */
public final class RidgelineScopeItem extends Item {
   public RidgelineScopeItem() {
      super(new Properties().stacksTo(1));
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
      lines.add(Component.translatable("item.frontierhunts.ridgeline_scope.desc").withStyle(ChatFormatting.GRAY));
      lines.add(Component.translatable("item.frontierhunts.ridgeline_scope.fit").withStyle(ChatFormatting.DARK_GRAY));
   }
}
