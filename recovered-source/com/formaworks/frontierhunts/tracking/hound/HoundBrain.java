package com.formaworks.frontierhunts.tracking.hound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/**
 * [hound3] The tracking hound's working mind, free of world access so it can be driven offline
 * (tools/tracking/harness HoundHarness): the entity gathers what the dog can sense ({@link Sense}: its quarry's scent
 * line = the animal's own path, blood and prints in time order; the quarry itself; fresh game lines around it; the
 * hunter) every half second, and this decides where the nose goes next ({@link #plan}).
 *
 * <p>TRACK: run the line in time order, a few waypoints ahead (smooth, no stop-and-go), faster and louder as it gets
 * fresh; CAST in a widening, forward-biased spiral where it breaks (gaps, water, rock), pick it up again anywhere
 * near; skip ahead when a stretch can't be reached (cliffs, water the dog can't climb out of); wait, looking back,
 * when the hunter falls behind; BAY a live wounded animal at a few metres; FOUND: stand over a dead one and bawl
 * until the hunter walks up. SEARCH: range ahead of the hunter quartering the wind; strike fresh game scent on the
 * ground or on the wind (~64-96 blocks downwind), run it and freeze on POINT short of the animal so it isn't spooked.
 */
public final class HoundBrain {
   public static final int HEEL = 0, SIT = 1, TRACK = 2, CAST = 3, BAY = 4, FOUND = 5, RETREAT = 6, LOST = 7, SEARCH = 8, STRIKE = 9, POINT = 10,
      STOP = 11; // [hound4] stopped: stands still where he is (TrackingHound only; the brain never enters it)
   /** voice events */
   public static final int V_NONE = 0, V_SNIFF = 1, V_BAWL = 2, V_CHOP = 3, V_WHINE = 4, V_STRIKE = 5;
   /** give up a track after this long (10 in-game minutes) */
   public static final int TRACK_LIMIT = 12000;
   /** the hunter is "left behind" beyond this; the dog waits until they are within RESUME */
   public static final double WAIT_AT = 30.0, RESUME = 15.0;
   /** point this far short of game it has winded / run up on */
   public static final double POINT_SEEN = 26.0, POINT_BLIND = 15.0;
   /** fresh enough to strike in SEARCH */
   public static final long STRIKE_AGE = 7200L;

   public record Waypoint(Vec3 pos, long time, boolean end) {
   }

   /** one fresh game line (SEARCH) */
   public record Game(UUID id, Vec3 pos, boolean visible, boolean fleeing, List<Waypoint> line) {
   }

   public static final class Sense {
      public long now;
      public Vec3 hound;
      public Vec3 owner;
      /** horizontal facing of the hunter (unit), for quartering ahead of them */
      public Vec3 ownerLook = new Vec3(0, 0, 1);
      /** the quarry's scent line from the cursor on (time order) */
      public List<Waypoint> line = List.of();
      /** the quarry if loaded */
      public Vec3 quarryPos;
      public boolean quarryVisible, quarryDown, quarryWounded, quarryFleeing;
      /** wind (direction it blows toward, any length) */
      public Vec3 wind = Vec3.ZERO;
      /** fresh lines of game around (SEARCH) */
      public List<Game> game = List.of();
      /** the executor could not make progress toward the last goal */
      public boolean stuck;
      /** the hound is in water */
      public boolean swimming;
   }

   // ----------------------------------------------------------------------------------------- state
   public int mode = HEEL;
   public UUID quarry;
   public long cursor;
   public Vec3 lastReached;
   public Vec3 heading = Vec3.ZERO;
   private Vec3 castCenter;
   private double castAngle;
   private int castTicks, castLimit;
   private boolean reachedEnd;
   private boolean approach;
   private boolean waiting;
   private int trackTicks;
   private int skips;
   private int baySince;
   private int pointTicks, fleeTicks;
   private int legTicks, leg;
   private int windTicks;
   private int nextVoice, nextSniff;
   private float excite;
   public Vec3 foundAt;
   private boolean foundDead;
   private int foundTicks;
   private final Map<UUID, Long> ignore = new HashMap<>();
   private long seed = 1L;

