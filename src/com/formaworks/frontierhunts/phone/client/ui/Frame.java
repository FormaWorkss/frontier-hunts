package com.formaworks.frontierhunts.phone.client.ui;

/**
 * [phone] The canvas the phone UI draws on during one frame: forwards to the real canvas (game or mock) and keeps its
 * own copy of the transform and clip, so the UI can register tap zones in the same coordinates it draws in
 * (immediate-mode hit testing: what you see is exactly what you can tap). Fixed-size arrays, no allocation per frame.
 */
public final class Frame implements Canvas {
   private static final int DEPTH = 32;
   private static final int ZONES = 512;
   private Canvas out;
   private final float[] tx = new float[DEPTH];
   private final float[] ty = new float[DEPTH];
   private final float[] ts = new float[DEPTH];
   private final float[] ta = new float[DEPTH];
   private int top;
   private final float[] cx0 = new float[DEPTH];
   private final float[] cy0 = new float[DEPTH];
   private final float[] cx1 = new float[DEPTH];
   private final float[] cy1 = new float[DEPTH];
   private int clips;
   // zones, double-buffered: the UI hit-tests last frame's zones while it draws this frame's
   private final Zones[] zones = {new Zones(), new Zones()};
   private int cur;
   /** zones are only recorded while true (the closing animation draws but takes no taps) */
   public boolean recording = true;
   /** red night light: every colour becomes its brightness in deep red */
   public boolean night;
   private static final int NC = 4096;
   private final int[] nightKey = new int[NC];
   private final int[] nightVal = new int[NC];
   private final boolean[] nightUsed = new boolean[NC];
   private int nightCount;

   private int c(int argb) {
      if (!this.night) {
         return argb;
      }
      int i = (argb * 0x9E3779B1) >>> 20 & NC - 1;
      while (this.nightUsed[i]) {
         if (this.nightKey[i] == argb) {
            return this.nightVal[i];
         }
         i = i + 1 & NC - 1;
      }
      int v = Theme.night(argb);
      if (this.nightCount < NC * 3 / 4) {
         this.nightUsed[i] = true;
         this.nightKey[i] = argb;
         this.nightVal[i] = v;
         this.nightCount++;
      }
      return v;
   }

   private static final class Zones {
      final float[] x0 = new float[ZONES], y0 = new float[ZONES], x1 = new float[ZONES], y1 = new float[ZONES];
      final int[] id = new int[ZONES];
      final long[] data = new long[ZONES];
      int n;
   }

   public void begin(Canvas out) {
      this.out = out;
      this.top = 0;
      this.tx[0] = 0.0F;
      this.ty[0] = 0.0F;
      this.ts[0] = 1.0F;
      this.ta[0] = 1.0F;
      this.clips = 0;
      this.cur ^= 1;
      this.zones[this.cur].n = 0;
   }

   public Canvas out() {
      return this.out;
   }

   // ------------------------------------------------------------------------------------------------ zones

   /** A tap zone for the rectangle in current coordinates, clipped to the current clip. */
   public void zone(int id, float x, float y, float w, float h, long data) {
      if (!this.recording) {
         return;
      }
      Zones z = this.zones[this.cur];
      if (z.n >= ZONES || w <= 0.0F || h <= 0.0F) {
         return;
      }
      float s = this.ts[this.top];
      float x0 = this.tx[this.top] + x * s, y0 = this.ty[this.top] + y * s;
      float x1 = x0 + w * s, y1 = y0 + h * s;
      if (this.clips > 0) {
         int c = this.clips - 1;
         x0 = Math.max(x0, this.cx0[c]);
         y0 = Math.max(y0, this.cy0[c]);
         x1 = Math.min(x1, this.cx1[c]);
         y1 = Math.min(y1, this.cy1[c]);
         if (x1 <= x0 || y1 <= y0) {
            return;
         }
      }
      int i = z.n++;
      z.x0[i] = x0;
      z.y0[i] = y0;
      z.x1[i] = x1;
      z.y1[i] = y1;
      z.id[i] = id;
      z.data[i] = data;
   }

   public void zone(int id, float x, float y, float w, float h) {
      this.zone(id, x, y, w, h, 0L);
   }

   /** Index of the top-most zone of the last finished frame at a screen point, or -1. */
   public int hit(float sx, float sy) {
      Zones z = this.zones[this.cur ^ 1];
      for (int i = z.n - 1; i >= 0; i--) {
         if (sx >= z.x0[i] && sx < z.x1[i] && sy >= z.y0[i] && sy < z.y1[i]) {
            return i;
         }
      }
      return -1;
   }

   public int hitId(int index) {
      return index < 0 ? 0 : this.zones[this.cur ^ 1].id[index];
   }

   public long hitData(int index) {
      return index < 0 ? 0L : this.zones[this.cur ^ 1].data[index];
   }

   /** Converts a screen point into the current local coordinates. */
   public float localX(float sx) {
      return (sx - this.tx[this.top]) / this.ts[this.top];
   }

   public float localY(float sy) {
      return (sy - this.ty[this.top]) / this.ts[this.top];
   }

