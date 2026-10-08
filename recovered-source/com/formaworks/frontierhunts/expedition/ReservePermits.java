package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.HunterLedger;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class ReservePermits extends SavedData {
   public static final int PRICE = 8;
   public static final int QUOTA = 3;
   public static final int RECOVERY_REWARD = 5;
   private final Map<UUID, Map<String, ReservePermits.Tag>> hunters = new HashMap<>();

   public static ReservePermits get(ServerLevel var0) {
      return (ReservePermits)var0.getServer()
         .overworld()
         .getDataStorage()
         .computeIfAbsent(new Factory(ReservePermits::new, ReservePermits::load, null), "frontierhunts_permits");
   }

   public static boolean eligible(String var0) {
      return GameSpecies.byId(var0) != null;
   }

   public static boolean inSeason(String var0, int var1) {
      GameSpecies var2 = GameSpecies.byId(var0);
      return var2 != null && var2.inSeason(var1);
   }

   public int remaining(UUID var1, String var2, long var3) {
      ReservePermits.Tag var5 = this.hunters.getOrDefault(var1, Map.of()).get(var2);
      return var5 != null && var5.month == var3 ? var5.remaining : 0;
   }

   public boolean buy(ServerPlayer var1, String var2) {
      HuntingCalendar.Date var3 = HuntingCalendar.date(var1.serverLevel());
      if (eligible(var2) && inSeason(var2, var3.month()) && ExpeditionService.nearBoard(var1) && this.remaining(var1.getUUID(), var2, var3.serial()) <= 0) {
         if (!HunterLedger.get(var1.serverLevel()).spend(var1.getUUID(), 8)) {
            return false;
         } else {
            Map var4 = this.hunters.computeIfAbsent(var1.getUUID(), var0 -> new HashMap<>());
            var4.entrySet().removeIf(var1x -> ((ReservePermits.Tag)var1x.getValue()).month != var3.serial());
            var4.put(var2, new ReservePermits.Tag(var3.serial(), 3));
            this.setDirty();
            return true;
         }
      } else {
         return false;
      }
   }

   public boolean recover(ServerPlayer var1, String var2) {
      if (!var1.hasInfiniteMaterials() && !var1.isSpectator()) {
         long var3 = HuntingCalendar.date(var1.serverLevel()).serial();
         int var5 = this.remaining(var1.getUUID(), var2, var3);
         if (var5 == 0) {
            return false;
         } else {
            this.hunters.get(var1.getUUID()).put(var2, new ReservePermits.Tag(var3, var5 - 1));
            this.setDirty();
            HunterLedger.get(var1.serverLevel()).provisionPayment(var1.getUUID(), 5);
            ExpeditionService.message(var1, "Conservation tag filed · +5 tokens · " + (var5 - 1) + " tags remaining");
            return true;
         }
      } else {
         return false;
      }
   }

   public JsonObject snapshot(ServerPlayer var1) {
      JsonObject var2 = new JsonObject();
      HuntingCalendar.Date var3 = HuntingCalendar.date(var1.serverLevel());
      var2.addProperty("date", var3.title());
      var2.addProperty("days_left", var3.daysPerMonth() - var3.day() + 1);
      JsonArray var4 = new JsonArray();

      for (GameSpecies var8 : GameSpecies.values()) {
         String var9 = var8.id;
         JsonObject var10 = new JsonObject();
         var10.addProperty("id", var9);
         var10.addProperty("title", var8.title);
         var10.addProperty("open", inSeason(var9, var3.month()));
         var10.addProperty("remaining", this.remaining(var1.getUUID(), var9, var3.serial()));
         var10.addProperty("months", HuntingCalendar.months(var8));
         var4.add(var10);
      }

      var2.add("species", var4);
      return var2;
   }

   public CompoundTag save(CompoundTag var1, Provider var2) {
      ListTag var3 = new ListTag();
      this.hunters.forEach((var1x, var2x) -> {
         CompoundTag var3x = new CompoundTag();
         var3x.putUUID("Hunter", var1x);
         ListTag var4 = new ListTag();
         var2x.forEach((var1xx, var2xx) -> {
            CompoundTag var3xx = new CompoundTag();
            var3xx.putString("Species", var1xx);
            var3xx.putLong("Month", var2xx.month);
            var3xx.putInt("Remaining", var2xx.remaining);
            var4.add(var3xx);
         });
         var3x.put("Tags", var4);
         var3.add(var3x);
      });
      var1.put("Hunters", var3);
      return var1;
   }

   public static ReservePermits load(CompoundTag var0, Provider var1) {
      ReservePermits var2 = new ReservePermits();

      for (net.minecraft.nbt.Tag var4 : var0.getList("Hunters", 10)) {
         CompoundTag var5 = (CompoundTag)var4;
         if (var5.hasUUID("Hunter")) {
            HashMap var6 = new HashMap();

            for (net.minecraft.nbt.Tag var8 : var5.getList("Tags", 10)) {
               CompoundTag var9 = (CompoundTag)var8;
               String var10 = var9.getString("Species");
               if (eligible(var10)) {
                  var6.put(var10, new ReservePermits.Tag(Math.max(0L, var9.getLong("Month")), Math.clamp((long)var9.getInt("Remaining"), 0, 3)));
               }
            }

            var2.hunters.put(var5.getUUID("Hunter"), var6);
         }
      }

      return var2;
   }

   private static record Tag(long month, int remaining) {
   }
}