   /** the result of one think */
   public static final class Plan {
      public int mode;
      public Vec3 goal;
      public double speed;
      public Vec3 lookAt;
      public float excite;
      public int voice;
      public boolean waiting;
      public boolean hot;
      public boolean nose;
      public boolean holdQuarry;
      public String say;
   }

   // ----------------------------------------------------------------------------------------- orders
   public void heel() {
      this.reset(HEEL);
   }

   public void reset(int m) {
      this.mode = m;
      this.quarry = null;
      this.castCenter = null;
      this.foundAt = null;
      this.waiting = false;
      this.approach = false;
      this.excite = 0.0F;
   }

   /** put the nose on an animal's line from {@code from} (game time), starting near {@code at} */
   public void track(UUID animal, long from, Vec3 at, Vec3 hound) {
      this.mode = TRACK;
      this.quarry = animal;
      this.cursor = from;
      this.lastReached = at;
      this.heading = Vec3.ZERO;
      this.castCenter = null;
      this.trackTicks = 0;
      this.skips = 0;
      this.reachedEnd = false;
      this.foundAt = null;
      this.waiting = false;
      this.windTicks = 0;
      this.approach = hound != null && horiz(hound, at) > 36.0;
      this.nextVoice = 40;
      this.nextSniff = 0;
   }

   public void search() {
      this.reset(SEARCH);
      this.legTicks = 0;
      this.leg = 0;
   }

   public boolean working() {
      return this.mode == TRACK || this.mode == CAST || this.mode == BAY || this.mode == FOUND || this.mode == STRIKE || this.mode == POINT;
   }

   public boolean trailing() {
      return this.mode == TRACK || this.mode == CAST || this.mode == BAY || this.mode == FOUND;
   }

   // ----------------------------------------------------------------------------------------- think (every 10 ticks)
   public Plan plan(Sense s) {
      Plan p = new Plan();
      p.mode = this.mode;
      switch (this.mode) {
         case TRACK, CAST, BAY, FOUND -> this.trail(s, p);
         case SEARCH, STRIKE, POINT -> this.hunt(s, p);
         default -> {
         }
      }
      p.mode = this.mode;
      p.excite = this.excite;
      p.waiting = this.waiting;
      return p;
   }

   private void trail(Sense s, Plan p) {
      if ((this.trackTicks += 10) > TRACK_LIMIT && this.mode != FOUND) {
         this.lose(p, "gives up the line and comes back");
         return;
      }
      // the hunter fell behind: wait for them, looking back (never while baying / standing over it)
      if (this.mode == TRACK || this.mode == CAST) {
         double od = Math.sqrt(horiz(s.hound, s.owner));
         if (this.waiting ? od > RESUME : od > WAIT_AT) {
            if (!this.waiting) {
               p.voice = V_WHINE;
            }
            this.waiting = true;
            p.lookAt = s.owner;
            p.goal = null;
            if (--this.nextVoice <= 0) {
               this.nextVoice = 14;
               p.voice = V_WHINE;
            }
            return;
         }
         this.waiting = false;
      }
      // the quarry itself: found / bayed
      if (s.quarryPos != null && this.mode != FOUND) {
         double d = s.quarryPos.distanceTo(s.hound);
         if (d < 20.0 && (d < 7.0 || s.quarryVisible)) {
            this.excite(1.0F);
            if (s.quarryDown) {
               this.found(s.quarryPos, true, p);
               return;
            }
            this.mode = BAY;
            this.baySince = 0;
            Vec3 away = flat(s.hound.subtract(s.quarryPos));
            Vec3 stand = away.lengthSqr() < 1.0E-4 ? s.hound : s.quarryPos.add(away.normalize().scale(3.8));
            p.goal = d > 4.6 ? stand : null;
            p.speed = 1.35;
            p.lookAt = s.quarryPos.add(0, 0.6, 0);
            p.holdQuarry = s.quarryWounded && d < 7.0;
            p.hot = true;
            if (--this.nextVoice <= 0) {
               this.nextVoice = 1 + (int)(this.rand() * 2);
               p.voice = this.rand() < 0.2 ? V_BAWL : V_CHOP;
            }
            return;
         }
         if (this.mode == BAY && (this.baySince += 10) > 40) {
            // it broke away: back on its line from here
            this.mode = TRACK;
            this.cursor = Math.max(this.cursor, s.now - 200L);
            this.lastReached = s.hound;
         }
      }
      if (this.mode == BAY && s.quarryPos == null) {
         this.mode = TRACK;
      }
      if (this.mode == FOUND) {
         this.standOver(s, p);
         return;
      }
      if (this.mode == CAST) {
         this.cast(s, p);
         return;
      }
      this.run(s, p, false);
   }

