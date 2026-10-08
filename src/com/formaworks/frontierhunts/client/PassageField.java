package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.tracking.TrailMark;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;

/**
 * [1.1.6] Client side of sign of passage: turns the PASSAGE / MATTED trail marks near you into a per-block bend
 * (direction and angle) that {@link PassageModel} applies to grass and brush models. Angles are quantised so a chunk
 * section only rebuilds when a plant visibly changes (as the grass slowly stands back up).
 */
public final class PassageField {
   private PassageField() {
   }

   /** a bend for one plant column: unit direction the plant leans to, lean angle (radians), feet height of the animal */
   public record Bend(float dx, float dz, float angle, int y) {
   }

   private static volatile Map<Long, Bend> field = Map.of();
   /**
    * [1.1.8] the same bends keyed by the block's render seed, for renderers that never ask a model for its ModelData
    * (Sodium builds chunks without it, so the bend has to be found from the seeded random each plant is drawn with)
    */
   private static volatile Map<Long, long[]> bySeed = Map.of();
   private static long lastBuild = Long.MIN_VALUE;

   public static Bend at(BlockPos pos) {
      Map<Long, Bend> f = field;
      if (f.isEmpty()) {
         return null;
      }
      Bend b = f.get(BlockPos.asLong(pos.getX(), 0, pos.getZ()));
      return b != null && pos.getY() >= b.y() - 1 && pos.getY() <= b.y() + 1 ? b : null;
   }

   private static final Map<Class<?>, java.lang.reflect.Field> SEED_FIELDS = new java.util.concurrent.ConcurrentHashMap<>();
   private static final java.lang.reflect.Field NONE;

   static {
      java.lang.reflect.Field f = null;
      try {
         f = PassageField.class.getDeclaredField("lastBuild");
      } catch (ReflectiveOperationException ignored) {
      }
      NONE = f;
   }

   /** the internal state of a just-seeded random source (the value the renderer set from the block position), or null */
   private static Long seedState(Object rand) {
      if (rand == null) {
         return null;
      }
      Object target = rand;
      java.lang.reflect.Field f = SEED_FIELDS.computeIfAbsent(rand.getClass(), PassageField::findSeed);
      if (f == NONE || f == null) {
         return null;
      }
      try {
         Object v = f.get(target);
         if (v instanceof Long l) {
            return l;
         }
         if (v instanceof java.util.concurrent.atomic.AtomicLong a) {
            return a.get();
         }
         if (v != null) {
            // Xoroshiro: the generator object carries seedLo
            java.lang.reflect.Field lo = SEED_FIELDS.computeIfAbsent(v.getClass(), c -> field(c, "seedLo"));
            if (lo != NONE && lo != null) {
               return (Long)lo.get(v);
            }
         }
      } catch (ReflectiveOperationException | RuntimeException e) {
         return null;
      }
      return null;
   }

   private static java.lang.reflect.Field findSeed(Class<?> c) {
      java.lang.reflect.Field f = field(c, "seed");
      return f != NONE ? f : field(c, "randomNumberGenerator");
   }

