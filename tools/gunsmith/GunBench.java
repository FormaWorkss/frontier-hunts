package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.expedition.AttachmentSpec;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.expedition.WeaponAction;
import com.formaworks.frontierhunts.rifle.RidgelineOptics;
import com.formaworks.frontierhunts.rifle.RifleState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * [gunsmith] Offline weapon bench: draws the field guns, the Ridgeline and every attachment / sight through the REAL
 * client draw code (FieldWeaponMesh parts, FieldMountedOptic, FieldTacticalSight, FieldAttachmentHardware,
 * CombatPistolMesh, RidgelineSights, CamoRifleModel) into capturing vertex buffers, then
 * <ul>
 *   <li>{@code render}: rasterises them in software with Minecraft's entity lighting (two directional lights, 0.4
 *       ambient, cutout alpha 0.1, translucent glass blended last) into contact sheets;</li>
 *   <li>{@code ads}: measures every weapon + sight combination's real sight axis (glass / window / iron centres from the
 *       drawn geometry) against the socket numbers the first-person aim pose uses;</li>
 *   <li>{@code tris}: triangle counts per weapon / attachment / LOD.</li>
 * </ul>
 * Asset roots (directories mirroring the jar, later roots win) are given on the command line, so the same build can
 * show "before" (base jar assets) and "after" (base + patch/).
 */
public final class GunBench {
   static final List<File> ROOTS = new ArrayList<>();
   static final Map<String, BufferedImage> TEXTURES = new HashMap<>();
   static final int LIGHT = 0xF000F0;

   // ------------------------------------------------------------------------------------------------ bootstrap
   static File find(String path) {
      for (int i = ROOTS.size() - 1; i >= 0; i--) {
         File f = new File(ROOTS.get(i), path);
         if (f.isFile()) {
            return f;
         }
      }
      return null;
   }

   static void setConfig(Object value, Object v) throws Exception {
      Field f = net.neoforged.neoforge.common.ModConfigSpec.ConfigValue.class.getDeclaredField("cachedValue");
      f.setAccessible(true);
      f.set(value, v);
   }

   static void boot(HuntConfig.Quality q) throws Exception {
      net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
      net.neoforged.fml.ModList ml = net.neoforged.fml.ModList.of(List.of(), List.of());
      for (String fn : new String[]{"indexedMods", "fileById"}) {
         try {
            Field f = net.neoforged.fml.ModList.class.getDeclaredField(fn);
            f.setAccessible(true);
            if (f.get(ml) == null) {
               f.set(ml, new HashMap<>());
            }
         } catch (NoSuchFieldException ignored) {
         }
      }
      net.minecraft.SharedConstants.tryDetectVersion();
      net.minecraft.server.Bootstrap.bootStrap();
      setConfig(HuntConfig.QUALITY, q);
      // Ultra look by default; -Dbench.look=MINECRAFT shows the Vanilla preset's MC-styled atlas
      setConfig(HuntConfig.WORLD_LOOK, HuntConfig.WorldLook.valueOf(System.getProperty("bench.look", "REALISTIC")));
      try {
         setConfig(HuntConfig.REDUCED_MOTION, Boolean.FALSE);
      } catch (Throwable ignored) {
      }
      // FieldWeaponMesh resolves "models/equipment/<name>.fheq" through the resource manager: pre-fill its cache.
      Field cacheF = FieldWeaponMesh.class.getDeclaredField("CACHE");
      cacheF.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, Object> cache = (Map<String, Object>)cacheF.get(null);
      cache.clear();
      Class<?> partC = Class.forName("com.formaworks.frontierhunts.client.FieldWeaponMesh$Part");
      Constructor<?> ctor = partC.getDeclaredConstructors()[0];
      ctor.setAccessible(true);
      Set<String> names = new TreeSet<>();
      for (File r : ROOTS) {
         File[] fs = new File(r, "assets/frontierhunts/models/equipment").listFiles();
         if (fs != null) {
            for (File f : fs) {
               if (f.getName().endsWith(".fheq")) {
                  names.add(f.getName().substring(0, f.getName().length() - 5));
               }
            }
         }
      }
      for (String n : names) {
         cache.put(n, readFheq(find("assets/frontierhunts/models/equipment/" + n + ".fheq"), ctor));
      }
   }

