package com.formaworks.frontierhunts.wingshot.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import com.formaworks.frontierhunts.wingshot.BirdNet;
import com.formaworks.frontierhunts.wingshot.WingshotConfig;
import com.formaworks.frontierhunts.wingshot.WingshotContent;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/**
 * [wingshot] The bird hit effect: a pooled particle system (fixed struct-of-arrays, no allocation per particle, a hard
 * cap) drawn through Minecraft's own particle pipeline as two "cloud" particles (one cutout, one translucent), so it
 * renders and lights like any particle with and without Iris shader packs.
 *
 * <p>Elements: contour feathers in the species' colours and a few long flight feathers (3-D tumbling quads that
 * flutter down like falling leaves, some hanging in the air a moment first, settling flat on the ground or floating on
 * water), puffs of down drifting off on the wind, blood mist sprayed out along the shot, blood droplets that fly
 * ballistically and leave spatter on whatever they hit (ground, tree trunks, blocks), a pink bloom where they hit
 * water. Everything runs on the wing-shot moment's clock (slow motion when it plays).
 */
public final class BirdFx {
   static final int FEATHER = 0, FLIGHT = 1, DOWN = 2, DROP = 3, SPLAT = 4, MIST = 5, BLOOM = 6;
   static final int CAP = 1600;
   // sprite indices (particles/wingshot_fx.json)
   static final int S_DUCK = 0, S_DUCK_FLIGHT = 6, S_GROUSE = 9, S_GROUSE_FLIGHT = 15, S_DOWN = 18, S_DROP = 21, S_SPLAT = 22, S_MIST = 24;
   static final int SPRITES = 27;

   static SpriteSet spriteSet;
   private static TextureAtlasSprite[] sprites;

   // ---- the pool
   private static final byte[] kind = new byte[CAP], spr = new byte[CAP], flags = new byte[CAP];
   private static final float[] x = new float[CAP], y = new float[CAP], z = new float[CAP], ox = new float[CAP], oy = new float[CAP], oz = new float[CAP];
   private static final float[] vx = new float[CAP], vy = new float[CAP], vz = new float[CAP];
   private static final float[] ra = new float[CAP], rb = new float[CAP], rc = new float[CAP], ora = new float[CAP], orb = new float[CAP], orc = new float[CAP];
   private static final float[] wa = new float[CAP], wb = new float[CAP], wc = new float[CAP];
   private static final float[] size = new float[CAP], grow = new float[CAP], cr = new float[CAP], cg = new float[CAP], cb = new float[CAP], alpha = new float[CAP];
   private static final float[] age = new float[CAP], life = new float[CAP], hang = new float[CAP], seed = new float[CAP];
   private static final float[] nx = new float[CAP], ny = new float[CAP], nz = new float[CAP];
   private static final int[] light = new int[CAP];
   private static int count;
   /** wind (blocks/tick) for drifting down and feathers */
   private static float windX, windZ;
   private static Cloud opaque, translucent;
   private static int idle;
   private static final java.util.Random R = new java.util.Random();
   private static final BlockPos.MutableBlockPos MP = new BlockPos.MutableBlockPos();

   static final byte SETTLED = 1, WATER = 2, LIT = 4;

   private BirdFx() {
   }

   private static boolean ready() {
      if (sprites == null && spriteSet != null) {
         try {
            TextureAtlasSprite[] s = new TextureAtlasSprite[SPRITES];
            for (int i = 0; i < SPRITES; i++) {
               s[i] = spriteSet.get(i, SPRITES - 1);
            }
            sprites = s;
         } catch (RuntimeException e) {
            return false;
         }
      }
      return sprites != null;
   }

   /** resource reload: re-read the sprites */
   static void resetSprites() {
      sprites = null;
   }

   static void clear() {
      count = 0;
      if (opaque != null) {
         opaque.remove();
      }
      if (translucent != null) {
         translucent.remove();
      }
      opaque = null;
      translucent = null;
   }

   /** Cosmetic budget: the bird hit setting, Effects quality and Minecraft's Particles option. */
   static float budget() {
      WingshotConfig.HitEffects fx = WingshotConfig.hitEffects();
      if (fx == WingshotConfig.HitEffects.OFF) {
         return 0.0F;
      }
      float q = fx == WingshotConfig.HitEffects.GRAPHIC ? 1.0F : 0.45F;
      try {
         HuntConfig.Quality v = HuntConfig.QUALITY.get();
         q *= v == HuntConfig.Quality.PERFORMANCE ? 0.55F : 1.0F;
      } catch (Throwable t) {
      }
      ParticleStatus ps = Minecraft.getInstance().options.particles().get();
      if (ps == ParticleStatus.DECREASED) {
         q *= 0.6F;
      } else if (ps == ParticleStatus.MINIMAL) {
         q *= 0.25F;
      }
      return q;
   }

