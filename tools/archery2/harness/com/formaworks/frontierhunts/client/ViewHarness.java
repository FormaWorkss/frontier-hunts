package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.archery.ArrowFlight;
import com.formaworks.frontierhunts.archery.BowBallistics;
import com.formaworks.frontierhunts.archery.SightOptics;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.hunting.ArrowSupply;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * [archery2] Offline first-person view harness: runs the game's own first-person bow code (FieldBowPresentation.place /
 * draw, the bow meshes, the hand placement) into a capturing vertex consumer and rasterises it with Minecraft's hand
 * projection (hand FOV) over a simple world (sky, ground, a 1 m target board at a set distance, drawn with the world
 * FOV), then overlays the HUD aim reference. Writes PNGs to look at, and reports what covers the screen centre.
 *
 * usage: java ... ViewHarness <outDir> [userFov]
 */
public final class ViewHarness {
   static final int W = 1280;
   static final int H = 720;
   static BufferedImage atlas;

   /** One captured vertex. */
   static final class V {
      float x, y, z, u, v, nx, ny, nz;
      int argb = -1;
      boolean tex = true;
      String tag;
   }

   static final class Cap implements VertexConsumer {
      final List<V> verts = new ArrayList<>();
      V cur;
      boolean textured = true;
      int forced = 0;

      public VertexConsumer addVertex(float x, float y, float z) {
         this.cur = new V();
         this.cur.x = x;
         this.cur.y = y;
         this.cur.z = z;
         this.cur.tex = this.textured;
         if (this.forced != 0) {
            this.cur.argb = this.forced;
         }
         this.verts.add(this.cur);
         return this;
      }

      public VertexConsumer setColor(int r, int g, int b, int a) {
         if (this.forced == 0) {
            this.cur.argb = a << 24 | r << 16 | g << 8 | b;
         }
         return this;
      }

      public VertexConsumer setUv(float u, float v) {
         this.cur.u = u;
         this.cur.v = v;
         return this;
      }

      public VertexConsumer setUv1(int u, int v) {
         return this;
      }

      public VertexConsumer setUv2(int u, int v) {
         return this;
      }

      public VertexConsumer setNormal(float x, float y, float z) {
         this.cur.nx = x;
         this.cur.ny = y;
         this.cur.nz = z;
         return this;
      }
   }

   // ------------------------------------------------------------------ raster
   final int[] rgb = new int[W * H];
   float[] depth = new float[W * H];
   final String[] owner = new String[W * H];
   String tag = "";

   void clearDepth() {
      java.util.Arrays.fill(this.depth, Float.POSITIVE_INFINITY);
   }

   /** Perspective: camera-space point -> {sx, sy, depth}. */
   static double[] proj(double x, double y, double z, double fovDeg) {
      double t = Math.tan(Math.toRadians(fovDeg) * 0.5);
      double d = -z;
      double sx = W * 0.5 + x / d / t * H * 0.5;
      double sy = H * 0.5 - y / d / t * H * 0.5;
      return new double[]{sx, sy, d};
   }

   void triangles(List<V> vs, double fov, String tagName) {
      for (int i = 0; i + 3 < vs.size(); i += 4) {
         V a = vs.get(i), b = vs.get(i + 1), c = vs.get(i + 2), d = vs.get(i + 3);
         this.tri(a, b, c, fov, tagName);
         this.tri(a, c, d, fov, tagName);
      }
   }

   void tri(V a, V b, V c, double fov, String tagName) {
      // clip against the near plane z = -0.05
      List<V> poly = new ArrayList<>(List.of(a, b, c));
      List<V> out = new ArrayList<>();
      double near = -0.05;
      for (int i = 0; i < poly.size(); i++) {
         V p = poly.get(i), q = poly.get((i + 1) % poly.size());
         boolean pin = p.z <= near, qin = q.z <= near;
         if (pin) {
            out.add(p);
         }
         if (pin != qin) {
            double t = (near - p.z) / (q.z - p.z);
            V m = new V();
            m.x = (float)(p.x + (q.x - p.x) * t);
            m.y = (float)(p.y + (q.y - p.y) * t);
            m.z = (float)near;
            m.u = (float)(p.u + (q.u - p.u) * t);
            m.v = (float)(p.v + (q.v - p.v) * t);
            m.nx = p.nx;
            m.ny = p.ny;
            m.nz = p.nz;
            m.argb = p.argb;
            m.tex = p.tex;
            out.add(m);
         }
      }
      for (int i = 1; i + 1 < out.size(); i++) {
         this.raster(out.get(0), out.get(i), out.get(i + 1), fov, tagName);
      }
   }

