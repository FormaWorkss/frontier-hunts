package com.formaworks.frontierhunts.recipes;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.survival.SurvivalContent;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * [recipes] Hides and pelts are crafting materials (fur clothing, robes, bedrolls, ghillie suits, tents), so wildlife drops
 * them even with Frontier Survival switched off. With survival on, {@code survival/SurvivalHarvest.drops} does the full
 * conversion (meat, fat, organs, pelts); this is only the hide part of it, called from there when survival is off.
 */
public final class PeltDrops {
   private PeltDrops() {
   }

   private static boolean bear(WildlifeSpecies s) {
      return s == WildlifeSpecies.BLACK_BEAR || s == WildlifeSpecies.GRIZZLY || s == WildlifeSpecies.POLAR_BEAR;
   }

   private static boolean furbearer(WildlifeSpecies s) {
      return s == WildlifeSpecies.WOLF || s == WildlifeSpecies.COYOTE || s == WildlifeSpecies.COUGAR || s == WildlifeSpecies.PANTHER
         || s == WildlifeSpecies.LION || s == WildlifeSpecies.CHEETAH;
   }

   public static void apply(LivingDropsEvent e, WildlifeMob m) {
      WildlifeSpecies s = m.species;
      boolean pelt = false;
      for (ItemEntity ie : e.getDrops()) {
         ItemStack st = ie.getItem();
         if (!st.is(Items.LEATHER)) {
            continue;
         }
         Item to = s == WildlifeSpecies.BISON ? SurvivalContent.HEAVY_HIDE.get()
            : bear(s) ? SurvivalContent.BEAR_PELT.get()
            : furbearer(s) ? SurvivalContent.FUR_PELT.get()
            : s == WildlifeSpecies.PRONGHORN ? HuntContent.DEER_HIDE.get() : null;
         if (to != null) {
            boolean one = bear(s) || furbearer(s);
            ie.setItem(new ItemStack(to, one ? 1 : Math.max(1, st.getCount())));
            pelt |= one;
         }
      }
      if (!pelt && (bear(s) || furbearer(s))) {
         ItemEntity ie = new ItemEntity(m.level(), m.getX(), m.getY() + 0.3, m.getZ(),
            new ItemStack(bear(s) ? SurvivalContent.BEAR_PELT.get() : SurvivalContent.FUR_PELT.get()));
         ie.setDefaultPickUpDelay();
         e.getDrops().add(ie);
      }
   }
}
