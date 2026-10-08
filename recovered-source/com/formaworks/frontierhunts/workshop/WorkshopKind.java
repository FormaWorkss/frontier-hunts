package com.formaworks.frontierhunts.workshop;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.camp.CampingTent;
import com.formaworks.frontierhunts.expedition.Coverall;
import com.formaworks.frontierhunts.expedition.ExpeditionStation;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.GhillieSuit;
import com.formaworks.frontierhunts.landscape.tent.CompactTent;
import java.util.Set;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public enum WorkshopKind {
   WEAPONS("Armory", 12165755, "Firearms and field blades"),
   AMMUNITION("Ammunition", 13280618, "Cartridges, shells and darts"),
   ATTACHMENTS("Optics & attachments", 9353666, "Fit equipment to your selected weapon"),
   BOWS("Bow workshop", 11123590, "Bows, crossbows and arrows"),
   FISHING("Fishing workshop", 8960432, "Rods, bowfishing and tackle"),
   CLOTHING("Field clothing", 9413240, "Furs and hides, ghillie layers, camo coveralls and the carbon scent layer"), // [recipes] furs + hides are made here
   TENTS("Tent bench", 13809535, "Camping shelters, packed and ready to pitch");

   public final String title;
   public final String description;
   public final int accent;
   private static final Set<String> FISH = Set.of(
      "field_fishing_rod", "bowfishing_bow", "bowfishing_arrow", "bait", "glow_lure", "fishing_drag_kit", "fish_finder", "landing_net", "chum_bucket"
   );
   private static final Set<String> OPTICS = Set.of(
      "binoculars",
      "rangefinder",
      "thermal_binoculars",
      "attachment_tool",
      "night_vision_binoculars",
      "field_battery_pack",
      "field_flashlight",
      "field_spotlight",
      "field_camera"
   );

   private WorkshopKind(String nullxx, int nullxxx, String nullxxxx) {
      this.title = nullxx;
      this.accent = nullxxx;
      this.description = nullxxxx;
   }

   public boolean accepts(Item var1) {
      if (var1 instanceof GhillieSuit || var1 instanceof Coverall || var1 instanceof com.formaworks.frontierhunts.expedition.ScentControl) { // [clothing] the scent suit is a base layer now
         return this == CLOTHING;
      } else if (!(var1 instanceof CampingTent.Kit) && !(var1 instanceof CompactTent.Kit)) {
         String var2 = EquipmentCatalog.id(var1);
         if (var2.equals("hunting_spear")) {
            return this == WEAPONS;
         } else if (var2.equals("hunter_pack")) {
            return this == CLOTHING;
         } else if (var2.startsWith("carbon_")) {
            return this == CLOTHING;
         } else if (var2.equals("hunters_quiver")) {
            return this == CLOTHING;
         } else if (FISH.contains(var2)) {
            return this == FISHING;
         } else if (EquipmentCatalog.attachment(var1) || OPTICS.contains(var2)) {
            return this == ATTACHMENTS;
         } else if (var1 != HuntContent.FIELD_BOW.get() && !HuntContent.archery(var1)) {
            if (var1 instanceof ExpeditionWeapon var3 && var3.weapon.bow) {
               return this == BOWS;
            }

            return EquipmentCatalog.ammunition(var1)
               ? this == AMMUNITION
               : this == WEAPONS && (EquipmentCatalog.weapon(var1) || var1 == HuntContent.SKINNING_TOOL.get());
         } else {
            return this == BOWS;
         }
      } else {
         return this == TENTS;
      }
   }

   public static WorkshopKind of(BlockState var0) {
      if (var0.is((Block)WorkshopContent.ATTACHMENT_BENCH.get())) {
         return ATTACHMENTS;
      } else if (var0.getBlock() instanceof ExpeditionStation var1) {
         String var5 = var1.id;

         return switch (var5) {
            case "ammo_reloader" -> AMMUNITION;
            case "bow_tuning_rack" -> BOWS;
            case "fishing_station" -> FISHING;
            case "clothing_workbench" -> CLOTHING;
            case "tent_bench" -> TENTS;
            default -> null;
         };
      } else {
         return var0.is((Block)WorkshopContent.BENCH.get()) ? WEAPONS : null;
      }
   }

   public MenuType<WorkbenchMenu> menu() {
      return switch (this) {
         case WEAPONS -> (MenuType)WorkshopContent.MENU.get();
         case AMMUNITION -> (MenuType)WorkshopContent.AMMO_MENU.get();
         case ATTACHMENTS -> (MenuType)WorkshopContent.ATTACHMENT_MENU.get();
         case BOWS -> (MenuType)WorkshopContent.BOW_MENU.get();
         case FISHING -> (MenuType)WorkshopContent.FISH_MENU.get();
         case CLOTHING -> (MenuType)WorkshopContent.CLOTHING_MENU.get();
         case TENTS -> (MenuType)WorkshopContent.TENT_MENU.get();
      };
   }
}
