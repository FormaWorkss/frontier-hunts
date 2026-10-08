package com.formaworks.frontierhunts.wildlife2026;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(
   modid = "frontierhunts",
   bus = Bus.MOD
)
public final class WildlifeContent {
   public static final Map<WildlifeSpecies, DeferredHolder<EntityType<?>, EntityType<WildlifeMob>>> TYPES = new EnumMap<>(
      WildlifeSpecies.class
   );
   public static final Map<WildlifeSpecies, DeferredHolder<Item, Item>> EGGS = new EnumMap<>(
      WildlifeSpecies.class
   );

   public static ResourceLocation id(String name) {
      return ResourceLocation.fromNamespaceAndPath("frontierhunts", name);
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      for (WildlifeSpecies s : TYPES.keySet()) {
         e.register(
            Registries.ENTITY_TYPE,
            id(s.id),
            () -> Builder.<WildlifeMob>of((t, l) -> new WildlifeMob(t, l, s), MobCategory.CREATURE)
                  .sized(s.width, s.height)
                  .eyeHeight(s.height * 0.85F)
                  .clientTrackingRange(10)
                  .updateInterval(2)
                  .build("frontierhunts:" + s.id)
         );
         // [1.1.9] unfinished animals' eggs only work for server operators
         e.register(Registries.ITEM, id(s.id + "_spawn_egg"), () -> Beta.animal(s.id)
            ? new BetaEggItem((Supplier)TYPES.get(s), s.color, 14141866, new Properties())
            : new DeferredSpawnEggItem((Supplier)TYPES.get(s), s.color, 14141866, new Properties()));
      }
   }

   @SubscribeEvent
   public static void attributes(EntityAttributeCreationEvent e) {
      TYPES.forEach(
         (s, t) -> e.put(
               (EntityType)t.get(),
               Mob.createMobAttributes()
                  .add(Attributes.MAX_HEALTH, s.health)
                  .add(Attributes.MOVEMENT_SPEED, s.speed)
                  .add(Attributes.FOLLOW_RANGE, 32.0)
                  .add(Attributes.ATTACK_DAMAGE, s.bird ? 1.0 : 5.0)
                  .add(Attributes.STEP_HEIGHT, s.bird ? 0.6 : 1.0)
                  .build()
            )
      );
   }

   @SubscribeEvent
   public static void placement(RegisterSpawnPlacementsEvent e) {
      TYPES.values()
         .forEach(
            t -> e.register(
                  (EntityType)t.get(),
                  SpawnPlacementTypes.ON_GROUND,
                  Types.MOTION_BLOCKING_NO_LEAVES,
                  (type, level, reason, pos, random) -> level.getRawBrightness(pos, 0) > 8
                        && level.getBlockState(pos.below()).isSolidRender(level, pos.below()),
                  Operation.REPLACE
               )
         );
   }

   @SubscribeEvent
   public static void creative(BuildCreativeModeTabContentsEvent e) {
      if (e.getTabKey().location().equals(id("wildlife"))) {
         // [1.1.9] the eggs of animals that aren't finished are no longer in the tab
         EGGS.forEach((s, t) -> {
            if (!Beta.animal(s.id)) {
               e.accept((ItemLike)t.get());
            }
         });
      }
   }

   static {
      for (WildlifeSpecies s : WildlifeSpecies.values()) {
         if (!s.existing()) {
            TYPES.put(s, DeferredHolder.create(Registries.ENTITY_TYPE, id(s.id)));
            EGGS.put(s, DeferredHolder.create(Registries.ITEM, id(s.id + "_spawn_egg")));
         }
      }
   }
}
