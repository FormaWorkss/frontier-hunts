package com.formaworks.frontierhunts.hunting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

public final class ImpactMarks {
   public static final int MAX = 5;

   public static CompoundTag add(CompoundTag var0, Vec3 var1, Vec3 var2, int var3, boolean var4, boolean var5, boolean var6) {
      CompoundTag var7 = new CompoundTag();
      int var8 = Math.clamp((long)var0.getInt("count"), 0, 5);
      int var9 = Math.min(var8, 4);

      for (int var10 = 0; var10 < var9; var10++) {
         copy(var0, Math.max(0, var8 - var9) + var10, var7, var10);
      }

      Vec3 var11 = var2.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : var2.normalize();
      put(var7, var9, var1, var11, Math.clamp((long)var3, 0, 31), var4, var5, var6);
      var7.putInt("count", var9 + 1);
      return var7;
   }

   public static CompoundTag addSkeletal(CompoundTag var0, Vec3 var1, Vec3 var2, int var3, boolean var4, boolean var5, boolean var6) {
      CompoundTag var7 = add(skeletal(var0) ? var0 : new CompoundTag(), var1, var2, var3, var4, var5, var6);
      var7.putInt("space", 5);
      return var7;
   }

   public static boolean skeletal(CompoundTag var0) {
      return var0.getInt("space") == 5;
   }

   public static ImpactMarks.Mark get(CompoundTag var0, int var1) {
      int var2 = Math.clamp((long)var0.getInt("count"), 0, 5);
      if (var1 >= 0 && var1 < var2) {
         Vec3 var3 = new Vec3(var0.getDouble("px" + var1), var0.getDouble("py" + var1), var0.getDouble("pz" + var1));
         Vec3 var4 = new Vec3(var0.getDouble("dx" + var1), var0.getDouble("dy" + var1), var0.getDouble("dz" + var1));
         if (Double.isFinite(var3.lengthSqr()) && Double.isFinite(var4.lengthSqr())) {
            int var5 = var0.contains("bone" + var1) ? Math.clamp((long)var0.getInt("bone" + var1), 0, 31) : -1;
            return new ImpactMarks.Mark(
               var3,
               var4.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : var4.normalize(),
               var5,
               var0.getBoolean("arrow" + var1),
               var0.getBoolean("tracer" + var1),
               var0.getBoolean("exit" + var1)
            );
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static void copy(CompoundTag var0, int var1, CompoundTag var2, int var3) {
      ImpactMarks.Mark var4 = get(var0, var1);
      if (var4 != null) {
         put(var2, var3, var4.point, var4.direction, var4.bone, var4.arrow, var4.tracer, var4.exit);
      }
   }

   private static void put(CompoundTag var0, int var1, Vec3 var2, Vec3 var3, int var4, boolean var5, boolean var6, boolean var7) {
      var0.putDouble("px" + var1, var2.x);
      var0.putDouble("py" + var1, var2.y);
      var0.putDouble("pz" + var1, var2.z);
      var0.putDouble("dx" + var1, var3.x);
      var0.putDouble("dy" + var1, var3.y);
      var0.putDouble("dz" + var1, var3.z);
      var0.putInt("bone" + var1, var4);
      var0.putBoolean("arrow" + var1, var5);
      var0.putBoolean("tracer" + var1, var6);
      var0.putBoolean("exit" + var1, var7);
   }

   private ImpactMarks() {
   }

   public static record Mark(Vec3 point, Vec3 direction, int bone, boolean arrow, boolean tracer, boolean exit) {
   }
}
