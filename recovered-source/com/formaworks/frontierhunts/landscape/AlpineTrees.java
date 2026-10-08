package com.formaworks.frontierhunts.landscape;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongListIterator;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

final class AlpineTrees {
   static BlockState block(String var0) {
      return AlpineRegistration.prop(var0).defaultBlockState();
   }

   static AlpineTrees.Growth grow(AlpineTrees.Species var0, RandomSource var1, float var2) {
      var2 = Mth.clamp(var2, 0.0F, 1.0F);
      AlpineTrees.Growth var3 = new AlpineTrees.Growth(var0);
      switch (var0) {
         case SPRUCE:
            var3.wood = block("alpine_spruce_log");
            var3.leaf = block("spruce_boughs");
            var3.litter = true;
            spire(var3, var1, var2, false, false);
            break;
         case FIR:
            var3.wood = block("alpine_spruce_log");
            var3.leaf = block("fir_needles");
            var3.litter = true;
            spire(var3, var1, var2, true, false);
            break;
         case PINE:
            var3.wood = block("alpine_pine_log");
            var3.leaf = block("pine_needles");
            var3.litter = true;
            pine(var3, var1, var2);
            break;
         case ASPEN:
            var3.wood = block("aspen_log");
            var3.leaf = block("aspen_leaves");
            aspen(var3, var1, var2);
            var3.broadleaf = true;
            break;
         case BIRCH:
            var3.wood = block("birch_log");
            var3.leaf = block("birch_leaves");
            birch(var3, var1, var2);
            var3.broadleaf = true;
            break;
         case MAPLE:
            var3.wood = block("alpine_maple_log");
            var3.leaf = block("maple_leaves");
            maple(var3, var1, var2);
            break;
         case WILLOW:
            var3.wood = block("alpine_maple_log");
            var3.leaf = block("willow_leaves");
            willow(var3, var1, var2);
            var3.broadleaf = true;
            break;
         case YOUNG_SPRUCE:
            var3.wood = block("alpine_spruce_log");
            var3.leaf = block("spruce_boughs");
            spire(var3, var1, var2, false, true);
            break;
         case YOUNG_FIR:
            var3.wood = block("alpine_spruce_log");
            var3.leaf = block("fir_needles");
            spire(var3, var1, var2, true, true);
            break;
         case SNAG:
            var3.wood = block("alpine_spruce_log");
            snag(var3, var1, var2);
            break;
         case DEADFALL:
            var3.wood = block("alpine_spruce_log");
            deadfall(var3, var1, var2);
            break;
         case GOLDEN_ASPEN:
            var3.wood = block("aspen_log");
            var3.leaf = block("golden_aspen_leaves");
            aspen(var3, var1, var2);
            var3.broadleaf = true;
            break;
         case AUTUMN_MAPLE:
            var3.wood = block("alpine_maple_log");
            var3.leaf = block("autumn_maple_leaves");
            maple(var3, var1, var2);
            var3.broadleaf = true;
            break;
         case BLUE_SPRUCE:
            var3.wood = block("alpine_spruce_log");
            var3.leaf = block("blue_spruce_boughs");
            var3.litter = true;
            spire(var3, var1, var2, false, false);
            break;
         case LARCH:
            var3.wood = block("alpine_pine_log");
            var3.leaf = block("larch_needles");
            var3.litter = true;
            spire(var3, var1, var2, true, false);
            LongArrayList var4 = new LongArrayList();
            LongBidirectionalIterator var5 = var3.leaves.keySet().iterator();

            while (var5.hasNext()) {
               long var6 = (Long)var5.next();
               int var8 = BlockPos.getX(var6);
               int var9 = BlockPos.getZ(var6);
               if (Math.abs(var8) + Math.abs(var9) >= 2 && var1.nextFloat() < 0.24F) {
                  var4.add(var6);
               }
            }

            LongListIterator var11 = var4.iterator();

            while (var11.hasNext()) {
               long var12 = (Long)var11.next();
               var3.leaves.remove(var12);
            }
            break;
         case ALDER:
            var3.wood = block("alpine_alder_log");
            var3.leaf = block("alder_leaves");
            birch(var3, var1, var2 * 0.85F);
            var3.broadleaf = true;
            break;
         case ROWAN:
            var3.wood = block("alpine_rowan_log");
            var3.leaf = block("rowan_leaves");
            aspen(var3, var1, var2 * 0.8F);
            var3.broadleaf = true;
            break;
         case COTTONWOOD:
            var3.wood = block("alpine_cottonwood_log");
            var3.leaf = block("cottonwood_leaves");
            maple(var3, var1, var2);
            var3.broadleaf = true;
            break;
         case DEADFALL_PINE:
            var3.wood = block("alpine_pine_log");
            deadfall(var3, var1, var2);
      }

      trim(var3);
      return var3;
   }

