package com.formaworks.frontierhunts.sled;

/**
 * [1.2.2] The toboggan's ride, on its own (no game classes), so the same code runs in the game and in the offline
 * test harness.
 *
 * <p>The sled is a body on the snow, not a walking mob: it rides a smooth surface laid over the block steps and the snow
 * layers, gravity pulls it down the fall line of that surface, the runners hold it to the way it points (a carve turns
 * the speed, it doesn't throw it away), the snow and the air hold it back a little. Off a crest at speed it flies and
 * lands further down. Only something standing up out of the snow - a trunk, a rock face, a wall - stops it, and a glancing
 * hit only takes the part of the speed that went into it.
 *
 * <p>Units are blocks and ticks. {@link #G} is a little stronger than real gravity (sledding in a game should feel
 * quick): on a 30 degree slope the sled is at 60 km/h in about three seconds and tops out around 130.
 */
public final class SledPhysics {
   /** what the sled needs to know about the world */
   public interface Terrain {
      /**
       * the top of the ground in a column: the first solid ground (snow at its drawn depth, not trunks, leaves or plants)
       * at or below {@code fromY}, searching down about a dozen blocks; far below ({@code fromY - 64}) if there is none
       * that close (a drop), NaN only where the land isn't loaded
       */
      double top(int x, int z, double fromY);

      /** does this box hit something standing up out of the ground (a trunk, a fence, a post): the ground itself, rock
       * and snow, is the surface's business, and bushes and leaves are pushed through */
      boolean blocked(double x0, double y0, double z0, double x1, double y1, double z1);

      /** how the runners slide on the ground at this column: 0 ice, about 0.02 snow, 1 bare earth (and water) */
      double grip(int x, int z, double y);
   }

   /** gravity, blocks per tick per tick (real gravity is about 0.0245) */
   public static final double G = 0.037;
   /** top speed in blocks per tick (about 160 km/h) */
   public static final double MAX = 2.2;
   /** how far the sled can reach up a step in one move before it counts as a wall */
   static final double STEP = 2.25;
   static final double HALF = 0.42;

   public double x, y, z;
   /** horizontal velocity, and vertical in the air */
   public double vx, vz, vy;
   /** facing, degrees, Minecraft's convention (0 = +z, 90 = -x) */
   public float yaw;
   public boolean ground = true;
   /** ticks of an unbroken run on snow: the runners glaze and the track packs, so it glides better as it goes */
   public int run;
   /** the last hit: 0 none, else how much of the speed it took (for a sound and a shake) */
   public double impact;
   /** the slope under the sled along and across it (for the pitch and roll of the drawing) */
   public double pitchSlope, rollSlope;
   /** where the sled is drawn (and the rider's eyes ride): its height smoothed, so the steps under the snow never
    * jolt the view; NaN until the first tick */
   public double viewY = Double.NaN;
   private double viewVy;

   private double gx0, gz0;

   /** for testing: what last held the sled back (a wall, a block in the way, a hard landing) */
   public String why = "";

   /** the rider's lean, eased toward the steering input */
   public float lean;

   // ------------------------------------------------------------------------------------------------ [1.2.5] motor
   /** [1.2.5] a snowmobile: an engine drives the track, skis steer, the fall line doesn't swing it round */
   public boolean motor;
   /** [1.2.5] throttle (0..1) and brake/reverse (-1..0) the engine and the brake lever deliver this tick */
   public float throttle;
   /** [1.2.5] what the track is pushing with this tick (0..1 of full traction), for the sound and the spray */
   public double drive;
   /** grip limit: how hard the track can push on snow, blocks per tick per tick (a little under 0.7 g) */
   public static final double TRACTION = 0.022;
   /** engine power: thrust times speed (it tops out about 115 km/h on the flat, more downhill) */
   public static final double POWER = 0.0165;
   /** [1.4.0] full throttle on bare ground: about 2 blocks a second, enough to get back to the snow, too slow to travel on */
   public static final double OFF_SNOW_CRAWL = 0.1;
   /** 0..1 how hard the runners are carving (for the spray and the sound) */
   public double carve;

   private final java.util.HashMap<Long, Double> tops = new java.util.HashMap<>();
   private Terrain t;
   private double reachY;

   public double speed() {
      return Math.hypot(this.vx, this.vz);
   }

