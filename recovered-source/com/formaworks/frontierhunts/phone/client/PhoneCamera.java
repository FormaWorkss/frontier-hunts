package com.formaworks.frontierhunts.phone.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.client.FieldPhotoStore;
import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.phone.PhoneNet;
import com.formaworks.frontierhunts.phone.client.ui.G;
import com.formaworks.frontierhunts.phone.client.ui.PhoneActions;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.file.Path;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

/**
 * [1.4.0] The Field Phone held up as a camera. It replaced the Field Camera, and does what it did (the real frame,
 * whatever the preset, shader pack or window size; filters; zoom) without stopping the hunter: there is no screen, the
 * whole view is the viewfinder, so the hunter walks, crouches and turns to frame the shot.
 * <ul>
 * <li><b>Click</b> takes the photo (or starts the 3-second timer), <b>right-click</b> flips to the front camera (a
 * selfie from arm's length, or from a selfie stick: Shift + wheel), the <b>wheel</b> zooms (up to 8x) or, for a selfie,
 * picks one of the {@link SelfiePose poses}, <b>middle-click</b> or the swap-hands key (F) changes the filter, <b>R</b>
 * the timer, and the phone key or Esc puts it down (back to the Camera app).</li>
 * <li>Photos are saved full size in the game's screenshots folder (as the Field Camera's always were) and appear in the
 * phone's Camera app; when the camera was opened from a text thread, the photo is offered to that hunter (Enter sends,
 * Backspace retakes).</li>
 * <li>While a selfie pose is held, hunters nearby see it too ({@link PhoneNet#OP_POSE}).</li>
 * </ul>
 */
@EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT)
public final class PhoneCamera {
   public static final String[] POSES = SelfiePose.NAMES;
   private static final ResourceLocation GLYPHS = FrontierHunts.id("textures/gui/phone/glyphs.png");
   private static final int SETTLE_FRAMES = 2;
   private static final int BLANK_RETRIES = 3;
   private static final int SHUTTER_TIMEOUT = 240;
   /** layout units: the overlay is laid out in a 270-unit-tall space and scaled to the window */
   private static final float DESIGN_H = 270.0F;

   static boolean active;
   static boolean selfie;
   /** opened with a retired Field Camera (no phone needed) */
   private static boolean fromItem;
   private static String sendTo;
   private static double zoom = 1.0;
   private static double shownZoom = 1.0;
   private static float stick = 1.6F;
   private static int filter;
   static int pose = 1;
   private static boolean timer;
   private static CameraType before;
   // the shutter
   private static boolean shooting;
   private static int settle;
   private static int frames;
   private static int blank;
   private static int countdown;
   private static long flashAt;
   private static long pressAt;
   private static String toast = "";
   private static long toastAt;
   private static Path last;
   /** a photo waiting for "send to ...?" */
   private static Path review;
   private static int ticks;
   private static int posedAt = -1000;
   private static boolean sentPose;

   private PhoneCamera() {
   }

   public static boolean active() {
      return active;
   }

   public static boolean selfie() {
      return active && selfie;
   }

   // ------------------------------------------------------------------------------------------------ up and down

