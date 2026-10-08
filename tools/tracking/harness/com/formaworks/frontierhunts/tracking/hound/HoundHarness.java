package com.formaworks.frontierhunts.tracking.hound;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/**
 * [hound3] Offline check of the hound's working mind ({@link HoundBrain}) on a block grid: a wounded deer's path with
 * scent points + blood, a scent gap (rock), a creek (slow water cells), a rock ledge the dog can't cross (must route
 * round / skip ahead), a dead-end loop the deer doubled back out of, a hunter who walks behind (and stops for a while:
 * the dog must wait for him), and the deer dead or bedded alive at the end. SEARCH: fresh deer line and the deer
 * upwind ~80 blocks off - the dog must strike, run it and point short of the deer without spooking it.
 *
 * <p>Movement model ~ the entity: think every 10 ticks, steer at {@code speed * 0.2} blocks/tick (water 0.5x), blocked
 * cells stop it and 50 ticks without 0.8 blocks of progress report "stuck" (TrackingHound.drive).
 */
public final class HoundHarness {
   static int fails;
   static final boolean TRACE = System.getenv("TRACE") != null;
   static final int W = 220;
   static boolean[][] wall = new boolean[W][W];
   static boolean[][] water = new boolean[W][W];

   static void check(boolean ok, String what) {
      System.out.println((ok ? "PASS " : "FAIL ") + what);
      if (!ok) {
         fails++;
      }
   }

   static boolean blocked(Vec3 p) {
      int x = (int)Math.floor(p.x), z = (int)Math.floor(p.z);
      return x < 0 || z < 0 || x >= W || z >= W || wall[x][z];
   }

