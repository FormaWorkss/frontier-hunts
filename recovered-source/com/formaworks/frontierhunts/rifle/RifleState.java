package com.formaworks.frontierhunts.rifle;

import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public record RifleState(int magazine, boolean chamber, boolean spent, int action, long started, String dimension, String owner, long firedAt, int limit) {
   public static final int CAPACITY = 3;
   public static final int IDLE = 0;
   public static final int CYCLE = 1;
   public static final int RELOAD = 2;
   public static final int CYCLE_TICKS = 26;
   public static final int RELOAD_TICKS = 52;
   public static final RifleState EMPTY = new RifleState(0, false, false, 0, 0L, "", "", -1000L);

   public RifleState(int var1, boolean var2, boolean var3, int var4, long var5, String var7, String var8, long var9) {
      this(var1, var2, var3, var4, var5, var7, var8, var9, 3);
   }

   public RifleState(int magazine, boolean chamber, boolean spent, int action, long started, String dimension, String owner, long firedAt, int limit) {
      limit = limit == 5 ? 5 : 3;
      magazine = Math.clamp((long)magazine, 0, limit - (chamber ? 1 : 0));
      action = Math.clamp((long)action, 0, 2);
      if (chamber) {
         spent = false;
      }

      this.magazine = magazine;
      this.chamber = chamber;
      this.spent = spent;
      this.action = action;
      this.started = started;
      this.dimension = dimension;
      this.owner = owner;
      this.firedAt = firedAt;
      this.limit = limit;
   }

   public static int capacity(ItemStack var0) {
      return ExpeditionWeapon.attachment(var0, "sniper_magazine") ? 5 : 3;
   }

   private static RifleState empty(ItemStack var0) {
      return new RifleState(0, false, false, 0, 0L, "", "", -1000L, capacity(var0));
   }

   public static RifleState read(ItemStack var0) {
      CustomData var1 = (CustomData)var0.get(DataComponents.CUSTOM_DATA);
      if (var1 == null) {
         return empty(var0);
      } else {
         CompoundTag var2 = var1.copyTag().getCompound("frontier_rifle");
         return var2.getInt("schema") >= 1 && var2.getInt("schema") <= 2
            ? new RifleState(
               var2.getInt("magazine"),
               var2.getBoolean("chamber"),
               var2.getBoolean("spent"),
               var2.getInt("action"),
               var2.getLong("started"),
               var2.getString("dimension"),
               var2.getString("owner"),
               var2.getLong("fired_at"),
               capacity(var0)
            )
            : empty(var0);
      }
   }

   public void write(ItemStack var1) {
      CompoundTag var2 = ((CustomData)var1.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).copyTag();
      CompoundTag var3 = new CompoundTag();
      var3.putInt("schema", 2);
      var3.putInt("magazine", Math.min(this.magazine, capacity(var1) - (this.chamber ? 1 : 0)));
      var3.putBoolean("chamber", this.chamber);
      var3.putBoolean("spent", this.spent);
      var3.putInt("action", this.action);
      var3.putLong("started", this.started);
      var3.putString("dimension", this.dimension);
      var3.putString("owner", this.owner);
      var3.putLong("fired_at", this.firedAt);
      var2.put("frontier_rifle", var3);
      var1.set(DataComponents.CUSTOM_DATA, CustomData.of(var2));
   }

   public RifleState idle() {
      return new RifleState(this.magazine, this.chamber, this.spent, 0, 0L, "", "", this.firedAt, this.limit);
   }

   public static int legacyExcess(ItemStack var0) {
      CustomData var1 = (CustomData)var0.get(DataComponents.CUSTOM_DATA);
      if (var1 == null) {
         return 0;
      } else {
         CompoundTag var2 = var1.copyTag().getCompound("frontier_rifle");
         return var2.getInt("schema") != 1
            ? 0
            : Math.max(0, Math.clamp((long)var2.getInt("magazine"), 0, 5) + (var2.getBoolean("chamber") ? 1 : 0) - capacity(var0));
      }
   }

   public int duration() {
      return this.action == 2 ? 52 : 26;
   }

   public int total() {
      return this.magazine + (this.chamber ? 1 : 0);
   }
}
