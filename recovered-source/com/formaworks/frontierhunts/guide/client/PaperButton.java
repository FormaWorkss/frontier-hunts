package com.formaworks.frontierhunts.guide.client;

import com.formaworks.frontierhunts.client.FrontierUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * [1.2.9] A button that belongs on the Handbook's paper: rounded, in the journal's colours and the Frontier font, instead
 * of Minecraft's grey stone button. Three looks: the gold primary action, the dark green secondary, and a quiet outline.
 * An optional arrow after the label nudges right while the button is hovered.
 */
class PaperButton extends Button {
   enum Look {
      GOLD, GREEN, OUTLINE
   }

   private final Look look;
   private final boolean arrow;

   PaperButton(int x, int y, int w, int h, Component msg, OnPress press, Look look, boolean arrow) {
      super(x, y, w, h, msg, press, DEFAULT_NARRATION);
      this.look = look;
      this.arrow = arrow;
   }

   /** the width a label needs (text, padding and the arrow), at least {@code min} */
   static int widthFor(Component msg, boolean arrow, int min) {
      return Math.max(min, FrontierUi.width(msg.getString(), FrontierUi.Size.STRONG) + 20 + (arrow ? 10 : 0));
   }

   @Override
   protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
      boolean hot = this.active && this.isHoveredOrFocused();
      float x = this.getX(), y = this.getY(), w = this.width, h = this.height;
      int bg, fg, edge;
      switch (this.look) {
         case GOLD -> {
            bg = !this.active ? 0xFFC9BC9A : hot ? 0xFFD3B26E : GuideUi.GOLD;
            fg = !this.active ? 0xFF8A8270 : 0xFF2B2014;
            edge = !this.active ? 0xFFB3A784 : 0xFF8F6E2F;
         }
         case GREEN -> {
            bg = !this.active ? GuideUi.BUTTON_OFF : hot ? GuideUi.BUTTON_HOVER : GuideUi.BUTTON;
            fg = !this.active ? 0xFFC5C9B7 : 0xFFF3ECDB;
            edge = 0xFF1F2E26;
         }
         default -> {
            bg = hot ? 0x26B99859 : 0x00000000;
            fg = !this.active ? GuideUi.MUTED : GuideUi.INK;
            edge = hot ? GuideUi.GOLD_DARK : 0xFFB5AC92;
         }
      }
      final int fbg = bg, fedge = edge;
      FrontierUi.batch(g, () -> {
         if (this.look != Look.OUTLINE && this.active) {
            FrontierUi.rect(g, x, y + 1.5F, w, h, 4.0F, 0x30000000); // a soft drop under the button
         }
         FrontierUi.rect(g, x, y, w, h, 4.0F, fedge);
         if (fbg >>> 24 != 0 || this.look == Look.OUTLINE) {
            FrontierUi.rect(g, x + 1, y + 1, w - 2, h - 2, 3.0F, this.look == Look.OUTLINE ? GuideUi.PAPER : fbg);
         }
         if (this.look == Look.OUTLINE && fbg >>> 24 != 0) {
            FrontierUi.rect(g, x + 1, y + 1, w - 2, h - 2, 3.0F, fbg);
         }
         if (this.look == Look.GOLD && this.active) {
            FrontierUi.rect(g, x + 2, y + 1, w - 4, 1.5F, 0.0F, 0x50FFFFFF); // top highlight
         }
      });
      String s = this.getMessage().getString();
      float aw = this.arrow ? 10 : 0;
      String fit = FrontierUi.fit(s, (int)(w - 10 - aw), FrontierUi.Size.STRONG);
      float tw = FrontierUi.width(fit, FrontierUi.Size.STRONG) + aw;
      float tx = x + (w - tw) / 2.0F, ty = y + (h - 9) / 2.0F + 0.5F;
      FrontierUi.text(g, fit, tx, ty, fg, FrontierUi.Size.STRONG);
      if (this.arrow) {
         float nudge = hot ? 1.5F + (float)Math.sin(System.currentTimeMillis() / 140.0) * 0.8F : 0.0F;
         chevron(g, tx + tw - 4 + nudge, y + h / 2.0F, 3.0F, fg);
      }
   }

   /** a right-pointing chevron centred on (cx, cy), {@code r} GUI pixels tall either side */
   static void chevron(GuiGraphics g, float cx, float cy, float r, int color) {
      GuideUi.line(g, cx - r * 0.55F, cy - r, cx + r * 0.45F, cy, 1.3F, color);
      GuideUi.line(g, cx + r * 0.45F, cy, cx - r * 0.55F, cy + r, 1.3F, color);
   }
}
