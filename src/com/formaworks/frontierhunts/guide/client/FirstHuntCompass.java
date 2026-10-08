package com.formaworks.frontierhunts.guide.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.firsthunt.FirstHunt;
import com.formaworks.frontierhunts.firsthunt.FirstHuntNetwork;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;

/**
 * [fharea] The way to the beginner area without the Handbook open: a compass ribbon at the top centre of the screen,
 * shown while the first hunt is on a step that happens in the area (sign, wind, shot) and the hunter is outside it.
 *
 * <p>N / E / S / W and the points between slide past as the hunter turns; a gold marker sits on the area's bearing (at
 * the ribbon's end, as an arrow, when it is off to the side or behind) with the distance under it, "Beginner area" under
 * that while the hunter faces it. Walking in swaps the ribbon for a short "You're in the beginner area" note with what
 * to look for, which then fades away. The ribbon steps aside like the objective card (screens, F1, F3, aiming, scopes,
 * the kill cam) and is never drawn while the hunter has hidden the first-hunt steps.
 *
 * <p>Cheap: state and text are worked out 20 times a second; the frame only draws plain fills (one batch, at the
 * screen's own pixels) and a few cached lines of text, with no allocation of its own.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class FirstHuntCompass {
   // layout, in GUI pixels
   static final float RW = 176.0F, RH = 13.0F, TOP = 4.0F, TAPER = 46.0F, HALF_FOV = 80.0F;
   // colours (the first hunt's gold, the card's dark green-black and cream)
   static final float CAP_GAP = 7.0F;
   static final int GOLD = 0xFFD6A94A, INK = 0xFF121812, CREAM = 0xFFEFE8D7, MUTED = 0xFFB9B4A2, BAND = 0x9A0E130F;
   /** how long the "you're in the area" note stays (ms) */
   static final long ARRIVE_MS = 5600L;

   // ---- state worked out per tick
   private static boolean guiding;
   private static boolean inside;
   private static boolean insideKnown;
   private static int lastAreaX = Integer.MIN_VALUE, lastAreaZ = Integer.MIN_VALUE;
   private static double targetX, targetZ;
   private static int shownMetres = -1;
   private static FormattedCharSequence distText;
   private static int distW;
   private static long ribbonSince;
   private static long arrivedAt = Long.MIN_VALUE;
   private static FirstHunt.Step arrivedStep;
   private static FormattedCharSequence arriveTitle, arriveSub;
   private static int arriveTitleW, arriveSubW;
   private static FormattedCharSequence caption;
   private static int captionW;
   /** compass point labels (N, NE, E ...) and their widths, made once per language */
   private static final FormattedCharSequence[] POINTS = new FormattedCharSequence[8];
   private static final int[] POINT_W = new int[8];
   private static String pointsLang;

   // ---- per frame
   private static float alpha, arriveAlpha;
   private static long lastNanos;
   private static int edgeSide = 1;
   private static boolean captionOn;
   private static float captionT, frameDt;
   private static final Matrix4f SAVED = new Matrix4f();
   /** what the batched fills of this frame draw (read by the static Runnables: no capture, no allocation) */
   private static GuiGraphics frameG;
   private static float frameS, frameCx, frameRel, frameA, frameW, frameLx, frameTy, frameCapA;
   private static boolean frameClamped;
   private static final Runnable BAND_FILLS = FirstHuntCompass::bandFills;
   private static final Runnable MARKER_FILLS = FirstHuntCompass::markerFills;
   private static final Runnable ARRIVE_FILLS = FirstHuntCompass::arriveFills;
   // [phone] a place the hunter picked in the Field Phone's Maps: the same ribbon shows the way when the first hunt is not
   private static boolean phoneOn, phoneMode;
   private static double phoneX, phoneZ;
   private static String phoneLabel = "";
   private static FormattedCharSequence phoneCaption;
   private static int phoneCaptionW;

   private FirstHuntCompass() {
   }

   @EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
   public static final class Setup {
      @SubscribeEvent
      public static void layers(RegisterGuiLayersEvent e) {
         e.registerBelow(VanillaGuiLayers.BOSS_OVERLAY, FrontierHunts.id("first_hunt_compass"), FirstHuntCompass::render);
      }
   }

   /** the steps that happen in the beginner area */
   /** [phone] Guide the ribbon to a place picked on the Field Phone (null label: stop). */
   public static void phoneTarget(String label, double x, double z) {
      boolean on = label != null && Double.isFinite(x) && Double.isFinite(z);
      if (on && (!phoneOn || x != phoneX || z != phoneZ || !label.equals(phoneLabel))) {
         ribbonSince = System.currentTimeMillis();
         insideKnown = false;
      }
      phoneOn = on;
      phoneX = x;
      phoneZ = z;
      phoneLabel = on ? label : "";
      phoneCaption = null;
   }

   /** [phone] Is the ribbon guiding to a Field Phone place right now? */
   public static boolean phoneGuiding() {
      return phoneMode && guiding;
   }

   static boolean areaStep(FirstHunt.Step s) {
      return s == FirstHunt.Step.SIGNS || s == FirstHunt.Step.WIND || s == FirstHunt.Step.SHOT;
   }

   /** the first hunt is pointing the hunter at the area (on an area step, an area in this dimension, not gone quiet, not hidden) */
   static boolean areaShown(FirstHuntNetwork.State st) {
      return st != null && FirstHuntClient.active() && !st.has(FirstHuntNetwork.F_HIDDEN) && areaStep(st.current()) && st.has(FirstHuntNetwork.F_AREA)
         && st.has(FirstHuntNetwork.F_AREA_HERE) && !st.has(FirstHuntNetwork.F_AREA_QUIET);
   }

   /** the ribbon is showing the way (the card then leaves out its own little arrow) */
   public static boolean guiding() {
      return guiding;
   }

   /** the hunter is inside the area (with a little hysteresis at the edge) */
   static boolean inside() {
      return inside;
   }

   static double targetX() {
      return targetX;
   }

   static double targetZ() {
      return targetZ;
   }

   // ============================================================================================ tick

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post e) {
      Minecraft mc = Minecraft.getInstance();
      FirstHuntNetwork.State st = FirstHuntClient.state;
      if (mc.player != null && mc.level != null && !areaShown(st) && phoneOn) {
         phoneTick(mc); // [phone]
         return;
      }
      phoneMode = false;
      if (mc.player == null || mc.level == null || !areaShown(st)) {
         guiding = false;
         insideKnown = false;
         return;
      }
      int r = Math.max(8, st.areaR());
      if (st.areaX() != lastAreaX || st.areaZ() != lastAreaZ) {
         lastAreaX = st.areaX();
         lastAreaZ = st.areaZ();
         insideKnown = false; // a new area: judge afresh (and say so if the hunter is already in it)
      }
      targetX = st.areaX() + 0.5;
      targetZ = st.areaZ() + 0.5;
      double dist = Math.hypot(targetX - mc.player.getX(), targetZ - mc.player.getZ());
      boolean in = inside ? dist <= r + 6 : dist <= r;
      long now = System.currentTimeMillis();
      FirstHunt.Step step = st.current();
      if (in && (!inside || !insideKnown)) {
         arrivedAt = now;
         arrivedStep = step;
         arriveText(step);
      } else if (in && arrivedStep != step && now - arrivedAt < ARRIVE_MS) {
         arrivedStep = step;
         arriveText(step);
      }
      if (!in && !guiding) {
         ribbonSince = now; // the ribbon (re)appears: "Beginner area" under the marker for its first seconds
      }
      inside = in;
      insideKnown = true;
      guiding = !in;
      // the distance as the card and the Handbook count it (to the middle of the broad circle), rounded more the farther it is
      int metres = (int)Math.max(0, Math.round(dist - r * 0.5));
      metres = metres < 50 ? metres : metres < 500 ? (metres + 2) / 5 * 5 : (metres + 5) / 10 * 10;
      if (metres != shownMetres || distText == null) {
         shownMetres = metres;
         distText = FrontierUi.c(I18n.get("firsthunt.frontierhunts.hud.distance", metres), FrontierUi.Size.SMALL).getVisualOrderText();
         distW = mc.font.width(distText);
      }
      String lang = mc.options.languageCode;
      if (!lang.equals(pointsLang) || caption == null) {
         pointsLang = lang;
         String[] keys = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
         for (int i = 0; i < 8; i++) {
            String label = I18n.get("firsthunt.frontierhunts.hud.point." + keys[i]);
            POINTS[i] = FrontierUi.c(label, i % 2 == 0 ? FrontierUi.Size.STRONG : FrontierUi.Size.SMALL).getVisualOrderText();
            POINT_W[i] = mc.font.width(POINTS[i]);
         }
         caption = FrontierUi.c(I18n.get("firsthunt.frontierhunts.hud.caption"), FrontierUi.Size.SMALL).getVisualOrderText();
         captionW = mc.font.width(caption);
         if (arrivedStep != null) {
            arriveText(arrivedStep);
         }
      }
   }

   /** [phone] One tick of guiding to a Field Phone place: the ribbon's marker, distance and "arrived" note. */
   private static void phoneTick(Minecraft mc) {
      if (!phoneMode) {
         insideKnown = false;
      }
      phoneMode = true;
      double r = 6.0;
      targetX = phoneX;
      targetZ = phoneZ;
      double dist = Math.hypot(targetX - mc.player.getX(), targetZ - mc.player.getZ());
      boolean in = inside ? dist <= r + 4.0 : dist <= r;
      long now = System.currentTimeMillis();
      if (in && (!inside || !insideKnown)) {
         arrivedAt = now;
         arrivedStep = null;
         arriveTitle = FrontierUi.c(I18n.get("phone.frontierhunts.nav.arrived"), FrontierUi.Size.STRONG).getVisualOrderText();
         arriveSub = FrontierUi.c(phoneLabel, FrontierUi.Size.SMALL).getVisualOrderText();
         arriveTitleW = mc.font.width(arriveTitle);
         arriveSubW = mc.font.width(arriveSub);
      }
      if (!in && !guiding) {
         ribbonSince = now;
      }
      inside = in;
      insideKnown = true;
      guiding = !in;
      int metres = (int)Math.max(0, Math.round(dist));
      metres = metres < 50 ? metres : metres < 500 ? (metres + 2) / 5 * 5 : (metres + 5) / 10 * 10;
      if (metres != shownMetres || distText == null) {
         shownMetres = metres;
         distText = FrontierUi.c(I18n.get("firsthunt.frontierhunts.hud.distance", metres), FrontierUi.Size.SMALL).getVisualOrderText();
         distW = mc.font.width(distText);
      }
      if (phoneCaption == null) {
         phoneCaption = FrontierUi.c(phoneLabel, FrontierUi.Size.SMALL).getVisualOrderText();
         phoneCaptionW = mc.font.width(phoneCaption);
      }
      if (POINTS[0] == null || caption == null) {
         String[] keys = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
         for (int i = 0; i < 8; i++) {
            String label = I18n.get("firsthunt.frontierhunts.hud.point." + keys[i]);
            POINTS[i] = FrontierUi.c(label, i % 2 == 0 ? FrontierUi.Size.STRONG : FrontierUi.Size.SMALL).getVisualOrderText();
            POINT_W[i] = mc.font.width(POINTS[i]);
         }
         pointsLang = mc.options.languageCode;
         caption = FrontierUi.c(I18n.get("firsthunt.frontierhunts.hud.caption"), FrontierUi.Size.SMALL).getVisualOrderText();
         captionW = mc.font.width(caption);
      }
   }

   private static void arriveText(FirstHunt.Step step) {
      Minecraft mc = Minecraft.getInstance();
      arriveTitle = FrontierUi.c(I18n.get("firsthunt.frontierhunts.hud.inside"), FrontierUi.Size.STRONG).getVisualOrderText();
      arriveSub = FrontierUi.c(I18n.get("firsthunt.frontierhunts.hud.inside." + step.key()), FrontierUi.Size.SMALL).getVisualOrderText();
      arriveTitleW = mc.font.width(arriveTitle);
      arriveSubW = mc.font.width(arriveSub);
   }

   @SubscribeEvent
   public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e) {
      guiding = false;
      inside = false;
      insideKnown = false;
      lastAreaX = lastAreaZ = Integer.MIN_VALUE;
      arrivedAt = Long.MIN_VALUE;
      arrivedStep = null;
      phoneOn = phoneMode = false; // [phone]
      alpha = arriveAlpha = 0.0F;
      shownMetres = -1;
      distText = null;
   }

   // ============================================================================================ frame

   static void render(GuiGraphics g, DeltaTracker dt) {
      Minecraft mc = Minecraft.getInstance();
      long nanos = System.nanoTime();
      float step = lastNanos == 0L ? 0.016F : Math.min(0.1F, (nanos - lastNanos) / 1.0E9F);
      lastNanos = nanos;
      frameDt = step;
      boolean reduced = FirstHuntClient.reducedMotion();
      boolean hide = mc.player == null || ObjectiveCard.busy(mc) || !areaShown(FirstHuntClient.state) && !phoneMode; // [phone]
      float k = reduced ? 1.0F : 1.0F - (float)Math.exp(-step * 8.0F);
      alpha += ((!hide && guiding ? 1.0F : 0.0F) - alpha) * k;
      long now = System.currentTimeMillis();
      boolean arriving = inside && now - arrivedAt < ARRIVE_MS && arriveTitle != null;
      arriveAlpha += ((!hide && arriving ? 1.0F : 0.0F) - arriveAlpha) * (reduced ? 1.0F : 1.0F - (float)Math.exp(-step * (arriving ? 9.0F : 4.0F)));
      if (alpha < 0.01F) {
         alpha = 0.0F;
      }
      if (arriveAlpha < 0.01F) {
         arriveAlpha = 0.0F;
      }
      if (mc.player == null || mc.options.hideGui) {
         return;
      }
      if (alpha > 0.0F && distText != null && POINTS[0] != null) {
         ribbon(g, mc);
      }
      if (arriveAlpha > 0.0F && arriveTitle != null) {
         arrival(g, mc);
      }
   }

   /** heading the camera faces, degrees clockwise from north */
   static float heading(Minecraft mc) {
      return Mth.wrapDegrees(mc.gameRenderer.getMainCamera().getYRot() + 180.0F);
   }

   private static void ribbon(GuiGraphics g, Minecraft mc) {
      Font font = mc.font;
      float s = (float)mc.getWindow().getGuiScale();
      float cx = g.guiWidth() / 2.0F;
      float a = smooth(alpha);
      float head = heading(mc);
      double dx = targetX - mc.player.getX(), dz = targetZ - mc.player.getZ();
      float bearing = (float)Math.toDegrees(Math.atan2(dx, -dz));
      float rel = Mth.wrapDegrees(bearing - head);
      // which end the marker sits at when it is out of view (kept while it's roughly behind, so it doesn't flicker)
      if (Math.abs(rel) < 150.0F) {
         edgeSide = rel >= 0.0F ? 1 : -1;
      }
      boolean clamped = Math.abs(rel) > HALF_FOV - 6.0F;
      frameG = g;
      frameS = s;
      frameCx = cx;
      frameRel = rel;
      frameA = a;
      frameClamped = clamped;
      // the band and its ticks (fills), at the screen's own pixels
      pixels(g, s);
      FrontierUi.batch(g, BAND_FILLS);
      unpixels(g);
      // the compass points (a letter under the marker steps back so the marker reads cleanly)
      float ppd = (RW / 2.0F - 8.0F) / HALF_FOV;
      float mx = markerX(cx, rel);
      for (int i = 0; i < 8; i++) {
         float r = Mth.wrapDegrees(i * 45.0F - head);
         if (Math.abs(r) > HALF_FOV - 2.0F) {
            continue;
         }
         float x = cx + r * ppd;
         float ta = taper(x - cx) * a;
         if (!clamped) {
            ta *= Mth.clamp((Math.abs(x - mx) - 5.0F) / 7.0F, 0.15F, 1.0F);
         }
         if (ta < 0.04F) {
            continue;
         }
         boolean major = i % 2 == 0;
         int c = major ? (i == 0 ? CREAM : 0xFFE2DCCB) : MUTED;
         float ty = major ? TOP + 2.5F : TOP + 4.0F;
         g.drawString(font, POINTS[i], x - POINT_W[i] / 2.0F, ty, fade(c, ta * (major ? 1.0F : 0.85F)), false);
      }
      // under the marker, one small pill: the distance, and "· Beginner area" while the hunter faces it (and for the first seconds)
      // shown while facing it (within 10 degrees, until past 15) and for the ribbon's first 6 seconds; it slides open and shut
      captionOn = Math.abs(rel) <= (captionOn ? 15.0F : 10.0F) || System.currentTimeMillis() - ribbonSince < 6000L;
      captionT += ((captionOn ? 1.0F : 0.0F) - captionT) * (FirstHuntClient.reducedMotion() ? 1.0F : 1.0F - (float)Math.exp(-frameDt * 12.0F));
      float capA = captionT < 0.01F ? 0.0F : smooth(captionT);
      int capW = phoneMode && phoneCaption != null ? phoneCaptionW : captionW; // [phone]
      float w = distW + 8.0F + capA * (CAP_GAP + capW);
      float ty = TOP + RH + 3.0F;
      float lx = Mth.clamp(mx - w / 2.0F, cx - RW / 2.0F + 4.0F, cx + RW / 2.0F - 4.0F - w);
      frameLx = lx;
      frameTy = ty;
      frameW = w;
      frameCapA = capA;
      pixels(g, s);
      FrontierUi.batch(g, MARKER_FILLS);
      unpixels(g);
      g.drawString(font, distText, lx + 4.0F, ty + 1.5F, fade(GOLD, a), false);
      if (capA > 0.05F) {
         g.drawString(font, phoneMode && phoneCaption != null ? phoneCaption : caption, lx + 4.0F + distW + CAP_GAP, ty + 1.5F, // [phone]
            fade(CREAM, a * capA * capA * capA), false);
      }
   }

   static float markerX(float cx, float rel) {
      float ppd = (RW / 2.0F - 8.0F) / HALF_FOV;
      if (Math.abs(rel) > HALF_FOV - 6.0F) {
         return cx + edgeSide * (RW / 2.0F - 7.0F);
      }
      return cx + rel * ppd;
   }

   /** 0 at the ribbon's ends, 1 in the middle (a soft edge, as if it fades into the sky) */
   static float taper(float fromCentre) {
      float d = RW / 2.0F - Math.abs(fromCentre);
      return smooth(d / TAPER);
   }

   /** the band: a soft dark strip that fades at both ends, hairlines top and bottom, minor ticks every 15 degrees, a centre notch */
   private static void bandFills() {
      GuiGraphics g = frameG;
      float s = frameS, cx = frameCx, a = frameA;
      int x0 = px(cx - RW / 2.0F, s), x1 = px(cx + RW / 2.0F, s);
      int y0 = px(TOP, s), y1 = px(TOP + RH, s);
      int slice = Math.max(1, Math.round(s));
      for (int x = x0; x < x1; x += slice) {
         float t = taper((x + slice * 0.5F) / s - cx);
         if (t <= 0.0F) {
            continue;
         }
         int w = Math.min(slice, x1 - x);
         g.fill(x, y0, x + w, y1, fade(BAND, a * t));
         g.fill(x, y0, x + w, y0 + 1, fade(0x55EFE8D7, a * t));
         g.fill(x, y1 - 1, x + w, y1, fade(0x40D6A94A, a * t));
      }
      // minor ticks every 15 degrees (the 45s carry letters)
      float head = heading(Minecraft.getInstance());
      float ppd = (RW / 2.0F - 8.0F) / HALF_FOV;
      int tw = Math.max(1, Math.round(s * 0.5F));
      for (int i = 0; i < 24; i++) {
         if (i % 3 == 0) {
            continue;
         }
         float r = Mth.wrapDegrees(i * 15.0F - head);
         if (Math.abs(r) > HALF_FOV) {
            continue;
         }
         float x = cx + r * ppd;
         float ta = taper(x - cx) * a;
         if (ta < 0.03F) {
            continue;
         }
         int tx = px(x, s) - tw / 2;
         g.fill(tx, px(TOP + RH - 4.0F, s), tx + tw, px(TOP + RH - 1.5F, s), fade(0xB0EFE8D7, ta));
      }
      // the centre: a small cream notch hanging from the top edge
      for (int row = 0; row < Math.round(2.5F * s); row++) {
         int half = Math.max(0, Math.round((2.5F * s - row) * 0.8F));
         int xc = px(cx, s);
         g.fill(xc - half, y0 + row, xc + half + 1, y0 + row + 1, fade(0xE6EFE8D7, a));
      }
   }

   /** the gold marker on the band: a diamond with a dark rim, or an arrow at the ribbon's end when out of view */
   private static void markerFills() {
      GuiGraphics g = frameG;
      float s = frameS, a = frameA;
      float mx = markerX(frameCx, frameRel);
      float my = TOP + RH / 2.0F;
      if (frameClamped) {
         // an arrow pointing out of the ribbon toward the side to turn to
         float h = 5.0F, depth = 5.5F;
         int dir = edgeSide;
         arrow(g, s, mx, my, h + 1.1F, depth + 1.4F, dir, fade(INK, a * 0.9F));
         arrow(g, s, mx, my, h, depth, dir, fade(GOLD, a));
      } else {
         diamond(g, s, mx, my, 5.2F, fade(INK, a * 0.9F));
         diamond(g, s, mx, my, 4.1F, fade(GOLD, a));
         diamond(g, s, mx, my, 1.5F, fade(INK, a));
      }
      // a dark pill behind the distance (and the caption), so it reads against a bright sky; a gold dot between the two
      pill(g, s, frameLx, frameTy - 1.5F, frameW, 10.0F, fade(0xB80E130F, a));
      if (frameCapA > 0.05F) {
         float dx = frameLx + 4.0F + distW + CAP_GAP / 2.0F, dy = frameTy + 4.0F;
         g.fill(px(dx - 0.6F, s), px(dy - 0.6F, s), px(dx + 0.6F, s) + 1, px(dy + 0.6F, s) + 1, fade(0xC0D6A94A, a * frameCapA));
      }
   }

   /** a filled diamond of half-size h (GUI px), row by row at screen pixels */
   static void diamond(GuiGraphics g, float s, float cx, float cy, float h, int color) {
      int rows = Math.max(1, Math.round(h * s));
      int xc = px(cx, s), yc = px(cy, s);
      for (int r = -rows; r <= rows; r++) {
         int half = rows - Math.abs(r);
         g.fill(xc - half, yc + r, xc + half + 1, yc + r + 1, color);
      }
   }

   /** a filled triangle pointing {@code dir} (+1 right, -1 left): tip at cx + dir*depth/2, half-height h */
   static void arrow(GuiGraphics g, float s, float cx, float cy, float h, float depth, int dir, int color) {
      int rows = Math.max(1, Math.round(h * s));
      int base = px(cx - dir * depth / 2.0F, s), yc = px(cy, s);
      float d = depth * s;
      for (int r = -rows; r <= rows; r++) {
         int len = Math.round(d * (1.0F - Math.abs(r) / (float)(rows + 1)));
         if (dir > 0) {
            g.fill(base, yc + r, base + len, yc + r + 1, color);
         } else {
            g.fill(base - len, yc + r, base, yc + r + 1, color);
         }
      }
   }

   /** a small rounded pill behind a line of text */
   static void pill(GuiGraphics g, float s, float x, float y, float w, float h, int color) {
      int x0 = px(x, s), y0 = px(y, s), x1 = px(x + w, s), y1 = px(y + h, s);
      int r = Math.max(1, Math.round(s * 1.5F));
      g.fill(x0 + r, y0, x1 - r, y0 + 1, color);
      g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + r, color);
      g.fill(x0, y0 + r, x1, y1 - r, color);
      g.fill(x0 + 1, y1 - r, x1 - 1, y1 - 1, color);
      g.fill(x0 + r, y1 - 1, x1 - r, y1, color);
   }

   // ---- the arrival note

   private static void arrival(GuiGraphics g, Minecraft mc) {
      float s = (float)mc.getWindow().getGuiScale();
      float cx = g.guiWidth() / 2.0F;
      float a = smooth(arriveAlpha);
      float w = Math.max(arriveTitleW, arriveSubW) + 34.0F;
      float y = TOP + (1.0F - a) * -4.0F;
      frameG = g;
      frameS = s;
      frameCx = cx;
      frameA = a;
      frameW = w;
      pixels(g, s);
      FrontierUi.batch(g, ARRIVE_FILLS);
      unpixels(g);
      float tx = cx - w / 2.0F + 26.0F;
      g.drawString(mc.font, arriveTitle, tx, y + 3.5F, fade(CREAM, a), false);
      g.drawString(mc.font, arriveSub, tx, y + 14.5F, fade(MUTED, a), false);
   }

   private static void arriveFills() {
      GuiGraphics g = frameG;
      float s = frameS, cx = frameCx, a = frameA, w = frameW;
      float y = TOP + (1.0F - a) * -4.0F;
      float x = cx - w / 2.0F;
      pill(g, s, x, y, w, 24.0F, fade(0xD8161D17, a));
      // a gold rule on the left, like the card
      g.fill(px(x + 1.0F, s), px(y + 3.0F, s), px(x + 3.5F, s), px(y + 21.0F, s), fade(GOLD, a));
      // the area mark: a gold ring with a dot (you're in it)
      float ox = x + 14.0F, oy = y + 12.0F;
      ring(g, s, ox, oy, 6.0F, 1.4F, fade(GOLD, a));
      diamond(g, s, ox, oy, 2.2F, fade(GOLD, a));
   }

   /** a ring of radius r and thickness t (GUI px), row spans at screen pixels */
   static void ring(GuiGraphics g, float s, float cx, float cy, float r, float t, int color) {
      float ro = r * s, ri = (r - t) * s;
      int xc = px(cx, s), yc = px(cy, s);
      int n = (int)Math.ceil(ro);
      for (int dy = -n; dy <= n; dy++) {
         float yy = dy + 0.5F;
         if (Math.abs(yy) > ro) {
            continue;
         }
         int outer = (int)Math.floor(Math.sqrt(ro * ro - yy * yy));
         int inner = Math.abs(yy) < ri ? (int)Math.ceil(Math.sqrt(ri * ri - yy * yy)) : -1;
         if (inner < 0) {
            g.fill(xc - outer, yc + dy, xc + outer, yc + dy + 1, color);
         } else {
            g.fill(xc - outer, yc + dy, xc - inner, yc + dy + 1, color);
            g.fill(xc + inner, yc + dy, xc + outer, yc + dy + 1, color);
         }
      }
   }

   // ============================================================================================ helpers

   /** switch the pose to screen pixels (in place: no new pose) */
   private static void pixels(GuiGraphics g, float s) {
      Matrix4f m = g.pose().last().pose();
      SAVED.set(m);
      m.scale(1.0F / s, 1.0F / s, 1.0F);
   }

   private static void unpixels(GuiGraphics g) {
      g.pose().last().pose().set(SAVED);
   }

   static int px(float guiX, float s) {
      return Math.round(guiX * s);
   }

   static float smooth(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   static int fade(int argb, float f) {
      int a = (int)((argb >>> 24) * Mth.clamp(f, 0.0F, 1.0F));
      return a << 24 | argb & 0x00FFFFFF;
   }
}
