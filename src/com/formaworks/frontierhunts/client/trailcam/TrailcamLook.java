package com.formaworks.frontierhunts.client.trailcam;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Turns a raw frame of the world (read back from the framebuffer) into a trail camera photograph: 16:9 crop, a
 * slightly barrelled wide-angle lens, the cheap-sensor colour of a daylight trail cam or the infrared night look
 * (flash fall-off with distance, bright foliage, black background, eyeshine, motion smear), vignette, sharpening,
 * sensor noise and the burned-in data strip. Pure Java on int/float arrays so it runs on a worker thread and can be
 * tested outside the game.
 */
public final class TrailcamLook {
   private static final float BARREL = 0.075F;

   private TrailcamLook() {
   }

   /** Where the lens was and what it saw, in the render's own terms. */
   public static final class Input {
      /** full frame, top-down rows, 0xAARRGGBB */
      public int[] argb;
      public int sw;
      public int sh;
      /** optional raw depth buffer (0..1), bottom-up rows like GL, same size as the frame */
      public float[] depth;
      public float near = 0.05F;
      public float far = 256.0F;
      /** 16:9 crop of the frame the photo uses (top-down source pixels) */
      public int cropX;
      public int cropY;
      public int cropW;
      public int cropH;
      public int outW = 640;
      public int outH = 360;
      /** tan of half the photo's horizontal / vertical field of view (after cropping) */
      public float tanH;
      public float tanV;
      public boolean infrared;
      /** metres to the subject that tripped the sensor, for the flash auto-exposure */
      public float subjectDistance = 8.0F;
      /** lens height above the ground under the subjects, for the no-depth fallback */
      public float lensHeight = 1.4F;
      public final List<Mark> marks = new ArrayList<>();
      public long seed;
      public Strip strip;
   }

   /** A subject on the photo, in undistorted normalised lens coordinates (x right, y up, -1..1). */
   public static final class Mark {
      public float x0;
      public float y0;
      public float x1;
      public float y1;
      public float distance;
      /** motion smear during the exposure, in normalised units */
      public float mx;
      public float my;
      /** eyes (normalised positions), empty when none face the lens */
      public final List<float[]> eyes = new ArrayList<>();
      public float shine;
   }

   /** The text burned into the bottom of every frame. */
   public static final class Strip {
      public String camera = "";
      public String temperature = "";
      public int moonPhase;
      public String date = "";
      public String time = "";
      public String frame = "";
   }

   public static int[] develop(Input in) {
      int w = in.outW;
      int h = in.outH;
      int w2 = w * 2;
      int h2 = h * 2;
      float[] big = downscale(in, w2, h2);
      float[] dist = in.infrared ? distances(in, w2, h2) : null;
      boolean depthOk = dist != null && depthPlausible(dist, in);
      if (in.infrared && !depthOk) {
         dist = pseudoDistances(in, w2, h2);
      }
      if (in.infrared) {
         motionSmear(big, dist, w2, h2, in);
      }
      float[] img = new float[w * h * 3];
      float[] far = in.infrared ? new float[w * h] : null;
      lens(big, dist, w2, h2, img, far, w, h);
      if (in.infrared) {
         infrared(img, far, w, h, in);
         eyeshine(img, far, w, h, in, depthOk);
      } else {
         daylight(img, w, h);
      }
      sharpen(img, w, h, in.infrared ? 0.18F : 0.42F);
      vignette(img, w, h, in.infrared ? 0.32F : 0.24F);
      noise(img, w, h, in.seed, in.infrared);
      int[] out = new int[w * h];
      for (int i = 0; i < w * h; i++) {
         int r = clamp8(img[i * 3]);
         int g = clamp8(img[i * 3 + 1]);
         int b = clamp8(img[i * 3 + 2]);
         out[i] = 0xFF000000 | r << 16 | g << 8 | b;
      }
      if (in.strip != null) {
         strip(out, w, h, in.strip);
      }
      return out;
   }

   // ------------------------------------------------------------------------------------------------ sampling

