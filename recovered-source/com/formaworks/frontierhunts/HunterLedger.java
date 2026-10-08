package com.formaworks.frontierhunts;

import com.formaworks.frontierhunts.progression.AssignmentProgress;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class HunterLedger extends SavedData {
   public static final int SCHEMA = 4;
   private final Map<UUID, HunterLedger.Hunter> hunters = new HashMap<>();
   private boolean initialized;

   public static HunterLedger get(ServerLevel var0) {
      return (HunterLedger)var0.getServer()
         .overworld()
         .getDataStorage()
         .computeIfAbsent(new Factory(HunterLedger::new, HunterLedger::load, null), "frontierhunts_hunters");
   }

   public boolean initialized() {
      return this.initialized;
   }

   public void initialize() {
      this.initialized = true;
      this.setDirty();
   }

   public HunterLedger.Hunter hunter(UUID var1) {
      return this.hunters.computeIfAbsent(var1, var1x -> {
         this.setDirty();
         return new HunterLedger.Hunter();
      });
   }

   public void issued(UUID var1) {
      this.hunter(var1).journalIssued = true;
      this.setDirty();
   }

   public void opened(UUID var1) {
      this.hunter(var1).journalOpened = true;
      this.setDirty();
   }

   public void inspectClue(UUID var1) {
      if (!this.hunter(var1).clueInspected) {
         this.hunter(var1).clueInspected = true;
         this.setDirty();
      }
   }

   public boolean harvestWhitetail(UUID var1) {
      HunterLedger.Hunter var2 = this.hunter(var1);
      if (var2.clueInspected && !var2.whitetailHarvested) {
         var2.whitetailHarvested = true;
         this.setDirty();
         return true;
      } else {
         return false;
      }
   }

   public boolean claimFirstHunt(UUID var1) {
      HunterLedger.Hunter var2 = this.hunter(var1);
      if (var2.firstHuntReady() && var2.tokens <= 2147483587) {
         var2.tokens += 60;
         var2.firstHuntClaimed = true;
         this.setDirty();
         return true;
      } else {
         return false;
      }
   }

   public boolean provisionPayment(UUID var1, int var2) {
      HunterLedger.Hunter var3 = this.hunter(var1);
      if (var2 > 0 && var2 <= 100 && var3.tokens <= Integer.MAX_VALUE - var2 && var3.provisionsDelivered != Integer.MAX_VALUE) {
         var3.tokens += var2;
         var3.provisionsDelivered++;
         this.setDirty();
         return true;
      } else {
         return false;
      }
   }

   public boolean spend(UUID var1, int var2) {
      HunterLedger.Hunter var3 = this.hunter(var1);
      if (var2 > 0 && var3.tokens >= var2) {
         var3.tokens -= var2;
         this.setDirty();
         return true;
      } else {
         return false;
      }
   }

   public boolean claimAssignment(UUID var1, long var2) {
      HunterLedger.Hunter var4 = this.hunter(var1);
      AssignmentProgress var5 = var4.assignments;
      if (var5.state() == AssignmentProgress.State.READY && var4.tokens <= Integer.MAX_VALUE - var5.terms().tokens()) {
         int var6 = var5.terms().tokens();
         if (!var5.finish(var2)) {
            return false;
         } else {
            var4.tokens += var6;
            this.setDirty();
            return true;
         }
      } else {
         return false;
      }
   }

   public boolean respec(UUID var1) {
      HunterLedger.Hunter var2 = this.hunter(var1);
      if (var2.tokens >= 20 && var2.assignments.respec()) {
         var2.tokens -= 20;
         this.setDirty();
         return true;
      } else {
         return false;
      }
   }

   public boolean discover(UUID var1, ResourceLocation var2, int var3) {
      HunterLedger.Hunter var4 = this.hunter(var1);
      if (var4.discoveries.size() >= Math.min(2048, Math.max(3, var3))) {
         return false;
      } else if (var4.discoveries.add(var2.toString())) {
         this.setDirty();
         return true;
      } else {
         return false;
      }
   }

   public boolean claimSurvey(UUID var1, int var2) {
      HunterLedger.Hunter var3 = this.hunter(var1);
      if (var3.surveyReady() && var2 >= 0 && var2 <= 10000 && var3.tokens <= Integer.MAX_VALUE - var2) {
         var3.tokens = Math.addExact(var3.tokens, var2);
         var3.surveyClaimed = true;
         this.setDirty();
         return true;
      } else {
         return false;
      }
   }

   public CompoundTag save(CompoundTag var1, Provider var2) {
      var1.putInt("schema", 4);
      var1.putBoolean("initialized", this.initialized);
      ListTag var3 = new ListTag();
      this.hunters.entrySet().stream().sorted(Entry.comparingByKey()).forEach(var1x -> {
         HunterLedger.Hunter var2x = var1x.getValue();
         CompoundTag var3x = new CompoundTag();
         var3x.putUUID("uuid", var1x.getKey());
         var3x.putInt("tokens", var2x.tokens);
         var3x.putInt("provisions_delivered", var2x.provisionsDelivered);
         var3x.put("assignments", var2x.assignments.save());
         var3x.putBoolean("journal_issued", var2x.journalIssued);
         var3x.putBoolean("journal_opened", var2x.journalOpened);
         var3x.putBoolean("survey_claimed", var2x.surveyClaimed);
         var3x.putBoolean("clue_inspected", var2x.clueInspected);
         var3x.putBoolean("whitetail_harvested", var2x.whitetailHarvested);
         var3x.putBoolean("first_hunt_claimed", var2x.firstHuntClaimed);
         ListTag var4 = new ListTag();
         var2x.discoveries.forEach(var1xx -> var4.add(StringTag.valueOf(var1xx)));
         var3x.put("biomes", var4);
         var3.add(var3x);
      });
      var1.put("hunters", var3);
      return var1;
   }

   public static HunterLedger load(CompoundTag var0, Provider var1) {
      int var2 = var0.getInt("schema");
      if (var2 >= 1 && var2 <= 4) {
         HunterLedger var3 = new HunterLedger();
         var3.initialized = var0.getBoolean("initialized");
         ListTag var4 = var0.getList("hunters", 10);

         for (int var5 = 0; var5 < var4.size(); var5++) {
            CompoundTag var6 = var4.getCompound(var5);
            if (!var6.hasUUID("uuid")) {
               throw new IllegalStateException("Hunter record missing UUID at " + var5);
            }

            UUID var7 = var6.getUUID("uuid");
            if (var3.hunters.containsKey(var7)) {
               throw new IllegalStateException("Duplicate hunter UUID " + var7);
            }

            HunterLedger.Hunter var8 = new HunterLedger.Hunter();
            var8.tokens = var6.getInt("tokens");
            if (var2 >= 4) {
               if (!var6.contains("assignments", 10)) {
                  throw new IllegalStateException("Missing assignment record for " + var7);
               }

               var8.assignments = AssignmentProgress.load(var6.getCompound("assignments"));
            }

            if (var8.tokens < 0) {
               throw new IllegalStateException("Negative token balance for " + var7);
            }

            var8.provisionsDelivered = var2 >= 3 ? var6.getInt("provisions_delivered") : 0;
            if (var8.provisionsDelivered < 0) {
               throw new IllegalStateException("Negative provision count for " + var7);
            }

            var8.journalIssued = var6.getBoolean("journal_issued");
            var8.journalOpened = var6.getBoolean("journal_opened");
            var8.surveyClaimed = var6.getBoolean("survey_claimed");
            if (var2 >= 2) {
               var8.clueInspected = var6.getBoolean("clue_inspected");
               var8.whitetailHarvested = var6.getBoolean("whitetail_harvested");
               var8.firstHuntClaimed = var6.getBoolean("first_hunt_claimed");
               if (var8.firstHuntClaimed && (!var8.clueInspected || !var8.whitetailHarvested)) {
                  throw new IllegalStateException("Inconsistent first-hunt progress for " + var7);
               }
            }

            ListTag var9 = var6.getList("biomes", 8);
            if (var9.size() > 2048) {
               throw new IllegalStateException("Biome ledger exceeds supported capacity for " + var7);
            }

            for (int var10 = 0; var10 < var9.size(); var10++) {
               String var11 = var9.getString(var10);
               if (ResourceLocation.tryParse(var11) == null) {
                  throw new IllegalStateException("Invalid saved biome id " + var11);
               }

               var8.discoveries.add(var11);
            }

            var3.hunters.put(var7, var8);
         }

         if (var2 < 4) {
            var3.setDirty();
         }

         return var3;
      } else {
         throw new IllegalStateException("Unsupported Frontier Hunts save schema " + var2 + "; use the matching mod version. The save must not be rewritten.");
      }
   }

   public static final class Hunter {
      private AssignmentProgress assignments = new AssignmentProgress();
      private final LinkedHashSet<String> discoveries = new LinkedHashSet<>();
      private boolean journalIssued;
      private boolean journalOpened;
      private boolean surveyClaimed;
      private int tokens;
      private int provisionsDelivered;
      private boolean clueInspected;
      private boolean whitetailHarvested;
      private boolean firstHuntClaimed;

      public AssignmentProgress assignments() {
         return this.assignments;
      }

      public int provisionsDelivered() {
         return this.provisionsDelivered;
      }

      public boolean clueInspected() {
         return this.clueInspected;
      }

      public boolean whitetailHarvested() {
         return this.whitetailHarvested;
      }

      public boolean firstHuntClaimed() {
         return this.firstHuntClaimed;
      }

      public boolean firstHuntReady() {
         return this.clueInspected && this.whitetailHarvested && !this.firstHuntClaimed;
      }

      public List<String> discoveries() {
         return List.copyOf(this.discoveries);
      }

      public boolean journalIssued() {
         return this.journalIssued;
      }

      public boolean journalOpened() {
         return this.journalOpened;
      }

      public boolean surveyClaimed() {
         return this.surveyClaimed;
      }

      public int tokens() {
         return this.tokens;
      }

      public boolean surveyReady() {
         return this.journalOpened && this.discoveries.size() >= 3 && !this.surveyClaimed;
      }
   }
}
