package com.formaworks.frontierhunts.wingshot.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wingshot.WingshotConfig;
import com.formaworks.frontierhunts.wingshot.WingshotContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * [wingshot] The clean wing shot: for a moment time slows around the bird you dropped. A local time dilation, not a
 * world slowdown: the server and everything else run on; the bird's own fall is replayed from its real, server-driven
 * path on a slowed clock (and stays a fraction of a second behind until it is down), the feather burst and blood mist
 * hang in the air on the same clock, the view tightens a little and the edges darken. About 1.2 s; any shot after it
 * starts plays normally. Off with the setting, with Reduced motion, during the kill cam or a screen.
 */
public final class WingShotMoment {
   /** history of the bird's real positions while the moment runs (entity time -> position, down, water) */
   private static final int CAP = 2048;
   private static final float[] HT = new float[CAP];
   private static final double[] HX = new double[CAP], HY = new double[CAP], HZ = new double[CAP];
   private static final byte[] HF = new byte[CAP];
   private static int hn;

   private static WildlifeMob bird;
   private static long startNanos, lastNanos;
   /** seconds since the start, and how far (ticks) the replay lags the real bird */
   private static float t = -1.0F, lag;
   private static float endAt = Float.MAX_VALUE;
   private static long cooldownUntil;

   private WingShotMoment() {
   }

   public static boolean active() {
      return bird != null;
   }

