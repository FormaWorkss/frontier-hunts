package com.formaworks.frontierhunts.guide.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.firsthunt.FirstHunt;
import com.formaworks.frontierhunts.firsthunt.FirstHuntNetwork;
import com.formaworks.frontierhunts.licence.Regulations;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * [1.2.7] The first-hunt page: the one objective, plain. [1.2.9] Redrawn as a proper Handbook page: a dark header with
 * the seven steps as a labelled trail, the step you're on as a highlighted card, then two cards side by side (your
 * paperwork, and where to look with a compass needle that turns with you), and paper buttons instead of Minecraft's
 * grey ones. The rules page lists the four things to know as numbered cards. There is no free permit any more: the
 * licence comes from finishing the whole Ranger Academy and a deer tag is bought at a ranger counter.
 */
public final class FirstHuntScreen extends Screen {
   private static final int STEPS = 7;
   private static final int BAND = 0xFF263A30, BAND_DARK = 0xFF1C2B23, BAND_TEXT = 0xFFF3ECDB, BAND_MUTED = 0xFFA9B4A2, BAND_GOLD = 0xFFD8BD88;
   private static final int CARD = 0xFFF3EEDF, CARD_EDGE = 0xFFD3C8AC;
   private final Screen parent;
   private final boolean intro;
   private int left, top, w, h;
   /** [1.2.8] the rules page: licence, tag and knife, spelled out */
   private boolean rules;
   private long openedAt;

   FirstHuntScreen(Screen parent, boolean intro) {
      super(Component.translatable("firsthunt.frontierhunts.screen.title"));
      this.parent = parent;
      this.intro = intro;
   }

   void stateChanged() {
      this.rebuildWidgets();
   }

   private static FirstHuntNetwork.State st() {
      return FirstHuntClient.state;
   }

   private static boolean started(FirstHuntNetwork.State s) {
      return s != null && s.has(FirstHuntNetwork.F_STARTED);
   }

   // ============================================================================================ layout

   private int bandH() {
      return this.rules ? 46 : 82;
   }

   private int footY() {
      return this.top + this.h - 30;
   }

   /** the two cards under the step card: top, height, and the split */
   private int cardsTop() {
      return this.top + this.bandH() + 86;
   }

   private int colW() {
      return (this.w - 28 - 10) / 2;
   }