   private static void spire(AlpineTrees.Growth var0, RandomSource var1, float var2, boolean var3, boolean var4) {
      int var5;
      int var6;
      double var7;
      if (var4) {
         var5 = 3 + Math.round(var2 * 4.0F) + var1.nextInt(2);
         var7 = var3 ? 0.9 + (double)var2 * 0.6 : 1.1 + (double)var2 * 0.8;
         var6 = 0;
      } else if (var3) {
         var5 = Math.round(Mth.lerp(var2, 11.0F, 20.0F)) + var1.nextInt(3);
         var7 = (double)Mth.lerp(var2, 1.5F, 2.5F) + (double)var1.nextFloat() * 0.3;
         var6 = Math.round(Mth.lerp(var2, 1.5F, 4.5F)) + var1.nextInt(2);
      } else {
         var5 = Math.round(Mth.lerp(var2, 13.0F, 24.0F)) + var1.nextInt(4);
         var7 = (double)Mth.lerp(var2, 2.2F, 3.4F) + (double)var1.nextFloat() * 0.4;
         var6 = Math.max(var2 > 0.4F ? 4 : 3, Math.round((float)var5 * Mth.lerp(var2, 0.16F, 0.28F))) + var1.nextInt(2);
      }

      boolean var9 = !var4 && !var3 && var2 > 0.9F && var1.nextFloat() < 0.4F;
      if (var9) {
         var5 += 4 + var1.nextInt(4);
         var7 += 0.7;
      }

      var0.height = var5 + 1;
      int var10 = var5 - 2;
      double var11 = var9 ? 0.5 : 0.0;

      for (int var13 = 0; var13 <= var10; var13++) {
         var0.log(0, var13, 0, Axis.Y);
         if (var9 && (double)var13 < (double)var5 * 0.62) {
            var0.log(1, var13, 0, Axis.Y);
            var0.log(0, var13, 1, Axis.Y);
            var0.log(1, var13, 1, Axis.Y);
         }
      }

      ArrayList var30 = new ArrayList();
      int var14 = var5 - 1;

      while (var14 >= var6) {
         var30.add(var14);
         double var15 = (double)(var14 - var6) / (double)Math.max(1, var5 - var6);
         var14 -= !var4 && !(var15 > 0.55) && (!var3 || !(var15 > 0.35)) ? 3 : 2;
      }

      for (int var31 = 0; var31 < var30.size(); var31++) {
         int var16 = (Integer)var30.get(var31);
         double var17 = (double)(var16 - var6) / (double)Math.max(1, var5 - var6);
         double var19 = Math.max(0.9, var7 * Math.pow(1.0 - var17, var3 ? 0.75 : 0.85));
         AlpineTrees.Lobes var21 = new AlpineTrees.Lobes(var1, var3 ? 0.18 : 0.3);
         int var22 = (int)Math.ceil(var19 * 1.35) + 1;

         for (int var23 = -var22; var23 <= var22 + (var9 ? 1 : 0); var23++) {
            for (int var24 = -var22; var24 <= var22 + (var9 ? 1 : 0); var24++) {
               double var25 = Math.sqrt(((double)var23 - var11) * ((double)var23 - var11) + ((double)var24 - var11) * ((double)var24 - var11));
               double var27 = var19 * var21.at(Math.atan2((double)var24 - var11, (double)var23 - var11)) + 0.4 + var11;
               if (!(var25 > var27)) {
                  boolean var29 = var25 > 1.3 && var25 > var27 * 0.62;
                  if (var29 && var16 - 1 >= 0) {
                     var0.leaf(var23, var16 - 1, var24);
                     if (var25 > var27 * 0.8) {
                        continue;
                     }
                  }

                  var0.leaf(var23, var16, var24);
                  if (var25 < var27 * 0.45 && var19 > 1.6) {
                     var0.leaf(var23, var16 + 1, var24);
                  }
               }
            }
         }
      }

      for (int var32 = Math.max(0, var6 - 1); var32 <= var10; var32++) {
         for (Direction var35 : Plane.HORIZONTAL) {
            var0.leaf(var35.getStepX(), var32, var35.getStepZ());
         }
      }

      for (int var33 = var10 + 1; var33 <= var5 + 1; var33++) {
         var0.leaf(0, var33, 0);
      }

      if (!var4 && !var3 && var2 > 0.7F && var1.nextFloat() < 0.6F) {
         flare(var0, var1);
      }
   }