   /** Same parser and budgets as FieldWeaponMesh.load. */
   static List<Object> readFheq(File f, Constructor<?> ctor) throws Exception {
      try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(f.toPath())))) {
         if (in.readInt() != 1179141457 || in.readInt() != 1) {
            throw new IOException("Equipment header " + f);
         }
         int parts = in.readInt();
         if (parts < 1 || parts > 16) {
            throw new IOException("Equipment part budget " + f);
         }
         List<Object> out = new ArrayList<>();
         for (int p = 0; p < parts; p++) {
            int id = in.readInt();
            int nv = in.readInt();
            int nt = in.readInt();
            if (id < 0 || id > 15 || nv < 1 || nv > 100000 || nt < 1 || nt > 40000) {
               throw new IOException("Equipment mesh budget " + f + " part " + p + " nv=" + nv + " nt=" + nt);
            }
            float[] v = new float[nv * 8];
            int[] c = new int[nv];
            int[] idx = new int[nt * 3];
            for (int i = 0; i < nv; i++) {
               for (int k = 0; k < 8; k++) {
                  v[i * 8 + k] = in.readFloat();
                  if (!Float.isFinite(v[i * 8 + k])) {
                     throw new IOException("Equipment vertex " + f);
                  }
               }
               c[i] = in.readInt();
            }
            for (int i = 0; i < idx.length; i++) {
               idx[i] = in.readInt();
               if (idx[i] < 0 || idx[i] >= nv) {
                  throw new IOException("Equipment index " + f);
               }
            }
            out.add(ctor.newInstance(id, v, c, idx, new float[nv * 6]));
         }
         if (in.read() != -1) {
            throw new IOException("Equipment trailing data " + f);
         }
         return List.copyOf(out);
      }
   }

   static BufferedImage texture(String key) {
      return TEXTURES.computeIfAbsent(key, k -> {
         if (k.isEmpty()) {
            return null;
         }
         String[] ns = k.split(":", 2);
         File f = find("assets/" + ns[0] + "/" + ns[1]);
         if (f == null) {
            System.err.println("missing texture " + k);
            return null;
         }
         try {
            return ImageIO.read(f);
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      });
   }

   // ------------------------------------------------------------------------------------------------ capture
   static final Pattern TEX = Pattern.compile("([a-z0-9_.-]+:[a-z0-9_/.-]+\\.png)");

   /** One captured polygon (3 or 4 vertices): x y z r g b a u v nx ny nz per vertex. */
   static final class Poly {
      final float[][] v;
      final String tex;
      final boolean translucent;
      final String tag;

      Poly(float[][] v, String tex, boolean translucent, String tag) {
         this.v = v;
         this.tex = tex;
         this.translucent = translucent;
         this.tag = tag;
      }
   }

   static final class Capture implements MultiBufferSource {
      final List<Poly> polys = new ArrayList<>();
      final Map<RenderType, Sink> sinks = new LinkedHashMap<>();
      String tag = "";

      @Override
      public VertexConsumer getBuffer(RenderType type) {
         Sink s = this.sinks.computeIfAbsent(type, t -> new Sink(this, t));
         s.flushVertex();
         return s;
      }

      void finish() {
         for (Sink s : this.sinks.values()) {
            s.flushVertex();
         }
      }

      int triangles() {
         this.finish();
         int n = 0;
         for (Poly p : this.polys) {
            n += p.v.length - 2;
         }
         return n;
      }
   }

   static final class Sink implements VertexConsumer {
      final Capture cap;
      final String tex;
      final int per;
      final boolean translucent;
      float[] cur;
      String primTag = "";
      final List<float[]> prim = new ArrayList<>();

      Sink(Capture cap, RenderType t) {
         this.cap = cap;
         String s = t.toString();
         Matcher m = TEX.matcher(s);
         this.tex = m.find() ? m.group(1) : "";
         this.per = t.mode() == VertexFormat.Mode.QUADS ? 4 : 3;
         this.translucent = s.contains("translucent") || s.contains("TRANSLUCENT");
      }

      void flushVertex() {
         if (this.cur != null) {
            this.prim.add(this.cur);
            this.cur = null;
            if (this.prim.size() == this.per) {
               this.cap.polys.add(new Poly(this.prim.toArray(new float[0][]), this.tex, this.translucent, this.primTag));
               this.prim.clear();
            }
         }
      }

      @Override
      public VertexConsumer addVertex(float x, float y, float z) {
         this.flushVertex();
         if (this.prim.isEmpty()) {
            this.primTag = this.cap.tag;
         }
         this.cur = new float[]{x, y, z, 1, 1, 1, 1, 0, 0, 0, 1, 0};
         return this;
      }

      @Override
      public VertexConsumer setColor(int r, int g, int b, int a) {
         this.cur[3] = r / 255.0F;
         this.cur[4] = g / 255.0F;
         this.cur[5] = b / 255.0F;
         this.cur[6] = a / 255.0F;
         return this;
      }

      @Override
      public VertexConsumer setUv(float u, float v) {
         this.cur[7] = u;
         this.cur[8] = v;
         return this;
      }

      @Override
      public VertexConsumer setUv1(int u, int v) {
         return this;
      }

      @Override
      public VertexConsumer setUv2(int u, int v) {
         return this;
      }

      @Override
      public VertexConsumer setNormal(float x, float y, float z) {
         this.cur[9] = x;
         this.cur[10] = y;
         this.cur[11] = z;
         return this;
      }
   }

   // ------------------------------------------------------------------------------------------------ assembly
   /** What is fitted: attachment ids plus the sight ("" = irons). */
   record Rig(Weapon weapon, Set<String> att, String sight) {
   }

   static Method EMIT;
   static Method LOAD;

   @SuppressWarnings("unchecked")
   static List<Object> parts(String key) throws Exception {
      Field cacheF = FieldWeaponMesh.class.getDeclaredField("CACHE");
      cacheF.setAccessible(true);
      Object o = ((Map<String, Object>)cacheF.get(null)).get(key);
      if (o == null) {
         throw new IllegalStateException("no mesh " + key);
      }
      return (List<Object>)o;
   }

   /**
    * Mirror of FieldWeaponMesh.draw at rest (no shot, no reload, no draw animation) with the attachments taken from
    * the rig instead of the ItemStack. Keep in step with FieldWeaponMesh.draw.
    */
   static void drawField(Rig rig, String lod, PoseStack ps, Capture cap, float bipodDeploy) throws Exception {
      if (EMIT == null) {
         Class<?> partC = Class.forName("com.formaworks.frontierhunts.client.FieldWeaponMesh$Part");
         EMIT = FieldWeaponMesh.class.getDeclaredMethod("emit", partC, PoseStack.Pose.class, VertexConsumer.class, int.class);
         EMIT.setAccessible(true);
      }
      Weapon w = rig.weapon;
      FieldWeaponMesh.lod = lod;
      FieldWeaponMesh.clearDraw();
      String sight = rig.sight;
      float folded = sight.isEmpty() ? 0.0F : 1.0F;
      cap.tag = "gun";
      if (w == Weapon.FIELD_PISTOL) {
         CombatPistolMesh.draw(ps, cap.getBuffer(RenderType.entityCutoutNoCull(CombatPistolMesh.Tex.ID)), LIGHT, 0, 0, 0);
      } else {
         for (Object part : parts(w.id() + "_" + lod)) {
            int id = (int)part.getClass().getMethod("id").invoke(part);
            ps.pushPose();
            if (id == 7) {
               rotateX(ps, 0.106, 0.103, folded * -84.0F);
            } else if (id == 8) {
               rotateX(ps, 0.106, -0.377, folded * 84.0F);
            }
            if (id == 3) {
               cap.tag = "magazine";
            }
            if (id == 3 && rig.att.contains("extended_magazine") && WeaponAction.supports(w, "extended_magazine")) {
               cap.tag = "extended_magazine";
               FieldAttachmentHardware.magazine(false, ps, cap, LIGHT);
               cap.tag = "gun";
            } else {
               EMIT.invoke(null, part, ps.last(), cap.getBuffer(HuntRenderTypes.supplied(gunsTexture())), LIGHT);
               cap.finish();
               cap.tag = "gun";
            }
            ps.popPose();
         }
      }
      if (w == Weapon.FIELD_PISTOL && rig.att.contains("pistol_magazine")) {
         // the 2011 draws its own magazine; FieldWeaponMesh only swaps part 3 of .fheq guns
      }
      for (String m : new String[]{"suppressor", "muzzle_brake"}) {
         if (rig.att.contains(m) && WeaponAction.supports(w, m)) {
            cap.tag = m;
            ps.pushPose();
            ps.translate(0.0, FieldWeaponSockets.muzzleY(w), FieldWeaponSockets.muzzleZ(w));
            FieldAttachmentHardware.muzzle(m, ps, cap, LIGHT);
            ps.popPose();
         }
      }
      if (rig.att.contains("bipod") && WeaponAction.supports(w, "bipod")) {
         cap.tag = "bipod";
         ps.pushPose();
         ps.translate(0.0, FieldWeaponSockets.supportY(w) - 0.005, FieldWeaponSockets.supportZ(w) - 0.065);
         FieldAttachmentHardware.bipod(ps, cap, LIGHT, bipodDeploy);
         ps.popPose();
      }
      if (rig.att.contains("angled_foregrip") && WeaponAction.supports(w, "angled_foregrip")) {
         cap.tag = "angled_foregrip";
         ps.pushPose();
         ps.translate(0.0, FieldWeaponSockets.supportY(w), FieldWeaponSockets.supportZ(w));
         FieldAttachmentHardware.grip(ps, cap, LIGHT);
         ps.popPose();
      }
      if (rig.att.contains("steady_stock") && WeaponAction.supports(w, "steady_stock")) {
         cap.tag = "steady_stock";
         ps.pushPose();
         ps.translate(0.0, FieldWeaponSockets.stockY(w), FieldWeaponSockets.stockZ(w));
         ps.scale(1.0F, 1.0F, FieldWeaponSockets.stockScale(w));
         FieldAttachmentHardware.stock(ps, cap, LIGHT);
         ps.popPose();
      }
      double lift = FieldWeaponSockets.mountLift(w);
      if (FieldWeaponSockets.tactical(sight) && WeaponAction.supports(w, sight)) {
         cap.tag = "rail";
         if (lift > 0.0) {
            ps.pushPose();
            ps.translate(0.0, FieldWeaponSockets.opticY(w), FieldWeaponSockets.opticZ(w) - 0.012);
            FieldWeaponMesh.part("att_optic_rail_short", ps, cap, LIGHT);
            ps.popPose();
         }
         cap.tag = "sight";
         ps.pushPose();
         ps.translate(0.0, FieldWeaponSockets.opticY(w) + lift + 5.0E-4, FieldWeaponSockets.opticZ(w) - 0.012);
         FieldTacticalSight.draw(sight, ps, cap, LIGHT);
         ps.popPose();
      }
      boolean scope = Set.of("six_power_scope", "eight_power_scope", "twelve_power_scope", "thermal_scope").contains(sight)
         || w == Weapon.TRANQUILIZER_RIFLE && sight.isEmpty();
      if (scope && WeaponAction.supports(w, "six_power_scope")) {
         cap.tag = "rail";
         if (lift > 0.0) {
            ps.pushPose();
            ps.translate(0.0, FieldWeaponSockets.opticY(w), FieldWeaponSockets.opticZ(w) + 0.01);
            FieldWeaponMesh.part("att_optic_rail_long", ps, cap, LIGHT);
            ps.popPose();
         }
         cap.tag = "sight";
         ps.pushPose();
         ps.translate(0.0, FieldWeaponSockets.opticY(w) + lift - 0.0955, FieldWeaponSockets.opticZ(w) + 0.02);
         FieldMountedOptic.draw(ps, cap, LIGHT, sight.isEmpty() ? "six_power_scope" : sight);
         ps.popPose();
      }
      cap.tag = "";
      cap.finish();
   }

   /** FieldWeaponMesh.guns() when present (this branch), else the GUNS field (master), so the bench runs on both. */
   static ResourceLocation gunsTexture() {
      try {
         Method m = FieldWeaponMesh.class.getDeclaredMethod("guns");
         m.setAccessible(true);
         return (ResourceLocation)m.invoke(null);
      } catch (ReflectiveOperationException e) {
         return FieldWeaponMesh.GUNS;
      }
   }

   static void rotateX(PoseStack ps, double y, double z, float deg) {
      ps.translate(0.0, y, z);
      ps.mulPose(Axis.XP.rotationDegrees(deg));
      ps.translate(0.0, -y, -z);
   }

   // ----- Ridgeline (supplied mesh; CamoRifleModel needs the texture manager, so it is filled in by reflection)
   static boolean ridgelineReady;

   static void ridgelineBoot() throws Exception {
      Field cacheF = CamoRifleModel.class.getDeclaredField("CACHE");
      cacheF.setAccessible(true);
      Object[] cache = (Object[])cacheF.get(null);
      Field unsafeF = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
      unsafeF.setAccessible(true);
      sun.misc.Unsafe unsafe = (sun.misc.Unsafe)unsafeF.get(null);
      for (int slot : new int[]{0, 2}) {
         String lod = slot == 0 ? "close" : "distant";
         File f = find("assets/frontierhunts/models/item/camo_rifle_" + lod + ".fhrm");
         ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(f.toPath())).order(ByteOrder.LITTLE_ENDIAN);
         if (b.getInt() != 1297238086 || b.getInt() != 2) {
            throw new IOException("rifle header");
         }
         int nv = b.getInt();
         int nf = b.getInt();
         float[] v = new float[nv * 8];
         for (int i = 0; i < v.length; i++) {
            v[i] = b.getFloat();
         }
         int[][] idx = new int[3][nf * 3];
         int[] n = new int[3];
         for (int i = 0; i < nf; i++) {
            int a = b.getInt(), c = b.getInt(), d = b.getInt(), p = b.getInt();
            idx[p][n[p]++] = a;
            idx[p][n[p]++] = c;
            idx[p][n[p]++] = d;
         }
         Object model = unsafe.allocateInstance(CamoRifleModel.class);
         Field vf = CamoRifleModel.class.getDeclaredField("vertices");
         vf.setAccessible(true);
         unsafe.putObject(model, unsafe.objectFieldOffset(vf), v);
         Field inf = CamoRifleModel.class.getDeclaredField("indices");
         inf.setAccessible(true);
         int[][] four = new int[4][];
         for (int p = 0; p < 3; p++) {
            four[p] = java.util.Arrays.copyOf(idx[p], n[p]);
         }
         four[3] = new int[0];
         unsafe.putObject(model, unsafe.objectFieldOffset(inf), four);
         Method split = CamoRifleModel.class.getDeclaredMethod("splitScope");
         split.setAccessible(true);
         split.invoke(model);
         cache[slot] = model;
      }
      ridgelineReady = true;
   }

   static void drawRidgeline(String sight, boolean bipod, float deploy, PoseStack ps, Capture cap) throws Exception {
      if (!ridgelineReady) {
         ridgelineBoot();
      }
      RifleState state = new RifleState(3, true, false, 0, 0L, "", null, 0L, 3);
      cap.tag = "gun";
      CamoRifleModel.draw(ps, cap, LIGHT, state, 0.0, RidgelineOptics.STOCK.equals(sight));
      cap.tag = "sight";
      RidgelineSights.draw(ps, cap, LIGHT, sight);
      if (bipod) {
         cap.tag = "bipod";
         ps.pushPose();
         ps.translate(0.0F, RidgelineModel.BIPOD_Y, RidgelineModel.BIPOD_Z);
         String l = FieldWeaponMesh.lod;
         FieldWeaponMesh.lod = "close";
         FieldAttachmentHardware.bipod(ps, cap, LIGHT, deploy);
         FieldWeaponMesh.lod = l;
         ps.popPose();
      }
      cap.tag = "";
      cap.finish();
   }

   // ------------------------------------------------------------------------------------------------ raster
   static final class Raster {
      final int w, h;
      final float[] r, g, b, z;

      Raster(int w, int h, int bg0, int bg1) {
         this.w = w;
         this.h = h;
         this.r = new float[w * h];
         this.g = new float[w * h];
         this.b = new float[w * h];
         this.z = new float[w * h];
         java.util.Arrays.fill(this.z, Float.POSITIVE_INFINITY);
         for (int y = 0; y < h; y++) {
            float t = y / (float)(h - 1);
            for (int x = 0; x < w; x++) {
               int i = y * w + x;
               this.r[i] = lerp(((bg0 >> 16) & 255), ((bg1 >> 16) & 255), t) / 255.0F;
               this.g[i] = lerp(((bg0 >> 8) & 255), ((bg1 >> 8) & 255), t) / 255.0F;
               this.b[i] = lerp((bg0 & 255), (bg1 & 255), t) / 255.0F;
            }
         }
      }

      static float lerp(float a, float b, float t) {
         return a + (b - a) * t;
      }
   }

   /** Camera: view matrix (world -> camera, camera looks down -z) and either a perspective fov or an ortho half-height. */
   record Cam(Matrix4f view, double fovDeg, double orthoHalf) {
   }

   static final Vector3f L0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
   static final Vector3f L1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();

   static void raster(Raster rs, Cam cam, List<Poly> polys, boolean translucentPass) {
      double f = cam.orthoHalf > 0 ? 0 : 1.0 / Math.tan(Math.toRadians(cam.fovDeg) / 2.0);
      double asp = rs.w / (double)rs.h;
      Vector3f p = new Vector3f();
      Vector3f n = new Vector3f();
      for (Poly poly : polys) {
         if (poly.translucent != translucentPass) {
            continue;
         }
         int k = poly.v.length;
         double[] sx = new double[k], sy = new double[k], sz = new double[k], li = new double[k];
         boolean ok = true;
         for (int i = 0; i < k; i++) {
            float[] v = poly.v[i];
            cam.view.transformPosition(p.set(v[0], v[1], v[2]));
            cam.view.transformDirection(n.set(v[9], v[10], v[11]));
            if (n.lengthSquared() > 1e-12F) {
               n.normalize();
            }
            double depth = -p.z;
            if (cam.orthoHalf > 0) {
               sx[i] = (p.x / (cam.orthoHalf * asp) * 0.5 + 0.5) * rs.w;
               sy[i] = (0.5 - p.y / cam.orthoHalf * 0.5) * rs.h;
               sz[i] = depth;
            } else {
               if (depth < 0.01) {
                  ok = false;
                  break;
               }
               sx[i] = (p.x / depth * f / asp * 0.5 + 0.5) * rs.w;
               sy[i] = (0.5 - p.y / depth * f * 0.5) * rs.h;
               sz[i] = depth;
            }
            // Minecraft entity lighting (no-cull: back faces are lit with the flipped normal)
            double d0 = Math.max(0, n.dot(L0)), d1 = Math.max(0, n.dot(L1));
            li[i] = Math.min(1.0, (d0 + d1) * 0.6 + 0.4);
         }
         if (!ok) {
            continue;
         }
         BufferedImage tex = texture(poly.tex);
         for (int t = 1; t + 1 < k; t++) {
            tri(rs, cam.orthoHalf > 0, poly, tex, sx, sy, sz, li, 0, t, t + 1, translucentPass);
         }
      }
   }

   static void tri(Raster rs, boolean ortho, Poly poly, BufferedImage tex, double[] sx, double[] sy, double[] sz, double[] li, int a, int b, int c, boolean blend) {
      double minx = Math.min(sx[a], Math.min(sx[b], sx[c])), maxx = Math.max(sx[a], Math.max(sx[b], sx[c]));
      double miny = Math.min(sy[a], Math.min(sy[b], sy[c])), maxy = Math.max(sy[a], Math.max(sy[b], sy[c]));
      int x0 = Math.max(0, (int)Math.floor(minx)), x1 = Math.min(rs.w - 1, (int)Math.ceil(maxx));
      int y0 = Math.max(0, (int)Math.floor(miny)), y1 = Math.min(rs.h - 1, (int)Math.ceil(maxy));
      double den = (sy[b] - sy[c]) * (sx[a] - sx[c]) + (sx[c] - sx[b]) * (sy[a] - sy[c]);
      if (Math.abs(den) < 1e-12 || x0 > x1 || y0 > y1) {
         return;
      }
      float[] va = poly.v[a], vb = poly.v[b], vc = poly.v[c];
      double iza = ortho ? 1 : 1 / sz[a], izb = ortho ? 1 : 1 / sz[b], izc = ortho ? 1 : 1 / sz[c];
      int tw = tex == null ? 0 : tex.getWidth(), th = tex == null ? 0 : tex.getHeight();
      for (int y = y0; y <= y1; y++) {
         double py = y + 0.5;
         for (int x = x0; x <= x1; x++) {
            double px = x + 0.5;
            double l1 = ((sy[b] - sy[c]) * (px - sx[c]) + (sx[c] - sx[b]) * (py - sy[c])) / den;
            double l2 = ((sy[c] - sy[a]) * (px - sx[c]) + (sx[a] - sx[c]) * (py - sy[c])) / den;
            double l3 = 1 - l1 - l2;
            if (l1 < -1e-9 || l2 < -1e-9 || l3 < -1e-9) {
               continue;
            }
            double w1 = l1 * iza, w2 = l2 * izb, w3 = l3 * izc, ws = w1 + w2 + w3;
            w1 /= ws;
            w2 /= ws;
            w3 /= ws;
            double depth = ortho ? l1 * sz[a] + l2 * sz[b] + l3 * sz[c] : 1 / (l1 * iza + l2 * izb + l3 * izc);
            int i = y * rs.w + x;
            if (depth >= rs.z[i] - (blend ? 1e-5 : 0)) {
               continue;
            }
            double u = w1 * va[7] + w2 * vb[7] + w3 * vc[7];
            double v = w1 * va[8] + w2 * vb[8] + w3 * vc[8];
            double cr = w1 * va[3] + w2 * vb[3] + w3 * vc[3];
            double cg = w1 * va[4] + w2 * vb[4] + w3 * vc[4];
            double cb = w1 * va[5] + w2 * vb[5] + w3 * vc[5];
            double ca = w1 * va[6] + w2 * vb[6] + w3 * vc[6];
            double tr = 1, tg = 1, tb = 1, ta = 1;
            if (tex != null) {
               int argb = bilinear(tex, u * tw - 0.5, v * th - 0.5);
               ta = ((argb >>> 24) & 255) / 255.0;
               tr = ((argb >> 16) & 255) / 255.0;
               tg = ((argb >> 8) & 255) / 255.0;
               tb = (argb & 255) / 255.0;
            }
            double alpha = ta * ca;
            if (!blend && alpha < 0.1) {
               continue;
            }
            double lit = l1 * li[a] + l2 * li[b] + l3 * li[c];
            double r = tr * cr * lit, g = tg * cg * lit, bb = tb * cb * lit;
            if (blend) {
               rs.r[i] = (float)(rs.r[i] * (1 - alpha) + r * alpha);
               rs.g[i] = (float)(rs.g[i] * (1 - alpha) + g * alpha);
               rs.b[i] = (float)(rs.b[i] * (1 - alpha) + bb * alpha);
            } else {
               rs.r[i] = (float)r;
               rs.g[i] = (float)g;
               rs.b[i] = (float)bb;
               rs.z[i] = (float)depth;
            }
         }
      }
   }

   static int bilinear(BufferedImage t, double x, double y) {
      int w = t.getWidth(), h = t.getHeight();
      int x0 = (int)Math.floor(x), y0 = (int)Math.floor(y);
      double fx = x - x0, fy = y - y0;
      double[] acc = new double[4];
      for (int j = 0; j < 2; j++) {
         for (int i = 0; i < 2; i++) {
            int c = t.getRGB(Math.floorMod(Math.min(Math.max(x0 + i, 0), w - 1), w), Math.min(Math.max(y0 + j, 0), h - 1));
            double wt = (i == 0 ? 1 - fx : fx) * (j == 0 ? 1 - fy : fy);
            acc[0] += ((c >>> 24) & 255) * wt;
            acc[1] += ((c >> 16) & 255) * wt;
            acc[2] += ((c >> 8) & 255) * wt;
            acc[3] += (c & 255) * wt;
         }
      }
      return ((int)acc[0] << 24) | ((int)acc[1] << 16) | ((int)acc[2] << 8) | (int)acc[3];
   }

   static BufferedImage render(List<Poly> polys, Cam cam, int w, int h, int ss, int bg0, int bg1) {
      Raster rs = new Raster(w * ss, h * ss, bg0, bg1);
      raster(rs, cam, polys, false);
      // translucent: back to front by centroid depth
      List<Poly> tr = new ArrayList<>();
      for (Poly p : polys) {
         if (p.translucent) {
            tr.add(p);
         }
      }
      Vector3f tmp = new Vector3f();
      tr.sort((x, y) -> Double.compare(depthOf(y, cam, tmp), depthOf(x, cam, tmp)));
      raster(rs, cam, tr, true);
      BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            double r = 0, g = 0, b = 0;
            for (int j = 0; j < ss; j++) {
               for (int i = 0; i < ss; i++) {
                  int k = (y * ss + j) * rs.w + x * ss + i;
                  r += rs.r[k];
                  g += rs.g[k];
                  b += rs.b[k];
               }
            }
            double s = ss * ss;
            out.setRGB(x, y, (clamp(r / s) << 16) | (clamp(g / s) << 8) | clamp(b / s));
         }
      }
      return out;
   }

   static double depthOf(Poly p, Cam cam, Vector3f tmp) {
      double d = 0;
      for (float[] v : p.v) {
         cam.view.transformPosition(tmp.set(v[0], v[1], v[2]));
         d += -tmp.z;
      }
      return d / p.v.length;
   }

   static int clamp(double v) {
      return (int)Math.max(0, Math.min(255, Math.round(Math.pow(Math.max(0, v), 1.0) * 255)));
   }

   // ------------------------------------------------------------------------------------------------ views
   static float[] bounds(List<Poly> polys) {
      float[] bb = {1e9F, 1e9F, 1e9F, -1e9F, -1e9F, -1e9F};
      for (Poly p : polys) {
         for (float[] v : p.v) {
            for (int i = 0; i < 3; i++) {
               bb[i] = Math.min(bb[i], v[i]);
               bb[i + 3] = Math.max(bb[i + 3], v[i]);
            }
         }
      }
      return bb;
   }

   /** Orthographic view orbiting the bounds' centre: yaw around Y (0 = from +x: muzzle (-z) to the left), pitch down. */
   static Cam orbit(float[] bb, double yaw, double pitch, double margin, double aspect) {
      float cx = (bb[0] + bb[3]) / 2, cy = (bb[1] + bb[4]) / 2, cz = (bb[2] + bb[5]) / 2;
      Matrix4f view = new Matrix4f().rotateX((float)Math.toRadians(pitch)).rotateY((float)Math.toRadians(-90 + yaw)).translate(-cx, -cy, -cz);
      // fit: project the 8 corners
      Vector3f t = new Vector3f();
      double hx = 0, hy = 0;
      for (int i = 0; i < 8; i++) {
         view.transformPosition(t.set(bb[(i & 1) * 3], bb[1 + ((i >> 1) & 1) * 3], bb[2 + ((i >> 2) & 1) * 3]));
         hx = Math.max(hx, Math.abs(t.x));
         hy = Math.max(hy, Math.abs(t.y));
      }
      Matrix4f v2 = new Matrix4f().translate(0, 0, -5).mul(view);
      return new Cam(v2, 0, Math.max(hy, hx / aspect) * margin);
   }

   /**
    * First-person camera: the pose FieldWeaponFirstPerson builds at rest (aim 0, right hand) or full aim (aim 1);
    * returns the world->camera matrix for geometry drawn in gun space (already includes the 1.35 / 1.22 scale).
    */
   static Matrix4f firstPerson(Weapon w, String sight, float aim) {
      boolean pistol = w == Weapon.FIELD_PISTOL || w == Weapon.REVOLVER || w == Weapon.FLARE_GUN;
      float s = pistol ? 1.22F : 1.35F;
      double side = pistol ? 0.235 : 0.26;
      double y0 = pistol ? -0.3 : -0.305;
      double z0 = pistol ? -0.66 : -0.6;
      double axis = FieldWeaponSockets.opticalAxisY(w, sight);
      double x = side * (1 - aim);
      double y = y0 * (1 - aim) - axis * s * aim;
      double z = z0 * (1 - aim) - FieldWeaponSockets.eyeZ(w, sight) * s * aim;
      return new Matrix4f().translate((float)x, (float)y, (float)z).rotateZ((float)Math.toRadians(-3.0 * (1 - aim))).scale(s);
   }

   static Matrix4f ridgelineFirstPerson(String sight, float aim) {
      // RifleClient hand pose (rest) blended to the aim pose that puts RidgelineSights.axisY / eyeZ on the eye
      float s = 1.35F;
      double x = 0.3 * (1 - aim);
      double y = -0.315 * (1 - aim) - RidgelineSights.axisY(sight) * s * aim;
      double z = -0.42 * (1 - aim) - RidgelineSights.eyeZ(sight) * s * aim;
      return new Matrix4f().translate((float)x, (float)y, (float)z).rotateZ((float)Math.toRadians(-3.0 * (1 - aim))).scale(s);
   }

   static List<Poly> transformed(List<Poly> in, Matrix4f m) {
      List<Poly> out = new ArrayList<>(in.size());
      Vector3f p = new Vector3f(), n = new Vector3f();
      for (Poly poly : in) {
         float[][] v = new float[poly.v.length][];
         for (int i = 0; i < v.length; i++) {
            float[] s = poly.v[i].clone();
            m.transformPosition(p.set(s[0], s[1], s[2]));
            m.transformDirection(n.set(s[9], s[10], s[11]));
            if (n.lengthSquared() > 0) {
               n.normalize();
            }
            s[0] = p.x;
            s[1] = p.y;
            s[2] = p.z;
            s[9] = n.x;
            s[10] = n.y;
            s[11] = n.z;
            v[i] = s;
         }
         out.add(new Poly(v, poly.tex, poly.translucent, poly.tag));
      }
      return out;
   }

   // ------------------------------------------------------------------------------------------------ ADS
   /**
    * Sight axis measured from drawn geometry (gun space):
    * scopes / prism: centres of the front and rear glass discs (translucent polys tagged "sight");
    * red dots: centre of the window glass; irons: handled by the socket numbers (no glass).
    * Returns {x, yFront, yRear, zFront, zRear} or null.
    */
   static double[] glassAxis(List<Poly> polys) {
      // cluster translucent sight polys by z
      Map<Long, double[]> groups = new LinkedHashMap<>();
      for (Poly p : polys) {
         if (!p.translucent || !p.tag.equals("sight")) {
            continue;
         }
         for (float[] v : p.v) {
            long key = Math.round(v[2] * 200.0); // 5 mm bins
            double[] g = groups.computeIfAbsent(key, kk -> new double[7]);
            g[0] += v[0];
            g[1] += v[1];
            g[2] += v[2];
            g[3] += 1;
            g[4] = Math.min(g[4] == 0 ? 1e9 : g[4], v[1]);
            g[5] = Math.max(g[5] == 0 ? -1e9 : g[5], v[1]);
         }
      }
      if (groups.isEmpty()) {
         return null;
      }
      // merge neighbouring bins
      List<double[]> merged = new ArrayList<>();
      List<Long> keys = new ArrayList<>(groups.keySet());
      keys.sort(Long::compare);
      double[] cur = null;
      long last = Long.MIN_VALUE;
      for (long k : keys) {
         double[] g = groups.get(k);
         if (cur == null || k - last > 1) {
            cur = new double[]{0, 0, 0, 0, 1e9, -1e9};
            merged.add(cur);
         }
         cur[0] += g[0];
         cur[1] += g[1];
         cur[2] += g[2];
         cur[3] += g[3];
         cur[4] = Math.min(cur[4], g[4]);
         cur[5] = Math.max(cur[5], g[5]);
         last = k;
      }
      double[] front = merged.get(0), rear = merged.get(merged.size() - 1);
      // glass discs are rotationally symmetric: the vertex mean is the disc centre (y extent mid-point as a check)
      return new double[]{(front[0] / front[3] + rear[0] / rear[3]) / 2, (front[4] + front[5]) / 2, (rear[4] + rear[5]) / 2, front[2] / front[3],
         rear[2] / rear[3], merged.size(), rear[4], rear[5]};
   }


   // ------------------------------------------------------------------------------------------------ [guns3] reload frames
   static float win(double v, double a, double b, double c) {
      if (v < a || v > c) {
         return 0.0F;
      }
      return v < b ? sm((v - a) / (b - a)) : 1.0F - sm((v - b) / (c - b));
   }

   static float sm(double v) {
      v = Math.max(0.0, Math.min(1.0, v));
      return (float)(v * v * (3 - 2 * v));
   }

   /** A brass/red marker shell (12 ga: 18.5 mm x 70 mm; rifle: 8 x 55 mm) along local z, centred on the origin. */
   static void shell(PoseStack ps, Capture cap, boolean shotgun) {
      float r = shotgun ? 0.0093F : 0.0042F, len = shotgun ? 0.07F : 0.055F;
      VertexConsumer vc = cap.getBuffer(HuntRenderTypes.supplied(gunsTexture()));
      float u = 0.5F + 0.12F, v = 0.25F + 0.12F; // BRASS cell
      float[][] c = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
      PoseStack.Pose pose = ps.last();
      for (int i = 0; i < 4; i++) {
         float[] a = c[i], b = c[(i + 1) % 4];
         float[][] q = {{a[0], a[1], -len / 2}, {b[0], b[1], -len / 2}, {b[0], b[1], len / 2}, {a[0], a[1], len / 2}};
         for (float[] k : q) {
            vc.addVertex(pose, k[0], k[1], k[2]).setColor(shotgun && k[2] > 0 ? 0xFFB03030 : 0xFFFFFFFF).setUv(u, v).setOverlay(0)
               .setLight(LIGHT).setNormal(pose, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2, 0);
         }
      }
   }

   static void reloadFrames(File out) throws Exception {
      int W = 480, H = 300;
      Weapon[] ws = {Weapon.PUMP_SHOTGUN, Weapon.SEMI_AUTO_SHOTGUN, Weapon.LEVER_RIFLE, Weapon.SEMI_AUTO_RIFLE};
      capField(new Rig(Weapon.PUMP_SHOTGUN, Set.of(), ""), "close", 0); // loads the meshes and the emit hook
      for (Weapon w : ws) {
         boolean tube = WeaponAction.reload(w) == WeaponAction.Reload.TUBE;
         int dur = WeaponAction.duration(w, 3);
         double[] ts = tube ? new double[]{4, 16, 21, 26, 33, 50, dur - 8, dur - 5, dur - 2} : new double[]{4, 10, 18, 26, 34, 40, 44, 46, 48};
         List<BufferedImage> cells = new ArrayList<>();
         for (double t : ts) {
            float p = (float)Math.max(0, Math.min(1, t / dur));
            boolean empty = true;
            // FieldWeaponMesh.draw: reload "open" amount (magazine drop / breach)
            float open = win(p, 0.0, 0.19, 0.97);
            if (p > 0.19F && p < 0.77F) {
               open = 1.0F;
            } else if (p >= 0.77F) {
               open = 1.0F - sm((p - 0.77) / 0.2);
            }
            float cycle = tube && (w == Weapon.LEVER_RIFLE || w == Weapon.PUMP_SHOTGUN) ? win(t, dur - 9, dur - 4, dur) : 0.0F;
            float slide = 0.0F;
            if (w == Weapon.SEMI_AUTO_SHOTGUN) {
               slide = t < dur - 6 ? 1.0F : 1.0F - sm((t - (dur - 6)) / 5.0);
            } else if (w == Weapon.SEMI_AUTO_RIFLE) {
               slide = p > 0.8 ? 1.0F - sm((p - 0.8) / 0.18) : 1.0F;
            }
            // FieldWeaponFirstPerson: reload pose
            float s33 = (float)Math.sin(p * Math.PI);
            float rx = 0, rz = 0, ry = 0, down = 0;
            float side = 0.0F;
            if (tube) {
               // defaults = FieldWeaponFirstPerson's TUBE pose (lever / shotguns); -Dtube.* to try others
               boolean lev = w == Weapon.LEVER_RIFLE;
               rz = Float.parseFloat(System.getProperty("tube.rz", lev ? "-12" : "-52")) * s33;
               rx = Float.parseFloat(System.getProperty("tube.rx", lev ? "8" : "16")) * s33;
               down = Float.parseFloat(System.getProperty("tube.down", lev ? "-0.08" : "-0.11")) * s33;
               ry = Float.parseFloat(System.getProperty("tube.ry", lev ? "-28" : "-5")) * s33;
               side = Float.parseFloat(System.getProperty("tube.x", lev ? "-0.05" : "-0.08")) * s33;
            } else {
               float a = win(p, 0.0, 0.18, 0.62), b = win(p, 0.55, 0.7, 0.9);
               rz = -22.0F * a - 10.0F * b; rx = 8.0F * a + 14.0F * b; down = 0.05F * a;
            }
            Matrix4f view = new Matrix4f().translate(0.26F + side, -0.305F - down, -0.6F).rotateX((float)Math.toRadians(rx)).rotateY((float)Math.toRadians(ry))
               .rotateZ((float)Math.toRadians(-3.0F + rz));
            Capture cap = new Capture();
            cap.tag = "gun";
            PoseStack ps = new PoseStack();
            ps.pushPose();
            ps.scale(1.35F, 1.35F, 1.35F);
            FieldWeaponMesh.lod = "close";
            for (Object part : parts(w.id() + "_close")) {
               int id = (int)part.getClass().getMethod("id").invoke(part);
               ps.pushPose();
               switch (id) {
                  case 1 -> {
                     if (w == Weapon.PUMP_SHOTGUN) {
                        ps.translate(0.0F, 0.0F, cycle * 0.075F);
                     }
                  }
                  case 2 -> ps.translate(0.0, 0.0, w == Weapon.PUMP_SHOTGUN || w == Weapon.LEVER_RIFLE ? Math.min(1, cycle) * 0.048
                     : Math.min(1, slide) * (w == Weapon.SEMI_AUTO_RIFLE ? 0.028 : 0.046));
                  case 3 -> ps.translate(0.0, -open * 0.2, open * 0.024);
                  case 4 -> rotateX(ps, FieldWeaponSockets.leverY(w), FieldWeaponSockets.leverZ(w), cycle * 48.0F);
                  default -> {
                  }
               }
               EMIT.invoke(null, part, ps.last(), cap.getBuffer(HuntRenderTypes.supplied(gunsTexture())), LIGHT);
               ps.popPose();
            }
            ps.popPose();
            // FieldWeaponHands: the support hand and the round it carries (hand frame = unscaled gun frame)
            float sc = 1.35F;
            double hx = -0.035, hy = FieldWeaponSockets.supportY(w) * sc - 0.115, hz = FieldWeaponSockets.supportZ(w) * sc;
            float wgt = sm(p / 0.18) * (1.0F - sm((p - 0.76) / 0.22));
            boolean showRound = false;
            double tx = -0.02, ty = -0.32, tz = 0.065;
            if (tube && !Boolean.getBoolean("tube.oldhands")) {
               float k = win((t - 12.0) % 17.0, 0.0, 9.0, 17.0);
               double[] port = FieldWeaponSockets.loadingPort(w);
               double sx = port[0] * sc, sy = port[1] * sc, sz = port[2] * sc;
               double rx0 = sx + (1.0F - k) * port[3], ry0 = sy - (1.0F - k) * 0.075, rz0 = sz + (1.0F - k) * 0.03;
               tx = rx0 + (w == Weapon.LEVER_RIFLE ? 0.03 : -0.005);
               ty = ry0 - 0.085;
               tz = rz0 + 0.04;
               showRound = t > 12.0 && t < dur - 9 && k > 0.15;
               double fx2 = hx + (tx - hx) * wgt, fy2 = hy + (ty - hy) * wgt, fz2 = hz + (tz - hz) * wgt;
               ps.pushPose();
               ps.translate(fx2, fy2, fz2);
               cap.tag = "hand";
               ps.pushPose();
               ps.scale(2.2F, 2.2F, 1.6F);
               shell(ps, cap, true);
               ps.popPose();
               ps.popPose();
               if (showRound) {
                  ps.pushPose();
                  ps.translate(rx0, ry0, rz0);
                  shell(ps, cap, w != Weapon.LEVER_RIFLE);
                  ps.popPose();
               }
               cap.finish();
               cells.add(label(fp(cap.polys, view, W, H), w.id() + String.format(Locale.ROOT, " t=%.0f p=%.2f", t, p)));
               continue;
            }
            if (tube) {
               float k = win((t - 12.0) % 17.0, 0.0, 9.0, 17.0);
               tx = w == Weapon.LEVER_RIFLE ? 0.12 : 0.025;
               ty = w == Weapon.LEVER_RIFLE ? -0.095 : -0.23;
               tz = -0.025;
               tx += (1.0F - k) * 0.055;
               ty -= (1.0F - k) * 0.11;
               showRound = t > 12.0 && t < dur - 9 && k > 0.15;
            }
            double fx = hx + (tx - hx) * wgt, fy = hy + (ty - hy) * wgt, fz = hz + (tz - hz) * wgt;
            ps.pushPose();
            ps.translate(fx, fy, fz);
            cap.tag = "hand";
            // a stand-in for the hand: a small box at the wrist point
            ps.pushPose();
            ps.scale(2.2F, 2.2F, 1.6F);
            shell(ps, cap, true);
            ps.popPose();
            ps.popPose();
            if (showRound && tube) {
               ps.pushPose();
               ps.translate(tx, ty + 0.11, tz - 0.025);
               ps.mulPose(Axis.XP.rotationDegrees(90.0F));
               shell(ps, cap, w != Weapon.LEVER_RIFLE);
               ps.popPose();
            }
            cap.finish();
            cells.add(label(fp(cap.polys, view, W, H), w.id() + String.format(Locale.ROOT, " t=%.0f p=%.2f", t, p)));
         }
         ImageIO.write(grid(cells, 3), "png", new File(out, "reload_" + w.id() + ".png"));
         System.out.println("reload " + w.id());
      }
   }


   // ------------------------------------------------------------------------------------------------ [gear21] hand loading
   /** The hand-loaded guns from outside (3/4 from the shooter's right and behind, and from the side) through a reload:
    *  break actions, the revolver, the single-shot dart and bait guns, with the real rounds from ReloadRounds. */
   static void loadView(File out) throws Exception {
      int W = 420, H = 300;
      Weapon[] ws = {Weapon.DOUBLE_BARREL, Weapon.FLARE_GUN, Weapon.REVOLVER, Weapon.TRANQUILIZER_RIFLE, Weapon.BAIT_LAUNCHER};
      capField(new Rig(Weapon.PUMP_SHOTGUN, Set.of(), ""), "close", 0);
      for (Weapon w : ws) {
         List<BufferedImage> cells = new ArrayList<>();
         float[] ps0 = {0.1F, 0.25F, 0.3F, 0.45F, 0.55F, 0.62F, 0.7F, 0.76F, 0.9F};
         for (int view = 0; view < 2; view++) {
            for (float p : ps0) {
               Capture cap = new Capture();
               cap.tag = "gun";
               PoseStack ps = new PoseStack();
               float open = ReloadRounds.open(p);
               for (Object part : parts(w.id() + "_close")) {
                  int id = (int)part.getClass().getMethod("id").invoke(part);
                  if (id == 9 && p > 0.2F && p < 0.97F) {
                     continue;
                  }
                  ps.pushPose();
                  if (id == 1 && w == Weapon.DOUBLE_BARREL) rotateX(ps, 0.017, -0.159, -open * 31.0F);
                  if (id == 1 && w == Weapon.FLARE_GUN) rotateX(ps, 0.014, -0.08, -open * 37.0F);
                  if ((id == 1 || id == 3 || id == 9) && w == Weapon.REVOLVER) {
                     ps.translate(0.0, 0.016, 0.068);
                     ps.mulPose(Axis.ZP.rotationDegrees(Math.min(1.0F, open) * 87.0F));
                     ps.translate(0.0, -0.016, -0.068);
                  }
                  if (id == 2 && WeaponAction.reload(w) == WeaponAction.Reload.SINGLE) {
                     ps.translate(0.0, ReloadRounds.BOLT_Y, 0.0);
                     ps.mulPose(Axis.ZP.rotationDegrees(ReloadRounds.boltLift(p) * 62.0F));
                     ps.translate(0.0, -ReloadRounds.BOLT_Y, 0.0);
                     ps.translate(0.0, 0.0, ReloadRounds.boltBack(p) * 0.046);
                  }
                  EMIT.invoke(null, part, ps.last(), cap.getBuffer(HuntRenderTypes.supplied(gunsTexture())), LIGHT);
                  ps.popPose();
               }
               cap.tag = "round";
               ReloadRounds.Frame f = ReloadRounds.frame(w, p);
               for (ReloadRounds.Round r : f.rounds()) {
                  ps.pushPose();
                  ps.translate(r.x(), r.y(), r.z());
                  ps.mulPose(Axis.XP.rotationDegrees(r.pitch()));
                  ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
                  ps.scale(r.scale(), r.scale(), r.scale());
                  for (Object part : parts(w.ammo + "_close")) {
                     EMIT.invoke(null, part, ps.last(), cap.getBuffer(HuntRenderTypes.supplied(gunsTexture())), LIGHT);
                  }
                  ps.popPose();
               }
               if (f.hand() != null) {
                  ps.pushPose();
                  ps.translate(f.hand().x(), f.hand().y(), f.hand().z());
                  cap.tag = "hand";
                  ps.scale(0.6F, 0.6F, 0.6F);
                  shell(ps, cap, true);
                  ps.popPose();
               }
               cap.finish();
               float[] bb = tagBounds(cap.polys, "gun");
               // frame the breech end
               float[] focus = {bb[0] - 0.04F, bb[1], Math.max(bb[2], -0.25F), bb[3] + 0.06F, bb[4] + 0.03F, Math.min(bb[5], 0.12F)};
               Cam cam = orbit(focus, view == 0 ? 150 : 90, view == 0 ? 25 : 5, 1.15, W / (double)H);
               cells.add(label(render(cap.polys, cam, W, H, 2, 0x8FB7D8, 0x6E8B5A), w.id() + String.format(Locale.ROOT, " p=%.2f", p)));
            }
         }
         ImageIO.write(grid(cells, 3), "png", new File(out, "load_" + w.id() + ".png"));
         System.out.println("load " + w.id());
      }
   }

   // ------------------------------------------------------------------------------------------------ [guns3] fit check
   static float[] tagBounds(List<Poly> polys, String tag) {
      float[] b = {1e9F, 1e9F, 1e9F, -1e9F, -1e9F, -1e9F};
      boolean any = false;
      for (Poly p : polys) {
         if (!p.tag.equals(tag)) {
            continue;
         }
         for (float[] v : p.v) {
            any = true;
            for (int k = 0; k < 3; k++) {
               b[k] = Math.min(b[k], v[k]);
               b[k + 3] = Math.max(b[k + 3], v[k]);
            }
         }
      }
      return any ? b : null;
   }

   static void fitSheets(File out) throws Exception {
      int W = 420, H = 300;
      for (Weapon w : GUNS) {
         if (w == Weapon.FIELD_PISTOL) {
            continue;
         }
         List<BufferedImage> cells = new ArrayList<>();
         String[][] cases = {{"magazine", ""}, {"extended_magazine", ""}, {"bipod", ""}, {"angled_foregrip", ""}, {"steady_stock", ""}, {"suppressor", ""},
            {"muzzle_brake", ""}, {"sight", "reflex_sight"}, {"sight", "six_power_scope"}};
         for (String[] c : cases) {
            String a = c[0];
            if (!a.equals("magazine") && !a.equals("sight") && !WeaponAction.supports(w, a)) {
               continue;
            }
            if (a.equals("sight") && !WeaponAction.supports(w, c[1])) {
               continue;
            }
            Set<String> att = a.equals("magazine") || a.equals("sight") ? Set.of() : Set.of(a);
            List<Poly> polys = capField(new Rig(w, att, c[1]), "close", 1);
            float[] bb = tagBounds(polys, a);
            if (bb == null) {
               continue;
            }
            for (int k = 0; k < 3; k++) {
               float m = 0.02F;
               bb[k] -= m;
               bb[k + 3] += m;
            }
            String name = a + (c[1].isEmpty() ? "" : " " + c[1]);
            cells.add(label(render(polys, orbit(bb, 0, 0, 1.3, W / (double)H), W, H, 3, BG0, BG1), w.id() + ": " + name + " side"));
            cells.add(label(render(polys, orbit(bb, 40, 22, 1.3, W / (double)H), W, H, 3, BG0, BG1), name + " 3/4"));
            cells.add(label(render(polys, orbit(bb, 150, -30, 1.3, W / (double)H), W, H, 3, BG0, BG1), name + " from below/behind"));
         }
         if (!cells.isEmpty()) {
            ImageIO.write(grid(cells, 3), "png", new File(out, "fit_" + w.id() + ".png"));
            System.out.println("fit " + w.id() + " " + cells.size() / 3);
         }
      }
   }

   // ------------------------------------------------------------------------------------------------ main
   static final Weapon[] GUNS = {Weapon.LEVER_RIFLE, Weapon.SEMI_AUTO_RIFLE, Weapon.PUMP_SHOTGUN, Weapon.DOUBLE_BARREL, Weapon.SEMI_AUTO_SHOTGUN,
      Weapon.REVOLVER, Weapon.FIELD_PISTOL, Weapon.TRANQUILIZER_RIFLE, Weapon.FLARE_GUN, Weapon.BAIT_LAUNCHER};
   static final String[] SIGHTS = {"", "reflex_sight", "micro_red_dot", "holographic_sight", "two_power_prism", "six_power_scope", "eight_power_scope",
      "twelve_power_scope", "thermal_scope"};
   static final String[] RIDGE_SIGHTS = {RidgelineOptics.STOCK, RidgelineOptics.IRONS, "four_power_optic", "six_power_scope", "eight_power_scope",
      "twelve_power_scope", "thermal_scope", "reflex_sight", "micro_red_dot", "holographic_sight", "two_power_prism"};

   public static void main(String[] args) throws Exception {
      String mode = args[0];
      File out = new File(args[1]);
      for (int i = 2; i < args.length; i++) {
         ROOTS.add(new File(args[i]));
      }
      boot(HuntConfig.Quality.CINEMATIC);
      out.mkdirs();
      switch (mode) {
         case "ads" -> ads(new PrintStream(new File(out, "ads.txt")));
         case "tris" -> tris(new PrintStream(new File(out, "tris.txt")));
         case "render" -> renderAll(out, args.length > 2 ? "" : "");
         case "reload" -> reloadFrames(out);
         case "fit" -> fitSheets(out);
         case "loadview" -> loadView(out);
         case "one" -> {
            for (String wht : System.getProperty("bench.what", "LEVER_RIFLE").split(",")) {
               renderOne(out, wht);
            }
         }
         default -> throw new IllegalArgumentException(mode);
      }
   }

   static boolean supportsSight(Weapon w, String s) {
      if (s.isEmpty()) {
         return true;
      }
      return WeaponAction.supports(w, s);
   }

   static void ads(PrintStream log) throws Exception {
      PrintStream both = new PrintStream(new java.io.OutputStream() {
         @Override
         public void write(int b) {
            System.out.write(b);
            log.write(b);
         }
      }, true);
      both.println("# ADS alignment: measured sight axis (drawn glass centres) vs FieldWeaponSockets.opticalAxisY / eyeZ (gun space)");
      both.println(String.format(Locale.ROOT, "%-20s %-20s %9s %9s %9s %9s %8s %9s %s", "weapon", "sight", "socketY", "frontY", "rearY", "dY(mm*)", "x", "eye-rear",
         "result"));
      int bad = 0;
      for (Weapon w : GUNS) {
         for (String s : SIGHTS) {
            if (s.isEmpty() || !supportsSight(w, s)) {
               continue;
            }
            Capture cap = new Capture();
            drawField(new Rig(w, Set.of(), s), "close", new PoseStack(), cap, 0);
            double[] ax = glassAxis(cap.polys);
            double sock = FieldWeaponSockets.opticalAxisY(w, s);
            bad += report(both, w.id(), s, sock, FieldWeaponSockets.eyeZ(w, s), ax);
            bad += housing(both, cap.polys, ax);
         }
      }
      for (String s : RIDGE_SIGHTS) {
         if (s.equals(RidgelineOptics.IRONS) || s.equals(RidgelineOptics.STOCK)) {
            continue;
         }
         Capture cap = new Capture();
         drawRidgeline(s, false, 0, new PoseStack(), cap);
         double[] ax = glassAxis(cap.polys);
         bad += report(both, "ridgeline", s, RidgelineSights.axisY(s), RidgelineSights.eyeZ(s), ax);
         bad += housing(both, cap.polys, ax);
      }
      both.println("# mm* = millimetres at Minecraft scale (1 block = 1 m); first person draws guns x1.35 / x1.22");
      both.println("FAILURES " + bad);
   }

   /**
    * Housing concentricity for scopes / prism: the opaque optic geometry within 6 mm of each glass plane and within 1.5x the
    * glass radius must be centred on the glass (mesh tube axis = drawn glass axis = socket axis), and no opaque vertex may
    * sit inside 85 % of the glass radius in front of the glass (nothing blocking the lens).
    */
   static int housing(PrintStream o, List<Poly> polys, double[] ax) {
      if (ax == null || ax[5] < 2) {
         return 0;
      }
      int bad = 0;
      for (int end = 0; end < 2; end++) {
         double gz = end == 0 ? ax[3] : ax[4];
         double gy = end == 0 ? ax[1] : ax[2];
         // glass radius from the translucent ring at this plane
         double gr = 0;
         for (Poly p : polys) {
            if (p.translucent && p.tag.equals("sight")) {
               for (float[] v : p.v) {
                  if (Math.abs(v[2] - gz) < 0.004) {
                     gr = Math.max(gr, Math.hypot(v[0] - ax[0], v[1] - gy));
                  }
               }
            }
         }
         double sx = 0, sy = 0;
         int n = 0, blocking = 0;
         for (Poly p : polys) {
            if (p.translucent || !p.tag.equals("sight")) {
               continue;
            }
            for (float[] v : p.v) {
               double r = Math.hypot(v[0] - ax[0], v[1] - gy);
               if (Math.abs(v[2] - gz) < 0.006 && r < gr * 1.25 && r > gr * 0.95) {
                  sx += v[0];
                  sy += v[1];
                  n++;
               }
               boolean outside = end == 0 ? v[2] < gz - 0.0005 : v[2] > gz + 0.0005;
               if (outside && Math.abs(v[2] - gz) < 0.02 && r < gr * 0.85) {
                  blocking++;
               }
            }
         }
         double cx = n > 0 ? sx / n : Double.NaN, cy = n > 0 ? sy / n : Double.NaN;
         double off = Math.hypot(cx - ax[0], cy - gy) * 1000;
         boolean ok = n > 8 && off < 0.5 && blocking == 0;
         o.println(String.format(Locale.ROOT, "    %s glass z %+.4f r %.4f: housing centre offset %.2f mm (%d verts), blocking verts %d %s",
            end == 0 ? "objective" : "ocular   ", gz, gr, off, n, blocking, ok ? "OK" : "FAIL"));
         bad += ok ? 0 : 1;
      }
      return bad;
   }

   static int report(PrintStream o, String w, String s, double sock, double eyeZ, double[] ax) {
      if (ax == null) {
         o.println(String.format(Locale.ROOT, "%-20s %-20s %9.4f   (no glass found)  FAIL", w, s, sock));
         return 1;
      }
      double meanY = (ax[1] + ax[2]) / 2;
      double dy = (meanY - sock) * 1000;
      double tilt = (ax[1] - ax[2]) * 1000;
      boolean ok;
      if (ax[5] >= 2) {
         // magnified optics / prism: both glass centres on the socket axis (0.5 mm), axis parallel to the bore line
         ok = Math.abs(dy) <= 0.6 && Math.abs(tilt) <= 0.5;
      } else {
         // red dots: the projected dot (socket axis) must sit well inside the window (middle half of its height)
         double h = ax[7] - ax[6];
         ok = sock >= ax[6] + 0.25 * h && sock <= ax[7] - 0.25 * h;
      }
      ok &= Math.abs(ax[0]) < 5e-4 && eyeZ > ax[4];
      o.println(String.format(Locale.ROOT, "%-20s %-20s %9.4f %9.4f %9.4f %+9.2f %+8.4f %9.4f %s (tilt %+.2f mm, %d glass groups)", w, s, sock, ax[1],
         ax[2], dy, ax[0], eyeZ - ax[4], ok ? "OK" : "FAIL", tilt, (int)ax[5]));
      return ok ? 0 : 1;
   }

   static void tris(PrintStream log) throws Exception {
      for (String lod : new String[]{"close", "field", "distant"}) {
         for (Weapon w : GUNS) {
            Capture cap = new Capture();
            drawField(new Rig(w, Set.of(), ""), lod, new PoseStack(), cap, 0);
            log.println(String.format(Locale.ROOT, "%-8s %-22s %7d", lod, w.id(), cap.triangles()));
         }
         for (String s : SIGHTS) {
            if (s.isEmpty()) {
               continue;
            }
            Capture cap = new Capture();
            FieldWeaponMesh.lod = lod;
            cap.tag = "sight";
            if (FieldWeaponSockets.tactical(s)) {
               FieldTacticalSight.draw(s, new PoseStack(), cap, LIGHT);
            } else {
               FieldMountedOptic.draw(new PoseStack(), cap, LIGHT, s);
            }
            log.println(String.format(Locale.ROOT, "%-8s %-22s %7d", lod, s, cap.triangles()));
         }
         for (String a : new String[]{"suppressor", "muzzle_brake", "bipod", "steady_stock", "angled_foregrip", "extended_magazine", "pistol_magazine",
            "sniper_magazine", "four_power_optic"}) {
            Capture cap = new Capture();
            FieldWeaponMesh.lod = lod;
            PoseStack ps = new PoseStack();
            switch (a) {
               case "suppressor", "muzzle_brake" -> FieldAttachmentHardware.muzzle(a, ps, cap, LIGHT);
               case "bipod" -> FieldAttachmentHardware.bipod(ps, cap, LIGHT, 1);
               case "steady_stock" -> FieldAttachmentHardware.stock(ps, cap, LIGHT);
               case "angled_foregrip" -> FieldAttachmentHardware.grip(ps, cap, LIGHT);
               case "extended_magazine" -> FieldAttachmentHardware.magazine(false, ps, cap, LIGHT);
               case "pistol_magazine" -> FieldAttachmentHardware.magazine(true, ps, cap, LIGHT);
               case "sniper_magazine" -> FieldAttachmentHardware.sniperMagazine(ps, cap, LIGHT, true);
               case "four_power_optic" -> FieldMountedOptic.draw(ps, cap, LIGHT, a);
               default -> {
               }
            }
            log.println(String.format(Locale.ROOT, "%-8s %-22s %7d", lod, a, cap.triangles()));
         }
         Capture cap = new Capture();
         FieldWeaponMesh.lod = lod;
         drawRidgeline(RidgelineOptics.STOCK, false, 0, new PoseStack(), cap);
         log.println(String.format(Locale.ROOT, "%-8s %-22s %7d", lod, "ridgeline(stock)", cap.triangles()));
      }
      log.close();
   }

   // ----- contact sheets
   static final int BG0 = 0x9AA7B0, BG1 = 0x5E6A70;

   static BufferedImage label(BufferedImage img, String text) {
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
      g.setColor(new Color(0, 0, 0, 140));
      g.fillRect(0, 0, img.getWidth(), 22);
      g.setColor(Color.WHITE);
      g.drawString(text, 6, 16);
      g.dispose();
      return img;
   }

   static BufferedImage grid(List<BufferedImage> cells, int cols) {
      int cw = cells.get(0).getWidth(), ch = cells.get(0).getHeight();
      int rows = (cells.size() + cols - 1) / cols;
      BufferedImage out = new BufferedImage(cw * cols, ch * rows, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = out.createGraphics();
      for (int i = 0; i < cells.size(); i++) {
         g.drawImage(cells.get(i), (i % cols) * cw, (i / cols) * ch, null);
      }
      g.dispose();
      return out;
   }

   static List<Poly> capField(Rig rig, String lod, float deploy) throws Exception {
      Capture c = new Capture();
      drawField(rig, lod, new PoseStack(), c, deploy);
      return c.polys;
   }

   static List<Poly> capRidge(String sight, boolean bipod) throws Exception {
      Capture c = new Capture();
      drawRidgeline(sight, bipod, 0, new PoseStack(), c);
      return c.polys;
   }

   static BufferedImage side(List<Poly> polys, int w, int h) {
      return render(polys, orbit(bounds(polys), 0, 0, 1.06, w / (double)h), w, h, 3, BG0, BG1);
   }

   static BufferedImage threeQuarter(List<Poly> polys, int w, int h) {
      return render(polys, orbit(bounds(polys), 35, 18, 1.08, w / (double)h), w, h, 3, BG0, BG1);
   }

   static BufferedImage fp(List<Poly> polys, Matrix4f view, int w, int h) {
      return fp(polys, view, w, h, 70);
   }

   static BufferedImage fp(List<Poly> polys, Matrix4f view, int w, int h, double fov) {
      BufferedImage img = render(transformed(polys, view), new Cam(new Matrix4f(), fov, 0), w, h, 2, 0x8FB7D8, 0x6E8B5A);
      Graphics2D g = img.createGraphics();
      g.setColor(new Color(255, 40, 40, 150));
      g.drawLine(w / 2 - 12, h / 2, w / 2 - 4, h / 2);
      g.drawLine(w / 2 + 4, h / 2, w / 2 + 12, h / 2);
      g.drawLine(w / 2, h / 2 - 12, w / 2, h / 2 - 4);
      g.drawLine(w / 2, h / 2 + 4, w / 2, h / 2 + 12);
      g.dispose();
      return img;
   }

   static void renderAll(File out, String unused) throws Exception {
      int W = 640, H = 300;
      // one sheet per weapon: side (irons), 3/4, first-person hip, first-person ADS irons, + with typical attachments
      for (Weapon w : GUNS) {
         List<BufferedImage> cells = new ArrayList<>();
         List<Poly> bare = capField(new Rig(w, Set.of(), ""), "close", 0);
         cells.add(label(side(bare, W, H), w.id() + " - side (" + count(bare) + " tris)"));
         cells.add(label(threeQuarter(bare, W, H), w.id() + " - 3/4"));
         cells.add(label(fp(bare, firstPerson(w, "", 0), W, H), "first person - hip"));
         cells.add(label(fp(bare, firstPerson(w, "", 1), W, H), "first person - ADS irons"));
         String scope = supportsSight(w, "six_power_scope") ? "six_power_scope" : (supportsSight(w, "reflex_sight") ? "reflex_sight" : "");
         Set<String> att = new TreeSet<>();
         for (String a : new String[]{"suppressor", "bipod", "angled_foregrip", "steady_stock", "extended_magazine"}) {
            if (WeaponAction.supports(w, a)) {
               att.add(a);
            }
         }
         List<Poly> kit = capField(new Rig(w, att, scope), "close", 0);
         cells.add(label(side(kit, W, H), "kit: " + String.join(", ", att) + " + " + scope));
         cells.add(label(threeQuarter(kit, W, H), "kit 3/4 (" + count(kit) + " tris)"));
         String red = supportsSight(w, "holographic_sight") ? "holographic_sight" : "";
         if (!red.isEmpty()) {
            List<Poly> rd = capField(new Rig(w, Set.of(), red), "close", 0);
            cells.add(label(fp(rd, firstPerson(w, red, 0.0F), W, H), "hip + holographic"));
            cells.add(label(fp(rd, firstPerson(w, red, 1.0F), W, H), "ADS holographic (centre = crosshair)"));
         }
         ImageIO.write(grid(cells, 2), "png", new File(out, "gun_" + w.id() + ".png"));
         System.out.println("sheet " + w.id());
      }
      // Ridgeline
      {
         List<BufferedImage> cells = new ArrayList<>();
         for (String s : RIDGE_SIGHTS) {
            List<Poly> p = capRidge(s, s.equals(RidgelineOptics.STOCK));
            cells.add(label(side(p, W, H), "ridgeline + " + (s.isEmpty() ? "irons" : s) + " (" + count(p) + ")"));
         }
         List<Poly> p = capRidge(RidgelineOptics.STOCK, false);
         cells.add(label(fp(p, ridgelineFirstPerson(RidgelineOptics.STOCK, 0), W, H), "ridgeline first person"));
         List<Poly> ir = capRidge(RidgelineOptics.IRONS, false);
         cells.add(label(fp(ir, ridgelineFirstPerson(RidgelineOptics.IRONS, 1), W, H), "ridgeline ADS irons"));
         ImageIO.write(grid(cells, 2), "png", new File(out, "gun_ridgeline.png"));
      }
      // sights / attachments close-ups (loose, side + 3/4)
      {
         List<BufferedImage> cells = new ArrayList<>();
         int w2 = 480, h2 = 260;
         for (String s : SIGHTS) {
            if (s.isEmpty()) {
               continue;
            }
            Capture cap = new Capture();
            cap.tag = "sight";
            FieldWeaponMesh.lod = "close";
            if (FieldWeaponSockets.tactical(s)) {
               FieldTacticalSight.draw(s, new PoseStack(), cap, LIGHT);
            } else {
               FieldMountedOptic.draw(new PoseStack(), cap, LIGHT, s);
            }
            cap.finish();
            cells.add(label(side(cap.polys, w2, h2), s + " (" + count(cap.polys) + ")"));
            cells.add(label(threeQuarter(cap.polys, w2, h2), s + " 3/4"));
            cells.add(label(render(cap.polys, orbit(bounds(cap.polys), 180 + 30, 12, 1.1, w2 / (double)h2), w2, h2, 3, BG0, BG1), s + " rear"));
         }
         {
            Capture cap = new Capture();
            cap.tag = "sight";
            FieldMountedOptic.draw(new PoseStack(), cap, LIGHT, "four_power_optic");
            cap.finish();
            cells.add(label(side(cap.polys, w2, h2), "four_power_optic (" + count(cap.polys) + ")"));
            cells.add(label(threeQuarter(cap.polys, w2, h2), "four_power_optic 3/4"));
            cells.add(label(render(cap.polys, orbit(bounds(cap.polys), 210, 12, 1.1, w2 / (double)h2), w2, h2, 3, BG0, BG1), "rear"));
         }
         ImageIO.write(grid(cells, 3), "png", new File(out, "sights.png"));
      }
      {
         List<BufferedImage> cells = new ArrayList<>();
         int w2 = 480, h2 = 260;
         for (String a : new String[]{"suppressor", "muzzle_brake", "bipod", "bipod_folded", "steady_stock", "angled_foregrip", "extended_magazine",
            "pistol_magazine", "sniper_magazine", "rail_long", "rail_short"}) {
            Capture cap = new Capture();
            FieldWeaponMesh.lod = "close";
            PoseStack ps = new PoseStack();
            switch (a) {
               case "suppressor", "muzzle_brake" -> FieldAttachmentHardware.muzzle(a, ps, cap, LIGHT);
               case "bipod" -> FieldAttachmentHardware.bipod(ps, cap, LIGHT, 1);
               case "bipod_folded" -> FieldAttachmentHardware.bipod(ps, cap, LIGHT, 0);
               case "steady_stock" -> FieldAttachmentHardware.stock(ps, cap, LIGHT);
               case "angled_foregrip" -> FieldAttachmentHardware.grip(ps, cap, LIGHT);
               case "extended_magazine" -> FieldAttachmentHardware.magazine(false, ps, cap, LIGHT);
               case "pistol_magazine" -> FieldAttachmentHardware.magazine(true, ps, cap, LIGHT);
               case "sniper_magazine" -> FieldAttachmentHardware.sniperMagazine(ps, cap, LIGHT, true);
               case "rail_long" -> FieldWeaponMesh.part("att_optic_rail_long", ps, cap, LIGHT);
               case "rail_short" -> FieldWeaponMesh.part("att_optic_rail_short", ps, cap, LIGHT);
               default -> {
               }
            }
            cap.finish();
            cells.add(label(side(cap.polys, w2, h2), a + " (" + count(cap.polys) + ")"));
            cells.add(label(threeQuarter(cap.polys, w2, h2), a + " 3/4"));
         }
         ImageIO.write(grid(cells, 4), "png", new File(out, "attachments.png"));
      }
   }

   static int count(List<Poly> polys) {
      int n = 0;
      for (Poly p : polys) {
         n += p.v.length - 2;
      }
      return n;
   }

   static void renderOne(File out, String what) throws Exception {
      List<Poly> polys = null;
      if (what.startsWith("ridgeline:")) {
         polys = capRidge(what.substring(10), false);
      } else if (what.startsWith("sight:")) {
         Capture cap = new Capture();
         cap.tag = "sight";
         String s = what.substring(6);
         FieldWeaponMesh.lod = "close";
         if (FieldWeaponSockets.tactical(s)) {
            FieldTacticalSight.draw(s, new PoseStack(), cap, LIGHT);
         } else {
            FieldMountedOptic.draw(new PoseStack(), cap, LIGHT, s);
         }
         cap.finish();
         polys = cap.polys;
      } else if (what.startsWith("part:")) {
         Capture cap = new Capture();
         FieldWeaponMesh.lod = "close";
         FieldWeaponMesh.part(what.substring(5), new PoseStack(), cap, LIGHT);
         cap.finish();
         polys = cap.polys;
      } else if (!what.startsWith("ads:")) {
         polys = capField(new Rig(Weapon.valueOf(what), Set.of(), ""), "close", 0);
      }
      String n = what.replace(':', '_');
      if (what.startsWith("ads:")) {
         // ads:<WEAPON or ridgeline>:<sight> - full-aim view at 25 deg fov with a centre mark
         String[] a = what.split(":", -1);
         boolean ridge = a[1].equals("ridgeline");
         List<Poly> p = ridge ? capRidge(a[2], false) : capField(new Rig(Weapon.valueOf(a[1]), Set.of(), a[2]), "close", 0);
         Matrix4f v = ridge ? ridgelineFirstPerson(a[2], 1) : firstPerson(Weapon.valueOf(a[1]), a[2], 1);
         ImageIO.write(fp(p, v, 900, 600, 30), "png", new File(out, "ads_" + a[1] + "_" + (a[2].isEmpty() ? "irons" : a[2]) + ".png"));
         return;
      }
      ImageIO.write(side(polys, 1500, 640), "png", new File(out, "one_" + n + "_side.png"));
      ImageIO.write(threeQuarter(polys, 1500, 700), "png", new File(out, "one_" + n + "_34.png"));
      ImageIO.write(render(polys, orbit(bounds(polys), 180 + 35, 15, 1.08, 1500 / 700.0), 1500, 700, 3, BG0, BG1), "png", new File(out, "one_" + n + "_rear.png"));
   }

   private GunBench() {
   }
}
