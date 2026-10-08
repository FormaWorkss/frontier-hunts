package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntContent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.joml.Vector3f;

public final class HarvestItemRenderer extends BlockEntityWithoutLevelRenderer {
   private static final HarvestItemRenderer.V[] MEAT = meat();
   private static final HarvestItemRenderer.V[] HIDE = hide();
   private static final HarvestItemRenderer.V[] LOIN = loin(false);
   private static final HarvestItemRenderer.V[] SILVERSKIN = loin(true);
   private static final HarvestItemRenderer.V[] QUARTER = quarter(false);
   private static final HarvestItemRenderer.V[] BONE = quarter(true);
   private static HarvestItemRenderer instance;
   private static final ResourceLocation MATERIAL = FrontierHunts.id("textures/equipment/harvest/material_v1.png");

   public HarvestItemRenderer() {
      super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
   }

   static int kind(ItemStack var0) {
      if (var0.is((Item)HuntContent.DEER_HIDE.get())) {
         return 2;
      } else if (var0.is((Item)HuntContent.TANNED_HIDE.get())) {
         return 3;
      } else if (var0.is((Item)HuntContent.VENISON_QUARTER.get())) {
         return 4;
      } else if (var0.is((Item)HuntContent.AGED_VENISON.get())) {
         return 5;
      } else if (var0.is((Item)HuntContent.BACKSTRAP.get())) {
         return 6;
      } else if (var0.is((Item)HuntContent.COOKED_BACKSTRAP.get())) {
         return 7;
      } else {
         return var0.is((Item)HuntContent.COOKED_VENISON.get()) ? 1 : 0;
      }
   }

   public void renderByItem(ItemStack var1, ItemDisplayContext var2, PoseStack var3, MultiBufferSource var4, int var5, int var6) {
      var3.pushPose();
      var3.translate(0.5, 0.5, 0.5);
      draw(kind(var1), var3, var4, var5);
      var3.popPose();
   }

   static void draw(boolean var0, boolean var1, PoseStack var2, MultiBufferSource var3, int var4) {
      draw(var0 ? 2 : (var1 ? 1 : 0), var2, var3, var4);
   }

   static void draw(int var0, PoseStack var1, MultiBufferSource var2, int var3) {
      FilteredFieldTexture.ensure(MATERIAL);
      VertexConsumer var4 = var2.getBuffer(RenderType.entityCutoutNoCull(MATERIAL));
      boolean var5 = var0 == 1 || var0 == 7;

      int var6 = switch (var0) {
         case 3 -> 15721414;
         case 4 -> 15776164;
         case 5 -> 11039581;
         case 6 -> 14453367;
         case 7 -> 10315592;
         default -> 16777215;
      };
      var1.pushPose();
      switch (var0) {
         case 2:
         case 3:
            for (HarvestItemRenderer.V var31 : HIDE) {
               HuntMesh.vertex(var4, var1.last(), var3, var6, var31.x, var31.y, var31.z, var31.u, var31.v, var31.nx, var31.ny, var31.nz);
            }
            break;
         case 4:
         case 5:
            var1.mulPose(Axis.ZP.rotationDegrees(var0 == 5 ? -13.0F : -9.0F));
            if (var0 == 5) {
               var1.scale(0.95F, 1.0F, 0.95F);
            }

            for (HarvestItemRenderer.V var29 : QUARTER) {
               HuntMesh.vertex(var4, var1.last(), var3, var6, var29.x, var29.y, var29.z, var29.u, var29.v, var29.nx, var29.ny, var29.nz);
            }

            for (HarvestItemRenderer.V var30 : BONE) {
               HuntMesh.vertex(
                  var4, var1.last(), var3, var0 == 5 ? 14208954 : 15854556, var30.x, var30.y, var30.z, var30.u, var30.v, var30.nx, var30.ny, var30.nz
               );
            }
            break;
         case 6:
         case 7:
            var1.mulPose(Axis.YP.rotationDegrees(24.0F));

            for (HarvestItemRenderer.V var27 : LOIN) {
               float var32 = var27.u + (var5 ? 0.5F : 0.0F);
               HuntMesh.vertex(var4, var1.last(), var3, var6, var27.x, var27.y, var27.z, var32, var27.v, var27.nx, var27.ny, var27.nz);
            }

            for (HarvestItemRenderer.V var28 : SILVERSKIN) {
               float var33 = var28.u + (var5 ? 0.5F : 0.0F);
               HuntMesh.vertex(var4, var1.last(), var3, var5 ? 6109734 : 15195592, var28.x, var28.y, var28.z, var33, var28.v, var28.nx, var28.ny, var28.nz);
            }
            break;
         default:
            for (HarvestItemRenderer.V var10 : MEAT) {
               float var11 = var10.u + (var5 ? 0.5F : 0.0F);
               HuntMesh.vertex(var4, var1.last(), var3, var6, var10.x, var10.y, var10.z, var11, var10.v, var10.nx, var10.ny, var10.nz);
            }
      }

      var1.popPose();
   }

