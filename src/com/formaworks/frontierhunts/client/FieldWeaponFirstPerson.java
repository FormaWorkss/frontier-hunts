package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.expedition.AttachmentSpec;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.expedition.WeaponAction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import org.joml.Matrix4f;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class FieldWeaponFirstPerson {
   private static float aim;
   private static float oldAim;
   private static float equip;
   private static float oldEquip;
   private static int equipTicks = 13;
   private static float sprint;
   private static float oldSprint;
   private static int slot = -1;
   private static Item item;
   private static float back;
   private static float backV;
   private static float backO;
   private static float rise;
   private static float riseV;
   private static float riseO;
   private static float side;
   private static float sideV;
   private static float sideO;
   private static float roll;
   private static float rollV;
   private static float rollO;
   private static float swayX;
   private static float swayY;
   private static float swayXO;
   private static float swayYO;
   private static float lastYaw = Float.NaN;
   private static float lastPitch = Float.NaN;
   private static final Random RANDOM = new Random();

   static void impulse(Weapon var0, ItemStack var1) {
      boolean var2 = ExpeditionClient.aiming();
      float var3 = ExpeditionWeapon.attachment(var1, "muzzle_brake") ? 0.72F : 1.0F;
      float var4 = (var2 ? 0.75F : 1.0F) * var3 * (HuntConfig.REDUCED_MOTION.get() ? 0.35F : 1.0F)
         * (com.formaworks.frontierhunts.sticks.ShootingSticks.rested(Minecraft.getInstance().player) ? 0.6F : 1.0F); // [sticks]
      backV = backV + FieldWeaponMotion.travel(var0) * 1.9F * var4;
      riseV = riseV + FieldWeaponMotion.rise(var0) * 1.25F * var4;
      sideV = sideV + (RANDOM.nextFloat() - 0.5F) * FieldWeaponMotion.rise(var0) * 0.55F * var4;
      rollV = rollV + (RANDOM.nextFloat() - 0.35F) * FieldWeaponMotion.rise(var0) * 0.9F * var4;
   }

   private static float spring(float var0, float[] var1, float var2, float var3) {
      var1[0] += -var0 * var2;
      var1[0] *= var3;
      return var0 + var1[0];
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      oldAim = aim;
      oldEquip = equip;
      oldSprint = sprint;
      backO = back;
      riseO = rise;
      sideO = side;
      rollO = roll;
      swayXO = swayX;
      swayYO = swayY;
      if (var1.player == null || var1.level == null || !(var1.player.getMainHandItem().getItem() instanceof ExpeditionWeapon var2) || var2.weapon.bow) {
         oldAim = 0.0F;
         aim = 0.0F;
         oldEquip = 1.0F;
         equip = 1.0F;
         oldSprint = 0.0F;
         sprint = 0.0F;
         slot = -1;
         item = null;
         roll = 0.0F;
         side = 0.0F;
         rise = 0.0F;
         back = 0.0F;
         rollV = 0.0F;
         sideV = 0.0F;
         riseV = 0.0F;
         backV = 0.0F;
      } else if (!var1.isPaused()) {
         if (slot != var1.player.getInventory().selected || item != var2) {
            oldEquip = 1.0F;
            equip = 1.0F;
            oldAim = 0.0F;
            aim = 0.0F;
            slot = var1.player.getInventory().selected;
            item = var2;
            equipTicks = Math.max(4, DrawAnimation.ticks(var2.weapon));
         }

         equip = Math.max(0.0F, equip - 1.0F / (float)equipTicks);
         float var11 = ExpeditionClient.aiming() ? 1.0F : 0.0F;
         aim = aim + Math.clamp(var11 - aim, -0.16F, 0.16F);
         float var4 = var1.player.isSprinting() && var11 == 0.0F ? 1.0F : 0.0F;
         sprint = sprint + Math.clamp(var4 - sprint, -0.2F, 0.2F);
         float[] var5 = new float[]{backV};
         back = spring(back, var5, 0.55F, 0.48F);
         backV = var5[0];
         var5[0] = riseV;
         rise = spring(rise, var5, 0.4F, 0.55F);
         riseV = var5[0];
         var5[0] = sideV;
         side = spring(side, var5, 0.38F, 0.55F);
         sideV = var5[0];
         var5[0] = rollV;
         roll = spring(roll, var5, 0.34F, 0.58F);
         rollV = var5[0];
         float var6 = var1.player.getYRot();
         float var7 = var1.player.getXRot();
         if (!Float.isNaN(lastYaw)) {
            float var8 = Mth.wrapDegrees(var6 - lastYaw);
            float var9 = var7 - lastPitch;
            float var10 = var11 > 0.0F ? 0.6F : 2.4F;
            swayX = Mth.clamp(swayX * 0.62F - var8 * 0.1F, -var10, var10);
            swayY = Mth.clamp(swayY * 0.62F - var9 * 0.1F, -var10, var10);
         }

         lastYaw = var6;
         lastPitch = var7;
      }
   }

   static float aim(float var0) {
      return ease(oldAim + (aim - oldAim) * var0);
   }

   private static float ease(float var0) {
      var0 = Math.clamp(var0, 0.0F, 1.0F);
      return var0 * var0 * (3.0F - 2.0F * var0);
   }

   @SubscribeEvent
   public static void hand(RenderHandEvent var0) {
      FieldWeaponMesh.clearDraw();
      Minecraft var1 = Minecraft.getInstance();
      if (var1.player != null
         && var1.level != null
         && var1.player.getMainHandItem().getItem() instanceof ExpeditionWeapon var2
         && !var2.weapon.bow
         && var0.getHand() == InteractionHand.MAIN_HAND) {
         var0.setCanceled(true);
         float var58 = var0.getPartialTick();
         ItemStack var4 = var1.player.getMainHandItem();
         Weapon var5 = var2.weapon;
         double var6 = (double)((float)var1.level.getGameTime() + var58);
         float var8 = aim(var58);
         float var9 = ease(oldEquip + (equip - oldEquip) * var58);
         float var10 = FieldWeaponMesh.reloadProgress(var4, var6);
         float var11 = ease(oldSprint + (sprint - oldSprint) * var58) * (1.0F - var8);
         boolean var12 = var5 == Weapon.TRANQUILIZER_RIFLE && AttachmentSpec.installedSight(var4).isEmpty()
            || AttachmentSpec.magnification(var4, var5) >= 4.0F
            || AttachmentSpec.installedSight(var4).equals("two_power_prism");
         if (!(var8 > 0.985F) || !var12) {
            boolean var13 = var5 == Weapon.FIELD_PISTOL || var5 == Weapon.REVOLVER || var5 == Weapon.FLARE_GUN;
            float var14 = var1.player.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            String var15 = AttachmentSpec.installedSight(var4);
            double var16 = FieldWeaponSockets.opticalAxisY(var5, var15);
            boolean var18 = (Boolean)HuntConfig.REDUCED_MOTION.get();
            float var19 = Mth.lerp(var58, backO, back);
            float var20 = Mth.lerp(var58, riseO, rise);
            float var21 = Mth.lerp(var58, sideO, side);
            float var22 = Mth.lerp(var58, rollO, roll);
            float var23 = Mth.lerp(var58, swayXO, swayX);
            float var24 = Mth.lerp(var58, swayYO, swayY);
            double var25 = (double)((float)var1.player.tickCount + var58) / 20.0;
            float var27 = var18 ? 0.0F : 1.0F - var8 * 0.8F;
            double var28 = Math.sin(var25 * 1.1) * 0.0035 * (double)var27;
            double var30 = Math.sin(var25 * 2.2) * 0.0028 * (double)var27;
            WeaponAction.Reload var32 = WeaponAction.reload(var5);
            float var33 = (float)Math.sin((double)var10 * Math.PI);
            float var34 = 0.0F;
            float var35 = 0.0F;
            float var36 = 0.0F;
            float var37 = 0.0F;
            float reloadSide = 0.0F;
            if (var10 > 0.0F) {
               switch (var32) {
                  case MAGAZINE:
                     float var38 = FieldWeaponMesh.window((double)var10, 0.0, 0.18, 0.62);
                     float var39 = FieldWeaponMesh.window((double)var10, 0.55, 0.7, 0.9);
                     var35 = -22.0F * var38 - 10.0F * var39;
                     var34 = 8.0F * var38 + 14.0F * var39;
                     var36 = 0.05F * var38;
                     break;
                  case TUBE:
                     // [guns3] loading a tube: the gun comes up and over so its loading port faces you and the
                     // rounds going in are in view (it used to roll away with the hand below the screen)
                     if (var5 == Weapon.LEVER_RIFLE) {
                        var35 = -12.0F * var33;
                        var34 = 8.0F * var33;
                        var36 = -0.08F * var33;
                        var37 = -28.0F * var33;
                        reloadSide = -0.05F * var33;
                     } else {
                        var35 = -52.0F * var33;
                        var34 = 16.0F * var33;
                        var36 = -0.11F * var33;
                        var37 = -5.0F * var33;
                        reloadSide = -0.08F * var33;
                     }
                     break;
                  case BREAK:
                     var34 = -26.0F * var33;
                     var35 = -8.0F * var33;
                     var36 = 0.06F * var33;
                     break;
                  case CYLINDER:
                     float var40 = FieldWeaponMesh.window((double)var10, 0.0, 0.2, 0.9);
                     var35 = 28.0F * var40;
                     var34 = 18.0F * var40;
                     var36 = 0.05F * var40;
                     break;
                  case SINGLE:
                     var35 = -18.0F * var33;
                     var34 = 6.0F * var33;
                     var36 = 0.035F * var33;
               }
            }

            float var60 = DrawAnimation.progress(var1.player, var5, var6);
            var9 = 1.0F - var60;
            float[] var61 = DrawAnimation.pose(var5, var60);
            FieldWeaponMesh.drawLever = DrawAnimation.lever(var5, var60);
            FieldWeaponMesh.drawSlide = DrawAnimation.slide(var5, var60);
            FieldWeaponMesh.drawPump = DrawAnimation.pump(var5, var60);
            FieldWeaponMesh.drawBreak = DrawAnimation.breakOpen(var5, var60);
            FieldWeaponMesh.drawCylinder = DrawAnimation.cylinderSpin(var5, var60);
            FieldWeaponMesh.drawHammer = DrawAnimation.hammer(var5, var60);
            PoseStack var62 = var0.getPoseStack();
            var62.pushPose();
            Matrix4f var41 = new Matrix4f(var62.last().pose());
            float var42 = var13 ? 1.22F : 1.35F;
            double var43 = (double)var14 * (var13 ? 0.235 : 0.26);
            double var45 = var13 ? -0.3 : -0.305;
            double var47 = var13 ? -0.66 : -0.6;
            double var49 = var43 * (double)(1.0F - var8) + (double)(var14 * reloadSide) + (double)(var14 * var11) * 0.05 + (double)var23 * 0.012 * (1.0 - (double)var8 * 0.7) + var28;
            double var51 = var45 * (double)(1.0F - var8)
               - var16 * (double)var42 * (double)var8
               + (double)var61[1]
               - (double)var36
               - (double)var11 * 0.07
               + (double)var24 * 0.012 * (1.0 - (double)var8 * 0.7)
               + var30;
            double var53 = var47 * (double)(1.0F - var8)
               - FieldWeaponSockets.eyeZ(var5, var15) * (double)var42 * (double)var8
               + (double)(var19 * var42)
               + (double)var11 * 0.04;
            // [sticks] rested on shooting sticks: the gun lies in the yoke (lower right, head off the stock) until the sight comes up
            float sticksRest = com.formaworks.frontierhunts.sticks.client.SticksView.rest(var58);
            float sticksTilt = 0.0F;
            float sticksYaw = 0.0F;
            float sticksRoll = 0.0F;
            if (sticksRest > 0.0F) {
               double eyeZ = FieldWeaponSockets.eyeZ(var5, var15) * (double)var42;
               double bx = var43 * (double)(1.0F - var8);
               double by = var45 * (double)(1.0F - var8) - var16 * (double)var42 * (double)var8;
               double bz = var47 * (double)(1.0F - var8) - eyeZ * (double)var8;
               com.formaworks.frontierhunts.sticks.client.SticksView.pose(sticksRest, var8, var14, bx, by, bz, var16 * (double)var42, eyeZ);
               double sdx = com.formaworks.frontierhunts.sticks.client.SticksView.dx;
               double sdy = com.formaworks.frontierhunts.sticks.client.SticksView.dy;
               double sdz = com.formaworks.frontierhunts.sticks.client.SticksView.dz;
               var49 += sdx - (var28 + (double)var23 * 0.012 * (1.0 - (double)var8 * 0.7)) * sticksRest;
               var51 += sdy - (var30 + (double)var24 * 0.012 * (1.0 - (double)var8 * 0.7)) * sticksRest;
               var53 += sdz;
               sticksTilt = com.formaworks.frontierhunts.sticks.client.SticksView.tilt;
               sticksYaw = com.formaworks.frontierhunts.sticks.client.SticksView.yaw;
               sticksRoll = com.formaworks.frontierhunts.sticks.client.SticksView.roll;
               // the forend's underside (pistols rest on the frame under the barrel)
               double cy = var13 ? FieldWeaponSockets.muzzleY(var5) - 0.036 : FieldWeaponSockets.supportY(var5) - 0.004;
               double cz = var13 ? FieldWeaponSockets.muzzleZ(var5) * 0.55 : FieldWeaponSockets.supportZ(var5) - 0.095;
               com.formaworks.frontierhunts.sticks.client.SticksView.anchor(bx + sdx, by + sdy, bz + sdz, cy, cz, var42, var14, -3.0F * (1.0F - var8));
            }
            var62.translate(var49 + (double)(var14 * var61[0]), var51, var53 + (double)var61[2]);
            var62.mulPose(Axis.XP.rotationDegrees(var20 * (1.0F - 0.15F * var8) + var61[3] + var34 - var11 * (float)(var13 ? 38 : 22) + var24 * 0.6F * (1.0F - sticksRest) + sticksTilt));
            var62.mulPose(Axis.YP.rotationDegrees(var14 * (var21 + var37 + var61[4] + var11 * (float)(var13 ? 8 : 34) + sticksYaw) + var23 * 0.6F * (1.0F - sticksRest)));
            var62.mulPose(Axis.ZP.rotationDegrees(var14 * (-3.0F * (1.0F - var8) + sticksRoll + var35 + var22 + var61[5] - var11 * 10.0F)));
            var62.pushPose();
            if (var14 < 0.0F) {
               var62.scale(-1.0F, 1.0F, 1.0F);
            }

            var62.scale(var42, var42, var42);
            FieldGunEffects.gunFrame(var41, var62.last().pose());
            FilteredFieldTexture.ensure(FieldMaterials.ATLAS);
            FieldWeaponMesh.draw(var5, var4, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, var62, var0.getMultiBufferSource(), var0.getPackedLight(), var6);
            var62.popPose();
            FieldWeaponMesh.localView = true;

            try {
               FieldWeaponHands.draw(var5, var4, var62, var0.getMultiBufferSource(), var0.getPackedLight(), var6, var42);
            } finally {
               FieldWeaponMesh.localView = false;
            }

            FieldWeaponMesh.clearDraw();
            var62.popPose();
            FieldGunEffects.drawViewCasings(var62, var0.getMultiBufferSource(), var0.getPackedLight(), var58);
         }
      }
   }

   private FieldWeaponFirstPerson() {
   }
}
