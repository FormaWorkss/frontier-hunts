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
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * [onboard] Course 1 (curriculum) · The Archery Range. A bow line under a small shelter, the mod's own shooting
 * targets (they keep every arrow) at 10, 20, 30 and 40 yards, two 3D deer (broadside at 15 yd, quartering away at
 * 25 yd), a deer walking a lane at about 22 yd, and a ladder tree stand with a deer below it for the steep, elevated
 * shot. Wind flags stand down the range. The lent kit is the Field Recurve Bow, a compound bow, field-point arrows
 * for paper and broadhead arrows for the deer.
 *
 * <p>Everything is judged from the real bow and arrow: paper hits come from {@link TargetFace} (the arrow's own hit
 * record), deer hits from the whitetail's shot region (heart or lung drops it with the kill cam, anything else is
 * called out and the lane resets). Paper and the 3D / walking deer only count from behind the shooting line; the
 * stand deer only from the stand.
 */
final class ArcheryKit extends CourseKit {
   /** Paper targets: local x, z, yards. */
   static final int[][] PAPER = {{-7, -9, 10}, {-4, -18, 20}, {-1, -27, 30}, {2, -37, 40}};
   static final int LANE_DEER = 0, LANE_MOVING = 1, LANE_STAND = 2;
   /** 3D lanes: kind, local x, z, yaw, yards. Two static deer, the walking deer (start of its path), the stand deer. */
   static final int[][] LANES = {{LANE_DEER, 7, -14, 90, 15}, {LANE_DEER, 11, -23, 135, 25}, {LANE_MOVING, 15, -17, -90, 20}, {LANE_STAND, -22, -11, 65, 14}};
   static final int OBJ_PAPER = 0, OBJ_DEER = 1, OBJ_MOVING = 2, OBJ_STAND = 3;
   static final double LINE_Z = 0.6;
   /** Walking deer path along x at the lane's z: from x0 to x1, walking pace (1.3 m/s), a pause at each end. */
   static final double WALK_X0 = 14.5, WALK_X1 = 26.5, WALK_SPEED = 0.065;
   static final int WALK_PAUSE = 34;
   /** Tree stand: platform centre (local x, z) and its floor (dy); the shooter stands one block higher. */
   static final int STAND_X = -16, STAND_Z = 2, STAND_FLOOR = 5;

   ArcheryKit() {
      super(Course.ARCHERY);
   }

   static final class State {
      final Set<TargetFace.Hit> seen = new HashSet<>();
      final boolean[] paperDone = new boolean[PAPER.length];
      final boolean[] laneDone = new boolean[LANES.length];
      final UUID[] deer = new UUID[LANES.length];
      final int[] resetIn = {-1, -1, -1, -1};
      final boolean[] judged = new boolean[LANES.length];
      double walkX = WALK_X0;
      int walkDir = 1;
      int walkPause;
      boolean offLine;
      int offLineNoted;
      int score;
      boolean introduced;
      boolean broadheadNoted;
   }

   // ============================================================================================ pure scoring (harness)

   /** Ring name for a hit at normalized radius r (same rings as the paper face art). */
   static String ring(double r) {
      return r <= 0.25 ? "gold" : (r <= 0.44 ? "red" : (r <= 0.66 ? "blue" : (r <= 0.82 ? "black" : "white")));
   }

   static int points(String ring) {
      return switch (ring) {
         case "gold" -> 10;
         case "red" -> 8;
         case "blue" -> 6;
         case "black" -> 4;
         default -> 2;
      };
   }

   /** Does a paper hit at this radius qualify the target? Up close (10 / 20 yd) it must be inside the blue. */
   static boolean qualifies(int yards, double r) {
      return yards <= 20 ? r <= 0.66 : r <= 1.0;
   }

   /** Walking deer: next x along the path (and direction), with a pause at each end. Returns {x, dir, pause}. */
   static double[] walk(double x, int dir, int pause) {
      if (pause > 0) {
         return new double[]{x, dir, pause - 1};
      }
      double nx = x + dir * WALK_SPEED;
      if (nx >= WALK_X1) {
         return new double[]{WALK_X1, -1, WALK_PAUSE};
      }
      if (nx <= WALK_X0) {
         return new double[]{WALK_X0, 1, WALK_PAUSE};
      }
      return new double[]{nx, dir, 0};
   }

   static boolean onStand(double lx, double dy, double lz) {
      return dy >= STAND_FLOOR + 0.5 && Math.abs(lx - (STAND_X + 0.5)) <= 3.0 && lz >= STAND_Z - 2.5 && lz <= STAND_Z + 3.0;
   }

