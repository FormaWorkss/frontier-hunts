package com.formaworks.frontierhunts.ecology;

import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * [ecology] How prey answer a predator, through the animals' own systems: deer, elk and moose get the same alarm a
 * hunter causes (stance and stomp, blow, then the bolt with the herd following; a bull moose stands at bay), the
 * 2026 wildlife flee (birds flush) or, for adult bison and boar, defend and charge; rabbits dash for cover.
 */
final class PreyReaction {
   private PreyReaction() {
   }

   /**
    * A deer bolting from a predator is not hunting pressure: the routines' pressure ledger counts a bolt near a player
    * once per 30 s per animal ({@code fh_spook_at}), so stamping that time first keeps wolves from making an area
    * look hunted (a real hunter's bolt half a minute later is the only thing it hides).
    */
   private static void notPressure(Whitetail w) {
      w.getPersistentData().putLong("fh_spook_at", w.level().getGameTime());
   }

   /** The prey has spotted the predator: head up, staring, stamping; not running yet. */
   static void watch(LivingEntity prey, LivingEntity predator) {
      if (prey instanceof Whitetail w) {
         notPressure(w);
         w.alarm(predator.position(), 0.5F, 120);
      } else if (prey instanceof WildlifeMob m) {
         m.getLookControl().setLookAt(predator, 30.0F, 30.0F);
      }
   }

   /** Run from the predator ({@code startle}: a sudden bolt, from a stalk gone wrong or a missed pounce). */
   static void flee(ServerLevel level, LivingEntity prey, LivingEntity predator, boolean startle) {
      if (prey == null || predator == null || !prey.isAlive()) {
         return;
      }
      Vec3 from = predator.position();
      if (prey instanceof Whitetail w) {
         notPressure(w);
         w.alarm(from, 1.0F, startle ? 200 : 160);
      } else if (prey instanceof WildlifeMob m) {
         m.ecoScare(from, predator, false);
      } else if (prey instanceof Rabbit r) {
         Vec3 away = DefaultRandomPos.getPosAway(r, 10, 4, from);
         if (away != null) {
            r.getNavigation().moveTo(away.x, away.y, away.z, 2.2);
         }
      }
   }

   /** Keep facing the wolves: stance, stomps and snorts (deer family); a bison or boar charges the nearest one. */
   static void standGround(ServerLevel level, LivingEntity prey, LivingEntity predator) {
      if (prey instanceof Whitetail w) {
         notPressure(w);
         w.alarm(predator.position(), 0.55F, 120);
         w.getLookControl().setLookAt(predator, 30.0F, 30.0F);
      } else if (prey instanceof WildlifeMob m) {
         m.ecoScare(predator.position(), predator, true);
      }
   }

   /** The rest of the herd bolts too; a few adult bison (and boar) turn to defend instead. */
   static void herd(ServerLevel level, LivingEntity prey, LivingEntity predator, Prey info) {
      Vec3 from = predator.position();
      if (prey instanceof Whitetail w) {
         List<Whitetail> mates = level.getEntitiesOfClass(Whitetail.class, w.getBoundingBox().inflate(20.0, 6.0, 20.0),
            o -> o != w && o.species() == w.species() && !o.downed());
         for (int i = 0; i < mates.size() && i < 12; i++) {
            notPressure(mates.get(i));
            mates.get(i).alarm(from, 0.9F, 140);
         }
      } else if (prey instanceof WildlifeMob m && !m.species.bird) {
         List<WildlifeMob> mates = level.getEntitiesOfClass(WildlifeMob.class, m.getBoundingBox().inflate(18.0, 6.0, 18.0),
            o -> o != m && o.species == m.species && o.isAlive());
         int defenders = 0;
         for (int i = 0; i < mates.size() && i < 12; i++) {
            WildlifeMob o = mates.get(i);
            Prey p = Prey.of(o);
            boolean defend = (m.species == WildlifeSpecies.BISON || m.species == WildlifeSpecies.BOAR) && p != null && !p.weak()
               && defenders < 2 && o.distanceToSqr(predator) < 196.0 && level.random.nextFloat() < 0.6F;
            if (defend) {
               defenders++;
            }
            o.ecoScare(from, predator, defend);
         }
      }
   }
}
