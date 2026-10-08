package com.formaworks.frontierhunts.guide.client;

import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.guide.GuideNetwork;
import com.formaworks.frontierhunts.guide.Lesson;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * [guide] The Field School guide: eight illustrated lessons in the Hunter's Journal notebook. Opens with the guide key
 * (H), from the welcome card, or from the "Field School" entry in the Journal and Expedition Guide sidebars.
 */
public final class FieldSchoolScreen extends Screen {
   private static final String[] TIP_SECTIONS = {"camps", "atv", "seasons", "steady"};

   /** [integ3] The bound key of the rifle workstream's prone stance (default Z), for the "steady" texts. */
   private static String proneKey() {
      return com.formaworks.frontierhunts.client.prone.ProneClient.PRONE.getTranslatedKeyMessage().getString();
   }
   private final Screen parent;
   private int page;
   private int left, top, panelW, panelH, side, bodyX, bodyW, viewTop, viewBottom;
   private int scroll, maxScroll, contentH;
   private final List<Block> blocks = new ArrayList<>();
   private Button skipLesson, courseToggle, prev, next;
   // [onboard] "practice this on the range" + "open the Handbook", placed over the practice block while it is in view
   private Button practiceBtn, handbookBtn;
   private int practiceY = Integer.MIN_VALUE;
   private static final int PRACTICE_H = 64;
   private long skipArmedUntil, courseArmedUntil;
   private boolean tipsReported;
   private long openedAt;

   private interface Draw {
      void draw(GuiGraphics g, int x, int y, int w);
   }

   private record Block(int height, Draw draw) {
   }

   public FieldSchoolScreen(Screen parent, int page) {
      super(Component.translatable("guide.frontierhunts.screen.title"));
      this.parent = parent;
      this.page = page;
   }

   // ============================================================================================ state

   private static int done() {
      return GuideClient.state == null ? 0 : GuideClient.state.done();
   }

   private static int status(Lesson l) {
      int d = done();
      if (l.done(d)) {
         return 2;
      }
      return Lesson.current(d) == l ? 1 : 0;
   }

   void stateChanged() {
      this.rebuild(false);
   }

   // ============================================================================================ layout

   @Override
   protected void init() {
      this.openedAt = System.currentTimeMillis();
      if (this.page < 0) {
         Lesson cur = Lesson.current(done());
         this.page = cur == null ? 0 : cur.ordinal();
      }
      this.page = Mth.clamp(this.page, 0, Lesson.count() - 1);
      this.panelW = Math.min(600, this.width - 16);
      this.panelH = Math.min(392, this.height - 16);
      this.left = (this.width - this.panelW) / 2;
      this.top = (this.height - this.panelH) / 2;
      this.side = this.panelW < 460 ? 104 : 132;
      this.bodyX = this.left + this.side + 20;
      this.bodyW = this.panelW - this.side - 40;
      this.viewTop = this.top + 52;
      this.viewBottom = this.top + this.panelH - 34;
      int rows = Lesson.count();
      int rowH = Math.max(14, Math.min(26, (this.panelH - 48 - 62) / rows));
      for (int i = 0; i < rows; i++) {
         int idx = i;
         this.addRenderableWidget(new LessonRow(this.left + 6, this.top + 46 + i * rowH, this.side - 12, rowH - 3, idx, b -> this.go(idx)));
      }
      this.courseToggle = this.addRenderableWidget(new GuideButton(this.left + 6, this.top + this.panelH - 26, this.side - 12, 18, Component.empty(), b -> this.toggleCourse(), true));
      int by = this.top + this.panelH - 27;
      int bw = Math.min(84, (this.bodyW - 10) / 3);
      this.next = this.addRenderableWidget(new GuideButton(this.bodyX + this.bodyW - bw, by, bw, 20, Component.translatable("guide.frontierhunts.screen.next"), b -> this.go(this.page + 1), false));
      this.prev = this.addRenderableWidget(new GuideButton(this.bodyX + this.bodyW - bw * 2 - 6, by, bw, 20, Component.translatable("guide.frontierhunts.screen.prev"), b -> this.go(this.page - 1), false));
      this.skipLesson = this.addRenderableWidget(new GuideButton(this.bodyX, by, Math.min(150, this.bodyW - bw * 2 - 16), 20, Component.empty(), b -> this.skipLesson(), false));
      this.practiceBtn = this.addRenderableWidget(new GuideButton(this.bodyX + 10, by, Math.min(190, (this.bodyW - 30) / 2), 18, Component.empty(), b -> {
         com.formaworks.frontierhunts.academy.Course c = com.formaworks.frontierhunts.onboard.Handbook.courseFor(Lesson.byId(this.page));
         if (c != null) {
            HandbookClient.practice(c);
         }
      }, false));
      this.handbookBtn = this.addRenderableWidget(new GuideButton(this.bodyX + 10, by, Math.min(150, (this.bodyW - 30) / 2), 18,
         Component.translatable("onboard.frontierhunts.school.open_handbook"), b -> this.openHandbook(), false));
      this.practiceBtn.visible = false;
      this.handbookBtn.visible = false;
      this.rebuild(true);
   }