   private static void flare(AlpineTrees.Growth var0, RandomSource var1) {
      for (Direction var3 : Plane.HORIZONTAL) {
         if (var1.nextFloat() < 0.45F) {
            var0.log(var3.getStepX(), 0, var3.getStepZ(), var3.getAxis());
         }
      }
   }

   private static void pine(AlpineTrees.Growth var0, RandomSource var1, float var2) {
      int var3 = Math.round(Mth.lerp(var2, 14.0F, 25.0F)) + var1.nextInt(4);
      int var4 = Math.round((float)var3 * Mth.lerp(var2, 0.5F, 0.62F));
      double var5 = (double)Mth.lerp(var2, 1.7F, 2.7F) + (double)var1.nextFloat() * 0.3;
      var0.height = var3 + 2;

      for (int var7 = 0; var7 < var3; var7++) {
         var0.log(0, var7, 0, Axis.Y);
      }

      for (int var25 = var4 + var1.nextInt(2); var25 < var3 - 1; var25 += 2 + (var1.nextFloat() < 0.3F ? 1 : 0)) {
         double var8 = (double)(var25 - var4) / (double)Math.max(1, var3 - var4);
         double var10 = var5 * (var8 < 0.35 ? 0.72 + 0.8 * var8 : Math.pow((1.0 - var8) / 0.65, 0.55));
         int var12 = 2 + var1.nextInt(2) + (var10 > 1.8 ? 1 : 0);
         double var13 = var1.nextDouble() * (float) (Math.PI * 2);

         for (int var15 = 0; var15 < var12; var15++) {
            double var16 = var13 + (double)((float)var15 * (float) (Math.PI * 2) / (float)var12) + (var1.nextDouble() - 0.5) * 0.8;
            double var18 = var10 * (0.75 + 0.35 * var1.nextDouble());
            double var20 = Math.cos(var16) * var18;
            double var22 = Math.sin(var16) * var18;
            int var24 = var18 > 1.6 && var1.nextFloat() < 0.5F ? 1 : 0;
            if (var18 > 1.5) {
               var0.limb(Math.cos(var16), (double)var25, Math.sin(var16), var20 * 0.8, (double)(var25 + var24), var22 * 0.8);
            }

            var0.blob(
               var20,
               (double)(var25 + var24) + 0.3,
               var22,
               1.25 + var1.nextDouble() * 0.35,
               0.85 + var1.nextDouble() * 0.3,
               1.25 + var1.nextDouble() * 0.35,
               0.45,
               var1
            );
         }
      }

      var0.blob(0.0, (double)var3, 0.0, 1.2, 1.6, 1.2, 0.3, var1);
      var0.leaf(0, var3 + 1, 0);
   }

   private static void aspen(AlpineTrees.Growth var0, RandomSource var1, float var2) {
      int var3 = Math.round(Mth.lerp(var2, 11.0F, 19.0F)) + var1.nextInt(3);
      int var4 = Math.round((float)var3 * Mth.lerp(var2, 0.46F, 0.56F));
      double var5 = (double)Mth.lerp(var2, 1.8F, 2.7F) + (double)var1.nextFloat() * 0.3;
      var0.height = var3 + 2;

      for (int var7 = 0; var7 < var3 - 1; var7++) {
         var0.log(0, var7, 0, Axis.Y);
      }

      double var19 = (double)(var4 + var3) / 2.0;
      var0.blob(0.0, var19 + 0.5, 0.0, var5 * 0.85, (double)(var3 - var4) / 2.0 + 0.8, var5 * 0.85, 0.35, var1);
      int var9 = 2 + var1.nextInt(2);
      double var10 = var1.nextDouble() * (float) (Math.PI * 2);

      for (int var12 = 0; var12 < var9; var12++) {
         double var13 = var10 + (double)((float)var12 * (float) (Math.PI * 2) / (float)var9) + (var1.nextDouble() - 0.5);
         double var15 = var5 * 0.55;
         double var17 = Mth.lerp(var1.nextDouble(), (double)(var4 + 1), (double)(var3 - 1));
         var0.limb(0.0, var17 - 2.0, 0.0, Math.cos(var13) * var15, var17, Math.sin(var13) * var15);
         var0.blob(Math.cos(var13) * var15, var17 + 0.4, Math.sin(var13) * var15, var5 * 0.62, var5 * 0.62, var5 * 0.62, 0.5, var1);
      }
   }