   private static void limb(List<HarvestItemRenderer.V> var0, double[][] var1, int var2, float var3, float var4, boolean var5, boolean var6) {
      int var7 = var1.length;

      for (int var8 = 0; var8 < var7 - 1; var8++) {
         for (int var9 = 0; var9 < var2; var9++) {
            double var10 = (double)var9 * Math.PI * 2.0 / (double)var2;
            double var12 = (double)(var9 + 1) * Math.PI * 2.0 / (double)var2;
            HarvestItemRenderer.V var14 = ring(var1[var8], var10, var3, var4, (float)var8 / (float)(var7 - 1));
            HarvestItemRenderer.V var15 = ring(var1[var8], var12, var3, var4, (float)var8 / (float)(var7 - 1));
            HarvestItemRenderer.V var16 = ring(var1[var8 + 1], var12, var3, var4, (float)(var8 + 1) / (float)(var7 - 1));
            HarvestItemRenderer.V var17 = ring(var1[var8 + 1], var10, var3, var4, (float)(var8 + 1) / (float)(var7 - 1));
            quad(var0, var14, var15, var16, var17);
         }
      }

      if (var5) {
         cap(var0, var1[0], var2, var3, var4, true);
      }

      if (var6) {
         cap(var0, var1[var7 - 1], var2, var3, var4, false);
      }
   }

   private static HarvestItemRenderer.V ring(double[] var0, double var1, float var3, float var4, float var5) {
      float var6 = (float)(var0[0] + Math.cos(var1) * var0[3]);
      float var7 = (float)var0[1];
      float var8 = (float)(var0[2] + Math.sin(var1) * var0[4]);
      return new HarvestItemRenderer.V(
         var6, var7, var8, var3 + (var4 - var3) * (float)(var1 / (Math.PI * 2)), 0.09F + var5 * 0.3F, (float)Math.cos(var1), 0.0F, (float)Math.sin(var1)
      );
   }

   private static void cap(List<HarvestItemRenderer.V> var0, double[] var1, int var2, float var3, float var4, boolean var5) {
      HarvestItemRenderer.V var6 = new HarvestItemRenderer.V(
         (float)var1[0], (float)var1[1], (float)var1[2], (var3 + var4) / 2.0F, 0.25F, 0.0F, var5 ? -1.0F : 1.0F, 0.0F
      );

      for (int var7 = 0; var7 < var2; var7++) {
         double var8 = (double)var7 * Math.PI * 2.0 / (double)var2;
         double var10 = (double)(var7 + 1) * Math.PI * 2.0 / (double)var2;
         HarvestItemRenderer.V var12 = ring(var1, var8, var3, var4, var5 ? 0.0F : 1.0F);
         HarvestItemRenderer.V var13 = ring(var1, var10, var3, var4, var5 ? 0.0F : 1.0F);
         HarvestItemRenderer.V var14 = new HarvestItemRenderer.V(
            var12.x, var12.y, var12.z, 0.25F + (float)Math.cos(var8) * 0.2F, 0.25F + (float)Math.sin(var8) * 0.2F, 0.0F, var5 ? -1.0F : 1.0F, 0.0F
         );
         HarvestItemRenderer.V var15 = new HarvestItemRenderer.V(
            var13.x, var13.y, var13.z, 0.25F + (float)Math.cos(var10) * 0.2F, 0.25F + (float)Math.sin(var10) * 0.2F, 0.0F, var5 ? -1.0F : 1.0F, 0.0F
         );
         if (var5) {
            quad(var0, var6, var14, var15, var15);
         } else {
            quad(var0, var6, var15, var14, var14);
         }
      }
   }