   private static java.lang.reflect.Field field(Class<?> c, String name) {
      for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
         try {
            java.lang.reflect.Field f = k.getDeclaredField(name);
            f.setAccessible(true);
            return f;
         } catch (ReflectiveOperationException | RuntimeException ignored) {
         }
      }
      return NONE;
   }

   /** [1.1.8] the bend for the plant whose quads are being drawn with this freshly seeded random, or null */
   public static BlockPos posFor(Object rand) {
      Map<Long, long[]> m = bySeed;
      if (m.isEmpty()) {
         return null;
      }
      Long st = seedState(rand);
      if (st == null) {
         return null;
      }
      long[] p = m.get(st);
      return p == null ? null : new BlockPos((int)p[0], (int)p[1], (int)p[2]);
   }

   /** called when marks arrive and every few seconds (wear lets the grass stand back up) */
   public static void rebuild(List<TrailMark> marks, long now) {
      lastBuild = now;
      Map<Long, Bend> next = new HashMap<>();
      for (TrailMark m : marks) {
         if (!m.plantSign() || m.expired(now)) {
            continue;
         }
         float wear = m.wearFraction(now);
         double yaw = Math.toRadians(m.yaw());
         double fx = -Math.sin(yaw), fz = Math.cos(yaw);
         int y = Mth.floor(m.position().y);
         if (m.style() == TrailMark.PASSAGE) {
            // the strip the body went through, from the previous stride to here
            double len = Math.max(0.6, m.stride());
            double half = m.radius() + 0.25 * m.scale();
            double base = Math.toRadians(m.activity() == 2 ? 68 : (m.activity() == 1 ? 52 : 36));
            float angle = (float)(base * (1.0 - wear) * (1.0 - wear * 0.3));
            double x0 = m.position().x - fx * len, z0 = m.position().z - fz * len;
            int minX = Mth.floor(Math.min(x0, m.position().x) - half), maxX = Mth.floor(Math.max(x0, m.position().x) + half);
            int minZ = Mth.floor(Math.min(z0, m.position().z) - half), maxZ = Mth.floor(Math.max(z0, m.position().z) + half);
            for (int x = minX; x <= maxX; x++) {
               for (int z = minZ; z <= maxZ; z++) {
                  double cx = x + 0.5 - x0, cz = z + 0.5 - z0;
                  double t = Mth.clamp((cx * fx + cz * fz) / len, 0.0, 1.0);
                  double px = cx - fx * t * len, pz = cz - fz * t * len;
                  double side = cx * fz - cz * fx; // + right of travel
                  double dist = Math.sqrt(px * px + pz * pz);
                  if (dist > half) {
                     continue;
                  }
                  // pushed forward and a little outward, away from the body's centre line
                  double out = Math.copySign(Math.min(0.55, Math.abs(side) / Math.max(0.2, half)), side);
                  double dx = fx + fz * out, dz = fz - fx * out;
                  double n = Math.sqrt(dx * dx + dz * dz);
                  float a = (float)(angle * (1.0 - 0.45 * dist / half));
                  put(next, x, z, new Bend((float)(dx / n), (float)(dz / n), quant(a), y));
               }
            }
         } else {
            // pressed flat in a body-sized ellipse along the animal's length
            double along = 0.95 * m.scale(), across = 0.55 * m.scale();
            float angle = (float)(Math.toRadians(82) * (1.0 - wear * 0.6));
            int r = (int)Math.ceil(along) + 1;
            int cxb = Mth.floor(m.position().x), czb = Mth.floor(m.position().z);
            for (int x = cxb - r; x <= cxb + r; x++) {
               for (int z = czb - r; z <= czb + r; z++) {
                  double cx = x + 0.5 - m.position().x, cz = z + 0.5 - m.position().z;
                  double a = cx * fx + cz * fz, b = cx * fz - cz * fx;
                  double e = (a * a) / (along * along) + (b * b) / (across * across);
                  if (e > 1.0) {
                     continue;
                  }
                  double dx = cx, dz = cz, n = Math.sqrt(dx * dx + dz * dz);
                  if (n < 0.2) {
                     dx = fx;
                     dz = fz;
                     n = 1.0;
                  }
                  put(next, x, z, new Bend((float)(dx / n), (float)(dz / n), quant((float)(angle * (1.0 - 0.25 * e))), y));
               }
            }
         }
      }
      Map<Long, Bend> old = field;
      Map<Long, long[]> seeds = new HashMap<>(next.size() * 6);
      for (Map.Entry<Long, Bend> e : next.entrySet()) {
         int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey());
         for (int dy = -1; dy <= 1; dy++) {
            long posSeed = Mth.getSeed(x, e.getValue().y() + dy, z);
            long[] v = {x, e.getValue().y() + dy, z};
            seeds.put((posSeed ^ 0x5DEECE66DL) & ((1L << 48) - 1L), v); // Legacy / SingleThreaded random sources
            seeds.put(net.minecraft.world.level.levelgen.RandomSupport.upgradeSeedTo128bit(posSeed).seedLo(), v); // Xoroshiro
         }
      }
      field = next;
      bySeed = seeds;
      dirty(old, next);
   }

   public static void clear() {
      Map<Long, Bend> old = field;
      field = Map.of();
      bySeed = Map.of();
      dirty(old, field);
   }

   private static void put(Map<Long, Bend> map, int x, int z, Bend b) {
      if (b.angle() <= 0.0F) {
         return;
      }
      long k = BlockPos.asLong(x, 0, z);
      Bend o = map.get(k);
      if (o == null || o.angle() < b.angle()) {
         map.put(k, b);
      }
   }

   /** 6 degree steps: a plant only changes (and its section only rebuilds) when the lean visibly changes */
   private static float quant(float a) {
      float step = (float)Math.toRadians(6.0);
      return Math.round(a / step) * step;
   }

   private static void dirty(Map<Long, Bend> a, Map<Long, Bend> b) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.levelRenderer == null || mc.level == null) {
         return;
      }
      Set<Long> keys = new HashSet<>(a.keySet());
      keys.addAll(b.keySet());
      Set<Long> sections = new HashSet<>();
      for (long k : keys) {
         Bend p = a.get(k), q = b.get(k);
         if (Objects.equals(p, q)) {
            continue;
         }
         int x = BlockPos.getX(k), z = BlockPos.getZ(k);
         int y = (p != null ? p : q).y();
         for (int dy = -1; dy <= 1; dy++) {
            sections.add(SectionPos.asLong(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(y + dy), SectionPos.blockToSectionCoord(z)));
         }
      }
      for (long s : sections) {
         mc.levelRenderer.setSectionDirty(SectionPos.x(s), SectionPos.y(s), SectionPos.z(s));
      }
   }
}
