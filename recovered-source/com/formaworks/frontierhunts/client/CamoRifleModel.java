package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.rifle.RifleMotion;
import com.formaworks.frontierhunts.rifle.RifleState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

public final class CamoRifleModel {
   public static final float OPTIC_Y = 0.151F;
   public static final float MAG_Z = 0.051F;
   public static final float BOLT_Y = 0.104F;
   public static final float BOLT_Z = 0.148F;
   private static final ResourceLocation TEXTURE = FrontierHunts.id("textures/equipment/rifle/camo_rifle_clean_v3.png");
   private static final RenderType SURFACE = RifleRenderType.createFor(TEXTURE);
   private static final CamoRifleModel[] CACHE = new CamoRifleModel[3];
   private final float[] vertices;
   private final int[][] indices = new int[4][]; // [rifle] 0 body, 1 bolt, 2 magazine, 3 factory scope (split from 0)

   public static void clear() {
      Arrays.fill(CACHE, null);
   }

   private static CamoRifleModel get() {
      // [presets] Cinematic and Standard (BALANCED) effects share the close mesh: the separate "balanced" rifle mesh was
      // removed from the jar with the Balanced preset; Performance keeps the distant one
      byte var0 = switch ((HuntConfig.Quality)HuntConfig.QUALITY.get()) {
         case CINEMATIC, BALANCED -> 0;
         case PERFORMANCE -> 2;
      };
      if (HuntShaderCompat.shadowPass()) {
         var0 = 2;
      }

      if (CACHE[var0] == null) {
         CACHE[var0] = new CamoRifleModel(new String[]{"close", "close", "distant"}[var0]);
      }

      return CACHE[var0];
   }

   private CamoRifleModel(String var1) {
      TextureManager var2 = Minecraft.getInstance().getTextureManager();
      if (!(var2.getTexture(TEXTURE, null) instanceof FilteredRifleTexture)) {
         var2.register(TEXTURE, new FilteredRifleTexture(TEXTURE));
      }

      try {
         try (InputStream var3 = Minecraft.getInstance()
               .getResourceManager()
               .getResourceOrThrow(FrontierHunts.id("models/item/camo_rifle_" + var1 + ".fhrm"))
               .open()) {
            byte[] var4 = var3.readNBytes(4000001);
            if (var4.length > 4000000) {
               throw new IOException("Rifle mesh byte budget");
            }

            ByteBuffer var5 = ByteBuffer.wrap(var4).order(ByteOrder.LITTLE_ENDIAN);
            if (var5.getInt() != 1297238086 || var5.getInt() != 2) {
               throw new IOException("Unsupported rifle mesh");
            }

            int var6 = var5.getInt();
            int var7 = var5.getInt();
            if (var6 < 1 || var6 > 40000 || var7 < 1 || var7 > 60000 || var5.remaining() != var6 * 32 + var7 * 16) {
               throw new IOException("Rifle mesh budget/length");
            }

            this.vertices = new float[var6 * 8];

            for (int var8 = 0; var8 < this.vertices.length; var8++) {
               this.vertices[var8] = var5.getFloat();
               if (!Float.isFinite(this.vertices[var8])) {
                  throw new IOException("Nonfinite rifle vertex");
               }
            }

            int[][] var18 = new int[3][var7 * 3];
            int[] var9 = new int[3];

            for (int var10 = 0; var10 < var7; var10++) {
               int var11 = var5.getInt();
               int var12 = var5.getInt();
               int var13 = var5.getInt();
               int var14 = var5.getInt();
               if (var11 < 0 || var11 >= var6 || var12 < 0 || var12 >= var6 || var13 < 0 || var13 >= var6 || var14 < 0 || var14 > 2) {
                  throw new IOException("Invalid rifle face");
               }

               var18[var14][var9[var14]++] = var11;
               var18[var14][var9[var14]++] = var12;
               var18[var14][var9[var14]++] = var13;
            }

            for (int var19 = 0; var19 < 3; var19++) {
               this.indices[var19] = Arrays.copyOf(var18[var19], var9[var19]);
            }

            this.splitScope();
         }
      } catch (BufferUnderflowException | IOException var17) {
         throw new IllegalStateException("Cannot load supplied rifle mesh", var17);
      }
   }

