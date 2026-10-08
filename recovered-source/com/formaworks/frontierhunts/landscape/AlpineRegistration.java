package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.environment.WildLeaves;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.OffsetType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(
   modid = "frontierhunts",
   bus = Bus.MOD
)
public final class AlpineRegistration {
   public static final String[] BOULDER_IDS = new String[]{
      "river_pebbles", "river_stone", "river_boulder", "mossy_river_pebbles", "mossy_river_stone", "mossy_river_boulder"
   };
   public static final String[] THICKET_IDS = new String[]{
      "woodland_bush",
      "spreading_fern",
      "river_brush",
      "flowering_bramble",
      "arching_fern",
      "broadleaf_thicket",
      "huckleberry_shrub",
      "juniper_shrub",
      "spruce_seedling",
      "fireweed",
      "heather",
      "sagebrush",
      "cottongrass"
   };
   public static final String[] LOG_IDS = new String[]{
      "alpine_spruce_log", "alpine_pine_log", "alpine_maple_log", "alpine_alder_log", "alpine_rowan_log", "alpine_cottonwood_log"
   };
   public static final String[] LEAF_IDS = new String[]{
      "spruce_boughs", "golden_aspen_leaves", "autumn_maple_leaves", "blue_spruce_boughs", "larch_needles", "alder_leaves", "rowan_leaves", "cottonwood_leaves"
   };
   public static final DeferredHolder<Block, AlpineWeatheredRock> WEATHERED_ROCK = DeferredHolder.create(
      Registries.BLOCK, FrontierHunts.id("weathered_river_rock")
   );
   public static final DeferredHolder<Block, AlpineFlow> FLOW = DeferredHolder.create(Registries.BLOCK, FrontierHunts.id("alpine_flow"));
   public static final DeferredHolder<Block, AlpinePasture> PASTURE = DeferredHolder.create(Registries.BLOCK, FrontierHunts.id("alpine_pasture"));
   public static final DeferredHolder<Block, AlpineCascade> CASCADE = DeferredHolder.create(Registries.BLOCK, FrontierHunts.id("alpine_cascade"));
   public static final DeferredHolder<Block, AlpineTurf> TURF = DeferredHolder.create(Registries.BLOCK, FrontierHunts.id("alpine_turf"));
   public static final DeferredHolder<Block, AlpineOvergrowth> OVERGROWTH = DeferredHolder.create(Registries.BLOCK, FrontierHunts.id("alpine_overgrowth"));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPRAY_PARTICLE = DeferredHolder.create(
      Registries.PARTICLE_TYPE, FrontierHunts.id("alpine_spray")
   );
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BUBBLE_PARTICLE = DeferredHolder.create(
      Registries.PARTICLE_TYPE, FrontierHunts.id("alpine_entrained_air")
   );
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MIST_PARTICLE = DeferredHolder.create(
      Registries.PARTICLE_TYPE, FrontierHunts.id("falls_mist")
   );
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLUSH_BIRD = particle("flush_bird");
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SOARING_HAWK = particle("soaring_hawk");
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RAVEN = particle("raven");
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RISING_FISH = particle("rising_fish");
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BREATH = particle("breath");
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SONGBIRD = particle("songbird");
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLOCK_BIRD = particle("flock_bird");
   public static final String[] LIFE_PARTICLES = new String[]{"flush_bird", "soaring_hawk", "raven", "rising_fish", "breath", "songbird", "flock_bird"};
   public static final String[] FALLS_SOUNDS = new String[]{"falls_roar_far", "falls_roar", "falls_crash", "falls_trickle", "falls_underwater"};
   public static final String[] AMBIENT_SOUNDS = new String[]{
      "amb_wind_high",
      "amb_wind_trees",
      "amb_creek",
      "amb_meadow",
      "amb_night",
      "amb_bird_song",
      "amb_bird_chirp",
      "amb_woodpecker",
      "amb_raven",
      "amb_hawk",
      "amb_pika",
      "amb_owl",
      "amb_coyote",
      "amb_loon",
      "amb_frog",
      "wildlife_flush",
      "fish_splash",
      "amb_song_thrush",
      "amb_song_warbler",
      "amb_song_chickadee"
   };
   public static final DeferredHolder<SoundEvent, SoundEvent> FALLS_ROAR_FAR = sound("falls_roar_far");
   public static final DeferredHolder<SoundEvent, SoundEvent> FALLS_ROAR = sound("falls_roar");
   public static final DeferredHolder<SoundEvent, SoundEvent> FALLS_CRASH = sound("falls_crash");
   public static final DeferredHolder<SoundEvent, SoundEvent> FALLS_TRICKLE = sound("falls_trickle");
   public static final DeferredHolder<SoundEvent, SoundEvent> FALLS_UNDERWATER = sound("falls_underwater");
   public static final DeferredHolder<Block, AlpineSpray> SPRAY = DeferredHolder.create(Registries.BLOCK, FrontierHunts.id("alpine_spray"));
   public static final DeferredHolder<Block, AlpineFoam> FOAM = DeferredHolder.create(Registries.BLOCK, FrontierHunts.id("alpine_foam"));
   public static final DeferredHolder<Block, AlpineTracks> TRACKS = DeferredHolder.create(Registries.BLOCK, FrontierHunts.id("tracks"));
   public static final DeferredHolder<Block, AlpineRock> ROCK = DeferredHolder.create(Registries.BLOCK, FrontierHunts.id("alpine_rock"));

