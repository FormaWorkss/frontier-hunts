package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.HuntProjectile;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.hunting.ArrowSupply;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class HuntProjectileRenderer extends EntityRenderer<HuntProjectile> {
   public HuntProjectileRenderer(Context var1) {
      super(var1);
   }

   public ResourceLocation getTextureLocation(HuntProjectile var1) {
      return WhitetailRenderer.MATERIAL;
   }

   public void render(HuntProjectile var1, float var2, float var3, PoseStack var4, MultiBufferSource var5, int var6) {
      Weapon var7 = var1.kind();
      if (var7 == Weapon.FLARE_GUN) {
         VertexConsumer var8 = FieldMaterials.flat(var5.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS)));
         var4.pushPose();
         Vec3 var9 = var1.getDeltaMovement();
         if (var9.lengthSqr() > 1.0E-4) {
            var4.mulPose(Axis.YP.rotation((float)Math.atan2(-var9.x, -var9.z)));
            var4.mulPose(Axis.XP.rotation((float)Math.atan2(var9.y, var9.horizontalDistance())));
         }

         HuntMesh.tube(var4, var8, var6, 9779495, 0.0, 0.0, -0.1, 0.0, 0.0, 0.09, 0.027, 0.027, 10);
         HuntMesh.tube(var4, var8, 15728880, 16717579, 0.0, 0.0, 0.085, 0.0, 0.0, 0.145, 0.042, 0.02, 10);
         HuntMesh.tube(var4, var8, 15728880, 16755857, 0.0, 0.0, 0.11, 0.0, 0.0, 0.18, 0.023, 0.0, 8);
         var4.popPose();
         FlareGlow.track(var1);
      }

      if (var7.bow) {
         VertexConsumer var16 = var5.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS));
         Vec3 var17 = var1.getDeltaMovement();
         var4.pushPose();
         if (var17.lengthSqr() > 1.0E-4) {
            var4.mulPose(Axis.YP.rotation((float)Math.atan2(-var17.x, -var17.z)));
            var4.mulPose(Axis.XP.rotation((float)Math.atan2(var17.y, var17.horizontalDistance())));
         }

         if (var7 == Weapon.HUNTING_SPEAR) {
            HuntMesh.tube(var4, FieldMaterials.flat(var16), var6, 9205843, 0.0, 0.0, -0.75, 0.0, 0.0, 0.75, 0.012, 0.012, 12);
            HuntMesh.tube(var4, FieldMaterials.flat(var16), var6, 12763825, 0.0, 0.0, -0.75, 0.0, 0.0, -0.9, 0.025, 0.0, 12);
         } else {
            ArrowTip var10 = var1.tip();
            FieldArrowModel.draw(
               var4,
               var16,
               var6,
               var7 == Weapon.BOWFISHING_BOW,
               var7 == Weapon.CROSSBOW,
               var10 == null ? ArrowSupply.Shot.DEFAULT : new ArrowSupply.Shot(var10, var1.primitive()),
               false
            );
            if (var10 != null && var10.tracer() && var17.lengthSqr() > 1.0E-6) {
               Vec3 var11 = var17.normalize();
               ArrowTrails.track(var1.getId(), var10.glow, var1.getPosition(var3).add(var11.scale(var7 == Weapon.CROSSBOW ? 0.26 : 0.36)), var11, false, var3);
            }
         }

         var4.popPose();
         if (var7 == Weapon.BOWFISHING_BOW && var1.getOwner() != null) {
            Vec3 var18 = BowfishingClient.socket(var1.getOwner(), var3).subtract(var1.getPosition(var3));
            if (var18.lengthSqr() < 1600.0) {
               Vec3 var19 = Vec3.ZERO;

               for (int var12 = 1; var12 <= 24; var12++) {
                  double var13 = (double)var12 / 24.0;
                  Vec3 var15 = var18.scale(var13)
                     .add(
                        0.0,
                        -Math.sin(var13 * Math.PI)
                           * Math.min(0.7, var1.tethered() ? Math.max(0.02, (double)var1.lineLength() - var18.length()) * 0.3 : var18.length() * 0.025),
                        0.0
                     );
                  HuntMesh.tube(var4, var16, var6, 13091242, var19.x, var19.y, var19.z, var15.x, var15.y, var15.z, 0.0015, 0.0015, 4);
                  var19 = var15;
               }
            }
         }
      }

      super.render(var1, var2, var3, var4, var5, var6);
   }
}