   @Override
   protected void init() {
      if (this.openedAt == 0L) {
         this.openedAt = System.currentTimeMillis();
      }
      this.w = Math.min(470, this.width - 16);
      this.h = Math.min(330, this.height - 16);
      this.left = (this.width - this.w) / 2;
      this.top = (this.height - this.h) / 2;
      FirstHuntNetwork.State s = st();
      int by = this.footY() + 6;
      int bx = this.left + 14, right = this.left + this.w - 14;
      Component done = Component.translatable("gui.done");
      int doneW = PaperButton.widthFor(done, false, 64);
      if (this.rules) {
         Component back = Component.translatable("firsthunt.frontierhunts.button.back");
         this.addRenderableWidget(new PaperButton(bx, by, PaperButton.widthFor(back, false, 70), 20, back, b -> {
            this.rules = false;
            this.rebuildWidgets();
         }, PaperButton.Look.OUTLINE, false));
         this.addRenderableWidget(new PaperButton(right - doneW, by, doneW, 20, done, b -> this.onClose(), PaperButton.Look.GREEN, false));
         return;
      }
      Component rulesLabel = Component.translatable("firsthunt.frontierhunts.button.rules");
      int rw = PaperButton.widthFor(rulesLabel, false, 90);
      this.addRenderableWidget(new PaperButton(bx, by, rw, 20, rulesLabel, b -> {
         this.rules = true;
         this.rebuildWidgets();
      }, PaperButton.Look.OUTLINE, false));
      this.addRenderableWidget(new PaperButton(right - doneW, by, doneW, 20, done, b -> this.onClose(), PaperButton.Look.GREEN, false));
      if (!started(s)) {
         // one big way in
         Component start = Component.translatable("firsthunt.frontierhunts.button.start");
         int sw = PaperButton.widthFor(start, true, 190);
         this.addRenderableWidget(new PaperButton(this.left + (this.w - sw) / 2, this.top + this.bandH() + 120, sw, 26, start,
            b -> FirstHuntClient.send(FirstHuntNetwork.A_START), PaperButton.Look.GOLD, true));
         return;
      }
      if (s.has(FirstHuntNetwork.F_CLAIMED)) {
         return;
      }
      boolean hidden = s.has(FirstHuntNetwork.F_HIDDEN);
      Component hide = Component.translatable(hidden ? "firsthunt.frontierhunts.button.show" : "firsthunt.frontierhunts.button.hide");
      this.addRenderableWidget(new PaperButton(bx + rw + 6, by, PaperButton.widthFor(hide, false, 80), 20, hide,
         b -> FirstHuntClient.send(hidden ? FirstHuntNetwork.A_SHOW : FirstHuntNetwork.A_HIDE), PaperButton.Look.OUTLINE, false));
      Component claim = Component.translatable("firsthunt.frontierhunts.button.claim", FirstHunt.REWARD_TOKENS);
      int cw = PaperButton.widthFor(claim, true, 110);
      PaperButton claimB = new PaperButton(right - doneW - 6 - cw, by, cw, 20, claim, b -> FirstHuntClient.send(FirstHuntNetwork.A_CLAIM),
         PaperButton.Look.GOLD, true);
      claimB.active = s.current() == FirstHunt.Step.CLAIM;
      this.addRenderableWidget(claimB);
      if (s.current().ordinal() <= FirstHunt.Step.SHOT.ordinal() && s.has(FirstHuntNetwork.F_AREA)) {
         Component area = Component.translatable("firsthunt.frontierhunts.button.area");
         int aw = Math.min(this.colW() - 20, PaperButton.widthFor(area, false, 100));
         int ax = this.left + 14 + this.colW() + 10 + this.colW() - 10 - aw;
         this.addRenderableWidget(new PaperButton(ax, this.cardsTop() + this.cardsH() - 24, aw, 16, area,
            b -> FirstHuntClient.send(FirstHuntNetwork.A_NEW_AREA), PaperButton.Look.OUTLINE, false));
      }
   }

   private int cardsH() {
      return this.footY() - 8 - this.cardsTop();
   }

   // ============================================================================================ drawing