   public static Block prop(String var0) {
      return (Block)BuiltInRegistries.BLOCK.get(FrontierHunts.id(var0));
   }

   private static DeferredHolder<ParticleType<?>, SimpleParticleType> particle(String var0) {
      return DeferredHolder.create(Registries.PARTICLE_TYPE, FrontierHunts.id(var0));
   }

   private static DeferredHolder<SoundEvent, SoundEvent> sound(String var0) {
      return DeferredHolder.create(Registries.SOUND_EVENT, FrontierHunts.id(var0));
   }

   @SubscribeEvent
   public static void register(RegisterEvent var0) {
      for (String var4 : new String[]{"river_outcrop", "mossy_outcrop"}) {
         var0.register(Registries.BLOCK, FrontierHunts.id(var4), () -> new AlpineOutcrop(Properties.ofFullCopy(Blocks.STONE).noOcclusion().noLootTable()));
      }

      for (String var29 : new String[]{"giant_boulder", "mossy_giant_boulder"}) {
         var0.register(Registries.BLOCK, FrontierHunts.id(var29), () -> new AlpineGiantBoulder(Properties.ofFullCopy(Blocks.STONE).noOcclusion().noLootTable()));
      }

      for (int var6 = 0; var6 < BOULDER_IDS.length; var6++) {
         String var14 = BOULDER_IDS[var6];
         int var22 = var6 % 3;
         var0.register(Registries.BLOCK, FrontierHunts.id(var14), () -> new AlpineBoulder(Properties.ofFullCopy(Blocks.STONE).noOcclusion(), var22));
         var0.register(Registries.ITEM, FrontierHunts.id(var14), () -> new BlockItem(prop(var14), new net.minecraft.world.item.Item.Properties()));
      }

      for (String var30 : THICKET_IDS) {
         var0.register(
            Registries.BLOCK,
            FrontierHunts.id(var30),
            () -> new AlpineThicket(Properties.of().replaceable().noCollission().noOcclusion().instabreak().sound(SoundType.GRASS))
         );
         var0.register(Registries.ITEM, FrontierHunts.id(var30), () -> new BlockItem(prop(var30), new net.minecraft.world.item.Item.Properties()));
      }

      for (String var31 : LOG_IDS) {
         var0.register(Registries.BLOCK, FrontierHunts.id(var31), () -> new RotatedPillarBlock(Properties.ofFullCopy(Blocks.SPRUCE_LOG)));
         var0.register(Registries.ITEM, FrontierHunts.id(var31), () -> new BlockItem(prop(var31), new net.minecraft.world.item.Item.Properties()));
      }

      for (String var32 : LEAF_IDS) {
         var0.register(Registries.BLOCK, FrontierHunts.id(var32), () -> new WildLeaves(Properties.ofFullCopy(Blocks.SPRUCE_LEAVES)));
         var0.register(Registries.ITEM, FrontierHunts.id(var32), () -> new BlockItem(prop(var32), new net.minecraft.world.item.Item.Properties()));
      }

      var0.register(Registries.BLOCK, FrontierHunts.id("weathered_river_rock"), () -> new AlpineWeatheredRock(Properties.ofFullCopy(Blocks.STONE)));
      var0.register(
         Registries.ITEM,
         FrontierHunts.id("weathered_river_rock"),
         () -> new BlockItem((Block)WEATHERED_ROCK.get(), new net.minecraft.world.item.Item.Properties())
      );
      var0.register(
         Registries.BLOCK,
         FrontierHunts.id("alpine_flow"),
         () -> new AlpineFlow(Properties.of().replaceable().noCollission().noOcclusion().instabreak().noLootTable())
      );
      var0.register(
         Registries.BLOCK,
         FrontierHunts.id("alpine_pasture"),
         () -> new AlpinePasture(
               Properties.of().replaceable().noCollission().noOcclusion().instabreak().noLootTable().sound(SoundType.GRASS).offsetType(OffsetType.XZ)
            )
      );
      var0.register(
         Registries.BLOCK,
         FrontierHunts.id("alpine_cascade"),
         () -> new AlpineCascade(
               Properties.of()
                  .mapColor(MapColor.WATER)
                  .noCollission()
                  .noOcclusion()
                  .noLootTable()
                  .strength(-1.0F, 3600000.0F)
                  .sound(SoundType.EMPTY)
                  .pushReaction(PushReaction.DESTROY)
                  .isValidSpawn((var0x, var1, var2, var3) -> false)
                  .isSuffocating((var0x, var1, var2) -> false)
                  .isViewBlocking((var0x, var1, var2) -> false)
            )
      );
      var0.register(
         Registries.BLOCK,
         FrontierHunts.id("alpine_turf"),
         () -> new AlpineTurf(
               Properties.of()
                  .mapColor(MapColor.GRASS)
                  .strength(0.6F)
                  .sound(SoundType.GRASS)
                  .forceSolidOn()
                  .noLootTable()
                  .isValidSpawn((var0x, var1, var2, var3) -> true)
            )
      );
      var0.register(
         Registries.BLOCK,
         FrontierHunts.id("reeds"),
         () -> new AlpineReeds(
               Properties.of()
                  .mapColor(MapColor.PLANT)
                  .noCollission()
                  .noOcclusion()
                  .instabreak()
                  .sound(SoundType.GRASS)
                  .offsetType(OffsetType.XZ)
                  .pushReaction(PushReaction.DESTROY)
            )
      );
      var0.register(Registries.ITEM, FrontierHunts.id("reeds"), () -> new BlockItem(prop("reeds"), new net.minecraft.world.item.Item.Properties()));
      var0.register(
         Registries.BLOCK,
         FrontierHunts.id("forest_sticks"),
         () -> new AlpineSticks(
               Properties.of()
                  .mapColor(MapColor.PODZOL)
                  .replaceable()
                  .noCollission()
                  .noOcclusion()
                  .instabreak()
                  .sound(SoundType.WOOD)
                  .pushReaction(PushReaction.DESTROY)
                  .ignitedByLava()
            )
      );
      var0.register(
         Registries.ITEM, FrontierHunts.id("forest_sticks"), () -> new BlockItem(prop("forest_sticks"), new net.minecraft.world.item.Item.Properties())
      );
      var0.register(
         Registries.BLOCK,
         FrontierHunts.id("branch_stub"),
         () -> new AlpineBranchStub(
               Properties.of()
                  .mapColor(MapColor.WOOD)
                  .noCollission()
                  .noOcclusion()
                  .strength(0.3F)
                  .sound(SoundType.WOOD)
                  .pushReaction(PushReaction.DESTROY)
                  .ignitedByLava()
            )
      );
      var0.register(Registries.ITEM, FrontierHunts.id("branch_stub"), () -> new BlockItem(prop("branch_stub"), new net.minecraft.world.item.Item.Properties()));
      var0.register(
         Registries.BLOCK,
         FrontierHunts.id("sapling_pole"),
         () -> new AlpinePole(
               Properties.of()
                  .mapColor(MapColor.WOOD)
                  .noOcclusion()
                  .strength(0.8F)
                  .sound(SoundType.WOOD)
                  .pushReaction(PushReaction.DESTROY)
                  .ignitedByLava()
                  .isSuffocating((var0x, var1, var2) -> false)
                  .isViewBlocking((var0x, var1, var2) -> false)
            )
      );
      var0.register(
         Registries.ITEM, FrontierHunts.id("sapling_pole"), () -> new BlockItem(prop("sapling_pole"), new net.minecraft.world.item.Item.Properties())
      );
      var0.register(
         Registries.BLOCK,
         FrontierHunts.id("tracks"),
         () -> new AlpineTracks(
               Properties.of()
                  .replaceable()
                  .noCollission()
                  .noOcclusion()
                  .instabreak()
                  .noLootTable()
                  .randomTicks()
                  .sound(SoundType.EMPTY)
                  .pushReaction(PushReaction.DESTROY)
                  .isValidSpawn((var0x, var1, var2, var3) -> false)
            )
      );
      var0.register(
         Registries.BLOCK,
         FrontierHunts.id("alpine_overgrowth"),
         () -> new AlpineOvergrowth(Properties.of().replaceable().noCollission().noOcclusion().instabreak().noLootTable().sound(SoundType.MOSS_CARPET))
      );
      var0.register(Registries.PARTICLE_TYPE, FrontierHunts.id("alpine_spray"), () -> new SimpleParticleType(false));
      var0.register(Registries.PARTICLE_TYPE, FrontierHunts.id("alpine_entrained_air"), () -> new SimpleParticleType(false));
      var0.register(Registries.PARTICLE_TYPE, FrontierHunts.id("falls_mist"), () -> new SimpleParticleType(false));

      for (String var33 : LIFE_PARTICLES) {
         var0.register(Registries.PARTICLE_TYPE, FrontierHunts.id(var33), () -> new SimpleParticleType(!var33.equals("breath")));
      }

      for (String var34 : FALLS_SOUNDS) {
         var0.register(Registries.SOUND_EVENT, FrontierHunts.id(var34), () -> SoundEvent.createVariableRangeEvent(FrontierHunts.id(var34)));
      }

      for (String var35 : AMBIENT_SOUNDS) {
         var0.register(Registries.SOUND_EVENT, FrontierHunts.id(var35), () -> SoundEvent.createVariableRangeEvent(FrontierHunts.id(var35)));
      }

      var0.register(
         Registries.BLOCK,
         FrontierHunts.id("alpine_spray"),
         () -> new AlpineSpray(Properties.of().replaceable().noCollission().noOcclusion().instabreak().noLootTable().pushReaction(PushReaction.DESTROY))
      );
      var0.register(Registries.CHUNK_GENERATOR, FrontierHunts.id("alpine"), () -> AlpineGenerator.CODEC);
      var0.register(Registries.BIOME_SOURCE, FrontierHunts.id("alpine"), () -> AlpineBiomes.CODEC);
      var0.register(Registries.FEATURE, FrontierHunts.id("alpine_ecology"), AlpineEcology::new);
      var0.register(Registries.FEATURE, FrontierHunts.id("falls_dressing"), AlpineFallsDressing::new);
      var0.register(
         Registries.BLOCK,
         FrontierHunts.id("alpine_foam"),
         () -> new AlpineFoam(Properties.of().replaceable().noCollission().noOcclusion().instabreak().noLootTable().pushReaction(PushReaction.DESTROY))
      );
      var0.register(Registries.BLOCK, FrontierHunts.id("alpine_rock"), () -> new AlpineRock(Properties.ofFullCopy(Blocks.STONE)));
      var0.register(Registries.ITEM, FrontierHunts.id("alpine_rock"), () -> new BlockItem((Block)ROCK.get(), new net.minecraft.world.item.Item.Properties()));
      var0.register(
         Registries.ITEM, FrontierHunts.id("alpine_pasture"), () -> new BlockItem((Block)PASTURE.get(), new net.minecraft.world.item.Item.Properties())
      );
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void creative(BuildCreativeModeTabContentsEvent var0) {
      if (var0.getTabKey().location().equals(FrontierHunts.id("world"))) {
         var0.accept((ItemLike)WEATHERED_ROCK.get());

         for (String var4 : BOULDER_IDS) {
            var0.accept(prop(var4));
         }

         for (String var14 : LOG_IDS) {
            var0.accept(prop(var14));
         }

         for (String var15 : LEAF_IDS) {
            var0.accept(prop(var15));
         }

         for (String var16 : THICKET_IDS) {
            var0.accept(prop(var16));
         }

         var0.accept(prop("reeds"));
         var0.accept(prop("forest_sticks"));
         var0.accept(prop("branch_stub"));
         var0.accept(prop("sapling_pole"));
         var0.accept((ItemLike)PASTURE.get());
      }
   }
}