   /** Area-average the crop down to w2 x h2 (float RGB 0..1). */
   private static float[] downscale(Input in, int w2, int h2) {
      float[] out = new float[w2 * h2 * 3];
      double sx = (double)in.cropW / w2;
      double sy = (double)in.cropH / h2;
      for (int y = 0; y < h2; y++) {
         int y0 = in.cropY + (int)Math.floor(y * sy);
         int y1 = Math.max(y0 + 1, in.cropY + (int)Math.floor((y + 1) * sy));
         y0 = clamp(y0, 0, in.sh - 1);
         y1 = clamp(y1, y0 + 1, in.sh);
         for (int x = 0; x < w2; x++) {
            int x0 = in.cropX + (int)Math.floor(x * sx);
            int x1 = Math.max(x0 + 1, in.cropX + (int)Math.floor((x + 1) * sx));
            x0 = clamp(x0, 0, in.sw - 1);
            x1 = clamp(x1, x0 + 1, in.sw);
            int r = 0;
            int g = 0;
            int b = 0;
            int n = 0;
            for (int yy = y0; yy < y1; yy++) {
               int row = yy * in.sw;
               for (int xx = x0; xx < x1; xx++) {
                  int c = in.argb[row + xx];
                  r += c >> 16 & 255;
                  g += c >> 8 & 255;
                  b += c & 255;
                  n++;
               }
            }
            int o = (y * w2 + x) * 3;
            float k = 1.0F / (255.0F * n);
            out[o] = r * k;
            out[o + 1] = g * k;
            out[o + 2] = b * k;
         }
      }
      return out;
   }

   /** Linear distance (metres along the ray) per pixel from the depth buffer, sampled at each output block's centre. */
   private static float[] distances(Input in, int w2, int h2) {
      if (in.depth == null || in.depth.length < in.sw * in.sh) {
         return null;
      }
      float[] out = new float[w2 * h2];
      float n = in.near;
      float f = in.far;
      for (int y = 0; y < h2; y++) {
         int sy = clamp(in.cropY + (int)((y + 0.5) * in.cropH / h2), 0, in.sh - 1);
         int gy = in.sh - 1 - sy;
         float ny = 1.0F - 2.0F * (y + 0.5F) / h2;
         for (int x = 0; x < w2; x++) {
            int sx = clamp(in.cropX + (int)((x + 0.5) * in.cropW / w2), 0, in.sw - 1);
            float d = in.depth[gy * in.sw + sx];
            float nx = 2.0F * (x + 0.5F) / w2 - 1.0F;
            if (!(d < 0.999999F)) {
               out[y * w2 + x] = Float.POSITIVE_INFINITY;
               continue;
            }
            float z = 2.0F * n * f / (f + n - (2.0F * d - 1.0F) * (f - n));
            float rx = nx * in.tanH;
            float ry = ny * in.tanV;
            out[y * w2 + x] = z * (float)Math.sqrt(1.0F + rx * rx + ry * ry);
         }
      }
      return out;
   }

   /** A depth buffer that is flat, empty or cleared (some shader pipelines) is useless for the flash fall-off. */
   private static boolean depthPlausible(float[] dist, Input in) {
      int finite = 0;
      float min = Float.MAX_VALUE;
      float max = 0.0F;
      for (int i = 0; i < dist.length; i += 7) {
         float d = dist[i];
         if (Float.isFinite(d)) {
            finite++;
            min = Math.min(min, d);
            max = Math.max(max, d);
         }
      }
      return finite > dist.length / 7 / 20 && max - min > 1.0F;
   }

