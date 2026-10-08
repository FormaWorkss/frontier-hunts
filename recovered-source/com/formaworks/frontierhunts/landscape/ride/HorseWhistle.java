package com.formaworks.frontierhunts.landscape.ride;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;

public class HorseWhistle extends Item {
   public HorseWhistle(Properties var1) {
      super(var1);
   }

   public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
      ItemStack var4 = var2.getItemInHand(var3);
      var1.playSound(
         var2,
         var2.getX(),
         var2.getEyeY(),
         var2.getZ(),
         (SoundEvent)RideContent.SND_WHISTLE.get(),
         SoundSource.PLAYERS,
         2.2F,
         0.97F + var2.getRandom().nextFloat() * 0.06F
      );
      if (var2 instanceof ServerPlayer var5) {
         Horses.whistle(var5);
      }

      var2.getCooldowns().addCooldown(this, 50);
      return InteractionResultHolder.sidedSuccess(var4, var1.isClientSide);
   }

   public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
      var3.add(Component.translatable("item.frontierhunts.horse_whistle.tip").withStyle(ChatFormatting.GRAY));
   }
}
