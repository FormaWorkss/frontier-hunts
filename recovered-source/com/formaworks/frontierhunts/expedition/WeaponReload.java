package com.formaworks.frontierhunts.expedition;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public final class WeaponReload {
   private static final String[] ATTACHMENTS = new String[]{
      "suppressor",
      "pistol_magazine",
      "extended_magazine",
      "steady_stock",
      "bipod",
      "reflex_sight",
      "micro_red_dot",
      "holographic_sight",
      "two_power_prism",
      "muzzle_brake",
      "angled_foregrip",
      "six_power_scope",
      "eight_power_scope",
      "twelve_power_scope",
      "thermal_scope"
   };

   public static void migrate(ServerPlayer var0, ItemStack var1, ExpeditionWeapon var2) {
      CompoundTag var3 = ExpeditionWeapon.data(var1);
      if (var3.getInt("field_equipment_schema") < 2) { // [guns3] 2: bipods off short guns (returned to the player)
         int var4 = 0;

         for (String var8 : ATTACHMENTS) {
            if (var3.getBoolean(var8) && !WeaponAction.supports(var2.weapon, var8)) {
               if (var8.equals("extended_magazine") || var8.equals("pistol_magazine")) {
                  var4 = Math.clamp((long)(var3.getInt("rounds") - var2.weapon.capacity), 0, 3);
               }

               var3.remove(var8);
               give(var0, new ItemStack(ExpeditionContent.item(var8)));
            }
         }

         if (var4 > 0) {
            var3.putInt("rounds", Math.clamp((long)var3.getInt("rounds"), 0, var2.weapon.capacity));
            give(var0, new ItemStack(ExpeditionContent.item(var2.weapon.ammo), var4));
         }

         var3.putInt("field_equipment_schema", 2);
         ExpeditionWeapon.save(var1, var3);
      }
   }

   private static void give(ServerPlayer var0, ItemStack var1) {
      if (!var0.getInventory().add(var1)) {
         var0.drop(var1, false);
      }
   }

   public static int available(ServerPlayer var0, String var1) {
      if (var0.hasInfiniteMaterials()) {
         return 64;
      } else {
         int var2 = 0;

         for (ItemStack var4 : var0.getInventory().items) {
            if (var4.is(ExpeditionContent.item(var1))) {
               var2 += var4.getCount();
            }
         }

         return var2;
      }
   }

   public static boolean start(ServerPlayer var0, ExpeditionWeapon var1) {
      ItemStack var2 = var0.getMainHandItem();
      if (var2.getItem() == var1
         && !var1.weapon.bow
         && var0.isAlive()
         && !var0.isSpectator()
         && !var0.isUnderWater()
         && var0.containerMenu == var0.inventoryMenu) {
         CompoundTag var3 = ExpeditionWeapon.data(var2);
         long var4 = var0.level().getGameTime();
         if (var3.getLong("reload_until") > 0L) {
            return false;
         } else {
            int var6 = var1.capacity(var2) - Math.clamp((long)var3.getInt("rounds"), 0, var1.capacity(var2));
            int var7 = Math.min(var6, available(var0, var1.weapon.ammo));
            if (var7 <= 0) {
               return false;
            } else {
               long var8 = Math.max(var4, Math.min(var4 + (long)var1.weapon.interval, var3.getLong("next_shot")));
               var3.putLong("reload_started", var8);
               var3.putLong("reload_until", var8 + (long)WeaponAction.duration(var1.weapon, var7));
               var3.putBoolean("reload_empty", var3.getInt("rounds") == 0);
               var3.putLong("reload_load_at", var8 + (long)WeaponAction.insertion(var1.weapon));
               var3.putInt("reload_remaining", var7);
               var3.putBoolean("reload_waiting", var8 > var4);
               var3.putString("reload_owner", var0.getUUID().toString());
               var3.putString("reload_dimension", var0.level().dimension().location().toString());
               ExpeditionWeapon.save(var2, var3);
               if (var8 == var4) {
                  sound(var0, EquipmentSounds.action(var1.weapon, "open"), 0.7F);
               }

               return true;
            }
         }
      } else {
         return false;
      }
   }

   public static void tick(ServerPlayer var0, ItemStack var1, boolean var2, ExpeditionWeapon var3) {
      CompoundTag var4 = ExpeditionWeapon.data(var1);
      long var5 = var4.getLong("reload_until");
      if (var5 != 0L) {
         long var7 = var0.level().getGameTime();
         long var9 = var4.getLong("reload_started");
         if (!var2
            || var0.getMainHandItem() != var1
            || !var0.isAlive()
            || var0.isSpectator()
            || var0.isUnderWater()
            || var0.containerMenu != var0.inventoryMenu
            || !var4.getString("reload_owner").equals(var0.getUUID().toString())
            || !var4.getString("reload_dimension").equals(var0.level().dimension().location().toString())
            || var7 < var9 - (long)var3.weapon.interval
            || var5 - var9 > 200L
            || var7 > var5 + 4L) {
            cancel(var1, var4);
         } else if (var7 >= var9) {
            if (var4.getBoolean("reload_waiting")) {
               var4.remove("reload_waiting");
               ExpeditionWeapon.save(var1, var4);
               sound(var0, EquipmentSounds.action(var3.weapon, "open"), 0.7F);
            }

            int var11 = Math.clamp((long)var4.getInt("reload_remaining"), 0, var3.capacity(var1));
            long var12 = var4.getLong("reload_load_at");
            boolean var14 = WeaponAction.reload(var3.weapon) == WeaponAction.Reload.TUBE;
            boolean var15 = false;

            while (var11 > 0 && var7 >= var12) {
               int var16 = Math.clamp((long)var4.getInt("rounds"), 0, var3.capacity(var1));
               if (var16 >= var3.capacity(var1) || !ExpeditionWeapon.consume(var0, var3.weapon.ammo)) {
                  var11 = 0;
                  var15 = true;
                  break;
               }

               var4.putInt("rounds", var16 + 1);
               var11--;
               var15 = true;
               if (var14) {
                  var12 += 17L;
               }
            }

            if (var15) {
               var4.putInt("reload_remaining", var11);
               var4.putLong("reload_load_at", var12);
               ExpeditionWeapon.save(var1, var4);
               sound(var0, EquipmentSounds.action(var3.weapon, "load"), var14 ? 0.47F : 0.72F);
            }

            if (var7 >= var5) {
               cancel(var1, var4);
               sound(var0, EquipmentSounds.action(var3.weapon, "close"), 0.64F);
            }
         }
      }
   }

   public static boolean interruptTube(ServerPlayer var0, ItemStack var1, ExpeditionWeapon var2) {
      CompoundTag var3 = ExpeditionWeapon.data(var1);
      if (var3.getLong("reload_until") > 0L && WeaponAction.reload(var2.weapon) == WeaponAction.Reload.TUBE && var3.getInt("rounds") > 0) {
         cancel(var1, var3);
         return true;
      } else {
         return false;
      }
   }

   private static void cancel(ItemStack var0, CompoundTag var1) {
      for (String var5 : new String[]{
         "reload_started", "reload_until", "reload_load_at", "reload_remaining", "reload_owner", "reload_dimension", "reload_waiting", "reload_empty"
      }) {
         var1.remove(var5);
      }

      ExpeditionWeapon.save(var0, var1);
   }

   private static void sound(ServerPlayer var0, SoundEvent var1, float var2) {
      var0.level().playSound(null, var0.blockPosition(), var1, SoundSource.PLAYERS, var2, 1.0F);
   }

   private WeaponReload() {
   }
}
