package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.Campaign;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.ExpeditionService;
import com.formaworks.frontierhunts.expedition.HuntingCalendar;
import com.formaworks.frontierhunts.guide.Lesson;
import com.formaworks.frontierhunts.guide.client.FieldSchoolScreen;
import com.formaworks.frontierhunts.guide.client.GuideClient;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.journal.client.JournalIcons;
import com.formaworks.frontierhunts.journal.client.JournalUi;
import com.formaworks.frontierhunts.season.SeasonClock;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.lwjgl.glfw.GLFW;

/**
 * [ledger] Frontier Expeditions — the Expedition journal, rebuilt in the Hunter's Journal style (leather notebook, paper
 * pages, pixel-art icons from {@link JournalIcons}). Same data and server actions as before (the expedition snapshot from
 * {@code ExpeditionService.send} and {@code ExpeditionClient.send(action, value)} requests), laid out for clarity:
 * <ul>
 * <li>a header "Next" line on every page that says what to do now and jumps to the page that does it;</li>
 * <li>Campaign: a Next-objective card (progress, how it counts, the button that completes it), chapter / report pips,
 *     the current field report, Mara's last entry, the nearby hunting party;</li>
 * <li>Contracts: the running contract with a live timer and progress, the five board contracts with reward and why a
 *     button is disabled; Stores: the station shop with item icons, prices and reasons; Training: the four expedition
 *     challenges with ranks and timers; Lodge: camp upgrades, party, shared marker, lodge records; Ledger: filed reports;
 *     Seasons: the live reserve date and season (SeasonClock), a 12-month open-season chart and conservation tags.</li>
 * </ul>
 * Row-based like the journal (text pre-built on layout, frames only draw), smooth scrolling, tooltips, keys 1-7 / Tab,
 * works from 854x480 at GUI scale 2 up to GUI scale 4.
 */
public final class ExpeditionScreen extends Screen {
   private static final String[] TABS = {"campaign", "contracts", "stores", "training", "lodge", "ledger", "seasons"};
   private static final String[] TAB_ICONS = {"campaign", "contracts", "equipment", "training", "lodge", "ledger", "seasons"};
   private static final String[] TRAINING_NAMES = {"marksman", "tracker", "survivalist", "bowfisher"};
   private static final String[] TRAINING_ICONS = {"marksmanship", "tracking", "survival", "fish"};
   private static final int[] TRAINING_TARGETS = {2, 3, 3, 2};
   private static final String[] CAMP_STATIONS = {"lodge_stores", "ammo_reloader", "bow_tuning_rack", "fishing_station"};
   /** a no-op expedition action: the server answers it with a fresh snapshot (open=false), used to keep timers live */
   private static final int ACTION_POLL = 17;
   private static int lastTab;
   private static float lastScroll;

   private int tab;
   private int left, top, panelW, panelH, side, bodyX, bodyW, viewTop, viewBottom, tabTop, tabH;
   private final List<Row> rows = new ArrayList<>();
   private final List<FormattedCharSequence> tabLabels = new ArrayList<>();
   private int contentH;
   private float scroll;
   private int scrollTarget;
   private boolean dragging;
   private long lastFrame;
   private JsonObject builtState;
   private long stateAt = System.currentTimeMillis();
   private long openedAt = System.currentTimeMillis();
   private long lastPoll;
   private long lastClick;
   private int builtSecond = -1;
   private String confirm = "";
   private long confirmAt;
   private FormattedCharSequence title = FormattedCharSequence.EMPTY;
   private FormattedCharSequence brand1, brand2, tokenLine, schoolLabel, journalLabel, assignLabel;
   private Next next;
   private FormattedCharSequence nextSeq = FormattedCharSequence.EMPTY;
   private FormattedCharSequence nextLabel = FormattedCharSequence.EMPTY;

   public ExpeditionScreen() {
      super(Component.translatable("screen.frontierhunts.expedition"));
      this.tab = Mth.clamp(lastTab, 0, TABS.length - 1);
   }

   // ============================================================================================ text

   /** Lang key with an English fallback (keys live in lang fragment ledger.json, generated from these calls). */
   static String L(String key, String fallback, Object... args) {
      String k = "expedition.frontierhunts.ui." + key;
      if (I18n.exists(k)) {
         return I18n.get(k, args);
      }
      try {
         return args.length == 0 ? fallback : String.format(Locale.ROOT, fallback, args);
      } catch (RuntimeException e) {
         return fallback;
      }
   }

   private static FormattedCharSequence seq(String s, FrontierUi.Size size) {
      return JournalUi.seq(s, size);
   }

   private static FormattedCharSequence fit(String s, int w, FrontierUi.Size size) {
      return JournalUi.fit(s, w, size);
   }

