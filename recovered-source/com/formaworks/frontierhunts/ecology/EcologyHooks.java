package com.formaworks.frontierhunts.ecology;

import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** [ecology] Entry points for the few hook lines in shared classes (Whitetail, WildlifeMob). Cheap and side-safe. */
public final class EcologyHooks {
   private EcologyHooks() {
   }

   /**
    * Whitetail downed tick. 1 = the body is gone (a predator kill that rotted to bones: discard now), 0 = keep, and for
    * predator kills the old 36000-tick limit does not apply ({@link #predatorKill}).
    */
   public static boolean carcassGone(Whitetail w, int downTicks) {
      return downTicks % 20 == 0 && !w.level().isClientSide && KillSites.deerExpired(w);
   }

   /** a predator-killed deer lies for its own lifetime (EcologyConfig.carcassDays), not the hunter-kill one */
   public static boolean predatorKill(Whitetail w) {
      return KillSites.predatorKill(w);
   }

   /**
    * WildlifeMob.hurt: a predator hurt by its prey (a bison or boar charging, a cornered elk) or while hunting backs
    * off instead of fighting back.
    */
   public static boolean yields(WildlifeMob mob, LivingEntity attacker) {
      if (attacker instanceof Player || Predator.of(mob.species) == null) {
         return false;
      }
      return mob.hunting() || Prey.of(attacker) != null;
   }
}
