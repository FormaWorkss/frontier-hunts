package com.formaworks.frontierhunts.academy;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.registries.RegisterEvent;

/** [academy] Original synthesized academy sounds (tools/academy/synth_audio.py), registered on both sides. */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class AcademySounds {
   public static final SoundEvent RING = event("academy.ring");
   public static final SoundEvent RING_GOLD = event("academy.ring_gold");
   public static final SoundEvent TICK = event("academy.tick");
   public static final SoundEvent PASSED = event("academy.passed");
   public static final SoundEvent ARRIVE = event("academy.arrive");
   public static final SoundEvent DEPART = event("academy.depart");
   public static final SoundEvent BUSTED = event("academy.busted");
   public static final SoundEvent PAGE = event("academy.page");
   private static final SoundEvent[] ALL = {RING, RING_GOLD, TICK, PASSED, ARRIVE, DEPART, BUSTED, PAGE};

   private AcademySounds() {
   }

   private static SoundEvent event(String path) {
      return SoundEvent.createVariableRangeEvent(FrontierHunts.id(path));
   }

   @SubscribeEvent
   public static void register(RegisterEvent event) {
      event.register(Registries.SOUND_EVENT, helper -> {
         for (SoundEvent s : ALL) {
            helper.register(s.getLocation(), s);
         }
      });
   }
}
