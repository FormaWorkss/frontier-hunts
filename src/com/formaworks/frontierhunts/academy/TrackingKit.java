package com.formaworks.frontierhunts.academy;

import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.tracking.BloodTrail;
import com.formaworks.frontierhunts.tracking.TrailMark;
import com.formaworks.frontierhunts.tracking.TrailStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * [academy] Course 4 · The Blood Trail. A woodland lane where a buck was hit an hour ago, quartering away, behind the
 * ribs. A scripted liver-blood trail (real trail marks, read with the mod's own sign reading) winds ~75 m through
 * pines and undergrowth, with a bed where it lay down and a pool where it lies. Read the hit site, follow the blood,
 * find the bed, recover the buck.
 */
final class TrackingKit extends CourseKit {
   /** Trail control points (local x, z), hit site first, the buck last. Index 4 is the bed. */
   static final double[][] PATH = {{0, 0}, {3, -7}, {-1, -15}, {-8, -22}, {-10, -30}, {-6, -39}, {3, -46}, {5, -54}, {-2, -61}, {-7, -67}};
   private static final int BED = 4;
   private static final double SITE = 4.5;

   TrackingKit() {
      super(Course.TRACKING);
   }

   static final class State {
      UUID deer;
      UUID bed;
      final Set<UUID> read = new HashSet<>();
   }

   @Override
   int version() {
      return 1;
   }

   @Override
   float[] arrival() {
      return new float[]{0.5F, 0.0F, 6.5F, 180.0F, 10.0F};
   }

   /** Catmull-Rom samples along the path, ~0.5 block apart. */
   static List<double[]> samples() {
      List<double[]> out = new ArrayList<>();
      for (int i = 0; i < PATH.length - 1; i++) {
         double[] p0 = PATH[Math.max(0, i - 1)], p1 = PATH[i], p2 = PATH[i + 1], p3 = PATH[Math.min(PATH.length - 1, i + 2)];
         double len = Math.hypot(p2[0] - p1[0], p2[1] - p1[1]);
         int n = Math.max(2, (int)Math.ceil(len / 0.5));
         for (int k = 0; k < n; k++) {
            double t = (double)k / n, t2 = t * t, t3 = t2 * t;
            double x = 0.5 * (2 * p1[0] + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3);
            double z = 0.5 * (2 * p1[1] + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3);
            out.add(new double[]{x, z, i});
         }
      }
      out.add(new double[]{PATH[PATH.length - 1][0], PATH[PATH.length - 1][1], PATH.length - 1});
      return out;
   }

   @Override
   void build(Plot p) {
      Scenery sc = new Scenery(p).keepClear(-4, -3, 4, 10);
      List<double[]> trail = samples();
      for (double[] q : trail) {
         int x = (int)Math.floor(q[0]), z = (int)Math.floor(q[1]);
         sc.keepClear(x - 2, z - 2, x + 2, z + 2);
      }
      double[] end = PATH[PATH.length - 1];
      sc.keepClear((int)end[0] - 4, (int)end[1] - 4, (int)end[0] + 4, (int)end[1] + 4);
      // woodland floor everywhere; plants only off the trail
      sc.forestFloor(-32, -82, 32, 12, 0.62F, 0.22F);
      // pines, firs and a few aspens, denser toward the back
      for (int i = 0; i < 120; i++) {
         int x = p.rnd(-31, 31), z = p.rnd(-80, 4);
         if (!sc.blocked(x, z) && !sc.blocked(x + 1, z) && !sc.blocked(x - 1, z) && !sc.blocked(x, z + 1) && !sc.blocked(x, z - 1)
            && p.free(x, 0, z) && p.free(x + 1, 0, z) && p.free(x - 1, 0, z)) {
            sc.tree(x, z, 0.78F);
            sc.keepClear(x - 1, z - 1, x + 1, z + 1);
         }
      }
      for (int i = 0; i < 22; i++) {
         sc.deadfall(p.rnd(-30, 30), p.rnd(-80, 2));
      }
      for (int i = 0; i < 18; i++) {
         sc.clump(p.rnd(-30, 30), p.rnd(-80, 2), p.rnd(1, 2));
      }
      for (int i = 0; i < 10; i++) {
         sc.boulder(p.rnd(-30, 30), p.rnd(-80, 2));
      }
      // the hit site: an old stump and a trail sign
      p.put(2, 0, 2, Plot.facing(Plot.block("trail_sign", Blocks.SPRUCE_FENCE.defaultBlockState()), Direction.SOUTH));
      p.put(-2, 0, 1, Plot.block("forest_sticks", Blocks.AIR.defaultBlockState()));
      p.boundary(-34, 34, -84, 14, 5);
   }

   @Override
   void begin(Session s, ServerLevel level) {
      State st = new State();
      s.state = st;
      double[] end = PATH[PATH.length - 1];
      double[] prev = PATH[PATH.length - 2];
      float yaw = (float)Math.toDegrees(Math.atan2(-(end[0] - prev[0]), end[1] - prev[1]));
      Whitetail d = deer(s, level, end[0] + 0.5, end[1] + 0.5, yaw, false, traits(true, 70, 82, 118, level.random.nextInt(), 58));
      if (d != null) {
         st.deer = d.getUUID();
         down(level, d);
         // the death fall leaves its own blood: take it under the session's care so it is cleared afterwards
         for (TrailMark m : TrailStore.get(level).nearby(d.position(), 3.0, 64, level.getGameTime())) {
            if (m.animal().equals(d.getUUID())) {
               s.marks.add(m.id());
            }
         }
      }
      UUID animal = d == null ? UUID.randomUUID() : d.getUUID();
      String who = d == null ? "Whitetail · large buck" : describe(d);
      int liver = BloodTrail.BloodType.LIVER.ordinal();
      long now = level.getGameTime();
      long shot = now - 1100L;
      RandomSource rnd = RandomSource.create(s.slot * 7919L + now);
      List<double[]> trail = samples();
      double walked = 0.0, next = 0.0;
      double[] last = trail.get(0);
      // hit site: impact burst and spray
      blood(s, level, PATH[0][0] + 0.5, PATH[0][1] + 0.5, TrailMark.IMPACT, 0.38F, liver, animal, who, shot, 180.0F);
      blood(s, level, PATH[0][0] + 1.3, PATH[0][1] - 0.4, TrailMark.DRIP, 0.22F, liver, animal, who, shot, 180.0F);
      blood(s, level, PATH[0][0] - 0.4, PATH[0][1] - 0.9, TrailMark.DENSE, 0.3F, liver, animal, who, shot + 2, 180.0F);
      boolean bedDone = false;
      for (int i = 1; i < trail.size(); i++) {
         double[] q = trail.get(i);
         walked += Math.hypot(q[0] - last[0], q[1] - last[1]);
         float yaw2 = (float)Math.toDegrees(Math.atan2(-(q[0] - last[0]), q[1] - last[1]));
         last = q;
         if (!bedDone && (int)q[2] >= BED) {
            bedDone = true;
            double[] b = PATH[BED];
            TrailMark bed = blood(s, level, b[0] + 0.5, b[1] + 0.5, TrailMark.BED, 0.45F, liver, animal, who, shot + 260, yaw2);
            st.bed = bed == null ? null : bed.id();
            blood(s, level, b[0] + 1.6, b[1] - 0.3, TrailMark.POOL, 0.36F, liver, animal, who, shot + 300, yaw2);
            next = walked + 2.5;
            continue;
         }
         if (walked < next || i > trail.size() - 3) {
            continue;
         }
         boolean early = walked < 9.0;
         boolean afterBed = (int)q[2] > BED;
         next = walked + (early ? 1.4 : (afterBed ? 2.8 + rnd.nextDouble() * 2.2 : 2.0 + rnd.nextDouble() * 1.6));
         if (!early && rnd.nextFloat() < 0.12F) {
            continue; // a gap: the trail is lost for a few steps here
         }
         double jx = (rnd.nextDouble() - 0.5) * 1.1, jz = (rnd.nextDouble() - 0.5) * 1.1;
         int style = early ? TrailMark.DENSE : TrailMark.DRIP;
         float radius = early ? 0.28F : 0.2F + rnd.nextFloat() * 0.06F;
         blood(s, level, q[0] + 0.5 + jx, q[1] + 0.5 + jz, style, radius, liver, animal, who, shot + (long)(walked * 4.0), yaw2);
      }
      blood(s, level, end[0] + 1.2, end[1] + 0.9, TrailMark.POOL, 0.42F, liver, animal, who, shot + 600, yaw);
      label(s, level, 0.5, 2.2, 0.5, "academy.frontierhunts.sign.hit_site", "HIT SITE · buck, quartering away", 0.55F, 0x99201810, "vertical");
   }

   @Override
   void inspected(Session s, ServerLevel level, ServerPlayer p, TrailMark m) {
      State st = (State)s.state;
      if (st == null || !s.marks.contains(m.id())) {
         return;
      }
      boolean fresh = st.read.add(m.id());
      double fromSite = Math.hypot(s.lx(m.position()) - (PATH[0][0] + 0.5), s.lz(m.position()) - (PATH[0][1] + 0.5));
      if (m.id().equals(st.bed) || m.style() == TrailMark.BED || m.style() == TrailMark.POOL && fromSite > 20.0 && fromSite < 45.0) {
         if (!s.done(2)) {
            s.set(2, 1);
            TrainingService.tick(s, p, 2, "academy.frontierhunts.tracking.bed");
         }
         return;
      }
      if (fromSite <= SITE) {
         if (!s.done(0)) {
            s.set(0, 1);
            TrainingService.tick(s, p, 0, "academy.frontierhunts.tracking.site");
         }
         return;
      }
      if (fresh && !s.done(1)) {
         s.bump(1, 1);
         TrainingService.tick(s, p, 1, "academy.frontierhunts.tracking.read", Integer.toString(s.progress[1]), Integer.toString(Course.TRACKING.targets[1]));
      }
   }

   @Override
   void tick(Session s, ServerLevel level, ServerPlayer p) {
      State st = (State)s.state;
      if (st == null || s.ticks % 5 != 0 || s.done(3)) {
         return;
      }
      Whitetail d = find(level, st.deer);
      Vec3 at = d != null ? d.position() : s.world(PATH[PATH.length - 1][0] + 0.5, 0.0, PATH[PATH.length - 1][1] + 0.5);
      double dist = horizontal(p.position(), at);
      if (dist <= 3.5) {
         s.set(3, 1);
         TrainingService.tick(s, p, 3, "academy.frontierhunts.tracking.recovered", Integer.toString(Math.max(1, (int)Math.round(trailLength()))));
      }
   }

   static double trailLength() {
      double l = 0;
      List<double[]> t = samples();
      for (int i = 1; i < t.size(); i++) {
         l += Math.hypot(t.get(i)[0] - t.get(i - 1)[0], t.get(i)[1] - t.get(i - 1)[1]);
      }
      return l;
   }
}
