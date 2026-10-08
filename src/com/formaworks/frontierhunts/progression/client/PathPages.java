package com.formaworks.frontierhunts.progression.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.journal.client.JournalPageView;
import com.formaworks.frontierhunts.journal.client.JournalUi;
import com.formaworks.frontierhunts.progression.HunterPath;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * [1.1.6] Journal pages "Hunter's Path" (server data from {@link HunterPath}) and "Coming Soon" (static text, lang keys
 * {@code journal.frontierhunts.soon.*}, and the Ko-fi support button). One layout routine measures, draws and
 * hit-tests ({@code g == null} while measuring or clicking).
 */
public final class PathPages {
   public static final String KOFI = "https://ko-fi.com/formaworks26954";

   private PathPages() {
   }

   @EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void setup(FMLClientSetupEvent e) {
         JournalPageView.Views.register(HunterPath.PAGE, new Path());
         JournalPageView.Views.register(HunterPath.SOON, new Soon());
      }
   }

   static int para(GuiGraphics g, int x, int y, int w, String text, int color, FrontierUi.Size size, int gap) {
      List<FormattedCharSequence> lines = JournalUi.wrap(text, w, size);
      int lh = size == FrontierUi.Size.SMALL ? 9 : 11;
      if (g != null) {
         int yy = y;
         for (FormattedCharSequence l : lines) {
            JournalUi.draw(g, l, x, yy, color);
            yy += lh;
         }
      }
      return y + lines.size() * lh + gap;
   }

   static int heading(GuiGraphics g, int x, int y, int w, String text) {
      if (g != null) {
         JournalUi.draw(g, JournalUi.fit(text, w, FrontierUi.Size.STRONG), x, y + 2, JournalUi.INK_BROWN);
         g.fill(x, y + 13, x + w, y + 14, JournalUi.RULE);
      }
      return y + 18;
   }

   // ============================================================================================ Hunter's Path

   static final class Path implements JournalPageView {
      @Override
      public int height(CompoundTag data, int width) {
         return this.paint(null, data, 0, 0, width);
      }

      @Override
      public void render(GuiGraphics g, CompoundTag data, int x, int y, int width, int mouseX, int mouseY) {
         this.paint(g, data, x, y, width);
      }

      static final String[] CHAPTERS = {"journal.frontierhunts.path.ch1", "journal.frontierhunts.path.ch2", "journal.frontierhunts.path.ch3"};

      /**
       * [1.1.7] A trail: a summary card (the stage you're on, segmented progress), then one line per stage on a rail,
       * grouped in three chapters. Only the current stage opens up with what it gives and how to get there.
       */
      private int paint(GuiGraphics g, CompoundTag d, int x, int y0, int w) {
         int y = y0;
         ListTag list = d.getList("s", Tag.TAG_COMPOUND);
         if (list.isEmpty()) {
            return para(g, x, y, w, JournalUi.tr("journal.frontierhunts.loading"), JournalUi.MUTED, FrontierUi.Size.BODY, 4) - y0;
         }
         int n = list.size(), next = d.getInt("next"), done = 0;
         for (int i = 0; i < n; i++) {
            done += list.getCompound(i).getBoolean("d") ? 1 : 0;
         }
         // ------------------------------------------------------------ summary card
         String head = next < 0 ? JournalUi.tr("journal.frontierhunts.path.complete") : list.getCompound(next).getString("t");
         int cardH = 42;
         if (g != null) {
            FrontierUi.rect(g, x - 2, y, w + 4, cardH, 4.0F, 0xFF2F3A2E);
            JournalUi.draw(g, JournalUi.seq(JournalUi.tr("journal.frontierhunts.path.now", Math.min(n, done + 1), n), FrontierUi.Size.SMALL), x + 6, y + 5, 0xFFC9B99A);
            JournalUi.draw(g, JournalUi.fit(head, w - 12, FrontierUi.Size.STRONG), x + 6, y + 15, 0xFFF4ECD8);
            float sw = (w - 12 - (n - 1) * 2.0F) / n;
            for (int i = 0; i < n; i++) {
               boolean dn = list.getCompound(i).getBoolean("d");
               int col = dn ? 0xFF7BB06F : i == next ? 0xFFE0A83A : 0x40F4ECD8;
               FrontierUi.rect(g, x + 6 + i * (sw + 2.0F), y + 31, sw, 4.0F, 1.5F, col);
            }
         }
         y += cardH + 8;
         // ------------------------------------------------------------ the trail
         int chapter = -1;
         int railX = x + 7;
         for (int i = 0; i < n; i++) {
            CompoundTag s = list.getCompound(i);
            int ch = s.getByte("ch");
            if (ch != chapter) {
               chapter = ch;
               if (g != null) {
                  JournalUi.draw(g, JournalUi.seq(JournalUi.tr(CHAPTERS[Math.clamp(ch, 0, 2)]).toUpperCase(java.util.Locale.ROOT), FrontierUi.Size.SMALL),
                     x, y + 2, JournalUi.GOLD_DARK);
                  g.fill(x, y + 12, x + w, y + 13, JournalUi.RULE);
               }
               y += 17;
            }
            boolean dn = s.getBoolean("d"), cur = i == next;
            int textX = x + 20, textW = w - 20;
            FormattedCharSequence prog = JournalUi.seq(s.getString("p"), FrontierUi.Size.SMALL);
            int pw = JournalUi.width(prog) + 8;
            int top = y, h;
            if (cur) {
               int body = para(null, textX, top + 16, textW - 6, s.getString("w"), 0, FrontierUi.Size.SMALL, 2);
               body = para(null, textX, body, textW - 6, s.getString("h"), 0, FrontierUi.Size.SMALL, 2);
               h = body - top + 4;
            } else {
               h = 16;
            }
            if (g != null) {
               // rail segment to the next stage
               if (i < n - 1) {
                  g.fill(railX, top + 9, railX + 2, top + h + 4, dn ? 0xFF7BB06F : 0x30243A32);
               }
               if (cur) {
                  FrontierUi.rect(g, x + 14, top, w - 14, h, 4.0F, 0x26B99859);
                  FrontierUi.circle(g, railX + 1, top + 7, 6.0F, 0x50E0A83A);
                  FrontierUi.circle(g, railX + 1, top + 7, 4.2F, 0xFFE0A83A);
                  FrontierUi.circle(g, railX + 1, top + 7, 2.0F, 0xFFF4ECD8);
               } else if (dn) {
                  FrontierUi.circle(g, railX + 1, top + 7, 4.2F, 0xFF3E6E37);
                  JournalUi.tick(g, railX + 1, top + 7, JournalUi.PAPER);
               } else {
                  FrontierUi.circle(g, railX + 1, top + 7, 3.6F, 0x60243A32);
                  FrontierUi.circle(g, railX + 1, top + 7, 2.4F, JournalUi.PAPER);
               }
               int tc = cur ? JournalUi.INK : dn ? JournalUi.MUTED : JournalUi.FAINT;
               JournalUi.draw(g, JournalUi.fit(s.getString("t"), textW - pw - 4, cur ? FrontierUi.Size.STRONG : FrontierUi.Size.BODY), textX, top + 3, tc);
               JournalUi.drawRight(g, prog, x + w - 4, top + 4, dn ? JournalUi.GREEN : cur ? JournalUi.GOLD_DARK : JournalUi.FAINT);
               if (cur) {
                  int yy = para(g, textX, top + 16, textW - 6, s.getString("w"), JournalUi.INK, FrontierUi.Size.SMALL, 2);
                  para(g, textX, yy, textW - 6, s.getString("h"), JournalUi.MUTED, FrontierUi.Size.SMALL, 2);
               }
            }
            y = top + h + 4;
         }
         return y - y0 + 4;
      }
   }

   // ============================================================================================ Coming Soon

   static final class Soon implements JournalPageView {
      private int btnX, btnY, btnW;

      @Override
      public int height(CompoundTag data, int width) {
         return this.paint(null, 0, 0, width, -1, -1);
      }

      @Override
      public void render(GuiGraphics g, CompoundTag data, int x, int y, int width, int mouseX, int mouseY) {
         this.paint(g, x, y, width, mouseX, mouseY);
      }

      @Override
      public boolean click(CompoundTag data, int x, int y, int width, double mouseX, double mouseY) {
         this.paint(null, x, y, width, -1, -1);
         if (mouseX >= this.btnX && mouseX < this.btnX + this.btnW && mouseY >= this.btnY && mouseY < this.btnY + 16) {
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.7F));
            ConfirmLinkScreen.confirmLinkNow(mc.screen, KOFI);
            return true;
         }
         return false;
      }

      private static final String S = "journal.frontierhunts.soon.";

      private int paint(GuiGraphics g, int x, int y0, int w, int mx, int my) {
         int y = y0;
         y = para(g, x, y, w, JournalUi.tr(S + "intro"), JournalUi.MUTED, FrontierUi.Size.SMALL, 6);
         y = heading(g, x, y, w, JournalUi.tr(S + "animals"));
         y = para(g, x, y, w, JournalUi.tr(S + "animals.text"), JournalUi.INK, FrontierUi.Size.SMALL, 2);
         y = para(g, x, y, w, JournalUi.tr(S + "animals.note"), JournalUi.MUTED, FrontierUi.Size.SMALL, 6);
         // [1.1.8] only what's coming: teasers, one card each (title + a line), no list of what the mod lacks
         y = heading(g, x, y, w, JournalUi.tr(S + "ahead"));
         for (int i = 1; JournalUi.has(S + "ahead." + i + ".title"); i++) {
            String t = JournalUi.tr(S + "ahead." + i + ".title");
            int top = y;
            int bottom = para(null, x + 8, top + 15, w - 14, JournalUi.tr(S + "ahead." + i + ".text"), 0, FrontierUi.Size.SMALL, 0) + 4;
            if (g != null) {
               FrontierUi.rect(g, x, top, w, bottom - top, 3.0F, 0x1EB99859);
               g.fill(x, top, x + 2, bottom, JournalUi.GOLD);
               JournalUi.draw(g, JournalUi.fit(t, w - 14, FrontierUi.Size.STRONG), x + 8, top + 4, JournalUi.INK_BROWN);
            }
            para(g, x + 8, top + 15, w - 14, JournalUi.tr(S + "ahead." + i + ".text"), JournalUi.INK, FrontierUi.Size.SMALL, 0);
            y = bottom + 4;
         }
         y += 3;
         y = heading(g, x, y, w, JournalUi.tr(S + "support"));
         y = para(g, x, y, w, JournalUi.tr(S + "support.text"), JournalUi.INK, FrontierUi.Size.SMALL, 4);
         FormattedCharSequence label = JournalUi.seq(JournalUi.tr(S + "support.btn"), FrontierUi.Size.SMALL);
         this.btnW = JournalUi.width(label) + 16;
         this.btnX = x;
         this.btnY = y;
         if (g != null) {
            boolean hover = mx >= this.btnX && mx < this.btnX + this.btnW && my >= this.btnY && my < this.btnY + 16;
            JournalUi.button(g, this.btnX, this.btnY, this.btnW, 16, label, hover, false, true);
         }
         y += 20;
         y = para(g, x, y, w, JournalUi.tr(S + "support.note"), JournalUi.FAINT, FrontierUi.Size.SMALL, 4);
         return y - y0;
      }
   }
}
