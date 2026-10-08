package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.AttachmentSpec;
import com.formaworks.frontierhunts.expedition.ExpeditionGear;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.FieldElectronics;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.rifle.RifleItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class ExpeditionOptics {
   // [items] the anatomy binoculars (organ X-ray view) were removed; the kill cam's X-ray lives in KillCamOverlay
   private static final Map<Integer, long[]> SIGHT = new HashMap<>();

   private static String mode() {
      Minecraft var0 = Minecraft.getInstance();
      LocalPlayer var1 = var0.player;
      if (var1 != null && var0.options.getCameraType().isFirstPerson()) {
         ItemStack var2 = var1.getMainHandItem();
         if (var2.getItem() instanceof RifleItem
            && RifleClient.aim(1.0F) > 0.85F
            && ExpeditionWeapon.attachment(var2, "thermal_scope")
            && FieldElectronics.available(var2)) {
            return "thermal_scope";
         } else {
            // [rifle] red dots and the prism on the Ridgeline use the same sight pictures as on the field guns
            if (var2.getItem() instanceof RifleItem && RifleClient.aim(1.0F) > 0.9F) {
               String ridge = com.formaworks.frontierhunts.rifle.RidgelineOptics.sight(var2);
               if (AttachmentSpec.unmagnified(ridge)) {
                  return "reflex_sight";
               }

               if (ridge.equals("two_power_prism")) {
                  return "prism_sight";
               }
            }

            if (var2.getItem() instanceof ExpeditionWeapon var3 && FieldWeaponFirstPerson.aim(1.0F) > 0.9F) {
               if (ExpeditionWeapon.attachment(var2, "thermal_scope")) {
                  return FieldElectronics.available(var2) ? "thermal_scope" : "field_scope";
               }

               if (AttachmentSpec.unmagnified(AttachmentSpec.installedSight(var2))) {
                  return "reflex_sight";
               }

               if (ExpeditionWeapon.attachment(var2, "two_power_prism")) {
                  return "prism_sight";
               }

               if (var3.weapon == Weapon.TRANQUILIZER_RIFLE
                  || ExpeditionWeapon.attachment(var2, "six_power_scope")
                  || ExpeditionWeapon.attachment(var2, "eight_power_scope")
                  || ExpeditionWeapon.attachment(var2, "twelve_power_scope")) {
                  return "field_scope";
               }
            }

            return var1.isUsingItem() && FieldOpticPresentation.progress(1.0F) > 0.985F && var1.getUseItem().getItem() instanceof ExpeditionGear var5
               ? (var5.id.equals("thermal_binoculars") && !FieldElectronics.available(var1.getUseItem()) ? "binoculars" : var5.id)
               : "";
         }
      } else {
         return "";
      }
   }

   private static LivingEntity target() {
      Minecraft var0 = Minecraft.getInstance();
      if (var0.player != null && var0.level != null) {
         double var1 = ObservationView.range();
         Vec3 var3 = var0.player.getEyePosition();
         Vec3 var4 = var3.add(var0.player.getLookAngle().scale(var1));
         double var5 = var0.player.pick(var1, 0.0F, false).getLocation().distanceToSqr(var3);
         LivingEntity var7 = null;

         for (Entity var9 : var0.level.entitiesForRendering()) {
            if (var9 instanceof LivingEntity) {
               LivingEntity var10 = (LivingEntity)var9;
               if (var9 != var0.player && var9.isAlive() && var9.distanceToSqr(var0.player) < var1 * var1) {
                  Optional var11 = var9.getBoundingBox().clip(var3, var4);
                  if (var11.isPresent() && ((Vec3)var11.get()).distanceToSqr(var3) < var5) {
                     var5 = ((Vec3)var11.get()).distanceToSqr(var3);
                     var7 = var10;
                  }
               }
            }
         }

         return var7;
      } else {
         return null;
      }
   }

   public static boolean active() {
      String var0 = mode();
      return var0.contains("binoculars")
         || var0.equals("rangefinder")
         || var0.equals("thermal_scope")
         || var0.equals("field_scope")
         || var0.equals("reflex_sight")
         || var0.equals("prism_sight");
   }

   @SubscribeEvent
   public static void layer(Pre var0) {
      Minecraft var1 = Minecraft.getInstance();
      boolean var2 = var1.player != null
         && var1.options.getCameraType().isFirstPerson()
         && var1.player.getMainHandItem().getItem() instanceof RifleItem
         && RifleClient.aim(1.0F) > 0.985F;
      if (active() || var2) {
         ResourceLocation var3 = var0.getName();
         String var4 = var3.getPath();
         if (var3.getNamespace().equals("minecraft")
               && Set.of(
                     "boss_overlay",
                     "selected_item_name",
                     "overlay_message",
                     "crosshair",
                     "hotbar",
                     "experience_level",
                     "experience_bar",
                     "player_health",
                     "armor_level",
                     "food_level",
                     "air_level"
                  )
                  .contains(var4)
            || var3.getNamespace().equals("frontierhunts") && var4.equals("wilderness")) {
            var0.setCanceled(true);
         }
      }
   }

   private static void reticleDisc(GuiGraphics var0, float var1, float var2, float var3, int var4, boolean var5) {
      int var6 = var4 & 16777215;
      if (var5) {
         float var7 = var3 * 3.2F;

         for (int var8 = (int)Math.floor((double)(var2 - var7)); (double)var8 <= Math.ceil((double)(var2 + var7)); var8++) {
            float var9 = (float)var8 + 0.5F - var2;
            float var10 = (float)Math.sqrt((double)Math.max(0.0F, var7 * var7 - var9 * var9));
            if (!(var10 <= 0.0F)) {
               int var11 = (int)(38.0F * Math.max(0.0F, 1.0F - Math.abs(var9) / var7));
               var0.fill((int)(var1 - var10), var8, (int)Math.ceil((double)(var1 + var10)), var8 + 1, var11 << 24 | var6);
            }
         }
      }

      for (int var12 = (int)Math.floor((double)(var2 - var3)); (double)var12 <= Math.ceil((double)(var2 + var3)); var12++) {
         float var13 = (float)var12 + 0.5F - var2;
         float var14 = (float)Math.sqrt((double)Math.max(0.0F, var3 * var3 - var13 * var13));
         if (!(var14 <= 0.0F)) {
            var0.fill(Math.round(var1 - var14), var12, Math.max(Math.round(var1 - var14) + 1, Math.round(var1 + var14)), var12 + 1, 0xFF000000 | var6);
         }
      }
   }

   private static void reticleRing(GuiGraphics var0, float var1, float var2, float var3, float var4, int var5) {
      int var6 = var5 & 16777215;
      float var7 = var3 + var4 / 2.0F;
      float var8 = var3 - var4 / 2.0F;

      for (int var9 = (int)Math.floor((double)(var2 - var7)); (double)var9 <= Math.ceil((double)(var2 + var7)); var9++) {
         float var10 = (float)var9 + 0.5F - var2;
         float var11 = (float)Math.sqrt((double)Math.max(0.0F, var7 * var7 - var10 * var10));
         if (!(var11 <= 0.0F)) {
            float var12 = (float)Math.sqrt((double)Math.max(0.0F, var8 * var8 - var10 * var10));
            if (var12 <= 0.0F) {
               var0.fill(Math.round(var1 - var11), var9, Math.round(var1 + var11), var9 + 1, -268435456 | var6);
            } else {
               var0.fill(Math.round(var1 - var11), var9, Math.max(Math.round(var1 - var11) + 1, Math.round(var1 - var12)), var9 + 1, -268435456 | var6);
               var0.fill(Math.min(Math.round(var1 + var12), Math.round(var1 + var11) - 1), var9, Math.round(var1 + var11), var9 + 1, -268435456 | var6);
            }
         }
      }
   }

   static void hud(GuiGraphics var0) {
      if (active()) {
         String var1 = mode();
         Minecraft var2 = Minecraft.getInstance();
         int var3 = var0.guiWidth();
         int var4 = var0.guiHeight();
         int var5 = var3 / 2;
         int var6 = var4 / 2;
         if (var1.equals("reflex_sight")) {
            int var21 = OpticInput.reticleColor();
            ItemStack var22 = var2.player.getMainHandItem();
            String var23 = AttachmentSpec.installedSight(var22);
            double var24 = var2.getWindow().getGuiScale();
            float var26 = (float)((double)var3 * var24 / 2.0);
            float var28 = (float)((double)var4 * var24 / 2.0);
            var0.pose().pushPose();
            var0.pose().scale((float)(1.0 / var24), (float)(1.0 / var24), 1.0F);
            float var31 = (float)Math.max(1.0, (double)var2.getWindow().getHeight() / 1080.0);
            if (var23.equals("holographic_sight")) {
               reticleRing(var0, var26, var28, 34.0F * var31, 2.1F * var31, var21);

               for (int var32 = 0; var32 < 4; var32++) {
                  double var16 = (double)var32 * Math.PI / 2.0;
                  float var18 = 34.0F * var31;
                  float var19 = 24.0F * var31;

                  for (float var20 = var19; var20 <= var18; var20 += 0.5F) {
                     reticleDisc(var0, var26 + (float)Math.cos(var16) * var20, var28 + (float)Math.sin(var16) * var20, 1.1F * var31, var21, false);
                  }
               }

               reticleDisc(var0, var26, var28, 1.8F * var31, var21, true);
            } else if (var23.equals("reflex_sight")) {
               reticleDisc(var0, var26, var28, 3.2F * var31, var21, true);
            } else {
               reticleDisc(var0, var26, var28, 2.4F * var31, var21, true);
            }

            var0.pose().popPose();
         } else if (var1.equals("prism_sight")) {
            FieldScopeView.draw(var0, 2.0, true);
         } else if (var1.equals("field_scope")) {
            FieldScopeView.draw(var0, OpticInput.magnification(), ExpeditionWeapon.attachment(var2.player.getMainHandItem(), "two_power_prism"));
         } else {
            boolean var7 = var1.equals("night_vision_binoculars");
            boolean var8 = var1.startsWith("thermal");
            if (var7 && FieldElectronics.available(var2.player.getUseItem())) {
               var0.fill(0, 0, var3, var4, 576428373);
            }

            if (var8) {
               var0.fill(0, 0, var3, var4, 1144274248);
            }

            int var10 = (int)((double)var4 * 0.42);
            OpticMask.draw(
               var0,
               (float)var5,
               (float)var6,
               (float)var10,
               (float)(var10 + 2),
               (float)Math.hypot((double)var3, (double)var4),
               -15722732,
               -15722732,
               -15064033,
               6.0F,
               1343231764
            );
            int var11 = -4601933;
            var0.fill(var5 - 9, var6, var5 - 3, var6 + 1, var11);
            var0.fill(var5 + 4, var6, var5 + 10, var6 + 1, var11);
            var0.fill(var5, var6 - 9, var5 + 1, var6 - 3, var11);
            var0.fill(var5, var6 + 4, var5 + 1, var6 + 10, var11);
            if (var1.equals("field_scope")) {
               int var12 = -15195104;
               var0.fill(var5 - var10 + 12, var6, var5 - 4, var6 + 1, var12);
               var0.fill(var5 + 5, var6, var5 + var10 - 12, var6 + 1, var12);
               var0.fill(var5, var6 - var10 + 12, var5 + 1, var6 - 4, var12);
               var0.fill(var5, var6 + 5, var5 + 1, var6 + var10 - 12, var12);

               for (int var13 = 1; var13 < 5; var13++) {
                  int var14 = var6 + var13 * 18;
                  var0.fill(var5 - 3, var14, var5 + 4, var14 + 1, var12);
               }

               var0.fill(var5, var6, var5 + 1, var6 + 1, OpticInput.reticleColor());
            }

            OpticInput.instrument(var0, var1, var10);
            if (ObservationView.elevated()) {
               var0.drawCenteredString(var2.font, "HIGH VANTAGE · SUPPORTED OBSERVATION", var5, 43, -5588588);
            }

            LivingEntity var25 = target();
            var0.drawCenteredString(
               var2.font,
               (
                     var7
                        ? "NIGHT VISION  /  FIELD OPTICS"
                        : (
                           var8
                              ? "THERMAL  /  FIELD OPTICS"
                              : (var1.equals("field_scope") ? "FIELD SCOPE" : (var1.equals("rangefinder") ? "RANGEFINDER" : "FIELD OPTICS"))
                        )
                  ),
               var5,
               12,
               -2367537
            );
            ItemStack var27 = var2.player.isUsingItem() ? var2.player.getUseItem() : var2.player.getMainHandItem();
            if (FieldElectronics.powered(var27)) {
               var0.drawCenteredString(
                  var2.font,
                  FieldElectronics.available(var27) ? "BAT " + Math.round((float)FieldElectronics.charge(var27) * 100.0F / 1800.0F) + "%" : "BATTERY EMPTY",
                  var5,
                  var4 - 32,
                  -4928863
               );
            }

            if (var1.equals("rangefinder")) {
               HitResult var29 = var2.player.pick(ObservationView.range(), 0.0F, false);
               double var15 = var2.player.getEyePosition().distanceTo(var25 == null ? var29.getLocation() : var25.getBoundingBox().getCenter());
               String reading = String.format(Locale.ROOT, "%.1f m", var15);
               if (var25 == null && var29.getType() == Type.MISS) {
                  // [1.1.0] past the loaded land: a far animal in the beam, or the Distant Horizons terrain
                  var far = com.formaworks.frontierhunts.optics.client.GlassingClient.farRange();
                  reading = far.isPresent() ? String.format(Locale.ROOT, "%.0f m", far.get()) : "NO RETURN";
               }
               var0.drawCenteredString(
                  var2.font,
                  reading,
                  var5,
                  var4 - 20,
                  -3089464
               );
            }

            if (var25 instanceof Whitetail var30) {
               var0.drawCenteredString(
                  var2.font, var30.traits().description() + "  /  " + var30.massKg() + " kg  /  Trophy " + var30.traits().trophyScore(), var5, 28, -5325907
               );
            }
         }
      }
   }

   private static boolean sight(Player var0, LivingEntity var1) {
      long var2 = var0.level().getGameTime();
      if (SIGHT.size() > 512) {
         SIGHT.clear();
      }

      long[] var4 = SIGHT.get(var1.getId());
      if (var4 != null && var2 - var4[0] < 4L && var2 >= var4[0]) {
         return var4[1] != 0L;
      } else {
         boolean var5 = var0.hasLineOfSight(var1);
         SIGHT.put(var1.getId(), new long[]{var2, var5 ? 1L : 0L});
         return var5;
      }
   }

   @SubscribeEvent
   public static void render(RenderLevelStageEvent var0) {
      String var1 = mode();
      boolean var3 = var1.startsWith("thermal");
      if (var0.getStage() == Stage.AFTER_ENTITIES && !HuntShaderCompat.shadowPass()) {
         if (var3) {
            Minecraft var4 = Minecraft.getInstance();
            if (var4.level != null && var4.player != null) {
               BufferSource var5 = var4.renderBuffers().bufferSource();
               var5.endBatch();
               Vec3 var6 = var0.getCamera().getPosition();
               PoseStack var7 = var0.getPoseStack();
               int var8 = 0;
               for (Entity var11 : var4.level.entitiesForRendering()) {
                  if (var11 instanceof LivingEntity) {
                     LivingEntity var12 = (LivingEntity)var11;
                     if (var11 != var4.player
                        && !(var11.distanceToSqr(var4.player) > 16384.0)
                        && var0.getFrustum().isVisible(var11.getBoundingBox())
                        && sight(var4.player, var12)) {
                        if (++var8 <= 12) {
                           float var13 = var0.getPartialTick().getGameTimeDeltaPartialTick(false);
                           var7.pushPose();
                           var7.translate(
                              Mth.lerp((double)var13, var11.xo, var11.getX()) - var6.x,
                              Mth.lerp((double)var13, var11.yo, var11.getY()) - var6.y,
                              Mth.lerp((double)var13, var11.zo, var11.getZ()) - var6.z
                           );
                           if (var11 instanceof Whitetail) {
                              Whitetail var14 = (Whitetail)var11;
                              var7.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(var13, var14.yBodyRotO, var14.yBodyRot)));
                              ExpeditionOptics.Tint var15 = new ExpeditionOptics.Tint(
                                 var5.getBuffer(OpticsRenderType.THERMAL), var14.downed() ? 11569506 : 16770724, 210
                              );
                              WhitetailRenderer.thermal(var14, var13, var7, var15);
                           } else {
                              int var17 = var12.isDeadOrDying() ? 11373928 : 16767904;
                              MultiBufferSource var16 = var2x -> new ExpeditionOptics.Tint(
                                    var5.getBuffer(var2x.mode() == Mode.TRIANGLES ? OpticsRenderType.THERMAL : OpticsRenderType.ORGANS), var17, 165
                                 );
                              var4.getEntityRenderDispatcher()
                                 .getRenderer(var12)
                                 .render(var12, Mth.rotLerp(var13, var12.yRotO, var12.getYRot()), var13, var7, var16, 15728880);
                           }

                           var7.popPose();
                        }
                     }
                  }
               }

               var5.endBatch(OpticsRenderType.ORGANS);
               var5.endBatch(OpticsRenderType.THERMAL);
            }
         }
      }
   }

   private ExpeditionOptics() {
   }

   private static record Tint(VertexConsumer delegate, int color, int alpha) implements VertexConsumer {
      public VertexConsumer addVertex(float var1, float var2, float var3) {
         this.delegate.addVertex(var1, var2, var3);
         return this;
      }

      public VertexConsumer setColor(int var1, int var2, int var3, int var4) {
         this.delegate.setColor(this.color >> 16 & 0xFF, this.color >> 8 & 0xFF, this.color & 0xFF, this.alpha);
         return this;
      }

      public VertexConsumer setUv(float var1, float var2) {
         this.delegate.setUv(0.5F, 0.5F);
         return this;
      }

      public VertexConsumer setUv1(int var1, int var2) {
         this.delegate.setUv1(var1, var2);
         return this;
      }

      public VertexConsumer setUv2(int var1, int var2) {
         this.delegate.setUv2(var1, var2);
         return this;
      }

      public VertexConsumer setNormal(float var1, float var2, float var3) {
         this.delegate.setNormal(var1, var2, var3);
         return this;
      }
   }
}
