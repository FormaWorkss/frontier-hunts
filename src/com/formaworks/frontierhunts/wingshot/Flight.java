package com.formaworks.frontierhunts.wingshot;

/**
 * [wingshot] Pure flight model and pilot for the game birds (no Minecraft types, so it runs in the offline harness).
 *
 * <p>The bird is a point mass flown by a smooth guidance law: heading turns with a rate limited by the load the bird can
 * pull at its speed (and an angular-acceleration limit, so there is never a snap), speed tracks its target with
 * acceleration/deceleration limits, and height follows a rate-limited first-order loop on the altitude error (no
 * overshoot, no bobbing). Terrain is followed from heightmap probes under and ahead of the bird; obstacles (trees,
 * cliffs, buildings) are found with short ray probes and flown around. All units are blocks and ticks (1 block = 1 m,
 * 20 ticks = 1 s), so a mallard cruising at 0.95 b/t flies 19 m/s - the real figure.
 *
 * <p>The pilot runs a small mission state machine: a mallard jumps off the water ("puddle duck" take-off), climbs out
 * away from the threat to cruising height, flies off, asks the adapter for water to land on, circles it with set
 * wings while losing height, turns onto a final leg into the wind and lands with cupped wings and feet down; flock
 * mates hold loose V or echelon slots on the leader and land with it. A grouse explodes off the ground, flies low and
 * fast through the trees for 20-60 m, sets its wings, glides in and lands running. Dead birds fall ballistically with
 * real gravity and drag.
 */
public final class Flight {
   // ------------------------------------------------------------------ phases (synced to clients as one byte)
   public static final byte NONE = 0, TAKEOFF = 1, FLAP = 2, GLIDE = 3, SET = 4, LAND = 5, FLUSH = 6, FALL = 7, DRUM_A = 8, DRUM_B = 9;

   /** gravity, blocks/tick^2 (9.81 m/s^2) */
   public static final double G = 9.81 / 400.0;

   public interface World {
      /** Top of the surface (ground, or water surface) at (x, z); {@code canopy} = count leaves. NaN where not loaded. */
      double floor(double x, double z, boolean canopy);

      /** Free distance along the ray from (x, y, z) toward unit (dx, dy, dz), up to {@code max}. */
      double clear(double x, double y, double z, double dx, double dy, double dz, double max);
   }

   public enum Kind {
      DUCK(0.95, 0.62, 0.20, 0.035, 0.03, 0.26, 0.30, 2.6, 0.13, 0.010, 3.5, true),
      GROUSE(0.78, 0.55, 0.22, 0.09, 0.06, 0.30, 0.24, 4.5, 0.36, 0.06, 1.6, false);

      /** cruise / approach / touchdown speed (b/t), accel / decel (b/t^2), climb / sink limit (b/t), load factor, turn-rate cap (rad/t), turn accel (rad/t^2), min clearance (b) */
      public final double cruise, approach, touchdown, accel, decel, climb, sink, load, omegaCap, alpha, minClear;
      /** flies above the canopy (ducks) or under it among the trunks (grouse) */
      public final boolean overCanopy;

      Kind(double cruise, double approach, double touchdown, double accel, double decel, double climb, double sink, double load, double omegaCap,
         double alpha, double minClear, boolean overCanopy) {
         this.cruise = cruise;
         this.approach = approach;
         this.touchdown = touchdown;
         this.accel = accel;
         this.decel = decel;
         this.climb = climb;
         this.sink = sink;
         this.load = load;
         this.omegaCap = omegaCap;
         this.alpha = alpha;
         this.minClear = minClear;
         this.overCanopy = overCanopy;
      }
   }

   public enum Mission {
      /** spooked: off and away, then land somewhere else */
      FLEE,
      /** called / decoyed: in over the spread, circle, land at {@link #land} */
      LURE,
      /** an unhurried move to other water */
      RELOCATE,
      /** a flock mate holding a slot on a leader */
      FOLLOW,
      /** come down where it is (stranded in the air, wounded, out of room) */
      SETTLE,
      /** dead: ballistic fall */
      FALL
   }

   /** pilot stages */
   static final int WAIT = 0, LIFT = 1, OUT = 2, APPROACH = 3, CIRCLE = 4, TOFINAL = 5, FINAL = 6, GLIDEIN = 7, DONE = 8;

   /** small deterministic random source (the adapter seeds it per bird) */
   public static final class Rng {
      private long s;

      public Rng(long seed) {
         this.s = seed ^ 0x9E3779B97F4A7C15L;
      }

      public double next() {
         this.s ^= this.s << 13;
         this.s ^= this.s >>> 7;
         this.s ^= this.s << 17;
         return (this.s >>> 11) * 0x1.0p-53;
      }

      public double range(double a, double b) {
         return a + (b - a) * this.next();
      }
   }

