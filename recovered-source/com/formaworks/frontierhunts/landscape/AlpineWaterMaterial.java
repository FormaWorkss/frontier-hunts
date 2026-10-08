package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.client.ShaderState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.MeshData.DrawState;
import com.mojang.blaze3d.vertex.VertexBuffer.Usage;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderStateShard.ShaderStateShard;
import net.minecraft.client.renderer.RenderStateShard.TextureStateShard;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class AlpineWaterMaterial {
   private static ShaderInstance shader;
   private static ShaderInstance sheet;
   private static VertexBuffer buffer;

   static boolean ready() {
      return shader != null;
   }

   static void draw(MeshData mesh, RenderLevelStageEvent event) {
      draw(mesh, event, ResourceLocation.fromNamespaceAndPath("frontierhunts", "textures/block/alpine_cascade_body.png"));
   }

   static void draw(MeshData mesh, RenderLevelStageEvent event, ResourceLocation texture) {
      if (mesh != null) {
         if (ShaderState.on) {
            drawCompatible(mesh, event, texture, false, 0.0F);
         } else if (shader == null) {
            mesh.close();
         } else {
            Minecraft mc = Minecraft.getInstance();
            ShaderInstance oldShader = RenderSystem.getShader();
            int oldTexture = RenderSystem.getShaderTexture(0);
            int depthFunc = GL11.glGetInteger(2932);
            int src = GL11.glGetInteger(32969);
            int dst = GL11.glGetInteger(32968);
            int srcA = GL11.glGetInteger(32971);
            int dstA = GL11.glGetInteger(32970);
            boolean blend = GL11.glIsEnabled(3042);
            boolean depth = GL11.glIsEnabled(2929);
            boolean cull = GL11.glIsEnabled(2884);
            boolean write = GL11.glGetBoolean(2930);

            try {
               if (buffer == null) {
                  buffer = new VertexBuffer(Usage.DYNAMIC);
               }

               RenderSystem.setShaderTexture(0, texture);
               shader.setSampler("Sampler0", mc.getTextureManager().getTexture(texture).getId());
               Vec3 sky = mc.level.getSkyColor(event.getCamera().getPosition(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
               shader.safeGetUniform("DayLight").set((float)Math.clamp(sky.length(), 0.16, 1.0));
               float[] grade = AlpineLook.overlayGrade();
               shader.safeGetUniform("Grade").set(grade[0], grade[1]);
               RenderSystem.enableDepthTest();
               RenderSystem.depthFunc(515);
               RenderSystem.depthMask(true);
               RenderSystem.disableCull();
               RenderSystem.enableBlend();
               RenderSystem.defaultBlendFunc();
               buffer.bind();
               buffer.upload(mesh);
               buffer.drawWithShader(event.getModelViewMatrix(), event.getProjectionMatrix(), shader);
            } finally {
               VertexBuffer.unbind();
               RenderSystem.setShader(() -> oldShader);
               RenderSystem.setShaderTexture(0, oldTexture);
               RenderSystem.depthFunc(depthFunc);
               RenderSystem.depthMask(write);
               RenderSystem.blendFuncSeparate(src, dst, srcA, dstA);
               if (!blend) {
                  RenderSystem.disableBlend();
               }

               if (!depth) {
                  RenderSystem.disableDepthTest();
               }

               if (cull) {
                  RenderSystem.enableCull();
               }
            }
         }
      }
   }

   static boolean sheetReady() {
      return sheet != null;
   }

   static void drawSheet(MeshData mesh, RenderLevelStageEvent event, float time) {
      if (mesh != null) {
         if (ShaderState.on) {
            drawCompatible(mesh, event, ResourceLocation.fromNamespaceAndPath("frontierhunts", "textures/block/alpine_cascade_body.png"), true, time);
         } else if (sheet == null) {
            mesh.close();
         } else {
            Minecraft mc = Minecraft.getInstance();
            ShaderInstance oldShader = RenderSystem.getShader();
            int oldTexture = RenderSystem.getShaderTexture(0);
            int depthFunc = GL11.glGetInteger(2932);
            int src = GL11.glGetInteger(32969);
            int dst = GL11.glGetInteger(32968);
            int srcA = GL11.glGetInteger(32971);
            int dstA = GL11.glGetInteger(32970);
            boolean blend = GL11.glIsEnabled(3042);
            boolean depth = GL11.glIsEnabled(2929);
            boolean cull = GL11.glIsEnabled(2884);
            boolean write = GL11.glGetBoolean(2930);

            try {
               if (buffer == null) {
                  buffer = new VertexBuffer(Usage.DYNAMIC);
               }

               float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
               Vec3 sky = mc.level.getSkyColor(event.getCamera().getPosition(), partial);
               sheet.safeGetUniform("DayLight").set((float)Math.clamp(sky.length(), 0.16, 1.0));
               sheet.safeGetUniform("FallTime").set(time);
               float angle = mc.level.getSunAngle(partial);
               sheet.safeGetUniform("SunDir").set((float)(-Math.sin((double)angle)), (float)Math.cos((double)angle), 0.15F);
               sheet.safeGetUniform("SkyTint").set((float)sky.x, (float)sky.y, (float)sky.z);
               float[] grade = AlpineLook.overlayGrade();
               sheet.safeGetUniform("Grade").set(grade[0], grade[1]);
               RenderSystem.enableDepthTest();
               RenderSystem.depthFunc(515);
               RenderSystem.depthMask(false);
               RenderSystem.disableCull();
               RenderSystem.enableBlend();
               RenderSystem.defaultBlendFunc();
               buffer.bind();
               buffer.upload(mesh);
               buffer.drawWithShader(event.getModelViewMatrix(), event.getProjectionMatrix(), sheet);
            } finally {
               VertexBuffer.unbind();
               RenderSystem.setShader(() -> oldShader);
               RenderSystem.setShaderTexture(0, oldTexture);
               RenderSystem.depthFunc(depthFunc);
               RenderSystem.depthMask(write);
               RenderSystem.blendFuncSeparate(src, dst, srcA, dstA);
               if (!blend) {
                  RenderSystem.disableBlend();
               }

               if (!depth) {
                  RenderSystem.disableDepthTest();
               }

               if (cull) {
                  RenderSystem.enableCull();
               }
            }
         }
      }
   }

   private static void clear() {
      if (buffer != null) {
         buffer.close();
         buffer = null;
      }
   }

   private static void drawCompatible(MeshData mesh, RenderLevelStageEvent event, ResourceLocation texture, boolean falling, float time) {
      Minecraft mc = Minecraft.getInstance();
      RenderType type = AlpineWaterMaterial.FoamPass.material(texture);
      BufferSource buffers = mc.renderBuffers().bufferSource();
      VertexConsumer target = buffers.getBuffer(type);
      DrawState state = mesh.drawState();
      VertexFormat format = state.format();
      ByteBuffer bytes = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
      int position = format.getOffset(VertexFormatElement.POSITION);
      int color = format.getOffset(VertexFormatElement.COLOR);
      int uv = format.getOffset(VertexFormatElement.UV0);
      int overlay = format.getOffset(VertexFormatElement.UV1);
      int light = format.getOffset(VertexFormatElement.UV2);
      int normal = format.getOffset(VertexFormatElement.NORMAL);
      Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).invert().mul(event.getModelViewMatrix());
      Matrix3f normals = new Matrix3f(view).invert().transpose();
      Vector3f transformedNormal = new Vector3f();

      try {
         for (int vertex = 0; vertex < state.vertexCount(); vertex++) {
            int offset = vertex * format.getVertexSize();
            float u = bytes.getFloat(offset + uv);
            float v = bytes.getFloat(offset + uv + 4);
            int red = Byte.toUnsignedInt(bytes.get(offset + color));
            int green = Byte.toUnsignedInt(bytes.get(offset + color + 1));
            int blue = Byte.toUnsignedInt(bytes.get(offset + color + 2));
            float alpha = (float)Byte.toUnsignedInt(bytes.get(offset + color + 3)) / 255.0F;
            if (falling) {
               int encoded = Short.toUnsignedInt(bytes.getShort(offset + overlay));
               int mode = encoded >> 12 & 3;
               float height = (float)Math.max(1, encoded & 1023);
               float drop = Math.max(0.0F, v);
               float travel = ((float)Math.sqrt((double)(2.25F + 19.62F * drop)) - 1.5F) / 9.81F;
               u /= 2.0F;
               v = travel * 0.72F - time * 0.72F;
               float fade = Math.clamp(drop / 0.4F, 0.0F, 1.0F) * Math.clamp((height + 0.1F - drop) / 0.8F, 0.0F, 1.0F);
               float edge = (float)Short.toUnsignedInt(bytes.getShort(offset + overlay + 2)) / 1000.0F;
               fade *= 1.0F - 0.85F * Math.clamp(edge, 0.0F, 1.0F);
               alpha *= mode == 1 ? 0.12F * fade : (mode == 2 ? 0.15F : 0.55F * fade);
               red = 150 + (int)((float)red * 0.25F);
               green = 175 + (int)((float)green * 0.2F);
               blue = 192 + (int)((float)blue * 0.15F);
            } else if (texture.getPath().endsWith("alpine_pool_surface.png")) {
               alpha *= 0.45F;
            }

            transformedNormal.set(
                  (float)bytes.get(offset + normal) / 127.0F, (float)bytes.get(offset + normal + 1) / 127.0F, (float)bytes.get(offset + normal + 2) / 127.0F
               )
               .mul(normals)
               .normalize();
            target.addVertex(view, bytes.getFloat(offset + position), bytes.getFloat(offset + position + 4), bytes.getFloat(offset + position + 8))
               .setColor(red, green, blue, (int)Math.clamp(alpha * 255.0F, 0.0F, 255.0F))
               .setUv(u, v)
               .setOverlay(OverlayTexture.NO_OVERLAY)
               .setUv2(Short.toUnsignedInt(bytes.getShort(offset + light)), Short.toUnsignedInt(bytes.getShort(offset + light + 2)))
               .setNormal(transformedNormal.x, transformedNormal.y, transformedNormal.z);
         }

         buffers.endBatch(type);
      } finally {
         mesh.close();
      }
   }

   @SubscribeEvent
   public static void logout(LoggingOut e) {
      clear();
   }

   private static final class FoamPass extends RenderType {
      private static final Map<ResourceLocation, RenderType> types = new HashMap<>();

      private FoamPass() {
         super("frontier_water", DefaultVertexFormat.PARTICLE, Mode.QUADS, 262144, false, true, () -> {
         }, () -> {
         });
      }

      static RenderType material(ResourceLocation texture) {
         return types.computeIfAbsent(
            texture,
            key -> create(
                  "frontier_water",
                  DefaultVertexFormat.PARTICLE,
                  Mode.QUADS,
                  262144,
                  false,
                  true,
                  CompositeState.builder()
                     .setShaderState(new ShaderStateShard(GameRenderer::getParticleShader))
                     .setTextureState(new TextureStateShard(key, false, false))
                     .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                     .setCullState(NO_CULL)
                     .setLightmapState(LIGHTMAP)
                     .setWriteMaskState(COLOR_WRITE)
                     .createCompositeState(false)
               )
         );
      }
   }

   @EventBusSubscriber(
      modid = "frontierhunts",
      value = {Dist.CLIENT},
      bus = Bus.MOD
   )
   public static final class Registration {
      @SubscribeEvent
      public static void register(RegisterShadersEvent e) throws IOException {
         AlpineWaterMaterial.clear();
         e.registerShader(
            new ShaderInstance(e.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("frontierhunts", "alpine_water"), DefaultVertexFormat.NEW_ENTITY),
            s -> AlpineWaterMaterial.shader = s
         );
         e.registerShader(
            new ShaderInstance(e.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("frontierhunts", "falls_sheet"), DefaultVertexFormat.NEW_ENTITY),
            s -> AlpineWaterMaterial.sheet = s
         );
      }
   }
}