   void raster(V a, V b, V c, double fov, String tagName) {
      double[] pa = proj(a.x, a.y, a.z, fov), pb = proj(b.x, b.y, b.z, fov), pc = proj(c.x, c.y, c.z, fov);
      double area = (pb[0] - pa[0]) * (pc[1] - pa[1]) - (pc[0] - pa[0]) * (pb[1] - pa[1]);
      if (Math.abs(area) < 1.0E-9) {
         return;
      }
      int x0 = (int)Math.max(0, Math.floor(Math.min(pa[0], Math.min(pb[0], pc[0]))));
      int x1 = (int)Math.min(W - 1, Math.ceil(Math.max(pa[0], Math.max(pb[0], pc[0]))));
      int y0 = (int)Math.max(0, Math.floor(Math.min(pa[1], Math.min(pb[1], pc[1]))));
      int y1 = (int)Math.min(H - 1, Math.ceil(Math.max(pa[1], Math.max(pb[1], pc[1]))));
      // flat-ish shading from the vertex normal (camera space), MC-like two lights
      float nx = a.nx + b.nx + c.nx, ny = a.ny + b.ny + c.ny, nz = a.nz + b.nz + c.nz;
      float nl = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
      if (nl > 1.0E-6F) {
         nx /= nl;
         ny /= nl;
         nz /= nl;
      }
      double l1 = Math.max(0.0, nx * 0.2 + ny * 1.0 + nz * 0.7) / Math.sqrt(1.53);
      double l2 = Math.max(0.0, -nx * 0.2 + ny * 1.0 - nz * 0.7) / Math.sqrt(1.53);
      double shade = Math.min(1.0, 0.4 + 0.6 * Math.min(1.0, (l1 + l2) * 0.6) + 0.18 * Math.abs(nz));
      for (int y = y0; y <= y1; y++) {
         for (int x = x0; x <= x1; x++) {
            double px = x + 0.5, py = y + 0.5;
            double w0 = ((pb[0] - px) * (pc[1] - py) - (pc[0] - px) * (pb[1] - py)) / area;
            double w1 = ((pc[0] - px) * (pa[1] - py) - (pa[0] - px) * (pc[1] - py)) / area;
            double w2 = 1.0 - w0 - w1;
            if (w0 < 0 || w1 < 0 || w2 < 0) {
               continue;
            }
            // perspective-correct interpolation
            double ia = w0 / pa[2], ib = w1 / pb[2], ic = w2 / pc[2];
            double iz = ia + ib + ic;
            double d = 1.0 / iz;
            int idx = y * W + x;
            if (d >= this.depth[idx]) {
               continue;
            }
            int col = a.argb;
            int tr = 255, tg = 255, tb = 255;
            if (a.tex && atlas != null) {
               double u = (a.u * ia + b.u * ib + c.u * ic) / iz;
               double v = (a.v * ia + b.v * ib + c.v * ic) / iz;
               int tx = (int)Math.max(0, Math.min(atlas.getWidth() - 1, u * atlas.getWidth()));
               int ty = (int)Math.max(0, Math.min(atlas.getHeight() - 1, v * atlas.getHeight()));
               int t = atlas.getRGB(tx, ty);
               tr = t >> 16 & 255;
               tg = t >> 8 & 255;
               tb = t & 255;
            }
            int r = (int)Math.min(255, (col >> 16 & 255) * tr / 255 * shade);
            int g = (int)Math.min(255, (col >> 8 & 255) * tg / 255 * shade);
            int bl = (int)Math.min(255, (col & 255) * tb / 255 * shade);
            this.depth[idx] = (float)d;
            this.rgb[idx] = r << 16 | g << 8 | bl;
            this.owner[idx] = tagName;
         }
      }
   }

