package com.formaworks.frontierhunts.landscape.ride.rig;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** [atvfuel] Pair of steel jerry-can cradles with ratchet straps that bolt to the outer rails of the ATV's rear rack. */
public class CanCarrierItem extends Item {
   public CanCarrierItem(Item.Properties props) {
      super(props);
   }

   @Override
   public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
      lines.add(Component.translatable("item.frontierhunts.atv_can_carrier.tip").withStyle(ChatFormatting.GRAY));
      lines.add(Component.translatable("item.frontierhunts.atv_can_carrier.tip2").withStyle(ChatFormatting.DARK_GRAY));
   }
}
