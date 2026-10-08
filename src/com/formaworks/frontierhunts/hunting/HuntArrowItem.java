package com.formaworks.frontierhunts.hunting;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;

public final class HuntArrowItem extends FieldItem {
   private final boolean primitive;
   private final boolean legacy;

   public HuntArrowItem(boolean var1, boolean var2, Properties var3) {
      super(var3);
      this.primitive = var1;
      this.legacy = var2;
   }

   public Component getName(ItemStack var1) {
      return this.legacy
         ? Component.literal("Tracer Arrow (old)")
         : Component.literal((this.primitive ? "Primitive Arrow" : "Hunting Arrow") + " · " + ArrowTip.of(var1).title);
   }

   public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
      ArrowTip var5 = ArrowTip.of(var1);
      // [bows] the head is the first thing you read, with its numbers
      var3.add(
         Component.literal("Head: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(var5.title).withStyle(var5.tracer() ? ChatFormatting.GOLD : ChatFormatting.WHITE))
            .append(Component.literal(ArrowTip.fitted(var1) || this.legacy ? "" : " (stock)").withStyle(ChatFormatting.DARK_GRAY))
      );
      var3.add(
         Component.literal(
               String.format(
                  java.util.Locale.ROOT,
                  "Penetration %d%% · Bleeding %d%% · Recovery %d%%",
                  Math.round(var5.penetration * 100.0F),
                  Math.round(var5.bleed * 100.0F),
                  Math.round(var5.recovery * 100.0F)
               )
            )
            .withStyle(ChatFormatting.DARK_GREEN)
      );
      var3.add(Component.literal(var5.summary()).withStyle(ChatFormatting.GRAY));
      var3.add(Component.literal(this.primitive ? "Cedar shaft, turkey-feather fletching" : "Carbon shaft, low-profile vanes").withStyle(ChatFormatting.DARK_GRAY));
      var3.add(Component.literal("Change heads at the Reloading Bench: Fit arrowheads." /* [smalls] */).withStyle(ChatFormatting.DARK_GRAY));
      var3.add(Component.literal("Holding a bow: " + ArrowSupply.switchKeys() + " picks which arrow to draw.").withStyle(ChatFormatting.DARK_GRAY));
   }

   public boolean isFoil(ItemStack var1) {
      return false;
   }

   public void inventoryTick(ItemStack var1, Level var2, Entity var3, int var4, boolean var5) {
      if (this.legacy && !var2.isClientSide && var3 instanceof Player var6) {
         ItemStack var7 = ArrowTip.arrowStack(false, ArrowTip.TRACER_BROADHEAD, var1.getCount());
         Inventory var8 = var6.getInventory();

         for (int var9 = 0; var9 < var8.getContainerSize(); var9++) {
            if (var8.getItem(var9) == var1) {
               var8.setItem(var9, var7);
               return;
            }
         }
      }
   }
}
