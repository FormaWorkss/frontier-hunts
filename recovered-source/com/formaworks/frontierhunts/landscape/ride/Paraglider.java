package com.formaworks.frontierhunts.landscape.ride;

import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class Paraglider extends Item {
   public static volatile BiConsumer<Player, InteractionHand> clientLaunch = (var0, var1) -> {
   };

   public Paraglider(Properties var1) {
      super(var1);
   }

   public static boolean isOpen(ItemStack var0) {
      return !var0.isEmpty() && var0.getItem() instanceof Paraglider && Boolean.TRUE.equals(var0.get((DataComponentType)RideContent.WING_OPEN.get()));
   }

   static void setOpen(ItemStack var0, boolean var1) {
      if (var1) {
         var0.set((DataComponentType)RideContent.WING_OPEN.get(), true);
      } else {
         var0.remove((DataComponentType)RideContent.WING_OPEN.get());
      }
   }

   public static ItemStack held(Player var0) {
      ItemStack var1 = var0.getMainHandItem();
      if (var1.getItem() instanceof Paraglider) {
         return var1;
      } else {
         ItemStack var2 = var0.getOffhandItem();
         return var2.getItem() instanceof Paraglider ? var2 : ItemStack.EMPTY;
      }
   }

   public static InteractionHand heldHand(Player var0) {
      return var0.getMainHandItem().getItem() instanceof Paraglider ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
   }

   public static boolean gliding(Player var0) {
      return isOpen(held(var0));
   }

   public static double clearance(Player var0, double var1) {
      Level var3 = var0.level();
      MutableBlockPos var4 = new MutableBlockPos();
      int var5 = var0.getBlockX();
      int var6 = var0.getBlockZ();
      double var7 = var0.getY();

      for (int var9 = 0; (double)var9 <= var1; var9++) {
         var4.set(var5, (int)Math.floor(var7) - var9, var6);
         BlockState var10 = var3.getBlockState(var4);
         if (!var10.getCollisionShape(var3, var4).isEmpty() || !var10.getFluidState().isEmpty()) {
            return Math.max(0.0, var7 - (double)(var4.getY() + 1));
         }
      }

      return var1;
   }

   public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
      ItemStack var4 = var2.getItemInHand(var3);
      if (isOpen(var4)) {
         if (!var1.isClientSide) {
            GliderServer.fold(var2, var4, false);
         } else {
            setOpen(var4, false);
         }

         return InteractionResultHolder.sidedSuccess(var4, var1.isClientSide);
      } else {
         boolean var5 = var2.onGround() || var2.isInWater() || var2.isPassenger() || var2.getAbilities().flying || var2.isFallFlying();
         if (!var5) {
            if (clearance(var2, 3.0) < 2.5) {
               if (var1.isClientSide) {
                  var2.displayClientMessage(Component.translatable("message.frontierhunts.glider_too_low"), true);
               } else {
                  resync(var2);
               }

               return InteractionResultHolder.fail(var4);
            } else {
               if (!var1.isClientSide) {
                  GliderServer.open(var2, var4);
               } else {
                  setOpen(var4, true);
               }

               return InteractionResultHolder.sidedSuccess(var4, var1.isClientSide);
            }
         } else {
            if (var1.isClientSide && var2.onGround()) {
               clientLaunch.accept(var2, var3);
            } else if (!var1.isClientSide) {
               resync(var2);
            }

            return InteractionResultHolder.pass(var4);
         }
      }
   }

   private static void resync(Player var0) {
      if (var0 instanceof ServerPlayer var1) {
         var1.inventoryMenu.sendAllDataToRemote();
      }
   }

   public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
      var3.add(Component.translatable("item.frontierhunts.paraglider.tip1").withStyle(ChatFormatting.GRAY));
      var3.add(Component.translatable("item.frontierhunts.paraglider.tip2").withStyle(ChatFormatting.GRAY));
      var3.add(Component.translatable("item.frontierhunts.paraglider.tip3").withStyle(ChatFormatting.DARK_GRAY));
   }

   public boolean isFoil(ItemStack var1) {
      return false;
   }
}
