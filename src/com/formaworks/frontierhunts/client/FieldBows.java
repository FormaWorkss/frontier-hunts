package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.Weapon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

final class FieldBows {
   private static final double[][] GRIP = new double[][]{
      {-0.132, -0.011}, {-0.1, 0.021}, {-0.026, 0.027}, {0.026, 0.008}, {0.046, -0.018}, {0.006, -0.041}, {-0.113, -0.04}
   };

   static double nockZ(float var0) {
      return 0.092 + (double)var0 * 0.62;
   }

   static double cut(float var0) {
      return var0 <= 0.0F ? 9.0 : Mth.lerp((double)var0, 9.0, 0.32);
   }

   static void draw(Weapon var0, PoseStack var1, VertexConsumer var2, int var3, float var4) {
      draw(var0, var1, var2, var3, var4, 0.0F);
   }

   static void draw(Weapon var0, PoseStack var1, VertexConsumer var2, int var3, float var4, float var5) {
      if (var0 == Weapon.CROSSBOW) {
         crossbow(var1, var2, var3, var4);
      } else {
         double var6 = cut(var5);
         boolean var8 = var0 == Weapon.COMPOUND_BOW || var0 == Weapon.BOWFISHING_BOW;
         VertexConsumer var9 = FieldMaterials.tile(var2, 0);
         VertexConsumer var10 = FieldMaterials.tile(var2, 1);
         VertexConsumer var11 = FieldMaterials.tile(var2, 2);
         VertexConsumer var12 = FieldMaterials.flat(var2);
         ArtMesh.profile(var1, var8 ? var9 : var10, var3, 16777215, 0.023, 0.004, GRIP);

         for (int var16 : new int[]{-1, 1}) {
            var1.pushPose();
            var1.translate((double)var16 * 0.024, 0.0, 0.0);
            ArtMesh.profile(var1, var11, var3, 11187102, 0.0035, 0.001, GRIP);
            var1.popPose();

            for (int var17 = 0; var17 < 7; var17++) {
               ArtMesh.box(var1, var12, var3, 3753269, (double)var16 * 0.0277, -0.09 + (double)var17 * 0.014, 0.012, 0.001, 0.002, 0.016);
            }
         }

         for (int var46 : new int[]{-1, 1}) {
            double var48 = var8 ? (var46 > 0 ? 0.335 : -0.325) : (var46 > 0 ? 0.2 : -0.21);
            if (var8) {
               ArtMesh.sweep(
                  var1,
                  var9,
                  var3,
                  12042161,
                  var46 > 0
                     ? new double[][]{
                        {-0.012, 0.034, -0.052, 0.024, 0.031},
                        {-0.056, 0.098, -0.061, 0.022, 0.035},
                        {-0.078, 0.215, -0.057, 0.02, 0.033},
                        {-0.038, var48 - 0.016, -0.05, 0.026, 0.031}
                     }
                     : new double[][]{
                        {-0.012, -0.098, -0.052, 0.024, 0.031}, {-0.048, -0.168, -0.059, 0.022, 0.033}, {-0.03, var48 + 0.016, -0.05, 0.026, 0.031}
                     },
                  16
               );
               ArtMesh.box(var1, var9, var3, 12173496, -0.015, var48, -0.055, 0.052, 0.046, 0.096);

               for (int var22 : new int[]{-1, 1}) {
                  bolt(var1, var12, var3, -0.015 + (double)var22 * 0.028, var48, -0.056);
               }
            } else {
               ArtMesh.sweep(
                  var1,
                  var10,
                  var3,
                  16777215,
                  var46 > 0
                     ? new double[][]{{-0.012, 0.022, -0.028, 0.021, 0.018}, {-0.044, 0.086, -0.05, 0.019, 0.019}, {-0.022, var48, -0.075, 0.023, 0.014}}
                     : new double[][]{{-0.012, -0.1, -0.025, 0.021, 0.018}, {-0.011, var48 * 0.72, -0.064, 0.024, 0.018}, {0.0, var48, -0.075, 0.025, 0.013}},
                  16
               );
               ArtMesh.box(var1, var12, var3, 4274988, 0.0, var48, -0.063, 0.033, 0.023, 0.02);
            }

            double var50 = (double)var46 * (var8 ? 0.6 : 0.71) - (double)((float)var46 * var4) * (var8 ? 0.04 : 0.068);
            double var52 = (var8 ? -0.105 : 0.03) + (double)var4 * (var8 ? 0.16 : 0.22);
            double var23 = var8 ? var48 + (var50 - var48) * 0.4 : (double)var46 * 0.34;
            double var25 = var8 ? var48 + (var50 - var48) * 0.72 : (double)var46 * 0.5;
            double var27 = var8 ? -0.118 + (double)var4 * 0.055 : -0.13 + (double)var4 * 0.025;
            double var29 = var8 ? -0.128 + (double)var4 * 0.115 : -0.14 + (double)var4 * 0.11;

            for (int var34 : var8 ? new int[]{-1, 1} : new int[]{0}) {
               double var35 = (double)var34 * 0.017;
               ArtMesh.sweep(
                  var1,
                  var8 ? var11 : var10,
                  var3,
                  16777215,
                  new double[][]{
                     {var35, var48, -0.08, var8 ? 0.01 : 0.024, 0.007},
                     {var35, var23, var27, var8 ? 0.01 : 0.02, 0.006},
                     {var35, var25, var29, var8 ? 0.009 : 0.014, 0.005},
                     {var35, var50, var52, var8 ? 0.008 : 0.007, 0.004}
                  },
                  14
               );
               ArtMesh.sweep(
                  var1,
                  var12,
                  var3,
                  var8 ? 7370854 : 11704935,
                  new double[][]{
                     {var35, var48, -0.088, var8 ? 0.008 : 0.021, 7.0E-4},
                     {var35, var23, var27 - 0.006, var8 ? 0.008 : 0.018, 7.0E-4},
                     {var35, var25, var29 - 0.005, var8 ? 0.007 : 0.012, 7.0E-4},
                     {var35, var50, var52 - 0.004, var8 ? 0.006 : 0.005, 5.0E-4}
                  },
                  12
               );
            }

            if (var8) {
               cam(var1, var9, var12, var3, var50, var52, (double)(var4 * (float)var46));
               HuntMesh.tube(
                  var1, var12, var3, 8687994, -0.018, var50, var52 - 0.034, -0.062, (double)(-var46) * 0.3, -0.052 + (double)var4 * 0.06, 0.0011, 0.0011, 6
               );
            }

            strand(var1, var12, var3, 12894631, 0.0, var50, var52 + (var8 ? 0.05 : 0.0), 0.015, 0.05, nockZ(var4), 0.00125, var6, 7);
            if (!var8) {
               HuntMesh.tube(var1, var12, var3, 2698791, -0.01, var50, var52, 0.01, var50, var52, 0.006, 0.006, 10);
            }
         }

         HuntMesh.tube(var1, var9, var3, 8753030, -0.02, 0.026, -0.018, 0.026, 0.026, -0.018, 0.003, 0.003, 10);

         for (int var47 : new int[]{-1, 1}) {
            HuntMesh.tube(
               var1, var12, var3, 4543038, 0.015 + (double)var47 * 0.011, 0.028, -0.018, 0.015 + (double)var47 * 0.004, 0.048, -0.018, 0.002, 0.002, 8
            );
         }

         bolt(var1, var12, var3, -0.026, 0.026, -0.018);
         if (var8) {
            HuntMesh.tube(var1, var9, var3, 11121055, 0.0, -0.16, -0.094, 0.0, -0.16, -0.33, 0.011, 0.011, 18);
            HuntMesh.tube(var1, var11, var3, 12634292, 0.0, -0.16, -0.3, 0.0, -0.16, -0.35, 0.02, 0.019, 20);

            for (int var39 = 0; var39 < 4; var39++) {
               HuntMesh.tube(
                  var1, var12, var3, 7107684, 0.0, -0.16, -0.308 - (double)var39 * 0.009, 0.0, -0.16, -0.31 - (double)var39 * 0.009, 0.0205, 0.0205, 16
               );
            }

            // [bows] sight: in first person the housing and every pin sit where BowSight calibrated them (pins match the
            // arrow's real flight at the configured distances); anywhere else the bow shows a plain three-pin sight
            BowSight.Pins sight = BowSight.modelPins();
            double var40 = sight == null ? 0.015 : sight.x();
            double var45 = sight == null ? 0.119 : sight.y();
            double var49 = -0.205;
            double var51 = -0.05;
            double ringR = sight == null ? 0.03 : sight.r();
            ArtMesh.box(var1, var9, var3, 11055266, -0.062, var45, -0.066, 0.038, 0.03, 0.024);
            ArtMesh.box(var1, var9, var3, 10660508, -0.046, var45, -0.062, 0.034, 0.013, 0.018);
            HuntMesh.tube(var1, var9, var3, 11121055, var51, var45, -0.058, var51, var45, var49 + 0.006, 0.006, 0.0055, 10);
            HuntMesh.tube(var1, var9, var3, 11581608, var51, var45, var49, var40 - ringR, var45, var49, 0.0042, 0.0038, 10);

            for (int var53 = 0; var53 < 24; var53++) {
               double var55 = (double)var53 * Math.PI / 12.0;
               double var24 = (double)(var53 + 1) * Math.PI / 12.0;
               // front lip of the housing, and its deeper rear edge (a short tube, not a wire hoop)
               HuntMesh.tube(var1, var9, var3, 11976111,
                  var40 + Math.cos(var55) * ringR, var45 + Math.sin(var55) * ringR, var49,
                  var40 + Math.cos(var24) * ringR, var45 + Math.sin(var24) * ringR, var49, 0.0022, 0.0022, 6);
               HuntMesh.tube(var1, var12, var3, 3355443,
                  var40 + Math.cos(var55) * (ringR + 0.0012), var45 + Math.sin(var55) * (ringR + 0.0012), var49 + 0.013,
                  var40 + Math.cos(var24) * (ringR + 0.0012), var45 + Math.sin(var24) * (ringR + 0.0012), var49 + 0.013, 0.0016, 0.0016, 5);
            }

            double[] pinY = sight == null ? new double[]{var45 + 0.011, var45, var45 - 0.011} : sight.pins();
            int[] pinColor = sight == null ? new int[]{6476910, 14999626, 14704700} : sight.colors();
            for (int var56 = 0; var56 < pinY.length; var56++) {
               double var58 = pinY[var56];
               double reach = Math.sqrt(Math.max(0.0, ringR * ringR - (var58 - var45) * (var58 - var45)));
               // horizontal pin arm from the right of the housing, fibre tip glowing at the aiming point
               HuntMesh.tube(var1, var12, var3, 7173992, var40 + reach, var58, var49, var40 + 0.0026, var58, var49, 0.0012, 0.0009, 5);
               HuntMesh.tube(var1, var12, 15728880, pinColor[var56 % pinColor.length], var40, var58, var49 - 0.0018, var40, var58, var49 + 0.0018, 0.0024, 0.0024, 10);
            }

            ArtMesh.box(var1, var12, var3, 2832947, var40, var45 - ringR - 0.006, var49, 0.013, 0.005, 0.004);
            if ((double)var4 > 0.45 && nockZ(var4) - 0.012 < var6) {
               double var57 = nockZ(var4) - 0.012;

               for (int var59 = 0; var59 < 12; var59++) {
                  double var60 = (double)var59 * Math.PI / 6.0;
                  double var61 = (double)(var59 + 1) * Math.PI / 6.0;
                  HuntMesh.tube(
                     var1,
                     var12,
                     var3,
                     2106396,
                     var40 + Math.cos(var60) * 0.0095,
                     var45 + Math.sin(var60) * 0.0095,
                     var57,
                     var40 + Math.cos(var61) * 0.0095,
                     var45 + Math.sin(var61) * 0.0095,
                     var57,
                     0.0021,
                     0.0021,
                     5
                  );
               }
            }
         }
      }
   }

