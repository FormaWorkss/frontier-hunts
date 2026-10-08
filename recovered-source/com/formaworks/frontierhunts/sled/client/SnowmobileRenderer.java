package com.formaworks.frontierhunts.sled.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.client.HuntRenderTypes;
import com.formaworks.frontierhunts.sled.SledContent;
import com.formaworks.frontierhunts.sled.SnowmobileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * [1.2.5] The snowmobile: a full 3D mesh (tools/snowmobile/snowmobile_model.py) laid along the snow like the toboggan.
 * The skis and the bars turn with the steering, the track runs with the ground speed, the lamps glow, and the
 * windshield is tinted glass you can see through.
 */
public final class SnowmobileRenderer extends EntityRenderer<SnowmobileEntity> {
   static final ResourceLocation MESH = FrontierHunts.id("models/entity/snowmobile.fhvm");
   static final ResourceLocation TEX = FrontierHunts.id("textures/entity/snowmobile.png");
   static final ResourceLocation TRACK_TEX = FrontierHunts.id("textures/entity/snowmobile_track.png");
   static final int BODY = 0, SKI_L = 1, SKI_R = 2, BAR = 3, TRACK = 4, LIGHTS = 5, GLASS = 6;
   /** [1.2.5] the snow coat: three cutout levels, and which triangles each level covers */
   static final ResourceLocation[] SNOW = {FrontierHunts.id("textures/entity/snowmobile_snow_1.png"), FrontierHunts.id("textures/entity/snowmobile_snow_2.png"),
      FrontierHunts.id("textures/entity/snowmobile_snow_3.png")};
   static final float[] SNOW_THRESHOLD = {0.75F, 0.45F, 0.2F};
   /** per part, per triangle: how exposed it is to snow (low down, facing up, at the front), 0..1 */
   private static float[][] exposure;

   private static float[][] parts;
   private static float[][] pivots;
   private static boolean failed;

