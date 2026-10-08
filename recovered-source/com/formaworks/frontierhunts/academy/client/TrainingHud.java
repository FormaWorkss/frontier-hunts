package com.formaworks.frontierhunts.academy.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.academy.AcademyConfig;
import com.formaworks.frontierhunts.academy.AcademyNetwork;
import com.formaworks.frontierhunts.academy.AcademySounds;
import com.formaworks.frontierhunts.academy.Course;
import com.formaworks.frontierhunts.client.AssignmentScreen;
import com.formaworks.frontierhunts.client.ExpeditionOptics;
import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.client.KillCamClient;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * [academy] Everything the hunter sees while training: the field card (course, objectives with live bars, timer,
 * wind, hold-to-leave), coaching lines, the arrival title card, the departure / return fades, the course-passed
 * moment and the result card back home. Vanilla GUI drawing only (shader independent), all timed on the real clock.
 */
final class TrainingHud {
   static final int GOLD = 0xFFD8B46A;
   static final int GOLD_DEEP = 0xFFB99859;
   static final int PAPER = 0xFFF1E8D2;
   static final int MUTED = 0xFFB9B09A;
   static final int GREEN = 0xFF8FC27A;
   static final int RED = 0xFFE07A62;
   static final int AMBER = 0xFFE8B04A;
   private static final int CARD_W = 204;

   // fades
   private static float fade;
   private static float fadeFrom;
   private static float fadeTo;
   private static long fadeStart;
   private static long fadeMs = 1;
   private static long blackSince = -1L;
   private static String fadeLine = "";
   // cards
   private static int titleCourse = -1;
   private static long titleAt;
   private static int passCourse = -1;
   private static long passAt;
   private static int passTicks;
   private static boolean passBest;
   private static String failKey = "";
   private static long failAt;
   private static long homeAt;
   private static final List<String> homeLines = new ArrayList<>();
   private static String homeTitle = "";
   private static int homeResult;
   // lines
   private static String banner = "";
   private static int bannerColor = GREEN;
   private static long bannerAt;
   private static String note = "";
   private static int noteColor = PAPER;
   private static long noteAt;
   private static int pulseObjective = -1;
   private static long pulseAt;
   private static long dipAt = -1L;
   private static final List<long[]> SOUNDS = new ArrayList<>();
   private static final List<float[]> SPARKS = new ArrayList<>();
   private static long lastNanos;

   private TrainingHud() {
   }

   static void reset() {
      fade = fadeFrom = fadeTo = 0.0F;
      blackSince = -1L;
      titleCourse = passCourse = -1;
      failKey = "";
      homeAt = 0L;
      banner = note = "";
      SOUNDS.clear();
      SPARKS.clear();
      dipAt = -1L;
   }

   static String tr(String key, Object... args) {
      return key == null || key.isEmpty() ? "" : I18n.get(key, args);
   }

   static boolean reduced() {
      try {
         return HuntConfig.REDUCED_MOTION.get();
      } catch (RuntimeException ex) {
         return false;
      }
   }