   private static void strand(
      PoseStack var0,
      VertexConsumer var1,
      int var2,
      int var3,
      double var4,
      double var6,
      double var8,
      double var10,
      double var12,
      double var14,
      double var16,
      double var18,
      int var20
   ) {
      if (!(var8 >= var18)) {
         if (var14 > var18) {
            double var21 = (var18 - var8) / (var14 - var8);
            HuntMesh.tube(var0, var1, var2, var3, var4, var6, var8, var4 + (var10 - var4) * var21, var6 + (var12 - var6) * var21, var18, var16, 0.0, var20);
         } else {
            HuntMesh.tube(var0, var1, var2, var3, var4, var6, var8, var10, var12, var14, var16, var16, var20);
         }
      }
   }

   private static void cam(PoseStack var0, VertexConsumer var1, VertexConsumer var2, int var3, double var4, double var6, double var8) {
      var0.pushPose();
      var0.translate(0.0, var4, var6);
      var0.mulPose(Axis.XP.rotation((float)(var8 * 1.45)));

      for (int var13 : new int[]{-1, 1}) {
         var0.pushPose();
         var0.translate((double)var13 * 0.007, 0.0, 0.0);
         FieldEquipmentModel.loop(var0, var1, var3, 11713197, 0.0, 0.0, 0.0, 0.052, 0.066);
         var0.popPose();
      }

      for (int var14 = 0; var14 < 5; var14++) {
         double var16 = (double)var14 * Math.PI * 2.0 / 5.0;
         HuntMesh.tube(var0, var1, var3, 11252895, 0.0, 0.0, 0.0, 0.0, Math.sin(var16) * 0.049, Math.cos(var16) * 0.061, 0.005, 0.004, 8);
      }

      HuntMesh.tube(var0, var2, var3, 11450286, -0.032, 0.0, 0.0, 0.032, 0.0, 0.0, 0.007, 0.007, 14);

      for (int var19 : new int[]{-1, 1}) {
         bolt(var0, var2, var3, (double)var19 * 0.033, 0.0, 0.0);
      }

      var0.popPose();
   }

