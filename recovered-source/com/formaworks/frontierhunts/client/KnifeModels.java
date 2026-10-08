package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/**
 * Sculpted 3D knives replacing the old flat item sprites: the drop-point Field Knife (full tang, walnut scales,
 * brass pins, steel finger guard, lanyard tube) and the Contour Skinning Knife (upswept trailing point, contoured
 * G10 handle with finger grooves, orange liners, steel bolster and butt cap).
 *
 * <p>Blades are real cross-sections swept along the length: spine, chamfer, flat, primary grind and a polished
 * secondary edge bevel, each shaded like satin, ground or mirror-polished steel. Handles are rounded scale
 * sections with the steel tang visible between them. Textures come from one mip-mapped material sheet
 * (brushed steel, walnut, G10, brass) so they stay clean at any distance.
 *
 * <p>Native space: length along +Y (tip up), edge towards +Z, spine towards -Z, thickness along X, the front of
 * the guard at Y = 0. Units are blocks; each knife is about 1.2 long.
 */
public final class KnifeModels {
   static final ResourceLocation TEXTURE = FrontierHunts.id("textures/material/knife_materials.png");
   private static final int STEEL = 0;
   private static final int WOOD = 1;
   private static final int G10 = 2;
   private static final int BRASS = 3;
   private static Mesh field;
   private static Mesh skinner;

   private KnifeModels() {
   }

   /** Item-space draw (origin at the centre of the item cube), laid along the same diagonal as a sword sprite. */
   static void drawItem(boolean isSkinner, PoseStack pose, MultiBufferSource buffers, int light) {
      pose.pushPose();
      pose.mulPose(Axis.ZP.rotationDegrees(-45.0F));
      pose.mulPose(Axis.YP.rotationDegrees(90.0F));
      pose.translate(0.0F, isSkinner ? 0.06F : 0.03F, 0.0F);
      drawNative(isSkinner, pose, buffers, light);
      pose.popPose();
   }

   /** Native-space draw (see class notes). */
   static void drawNative(boolean isSkinner, PoseStack pose, MultiBufferSource buffers, int light) {
      FilteredFieldTexture.ensure(TEXTURE);
      Mesh mesh = isSkinner ? skinner() : field();
      VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
      Pose p = pose.last();
      float[] d = mesh.data;
      int[] c = mesh.colors;
      for (int i = 0, v = 0; v < c.length; i += 8, v++) {
         out.addVertex(p.pose(), d[i], d[i + 1], d[i + 2])
            .setColor(0xFF000000 | c[v])
            .setUv(d[i + 3], d[i + 4])
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(p, d[i + 5], d[i + 6], d[i + 7]);
      }
   }

   static synchronized Mesh field() {
      if (field == null) {
         field = buildField();
      }

      return field;
   }

   static synchronized Mesh skinner() {
      if (skinner == null) {
         skinner = buildSkinner();
      }

      return skinner;
   }

   // ------------------------------------------------------------------ Field Knife

