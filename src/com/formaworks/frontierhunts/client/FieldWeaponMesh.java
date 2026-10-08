package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.expedition.AttachmentSpec;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.expedition.WeaponAction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

final class FieldWeaponMesh {
   static final ResourceLocation TEXTURE = FrontierHunts.id("textures/item/field_equipment.png");
   static final ResourceLocation GUNS = FrontierHunts.id("textures/item/firearms_v2.png");
   /** [gunsmith] Minecraft-styled material atlas (16 px cells, same layout) for the Vanilla look. */
   static final ResourceLocation GUNS_MC = FrontierHunts.id("textures/item/firearms_mc.png");

   /** [gunsmith] Material atlas for firearms, attachments and optics: Vanilla world look -> MC-styled, else Ultra materials. */
   static ResourceLocation guns() {
      return FrontierGraphics.realisticWorld() ? GUNS : GUNS_MC;
   }
   static String lod = "close";
   static float drawLever;
   static float drawSlide;
   static float drawPump;
   static float drawBreak;
   static float drawCylinder;
   static float drawHammer;
   static float drawCrane;
   static boolean localView;
   private static final Map<String, List<FieldWeaponMesh.Part>> CACHE = new HashMap<>();
   private static final Vector3f POINT = new Vector3f();
   private static final Vector3f NORMAL = new Vector3f();
   static final float PUMP_TRAVEL = 0.075F;

   static void clearDraw() {
      drawHammer = 0.0F;
      drawCrane = 0.0F;
      drawCylinder = 0.0F;
      drawBreak = 0.0F;
      drawPump = 0.0F;
      drawSlide = 0.0F;
      drawLever = 0.0F;
      DrawAnimation.supportAway = 0.0F;
   }

   static void part(String var0, PoseStack var1, MultiBufferSource var2, int var3) {
      drawStatic(var0 + "_" + lod, guns(), var1, var2, var3); // [gunsmith] per-look atlas
   }

   static void clear() {
      CACHE.clear();
   }

   private static List<FieldWeaponMesh.Part> load(String var0) {
      return CACHE.computeIfAbsent(
         var0,
         var0x -> {
            try {
               List var1;
               try (DataInputStream var2 = new DataInputStream(
                     Minecraft.getInstance().getResourceManager().getResourceOrThrow(FrontierHunts.id("models/equipment/" + var0x + ".fheq")).open()
                  )) {
                  if (var2.readInt() != 1179141457 || var2.readInt() != 1) {
                     throw new IOException("Equipment header");
                  }

                  int var3 = var2.readInt();
                  if (var3 < 1 || var3 > 16) {
                     throw new IOException("Equipment part budget");
                  }

                  ArrayList var4 = new ArrayList();

                  for (int var5 = 0; var5 < var3; var5++) {
                     int var6 = var2.readInt();
                     int var7 = var2.readInt();
                     int var8 = var2.readInt();
                     if (var6 < 0 || var6 > 15 || var7 < 1 || var7 > 100000 || var8 < 1 || var8 > 40000) {
                        throw new IOException("Equipment mesh budget");
                     }

                     float[] var9 = new float[var7 * 8];
                     int[] var10 = new int[var7];
                     int[] var11 = new int[var8 * 3];

                     for (int var12 = 0; var12 < var7; var12++) {
                        for (int var13 = 0; var13 < 8; var13++) {
                           float var14 = var2.readFloat();
                           if (!Float.isFinite(var14)) {
                              throw new IOException("Equipment vertex");
                           }

                           var9[var12 * 8 + var13] = var14;
                        }

                        var10[var12] = var2.readInt();
                     }

                     for (int var18 = 0; var18 < var11.length; var18++) {
                        var11[var18] = var2.readInt();
                        if (var11[var18] < 0 || var11[var18] >= var7) {
                           throw new IOException("Equipment index");
                        }
                     }

                     var4.add(new FieldWeaponMesh.Part(var6, var9, var10, var11, new float[var7 * 6]));
                  }

                  if (var2.read() != -1) {
                     throw new IOException("Equipment trailing data");
                  }

                  var1 = List.copyOf(var4);
               }

               return var1;
            } catch (IOException var17) {
               throw new IllegalStateException("Cannot load original equipment " + var0x, var17);
            }
         }
      );
   }

   static float smooth(double var0) {
      float var2 = (float)Math.clamp(var0, 0.0, 1.0);
      return var2 * var2 * (3.0F - 2.0F * var2);
   }

