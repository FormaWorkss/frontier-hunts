package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.Weapon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;

/**
 * First-person hands on a bow, in the bow's frame (before the right-hand mirror of the bow model).
 *
 * <p>[archery2] Right-handed archer: the left fist closes on the grip under the arrow shelf, the right hand holds the
 * string at the arrow's nock (on the arrow line, left of the riser) and comes back to the anchor at the face as the
 * draw completes. Crossbow: right hand on the pistol grip and left hand under the fore-end, both arms falling away
 * below and outside the sight line (they used to sit right over the centre of the view).
 */
final class FieldBowHands {
   static void draw(Weapon var0, float var1, PoseStack var2, MultiBufferSource var3, int var4) {
      draw(var0, false, var1, 0.0F, var2, var3, var4);
   }

   static void draw(Weapon var0, float var1, float var2, PoseStack var3, MultiBufferSource var4, int var5) {
      draw(var0, false, var1, var2, var3, var4, var5);
   }

   static void draw(Weapon weapon, boolean fieldBow, float draw, float aim, PoseStack pose, MultiBufferSource buffers, int light) {
      if (FieldPlayerArms.testSink == null) {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player == null) {
            return;
         }
      }
      if (weapon == Weapon.CROSSBOW) {
         // right hand round the pistol grip, forearm down and back to the elbow at the side
         FieldPlayerArms.reach(pose, buffers, light, HumanoidArm.RIGHT, 0.004, -0.112, 0.232, 0.17, -0.42, 0.42, 8.0F, ARM_GIRTH);
         if (FieldPlayerArms.testSink != null || Minecraft.getInstance().player.getOffhandItem().isEmpty()) {
            // left hand cupping the fore-end from below, elbow down and out to the left
            FieldPlayerArms.reach(pose, buffers, light, HumanoidArm.LEFT, -0.004, -0.108, -0.13, -0.16, -0.46, 0.12, -12.0F, ARM_GIRTH);
         }
         return;
      }
      if (weapon == Weapon.HUNTING_SPEAR) {
         FieldPlayerArms.wrist(pose, buffers, light, HumanoidArm.RIGHT, 0.025, -0.075, 0.025, -64.0F, 30.0F, 4.0F);
         return;
      }
      if (weapon == Weapon.BOWFISHING_BOW) {
         // unchanged bowfishing hold (BowfishingClient draws that bow itself)
         FieldPlayerArms.wrist(pose, buffers, light, HumanoidArm.LEFT, -0.025, -0.075, 0.025, -64.0F, -30.0F, -4.0F);
         float k = Math.min(1.0F, draw / 0.2F);
         k = k * k * (3.0F - 2.0F * k);
         FieldPlayerArms.wrist(
            pose, buffers, light, HumanoidArm.RIGHT, 0.04 + 0.1 * (double)(1.0F - k), 0.045 - 0.2 * (double)(1.0F - k), FieldBows.nockZ(draw), -58.0F, 42.0F, 5.0F
         );
         return;
      }
      boolean mirrored = FieldBowPresentation.mirrored(weapon);
      double ax = FieldBowPresentation.arrowX(weapon, fieldBow) * (mirrored ? -1.0 : 1.0);
      double ay = FieldBowPresentation.arrowY(weapon, fieldBow);
      // bow hand: the left fist round the grip just under the arrow shelf, the arm reaching back to the left shoulder
      FieldPlayerArms.reach(pose, buffers, light, HumanoidArm.LEFT, 0.0, ay - 0.09, 0.004, -0.3, -0.22, 0.64, -6.0F, ARM_GIRTH);
      // string hand: comes up from the hip to the nock as the draw starts, then rides back with the string to the anchor
      float k = Math.min(1.0F, draw / 0.2F);
      k = k * k * (3.0F - 2.0F * k);
      double nock = FieldBowPresentation.nockZ(weapon, fieldBow, draw);
      double hx = ax + 0.028 + 0.1 * (double)(1.0F - k);
      double hy = ay - 0.022 - 0.2 * (double)(1.0F - k);
      double hz = nock + 0.03;
      FieldPlayerArms.reach(pose, buffers, light, HumanoidArm.RIGHT, hx, hy, hz, hx + 0.32, hy - 0.06, hz + 0.3, 70.0F, ARM_GIRTH);
   }

   /** [archery2] First-person arm thickness on bows (the vanilla 4-pixel arm is ~25 cm thick at this distance). */
   static final float ARM_GIRTH = 0.56F;

   private FieldBowHands() {
   }
}
