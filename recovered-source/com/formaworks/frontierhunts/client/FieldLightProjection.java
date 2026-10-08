package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.expedition.ExpeditionGear;
import com.formaworks.frontierhunts.expedition.FieldElectronics;
import com.formaworks.frontierhunts.expedition.FieldFlashlight;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class FieldLightProjection {
   private static ShaderInstance shader;
   private static ShaderInstance depthShader;
   private static TextureTarget shadow;
   private static final Map<UUID, FieldLightTerrain> TERRAINS = new HashMap<>();
   public static int renderedTiles;

   private static boolean torch(ItemStack var0) {
      if (var0.getItem() instanceof ExpeditionGear var1 && var1.id.equals("field_flashlight")) {
         return true;
      }

      return false;
   }

   @SubscribeEvent
   public static void render(RenderLevelStageEvent var0) {
      if (var0.getStage() == Stage.AFTER_LEVEL && !HuntShaderCompat.shadowPass() && shader != null && depthShader != null) {
         Minecraft var1 = Minecraft.getInstance();
         if (var1.level != null && var1.player != null) {
            renderedTiles = 0;
            Vec3 var2 = var0.getCamera().getPosition();
            float var3 = var0.getPartialTick().getGameTimeDeltaPartialTick(false);
            List var4 = var1.level
               .players()
               .stream()
               .filter(
                  var1x -> var1x.isAlive()
                        && !var1x.isSpectator()
                        && !var1x.isSleeping()
                        && var1x.distanceToSqr(var2) < 1296.0
                        && (torch(var1x.getMainHandItem()) || torch(var1x.getOffhandItem()))
               )
               .sorted(Comparator.comparingDouble(var2x -> var2x == var1.player ? -1.0 : var2x.distanceToSqr(var2)))
               .limit(2L)
               .toList();
            Set var5 = var4.stream().map(Entity::getUUID).collect(Collectors.toSet());
            TERRAINS.entrySet().removeIf(var1x -> {
               if (!var5.contains(var1x.getKey())) {
                  var1x.getValue().close();
                  return true;
               } else {
                  return false;
               }
            });

            for (AbstractClientPlayer var7 : var4) {
               TERRAINS.computeIfAbsent(var7.getUUID(), var0x -> new FieldLightTerrain())
                  .update(var1.level, var7.getEyePosition(var3), var7.getViewVector(var3));
            }

            if (!var4.isEmpty()) {
               if (!var4.stream().noneMatch(var0x -> FieldFlashlight.enabled(var0x.getMainHandItem()) || FieldFlashlight.enabled(var0x.getOffhandItem()))) {
                  int var37 = GL11.glGetInteger(36006);
                  int var38 = GL11.glGetInteger(36010);
                  int[] var8 = new int[4];
                  GL11.glGetIntegerv(2978, var8);
                  int var9 = RenderSystem.getShaderTexture(0);
                  int var10 = RenderSystem.getShaderTexture(1);
                  int var11 = GL11.glGetInteger(2932);
                  boolean var12 = GL11.glIsEnabled(3042);
                  boolean var13 = GL11.glIsEnabled(2929);
                  boolean var14 = GL11.glIsEnabled(2884);
                  boolean var15 = GL11.glIsEnabled(32823);
                  boolean var16 = GL11.glGetBoolean(2930);
                  int var17 = GL11.glGetInteger(32969);
                  int var18 = GL11.glGetInteger(32968);
                  int var19 = GL11.glGetInteger(32971);
                  int var20 = GL11.glGetInteger(32970);
                  float var21 = GL11.glGetFloat(32824);
                  float var22 = GL11.glGetFloat(10752);
                  ShaderInstance var23 = RenderSystem.getShader();

                  try {
                     RenderSystem.enableDepthTest();
                     RenderSystem.depthFunc(515);
                     RenderSystem.disableCull();
                     RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);

                     for (AbstractClientPlayer var25 : var4) {
                        if (FieldFlashlight.enabled(var25.getMainHandItem()) || FieldFlashlight.enabled(var25.getOffhandItem())) {
                           FieldLightTerrain var26 = TERRAINS.get(var25.getUUID());
                           ItemStack var27 = FieldFlashlight.enabled(var25.getMainHandItem()) ? var25.getMainHandItem() : var25.getOffhandItem();
                           shader.safeGetUniform("BeamPower").set(FieldElectronics.intensity(var27));
                           Vec3 var28 = var25.getEyePosition(var3);
                           Vec3 var29 = var25.getViewVector(var3);
                           Matrix4f var30 = new Matrix4f()
                              .lookAlong(
                                 (float)var29.x,
                                 (float)var29.y,
                                 (float)var29.z,
                                 0.0F,
                                 Math.abs(var29.y) > 0.98 ? 0.0F : 1.0F,
                                 Math.abs(var29.y) > 0.98 ? 1.0F : 0.0F
                              );
                           Matrix4f var31 = new Matrix4f().perspective((float)Math.toRadians(88.0), 1.0F, 0.08F, 44.0F);
                           if (shadow == null) {
                              shadow = new TextureTarget(512, 512, true, Minecraft.ON_OSX);
                           }

                           RenderSystem.depthMask(true);
                           RenderSystem.disableBlend();
                           RenderSystem.disablePolygonOffset();
                           shadow.clear(Minecraft.ON_OSX);
                           shadow.bindWrite(true);
                           var26.draw(var28, var30, var31, depthShader);
                           GL30.glBindFramebuffer(36009, var37);
                           GL30.glBindFramebuffer(36008, var38);
                           RenderSystem.viewport(var8[0], var8[1], var8[2], var8[3]);
                           Vec3 var32 = var2.subtract(var28);
                           shader.safeGetUniform("ShadowFromView")
                              .set(
                                 new Matrix4f(var31)
                                    .mul(var30)
                                    .translate((float)var32.x, (float)var32.y, (float)var32.z)
                                    .mul(new Matrix4f(var0.getModelViewMatrix()).invert())
                              );
                           Vec3 var33 = var28.subtract(var2);
                           shader.safeGetUniform("Lamp0")
                              .set(var0.getModelViewMatrix().transformPosition(new Vector3f((float)var33.x, (float)var33.y, (float)var33.z)));
                           shader.safeGetUniform("BeamDirection")
                              .set(var0.getModelViewMatrix().transformDirection(new Vector3f((float)var29.x, (float)var29.y, (float)var29.z)));
                           RenderSystem.setShaderTexture(1, shadow.getDepthTextureId());
                           RenderSystem.enableBlend();
                           RenderSystem.blendFunc(770, 1);
                           RenderSystem.depthMask(false);
                           RenderSystem.enablePolygonOffset();
                           RenderSystem.polygonOffset(-1.0F, -1.0F);
                           renderedTiles = renderedTiles + var26.draw(var2, var0.getModelViewMatrix(), var0.getProjectionMatrix(), shader);
                        }
                     }
                  } finally {
                     VertexBuffer.unbind();
                     GL30.glBindFramebuffer(36009, var37);
                     GL30.glBindFramebuffer(36008, var38);
                     RenderSystem.viewport(var8[0], var8[1], var8[2], var8[3]);
                     RenderSystem.setShaderTexture(0, var9);
                     RenderSystem.setShaderTexture(1, var10);
                     RenderSystem.setShader(() -> var23);
                     RenderSystem.depthFunc(var11);
                     RenderSystem.depthMask(var16);
                     RenderSystem.polygonOffset(var21, var22);
                     if (!var15) {
                        RenderSystem.disablePolygonOffset();
                     }

                     RenderSystem.blendFuncSeparate(var17, var18, var19, var20);
                     if (!var12) {
                        RenderSystem.disableBlend();
                     }

                     if (!var13) {
                        RenderSystem.disableDepthTest();
                     }

                     if (var14) {
                        RenderSystem.enableCull();
                     }
                  }
               }
            }
         }
      }
   }

   public static void close() {
      TERRAINS.values().forEach(FieldLightTerrain::close);
      TERRAINS.clear();
      if (shadow != null) {
         shadow.destroyBuffers();
         shadow = null;
      }
   }

   @SubscribeEvent
   public static void logout(LoggingOut var0) {
      close();
   }

   private FieldLightProjection() {
   }

   @EventBusSubscriber(
      modid = "frontierhunts",
      value = {Dist.CLIENT},
      bus = Bus.MOD
   )
   public static final class Registration {
      @SubscribeEvent
      public static void shaders(RegisterShadersEvent var0) throws IOException {
         FieldLightProjection.close();
         var0.registerShader(
            new ShaderInstance(var0.getResourceProvider(), FrontierHunts.id("field_surface_light"), DefaultVertexFormat.BLOCK),
            var0x -> FieldLightProjection.shader = var0x
         );
         var0.registerShader(
            new ShaderInstance(var0.getResourceProvider(), FrontierHunts.id("field_surface_depth"), DefaultVertexFormat.BLOCK),
            var0x -> FieldLightProjection.depthShader = var0x
         );
      }
   }
}
