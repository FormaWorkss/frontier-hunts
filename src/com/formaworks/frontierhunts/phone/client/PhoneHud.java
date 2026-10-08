package com.formaworks.frontierhunts.phone.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.guide.client.FirstHuntCompass;
import com.formaworks.frontierhunts.phone.client.ui.App;
import com.formaworks.frontierhunts.phone.client.ui.Canvas.Font;
import com.formaworks.frontierhunts.phone.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * [phone] A notification from the Field Phone while it is in the pocket: a small banner slides down at the top of the
 * screen (the app's icon, who or what, one line of news) and leaves after a few seconds. Drawn with the phone's own
 * canvas; nothing is drawn (and nothing allocated) while there is no banner.
 */
final class PhoneHud {
   private static final long SHOW_MS = 4800L;
   private static final GuiCanvas CANVAS = new GuiCanvas();
   private static String app = "", title = "", body = "";
   private static long shownAt = Long.MIN_VALUE / 2;
   private static String[] queue = new String[12];
   private static int queued;

   private PhoneHud() {
   }

   static void show(String a, String t, String b) {
      long now = net.minecraft.Util.getMillis();
      if (now - shownAt < SHOW_MS - 600L) {
         if (queued + 3 <= queue.length) {
            queue[queued++] = a;
            queue[queued++] = t;
            queue[queued++] = b;
         }
         return;
      }
      app = a == null ? "" : a;
      title = t == null ? "" : t;
      body = b == null ? "" : b;
      shownAt = now;
   }

   static void clear() {
      queued = 0;
      shownAt = Long.MIN_VALUE / 2;
   }

   static void render(GuiGraphics g, DeltaTracker dt) {
      Minecraft mc = Minecraft.getInstance();
      long now = net.minecraft.Util.getMillis();
      long age = now - shownAt;
      if (age > SHOW_MS) {
         if (queued >= 3) {
            String a = queue[0], t = queue[1], b = queue[2];
            System.arraycopy(queue, 3, queue, 0, queued - 3);
            queued -= 3;
            app = a;
            title = t;
            body = b;
            shownAt = now;
            age = 0L;
         } else {
            return;
         }
      }
      if (mc.options.hideGui || mc.player == null || mc.screen instanceof PhoneScreen) {
         return;
      }
      boolean reduced;
      try {
         reduced = HuntConfig.REDUCED_MOTION.get();
      } catch (RuntimeException e) {
         reduced = false;
      }
      float in = reduced ? 1.0F : Theme.easeOut(Math.min(1.0F, age / 260.0F));
      float out = reduced ? (age > SHOW_MS - 200L ? 0.0F : 1.0F) : Math.min(1.0F, (SHOW_MS - age) / 320.0F);
      float a = Math.max(0.0F, Math.min(in, out));
      if (a <= 0.01F) {
         return;
      }
      float w = 168.0F, h = body.isEmpty() ? 24.0F : 32.0F;
      float x = g.guiWidth() / 2.0F - w / 2.0F;
      float y = (FirstHuntCompass.phoneGuiding() ? 36.0F : 6.0F) - (1.0F - in) * 14.0F;
      GuiCanvas c = CANVAS;
      c.begin(g);
      try {
         g.drawManaged(() -> {
            c.push();
            c.alpha(a);
            for (int i = 4; i >= 1; i--) {
               c.round(x - i, y - i + 2.0F, w + i * 2.0F, h + i * 2.0F, 10.0F + i, Theme.withAlpha(0, 14));
            }
            c.round(x, y, w, h, 10.0F, 0xEE161B18);
            c.round(x + 0.5F, y + 0.5F, w - 1.0F, h - 1.0F, 9.5F, 0xEE1E2420);
            App ap = PhoneClient.ui().app(app);
            float tx = x + 9.0F;
            if (ap != null) {
               int col = ap.icon % 8, row = ap.icon / 8;
               c.sprite("apps", x + 6.0F, y + h / 2.0F - 8.0F, 16.0F, 16.0F, col / 8.0F, row / 2.0F, (col + 1) / 8.0F, (row + 1) / 2.0F, 0xFFFFFFFF);
               tx = x + 27.0F;
            }
            float tw = x + w - 8.0F - tx;
            c.text(PhoneClient.ui().txt.fit(c, title, tw, Font.STRONG), tx, y + (body.isEmpty() ? 7.5F : 5.0F), Theme.TEXT, Font.STRONG);
            if (!body.isEmpty()) {
               c.text(PhoneClient.ui().txt.fit(c, body, tw, Font.SMALL), tx, y + 18.0F, Theme.TEXT2, Font.SMALL);
            }
            c.pop();
         });
      } finally {
         c.end();
      }
   }
}
