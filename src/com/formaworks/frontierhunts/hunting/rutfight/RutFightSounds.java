package com.formaworks.frontierhunts.hunting.rutfight;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * [rutfight] Single antler clacks and the grinding of locked racks, cut from the mod's own antler-rattle recordings
 * (tools/rutfight/cut_sounds.py), registered on both sides.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class RutFightSounds {
   public static final SoundEvent CLASH = SoundEvent.createVariableRangeEvent(FrontierHunts.id("deer_antler_clash"));
   public static final SoundEvent GRIND = SoundEvent.createVariableRangeEvent(FrontierHunts.id("deer_antler_grind"));

   private RutFightSounds() {
   }

   @SubscribeEvent
   public static void register(RegisterEvent event) {
      event.register(Registries.SOUND_EVENT, helper -> {
         helper.register(CLASH.getLocation(), CLASH);
         helper.register(GRIND.getLocation(), GRIND);
      });
   }
}
