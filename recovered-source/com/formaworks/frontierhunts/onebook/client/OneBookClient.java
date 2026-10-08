package com.formaworks.frontierhunts.onebook.client;

import com.formaworks.frontierhunts.client.AssignmentScreen;
import com.formaworks.frontierhunts.client.ExpeditionScreen;
import com.formaworks.frontierhunts.client.JournalScreen;
import com.formaworks.frontierhunts.guide.client.HandbookScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * [onebook] "Back to the Handbook". The Hunter's Journal, the Expedition journal and Ranger Assignments live inside the
 * Frontier Handbook now: opened from the Handbook's buttons, closing them (Esc, the close button, their hotkey, the
 * Assignments "Handbook" button) returns to the Handbook page they were opened from. Opened straight from the world
 * with J / N / K they close to the game as before. Moving between the books (Expedition -> Journal, Journal -> Field
 * School -> back) keeps the way home. The Field School already takes the Handbook as its parent screen.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class OneBookClient {
   /** a book has this long to arrive from the server after a Handbook button is pressed */
   private static final long ARM_MS = 8000L;
   private static HandbookScreen handbook;
   private static long armedAt;
   private static boolean active;

   private OneBookClient() {
   }

   /** Called by the Handbook right before one of its buttons asks the server for a book. */
   public static void leaving(HandbookScreen from) {
      handbook = from;
      armedAt = System.currentTimeMillis();
      active = false;
   }

   /** True while the open book was opened from the Handbook (its close / back returns there). */
   public static boolean fromHandbook() {
      return active && handbook != null;
   }

   /**
    * Hook for the books' {@code onClose()}: reopens the Handbook when this book was opened from it.
    * @return true when the Handbook was reopened (the caller must not close to the game)
    */
   public static boolean back(Screen from) {
      if (!fromHandbook()) {
         return false;
      }
      Minecraft mc = Minecraft.getInstance();
      HandbookScreen h = handbook;
      reset();
      if (mc.level == null || mc.player == null || !mc.player.isAlive()) {
         return false;
      }
      mc.setScreen(h);
      return true;
   }

   /** Drops the way back (the next close goes to the game), e.g. when leaving an Academy course. */
   public static void forget() {
      reset();
   }

   private static void reset() {
      handbook = null;
      active = false;
      armedAt = 0L;
   }

   private static boolean book(Screen s) {
      return s instanceof JournalScreen || s instanceof ExpeditionScreen || s instanceof AssignmentScreen;
   }

   @SubscribeEvent
   public static void opening(ScreenEvent.Opening e) {
      Screen next = e.getNewScreen();
      if (next instanceof HandbookScreen) {
         if (next != handbook || active) {
            reset();
         }
         return;
      }
      if (book(next)) {
         if (handbook != null && (active || System.currentTimeMillis() - armedAt < ARM_MS)) {
            active = true;
         } else {
            reset();
         }
      }
      // any other screen opened from a book (Field School, settings) returns to that book: keep the way home
   }

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post e) {
      if (handbook == null) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || active && mc.screen == null || !active && System.currentTimeMillis() - armedAt >= ARM_MS) {
         reset(); // the book closed to the game some other way, or never arrived
      }
   }
}
