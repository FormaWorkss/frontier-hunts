package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.expedition.WeaponAction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

final class FieldWeaponHands {
   static void draw(Weapon var0, ItemStack var1, PoseStack var2, MultiBufferSource var3, int var4, double var5) {
      draw(var0, var1, var2, var3, var4, var5, 1.0F);
   }

   static void draw(Weapon var0, ItemStack var1, PoseStack var2, MultiBufferSource var3, int var4, double var5, float var7) {
      Minecraft var8 = Minecraft.getInstance();
      if (var8.player != null) {
         HumanoidArm var9 = var8.player.getMainArm();
         float var10 = var9 == HumanoidArm.RIGHT ? 1.0F : -1.0F;
         boolean var11 = var0 == Weapon.FIELD_PISTOL || var0 == Weapon.REVOLVER || var0 == Weapon.FLARE_GUN;
         float var12 = FieldWeaponMesh.reloadProgress(var1, var5);
         float var13 = FieldWeaponMotion.cycle(var0, var1, var5);
         double var14 = FieldWeaponSockets.gripY(var0) * (double)var7;
         double var16 = FieldWeaponSockets.gripZ(var0) * (double)var7;
         if (var0 == Weapon.LEVER_RIFLE) {
            double var18 = FieldWeaponSockets.leverY(var0) * (double)var7;
            double var20 = FieldWeaponSockets.leverZ(var0) * (double)var7;
            double var22 = Math.toRadians((double)(var13 * 48.0F));
            double var24 = var14 - var18;
            double var26 = var16 - var20;
            var14 = var18 + var24 * Math.cos(var22) - var26 * Math.sin(var22);
            var16 = var20 + var24 * Math.sin(var22) + var26 * Math.cos(var22);
         }

         FieldPlayerArms.wrist(
            var2,
            var3,
            var4,
            var9,
            (double)var10 * 0.025,
            var14,
            var16,
            -52.0F + var13 * (float)(var0 == Weapon.LEVER_RIFLE ? 48 : 0),
            var10 * 38.0F,
            var10 * 4.0F
         );
         if (var8.player.getOffhandItem().isEmpty()) {
            HumanoidArm var37 = var9 == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
            double var19 = (double)(-var10) * 0.035;
            double var21 = var11 ? var14 - 0.012 : FieldWeaponSockets.supportY(var0) * (double)var7 - 0.115;
            double var23 = var11 ? var16 + 0.004 : FieldWeaponSockets.supportZ(var0) * (double)var7;
            float var25 = 0.0F;
            if (var0 == Weapon.PUMP_SHOTGUN) {
               var23 += (double)(var13 * 0.075F * var7);
            }

            if (var12 > 0.0F) {
               float var38 = FieldWeaponMesh.smooth((double)var12 / 0.18) * (1.0F - FieldWeaponMesh.smooth(((double)var12 - 0.76) / 0.22));
               double var27 = (double)(-var10) * 0.02;
               double var29 = -0.32;
               double var31 = 0.065;
               if (WeaponAction.reload(var0) == WeaponAction.Reload.TUBE) {
                  // [guns3] each round: the hand brings it up from below (or from the side for the lever gun's
                  // gate) and pushes it into the loading port, then drops for the next one
                  CompoundTag var41 = ExpeditionWeapon.data(var1);
                  double var42 = var5 - (double)var41.getLong("reload_started");
                  float var36 = FieldWeaponMesh.window((var42 - 12.0) % 17.0, 0.0, 9.0, 17.0);
                  double[] port = FieldWeaponSockets.loadingPort(var0);
                  double sx = port[0] * (double)var7 + (double)(1.0F - var36) * port[3];
                  double sy = port[1] * (double)var7 - (double)(1.0F - var36) * 0.075;
                  double sz = port[2] * (double)var7 + (double)(1.0F - var36) * 0.03;
                  var27 = (double)var10 * (sx + (var0 == Weapon.LEVER_RIFLE ? 0.03 : -0.005));
                  var29 = sy - 0.085;
                  var31 = sz + 0.04;
                  if (var42 > 12.0 && var5 < (double)(var41.getLong("reload_until") - 9L) && (double)var36 > 0.15) {
                     var2.pushPose();
                     var2.translate((double)var10 * sx, sy, sz);
                     // [gear21] nose first into the tube (ammo meshes point along +y)
                     var2.mulPose(Axis.XP.rotationDegrees(-90.0F));
                     var2.scale(0.45F, 0.45F, 0.45F);
                     FieldSupplyMesh.draw(var0.ammo, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, var2, var3, var4);
                     var2.popPose();
                  }
               } else {
                  // [gear21] break actions, the revolver and the single-shot guns: the rounds really go into the
                  // chambers (and the empties come out) - see ReloadRounds; the support hand follows the round
                  ReloadRounds.Frame rf = ReloadRounds.frame(var0, var12);
                  for (ReloadRounds.Round r : rf.rounds()) {
                     var2.pushPose();
                     var2.translate((double)var10 * r.x() * (double)var7, r.y() * (double)var7, r.z() * (double)var7);
                     var2.mulPose(Axis.XP.rotationDegrees(r.pitch()));
                     var2.mulPose(Axis.XP.rotationDegrees(-90.0F));
                     float sc = r.scale() * var7;
                     var2.scale(sc, sc, sc);
                     FieldSupplyMesh.draw(var0.ammo, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, var2, var3, var4);
                     var2.popPose();
                  }
                  if (rf.hand() != null) {
                     ReloadRounds.Hand h = rf.hand();
                     var27 = (double)var10 * h.x() * (double)var7;
                     var29 = h.y() * (double)var7;
                     var31 = h.z() * (double)var7;
                     if (WeaponAction.reload(var0) == WeaponAction.Reload.CYLINDER) {
                        var25 = 28.0F * var38;
                     }
                  } else if (WeaponAction.reload(var0) == WeaponAction.Reload.CYLINDER) {
                     var27 = (double)(-var10) * 0.13;
                     var29 = -0.08;
                     var31 = 0.025;
                     var25 = 28.0F * var38;
                  } else if (WeaponAction.reload(var0) == WeaponAction.Reload.SINGLE) {
                     var27 = (double)var10 * 0.1;
                     var29 = -0.1;
                     var31 = -0.035;
                  }
               }

               var19 += (var27 - var19) * (double)var38;
               var21 += (var29 - var21) * (double)var38;
               var23 += (var31 - var23) * (double)var38;
            }

            // during a draw flourish the support hand drops away so the gun being shown off stays in view
            float away = DrawAnimation.supportAway();
            if (away > 0.0F) {
               var19 += ((double)(-var10) * 0.16 - var19) * (double)away;
               var21 += -0.62 * (double)away;
               var23 += 0.12 * (double)away;
            }

            FieldPlayerArms.wrist(var2, var3, var4, var37, var19, var21, var23, -52.0F + 30.0F * away, var10 * (-48.0F + var25 + 20.0F * away), var10 * -5.0F);
         }
      }
   }

   private FieldWeaponHands() {
   }
}
