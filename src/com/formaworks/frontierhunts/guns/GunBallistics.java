package com.formaworks.frontierhunts.guns;

import com.formaworks.frontierhunts.expedition.Weapon;

/**
 * [guns2] What each gun really is, in one table: cartridge, a relative muzzle energy (a .30 hunting rifle = 1.0) used
 * for the water splash size, and a short note. Damage and speed live on {@link Weapon}; this keeps the realism notes
 * and the per-cartridge energy next to each other so the numbers stay consistent.
 *
 * <pre>
 * gun                  cartridge              energy  damage  pellets  notes   (energy: splash scale, see waterEnergy)
 * Ridgeline (bolt)     .308 Win               1.35    24*     -        RifleBullet; most powerful, long range
 * Lever rifle          .30-30 Win             1.10    22      -        classic deer rifle, ~150 m
 * Assault rifle        5.56 NATO              0.80    16      -        fast and flat but light for elk/moose/bear
 * Revolver             .357 Magnum            0.75    15      -        close-range sidearm
 * Field pistol         .45 ACP                0.55    12      -        defensive sidearm, poor on big game
 * Pump shotgun         12 ga 00 buck          0.55/p  5.0/p   9        close range only
 * Double barrel        12 ga 00 buck          0.55/p  5.5/p   9        two fast shots
 * Semi-auto shotgun    12 ga #4 buck          0.45/p  3.8/p   12       birds and small game, close
 * Tranquilizer rifle   dart (CO2)             0.25    1       -        no wound
 * (* RifleBullet: damage 24 x energy fraction)
 * </pre>
 */
public final class GunBallistics {
   private GunBallistics() {
   }

   /** Energy for the water splash, relative to a .30 hunting rifle (pellets: per pellet). */
   public static float waterEnergy(Weapon w) {
      return switch (w) {
         // [guns3] visual/aural scale: every gun makes a proper splash, the bigger the round the bigger it gets
         case LEVER_RIFLE -> 1.1F;
         case SEMI_AUTO_RIFLE -> 0.8F;
         case REVOLVER -> 0.75F;
         case FIELD_PISTOL -> 0.55F;
         case PUMP_SHOTGUN, DOUBLE_BARREL -> 0.55F;
         case SEMI_AUTO_SHOTGUN -> 0.45F;
         case TRANQUILIZER_RIFLE -> 0.25F;
         default -> 0.4F;
      };
   }
}
