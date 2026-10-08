package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.killcam.KillCamNetwork;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * The kill cam's own world effects, all on the replay's slow-motion clock: the bullet (spinning, rifling engraving,
 * vapour trail), muzzle smoke, and the impact (fine blood mist, droplets that land and stay, a far-side spatter on
 * pass-throughs, a puff of hair). Drawn with a small dedicated shader at AFTER_LEVEL, the same path the mod's tracer
 * trails use, so it behaves the same with and without shader packs.
 */
public final class KillCamFx {
   private static final int MAX = 640;
   private static final byte MIST = 0;
   private static final byte DROP = 1;
   private static final byte HAIR = 2;
   private static final byte SMOKE = 3;
   private static final byte SPLAT = 4;
   private static ShaderInstance shader;
   private static VertexBuffer solidBuffer;
   private static VertexBuffer softBuffer;
   private static com.mojang.blaze3d.vertex.ByteBufferBuilder solidBytes;
   private static com.mojang.blaze3d.vertex.ByteBufferBuilder softBytes;

   private static final float[] px = new float[MAX];
   private static final float[] py = new float[MAX];
   private static final float[] pz = new float[MAX];
   private static final float[] ox = new float[MAX];
   private static final float[] oy = new float[MAX];
   private static final float[] oz = new float[MAX];
   private static final float[] vx = new float[MAX];
   private static final float[] vy = new float[MAX];
   private static final float[] vz = new float[MAX];
   private static final float[] age = new float[MAX];
   private static final float[] life = new float[MAX];
   private static final float[] size = new float[MAX];
   private static final float[] grow = new float[MAX];
   private static final float[] spin = new float[MAX];
   private static final int[] rgba = new int[MAX];
   private static final byte[] kind = new byte[MAX];
   private static int count;
   private static double simTo;
   private static double originX;
   private static double originY;
   private static double originZ;
   private static float light = 1.0F;
   private static final RandomSource RANDOM = RandomSource.create();

   private KillCamFx() {
   }

