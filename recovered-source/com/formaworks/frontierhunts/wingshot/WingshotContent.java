package com.formaworks.frontierhunts.wingshot;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/** [wingshot] Sound events and the feather/blood particle type of the game birds. */
@EventBusSubscriber(modid = "frontierhunts", bus = EventBusSubscriber.Bus.MOD)
public final class WingshotContent {
   static final String[] SOUNDS = {
      "wingshot.grouse_flush", "wingshot.duck_takeoff", "wingshot.duck_wings", "wingshot.grouse_wings", "wingshot.splash_small", "wingshot.bird_hit",
      "wingshot.body_thud", "wingshot.body_splash", "wingshot.slowmo", "wingshot.grouse_drum_a", "wingshot.grouse_drum_b"
   };
   public static final DeferredHolder<SoundEvent, SoundEvent> FLUSH = sound("wingshot.grouse_flush");
   public static final DeferredHolder<SoundEvent, SoundEvent> DUCK_TAKEOFF = sound("wingshot.duck_takeoff");
   public static final DeferredHolder<SoundEvent, SoundEvent> DUCK_WINGS = sound("wingshot.duck_wings");
   public static final DeferredHolder<SoundEvent, SoundEvent> GROUSE_WINGS = sound("wingshot.grouse_wings");
   public static final DeferredHolder<SoundEvent, SoundEvent> SPLASH_SMALL = sound("wingshot.splash_small");
   public static final DeferredHolder<SoundEvent, SoundEvent> BIRD_HIT = sound("wingshot.bird_hit");
   public static final DeferredHolder<SoundEvent, SoundEvent> BODY_THUD = sound("wingshot.body_thud");
   public static final DeferredHolder<SoundEvent, SoundEvent> BODY_SPLASH = sound("wingshot.body_splash");
   public static final DeferredHolder<SoundEvent, SoundEvent> SLOWMO = sound("wingshot.slowmo");
   public static final DeferredHolder<SoundEvent, SoundEvent> DRUM_A = sound("wingshot.grouse_drum_a");
   public static final DeferredHolder<SoundEvent, SoundEvent> DRUM_B = sound("wingshot.grouse_drum_b");
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FX = DeferredHolder.create(Registries.PARTICLE_TYPE, FrontierHunts.id("wingshot_fx"));

   private WingshotContent() {
   }

   private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
      return DeferredHolder.create(Registries.SOUND_EVENT, FrontierHunts.id(id));
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      e.register(Registries.SOUND_EVENT, helper -> {
         for (String id : SOUNDS) {
            ResourceLocation rl = FrontierHunts.id(id);
            helper.register(rl, SoundEvent.createVariableRangeEvent(rl));
         }
      });
      e.register(Registries.PARTICLE_TYPE, FrontierHunts.id("wingshot_fx"), () -> new SimpleParticleType(true));
   }
}
