package com.formaworks.frontierhunts.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

public record FieldShelterGeometry(List<FieldShelterGeometry.Face> faces) implements IUnbakedGeometry<FieldShelterGeometry> {
   public static final IGeometryLoader<FieldShelterGeometry> LOADER = (var0, var1) -> {
      JsonArray var2 = var0.getAsJsonArray("shelter_faces");
      if (var2 != null && var2.size() <= 8192) {
         ArrayList var3 = new ArrayList();

         for (JsonElement var5 : var2) {
            JsonObject var6 = var5.getAsJsonObject();
            JsonArray var7 = var6.getAsJsonArray("v");
            if (var7.size() != 4) {
               throw new JsonParseException("Shelter face requires four vertices");
            }

            ArrayList var8 = new ArrayList();

            for (JsonElement var10 : var7) {
               JsonArray var11 = var10.getAsJsonArray();
               if (var11.size() != 5) {
                  throw new JsonParseException("Shelter vertex must contain XYZ/UV");
               }

               float[] var12 = new float[5];

               for (int var13 = 0; var13 < 5; var13++) {
                  var12[var13] = var11.get(var13).getAsFloat();
                  if (!Float.isFinite(var12[var13])) {
                     throw new JsonParseException("Non-finite shelter vertex");
                  }
               }

               var8.add(new FieldShelterGeometry.Vertex(var12[0], var12[1], var12[2], var12[3], var12[4]));
            }

            var3.add(new FieldShelterGeometry.Face(var6.get("m").getAsString(), var6.has("color") ? var6.get("color").getAsInt() : 16777215, List.copyOf(var8)));
         }

         return new FieldShelterGeometry(List.copyOf(var3));
      } else {
         throw new JsonParseException("Invalid shelter face budget");
      }
   };

   public BakedModel bake(IGeometryBakingContext var1, ModelBaker var2, Function<Material, TextureAtlasSprite> var3, ModelState var4, ItemOverrides var5) {
      Builder var6 = new Builder(true, true, true, var1.getTransforms(), var5).particle((TextureAtlasSprite)var3.apply(var1.getMaterial("particle")));
      Matrix4f var7 = var4.getRotation().getMatrix();
      HashMap var8 = new HashMap();

      for (FieldShelterGeometry.Face var10 : this.faces) {
         TextureAtlasSprite var11 = var8.computeIfAbsent(var10.material(), var2x -> (TextureAtlasSprite)var3.apply(var1.getMaterial(var2x)));
         FieldShelterGeometry.Vertex var12 = var10.vertices().get(0);
         FieldShelterGeometry.Vertex var13 = var10.vertices().get(1);
         FieldShelterGeometry.Vertex var14 = var10.vertices().get(2);
         Vector3f var15 = new Vector3f(var13.x - var12.x, var13.y - var12.y, var13.z - var12.z).cross(var14.x - var12.x, var14.y - var12.y, var14.z - var12.z);
         if (!((double)var15.lengthSquared() < 1.0E-14)) {
            var15.normalize();
            var7.transformDirection(var15);
            QuadBakingVertexConsumer var16 = new QuadBakingVertexConsumer();
            var16.setSprite(var11);
            var16.setDirection(Direction.getNearest(var15.x, var15.y, var15.z));
            var16.setShade(true);
            var16.setHasAmbientOcclusion(true);

            for (FieldShelterGeometry.Vertex var18 : var10.vertices()) {
               Vector3f var19 = new Vector3f(var18.x - 0.5F, var18.y - 0.5F, var18.z - 0.5F);
               var7.transformPosition(var19);
               var19.add(0.5F, 0.5F, 0.5F);
               var16.addVertex(var19.x, var19.y, var19.z)
                  .setColor(var10.color >> 16 & 0xFF, var10.color >> 8 & 0xFF, var10.color & 0xFF, 255)
                  .setUv(var11.getU(var18.u), var11.getV(var18.v))
                  .setUv2(0, 0)
                  .setNormal(var15.x, var15.y, var15.z);
            }

            var6.addUnculledFace(var16.bakeQuad());
         }
      }

      return var6.build();
   }

   public static record Face(String material, int color, List<FieldShelterGeometry.Vertex> vertices) {
   }

   public static record Vertex(float x, float y, float z, float u, float v) {
   }
}
