package com.formaworks.frontierhunts.expedition;

public final class WeaponAction {
   public static WeaponAction.Reload reload(Weapon var0) {
      return switch (var0) {
         case LEVER_RIFLE, PUMP_SHOTGUN, SEMI_AUTO_SHOTGUN -> WeaponAction.Reload.TUBE;
         case DOUBLE_BARREL, FLARE_GUN -> WeaponAction.Reload.BREAK;
         case REVOLVER -> WeaponAction.Reload.CYLINDER;
         case SEMI_AUTO_RIFLE, FIELD_PISTOL -> WeaponAction.Reload.MAGAZINE;
         default -> WeaponAction.Reload.SINGLE;
      };
   }

   public static int duration(Weapon var0, int var1) {
      return switch (reload(var0)) {
         case TUBE -> 12 + Math.clamp((long)var1, 1, 8) * 17 + 9;
         case MAGAZINE -> var0 == Weapon.FIELD_PISTOL ? 43 : 48;
         case BREAK -> var0 == Weapon.FLARE_GUN ? 40 : 52;
         case CYLINDER -> 61;
         case SINGLE -> 38;
      };
   }

   public static int insertion(Weapon var0) {
      return switch (reload(var0)) {
         case TUBE -> 29;
         case MAGAZINE -> 31;
         case BREAK -> 34;
         case CYLINDER -> 43;
         case SINGLE -> 25;
      };
   }

   public static boolean supports(Weapon var0, String var1) {
      if (var0.bow) {
         return false;
      } else {
         boolean var2 = var0 != Weapon.FIELD_PISTOL && var0 != Weapon.REVOLVER && var0 != Weapon.FLARE_GUN;

         return switch (var1) {
            case "pistol_magazine" -> var0 == Weapon.FIELD_PISTOL;
            case "extended_magazine" -> var0 == Weapon.SEMI_AUTO_RIFLE || var0 == Weapon.FIELD_PISTOL;
            case "suppressor" -> var0 == Weapon.SEMI_AUTO_RIFLE
            || var0 == Weapon.FIELD_PISTOL
            || var0 == Weapon.LEVER_RIFLE
            || var0 == Weapon.TRANQUILIZER_RIFLE;
            // [guns3] bipods are for long-range guns only: the Assault Rifle and the Tranquilizer Rifle (the Ridgeline has its own)
            case "bipod" -> var0 == Weapon.SEMI_AUTO_RIFLE || var0 == Weapon.TRANQUILIZER_RIFLE;
            case "steady_stock" -> var2;
            case "reflex_sight", "micro_red_dot", "holographic_sight" -> var0 != Weapon.FLARE_GUN && var0 != Weapon.BAIT_LAUNCHER;
            case "two_power_prism" -> var2 && var0 != Weapon.DOUBLE_BARREL && var0 != Weapon.BAIT_LAUNCHER;
            case "muzzle_brake" -> var0 == Weapon.LEVER_RIFLE || var0 == Weapon.SEMI_AUTO_RIFLE;
            case "angled_foregrip" -> var0 == Weapon.SEMI_AUTO_RIFLE
            || var0 == Weapon.PUMP_SHOTGUN
            || var0 == Weapon.SEMI_AUTO_SHOTGUN
            || var0 == Weapon.TRANQUILIZER_RIFLE;
            case "six_power_scope", "eight_power_scope", "twelve_power_scope", "thermal_scope" -> var2
            && var0 != Weapon.DOUBLE_BARREL
            && var0 != Weapon.BAIT_LAUNCHER;
            default -> false;
         };
      }
   }

   private WeaponAction() {
   }

   public static enum Reload {
      TUBE,
      MAGAZINE,
      BREAK,
      CYLINDER,
      SINGLE;
   }
}
