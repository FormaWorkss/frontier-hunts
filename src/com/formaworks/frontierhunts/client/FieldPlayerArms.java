package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin.Model;
import net.minecraft.world.entity.HumanoidArm;

final class FieldPlayerArms {
   /** [archery2] Offline view harness only (tools/archery2): draws a stand-in arm box instead of the player's skin. Null in game. */
   interface Sink {
      void arm(PoseStack pose, boolean right, boolean slim);
   }

   static Sink testSink;

   static void wrist(
      PoseStack var0, MultiBufferSource var1, int var2, HumanoidArm var3, double var4, double var6, double var8, float var10, float var11, float var12
   ) {
      wrist(var0, var1, var2, var3, var4, var6, var8, var10, var11, var12, 1.0F);
   }

   /**
    * [archery2] An arm whose hand is at (hx, hy, hz) and whose length runs toward (tx, ty, tz) (the elbow or shoulder it
    * leads to), in the current frame. {@code roll} turns the hand about the forearm (degrees); {@code girth} scales the
    * arm's thickness about its own axis (1 = the vanilla 4-pixel arm, which at bow distance blots out half the view).
    */
   static void reach(
      PoseStack pose, MultiBufferSource buffers, int light, HumanoidArm arm, double hx, double hy, double hz, double tx, double ty, double tz,
      float roll, float girth
   ) {
      double dx = tx - hx;
      double dy = ty - hy;
      double dz = tz - hz;
      double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (len < 1.0E-6) {
         return;
      }
      dx /= len;
      dy /= len;
      dz /= len;
      // wrist() turns the arm's -y axis by YP(a) ZP(c) XP(b): solve b and a for that axis to point along d
      double c = Math.toRadians(roll);
      double cosB = Math.max(-1.0, Math.min(1.0, -dy / Math.max(1.0E-3, Math.cos(c))));
      double b = -Math.acos(cosB);
      double x1 = Math.cos(b) * Math.sin(c);
      double z1 = -Math.sin(b);
      double a = Math.atan2(dx, dz) - Math.atan2(x1, z1);
      wrist(pose, buffers, light, arm, hx, hy, hz, (float)Math.toDegrees(b), (float)Math.toDegrees(a), roll, girth);
   }

   static void wrist(
      PoseStack var0, MultiBufferSource var1, int var2, HumanoidArm var3, double var4, double var6, double var8, float var10, float var11, float var12,
      float girth
   ) {
      if (testSink != null) { // [archery2] harness path: same transforms as below, no Minecraft instance
         boolean right = var3 == HumanoidArm.RIGHT;
         var0.pushPose();
         var0.translate(var4, var6, var8);
         var0.mulPose(Axis.YP.rotationDegrees(var11));
         var0.mulPose(Axis.ZP.rotationDegrees(var12));
         var0.mulPose(Axis.XP.rotationDegrees(var10));
         var0.translate((double)(right ? 1 : -1) * 6.0 / 16.0, -0.71875, 0.0);
         girth(var0, right, false, girth);
         testSink.arm(var0, right, false);
         var0.popPose();
         return;
      }
      Minecraft var13 = Minecraft.getInstance();
      if (var13.player != null && !var13.player.isInvisible()) {
         if (var13.getEntityRenderDispatcher().getRenderer(var13.player) instanceof PlayerRenderer var14) {
            boolean var17 = var3 == HumanoidArm.RIGHT;
            boolean var16 = var13.player.getSkin().model() == Model.SLIM;
            var0.pushPose();
            var0.translate(var4, var6, var8);
            var0.mulPose(Axis.YP.rotationDegrees(var11));
            var0.mulPose(Axis.ZP.rotationDegrees(var12));
            var0.mulPose(Axis.XP.rotationDegrees(var10));
            var0.translate((double)(var17 ? 1 : -1) * (var16 ? 5.5 : 6.0) / 16.0, -0.71875, 0.0);
            girth(var0, var17, var16, girth);
            if (var17) {
               var14.renderRightHand(var0, var1, var2, var13.player);
            } else {
               var14.renderLeftHand(var0, var1, var2, var13.player);
            }

            var0.popPose();
         }
      }
   }

   /** [archery2] Thin the arm about its long axis (the arm model's pivot column) without moving the hand. */
   private static void girth(PoseStack pose, boolean right, boolean slim, float girth) {
      if (girth == 1.0F) {
         return;
      }
      // PlayerModel arm cube centre column: x = -+5/16 -+ (slim ? 0.5 : 1)/16 ... measured from the pivot frame used here
      double cx = (right ? -1.0 : 1.0) * (slim ? 5.5 : 6.0) / 16.0;
      pose.translate(cx, 0.0, 0.0);
      pose.scale(girth, 1.0F, girth);
      pose.translate(-cx, 0.0, 0.0);
   }

   private FieldPlayerArms() {
   }
}
