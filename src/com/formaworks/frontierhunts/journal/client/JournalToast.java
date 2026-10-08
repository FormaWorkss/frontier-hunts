package com.formaworks.frontierhunts.journal.client;

import com.formaworks.frontierhunts.client.FrontierUi;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * [journal] Journal toast in the vanilla toast stack: new rank (brass, with a rank ribbon), skill level, perk unlocked,
 * checklist entry ticked off, season summary written. All text is laid out once in the constructor.
 */
final class JournalToast implements Toast {
   enum Kind {
      RANK(0xFFD8B25A, 9000L),
      LEVEL(0xFF8DBE6E, 5000L),
      PERK(0xFFE0B865, 8000L),
      CHECK(0xFFB99859, 5500L),
      SUMMARY(0xFF6F98AE, 8000L);

      final int accent;
      final long millis;

      Kind(int accent, long millis) {
         this.accent = accent;
         this.millis = millis;
      }
   }

   private static final int WIDTH = 214;
   private final Kind kind;
   private final String icon; // [ledger] icon reference (journal atlas art or an item id)
   private final FormattedCharSequence eyebrow;
   private final FormattedCharSequence title;
   private final FormattedCharSequence right;
   private final List<FormattedCharSequence> body;
   private final int height;
   private final int accent;

   JournalToast(Kind kind, String icon, String eyebrow, String title, String right, String body, int accent) {
      this.kind = kind;
      this.icon = icon;
      this.accent = accent == 0 ? kind.accent : accent;
      this.eyebrow = JournalUi.seq(eyebrow, FrontierUi.Size.SMALL);
      this.right = right == null || right.isEmpty() ? null : JournalUi.seq(right, FrontierUi.Size.SMALL);
      int rightW = this.right == null ? 0 : JournalUi.width(this.right) + 6;
      this.title = JournalUi.fit(title, WIDTH - 40 - (kind == Kind.CHECK ? rightW : 0), FrontierUi.Size.STRONG);
      List<FormattedCharSequence> lines = body == null || body.isEmpty() ? List.of() : JournalUi.wrap(body, WIDTH - 40, FrontierUi.Size.SMALL);
      this.body = lines.size() > 3 ? lines.subList(0, 3) : lines;
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
      FrontierUi.rect(g, 0, 0, WIDTH, this.height, 4.0F, 0xF0201914);
      FrontierUi.rect(g, 0.5F, 0.5F, WIDTH - 1, this.height - 1, 3.5F, 0xF02E241B);
      FrontierUi.rect(g, 0, 0, 3, this.height, 1.5F, this.accent);
      if (this.kind == Kind.RANK) {
         // a thin brass rule along the top for the rank-up
         FrontierUi.rect(g, 6, 2, WIDTH - 12, 1, 0.5F, JournalUi.alpha(this.accent, 0.55F));
      }
      if (this.kind == Kind.RANK) {
         JournalIcons.drawRef(g, this.icon, 6, 4, 24, false, 1.0F); // the rank medal, large
      } else {
         FrontierUi.circle(g, 18, 16, 11, this.accent & 0x00FFFFFF | 0x30000000);
         JournalIcons.drawRef(g, this.icon, 10, 8, 16, false, 1.0F);
      }
      JournalUi.draw(g, this.eyebrow, 34, 5, this.accent);
      JournalUi.draw(g, this.title, 34, 14, 0xFFEFE8D7);
      if (this.right != null) {
         JournalUi.drawRight(g, this.right, WIDTH - 7, 5, this.accent);
      }
      int y = 26;
      for (FormattedCharSequence line : this.body) {
         JournalUi.draw(g, line, 34, y, 0xFFC9C3B0);
         y += 9;
      }
      float left = 1.0F - (float)Mth.clamp(time / total, 0.0, 1.0);
      FrontierUi.rect(g, 6, this.height - 2.5F, (WIDTH - 12) * left, 1.2F, 0.6F, this.accent & 0x00FFFFFF | 0x90000000);
      return time >= total ? Visibility.HIDE : Visibility.SHOW;
   }
}