   /** TRACK / STRIKE: follow the line */
   private void run(Sense s, Plan p, boolean strike) {
      List<Waypoint> line = s.line;
      if (this.approach) {
         // hunter put him on a line some way off: lope to where it starts
         Waypoint first = line.isEmpty() ? null : line.get(0);
         Vec3 to = first != null ? first.pos : this.lastReached;
         if (to == null || horiz(s.hound, to) < 16.0 || s.stuck) {
            this.approach = false;
         } else {
            p.goal = to;
            p.speed = 1.15;
            p.nose = false;
            return;
         }
      }
      if (s.stuck) {
         // that stretch can't be reached: go round - pick the line up further on, or cast from here
         if (++this.skips > 3) {
            this.skips = 0;
            if (strike) {
               p.goal = null;
               return;
            }
            this.startCast(s, 500);
            this.cast(s, p);
            return;
         }
         Waypoint far = skipAhead(line, s.hound, this.cursor, 6.0);
         if (far != null) {
            this.cursor = far.time;
            this.lastReached = far.pos;
         }
      }
      Step step = advance(line, s.hound, this.cursor);
      if (step.reached != null) {
         if (this.lastReached != null && horiz(this.lastReached, step.reached.pos) > 0.5) {
            Vec3 h = flat(step.reached.pos.subtract(this.lastReached));
            if (h.lengthSqr() > 1.0E-6) {
               this.heading = this.heading.scale(0.6).add(h.normalize().scale(0.4));
            }
         }
         this.cursor = step.reached.time;
         this.lastReached = step.reached.pos;
         this.skips = 0;
         if (step.reached.end && !strike) {
            this.found(step.reached.pos, s.quarryPos == null || s.quarryDown, p);
            return;
         }
      }
      if (step.next == null || step.gap) {
         if (strike) {
            p.goal = null;
            return;
         }
         this.reachedEnd = step.next == null && line.stream().anyMatch(Waypoint::end);
         this.startCast(s, step.next == null && s.quarryPos == null ? 900 : 600);
         this.cast(s, p);
         return;
      }
      // aim a few waypoints ahead while the line runs straight (smooth running, no stop-and-go on 1.6 m points)
      Vec3 goal = lookAhead(line, step.index, s.hound, 6.5);
      long age = Math.max(0L, s.now - step.next.time);
      float fresh = 1.0F - Math.min(1.0F, age / 14000.0F);
      float near = s.quarryPos == null ? 0.0F : 1.0F - (float)Math.min(1.0, s.quarryPos.distanceTo(s.hound) / 48.0);
      this.excite(Math.max(fresh * 0.75F, near));
      p.goal = goal;
      p.nose = true;
      // a pace the hunter can follow on foot: ~2.6 m/s on an old line, ~4 m/s hot (a walking player does 4.3)
      p.speed = (strike ? 0.9 : 0.95) + (strike ? 0.15 : 0.25) * this.excite;
      p.hot = this.excite > 0.6F;
      if (s.swimming) {
         p.speed = 1.2;
      }
      if (--this.nextSniff <= 0) {
         this.nextSniff = 2 + (int)(this.rand() * 3);
         p.voice = V_SNIFF;
      }
      if (!strike && --this.nextVoice <= 0) {
         this.nextVoice = (int)(30 - 24 * this.excite + this.rand() * 4);
         if (this.excite > 0.3F) {
            p.voice = V_BAWL;
         }
      }
      // an old line is harder: now and then the hound overruns a turn and casts back for it
      if (!strike && age > 8000L && this.rand() < 0.02) {
         this.startCast(s, 140);
      }
   }