   /** the speed along the way it points (negative when sliding backwards) */
   public double along() {
      double r = Math.toRadians(this.yaw);
      return this.vx * -Math.sin(r) + this.vz * Math.cos(r);
   }

   // ============================================================================================ the surface

   private double column(int bx, int bz) {
      long k = (long)bx << 32 ^ bz & 0xFFFFFFFFL;
      Double v = this.tops.get(k);
      if (v == null) {
         v = this.t.top(bx, bz, this.reachY);
         this.tops.put(k, v);
      }
      return v;
   }

   /** a corner of the block grid: the highest of the four columns around it, so the surface never cuts into a step */
   private double corner(int cx, int cz) {
      double h = Double.NaN;
      for (int i = 0; i < 4; i++) {
         double c = this.column(cx - 1 + (i & 1), cz - 1 + (i >> 1));
         if (!Double.isNaN(c) && (Double.isNaN(h) || c > h)) {
            h = c;
         }
      }
      return h;
   }

   /** the snow as a smooth surface over the steps; NaN where there's no ground in reach */
   public double surface(double px, double pz) {
      int fx = (int)Math.floor(px), fz = (int)Math.floor(pz);
      double tx = px - fx, tz = pz - fz;
      double a = this.corner(fx, fz), b = this.corner(fx + 1, fz), c = this.corner(fx, fz + 1), d = this.corner(fx + 1, fz + 1);
      int n = 0;
      double s = 0.0;
      for (double v : new double[]{a, b, c, d}) {
         if (!Double.isNaN(v)) {
            s += v;
            n++;
         }
      }
      if (n == 0) {
         return Double.NaN;
      }
      // a missing corner (unloaded land, a hole) takes the mean of the others
      double m = s / n;
      a = Double.isNaN(a) ? m : a;
      b = Double.isNaN(b) ? m : b;
      c = Double.isNaN(c) ? m : c;
      d = Double.isNaN(d) ? m : d;
      // smoothstep between the corners: no kink at every block line, so the ride doesn't rattle
      tx = tx * tx * (3.0 - 2.0 * tx);
      tz = tz * tz * (3.0 - 2.0 * tz);
      double ab = a + (b - a) * tx, cd = c + (d - c) * tx;
      return ab + (cd - ab) * tz;
   }

   /** the fall of the surface around a point, sampled over a sled length so single steps don't jolt it */
   private double[] gradient(double px, double pz) {
      double e = 0.75;
      double hx1 = this.surface(px + e, pz), hx0 = this.surface(px - e, pz), hz1 = this.surface(px, pz + e), hz0 = this.surface(px, pz - e);
      double gx = Double.isNaN(hx1) || Double.isNaN(hx0) ? 0.0 : (hx1 - hx0) / (2.0 * e);
      double gz = Double.isNaN(hz1) || Double.isNaN(hz0) ? 0.0 : (hz1 - hz0) / (2.0 * e);
      double g = Math.hypot(gx, gz);
      if (g > 2.0) {
         // a cliff edge or a wall in the samples: it's not a slope to ride
         gx *= 2.0 / g;
         gz *= 2.0 / g;
      }
      return new double[]{gx, gz};
   }

   // ============================================================================================ a tick

   /** only look: the slope under the sled where it stands (for drawing a sled this game doesn't drive) */
   public void sense(Terrain terrain) {
      this.t = terrain;
      this.tops.clear();
      this.reachY = this.y + STEP + 0.35;
      double rad = Math.toRadians(this.yaw);
      double fx = -Math.sin(rad), fz = Math.cos(rad);
      double[] gr = this.gradient(this.x, this.z);
      this.pitchSlope = -(gr[0] * fx + gr[1] * fz);
      this.rollSlope = gr[0] * -fz + gr[1] * fx;
      this.ground = true;
   }

   /**
    * how high the sled rides at a point: a sled is about two blocks of stiff wood, so it bridges the little steps and
    * lumps under it (the surface at its middle, with its nose and tail)
    */
   public double ride(double px, double pz) {
      double c = this.surface(px, pz);
      if (Double.isNaN(c)) {
         return c;
      }
      // the runners bridge the steps and the snow fills them: the surface averaged over a disc the size of the sled.
      // The same whichever way it points, so turning never lifts or drops it
      double sum = c, w = 1.0;
      for (int ring = 1; ring <= 3; ring++) {
         double r = ring * 0.6, k = ring == 3 ? 0.8 : 1.0;
         for (int i = 0; i < 6; i++) {
            double a = (i + (ring == 2 ? 0.5 : 0.0)) * Math.PI / 3.0;
            double h = this.surface(px + Math.cos(a) * r, pz + Math.sin(a) * r);
            if (!Double.isNaN(h) && Math.abs(h - c) < 3.0) {
               sum += h * k;
               w += k;
            }
         }
      }
      // never deeper than a hand into the snow it sits on, nor riding high over it
      return Math.min(c + 0.5, Math.max(sum / w, c - 0.5));
   }

