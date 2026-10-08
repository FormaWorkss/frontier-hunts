package com.formaworks.frontierhunts.sticks;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/** [sticks] The placed shooting sticks' entity type and their sounds (the item itself is registered by RangeContent). */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class SticksContent {
   public static final DeferredHolder<EntityType<?>, EntityType<ShootingSticksEntity>> STICKS = DeferredHolder.create(Registries.ENTITY_TYPE,
      FrontierHunts.id("shooting_sticks"));
   /** legs swing out and the locks snap */
   public static final DeferredHolder<SoundEvent, SoundEvent> DEPLOY = sound("sticks.deploy");
   /** locks open, legs slide in, the bundle clatters together */
   public static final DeferredHolder<SoundEvent, SoundEvent> FOLD = sound("sticks.fold");
   /** a lock lever opens, a section slides, the lever snaps shut */
   public static final DeferredHolder<SoundEvent, SoundEvent> ADJUST = sound("sticks.adjust");
   /** the forend settles into the padded yoke */
   public static final DeferredHolder<SoundEvent, SoundEvent> REST = sound("sticks.rest");
   /** the gun comes up off the yoke */
   public static final DeferredHolder<SoundEvent, SoundEvent> LIFT = sound("sticks.lift");

   private SticksContent() {
   }

   private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
      return DeferredHolder.create(Registries.SOUND_EVENT, FrontierHunts.id(id));
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      e.register(Registries.ENTITY_TYPE, h -> h.register(FrontierHunts.id("shooting_sticks"),
         EntityType.Builder.<ShootingSticksEntity>of(ShootingSticksEntity::new, MobCategory.MISC).sized(0.5F, 1.5F).clientTrackingRange(8)
            .updateInterval(20).build("shooting_sticks")));
      e.register(Registries.SOUND_EVENT, h -> {
         for (String id : new String[]{"sticks.deploy", "sticks.fold", "sticks.adjust", "sticks.rest", "sticks.lift"}) {
            ResourceLocation rl = FrontierHunts.id(id);
            h.register(rl, SoundEvent.createVariableRangeEvent(rl));
         }
      });
   }
}