   /** the page: shadow, leather edge, paper, the dark header band and the footer rule */
   private void panel(GuiGraphics g) {
      int x = this.left, y = this.top, w = this.w, h = this.h;
      FrontierUi.shadow(g, x, y, w, h, 6.0F, 7.0F);
      FrontierUi.batch(g, () -> {
         FrontierUi.rect(g, x - 2, y - 2, w + 4, h + 4, 7.0F, 0xFF41352A);
         FrontierUi.rect(g, x - 1, y - 1, w + 2, h + 2, 6.5F, 0xFF88704A);
         FrontierUi.rect(g, x, y, w, h, 6.0F, GuideUi.PAPER);
         // the dark header band with rounded top corners, a soft gradient and a gold rule under it
         int bh = this.bandH();
         FrontierUi.rect(g, x, y, w, bh, 6.0F, BAND);
         g.fill(x, y + bh - 8, x + w, y + bh, BAND);
         g.fillGradient(x, y + bh / 2, x + w, y + bh, 0x00000000, 0x30000000);
         g.fill(x, y + bh, x + w, y + bh + 1, GuideUi.GOLD);
         g.fill(x, y + bh + 1, x + w, y + bh + 3, 0x18000000);
         // the footer rule
         g.fill(x + 14, this.footY(), x + w - 14, this.footY() + 1, GuideUi.RULE);
      });
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float pt) {
      float t = HuntConfig.REDUCED_MOTION.get() ? 1.0F : Mth.clamp((System.currentTimeMillis() - this.openedAt) / 260.0F, 0.0F, 1.0F);
      float e = 1.0F - (1.0F - t) * (1.0F - t);
      this.renderBackground(g, mx, my, pt);
      g.pose().pushPose();
      g.pose().translate(0, (1.0F - e) * 8.0F, 0);
      this.panel(g);
      FirstHuntNetwork.State s = st();
      int x = this.left + 14, y = this.top + 11, tw = this.w - 28;
      if (this.rules) {
         FrontierUi.text(g, GuideUi.tr("firsthunt.frontierhunts.screen.eyebrow"), x, y, BAND_GOLD, FrontierUi.Size.SMALL);
         FrontierUi.text(g, GuideUi.tr("firsthunt.frontierhunts.rules.title"), x, y + 11, BAND_TEXT, FrontierUi.Size.TITLE);
         this.rulesPage(g, x, this.top + this.bandH() + 12, tw);
      } else {
         boolean fresh = this.intro || !started(s) || (s.done() & ~1) == 0 && s.current().ordinal() <= FirstHunt.Step.SIGNS.ordinal();
         FrontierUi.text(g, GuideUi.tr("firsthunt.frontierhunts.screen.eyebrow"), x, y, BAND_GOLD, FrontierUi.Size.SMALL);
         FrontierUi.text(g, FrontierUi.fit(GuideUi.tr(fresh ? "firsthunt.frontierhunts.screen.start" : "firsthunt.frontierhunts.screen.title"), tw - 150,
            FrontierUi.Size.TITLE), x, y + 11, BAND_TEXT, FrontierUi.Size.TITLE);
         this.rewardPill(g, this.left + this.w - 14, y + 2, s != null && s.has(FirstHuntNetwork.F_CLAIMED));
         this.trail(g, x, this.top + 54, tw, s);
         int body = this.top + this.bandH() + 12;
         if (!started(s)) {
            this.notStarted(g, x, body, tw);
         } else if (s.has(FirstHuntNetwork.F_CLAIMED)) {
            this.finished(g, x, body, tw);
         } else {
            this.stepCard(g, x, body, tw, s);
            int cy = this.cardsTop(), ch = this.cardsH(), cw = this.colW();
            this.paperwork(g, x, cy, cw, ch, s);
            this.whereCard(g, x + cw + 10, cy, cw, ch, s);
         }
      }
      // the buttons last, over the cards
      for (var r : this.renderables) {
         r.render(g, mx, my, pt);
      }
      g.pose().popPose();
   }

   /** the reward, as a gold pill in the header (a tick when it has been claimed) */
   private void rewardPill(GuiGraphics g, int rightX, int y, boolean claimed) {
      String text = GuideUi.tr(claimed ? "firsthunt.frontierhunts.screen.reward_done" : "firsthunt.frontierhunts.screen.reward", FirstHunt.REWARD_TOKENS);
      int pw = FrontierUi.width(text, FrontierUi.Size.SMALL) + 22;
      FrontierUi.batch(g, () -> {
         FrontierUi.rect(g, rightX - pw, y, pw, 14, 7.0F, 0x40D8BD88);
         FrontierUi.circle(g, rightX - pw + 8, y + 7, 3.2F, BAND_GOLD);
         FrontierUi.circle(g, rightX - pw + 8, y + 7, 1.6F, BAND);
      });
      FrontierUi.text(g, text, rightX - pw + 15, y + 3, BAND_GOLD, FrontierUi.Size.SMALL);
   }

