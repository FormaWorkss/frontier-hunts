package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.hunting.CuriosBridge;
import com.formaworks.frontierhunts.hunting.NativeGear;
import com.formaworks.frontierhunts.workshop.FieldRodItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class FieldGearActions {
   public static final List<String> ATTACHMENTS = List.of(
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
   );

   @SubscribeEvent
   public static void copyPack(Clone var0) {
      CompoundTag var1 = var0.getOriginal().getPersistentData();
      if (var1.contains("frontier_pack", 9) || var1.contains("frontier_pack", 10)) {
         var0.getEntity().getPersistentData().put("frontier_pack", var1.get("frontier_pack").copy());
      }

      for (String var3 : var1.getAllKeys()) {
         if (var3.startsWith("frontier_fish_best_")) {
            var0.getEntity().getPersistentData().putDouble(var3, var1.getDouble(var3));
         }
      }
   }

   public static boolean tool(ServerPlayer var0) {
      ItemStack var1 = var0.getOffhandItem();
      if (var1.getItem() instanceof ExpeditionWeapon var2) {
         CompoundTag var8 = ExpeditionWeapon.data(var1);
         if (var8.getLong("reload_until") > 0L) {
            ExpeditionService.message(var0, "Finish or cancel the reload first.");
            return true;
         } else {
            String var4 = ATTACHMENTS.stream().filter(var8::getBoolean).findFirst().orElse(null);
            if (var4 == null) {
               ExpeditionService.message(var0, "No fitted attachments.");
               return true;
            } else {
               var8.remove(var4);
               ItemStack var5 = var1.copy();
               ExpeditionWeapon.save(var5, var8);
               int var6 = var2.capacity(var5);
               int var7 = Math.max(0, var8.getInt("rounds") - var6);
               var8.putInt("rounds", Math.min(var8.getInt("rounds"), var6));
               ExpeditionWeapon.save(var1, var8);
               ExpeditionService.give(var0, new ItemStack(ExpeditionContent.item(var4)));
               if (var7 > 0) {
                  ExpeditionService.give(var0, new ItemStack(ExpeditionContent.item(var2.weapon.ammo), var7));
               }

               ExpeditionService.message(var0, "Removed " + var4.replace('_', ' '));
               return true;
            }
         }
      } else {
         return false;
      }
   }

   public static boolean wearPack(ServerPlayer var0, ItemStack var1) {
      if (!wornPack(var0).isEmpty()) {
         ExpeditionService.message(var0, "You are already wearing a pack.");
         return true;
      } else if (NativeGear.equipPack(var0, var1)) {
         var0.playNotifySound((SoundEvent)SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
         ExpeditionService.message(var0, "Pack on: press the pack key to open it, or sneak + pack key to remove it.");
         return true;
      } else if (CuriosBridge.equip(var0, "back", var1.copyWithCount(1))) {
         var1.shrink(1);
         var0.playNotifySound((SoundEvent)SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
         ExpeditionService.message(var0, "Pack on: open it any time with the pack key.");
         return true;
      } else {
         return false;
      }
   }

   public static ItemStack wornPack(Player var0) {
      ItemStack var1 = NativeGear.pack(var0);
      return !var1.isEmpty() ? var1 : CuriosBridge.find(var0, var0x -> var0x.is(ExpeditionContent.item("hunter_pack")), "back");
   }

   public static void packKey(ServerPlayer var0) {
      if (var0.isShiftKeyDown() && NativeGear.unequipPack(var0)) {
         ExpeditionService.message(var0, "Pack removed.");
      } else {
         boolean var1 = !wornPack(var0).isEmpty() || var0.getInventory().contains(var0x -> var0x.is(ExpeditionContent.item("hunter_pack")));
         if (var1) {
            pack(var0);
         } else {
            ExpeditionService.message(var0, "No Hunter's Field Pack on your back or in your inventory.");
         }
      }
   }

   public static void pack(ServerPlayer var0) {
      if (var0.containerMenu == var0.inventoryMenu) {
         SimpleContainer var1 = new SimpleContainer(27);
         CompoundTag var2 = var0.getPersistentData();
         if (var2.contains("frontier_pack", 10)) {
            NonNullList var3 = NonNullList.withSize(27, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(var2.getCompound("frontier_pack"), var3, var0.registryAccess());

            for (int var4 = 0; var4 < 27; var4++) {
               var1.setItem(var4, (ItemStack)var3.get(var4));
            }
         } else {
            var1.fromTag(var2.getList("frontier_pack", 10), var0.registryAccess());
         }

         var1.addListener(var3x -> {
            NonNullList var4x = NonNullList.withSize(27, ItemStack.EMPTY);

            for (int var5 = 0; var5 < 27; var5++) {
               var4x.set(var5, var1.getItem(var5));
            }

            CompoundTag var6 = new CompoundTag();
            ContainerHelper.saveAllItems(var6, var4x, var0.registryAccess());
            var2.put("frontier_pack", var6);
         });
         var0.openMenu(new SimpleMenuProvider((var1x, var2x, var3x) -> ChestMenu.threeRows(var1x, var2x, var1), Component.literal("Hunter pack")));
      }
   }

   public static boolean fitLure(ServerPlayer var0, ItemStack var1, String var2) {
      ItemStack var3 = var0.getOffhandItem();
      if (!(var3.getItem() instanceof FieldRodItem) && !var3.is(ExpeditionContent.item("bowfishing_bow"))) {
         return false;
      } else {
         CompoundTag var4 = ExpeditionWeapon.data(var3);
         if (var4.getBoolean(var2)) {
            ExpeditionService.message(var0, "This lure is already fitted.");
            return true;
         } else {
            var4.putBoolean(var2, true);
            ExpeditionWeapon.save(var3, var4);
            if (!var0.hasInfiniteMaterials()) {
               var1.shrink(1);
            }

            ExpeditionService.message(var0, "Glow lure fitted · attracts fish at night");
            return true;
         }
      }
   }

   public static boolean place(ServerPlayer var0, BlockPos var1, String var2) {
      ServerLevel var3 = var0.serverLevel();
      StructureTemplate var4 = var3.getStructureManager().getOrCreate(FrontierHunts.id("expedition/" + var2));
      Vec3i var5 = var4.getSize();
      if (var5.getX() == 0) {
         return false;
      } else {
         for (BlockPos var7 : BlockPos.betweenClosed(var1, var1.offset(var5.getX() - 1, var5.getY() - 1, var5.getZ() - 1))) {
            if (!var3.getWorldBorder().isWithinBounds(var7) || !var3.hasChunkAt(var7) || !var3.getBlockState(var7).canBeReplaced()) {
               ExpeditionService.message(var0, "The shelter needs an open footprint.");
               return false;
            }
         }

         return var4.placeInWorld(var3, var1, var1, new StructurePlaceSettings(), var3.random, 3);
      }
   }

   private FieldGearActions() {
   }
}
