package com.formaworks.frontierhunts.onebook;

import com.formaworks.frontierhunts.onboard.OnboardContent;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * [onebook] One book: the Frontier Handbook is the only book item. The Hunter's Journal and the Expedition Guide items
 * are legacy - still registered (old chests and inventories load), but hidden from the creative tabs, recipe viewers,
 * crafting and loot. Their screens and every page are unchanged and open from the Handbook (buttons) or J / N.
 *
 * <p>Old stacks are bound into the Handbook: on login and every 2 s, a legacy journal/guide in a hunter's inventory
 * becomes a Frontier Handbook when the hunter has none, otherwise it is simply removed. Nothing is lost: journal,
 * expedition, licence, Field School and Handbook progress are all stored per player in SavedData, never on the item
 * (the two items carry no data components). This also covers any old code path that still hands one out (the
 * {@code journal_replacement} command, the legacy {@code issueJournalOnJoin} option, Mara's old starting kit).
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class OneBook {
   /** The retired book items (ids). */
   public static final String[] LEGACY = {"hunter_journal", "expedition_guide"};
   private static final int SCAN_TICKS = 40;
   /** Hunters already told this session that their old book was bound into the Handbook. */
   private static final Set<UUID> TOLD = new HashSet<>();
   /** Per-hunter tick counter for the 2 s scan (the entity tickCount is advanced by the level, not by Player.tick). */
   private static final java.util.Map<UUID, Integer> TICKS = new java.util.HashMap<>();

   private OneBook() {
   }

   /** The Frontier Handbook item, or {@code fallback} before it is registered. */
   public static Item handbookOr(Item fallback) {
      return OnboardContent.HANDBOOK != null ? OnboardContent.HANDBOOK : fallback;
   }

   /** True for a Hunter's Journal or Expedition Guide stack (the legacy book items). */
   public static boolean legacy(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      }
      var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
      if (!"frontierhunts".equals(key.getNamespace())) {
         return false;
      }
      for (String id : LEGACY) {
         if (id.equals(key.getPath())) {
            return true;
         }
      }
      return false;
   }

   @SubscribeEvent
   public static void login(PlayerEvent.PlayerLoggedInEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         TOLD.remove(p.getUUID());
         bind(p);
      }
   }

   @SubscribeEvent
   public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
      TOLD.remove(e.getEntity().getUUID());
      TICKS.remove(e.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void tick(PlayerTickEvent.Post e) {
      if (e.getEntity() instanceof ServerPlayer p && !p.isSpectator()) {
         int t = TICKS.merge(p.getUUID(), 1, (a, b) -> (a + b) % SCAN_TICKS);
         if (t == 0) {
            bind(p);
         }
      }
   }

   /** Replaces legacy book stacks with one Frontier Handbook (or removes them when the hunter already has one). */
   static int bind(ServerPlayer p) {
      Item handbook = OnboardContent.HANDBOOK;
      if (handbook == null || handbook == Items.AIR) {
         return 0;
      }
      Inventory inv = p.getInventory();
      int size = inv.getContainerSize();
      boolean any = false;
      for (int i = 0; i < size; i++) {
         if (legacy(inv.getItem(i))) {
            any = true;
            break;
         }
      }
      if (!any) {
         return 0;
      }
      boolean has = inv.contains(s -> s.is(handbook)); // [1.1.5] item match only
      int n = 0;
      String first = null;
      for (int i = 0; i < size; i++) {
         ItemStack s = inv.getItem(i);
         if (!legacy(s)) {
            continue;
         }
         if (first == null) {
            first = s.getDescriptionId();
         }
         inv.setItem(i, has ? ItemStack.EMPTY : new ItemStack(handbook));
         has = true;
         n++;
      }
      if (n > 0) {
         inv.setChanged();
         if (TOLD.add(p.getUUID())) {
            p.sendSystemMessage(Component.translatable("onebook.frontierhunts.bound", Component.translatable(first),
               Component.keybind("key.frontierhunts.field_school"), Component.keybind("key.frontierhunts.journal"),
               Component.keybind("key.frontierhunts.expedition")).withStyle(ChatFormatting.GRAY));
         }
      }
      return n;
   }
}