   /** the seven steps as a trail across the header: numbered stops, a line between them, short names under */
   private void trail(GuiGraphics g, int x, int y, int tw, FirstHuntNetwork.State s) {
      int done = s == null ? 0 : s.done();
      int cur = started(s) && !s.has(FirstHuntNetwork.F_CLAIMED) ? s.current().ordinal() : -1;
      float cell = tw / (float)STEPS;
      long now = System.currentTimeMillis();
      FrontierUi.batch(g, () -> {
         for (int i = 0; i < STEPS - 1; i++) {
            float x0 = x + cell * (i + 0.5F) + 8, x1 = x + cell * (i + 1.5F) - 8;
            boolean d = (done & 1 << i) != 0;
            FrontierUi.rect(g, x0, y - 0.75F, x1 - x0, 1.5F, 0.75F, d ? BAND_GOLD : 0x40F3ECDB);
         }
         for (int i = 0; i < STEPS; i++) {
            float cx = x + cell * (i + 0.5F);
            boolean d = (done & 1 << i) != 0;
            if (i == cur) {
               float p = HuntConfig.REDUCED_MOTION.get() ? 0.5F : (float)(0.5 + 0.5 * Math.sin(now / 380.0));
               FrontierUi.circle(g, cx, y, 9.5F + p * 1.5F, (int)(0x30 + 0x30 * p) << 24 | 0xD8BD88);
               FrontierUi.circle(g, cx, y, 7.0F, BAND_GOLD);
               FrontierUi.circle(g, cx, y, 5.6F, BAND_DARK);
            } else if (d) {
               FrontierUi.circle(g, cx, y, 7.0F, BAND_GOLD);
            } else {
               FrontierUi.circle(g, cx, y, 7.0F, 0x50F3ECDB);
               FrontierUi.circle(g, cx, y, 6.0F, BAND);
            }
         }
      });
      for (int i = 0; i < STEPS; i++) {
         float cx = x + cell * (i + 0.5F);
         boolean d = (done & 1 << i) != 0;
         if (d && i != cur) {
            GuideUi.tick(g, cx, y, BAND_DARK);
         } else {
            FrontierUi.center(g, Integer.toString(i + 1), cx + 0.5F, y - 3.5F, i == cur ? BAND_GOLD : BAND_MUTED, FrontierUi.Size.SMALL);
         }
         String label = FrontierUi.fit(GuideUi.tr("firsthunt.frontierhunts.step." + FirstHunt.Step.values()[i].key() + ".short"), (int)cell - 4,
            FrontierUi.Size.SMALL);
         FrontierUi.center(g, label, cx, y + 11, i == cur ? BAND_TEXT : d ? BAND_GOLD : BAND_MUTED, FrontierUi.Size.SMALL);
      }
   }

   /** before the hunt is started: what it is, and one big Start button (a widget, placed in init) */
   private void notStarted(GuiGraphics g, int x, int y, int tw) {
      GuideUi.para(g, GuideUi.tr("firsthunt.frontierhunts.screen.not_started"), x + 30, y + 10, tw - 60, GuideUi.INK_BROWN, FrontierUi.Size.BODY, 3);
      FrontierUi.center(g, GuideUi.tr("firsthunt.frontierhunts.screen.start_hint"), this.left + this.w / 2.0F, this.top + this.bandH() + 152, GuideUi.MUTED,
         FrontierUi.Size.SMALL);
   }

   private void finished(GuiGraphics g, int x, int y, int tw) {
      float cx = this.left + this.w / 2.0F;
      FrontierUi.batch(g, () -> {
         FrontierUi.circle(g, cx, y + 34, 18.0F, 0x30B99859);
         FrontierUi.circle(g, cx, y + 34, 13.0F, GuideUi.GOLD);
      });
      g.pose().pushPose();
      g.pose().translate(cx, y + 34, 0);
      g.pose().scale(2.0F, 2.0F, 1.0F);
      GuideUi.tick(g, 0, 0, GuideUi.PAPER);
      g.pose().popPose();
      FrontierUi.center(g, GuideUi.tr("firsthunt.frontierhunts.step.done.title"), cx, y + 60, GuideUi.INK, FrontierUi.Size.TITLE);
      List<FormattedCharSequence> lines = GuideUi.wrap(GuideUi.tr("firsthunt.frontierhunts.screen.done", FirstHunt.REWARD_TOKENS), tw - 80,
         FrontierUi.Size.BODY);
      int ly = y + 82;
      for (FormattedCharSequence l : lines) {
         g.drawString(GuideUi.font(), l, (int)(cx - GuideUi.font().width(l) / 2.0F), ly, GuideUi.INK_BROWN, false);
         ly += 12;
      }
   }

