package com.formaworks.frontierhunts.environment;

import com.formaworks.frontierhunts.HuntConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FoliageDensity extends PlacementModifier {
   public static final DeferredRegister<PlacementModifierType<?>> TYPES = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, "frontierhunts");
   public static final FoliageDensity.MapCodecHolder CODEC_HOLDER = new FoliageDensity.MapCodecHolder();
   public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<FoliageDensity>> TYPE = TYPES.register(
      "foliage_density", () -> () -> CODEC_HOLDER.codec
   );
   private final int count;

   public FoliageDensity(int var1) {
      this.count = var1;
   }

   public Stream<BlockPos> getPositions(PlacementContext var1, RandomSource var2, BlockPos var3) {
      double var4 = (double)((Integer)HuntConfig.FOLIAGE_DENSITY.get()).intValue() / 100.0;
      double var6 = (double)this.count * var4;
      int var8 = (int)var6;
      if (var2.nextDouble() < var6 - (double)var8) {
         var8++;
      }

      return var8 <= 0 ? Stream.empty() : IntStream.range(0, var8).mapToObj(var1x -> var3);
   }

   public PlacementModifierType<?> type() {
      return (PlacementModifierType<?>)TYPE.get();
   }

   public static void register(IEventBus var0) {
      TYPES.register(var0);
   }

   public static final class MapCodecHolder {
      public final MapCodec<FoliageDensity> codec = RecordCodecBuilder.mapCodec(
         var0 -> var0.group(Codec.intRange(0, 256).fieldOf("count").forGetter(var0x -> var0x.count)).apply(var0, FoliageDensity::new)
      );
   }
}