   private static Mesh buildField() {
      Builder b = new Builder();
      final double len = 0.62;
      Blade blade = new Blade() {
         @Override
         double spine(double y) {
            double s = -0.075;
            if (y > 0.38) {
               s += 0.047 * Math.pow((y - 0.38) / (len - 0.38), 1.55);
            }

            return s;
         }

         @Override
         double edge(double y) {
            if (y < 0.03) {
               return 0.062;
            }

            if (y < 0.055) {
               return 0.062 - 0.016 * Math.sin((y - 0.03) / 0.025 * Math.PI);
            }

            double e = 0.062 + 0.023 * smooth((y - 0.055) / 0.05);
            if (y > 0.25) {
               e -= (0.085 + 0.028) * Math.pow((y - 0.25) / (len - 0.25), 2.05);
            }

            return e;
         }

         @Override
         double thickness(double y) {
            return 0.024 - 0.011 * Math.pow(y / len, 1.4);
         }

         @Override
         double grindLine(double y) {
            return 0.36;
         }

         @Override
         double ground(double y) {
            return smooth((y - 0.035) / 0.035);
         }
      };
      blade.build(b, 0.0, len, 110);
      spineJimping(b, blade, 0.045, 0.11, 7);
      // finger guard
      roundedSlab(b, -0.032, 0.0, -0.028, 0.028, -0.086, 0.108, 4.0, STEEL, 0x9EA4AA);
      Handle h = new Handle() {
         @Override
         double top(double y) {
            double t = -y;
            return -0.066 - 0.006 * bump(t, 0.34, 0.2) + 0.012 * smooth((t - 0.5) / 0.06);
         }

         @Override
         double bottom(double y) {
            double t = -y;
            return 0.066 - 0.016 * bump(t, 0.085, 0.045) + 0.006 * bump(t, 0.3, 0.15) - 0.014 * smooth((t - 0.48) / 0.08);
         }

         @Override
         double half(double y) {
            double t = -y;
            return 0.036 + 0.007 * bump(t, 0.33, 0.2) - 0.004 * smooth((t - 0.48) / 0.08);
         }
      };
      h.tang = 0.0085;
      h.material = WOOD;
      h.color = 0xFFFFFF;
      h.linerColor = -1;
      h.build(b, -0.032, -0.58, 70);
      for (double py : new double[]{-0.13, -0.33}) {
         pins(b, h, py, 0.0115, BRASS, 0xF2D9A0, 0x3A2A12);
      }

      lanyard(b, h, -0.51);
      return b.mesh();
   }

   // ------------------------------------------------------------------ Contour Skinning Knife

   private static Mesh buildSkinner() {
      Builder b = new Builder();
      final double len = 0.5;
      Blade blade = new Blade() {
         @Override
         double spine(double y) {
            if (y < 0.26) {
               return -0.07 + 0.012 * Math.pow(y / 0.26, 1.3);
            }

            return -0.058 - 0.05 * Math.pow((y - 0.26) / (len - 0.26), 1.6);
         }

         @Override
         double edge(double y) {
            if (y < 0.03) {
               return 0.052;
            }

            double e = 0.052 + 0.045 * smooth((y - 0.03) / 0.17);
            if (y > 0.2) {
               e -= 0.205 * Math.pow((y - 0.2) / (len - 0.2), 1.75);
            }

            return e;
         }

         @Override
         double thickness(double y) {
            return 0.021 - 0.009 * Math.pow(y / len, 1.3);
         }

         @Override
         double grindLine(double y) {
            return 0.2;
         }

         @Override
         double ground(double y) {
            return smooth((y - 0.03) / 0.03);
         }
      };
      blade.build(b, 0.0, len, 110);
      spineJimping(b, blade, 0.03, 0.1, 8);
      // integral bolster with a lower finger guard
      roundedSlab(b, -0.045, 0.0, -0.027, 0.027, -0.074, 0.09, 3.2, STEEL, 0xA8AEB4);
      Handle h = new Handle() {
         @Override
         double top(double y) {
            double t = -y;
            return -0.064 - 0.01 * bump(t, 0.3, 0.2) + 0.008 * smooth((t - 0.5) / 0.05);
         }

         @Override
         double bottom(double y) {
            double t = -y;
            double grooves = 0.0;
            for (double c : new double[]{0.1, 0.19, 0.28, 0.37}) {
               grooves += bump(t, c, 0.03);
            }

            return 0.07 - 0.011 * grooves + 0.004 * bump(t, 0.46, 0.05) - 0.01 * smooth((t - 0.5) / 0.05);
         }

         @Override
         double half(double y) {
            double t = -y;
            return 0.037 + 0.008 * bump(t, 0.3, 0.18);
         }
      };
      h.tang = 0.0075;
      h.material = G10;
      h.color = 0xFFFFFF;
      h.linerColor = 0xE0632A;
      h.build(b, -0.045, -0.535, 70);
      for (double py : new double[]{-0.14, -0.38}) {
         pins(b, h, py, 0.0105, STEEL, 0xD6DADE, 0x2A2C2E);
      }

      // steel butt cap
      roundedSlab(b, -0.585, -0.535, -0.036, 0.036, -0.058, 0.064, 3.0, STEEL, 0xA2A8AE);
      ringX(b, -0.562, 0.004, 0.037, 0.0125, 0.0075, 0xC8CCD0, 0x151515);
      return b.mesh();
   }

