package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.hunting.GameSpecies;
import java.util.EnumMap;

public final class CubeAnimalData {
   private static final EnumMap<GameSpecies, CubeAnimalData> BY_SPECIES = new EnumMap<>(GameSpecies.class);
   private static final EnumMap<GameSpecies, CubeAnimalData> VANILLA = new EnumMap<>(GameSpecies.class);
   public final int[] bones;
   public final float[] data;
   public final int stride;
   private final int[] order;

   private CubeAnimalData(int[] var1, float[] var2, int var3) {
      this.bones = var1;
      this.data = var2;
      this.stride = var3;
      this.order = new int[var1.length];
      int var4 = 0;

      while (var4 < this.order.length) {
         this.order[var4] = var4++;
      }
   }

   public static CubeAnimalData of(GameSpecies var0) {
      return of(var0, FrontierGraphics.vanillaAnimals());
   }

   public static CubeAnimalData of(GameSpecies var0, boolean var1) {
      return var1 ? VANILLA.computeIfAbsent(var0, var0x -> {
         return switch (var0x) {
            case ELK -> new CubeAnimalData(ElkVanillaCubes.BONE, ElkVanillaCubes.DATA, 60);
            case MOOSE -> new CubeAnimalData(MooseVanillaCubes.BONE, MooseVanillaCubes.DATA, 60);
            default -> new CubeAnimalData(WhitetailVanillaCubes.BONE, WhitetailVanillaCubes.DATA, 60);
         };
      }) : BY_SPECIES.computeIfAbsent(var0, var0x -> {
         return switch (var0x) {
            case ELK -> new CubeAnimalData(ElkCubes.BONE, ElkCubes.DATA, 60);
            case MOOSE -> new CubeAnimalData(MooseCubes.BONE, MooseCubes.DATA, 60);
            default -> new CubeAnimalData(WhitetailCubes.BONE, WhitetailCubes.DATA, 60);
         };
      });
   }

   public long hideMask(float var1) {
      long var2 = 0L;
      int var4 = Math.min(64, this.order.length);
      int var5 = (int)(Math.min(1.0F, Math.max(0.0F, var1)) * (float)var4);

      for (int var6 = 0; var6 < var5; var6++) {
         var2 |= 1L << this.order[var6];
      }

      return var2;
   }

   public int count() {
      return this.bones.length;
   }
}