   private static void bolt(PoseStack var0, VertexConsumer var1, int var2, double var3, double var5, double var7) {
      HuntMesh.tube(var0, var1, var2, 11384755, var3, var5, var7, var3 + Math.copySign(0.0015, var3), var5, var7, 0.004, 0.004, 12);
      ArtMesh.box(var0, var1, var2, 2503472, var3 + Math.copySign(0.0017, var3), var5, var7, 5.0E-4, 0.001, 0.004);
   }

   private static void crossbow(PoseStack var0, VertexConsumer var1, int var2, float var3) {
      VertexConsumer var4 = FieldMaterials.tile(var1, 0);
      VertexConsumer var5 = FieldMaterials.tile(var1, 2);
      VertexConsumer var6 = FieldMaterials.flat(var1);
      VertexConsumer var7 = FieldMaterials.tile(var1, 1);
      ArtMesh.box(var0, var4, var2, 13685960, 0.0, -0.013, -0.042, 0.072, 0.054, 0.63);

      for (int var11 : new int[]{-1, 1}) {
         ArtMesh.box(var0, var6, var2, 10267039, (double)var11 * 0.019, 0.019, -0.054, 0.014, 0.008, 0.6);
      }

      ArtMesh.profile(
         var0, var7, var2, 16777215, 0.036, 0.005, new double[][]{{-0.029, 0.2}, {-0.025, 0.45}, {-0.09, 0.51}, {-0.17, 0.49}, {-0.13, 0.33}, {-0.1, 0.18}}
      );
      ArtMesh.box(var0, var5, var2, 16777215, 0.0, -0.11, 0.493, 0.078, 0.135, 0.025);
      ArtMesh.profile(
         var0, var5, var2, 16777215, 0.027, 0.004, new double[][]{{-0.025, 0.14}, {-0.065, 0.125}, {-0.172, 0.21}, {-0.173, 0.27}, {-0.079, 0.225}}
      );
      ArtMesh.profile(
         var0, var5, var2, 16777215, 0.035, 0.004, new double[][]{{-0.025, -0.27}, {-0.074, -0.23}, {-0.095, -0.07}, {-0.05, 0.01}, {-0.025, -0.01}}
      );
      FieldEquipmentModel.loop(var0, var4, var2, 11647147, 0.0, -0.055, 0.076, 0.04, 0.065);
      HuntMesh.tube(var0, var4, var2, 13685707, 0.0, -0.028, 0.09, 0.0, -0.063, 0.1, 0.003, 0.003, 10);

      for (int var22 : new int[]{-1, 1}) {
         ArtMesh.box(var0, var4, var2, 12043185, (double)var22 * 0.048, 0.0, -0.3, 0.06, 0.05, 0.097);

         for (int var15 : new int[]{-1, 1}) {
            double var16 = (double)var15 * 0.014;
            HuntMesh.tube(
               var0, var5, var2, 16777215, (double)var22 * 0.055, var16, -0.32, (double)var22 * 0.18, var16, -0.345 + (double)var3 * 0.03, 0.012, 0.01, 14
            );
            HuntMesh.tube(
               var0,
               var5,
               var2,
               16777215,
               (double)var22 * 0.18,
               var16,
               -0.345 + (double)var3 * 0.03,
               (double)var22 * 0.34,
               var16,
               -0.19 + (double)var3 * 0.1,
               0.01,
               0.008,
               14
            );
         }

         double var23 = (double)var22 * 0.34;
         double var24 = -0.19 + (double)var3 * 0.1;
         var0.pushPose();
         var0.translate(var23, 0.0, var24);
         var0.mulPose(Axis.ZP.rotationDegrees(90.0F));
         cam(var0, var4, var6, var2, 0.0, 0.0, (double)((float)var22 * var3));
         var0.popPose();
         HuntMesh.tube(
            var0, var6, var2, 12695960, var23, FieldBowPresentation.XB_BOLT_Y, var24 + 0.06, 0.0, FieldBowPresentation.XB_BOLT_Y,
            FieldBowPresentation.crossbowLatchZ(var3), 0.0014, 0.0014, 7
         ); // [archery2] string rides the rail to the latch
      }

      // [archery2] the cocking stirrup hangs below the rail (it used to stand up into the sight line)
      FieldEquipmentModel.loop(var0, var4, var2, 11647147, 0.0, -0.052, -0.415, 0.033, 0.07);
      reflexSight(var0, var4, var5, var6, var2);
   }

