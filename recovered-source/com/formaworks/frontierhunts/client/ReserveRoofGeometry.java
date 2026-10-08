package com.formaworks.frontierhunts.client;

import java.util.function.Function;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.SimpleBakedModel.Builder;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public record ReserveRoofGeometry(boolean high, boolean gable, boolean ridge, int tile, float slope) implements IUnbakedGeometry<ReserveRoofGeometry> {
   public static final IGeometryLoader<ReserveRoofGeometry> LOADER = (var0, var1) -> new ReserveRoofGeometry(
         var0.has("high") && var0.get("high").getAsBoolean(),
         var0.has("gable") && var0.get("gable").getAsBoolean(),
         var0.has("ridge") && var0.get("ridge").getAsBoolean(),
         var0.has("tile") ? var0.get("tile").getAsInt() : 1,
         var0.has("slope") ? var0.get("slope").getAsFloat() : 0.5F
      );

   public BakedModel bake(IGeometryBakingContext var1, ModelBaker var2, Function<Material, TextureAtlasSprite> var3, ModelState var4, ItemOverrides var5) {
      TextureAtlasSprite var6 = (TextureAtlasSprite)var3.apply(var1.getMaterial("roof"));
      Builder var7 = new Builder(true, true, true, var1.getTransforms(), var5).particle(var6);
      float var8 = this.high ? 0.5F : 0.0F;
      if (this.ridge) {
         prism(var7, var6, var4, 0.0F, 0.5F, var8, var8 + this.slope * 0.5F, this.gable, this.tile);
         prism(var7, var6, var4, 0.5F, 1.0F, var8 + this.slope * 0.5F, var8, this.gable, this.tile);
      } else {
         if (this.gable) {
            prism(var7, var6, var4, 0.0F, 1.0F, var8, var8 + this.slope, true, this.tile == 8 ? 8 : 9);
         }

         prism(var7, var6, var4, 0.0F, 1.0F, var8, var8 + this.slope, false, this.tile);
      }

      return var7.build();
   }

   private static void prism(Builder var0, TextureAtlasSprite var1, ModelState var2, float var3, float var4, float var5, float var6, boolean var7, int var8) {
      float var9 = var7 ? 0.0F : var5;
      float var10 = var7 ? 0.0F : var6;
      float var11 = var7 ? 0.0F : 0.075F;
      float[][] var12 = new float[][]{
         {var3, var9, 0.0F},
         {var4, var10, 0.0F},
         {var4, var10, 1.0F},
         {var3, var9, 1.0F},
         {var3, var5 + var11, 0.0F},
         {var4, var6 + var11, 0.0F},
         {var4, var6 + var11, 1.0F},
         {var3, var5 + var11, 1.0F}
      };
      int[][] var13 = new int[][]{{4, 7, 6, 5}, {0, 1, 2, 3}, {0, 4, 5, 1}, {3, 2, 6, 7}, {0, 3, 7, 4}, {1, 5, 6, 2}};

      for (int[] var17 : var13) {
         Vector3f var18 = new Vector3f(var12[var17[0]]);
         Vector3f var19 = new Vector3f(var12[var17[1]]).sub(var18).cross(new Vector3f(var12[var17[2]]).sub(var18));
         if ((double)var19.lengthSquared() < 1.0E-10) {
            var19 = new Vector3f(var12[var17[2]]).sub(var18).cross(new Vector3f(var12[var17[3]]).sub(var18));
         }

         if (!((double)var19.lengthSquared() < 1.0E-10)) {
            var19.normalize();
            Matrix4f var20 = var2.getRotation().getMatrix();
            var20.transformDirection(var19);
            QuadBakingVertexConsumer var21 = new QuadBakingVertexConsumer();
            var21.setSprite(var1);
            var21.setDirection(Direction.getNearest(var19.x, var19.y, var19.z));
            var21.setShade(true);
            var21.setHasAmbientOcclusion(true);

            for (int var22 = 0; var22 < 4; var22++) {
               Vector3f var23 = new Vector3f(var12[var17[var22]]).sub(0.5F, 0.5F, 0.5F);
               var20.transformPosition(var23);
               var23.add(0.5F, 0.5F, 0.5F);
               float var24 = (float)(var8 % 4) * 0.25F + 0.00375F + (var22 != 1 && var22 != 2 ? 0.0F : 0.2425F);
               float var25 = (float)(var8 / 4) * 0.25F + 0.00375F + (var22 >= 2 ? 0.2425F : 0.0F);
               var21.addVertex(var23.x, var23.y, var23.z)
                  .setColor(255, 255, 255, 255)
                  .setUv(var1.getU(var24), var1.getV(var25))
                  .setUv2(0, 0)
                  .setNormal(var19.x, var19.y, var19.z);
            }

            var0.addUnculledFace(var21.bakeQuad());
         }
      }
   }
}
