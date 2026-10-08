package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.FieldGearActions;
import com.formaworks.frontierhunts.hunting.ArrowSupply;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class WornGearCache {
   private static final Map<Integer, WornGearCache.Entry> CACHE = new HashMap<>();
   private static Object level;

   private static WornGearCache.Entry get(Player var0) {
      Minecraft var1 = Minecraft.getInstance();
      long var2 = var1.level == null ? 0L : var1.level.getGameTime();
      if (level != var1.level) {
         CACHE.clear();
         level = var1.level;
      }

      WornGearCache.Entry var4 = CACHE.get(var0.getId());
      if (var4 == null || var4.tick != var2) {
         if (CACHE.size() > 128) {
            CACHE.clear();
         }

         var4 = new WornGearCache.Entry(var2, ArrowSupply.wornQuiver(var0), FieldGearActions.wornPack(var0), ArrowSupply.peek(var0));
         CACHE.put(var0.getId(), var4);
      }

      return var4;
   }

   static ItemStack quiver(Player var0) {
      return get(var0).quiver;
   }

   static ItemStack pack(Player var0) {
      return get(var0).pack;
   }

   static ArrowSupply.Shot shot(Player var0) {
      return get(var0).shot;
   }

   private WornGearCache() {
   }

   private static record Entry(long tick, ItemStack quiver, ItemStack pack, ArrowSupply.Shot shot) {
   }
}