   /**
    * [archery2] Crossbow reflex sight on a bridge mount over the flight groove (the bolt and string pass underneath): a
    * short hooded window whose centre is the optic axis. In first person its size and height come from
    * {@link BowSight#crossbowSight()} so the window frames the range dots exactly; elsewhere a plain 20 mm window.
    */
   private static void reflexSight(PoseStack var0, VertexConsumer steel, VertexConsumer rubber, VertexConsumer flat, int light) {
      double y = FieldBowPresentation.XB_SIGHT_Y;
      double z = FieldBowPresentation.XB_SIGHT_Z;
      double[] sight = BowSight.crossbowSight();
      double r = sight == null ? 0.02 : sight[1];
      // bridge: two posts from the rail sides and a plate over the groove
      for (int side : new int[]{-1, 1}) {
         ArtMesh.box(var0, steel, light, 7368816, side * 0.022, 0.026, z, 0.008, 0.026, 0.05);
      }
      ArtMesh.box(var0, steel, light, 6316128, 0.0, 0.0405, z, 0.052, 0.007, 0.054);
      for (int i = 0; i < 3; i++) {
         ArtMesh.box(var0, flat, light, 2763306, 0.0, 0.0445, z - 0.018 + i * 0.018, 0.05, 0.0015, 0.006);
      }
      // body: base under the window, hood ring (front lip thicker, rear lip thin), a turret on the right
      double base = y - r - 0.0015;
      ArtMesh.box(var0, steel, light, 4210752, 0.0, 0.044 + (base - 0.044) * 0.5, z + 0.002, 0.03, Math.max(0.004, base - 0.044), 0.04);
      int n = 32;
      for (int i = 0; i < n; i++) {
         double a0 = i * Math.PI * 2.0 / n;
         double a1 = (i + 1) * Math.PI * 2.0 / n;
         double c0 = Math.cos(a0);
         double s0 = Math.sin(a0);
         double c1 = Math.cos(a1);
         double s1 = Math.sin(a1);
         HuntMesh.tube(var0, steel, light, 3684408, c0 * (r + 0.002), y + s0 * (r + 0.002), z - 0.016, c1 * (r + 0.002), y + s1 * (r + 0.002), z - 0.016, 0.0026, 0.0026, 6);
         HuntMesh.tube(var0, rubber, light, 2631720, c0 * (r + 0.0016), y + s0 * (r + 0.0016), z + 0.014, c1 * (r + 0.0016), y + s1 * (r + 0.0016), z + 0.014, 0.0014, 0.0014, 5);
         if (i % 4 == 0) {
            HuntMesh.tube(var0, steel, light, 3355443, c0 * (r + 0.0028), y + s0 * (r + 0.0028), z - 0.016, c0 * (r + 0.0022), y + s0 * (r + 0.0022), z + 0.014, 0.0013, 0.0013, 4);
         }
      }
      HuntMesh.tube(var0, steel, light, 4473924, r + 0.002, y - r * 0.35, z, r + 0.011, y - r * 0.35, z, 0.0062, 0.0062, 12);
      HuntMesh.tube(var0, flat, light, 2236962, r + 0.011, y - r * 0.35, z, r + 0.0125, y - r * 0.35, z, 0.0058, 0.0058, 12);
   }

   private FieldBows() {
   }
}
