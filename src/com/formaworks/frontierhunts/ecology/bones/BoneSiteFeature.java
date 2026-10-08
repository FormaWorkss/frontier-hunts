package com.formaworks.frontierhunts.ecology.bones;

import com.formaworks.frontierhunts.ecology.EcologyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * [ecology] Worldgen: a rare weathered skeleton (placed feature {@code frontierhunts:bone_site}, rarity 1/160 per chunk
 * in the {@code has_bone_sites} biomes, halved again here and scaled by {@code boneSiteFrequency}: about one per 320
 * suitable chunks). Never on structures, water or man-made ground.
 */
public final class BoneSiteFeature extends Feature<NoneFeatureConfiguration> {
   public BoneSiteFeature() {
      super(NoneFeatureConfiguration.CODEC);
   }

   @Override
   public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
      if (!EcologyConfig.boneSites()) {
         return false;
      }
      RandomSource r = ctx.random();
      if (r.nextFloat() >= 0.5F * EcologyConfig.boneSiteFrequency()) {
         return false;
      }
      WorldGenLevel level = ctx.level();
      BlockPos origin = ctx.origin();
      if (onStructure(level, origin)) {
         return false;
      }
      BoneSites.Kind kind = BoneSites.pick(level.getBiome(origin), r);
      boolean antlers = switch (kind) {
         case WHITETAIL -> r.nextFloat() < 0.6F;
         case ELK -> r.nextFloat() < 0.55F;
         case MOOSE -> r.nextFloat() < 0.5F;
         default -> true;
      };
      return BoneSites.place(level, origin, kind, true, antlers, r, 2) > 0;
   }

   private static boolean onStructure(WorldGenLevel level, BlockPos pos) {
      try {
         if (level instanceof WorldGenRegion region) {
            return level.getLevel().structureManager().forWorldGenRegion(region).hasAnyStructureAt(pos);
         }
      } catch (RuntimeException e) {
         return true; // unsure: skip the site rather than risk bones in a village
      }
      return false;
   }
}
