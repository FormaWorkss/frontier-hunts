package com.formaworks.frontierhunts.landscape.ride.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.landscape.ride.Atv;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class AtvRenderer extends EntityRenderer<Atv> {
   static final ResourceLocation TEXTURE = FrontierHunts.id("textures/entity/atv.png");
   static final ResourceLocation LIGHTS = FrontierHunts.id("textures/entity/atv_lights.png");
   private final AtvModel model;
   public static final float SCALE = 1.18F;

   public AtvRenderer(Context var1) {
      super(var1);
      this.shadowRadius = 1.0F;
      this.model = new AtvModel(var1.bakeLayer(AtvModel.LAYER));
   }

   public void render(Atv var1, float var2, float var3, PoseStack var4, MultiBufferSource var5, int var6) {
      var4.pushPose();
      var4.mulPose(Axis.YP.rotationDegrees(180.0F - var2));
      var4.mulPose(Axis.XP.rotationDegrees(Mth.lerp(var3, var1.bodyPitchO, var1.bodyPitch)));
      var4.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(var3, var1.bodyRollO, var1.bodyRoll)));
      float var7 = (float)var1.getHurtTime() - var3;
      float var8 = Math.max(0.0F, var1.getDamage() - var3);
      if (var7 > 0.0F) {
         var4.mulPose(Axis.XP.rotationDegrees(Mth.sin(var7) * var7 * Math.min(var8, 30.0F) / 60.0F * (float)var1.getHurtDir()));
      }

      var4.translate(0.0F, -Mth.lerp(var3, var1.suspO, var1.susp) * 0.03F, 0.0F);
      var4.scale(1.18F, 1.18F, 1.18F);
      var4.scale(-1.0F, -1.0F, 1.0F);
      var4.translate(0.0F, -1.501F, 0.0F);
      if (RealisticAtv.active()) {
         RealisticAtv.render(var4, var5, var6, var1.steerShown, Mth.lerp(var3, var1.wheelSpinO, var1.wheelSpin) * (float) (Math.PI / 180.0), com.formaworks.frontierhunts.landscape.ride.grime.client.AtvGrimeRender.bodyTint(var1)); // [atv2] wet = darker paint
         com.formaworks.frontierhunts.landscape.ride.grime.client.AtvGrimeRender.realistic(var1, var4, var5, var6, var1.steerShown, Mth.lerp(var3, var1.wheelSpinO, var1.wheelSpin) * (float) (Math.PI / 180.0)); // [atvgrime] mud/snow/wet layers
         com.formaworks.frontierhunts.landscape.ride.rig.client.AtvRigRender.render(var1, var3, var4, var5, var6, true); // [atvfuel] rear rack cargo
         var4.popPose();
         super.render(var1, var2, var3, var4, var5, var6);
      } else {
         this.model.pose(var1.steerShown, Mth.lerp(var3, var1.wheelSpinO, var1.wheelSpin) * (float) (Math.PI / 180.0));
         this.model.renderToBuffer(var4, var5.getBuffer(this.model.renderType(TEXTURE)), var6, OverlayTexture.NO_OVERLAY, com.formaworks.frontierhunts.landscape.ride.grime.client.AtvGrimeRender.bodyTint(var1)); // [atv2] wet = darker paint (was -1)
         com.formaworks.frontierhunts.landscape.ride.grime.client.AtvGrimeRender.blocky(var1, this.model, var4, var5, var6); // [atvgrime] mud/snow/wet layers
         com.formaworks.frontierhunts.landscape.ride.rig.client.AtvRigRender.render(var1, var3, var4, var5, var6, false); // [atvfuel] rear rack cargo
         if (com.formaworks.frontierhunts.landscape.ride.rig.AtvFuel.running(var1)) { // [atvfuel] lights die with the engine (was isVehicle && !flooded)
            this.model.renderToBuffer(var4, var5.getBuffer(RenderType.eyes(LIGHTS)), 15728880, OverlayTexture.NO_OVERLAY, -1);
         }

         var4.popPose();
         super.render(var1, var2, var3, var4, var5, var6);
      }
   }

   public ResourceLocation getTextureLocation(Atv var1) {
      return TEXTURE;
   }
}
