package com.formaworks.frontierhunts.academy;

import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * [academy] Course 2 · The Stalk. An open meadow with broken cover and a feeding doe at ~75 m. Close to 15 m and hold
 * there for three seconds while she stays calm. If she winds you or sees you move she bolts: you are walked back to
 * the start mark and a fresh doe settles in (attempts are counted, there is no limit inside the time).
 */
final class StalkKit extends CourseKit {
   private static final double DOE_X = 0.0, DOE_Z = -74.0;
   private static final double CLOSE = 15.0;
   private static final float CALM = 0.45F;
   private static final float BUSTED = 0.72F;

   StalkKit() {
      super(Course.STALK);
   }

   static final class State {
      UUID doe;
      int hold;
      int resetIn = -1;
      boolean winded;
      int warned;
   }

   @Override
   int version() {
      return 1;
   }

   @Override
   float[] arrival() {
      return new float[]{0.5F, 0.0F, 6.5F, 180.0F, 0.0F};
   }

   @Override
   void build(Plot p) {
      Scenery sc = new Scenery(p).keepClear(-3, 2, 3, 10).keepClear(-4, -78, 4, -70);
      // start mark: a trodden patch with a wind flag
      for (int x = -2; x <= 2; x++) {
         for (int z = 4; z <= 9; z++) {
            p.ground(x, z, p.rndf() < 0.6F ? Blocks.COARSE_DIRT.defaultBlockState() : Plot.block("forest_duff", Blocks.PODZOL.defaultBlockState()));
         }
      }
      BlockState fence = Plot.block("pine_fence", Blocks.SPRUCE_FENCE.defaultBlockState());
      p.put(3, 0, 5, fence);
      p.put(3, 1, 5, fence);
      p.put(3, 2, 5, Blocks.ORANGE_BANNER.defaultBlockState());
      // cover: meadow grass drifts, shrub knots in broken lines, deadfall and boulders; open ground near the doe
      sc.meadow(-40, -94, 40, 2, 0.20F, 0.03F, 0.0F);
      int[][] lines = {{-30, -10, -8, -30}, {12, -18, 30, -40}, {-24, -42, -6, -58}, {6, -50, 22, -62}, {-36, -66, -20, -84}, {26, -70, 34, -88}};
      for (int[] l : lines) {
         int n = 6;
         for (int i = 0; i <= n; i++) {
            int x = l[0] + (l[2] - l[0]) * i / n + p.rnd(-1, 1);
            int z = l[1] + (l[3] - l[1]) * i / n + p.rnd(-1, 1);
            if (p.rndf() < 0.8F) {
               sc.clump(x, z, p.rnd(1, 2));
            }
         }
      }
      for (int i = 0; i < 9; i++) {
         sc.deadfall(p.rnd(-36, 36), p.rnd(-66, -8));
      }
      for (int i = 0; i < 8; i++) {
         sc.boulder(p.rnd(-36, 36), p.rnd(-86, -8));
      }
      int[][] lone = {{-18, -26}, {20, -30}, {-12, -60}, {16, -78}, {-28, -54}, {30, -10}};
      for (int[] t : lone) {
         sc.tree(t[0], t[1], 0.6F);
      }
      sc.treeBelt(-40, -94, 40, -94, 5, 0.75F);
      sc.treeBelt(-40, -88, -40, 6, 6, 0.7F);
      sc.treeBelt(40, -88, 40, 6, 6, 0.7F);
      p.boundary(-44, 44, -98, 12, 5);
   }

   @Override
   void begin(Session s, ServerLevel level) {
      State st = new State();
      s.state = st;
      s.banners.add(s.block(3, 2, 5));
      spawnDoe(s, level, st);
      label(s, level, 0.5, 2.4, 3.0, "academy.frontierhunts.sign.stalk", "START MARK · watch the wind", 0.55F, 0x99201810, "vertical");
   }

   private void spawnDoe(Session s, ServerLevel level, State st) {
      Whitetail d = deer(s, level, DOE_X + level.random.nextInt(7) - 3, DOE_Z + level.random.nextInt(5) - 2, level.random.nextFloat() * 360.0F, true,
         traits(false, 30 + level.random.nextInt(40), 40 + level.random.nextInt(30), 0, level.random.nextInt(), 40 + level.random.nextInt(30)));
      st.doe = d == null ? null : d.getUUID();
   }

   @Override
   void tick(Session s, ServerLevel level, ServerPlayer p) {
      State st = (State)s.state;
      if (st == null) {
         return;
      }
      if (s.ticks % 40 == 0) {
         flags(s, level);
      }
      if (st.resetIn >= 0) {
         if (--st.resetIn == 0) {
            st.resetIn = -1;
            remove(level, st.doe);
            s.entities.remove(st.doe);
            spawnDoe(s, level, st);
            st.hold = 0;
            s.set(1, 0);
            s.set(0, 0);
            TrainingService.toStart(s, p);
         }
         return;
      }
      Whitetail d = find(level, st.doe);
      if (d == null) {
         spawnDoe(s, level, st);
         return;
      }
      if (s.ticks % 2 != 0) {
         return;
      }
      float alert = d.alertness();
      boolean fleeing = d.behavior() == Whitetail.BEHAVIOR_FLEEING || d.behavior() == Whitetail.BEHAVIOR_CHARGE;
      double dist = horizontal(p.position(), d.position());
      if (alert >= BUSTED || fleeing) {
         s.attempts++;
         st.resetIn = 60;
         st.hold = 0;
         Wilderness.Wind w = wind(level);
         Vec3 to = d.position().subtract(p.position());
         double sp = Math.max(1.0E-3, Math.hypot(w.east(), w.south()));
         double along = (to.x * w.east() + to.z * w.south()) / (sp * Math.max(1.0E-3, Math.hypot(to.x, to.z)));
         boolean scent = along > 0.45 && dist < 70.0;
         TrainingService.busted(s, p, scent ? "academy.frontierhunts.stalk.winded" : "academy.frontierhunts.stalk.seen");
         return;
      }
      if (dist <= CLOSE && alert < CALM) {
         if (!s.done(0)) {
            s.set(0, 1);
            TrainingService.tick(s, p, 0, "academy.frontierhunts.stalk.close", Integer.toString((int)Math.round(dist)));
         }
         st.hold += 2;
         int secs = st.hold / 20;
         if (secs > s.progress[1]) {
            s.set(1, secs);
            if (secs >= Course.STALK.targets[1]) {
               TrainingService.tick(s, p, 1, "academy.frontierhunts.stalk.held", Integer.toString((int)Math.round(dist)));
            }
         }
      } else if (st.hold > 0 && !s.done(1)) {
         st.hold = 0;
         s.set(1, 0);
      }
      if (alert > 0.3F && alert < BUSTED && st.warned < s.ticks - 100) {
         st.warned = s.ticks;
         TrainingService.note(s, p, "academy.frontierhunts.stalk.alert");
      }
   }
}
