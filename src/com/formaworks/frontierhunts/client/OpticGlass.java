package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * [gunsmith] Coated optical glass for scopes, prisms, red dots and binocular-type optics (vertex-coloured, translucent,
 * drawn on one white texel of the field material atlas, so it costs nothing beyond its few hundred quads).
 *
 * <p>Lens model (rr = normalised radius): the eye sees down a dark tube, so the centre is a deep blue-black; towards
 * the rim the glass picks up the multi-coating's green / magenta sheen (hue shifts round the lens), a crisp window
 * reflection sits upper left with a fainter counter-glint lower right, and the outermost band darkens where the
 * retaining ring shadows the glass. The surface is slightly domed. Red-dot windows: nearly clear in the middle, a faint
 * cyan tint, the emitter coating's amber-rose reflection as a diagonal band, and denser glass at the frame.
 */
final class OpticGlass {
   private static final float U = 0.875F;
   private static final float V = 0.125F;

   private OpticGlass() {
   }

   private static int lensRings() {
      return "distant".equals(FieldWeaponMesh.lod) ? 4 : ("field".equals(FieldWeaponMesh.lod) ? 6 : 8);
   }

   private static int lensSegments() {
      return "distant".equals(FieldWeaponMesh.lod) ? 24 : ("field".equals(FieldWeaponMesh.lod) ? 36 : 48);
   }

