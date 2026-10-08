package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class TrophyDisplay {
   static void clear() {
   }

   static void draw(ItemStack var0, PoseStack var1, MultiBufferSource var2, int var3, boolean var4) {
      boolean var5 = var0.is((Item)HuntContent.WHITETAIL_TROPHY.get());
      FilteredFieldTexture.ensure(FieldMaterials.ATLAS);
      VertexConsumer var6 = var2.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS));
      VertexConsumer var7 = FieldMaterials.tile(var6, 1);
      VertexConsumer var8 = FieldMaterials.flat(var6);
      var1.pushPose();
      if (var4) {
         var1.scale(0.7F, 0.7F, 0.7F);
      }

      var1.pushPose();
      var1.mulPose(Axis.YP.rotationDegrees(90.0F));
      double var9 = 0.32;
      double var11 = 0.48;
      double[][] var13 = new double[][]{
         {var11, -var9 * 0.72},
         {var11 * 0.72, -var9},
         {-var11 * 0.6, -var9 * 0.93},
         {-var11, -var9 * 0.15},
         {-var11, var9 * 0.15},
         {-var11 * 0.6, var9 * 0.93},
         {var11 * 0.72, var9},
         {var11, var9 * 0.72}
      };
      ArtMesh.profile(var1, var7, var3, 12626046, 0.025, 0.009, var13);
      var1.popPose();

      for (int var17 : new int[]{-1, 1}) {
         HuntMesh.tube(var1, var8, var3, 9210996, (double)var17 * 0.23, 0.33, -0.027, (double)var17 * 0.23, 0.33, -0.03, 0.009, 0.009, 12);
         ArtMesh.box(var1, var8, var3, 3950139, (double)var17 * 0.23, 0.33, -0.031, 0.009, 0.002, 0.001);
      }

      ArtMesh.box(var1, var8, var3, 11836520, 0.0, -0.385, -0.03, 0.22, 0.045, 0.006);
      if (var5) {
         DeerTraits var18 = DeerTraits.REFERENCE;
         CompoundTag var19 = ExpeditionWeapon.data(var0);
         if (var19.getCompound("deer_traits").getInt("schema") >= 1) {
            var18 = DeerTraits.load(var19.getCompound("deer_traits"));
         }

         DeerMount.draw(var1, var2, var3, var18, var4);
      }

      var1.popPose();
   }

   private TrophyDisplay() {
   }
}