   // ------------------------------------------------------------------ parts

   /** Small notches across the spine near the guard for the thumb. */
   private static void spineJimping(Builder b, Blade blade, double from, double to, int count) {
      for (int i = 0; i < count; i++) {
         double y = from + (to - from) * (i + 0.5) / count;
         double s = blade.spine(y);
         double t = blade.thickness(y) * 0.5 - 0.0015;
         double w = (to - from) / count * 0.38;
         b.box(-t, y - w, s - 0.0035, t, y + w, s + 0.0006, STEEL, 0x4A4E52);
      }
   }

   /** A rounded (superellipse) slab extruded along Y from y0 to y1: guards, bolsters, butt caps. */
   private static void roundedSlab(Builder b, double y0, double y1, double x0, double x1, double z0, double z1, double n, int tile, int color) {
      int seg = 28;
      double cx = (x0 + x1) * 0.5;
      double cz = (z0 + z1) * 0.5;
      double rx = (x1 - x0) * 0.5;
      double rz = (z1 - z0) * 0.5;
      double bevel = Math.min(rx, rz) * 0.22;
      double[][] rings = new double[][]{{y0, 1.0 - bevel / rx, 1.0 - bevel / rz}, {y0 + bevel, 1.0, 1.0}, {y1 - bevel, 1.0, 1.0}, {y1, 1.0 - bevel / rx, 1.0 - bevel / rz}};
      double[][][] pts = new double[rings.length][seg][];
      for (int r = 0; r < rings.length; r++) {
         for (int i = 0; i < seg; i++) {
            double a = Math.PI * 2 * i / seg;
            double c = Math.cos(a);
            double s = Math.sin(a);
            double px = Math.signum(c) * Math.pow(Math.abs(c), 2.0 / n);
            double pz = Math.signum(s) * Math.pow(Math.abs(s), 2.0 / n);
            pts[r][i] = new double[]{cx + px * rx * rings[r][1], rings[r][0], cz + pz * rz * rings[r][2], px, pz};
         }
      }

      for (int r = 0; r < rings.length - 1; r++) {
         for (int i = 0; i < seg; i++) {
            double[] a = pts[r][i];
            double[] bb = pts[r][(i + 1) % seg];
            double[] c = pts[r + 1][(i + 1) % seg];
            double[] d = pts[r + 1][i];
            double shade = r == 1 ? 1.0 : 1.12;
            int col = scale(color, shade * env(a[3], a[4]));
            double u0 = (double)i / seg;
            double u1 = (double)(i + 1) / seg;
            b.quadFlat(tile, col, a[0], a[1], a[2], u0, 0.0, bb[0], bb[1], bb[2], u1, 0.0, c[0], c[1], c[2], u1, 1.0, d[0], d[1], d[2], u0, 1.0, a[3], 0.0, a[4]);
         }
      }

      // end caps
      for (int end = 0; end < 2; end++) {
         double[][] ring = pts[end == 0 ? 0 : rings.length - 1];
         double ny = end == 0 ? -1.0 : 1.0;
         for (int i = 0; i < seg; i++) {
            double[] a = ring[i];
            double[] bb = ring[(i + 1) % seg];
            b.quadFlat(tile, scale(color, 0.92), cx, a[1], cz, 0.5, 0.5, a[0], a[1], a[2], 0.5 + a[3] * 0.5, 0.5 + a[4] * 0.5, bb[0], bb[1], bb[2], 0.5 + bb[3] * 0.5, 0.5 + bb[4] * 0.5, cx, a[1], cz, 0.5, 0.5, 0.0, ny, 0.0);
         }
      }
   }