   static void drawStatic(String var0, ResourceLocation var1, PoseStack var2, MultiBufferSource var3, int var4) {
      FilteredFieldTexture.ensure(var1);
      VertexConsumer var5 = var3.getBuffer(HuntRenderTypes.supplied(var1));
      Pose var6 = var2.last();

      for (FieldWeaponMesh.Part var8 : load(var0)) {
         emit(var8, var6, var5, var4);
      }
   }

   /** [1.1.0] one part of an equipment mesh (boats: hull, oars, motor, propeller), with the per-look material atlas */
   static void drawPart(String name, int part, PoseStack pose, MultiBufferSource buffers, int light) {
      ResourceLocation tex = guns();
      FilteredFieldTexture.ensure(tex);
      VertexConsumer vc = buffers.getBuffer(HuntRenderTypes.supplied(tex));
      for (FieldWeaponMesh.Part p : load(name)) {
         if (p.id == part) {
            emit(p, pose.last(), vc, light);
         }
      }
   }

   /** [1.1.0] a mesh's triangles as bare positions (the boats' water masks, drawn depth-only) */
   static void drawPositions(String name, PoseStack pose, VertexConsumer vc) {
      Pose last = pose.last();
      for (FieldWeaponMesh.Part p : load(name)) {
         for (int i : p.indices) {
            vc.addVertex(last, p.vertices[i * 8], p.vertices[i * 8 + 1], p.vertices[i * 8 + 2]);
         }
      }
   }

   private static void emit(FieldWeaponMesh.Part var0, Pose var1, VertexConsumer var2, int var3) {
      float[] var4 = var0.vertices;
      float[] var5 = var0.posed;

      for (int var6 = 0; var6 < var0.colors.length; var6++) {
         int var7 = var6 * 8;
         int var8 = var6 * 6;
         var1.pose().transformPosition(POINT.set(var4[var7], var4[var7 + 1], var4[var7 + 2]));
         var1.transformNormal(var4[var7 + 3], var4[var7 + 4], var4[var7 + 5], NORMAL);
         var5[var8] = POINT.x;
         var5[var8 + 1] = POINT.y;
         var5[var8 + 2] = POINT.z;
         var5[var8 + 3] = NORMAL.x;
         var5[var8 + 4] = NORMAL.y;
         var5[var8 + 5] = NORMAL.z;
      }

      for (int var9 : var0.indices) {
         int var10 = var9 * 8;
         int var11 = var9 * 6;
         var2.addVertex(
            var5[var11],
            var5[var11 + 1],
            var5[var11 + 2],
            0xFF000000 | var0.colors[var9],
            var4[var10 + 6],
            var4[var10 + 7],
            OverlayTexture.NO_OVERLAY,
            var3,
            var5[var11 + 3],
            var5[var11 + 4],
            var5[var11 + 5]
         );
      }
   }

   static float window(double var0, double var2, double var4, double var6) {
      if (!(var0 < var2) && !(var0 > var6)) {
         return var0 < var4 ? smooth((var0 - var2) / (var4 - var2)) : 1.0F - smooth((var0 - var4) / (var6 - var4));
      } else {
         return 0.0F;
      }
   }

   static float reloadProgress(ItemStack var0, double var1) {
      CompoundTag var3 = ExpeditionWeapon.data(var0);
      long var4 = var3.getLong("reload_started");
      long var6 = var3.getLong("reload_until");
      return (double)var6 > var1 && var6 > var4 ? (float)Math.clamp((var1 - (double)var4) / (double)(var6 - var4), 0.0, 1.0) : 0.0F;
   }

   static float tubeClosing(ItemStack var0, double var1) {
      long var3 = ExpeditionWeapon.data(var0).getLong("reload_until");
      return (double)var3 > var1 ? window(var1, (double)(var3 - 9L), (double)(var3 - 4L), (double)var3) : 0.0F;
   }

