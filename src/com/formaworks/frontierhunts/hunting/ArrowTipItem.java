package com.formaworks.frontierhunts.hunting;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;

public final class ArrowTipItem extends FieldItem {
   public final ArrowTip tip;

   public ArrowTipItem(ArrowTip var1, Properties var2) {
      super(var2);
      this.tip = var1;
   }

   public Component getName(ItemStack var1) {
      return Component.literal(this.tip.title);
   }

   public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
      var3.add(Component.literal(this.tip.summary()).withStyle(this.tip.tracer() ? ChatFormatting.GOLD : ChatFormatting.GRAY));
      var3.add(
         Component.literal(
               String.format(Locale.ROOT, "Penetration %d%% · Bleeding %d%% · Recovery %d%%", Math.round(this.tip.penetration * 100.0F), Math.round(this.tip.bleed * 100.0F), Math.round(this.tip.recovery * 100.0F))
            )
            .withStyle(ChatFormatting.DARK_GREEN)
      );
      // [smalls] how a head gets onto an arrow: the Reloading Bench's Fit arrowheads tab, or new arrows made with it
      var3.add(Component.literal("Fit at the Reloading Bench: Fit arrowheads.").withStyle(ChatFormatting.DARK_GRAY));
      var3.add(Component.literal("Or make arrows with this head on its Arrows tab.").withStyle(ChatFormatting.DARK_GRAY));
   }
}
