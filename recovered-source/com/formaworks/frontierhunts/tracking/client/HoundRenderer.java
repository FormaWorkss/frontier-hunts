package com.formaworks.frontierhunts.tracking.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.client.HuntRenderTypes;
import com.formaworks.frontierhunts.tracking.hound.TrackingHound;
import com.formaworks.frontierhunts.wildlife2026.client.SkinnedMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * [tracking] Tracking hound renderer, following the wildlife presets: Vanilla = box model, Ultra = full sculpted body
 * with the photographic coat up close (the low body, hound_bal.fhsk, with the distant coat further out).
 * Poses come from the synced work mode (sniffing, sitting, baying, slinking) and are smoothed per hound.
 */
public final class HoundRenderer extends MobRenderer<TrackingHound, HoundModel> {
   public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("frontierhunts", "tracking_hound"), "main");
   private static final String[] COATS = {"redbone", "bluetick"};
   private final ResourceLocation[] classic = new ResourceLocation[2], real = new ResourceLocation[2], far = new ResourceLocation[2]; // [presets] Balanced pixel coats retired
   private final ResourceLocation ultraMesh = rl("models/wildlife/hound_ultra.fhsk");
   private final ResourceLocation lowMesh = rl("models/wildlife/hound_bal.fhsk");
   private final WeakHashMap<TrackingHound, float[]> states = new WeakHashMap<>();
   private final HoundRig.Input input = new HoundRig.Input();
   private final Vector3f tmp = new Vector3f();
   private float[] matrices = new float[0];
   private float[] skinned = new float[0];
   private boolean broken;
   private boolean pushed;

   public HoundRenderer(EntityRendererProvider.Context context) {
      super(context, new HoundModel(context.bakeLayer(LAYER)), 0.36F);
      for (int i = 0; i < 2; i++) {
         this.classic[i] = rl("textures/entity/wildlife/hound_" + COATS[i] + ".png");
         this.real[i] = rl("textures/entity/wildlife/real/hound_" + COATS[i] + ".png");
         this.far[i] = rl("textures/entity/wildlife/real/hound_" + COATS[i] + "_far.png");
      }
   }

   private static ResourceLocation rl(String path) {
      return ResourceLocation.fromNamespaceAndPath("frontierhunts", path);
   }

   private static int coat(TrackingHound e) {
      return e.variant() == 1 ? 1 : 0;
   }

   @Override
   public ResourceLocation getTextureLocation(TrackingHound e) {
      return this.classic[coat(e)];
   }

   /** [presets] VANILLA or REALISTIC (the retired Minecraft+ style of the Balanced preset draws as REALISTIC). */
   private static HuntConfig.AnimalStyle style() {
      return com.formaworks.frontierhunts.client.FrontierGraphics.animalStyle();
   }

   private static double lodDistance() {
      int d;
      try {
         d = HuntConfig.ANIMAL_DETAIL.get().ordinal();
      } catch (RuntimeException e) {
         d = 1;
      }
      return switch (d) {
         case 0 -> 12.0;
         case 1 -> 22.0;
         case 2 -> 34.0;
         default -> 52.0;
      };
   }

   @Override
   protected void scale(TrackingHound e, PoseStack ps, float pt) {
      ps.scale(0.9F, 0.9F, 0.9F);
   }

   // [hound3] state slots
   private static final int S_SNIFF = 0, S_SIT = 1, S_BAY = 2, S_TUCK = 3, S_WAG = 4, S_LIE = 5, S_AIR = 6, S_AGE = 7, S_LOD = 8, S_POINT = 9,
      S_SPEED = 10, S_PHASE = 11, S_WALK = 12, S_TROT = 13, S_GALLOP = 14, S_TURN = 15, S_MODE = 16, S_LABEL = 17, S_N = 18;

   /** smooth the synced work mode into pose weights; integrate speed, gait and stride phase */
   private float[] animate(TrackingHound e, float pt) {
      float[] st = this.states.computeIfAbsent(e, k -> {
         float[] a = new float[S_N];
         a[S_MODE] = -1;
         return a;
      });
      float now = e.tickCount + pt;
      boolean snap = st[S_AGE] == 0.0F || now - st[S_AGE] > 20.0F;
      float dt = snap ? 1.0F : Mth.clamp(now - st[S_AGE], 0.0F, 5.0F);
      st[S_AGE] = now;
      int mode = e.mode();
      float ex = e.excitement();
      // ground speed from the (interpolated) motion, blocks per tick
      float raw = (float)Math.sqrt((e.getX() - e.xo) * (e.getX() - e.xo) + (e.getZ() - e.zo) * (e.getZ() - e.zo));
      if (e.isInSittingPose() || raw > 1.5F) {
         raw = 0.0F;
      }
      float ks = snap ? 1.0F : 1.0F - (float)Math.exp(-dt * 0.25F);
      st[S_SPEED] += (raw - st[S_SPEED]) * ks;
      float v = st[S_SPEED];
      // gait by speed: walk < ~2.6 m/s < trot < ~5.2 m/s < gallop
      float tw = v < 0.012F ? 0.0F : 1.0F - smooth((v - 0.115F) / 0.03F);
      float tg = smooth((v - 0.25F) / 0.04F);
      float tt = v < 0.012F ? 0.0F : Math.max(0.0F, 1.0F - tw - tg);
      float[] t = new float[S_N];
      t[S_WALK] = tw;
      t[S_TROT] = tt;
      t[S_GALLOP] = tg;
      boolean lying = e.flag(TrackingHound.F_LYING);
      boolean sitting = (e.isInSittingPose() || mode == TrackingHound.SIT) && !lying;
      boolean nose = e.flag(TrackingHound.F_NOSE);
      switch (mode) {
         case TrackingHound.TRACK, TrackingHound.CAST, TrackingHound.STRIKE, TrackingHound.SEARCH -> {
            t[S_SNIFF] = nose ? 1.0F : 0.0F;
            t[S_WAG] = 0.55F + 0.45F * ex;
         }
         case TrackingHound.BAY -> {
            t[S_BAY] = 0.8F + 0.2F * Mth.sin(now * 0.9F);
            t[S_WAG] = 1.0F;
         }
         case TrackingHound.FOUND -> {
            if (!e.flag(TrackingHound.F_DONE)) {
               t[S_BAY] = 0.35F + 0.35F * Mth.sin(now * 0.25F);
            }
            t[S_WAG] = 1.0F;
         }
         case TrackingHound.POINT -> t[S_POINT] = 1.0F;
         case TrackingHound.RETREAT -> t[S_TUCK] = 1.0F;
         default -> t[S_WAG] = v > 0.02F ? 0.3F : 0.45F;
      }
      if (e.flag(TrackingHound.F_WAITING)) {
         t[S_SNIFF] = 0.0F;
      }
      if (sitting) {
         t[S_SIT] = 1.0F;
         t[S_SNIFF] = 0.0F;
      }
      if (lying) {
         t[S_LIE] = 1.0F;
         t[S_SNIFF] = 0.0F;
         t[S_WAG] = 0.15F;
      }
      boolean air = !e.onGround() && !e.isInWater() && Math.abs(e.getDeltaMovement().y) > 0.12;
      t[S_AIR] = air ? 1.0F : 0.0F;
      for (int i : new int[]{S_SNIFF, S_SIT, S_BAY, S_TUCK, S_WAG, S_LIE, S_AIR, S_POINT, S_WALK, S_TROT, S_GALLOP}) {
         float rate = i == S_SIT || i == S_LIE ? (t[i] > st[i] ? 0.10F : 0.22F) : i >= S_WALK ? 0.2F : 0.16F;
         float k = snap ? 1.0F : 1.0F - (float)Math.exp(-dt * rate);
         st[i] += (t[i] - st[i]) * k;
      }
      // stride phase: one cycle per stride length of the blended gait
      // [hound4] the rig's effective stride (speed-scaled, reach-limited): planted paws don't skate
      float stride = HoundRig.strideFor(st[S_WALK], st[S_TROT], st[S_GALLOP], v);
      st[S_PHASE] += v / stride * dt * (e.isInWater() ? 0.0F : 1.0F);
      st[S_PHASE] -= Mth.floor(st[S_PHASE]);
      // body turn rate (radians / tick) for the spine bend
      float turn = Mth.wrapDegrees(e.yBodyRot - e.yBodyRotO) * Mth.DEG_TO_RAD;
      st[S_TURN] += (turn - st[S_TURN]) * (snap ? 1.0F : 1.0F - (float)Math.exp(-dt * 0.3F));
      // the owner sees the new order / state above his head for a few seconds
      if (st[S_MODE] != mode) {
         if (st[S_MODE] >= 0) {
            st[S_LABEL] = now + 70.0F;
         }
         st[S_MODE] = mode;
      }
      return st;
   }

   private static float smooth(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   /** [hound3] the hound's state in plain words (over his head after an order, and in the owner's HUD) */
   public static Component modeText(TrackingHound e) {
      String k = switch (e.mode()) {
         case TrackingHound.SIT -> e.flag(TrackingHound.F_LYING) ? "down" : "stay";
         case TrackingHound.TRACK -> e.flag(TrackingHound.F_WAITING) ? "waiting" : e.flag(TrackingHound.F_HOT) ? "hot" : "track";
         case TrackingHound.CAST -> e.flag(TrackingHound.F_WAITING) ? "waiting" : "cast";
         case TrackingHound.BAY -> "bay";
         case TrackingHound.FOUND -> e.flag(TrackingHound.F_DONE) ? "done" : "found";
         case TrackingHound.RETREAT -> "retreat";
         case TrackingHound.LOST -> "lost";
         case TrackingHound.SEARCH -> "search";
         case TrackingHound.STRIKE -> e.flag(TrackingHound.F_WAITING) ? "waiting" : "strike";
         case TrackingHound.POINT -> "point";
         case TrackingHound.STOP -> "stop"; // [hound4]
         default -> "heel";
      };
      return Component.translatable("hound.frontierhunts.mode." + k);
   }

   @Override
   public void render(TrackingHound e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
      float[] st = this.animate(e, pt);
      HoundModel m = this.model;
      m.sniff = st[S_SNIFF];
      m.sit = st[S_SIT];
      m.bay = st[S_BAY];
      m.tuck = st[S_TUCK];
      m.wag = st[S_WAG];
      m.lie = st[S_LIE];
      m.point = st[S_POINT];
      m.walk = st[S_WALK];
      m.trot = st[S_TROT];
      m.gallop = st[S_GALLOP];
      m.phase = st[S_PHASE];
      m.speed = st[S_SPEED];
      this.label(e, st, pt, ps, buffers, light);
      if (!this.broken && style() != HuntConfig.AnimalStyle.VANILLA) {
         this.pushed = false;
         try {
            this.renderSculpted(e, st, pt, ps, buffers, light);
            return;
         } catch (RuntimeException ex) {
            this.broken = true;
            com.mojang.logging.LogUtils.getLogger().error("Frontier hound: sculpted model failed, using Classic", ex);
            if (this.pushed) {
               ps.popPose();
               this.pushed = false;
            }
         }
      }
      super.render(e, yaw, pt, ps, buffers, light);
   }

   /** [hound3] the new order / state over his head for a few seconds - only the owner sees it */
   private void label(TrackingHound e, float[] st, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
      float now = e.tickCount + pt;
      if (st[S_LABEL] <= now || e.isInvisible()) {
         return;
      }
      net.minecraft.client.player.LocalPlayer me = net.minecraft.client.Minecraft.getInstance().player;
      if (me == null || !me.getUUID().equals(e.getOwnerUUID())) {
         return;
      }
      ps.pushPose();
      ps.translate(0.0, 0.32, 0.0);
      this.renderNameTag(e, modeText(e).copy().withStyle(net.minecraft.ChatFormatting.GOLD), ps, buffers, light, pt);
      ps.popPose();
   }

   private void renderSculpted(TrackingHound e, float[] st, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
      boolean ultra = true; // [presets] not Vanilla = Ultra (the Balanced pixel-coat look is retired)
      boolean near = false;
      if (ultra) {
         double lod = lodDistance();
         double swap = st[S_LOD] > 0.5F ? lod * 1.12 : lod;
         near = this.entityRenderDispatcher.distanceToSqr(e) < swap * swap;
         st[S_LOD] = near ? 1.0F : 0.0F;
      }
      SkinnedMesh mesh = SkinnedMesh.get(near ? this.ultraMesh : this.lowMesh);
      if (!mesh.valid()) {
         throw new IllegalStateException("hound mesh missing");
      }
      int c = coat(e);
      ResourceLocation tex = near ? this.real[c] : this.far[c]; // [presets]
      HoundTexture.ensure(tex);
      if (NeoForge.EVENT_BUS.post(new RenderLivingEvent.Pre<>(e, this, pt, ps, buffers, light)).isCanceled()) {
         return;
      }
      if (!e.isInvisible()) {
         HoundRig.Input in = this.input;
         in.age = e.tickCount + pt;
         float body = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
         in.headYaw = Mth.wrapDegrees(Mth.rotLerp(pt, e.yHeadRotO, e.yHeadRot) - body);
         in.headPitch = Mth.lerp(pt, e.xRotO, e.getXRot());
         in.speed = st[S_SPEED];
         in.phase = st[S_PHASE];
         in.walk = st[S_WALK];
         in.trot = st[S_TROT];
         in.gallop = st[S_GALLOP];
         in.sniff = st[S_SNIFF];
         in.sit = st[S_SIT];
         in.lie = st[S_LIE];
         in.bay = st[S_BAY];
         in.point = st[S_POINT];
         in.tuck = st[S_TUCK];
         in.wag = st[S_WAG];
         in.air = st[S_AIR];
         in.sweep = Mth.sin(in.age * 0.32F);
         in.turn = st[S_TURN];
         in.water = e.isInWater() && !e.onGround();
         int nb = mesh.names.length;
         if (this.matrices.length < nb * 12) {
            this.matrices = new float[nb * 12];
         }
         HoundRig.pose(mesh, in, this.matrices);
         ps.pushPose();
         this.pushed = true;
         float scale = e.getScale();
         ps.scale(scale, scale, scale);
         ps.mulPose(Axis.YP.rotationDegrees(180.0F - body));
         if (e.deathTime > 0) {
            float f = Mth.sqrt((e.deathTime + pt - 1.0F) / 20.0F * 1.6F);
            ps.mulPose(Axis.ZP.rotationDegrees(Math.min(f, 1.0F) * 90.0F));
         }
         int overlay = LivingEntityRenderer.getOverlayCoords(e, 0.0F);
         this.draw(mesh, ps.last(), buffers.getBuffer(HuntRenderTypes.supplied(tex)), light, overlay);
         // [1.1.8] the outline must not depend on which buffer source the caller passed: with Iris / Sodium the sculpted
         // hound can be drawn through a wrapped source, and then the outline silently vanished on the Ultra preset.
         // When the game says this hound glows, write the silhouette straight into the outline buffers.
         OutlineBufferSource outline = buffers instanceof OutlineBufferSource o ? o : null;
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         if (outline == null && mc.shouldEntityAppearGlowing(e)) {
            outline = mc.renderBuffers().outlineBufferSource();
            int tc = e.getTeamColor();
            outline.setColor(tc >> 16 & 255, tc >> 8 & 255, tc & 255, 255); // blaze orange, like the collar
         }
         if (outline != null) {
            this.drawOutline(mesh, outline.getBuffer(RenderType.outline(tex)));
         }
         ps.popPose();
         this.pushed = false;
      }
      RenderNameTagEvent tag = new RenderNameTagEvent(e, e.getDisplayName(), this, ps, buffers, light, pt);
      NeoForge.EVENT_BUS.post(tag);
      if (tag.canRender().isTrue() || tag.canRender().isDefault() && this.shouldShowName(e)) {
         this.renderNameTag(e, tag.getContent(), ps, buffers, light, pt);
      }
      NeoForge.EVENT_BUS.post(new RenderLivingEvent.Post<>(e, this, pt, ps, buffers, light));
   }

   private void draw(SkinnedMesh m, PoseStack.Pose pose, VertexConsumer vc, int light, int overlay) {
      int nv = m.vertexCount;
      if (this.skinned.length < nv * 6) {
         this.skinned = new float[nv * 6];
      }
      float[] M = this.matrices;
      float[] out = this.skinned;
      float[] P = m.pos, N = m.nrm, W = m.weight;
      byte[] B = m.bone;
      Matrix4f mat = pose.pose();
      Vector3f t = this.tmp;
      for (int v = 0; v < nv; v++) {
         float px = P[v * 3], py = P[v * 3 + 1], pz = P[v * 3 + 2];
         float nx = N[v * 3], ny = N[v * 3 + 1], nz = N[v * 3 + 2];
         float x = 0, y = 0, z = 0, a = 0, b = 0, c = 0;
         for (int k = 0; k < 4; k++) {
            float w = W[v * 4 + k];
            if (w <= 0.0F) {
               continue;
            }
            int o = (B[v * 4 + k] & 255) * 12;
            x += w * (M[o] * px + M[o + 1] * py + M[o + 2] * pz + M[o + 3]);
            y += w * (M[o + 4] * px + M[o + 5] * py + M[o + 6] * pz + M[o + 7]);
            z += w * (M[o + 8] * px + M[o + 9] * py + M[o + 10] * pz + M[o + 11]);
            a += w * (M[o] * nx + M[o + 1] * ny + M[o + 2] * nz);
            b += w * (M[o + 4] * nx + M[o + 5] * ny + M[o + 6] * nz);
            c += w * (M[o + 8] * nx + M[o + 9] * ny + M[o + 10] * nz);
         }
         float l = Mth.invSqrt(a * a + b * b + c * c + 1.0E-12F);
         int o = v * 6;
         mat.transformPosition(x, y, z, t);
         out[o] = t.x;
         out[o + 1] = t.y;
         out[o + 2] = t.z;
         pose.transformNormal(a * l, b * l, c * l, t);
         out[o + 3] = t.x;
         out[o + 4] = t.y;
         out[o + 5] = t.z;
      }
      int[] T = m.tris;
      float[] UV = m.uv;
      for (int i = 0; i < T.length; i++) {
         int v = T[i];
         int o = v * 6;
         // [perf2] one call per vertex: BufferBuilder writes the whole entity vertex at once (vanilla's fast path)
         // instead of six chained attribute calls, ~27k times per sculpted animal per pass
         vc.addVertex(out[o], out[o + 1], out[o + 2], -1, UV[v * 2], UV[v * 2 + 1], overlay, light, out[o + 3], out[o + 4], out[o + 5]);
      }
   }

   private void drawOutline(SkinnedMesh m, VertexConsumer oc) {
      float[] out = this.skinned;
      int[] T = m.tris;
      float[] UV = m.uv;
      for (int i = 0; i + 2 < T.length; i += 3) {
         for (int k = 0; k < 4; k++) {
            int v = T[i + Math.min(k, 2)];
            int o = v * 6;
            oc.addVertex(out[o], out[o + 1], out[o + 2]).setColor(-1).setUv(UV[v * 2], UV[v * 2 + 1]);
         }
      }
   }
}
