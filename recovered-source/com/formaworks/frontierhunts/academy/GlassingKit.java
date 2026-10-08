package com.formaworks.frontierhunts.academy;

import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * [academy] Course 1 · Glass & Call. A ranger's lookout tower over a wide meadow with a buck and two does feeding at
 * 40-100 m. Find all three through the binoculars (hold on each for a moment), range the buck, then call him in to
 * the foot of the tower with the grunt tube or bleat.
 */
final class GlassingKit extends CourseKit {
   private static final double[][] DEER = {{18.0, -72.0, 90.0}, {-26.0, -58.0, -60.0}, {-6.0, -96.0, 30.0}};
   private static final double STAND_X = 0.0, STAND_Z = -9.0;
   private static final double CALL_RANGE = 25.0;

   GlassingKit() {
      super(Course.GLASSING);
   }

   static final class State {
      final UUID[] deer = new UUID[3];
      final int[] hold = new int[3];
      final boolean[] spotted = new boolean[3];
      int rangeHold;
      boolean called;
      long calledAt = -10000L;
      long lastNudge = -10000L;
      long lastCall = -10000L;
   }

   @Override
   int version() {
      return 1;
   }

   @Override
   float[] arrival() {
      return new float[]{0.0F, 6.0F, 0.5F, 180.0F, 4.0F};
   }

   @Override
   void build(Plot p) {
      Scenery sc = new Scenery(p).keepClear(-4, -4, 4, 6).keepClear(-2, 6, 2, 12);
      BlockState log = Plot.block("pine_log", Blocks.SPRUCE_LOG.defaultBlockState());
      BlockState planks = Plot.block("pine_planks", Blocks.SPRUCE_PLANKS.defaultBlockState());
      BlockState fence = Plot.block("pine_fence", Blocks.SPRUCE_FENCE.defaultBlockState());
      BlockState slab = Plot.block("roof_slab", Blocks.SPRUCE_SLAB.defaultBlockState());
      // ground around the tower: trodden earth and a gravel walk to the south
      for (int x = -4; x <= 4; x++) {
         for (int z = -4; z <= 4; z++) {
            if (x * x + z * z <= 18) {
               p.ground(x, z, p.rndf() < 0.5F ? Blocks.COARSE_DIRT.defaultBlockState() : Plot.block("forest_duff", Blocks.PODZOL.defaultBlockState()));
            }
         }
      }
      for (int z = 3; z <= 12; z++) {
         p.ground(0, z, Blocks.GRAVEL.defaultBlockState());
         if (p.rndf() < 0.6F) {
            p.ground(z % 2 == 0 ? 1 : -1, z, Blocks.COARSE_DIRT.defaultBlockState());
         }
      }
      // lookout tower: four log legs, braced, a plank deck at +5 with rails and a slab roof
      for (int y = 0; y <= 9; y++) {
         p.put(-2, y, -2, log);
         p.put(2, y, -2, log);
         p.put(-2, y, 2, log);
         p.put(2, y, 2, log);
      }
      for (int x = -2; x <= 2; x++) {
         for (int z = -2; z <= 2; z++) {
            p.put(x, 5, z, planks);
         }
      }
      for (int i = -1; i <= 1; i++) {
         p.put(i, 6, -2, fence);
         p.put(-2, 6, i, fence);
         p.put(2, 6, i, fence);
      }
      p.put(-1, 6, 2, fence);
      p.put(1, 6, 2, fence);
      for (int x = -3; x <= 3; x++) {
         for (int z = -3; z <= 3; z++) {
            p.put(x, 10, z, slab);
         }
      }
      // cross braces under the deck
      p.put(-1, 2, -2, Plot.axis(log, Direction.Axis.X));
      p.put(0, 2, -2, Plot.axis(log, Direction.Axis.X));
      p.put(1, 2, -2, Plot.axis(log, Direction.Axis.X));
      p.put(-2, 2, -1, Plot.axis(log, Direction.Axis.Z));
      p.put(-2, 2, 0, Plot.axis(log, Direction.Axis.Z));
      p.put(-2, 2, 1, Plot.axis(log, Direction.Axis.Z));
      p.put(2, 2, -1, Plot.axis(log, Direction.Axis.Z));
      p.put(2, 2, 0, Plot.axis(log, Direction.Axis.Z));
      p.put(2, 2, 1, Plot.axis(log, Direction.Axis.Z));
      // ladder up the south face, backed by a post
      BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
      for (int y = 0; y <= 4; y++) {
         p.put(0, y, 2, planks);
         p.put(0, y, 3, ladder);
      }
      p.put(0, 5, 3, ladder);
      // lantern on the deck corner and a stool
      p.put(1, 6, -1, Plot.block("cabin_lantern", Blocks.LANTERN.defaultBlockState()));
      p.put(-1, 6, 1, Plot.block("lodge_chair", Blocks.AIR.defaultBlockState()));
      // the meadow: drifts of grass and flowers, shrub knots, a few boulders and lone trees
      sc.meadow(-58, -104, 58, 8, 0.10F, 0.035F, 0.004F);
      for (int i = 0; i < 26; i++) {
         sc.clump(p.rnd(-56, 56), p.rnd(-100, -14), p.rnd(1, 2));
      }
      for (int i = 0; i < 10; i++) {
         sc.boulder(p.rnd(-56, 56), p.rnd(-100, -10));
      }
      int[][] lone = {{-34, -44}, {38, -36}, {44, -88}, {-46, -80}, {-14, -70}, {28, -100}, {-30, -102}, {10, -40}};
      for (int[] t : lone) {
         sc.tree(t[0], t[1], 0.5F);
      }
      sc.treeBelt(-58, -110, 58, -110, 6, 0.8F);
      sc.treeBelt(-58, -104, 58, -104, 9, 0.75F);
      sc.treeBelt(-58, -100, -58, 6, 7, 0.7F);
      sc.treeBelt(58, -100, 58, 6, 7, 0.7F);
      p.boundary(-62, 62, -116, 12, 5);
   }