   /** the step the hunter is on, as the page's one highlighted card */
   private void stepCard(GuiGraphics g, int x, int y, int tw, FirstHuntNetwork.State s) {
      FirstHunt.Step step = s.current();
      int ch = 64;
      FrontierUi.batch(g, () -> {
         FrontierUi.rect(g, x, y, tw, ch, 5.0F, 0xFFDDCB9E);
         FrontierUi.rect(g, x + 1, y + 1, tw - 2, ch - 2, 4.0F, 0xFFF1E6C8);
         FrontierUi.rect(g, x + 1, y + 1, 3.5F, ch - 2, 1.5F, GuideUi.GOLD);
      });
      // the step's icon in a gold disc
      FrontierUi.batch(g, () -> {
         FrontierUi.circle(g, x + 24, y + 24, 14.0F, 0xFF2D4237);
         FrontierUi.circle(g, x + 24, y + 24, 12.5F, 0xFF3E5945);
      });
      GuideIcons.draw(g, "item:" + FirstHuntClient.icon(step), x + 15, y + 15, 18, 1.0F);
      int tx = x + 46, textW = tw - 46 - 12;
      FrontierUi.text(g, GuideUi.tr("firsthunt.frontierhunts.screen.step_eyebrow", Math.min(step.ordinal() + 1, STEPS), STEPS), tx, y + 8, GuideUi.GOLD_DARK,
         FrontierUi.Size.SMALL);
      FrontierUi.text(g, FrontierUi.fit(FirstHuntClient.title(step), textW, FrontierUi.Size.STRONG), tx, y + 18, GuideUi.INK, FrontierUi.Size.STRONG);
      g.enableScissor(tx, y + 30, tx + textW, y + ch - 4);
      GuideUi.para(g, GuideUi.tr("firsthunt.frontierhunts.step." + step.key() + ".how", FirstHunt.REWARD_TOKENS, FirstHuntClient.academyCourses(),
         AcademyKey.name()), tx, y + 31, textW, GuideUi.INK_BROWN, FrontierUi.Size.SMALL, 1);
      g.disableScissor();
   }

   /** a card with a small caps title and a rule */
   private static void card(GuiGraphics g, int x, int y, int w, int h, String title) {
      FrontierUi.batch(g, () -> {
         FrontierUi.rect(g, x, y, w, h, 5.0F, CARD_EDGE);
         FrontierUi.rect(g, x + 1, y + 1, w - 2, h - 2, 4.0F, CARD);
      });
      FrontierUi.text(g, title.toUpperCase(java.util.Locale.ROOT), x + 10, y + 8, GuideUi.GOLD_DARK, FrontierUi.Size.SMALL);
      g.fill(x + 10, y + 18, x + w - 10, y + 19, GuideUi.RULE);
   }