   static float slide(Weapon var0, ItemStack var1, double var2) {
      CompoundTag var4 = ExpeditionWeapon.data(var1);
      long var5 = FieldGunEffects.shotTime(var1);
      float var7 = window(var5 > 0L ? var2 - (double)var5 : 1000.0, 0.0, 1.4, (double)Math.min(5, var0.interval));
      if (var0 == Weapon.FIELD_PISTOL || var0 == Weapon.SEMI_AUTO_RIFLE) {
         // slide / bolt locks back on the last round and is released at the end of an empty-gun magazine change
         float var8 = reloadProgress(var1, var2);
         if (var8 > 0.0F && var4.getBoolean("reload_empty")) {
            var7 = Math.max(var7, (double)var8 > 0.8 ? 1.0F - smooth(((double)var8 - 0.8) / 0.18) : (var5 > 0L ? 1.0F : smooth((double)var8 / 0.12)));
         } else if (var5 > 0L && var4.getInt("rounds") == 0) {
            var7 = 1.0F;
         }
      } else if (var0 == Weapon.SEMI_AUTO_SHOTGUN) {
         // [guns3] the semi-auto shotgun's bolt holds open on the last shell and is released (slammed home) once the
         // tube is loaded; after a top-up load the handle is still racked so the action visibly cycles
         long until = var4.getLong("reload_until");
         if (reloadProgress(var1, var2) > 0.0F) {
            if (var4.getBoolean("reload_empty")) {
               double release = until - 6.0;
               var7 = var2 < release ? 1.0F : 1.0F - smooth((var2 - release) / 5.0);
            } else {
               var7 = Math.max(var7, tubeClosing(var1, var2));
            }
         } else if (var5 > 0L && var4.getInt("rounds") == 0) {
            var7 = 1.0F;
         }
      }

      return var7;
   }

