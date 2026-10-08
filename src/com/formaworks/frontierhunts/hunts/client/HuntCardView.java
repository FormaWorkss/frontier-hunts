package com.formaworks.frontierhunts.hunts.client;

import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.hunts.HuntBook;
import com.formaworks.frontierhunts.hunts.HuntRewardsText;
import com.formaworks.frontierhunts.journal.HunterRecord;
import com.formaworks.frontierhunts.journal.client.JournalIcons;
import com.formaworks.frontierhunts.journal.client.JournalUi;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * [hunts] The species hunt card in the Hunter's Journal (Species page → click a species): header with the species
 * track, progress pips and the master title, the hunter's record for it, how the animal is really hunted, where it lives
 * and its contract season, then the five milestones as a checklist (rule, progress, rewards, gear it needs). Built as
 * rows of pre-laid-out text (the journal screen hosts them in its own scroll view); frames only draw.
 */
public final class HuntCardView {
   @FunctionalInterface
   public interface Draw {
      void draw(GuiGraphics g, int x, int y, int w, int mx, int my, boolean hover);
   }

   @FunctionalInterface
   public interface Click {
      boolean click(double mx, double my, int x, int y, int w);
   }

   /** One row of the card: height, painter, optional tooltip and click. */
   public record Line(int h, Draw draw, List<FormattedCharSequence> tip, Click click) {
   }

   static final int SCOUT = 0xFF5F7A52, TAKE = 0xFF8F6E2F, TECH = 0xFF3F6278, QUALITY = 0xFF7D4F2A, MASTER = 0xFFB99859;
   private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

   private HuntCardView() {
   }

   private static String tr(String key, Object... args) {
      return JournalUi.tr(key, args);
   }

   // ---------------------------------------------------------------------------------------------- table helpers

   public static int done(HunterRecord r, HuntBook.Hunt h) {
      int n = 0;
      for (HuntBook.Milestone m : h.milestones()) {
         if (r.done.contains(m.id())) {
            n++;
         }
      }
      return n;
   }

   public static boolean mastered(HunterRecord r, HuntBook.Hunt h) {
      return r.done.contains(h.master().id());
   }

   /** Five progress pips ending at {@code right} (a gold medal when the hunt is mastered). Width {@link #PIPS_W}. */
   public static void pips(GuiGraphics g, HunterRecord r, HuntBook.Hunt h, int right, int y) {
      if (mastered(r, h)) {
         HuntIcons.draw(g, "medal", right - 12, y - 4, 12, false, 1.0F);
         return;
      }
      int n = h.milestones().size();
      for (int i = 0; i < n; i++) {
         float cx = right - (n - i) * 6 + 2.5F;
         boolean d = r.done.contains(h.milestones().get(i).id());
         if (d) {
            FrontierUi.circle(g, cx, y + 2.5F, 2.4F, i == n - 1 ? MASTER : JournalUi.GREEN);
         } else {
            FrontierUi.circle(g, cx, y + 2.5F, 2.4F, 0xFF9C9480);
            FrontierUi.circle(g, cx, y + 2.5F, 1.5F, JournalUi.PAPER);
         }
      }
   }

   public static final int PIPS_W = 30;

   public static String tierLabel(HuntBook.Tier t) {
      return tr("hunts.frontierhunts.tier." + t.key());
   }

   public static int tierColor(HuntBook.Tier t) {
      return switch (t) {
         case SCOUT -> SCOUT;
         case TAKE -> TAKE;
         case TECHNIQUE -> TECH;
         case QUALITY -> QUALITY;
         case MASTER -> MASTER;
      };
   }

