package com.formaworks.frontierhunts.landscape;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate.Sampler;

public final class AlpineBiomes extends BiomeSource {
   public static final MapCodec<AlpineBiomes> CODEC = RecordCodecBuilder.mapCodec(
      var0 -> var0.group(
               Biome.CODEC.listOf().fieldOf("biomes").forGetter(var0x -> var0x.biomes),
               Codec.LONG.optionalFieldOf("terrain_seed", 0L).forGetter(var0x -> var0x.layout.seed())
            )
            .apply(var0, AlpineBiomes::new)
   );
   private final List<Holder<Biome>> biomes;
   private volatile AlpineLayout layout = new AlpineLayout(0L);

   public AlpineBiomes(List<Holder<Biome>> var1) {
      this(var1, 0L);
   }

   public AlpineBiomes(List<Holder<Biome>> var1, long var2) {
      if (var1.size() != 6
         && var1.size() != 12
         && var1.size() != 21
         && var1.size() != 24
         && var1.size() != 28
         && var1.size() != 32
         && var1.size() != 36
         && var1.size() != 39) {
         throw new IllegalArgumentException("Alpine preset requires 6, 12, 21, 24, 28, 32, 36 or 39 biome holders");
      } else {
         this.biomes = List.copyOf(var1);
         this.layout = new AlpineLayout(var2);
      }
   }

   void initialize(AlpineLayout var1) {
      this.layout = var1;
   }

   protected MapCodec<? extends BiomeSource> codec() {
      return CODEC;
   }

   protected Stream<Holder<Biome>> collectPossibleBiomes() {
      return this.biomes.stream();
   }

   public Holder<Biome> getNoiseBiome(int var1, int var2, int var3, Sampler var4) {
      int var5 = this.layout.sample((double)var1 * 4.0, (double)var3 * 4.0).biome();
      if (var5 >= 24 && var5 >= this.biomes.size()) {
         var5 = switch (var5) {
            case 24 -> 8;
            case 25 -> 7;
            case 26 -> 3;
            case 27 -> 2;
            case 28 -> 4;
            case 29 -> 1;
            case 30 -> 11;
            case 31 -> 16;
            case 32 -> 4;
            case 33 -> 8;
            case 34 -> 1;
            case 35 -> 16;
            case 36 -> 3;
            case 37 -> 12;
            case 38 -> 6;
            default -> 16;
         };
      }

      if (var5 >= this.biomes.size()) {
         var5 = this.layout.watershed() != null ? 1 : 0;
      }

      return this.biomes.get(var5);
   }
}