   private static boolean ensure() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || !ready()) {
         return false;
      }
      if (opaque == null || !opaque.isAlive() || opaque.lvl() != mc.level) {
         if (opaque != null) {
            count = 0;
         }
         opaque = new Cloud(mc.level, false);
         mc.particleEngine.add(opaque);
      }
      if (translucent == null || !translucent.isAlive() || translucent.lvl() != mc.level) {
         translucent = new Cloud(mc.level, true);
         mc.particleEngine.add(translucent);
      }
      idle = 0;
      return true;
   }

   private static int add(int k, int sprite, double px, double py, double pz, double dx, double dy, double dz, float sz, float lifeTicks) {
      if (count >= CAP) {
         // full: recycle the oldest settled feather or finished element (start of the pool)
         int victim = -1;
         for (int i = 0; i < count && victim < 0; i++) {
            if (kind[i] == SPLAT || (flags[i] & SETTLED) != 0) {
               victim = i;
            }
         }
         if (victim < 0) {
            return -1;
         }
         remove(victim);
      }
      int i = count++;
      kind[i] = (byte)k;
      spr[i] = (byte)sprite;
      flags[i] = 0;
      x[i] = ox[i] = (float)px;
      y[i] = oy[i] = (float)py;
      z[i] = oz[i] = (float)pz;
      vx[i] = (float)dx;
      vy[i] = (float)dy;
      vz[i] = (float)dz;
      ra[i] = ora[i] = R.nextFloat() * Mth.TWO_PI;
      rb[i] = orb[i] = R.nextFloat() * Mth.TWO_PI;
      rc[i] = orc[i] = R.nextFloat() * Mth.TWO_PI;
      wa[i] = (R.nextFloat() - 0.5F) * 0.6F;
      wb[i] = (R.nextFloat() - 0.5F) * 0.6F;
      wc[i] = (R.nextFloat() - 0.5F) * 0.6F;
      size[i] = sz;
      grow[i] = 0.0F;
      cr[i] = cg[i] = cb[i] = 0.9F + R.nextFloat() * 0.12F;
      alpha[i] = 1.0F;
      age[i] = 0.0F;
      life[i] = lifeTicks;
      hang[i] = 0.0F;
      seed[i] = R.nextFloat() * 100.0F;
      light[i] = -1;
      return i;
   }

   private static void remove(int i) {
      int j = --count;
      if (i == j) {
         return;
      }
      kind[i] = kind[j];
      spr[i] = spr[j];
      flags[i] = flags[j];
      x[i] = x[j];
      y[i] = y[j];
      z[i] = z[j];
      ox[i] = ox[j];
      oy[i] = oy[j];
      oz[i] = oz[j];
      vx[i] = vx[j];
      vy[i] = vy[j];
      vz[i] = vz[j];
      ra[i] = ra[j];
      rb[i] = rb[j];
      rc[i] = rc[j];
      ora[i] = ora[j];
      orb[i] = orb[j];
      orc[i] = orc[j];
      wa[i] = wa[j];
      wb[i] = wb[j];
      wc[i] = wc[j];
      size[i] = size[j];
      grow[i] = grow[j];
      cr[i] = cr[j];
      cg[i] = cg[j];
      cb[i] = cb[j];
      alpha[i] = alpha[j];
      age[i] = age[j];
      life[i] = life[j];
      hang[i] = hang[j];
      seed[i] = seed[j];
      nx[i] = nx[j];
      ny[i] = ny[j];
      nz[i] = nz[j];
      light[i] = light[j];
   }

   // ================================================================ spawning

   private static final float[] DUCK_W = {2, 3, 2, 2, 0.7F, 2}, GROUSE_W = {3, 2, 2, 2, 0.6F, 1.5F};

   private static int pick(float[] w) {
      float sum = 0;
      for (float f : w) {
         sum += f;
      }
      float r = R.nextFloat() * sum;
      for (int i = 0; i < w.length; i++) {
         r -= w[i];
         if (r <= 0) {
            return i;
         }
      }
      return w.length - 1;
   }

   private static float g(double s) {
      return (float)(R.nextGaussian() * s);
   }

   /** The server's hit event: feathers, down, blood mist and droplets at the hit point, with the shot's direction. */
   static void onHit(BirdNet.Hit h) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      double px = h.x(), py = h.y(), pz = h.z();
      if (mc.player != null && mc.player.distanceToSqr(px, py, pz) > 128.0 * 128.0) {
         return;
      }
      windX = Mth.clamp(h.windE() * 0.004F, -0.03F, 0.03F);
      windZ = Mth.clamp(h.windS() * 0.004F, -0.03F, 0.03F);
      boolean killed = h.killed(), flying = h.flying(), water = h.water();
      WildlifeSpecies sp = h.species() >= 0 && h.species() < WildlifeSpecies.values().length ? WildlifeSpecies.values()[h.species()] : WildlifeSpecies.DUCK;
      boolean grouse = sp == WildlifeSpecies.GROUSE;
      // the puff of a pellet strike - heard by everyone nearby
      mc.level.playLocalSound(px, py, pz, WingshotContent.BIRD_HIT.get(), SoundSource.NEUTRAL, killed ? 1.1F : 0.8F, 0.9F + R.nextFloat() * 0.2F, false);
      float q = budget();
      if (q <= 0.0F || !ensure()) {
         return;
      }
      boolean graphic = WingshotConfig.hitEffects() == WingshotConfig.HitEffects.GRAPHIC;
      Entity e = mc.level.getEntity(h.entity());
      double bvx = 0, bvy = 0, bvz = 0;
      if (e != null) {
         bvx = e.getX() - e.xo;
         bvy = e.getY() - e.yo;
         bvz = e.getZ() - e.zo;
      }
      float dx = h.dx(), dy = h.dy(), dz = h.dz();
      float energy = Mth.clamp(h.energy(), 0.2F, 2.5F);
      float scale = (killed ? 1.0F : 0.35F) * (0.75F + 0.25F * Math.min(2.0F, energy)) * q;
      // ---- what hit it shapes the burst: a charge of shot tears the bird open from several pellet holes at once (a
      // wide, dense cloud), an arrow/bolt slices through (fewer feathers, a narrow spray along its path), a bullet
      // punches through and blows a hard exit spray of blood out of the far side
      int pellets = Mth.clamp(h.count(), 1, 12);
      float fMul, cone, mistMul, dropMul, kick;
      int centers = 1;
      switch (h.shot()) {
         case BirdNet.SHOT_PELLETS -> {
            fMul = 0.85F + 0.07F * Math.min(pellets, 7);
            cone = 0.85F;
            mistMul = 0.8F + 0.1F * Math.min(pellets, 6);
            dropMul = 0.7F + 0.1F * Math.min(pellets, 6);
            kick = 1.0F;
            centers = Math.min(pellets, 5);
         }
         case BirdNet.SHOT_ARROW -> {
            fMul = 0.5F;
            cone = 0.35F;
            mistMul = 0.55F;
            dropMul = 0.8F;
            kick = 0.6F;
         }
         case BirdNet.SHOT_BULLET -> {
            fMul = 0.8F;
            cone = 0.55F;
            mistMul = 1.7F;
            dropMul = 1.35F;
            kick = 1.45F;
         }
         default -> {
            fMul = 0.6F;
            cone = 0.7F;
            mistMul = 0.6F;
            dropMul = 0.6F;
            kick = 0.8F;
         }
      }
      int feathers = Math.round((flying ? 58 : 44) * scale * fMul + 2);
      int flight = Math.round((flying ? 5 : 3) * scale * (h.shot() == BirdNet.SHOT_PELLETS ? 1.4F : 1.0F));
      int downs = Math.round(12 * scale * (0.6F + 0.4F * fMul));
      int mists = Math.round((graphic ? 9 : 4) * scale * (killed ? 1.0F : 0.7F) * mistMul + 1);
      int drops = Math.round((graphic ? 18 : 5) * scale * dropMul);
      int base = grouse ? S_GROUSE : S_DUCK;
      float[] wts = grouse ? GROUSE_W : DUCK_W;
      // the pellet holes are spread over the body (a ~quarter-block pattern at bird range)
      float[] cx = new float[centers], cy = new float[centers], cz = new float[centers];
      for (int c = 1; c < centers; c++) {
         cx[c] = g(0.12);
         cy[c] = g(0.07);
         cz[c] = g(0.12);
      }
      float dropCone = 0.5F * cone / 0.7F;
      // ---- contour feathers: a burst biased along the shot, carrying the bird's own speed, then braking hard
      for (int k = 0; k < feathers; k++) {
         int c = k % centers;
         float sp0 = (0.05F + R.nextFloat() * 0.22F) * (0.85F + 0.15F * kick);
         float ax = dx * 0.6F + g(cone), ay = dy * 0.6F + g(cone) + 0.15F, az = dz * 0.6F + g(cone);
         float l = Mth.sqrt(ax * ax + ay * ay + az * az) + 1.0E-4F;
         int i = add(FEATHER, base + pick(wts), px + cx[c] + g(0.08), py + cy[c] + g(0.08), pz + cz[c] + g(0.08), bvx * 0.7 + ax / l * sp0,
            bvy * 0.5 + ay / l * sp0, bvz * 0.7 + az / l * sp0, 0.032F + R.nextFloat() * 0.03F, 700 + R.nextInt(400));
         if (i >= 0 && R.nextFloat() < 0.22F) {
            hang[i] = 12.0F + R.nextFloat() * 22.0F; // some stay hanging in the air a moment
         }
      }
      // ---- long flight feathers knocked out of the wing: tumble down slowly
      for (int k = 0; k < flight; k++) {
         add(FLIGHT, (grouse ? S_GROUSE_FLIGHT : S_DUCK_FLIGHT) + R.nextInt(3), px + g(0.1), py + g(0.05), pz + g(0.1), bvx * 0.5 + g(0.06), bvy * 0.3 + 0.04 + g(0.03),
            bvz * 0.5 + g(0.06), 0.022F + R.nextFloat() * 0.008F, 800 + R.nextInt(400));
      }
      // ---- down: soft puffs that drift off on the wind
      for (int k = 0; k < downs; k++) {
         int c = k % centers;
         int i = add(DOWN, S_DOWN + R.nextInt(3), px + cx[c] + g(0.06), py + cy[c] + g(0.06), pz + cz[c] + g(0.06), bvx * 0.4 + g(0.05),
            bvy * 0.3 + g(0.04) + 0.01, bvz * 0.4 + g(0.05), 0.03F + R.nextFloat() * 0.04F, 50 + R.nextInt(60));
         if (i >= 0) {
            grow[i] = 0.004F + R.nextFloat() * 0.004F;
            alpha[i] = 0.85F;
         }
      }
      // ---- blood mist sprayed out of the far side along the shot (a bullet's exit wound blows it out hard)
      for (int k = 0; k < mists; k++) {
         int c = k % centers;
         float sp0 = (0.04F + R.nextFloat() * 0.12F) * kick;
         int i = add(MIST, S_MIST + R.nextInt(3), px + cx[c] + dx * 0.08, py + cy[c] + dy * 0.08, pz + cz[c] + dz * 0.08, bvx * 0.6 + dx * sp0 + g(0.03),
            bvy * 0.4 + dy * sp0 + g(0.03), bvz * 0.6 + dz * sp0 + g(0.03), (0.05F + R.nextFloat() * 0.06F) * (0.8F + 0.2F * kick), 10 + R.nextInt(14));
         if (i >= 0) {
            grow[i] = 0.012F + R.nextFloat() * 0.012F;
            alpha[i] = graphic ? 0.85F : 0.6F;
         }
      }
      // ---- droplets: ballistic, spatter where they land (ground, trunks, blocks), a pink bloom on water
      for (int k = 0; k < drops; k++) {
         int c = k % centers;
         float sp0 = (0.1F + R.nextFloat() * 0.25F) * kick;
         float ax = dx + g(dropCone), ay = dy + g(dropCone), az = dz + g(dropCone);
         float l = Mth.sqrt(ax * ax + ay * ay + az * az) + 1.0E-4F;
         add(DROP, S_DROP, px + cx[c], py + cy[c], pz + cz[c], bvx * 0.6 + ax / l * sp0, bvy * 0.4 + ay / l * sp0, bvz * 0.6 + az / l * sp0,
            0.012F + R.nextFloat() * 0.012F, 60 + R.nextInt(30));
      }
      if (water && killed) {
         splash(mc.level, px, py, pz, 10, 0.5F);
      }
   }

   /** a shot bird hits the ground / water: thud or splash, a last puff of down and loose feathers */
   static void bodyDown(WildlifeMob e, boolean water) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      double px = e.getX(), py = e.getY() + 0.1, pz = e.getZ();
      mc.level.playLocalSound(px, py, pz, water ? WingshotContent.BODY_SPLASH.get() : WingshotContent.BODY_THUD.get(), SoundSource.NEUTRAL, 1.0F,
         0.9F + R.nextFloat() * 0.2F, false);
      if (water) {
         splash(mc.level, px, py, pz, 18, 0.8F);
      }
      float q = budget();
      if (q <= 0.0F || !ensure()) {
         return;
      }
      boolean grouse = e.species == WildlifeSpecies.GROUSE;
      int n = Math.round(6 * q) + 1;
      for (int k = 0; k < n; k++) {
         int i = add(FEATHER, (grouse ? S_GROUSE : S_DUCK) + pick(grouse ? GROUSE_W : DUCK_W), px + g(0.1), py + 0.05, pz + g(0.1), g(0.05), 0.06 + R.nextFloat() * 0.05,
            g(0.05), 0.03F + R.nextFloat() * 0.025F, 600 + R.nextInt(300));
         if (i >= 0) {
            hang[i] = R.nextFloat() * 6.0F;
         }
      }
      for (int k = 0; k < Math.round(4 * q); k++) {
         int i = add(DOWN, S_DOWN + R.nextInt(3), px + g(0.1), py + 0.05, pz + g(0.1), g(0.03), 0.02, g(0.03), 0.03F, 40 + R.nextInt(30));
         if (i >= 0) {
            grow[i] = 0.004F;
            alpha[i] = 0.75F;
         }
      }
   }

   /** take-off / landing on water */
   static void splash(ClientLevel level, double px, double py, double pz, int n, float strength) {
      ParticleStatus ps = Minecraft.getInstance().options.particles().get();
      if (ps == ParticleStatus.MINIMAL) {
         n /= 4;
      }
      for (int k = 0; k < n; k++) {
         double a = R.nextDouble() * Math.PI * 2.0, r = R.nextDouble() * 0.45;
         level.addParticle(ParticleTypes.SPLASH, px + Math.cos(a) * r, py + 0.05, pz + Math.sin(a) * r, Math.cos(a) * 0.1 * strength,
            0.15 + R.nextDouble() * 0.2 * strength, Math.sin(a) * 0.1 * strength);
      }
      for (int k = 0; k < n / 3; k++) {
         level.addParticle(ParticleTypes.BUBBLE_POP, px + g(0.3), py + 0.05, pz + g(0.3), 0.0, 0.02, 0.0);
      }
   }

   // ================================================================ simulation (client tick, on the moment's clock)

   static void tick() {
      if (count == 0) {
         if (++idle > 100) {
            clear();
         }
         return;
      }
      idle = 0;
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) {
         clear();
         return;
      }
      float dt = WingShotMoment.scale();
      for (int i = count - 1; i >= 0; i--) {
         ox[i] = x[i];
         oy[i] = y[i];
         oz[i] = z[i];
         ora[i] = ra[i];
         orb[i] = rb[i];
         orc[i] = rc[i];
         age[i] += dt;
         if (age[i] >= life[i]) {
            remove(i);
            continue;
         }
         if (light[i] == -1 || ((int)age[i] + i) % 10 == 0) {
            MP.set(x[i], y[i] + 0.05, z[i]);
            light[i] = level.isLoaded(MP) ? LevelRenderer.getLightColor(level, MP) : 0xF000F0;
         }
         switch (kind[i]) {
            case FEATHER, FLIGHT -> feather(level, i, dt);
            case DOWN -> {
               damp(i, 0.1F, dt);
               vx[i] += windX * 0.06F * dt;
               vz[i] += windZ * 0.06F * dt;
               vy[i] += (0.0004F - 0.0012F) * dt;
               move(i, dt);
               size[i] += grow[i] * dt;
               grow[i] *= (float)Math.pow(0.97, dt);
               ra[i] += wa[i] * 0.1F * dt;
            }
            case MIST -> {
               damp(i, 0.18F, dt);
               vy[i] -= 0.0008F * dt;
               move(i, dt);
               size[i] += grow[i] * dt;
               grow[i] *= (float)Math.pow(0.9, dt);
               ra[i] += wa[i] * 0.05F * dt;
            }
            case DROP -> drop(level, i, dt);
            case BLOOM -> {
               size[i] += grow[i] * dt;
               grow[i] *= (float)Math.pow(0.97, dt);
            }
            default -> {
            }
         }
      }
   }

   private static void damp(int i, float k, float dt) {
      float f = (float)Math.exp(-k * dt);
      vx[i] *= f;
      vy[i] *= f;
      vz[i] *= f;
   }

   private static void move(int i, float dt) {
      x[i] += vx[i] * dt;
      y[i] += vy[i] * dt;
      z[i] += vz[i] * dt;
   }

   /** falling-leaf flutter: high drag, low terminal speed, side-slip that follows the tumble; settles flat or floats */
   private static void feather(ClientLevel level, int i, float dt) {
      if ((flags[i] & SETTLED) != 0) {
         if ((flags[i] & WATER) != 0) {
            // floats and drifts, bobbing on the ripples
            x[i] += windX * 0.15F * dt;
            z[i] += windZ * 0.15F * dt;
            y[i] = ny[i] + Mth.sin(age[i] * 0.12F + seed[i]) * 0.01F;
         }
         return;
      }
      boolean flight = kind[i] == FLIGHT;
      damp(i, flight ? 0.1F : 0.16F, dt);
      float grav = flight ? 0.0042F : 0.0036F;
      if (hang[i] > 0.0F) {
         hang[i] -= dt;
         grav *= 0.05F;
         vy[i] += 0.0006F * dt; // caught in the air for a moment
      }
      vy[i] -= grav * dt;
      float t = age[i] * 0.22F + seed[i];
      vx[i] += (Mth.sin(t) * 0.0035F + windX * 0.05F) * dt;
      vz[i] += (Mth.cos(t * 0.8F) * 0.0035F + windZ * 0.05F) * dt;
      // tumble: spin rates relax toward a gentle rocking
      wa[i] *= (float)Math.pow(0.97, dt);
      wb[i] = wb[i] * (float)Math.pow(0.95, dt) + Mth.sin(t * 1.3F) * 0.02F * dt;
      wc[i] *= (float)Math.pow(0.97, dt);
      ra[i] += wa[i] * dt;
      rb[i] += wb[i] * dt;
      rc[i] += wc[i] * dt;
      float nx0 = x[i] + vx[i] * dt, ny0 = y[i] + vy[i] * dt, nz0 = z[i] + vz[i] * dt;
      MP.set(nx0, ny0, nz0);
      if (!level.isLoaded(MP)) {
         age[i] = life[i];
         return;
      }
      FluidState fs = level.getFluidState(MP);
      if (!fs.isEmpty()) {
         float surface = MP.getY() + fs.getHeight(level, MP);
         if (ny0 <= surface) {
            settle(i, nx0, surface + 0.008F, nz0, true);
            return;
         }
      }
      BlockState bs = level.getBlockState(MP);
      if (!bs.isAir()) {
         VoxelShape shape = bs.getCollisionShape(level, MP);
         if (!shape.isEmpty()) {
            AABB b = shape.bounds();
            float top = (float)(MP.getY() + b.maxY);
            if (ny0 < top && oy[i] >= top - 0.02F) {
               settle(i, nx0, top + 0.006F, nz0, false);
               return;
            }
            if (ny0 < top) {
               // brushed a side: slide down it
               vx[i] *= -0.2F;
               vz[i] *= -0.2F;
               return;
            }
         }
      }
      x[i] = nx0;
      y[i] = ny0;
      z[i] = nz0;
   }

   private static void settle(int i, float px, float py, float pz, boolean water) {
      x[i] = px;
      y[i] = py;
      z[i] = pz;
      ny[i] = py;
      vx[i] = vy[i] = vz[i] = 0.0F;
      flags[i] |= SETTLED;
      if (water) {
         flags[i] |= WATER;
      }
      // lie flat with a random heading
      rb[i] = orb[i] = Mth.HALF_PI;
      rc[i] = orc[i] = 0.0F;
      ora[i] = ra[i];
      life[i] = Math.min(life[i], age[i] + (water ? 500 : 700) + R.nextInt(300));
   }

   /** a droplet in flight: on a block it leaves a splat on that face; on water a pink bloom */
   private static void drop(ClientLevel level, int i, float dt) {
      damp(i, 0.02F, dt);
      vy[i] -= 0.03F * dt;
      float nx0 = x[i] + vx[i] * dt, ny0 = y[i] + vy[i] * dt, nz0 = z[i] + vz[i] * dt;
      MP.set(nx0, ny0, nz0);
      if (!level.isLoaded(MP)) {
         age[i] = life[i];
         return;
      }
      FluidState fs = level.getFluidState(MP);
      if (!fs.isEmpty() && ny0 <= MP.getY() + fs.getHeight(level, MP)) {
         float surface = MP.getY() + fs.getHeight(level, MP);
         toBloom(i, nx0, surface + 0.01F, nz0);
         return;
      }
      BlockState bs = level.getBlockState(MP);
      if (!bs.isAir()) {
         VoxelShape shape = bs.getCollisionShape(level, MP);
         if (!shape.isEmpty()) {
            AABB b = shape.bounds().move(MP);
            if (b.contains(nx0, ny0, nz0)) {
               // which face did it come through?
               float px = x[i], py = y[i], pz = z[i];
               float fx = 0, fy = 0, fz = 0;
               float hx, hy, hz;
               if (py >= b.maxY) {
                  fy = 1;
                  hx = nx0;
                  hy = (float)b.maxY;
                  hz = nz0;
               } else if (py <= b.minY) {
                  fy = -1;
                  hx = nx0;
                  hy = (float)b.minY;
                  hz = nz0;
               } else if (Math.abs(nx0 - px) > Math.abs(nz0 - pz)) {
                  fx = px < b.minX ? -1 : 1;
                  hx = (float)(fx < 0 ? b.minX : b.maxX);
                  hy = ny0;
                  hz = nz0;
               } else {
                  fz = pz < b.minZ ? -1 : 1;
                  hx = nx0;
                  hy = ny0;
                  hz = (float)(fz < 0 ? b.minZ : b.maxZ);
               }
               toSplat(i, hx, hy, hz, fx, fy, fz);
               return;
            }
         }
      }
      x[i] = nx0;
      y[i] = ny0;
      z[i] = nz0;
   }

   private static void toSplat(int i, float px, float py, float pz, float fx, float fy, float fz) {
      boolean graphic = WingshotConfig.hitEffects() == WingshotConfig.HitEffects.GRAPHIC;
      if (!graphic && R.nextFloat() < 0.6F || fy < 0) {
         age[i] = life[i];
         return;
      }
      kind[i] = SPLAT;
      spr[i] = (byte)(S_SPLAT + R.nextInt(2));
      nx[i] = fx;
      ny[i] = fy;
      nz[i] = fz;
      x[i] = ox[i] = px + fx * 0.012F;
      y[i] = oy[i] = py + fy * 0.012F;
      z[i] = oz[i] = pz + fz * 0.012F;
      ra[i] = ora[i] = R.nextFloat() * Mth.TWO_PI;
      size[i] = 0.05F + R.nextFloat() * 0.07F;
      age[i] = 0.0F;
      life[i] = 900 + R.nextInt(400);
      vx[i] = vy[i] = vz[i] = 0.0F;
   }

   private static void toBloom(int i, float px, float py, float pz) {
      kind[i] = BLOOM;
      spr[i] = (byte)(S_MIST + R.nextInt(3));
      x[i] = ox[i] = px;
      y[i] = oy[i] = py;
      z[i] = oz[i] = pz;
      size[i] = 0.05F;
      grow[i] = 0.006F;
      alpha[i] = 0.55F;
      age[i] = 0.0F;
      life[i] = 120 + R.nextInt(80);
      vx[i] = vy[i] = vz[i] = 0.0F;
   }

   // ================================================================ drawing

   private static final Vector3f L = new Vector3f(), U = new Vector3f();

   static void render(VertexConsumer vc, Camera cam, float pt, boolean translucentPass) {
      if (sprites == null || count == 0) {
         return;
      }
      Vec3 c = cam.getPosition();
      L.set(cam.getLeftVector());
      U.set(cam.getUpVector());
      float f = pt;
      for (int i = 0; i < count; i++) {
         int k = kind[i];
         boolean tl = k == DOWN || k == MIST || k == BLOOM;
         if (tl != translucentPass) {
            continue;
         }
         float px = (float)(Mth.lerp(f, ox[i], x[i]) - c.x), py = (float)(Mth.lerp(f, oy[i], y[i]) - c.y), pz = (float)(Mth.lerp(f, oz[i], z[i]) - c.z);
         if (px * px + py * py + pz * pz > 96.0F * 96.0F) {
            continue;
         }
         TextureAtlasSprite s = sprites[spr[i] & 255];
         float a = alpha[i];
         float lifeLeft = life[i] - age[i];
         int lt = light[i] == -1 ? 0xF000F0 : light[i];
         switch (k) {
            case FEATHER, FLIGHT -> {
               float fade = Mth.clamp(lifeLeft / 60.0F, 0.0F, 1.0F);
               if (fade <= 0.01F) {
                  continue;
               }
               float A = Mth.lerp(f, ora[i], ra[i]), B = Mth.lerp(f, orb[i], rb[i]), C = Mth.lerp(f, orc[i], rc[i]);
               // basis from yaw A, pitch B, roll C
               float ca = Mth.cos(A), sa = Mth.sin(A), cbb = Mth.cos(B), sbb = Mth.sin(B), cc = Mth.cos(C), sc = Mth.sin(C);
               // R = Ry(A) * Rx(B) * Rz(C); local x and y axes
               float xx = ca * cc + sa * sbb * sc, xy = cbb * sc, xz = -sa * cc + ca * sbb * sc;
               float yx = -ca * sc + sa * sbb * cc, yy = cbb * cc, yz = sa * sc + ca * sbb * cc;
               float nzx = sa * cbb, nzy = -sbb, nzz = ca * cbb;
               float shade = 0.72F + 0.28F * Math.abs(nzy);
               float hx = size[i], hy = size[i];
               if (k == FLIGHT) {
                  hy = size[i] * 3.0F; // long and narrow (16x48 sprite)
               }
               // fading feathers shrink away (cutout can't fade)
               hx *= fade;
               hy *= fade;
               quad(vc, s, px, py, pz, xx * hx, xy * hx, xz * hx, yx * hy, yy * hy, yz * hy, cr[i] * shade, cg[i] * shade, cb[i] * shade, 1.0F, lt, true);
            }
            case SPLAT -> {
               float fade = Mth.clamp(lifeLeft / 100.0F, 0.0F, 1.0F) * Mth.clamp(age[i] / 2.0F + 0.5F, 0.0F, 1.0F);
               float h = size[i] * (0.6F + 0.4F * fade);
               // two tangents on the face
               float tx, ty, tz;
               if (Math.abs(ny[i]) > 0.5F) {
                  tx = 1;
                  ty = 0;
                  tz = 0;
               } else {
                  tx = 0;
                  ty = 1;
                  tz = 0;
               }
               float bx = ny[i] * tz - nz[i] * ty, by = nz[i] * tx - nx[i] * tz, bz = nx[i] * ty - ny[i] * tx;
               float ca = Mth.cos(ra[i]), sa = Mth.sin(ra[i]);
               float ux = (tx * ca + bx * sa) * h, uy = (ty * ca + by * sa) * h, uz = (tz * ca + bz * sa) * h;
               float vx2 = (-tx * sa + bx * ca) * h, vy2 = (-ty * sa + by * ca) * h, vz2 = (-tz * sa + bz * ca) * h;
               float dark = 0.75F + 0.25F * fade;
               quad(vc, s, px, py, pz, ux, uy, uz, vx2, vy2, vz2, dark, dark, dark, 1.0F, lt, true);
            }
            case DROP -> {
               float h = size[i];
               quad(vc, s, px, py, pz, L.x() * h, L.y() * h, L.z() * h, U.x() * h, U.y() * h, U.z() * h, 1, 1, 1, 1, lt, false);
            }
            case DOWN, MIST -> {
               float t = age[i] / life[i];
               float al = a * (k == MIST ? (1.0F - t) * (1.0F - t) : Mth.clamp((1.0F - t) * 1.6F, 0.0F, 1.0F)) * Mth.clamp(age[i] * 0.8F, 0.0F, 1.0F);
               if (al < 0.01F) {
                  continue;
               }
               float h = size[i];
               float ca = Mth.cos(ra[i]), sa = Mth.sin(ra[i]);
               float lx = (L.x() * ca + U.x() * sa) * h, ly = (L.y() * ca + U.y() * sa) * h, lz = (L.z() * ca + U.z() * sa) * h;
               float ux = (-L.x() * sa + U.x() * ca) * h, uy = (-L.y() * sa + U.y() * ca) * h, uz = (-L.z() * sa + U.z() * ca) * h;
               quad(vc, s, px, py, pz, lx, ly, lz, ux, uy, uz, cr[i], cg[i], cb[i], al, lt, false);
            }
            case BLOOM -> {
               float t = age[i] / life[i];
               float al = a * (1.0F - t) * Mth.clamp(age[i] * 0.3F, 0.0F, 1.0F);
               float h = size[i];
               float ca = Mth.cos(ra[i]), sa = Mth.sin(ra[i]);
               quad(vc, s, px, py, pz, ca * h, 0, sa * h, -sa * h, 0, ca * h, 0.9F, 0.75F, 0.75F, al, lt, true);
            }
            default -> {
            }
         }
      }
   }

   /** a quad centred at p spanned by half-axes a and b; {@code both}: also the back face */
   private static void quad(VertexConsumer vc, TextureAtlasSprite s, float px, float py, float pz, float ax, float ay, float az, float bx, float by, float bz,
      float r, float g, float b, float a, int lt, boolean both) {
      float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
      // corners: (-a -b) (-a +b) (+a +b) (+a -b): counter-clockwise seen from the a x b side
      vc.addVertex(px - ax - bx, py - ay - by, pz - az - bz).setUv(u0, v1).setColor(r, g, b, a).setLight(lt);
      vc.addVertex(px + ax - bx, py + ay - by, pz + az - bz).setUv(u1, v1).setColor(r, g, b, a).setLight(lt);
      vc.addVertex(px + ax + bx, py + ay + by, pz + az + bz).setUv(u1, v0).setColor(r, g, b, a).setLight(lt);
      vc.addVertex(px - ax + bx, py - ay + by, pz - az + bz).setUv(u0, v0).setColor(r, g, b, a).setLight(lt);
      if (both) {
         vc.addVertex(px - ax + bx, py - ay + by, pz - az + bz).setUv(u0, v0).setColor(r, g, b, a).setLight(lt);
         vc.addVertex(px + ax + bx, py + ay + by, pz + az + bz).setUv(u1, v0).setColor(r, g, b, a).setLight(lt);
         vc.addVertex(px + ax - bx, py + ay - by, pz + az - bz).setUv(u1, v1).setColor(r, g, b, a).setLight(lt);
         vc.addVertex(px - ax - bx, py - ay - by, pz - az - bz).setUv(u0, v1).setColor(r, g, b, a).setLight(lt);
      }
   }

   static int live() {
      return count;
   }

   /** One of the two pool renderers in the particle engine. The opaque one also ticks the pool. */
   static final class Cloud extends Particle {
      private final boolean translucentPass;

      Cloud(ClientLevel level, boolean translucent) {
         super(level, 0.0, 0.0, 0.0);
         this.translucentPass = translucent;
         this.lifetime = Integer.MAX_VALUE;
         this.hasPhysics = false;
         this.gravity = 0.0F;
      }

      ClientLevel lvl() {
         return this.level;
      }

      @Override
      public void tick() {
         if (!this.translucentPass) {
            BirdFx.tick();
            if (opaque != this) {
               this.remove();
            }
         } else if (translucent != this) {
            this.remove();
         }
      }

      @Override
      public void render(VertexConsumer vc, Camera cam, float pt) {
         BirdFx.render(vc, cam, pt, this.translucentPass);
      }

      @Override
      public ParticleRenderType getRenderType() {
         return this.translucentPass ? ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT : ParticleRenderType.PARTICLE_SHEET_OPAQUE;
      }

      @Override
      public AABB getRenderBoundingBox(float pt) {
         return AABB.INFINITE;
      }
   }
}
