package com.formaworks.frontierhunts.guide.client;

import com.formaworks.frontierhunts.client.FrontierUi;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * [guide] Field School toast: lesson complete, coaching hints, one-time field notes and graduation. Uses the vanilla
 * toast stack (top-right, queued, slides in and out with the vanilla toast sound) in the Hunter's Journal palette.
 */
final class GuideToast implements Toast {
   enum Kind {
      LESSON(0xFFB99859, 6000L),
      HINT(0xFFD08A3A, 6500L),
      NOTE(0xFF6F98AE, 9000L),
      GRADUATED(0xFF8DBE6E, 9000L);

      final int accent;
      final long millis;

      Kind(int accent, long millis) {
         this.accent = accent;
         this.millis = millis;
      }
   }

   private static final int WIDTH = 214;
   private final Kind kind;
   private final String icon; // [fieldbook] GuideIcons name
   private final String eyebrow;
   private final String title;
   private final List<FormattedCharSequence> body;
   private final int height;

   GuideToast(Kind kind, String icon, String eyebrow, String title, String body) {
      this.kind = kind;
      this.icon = icon;
      this.eyebrow = eyebrow;
      this.title = title;
      List<FormattedCharSequence> lines = body == null || body.isEmpty() ? List.of() : GuideUi.wrap(body, WIDTH - 40, FrontierUi.Size.SMALL);
      this.body = lines.size() > 4 ? lines.subList(0, 4) : lines;
      this.height = Math.max(32, 28 + this.body.size() * 9);
   }

   @Override
   public int width() {
      return WIDTH;
   }

   @Override
   public int height() {
      return this.height;
   }

   @Override
   public Visibility render(GuiGraphics g, ToastComponent toasts, long time) {
      double total = this.kind.millis * toasts.getNotificationDisplayTimeMultiplier();
      FrontierUi.shadow(g, 1, 1, WIDTH - 2, this.height - 2, 4.0F, 3.0F);
      FrontierUi.rect(g, 0, 0, WIDTH, this.height, 4.0F, 0xF0182019);
      FrontierUi.rect(g, 0.5F, 0.5F, WIDTH - 1, this.height - 1, 3.5F, 0xF0222C24);
      FrontierUi.rect(g, 0, 0, 3, this.height, 1.5F, this.kind.accent);
      // [fieldbook] medallion icon with a soft accent halo
      FrontierUi.circle(g, 18, 16, 11.5F, (this.kind.accent & 0x00FFFFFF) | 0x40000000);
      GuideIcons.draw(g, this.icon, 8, 6, 20, 1.0F);
      FrontierUi.text(g, this.eyebrow, 34, 5, this.kind.accent, FrontierUi.Size.SMALL);
      FrontierUi.text(g, FrontierUi.fit(this.title, WIDTH - 40, FrontierUi.Size.STRONG), 34, 14, 0xFFEFE8D7, FrontierUi.Size.STRONG);
      int y = 26;
      for (FormattedCharSequence line : this.body) {
         g.drawString(GuideUi.font(), line, 34, y, 0xFFC9C3B0, false);
         y += 9;
      }
      // time bar
      float left = 1.0F - (float)Mth.clamp(time / total, 0.0, 1.0);
      FrontierUi.rect(g, 6, this.height - 2.5F, (WIDTH - 12) * left, 1.2F, 0.6F, (this.kind.accent & 0x00FFFFFF) | 0x90000000);
      return time >= total ? Visibility.HIDE : Visibility.SHOW;
   }
}
