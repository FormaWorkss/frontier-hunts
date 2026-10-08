package com.formaworks.frontierhunts.phone.client.ui;

/** [phone] FieldOS colours, sizes and easing curves. Dark, warm, outdoors: moss, bone, blaze orange and the mod's gold. */
public final class Theme {
   // screen geometry (logical pixels)
   public static final float CASE_W = 176.0F;
   public static final float CASE_H = 356.0F;
   public static final float BEZEL = 7.0F;
   public static final float SCREEN_W = CASE_W - BEZEL * 2.0F;
   public static final float SCREEN_H = CASE_H - BEZEL * 2.0F;
   public static final float SCREEN_R = 17.0F;
   public static final float STATUS_H = 15.0F;
   public static final float HOME_BAR_H = 11.0F;

   // surfaces
   public static final int BG = 0xFF0D110F;
   public static final int SURFACE = 0xFF161B18;
   public static final int SURFACE2 = 0xFF1E2420;
   public static final int SURFACE3 = 0xFF283029;
   public static final int SURFACE4 = 0xFF323B34;
   public static final int LINE = 0xFF2C3530;
   public static final int SCRIM = 0xB0060807;

   // text
   public static final int TEXT = 0xFFF1EFE6;
   public static final int TEXT2 = 0xFFADB5AA;
   public static final int TEXT3 = 0xFF75807A;
   public static final int TEXT_DARK = 0xFF1B201C;

   // accents
   public static final int BLAZE = 0xFFFF7A1F;
   public static final int BLAZE_DIM = 0xFF7A3A12;
   public static final int GOLD = 0xFFD8B46A;
   public static final int MOSS = 0xFF8CC071;
   public static final int MOSS_DIM = 0xFF2E4A2A;
   public static final int SKY = 0xFF73A9DE;
   public static final int SKY_DIM = 0xFF1D3247;
   public static final int RED = 0xFFE5574B;
   public static final int RED_DIM = 0xFF4A1C19;
   public static final int YELLOW = 0xFFF0C64E;
   public static final int VIOLET = 0xFFA996E0;
   public static final int IR = 0xFFB6A6E8;

   private Theme() {
   }

   // ------------------------------------------------------------------------------------------------ colour maths

   public static int alpha(int argb, float a) {
      int al = (int)((argb >>> 24) * Math.max(0.0F, Math.min(1.0F, a)));
      return al << 24 | argb & 0xFFFFFF;
   }

   public static int withAlpha(int rgb, int a) {
      return (Math.max(0, Math.min(255, a)) << 24) | rgb & 0xFFFFFF;
   }

   public static int mix(int a, int b, float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
      int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
      return (int)(aa + (ba - aa) * t) << 24 | (int)(ar + (br - ar) * t) << 16 | (int)(ag + (bg - ag) * t) << 8 | (int)(ab + (bb - ab) * t);
   }

   /** Brightness multiply (rgb only). */
   public static int shade(int argb, float k) {
      int r = Math.min(255, (int)((argb >> 16 & 255) * k));
      int g = Math.min(255, (int)((argb >> 8 & 255) * k));
      int b = Math.min(255, (int)((argb & 255) * k));
      return argb & 0xFF000000 | r << 16 | g << 8 | b;
   }

   /** Red night-vision version of a colour: its luminance in deep red. */
   public static int night(int argb) {
      int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
      float l = (0.30F * r + 0.59F * g + 0.11F * b) / 255.0F;
      l = (float)Math.pow(l, 0.85);
      int rr = (int)(255 * Math.min(1.0F, l * 1.05F));
      int gg = (int)(rr * 0.08F);
      int bb = (int)(rr * 0.05F);
      return argb & 0xFF000000 | rr << 16 | gg << 8 | bb;
   }

   // ------------------------------------------------------------------------------------------------ easing

   public static float clamp01(float t) {
      return t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
   }

   public static float easeOut(float t) {
      t = clamp01(t);
      float u = 1.0F - t;
      return 1.0F - u * u * u;
   }

   public static float easeInOut(float t) {
      t = clamp01(t);
      return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float)Math.pow(-2.0F * t + 2.0F, 3.0) / 2.0F;
   }

   /** Overshoot spring-ish ease for pop-ins. */
   public static float easeBack(float t) {
      t = clamp01(t);
      float c1 = 1.40158F;
      float c3 = c1 + 1.0F;
      float u = t - 1.0F;
      return 1.0F + c3 * u * u * u + c1 * u * u;
   }

   /** Moves {@code v} toward {@code target} with a frame-rate independent exponential smoothing. */
   public static float approach(float v, float target, float rate, float dt) {
      float k = 1.0F - (float)Math.exp(-rate * dt);
      return v + (target - v) * k;
   }
}
