package com.formaworks.frontierhunts.tracking;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * [tracking] The scent line a hound's nose follows: every tracked animal's path as a bounded ring of timed points,
 * laid wherever the animal walks (rock and grass included, where no print shows). Server memory only: after a
 * restart the hound falls back to the saved prints and blood in {@link TrailStore} and casts across the gaps.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class ScentLedger {
   /** metres between points */
   public static final double SPACING = 1.6;
   public static final int PER_ANIMAL = 640;
   public static final int ANIMALS = 1536;
   /** scent older than this is gone */
   public static final long MAX_AGE = 36000L;
   private static final Map<ResourceKey<Level>, ScentLedger> LEDGERS = new LinkedHashMap<>();

   public record Point(double x, double y, double z, long time, boolean end) {
      public Vec3 pos() {
         return new Vec3(this.x, this.y, this.z);
      }
   }

   private final LinkedHashMap<UUID, ArrayDeque<Point>> trails = new LinkedHashMap<>(256, 0.75F, true);

   public static synchronized ScentLedger get(ServerLevel level) {
      return LEDGERS.computeIfAbsent(level.dimension(), k -> new ScentLedger());
   }

   /** records a point if the animal moved far enough from its last one */
   public synchronized void step(UUID animal, Vec3 pos, long now) {
      ArrayDeque<Point> q = this.trails.get(animal);
      if (q == null) {
         if (this.trails.size() >= ANIMALS) {
            Iterator<UUID> it = this.trails.keySet().iterator();
            it.next();
            it.remove();
         }
         q = new ArrayDeque<>();
         this.trails.put(animal, q);
      } else {
         Point last = q.peekLast();
         if (last != null && !last.end && last.pos().distanceToSqr(pos) < SPACING * SPACING && now - last.time < 600L) {
            return;
         }
      }
      q.addLast(new Point(pos.x, pos.y, pos.z, now, false));
      while (q.size() > PER_ANIMAL || !q.isEmpty() && now - q.peekFirst().time > MAX_AGE) {
         q.removeFirst();
      }
   }

   /** where the animal died or dropped: the end of its line */
   public synchronized void end(UUID animal, Vec3 pos, long now) {
      ArrayDeque<Point> q = this.trails.computeIfAbsent(animal, k -> new ArrayDeque<>());
      q.addLast(new Point(pos.x, pos.y, pos.z, now, true));
      while (q.size() > PER_ANIMAL) {
         q.removeFirst();
      }
   }

   /** the animal's points made at or after {@code from}, oldest first */
   public synchronized List<Point> after(UUID animal, long from) {
      ArrayDeque<Point> q = this.trails.get(animal);
      List<Point> out = new ArrayList<>();
      if (q != null) {
         for (Point p : q) {
            if (p.time >= from) {
               out.add(p);
            }
         }
      }
      return out;
   }

   /** [hound3] animals whose newest scent point lies within {@code r} of {@code at} and is younger than {@code maxAge} */
   public synchronized List<Map.Entry<UUID, Point>> recent(Vec3 at, double r, long now, long maxAge) {
      List<Map.Entry<UUID, Point>> out = new ArrayList<>();
      for (Map.Entry<UUID, ArrayDeque<Point>> e : this.trails.entrySet()) {
         Point p = e.getValue().peekLast();
         if (p != null && now - p.time <= maxAge && p.pos().distanceToSqr(at) <= r * r) {
            out.add(Map.entry(e.getKey(), p));
         }
      }
      return out;
   }

   public synchronized Point last(UUID animal) {
      ArrayDeque<Point> q = this.trails.get(animal);
      return q == null ? null : q.peekLast();
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent e) {
      synchronized (ScentLedger.class) {
         LEDGERS.clear();
      }
   }
}