   private static HarvestItemRenderer.V[] loin(boolean var0) {
      ArrayList var1 = new ArrayList();
      byte var2 = 22;
      double[][] var3 = new double[var2][];

      for (int var4 = 0; var4 < var2; var4++) {
         double var5 = (double)var4 / (double)(var2 - 1);
         double var7 = 0.26 + 0.74 * Math.pow(Math.sin(Math.PI * Math.min(1.0, Math.max(0.0, var5 * 1.03 - 0.015))), 0.55) * (1.0 - 0.3 * var5);
         var3[var4] = new double[]{0.034 * Math.sin(var5 * 2.5), -0.4 + 0.8 * var5, 0.018 * Math.sin(var5 * 3.1 + 0.4), 0.104 * var7, 0.059 * var7};
      }

      if (!var0) {
         limb(var1, var3, 14, 0.03F, 0.47F, true, true);
         return var1.toArray(HarvestItemRenderer.V[]::new);
      } else {
         for (int var12 = 0; var12 < var2 - 1; var12++) {
            for (int var13 = -2; var13 < 2; var13++) {
               double var6 = (Math.PI / 2) + (double)var13 * 0.21;
               double var8 = (Math.PI / 2) + (double)(var13 + 1) * 0.21;
               double[] var10 = var3[var12];
               double[] var11 = var3[var12 + 1];
               quad(
                  var1,
                  skinPoint(var10, var6, 0.3F, 0.4F),
                  skinPoint(var10, var8, 0.34F, 0.4F),
                  skinPoint(var11, var8, 0.34F, 0.44F),
                  skinPoint(var11, var6, 0.3F, 0.44F)
               );
            }
         }

         return var1.toArray(HarvestItemRenderer.V[]::new);
      }
   }

   private static HarvestItemRenderer.V skinPoint(double[] var0, double var1, float var3, float var4) {
      return new HarvestItemRenderer.V(
         (float)(var0[0] + Math.cos(var1) * var0[3] * 1.04),
         (float)var0[1],
         (float)(var0[2] + Math.sin(var1) * var0[4] * 1.04),
         var3,
         var4,
         (float)Math.cos(var1),
         0.0F,
         (float)Math.sin(var1)
      );
   }

   private static HarvestItemRenderer.V[] quarter(boolean var0) {
      ArrayList var1 = new ArrayList();
      if (var0) {
         limb(
            var1,
            new double[][]{
               {0.0, 0.288, -0.012, 0.028, 0.026},
               {0.0, 0.36, -0.01, 0.023, 0.022},
               {0.0, 0.424, -0.004, 0.022, 0.021},
               {0.0, 0.454, 0.0, 0.031, 0.029},
               {0.0, 0.47, 0.004, 0.025, 0.024}
            },
            10,
            0.3F,
            0.44F,
            false,
            true
         );
         return var1.toArray(HarvestItemRenderer.V[]::new);
      } else {
         limb(
            var1,
            new double[][]{
               {0.0, -0.352, 0.02, 0.085, 0.056},
               {0.0, -0.3, 0.012, 0.15, 0.094},
               {0.0, -0.215, 0.004, 0.205, 0.126},
               {0.0, -0.12, 0.0, 0.232, 0.142},
               {0.0, -0.03, -0.006, 0.222, 0.134},
               {0.0, 0.055, -0.016, 0.174, 0.108},
               {0.0, 0.13, -0.03, 0.119, 0.081},
               {0.0, 0.196, -0.04, 0.079, 0.059},
               {0.0, 0.252, -0.034, 0.057, 0.045},
               {0.0, 0.306, -0.018, 0.047, 0.039}
            },
            18,
            0.03F,
            0.47F,
            true,
            true
         );
         return var1.toArray(HarvestItemRenderer.V[]::new);
      }
   }

