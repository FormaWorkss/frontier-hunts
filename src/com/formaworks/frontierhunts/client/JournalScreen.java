package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntNetwork;
import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.camps.Quarry;
import com.formaworks.frontierhunts.expedition.Campaign;
import com.formaworks.frontierhunts.expedition.ExpeditionNetwork;
import com.formaworks.frontierhunts.expedition.HuntingCalendar;
import com.formaworks.frontierhunts.guide.Lesson;
import com.formaworks.frontierhunts.guide.client.FieldSchoolScreen;
import com.formaworks.frontierhunts.guide.client.GuideClient;
import com.formaworks.frontierhunts.journal.Checklist;
import com.formaworks.frontierhunts.journal.HunterRecord;
import com.formaworks.frontierhunts.journal.HunterSkills;
import com.formaworks.frontierhunts.journal.JournalNet;
import com.formaworks.frontierhunts.journal.JournalPages;
import com.formaworks.frontierhunts.journal.Perk;
import com.formaworks.frontierhunts.journal.Rank;
import com.formaworks.frontierhunts.journal.Skill;
import com.formaworks.frontierhunts.journal.Stat;
import com.formaworks.frontierhunts.journal.client.JournalClient;
import com.formaworks.frontierhunts.journal.client.JournalIcons;
import com.formaworks.frontierhunts.journal.client.JournalPageView;
import com.formaworks.frontierhunts.journal.client.JournalUi;
import com.formaworks.frontierhunts.season.SeasonClock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * [journal] The Hunter's Journal, overhauled: a leather notebook with a sidebar of pages —
 * Home (rank, XP, date, skills, what to do next, recent notes), Checklist (everything in the mod, by category, with
 * progress and hints; Field School lessons included), Skills (levels and perks), Species (seen / photographed / taken,
 * bests, first dates), Records (stats, trophy room, season summaries to copy or print), Field notes (dated auto-entries)
 * and Reserve (the old journal's conditions, habitat survey, first-hunt contract, Mara's campaign and contracts, with
 * buttons to the Expedition Guide, Assignments, Field School and Settings). Extra pages come from
 * {@link JournalPages} + {@link JournalPageView}.
 *
 * <p>Pages are laid out into rows (pre-built text) when the page, the data or the window changes; frames only draw
 * the visible rows. Smooth scrolling (wheel, keys, draggable bar), tooltips, keyboard tabs (1-9, Tab / Shift-Tab).
 * The class keeps the old journal's public surface ({@code new JournalScreen()}, {@link #refresh()}, {@link #biomeName}).
 */
public final class JournalScreen extends net.minecraft.client.gui.screens.Screen {
   private static final String[] TABS = {"home", "checklist", "skills", "species", "records", "notes", "reserve"};
   /** [ledger] original pixel-art tab icons (journal.client.JournalIcons atlas). */
   private static final String[] TAB_ICONS = {"home", "checklist", "skills", "species", "records", "notes", "reserve"};
   private static int lastTab;
   private static int filter = -1;
   /** [hunts] species whose hunt card is open on the Species page (null = the species table) */
   private static String huntSpecies;

   private final List<String> tabKeys = new ArrayList<>();
   private final List<String> tabIcons = new ArrayList<>();
   private final List<FormattedCharSequence> tabLabels = new ArrayList<>();
   private final List<Row> rows = new ArrayList<>();
   private int tab;
   private int left, top, panelW, panelH, side, bodyX, bodyW, viewTop, viewBottom, tabTop, tabH;
   private int contentH;
   private float scroll;
   private int scrollTarget;
   private boolean dragging;
   private long lastFrame;
   private int builtVersion = -1;
   private int builtFieldSchool = -2;
   private int builtHandbook = -2; // [onboard]
   private FormattedCharSequence title = FormattedCharSequence.EMPTY;
   private FormattedCharSequence subtitle = FormattedCharSequence.EMPTY;
   private FormattedCharSequence brand1, brand2, rankLine, schoolLabel, tokenLine;
   private FormattedCharSequence dateSeq = FormattedCharSequence.EMPTY;
   private long dateAt;
   private Row hovered;

   /** [licence] extra page to show when the screen next opens (see {@link #openPage}) */
   private static String pendingPage;

   /** [licence] Opens the journal on an extra page (JournalPages id), e.g. "licence". */
   public static void openPage(String id) {
      pendingPage = id;
      Minecraft.getInstance().setScreen(new JournalScreen());
   }

   public JournalScreen() {
      super(Component.translatable("screen.frontierhunts.journal"));
      this.tab = lastTab;
   }

   // ============================================================================================ rows

   @FunctionalInterface
   private interface Draw {
      void draw(GuiGraphics g, int x, int y, int w, int mx, int my, boolean hover);
   }

   @FunctionalInterface
   private interface Click {
      boolean click(double mx, double my, int x, int y, int w);
   }

   private static final class Row {
      final int h;
      final Draw draw;
      Click click;
      List<FormattedCharSequence> tip;
      boolean highlight;
      int y;

      Row(int h, Draw draw) {
         this.h = h;
         this.draw = draw;
      }

      Row onClick(Click c) {
         this.click = c;
         this.highlight = true;
         return this;
      }

      Row tip(List<FormattedCharSequence> t) {
         this.tip = t;
         return this;
      }
   }

   private Row add(Row r) {
      r.y = this.contentH;
      this.contentH += r.h;
      this.rows.add(r);
      return r;
   }

   private void space(int h) {
      this.contentH += h;
   }

   // ============================================================================================ layout

