package com.formaworks.frontierhunts.workshop;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class OpticUpgrade {
   public static boolean fitted(ItemStack var0) {
      CustomData var1 = (CustomData)var0.get(DataComponents.CUSTOM_DATA);
      return var1 != null && var1.copyTag().getBoolean("frontier_four_power");
   }

   public static void set(ItemStack var0, boolean var1) {
      CompoundTag var2 = ((CustomData)var0.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).copyTag();
      if (var1) {
         var2.putBoolean("frontier_four_power", true);
      } else {
         var2.remove("frontier_four_power");
      }

      var0.set(DataComponents.CUSTOM_DATA, CustomData.of(var2));
   }

   private OpticUpgrade() {
   }
}
