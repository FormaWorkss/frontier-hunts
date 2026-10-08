package com.formaworks.frontierhunts.landscape.ride.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.landscape.ride.Paraglider;
import com.formaworks.frontierhunts.landscape.ride.Wingsuit;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class GliderRender {
   static final ResourceLocation WING = FrontierHunts.id("textures/entity/paraglider_wing.png");
   static final ResourceLocation GEAR = FrontierHunts.id("textures/entity/paraglider_gear.png");
   static final ResourceLocation CANOPY = FrontierHunts.id("textures/entity/parachute_canopy.png");
   static final int CELLS = 40;
   static final int CHORD = 12;
   static final double LINES = 8.0;
   static final double THETA = 48.0;
   static final double C0 = 2.7;
   static final double HARNESS = 1.25;
   static final GliderRender.Spec PARAGLIDER = new GliderRender.Spec(40, 12, 8.0, 48.0, 1.35, 2.7, 0.86, 0.62, 0.16, 0.05, 1.25, 14.0F, WING);
   static final GliderRender.Spec PARACHUTE = new GliderRender.Spec(9, 10, 4.4, 34.0, 1.15, 2.35, 0.22, 0.12, 0.2, 0.1, 1.45, 26.0F, CANOPY);
   static final Map<Player, GliderRender.Look> LOOKS = new WeakHashMap<>();

   private GliderRender() {
   }

   static void tick() {
      Minecraft var0 = Minecraft.getInstance();
      if (var0.level == null) {
         LOOKS.clear();
      } else {
         for (Player var2 : var0.level.players()) {
            GliderRender.Spec var3 = Paraglider.gliding(var2) ? PARAGLIDER : (Wingsuit.canopyOpen(var2) ? PARACHUTE : null);
            boolean var4 = var3 != null && !var2.onGround();
            GliderRender.Look var5 = LOOKS.get(var2);
            if (var5 != null && var5.spec != var3) {
               LOOKS.remove(var2);
               var5 = null;
            }

            if (!var4) {
               if (var5 != null) {
                  LOOKS.remove(var2);
               }
            } else {
               if (var5 == null) {
                  var5 = new GliderRender.Look();
                  LOOKS.put(var2, var5);
                  var5.spec = var3;
                  var5.last = var2.position();
                  Vec3 var6 = var2.getDeltaMovement();
                  var5.heading = var5.headingO = var6.horizontalDistanceSqr() > 0.001 ? Math.toDegrees(Math.atan2(-var6.x, var6.z)) : (double)var2.getYRot();
               }

               var5.headingO = var5.heading;
               var5.bankO = var5.bank;
               var5.brakeO = var5.brake;
               var5.openO = var5.open++;
               if (var2 == var0.player && GliderClient.active) {
                  var5.heading = GliderClient.heading;
                  var5.bank = GliderClient.bank;
                  var5.brake = var5.brake + ((double)(GliderClient.forwardIn < -0.1F ? 1 : 0) - var5.brake) * 0.2;
               } else {
                  Vec3 var15 = var2.position().subtract(var5.last);
                  var5.last = var2.position();
                  if (var15.x * var15.x + var15.z * var15.z > 1.0E-4) {
                     double var7 = Math.toDegrees(Math.atan2(-var15.x, var15.z));
                     double var9 = Mth.wrapDegrees(var7 - var5.heading);
                     var5.heading = Mth.wrapDegrees(var5.heading + var9 * 0.35);
                     double var11 = var9 * 0.35 * (float) (Math.PI / 180.0) * 20.0;
                     double var13 = Math.hypot(var15.x, var15.z) * 20.0;
                     var5.bank = var5.bank + (Mth.clamp(Math.toDegrees(Math.atan(var11 * var13 / 9.81)), -60.0, 60.0) - var5.bank) * 0.2;
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void render(RenderLevelStageEvent var0) {
      if (var0.getStage() == Stage.AFTER_ENTITIES && !LOOKS.isEmpty()) {
         Minecraft var1 = Minecraft.getInstance();
         if (var1.level != null) {
            float var2 = var0.getPartialTick().getGameTimeDeltaPartialTick(false);
            Vec3 var3 = var0.getCamera().getPosition();
            BufferSource var4 = var1.renderBuffers().bufferSource();
            PoseStack var5 = var0.getPoseStack();

            for (Entry var7 : LOOKS.entrySet()) {
               Player var8 = (Player)var7.getKey();
               GliderRender.Look var9 = (GliderRender.Look)var7.getValue();
               if (!var8.isRemoved() && var8.level() == var1.level) {
                  double var10 = Mth.lerp((double)var2, var8.xo, var8.getX());
                  double var12 = Mth.lerp((double)var2, var8.yo, var8.getY());
                  double var14 = Mth.lerp((double)var2, var8.zo, var8.getZ());
                  if (!(new Vec3(var10, var12, var14).distanceToSqr(var3) > 102400.0)) {
                     float var16 = (float)(var9.headingO + Mth.wrapDegrees(var9.heading - var9.headingO) * (double)var2);
                     float var17 = (float)Mth.lerp((double)var2, var9.bankO, var9.bank);
                     float var18 = (float)Mth.lerp((double)var2, var9.brakeO, var9.brake);
                     GliderRender.Spec var19 = var9.spec;
                     float var20 = (float)var9.openO + (float)(var9.open - var9.openO) * var2;
                     boolean var21 = var19 == PARACHUTE;
                     float var22 = var21 ? Mth.clamp((var20 - 5.0F) / (var19.fill() - 5.0F), 0.0F, 1.0F) : Mth.clamp(var20 / var19.fill(), 0.0F, 1.0F);
                     float var23 = var21 ? Mth.clamp(var20 / 6.0F, 0.0F, 1.0F) : 1.0F;
                     float var24 = (float)(var1.level.getGameTime() % 24000L) + var2;
                     int var25 = LevelRenderer.getLightColor(var1.level, BlockPos.containing(var10, var12 + var19.anchor() + var19.lines() * 0.9, var14));
                     int var26 = LevelRenderer.getLightColor(var1.level, BlockPos.containing(var10, var12 + 1.0, var14));
                     if (!var21) {
                        var5.pushPose();
                        var5.translate(var10 - var3.x, var12 - var3.y, var14 - var3.z);
                        float var27 = Mth.rotLerp(var2, var8.yBodyRotO, var8.yBodyRot);
                        var5.mulPose(Axis.YP.rotationDegrees(-var27));
                        drawHarness(var5, var4.getBuffer(RenderType.entityCutoutNoCull(GEAR)), var26);
                        var5.popPose();
                     }

                     var5.pushPose();
                     var5.translate(var10 - var3.x, var12 - var3.y + var19.anchor(), var14 - var3.z);
                     var5.mulPose(Axis.YP.rotationDegrees(-var16));
                     var5.mulPose(Axis.ZP.rotationDegrees(var17));
                     if (var21) {
                        if (var20 < 5.0F) {
                           drawBag(var5, var4.getBuffer(RenderType.entityCutoutNoCull(GEAR)), var26, var23, var24);
                        } else {
                           drawWing(var19, var5, var4.getBuffer(RenderType.entityCutoutNoCull(var19.tex())), var25, var22, var18, var24, var8.getId());
                        }

                        drawChuteRig(var19, var5, var4.getBuffer(RenderType.entityCutoutNoCull(GEAR)), var26, var25, var22, var20, var24, var8.getId());
                        drawChuteLines(var19, var5, var4.getBuffer(RenderType.lines()), var22, var23, var18, var24, var8.getId());
                     } else {
                        drawWing(var19, var5, var4.getBuffer(RenderType.entityCutoutNoCull(WING)), var25, var22, var18, var24, var8.getId());
                        drawRisers(var5, var4.getBuffer(RenderType.entityCutoutNoCull(GEAR)), var26, var22);
                        drawLines(var5, var4.getBuffer(RenderType.lines()), var22, var18, var24, var8.getId());
                     }

                     var5.popPose();
                  }
               }
            }

            var4.endBatch(RenderType.entityCutoutNoCull(WING));
            var4.endBatch(RenderType.entityCutoutNoCull(CANOPY));
            var4.endBatch(RenderType.entityCutoutNoCull(GEAR));
            var4.endBatch(RenderType.lines());
         }
      }
   }

   static double easeOut(double var0) {
      var0 = Mth.clamp(var0, 0.0, 1.0);
      double var2 = 1.0 - var0;
      return 1.0 - var2 * var2 * var2 + Math.sin(var0 * Math.PI) * 0.05;
   }

   static Vector3f point(double var0, double var2, int var4, float var5, float var6, float var7, int var8, double var9) {
      return point(PARAGLIDER, var0, var2, var4, var5, var6, var7, var8, var9);
   }

   static Vector3f point(GliderRender.Spec var0, double var1, double var3, int var5, float var6, float var7, float var8, int var9, double var10) {
      double var12 = easeOut((double)var6);
      double var14 = Math.abs(var1);
      double var16 = Math.toRadians(var0.theta() * Math.pow(var14, var0.curl()) * (0.3 + 0.7 * var12)) * (double)(var1 >= 0.0 ? 1 : -1);
      double var18 = var0.lines() * (1.0 - 0.07 * var1 * var1) * (0.5 + 0.5 * var12);
      double var20 = var0.c0() * Math.sqrt(Math.max(0.02, 1.0 - var0.taper() * var1 * var1)) * (0.65 + 0.35 * var12);
      double var22 = var0.thick() * var20 * (1.0 - 0.35 * var1 * var1) * var12;
      double var24 = var22 * (1.4845 * Math.sqrt(var3) - 0.63 * var3 - 1.758 * var3 * var3 + 1.4215 * var3 * var3 * var3 - 0.5075 * var3 * var3 * var3 * var3);
      double var26 = var22 * 0.3 * 4.0 * var3 * (1.0 - var3);
      double var28 = var26 + (var5 > 0 ? 0.95 * var24 : -0.3 * var24);
      var28 += var10 * (var0.pillow() * var20 / var0.c0()) * Math.sqrt(Math.max(0.0, Math.sin(Math.PI * var3))) * (var5 > 0 ? 1.0 : 0.5);
      if (var3 > 0.6) {
         var28 -= (double)var7 * 0.65 * (var0.c0() / 2.7) * Math.pow((var3 - 0.6) / 0.4, 2.0) * (1.0 - 0.4 * var1 * var1);
      }

      var28 += 0.03 * Math.sin((double)var8 * 1.6 + var1 * 9.0 + (double)var9) * var3 * var3 * var12;
      if (var12 < 0.999) {
         var28 += (1.0 - var12) * 0.25 * Math.sin((double)var8 * 6.3 + var1 * 5.0 + var3 * 4.0 + (double)var9) * (0.4 + var3);
      }

      double var30 = (1.0 - var12) * (var0 == PARAGLIDER ? 3.5 : 2.2);
      double var32 = 0.45 * var0.c0() - var0.sweep() * var1 * var1;
      double var34 = var32 - var3 * var20 - var30;
      double var36 = var18 + var28;
      return new Vector3f((float)(Math.sin(var16) * var36), (float)(Math.cos(var16) * var36 - (1.0 - var12) * var18 * 0.5), (float)var34);
   }

   static void drawWing(GliderRender.Spec var0, PoseStack var1, VertexConsumer var2, int var3, float var4, float var5, float var6, int var7) {
      Matrix4f var8 = var1.last().pose();
      Pose var9 = var1.last();
      int var10 = var0.cells();
      int var11 = var0.chord();
      int var12 = var10 * 2 + 1;
      double[] var13 = new double[var12];
      double[] var14 = new double[var12];
      double[] var15 = new double[var12];

      for (int var16 = 0; var16 < var10; var16++) {
         var13[2 * var16] = -1.0 + 2.0 * (double)var16 / (double)var10;
         var14[2 * var16] = 0.0;
         var15[2 * var16] = (double)var16 / (double)var10;
         var13[2 * var16 + 1] = var13[2 * var16] + 1.0 / (double)var10;
         var14[2 * var16 + 1] = 1.0;
         var15[2 * var16 + 1] = ((double)var16 + 0.5) / (double)var10;
      }

      var13[var12 - 1] = 1.0;
      var14[var12 - 1] = 0.0;
      var15[var12 - 1] = 1.0;
      double[] var25 = new double[var11 + 1];

      for (int var17 = 0; var17 <= var11; var17++) {
         var25[var17] = 0.5 - 0.5 * Math.cos(Math.PI * (double)var17 / (double)var11);
      }

      Vector3f[][] var26 = new Vector3f[var12][var11 + 1];
      Vector3f[][] var18 = new Vector3f[var12][var11 + 1];

      for (int var19 = 0; var19 < var12; var19++) {
         for (int var20 = 0; var20 <= var11; var20++) {
            var26[var19][var20] = point(var0, var13[var19], var25[var20], 1, var4, var5, var6, var7, var14[var19]);
            var18[var19][var20] = point(var0, var13[var19], var25[var20], -1, var4, var5, var6, var7, var14[var19]);
         }
      }

      for (int var27 = 0; var27 < var12 - 1; var27++) {
         float var28 = (float)var15[var27];
         float var21 = (float)var15[var27 + 1];

         for (int var22 = 0; var22 < var11; var22++) {
            float var23 = (float)(var25[var22] * 0.5);
            float var24 = (float)(var25[var22 + 1] * 0.5);
            quad(
               var2,
               var8,
               var9,
               var26[var27][var22],
               var26[var27 + 1][var22],
               var26[var27 + 1][var22 + 1],
               var26[var27][var22 + 1],
               var28,
               var21,
               var23,
               var24,
               var3
            );
            quad(
               var2,
               var8,
               var9,
               var18[var27][var22 + 1],
               var18[var27 + 1][var22 + 1],
               var18[var27 + 1][var22],
               var18[var27][var22],
               var28,
               var21,
               0.5F + var24,
               0.5F + var23,
               var3
            );
         }
      }
   }

   private static void quad(
      VertexConsumer var0,
      Matrix4f var1,
      Pose var2,
      Vector3f var3,
      Vector3f var4,
      Vector3f var5,
      Vector3f var6,
      float var7,
      float var8,
      float var9,
      float var10,
      int var11
   ) {
      Vector3f var12 = new Vector3f(var5).sub(var3);
      Vector3f var13 = new Vector3f(var6).sub(var4);
      Vector3f var14 = var12.cross(var13);
      if ((double)var14.lengthSquared() < 1.0E-12) {
         var14.set(0.0F, 1.0F, 0.0F);
      } else {
         var14.normalize();
      }

      vert(var0, var1, var2, var3, var7, var9, var11, var14);
      vert(var0, var1, var2, var4, var8, var9, var11, var14);
      vert(var0, var1, var2, var5, var8, var10, var11, var14);
      vert(var0, var1, var2, var6, var7, var10, var11, var14);
   }

   private static void vert(VertexConsumer var0, Matrix4f var1, Pose var2, Vector3f var3, float var4, float var5, int var6, Vector3f var7) {
      var0.addVertex(var1, var3.x, var3.y, var3.z)
         .setColor(255, 255, 255, 255)
         .setUv(var4, var5)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(var6)
         .setNormal(var2, var7.x, var7.y, var7.z);
   }

   static float sliderY(GliderRender.Spec var0, float var1) {
      return (float)Mth.lerp(easeOut((double)var1), var0.lines() * 0.8, 0.78);
   }

   static Vector3f toggle(int var0, float var1) {
      return new Vector3f((float)var0 * 0.3F, Mth.lerp(var1, 0.62F, -0.5F), Mth.lerp(var1, 0.02F, 0.12F));
   }

   static Vector3f bagAt(float var0, float var1) {
      float var2 = (float)easeOut((double)var0);
      return new Vector3f((float)Math.sin((double)var1 * 2.1) * 0.12F * var2, Mth.lerp(var2, -0.2F, 3.1F), Mth.lerp(var2, -0.32F, -2.4F));
   }

   static void drawBag(PoseStack var0, VertexConsumer var1, int var2, float var3, float var4) {
      Vector3f var5 = bagAt(var3, var4);
      box(var1, var0, var5.x - 0.17F, var5.y - 0.12F, var5.z - 0.2F, var5.x + 0.17F, var5.y + 0.12F, var5.z + 0.2F, 0, var2);
   }

   static void drawChuteRig(GliderRender.Spec var0, PoseStack var1, VertexConsumer var2, int var3, int var4, float var5, float var6, float var7, int var8) {
      for (byte var9 = -1; var9 <= 1; var9 += 2) {
         for (byte var10 = -1; var10 <= 1; var10 += 2) {
            float var11 = (float)var9 * 0.19F;
            float var12 = var10 > 0 ? 0.06F : -0.08F;
            box(var2, var1, var11 - 0.025F, -0.02F, var12 - 0.015F, var11 + 0.025F, 0.52F, var12 + 0.015F, 0, var3);
            box(var2, var1, var11 - 0.03F, 0.5F, var12 - 0.02F, var11 + 0.03F, 0.56F, var12 + 0.02F, 2, var3);
         }
      }

      if (!(var6 < 5.0F)) {
         float var26 = sliderY(var0, var5);
         float var27 = 0.3F;
         Matrix4f var28 = var1.last().pose();
         Pose var29 = var1.last();
         quad(
            var2,
            var28,
            var29,
            new Vector3f(-var27, var26, var27),
            new Vector3f(var27, var26, var27),
            new Vector3f(var27, var26, -var27),
            new Vector3f(-var27, var26, -var27),
            0.05F,
            0.45F,
            0.55F,
            0.95F,
            var4
         );

         for (byte var13 = -1; var13 <= 1; var13 += 2) {
            for (byte var14 = -1; var14 <= 1; var14 += 2) {
               box(
                  var2,
                  var1,
                  (float)var13 * var27 - 0.03F,
                  var26 - 0.02F,
                  (float)var14 * var27 - 0.03F,
                  (float)var13 * var27 + 0.03F,
                  var26 + 0.02F,
                  (float)var14 * var27 + 0.03F,
                  2,
                  var4
               );
            }
         }

         Vector3f var30 = point(var0, 0.0, 0.42, 1, var5, 0.0F, var7, var8, 1.0);
         Vector3f var31 = new Vector3f(
            var30.x + (float)Math.sin((double)var7 * 1.7 + (double)var8) * 0.25F, var30.y + 1.1F, var30.z - 1.9F + (float)Math.sin((double)var7 * 2.3) * 0.15F
         );
         float var15 = 0.42F;

         for (int var16 = 0; var16 < 10; var16++) {
            double var17 = (Math.PI * 2) * (double)var16 / 10.0;
            double var19 = (Math.PI * 2) * (double)(var16 + 1) / 10.0;
            Vector3f var21 = new Vector3f(var31.x + (float)Math.cos(var17) * var15, var31.y + (float)Math.sin(var17) * var15, var31.z);
            Vector3f var22 = new Vector3f(var31.x + (float)Math.cos(var19) * var15, var31.y + (float)Math.sin(var19) * var15, var31.z);
            Vector3f var23 = new Vector3f(var31.x, var31.y, var31.z - 0.32F);
            Vector3f var24 = new Vector3f(var31.x + (float)Math.cos(var17) * var15 * 0.7F, var31.y + (float)Math.sin(var17) * var15 * 0.7F, var31.z - 0.22F);
            Vector3f var25 = new Vector3f(var31.x + (float)Math.cos(var19) * var15 * 0.7F, var31.y + (float)Math.sin(var19) * var15 * 0.7F, var31.z - 0.22F);
            quad(var2, var28, var29, var21, var22, var25, var24, 0.55F, 0.95F, 0.55F, 0.95F, var4);
            quad(var2, var28, var29, var24, var25, var23, var23, 0.55F, 0.95F, 0.55F, 0.95F, var4);
         }
      }
   }

   static void drawChuteLines(GliderRender.Spec var0, PoseStack var1, VertexConsumer var2, float var3, float var4, float var5, float var6, int var7) {
      Matrix4f var8 = var1.last().pose();
      Pose var9 = var1.last();
      if (var3 <= 0.0F) {
         Vector3f var23 = bagAt(var4, var6);

         for (byte var24 = -1; var24 <= 1; var24 += 2) {
            for (byte var25 = -1; var25 <= 1; var25 += 2) {
               line(var2, var8, var9, new Vector3f((float)var24 * 0.2F, 0.55F, var25 > 0 ? 0.05F : -0.07F), var23, 226, 226, 216);
            }
         }
      } else {
         float var10 = sliderY(var0, var3);
         float var11 = 0.3F;
         double[] var12 = new double[]{0.06, 0.3, 0.56, 0.8};
         int var13 = var0.cells() + 1;

         for (byte var14 = -1; var14 <= 1; var14 += 2) {
            for (int var15 = 0; var15 < var12.length; var15++) {
               boolean var16 = var15 < 2;
               Vector3f var17 = new Vector3f((float)var14 * 0.2F, 0.55F, var16 ? 0.05F : -0.07F);
               Vector3f var18 = new Vector3f((float)var14 * var11, var10, var16 ? var11 : -var11);
               line(var2, var8, var9, var17, var18, 226, 226, 216);

               for (int var19 = 0; var19 < var13; var19++) {
                  double var20 = -1.0 + 2.0 * (double)var19 / (double)(var13 - 1);
                  if (!(var20 * (double)var14 < -1.0E-6)) {
                     line(var2, var8, var9, var18, point(var0, var20, var12[var15], -1, var3, var5, var6, var7, 0.0), 226, 226, 216);
                  }
               }
            }

            Vector3f var27 = toggle(var14, var5);
            Vector3f var29 = new Vector3f((float)var14 * 0.2F, 0.5F, -0.08F);
            Vector3f var30 = new Vector3f((float)var14 * 0.95F, var10 + (float)var0.lines() * 0.45F, -0.95F);
            line(var2, var8, var9, var27, var29, 220, 90, 36);
            line(var2, var8, var9, var29, var30, 220, 90, 36);

            for (double var21 : new double[]{0.25, 0.5, 0.75, 1.0}) {
               line(var2, var8, var9, var30, point(var0, (double)var14 * var21, 1.0, -1, var3, var5, var6, var7, 0.0), 220, 90, 36);
            }
         }

         Vector3f var26 = point(var0, 0.0, 0.42, 1, var3, 0.0F, var6, var7, 1.0);
         Vector3f var28 = new Vector3f(
            var26.x + (float)Math.sin((double)var6 * 1.7 + (double)var7) * 0.25F, var26.y + 1.1F, var26.z - 1.9F + (float)Math.sin((double)var6 * 2.3) * 0.15F
         );
         line(var2, var8, var9, var26, var28, 40, 40, 40);
      }
   }

   private static void box(VertexConsumer var0, PoseStack var1, float var2, float var3, float var4, float var5, float var6, float var7, int var8, int var9) {
      Matrix4f var10 = var1.last().pose();
      Pose var11 = var1.last();
      float var12 = (float)(var8 % 2) * 0.5F + 0.05F;
      float var13 = var12 + 0.4F;
      float var14 = (float)(var8 / 2) * 0.5F + 0.05F;
      float var15 = var14 + 0.4F;
      Vector3f[] var16 = new Vector3f[]{
         new Vector3f(var2, var3, var4),
         new Vector3f(var5, var3, var4),
         new Vector3f(var5, var6, var4),
         new Vector3f(var2, var6, var4),
         new Vector3f(var2, var3, var7),
         new Vector3f(var5, var3, var7),
         new Vector3f(var5, var6, var7),
         new Vector3f(var2, var6, var7)
      };
      int[][] var17 = new int[][]{{3, 2, 6, 7}, {4, 5, 1, 0}, {0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}};

      for (int[] var21 : var17) {
         quad(var0, var10, var11, var16[var21[0]], var16[var21[1]], var16[var21[2]], var16[var21[3]], var12, var13, var14, var15, var9);
      }
   }

   static void drawHarness(PoseStack var0, VertexConsumer var1, int var2) {
      box(var1, var0, -0.29F, 0.56F, -0.46F, 0.29F, 0.66F, 0.14F, 0, var2);
      box(var1, var0, -0.27F, 0.66F, -0.34F, 0.27F, 1.4F, -0.16F, 0, var2);
      box(var1, var0, -0.25F, 1.05F, -0.36F, 0.25F, 1.25F, -0.33F, 3, var2);

      for (byte var3 = -1; var3 <= 1; var3 += 2) {
         box(var1, var0, (float)var3 * 0.21F - 0.03F, 0.62F, -0.14F, (float)var3 * 0.21F + 0.03F, 1.42F, 0.14F, 1, var2);
         box(var1, var0, (float)var3 * 0.14F - 0.05F, 0.58F, -0.02F, (float)var3 * 0.14F + 0.05F, 0.66F, 0.16F, 1, var2);
         box(var1, var0, (float)var3 * 0.24F - 0.025F, 0.9F, 0.02F, (float)var3 * 0.24F + 0.025F, 1.02F, 0.08F, 2, var2);
      }
   }

   static void drawRisers(PoseStack var0, VertexConsumer var1, int var2, float var3) {
      if (!(var3 < 0.15F)) {
         for (byte var4 = -1; var4 <= 1; var4 += 2) {
            float var5 = (float)var4 * 0.25F;
            box(var1, var0, var5 - 0.03F, -0.3F, 0.03F, var5 + 0.03F, 0.55F, 0.07F, 0, var2);
            box(var1, var0, var5 - 0.035F, 0.5F, 0.0F, var5 + 0.035F, 0.58F, 0.1F, 2, var2);
         }
      }
   }

   static void drawLines(PoseStack var0, VertexConsumer var1, float var2, float var3, float var4, int var5) {
      if (!(var2 < 0.2F)) {
         Matrix4f var6 = var0.last().pose();
         Pose var7 = var0.last();
         double[] var8 = new double[]{0.08, 0.3, 0.55, 0.78};

         for (byte var9 = -1; var9 <= 1; var9 += 2) {
            Vector3f var10 = new Vector3f((float)var9 * 0.25F, 0.56F, 0.05F);

            for (int var11 = 0; var11 < var8.length; var11++) {
               Vector3f var12 = new Vector3f((float)var9 * 1.2F, 3.3F, 0.45F - (float)var11 * 0.42F);
               line(var1, var6, var7, var10, var12, 226, 226, 216);

               for (byte var13 = 0; var13 <= 20; var13 += 2) {
                  double var14 = (double)var9 * ((double)var13 / 20.0);
                  if (var13 != 0 || var9 >= 0) {
                     line(var1, var6, var7, var12, point(var14, var8[var11], -1, var2, var3, var4, var5, 0.0), 226, 226, 216);
                  }
               }
            }

            Vector3f var18 = new Vector3f((float)var9 * 0.34F, -0.1F - var3 * 0.35F, 0.08F);
            Vector3f var19 = new Vector3f((float)var9 * 1.8F, 3.0F, -1.3F);
            line(var1, var6, var7, var18, var19, 220, 90, 36);

            for (double var16 : new double[]{0.3, 0.5, 0.7, 0.85, 1.0}) {
               line(var1, var6, var7, var19, point((double)var9 * var16, 1.0, -1, var2, var3, var4, var5, 0.0), 220, 90, 36);
            }
         }
      }
   }

   private static void line(VertexConsumer var0, Matrix4f var1, Pose var2, Vector3f var3, Vector3f var4, int var5, int var6, int var7) {
      Vector3f var8 = new Vector3f(var4).sub(var3);
      if (!((double)var8.lengthSquared() < 1.0E-8)) {
         var8.normalize();
         var0.addVertex(var1, var3.x, var3.y, var3.z).setColor(var5, var6, var7, 255).setNormal(var2, var8.x, var8.y, var8.z);
         var0.addVertex(var1, var4.x, var4.y, var4.z).setColor(var5, var6, var7, 255).setNormal(var2, var8.x, var8.y, var8.z);
      }
   }

   static final class Look {
      double heading;
      double headingO;
      double bank;
      double bankO;
      double brake;
      double brakeO;
      int open;
      int openO;
      Vec3 last;
      GliderRender.Spec spec;
   }

   static record Spec(
      int cells,
      int chord,
      double lines,
      double theta,
      double curl,
      double c0,
      double taper,
      double sweep,
      double thick,
      double pillow,
      double anchor,
      float fill,
      ResourceLocation tex
   ) {
   }
}
