package com.formaworks.frontierhunts.phone.games;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * [phone] Flush!: sixty seconds of wingshooting over a marsh at dawn. Ducks, teal, pheasant, grouse, doves and geese
 * flush from the reeds or pass high; a double gun, two shells, a quick reload; the shot pattern takes a moment to arrive,
 * so lead fast birds. Hens and hawks are protected (shooting one costs points). Hits in a row build a multiplier.
 *
 * <p>Fully deterministic from the seed and the list of shots (integer ticks and positions, StrictMath): the server
 * replays a run to check its score for the leaderboard, and both players of an online race fly the same birds. The
 * optional AI marksman shares the flock (vs AI mode only).
 */
public final class Flush {
   public static final int TICKS = 1200;
   public static final float W = 100.0F;
   public static final float H = 48.0F;
   public static final float COVER = 39.0F;
   public static final int TRAVEL = 2;
   public static final int RELOAD = 28;
   public static final int COOLDOWN = 4;
   public static final float PATTERN = 3.6F;

   public enum Kind {
      MALLARD("Mallard", 10, 2.2F, false),
      TEAL("Teal", 20, 1.6F, false),
      PHEASANT("Pheasant", 15, 2.4F, false),
      GROUSE("Grouse", 20, 1.8F, false),
      DOVE("Dove", 25, 1.4F, false),
      GOOSE("Goose", 12, 3.0F, false),
      HEN("Hen pheasant", -20, 2.4F, true),
      HAWK("Hawk", -30, 2.6F, true);

      public final String title;
      public final int points;
      public final float radius;
      public final boolean protectedBird;

      Kind(String title, int points, float radius, boolean p) {
         this.title = title;
         this.points = points;
         this.radius = radius;
         this.protectedBird = p;
      }
   }

   public static final class Bird {
      public final int id;
      public final Kind kind;
      public final int spawn;
      final float x0, y0, vx, climb, tau, sink, amp, freq, phase;
      /** tick it was hit (falls from there), or -1 */
      public int hitAt = -1;
      /** 0 player, 1 AI */
      public int hitBy = -1;
      public float hitX, hitY;
      public boolean gone;

      Bird(int id, Kind kind, int spawn, float x0, float y0, float vx, float climb, float tau, float sink, float amp, float freq, float phase) {
         this.id = id;
         this.kind = kind;
         this.spawn = spawn;
         this.x0 = x0;
         this.y0 = y0;
         this.vx = vx;
         this.climb = climb;
         this.tau = tau;
         this.sink = sink;
         this.amp = amp;
         this.freq = freq;
         this.phase = phase;
      }

      /** Position at a (fractional) tick while flying. */
      public float x(float tick) {
         float t = tick - this.spawn;
         return this.x0 + this.vx * t;
      }

      public float y(float tick) {
         float t = tick - this.spawn;
         float y = this.y0 - this.climb * (1.0F - (float)StrictMath.exp(-t / this.tau)) + this.sink * t;
         return y + this.amp * (float)StrictMath.sin(this.freq * t + this.phase);
      }

      /** heading +1 right, -1 left */
      public int dir() {
         return this.vx >= 0.0F ? 1 : -1;
      }

      public boolean flying(int tick) {
         return !this.gone && this.hitAt < 0 && tick >= this.spawn;
      }
   }

   /** Something to show: a hit, a miss, a penalty, a double. */
   public record Event(int tick, int type, float x, float y, int points, int shooter, String text) {
   }

   public static final int E_HIT = 0, E_MISS = 1, E_PROTECTED = 2, E_DOUBLE = 3, E_SHOT = 4, E_RELOAD = 5;

   private final long seed;
   private final Random spawnRnd;
   public final List<Bird> birds = new ArrayList<>();
   private final List<int[]> pending = new ArrayList<>();
   public final List<Event> events = new ArrayList<>();
   public int tick;
   private int nextWave;
   private int nextId;
   // the guns: [0] player, [1] AI
   public final int[] shells = {2, 2};
   public final int[] reloadUntil = {0, 0};
   public final int[] cooldown = {0, 0};
   public final int[] score = {0, 0};
   public final int[] streak = {0, 0};
   public final int[] hits = {0, 0};
   public final int[] shots = {0, 0};
   public final int[] bestStreak = {0, 0};
   private final int[] log = new int[3 * 400];
   private int logged;
   // the AI marksman
   private final boolean ai;
   private final int aiLevel;
   private final Random aiRnd;
   private int aiTarget = -1;
   private int aiReadyAt;

