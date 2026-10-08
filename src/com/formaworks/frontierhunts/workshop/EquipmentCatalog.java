package com.formaworks.frontierhunts.workshop;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.camp.CampingTent;
import com.formaworks.frontierhunts.expedition.Coverall;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.GhillieSuit;
import com.formaworks.frontierhunts.hunting.ArrowTipItem;
import com.formaworks.frontierhunts.landscape.tent.CompactTent;
import com.formaworks.frontierhunts.rifle.RifleContent;
import com.formaworks.frontierhunts.rifle.RifleItem;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class EquipmentCatalog {
   public static final List<String> PARTS = List.of(
      "four_power_optic",
      "reflex_sight",
      "micro_red_dot",
      "holographic_sight",
      "two_power_prism",
      "muzzle_brake",
      "angled_foregrip",
      "six_power_scope",
      "eight_power_scope",
      "twelve_power_scope",
      "thermal_scope",
      "suppressor",
      "sniper_magazine",
      "pistol_magazine",
      "extended_magazine",
      "steady_stock",
      "bipod",
      "fishing_drag_kit",
      "ridgeline_scope" // [rifle] appended last: workbench button ids are indices into this list
   );
   private static final Set<String> AMMO = Set.of(
      "bait",
      "field_arrow",
      "primitive_arrow",
      "rifle_ammo",
      "rifle_cartridge",
      "rifle_round",
      "shotgun_shell",
      "pistol_round",
      "tranquilizer_dart",
      "bowfishing_arrow",
      "flare_round"
   );
   private static final Map<RecipeManager, List<RecipeHolder<CraftingRecipe>>> CACHE = new WeakHashMap<>();

   public static String id(Item var0) {
      return BuiltInRegistries.ITEM.getKey(var0).getPath();
   }

   public static boolean weapon(Item var0) {
      return var0 instanceof ExpeditionWeapon || var0 instanceof RifleItem || var0 == HuntContent.FIELD_BOW.get() || var0 == HuntContent.FIELD_KNIFE.get();
   }

   public static boolean ammunition(Item var0) {
      return AMMO.contains(id(var0)) || var0 instanceof ArrowTipItem || var0 == RifleContent.AMMO.get();
   }

   public static boolean attachment(Item var0) {
      return PARTS.contains(id(var0));
   }

   public static boolean creative(Item var0) {
      String var1 = id(var0);
      return !(var0 instanceof GhillieSuit)
         && !(var0 instanceof Coverall)
         && !(var0 instanceof com.formaworks.frontierhunts.expedition.ScentControl) // [clothing] scent suit: was a Coverall
         && !var1.startsWith("carbon_")
         && !(var0 instanceof CampingTent.Kit)
         && !weapon(var0)
         && !ammunition(var0)
         && !attachment(var0)
         && !var1.equals("field_tent");
   }

   public static synchronized void clear() {
      CACHE.clear();
      com.formaworks.frontierhunts.recipes.ClothingTable.clear(); // [recipes]
   }

   @SubscribeEvent
   public static void sync(OnDatapackSyncEvent var0) {
      clear();
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent var0) {
      clear();
   }

   public static synchronized List<RecipeHolder<CraftingRecipe>> recipes(Level var0, boolean var1) {
      return recipes(var0, var1 ? WorkshopKind.ATTACHMENTS : WorkshopKind.WEAPONS);
   }

   public static synchronized List<RecipeHolder<CraftingRecipe>> recipes(Level var0, WorkshopKind var1) {
      List<RecipeHolder<CraftingRecipe>> var2 = CACHE.computeIfAbsent(
         var0.getRecipeManager(),
         var1x -> var1x.getAllRecipesFor(RecipeType.CRAFTING)
               .stream()
               .filter(
                  var1xx -> {
                     if (var1xx.id().getNamespace().equals("frontierhunts")
                        && !((CraftingRecipe)var1xx.value()).isSpecial()
                        && !((CraftingRecipe)var1xx.value()).getIngredients().isEmpty()) {
                        Item var2x = ((CraftingRecipe)var1xx.value()).getResultItem(var0.registryAccess()).getItem();
                        String var3 = id(var2x);
                        return !var3.equals("field_tent")
                           && (
                              weapon(var2x)
                                 || HuntContent.archery(var2x)
                                 || ammunition(var2x)
                                 || attachment(var2x)
                                 || var2x == HuntContent.SKINNING_TOOL.get()
                                 || var2x == WorkshopContent.ROD.get()
                                 || ExpeditionContent.ITEMS.containsKey(var3) && !var3.endsWith("spawn_egg")
                                 || var2x instanceof CompactTent.Kit
                                 || var3.startsWith("carbon_")
                           );
                     } else {
                        return false;
                     }
                  }
               )
               .sorted(Comparator.comparing(var0xx -> var0xx.id().toString()))
               .toList()
      );
      List<RecipeHolder<CraftingRecipe>> var3 = var2.stream().filter(var2x -> var1.accepts(((CraftingRecipe)var2x.value()).getResultItem(var0.registryAccess()).getItem())).toList();
      // [recipes] the Clothing Table also lists its own station-only recipes (frontierhunts:clothing_table: furs, hides, sewing)
      if (var1 == WorkshopKind.CLOTHING) {
         var3 = new java.util.ArrayList<>(var3);
         var3.addAll(com.formaworks.frontierhunts.recipes.ClothingTable.recipes(var0.getRecipeManager()));
         var3 = List.copyOf(var3);
      }
      return var3;
   }

   private EquipmentCatalog() {
   }
}
