package com.formaworks.frontierhunts.guns.client;

import com.formaworks.frontierhunts.guns.WaterImpact;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.joml.Vector3f;

/**
 * [guns3] The water impact, redrawn with our own art (tools/gen_water_impact_art.py) and our own particle shapes:
 *
 * <ol>
 * <li>a crown of droplets thrown up and out, drawn as streaks stretched along their flight (they read as water, not
 * as dots), with white-water clumps around the rim,</li>
 * <li>a jet: a column of fast streaks and foam shooting straight up, whose height follows the round (pistol ~1 m, .30
 * rifles ~3 m, the .308 ~4 m), then falling back and pocking the surface,</li>
 * <li>foam rings laid flat on the water, spreading out at different speeds, and a foam patch that lingers,</li>
 * <li>a faint mist that hangs and drifts.</li>
 * </ol>
 * A shotgun charge is many spouts at once plus one wide blast (sheet of spray, big rings, broad mist), so it is the
 * biggest splash of all. Everything is particles (works with any shader pack), scaled down with the Particles setting.
 * [gear20] Tinted with the water's own colour (no more bright white), thinner foam, no impact flash, no bubbles.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = Bus.MOD)
public final class WaterImpactClient {
   private static SpriteSet drops, foams, mists, rings;
   /** client side of the blast grouping (see WaterImpact#splash) */
   private static long blastTick = Long.MIN_VALUE;
   private static Vec3 blastAt = Vec3.ZERO;
   /** [gear20] colours for the splash being drawn: spray, white water, mist - grey water mixed with the biome's water */
   private static float sprayR = 0.62F, sprayG = 0.7F, sprayB = 0.76F;
   private static float foamR = 0.74F, foamG = 0.79F, foamB = 0.82F;

   private WaterImpactClient() {
   }

   @SubscribeEvent
   public static void setup(FMLClientSetupEvent e) {
      WaterImpact.receiver = WaterImpactClient::splash;
   }

   @SubscribeEvent
   public static void particles(RegisterParticleProvidersEvent e) {
      e.registerSpriteSet(WaterImpact.SPRAY.get(), s -> {
         drops = s;
         return new Provider(s, 0);
      });
      e.registerSpriteSet(WaterImpact.FOAM.get(), s -> {
         foams = s;
         return new Provider(s, 1);
      });
      e.registerSpriteSet(WaterImpact.MIST.get(), s -> {
         mists = s;
         return new Provider(s, 2);
      });
      e.registerSpriteSet(WaterImpact.RING.get(), s -> {
         rings = s;
         return new Provider(s, 3);
      });
   }

   // ------------------------------------------------------------------ the splash

   private static void splash(WaterImpact.Splash s) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null || mc.player == null || drops == null || foams == null || mists == null || rings == null
         || !Double.isFinite(s.x() + s.y() + s.z()) || mc.player.distanceToSqr(s.x(), s.y(), s.z()) > 160.0 * 160.0) {
         return;
      }
      ParticleStatus status = mc.options.particles().get();
      float density = status == ParticleStatus.MINIMAL ? 0.3F : status == ParticleStatus.DECREASED ? 0.6F : 1.0F;
      RandomSource r = level.random;
      double x = s.x(), z = s.z();
      BlockPos bp = BlockPos.containing(s.x(), s.y() - 0.01, s.z());
      double y = level.getFluidState(bp).is(FluidTags.WATER) ? bp.getY() + level.getFluidState(bp).getHeight(level, bp) : s.y();
      tint(level, bp);
      if (s.pellet()) {
         long now = level.getGameTime();
         boolean first = blastTick != now || blastAt.distanceToSqr(x, y, z) > 100.0;
         if (first) {
            blastTick = now;
            blastAt = new Vec3(x, y, z);
            blast(level, r, x, y, z, density);
         }
         spout(level, r, x, y, z, s, Math.max(0.35F, s.energy()), density * 0.55F);
         return;
      }
      spout(level, r, x, y, z, s, s.energy(), density);
   }

   /** One round's splash. {@code e}: about 0.5 (pistol, pellet) .. 1.1 (.30-30) .. 1.6+ (.308). */
   private static void spout(ClientLevel level, RandomSource r, double x, double y, double z, WaterImpact.Splash s, float e, float density) {
      e = Mth.clamp(e, 0.2F, 2.0F);
      // 2. the crown: streaks thrown up and out, a little against the shot, with foam on the rim
      int crown = n(16 + 38 * e, density);
      double reach = 0.11 + 0.08 * e;
      for (int i = 0; i < crown; i++) {
         double a = (i + r.nextDouble() * 0.7) / crown * Math.PI * 2.0;
         double out = reach * (0.6 + r.nextDouble() * 0.7);
         double up = 0.16 + 0.11 * e + r.nextDouble() * 0.08;
         add(new Streak(level, x + Math.cos(a) * 0.12 * e, y + 0.04, z + Math.sin(a) * 0.12 * e,
            Math.cos(a) * out - s.dx() * 0.06, up, Math.sin(a) * out - s.dz() * 0.06, drops, 0.05F + 0.035F * e));
      }
      for (int i = 0; i < n(3 + 5 * e, density); i++) {
         double a = r.nextDouble() * Math.PI * 2.0;
         double rr = 0.15 + 0.2 * e;
         add(new Blob(level, x + Math.cos(a) * rr, y + 0.05, z + Math.sin(a) * rr,
            Math.cos(a) * 0.05 * e, 0.07 + 0.06 * e * r.nextDouble(), Math.sin(a) * 0.05 * e,
            foams, 0.07F + 0.06F * e, 1.3F, 10 + r.nextInt(8), 0.9F, false));
      }
      // 3. the jet: fast streaks and white water straight up, its height set by the round
      double height = 0.7 + 2.0 * e;
      double vTop = Math.sqrt(2.0 * 0.04 * height);
      for (int i = 0; i < n(10 + 30 * e, density); i++) {
         double f = Math.pow(r.nextDouble(), 0.6);
         double j = 0.02 + 0.025 * e;
         add(new Streak(level, x + r.nextGaussian() * 0.05 * e, y + 0.05, z + r.nextGaussian() * 0.05 * e,
            r.nextGaussian() * j, vTop * (0.45 + 0.55 * f), r.nextGaussian() * j, drops, 0.05F + 0.04F * e));
      }
      for (int i = 0; i < n(4 + 8 * e, density); i++) {
         double f = r.nextDouble();
         add(new Blob(level, x + r.nextGaussian() * 0.04, y + 0.1, z + r.nextGaussian() * 0.04,
            r.nextGaussian() * 0.008, vTop * (0.3 + 0.68 * f), r.nextGaussian() * 0.008,
            foams, 0.06F + 0.05F * e, 1.4F, 14 + r.nextInt(12), 0.85F, false));
      }
      // 4. rings on the surface and a foam patch that lingers
      int ringCount = e > 1.0F ? 3 : 2;
      for (int i = 0; i < ringCount; i++) {
         add(new Ring(level, x, y + 0.02 + i * 0.004, z, rings, 0.3F, (0.9F + 1.2F * e) * (1.0F - 0.25F * i), 22 + 10 * i, 0.55F - 0.12F * i));
      }
      add(new Ring(level, x, y + 0.015, z, foams, 0.2F + 0.15F * e, 0.5F + 0.4F * e, 50 + r.nextInt(20), 0.45F));
      // 5. a faint mist
      for (int i = 0; i < n(1 + 4 * e, density); i++) {
         double a = r.nextDouble() * Math.PI * 2.0;
         double rr = 0.2 + r.nextDouble() * 0.6 * e;
         add(new Blob(level, x + Math.cos(a) * rr, y + 0.2 + r.nextDouble() * height * 0.7, z + Math.sin(a) * rr,
            Math.cos(a) * 0.012, 0.004 + 0.01 * r.nextDouble(), Math.sin(a) * 0.012,
            mists, 0.35F + 0.35F * e, 1.9F, 30 + r.nextInt(30), -0.01F, true));
      }
   }

   /** The shotgun blast on top of its pellets' spouts: a wide sheet of spray, big rings, broad mist. */
   private static void blast(ClientLevel level, RandomSource r, double x, double y, double z, float density) {
      for (int i = 0; i < n(70, density); i++) {
         double a = r.nextDouble() * Math.PI * 2.0;
         double out = 0.12 + r.nextDouble() * 0.22;
         add(new Streak(level, x + Math.cos(a) * 0.5, y + 0.05, z + Math.sin(a) * 0.5, Math.cos(a) * out, 0.22 + r.nextDouble() * 0.22,
            Math.sin(a) * out, drops, 0.08F));
      }
      for (int i = 0; i < n(12, density); i++) {
         double a = r.nextDouble() * Math.PI * 2.0;
         double rr = r.nextDouble() * 1.2;
         add(new Blob(level, x + Math.cos(a) * rr, y + 0.08, z + Math.sin(a) * rr, Math.cos(a) * 0.06, 0.1 + r.nextDouble() * 0.16,
            Math.sin(a) * 0.06, foams, 0.25F + r.nextFloat() * 0.2F, 1.5F, 16 + r.nextInt(12), 0.8F, false));
      }
      for (int i = 0; i < 3; i++) {
         add(new Ring(level, x, y + 0.025 + i * 0.004, z, rings, 0.5F, 3.2F - 0.7F * i, 30 + 8 * i, 0.6F - 0.12F * i));
      }
      add(new Ring(level, x, y + 0.016, z, foams, 0.6F, 1.6F, 60, 0.45F));
      for (int i = 0; i < n(8, density); i++) {
         double a = r.nextDouble() * Math.PI * 2.0;
         double rr = r.nextDouble() * 1.4;
         add(new Blob(level, x + Math.cos(a) * rr, y + 0.2 + r.nextDouble() * 0.8, z + Math.sin(a) * rr, Math.cos(a) * 0.02, 0.01 + 0.015 * r.nextDouble(),
            Math.sin(a) * 0.02, mists, 1.0F + r.nextFloat() * 0.6F, 1.7F, 50 + r.nextInt(30), -0.02F, true));
      }
   }

   /** Grey water mixed with this biome's water colour, so the splash reads as water, not as white paint. */
   private static void tint(ClientLevel level, BlockPos at) {
      int c = net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(level, at);
      float wr = (c >> 16 & 255) / 255.0F, wg = (c >> 8 & 255) / 255.0F, wb = (c & 255) / 255.0F;
      sprayR = Mth.lerp(0.32F, 0.66F, wr);
      sprayG = Mth.lerp(0.32F, 0.72F, wg);
      sprayB = Mth.lerp(0.32F, 0.76F, wb);
      foamR = Mth.lerp(0.18F, 0.76F, wr);
      foamG = Mth.lerp(0.18F, 0.8F, wg);
      foamB = Mth.lerp(0.18F, 0.82F, wb);
   }

   private static int n(float count, float density) {
      return Math.max(1, Math.round(count * density));
   }

   private static void add(Particle p) {
      Minecraft.getInstance().particleEngine.add(p);
   }

   // ------------------------------------------------------------------ particles

   private record Provider(SpriteSet sprites, int kind) implements ParticleProvider<SimpleParticleType> {
      @Override
      public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
         return switch (this.kind) {
            case 0 -> new Streak(level, x, y, z, dx, dy, dz, this.sprites, 0.08F);
            case 3 -> new Ring(level, x, y, z, this.sprites, 0.3F, 1.5F, 24, 0.8F);
            default -> new Blob(level, x, y, z, dx, dy, dz, this.sprites, 0.4F, 1.5F, 30, this.kind == 2 ? -0.02F : 0.8F, this.kind == 2);
         };
      }
   }

   /**
    * A drop of water, drawn as a streak along its velocity (longer the faster it flies). A drop that falls back into
    * water pocks the surface with a tiny ring now and then.
    */
   private static final class Streak extends TextureSheetParticle {
      private final float width;

      Streak(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, float width) {
         super(level, x, y, z, 0.0, 0.0, 0.0);
         this.xd = dx;
         this.yd = dy;
         this.zd = dz;
         this.pickSprite(sprites);
         this.width = width * (0.7F + this.random.nextFloat() * 0.6F);
         this.quadSize = this.width;
         this.gravity = 1.0F;
         this.friction = 0.985F;
         this.lifetime = 50 + this.random.nextInt(25);
         this.hasPhysics = true;
         this.rCol = sprayR;
         this.gCol = sprayG;
         this.bCol = sprayB;
         this.alpha = 0.62F;
      }

      @Override
      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
      }

      @Override
      public void tick() {
         super.tick();
         if (this.removed) {
            return;
         }
         if (this.yd < 0.0 && this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z)).is(FluidTags.WATER)) {
            if (rings != null && this.random.nextFloat() < 0.12F) {
               BlockPos p = BlockPos.containing(this.x, this.y, this.z);
               double top = p.getY() + this.level.getFluidState(p).getHeight(this.level, p);
               Minecraft.getInstance().particleEngine.add(new Ring(this.level, this.x, top + 0.012, this.z, rings, 0.06F, 0.35F, 12, 0.6F));
            }
            this.remove();
            return;
         }
         if (this.onGround) {
            this.lifetime = Math.min(this.lifetime, this.age + 3);
         }
         this.alpha = 0.62F * Mth.clamp((1.0F - (float)this.age / this.lifetime) * 4.0F, 0.0F, 1.0F);
      }

      @Override
      public void render(VertexConsumer buffer, Camera camera, float partial) {
         Vec3 cam = camera.getPosition();
         float px = (float)(Mth.lerp(partial, this.xo, this.x) - cam.x);
         float py = (float)(Mth.lerp(partial, this.yo, this.y) - cam.y);
         float pz = (float)(Mth.lerp(partial, this.zo, this.z) - cam.z);
         Vector3f v = new Vector3f((float)this.xd, (float)this.yd, (float)this.zd);
         float speed = v.length();
         if (speed < 0.02F) {
            super.render(buffer, camera, partial);
            return;
         }
         v.div(speed);
         Vector3f toCam = new Vector3f(-px, -py, -pz);
         Vector3f side = v.cross(toCam, new Vector3f());
         if (side.lengthSquared() < 1.0E-8F) {
            super.render(buffer, camera, partial);
            return;
         }
         side.normalize().mul(this.width);
         float half = this.width + speed * 1.6F;
         Vector3f along = new Vector3f(v).mul(half);
         int light = this.getLightColor(partial);
         float u0 = this.getU0(), u1 = this.getU1(), v0 = this.getV0(), v1 = this.getV1();
         vertex(buffer, px - along.x - side.x, py - along.y - side.y, pz - along.z - side.z, u1, v1, light);
         vertex(buffer, px + along.x - side.x, py + along.y - side.y, pz + along.z - side.z, u1, v0, light);
         vertex(buffer, px + along.x + side.x, py + along.y + side.y, pz + along.z + side.z, u0, v0, light);
         vertex(buffer, px - along.x + side.x, py - along.y + side.y, pz - along.z + side.z, u0, v1, light);
      }

      private void vertex(VertexConsumer b, float x, float y, float z, float u, float v, int light) {
         b.addVertex(x, y, z).setUv(u, v).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
      }
   }

   /** A clump of white water (rises, grows, falls back and fades) or, with {@code mist}, a drifting cloud of spray. */
   private static final class Blob extends TextureSheetParticle {
      private final float startSize;
      private final float growTo;
      private final boolean mist;
      private final float baseAlpha;

      Blob(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, float size, float grow, int life,
         float gravity, boolean mist) {
         super(level, x, y, z, 0.0, 0.0, 0.0);
         this.xd = dx;
         this.yd = dy;
         this.zd = dz;
         this.pickSprite(sprites);
         this.startSize = size * (0.8F + this.random.nextFloat() * 0.4F);
         this.quadSize = this.startSize;
         this.growTo = grow;
         this.lifetime = life;
         this.gravity = gravity;
         this.mist = mist;
         this.friction = mist ? 0.92F : 0.95F;
         this.hasPhysics = !mist;
         this.roll = this.random.nextFloat() * Mth.TWO_PI;
         this.oRoll = this.roll;
         float shade = mist ? 1.0F : 0.94F + this.random.nextFloat() * 0.06F;
         this.rCol = (mist ? sprayR : foamR) * shade;
         this.gCol = (mist ? sprayG : foamG) * shade;
         this.bCol = (mist ? sprayB : foamB) * shade;
         this.baseAlpha = mist ? 0.12F : 0.7F;
         this.alpha = this.baseAlpha;
      }

      @Override
      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
      }

      @Override
      public void tick() {
         super.tick();
         if (this.removed) {
            return;
         }
         float t = (float)this.age / this.lifetime;
         this.quadSize = this.startSize * (1.0F + (this.growTo - 1.0F) * (1.0F - (1.0F - t) * (1.0F - t)));
         this.oRoll = this.roll;
         this.roll += this.mist ? 0.01F : 0.04F;
         if (!this.mist && this.yd < 0.0 && this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z)).is(FluidTags.WATER)) {
            this.yd = 0.0;
            this.gravity = 0.0F;
            this.xd *= 0.5;
            this.zd *= 0.5;
         }
         this.alpha = this.baseAlpha * (this.mist ? Mth.clamp((1.0F - t) * 1.4F, 0.0F, 1.0F) * Mth.clamp(t * 6.0F, 0.0F, 1.0F)
            : Mth.clamp((1.0F - t) * 2.2F, 0.0F, 1.0F));
      }
   }

   /** Laid flat on the water: a foam ring spreading out (or, with the foam art, a patch of foam slowly fading). */
   private static final class Ring extends TextureSheetParticle {
      private final float from;
      private final float to;
      private final float baseAlpha;

      Ring(ClientLevel level, double x, double y, double z, SpriteSet sprites, float from, float to, int life, float alpha) {
         super(level, x, y, z, 0.0, 0.0, 0.0);
         this.xd = 0.0;
         this.yd = 0.0;
         this.zd = 0.0;
         this.pickSprite(sprites);
         this.from = from;
         this.to = to;
         this.lifetime = life;
         this.gravity = 0.0F;
         this.hasPhysics = false;
         this.baseAlpha = alpha;
         this.alpha = alpha;
         this.rCol = foamR;
         this.gCol = foamG;
         this.bCol = foamB;
         this.quadSize = from;
         this.roll = this.random.nextFloat() * Mth.TWO_PI;
      }

      @Override
      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
      }

      @Override
      public void tick() {
         this.xo = this.x;
         this.yo = this.y;
         this.zo = this.z;
         if (this.age++ >= this.lifetime) {
            this.remove();
            return;
         }
         float t = (float)this.age / this.lifetime;
         this.quadSize = this.from + (this.to - this.from) * (1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t));
         this.alpha = this.baseAlpha * (1.0F - t) * (1.0F - t * 0.3F);
      }

      @Override
      public void render(VertexConsumer buffer, Camera camera, float partial) {
         Vec3 cam = camera.getPosition();
         float px = (float)(this.x - cam.x);
         float py = (float)(this.y - cam.y);
         float pz = (float)(this.z - cam.z);
         float s = this.getQuadSize(partial);
         float c = Mth.cos(this.roll) * s, n = Mth.sin(this.roll) * s;
         int light = this.getLightColor(partial);
         float u0 = this.getU0(), u1 = this.getU1(), v0 = this.getV0(), v1 = this.getV1();
         // both windings, so the ring shows from above and (under water) from below
         float[][] q = {{-c + n, -n - c}, {-c - n, -n + c}, {c - n, n + c}, {c + n, n - c}};
         float[][] uv = {{u0, v1}, {u0, v0}, {u1, v0}, {u1, v1}};
         for (int i = 0; i < 4; i++) {
            buffer.addVertex(px + q[i][0], py, pz + q[i][1]).setUv(uv[i][0], uv[i][1]).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
         }
         for (int i = 3; i >= 0; i--) {
            buffer.addVertex(px + q[i][0], py, pz + q[i][1]).setUv(uv[i][0], uv[i][1]).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
         }
      }
   }
}