   // ------------------------------------------------------------------ scene
   /** Background: sky, ground plane at eye height 1.62, target board (1x1 m, red 20 cm centre) at {@code dist}. */
   void world(double worldFov, double pitchDeg, double dist) {
      double t = Math.tan(Math.toRadians(worldFov) * 0.5);
      double cp = Math.cos(Math.toRadians(pitchDeg)), sp = Math.sin(Math.toRadians(pitchDeg));
      for (int y = 0; y < H; y++) {
         for (int x = 0; x < W; x++) {
            // ray in camera space
            double rx = (x + 0.5 - W * 0.5) / (H * 0.5) * t;
            double ry = -(y + 0.5 - H * 0.5) / (H * 0.5) * t;
            double rz = -1.0;
            // to world: pitch about x (positive = look up)
            double wy = ry * cp - rz * sp;
            double wz = ry * sp + rz * cp;
            double wx = rx;
            int col;
            if (wy < 0) {
               double s = -1.62 / wy;
               double gx = wx * s, gz = wz * s;
               boolean chk = ((int)Math.floor(gx) + (int)Math.floor(gz) & 1) == 0;
               col = chk ? 0x5E8A3A : 0x557F35;
            } else {
               double k = Math.min(1.0, wy * 3);
               col = (int)(150 + 40 * k) << 16 | (int)(190 + 30 * k) << 8 | 235;
            }
            // board in the plane z = -dist
            if (wz < 0) {
               double s = -dist / wz;
               double bx = wx * s, by = wy * s;
               if (Math.abs(bx) < 0.5 && Math.abs(by) < 0.5) {
                  col = Math.hypot(bx, by) < 0.1 ? 0xC0302A : (Math.hypot(bx, by) < 0.3 && Math.hypot(bx, by) > 0.28 ? 0x222222 : 0xEDE6D6);
               }
            }
            this.rgb[y * W + x] = col;
            this.owner[y * W + x] = "world";
         }
      }
   }

   void dot(double x, double y, int r, int col) {
      for (int j = -r; j <= r; j++) {
         for (int i = -r; i <= r; i++) {
            int px = (int)Math.round(x) + i, py = (int)Math.round(y) + j;
            if (px >= 0 && py >= 0 && px < W && py < H && i * i + j * j <= r * r) {
               this.rgb[py * W + px] = col;
            }
         }
      }
   }

   void save(File f) throws Exception {
      BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
      img.setRGB(0, 0, W, H, this.rgb, 0, W);
      // centre cross, 1 px, for reference
      for (int i = -6; i <= 6; i++) {
         if (Math.abs(i) > 2) {
            img.setRGB(W / 2 + i, H / 2, 0xFF00FF);
            img.setRGB(W / 2, H / 2 + i, 0xFF00FF);
         }
      }
      ImageIO.write(img, "png", f);
   }

   // ------------------------------------------------------------------ the game's bow code
   static void setFov(double world, double hand) throws Exception {
      var f1 = BowSight.class.getDeclaredField("worldFov");
      var f2 = BowSight.class.getDeclaredField("handFov");
      f1.setAccessible(true);
      f2.setAccessible(true);
      f1.setDouble(null, world);
      f2.setDouble(null, hand);
   }

   static Cap capture(Weapon w, boolean field, float draw, double pitch) {
      Cap cap = new Cap();
      MultiBufferSource buffers = rt -> cap;
      FieldPlayerArms.testSink = (pose, right, slim) -> arm(cap, pose, right);
      float aim = FieldBowAim.aim(draw);
      double[] anchor = FieldBowPresentation.anchor(w, field, draw, pitch);
      PoseStack pose = new PoseStack();
      FieldBowPresentation.place(pose, anchor, aim, 0.0F, 0.0F, 0.0F);
      FieldBowPresentation.draw(pose, buffers, cap, 15728880, w, field, draw, aim, pitch, anchor, ArrowSupply.Shot.DEFAULT, true, 0.0);
      return cap;
   }

