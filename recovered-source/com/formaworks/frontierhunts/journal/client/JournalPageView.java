package com.formaworks.frontierhunts.journal.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;

/**
 * [journal] Client renderer of an extra journal page (see {@code journal.JournalPages}). The journal calls
 * {@link #height} when it lays the page out (on open, resize and data change) and {@link #render} every frame for the
 * visible part; {@code data} is the page's server tag (empty until the first sync). Draw inside {@code x..x+width}
 * starting at {@code y}; the journal scrolls and clips. Use {@link JournalUi} for the journal's palette and text.
 */
public interface JournalPageView {
   int height(CompoundTag data, int width);

   void render(GuiGraphics g, CompoundTag data, int x, int y, int width, int mouseX, int mouseY);

   /** [licence] A left click inside the page (same coordinates as {@link #render}); true when it was handled. */
   default boolean click(CompoundTag data, int x, int y, int width, double mouseX, double mouseY) {
      return false;
   }

   /** Registry (client only). */
   final class Views {
      private static final Map<String, JournalPageView> VIEWS = new HashMap<>();

      private Views() {
      }

      public static synchronized void register(String id, JournalPageView view) {
         VIEWS.put(id, view);
      }

      public static synchronized JournalPageView get(String id) {
         return VIEWS.get(id);
      }
   }
}
