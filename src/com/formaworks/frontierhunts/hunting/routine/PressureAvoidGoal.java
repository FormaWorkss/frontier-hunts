package com.formaworks.frontierhunts.hunting.routine;

import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * [routines] Prey species of the newer wildlife (pronghorn, bison, boar, grouse, duck) drift out of hard-hunted
 * 64-block cells into the quietest neighbouring one, at a calm walk. Checks pressure every ~15 s at most.
 */
public final class PressureAvoidGoal extends Goal {
   private final WildlifeMob mob;
   private long nextCheck;
   private long until;
   private Vec3 target;

   public PressureAvoidGoal(WildlifeMob mob) {
      this.mob = mob;
      this.setFlags(EnumSet.of(Goal.Flag.MOVE));
   }

   @Override
   public boolean canUse() {
      if (this.mob.species != null && RoutineHooks.prey(this.mob.species) && this.mob.behavior() == 0 && this.mob.level() instanceof ServerLevel level && RoutineConfig.pressure()) {
         long now = level.getGameTime();
         if (now < this.nextCheck) {
            return false;
         } else {
            this.nextCheck = now + 300L + this.mob.getRandom().nextInt(200);
            PressureStore store = PressureStore.of(level);
            int cx = PressureStore.cell(this.mob.getX());
            int cz = PressureStore.cell(this.mob.getZ());
            float here = store.value(now, cx, cz);
            if (PressureStore.level01(here) < 0.3F) {
               return false;
            } else {
               int bx = 0;
               int bz = 0;
               float best = here;

               for (int dx = -1; dx <= 1; dx++) {
                  for (int dz = -1; dz <= 1; dz++) {
                     float v = store.value(now, cx + dx, cz + dz);
                     if (v < best) {
                        best = v;
                        bx = dx;
                        bz = dz;
                     }
                  }
               }

               if (best > here * 0.6F || bx == 0 && bz == 0) {
                  return false;
               } else {
                  Vec3 centre = new Vec3((cx + bx + 0.5) * PressureStore.CELL_SIZE, this.mob.getY(), (cz + bz + 0.5) * PressureStore.CELL_SIZE);
                  this.target = LandRandomPos.getPosTowards(this.mob, 24, 7, centre);
                  return this.target != null;
               }
            }
         }
      } else {
         return false;
      }
   }

   @Override
   public void start() {
      this.until = this.mob.level().getGameTime() + 400L;
      this.mob.getNavigation().moveTo(this.target.x, this.target.y, this.target.z, 0.9);
   }

   @Override
   public boolean canContinueToUse() {
      return this.mob.behavior() == 0 && !this.mob.getNavigation().isDone() && this.mob.level().getGameTime() < this.until;
   }

   @Override
   public void stop() {
      this.mob.getNavigation().stop();
   }
}
