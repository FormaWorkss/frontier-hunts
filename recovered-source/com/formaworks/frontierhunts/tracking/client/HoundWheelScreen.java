package com.formaworks.frontierhunts.tracking.client;

import com.formaworks.frontierhunts.tracking.hound.HoundNet;
import com.formaworks.frontierhunts.tracking.hound.TrackingHound;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * [hound3][hound4] The hound command wheel: Heel, Sit, Stop, Track, Search, Come, Dismiss around a small hub that
 * shows the hound's name and what he is doing. Point with the mouse (or press 1-7) and click; when it was opened by
 * holding the call key, letting go of the key gives the pointed order. Under Track: the animals you hit lately - click
 * one to put him on that one. Dismiss (send him home) asks for a second click. The world stays visible (no blur, no pause).
 */
public final class HoundWheelScreen extends Screen {
   private static final int[] ORDERS = {HoundNet.HEEL, HoundNet.STAY, HoundNet.STOP, HoundNet.TRACK, HoundNet.SEARCH, HoundNet.COME, HoundNet.DISMISS};
   private static final String[] KEYS = {"heel", "stay", "stop", "track", "search", "come", "dismiss"};
   private static final int TRACK_SLOT = 3, DISMISS_SLOT = 6;
   private static final int RING = 64, DISC = 23;
   private boolean hold;
   /** [hound4] Dismiss was picked once: a second pick within 4 s sends him home */
   private long armedAt = Long.MIN_VALUE;
   private int hover = -1;
   private int hoverRow = -1;
   private final long openedAt = net.minecraft.Util.getMillis();

   public HoundWheelScreen(boolean hold) {
      super(Component.translatable("hound.frontierhunts.wheel.title"));
      this.hold = hold;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   private int cx() {
      return this.width / 2;
   }

   private int cy() {
      return this.height / 2 - 12;
   }

   /** option i sits at this angle (top first, clockwise) */
   private static double angle(int i) {
      return -Math.PI / 2 + i * (Math.PI * 2 / ORDERS.length);
   }

   private int pick(double mx, double my) {
      double dx = mx - this.cx(), dy = my - this.cy();
      if (dx * dx + dy * dy < 16 * 16) {
         return -1;
      }
      double a = Math.atan2(dy, dx);
      int best = -1;
      double bd = 9;
      for (int i = 0; i < ORDERS.length; i++) {
         double d = Math.abs(Mth.wrapDegrees((a - angle(i)) * Mth.RAD_TO_DEG));
         if (d < bd || best < 0) {
            bd = d;
            best = i;
         }
      }
      return best;
   }

   private List<HoundNet.Wound> wounds() {
      HoundNet.Info i = HoundClient.info;
      return i == null ? List.of() : i.wounds();
   }

   private int rowY(int k) {
      return this.cy() + RING + DISC + 22 + k * 13;
   }

   private int rowAt(double mx, double my) {
      List<HoundNet.Wound> w = this.wounds();
      for (int k = 0; k < w.size(); k++) {
         int y = this.rowY(k);
         if (my >= y - 2 && my < y + 11 && Math.abs(mx - this.cx()) < 130) {
            return k;
         }
      }
      return -1;
   }

   @Override
   public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
      // a soft dark vignette behind the wheel only - the woods stay in view
      int cx = this.cx(), cy = this.cy();
      disc(g, cx, cy, RING + DISC + 14, 0x30000000);
      disc(g, cx, cy, RING + DISC + 6, 0x30000000);
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float pt) {
      float open = Math.min(1.0F, (net.minecraft.Util.getMillis() - this.openedAt) / 120.0F);
      this.renderBackground(g, mx, my, pt);
      this.hoverRow = this.rowAt(mx, my);
      this.hover = this.hoverRow >= 0 ? TRACK_SLOT : this.pick(mx, my);
      int cx = this.cx(), cy = this.cy();
      float ease = 1.0F - (1.0F - open) * (1.0F - open);
      TrackingHound h = HoundClient.mine(200.0);
      // hub: name + what he is doing
      disc(g, cx, cy, 30, 0xC0201A14);
      disc(g, cx, cy, 28, 0xC03A2E22);
      HoundNet.Info info = HoundClient.info;
      String name = h != null ? h.getName().getString() : info != null && !info.hound().isEmpty() ? info.hound() : "";
      if (name.isEmpty()) {
         g.drawCenteredString(this.font, Component.translatable("hound.frontierhunts.wheel.none"), cx, cy - 4, 0xFFE0D0B0);
      } else {
         g.drawCenteredString(this.font, name, cx, cy - 9, 0xFFFFFFFF);
         Component st = h != null ? HoundRenderer.modeText(h) : Component.translatable("hound.frontierhunts.wheel.away");
         g.pose().pushPose();
         g.pose().translate(cx, cy + 3, 0);
         g.pose().scale(0.75F, 0.75F, 1.0F);
         g.drawCenteredString(this.font, st, 0, 0, 0xFFE8C890);
         g.pose().popPose();
      }
      for (int i = 0; i < ORDERS.length; i++) {
         double a = angle(i);
         int r = Math.round(RING * ease);
         int x = cx + (int)Math.round(Math.cos(a) * r), y = cy + (int)Math.round(Math.sin(a) * r);
         boolean on = i == this.hover;
         boolean active = h != null && current(h) == i;
         int rad = on ? DISC + 3 : DISC;
         boolean confirm = i == DISMISS_SLOT && this.armed();
         disc(g, x, y, rad + 2, confirm ? 0xF0E05040 : on ? 0xF0E8B860 : active ? 0xC0A07840 : 0xA0100C08);
         disc(g, x, y, rad, confirm ? 0xF0602018 : on ? 0xF05A4024 : 0xD02C241C);
         Component label = Component.translatable("hound.frontierhunts.wheel." + KEYS[i] + (confirm ? ".confirm" : ""));
         g.drawCenteredString(this.font, label, x, y - 6, on ? 0xFFFFE6B0 : 0xFFE6DCCB);
         g.pose().pushPose();
         g.pose().translate(x, y + 5, 0);
         g.pose().scale(0.7F, 0.7F, 1.0F);
         g.drawCenteredString(this.font, String.valueOf(i + 1), 0, 0, 0xFF9C8C74);
         g.pose().popPose();
      }
      // what the pointed order does
      if (this.hover >= 0 || this.armed()) {
         int k = this.armed() ? DISMISS_SLOT : this.hover;
         Component hint = this.armed() ? Component.translatable("hound.frontierhunts.wheel.dismiss.ask", name)
            : Component.translatable("hound.frontierhunts.wheel." + KEYS[k] + ".hint");
         int y = cy + RING + DISC + 8;
         int w = this.font.width(hint);
         g.fill(cx - w / 2 - 4, y - 2, cx + w / 2 + 4, y + 10, 0x90000000);
         g.drawCenteredString(this.font, hint, cx, y, 0xFFFFFFFF);
      }
      // Track: the animals you hit
      List<HoundNet.Wound> list = this.wounds();
      if (this.hover == TRACK_SLOT || this.hoverRow >= 0) {
         for (int k = 0; k < list.size(); k++) {
            HoundNet.Wound w = list.get(k);
            Component row = Component.translatable(w.dead() ? "hound.frontierhunts.wheel.row_down" : "hound.frontierhunts.wheel.row",
               w.label(), ago(w.seconds()), w.metres());
            int y = this.rowY(k);
            int tw = this.font.width(row);
            boolean on = k == this.hoverRow || this.hoverRow < 0 && k == 0;
            g.fill(cx - tw / 2 - 4, y - 2, cx + tw / 2 + 4, y + 10, on ? 0xC05A4024 : 0x90000000);
            g.drawCenteredString(this.font, row, cx, y, on ? 0xFFFFE6B0 : 0xFFBFB4A4);
         }
      }
      String foot = this.hold ? "hound.frontierhunts.wheel.foot_hold" : "hound.frontierhunts.wheel.foot";
      g.drawCenteredString(this.font, Component.translatable(foot), cx, this.height - 34, 0xFFB0A898);
   }

