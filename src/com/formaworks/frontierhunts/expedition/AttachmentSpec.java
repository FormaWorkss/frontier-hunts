package com.formaworks.frontierhunts.expedition;

import java.util.List;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

public final class AttachmentSpec {
   public static final List<String> SIGHTS = List.of(
      "reflex_sight",
      "micro_red_dot",
      "holographic_sight",
      "two_power_prism",
      "six_power_scope",
      "eight_power_scope",
      "twelve_power_scope",
      "thermal_scope",
      "four_power_optic",
      "ridgeline_scope" // [rifle] the Ridgeline's factory scope is a swappable sight too (never stored as a boolean)
   );

   public static boolean sight(String var0) {
      return SIGHTS.contains(var0);
   }

   public static boolean unmagnified(String var0) {
      return var0.equals("reflex_sight") || var0.equals("micro_red_dot") || var0.equals("holographic_sight");
   }

   public static String installedSight(ItemStack var0) {
      return SIGHTS.stream().filter(var1 -> ExpeditionWeapon.attachment(var0, var1)).findFirst().orElse("");
   }

   public static float magnification(ItemStack var0, Weapon var1) {
      String var2 = installedSight(var0);

      return switch (var2) {
         case "reflex_sight", "micro_red_dot", "holographic_sight" -> 1.0F;
         case "two_power_prism" -> 2.0F;
         case "six_power_scope" -> 6.0F;
         case "eight_power_scope" -> 8.0F;
         case "twelve_power_scope" -> 12.0F;
         case "thermal_scope", "four_power_optic" -> 4.0F;
         case "ridgeline_scope" -> 3.0F; // [rifle]
         default -> var1 == Weapon.TRANQUILIZER_RIFLE ? 3.0F : 1.5F;
      };
   }

   public static boolean exclusive(String var0, String var1) {
      return Set.of("extended_magazine", "pistol_magazine", "sniper_magazine").contains(var0)
            && Set.of("extended_magazine", "pistol_magazine", "sniper_magazine").contains(var1)
         || sight(var0) && sight(var1)
         || Set.of("suppressor", "muzzle_brake").contains(var0) && Set.of("suppressor", "muzzle_brake").contains(var1);
   }

   private AttachmentSpec() {
   }
}