   static String itemName(String id) {
      ResourceLocation rl = ResourceLocation.tryParse(id);
      if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
         return id;
      }
      Item it = BuiltInRegistries.ITEM.get(rl);
      return it == Items.AIR ? id : it.getDescription().getString();
   }

   /** "Grunt Tube or Doe Bleat Call" for an alternatives list "a|b". */
   static String needsText(String spec) {
      List<String> names = new ArrayList<>();
      for (String s : spec.split("\\|")) {
         names.add(itemName(s));
      }
      if (names.size() <= 2) {
         return String.join(" " + tr("hunts.frontierhunts.or") + " ", names);
      }
      return String.join(", ", names.subList(0, names.size() - 1)) + " " + tr("hunts.frontierhunts.or") + " " + names.get(names.size() - 1);
   }

   static String months(int mask) {
      // contiguous runs, wrapping over the new year: "Dec–Mar", "May–Jun · Sep–Oct"
      List<String> runs = new ArrayList<>();
      if (mask == 0xFFF) {
         return tr("hunts.frontierhunts.year_round");
      }
      int start = -1;
      for (int k = 0; k < 12; k++) {
         if ((mask >> k & 1) != 0 && (mask >> Math.floorMod(k - 1, 12) & 1) == 0) {
            start = k;
            int end = k;
            while ((mask >> Math.floorMod(end + 1, 12) & 1) != 0 && Math.floorMod(end + 1, 12) != start) {
               end = Math.floorMod(end + 1, 12);
            }
            runs.add(end == start ? MONTHS[start] : MONTHS[start] + "–" + MONTHS[end]);
         }
      }
      return String.join(" · ", runs);
   }

   // ---------------------------------------------------------------------------------------------- the card

   /**
    * @param back    "all species" action
    * @param go      opens another species' card (previous / next)
    */
   public static List<Line> card(HunterRecord r, HuntBook.Hunt h, int bodyW, Runnable back, java.util.function.Consumer<String> go) {
      List<Line> out = new ArrayList<>();
      String sp = h.species();
      List<HuntBook.Hunt> all = HuntBook.hunts();
      int idx = all.indexOf(h);
      String prev = all.get(Math.floorMod(idx - 1, all.size())).species();
      String next = all.get(Math.floorMod(idx + 1, all.size())).species();

      // ---- navigation row (species names on the arrows when there is room, plain arrows on narrow pages)
      FormattedCharSequence backS = JournalUi.seq("‹ " + tr("hunts.frontierhunts.card.back"), FrontierUi.Size.SMALL);
      int bw = JournalUi.width(backS) + 14;
      FormattedCharSequence prevS = JournalUi.fit("‹ " + tr("entity.frontierhunts." + prev), 92, FrontierUi.Size.SMALL);
      FormattedCharSequence nextS = JournalUi.fit(tr("entity.frontierhunts." + next) + " ›", 92, FrontierUi.Size.SMALL);
      if (bw + JournalUi.width(prevS) + JournalUi.width(nextS) + 24 + 12 > bodyW) {
         prevS = JournalUi.seq("‹", FrontierUi.Size.SMALL);
         nextS = JournalUi.seq("›", FrontierUi.Size.SMALL);
      }
      FormattedCharSequence fPrev = prevS, fNext = nextS;
      int pw = Math.max(16, JournalUi.width(prevS) + 12), nw = Math.max(16, JournalUi.width(nextS) + 12);
      out.add(new Line(20, (g, x, y, w, mx, my, hv) -> {
         boolean ob = mx >= x && mx < x + bw && my >= y + 2 && my < y + 17;
         JournalUi.button(g, x, y + 2, bw, 15, backS, ob, false, true);
         int nx = x + w - nw, px = nx - 4 - pw;
         boolean op = mx >= px && mx < px + pw && my >= y + 2 && my < y + 17;
         boolean on = mx >= nx && mx < nx + nw && my >= y + 2 && my < y + 17;
         FrontierUi.rect(g, px, y + 2, pw, 15, 2.0F, op ? 0xFFD6CDB4 : 0xFFE2D9C2);
         JournalUi.draw(g, fPrev, px + (pw - JournalUi.width(fPrev)) / 2, y + 6, JournalUi.INK);
         FrontierUi.rect(g, nx, y + 2, nw, 15, 2.0F, on ? 0xFFD6CDB4 : 0xFFE2D9C2);
         JournalUi.draw(g, fNext, nx + (nw - JournalUi.width(fNext)) / 2, y + 6, JournalUi.INK);
      }, null, (mx, my, x, y, w) -> {
         if (my < y + 2 || my >= y + 17) {
            return false;
         }
         if (mx >= x && mx < x + bw) {
            back.run();
            return true;
         }
         int nx = x + w - nw, px = nx - 4 - pw;
         if (mx >= nx && mx < nx + nw) {
            go.accept(next);
            return true;
         }
         if (mx >= px && mx < px + pw) {
            go.accept(prev);
            return true;
         }
         return false;
      }));

      // ---- header card: track, name, tagline, progress pips; the master title on its own line below
      int done = done(r, h);
      int total = h.milestones().size();
      boolean master = mastered(r, h);
      FormattedCharSequence name = JournalUi.fit(tr("entity.frontierhunts." + sp), bodyW - 52, FrontierUi.Size.TITLE);
      FormattedCharSequence prog = JournalUi.fit(tr("hunts.frontierhunts.card.progress", done, total), bodyW - 52 - total * 15 - 6, FrontierUi.Size.SMALL);
      String titleText = tr(HuntBook.titleKey(sp));
      FormattedCharSequence chip = JournalUi.fit(master ? "★ " + titleText : tr("hunts.frontierhunts.card.title_locked", titleText), bodyW - 60, FrontierUi.Size.SMALL);
      FormattedCharSequence tagline = JournalUi.fit(tr("hunts.frontierhunts." + sp + ".tagline"), bodyW - 52, FrontierUi.Size.SMALL);
      String trackIcon = JournalIcons.has("sp_" + sp) ? "sp_" + sp : "species";
      out.add(new Line(68, (g, x, y, w, mx, my, hv) -> {
         FrontierUi.rect(g, x - 2, y + 2, w + 4, 63, 3.0F, master ? 0x26B99859 : 0x14000000);
         FrontierUi.rect(g, x - 2, y + 2, 2, 63, 1.0F, master ? MASTER : JournalUi.GOLD_DARK);
         JournalIcons.draw(g, trackIcon, x + 5, y + 10, 32, false, 1.0F);
         JournalUi.draw(g, name, x + 44, y + 7, JournalUi.INK);
         JournalUi.draw(g, tagline, x + 44, y + 23, JournalUi.MUTED);
         int px = x + 44;
         for (int i = 0; i < total; i++) {
            boolean d = r.done.contains(h.milestones().get(i).id());
            FrontierUi.rect(g, px + i * 15, y + 35, 12, 6, 3.0F, d ? tierColor(h.milestones().get(i).tier()) : 0xFFD3CCB6);
         }
         JournalUi.draw(g, prog, px + total * 15 + 4, y + 34, JournalUi.INK_BROWN);
         int cw = JournalUi.width(chip) + 12;
         FrontierUi.rect(g, px, y + 47, cw, 13, 6.5F, master ? MASTER : 0xFFD6CEB6);
         if (master) {
            HuntIcons.draw(g, "medal", px + cw + 3, y + 45, 16, false, 1.0F);
         }
         JournalUi.draw(g, chip, px + 6, y + 50, master ? JournalUi.INK_BROWN : JournalUi.MUTED);
      }, null, null));

      if (com.formaworks.frontierhunts.wildlife2026.Beta.animal(sp)) {
         // [gear20] beta animal: say so before anything else on the card
         para(out, tr("hunts.frontierhunts.card.beta"), bodyW, 0xFF9A5A10, FrontierUi.Size.SMALL, 0);
         space(out, 2);
      }

      // ---- the hunter's record of this species
      HunterRecord.SpeciesLog log = r.species.get(sp);
      String best = "—";
      if (log != null && log.bestScore > 0) {
         best = log.bestScore + "\"";
      } else if (log != null && log.bestWeight > 0) {
         best = log.bestWeight >= 100 ? Math.round(log.bestWeight / 10.0F) + " kg" : String.format(Locale.ROOT, "%.1f kg", log.bestWeight / 10.0);
      }
      String stats = tr("hunts.frontierhunts.card.stats", log == null ? 0 : log.seen, log == null ? 0 : log.photos, log == null ? 0 : log.harvested, best);
      para(out, stats, bodyW, JournalUi.MUTED, FrontierUi.Size.SMALL, 0);
      space(out, 2);

      // ---- how it is hunted, where, season
      section(out, tr("hunts.frontierhunts.card.how"));
      para(out, tr("hunts.frontierhunts." + sp + ".brief"), bodyW, JournalUi.INK_BROWN, FrontierUi.Size.SMALL, 0);
      space(out, 2);
      iconLine(out, "reserve", tr("hunts.frontierhunts.card.where", tr("hunts.frontierhunts." + sp + ".where")), bodyW);
      HuntBook.Contract c = h.contract();
      if (c != null) {
         iconLine(out, "contracts", tr("hunts.frontierhunts.card.contract", c.title(), months(c.months()), c.tokens()), bodyW);
      }
      space(out, 4);

      // ---- milestones
      section(out, tr("hunts.frontierhunts.card.milestones"));
      for (HuntBook.Milestone m : h.milestones()) {
         milestone(out, r, m, bodyW);
      }
      space(out, 2);
      para(out, tr("hunts.frontierhunts.card.footer"), bodyW, JournalUi.FAINT, FrontierUi.Size.SMALL, 0);
      return out;
   }

   private static void milestone(List<Line> out, HunterRecord r, HuntBook.Milestone m, int bodyW) {
      boolean done = r.done.contains(m.id());
      int v = Math.min(m.target(), Math.max(0, r.get(m.counter())));
      String titleText = tr("journal.frontierhunts.check." + m.id());
      FormattedCharSequence tier = JournalUi.seq(tierLabel(m.tier()).toUpperCase(Locale.ROOT), FrontierUi.Size.SMALL);
      int tierW = JournalUi.width(tier) + 10;
      FormattedCharSequence title = JournalUi.fit(titleText, bodyW - 40 - tierW - 6, FrontierUi.Size.STRONG);
      List<FormattedCharSequence> hint = JournalUi.wrap(tr("journal.frontierhunts.check." + m.id() + ".hint"), bodyW - 40, FrontierUi.Size.SMALL);
      String reward = HuntRewardsText.line(m, HuntCardView::itemName, JournalUi::tr);
      List<FormattedCharSequence> rewardLines = JournalUi.wrap(reward, bodyW - 40, FrontierUi.Size.SMALL);
      List<FormattedCharSequence> needs = new ArrayList<>();
      for (String n : m.needs()) {
         needs.addAll(JournalUi.wrap(tr("hunts.frontierhunts.card.needs", needsText(n)), bodyW - 40, FrontierUi.Size.SMALL));
      }
      boolean multi = m.target() > 1;
      FormattedCharSequence count = multi ? JournalUi.seq(v + " / " + m.target() + (m.mode() == HuntBook.Mode.DAY ? " " + tr("hunts.frontierhunts.card.in_a_day") : ""),
         FrontierUi.Size.SMALL) : null;
      int color = tierColor(m.tier());
      int h = 18 + hint.size() * 9 + needs.size() * 9 + rewardLines.size() * 9 + (multi ? 10 : 0) + 6;
      out.add(new Line(h, (g, x, y, w, mx, my, hv) -> {
         FrontierUi.rect(g, x - 2, y + 1, w + 4, h - 3, 3.0F, done ? 0x163E6E37 : (m.master() ? 0x12B99859 : 0x0E3B2E20));
         JournalUi.checkbox(g, x + 2, y + 6, done, m.master() ? JournalUi.GOLD_DARK : JournalUi.GREEN);
         HuntIcons.draw(g, m.icon(), x + 16, y + 3, 16, false, done ? 0.75F : 1.0F);
         JournalUi.draw(g, title, x + 36, y + 6, done ? JournalUi.MUTED : JournalUi.INK);
         if (done) {
            g.fill(x + 36, y + 10, x + 36 + JournalUi.width(title), y + 11, 0x66243A32);
         }
         FrontierUi.rect(g, x + w - tierW, y + 4, tierW, 11, 5.5F, done ? JournalUi.alpha(color, 0.55F) : color);
         JournalUi.draw(g, tier, x + w - tierW + 5, y + 6, m.tier() == HuntBook.Tier.MASTER ? JournalUi.INK_BROWN : JournalUi.PAPER);
         int yy = y + 18;
         for (FormattedCharSequence l : hint) {
            JournalUi.draw(g, l, x + 36, yy, done ? JournalUi.FAINT : JournalUi.INK_BROWN);
            yy += 9;
         }
         for (FormattedCharSequence l : needs) {
            JournalUi.draw(g, l, x + 36, yy, JournalUi.MUTED);
            yy += 9;
         }
         if (multi) {
            JournalUi.bar(g, x + 36, yy + 3, Math.min(120, w - 90), 3.0F, (float)v / m.target(), JournalUi.TRACK, done ? JournalUi.GREEN : JournalUi.GOLD);
            JournalUi.draw(g, count, x + 40 + Math.min(120, w - 90), yy + 1, JournalUi.INK_BROWN);
            yy += 10;
         }
         for (FormattedCharSequence l : rewardLines) {
            JournalUi.draw(g, l, x + 36, yy, done ? JournalUi.FAINT : JournalUi.GOLD_DARK);
            yy += 9;
         }
      }, null, null));
   }

   // ---------------------------------------------------------------------------------------------- small builders

   private static void space(List<Line> out, int h) {
      out.add(new Line(h, (g, x, y, w, mx, my, hv) -> {
      }, null, null));
   }

   private static void section(List<Line> out, String text) {
      FormattedCharSequence s = JournalUi.seq(text.toUpperCase(Locale.ROOT), FrontierUi.Size.STRONG);
      out.add(new Line(20, (g, x, y, w, mx, my, hv) -> {
         JournalUi.draw(g, s, x, y + 6, JournalUi.INK);
         g.fill(x, y + 17, x + Math.min(36, w), y + 18, 0xFFB89B6E);
      }, null, null));
   }

   private static void para(List<Line> out, String text, int bodyW, int color, FrontierUi.Size size, int indent) {
      List<FormattedCharSequence> lines = JournalUi.wrap(text, bodyW - indent, size);
      int lh = FrontierUi.lineHeight(size) + 2;
      out.add(new Line(lines.size() * lh + 3, (g, x, y, w, mx, my, hv) -> {
         int yy = y;
         for (FormattedCharSequence l : lines) {
            JournalUi.draw(g, l, x + indent, yy, color);
            yy += lh;
         }
      }, null, null));
   }

   private static void iconLine(List<Line> out, String icon, String text, int bodyW) {
      List<FormattedCharSequence> lines = JournalUi.wrap(text, bodyW - 22, FrontierUi.Size.SMALL);
      int h = Math.max(16, lines.size() * 9 + 6);
      out.add(new Line(h, (g, x, y, w, mx, my, hv) -> {
         JournalIcons.draw(g, icon, x, y, 12, false, 1.0F);
         int yy = y + 2;
         for (FormattedCharSequence l : lines) {
            JournalUi.draw(g, l, x + 18, yy, JournalUi.INK_BROWN);
            yy += 9;
         }
      }, null, null));
   }
}
