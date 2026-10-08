package com.formaworks.frontierhunts.phone.client.ui.apps;

import com.formaworks.frontierhunts.phone.client.ui.App;
import com.formaworks.frontierhunts.phone.client.ui.Canvas.Font;
import com.formaworks.frontierhunts.phone.client.ui.Frame;
import com.formaworks.frontierhunts.phone.client.ui.G;
import com.formaworks.frontierhunts.phone.client.ui.PhoneActions;
import com.formaworks.frontierhunts.phone.client.ui.Theme;
import com.formaworks.frontierhunts.phone.client.ui.Ui;

/**
 * [phone] Torch: the phone's LED. It lights the way ahead while you hold the phone in a hand (the same real light the
 * Field Flashlight casts, just shorter), and drains the battery while it is on.
 */
public final class TorchApp extends App {
   private static final int Z_POWER = 100;

   public TorchApp() {
      super("torch", "Torch", 8);
   }

   @Override
   protected void drawPage(Frame f, int page, float w, float h) {
      boolean on = this.m.flashlight;
      float glow = this.ui.anim(0x70C4L, on ? 1.0F : 0.0F, 10.0F);
      f.fill(0.0F, 0.0F, w, h, Theme.mix(0xFF0A0C0B, 0xFF1E1C14, glow));
      float y = Ui.header(this.ui, f, "Torch", on ? "On" : "Off", false, w);
      float cx = w / 2.0F, cy = y + 90.0F;
      for (int i = 6; i >= 1; i--) {
         f.circle(cx, cy, 40.0F + i * 9.0F * glow, Theme.withAlpha(0xFFE9A8, (int)(16 * glow)));
      }
      boolean hot = this.ui.hot(Z_POWER, 0L);
      boolean down = this.ui.down(Z_POWER, 0L);
      float r = down ? 37.0F : 40.0F;
      f.circle(cx, cy + 2.0F, r + 1.0F, 0x70000000);
      f.circle(cx, cy, r, Theme.mix(hot ? 0xFF2C312D : 0xFF242925, 0xFFFFE9A8, glow));
      f.ring(cx, cy, r, 1.5F, Theme.mix(0xFF3A413B, 0xFFFFFFFF, glow * 0.6F));
      G.FLASHLIGHT.draw(f, cx, cy, 34.0F, Theme.mix(Theme.TEXT2, 0xFF2A2412, glow));
      f.zone(Z_POWER, cx - r, cy - r, r * 2.0F, r * 2.0F);
      y = cy + 58.0F;
      String hint;
      int col;
      if (on && !this.m.held) {
         hint = "Hold the phone in a hand to light the way.";
         col = Theme.YELLOW;
      } else if (on) {
         hint = "Lighting the ground ahead of you.";
         col = Theme.TEXT;
      } else {
         hint = "Tap to switch the light on.";
         col = Theme.TEXT2;
      }
      this.txt.paraCenter(f, hint, cx, y, w - 30.0F, col, Font.STRONG, 2.0F);
      y += 30.0F;
      this.txt.paraCenter(f, "The light uses the battery four times as fast as the phone does. Battery " + this.m.battery + "%.", cx, y, w - 30.0F,
         Theme.TEXT3, Font.SMALL, 2.0F);
   }

   @Override
   public void tap(int id, long data) {
      if (id == Z_POWER) {
         this.act.setFlashlight(!this.m.flashlight);
         this.act.sound(PhoneActions.Sfx.TOGGLE);
      }
   }
}
