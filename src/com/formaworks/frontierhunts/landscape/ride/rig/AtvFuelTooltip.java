package com.formaworks.frontierhunts.landscape.ride.rig;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** [atvfuel] Fuel line on the ATV item tooltip. */
public final class AtvFuelTooltip {
   private AtvFuelTooltip() {
   }

   public static void append(ItemStack stack, List<Component> lines) {
      Float f = stack.get(RigContent.FUEL.get());
      if (f != null) {
         lines.add(Component.translatable("item.frontierhunts.atv.fuel", String.format("%.1f", f), String.format("%.0f", AtvFuel.TANK))
            .withStyle(ChatFormatting.GOLD));
      } else {
         lines.add(Component.translatable("item.frontierhunts.atv.fuel_new").withStyle(ChatFormatting.GOLD));
      }
   }
}