   private void startCast(Sense s, int limit) {
      this.castTicks = 0;
      this.castLimit = limit;
      this.castAngle = this.rand() * Math.PI * 2.0;
      this.castCenter = this.lastReached != null ? this.lastReached : s.hound;
      this.mode = CAST;
   }

   private void cast(Sense s, Plan p) {
      this.castTicks += 10;
      p.nose = true;
      // picked the line up again anywhere near?
      Waypoint pick = pickup(s.line, s.hound, Math.min(4.5 + this.castTicks / 80.0, 9.0), this.cursor);
      if (pick != null) {
         this.cursor = pick.time;
         this.lastReached = pick.pos;
         this.mode = TRACK;
         this.skips = 0;
         p.voice = V_SNIFF;
         p.goal = pick.pos;
         p.speed = 1.0;
         return;
      }
      if (this.castTicks > this.castLimit) {
         if (s.quarryPos != null && s.quarryPos.distanceTo(s.hound) < 96.0 && this.windTicks < 600) {
            // can't own the line any more but winds the animal: head straight for it
            this.windTicks += 10;
            p.goal = s.quarryPos;
            p.speed = 1.05;
            p.nose = false;
            this.excite(0.5F);
            this.castTicks = this.castLimit - 10;
            return;
         }
         if (this.reachedEnd && this.lastReached != null) {
            this.found(this.lastReached, true, p);
            return;
         }
         this.lose(p, "lost the line and comes back to you");
         return;
      }
      // widening spiral, biased ahead along the way the line was heading
      double r = Math.min(16.0, 2.5 + this.castTicks / 28.0);
      this.castAngle += Math.min(1.1, 2.4 / r);
      Vec3 c = this.castCenter.add(this.heading.lengthSqr() > 1.0E-4 ? this.heading.normalize().scale(Math.min(12.0, this.castTicks / 20.0)) : Vec3.ZERO);
      p.goal = c.add(Math.cos(this.castAngle) * r, 0.0, Math.sin(this.castAngle) * r);
      p.speed = 0.95;
      this.excite(0.25F);
      if (s.stuck) {
         this.castAngle += 1.2;
      }
      if (this.castTicks % 90 == 0) {
         p.voice = V_WHINE;
      }
   }

   private void found(Vec3 at, boolean dead, Plan p) {
      this.mode = FOUND;
      this.foundAt = at;
      this.foundDead = dead;
      this.foundTicks = 0;
      this.nextVoice = 0;
      p.goal = at;
      p.speed = 1.2;
      p.say = "has found it";
   }

   /** dead: stands over it and bawls until the hunter walks up, then lies by it; the entity ends the job */
   private void standOver(Sense s, Plan p) {
      this.foundTicks += 10;
      if (s.quarryPos != null && !s.quarryDown && s.quarryPos.distanceTo(this.foundAt) > 3.0) {
         // it got up and went: bay / trail again
         this.mode = TRACK;
         this.cursor = Math.max(this.cursor, s.now - 200L);
         this.lastReached = s.hound;
         return;
      }
      Vec3 at = s.quarryPos != null ? s.quarryPos : this.foundAt;
      double d = Math.sqrt(horiz(s.hound, at));
      p.goal = d > 1.6 ? at : null;
      p.speed = 1.2;
      p.lookAt = at.add(0, 0.3, 0);
      double od = Math.sqrt(horiz(s.owner, at));
      if (od > 5.0 && --this.nextVoice <= 0) {
         this.nextVoice = 5 + (int)(this.rand() * 4);
         p.voice = this.rand() < 0.5 ? V_BAWL : V_CHOP;
      }
   }

