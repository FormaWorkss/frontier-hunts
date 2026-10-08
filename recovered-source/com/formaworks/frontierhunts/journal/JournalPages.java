package com.formaworks.frontierhunts.journal;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * [journal] Extra Hunter's Journal pages from other workstreams (e.g. a Survival page), with no dependency on them.
 *
 * <p>Two halves, so dedicated servers never touch client classes:
 * <ul>
 * <li>common (both sides, during common setup): {@link #register(String, String, String, int, Function)} declares the page
 *     (id, title lang key, icon item id, sidebar order) and optionally a server-side data provider; its tag is sent with the
 *     journal data whenever the owner has the journal open (keep it small, under ~8 KB);</li>
 * <li>client: {@code journal.client.JournalPageView.Views.register(id, view)} supplies the renderer; the page shows as a
 *     sidebar tab once both halves exist. Pages render in the journal's own content area (scroll, scissor and
 *     palette are handled by the journal; {@code journal.client.JournalUi} has the palette and helpers).</li>
 * </ul>
 * Example (survival): {@code JournalPages.register("survival", "journal.frontierhunts.page.survival", "frontierhunts:cooked_venison", 50,
 * p -> SurvivalApi.journalTag(p));} plus a client view drawing warmth/nutrition from that tag.
 */
public final class JournalPages {
   public record Page(String id, String titleKey, String icon, int order, Function<ServerPlayer, CompoundTag> data) {
   }

   private static final List<Page> PAGES = new ArrayList<>();

   private JournalPages() {
   }

   public static synchronized void register(String id, String titleKey, String icon, int order, Function<ServerPlayer, CompoundTag> data) {
      if (id == null || !id.matches("[a-z0-9_]{1,32}")) {
         throw new IllegalArgumentException("Bad journal page id " + id);
      }
      PAGES.removeIf(p -> p.id().equals(id));
      PAGES.add(new Page(id, titleKey == null ? id : titleKey, icon == null ? "" : icon, order, data));
      PAGES.sort(Comparator.comparingInt(Page::order));
      if (PAGES.size() > 12) {
         throw new IllegalStateException("Too many journal pages");
      }
   }

   public static synchronized List<Page> all() {
      return List.copyOf(PAGES);
   }

   /** Data tags of all pages for this hunter (server). */
   static List<JournalNet.PageData> data(ServerPlayer p) {
      List<JournalNet.PageData> out = new ArrayList<>();
      for (Page page : all()) {
         CompoundTag t = null;
         if (page.data() != null) {
            try {
               t = page.data().apply(p);
            } catch (RuntimeException e) {
               LogUtils.getLogger().warn("Frontier Hunts journal: page {} data failed", page.id(), e);
            }
         }
         out.add(new JournalNet.PageData(page.id(), t == null ? new CompoundTag() : t.copy()));
      }
      return out;
   }
}
