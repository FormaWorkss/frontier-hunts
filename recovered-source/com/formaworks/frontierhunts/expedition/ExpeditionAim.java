package com.formaworks.frontierhunts.expedition;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class ExpeditionAim {
   private static final Map<ServerPlayer, ExpeditionAim.Held> HELD = new WeakHashMap<>();

   public static void set(ServerPlayer var0, boolean var1) {
      if (var1 && eligible(var0)) {
         HELD.put(var0, new ExpeditionAim.Held(var0.getMainHandItem(), var0.level().dimension().location().toString(), var0.level().getGameTime() + 45L));
      } else {
         HELD.remove(var0);
      }
   }

   private static boolean eligible(ServerPlayer var0) {
      return var0.isAlive()
         && !var0.isSpectator()
         && !var0.isUnderWater()
         && var0.containerMenu == var0.inventoryMenu
         && var0.getMainHandItem().getItem() instanceof ExpeditionWeapon var1
         && !var1.weapon.bow
         && ExpeditionWeapon.data(var0.getMainHandItem()).getLong("reload_until") == 0L;
   }

   public static boolean aiming(ServerPlayer var0) {
      ExpeditionAim.Held var1 = HELD.get(var0);
      return var1 != null
         && var1.stack() == var0.getMainHandItem()
         && eligible(var0)
         && var1.dimension().equals(var0.level().dimension().location().toString())
         && var0.level().getGameTime() < var1.until();
   }

   private ExpeditionAim() {
   }

   private static record Held(ItemStack stack, String dimension, long until) {
   }
}