   /**
    * [rifle] Separates the factory scope (tube, turrets, rings) from the body at load time so it can be left off when
    * another optic is fitted. The supplied geometry is untouched: every triangle is drawn exactly as before when the
    * scope is on. Classified by triangle centroid: above the receiver top / Weaver base tops inside the scope's span
    * (the low objective bell dips slightly lower ahead of the front base). The bases stay with the rifle.
    */
   private void splitScope() {
      int[] body = this.indices[0];
      int[] keep = new int[body.length];
      int[] scope = new int[body.length];
      int k = 0;
      int n = 0;
      for (int i = 0; i + 2 < body.length; i += 3) {
         float y = 0.0F;
         float z = 0.0F;
         for (int j = 0; j < 3; j++) {
            y += this.vertices[body[i + j] * 8 + 1];
            z += this.vertices[body[i + j] * 8 + 2];
         }
         y /= 3.0F;
         z /= 3.0F;
         boolean inScope = z > -0.13F && z < 0.24F && (y > 0.124F || y > 0.1215F && z < -0.05F);
         // [gunsmith] the ring clamps wrap the Weaver bases: their side wings (wider than the 17 mm base) and any
         // triangle that rises above the base top belong to the rings, else they stay behind as ragged black spikes
         // on the bases once the factory scope is off (irons / red dots / field optics)
         if (!inScope && (z > -0.055F && z < 0.02F || z > 0.08F && z < 0.15F)) {
            float x = 0.0F;
            float top = -1.0F;
            for (int j = 0; j < 3; j++) {
               x += this.vertices[body[i + j] * 8];
               top = Math.max(top, this.vertices[body[i + j] * 8 + 1]);
            }
            x /= 3.0F;
            inScope = Math.abs(x + 4.0E-4F) > 0.0092F && y > 0.1165F || top > 0.1252F;
         }
         int[] into = inScope ? scope : keep;
         int at = inScope ? n : k;
         into[at] = body[i];
         into[at + 1] = body[i + 1];
         into[at + 2] = body[i + 2];
         if (inScope) {
            n += 3;
         } else {
            k += 3;
         }
      }
      this.indices[0] = Arrays.copyOf(keep, k);
      this.indices[3] = Arrays.copyOf(scope, n);
   }

   public static void draw(PoseStack var0, MultiBufferSource var1, int var2, RifleState var3, double var4) {
      draw(var0, var1, var2, var3, var4, true);
   }

   /** [rifle] The factory scope (with its lens glass) on its own, for the loose Ridgeline Hunting Scope item. */
   public static void drawScope(PoseStack pose, MultiBufferSource buffers, int light) {
      get().part(3, pose, buffers.getBuffer(SURFACE), light);
      RifleOptics.draw(pose, buffers, light);
   }

