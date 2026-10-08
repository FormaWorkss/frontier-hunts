package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.HunterLedger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class CampaignProgress {
   public static final long CONTRACT_TICKS = 24000L;

   public static String contractStatus(ExpeditionLedger.Hunter var0, long var1) {
      return var0.contract < 0 ? "none" : (var0.contractReadyAt > 0L ? "ready" : (var1 > var0.deadline ? "expired" : "active"));
   }

   public static boolean accept(ExpeditionLedger.Hunter var0, int var1, long var2) {
      if (var1 >= 0
         && var1 < Campaign.CONTRACTS.size()
         && com.formaworks.frontierhunts.hunts.HuntHooks.contractOffered(var1) // [hunts] species contracts only while on this month's board
         && (var0.contract < 0 || contractStatus(var0, var2).equals("expired"))
         && var0.contractSerial != Long.MAX_VALUE) {
         var0.contract = var1;
         var0.contractCount = 0;
         var0.contractReadyAt = 0L;
         var0.started = var2;
         var0.deadline = var2 + (var0.camp >= 3 ? 36000L : 24000L); // [1.1.6] a hunting-lodge camp: 30 minutes instead of 20
         var0.contractSerial++;
         return true;
      } else {
         return false;
      }
   }

   public static boolean event(ExpeditionLedger.Hunter var0, String var1, String var2, int var3, double var4, long var6, boolean var8) {
      if (var3 > 0 && var3 <= 64 && Double.isFinite(var4) && !(var4 < 0.0) && var2 != null && var1 != null) {
         if (var1.equals("visit")) {
            if (ResourceLocation.tryParse(var2) == null || var0.discoveries.size() >= 256 || !var0.discoveries.add(var2)) {
               return false;
            }

            var3 = 1;
         }

         if (var0.stage < Campaign.MISSIONS.size()) {
            Campaign.Mission var9 = Campaign.MISSIONS.get(var0.stage);
            if (Campaign.matches(var9, var1, var2)) {
               var0.count = Math.min(var9.amount(), var0.count + var3);
            }
         }

         if (var0.contract >= 0 && var0.contract < Campaign.CONTRACTS.size() && var0.contractReadyAt == 0L && var6 <= var0.deadline) {
            Campaign.Mission var11 = Campaign.CONTRACTS.get(var0.contract);
            boolean var10 = var1.equals("harvest")
               && var11.species().equals(var2)
               && (var11.event().equals("longshot") && var4 >= 150.0 || var11.event().equals("group") && var8);
            if (Campaign.matches(var11, var1, var2) || var10) {
               var0.contractCount = Math.min(var11.amount(), var0.contractCount + var3);
               if (var0.contractCount >= var11.amount()) {
                  var0.contractReadyAt = Math.max(1L, var6);
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public static boolean claimMission(ServerPlayer var0, String var1) {
      if (var0.hasDisconnected()) {
         return false; // [1.2.7] the hunter has already left
      }
      ExpeditionLedger var2 = ExpeditionLedger.get(var0.serverLevel());
      ExpeditionLedger.Hunter var3 = var2.hunter(var0.getUUID());
      if (var3.stage >= Campaign.MISSIONS.size()) {
         return false;
      } else {
         Campaign.Mission var4 = Campaign.MISSIONS.get(var3.stage);
         if (Campaign.id(var3.stage).equals(var1)
            && var3.count >= var4.amount()
            && HunterLedger.get(var0.serverLevel()).provisionPayment(var0.getUUID(), var4.tokens())) {
            var3.stage++;
            var3.count = 0;
            var3.xp = Math.min(1000000, var3.xp + 60);
            var2.setDirty();
            ExpeditionService.message(var0, "Report filed · " + var4.tokens() + " tokens");
            com.formaworks.frontierhunts.progression.Durability.commit(var0.server, "mission paid"); // [1.2.7]
            return true;
         } else {
            return false;
         }
      }
   }

   public static boolean claimContract(ServerPlayer var0, String var1) {
      if (var0.hasDisconnected()) {
         return false; // [1.2.7] the hunter has already left
      }
      ExpeditionLedger var2 = ExpeditionLedger.get(var0.serverLevel());
      ExpeditionLedger.Hunter var3 = var2.hunter(var0.getUUID());
      if (var3.contract >= 0 && var3.contract < Campaign.CONTRACTS.size() && var3.contractReadyAt > 0L && Long.toString(var3.contractSerial).equals(var1)) {
         Campaign.Mission var4 = Campaign.CONTRACTS.get(var3.contract);
         if (var3.contractCount >= var4.amount() && HunterLedger.get(var0.serverLevel()).provisionPayment(var0.getUUID(), var4.tokens())) {
            var3.completed = Math.min(1000000, var3.completed + 1);
            long var5 = Math.max(1L, var3.contractReadyAt - var3.started);
            if (var3.bestHunt == 0L || var5 < var3.bestHunt) {
               var3.bestHunt = var5;
            }

            var3.contract = -1;
            var3.contractCount = 0;
            var3.contractReadyAt = 0L;
            var3.xp = Math.min(1000000, var3.xp + 40);
            var2.setDirty();
            ExpeditionService.message(var0, "Contract paid · " + var4.tokens() + " tokens");
            com.formaworks.frontierhunts.progression.Durability.commit(var0.server, "contract paid"); // [1.2.7]
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private CampaignProgress() {
   }
}