   private static void birch(AlpineTrees.Growth var0, RandomSource var1, float var2) {
      int var3 = var2 > 0.3F && var1.nextFloat() < 0.45F ? (var1.nextFloat() < 0.3F ? 3 : 2) : 1;
      double var4 = var1.nextDouble() * (float) (Math.PI * 2);

      for (int var6 = 0; var6 < var3; var6++) {
         double var7 = var4 + (double)((float)var6 * (float) (Math.PI * 2) / (float)var3);
         int var9 = var6 == 0 ? 0 : (int)Math.round(Math.cos(var7));
         int var10 = var6 == 0 ? 0 : (int)Math.round(Math.sin(var7));
         int var11 = Math.round(Mth.lerp(var2, 10.0F, 17.0F)) + var1.nextInt(3) - (var6 > 0 ? 2 : 0);
         int var12 = var3 <= 1 && !(var1.nextFloat() < 0.4F) ? var11 : var11 / 2;
         int var13 = var3 > 1 ? (int)Math.round(Math.cos(var7)) : 0;
         int var14 = var3 > 1 ? (int)Math.round(Math.sin(var7)) : 0;
         if (var6 == 0 && var3 > 1) {
            var13 = -var13;
            var14 = -var14;
         }

         int var15 = var9;
         int var16 = var10;

         for (int var17 = 0; var17 < var11 - 1; var17++) {
            if (var17 == var12) {
               var15 += var13;
               var16 += var14;
            }

            var0.log(var15, var17, var16, Axis.Y);
         }

         int var28 = Math.round((float)var11 * 0.5F);
         double var18 = (double)Mth.lerp(var2, 1.5F, 2.2F) + (double)var1.nextFloat() * 0.3;
         var0.blob((double)var15, (double)(var28 + var11) / 2.0 + 0.3, (double)var16, var18, (double)(var11 - var28) / 2.0 + 0.8, var18, 0.45, var1);

         for (int var20 = 0; var20 < 5; var20++) {
            double var21 = var1.nextDouble() * (float) (Math.PI * 2);
            double var23 = var18 * 0.8;
            int var25 = var15 + (int)Math.round(Math.cos(var21) * var23);
            int var26 = var16 + (int)Math.round(Math.sin(var21) * var23);

            for (int var27 = var28; var27 > var28 - 1 - var1.nextInt(2) && var27 > 1; var27--) {
               var0.leaf(var25, var27, var26);
            }
         }

         var0.height = Math.max(var0.height, var11 + 1);
      }
   }