   /** Puts the camera up. {@code sendTo}: the next photo is offered to this hunter. */
   public static void open(boolean front, String to) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null || !mc.player.isAlive()) {
         return;
      }
      PhoneFeed.device(mc, true);
      PhoneModel m = PhoneFeed.MODEL;
      if (!fromItem && (!m.hasPhone || m.battery <= 0)) {
         mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(m.hasPhone ? "The phone's battery is flat" : "You need a Field Phone"),
            true);
         return;
      }
      if (mc.screen != null) {
         mc.setScreen(null);
      }
      if (!active) {
         before = mc.options.getCameraType();
         zoom = 1.0;
         shownZoom = 1.0;
      }
      active = true;
      selfie = front;
      sendTo = to == null || to.isEmpty() ? null : to;
      review = null;
      countdown = 0;
      shooting = false;
      ticks = 0;
      applyView(mc);
      PhoneClient.actions().sound(PhoneActions.Sfx.OPEN);
      PhoneClient.sendState();
   }

   /** From a retired Field Camera in hand: the camera works without a phone. */
   public static void openFromItem() {
      fromItem = true;
      try {
         open(false, null);
      } finally {
         if (!active) {
            fromItem = false;
         }
      }
   }

   /** Puts the camera down; {@code toPhone}: back to the phone's Camera app. */
   public static void close(boolean toPhone) {
      if (!active) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      active = false;
      endShutter();
      countdown = 0;
      review = null;
      if (before != null) {
         mc.options.setCameraType(before);
      }
      before = null;
      if (sentPose) {
         ClientActions.send(PhoneNet.Ask.of(PhoneNet.OP_POSE, 0L, 0L));
         sentPose = false;
      }
      boolean item = fromItem;
      fromItem = false;
      PhoneClient.sendState();
      if (toPhone && !item && mc.player != null && mc.player.isAlive() && mc.screen == null) {
         PhoneClient.open("camera", null);
      }
   }

   private static void applyView(Minecraft mc) {
      CameraType want = selfie ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON;
      if (mc.options.getCameraType() != want) {
         mc.options.setCameraType(want);
      }
   }

   private static void flip() {
      selfie = !selfie;
      applyView(Minecraft.getInstance());
      PhoneClient.actions().sound(PhoneActions.Sfx.TOGGLE);
   }

   static void say(String s) {
      toast = s;
      toastAt = System.currentTimeMillis();
   }

   // ------------------------------------------------------------------------------------------------ input

   @SubscribeEvent
   public static void tickPre(ClientTickEvent.Pre e) {
      if (!active) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null || !mc.player.isAlive() || mc.screen instanceof DeathScreen) {
         close(false);
         return;
      }
      ticks++;
      // the phone key puts it down (back to the app)
      while (PhoneClient.KEY.consumeClick()) {
         close(true);
         return;
      }
      if (mc.screen != null) {
         return;
      }
      // the camera owns the mouse buttons: no mining, placing or swinging while it's up
      while (mc.options.keyAttack.consumeClick()) {
         if (review == null) {
            shutter();
         }
      }
      while (mc.options.keyUse.consumeClick()) {
         if (review == null && countdown == 0 && !shooting) {
            flip();
         }
      }
      while (mc.options.keyPickItem.consumeClick() || mc.options.keySwapOffhand.consumeClick()) {
         filter = (filter + 1) % FieldPhotoStore.FILTERS.length;
         say("Filter: " + FieldPhotoStore.FILTERS[filter]);
         PhoneClient.actions().sound(PhoneActions.Sfx.TAP);
      }
      while (mc.options.keyTogglePerspective.consumeClick()) {
         flip();
      }
      mc.options.keyAttack.setDown(false);
      mc.options.keyUse.setDown(false);
      applyView(mc);
   }

   @SubscribeEvent
   public static void tickPost(ClientTickEvent.Post e) {
      if (!active) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return;
      }
      if (!fromItem && ticks % 20 == 0) {
         PhoneFeed.device(mc, true);
         PhoneModel m = PhoneFeed.MODEL;
         if (!m.hasPhone || m.battery <= 0) {
            say(m.hasPhone ? "Battery flat" : "");
            close(false);
            return;
         }
      }
      if (ticks % 100 == 0) {
         PhoneClient.sendState();
      }
      if (countdown > 0) {
         countdown--;
         if (countdown % 20 == 0 && countdown > 0) {
            PhoneClient.actions().sound(PhoneActions.Sfx.KEY);
         }
         if (countdown == 0) {
            begin();
         }
      }
      // a held pose is shown to the hunters around (every two seconds while it lasts)
      boolean posing = selfie && pose > 0;
      if (posing && ticks - posedAt >= 40) {
         posedAt = ticks;
         ClientActions.send(PhoneNet.Ask.of(PhoneNet.OP_POSE, pose, 0L));
         sentPose = true;
      } else if (!posing && sentPose) {
         ClientActions.send(PhoneNet.Ask.of(PhoneNet.OP_POSE, 0L, 0L));
         sentPose = false;
      }
   }

   @SubscribeEvent
   public static void scroll(InputEvent.MouseScrollingEvent e) {
      Minecraft mc = Minecraft.getInstance();
      if (!active || mc.screen != null) {
         return;
      }
      e.setCanceled(true);
      double d = e.getScrollDeltaY();
      if (d == 0.0) {
         return;
      }
      if (selfie) {
         if (mc.player != null && mc.player.isShiftKeyDown() || hasShift()) {
            stick = Mth.clamp(stick + (d > 0 ? -0.25F : 0.25F), 1.0F, 4.5F);
            say(stick > 2.2F ? "Selfie stick " + String.format(java.util.Locale.ROOT, "%.1f", stick) + " m" : "Arm's length");
         } else {
            pose = Math.floorMod(pose + (d > 0 ? -1 : 1), POSES.length);
            posedAt = -1000;
            PhoneClient.actions().sound(PhoneActions.Sfx.TAP);
         }
      } else {
         zoom = Mth.clamp(zoom * (d > 0 ? 1.18 : 1.0 / 1.18), 1.0, 8.0);
      }
   }

   private static boolean hasShift() {
      long w = Minecraft.getInstance().getWindow().getWindow();
      return GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
   }

   @SubscribeEvent
   public static void key(InputEvent.Key e) {
      Minecraft mc = Minecraft.getInstance();
      if (!active || mc.screen != null || e.getAction() != GLFW.GLFW_PRESS) {
         return;
      }
      int k = e.getKey();
      if (review != null) {
         if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
            String to = sendTo;
            Path p = review;
            review = null;
            ClientActions.sendPhoto(to, p);
            // back to the thread the selfie was for
            close(false);
            PhoneClient.open("messages", null);
         } else if (k == GLFW.GLFW_KEY_BACKSPACE) {
            review = null;
            say("Retake");
         }
         return;
      }
      if (k == GLFW.GLFW_KEY_R) {
         timer = !timer;
         say(timer ? "Timer 3 s" : "Timer off");
         PhoneClient.actions().sound(PhoneActions.Sfx.TOGGLE);
      }
   }

   /** Esc puts the camera down instead of pausing. */
   @SubscribeEvent
   public static void screen(ScreenEvent.Opening e) {
      if (!active) {
         return;
      }
      if (e.getNewScreen() instanceof PauseScreen) {
         e.setCanceled(true);
         if (review != null) {
            review = null;
            return;
         }
         close(true);
      } else if (e.getNewScreen() instanceof DeathScreen) {
         close(false);
      }
   }

   // ------------------------------------------------------------------------------------------------ the view

   @SubscribeEvent
   public static void fov(ViewportEvent.ComputeFov e) {
      if (active && !selfie && e.usedConfiguredFov()) {
         e.setFOV(e.getFOV() / shownZoom);
      }
   }

   @SubscribeEvent
   public static void hand(RenderHandEvent e) {
      if (active) {
         e.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void distance(CalculateDetachedCameraDistanceEvent e) {
      if (active && selfie) {
         e.setDistance(Math.min(e.getDistance(), stick));
      }
   }

   /** Every other HUD layer is hidden while the camera is up: the screen is the viewfinder. */
   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void layer(RenderGuiLayerEvent.Pre e) {
      if (active && !e.getName().equals(LAYER)) {
         e.setCanceled(true);
      }
   }

   public static final ResourceLocation LAYER = FrontierHunts.id("phone_camera");

   // ------------------------------------------------------------------------------------------------ the shutter

   private static void shutter() {
      if (shooting || countdown > 0) {
         return;
      }
      if (FieldPhotoStore.pending() >= FieldPhotoStore.MAX_PENDING) {
         say("Still saving, one moment");
         return;
      }
      pressAt = System.currentTimeMillis();
      if (timer) {
         countdown = 60;
         PhoneClient.actions().sound(PhoneActions.Sfx.KEY);
         return;
      }
      begin();
   }

   private static boolean oldHud;

   private static void begin() {
      Minecraft mc = Minecraft.getInstance();
      // every HUD hides for the frame (the camera's own overlay, and anything drawn outside the layers)
      oldHud = mc.options.hideGui;
      mc.options.hideGui = true;
      shooting = true;
      settle = SETTLE_FRAMES;
      frames = 0;
      blank = 0;
   }

   private static void endShutter() {
      if (shooting) {
         Minecraft.getInstance().options.hideGui = oldHud;
      }
      shooting = false;
   }

   @SubscribeEvent
   public static void frame(RenderFrameEvent.Post e) {
      if (!active || !shooting) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (++frames > SHUTTER_TIMEOUT) {
         endShutter();
         say("The camera couldn't get a frame, try again");
         return;
      }
      if (settle > 0) {
         settle--;
         return;
      }
      RenderTarget target = mc.getMainRenderTarget();
      if (mc.level == null || mc.noRender || target == null || target.width <= 0 || target.height <= 0) {
         return;
      }
      NativeImage image;
      try {
         image = Screenshot.takeScreenshot(target);
      } catch (Throwable ex) {
         endShutter();
         say("The camera couldn't read this frame");
         LoggerFactory.getLogger(PhoneCamera.class).error("Cannot read the frame for a phone photo", ex);
         return;
      }
      if (FieldPhotoStore.looksBlank(image) && blank++ < BLANK_RETRIES) {
         image.close();
         return;
      }
      endShutter();
      flashAt = System.currentTimeMillis();
      PhoneClient.actions().sound(PhoneActions.Sfx.SHUTTER);
      try {
         FieldPhotoStore.save(mc, image, filter, PhoneCamera::saved);
      } catch (Throwable ex) {
         image.close();
         say("The photo couldn't be saved");
         LoggerFactory.getLogger(PhoneCamera.class).error("Cannot save a phone photo", ex);
      }
   }

   private static void saved(FieldPhotoStore.Saved result) {
      if (!result.ok()) {
         say("The photo couldn't be saved: " + result.error());
         return;
      }
      last = result.path();
      PhoneGallery.refresh();
      if (active && sendTo != null) {
         review = result.path();
      } else {
         say("Saved to Photos");
      }
   }

   // ------------------------------------------------------------------------------------------------ the overlay

   /** The camera's own HUD layer (registered above everything). */
   public static void render(GuiGraphics g, DeltaTracker dt) {
      if (!active || shooting) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.options.hideGui && review == null) {
         return;
      }
      shownZoom += (zoom - shownZoom) * 0.25;
      float sw = g.guiWidth(), sh = g.guiHeight();
      float k = sh / DESIGN_H;
      float w = sw / k, h = DESIGN_H;
      long now = System.currentTimeMillis();
      g.pose().pushPose();
      g.pose().scale(k, k, 1.0F);
      FrontierUi.batch(g, () -> chrome(g, w, h, now));
      labels(g, w, h, now);
      g.pose().popPose();
   }

   private static int a(int argb, float alpha) {
      return ((int)((argb >>> 24) * Mth.clamp(alpha, 0.0F, 1.0F)) << 24) | (argb & 0xFFFFFF);
   }

   private static void chrome(GuiGraphics g, float w, float h, long now) {
      // filter preview: a hint of the look over the view
      int tint = switch (filter) {
         case 1 -> 0x30808080;
         case 2 -> 0x24FF9A40;
         case 4 -> 0x40607A60;
         case 5 -> 0x30A07850;
         case 6 -> 0x28FFB050;
         default -> 0;
      };
      if (tint != 0) {
         g.fill(0, 0, (int)Math.ceil(w), (int)Math.ceil(h), tint);
      }
      // top and bottom shades
      g.fillGradient(0, 0, (int)Math.ceil(w), 34, 0x90000000, 0x00000000);
      g.fillGradient(0, (int)h - 64, (int)Math.ceil(w), (int)h, 0x00000000, 0xA0000000);
      // viewfinder corners
      float m = 14.0F, L = 16.0F, t = 1.2F;
      int c = 0xD0FFFFFF;
      for (int i = 0; i < 4; i++) {
         float x = (i & 1) == 0 ? m : w - m, y = (i & 2) == 0 ? m + 22.0F : h - m - 56.0F;
         float sx = (i & 1) == 0 ? 1 : -1, sy = (i & 2) == 0 ? 1 : -1;
         FrontierUi.rect(g, sx > 0 ? x : x - L, y - (sy > 0 ? 0 : t), L, t, 0.0F, c);
         FrontierUi.rect(g, x - (sx > 0 ? 0 : t), sy > 0 ? y : y - L, t, L, 0.0F, c);
      }
      if (!selfie) {
         // rule of thirds, very faint
         for (int i = 1; i < 3; i++) {
            FrontierUi.rect(g, w * i / 3.0F, 36.0F, 0.5F, h - 100.0F, 0.0F, 0x22FFFFFF);
            FrontierUi.rect(g, 0.0F, 36.0F + (h - 100.0F) * i / 3.0F, w, 0.5F, 0.0F, 0x22FFFFFF);
         }
         // focus brackets: they pull in when the shutter is pressed
         float f = Mth.clamp((now - pressAt) / 300.0F, 0.0F, 1.0F);
         float s = 18.0F - 5.0F * (1.0F - f);
         int fc = f < 1.0F ? 0xFFF0C64E : 0xB0FFFFFF;
         float cx = w / 2.0F, cy = (h - 30.0F) / 2.0F + 10.0F;
         for (int i = 0; i < 4; i++) {
            float sx = (i & 1) == 0 ? -1 : 1, sy = (i & 2) == 0 ? -1 : 1;
            float x = cx + sx * s, y = cy + sy * s;
            FrontierUi.rect(g, sx < 0 ? x : x - 5.0F, y - (sy < 0 ? 0 : 1), 5.0F, 1.0F, 0.0F, fc);
            FrontierUi.rect(g, x - (sx < 0 ? 0 : 1), sy < 0 ? y : y - 5.0F, 1.0F, 5.0F, 0.0F, fc);
         }
      }
      // the shutter
      float bx = w / 2.0F, by = h - 30.0F;
      boolean pressed = now - pressAt < 140L;
      FrontierUi.circle(g, bx, by, 17.0F, 0xFFFFFFFF);
      FrontierUi.circle(g, bx, by, 15.0F, 0xFF101010);
      FrontierUi.circle(g, bx, by, pressed ? 11.5F : 13.5F, countdown > 0 ? 0xFFE5574B : (sendTo != null ? 0xFFFF7A1F : 0xFFFFFFFF));
      // the last photo
      float tx = w / 2.0F - 78.0F, ty = h - 44.0F;
      FrontierUi.rect(g, tx - 1.0F, ty - 1.0F, 28.0F, 28.0F, 6.0F, 0xFFFFFFFF);
      FrontierUi.rect(g, tx, ty, 26.0F, 26.0F, 5.0F, 0xFF202420);
      // flip button
      float fx = w / 2.0F + 64.0F, fy = h - 44.0F;
      FrontierUi.circle(g, fx + 13.0F, fy + 13.0F, 13.0F, 0x70000000);
      // zoom pills or the pose picker above the shutter
      if (!selfie) {
         float[] stops = {1.0F, 2.0F, 4.0F, 8.0F};
         float px = w / 2.0F - 46.0F, py = h - 70.0F;
         FrontierUi.rect(g, px, py, 92.0F, 16.0F, 8.0F, 0x80000000);
         for (int i = 0; i < stops.length; i++) {
            boolean on = Math.abs(shownZoom - stops[i]) < stops[i] * 0.09 || i == stops.length - 1 && shownZoom > 7.0;
            if (on) {
               FrontierUi.circle(g, px + 12.0F + i * 23.0F, py + 8.0F, 7.0F, 0xFF2A2A2A);
            }
         }
      } else {
         float py = h - 72.0F;
         FrontierUi.rect(g, w / 2.0F - 60.0F, py, 120.0F, 18.0F, 9.0F, 0x90000000);
      }
      // flash after the shot
      long since = now - flashAt;
      if (since < 260L) {
         g.fill(0, 0, (int)Math.ceil(w), (int)Math.ceil(h), a(0xFFFFFFFF, 1.0F - since / 260.0F));
      }
      // review card
      if (review != null) {
         g.fill(0, 0, (int)Math.ceil(w), (int)Math.ceil(h), 0x90000000);
         float cw = 220.0F, ch = 168.0F, cx = (w - cw) / 2.0F, cy = (h - ch) / 2.0F;
         FrontierUi.rect(g, cx, cy, cw, ch, 12.0F, 0xF0161B18);
      }
   }

   private static void labels(GuiGraphics g, float w, float h, long now) {
      FrontierUi.Size S = FrontierUi.Size.SMALL, B = FrontierUi.Size.STRONG;
      // mode switch
      float my = 12.0F;
      String a = "PHOTO", b = "SELFIE";
      float aw = FrontierUi.width(a, B), bw = FrontierUi.width(b, B);
      float mx = w / 2.0F - (aw + bw + 16.0F) / 2.0F;
      FrontierUi.text(g, a, mx, my, selfie ? 0xC0FFFFFF : 0xFFF0C64E, B);
      FrontierUi.text(g, b, mx + aw + 16.0F, my, selfie ? 0xFFF0C64E : 0xC0FFFFFF, B);
      if (sendTo != null) {
         FrontierUi.center(g, (selfie ? "Selfie" : "Photo") + " for " + sendTo, w / 2.0F, my + 13.0F, 0xFFFF9A50, S);
      }
      // filter, timer
      String f = "Filter: " + FieldPhotoStore.FILTERS[filter];
      FrontierUi.text(g, f, 16.0F, my, 0xE0FFFFFF, S);
      if (timer) {
         FrontierUi.right(g, "Timer 3s", w - 16.0F, my, 0xFFF0C64E, S);
      }
      // last photo, flip
      PhoneModel.Pic pic = last == null ? null : PhoneGallery.thumbOf(last);
      float tx = w / 2.0F - 78.0F, ty = h - 44.0F;
      if (pic != null && pic.handle() instanceof ResourceLocation rl) {
         float pa = pic.w() / (float)Math.max(1, pic.h());
         int iw = 24, ih = 24;
         int u0 = pa > 1 ? (int)((pic.w() - pic.h()) / 2.0F) : 0, v0 = pa < 1 ? (int)((pic.h() - pic.w()) / 2.0F) : 0;
         int side = Math.min(pic.w(), pic.h());
         g.blit(rl, (int)tx + 1, (int)ty + 1, iw, ih, u0, v0, side, side, pic.w(), pic.h());
      } else {
         glyph(g, G.CAMERA, tx + 13.0F, ty + 13.0F, 14.0F, 0x80FFFFFF);
      }
      glyph(g, G.REFRESH, w / 2.0F + 77.0F, h - 31.0F, 15.0F, 0xFFFFFFFF);
      FrontierUi.center(g, selfie ? "Back" : "Selfie", w / 2.0F + 77.0F, h - 14.0F, 0xB0FFFFFF, S);
      FrontierUi.center(g, "Photos", w / 2.0F - 65.0F, h - 14.0F, 0xB0FFFFFF, S);
      // zoom labels or the pose
      if (!selfie) {
         String[] stops = {"1x", "2", "4", "8"};
         float px = w / 2.0F - 46.0F, py = h - 70.0F;
         for (int i = 0; i < stops.length; i++) {
            float sz = i == 0 ? 1.0F : (i == 1 ? 2.0F : (i == 2 ? 4.0F : 8.0F));
            boolean on = Math.abs(shownZoom - sz) < sz * 0.09 || i == 3 && shownZoom > 7.0;
            String label = on && i > 0 ? stops[i] + "x" : stops[i];
            FrontierUi.center(g, label, px + 12.0F + i * 23.0F, py + 4.0F, on ? 0xFFF0C64E : 0xD0FFFFFF, S);
         }
         if (shownZoom > 1.05) {
            String z = String.format(java.util.Locale.ROOT, "%.1fx", shownZoom);
            FrontierUi.center(g, z, w / 2.0F, py - 12.0F, 0xFFF0C64E, B);
         }
      } else {
         float py = h - 72.0F;
         String name = POSES[pose];
         FrontierUi.center(g, "<   " + name + "   >", w / 2.0F, py + 5.0F, 0xFFFFFFFF, B);
         FrontierUi.center(g, "Wheel: pose · Shift + wheel: selfie stick", w / 2.0F, py - 10.0F, 0x90FFFFFF, S);
      }
      // hints, bottom left
      FrontierUi.text(g, "Click: shoot   Right-click: flip   F: filter   R: timer", 16.0F, h - 12.0F, 0x80FFFFFF, S);
      FrontierUi.right(g, PhoneClient.KEY.getTranslatedKeyMessage().getString() + " / Esc: put away", w - 16.0F, h - 12.0F, 0x80FFFFFF, S);
      // the countdown
      if (countdown > 0) {
         int n = (countdown + 19) / 20;
         float ph = (countdown % 20) / 20.0F;
         g.pose().pushPose();
         g.pose().translate(w / 2.0F, h / 2.0F - 10.0F, 0.0F);
         float s = 3.0F + ph * 1.2F;
         g.pose().scale(s, s, 1.0F);
         FrontierUi.center(g, Integer.toString(n), 0.0F, -5.0F, a(0xFFFFFFFF, 0.4F + 0.6F * ph), FrontierUi.Size.TITLE);
         g.pose().popPose();
      }
      // a short message
      long since = now - toastAt;
      if (!toast.isEmpty() && since < 1800L) {
         float al = since < 1500L ? 1.0F : 1.0F - (since - 1500L) / 300.0F;
         float tw = FrontierUi.width(toast, B) + 16.0F;
         float ty2 = h - 94.0F;
         FrontierUi.rect(g, w / 2.0F - tw / 2.0F, ty2, tw, 15.0F, 7.5F, a(0xC0000000, al));
         FrontierUi.center(g, toast, w / 2.0F, ty2 + 4.0F, a(0xFFFFFFFF, al), B);
      }
      // review: send this one?
      if (review != null) {
         float cw = 220.0F, ch = 168.0F, cx = (w - cw) / 2.0F, cy = (h - ch) / 2.0F;
         FrontierUi.center(g, "Send to " + sendTo + "?", w / 2.0F, cy + 10.0F, 0xFFFFFFFF, B);
         PhoneModel.Pic p = PhoneGallery.thumbOf(review);
         float pw = cw - 24.0F, phh = pw * 9.0F / 16.0F;
         if (p != null && p.handle() instanceof ResourceLocation rl) {
            float pa = p.w() / (float)Math.max(1, p.h());
            phh = Math.min(phh, pw / pa);
            float rw = phh * pa;
            g.blit(rl, (int)(cx + (cw - rw) / 2.0F), (int)(cy + 26.0F), (int)rw, (int)phh, 0.0F, 0.0F, p.w(), p.h(), p.w(), p.h());
         } else {
            FrontierUi.center(g, "Developing…", w / 2.0F, cy + 70.0F, 0x90FFFFFF, S);
         }
         FrontierUi.center(g, "Enter: send     Backspace: retake", w / 2.0F, cy + ch - 18.0F, 0xFFFF9A50, B);
      }
   }

   /** A white glyph from the phone's sheet, tinted, centred. */
   private static void glyph(GuiGraphics g, G glyph, float cx, float cy, float size, int argb) {
      int sheetW = G.COLS * G.CELL, sheetH = G.ROWS * G.CELL;
      int u = (glyph.ordinal() % G.COLS) * G.CELL, v = (glyph.ordinal() / G.COLS) * G.CELL;
      RenderSystem.enableBlend();
      RenderSystem.setShaderColor((argb >> 16 & 255) / 255.0F, (argb >> 8 & 255) / 255.0F, (argb & 255) / 255.0F, (argb >>> 24) / 255.0F);
      g.pose().pushPose();
      g.pose().translate(cx - size / 2.0F, cy - size / 2.0F, 0.0F);
      g.pose().scale(size / G.CELL, size / G.CELL, 1.0F);
      g.blit(GLYPHS, 0, 0, G.CELL, G.CELL, u, v, G.CELL, G.CELL, sheetW, sheetH);
      g.pose().popPose();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }
}
