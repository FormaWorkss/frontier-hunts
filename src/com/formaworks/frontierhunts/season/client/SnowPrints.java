package com.formaworks.frontierhunts.season.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.season.DeepSnow;
import com.formaworks.frontierhunts.weather.SeasonalWeather;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * [1.1.1] Snow you can see yourself walk through.
 *
 * <ul>
 * <li><b>Prints:</b> every step a person or animal takes in snow presses a print into the surface - an oval boot print
 *     left and right of the line a player walks, small paired hoof prints for deer, elk and moose - as deep as the snow
 *     allows. Wading deeper than the knee ploughs a trench the width of the body instead. The snow mesh is rebuilt with
 *     the dents ({@link SmoothSnowModel}), so the snowpack visibly deforms; fresh snowfall slowly fills prints in.</li>
 * <li><b>Wading feel:</b> each heavy step kicks snow up and out, crunches (powder snow underfoot when deep), and your
 *     view lurches with the effort; landing in deep snow throws up a burst.</li>
 * <li><b>Wind:</b> in a stiff wind, spindrift streams low across open snow.</li>
 * </ul>
 * All client side and local to what you can see (40 blocks), so it costs nothing on a server.
 */
@EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT)
public final class SnowPrints {
   private SnowPrints() {
   }

   /** vertices per block side in a dent grid */
   public static final int N = 7;
   static final Map<Long, float[]> DENTS = new ConcurrentHashMap<>();
   static final Map<UUID, double[]> STEPS = new HashMap<>(); // last x, last z, distance since the last print, foot side
   private static final int MAX = 24000;
   private static float lurch, lurchO;

   /** the dent grid of a snow block (row-major [i * N + j], i along x), or null */
   public static float[] dents(BlockPos pos) {
      return DENTS.get(pos.asLong());
   }

