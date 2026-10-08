package com.formaworks.frontierhunts.wingshot.client;

import com.formaworks.frontierhunts.client.HuntRenderTypes;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import com.formaworks.frontierhunts.wildlife2026.client.SkinnedMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * [wingshot] Draws the Ultra birds' feathered wings ({@link WingGeometry}) with the body: built when the body is
 * posed (kept per bird, so a held far pose keeps its wings), drawn in the same pass with the species' wing atlas
 * (cutout, back faces culled: every feather has its own top and underside).
 */
public final class BirdWings {
   private static final ResourceLocation DUCK = ResourceLocation.fromNamespaceAndPath("frontierhunts", "textures/entity/wildlife/wings/duck.png");
   private static final ResourceLocation GROUSE = ResourceLocation.fromNamespaceAndPath("frontierhunts", "textures/entity/wildlife/wings/grouse.png");
   private static final WeakHashMap<WildlifeMob, float[]> CACHE = new WeakHashMap<>();
   private static final WingGeometry GEO = new WingGeometry();
   private static final float[] JL = new float[3], JR = new float[3];
   private static final Vector3f TMP = new Vector3f();

   private BirdWings() {
   }

   /** After the body was posed: build this frame's wings from the chest bone (render thread). */
   public static void build(WildlifeMob e, SkinnedMesh mesh, float[] matrices, BirdAnim.State st) {
      if (st == null || !st.wingsOut) {
         CACHE.remove(e);
         return;
      }
      int chest = mesh.bone("chest"), wl = mesh.bone("wing_l"), wr = mesh.bone("wing_r");
      if (chest < 0 || wl < 0 || wr < 0 || matrices.length < (chest + 1) * 12) {
         CACHE.remove(e);
         return;
      }
      for (int k = 0; k < 3; k++) {
         JL[k] = mesh.joint[wl * 3 + k];
         JR[k] = mesh.joint[wr * 3 + k];
      }
      GEO.build(e.species == WildlifeSpecies.GROUSE ? WingGeometry.GROUSE : WingGeometry.DUCK, st, matrices, chest * 12, JL, JR);
      int len = GEO.vertices() * WingGeometry.STRIDE + 1;
      float[] c = CACHE.get(e);
      if (c == null || c.length < len) {
         c = new float[Math.max(len, 1024)];
         CACHE.put(e, c);
      }
      System.arraycopy(GEO.data(), 0, c, 1, len - 1);
      c[0] = GEO.vertices();
   }

   /** Draw the cached wings with the body's pose (model space -> camera space). */
   public static void draw(WildlifeMob e, PoseStack.Pose pose, MultiBufferSource buffers, int light, int overlay) {
      float[] c = CACHE.get(e);
      if (c == null || c[0] < 3.0F) {
         return;
      }
      int n = (int)c[0];
      VertexConsumer vc = buffers.getBuffer(HuntRenderTypes.sculpt(e.species == WildlifeSpecies.GROUSE ? GROUSE : DUCK));
      Matrix4f mat = pose.pose();
      Vector3f t = TMP;
      for (int i = 0; i < n; i++) {
         int o = 1 + i * WingGeometry.STRIDE;
         mat.transformPosition(c[o], c[o + 1], c[o + 2], t);
         float x = t.x, y = t.y, z = t.z;
         pose.transformNormal(c[o + 3], c[o + 4], c[o + 5], t);
         vc.addVertex(x, y, z, -1, c[o + 6], c[o + 7], overlay, light, t.x, t.y, t.z);
      }
   }

   static void clear() {
      CACHE.clear();
   }
}