   private static void quad(
      List<HarvestItemRenderer.V> var0, HarvestItemRenderer.V var1, HarvestItemRenderer.V var2, HarvestItemRenderer.V var3, HarvestItemRenderer.V var4
   ) {
      Collections.addAll(var0, var1, var2, var3, var4);
   }

   private static float radius(double var0) {
      return (float)(1.0 + 0.105 * Math.sin(3.0 * var0 + 0.6) + 0.055 * Math.cos(5.0 * var0) - 0.09 * Math.sin(var0));
   }

   private static HarvestItemRenderer.V cut(double var0, float var2, float var3, float var4) {
      float var5 = (float)Math.cos(var0) * 0.3F * var2 * radius(var0);
      float var6 = (float)Math.sin(var0) * 0.225F * var2 * radius(var0);
      return new HarvestItemRenderer.V(var5, var3, var6, 0.25F + var5 / 0.68F * 0.46F, 0.25F + var6 / 0.53F * 0.46F, 0.0F, var4, 0.0F);
   }

   private static HarvestItemRenderer.V[] meat() {
      ArrayList var0 = new ArrayList();
      byte var1 = 56;

      for (int var5 : new int[]{1, -1}) {
         float var6 = var5 > 0 ? 0.049F : -0.026F;

         for (int var7 = 0; var7 < 4; var7++) {
            for (int var8 = 0; var8 < var1; var8++) {
               double var9 = (double)var8 * Math.PI * 2.0 / (double)var1;
               double var11 = (double)(var8 + 1) * Math.PI * 2.0 / (double)var1;
               HarvestItemRenderer.V var13 = cut(var9, (float)var7 / 4.0F * 0.96F, var6, (float)var5);
               HarvestItemRenderer.V var14 = cut(var11, (float)var7 / 4.0F * 0.96F, var6, (float)var5);
               HarvestItemRenderer.V var15 = cut(var11, (float)(var7 + 1) / 4.0F * 0.96F, var6, (float)var5);
               HarvestItemRenderer.V var16 = cut(var9, (float)(var7 + 1) / 4.0F * 0.96F, var6, (float)var5);
               if (var5 > 0) {
                  quad(var0, var13, var14, var15, var16);
               } else {
                  quad(var0, var16, var15, var14, var13);
               }
            }
         }
      }

      float[] var17 = new float[]{-0.026F, -0.019F, 0.037F, 0.049F};
      float[] var18 = new float[]{0.96F, 1.0F, 1.0F, 0.96F};

      for (int var19 = 0; var19 < 3; var19++) {
         for (int var20 = 0; var20 < var1; var20++) {
            HarvestItemRenderer.V[] var21 = new HarvestItemRenderer.V[4];
            int[] var22 = new int[]{var20, var20, var20 + 1, var20 + 1};
            int[] var23 = new int[]{var19, var19 + 1, var19 + 1, var19};

            for (int var24 = 0; var24 < 4; var24++) {
               double var10 = (double)var22[var24] * Math.PI * 2.0 / (double)var1;
               HarvestItemRenderer.V var12 = cut(var10, var18[var23[var24]], var17[var23[var24]], 0.0F);
               var21[var24] = new HarvestItemRenderer.V(
                  var12.x,
                  var12.y,
                  var12.z,
                  0.025F + 0.45F * (float)var22[var24] / (float)var1,
                  0.08F + (float)var23[var24] * 0.09F,
                  (float)Math.cos(var10),
                  var19 == 0 ? -0.25F : (var19 == 2 ? 0.25F : 0.0F),
                  (float)Math.sin(var10)
               );
            }

            quad(var0, var21[0], var21[1], var21[2], var21[3]);
         }
      }

      return var0.toArray(HarvestItemRenderer.V[]::new);
   }