   public float scaleNow() {
      return this.ts[this.top];
   }

   public float alphaNow() {
      return this.ta[this.top];
   }

   // ------------------------------------------------------------------------------------------------ transform

   @Override
   public void push() {
      if (this.top + 1 >= DEPTH) {
         throw new IllegalStateException("phone canvas push overflow");
      }
      this.top++;
      this.tx[this.top] = this.tx[this.top - 1];
      this.ty[this.top] = this.ty[this.top - 1];
      this.ts[this.top] = this.ts[this.top - 1];
      this.ta[this.top] = this.ta[this.top - 1];
      this.out.push();
   }

   @Override
   public void pop() {
      if (this.top > 0) {
         this.top--;
         this.out.pop();
      }
   }

   @Override
   public void translate(float x, float y) {
      this.tx[this.top] += x * this.ts[this.top];
      this.ty[this.top] += y * this.ts[this.top];
      this.out.translate(x, y);
   }

   @Override
   public void scale(float s) {
      this.ts[this.top] *= s;
      this.out.scale(s);
   }

   @Override
   public void alpha(float a) {
      this.ta[this.top] *= a;
      this.out.alpha(a);
   }

   // ------------------------------------------------------------------------------------------------ clip

   @Override
   public void clip(float x, float y, float w, float h) {
      float s = this.ts[this.top];
      float x0 = this.tx[this.top] + x * s, y0 = this.ty[this.top] + y * s;
      float x1 = x0 + w * s, y1 = y0 + h * s;
      if (this.clips > 0) {
         int c = this.clips - 1;
         x0 = Math.max(x0, this.cx0[c]);
         y0 = Math.max(y0, this.cy0[c]);
         x1 = Math.min(x1, this.cx1[c]);
         y1 = Math.min(y1, this.cy1[c]);
      }
      if (this.clips < DEPTH) {
         this.cx0[this.clips] = x0;
         this.cy0[this.clips] = y0;
         this.cx1[this.clips] = Math.max(x0, x1);
         this.cy1[this.clips] = Math.max(y0, y1);
         this.clips++;
      }
      this.out.clip(x, y, w, h);
   }

   @Override
   public void unclip() {
      if (this.clips > 0) {
         this.clips--;
      }
      this.out.unclip();
   }

   /** Is a local rectangle at least partly inside the current clip? (skip drawing list rows that are off screen) */
   public boolean visible(float x, float y, float w, float h) {
      if (this.clips == 0) {
         return true;
      }
      float s = this.ts[this.top];
      float x0 = this.tx[this.top] + x * s, y0 = this.ty[this.top] + y * s;
      int c = this.clips - 1;
      return x0 + w * s > this.cx0[c] && x0 < this.cx1[c] && y0 + h * s > this.cy0[c] && y0 < this.cy1[c];
   }

   // ------------------------------------------------------------------------------------------------ drawing

   @Override
   public void fill(float x, float y, float w, float h, int argb) {
      this.out.fill(x, y, w, h, this.c(argb));
   }

   @Override
   public void round(float x, float y, float w, float h, float r, int argb) {
      this.out.round(x, y, w, h, r, this.c(argb));
   }

   @Override
   public void gradient(float x, float y, float w, float h, int top, int bottom) {
      this.out.gradient(x, y, w, h, this.c(top), this.c(bottom));
   }

   @Override
   public void hgradient(float x, float y, float w, float h, int left, int right) {
      this.out.hgradient(x, y, w, h, this.c(left), this.c(right));
   }

   @Override
   public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
      this.out.quad(x0, y0, x1, y1, x2, y2, x3, y3, this.c(argb));
   }

   @Override
   public void line(float x0, float y0, float x1, float y1, float width, int argb) {
      this.out.line(x0, y0, x1, y1, width, this.c(argb));
   }

   @Override
   public void ring(float cx, float cy, float r, float thickness, int argb) {
      this.out.ring(cx, cy, r, thickness, this.c(argb));
   }

   @Override
   public void arc(float cx, float cy, float r, float thickness, float a0, float a1, int argb) {
      this.out.arc(cx, cy, r, thickness, a0, a1, this.c(argb));
   }

   @Override
   public float text(String s, float x, float y, int argb, Font f) {
      return this.out.text(s, x, y, this.c(argb), f);
   }

   @Override
   public float width(String s, Font f) {
      return this.out.width(s, f);
   }

   @Override
   public void sprite(String sheet, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int tint) {
      this.out.sprite(sheet, x, y, w, h, u0, v0, u1, v1, this.c(tint));
   }

   @Override
   public void image(Object handle, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int tint) {
      if (handle != null) {
         this.out.image(handle, x, y, w, h, u0, v0, u1, v1, this.c(tint));
      }
   }

   @Override
   public void item(String id, float x, float y, float size) {
      this.out.item(id, x, y, size);
   }

   @Override
   public void custom(Custom c, float x, float y, float w, float h) {
      if (c != null) {
         this.out.custom(c, x, y, w, h);
      }
   }
}