   private static void maple(AlpineTrees.Growth var0, RandomSource var1, float var2) {
      boolean var3 = var2 > 0.55F;
      double var4 = var3 ? 0.5 : 0.0;
      int var6 = 3 + var1.nextInt(2) + (var2 > 0.5F ? 1 : 0);
      int var7 = Math.round(Mth.lerp(var2, 10.0F, 18.0F)) + var1.nextInt(2);
      var0.height = var7 + 2;

      for (int var8 = 0; var8 <= var6; var8++) {
         var0.log(0, var8, 0, Axis.Y);
         if (var3) {
            var0.log(1, var8, 0, Axis.Y);
            var0.log(0, var8, 1, Axis.Y);
            var0.log(1, var8, 1, Axis.Y);
         }
      }

      int var40 = 3 + var1.nextInt(2) + (var3 ? 1 : 0);
      double var9 = var1.nextDouble() * (float) (Math.PI * 2);
      double var11 = (double)Mth.lerp(var2, 2.0F, 4.4F);
      double var13 = (double)Mth.lerp(var2, 2.0F, 2.8F);

      for (int var15 = 0; var15 < var40; var15++) {
         double var16 = var9 + (double)((float)var15 * (float) (Math.PI * 2) / (float)var40) + (var1.nextDouble() - 0.5) * 0.6;
         double var18 = var11 * (0.75 + 0.35 * var1.nextDouble());
         double var20 = var4 + Math.cos(var16) * var18 * 0.55;
         double var22 = var4 + Math.sin(var16) * var18 * 0.55;
         double var24 = (double)var6 + (double)(var7 - var6) * 0.45;
         double var26 = var4 + Math.cos(var16) * var18;
         double var28 = var4 + Math.sin(var16) * var18;
         double var30 = (double)var7 - 2.5 - (double)var1.nextInt(3);
         var0.limb(var4, (double)var6, var4, var20, var24, var22);
         var0.limb(var20, var24, var22, var26, var30, var28);
         var0.blob(var26, var30 + 0.8, var28, var13 * (0.9 + 0.25 * var1.nextDouble()), var13 * 0.72, var13 * (0.9 + 0.25 * var1.nextDouble()), 0.45, var1);
         if (var1.nextFloat() < 0.7F) {
            double var32 = var16 + (var1.nextBoolean() ? 0.7 : -0.7);
            double var34 = var20 + Math.cos(var32) * var18 * 0.55;
            double var36 = var22 + Math.sin(var32) * var18 * 0.55;
            double var38 = var24 + 1.0 + (double)var1.nextInt(2);
            var0.limb(var20, var24, var22, var34, var38, var36);
            var0.blob(var34, var38 + 0.6, var36, var13 * 0.75, var13 * 0.6, var13 * 0.75, 0.5, var1);
         }
      }

      var0.blob(var4, (double)var7 - 0.8, var4, var13 * 1.1, var13 * 0.8, var13 * 1.1, 0.4, var1);
      var0.leaves.keySet().removeIf(var1x -> BlockPos.getY(var1x) < var6);
      var0.litter = false;
      var0.broadleaf = true;
   }

   private static void willow(AlpineTrees.Growth var0, RandomSource var1, float var2) {
      int var3 = 2 + var1.nextInt(2);
      double var4 = var1.nextDouble() * (float) (Math.PI * 2);
      int var6 = (int)Math.round(Math.cos(var4));
      int var7 = (int)Math.round(Math.sin(var4));

      for (int var8 = 0; var8 <= var3; var8++) {
         var0.log(var8 >= var3 - 1 ? var6 : 0, var8, var8 >= var3 - 1 ? var7 : 0, Axis.Y);
      }

      int var21 = Math.round(Mth.lerp(var2, 8.0F, 12.0F)) + var1.nextInt(2);
      var0.height = var21 + 2;
      int var9 = 3 + var1.nextInt(2);
      double var10 = (double)Mth.lerp(var2, 2.2F, 3.2F);

      for (int var12 = 0; var12 < var9; var12++) {
         double var13 = var4 + (double)((float)var12 * (float) (Math.PI * 2) / (float)var9) + (var1.nextDouble() - 0.5) * 0.5;
         double var15 = (double)var6 + Math.cos(var13) * var10;
         double var17 = (double)var7 + Math.sin(var13) * var10;
         double var19 = (double)(var21 - 2);
         var0.limb((double)var6, (double)var3, (double)var7, var15, var19, var17);
         var0.blob(var15, var19 + 0.5, var17, 2.4, 1.7, 2.4, 0.35, var1);
      }

      var0.blob((double)var6, (double)var21 - 0.5, (double)var7, 2.3, 1.6, 2.3, 0.3, var1);
      ArrayList var22 = new ArrayList();
      LongBidirectionalIterator var23 = var0.leaves.keySet().iterator();

      while (var23.hasNext()) {
         long var14 = (Long)var23.next();
         int var16 = BlockPos.getX(var14);
         int var27 = BlockPos.getY(var14);
         int var18 = BlockPos.getZ(var14);
         if (!var0.leaves.containsKey(BlockPos.asLong(var16, var27 - 1, var18)) && !var0.logs.containsKey(BlockPos.asLong(var16, var27 - 1, var18))) {
            double var30 = Math.sqrt((double)((var16 - var6) * (var16 - var6) + (var18 - var7) * (var18 - var7)));
            if (var30 > 2.2 && var1.nextFloat() < 0.62F) {
               var22.add(var14);
            }
         }
      }

      for (long var25 : var22) {
         int var26 = BlockPos.getX(var25);
         int var28 = BlockPos.getY(var25);
         int var29 = BlockPos.getZ(var25);
         int var31 = 1 + var1.nextInt(4);

         for (int var20 = 1; var20 <= var31 && var28 - var20 >= 1; var20++) {
            var0.leaf(var26, var28 - var20, var29);
         }
      }
   }

