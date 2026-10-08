package com.formaworks.frontierhunts.clothing;

import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * [clothing] The noses of the 2026 wildlife (bears, wolves, coyotes, cougars, boar, bison, pronghorn, the big cats; elk and
 * moose of that family too). Before this they only saw and heard a hunter; now a hunter upwind of them is winded through
 * the same scent plume the deer use ({@link Wilderness#scent}) times {@link Scent#factor}, so the carbon base layer,
 * the spray, sweat and wet clothes work on every animal. Birds (grouse, ducks) do not hunt by smell and are left out;
 * the tracking hound follows game, not its owner, and is not a {@link WildlifeMob}.
 *
 * <p>Cost: one wind sample and a loop over the level's players within 48 blocks, every second per animal (the caller
 * already throttles it with {@code AiThrottle}).
 */
public final class Noses {
   /**
    * Plume strength x scent factor x nose above which the animal is winded: a hunter in plain clothes straight upwind of a
    * bear, wolf or coyote is smelled from 48 blocks, of a cougar from ~45, of a lion or cheetah from ~35; a full fresh carbon
    * layer standing still is never winded (0.12 x 1.6 x 0.93 = 0.18 even for a bear at 5 blocks), sweating and running
    * in it gives you away again.
    */
   public static final double WINDED = 0.40;
   private static final double REACH = 48.0;

   private Noses() {
   }

   /** How keen a species' nose is (1 = a whitetail's). 0 = does not wind hunters. */
   public static double nose(WildlifeSpecies s) {
      return switch (s) {
         case GROUSE, DUCK -> 0.0;
         case GRIZZLY, BLACK_BEAR, POLAR_BEAR -> 1.6;
         case WOLF -> 1.4;
         case COYOTE, BOAR -> 1.3;
         case ELK, MOOSE -> 1.15;
         case BISON -> 1.0;
         case COUGAR, PRONGHORN -> 0.8;
         case LION, PANTHER, CHEETAH -> 0.7;
      };
   }

   /** The hunter this animal winds right now (strongest whiff), or null. Server only. */
   public static Player winded(WildlifeMob mob) {
      double nose = nose(mob.species);
      if (nose <= 0.0 || !(mob.level() instanceof ServerLevel level)) {
         return null;
      }
      Player best = null;
      double top = WINDED;
      Wilderness.Wind wind = null;
      double thermal = 0.0;
      for (Player p : level.players()) {
         if (!p.isAlive() || p.isSpectator() || p.isCreative() || mob.distanceToSqr(p) > REACH * REACH) {
            continue;
         }
         if (wind == null) {
            wind = Wilderness.wind(level.getSeed(), level.getGameTime(), level.isRaining(), level.isThundering());
            thermal = Wilderness.thermal(level.getDayTime(), level.isRaining(), level.getSkyDarken() / 15.0F);
         }
         double s = whiff(level, wind, thermal, mob, p, nose);
         if (s > top) {
            top = s;
            best = p;
         }
      }
      return best;
   }

   private static double whiff(ServerLevel level, Wilderness.Wind wind, double thermal, WildlifeMob mob, Player p, double nose) {
      double plume = Wilderness.scent(wind, mob.getX() - p.getX(), mob.getZ() - p.getZ(), mob.getY() - p.getY(), level.isRaining(), false, thermal);
      return plume * Scent.factor(p) * nose;
   }

   /** How strongly this animal smells this hunter right now (winded above {@link #WINDED}). Server only; tests and debug. */
   public static double whiff(WildlifeMob mob, Player p) {
      if (!(mob.level() instanceof ServerLevel level)) {
         return 0.0;
      }
      Wilderness.Wind wind = Wilderness.wind(level.getSeed(), level.getGameTime(), level.isRaining(), level.isThundering());
      double thermal = Wilderness.thermal(level.getDayTime(), level.isRaining(), level.getSkyDarken() / 15.0F);
      return whiff(level, wind, thermal, mob, p, nose(mob.species));
   }
}