   /** A round lens at depth z, radius r, facing dir (+1 towards the shooter, -1 towards the target). */
   static void lens(PoseStack pose, MultiBufferSource buffers, int light, double z, double r, int dir) {
      VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(FieldMaterials.ATLAS));
      Pose p = pose.last();
      int rings = lensRings();
      int seg = lensSegments();
      double dome = Math.min(0.0018, r * 0.07);
      for (int i = 0; i < rings; i++) {
         for (int j = 0; j < seg; j++) {
            for (int k = 0; k < 4; k++) {
               // quad corners in the same winding as the original glass: (i,j) (i+1,j) (i+1,j+1) (i,j+1)
               int ri = i + (k == 1 || k == 2 ? 1 : 0);
               int sj = j + (k >= 2 ? 1 : 0);
               double rr = (double)ri / rings;
               double a = sj * Math.PI * 2.0 / seg;
               double x = Math.cos(a) * rr * r;
               double y = Math.sin(a) * rr * r;
               int[] c = lensColour(rr, Math.cos(a) * rr, Math.sin(a) * rr, dir);
               out.addVertex(p.pose(), (float)x, (float)y, (float)(z + dir * (1.0 - rr * rr) * dome))
                  .setColor(c[0], c[1], c[2], c[3])
                  .setUv(U, V)
                  .setOverlay(OverlayTexture.NO_OVERLAY)
                  .setLight(light)
                  .setNormal(p, (float)(x / r * 0.45), (float)(y / r * 0.45), (float)dir);
            }
         }
      }
   }

   /** RGBA for a lens point (nx, ny in -1..1 of the lens radius). Public for the offline bench. */
   static int[] lensColour(double rr, double nx, double ny, int dir) {
      // deep tube interior
      double r = 7 + 10 * rr * rr;
      double g = 11 + 22 * rr * rr;
      double b = 17 + 26 * rr * rr;
      // multi-coating sheen: green on one side, magenta on the other, strongest two-thirds out
      double band = Math.exp(-Math.pow((rr - 0.72) / 0.2, 2));
      double hue = 0.5 + 0.5 * Math.sin(Math.atan2(ny, nx) * 1.0 + 0.6);
      r += band * (16 + 34 * (1 - hue));
      g += band * (30 + 28 * hue);
      b += band * (22 + 30 * (1 - hue));
      // window reflection upper left (sharp), counter-glint lower right (soft)
      double sx = nx + 0.34, sy = ny - 0.40;
      double glint = Math.exp(-(sx * sx / 0.030 + sy * sy / 0.012 + sx * sy / 0.03));
      double glint2 = Math.exp(-(Math.pow(nx - 0.42, 2) + Math.pow(ny + 0.38, 2)) / 0.025) * 0.35;
      double s = Math.min(1.0, glint + glint2);
      r += s * 175;
      g += s * 190;
      b += s * 200;
      // retaining-ring shadow at the rim
      double rim = Math.max(0.0, (rr - 0.9) / 0.1);
      r *= 1 - 0.45 * rim;
      g *= 1 - 0.45 * rim;
      b *= 1 - 0.45 * rim;
      double alpha = 170 + 40 * rr * rr + s * 45;
      return new int[]{clamp(r), clamp(g), clamp(b), clamp(alpha)};
   }

   /** Flat red-dot / holographic window, x0..x1 / y0..y1 at depth z, normal (0,0,-1). */
   static void window(PoseStack pose, MultiBufferSource buffers, int light, double x0, double x1, double y0, double y1, double z) {
      VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(FieldMaterials.ATLAS));
      Pose p = pose.last();
      int n = "distant".equals(FieldWeaponMesh.lod) ? 2 : 6;
      for (int i = 0; i < n; i++) {
         for (int j = 0; j < n; j++) {
            for (int k = 0; k < 4; k++) {
               // original winding: corner index 3 - k -> (x sign, y top) like FieldReflexSight
               int q = 3 - k;
               int ix = i + (q == 1 || q == 2 ? 1 : 0);
               int iy = j + (q >= 2 ? 1 : 0);
               double fx = (double)ix / n;
               double fy = (double)iy / n;
               int[] c = windowColour(fx, fy);
               out.addVertex(p.pose(), (float)(x0 + (x1 - x0) * fx), (float)(y0 + (y1 - y0) * fy), (float)z)
                  .setColor(c[0], c[1], c[2], c[3])
                  .setUv(U, V)
                  .setOverlay(OverlayTexture.NO_OVERLAY)
                  .setLight(light)
                  .setNormal(p, 0.0F, 0.0F, -1.0F);
            }
         }
      }
   }

   /** Red-dot window colour at (fx, fy) in 0..1 across the window. */
   static int[] windowColour(double fx, double fy) {
      double ex = Math.abs(fx - 0.5) * 2, ey = Math.abs(fy - 0.5) * 2;
      double edge = Math.pow(Math.max(ex, ey), 4);
      // diagonal amber-rose coating reflection (upper left to lower right)
      double d = (fx - fy) - 0.18;
      double band = Math.exp(-d * d / 0.03);
      double r = 70 + 120 * band + 20 * edge;
      double g = 112 + 40 * band + 20 * edge;
      double b = 124 - 30 * band + 18 * edge;
      double a = 14 + 26 * band + 46 * edge;
      return new int[]{clamp(r), clamp(g), clamp(b), clamp(a)};
   }

   /** Micro red dot objective: a coated round window (mostly clear). */
   static void dotWindow(PoseStack pose, MultiBufferSource buffers, int light, double z, double r) {
      VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(FieldMaterials.ATLAS));
      Pose p = pose.last();
      int seg = "distant".equals(FieldWeaponMesh.lod) ? 12 : 24;
      int rings = "distant".equals(FieldWeaponMesh.lod) ? 1 : 3;
      for (int i = 0; i < rings; i++) {
         for (int j = 0; j < seg; j++) {
            for (int k = 0; k < 4; k++) {
               int sj = j + (k != 0 && k != 3 ? 1 : 0);
               int ri = i + (k <= 1 ? 0 : 1);
               double rr = (double)ri / rings;
               double a = sj * Math.PI * 2.0 / seg;
               double nx = Math.cos(a) * rr, ny = Math.sin(a) * rr;
               int[] c = windowColour(0.5 + nx * 0.5, 0.5 + ny * 0.5);
               out.addVertex(p.pose(), (float)(nx * r), (float)(ny * r), (float)z)
                  .setColor(c[0], c[1], c[2], c[3])
                  .setUv(U, V)
                  .setOverlay(OverlayTexture.NO_OVERLAY)
                  .setLight(light)
                  .setNormal(p, 0.0F, 0.0F, -1.0F);
            }
         }
      }
   }

   private static int clamp(double v) {
      return (int)Math.max(0, Math.min(255, Math.round(v)));
   }
}
