package com.formaworks.frontierhunts.academy;

import com.formaworks.frontierhunts.expedition.ShootingTarget;
import com.formaworks.frontierhunts.expedition.TargetFace;
import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * [academy] Course 3 · The Range. A covered firing line, paper targets at 25, 50 and 100 m (the mod's own shooting
 * targets, which keep every hole), wind flags down the range and two 3D deer lanes at 50 and 100 m with live
 * whitetails standing broadside: a heart or lung hit drops them with the kill cam, anything else is called out
 * ("gut - it would have run") and the lane resets. Shots only count from behind the firing line.
 */
final class RangeKit extends CourseKit {
   /** Paper targets: local x, z, objective index. */
   private static final int[][] PAPER = {{-12, -25, 0}, {-7, -50, 1}, {-2, -100, 2}};
   /** Deer lanes: local x, z, objective index. */
   private static final int[][] LANES = {{6, -50, 3}, {12, -100, 4}};
   private static final double LINE_Z = 0.6;

   RangeKit() {
      super(Course.RANGE);
   }

   static final class State {
      final Set<TargetFace.Hit> seen = new HashSet<>();
      final UUID[] deer = new UUID[2];
      final int[] resetIn = {-1, -1};
      final boolean[] judged = new boolean[2];
      boolean offLine;
      int offLineNoted;
   }

   @Override
   int version() {
      return 1;
   }

   @Override
   float[] arrival() {
      return new float[]{0.5F, 0.0F, 3.5F, 180.0F, 0.0F};
   }