   /** Vanilla player arm (4x12x4 px, sleeve on the upper part) in the frame FieldPlayerArms sets up. */
   static void arm(Cap cap, PoseStack pose, boolean right) {
      // PlayerModel arm (pivot offset +-5,2 px; cube 4x12x4 px), in the frame FieldPlayerArms hands to renderXHand:
      // right x -0.5..-0.25, left x 0.25..0.5; y 0 (shoulder) .. 0.75 (hand); z -0.125..0.125
      float x0 = right ? -0.5F : 0.25F, x1 = right ? -0.25F : 0.5F, y0 = 0.0F, y1 = 0.75F, z0 = -0.125F, z1 = 0.125F;
      Matrix4f m = pose.last().pose();
      if (System.getProperty("arms") != null) {
         Vector3f w = m.transformPosition(new Vector3f(right ? -0.375F : 0.375F, 0.72F, 0));
         Vector3f sh = m.transformPosition(new Vector3f(right ? -0.375F : 0.375F, 0.0F, 0));
         System.out.printf("  %s wrist %.3f %.3f %.3f  shoulder %.3f %.3f %.3f%n", right ? "R" : "L", w.x, w.y, w.z, sh.x, sh.y, sh.z);
      }
      float split = 0.3F; // sleeve toward the shoulder, skin toward the hand
      for (int part = 0; part < 2; part++) {
         float ya = part == 0 ? y0 : split, yb = part == 0 ? split : y1;
         int col = part == 0 ? 0xFF2F7F86 : 0xFFC08B67;
         float[][] c = {
            {x0, ya, z0}, {x1, ya, z0}, {x1, yb, z0}, {x0, yb, z0}, {x0, ya, z1}, {x1, ya, z1}, {x1, yb, z1}, {x0, yb, z1}
         };
         int[][] faces = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {3, 2, 6, 7}, {4, 5, 1, 0}};
         float[][] n = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
         cap.textured = false;
         cap.forced = col;
         for (int f = 0; f < 6; f++) {
            Vector3f nn = pose.last().normal().transform(new Vector3f(n[f][0], n[f][1], n[f][2]));
            for (int k : faces[f]) {
               Vector3f p = m.transformPosition(new Vector3f(c[k][0], c[k][1], c[k][2]));
               cap.addVertex(p.x, p.y, p.z).setNormal(nn.x, nn.y, nn.z);
               cap.cur.tag = right ? "right hand" : "left hand";
            }
         }
         cap.textured = true;
         cap.forced = 0;
      }
   }

   public static void main(String[] args) throws Exception {
      File out = new File(args.length > 0 ? args[0] : "/tmp/claude-0/archery2-views");
      out.mkdirs();
      double userFov = args.length > 1 ? Double.parseDouble(args[1]) : 70.0;
      try (var in = new java.util.zip.ZipFile("/home/claude/fh/merged62g8.jar")) {
         atlas = ImageIO.read(in.getInputStream(in.getEntry("assets/frontierhunts/textures/material/field_materials.png")));
      }
      record Case(String name, Weapon w, boolean field, float draw, double dist) {
      }
      List<Case> cases = List.of(
         new Case("crossbow_rest", Weapon.CROSSBOW, false, 0.0F, 30.0),
         new Case("crossbow_half", Weapon.CROSSBOW, false, 0.5F, 30.0),
         new Case("crossbow_aim", Weapon.CROSSBOW, false, 1.0F, 30.0),
         new Case("recurve_aim", Weapon.RECURVE_BOW, false, 1.0F, 30.0),
         new Case("recurve_half", Weapon.RECURVE_BOW, false, 0.5F, 30.0),
         new Case("recurve_rest", Weapon.RECURVE_BOW, false, 0.0F, 30.0),
         new Case("fieldbow_rest", Weapon.RECURVE_BOW, true, 0.0F, 30.0),
         new Case("compound_rest", Weapon.COMPOUND_BOW, false, 0.0F, 30.0),
         new Case("fieldbow_aim", Weapon.RECURVE_BOW, true, 1.0F, 20.0),
         new Case("fieldbow_half", Weapon.RECURVE_BOW, true, 0.5F, 20.0),
         new Case("compound_aim", Weapon.COMPOUND_BOW, false, 1.0F, 30.0)
      );
      for (Case c : cases) {
         float aim = FieldBowAim.aim(c.draw);
         double zoom = 1.0 + 0.17 * aim;
         double worldFov = userFov / zoom;
         double handFov = 70.0 / zoom;
         setFov(worldFov, handFov);
         ViewHarness h = new ViewHarness();
         h.world(worldFov, 0.0, c.dist);
         h.clearDepth();
         Cap cap = capture(c.w, c.field, c.draw, 0.0);
         h.handTriangles(cap.verts, handFov);
         // HUD reference
         StringBuilder note = new StringBuilder();
         if (c.draw >= 1.0F) {
            BowBallistics.Profile p = c.field ? BowBallistics.FIELD_RECURVE : BowBallistics.forId(c.w == Weapon.RECURVE_BOW ? "recurve_bow" : c.w == Weapon.CROSSBOW ? "crossbow" : "compound_bow");
            if (c.w == Weapon.RECURVE_BOW) {
               // impact dot on the board: fly the arrow from the eye along the view (yaw 180 = north = -z)
               double eyeY = 1.62;
               double[] v = ArrowFlight.launch(0.0F, 180.0F, ArrowFlight.speed(p, 1.0F));
               final double d = c.dist;
               ArrowFlight.Impact imp = ArrowFlight.fly(p, 0.0, eyeY - p.originDrop(), 0.0, v, 0.0, 0.0,
                  (ax, ay, az, bx, by, bz) -> (az > -d && bz <= -d) ? (-d - az) / (bz - az) : Double.NaN, 200);
               double[] s = proj(imp.x(), imp.y() - eyeY, imp.z(), worldFov);
               h.dot(s[0], s[1], 3, 0x000000);
               h.dot(s[0], s[1], 2, 0xFFE9A8);
               note.append(String.format(" impact dot %.1f px below centre (%.3f m low at %.0f m)", s[1] - H * 0.5, eyeY - imp.y(), c.dist));
            } else if (c.w == Weapon.CROSSBOW) {
               double[] a = BowSight.angles(p, 0.0);
               int[] cols = {0x62D46E, 0xE4E04A, 0xE0623C};
               for (int i = 0; i < a.length; i++) {
                  double y = H * 0.5 + SightOptics.screenDrop(a[i], worldFov, H);
                  h.dot(W * 0.5, y, 2, cols[i % 3]);
               }
            }
         }
         h.save(new File(out, c.name + (userFov != 70.0 ? "_fov" + (int)userFov : "") + ".png"));
         System.out.println(c.name + ": centre covered by " + h.coverage() + note);
      }
      closeUp(out, "zoom_fieldbow_shelf", Weapon.RECURVE_BOW, true, 1.0F, FieldPrimitiveBow.ARROW_X, FieldPrimitiveBow.ARROW_Y, -0.01, 9.0);
      closeUp(out, "zoom_fieldbow_shelf_half", Weapon.RECURVE_BOW, true, 0.35F, FieldPrimitiveBow.ARROW_X, FieldPrimitiveBow.ARROW_Y, -0.01, 16.0);
      closeUp(out, "zoom_recurve_shelf", Weapon.RECURVE_BOW, false, 1.0F, 0.015, 0.05, -0.02, 9.0);
      closeUp(out, "zoom_crossbow_sight", Weapon.CROSSBOW, false, 1.0F, 0.0, FieldBowPresentation.XB_SIGHT_Y, FieldBowPresentation.XB_SIGHT_Z, 14.0);
   }

   /** Close-up: turn the captured hand scene so model point (mx, my, mz) of the (mirrored) bow is centred. */
   static void closeUp(File out, String name, Weapon w, boolean field, float draw, double mx, double my, double mz, double fov) throws Exception {
      setFov(70.0 / 1.17, 70.0 / 1.17);
      Cap cap = capture(w, field, draw, 0.0);
      float aim = FieldBowAim.aim(draw);
      double[] anchor = FieldBowPresentation.anchor(w, field, draw, 0.0);
      PoseStack pose = new PoseStack();
      FieldBowPresentation.place(pose, anchor, aim, 0.0F, 0.0F, 0.0F);
      if (FieldBowPresentation.mirrored(w)) {
         pose.scale(-1.0F, 1.0F, 1.0F);
      }
      Vector3f p = pose.last().pose().transformPosition(new Vector3f((float)mx, (float)my, (float)mz));
      org.joml.Quaternionf q = new org.joml.Quaternionf().rotationTo(new Vector3f(p).normalize(), new Vector3f(0, 0, -1));
      for (V v : cap.verts) {
         Vector3f a = q.transform(new Vector3f(v.x, v.y, v.z));
         Vector3f n = q.transform(new Vector3f(v.nx, v.ny, v.nz));
         v.x = a.x; v.y = a.y; v.z = a.z; v.nx = n.x; v.ny = n.y; v.nz = n.z;
      }
      ViewHarness h = new ViewHarness();
      java.util.Arrays.fill(h.rgb, 0x8FB3D9);
      h.clearDepth();
      h.handTriangles(cap.verts, fov);
      h.save(new File(out, name + ".png"));
   }

   void handTriangles(List<V> vs, double handFov) {
      for (int i = 0; i + 3 < vs.size(); i += 4) {
         V a = vs.get(i);
         String t = a.tag != null ? a.tag : "bow";
         this.tri(a, vs.get(i + 1), vs.get(i + 2), handFov, t);
         this.tri(a, vs.get(i + 2), vs.get(i + 3), handFov, t);
      }
   }

   /** What occupies the aiming centre: a circle of ~1 degree (10 px at 720p) round the view centre. */
   String coverage() {
      java.util.Map<String, Integer> m = new java.util.TreeMap<>();
      int r = 10;
      for (int y = H / 2 - r; y <= H / 2 + r; y++) {
         for (int x = W / 2 - r; x <= W / 2 + r; x++) {
            if ((x - W / 2) * (x - W / 2) + (y - H / 2) * (y - H / 2) > r * r) {
               continue;
            }
            String o = this.owner[y * W + x];
            if (o != null && !o.equals("world")) {
               m.merge(o, 1, Integer::sum);
            }
         }
      }
      return m.isEmpty() ? "nothing (clear)" : m.toString();
   }
}
