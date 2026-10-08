package com.formaworks.frontierhunts.camps;

import com.formaworks.frontierhunts.expedition.ExpeditionLedger;
import net.minecraft.server.level.ServerPlayer;

/**
 * [1.1.6] What a camp upgrade changes. Each tier still issues its station (lodge stores, ammo reloader, bow tuning
 * rack, fishing station); on top of that every member of the camp gets:
 * <ol>
 * <li>Tent camp: the camp post is a licence counter - buy licences, tags, stamps and ranger services there.</li>
 * <li>Wall-tent camp: venison delivered on the ranger board pays 25% more (the camp's meat pole).</li>
 * <li>Hunting lodge: expedition contracts give you 30 minutes instead of 20.</li>
 * <li>Frontier lodge: one more deer tag every season (the landowner's tag).</li>
 * </ol>
 */
public final class CampPerks {
   private CampPerks() {
   }

   /** the camp tier of the player's camp (0 = none or spike camp) */
   public static int tier(ServerPlayer p) {
      try {
         CampRegistry.Camp c = CampService.campOf(p);
         if (c != null) {
            return Math.clamp(c.tier, 0, 4);
         }
         return Math.clamp(ExpeditionLedger.get(p.serverLevel()).hunter(p.getUUID()).camp, 0, 4);
      } catch (RuntimeException e) {
         return 0;
      }
   }

   public static final String[] PERKS = {
      "The camp post works as a licence counter.",
      "Venison delivered on the ranger board pays 25% more.",
      "Expedition contracts give you 30 minutes instead of 20.",
      "One more deer tag every season (landowner tag)."
   };
}