   /** The server says this player's shot dropped a bird cleanly on the wing. */
   static void start(int entityId, float distance) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null || !WingshotConfig.slowMo() || reducedMotion() || mc.screen != null) {
         return;
      }
      if (com.formaworks.frontierhunts.client.KillCamClient.active()) {
         return;
      }
      long now = System.nanoTime();
      if (now < cooldownUntil || bird != null) {
         return;
      }
      Entity e = mc.level.getEntity(entityId);
      if (!(e instanceof WildlifeMob m) || !m.species.bird) {
         return;
      }
      bird = m;
      startNanos = now;
      lastNanos = now;
      t = 0.0F;
      lag = 0.0F;
      endAt = Float.MAX_VALUE;
      hn = 0;
      record(mc.getTimer().getGameTimeDeltaPartialTick(true));
      mc.getSoundManager().play(SimpleSoundInstance.forUI(WingshotContent.SLOWMO.get(), 1.0F, 0.8F));
   }

   private static boolean reducedMotion() {
      try {
         return HuntConfig.REDUCED_MOTION.get();
      } catch (Throwable x) {
         return false;
      }
   }

   /** time dilation of the moment's clock right now (1 = normal) */
   public static float scale() {
      if (bird == null) {
         return 1.0F;
      }
      return scaleAt(t);
   }

   static float scaleAt(float s) {
      if (s < 0.0F) {
         return 1.0F;
      }
      if (s < 0.05F) {
         return Mth.lerp(s / 0.05F, 1.0F, 0.2F);
      }
      if (s < 0.75F) {
         return Mth.lerp((s - 0.05F) / 0.7F, 0.2F, 0.3F);
      }
      if (s < 1.05F) {
         float u = (s - 0.75F) / 0.3F;
         return Mth.lerp(u * u * (3.0F - 2.0F * u), 0.3F, 1.0F);
      }
      return 1.0F;
   }

   /** 0..1 strength of the camera treatment (FOV, vignette) */
   public static float weight() {
      if (bird == null || t < 0.0F) {
         return 0.0F;
      }
      if (t < 0.06F) {
         return t / 0.06F;
      }
      if (t < 0.8F) {
         return 1.0F;
      }
      return Math.max(0.0F, 1.0F - (t - 0.8F) / 0.45F);
   }

   /** Once per frame (before anything renders): advance the clock, record the bird, end when it is down and caught up. */
   static void frame(float pt) {
      if (bird == null) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      long now = System.nanoTime();
      float dtSec = Math.min(0.1F, (now - lastNanos) / 1.0E9F);
      lastNanos = now;
      boolean paused = mc.isPaused();
      if (!paused) {
         float s0 = scaleAt(t);
         t += dtSec;
         float s1 = scaleAt(t);
         lag += (1.0F - (s0 + s1) * 0.5F) * dtSec * 20.0F;
      }
      if (bird.isRemoved() || bird.level() != mc.level || mc.player == null || !mc.player.isAlive() || mc.screen != null && t > 0.3F) {
         stop();
         return;
      }
      record(pt);
      // the real bird lies still: once the replay has played to the end of its fall, we are done
      if (t > 1.1F && hn > 2) {
         float real = bird.tickCount + pt;
         float warped = real - lag;
         int i = hn - 1;
         boolean still = (HF[i] & 1) != 0;
         if (still) {
            // find when it came to rest
            int k = i;
            while (k > 0 && (HF[k - 1] & 1) != 0) {
               k--;
            }
            if (warped >= HT[k] + 4.0F) {
               stop();
               return;
            }
         }
      }
      if (t > 7.0F) {
         stop();
      }
   }

   private static void record(float pt) {
      WildlifeMob e = bird;
      float time = e.tickCount + pt;
      if (hn > 0 && time <= HT[hn - 1]) {
         return;
      }
      if (hn == CAP) {
         System.arraycopy(HT, 1, HT, 0, CAP - 1);
         System.arraycopy(HX, 1, HX, 0, CAP - 1);
         System.arraycopy(HY, 1, HY, 0, CAP - 1);
         System.arraycopy(HZ, 1, HZ, 0, CAP - 1);
         System.arraycopy(HF, 1, HF, 0, CAP - 1);
         hn--;
      }
      HT[hn] = time;
      HX[hn] = Mth.lerp(pt, e.xo, e.getX());
      HY[hn] = Mth.lerp(pt, e.yo, e.getY());
      HZ[hn] = Mth.lerp(pt, e.zo, e.getZ());
      boolean down = e.onGround() || e.isInWater();
      HF[hn] = (byte)((down ? 1 : 0) | (e.isInWater() ? 2 : 0));
      hn++;
   }

   static void stop() {
      bird = null;
      t = -1.0F;
      lag = 0.0F;
      hn = 0;
      cooldownUntil = System.nanoTime() + 1_500_000_000L;
   }

   /** The bird's animation clock: the slowed replay clock for the moment's bird, real time for everything else. */
   public static float time(WildlifeMob e, float real) {
      return e == bird ? real - lag : real;
   }

   /** The replayed position (x, y, z, down, water) of the moment's bird, else null. */
   public static double[] position(WildlifeMob e, float pt) {
      if (e != bird || hn == 0) {
         return null;
      }
      float warped = e.tickCount + pt - lag;
      int i = 0;
      if (warped <= HT[0]) {
         return sample(0, 0, 0.0);
      }
      if (warped >= HT[hn - 1]) {
         return sample(hn - 1, hn - 1, 0.0);
      }
      int lo = 0, hi = hn - 1;
      while (hi - lo > 1) {
         int mid = (lo + hi) >>> 1;
         if (HT[mid] <= warped) {
            lo = mid;
         } else {
            hi = mid;
         }
      }
      double f = (warped - HT[lo]) / Math.max(1.0E-4, HT[hi] - HT[lo]);
      return sample(lo, hi, f);
   }

   private static double[] sample(int a, int b, double f) {
      return new double[]{Mth.lerp(f, HX[a], HX[b]), Mth.lerp(f, HY[a], HY[b]), Mth.lerp(f, HZ[a], HZ[b]), (HF[a] & 1) != 0 && (HF[b] & 1) != 0 ? 1.0 : 0.0,
         (HF[b] & 2) != 0 ? 1.0 : 0.0};
   }

   /** Render offset (replay minus real interpolated position) for the moment's bird, else null. */
   public static double[] offset(WildlifeMob e, float pt) {
      double[] p = position(e, pt);
      if (p == null) {
         return null;
      }
      double dx = p[0] - Mth.lerp(pt, e.xo, e.getX()), dy = p[1] - Mth.lerp(pt, e.yo, e.getY()), dz = p[2] - Mth.lerp(pt, e.zo, e.getZ());
      if (dx * dx + dy * dy + dz * dz > 40.0 * 40.0) {
         return null;
      }
      return new double[]{dx, dy, dz};
   }
}
