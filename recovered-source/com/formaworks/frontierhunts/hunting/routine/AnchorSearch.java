package com.formaworks.frontierhunts.hunting.routine;

import com.formaworks.frontierhunts.hunting.GameSpecies;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * Incremental home-range survey. Each {@link #step} scores a handful of candidate columns (bedding, then feeding, then
 * the nearest water ring by ring), so establishing a range is spread over a second or two of game time and never
 * scans a big area in one tick. A global per-tick budget keeps many herds from searching at once.
 */
final class AnchorSearch {
   private static final int BED_SAMPLES = 150;
   private static final int FEED_SAMPLES = 150;
   private static long budgetTick = Long.MIN_VALUE;
   private static int budgetUsed;
   private static final int BUDGET_PER_TICK = 14;
   final HomeRange range;
   final BlockPos centre;
   final int radius;
   final boolean[] want = new boolean[3];
   final boolean relocating;
   int phase;
   int samples;
   BlockPos best;
   float bestScore = Habitat.REJECT;
   BlockPos bed;
   BlockPos feed;
   BlockPos drink;
   BlockPos water;
   int ring;
   private final RandomSource random;

   AnchorSearch(HomeRange range, BlockPos centre, int radius, boolean bed, boolean feed, boolean water, boolean relocating, RandomSource random) {
      this.range = range;
      this.centre = centre.immutable();
      this.radius = radius;
      this.want[HomeRange.BED] = bed;
      this.want[HomeRange.FEED] = feed;
      this.want[HomeRange.WATER] = water;
      this.relocating = relocating;
      this.random = random;
      this.bed = bed ? null : range.anchors[HomeRange.BED];
      this.feed = feed ? null : range.anchors[HomeRange.FEED];
      this.phase = bed ? 0 : (feed ? 1 : 2);
   }

   static int radiusFor(GameSpecies species) {
      int r = RoutineConfig.homeRange();
      return switch (species) {
         case ELK -> Math.round(r * 1.5F);
         case MOOSE -> Math.round(r * 1.25F);
         default -> r;
      };
   }

   private static boolean takeBudget(long now) {
      if (budgetTick != now) {
         budgetTick = now;
         budgetUsed = 0;
      }

      if (budgetUsed >= BUDGET_PER_TICK) {
         return false;
      } else {
         budgetUsed++;
         return true;
      }
   }

   private BlockPos sample(BlockPos around, int r) {
      double a = this.random.nextDouble() * Math.PI * 2.0;
      double d = r * Math.sqrt(this.random.nextDouble());
      return new BlockPos(around.getX() + (int)Math.round(Math.cos(a) * d), around.getY(), around.getZ() + (int)Math.round(Math.sin(a) * d));
   }

   private float pressurePenalty(ServerLevel level, BlockPos p, float weight) {
      if (!RoutineConfig.pressure()) {
         return 0.0F;
      } else {
         float units = PressureStore.of(level).value(level.getGameTime(), p);
         return weight * PressureStore.level01(units) * (this.relocating ? 1.6F : 1.0F);
      }
   }