   /** [1.1.2] the prints as SnowField reads them: world-space, across block borders */
   public static final SnowField.Dents FIELD = new SnowField.Dents() {
      @Override
      public boolean near(int x, int y, int z) {
         if (DENTS.isEmpty()) {
            return false;
         }
         for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
               for (int dz = -1; dz <= 1; dz++) {
                  if (DENTS.containsKey(BlockPos.asLong(x + dx, y + dy, z + dz))) {
                     return true;
                  }
               }
            }
         }
         return false;
      }

      @Override
      public float at(int y, double wx, double wz) {
         int bx = Mth.floor(wx), bz = Mth.floor(wz);
         // a vertex on a block border belongs to both blocks: take the deeper print
         float best = 0.0F;
         for (int ox = 0; ox <= 1; ox++) {
            for (int oz = 0; oz <= 1; oz++) {
               int cx = bx - ox, cz = bz - oz;
               double u = wx - cx, v = wz - cz;
               if (u < 0.0 || u > 1.0 || v < 0.0 || v > 1.0 || (ox == 1 && u != 1.0) || (oz == 1 && v != 1.0)) {
                  continue;
               }
               for (int dy = -1; dy <= 1; dy++) {
                  float[] g = DENTS.get(BlockPos.asLong(cx, y + dy, cz));
                  if (g != null) {
                     best = Math.max(best, sample(g, (float)u, (float)v));
                  }
               }
            }
         }
         return best;
      }
   };

   /** bilinear dent depth at (u, v) in 0..1 */
   public static float sample(float[] d, float u, float v) {
      float fx = u * (N - 1), fz = v * (N - 1);
      int i = Math.min(N - 2, (int)fx), j = Math.min(N - 2, (int)fz);
      float tx = fx - i, tz = fz - j;
      float a = d[i * N + j], b = d[(i + 1) * N + j], c = d[i * N + j + 1], e = d[(i + 1) * N + j + 1];
      return Mth.lerp(tz, Mth.lerp(tx, a, b), Mth.lerp(tx, c, e));
   }

   static boolean snow(BlockState s) {
      return s.getBlock() instanceof SnowLayerBlock;
   }

   /** the top snow block of the pile under / around (x, y, z), or null */
   static BlockPos pile(ClientLevel level, double x, double y, double z) {
      BlockPos p = DeepSnow.top(level, BlockPos.containing(x, y + 0.01, z));
      if (p == null) {
         p = DeepSnow.top(level, BlockPos.containing(x, y + 1.01, z));
      }
      return p;
   }

   /**
    * press an ellipse (radii ra along heading, rb across, yaw in radians) of the given depth into the snow surface
    * around world (cx, cz); the surface block is found per column
    */
   static void stamp(ClientLevel level, double cx, double cy, double cz, double ra, double rb, float yaw, float depth) {
      if (!SnowLook.frontier()) {
         return; // [1.1.2] prints are pressed into the Frontier snowpack only
      }
      double reach = Math.max(ra, rb) + 0.2;
      int x0 = Mth.floor(cx - reach), x1 = Mth.floor(cx + reach), z0 = Mth.floor(cz - reach), z1 = Mth.floor(cz + reach);
      double c = Math.cos(yaw), s = Math.sin(yaw);
      for (int bx = x0; bx <= x1; bx++) {
         for (int bz = z0; bz <= z1; bz++) {
            BlockPos top = pile(level, bx + 0.5, cy, bz + 0.5);
            if (top == null || Math.abs(top.getY() - cy) > 2.5) {
               continue;
            }
            float[] cur = DENTS.get(top.asLong());
            float[] next = cur == null ? new float[N * N] : cur.clone();
            boolean changed = false;
            // [1.1.2] no cap per block: the blanket stops at the ground by itself, so a trench floor runs on level
            // across blocks of different depth
            float cap = 1.6F;
            for (int i = 0; i < N; i++) {
               for (int j = 0; j < N; j++) {
                  double wx = bx + (double)i / (N - 1) - cx, wz = bz + (double)j / (N - 1) - cz;
                  // into the print's own frame: a along the heading, b across it
                  double a = -wx * s + wz * c, b = wx * c + wz * s;
                  double r = (a * a) / (ra * ra) + (b * b) / (rb * rb);
                  if (r >= 1.0) {
                     continue;
                  }
                  // a soft-walled hollow: flat floor, rounded rim
                  float k = (float)(r < 0.45 ? 1.0 : 1.0 - Mth.smoothstep((r - 0.45) / 0.55));
                  float want = Math.min(cap, depth * k);
                  if (want > next[i * N + j] + 0.004F) {
                     next[i * N + j] = want;
                     changed = true;
                  }
               }
            }
            if (changed) {
               DENTS.put(top.asLong(), next);
               Minecraft.getInstance().levelRenderer.setBlocksDirty(top.getX() - 1, top.getY() - 1, top.getZ() - 1, top.getX() + 1, top.getY() + 1, top.getZ() + 1);
            }
         }
      }
   }

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post event) {
      try {
         prints();
      } catch (RuntimeException e) {
         // [1.1.3] cosmetic: never let a print take the game down
         if (!warned) {
            warned = true;
            org.slf4j.LoggerFactory.getLogger("frontierhunts").warn("Snow prints switched off after an error", e);
         }
      }
   }

   private static boolean warned;

   private static void prints() {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null || mc.player == null || mc.isPaused()) {
         return;
      }
      lurchO = lurch;
      lurch *= 0.82F;
      RandomSource r = level.random;
      AABB near = mc.player.getBoundingBox().inflate(40.0, 12.0, 40.0);
      for (Entity e : level.getEntities((Entity)null, near, x -> x instanceof LivingEntity && !x.isSpectator())) {
         LivingEntity le = (LivingEntity)e;
         if (le.getVehicle() != null) {
            continue;
         }
         BlockPos top = pile(level, le.getX(), le.getY(), le.getZ());
         if (top == null) {
            continue;
         }
         double surface = top.getY() + level.getBlockState(top).getValue(SnowLayerBlock.LAYERS) / 8.0;
         double wade = Math.max(0.0, surface - le.getY());
         // distance walked, from the entity's own movement (remote players and animals included)
         double[] st = STEPS.computeIfAbsent(le.getUUID(), k -> new double[]{le.getX(), le.getZ(), 0.0, 1.0});
         double step = Math.hypot(le.getX() - st[0], le.getZ() - st[1]);
         st[0] = le.getX();
         st[1] = le.getZ();
         boolean quad = !(le instanceof Player) && le.getBbWidth() > 0.7F;
         float stride = le instanceof Player ? 0.62F : quad ? 0.5F : 0.3F;
         if (step > 3.0 || !le.onGround()) {
            continue; // teleport, or in the air
         }
         st[2] += step;
         if (st[2] < stride) {
            continue;
         }
         st[2] = 0.0;
         st[3] = st[3] > 0.0 ? -1.0 : 1.0;
         float yaw = le.getYRot() * (float)(Math.PI / 180.0);
         double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
         double side = st[3] * (le instanceof Player ? 0.14 : le.getBbWidth() * 0.22);
         double px = le.getX() + fz * side, pz = le.getZ() - fx * side;
         float body = le.getBbWidth();
         if (wade > 0.32) {
            // ploughing: a trench the width of the body, as deep as you have sunk
            stamp(level, le.getX(), le.getY(), le.getZ(), body * 0.55 + 0.25, body * 0.5 + 0.12, yaw, (float)Math.min(1.0, wade * 0.85));
         } else if (le instanceof Player) {
            stamp(level, px, le.getY(), pz, 0.17, 0.085, yaw, (float)Math.max(0.07, Math.min(0.3, wade + 0.06)));
         } else {
            float hoof = quad ? 0.075F : 0.05F;
            stamp(level, px + fx * 0.1, le.getY(), pz + fz * 0.1, hoof, hoof * 0.8, yaw, (float)Math.max(0.05, Math.min(0.25, wade + 0.05)));
            stamp(level, px - fx * body * 0.7, le.getY(), pz - fz * body * 0.7, hoof, hoof * 0.8, yaw, (float)Math.max(0.05, Math.min(0.25, wade + 0.05)));
         }
         // the step itself: kicked snow, the crunch, and (for you) the lurch of a heavy step
         if (le.distanceToSqr(mc.player) < 24.0 * 24.0) {
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState());
            int puffs = wade > 0.32 ? 9 : 3;
            for (int i = 0; i < puffs; i++) {
               double vx = fx * (0.05 + r.nextDouble() * 0.12) + (r.nextDouble() - 0.5) * 0.12;
               double vz = fz * (0.05 + r.nextDouble() * 0.12) + (r.nextDouble() - 0.5) * 0.12;
               level.addParticle(dust, px + (r.nextDouble() - 0.5) * 0.3, surface + 0.05, pz + (r.nextDouble() - 0.5) * 0.3, vx,
                  0.08 + r.nextDouble() * (wade > 0.32 ? 0.22 : 0.08), vz);
            }
            if (wade > 0.32) {
               level.playLocalSound(px, surface, pz, SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 0.55F, 0.75F + r.nextFloat() * 0.2F, false);
            }
         }
         if (le == mc.player && wade > 0.25) {
            lurch += (float)Math.min(1.0, wade) * (st[3] > 0 ? 1.0F : -1.0F);
         }
      }
      // landing in deep snow
      if (mc.player.onGround() && mc.player.fallDistance == 0.0F && lastAir > 6) {
         double w = DeepSnow.wade(mc.player);
         if (w > 0.25) {
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState());
            for (int i = 0; i < 24; i++) {
               level.addParticle(dust, mc.player.getX() + (r.nextDouble() - 0.5), mc.player.getY() + w, mc.player.getZ() + (r.nextDouble() - 0.5),
                  (r.nextDouble() - 0.5) * 0.3, 0.15 + r.nextDouble() * 0.25, (r.nextDouble() - 0.5) * 0.3);
            }
            level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.POWDER_SNOW_FALL, SoundSource.PLAYERS, 0.9F, 0.9F, false);
            stamp(level, mc.player.getX(), mc.player.getY(), mc.player.getZ(), 0.55, 0.45, mc.player.getYRot() * (float)(Math.PI / 180.0), (float)Math.min(1.0, w));
         }
      }
      lastAir = mc.player.onGround() ? 0 : lastAir + 1;
      spindrift(mc, level, r);
      // fresh snow slowly fills prints; keep the store bounded to the land around you
      // [perf3] spread over 64 ticks: each tick sweeps 1/64 of the store (each print still once per ~64 ticks, at the same
      // fill rate). The whole store at once (up to 24,000 prints, each re-meshing the sections around it while it
      // snows) was a burst of section rebuilds every 3.2 s: a periodic hitch in snow country.
      {
         boolean snowing = level.isRaining();
         int px = mc.player.getBlockX(), pz = mc.player.getBlockZ();
         Iterator<Map.Entry<Long, float[]>> it = sweep;
         if (it == null || !it.hasNext()) it = sweep = DENTS.entrySet().iterator();
         for (int quota = (DENTS.size() + 63) / 64; quota > 0 && it.hasNext(); quota--) {
            Map.Entry<Long, float[]> en = it.next();
            long k = en.getKey();
            if (Math.abs(BlockPos.getX(k) - px) > 160 || Math.abs(BlockPos.getZ(k) - pz) > 160 || DENTS.size() > MAX) {
               it.remove();
               continue;
            }
            if (snowing) {
               float[] d = en.getValue().clone();
               float max = 0.0F;
               for (int i = 0; i < d.length; i++) {
                  d[i] = Math.max(0.0F, d[i] - 0.006F);
                  max = Math.max(max, d[i]);
               }
               BlockPos p = BlockPos.of(k);
               if (max <= 0.0F) {
                  it.remove();
               } else {
                  en.setValue(d);
               }
               mc.levelRenderer.setBlocksDirty(p.getX() - 1, p.getY() - 1, p.getZ() - 1, p.getX() + 1, p.getY() + 1, p.getZ() + 1);
            }
         }
      }
      if ((level.getGameTime() & 63) == 0) {
         STEPS.keySet().removeIf(u -> level.getPlayerByUUID(u) == null && STEPS.size() > 256);
      }
   }

   /** [perf3] Where the fill sweep is in {@link #DENTS} (a weakly consistent iterator, resumed every tick). */
   private static Iterator<Map.Entry<Long, float[]>> sweep;

   private static int lastAir;

   /** in a stiff wind, loose snow streams low across open snowfields */
   private static void spindrift(Minecraft mc, ClientLevel level, RandomSource r) {
      float we = SeasonalWeather.windEast(level), ws = SeasonalWeather.windSouth(level);
      float speed = (float)Math.sqrt(we * we + ws * ws);
      if (speed < 4.0F || mc.options.particles().get().getId() == 2) {
         return;
      }
      int n = Math.min(14, (int)((speed - 4.0F) * 1.5F) + 2);
      for (int i = 0; i < n; i++) {
         int x = mc.player.getBlockX() + r.nextInt(33) - 16, z = mc.player.getBlockZ() + r.nextInt(33) - 16;
         int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
         BlockState s = level.getBlockState(BlockPos.containing(x, y, z));
         if (!snow(s)) {
            continue;
         }
         double top = y + s.getValue(SnowLayerBlock.LAYERS) / 8.0;
         level.addParticle(ParticleTypes.SNOWFLAKE, x + r.nextDouble(), top + 0.05 + r.nextDouble() * 0.25, z + r.nextDouble(),
            we * 0.045 + (r.nextDouble() - 0.5) * 0.04, 0.01 + r.nextDouble() * 0.03, ws * 0.045 + (r.nextDouble() - 0.5) * 0.04);
      }
   }

   /** the view lurches with each heavy step through deep snow */
   @SubscribeEvent
   public static void camera(ViewportEvent.ComputeCameraAngles e) {
      if (warned) {
         return;
      }
      float k = Mth.lerp((float)e.getPartialTick(), lurchO, lurch) * com.formaworks.frontierhunts.HuntConfig.shake(); // [1.1.5] camera shake setting
      if (Math.abs(k) < 0.01F) {
         return;
      }
      e.setRoll(e.getRoll() + k * 2.2F);
      e.setPitch(e.getPitch() + Math.abs(k) * 1.6F);
   }

   @SubscribeEvent
   public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
      DENTS.clear();
      STEPS.clear();
   }
}
