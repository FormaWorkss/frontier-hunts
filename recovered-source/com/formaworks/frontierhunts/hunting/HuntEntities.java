package com.formaworks.frontierhunts.hunting;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HuntEntities {
   public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, "frontierhunts");
   public static final Map<GameSpecies, DeferredHolder<EntityType<?>, EntityType<Whitetail>>> GAME = new EnumMap<>(GameSpecies.class);
   public static final DeferredHolder<EntityType<?>, EntityType<Whitetail>> WHITETAIL;
   public static final DeferredHolder<EntityType<?>, EntityType<FieldArrow>> ARROW;
   public static final DeferredHolder<EntityType<?>, EntityType<TrackClue>> CLUE;

   public static void register(IEventBus var0) {
      TYPES.register(var0);
      var0.addListener(var0x -> GAME.forEach((var1, var2) -> var0x.put((EntityType)var2.get(), Whitetail.attributes(var1).build())));
      var0.addListener(
         var0x -> GAME.values()
               .forEach(
                  var1 -> var0x.register(
                        (EntityType)var1.get(), SpawnPlacementTypes.ON_GROUND, Types.MOTION_BLOCKING_NO_LEAVES, Whitetail::canSpawn, Operation.REPLACE
                     )
               )
      );
   }

   private HuntEntities() {
   }

   static {
      for (GameSpecies var3 : GameSpecies.values()) {
         GAME.put(
            var3,
            TYPES.register(
               var3.id,
               () -> Builder.of((var1, var2) -> new Whitetail(var1, var2, var3), MobCategory.CREATURE)
                     .sized(var3.width, var3.height)
                     .eyeHeight(var3.eyeHeight)
                     .clientTrackingRange(10)
                     .updateInterval(2)
                     .build("frontierhunts:" + var3.id)
            )
         );
      }

      WHITETAIL = GAME.get(GameSpecies.WHITETAIL);
      ARROW = TYPES.register(
         "field_arrow",
         () -> Builder.of(FieldArrow::new, MobCategory.MISC).sized(0.1F, 0.1F).clientTrackingRange(8).updateInterval(1).build("frontierhunts:field_arrow")
      );
      CLUE = TYPES.register(
         "track_clue",
         () -> Builder.of(TrackClue::new, MobCategory.MISC).sized(0.48F, 0.08F).clientTrackingRange(3).updateInterval(100).build("frontierhunts:track_clue")
      );
   }
}
