package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.expedition.WeaponAction;
import net.minecraft.world.item.ItemStack;

final class FieldWeaponMotion {
   static float cycle(Weapon var0, ItemStack var1, double var2) {
      if (var0 != Weapon.LEVER_RIFLE && var0 != Weapon.PUMP_SHOTGUN) {
         return 0.0F;
      } else if (WeaponAction.reload(var0) == WeaponAction.Reload.TUBE && FieldWeaponMesh.reloadProgress(var1, var2) > 0.0F) {
         return FieldWeaponMesh.tubeClosing(var1, var2);
      } else {
         long var4 = FieldGunEffects.shotTime(var1);
         return var4 <= 0L
            ? 0.0F
            : FieldWeaponMesh.window(
               var2 - (double)var4, var0 == Weapon.PUMP_SHOTGUN ? 3.0 : 2.0, var0 == Weapon.PUMP_SHOTGUN ? 9.0 : 7.0, (double)var0.interval
            );
      }
   }

   static float kick(Weapon var0, double var1) {
      return FieldWeaponMesh.window(var1, 0.0, 0.65, var0.pellets > 0 ? 9.0 : 6.0);
   }

   static float rise(Weapon var0) {
      return switch (var0) {
         case DOUBLE_BARREL -> 6.8F;
         case PUMP_SHOTGUN -> 5.7F;
         case SEMI_AUTO_SHOTGUN -> 4.8F;
         case LEVER_RIFLE -> 3.8F;
         case SEMI_AUTO_RIFLE -> 3.1F;
         case REVOLVER -> 10.0F;
         case FIELD_PISTOL -> 7.0F;
         case FLARE_GUN -> 4.5F;
         case TRANQUILIZER_RIFLE -> 1.7F;
         case BAIT_LAUNCHER -> 2.0F;
         default -> 0.0F;
      };
   }

   static float travel(Weapon var0) {
      return switch (var0) {
         case DOUBLE_BARREL -> 0.067F;
         case PUMP_SHOTGUN -> 0.059F;
         case SEMI_AUTO_SHOTGUN -> 0.048F;
         case LEVER_RIFLE -> 0.041F;
         case SEMI_AUTO_RIFLE -> 0.033F;
         case REVOLVER -> 0.043F;
         case FIELD_PISTOL -> 0.035F;
         default -> 0.025F;
      };
   }

   private FieldWeaponMotion() {
   }
}
