package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.expedition.HuntProjectile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexBuffer.Usage;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.lwjgl.opengl.GL11;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class FlareGlow {
   static ShaderInstance shader;
   private static VertexBuffer buffer;
   private static final Map<Integer, HuntProjectile> VISIBLE = new LinkedHashMap<>();

   static void track(HuntProjectile var0) {
      if (!HuntShaderCompat.shadowPass() && VISIBLE.size() < 32) {
         VISIBLE.put(var0.getId(), var0);
      }
   }

   @SubscribeEvent
   public static void render(RenderLevelStageEvent var0) {
      if (var0.getStage() == Stage.AFTER_LEVEL && !HuntShaderCompat.shadowPass()) {
         Minecraft var1 = Minecraft.getInstance();
         if (shader != null && var1.level != null && !VISIBLE.isEmpty()) {
            int var2 = GL11.glGetInteger(2932);
            boolean var3 = GL11.glIsEnabled(3042);
            boolean var4 = GL11.glIsEnabled(2929);
            boolean var5 = GL11.glIsEnabled(2884);
            boolean var6 = GL11.glGetBoolean(2930);
            int var7 = GL11.glGetInteger(32969);
            int var8 = GL11.glGetInteger(32968);
            int var9 = GL11.glGetInteger(32971);
            int var10 = GL11.glGetInteger(32970);
            ShaderInstance var11 = RenderSystem.getShader();

            try {
               BufferBuilder var12 = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
               float var13 = var0.getPartialTick().getGameTimeDeltaPartialTick(false);

               for (HuntProjectile var15 : VISIBLE.values()) {
                  if (!var15.isRemoved() && var15.level() == var1.level) {
                     draw(var15, var12, var13);
                  }
               }

               MeshData var19 = var12.build();
               if (var19 != null) {
                  if (buffer == null) {
                     buffer = new VertexBuffer(Usage.DYNAMIC);
                  }

                  RenderSystem.enableDepthTest();
                  RenderSystem.depthFunc(515);
                  RenderSystem.depthMask(false);
                  RenderSystem.disableCull();
                  RenderSystem.enableBlend();
                  RenderSystem.defaultBlendFunc();
                  buffer.bind();
                  buffer.upload(var19);
                  buffer.drawWithShader(var0.getModelViewMatrix(), var0.getProjectionMatrix(), shader);
                  return;
               }
            } finally {
               VISIBLE.clear();
               VertexBuffer.unbind();
               RenderSystem.setShader(() -> var11);
               RenderSystem.depthFunc(var2);
               RenderSystem.depthMask(var6);
               RenderSystem.blendFuncSeparate(var7, var8, var9, var10);
               if (!var3) {
                  RenderSystem.disableBlend();
               }

               if (!var4) {
                  RenderSystem.disableDepthTest();
               }

               if (var5) {
                  RenderSystem.enableCull();
               }
            }
         } else {
            VISIBLE.clear();
         }
      }
   }

   private static void draw(HuntProjectile var0, VertexConsumer var1, float var2) {
      Minecraft var3 = Minecraft.getInstance();
      Vec3 var4 = var0.getPosition(var2).subtract(var3.gameRenderer.getMainCamera().getPosition());
      double var5 = var4.length();
      if (!(var5 < 0.7)) {
         float var7 = var0.burstAge(var2);
         float var8 = var7 >= 0.0F && var7 < 12.0F ? 1.0F - var7 / 12.0F : 0.0F;
         float var9 = var7 < 500.0F ? 1.0F : Math.clamp((600.0F - var7) / 100.0F, 0.0F, 1.0F);
         var9 = var9 * var9 * (3.0F - 2.0F * var9);
         float var10 = (float)Math.clamp(0.4 + var5 * 0.033, 0.4, 8.0) * (1.0F + var8 * 2.2F);
         PoseStack var11 = new PoseStack();
         var11.translate(var4.x, var4.y, var4.z);
         var11.mulPose(var3.getEntityRenderDispatcher().cameraOrientation());
         int var12 = (int)(255.0F * var9);
         vertex(var11, var1, -var10, -var10, -1.0F, -1.0F, var12);
         vertex(var11, var1, var10, -var10, 1.0F, -1.0F, var12);
         vertex(var11, var1, var10, var10, 1.0F, 1.0F, var12);
         vertex(var11, var1, -var10, var10, -1.0F, 1.0F, var12);
      }
   }

   private static void vertex(PoseStack var0, VertexConsumer var1, float var2, float var3, float var4, float var5, int var6) {
      var1.addVertex(var0.last().pose(), var2, var3, 0.0F).setUv(var4, var5).setColor(255, 255, 255, var6);
   }

   private static void clear() {
      VISIBLE.clear();
      if (buffer != null) {
         buffer.close();
         buffer = null;
      }
   }

   @SubscribeEvent
   public static void logout(LoggingOut var0) {
      clear();
   }

   private FlareGlow() {
   }

   @EventBusSubscriber(
      modid = "frontierhunts",
      value = {Dist.CLIENT},
      bus = Bus.MOD
   )
   public static final class Registration {
      @SubscribeEvent
      public static void shaders(RegisterShadersEvent var0) throws IOException {
         FlareGlow.clear();
         var0.registerShader(
            new ShaderInstance(var0.getResourceProvider(), FrontierHunts.id("flare_glow"), DefaultVertexFormat.POSITION_TEX_COLOR),
            var0x -> FlareGlow.shader = var0x
         );
      }
   }
}
