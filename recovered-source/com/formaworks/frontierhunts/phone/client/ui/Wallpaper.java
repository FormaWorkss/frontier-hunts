package com.formaworks.frontierhunts.phone.client.ui;

/**
 * [phone] The live home-screen wallpaper: a mountain valley drawn in layers, lit by the real in-game hour (night sky
 * with stars, dawn glow, blue day, orange dusk), with the sun or moon in its place, and rain or snow when it falls.
 * The shapes are made once from a fixed seed; drawing is a few hundred flat quads.
 */
public final class Wallpaper {
   private static final int SEG = 40;
   private final float[][] ridge = new float[3][SEG + 1];
   private final float[] pineX = new float[22];
   private final float[] pineH = new float[22];
   private final float[] starX = new float[46];
   private final float[] starY = new float[46];
   private final float[] starB = new float[46];
   private final float[] dropX = new float[48];
   private final float[] dropY = new float[48];
   private boolean made;
   private static final float[] BASE = {0.36F, 0.45F, 0.54F};
   private static final int[] ROCK = {0xFF56657A, 0xFF34424F, 0xFF1E2B27};
   private static final float[] HAZE = {0.42F, 0.24F, 0.08F};

   private void make(float w, float h) {
      java.util.Random r = new java.util.Random(0x51C0FEL);
      float[] base = BASE;
      float[] amp = {0.16F, 0.10F, 0.07F};
      for (int l = 0; l < 3; l++) {
         float p1 = r.nextFloat() * 6.0F, p2 = r.nextFloat() * 6.0F, p3 = r.nextFloat() * 6.0F;
         for (int i = 0; i <= SEG; i++) {
            float t = (float)i / SEG;
            double v = Math.sin(t * 5.1 + p1) * 0.55 + Math.sin(t * 11.3 + p2) * 0.28 + Math.sin(t * 23.7 + p3) * 0.12;
            // a peak a little off centre on the far range
            if (l == 0) {
               v += Math.exp(-Math.pow((t - 0.62) * 5.5, 2)) * 1.1;
            }
            this.ridge[l][i] = h * (base[l] - (float)v * amp[l]);
         }
      }
      for (int i = 0; i < this.pineX.length; i++) {
         this.pineX[i] = (i + r.nextFloat() * 0.6F) * w / (this.pineX.length - 2) - 6.0F;
         this.pineH[i] = 16.0F + r.nextFloat() * 18.0F + (i % 5 == 0 ? 9.0F : 0.0F);
      }
      for (int i = 0; i < this.starX.length; i++) {
         this.starX[i] = r.nextFloat() * w;
         this.starY[i] = r.nextFloat() * h * 0.5F;
         this.starB[i] = 0.3F + r.nextFloat() * 0.7F;
      }
      for (int i = 0; i < this.dropX.length; i++) {
         this.dropX[i] = r.nextFloat();
         this.dropY[i] = r.nextFloat();
      }
      this.made = true;
   }

   // sky keyframes: minute of day, top colour, horizon colour
   private static final int[] KEY_MIN = {0, 270, 330, 400, 540, 960, 1050, 1110, 1170, 1260, 1440};
   private static final int[] KEY_TOP = {0xFF04070F, 0xFF080D1E, 0xFF22264A, 0xFF35558A, 0xFF3E72AD, 0xFF3E72AD, 0xFF3A5A92, 0xFF34305E, 0xFF1A1A38,
      0xFF070B18, 0xFF04070F};
   private static final int[] KEY_LOW = {0xFF0F1828, 0xFF1A2236, 0xFFD9845A, 0xFFF0B27A, 0xFFA9C9DE, 0xFFB4CFDD, 0xFFE8B07A, 0xFFF0864A, 0xFF7A3E48,
      0xFF141C30, 0xFF0F1828};

   /** Sky colour at the top for a minute of the day. */
   public static int skyTop(int minute) {
      return key(KEY_TOP, minute);
   }

   /** Sky colour at the horizon for a minute of the day. */
   public static int skyLow(int minute) {
      return key(KEY_LOW, minute);
   }

   private static int key(int[] cols, int minute) {
      for (int i = 0; i < KEY_MIN.length - 1; i++) {
         if (minute >= KEY_MIN[i] && minute <= KEY_MIN[i + 1]) {
            float t = (float)(minute - KEY_MIN[i]) / (KEY_MIN[i + 1] - KEY_MIN[i]);
            return Theme.mix(cols[i], cols[i + 1], t);
         }
      }
      return cols[0];
   }

