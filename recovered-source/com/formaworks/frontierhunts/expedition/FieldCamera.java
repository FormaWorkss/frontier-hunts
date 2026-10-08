package com.formaworks.frontierhunts.expedition;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Field Camera. [1.4.0] Retired: the Field Phone's Camera app replaced it (walk while you frame, selfies, poses,
 * filters, photos you can text). Like the retired benches and base station it stays registered so old worlds and
 * inventories load, but it is gone from creative and has no recipe; using one puts the phone camera up (no phone
 * needed for a camera you already own).
 */
public final class FieldCamera extends Item {
   /** the old viewfinder screen (set by the client) */
   public static Runnable open = () -> {
   };
   /** [1.4.0] the phone camera (set by the client): used in place of the old viewfinder */
   public static Runnable phoneCamera;

   public FieldCamera() {
      super(new Item.Properties().stacksTo(1));
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      if (level.isClientSide) {
         (phoneCamera != null ? phoneCamera : open).run();
      }
      return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
   }

   @Override
   public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
      lines.add(Component.literal("Retired: the Field Phone's Camera app replaced it").withStyle(ChatFormatting.GOLD));
      lines.add(Component.literal("Use: puts up the phone camera · walk to frame, right-click for selfies").withStyle(ChatFormatting.GRAY));
   }
}
