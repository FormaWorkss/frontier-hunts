package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.FieldElectronics;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;

final class FishFinderHud {
   private static final double RANGE = 24.0;

   static void draw(GuiGraphics var0, Minecraft var1, int var2, int var3) {
      LocalPlayer var4 = var1.player;
      Item var5 = ExpeditionContent.item("fish_finder");
      ItemStack var6 = var4.getMainHandItem().is(var5) ? var4.getMainHandItem() : (var4.getOffhandItem().is(var5) ? var4.getOffhandItem() : null);
      if (var6 != null) {
         byte var7 = 46;
         int var8 = 12 + var7;
         int var9 = var3 - 64 - var7;
         var0.fill(var8 - var7 - 6, var9 - var7 - 6, var8 + var7 + 6, var9 + var7 + 20, -804251371);
         if (!FieldElectronics.available(var6)) {
            var0.drawCenteredString(var1.font, "SONAR · BATTERY EMPTY", var8, var9 - 4, -4162974);
         } else {
            for (int var10 = -var7; var10 <= var7; var10++) {
               int var11 = (int)Math.sqrt((double)(var7 * var7 - var10 * var10));
               var0.fill(var8 - var11, var9 + var10, var8 + var11, var9 + var10 + 1, -15979996);
            }

            for (int var42 = 1; var42 <= 3; var42++) {
               circle(var0, var8, var9, (float)(var7 * var42) / 3.0F, 1615377306);
            }

            var0.fill(var8, var9 - var7, var8 + 1, var9 + var7, 810070938);
            var0.fill(var8 - var7, var9, var8 + var7, var9 + 1, 810070938);
            double var43 = (double)((float)var1.level.getGameTime() + var1.getTimer().getGameTimeDeltaPartialTick(false)) * 0.11;

            for (int var12 = 0; var12 < var7; var12++) {
               int var13 = var8 + (int)Math.round(Math.sin(var43) * (double)var12);
               int var14 = var9 - (int)Math.round(Math.cos(var43) * (double)var12);
               var0.fill(var13, var14, var13 + 1, var14 + 1, -1872699200);
            }

            int var44 = 0;
            double var45 = 1.0E9;
            double var15 = 0.0;
            double var17 = Math.toRadians((double)var4.getYRot());

            for (Entity var20 : var1.level.entitiesForRendering()) {
               if (var20 instanceof AbstractFish || var20 instanceof WaterAnimal) {
                  double var21 = var20.getX() - var4.getX();
                  double var23 = var20.getZ() - var4.getZ();
                  double var25 = Math.hypot(var21, var23);
                  if (!(var25 > 24.0)) {
                     var44++;
                     double var27 = depthBelowSurface(var1, var20.blockPosition(), var20.getY());
                     if (var25 < var45) {
                        var45 = var25;
                        var15 = var27;
                     }

                     double var29 = -Math.sin(var17);
                     double var31 = Math.cos(var17);
                     double var33 = var21 * var29 + var23 * var31;
                     double var35 = var21 * -var31 + var23 * var29;
                     int var37 = var8 + (int)Math.round(-var35 / 24.0 * (double)var7);
                     int var38 = var9 - (int)Math.round(var33 / 24.0 * (double)var7);
                     int var39 = (double)var20.getBbWidth() > 0.6 ? 3 : 2;
                     int var40 = (int)Math.max(90.0, 255.0 - var27 * 28.0);
                     int var41 = 0xFF000000 | var40 / 3 << 16 | var40 << 8 | Math.min(255, var40 + 40);
                     var0.fill(var37 - var39 + 1, var38 - var39 + 1, var37 + var39, var38 + var39, var41);
                  }
               }
            }

            var0.fill(var8 - 1, var9 - 1, var8 + 2, var9 + 2, -1514801);
            String var46 = var44 == 0 ? "SONAR · no fish in range" : "SONAR · " + var44 + (var44 == 1 ? " fish" : " fish");
            String var47 = var44 == 0 ? "" : String.format(Locale.ROOT, "near %.0f m · %.1f m deep", var45, var15);
            var0.drawString(var1.font, var46, var8 - var7 - 2, var9 + var7 + 2, -6364984, false);
            BlockHitResult var48 = var1.level
               .clip(new ClipContext(var4.getEyePosition(), var4.getEyePosition().add(var4.getLookAngle().scale(32.0)), Block.COLLIDER, Fluid.WATER, var4));
            if (var48.getType() == Type.BLOCK && var1.level.getFluidState(var48.getBlockPos()).is(FluidTags.WATER)) {
               int var22 = 0;
               BlockPos var49 = var48.getBlockPos();

               while (var22 < 40 && var1.level.getFluidState(var49.below(var22)).is(FluidTags.WATER)) {
                  var22++;
               }

               var47 = (var47.isEmpty() ? "" : var47 + " · ") + "bottom " + var22 + " m";
            }

            if (!var47.isEmpty()) {
               var0.drawString(var1.font, var47, var8 - var7 - 2, var9 + var7 + 11, -8406874, false);
            }
         }
      }
   }

   private static double depthBelowSurface(Minecraft var0, BlockPos var1, double var2) {
      BlockPos var4 = var1;

      for (int var5 = 0; var5 < 24 && var0.level.getFluidState(var4.above()).is(FluidTags.WATER); var5++) {
         var4 = var4.above();
      }

      return Math.max(0.0, (double)(var4.getY() + 1) - var2);
   }

   private static void circle(GuiGraphics var0, int var1, int var2, float var3, int var4) {
      int var5 = (int)(var3 * 6.3F);

      for (int var6 = 0; var6 < var5; var6++) {
         double var7 = (double)var6 * Math.PI * 2.0 / (double)var5;
         int var9 = var1 + (int)Math.round(Math.cos(var7) * (double)var3);
         int var10 = var2 + (int)Math.round(Math.sin(var7) * (double)var3);
         var0.fill(var9, var10, var9 + 1, var10 + 1, var4);
      }
   }

   private FishFinderHud() {
   }
}
