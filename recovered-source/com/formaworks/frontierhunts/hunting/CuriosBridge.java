package com.formaworks.frontierhunts.hunting;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class CuriosBridge {
   private static boolean tried;
   private static boolean ok;
   private static MethodHandle inventory;
   private static MethodHandle findFirst;
   private static MethodHandle findFirstInSlot;
   private static MethodHandle stackOf;
   private static MethodHandle setEquipped;
   private static MethodHandle stacksHandler;
   private static MethodHandle handlerStacks;
   private static MethodHandle handlerSlots;
   private static MethodHandle getStack;

   private static synchronized boolean init() {
      if (tried) {
         return ok;
      } else {
         tried = true;

         try {
            if (!ModList.get().isLoaded("curios")) {
               return false;
            }

            Lookup var0 = MethodHandles.publicLookup();
            Class var1 = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class var2 = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            Class var3 = Class.forName("top.theillusivec4.curios.api.SlotResult");
            Class var4 = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            Class var5 = Class.forName("top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler");
            inventory = var0.findStatic(var1, "getCuriosInventory", MethodType.methodType(Optional.class, LivingEntity.class));
            findFirst = var0.findVirtual(var2, "findFirstCurio", MethodType.methodType(Optional.class, Predicate.class));
            findFirstInSlot = var0.findVirtual(var2, "findFirstCurio", MethodType.methodType(Optional.class, Predicate.class, String.class));
            stackOf = var0.findVirtual(var3, "stack", MethodType.methodType(ItemStack.class));
            setEquipped = var0.findVirtual(var2, "setEquippedCurio", MethodType.methodType(void.class, String.class, int.class, ItemStack.class));
            stacksHandler = var0.findVirtual(var2, "getStacksHandler", MethodType.methodType(Optional.class, String.class));
            handlerStacks = var0.findVirtual(var4, "getStacks", MethodType.methodType(var5));
            handlerSlots = var0.findVirtual(var5, "getSlots", MethodType.methodType(int.class));
            getStack = var0.findVirtual(var5, "getStackInSlot", MethodType.methodType(ItemStack.class, int.class));
            ok = true;
         } catch (Throwable var6) {
            LogUtils.getLogger().warn("Frontier Hunts: Curios found but its API could not be linked; quiver/backpack slots disabled", var6);
            ok = false;
         }

         return ok;
      }
   }

   public static boolean available() {
      return init();
   }

   private static Object handler(LivingEntity var0) throws Throwable {
      Optional var1 = (Optional)inventory.invoke((LivingEntity)var0);
      return var1.orElse(null);
   }

   public static ItemStack find(LivingEntity var0, Predicate<ItemStack> var1, String var2) {
      if (var0 != null && init()) {
         try {
            Object var3 = handler(var0);
            if (var3 == null) {
               return ItemStack.EMPTY;
            } else {
               Optional var4 = var2 == null
                  ? (Optional)findFirst.invoke((Object)var3, (Predicate)var1)
                  : (Optional)findFirstInSlot.invoke((Object)var3, (Predicate)var1, (String)var2);
               return var4.isPresent() ? (ItemStack)stackOf.invoke((Object)var4.get()) : ItemStack.EMPTY;
            }
         } catch (Throwable var5) {
            return ItemStack.EMPTY;
         }
      } else {
         return ItemStack.EMPTY;
      }
   }

   public static boolean equip(LivingEntity var0, String var1, ItemStack var2) {
      if (var0 != null && init()) {
         try {
            Object var3 = handler(var0);
            if (var3 == null) {
               return false;
            } else {
               Optional var4 = (Optional)stacksHandler.invoke((Object)var3, (String)var1);
               if (var4.isEmpty()) {
                  return false;
               } else {
                  Object var5 = (Object)handlerStacks.invoke((Object)var4.get());
                  int var6 = (int)handlerSlots.invoke((Object)var5);

                  for (int var7 = 0; var7 < var6; var7++) {
                     if ((ItemStack)getStack.invoke((Object)var5, (int)var7).isEmpty()) {
                        setEquipped.invoke((Object)var3, (String)var1, (int)var7, (ItemStack)var2);
                        return true;
                     }
                  }

                  return false;
               }
            }
         } catch (Throwable var8) {
            return false;
         }
      } else {
         return false;
      }
   }

   private CuriosBridge() {
   }
}
