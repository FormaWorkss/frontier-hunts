package com.formaworks.frontierhunts.wingshot;

import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * [wingshot] Birds going about their day near a player: a flock of ducks now and then gets up and flies to other
 * water (mostly at dawn and dusk; never off a decoy spread), and on spring and autumn mornings and evenings a ruffed
 * grouse walks to a fallen log, hops up and drums: two or three bouts of accelerating wingbeats (the real NPS
 * recording, the animation timed to its beats) with long still pauses between. Cheap: one roll per bird every
 * 10-20 s, a small block search only when a display starts.
 */
final class BirdLife {
   private BirdLife() {
   }

   /** a grouse's drumming display */
   static final class Drum {
      BlockPos log;
      Vec3 stand;
      float yaw;
      int stage; // 0 walk, 1 on the log
      int t, bouts, nextBout, boutEnd;
      byte variant;
   }

   /** drum bout lengths (ticks) of the two recordings */
   static final int BOUT_A = 222, BOUT_B = 124;

   static void tick(WildlifeMob m, BirdFlight.Ctl c, ServerLevel level) {
      if (c.drum != null) {
         drum(m, c, level);
         return;
      }
      if (m.tickCount < c.nextAmbient) {
         return;
      }
      c.nextAmbient = m.tickCount + 200 + m.getRandom().nextInt(200);
      int b = m.behavior();
      if (b != WildlifeMob.IDLE && b != WildlifeMob.FEED || m.hunting() || m.isPassenger() || m.isLeashed() || m.isBaby()) {
         return;
      }
      Player p = level.getNearestPlayer(m, 96.0);
      if (p == null || p.isSpectator() || p.distanceToSqr(m) < 14.0 * 14.0) {
         return;
      }
      int tod = (int)Math.floorMod(level.getDayTime(), 24000L);
      boolean dawnDusk = tod > 22500 || tod < 2200 || tod > 11000 && tod < 13600;
      if (m.species == WildlifeSpecies.DUCK) {
         if (!WingshotConfig.ambient() || m.getRandom().nextFloat() > (dawnDusk ? 1.0F / 10.0F : 1.0F / 40.0F)) {
            return;
         }
         // ducks on a decoy spread stay put (that is the hunter's set-up)
         if (!com.formaworks.frontierhunts.hunts.HuntContent.liveDecoys(level, m.getX(), m.getY(), m.getZ(), 32.0, 1).isEmpty()) {
            return;
         }
         double a = m.getRandom().nextDouble() * Math.PI * 2.0;
         double r = 50.0 + m.getRandom().nextDouble() * 80.0;
         Vec3 spot = null;
         for (int k = 0; k < 4 && spot == null; k++) {
            double aa = a + k * 1.57;
            spot = waterNear(level, m.getX() + Math.cos(aa) * r, m.getZ() + Math.sin(aa) * r);
         }
         if (spot != null) {
            BirdFlight.relocate(m, level, spot, true);
         }
      } else if (m.species == WildlifeSpecies.GROUSE && WingshotConfig.drumming() && dawnDusk && m.onGround()) {
         int month;
         try {
            month = com.formaworks.frontierhunts.expedition.HuntingCalendar.date(level.getServer().overworld()).month();
         } catch (RuntimeException e) {
            month = 4;
         }
         boolean season = month == 2 || month == 3 || month == 4 || month == 8 || month == 9;
         if (season && m.getRandom().nextInt(5) == 0) {
            start(m, c, level);
         }
      }
   }

   static Vec3 waterNear(ServerLevel level, double x, double z) {
      for (int k = 0; k < 9; k++) {
         double a = k * 2.399, r = k == 0 ? 0.0 : 4.0 + k * 3.0;
         Vec3 w = BirdFlight.openWater(level, Mth.floor(x + Math.cos(a) * r), Mth.floor(z + Math.sin(a) * r));
         if (w != null) {
            return w;
         }
      }
      return null;
   }

