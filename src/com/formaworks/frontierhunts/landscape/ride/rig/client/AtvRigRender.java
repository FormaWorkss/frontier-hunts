package com.formaworks.frontierhunts.landscape.ride.rig.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.client.HuntRenderTypes;
import com.formaworks.frontierhunts.landscape.ride.Atv;
import com.formaworks.frontierhunts.landscape.ride.rig.AtvRig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * [atvfuel] Draws the rear-rack rig (cargo box with animated lid, can cradles, jerry cans with straps) inside the ATV
 * renderer's model space, for both ATV looks: the realistic mesh (tan/charcoal texture) and the classic cube model
 * (woodland camo lid). Anchors are fitted to each model's actual rear rack (see tools/atvfuel_art.py previews).
 * Same render type as the realistic ATV mesh (frontier_sculpt: entity cutout shader, lightmap, culling) so it lights and
 * behaves identically with and without shader packs.
 */
public final class AtvRigRender {
   static final ResourceLocation MESH = FrontierHunts.id("models/entity/atv_rig.fhrg");
   static final ResourceLocation TEX_REAL = FrontierHunts.id("textures/entity/atv_rig.png");
   static final ResourceLocation TEX_CLASSIC = FrontierHunts.id("textures/entity/atv_rig_classic.png");
   static final int BODY = 0;
   static final int LID = 1;
   static final int BOX_FEET = 2;
   static final int CARRIER_L = 3;
   static final int CARRIER_R = 4;
   static final int CARRIER_FEET = 5;
   static final int CAN = 6;
   static final int STRAP = 7;
   /** Lid hinge (model px, y-down, relative to the box anchor) and full opening angle. */
   static final float HINGE_Y = -3.55F;
   static final float HINGE_Z = -2.65F;
   static final float LID_OPEN = 1.25F;
   static final float TRAY = 0.28F;
   /** {boxX, boxY, boxZ, boxLeg, canX, canY, canZ, canLeg}: realistic mesh / classic cube model. */
   static final float[] REAL = {0.0F, 9.25F, 13.6F, 0.65F, 6.8F, 9.55F, 13.3F, 0.45F};
   static final float[] CLASSIC = {0.0F, 10.5F, 13.25F, 0.0F, 6.85F, 10.22F, 13.3F, 0.0F};

   private static float[][] parts;
   private static int[][] colors;
   private static boolean failed;

   private AtvRigRender() {
   }

   private static boolean load() {
      if (parts != null) {
         return true;
      }
      if (failed) {
         return false;
      }
      try {
         Resource res = Minecraft.getInstance().getResourceManager().getResource(MESH).orElse(null);
         if (res == null) {
            failed = true;
            return false;
         }
         try (InputStream in = res.open(); DataInputStream d = new DataInputStream(new BufferedInputStream(in))) {
            byte[] magic = new byte[4];
            d.readFully(magic);
            if (magic[0] != 'F' || magic[1] != 'H' || magic[2] != 'R' || magic[3] != 'G') {
               throw new IllegalStateException("bad rig mesh");
            }
            d.readInt();
            int n = d.readInt();
            if (n < 8 || n > 64) {
               throw new IllegalStateException("bad rig part count " + n);
            }
            float[][] p = new float[n][];
            int[][] c = new int[n][];
            for (int i = 0; i < n; i++) {
               int tris = d.readInt();
               if (tris < 0 || tris > 100000) {
                  throw new IllegalStateException("bad rig part size");
               }
               float[] v = new float[tris * 3 * 8];
               int[] col = new int[tris * 3];
               for (int k = 0; k < tris * 3; k++) {
                  for (int j = 0; j < 8; j++) {
                     v[k * 8 + j] = d.readFloat();
                  }
                  col[k] = d.readInt();
               }
               p[i] = v;
               c[i] = col;
            }
            colors = c;
            parts = p;
            return true;
         }
      } catch (Exception e) {
         LogUtils.getLogger().warn("[atvfuel] could not load {}", MESH, e);
         failed = true;
         return false;
      }
   }

   /** Called by AtvRenderer with the pose already in ATV model space (scaled, y-down, model px = 1/16). */
   public static void render(Atv atv, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, boolean realistic) {
      int f = AtvRig.flags(atv);
      if ((f & (AtvRig.BOX | AtvRig.CARRIER)) == 0 || !load()) {
         return;
      }
      float[] a = realistic ? REAL : CLASSIC;
      VertexConsumer vc = buffers.getBuffer(HuntRenderTypes.sculpt(realistic ? TEX_REAL : TEX_CLASSIC));
      if ((f & AtvRig.BOX) != 0) {
         draw(vc, ps, BODY, a[0], a[1], a[2], 1.0F, 0.0F, light);
         float open = Mth.lerp(partialTick, atv.tank.lidO, atv.tank.lid);
         // ease out with a small settle so the lid swings rather than slides
         float ang = LID_OPEN * (open * open * (3.0F - 2.0F * open));
         draw(vc, ps, LID, a[0], a[1], a[2], 1.0F, ang, light);
         if (a[3] > 0.05F) {
            draw(vc, ps, BOX_FEET, a[0], a[1], a[2], a[3], 0.0F, light);
         }
      }
      if ((f & AtvRig.CARRIER) != 0) {
         for (int side = 0; side < 2; side++) {
            float x = side == 0 ? -a[4] : a[4];
            draw(vc, ps, side == 0 ? CARRIER_L : CARRIER_R, x, a[5], a[6], 1.0F, 0.0F, light);
            if (a[7] > 0.05F) {
               draw(vc, ps, CARRIER_FEET, x, a[5] + TRAY, a[6], a[7], 0.0F, light);
            }
            if ((f & (side == 0 ? AtvRig.CAN0 : AtvRig.CAN1)) != 0) {
               draw(vc, ps, CAN, x, a[5], a[6], 1.0F, 0.0F, light);
               draw(vc, ps, STRAP, x, a[5], a[6], 1.0F, 0.0F, light);
            }
         }
      }
   }

   private static void draw(VertexConsumer vc, PoseStack ps, int part, float x, float y, float z, float sy, float lid, int light) {
      float[] v = parts[part];
      int[] col = colors[part];
      ps.pushPose();
      ps.translate(x / 16.0F, y / 16.0F, z / 16.0F);
      if (lid != 0.0F) {
         ps.translate(0.0F, HINGE_Y / 16.0F, HINGE_Z / 16.0F);
         ps.mulPose(com.mojang.math.Axis.XP.rotation(lid));
         ps.translate(0.0F, -HINGE_Y / 16.0F, -HINGE_Z / 16.0F);
      }
      if (sy != 1.0F) {
         ps.scale(1.0F, sy, 1.0F);
      }
      PoseStack.Pose pose = ps.last();
      Matrix4f m = pose.pose();
      for (int k = 0, i = 0; i < v.length; i += 8, k++) {
         float c = Math.min(1.0F, (col[k] & 0xFF) / 255.0F * 1.15F); // baked AO (exported as shade/1.15)
         vc.addVertex(m, v[i] / 16.0F, v[i + 1] / 16.0F, v[i + 2] / 16.0F)
            .setColor(c, c, c, 1.0F)
            .setUv(v[i + 3], v[i + 4])
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, v[i + 5], v[i + 6], v[i + 7]);
      }
      ps.popPose();
   }
}