   private static String clock(long seconds) {
      long s = Math.max(0L, seconds);
      return s >= 3600L ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600L, s / 60L % 60L, s % 60L) : String.format(Locale.ROOT, "%d:%02d", s / 60L, s % 60L);
   }

   private static String num(long v) {
      return String.format(Locale.ROOT, "%,d", v);
   }

   // ============================================================================================ state

   private static JsonObject state() {
      return ExpeditionClient.state == null ? new JsonObject() : ExpeditionClient.state;
   }

   private static int number(String k) {
      try {
         return state().has(k) ? state().get(k).getAsInt() : 0;
      } catch (RuntimeException e) {
         return 0;
      }
   }

   private static boolean flag(String k) {
      try {
         return state().has(k) && state().get(k).getAsBoolean();
      } catch (RuntimeException e) {
         return false;
      }
   }

   private static String value(String k) {
      try {
         return state().has(k) ? state().get(k).getAsString() : "";
      } catch (RuntimeException e) {
         return "";
      }
   }

   private static List<String> strings(String k) {
      List<String> out = new ArrayList<>();
      try {
         if (state().has(k)) {
            for (JsonElement e : state().getAsJsonArray(k)) {
               out.add(e.getAsString());
            }
         }
      } catch (RuntimeException ignored) {
      }
      return out;
   }

   private static int[] trainingRanks() {
      int[] r = new int[4];
      try {
         if (state().has("skills")) {
            JsonArray a = state().getAsJsonArray("skills");
            for (int i = 0; i < Math.min(4, a.size()); i++) {
               r[i] = Mth.clamp(a.get(i).getAsInt(), 0, 3);
            }
         }
      } catch (RuntimeException ignored) {
      }
      return r;
   }

   /** Seconds left on a server countdown, ticking down locally since the snapshot arrived. */
   private long secondsLeft(String key) {
      return Math.max(0L, number(key) - (System.currentTimeMillis() - this.stateAt) / 1000L);
   }

   private static boolean loaded() {
      return state().has("stage");
   }

   // ============================================================================================ mission help

   /** How an objective counts, in plain words, from its event type (matches CampaignProgress / record call sites). */
   static String howItCounts(Campaign.Mission m) {
      if (m.event().startsWith("hunt:")) {
         return com.formaworks.frontierhunts.hunts.client.HuntContractText.how(m); // [hunts] species contracts
      }
      boolean deer = "whitetail".equals(m.species());
      return switch (m.event()) {
         case "visit" -> L("how.visit", "Counts each new biome you stand in. Keep moving through different country.");
         case "clue" -> L("how.clue", "Aim at tracks, blood or sign and press Use to inspect it, or right-click a natural rub or scrape.");
         case "harvest" -> deer ? L("how.harvest", "Counts when you finish field-dressing a whitetail with the Contour Skinning Knife.")
            : L("how.harvest_any", "Counts when you finish field-dressing the animal.");
         case "craft" -> L("how.craft", "Counts any Frontier Hunts item you make at the Frontier Workbench, the Gunsmith's Bench or the Reloading Bench.");
         case "vital" -> L("how.vital", "Counts when you field-dress a whitetail that was hit in the heart or lungs.");
         case "fish" -> L("how.fish", "Land a living fish with the float rod or the bowfishing bow.");
         case "survey" -> L("how.survey", "Hit a whitetail with a tranquilizer dart. Each deer can be surveyed once.");
         case "display" -> L("how.display", "Place a recovered trophy on a trophy plinth.");
         case "longshot" -> L("how.longshot2", "Field-dress a whitetail you shot from 150 metres or more.");
         case "group" -> L("how.group", "Field-dress a whitetail while a party member is within 96 metres.");
         default -> "";
      };
   }

   static String missionIcon(Campaign.Mission m) {
      if (m.event().startsWith("hunt:") && JournalIcons.has("sp_" + m.species())) {
         return "sp_" + m.species(); // [hunts] species contracts show the species track
      }
      return switch (m.event()) {
         case "visit" -> "world";
         case "clue" -> "tracking";
         case "harvest" -> "butchery";
         case "craft" -> "equipment";
         case "vital", "longshot" -> "marksmanship";
         case "fish" -> "fish";
         case "survey" -> "stalking";
         case "display" -> "records";
         case "group" -> "party";
         default -> "campaign";
      };
   }

   // ============================================================================================ next objective

   private record Next(String icon, String title, String detail, String progress, float frac, int tab, int kind) {
   }

   private static final int N_NONE = 0, N_STARTER = 1, N_CONTRACT_READY = 2, N_REPORT_READY = 3, N_CONTRACT = 4, N_MISSION = 5, N_BOARD = 6;

   private Next computeNext() {
      if (!loaded()) {
         return new Next("clock", L("next.waiting", "Waiting for the reserve's field records…"), "", "", 0.0F, 0, N_NONE);
      }
      int stage = number("stage");
      int contract = number("contract");
      String status = value("contract_status");
      Campaign.Mission c = contract >= 0 && contract < Campaign.CONTRACTS.size() ? Campaign.CONTRACTS.get(contract) : null;
      if (!flag("starter")) {
         return new Next("equipment", L("next.starter", "Collect your starting equipment"),
            L("next.starter_detail", "Guide, field bow and arrows, knives, float rod and an expedition board. The survey only starts once you have them."),
            "", 0.0F, 0, N_STARTER);
      }
      if (c != null && status.equals("ready")) {
         return new Next("token", L("next.contract_ready", "Collect payment: %s", c.title()), L("next.contract_ready_detail", "%s tokens are waiting. Payment earned in time stays available.", c.tokens()),
            c.amount() + " / " + c.amount(), 1.0F, 1, N_CONTRACT_READY);
      }
      if (stage < Campaign.MISSIONS.size()) {
         Campaign.Mission m = Campaign.MISSIONS.get(stage);
         int n = Math.min(number("count"), m.amount());
         if (n >= m.amount()) {
            return new Next("campaign", L("next.report_ready", "File your report: %s", m.title()), L("next.report_ready_detail", "Objective complete. File it for %s tokens.", m.tokens()),
               n + " / " + m.amount(), 1.0F, 0, N_REPORT_READY);
         }
         if (c != null && status.equals("active")) {
            int cn = Math.min(number("contract_count"), c.amount());
            return new Next("contracts", c.title(), howItCounts(c), cn + " / " + c.amount() + "  ·  " + clock(this.secondsLeft("seconds")),
               (float)cn / c.amount(), 1, N_CONTRACT);
         }
         return new Next(missionIcon(m), m.title(), howItCounts(m), n + " / " + m.amount(), (float)n / m.amount(), 0, N_MISSION);
      }
      if (c != null && status.equals("active")) {
         int cn = Math.min(number("contract_count"), c.amount());
         return new Next("contracts", c.title(), howItCounts(c), cn + " / " + c.amount() + "  ·  " + clock(this.secondsLeft("seconds")), (float)cn / c.amount(), 1, N_CONTRACT);
      }
      return new Next("contracts", L("next.board", "Take a contract from the board"), L("next.board_detail", "The campaign is filed. Contracts pay tokens and can be repeated with friends."),
         "", 0.0F, 1, N_BOARD);
   }

   // ============================================================================================ rows

   @FunctionalInterface
   private interface Draw {
      void draw(GuiGraphics g, int x, int y, int w, int mx, int my, boolean hover);
   }

   /** A clickable button inside a row, at (dx, dy) relative to the row's top-left. */
   private static final class Btn {
      final int dx, dy, w, h;
      final FormattedCharSequence label;
      final String icon;
      final boolean active;
      final boolean primary;
      final Runnable action;
      final List<FormattedCharSequence> tip;

      Btn(int dx, int dy, int w, int h, String label, String icon, boolean active, boolean primary, Runnable action, String why) {
         this.dx = dx;
         this.dy = dy;
         this.w = w;
         this.h = h;
         this.icon = icon;
         this.label = fit(label, w - (icon == null ? 8 : 24), FrontierUi.Size.SMALL);
         this.active = active;
         this.primary = primary;
         this.action = action;
         this.tip = why == null || why.isEmpty() ? null : JournalUi.wrap(why, 200, FrontierUi.Size.SMALL);
      }

      boolean over(double mx, double my, int x, int y) {
         return mx >= x + this.dx && mx < x + this.dx + this.w && my >= y + this.dy && my < y + this.dy + this.h;
      }

      void draw(GuiGraphics g, int x, int y, boolean hover) {
         int bx = x + this.dx, by = y + this.dy;
         int bg = !this.active ? 0xFF8E8A78 : (hover ? JournalUi.BUTTON_HOVER : (this.primary ? 0xFF3E5945 : JournalUi.BUTTON));
         FrontierUi.rect(g, bx, by + 1, this.w, this.h, 2.0F, 0x30000000);
         FrontierUi.rect(g, bx, by, this.w, this.h, 2.0F, bg);
         if (this.primary && this.active) {
            FrontierUi.rect(g, bx, by, 2, this.h, 1.0F, JournalUi.GOLD);
         }
         int tw = JournalUi.width(this.label) + (this.icon == null ? 0 : 16);
         int tx = bx + (this.w - tw) / 2;
         if (this.icon != null) {
            JournalIcons.draw(g, this.icon, tx - 2, by + (this.h - 16) / 2.0F + (hover && this.active ? -0.5F : 0.0F), 16, hover && this.active, this.active ? 1.0F : 0.55F);
            tx += 16;
         }
         JournalUi.draw(g, this.label, tx, by + (this.h - 7) / 2, this.active ? JournalUi.SIDEBAR_TEXT : 0xFFE4E0D2);
      }
   }

   private static final class Row {
      final int h;
      final Draw draw;
      final List<Btn> buttons = new ArrayList<>();
      Runnable click;
      List<FormattedCharSequence> tip;
      int y;

      Row(int h, Draw draw) {
         this.h = h;
         this.draw = draw;
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

   private void section(String icon, String text) {
      FormattedCharSequence s = fit(text.toUpperCase(Locale.ROOT), this.bodyW - 22, FrontierUi.Size.STRONG);
      int tx = icon == null ? 0 : 20;
      add(new Row(24, (g, x, y, w, mx, my, hv) -> {
         if (icon != null) {
            JournalIcons.draw(g, icon, x, y + 4, 16, false, 1.0F);
         }
         JournalUi.draw(g, s, x + tx, y + 9, JournalUi.INK);
         g.fill(x + tx, y + 20, x + tx + Math.min(40, w), y + 21, 0xFFB89B6E);
      }));
   }

   private void para(String text, int color, FrontierUi.Size size) {
      this.paraAt(text, color, size, 0);
   }

   private void paraAt(String text, int color, FrontierUi.Size size, int indent) {
      if (text == null || text.isEmpty()) {
         return;
      }
      List<FormattedCharSequence> lines = JournalUi.wrap(text, this.bodyW - indent, size);
      int lh = FrontierUi.lineHeight(size) + 1;
      add(new Row(lines.size() * lh + 4, (g, x, y, w, mx, my, hv) -> {
         int yy = y + 1;
         for (FormattedCharSequence l : lines) {
            JournalUi.draw(g, l, x + indent, yy, color);
            yy += lh;
         }
      }));
   }

   /** One row of equal-width buttons. */
   private Row buttonRow(List<Btn> btns) {
      Row r = new Row(26, (g, x, y, w, mx, my, hv) -> {
      });
      r.buttons.addAll(btns);
      return add(r);
   }

   private Btn btn(int dx, int w, String label, String icon, boolean active, boolean primary, Runnable action, String why) {
      return new Btn(dx, 3, w, 19, label, icon, active, primary, action, active ? null : why);
   }

   private Runnable send(int action, String value) {
      return () -> {
         this.lastClick = System.currentTimeMillis();
         ExpeditionClient.send(action, value);
      };
   }

   /** Two-click confirm for destructive buttons; the first click arms it for 4 s. */
   private Runnable confirmed(String key, Runnable action) {
      return () -> {
         if (this.confirm.equals(key) && System.currentTimeMillis() - this.confirmAt < 4000L) {
            this.confirm = "";
            action.run();
         } else {
            this.confirm = key;
            this.confirmAt = System.currentTimeMillis();
         }
         this.rebuild(false);
      };
   }

   private boolean armed(String key) {
      return this.confirm.equals(key) && System.currentTimeMillis() - this.confirmAt < 4000L;
   }

   private static void progressBar(GuiGraphics g, int x, int y, int w, float frac, int fill, FormattedCharSequence label) {
      JournalUi.bar(g, x, y, w, 5.0F, frac, JournalUi.TRACK, fill);
      if (label != null) {
         JournalUi.drawRight(g, label, x + w, y - 10, JournalUi.INK_BROWN);
      }
   }

   private static void card(GuiGraphics g, int x, int y, int w, int h, int accent, boolean hover) {
      FrontierUi.rect(g, x - 3, y, w + 6, h, 3.0F, hover ? 0x22243A32 : 0x14000000);
      FrontierUi.rect(g, x - 3, y, 2, h, 1.0F, accent);
   }

   private static void pips(GuiGraphics g, float x, float cy, int n, int filled, int current, int on, int off) {
      for (int i = 0; i < n; i++) {
         float px = x + i * 9 + 3.5F;
         if (i == current) {
            FrontierUi.circle(g, px, cy, 3.6F, JournalUi.GOLD_DARK);
            FrontierUi.circle(g, px, cy, 2.4F, JournalUi.PAPER);
         } else {
            FrontierUi.circle(g, px, cy, 3.0F, i < filled ? on : off);
         }
      }
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
      this.side = this.panelW < 470 ? 104 : 130;
      this.bodyX = this.left + this.side + 18;
      this.bodyW = this.panelW - this.side - 36;
      this.viewTop = this.top + 56;
      this.viewBottom = this.top + this.panelH - 10;
      this.tabTop = this.top + 52;
      int avail = this.panelH - 52 - 66;
      this.tabH = Mth.clamp(avail / TABS.length, 14, 24);
      this.tabLabels.clear();
      for (String t : TABS) {
         this.tabLabels.add(fit(L("tab." + t, Character.toUpperCase(t.charAt(0)) + t.substring(1)), this.side - 36, FrontierUi.Size.BODY));
      }
      this.brand1 = seq(L("brand1", "FRONTIER"), FrontierUi.Size.SMALL);
      this.brand2 = fit(L("brand2", "Expeditions"), this.side - 26, FrontierUi.Size.STRONG);
      this.journalLabel = fit(L("side.journal", "Journal"), (this.side - 22) / 2 - 18, FrontierUi.Size.SMALL);
      this.assignLabel = fit(L("side.assignments", "Ranger"), (this.side - 22) / 2 - 18, FrontierUi.Size.SMALL);
      this.rebuild(false);
      this.scroll = Math.min(lastScroll, this.maxScroll());
      this.scrollTarget = Math.round(this.scroll);
   }

   /** Called by ExpeditionClient when a new snapshot arrives. */
   public void refresh() {
      if (this.minecraft != null) {
         this.rebuild(false);
      }
   }

   private void selectTab(int t) {
      t = Mth.clamp(t, 0, TABS.length - 1);
      if (t != this.tab) {
         this.tab = t;
         lastTab = t;
         this.confirm = "";
         Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.6F));
         this.rebuild(true);
      }
   }

   private void rebuild(boolean resetScroll) {
      if (this.minecraft == null) {
         return;
      }
      JsonObject st = state();
      if (st != this.builtState) {
         this.builtState = st;
         this.stateAt = System.currentTimeMillis();
      }
      this.rows.clear();
      this.contentH = 0;
      this.next = this.computeNext();
      this.sidebarText();
      if (!loaded()) {
         space(6);
         this.para(L("waiting", "Waiting for the reserve's field records…"), JournalUi.MUTED, FrontierUi.Size.BODY);
      } else {
         switch (TABS[this.tab]) {
            case "campaign" -> this.campaign();
            case "contracts" -> this.contracts();
            case "stores" -> this.stores();
            case "training" -> this.training();
            case "lodge" -> this.lodge();
            case "ledger" -> this.ledger();
            default -> this.seasons();
         }
      }
      space(10);
      this.title = fit(L("title." + TABS[this.tab], L("tab." + TABS[this.tab], TABS[this.tab])), this.bodyW - 30, FrontierUi.Size.TITLE);
      this.nextLabel = seq(L("next.label", "NEXT"), FrontierUi.Size.STRONG);
      String nt = this.next.title() + (this.next.progress().isEmpty() ? "" : "  ·  " + this.next.progress());
      this.nextSeq = fit(nt, this.bodyW - 24 - JournalUi.width(this.nextLabel) - 6, FrontierUi.Size.SMALL);
      int max = this.maxScroll();
      if (resetScroll) {
         this.scrollTarget = 0;
         this.scroll = 0.0F;
      } else {
         this.scrollTarget = Math.min(this.scrollTarget, max);
         this.scroll = Math.min(this.scroll, max);
      }
      this.builtSecond = (int)(System.currentTimeMillis() / 1000L);
   }

   private int maxScroll() {
      return Math.max(0, this.contentH - (this.viewBottom - this.viewTop));
   }

   private void sidebarText() {
      int tokens = loaded() ? number("tokens") : -1;
      this.tokenLine = tokens < 0 ? null : fit(L("side.tokens", "%s tokens", num(tokens)), this.side - 34, FrontierUi.Size.SMALL);
      int fs = GuideClient.fieldSchoolDone();
      String count = fs < 0 ? "" : (Lesson.graduated(fs) ? " ✓" : " " + Lesson.requiredDone(fs) + "/7");
      this.schoolLabel = fit(L("side.school", "Field School") + count, this.side - 36, FrontierUi.Size.SMALL);
   }

   // ============================================================================================ CAMPAIGN

   private void nextCard() {
      Next n = this.next;
      FormattedCharSequence eyebrow = seq(L("next.eyebrow", "NEXT OBJECTIVE"), FrontierUi.Size.SMALL);
      int textW = this.bodyW - 30;
      FormattedCharSequence t = fit(n.title(), textW, FrontierUi.Size.STRONG);
      List<FormattedCharSequence> detail = JournalUi.wrap(n.detail(), textW, FrontierUi.Size.SMALL);
      if (detail.size() > 3) {
         detail = detail.subList(0, 3);
      }
      List<FormattedCharSequence> det = detail;
      FormattedCharSequence prog = n.progress().isEmpty() ? null : seq(n.progress(), FrontierUi.Size.STRONG);
      boolean ready = n.kind() == N_CONTRACT_READY || n.kind() == N_REPORT_READY;
      int accent = ready ? JournalUi.GREEN : JournalUi.GOLD;
      // the action that completes it
      String bl = null;
      Runnable act = null;
      String bi = null;
      switch (n.kind()) {
         case N_STARTER -> {
            bl = L("btn.starter", "Collect starting equipment");
            act = this.send(8, "");
            bi = "equipment";
         }
         case N_REPORT_READY -> {
            bl = L("btn.file_report", "File report · %s tokens", Campaign.MISSIONS.get(number("stage")).tokens());
            act = this.send(1, value("mission_id"));
            bi = "campaign";
         }
         case N_CONTRACT_READY -> {
            bl = L("btn.collect", "Collect payment");
            act = this.send(3, value("contract_serial"));
            bi = "token";
         }
         case N_CONTRACT, N_BOARD -> {
            if (this.tab != 1) {
               bl = L("btn.open_contracts", "Open contracts");
               act = () -> this.selectTab(1);
               bi = "contracts";
            }
         }
         default -> {
         }
      }
      int barY = 30 + det.size() * 9 + 4;
      int h = barY + (prog != null ? 12 : 2) + (bl != null ? 24 : 4);
      Row r = add(new Row(h, (g, x, y, w, mx, my, hv) -> {
         card(g, x, y, w, h - 2, accent, false);
         JournalIcons.draw(g, n.icon(), x + 3, y + 6, 16, false, 1.0F);
         JournalUi.draw(g, eyebrow, x + 25, y + 6, ready ? JournalUi.GREEN : JournalUi.GOLD_DARK);
         JournalUi.draw(g, t, x + 25, y + 16, JournalUi.INK);
         int yy = y + 29;
         for (FormattedCharSequence l : det) {
            JournalUi.draw(g, l, x + 25, yy, JournalUi.MUTED);
            yy += 9;
         }
         if (prog != null) {
            JournalUi.bar(g, x + 25, y + barY + 3, w - 30 - JournalUi.width(prog) - 8, 5.0F, n.frac(), JournalUi.TRACK, accent);
            JournalUi.drawRight(g, prog, x + w - 4, y + barY + 1, JournalUi.INK_BROWN);
         }
      }));
      if (bl != null) {
         int bw = Math.min(this.bodyW - 30, Math.max(120, JournalUi.width(seq(bl, FrontierUi.Size.SMALL)) + 34));
         r.buttons.add(new Btn(25, h - 25, bw, 19, bl, bi, true, true, act, null));
      }
      space(6);
   }

   private void campaign() {
      this.nextCard();
      int stage = Math.min(number("stage"), Campaign.MISSIONS.size());
      // chapter / report pips
      FormattedCharSequence chap = fit(stage < Campaign.MISSIONS.size()
         ? L("campaign.progress", "Chapter %s of 3  ·  report %s of %s", Campaign.chapter(stage), stage + 1, Campaign.MISSIONS.size())
         : L("campaign.progress_done", "All %s reports filed", Campaign.MISSIONS.size()), this.bodyW - 100, FrontierUi.Size.SMALL);
      add(new Row(18, (g, x, y, w, mx, my, hv) -> {
         JournalUi.draw(g, chap, x, y + 5, JournalUi.GOLD_DARK);
         float px = x + w - Campaign.MISSIONS.size() * 9 - 6;
         for (int i = 0; i < Campaign.MISSIONS.size(); i++) {
            float cx = px + i * 9 + (i >= 3 ? 3 : 0) + (i >= 6 ? 3 : 0);
            pips(g, cx, y + 9, 1, i < stage ? 1 : 0, i == stage ? 0 : -1, JournalUi.GREEN, 0xFFCFC7AF);
         }
      }));
      if (stage < Campaign.MISSIONS.size()) {
         Campaign.Mission m = Campaign.MISSIONS.get(stage);
         section("campaign", L("campaign.report", "Field report"));
         FormattedCharSequence mt = fit(m.title(), this.bodyW - 80, FrontierUi.Size.STRONG);
         FormattedCharSequence reward = seq(L("campaign.reward", "%s tokens · +60 XP", m.tokens()), FrontierUi.Size.SMALL);
         add(new Row(14, (g, x, y, w, mx, my, hv) -> {
            JournalUi.draw(g, mt, x, y + 2, JournalUi.INK_BROWN);
            JournalIcons.draw(g, "token", x + w - JournalUi.width(reward) - 14, y, 12, false, 1.0F);
            JournalUi.drawRight(g, reward, x + w, y + 3, JournalUi.GOLD_DARK);
         }));
         this.para(m.story(), JournalUi.INK, FrontierUi.Size.BODY);
         if (stage > 0) {
            section("quill", L("campaign.last_entry", "Mara's last entry"));
            this.paraAt("“" + Campaign.DEBRIEFS.get(stage - 1) + "”", JournalUi.MUTED, FrontierUi.Size.SMALL, 4);
         }
      } else {
         section("ledger", L("campaign.recorded", "The expedition is recorded"));
         this.para(Campaign.DEBRIEFS.getLast(), JournalUi.INK, FrontierUi.Size.BODY);
      }
      section("party", L("campaign.party", "Nearby hunting party"));
      List<String> comps = strings("companions");
      for (String s : comps) {
         FormattedCharSequence cs = fit(s, this.bodyW - 22, FrontierUi.Size.BODY);
         add(new Row(14, (g, x, y, w, mx, my, hv) -> {
            FrontierUi.circle(g, x + 5, y + 6, 2.5F, JournalUi.GREEN);
            JournalUi.draw(g, cs, x + 14, y + 2, JournalUi.INK);
         }));
      }
      this.para(L("campaign.party_note", "Party members within 96 m share hunt events toward their own current objective. Biome visits, tranquilizer surveys and trophy placement stay personal. Invite with /expedition invite <name> (Lodge page)."),
         JournalUi.MUTED, FrontierUi.Size.SMALL);
   }

   // ============================================================================================ CONTRACTS

   private void contracts() {
      int contract = number("contract");
      String status = value("contract_status");
      boolean has = contract >= 0 && contract < Campaign.CONTRACTS.size();
      if (has) {
         Campaign.Mission c = Campaign.CONTRACTS.get(contract);
         int n = Math.min(number("contract_count"), c.amount());
         boolean ready = status.equals("ready"), expired = status.equals("expired");
         int accent = ready ? JournalUi.GREEN : (expired ? JournalUi.RED : JournalUi.GOLD);
         FormattedCharSequence eyebrow = seq(ready ? L("contract.ready", "PAYMENT READY") : (expired ? L("contract.expired", "TIME EXPIRED")
            : L("contract.active", "IN THE FIELD")), FrontierUi.Size.SMALL);
         FormattedCharSequence t = fit(c.title(), this.bodyW - 90, FrontierUi.Size.STRONG);
         List<FormattedCharSequence> story = JournalUi.wrap(c.story() + " " + howItCounts(c), this.bodyW - 30, FrontierUi.Size.SMALL);
         FormattedCharSequence prog = seq(n + " / " + c.amount(), FrontierUi.Size.STRONG);
         FormattedCharSequence reward = seq(L("contract.reward", "%s tokens", c.tokens()), FrontierUi.Size.SMALL);
         FormattedCharSequence note = fit(ready ? L("contract.ready_note", "Earned before the deadline — collect whenever you like.")
            : (expired ? L("contract.expired_note", "Abandon it or take another contract; no campaign progress is lost.") : ""), this.bodyW - 30, FrontierUi.Size.SMALL);
         int h = 30 + story.size() * 9 + 34 + 24;
         Row r = add(new Row(h, (g, x, y, w, mx, my, hv) -> {
            card(g, x, y, w, h - 2, accent, false);
            JournalIcons.draw(g, "contracts", x + 3, y + 6, 16, false, 1.0F);
            JournalUi.draw(g, eyebrow, x + 25, y + 6, accent);
            JournalUi.draw(g, t, x + 25, y + 16, JournalUi.INK);
            JournalIcons.draw(g, "token", x + w - JournalUi.width(reward) - 18, y + 4, 12, false, 1.0F);
            JournalUi.drawRight(g, reward, x + w - 4, y + 7, JournalUi.GOLD_DARK);
            int yy = y + 29;
            for (FormattedCharSequence l : story) {
               JournalUi.draw(g, l, x + 25, yy, JournalUi.MUTED);
               yy += 9;
            }
            int bw = w - 30 - JournalUi.width(prog) - 8;
            JournalUi.bar(g, x + 25, yy + 4, bw, 5.0F, (float)n / c.amount(), JournalUi.TRACK, accent);
            JournalUi.drawRight(g, prog, x + w - 4, yy + 2, JournalUi.INK_BROWN);
            yy += 13;
            if (!ready && !expired) {
               long left = this.secondsLeft("seconds");
               float tf = Mth.clamp(left / 1200.0F, 0.0F, 1.0F);
               JournalIcons.draw(g, "clock", x + 25, yy - 2, 12, false, 1.0F);
               JournalUi.bar(g, x + 41, yy + 3, bw - 16, 3.0F, tf, JournalUi.TRACK, left < 180 ? JournalUi.RED : 0xFF6F98AE);
               JournalUi.drawRight(g, seq(L("contract.left", "%s left", clock(left)), FrontierUi.Size.SMALL), x + w - 4, yy + 1, left < 180 ? JournalUi.RED : JournalUi.MUTED);
            } else {
               JournalUi.draw(g, note, x + 25, yy + 1, JournalUi.MUTED);
            }
         }));
         int half = (this.bodyW - 36) / 2;
         r.buttons.add(new Btn(25, h - 25, half, 19, L("btn.collect", "Collect payment"), "token", ready, true, this.send(3, value("contract_serial")),
            L("why.not_ready", "Complete the objective before the timer runs out.")));
         String ab = armed("abandon") ? L("btn.confirm", "Click again to confirm") : (ready ? L("btn.forfeit", "Forfeit payment") : L("btn.abandon", "Abandon contract"));
         r.buttons.add(new Btn(31 + half, h - 25, half, 19, ab, null, true, false, this.confirmed("abandon", this.send(9, "")), null));
         space(6);
      }
      section("contracts", L("contract.board", "Contract board"));
      this.para(L("contract.rules2", "One contract at a time. Each runs for 20 minutes (30 once your camp reaches rank 3); payment earned in time stays available after the clock runs out. Contract hunts are shared with party members nearby."),
         JournalUi.MUTED, FrontierUi.Size.SMALL);
      boolean canAccept = !has || status.equals("expired");
      // [hunts] the standing contracts plus this month's species contracts (they rotate with the reserve calendar)
      Minecraft hmc = Minecraft.getInstance();
      HuntingCalendar.Date boardDate = hmc.level == null ? null : HuntingCalendar.date(SeasonClock.calendarTicks(hmc.level), SeasonClock.daysPerMonth(hmc.level));
      int boardMonth = boardDate == null ? 8 : boardDate.month();
      int boardHalf = boardDate == null ? 0 : com.formaworks.frontierhunts.hunts.HuntContracts.half(boardDate.day(), boardDate.daysPerMonth());
      this.para(com.formaworks.frontierhunts.hunts.client.HuntContractText.boardNote(boardMonth, boardHalf), JournalUi.GOLD_DARK, FrontierUi.Size.SMALL);
      for (int i : com.formaworks.frontierhunts.hunts.HuntContracts.board(boardMonth, boardHalf)) {
         Campaign.Mission c = Campaign.CONTRACTS.get(i);
         boolean current = has && i == contract;
         String icon = missionIcon(c);
         FormattedCharSequence t = fit(c.title(), this.bodyW - 100, FrontierUi.Size.STRONG);
         FormattedCharSequence reward = seq(L("contract.reward", "%s tokens", c.tokens()), FrontierUi.Size.SMALL);
         List<FormattedCharSequence> story = JournalUi.wrap(c.story() + " " + howItCounts(c), this.bodyW - 30, FrontierUi.Size.SMALL);
         int h = 20 + story.size() * 9 + 26;
         Row r = add(new Row(h, (g, x, y, w, mx, my, hv) -> {
            card(g, x, y + 2, w, h - 4, current ? JournalUi.GOLD : 0xFFB9A984, false);
            JournalIcons.draw(g, icon, x + 3, y + 6, 16, false, 1.0F);
            JournalUi.draw(g, t, x + 25, y + 8, JournalUi.INK);
            JournalIcons.draw(g, "token", x + w - JournalUi.width(reward) - 18, y + 5, 12, false, 1.0F);
            JournalUi.drawRight(g, reward, x + w - 4, y + 8, JournalUi.GOLD_DARK);
            int yy = y + 20;
            for (FormattedCharSequence l : story) {
               JournalUi.draw(g, l, x + 25, yy, JournalUi.MUTED);
               yy += 9;
            }
         }));
         String label = current ? L("btn.current", "Current contract") : L("btn.take", "Take contract");
         r.buttons.add(new Btn(25, h - 25, Math.min(140, this.bodyW - 30), 19, label, null, canAccept && !current, false, this.send(2, Integer.toString(i)),
            current ? L("why.current", "This is your running contract.") : L("why.busy", "Finish, collect or abandon your current contract first.")));
         space(2);
      }
   }

   // ============================================================================================ STORES

   private void stores() {
      boolean station = flag("station");
      int tokens = number("tokens");
      FormattedCharSequence st = fit(station ? L("stores.station", "At an expedition station — purchases use your tokens.")
         : L("stores.no_station", "Stand within 6 blocks of an expedition station to buy."), this.bodyW - 26, FrontierUi.Size.SMALL);
      add(new Row(22, (g, x, y, w, mx, my, hv) -> {
         FrontierUi.rect(g, x - 3, y + 2, w + 6, 18, 3.0F, station ? 0x223E6E37 : 0x22A5281F);
         JournalIcons.draw(g, station ? "lodge" : "lock", x, y + 3, 16, false, 1.0F);
         JournalUi.draw(g, st, x + 22, y + 8, station ? JournalUi.GREEN : JournalUi.RED);
      }));
      space(4);
      Minecraft mc = Minecraft.getInstance();
      for (Map.Entry<String, Integer> e : ExpeditionService.SHOP.entrySet()) {
         String id = e.getKey();
         int price = e.getValue();
         ItemStack stack;
         try {
            Item it = ExpeditionContent.item(id);
            int count = !id.contains("round") && !id.contains("shell") && !id.contains("dart") && !id.contains("arrow") ? 1 : 12;
            stack = new ItemStack(it, count);
         } catch (RuntimeException ex) {
            continue;
         }
         boolean space = mc.player == null || mc.player.getInventory().getFreeSlot() >= 0 || mc.player.getInventory().getSlotWithRemainingSpace(stack) >= 0;
         boolean active = station && tokens >= price && space;
         String why = !station ? L("why.station", "Stand within 6 blocks of an expedition station.")
            : (tokens < price ? L("why.tokens", "You need %s more tokens.", price - tokens) : L("why.full", "Your inventory is full."));
         // [1.1.7] counter exclusives: a heading before the range targets, and a line on what each one does
         String blurbKey = "store.frontierhunts.blurb." + id;
         String blurb = I18n.exists(blurbKey) ? I18n.get(blurbKey) : "";
         FormattedCharSequence name = fit(stack.getHoverName().getString() + (stack.getCount() > 1 ? "  ×" + stack.getCount() : ""), this.bodyW - 130, FrontierUi.Size.BODY);
         FormattedCharSequence sub = fit(blurb, this.bodyW - 130, FrontierUi.Size.SMALL);
         int rowH = blurb.isEmpty() ? 22 : 30;
         FormattedCharSequence pr = seq(num(price), FrontierUi.Size.STRONG);
         boolean band = this.rows.size() % 2 == 0;
         Row r = add(new Row(rowH, (g, x, y, w, mx, my, hv) -> {
            if (band) {
               g.fill(x - 3, y, x + w + 3, y + rowH, 0x0E3B2E20);
            }
            g.renderItem(stack, x, y + (rowH - 16) / 2);
            JournalUi.draw(g, name, x + 22, y + (rowH == 22 ? 8 : 5), JournalUi.INK);
            if (rowH > 22) {
               JournalUi.draw(g, sub, x + 22, y + 17, JournalUi.MUTED);
            }
            int px = x + w - 64;
            JournalUi.drawRight(g, pr, px - 4, y + rowH / 2 - 4, tokens >= price ? JournalUi.INK_BROWN : JournalUi.RED);
            JournalIcons.draw(g, "token", px - 4 - JournalUi.width(pr) - 14, y + rowH / 2 - 6, 12, false, 1.0F);
         }));
         r.buttons.add(new Btn(this.bodyW - 58, (rowH - 18) / 2, 58, 18, L("btn.buy", "Buy"), null, active, false, this.send(5, id), why));
      }
      space(6);
      this.para(L("stores.counter3", "The outfitter's counter only stocks what nobody can make: field gear from town and the tracking hound. Ammunition, batteries, scent cover, first aid and range targets are made at your benches; licences, tags and the game report are at the licence counter."),
         JournalUi.MUTED, FrontierUi.Size.SMALL);
      space(4);
      section("equipment", L("stores.gear", "Field gear & benches"));
      this.para(L("stores.gear_text", "Hold a field pack or quiver and press Shift + your Pack key for Field Gear: both worn slots work together and a worn quiver feeds arrows."),
         JournalUi.INK, FrontierUi.Size.SMALL);
      this.para(L("stores.bench_text", "Three benches make everything. Frontier Workbench: gear, clothing, camp, fishing, vehicles and building. Gunsmith's Bench: guns, bows, blades and attachments (its Fit tab fits parts to the weapon in your hand). Reloading Bench: cartridges, shells, arrows, tips, darts and flares."),
         JournalUi.MUTED, FrontierUi.Size.SMALL);
   }

   // ============================================================================================ TRAINING

   private void training() {
      int[] ranks = trainingRanks();
      int used = ranks[0] + ranks[1] + ranks[2] + ranks[3];
      int xp = number("xp");
      int points = Math.max(0, xp / 100 - used);
      int active = number("training");
      FormattedCharSequence xl = seq(L("training.xp", "Expedition XP %s", num(xp)), FrontierUi.Size.STRONG);
      FormattedCharSequence pl = seq(L("training.points", "%s point(s) to spend", points), FrontierUi.Size.SMALL);
      FormattedCharSequence nl = fit(L("training.next_point", "%s XP to the next point", 100 - xp % 100), this.bodyW / 2, FrontierUi.Size.SMALL);
      add(new Row(35, (g, x, y, w, mx, my, hv) -> {
         JournalIcons.draw(g, "star", x, y + 3, 16, false, 1.0F);
         JournalUi.draw(g, xl, x + 22, y + 4, JournalUi.INK);
         JournalUi.drawRight(g, pl, x + w, y + 5, points > 0 ? JournalUi.GREEN : JournalUi.MUTED);
         JournalUi.bar(g, x + 22, y + 17, w - 22, 4.0F, (xp % 100) / 100.0F, JournalUi.TRACK, JournalUi.GOLD);
         JournalUi.draw(g, nl, x + 22, y + 23, JournalUi.FAINT);
      }));
      this.para(L("training.intro", "Earn a point per 100 expedition XP (filed reports +60, contracts +40, recoveries +15). Spend it by finishing a field challenge within 15 minutes; each challenge ranks up to 3. One challenge at a time."),
         JournalUi.MUTED, FrontierUi.Size.SMALL);
      for (int i = 0; i < 4; i++) {
         int idx = i;
         String key = TRAINING_NAMES[i];
         boolean running = active == i;
         FormattedCharSequence name = fit(L("training." + key, key), this.bodyW - 90, FrontierUi.Size.STRONG);
         List<FormattedCharSequence> desc = JournalUi.wrap(L("training." + key + ".task", "") + " " + L("training." + key + ".perk", ""), this.bodyW - 30, FrontierUi.Size.SMALL);
         int prog = running ? number("training_progress") : 0;
         int h = 22 + desc.size() * 9 + (running ? 14 : 0) + 26;
         Row r = add(new Row(h, (g, x, y, w, mx, my, hv) -> {
            card(g, x, y + 2, w, h - 4, running ? JournalUi.GOLD : 0xFFB9A984, false);
            JournalIcons.draw(g, TRAINING_ICONS[idx], x + 3, y + 6, 16, false, 1.0F);
            JournalUi.draw(g, name, x + 25, y + 8, JournalUi.INK);
            pips(g, x + w - 31, y + 12, 3, ranks[idx], -1, JournalUi.GOLD, 0xFFCFC7AF);
            int yy = y + 21;
            for (FormattedCharSequence l : desc) {
               JournalUi.draw(g, l, x + 25, yy, JournalUi.MUTED);
               yy += 9;
            }
            if (running) {
               long left = this.secondsLeft("training_seconds");
               FormattedCharSequence ps = seq(prog + " / " + TRAINING_TARGETS[idx] + "  ·  " + clock(left), FrontierUi.Size.SMALL);
               JournalUi.bar(g, x + 25, yy + 4, w - 30 - JournalUi.width(ps) - 8, 4.0F, (float)prog / TRAINING_TARGETS[idx], JournalUi.TRACK, JournalUi.GOLD);
               JournalUi.drawRight(g, ps, x + w - 4, yy + 2, JournalUi.INK_BROWN);
            }
         }));
         boolean can = points > 0 && ranks[i] < 3 && active < 0;
         String why = ranks[i] >= 3 ? L("why.max_rank", "Already at rank 3.") : (active >= 0 ? L("why.challenge_running", "A challenge is already running.")
            : L("why.no_points", "No points to spend — earn 100 expedition XP."));
         String label = running ? L("btn.underway", "Challenge underway") : (ranks[i] >= 3 ? L("btn.mastered", "Mastered") : L("btn.start", "Start challenge"));
         r.buttons.add(new Btn(25, h - 25, Math.min(150, this.bodyW - 30), 19, label, null, can, false, this.send(4, Integer.toString(i)), why));
         space(2);
      }
   }

   // ============================================================================================ LODGE

   private void lodge() {
      int camp = Mth.clamp(number("camp"), 0, 4);
      int tokens = number("tokens");
      boolean station = flag("station");
      section("lodge", L("lodge.camp", "The shared lodge"));
      FormattedCharSequence rl = seq(L("lodge.rank", "Camp rank %s / 4", camp), FrontierUi.Size.STRONG);
      List<ItemStack> stations = new ArrayList<>();
      for (String s : CAMP_STATIONS) {
         try {
            stations.add(com.formaworks.frontierhunts.benches.OldBenches.modernise(new ItemStack((ItemLike)ExpeditionContent.STATIONS.get(s).get()))); // [benches]
         } catch (RuntimeException e) {
            stations.add(ItemStack.EMPTY);
         }
      }
      add(new Row(42, (g, x, y, w, mx, my, hv) -> {
         JournalUi.draw(g, rl, x, y + 3, JournalUi.INK);
         for (int i = 0; i < 4; i++) {
            int sx = x + i * 26;
            FrontierUi.rect(g, sx, y + 15, 22, 22, 3.0F, i < camp ? 0x333E6E37 : 0x14000000);
            if (!stations.get(i).isEmpty()) {
               g.renderItem(stations.get(i), sx + 3, y + 18);
            }
            if (i < camp) {
               FrontierUi.circle(g, sx + 19, y + 17, 4.0F, JournalUi.GREEN);
               JournalUi.tick(g, sx + 19, y + 17, JournalUi.PAPER);
            }
         }
      }));
      String nextName = camp < 4 && !stations.get(camp).isEmpty() ? stations.get(camp).getHoverName().getString() : "";
      this.para(camp >= 4 ? L("lodge.all", "All camp supplies issued.")
         : L("lodge.next2", "Next upgrade: %s (%s tokens).", nextName, 25 + camp * 20),
         JournalUi.MUTED, FrontierUi.Size.SMALL);
      // [1.1.6] what each rank actually changes, beyond the station it issues
      String[] perks = {
         L("lodge.perk1", "Rank 1 - storage, and your camp post becomes a licence counter: buy licences, tags, stamps and ranger services at camp."),
         L("lodge.perk2", "Rank 2 - Reloading Bench, and venison you deliver on the ranger board pays 25% more."),
         L("lodge.perk3", "Rank 3 - Gunsmith's Bench, and expedition contracts give you 30 minutes instead of 20."),
         L("lodge.perk4", "Rank 4 - Frontier Workbench, and one more deer tag every season (the landowner's tag).")
      };
      for (int i = 0; i < perks.length; i++) {
         this.para((i < camp ? "\u2713 " : "\u2022 ") + perks[i], i < camp ? JournalUi.INK : JournalUi.MUTED, FrontierUi.Size.SMALL);
      }
      int cost = 25 + camp * 20;
      buttonRow(List.of(btn(0, Math.min(180, this.bodyW), camp >= 4 ? L("btn.camp_done", "All supplies issued") : L("btn.upgrade", "Upgrade camp · %s tokens", cost),
         "lodge", camp < 4 && station && tokens >= cost, true, this.send(6, ""),
         camp >= 4 ? "" : (!station ? L("why.station", "Stand within 6 blocks of an expedition station.") : L("why.tokens", "You need %s more tokens.", cost - tokens)))));
      section("party", L("lodge.party", "Hunting party"));
      boolean party = flag("party"), invite = flag("invite");
      this.para(party ? L("lodge.in_party", "You are in a hunting party. Members within 96 m share hunt progress (up to 8 hunters).")
         : L("lodge.no_party", "Invite a hunter with /expedition invite <name>; they accept here. Members within 96 m share hunt progress (up to 8)."),
         JournalUi.INK, FrontierUi.Size.SMALL);
      int half = (this.bodyW - 6) / 2;
      buttonRow(List.of(
         btn(0, half, L("btn.accept", "Accept invitation"), "party", invite, invite, this.send(12, ""), L("why.no_invite", "Nobody has invited you yet.")),
         btn(half + 6, half, armed("leave") ? L("btn.confirm", "Click again to confirm") : L("btn.leave", "Leave party"), null, party, false,
            this.confirmed("leave", this.send(13, "")), L("why.no_party", "You are not in a party."))));
      section("marker", L("lodge.marker", "Shared marker"));
      String marker = value("marker");
      this.para(marker.isEmpty() ? L("lodge.no_marker", "No marker shared yet. Sharing marks where you stand for everyone in your party.") : marker,
         marker.isEmpty() ? JournalUi.MUTED : JournalUi.INK, FrontierUi.Size.SMALL);
      buttonRow(List.of(btn(0, Math.min(180, this.bodyW), L("btn.marker", "Share a marker here"), "marker", true, false, this.send(14, ""), null)));
      section("records", L("lodge.records", "Lodge records"));
      List<String> leaders = strings("leaders");
      if (leaders.isEmpty()) {
         this.para(L("lodge.no_records", "No hunters on the board yet."), JournalUi.MUTED, FrontierUi.Size.SMALL);
      } else {
         this.leaderTable(leaders);
      }
   }

   private void leaderTable(List<String> leaders) {
      boolean wide = this.bodyW >= 330;
      int cLong = 48, cCon = 44, cBest = wide ? 50 : 0, cTro = wide ? 46 : 0;
      int nameW = this.bodyW - cLong - cCon - cBest - cTro;
      FormattedCharSequence h1 = seq(L("lodge.col.hunter", "Hunter"), FrontierUi.Size.SMALL), h2 = seq(L("lodge.col.longest", "Longest"), FrontierUi.Size.SMALL),
         h3 = seq(L("lodge.col.contracts", "Contracts"), FrontierUi.Size.SMALL), h4 = seq(L("lodge.col.best", "Best run"), FrontierUi.Size.SMALL),
         h5 = seq(L("lodge.col.trophy", "Trophy"), FrontierUi.Size.SMALL);
      add(new Row(14, (g, x, y, w, mx, my, hv) -> {
         int cx = x + nameW;
         JournalUi.draw(g, h1, x + 14, y + 3, JournalUi.GOLD_DARK);
         JournalUi.drawRight(g, h2, cx += cLong, y + 3, JournalUi.GOLD_DARK);
         JournalUi.drawRight(g, h3, cx += cCon, y + 3, JournalUi.GOLD_DARK);
         if (cBest > 0) {
            JournalUi.drawRight(g, h4, cx += cBest, y + 3, JournalUi.GOLD_DARK);
            JournalUi.drawRight(g, h5, cx + cTro, y + 3, JournalUi.GOLD_DARK);
         }
         g.fill(x, y + 12, x + w, y + 13, JournalUi.RULE);
      }));
      int i = 0;
      for (String s : leaders) {
         String[] p = s.split(" · ");
         int place = ++i;
         if (p.length < 5) {
            this.para(place + ". " + s, JournalUi.INK, FrontierUi.Size.SMALL);
            continue;
         }
         FormattedCharSequence nm = fit(p[0].isEmpty() ? "?" : p[0], nameW - 16, FrontierUi.Size.BODY);
         FormattedCharSequence v1 = seq(p[1], FrontierUi.Size.SMALL), v2 = seq(p[2].replace(" contracts", ""), FrontierUi.Size.SMALL),
            v3 = seq(p[3].replace(" best", ""), FrontierUi.Size.SMALL), v4 = seq(p[4].replace("trophy ", ""), FrontierUi.Size.SMALL);
         FormattedCharSequence pl = seq(Integer.toString(place), FrontierUi.Size.STRONG);
         boolean band = place % 2 == 1;
         add(new Row(15, (g, x, y, w, mx, my, hv) -> {
            if (band) {
               g.fill(x - 3, y, x + w + 3, y + 15, 0x0E3B2E20);
            }
            JournalUi.draw(g, pl, x + 2, y + 4, place == 1 ? JournalUi.GOLD_DARK : JournalUi.MUTED);
            JournalUi.draw(g, nm, x + 14, y + 4, JournalUi.INK);
            int cx = x + nameW;
            JournalUi.drawRight(g, v1, cx += cLong, y + 5, JournalUi.INK_BROWN);
            JournalUi.drawRight(g, v2, cx += cCon, y + 5, JournalUi.INK_BROWN);
            if (cBest > 0) {
               JournalUi.drawRight(g, v3, cx += cBest, y + 5, JournalUi.MUTED);
               JournalUi.drawRight(g, v4, cx + cTro, y + 5, JournalUi.MUTED);
            }
         }));
      }
   }

   // ============================================================================================ LEDGER

   private void ledger() {
      int stage = Math.min(number("stage"), Campaign.MISSIONS.size());
      int[] ranks = trainingRanks();
      List<String[]> stats = new ArrayList<>();
      stats.add(new String[]{L("ledger.reports", "Reports filed"), stage + " / " + Campaign.MISSIONS.size()});
      stats.add(new String[]{L("ledger.habitats", "Habitats observed"), num(number("discoveries"))});
      stats.add(new String[]{L("ledger.xp", "Expedition XP"), num(number("xp"))});
      stats.add(new String[]{L("ledger.training", "Training ranks"), (ranks[0] + ranks[1] + ranks[2] + ranks[3]) + " / 12"});
      stats.add(new String[]{L("ledger.camp", "Camp rank"), Mth.clamp(number("camp"), 0, 4) + " / 4"});
      stats.add(new String[]{L("ledger.tokens", "Tokens"), num(number("tokens"))});
      boolean two = this.bodyW >= 280;
      int colW = two ? (this.bodyW - 16) / 2 : this.bodyW;
      for (int i = 0; i < stats.size(); i += two ? 2 : 1) {
         FormattedCharSequence l1 = fit(stats.get(i)[0], colW - 50, FrontierUi.Size.SMALL), v1 = seq(stats.get(i)[1], FrontierUi.Size.STRONG);
         FormattedCharSequence l2 = two && i + 1 < stats.size() ? fit(stats.get(i + 1)[0], colW - 50, FrontierUi.Size.SMALL) : null;
         FormattedCharSequence v2 = l2 == null ? null : seq(stats.get(i + 1)[1], FrontierUi.Size.STRONG);
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
      section("ledger", L("ledger.title", "Mara's expedition ledger"));
      if (stage == 0) {
         this.para(L("ledger.empty", "Your first report appears here once it is filed."), JournalUi.MUTED, FrontierUi.Size.SMALL);
      }
      for (int i = stage - 1; i >= 0; i--) {
         Campaign.Mission m = Campaign.MISSIONS.get(i);
         FormattedCharSequence nb = seq(String.format(Locale.ROOT, "%02d", i + 1), FrontierUi.Size.STRONG);
         FormattedCharSequence t = fit(m.title(), this.bodyW - 26, FrontierUi.Size.STRONG);
         List<FormattedCharSequence> d = JournalUi.wrap(Campaign.DEBRIEFS.get(i), this.bodyW - 26, FrontierUi.Size.SMALL);
         boolean last = i == 0;
         add(new Row(16 + d.size() * 9 + 6, (g, x, y, w, mx, my, hv) -> {
            FrontierUi.circle(g, x + 8, y + 7, 7.0F, JournalUi.GOLD);
            JournalUi.draw(g, nb, x + 8 - JournalUi.width(nb) / 2, y + 3, JournalUi.PAPER);
            if (!last) {
               g.fill(x + 7, y + 15, x + 9, y + 16 + d.size() * 9 + 6, 0x40B99859);
            }
            JournalUi.draw(g, t, x + 22, y + 3, JournalUi.INK);
            int yy = y + 15;
            for (FormattedCharSequence l : d) {
               JournalUi.draw(g, l, x + 22, yy, JournalUi.MUTED);
               yy += 9;
            }
         }));
      }
   }

   // ============================================================================================ SEASONS

   private void seasons() {
      Minecraft mc = Minecraft.getInstance();
      int month = 0;
      String date = "";
      float seasonFrac = 0.0F;
      String season = "";
      int daysLeft = 0;
      if (mc.level != null) {
         long cal = SeasonClock.calendarTicks(mc.level);
         int dpm = SeasonClock.daysPerMonth(mc.level);
         HuntingCalendar.Date d = HuntingCalendar.date(cal, dpm);
         month = d.month();
         date = d.title();
         double pos = SeasonClock.yearPosition(cal, dpm);
         SeasonClock.Season s = SeasonClock.seasonAt(pos);
         season = L("season." + s.name().toLowerCase(Locale.ROOT), s.title());
         seasonFrac = SeasonClock.progressAt(pos);
         daysLeft = dpm - d.day() + 1;
      }
      String seasonIcon = switch (season.isEmpty() ? 0 : Math.floorMod(month - 2, 12) / 3) {
         case 3 -> "snowflake";
         case 1 -> "world";
         default -> "leaf";
      };
      FormattedCharSequence ds = fit(date, this.bodyW - 30, FrontierUi.Size.STRONG);
      FormattedCharSequence ss = fit(L("seasons.season", "%s · %s%% through the season · %s day(s) left this month", season, Math.round(seasonFrac * 100.0F), daysLeft),
         this.bodyW - 30, FrontierUi.Size.SMALL);
      float sf = seasonFrac;
      add(new Row(38, (g, x, y, w, mx, my, hv) -> {
         card(g, x, y, w, 36, JournalUi.GOLD, false);
         JournalIcons.draw(g, seasonIcon, x + 3, y + 6, 16, false, 1.0F);
         JournalUi.draw(g, ds, x + 25, y + 5, JournalUi.INK);
         JournalUi.draw(g, ss, x + 25, y + 16, JournalUi.MUTED);
         JournalUi.bar(g, x + 25, y + 27, w - 30, 3.0F, sf, JournalUi.TRACK, JournalUi.GOLD);
      }));
      space(4);
      // 12-month open-season chart
      String[] ml = L("seasons.month_letters", "J,F,M,A,M,J,J,A,S,O,N,D").split(",");
      int nameW = Math.min(90, this.bodyW / 3);
      int cellW = Math.max(9, (this.bodyW - nameW - 4) / 12);
      int cm = month;
      FormattedCharSequence[] letters = new FormattedCharSequence[12];
      for (int i = 0; i < 12; i++) {
         letters[i] = seq(i < ml.length ? ml[i] : "?", FrontierUi.Size.SMALL);
      }
      section("seasons", L("seasons.chart", "Open seasons"));
      add(new Row(12, (g, x, y, w, mx, my, hv) -> {
         for (int i = 0; i < 12; i++) {
            int cx = x + nameW + i * cellW;
            JournalUi.draw(g, letters[i], cx + (cellW - JournalUi.width(letters[i])) / 2, y + 2, i == cm ? JournalUi.INK : JournalUi.FAINT);
         }
      }));
      // [licence] the chart follows the hunting regulations (every regulated species and its open months)
      for (com.formaworks.frontierhunts.licence.Regulations.Rule rule : com.formaworks.frontierhunts.licence.Regulations.all()) {
         String id = rule.species();
         FormattedCharSequence nm = fit(rule.title(), nameW - 22, FrontierUi.Size.BODY);
         boolean[] open = new boolean[12];
         for (int i = 0; i < 12; i++) {
            open[i] = rule.open(i);
         }
         add(new Row(18, (g, x, y, w, mx, my, hv) -> {
            JournalIcons.draw(g, "sp_" + id, x, y + 1, 16, false, 1.0F);
            JournalUi.draw(g, nm, x + 20, y + 5, JournalUi.INK);
            for (int i = 0; i < 12; i++) {
               int cx = x + nameW + i * cellW;
               FrontierUi.rect(g, cx + 1, y + 4, cellW - 2, 10, 2.0F, open[i] ? (i == cm ? JournalUi.GREEN : 0xFF8FAF78) : 0x18000000);
               if (i == cm) {
                  g.fill(cx, y + 2, cx + cellW, y + 3, JournalUi.INK);
                  g.fill(cx, y + 15, cx + cellW, y + 16, JournalUi.INK);
               }
            }
         }));
      }
      space(4);
      // [licence] licences and tags replace the old optional conservation tags: bought at a counter from the journal page
      section("contracts", L("seasons.licence", "Licences & tags"));
      com.formaworks.frontierhunts.licence.LicenceConfig.Mode lm = com.formaworks.frontierhunts.licence.LicenceConfig.mode();
      this.para(com.formaworks.frontierhunts.journal.client.JournalUi.tr("licence.frontierhunts.expedition." + lm.key()), JournalUi.INK, FrontierUi.Size.SMALL);
      Row lr = add(new Row(24, (g, x, y, w, mx, my, hv) -> {
      }));
      lr.buttons.add(new Btn(0, 3, 150, 18, L("btn.licence", "Licence & tags page"), "contracts", true, true,
         () -> com.formaworks.frontierhunts.client.JournalScreen.openPage(com.formaworks.frontierhunts.licence.LicenceJournal.PAGE), ""));
   }

   // ============================================================================================ render

   @Override
   public void tick() {
      if (state() != this.builtState) {
         this.rebuild(false);
      } else if (this.builtSecond != (int)(System.currentTimeMillis() / 1000L)) {
         // live countdowns and the header Next line
         this.rebuild(false);
      }
      if (this.confirm.length() > 0 && System.currentTimeMillis() - this.confirmAt > 4000L) {
         this.confirm = "";
         this.rebuild(false);
      }
      long now = System.currentTimeMillis();
      // the server refreshes an open guide for 60 s; afterwards ask for a snapshot every 10 s (never right after a click)
      if (now - this.openedAt > 55000L && now - this.lastPoll > 10000L && now - this.lastClick > 1500L && Minecraft.getInstance().getConnection() != null) {
         this.lastPoll = now;
         ExpeditionClient.send(ACTION_POLL, "");
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
      lastScroll = this.scroll;
      g.fill(0, 0, this.width, this.height, 0xB0101208);
      JournalUi.notebook(g, this.left, this.top, this.panelW, this.panelH, this.side);
      List<FormattedCharSequence> tip = this.sidebar(g, mx, my);
      // header: page title + the Next line
      JournalIcons.draw(g, TAB_ICONS[this.tab], this.bodyX, this.top + 10, 16, false, 1.0F);
      JournalUi.draw(g, this.title, this.bodyX + 22, this.top + 13, JournalUi.INK);
      int ny = this.top + 30;
      int nx = this.bodyX;
      int right = this.left + this.panelW - 16;
      boolean overNext = loaded() && mx >= nx - 3 && mx < right && my >= ny - 2 && my < ny + 15;
      FrontierUi.rect(g, nx - 3, ny - 2, right - nx + 3, 16, 3.0F, overNext ? 0x2AB99859 : 0x18B99859);
      JournalIcons.draw(g, this.next.icon(), nx, ny - 1, 14, overNext, 1.0F);
      JournalUi.draw(g, this.nextLabel, nx + 18, ny + 3, JournalUi.GOLD_DARK);
      JournalUi.draw(g, this.nextSeq, nx + 18 + JournalUi.width(this.nextLabel) + 6, ny + 3, JournalUi.INK);
      if (overNext && !this.next.detail().isEmpty()) {
         tip = JournalUi.wrap(this.next.detail(), 220, FrontierUi.Size.SMALL);
      }
      g.fill(this.bodyX, this.top + 49, this.left + this.panelW - 16, this.top + 50, JournalUi.RULE);
      // close
      int cx = this.left + this.panelW - 28, cy = this.top + 8;
      boolean overClose = mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18;
      JournalUi.button(g, cx, cy, 18, 18, CLOSE, overClose, false, true);
      // content
      int rightEdge = this.left + this.panelW - 12;
      g.enableScissor(this.bodyX - 6, this.viewTop, rightEdge, this.viewBottom);
      int base = this.viewTop - Math.round(this.scroll);
      boolean inView = mx >= this.bodyX - 6 && mx < rightEdge && my >= this.viewTop && my < this.viewBottom;
      for (Row r : this.rows) {
         int y = base + r.y;
         if (y + r.h < this.viewTop || y > this.viewBottom) {
            continue;
         }
         boolean hv = inView && my >= y && my < y + r.h;
         r.draw.draw(g, this.bodyX, y, this.bodyW, mx, my, hv && r.click != null);
         for (Btn b : r.buttons) {
            boolean over = inView && b.over(mx, my, this.bodyX, y);
            b.draw(g, this.bodyX, y, over);
            if (over && b.tip != null) {
               tip = b.tip;
            }
         }
         if (hv && r.tip != null && tip == null) {
            tip = r.tip;
         }
      }
      g.disableScissor();
      if (this.scroll > 0.5F) {
         g.fillGradient(this.bodyX - 6, this.viewTop, rightEdge, this.viewTop + 6, 0x40B9A984, 0x00EAE3D0);
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
      if (tip != null && !tip.isEmpty()) {
         g.renderTooltip(this.font, tip, mx, my);
      }
   }

   private static final FormattedCharSequence CLOSE = FormattedCharSequence.forward("×", net.minecraft.network.chat.Style.EMPTY);
   private static final FormattedCharSequence[] DIGITS = new FormattedCharSequence[9];

   static {
      for (int i = 0; i < 9; i++) {
         DIGITS[i] = FormattedCharSequence.forward(Integer.toString(i + 1), net.minecraft.network.chat.Style.EMPTY);
      }
   }

   private List<FormattedCharSequence> sidebar(GuiGraphics g, int mx, int my) {
      List<FormattedCharSequence> tip = null;
      int x = this.left, y = this.top;
      g.fill(x + 9, y + 11, x + 11, y + 44, JournalUi.GOLD);
      JournalUi.draw(g, this.brand1, x + 16, y + 12, JournalUi.SIDEBAR_MUTED);
      JournalUi.draw(g, this.brand2, x + 16, y + 21, JournalUi.SIDEBAR_TEXT);
      if (this.tokenLine != null) {
         JournalIcons.draw(g, "token", x + 15, y + 32, 12, false, 1.0F);
         JournalUi.draw(g, this.tokenLine, x + 30, y + 35, JournalUi.GOLD);
      }
      for (int i = 0; i < TABS.length; i++) {
         int ty = this.tabTop + i * this.tabH;
         int h = this.tabH - 3;
         boolean sel = i == this.tab;
         boolean over = mx >= x + 6 && mx < x + this.side - 8 && my >= ty && my < ty + h;
         if (sel) {
            FrontierUi.rect(g, x + 6, ty, this.side - 6, h, 2.0F, JournalUi.PAPER);
         } else if (over) {
            FrontierUi.rect(g, x + 6, ty, this.side - 14, h, 2.0F, 0xFF5C4636);
         }
         int isz = h >= 18 ? 16 : 12;
         JournalIcons.drawLifted(g, TAB_ICONS[i], x + 10, ty + (h - isz) / 2, isz, sel || over);
         JournalUi.draw(g, this.tabLabels.get(i), x + 30, ty + (h - 8) / 2, sel ? JournalUi.INK : JournalUi.SIDEBAR_TEXT);
         if (this.side >= 120) {
            JournalUi.drawRight(g, DIGITS[i], x + this.side - 12, ty + (h - 8) / 2, sel ? JournalUi.FAINT : 0xFF8E7B62);
         }
         // a dot on a tab with something to collect
         if (!sel && ((i == 0 && this.next.kind() == N_REPORT_READY) || (i == 1 && this.next.kind() == N_CONTRACT_READY) || (i == 0 && this.next.kind() == N_STARTER))) {
            FrontierUi.circle(g, x + 24, ty + 4, 2.5F, 0xFFD8B25A);
         }
      }
      // bottom: Field School, then Journal / Ranger assignments
      int by = this.top + this.panelH - 46;
      boolean overSchool = mx >= x + 8 && mx < x + this.side - 10 && my >= by && my < by + 18;
      FrontierUi.rect(g, x + 8, by, this.side - 18, 18, 2.0F, overSchool ? JournalUi.BUTTON_HOVER : JournalUi.BUTTON);
      FrontierUi.rect(g, x + 8, by, 2, 18, 1.0F, JournalUi.GOLD);
      JournalIcons.drawLifted(g, "school", x + 12, by + 1, 16, overSchool);
      JournalUi.draw(g, this.schoolLabel, x + 30, by + 6, JournalUi.SIDEBAR_TEXT);
      int hw = (this.side - 22) / 2;
      int by2 = by + 22;
      boolean overJ = mx >= x + 8 && mx < x + 8 + hw && my >= by2 && my < by2 + 18;
      boolean overA = mx >= x + 12 + hw && mx < x + 12 + 2 * hw && my >= by2 && my < by2 + 18;
      FrontierUi.rect(g, x + 8, by2, hw, 18, 2.0F, overJ ? JournalUi.BUTTON_HOVER : JournalUi.BUTTON);
      FrontierUi.rect(g, x + 12 + hw, by2, hw, 18, 2.0F, overA ? JournalUi.BUTTON_HOVER : JournalUi.BUTTON);
      JournalIcons.drawLifted(g, "notes", x + 10, by2 + 1, 16, overJ);
      JournalIcons.drawLifted(g, "assign", x + 14 + hw, by2 + 1, 16, overA);
      JournalUi.draw(g, this.journalLabel, x + 27, by2 + 6, JournalUi.SIDEBAR_TEXT);
      JournalUi.draw(g, this.assignLabel, x + 31 + hw, by2 + 6, JournalUi.SIDEBAR_TEXT);
      if (overJ) {
         tip = List.of(seq(L("side.journal_tip", "Open the Hunter's Journal"), FrontierUi.Size.SMALL));
      } else if (overA) {
         tip = List.of(seq(L("side.assignments_tip", "Open Ranger Assignments"), FrontierUi.Size.SMALL));
      }
      return tip;
   }

   // ============================================================================================ input

   private static void clickSound() {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
   }

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (button == 0) {
         int cx = this.left + this.panelW - 28, cy = this.top + 8;
         if (mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18) {
            clickSound();
            this.onClose();
            return true;
         }
         for (int i = 0; i < TABS.length; i++) {
            int ty = this.tabTop + i * this.tabH;
            if (mx >= this.left + 6 && mx < this.left + this.side - 4 && my >= ty && my < ty + this.tabH - 3) {
               this.selectTab(i);
               return true;
            }
         }
         int x = this.left;
         int by = this.top + this.panelH - 46;
         if (mx >= x + 8 && mx < x + this.side - 10 && my >= by && my < by + 18) {
            clickSound();
            this.minecraft.setScreen(new FieldSchoolScreen(this, -1));
            return true;
         }
         int hw = (this.side - 22) / 2;
         int by2 = by + 22;
         if (my >= by2 && my < by2 + 18) {
            if (mx >= x + 8 && mx < x + 8 + hw) {
               clickSound();
               this.minecraft.setScreen(new JournalScreen());
               return true;
            }
            if (mx >= x + 12 + hw && mx < x + 12 + 2 * hw) {
               clickSound();
               AssignmentScreen.send(0, "", 0); // the academy workstream's Ranger Assignments screen opens from the server reply
               return true;
            }
         }
         int ny = this.top + 30;
         if (loaded() && mx >= this.bodyX - 3 && mx < this.left + this.panelW - 16 && my >= ny - 2 && my < ny + 15) {
            clickSound();
            this.selectTab(this.next.tab());
            return true;
         }
         int sx = this.left + this.panelW - 12;
         if (this.maxScroll() > 0 && mx >= sx && mx < sx + 7 && my >= this.viewTop && my < this.viewBottom) {
            this.dragging = true;
            this.dragTo(my);
            return true;
         }
         if (mx >= this.bodyX - 6 && mx < this.left + this.panelW - 12 && my >= this.viewTop && my < this.viewBottom) {
            int base = this.viewTop - Math.round(this.scroll);
            for (Row r : this.rows) {
               int y = base + r.y;
               if (my < y || my >= y + r.h) {
                  continue;
               }
               for (Btn b : r.buttons) {
                  if (b.over(mx, my, this.bodyX, y)) {
                     if (b.active) {
                        clickSound();
                        b.action.run();
                     }
                     return true;
                  }
               }
               if (r.click != null) {
                  clickSound();
                  r.click.run();
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
      if (mx < this.left + this.side && mx >= this.left && my < this.top + this.panelH - 50) {
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
         case GLFW.GLFW_KEY_TAB -> this.selectTab(Math.floorMod(this.tab + ((mods & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1), TABS.length));
         default -> {
            if (key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + TABS.length) {
               this.selectTab(key - GLFW.GLFW_KEY_1);
               return true;
            }
            return super.keyPressed(key, scan, mods);
         }
      }
      return true;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   /** [onebook] opened from the Frontier Handbook: closing goes back to the Handbook page it came from. */
   @Override
   public void onClose() {
      if (!com.formaworks.frontierhunts.onebook.client.OneBookClient.back(this)) {
         super.onClose();
      }
   }
}
