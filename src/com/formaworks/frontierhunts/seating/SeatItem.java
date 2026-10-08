package com.formaworks.frontierhunts.seating;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** [onboard2] Seat block item with a two-line tooltip (what it is, how to sit). */
public class SeatItem extends BlockItem {
   private final SeatKind kind;

   public SeatItem(SeatBlock block, Properties props) {
      super(block, props);
      this.kind = block.kind;
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
      lines.add(Component.translatable("seat.frontierhunts.tip." + this.kind.id).withStyle(ChatFormatting.GRAY));
      lines.add(Component.translatable("seat.frontierhunts.tip.sit").withStyle(ChatFormatting.DARK_GRAY));
   }
}
