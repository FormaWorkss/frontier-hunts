package com.formaworks.frontierhunts.guide.client;

import com.formaworks.frontierhunts.academy.Course;
import com.formaworks.frontierhunts.academy.client.AcademyClient;
import com.formaworks.frontierhunts.client.AssignmentScreen;
import com.formaworks.frontierhunts.client.ExpeditionClient;
import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.HuntNetwork;
import com.formaworks.frontierhunts.guide.Lesson;
import com.formaworks.frontierhunts.onboard.Handbook;
import com.formaworks.frontierhunts.onboard.OnboardNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * [onboard] The Frontier Handbook: the new hunter's starting point, in the Hunter's Journal notebook (the Field School
 * book's look). "Start here" shows the one next step and the eight steps of the first hour with ticks; each step page
 * has short plain text, the exact recipes (real item icons, read live from the recipe manager), the tasks with ticks
 * and links into the Field School lesson and the Ranger Academy course that practises it. The footer opens the Hunter's
 * Journal, the Expedition journal, the Field School and Ranger Assignments. [onebook] The Handbook is the only book item:
 * those four books live inside it (footer on every page + "Inside this Handbook" on Start here); closing one opened
 * from here comes back to this page (OneBookClient). [onboard2] The last sidebar tab, under the
 * steps, is "Assignments": the Ranger Academy courses in teaching order with progress, the field assignment taken, and
 * a button into Ranger Assignments, so new hunters see that the courses are part of the path.
 */
public final class HandbookScreen extends Screen {
   /** [onboard2] Page index of the Assignments tab (after the eight steps). */
   public static final int ASSIGNMENTS = Handbook.STEPS + 1;
   private static final net.minecraft.resources.ResourceLocation TAB_ICON =
      com.formaworks.frontierhunts.FrontierHunts.id("textures/gui/handbook/assignments_tab.png");
   private static final String[][] RECIPES = {
      {},
      // [benches] step 1 ends with the three benches; every mod item is made at one of them
      {"minecraft:oak_planks", "minecraft:stick", "minecraft:crafting_table", "frontierhunts:frontier_workbench", "frontierhunts:gunsmith_bench",
         "frontierhunts:reloading_bench"},
      {"frontierhunts:field_bow", "frontierhunts:field_arrow", "frontierhunts:primitive_arrow", "frontierhunts:shooting_target"}, // [onboard2] + target
      {"frontierhunts:field_point", "frontierhunts:fixed_broadhead", "frontierhunts:arrows/hunting_arrow_field_point", "fit:field_point"}, // [benches] the Reloading Bench makes and fits tips; [smalls] + arrows made with a head
      {"frontierhunts:wind_checker", "frontierhunts:expedition_binoculars"},
      {}, // [1.1.8] the hound lead is bought at the outfitter's counter now, it has no recipe
      {"frontierhunts:skinning_tool", "minecraft:campfire", "frontierhunts:campfire_venison", "frontierhunts:dutch_oven",
         "frontierhunts:campcook/venison_stew"}, // [licence] the Dutch oven and a camp meal
      {"frontierhunts:tanning_rack", "frontierhunts:clothing_table/buckskin_coat", "frontierhunts:reserve_canvas_wall",
         "frontierhunts:pup_tent"}, // [benches] furs are made at the Frontier Workbench's Clothing tab
      {"frontierhunts:ridgeline_rifle", "frontierhunts:reserve_308"} // [benches] the rifle at the Gunsmith's Bench, its cartridges at the Reloading Bench
   };

   private final Screen parent;
   private int page;
   private int left, top, panelW, panelH, side, bodyX, bodyW, viewTop, viewBottom, rowH, rowsTop;
   private int scroll, maxScroll;
   private final List<Block> blocks = new ArrayList<>();
   private final List<Hot> hots = new ArrayList<>();
   private ItemStack hoverStack = ItemStack.EMPTY;
   private int armedTask = -1;
   private long armedUntil;
   private long openedAt;
   /** [1.1.7] streamlined layout: a step's later paragraphs are shown after Read more */
   private boolean more;

   private interface Draw {
      void draw(GuiGraphics g, int x, int y, int w, int mx, int my);
   }

   private record Block(int height, Draw draw) {
   }

   private record Hot(int x, int y, int w, int h, Runnable run) {
      boolean in(double mx, double my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   /** page -1 = the step the hunter is on (Start here when nothing is synced yet), 0 = Start here, 1..8 = steps, 9 = Assignments. */
   public HandbookScreen(Screen parent, int page) {
      super(Component.translatable("onboard.frontierhunts.screen.title"));
      this.parent = parent instanceof HandbookScreen h ? h.parent : parent;
      this.page = page;
   }

   // ============================================================================================ state

   private static int mask() {
      return Math.max(0, HandbookClient.mask());
   }

   private static boolean synced() {
      return HandbookClient.state != null;
   }

   private static boolean done(Handbook.Task t) {
      return synced() && t.done(HandbookClient.state.tasks());
   }

   private static boolean skipped(Handbook.Task t) {
      return synced() && t.done(HandbookClient.state.skipped());
   }

   void stateChanged() {
      this.rebuild(false);
   }

   // ============================================================================================ layout

   @Override
   protected void init() {
      this.openedAt = System.currentTimeMillis();
      if (this.page < 0) {
         this.page = 0; // Start here: the next step card and the whole path at a glance
      }
      this.page = Mth.clamp(this.page, 0, ASSIGNMENTS); // [onboard2] + the Assignments tab
      this.panelW = Math.min(620, this.width - 16);
      this.panelH = Math.min(400, this.height - 16);
      this.left = (this.width - this.panelW) / 2;
      this.top = (this.height - this.panelH) / 2;
      this.side = this.panelW < 470 ? 106 : 136;
      this.bodyX = this.left + this.side + 18;
      this.bodyW = this.panelW - this.side - 36;
      this.viewTop = this.top + 50;
      this.viewBottom = this.top + this.panelH - 30;
      this.rowsTop = this.top + 44;
      this.rowH = Mth.clamp((this.panelH - 44 - 36 - TAB_GAP) / (Handbook.STEPS + 2), 14, 24); // [onboard2] + Assignments tab
      this.rebuild(true);
   }

   private void go(int p) {
      int np = Mth.clamp(p, 0, ASSIGNMENTS); // [onboard2]
      if (np != this.page) {
         this.page = np;
         this.more = false;
         this.armedTask = -1;
         this.rebuild(true);
         Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.6F));
      }
   }

   private static void click() {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.6F));
   }

   // ============================================================================================ content

   private void rebuild(boolean resetScroll) {
      this.blocks.clear();
      if (this.page == 0) {
         this.startPage();
      } else if (this.page == ASSIGNMENTS) {
         this.assignmentsPage(); // [onboard2]
      } else {
         this.stepPage(this.page);
      }
      int contentH = 0;
      for (Block b : this.blocks) {
         contentH += b.height;
      }
      this.maxScroll = Math.max(0, contentH - (this.viewBottom - this.viewTop) + 6);
      this.scroll = resetScroll ? 0 : Mth.clamp(this.scroll, 0, this.maxScroll);
   }

   /** [1.1.7] Off by default: the streamlined Handbook. The settings toggle brings back the 1.1.6 layout. */
   private static boolean classic() {
      try {
         return com.formaworks.frontierhunts.HuntConfig.CLASSIC_HANDBOOK.get();
      } catch (RuntimeException ex) {
         return false;
      }
   }

   private void startPage() {
      this.firstHuntBanner(this.bodyW); // [1.2.7]
      this.settingsRow(this.bodyW); // [1.2.8]
      if (!classic()) {
         this.startClean();
         return;
      }
      int w = this.bodyW;
      this.text(GuideUi.tr("onboard.frontierhunts.start.p1"), FrontierUi.Size.BODY, GuideUi.INK, 6);
      this.nextCard(w);
      this.section(GuideUi.tr("onboard.frontierhunts.steps"));
      for (int s = 1; s <= Handbook.STEPS; s++) {
         this.stepRow(s, w);
      }
      this.gap(6);
      this.booksSection(w); // [onebook] the Journal, Expedition journal, Field School and Assignments live in this book
      this.gap(4);
      this.note(GuideUi.tr("onboard.frontierhunts.start.pack"));
      this.text(GuideUi.tr("onboard.frontierhunts.start.card_hint", GuideClient.keyName()), FrontierUi.Size.SMALL, GuideUi.MUTED, 4);
   }

   /**
    * [1.1.7] Start here, streamlined: the one next step, then the first hour as a row of eight stops. The four books are
    * in the footer on every page, so they are not listed again here.
    */
   private void startClean() {
      int w = this.bodyW;
      if (synced() && Handbook.stepsDone(mask()) == 0) {
         this.text(GuideUi.tr("onboard.frontierhunts.start.p1"), FrontierUi.Size.SMALL, GuideUi.MUTED, 6);
      }
      this.nextCard(w);
      this.section(GuideUi.tr("onboard.frontierhunts.steps"));
      this.pathStrip(w);
      this.text(GuideUi.tr("onboard.frontierhunts.clean.books_hint"), FrontierUi.Size.SMALL, GuideUi.MUTED, 2);
   }

   /** [1.2.9] the first hunt is on and not yet claimed: it is the page's START HERE */
   private static boolean firstHuntOpen() {
      var st = FirstHuntClient.state;
      return st == null || !st.has(com.formaworks.frontierhunts.firsthunt.FirstHuntNetwork.F_CLAIMED);
   }

   /**
    * [1.2.7] the first hunt, top of Start here. [1.2.9] Now the page's big START HERE card: dark green with a gold edge
    * that breathes, the step you're on in large type, the seven steps as pips, and a gold Start / Continue button with
    * an arrow that nudges toward it. Nobody can miss it.
    */
   private void firstHuntBanner(int w) {
      var st = FirstHuntClient.state;
      if (st != null && st.has(com.formaworks.frontierhunts.firsthunt.FirstHuntNetwork.F_CLAIMED)) {
         return;
      }
      boolean started = st != null && st.has(com.formaworks.frontierhunts.firsthunt.FirstHuntNetwork.F_STARTED);
      int ch = 70;
      this.blocks.add(new Block(ch + 8, (g, x, y, bw, mx, my) -> {
         boolean inView = my >= this.viewTop && my < this.viewBottom;
         boolean hover = inView && mx >= x && mx < x + bw && my >= y && my < y + ch;
         boolean still = com.formaworks.frontierhunts.HuntConfig.REDUCED_MOTION.get();
         long now = System.currentTimeMillis();
         float breath = still ? 0.5F : (float)(0.5 + 0.5 * Math.sin(now / 520.0));
         FrontierUi.batch(g, () -> {
            // a soft gold glow that breathes around the card
            FrontierUi.rect(g, x - 3, y - 3, bw + 6, ch + 6, 8.0F, (int)(0x18 + 0x30 * breath) << 24 | 0xD6A94A);
            FrontierUi.rect(g, x - 1, y - 1, bw + 2, ch + 2, 6.0F, hover ? 0xFFE0BE76 : 0xFFB99859);
            FrontierUi.rect(g, x, y, bw, ch, 5.0F, hover ? 0xFF31493D : 0xFF263A30);
         });
         g.fillGradient(x + 2, y + ch / 2, x + bw - 2, y + ch - 2, 0x00000000, 0x28000000);
         // the START HERE tab
         String tab = GuideUi.tr("onboard.frontierhunts.start_here");
         int tabW = FrontierUi.width(tab, FrontierUi.Size.SMALL) + 12;
         FrontierUi.rect(g, x + 10, y + 8, tabW, 11, 5.5F, 0xFFD6A94A);
         FrontierUi.text(g, tab, x + 16, y + 10, 0xFF2B2014, FrontierUi.Size.SMALL);
         FrontierUi.text(g, GuideUi.tr("firsthunt.frontierhunts.banner.eyebrow"), x + 16 + tabW, y + 10, 0xFFD8BD88, FrontierUi.Size.SMALL);
         // the button on the right
         String label = GuideUi.tr(started ? "firsthunt.frontierhunts.banner.continue" : "firsthunt.frontierhunts.banner.go");
         int lw = FrontierUi.width(label, FrontierUi.Size.STRONG);
         int btnW = lw + 30, btnH = 24;
         int bx = x + bw - 12 - btnW, by = y + (ch - btnH) / 2;
         FrontierUi.batch(g, () -> {
            FrontierUi.rect(g, bx, by + 1.5F, btnW, btnH, 5.0F, 0x50000000);
            FrontierUi.rect(g, bx, by, btnW, btnH, 5.0F, hover ? 0xFFE6C77F : 0xFFD6A94A);
            FrontierUi.rect(g, bx + 2, by + 1, btnW - 4, 1.5F, 0.0F, 0x60FFFFFF);
         });
         FrontierUi.text(g, label, bx + 10, by + 8, 0xFF2B2014, FrontierUi.Size.STRONG);
         float nudge = still ? 0.0F : (float)Math.max(0.0, Math.sin(now / 190.0)) * 2.5F;
         PaperButton.chevron(g, bx + btnW - 12 + nudge, by + btnH / 2.0F, 3.4F, 0xFF2B2014);
         // arrows leading the eye from the title to the button
         float ax = bx - 16;
         for (int i = 0; i < 3; i++) {
            float phase = still ? 0.6F : (float)((now / 900.0 + i / 3.0) % 1.0);
            int a = (int)(255 * Math.sin(Math.PI * phase));
            PaperButton.chevron(g, ax - 18 + i * 7 + phase * 3, by + btnH / 2.0F, 3.0F, a << 24 | 0xD6A94A);
         }
         // the title: the step you're on, large
         String line = started ? GuideUi.tr("firsthunt.frontierhunts.screen.step", st.current().ordinal() + 1, 7, FirstHuntClient.title(st.current()))
            : GuideUi.tr("firsthunt.frontierhunts.screen.start");
         int textW = bx - 30 - (x + 12);
         FrontierUi.text(g, FrontierUi.fit(line, textW, FrontierUi.Size.TITLE), x + 12, y + 25, 0xFFF4EEDD, FrontierUi.Size.TITLE);
         // the seven steps as pips, and what it pays
         int done = st == null ? 0 : st.done();
         int cur = started ? st.current().ordinal() : -1;
         FrontierUi.batch(g, () -> {
            for (int i = 0; i < 7; i++) {
               boolean d = (done & 1 << i) != 0;
               float px = x + 12 + i * 11;
               FrontierUi.rect(g, px, y + 47, 8, 4, 2.0F, d ? 0xFFD6A94A : i == cur ? 0xFFF4EEDD : 0x40F4EEDD);
            }
         });
         FrontierUi.text(g, FrontierUi.fit(GuideUi.tr("firsthunt.frontierhunts.banner.sub", com.formaworks.frontierhunts.firsthunt.FirstHunt.REWARD_TOKENS),
            textW - 82, FrontierUi.Size.SMALL), x + 12 + 7 * 11 + 6, y + 45, 0xFFA9B4A2, FrontierUi.Size.SMALL);
         this.hot(x, y, bw, ch, () -> {
            if (!started) {
               FirstHuntClient.send(com.formaworks.frontierhunts.firsthunt.FirstHuntNetwork.A_START);
            }
            Minecraft.getInstance().setScreen(new FirstHuntScreen(this, false));
         });
      }));
   }

   /**
    * [1.2.8] the Frontier visual settings, on the first page where nobody can miss them. [1.2.9] A card of its own: a
    * sliders icon, what it's for, the preset now as a coloured pill, the key drawn as a key cap, and Open.
    */
   private void settingsRow(int w) {
      int ch = 34;
      this.blocks.add(new Block(ch + 10, (g, x, y, bw, mx, my) -> {
         boolean inView = my >= this.viewTop && my < this.viewBottom;
         boolean hover = inView && mx >= x && mx < x + bw && my >= y && my < y + ch;
         var preset = com.formaworks.frontierhunts.HuntConfig.GraphicsPreset.ULTRA;
         try {
            preset = com.formaworks.frontierhunts.HuntConfig.GRAPHICS_PRESET.get();
         } catch (RuntimeException ignored) {
         }
         boolean vanilla = preset == com.formaworks.frontierhunts.HuntConfig.GraphicsPreset.CLASSIC;
         String presetName = GuideUi.tr(vanilla ? "onboard.frontierhunts.settings.vanilla"
            : preset == com.formaworks.frontierhunts.HuntConfig.GraphicsPreset.CUSTOM ? "onboard.frontierhunts.settings.custom" : "onboard.frontierhunts.settings.ultra");
         FrontierUi.batch(g, () -> {
            FrontierUi.rect(g, x, y, bw, ch, 5.0F, hover ? 0xFF6B8FA8 : 0xFF9DB0BC);
            FrontierUi.rect(g, x + 1, y + 1, bw - 2, ch - 2, 4.0F, hover ? 0xFFE4EBEE : 0xFFEDF0EC);
            // the icon: three sliders on a blue disc
            FrontierUi.circle(g, x + 18, y + ch / 2.0F, 11.0F, 0xFF2F5F80);
            float[] knob = {15.5F, 21.0F, 17.5F};
            for (int i = 0; i < 3; i++) {
               float ly = y + ch / 2.0F - 4.5F + i * 4.5F;
               FrontierUi.rect(g, x + 12, ly - 0.6F, 12, 1.2F, 0.6F, 0xFFD9E6EE);
               FrontierUi.circle(g, x + knob[i], ly, 1.9F, 0xFFFFFFFF);
            }
         });
         int tx = x + 36;
         // the right side, right to left: chevron, key cap, preset pill
         float rx = x + bw - 12;
         PaperButton.chevron(g, rx - 2 + (hover ? 1.5F : 0.0F), y + ch / 2.0F, 3.2F, 0xFF2F5F80);
         rx -= 14;
         String key = com.formaworks.frontierhunts.client.FrontierSettingsHooks.keyName();
         int kw = Math.max(18, FrontierUi.width(key, FrontierUi.Size.STRONG) + 10);
         float kx = rx - kw, ky = y + (ch - 18) / 2.0F;
         FrontierUi.batch(g, () -> {
            FrontierUi.rect(g, kx, ky, kw, 18, 3.5F, 0xFF4A5560);
            FrontierUi.rect(g, kx + 1, ky + 1, kw - 2, 14.5F, 3.0F, 0xFFF7F7F2);
         });
         FrontierUi.center(g, key, kx + kw / 2.0F, ky + 4.5F, 0xFF2B3238, FrontierUi.Size.STRONG);
         String press = GuideUi.tr("onboard.frontierhunts.settings.press");
         int pw0 = FrontierUi.width(press, FrontierUi.Size.SMALL);
         FrontierUi.text(g, press, kx - 5 - pw0, y + ch / 2.0F - 3.5F, GuideUi.MUTED, FrontierUi.Size.SMALL);
         float pillR = kx - 5 - pw0 - 8;
         int pw = FrontierUi.width(presetName, FrontierUi.Size.SMALL) + 12;
         FrontierUi.rect(g, pillR - pw, y + (ch - 12) / 2.0F, pw, 12, 6.0F, vanilla ? 0xFF8A8F86 : 0xFFD6A94A);
         FrontierUi.text(g, presetName, pillR - pw + 6, y + (ch - 12) / 2.0F + 2.5F, vanilla ? 0xFFF7F7F2 : 0xFF2B2014, FrontierUi.Size.SMALL);
         int textW = (int)(pillR - pw - 8 - tx);
         FrontierUi.text(g, FrontierUi.fit(GuideUi.tr("onboard.frontierhunts.settings.title"), textW, FrontierUi.Size.STRONG), tx, y + 7, GuideUi.INK,
            FrontierUi.Size.STRONG);
         FrontierUi.text(g, FrontierUi.fit(GuideUi.tr("onboard.frontierhunts.settings.sub"), textW, FrontierUi.Size.SMALL), tx, y + 19, GuideUi.MUTED,
            FrontierUi.Size.SMALL);
         this.hot(x, y, bw, ch, () -> com.formaworks.frontierhunts.client.FrontierSettingsHooks.open(this));
      }));
   }

   /** the eight steps as one row of numbered stops on a line, the short name under each; click one to open it */
   private void pathStrip(int w) {
      int n = Handbook.STEPS;
      this.blocks.add(new Block(44, (g, x, y, bw, mx, my) -> {
         int m = mask();
         float cell = bw / (float)n;
         float ly = y + 11;
         for (int i = 1; i < n; i++) {
            float x0 = x + cell * (i - 0.5F), x1 = x + cell * (i + 0.5F);
            boolean dn = synced() && Handbook.stepDone(i, m);
            FrontierUi.rect(g, x0, ly - 1, x1 - x0, 2, 1.0F, dn ? GuideUi.GREEN : 0x40655F50);
         }
         for (int i = 1; i <= n; i++) {
            float cx = x + cell * (i - 0.5F);
            boolean dn = synced() && Handbook.stepDone(i, m);
            boolean cur = synced() && Handbook.currentStep(m) == i;
            int hx = (int)(x + cell * (i - 1)), hw = (int)cell;
            boolean inView = my >= this.viewTop && my < this.viewBottom;
            boolean hover = inView && mx >= hx && mx < hx + hw && my >= y && my < y + 40;
            if (hover) {
               FrontierUi.rect(g, hx + 1, y, hw - 2, 40, 3.0F, 0x1A8F6E2F);
            }
            if (cur) {
               FrontierUi.circle(g, cx, ly, 10.0F, 0x40B99859);
            }
            GuideUi.badge(g, cx, ly, i, dn ? 2 : cur ? 1 : 0, false);
            String label = FrontierUi.fit(GuideUi.tr("onboard.frontierhunts.step." + i + ".short"), hw - 4, FrontierUi.Size.SMALL);
            FrontierUi.center(g, label, cx, y + 25, cur ? GuideUi.INK_BROWN : dn ? GuideUi.MUTED : GuideUi.INK, FrontierUi.Size.SMALL);
            int step = i;
            this.hot(hx, y, hw, 40, () -> this.go(step));
         }
      }));
   }

   private void stepPage(int step) {
      int w = this.bodyW;
      boolean clean = !classic();
      int paras = 0;
      for (int i = 1; i <= 4; i++) {
         String key = "onboard.frontierhunts.step." + step + ".p" + i;
         if (!GuideUi.has(key)) {
            break;
         }
         paras++;
         if (clean && i > 1 && !this.more) {
            continue;
         }
         this.text(GuideUi.tr(key, GuideClient.keyName()), FrontierUi.Size.BODY, GuideUi.INK, 6);
      }
      if (clean && paras > 1) {
         this.moreLink();
      }
      GuideArt art = step == 4 ? GuideArt.WIND : step == 5 ? GuideArt.VITALS : step == 6 ? GuideArt.HARVEST : null;
      if (art != null) {
         this.art(art, w);
      }
      String[] recipes = RECIPES[step];
      if (recipes.length > 0) {
         this.section(GuideUi.tr("onboard.frontierhunts.section.make"));
         this.recipes(recipes, w);
      }
      this.section(GuideUi.tr("onboard.frontierhunts.section.do"));
      for (Handbook.Task t : Handbook.tasks(step)) {
         this.taskRow(t, w);
      }
      if (synced() && HandbookClient.state.has(OnboardNetwork.F_LESSONS_OFF) && step >= 4 && step <= 6) {
         this.text(GuideUi.tr("onboard.frontierhunts.lessons_off"), FrontierUi.Size.SMALL, GuideUi.MUTED, 4);
      }
      this.gap(4);
      this.navRow(step, w);
   }

   // ------------------------------------------------------------------------------------------ blocks

   private void gap(int h) {
      this.blocks.add(new Block(h, (g, x, y, w, mx, my) -> {
      }));
   }

   private void text(String s, FrontierUi.Size size, int color, int gap) {
      List<FormattedCharSequence> lines = GuideUi.wrap(s, this.bodyW, size);
      int lh = FrontierUi.lineHeight(size) + 2;
      this.blocks.add(new Block(lines.size() * lh + gap, (g, x, y, w, mx, my) -> {
         int yy = y;
         for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, x, yy, color, false);
            yy += lh;
         }
      }));
   }

   private void note(String s) {
      List<FormattedCharSequence> lines = GuideUi.wrap(s, this.bodyW - 30, FrontierUi.Size.SMALL);
      int h = lines.size() * 10 + 10;
      this.blocks.add(new Block(h + 6, (g, x, y, w, mx, my) -> {
         FrontierUi.rect(g, x, y, w, h, 3.0F, 0x30B99859);
         g.fill(x, y, x + 2, y + h, GuideUi.GOLD);
         HandbookUi.item(g, "frontierhunts:field_bow", x + 6, y + (h - 16) / 2.0F, 1.0F);
         int yy = y + 5;
         for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, x + 26, yy, GuideUi.INK_BROWN, false);
            yy += 10;
         }
      }));
   }

   private void section(String title) {
      this.blocks.add(new Block(18, (g, x, y, w, mx, my) -> {
         FrontierUi.text(g, title, x, y + 5, GuideUi.GOLD_DARK, FrontierUi.Size.SMALL);
         int tw = FrontierUi.width(title, FrontierUi.Size.SMALL);
         g.fill(x + tw + 6, y + 9, x + w, y + 10, GuideUi.RULE);
      }));
   }

   private void art(GuideArt art, int w) {
      int maxH = Math.max(80, (int)((this.viewBottom - this.viewTop) * 0.55F));
      int h = w * art.height / art.width;
      int aw = w;
      if (h > maxH) {
         h = maxH;
         aw = h * art.width / art.height;
      }
      int fw = aw, fh = h;
      this.blocks.add(new Block(fh + 10, (g, x, y, bw, mx, my) -> {
         int ax = x + (bw - fw) / 2;
         FrontierUi.rect(g, ax - 3, y - 2, fw + 6, fh + 4, 3.0F, 0x22B99859);
         art.draw(g, ax, y, fw, fh, 1.0F);
      }));
   }

   /** Recipe cards in rows (as many per row as fit). */
   private void recipes(String[] ids, int w) {
      List<HandbookUi.View> views = new ArrayList<>();
      for (String id : ids) {
         HandbookUi.View v = HandbookUi.view(id);
         if (v != null) {
            views.add(v);
         }
      }
      if (views.isEmpty()) {
         this.text(GuideUi.tr("onboard.frontierhunts.recipe_missing"), FrontierUi.Size.SMALL, GuideUi.MUTED, 4);
         return;
      }
      int gapX = 8;
      List<HandbookUi.View> row = new ArrayList<>();
      int rowW = 0;
      for (HandbookUi.View v : views) {
         if (!row.isEmpty() && rowW + gapX + v.width() > w) {
            this.recipeRow(row);
            row = new ArrayList<>();
            rowW = 0;
         }
         rowW += (row.isEmpty() ? 0 : gapX) + v.width();
         row.add(v);
      }
      if (!row.isEmpty()) {
         this.recipeRow(row);
      }
   }

   private void recipeRow(List<HandbookUi.View> row) {
      int h = 0;
      for (HandbookUi.View v : row) {
         h = Math.max(h, v.height());
      }
      int rh = h;
      List<HandbookUi.View> copy = List.copyOf(row);
      this.blocks.add(new Block(rh + 7, (g, x, y, w, mx, my) -> {
         int xx = x;
         for (HandbookUi.View v : copy) {
            boolean inView = my >= this.viewTop && my < this.viewBottom;
            ItemStack hv = HandbookUi.card(g, v, xx, y, inView ? mx : -1, inView ? my : -1);
            if (!hv.isEmpty()) {
               this.hoverStack = hv;
            }
            xx += v.width() + 8;
         }
      }));
   }

   /** The big "next step" card on Start here. */
   private void nextCard(int w) {
      Handbook.Task t = HandbookClient.next();
      boolean all = synced() && t == null;
      String eyebrow = !synced() ? GuideUi.tr("onboard.frontierhunts.waiting")
         : all ? GuideUi.tr("onboard.frontierhunts.next.done.eyebrow") : GuideUi.tr("onboard.frontierhunts.next.eyebrow", t.step, Handbook.STEPS);
      String title = !synced() ? "" : all ? GuideUi.tr("onboard.frontierhunts.next.done.title") : GuideUi.tr(t.lang("title"));
      String body = !synced() ? "" : all ? GuideUi.tr("onboard.frontierhunts.next.done.body") : GuideUi.tr(t.lang("how"));
      List<FormattedCharSequence> lines = GuideUi.wrap(body, w - 46, FrontierUi.Size.BODY);
      List<Btn> btns = new ArrayList<>();
      if (t != null) {
         btns.add(new Btn(GuideUi.tr("onboard.frontierhunts.btn.show"), true, () -> this.go(t.step)));
         if (t.lesson != null) {
            btns.add(new Btn(GuideUi.tr("onboard.frontierhunts.btn.lesson"), false, () -> this.openLesson(t.lesson)));
         }
         Course c = HandbookClient.recommended();
         if (c != null) {
            btns.add(new Btn(GuideUi.tr("onboard.frontierhunts.btn.practice", GuideUi.tr(c.lang("title"))), false, () -> { arm(); HandbookClient.practice(c); }));
         }
      } else if (all) {
         // [1.1.5] after the path, the Journal's Up next is the one place that always says what to do next
         btns.add(new Btn(GuideUi.tr("onboard.frontierhunts.btn.up_next"), true, () -> this.openBook(0)));
      }
      int bh = btns.isEmpty() ? 0 : flowHeight(btns, w - 46) + 6;
      int h = 12 + 16 + lines.size() * 11 + 8 + bh;
      this.blocks.add(new Block(h + 10, (g, x, y, bw, mx, my) -> {
         // [1.2.9] while the first hunt is the START HERE, this card steps back to plain paper so the two don't compete
         boolean quiet = firstHuntOpen();
         if (quiet) {
            FrontierUi.batch(g, () -> {
               FrontierUi.rect(g, x, y, bw, h, 4.0F, 0xFFD8CFB6);
               FrontierUi.rect(g, x + 1, y + 1, bw - 2, h - 2, 3.5F, 0xFFF1EBDB);
            });
         } else {
            FrontierUi.shadow(g, x + 1, y + 1, bw - 2, h - 2, 4.0F, 3.0F);
            FrontierUi.rect(g, x, y, bw, h, 4.0F, 0xF2213027);
            FrontierUi.rect(g, x, y, 3, h, 1.5F, GuideUi.GOLD);
         }
         if (t != null) {
            FrontierUi.circle(g, x + 20, y + 22, 12.0F, quiet ? 0x30B99859 : 0x40B99859);
            HandbookUi.item(g, t.icon, x + 12, y + 14, 1.0F);
         }
         int tx = x + 40;
         FrontierUi.text(g, eyebrow, tx, y + 7, quiet ? GuideUi.GOLD_DARK : GuideUi.GOLD, FrontierUi.Size.SMALL);
         FrontierUi.text(g, FrontierUi.fit(title, bw - 48, FrontierUi.Size.STRONG), tx, y + 17, quiet ? GuideUi.INK : 0xFFF4EEDD, FrontierUi.Size.STRONG);
         int yy = y + 30;
         for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, tx, yy, quiet ? GuideUi.INK_BROWN : 0xFFCFC8B4, false);
            yy += 11;
         }
         if (!btns.isEmpty()) {
            this.flow(g, tx, y + h - bh, bw - 46, btns, mx, my);
         }
      }));
   }

   /** One step on Start here: badge, title, n/m tasks; click opens it. */
   private void stepRow(int step, int w) {
      List<Handbook.Task> tasks = Handbook.tasks(step);
      this.blocks.add(new Block(21, (g, x, y, bw, mx, my) -> {
         int m = mask();
         boolean stepDone = synced() && Handbook.stepDone(step, m);
         boolean current = synced() && Handbook.currentStep(m) == step;
         boolean hover = mx >= x && mx < x + bw && my >= y && my < y + 19 && my >= this.viewTop && my < this.viewBottom;
         if (hover || current) {
            FrontierUi.rect(g, x - 2, y, bw + 4, 19, 2.5F, current ? 0x30B99859 : 0x1A8F6E2F);
         }
         GuideUi.badge(g, x + 9, y + 9.5F, step, stepDone ? 2 : current ? 1 : 0, false);
         int n = 0;
         for (Handbook.Task t : tasks) {
            if (t.done(m)) {
               n++;
            }
         }
         String count = n + "/" + tasks.size();
         int cw = FrontierUi.width(count, FrontierUi.Size.SMALL);
         FrontierUi.text(g, FrontierUi.fit(GuideUi.tr("onboard.frontierhunts.step." + step + ".title"), bw - 50 - cw, FrontierUi.Size.BODY), x + 22, y + 5,
            stepDone ? GuideUi.MUTED : GuideUi.INK_BROWN, FrontierUi.Size.BODY);
         FrontierUi.right(g, count, x + bw - 4, y + 6, stepDone ? GuideUi.GREEN : GuideUi.GOLD_DARK, FrontierUi.Size.SMALL);
         if (stepDone) {
            g.fill(x + 22, y + 10, x + 22 + Math.min(bw - 60, FrontierUi.width(GuideUi.tr("onboard.frontierhunts.step." + step + ".title"), FrontierUi.Size.BODY)),
               y + 11, 0x80657064);
         }
         this.hot(x - 2, y, bw + 4, 19, () -> this.go(step));
      }));
   }

   /** [1.1.7] "Read more" / "Show less" under a step's first paragraph (streamlined layout) */
   private void moreLink() {
      String label = GuideUi.tr(this.more ? "onboard.frontierhunts.clean.less" : "onboard.frontierhunts.clean.more");
      this.blocks.add(new Block(16, (g, x, y, bw, mx, my) -> {
         int lw = FrontierUi.width(label, FrontierUi.Size.SMALL);
         boolean inView = my >= this.viewTop && my < this.viewBottom;
         boolean hover = inView && mx >= x && mx < x + lw + 4 && my >= y && my < y + 12;
         FrontierUi.text(g, label, x, y + 1, hover ? GuideUi.INK_BROWN : GuideUi.GOLD_DARK, FrontierUi.Size.SMALL);
         g.fill(x, y + 10, x + lw, y + 11, hover ? GuideUi.INK_BROWN : 0x80B99859);
         this.hot(x, y, lw + 4, 12, () -> {
            this.more = !this.more;
            this.rebuild(false);
         });
      }));
   }

   /** [1.1.7] a finished task in the streamlined layout: one line, ticked */
   private void doneRow(Handbook.Task t, int w) {
      this.blocks.add(new Block(19, (g, x, y, bw, mx, my) -> {
         FrontierUi.rect(g, x + 7, y + 3, 11, 11, 2.0F, GuideUi.GREEN);
         GuideUi.tick(g, x + 12.5F, y + 8.5F, GuideUi.PAPER);
         String title = GuideUi.tr(t.lang("title"));
         String state = GuideUi.tr("onboard.frontierhunts.task.done");
         int sw = FrontierUi.width(state, FrontierUi.Size.SMALL) + 8;
         FrontierUi.text(g, FrontierUi.fit(title, bw - 34 - sw, FrontierUi.Size.BODY), x + 26, y + 4, GuideUi.MUTED, FrontierUi.Size.BODY);
         FrontierUi.right(g, state, x + bw - 6, y + 5, GuideUi.GREEN, FrontierUi.Size.SMALL);
      }));
   }

   /** A task with its tick, how-to and links (Field School lesson, Ranger Academy course, skip). */
   private void taskRow(Handbook.Task t, int w) {
      if (!classic() && done(t)) {
         this.doneRow(t, w);
         return;
      }
      List<FormattedCharSequence> how = GuideUi.wrap(GuideUi.tr(t.lang("how")), w - 34, FrontierUi.Size.SMALL);
      boolean d = done(t), sk = skipped(t);
      boolean next = HandbookClient.next() == t;
      List<Btn> btns = new ArrayList<>();
      if (t.lesson != null) {
         btns.add(new Btn(GuideUi.tr("onboard.frontierhunts.btn.lesson"), next && !d, () -> this.openLesson(t.lesson)));
      }
      if (t.course != null) {
         boolean passed = HandbookClient.passed(t.course);
         btns.add(new Btn(GuideUi.tr(passed ? "onboard.frontierhunts.btn.practice_done" : "onboard.frontierhunts.btn.practice", GuideUi.tr(t.course.lang("title"))),
            next && !d && t.lesson == null, () -> { arm(); HandbookClient.practice(t.course); }));
      }
      int idx = t.ordinal();
      if (t.lesson == null && !d) {
         if (sk) {
            btns.add(new Btn(GuideUi.tr("onboard.frontierhunts.btn.unskip"), false, () -> HandbookClient.send(OnboardNetwork.A_UNSKIP, idx)));
         } else {
            // the label grows to "Click again to skip" while armed: lay out for the longer one
            btns.add(new Btn(GuideUi.tr("onboard.frontierhunts.btn.skip_confirm"), false, () -> {
               if (this.armedTask == idx && System.currentTimeMillis() < this.armedUntil) {
                  this.armedTask = -1;
                  HandbookClient.send(OnboardNetwork.A_SKIP, idx);
               } else {
                  this.armedTask = idx;
                  this.armedUntil = System.currentTimeMillis() + 3000L;
               }
            }, idx));
         }
      }
      int bh = btns.isEmpty() ? 0 : flowHeight(btns, w - 34) + 4;
      int h = 19 + how.size() * 10 + bh + 4;
      this.blocks.add(new Block(h + 5, (g, x, y, bw, mx, my) -> {
         FrontierUi.rect(g, x, y, bw, h, 3.0F, next ? 0x34B99859 : 0x18000000);
         if (next) {
            g.fill(x, y, x + 2, y + h, GuideUi.GOLD);
         }
         // tick box
         FrontierUi.rect(g, x + 7, y + 6, 13, 13, 2.0F, d ? GuideUi.GREEN : (sk ? 0xFF8A8C7E : 0xFFB9AE8E));
         FrontierUi.rect(g, x + 8, y + 7, 11, 11, 1.5F, d ? GuideUi.GREEN : GuideUi.PAPER);
         if (d) {
            GuideUi.tick(g, x + 13.5F, y + 12.5F, GuideUi.PAPER);
         } else if (sk) {
            g.fill(x + 10, y + 12, x + 17, y + 13, 0xFF8A8C7E);
         }
         HandbookUi.item(g, t.icon, x + bw - 22, y + 4, 1.0F);
         String title = GuideUi.tr(t.lang("title"));
         String state = d ? GuideUi.tr("onboard.frontierhunts.task.done") : sk ? GuideUi.tr("onboard.frontierhunts.task.skipped")
            : t.lesson != null ? GuideUi.tr("onboard.frontierhunts.task.lesson", t.lesson.ordinal() + 1) : "";
         int sw = state.isEmpty() ? 0 : FrontierUi.width(state, FrontierUi.Size.SMALL) + 8;
         FrontierUi.text(g, FrontierUi.fit(title, bw - 54 - sw, FrontierUi.Size.STRONG), x + 26, y + 6, d ? GuideUi.MUTED : GuideUi.INK_BROWN, FrontierUi.Size.STRONG);
         if (!state.isEmpty()) {
            FrontierUi.right(g, state, x + bw - 28, y + 7, d ? GuideUi.GREEN : GuideUi.MUTED, FrontierUi.Size.SMALL);
         }
         int yy = y + 19;
         for (FormattedCharSequence line : how) {
            g.drawString(this.font, line, x + 26, yy, GuideUi.INK, false);
            yy += 10;
         }
         if (!btns.isEmpty()) {
            this.flow(g, x + 26, y + h - bh, bw - 34, btns, mx, my);
         }
      }));
   }

   /** A button of a wrapped button row. */
   private record Btn(String label, boolean primary, Runnable run, int skipTask) {
      Btn(String label, boolean primary, Runnable run) {
         this(label, primary, run, -1);
      }
   }

   private static int buttonWidth(String label) {
      return FrontierUi.width(FrontierUi.fit(label, 230, FrontierUi.Size.SMALL), FrontierUi.Size.SMALL) + 16;
   }

   /** Height of a row of buttons wrapped to {@code avail} (20 px per line). */
   private static int flowHeight(List<Btn> btns, int avail) {
      int lines = 1, used = 0;
      for (Btn b : btns) {
         int bw = Math.min(avail, buttonWidth(b.label));
         if (used > 0 && used + 6 + bw > avail) {
            lines++;
            used = 0;
         }
         used += (used > 0 ? 6 : 0) + bw;
      }
      return lines * 20 - 3;
   }

   /** Draws the buttons left to right, wrapping like {@link #flowHeight}. */
   private void flow(GuiGraphics g, int x, int y, int avail, List<Btn> btns, int mx, int my) {
      int used = 0, yy = y;
      for (Btn b : btns) {
         int bw = Math.min(avail, buttonWidth(b.label));
         if (used > 0 && used + 6 + bw > avail) {
            yy += 20;
            used = 0;
         }
         String label = b.label;
         if (b.skipTask >= 0) {
            boolean armed = this.armedTask == b.skipTask && System.currentTimeMillis() < this.armedUntil;
            label = GuideUi.tr(armed ? "onboard.frontierhunts.btn.skip_confirm" : "onboard.frontierhunts.btn.skip");
         }
         this.button(g, x + used + (used > 0 ? 6 : 0), yy, label, b.primary, mx, my, b.run, Math.min(avail, buttonWidth(label)));
         used += (used > 0 ? 6 : 0) + bw;
      }
   }

   private void navRow(int step, int w) {
      this.blocks.add(new Block(28, (g, x, y, bw, mx, my) -> {
         this.button(g, x, y + 4, GuideUi.tr("onboard.frontierhunts.btn.back_start"), false, mx, my, () -> this.go(0));
         if (step < ASSIGNMENTS) { // [onboard2] step 8 leads on to the Assignments tab
            String label = GuideUi.tr("onboard.frontierhunts.btn.next_step", GuideUi.tr(step < Handbook.STEPS
               ? "onboard.frontierhunts.step." + (step + 1) + ".title" : "onboard.frontierhunts.asg.title"));
            String fit = FrontierUi.fit(label, Math.min(220, bw - 110), FrontierUi.Size.SMALL);
            int lw = FrontierUi.width(fit, FrontierUi.Size.SMALL) + 16;
            this.button(g, x + bw - lw, y + 4, fit, true, mx, my, () -> this.go(step + 1));
         }
      }));
   }

   // ------------------------------------------------------------------------------------------ widgets drawn in content

   private void hot(int x, int y, int w, int h, Runnable run) {
      int y0 = Math.max(y, this.viewTop), y1 = Math.min(y + h, this.viewBottom);
      if (y1 > y0) {
         this.hots.add(new Hot(x, y0, w, y1 - y0, run));
      }
   }

   /** A journal-style button inside the scrolled content; returns its right edge. */
   private int button(GuiGraphics g, int x, int y, String label, boolean primary, int mx, int my, Runnable run) {
      return this.button(g, x, y, label, primary, mx, my, run, buttonWidth(label));
   }

   private int button(GuiGraphics g, int x, int y, String label, boolean primary, int mx, int my, Runnable run, int width) {
      int w = Math.max(24, width), h = 17;
      String text = FrontierUi.fit(label, w - 10, FrontierUi.Size.SMALL);
      boolean inView = my >= this.viewTop && my < this.viewBottom;
      boolean hover = inView && mx >= x && mx < x + w && my >= y && my < y + h;
      int bg = primary ? (hover ? 0xFFCBA968 : GuideUi.GOLD) : (hover ? GuideUi.BUTTON_HOVER : GuideUi.BUTTON);
      FrontierUi.rect(g, x, y, w, h, 2.0F, bg);
      FrontierUi.center(g, text, x + w / 2.0F, y + 4.5F, primary ? 0xFF2B2014 : 0xFFEFE8D7, FrontierUi.Size.SMALL);
      this.hot(x, y, w, h, run);
      return x + w;
   }

   // ============================================================================================ [onboard2] Assignments tab

   /** Space between the last step row and the Assignments tab (a rule is drawn in it). */
   private static final int TAB_GAP = 7;

   private static boolean coursePassed(Course c) {
      if (HandbookClient.passed(c)) {
         return true;
      }
      com.formaworks.frontierhunts.academy.AcademyNetwork.State a = AcademyClient.state();
      return a != null && c.ordinal() < a.courses().size() && a.courses().get(c.ordinal()).passes() > 0;
   }

   private static Course trainingCourse() {
      com.formaworks.frontierhunts.academy.AcademyNetwork.State a = AcademyClient.state();
      return a == null ? null : Course.byId(a.active());
   }

   /** The first course of the curriculum not passed yet, or null. */
   private static Course nextCourse() {
      for (Course c : Course.curriculum()) {
         if (!coursePassed(c)) {
            return c;
         }
      }
      return null;
   }

   /** {passed, total} Ranger Academy courses. */
   private static int[] academyProgress() {
      int n = 0;
      for (Course c : Course.values()) {
         if (coursePassed(c)) {
            n++;
         }
      }
      return new int[]{n, Course.count()};
   }

   /** An item that stands for the course (the journal's own "icon:blood" for the Blood Trail is not an item). */
   private static String courseItem(Course c) {
      String id = com.formaworks.frontierhunts.onboard.Onboarding.courseIcon(c);
      return id.startsWith("icon:") ? "frontierhunts:hound_lead" : id;
   }

   private static String assignmentsKey() {
      return keyName(AcademyClient.ASSIGNMENTS);
   }

   /** Opens Ranger Assignments with the course selected (or the next course to take when null). */
   private static void openAssignments(Course c) {
      Course focus = c != null ? c : nextCourse();
      arm(); // [onebook]
      if (focus != null) {
         HandbookClient.practice(focus);
      } else {
         AssignmentScreen.send(0, "", 0);
      }
   }

   private static void tabIcon(GuiGraphics g, float x, float y, float size) {
      try {
         if (!com.formaworks.frontierhunts.artqa.client.GuiArtTexture.bind(TAB_ICON, true)) {
            return;
         }
      } catch (RuntimeException ex) {
         return;
      }
      com.mojang.blaze3d.systems.RenderSystem.enableBlend();
      com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
      g.pose().pushPose();
      g.pose().translate(x, y, 0.0F);
      float k = size / 64.0F;
      g.pose().scale(k, k, 1.0F);
      g.blit(TAB_ICON, 0, 0, 64, 64, 0.0F, 0.0F, 64, 64, 64, 64);
      g.pose().popPose();
      com.mojang.blaze3d.systems.RenderSystem.disableBlend();
   }

   /** The Assignments tab at the bottom of the sidebar's tab column, under a rule. */
   private void assignmentsTab(GuiGraphics g, int mx, int my) {
      int rx = this.left + 6, rw = this.side - 12, rh = this.rowH - 3;
      int ry = this.rowsTop + (Handbook.STEPS + 1) * this.rowH + TAB_GAP;
      g.fill(rx + 4, ry - (TAB_GAP + 3) / 2 - 1, rx + rw - 4, ry - (TAB_GAP + 3) / 2, 0x50EFE8D7);
      boolean selected = this.page == ASSIGNMENTS;
      boolean hover = mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
      int[] pr = academyProgress();
      boolean pending = pr[0] < pr[1];
      if (selected || hover) {
         FrontierUi.rect(g, rx, ry, rw, rh, 2.0F, selected ? 0xFF5E4636 : 0xFF54402F);
      } else if (pending) {
         // a soft gold breathing glow while courses are left, so the tab is noticed
         float t = (float)((System.currentTimeMillis() - this.openedAt) % 2400L) / 2400.0F;
         int a = (int)(0x18 + 0x20 * (0.5F - 0.5F * Mth.cos(t * (float)Math.PI * 2.0F)));
         FrontierUi.rect(g, rx, ry, rw, rh, 2.0F, a << 24 | 0xB99859);
      }
      if (selected) {
         g.fill(rx, ry, rx + 2, ry + rh, GuideUi.GOLD);
      }
      float cy = ry + rh / 2.0F;
      tabIcon(g, rx + 3.5F, cy - 7.0F, 14.0F);
      String count = pr[0] + "/" + pr[1];
      int cw = FrontierUi.width(count, FrontierUi.Size.SMALL);
      FrontierUi.text(g, FrontierUi.fit(GuideUi.tr("onboard.frontierhunts.tab.assignments"), rw - 30 - cw, FrontierUi.Size.SMALL), rx + 21, cy - 4,
         selected ? 0xFFF4EEDD : 0xFFE3D9C3, FrontierUi.Size.SMALL);
      FrontierUi.right(g, count, rx + rw - 4, cy - 4, pending ? 0xFFE2C27A : 0xFF9FC58F, FrontierUi.Size.SMALL);
      this.hots.add(new Hot(rx, ry, rw, rh, () -> this.go(ASSIGNMENTS)));
   }

   /** The Assignments page: what the courses are, the next one, every course with its state, the field assignment. */
   private void assignmentsPage() {
      int w = this.bodyW;
      this.text(GuideUi.tr("onboard.frontierhunts.asg.p1"), FrontierUi.Size.BODY, GuideUi.INK, 6);
      this.academyCard(w);
      this.section(GuideUi.tr("onboard.frontierhunts.asg.courses"));
      Course[] cur = Course.curriculum();
      for (Course c : cur) {
         this.courseRow(c, w);
      }
      this.gap(4);
      this.section(GuideUi.tr("onboard.frontierhunts.asg.field"));
      this.fieldRow(w);
      this.gap(2);
      this.text(GuideUi.tr("onboard.frontierhunts.asg.footer", assignmentsKey()), FrontierUi.Size.SMALL, GuideUi.MUTED, 4);
   }

   /** Dark card: n of 6 passed (segmented bar), the next course and the button into Ranger Assignments. */
   private void academyCard(int w) {
      int[] pr = academyProgress();
      Course training = trainingCourse();
      Course next = nextCourse();
      boolean all = next == null;
      String eyebrow = GuideUi.tr("onboard.frontierhunts.asg.card.eyebrow", pr[0], pr[1]);
      String title = training != null ? GuideUi.tr("onboard.frontierhunts.asg.card.training", GuideUi.tr(training.lang("title")))
         : all ? GuideUi.tr("onboard.frontierhunts.asg.card.done") : GuideUi.tr("onboard.frontierhunts.asg.card.next", GuideUi.tr(next.lang("title")));
      String body = all && training == null ? GuideUi.tr("onboard.frontierhunts.asg.card.done_body")
         : GuideUi.tr((training != null ? training : next).lang("tagline"));
      List<FormattedCharSequence> lines = GuideUi.wrap(body, w - 46, FrontierUi.Size.BODY);
      List<Btn> btns = new ArrayList<>();
      btns.add(new Btn(GuideUi.tr("onboard.frontierhunts.asg.btn.open", assignmentsKey()), true, () -> openAssignments(training)));
      int bh = flowHeight(btns, w - 46) + 6;
      int h = 12 + 16 + lines.size() * 11 + 4 + 8 + bh;
      Course iconCourse = training != null ? training : next;
      this.blocks.add(new Block(h + 10, (g, x, y, bw, mx, my) -> {
         FrontierUi.shadow(g, x + 1, y + 1, bw - 2, h - 2, 4.0F, 3.0F);
         FrontierUi.rect(g, x, y, bw, h, 4.0F, 0xF2213027);
         FrontierUi.rect(g, x, y, 3, h, 1.5F, GuideUi.GOLD);
         FrontierUi.circle(g, x + 20, y + 22, 12.0F, 0x40B99859);
         if (iconCourse != null) {
            HandbookUi.item(g, courseItem(iconCourse), x + 12, y + 14, 1.0F);
         } else {
            tabIcon(g, x + 11, y + 13, 18.0F);
         }
         int tx = x + 40;
         FrontierUi.text(g, eyebrow, tx, y + 7, GuideUi.GOLD, FrontierUi.Size.SMALL);
         FrontierUi.text(g, FrontierUi.fit(title, bw - 48, FrontierUi.Size.STRONG), tx, y + 17, 0xFFF4EEDD, FrontierUi.Size.STRONG);
         int yy = y + 30;
         for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, tx, yy, 0xFFCFC8B4, false);
            yy += 11;
         }
         // one segment per course, curriculum order: passed gold, training/next outlined
         int segs = Course.count(), gap = 3, sw = Math.max(6, (bw - 48 - gap * (segs - 1)) / segs);
         int sy = yy + 2;
         Course[] cur = Course.curriculum();
         for (int i = 0; i < cur.length; i++) {
            int sx = tx + i * (sw + gap);
            boolean done = coursePassed(cur[i]);
            boolean now = cur[i] == (training != null ? training : next);
            FrontierUi.rect(g, sx, sy, sw, 4, 1.5F, done ? GuideUi.GOLD : now ? 0xFF8F7A4E : 0x40EFE8D7);
         }
         this.flow(g, tx, y + h - bh, bw - 46, btns, mx, my);
      }));
   }

   /** One course: curriculum number, its item icon, title, tagline, state; click opens it in Ranger Assignments. */
   private void courseRow(Course c, int w) {
      String tagline = GuideUi.tr(c.lang("tagline"));
      this.blocks.add(new Block(31, (g, x, y, bw, mx, my) -> {
         boolean passed = coursePassed(c);
         Course training = trainingCourse();
         boolean now = training == c || training == null && nextCourse() == c;
         boolean inView = my >= this.viewTop && my < this.viewBottom;
         boolean hover = inView && mx >= x && mx < x + bw && my >= y && my < y + 28;
         FrontierUi.rect(g, x, y, bw, 28, 3.0F, hover ? 0x30B99859 : now ? 0x26B99859 : 0x14000000);
         if (now) {
            g.fill(x, y, x + 2, y + 28, GuideUi.GOLD);
         }
         GuideUi.badge(g, x + 12, y + 14, c.step(), passed ? 2 : now ? 1 : 0, false);
         HandbookUi.item(g, courseItem(c), x + 24, y + 6, 1.0F);
         String state = training == c ? GuideUi.tr("onboard.frontierhunts.asg.pill.training") : passed ? GuideUi.tr("onboard.frontierhunts.asg.pill.passed")
            : now ? GuideUi.tr("onboard.frontierhunts.asg.pill.next") : "";
         int pw = 0;
         if (!state.isEmpty()) {
            pw = GuideUi.pill(g, state, x + bw - 6, y + 8, passed && training != c ? GuideUi.GREEN : GuideUi.GOLD_DARK, 0xFFF4EEDD) + 6;
         }
         int tx = x + 46, tw = bw - 52 - pw;
         FrontierUi.text(g, FrontierUi.fit(GuideUi.tr(c.lang("title")), tw, FrontierUi.Size.STRONG), tx, y + 5, passed ? GuideUi.MUTED : GuideUi.INK_BROWN,
            FrontierUi.Size.STRONG);
         FrontierUi.text(g, FrontierUi.fit(tagline, bw - 52, FrontierUi.Size.SMALL), tx, y + 17, GuideUi.INK, FrontierUi.Size.SMALL);
         this.hot(x, y, bw, 28, () -> openAssignments(c));
      }));
   }

   /** The field assignment taken on the board, if the client knows it; otherwise what the board offers. */
   private void fieldRow(int w) {
      com.formaworks.frontierhunts.progression.AssignmentNetwork.Snapshot s = com.formaworks.frontierhunts.client.FrontierClient.assignments;
      boolean mine = s != null && s.terms() != null && !s.id().isEmpty() && s.state() >= 1 && s.state() <= 3;
      String line = !mine ? GuideUi.tr("onboard.frontierhunts.asg.field.none")
         : GuideUi.tr(s.state() == 2 ? "onboard.frontierhunts.asg.field.ready" : s.state() == 3 ? "onboard.frontierhunts.asg.field.failed"
            : "onboard.frontierhunts.asg.field.active", s.terms().title());
      List<FormattedCharSequence> lines = GuideUi.wrap(line, w - 34, FrontierUi.Size.SMALL);
      int h = Math.max(24, lines.size() * 10 + 10);
      this.blocks.add(new Block(h + 6, (g, x, y, bw, mx, my) -> {
         FrontierUi.rect(g, x, y, bw, h, 3.0F, 0x30B99859);
         g.fill(x, y, x + 2, y + h, mine && s.state() == 2 ? GuideUi.GREEN : GuideUi.GOLD);
         tabIcon(g, x + 7, y + (h - 16) / 2.0F, 16.0F);
         int yy = y + (h - lines.size() * 10) / 2 + 1;
         for (FormattedCharSequence l : lines) {
            g.drawString(this.font, l, x + 28, yy, GuideUi.INK_BROWN, false);
            yy += 10;
         }
         boolean inView = my >= this.viewTop && my < this.viewBottom;
         if (inView && mx >= x && mx < x + bw && my >= y && my < y + h) {
            g.fill(x, y + h - 1, x + bw, y + h, GuideUi.GOLD);
         }
         this.hot(x, y, bw, h, () -> {
            arm(); // [onebook]
            AssignmentScreen.send(0, "", 0);
         });
      }));
   }

   private void openLesson(Lesson l) {
      Minecraft.getInstance().setScreen(new FieldSchoolScreen(this, l.ordinal()));
   }

   // ============================================================================================ rendering

   @Override
   public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
   }

   @Override
   public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
      this.hots.clear();
      this.hoverStack = ItemStack.EMPTY;
      g.fill(0, 0, this.width, this.height, 0xB0101716);
      GuideUi.notebook(g, this.left, this.top, this.panelW, this.panelH, this.side);
      this.sidebar(g, mouseX, mouseY);
      // header
      boolean start = this.page == 0;
      boolean asg = this.page == ASSIGNMENTS; // [onboard2]
      int m = mask();
      String eyebrow = start ? GuideUi.tr("onboard.frontierhunts.start.eyebrow") : asg ? GuideUi.tr("onboard.frontierhunts.asg.eyebrow")
         : GuideUi.tr("onboard.frontierhunts.step.eyebrow", this.page, Handbook.STEPS);
      FrontierUi.text(g, eyebrow, this.bodyX, this.top + 13, GuideUi.GOLD_DARK, FrontierUi.Size.SMALL);
      int pillW = 0;
      if (!start && !asg && synced()) {
         boolean d = Handbook.stepDone(this.page, m);
         boolean now = Handbook.currentStep(m) == this.page;
         if (d || now) {
            pillW = GuideUi.pill(g, GuideUi.tr(d ? "onboard.frontierhunts.pill.done" : "onboard.frontierhunts.pill.now"), this.left + this.panelW - 16, this.top + 12,
               d ? GuideUi.GREEN : GuideUi.GOLD_DARK, 0xFFF4EEDD);
         }
      }
      if (asg) {
         int[] pr = academyProgress();
         boolean all = pr[0] >= pr[1];
         pillW = GuideUi.pill(g, pr[0] + "/" + pr[1], this.left + this.panelW - 16, this.top + 12, all ? GuideUi.GREEN : GuideUi.GOLD_DARK, 0xFFF4EEDD);
      }
      String title = start ? GuideUi.tr("onboard.frontierhunts.start.title") : asg ? GuideUi.tr("onboard.frontierhunts.asg.title")
         : GuideUi.tr("onboard.frontierhunts.step." + this.page + ".title");
      FrontierUi.text(g, FrontierUi.fit(title, this.bodyW - pillW - 6, FrontierUi.Size.TITLE), this.bodyX, this.top + 25, GuideUi.INK_BROWN, FrontierUi.Size.TITLE);
      g.fill(this.bodyX, this.top + 45, this.left + this.panelW - 16, this.top + 46, GuideUi.RULE);
      // body
      g.enableScissor(this.bodyX - 4, this.viewTop, this.left + this.panelW - 10, this.viewBottom);
      int y = this.viewTop + 4 - this.scroll;
      for (Block b : this.blocks) {
         if (y + b.height >= this.viewTop - 4 && y < this.viewBottom + 4) {
            b.draw.draw(g, this.bodyX, y, this.bodyW, mouseX, mouseY);
         }
         y += b.height;
      }
      g.disableScissor();
      if (this.maxScroll > 0) {
         int track = this.viewBottom - this.viewTop;
         int thumb = Math.max(16, track * track / (track + this.maxScroll));
         int ty = this.viewTop + (track - thumb) * this.scroll / this.maxScroll;
         g.fill(this.left + this.panelW - 11, this.viewTop, this.left + this.panelW - 9, this.viewBottom, 0x40655F50);
         g.fill(this.left + this.panelW - 11, ty, this.left + this.panelW - 9, ty + thumb, GuideUi.GOLD);
         if (this.scroll > 0) {
            g.fillGradient(this.bodyX - 4, this.viewTop, this.left + this.panelW - 12, this.viewTop + 8, 0xFFEAE3D0, 0x00EAE3D0);
         }
         if (this.scroll < this.maxScroll) {
            g.fillGradient(this.bodyX - 4, this.viewBottom - 8, this.left + this.panelW - 12, this.viewBottom, 0x00EAE3D0, 0xFFEAE3D0);
         }
      }
      this.footer(g, mouseX, mouseY);
      super.render(g, mouseX, mouseY, pt);
      if (!this.hoverStack.isEmpty()) {
         g.renderTooltip(this.font, this.hoverStack, mouseX, mouseY);
      }
   }

   private void sidebar(GuiGraphics g, int mx, int my) {
      // [outfitter] leather cover plate (emblem, brass corners, strap) with the gold-stamped title on it
      int px = this.left + 5, pw = this.side - 10;
      int tx = com.formaworks.frontierhunts.outfitter.client.HandbookCover.plate(g, px, this.top + 6, pw, 33);
      int tw = com.formaworks.frontierhunts.outfitter.client.HandbookCover.textRight(px, pw) - tx;
      FrontierUi.text(g, FrontierUi.fit(GuideUi.tr("onboard.frontierhunts.screen.brand_top"), tw, FrontierUi.Size.STRONG), tx, this.top + 11, 0xFFE2C27A, FrontierUi.Size.STRONG);
      FrontierUi.text(g, FrontierUi.fit(GuideUi.tr("onboard.frontierhunts.screen.brand_bottom"), tw, FrontierUi.Size.SMALL), tx, this.top + 24, 0xFFC9AE7C,
         FrontierUi.Size.SMALL);
      int m = mask();
      for (int i = 0; i <= Handbook.STEPS; i++) {
         int rx = this.left + 6, ry = this.rowsTop + i * this.rowH, rw = this.side - 12, rh = this.rowH - 3;
         boolean selected = this.page == i;
         boolean hover = mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
         if (selected || hover) {
            FrontierUi.rect(g, rx, ry, rw, rh, 2.0F, selected ? 0xFF5E4636 : 0xFF54402F);
         }
         if (selected) {
            g.fill(rx, ry, rx + 2, ry + rh, GuideUi.GOLD);
         }
         float cy = ry + rh / 2.0F;
         String label;
         if (i == 0) {
            HandbookUi.item(g, "frontierhunts:frontier_handbook", rx + 4, cy - 7.5F, 0.9F);
            label = GuideUi.tr("onboard.frontierhunts.start");
         } else {
            int st = synced() && Handbook.stepDone(i, m) ? 2 : synced() && Handbook.currentStep(m) == i ? 1 : 0;
            GuideUi.badge(g, rx + 11, cy, i, st, true);
            label = GuideUi.tr("onboard.frontierhunts.step." + i + ".short");
         }
         FrontierUi.text(g, FrontierUi.fit(label, rw - 26, FrontierUi.Size.SMALL), rx + 21, cy - 4, selected ? 0xFFF4EEDD : 0xFFE3D9C3, FrontierUi.Size.SMALL);
         int idx = i;
         this.hots.add(new Hot(rx, ry, rw, rh, () -> this.go(idx)));
      }
      this.assignmentsTab(g, mx, my); // [onboard2]
      // progress
      int done = synced() ? Handbook.stepsDone(m) : 0;
      FrontierUi.text(g, FrontierUi.fit(GuideUi.tr("onboard.frontierhunts.steps_done", done, Handbook.STEPS), this.side - 16, FrontierUi.Size.SMALL), this.left + 9,
         this.top + this.panelH - 24, GuideUi.SIDEBAR_MUTED, FrontierUi.Size.SMALL);
      FrontierUi.rect(g, this.left + 9, this.top + this.panelH - 14, this.side - 18, 2, 1.0F, 0x40EFE8D7);
      FrontierUi.rect(g, this.left + 9, this.top + this.panelH - 14, (this.side - 18) * (done / (float)Handbook.STEPS), 2, 1.0F, GuideUi.GOLD);
   }

   // ============================================================================================ [onebook] the books inside

   /** The books the Handbook holds, in footer order: journal-atlas icon and lang stem. */
   private static final String[][] BOOKS = {{"notes", "journal"}, {"campaign", "expedition"}, {"school", "school"}, {"assign", "assignments"}};

   /** Remembers this Handbook page so the book about to open comes back here when it closes. */
   private static void arm() {
      if (Minecraft.getInstance().screen instanceof HandbookScreen h) {
         com.formaworks.frontierhunts.onebook.client.OneBookClient.leaving(h);
      }
   }

   /** The hotkey that opens book {@code i} from the world ("" for the Field School, which lives only in here). */
   private static String bookKey(int i) {
      return switch (i) {
         case 0 -> keyName(com.formaworks.frontierhunts.client.FrontierClient.JOURNAL);
         case 1 -> expeditionKey();
         case 3 -> keyName(AcademyClient.ASSIGNMENTS);
         default -> "";
      };
   }

   /** Opens book {@code i}: the same requests as the J / N / K keys, the Field School with this page as its parent. */
   private void openBook(int i) {
      switch (i) {
         case 0 -> {
            arm();
            PacketDistributor.sendToServer(new HuntNetwork.Request(0), new CustomPacketPayload[0]); // same request as the J key
         }
         case 1 -> {
            arm();
            ExpeditionClient.send(0, ""); // same request as the N key
         }
         case 2 -> Minecraft.getInstance().setScreen(new FieldSchoolScreen(this, -1));
         default -> {
            arm();
            AssignmentScreen.send(0, "", 0); // same request as the K key
         }
      }
   }

   /** Start here: the four books with what each is for and its key; click opens it. */
   private void booksSection(int w) {
      this.section(GuideUi.tr("onebook.frontierhunts.books"));
      for (int i = 0; i < BOOKS.length; i++) {
         this.bookRow(i, w);
      }
      this.text(GuideUi.tr("onebook.frontierhunts.books.hint"), FrontierUi.Size.SMALL, GuideUi.MUTED, 2);
   }

   private void bookRow(int i, int w) {
      String stem = "onebook.frontierhunts.book." + BOOKS[i][1];
      String title = GuideUi.tr(stem + ".title");
      List<FormattedCharSequence> lines = GuideUi.wrap(GuideUi.tr(stem + ".desc"), w - 66, FrontierUi.Size.SMALL);
      int h = Math.max(30, 18 + lines.size() * 10 + 3);
      this.blocks.add(new Block(h + 4, (g, x, y, bw, mx, my) -> {
         boolean inView = my >= this.viewTop && my < this.viewBottom;
         boolean hover = inView && mx >= x && mx < x + bw && my >= y && my < y + h;
         FrontierUi.rect(g, x, y, bw, h, 3.0F, hover ? 0x34B99859 : 0x18000000);
         g.fill(x, y, x + 2, y + h, hover ? GuideUi.GOLD : 0x80B99859);
         FrontierUi.circle(g, x + 17, y + h / 2.0F, 11.0F, 0x30B99859);
         com.formaworks.frontierhunts.journal.client.JournalIcons.draw(g, BOOKS[i][0], x + 9, y + h / 2.0F - 8.0F, 16, hover, 1.0F);
         String key = bookKey(i);
         int kw = key.isEmpty() ? 0 : GuideUi.pill(g, key, x + bw - 6, y + 6, GuideUi.GOLD_DARK, 0xFFF4EEDD) + 6;
         FrontierUi.text(g, FrontierUi.fit(title, bw - 40 - kw, FrontierUi.Size.STRONG), x + 34, y + 5, GuideUi.INK_BROWN, FrontierUi.Size.STRONG);
         int yy = y + 17;
         for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, x + 34, yy, GuideUi.INK, false);
            yy += 10;
         }
         this.hot(x, y, bw, h, () -> this.openBook(i));
      }));
   }

   /** The books inside the Handbook, on every page: icon, name and the key that opens it from the world. */
   private void footer(GuiGraphics g, int mx, int my) {
      int by = this.top + this.panelH - 26, bh = 19, gap = 4;
      int bw = (this.bodyW - gap * 3) / 4;
      for (int i = 0; i < BOOKS.length; i++) {
         int bx = this.bodyX + i * (bw + gap);
         boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
         FrontierUi.rect(g, bx, by, bw, bh, 2.5F, hover ? GuideUi.BUTTON_HOVER : GuideUi.BUTTON);
         g.fill(bx + 3, by, bx + bw - 3, by + 1, hover ? 0xC0E2C27A : 0x50E2C27A);
         com.formaworks.frontierhunts.journal.client.JournalIcons.draw(g, BOOKS[i][0], bx + 4, by + 3.5F, 12, hover, 1.0F);
         String key = bookKey(i);
         String label = GuideUi.tr("onebook.frontierhunts.book." + BOOKS[i][1] + ".short");
         int kw = key.isEmpty() ? 0 : FrontierUi.width(key, FrontierUi.Size.SMALL) + 5;
         if (kw > 0 && FrontierUi.width(label, FrontierUi.Size.SMALL) + kw > bw - 21) {
            kw = 0; // narrow window: the name matters more than the key
         }
         FrontierUi.text(g, FrontierUi.fit(label, bw - 21 - kw, FrontierUi.Size.SMALL), bx + 19, by + 5.5F, 0xFFEFE8D7, FrontierUi.Size.SMALL);
         if (kw > 0) {
            FrontierUi.right(g, key, bx + bw - 4, by + 5.5F, 0xFFE2C27A, FrontierUi.Size.SMALL);
         }
         int idx = i;
         this.hots.add(new Hot(bx, by, bw, bh, () -> this.openBook(idx)));
      }
   }

   private static String keyName(KeyMapping k) {
      try {
         return k.getTranslatedKeyMessage().getString();
      } catch (RuntimeException ex) {
         return "?";
      }
   }

   private static String expeditionKey() {
      for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
         if ("key.frontierhunts.expedition".equals(k.getName())) {
            return keyName(k);
         }
      }
      return "N";
   }

   // ============================================================================================ input

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (button == 0) {
         for (Hot h : List.copyOf(this.hots)) {
            if (h.in(mx, my)) {
               click();
               h.run.run();
               return true;
            }
         }
      }
      return super.mouseClicked(mx, my, button);
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      if (mx < this.left + this.side) {
         this.go(this.page - (int)Math.signum(sy));
         return true;
      }
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
      if (key == GLFW.GLFW_KEY_LEFT) {
         this.go(this.page - 1);
         return true;
      }
      if (key == GLFW.GLFW_KEY_RIGHT) {
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
      if (key == GLFW.GLFW_KEY_HOME) {
         this.go(0);
         return true;
      }
      return super.keyPressed(key, scan, mods);
   }

   @Override
   public void onClose() {
      Minecraft.getInstance().setScreen(this.parent);
   }

   @Override
   public void tick() {
      if (this.armedTask >= 0 && System.currentTimeMillis() >= this.armedUntil) {
         this.armedTask = -1;
      }
   }
}