   /** the wheel slot of what he is doing now */
   private static int current(TrackingHound h) {
      return switch (h.mode()) {
         case TrackingHound.SIT -> 1;
         case TrackingHound.STOP -> 2;
         case TrackingHound.TRACK, TrackingHound.CAST, TrackingHound.BAY, TrackingHound.FOUND -> TRACK_SLOT;
         case TrackingHound.SEARCH, TrackingHound.STRIKE, TrackingHound.POINT -> 4;
         default -> 0;
      };
   }

   private static Component ago(int s) {
      if (s < 60) {
         return Component.translatable("hound.frontierhunts.wheel.secs", s);
      }
      return Component.translatable("hound.frontierhunts.wheel.mins", s / 60);
   }

   private boolean armed() {
      return net.minecraft.Util.getMillis() - this.armedAt < 4000L;
   }

   private void give(int slot, UUID target) {
      if (slot == DISMISS_SLOT && !this.armed()) {
         // [hound4] first pick only asks; the wheel stays open (and no longer closes on releasing the call key)
         this.armedAt = net.minecraft.Util.getMillis();
         this.hold = false;
         Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, 0.35F));
         return;
      }
      if (slot >= 0 && slot < ORDERS.length) {
         HoundClient.send(ORDERS[slot], target, true);
         Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.3F, 0.35F));
      }
      this.onClose();
   }

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (button == 0 || button == 1) {
         int row = this.rowAt(mx, my);
         if (row >= 0 && (this.hover == TRACK_SLOT || this.hoverRow >= 0)) {
            this.give(TRACK_SLOT, this.wounds().get(row).id());
            return true;
         }
         int i = this.pick(mx, my);
         if (i >= 0) {
            this.give(i, null);
         } else {
            this.onClose();
         }
         return true;
      }
      return super.mouseClicked(mx, my, button);
   }

   @Override
   public boolean keyPressed(int key, int scan, int mods) {
      if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_1 + ORDERS.length - 1) {
         this.give(key - GLFW.GLFW_KEY_1, null);
         return true;
      }
      if (!this.hold && HoundClient.CALL.matches(key, scan)) {
         this.onClose();
         return true;
      }
      return super.keyPressed(key, scan, mods);
   }

   @Override
   public boolean keyReleased(int key, int scan, int mods) {
      if (this.hold && HoundClient.CALL.matches(key, scan)) {
         if (this.hoverRow >= 0) {
            this.give(TRACK_SLOT, this.wounds().get(this.hoverRow).id());
         } else if (this.hover >= 0) {
            this.give(this.hover, null);
         } else {
            this.onClose();
         }
         return true;
      }
      return super.keyReleased(key, scan, mods);
   }

   /** a filled disc from horizontal spans (alpha-blended colour) */
   static void disc(GuiGraphics g, int cx, int cy, int r, int argb) {
      for (int dy = -r; dy <= r; dy++) {
         int half = (int)Math.floor(Math.sqrt(r * r - dy * dy + 0.25));
         g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, argb);
      }
   }
}