   /** [rifle] {@code stockScope}: draw the factory scope (false when another optic or the iron sights are fitted). */
   public static void draw(PoseStack var0, MultiBufferSource var1, int var2, RifleState var3, double var4, boolean stockScope) {
      CamoRifleModel var6 = get();
      VertexConsumer var7 = var1.getBuffer(SURFACE);
      var6.part(0, var0, var7, var2);
      if (stockScope) {
         var6.part(3, var0, var7, var2);
      }
      float var8 = RifleMotion.progress(var3, var4);
      float var9 = var3.action() == 1 ? var8 : 0.0F;
      var0.pushPose();
      var0.translate(0.0F, 0.104F, 0.148F + RifleMotion.pull(var9));
      var0.mulPose(Axis.ZP.rotationDegrees(RifleMotion.lift(var9)));
      var0.translate(0.0F, -0.104F, -0.148F);
      var6.part(1, var0, var7, var2);
      var0.popPose();
      var0.pushPose();
      magazinePose(var0, var3, var8);
      if (var3.limit() == 3) {
         var6.part(2, var0, var7, var2);
      }

      var0.popPose();
      VertexConsumer var10 = var1.getBuffer(RenderType.entityCutoutNoCull(WhitetailRenderer.MATERIAL));
      box(var0, var10, var2, 2238762, -0.018, 0.091, 0.012, 0.018, 0.094, 0.094);

      for (int var14 : new int[]{-1, 1}) {
         box(var0, var10, var2, 6381137, (double)var14 * 0.018 - 0.0015, 0.094, 0.012, (double)var14 * 0.018 + 0.0015, 0.102, 0.094);
      }

      box(var0, var10, var2, 1317402, -0.015, 0.086, 0.003, 0.015, 0.089, 0.099);

      for (int var23 : new int[]{-1, 1}) {
         box(var0, var10, var2, 2370602, (double)var23 * 0.015 - 0.001, 0.053, 0.003, (double)var23 * 0.015 + 0.001, 0.087, 0.099);
      }

      for (double var24 : new double[]{0.003, 0.099}) {
         box(var0, var10, var2, 2107172, -0.015, 0.053, var24 - 0.001, 0.015, 0.087, var24 + 0.001);
      }

      var0.pushPose();
      var0.translate(0.0F, 0.0F, RifleMotion.pull(var9));
      HuntMesh.tube(var0, var10, var2, 10329753, 0.0, 0.104F, 0.013, 0.0, 0.104F, 0.174, 0.01, 0.01, 24);
      var0.popPose();
      var0.pushPose();
      var0.translate(0.0F, 0.104F, 0.148F + RifleMotion.pull(var9));
      var0.mulPose(Axis.ZP.rotationDegrees(RifleMotion.lift(var9)));
      var0.translate(0.0F, -0.104F, -0.148F);
      HuntMesh.tube(var0, var10, var2, 9472375, 0.005, 0.104, 0.148, 0.03, 0.09, 0.15, 0.004, 0.004, 16);
      var0.popPose();
      var0.pushPose();
      magazinePose(var0, var3, var8);
      if (var3.limit() == 3) {
         box(var0, var10, var2, 2370345, -0.0135, 0.051, 0.004, 0.0135, 0.085, 0.098);
      }

      if (var3.limit() == 3) {
         box(var0, var10, var2, 1054486, -0.01, 0.085, 0.009, 0.01, 0.087, 0.094);
      }

      if (var3.magazine() > 0) {
         var0.pushPose();
         var0.translate(0.0, 0.088, 0.052);
         RidgelineModel.cartridge(var0, var10, var2, false);
         var0.popPose();
      }

      var0.popPose();
      HuntMesh.tube(var0, var10, var2, 2567466, 0.0, 0.1014, -0.6658, 0.0, 0.1014, -0.6662, 0.0031, 0.0031, 18);
      if (var9 > 0.31F && var9 < 0.6F) {
         float var18 = (var9 - 0.31F) / 0.29F;
         var0.pushPose();
         var0.translate(0.023 + (double)var18 * 0.18, 0.11 + 0.085 * Math.sin((double)var18 * Math.PI), 0.05 + (double)RifleMotion.pull(var9));
         var0.mulPose(Axis.ZP.rotationDegrees(var18 * 310.0F));
         RidgelineModel.cartridge(var0, var10, var2, var3.spent());
         var0.popPose();
      }

      if (stockScope) {
         RifleOptics.draw(var0, var1, var2);
      }
   }

   private static void magazinePose(PoseStack var0, RifleState var1, float var2) {
      if (var1.action() == 2) {
         float var3 = RifleMotion.magazine(var2);
         var0.translate(0.0F, -var3, 0.051F);
         var0.mulPose(Axis.XP.rotationDegrees(Math.max(0.0F, var3 - 0.07F) * 75.0F));
         var0.translate(0.0F, 0.0F, -0.051F);
      }
   }

   private void part(int var1, PoseStack var2, VertexConsumer var3, int var4) {
      Pose var5 = var2.last();
      int[] var6 = this.indices[var1];

      for (int var10 : var6) {
         int var11 = var10 * 8;
         HuntMesh.vertex(
            var3,
            var5,
            var4,
            16777215,
            this.vertices[var11],
            this.vertices[var11 + 1],
            this.vertices[var11 + 2],
            this.vertices[var11 + 6],
            this.vertices[var11 + 7],
            this.vertices[var11 + 3],
            this.vertices[var11 + 4],
            this.vertices[var11 + 5]
         );
      }
   }

   private static void box(
      PoseStack var0, VertexConsumer var1, int var2, int var3, double var4, double var6, double var8, double var10, double var12, double var14
   ) {
      double[][] var16 = new double[][]{
         {var4, var6, var8},
         {var10, var6, var8},
         {var10, var12, var8},
         {var4, var12, var8},
         {var4, var6, var14},
         {var10, var6, var14},
         {var10, var12, var14},
         {var4, var12, var14}
      };
      int[][] var17 = new int[][]{{0, 3, 2, 1}, {5, 6, 7, 4}, {4, 7, 3, 0}, {1, 2, 6, 5}, {3, 7, 6, 2}, {4, 0, 1, 5}};
      int[][] var18 = new int[][]{{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
      Pose var19 = var0.last();

      for (int var20 = 0; var20 < 6; var20++) {
         for (int var24 : var17[var20]) {
            double[] var25 = var16[var24];
            int[] var26 = var18[var20];
            HuntMesh.vertex(
               var1, var19, var2, var3, (float)var25[0], (float)var25[1], (float)var25[2], 0.5F, 0.5F, (float)var26[0], (float)var26[1], (float)var26[2]
            );
         }
      }
   }
}
