package com.formaworks.frontierhunts.workshop;

import com.formaworks.frontierhunts.expedition.AttachmentSpec;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.WeaponAction;
import com.formaworks.frontierhunts.rifle.RifleContent;
import com.formaworks.frontierhunts.rifle.RifleItem;
import com.formaworks.frontierhunts.rifle.RifleState;
import com.formaworks.frontierhunts.rifle.RidgelineOptics;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public final class AttachmentFitting {
   public static Item part(String var0) {
      if (var0.equals(RidgelineOptics.STOCK)) {
         return (Item)RifleContent.SCOPE.get(); // [rifle]
      }
      return var0.equals("four_power_optic") ? (Item)WorkshopContent.OPTIC.get() : ExpeditionContent.item(var0);
   }

   public static List<ItemStack> plan(Inventory var0, String var1, boolean var2) {
      List<ItemStack> var3 = var0.items.stream().map(ItemStack::copy).collect(Collectors.toCollection(ArrayList::new));
      return plan(var3, var0.selected, var1, var2);
   }

   public static List<ItemStack> plan(List<ItemStack> var0, int var1, String var2, boolean var3) {
      if (var1 >= 0 && var1 < var0.size()) {
         List<ItemStack> var4 = var0.stream().map(ItemStack::copy).collect(Collectors.toCollection(ArrayList::new));
         ItemStack var5 = (ItemStack)var4.get(var1);
         CompoundTag var6 = ExpeditionWeapon.data(var5);
         boolean var7 = var5.getItem() instanceof RifleItem;
         boolean var8 = var5.getItem() instanceof FieldRodItem;
         ExpeditionWeapon var9 = var5.getItem() instanceof ExpeditionWeapon var10 ? var10 : null;
         if (var7) {
            // [rifle] the Ridgeline takes every field optic, its own factory scope, the 5-round magazine and the bipod
            if (!RidgelineOptics.PARTS.contains(var2) || RifleState.read(var5).action() != 0) {
               return null;
            }

            // [rifle] swapping sights: the optic on the rifle comes off first (back into the inventory), then the new one goes on
            if (var3 && AttachmentSpec.sight(var2)) {
               for (int guard = 0; guard < 4; guard++) {
                  String current = RidgelineOptics.sight(var5);
                  if (current.equals(RidgelineOptics.IRONS) || current.equals(var2)) {
                     break;
                  }

                  List<ItemStack> off = plan(var4, var1, current, false);
                  if (off == null) {
                     return null;
                  }

                  var4 = off;
                  var5 = var4.get(var1);
                  var6 = ExpeditionWeapon.data(var5);
               }
            }

            if (var2.equals(RidgelineOptics.STOCK)) {
               boolean on = RidgelineOptics.stockFitted(var5);
               if (var3 == on || var3 && !RidgelineOptics.sight(var5).equals(RidgelineOptics.IRONS)) {
                  return null;
               }

               if (var3) {
                  ItemStack scope = var4.stream().filter(x -> x.is(part(var2))).findFirst().orElse(ItemStack.EMPTY);
                  if (scope.isEmpty()) {
                     return null;
                  }

                  scope.shrink(1);
                  var6.remove(RidgelineOptics.STOCK_OFF);
               } else {
                  if (!WorkbenchMenu.insert(var4, new ItemStack(part(var2)))) {
                     return null;
                  }

                  var6.putBoolean(RidgelineOptics.STOCK_OFF, true);
               }

               ExpeditionWeapon.save(var5, var6);
               return var4;
            }
         } else if (var8) {
            if (!var2.equals("fishing_drag_kit")) {
               return null;
            }
         } else if (var9 == null || !WeaponAction.supports(var9.weapon, var2) || var6.getLong("reload_until") != 0L) {
            return null;
         }

         String var15 = var2.equals("fishing_drag_kit") ? "drag_kit" : var2;
         boolean var16 = var2.equals("four_power_optic") ? OpticUpgrade.fitted(var5) : var6.getBoolean(var15);
         if (var3 == var16) {
            return null;
         } else {
            if (var3) {
               for (String var13 : EquipmentCatalog.PARTS) {
                  if (AttachmentSpec.exclusive(var2, var13) && fitted(var5, var13)) { // [rifle] fitted(): 4x optic / factory scope are not plain booleans
                     return null;
                  }
               }
            }

            if (var3) {
               ItemStack var17 = var4.stream().filter(var1x -> var1x.is(part(var2))).findFirst().orElse(ItemStack.EMPTY);
               if (var17.isEmpty()) {
                  return null;
               }

               var17.shrink(1);
            } else if (!WorkbenchMenu.insert(var4, new ItemStack(part(var2)))) {
               return null;
            }

            if (var2.equals("four_power_optic")) {
               OpticUpgrade.set(var5, var3);
            } else {
               if (var3) {
                  var6.putBoolean(var15, true);
               } else {
                  var6.remove(var15);
               }

               if (!var3 && (var2.equals("extended_magazine") || var2.equals("pistol_magazine")) && var9 != null) {
                  int var18 = Math.max(0, var6.getInt("rounds") - var9.weapon.capacity);
                  var6.putInt("rounds", Math.min(var6.getInt("rounds"), var9.weapon.capacity));
                  if (var18 > 0 && !WorkbenchMenu.insert(var4, new ItemStack(ExpeditionContent.item(var9.weapon.ammo), var18))) {
                     return null;
                  }
               }

               if (!var3 && var2.equals("sniper_magazine") && var7) {
                  CompoundTag var19 = var6.getCompound("frontier_rifle");
                  int var20 = var19.getInt("magazine") + (var19.getBoolean("chamber") ? 1 : 0);
                  int var14 = Math.max(0, var20 - 3);
                  if (var14 > 0 && !WorkbenchMenu.insert(var4, new ItemStack((ItemLike)RifleContent.AMMO.get(), var14))) {
                     return null;
                  }

                  var19.putInt("magazine", Math.min(var19.getInt("magazine"), 3 - (var19.getBoolean("chamber") ? 1 : 0)));
               }

               ExpeditionWeapon.save(var5, var6);
            }

            return var4;
         }
      } else {
         return null;
      }
   }

   public static boolean supported(ItemStack var0, String var1) {
      if (var0.getItem() instanceof RifleItem) {
         return RidgelineOptics.PARTS.contains(var1); // [rifle]
      } else if (var0.getItem() instanceof FieldRodItem) {
         return var1.equals("fishing_drag_kit");
      } else {
         if (var0.getItem() instanceof ExpeditionWeapon var2 && WeaponAction.supports(var2.weapon, var1)) {
            return true;
         }

         return false;
      }
   }

   public static boolean fitted(ItemStack var0, String var1) {
      if (var1.equals(RidgelineOptics.STOCK)) {
         return RidgelineOptics.stockFitted(var0); // [rifle]
      }

      return var1.equals("four_power_optic")
         ? OpticUpgrade.fitted(var0)
         : ExpeditionWeapon.data(var0).getBoolean(var1.equals("fishing_drag_kit") ? "drag_kit" : var1);
   }

   private AttachmentFitting() {
   }
}