   public Flush(long seed, boolean ai, int aiLevel) {
      this.seed = seed;
      this.spawnRnd = new Random(seed);
      this.ai = ai;
      this.aiLevel = aiLevel;
      this.aiRnd = new Random(seed * 31L + 7L);
      this.nextWave = 20;
   }

   public boolean over() {
      return this.tick >= TICKS && this.pending.isEmpty();
   }

   public int secondsLeft() {
      return Math.max(0, (TICKS - this.tick + 19) / 20);
   }

   // ------------------------------------------------------------------------------------------------ the flock

   private void spawnWave() {
      Random r = this.spawnRnd;
      float prog = Math.min(1.0F, this.tick / (float)TICKS);
      int count = 1 + r.nextInt(prog > 0.5F ? 4 : 3);
      float speedK = 1.0F + prog * 0.55F;
      int kindRoll = r.nextInt(100);
      Kind base;
      if (kindRoll < 30) {
         base = Kind.MALLARD;
      } else if (kindRoll < 44) {
         base = Kind.PHEASANT;
      } else if (kindRoll < 56) {
         base = Kind.GROUSE;
      } else if (kindRoll < 68) {
         base = Kind.TEAL;
      } else if (kindRoll < 80) {
         base = Kind.DOVE;
      } else if (kindRoll < 92) {
         base = Kind.GOOSE;
      } else {
         base = Kind.HAWK;
      }
      boolean fromLeft = r.nextBoolean();
      float cx = 15.0F + r.nextFloat() * 70.0F;
      for (int i = 0; i < count; i++) {
         Kind k = base;
         if (k == Kind.PHEASANT && r.nextInt(100) < 35) {
            k = Kind.HEN;
         }
         if (k == Kind.HAWK && i > 0) {
            k = Kind.DOVE;
         }
         int spawn = this.tick + i * (2 + r.nextInt(6));
         float dir = fromLeft ? 1.0F : -1.0F;
         Bird b;
         switch (k) {
            case GOOSE, HAWK -> {
               // high passing birds from the side
               float y = 4.0F + r.nextFloat() * 12.0F + i * 2.0F;
               float vx = dir * (0.32F + r.nextFloat() * 0.12F) * speedK;
               b = new Bird(this.nextId++, k, spawn, fromLeft ? -6.0F - i * 4.0F : W + 6.0F + i * 4.0F, y, vx, 0.0F, 1.0F, (r.nextFloat() - 0.5F) * 0.01F,
                  0.6F, 0.05F, r.nextFloat() * 6.0F);
            }
            case DOVE -> {
               float y = 8.0F + r.nextFloat() * 18.0F;
               float vx = dir * (0.62F + r.nextFloat() * 0.18F) * speedK;
               b = new Bird(this.nextId++, k, spawn, fromLeft ? -5.0F : W + 5.0F, y, vx, 0.0F, 1.0F, (r.nextFloat() - 0.5F) * 0.03F, 2.4F, 0.22F,
                  r.nextFloat() * 6.0F);
            }
            default -> {
               // flushers rise out of the cover
               float x = Math.max(6.0F, Math.min(W - 6.0F, cx + (r.nextFloat() - 0.5F) * 18.0F));
               boolean steep = k == Kind.PHEASANT || k == Kind.HEN;
               float climb = steep ? 26.0F + r.nextFloat() * 8.0F : 18.0F + r.nextFloat() * 10.0F;
               float tau = steep ? 9.0F : 14.0F;
               float speed = switch (k) {
                  case TEAL -> 0.62F;
                  case GROUSE -> 0.56F;
                  case MALLARD -> 0.42F;
                  default -> 0.38F;
               };
               float vx = dir * (speed + r.nextFloat() * 0.1F) * speedK;
               if (r.nextInt(4) == 0) {
                  vx = -vx;
               }
               float amp = k == Kind.TEAL ? 1.6F : (k == Kind.GROUSE ? 1.2F : 0.4F);
               b = new Bird(this.nextId++, k, spawn, x, COVER + 1.0F, vx, climb, tau, -0.02F, amp, 0.18F, r.nextFloat() * 6.0F);
            }
         }
         this.birds.add(b);
      }
      this.nextWave = this.tick + Math.max(14, (int)((52 + r.nextInt(30)) * (1.0F - prog * 0.45F)));
   }