   public final Kind kind;
   public final Rng rng;
   // ------------------------------------------------------------------ body state
   public double x, y, z;
   /** horizontal airspeed (b/t) and its direction (radians: 0 = +x/east, pi/2 = +z/south) */
   public double speed, heading;
   /** turn rate (rad/t) and vertical speed (b/t) */
   public double omega, vy;
   /** full 3-D velocity used by FALL (dead) */
   public double fx, fy, fz;
   public byte phase = NONE;
   public int age, phaseAge;
   // ------------------------------------------------------------------ mission
   public Mission mission;
   int stage = WAIT;
   int stageAge;
   int delay;
   double tx, ty, tz;
   /** landing site */
   public double lx, ly, lz;
   public boolean landSet, landWater;
   /** set while the pilot wants the adapter to find it somewhere to land ({@link #land}) */
   public boolean needLanding;
   double ox, oy, oz;
   double thX, thZ;
   boolean hasThreat;
   double windX, windZ;
   double outDist;
   double cx, cz, cr, swept, entryAlt;
   int cdir, laps, goArounds;
   double finalDist = 26.0;
   public Flight leader;
   double slotBack, slotRight, slotUp;
   int index;
   double avoidHeading;
   int avoidTicks, avoidSide;
   boolean braking;
   double floorHere = Double.NaN;
   int blockedTicks;
   /** finished: on the ground / water ({@link #landed}) or given up */
   public boolean landed, done;
   public boolean onWaterAtEnd;
   /** debug: last desired heading and altitude */
   public double dbgHeading, dbgAlt;
   /** the bird is spooked into the air (adapter: alarmed take-off sound, flare) */
   public boolean alarmed;

   public Flight(Kind kind, long seed) {
      this.kind = kind;
      this.rng = new Rng(seed);
   }

   // ================================================================== mission starts