   @EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
         e.registerEntityRenderer(SledContent.SNOWMOBILE.get(), SnowmobileRenderer::new);
      }
   }

   public SnowmobileRenderer(EntityRendererProvider.Context ctx) {
      super(ctx);
      this.shadowRadius = 1.0F;
   }

   @Override
   public ResourceLocation getTextureLocation(SnowmobileEntity e) {
      return TEX;
   }

   static boolean load() {
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
            d.readFully(new byte[4]);
            int ver = d.readInt();
            int n = d.readInt();
            float[][] ps = new float[n][];
            float[][] pv = new float[n][3];
            for (int i = 0; i < n; i++) {
               for (int k = 0; k < 3; k++) {
                  pv[i][k] = d.readFloat();
               }
               if (ver >= 2) {
                  d.readInt();
               }
               int nt = d.readInt();
               float[] v = new float[nt * 24];
               for (int k = 0; k < v.length; k++) {
                  v[k] = d.readFloat();
               }
               ps[i] = v;
            }
            pivots = pv;
            parts = ps;
            exposure = exposure(ps);
            return true;
         }
      } catch (Exception ex) {
         org.slf4j.LoggerFactory.getLogger("frontierhunts").warn("snowmobile mesh failed to load", ex);
         failed = true;
         return false;
      }
   }

   @Override
   public void render(SnowmobileEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
      if (!load()) {
         super.render(e, yaw, pt, ps, buffers, light);
         return;
      }
      ps.pushPose();
      ps.translate(0.0, e.visY(pt) - Mth.lerp(pt, e.yo, e.getY()), 0.0);
      ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot())));
      // laid along the snow: pitch and roll about the middle of the track
      ps.translate(0.0, 0.25, 0.0);
      ps.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(pt, e.visPitchO, e.visPitch)));
      ps.mulPose(Axis.ZP.rotationDegrees(-Mth.lerp(pt, e.visRollO, e.visRoll)));
      ps.translate(0.0, -0.25, 0.0);
      float steer = Mth.lerp(pt, e.steerO, e.steer) * 0.4F; // radians: about 23 degrees at full lock
      float scroll = 0.0F;
      float[] tp = pivots[TRACK];
      if (tp[2] > 0.0F) {
         // tp = {texels per metre, lug period in texels, texture height}
         double off = Mth.lerp(pt, e.trackPosO, e.trackPos) * tp[0];
         off = off - Math.floor(off / tp[1]) * tp[1];
         scroll = (float)(off / tp[2]);
      }
      float coat = e.snowCoat();
      int coatLevel = coat < 0.08F ? 0 : coat < 0.38F ? 1 : coat < 0.7F ? 2 : 3;
      for (int i = 0; i < parts.length; i++) {
         ps.pushPose();
         if (i == SKI_L || i == SKI_R || i == BAR) {
            float[] p = pivots[i];
            ps.translate(p[0] / 16.0F, p[1] / 16.0F, p[2] / 16.0F);
            if (i == BAR) {
               // the post leans back: turn about its axis
               ps.mulPose(new Quaternionf().rotationAxis(steer * 0.8F, 0.0F, 0.93F, -0.37F));
            } else {
               ps.mulPose(Axis.YP.rotation(steer));
            }
            ps.translate(-p[0] / 16.0F, -p[1] / 16.0F, -p[2] / 16.0F);
         }
         boolean glass = i == GLASS;
         RenderType type = glass ? RenderType.entityTranslucent(TEX) : HuntRenderTypes.sculpt(i == TRACK ? TRACK_TEX : TEX);
         emit(buffers.getBuffer(type), ps.last(), parts[i], i == LIGHTS ? LightTexture.FULL_BRIGHT : light, i == TRACK ? scroll : 0.0F, glass);
         if (coatLevel > 0 && (i == BODY || i == SKI_L || i == SKI_R || i == BAR)) {
            snowCoat(buffers.getBuffer(RenderType.entityCutoutNoCull(SNOW[coatLevel - 1])), ps.last(), parts[i], exposure[i],
               SNOW_THRESHOLD[coatLevel - 1], light);
         }
         ps.popPose();
      }
      ps.popPose();
      super.render(e, yaw, pt, ps, buffers, light);
   }

   private static float[][] exposure(float[][] ps) {
      float[][] out = new float[ps.length][];
      for (int i = 0; i < ps.length; i++) {
         float[] v = ps[i];
         float[] w = new float[v.length / 24];
         for (int t = 0; t < w.length; t++) {
            int k = t * 24;
            float cy = (v[k + 1] + v[k + 9] + v[k + 17]) / 48.0F, cz = (v[k + 2] + v[k + 10] + v[k + 18]) / 48.0F;
            float ny = (v[k + 6] + v[k + 14] + v[k + 22]) / 3.0F, nz = (v[k + 7] + v[k + 15] + v[k + 23]) / 3.0F;
            float low = Mth.clamp((0.55F - cy) / 0.4F, 0.0F, 1.0F);
            float up = Mth.clamp((ny - 0.35F) / 0.5F, 0.0F, 1.0F);
            float front = Mth.clamp((cz - 0.9F) / 0.6F, 0.0F, 1.0F) * Mth.clamp(nz + 0.3F, 0.0F, 1.0F);
            w[t] = Math.max(low, Math.max(up * 0.85F, front * 0.9F));
         }
         out[i] = w;
      }
      return out;
   }

   /** [1.2.5] the snow caked on: the exposed triangles again, a hair out along their normals, in a cutout snow pattern */
   private static void snowCoat(VertexConsumer vc, PoseStack.Pose pose, float[] v, float[] w, float threshold, int light) {
      Matrix4f m = pose.pose();
      for (int t = 0; t < w.length; t++) {
         if (w[t] < threshold) {
            continue;
         }
         int k0 = t * 24;
         for (int c = 0; c < 4; c++) {
            int k = k0 + Math.min(c, 2) * 8;
            float nx = v[k + 5], ny = v[k + 6], nz = v[k + 7];
            float x = v[k] + nx * 0.09F, y = v[k + 1] + ny * 0.09F, z = v[k + 2] + nz * 0.09F;
            // a box projection of the position (tiles about every 0.6 blocks)
            float ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
            float u, uv;
            if (ay >= ax && ay >= az) {
               u = x / 10.0F;
               uv = z / 10.0F;
            } else if (ax >= az) {
               u = z / 10.0F;
               uv = y / 10.0F;
            } else {
               u = x / 10.0F;
               uv = y / 10.0F;
            }
            vc.addVertex(m, x / 16.0F, y / 16.0F, z / 16.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F).setUv(u, uv).setOverlay(OverlayTexture.NO_OVERLAY)
               .setLight(light).setNormal(pose, nx, ny, nz);
         }
      }
   }

   private static void emit(VertexConsumer vc, PoseStack.Pose pose, float[] v, int light, float dv, boolean quads) {
      Matrix4f m = pose.pose();
      for (int k = 0; k < v.length; k += 8) {
         vertex(vc, pose, m, v, k, light, dv);
         if (quads && (k / 8) % 3 == 2) {
            vertex(vc, pose, m, v, k, light, dv); // a triangle as a quad with its last corner twice
         }
      }
   }

   private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, float[] v, int k, int light, float dv) {
      vc.addVertex(m, v[k] / 16.0F, v[k + 1] / 16.0F, v[k + 2] / 16.0F)
         .setColor(1.0F, 1.0F, 1.0F, 1.0F)
         .setUv(v[k + 3], v[k + 4] + dv)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(light)
         .setNormal(pose, v[k + 5], v[k + 6], v[k + 7]);
   }
}