   // ------------------------------------------------------------------------------------------------ shooting

   /** The player fires at (x, y) now; false if the gun is empty, reloading or the round is over. */
   public boolean shoot(float x, float y) {
      return this.fire(0, Math.round(x * 100.0F), Math.round(y * 100.0F), true);
   }

   private boolean fire(int who, int x100, int y100, boolean record) {
      if (this.tick >= TICKS || this.tick < this.reloadUntil[who] || this.tick < this.cooldown[who] || this.shells[who] <= 0) {
         return false;
      }
      x100 = Math.max(-500, Math.min(10500, x100));
      y100 = Math.max(-500, Math.min(5300, y100));
      this.shells[who]--;
      this.shots[who]++;
      this.cooldown[who] = this.tick + COOLDOWN;
      if (this.shells[who] == 0) {
         this.reloadUntil[who] = this.tick + RELOAD;
         this.shells[who] = 2;
      }
      this.pending.add(new int[]{this.tick + TRAVEL, x100, y100, who});
      this.events.add(new Event(this.tick, E_SHOT, x100 / 100.0F, y100 / 100.0F, 0, who, ""));
      if (record && who == 0 && this.logged + 3 <= this.log.length) {
         this.log[this.logged++] = this.tick;
         this.log[this.logged++] = x100;
         this.log[this.logged++] = y100;
      }
      return true;
   }

   /** Reloads early (one shell left). */
   public boolean reload() {
      if (this.tick < this.reloadUntil[0] || this.shells[0] >= 2 || this.tick >= TICKS) {
         return false;
      }
      this.reloadUntil[0] = this.tick + RELOAD / 2;
      this.shells[0] = 2;
      this.events.add(new Event(this.tick, E_RELOAD, 0, 0, 0, 0, ""));
      return true;
   }

   public boolean reloading(int who) {
      return this.tick < this.reloadUntil[who];
   }

   /** The shots fired so far: {tick, x*100, y*100} triples, for the server's replay. */
   public int[] shotLog() {
      return java.util.Arrays.copyOf(this.log, this.logged);
   }

   // ------------------------------------------------------------------------------------------------ the clock

   public void step() {
      if (this.over()) {
         return;
      }
      if (this.tick < TICKS && this.tick >= this.nextWave) {
         this.spawnWave();
      }
      if (this.ai && this.tick < TICKS) {
         this.aiThink();
      }
      // shot patterns arriving now
      for (int i = 0; i < this.pending.size(); i++) {
         int[] p = this.pending.get(i);
         if (p[0] <= this.tick) {
            this.resolve(p[1] / 100.0F, p[2] / 100.0F, p[3]);
            this.pending.remove(i--);
         }
      }
      // birds leaving the field
      for (Bird b : this.birds) {
         if (b.gone) {
            continue;
         }
         if (b.hitAt >= 0) {
            if (this.tick - b.hitAt > 40) {
               b.gone = true;
            }
            continue;
         }
         if (this.tick >= b.spawn) {
            float x = b.x(this.tick), y = b.y(this.tick);
            if (x < -12.0F || x > W + 12.0F || y < -12.0F || y > H + 4.0F) {
               b.gone = true;
            }
         }
      }
      this.birds.removeIf(b -> b.gone && this.tick - b.spawn > 200);
      this.tick++;
   }