   static float wrapDegrees(float a) {
      a %= 360.0F;
      if (a >= 180.0F) {
         a -= 360.0F;
      }
      if (a < -180.0F) {
         a += 360.0F;
      }
      return a;
   }

   private double rideOr(double px, double pz, double or) {
      double h = this.ride(px, pz);
      return Double.isNaN(h) ? or : h;
   }

   /**
    * one tick of the ride.
    *
    * @param forward the rider's forward/back input (-1..1), side left/right (-1..1, positive = left)
    * @param ridden false for a sled nobody's on: it just settles where it is
    */
   public void tick(Terrain terrain, float forward, float side, boolean ridden) {
      this.t = terrain;
      this.tops.clear();
      this.reachY = this.y + STEP + 0.35;
      this.impact = 0.0;
      double here = this.ride(this.x, this.z);
      if (this.ground && !Double.isNaN(here)) {
         if (this.y > here + 0.6) {
            this.ground = false; // the snow went from under it (dug out, or it was carried)
         } else if (this.y < here) {
            // newly placed, or fresh snow fell: up onto the snow ([1.2.5] a snowmobile at most 0.6 a tick, so a face it
            // is pressed against is never climbed this way)
            this.y = this.motor ? Math.min(here, this.y + 0.6) : here;
         }
      }
      double grip = terrain.grip((int)Math.floor(this.x), (int)Math.floor(this.z), this.y);
      double rad = Math.toRadians(this.yaw);
      double fx = -Math.sin(rad), fz = Math.cos(rad);
      double[] gr = this.gradient(this.x, this.z);
      this.gx0 = gr[0];
      this.gz0 = gr[1];
      this.pitchSlope = -(gr[0] * fx + gr[1] * fz);
      this.rollSlope = gr[0] * -fz + gr[1] * fx;

      if (!ridden) {
         // left alone it stays put (no runaway sleds) and only settles onto the snow
         this.vx *= 0.5;
         this.vz *= 0.5;
         if (this.speed() < 0.01) {
            this.vx = 0.0;
            this.vz = 0.0;
         }
         this.carve = 0.0;
      } else if (this.ground && this.motor) {
         this.motorGround(gr, grip, side);
      } else if (this.ground) {
         double sp = this.speed();
         // steer: lean the runners. Quick at a crawl, firm at speed
         // (the lean comes on and off over a few ticks, so a tap of the key is a small, smooth correction)
         this.lean += (side - this.lean) * (Math.abs(side) > Math.abs(this.lean) ? 0.22F : 0.35F);
         float turn = this.lean * (float)(2.6 + 1.6 * Math.min(1.0, sp * 2.0) - 1.2 * Math.max(0.0, Math.min(1.0, sp - 0.8)));
         this.yaw -= turn;
         rad = Math.toRadians(this.yaw);
         fx = -Math.sin(rad);
         fz = Math.cos(rad);
         // a sled swings round to where it's going, and, left to itself, round to the fall line (that's what makes it
         // go downhill on a real mountain, where the fall line keeps bending; to cross a slope you hold it there)
         double g = Math.hypot(gr[0], gr[1]);
         if (sp > 0.03) {
            float velYaw = (float)Math.toDegrees(Math.atan2(-this.vx, this.vz));
            float diff = wrapDegrees(velYaw - this.yaw);
            if (Math.abs(diff) < 100.0F) {
               this.yaw += diff * 0.12F;
            }
         }
         if (Math.abs(this.lean) < 0.15F && g > 0.04) {
            float downYaw = (float)Math.toDegrees(Math.atan2(gr[0], -gr[1]));
            float diff = wrapDegrees(downYaw - this.yaw);
            float rate = (float)Math.min(3.0, 3.5 * Math.min(1.0, g)) * Math.min(1.0F, Math.abs(diff) / 25.0F);
            this.yaw += Math.signum(diff) * rate;
         }
         rad = Math.toRadians(this.yaw);
         fx = -Math.sin(rad);
         fz = Math.cos(rad);
         // gravity down the fall line of the surface
         double n2 = 1.0 + gr[0] * gr[0] + gr[1] * gr[1];
         this.vx += -G * gr[0] / n2;
         this.vz += -G * gr[1] / n2;
         // the runners hold the sled to the way it points: the sideways speed is turned onto the new line (a carve),
         // with a little lost to the skid
         sp = this.speed();
         double par = this.vx * fx + this.vz * fz, lat = this.vx * -fz + this.vz * fx;
         double hold = grip < 0.5 ? 0.32 : 0.85;
         double lat2 = lat * (1.0 - hold);
         double skid = Math.abs(lat) - Math.abs(lat2);
         double mag = Math.sqrt(Math.max(0.0, sp * sp - lat2 * lat2)) * (1.0 - Math.min(0.08, skid * 0.25));
         par = Math.signum(par == 0.0 ? 1.0 : par) * mag;
         lat = lat2;
         this.carve = Math.min(1.0, skid * 8.0 + Math.abs(side) * Math.min(1.0, sp * 1.2));
         // a push off with the hands, only to get going
         if (forward > 0.0F && Math.abs(par) < 0.22) {
            par += 0.02 * forward;
         }
         this.vx = fx * par + -fz * lat;
         this.vz = fz * par + fx * lat;
         // the snow and the air
         double glaze = Math.max(0.6, 1.0 - this.run / 300.0);
         double mu = grip <= 0.0 ? 0.008 : grip < 0.1 ? 0.024 * glaze : 0.08 + 0.45 * grip;
         double decel = mu * G / Math.sqrt(n2) + 0.0003;
         if (forward < 0.0F) {
            decel += 0.028; // dig the heels in
            this.run = 0;
         }
         sp = this.speed();
         decel += 0.0036 * sp * sp;
         double ns = Math.min(MAX, Math.max(0.0, sp - decel));
         if (sp > 1.0E-6) {
            this.vx *= ns / sp;
            this.vz *= ns / sp;
         }
         this.run = ns > 0.3 && grip < 0.1 ? Math.min(900, this.run + 1) : ns < 0.1 ? 0 : this.run;
      } else {
         // in the air: it keeps its line, the air takes a little
         double sp = this.speed();
         double ns = Math.max(0.0, sp - 0.0035 * sp * sp);
         if (sp > 1.0E-6) {
            this.vx *= ns / sp;
            this.vz *= ns / sp;
         }
         this.lean += (side - this.lean) * 0.2F;
         this.yaw -= this.lean * 0.8F;
         this.carve = 0.0;
      }
      double before = this.y;
      this.move(ridden);
      this.smoothView(before);
   }