   /** Start a display: the nearest fallen log (or stump) within 8 blocks, else right here on the ground. */
   static void start(WildlifeMob m, BirdFlight.Ctl c, ServerLevel level) {
      Drum d = new Drum();
      BlockPos best = null;
      double bd = Double.MAX_VALUE;
      BlockPos at = m.blockPosition();
      BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
      for (int dx = -8; dx <= 8; dx++) {
         for (int dz = -8; dz <= 8; dz++) {
            for (int dy = -2; dy <= 1; dy++) {
               q.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
               if (!level.isLoaded(q)) {
                  continue;
               }
               BlockState s = level.getBlockState(q);
               if (!s.is(BlockTags.LOGS) || !level.getBlockState(q.above()).isAir() || !level.getBlockState(q.above(2)).isAir()) {
                  continue;
               }
               boolean fallen = s.hasProperty(RotatedPillarBlock.AXIS) && s.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y;
               boolean stump = !fallen && !level.getBlockState(q.below()).is(BlockTags.LOGS);
               if (!fallen && !stump) {
                  continue;
               }
               double dist = dx * dx + dz * dz + dy * dy * 4 + (fallen ? 0 : 20);
               if (dist < bd) {
                  bd = dist;
                  best = q.immutable();
               }
            }
         }
      }
      d.log = best;
      d.stand = best != null ? Vec3.atBottomCenterOf(best.above()) : m.position();
      d.yaw = m.getRandom().nextFloat() * 360.0F;
      d.stage = best != null ? 0 : 1;
      d.bouts = 2 + m.getRandom().nextInt(2);
      d.nextBout = 30 + m.getRandom().nextInt(60);
      c.drum = d;
   }

   private static void drum(WildlifeMob m, BirdFlight.Ctl c, ServerLevel level) {
      Drum d = c.drum;
      d.t++;
      int b = m.behavior();
      boolean disturbed = b == WildlifeMob.FLEE || b == WildlifeMob.ALERT || b == WildlifeMob.WARN || m.hurtTime > 0 || m.hunting() || !m.isAlive();
      if (disturbed || d.t > 3600 || d.log != null && !level.getBlockState(d.log).is(BlockTags.LOGS)) {
         stop(m, c);
         return;
      }
      if (d.stage == 0) {
         // walk to the log and hop up onto it
         double hx = d.stand.x - m.getX(), hz = d.stand.z - m.getZ();
         double h2 = hx * hx + hz * hz;
         if (h2 < 1.2 * 1.2 && m.getY() < d.stand.y - 0.3 && m.onGround()) {
            m.setDeltaMovement(hx * 0.2, 0.42, hz * 0.2);
            m.hasImpulse = true;
         } else if (h2 < 0.45 * 0.45 && m.getY() >= d.stand.y - 0.2 && m.onGround()) {
            d.stage = 1;
            m.getNavigation().stop();
         } else if (d.t % 20 == 1) {
            if (!m.getNavigation().moveTo(d.stand.x, d.stand.y, d.stand.z, 0.75) && h2 > 9.0) {
               d.stage = 1; // can't get there: display where it is
               d.stand = m.position();
            }
         } else if (h2 < 3.0) {
            m.getMoveControl().setWantedPosition(d.stand.x, d.stand.y, d.stand.z, 0.5);
         }
         if (d.t > 400) {
            d.stage = 1;
            d.stand = m.position();
         }
         return;
      }
      // on the log: braced, still, facing one way; bouts of drumming with long pauses
      m.getNavigation().stop();
      m.setYRot(d.yaw);
      m.yBodyRot = d.yaw;
      Vec3 v = m.getDeltaMovement();
      m.setDeltaMovement(0.0, v.y, 0.0);
      if (d.boutEnd > 0) {
         if (d.t >= d.boutEnd) {
            d.boutEnd = 0;
            m.flightPhase(Flight.NONE);
            if (--d.bouts <= 0) {
               stop(m, c);
            } else {
               d.nextBout = d.t + 500 + m.getRandom().nextInt(700);
            }
         }
      } else if (d.t >= d.nextBout) {
         d.variant = m.getRandom().nextBoolean() ? Flight.DRUM_A : Flight.DRUM_B;
         d.boutEnd = d.t + (d.variant == Flight.DRUM_A ? BOUT_A : BOUT_B);
         m.flightPhase(d.variant);
         level.playSound(null, m.getX(), m.getY() + 0.2, m.getZ(), d.variant == Flight.DRUM_A ? WingshotContent.DRUM_A.get() : WingshotContent.DRUM_B.get(),
            SoundSource.NEUTRAL, 1.7F, 1.0F);
      }
   }

   static void stop(WildlifeMob m, BirdFlight.Ctl c) {
      c.drum = null;
      if (m.flightPhase() == Flight.DRUM_A || m.flightPhase() == Flight.DRUM_B) {
         m.flightPhase(Flight.NONE);
      }
      c.nextAmbient = m.tickCount + 1200 + m.getRandom().nextInt(1200);
   }
}