   @Override
   public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
   }

   @Override
   protected void init() {
      this.panelW = Math.min(620, this.width - 12);
      this.panelH = Math.min(400, this.height - 12);
      this.left = (this.width - this.panelW) / 2;
      this.top = (this.height - this.panelH) / 2;
      this.side = this.panelW < 470 ? 100 : 128;
      this.bodyX = this.left + this.side + 18;
      this.bodyW = this.panelW - this.side - 36;
      this.viewTop = this.top + 52;
      this.viewBottom = this.top + this.panelH - 10;
      this.tabKeys.clear();
      this.tabIcons.clear();
      this.tabLabels.clear();
      for (int i = 0; i < TABS.length; i++) {
         this.tabKeys.add(TABS[i]);
         this.tabIcons.add(JournalIcons.ref(TAB_ICONS[i]));
      }
      for (JournalPages.Page p : JournalPages.all()) {
         if (JournalPageView.Views.get(p.id()) != null) {
            this.tabKeys.add("page:" + p.id());
            this.tabIcons.add(JournalIcons.has(p.id()) ? JournalIcons.ref(p.id()) : p.icon()); // [ledger] atlas art for known pages
         }
      }
      // [1.1.7] the Hunter's Path sits right under Home
      int pi0 = this.tabKeys.indexOf("page:" + com.formaworks.frontierhunts.progression.HunterPath.PAGE);
      int hi0 = this.tabKeys.indexOf("home");
      if (pi0 > hi0 + 1 && hi0 >= 0) {
         String k = this.tabKeys.remove(pi0);
         String ic = this.tabIcons.remove(pi0);
         this.tabKeys.add(hi0 + 1, k);
         this.tabIcons.add(hi0 + 1, ic);
      }
      int labelW = this.side - 34;
      for (String k : this.tabKeys) {
         String label = k.startsWith("page:") ? pageTitle(k.substring(5)) : JournalUi.tr("journal.frontierhunts.tab." + k);
         this.tabLabels.add(JournalUi.fit(label, labelW, FrontierUi.Size.BODY));
      }
      this.tabTop = this.top + 54;
      int avail = this.panelH - 54 - 58;
      this.tabH = Mth.clamp(avail / Math.max(1, this.tabKeys.size()), 14, 24);
      if (pendingPage != null) { // [licence] openPage(id): start on that extra page
         int pi = this.tabKeys.indexOf("page:" + pendingPage);
         if (pi >= 0) {
            this.tab = pi;
            lastTab = pi;
         }
         pendingPage = null;
      }
      this.tab = Mth.clamp(this.tab, 0, this.tabKeys.size() - 1);
      this.brand1 = JournalUi.seq(JournalUi.tr("journal.frontierhunts.brand1"), FrontierUi.Size.SMALL);
      this.brand2 = JournalUi.fit(JournalUi.tr("journal.frontierhunts.brand2"), this.side - 26, FrontierUi.Size.STRONG);
      JournalClient.ask(JournalNet.A_OPEN, 0);
      this.rebuild(true);
   }

   private static String pageTitle(String id) {
      for (JournalPages.Page p : JournalPages.all()) {
         if (p.id().equals(id)) {
            return JournalUi.tr(p.titleKey());
         }
      }
      return id;
   }

   /** Old API: called by FrontierClient when the reserve snapshot / first-hunt progress changes. */
   public void refresh() {
      this.rebuild(false);
   }

   /** Called by JournalClient when new journal data arrives. */
   public void dataChanged() {
      this.rebuild(false);
   }

   private void selectTab(int t) {
      t = Mth.clamp(t, 0, this.tabKeys.size() - 1);
      if (t != this.tab) {
         this.tab = t;
         lastTab = t;
         Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.6F));
         this.rebuild(true);
      }
   }

   private void rebuild(boolean resetScroll) {
      if (this.minecraft == null) {
         return;
      }
      this.rows.clear();
      this.contentH = 0;
      this.hovered = null;
      this.builtVersion = JournalClient.version;
      this.builtFieldSchool = GuideClient.fieldSchoolDone();
      this.builtHandbook = com.formaworks.frontierhunts.guide.client.HandbookClient.mask(); // [onboard]
      this.sidebarText();
      this.updateDate();
      String key = this.tabKeys.get(this.tab);
      HunterRecord r = JournalClient.data == null ? null : JournalClient.data.record();
      String sub = "";
      if (key.startsWith("page:")) {
         sub = this.extraPage(key.substring(5));
      } else if (r == null && !key.equals("reserve")) {
         this.para(JournalUi.tr("journal.frontierhunts.loading"), JournalUi.MUTED, FrontierUi.Size.BODY);
      } else {
         sub = switch (key) {
            case "home" -> this.home(r);
            case "checklist" -> this.checklist(r);
            case "skills" -> this.skills(r);
            case "species" -> this.species(r);
            case "records" -> this.records(r);
            case "notes" -> this.notes(r);
            default -> this.reserve();
         };
      }
      space(10);
      String t = key.startsWith("page:") ? pageTitle(key.substring(5)) : JournalUi.tr("journal.frontierhunts.title." + key);
      this.title = JournalUi.fit(t, this.bodyW - 30, FrontierUi.Size.TITLE);
      this.subtitle = JournalUi.fit(sub, this.bodyW - 8, FrontierUi.Size.SMALL);
      int max = this.maxScroll();
      if (resetScroll) {
         this.scrollTarget = 0;
         this.scroll = 0.0F;
      } else {
         this.scrollTarget = Math.min(this.scrollTarget, max);
         this.scroll = Math.min(this.scroll, max);
      }
   }

   private int maxScroll() {
      return Math.max(0, this.contentH - (this.viewBottom - this.viewTop));
   }

   private void sidebarText() {
      HunterRecord r = JournalClient.data == null ? null : JournalClient.data.record();
      Rank rank = r == null ? Rank.GREENHORN : Rank.of(r.totalXp());
      this.rankLine = JournalUi.fit(JournalUi.tr(rank.lang()), this.side - 26, FrontierUi.Size.SMALL);
      int fs = GuideClient.fieldSchoolDone();
      String school = JournalUi.tr("journal.frontierhunts.side.school");
      String count = fs < 0 ? "" : (Lesson.graduated(fs) ? " ✓" : " " + Lesson.requiredDone(fs) + "/7");
      this.schoolLabel = JournalUi.fit(school + count, this.side - 36, FrontierUi.Size.SMALL);
      int tokens = JournalClient.data != null ? JournalClient.data.tokens() : (FrontierClient.state != null ? FrontierClient.state.tokens() : -1);
      this.tokenLine = tokens < 0 ? null : JournalUi.fit(JournalUi.tr("journal.frontierhunts.side.tokens", String.format(Locale.ROOT, "%,d", tokens)),
         this.side - 34, FrontierUi.Size.SMALL);
   }

   private void updateDate() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         this.dateSeq = FormattedCharSequence.EMPTY;
         return;
      }
      long cal = SeasonClock.calendarTicks(mc.level);
      HuntingCalendar.Date d = HuntingCalendar.date(cal, SeasonClock.daysPerMonth(mc.level));
      long t = Math.floorMod(mc.level.getDayTime(), 24000L);
      int h = (int)((t / 1000L + 6L) % 24L);
      int m = (int)(t % 1000L * 60L / 1000L);
      String time = (h % 12 == 0 ? 12 : h % 12) + ":" + (m < 10 ? "0" : "") + m + (h < 12 ? " AM" : " PM");
      String season = JournalUi.tr("journal.frontierhunts.season." + SeasonClock.seasonAt(SeasonClock.yearPosition(cal, SeasonClock.daysPerMonth(mc.level))).name().toLowerCase(Locale.ROOT));
      String s = JournalUi.tr("journal.frontierhunts.date", season, JournalUi.tr("journal.frontierhunts.month." + d.month()), d.day(), d.serial() / 12L + 1L, time);
      this.dateSeq = JournalUi.fit(s, this.bodyW - 4, FrontierUi.Size.SMALL);
      this.dateAt = System.currentTimeMillis();
   }

   // ============================================================================================ generic row builders

   private void section(String text) {
      FormattedCharSequence s = JournalUi.seq(text.toUpperCase(Locale.ROOT), FrontierUi.Size.STRONG);
      add(new Row(20, (g, x, y, w, mx, my, hv) -> {
         JournalUi.draw(g, s, x, y + 6, JournalUi.INK);
         g.fill(x, y + 17, x + Math.min(36, w), y + 18, 0xFFB89B6E);
      }));
   }

   private void sectionRight(String text, String right, float frac, String icon) {
      FormattedCharSequence s = JournalUi.seq(text.toUpperCase(Locale.ROOT), FrontierUi.Size.STRONG);
      FormattedCharSequence rs = JournalUi.seq(right, FrontierUi.Size.SMALL);
      int tx = icon == null ? 0 : 20;
      add(new Row(24, (g, x, y, w, mx, my, hv) -> {
         if (icon != null) {
            JournalIcons.drawRef(g, icon, x, y + 3, 16, false, 1.0F);
         }
         JournalUi.draw(g, s, x + tx, y + 8, JournalUi.INK);
         JournalUi.drawRight(g, rs, x + w, y + 9, JournalUi.MUTED);
         JournalUi.bar(g, x + tx, y + 20, w - tx, 2.0F, frac, JournalUi.TRACK, JournalUi.GOLD);
      }));
   }

   private void para(String text, int color, FrontierUi.Size size) {
      List<FormattedCharSequence> lines = JournalUi.wrap(text, this.bodyW, size);
      int lh = FrontierUi.lineHeight(size) + 2;
      add(new Row(lines.size() * lh + 3, (g, x, y, w, mx, my, hv) -> {
         int yy = y;
         for (FormattedCharSequence l : lines) {
            JournalUi.draw(g, l, x, yy, color);
            yy += lh;
         }
      }));
   }

   private void buttons(String[] labels, Runnable[] actions, boolean[] active) {
      int n = labels.length;
      int gap = 6;
      int bw = Math.min(132, (this.bodyW - gap * (n - 1)) / n);
      FormattedCharSequence[] seqs = new FormattedCharSequence[n];
      for (int i = 0; i < n; i++) {
         seqs[i] = JournalUi.fit(labels[i], bw - 8, FrontierUi.Size.SMALL);
      }
      Row row = new Row(24, (g, x, y, w, mx, my, hv) -> {
         for (int i = 0; i < n; i++) {
            int bx = x + i * (bw + gap);
            boolean over = mx >= bx && mx < bx + bw && my >= y + 3 && my < y + 21;
            JournalUi.button(g, bx, y + 3, bw, 18, seqs[i], over, false, active == null || active[i]);
         }
      });
      row.click = (mx, my, x, y, w) -> {
         for (int i = 0; i < n; i++) {
            int bx = x + i * (bw + gap);
            if (mx >= bx && mx < bx + bw && my >= y + 3 && my < y + 21 && (active == null || active[i])) {
               click();
               actions[i].run();
               return true;
            }
         }
         return false;
      };
      add(row);
   }

   private static void click() {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
   }

   private static List<FormattedCharSequence> tipLines(String head, String body, int width) {
      List<FormattedCharSequence> l = new ArrayList<>();
      if (head != null && !head.isEmpty()) {
         l.add(JournalUi.seq(head, FrontierUi.Size.STRONG));
      }
      if (body != null && !body.isEmpty()) {
         for (String part : body.split("\n")) {
            l.addAll(JournalUi.wrap(part, width, FrontierUi.Size.SMALL));
         }
      }
      return l;
   }

   private static String num(long v) {
      return String.format(Locale.ROOT, "%,d", v);
   }

   // ============================================================================================ HOME

   private String home(HunterRecord r) {
      long total = r.totalXp();
      Rank rank = Rank.of(total);
      Rank next = rank.next();
      float frac = next == null ? 1.0F : (float)(total - rank.xp) / (float)Math.max(1, next.xp - rank.xp);
      FormattedCharSequence eyebrow = JournalUi.seq(JournalUi.tr("journal.frontierhunts.home.rank", rank.ordinal() + 1, Rank.values().length), FrontierUi.Size.SMALL);
      FormattedCharSequence name = JournalUi.fit(JournalUi.tr(rank.lang()), this.bodyW - 60, FrontierUi.Size.TITLE);
      FormattedCharSequence xpLine = JournalUi.fit(next == null
         ? JournalUi.tr("journal.frontierhunts.home.xp_top", num(total))
         : JournalUi.tr("journal.frontierhunts.home.xp", num(total), num(next.xp - total), JournalUi.tr(next.lang())), this.bodyW - 60, FrontierUi.Size.SMALL);
      String icon = rank.icon;
      add(new Row(56, (g, x, y, w, mx, my, hv) -> {
         float cx = x + 22, cy = y + 25;
         FrontierUi.circle(g, cx, cy, 21.0F, JournalUi.GOLD);
         FrontierUi.circle(g, cx, cy, 19.5F, JournalUi.LEATHER);
         FrontierUi.circle(g, cx, cy, 15.5F, 0xFF5A4433);
         // rank pips around the emblem
         for (int i = 0; i < Rank.values().length; i++) {
            double a = Math.PI * (0.75 + 1.5 * i / (Rank.values().length - 1.0));
            float px = cx + (float)Math.cos(a) * 17.2F, py = cy + (float)Math.sin(a) * 17.2F;
            FrontierUi.circle(g, px, py, 1.3F, i <= rank.ordinal() ? JournalUi.GOLD : 0xFF7A6650);
         }
         JournalIcons.drawRef(g, icon, (int)cx - 16, (int)cy - 16, 32, false, 1.0F); // [ledger] rank medal
         JournalUi.draw(g, eyebrow, x + 52, y + 3, JournalUi.GOLD_DARK);
         JournalUi.draw(g, name, x + 52, y + 13, JournalUi.INK);
         JournalUi.bar(g, x + 52, y + 32, w - 54, 5.0F, frac, JournalUi.TRACK, JournalUi.GOLD);
         JournalUi.draw(g, xpLine, x + 52, y + 41, JournalUi.MUTED);
      })).tip(tipLines(JournalUi.tr("journal.frontierhunts.home.rank_tip_head"), JournalUi.tr("journal.frontierhunts.home.rank_tip"), 220));
      // [1.1.6] what the rank is worth, and what the next one adds
      FormattedCharSequence perkNow = JournalUi.fit(JournalUi.tr("journal.frontierhunts.home.perk_now", JournalUi.tr(com.formaworks.frontierhunts.journal.RankPerks.perkKey(rank))),
         this.bodyW, FrontierUi.Size.SMALL);
      FormattedCharSequence perkNext = next == null ? null : JournalUi.fit(JournalUi.tr("journal.frontierhunts.home.perk_next", JournalUi.tr(next.lang()),
         JournalUi.tr(com.formaworks.frontierhunts.journal.RankPerks.perkKey(next))), this.bodyW, FrontierUi.Size.SMALL);
      add(new Row(perkNext == null ? 11 : 20, (g, x, y, w, mx, my, hv) -> {
         JournalUi.draw(g, perkNow, x, y + 1, JournalUi.GREEN);
         if (perkNext != null) {
            JournalUi.draw(g, perkNext, x, y + 10, JournalUi.MUTED);
         }
      })).tip(tipLines(JournalUi.tr("journal.frontierhunts.home.perk_head"), JournalUi.tr("journal.frontierhunts.home.perk_all"), 240));

      add(new Row(16, (g, x, y, w, mx, my, hv) -> JournalUi.draw(g, this.dateSeq, x, y + 3, JournalUi.MUTED)));

      // skills strip
      int[] lv = r.levels();
      int n = Skill.count();
      FormattedCharSequence[] names = new FormattedCharSequence[n];
      FormattedCharSequence[] lvs = new FormattedCharSequence[n];
      float[] fr = new float[n];
      int colW = (this.bodyW - (n - 1) * 8) / n;
      for (Skill s : Skill.values()) {
         int i = s.ordinal();
         lvs[i] = JournalUi.seq(Integer.toString(lv[i]), FrontierUi.Size.STRONG);
         names[i] = JournalUi.fit(JournalUi.tr(s.lang("name")), colW - JournalUi.width(lvs[i]) - 4, FrontierUi.Size.SMALL);
         int a = Skill.xpFor(lv[i]), b = Skill.xpFor(lv[i] + 1);
         fr[i] = lv[i] >= Skill.MAX_LEVEL ? 1.0F : (float)(r.skillXp[i] - a) / Math.max(1, b - a);
      }
      space(4);
      add(new Row(28, (g, x, y, w, mx, my, hv) -> {
         for (int i = 0; i < n; i++) {
            int cx = x + i * (colW + 8);
            JournalUi.draw(g, names[i], cx, y + 4, JournalUi.MUTED);
            JournalUi.drawRight(g, lvs[i], cx + colW, y + 2, JournalUi.INK);
            JournalUi.bar(g, cx, y + 15, colW, 4.0F, fr[i], JournalUi.TRACK, 0xFF000000 | Skill.byId(i).color);
         }
      }).onClick((mx, my, x, y, w) -> {
         this.selectTab(this.tabKeys.indexOf("skills"));
         return true;
      }).tip(tipLines(JournalUi.tr("journal.frontierhunts.home.skills_tip_head"), JournalUi.tr("journal.frontierhunts.home.skills_tip"), 200)));

      // checklist summary
      int total2 = 0, done = 0;
      for (Checklist.Entry e : Checklist.visible()) {
         total2++;
         if (r.done.contains(e.id())) {
            done++;
         }
      }
      float cf = total2 == 0 ? 0.0F : (float)done / total2;
      FormattedCharSequence cl = JournalUi.seq(JournalUi.tr("journal.frontierhunts.home.checklist"), FrontierUi.Size.SMALL);
      FormattedCharSequence cv = JournalUi.seq(JournalUi.tr("journal.frontierhunts.home.checklist_value", done, total2, Math.round(cf * 100.0F)), FrontierUi.Size.STRONG);
      add(new Row(30, (g, x, y, w, mx, my, hv) -> {
         JournalUi.draw(g, cl, x, y + 5, JournalUi.GOLD_DARK);
         JournalUi.drawRight(g, cv, x + w, y + 3, JournalUi.INK);
         JournalUi.bar(g, x, y + 17, w, 5.0F, cf, JournalUi.TRACK, JournalUi.GREEN);
      }).onClick((mx, my, x, y, w) -> {
         filter = -1;
         this.selectTab(this.tabKeys.indexOf("checklist"));
         return true;
      }));

      // up next
      section(JournalUi.tr("journal.frontierhunts.home.next"));
      int shown = 0;
      // [1.1.5] rewards waiting to be claimed come first, wherever they are claimed, so none is missed
      shown += this.claimRows();
      int fs = GuideClient.fieldSchoolDone();
      // [onboard] the Frontier Handbook's one next step (a Field School lesson or a Handbook task); opens the Handbook there
      com.formaworks.frontierhunts.onboard.Handbook.Task hb = com.formaworks.frontierhunts.guide.client.HandbookClient.next();
      if (hb != null) {
         int hs = com.formaworks.frontierhunts.onboard.Handbook.stepsDone(com.formaworks.frontierhunts.guide.client.HandbookClient.mask());
         objective(hb.lesson != null ? lessonIcon(hb.lesson) : hb.icon, JournalUi.tr(hb.lang("title")),
            JournalUi.tr("onboard.frontierhunts.journal.next", hb.step, com.formaworks.frontierhunts.onboard.Handbook.STEPS),
            hs + "/" + com.formaworks.frontierhunts.onboard.Handbook.STEPS, hs / (float)com.formaworks.frontierhunts.onboard.Handbook.STEPS,
            () -> com.formaworks.frontierhunts.guide.client.HandbookClient.open(hb.step));
         shown++;
         fs = -1; // the lesson row below would say the same thing twice
      }
      if (fs >= 0 && !Lesson.graduated(fs) && (GuideClient.fieldSchoolFlags() & 2) == 0) {
         Lesson l = Lesson.current(fs);
         if (l != null) {
            objective(lessonIcon(l), JournalUi.tr(l.lang("title")),
               JournalUi.tr("journal.frontierhunts.home.next_school", l.ordinal() + 1), Lesson.requiredDone(fs) + "/7", Lesson.requiredDone(fs) / 7.0F,
               () -> this.minecraft.setScreen(new FieldSchoolScreen(this, l.ordinal())));
            shown++;
         }
      }
      JournalNet.Data d = JournalClient.data;
      if (d != null && d.campaignStage() < Campaign.MISSIONS.size()) {
         Campaign.Mission m = Campaign.MISSIONS.get(d.campaignStage());
         objective(JournalIcons.ref("campaign"), m.title(), JournalUi.tr("journal.frontierhunts.home.next_campaign", d.campaignStage() + 1,
            Campaign.MISSIONS.size()), Math.min(d.campaignCount(), m.amount()) + "/" + m.amount(), (float)d.campaignCount() / Math.max(1, m.amount()),
            JournalScreen::openGuide);
         shown++;
      }
      if (d != null && (d.contractState() == 1 || d.contractState() == 2) && d.contract() >= 0 && d.contract() < Campaign.CONTRACTS.size()) {
         Campaign.Mission c = Campaign.CONTRACTS.get(d.contract());
         objective(JournalIcons.ref("contracts"), c.title(), JournalUi.tr(d.contractState() == 2
            ? "journal.frontierhunts.home.contract_ready" : "journal.frontierhunts.home.contract_active"),
            Math.min(d.contractCount(), c.amount()) + "/" + c.amount(), (float)d.contractCount() / Math.max(1, c.amount()), JournalScreen::openGuide);
         shown++;
      }
      // [1.1.5] the next Ranger Academy course, once the Handbook path no longer points at one
      if (hb == null) {
         com.formaworks.frontierhunts.academy.Course course = com.formaworks.frontierhunts.guide.client.HandbookClient.recommended();
         if (course != null) {
            objective(JournalIcons.ref("skills"), JournalUi.tr(course.lang("title")), JournalUi.tr("journal.frontierhunts.home.next_academy"), "K", 0.0F,
               () -> com.formaworks.frontierhunts.client.AssignmentScreen.send(0, "", 0));
            shown++;
         }
      }
      // [1.1.6] the Hunter's Path: the next real goal beyond the first deer, and what it gives
      if (hb == null && JournalClient.data != null) {
         for (JournalNet.PageData pd : JournalClient.data.pages()) {
            if (pd.id().equals(com.formaworks.frontierhunts.progression.HunterPath.PAGE) && pd.tag() != null) {
               int ni = pd.tag().getInt("next");
               net.minecraft.nbt.ListTag st = pd.tag().getList("s", net.minecraft.nbt.Tag.TAG_COMPOUND);
               if (ni >= 0 && ni < st.size()) {
                  net.minecraft.nbt.CompoundTag c = st.getCompound(ni);
                  int pathTab = this.tabKeys.indexOf("page:" + com.formaworks.frontierhunts.progression.HunterPath.PAGE);
                  objective(JournalIcons.ref("campaign"), JournalUi.tr("journal.frontierhunts.home.path", c.getString("t")), c.getString("w"),
                     (ni + 1) + "/" + st.size(), ni / (float)st.size(), () -> this.selectTab(pathTab));
                  shown++;
               }
            }
         }
      }
      for (Checklist.Entry e : nextEntries(r, Math.max(2, 5 - shown))) {
         int v = r.get(e.counter());
         objective(entryIcon(e), JournalClient.checkTitle(e), JournalUi.tr(e.category().lang()), e.target() == 1 ? "+" + e.xp() + " XP" : progressText(e, v),
            Math.min(1.0F, (float)v / e.target()), () -> {
               filter = e.category().ordinal();
               this.selectTab(this.tabKeys.indexOf("checklist"));
            });
      }

      // recent notes
      if (!r.notes.isEmpty()) {
         section(JournalUi.tr("journal.frontierhunts.home.recent"));
         int i = 0;
         for (HunterRecord.Note note : r.notes) {
            if (i++ >= 4) {
               break;
            }
            this.noteRow(note);
         }
      }
      return JournalUi.tr("journal.frontierhunts.sub.home", Minecraft.getInstance().getUser().getName());
   }

   /** [1.1.5] "Up next" rows for rewards that are ready to claim (Ranger assignment, habitat survey, first hunt). */
   private int claimRows() {
      int n = 0;
      com.formaworks.frontierhunts.progression.AssignmentNetwork.Snapshot a = FrontierClient.assignments;
      if (a != null && a.state() == 2) {
         objective(JournalIcons.ref("contracts"), JournalUi.tr("journal.frontierhunts.home.claim_assignment"), JournalUi.tr("journal.frontierhunts.home.claim_assignment_sub"),
            JournalUi.tr("journal.frontierhunts.home.claim"), 1.0F, () -> com.formaworks.frontierhunts.client.AssignmentScreen.send(0, "", 0));
         n++;
      }
      HuntNetwork.Snapshot s = FrontierClient.state;
      if (s != null && s.active() && s.surveyReady() && !s.surveyClaimed()) {
         objective(JournalIcons.ref("reserve"), JournalUi.tr("journal.frontierhunts.home.claim_survey"), JournalUi.tr("journal.frontierhunts.home.claim_reserve_sub"),
            JournalUi.tr("journal.frontierhunts.home.claim"), 1.0F, () -> this.selectTab(this.tabKeys.indexOf("reserve")));
         n++;
      }
      HuntNetwork.HuntProgress h = FrontierClient.hunt;
      if (s != null && s.active() && h != null && h.clue() && h.harvested() && !h.claimed()) {
         objective(JournalIcons.ref("reserve"), JournalUi.tr("journal.frontierhunts.home.claim_hunt"), JournalUi.tr("journal.frontierhunts.home.claim_reserve_sub"),
            JournalUi.tr("journal.frontierhunts.home.claim"), 1.0F, () -> this.selectTab(this.tabKeys.indexOf("reserve")));
         n++;
      }
      return n;
   }

   private void objective(String icon, String title, String sub, String right, float frac, Runnable action) {
      FormattedCharSequence rs = JournalUi.seq(right, FrontierUi.Size.STRONG);
      int rw = JournalUi.width(rs) + 8;
      FormattedCharSequence ts = JournalUi.fit(title, this.bodyW - 26 - rw, FrontierUi.Size.STRONG);
      FormattedCharSequence ss = JournalUi.fit(sub, this.bodyW - 26 - rw, FrontierUi.Size.SMALL);
      add(new Row(26, (g, x, y, w, mx, my, hv) -> {
         if (hv) {
            FrontierUi.rect(g, x - 3, y + 1, w + 6, 24, 3.0F, JournalUi.HOVER);
         }
         JournalIcons.drawRef(g, icon, x, y + 4, 16, hv, 1.0F);
         JournalUi.draw(g, ts, x + 22, y + 4, JournalUi.INK);
         JournalUi.draw(g, ss, x + 22, y + 15, JournalUi.MUTED);
         JournalUi.drawRight(g, rs, x + w, y + 5, JournalUi.INK_BROWN);
         JournalUi.bar(g, x + w - rw + 8, y + 17, rw - 8, 2.0F, frac, JournalUi.TRACK, JournalUi.GOLD);
      })).onClick((mx, my, x, y, w) -> {
         click();
         action.run();
         return true;
      });
   }

   /** The unfinished checklist entries closest to done (any progress first), skipping species ones with none. */
   private static List<Checklist.Entry> nextEntries(HunterRecord r, int n) {
      List<Checklist.Entry> list = new ArrayList<>();
      for (Checklist.Entry e : Checklist.visible()) {
         if (!r.done.contains(e.id()) && e.category() != Checklist.Category.SCHOOL) {
            list.add(e);
         }
      }
      list.sort((a, b) -> {
         float fa = (float)r.get(a.counter()) / a.target(), fb = (float)r.get(b.counter()) / b.target();
         if (fa != fb) {
            return Float.compare(fb, fa);
         }
         return Integer.compare(a.xp() * (a.species() != null ? 3 : 1), b.xp() * (b.species() != null ? 3 : 1)); // cheap ones first, species last
      });
      return list.size() > n ? list.subList(0, n) : list;
   }

   /** [ledger] icon reference of a checklist entry: species -> their print, else the entry's own (atlas or item). */
   public static String entryIcon(Checklist.Entry e) {
      if (e.category() == Checklist.Category.HUNTS && e.icon() != null && !e.icon().isEmpty()) {
         return e.icon(); // [hunts] milestone icon (hunt atlas)
      }
      if (e.species() != null) {
         return JournalIcons.has("sp_" + e.species()) ? JournalIcons.ref("sp_" + e.species()) : JournalIcons.ref("species");
      }
      return e.icon() == null || e.icon().isEmpty() ? JournalIcons.ref(categoryIcon(e.category())) : e.icon();
   }

   private static String categoryIcon(Checklist.Category c) {
      String n = JournalIcons.atlasName(c.icon);
      return n == null ? "checklist" : n;
   }

   /** [ledger] Field School lessons in the journal's own icon set. */
   private static String lessonIcon(Lesson l) {
      return JournalIcons.ref(switch (l) {
         case WIND -> "wind";
         case SIGN -> "tracking";
         case GLASS -> "glass";
         case STALK -> "stalking";
         case SHOT -> "marksmanship";
         case TRAIL -> "blood";
         case HARVEST -> "butchery";
         case TIPS -> "camp";
      });
   }

   private static String progressText(Checklist.Entry e, int v) {
      int shown = Math.min(v, e.target());
      return switch (e.unit()) {
         case KM -> String.format(Locale.ROOT, "%.1f / %d km", shown / 1000.0, e.target() / 1000);
         case METRES -> shown + " / " + e.target() + " m";
         case INCHES -> shown + "\" / " + e.target() + "\"";
         default -> shown + " / " + e.target();
      };
   }

   private static void openGuide() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.getConnection() != null) {
         PacketDistributor.sendToServer(new ExpeditionNetwork.Request(0, ""), new CustomPacketPayload[0]);
      }
   }

   // ============================================================================================ CHECKLIST

   private String checklist(HunterRecord r) {
      Checklist.Category[] cats = Checklist.Category.values();
      int[] tot = new int[cats.length], dn = new int[cats.length];
      int all = 0, allDone = 0;
      for (Checklist.Entry e : Checklist.visible()) {
         int c = e.category().ordinal();
         tot[c]++;
         all++;
         if (r.done.contains(e.id())) {
            dn[c]++;
            allDone++;
         }
      }
      int fs = GuideClient.fieldSchoolDone();
      // filter chips
      List<int[]> chipBox = new ArrayList<>();
      List<FormattedCharSequence> chipText = new ArrayList<>();
      List<Integer> chipCat = new ArrayList<>();
      int cx = 0, cy = 0;
      for (int i = -1; i < cats.length; i++) {
         if (i >= 0 && tot[i] == 0) {
            continue;
         }
         String label = i < 0
            ? JournalUi.tr("journal.frontierhunts.cat.all") + " " + Math.round(100.0F * allDone / Math.max(1, all)) + "%"
            : JournalUi.tr(cats[i].lang()) + " " + dn[i] + "/" + tot[i];
         FormattedCharSequence s = JournalUi.seq(label, FrontierUi.Size.SMALL);
         int w = JournalUi.width(s) + 12;
         if (cx > 0 && cx + w > this.bodyW) {
            cx = 0;
            cy += 16;
         }
         chipBox.add(new int[]{cx, cy, w});
         chipText.add(s);
         chipCat.add(i);
         cx += w + 4;
      }
      int chipsH = cy + 18;
      Row chips = new Row(chipsH + 6, (g, x, y, w, mx, my, hv) -> {
         for (int i = 0; i < chipBox.size(); i++) {
            int[] b = chipBox.get(i);
            boolean sel = chipCat.get(i) == filter;
            boolean over = mx >= x + b[0] && mx < x + b[0] + b[2] && my >= y + b[1] && my < y + b[1] + 13;
            FrontierUi.rect(g, x + b[0], y + b[1], b[2], 13, 6.5F, sel ? JournalUi.BUTTON_SELECTED : (over ? 0xFFD6CDB4 : 0xFFE2D9C2));
            JournalUi.draw(g, chipText.get(i), x + b[0] + 6, y + b[1] + 3, sel ? JournalUi.SIDEBAR_TEXT : JournalUi.INK);
         }
      });
      chips.click = (mx, my, x, y, w) -> {
         for (int i = 0; i < chipBox.size(); i++) {
            int[] b = chipBox.get(i);
            if (mx >= x + b[0] && mx < x + b[0] + b[2] && my >= y + b[1] && my < y + b[1] + 13) {
               click();
               filter = chipCat.get(i);
               this.rebuild(true);
               return true;
            }
         }
         return false;
      };
      add(chips);
      for (Checklist.Category c : cats) {
         if (tot[c.ordinal()] == 0 || filter >= 0 && filter != c.ordinal()) {
            continue;
         }
         sectionRight(JournalUi.tr(c.lang()), dn[c.ordinal()] + " / " + tot[c.ordinal()], (float)dn[c.ordinal()] / tot[c.ordinal()], JournalIcons.ref(categoryIcon(c)));
         space(2);
         if (c == Checklist.Category.SCHOOL && fs >= 0) {
            for (Lesson l : Lesson.values()) {
               if (l.optional()) {
                  continue;
               }
               lessonRow(l, l.done(fs));
            }
         }
         String lastSpecies = null;
         for (Checklist.Entry e : Checklist.visible()) {
            if (e.category() == c) {
               if (c == Checklist.Category.HUNTS && e.species() != null && !e.species().equals(lastSpecies)) {
                  lastSpecies = e.species();
                  this.huntHeader(r, e.species()); // [hunts] one group per species, click opens its hunt card
               }
               this.entryRow(e, r.get(e.counter()), r.done.contains(e.id()));
            }
         }
         space(6);
      }
      return JournalUi.tr("journal.frontierhunts.sub.checklist", allDone, all, Math.round(100.0F * allDone / Math.max(1, all)));
   }

   private void entryRow(Checklist.Entry e, int v, boolean done) {
      String icon = entryIcon(e);
      boolean wide = this.bodyW >= 300;
      FormattedCharSequence xp = wide && e.xp() > 0 ? JournalUi.seq("+" + e.xp(), FrontierUi.Size.SMALL) : null;
      FormattedCharSequence prog = done ? JournalUi.seq(JournalUi.tr("journal.frontierhunts.check.done"), FrontierUi.Size.SMALL)
         : (e.target() == 1 ? FormattedCharSequence.EMPTY : JournalUi.seq(progressText(e, v), FrontierUi.Size.SMALL));
      int rightW = JournalUi.width(prog) + (xp == null ? 0 : 34) + 8;
      String titleText = JournalClient.checkTitle(e);
      FormattedCharSequence title = JournalUi.fit(titleText, this.bodyW - 40 - rightW, FrontierUi.Size.BODY);
      float frac = Math.min(1.0F, (float)v / e.target());
      boolean multi = e.target() > 1 && !done;
      add(new Row(20, (g, x, y, w, mx, my, hv) -> {
         if (hv) {
            FrontierUi.rect(g, x - 3, y, w + 6, 20, 3.0F, JournalUi.HOVER);
         }
         JournalUi.checkbox(g, x + 1, y + 5, done, JournalUi.GREEN);
         JournalIcons.drawRef(g, icon, x + 15, y + 2, 16, hv, 1.0F);
         JournalUi.draw(g, title, x + 36, y + (multi ? 3 : 6), done ? JournalUi.MUTED : JournalUi.INK);
         if (done) {
            g.fill(x + 36, y + 10, x + 36 + JournalUi.width(title), y + 11, 0x66243A32);
         }
         if (multi) {
            JournalUi.bar(g, x + 36, y + 14, Math.min(110, w - 40 - rightW), 2.0F, frac, JournalUi.TRACK, JournalUi.GOLD);
         }
         int rx = x + w;
         if (xp != null) {
            JournalUi.drawRight(g, xp, rx, y + 7, done ? JournalUi.FAINT : JournalUi.GOLD_DARK);
            rx -= 34;
         }
         JournalUi.drawRight(g, prog, rx, y + 7, done ? JournalUi.GREEN : JournalUi.INK_BROWN);
      })).tip(tipLines(titleText, JournalClient.checkHint(e) + (e.xp() > 0 ? "\n" + JournalUi.tr("journal.frontierhunts.check.reward", e.xp()) : ""), 220));
   }

   private void lessonRow(Lesson l, boolean done) {
      String icon = lessonIcon(l);
      FormattedCharSequence prog = JournalUi.seq(done ? JournalUi.tr("journal.frontierhunts.check.done")
         : JournalUi.tr("journal.frontierhunts.check.lesson", l.ordinal() + 1), FrontierUi.Size.SMALL);
      String t = JournalUi.tr(l.lang("title"));
      FormattedCharSequence title = JournalUi.fit(t, this.bodyW - 40 - JournalUi.width(prog) - 8, FrontierUi.Size.BODY);
      add(new Row(20, (g, x, y, w, mx, my, hv) -> {
         if (hv) {
            FrontierUi.rect(g, x - 3, y, w + 6, 20, 3.0F, JournalUi.HOVER);
         }
         JournalUi.checkbox(g, x + 1, y + 5, done, JournalUi.GREEN);
         JournalIcons.drawRef(g, icon, x + 15, y + 2, 16, hv, 1.0F);
         JournalUi.draw(g, title, x + 36, y + 6, done ? JournalUi.MUTED : JournalUi.INK);
         JournalUi.drawRight(g, prog, x + w, y + 7, done ? JournalUi.GREEN : JournalUi.INK_BROWN);
      })).onClick((mx, my, x, y, w) -> {
         click();
         this.minecraft.setScreen(new FieldSchoolScreen(this, l.ordinal()));
         return true;
      }).tip(tipLines(t, JournalUi.tr(l.lang("objective")) + "\n" + JournalUi.tr("journal.frontierhunts.check.lesson_tip"), 220));
   }

   // ============================================================================================ SKILLS

   private String skills(HunterRecord r) {
      this.para(JournalUi.tr("journal.frontierhunts.skills.intro"), JournalUi.MUTED, FrontierUi.Size.SMALL);
      int[] lv = r.levels();
      int mask = HunterSkills.clientMask;
      for (Skill s : Skill.values()) {
         int i = s.ordinal();
         int l = lv[i];
         int a = Skill.xpFor(l), b = Skill.xpFor(l + 1);
         float frac = l >= Skill.MAX_LEVEL ? 1.0F : (float)(r.skillXp[i] - a) / Math.max(1, b - a);
         String icon = s.icon;
         FormattedCharSequence name = JournalUi.seq(JournalUi.tr(s.lang("name")), FrontierUi.Size.STRONG);
         FormattedCharSequence lvl = JournalUi.seq(JournalUi.tr("journal.frontierhunts.skills.level", l), FrontierUi.Size.STRONG);
         FormattedCharSequence xpText = JournalUi.fit(l >= Skill.MAX_LEVEL
            ? JournalUi.tr("journal.frontierhunts.skills.max", num(r.skillXp[i]))
            : JournalUi.tr("journal.frontierhunts.skills.xp", num(r.skillXp[i] - a), num(b - a), l + 1), this.bodyW - 30, FrontierUi.Size.SMALL);
         int color = 0xFF000000 | s.color;
         space(4);
         add(new Row(34, (g, x, y, w, mx, my, hv) -> {
            FrontierUi.rect(g, x - 2, y, w + 4, 33, 3.0F, 0x14000000);
            FrontierUi.rect(g, x - 2, y, 2, 33, 1.0F, color);
            JournalIcons.drawRef(g, icon, x + 4, y + 8, 16, false, 1.0F);
            JournalUi.draw(g, name, x + 26, y + 4, JournalUi.INK);
            JournalUi.drawRight(g, lvl, x + w - 4, y + 4, color);
            JournalUi.bar(g, x + 26, y + 16, w - 32, 4.0F, frac, JournalUi.TRACK, color);
            JournalUi.draw(g, xpText, x + 26, y + 23, JournalUi.MUTED);
         })).tip(tipLines(JournalUi.tr(s.lang("name")), JournalUi.tr(s.lang("how")), 220));
         List<FormattedCharSequence> how = JournalUi.wrap(JournalUi.tr(s.lang("how")), this.bodyW - 8, FrontierUi.Size.SMALL);
         add(new Row(how.size() * 9 + 4, (g, x, y, w, mx, my, hv) -> {
            int yy = y + 2;
            for (FormattedCharSequence line : how) {
               JournalUi.draw(g, line, x + 4, yy, JournalUi.MUTED);
               yy += 9;
            }
         }));
         for (Perk p : Perk.values()) {
            if (p.skill != s) {
               continue;
            }
            boolean unlocked = l >= p.level;
            boolean active = p.in(mask);
            boolean disabled = unlocked && !active && mask != 0;
            FormattedCharSequence pn = JournalUi.fit(JournalUi.tr(p.lang("name")), this.bodyW - 80, FrontierUi.Size.STRONG);
            FormattedCharSequence pill = JournalUi.seq(disabled ? JournalUi.tr("journal.frontierhunts.skills.off")
               : JournalUi.tr("journal.frontierhunts.skills.at", p.level), FrontierUi.Size.SMALL);
            List<FormattedCharSequence> desc = JournalUi.wrap(JournalUi.tr(p.lang("desc")), this.bodyW - 30, FrontierUi.Size.SMALL);
            int h = 14 + desc.size() * 9 + 4;
            add(new Row(h, (g, x, y, w, mx, my, hv) -> {
               float cx = x + 12, cy = y + 7;
               if (unlocked && !disabled) {
                  FrontierUi.circle(g, cx, cy, 5.5F, JournalUi.GOLD);
                  JournalUi.tick(g, cx, cy, JournalUi.PAPER);
               } else {
                  FrontierUi.circle(g, cx, cy, 5.5F, 0xFFCFC7AF);
                  JournalUi.lock(g, cx, cy + 1.5F, 0xFF8C8A78, 0xFFCFC7AF);
               }
               JournalUi.draw(g, pn, x + 24, y + 3, unlocked ? JournalUi.INK : JournalUi.MUTED);
               int pw = JournalUi.width(pill) + 10;
               FrontierUi.rect(g, x + w - pw, y + 1, pw, 11, 5.5F, unlocked && !disabled ? JournalUi.alpha(color, 0.85F) : 0xFFD6CEB6);
               JournalUi.draw(g, pill, x + w - pw + 5, y + 3, unlocked && !disabled ? JournalUi.PAPER : JournalUi.MUTED);
               int yy = y + 14;
               for (FormattedCharSequence line : desc) {
                  JournalUi.draw(g, line, x + 24, yy, unlocked ? JournalUi.INK_BROWN : JournalUi.FAINT);
                  yy += 9;
               }
            }));
         }
         space(4);
      }
      long total = 0;
      for (int x : r.skillXp) {
         total += x;
      }
      return JournalUi.tr("journal.frontierhunts.sub.skills", num(total));
   }

   // ============================================================================================ SPECIES

   private String species(HunterRecord r) {
      com.formaworks.frontierhunts.hunts.HuntBook.Hunt openHunt = com.formaworks.frontierhunts.hunts.HuntBook.of(huntSpecies);
      if (openHunt != null) {
         return this.huntCard(r, openHunt); // [hunts]
      }
      this.para(JournalUi.tr("hunts.frontierhunts.species.intro"), JournalUi.MUTED, FrontierUi.Size.SMALL); // [hunts]
      boolean wide = this.bodyW >= 360;
      int cSeen = 34, cPhoto = wide ? 40 : 0, cTaken = 36, cBest = 54, cFirst = wide ? 74 : 0;
      int nameW = this.bodyW - cSeen - cPhoto - cTaken - cBest - cFirst;
      FormattedCharSequence hName = JournalUi.seq(JournalUi.tr("journal.frontierhunts.species.name"), FrontierUi.Size.SMALL);
      FormattedCharSequence hSeen = JournalUi.seq(JournalUi.tr("journal.frontierhunts.species.seen"), FrontierUi.Size.SMALL);
      FormattedCharSequence hPhoto = JournalUi.seq(JournalUi.tr("journal.frontierhunts.species.photos"), FrontierUi.Size.SMALL);
      FormattedCharSequence hTaken = JournalUi.seq(JournalUi.tr("journal.frontierhunts.species.taken"), FrontierUi.Size.SMALL);
      FormattedCharSequence hBest = JournalUi.seq(JournalUi.tr("journal.frontierhunts.species.best"), FrontierUi.Size.SMALL);
      FormattedCharSequence hFirst = JournalUi.seq(JournalUi.tr("journal.frontierhunts.species.first"), FrontierUi.Size.SMALL);
      add(new Row(16, (g, x, y, w, mx, my, hv) -> {
         int cx = x + nameW;
         JournalUi.draw(g, hName, x + 22, y + 4, JournalUi.GOLD_DARK);
         JournalUi.drawRight(g, hSeen, cx += cSeen, y + 4, JournalUi.GOLD_DARK);
         if (cPhoto > 0) {
            JournalUi.drawRight(g, hPhoto, cx += cPhoto, y + 4, JournalUi.GOLD_DARK);
         }
         JournalUi.drawRight(g, hTaken, cx += cTaken, y + 4, JournalUi.GOLD_DARK);
         JournalUi.drawRight(g, hBest, cx += cBest, y + 4, JournalUi.GOLD_DARK);
         if (cFirst > 0) {
            JournalUi.drawRight(g, hFirst, cx + cFirst, y + 4, JournalUi.GOLD_DARK);
         }
         g.fill(x, y + 14, x + w, y + 15, JournalUi.RULE);
      }));
      int taken = 0, photographed = 0, seen = 0, idx = 0;
      for (Quarry q : Quarry.values()) {
         HunterRecord.SpeciesLog log = r.species.get(q.id);
         int sSeen = log == null ? 0 : log.seen, sPhoto = log == null ? 0 : log.photos, sTaken = log == null ? 0 : log.harvested;
         if (sTaken > 0) {
            taken++;
         }
         if (sPhoto > 0) {
            photographed++;
         }
         if (sSeen > 0) {
            seen++;
         }
         boolean known = sSeen + sPhoto + sTaken > 0;
         String best = "—";
         if (log != null && log.bestScore > 0) {
            best = log.bestScore + "\"";
         } else if (log != null && log.bestWeight > 0) {
            best = log.bestWeight >= 100 ? Math.round(log.bestWeight / 10.0F) + " kg" : String.format(Locale.ROOT, "%.1f kg", log.bestWeight / 10.0);
         }
         String nameText = JournalUi.tr("entity.frontierhunts." + q.id);
         String icon = JournalIcons.ref("sp_" + q.id);
         FormattedCharSequence nm = JournalUi.fit(nameText, nameW - 26 - com.formaworks.frontierhunts.hunts.client.HuntCardView.PIPS_W - 4, FrontierUi.Size.BODY); // [hunts] room for the hunt pips
         FormattedCharSequence vSeen = JournalUi.seq(sSeen == 0 ? "·" : num(sSeen), FrontierUi.Size.SMALL);
         FormattedCharSequence vPhoto = JournalUi.seq(sPhoto == 0 ? "·" : num(sPhoto), FrontierUi.Size.SMALL);
         FormattedCharSequence vTaken = JournalUi.seq(sTaken == 0 ? "·" : num(sTaken), FrontierUi.Size.STRONG);
         FormattedCharSequence vBest = JournalUi.seq(best, FrontierUi.Size.SMALL);
         FormattedCharSequence vFirst = JournalUi.fit(log == null || log.firstDate.isEmpty() ? "—" : log.firstDate, cFirst - 4, FrontierUi.Size.SMALL);
         boolean band = idx++ % 2 == 0;
         StringBuilder tip = new StringBuilder();
         tip.append(JournalUi.tr("journal.frontierhunts.species.tip", sSeen, sPhoto, sTaken));
         if (log != null && log.longestShot > 0) {
            tip.append('\n').append(JournalUi.tr("journal.frontierhunts.species.tip_shot", Math.round(log.longestShot / 10.0F)));
         }
         if (log != null && log.bestScore > 0) {
            tip.append('\n').append(JournalUi.tr("journal.frontierhunts.species.tip_score", log.bestScore));
         }
         if (log != null && log.bestWeight > 0) {
            tip.append('\n').append(JournalUi.tr("journal.frontierhunts.species.tip_weight", best.endsWith("\"") ? Math.round(log.bestWeight / 10.0F) + " kg" : best));
         }
         if (log != null && !log.firstDate.isEmpty()) {
            tip.append('\n').append(JournalUi.tr("journal.frontierhunts.species.tip_first", log.firstDate));
         }
         if (!known) {
            tip.append('\n').append(JournalUi.tr("journal.frontierhunts.species.tip_unknown"));
         }
         com.formaworks.frontierhunts.hunts.HuntBook.Hunt hunt = com.formaworks.frontierhunts.hunts.HuntBook.of(q.id); // [hunts]
         if (hunt != null) {
            tip.append('\n').append(JournalUi.tr("hunts.frontierhunts.species.tip_hunt",
               com.formaworks.frontierhunts.hunts.client.HuntCardView.done(r, hunt), hunt.milestones().size()));
         }
         Row speciesRow = add(new Row(19, (g, x, y, w, mx, my, hv) -> {
            if (hv) {
               FrontierUi.rect(g, x - 3, y, w + 6, 19, 3.0F, JournalUi.HOVER);
            } else if (band) {
               g.fill(x - 3, y, x + w + 3, y + 19, 0x0E3B2E20);
            }
            JournalIcons.drawRef(g, icon, x + 1, y + 1, 16, hv, known ? 1.0F : 0.4F);
            JournalUi.draw(g, nm, x + 22, y + 6, known ? JournalUi.INK : JournalUi.FAINT);
            if (hunt != null) {
               com.formaworks.frontierhunts.hunts.client.HuntCardView.pips(g, r, hunt, x + nameW - 4, y + 7); // [hunts] hunt progress
            }
            int cx = x + nameW;
            JournalUi.drawRight(g, vSeen, cx += cSeen, y + 7, JournalUi.MUTED);
            if (cPhoto > 0) {
               JournalUi.drawRight(g, vPhoto, cx += cPhoto, y + 7, JournalUi.MUTED);
            }
            JournalUi.drawRight(g, vTaken, cx += cTaken, y + 6, sTaken > 0 ? JournalUi.GREEN : JournalUi.FAINT);
            JournalUi.drawRight(g, vBest, cx += cBest, y + 7, JournalUi.INK_BROWN);
            if (cFirst > 0) {
               JournalUi.drawRight(g, vFirst, cx + cFirst, y + 7, JournalUi.MUTED);
            }
         })).tip(tipLines(nameText, tip.toString(), 220));
         if (hunt != null) {
            speciesRow.onClick((mx, my, x, y, w) -> { // [hunts] open the species hunt card
               click();
               huntSpecies = q.id;
               this.rebuild(true);
               return true;
            });
         }
      }
      space(6);
      this.para(JournalUi.tr("journal.frontierhunts.species.footer"), JournalUi.MUTED, FrontierUi.Size.SMALL);
      return JournalUi.tr("journal.frontierhunts.sub.species", taken, Quarry.values().length, seen, photographed);
   }

   // ============================================================================================ [hunts] species hunt card

   private String huntCard(HunterRecord r, com.formaworks.frontierhunts.hunts.HuntBook.Hunt h) {
      for (com.formaworks.frontierhunts.hunts.client.HuntCardView.Line l : com.formaworks.frontierhunts.hunts.client.HuntCardView.card(r, h, this.bodyW,
         () -> {
            click();
            huntSpecies = null;
            this.rebuild(true);
         }, sp -> {
            click();
            huntSpecies = sp;
            this.rebuild(true);
         })) {
         Row row = add(new Row(l.h(), (g, x, y, w, mx, my, hv) -> l.draw().draw(g, x, y, w, mx, my, hv)));
         if (l.click() != null) {
            row.click = (mx, my, x, y, w) -> l.click().click(mx, my, x, y, w);
         }
         if (l.tip() != null) {
            row.tip(l.tip());
         }
      }
      int done = com.formaworks.frontierhunts.hunts.client.HuntCardView.done(r, h);
      return JournalUi.tr("hunts.frontierhunts.sub.card", JournalUi.tr("entity.frontierhunts." + h.species()), done, h.milestones().size());
   }

   /** Checklist → Hunts: a species header (track, name, pips); click opens its hunt card on the Species page. */
   private void huntHeader(HunterRecord r, String sp) {
      com.formaworks.frontierhunts.hunts.HuntBook.Hunt h = com.formaworks.frontierhunts.hunts.HuntBook.of(sp);
      if (h == null) {
         return;
      }
      FormattedCharSequence nm = JournalUi.fit(JournalUi.tr("entity.frontierhunts." + sp), this.bodyW - 80, FrontierUi.Size.STRONG);
      String icon = JournalIcons.ref(JournalIcons.has("sp_" + sp) ? "sp_" + sp : "species");
      space(3);
      add(new Row(18, (g, x, y, w, mx, my, hv) -> {
         if (hv) {
            FrontierUi.rect(g, x - 3, y, w + 6, 18, 3.0F, JournalUi.HOVER);
         }
         JournalIcons.drawRef(g, icon, x + 1, y + 1, 16, hv, 1.0F);
         JournalUi.draw(g, nm, x + 22, y + 5, JournalUi.INK_BROWN);
         com.formaworks.frontierhunts.hunts.client.HuntCardView.pips(g, r, h, x + w - 2, y + 7);
         g.fill(x + 22, y + 16, x + w, y + 17, 0x40B89B6E);
      })).onClick((mx, my, x, y, w) -> {
         click();
         huntSpecies = sp;
         int si = this.tabKeys.indexOf("species");
         if (si >= 0) {
            this.selectTab(si);
         }
         this.rebuild(true);
         return true;
      }).tip(tipLines(JournalUi.tr("entity.frontierhunts." + sp), JournalUi.tr("hunts.frontierhunts.check.open_card"), 200));
   }

   // ============================================================================================ RECORDS

   private String records(HunterRecord r) {
      int shots = r.get(Stat.SHOTS), hits = r.get(Stat.HITS), kills = r.get(Stat.KILLS), clean = r.get(Stat.CLEAN_KILLS);
      int rec = r.get(Stat.RECOVERIES), lost = r.get(Stat.LOST);
      section(JournalUi.tr("journal.frontierhunts.records.shooting"));
      List<String[]> s1 = new ArrayList<>();
      s1.add(stat("shots", num(shots)));
      s1.add(stat("hits", num(hits)));
      s1.add(stat("hit_rate", shots == 0 ? "—" : Math.round(100.0 * Math.min(hits, shots) / shots) + "%"));
      s1.add(stat("kills", num(kills)));
      s1.add(stat("clean", num(clean)));
      s1.add(stat("clean_rate", kills == 0 ? "—" : Math.round(100.0 * clean / kills) + "%"));
      s1.add(stat("avg_shot", kills == 0 ? "—" : Math.round(r.get(Stat.SHOT_SUM_DM) / 10.0 / kills) + " m"));
      s1.add(stat("longest_shot", r.get(Stat.LONGEST_SHOT) == 0 ? "—" : r.get(Stat.LONGEST_SHOT) + " m"));
      s1.add(stat("bow_rifle", num(r.get(Stat.BOW_KILLS)) + " / " + num(r.get(Stat.RIFLE_KILLS))));
      s1.add(stat("prone", num(r.get(Stat.PRONE_SHOTS))));
      this.grid(s1);
      section(JournalUi.tr("journal.frontierhunts.records.tracking"));
      List<String[]> s2 = new ArrayList<>();
      s2.add(stat("recovered", num(rec)));
      s2.add(stat("lost", num(lost)));
      s2.add(stat("recovery_rate", rec + lost == 0 ? "—" : Math.round(100.0 * rec / (rec + lost)) + "%"));
      s2.add(stat("longest_trail", r.get(Stat.LONGEST_TRAIL) == 0 ? "—" : r.get(Stat.LONGEST_TRAIL) + " m"));
      s2.add(stat("signs", num(r.get(Stat.SIGNS))));
      s2.add(stat("stalks", num(r.get(Stat.STALKS))));
      s2.add(stat("calls", num(r.get(Stat.CALLS))));
      s2.add(stat("photos", num(r.get(Stat.PHOTOS))));
      s2.add(stat("walked", String.format(Locale.ROOT, "%.1f km", r.get(Stat.WALK_M) / 1000.0)));
      s2.add(stat("ridden", String.format(Locale.ROOT, "%.1f km", r.get(Stat.RIDE_M) / 1000.0)));
      int sec = r.get(Stat.FIELD_S);
      s2.add(stat("field_time", sec >= 3600 ? sec / 3600 + "h " + sec % 3600 / 60 + "m" : sec / 60 + "m"));
      s2.add(stat("harvests", num(r.get(Stat.HARVESTS))));
      this.grid(s2);
      // trophy room
      section(JournalUi.tr("journal.frontierhunts.records.trophies"));
      List<String[]> s3 = new ArrayList<>();
      for (Quarry q : new Quarry[]{Quarry.WHITETAIL, Quarry.ELK, Quarry.MOOSE}) {
         HunterRecord.SpeciesLog log = r.species.get(q.id);
         s3.add(new String[]{JournalUi.tr("journal.frontierhunts.records.best_rack", JournalUi.tr("entity.frontierhunts." + q.id)),
            log == null || log.bestScore == 0 ? "—" : log.bestScore + "\""});
      }
      String heavyName = "—";
      int heavy = 0;
      int taken = 0;
      for (var e : r.species.entrySet()) {
         if (e.getValue().bestWeight > heavy) {
            heavy = e.getValue().bestWeight;
            heavyName = JournalUi.tr("entity.frontierhunts." + e.getKey());
         }
         if (e.getValue().harvested > 0) {
            taken++;
         }
      }
      s3.add(new String[]{JournalUi.tr("journal.frontierhunts.records.heaviest"), heavy == 0 ? "—" : heavyName + " · " + Math.round(heavy / 10.0F) + " kg"});
      s3.add(new String[]{JournalUi.tr("journal.frontierhunts.records.species"), taken + " / " + Quarry.values().length});
      this.grid(s3);
      // season summaries
      section(JournalUi.tr("journal.frontierhunts.records.seasons"));
      if (r.summaries.isEmpty()) {
         this.para(JournalUi.tr("journal.frontierhunts.records.no_seasons"), JournalUi.MUTED, FrontierUi.Size.SMALL);
      }
      int i = 0;
      for (HunterRecord.Summary s : r.summaries) {
         int index = i++;
         FormattedCharSequence t = JournalUi.seq(s.title(), FrontierUi.Size.STRONG);
         add(new Row(16, (g, x, y, w, mx, my, hv) -> JournalUi.draw(g, t, x, y + 5, JournalUi.INK_BROWN)));
         for (String line : s.lines()) {
            FormattedCharSequence ls = JournalUi.fit("·  " + line, this.bodyW - 6, FrontierUi.Size.SMALL);
            add(new Row(10, (g, x, y, w, mx, my, hv) -> JournalUi.draw(g, ls, x + 4, y + 1, JournalUi.INK)));
         }
         StringBuilder copy = new StringBuilder(s.title()).append('\n');
         s.lines().forEach(l -> copy.append(l).append('\n'));
         String text = copy.toString();
         this.buttons(new String[]{JournalUi.tr("journal.frontierhunts.records.copy"), JournalUi.tr("journal.frontierhunts.records.print")},
            new Runnable[]{() -> {
               Minecraft.getInstance().keyboardHandler.setClipboard(text);
               Minecraft.getInstance().player.displayClientMessage(Component.translatable("journal.frontierhunts.records.copied"), true);
            }, () -> JournalClient.ask(JournalNet.A_PRINT, index)}, null);
         space(4);
      }
      return JournalUi.tr("journal.frontierhunts.sub.records");
   }

   private static String[] stat(String key, String value) {
      return new String[]{JournalUi.tr("journal.frontierhunts.stat." + key), value};
   }

   private void grid(List<String[]> pairs) {
      boolean two = this.bodyW >= 300;
      int colW = two ? (this.bodyW - 16) / 2 : this.bodyW;
      for (int i = 0; i < pairs.size(); i += two ? 2 : 1) {
         FormattedCharSequence l1 = JournalUi.fit(pairs.get(i)[0], colW - 60, FrontierUi.Size.SMALL);
         FormattedCharSequence v1 = JournalUi.fit(pairs.get(i)[1], colW - JournalUi.width(l1) - 6, FrontierUi.Size.STRONG);
         FormattedCharSequence l2 = two && i + 1 < pairs.size() ? JournalUi.fit(pairs.get(i + 1)[0], colW - 60, FrontierUi.Size.SMALL) : null;
         FormattedCharSequence v2 = l2 == null ? null : JournalUi.fit(pairs.get(i + 1)[1], colW - JournalUi.width(l2) - 6, FrontierUi.Size.STRONG);
         add(new Row(15, (g, x, y, w, mx, my, hv) -> {
            JournalUi.draw(g, l1, x, y + 4, JournalUi.MUTED);
            JournalUi.drawRight(g, v1, x + colW, y + 3, JournalUi.INK);
            g.fill(x, y + 14, x + colW, y + 15, 0x22243A32);
            if (l2 != null) {
               int x2 = x + colW + 16;
               JournalUi.draw(g, l2, x2, y + 4, JournalUi.MUTED);
               JournalUi.drawRight(g, v2, x2 + colW, y + 3, JournalUi.INK);
               g.fill(x2, y + 14, x2 + colW, y + 15, 0x22243A32);
            }
         }));
      }
      space(4);
   }

   // ============================================================================================ NOTES

   private String notes(HunterRecord r) {
      if (r.notes.isEmpty()) {
         this.para(JournalUi.tr("journal.frontierhunts.notes.empty"), JournalUi.MUTED, FrontierUi.Size.BODY);
      } else {
         this.para(JournalUi.tr("journal.frontierhunts.notes.intro"), JournalUi.MUTED, FrontierUi.Size.SMALL);
         for (HunterRecord.Note n : r.notes) {
            this.noteRow(n);
         }
      }
      return JournalUi.tr("journal.frontierhunts.sub.notes", r.notes.size());
   }

   private static final int[] NOTE_COLORS = {0xFF8E3B32, 0xFF3E6E37, 0xFFB99859, 0xFF5E7F4A, 0xFF3F6C78, 0xFF6F98AE, 0xFF6A7266};

   /** [ledger] Field-note icon: by the note's text key where it says more than the kind (blizzard, shed, bones ...). */
   public static String noteIcon(HunterRecord.Note n) {
      String t = n.text() == null ? "" : n.text();
      String key = t.startsWith("@") ? t.substring(1, t.indexOf('|') > 0 ? t.indexOf('|') : t.length()) : "";
      String name = switch (key) {
         case "journal.frontierhunts.note.blizzard" -> "snowflake";
         case "journal.frontierhunts.note.shed" -> "antlers";
         case "journal.frontierhunts.note.bones" -> "skull";
         case "journal.frontierhunts.note.killsite" -> "predator";
         case "journal.frontierhunts.note.event" -> "podium";
         case "journal.frontierhunts.note.summary" -> "records";
         case "journal.frontierhunts.note.rank" -> "star";
         default -> switch (n.kind()) {
            case 0 -> "antlers";
            case 1 -> "checklist";
            case 2, 3 -> "star";
            case 4 -> "world";
            case 5 -> "leaf";
            default -> "quill";
         };
      };
      return JournalIcons.ref(name);
   }

   private void noteRow(HunterRecord.Note n) {
      FormattedCharSequence stamp = JournalUi.seq(n.stamp(), FrontierUi.Size.SMALL);
      List<FormattedCharSequence> lines = JournalUi.wrap(JournalClient.noteText(n.text()), this.bodyW - 24, FrontierUi.Size.BODY);
      int color = NOTE_COLORS[Math.floorMod(n.kind(), NOTE_COLORS.length)];
      String icon = noteIcon(n);
      add(new Row(Math.max(22, 13 + lines.size() * 10 + 5), (g, x, y, w, mx, my, hv) -> {
         JournalIcons.drawRef(g, icon, x, y + 1, 16, false, 1.0F);
         JournalUi.draw(g, stamp, x + 22, y + 3, JournalUi.GOLD_DARK);
         int yy = y + 13;
         for (FormattedCharSequence l : lines) {
            JournalUi.draw(g, l, x + 22, yy, JournalUi.INK);
            yy += 10;
         }
         if (yy - 2 > y + 20) {
            g.fill(x + 7, y + 19, x + 8, yy - 2, JournalUi.alpha(color, 0.35F));
         }
      }));
   }

   // ============================================================================================ RESERVE (the old journal, condensed)

   private String reserve() {
      HuntNetwork.Snapshot s = FrontierClient.state;
      HuntNetwork.HuntProgress h = FrontierClient.hunt;
      JournalNet.Data d = JournalClient.data;
      if (s == null) {
         this.para(JournalUi.tr("journal.frontierhunts.reserve.waiting"), JournalUi.INK, FrontierUi.Size.BODY);
      } else if (!s.active()) {
         this.para(JournalUi.tr("journal.frontierhunts.reserve.inactive"), JournalUi.INK, FrontierUi.Size.BODY);
      } else {
         section(JournalUi.tr("journal.frontierhunts.reserve.conditions"));
         Wilderness.Wind wind = new Wilderness.Wind(s.windEast(), s.windSouth());
         this.para(JournalUi.tr("journal.frontierhunts.reserve.wind", wind.directionTo(), String.format(Locale.ROOT, "%.1f", wind.speed()))
            + " " + JournalUi.tr(s.rain() ? "journal.frontierhunts.reserve.rain" : "journal.frontierhunts.reserve.dry"), JournalUi.INK, FrontierUi.Size.BODY);
         this.para(biomeName(s.biome()) + " — " + s.description(), JournalUi.MUTED, FrontierUi.Size.SMALL);
         section(JournalUi.tr("journal.frontierhunts.reserve.survey"));
         int found = Math.min(3, s.discovered());
         FormattedCharSequence sv = JournalUi.seq(JournalUi.tr("journal.frontierhunts.reserve.survey_value", found, s.reward()), FrontierUi.Size.SMALL);
         add(new Row(18, (g, x, y, w, mx, my, hv) -> {
            JournalUi.bar(g, x, y + 4, Math.min(160, w), 5.0F, found / 3.0F, JournalUi.TRACK, JournalUi.GREEN);
            JournalUi.draw(g, sv, x + Math.min(160, w) + 8, y + 3, JournalUi.INK);
         }));
         this.para(JournalUi.tr(s.surveyClaimed() ? "journal.frontierhunts.reserve.survey_done"
            : (s.surveyReady() ? "journal.frontierhunts.reserve.survey_ready" : "journal.frontierhunts.reserve.survey_more")), JournalUi.MUTED, FrontierUi.Size.SMALL);
         if (s.surveyReady() && !s.surveyClaimed()) {
            this.buttons(new String[]{JournalUi.tr("journal.frontierhunts.reserve.claim_survey")},
               new Runnable[]{() -> PacketDistributor.sendToServer(new HuntNetwork.Request(1), new CustomPacketPayload[0])}, null);
         }
         section(JournalUi.tr("journal.frontierhunts.reserve.first_hunt"));
         boolean clue = h != null && h.clue(), harvested = h != null && h.harvested(), claimed = h != null && h.claimed();
         this.stepRow(clue, JournalUi.tr("journal.frontierhunts.reserve.first_clue"));
         this.stepRow(harvested, JournalUi.tr("journal.frontierhunts.reserve.first_harvest"));
         if (claimed) {
            this.para(JournalUi.tr("journal.frontierhunts.reserve.first_paid"), JournalUi.MUTED, FrontierUi.Size.SMALL);
         } else if (clue && harvested) {
            this.buttons(new String[]{JournalUi.tr("journal.frontierhunts.reserve.claim_hunt")},
               new Runnable[]{() -> PacketDistributor.sendToServer(new HuntNetwork.Request(2), new CustomPacketPayload[0])}, null);
         }
      }
      section(JournalUi.tr("journal.frontierhunts.reserve.expedition"));
      if (d != null) {
         if (d.campaignStage() < Campaign.MISSIONS.size()) {
            Campaign.Mission m = Campaign.MISSIONS.get(d.campaignStage());
            this.para(JournalUi.tr("journal.frontierhunts.reserve.mission", d.campaignStage() + 1, Campaign.MISSIONS.size(), m.title(),
               Math.min(d.campaignCount(), m.amount()), m.amount()), JournalUi.INK, FrontierUi.Size.BODY);
            this.para(m.story(), JournalUi.MUTED, FrontierUi.Size.SMALL);
         } else {
            this.para(JournalUi.tr("journal.frontierhunts.reserve.campaign_done"), JournalUi.INK, FrontierUi.Size.BODY);
         }
         if (d.contract() >= 0 && d.contract() < Campaign.CONTRACTS.size() && d.contractState() > 0) {
            Campaign.Mission c = Campaign.CONTRACTS.get(d.contract());
            this.para(JournalUi.tr("journal.frontierhunts.reserve.contract." + d.contractState(), c.title(), Math.min(d.contractCount(), c.amount()), c.amount()),
               JournalUi.INK, FrontierUi.Size.BODY);
         } else {
            this.para(JournalUi.tr("journal.frontierhunts.reserve.no_contract"), JournalUi.MUTED, FrontierUi.Size.SMALL);
         }
      }
      this.buttons(new String[]{JournalUi.tr("journal.frontierhunts.reserve.open_guide"), JournalUi.tr("journal.frontierhunts.reserve.open_assignments")},
         new Runnable[]{JournalScreen::openGuide, () -> AssignmentScreen.send(0, "", 0)}, null);
      this.buttons(new String[]{JournalUi.tr("journal.frontierhunts.reserve.open_school"), JournalUi.tr("journal.frontierhunts.reserve.open_settings")},
         new Runnable[]{() -> this.minecraft.setScreen(new FieldSchoolScreen(this, -1)), () -> this.minecraft.setScreen(new FrontierSettingsScreen(this))}, null);
      if (s != null && !s.recentBiomes().isEmpty()) {
         section(JournalUi.tr("journal.frontierhunts.reserve.habitats", s.discovered()));
         List<String> rb = s.recentBiomes().reversed();
         StringBuilder b = new StringBuilder();
         for (int i = 0; i < rb.size(); i++) {
            if (i > 0) {
               b.append("  ·  ");
            }
            b.append(biomeName(rb.get(i)));
         }
         this.para(b.toString(), JournalUi.INK, FrontierUi.Size.SMALL);
      }
      section(JournalUi.tr("journal.frontierhunts.reserve.fieldcraft"));
      this.para(JournalUi.tr("journal.frontierhunts.reserve.fieldcraft_text"), JournalUi.INK, FrontierUi.Size.SMALL);
      return s != null && s.active() ? s.region() : JournalUi.tr("journal.frontierhunts.sub.reserve");
   }

   private void stepRow(boolean done, String text) {
      FormattedCharSequence t = JournalUi.fit(text, this.bodyW - 20, FrontierUi.Size.BODY);
      add(new Row(17, (g, x, y, w, mx, my, hv) -> {
         JournalUi.checkbox(g, x + 1, y + 3, done, JournalUi.GREEN);
         JournalUi.draw(g, t, x + 18, y + 4, done ? JournalUi.MUTED : JournalUi.INK);
      }));
   }

   // ============================================================================================ extra pages

   private String extraPage(String id) {
      JournalPageView view = JournalPageView.Views.get(id);
      CompoundTag tag = new CompoundTag();
      if (JournalClient.data != null) {
         for (JournalNet.PageData p : JournalClient.data.pages()) {
            if (p.id().equals(id)) {
               tag = p.tag();
            }
         }
      }
      if (view == null) {
         return "";
      }
      CompoundTag data = tag;
      int h;
      try {
         h = Math.max(0, view.height(data, this.bodyW));
      } catch (RuntimeException e) {
         h = 0;
      }
      Row pageRow = add(new Row(h, (g, x, y, w, mx, my, hv) -> {
         try {
            view.render(g, data, x, y, w, mx, my);
         } catch (RuntimeException ignored) {
         }
      }));
      pageRow.click = (mx, my, x, y, w) -> { // [licence] pages with buttons (no row highlight)
         try {
            return view.click(data, x, y, w, mx, my);
         } catch (RuntimeException e) {
            return false;
         }
      };
      return "";
   }

   // ============================================================================================ render

   @Override
   public void tick() {
      if (this.builtVersion != JournalClient.version || this.builtFieldSchool != GuideClient.fieldSchoolDone()
         || this.builtHandbook != com.formaworks.frontierhunts.guide.client.HandbookClient.mask()) { // [onboard]
         this.rebuild(false);
      }
      if (System.currentTimeMillis() - this.dateAt > 1000L) {
         this.updateDate();
      }
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float pt) {
      long now = System.nanoTime();
      float dt = this.lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - this.lastFrame) / 1.0E9F);
      this.lastFrame = now;
      int max = this.maxScroll();
      this.scrollTarget = Mth.clamp(this.scrollTarget, 0, max);
      if (com.formaworks.frontierhunts.HuntConfig.REDUCED_MOTION.get()) {
         this.scroll = this.scrollTarget;
      } else {
         this.scroll += (this.scrollTarget - this.scroll) * Math.min(1.0F, dt * 16.0F);
         if (Math.abs(this.scrollTarget - this.scroll) < 0.3F) {
            this.scroll = this.scrollTarget;
         }
      }
      g.fill(0, 0, this.width, this.height, 0xB0101208);
      JournalUi.notebook(g, this.left, this.top, this.panelW, this.panelH, this.side);
      this.sidebar(g, mx, my);
      // header
      JournalUi.draw(g, this.title, this.bodyX, this.top + 14, JournalUi.INK);
      JournalUi.draw(g, this.subtitle, this.bodyX, this.top + 32, JournalUi.MUTED);
      g.fill(this.bodyX, this.top + 45, this.left + this.panelW - 16, this.top + 46, JournalUi.RULE);
      // close
      int cx = this.left + this.panelW - 28, cy = this.top + 10;
      boolean overClose = mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18;
      JournalUi.button(g, cx, cy, 18, 18, CLOSE, overClose, false, true);
      // content
      int right = this.left + this.panelW - 12;
      g.enableScissor(this.bodyX - 6, this.viewTop, right, this.viewBottom);
      int base = this.viewTop - Math.round(this.scroll);
      boolean inView = mx >= this.bodyX - 6 && mx < right && my >= this.viewTop && my < this.viewBottom;
      Row over = null;
      for (Row r : this.rows) {
         int y = base + r.y;
         if (y + r.h < this.viewTop || y > this.viewBottom) {
            continue;
         }
         boolean hv = inView && my >= y && my < y + r.h && (r.highlight || r.tip != null);
         if (hv) {
            over = r;
         }
         r.draw.draw(g, this.bodyX, y, this.bodyW, mx, my, hv && r.highlight);
      }
      g.disableScissor();
      this.hovered = over;
      // fade at the edges of the scroll view
      if (this.scroll > 0.5F) {
         g.fillGradient(this.bodyX - 6, this.viewTop, right, this.viewTop + 6, 0x40B9A984, 0x00EAE3D0);
      }
      if (max > 0) {
         int track = this.viewBottom - this.viewTop;
         int thumb = Math.max(14, track * track / (track + max));
         int ty = this.viewTop + Math.round((track - thumb) * this.scroll / max);
         int sx = this.left + this.panelW - 10;
         FrontierUi.rect(g, sx, this.viewTop, 3, track, 1.5F, 0x30243A32);
         FrontierUi.rect(g, sx, ty, 3, thumb, 1.5F, this.dragging ? JournalUi.GOLD_DARK : JournalUi.GOLD);
      }
      super.render(g, mx, my, pt);
      if (over != null && over.tip != null && !over.tip.isEmpty()) {
         g.renderTooltip(this.font, over.tip, mx, my);
      }
   }

   private static final FormattedCharSequence CLOSE = FormattedCharSequence.forward("×", net.minecraft.network.chat.Style.EMPTY);

   private void sidebar(GuiGraphics g, int mx, int my) {
      int x = this.left, y = this.top;
      g.fill(x + 9, y + 11, x + 11, y + 44, JournalUi.GOLD);
      JournalUi.draw(g, this.brand1, x + 16, y + 12, JournalUi.SIDEBAR_MUTED);
      JournalUi.draw(g, this.brand2, x + 16, y + 21, JournalUi.SIDEBAR_TEXT);
      if (this.rankLine != null) {
         JournalUi.draw(g, this.rankLine, x + 16, y + 34, JournalUi.GOLD);
      }
      for (int i = 0; i < this.tabKeys.size(); i++) {
         int ty = this.tabTop + i * this.tabH;
         int h = this.tabH - 3;
         boolean sel = i == this.tab;
         boolean over = mx >= x + 6 && mx < x + this.side - 8 && my >= ty && my < ty + h;
         int bg = sel ? 0xFFEAE3D0 : (over ? 0xFF5C4636 : 0x00000000);
         if (sel) {
            // the selected page tab is paper coloured and runs into the page
            FrontierUi.rect(g, x + 6, ty, this.side - 6, h, 2.0F, bg);
         } else if (over) {
            FrontierUi.rect(g, x + 6, ty, this.side - 14, h, 2.0F, bg);
         }
         // [ledger] original pixel-art tab icons; hover / selected lift with the brightened art
         String icon = this.tabIcons.get(i);
         int isz = h >= 18 ? 16 : 12;
         int iy = ty + (h - isz) / 2;
         if (h >= 12) {
            String an = JournalIcons.atlasName(icon);
            if (an != null) {
               JournalIcons.drawLifted(g, an, x + 10, iy, isz, sel || over);
            } else {
               JournalIcons.drawRef(g, icon, x + 10, iy, isz, false, 1.0F);
            }
         }
         JournalUi.draw(g, this.tabLabels.get(i), x + 30, ty + (h - 8) / 2, sel ? JournalUi.INK : JournalUi.SIDEBAR_TEXT);
         if (i < 9 && this.side >= 120) {
            FormattedCharSequence k = DIGITS[i];
            JournalUi.drawRight(g, k, x + this.side - 12, ty + (h - 8) / 2, sel ? JournalUi.FAINT : 0xFF8E7B62);
         }
      }
      // Field School + tokens at the bottom
      int by = this.top + this.panelH - 44;
      boolean overSchool = mx >= x + 8 && mx < x + this.side - 10 && my >= by && my < by + 17;
      FrontierUi.rect(g, x + 8, by, this.side - 18, 17, 2.0F, overSchool ? JournalUi.BUTTON_HOVER : JournalUi.BUTTON);
      FrontierUi.rect(g, x + 8, by, 2, 17, 1.0F, JournalUi.GOLD);
      JournalIcons.drawLifted(g, "school", x + 12, by + 1, 16, overSchool); // [ledger]
      JournalUi.draw(g, this.schoolLabel, x + 30, by + 5, JournalUi.SIDEBAR_TEXT);
      if (this.tokenLine != null) {
         JournalIcons.draw(g, "token", x + 10, by + 21, 12, false, 1.0F);
         JournalUi.draw(g, this.tokenLine, x + 25, by + 24, JournalUi.SIDEBAR_MUTED);
      }
   }

   private static final FormattedCharSequence[] DIGITS = new FormattedCharSequence[9];

   static {
      for (int i = 0; i < 9; i++) {
         DIGITS[i] = FormattedCharSequence.forward(Integer.toString(i + 1), net.minecraft.network.chat.Style.EMPTY);
      }
   }

   // ============================================================================================ input

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (button == 0) {
         // close
         int cx = this.left + this.panelW - 28, cy = this.top + 10;
         if (mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18) {
            click();
            this.onClose();
            return true;
         }
         // tabs
         for (int i = 0; i < this.tabKeys.size(); i++) {
            int ty = this.tabTop + i * this.tabH;
            if (mx >= this.left + 6 && mx < this.left + this.side - 4 && my >= ty && my < ty + this.tabH - 3) {
               this.selectTab(i);
               return true;
            }
         }
         int by = this.top + this.panelH - 44;
         if (mx >= this.left + 8 && mx < this.left + this.side - 10 && my >= by && my < by + 17) {
            click();
            this.minecraft.setScreen(new FieldSchoolScreen(this, -1));
            return true;
         }
         // scrollbar
         int sx = this.left + this.panelW - 12;
         if (this.maxScroll() > 0 && mx >= sx && mx < sx + 7 && my >= this.viewTop && my < this.viewBottom) {
            this.dragging = true;
            this.dragTo(my);
            return true;
         }
         // rows
         if (mx >= this.bodyX - 6 && mx < this.left + this.panelW - 12 && my >= this.viewTop && my < this.viewBottom) {
            int base = this.viewTop - Math.round(this.scroll);
            for (Row r : this.rows) {
               int y = base + r.y;
               if (r.click != null && my >= y && my < y + r.h && r.click.click(mx, my, this.bodyX, y, this.bodyW)) {
                  return true;
               }
            }
         }
      }
      return super.mouseClicked(mx, my, button);
   }

   private void dragTo(double my) {
      int max = this.maxScroll();
      int track = this.viewBottom - this.viewTop;
      int thumb = Math.max(14, track * track / (track + max));
      double f = (my - this.viewTop - thumb / 2.0) / Math.max(1, track - thumb);
      this.scrollTarget = (int)Math.round(Mth.clamp(f, 0.0, 1.0) * max);
      this.scroll = this.scrollTarget;
   }

   @Override
   public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      if (this.dragging) {
         this.dragTo(my);
         return true;
      }
      return super.mouseDragged(mx, my, button, dx, dy);
   }

   @Override
   public boolean mouseReleased(double mx, double my, int button) {
      this.dragging = false;
      return super.mouseReleased(mx, my, button);
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      if (mx < this.left + this.side && mx >= this.left) {
         this.selectTab(this.tab + (sy < 0 ? 1 : -1));
         return true;
      }
      this.scrollTarget = Mth.clamp(this.scrollTarget - (int)Math.round(sy * 30.0), 0, this.maxScroll());
      return true;
   }

   @Override
   public boolean keyPressed(int key, int scan, int mods) {
      int view = this.viewBottom - this.viewTop;
      switch (key) {
         case GLFW.GLFW_KEY_PAGE_UP -> this.scrollTarget = Math.max(0, this.scrollTarget - view + 20);
         case GLFW.GLFW_KEY_PAGE_DOWN -> this.scrollTarget = Math.min(this.maxScroll(), this.scrollTarget + view - 20);
         case GLFW.GLFW_KEY_UP -> this.scrollTarget = Math.max(0, this.scrollTarget - 24);
         case GLFW.GLFW_KEY_DOWN -> this.scrollTarget = Math.min(this.maxScroll(), this.scrollTarget + 24);
         case GLFW.GLFW_KEY_HOME -> this.scrollTarget = 0;
         case GLFW.GLFW_KEY_END -> this.scrollTarget = this.maxScroll();
         case GLFW.GLFW_KEY_TAB -> this.selectTab(Math.floorMod(this.tab + ((mods & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1), this.tabKeys.size()));
         default -> {
            if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9 && key - GLFW.GLFW_KEY_1 < this.tabKeys.size()) {
               this.selectTab(key - GLFW.GLFW_KEY_1);
               return true;
            }
            if (FrontierClient.JOURNAL.matches(key, scan)) {
               this.onClose();
               return true;
            }
            return super.keyPressed(key, scan, mods);
         }
      }
      return true;
   }

   @Override
   public void removed() {
      JournalClient.ask(JournalNet.A_CLOSE, 0);
      super.removed();
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   // ============================================================================================ old API

   public static String biomeName(String id) {
      String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
      StringBuilder b = new StringBuilder();
      for (String part : path.replace('/', ' ').replace('_', ' ').split(" ")) {
         if (!part.isEmpty()) {
            if (!b.isEmpty()) {
               b.append(' ');
            }
            b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
         }
      }
      return b.toString();
   }

   /** [onebook] opened from the Frontier Handbook: closing goes back to the Handbook page it came from. */
   @Override
   public void onClose() {
      if (!com.formaworks.frontierhunts.onebook.client.OneBookClient.back(this)) {
         super.onClose();
      }
   }
}