   /** licence, tag and season with a tick or a cross, then what to do about whatever is missing */
   private void paperwork(GuiGraphics g, int x, int y, int w, int h, FirstHuntNetwork.State s) {
      card(g, x, y, w, h, GuideUi.tr("firsthunt.frontierhunts.screen.licence"));
      int ry = y + 25, iw = w - 20;
      if (!s.has(FirstHuntNetwork.F_REGULATED)) {
         GuideUi.para(g, GuideUi.tr("firsthunt.frontierhunts.licence.off"), x + 10, ry, iw, GuideUi.MUTED, FrontierUi.Size.SMALL, 1);
         return;
      }
      boolean lic = s.has(FirstHuntNetwork.F_LICENCE) && !s.has(FirstHuntNetwork.F_SUSPENDED);
      ry = row(g, x + 10, ry, iw, lic, GuideUi.tr("firsthunt.frontierhunts.licence.licence"),
         GuideUi.tr(s.has(FirstHuntNetwork.F_SUSPENDED) ? "firsthunt.frontierhunts.licence.suspended"
            : s.has(FirstHuntNetwork.F_LICENCE) ? "firsthunt.frontierhunts.licence.valid" : "firsthunt.frontierhunts.licence.academy_short",
            s.academyDone(), FirstHuntClient.academyCourses()));
      ry = row(g, x + 10, ry, iw, s.has(FirstHuntNetwork.F_TAG), GuideUi.tr("firsthunt.frontierhunts.licence.tag"),
         GuideUi.tr(s.has(FirstHuntNetwork.F_TAG) ? "firsthunt.frontierhunts.licence.tag_ok" : "firsthunt.frontierhunts.licence.tag_missing"));
      ry = row(g, x + 10, ry, iw, s.has(FirstHuntNetwork.F_OPEN), GuideUi.tr("firsthunt.frontierhunts.licence.season"),
         s.has(FirstHuntNetwork.F_OPEN) ? GuideUi.tr("firsthunt.frontierhunts.licence.open_short")
            : GuideUi.tr("firsthunt.frontierhunts.licence.closed_short", s.daysToOpen()));
      // what to do next, if anything is missing
      String todo = null;
      if (s.has(FirstHuntNetwork.F_SUSPENDED)) {
         todo = GuideUi.tr("firsthunt.frontierhunts.permit.line.suspended");
      } else if (!s.has(FirstHuntNetwork.F_LICENCE)) {
         todo = GuideUi.tr("firsthunt.frontierhunts.licence.route_academy", AcademyKey.name(), s.academyDone(), FirstHuntClient.academyCourses());
      } else if (!s.has(FirstHuntNetwork.F_TAG)) {
         Minecraft mc = Minecraft.getInstance();
         todo = s.has(FirstHuntNetwork.F_CAMP) && mc.player != null
            ? GuideUi.tr("firsthunt.frontierhunts.licence.route_tag_camp", s.tagTokens(), s.campX(), s.campZ(),
               (int)Math.hypot(s.campX() - mc.player.getX(), s.campZ() - mc.player.getZ()))
            : GuideUi.tr("firsthunt.frontierhunts.licence.route_tag", s.tagTokens());
      } else if (!s.has(FirstHuntNetwork.F_OPEN)) {
         todo = GuideUi.tr("firsthunt.frontierhunts.licence.season_note", Regulations.months(s.months()));
      }
      if (todo != null) {
         ry += 3;
         int left = y + h - 6 - ry;
         List<FormattedCharSequence> lines = GuideUi.wrap(todo, iw - 8, FrontierUi.Size.SMALL);
         int n = Math.max(1, Math.min(lines.size(), left / 9));
         int bh = n * 9 + 6;
         int fry = ry;
         FrontierUi.rect(g, x + 10, fry, iw, bh, 3.0F, 0x1EB99859);
         int ly = ry + 3;
         for (int i = 0; i < n; i++) {
            g.drawString(GuideUi.font(), lines.get(i), x + 15, ly, GuideUi.INK_BROWN, false);
            ly += 9;
         }
      }
   }

   /** a paperwork line: a green tick or a red cross in a disc, the item, its state on the right */
   private static int row(GuiGraphics g, int x, int y, int w, boolean ok, String what, String state) {
      float cx = x + 6, cy = y + 6;
      FrontierUi.batch(g, () -> {
         FrontierUi.circle(g, cx, cy, 6.0F, ok ? 0x2A3E6E37 : 0x22A5281F);
      });
      if (ok) {
         GuideUi.tick(g, cx + 0.3F, cy, GuideUi.GREEN);
      } else {
         GuideUi.line(g, cx - 2.4F, cy - 2.4F, cx + 2.4F, cy + 2.4F, 1.3F, GuideUi.RED);
         GuideUi.line(g, cx + 2.4F, cy - 2.4F, cx - 2.4F, cy + 2.4F, 1.3F, GuideUi.RED);
      }
      FrontierUi.text(g, what, x + 17, y + 2, GuideUi.INK, FrontierUi.Size.SMALL);
      int sw = w - 17 - FrontierUi.width(what, FrontierUi.Size.SMALL) - 8;
      FrontierUi.right(g, FrontierUi.fit(state, sw, FrontierUi.Size.SMALL), x + w, y + 2, ok ? GuideUi.MUTED : GuideUi.RED, FrontierUi.Size.SMALL);
      return y + 15;
   }