   private void go(int p) {
      int np = Mth.clamp(p, 0, Lesson.count() - 1);
      if (np != this.page) {
         this.page = np;
         this.skipArmedUntil = 0L;
         this.rebuild(true);
      }
   }

   private void skipLesson() {
      long now = System.currentTimeMillis();
      if (now < this.skipArmedUntil) {
         this.skipArmedUntil = 0L;
         GuideClient.send(GuideNetwork.A_SKIP_LESSON, this.page);
      } else {
         this.skipArmedUntil = now + 3000L;
      }
      this.updateButtons();
   }

   private void toggleCourse() {
      GuideNetwork.State s = GuideClient.state;
      if (s == null) {
         return;
      }
      long now = System.currentTimeMillis();
      if (!s.has(GuideNetwork.F_WELCOMED)) {
         GuideClient.send(GuideNetwork.A_BEGIN, 0);
      } else if (s.has(GuideNetwork.F_SKIPPED)) {
         GuideClient.send(GuideNetwork.A_RESUME, 0);
      } else if (now < this.courseArmedUntil) {
         this.courseArmedUntil = 0L;
         GuideClient.send(GuideNetwork.A_SKIP_ALL, 0);
      } else {
         this.courseArmedUntil = now + 3000L;
      }
      this.updateButtons();
   }

   private void updateButtons() {
      if (this.next == null) {
         return;
      }
      long now = System.currentTimeMillis();
      GuideNetwork.State s = GuideClient.state;
      Lesson l = Lesson.byId(this.page);
      this.prev.active = this.page > 0;
      this.next.active = this.page < Lesson.count() - 1;
      boolean canSkip = s != null && GuideClient.courseRunning() && l != null && !l.done(s.done()) && !l.optional();
      this.skipLesson.visible = canSkip;
      this.skipLesson.setMessage(Component.translatable(now < this.skipArmedUntil ? "guide.frontierhunts.screen.skip_confirm" : "guide.frontierhunts.screen.skip_lesson"));
      this.courseToggle.visible = s != null && s.has(GuideNetwork.F_ENABLED) && !(s.has(GuideNetwork.F_GRADUATED) && !s.has(GuideNetwork.F_SKIPPED));
      String key = s == null ? "guide.frontierhunts.screen.skip_course"
         : !s.has(GuideNetwork.F_WELCOMED) ? "guide.frontierhunts.screen.begin_course"
         : s.has(GuideNetwork.F_SKIPPED) ? "guide.frontierhunts.screen.resume_course"
         : now < this.courseArmedUntil ? "guide.frontierhunts.screen.skip_confirm" : "guide.frontierhunts.screen.skip_course";
      this.courseToggle.setMessage(Component.translatable(key));
   }

   @Override
   public void tick() {
      long now = System.currentTimeMillis();
      if (this.skipArmedUntil != 0L && now >= this.skipArmedUntil || this.courseArmedUntil != 0L && now >= this.courseArmedUntil) {
         if (now >= this.skipArmedUntil) {
            this.skipArmedUntil = 0L;
         }
         if (now >= this.courseArmedUntil) {
            this.courseArmedUntil = 0L;
         }
         this.updateButtons();
      }
   }