   /** Without depth: a ground plane under the lens, with the subjects' boxes at their measured distance. */
   private static float[] pseudoDistances(Input in, int w2, int h2) {
      float[] out = new float[w2 * h2];
      for (int y = 0; y < h2; y++) {
         float ny = 1.0F - 2.0F * (y + 0.5F) / h2;
         float down = -ny * in.tanV;
         for (int x = 0; x < w2; x++) {
            float nx = 2.0F * (x + 0.5F) / w2 - 1.0F;
            float d = down > 0.02F ? in.lensHeight / down : 60.0F;
            d *= (float)Math.sqrt(1.0F + nx * in.tanH * nx * in.tanH);
            out[y * w2 + x] = Math.min(d, 60.0F);
         }
      }
      // no per-pixel depth for the subjects: a soft elliptical flash hot-spot at their distance (never a hard box)
      for (Mark m : in.marks) {
         float cx = ((m.x0 + m.x1) * 0.25F + 0.5F) * w2;
         float cy = (0.5F - (m.y0 + m.y1) * 0.25F) * h2;
         float hw = Math.max(2.0F, (m.x1 - m.x0) * 0.25F * w2 * 1.15F);
         float hh = Math.max(2.0F, (m.y1 - m.y0) * 0.25F * h2 * 1.1F);
         int x0 = clamp((int)(cx - hw) - 1, 0, w2 - 1);
         int x1 = clamp((int)(cx + hw) + 1, 0, w2 - 1);
         int y0 = clamp((int)(cy - hh) - 1, 0, h2 - 1);
         int y1 = clamp((int)(cy + hh) + 1, 0, h2 - 1);
         for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
               float ex = (x + 0.5F - cx) / hw;
               float ey = (y + 0.5F - cy) / hh;
               float r = (float)Math.sqrt(ex * ex + ey * ey);
               float t = clampf((r - 0.45F) / 0.55F, 0.0F, 1.0F);
               float wgt = 1.0F - t * t * (3.0F - 2.0F * t);
               int i = y * w2 + x;
               if (wgt > 0.0F && m.distance < out[i]) {
                  out[i] = out[i] + (m.distance - out[i]) * wgt;
               }
            }
         }
      }
      return out;
   }

   /** Night shutter speeds smear a moving animal: blur along its motion, only on pixels at its depth. */
   private static void motionSmear(float[] big, float[] dist, int w2, int h2, Input in) {
      for (Mark m : in.marks) {
         float px = m.mx * 0.5F * w2;
         float py = -m.my * 0.5F * h2;
         float len = (float)Math.sqrt(px * px + py * py);
         if (len < 1.5F) {
            continue;
         }
         len = Math.min(len, 36.0F);
         int steps = Math.max(2, (int)Math.ceil(len));
         float dx = px / len * (len / steps);
         float dy = py / len * (len / steps);
         int x0 = clamp((int)((m.x0 * 0.5F + 0.5F) * w2) - 2, 0, w2 - 1);
         int x1 = clamp((int)((m.x1 * 0.5F + 0.5F) * w2) + 2, 0, w2 - 1);
         int y0 = clamp((int)((0.5F - m.y1 * 0.5F) * h2) - 2, 0, h2 - 1);
         int y1 = clamp((int)((0.5F - m.y0 * 0.5F) * h2) + 2, 0, h2 - 1);
         float tol = Math.max(0.8F, m.distance * 0.12F);
         float[] copy = big.clone();
         for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
               float d = dist[y * w2 + x];
               if (Math.abs(d - m.distance) > tol * 2.0F) {
                  continue;
               }
               float r = 0.0F;
               float g = 0.0F;
               float b = 0.0F;
               int n = 0;
               for (int s = -steps / 2; s <= steps / 2; s++) {
                  int sx = clamp(Math.round(x + dx * s), 0, w2 - 1);
                  int sy = clamp(Math.round(y + dy * s), 0, h2 - 1);
                  int o = (sy * w2 + sx) * 3;
                  r += copy[o];
                  g += copy[o + 1];
                  b += copy[o + 2];
                  n++;
               }
               int o = (y * w2 + x) * 3;
               float k = Math.abs(d - m.distance) <= tol ? 1.0F : 0.5F;
               big[o] += (r / n - big[o]) * k;
               big[o + 1] += (g / n - big[o + 1]) * k;
               big[o + 2] += (b / n - big[o + 2]) * k;
            }
         }
      }
   }

   /** Barrel distortion that keeps the corners, sampled with a 2x2 bilinear supersample from the 2x buffer. */
   private static void lens(float[] big, float[] dist, int w2, int h2, float[] img, float[] far, int w, int h) {
      float aspect = (float)w / h;
      float rmax2 = aspect * aspect + 1.0F;
      float norm = 1.0F + BARREL * rmax2;
      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            float r = 0.0F;
            float g = 0.0F;
            float b = 0.0F;
            float d = 0.0F;
            for (int s = 0; s < 4; s++) {
               float fx = (x + 0.25F + 0.5F * (s & 1)) / w * 2.0F - 1.0F;
               float fy = (y + 0.25F + 0.5F * (s >> 1)) / h * 2.0F - 1.0F;
               float ax = fx * aspect;
               float r2 = ax * ax + fy * fy;
               float k = (1.0F + BARREL * r2) / norm;
               float sx = (fx * k * 0.5F + 0.5F) * w2 - 0.5F;
               float sy = (fy * k * 0.5F + 0.5F) * h2 - 0.5F;
               int ix = clamp((int)Math.floor(sx), 0, w2 - 1);
               int iy = clamp((int)Math.floor(sy), 0, h2 - 1);
               int jx = Math.min(ix + 1, w2 - 1);
               int jy = Math.min(iy + 1, h2 - 1);
               float tx = clampf(sx - ix, 0.0F, 1.0F);
               float ty = clampf(sy - iy, 0.0F, 1.0F);
               for (int c = 0; c < 3; c++) {
                  float a = big[(iy * w2 + ix) * 3 + c] * (1 - tx) + big[(iy * w2 + jx) * 3 + c] * tx;
                  float bb = big[(jy * w2 + ix) * 3 + c] * (1 - tx) + big[(jy * w2 + jx) * 3 + c] * tx;
                  float v = a * (1 - ty) + bb * ty;
                  if (c == 0) {
                     r += v;
                  } else if (c == 1) {
                     g += v;
                  } else {
                     b += v;
                  }
               }
               if (far != null) {
                  float dd = dist[clamp(Math.round(sy), 0, h2 - 1) * w2 + clamp(Math.round(sx), 0, w2 - 1)];
                  d += Float.isFinite(dd) ? Math.min(dd, 400.0F) : 400.0F;
               }
            }
            int o = (y * w + x) * 3;
            img[o] = r * 0.25F;
            img[o + 1] = g * 0.25F;
            img[o + 2] = b * 0.25F;
            if (far != null) {
               far[y * w + x] = d * 0.25F;
            }
         }
      }
   }

   /** Maps an undistorted normalised position to the distorted output (inverse of the barrel sampling). */
   static float[] distort(float nx, float ny, float aspect) {
      float rmax2 = aspect * aspect + 1.0F;
      float norm = 1.0F + BARREL * rmax2;
      // solve p = q * (1 + B|q|^2) / norm for q (output) given p (source), a few Newton steps along the radius
      float ax = nx * aspect;
      float rp = (float)Math.sqrt(ax * ax + ny * ny);
      if (rp < 1.0E-5F) {
         return new float[]{nx, ny};
      }
      float rq = rp;
      for (int i = 0; i < 6; i++) {
         float f = rq * (1.0F + BARREL * rq * rq) / norm - rp;
         float df = (1.0F + 3.0F * BARREL * rq * rq) / norm;
         rq -= f / df;
      }
      float s = rq / rp;
      return new float[]{nx * s, ny * s};
   }

   // ------------------------------------------------------------------------------------------------ grading

   private static void daylight(float[] img, int w, int h) {
      for (int i = 0; i < w * h; i++) {
         int o = i * 3;
         float r = img[o];
         float g = img[o + 1];
         float b = img[o + 2];
         float l = 0.2126F * r + 0.7152F * g + 0.0722F * b;
         // small sensor: a little flat in the shadows, colours slightly muted, warm-green cast, soft highlight roll-off
         float sat = 0.86F;
         r = l + (r - l) * sat;
         g = l + (g - l) * sat;
         b = l + (b - l) * sat;
         r = r * 1.025F + 0.012F;
         g = g * 1.01F + 0.012F;
         b = b * 0.955F + 0.018F;
         img[o] = tone(r);
         img[o + 1] = tone(g);
         img[o + 2] = tone(b);
      }
   }

   private static float tone(float v) {
      v = clampf(v, 0.0F, 1.2F);
      float s = v * v * (3.0F - 2.0F * Math.min(v, 1.0F));
      float c = v + (s - v) * 0.22F;
      return c > 0.9F ? 0.9F + (1.0F - (float)Math.exp(-(c - 0.9F) * 6.0F)) * 0.1F : c;
   }

   private static float flash(float d) {
      float k = d / 7.5F;
      return 1.0F / (1.0F + k * k);
   }

   private static void infrared(float[] img, float[] far, int w, int h, Input in) {
      float aspect = (float)w / h;
      float exposure = clampf(1.2F / flash(Math.max(2.0F, in.subjectDistance)), 1.2F, 5.0F);
      for (int y = 0; y < h; y++) {
         float ny = 2.0F * (y + 0.5F) / h - 1.0F;
         for (int x = 0; x < w; x++) {
            float nx = 2.0F * (x + 0.5F) / w - 1.0F;
            int i = y * w + x;
            int o = i * 3;
            float r = img[o];
            float g = img[o + 1];
            float b = img[o + 2];
            // near infrared: chlorophyll reflects strongly, so leaves and grass come out bright ("Wood effect")
            float l = 0.34F * r + 0.5F * g + 0.16F * b;
            float leaf = g - Math.max(r, b);
            if (leaf > 0.0F) {
               l *= 1.0F + Math.min(0.75F, leaf * 5.0F);
            }
            float d = far[i];
            float light;
            if (d >= 390.0F) {
               light = 0.0F; // sky: the flash never reaches it
            } else {
               float rr = (nx * aspect) * (nx * aspect) * 0.28F + ny * ny * 0.5F;
               float beam = 1.0F - Math.min(0.55F, rr * 0.55F);
               light = flash(d) * beam * exposure;
            }
            float v = l * light + l * 0.018F;
            v = 1.0F - (float)Math.exp(-v * 1.7F);
            v = (float)Math.pow(Math.max(0.0F, v), 0.85F);
            img[o] = v * 0.985F;
            img[o + 1] = v;
            img[o + 2] = v * 1.02F;
         }
      }
      // IR sensors are soft: a light blur, then the highlights bloom a little
      float[] copy = img.clone();
      for (int y = 1; y < h - 1; y++) {
         for (int x = 1; x < w - 1; x++) {
            for (int c = 0; c < 3; c++) {
               int o = (y * w + x) * 3 + c;
               float sum = copy[o] * 4.0F + copy[o - 3] + copy[o + 3] + copy[o - w * 3] + copy[o + w * 3];
               float soft = sum / 8.0F;
               float bloom = Math.max(0.0F, soft - 0.82F) * 0.6F;
               img[o] = copy[o] * 0.55F + soft * 0.45F + bloom;
            }
         }
      }
   }

   private static void eyeshine(float[] img, float[] far, int w, int h, Input in, boolean depthOk) {
      float aspect = (float)w / h;
      float focal = (h * 0.5F) / Math.max(0.05F, in.tanV);
      for (Mark m : in.marks) {
         if (m.shine <= 0.01F) {
            continue;
         }
         for (float[] e : m.eyes) {
            float[] q = distort(e[0], e[1], aspect);
            float cx = (q[0] * 0.5F + 0.5F) * w;
            float cy = (0.5F - q[1] * 0.5F) * h;
            if (cx < -4 || cy < -4 || cx > w + 4 || cy > h + 4) {
               continue;
            }
            float ed = e[2];
            if (depthOk) {
               int px = clamp((int)cx, 0, w - 1);
               int py = clamp((int)cy, 0, h - 1);
               if (far[py * w + px] < ed - 0.45F) {
                  continue; // a branch or the animal's own head is in front of the eye
               }
            }
            float core = Math.max(0.75F, 0.03F * focal / Math.max(1.0F, ed));
            float glow = core * 2.8F;
            float s = m.shine * clampf(1.3F * flash(ed) * 3.0F, 0.35F, 1.0F);
            int r = (int)Math.ceil(glow * 2.5F);
            for (int y = (int)cy - r; y <= (int)cy + r; y++) {
               if (y < 0 || y >= h) {
                  continue;
               }
               for (int x = (int)cx - r; x <= (int)cx + r; x++) {
                  if (x < 0 || x >= w) {
                     continue;
                  }
                  float dx = x + 0.5F - cx;
                  float dy = y + 0.5F - cy;
                  float d2 = dx * dx + dy * dy;
                  float v = s * ((float)Math.exp(-d2 / (core * core)) * 1.4F + (float)Math.exp(-d2 / (glow * glow)) * 0.38F);
                  int o = (y * w + x) * 3;
                  img[o] += v;
                  img[o + 1] += v;
                  img[o + 2] += v;
               }
            }
         }
      }
   }

   private static void sharpen(float[] img, int w, int h, float amount) {
      float[] copy = img.clone();
      for (int y = 1; y < h - 1; y++) {
         for (int x = 1; x < w - 1; x++) {
            for (int c = 0; c < 3; c++) {
               int o = (y * w + x) * 3 + c;
               float blur = (copy[o - 3] + copy[o + 3] + copy[o - w * 3] + copy[o + w * 3]) * 0.25F;
               img[o] = copy[o] + (copy[o] - blur) * amount;
            }
         }
      }
   }

   private static void vignette(float[] img, int w, int h, float strength) {
      float aspect = (float)w / h;
      for (int y = 0; y < h; y++) {
         float ny = 2.0F * (y + 0.5F) / h - 1.0F;
         for (int x = 0; x < w; x++) {
            float nx = (2.0F * (x + 0.5F) / w - 1.0F) * aspect / (float)Math.sqrt(aspect * aspect + 1.0F);
            float nyy = ny / (float)Math.sqrt(aspect * aspect + 1.0F);
            float r2 = (nx * nx + nyy * nyy) * 2.0F;
            float v = 1.0F - strength * (float)Math.pow(r2 * 0.5F, 1.35F) * 1.6F;
            v = Math.max(0.35F, v);
            int o = (y * w + x) * 3;
            img[o] *= v;
            img[o + 1] *= v;
            img[o + 2] *= v;
         }
      }
   }

   private static void noise(float[] img, int w, int h, long seed, boolean ir) {
      Random rnd = new Random(seed);
      float luma = ir ? 0.034F : 0.011F;
      float chroma = ir ? 0.0F : 0.006F;
      for (int i = 0; i < w * h; i++) {
         int o = i * 3;
         float l = img[o] * 0.3F + img[o + 1] * 0.59F + img[o + 2] * 0.11F;
         // sensor noise is strongest in the mid-shadows
         float amp = luma * (0.55F + 1.2F * (float)Math.sqrt(Math.max(0.0F, l)) * (1.0F - l));
         float n = (float)rnd.nextGaussian() * amp;
         img[o] += n + (chroma > 0 ? (float)rnd.nextGaussian() * chroma : 0);
         img[o + 1] += n;
         img[o + 2] += n + (chroma > 0 ? (float)rnd.nextGaussian() * chroma : 0);
      }
   }

   // ------------------------------------------------------------------------------------------------ data strip

   private static void strip(int[] px, int w, int h, Strip s) {
      int scale = Math.max(1, Math.round(w / 640.0F));
      int pad = 2 * scale;
      int bar = 7 * scale + pad * 2 + scale;
      int top = h - bar;
      for (int y = top; y < h; y++) {
         for (int x = 0; x < w; x++) {
            px[y * w + x] = 0xFF050505;
         }
      }
      int ty = top + pad + scale / 2;
      int white = 0xFFF2F2F2;
      // right side, right-aligned: date, time, frame counter
      String right = s.date + "   " + s.time + "   " + s.frame;
      int rw = width(right, scale);
      int rx = w - pad * 2 - rw;
      text(px, w, h, rx, ty, scale, right, white);
      int x = pad * 2;
      String cam = s.camera.toUpperCase(java.util.Locale.ROOT);
      int room = Math.max(0, (rx - x - 12 * scale) / (6 * scale));
      if (cam.length() > room) {
         cam = cam.substring(0, room);
      }
      x = text(px, w, h, x, ty, scale, cam, white);
      // middle: moon phase icon + temperature, centred in the gap
      String temp = s.temperature;
      int mid = 9 * scale + 5 * scale + width(temp, scale);
      int mx = Math.max(x + 8 * scale, (x + rx) / 2 - mid / 2);
      if (mx + mid < rx - 4 * scale) {
         moon(px, w, h, mx, ty, scale, s.moonPhase, white);
         text(px, w, h, mx + 9 * scale + 5 * scale, ty, scale, temp, white);
      }
   }

   private static void moon(int[] px, int w, int h, int x0, int y0, int scale, int phase, int col) {
      // 0 full, 4 new (Minecraft); the lit side grows from the right (waxing) after new moon
      float size = 7 * scale;
      float cx = x0 + size / 2.0F + scale;
      float cy = y0 + size / 2.0F;
      float rad = size / 2.0F + 0.3F;
      double t = Math.floorMod(phase + 4, 8) / 8.0; // 0 new .. 0.5 full
      double k = Math.cos(t * Math.PI * 2.0); // 1 new, -1 full
      for (int y = (int)(cy - rad - 1); y <= cy + rad + 1; y++) {
         for (int x = (int)(cx - rad - 1); x <= cx + rad + 1; x++) {
            if (x < 0 || y < 0 || x >= w || y >= h) {
               continue;
            }
            float dx = x + 0.5F - cx;
            float dy = y + 0.5F - cy;
            float d = (float)Math.sqrt(dx * dx + dy * dy);
            if (d > rad) {
               continue;
            }
            float half = (float)Math.sqrt(Math.max(0.0F, rad * rad - dy * dy));
            float term = (float)(k * half);
            boolean lit = t < 0.5 ? dx > term : dx < -term;
            boolean edge = d > rad - scale;
            if (lit) {
               px[y * w + x] = col;
            } else if (edge) {
               px[y * w + x] = 0xFF8A8A8A;
            }
         }
      }
   }

   static int width(String s, int scale) {
      return s.length() * 6 * scale;
   }

   static int text(int[] px, int w, int h, int x, int y, int scale, String s, int col) {
      for (int i = 0; i < s.length(); i++) {
         long g = Font57.glyph(s.charAt(i));
         for (int row = 0; row < 7; row++) {
            for (int c = 0; c < 5; c++) {
               if ((g >> (row * 5 + (4 - c)) & 1L) == 0L) {
                  continue;
               }
               for (int sy = 0; sy < scale; sy++) {
                  for (int sx = 0; sx < scale; sx++) {
                     int xx = x + c * scale + sx;
                     int yy = y + row * scale + sy;
                     if (xx >= 0 && yy >= 0 && xx < w && yy < h) {
                        px[yy * w + xx] = col;
                     }
                  }
               }
            }
         }
         x += 6 * scale;
      }
      return x;
   }

   // ------------------------------------------------------------------------------------------------ util

   static int clamp(int v, int lo, int hi) {
      return v < lo ? lo : (v > hi ? hi : v);
   }

   static float clampf(float v, float lo, float hi) {
      return v < lo ? lo : (v > hi ? hi : v);
   }

   private static int clamp8(float v) {
      int i = Math.round(v * 255.0F);
      return i < 0 ? 0 : (i > 255 ? 255 : i);
   }
}