   /** where to look: a compass needle toward the beginner area (turns with the hunter), the distance, the advice */
   private void whereCard(GuiGraphics g, int x, int y, int w, int h, FirstHuntNetwork.State s) {
      FirstHunt.Step step = s.current();
      boolean looking = step.ordinal() <= FirstHunt.Step.SHOT.ordinal();
      card(g, x, y, w, h, GuideUi.tr(looking ? "firsthunt.frontierhunts.screen.area" : "firsthunt.frontierhunts.screen.now"));
      int ty = y + 25;
      if (!looking) {
         GuideUi.para(g, FirstHuntClient.body(s), x + 10, ty, w - 20, GuideUi.INK_BROWN, FrontierUi.Size.SMALL, 1);
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      boolean area = s.has(FirstHuntNetwork.F_AREA) && !s.has(FirstHuntNetwork.F_AREA_QUIET) && mc.player != null;
      int textX = x + 10, textW = w - 20;
      if (area) {
         double dx = s.areaX() - mc.player.getX(), dz = s.areaZ() - mc.player.getZ();
         boolean inside = Math.hypot(dx, dz) <= s.areaR();
         float ccx = x + 26, ccy = ty + 17;
         compass(g, ccx, ccy, 15.0F, inside ? Float.NaN : (float)(Math.atan2(-dx, dz) - Math.toRadians(mc.player.getYRot())));
         textX = x + 48;
         textW = w - 58;
      }
      int used = GuideUi.para(g, FirstHuntClient.where(s), textX, ty + 1, textW, GuideUi.INK, FrontierUi.Size.STRONG, 1);
      if (area) {
         FrontierUi.text(g, GuideUi.tr("firsthunt.frontierhunts.screen.compass"), textX, ty + 2 + used, GuideUi.MUTED, FrontierUi.Size.SMALL);
         used = Math.max(used + 10, 36);
      }
      if (s.has(FirstHuntNetwork.F_AREA) && !s.has(FirstHuntNetwork.F_AREA_QUIET)) {
         int left = y + h - 28 - (ty + used + 4);
         List<FormattedCharSequence> lines = GuideUi.wrap(GuideUi.tr("firsthunt.frontierhunts.advice." + Mth.clamp(s.advice(), 0, 4)), w - 20,
            FrontierUi.Size.SMALL);
         int ly = ty + used + 4;
         for (int i = 0; i < lines.size() && (i + 1) * 9 <= left; i++) {
            g.drawString(GuideUi.font(), lines.get(i), x + 10, ly, GuideUi.INK_BROWN, false);
            ly += 9;
         }
      }
   }

   /** a small compass: ring, ticks, and a gold needle pointing at {@code angle} (0 = straight ahead); NaN = you're there */
   private static void compass(GuiGraphics g, float cx, float cy, float r, float angle) {
      FrontierUi.batch(g, () -> {
         FrontierUi.circle(g, cx, cy, r, 0xFF2D4237);
         FrontierUi.circle(g, cx, cy, r - 1.5F, 0xFFEDE3C9);
         for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            float l = i % 2 == 0 ? 2.6F : 1.6F;
            float ox = (float)Math.sin(a), oy = (float)-Math.cos(a);
            GuideUi.line(g, cx + ox * (r - 2.5F - l), cy + oy * (r - 2.5F - l), cx + ox * (r - 2.5F), cy + oy * (r - 2.5F), 0.8F, 0xFF9C8B68);
         }
         if (Float.isNaN(angle)) {
            FrontierUi.circle(g, cx, cy, 5.0F, GuideUi.GREEN);
            return;
         }
         float sx = (float)Math.sin(angle), sy = (float)-Math.cos(angle);
         float px = -sy, py = sx; // perpendicular
         float tipX = cx + sx * (r - 3.5F), tipY = cy + sy * (r - 3.5F);
         float tailX = cx - sx * (r - 6.0F), tailY = cy - sy * (r - 6.0F);
         // the needle: a filled gold kite toward the area, a dark tail
         for (int k = -6; k <= 6; k++) {
            float f = k / 6.0F * 3.2F;
            GuideUi.line(g, tipX, tipY, cx + px * f, cy + py * f, 0.9F, 0xFFC08A2E);
            GuideUi.line(g, tailX, tailY, cx + px * f * 0.8F, cy + py * f * 0.8F, 0.9F, 0xFF5B6B60);
         }
         FrontierUi.circle(g, cx, cy, 1.6F, 0xFF2B2014);
      });
   }

