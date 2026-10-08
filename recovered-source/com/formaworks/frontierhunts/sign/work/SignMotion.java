package com.formaworks.frontierhunts.sign.work;

/**
 * [deersign] Deterministic motion of a buck working sign: pure functions of a seed and the time into the phase, so the
 * server, every client and the offline harness agree on every stroke without sending them.
 *
 * <ul>
 *   <li>Rubbing comes in bouts of 1.6-3.4 s: vigorous up-and-down strokes (0.8-1.5 per second) with the head twisting
 *   side to side so first one beam, then the other, works the bark; short pauses between bouts where the head eases off
 *   the trunk (often to smell the fresh rub).</li>
 *   <li>Pawing: one front hoof strokes 2-4 times (lift, reach forward, strike, drag back through the litter), a short
 *   pause, then the other hoof.</li>
 *   <li>Licking branch: the raised head nuzzles, twists and bobs into the branch tip; the mouth works it.</li>
 *   <li>Rub-urination: the hocks rub together slowly.</li>
 * </ul>
 */
public final class SignMotion {
   private SignMotion() {
   }

   /** Deterministic 0..1 noise. */
   public static float rnd(int seed, int i) {
      int h = seed * 0x9E3779B1 + i * 0x85EBCA6B;
      h ^= h >>> 15;
      h *= 0x2C1B3C6D;
      h ^= h >>> 12;
      h *= 0x297A2D39;
      h ^= h >>> 15;
      return (h >>> 8) / (float)(1 << 24);
   }

   public static float smooth(float a, float b, float x) {
      float t = Math.max(0.0F, Math.min(1.0F, (x - a) / (b - a)));
      return t * t * (3.0F - 2.0F * t);
   }

   /** Envelope of a phase: eases in over {@code in} seconds and out over the last {@code out} seconds. */
   public static float envelope(float t, float dur, float in, float out) {
      return smooth(0.0F, in, t) * (1.0F - smooth(dur - out, dur, t));
   }

   // ------------------------------------------------------------------------------------------------ rub

   /** {@link #rub} output slots */
   public static final int V = 0;
   public static final int ROLL = 1;
   public static final int YAW = 2;
   public static final int AMP = 3;
   public static final int STROKE = 4;
   public static final int PRESS = 5;

   /**
    * Rubbing at {@code t} seconds into a rub phase of {@code dur} seconds.
    *
    * @param out {vertical -1..1, roll (rad), yaw (rad), stroke amplitude 0..1, stroke count so far (a new stroke is
    *            a sound and a spray of bark), press 0..1 (antlers in contact; eases off in the pauses)}
    */
   public static void rub(int seed, float t, float dur, float[] out) {
      float env = envelope(t, dur, 0.35F, 0.45F);
      float u = t;
      int strokes = 0;
      for (int k = 0; k < 24; k++) {
         float len = 1.6F + 1.8F * rnd(seed, k * 5);
         float freq = 0.8F + 0.7F * rnd(seed, k * 5 + 1);
         float pause = 0.3F + 0.55F * rnd(seed, k * 5 + 2);
         float twist = 0.25F + 0.75F * rnd(seed, k * 5 + 3);
         if (u < len) {
            float a = smooth(0.0F, 0.3F, u) * (1.0F - smooth(len - 0.3F, len, u)) * env;
            double w = Math.PI * 2.0 * freq * u;
            out[V] = (float)Math.sin(w) * a;
            // twisting peaks at the ends of the strokes: one beam, then the other, bites into the bark
            out[ROLL] = (float)Math.sin(w + Math.PI * 0.5) * 0.30F * twist * a + (rnd(seed, k * 5 + 4) - 0.5F) * 0.12F * a;
            out[YAW] = (float)Math.sin(w * 0.5 + k) * 0.14F * a;
            out[AMP] = a;
            out[STROKE] = strokes + (float)Math.floor(2.0F * freq * u);
            out[PRESS] = env * (0.35F + 0.65F * smooth(0.0F, 0.2F, u) * (1.0F - smooth(len - 0.15F, len + 0.2F, u)));
            return;
         }
         strokes += (int)Math.floor(2.0F * freq * len);
         u -= len;
         if (u < pause) {
            // between bouts: the head eases back off the bark and the nose drops to the fresh wood
            float p = (float)Math.sin(Math.PI * u / pause);
            out[V] = -0.25F * p * env;
            out[ROLL] = 0.0F;
            out[YAW] = (rnd(seed, k * 5 + 4) - 0.5F) * 0.1F * p * env;
            out[AMP] = 0.0F;
            out[STROKE] = strokes;
            out[PRESS] = env * (1.0F - 0.65F * p);
            return;
         }
         u -= pause;
      }
      out[V] = 0.0F;
      out[ROLL] = 0.0F;
      out[YAW] = 0.0F;
      out[AMP] = 0.0F;
      out[STROKE] = strokes;
      out[PRESS] = env;
   }