   private static void play(SoundEvent e, float volume, float pitch) {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, volume));
   }

   private static void fadeTo(float target, long ms) {
      fadeFrom = currentFade();
      fadeTo = target;
      fadeStart = System.currentTimeMillis();
      fadeMs = Math.max(1L, ms);
   }

   /** [regions] True while an academy fade or the back-home results card is up (arrival cards wait for it). */
   static boolean showing() {
      return homeAt > 0L && System.currentTimeMillis() - homeAt < 9000L || currentFade() > 0.01F;
   }

   private static float currentFade() {
      float t = Mth.clamp((System.currentTimeMillis() - fadeStart) / (float)fadeMs, 0.0F, 1.0F);
      float s = t * t * (3.0F - 2.0F * t);
      return fadeFrom + (fadeTo - fadeFrom) * s;
   }

   // ================================================================================================ cues

   static void cue(AcademyNetwork.Cue c) {
      Minecraft mc = Minecraft.getInstance();
      long now = System.currentTimeMillis();
      boolean cinematic = AcademyConfig.cinematic();
      Object[] args = c.args().toArray();
      switch (c.kind()) {
         case AcademyNetwork.C_DEPART -> {
            fadeLine = c.course() >= 0 && !c.key().isEmpty() ? tr("academy.frontierhunts.hud.heading", tr(c.key())) : tr("academy.frontierhunts.hud.returning");
            fadeTo(1.0F, cinematic ? Math.max(200L, c.value() * 50L - 60L) : 120L);
            blackSince = now;
            play(AcademySounds.DEPART, 0.7F, 1.0F);
         }
         case AcademyNetwork.C_ARRIVE -> {
            fadeTo(0.0F, cinematic ? 1300L : 200L);
            blackSince = -1L;
            titleCourse = c.course();
            titleAt = now;
            passCourse = -1;
            failKey = "";
            homeAt = 0L;
            SPARKS.clear();
            play(AcademySounds.ARRIVE, 0.85F, 1.0F);
            if (mc.screen instanceof AssignmentScreen) {
               mc.setScreen(null);
            }
         }
         case AcademyNetwork.C_PASSED -> {
            passCourse = c.course();
            passAt = now;
            passTicks = c.value();
            passBest = !c.args().isEmpty() && "1".equals(c.args().get(0));
            titleCourse = -1;
            spawnSparks();
            play(AcademySounds.PASSED, 0.9F, 1.0F);
         }
         case AcademyNetwork.C_FAILED -> {
            failKey = c.key();
            failAt = now;
            titleCourse = -1;
            play(AcademySounds.BUSTED, 0.8F, 0.8F);
         }
         case AcademyNetwork.C_HOME -> {
            fadeTo(0.0F, cinematic ? 1100L : 200L);
            blackSince = -1L;
            titleCourse = -1;
            passCourse = -1;
            failKey = "";
            SPARKS.clear();
            AcademyClient.clearHud();
            home(c);
            if (!homeLines.isEmpty()) {
               play(AcademySounds.PAGE, 0.8F, 1.0F);
            }
         }
         case AcademyNetwork.C_TICK -> {
            banner = tr(c.key(), args);
            bannerColor = GOLD;
            bannerAt = now;
            pulseObjective = c.value();
            pulseAt = now;
            play(AcademySounds.TICK, 0.8F, 1.0F);
         }
         case AcademyNetwork.C_NOTE -> {
            note = tr(c.key(), args);
            noteColor = PAPER;
            noteAt = now;
         }
         case AcademyNetwork.C_BUSTED -> {
            note = tr(c.key(), args);
            noteColor = RED;
            noteAt = now;
            dipAt = now + 2600L;
            play(AcademySounds.BUSTED, 0.9F, 1.0F);
         }
         case AcademyNetwork.C_REFUSED -> {
            String text = tr(c.key(), args);
            if (mc.screen instanceof AssignmentScreen screen) {
               screen.toast(text);
            } else {
               note = text;
               noteColor = AMBER;
               noteAt = now;
            }
            if (fadeTo > 0.0F) {
               fadeTo(0.0F, 300L);
               blackSince = -1L;
            }
         }
         case AcademyNetwork.C_RING -> {
            int metres = 30;
            try {
               metres = c.args().isEmpty() ? 30 : Integer.parseInt(c.args().get(0));
            } catch (NumberFormatException ignored) {
            }
            float vol = Mth.clamp(1.15F - metres / 160.0F, 0.35F, 1.0F);
            SOUNDS.add(new long[]{now + c.value() * 50L, "gold".equals(c.key()) ? 1 : 0, Float.floatToIntBits(vol)});
         }
         default -> {
         }
      }
   }

   private static void home(AcademyNetwork.Cue c) {
      homeLines.clear();
      homeResult = c.value();
      List<String> a = c.args();
      if (c.course() < 0) {
         if (!c.key().isEmpty()) {
            homeTitle = tr("academy.frontierhunts.home.title");
            homeLines.add(tr(c.key()));
            homeAt = System.currentTimeMillis();
         }
         return;
      }
      Course course = Course.byId(c.course());
      homeTitle = course == null ? "" : tr(course.lang("title"));
      homeAt = System.currentTimeMillis();
      int ranger = a.size() > 0 ? parse(a.get(0)) : 0;
      int skill = a.size() > 1 ? parse(a.get(1)) : 0;
      boolean cert = a.size() > 2 && "1".equals(a.get(2));
      boolean best = a.size() > 3 && "1".equals(a.get(3));
      int ticks = a.size() > 4 ? parse(a.get(4)) : 0;
      switch (homeResult) {
         case 0 -> {
            homeLines.add(tr("academy.frontierhunts.home.passed", clock(ticks)) + (best ? "  ·  " + tr("academy.frontierhunts.home.best") : ""));
            if (ranger > 0 || skill > 0) {
               homeLines.add(tr("academy.frontierhunts.home.rewards", ranger, skill, course == null ? "" : tr("journal.frontierhunts.skill." + course.skill.key + ".name")));
            } else {
               homeLines.add(tr("academy.frontierhunts.home.practice"));
            }
            if (cert && course != null) {
               homeLines.add(tr("academy.frontierhunts.home.certified", tr("academy.frontierhunts.cert." + course.cert)));
            }
         }
         case 1 -> homeLines.add(tr("academy.frontierhunts.home.abandoned"));
         case 2 -> homeLines.add(tr("academy.frontierhunts.home.timeout"));
         case 3 -> homeLines.add(tr("academy.frontierhunts.home.hurt"));
         default -> homeLines.add(tr("academy.frontierhunts.home.interrupted"));
      }
      homeLines.add(tr("academy.frontierhunts.home.safe"));
   }

   private static int parse(String s) {
      try {
         return Integer.parseInt(s);
      } catch (NumberFormatException ex) {
         return 0;
      }
   }

   static String clock(int ticks) {
      int s = Math.max(0, ticks) / 20;
      return s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60);
   }

   static void hudChanged(AcademyNetwork.Hud before, AcademyNetwork.Hud now) {
      if (before != null && now != null && before.course() == now.course() && before.attempts() != now.attempts()) {
         pulseObjective = -1;
      }
   }

   private static void spawnSparks() {
      SPARKS.clear();
      if (reduced()) {
         return;
      }
      java.util.Random r = new java.util.Random();
      for (int i = 0; i < 46; i++) {
         float a = (float)(r.nextFloat() * Math.PI * 2);
         float sp = 40.0F + r.nextFloat() * 120.0F;
         SPARKS.add(new float[]{0.0F, 0.0F, (float)Math.cos(a) * sp, (float)Math.sin(a) * sp - 60.0F, 1.2F + r.nextFloat() * 1.3F, r.nextFloat() * 2.0F + 0.8F, 0.0F});
      }
   }

   static void tick(Minecraft mc) {
      long now = System.currentTimeMillis();
      for (Iterator<long[]> it = SOUNDS.iterator(); it.hasNext(); ) {
         long[] s = it.next();
         if (now >= s[0]) {
            it.remove();
            float vol = Float.intBitsToFloat((int)s[2]);
            play(s[1] == 1 ? AcademySounds.RING_GOLD : AcademySounds.RING, vol, 0.97F + (float)Math.random() * 0.06F);
         }
      }
      if (blackSince > 0L && now - blackSince > 9000L) {
         fadeTo(0.0F, 400L); // nothing came: never leave the player blind
         blackSince = -1L;
      }
   }

   // ================================================================================================ field card

   private static boolean busy(Minecraft mc) {
      return mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || mc.player == null || KillCamClient.active();
   }

   private static boolean optic(Minecraft mc) {
      Player p = mc.player;
      return p != null && (p.isScoping() || ExpeditionOptics.active() || p.isUsingItem() && p.getUseItem().getUseAnimation() == net.minecraft.world.item.UseAnim.SPYGLASS);
   }

   static void render(GuiGraphics g) {
      Minecraft mc = Minecraft.getInstance();
      if (!AcademyClient.training() || !AcademyConfig.hud() || busy(mc) || mc.screen != null) {
         return;
      }
      AcademyNetwork.Hud h = AcademyClient.hud();
      Course c = Course.byId(h.course());
      if (c == null) {
         return;
      }
      long now = System.currentTimeMillis();
      if (optic(mc)) {
         compact(g, mc, h, c, now);
      } else {
         card(g, mc, h, c, now);
      }
      lines(g, mc, now);
   }

   private static int focus(AcademyNetwork.Hud h, Course c) {
      for (int i = 0; i < c.targets.length && i < h.progress().length; i++) {
         if (h.progress()[i] < c.targets[i]) {
            return i;
         }
      }
      return -1;
   }

   private static void card(GuiGraphics g, Minecraft mc, AcademyNetwork.Hud h, Course c, long now) {
      int x = 6, y = 6, w = CARD_W;
      int pad = 8;
      int rows = c.targets.length;
      boolean wind = (h.flags() & 1) != 0;
      boolean offLine = (h.flags() & 2) != 0;
      int rowH = 14;
      int hgt = 36 + rows * rowH + 6 + (wind ? 14 : 0) + (offLine ? 14 : 0) + (h.attempts() > 0 ? 11 : 0) + 16;
      // body
      FrontierUi.shadow(g, x, y, w, hgt, 5.0F, 4.0F);
      FrontierUi.rect(g, x, y, w, hgt, 5.0F, 0xE0181A15);
      FrontierUi.rect(g, x, y, 3.0F, hgt, 1.5F, GOLD_DEEP);
      // header
      String eyebrow = tr("academy.frontierhunts.hud.eyebrow", c.step(), Course.count()); // [onboard] curriculum position
      FrontierUi.text(g, eyebrow, x + pad, y + 6, GOLD, FrontierUi.Size.SMALL);
      int rem = AcademyClient.remaining();
      String t = clock(rem);
      int tc = rem < 400 ? (rem < 20 * 20 && (now / 350L) % 2 == 0 ? RED : AMBER) : PAPER;
      if (h.phase() != AcademyNetwork.P_ACTIVE) {
         tc = MUTED;
      }
      DossierArt.icon(g, DossierArt.Icon.CLOCK, x + w - pad - FrontierUi.width(t, FrontierUi.Size.STRONG) - 12, y + 5, 10, tc);
      FrontierUi.right(g, t, x + w - pad, y + 6, tc, FrontierUi.Size.STRONG);
      FrontierUi.text(g, FrontierUi.fit(tr(c.lang("title")), w - pad * 2, FrontierUi.Size.STRONG), x + pad, y + 17, PAPER, FrontierUi.Size.STRONG);
      g.fill(x + pad, y + 30, x + w - pad, y + 31, 0x44D8B46A);
      // objectives
      int fy = y + 35;
      int f = focus(h, c);
      for (int i = 0; i < rows; i++) {
         int p = i < h.progress().length ? h.progress()[i] : 0;
         int target = c.targets[i];
         boolean done = p >= target;
         boolean current = i == f;
         float pulse = i == pulseObjective ? Math.max(0.0F, 1.0F - (now - pulseAt) / 1200.0F) : 0.0F;
         if (pulse > 0.0F) {
            FrontierUi.rect(g, x + 4, fy - 2, w - 8, rowH, 3.0F, alpha(0x66D8B46A, pulse));
         } else if (current) {
            FrontierUi.rect(g, x + 4, fy - 2, w - 8, rowH, 3.0F, 0x22F1E8D2);
         }
         // box
         if (done) {
            FrontierUi.rect(g, x + pad, fy + 1, 8, 8, 2.0F, GOLD_DEEP);
            DossierArt.icon(g, DossierArt.Icon.CHECK, x + pad - 1, fy, 10, 0xFF1A1A14);
         } else {
            FrontierUi.outline(g, x + pad, fy + 1, 8, 8, 2.0F, current ? GOLD : 0xFF6F6A5C, 0xFF181A15);
         }
         String label = tr(c.objectiveLang(i));
         String count = target > 1 ? p + "/" + target : (done ? "" : "");
         int cw = count.isEmpty() ? 0 : FrontierUi.width(count, FrontierUi.Size.SMALL) + 4;
         int col = done ? MUTED : (current ? PAPER : 0xFFD9D1BE);
         FrontierUi.text(g, FrontierUi.fit(label, w - pad * 2 - 14 - cw, FrontierUi.Size.SMALL), x + pad + 13, fy + 1, col, FrontierUi.Size.SMALL);
         if (!count.isEmpty()) {
            FrontierUi.right(g, count, x + w - pad, fy + 1, done ? GOLD : PAPER, FrontierUi.Size.SMALL);
         }
         if (done) {
            int lw = FrontierUi.width(FrontierUi.fit(label, w - pad * 2 - 14 - cw, FrontierUi.Size.SMALL), FrontierUi.Size.SMALL);
            g.fill(x + pad + 13, fy + 4, x + pad + 13 + lw, fy + 5, 0x88B9B09A);
         } else if (target > 1) {
            float frac = (float)p / target;
            FrontierUi.rect(g, x + pad + 13, fy + 10, w - pad * 2 - 13, 1.6F, 0.8F, 0x33F1E8D2);
            FrontierUi.rect(g, x + pad + 13, fy + 10, (w - pad * 2 - 13) * frac, 1.6F, 0.8F, GOLD);
         }
         fy += rowH;
      }
      fy += 2;
      if (wind) {
         wind(g, mc, h, x + pad, fy, w - pad * 2);
         fy += 14;
      }
      if (offLine) {
         FrontierUi.rect(g, x + 4, fy - 2, w - 8, 12, 3.0F, 0x88A5281F);
         DossierArt.icon(g, DossierArt.Icon.ALERT, x + pad, fy - 1, 10, 0xFFFFE0D0);
         FrontierUi.text(g, tr("academy.frontierhunts.hud.off_line"), x + pad + 13, fy, 0xFFFFE6DC, FrontierUi.Size.SMALL);
         fy += 14;
      }
      if (h.attempts() > 0) {
         FrontierUi.text(g, tr("academy.frontierhunts.hud.attempts", h.attempts() + 1), x + pad, fy, MUTED, FrontierUi.Size.SMALL);
         fy += 11;
      }
      // footer: hold to leave
      g.fill(x + pad, fy, x + w - pad, fy + 1, 0x22F1E8D2);
      String key = AcademyClient.LEAVE.getTranslatedKeyMessage().getString();
      String foot = tr("academy.frontierhunts.hud.leave", key);
      float hold = AcademyClient.leaveHold / (float)AcademyClient.LEAVE_HOLD;
      FrontierUi.text(g, FrontierUi.fit(foot, w - pad * 2 - 14, FrontierUi.Size.SMALL), x + pad, fy + 4, hold > 0.0F ? AMBER : MUTED, FrontierUi.Size.SMALL);
      ring(g, x + w - pad - 5, fy + 8, 4.5F, hold);
   }

   /** Compact strip at the top while looking through optics. */
   private static void compact(GuiGraphics g, Minecraft mc, AcademyNetwork.Hud h, Course c, long now) {
      int f = focus(h, c);
      String obj = f < 0 ? tr("academy.frontierhunts.hud.all_done") : tr(c.objectiveLang(f)) + (c.targets[f] > 1 ? "  " + h.progress()[f] + "/" + c.targets[f] : "");
      String t = clock(AcademyClient.remaining());
      String s = obj + "   " + t;
      int w = FrontierUi.width(s, FrontierUi.Size.SMALL) + 16;
      int x = (g.guiWidth() - w) / 2;
      FrontierUi.rect(g, x, 4, w, 13, 4.0F, 0xB0181A15);
      FrontierUi.text(g, s, x + 8, 7, PAPER, FrontierUi.Size.SMALL);
   }

   private static void wind(GuiGraphics g, Minecraft mc, AcademyNetwork.Hud h, int x, int y, int w) {
      double e = h.windEast(), s = h.windSouth();
      double speed = Math.hypot(e, s);
      String from = compass(-e, -s);
      // relative to the view: 0 = blowing straight ahead (tail wind)
      float yaw = mc.player.getYRot();
      double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
      double along = (e * fx + s * fz) / Math.max(1.0E-3, speed);
      String rel;
      if (speed < 0.6) {
         rel = tr("academy.frontierhunts.hud.wind_calm");
      } else if (along > 0.7) {
         rel = tr("academy.frontierhunts.hud.wind_tail");
      } else if (along < -0.7) {
         rel = tr("academy.frontierhunts.hud.wind_head");
      } else {
         // right-hand vector of the view (+x east, +z south): facing (fx, fz) -> right (-fz, fx)
         double right = e * -fz + s * fx;
         rel = tr(right > 0 ? "academy.frontierhunts.hud.wind_to_right" : "academy.frontierhunts.hud.wind_to_left");
      }
      // arrow: screen-up = forward, rotated by the wind's angle relative to the view
      double ang = Math.atan2(e * -fz + s * fx, e * fx + s * fz);
      float cx = x + 5, cy = y + 5;
      FrontierUi.circle(g, cx, cy, 5.5F, 0x33F1E8D2);
      g.pose().pushPose();
      g.pose().translate(cx, cy, 0.0F);
      g.pose().mulPose(Axis.ZP.rotation((float)ang));
      FrontierUi.rect(g, -0.7F, -3.6F, 1.4F, 7.0F, 0.6F, 0xFF9CC6E8);
      FrontierUi.rect(g, -2.4F, -3.8F, 4.8F, 1.3F, 0.6F, 0xFF9CC6E8);
      g.pose().popPose();
      String txt = speed < 0.6 ? rel : tr("academy.frontierhunts.hud.wind", String.format(Locale.ROOT, "%.1f", speed), from, rel);
      FrontierUi.text(g, FrontierUi.fit(txt, w - 14, FrontierUi.Size.SMALL), x + 13, y + 1, 0xFFCFE3F0, FrontierUi.Size.SMALL);
   }

   static String compass(double dx, double dz) {
      if (Math.abs(dx) + Math.abs(dz) < 1.0E-4) {
         return "-";
      }
      double deg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0) % 360.0; // 0 = north, 90 = east
      String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
      return tr("academy.frontierhunts.compass." + names[(int)Math.round(deg / 45.0) % 8].toLowerCase(Locale.ROOT));
   }

   private static void ring(GuiGraphics g, float cx, float cy, float r, float frac) {
      int n = 20;
      for (int i = 0; i < n; i++) {
         double a = -Math.PI / 2 + i * Math.PI * 2 / n;
         boolean on = i < Math.round(frac * n);
         FrontierUi.circle(g, (float)(cx + Math.cos(a) * r), (float)(cy + Math.sin(a) * r), 0.8F, on ? AMBER : 0x44F1E8D2);
      }
   }

   private static void lines(GuiGraphics g, Minecraft mc, long now) {
      int cx = g.guiWidth() / 2;
      // objective banner: top centre
      float ba = window(now - bannerAt, 2600L, 180L, 500L);
      if (ba > 0.0F && !banner.isEmpty()) {
         int w = Math.min(g.guiWidth() - 40, FrontierUi.width(banner, FrontierUi.Size.STRONG) + 34);
         int x = cx - w / 2;
         int y = 18 + (int)((1.0F - Math.min(1.0F, (now - bannerAt) / 180.0F)) * -6.0F);
         FrontierUi.shadow(g, x, y, w, 18, 6.0F, 3.0F);
         FrontierUi.rect(g, x, y, w, 18, 6.0F, alpha(0xE8181A15, ba));
         FrontierUi.rect(g, x, y + 17, w, 1.2F, 0.6F, alpha(GOLD, ba));
         DossierArt.icon(g, DossierArt.Icon.CHECK, x + 6, y + 3, 12, alpha(GOLD, ba));
         FrontierUi.text(g, FrontierUi.fit(banner, w - 28, FrontierUi.Size.STRONG), x + 22, y + 5, alpha(PAPER, ba), FrontierUi.Size.STRONG);
      }
      // coaching line: above the hotbar
      float na = window(now - noteAt, 4200L, 150L, 700L);
      if (na > 0.0F && !note.isEmpty()) {
         List<FormattedCharSequence> ls = Minecraft.getInstance().font.split(FrontierUi.c(note, FrontierUi.Size.BODY), Math.min(320, g.guiWidth() - 60));
         int lh = 10;
         int bw = 0;
         for (FormattedCharSequence l : ls) {
            bw = Math.max(bw, mc.font.width(l));
         }
         int y = g.guiHeight() - 72 - ls.size() * lh;
         FrontierUi.rect(g, cx - bw / 2 - 8, y - 4, bw + 16, ls.size() * lh + 6, 4.0F, alpha(0xC0181A15, na));
         for (FormattedCharSequence l : ls) {
            g.drawString(mc.font, l, cx - mc.font.width(l) / 2, y, alpha(noteColor, na), false);
            y += lh;
         }
      }
   }

   /** 0..1 visibility of a timed element: fade in, hold, fade out. */
   static float window(long age, long total, long in, long out) {
      if (age < 0L || age > total) {
         return 0.0F;
      }
      if (age < in) {
         return age / (float)in;
      }
      if (age > total - out) {
         return (total - age) / (float)out;
      }
      return 1.0F;
   }

   static int alpha(int color, float a) {
      int al = (int)((color >>> 24) * Mth.clamp(a, 0.0F, 1.0F));
      return al << 24 | color & 0xFFFFFF;
   }

   // ================================================================================================ cinematic layer

   static void renderCinematic(GuiGraphics g) {
      Minecraft mc = Minecraft.getInstance();
      long now = System.currentTimeMillis();
      long nanos = System.nanoTime();
      float dt = lastNanos == 0L ? 0.016F : Math.min(0.1F, (nanos - lastNanos) / 1.0E9F);
      lastNanos = nanos;
      int W = g.guiWidth(), H = g.guiHeight();
      boolean hidden = mc.options.hideGui || KillCamClient.active();
      if (!hidden && titleCourse >= 0) {
         titleCard(g, W, H, now);
      }
      if (!hidden && passCourse >= 0) {
         passed(g, W, H, now, dt);
      }
      if (!hidden && !failKey.isEmpty()) {
         failed(g, W, H, now);
      }
      if (homeAt > 0L && mc.screen == null && !hidden) {
         homeCard(g, W, H, now);
      }
      // dip for a stalk reset
      float dip = dipAt > 0L ? window(now - dipAt, 900L, 300L, 450L) : 0.0F;
      if (dipAt > 0L && now - dipAt > 900L) {
         dipAt = -1L;
      }
      fade = currentFade();
      float black = Math.max(fade, dip);
      if (black > 0.004F) {
         g.pose().pushPose();
         g.pose().translate(0.0F, 0.0F, 400.0F);
         g.fill(0, 0, W, H, alpha(0xFF050505, black));
         if (fade > 0.6F && !fadeLine.isEmpty() && blackSince > 0L) {
            float ta = Mth.clamp((fade - 0.6F) / 0.4F, 0.0F, 1.0F);
            FrontierUi.center(g, fadeLine, W / 2.0F, H / 2.0F - 4, alpha(0xFFE9DFC8, ta), FrontierUi.Size.BODY);
            float dots = (now / 300L) % 4;
            for (int i = 0; i < 3; i++) {
               FrontierUi.circle(g, W / 2.0F - 6 + i * 6, H / 2.0F + 12, 1.2F, alpha(i < dots ? GOLD : 0xFF3A3A34, ta));
            }
         }
         g.pose().popPose();
      }
   }

   private static void titleCard(GuiGraphics g, int W, int H, long now) {
      Course c = Course.byId(titleCourse);
      long age = now - titleAt - 500L;
      float a = window(age, 8200L, 700L, 900L);
      if (c == null || a <= 0.0F) {
         if (age > 8200L) {
            titleCourse = -1;
         }
         return;
      }
      float cy = H * 0.36F;
      // a soft dark band behind the words
      int bandH = 112;
      for (int i = 0; i < 24; i++) {
         float k = 1.0F - Math.abs(i - 11.5F) / 12.0F;
         int y0 = (int)(cy - bandH / 2.0F + i * bandH / 24.0F);
         g.fill(0, y0, W, (int)(y0 + bandH / 24.0F + 1), alpha(0x99000000, a * k * k));
      }
      String kicker = tr("academy.frontierhunts.title.kicker", c.step(), Course.count()); // [onboard] curriculum position
      FrontierUi.center(g, spaced(kicker), W / 2.0F, cy - 40, alpha(GOLD, a), FrontierUi.Size.SMALL);
      String title = tr(c.lang("title")).toUpperCase(Locale.ROOT);
      float grow = reduced() ? 1.0F : 1.0F + 0.04F * (1.0F - Math.min(1.0F, age / 4000.0F));
      float scale = Math.min(2.4F, (W - 40) / (float)Math.max(1, FrontierUi.width(title, FrontierUi.Size.TITLE))) * grow;
      g.pose().pushPose();
      g.pose().translate(W / 2.0F, cy - 26, 0.0F);
      g.pose().scale(scale, scale, 1.0F);
      FrontierUi.center(g, title, 0.0F, 0.0F, alpha(0xFFF6EEDA, a), FrontierUi.Size.TITLE);
      g.pose().popPose();
      float ry = cy - 26 + 16 * scale + 2;
      float rw = Math.min(W - 60, 220) * Math.min(1.0F, Math.max(0.0F, age / 900.0F));
      FrontierUi.rect(g, W / 2.0F - rw / 2, ry, rw, 1.2F, 0.6F, alpha(GOLD, a));
      g.pose().pushPose();
      g.pose().translate(W / 2.0F, ry + 0.6F, 0.0F);
      g.pose().mulPose(Axis.ZP.rotationDegrees(45.0F));
      FrontierUi.rect(g, -2.5F, -2.5F, 5.0F, 5.0F, 0.5F, alpha(GOLD, a));
      g.pose().popPose();
      String tag = tr(c.lang("tagline"));
      List<FormattedCharSequence> tl = Minecraft.getInstance().font.split(FrontierUi.c(tag, FrontierUi.Size.BODY), Math.min(W - 60, 300));
      float y = ry + 8;
      for (FormattedCharSequence l : tl) {
         g.drawString(Minecraft.getInstance().font, l, (int)(W / 2.0F - Minecraft.getInstance().font.width(l) / 2.0F), (int)y, alpha(0xFFE6DCC4, a), false);
         y += 10;
      }
      float oa = a * Mth.clamp((age - 1600L) / 700.0F, 0.0F, 1.0F);
      if (oa > 0.0F) {
         y += 6;
         for (int i = 0; i < c.objectives.length; i++) {
            String o = (i + 1) + "  " + tr(c.objectiveLang(i));
            FrontierUi.center(g, o, W / 2.0F, y, alpha(0xFFCFC6B2, oa), FrontierUi.Size.SMALL);
            y += 9;
         }
         y += 4;
         String foot = tr("academy.frontierhunts.title.foot", clock(c.timeLimit), AcademyClient.LEAVE.getTranslatedKeyMessage().getString());
         FrontierUi.center(g, foot, W / 2.0F, y, alpha(MUTED, oa), FrontierUi.Size.SMALL);
      }
   }

   private static String spaced(String s) {
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < s.length(); i++) {
         b.append(s.charAt(i));
         if (i < s.length() - 1 && s.charAt(i) != ' ') {
            b.append(' ');
         }
      }
      return b.toString();
   }

   private static void passed(GuiGraphics g, int W, int H, long now, float dt) {
      long age = now - passAt;
      float a = window(age, 4300L, 250L, 600L);
      if (a <= 0.0F) {
         if (age > 4300L) {
            passCourse = -1;
            SPARKS.clear();
         }
         return;
      }
      // warm vignette
      int edge = alpha(0x70B07A20, a);
      g.fillGradient(0, 0, W, H / 3, edge, 0x00000000);
      g.fillGradient(0, H - H / 3, W, H, 0x00000000, edge);
      float cx = W / 2.0F, cy = H * 0.38F;
      // sparks
      for (float[] s : SPARKS) {
         s[6] += dt;
         if (s[6] > s[4]) {
            continue;
         }
         s[0] += s[2] * dt;
         s[1] += s[3] * dt;
         s[3] += 70.0F * dt;
         s[2] *= 1.0F - 0.9F * dt;
         float life = 1.0F - s[6] / s[4];
         FrontierUi.circle(g, cx + s[0], cy + s[1], s[5] * 0.6F, alpha(0xFFFFE3A0, life * a));
      }
      // stamp
      float t = Math.min(1.0F, age / 380.0F);
      float pop = reduced() ? 1.0F : 1.0F + 0.55F * (float)Math.pow(1.0F - t, 3.0) - 0.06F * (float)Math.sin(t * Math.PI);
      String word = tr("academy.frontierhunts.passed.stamp");
      float sw = FrontierUi.width(word, FrontierUi.Size.TITLE);
      float scale = Math.min(2.6F, (W - 60) / Math.max(1.0F, sw + 24)) * pop;
      g.pose().pushPose();
      g.pose().translate(cx, cy, 0.0F);
      g.pose().mulPose(Axis.ZP.rotationDegrees(-4.0F));
      g.pose().scale(scale, scale, 1.0F);
      float bw = sw + 20, bh = 24;
      FrontierUi.rect(g, -bw / 2 - 2, -bh / 2 - 2, bw + 4, bh + 4, 3.0F, alpha(0xCC1A140C, a));
      FrontierUi.outline(g, -bw / 2, -bh / 2, bw, bh, 2.0F, alpha(GOLD, a), alpha(0x00000000, a));
      FrontierUi.outline(g, -bw / 2 + 2, -bh / 2 + 2, bw - 4, bh - 4, 1.5F, alpha(0x99D8B46A, a), alpha(0xCC1A140C, a));
      FrontierUi.center(g, word, 0.0F, -6.0F, alpha(0xFFF2D592, a), FrontierUi.Size.TITLE);
      // shimmer sweep
      float sweep = ((age % 1600L) / 1600.0F) * (bw + 40) - bw / 2 - 20;
      g.enableScissor((int)(cx - bw * scale / 2), (int)(cy - bh * scale / 2), (int)(cx + bw * scale / 2), (int)(cy + bh * scale / 2));
      FrontierUi.rect(g, sweep, -bh / 2, 6, bh, 0.0F, alpha(0x30FFFFFF, a));
      g.disableScissor();
      g.pose().popPose();
      Course c = Course.byId(passCourse);
      String sub = (c == null ? "" : tr(c.lang("title")) + "  ·  ") + clock(passTicks);
      FrontierUi.center(g, sub, cx, cy + 26 * Math.min(scale, 2.0F) / 2.0F + 10, alpha(0xFFF1E8D2, a), FrontierUi.Size.STRONG);
      if (passBest) {
         String best = tr("academy.frontierhunts.passed.best");
         float bw2 = FrontierUi.width(best, FrontierUi.Size.SMALL) + 12;
         float by = cy + 26 * Math.min(scale, 2.0F) / 2.0F + 24;
         FrontierUi.rect(g, cx - bw2 / 2, by, bw2, 11, 5.5F, alpha(GOLD_DEEP, a));
         FrontierUi.center(g, best, cx, by + 2, alpha(0xFF1A140C, a), FrontierUi.Size.SMALL);
      }
   }

   private static void failed(GuiGraphics g, int W, int H, long now) {
      long age = now - failAt;
      float a = window(age, 2600L, 200L, 500L);
      if (a <= 0.0F) {
         if (age > 2600L) {
            failKey = "";
         }
         return;
      }
      float cx = W / 2.0F, cy = H * 0.38F;
      String word = tr(failKey);
      float sw = FrontierUi.width(word, FrontierUi.Size.TITLE);
      float scale = Math.min(2.2F, (W - 60) / Math.max(1.0F, sw + 24));
      g.pose().pushPose();
      g.pose().translate(cx, cy, 0.0F);
      g.pose().mulPose(Axis.ZP.rotationDegrees(-3.0F));
      g.pose().scale(scale, scale, 1.0F);
      float bw = sw + 20, bh = 24;
      FrontierUi.rect(g, -bw / 2 - 2, -bh / 2 - 2, bw + 4, bh + 4, 3.0F, alpha(0xCC1A0E0C, a));
      FrontierUi.outline(g, -bw / 2, -bh / 2, bw, bh, 2.0F, alpha(0xFFC0614E, a), alpha(0x00000000, a));
      FrontierUi.center(g, word, 0.0F, -6.0F, alpha(0xFFF0B8A8, a), FrontierUi.Size.TITLE);
      g.pose().popPose();
   }

   private static void homeCard(GuiGraphics g, int W, int H, long now) {
      long age = now - homeAt - 600L;
      float a = window(age, 7000L, 400L, 800L);
      if (a <= 0.0F) {
         if (age > 7000L) {
            homeAt = 0L;
         }
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      int w = Math.min(W - 24, 260);
      List<FormattedCharSequence> all = new ArrayList<>();
      for (String l : homeLines) {
         all.addAll(mc.font.split(FrontierUi.c(l, FrontierUi.Size.SMALL), w - 24));
      }
      int h = 26 + all.size() * 9 + 6;
      int x = (W - w) / 2;
      int y = 22 + (int)((1.0F - Math.min(1.0F, Math.max(0.0F, age) / 400.0F)) * -8);
      FrontierUi.shadow(g, x, y, w, h, 6.0F, 4.0F);
      FrontierUi.rect(g, x, y, w, h, 6.0F, alpha(0xF0EAE3D0, a));
      FrontierUi.rect(g, x, y, w, 3.0F, 1.5F, alpha(homeResult == 0 ? GOLD_DEEP : 0xFF6A7266, a));
      DossierArt.icon(g, homeResult == 0 ? DossierArt.Icon.MEDAL : DossierArt.Icon.ACADEMY, x + 9, y + 7, 14, alpha(0xFFFFFFFF, a));
      FrontierUi.text(g, FrontierUi.fit(homeTitle, w - 40, FrontierUi.Size.STRONG), x + 28, y + 10, alpha(0xFF243A32, a), FrontierUi.Size.STRONG);
      int ly = y + 26;
      for (FormattedCharSequence l : all) {
         g.drawString(mc.font, l, x + 12, ly, alpha(0xFF3B2E20, a), false);
         ly += 9;
      }
   }
}