   private void lose(Plan p, String why) {
      this.reset(LOST);
      p.voice = V_WHINE;
      p.say = why;
   }

   // ----------------------------------------------------------------------------------------- search / strike / point
   private void hunt(Sense s, Plan p) {
      this.ignore.values().removeIf(t -> t < s.now);
      double od = Math.sqrt(horiz(s.hound, s.owner));
      if (this.mode == POINT) {
         this.point(s, p, od);
         return;
      }
      if (this.mode == STRIKE) {
         // the hunter fell behind: hold up
         if (this.waiting ? od > RESUME : od > WAIT_AT + 6.0) {
            this.waiting = true;
            p.lookAt = s.owner;
            return;
         }
         this.waiting = false;
         if (s.quarryPos == null) {
            this.mode = SEARCH;
            this.quarry = null;
         } else {
            double d = s.quarryPos.distanceTo(s.hound);
            if (d < POINT_BLIND || s.quarryVisible && d < POINT_SEEN) {
               this.mode = POINT;
               this.pointTicks = 0;
               this.fleeTicks = 0;
               p.voice = V_WHINE;
               p.lookAt = s.quarryPos.add(0, 0.8, 0);
               p.say = "is on point";
               return;
            }
            if (s.quarryFleeing) {
               this.drop(s, 2400L);
               return;
            }
            if (!s.line.isEmpty()) {
               // run its line toward it (newer and newer scent)
               this.run(s, p, true);
               if (p.goal != null) {
                  return;
               }
            }
            // air scent: straight up the wind to it
            p.goal = s.quarryPos;
            p.speed = 0.95;
            p.nose = false;
            p.lookAt = s.quarryPos.add(0, 0.8, 0);
            if ((this.legTicks += 10) > 1200) {
               this.drop(s, 2400L);
            }
            return;
         }
      }
      // SEARCH: strike any fresh game line on the ground or on the wind
      Game best = null;
      double bestScore = 0;
      for (Game g : s.game) {
         if (this.ignore.containsKey(g.id)) {
            continue;
         }
         double sc = scent(g, s);
         if (sc > bestScore) {
            bestScore = sc;
            best = g;
         }
      }
      if (best != null) {
         this.mode = STRIKE;
         this.quarry = best.id;
         this.castCenter = null;
         this.legTicks = 0;
         Waypoint near = nearest(best.line, s.hound);
         this.cursor = near != null && horiz(near.pos, s.hound) < 36.0 ? near.time - 20L : s.now;
         this.lastReached = near != null ? near.pos : s.hound;
         this.approach = false;
         this.heading = Vec3.ZERO;
         p.voice = V_STRIKE;
         p.say = "strikes fresh scent";
         p.goal = near != null && horiz(near.pos, s.hound) < 36.0 ? near.pos : best.pos;
         p.speed = 1.0;
         return;
      }
      // ranging: quarter back and forth ahead of the hunter, nose working
      if (od > 56.0) {
         p.goal = s.owner;
         p.speed = 1.25;
         return;
      }
      this.legTicks += 10;
      if (this.legTicks > 160 || s.stuck || this.castCenter == null || horiz(s.hound, this.castCenter) < 6.0) {
         this.legTicks = 0;
         this.leg++;
         Vec3 f = s.ownerLook.lengthSqr() > 1.0E-4 ? flat(s.ownerLook).normalize() : new Vec3(0, 0, 1);
         Vec3 side = new Vec3(-f.z, 0, f.x);
         double ahead = 16.0 + 10.0 * this.rand();
         double lat = ((this.leg & 1) == 0 ? 1 : -1) * (12.0 + 10.0 * this.rand());
         this.castCenter = s.owner.add(f.scale(ahead)).add(side.scale(lat));
      }
      p.goal = this.castCenter;
      p.speed = 1.1;
      p.nose = this.legTicks % 60 < 30;
      if (p.nose && --this.nextSniff <= 0) {
         this.nextSniff = 3;
         p.voice = V_SNIFF;
      }
   }