   // ============================================================================================ plot

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
      Scenery sc = new Scenery(p).keepClear(-12, -42, 30, 8).keepClear(-24, -14, -12, 0);
      BlockState log = Plot.block("pine_log", Blocks.SPRUCE_LOG.defaultBlockState());
      BlockState planks = Plot.block("pine_planks", Blocks.SPRUCE_PLANKS.defaultBlockState());
      BlockState slab = Plot.block("roof_slab", Blocks.SPRUCE_SLAB.defaultBlockState());
      BlockState fence = Plot.block("pine_fence", Blocks.SPRUCE_FENCE.defaultBlockState());
      BlockState stone = Plot.block("fieldstone", Blocks.COBBLESTONE.defaultBlockState());
      BlockState table = Plot.block("lodge_table", Blocks.SPRUCE_SLAB.defaultBlockState());
      // shooting line: a plank deck under a slab roof, a stone kerb on the line itself, a bow rack bench
      for (int x = -7; x <= 7; x++) {
         for (int z = 1; z <= 5; z++) {
            p.ground(x, z, planks);
         }
         p.ground(x, 0, stone);
      }
      for (int[] q : new int[][]{{-7, 1}, {-7, 5}, {7, 1}, {7, 5}}) {
         for (int y = 0; y <= 2; y++) {
            p.put(q[0], y, q[1], log);
         }
      }
      for (int x = -8; x <= 8; x++) {
         for (int z = 1; z <= 6; z++) {
            p.put(x, 3, z, slab);
         }
         p.put(x, 2, 5, Plot.axis(log, Direction.Axis.X)); // the beam sits at the back: nothing over the archer's arrow path
      }
      for (int x : new int[]{-5, -4, 4, 5}) {
         p.put(x, 0, 5, table);
      }
      p.put(-6, 0, 5, Plot.block("stacked_firewood", Blocks.BARREL.defaultBlockState()));
      p.put(6, 2, 2, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
      // mown shooting lanes: coarse dirt strips in front of every target, gravel yardage marks every 10 yards
      for (int[] t : PAPER) {
         for (int dx = -1; dx <= 1; dx++) {
            p.ground(t[0] + dx, t[1], Blocks.COARSE_DIRT.defaultBlockState());
            p.ground(t[0] + dx, t[1] + 1, Blocks.COARSE_DIRT.defaultBlockState());
         }
      }
      for (int[] t : PAPER) {
         for (int x : new int[]{-10, 30}) {
            p.ground(x, t[1], Blocks.GRAVEL.defaultBlockState());
         }
      }
      // paper target stands: plank pedestal, the target, fence posts as a frame
      for (int[] t : PAPER) {
         p.put(t[0], 0, t[1], planks);
         p.put(t[0], 1, t[1], target());
         p.put(t[0] - 1, 0, t[1], fence);
         p.put(t[0] + 1, 0, t[1], fence);
         p.put(t[0] - 1, 1, t[1], fence);
         p.put(t[0] + 1, 1, t[1], fence);
      }
      // 3D deer pads and the walking lane (a worn game trail)
      for (int[] l : LANES) {
         if (l[0] == LANE_MOVING) {
            continue;
         }
         for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               p.ground(l[1] + dx, l[2] + dz, Blocks.COARSE_DIRT.defaultBlockState());
            }
         }
      }
      for (int x = (int)WALK_X0 - 2; x <= (int)WALK_X1 + 2; x++) {
         p.ground(x, LANES[2][2], p.rndf() < 0.7F ? Blocks.COARSE_DIRT.defaultBlockState() : Plot.block("forest_duff", Blocks.PODZOL.defaultBlockState()));
      }
      // ladder tree stand: four log legs, plank floor, rail, ladder on the shooting side of the back
      int sx = STAND_X, sz = STAND_Z, fy = STAND_FLOOR;
      for (int[] q : new int[][]{{sx - 1, sz - 1}, {sx + 1, sz - 1}, {sx - 1, sz + 1}, {sx + 1, sz + 1}}) {
         for (int y = 0; y < fy; y++) {
            p.put(q[0], y, q[1], log);
         }
      }
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            p.put(sx + dx, fy, sz + dz, planks);
         }
      }
      for (int dx = -1; dx <= 1; dx++) {
         p.put(sx + dx, fy + 1, sz - 1, fence);
      }
      p.put(sx - 1, fy + 1, sz, fence);
      p.put(sx + 1, fy + 1, sz, fence);
      p.put(sx - 1, fy + 1, sz + 1, fence);
      p.put(sx + 1, fy + 1, sz + 1, fence);
      // the ladder climbs the back (south) face of the middle back leg block column
      for (int y = 0; y < fy; y++) {
         p.put(sx, y, sz + 1, log);
         p.put(sx, y, sz + 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
      }
      p.put(sx, fy, sz + 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
      // backstop berm across the end of the range
      for (int x = -30; x <= 30; x++) {
         int[] heights = {1, 2, 3, 4, 4, 3, 2};
         for (int i = 0; i < heights.length; i++) {
            int z = -44 - i;
            for (int y = 0; y < heights[i]; y++) {
               p.put(x, y, z, y == heights[i] - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState());
            }
         }
      }
      // wind flags down the range (between the stand lane and the paper)
      for (int z : new int[]{-12, -26, -40}) {
         for (int y = 0; y <= 2; y++) {
            p.put(-11, y, z, fence);
         }
         p.put(-11, 3, z, Blocks.RED_BANNER.defaultBlockState());
      }
      // scenery: pine belts on both sides and behind the berm, the stand backed by timber, meadow in the open
      sc.treeBelt(-30, -40, -30, 6, 6, 0.8F);
      sc.treeBelt(31, -40, 31, 6, 6, 0.7F);
      sc.treeBelt(-28, -52, 28, -52, 5, 0.8F);
      for (int[] t : new int[][]{{-19, 6}, {-25, 2}, {-27, -6}, {-26, -18}}) {
         sc.tree(t[0], t[1], 0.9F);
      }
      sc.meadow(-31, -53, 31, 9, 0.06F, 0.02F, 0.003F);
      p.boundary(-32, 32, -54, 10, 5);
   }

   private static BlockState target() {
      BlockState t = Plot.block("shooting_target", Blocks.TARGET.defaultBlockState());
      return Plot.facing(t, Direction.SOUTH);
   }

   // ============================================================================================ session

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
         label(s, level, t[0] + 0.5, 2.9, t[1] + 0.5, "academy.frontierhunts.archery.sign.paper" + t[2], t[2] + " YD", 1.1F + t[2] / 60.0F, 0x88201810,
            "vertical");
      }
      for (int i = 0; i < LANES.length; i++) {
         this.spawnLane(s, level, st, i);
      }
      label(s, level, LANES[0][1] + 2.5, 2.4, LANES[0][2] + 0.5, "academy.frontierhunts.archery.sign.deer15", "3D DEER · 15 YD", 1.2F, 0x88201810, "vertical");
      label(s, level, LANES[1][1] + 2.5, 2.4, LANES[1][2] + 0.5, "academy.frontierhunts.archery.sign.deer25", "3D DEER · 25 YD", 1.4F, 0x88201810, "vertical");
      label(s, level, (WALK_X0 + WALK_X1) / 2.0, 3.0, LANES[2][2] - 1.5, "academy.frontierhunts.archery.sign.moving", "WALKING DEER · 20 YD", 1.4F,
         0x88201810, "vertical");
      label(s, level, STAND_X + 0.5, STAND_FLOOR + 3.2, STAND_Z + 0.5, "academy.frontierhunts.archery.sign.stand", "TREE STAND · climb the ladder", 0.7F,
         0x99201810, "vertical");
      label(s, level, 0.5, 2.4, 1.3, "academy.frontierhunts.archery.sign.line", "SHOOTING LINE", 0.6F, 0x99201810, "fixed");
      for (int z : new int[]{-12, -26, -40}) {
         s.banners.add(s.block(-11, 3, z));
      }
   }

   private void spawnLane(Session s, ServerLevel level, State st, int i) {
      int[] l = LANES[i];
      boolean buck = level.random.nextBoolean();
      double x = l[0] == LANE_MOVING ? st.walkX : l[1] + 0.5;
      float yaw = l[0] == LANE_MOVING ? (st.walkDir > 0 ? -90.0F : 90.0F) : l[3];
      Whitetail d = deer(s, level, x, l[2] + 0.5, yaw, false,
         traits(buck, 30 + level.random.nextInt(50), 50 + level.random.nextInt(40), buck ? 60 + level.random.nextInt(50) : 0, level.random.nextInt(),
            level.random.nextInt(100)));
      st.deer[i] = d == null ? null : d.getUUID();
      st.judged[i] = false;
   }

   @Override
   void tick(Session s, ServerLevel level, ServerPlayer p) {
      State st = (State)s.state;
      if (st == null) {
         return;
      }
      if (!st.introduced && s.ticks >= 50) {
         st.introduced = true;
         TrainingService.note(s, p, "academy.frontierhunts.archery.intro");
      }
      if (s.ticks % 40 == 0) {
         flags(s, level);
      }
      double lx = s.lx(p.position()), lz = s.lz(p.position()), dy = p.getY() - s.origin.getY();
      boolean onLine = lz >= LINE_Z;
      boolean onStand = onStand(lx, dy, lz);
      if (onLine != !st.offLine) {
         st.offLine = !onLine;
         s.hudFlags = st.offLine ? s.hudFlags | 2 : s.hudFlags & ~2;
         s.hudDirty = true;
      }
      for (int i = 0; i < LANES.length; i++) {
         this.lane(s, level, p, st, i, onLine, onStand);
      }
      if (s.ticks % 4 == 0) {
         this.paper(s, level, p, st, onLine);
      }
   }

   private void lane(Session s, ServerLevel level, ServerPlayer p, State st, int i, boolean onLine, boolean onStand) {
      int[] l = LANES[i];
      if (st.resetIn[i] >= 0) {
         if (--st.resetIn[i] == 0) {
            st.resetIn[i] = -1;
            remove(level, st.deer[i]);
            s.entities.remove(st.deer[i]);
            this.spawnLane(s, level, st, i);
         }
         return;
      }
      Whitetail d = find(level, st.deer[i]);
      if (d == null) {
         this.spawnLane(s, level, st, i);
         return;
      }
      if (st.judged[i]) {
         return;
      }
      boolean hit = d.downed() || d.bleeding() || d.getHealth() < d.getMaxHealth() - 0.01F;
      if (!hit) {
         if (l[0] == LANE_MOVING) {
            double[] w = walk(st.walkX, st.walkDir, st.walkPause);
            st.walkX = w[0];
            st.walkDir = (int)w[1];
            st.walkPause = (int)w[2];
            float yaw = st.walkDir > 0 ? -90.0F : 90.0F;
            var pos = s.world(st.walkX, 0.0, l[2] + 0.5);
            d.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
            d.setYBodyRot(yaw);
            d.setYHeadRot(yaw);
         }
         return;
      }
      st.judged[i] = true;
      String region = d.shotRegion() == null ? "BODY" : d.shotRegion();
      String pretty = region.toLowerCase(Locale.ROOT).replace('_', ' ');
      int obj = switch (l[0]) {
         case LANE_MOVING -> OBJ_MOVING;
         case LANE_STAND -> OBJ_STAND;
         default -> OBJ_DEER;
      };
      if (l[0] == LANE_STAND && !onStand) {
         TrainingService.note(s, p, "academy.frontierhunts.archery.stand_only");
         st.resetIn[i] = 60;
      } else if (l[0] != LANE_STAND && !onLine) {
         TrainingService.note(s, p, "academy.frontierhunts.range.off_line");
         st.resetIn[i] = 60;
      } else if (d.downed()) {
         if (!st.laneDone[i]) {
            st.laneDone[i] = true;
            st.score += 10;
            s.bump(obj, 1);
            TrainingService.tick(s, p, obj, "academy.frontierhunts.archery." + (l[0] == LANE_MOVING ? "moving_clean" : l[0] == LANE_STAND ? "stand_clean" : "deer_clean"),
               pretty, Integer.toString(l[4]), Integer.toString(st.score));
         } else {
            TrainingService.note(s, p, "academy.frontierhunts.range.deer_again", pretty);
         }
         st.resetIn[i] = 140; // the kill cam and the collapse play out first
      } else {
         String verdict = verdict(region);
         String key = l[0] == LANE_MOVING && !"chest".equals(verdict) ? "academy.frontierhunts.archery.moving_miss"
            : l[0] == LANE_STAND ? "academy.frontierhunts.archery.stand_miss" : "academy.frontierhunts.range.deer_" + verdict;
         TrainingService.note(s, p, key, pretty);
         st.resetIn[i] = 70;
      }
   }

   private void paper(Session s, ServerLevel level, ServerPlayer p, State st, boolean onLine) {
      for (int k = 0; k < PAPER.length; k++) {
         int[] t = PAPER[k];
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
            String ring = ring(r);
            int pts = points(ring);
            st.score += pts;
            String yards = Integer.toString(t[2]);
            if (!st.broadheadNoted && h.tip() >= 0 && com.formaworks.frontierhunts.hunting.ArrowTip.byOrdinal(h.tip()).broadhead()) {
               st.broadheadNoted = true; // still counts; a one-time reminder which tip goes where
               TrainingService.note(s, p, "academy.frontierhunts.archery.broadhead_paper");
            }
            if (!st.paperDone[k] && qualifies(t[2], r)) {
               st.paperDone[k] = true;
               s.bump(OBJ_PAPER, 1);
               TrainingService.tick(s, p, OBJ_PAPER, "academy.frontierhunts.archery.paper_" + ring, yards, Integer.toString(pts), Integer.toString(st.score));
            } else {
               TrainingService.note(s, p, !st.paperDone[k] ? "academy.frontierhunts.archery.paper_wide" : "academy.frontierhunts.archery.paper_" + ring, yards,
                  Integer.toString(pts), Integer.toString(st.score));
            }
         }
      }
   }

   static String verdict(String region) {
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
