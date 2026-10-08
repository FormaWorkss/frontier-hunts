package com.formaworks.frontierhunts.rifle;

import com.formaworks.frontierhunts.expedition.AttachmentSpec;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.workshop.OpticUpgrade;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * [rifle] Which optic sits on a Ridgeline bolt rifle, and which parts the rifle accepts (common code, no client types).
 *
 * <p>The rifle leaves the factory with its own 3-9x hunting scope. That scope is a part like any other: removing it at
 * the Attachment Workbench returns a {@link RifleContent#SCOPE} item and sets {@link #STOCK_OFF} on the rifle, which
 * then shows its flip-up iron sights. Any other sight replaces it (the workbench swaps them: the old optic goes back
 * into the inventory). Storage on the item (CUSTOM_DATA): the field-gun optic booleans ("six_power_scope" ...),
 * the legacy "frontier_four_power" flag of the 4-12x Field Optic, and {@link #STOCK_OFF}.
 */
public final class RidgelineOptics {
   /** Part id of the factory scope. */
   public static final String STOCK = "ridgeline_scope";
   /** Item flag: the factory scope has been taken off. */
   public static final String STOCK_OFF = "ridgeline_scope_off";
   /** Sight id when no optic is fitted: the flip-up iron sights. */
   public static final String IRONS = "";

   /** Every part the Ridgeline accepts at the Attachment Workbench. */
   public static final List<String> PARTS = List.of(
      STOCK,
      "four_power_optic",
      "six_power_scope",
      "eight_power_scope",
      "twelve_power_scope",
      "thermal_scope",
      "reflex_sight",
      "micro_red_dot",
      "holographic_sight",
      "two_power_prism",
      "sniper_magazine",
      "bipod"
   );

   /** Name of the prone key for tooltips; replaced by the client with the bound key. */
   public static java.util.function.Supplier<String> PRONE_KEY = () -> "Z";

   private RidgelineOptics() {
   }

   /**
    * The sight in use: {@link #STOCK}, a field optic id ("thermal_scope", "four_power_optic", "six_power_scope",
    * "reflex_sight" ...) or {@link #IRONS}. Legacy rifles that carry the factory scope plus a fitted thermal / 4x
    * optic show that optic (the factory scope stays on the rifle's books and comes back when it is removed).
    */
   public static String sight(ItemStack rifle) {
      if (ExpeditionWeapon.attachment(rifle, "thermal_scope")) {
         return "thermal_scope";
      }
      if (OpticUpgrade.fitted(rifle)) {
         return "four_power_optic";
      }
      String fitted = AttachmentSpec.installedSight(rifle);
      if (!fitted.isEmpty() && !fitted.equals(STOCK)) {
         return fitted;
      }
      return ExpeditionWeapon.attachment(rifle, STOCK_OFF) ? IRONS : STOCK;
   }

   /** Is the factory scope fitted (and the sight in use)? */
   public static boolean stockFitted(ItemStack rifle) {
      return rifle.getItem() instanceof RifleItem && STOCK.equals(sight(rifle));
   }

   /** Translation key of the sight's display name. */
   public static String nameKey(String sight) {
      return sight.isEmpty() ? "rifle.frontierhunts.sight.irons" : (sight.equals(STOCK) ? "item.frontierhunts.ridgeline_scope" : "item.frontierhunts." + sight);
   }
}