   /**
    * [1.2.5] a snowmobile on the ground. The skis steer it where it points (only when it moves, as real skis do),
    * the track keeps it on that line and drives it: up to the grip of the track at low speed, then the engine's power.
    * Steep slopes make the track spin, bare ground drags it to a crawl, and with the throttle off the track holds it
    * back (engine braking). Brake lever: hard braking, then reverse at a walk.
    */
   private void motorGround(double[] gr, double grip, float side) {
      double sp = this.speed();
      double along = this.along();
      this.lean += (side - this.lean) * (Math.abs(side) > Math.abs(this.lean) ? 0.25F : 0.35F);
      double steerK = Math.min(1.0, sp * 3.0);
      double rate = 4.2 - 1.9 * Math.min(1.0, sp / 1.4);
      this.yaw -= (float)(this.lean * rate * steerK * (along < -0.01 ? -1.0 : 1.0));
      if (sp > 0.05 && along > 0.0) {
         float velYaw = (float)Math.toDegrees(Math.atan2(-this.vx, this.vz));
         float diff = wrapDegrees(velYaw - this.yaw);
         if (Math.abs(diff) < 100.0F) {
            this.yaw += diff * 0.06F;
         }
      }
      double rad = Math.toRadians(this.yaw);
      double fx = -Math.sin(rad), fz = Math.cos(rad);
      double n2 = 1.0 + gr[0] * gr[0] + gr[1] * gr[1];
      double g = Math.hypot(gr[0], gr[1]);
      this.vx += -G * gr[0] / n2;
      this.vz += -G * gr[1] / n2;
      sp = this.speed();
      double par = this.vx * fx + this.vz * fz, lat = this.vx * -fz + this.vz * fx;
      // the skis and the track's keel hold the line much better than a toboggan's runners
      double hold = grip < 0.5 ? 0.55 : 0.85;
      double lat2 = lat * (1.0 - hold);
      double skid = Math.abs(lat) - Math.abs(lat2);
      if (par > 0.05) {
         // running forward the skis carve: the sideways speed turns onto the new line
         double mag = Math.sqrt(Math.max(0.0, sp * sp - lat2 * lat2)) * (1.0 - Math.min(0.06, skid * 0.2));
         par = mag;
      }
      // (at a crawl or rolling back the sideways slip is simply lost to the skis and the track, not turned backwards)
      lat = lat2;
      this.carve = Math.min(1.0, skid * 8.0 + Math.abs(side) * Math.min(1.0, sp * 1.2));
      // the engine
      // [1.2.5] it only runs on snow (and ice): on earth, rock, sand or water the track has nothing to bite
      double traction = grip <= 0.0 ? 0.55 : grip < 0.1 ? 1.0 : 0.0;
      // [1.4.0] off the snow (earth, rock, sand - not water) the track still claws along at a crawl, like a boat dragged
      // over land, so a rider who slid off the snow can work back onto it
      boolean offSnow = grip >= 0.1 && grip <= 1.0;
      double climb = Math.max(0.35, Math.min(1.0, 1.6 - 0.5 * g)); // past about 50 degrees the track starts to spin
      this.drive = 0.0;
      if (this.throttle > 0.0F && traction > 0.0) {
         double push = Math.min(TRACTION, POWER / Math.max(0.05, Math.abs(par))) * this.throttle * traction * climb;
         if (par < -0.02) {
            push = TRACTION * this.throttle; // rolling backwards: the track bites to stop it first
         }
         par += push;
         this.drive = push / TRACTION;
      } else if (this.throttle < 0.0F) {
         if (par > 0.03) {
            par = Math.max(0.0, par - 0.032 * -this.throttle); // brake
         } else if (traction > 0.0 || offSnow) {
            par = Math.max(offSnow ? -OFF_SNOW_CRAWL * 0.6 : -0.16, par - 0.007 * -this.throttle); // reverse
            this.drive = 0.3;
         }
      }
      this.vx = fx * par + -fz * lat;
      this.vz = fz * par + fx * lat;
      double mu = grip <= 0.0 ? 0.01 : grip < 0.1 ? 0.03 : 0.1 + 0.3 * grip; // off the snow it drags to a stop
      double decel = mu * G / Math.sqrt(n2) + 0.0004 + (this.throttle == 0.0F ? 0.0035 : 0.0);
      sp = this.speed();
      decel += 0.0036 * sp * sp;
      double ns = Math.min(MAX, Math.max(0.0, sp - decel));
      if (this.throttle == 0.0F && ns < 0.03 && g < 0.25) {
         ns = 0.0; // parked: the track holds it
      }
      if (offSnow && this.throttle > 0.0F && par >= -0.02) {
         // [1.4.0] the crawl: the drag still bleeds off any speed carried in from the snow, but never below a slow walk
         double crawl = OFF_SNOW_CRAWL * this.throttle * climb;
         if (ns < crawl) {
            ns = Math.min(crawl, Math.max(ns, sp) + 0.012);
            this.drive = Math.max(this.drive, 0.25);
            if (sp <= 1.0E-6 || par < 0.0) {
               this.vx = fx * ns;
               this.vz = fz * ns;
               sp = ns;
            }
         }
      } else if (offSnow && this.throttle < 0.0F && par <= 0.03) {
         // [1.4.0] and backs off the same way, slower
         double crawl = OFF_SNOW_CRAWL * 0.6 * -this.throttle;
         ns = Math.min(crawl, (par < 0.0 ? sp : 0.0) + 0.01);
         this.drive = Math.max(this.drive, 0.2);
         this.vx = -fx * ns;
         this.vz = -fz * ns;
         sp = ns;
      }
      if (sp > 1.0E-6) {
         this.vx *= ns / sp;
         this.vz *= ns / sp;
      }
      this.run = ns > 0.3 && grip < 0.1 ? Math.min(900, this.run + 1) : ns < 0.1 ? 0 : this.run;
   }

