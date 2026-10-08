package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class ClassicAntlers {
   static Boolean force;

   private ClassicAntlers() {
   }

   private static boolean vanilla() {
      if (force != null) {
         return force;
      } else {
         try {
            return FrontierGraphics.vanillaAnimals();
         } catch (Throwable var1) {
            return false;
         }
      }
   }

   static int start(GameSpecies var0) {
      if (!vanilla()) {
         return switch (var0) {
            case ELK -> 38;
            case MOOSE -> 41;
            default -> 37;
         };
      } else {
         return switch (var0) {
            case ELK -> 22;
            case MOOSE -> 23;
            default -> 21;
         };
      }
   }

   static long antlerBits(GameSpecies var0) {
      int var1 = start(var0);
      int var2 = Math.min(64, CubeAnimalData.of(var0, vanilla()).count());
      long var3 = 0L;

      for (int var5 = var1; var5 < var2; var5++) {
         var3 |= 1L << var5;
      }

      return var3;
   }

   private static int headBone(GameSpecies var0) {
      if (!vanilla()) {
         return switch (var0) {
            case ELK -> 7;
            case MOOSE -> 7;
            default -> 10;
         };
      } else {
         return switch (var0) {
            case ELK -> 7;
            case MOOSE -> 7;
            default -> 10;
         };
      }
   }

   private static float[] base(GameSpecies var0) {
      if (!vanilla()) {
         return switch (var0) {
            case ELK -> ElkCubes.ANTLER_BASE;
            case MOOSE -> MooseCubes.ANTLER_BASE;
            default -> WhitetailCubes.ANTLER_BASE;
         };
      } else {
         return switch (var0) {
            case ELK -> ElkVanillaCubes.ANTLER_BASE;
            case MOOSE -> MooseVanillaCubes.ANTLER_BASE;
            default -> WhitetailVanillaCubes.ANTLER_BASE;
         };
      }
   }

   private static float[] standHead(GameSpecies var0) {
      if (!vanilla()) {
         return switch (var0) {
            case ELK -> ElkCubes.STAND_HEAD_SKIN;
            case MOOSE -> MooseCubes.STAND_HEAD_SKIN;
            default -> WhitetailCubes.STAND_HEAD_SKIN;
         };
      } else {
         float[][] var1 = switch (var0) {
            case ELK -> ElkVanillaCubes.STAND_SKIN;
            case MOOSE -> MooseVanillaCubes.STAND_SKIN;
            default -> WhitetailVanillaCubes.STAND_SKIN;
         };
         return var1[headBone(var0)];
      }
   }

   static void draw(Pose var0, VertexConsumer var1, int var2, int var3, DeerTraits var4, Matrix4f[] var5, float var6, float var7, float var8) {
      if (var4.buck()) {
         GameSpecies var9 = var4.species();
         CubeAnimalData var10 = CubeAnimalData.of(var9, vanilla());
         int var11 = headBone(var9);
         if (var11 < var5.length) {
            float var12 = Math.max(0.6F, Math.min(1.2F, var4.rackScale()));
            float[] var13 = base(var9);
            Vector3f var14 = new Matrix4f().set(standHead(var9)).invert().transformPosition(new Vector3f(var13[0], var13[1], var13[2]));
            Matrix4f[] var15 = (Matrix4f[])var5.clone();
            var15[var11] = new Matrix4f(var5[var11]).translate(var14).scale(var12).translate(-var14.x, -var14.y, -var14.z);
            long var16 = ~antlerBits(var9);
            CubeAnimal.draw(var0, var1, var2, var3, var10.bones, var10.data, var10.stride, var15, var6, var7, var8, 1.0F, var16);
         }
      }
   }
}