   // ============================================================================================ content

   private void rebuild(boolean resetScroll) {
      this.blocks.clear();
      Lesson l = Lesson.byId(this.page);
      if (l == null) {
         return;
      }
      int w = this.bodyW;
      switch (l) {
         case WIND -> this.art(GuideArt.WIND, w);
         case SIGN -> this.art(GuideArt.TRACKS, w);
         case GLASS -> this.art(GuideArt.GLASS, w);
         case STALK -> this.art(GuideArt.STALK, w);
         case SHOT -> this.art(GuideArt.VITALS, w);
         case TRAIL -> this.art(GuideArt.BLOOD, w);
         case HARVEST -> this.art(GuideArt.HARVEST, w);
         default -> {
         }
      }
      for (int i = 1; i <= 8; i++) {
         String key = l.lang("p" + i);
         if (!GuideUi.has(key)) {
            break;
         }
         this.text(GuideUi.tr(key, proneKey()), FrontierUi.Size.BODY, GuideUi.INK, 7); // [integ3] shot.p4 names the prone key
         if (l == Lesson.SIGN && i == 2) {
            this.art(GuideArt.SIGN, w);
         }
      }
      if (l == Lesson.TIPS) {
         for (int i = 0; i < TIP_SECTIONS.length; i++) {
            this.tipRow(i, w);
         }
      }
      this.fieldBox(l, w);
      this.practiceBlock(l, w);
      this.contentH = 0;
      for (Block b : this.blocks) {
         this.contentH += b.height;
      }
      this.maxScroll = Math.max(0, this.contentH - (this.viewBottom - this.viewTop) + 4);
      this.scroll = resetScroll ? 0 : Mth.clamp(this.scroll, 0, this.maxScroll);
      if (l == Lesson.TIPS && !this.tipsReported && GuideClient.courseRunning() && !l.done(done())) {
         this.tipsReported = true;
         GuideClient.send(GuideNetwork.A_READ_TIPS, 0);
      }
      this.updateButtons();
   }

   private void art(GuideArt art, int w) {
      int maxH = Math.max(90, (int)((this.viewBottom - this.viewTop) * 0.62F));
      int h = w * art.height / art.width;
      int aw = w;
      if (h > maxH) {
         h = maxH;
         aw = h * art.width / art.height;
      }
      int fw = aw, fh = h;
      this.blocks.add(new Block(fh + 10, (g, x, y, bw) -> {
         int ax = x + (bw - fw) / 2;
         FrontierUi.rect(g, ax - 3, y - 2, fw + 6, fh + 4, 3.0F, 0x22B99859);
         art.draw(g, ax, y, fw, fh, 1.0F);
      }));
   }