   /** Set position and heading (radians) before a mission starts. */
   public Flight at(double x, double y, double z, double heading, double speed, double vy) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.ox = x;
      this.oy = y;
      this.oz = z;
      this.heading = finite(heading) ? heading : 0.0;
      this.speed = finite(speed) ? Math.max(0.0, speed) : 0.0;
      this.vy = finite(vy) ? vy : 0.0;
      return this;
   }

   public Flight wind(double east, double south) {
      this.windX = finite(east) ? east : 0.0;
      this.windZ = finite(south) ? south : 0.0;
      return this;
   }

   /** Spooked by something at (tx, tz): up and away from it. {@code airborne}: already flying (a flare). */
   public Flight flee(double threatX, double threatZ, boolean airborne) {
      this.mission = Mission.FLEE;
      this.hasThreat = finite(threatX) && finite(threatZ);
      this.thX = threatX;
      this.thZ = threatZ;
      this.alarmed = true;
      double away = this.awayHeading();
      if (this.kind == Kind.GROUSE) {
         // a flush rarely goes straight away: up to ~40 degrees either side, often toward cover
         away += this.rng.range(-0.7, 0.7);
         this.outDist = this.rng.range(20.0, 60.0);
      } else {
         away += this.rng.range(-0.35, 0.35);
         this.outDist = this.rng.range(90.0, 170.0);
      }
      this.tx = this.x + Math.cos(away) * this.outDist;
      this.tz = this.z + Math.sin(away) * this.outDist;
      if (!airborne) {
         this.heading = away;
         this.stage(LIFT);
      } else {
         this.stage(OUT);
      }
      this.landSet = false;
      return this;
   }

   /** Called in to a landing site (decoys, call): fly to it at height, circle, final into the wind, land. */
   public Flight lure(double lx, double ly, double lz, boolean water, boolean airborne) {
      this.mission = Mission.LURE;
      this.land(lx, ly, lz, water);
      this.laps = 1 + (int)(this.rng.next() * 2.2);
      this.stage(airborne ? APPROACH : LIFT);
      if (!airborne) {
         this.heading = Math.atan2(lz - this.z, lx - this.x);
      }
      return this;
   }

   /** An unhurried move to (lx, ly, lz). */
   public Flight relocate(double lx, double ly, double lz, boolean water) {
      this.mission = Mission.RELOCATE;
      this.land(lx, ly, lz, water);
      this.laps = this.rng.next() < 0.5 ? 0 : 1;
      this.heading = Math.atan2(lz - this.z, lx - this.x);
      this.stage(LIFT);
      return this;
   }

   /** Take slot {@code index} (1..) behind {@code leader}; lifts off after {@code delayTicks}. */
   public Flight follow(Flight leader, int index, boolean echelon, int delayTicks, boolean airborne) {
      this.mission = Mission.FOLLOW;
      this.leader = leader;
      this.index = Math.max(1, index);
      int side = (this.index & 1) == 1 ? 1 : -1;
      int rank = (this.index + 1) / 2;
      double jitter = this.rng.range(-0.35, 0.35);
      if (echelon) {
         this.slotBack = 2.1 * this.index + jitter;
         this.slotRight = 1.15 * this.index + this.rng.range(-0.3, 0.3);
         this.slotUp = 0.18 * this.index;
      } else {
         this.slotBack = 1.9 * rank + jitter;
         this.slotRight = side * (1.7 * rank + this.rng.range(-0.3, 0.3));
         this.slotUp = 0.25 * rank + this.rng.range(-0.2, 0.2);
      }
      this.hasThreat = leader.hasThreat;
      this.thX = leader.thX;
      this.thZ = leader.thZ;
      this.alarmed = leader.alarmed;
      this.delay = Math.max(0, delayTicks);
      this.heading = leader.heading + this.rng.range(-0.3, 0.3);
      this.stage(airborne ? OUT : (this.delay > 0 ? WAIT : LIFT));
      return this;
   }

   /** Come down where it is: glide to the floor ahead. */
   public Flight settle() {
      this.mission = Mission.SETTLE;
      this.stage(GLIDEIN);
      this.landSet = false;
      return this;
   }

   /** Dead: ballistic from its current velocity plus a push ({@code px, py, pz}) from the shot. */
   public Flight fall(double px, double py, double pz) {
      this.mission = Mission.FALL;
      double hx = Math.cos(this.heading) * this.speed, hz = Math.sin(this.heading) * this.speed;
      this.fx = hx * 0.8 + (finite(px) ? px : 0.0);
      this.fy = this.vy * 0.6 + (finite(py) ? py : 0.0);
      this.fz = hz * 0.8 + (finite(pz) ? pz : 0.0);
      this.setPhase(FALL);
      this.stage(DONE);
      this.landed = false;
      this.done = false;
      return this;
   }

   /** The adapter found somewhere to land. */
   public void land(double lx, double ly, double lz, boolean water) {
      if (!finite(lx) || !finite(ly) || !finite(lz)) {
         return;
      }
      this.lx = lx;
      this.ly = ly;
      this.lz = lz;
      this.landWater = water;
      this.landSet = true;
      this.needLanding = false;
   }

   /** Hit but still flying: badly hurt birds sail down and land ({@code wounded}), others flinch and flare. */
   public void hit(boolean wounded) {
      if (this.mission == Mission.FALL) {
         return;
      }
      this.vy -= 0.12;
      this.omega += this.rng.range(-0.06, 0.06);
      if (wounded) {
         this.mission = Mission.SETTLE;
         this.leader = null;
         this.stage(GLIDEIN);
      }
   }

   void stage(int s) {
      this.stage = s;
      this.stageAge = 0;
   }

   public int stage() {
      return this.stage;
   }

   private double awayHeading() {
      if (!this.hasThreat) {
         return this.heading;
      }
      double dx = this.x - this.thX, dz = this.z - this.thZ;
      if (dx * dx + dz * dz < 1.0E-4) {
         return this.heading;
      }
      return Math.atan2(dz, dx);
   }

   // ================================================================== per tick

   /** Advance one tick: decide where to go, then fly there. Position must be written back by the adapter after the move. */
   public void tick(World w) {
      this.age++;
      this.phaseAge++;
      this.stageAge++;
      if (this.mission == Mission.FALL) {
         this.tickFall(w);
         return;
      }
      if (this.done || this.landed) {
         return;
      }
      if (!finite(this.x + this.y + this.z + this.speed + this.heading + this.vy + this.omega)) {
         // never fly on NaN: stop where we are
         this.speed = 0.0;
         this.vy = 0.0;
         this.omega = 0.0;
         this.done = true;
         return;
      }
      if (this.age > (this.kind == Kind.GROUSE ? 400 : 2400) && this.stage != FINAL && this.stage != GLIDEIN && this.mission != Mission.SETTLE) {
         this.settle(); // took too long: come down
      }
      switch (this.mission) {
         case FOLLOW -> this.pilotFollow(w);
         case SETTLE -> this.pilotGlideIn(w);
         default -> {
            if (this.kind == Kind.GROUSE) {
               this.pilotGrouse(w);
            } else {
               this.pilotDuck(w);
            }
         }
      }
   }

   // ------------------------------------------------------------------ duck pilot

   private void pilotDuck(World w) {
      switch (this.stage) {
         case WAIT -> {
            this.speed = 0.0;
            this.vy = 0.0;
            if (--this.delay <= 0) {
               this.stage(LIFT);
            }
         }
         case LIFT -> {
            // puddle-duck jump: straight up off the water with deep, fast strokes, then away
            this.setPhase(TAKEOFF);
            double up = this.mission == Mission.FLEE ? 0.34 : 0.24;
            this.vy = this.stageAge < 3 ? Math.max(this.vy, up) : this.vy + (up * 0.8 - this.vy) * 0.3;
            double target = this.mission == Mission.FLEE ? this.awayHeading() : this.targetHeading();
            this.turnToward(target, 0.08);
            this.speed = Math.min(this.kind.approach, this.speed + (this.stageAge < 4 ? 0.015 : 0.05));
            this.integrate();
            if (this.stageAge >= 14) {
               this.stage(this.mission == Mission.FLEE ? OUT : APPROACH);
            }
         }
         case OUT -> {
            // climb out to cruising height and away
            this.setPhase(this.vy < -0.07 && this.stageAge > 20 ? GLIDE : FLAP);
            double cruise = this.cruiseFloor(w) + 22.0 + (this.index * 0.0);
            this.fly(w, this.tx, this.tz, this.kind.cruise, cruise, true);
            double dx = this.tx - this.x, dz = this.tz - this.z;
            if (dx * dx + dz * dz < 20.0 * 20.0 || this.stageAge > 400) {
               if (this.landSet) {
                  this.stage(APPROACH);
               } else {
                  this.needLanding = true;
                  // keep going while the adapter looks; it answers within a tick or two
                  this.tx = this.x + Math.cos(this.heading) * 40.0;
                  this.tz = this.z + Math.sin(this.heading) * 40.0;
                  if (this.stageAge > 460) {
                     // nowhere found: go back to where we started
                     this.land(this.ox, this.oy, this.oz, true);
                     this.stage(APPROACH);
                  }
               }
            } else if (this.landSet && this.stageAge > 1) {
               this.stage(APPROACH);
            }
         }
         case APPROACH -> {
            this.setPhase(this.vy < -0.07 ? GLIDE : FLAP);
            double d = Math.sqrt(sq(this.lx - this.x) + sq(this.lz - this.z));
            double alt = this.ly + Math.max(12.0, Math.min(26.0, d * 0.25 + 10.0));
            alt = Math.max(alt, this.cruiseFloor(w) + 8.0);
            this.fly(w, this.lx, this.lz, d > 40.0 ? this.kind.cruise : this.kind.approach + 0.12, alt, true);
            if (d < 30.0) {
               if (this.laps <= 0) {
                  this.stage(TOFINAL);
               } else {
                  this.startCircle();
               }
            }
         }
         case CIRCLE -> {
            // set wings and work the spread: an orbit losing height every lap, a few wingbeats now and then
            this.setPhase(SET);
            double ang = Math.atan2(this.z - this.cz, this.x - this.cx);
            double lead = ang + this.cdir * 0.55;
            double px = this.cx + Math.cos(lead) * this.cr, pz = this.cz + Math.sin(lead) * this.cr;
            double total = Math.max(1, this.laps) * Math.PI * 2.0;
            double f = Math.min(1.0, this.swept / total);
            double alt = this.entryAlt + (this.ly + 9.0 - this.entryAlt) * f;
            this.fly(w, px, pz, this.kind.approach, Math.max(alt, this.cruiseFloor(w) + 4.0), true);
            double na = Math.atan2(this.z - this.cz, this.x - this.cx);
            this.swept += Math.abs(wrap(na - ang));
            if (this.swept >= total || this.stageAge > 1200) {
               this.stage(TOFINAL);
            }
         }
         case TOFINAL -> {
            // to the start of a final leg that faces into the wind
            this.setPhase(SET);
            double[] f0 = this.finalStart();
            double d = Math.sqrt(sq(f0[0] - this.x) + sq(f0[1] - this.z));
            this.fly(w, f0[0], f0[1], this.kind.approach, Math.max(this.ly + 7.0, this.cruiseFloor(w) + 3.0), true);
            if (d < 5.0 || this.stageAge > 300) {
               this.stage(FINAL);
            }
         }
         case FINAL -> this.pilotFinal(w);
         case GLIDEIN -> this.pilotGlideIn(w);
         default -> {
         }
      }
   }

   private void startCircle() {
      this.cx = this.lx;
      this.cz = this.lz;
      this.cr = this.rng.range(15.0, 22.0);
      // circle the way the bird is already turning (or the shorter way round)
      double ang = Math.atan2(this.z - this.cz, this.x - this.cx);
      double tangentCcw = ang + Math.PI / 2.0;
      this.cdir = Math.cos(wrap(tangentCcw - this.heading)) >= 0.0 ? 1 : -1;
      this.swept = 0.0;
      this.entryAlt = this.y;
      this.stage(CIRCLE);
   }

   /** downwind of the landing site, so the last leg is flown into the wind (as waterfowl land) */
   private double[] finalStart() {
      double ws = Math.hypot(this.windX, this.windZ);
      double ux, uz;
      if (ws > 0.2) {
         ux = this.windX / ws;
         uz = this.windZ / ws;
      } else {
         // calm: straight in from where the bird is
         double dx = this.x - this.lx, dz = this.z - this.lz;
         double d = Math.hypot(dx, dz);
         ux = d < 1.0E-3 ? 1.0 : dx / d;
         uz = d < 1.0E-3 ? 0.0 : dz / d;
      }
      return new double[]{this.lx + ux * this.finalDist, this.lz + uz * this.finalDist};
   }

   private void pilotFinal(World w) {
      double dx = this.lx - this.x, dz = this.lz - this.z;
      double d = Math.sqrt(dx * dx + dz * dz);
      // glide slope onto the spread; slows to touchdown speed over the last ~20 blocks
      double slope = this.kind == Kind.GROUSE ? 0.35 : 0.3;
      double alt = this.ly + Math.max(0.0, d - 1.5) * slope;
      double sp = this.kind.touchdown + (this.kind.approach - this.kind.touchdown) * Math.min(1.0, d / 20.0);
      this.setPhase(d < 13.0 || this.y - this.ly < 4.0 ? LAND : SET);
      // fly the final line (start point downwind -> landing site): aim at a point on the line ahead of our projection
      double px = this.lx, pz = this.lz;
      if (this.mission != Mission.FOLLOW && d > 4.0) {
         double[] f0 = this.finalStart();
         double ux = this.lx - f0[0], uz = this.lz - f0[1];
         double len = Math.sqrt(ux * ux + uz * uz);
         if (len > 1.0) {
            ux /= len;
            uz /= len;
            double along = (this.x - f0[0]) * ux + (this.z - f0[1]) * uz;
            double ahead = Math.min(len, Math.max(0.0, along) + 7.0);
            px = f0[0] + ux * ahead;
            pz = f0[1] + uz * ahead;
         }
      }
      this.fly(w, px, pz, sp, alt, false);
      double floor = w.floor(this.x, this.z, false);
      if (finite(floor) && this.y < floor) {
         this.y = floor;
         this.vy = 0.0;
      }
      double err = Math.abs(wrap(Math.atan2(dz, dx) - this.heading));
      if (d < 3.5 && err > 1.6 && this.y - this.ly > 0.8) {
         // overshot: go around once or twice, then put down where we are
         if (++this.goArounds > 2) {
            this.settle();
         } else {
            this.stage(TOFINAL);
         }
         return;
      }
      if (this.y - this.ly <= 0.12 || (d < 2.5 && this.y - this.ly < 0.6) || this.touching(w)) {
         this.touchdown(w);
      }
      if (this.stageAge > 500) {
         this.settle();
      }
   }

   private boolean touching(World w) {
      double f = w.floor(this.x, this.z, false);
      return finite(f) && this.y - f < 0.08 && this.vy <= 0.0;
   }

   private void touchdown(World w) {
      this.landed = true;
      this.done = true;
      this.setPhase(NONE);
      double f = w.floor(this.x, this.z, false);
      this.onWaterAtEnd = this.landWater;
      if (finite(f)) {
         this.y = Math.max(this.y, f);
      }
      this.vy = 0.0;
      this.omega = 0.0;
   }

   /** come down ahead of where it is: wings set, gentle descent to the floor */
   private void pilotGlideIn(World w) {
      this.setPhase(this.stageAge < 8 && this.vy < -0.15 ? FLAP : (this.y - this.floorAt(w, this.x, this.z, false) < 3.0 ? LAND : GLIDE));
      if (this.stageAge == 1 || !finite(this.tx)) {
         double d = Math.max(10.0, Math.min(40.0, (this.y - this.floorAt(w, this.x, this.z, false)) * 2.5));
         this.tx = this.x + Math.cos(this.heading) * d;
         this.tz = this.z + Math.sin(this.heading) * d;
      }
      double floor = this.floorAt(w, this.x, this.z, false);
      double ahead = this.floorAt(w, this.x + Math.cos(this.heading) * 4.0, this.z + Math.sin(this.heading) * 4.0, false);
      double base = Math.max(floor, ahead);
      double h = this.y - base;
      double sp = this.kind.touchdown + (this.kind.approach - this.kind.touchdown) * Math.min(1.0, h / 6.0);
      // aim a little below the floor so the descent never stalls just above it
      double alt = base - 0.5;
      this.fly(w, this.tx, this.tz, sp, alt, false);
      double under = w.floor(this.x, this.z, false);
      if (finite(under) && this.y < under) {
         this.y = under;
         this.vy = 0.0;
      }
      if (h < 0.15 || this.touching(w) || this.stageAge > 600) {
         this.touchdown(w);
      }
   }

   // ------------------------------------------------------------------ flock mate

   private void pilotFollow(World w) {
      Flight l = this.leader;
      if (this.stage == WAIT) {
         this.speed = 0.0;
         this.vy = 0.0;
         if (--this.delay <= 0) {
            this.stage(LIFT);
         }
         return;
      }
      if (this.stage == LIFT) {
         this.setPhase(TAKEOFF);
         this.vy = this.stageAge < 3 ? Math.max(this.vy, 0.32) : this.vy + (0.27 - this.vy) * 0.3;
         if (l != null) {
            this.turnToward(l.heading, 0.08);
         }
         this.speed = Math.min(this.kind.approach, this.speed + (this.stageAge < 4 ? 0.015 : 0.05));
         this.integrate();
         if (this.stageAge >= 14) {
            this.stage(OUT);
         }
         return;
      }
      if (this.stage == FINAL) {
         this.pilotFinal(w);
         return;
      }
      if (this.stage == GLIDEIN) {
         this.pilotGlideIn(w);
         return;
      }
      if (l == null || l.mission == Mission.FALL || l.done && !l.landed) {
         // the leader is down (shot) or gone: flare and go on alone
         this.leader = null;
         this.mission = Mission.FLEE;
         this.alarmed = true;
         this.outDist = this.rng.range(70.0, 140.0);
         double away = this.heading + this.rng.range(-0.6, 0.6);
         this.tx = this.x + Math.cos(away) * this.outDist;
         this.tz = this.z + Math.sin(away) * this.outDist;
         this.landSet = false;
         this.vy = Math.max(this.vy, 0.18);
         this.stage(OUT);
         return;
      }
      if ((l.stage == FINAL || l.landed) && l.landSet) {
         // the leader is landing: put down beside it
         double ux = Math.cos(l.heading), uz = Math.sin(l.heading);
         double rx = -uz, rz = ux;
         this.land(l.lx - ux * this.slotBack * 0.7 + rx * this.slotRight * 0.9, l.ly, l.lz - uz * this.slotBack * 0.7 + rz * this.slotRight * 0.9, l.landWater);
         this.stage(FINAL);
         return;
      }
      // hold a loose slot on the leader
      double ux = Math.cos(l.heading), uz = Math.sin(l.heading);
      double rx = -uz, rz = ux;
      double wob = Math.sin(this.age * 0.045 + this.index * 1.7) * 0.5;
      double sx = l.x - ux * this.slotBack + rx * (this.slotRight + wob);
      double sz = l.z - uz * this.slotBack + rz * (this.slotRight + wob);
      double sy = l.y + this.slotUp + Math.sin(this.age * 0.06 + this.index) * 0.25;
      double along = (sx - this.x) * ux + (sz - this.z) * uz;
      double sp = clamp(l.speed + along * 0.07, 0.25, this.kind.cruise * 1.25);
      // steer at a point a little ahead of the slot so the mate flies parallel to the leader instead of chasing it
      double ax = sx + ux * 5.0, az = sz + uz * 5.0;
      this.setPhase(l.phase == SET || l.phase == GLIDE || l.phase == LAND ? l.phase : (this.vy < -0.07 ? GLIDE : FLAP));
      this.fly(w, ax, az, sp, sy, true);
   }

   // ------------------------------------------------------------------ grouse pilot

   private void pilotGrouse(World w) {
      switch (this.stage) {
         case WAIT -> {
            if (--this.delay <= 0) {
               this.stage(LIFT);
            }
         }
         case LIFT -> {
            // the flush: a thunderous burst up to head height, accelerating hard (already dodging trunks)
            this.setPhase(FLUSH);
            if (this.stageAge < 3) {
               this.vy = Math.max(this.vy, 0.36);
            }
            double floor = this.floorAt(w, this.x, this.z, false);
            this.fly(w, this.tx, this.tz, this.kind.cruise, floor + 2.6, true);
            if (this.stageAge >= 9) {
               this.stage(OUT);
            }
         }
         case OUT -> {
            // low and fast, weaving through the trunks; wings beating hard all the way (a short glide at the end)
            this.setPhase(this.stageAge < 14 ? FLUSH : FLAP);
            double floor = this.floorAt(w, this.x, this.z, false);
            double alt = floor + 2.4 + Math.sin(this.age * 0.21) * 0.5;
            this.fly(w, this.tx, this.tz, this.kind.cruise, alt, true);
            double d = Math.sqrt(sq(this.tx - this.x) + sq(this.tz - this.z));
            double flown = Math.sqrt(sq(this.ox - this.x) + sq(this.oz - this.z));
            if (d < 12.0 || flown > this.outDist - 10.0 || this.stageAge > 160) {
               this.stage(GLIDEIN);
               this.tx = this.x + Math.cos(this.heading) * 11.0;
               this.tz = this.z + Math.sin(this.heading) * 11.0;
            }
         }
         case GLIDEIN -> this.pilotGlideIn(w);
         default -> {
         }
      }
   }

   // ------------------------------------------------------------------ dead fall

   private void tickFall(World w) {
      if (this.landed) {
         return;
      }
      // real gravity and quadratic drag (terminal velocity ~0.9 b/t = 18 m/s for a limp duck)
      double k = this.kind == Kind.GROUSE ? 0.036 : 0.030;
      double v = Math.sqrt(this.fx * this.fx + this.fy * this.fy + this.fz * this.fz);
      double drag = k * v;
      this.fx -= this.fx * drag;
      this.fy -= this.fy * drag;
      this.fz -= this.fz * drag;
      this.fy -= G;
      if (!finite(this.fx + this.fy + this.fz)) {
         this.fx = 0.0;
         this.fy = -0.2;
         this.fz = 0.0;
      }
      this.x += this.fx;
      this.y += this.fy;
      this.z += this.fz;
      double f = w.floor(this.x, this.z, false);
      if (finite(f) && this.y <= f) {
         this.y = f;
         this.landed = true;
      }
   }

   // ================================================================== flying

   /** Steer toward (px, pz) at speed {@code sp}, holding altitude {@code alt}; {@code terrain}: keep clear of the ground. */
   void fly(World w, double px, double pz, double sp, double alt, boolean terrain) {
      double want = Math.atan2(pz - this.z, px - this.x);
      if (!finite(want)) {
         want = this.heading;
      }
      // ---- obstacles: probe ahead (a centre ray and two rays a body-width either side) every other tick; when
      // blocked, score headings either side for free distance, small deviation and closeness to the goal
      if (this.avoidTicks > 0) {
         this.avoidTicks--;
         want = this.avoidHeading;
      }
      if (((this.age & 1) == 0 || this.avoidTicks > 0) && this.speed > 0.05) {
         double look = 3.0 + this.speed * (this.kind.overCanopy ? 12.0 : 15.0);
         double free = this.probe(w, this.heading, look, true);
         if (free < look - 0.01) {
            double best = Double.NEGATIVE_INFINITY, bestH = this.heading;
            double towards = wrap(want - this.heading);
            for (double off : AVOID) {
               double h = this.heading + off * (towards >= 0.0 ? 1.0 : -1.0);
               double c = this.probe(w, h, look, false);
               double score = c - Math.abs(off) * 1.1 - Math.abs(wrap(h - want)) * 0.9;
               // stick with the side already chosen (no dithering between two gaps)
               if (this.avoidSide != 0 && off != 0.0 && Math.signum(wrap(h - this.heading)) == this.avoidSide) {
                  score += 1.5;
               }
               if (score > best) {
                  best = score;
                  bestH = h;
               }
            }
            this.avoidHeading = bestH;
            this.avoidTicks = 3;
            this.avoidSide = (int)Math.signum(wrap(bestH - this.heading));
            want = bestH;
            // tight: brake hard (a bird can flare its wings and tail), and over open ground pull up
            sp = Math.min(sp, Math.max(0.16, free * 0.1));
            this.braking = free < look * 0.5;
            if (free < 2.0 + this.speed * 4.0 && this.kind.overCanopy) {
               alt = Math.max(alt, this.y + 3.0);
            }
            if (!this.kind.overCanopy && best < look * 0.5) {
               // under the trees: duck under a low bough or hop over a log when that way is clearer
               double c = Math.cos(this.heading), sn = Math.sin(this.heading);
               double dn = w.clear(this.x, this.y + 0.15, this.z, c * 0.93, -0.37, sn * 0.93, look);
               double upc = w.clear(this.x, this.y + 0.15, this.z, c * 0.93, 0.37, sn * 0.93, look);
               if (dn > best + 1.0 && dn >= upc) {
                  alt = Math.min(alt, this.y - 1.4);
                  want = this.heading;
               } else if (upc > best + 1.0) {
                  alt = Math.max(alt, this.y + 1.4);
                  want = this.heading;
               }
            }
         }
      }
      // ---- terrain: never below the floor under and ahead of the bird (plus clearance)
      if (terrain) {
         double floor = this.floorAt(w, this.x, this.z, this.kind.overCanopy);
         double look = 6.0 + this.speed * 14.0;
         double ahead = this.floorAt(w, this.x + Math.cos(this.heading) * look, this.z + Math.sin(this.heading) * look, this.kind.overCanopy);
         double mid = this.floorAt(w, this.x + Math.cos(this.heading) * look * 0.5, this.z + Math.sin(this.heading) * look * 0.5, this.kind.overCanopy);
         double top = Math.max(floor, Math.max(ahead, mid));
         if (finite(top)) {
            alt = Math.max(alt, top + this.kind.minClear);
         }
      }
      this.dbgHeading = want;
      this.dbgAlt = alt;
      // ---- turn: proportional on the heading error, capped by the load the bird can pull at this speed
      double err = wrap(want - this.heading);
      double wmax = Math.min(this.kind.omegaCap, G * this.kind.load / Math.max(0.12, this.speed));
      double wcmd = clamp(err * (this.avoidTicks > 0 ? 0.4 : 0.2), -wmax, wmax);
      if (this.avoidTicks == 0) {
         this.avoidSide = 0;
      }
      this.omega += clamp(wcmd - this.omega, -this.kind.alpha, this.kind.alpha);
      // ---- speed
      this.speed += clamp(sp - this.speed, -(this.braking ? this.kind.decel * 2.5 : this.kind.decel), this.kind.accel);
      this.braking = false;
      // ---- height: rate-limited first-order loop, slow enough not to overshoot
      double vcmd = clamp((alt - this.y) * 0.07, -this.kind.sink, this.kind.climb);
      double dv = clamp((vcmd - this.vy) * 0.22, -0.03, 0.03);
      this.vy += dv;
      // climbing costs airspeed, diving gains a little
      this.speed = Math.max(0.05, this.speed - Math.max(0.0, this.vy) * 0.012 + Math.max(0.0, -this.vy) * 0.004);
      this.integrate();
   }

   /** free distance along heading {@code h} (level, plus the climb angle when {@code pitched}), the least of three rays a body-width apart */
   private double probe(World w, double h, double look, boolean pitched) {
      double c = Math.cos(h), s = Math.sin(h);
      double cp = 1.0, sp = 0.0;
      if (pitched) {
         double p = Math.atan2(this.vy, Math.max(0.05, this.speed));
         cp = Math.cos(p);
         sp = Math.sin(p);
      }
      double half = this.kind == Kind.GROUSE ? 0.35 : 0.45;
      double y = this.y + 0.15;
      double m = w.clear(this.x, y, this.z, c * cp, sp, s * cp, look);
      if (m > 0.5) {
         m = Math.min(m, w.clear(this.x - s * half, y, this.z + c * half, c * cp, sp, s * cp, m));
         m = Math.min(m, w.clear(this.x + s * half, y, this.z - c * half, c * cp, sp, s * cp, m));
      }
      return m;
   }

   private static final double[] AVOID = {0.0, 0.3, -0.3, 0.6, -0.6, 0.95, -0.95, 1.4, -1.4, 2.1, -2.1};

   private void turnToward(double h, double rate) {
      double err = wrap(h - this.heading);
      this.omega = clamp(err * 0.25, -rate, rate);
   }

   private void integrate() {
      this.heading = wrap(this.heading + this.omega);
      this.x += Math.cos(this.heading) * this.speed;
      this.z += Math.sin(this.heading) * this.speed;
      this.y += this.vy;
   }

   /** Velocity this tick (blocks/tick): x, y, z. */
   public double vx() {
      return this.mission == Mission.FALL ? this.fx : Math.cos(this.heading) * this.speed;
   }

   public double vyNow() {
      return this.mission == Mission.FALL ? this.fy : this.vy;
   }

   public double vz() {
      return this.mission == Mission.FALL ? this.fz : Math.sin(this.heading) * this.speed;
   }

   /** Bank angle (radians, + = right wing down) from the coordinated-turn relation. */
   public double bank() {
      return Math.atan2(this.speed * this.omega, G);
   }

   private double targetHeading() {
      double px = this.landSet && (this.mission == Mission.LURE || this.mission == Mission.RELOCATE) ? this.lx : this.tx;
      double pz = this.landSet && (this.mission == Mission.LURE || this.mission == Mission.RELOCATE) ? this.lz : this.tz;
      double h = Math.atan2(pz - this.z, px - this.x);
      return finite(h) ? h : this.heading;
   }

   private double cruiseFloor(World w) {
      double f = this.floorAt(w, this.x, this.z, true);
      return finite(f) ? f : this.y - 10.0;
   }

   private double floorAt(World w, double x, double z, boolean canopy) {
      double f = w.floor(x, z, canopy);
      if (!finite(f)) {
         // unloaded: treat it as a wall of high ground so the bird turns back
         return this.y + 2.0;
      }
      return f;
   }

   void setPhase(byte p) {
      if (this.phase != p) {
         this.phase = p;
         this.phaseAge = 0;
      }
   }

   // ================================================================== helpers

   public static double wrap(double a) {
      a %= Math.PI * 2.0;
      if (a > Math.PI) {
         a -= Math.PI * 2.0;
      } else if (a < -Math.PI) {
         a += Math.PI * 2.0;
      }
      return a;
   }

   static double clamp(double v, double lo, double hi) {
      return v < lo ? lo : (v > hi ? hi : v);
   }

   static double sq(double v) {
      return v * v;
   }

   static boolean finite(double v) {
      return !Double.isNaN(v) && !Double.isInfinite(v);
   }
}
