package com.formaworks.frontierhunts.hunting;

import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

public final class DeerSpawnEgg extends DeferredSpawnEggItem {
   public DeerSpawnEgg(boolean var1) {
      this(GameSpecies.WHITETAIL, var1, var1 ? 7426362 : 10189657, var1 ? 14073745 : 15195845);
   }

   public DeerSpawnEgg(GameSpecies var1, boolean var2, int var3, int var4) {
      super((Supplier)HuntEntities.GAME.get(var1), var3, var4, properties(var1, var2));
   }

   private static Properties properties(GameSpecies var0, boolean var1) {
      CompoundTag var2 = new CompoundTag();
      var2.putString("id", "frontierhunts:" + var0.id);
      var2.putString("frontier_spawn_sex", var1 ? "buck" : "doe");
      return new Properties().component(DataComponents.ENTITY_DATA, CustomData.of(var2));
   }
}
