package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexBuffer.Usage;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class ArrowTrails {
   private static final int MAX_POINTS = 48;
   private static final double LIFE = 0.34;
   static ShaderInstance shader;
   private static VertexBuffer buffer;
   private static final Map<Integer, ArrowTrails.Trail> TRAILS = new HashMap<>();

   private static double now(float var0) {
      Minecraft var1 = Minecraft.getInstance();
      return var1.level == null ? 0.0 : (double)((float)var1.level.getGameTime() + var0) / 20.0;
   }

   static void track(int var0, int var1, Vec3 var2, Vec3 var3, boolean var4, float var5) {
      if (var1 != 0 && !HuntShaderCompat.shadowPass() && (TRAILS.size() <= 96 || TRAILS.containsKey(var0))) {
         double var6 = now(var5);
         ArrowTrails.Trail var8 = TRAILS.computeIfAbsent(var0, var0x -> new ArrowTrails.Trail());
         var8.color = var1;
         var8.seen = var6;
         var8.dirX = var3.x;
         var8.dirY = var3.y;
         var8.dirZ = var3.z;
         if (var4) {
            if (!var8.resting) {
               var8.resting = true;
               var8.restSince = var6;
            }

            var8.restX = var2.x;
            var8.restY = var2.y;
            var8.restZ = var2.z;
         } else {
            var8.resting = false;
            var8.add(var2.x, var2.y, var2.z, var6);
         }
      }
   }

   @SubscribeEvent
   public static void render(RenderLevelStageEvent var0) {
      if (var0.getStage() == Stage.AFTER_LEVEL && !HuntShaderCompat.shadowPass()) {
         Minecraft var1 = Minecraft.getInstance();
         if (var1.level == null) {
            TRAILS.clear();
         } else if (!TRAILS.isEmpty()) {
            float var2 = var0.getPartialTick().getGameTimeDeltaPartialTick(false);
            double var3 = now(var2);
            Vec3 var5 = var1.gameRenderer.getMainCamera().getPosition();
            BufferBuilder var6 = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            int var7 = 0;
            Iterator var8 = TRAILS.values().iterator();

            while (var8.hasNext()) {
               ArrowTrails.Trail var9 = (ArrowTrails.Trail)var8.next();
               var9.expire(var3);
               boolean var10 = var3 - var9.seen > 0.15;
               if (var10 && var9.size == 0) {
                  var8.remove();
               } else if (var9.size != 0 || var9.resting) {
                  var7 += streak(var6, var9, var3, var5);
                  if (!var10) {
                     var7 += head(var6, var9, var3, var5);
                  }
               }
            }

            if (var7 == 0) {
               var6.build();
            } else if (shader == null) {
               var6.build();
            } else {
               MeshData var23 = var6.build();
               if (var23 != null) {
                  int var24 = GL11.glGetInteger(2932);
                  boolean var11 = GL11.glIsEnabled(3042);
                  boolean var12 = GL11.glIsEnabled(2929);
                  boolean var13 = GL11.glIsEnabled(2884);
                  boolean var14 = GL11.glGetBoolean(2930);
                  int var15 = GL11.glGetInteger(32969);
                  int var16 = GL11.glGetInteger(32968);
                  int var17 = GL11.glGetInteger(32971);
                  int var18 = GL11.glGetInteger(32970);
                  ShaderInstance var19 = RenderSystem.getShader();

                  try {
                     if (buffer == null) {
                        buffer = new VertexBuffer(Usage.DYNAMIC);
                     }

                     RenderSystem.enableDepthTest();
                     RenderSystem.depthFunc(515);
                     RenderSystem.depthMask(false);
                     RenderSystem.disableCull();
                     RenderSystem.enableBlend();
                     RenderSystem.blendFuncSeparate(770, 1, 1, 771);
                     buffer.bind();
                     buffer.upload(var23);
                     buffer.drawWithShader(var0.getModelViewMatrix(), var0.getProjectionMatrix(), shader);
                  } finally {
                     VertexBuffer.unbind();
                     RenderSystem.setShader(() -> var19);
                     RenderSystem.depthFunc(var24);
                     RenderSystem.depthMask(var14);
                     RenderSystem.blendFuncSeparate(var15, var16, var17, var18);
                     if (!var11) {
                        RenderSystem.disableBlend();
                     }

                     if (!var12) {
                        RenderSystem.disableDepthTest();
                     }

                     if (var13) {
                        RenderSystem.enableCull();
                     }
                  }
               }
            }
         }
      }
   }

   private static int streak(BufferBuilder var0, ArrowTrails.Trail var1, double var2, Vec3 var4) {
      if (var1.size < 2) {
         return 0;
      } else {
         int var5 = var1.color >> 16 & 0xFF;
         int var6 = var1.color >> 8 & 0xFF;
         int var7 = var1.color & 0xFF;
         int var8 = 0;
         Vector3f var9 = new Vector3f();
         Vector3f var10 = null;

         for (int var11 = 0; var11 < var1.size - 1; var11++) {
            int var12 = (var1.start + var11) % 48;
            int var13 = (var1.start + var11 + 1) % 48;
            float var14 = (float)(var1.pts[var12 * 3] - var4.x);
            float var15 = (float)(var1.pts[var12 * 3 + 1] - var4.y);
            float var16 = (float)(var1.pts[var12 * 3 + 2] - var4.z);
            float var17 = (float)(var1.pts[var13 * 3] - var4.x);
            float var18 = (float)(var1.pts[var13 * 3 + 1] - var4.y);
            float var19 = (float)(var1.pts[var13 * 3 + 2] - var4.z);
            Vector3f var20 = new Vector3f(var17 - var14, var18 - var15, var19 - var16);
            if (!(var20.lengthSquared() < 1.0E-6F)) {
               new Vector3f(var14 + var17, var15 + var18, var16 + var19).mul(0.5F).cross(var20, var9);
               if (!(var9.lengthSquared() < 1.0E-8F)) {
                  var9.normalize();
                  if (var10 == null) {
                     var10 = new Vector3f(var9);
                  }

                  float var21 = (float)Math.clamp(1.0 - (var2 - var1.born[var12]) / 0.34, 0.0, 1.0);
                  float var22 = (float)Math.clamp(1.0 - (var2 - var1.born[var13]) / 0.34, 0.0, 1.0);
                  float var23 = (float)Math.sqrt((double)(var14 * var14 + var15 * var15 + var16 * var16));
                  float var24 = (float)Math.sqrt((double)(var17 * var17 + var18 * var18 + var19 * var19));
                  float var25 = (0.035F + var21 * 0.06F) * (1.0F + var23 * 0.012F);
                  float var26 = (0.035F + var22 * 0.06F) * (1.0F + var24 * 0.012F);
                  int var27 = (int)(255.0F * var21 * var21);
                  int var28 = (int)(255.0F * var22 * var22);
                  var0.addVertex(var14 - var10.x * var25, var15 - var10.y * var25, var16 - var10.z * var25)
                     .setUv(-1.0F, 0.0F)
                     .setColor(var5, var6, var7, var27);
                  var0.addVertex(var17 - var9.x * var26, var18 - var9.y * var26, var19 - var9.z * var26).setUv(-1.0F, 0.0F).setColor(var5, var6, var7, var28);
                  var0.addVertex(var17 + var9.x * var26, var18 + var9.y * var26, var19 + var9.z * var26).setUv(1.0F, 0.0F).setColor(var5, var6, var7, var28);
                  var0.addVertex(var14 + var10.x * var25, var15 + var10.y * var25, var16 + var10.z * var25).setUv(1.0F, 0.0F).setColor(var5, var6, var7, var27);
                  var10 = new Vector3f(var9);
                  var8++;
               }
            }
         }

         return var8;
      }
   }

   private static int head(BufferBuilder var0, ArrowTrails.Trail var1, double var2, Vec3 var4) {
      double var5;
      double var7;
      double var9;
      float var11;
      if (var1.resting) {
         double var12 = var2 - var1.restSince;
         if (var12 > 60.0) {
            return 0;
         }

         var11 = (float)((0.45 + 0.12 * Math.sin(var2 * 4.0)) * Math.clamp((60.0 - var12) / 8.0, 0.0, 1.0));
         var5 = var1.restX;
         var7 = var1.restY;
         var9 = var1.restZ;
      } else {
         if (var1.size == 0) {
            return 0;
         }

         int var22 = (var1.start + var1.size - 1) % 48;
         var5 = var1.pts[var22 * 3];
         var7 = var1.pts[var22 * 3 + 1];
         var9 = var1.pts[var22 * 3 + 2];
         var11 = 1.0F;
      }

      float var23 = (float)(var5 - var4.x);
      float var13 = (float)(var7 - var4.y);
      float var14 = (float)(var9 - var4.z);
      float var15 = (float)Math.sqrt((double)(var23 * var23 + var13 * var13 + var14 * var14));
      if (var15 < 0.25F) {
         return 0;
      } else {
         float var16 = Math.clamp(0.22F + var15 * 0.014F, 0.22F, 3.0F) * (var1.resting ? 0.8F : 1.0F);
         Matrix4f var17 = new Matrix4f().translation(var23, var13, var14).rotate(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
         int var18 = var1.color >> 16 & 0xFF;
         int var19 = var1.color >> 8 & 0xFF;
         int var20 = var1.color & 0xFF;
         int var21 = (int)(255.0F * var11);
         var0.addVertex(var17, -var16, -var16, 0.0F).setUv(-1.0F, -1.0F).setColor(var18, var19, var20, var21);
         var0.addVertex(var17, var16, -var16, 0.0F).setUv(1.0F, -1.0F).setColor(var18, var19, var20, var21);
         var0.addVertex(var17, var16, var16, 0.0F).setUv(1.0F, 1.0F).setColor(var18, var19, var20, var21);
         var0.addVertex(var17, -var16, var16, 0.0F).setUv(-1.0F, 1.0F).setColor(var18, var19, var20, var21);
         return 1;
      }
   }

   private static void clear() {
      TRAILS.clear();
      if (buffer != null) {
         buffer.close();
         buffer = null;
      }
   }

   @SubscribeEvent
   public static void logout(LoggingOut var0) {
      clear();
   }

   private ArrowTrails() {
   }

   @EventBusSubscriber(
      modid = "frontierhunts",
      value = {Dist.CLIENT},
      bus = Bus.MOD
   )
   public static final class Registration {
      @SubscribeEvent
      public static void shaders(RegisterShadersEvent var0) throws IOException {
         ArrowTrails.clear();
         var0.registerShader(
            new ShaderInstance(var0.getResourceProvider(), FrontierHunts.id("tracer_glow"), DefaultVertexFormat.POSITION_TEX_COLOR),
            var0x -> ArrowTrails.shader = var0x
         );
      }
   }

   private static final class Trail {
      final double[] pts = new double[144];
      final double[] born = new double[48];
      int start;
      int size;
      int color;
      double seen;
      double restX;
      double restY;
      double restZ;
      double restSince = -1.0;
      double dirX;
      double dirY;
      double dirZ;
      boolean resting;

      void add(double var1, double var3, double var5, double var7) {
         if (this.size > 0) {
            int var9 = (this.start + this.size - 1) % 48;
            double var10 = var1 - this.pts[var9 * 3];
            double var12 = var3 - this.pts[var9 * 3 + 1];
            double var14 = var5 - this.pts[var9 * 3 + 2];
            if (var10 * var10 + var12 * var12 + var14 * var14 < 0.0025) {
               return;
            }
         }

         int var16 = (this.start + this.size) % 48;
         if (this.size == 48) {
            this.start = (this.start + 1) % 48;
         } else {
            this.size++;
         }

         this.pts[var16 * 3] = var1;
         this.pts[var16 * 3 + 1] = var3;
         this.pts[var16 * 3 + 2] = var5;
         this.born[var16] = var7;
      }

      void expire(double var1) {
         while (this.size > 0 && var1 - this.born[this.start] > 0.34) {
            this.start = (this.start + 1) % 48;
            this.size--;
         }
      }
   }
}
