package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.academy.AcademyNetwork;
import com.formaworks.frontierhunts.academy.Course;
import com.formaworks.frontierhunts.academy.client.AcademyClient;
import com.formaworks.frontierhunts.academy.client.DossierArt;
import com.formaworks.frontierhunts.journal.client.JournalUi;
import com.formaworks.frontierhunts.progression.Assignment;
import com.formaworks.frontierhunts.progression.AssignmentNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * [academy] Ranger Services dossier, rebuilt: a leather field dossier in the Hunter's Journal style. Left, the
 * assignments grouped (Ranger Academy courses, field work, bounties, supply runs) with status chips; right, the
 * selected entry: painted illustration, briefing, an objectives checklist with live progress, rewards, the record and
 * one clear primary action (Begin training / Leave training / Accept / Collect / Abandon). Smooth selection and
 * scrolling, keyboard support, lays out from the window size (GUI scale 1-4, small windows).
 *
 * <p>Kept for other classes: {@code new AssignmentScreen()}, {@link #refresh()}, {@link #send(int, String, int)}.
 */
public final class AssignmentScreen extends Screen {
   private static final int LEATHER_TEXT = JournalUi.SIDEBAR_TEXT;
   private static final int GOLD = 0xFFD8B46A;
   private static String lastKey = "";

   // layout
   private int x, y, w, h, side;
   private int listX, listY, listW, listH;
   private int pageX, pageY, pageW, pageH, barY;
   // content
   private final List<Row> rows = new ArrayList<>();
   private int selected = -1;
   private int hover = -1;
   private float listScroll, listScrollTarget;
   private int listMax;
   private float pageScroll, pageScrollTarget;
   private int pageMax;
   private float selY = -1.0F;
   private long selectedAt;
   private long openedAt;
   private long lastNanos;
   private String toast = "";
   private long toastAt;
   private String confirm = "";
   private long confirmAt;
   private long busyUntil;
   // buttons (x, y, w, h); w = 0 when absent
   private final int[] primary = new int[4];
   private final int[] secondary = new int[4];
   private final int[] close = new int[4];
   private final int[] back = new int[4];
   private Action primaryAction = Action.NONE;
   private Action secondaryAction = Action.NONE;
   private boolean dragList, dragPage;

   private enum Kind {
      HEADER,
      COURSE,
      ASSIGNMENT
   }

   private enum Action {
      NONE,
      BEGIN,
      LEAVE,
      ACCEPT,
      CLAIM,
      CANCEL
   }

   private record Row(Kind kind, String key, String label, Course course, AssignmentNetwork.Offer offer, String group) {
      boolean selectable() {
         return this.kind != Kind.HEADER;
      }
   }

   public AssignmentScreen() {
      super(Component.translatable("academy.frontierhunts.screen.title"));
   }

   // ================================================================================================ data

   private static AssignmentNetwork.Snapshot snap() {
      return FrontierClient.assignments;
   }

   private static AcademyNetwork.State acad() {
      return AcademyClient.state();
   }

   static String tr(String key, Object... args) {
      return I18n.get(key, args);
   }

   /** [onboard] Open the dossier on this course next time (the Handbook / Field School "practice on the range" links). */
   public static void focus(Course c) {
      if (c != null) {
         lastKey = "course:" + c.key;
      }
   }

   public static void send(int action, String id, int node) {
      PacketDistributor.sendToServer(new AssignmentNetwork.Request(action, id, node), new CustomPacketPayload[0]);
   }

   private static String group(AssignmentNetwork.Offer o) {
      Assignment a = o.terms();
      return switch (a.objective()) {
         case DELIVERY -> "supply";
         case TRACK -> "field";
         case HARVEST -> a.minimumMass() > 0 ? "bounty" : "field";
      };
   }

   static String plateFor(String id, Assignment a) {
      String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
      switch (path) {
         case "field_patrol":
            return "harvest";
         case "trail_reading":
            return "track";
         case "before_nightfall":
            return "timed";
         case "clean_recovery":
            return "clean";
         case "trophy_survey":
            return "trophy";
         case "patrol_provisions":
            return "supply";
         case "glassing_survey": // [1.1.7] the species-hunt assignments get their own pictures
            return "glassing";
         case "upland_count":
            return plateOr("upland", "harvest");
         case "waterfowl_survey":
            return plateOr("waterfowl", "harvest");
         default:
            return switch (a.objective()) {
               case TRACK -> "track";
               case DELIVERY -> "supply";
               case HARVEST -> a.minimumMass() > 0 ? "trophy" : (a.chestOnly() ? "clean" : (a.duration() > 0 ? "timed" : "harvest"));
            };
      }
   }

   /** [1.1.7] a plate if its picture ships, else the fallback */
   private static String plateOr(String plate, String fallback) {
      try {
         return net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(
            com.formaworks.frontierhunts.FrontierHunts.id("textures/gui/academy/plate_" + plate + ".png")).isPresent() ? plate : fallback;
      } catch (RuntimeException e) {
         return fallback;
      }
   }

   private static DossierArt.Icon iconFor(String plate) {
      return switch (plate) {
         case "glassing" -> DossierArt.Icon.GLASSING;
         case "stalk" -> DossierArt.Icon.STALK;
         case "range" -> DossierArt.Icon.RANGE;
         case "tracking" -> DossierArt.Icon.TRACKING;
         case "dressing" -> DossierArt.Icon.DRESSING;
         case "archery" -> DossierArt.Icon.ARCHERY; // [onboard]
         case "track" -> DossierArt.Icon.TRACK;
         case "timed" -> DossierArt.Icon.TIMED;
         case "clean" -> DossierArt.Icon.CLEAN;
         case "trophy" -> DossierArt.Icon.TROPHY;
         case "supply" -> DossierArt.Icon.SUPPLY;
         case "upland" -> DossierArt.Icon.UPLAND;
         case "waterfowl" -> DossierArt.Icon.WATERFOWL;
         default -> DossierArt.Icon.HARVEST;
      };
   }

   private void rebuildRows() {
      String keep = this.selected >= 0 && this.selected < this.rows.size() ? this.rows.get(this.selected).key : lastKey;
      this.rows.clear();
      this.rows.add(new Row(Kind.HEADER, "h:academy", tr("academy.frontierhunts.group.academy"), null, null, "academy"));
      for (Course c : Course.curriculum()) { // [onboard2] teaching order: the Archery Range last
         this.rows.add(new Row(Kind.COURSE, "course:" + c.key, tr(c.lang("title")), c, null, "academy"));
      }
      AssignmentNetwork.Snapshot s = snap();
      List<AssignmentNetwork.Offer> offers = new ArrayList<>();
      if (s != null) {
         offers.addAll(s.offers());
         if (s.terms() != null && offers.stream().noneMatch(o -> o.id().equals(s.id()))) {
            offers.add(new AssignmentNetwork.Offer(s.id(), s.terms(), 0));
         }
      }
      offers.sort(Comparator.comparingInt((AssignmentNetwork.Offer o) -> o.terms().tokens()).thenComparing(AssignmentNetwork.Offer::id));
      for (String g : new String[]{"field", "bounty", "supply"}) {
         boolean header = false;
         for (AssignmentNetwork.Offer o : offers) {
            if (!group(o).equals(g)) {
               continue;
            }
            if (!header) {
               this.rows.add(new Row(Kind.HEADER, "h:" + g, tr("academy.frontierhunts.group." + g), null, null, g));
               header = true;
            }
            this.rows.add(new Row(Kind.ASSIGNMENT, "a:" + o.id(), o.terms().title(), null, o, g));
         }
      }
      if (s == null) {
         this.rows.add(new Row(Kind.HEADER, "h:wait", tr("academy.frontierhunts.screen.waiting"), null, null, "field"));
      }
      this.selected = -1;
      for (int i = 0; i < this.rows.size(); i++) {
         if (this.rows.get(i).key.equals(keep)) {
            this.selected = i;
         }
      }
      if (this.selected < 0) {
         this.selected = this.defaultSelection();
      }
   }

   private int defaultSelection() {
      AcademyNetwork.State a = acad();
      AssignmentNetwork.Snapshot s = snap();
      String want = null;
      if (a != null && a.active() >= 0 && Course.byId(a.active()) != null) {
         want = "course:" + Course.byId(a.active()).key;
      } else if (s != null && !s.id().isEmpty() && s.state() != 0) {
         want = "a:" + s.id();
      }
      for (int i = 0; i < this.rows.size(); i++) {
         if (want != null ? this.rows.get(i).key.equals(want) : this.rows.get(i).selectable()) {
            return i;
         }
      }
      return 1;
   }

   private Row current() {
      return this.selected >= 0 && this.selected < this.rows.size() ? this.rows.get(this.selected) : null;
   }

   /** Called when new data arrives. */
   public void refresh() {
      int before = this.rows.size();
      String key = this.current() == null ? "" : this.current().key;
      this.rebuildRows();
      if (this.rows.size() != before || this.current() == null || !this.current().key.equals(key)) {
         this.layoutList();
      }
      this.busyUntil = 0L;
   }

   /** A short message on the page (refusals from the server). */
   public void toast(String text) {
      this.toast = text == null ? "" : text;
      this.toastAt = System.currentTimeMillis();
      this.busyUntil = 0L;
   }

   // ================================================================================================ layout

   @Override
   protected void init() {
      this.w = Math.min(this.width - 12, 740);
      this.h = Math.min(this.height - 12, 430);
      this.x = (this.width - this.w) / 2;
      this.y = (this.height - this.h) / 2;
      this.side = Mth.clamp((int)(this.w * 0.35F), 124, 214);
      this.listX = this.x + 6;
      this.listW = this.side - 12;
      this.listY = this.y + 46;
      this.listH = this.y + this.h - 30 - this.listY;
      this.pageX = this.x + this.side + 16;
      this.pageW = this.x + this.w - 12 - this.pageX;
      this.pageY = this.y + 8;
      this.barY = this.y + this.h - 27;
      this.pageH = this.barY - 6 - this.pageY;
      this.back[0] = this.x + 8;
      this.back[1] = this.y + this.h - 24;
      this.back[2] = this.side - 16;
      this.back[3] = 16;
      this.close[0] = this.x + this.w - 18;
      this.close[1] = this.y + 6;
      this.close[2] = 12;
      this.close[3] = 12;
      if (this.openedAt == 0L) {
         this.openedAt = System.currentTimeMillis();
      }
      this.rebuildRows();
      this.layoutList();
      AcademyClient.send(AcademyNetwork.A_SYNC, -1);
   }

   private void layoutList() {
      int total = 0;
      for (Row r : this.rows) {
         total += rowHeight(r);
      }
      this.listMax = Math.max(0, total - this.listH);
      this.listScrollTarget = Mth.clamp(this.listScrollTarget, 0, this.listMax);
      this.ensureVisible();
   }

   private static int rowHeight(Row r) {
      return r.kind == Kind.HEADER ? 16 : 20;
   }

   private int rowTop(int index) {
      int yy = 0;
      for (int i = 0; i < index && i < this.rows.size(); i++) {
         yy += rowHeight(this.rows.get(i));
      }
      return yy;
   }

   private void ensureVisible() {
      if (this.selected < 0) {
         return;
      }
      int top = this.rowTop(this.selected);
      int bottom = top + rowHeight(this.rows.get(this.selected));
      if (top - 16 < this.listScrollTarget) {
         this.listScrollTarget = Math.max(0, top - 16);
      } else if (bottom > this.listScrollTarget + this.listH) {
         this.listScrollTarget = Math.min(this.listMax, bottom - this.listH + 4);
      }
   }

   private void select(int index) {
      if (index < 0 || index >= this.rows.size() || !this.rows.get(index).selectable() || index == this.selected) {
         return;
      }
      this.selected = index;
      lastKey = this.rows.get(index).key;
      this.selectedAt = System.currentTimeMillis();
      this.pageScroll = this.pageScrollTarget = 0.0F;
      this.confirm = "";
      this.ensureVisible();
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.15F, 0.35F));
   }

   private void step(int dir) {
      int i = this.selected;
      for (int n = 0; n < this.rows.size(); n++) {
         i += dir;
         if (i < 0 || i >= this.rows.size()) {
            return;
         }
         if (this.rows.get(i).selectable()) {
            this.select(i);
            return;
         }
      }
   }

   // ================================================================================================ render

   @Override
   public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float pt) {
      long now = System.currentTimeMillis();
      long nanos = System.nanoTime();
      float dt = this.lastNanos == 0L ? 0.016F : Math.min(0.1F, (nanos - this.lastNanos) / 1.0E9F);
      this.lastNanos = nanos;
      float k = 1.0F - (float)Math.exp(-dt * 16.0F);
      this.listScroll += (this.listScrollTarget - this.listScroll) * k;
      this.pageScroll += (this.pageScrollTarget - this.pageScroll) * k;
      float open = Mth.clamp((now - this.openedAt) / 220.0F, 0.0F, 1.0F);
      g.fillGradient(0, 0, this.width, this.height, JournalUi.alpha(0xC8101210, open), JournalUi.alpha(0xD8080A08, open));
      g.pose().pushPose();
      float lift = (1.0F - open) * 8.0F;
      g.pose().translate(0.0F, lift, 0.0F);
      JournalUi.notebook(g, this.x, this.y, this.w, this.h, this.side);
      this.sidebar(g, mx, my - (int)lift, now, dt);
      this.page(g, mx, my - (int)lift, now);
      g.pose().popPose();
   }

   // ---------------------------------------------------------------------------------------------- sidebar

   private void sidebar(GuiGraphics g, int mx, int my, long now, float dt) {
      Font f = this.font;
      AssignmentNetwork.Snapshot s = snap();
      AcademyNetwork.State a = acad();
      DossierArt.icon(g, DossierArt.Icon.ACADEMY, this.x + 8, this.y + 7, 16, 0xFFFFFFFF);
      FrontierUi.text(g, FrontierUi.fit(tr("academy.frontierhunts.screen.brand"), this.side - 34, FrontierUi.Size.BRAND), this.x + 28, this.y + 8, LEATHER_TEXT,
         FrontierUi.Size.BRAND);
      String stats = s == null ? tr("academy.frontierhunts.screen.waiting") : tr("academy.frontierhunts.screen.stats", s.tokens(), s.xp());
      DossierArt.icon(g, DossierArt.Icon.TOKEN, this.x + 8, this.y + 25, 9, 0xFFFFFFFF);
      FrontierUi.text(g, FrontierUi.fit(stats, this.side - 26, FrontierUi.Size.SMALL), this.x + 20, this.y + 26, JournalUi.SIDEBAR_MUTED, FrontierUi.Size.SMALL);
      // certifications earned at the academy
      int skills = s == null ? 0 : s.skills();
      int cx = this.x + 8;
      for (int i = 0; i < 3; i++) {
         boolean on = (skills & 1 << i) != 0;
         DossierArt.icon(g, DossierArt.Icon.MEDAL, cx, this.y + 34, 9, on ? 0xFFFFFFFF : 0x55FFFFFF);
         cx += 11;
      }
      FrontierUi.text(g, tr("academy.frontierhunts.screen.certs", Integer.bitCount(skills)), cx + 2, this.y + 35, JournalUi.SIDEBAR_MUTED, FrontierUi.Size.SMALL);
      if (mx >= this.x + 8 && mx < this.x + 8 + 33 && my >= this.y + 33 && my < this.y + 44) {
         this.pendingTooltip = tr("academy.frontierhunts.screen.certs_tip", certName(0, skills), certName(1, skills), certName(2, skills));
      }
      // list
      g.enableScissor(this.listX, this.listY, this.listX + this.listW, this.listY + this.listH);
      int yy = this.listY - Math.round(this.listScroll);
      this.hover = -1;
      float targetSel = -1.0F;
      for (int i = 0; i < this.rows.size(); i++) {
         Row r = this.rows.get(i);
         int rh = rowHeight(r);
         if (i == this.selected) {
            targetSel = yy + Math.round(this.listScroll);
         }
         boolean inside = mx >= this.listX && mx < this.listX + this.listW && my >= Math.max(yy, this.listY) && my < Math.min(yy + rh, this.listY + this.listH);
         if (r.selectable() && inside) {
            this.hover = i;
         }
         yy += rh;
      }
      if (targetSel >= 0.0F) {
         if (this.selY < 0.0F) {
            this.selY = targetSel;
         }
         this.selY += (targetSel - this.selY) * (1.0F - (float)Math.exp(-dt * 18.0F));
         float sy = this.selY - this.listScroll;
         FrontierUi.rect(g, this.listX, sy + 1, this.listW, 18, 3.0F, 0x30F5E6C4);
         FrontierUi.rect(g, this.listX, sy + 3, 2.5F, 14, 1.2F, GOLD);
      }
      yy = this.listY - Math.round(this.listScroll);
      for (int i = 0; i < this.rows.size(); i++) {
         Row r = this.rows.get(i);
         int rh = rowHeight(r);
         if (yy + rh >= this.listY && yy <= this.listY + this.listH) {
            if (r.kind == Kind.HEADER) {
               String t = r.label.toUpperCase(Locale.ROOT);
               FrontierUi.text(g, FrontierUi.fit(t, this.listW - 6, FrontierUi.Size.SMALL), this.listX + 3, yy + 6, GOLD, FrontierUi.Size.SMALL);
               int tw = Math.min(this.listW - 6, FrontierUi.width(t, FrontierUi.Size.SMALL));
               g.fill(this.listX + 3 + tw + 4, yy + 9, this.listX + this.listW - 2, yy + 10, 0x40D8B46A);
            } else {
               this.row(g, r, i, yy, now);
            }
         }
         yy += rh;
      }
      g.disableScissor();
      if (this.listMax > 0) {
         float th = Math.max(14.0F, this.listH * this.listH / (float)(this.listH + this.listMax));
         float ty = this.listY + (this.listH - th) * (this.listScroll / this.listMax);
         FrontierUi.rect(g, this.x + this.side - 5, ty, 2.0F, th, 1.0F, 0x88D8B46A);
      }
      // back to the journal
      boolean bh = in(this.back, mx, my);
      FrontierUi.rect(g, this.back[0], this.back[1], this.back[2], this.back[3], 3.0F, bh ? 0xFF5B4636 : 0xFF3F2F24);
      DossierArt.icon(g, DossierArt.Icon.BOOK, this.back[0] + 4, this.back[1] + 3, 10, 0xFFFFFFFF);
      FrontierUi.text(g, FrontierUi.fit(tr(com.formaworks.frontierhunts.onebook.client.OneBookClient.fromHandbook() ? "onebook.frontierhunts.back_handbook" : "academy.frontierhunts.screen.back"), this.back[2] - 20 /* [onebook] */, FrontierUi.Size.SMALL), this.back[0] + 17, this.back[1] + 5,
         LEATHER_TEXT, FrontierUi.Size.SMALL);
   }

   private String pendingTooltip;

   private static String certName(int node, int skills) {
      return ((skills & 1 << node) != 0 ? "✔ " : "· ") + tr("academy.frontierhunts.cert." + node);
   }

   private void row(GuiGraphics g, Row r, int i, int yy, long now) {
      if (i == this.hover && i != this.selected) {
         FrontierUi.rect(g, this.listX, yy + 1, this.listW, 18, 3.0F, 0x18F5E6C4);
      }
      String plate = r.course != null ? r.course.key : plateFor(r.offer.id(), r.offer.terms());
      String path = r.course != null ? "" : r.offer.id().substring(r.offer.id().indexOf(':') + 1);
      DossierArt.Icon ic = path.equals("upland_count") ? DossierArt.Icon.UPLAND : path.equals("waterfowl_survey") ? DossierArt.Icon.WATERFOWL : iconFor(plate); // [1.1.7]
      DossierArt.icon(g, ic, this.listX + 5, yy + 3, 14, i == this.selected ? 0xFFFFFFFF : 0xDDFFFFFF);
      Chip chip = this.chip(r, now);
      int chipW = chip == null ? 0 : FrontierUi.width(chip.text, FrontierUi.Size.SMALL) + 8;
      int tx = this.listX + 23;
      int avail = this.listW - 23 - (chipW > 0 ? chipW + 6 : 2);
      FrontierUi.text(g, FrontierUi.fit(r.label, avail, FrontierUi.Size.BODY), tx, yy + 6, i == this.selected ? 0xFFFFF8E8 : LEATHER_TEXT, FrontierUi.Size.BODY);
      if (chip != null) {
         float cxp = this.listX + this.listW - chipW - 3;
         FrontierUi.rect(g, cxp, yy + 5, chipW, 10, 5.0F, chip.bg);
         FrontierUi.text(g, chip.text, cxp + 4, yy + 6, chip.fg, FrontierUi.Size.SMALL);
      }
   }

   private record Chip(String text, int bg, int fg) {
   }

   private Chip chip(Row r, long now) {
      if (r.course != null) {
         AcademyNetwork.State a = acad();
         if (a == null) {
            return null;
         }
         if (a.active() == r.course.ordinal()) {
            int pulse = (now / 500L) % 2 == 0 ? 0xFF4E8A45 : 0xFF3E6E37;
            return new Chip(tr("academy.frontierhunts.chip.training"), pulse, 0xFFF4FFE9);
         }
         AcademyNetwork.CourseInfo info = a.info(r.course.ordinal());
         if (info.certified()) {
            return new Chip(tr("academy.frontierhunts.chip.certified"), 0xFFB99859, 0xFF1F160C);
         }
         if (info.passes() > 0) {
            return new Chip(tr("academy.frontierhunts.chip.passed"), 0xFF3E6E37, 0xFFEAF5E0);
         }
         return new Chip(tr("academy.frontierhunts.chip.new"), 0x40F5E6C4, 0xFFE8DCC0);
      }
      AssignmentNetwork.Snapshot s = snap();
      if (s == null) {
         return null;
      }
      boolean mine = s.id().equals(r.offer.id()) && s.terms() != null;
      if (mine && s.state() == 1) {
         return new Chip(tr("academy.frontierhunts.chip.active"), 0xFF3E6E37, 0xFFEAF5E0);
      }
      if (mine && s.state() == 2) {
         int pulse = (now / 500L) % 2 == 0 ? 0xFFD8B46A : 0xFFB99859;
         return new Chip(tr("academy.frontierhunts.chip.ready"), pulse, 0xFF1F160C);
      }
      if (mine && s.state() == 3) {
         return new Chip(tr("academy.frontierhunts.chip.failed"), 0xFFA5281F, 0xFFFFE8E0);
      }
      if (r.offer.cooldown() > 0) {
         return new Chip(clock(r.offer.cooldown()), 0x40F5E6C4, 0xFFCFC2A4);
      }
      return null;
   }

   static String clock(long ticks) {
      long s = Math.max(0L, (ticks + 19L) / 20L);
      if (s >= 3600L) {
         return s / 3600L + ":" + String.format(Locale.ROOT, "%02d:%02d", s % 3600L / 60L, s % 60L);
      }
      return s / 60L + ":" + String.format(Locale.ROOT, "%02d", s % 60L);
   }

   // ---------------------------------------------------------------------------------------------- page

   private int cy;

   private void page(GuiGraphics g, int mx, int my, long now) {
      Row r = this.current();
      this.primary[2] = 0;
      this.secondary[2] = 0;
      this.primaryAction = Action.NONE;
      this.secondaryAction = Action.NONE;
      // close button
      boolean ch = in(this.close, mx, my);
      FrontierUi.circle(g, this.close[0] + 6, this.close[1] + 6, 6.0F, ch ? 0x30243A32 : 0x14243A32);
      DossierArt.icon(g, DossierArt.Icon.CROSS, this.close[0] + 1, this.close[1] + 1, 10, ch ? 0xFFA5281F : 0xFF5A5246);
      if (r == null) {
         this.pendingTooltip = null;
         return;
      }
      float fade = Mth.clamp((now - this.selectedAt) / 180.0F, 0.0F, 1.0F);
      int top = this.pageY;
      int bottom = this.pageY + this.pageH;
      g.enableScissor(this.pageX - 4, top, this.pageX + this.pageW + 4, bottom);
      this.cy = top - Math.round(this.pageScroll) + Math.round((1.0F - fade) * 6.0F);
      if (r.course != null) {
         this.coursePage(g, r.course, now, fade);
      } else {
         this.assignmentPage(g, r.offer, now, fade);
      }
      int contentH = this.cy + Math.round(this.pageScroll) - top;
      g.disableScissor();
      this.pageMax = Math.max(0, contentH - this.pageH + 6);
      this.pageScrollTarget = Mth.clamp(this.pageScrollTarget, 0, this.pageMax);
      if (this.pageMax > 0) {
         float th = Math.max(14.0F, this.pageH * this.pageH / (float)(this.pageH + this.pageMax));
         float ty = top + (this.pageH - th) * (this.pageScroll / this.pageMax);
         FrontierUi.rect(g, this.x + this.w - 7, ty, 2.0F, th, 1.0F, 0x66243A32);
         // soft paper fades at the scroll edges
         if (this.pageScroll > 1.0F) {
            g.fillGradient(this.pageX - 4, top, this.pageX + this.pageW + 4, top + 8, 0xFFEAE3D0, 0x00EAE3D0);
         }
         if (this.pageScroll < this.pageMax - 1) {
            g.fillGradient(this.pageX - 4, bottom - 8, this.pageX + this.pageW + 4, bottom, 0x00EAE3D0, 0xFFEAE3D0);
         }
      }
      // action bar
      g.fill(this.pageX, this.barY - 3, this.pageX + this.pageW, this.barY - 2, 0x40243A32);
      this.buttons(g, mx, my, now);
      // toast
      float ta = window(now - this.toastAt, 4500L, 150L, 600L);
      if (ta > 0.0F && !this.toast.isEmpty()) {
         int tw = Math.min(this.pageW - 8, FrontierUi.width(this.toast, FrontierUi.Size.SMALL) + 16);
         int tx = this.pageX + (this.pageW - tw) / 2;
         int ty = this.barY - 20;
         FrontierUi.rect(g, tx, ty, tw, 13, 4.0F, JournalUi.alpha(0xF03B2E20, ta));
         FrontierUi.text(g, FrontierUi.fit(this.toast, tw - 12, FrontierUi.Size.SMALL), tx + 8, ty + 3, JournalUi.alpha(0xFFF6E7C8, ta), FrontierUi.Size.SMALL);
      }
      if (this.pendingTooltip != null) {
         g.renderTooltip(this.font, this.font.split(Component.literal(this.pendingTooltip), 200), mx, my);
         this.pendingTooltip = null;
      }
   }

   private void plateBlock(GuiGraphics g, String plate, float fade) {
      int ph = Mth.clamp(Math.min(this.pageW / 2, (int)(this.h * 0.33F)), 52, 150);
      int px = this.pageX, py = this.cy, pw = this.pageW;
      // photo with a thin dark mount and two strips of tape
      g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF3B2E20);
      DossierArt.plate(g, plate, px, py, pw, ph, fade);
      g.fillGradient(px, py + ph - 14, px + pw, py + ph, 0x00000000, JournalUi.alpha(0x40000000, fade));
      tape(g, px + 10, py - 3, -8.0F);
      tape(g, px + pw - 34, py - 3, 7.0F);
      this.cy += ph + 8;
   }

   private static void tape(GuiGraphics g, float x, float y, float deg) {
      g.pose().pushPose();
      g.pose().translate(x + 12, y + 4, 0.0F);
      g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(deg));
      FrontierUi.rect(g, -12, -4, 24, 8, 0.5F, 0xB8E8DCC0);
      FrontierUi.rect(g, -12, -4, 24, 1, 0.0F, 0x30FFFFFF);
      g.pose().popPose();
   }

   private void kicker(GuiGraphics g, String text) {
      FrontierUi.text(g, FrontierUi.fit(text.toUpperCase(Locale.ROOT), this.pageW, FrontierUi.Size.SMALL), this.pageX, this.cy, JournalUi.GOLD_DARK, FrontierUi.Size.SMALL);
      this.cy += 9;
   }

   private void title(GuiGraphics g, String text) {
      for (FormattedCharSequence l : this.font.split(FrontierUi.c(text, FrontierUi.Size.TITLE), this.pageW)) {
         g.drawString(this.font, l, this.pageX, this.cy, JournalUi.INK, false);
         this.cy += 14;
      }
      this.cy += 2;
   }

   private void paragraph(GuiGraphics g, String text, int color) {
      for (FormattedCharSequence l : this.font.split(FrontierUi.c(text, FrontierUi.Size.BODY), this.pageW)) {
         g.drawString(this.font, l, this.pageX, this.cy, color, false);
         this.cy += 10;
      }
      this.cy += 4;
   }

   private void small(GuiGraphics g, String text, int color) {
      for (FormattedCharSequence l : this.font.split(FrontierUi.c(text, FrontierUi.Size.SMALL), this.pageW)) {
         g.drawString(this.font, l, this.pageX, this.cy, color, false);
         this.cy += 8;
      }
      this.cy += 3;
   }

   private void section(GuiGraphics g, String text) {
      this.cy += 3;
      String t = text.toUpperCase(Locale.ROOT);
      FrontierUi.text(g, t, this.pageX, this.cy, JournalUi.INK_BROWN, FrontierUi.Size.SMALL);
      int tw = FrontierUi.width(t, FrontierUi.Size.SMALL);
      g.fill(this.pageX + tw + 5, this.cy + 3, this.pageX + this.pageW, this.cy + 4, JournalUi.RULE);
      this.cy += 11;
   }

   /** One objective row: check box, label, count and a live bar. */
   private void objective(GuiGraphics g, String label, int progress, int target, boolean live, boolean bullet) {
      boolean done = target > 0 && progress >= target;
      int ix = this.pageX;
      if (bullet) {
         FrontierUi.circle(g, ix + 4, this.cy + 4, 1.6F, JournalUi.MUTED);
      } else {
         JournalUi.checkbox(g, ix, this.cy - 1, done, done ? JournalUi.GREEN : JournalUi.INK);
      }
      String count = !bullet && target > 1 ? progress + " / " + target : "";
      int cw = count.isEmpty() ? 0 : FrontierUi.width(count, FrontierUi.Size.SMALL) + 6;
      List<FormattedCharSequence> lines = this.font.split(FrontierUi.c(label, FrontierUi.Size.BODY), this.pageW - 15 - cw);
      int color = done ? JournalUi.MUTED : JournalUi.INK;
      int ly = this.cy;
      for (FormattedCharSequence l : lines) {
         g.drawString(this.font, l, ix + 15, ly, color, false);
         if (done) {
            g.fill(ix + 15, ly + 4, ix + 15 + this.font.width(l), ly + 5, 0x886A7266);
         }
         ly += 10;
      }
      if (!count.isEmpty()) {
         FrontierUi.right(g, count, this.pageX + this.pageW, this.cy, done ? JournalUi.GREEN : JournalUi.INK_BROWN, FrontierUi.Size.SMALL);
      }
      this.cy = ly;
      if (live && target > 1 && !bullet) {
         JournalUi.bar(g, ix + 15, this.cy, this.pageW - 15, 3, progress / (float)target, JournalUi.TRACK, done ? JournalUi.GREEN : JournalUi.GOLD_DARK);
         this.cy += 6;
      }
      this.cy += 3;
   }

   private int chipX;

   private void chipsStart() {
      this.chipX = this.pageX;
   }

   private void rewardChip(GuiGraphics g, DossierArt.Icon icon, String text, int bg, int fg) {
      int cw = FrontierUi.width(text, FrontierUi.Size.SMALL) + (icon == null ? 10 : 22);
      if (this.chipX + cw > this.pageX + this.pageW && this.chipX > this.pageX) {
         this.chipX = this.pageX;
         this.cy += 15;
      }
      FrontierUi.rect(g, this.chipX, this.cy, cw, 12, 6.0F, bg);
      if (icon != null) {
         DossierArt.icon(g, icon, this.chipX + 4, this.cy + 1.5F, 9, 0xFFFFFFFF);
      }
      FrontierUi.text(g, text, this.chipX + (icon == null ? 5 : 16), this.cy + 2, fg, FrontierUi.Size.SMALL);
      this.chipX += cw + 4;
   }

   private void chipsEnd() {
      this.cy += 17;
   }

   private void coursePage(GuiGraphics g, Course c, long now, float fade) {
      AcademyNetwork.State a = acad();
      AcademyNetwork.CourseInfo info = a == null ? new AcademyNetwork.CourseInfo(0, 0, 0, false) : a.info(c.ordinal());
      boolean active = a != null && a.active() == c.ordinal();
      AcademyNetwork.Hud hud = AcademyClient.hud();
      boolean live = active && hud != null && hud.course() == c.ordinal();
      this.plateBlock(g, c.key, fade);
      this.kicker(g, tr("academy.frontierhunts.page.course_kicker", c.step(), Course.count(), c.timeLimit / 1200)); // [onboard] curriculum position
      this.title(g, tr(c.lang("title")));
      // status line
      String status;
      int sc;
      if (active) {
         status = tr("academy.frontierhunts.page.training_now", clock(AcademyClient.remaining()));
         sc = JournalUi.GREEN;
      } else if (info.passes() > 0) {
         status = tr("academy.frontierhunts.page.record", info.passes(), clock(info.bestTicks()));
         sc = JournalUi.GREEN;
      } else {
         status = tr("academy.frontierhunts.page.not_attempted");
         sc = JournalUi.MUTED;
      }
      if (info.certified()) {
         status += "  ·  " + tr("academy.frontierhunts.page.certified", tr("academy.frontierhunts.cert." + c.cert));
      }
      this.small(g, status, sc);
      this.paragraph(g, tr(c.lang("brief")), JournalUi.INK_BROWN);
      this.section(g, tr("academy.frontierhunts.page.objectives"));
      for (int i = 0; i < c.objectives.length; i++) {
         int p = live && i < hud.progress().length ? hud.progress()[i] : 0;
         this.objective(g, tr(c.objectiveLang(i)), p, c.targets[i], live, false);
      }
      this.section(g, tr("academy.frontierhunts.page.rewards"));
      this.chipsStart();
      boolean first = info.passes() == 0;
      int ranger = first ? c.rangerXp : Math.max(5, c.rangerXp / 4);
      int skill = first ? c.skillXp : Math.max(10, c.skillXp / 4);
      this.rewardChip(g, DossierArt.Icon.STAR, tr("academy.frontierhunts.reward.ranger", ranger), 0xFF3E5945, 0xFFF1EAD6);
      this.rewardChip(g, DossierArt.Icon.STAR, tr("academy.frontierhunts.reward.skill", skill, tr("journal.frontierhunts.skill." + c.skill.key + ".name")), 0xFF5A4636,
         0xFFF1EAD6);
      if (c.cert >= 0 && !info.certified()) {
         this.rewardChip(g, DossierArt.Icon.MEDAL, tr("academy.frontierhunts.reward.cert", tr("academy.frontierhunts.cert." + c.cert)), 0xFFB99859, 0xFF1F160C);
      }
      this.chipsEnd();
      if (c.cert >= 0) {
         this.small(g, tr("academy.frontierhunts.cert." + c.cert + ".effect"), JournalUi.MUTED);
      }
      // [onboard] one learning path: passing the course also completes these Field School lessons (and Handbook steps)
      StringBuilder lessons = new StringBuilder();
      for (com.formaworks.frontierhunts.guide.Lesson l : com.formaworks.frontierhunts.onboard.Handbook.lessonsFor(c)) {
         lessons.append(lessons.length() > 0 ? ", " : "").append(tr(l.lang("title")));
      }
      this.small(g, tr("onboard.frontierhunts.dossier.credits", lessons), JournalUi.GREEN);
      if (!first) {
         this.small(g, info.cooldown() > 0 ? tr("academy.frontierhunts.page.repeat_wait", clock(info.cooldown())) : tr("academy.frontierhunts.page.repeat_ready"),
            JournalUi.MUTED);
      }
      this.section(g, tr("academy.frontierhunts.page.kit"));
      this.small(g, tr(c.lang("kit")), JournalUi.INK_BROWN);
      this.section(g, tr("academy.frontierhunts.page.how"));
      this.small(g, tr("academy.frontierhunts.page.how_text", AcademyClient.LEAVE.getTranslatedKeyMessage().getString()), JournalUi.MUTED);
      // actions
      if (active) {
         this.primaryAction = Action.LEAVE;
      } else if (a != null && a.available() && a.active() < 0) {
         this.primaryAction = Action.BEGIN;
      }
   }

   private void assignmentPage(GuiGraphics g, AssignmentNetwork.Offer o, long now, float fade) {
      AssignmentNetwork.Snapshot s = snap();
      Assignment t = o.terms();
      boolean mine = s != null && s.id().equals(o.id()) && s.terms() != null && s.state() != 0;
      Assignment terms = mine ? s.terms() : t;
      String plate = plateFor(o.id(), terms);
      this.plateBlock(g, plate, fade);
      String grp = group(o);
      String limit = terms.duration() > 0 ? tr("academy.frontierhunts.page.limit", clock(terms.duration())) : tr("academy.frontierhunts.page.no_limit");
      this.kicker(g, tr("academy.frontierhunts.group." + grp) + "  ·  " + limit);
      this.title(g, terms.title());
      String status;
      int sc;
      if (mine && s.state() == 2) {
         status = tr("academy.frontierhunts.page.ready");
         sc = JournalUi.GOLD_DARK;
      } else if (mine && s.state() == 3) {
         status = tr("academy.frontierhunts.page.failed");
         sc = JournalUi.RED;
      } else if (mine) {
         status = terms.duration() > 0 ? tr("academy.frontierhunts.page.active_timed", clock(s.remaining())) : tr("academy.frontierhunts.page.active");
         sc = JournalUi.GREEN;
      } else if (o.cooldown() > 0) {
         status = tr("academy.frontierhunts.page.cooldown", clock(o.cooldown()));
         sc = JournalUi.MUTED;
      } else if (s != null && s.terms() != null && (s.state() == 1 || s.state() == 2)) {
         status = tr("academy.frontierhunts.page.busy");
         sc = JournalUi.MUTED;
      } else {
         status = tr("academy.frontierhunts.page.open");
         sc = JournalUi.INK_BROWN;
      }
      this.small(g, status, sc);
      this.paragraph(g, terms.briefing(), JournalUi.INK_BROWN);
      this.section(g, tr("academy.frontierhunts.page.objectives"));
      int count = mine ? Math.min(s.count(), terms.target()) : 0;
      this.objective(g, terms.instruction(), count, terms.target(), mine, false);
      if (terms.duration() > 0) {
         this.objective(g, tr("academy.frontierhunts.page.within", clock(terms.duration())), 0, 0, false, true);
         if (mine && s.state() == 1) {
            JournalUi.bar(g, this.pageX + 15, this.cy - 2, this.pageW - 15, 3, s.remaining() / (float)Math.max(1, terms.duration()), JournalUi.TRACK, JournalUi.GOLD_DARK);
            this.cy += 5;
         }
      }
      if (terms.chestOnly()) {
         this.objective(g, tr("academy.frontierhunts.page.chest"), 0, 0, false, true);
      }
      if (terms.minimumMass() > 0) {
         this.objective(g, tr("academy.frontierhunts.page.mass", terms.minimumMass()), 0, 0, false, true);
      }
      this.objective(g, tr("academy.frontierhunts.page.after_accept"), 0, 0, false, true);
      this.section(g, tr("academy.frontierhunts.page.rewards"));
      this.chipsStart();
      this.rewardChip(g, DossierArt.Icon.TOKEN, tr("academy.frontierhunts.reward.tokens", terms.tokens()), 0xFF5A4636, 0xFFF1EAD6);
      this.rewardChip(g, DossierArt.Icon.STAR, tr("academy.frontierhunts.reward.ranger", terms.experience()), 0xFF3E5945, 0xFFF1EAD6);
      this.chipsEnd();
      this.section(g, tr("academy.frontierhunts.page.how"));
      this.small(g, tr(terms.hunt().isEmpty() ? "academy.frontierhunts.page.how_" + terms.objective().getSerializedName() : "hunts.frontierhunts.assign.how"), JournalUi.MUTED); // [hunts]
      if (s != null && !s.eligible()) {
         this.small(g, tr("academy.frontierhunts.page.ineligible"), JournalUi.RED);
      }
      // actions
      if (s != null && s.eligible()) {
         if (mine && s.state() == 2) {
            this.primaryAction = Action.CLAIM;
         } else if ((s.state() == 0 || s.state() == 3) && o.cooldown() == 0) {
            this.primaryAction = Action.ACCEPT;
         }
         if (mine && (s.state() == 1 || s.state() == 3)) {
            this.secondaryAction = Action.CANCEL;
         }
      }
   }

   private void buttons(GuiGraphics g, int mx, int my, long now) {
      Row r = this.current();
      String pLabel;
      boolean pActive = this.primaryAction != Action.NONE && now >= this.busyUntil;
      if (r == null) {
         return;
      }
      if (r.course != null) {
         AcademyNetwork.State a = acad();
         if (this.primaryAction == Action.LEAVE) {
            pLabel = this.confirm.equals("leave") && now - this.confirmAt < 3000L ? tr("academy.frontierhunts.button.leave_confirm") : tr("academy.frontierhunts.button.leave");
         } else if (a == null) {
            pLabel = tr("academy.frontierhunts.screen.waiting");
         } else if (!a.available()) {
            pLabel = tr("academy.frontierhunts.button.closed");
         } else if (a.active() >= 0) {
            pLabel = tr("academy.frontierhunts.button.elsewhere");
         } else if (now < this.busyUntil) {
            pLabel = tr("academy.frontierhunts.button.departing");
         } else {
            pLabel = tr("academy.frontierhunts.button.begin");
         }
      } else {
         pLabel = switch (this.primaryAction) {
            case CLAIM -> tr("academy.frontierhunts.button.claim");
            case ACCEPT -> tr("academy.frontierhunts.button.accept");
            default -> {
               AssignmentNetwork.Snapshot s = snap();
               boolean mine = s != null && s.id().equals(r.offer.id()) && s.state() == 1;
               yield mine ? tr("academy.frontierhunts.button.in_progress") : (r.offer.cooldown() > 0 ? tr("academy.frontierhunts.button.resting")
                  : tr("academy.frontierhunts.button.unavailable"));
            }
         };
      }
      int bw = Mth.clamp(FrontierUi.width(pLabel, FrontierUi.Size.STRONG) + 34, 92, this.pageW / 2 + 30);
      this.primary[0] = this.pageX + this.pageW - bw;
      this.primary[1] = this.barY;
      this.primary[2] = bw;
      this.primary[3] = 20;
      boolean hov = pActive && in(this.primary, mx, my);
      int bg = !pActive ? 0xFF8C8A7C : (this.primaryAction == Action.LEAVE ? (hov ? 0xFFB8463A : 0xFFA5281F)
         : (this.primaryAction == Action.CLAIM ? (hov ? 0xFFD8B46A : 0xFFB99859) : (hov ? JournalUi.BUTTON_HOVER : JournalUi.BUTTON)));
      FrontierUi.shadow(g, this.primary[0], this.primary[1], bw, 20, 4.0F, pActive ? 2.0F : 0.0F);
      FrontierUi.rect(g, this.primary[0], this.primary[1], bw, 20, 4.0F, bg);
      if (pActive && this.primaryAction != Action.LEAVE) {
         FrontierUi.rect(g, this.primary[0] + 2, this.primary[1] + 17, bw - 4, 1.2F, 0.6F, 0x55FFFFFF);
      }
      DossierArt.Icon pi = switch (this.primaryAction) {
         case BEGIN -> DossierArt.Icon.ACADEMY;
         case LEAVE -> DossierArt.Icon.LEAVE;
         case CLAIM -> DossierArt.Icon.MEDAL;
         case ACCEPT -> DossierArt.Icon.FLAG;
         default -> DossierArt.Icon.LOCK;
      };
      int fg = this.primaryAction == Action.CLAIM && pActive ? 0xFF1F160C : 0xFFF6EFDC;
      DossierArt.icon(g, pi, this.primary[0] + 8, this.primary[1] + 4, 12, 0xFFFFFFFF);
      FrontierUi.text(g, FrontierUi.fit(pLabel, bw - 30, FrontierUi.Size.STRONG), this.primary[0] + 24, this.primary[1] + 6, fg, FrontierUi.Size.STRONG);
      if (this.secondaryAction == Action.CANCEL) {
         String sl = this.confirm.equals("cancel") && now - this.confirmAt < 3000L ? tr("academy.frontierhunts.button.abandon_confirm")
            : tr("academy.frontierhunts.button.abandon");
         int sw = FrontierUi.width(sl, FrontierUi.Size.BODY) + 18;
         this.secondary[0] = this.primary[0] - sw - 6;
         this.secondary[1] = this.barY;
         this.secondary[2] = sw;
         this.secondary[3] = 20;
         boolean sh = in(this.secondary, mx, my);
         FrontierUi.outline(g, this.secondary[0], this.secondary[1], sw, 20, 4.0F, sh ? JournalUi.RED : 0xFF8C8A78, sh ? 0x18A5281F : JournalUi.PAPER);
         FrontierUi.text(g, sl, this.secondary[0] + 9, this.secondary[1] + 6, sh ? JournalUi.RED : JournalUi.INK_BROWN, FrontierUi.Size.BODY);
      }
      // safety note left of the buttons (courses)
      if (r.course != null && this.primary[0] - this.pageX > 70) {
         DossierArt.icon(g, DossierArt.Icon.LOCK, this.pageX, this.barY + 5, 9, 0xCCFFFFFF);
         List<FormattedCharSequence> ls = this.font.split(FrontierUi.c(tr("academy.frontierhunts.page.safe_short"), FrontierUi.Size.SMALL), this.primary[0] - this.pageX - 18);
         int ly = this.barY + (ls.size() > 1 ? 2 : 6);
         for (int i = 0; i < Math.min(2, ls.size()); i++) {
            g.drawString(this.font, ls.get(i), this.pageX + 12, ly, JournalUi.MUTED, false);
            ly += 8;
         }
      }
   }

   static float window(long age, long total, long in, long out) {
      if (age < 0L || age > total) {
         return 0.0F;
      }
      if (age < in) {
         return age / (float)in;
      }
      return age > total - out ? (total - age) / (float)out : 1.0F;
   }

   private static boolean in(int[] r, double mx, double my) {
      return r[2] > 0 && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
   }

   // ================================================================================================ input

   private void press(Action a) {
      Row r = this.current();
      if (r == null || a == Action.NONE) {
         return;
      }
      long now = System.currentTimeMillis();
      Minecraft mc = Minecraft.getInstance();
      switch (a) {
         case BEGIN -> {
            if (now < this.busyUntil) {
               return;
            }
            this.busyUntil = now + 2500L;
            AcademyClient.send(AcademyNetwork.A_BEGIN, r.course.ordinal());
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.5F));
         }
         case LEAVE -> {
            if (this.confirm.equals("leave") && now - this.confirmAt < 3000L) {
               this.confirm = "";
               AcademyClient.send(AcademyNetwork.A_LEAVE, -1);
               com.formaworks.frontierhunts.onebook.client.OneBookClient.forget(); // [onebook] leaving a course closes to the game
               this.onClose();
            } else {
               this.confirm = "leave";
               this.confirmAt = now;
            }
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.9F, 0.5F));
         }
         case ACCEPT -> {
            send(1, r.offer.id(), 0);
            this.busyUntil = now + 600L;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.7F));
         }
         case CLAIM -> {
            send(2, r.offer.id(), 0);
            this.busyUntil = now + 600L;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.4F, 0.35F));
         }
         case CANCEL -> {
            if (this.confirm.equals("cancel") && now - this.confirmAt < 3000L) {
               this.confirm = "";
               send(3, "", 0);
               this.busyUntil = now + 600L;
            } else {
               this.confirm = "cancel";
               this.confirmAt = now;
            }
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.9F, 0.5F));
         }
         default -> {
         }
      }
   }

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (button != 0) {
         return super.mouseClicked(mx, my, button);
      }
      if (in(this.close, mx, my)) {
         this.onClose();
         return true;
      }
      if (in(this.back, mx, my)) {
         if (com.formaworks.frontierhunts.onebook.client.OneBookClient.back(this)) { // [onebook] opened from the Handbook: back to it
            return true;
         }
         Minecraft.getInstance().setScreen(new JournalScreen());
         return true;
      }
      if (in(this.primary, mx, my)) {
         if (this.primaryAction != Action.NONE && System.currentTimeMillis() >= this.busyUntil) {
            this.press(this.primaryAction);
         }
         return true;
      }
      if (in(this.secondary, mx, my)) {
         this.press(this.secondaryAction);
         return true;
      }
      if (mx >= this.listX && mx < this.listX + this.listW && my >= this.listY && my < this.listY + this.listH) {
         if (this.hover >= 0) {
            this.select(this.hover);
         }
         return true;
      }
      if (this.listMax > 0 && mx >= this.x + this.side - 7 && mx < this.x + this.side && my >= this.listY && my < this.listY + this.listH) {
         this.dragList = true;
         return true;
      }
      if (this.pageMax > 0 && mx >= this.x + this.w - 9 && mx < this.x + this.w && my >= this.pageY && my < this.pageY + this.pageH) {
         this.dragPage = true;
         return true;
      }
      return super.mouseClicked(mx, my, button);
   }

   @Override
   public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      if (this.dragList && this.listMax > 0) {
         this.listScrollTarget = this.listScroll = Mth.clamp((float)((my - this.listY) / this.listH * (this.listH + this.listMax) - this.listH / 2.0), 0, this.listMax);
         return true;
      }
      if (this.dragPage && this.pageMax > 0) {
         this.pageScrollTarget = this.pageScroll = Mth.clamp((float)((my - this.pageY) / this.pageH * (this.pageH + this.pageMax) - this.pageH / 2.0), 0, this.pageMax);
         return true;
      }
      return super.mouseDragged(mx, my, button, dx, dy);
   }

   @Override
   public boolean mouseReleased(double mx, double my, int button) {
      this.dragList = false;
      this.dragPage = false;
      return super.mouseReleased(mx, my, button);
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      if (mx < this.x + this.side) {
         this.listScrollTarget = Mth.clamp(this.listScrollTarget - (float)(sy * 24.0), 0, this.listMax);
      } else {
         this.pageScrollTarget = Mth.clamp(this.pageScrollTarget - (float)(sy * 26.0), 0, this.pageMax);
      }
      return true;
   }

   @Override
   public boolean keyPressed(int key, int scan, int mods) {
      switch (key) {
         case GLFW.GLFW_KEY_UP -> {
            this.step(-1);
            return true;
         }
         case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_TAB -> {
            this.step(1);
            return true;
         }
         case GLFW.GLFW_KEY_PAGE_UP -> {
            this.pageScrollTarget = Mth.clamp(this.pageScrollTarget - this.pageH * 0.8F, 0, this.pageMax);
            return true;
         }
         case GLFW.GLFW_KEY_PAGE_DOWN -> {
            this.pageScrollTarget = Mth.clamp(this.pageScrollTarget + this.pageH * 0.8F, 0, this.pageMax);
            return true;
         }
         case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
            if (this.primaryAction != Action.NONE && System.currentTimeMillis() >= this.busyUntil) {
               this.press(this.primaryAction);
            }
            return true;
         }
         default -> {
            if (AcademyClient.ASSIGNMENTS.matches(key, scan)) {
               this.onClose();
               return true;
            }
            return super.keyPressed(key, scan, mods);
         }
      }
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