   private static Vector3f surface(float var0, float var1) {
      float var2 = 0.285F
         + 0.075F * (float)Math.exp(-Math.pow(((double)Math.abs(var1) - 0.72) / 0.17, 2.0))
         - 0.085F * (float)Math.pow((double)Math.abs(var1), 6.0);
      float var3 = var0 * var2;
      float var4 = var1 * 0.39F + 0.012F * (float)Math.sin((double)(var0 * 8.0F + var1 * 5.0F));
      float var5 = -0.052F + 0.02F * (float)Math.sin((double)(var1 * 6.0F + var0 * 3.0F)) + 0.012F * (float)Math.sin((double)(var1 * 13.0F + var0 * 8.0F));
      if (var0 > 0.38F) {
         float var6 = (var0 - 0.38F) / 0.62F * 2.85F;
         var3 = 0.38F * var2 + 0.095F * (float)Math.sin((double)var6);
         var5 += 0.095F * (1.0F - (float)Math.cos((double)var6));
      }

      return new Vector3f(var3, var5, var4);
   }

   private static HarvestItemRenderer.V skin(float var0, float var1, boolean var2) {
      Vector3f var3 = surface(var0, var1);
      Vector3f var4 = surface(var0 + 0.002F, var1).sub(surface(var0 - 0.002F, var1));
      Vector3f var5 = surface(var0, var1 + 0.002F).sub(surface(var0, var1 - 0.002F));
      Vector3f var6 = var5.cross(var4).normalize();
      var3.add(new Vector3f(var6).mul(var2 ? 0.005F : -0.005F));
      if (!var2) {
         var6.negate();
      }

      return new HarvestItemRenderer.V(
         var3.x, var3.y, var3.z, (var2 ? 0.0F : 0.5F) + 0.012F + (var0 + 1.0F) * 0.238F, 0.512F + (var1 + 1.0F) * 0.238F, var6.x, var6.y, var6.z
      );
   }

   private static HarvestItemRenderer.V[] hide() {
      ArrayList var0 = new ArrayList();
      byte var1 = 32;
      byte var2 = 28;

      for (boolean var6 : new boolean[]{true, false}) {
         for (int var7 = 0; var7 < var1; var7++) {
            for (int var8 = 0; var8 < var2; var8++) {
               float var9 = (float)var7 * 2.0F / (float)var1 - 1.0F;
               float var10 = (float)var8 * 2.0F / (float)var2 - 1.0F;
               float var11 = (float)(var7 + 1) * 2.0F / (float)var1 - 1.0F;
               float var12 = (float)(var8 + 1) * 2.0F / (float)var2 - 1.0F;
               if (var6) {
                  quad(var0, skin(var9, var10, true), skin(var9, var12, true), skin(var11, var12, true), skin(var11, var10, true));
               } else {
                  quad(var0, skin(var11, var10, false), skin(var11, var12, false), skin(var9, var12, false), skin(var9, var10, false));
               }
            }
         }
      }

      for (int var16 : new int[]{-1, 1}) {
         for (int var17 = 0; var17 < 32; var17++) {
            float var18 = (float)var17 / 16.0F - 1.0F;
            float var19 = (float)(var17 + 1) / 16.0F - 1.0F;
            quad(var0, skin((float)var16, var18, true), skin((float)var16, var19, true), skin((float)var16, var19, false), skin((float)var16, var18, false));
            quad(var0, skin(var18, (float)var16, true), skin(var19, (float)var16, true), skin(var19, (float)var16, false), skin(var18, (float)var16, false));
         }
      }

      return var0.toArray(HarvestItemRenderer.V[]::new);
   }

   public static final class Extensions implements IClientItemExtensions {
      public BlockEntityWithoutLevelRenderer getCustomRenderer() {
         if (HarvestItemRenderer.instance == null) {
            HarvestItemRenderer.instance = new HarvestItemRenderer();
         }

         return HarvestItemRenderer.instance;
      }
   }

   private static record V(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
   }
}