   /** Pins through both scales: a proud head with a darker ring where it meets the wood. */
   private static void pins(Builder b, Handle h, double y, double r, int tile, int head, int ring) {
      double z = (h.top(y) + h.bottom(y)) * 0.5;
      double w = h.half(y);
      for (int side = -1; side <= 1; side += 2) {
         diskX(b, side * (w + 0.0005), y, z, r * 1.25, side, ring, STEEL);
         cylinderX(b, side * (w - 0.004), side * (w + 0.0025), y, z, r, side, head, tile);
      }
   }

   private static void lanyard(Builder b, Handle h, double y) {
      double z = (h.top(y) + h.bottom(y)) * 0.5 + 0.004;
      ringX(b, y, z, h.half(y) + 0.0025, 0.0135, 0.008, 0xC4C9CE, 0x121212);
   }

   /** A tube running across the handle (lanyard hole): steel rim on both faces with a dark bore. */
   private static void ringX(Builder b, double y, double z, double halfWidth, double outer, double inner, int rim, int hole) {
      int seg = 20;
      for (int side = -1; side <= 1; side += 2) {
         double x = side * halfWidth;
         for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            double c0 = Math.cos(a0);
            double s0 = Math.sin(a0);
            double c1 = Math.cos(a1);
            double s1 = Math.sin(a1);
            b.quadFlat(
               STEEL, rim, x, y + c0 * inner, z + s0 * inner, 0, 0, x, y + c0 * outer, z + s0 * outer, 1, 0, x, y + c1 * outer, z + s1 * outer, 1, 1, x, y + c1 * inner, z + s1 * inner, 0, 1, side, 0, 0
            );
            b.quadFlat(
               STEEL, hole, x * 0.3, y, z, 0.5, 0.5, x * 0.98, y + c0 * inner, z + s0 * inner, 0, 0, x * 0.98, y + c1 * inner, z + s1 * inner, 1, 0, x * 0.3, y, z, 0.5, 0.5, side, 0, 0
            );
         }
      }
   }

   private static void diskX(Builder b, double x, double y, double z, double r, int side, int color, int tile) {
      int seg = 18;
      for (int i = 0; i < seg; i++) {
         double a0 = Math.PI * 2 * i / seg;
         double a1 = Math.PI * 2 * (i + 1) / seg;
         b.quadFlat(
            tile, color, x, y, z, 0.5, 0.5, x, y + Math.cos(a0) * r, z + Math.sin(a0) * r, 0.5 + Math.cos(a0) * 0.5, 0.5 + Math.sin(a0) * 0.5,
            x, y + Math.cos(a1) * r, z + Math.sin(a1) * r, 0.5 + Math.cos(a1) * 0.5, 0.5 + Math.sin(a1) * 0.5, x, y, z, 0.5, 0.5, side, 0, 0
         );
      }
   }

   private static void cylinderX(Builder b, double x0, double x1, double y, double z, double r, int side, int color, int tile) {
      int seg = 18;
      for (int i = 0; i < seg; i++) {
         double a0 = Math.PI * 2 * i / seg;
         double a1 = Math.PI * 2 * (i + 1) / seg;
         double c0 = Math.cos(a0);
         double s0 = Math.sin(a0);
         double c1 = Math.cos(a1);
         double s1 = Math.sin(a1);
         b.quadFlat(
            tile, scale(color, 0.85), x0, y + c0 * r, z + s0 * r, 0, 0, x1, y + c0 * r, z + s0 * r, 1, 0, x1, y + c1 * r, z + s1 * r, 1, 1, x0, y + c1 * r, z + s1 * r, 0, 1,
            0, (c0 + c1) * 0.5, (s0 + s1) * 0.5
         );
      }

      // slightly domed head
      double top = x1;
      double dome = side * r * 0.25;
      for (int i = 0; i < seg; i++) {
         double a0 = Math.PI * 2 * i / seg;
         double a1 = Math.PI * 2 * (i + 1) / seg;
         b.quadSmooth(
            tile,
            color,
            new double[]{top + dome, y, z, 0.5, 0.5, side, 0, 0},
            new double[]{top, y + Math.cos(a0) * r, z + Math.sin(a0) * r, 0.5 + Math.cos(a0) * 0.5, 0.5 + Math.sin(a0) * 0.5, side * 0.6, Math.cos(a0) * 0.8, Math.sin(a0) * 0.8},
            new double[]{top, y + Math.cos(a1) * r, z + Math.sin(a1) * r, 0.5 + Math.cos(a1) * 0.5, 0.5 + Math.sin(a1) * 0.5, side * 0.6, Math.cos(a1) * 0.8, Math.sin(a1) * 0.8},
            new double[]{top + dome, y, z, 0.5, 0.5, side, 0, 0}
         );
      }
   }

   // ------------------------------------------------------------------ blade sweep

   private abstract static class Blade {
      abstract double spine(double y);

      abstract double edge(double y);

      abstract double thickness(double y);

      /** Fraction of the blade height (from the spine) where the primary grind starts. */
      abstract double grindLine(double y);

      /** 0 = unground ricasso, 1 = fully ground. */
      abstract double ground(double y);

      void build(Builder b, double y0, double y1, int steps) {
         double[][] prev = null;
         double prevY = 0.0;
         for (int i = 0; i <= steps; i++) {
            double s = (double)i / steps;
            // denser sampling towards the tip where the profile turns fastest
            double y = y0 + (y1 - y0) * (1.0 - Math.pow(1.0 - s, 1.35));
            double[][] sec = this.section(y, i == steps);
            if (prev != null) {
               this.strip(b, prev, sec, prevY, y, y0, y1);
            }

            prev = sec;
            prevY = y;
         }

         // back face hidden inside the guard, closed anyway
         double[][] first = this.section(y0, false);
         for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < first.length - 1; k++) {
               double[] a = first[k];
               double[] c = first[k + 1];
               b.quadFlat(STEEL, 0x80868C, side * a[0], y0, a[1], 0, 0, side * c[0], y0, c[1], 0, 1, 0, y0, c[1], 1, 1, 0, y0, a[1], 1, 0, 0, -1, 0);
            }
         }
      }

      /** Cross-section on the +X side, spine to edge: {x, z} pairs. */
      double[][] section(double y, boolean tip) {
         double s = this.spine(y);
         double e = this.edge(y);
         if (tip || e - s < 1.0E-4) {
            double m = (s + e) * 0.5;
            return new double[][]{{0, m}, {0, m}, {0, m}, {0, m}, {0, m}};
         }

         double h = e - s;
         double t = this.thickness(y) * 0.5 * Math.min(1.0, h / 0.05 + 0.25);
         double g = this.ground(y);
         double cham = Math.min(t * 0.35, h * 0.08);
         double grindZ = s + h * (this.grindLine(y) + (1.0 - g) * (0.96 - this.grindLine(y)));
         double bevelH = Math.min(0.011, h * 0.16);
         double bevelZ = e - bevelH;
         double bevelT = 0.0017 + (1.0 - g) * (t - 0.0017);
         if (grindZ > bevelZ) {
            grindZ = bevelZ;
         }

         double edgeT = (1.0 - g) * t * 0.6;
         return new double[][]{{t - cham, s}, {t, s + cham}, {t, grindZ}, {bevelT, bevelZ}, {edgeT, e}};
      }

      private void strip(Builder b, double[][] a, double[][] c, double ya, double yc, double y0, double y1) {
         double ua = (ya - y0) / (y1 - y0);
         double uc = (yc - y0) / (y1 - y0);
         for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < a.length - 1; k++) {
               double[] p0 = a[k];
               double[] p1 = a[k + 1];
               double[] q0 = c[k];
               double[] q1 = c[k + 1];
               double midZ = (p0[1] + p1[1]) * 0.5;
               double hA = Math.max(1.0E-4, a[4][1] - a[0][1]);
               double rel = Math.clamp((midZ - a[0][1]) / hA, 0.0, 1.0);
               int col;
               switch (k) {
                  case 0 -> col = scale(0xB4BAC0, 1.0);
                  case 1 -> col = scale(0xC4CAD0, 0.8 + 0.14 * rel + 0.05 * Math.sin(ua * 7.0 + rel * 3.0));
                  case 2 -> col = scale(0xE2E7EC, 1.12 - 0.24 * rel);
                  default -> col = 0xFFFFFF;
               }

               double va0 = (p0[1] + 0.12) / 0.26;
               double va1 = (p1[1] + 0.12) / 0.26;
               double vc0 = (q0[1] + 0.12) / 0.26;
               double vc1 = (q1[1] + 0.12) / 0.26;
               b.quadAuto(
                  STEEL, col, side * p0[0], ya, p0[1], ua, va0, side * p1[0], ya, p1[1], ua, va1, side * q1[0], yc, q1[1], uc, vc1, side * q0[0], yc, q0[1], uc, vc0,
                  side, 0.0, k == 3 ? 1.0 : 0.0
               );
            }
         }

         // spine top strip
         double[] p = a[0];
         double[] q = c[0];
         b.quadAuto(STEEL, 0x9CA2A8, -p[0], ya, p[1], ua, 0.1, p[0], ya, p[1], ua, 0.2, q[0], yc, q[1], uc, 0.2, -q[0], yc, q[1], uc, 0.1, 0.0, 0.0, -1.0);
         // edge strip (non-zero only on the ricasso where the edge is left square)
         double[] pe = a[4];
         double[] qe = c[4];
         if (pe[0] > 1.0E-4 || qe[0] > 1.0E-4) {
            b.quadAuto(STEEL, 0xA0A6AC, -pe[0], ya, pe[1], ua, 0.8, pe[0], ya, pe[1], ua, 0.9, qe[0], yc, qe[1], uc, 0.9, -qe[0], yc, qe[1], uc, 0.8, 0.0, 0.0, 1.0);
         }
      }
   }

   // ------------------------------------------------------------------ handle sweep

   private abstract static class Handle {
      double tang;
      int material;
      int color;
      int linerColor;

      abstract double top(double y);

      abstract double bottom(double y);

      abstract double half(double y);

      /**
       * Sweeps from y0 (at the guard) back to y1 (butt). Section: steel tang strip across the top, rounded scale on
       * each side, tang strip across the bottom; the last few percent close into a rounded butt.
       */
      void build(Builder b, double y0, double y1, int steps) {
         int arc = 14;
         double[][][] rings = new double[steps + 1][][];
         double[] ys = new double[steps + 1];
         for (int i = 0; i <= steps; i++) {
            double s = (double)i / steps;
            double y = y0 + (y1 - y0) * s;
            ys[i] = y;
            double close = s < 0.93 ? 1.0 : Math.sqrt(Math.max(0.0, 1.0 - Math.pow((s - 0.93) / 0.07, 2.0)));
            close = Math.max(close, 0.02);
            double top = this.top(y);
            double bot = this.bottom(y);
            double mid = (top + bot) * 0.5;
            double hh = (bot - top) * 0.5 * close;
            double w = this.half(y) * close;
            double tg = Math.min(this.tang, w * 0.6);
            // points go around: right scale from top to bottom, then left scale from bottom to top
            double[][] ring = new double[2 * (arc + 1)][];
            for (int k = 0; k <= arc; k++) {
               double a = -Math.PI / 2 + Math.PI * k / arc;
               double c = Math.cos(a);
               double sn = Math.sin(a);
               double px = tg + (w - tg) * Math.pow(Math.max(0.0, c), 0.62);
               double pz = mid + hh * Math.signum(sn) * Math.pow(Math.abs(sn), 0.8);
               double nx = Math.pow(Math.max(0.0, c), 0.6);
               double nz = sn;
               double nl = Math.sqrt(nx * nx + nz * nz);
               ring[k] = new double[]{px, pz, nx / nl, nz / nl};
               ring[2 * (arc + 1) - 1 - k] = new double[]{-px, pz, -nx / nl, nz / nl};
            }

            rings[i] = ring;
         }

         int n = rings[0].length;
         for (int i = 0; i < steps; i++) {
            double ua = (ys[i] - y0) / (y1 - y0);
            double uc = (ys[i + 1] - y0) / (y1 - y0);
            for (int k = 0; k < n; k++) {
               int k2 = (k + 1) % n;
               double[] p0 = rings[i][k];
               double[] p1 = rings[i][k2];
               double[] q0 = rings[i + 1][k];
               double[] q1 = rings[i + 1][k2];
               boolean tangFace = k == arc || k == n - 1;
               int tile = tangFace ? STEEL : this.material;
               int col;
               if (tangFace) {
                  col = 0xA4AAB0;
               } else {
                  boolean liner = this.linerColor >= 0 && (k == 0 || k == arc - 1 || k == arc + 1 || k == n - 2);
                  col = liner ? this.linerColor : scale(this.color, 0.88 + 0.12 * Math.abs(p0[2]));
               }

               double v0 = (double)k / n;
               double v1 = (double)(k + 1) / n;
               b.quadSmooth(
                  tile,
                  col,
                  new double[]{p0[0], ys[i], p0[1], ua, v0, p0[2], 0, p0[3]},
                  new double[]{p1[0], ys[i], p1[1], ua, v1, p1[2], 0, p1[3]},
                  new double[]{q1[0], ys[i + 1], q1[1], uc, v1, q1[2], 0, q1[3]},
                  new double[]{q0[0], ys[i + 1], q0[1], uc, v0, q0[2], 0, q0[3]}
               );
            }
         }
      }
   }

   // ------------------------------------------------------------------ helpers

   static double smooth(double t) {
      t = Math.clamp(t, 0.0, 1.0);
      return t * t * (3.0 - 2.0 * t);
   }

   static double bump(double t, double centre, double width) {
      double d = (t - centre) / width;
      return Math.exp(-d * d * 2.0);
   }

   /** Fixed studio reflection so bare steel reads as metal whatever the world lighting. */
   static double env(double nx, double nz) {
      return 0.82 + 0.18 * Math.max(-1.0, Math.min(1.0, -nz * 0.8 + nx * 0.2));
   }

   static int scale(int rgb, double f) {
      int r = (int)Math.clamp(((rgb >> 16) & 255) * f, 0.0, 255.0);
      int g = (int)Math.clamp(((rgb >> 8) & 255) * f, 0.0, 255.0);
      int bl = (int)Math.clamp((rgb & 255) * f, 0.0, 255.0);
      return r << 16 | g << 8 | bl;
   }

   static final class Mesh {
      final float[] data;
      final int[] colors;

      Mesh(float[] data, int[] colors) {
         this.data = data;
         this.colors = colors;
      }

      /** Raw vertices for tools: {x, y, z, u, v, nx, ny, nz} per vertex, four per quad. */
      public float[] data() {
         return this.data;
      }

      public int[] colors() {
         return this.colors;
      }
   }

   private static final class Builder {
      private float[] data = new float[8 * 4096];
      private int[] colors = new int[4096];
      private int count;

      Mesh mesh() {
         float[] d = new float[this.count * 8];
         int[] c = new int[this.count];
         System.arraycopy(this.data, 0, d, 0, d.length);
         System.arraycopy(this.colors, 0, c, 0, c.length);
         return new Mesh(d, c);
      }

      private void put(int tile, int color, double x, double y, double z, double u, double v, double nx, double ny, double nz) {
         if (this.count == this.colors.length) {
            float[] nd = new float[this.data.length * 2];
            int[] nc = new int[this.colors.length * 2];
            System.arraycopy(this.data, 0, nd, 0, this.data.length);
            System.arraycopy(this.colors, 0, nc, 0, this.colors.length);
            this.data = nd;
            this.colors = nc;
         }

         double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
         if (len < 1.0E-9) {
            nx = 0.0;
            ny = 1.0;
            nz = 0.0;
            len = 1.0;
         }

         int o = this.count * 8;
         this.data[o] = (float)x;
         this.data[o + 1] = (float)y;
         this.data[o + 2] = (float)z;
         this.data[o + 3] = (float)((tile % 2) * 0.5 + 0.008 + Math.clamp(u, 0.0, 1.0) * 0.484);
         this.data[o + 4] = (float)((tile / 2) * 0.5 + 0.008 + Math.clamp(v, 0.0, 1.0) * 0.484);
         this.data[o + 5] = (float)(nx / len);
         this.data[o + 6] = (float)(ny / len);
         this.data[o + 7] = (float)(nz / len);
         this.colors[this.count] = color & 0xFFFFFF;
         this.count++;
      }

      /** Flat-shaded quad; the geometric normal is oriented to agree with the hint. */
      void quadFlat(
         int tile, int color,
         double x0, double y0, double z0, double u0, double v0,
         double x1, double y1, double z1, double u1, double v1,
         double x2, double y2, double z2, double u2, double v2,
         double x3, double y3, double z3, double u3, double v3,
         double hx, double hy, double hz
      ) {
         Vector3f n = new Vector3f((float)(x1 - x0), (float)(y1 - y0), (float)(z1 - z0)).cross((float)(x2 - x0), (float)(y2 - y0), (float)(z2 - z0));
         if (n.lengthSquared() < 1.0E-14F) {
            n.set((float)(x2 - x0), (float)(y2 - y0), (float)(z2 - z0)).cross((float)(x3 - x0), (float)(y3 - y0), (float)(z3 - z0));
         }

         if (n.lengthSquared() < 1.0E-14F) {
            n.set((float)hx, (float)hy, (float)hz);
         }

         if (n.dot((float)hx, (float)hy, (float)hz) < 0.0F) {
            n.negate();
         }

         this.put(tile, color, x0, y0, z0, u0, v0, n.x, n.y, n.z);
         this.put(tile, color, x1, y1, z1, u1, v1, n.x, n.y, n.z);
         this.put(tile, color, x2, y2, z2, u2, v2, n.x, n.y, n.z);
         this.put(tile, color, x3, y3, z3, u3, v3, n.x, n.y, n.z);
      }

      /** Flat quad whose normal is taken from the geometry, oriented towards the hint (x sign, y, z). */
      void quadAuto(
         int tile, int color,
         double x0, double y0, double z0, double u0, double v0,
         double x1, double y1, double z1, double u1, double v1,
         double x2, double y2, double z2, double u2, double v2,
         double x3, double y3, double z3, double u3, double v3,
         double hx, double hy, double hz
      ) {
         this.quadFlat(tile, color, x0, y0, z0, u0, v0, x1, y1, z1, u1, v1, x2, y2, z2, u2, v2, x3, y3, z3, u3, v3, hx, hy, hz);
      }

      /** Smooth quad with per-vertex normals: each vertex is {x, y, z, u, v, nx, ny, nz}. */
      void quadSmooth(int tile, int color, double[] a, double[] b, double[] c, double[] d) {
         for (double[] p : new double[][]{a, b, c, d}) {
            this.put(tile, color, p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7]);
         }
      }

      /** Axis-aligned box (for small details such as jimping). */
      void box(double x0, double y0, double z0, double x1, double y1, double z1, int tile, int color) {
         this.quadFlat(tile, color, x0, y0, z0, 0, 0, x1, y0, z0, 1, 0, x1, y1, z0, 1, 1, x0, y1, z0, 0, 1, 0, 0, -1);
         this.quadFlat(tile, color, x0, y0, z1, 0, 0, x0, y1, z1, 0, 1, x1, y1, z1, 1, 1, x1, y0, z1, 1, 0, 0, 0, 1);
         this.quadFlat(tile, color, x0, y0, z0, 0, 0, x0, y1, z0, 1, 0, x0, y1, z1, 1, 1, x0, y0, z1, 0, 1, -1, 0, 0);
         this.quadFlat(tile, color, x1, y0, z0, 0, 0, x1, y0, z1, 0, 1, x1, y1, z1, 1, 1, x1, y1, z0, 1, 0, 1, 0, 0);
         this.quadFlat(tile, color, x0, y1, z0, 0, 0, x1, y1, z0, 1, 0, x1, y1, z1, 1, 1, x0, y1, z1, 0, 1, 0, 1, 0);
         this.quadFlat(tile, color, x0, y0, z0, 0, 0, x0, y0, z1, 0, 1, x1, y0, z1, 1, 1, x1, y0, z0, 1, 0, 0, -1, 0);
      }
   }
}