   /** @return true when the search has finished (results applied to the range, or the range marked unusable). */
   boolean step(ServerLevel level) {
      long now = level.getGameTime();

      for (int i = 0; i < 6 && takeBudget(now); i++) {
         if (this.phase == 0) {
            BlockPos c = this.sample(this.centre, this.radius);
            BlockPos feet = Habitat.surface(level, c.getX(), c.getZ());
            if (feet != null && Math.abs(feet.getY() - this.centre.getY()) <= 28) {
               float s = Habitat.bedScore(level, this.range.species, feet);
               if (s > Habitat.REJECT) {
                  double d = Math.sqrt(feet.distSqr(this.centre)) / this.radius;
                  s -= 1.2F * (float)(d * d) + this.pressurePenalty(level, feet, 3.0F);
                  if (s > this.bestScore) {
                     this.bestScore = s;
                     this.best = feet;
                  }
               }
            }

            if (++this.samples >= BED_SAMPLES) {
               if (this.best == null || this.bestScore < 1.5F) {
                  return this.fail(now);
               }

               this.bed = this.best;
               this.nextPhase();
            }
         } else if (this.phase == 1) {
            BlockPos around = this.bed == null ? this.centre : this.bed;
            BlockPos c = this.sample(around, Math.round(this.radius * 1.15F));
            BlockPos feet = Habitat.surface(level, c.getX(), c.getZ());
            if (feet != null && Math.abs(feet.getY() - around.getY()) < 40) {
               float s = Habitat.feedScore(level, this.range.species, feet);
               if (s > Habitat.REJECT) {
                  double fromBed = Math.sqrt(feet.distSqr(around));
                  if (fromBed < 16.0) {
                     s -= 3.0F;
                  } else if (fromBed > this.radius * 1.3) {
                     s -= 1.5F;
                  }

                  double d = Math.sqrt(feet.distSqr(this.centre)) / this.radius;
                  s -= 0.8F * (float)(d * d) + this.pressurePenalty(level, feet, 3.5F);
                  if (s > this.bestScore) {
                     this.bestScore = s;
                     this.best = feet;
                  }
               }
            }

            if (++this.samples >= FEED_SAMPLES) {
               if (this.best == null || this.bestScore < 0.8F) {
                  return this.fail(now);
               }

               this.feed = this.best;
               this.nextPhase();
            }
         } else if (this.phase == 2) {
            // water: nearest drinkable bank to the middle of the range, searched outwards ring by ring
            BlockPos mid = this.bed != null && this.feed != null
               ? new BlockPos((this.bed.getX() + this.feed.getX()) / 2, this.bed.getY(), (this.bed.getZ() + this.feed.getZ()) / 2)
               : this.centre;
            int r = 2 + this.ring * 3;
            int count = Math.max(6, (int)(Math.PI * 2.0 * r / 3.0));

            for (int k = 0; k < count; k++) {
               double a = (k + this.random.nextDouble() * 0.5) / count * Math.PI * 2.0;
               int x = mid.getX() + (int)Math.round(Math.cos(a) * r);
               int z = mid.getZ() + (int)Math.round(Math.sin(a) * r);
               BlockPos[] spot = Habitat.drinkSpot(level, x, z);
               if (spot != null && Math.abs(spot[0].getY() - mid.getY()) < 30) {
                  float s = -(float)Math.sqrt(spot[0].distSqr(mid)) * 0.05F - this.pressurePenalty(level, spot[0], 2.0F);
                  if (s > this.bestScore) {
                     this.bestScore = s;
                     this.drink = spot[0];
                     this.water = spot[1];
                  }
               }
            }

            this.ring++;
            // finish once a ring beyond the first hit has been checked, or at 1.5x the range
            if (this.drink != null && this.ring * 3 > Math.sqrt(this.drink.distSqr(mid)) + 4.0 || r > this.radius * 1.5F) {
               this.nextPhase();
            }

            break;
         } else {
            return this.finish(now);
         }
      }

      return this.phase > 2 && this.finish(now);
   }

   private void nextPhase() {
      this.phase++;

      while (this.phase < 3 && !this.want[this.phase]) {
         this.phase++;
      }

      this.samples = 0;
      this.best = null;
      this.bestScore = Habitat.REJECT;
   }

   private boolean fail(long now) {
      if (this.relocating) {
         // nowhere quieter with cover: stay put, and don't look again for two days
         this.range.relocatedAt = now;
      } else if (this.range.ready()) {
         // lost one anchor and nothing replaces it here: keep the rest, try again tomorrow
         this.range.retryAt = now + 24000L;
      } else if (!this.range.ready()) {
         this.range.status = 3;
         this.range.retryAt = now + 24000L;
      }

      this.range.search = null;
      return true;
   }

   private boolean finish(long now) {
      HomeRange r = this.range;
      if (this.want[HomeRange.BED] && this.bed != null) {
         r.invalidateAnchor(HomeRange.BED);
         r.anchors[HomeRange.BED] = this.bed;
      }

      if (this.want[HomeRange.FEED] && this.feed != null) {
         r.invalidateAnchor(HomeRange.FEED);
         r.anchors[HomeRange.FEED] = this.feed;
      }

      if (this.want[HomeRange.WATER]) {
         r.invalidateAnchor(HomeRange.WATER);
         if (this.drink != null) {
            r.anchors[HomeRange.WATER] = this.drink;
            r.waterBlock = this.water;
         } else {
            r.retryAt = now + 24000L;
         }
      }

      if (this.relocating) {
         r.relocations++;
         r.relocatedAt = now;
         r.origin = this.centre;
      }

      r.status = r.anchors[HomeRange.BED] != null && r.anchors[HomeRange.FEED] != null ? 2 : 3;
      if (r.status == 3) {
         r.retryAt = now + 24000L;
      }

      r.establishedAt = now;
      r.search = null;
      return true;
   }
}