   static void draw(Weapon var0, ItemStack var1, ItemDisplayContext var2, PoseStack var3, MultiBufferSource var4, int var5, double var6) {
      ResourceLocation tex = guns(); // [gunsmith] per-look atlas
      FilteredFieldTexture.ensure(tex);
      String var8 = var2 == ItemDisplayContext.GROUND
         ? "distant"
         : (!var2.firstPerson() && var2 != ItemDisplayContext.GUI && var2 != ItemDisplayContext.FIXED ? "field" : "close");
      if (var8.equals("close") && HuntConfig.QUALITY.get() == HuntConfig.Quality.PERFORMANCE) {
         var8 = "field";
      }

      localView = var2.firstPerson();
      long var9 = FieldGunEffects.shotTime(var1);
      double var11 = var9 > 0L ? var6 - (double)var9 : 1000.0;
      float var13 = reloadProgress(var1, var6);
      float var14 = window((double)var13, 0.0, 0.19, 0.97);
      if (var13 > 0.19F && var13 < 0.77F) {
         var14 = 1.0F;
      } else if (var13 >= 0.77F) {
         var14 = 1.0F - smooth(((double)var13 - 0.77) / 0.2);
      }

      float var15 = FieldWeaponMotion.cycle(var0, var1, var6);
      float var16 = slide(var0, var1, var6);
      lod = var8;
      VertexConsumer var17 = var4.getBuffer(HuntRenderTypes.supplied(tex));
      if (var0 == Weapon.FIELD_PISTOL) {
         float var18 = Math.min(1.0F, var16 + drawSlide);
         float var19 = window(var11, 0.0, 1.15, 4.0);
         CombatPistolMesh.draw(
            var3,
            var4.getBuffer(RenderType.entityCutoutNoCull(CombatPistolMesh.Tex.ID)),
            var5,
            var18,
            var14,
            var19
         );
      }

      for (FieldWeaponMesh.Part var26 : var0 == Weapon.FIELD_PISTOL ? List.<FieldWeaponMesh.Part>of() : load(var0.id() + "_" + var8)) {
         var3.pushPose();
         switch (var26.id) {
            case 1:
               if (var0 == Weapon.PUMP_SHOTGUN) {
                  var3.translate(0.0F, 0.0F, (var15 + drawPump) * 0.075F);
               }

               if (var0 == Weapon.BAIT_LAUNCHER) {
                  var3.translate(0.0F, 0.0F, drawPump * 0.06F);
               }

               if (var0 == Weapon.DOUBLE_BARREL) {
                  rotateX(var3, 0.017, -0.159, -(var14 + drawBreak) * 31.0F);
               }

               if (var0 == Weapon.FLARE_GUN) {
                  rotateX(var3, 0.014, -0.08, -(var14 + drawBreak) * 37.0F);
               }

               if (var0 == Weapon.REVOLVER) {
                  var3.translate(0.0, 0.016, 0.068);
                  var3.mulPose(Axis.ZP.rotationDegrees(Math.min(1.0F, var14 + drawCrane) * 87.0F));
                  var3.translate(0.0, -0.016, -0.068);
               }
               break;
            case 2:
               if (WeaponAction.reload(var0) == WeaponAction.Reload.SINGLE && var13 > 0.0F) {
                  // [gear21] bolt guns: the handle turns up about the bolt axis, then the bolt draws back
                  var3.translate(0.0, ReloadRounds.BOLT_Y, 0.0);
                  var3.mulPose(Axis.ZP.rotationDegrees(ReloadRounds.boltLift(var13) * 62.0F));
                  var3.translate(0.0, -ReloadRounds.BOLT_Y, 0.0);
               }
               var3.translate(
                  0.0,
                  0.0,
                  var0 == Weapon.FIELD_PISTOL
                     ? (double)(var16 + drawSlide) * 0.054
                     : (
                        // [guns2] travel clamped to one full stroke; the assault rifle's carrier is short (its mesh
                        // spans ~0.13), so it moves 0.028 instead of sliding right out of the receiver
                        var0 != Weapon.PUMP_SHOTGUN && var0 != Weapon.LEVER_RIFLE
                           ? (double)Math.min(1.0F, Math.max(var16 + drawSlide, WeaponAction.reload(var0) == WeaponAction.Reload.SINGLE ? ReloadRounds.boltBack(var13) : 0.0F))
                              * (var0 == Weapon.SEMI_AUTO_RIFLE ? 0.028 : 0.046)
                           : (double)Math.min(1.0F, var15 + drawSlide) * 0.048
                     )
               );
               break;
            case 3:
               if (var0 == Weapon.REVOLVER) {
                  var3.translate(0.0, 0.016, 0.068);
                  var3.mulPose(Axis.ZP.rotationDegrees(Math.min(1.0F, var14 + drawCrane) * 87.0F));
                  var3.translate(0.0, -0.016, -0.068);
                  var3.translate(0.0, 0.057, 0.0);
                  var3.mulPose(Axis.ZP.rotationDegrees(window(var11, 0.0, 3.0, (double)var0.interval) * 60.0F + drawCylinder));
                  var3.translate(0.0, -0.057, 0.0);
               } else {
                  var3.translate(0.0, (double)(-var14) * 0.2, (double)var14 * 0.024);
               }
               break;
            case 4:
               rotateX(var3, FieldWeaponSockets.leverY(var0), FieldWeaponSockets.leverZ(var0), (var15 + drawLever) * 48.0F);
               break;
            case 5:
               rotateX(
                  var3,
                  FieldWeaponSockets.hammerY(var0),
                  FieldWeaponSockets.hammerZ(var0),
                  -window(var11, 0.0, 1.0, 5.0) * 23.0F + var15 * 12.0F - drawHammer * 26.0F
               );
               break;
            case 6:
               rotateX(var3, FieldWeaponSockets.triggerY(var0), FieldWeaponSockets.triggerZ(var0), window(var11, 0.0, 1.0, 5.0) * 14.0F);
               break;
            case 7:
               rotateX(var3, 0.106, 0.103, FieldAttachmentHardware.ironsFolded(var1, var6) * -84.0F);
               break;
            case 8:
               rotateX(var3, 0.106, -0.377, FieldAttachmentHardware.ironsFolded(var1, var6) * 84.0F);
               break;
            case 9:
               // [gear21] the revolver's cartridge heads: turn with the cylinder; gone from the moment the empties are
               // pushed out until the cylinder is closed again (ReloadRounds draws the rounds going in)
               var3.translate(0.0, 0.016, 0.068);
               var3.mulPose(Axis.ZP.rotationDegrees(Math.min(1.0F, var14 + drawCrane) * 87.0F));
               var3.translate(0.0, -0.016, -0.068);
               var3.translate(0.0, 0.057, 0.0);
               var3.mulPose(Axis.ZP.rotationDegrees(window(var11, 0.0, 3.0, (double)var0.interval) * 60.0F + drawCylinder));
               var3.translate(0.0, -0.057, 0.0);
         }
         if (var26.id == 9 && var13 > 0.2F && var13 < 0.97F) {
            var3.popPose();
            continue;
         }

         Pose var20 = var3.last();
         if (var26.id == 3
            && (
               ExpeditionWeapon.attachment(var1, "extended_magazine") && WeaponAction.supports(var0, "extended_magazine")
                  || ExpeditionWeapon.attachment(var1, "pistol_magazine") && var0 == Weapon.FIELD_PISTOL
            )) {
            FieldAttachmentHardware.magazine(var0 == Weapon.FIELD_PISTOL, var3, var4, var5);
            var17 = var4.getBuffer(HuntRenderTypes.supplied(tex));
         } else {
            emit(var26, var20, var17, var5);
         }

         var3.popPose();
      }

      for (String var21 : new String[]{"suppressor", "muzzle_brake"}) {
         if (ExpeditionWeapon.attachment(var1, var21) && WeaponAction.supports(var0, var21)) {
            var3.pushPose();
            var3.translate(0.0, FieldWeaponSockets.muzzleY(var0), FieldWeaponSockets.muzzleZ(var0));
            FieldAttachmentHardware.muzzle(var21, var3, var4, var5);
            var3.popPose();
         }
      }

      if (ExpeditionWeapon.attachment(var1, "bipod") && WeaponAction.supports(var0, "bipod")) {
         var3.pushPose();
         var3.translate(0.0, FieldWeaponSockets.supportY(var0) - 0.005, FieldWeaponSockets.supportZ(var0) - 0.065);
         Entity var24 = var2.firstPerson() ? Minecraft.getInstance().player : HuntEquipmentRenderer.renderedHolder;
         FieldAttachmentHardware.bipod(var3, var4, var5, FieldAttachmentHardware.bipodDeploy(var24, var24 != null && var24.isCrouching(), var6));
         var3.popPose();
      }

      if (ExpeditionWeapon.attachment(var1, "angled_foregrip") && WeaponAction.supports(var0, "angled_foregrip")) {
         var3.pushPose();
         var3.translate(0.0, FieldWeaponSockets.supportY(var0), FieldWeaponSockets.supportZ(var0));
         if (var0 == Weapon.PUMP_SHOTGUN) {
            var3.translate(0.0F, 0.0F, var15 * 0.075F);
         }

         FieldAttachmentHardware.grip(var3, var4, var5);
         var3.popPose();
      }

      if (ExpeditionWeapon.attachment(var1, "steady_stock") && WeaponAction.supports(var0, "steady_stock")) {
         var3.pushPose();
         var3.translate(0.0, FieldWeaponSockets.stockY(var0), FieldWeaponSockets.stockZ(var0));
         var3.scale(1.0F, 1.0F, FieldWeaponSockets.stockScale(var0));
         FieldAttachmentHardware.stock(var3, var4, var5);
         var3.popPose();
      }

      String var25 = AttachmentSpec.installedSight(var1);
      double var28 = FieldWeaponSockets.mountLift(var0);
      if (FieldWeaponSockets.tactical(var25) && WeaponAction.supports(var0, var25)) {
         if (var28 > 0.0) {
            var3.pushPose();
            var3.translate(0.0, FieldWeaponSockets.opticY(var0), FieldWeaponSockets.opticZ(var0) - 0.012);
            part("att_optic_rail_short", var3, var4, var5);
            var3.popPose();
         }

         var3.pushPose();
         var3.translate(
            0.0,
            FieldWeaponSockets.opticY(var0) + var28 + 5.0E-4,
            FieldWeaponSockets.opticZ(var0) - 0.012 + (var0 == Weapon.FIELD_PISTOL ? (double)(Math.min(1.0F, var16 + drawSlide) * CombatPistolMesh.SLIDE_TRAVEL) : 0.0)
         );
         FieldTacticalSight.draw(var25, var3, var4, var5);
         var3.popPose();
      }

      boolean var30 = ExpeditionWeapon.attachment(var1, "six_power_scope")
         || ExpeditionWeapon.attachment(var1, "eight_power_scope")
         || ExpeditionWeapon.attachment(var1, "twelve_power_scope")
         || ExpeditionWeapon.attachment(var1, "thermal_scope")
         || var0 == Weapon.TRANQUILIZER_RIFLE && var25.isEmpty();
      if (var30 && WeaponAction.supports(var0, "six_power_scope")) {
         if (var28 > 0.0) {
            var3.pushPose();
            var3.translate(0.0, FieldWeaponSockets.opticY(var0), FieldWeaponSockets.opticZ(var0) + 0.01);
            part("att_optic_rail_long", var3, var4, var5);
            var3.popPose();
         }

         var3.pushPose();
         var3.translate(0.0, FieldWeaponSockets.opticY(var0) + var28 - 0.0955, FieldWeaponSockets.opticZ(var0) + 0.02);
         FieldMountedOptic.draw(var3, var4, var5, var25.isEmpty() ? "six_power_scope" : var25);
         var3.popPose();
      }

      if (var2 != ItemDisplayContext.GUI && var2 != ItemDisplayContext.GROUND && var2 != ItemDisplayContext.FIXED && var9 > 0L) {
         FieldGunEffects.flash(var0, var1, var3, var4, var11);
      }

      localView = false;
   }

   private static void rotateX(PoseStack var0, double var1, double var3, float var5) {
      var0.translate(0.0, var1, var3);
      var0.mulPose(Axis.XP.rotationDegrees(var5));
      var0.translate(0.0, -var1, -var3);
   }

   private FieldWeaponMesh() {
   }

   private static record Part(int id, float[] vertices, int[] colors, int[] indices, float[] posed) {
   }
}
