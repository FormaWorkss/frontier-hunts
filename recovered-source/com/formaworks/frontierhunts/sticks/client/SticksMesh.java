package com.formaworks.frontierhunts.sticks.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.sticks.ShootingSticks;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * [sticks] The shooting sticks' mesh ({@code models/entity/shooting_sticks.fhsk}, made by tools/sticks/sticks_art.py)
 * and the one routine that puts a set together: three legs on hinges round the head, each of three telescoping
 * sections slid out to the leg's length, the head with its strap, and the padded yoke on its swivel. The same maths
 * draws the set in the world and, for a shooter resting on it, in the first-person hand pass.
 *
 * <p>Render thread only; no allocation per frame (scratch matrices, vertices transformed here).
 */
public final class SticksMesh {
   private static final Logger LOGGER = LoggerFactory.getLogger("frontierhunts/sticks");
   public static final ResourceLocation TEX = FrontierHunts.id("textures/entity/shooting_sticks.png");
   private static final ResourceLocation MESH = FrontierHunts.id("models/entity/shooting_sticks.fhsk");
   static final int UPPER = 0, MIDDLE = 1, LOWER = 2, HUB = 3, STRAP = 4, YOKE = 5;
   private static final String[] NAMES = {"upper", "middle", "lower", "hub", "strap", "yoke"};

   /** geometry shared with tools/sticks/mesh.py */
   static final float HINGE_R = 0.03F;
   static final float SWIVEL_TOP = 0.046F;
   static final float MIN_LEG = 0.74F;
   static final float MAX_LEG = 1.66F;

   private static float[][] parts;
   private static boolean failed;

   private static final Matrix4f LEG = new Matrix4f();
   private static final Matrix3f LEG_N = new Matrix3f();
   private static final Matrix4f M = new Matrix4f();
   private static final Matrix3f N = new Matrix3f();
   private static final Vector3f P = new Vector3f();
   private static final Vector3f Q = new Vector3f();

   private SticksMesh() {
   }

   static boolean ready() {
      if (parts == null && !failed) {
         load();
      }
      return parts != null;
   }

   /** Drop the mesh (resource reload). */
   static void clear() {
      parts = null;
      failed = false;
   }

   private static void load() {
      try (InputStream raw = Minecraft.getInstance().getResourceManager().getResourceOrThrow(MESH).open();
           DataInputStream in = new DataInputStream(raw)) {
         if (in.readInt() != 0x4648534B || in.readInt() != 1) {
            throw new IOException("not a sticks mesh");
         }
         int n = in.readInt();
         if (n < 1 || n > 16) {
            throw new IOException("part count " + n);
         }
         float[][] out = new float[NAMES.length][];
         for (int i = 0; i < n; i++) {
            String name = in.readUTF();
            int quads = in.readInt();
            if (quads < 0 || quads > 20000) {
               throw new IOException("quad count " + quads);
            }
            float[] d = new float[quads * 32];
            for (int k = 0; k < d.length; k++) {
               d[k] = in.readFloat();
               if (!Float.isFinite(d[k])) {
                  throw new IOException("bad vertex");
               }
            }
            for (int p = 0; p < NAMES.length; p++) {
               if (NAMES[p].equals(name)) {
                  out[p] = d;
               }
            }
         }
         for (int p = 0; p < NAMES.length; p++) {
            if (out[p] == null) {
               throw new IOException("missing part " + NAMES[p]);
            }
         }
         parts = out;
      } catch (IOException | RuntimeException e) {
         failed = true;
         LOGGER.error("Could not load the shooting sticks mesh: {}", e.toString());
      }
   }

   /** Middle and lower section offsets down a leg {@code legLen} long (hinge to sole), as tools/sticks/mesh.leg_layout. */
   static float middleOffset(float legLen) {
      float l = Math.clamp(legLen, MIN_LEG, MAX_LEG);
      return 0.06F + (l - MIN_LEG) * 0.5F;
   }

   static float lowerOffset(float legLen) {
      float l = Math.clamp(legLen, MIN_LEG, MAX_LEG);
      float e = (l - MIN_LEG) * 0.5F;
      return 0.08F + 2.0F * e;
   }

   /** Height of the hinge plane above the feet for legs {@code legLen} long splayed {@code splayDeg}. */
   static float hubHeight(float legLen, float splayDeg) {
      return legLen * (float)Math.cos(Math.toRadians(splayDeg));
   }

   /**
    * Draws a set standing at the origin of {@code base} (Y up, its ground centre): legs turned to {@code yaw}, splayed
    * {@code splayDeg}, {@code legLen} long; the yoke turned to {@code yokeYaw} and tilted {@code tilt} degrees (front
    * down positive, as Minecraft pitch). Yaws in Minecraft degrees (0 = facing +Z).
    */
   static void drawSet(Matrix4f base, Matrix3f baseN, VertexConsumer vc, int light, float yaw, float splayDeg, float legLen, float yokeYaw, float tilt,
      boolean strap) {
      if (!ready()) {
         return;
      }
      float hub = hubHeight(legLen, splayDeg);
      float sp = (float)Math.toRadians(splayDeg);
      float mid = middleOffset(legLen);
      float low = lowerOffset(legLen);
      float ry = (float)Math.toRadians(-yaw);
      for (int i = 0; i < 3; i++) {
         LEG.set(base).translate(0.0F, hub, 0.0F).rotateY(ry + (float)(Math.PI * 2.0 / 3.0 * i)).translate(0.0F, 0.0F, HINGE_R).rotateX(-sp);
         LEG_N.set(baseN).rotateY(ry + (float)(Math.PI * 2.0 / 3.0 * i)).rotateX(-sp);
         draw(parts[UPPER], LEG, LEG_N, vc, light);
         M.set(LEG).translate(0.0F, -mid, 0.0F);
         draw(parts[MIDDLE], M, LEG_N, vc, light);
         M.set(LEG).translate(0.0F, -low, 0.0F);
         draw(parts[LOWER], M, LEG_N, vc, light);
      }
      M.set(base).translate(0.0F, hub, 0.0F).rotateY(ry);
      N.set(baseN).rotateY(ry);
      draw(parts[HUB], M, N, vc, light);
      if (strap) {
         draw(parts[STRAP], M, N, vc, light);
      }
      float yy = (float)Math.toRadians(-yokeYaw);
      float tt = (float)Math.toRadians(Math.clamp(tilt, -14.0F, 14.0F));
      M.set(base).translate(0.0F, hub, 0.0F).rotateY(yy).translate(0.0F, SWIVEL_TOP, 0.0F).rotateX(tt);
      N.set(baseN).rotateY(yy).rotateX(tt);
      draw(parts[YOKE], M, N, vc, light);
   }

   private static void draw(float[] d, Matrix4f m, Matrix3f n, VertexConsumer vc, int light) {
      for (int i = 0; i < d.length; i += 8) {
         m.transformPosition(d[i], d[i + 1], d[i + 2], P);
         n.transform(d[i + 5], d[i + 6], d[i + 7], Q);
         float len = Q.length();
         if (len > 1.0E-6F) {
            Q.div(len);
         }
         vc.addVertex(P.x, P.y, P.z).setColor(-1).setUv(d[i + 3], d[i + 4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(Q.x, Q.y, Q.z);
      }
   }

   /** The cradle's contact height (forend underside) above the feet for this leg length and splay. */
   static float contact(float legLen, float splayDeg) {
      return hubHeight(legLen, splayDeg) + (float)ShootingSticks.HUB_BELOW;
   }
}
