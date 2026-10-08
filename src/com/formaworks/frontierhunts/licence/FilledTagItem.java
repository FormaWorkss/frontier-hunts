package com.formaworks.frontierhunts.licence;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** [licence] A notched tag: the record of one legally taken big-game animal (keep it, or hang it in the lodge). */
public class FilledTagItem extends Item {
   public FilledTagItem(Item.Properties p) {
      super(p);
   }

   @Override
   public Component getName(ItemStack stack) {
      FilledTag t = stack.get(LicenceContent.FILLED.get());
      return t == null ? super.getName(stack) : Component.translatable("item.frontierhunts.filled_tag.named", t.title());
   }

   @Override
   public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
      FilledTag t = stack.get(LicenceContent.FILLED.get());
      if (t == null) {
         tip.add(Component.translatable("item.frontierhunts.filled_tag.blank").withStyle(ChatFormatting.GRAY));
         return;
      }
      String what = t.weight() + (t.points() > 0 ? " · " + t.points() + " points" : "");
      tip.add(Component.literal(what).withStyle(ChatFormatting.GOLD));
      tip.add(Component.translatable("item.frontierhunts.filled_tag.date", t.date()).withStyle(ChatFormatting.GRAY));
      tip.add(Component.translatable("item.frontierhunts.filled_tag.place", t.place()).withStyle(ChatFormatting.GRAY));
      tip.add(Component.translatable("item.frontierhunts.filled_tag.hunter", t.hunter(), Regulations.periodTitle(t.period())).withStyle(ChatFormatting.DARK_GRAY));
   }
}
