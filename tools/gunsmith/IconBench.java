package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * [1.1.2] Hotbar icon bench: renders each weapon the way the GUI draws it (its model's gui transform, the slot matrix,
 * Minecraft's 3D-item lights) at GUI scale 3, once with the vanilla entity shading and once with the icon shader of
 * WeaponIcons (rendertype_weapon_icon: brighter light, lifted darks, a soft sheen at grazing angles), and lays both
 * rows into a real hotbar over a daylight background.  usage: IconBench <out dir> <item models dir> <asset roots...>
 */
public final class IconBench {
   static final int S = 3, SS = 4; // GUI scale, supersampling

   public static void main(String[] a) throws Exception {
      File out = new File(a[0]);
      File models = new File(a[1]);
      for (int i = 2; i < a.length; i++) {
         GunBench.ROOTS.add(new File(a[i]));
      }
      GunBench.boot(HuntConfig.Quality.CINEMATIC);
      out.mkdirs();
      List<String> ids = new ArrayList<>();
      List<List<GunBench.Poly>> meshes = new ArrayList<>();
      List<float[]> axes = new ArrayList<>(); // long axis, up axis (model space)
      float[] gunAxes = {0, 0, -1, 0, 1, 0}, bowAxes = {0, 1, 0, 0, 0, -1};
      for (Weapon w : new Weapon[]{Weapon.LEVER_RIFLE, Weapon.SEMI_AUTO_RIFLE, Weapon.PUMP_SHOTGUN, Weapon.DOUBLE_BARREL, Weapon.SEMI_AUTO_SHOTGUN,
         Weapon.REVOLVER, Weapon.FIELD_PISTOL, Weapon.TRANQUILIZER_RIFLE, Weapon.FLARE_GUN, Weapon.BAIT_LAUNCHER}) {
         ids.add(w.id());
         meshes.add(GunBench.capField(new GunBench.Rig(w, Set.of(), ""), "close", 0));
         axes.add(gunAxes);
      }
      for (Weapon w : new Weapon[]{Weapon.RECURVE_BOW, Weapon.COMPOUND_BOW, Weapon.CROSSBOW, Weapon.HUNTING_SPEAR}) {
         GunBench.Capture cap = new GunBench.Capture();
         FieldEquipmentModel.bow(w, new PoseStack(), cap.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(FieldMaterials.ATLAS)), GunBench.LIGHT, 0.0F);
         cap.finish();
         ids.add(w.id());
         meshes.add(cap.polys);
         axes.add(w == Weapon.CROSSBOW ? gunAxes : bowAxes); // the spear stands along y like the bows
      }
      ids.add("ridgeline_rifle");
      meshes.add(GunBench.capRidge(com.formaworks.frontierhunts.rifle.RidgelineOptics.STOCK, false));
      axes.add(gunAxes);
      int n = ids.size();
      BufferedImage[][] icons = new BufferedImage[2][n];
      File jsonOut = new File(out, "gui");
      jsonOut.mkdirs();
      for (int i = 0; i < n; i++) {
         JsonObject gui = null;
         File mf = new File(models, ids.get(i) + ".json");
         if (mf.exists()) {
            JsonObject m = JsonParser.parseString(Files.readString(mf.toPath())).getAsJsonObject();
            if (m.has("display") && m.getAsJsonObject("display").has("gui")) {
               gui = m.getAsJsonObject("display").getAsJsonObject("gui");
            }
         }
         icons[0][i] = icon(meshes.get(i), gui, false);
         JsonObject fit = fit(meshes.get(i), axes.get(i), ids.get(i));
         Files.writeString(new File(jsonOut, ids.get(i) + ".json").toPath(), fit.toString());
         icons[1][i] = icon(meshes.get(i), fit, true);
         ImageIO.write(scale(icons[1][i], 4), "png", new File(out, "icon_" + ids.get(i) + ".png"));
         System.out.println("icon " + ids.get(i) + " " + fit);
      }
      // two hotbars (vanilla shading / icon shader), 9 slots each, over a sky-and-snow background, then a 3x zoom strip
      BufferedImage bar = ImageIO.read(new File(System.getProperty("bench.hotbar")));
      int perRow = 9, rows = (n + perRow - 1) / perRow;
      int bw = 182 * S, bh = 22 * S, pad = 20;
      BufferedImage sheet = new BufferedImage(bw + 2 * pad, 2 * rows * (bh + pad) + pad + 40, BufferedImage.TYPE_INT_RGB);
      for (int y = 0; y < sheet.getHeight(); y++) {
         for (int x = 0; x < sheet.getWidth(); x++) {
            double t = (double)y / sheet.getHeight();
            int r = (int)(140 + 60 * t + 20 * Math.sin(x * 0.05)), g = (int)(170 + 45 * t), b = (int)(200 + 30 * t);
            sheet.setRGB(x, y, (clip(r) << 16) | (clip(g) << 8) | clip(b));
         }
      }
      java.awt.Graphics2D g2 = sheet.createGraphics();
      int y = pad;
      for (int mode = 0; mode < 2; mode++) {
         g2.setColor(java.awt.Color.BLACK);
         g2.drawString(mode == 0 ? "before (1.1.1 pose and light, no outline)" : "1.1.2: diagonal pose + icon shader", pad, y + 12);
         y += 18;
         for (int r = 0; r < rows; r++) {
            g2.drawImage(bar.getScaledInstance(bw, bh, java.awt.Image.SCALE_REPLICATE), pad, y, null);
            for (int c = 0; c < perRow && r * perRow + c < n; c++) {
               g2.drawImage(icons[mode][r * perRow + c], pad + (3 + 20 * c) * S, y + 3 * S, null);
            }
            y += bh + pad;
         }
      }
      g2.dispose();
      ImageIO.write(sheet, "png", new File(out, "hotbar_icons.png"));
   }

   static final float DIAG = (float)Math.toRadians(Double.parseDouble(System.getProperty("bench.diag", "38")));
   static final float TILT = (float)Math.toRadians(Double.parseDouble(System.getProperty("bench.tilt", "16")));
   static final float FILL = Float.parseFloat(System.getProperty("bench.fill", "15.2"));

   /**
    * the slot pose: the weapon's long axis along the slot diagonal (muzzle / top limb to the upper right), its top
    * toward the upper left, seen from the side and a little from above, as large as fits the slot
    */
   static JsonObject fit(List<GunBench.Poly> polys, float[] ax, String id) {
      Vector3f a = new Vector3f(ax[0], ax[1], ax[2]).normalize(), b = new Vector3f(ax[3], ax[4], ax[5]).normalize();
      Vector3f c = new Vector3f(a).cross(b).normalize();
      Vector3f d1 = new Vector3f((float)Math.cos(DIAG), (float)Math.sin(DIAG), 0), d2 = new Vector3f(-(float)Math.sin(DIAG), (float)Math.cos(DIAG), 0);
      Vector3f d3 = new Vector3f(d1).cross(d2);
      Matrix3f model = new Matrix3f(a, b, c), screen = new Matrix3f(d1, d2, d3);
      Matrix3f r = new Matrix3f(screen).mul(new Matrix3f(model).transpose());
      // tilt the top toward the viewer, about the long axis
      r = new Matrix3f().rotation(-TILT, d1).mul(r);
      float[] lo = {1e9F, 1e9F}, hi = {-1e9F, -1e9F};
      for (GunBench.Poly p : polys) {
         for (float[] v : p.v) {
            Vector3f q = r.transform(new Vector3f(v[0], v[1], v[2]));
            lo[0] = Math.min(lo[0], q.x); hi[0] = Math.max(hi[0], q.x);
            lo[1] = Math.min(lo[1], q.y); hi[1] = Math.max(hi[1], q.y);
         }
      }
      float ext = Math.max(hi[0] - lo[0], hi[1] - lo[1]);
      float sc = Math.min(4.0F, FILL / 16.0F / ext);
      float cx = (lo[0] + hi[0]) / 2 * sc, cy = (lo[1] + hi[1]) / 2 * sc;
      Quaternionf q = new Quaternionf();
      r.getNormalizedRotation(q);
      Vector3f e = q.getEulerAnglesXYZ(new Vector3f());
      JsonObject o = new JsonObject();
      JsonArray rot = new JsonArray(), tr = new JsonArray(), s = new JsonArray();
      rot.add(round(Math.toDegrees(e.x))); rot.add(round(Math.toDegrees(e.y))); rot.add(round(Math.toDegrees(e.z)));
      // the item renderer translates in slot units of 1/16 block before rotating: centre the weapon in the slot
      tr.add(round(-cx * 16)); tr.add(round(-cy * 16)); tr.add(0);
      s.add(round(sc)); s.add(round(sc)); s.add(round(sc));
      o.add("rotation", rot);
      o.add("translation", tr);
      o.add("scale", s);
      return o;
   }

   static double round(double v) {
      return Math.round(v * 1000.0) / 1000.0;
   }

   static BufferedImage scale(BufferedImage im, int k) {
      BufferedImage o = new BufferedImage(im.getWidth() * k, im.getHeight() * k, BufferedImage.TYPE_INT_ARGB);
      java.awt.Graphics2D g = o.createGraphics();
      g.setColor(new java.awt.Color(48, 48, 48));
      g.fillRect(0, 0, o.getWidth(), o.getHeight());
      g.drawImage(im.getScaledInstance(o.getWidth(), o.getHeight(), java.awt.Image.SCALE_REPLICATE), 0, 0, null);
      g.dispose();
      return o;
   }

   static int clip(int v) {
      return Math.max(0, Math.min(255, v));
   }

   /** one slot icon (16 GUI px = 48 screen px), transparent background */
   static BufferedImage icon(List<GunBench.Poly> polys, JsonObject gui, boolean lift) {
      Matrix4f m = new Matrix4f().translate(8, 8, 150).scale(16, -16, 16);
      if (gui != null) {
         float[] tr = vec(gui, "translation", 0), ro = vec(gui, "rotation", 0), sc = vec(gui, "scale", 1);
         m.translate(clampf(tr[0] / 16f, 5), clampf(tr[1] / 16f, 5), clampf(tr[2] / 16f, 5));
         m.rotate(new Quaternionf().rotationXYZ((float)Math.toRadians(ro[0]), (float)Math.toRadians(ro[1]), (float)Math.toRadians(ro[2])));
         m.scale(sc[0], sc[1], sc[2]);
      }
      m.translate(-0.5F, -0.5F, -0.5F).translate(0.5F, 0.5F, 0.5F);
      Matrix3f nm = new Matrix3f(m).invert().transpose();
      Matrix4f lm = new Matrix4f().scaling(1.0F, -1.0F, 1.0F).rotateY(-0.3926991F).rotateX(2.3561945F);
      Vector3f l0 = lm.transformDirection(new Vector3f(0.2F, 1.0F, -0.7F).normalize()).normalize();
      Vector3f l1 = lm.transformDirection(new Vector3f(-0.2F, 1.0F, 0.7F).normalize()).normalize();
      int W = 16 * S * SS;
      float[] zb = new float[W * W], cr = new float[W * W], cg = new float[W * W], cb = new float[W * W], ca = new float[W * W];
      java.util.Arrays.fill(zb, -1e9F);
      for (GunBench.Poly p : polys) {
         int k = p.v.length;
         double[] sx = new double[k], sy = new double[k], sz = new double[k], li = new double[k], rim = new double[k];
         for (int i = 0; i < k; i++) {
            float[] v = p.v[i];
            Vector3f q = m.transformPosition(new Vector3f(v[0], v[1], v[2]));
            sx[i] = q.x * S * SS;
            sy[i] = q.y * S * SS;
            sz[i] = q.z;
            Vector3f nn = nm.transform(new Vector3f(v[9], v[10], v[11]));
            if (nn.lengthSquared() > 1e-9) nn.normalize();
            double d0 = Math.max(0, nn.dot(l0)), d1 = Math.max(0, nn.dot(l1));
            li[i] = lift ? Math.min(1.0, (d0 + d1) * 0.5 + 0.62) : Math.min(1.0, (d0 + d1) * 0.6 + 0.4);
            rim[i] = Math.pow(1.0 - Math.min(1.0, Math.abs(nn.z)), 3.0);
         }
         BufferedImage tex = GunBench.texture(p.tex);
         for (int t = 1; t + 1 < k; t++) {
            tri(p, tex, sx, sy, sz, li, rim, 0, t, t + 1, W, zb, cr, cg, cb, ca, lift);
         }
      }
      BufferedImage img = new BufferedImage(16 * S, 16 * S, BufferedImage.TYPE_INT_ARGB);
      for (int y = 0; y < 16 * S; y++) {
         for (int x = 0; x < 16 * S; x++) {
            double r = 0, g = 0, b = 0, al = 0;
            for (int j = 0; j < SS; j++) {
               for (int i = 0; i < SS; i++) {
                  int idx = (y * SS + j) * W + x * SS + i;
                  r += cr[idx] * ca[idx];
                  g += cg[idx] * ca[idx];
                  b += cb[idx] * ca[idx];
                  al += ca[idx];
               }
            }
            if (al <= 0) {
               continue;
            }
            int A = clip((int)Math.round(al / (SS * SS) * 255));
            img.setRGB(x, y, (A << 24) | (clip((int)Math.round(r / al * 255)) << 16) | (clip((int)Math.round(g / al * 255)) << 8) | clip((int)Math.round(b / al * 255)));
         }
      }
      return img;
   }

   static void tri(GunBench.Poly poly, BufferedImage tex, double[] sx, double[] sy, double[] sz, double[] li, double[] rim, int a, int b, int c, int W,
      float[] zb, float[] cr, float[] cg, float[] cb, float[] ca, boolean lift) {
      int x0 = Math.max(0, (int)Math.floor(Math.min(sx[a], Math.min(sx[b], sx[c])))), x1 = Math.min(W - 1, (int)Math.ceil(Math.max(sx[a], Math.max(sx[b], sx[c]))));
      int y0 = Math.max(0, (int)Math.floor(Math.min(sy[a], Math.min(sy[b], sy[c])))), y1 = Math.min(W - 1, (int)Math.ceil(Math.max(sy[a], Math.max(sy[b], sy[c]))));
      double den = (sy[b] - sy[c]) * (sx[a] - sx[c]) + (sx[c] - sx[b]) * (sy[a] - sy[c]);
      if (Math.abs(den) < 1e-12 || x0 > x1 || y0 > y1) {
         return;
      }
      float[] va = poly.v[a], vb = poly.v[b], vc = poly.v[c];
      int tw = tex == null ? 0 : tex.getWidth(), th = tex == null ? 0 : tex.getHeight();
      for (int y = y0; y <= y1; y++) {
         for (int x = x0; x <= x1; x++) {
            double px = x + 0.5, py = y + 0.5;
            double l1 = ((sy[b] - sy[c]) * (px - sx[c]) + (sx[c] - sx[b]) * (py - sy[c])) / den;
            double l2 = ((sy[c] - sy[a]) * (px - sx[c]) + (sx[a] - sx[c]) * (py - sy[c])) / den;
            double l3 = 1 - l1 - l2;
            if (l1 < -1e-9 || l2 < -1e-9 || l3 < -1e-9) {
               continue;
            }
            double z = l1 * sz[a] + l2 * sz[b] + l3 * sz[c];
            int i = y * W + x;
            if (z <= zb[i]) {
               continue; // GUI depth: larger z is nearer
            }
            double u = l1 * va[7] + l2 * vb[7] + l3 * vc[7], v = l1 * va[8] + l2 * vb[8] + l3 * vc[8];
            double tr = 1, tg = 1, tb = 1, ta = 1;
            if (tex != null) {
               int argb = GunBench.bilinear(tex, u * tw - 0.5, v * th - 0.5);
               ta = ((argb >>> 24) & 255) / 255.0;
               tr = ((argb >> 16) & 255) / 255.0;
               tg = ((argb >> 8) & 255) / 255.0;
               tb = (argb & 255) / 255.0;
            }
            double al = ta * (l1 * va[6] + l2 * vb[6] + l3 * vc[6]);
            if (al < 0.1) {
               continue;
            }
            double lit = l1 * li[a] + l2 * li[b] + l3 * li[c];
            double r = tr * (l1 * va[3] + l2 * vb[3] + l3 * vc[3]) * lit;
            double g = tg * (l1 * va[4] + l2 * vb[4] + l3 * vc[4]) * lit;
            double bb = tb * (l1 * va[5] + l2 * vb[5] + l3 * vc[5]) * lit;
            if (lift) {
               double rm = (l1 * rim[a] + l2 * rim[b] + l3 * rim[c]) * WeaponIcons.SHEEN;
               r = Math.min(1, Math.pow(Math.max(0, r), WeaponIcons.GAMMA) + rm);
               g = Math.min(1, Math.pow(Math.max(0, g), WeaponIcons.GAMMA) + rm);
               bb = Math.min(1, Math.pow(Math.max(0, bb), WeaponIcons.GAMMA) + rm);
            }
            zb[i] = (float)z;
            cr[i] = (float)r;
            cg[i] = (float)g;
            cb[i] = (float)bb;
            ca[i] = 1.0F;
         }
      }
   }

   static float clampf(float v, float lim) {
      return Math.max(-lim, Math.min(lim, v));
   }

   static float[] vec(JsonObject o, String k, float def) {
      if (!o.has(k)) {
         return new float[]{def, def, def};
      }
      JsonArray a = o.getAsJsonArray(k);
      return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
   }
}
