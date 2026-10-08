package com.formaworks.frontierhunts.sign.work;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * [deersign] Sounds of a buck making sign (original, synthesized: tools/deersign/gen_sounds.py): antlers rasping bark,
 * a hoof pawing through litter and soil, the licking branch rustling and snapping, a quiet trickle. Registered on both
 * sides; played by the clients from the synced work (SignWorkClient).
 */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class SignSounds {
   public static final SoundEvent RUB = SoundEvent.createVariableRangeEvent(FrontierHunts.id("deer_rub"));
   public static final SoundEvent PAW = SoundEvent.createVariableRangeEvent(FrontierHunts.id("deer_paw"));
   public static final SoundEvent BRANCH = SoundEvent.createVariableRangeEvent(FrontierHunts.id("deer_licking_branch"));
   public static final SoundEvent SNAP = SoundEvent.createVariableRangeEvent(FrontierHunts.id("deer_twig_snap"));
   public static final SoundEvent TRICKLE = SoundEvent.createVariableRangeEvent(FrontierHunts.id("deer_trickle"));

   private SignSounds() {
   }

   @SubscribeEvent
   public static void register(RegisterEvent event) {
      event.register(Registries.SOUND_EVENT, helper -> {
         for (SoundEvent s : new SoundEvent[]{RUB, PAW, BRANCH, SNAP, TRICKLE}) {
            helper.register(s.getLocation(), s);
         }
      });
   }
}