   // ------------------------------------------------------------------------------------------------ paw

   /** {@link #paw} output slots */
   public static final int FOOT = 0;
   public static final int PHASE = 1;
   public static final int PAW_AMP = 2;
   public static final int PAW_STROKE = 3;

   /**
    * Pawing at {@code t} seconds into a paw phase.
    *
    * @param out {foot (0 left, 1 right), phase 0..1 within the stroke (lift .. reach .. strike .. drag back .. under),
    *            amplitude 0..1 (0 between groups), strokes so far (a new one when the hoof starts dragging back)}
    */
   public static void paw(int seed, float t, float dur, float[] out) {
      float env = envelope(t, dur, 0.2F, 0.3F);
      float u = t - 0.15F;
      int strokes = 0;
      int foot = (seed >>> 3) & 1;
      for (int g = 0; g < 24 && u >= 0.0F; g++) {
         int n = 2 + (int)(rnd(seed, 200 + g) * 3.0F);
         float period = 0.55F + 0.22F * rnd(seed, 230 + g);
         float group = n * period;
         if (u < group) {
            int i = (int)(u / period);
            float s = u / period - i;
            out[FOOT] = foot;
            out[PHASE] = s;
            out[PAW_AMP] = env;
            out[PAW_STROKE] = strokes + i + (s >= 0.55F ? 1 : 0);
            return;
         }
         strokes += n;
         u -= group;
         float pause = 0.25F + 0.45F * rnd(seed, 260 + g);
         if (u < pause) {
            break;
         }
         u -= pause;
         foot ^= 1;
      }
      out[FOOT] = foot;
      out[PHASE] = 0.0F;
      out[PAW_AMP] = 0.0F;
      out[PAW_STROKE] = strokes;
   }

   /**
    * Pawing leg curve at stroke phase {@code s}: {shoulder swing (rad, + = hoof forward), carpus fold (rad, + = cannon
    * folds back), fetlock (rad), hoof lift (blocks, model space, for the box model)}.
    */
   public static void pawLeg(float s, float[] out) {
      // keyframes: under (0), lifted and folded (0.22), reaching forward (0.42), struck down (0.55), dragged back (0.85), under (1)
      float[] key = {0.0F, 0.22F, 0.42F, 0.55F, 0.85F, 1.0F};
      float[][] val = {
         {0.0F, 0.0F, 0.0F, 0.0F},
         {0.18F, 0.9F, 0.4F, 0.14F},
         {0.46F, 0.30F, 0.05F, 0.10F},
         {0.30F, 0.0F, -0.04F, 0.0F},
         {-0.26F, 0.08F, 0.0F, 0.0F},
         {0.0F, 0.0F, 0.0F, 0.0F}};
      int i = 0;
      while (i < key.length - 2 && s > key[i + 1]) {
         i++;
      }
      float f = smooth(key[i], key[i + 1], s);
      for (int c = 0; c < 4; c++) {
         out[c] = val[i][c] + (val[i + 1][c] - val[i][c]) * f;
      }
   }

   // ------------------------------------------------------------------------------------------------ licking branch

   /** {@link #lick} output slots */
   public static final int L_YAW = 0;
   public static final int L_PITCH = 1;
   public static final int L_ROLL = 2;
   public static final int L_UP = 3;
   public static final int L_EVENT = 4;

   /** Working the licking branch: {yaw, pitch, roll (rad), reach up (blocks), rustle count so far}. */
   public static void lick(int seed, float t, float dur, float[] out) {
      float env = envelope(t, dur, 0.5F, 0.5F);
      float a = rnd(seed, 300) * 6.0F;
      out[L_YAW] = ((float)Math.sin(t * 4.1 + a) * 0.20F + (float)Math.sin(t * 13.7 + a) * 0.05F) * env;
      out[L_PITCH] = ((float)Math.sin(t * 6.3 + a * 0.5) * 0.10F - 0.05F) * env;
      out[L_ROLL] = (float)Math.sin(t * 5.2 + a * 1.3) * 0.22F * env;
      // bobbing up into the branch, the forehead and antler bases working the twigs
      out[L_UP] = ((float)Math.max(0.0, Math.sin(t * 7.6 + a)) * 0.05F) * env;
      out[L_EVENT] = (float)Math.floor((t + rnd(seed, 301)) / (0.7F + 0.4F * rnd(seed, 302)));
   }

   /** Mouth working the branch tip (0 closed .. 1 open), for rigs with a jaw. */
   public static float chew(int seed, float t) {
      return (float)Math.max(0.0, Math.sin(t * 9.0 + seed)) * 0.6F;
   }

   // ------------------------------------------------------------------------------------------------ rub-urination

   /** Hock rub at {@code t} seconds into urinating: -1..1 */
   public static float hockRub(float t) {
      return (float)Math.sin(t * 7.0) * 0.6F + (float)Math.sin(t * 2.3) * 0.4F;
   }
}
