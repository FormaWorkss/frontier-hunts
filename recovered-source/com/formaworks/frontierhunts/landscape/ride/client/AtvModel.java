package com.formaworks.frontierhunts.landscape.ride.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.landscape.ride.Atv;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;

public class AtvModel extends EntityModel<Atv> {
   public static final ModelLayerLocation LAYER = new ModelLayerLocation(FrontierHunts.id("atv"), "main");
   private final ModelPart root;
   private final ModelPart bars;
   private final ModelPart[] wheels = new ModelPart[4];

   public AtvModel(ModelPart var1) {
      super(RenderType::entityCutoutNoCull);
      this.root = var1;
      this.bars = var1.getChild("bars");
      String[] var2 = new String[]{"wheel_fl", "wheel_fr", "wheel_rl", "wheel_rr"};

      for (int var3 = 0; var3 < 4; var3++) {
         this.wheels[var3] = var1.getChild(var2[var3]);
      }
   }

   public static LayerDefinition createLayer() {
      MeshDefinition var0 = new MeshDefinition();
      PartDefinition var1 = var0.getRoot();
      var1.addOrReplaceChild(
         "body",
         CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-5.0F, 16.0F, -12.0F, 10.0F, 3.0F, 24.0F)
            .texOffs(69, 0)
            .addBox(-6.0F, 11.0F, -8.0F, 12.0F, 5.0F, 17.0F)
            .texOffs(0, 89)
            .addBox(-4.5F, 9.5F, -8.0F, 9.0F, 2.0F, 7.0F)
            .texOffs(53, 28)
            .addBox(-3.5F, 8.5F, -1.0F, 7.0F, 3.0F, 11.0F)
            .texOffs(33, 89)
            .addBox(-6.0F, 12.0F, -18.0F, 12.0F, 3.0F, 6.0F)
            .texOffs(51, 99)
            .addBox(-5.0F, 15.0F, -18.2F, 10.0F, 2.0F, 1.0F)
            .texOffs(74, 99)
            .addBox(-5.5F, 12.4F, -18.4F, 3.0F, 1.6F, 1.0F)
            .texOffs(83, 99)
            .addBox(2.5F, 12.4F, -18.4F, 3.0F, 1.6F, 1.0F)
            .texOffs(90, 28)
            .addBox(-11.0F, 13.0F, -16.0F, 6.0F, 1.5F, 12.0F)
            .texOffs(0, 48)
            .addBox(5.0F, 13.0F, -16.0F, 6.0F, 1.5F, 12.0F)
            .texOffs(37, 48)
            .addBox(-11.0F, 13.0F, 4.0F, 6.0F, 1.5F, 12.0F)
            .texOffs(74, 48)
            .addBox(5.0F, 13.0F, 4.0F, 6.0F, 1.5F, 12.0F)
            .texOffs(92, 99)
            .addBox(-11.0F, 14.5F, -16.0F, 6.0F, 2.0F, 1.0F)
            .texOffs(107, 99)
            .addBox(5.0F, 14.5F, -16.0F, 6.0F, 2.0F, 1.0F)
            .texOffs(0, 108)
            .addBox(-11.0F, 14.5F, 15.0F, 6.0F, 2.0F, 1.0F)
            .texOffs(15, 108)
            .addBox(5.0F, 14.5F, 15.0F, 6.0F, 2.0F, 1.0F)
            .texOffs(51, 63)
            .addBox(-6.0F, 11.5F, 9.0F, 12.0F, 3.5F, 7.0F)
            .texOffs(80, 108)
            .addBox(-5.5F, 12.2F, 16.1F, 2.0F, 1.2F, 0.6F)
            .texOffs(87, 108)
            .addBox(3.5F, 12.2F, 16.1F, 2.0F, 1.2F, 0.6F)
            .texOffs(70, 89)
            .addBox(-8.0F, 11.0F, -17.0F, 16.0F, 1.0F, 8.0F)
            .texOffs(0, 77)
            .addBox(-8.0F, 10.5F, 7.0F, 16.0F, 1.0F, 10.0F)
            .texOffs(0, 112)
            .addBox(-8.0F, 10.0F, -17.0F, 16.0F, 1.0F, 1.0F)
            .texOffs(35, 112)
            .addBox(-8.0F, 9.5F, 16.0F, 16.0F, 1.0F, 1.0F)
            .texOffs(53, 77)
            .addBox(-10.0F, 17.0F, -5.0F, 4.0F, 1.0F, 10.0F)
            .texOffs(82, 77)
            .addBox(6.0F, 17.0F, -5.0F, 4.0F, 1.0F, 10.0F)
            .texOffs(19, 99)
            .addBox(4.0F, 13.5F, 14.0F, 2.0F, 2.0F, 4.0F)
            .texOffs(0, 28)
            .addBox(-4.0F, 19.0F, -10.0F, 8.0F, 0.5F, 18.0F),
         PartPose.ZERO
      );
      var1.addOrReplaceChild(
         "bars",
         CubeListBuilder.create()
            .texOffs(32, 99)
            .addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.5F, 1.0F)
            .texOffs(30, 108)
            .addBox(-7.0F, -3.6F, -0.6F, 14.0F, 1.1F, 1.1F)
            .texOffs(62, 108)
            .addBox(-7.6F, -3.8F, -0.8F, 2.4F, 1.5F, 1.5F)
            .texOffs(71, 108)
            .addBox(5.2F, -3.8F, -0.8F, 2.4F, 1.5F, 1.5F)
            .texOffs(37, 99)
            .addBox(-2.2F, -4.2F, -1.9F, 4.4F, 1.6F, 1.8F)
            .texOffs(70, 112)
            .addBox(-1.4F, -4.0F, -2.1F, 2.8F, 1.1F, 0.3F),
         PartPose.offsetAndRotation(0.0F, 10.0F, -7.0F, -0.35F, 0.0F, 0.0F)
      );
      PartDefinition var2 = var1.addOrReplaceChild(
         "wheel_fl", CubeListBuilder.create().texOffs(0, 99).addBox(-2.3F, -2.0F, -2.0F, 4.6F, 4.0F, 4.0F), PartPose.offset(-10.0F, 19.5F, -10.0F)
      );
      var2.addOrReplaceChild(
         "slab0", CubeListBuilder.create().texOffs(111, 48).addBox(-2.0F, -4.5F, -1.889F, 4.0F, 9.0F, 3.778F), PartPose.rotation(0.0F, 0.0F, 0.0F)
      );
      var2.addOrReplaceChild(
         "slab1", CubeListBuilder.create().texOffs(0, 63).addBox(-1.96F, -4.5F, -1.889F, 3.92F, 9.0F, 3.778F), PartPose.rotation(0.785F, 0.0F, 0.0F)
      );
      var2.addOrReplaceChild(
         "slab2", CubeListBuilder.create().texOffs(17, 63).addBox(-1.92F, -4.5F, -1.889F, 3.84F, 9.0F, 3.778F), PartPose.rotation(1.571F, 0.0F, 0.0F)
      );
      var2.addOrReplaceChild(
         "slab3", CubeListBuilder.create().texOffs(34, 63).addBox(-1.88F, -4.5F, -1.889F, 3.76F, 9.0F, 3.778F), PartPose.rotation(2.356F, 0.0F, 0.0F)
      );
      var2 = var1.addOrReplaceChild(
         "wheel_fr", CubeListBuilder.create().texOffs(0, 99).addBox(-2.3F, -2.0F, -2.0F, 4.6F, 4.0F, 4.0F), PartPose.offset(10.0F, 19.5F, -10.0F)
      );
      var2.addOrReplaceChild(
         "slab0", CubeListBuilder.create().texOffs(111, 48).addBox(-2.0F, -4.5F, -1.889F, 4.0F, 9.0F, 3.778F), PartPose.rotation(0.0F, 0.0F, 0.0F)
      );
      var2.addOrReplaceChild(
         "slab1", CubeListBuilder.create().texOffs(0, 63).addBox(-1.96F, -4.5F, -1.889F, 3.92F, 9.0F, 3.778F), PartPose.rotation(0.785F, 0.0F, 0.0F)
      );
      var2.addOrReplaceChild(
         "slab2", CubeListBuilder.create().texOffs(17, 63).addBox(-1.92F, -4.5F, -1.889F, 3.84F, 9.0F, 3.778F), PartPose.rotation(1.571F, 0.0F, 0.0F)
      );
      var2.addOrReplaceChild(
         "slab3", CubeListBuilder.create().texOffs(34, 63).addBox(-1.88F, -4.5F, -1.889F, 3.76F, 9.0F, 3.778F), PartPose.rotation(2.356F, 0.0F, 0.0F)
      );
      var2 = var1.addOrReplaceChild(
         "wheel_rl", CubeListBuilder.create().texOffs(0, 99).addBox(-2.3F, -2.0F, -2.0F, 4.6F, 4.0F, 4.0F), PartPose.offset(-10.0F, 19.5F, 10.0F)
      );
      var2.addOrReplaceChild(
         "slab0", CubeListBuilder.create().texOffs(111, 48).addBox(-2.0F, -4.5F, -1.889F, 4.0F, 9.0F, 3.778F), PartPose.rotation(0.0F, 0.0F, 0.0F)
      );
      var2.addOrReplaceChild(
         "slab1", CubeListBuilder.create().texOffs(0, 63).addBox(-1.96F, -4.5F, -1.889F, 3.92F, 9.0F, 3.778F), PartPose.rotation(0.785F, 0.0F, 0.0F)
      );
      var2.addOrReplaceChild(
         "slab2", CubeListBuilder.create().texOffs(17, 63).addBox(-1.92F, -4.5F, -1.889F, 3.84F, 9.0F, 3.778F), PartPose.rotation(1.571F, 0.0F, 0.0F)
      );
      var2.addOrReplaceChild(
         "slab3", CubeListBuilder.create().texOffs(34, 63).addBox(-1.88F, -4.5F, -1.889F, 3.76F, 9.0F, 3.778F), PartPose.rotation(2.356F, 0.0F, 0.0F)
      );
      var2 = var1.addOrReplaceChild(
         "wheel_rr", CubeListBuilder.create().texOffs(0, 99).addBox(-2.3F, -2.0F, -2.0F, 4.6F, 4.0F, 4.0F), PartPose.offset(10.0F, 19.5F, 10.0F)
      );
      var2.addOrReplaceChild(
         "slab0", CubeListBuilder.create().texOffs(111, 48).addBox(-2.0F, -4.5F, -1.889F, 4.0F, 9.0F, 3.778F), PartPose.rotation(0.0F, 0.0F, 0.0F)
      );
      var2.addOrReplaceChild(
         "slab1", CubeListBuilder.create().texOffs(0, 63).addBox(-1.96F, -4.5F, -1.889F, 3.92F, 9.0F, 3.778F), PartPose.rotation(0.785F, 0.0F, 0.0F)
      );
      var2.addOrReplaceChild(
         "slab2", CubeListBuilder.create().texOffs(17, 63).addBox(-1.92F, -4.5F, -1.889F, 3.84F, 9.0F, 3.778F), PartPose.rotation(1.571F, 0.0F, 0.0F)
      );
      var2.addOrReplaceChild(
         "slab3", CubeListBuilder.create().texOffs(34, 63).addBox(-1.88F, -4.5F, -1.889F, 3.76F, 9.0F, 3.778F), PartPose.rotation(2.356F, 0.0F, 0.0F)
      );
      return LayerDefinition.create(var0, 128, 128);
   }

   public void pose(float var1, float var2) {
      float var3 = var1 * 0.55F;

      for (int var4 = 0; var4 < 4; var4++) {
         this.wheels[var4].xRot = var2;
         this.wheels[var4].yRot = var4 < 2 ? var3 : 0.0F;
      }

      this.bars.yRot = var3 * 0.8F;
   }

   public void setupAnim(Atv var1, float var2, float var3, float var4, float var5, float var6) {
   }

   public void renderToBuffer(PoseStack var1, VertexConsumer var2, int var3, int var4, int var5) {
      this.root.render(var1, var2, var3, var4, var5);
   }
}
