package com.formaworks.frontierhunts.expedition;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class ExpeditionLedger extends SavedData {
   public final Map<UUID, ExpeditionLedger.Hunter> hunters = new HashMap<>();

   public ExpeditionLedger.Hunter hunter(UUID var1) {
      return this.hunters.computeIfAbsent(var1, var1x -> {
         this.setDirty();
         return new ExpeditionLedger.Hunter();
      });
   }

   public static ExpeditionLedger get(ServerLevel var0) {
      return (ExpeditionLedger)var0.getServer()
         .overworld()
         .getDataStorage()
         .computeIfAbsent(new Factory(ExpeditionLedger::new, ExpeditionLedger::load, null), "frontierhunts_expeditions");
   }

   public CompoundTag save(CompoundTag var1, Provider var2) {
      ListTag var3 = new ListTag();
      this.hunters.forEach((var1x, var2x) -> {
         CompoundTag var3x = new CompoundTag();
         var3x.putUUID("id", var1x);
         var3x.putString("name", var2x.name);
         var3x.putInt("stage", var2x.stage);
         var3x.putString("mission_id", Campaign.id(var2x.stage));
         var3x.putInt("count", var2x.count);
         var3x.putInt("xp", var2x.xp);
         var3x.putIntArray("skills", var2x.skills);
         var3x.putInt("training", var2x.training);
         var3x.putInt("training_progress", var2x.trainingProgress);
         var3x.putLong("training_deadline", var2x.trainingDeadline);
         var3x.putInt("contract", var2x.contract);
         var3x.putInt("contract_count", var2x.contractCount);
         var3x.putInt("completed", var2x.completed);
         var3x.putInt("camp", var2x.camp);
         var3x.putLong("deadline", var2x.deadline);
         var3x.putLong("contract_ready", var2x.contractReadyAt);
         var3x.putLong("contract_serial", var2x.contractSerial);
         var3x.putLong("best_hunt", var2x.bestHunt);
         var3x.putLong("started", var2x.started);
         var3x.putLong("cooldown", var2x.cooldown);
         var3x.putDouble("longest", var2x.longest);
         var3x.putDouble("best_trophy", var2x.bestTrophy);
         var3x.putBoolean("starter", var2x.starter);
         var3x.putString("marker", var2x.marker);
         if (var2x.party != null) {
            var3x.putUUID("party", var2x.party);
         }

         if (var2x.invite != null) {
            var3x.putUUID("invite", var2x.invite);
         }

         ListTag var4 = new ListTag();
         var2x.discoveries.stream().sorted().limit(256L).forEach(var1xx -> var4.add(StringTag.valueOf(var1xx)));
         var3x.put("discoveries", var4);
         var3.add(var3x);
      });
      var1.put("hunters", var3);
      return var1;
   }

   public static ExpeditionLedger load(CompoundTag var0, Provider var1) {
      ExpeditionLedger var2 = new ExpeditionLedger();

      for (Tag var4 : var0.getList("hunters", 10)) {
         CompoundTag var5 = (CompoundTag)var4;
         if (var5.hasUUID("id")) {
            ExpeditionLedger.Hunter var6 = new ExpeditionLedger.Hunter();
            var6.name = var5.getString("name");
            var6.stage = Campaign.index(var5.getString("mission_id"), var5.getInt("stage"));
            var6.count = var6.stage < Campaign.MISSIONS.size() ? Math.clamp((long)var5.getInt("count"), 0, Campaign.MISSIONS.get(var6.stage).amount()) : 0;
            var6.xp = Math.clamp((long)var5.getInt("xp"), 0, 1000000);
            var6.training = var5.contains("training") ? Math.clamp((long)var5.getInt("training"), -1, 3) : -1;
            var6.trainingProgress = Math.clamp((long)var5.getInt("training_progress"), 0, 8);
            var6.trainingDeadline = Math.max(0L, var5.getLong("training_deadline"));
            int[] var7 = var5.getIntArray("skills");

            for (int var8 = 0; var8 < Math.min(4, var7.length); var8++) {
               var6.skills[var8] = Math.clamp((long)var7[var8], 0, 3);
            }

            var6.contract = var5.contains("contract") ? Math.clamp((long)var5.getInt("contract"), -1, Campaign.CONTRACTS.size() - 1) : -1;
            var6.contractCount = var6.contract >= 0 ? Math.clamp((long)var5.getInt("contract_count"), 0, Campaign.CONTRACTS.get(var6.contract).amount()) : 0;
            var6.completed = Math.clamp((long)var5.getInt("completed"), 0, 1000000);
            var6.camp = Math.clamp((long)var5.getInt("camp"), 0, 4);
            var6.deadline = Math.max(0L, var5.getLong("deadline"));
            var6.contractSerial = Math.max(0L, var5.getLong("contract_serial"));
            var6.contractReadyAt = var6.contract >= 0 && var6.contractCount >= Campaign.CONTRACTS.get(var6.contract).amount()
               ? Math.max(1L, var5.contains("contract_ready") ? var5.getLong("contract_ready") : var6.deadline)
               : 0L;
            var6.bestHunt = var5.getLong("best_hunt");
            var6.started = var5.getLong("started");
            var6.cooldown = var5.getLong("cooldown");
            var6.longest = finite(var5.getDouble("longest"));
            var6.bestTrophy = finite(var5.getDouble("best_trophy"));
            var6.starter = var5.getBoolean("starter");
            var6.marker = var5.getString("marker");
            var6.party = var5.hasUUID("party") ? var5.getUUID("party") : null;
            var6.invite = var5.hasUUID("invite") ? var5.getUUID("invite") : null;

            for (Tag var9 : var5.getList("discoveries", 8)) {
               if (var6.discoveries.size() < 256 && ResourceLocation.tryParse(var9.getAsString()) != null) {
                  var6.discoveries.add(var9.getAsString());
               }
            }

            var2.hunters.put(var5.getUUID("id"), var6);
         }
      }

      return var2;
   }

   private static double finite(double var0) {
      return Double.isFinite(var0) ? Math.clamp(var0, 0.0, 1000000.0) : 0.0;
   }

   public static class Hunter {
      public int stage;
      public int count;
      public int xp;
      public int contract = -1;
      public int contractCount;
      public int completed;
      public int camp;
      public int training = -1;
      public int trainingProgress;
      public int[] skills = new int[4];
      public long deadline;
      public long bestHunt;
      public long started;
      public long cooldown;
      public long contractReadyAt;
      public long contractSerial;
      public long trainingDeadline;
      public double longest;
      public double bestTrophy;
      public boolean starter;
      public UUID party;
      public UUID invite;
      public String name = "Hunter";
      public String marker = "";
      public Set<String> discoveries = new HashSet<>();
   }
}
