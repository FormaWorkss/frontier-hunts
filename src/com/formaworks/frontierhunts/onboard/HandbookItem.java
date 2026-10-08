package com.formaworks.frontierhunts.onboard;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * [onboard] The Frontier Handbook: right-click to open the book. The server answers with the hunter's fresh Handbook
 * state and the client opens the screen (like the Hunter's Journal item), so no client class is referenced here.
 */
public final class HandbookItem extends Item {
   public HandbookItem(Properties props) {
      super(props);
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      if (player instanceof ServerPlayer sp) {
         Onboarding.open(sp);
      }
      return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
      lines.add(Component.translatable("item.frontierhunts.frontier_handbook.tip").withStyle(ChatFormatting.GRAY));
      lines.add(Component.translatable("item.frontierhunts.frontier_handbook.use").withStyle(ChatFormatting.DARK_GRAY));
   }
}