   private static void snag(AlpineTrees.Growth var0, RandomSource var1, float var2) {
      int var3 = Math.round(Mth.lerp(var2, 5.0F, 13.0F)) + var1.nextInt(3);
      var0.height = var3;

      for (int var4 = 0; var4 < var3; var4++) {
         var0.log(0, var4, 0, Axis.Y);
      }

      int var8 = 1 + var1.nextInt(3);

      for (int var5 = 0; var5 < var8; var5++) {
         Direction var6 = Plane.HORIZONTAL.getRandomDirection(var1);
         int var7 = 3 + var1.nextInt(Math.max(1, var3 - 4));
         var0.log(var6.getStepX(), var7, var6.getStepZ(), var6.getAxis());
      }
   }

   private static void deadfall(AlpineTrees.Growth var0, RandomSource var1, float var2) {
      int var3 = 4 + Math.round(var2 * 4.0F) + var1.nextInt(2);
      Axis var4 = var1.nextBoolean() ? Axis.X : Axis.Z;
      int var5 = Math.max(2, Math.round((float)var3 * 0.6F));
      BlockState var6 = (BlockState)block("deadfall_log").setValue(HorizontalDirectionalBlock.FACING, var4 == Axis.X ? Direction.EAST : Direction.NORTH);

      for (int var7 = 0; var7 < var3; var7++) {
         int var8 = var4 == Axis.X ? var7 : 0;
         int var9 = var4 == Axis.Z ? var7 : 0;
         if (var7 < var5) {
            var0.log(var8, 0, var9, var4);
            if (var1.nextFloat() < 0.5F) {
               var0.cover.put(BlockPos.asLong(var8, 1, var9), Blocks.MOSS_CARPET.defaultBlockState());
            }
         } else {
            var0.logs.put(BlockPos.asLong(var8, 0, var9), var6);
         }
      }

      if (var1.nextFloat() < 0.6F) {
         int var10 = 1 + var1.nextInt(Math.max(1, var5 - 1));
         Axis var11 = var4 == Axis.X ? Axis.Z : Axis.X;
         int var12 = var1.nextBoolean() ? 1 : -1;
         var0.log(var4 == Axis.X ? var10 : var12, 0, var4 == Axis.Z ? var10 : var12, var11);
      }

      var0.height = 1;
      var0.radius = var3;
   }

   private static void trim(AlpineTrees.Growth var0) {
      if (!var0.leaves.isEmpty()) {
         Long2IntOpenHashMap var1 = new Long2IntOpenHashMap();
         LongArrayFIFOQueue var2 = new LongArrayFIFOQueue();
         LongBidirectionalIterator var3 = var0.logs.keySet().iterator();

         while (var3.hasNext()) {
            long var4 = (Long)var3.next();

            for (Direction var9 : Direction.values()) {
               long var10 = BlockPos.offset(var4, var9);
               if (var0.leaves.containsKey(var10) && !var1.containsKey(var10)) {
                  var1.put(var10, 1);
                  var2.enqueue(var10);
               }
            }
         }

         while (!var2.isEmpty()) {
            long var12 = var2.dequeueLong();
            int var5 = var1.get(var12) + 1;
            if (var5 <= 6) {
               for (Direction var18 : Direction.values()) {
                  long var19 = BlockPos.offset(var12, var18);
                  if (var0.leaves.containsKey(var19) && !var1.containsKey(var19)) {
                     var1.put(var19, var5);
                     var2.enqueue(var19);
                  }
               }
            }
         }

         ObjectBidirectionalIterator var13 = var0.leaves.long2ObjectEntrySet().fastIterator();

         while (var13.hasNext()) {
            Entry var14 = (Entry)var13.next();
            if (!var1.containsKey(var14.getLongKey())) {
               var13.remove();
            } else {
               var14.setValue((BlockState)((BlockState)var14.getValue()).setValue(LeavesBlock.DISTANCE, var1.get(var14.getLongKey())));
            }
         }
      }
   }

   private AlpineTrees() {
   }