   private void smoothView(double before) {
      if (Double.isNaN(this.viewY) || Math.abs(this.y - this.viewY) > 3.0) {
         this.viewY = this.y;
         this.viewVy = 0.0;
         return;
      }
      // a spring on the height: it follows the slope (velocity) at once and the steps (position) gently
      this.viewVy += (this.y - before - this.viewVy) * 0.3;
      double pred = this.viewY + this.viewVy;
      this.viewY = pred + (this.y - pred) * 0.3;
      this.viewY = Math.max(this.y - 0.45, Math.min(this.y + 0.45, this.viewY));
   }

   private void move(boolean ridden) {
      double y0 = this.y, x0 = this.x, z0 = this.z;
      boolean groundAtStart = this.ground;
      double dx = this.vx, dz = this.vz;
      double len = Math.hypot(dx, dz);
      int n = Math.max(1, (int)Math.ceil(len / 0.4));
      double sx = dx / n, sz = dz / n, slen = len / n;
      if (this.ground && ridden && len > 0.4) {
         // over a crest at speed the snow falls away faster than the sled can follow: it flies
         // (and keeps falling away: one step down a staircase is followed, a real crest is flown)
         double ahead = this.ride(this.x + dx, this.z + dz), ahead2 = this.ride(this.x + dx * 2.0, this.z + dz * 2.0);
         if (!Double.isNaN(ahead) && !Double.isNaN(ahead2)) {
            double drop = this.y - ahead, ballistic = -this.vy + G;
            double drop2 = this.y - ahead2, ballistic2 = -this.vy * 2.0 + G * 3.0;
            if (drop > ballistic + 0.6 && drop2 > ballistic2 + 1.1) {
               this.ground = false;
            }
         }
      }
      if (!this.ground) {
         if (this.motor && this.vy > Math.max(0.3, len)) {
            // [1.2.5] leaving the ground a snowmobile keeps the climb its speed allows, not the height it snapped up
            // a step in the last tick (that threw it into the sky)
            this.vy = Math.max(0.3, len);
         }
         this.vy = Math.max(-2.5, this.vy - G);
      }
      for (int k = 0; k < n; k++) {
         double nx = this.x + sx, nz = this.z + sz;
         double hn = this.ride(nx, nz);
         if (Double.isNaN(hn)) {
            hn = this.ground ? this.y : this.y - 64.0;
         }
         int wall = this.wall(nx, nz, hn, slen);
         if (wall != 0) {
            // slide along what it hit: keep the part of the move along it
            double before = Math.hypot(this.vx, this.vz);
            boolean xs = this.wall(this.x + sx, this.z, this.rideOr(this.x + sx, this.z, this.y), Math.abs(sx)) == 0;
            boolean zs = this.wall(this.x, this.z + sz, this.rideOr(this.x, this.z + sz, this.y), Math.abs(sz)) == 0;
            if (xs && Math.abs(sx) >= Math.abs(sz) * 0.2) {
               this.vz = 0.0;
               sz = 0.0;
            } else if (zs && Math.abs(sz) >= Math.abs(sx) * 0.2) {
               this.vx = 0.0;
               sx = 0.0;
            } else {
               this.vx *= -0.1;
               this.vz *= -0.1;
               sx = 0.0;
               sz = 0.0;
            }
            double after = Math.hypot(this.vx, this.vz);
            this.impact = Math.max(this.impact, before - after);
            if (before - after > 0.3) {
               this.run = 0;
            }
            if (sx == 0.0 && sz == 0.0 && this.ground) {
               break;
            }
            // [1.2.5] in the air it keeps falling past what it hit (it used to hang there)
            nx = this.x + sx;
            nz = this.z + sz;
            hn = this.rideOr(nx, nz, this.ground ? this.y : this.y - 64.0);
         }
         this.x = nx;
         this.z = nz;
         if (this.ground && this.y - hn > 1.2 + slen * 1.5) {
            this.ground = false; // over an edge: it drops (never snaps down a cliff)
            this.vy = Math.min(this.vy, 0.0);
         }
         if (this.ground) {
            this.y = hn;
         } else {
            this.y += this.vy / n;
            if (this.y <= hn) {
               // land: the part of the fall that goes into the slope (not along it) scrubs some speed
               double moved = Math.hypot(nx - x0, nz - z0), h0 = this.rideOr(x0, z0, hn);
               double along = moved > 0.05 ? (h0 - hn) / moved : 0.0; // the slope's descent per block where it lands
               double into = Math.max(0.0, -this.vy - Math.max(0.0, along) * len);
               double keep = 1.0 - Math.min(0.4, Math.max(0.0, into - 0.4) * 0.35);
               this.vx *= keep;
               this.vz *= keep;
               this.impact = Math.max(this.impact, into > 0.6 ? into * 0.3 : 0.0);
               this.y = hn;
               this.ground = true;
            }
         }
      }
      if (len == 0.0 && this.ground) {
         double hn = this.ride(this.x, this.z);
         if (!Double.isNaN(hn) && Math.abs(hn - this.y) < 1.6) {
            this.y = hn;
         }
      }
      if (this.ground) {
         this.vy = this.y - y0;
         if (groundAtStart && ridden) {
            // what the slope under the sled didn't account for (a bank or a step it rode up, a dip it dropped into):
            // climbing costs speed, as it would; a drop gives some back
            double predicted = this.gx0 * (this.x - x0) + this.gz0 * (this.z - z0);
            double extra = this.vy - predicted;
            double sp = this.speed();
            if (this.motor && this.throttle > 0.0F && extra > 0.0) {
               // [1.2.5] under power the track does the climbing, so a snowmobile crawls up a step from a standstill,
               // but only up to about 50 degrees: steeper than that the track spins and it pays for the height in speed
               double run = Math.hypot(this.x - x0, this.z - z0);
               if (this.vy / Math.max(run, 0.25) < 1.2) {
                  extra = 0.0;
               }
            }
            if (sp > 1.0E-6 && Math.abs(extra) > 0.02) {
               double v2 = sp * sp + (extra > 0.0 ? -2.0 * G * extra : 1.7 * G * -extra);
               double ns = Math.min(MAX, Math.sqrt(Math.max(0.0, v2)));
               this.vx *= ns / sp;
               this.vz *= ns / sp;
            }
         }
      }
   }

