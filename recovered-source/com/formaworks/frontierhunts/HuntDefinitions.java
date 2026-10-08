package com.formaworks.frontierhunts;

import com.formaworks.frontierhunts.progression.Assignment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Comparator;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.registries.DataPackRegistryEvent.NewRegistry;

public final class HuntDefinitions {
   public static final ResourceKey<Registry<HuntDefinitions.Region>> REGIONS = key("region");
   private static final Codec<String> TEXT = Codec.string(1, 1024);
   private static final Codec<String> NAME = Codec.string(1, 80);

   private static <T> ResourceKey<Registry<T>> key(String var0) {
      return ResourceKey.createRegistryKey(FrontierHunts.id(var0));
   }

   public static void register(NewRegistry var0) {
      var0.dataPackRegistry(Assignment.REGISTRY, Assignment.CODEC, Assignment.CODEC);
      var0.dataPackRegistry(REGIONS, HuntDefinitions.Region.CODEC, HuntDefinitions.Region.CODEC);
   }

   public static HuntDefinitions.Region region(RegistryAccess var0, Holder<Biome> var1) {
      return var0.registryOrThrow(REGIONS)
         .stream()
         .filter(var1x -> var1.is(TagKey.create(Registries.BIOME, var1x.biomeTag)))
         .sorted(Comparator.comparingInt(HuntDefinitions.Region::priority).reversed().thenComparing(HuntDefinitions.Region::name))
         .findFirst()
         .orElse(
            new HuntDefinitions.Region(
               "Uncharted country", 1, 0, FrontierHunts.id("uncharted"), "An unmapped habitat. Record its terrain and wildlife before planning an expedition."
            )
         );
   }

   private HuntDefinitions() {
   }

   public static record Region(String name, int threat, int priority, ResourceLocation biomeTag, String description) {
      public static final Codec<HuntDefinitions.Region> CODEC = RecordCodecBuilder.create(
         var0 -> var0.group(
                  HuntDefinitions.NAME.fieldOf("name").forGetter(HuntDefinitions.Region::name),
                  Codec.intRange(1, 5).fieldOf("threat").forGetter(HuntDefinitions.Region::threat),
                  Codec.intRange(0, 1000).fieldOf("priority").forGetter(HuntDefinitions.Region::priority),
                  ResourceLocation.CODEC.fieldOf("biome_tag").forGetter(HuntDefinitions.Region::biomeTag),
                  HuntDefinitions.TEXT.fieldOf("description").forGetter(HuntDefinitions.Region::description)
               )
               .apply(var0, HuntDefinitions.Region::new)
      );
   }
}