   /** 8-connected grid BFS from a to b within a 64-block box; cell centres, or null (no path) */
   static List<Vec3> bfs(Vec3 a, Vec3 b) {
      int ax = (int)Math.floor(a.x), az = (int)Math.floor(a.z), bx = (int)Math.floor(b.x), bz = (int)Math.floor(b.z);
      if (blocked(b) || Math.abs(ax - bx) > 64 || Math.abs(az - bz) > 64) {
         return null;
      }
      int[] prev = new int[W * W];
      java.util.Arrays.fill(prev, -2);
      java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
      int start = ax * W + az, goal = bx * W + bz;
      prev[start] = -1;
      q.add(start);
      while (!q.isEmpty()) {
         int c = q.poll();
         if (c == goal) {
            break;
         }
         int cx = c / W, cz = c % W;
         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               int nx = cx + dx, nz = cz + dz;
               if ((dx | dz) == 0 || nx < 0 || nz < 0 || nx >= W || nz >= W || wall[nx][nz] || Math.abs(nx - ax) > 64 || Math.abs(nz - az) > 64) {
                  continue;
               }
               if (dx != 0 && dz != 0 && (wall[cx + dx][cz] || wall[cx][cz + dz])) {
                  continue;
               }
               int n = nx * W + nz;
               if (prev[n] == -2) {
                  prev[n] = c;
                  q.add(n);
               }
            }
         }
      }
      if (prev[goal] == -2) {
         return null;
      }
      List<Vec3> out = new ArrayList<>();
      for (int c = goal; c != -1; c = prev[c]) {
         out.add(0, new Vec3(c / W + 0.5, 64, c % W + 0.5));
      }
      out.set(out.size() - 1, b);
      return out;
   }

   static boolean wet(Vec3 p) {
      int x = (int)Math.floor(p.x), z = (int)Math.floor(p.z);
      return x >= 0 && z >= 0 && x < W && z < W && water[x][z];
   }

   /** a polyline sampled at the ledger's 1.6 m spacing, 8 ticks apart (a wounded deer's lope); [gapFrom, gapTo) dropped */
   static List<HoundBrain.Waypoint> path(double[][] pts, long t0, boolean endFlag, int gapFrom, int gapTo) {
      List<HoundBrain.Waypoint> out = new ArrayList<>();
      long t = t0;
      int idx = 0;
      for (int s = 0; s + 1 < pts.length; s++) {
         Vec3 a = new Vec3(pts[s][0], 64, pts[s][1]), b = new Vec3(pts[s + 1][0], 64, pts[s + 1][1]);
         int n = Math.max(1, (int)Math.round(a.distanceTo(b) / 1.6));
         for (int k = 0; k < n; k++) {
            Vec3 p = a.lerp(b, k / (double)n);
            if (idx < gapFrom || idx >= gapTo) {
               out.add(new HoundBrain.Waypoint(p, t, false));
            }
            idx++;
            t += 8;
         }
      }
      double[] l = pts[pts.length - 1];
      out.add(new HoundBrain.Waypoint(new Vec3(l[0], 64, l[1]), t, endFlag));
      return out;
   }

   static final class Result {
      int ticks;
      int finalMode = -1;
      boolean waited, waitedWhileHunterStopped, bayed, found, pointed, strike;
      int maxGoalFlips, stuckReports;
      double closest = 1e9, pointDist = -1, maxLead;
   }

   static Result run(HoundBrain b, List<HoundBrain.Waypoint> line, Vec3 quarry, boolean down, Vec3 hound, Vec3 owner, long now, int limit,
      int stopFrom, int stopTo, List<HoundBrain.Game> game, Vec3 wind) {
      Result r = new Result();
      HoundBrain.Plan plan = null;
      Vec3 progressAt = null;
      double progressDist = 0;
      int progressTicks = 0;
      boolean stuck = false;
      Vec3 lastGoalDir = null;
      int flips = 0;
      List<Vec3> route = null;
      Vec3 routeGoal = null;
      int routeIdx = 0;
      for (int t = 0; t < limit; t++) {
         now++;
         if (t % 10 == 0) {
            HoundBrain.Sense s = new HoundBrain.Sense();
            s.now = now;
            s.hound = hound;
            s.owner = owner;
            s.ownerLook = new Vec3(1, 0, 0);
            s.wind = wind;
            s.stuck = stuck;
            stuck = false;
            s.swimming = wet(hound);
            boolean searching = b.mode == HoundBrain.SEARCH || b.mode == HoundBrain.STRIKE || b.mode == HoundBrain.POINT;
            // gather(): the quarry's line from the cursor on, capped at 200 like the entity
            List<HoundBrain.Waypoint> l = new ArrayList<>();
            if (!searching || b.quarry != null) {
               for (HoundBrain.Waypoint w : line) {
                  if (w.time() >= b.cursor && l.size() < 200) {
                     l.add(w);
                  }
               }
            }
            s.line = l;
            if (quarry != null && (!searching || b.quarry != null)) {
               s.quarryPos = quarry;
               s.quarryDown = down;
               s.quarryVisible = quarry.distanceTo(hound) < 30;
               s.quarryWounded = !down && !searching;
            }
            if (game != null) {
               s.game = game;
            }
            plan = b.plan(s);
            r.finalMode = plan.mode;
            if (TRACE && t % 100 == 0) {
               System.out.printf("  t=%4d hound %.1f,%.1f owner %.1f,%.1f mode %d goal %s speed %.2f wait %s cursor %d line %d%n", t, hound.x, hound.z, owner.x,
                  owner.z, plan.mode, plan.goal == null ? "-" : String.format("%.1f,%.1f", plan.goal.x, plan.goal.z), plan.speed, plan.waiting, b.cursor, l.size());
               HoundBrain.Step st = HoundBrain.advance(l, hound, b.cursor);
               System.out.println("    step reached=" + st.reached() + " next=" + st.next() + " gap=" + st.gap() + " first=" + (l.isEmpty() ? null : l.get(0)));
            }
            if (plan.mode == HoundBrain.LOST || plan.mode == HoundBrain.HEEL) {
               r.ticks = t;
               return r;
            }
            if (plan.waiting) {
               r.waited = true;
               r.waitedWhileHunterStopped |= t >= stopFrom && t < stopTo;
            }
            r.bayed |= plan.mode == HoundBrain.BAY;
            r.found |= plan.mode == HoundBrain.FOUND;
            r.strike |= plan.mode == HoundBrain.STRIKE;
            if (plan.mode == HoundBrain.POINT && !r.pointed) {
               r.pointed = true;
               r.pointDist = quarry == null ? -1 : quarry.distanceTo(hound);
            }
            if (s.stuck) {
               r.stuckReports++;
            }
            // spinning: the goal flipping back and forth think after think while running the line
            if (plan.goal != null && plan.mode == HoundBrain.TRACK) {
               Vec3 d = plan.goal.subtract(hound);
               d = new Vec3(d.x, 0, d.z);
               if (d.lengthSqr() > 1) {
                  d = d.normalize();
                  if (lastGoalDir != null && d.dot(lastGoalDir) < -0.5) {
                     r.maxGoalFlips = Math.max(r.maxGoalFlips, ++flips);
                  } else {
                     flips = Math.max(0, flips - 1);
                  }
                  lastGoalDir = d;
               }
            }
            if (plan.mode == HoundBrain.FOUND && quarry != null && owner.distanceTo(quarry) < 4.0) {
               r.ticks = t;
               return r;
            }
            if ((plan.mode == HoundBrain.POINT || plan.mode == HoundBrain.BAY) && owner.distanceTo(hound) < 6.0 && t > 200) {
               r.ticks = t;
               return r;
            }
         }
         // move the hound
         if (plan != null && plan.goal != null && !plan.waiting) {
            Vec3 d = plan.goal.subtract(hound);
            d = new Vec3(d.x, 0, d.z);
            double len = d.length();
            if (len > 0.9) {
               // the navigator: a grid path (like PathNavigation, search limited to ~64 blocks), else straight at it
               if (route == null || routeGoal == null || routeGoal.distanceToSqr(plan.goal) > 2.25 || t % 40 == 0) {
                  route = bfs(hound, plan.goal);
                  routeGoal = plan.goal;
                  routeIdx = 0;
               }
               Vec3 aim = plan.goal;
               if (route != null) {
                  while (routeIdx < route.size() - 1 && HoundBrain.horiz(route.get(routeIdx), hound) < 0.6 * 0.6) {
                     routeIdx++;
                  }
                  aim = route.get(routeIdx);
               }
               Vec3 dd = new Vec3(aim.x - hound.x, 0, aim.z - hound.z);
               double ll = dd.length();
               double sp = plan.speed * 0.2 * (wet(hound) ? 0.5 : 1.0);
               if (ll > 1.0E-6) {
                  Vec3 next = hound.add(dd.scale(Math.min(ll, sp) / ll));
                  if (!blocked(next)) {
                     hound = next;
                  }
               }
            }
            if (progressAt == null || progressAt.distanceToSqr(plan.goal) > 4.0) {
               progressAt = plan.goal;
               progressDist = len;
               progressTicks = 0;
            } else if (len < progressDist - 0.8) {
               progressDist = len;
               progressTicks = 0;
            } else if (len >= 0.9 && ++progressTicks > 50) {
               progressTicks = 0;
               progressDist = len;
               stuck = true;
            }
         }
         // the hunter follows the bawl at a walk (0.17 b/t), ~6 blocks behind, unless he stops; goes round walls
         if (!(t >= stopFrom && t < stopTo)) {
            Vec3 d = hound.subtract(owner);
            d = new Vec3(d.x, 0, d.z);
            boolean walkUp = plan != null && (plan.mode == HoundBrain.FOUND || plan.mode == HoundBrain.BAY || plan.mode == HoundBrain.POINT);
            if (d.length() > (walkUp ? 2.0 : 6.0)) {
               Vec3 next = owner.add(d.normalize().scale(0.17));
               owner = blocked(next) ? owner.add(new Vec3(0, 0, 1).scale(0.17)) : next;
            }
         }
         r.maxLead = Math.max(r.maxLead, Math.sqrt(HoundBrain.horiz(hound, owner)));
         if (quarry != null) {
            r.closest = Math.min(r.closest, hound.distanceTo(quarry));
         }
      }
      r.ticks = limit;
      return r;
   }

   public static void main(String[] a) {
      UUID deer = new UUID(1, 2);
      // the creek: x 70..74 across the whole map
      for (int x = 70; x < 75; x++) {
         for (int z = 0; z < W; z++) {
            water[x][z] = true;
         }
      }
      // a rock ledge across the line at x = 120..121, z 20..46 (the deer jumped down it; the dog must go round)
      for (int z = 20; z < 47; z++) {
         wall[120][z] = true;
         wall[121][z] = true;
      }
      // the wounded deer's run: hit at (10,30); a dead-end loop into a thicket and back out; through the creek; over the
      // ledge; a ~22-block scent gap (bare rock) after it; then a bend to its bed at (178, 80)
      double[][] run = {{10, 30}, {30, 32}, {44, 40}, {52, 52}, {46, 56}, {44, 44}, {56, 36}, {72, 34}, {96, 30}, {118, 33},
         {124, 34}, {140, 38}, {150, 52}, {160, 66}, {172, 76}, {178, 80}};
      long t0 = 1000;
      List<HoundBrain.Waypoint> line = path(run, t0, true, 85, 99);
      long now = line.get(line.size() - 1).time() + 1200; // put on it a minute after the deer lay down
      Vec3 bed = new Vec3(178, 64, 80);
      System.out.println("line points: " + line.size());

      // 1. dead deer: trail it all the way, wait for a hunter who stops for 40 s, stand over it
      HoundBrain b = new HoundBrain();
      b.seed(7);
      b.track(deer, t0 - 40, new Vec3(10, 64, 30), new Vec3(8, 64, 28));
      Result r = run(b, line, bed, true, new Vec3(8, 64, 28), new Vec3(6, 64, 28), now, 20 * 60 * 6, 300, 1100, null, Vec3.ZERO);
      System.out.printf("dead deer: mode %d after %d s, closest %.1f, waited %s (while hunter stopped %s), stuck %d, flips %d, lead %.0f%n",
         r.finalMode, r.ticks / 20, r.closest, r.waited, r.waitedWhileHunterStopped, r.stuckReports, r.maxGoalFlips, r.maxLead);
      check(r.found && r.finalMode == HoundBrain.FOUND, "dead deer: through the loop, creek, ledge and scent gap to FOUND");
      check(r.closest < 2.5, "dead deer: stands over it");
      check(r.waitedWhileHunterStopped, "dead deer: waits, looking back, when the hunter stops");
      check(r.maxLead < HoundBrain.WAIT_AT + 8, "dead deer: never more than ~38 blocks ahead of the hunter (" + Math.round(r.maxLead) + ")");
      check(r.maxGoalFlips < 3, "dead deer: never spins (goal flips " + r.maxGoalFlips + ")");
      check(r.ticks < 20 * 60 * 5, "dead deer: done within 5 minutes (" + r.ticks / 20 + " s)");

      // 2. alive, bedded and wounded: bays it up from a few metres
      b = new HoundBrain();
      b.seed(8);
      b.track(deer, t0 - 40, new Vec3(10, 64, 30), new Vec3(8, 64, 28));
      r = run(b, path(run, t0, false, 85, 99), bed, false, new Vec3(8, 64, 28), new Vec3(6, 64, 28), now, 20 * 60 * 6, -1, -1, null, Vec3.ZERO);
      System.out.printf("alive deer: bayed %s closest %.1f after %d s%n", r.bayed, r.closest, r.ticks / 20);
      check(r.bayed, "alive deer: bays it up");
      check(r.closest > 2.5 && r.closest < 6.0, "alive deer: bays from a few metres off (" + String.format("%.1f", r.closest) + ")");

      // 3. the line runs out and the animal is not loaded: casts, gives up and comes back - never hangs
      b = new HoundBrain();
      b.seed(9);
      b.track(deer, t0 - 40, new Vec3(10, 64, 30), new Vec3(8, 64, 28));
      r = run(b, new ArrayList<>(line.subList(0, 40)), null, false, new Vec3(8, 64, 28), new Vec3(6, 64, 28), now, 20 * 60 * 6, -1, -1, null, Vec3.ZERO);
      System.out.printf("dead end: mode %d after %d s%n", r.finalMode, r.ticks / 20);
      check(r.finalMode == HoundBrain.LOST, "dead end: casts, gives up and comes back");
      check(r.ticks < 20 * 150, "dead end: gives up within 2.5 minutes (" + r.ticks / 20 + " s)");

      // 4. SEARCH: a fresh deer line crossing ahead + the deer ~80 blocks off, upwind
      Vec3 deerAt = new Vec3(150, 64, 120);
      List<HoundBrain.Waypoint> fresh = path(new double[][]{{90, 150}, {110, 135}, {130, 125}, {150, 120}}, 20000, false, 999, 999);
      long sNow = fresh.get(fresh.size() - 1).time() + 600;
      List<HoundBrain.Game> game = List.of(new HoundBrain.Game(deer, deerAt, false, false, fresh));
      b = new HoundBrain();
      b.seed(10);
      b.search();
      r = run(b, fresh, deerAt, false, new Vec3(70, 64, 120), new Vec3(66, 64, 120), sNow, 20 * 60 * 4, -1, -1, game, new Vec3(-1, 0, 0));
      System.out.printf("search: strike %s point %s at %.1f m after %d s, closest %.1f%n", r.strike, r.pointed, r.pointDist, r.ticks / 20, r.closest);
      check(r.strike, "search: strikes fresh deer scent");
      check(r.pointed && r.pointDist > 8.0 && r.pointDist <= HoundBrain.POINT_SEEN + 1, "search: points short of the deer, not on top of it");
      check(r.closest > 8.0, "search: never runs in and spooks the deer (closest " + String.format("%.1f", r.closest) + ")");

      // 5. geometry helpers
      List<HoundBrain.Waypoint> l = path(new double[][]{{0, 0}, {16, 0}}, 0, false, 999, 999);
      HoundBrain.Step st = HoundBrain.advance(l, new Vec3(5, 64, 0.4), 0);
      check(st.next() != null && st.next().pos().x > 5, "advance: skips points already passed");
      check(HoundBrain.pickup(l, new Vec3(8, 64, 5), 6, 0) != null, "pickup: finds the line within the cast radius");
      System.out.println(fails == 0 ? "HOUND HARNESS: ALL PASS" : "HOUND HARNESS: " + fails + " FAILED");
      System.exit(fails == 0 ? 0 : 1);
   }
}