   /** how strongly the hound can wind / cut this animal's line now (0 = not at all) */
   static double scent(Game g, Sense s) {
      double best = 0.0;
      // ground: fresh line under the nose
      for (Waypoint w : g.line) {
         long age = s.now - w.time;
         if (age < STRIKE_AGE && horiz(w.pos, s.hound) < 4.5 * 4.5 && Math.abs(w.pos.y - s.hound.y) < 3.0) {
            best = Math.max(best, 2.0 - age / (double)STRIKE_AGE);
         }
      }
      // air: the animal upwind of the dog (scent drifts downwind), far further than any other way
      Vec3 to = flat(s.hound.subtract(g.pos));
      double d = to.length();
      double reach = 22.0;
      if (s.wind.lengthSqr() > 1.0E-6 && d > 1.0E-3) {
         double c = to.scale(1.0 / d).dot(flat(s.wind).normalize());
         reach = c > 0.55 ? 88.0 : c > 0.0 ? 40.0 : 22.0;
      }
      if (d < reach) {
         best = Math.max(best, 1.0 - d / reach + 0.2);
      }
      return best;
   }

   private void point(Sense s, Plan p, double od) {
      this.pointTicks += 10;
      p.goal = null;
      if (s.quarryPos == null) {
         this.mode = SEARCH;
         return;
      }
      p.lookAt = s.quarryPos.add(0, 0.8, 0);
      double d = s.quarryPos.distanceTo(s.hound);
      if (s.quarryFleeing) {
         if ((this.fleeTicks += 10) > 40) {
            this.drop(s, 3600L);
         }
         return;
      }
      if (d > POINT_SEEN + 10.0) {
         this.mode = STRIKE;
         return;
      }
      // holds point while the hunter comes up and for a while after; then breaks and goes back to quartering
      if (this.pointTicks > (od < 8.0 ? 1200 : 2400)) {
         this.drop(s, 2400L);
         return;
      }
      if (od > 12.0 && this.pointTicks % 120 == 0) {
         p.voice = V_WHINE;
      }
   }

   private void drop(Sense s, long ms) {
      if (this.quarry != null) {
         this.ignore.put(this.quarry, s.now + ms);
      }
      this.quarry = null;
      this.mode = SEARCH;
      this.castCenter = null;
      this.legTicks = 1000;
   }

   private void excite(float e) {
      this.excite += (Math.max(0.0F, Math.min(1.0F, e)) - this.excite) * 0.35F;
   }

   private double rand() {
      this.seed = this.seed * 6364136223846793005L + 1442695040888963407L;
      return ((this.seed >>> 11) & ((1L << 53) - 1)) / (double)(1L << 53);
   }

   public void seed(long s) {
      this.seed = s;
   }

   // ----------------------------------------------------------------------------------------- line geometry
   public record Step(Waypoint reached, Waypoint next, int index, boolean gap) {
   }

   static double horiz(Vec3 a, Vec3 b) {
      double dx = a.x - b.x, dz = a.z - b.z;
      return dx * dx + dz * dz;
   }

   static Vec3 flat(Vec3 v) {
      return new Vec3(v.x, 0.0, v.z);
   }