   private void resolve(float x, float y, int who) {
      int got = 0;
      int pts = 0;
      boolean protectedHit = false;
      for (Bird b : this.birds) {
         if (!b.flying(this.tick)) {
            continue;
         }
         float bx = b.x(this.tick), by = b.y(this.tick);
         float dx = bx - x, dy = by - y;
         float r = PATTERN + b.kind.radius;
         if (dx * dx + dy * dy <= r * r && by < COVER + 0.5F) {
            b.hitAt = this.tick;
            b.hitBy = who;
            b.hitX = bx;
            b.hitY = by;
            if (b.kind.protectedBird) {
               protectedHit = true;
               this.score[who] = Math.max(0, this.score[who] + b.kind.points);
               this.events.add(new Event(this.tick, E_PROTECTED, bx, by, b.kind.points, who, b.kind.title + "! Protected"));
            } else {
               got++;
               this.hits[who]++;
               int mult = 4 + Math.min(4, this.streak[who]);
               int p = b.kind.points * mult / 4;
               pts += p;
               this.events.add(new Event(this.tick, E_HIT, bx, by, p, who, b.kind.title));
            }
         }
      }
      if (protectedHit) {
         this.streak[who] = 0;
      } else if (got > 0) {
         this.streak[who]++;
         this.bestStreak[who] = Math.max(this.bestStreak[who], this.streak[who]);
         if (got >= 2) {
            int bonus = pts / 2;
            pts += bonus;
            this.events.add(new Event(this.tick, E_DOUBLE, x, y - 4.0F, bonus, who, got >= 3 ? "Triple!" : "Double!"));
         }
         this.score[who] += pts;
      } else {
         this.streak[who] = 0;
         this.events.add(new Event(this.tick, E_MISS, x, y, 0, who, ""));
      }
   }

   // ------------------------------------------------------------------------------------------------ the AI marksman

   private void aiThink() {
      int react = this.aiLevel == 0 ? 24 : (this.aiLevel == 1 ? 15 : 9);
      float err = this.aiLevel == 0 ? 3.6F : (this.aiLevel == 1 ? 2.3F : 1.4F);
      Bird target = null;
      for (Bird b : this.birds) {
         if (b.id == this.aiTarget && b.flying(this.tick)) {
            target = b;
         }
      }
      if (target == null) {
         // pick the nearest legal bird in view (easy sometimes mistakes a hen or a hawk)
         float best = 1e9F;
         for (Bird b : this.birds) {
            if (!b.flying(this.tick) || this.tick - b.spawn < 3) {
               continue;
            }
            float x = b.x(this.tick), y = b.y(this.tick);
            if (x < 2.0F || x > W - 2.0F || y < 1.0F || y > COVER - 2.0F) {
               continue;
            }
            if (b.kind.protectedBird && !(this.aiLevel == 0 && this.aiRnd.nextInt(5) == 0)) {
               continue;
            }
            float d = Math.abs(x - W / 2.0F) + this.aiRnd.nextFloat() * 20.0F;
            if (d < best) {
               best = d;
               target = b;
            }
         }
         if (target != null) {
            this.aiTarget = target.id;
            this.aiReadyAt = this.tick + react + this.aiRnd.nextInt(react / 2 + 1);
         }
         return;
      }
      if (this.tick < this.aiReadyAt) {
         return;
      }
      // lead the bird: where it will be when the pattern arrives
      float t = this.tick + TRAVEL;
      float x = target.x(t) + (float)this.aiRnd.nextGaussian() * err;
      float y = target.y(t) + (float)this.aiRnd.nextGaussian() * err;
      if (this.fire(1, Math.round(x * 100.0F), Math.round(y * 100.0F), false)) {
         this.aiReadyAt = this.tick + COOLDOWN + 3 + this.aiRnd.nextInt(5);
      }
   }

   // ------------------------------------------------------------------------------------------------ replay

   /** The score of a run, replayed from its seed and shot log; -1 if the log is malformed. */
   public static int replay(long seed, int[] log) {
      if (log == null || log.length % 3 != 0 || log.length > 1200) {
         return -1;
      }
      Flush f = new Flush(seed, false, 0);
      int i = 0;
      int last = -1;
      while (!f.over()) {
         while (i < log.length && log[i] == f.tick) {
            if (log[i] < last) {
               return -1;
            }
            last = log[i];
            f.fire(0, log[i + 1], log[i + 2], false);
            i += 3;
         }
         if (i < log.length && log[i] < f.tick) {
            return -1;
         }
         f.step();
         if (f.tick > TICKS + 10) {
            break;
         }
      }
      return i == log.length ? f.score[0] : -1;
   }

   public long seed() {
      return this.seed;
   }
}
