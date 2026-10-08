package com.formaworks.frontierhunts.tracking.hound;

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
 * [tracking] The Hound Lead: the first use brings you your own tracking hound (one per player). After that: use to call
 * it, sneak-use to send it to heel, and use while aiming at blood or prints (client sends the mark) to put it on the track.
 */
public class HoundLeadItem extends Item {
   public HoundLeadItem(Item.Properties p) {
      super(p);
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      if (!level.isClientSide && player instanceof ServerPlayer sp) {
         HoundCommands.useLead(sp);
      } else if (level.isClientSide && !player.isShiftKeyDown()) {
         HoundNet.leadWheel.test(player); // [hound3] the command wheel (client hook; nothing on a server)
      }
      return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
   }

   @Override
   public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
      tip.add(Component.translatable("item.frontierhunts.hound_lead.tip1").withStyle(ChatFormatting.GRAY));
      tip.add(Component.translatable("item.frontierhunts.hound_lead.tip2").withStyle(ChatFormatting.GRAY));
      tip.add(Component.translatable("item.frontierhunts.hound_lead.tip3").withStyle(ChatFormatting.GRAY));
   }
}
