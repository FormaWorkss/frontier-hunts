package com.formaworks.frontierhunts.hunting;

import com.formaworks.frontierhunts.client.FieldGearScreen;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;

public final class NativeGearClientHook {
   private static final Map<Integer, NativeGear.Sync> PLAYERS = new HashMap<>();
   private static Object level;

   public static void receive(NativeGear.Sync var0) {
      ClientLevel var1 = Minecraft.getInstance().level;
      if (level != var1) {
         PLAYERS.clear();
         level = var1;
      }

      PLAYERS.put(var0.playerId(), var0);
      if (Minecraft.getInstance().screen instanceof FieldGearScreen var2) {
         var2.refresh();
      }
   }

   public static ItemStack pack(int var0) {
      NativeGear.Sync var1 = PLAYERS.get(var0);
      return var1 == null ? ItemStack.EMPTY : var1.pack();
   }

   public static ItemStack quiver(int var0) {
      NativeGear.Sync var1 = PLAYERS.get(var0);
      return var1 == null ? ItemStack.EMPTY : var1.quiver();
   }

   private NativeGearClientHook() {
   }
}
