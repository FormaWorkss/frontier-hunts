package com.formaworks.frontierhunts.landscape.ride.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.landscape.ride.Wingsuit;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class WingsuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
   public static final ModelLayerLocation LAYER = new ModelLayerLocation(FrontierHunts.id("wingsuit"), "main");
   static final ResourceLocation SUIT = FrontierHunts.id("textures/entity/wingsuit.png");
   static final ResourceLocation WINGS = FrontierHunts.id("textures/entity/wingsuit_wings.png");
   static final float ARM = 1.3F;
   static final float LEG = 0.32F;
   private final HumanoidModel<AbstractClientPlayer> suit;
   private static HumanoidModel<AbstractClientPlayer> firstPerson;
   static boolean parentPosed;
   private static final int SPAN = 10;
   private static final int CHORD = 6;

   public WingsuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> var1, EntityModelSet var2) {
      super(var1);
      this.suit = new HumanoidModel(var2.bakeLayer(LAYER));
      if (firstPerson == null) {
         firstPerson = new HumanoidModel(var2.bakeLayer(LAYER));
      }
   }

   public static boolean insertPose(PlayerRenderer var0) {
      try {
         Field var1 = LivingEntityRenderer.class.getDeclaredField("layers");
         var1.setAccessible(true);
         ((List)var1.get(var0)).add(0, new WingsuitLayer.Pose(var0));
         return true;
      } catch (RuntimeException | ReflectiveOperationException var2) {
         RideContentLog.warn("Wingsuit: cannot pose the player model ahead of other layers; armour and held items keep the vanilla pose", var2);
         return false;
      }
   }

   static boolean poseLimbs(AbstractClientPlayer var0, float var1, float var2, ModelPart var3, ModelPart var4, ModelPart var5, ModelPart var6) {
      float var7 = spread(var0, var1);
      if (var7 > 0.0F) {
         pose(var3, var7, 0.0F, 0.0F, 1.3F);
         pose(var4, var7, 0.0F, 0.0F, -1.3F);
         pose(var5, var7, 0.0F, 0.0F, 0.32F);
         pose(var6, var7, 0.0F, 0.0F, -0.32F);
         return true;
      } else if (underCanopy(var0)) {
         GliderRender.Look var8 = GliderRender.LOOKS.get(var0);
         float var9 = var8 == null ? 0.0F : (float)Mth.lerp((double)var1, var8.brakeO, var8.brake);
         float var10 = Mth.lerp(var9, -2.85F, -0.35F);
         float var11 = Mth.lerp(var9, 0.16F, 0.3F);
         pose(var3, 1.0F, var10, 0.0F, -var11);
         pose(var4, 1.0F, var10, 0.0F, var11);
         float var12 = Mth.sin(var2 * 0.09F) * 0.06F;
         pose(var5, 1.0F, -0.12F + var12, 0.0F, 0.05F);
         pose(var6, 1.0F, 0.08F - var12, 0.0F, -0.05F);
         return true;
      } else {
         return false;
      }
   }

   public static LayerDefinition createLayer() {
      MeshDefinition var0 = HumanoidModel.createMesh(new CubeDeformation(0.4F), 0.0F);
      PartDefinition var1 = var0.getRoot().getChild("body");
      var1.addOrReplaceChild("rig", CubeListBuilder.create().texOffs(0, 32).addBox(-4.2F, 0.0F, 2.3F, 8.4F, 11.0F, 3.4F), PartPose.ZERO);
      var1.addOrReplaceChild("handle", CubeListBuilder.create().texOffs(28, 32).addBox(1.6F, 10.6F, 5.6F, 2.2F, 1.4F, 1.0F), PartPose.ZERO);
      var1.addOrReplaceChild("chest_strap", CubeListBuilder.create().texOffs(28, 36).addBox(-3.6F, 3.4F, -2.75F, 7.2F, 0.9F, 0.4F), PartPose.ZERO);
      return LayerDefinition.create(var0, 64, 64);
   }

   static float spread(AbstractClientPlayer var0, float var1) {
      if (!Wingsuit.flying(var0)) {
         return 0.0F;
      } else {
         float var2 = Mth.clamp(((float)var0.getFallFlyingTicks() + var1 - 1.0F) / 7.0F, 0.0F, 1.0F);
         return var2 * var2 * (3.0F - 2.0F * var2);
      }
   }

   static boolean underCanopy(AbstractClientPlayer var0) {
      return Wingsuit.canopyOpen(var0) && !var0.onGround();
   }

   public void render(
      PoseStack var1, MultiBufferSource var2, int var3, AbstractClientPlayer var4, float var5, float var6, float var7, float var8, float var9, float var10
   ) {
      if (Wingsuit.wearing(var4) && !var4.isInvisible() && !var4.isSpectator()) {
         ((PlayerModel)this.getParentModel()).copyPropertiesTo(this.suit);
         this.suit.head.visible = false;
         this.suit.hat.visible = false;
         this.suit.leftArm.visible = this.suit.rightArm.visible = this.suit.leftLeg.visible = this.suit.rightLeg.visible = this.suit.body.visible = true;
         float var11 = spread(var4, var7);
         if (!parentPosed) {
            poseLimbs(var4, var7, var8, this.suit.rightArm, this.suit.leftArm, this.suit.rightLeg, this.suit.leftLeg);
         }

         int var12 = LivingEntityRenderer.getOverlayCoords(var4, 0.0F);
         this.suit.renderToBuffer(var1, var2.getBuffer(RenderType.entityCutoutNoCull(SUIT)), var3, var12, -1);
         if (var11 > 0.02F) {
            this.drawWings(var1, var2.getBuffer(RenderType.entityCutoutNoCull(WINGS)), var3, var12, var11, var8 + (float)var4.getId() * 7.3F);
         }
      }
   }

   private static void pose(ModelPart var0, float var1, float var2, float var3, float var4) {
      var0.xRot = Mth.lerp(var1, var0.xRot, var2);
      var0.yRot = Mth.lerp(var1, var0.yRot, var3);
      var0.zRot = Mth.lerp(var1, var0.zRot, var4);
   }

   private static Vector3f at(ModelPart var0, float var1, float var2, float var3) {
      Vector3f var4 = new Vector3f(var1, var2, var3);
      new Quaternionf().rotationZYX(var0.zRot, var0.yRot, var0.xRot).transform(var4);
      return var4.add(var0.x, var0.y, var0.z);
   }

   private void drawWings(PoseStack var1, VertexConsumer var2, int var3, int var4, float var5, float var6) {
      for (byte var7 = -1; var7 <= 1; var7 += 2) {
         ModelPart var8 = var7 < 0 ? this.suit.rightArm : this.suit.leftArm;
         ModelPart var9 = var7 < 0 ? this.suit.rightLeg : this.suit.leftLeg;
         float var10 = var7 < 0 ? 1.35F : -1.35F;
         Vector3f var11 = at(var9, (float)var7 * 2.35F, 8.5F, 0.0F);
         Vector3f var12 = at(var8, var10, 10.2F, 0.0F);
         Vector3f[][] var13 = new Vector3f[11][7];
         Vector3f[][] var14 = new Vector3f[11][7];

         for (int var15 = 0; var15 <= 10; var15++) {
            float var16 = (float)var15 / 10.0F;
            Vector3f var17 = at(var8, var10, Mth.lerp(var16, -1.2F, 10.2F), 0.0F);
            Vector3f var18 = new Vector3f(var11).lerp(var12, var16);
            Vector3f var19 = new Vector3f(var18).lerp(var17, 0.24F * Mth.sin((float) Math.PI * var16));
            if (var16 < 0.001F) {
               var19 = new Vector3f(var11);
            }

            float var20 = 0.35F + 0.65F * Mth.sin((float) Math.PI * Math.min(1.0F, var16 * 1.1F));

            for (int var21 = 0; var21 <= 6; var21++) {
               float var22 = (float)var21 / 6.0F;
               Vector3f var23 = new Vector3f(var17).lerp(var19, var22);
               float var24 = Mth.sin((float) Math.PI * var22) * var20 * var5;
               float var25 = 0.35F * var22 * var22 * Mth.sin(var6 * 2.4F + var16 * 7.0F) * var5;
               var13[var15][var21] = new Vector3f(var23.x, var23.y, var23.z + 2.4F * var24 + var25);
               var14[var15][var21] = new Vector3f(var23.x, var23.y, var23.z - 0.45F * var24 + var25);
            }
         }

         surface(var1, var2, var13, var14, 0.0F, var3, var4);
      }

      Vector3f[][] var26 = new Vector3f[11][7];
      Vector3f[][] var27 = new Vector3f[11][7];

      for (int var28 = 0; var28 <= 10; var28++) {
         float var29 = (float)var28 / 10.0F;
         float var30 = Mth.lerp(var29, 0.6F, 11.4F);
         Vector3f var31 = at(this.suit.rightLeg, 2.3F, var30, 0.0F);
         Vector3f var32 = at(this.suit.leftLeg, -2.3F, var30, 0.0F);

         for (int var33 = 0; var33 <= 6; var33++) {
            float var34 = (float)var33 / 6.0F;
            Vector3f var35 = new Vector3f(var31).lerp(var32, var34);
            float var36 = 2.6F * Mth.sin((float) Math.PI * var34) * var29 * var29 * var29;
            float var37 = Mth.sin((float) Math.PI * var34) * (0.3F + 0.7F * var29) * var5;
            float var38 = 0.25F * var29 * var29 * Mth.sin(var6 * 2.9F + var34 * 5.0F) * var5;
            var26[var28][var33] = new Vector3f(var35.x, var35.y - var36, var35.z + 1.2F * var37 + var38);
            var27[var28][var33] = new Vector3f(var35.x, var35.y - var36, var35.z - 0.3F * var37 + var38);
         }
      }

      surface(var1, var2, var26, var27, 0.5F, var3, var4);
   }

   private static void surface(PoseStack var0, VertexConsumer var1, Vector3f[][] var2, Vector3f[][] var3, float var4, int var5, int var6) {
      com.mojang.blaze3d.vertex.PoseStack.Pose var7 = var0.last();
      Matrix4f var8 = var7.pose();
      int var9 = var2.length - 1;
      int var10 = var2[0].length - 1;

      for (int var11 = 0; var11 < var9; var11++) {
         for (int var12 = 0; var12 < var10; var12++) {
            float var13 = (float)var12 / (float)var10 * 0.5F;
            float var14 = (float)(var12 + 1) / (float)var10 * 0.5F;
            float var15 = var4 + (float)var11 / (float)var9 * 0.5F;
            float var16 = var4 + (float)(var11 + 1) / (float)var9 * 0.5F;
            quad(
               var1,
               var8,
               var7,
               var2[var11][var12],
               var2[var11 + 1][var12],
               var2[var11 + 1][var12 + 1],
               var2[var11][var12 + 1],
               var13,
               var14,
               var15,
               var16,
               var5,
               var6,
               true
            );
            quad(
               var1,
               var8,
               var7,
               var3[var11][var12 + 1],
               var3[var11 + 1][var12 + 1],
               var3[var11 + 1][var12],
               var3[var11][var12],
               0.5F + var14,
               0.5F + var13,
               var15,
               var16,
               var5,
               var6,
               false
            );
         }
      }
   }

   private static void quad(
      VertexConsumer var0,
      Matrix4f var1,
      com.mojang.blaze3d.vertex.PoseStack.Pose var2,
      Vector3f var3,
      Vector3f var4,
      Vector3f var5,
      Vector3f var6,
      float var7,
      float var8,
      float var9,
      float var10,
      int var11,
      int var12,
      boolean var13
   ) {
      Vector3f var14 = new Vector3f(var5).sub(var3).cross(new Vector3f(var6).sub(var4));
      if ((double)var14.lengthSquared() < 1.0E-10) {
         var14.set(0.0F, 0.0F, 1.0F);
      } else {
         var14.normalize();
      }

      if (var13 == var14.z < 0.0F) {
         var14.negate();
      }

      vert(var0, var1, var2, var3, var7, var9, var11, var12, var14);
      vert(var0, var1, var2, var4, var7, var10, var11, var12, var14);
      vert(var0, var1, var2, var5, var8, var10, var11, var12, var14);
      vert(var0, var1, var2, var6, var8, var9, var11, var12, var14);
   }

   private static void vert(
      VertexConsumer var0,
      Matrix4f var1,
      com.mojang.blaze3d.vertex.PoseStack.Pose var2,
      Vector3f var3,
      float var4,
      float var5,
      int var6,
      int var7,
      Vector3f var8
   ) {
      var0.addVertex(var1, var3.x / 16.0F, var3.y / 16.0F, var3.z / 16.0F)
         .setColor(255, 255, 255, 255)
         .setUv(var4, var5)
         .setOverlay(var7)
         .setLight(var6)
         .setNormal(var2, var8.x, var8.y, var8.z);
   }

   @EventBusSubscriber(
      modid = "frontierhunts",
      value = {Dist.CLIENT}
   )
   public static final class Hide {
      @SubscribeEvent
      public static void arm(RenderArmEvent var0) {
         AbstractClientPlayer var1 = var0.getPlayer();
         if (WingsuitLayer.firstPerson != null && Wingsuit.wearing(var1) && !var1.isInvisible()) {
            if (Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(var1) instanceof PlayerRenderer var2) {
               PlayerModel var6 = (PlayerModel)var2.getModel();
               var6.attackTime = 0.0F;
               var6.crouching = false;
               var6.swimAmount = 0.0F;
               var6.setupAnim(var1, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
               boolean var4 = var0.getArm() == HumanoidArm.RIGHT;
               ModelPart var5 = var4 ? WingsuitLayer.firstPerson.rightArm : WingsuitLayer.firstPerson.leftArm;
               var5.copyFrom(var4 ? var6.rightArm : var6.leftArm);
               var5.xRot = 0.0F;
               var5.visible = true;
               var5.render(
                  var0.getPoseStack(),
                  var0.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(WingsuitLayer.SUIT)),
                  var0.getPackedLight(),
                  OverlayTexture.NO_OVERLAY
               );
               var0.setCanceled(true);
            }
         }
      }

      @SubscribeEvent
      public static void pre(Pre var0) {
         if (var0.getEntity() instanceof AbstractClientPlayer var1 && Wingsuit.wearing(var1) && !var1.isInvisible() && !var1.isSpectator()) {
            PlayerModel var3 = (PlayerModel)var0.getRenderer().getModel();
            var3.leftArm.visible = var3.rightArm.visible = var3.leftSleeve.visible = var3.rightSleeve.visible = false;
            var3.leftLeg.visible = var3.rightLeg.visible = var3.leftPants.visible = var3.rightPants.visible = false;
            return;
         }
      }
   }

   public static final class Pose extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
      public Pose(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> var1) {
         super(var1);
      }

      public void render(
         PoseStack var1, MultiBufferSource var2, int var3, AbstractClientPlayer var4, float var5, float var6, float var7, float var8, float var9, float var10
      ) {
         if (Wingsuit.wearing(var4)) {
            PlayerModel var11 = (PlayerModel)this.getParentModel();
            if (WingsuitLayer.poseLimbs(var4, var7, var8, var11.rightArm, var11.leftArm, var11.rightLeg, var11.leftLeg)) {
               var11.rightSleeve.copyFrom(var11.rightArm);
               var11.leftSleeve.copyFrom(var11.leftArm);
               var11.rightPants.copyFrom(var11.rightLeg);
               var11.leftPants.copyFrom(var11.leftLeg);
            }
         }
      }
   }
}