   /** 0 clear; 1 the ground rises like a wall; 2 something stands in the way */
   private int wall(double nx, double nz, double hn, double slen) {
      // the ground itself (not the averaged ride) just ahead, over at least a third of a block: steeper than about 65
      // degrees is a wall, whatever the speed (a step of one block is a ramp the sled rides up)
      double dx = nx - this.x, dz = nz - this.z, d = Math.hypot(dx, dz);
      if (d > 1.0E-6) {
         double look = Math.max(d, 0.5);
         double here = this.surface(this.x, this.z), ahead = this.surface(this.x + dx / d * look, this.z + dz / d * look);
         // (a bank up to two blocks is snow it rides up, losing speed as it climbs, and flies off the top)
         if (!Double.isNaN(here) && !Double.isNaN(ahead) && ahead - here > 2.3 && ahead > this.y + 1.2) {
            this.why = String.format("steep rise %.2f over %.2f (y %.2f)", ahead - here, look, this.y);
            return 1;
         }
      }
      if (hn - this.y > 2.4) {
         this.why = String.format("step %.2f", hn - this.y);
         return 1;
      }
      if (this.motor && this.ground && hn - this.y > Math.max(1.25, slen * 1.6)) {
         // [1.2.5] a snowmobile can't drive up a face steeper than about 55 degrees: it's a wall to the track
         this.why = String.format("too steep for the track %.2f over %.2f", hn - this.y, slen);
         return 1;
      }
      // the box rides just above the snow under all of it (on a side slope the high side is higher than the middle)
      double base = Math.max(this.y, hn);
      for (int i = 0; i < 4; i++) {
         double c = this.surface(nx + ((i & 1) == 0 ? -HALF : HALF), nz + ((i >> 1) == 0 ? -HALF : HALF));
         if (!Double.isNaN(c) && c < this.y + STEP) {
            base = Math.max(base, c);
         }
      }
      base += 0.12;
      if (this.t.blocked(nx - HALF, base, nz - HALF, nx + HALF, base + 0.7, nz + HALF)) {
         this.why = String.format("blocked box at %.2f %.2f %.2f (base %.2f, y %.2f)", nx, base, nz, base, this.y);
         return 2;
      }
      return 0;
   }
}