   /**
    * Walk the line: the newest waypoint (within the next 40 after the cursor) the hound stands on is reached - so where the
    * line loops back over itself he takes the newer pass, like a real hound cutting a loop; the next target is the first
    * one after it that is still ahead. A next point far from the hound is a gap.
    */
   public static Step advance(List<Waypoint> line, Vec3 at, long cursor) {
      int start = 0;
      while (start < line.size() && line.get(start).time < cursor) {
         start++;
      }
      Waypoint reached = null;
      int from = start;
      for (int i = start; i < line.size() && i < start + 40; i++) {
         Waypoint w = line.get(i);
         if (horiz(w.pos, at) < 2.4 * 2.4 && Math.abs(w.pos.y - at.y) < 3.0) {
            reached = w;
            from = i + 1;
         }
      }
      for (int i = from; i < line.size(); i++) {
         Waypoint w = line.get(i);
         double d2 = horiz(w.pos, at);
         if (d2 < 2.4 * 2.4 && Math.abs(w.pos.y - at.y) < 3.0) {
            reached = w;
            continue;
         }
         // skip points the hound already passed: a later point is nearer than this one
         if (i + 1 < line.size() && horiz(line.get(i + 1).pos, at) < d2 && horiz(line.get(i + 1).pos, w.pos) < 9.0 && d2 < 36.0) {
            reached = w;
            continue;
         }
         return new Step(reached, w, i, d2 > 22.0 * 22.0);
      }
      return new Step(reached, null, -1, false);
   }

   /** the furthest waypoint (from index i on) still on a roughly straight run within {@code max} blocks */
   static Vec3 lookAhead(List<Waypoint> line, int i, Vec3 from, double max) {
      Waypoint first = line.get(i);
      Vec3 best = first.pos;
      for (int k = i + 1; k < line.size() && k < i + 8; k++) {
         Waypoint w = line.get(k);
         if (horiz(w.pos, from) > max * max || w.end) {
            break;
         }
         // every point between must lie near the straight segment from -> w
         boolean straight = true;
         for (int j = i; j < k; j++) {
            if (segDist2(line.get(j).pos, from, w.pos) > 1.4 * 1.4) {
               straight = false;
               break;
            }
         }
         if (!straight || Math.abs(w.pos.y - from.y) > 2.5) {
            break;
         }
         best = w.pos;
      }
      return best;
   }

   static double segDist2(Vec3 p, Vec3 a, Vec3 b) {
      double abx = b.x - a.x, abz = b.z - a.z;
      double l2 = abx * abx + abz * abz;
      double t = l2 < 1.0E-9 ? 0.0 : Math.max(0.0, Math.min(1.0, ((p.x - a.x) * abx + (p.z - a.z) * abz) / l2));
      double dx = a.x + abx * t - p.x, dz = a.z + abz * t - p.z;
      return dx * dx + dz * dz;
   }

   /**
    * the line picked up while casting: the newest waypoint after the cursor (strictly - not the spot where it was lost)
    * within the cast radius, so the hound goes on forward from there and never back to where it broke
    */
   static Waypoint pickup(List<Waypoint> line, Vec3 at, double radius, long cursor) {
      Waypoint best = null;
      for (Waypoint w : line) {
         if (w.time > cursor && horiz(w.pos, at) < radius * radius && Math.abs(w.pos.y - at.y) < 4.0) {
            best = w;
         }
      }
      return best;
   }

   /** the first waypoint after the cursor that is at least {@code min} blocks past the next one (route round an obstacle) */
   static Waypoint skipAhead(List<Waypoint> line, Vec3 at, long cursor, double min) {
      Waypoint first = null;
      for (Waypoint w : line) {
         if (w.time <= cursor) {
            continue;
         }
         if (first == null) {
            first = w;
         } else if (horiz(w.pos, first.pos) > min * min) {
            return w;
         }
      }
      return null;
   }

   static Waypoint nearest(List<Waypoint> line, Vec3 at) {
      Waypoint best = null;
      double bd = Double.MAX_VALUE;
      for (Waypoint w : line) {
         double d = horiz(w.pos, at);
         if (d < bd) {
            bd = d;
            best = w;
         }
      }
      return best;
   }

   public static List<Waypoint> sorted(List<Waypoint> in) {
      List<Waypoint> out = new ArrayList<>(in);
      out.sort((a, b) -> Long.compare(a.time, b.time));
      return out;
   }
}
