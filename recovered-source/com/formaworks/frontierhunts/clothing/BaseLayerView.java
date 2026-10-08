package com.formaworks.frontierhunts.clothing;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.item.ItemStack;

/**
 * [clothing] What the client last heard from the server: every visible player's base layer (by entity id) and the local
 * player's own scent state (sweat, wet clothes, the scent-cover spray). Plain data, no client classes, so common code
 * ({@link Scent}, tooltips, {@link com.formaworks.frontierhunts.survival.Clothing}) can read it on either side. Filled
 * and cleared on the client thread only ({@code clothing.client.BaseLayerClient}).
 */
public final class BaseLayerView {
   static final ItemStack[] NONE = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
   /** by entity id; fastutil, so the per-frame lookups (renderer, scent cone) never box the id */
   private static final Int2ObjectOpenHashMap<ItemStack[]> PLAYERS = new Int2ObjectOpenHashMap<>();
   /** local player: sweat 0..1, clothes wet 0..1, spray active until this game time */
   public static volatile float sweat, wet;
   public static volatile long sprayUntil;

   private BaseLayerView() {
   }

   static ItemStack[] stacks(int entityId) {
      ItemStack[] s = PLAYERS.get(entityId);
      return s == null ? NONE : s;
   }

   public static void put(int entityId, ItemStack hood, ItemStack top, ItemStack trousers) {
      if (hood.isEmpty() && top.isEmpty() && trousers.isEmpty()) {
         PLAYERS.remove(entityId);
      } else {
         PLAYERS.put(entityId, new ItemStack[]{hood, top, trousers});
      }
   }

   public static void clear() {
      PLAYERS.clear();
      sweat = 0F;
      wet = 0F;
      sprayUntil = 0L;
   }
}