   private void text(String s, FrontierUi.Size size, int color, int gap) {
      List<FormattedCharSequence> lines = GuideUi.wrap(s, this.bodyW, size);
      int lh = FrontierUi.lineHeight(size) + 2;
      this.blocks.add(new Block(lines.size() * lh + gap, (g, x, y, w) -> {
         int yy = y;
         for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, x, yy, color, false);
            yy += lh;
         }
      }));
   }

   private void tipRow(int i, int w) {
      String sec = TIP_SECTIONS[i];
      int tw = Math.min(150, (int)(w * 0.38F));
      int th = tw / 2;
      int textW = w - tw - 12;
      String title = GuideUi.tr("guide.frontierhunts.lesson.tips." + sec + ".title");
      List<FormattedCharSequence> body = GuideUi.wrap(GuideUi.tr("guide.frontierhunts.lesson.tips." + sec + ".body", proneKey()), textW, FrontierUi.Size.SMALL);
      int textH = 13 + body.size() * 9;
      int h = Math.max(th, textH) + 10;
      this.blocks.add(new Block(h, (g, x, y, bw) -> {
         FrontierUi.rect(g, x - 2, y - 2, tw + 4, th + 4, 3.0F, 0x22B99859);
         GuideArt.TIPS.blit(g, x, y, tw, th, i % 2 * (GuideArt.TIPS.width / 2), i / 2 * (GuideArt.TIPS.height / 2), GuideArt.TIPS.width / 2, GuideArt.TIPS.height / 2, 1.0F); // [fieldbook]
         int tx = x + tw + 12;
         FrontierUi.text(g, title, tx, y + 1, GuideUi.INK_BROWN, FrontierUi.Size.STRONG);
         int yy = y + 14;
         for (FormattedCharSequence line : body) {
            g.drawString(this.font, line, tx, yy, GuideUi.INK, false);
            yy += 9;
         }
      }));
   }

   private void fieldBox(Lesson l, int w) {
      GuideNetwork.State s = GuideClient.state;
      String objective = GuideUi.tr(l.lang("objective"));
      List<FormattedCharSequence> lines = GuideUi.wrap(objective, w - 22, FrontierUi.Size.BODY);
      String status;
      int statusColor;
      boolean tick = false;
      if (s == null) {
         status = GuideUi.tr("guide.frontierhunts.screen.waiting");
         statusColor = GuideUi.MUTED;
      } else if (!s.has(GuideNetwork.F_ENABLED)) {
         status = GuideUi.tr("guide.frontierhunts.screen.disabled");
         statusColor = GuideUi.MUTED;
      } else if (l.done(s.done())) {
         status = GuideUi.tr("guide.frontierhunts.screen.done");
         statusColor = GuideUi.GREEN;
         tick = true;
      } else if (s.has(GuideNetwork.F_SKIPPED)) {
         status = GuideUi.tr("guide.frontierhunts.screen.paused");
         statusColor = GuideUi.MUTED;
      } else if (Lesson.current(s.done()) == l) {
         status = l.goal > 1
            ? GuideUi.tr("guide.frontierhunts.screen.progress", Math.min(s.progress(), l.goal), l.goal)
            : GuideUi.tr("guide.frontierhunts.screen.current");
         statusColor = GuideUi.GOLD_DARK;
      } else {
         status = GuideUi.tr("guide.frontierhunts.screen.any_order");
         statusColor = GuideUi.MUTED;
      }
      List<FormattedCharSequence> statusLines = GuideUi.wrap(status, w - 34, FrontierUi.Size.SMALL);
      int h = 8 + 11 + lines.size() * 12 + 4 + statusLines.size() * 9 + 8;
      boolean done = tick;
      this.blocks.add(new Block(h + 6, (g, x, y, bw) -> {
         FrontierUi.rect(g, x, y, bw, h, 4.0F, 0x2EB99859);
         FrontierUi.rect(g, x, y, 3, h, 1.5F, GuideUi.GOLD);
         FrontierUi.text(g, GuideUi.tr("guide.frontierhunts.screen.in_the_field"), x + 11, y + 7, GuideUi.GOLD_DARK, FrontierUi.Size.SMALL);
         int yy = y + 19;
         for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, x + 11, yy, GuideUi.INK, false);
            yy += 12;
         }
         yy += 3;
         int sx = x + 11;
         if (done) {
            GuideUi.tick(g, sx + 4, yy + 3.5F, GuideUi.GREEN);
            sx += 12;
         }
         for (FormattedCharSequence line : statusLines) {
            g.drawString(this.font, line, sx, yy, statusColor, false);
            yy += 9;
         }
      }));
   }

   /** [onboard] The lesson's Ranger Academy course and its place in the Frontier Handbook. */
   private void practiceBlock(Lesson l, int w) {
      com.formaworks.frontierhunts.academy.Course c = com.formaworks.frontierhunts.onboard.Handbook.courseFor(l);
      com.formaworks.frontierhunts.onboard.Handbook.Task t = com.formaworks.frontierhunts.onboard.Handbook.forLesson(l);
      String head = c == null ? GuideUi.tr("onboard.frontierhunts.school.handbook_only", t == null ? 4 : t.step)
         : GuideUi.tr("onboard.frontierhunts.school.practice", GuideUi.tr(c.lang("title")));
      String sub = c == null ? "" : GuideUi.tr(HandbookClient.passed(c) ? "onboard.frontierhunts.school.passed" : "onboard.frontierhunts.school.credit");
      List<FormattedCharSequence> subLines = GuideUi.wrap(sub, w - 22, FrontierUi.Size.SMALL);
      int h = PRACTICE_H + Math.max(0, subLines.size() - 1) * 9;
      if (c != null) {
         this.practiceBtn.setMessage(Component.translatable("onboard.frontierhunts.btn.practice", Component.translatable(c.lang("title"))));
      }
      this.blocks.add(new Block(h + 6, (g, x, y, bw) -> {
         this.practiceY = y + h - 23;
         FrontierUi.rect(g, x, y, bw, h, 4.0F, 0x1E2D4237);
         FrontierUi.rect(g, x, y, 3, h, 1.5F, GuideUi.GREEN);
         FrontierUi.text(g, GuideUi.tr("onboard.frontierhunts.school.eyebrow"), x + 11, y + 6, GuideUi.GREEN, FrontierUi.Size.SMALL);
         FrontierUi.text(g, FrontierUi.fit(head, bw - 22, FrontierUi.Size.STRONG), x + 11, y + 16, GuideUi.INK_BROWN, FrontierUi.Size.STRONG);
         int yy = y + 28;
         for (FormattedCharSequence line : subLines) {
            g.drawString(this.font, line, x + 11, yy, GuideUi.MUTED, false);
            yy += 9;
         }
      }));
   }

   private void openHandbook() {
      if (this.parent instanceof HandbookScreen) {
         this.onClose();
      } else {
         Minecraft.getInstance().setScreen(new HandbookScreen(this, 0));
      }
   }

   /** [onboard] Puts the two practice-block buttons over the block while it is fully in view. */
   private void placePractice() {
      Lesson l = Lesson.byId(this.page);
      boolean hasCourse = l != null && com.formaworks.frontierhunts.onboard.Handbook.courseFor(l) != null;
      boolean inView = this.practiceY != Integer.MIN_VALUE && this.practiceY >= this.viewTop && this.practiceY + 18 <= this.viewBottom;
      this.practiceBtn.visible = inView && hasCourse;
      this.handbookBtn.visible = inView;
      this.practiceBtn.setY(this.practiceY);
      this.handbookBtn.setY(this.practiceY);
      this.practiceBtn.setX(this.bodyX + 11);
      this.handbookBtn.setX(hasCourse ? this.bodyX + 11 + this.practiceBtn.getWidth() + 6 : this.bodyX + 11);
   }

   // ============================================================================================ rendering

   @Override
   public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
   }

   @Override
   public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
      g.fill(0, 0, this.width, this.height, 0xB0101716);
      GuideUi.notebook(g, this.left, this.top, this.panelW, this.panelH, this.side);
      // sidebar brand
      g.fill(this.left + 9, this.top + 11, this.left + 12, this.top + 37, GuideUi.GOLD);
      FrontierUi.text(g, GuideUi.tr("guide.frontierhunts.screen.brand_top"), this.left + 17, this.top + 12, 0xFFEFE8D7, FrontierUi.Size.STRONG);
      FrontierUi.text(g, GuideUi.tr("guide.frontierhunts.screen.brand_bottom"), this.left + 17, this.top + 25, 0xFFD8BD88, FrontierUi.Size.SMALL);
      GuideNetwork.State s = GuideClient.state;
      int doneCount = s == null ? 0 : Lesson.requiredDone(s.done());
      String course = GuideUi.tr("guide.frontierhunts.screen.course", doneCount, 7);
      FrontierUi.text(g, FrontierUi.fit(course, this.side - 16, FrontierUi.Size.SMALL), this.left + 9, this.top + this.panelH - 40, GuideUi.SIDEBAR_MUTED, FrontierUi.Size.SMALL);
      // progress rule under the count
      float frac = doneCount / 7.0F;
      FrontierUi.rect(g, this.left + 9, this.top + this.panelH - 31, this.side - 18, 2, 1.0F, 0x40EFE8D7);
      FrontierUi.rect(g, this.left + 9, this.top + this.panelH - 31, (this.side - 18) * frac, 2, 1.0F, GuideUi.GOLD);

      // header
      Lesson l = Lesson.byId(this.page);
      String eyebrow = l.optional()
         ? GuideUi.tr("guide.frontierhunts.screen.eyebrow_optional", this.page + 1)
         : GuideUi.tr("guide.frontierhunts.screen.eyebrow", this.page + 1, 7);
      FrontierUi.text(g, eyebrow, this.bodyX, this.top + 13, GuideUi.GOLD_DARK, FrontierUi.Size.SMALL);
      int st = status(l);
      String pill = s != null && s.has(GuideNetwork.F_SKIPPED) && st != 2 ? GuideUi.tr("guide.frontierhunts.screen.pill_paused")
         : st == 2 ? GuideUi.tr("guide.frontierhunts.screen.pill_done")
         : st == 1 ? GuideUi.tr("guide.frontierhunts.screen.pill_current")
         : l.optional() ? GuideUi.tr("guide.frontierhunts.screen.pill_optional") : "";
      int pillW = 0;
      if (!pill.isEmpty()) {
         int bg = st == 2 ? GuideUi.GREEN : (st == 1 ? GuideUi.GOLD_DARK : 0xFF7B7F70);
         pillW = GuideUi.pill(g, pill, this.left + this.panelW - 16, this.top + 12, bg, 0xFFF4EEDD);
      }
      FrontierUi.text(g, FrontierUi.fit(GuideUi.tr(l.lang("title")), this.bodyW - pillW - 6, FrontierUi.Size.TITLE), this.bodyX, this.top + 25, GuideUi.INK_BROWN, FrontierUi.Size.TITLE);
      g.fill(this.bodyX, this.top + 45, this.left + this.panelW - 16, this.top + 46, GuideUi.RULE);

      // body
      g.enableScissor(this.bodyX - 4, this.viewTop, this.left + this.panelW - 10, this.viewBottom);
      this.practiceY = Integer.MIN_VALUE;
      int y = this.viewTop + 4 - this.scroll;
      for (Block b : this.blocks) {
         if (y + b.height >= this.viewTop - 4 && y < this.viewBottom + 4) {
            b.draw.draw(g, this.bodyX, y, this.bodyW);
         }
         y += b.height;
      }
      g.disableScissor();
      this.placePractice();
      if (this.maxScroll > 0) {
         int track = this.viewBottom - this.viewTop;
         int thumb = Math.max(16, track * track / (track + this.maxScroll));
         int ty = this.viewTop + (track - thumb) * this.scroll / this.maxScroll;
         g.fill(this.left + this.panelW - 11, this.viewTop, this.left + this.panelW - 9, this.viewBottom, 0x40655F50);
         g.fill(this.left + this.panelW - 11, ty, this.left + this.panelW - 9, ty + thumb, GuideUi.GOLD);
         // soft fades at the clipped edges
         if (this.scroll > 0) {
            g.fillGradient(this.bodyX - 4, this.viewTop, this.left + this.panelW - 12, this.viewTop + 8, 0xFFEAE3D0, 0x00EAE3D0);
         }
         if (this.scroll < this.maxScroll) {
            g.fillGradient(this.bodyX - 4, this.viewBottom - 8, this.left + this.panelW - 12, this.viewBottom, 0x00EAE3D0, 0xFFEAE3D0);
         }
      }
      String footer = GuideUi.tr(this.maxScroll > 0 ? "guide.frontierhunts.screen.footer_scroll" : "guide.frontierhunts.screen.footer", GuideClient.keyName());
      if (!this.skipLesson.visible) {
         FrontierUi.text(g, FrontierUi.fit(footer, this.prev.getX() - this.bodyX - 8, FrontierUi.Size.SMALL), this.bodyX, this.top + this.panelH - 20, GuideUi.MUTED, FrontierUi.Size.SMALL);
      }
      super.render(g, mouseX, mouseY, pt);
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      if (this.maxScroll > 0) {
         this.scroll = Mth.clamp(this.scroll - (int)(sy * 24.0), 0, this.maxScroll);
         return true;
      }
      return super.mouseScrolled(mx, my, sx, sy);
   }

   @Override
   public boolean keyPressed(int key, int scan, int mods) {
      if (GuideClient.KEY.matches(key, scan)) {
         this.onClose();
         return true;
      }
      if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP && this.maxScroll == 0) {
         this.go(this.page - 1);
         return true;
      }
      if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN && this.maxScroll == 0) {
         this.go(this.page + 1);
         return true;
      }
      if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_PAGE_DOWN) {
         this.scroll = Mth.clamp(this.scroll + (key == GLFW.GLFW_KEY_DOWN ? 24 : this.viewBottom - this.viewTop - 20), 0, this.maxScroll);
         return true;
      }
      if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_PAGE_UP) {
         this.scroll = Mth.clamp(this.scroll - (key == GLFW.GLFW_KEY_UP ? 24 : this.viewBottom - this.viewTop - 20), 0, this.maxScroll);
         return true;
      }
      return super.keyPressed(key, scan, mods);
   }

   @Override
   public void onClose() {
      Minecraft.getInstance().setScreen(this.parent);
   }

   // ============================================================================================ widgets

   /** Journal-style button (dark green slab, gold edge when focused). */
   static class GuideButton extends Button {
      private final boolean dark;

      GuideButton(int x, int y, int w, int h, Component msg, OnPress press, boolean dark) {
         super(x, y, w, h, msg, press, DEFAULT_NARRATION);
         this.dark = dark;
      }

      @Override
      protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
         int bg = !this.active ? GuideUi.BUTTON_OFF : (this.isHoveredOrFocused() ? GuideUi.BUTTON_HOVER : GuideUi.BUTTON);
         FrontierUi.rect(g, this.getX(), this.getY(), this.width, this.height, 2.0F, bg);
         if (this.isFocused()) {
            g.fill(this.getX(), this.getY(), this.getX() + 2, this.getY() + this.height, GuideUi.GOLD);
         }
         String s = this.getMessage().getString();
         int c = this.active ? 0xFFEFE8D7 : 0xFFC5C9B7;
         FrontierUi.center(g, FrontierUi.fit(s, this.width - 8, FrontierUi.Size.SMALL), this.getX() + this.width / 2.0F, this.getY() + (this.height - 8) / 2.0F, c, FrontierUi.Size.SMALL);
      }
   }

   /** A lesson in the sidebar: numbered badge (tick when done), title, highlight when open. */
   private final class LessonRow extends Button {
      private final int index;

      LessonRow(int x, int y, int w, int h, int index, OnPress press) {
         super(x, y, w, h, Component.translatable(Lesson.byId(index).lang("title")), press, DEFAULT_NARRATION);
         this.index = index;
      }

      @Override
      protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
         Lesson l = Lesson.byId(this.index);
         boolean selected = FieldSchoolScreen.this.page == this.index;
         if (selected || this.isHoveredOrFocused()) {
            FrontierUi.rect(g, this.getX(), this.getY(), this.width, this.height, 2.0F, selected ? 0xFF5E4636 : 0xFF54402F);
         }
         if (selected) {
            g.fill(this.getX(), this.getY(), this.getX() + 2, this.getY() + this.height, GuideUi.GOLD);
         }
         float cy = this.getY() + this.height / 2.0F;
         GuideUi.badge(g, this.getX() + 11, cy, this.index + 1, status(l), true);
         int color = status(l) == 2 ? 0xFFD8CDB0 : (selected ? 0xFFF4EEDD : 0xFFE3D9C3);
         String t = GuideUi.tr(l.lang(this.width < 110 ? "short" : "title"));
         FrontierUi.text(g, FrontierUi.fit(t, this.width - 26, FrontierUi.Size.SMALL), this.getX() + 21, cy - 4, color, FrontierUi.Size.SMALL);
      }
   }
}
