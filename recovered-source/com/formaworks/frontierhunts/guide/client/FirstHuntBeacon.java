package com.formaworks.frontierhunts.guide.client;

import com.formaworks.frontierhunts.client.HuntShaderCompat;
import com.formaworks.frontierhunts.client.KillCamClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * [fharea] A soft gold column of light over the middle of the beginner area, so it can be found from a distance without
 * the Handbook: a thin bright core in a wide faint glow, rising from the ground and fading into the sky, a slow shimmer
 * running up it. It fades out as the hunter walks into the area and is gone in the middle of it.
 *
 * <p>Drawn like a beacon beam (the {@code beacon_beam} render type, translucent, no depth write, full bright), which
 * shader packs (Iris: the beaconbeam program) light and fog the way they treat beacons, so it looks at home with and
 * without shaders. Far beyond the fog it is drawn nearer along the same line of sight at the same apparent size, so it
 * stays visible past the render distance; terrain nearer than that still hides it. Never drawn with F1, in the shadow
 * pass, during the kill cam or a hunt cinematic, or when the hunter has hidden the first-hunt steps. 128 vertices a
 * frame (both windings), no allocation of its own.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class FirstHuntBeacon {
   static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath("frontierhunts", "textures/misc/first_hunt_beam.png");
   /** the column's height above its foot, and how many slices it is drawn in (for the vertical fade and shimmer) */
   static final float HEIGHT = 150.0F;
   static final int SLICES = 8;
   private static RenderType type;
   /** the ground at the area's middle, when the land there is loaded (looked up once a second, not per frame) */
   private static int groundY = Integer.MIN_VALUE;
   private static int groundX = Integer.MIN_VALUE, groundZ = Integer.MIN_VALUE;
   private static int groundAge;
   private static float fade;
   private static long lastNanos;
   private static final Vector3f V = new Vector3f();
   private static final Vector3f N = new Vector3f();
   private static final float[] SLICE_Y = new float[SLICES + 1];
   private static final float[] SLICE_A = new float[SLICES + 1];

   private FirstHuntBeacon() {
   }

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post e) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || !FirstHuntCompass.areaShown(FirstHuntClient.state)) {
         groundY = Integer.MIN_VALUE;
         return;
      }
      int x = Mth.floor(FirstHuntCompass.targetX()), z = Mth.floor(FirstHuntCompass.targetZ());
      if (x != groundX || z != groundZ || ++groundAge >= 20) {
         groundAge = 0;
         groundX = x;
         groundZ = z;
         groundY = mc.level.hasChunk(x >> 4, z >> 4) ? mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : Integer.MIN_VALUE;
      }
   }

   @SubscribeEvent
   public static void render(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      long nanos = System.nanoTime();
      float dt = lastNanos == 0L ? 0.016F : Math.min(0.1F, (nanos - lastNanos) / 1.0E9F);
      lastNanos = nanos;
      boolean show = mc.player != null && mc.level != null && !mc.options.hideGui && FirstHuntCompass.areaShown(FirstHuntClient.state)
         && !KillCamClient.active() && !cinematic();
      fade += ((show ? 1.0F : 0.0F) - fade) * (FirstHuntClient.reducedMotion() ? 1.0F : 1.0F - (float)Math.exp(-dt * 3.0F));
      if (fade < 0.01F || mc.player == null || HuntShaderCompat.shadowPass()) {
         return;
      }
      Vec3 cam = event.getCamera().getPosition();
      double tx = FirstHuntCompass.targetX(), tz = FirstHuntCompass.targetZ();
      double dx = tx - cam.x, dz = tz - cam.z;
      double dist = Math.sqrt(dx * dx + dz * dz);
      // gone in the middle of the area, full from just outside it
      float near = smooth((float)((dist - 14.0) / 70.0));
      float a = fade * near;
      if (a < 0.01F) {
         return;
      }
      // far away: the same picture drawn nearer, inside the fog-free distance
      double far = Math.max(48.0, mc.options.getEffectiveRenderDistance() * 16.0 * 0.62);
      double k = dist > far ? far / dist : 1.0;
      double foot = groundY != Integer.MIN_VALUE ? groundY - 2.0 : cam.y - 30.0;
      float bx = (float)(dx * k), bz = (float)(dz * k);
      float by0 = (float)((foot - cam.y) * k);
      // a little wider the farther it is, so it never thins to a flickering hair
      float core = (float)(Math.max(0.32, dist * 0.0032) * k), glow = core * 4.2F;
      // the camera-facing side vector (the column turns to face the hunter)
      float len = (float)Math.max(1.0E-3, Math.sqrt(bx * bx + bz * bz));
      float sx = -bz / len, sz = bx / len;
      // the vertical fade: up from the ground over a few blocks, full low down, thinning into the sky; a slow shimmer running up
      float phase = (float)((System.nanoTime() * 1.7E-9) % (Math.PI * 2.0));
      for (int i = 0; i <= SLICES; i++) {
         float f = i / (float)SLICES;
         float y = f * f * HEIGHT; // finer slices low down, where it's seen
         SLICE_Y[i] = by0 + y * (float)k;
         float rise = Mth.clamp(y / 5.0F, 0.0F, 1.0F);
         float top = 1.0F - smooth((f - 0.25F) / 0.75F);
         float shimmer = 0.86F + 0.14F * Mth.sin(y * 0.06F - phase);
         SLICE_A[i] = rise * top * shimmer;
      }
      if (type == null) {
         type = RenderType.beaconBeam(TEX, true);
      }
      PoseStack.Pose pose = event.getPoseStack().last();
      Matrix4f m = pose.pose();
      pose.normal().transform(0.0F, 1.0F, 0.0F, N);
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      VertexConsumer vc = buffers.getBuffer(type);
      // the faint wide glow, then the bright narrow core (warm gold, whiter in the middle)
      column(vc, m, bx, bz, sx, sz, glow, 1.0F, 0.78F, 0.38F, a * 0.17F);
      column(vc, m, bx, bz, sx, sz, core, 1.0F, 0.9F, 0.62F, a * 0.62F);
      buffers.endBatch(type);
   }

   /** one camera-facing ribbon: two halves, clear at the outer edges and {@code alpha} along the middle line */
   private static void column(VertexConsumer vc, Matrix4f m, float bx, float bz, float sx, float sz, float half, float r, float g, float b, float alpha) {
      for (int i = 0; i < SLICES; i++) {
         float y0 = SLICE_Y[i], y1 = SLICE_Y[i + 1];
         float a0 = alpha * SLICE_A[i], a1 = alpha * SLICE_A[i + 1];
         if (a0 < 0.002F && a1 < 0.002F) {
            continue;
         }
         for (int side = -1; side <= 1; side += 2) {
            float ox = bx + sx * half * side, oz = bz + sz * half * side;
            float ou = side < 0 ? 0.0F : 1.0F;
            // both windings: whichever way the culling faces, one of them is toward the camera (the other is culled)
            vertex(vc, m, bx, y0, bz, r, g, b, a0, 0.5F, 1.0F);
            vertex(vc, m, ox, y0, oz, r, g, b, 0.0F, ou, 1.0F);
            vertex(vc, m, ox, y1, oz, r, g, b, 0.0F, ou, 0.0F);
            vertex(vc, m, bx, y1, bz, r, g, b, a1, 0.5F, 0.0F);
            vertex(vc, m, bx, y0, bz, r, g, b, a0, 0.5F, 1.0F);
            vertex(vc, m, bx, y1, bz, r, g, b, a1, 0.5F, 0.0F);
            vertex(vc, m, ox, y1, oz, r, g, b, 0.0F, ou, 0.0F);
            vertex(vc, m, ox, y0, oz, r, g, b, 0.0F, ou, 1.0F);
         }
      }
   }

   private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a, float u, float v) {
      m.transformPosition(x, y, z, V);
      vc.addVertex(V.x, V.y, V.z).setColor(r, g, b, a).setUv(u, v).setLight(LightTexture.FULL_BRIGHT).setNormal(N.x, N.y, N.z);
   }

   private static boolean cinematic() {
      try {
         return com.formaworks.frontierhunts.client.HuntCinematics.active();
      } catch (RuntimeException | LinkageError ex) {
         return false;
      }
   }

   static float smooth(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }
}