   // ============================================================================================ rules page

   private void rulesPage(GuiGraphics g, int x, int y, int tw) {
      String use = Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage().getString();
      String[] keys = {"before", "get", "tag", "knife"};
      String[] icons = {"frontierhunts:hunting_licence", "frontierhunts:deer_tag", "frontierhunts:deer_tag", "frontierhunts:skinning_tool"};
      int bottom = this.footY() - 6;
      int gap = 6;
      // measure, then share the room
      int[] hs = new int[keys.length];
      int total = 0;
      for (int i = 0; i < keys.length; i++) {
         hs[i] = 18 + GuideUi.paraHeight(this.rule(keys[i], use), tw - 48, FrontierUi.Size.SMALL, 1) + 6;
         total += hs[i];
      }
      int spare = Math.max(0, (bottom - y) - total - gap * (keys.length - 1)) / keys.length;
      for (int i = 0; i < keys.length; i++) {
         int ch = hs[i] + Math.min(spare, 6);
         int fy = y;
         FrontierUi.batch(g, () -> {
            FrontierUi.rect(g, x, fy, tw, ch, 5.0F, CARD_EDGE);
            FrontierUi.rect(g, x + 1, fy + 1, tw - 2, ch - 2, 4.0F, CARD);
            FrontierUi.circle(g, x + 18, fy + 16, 11.0F, 0xFF2D4237);
         });
         GuideIcons.draw(g, "item:" + icons[i], x + 10, y + 8, 16, 1.0F);
         FrontierUi.text(g, (i + 1) + "  " + GuideUi.tr("firsthunt.frontierhunts.rules." + keys[i] + ".head"), x + 38, y + 6, GuideUi.INK,
            FrontierUi.Size.STRONG);
         GuideUi.para(g, this.rule(keys[i], use), x + 38, y + 18, tw - 48, GuideUi.INK_BROWN, FrontierUi.Size.SMALL, 1);
         y += ch + gap;
      }
   }

   private String rule(String key, String use) {
      return key.equals("get")
         ? GuideUi.tr("firsthunt.frontierhunts.rules.get", AcademyKey.name(), FirstHuntClient.academyCourses())
         : GuideUi.tr("firsthunt.frontierhunts.rules." + key, use);
   }

   /** the Ranger Academy key (the assignments board), as bound */
   static final class AcademyKey {
      static String name() {
         try {
            return com.formaworks.frontierhunts.academy.client.AcademyClient.ASSIGNMENTS.getTranslatedKeyMessage().getString();
         } catch (RuntimeException | LinkageError ex) {
            return "K";
         }
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      if (this.intro) {
         FirstHuntClient.send(FirstHuntNetwork.A_INTRO_SEEN);
      }
      Minecraft.getInstance().setScreen(this.parent);
   }
}