   @EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = Bus.MOD)
   public static final class Registration {
      @SubscribeEvent
      public static void shaders(RegisterShadersEvent event) throws IOException {
         event.registerShader(new ShaderInstance(event.getResourceProvider(), FrontierHunts.id("killcam_fx"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> KillCamFx.shader = s);
         event.registerShader(new ShaderInstance(event.getResourceProvider(), FrontierHunts.id("killcam_grade"), DefaultVertexFormat.POSITION), s -> KillCamOverlay.grade = s);
      }
   }

   static void reset() {
      count = 0;
      simTo = 0.0;
   }

   private static float quality() {
      return switch (HuntConfig.QUALITY.get()) {
         case PERFORMANCE -> 0.6F;
         case BALANCED -> 1.0F;
         case CINEMATIC -> 1.45F;
      };
   }

   // ------------------------------------------------------------------ emission

   static void muzzle(KillCamReplay r) {
      Vec3 o = r.shot.origin();
      originX = o.x;
      originY = o.y;
      originZ = o.z;
      light = lightAt(r.level, o);
      if (r.bow) {
         return;
      }
      Vec3 d = r.path.tangent(0.0);
      int n = Math.round(9 * quality());
      for (int i = 0; i < n; i++) {
         Vec3 p = o.add(d.scale(0.25 + RANDOM.nextFloat() * 0.25));
         Vec3 v = d.scale(0.015 + RANDOM.nextFloat() * 0.05).add(gauss(0.012)).add(0.0, 0.004, 0.0);
         float g = 0.62F + RANDOM.nextFloat() * 0.12F;
         spawn(SMOKE, p, v, 0.05F + RANDOM.nextFloat() * 0.05F, 0.018F, 26 + RANDOM.nextInt(14), g, g, g * 0.97F, 0.34F);
      }
   }

   static void impact(KillCamReplay r) {
      Vec3 i = r.impact();
      Vec3 d = r.dir();
      light = lightAt(r.level, i);
      originX = i.x;
      originY = i.y;
      originZ = i.z;
      float q = quality();
      float w = r.bow ? 0.55F : 1.0F;
      Vec3 n = d.scale(-1.0);
      float[] hair = hairColor(r);
      int mist = Math.round(16 * q * w);
      for (int k = 0; k < mist; k++) {
         Vec3 p = i.add(n.scale(0.02)).add(gauss(0.02));
         Vec3 v = n.scale(0.015 + RANDOM.nextFloat() * 0.05).add(gauss(0.022));
         float s = 0.022F + RANDOM.nextFloat() * 0.035F;
         spawn(MIST, p, v, s, 0.0085F, 10 + RANDOM.nextInt(8), 0.46F, 0.025F, 0.025F, 0.5F);
      }
      int drops = Math.round(13 * q * w);
      for (int k = 0; k < drops; k++) {
         Vec3 v = n.scale(0.025 + RANDOM.nextFloat() * 0.06).add(gauss(0.035)).add(0.0, 0.018, 0.0);
         spawn(DROP, i.add(n.scale(0.015)), v, 0.008F + RANDOM.nextFloat() * 0.01F, 0.0F, 80, 0.5F, 0.035F, 0.03F, 1.0F);
      }
      int hairs = Math.round(11 * q * (r.bow ? 0.7F : 1.0F));
      for (int k = 0; k < hairs; k++) {
         Vec3 v = n.scale(0.02 + RANDOM.nextFloat() * 0.045).add(gauss(0.03)).add(0.0, 0.015, 0.0);
         float t = 0.85F + RANDOM.nextFloat() * 0.3F;
         spawn(HAIR, i.add(gauss(0.02)), v, 0.009F + RANDOM.nextFloat() * 0.01F, 0.0F, 30 + RANDOM.nextInt(20), hair[0] * t, hair[1] * t, hair[2] * t, 0.95F);
      }
      Vec3 e = r.shot.exit();
      if (e != null && !r.bow) {
         int far = Math.round(22 * q);
         for (int k = 0; k < far; k++) {
            Vec3 v = d.scale(0.04 + RANDOM.nextFloat() * 0.1).add(gauss(0.04));
            float s = 0.03F + RANDOM.nextFloat() * 0.045F;
            spawn(MIST, e.add(gauss(0.025)), v, s, 0.011F, 12 + RANDOM.nextInt(8), 0.44F, 0.02F, 0.022F, 0.5F);
         }
         int fd = Math.round(18 * q);
         for (int k = 0; k < fd; k++) {
            Vec3 v = d.scale(0.07 + RANDOM.nextFloat() * 0.13).add(gauss(0.05)).add(0.0, 0.01, 0.0);
            spawn(DROP, e, v, 0.008F + RANDOM.nextFloat() * 0.012F, 0.0F, 80, 0.48F, 0.03F, 0.03F, 1.0F);
         }
         int fh = Math.round(6 * q);
         for (int k = 0; k < fh; k++) {
            float t = 0.85F + RANDOM.nextFloat() * 0.3F;
            spawn(HAIR, e, d.scale(0.04 + RANDOM.nextFloat() * 0.06).add(gauss(0.03)), 0.009F, 0.0F, 30, hair[0] * t, hair[1] * t, hair[2] * t, 0.95F);
         }
      }
   }

   private static float[] hairColor(KillCamReplay r) {
      if (r.standIn != null && r.standIn.entity instanceof Whitetail w) {
         DeerTraits t = w.traits();
         float shade = t.coatShade();
         if (t.greyCoat()) {
            return new float[]{0.5F * shade, 0.47F * shade, 0.43F * shade};
         }
         float warm = t.coatWarmth();
         return new float[]{Math.min(1.0F, 0.56F * shade * (1.0F + warm * 0.08F)), 0.40F * shade, 0.27F * shade * (1.0F - warm * 0.1F)};
      }
      if (r.standIn != null && r.standIn.entity instanceof WildlifeMob m) {
         int c = m.species.color;
         return new float[]{(c >> 16 & 255) / 255.0F, (c >> 8 & 255) / 255.0F, (c & 255) / 255.0F};
      }
      return new float[]{0.5F, 0.38F, 0.26F};
   }

   private static Vec3 gauss(double s) {
      return new Vec3(RANDOM.nextGaussian() * s, RANDOM.nextGaussian() * s, RANDOM.nextGaussian() * s);
   }

   private static void spawn(byte k, Vec3 p, Vec3 v, float s, float g, int l, float r, float gr, float b, float a) {
      if (count >= MAX) {
         return;
      }
      int i = count++;
      px[i] = ox[i] = (float)(p.x - originX);
      py[i] = oy[i] = (float)(p.y - originY);
      pz[i] = oz[i] = (float)(p.z - originZ);
      vx[i] = (float)v.x;
      vy[i] = (float)v.y;
      vz[i] = (float)v.z;
      age[i] = 0.0F;
      life[i] = l;
      size[i] = s;
      grow[i] = g;
      spin[i] = RANDOM.nextFloat() * 6.2831855F;
      kind[i] = k;
      float lr = Math.clamp(r * light, 0.0F, 1.0F);
      float lg = Math.clamp(gr * light, 0.0F, 1.0F);
      float lb = Math.clamp(b * light, 0.0F, 1.0F);
      rgba[i] = (int)(a * 255.0F) << 24 | (int)(lr * 255.0F) << 16 | (int)(lg * 255.0F) << 8 | (int)(lb * 255.0F);
   }

   private static float lightAt(ClientLevel level, Vec3 p) {
      BlockPos pos = BlockPos.containing(p);
      int block = level.getBrightness(LightLayer.BLOCK, pos);
      int sky = level.getBrightness(LightLayer.SKY, pos);
      float day = level.getSkyDarken(1.0F);
      float b = Math.max(block / 15.0F, sky / 15.0F * Mth.clamp((day - 0.2F) / 0.8F, 0.08F, 1.0F));
      return 0.22F + 0.78F * b;
   }

   // ------------------------------------------------------------------ simulation (virtual ticks)

   static void step(KillCamReplay r) {
      int steps = 0;
      while (simTo + 1.0 <= r.v && steps++ < 40) {
         simTo += 1.0;
         tick(r.level);
      }
      if (steps >= 40) {
         simTo = Math.floor(r.v);
      }
   }

   private static void tick(ClientLevel level) {
      int w = 0;
      for (int i = 0; i < count; i++) {
         ox[i] = px[i];
         oy[i] = py[i];
         oz[i] = pz[i];
         age[i] += 1.0F;
         byte k = kind[i];
         if (k != SPLAT && age[i] >= life[i]) {
            continue;
         }
         switch (k) {
            case MIST -> {
               vx[i] *= 0.78F;
               vy[i] = vy[i] * 0.78F - 0.0018F;
               vz[i] *= 0.78F;
               size[i] += grow[i];
            }
            case SMOKE -> {
               vx[i] *= 0.86F;
               vy[i] = vy[i] * 0.86F + 0.0022F;
               vz[i] *= 0.86F;
               size[i] += grow[i] * (1.0F - age[i] / life[i]);
            }
            case HAIR -> {
               vx[i] *= 0.9F;
               vy[i] = vy[i] * 0.9F - 0.0045F;
               vz[i] *= 0.9F;
               spin[i] += 0.35F;
            }
            case DROP -> {
               vx[i] *= 0.985F;
               vy[i] = vy[i] * 0.985F - 0.03F;
               vz[i] *= 0.985F;
            }
            default -> {
            }
         }
         if (k != SPLAT) {
            px[i] += vx[i];
            py[i] += vy[i];
            pz[i] += vz[i];
            if (k == DROP || k == HAIR) {
               double wx = px[i] + originX;
               double wy = py[i] + originY;
               double wz = pz[i] + originZ;
               BlockPos pos = BlockPos.containing(wx, wy, wz);
               BlockState state = level.getBlockState(pos);
               VoxelShape shape = state.getCollisionShape(level, pos);
               if (!shape.isEmpty() && wy - pos.getY() <= shape.max(net.minecraft.core.Direction.Axis.Y) + 0.001) {
                  if (k == HAIR) {
                     continue;
                  }
                  py[i] = oy[i] = (float)(pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y) + 0.012 - originY);
                  px[i] = ox[i];
                  pz[i] = oz[i];
                  kind[i] = SPLAT;
                  size[i] = size[i] * (2.2F + RANDOM.nextFloat());
                  life[i] = 100000.0F;
                  vx[i] = vy[i] = vz[i] = 0.0F;
               }
            }
         }
         if (w != i) {
            copy(i, w);
         }
         w++;
      }
      count = w;
   }

   private static void copy(int from, int to) {
      px[to] = px[from];
      py[to] = py[from];
      pz[to] = pz[from];
      ox[to] = ox[from];
      oy[to] = oy[from];
      oz[to] = oz[from];
      vx[to] = vx[from];
      vy[to] = vy[from];
      vz[to] = vz[from];
      age[to] = age[from];
      life[to] = life[from];
      size[to] = size[from];
      grow[to] = grow[from];
      spin[to] = spin[from];
      rgba[to] = rgba[from];
      kind[to] = kind[from];
   }

   // ------------------------------------------------------------------ rendering

   static void render(RenderLevelStageEvent event, KillCamReplay r) {
      if (shader == null || Minecraft.getInstance().level == null) {
         return;
      }
      Camera cam = event.getCamera();
      Vec3 c = cam.getPosition();
      float f = (float)Math.clamp(r.v - simTo, 0.0, 1.0);
      if (solidBytes == null) {
         // our own byte buffers: two meshes are built before either is uploaded, so they must not share the Tesselator's
         solidBytes = new com.mojang.blaze3d.vertex.ByteBufferBuilder(16384);
         softBytes = new com.mojang.blaze3d.vertex.ByteBufferBuilder(65536);
      }
      BufferBuilder solid = new BufferBuilder(solidBytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
      int solidQuads = 0;
      if (r.projectileVisible() && !r.bow) {
         solidQuads = bullet(solid, r, c);
      }
      MeshData solidMesh = solid.build();
      BufferBuilder soft = new BufferBuilder(softBytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
      int softQuads = 0;
      if (r.projectileVisible() && !r.bow) {
         softQuads += trail(soft, r, c, cam);
      }
      softQuads += particles(soft, c, cam, f);
      MeshData softMesh = soft.build();
      if (solidMesh == null && softMesh == null) {
         return;
      }
      int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
      boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
      boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
      boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
      boolean mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
      int srcRgb = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB);
      int dstRgb = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
      int srcA = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA);
      int dstA = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
      ShaderInstance previous = RenderSystem.getShader();
      try {
         RenderSystem.enableDepthTest();
         RenderSystem.depthFunc(GL11.GL_LEQUAL);
         RenderSystem.disableCull();
         if (solidMesh != null) {
            MeshData mesh = solidMesh;
            solidMesh = null;
            if (solidBuffer == null) {
               solidBuffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            }
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            solidBuffer.bind();
            solidBuffer.upload(mesh);
            solidBuffer.drawWithShader(event.getModelViewMatrix(), event.getProjectionMatrix(), shader);
         }
         if (softMesh != null) {
            MeshData mesh = softMesh;
            softMesh = null;
            if (softBuffer == null) {
               softBuffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            }
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.depthMask(false);
            softBuffer.bind();
            softBuffer.upload(mesh);
            softBuffer.drawWithShader(event.getModelViewMatrix(), event.getProjectionMatrix(), shader);
         }
      } catch (RuntimeException ex) {
         LogUtils.getLogger().warn("Frontier Hunts kill cam: effect draw failed", ex);
      } finally {
         if (solidMesh != null) {
            solidMesh.close();
         }
         if (softMesh != null) {
            softMesh.close();
         }
         VertexBuffer.unbind();
         RenderSystem.setShader(() -> previous);
         RenderSystem.depthFunc(depthFunc);
         RenderSystem.depthMask(mask);
         RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcA, dstA);
         if (!blend) {
            RenderSystem.disableBlend();
         } else {
            RenderSystem.enableBlend();
         }
         if (!depth) {
            RenderSystem.disableDepthTest();
         }
         if (cull) {
            RenderSystem.enableCull();
         }
      }
   }

   /** Hunting bullet (copper jacket, polymer tip, boat-tail), drawn 3x life size so it reads on screen. */
   private static int bullet(BufferBuilder b, KillCamReplay r, Vec3 cam) {
      Vec3 p = r.projectilePos();
      Vec3 d = r.projectileDir();
      Vec3 side = d.cross(new Vec3(0.0, 1.0, 0.0));
      if (side.lengthSqr() < 1.0E-6) {
         side = new Vec3(1.0, 0.0, 0.0);
      }
      side = side.normalize();
      Vec3 up = side.cross(d).normalize();
      float radius = 0.0118F;
      float len = 0.094F;
      float[][] profile = {
         {0.0F, 0.0F}, {0.0F, 0.74F}, {0.07F, 0.88F}, {0.15F, 1.0F}, {0.56F, 1.0F}, {0.66F, 0.94F}, {0.75F, 0.8F}, {0.83F, 0.6F}, {0.89F, 0.42F},
         {0.95F, 0.22F}, {1.0F, 0.0F}
      };
      int seg = 16;
      double spinAngle = r.v * 0.9 + r.total * 5.0;
      Vec3 sun = sunDir(r.level);
      Vec3 view = cam.subtract(p).normalize();
      float lightBase = lightAt(r.level, p);
      Vec3 rel = p.subtract(cam);
      int quads = 0;
      for (int ring = 0; ring < profile.length - 1; ring++) {
         float z0 = (profile[ring][0] - 0.5F) * len;
         float z1 = (profile[ring + 1][0] - 0.5F) * len;
         float r0 = profile[ring][1] * radius;
         float r1 = profile[ring + 1][1] * radius;
         boolean tip = profile[ring][0] >= 0.88F;
         boolean bearing = profile[ring][0] >= 0.15F && profile[ring][0] < 0.56F;
         boolean base = ring == 0;
         for (int k = 0; k < seg; k++) {
            double a0 = Math.PI * 2.0 * k / seg + spinAngle;
            double a1 = Math.PI * 2.0 * (k + 1) / seg + spinAngle;
            float cr;
            float cg;
            float cb;
            if (tip) {
               cr = 0.62F;
               cg = 0.07F;
               cb = 0.05F;
            } else if (base) {
               cr = 0.5F;
               cg = 0.3F;
               cb = 0.17F;
            } else {
               cr = 0.8F;
               cg = 0.49F;
               cb = 0.26F;
               if (bearing && k % 4 == 0) {
                  // rifling engraving: turns with the spin, so the slow rotation is plain to see
                  cr *= 0.66F;
                  cg *= 0.62F;
                  cb *= 0.6F;
               }
            }
            Vec3 n0 = side.scale(Math.cos(a0)).add(up.scale(Math.sin(a0)));
            Vec3 n1 = side.scale(Math.cos(a1)).add(up.scale(Math.sin(a1)));
            float slope = (r0 - r1) / Math.max(1.0E-4F, z1 - z0);
            Vec3 m0 = n0.add(d.scale(slope)).normalize();
            Vec3 m1 = n1.add(d.scale(slope)).normalize();
            vertex(b, rel.add(d.scale(z0)).add(n0.scale(r0)), shade(m0, sun, view, lightBase, cr, cg, cb, tip));
            vertex(b, rel.add(d.scale(z0)).add(n1.scale(r0)), shade(m1, sun, view, lightBase, cr, cg, cb, tip));
            vertex(b, rel.add(d.scale(z1)).add(n1.scale(r1)), shade(m1, sun, view, lightBase, cr, cg, cb, tip));
            vertex(b, rel.add(d.scale(z1)).add(n0.scale(r1)), shade(m0, sun, view, lightBase, cr, cg, cb, tip));
            quads++;
         }
      }
      return quads;
   }

   private static int shade(Vec3 n, Vec3 sun, Vec3 view, float lightBase, float r, float g, float b, boolean matte) {
      double diffuse = Math.max(0.0, n.dot(sun));
      Vec3 h = sun.add(view).normalize();
      double spec = Math.pow(Math.max(0.0, n.dot(h)), matte ? 12.0 : 40.0) * (matte ? 0.25 : 0.85);
      double rim = Math.pow(1.0 - Math.abs(n.dot(view)), 3.0) * 0.18;
      double k = (0.28 + 0.72 * diffuse) * lightBase;
      float cr = (float)Math.clamp(r * k + (spec + rim) * lightBase, 0.0, 1.0);
      float cg = (float)Math.clamp(g * k + (spec * 0.85 + rim) * lightBase, 0.0, 1.0);
      float cb = (float)Math.clamp(b * k + (spec * 0.7 + rim) * lightBase, 0.0, 1.0);
      return 0xFF000000 | (int)(cr * 255.0F) << 16 | (int)(cg * 255.0F) << 8 | (int)(cb * 255.0F);
   }

   private static Vec3 sunDir(ClientLevel level) {
      float a = level.getSunAngle(1.0F);
      Vec3 s = new Vec3(-Math.sin(a), Math.cos(a), 0.25);
      if (s.y < 0.05) {
         s = new Vec3(0.35, 0.8, 0.45);
      }
      return s.normalize();
   }

   private static void vertex(BufferBuilder b, Vec3 p, int argb) {
      // UV (0, 30): the fx shader draws these texels solid
      b.addVertex((float)p.x, (float)p.y, (float)p.z).setUv(0.0F, 30.0F).setColor(argb);
   }

   /** Swirling vapour behind the bullet, thinning out down the path. */
   private static int trail(BufferBuilder b, KillCamReplay r, Vec3 cam, Camera camera) {
      double s = r.sOf(r.u);
      Vec3 d = r.projectileDir();
      Vector3f left = camera.getLeftVector();
      Vector3f upv = camera.getUpVector();
      Vec3 side = d.cross(new Vec3(0.0, 1.0, 0.0));
      if (side.lengthSqr() < 1.0E-6) {
         side = new Vec3(1.0, 0.0, 0.0);
      }
      side = side.normalize();
      Vec3 up = side.cross(d).normalize();
      Vec3 swap = r.projectilePos().subtract(r.path.at(s));
      double phase = r.v * 0.9 + r.total * 5.0;
      float lb = lightAt(r.level, r.projectilePos());
      int quads = 0;
      int n = 30;
      for (int k = 0; k < n; k++) {
         double back = 0.05 + k * 0.055;
         double sb = r.path.back(s, back);
         if (sb <= 0.0) {
            break;
         }
         double fade = Math.pow(1.0 - (double)k / n, 1.6);
         double swirl = phase - back * 9.0;
         double amp = 0.006 * Math.min(1.0, back * 4.0);
         Vec3 p = r.path.at(sb).add(swap).add(side.scale(Math.cos(swirl) * amp)).add(up.scale(Math.sin(swirl) * amp)).subtract(cam);
         float size = (float)(0.011 + back * 0.022);
         float a = (float)(0.15 * fade);
         float g = Math.clamp(0.93F * lb, 0.0F, 1.0F);
         billboard(b, p, left, upv, size, size, 0.0F, g, g, Math.min(1.0F, g * 1.02F), a, false);
         quads++;
      }
      return quads;
   }

   private static int particles(BufferBuilder b, Vec3 cam, Camera camera, float f) {
      Vector3f left = camera.getLeftVector();
      Vector3f upv = camera.getUpVector();
      Vec3 look = new Vec3(camera.getLookVector());
      int quads = 0;
      for (int i = 0; i < count; i++) {
         byte k = kind[i];
         float t = k == SPLAT ? 0.0F : Math.clamp((age[i] + f) / life[i], 0.0F, 1.0F);
         float x = Mth.lerp(f, ox[i], px[i]);
         float y = Mth.lerp(f, oy[i], py[i]);
         float z = Mth.lerp(f, oz[i], pz[i]);
         Vec3 p = new Vec3(x + originX - cam.x, y + originY - cam.y, z + originZ - cam.z);
         int c = rgba[i];
         float cr = (c >> 16 & 255) / 255.0F;
         float cg = (c >> 8 & 255) / 255.0F;
         float cb = (c & 255) / 255.0F;
         float a = (c >>> 24) / 255.0F;
         switch (k) {
            case MIST, SMOKE -> {
               float s = size[i] + (k == MIST ? grow[i] * f : 0.0F);
               float fade = k == MIST ? (1.0F - t) * (1.0F - t) : (1.0F - t) * Math.min(1.0F, t * 6.0F);
               billboard(b, p, left, upv, s, s, spin[i], cr, cg, cb, a * fade, false);
            }
            case HAIR -> billboard(b, p, left, upv, size[i] * 1.6F, size[i] * 0.35F, spin[i] + f * 0.35F, cr, cg, cb, a * (1.0F - t * t), true);
            case DROP -> {
               Vec3 v = new Vec3(vx[i], vy[i], vz[i]);
               Vec3 axis = v.subtract(look.scale(v.dot(look)));
               double speed = axis.length();
               if (speed < 1.0E-5) {
                  billboard(b, p, left, upv, size[i], size[i], 0.0F, cr, cg, cb, a, true);
               } else {
                  axis = axis.scale(1.0 / speed);
                  Vec3 perp = axis.cross(look).normalize();
                  double half = size[i] + Math.min(0.06, speed * 0.55);
                  quad(b, p, axis.scale(half), perp.scale(size[i] * 0.9), cr, cg, cb, a, true);
               }
            }
            case SPLAT -> {
               double s = size[i];
               quad(b, p, new Vec3(s, 0.0, 0.0), new Vec3(0.0, 0.0, s * 0.8), cr * 0.7F, cg * 0.7F, cb * 0.7F, 0.92F, true);
            }
            default -> {
            }
         }
         quads++;
      }
      return quads;
   }

   private static void billboard(BufferBuilder b, Vec3 p, Vector3f left, Vector3f up, float sx, float sy, float rot, float r, float g, float bl, float a, boolean hard) {
      float cos = Mth.cos(rot);
      float sin = Mth.sin(rot);
      Vec3 ax = new Vec3(left.x() * cos + up.x() * sin, left.y() * cos + up.y() * sin, left.z() * cos + up.z() * sin).scale(sx);
      Vec3 ay = new Vec3(-left.x() * sin + up.x() * cos, -left.y() * sin + up.y() * cos, -left.z() * sin + up.z() * cos).scale(sy);
      quad(b, p, ax, ay, r, g, bl, a, hard);
   }

   private static void quad(BufferBuilder b, Vec3 p, Vec3 ax, Vec3 ay, float r, float g, float bl, float a, boolean hard) {
      if (a <= 0.003F) {
         a = 0.0F;
      }
      float o = hard ? 10.0F : 0.0F;
      int col = (int)(Math.clamp(a, 0.0F, 1.0F) * 255.0F) << 24 | (int)(Math.clamp(r, 0.0F, 1.0F) * 255.0F) << 16
         | (int)(Math.clamp(g, 0.0F, 1.0F) * 255.0F) << 8 | (int)(Math.clamp(bl, 0.0F, 1.0F) * 255.0F);
      b.addVertex((float)(p.x - ax.x - ay.x), (float)(p.y - ax.y - ay.y), (float)(p.z - ax.z - ay.z)).setUv(-1.0F, -1.0F + o).setColor(col);
      b.addVertex((float)(p.x + ax.x - ay.x), (float)(p.y + ax.y - ay.y), (float)(p.z + ax.z - ay.z)).setUv(1.0F, -1.0F + o).setColor(col);
      b.addVertex((float)(p.x + ax.x + ay.x), (float)(p.y + ax.y + ay.y), (float)(p.z + ax.z + ay.z)).setUv(1.0F, 1.0F + o).setColor(col);
      b.addVertex((float)(p.x - ax.x + ay.x), (float)(p.y - ax.y + ay.y), (float)(p.z - ax.z + ay.z)).setUv(-1.0F, 1.0F + o).setColor(col);
   }
}
