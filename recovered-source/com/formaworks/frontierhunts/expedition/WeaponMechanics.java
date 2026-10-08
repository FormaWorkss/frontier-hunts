package com.formaworks.frontierhunts.expedition;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

final class WeaponMechanics {
   static void tick(ServerPlayer var0, ItemStack var1, ExpeditionWeapon var2, boolean var3) {
      if (var3 && var0.getMainHandItem() == var1 && !var2.weapon.bow) {
         CompoundTag var4 = ExpeditionWeapon.data(var1);
         long var5 = var4.getLong("shot_at");
         if (var5 > 0L && var4.getLong("reload_until") <= 0L) {
            long var7 = var0.level().getGameTime() - var5;
            Weapon var9 = var2.weapon;
            if (var9 != Weapon.LEVER_RIFLE && var9 != Weapon.PUMP_SHOTGUN) {
               if ((var9 == Weapon.FIELD_PISTOL || var9 == Weapon.SEMI_AUTO_RIFLE || var9 == Weapon.SEMI_AUTO_SHOTGUN) && var7 == 3L) {
                  var0.level().playSound(null, var0.blockPosition(), EquipmentSounds.action(var9, "close"), SoundSource.PLAYERS, 0.19F, 1.0F);
               }
            } else if (var7 == 3L || var7 == (long)(var9.interval - 3)) {
               var0.level()
                  .playSound(
                     null,
                     var0.blockPosition(),
                     EquipmentSounds.action(var9, var7 == 3L ? "open" : "close"),
                     SoundSource.PLAYERS,
                     var7 == 3L ? 0.32F : 0.4F,
                     1.0F
                  );
            }
         }
      }
   }

   private WeaponMechanics() {
   }
}