   void draw(PhoneUi ui, Frame f, float w, float h, long now) {
      if (!this.made) {
         this.make(w, h);
      }
      PhoneModel m = ui.model;
      int minute = Ui.minute(m.dayTime);
      int top = key(KEY_TOP, minute);
      int low = key(KEY_LOW, minute);
      String wx = m.weather.icon;
      boolean overcast = wx.equals("cloudy") || wx.equals("rain") || wx.equals("thunder") || wx.equals("snow") || wx.equals("blizzard")
         || wx.equals("fog") || wx.equals("drizzle") || wx.equals("sleet") || wx.equals("storm_night");
      if (overcast) {
         int grey = Theme.mix(0xFF5A626A, 0xFF101418, nightness(minute));
         top = Theme.mix(top, grey, 0.65F);
         low = Theme.mix(low, Theme.shade(grey, 1.35F), 0.6F);
      }
      float horizon = h * 0.62F;
      f.gradient(0.0F, 0.0F, w, horizon, top, low);
      f.fill(0.0F, horizon - 0.5F, w, h - horizon + 0.5F, low);
      float night = nightness(minute);
      // stars
      if (night > 0.05F && !overcast) {
         for (int i = 0; i < this.starX.length; i++) {
            float tw = 0.6F + 0.4F * (float)Math.sin(now / 700.0 + i * 1.7);
            int a = (int)(220 * night * this.starB[i] * tw);
            float s = this.starB[i] > 0.85F ? 1.3F : 0.8F;
            f.fill(this.starX[i], this.starY[i], s, s, Theme.withAlpha(0xFFF4E8, a));
         }
      }
      // sun or moon on an arc across the sky
      float dayT = (minute - 300) / 900.0F; // 05:00 .. 20:00
      if (dayT > -0.05F && dayT < 1.05F && !overcast) {
         float sx = w * (0.08F + 0.84F * dayT);
         float sy = horizon - (float)Math.sin(Math.max(0.0F, Math.min(1.0F, dayT)) * Math.PI) * h * 0.42F + 6.0F;
         int glow = Theme.mix(0xFFFFD9A0, 0xFFFFF4D8, (float)Math.sin(Math.max(0.0F, Math.min(1.0F, dayT)) * Math.PI));
         for (int i = 4; i >= 1; i--) {
            f.circle(sx, sy, 6.0F + i * 6.0F, Theme.withAlpha(glow, 14));
         }
         f.circle(sx, sy, 6.5F, Theme.withAlpha(glow, 255));
      } else if (night > 0.4F && !overcast) {
         float nt = ((minute + 1440 - 1200) % 1440) / 600.0F;
         float mx = w * (0.15F + 0.7F * nt);
         float my = h * 0.12F + (float)Math.abs(nt - 0.5F) * h * 0.18F;
         f.circle(mx, my, 14.0F, 0x0FEFF2FF);
         f.circle(mx, my, 6.0F, 0xFFE9EAF0);
         // the dark part of the moon by phase
         int ph = Math.floorMod(m.moonPhase, 8);
         if (ph != 0) {
            float off = ph <= 4 ? (ph * 3.2F) : ((8 - ph) * -3.2F);
            f.circle(mx + off, my - 0.3F, 6.2F, Theme.mix(top, low, 0.25F));
         }
      }
      // mountain ranges, far to near; mist pooling at the foot of each range gives the valley its depth
      float dark = 0.35F + 0.65F * (1.0F - night);
      int mist = Theme.shade(Theme.mix(low, 0xFFE8EEF2, 0.25F), 0.55F + 0.45F * dark);
      float ly = h * 0.58F;
      boolean winter = m.season.equals("Winter") || wx.equals("snow") || wx.equals("blizzard");
      for (int l = 0; l < 3; l++) {
         int c = Theme.shade(Theme.mix(ROCK[l], low, HAZE[l]), dark);
         float[] r = this.ridge[l];
         for (int i = 0; i < SEG; i++) {
            float x0 = w * i / SEG, x1 = w * (i + 1) / SEG + 0.4F;
            f.quad(x0, r[i], x1, r[i + 1], x1, h, x0, h, c);
         }
         if (l == 0) {
            // snow on the high peaks (all year on the highest, down the slopes in winter)
            float line = h * (winter ? 0.40F : 0.31F);
            int snowCol = Theme.shade(Theme.mix(0xFFE6ECF0, low, 0.18F), dark);
            for (int i = 0; i < SEG; i++) {
               if (r[i] < line || r[i + 1] < line) {
                  float x0 = w * i / SEG, x1 = w * (i + 1) / SEG + 0.4F;
                  float d0 = Math.max(0.0F, line - r[i]) * 0.55F + 1.5F, d1 = Math.max(0.0F, line - r[i + 1]) * 0.55F + 1.5F;
                  f.quad(x0, r[i], x1, r[i + 1], x1, r[i + 1] + d1, x0, r[i] + d0, snowCol);
               }
            }
         }
         if (l < 2) {
            float y0 = h * (BASE[l] - 0.01F);
            f.gradient(0.0F, y0, w, ly - y0 + 1.0F, Theme.withAlpha(mist, 0), Theme.withAlpha(mist, l == 0 ? 120 : 90));
         }
      }
      // a lake catching the sky, with the far ranges mirrored in it
      float lh = h * 0.26F;
      f.gradient(0.0F, ly, w, lh, Theme.shade(Theme.mix(low, top, 0.15F), 0.55F + 0.45F * dark), Theme.shade(Theme.mix(top, ROCK[2], 0.5F), 0.6F * dark + 0.1F));
      for (int l = 0; l < 2; l++) {
         int c = Theme.withAlpha(Theme.shade(Theme.mix(ROCK[l], low, HAZE[l] + 0.2F), dark * 0.85F), 70);
         float[] r = this.ridge[l];
         for (int i = 0; i < SEG; i++) {
            float m0 = Math.min(ly + lh, ly + Math.max(0.0F, ly - r[i]) * 0.45F), m1 = Math.min(ly + lh, ly + Math.max(0.0F, ly - r[i + 1]) * 0.45F);
            if (m0 > ly + 0.5F || m1 > ly + 0.5F) {
               // translucent: edges meet exactly (an overlap would show as a seam)
               float x0 = w * i / SEG, x1 = w * (i + 1) / SEG;
               f.quad(x0, ly, x1, ly, x1, m1, x0, m0, c);
            }
         }
      }
      f.fill(0.0F, ly, w, 1.0F, Theme.withAlpha(Theme.shade(low, 1.2F), (int)(90 * dark)));
      for (int i = 0; i < 9; i++) {
         float yy = ly + 4.0F + i * i * 1.3F;
         float xx = (float)((now / 120.0 + i * 37) % (w + 60)) - 30.0F;
         f.fill(xx, yy, 10.0F + i * 3.0F, 0.6F, Theme.withAlpha(0xFFFFFF, (int)(30 * dark)));
      }
      // pines along the near shore
      int pine = Theme.shade(Theme.mix(0xFF16261C, low, 0.10F), 0.35F + 0.65F * dark);
      float base = h * 0.86F;
      f.fill(0.0F, base, w, h - base, pine);
      for (int i = 0; i < this.pineX.length; i++) {
         float px = this.pineX[i];
         float edge = Math.abs(px - w / 2.0F) / (w / 2.0F);
         float ph = this.pineH[i] * (0.8F + edge * 0.9F);
         float bw = ph * 0.34F;
         for (int k = 0; k < 4; k++) {
            float ty = base - ph + k * ph * 0.22F;
            float hw = bw * (0.35F + k * 0.22F);
            f.triangle(px, ty, px + hw, ty + ph * 0.34F, px - hw, ty + ph * 0.34F, pine);
         }
         f.fill(px - 0.8F, base - 4.0F, 1.6F, 5.0F, pine);
      }
      // the bottom fades darker so the dock reads well
      f.gradient(0.0F, h * 0.62F, w, h * 0.38F, 0x00000000, 0x58000000);
      f.gradient(0.0F, 0.0F, w, 40.0F, 0x50000000, 0x00000000);
      // falling rain or snow
      boolean rain = wx.equals("rain") || wx.equals("thunder") || wx.equals("drizzle") || wx.equals("storm_night");
      boolean snow = wx.equals("snow") || wx.equals("blizzard") || wx.equals("sleet");
      if (rain || snow) {
         float t = (now % 100000L) / 1000.0F;
         for (int i = 0; i < this.dropX.length; i++) {
            float speed = rain ? 1.6F : 0.18F + this.dropX[i] * 0.1F;
            float yy = (this.dropY[i] + t * speed) % 1.0F * (h + 20.0F) - 10.0F;
            float xx = this.dropX[i] * w + (snow ? (float)Math.sin(t * 1.3 + i) * 4.0F : -yy * 0.08F);
            if (rain) {
               f.line(xx, yy, xx - 1.4F, yy + 9.0F, 0.6F, 0x46D8E4F0);
            } else {
               f.circle(xx, yy, 0.9F + this.dropX[i] * 0.6F, 0xC8F4F6FA);
            }
         }
      }
      if (wx.equals("fog")) {
         f.gradient(0.0F, h * 0.45F, w, h * 0.4F, 0x00C8D0D4, 0x90C8D0D4);
      }
   }

   public static float nightness(int minute) {
      // 1 deep night .. 0 day, smooth through dawn (04:30-06:30) and dusk (18:30-20:30)
      if (minute >= 390 && minute <= 1110) {
         return 0.0F;
      }
      if (minute > 1110 && minute < 1230) {
         return (minute - 1110) / 120.0F;
      }
      if (minute > 270 && minute < 390) {
         return 1.0F - (minute - 270) / 120.0F;
      }
      return 1.0F;
   }
}