   @Override
   void begin(Session s, ServerLevel level) {
      State st = new State();
      s.state = st;
      st.deer[0] = id(deer(s, level, DEER[0][0], DEER[0][1], (float)DEER[0][2], true, traits(true, 66, 78, 112, 41, 55)));
      st.deer[1] = id(deer(s, level, DEER[1][0], DEER[1][1], (float)DEER[1][2], true, traits(false, 44, 52, 0, 7, 62)));
      st.deer[2] = id(deer(s, level, DEER[2][0], DEER[2][1], (float)DEER[2][2], true, traits(false, 30, 46, 0, 19, 48)));
      label(s, level, 0.0, 7.6, -2.6, "academy.frontierhunts.sign.glassing", "LOOKOUT · glass, range, call", 0.55F, 0x99201810, "fixed");
   }

   private static UUID id(Whitetail w) {
      return w == null ? null : w.getUUID();
   }

   @Override
   void tick(Session s, ServerLevel level, ServerPlayer p) {
      State st = (State)s.state;
      if (st == null || s.ticks % 2 != 0) {
         return;
      }
      boolean finder = usingItem(p, "rangefinder");
      boolean glass = usingItem(p, "binoculars") || finder;
      long now = level.getGameTime();
      for (int i = 0; i < 3; i++) {
         Whitetail d = find(level, st.deer[i]);
         if (d == null) {
            continue;
         }
         Vec3 aim = d.position().add(0.0, d.getBbHeight() * 0.65, 0.0);
         double dist = p.getEyePosition().distanceTo(aim);
         if (glass && !st.spotted[i]) {
            double tolerance = Math.max(2.2, 5.5 - dist * 0.03);
            if (dist < 180.0 && lookAngle(p, aim) < tolerance && sight(level, p, aim)) {
               st.hold[i] += 2;
               if (st.hold[i] >= 14) {
                  st.spotted[i] = true;
                  s.bump(0, 1);
                  TrainingService.tick(s, p, 0, "academy.frontierhunts.glassing.spotted", describe(d), Integer.toString((int)Math.round(dist)));
               }
            } else {
               st.hold[i] = 0;
            }
         }
         if (i == 0 && finder && !s.done(1)) {
            if (lookAngle(p, aim) < 3.2 && sight(level, p, aim)) {
               st.rangeHold += 2;
               if (st.rangeHold >= 8) {
                  s.set(1, 1);
                  TrainingService.tick(s, p, 1, "academy.frontierhunts.glassing.ranged", Integer.toString((int)Math.round(dist)));
               }
            } else {
               st.rangeHold = 0;
            }
         }
         if (i == 0 && st.called && !s.done(2)) {
            Vec3 stand = s.world(STAND_X, 0.0, STAND_Z);
            double h = horizontal(d.position(), s.world(0.0, 0.0, 0.0));
            if (h <= CALL_RANGE) {
               s.set(2, 1);
               TrainingService.tick(s, p, 2, "academy.frontierhunts.glassing.came_in", Integer.toString((int)Math.round(h)));
            } else if (now - st.calledAt < 1800L && now - st.lastNudge >= 60L && !d.approachingCall()) {
               st.lastNudge = now;
               if (!d.approachCall(stand, 1.3F, false)) {
                  d.getNavigation().moveTo(stand.x, stand.y, stand.z, 0.8);
               }
            }
         }
      }
      if (!finder) {
         st.rangeHold = 0;
      }
   }

   @Override
   void usedItem(Session s, ServerLevel level, ServerPlayer p, ItemStack stack) {
      State st = (State)s.state;
      String id = itemId(stack);
      if (st == null || !(id.equals("grunt_tube") || id.equals("bleat_call") || id.equals("deer_call") || id.equals("rattling_antlers"))) {
         return;
      }
      long now = level.getGameTime();
      if (now - st.lastCall < 20L) {
         return;
      }
      st.lastCall = now;
      if (!s.done(2)) {
         boolean first = !st.called;
         st.called = true;
         st.calledAt = now;
         st.lastNudge = now - 40L;
         if (first) {
            TrainingService.note(s, p, "academy.frontierhunts.glassing.called");
         }
      }
   }
}