   static final class Growth {
      final Long2ObjectLinkedOpenHashMap<BlockState> logs = new Long2ObjectLinkedOpenHashMap();
      final Long2ObjectLinkedOpenHashMap<BlockState> leaves = new Long2ObjectLinkedOpenHashMap();
      final Long2ObjectLinkedOpenHashMap<BlockState> cover = new Long2ObjectLinkedOpenHashMap();
      final AlpineTrees.Species species;
      BlockState wood;
      BlockState leaf;
      int height;
      int radius;
      boolean litter;
      boolean broadleaf;

      Growth(AlpineTrees.Species var1) {
         this.species = var1;
      }

      void log(int var1, int var2, int var3, Axis var4) {
         long var5 = BlockPos.asLong(var1, var2, var3);
         this.logs.put(var5, (BlockState)this.wood.setValue(RotatedPillarBlock.AXIS, var4));
         this.leaves.remove(var5);
      }

      void leaf(int var1, int var2, int var3) {
         long var4 = BlockPos.asLong(var1, var2, var3);
         if (!this.logs.containsKey(var4)) {
            this.leaves.putIfAbsent(var4, this.leaf);
         }

         this.radius = Math.max(this.radius, Math.max(Math.abs(var1), Math.abs(var3)));
      }

      void blob(double var1, double var3, double var5, double var7, double var9, double var11, double var13, RandomSource var15) {
         int var16 = (int)Math.floor(var1 - var7);
         int var17 = (int)Math.ceil(var1 + var7);
         int var18 = (int)Math.floor(var3 - var9);
         int var19 = (int)Math.ceil(var3 + var9);
         int var20 = (int)Math.floor(var5 - var11);
         int var21 = (int)Math.ceil(var5 + var11);

         for (int var22 = var16; var22 <= var17; var22++) {
            for (int var23 = var18; var23 <= var19; var23++) {
               for (int var24 = var20; var24 <= var21; var24++) {
                  double var25 = ((double)var22 - var1) / var7;
                  double var27 = ((double)var23 - var3) / var9;
                  double var29 = ((double)var24 - var5) / var11;
                  double var31 = var25 * var25 + var27 * var27 + var29 * var29;
                  if (!(var31 > 1.08) && (!(var31 > 0.55) || !(var15.nextDouble() < var13 * (var31 - 0.55) * 2.2)) && var23 >= 0) {
                     this.leaf(var22, var23, var24);
                  }
               }
            }
         }
      }

      void limb(double var1, double var3, double var5, double var7, double var9, double var11) {
         double var13 = var7 - var1;
         double var15 = var9 - var3;
         double var17 = var11 - var5;
         double var19 = Math.sqrt(var13 * var13 + var15 * var15 + var17 * var17);
         Axis var21 = Math.abs(var15) >= Math.max(Math.abs(var13), Math.abs(var17)) * 0.9 ? Axis.Y : (Math.abs(var13) > Math.abs(var17) ? Axis.X : Axis.Z);
         int var22 = Math.max(1, (int)Math.ceil(var19 * 1.5));

         for (int var23 = 0; var23 <= var22; var23++) {
            double var24 = (double)var23 / (double)var22;
            this.log((int)Math.round(var1 + var13 * var24), (int)Math.round(var3 + var15 * var24), (int)Math.round(var5 + var17 * var24), var21);
         }
      }
   }

   private static final class Lobes {
      final double a1;
      final double a2;
      final double p1;
      final double p2;
      final int k1;
      final int k2;

      Lobes(RandomSource var1, double var2) {
         this.k1 = 3 + var1.nextInt(3);
         this.k2 = 6 + var1.nextInt(3);
         this.a1 = var2 * (0.5 + 0.5 * var1.nextDouble());
         this.a2 = var2 * 0.45 * var1.nextDouble();
         this.p1 = var1.nextDouble() * (float) (Math.PI * 2);
         this.p2 = var1.nextDouble() * (float) (Math.PI * 2);
      }

      double at(double var1) {
         return 1.0 + this.a1 * Math.sin((double)this.k1 * var1 + this.p1) + this.a2 * Math.sin((double)this.k2 * var1 + this.p2);
      }
   }

   static enum Species {
      SPRUCE,
      FIR,
      PINE,
      ASPEN,
      BIRCH,
      MAPLE,
      WILLOW,
      YOUNG_SPRUCE,
      YOUNG_FIR,
      SNAG,
      DEADFALL,
      GOLDEN_ASPEN,
      AUTUMN_MAPLE,
      BLUE_SPRUCE,
      LARCH,
      ALDER,
      ROWAN,
      COTTONWOOD,
      DEADFALL_PINE;
   }
}
