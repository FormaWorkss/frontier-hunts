package com.formaworks.frontierhunts.guide.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.guide.GuideNetwork;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * [guide] First-join welcome card: one illustrated card, two choices. Escape counts as "Begin" (the course is unobtrusive
 * and can be skipped later from the guide or with /frontierhunts tutorial skip).
 */
final class WelcomeScreen extends Screen {
   private int cardX, cardY, cardW, cardH, bannerH;
   private List<FormattedCharSequence> body;
   private List<FormattedCharSequence> kit;
   private boolean answered;
   private boolean openBook;
   private long openedAt;

   WelcomeScreen() {
      super(Component.translatable("guide.frontierhunts.welcome.title"));
   }

   @Override
   protected void init() {
      this.openedAt = System.currentTimeMillis();
      this.cardW = Math.min(372, this.width - 24);
      this.bannerH = this.cardW / 3;
      int textW = this.cardW - 32;
      String text = GuideUi.tr("guide.frontierhunts.welcome.p1") + "\n\n" + GuideUi.tr("guide.frontierhunts.welcome.p2", GuideClient.keyName())
         + "\n\n" + GuideUi.tr("guide.frontierhunts.welcome.settings", com.formaworks.frontierhunts.client.FrontierSettingsHooks.keyName()); // [1.2.8]
      this.body = GuideUi.wrap(text, textW, FrontierUi.Size.BODY);
      boolean gotKit = GuideClient.state != null && GuideClient.state.has(GuideNetwork.F_KIT);
      this.kit = gotKit ? GuideUi.wrap(GuideUi.tr("guide.frontierhunts.welcome.kit"), textW - 14, FrontierUi.Size.SMALL) : List.of();
      int textH = this.body.size() * 12 + (this.kit.isEmpty() ? 0 : this.kit.size() * 9 + 16);
      this.cardH = Math.min(this.height - 16, this.bannerH + 14 + textH + 14 + 22 + 8 + 14 + 10);
      this.cardX = (this.width - this.cardW) / 2;
      this.cardY = (this.height - this.cardH) / 2;
      int by = this.cardY + this.cardH - 44;
      int half = (this.cardW - 40) / 2;
      this.addRenderableWidget(new FieldSchoolScreen.GuideButton(this.cardX + 16, by, half + 20, 22, Component.translatable("guide.frontierhunts.welcome.begin"), b -> {
         this.openBook = true;
         this.answer(GuideNetwork.A_BEGIN);
      }, false) {
         @Override
         protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int bg = this.isHoveredOrFocused() ? 0xFFCBA968 : GuideUi.GOLD;
            FrontierUi.rect(g, this.getX(), this.getY(), this.width, this.height, 2.5F, bg);
            FrontierUi.center(g, FrontierUi.fit(this.getMessage().getString(), this.width - 8, FrontierUi.Size.STRONG), this.getX() + this.width / 2.0F,
               this.getY() + (this.height - 9) / 2.0F, 0xFF2B2014, FrontierUi.Size.STRONG);
         }
      });
      this.addRenderableWidget(new FieldSchoolScreen.GuideButton(this.cardX + 16 + half + 28, by, this.cardW - 32 - half - 28, 22, Component.translatable("guide.frontierhunts.welcome.skip"), b -> this.answer(GuideNetwork.A_SKIP_ALL), false));
   }

   private void answer(byte action) {
      if (!this.answered) {
         this.answered = true;
         GuideClient.send(action, 0);
      }
      Minecraft.getInstance().setScreen(null);
      if (action == GuideNetwork.A_BEGIN && this.openBook) {
         HandbookClient.open(0); // [onboard] the Frontier Handbook is the starting point
      }
   }

   @Override
   public void onClose() {
      this.answer(GuideNetwork.A_BEGIN);
   }

   @Override
   public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float pt) {
      float t = HuntConfig.REDUCED_MOTION.get() ? 1.0F : Mth.clamp((System.currentTimeMillis() - this.openedAt) / 320.0F, 0.0F, 1.0F);
      float e = 1.0F - (1.0F - t) * (1.0F - t);
      g.fill(0, 0, this.width, this.height, (int)(0xA0 * e) << 24 | 0x0E1310);
      g.pose().pushPose();
      g.pose().translate(0, (1.0F - e) * 10.0F, 0);
      int x = this.cardX, y = this.cardY, w = this.cardW, h = this.cardH;
      FrontierUi.shadow(g, x, y, w, h, 6.0F, 6.0F);
      FrontierUi.rect(g, x - 2, y - 2, w + 4, h + 4, 6.0F, 0xFF41352A);
      FrontierUi.rect(g, x, y, w, h, 5.0F, GuideUi.PAPER);
      // banner
      g.enableScissor(x + 2, y + 2, x + w - 2, y + 2 + this.bannerH);
      GuideArt.WELCOME.blit(g, x + 2, y + 2, w - 4, this.bannerH, 0, 0, GuideArt.WELCOME.width, GuideArt.WELCOME.height, 1.0F); // [fieldbook]
      g.fillGradient(x + 2, y + 2 + this.bannerH - 34, x + w - 2, y + 2 + this.bannerH, 0x00101410, 0xB0101410);
      g.disableScissor();
      FrontierUi.text(g, GuideUi.tr("guide.frontierhunts.welcome.eyebrow"), x + 14, y + this.bannerH - 28, 0xFFD8BD88, FrontierUi.Size.SMALL);
      FrontierUi.text(g, FrontierUi.fit(GuideUi.tr("guide.frontierhunts.welcome.title"), w - 28, FrontierUi.Size.TITLE), x + 14, y + this.bannerH - 18,
         0xFFF6EEDB, FrontierUi.Size.TITLE);
      g.fill(x + 2, y + 2 + this.bannerH, x + w - 2, y + 3 + this.bannerH, GuideUi.GOLD);
      int yy = y + this.bannerH + 14;
      for (FormattedCharSequence line : this.body) {
         g.drawString(this.font, line, x + 16, yy, GuideUi.INK, false);
         yy += 12;
      }
      if (!this.kit.isEmpty()) {
         yy += 4;
         int kh = this.kit.size() * 9 + 8;
         FrontierUi.rect(g, x + 16, yy, w - 32, kh, 3.0F, 0x30B99859);
         g.fill(x + 16, yy, x + 18, yy + kh, GuideUi.GOLD);
         int ky = yy + 4;
         for (FormattedCharSequence line : this.kit) {
            g.drawString(this.font, line, x + 24, ky, GuideUi.INK_BROWN, false);
            ky += 9;
         }
      }
      FrontierUi.center(g, FrontierUi.fit(GuideUi.tr("guide.frontierhunts.welcome.footer", GuideClient.keyName()), w - 24, FrontierUi.Size.SMALL), x + w / 2.0F,
         y + h - 16, GuideUi.MUTED, FrontierUi.Size.SMALL);
      super.render(g, mx, my, pt);
      g.pose().popPose();
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }
}