   @Override
   void build(Plot p) {
      Scenery sc = new Scenery(p).keepClear(-20, -112, 20, 8);
      BlockState log = Plot.block("pine_log", Blocks.SPRUCE_LOG.defaultBlockState());
      BlockState planks = Plot.block("pine_planks", Blocks.SPRUCE_PLANKS.defaultBlockState());
      BlockState slab = Plot.block("roof_slab", Blocks.SPRUCE_SLAB.defaultBlockState());
      BlockState fence = Plot.block("pine_fence", Blocks.SPRUCE_FENCE.defaultBlockState());
      BlockState stone = Plot.block("fieldstone", Blocks.COBBLESTONE.defaultBlockState());
      // firing line pavilion: plank floor, log posts, shingle-slab roof, benches at the line
      for (int x = -9; x <= 9; x++) {
         for (int z = 1; z <= 6; z++) {
            p.ground(x, z, planks);
         }
         p.ground(x, 0, stone);
      }
      int[][] posts = {{-9, 1}, {-9, 6}, {9, 1}, {9, 6}, {-3, 6}, {3, 6}};
      for (int[] q : posts) {
         for (int y = 0; y <= 3; y++) {
            p.put(q[0], y, q[1], log);
         }
      }
      for (int x = -10; x <= 10; x++) {
         for (int z = 0; z <= 7; z++) {
            p.put(x, 4, z, slab);
         }
         p.put(x, 3, 1, Plot.axis(log, Direction.Axis.X));
      }
      BlockState table = Plot.block("lodge_table", Blocks.SPRUCE_SLAB.defaultBlockState());
      for (int x : new int[]{-7, -6, -1, 0, 1, 6, 7}) {
         p.put(x, 0, 1, table);
      }
      p.put(-8, 0, 5, Plot.block("stacked_firewood", Blocks.BARREL.defaultBlockState()));
      p.put(8, 0, 5, Plot.block("cabin_lantern", Blocks.LANTERN.defaultBlockState()));
      p.put(-8, 3, 2, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
      // range floor: mown lanes with gravel lane edges every 25 m
      for (int z = -104; z <= -1; z++) {
         for (int x : new int[]{-15, -4, 3, 16}) {
            if (z % 25 == 0) {
               p.ground(x, z, Blocks.GRAVEL.defaultBlockState());
            }
         }
      }
      for (int x = -16; x <= 16; x++) {
         p.ground(x, -25, p.rndf() < 0.3F ? Blocks.COARSE_DIRT.defaultBlockState() : p.level.getBlockState(p.at(x, -1, -25)));
      }
      // paper target stands: plank pedestal, the target, two fence posts as a frame
      for (int[] t : PAPER) {
         p.put(t[0], 0, t[1], planks);
         p.put(t[0], 1, t[1], target());
         p.put(t[0] - 1, 0, t[1], fence);
         p.put(t[0] + 1, 0, t[1], fence);
         p.put(t[0] - 1, 1, t[1], fence);
         p.put(t[0] + 1, 1, t[1], fence);
         for (int dx = -1; dx <= 1; dx++) {
            p.ground(t[0] + dx, t[1], Blocks.COARSE_DIRT.defaultBlockState());
         }
      }
      // deer lanes: a mown strip and a low marker post
      for (int[] l : LANES) {
         for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               p.ground(l[0] + dx, l[1] + dz, Blocks.COARSE_DIRT.defaultBlockState());
            }
         }
         p.put(l[0] + 3, 0, l[1], fence);
      }
      // backstop berm across the end of the range
      for (int x = -22; x <= 22; x++) {
         int[] heights = {1, 2, 3, 4, 4, 4, 3, 2};
         for (int i = 0; i < heights.length; i++) {
            int z = -106 - i;
            for (int y = 0; y < heights[i]; y++) {
               p.put(x, y, z, y == heights[i] - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState());
            }
         }
      }
      // wind flags down the range
      for (int z : new int[]{-25, -60, -95}) {
         for (int y = 0; y <= 2; y++) {
            p.put(0, y, z, fence);
         }
         p.put(0, 3, z, Blocks.RED_BANNER.defaultBlockState());
      }
      // scenery outside the lanes: tree belts on both sides and behind the berm
      sc.treeBelt(-23, -100, -23, 4, 6, 0.75F);
      sc.treeBelt(23, -100, 23, 4, 6, 0.75F);
      sc.treeBelt(-22, -116, 22, -116, 5, 0.8F);
      sc.meadow(-25, -120, 25, 10, 0.05F, 0.02F, 0.002F);
      p.boundary(-26, 26, -122, 12, 5);
   }

   private static BlockState target() {
      BlockState t = Plot.block("shooting_target", Blocks.TARGET.defaultBlockState());
      return Plot.facing(t, Direction.SOUTH);
   }

   @Override
   void begin(Session s, ServerLevel level) {
      State st = new State();
      s.state = st;
      for (int[] t : PAPER) {
         BlockPos pos = s.block(t[0], 1, t[1]);
         if (!(level.getBlockState(pos).getBlock() instanceof ShootingTarget)) {
            level.setBlock(pos, target(), Block.UPDATE_CLIENTS);
         }
         if (level.getBlockEntity(pos) instanceof TargetFace f) {
            f.wipe();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_CLIENTS);
         }
         label(s, level, t[0] + 0.5, 2.9, t[1] + 0.5, "academy.frontierhunts.sign.paper" + (-t[1]), (-t[1]) + " m", 1.3F + (-t[1]) / 60.0F, 0x88201810, "vertical");
      }
      for (int i = 0; i < LANES.length; i++) {
         spawnLane(s, level, st, i);
         int[] l = LANES[i];
         label(s, level, l[0] + 3.5, 2.6, l[1] + 0.5, "academy.frontierhunts.sign.deer" + (-l[1]), "DEER · " + (-l[1]) + " m", 1.2F + (-l[1]) / 70.0F, 0x88201810,
            "vertical");
      }
      for (int z : new int[]{-25, -60, -95}) {
         s.banners.add(s.block(0, 3, z));
      }
      label(s, level, 0.5, 3.35, 1.3, "academy.frontierhunts.sign.firing_line", "FIRING LINE", 0.6F, 0x99201810, "fixed");
   }

   private void spawnLane(Session s, ServerLevel level, State st, int i) {
      int[] l = LANES[i];
      boolean buck = level.random.nextBoolean();
      Whitetail d = deer(s, level, l[0] + 0.5, l[1] + 0.5, 90.0F, false,
         traits(buck, 40 + level.random.nextInt(50), 50 + level.random.nextInt(40), buck ? 70 + level.random.nextInt(50) : 0, level.random.nextInt(), level.random.nextInt(100)));
      st.deer[i] = d == null ? null : d.getUUID();
      st.judged[i] = false;
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
      boolean onLine = s.lz(p.position()) >= LINE_Z;
      if (onLine != !st.offLine) {
         st.offLine = !onLine;
         s.hudFlags = st.offLine ? s.hudFlags | 2 : s.hudFlags & ~2;
         s.hudDirty = true;
      }
      // lanes: a vital hit drops the deer (kill cam), anything else is judged and the lane resets
      for (int i = 0; i < LANES.length; i++) {
         if (st.resetIn[i] >= 0) {
            if (--st.resetIn[i] == 0) {
               st.resetIn[i] = -1;
               remove(level, st.deer[i]);
               s.entities.remove(st.deer[i]);
               spawnLane(s, level, st, i);
            }
            continue;
         }
         Whitetail d = find(level, st.deer[i]);
         if (d == null) {
            spawnLane(s, level, st, i);
            continue;
         }
         if (st.judged[i]) {
            continue;
         }
         boolean hit = d.downed() || d.bleeding() || d.getHealth() < d.getMaxHealth() - 0.01F;
         if (!hit) {
            continue;
         }
         st.judged[i] = true;
         String region = d.shotRegion() == null ? "BODY" : d.shotRegion();
         String pretty = region.toLowerCase(Locale.ROOT).replace('_', ' ');
         int obj = LANES[i][2];
         if (!onLine) {
            TrainingService.note(s, p, "academy.frontierhunts.range.off_line");
            st.resetIn[i] = 60;
         } else if (d.downed()) {
            boolean fresh = !s.done(obj);
            s.set(obj, 1);
            if (fresh) {
               TrainingService.tick(s, p, obj, "academy.frontierhunts.range.deer_clean", pretty, Integer.toString(-LANES[i][1]));
            } else {
               TrainingService.note(s, p, "academy.frontierhunts.range.deer_again", pretty);
            }
            st.resetIn[i] = 140; // let the kill cam and the collapse play out
         } else {
            TrainingService.note(s, p, "academy.frontierhunts.range.deer_" + verdict(region), pretty);
            st.resetIn[i] = 70;
         }
      }
      // paper: new holes since the session began, only from the line
      if (s.ticks % 4 == 0) {
         for (int[] t : PAPER) {
            BlockPos pos = s.block(t[0], 1, t[1]);
            if (!(level.getBlockEntity(pos) instanceof TargetFace f)) {
               continue;
            }
            for (TargetFace.Hit h : f.hits()) {
               if (h.at() < s.started || !st.seen.add(h)) {
                  continue;
               }
               if (!onLine) {
                  if (st.offLineNoted < s.ticks - 60) {
                     st.offLineNoted = s.ticks;
                     TrainingService.note(s, p, "academy.frontierhunts.range.off_line");
                  }
                  continue;
               }
               double r = Math.sqrt(h.u() * h.u() + h.v() * h.v());
               String ring = r <= 0.25 ? "gold" : (r <= 0.44 ? "red" : (r <= 0.66 ? "blue" : (r <= 0.82 ? "black" : "white")));
               int metres = -t[1];
               Vec3 at = Vec3.atCenterOf(pos);
               TrainingService.ring(p, at, r <= 0.25);
               int obj = t[2];
               boolean counts = obj != 1 || r <= 0.66;
               if (counts && !s.done(obj)) {
                  s.bump(obj, 1);
                  TrainingService.tick(s, p, obj, "academy.frontierhunts.range.paper_" + ring, Integer.toString(metres));
               } else {
                  TrainingService.note(s, p, obj == 1 && !counts ? "academy.frontierhunts.range.paper_low" : "academy.frontierhunts.range.paper_" + ring,
                     Integer.toString(metres));
               }
            }
         }
      }
   }

   private static String verdict(String region) {
      return switch (region) {
         case "LIVER" -> "liver";
         case "GUT" -> "gut";
         case "NECK", "HEAD", "BRAIN", "SPINE" -> "neck";
         case "LEG", "SHOULDER" -> "leg";
         case "CHEST", "LUNG" -> "chest";
         default -> "body";
      };
   }

   @Override
   void end(Session s, ServerLevel level) {
      for (int[] t : PAPER) {
         BlockPos pos = s.block(t[0], 1, t[1]);
         if (level.getBlockEntity(pos) instanceof TargetFace f) {
            f.wipe();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_CLIENTS);
         }
      }
   }
}
