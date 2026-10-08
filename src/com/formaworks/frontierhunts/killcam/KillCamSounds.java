package com.formaworks.frontierhunts.killcam;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Original synthesized kill cam audio layers (registered on both sides so the ids exist everywhere). */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class KillCamSounds {
   public static final SoundEvent SLOWMO_IN = event("killcam.slowmo_in");
   public static final SoundEvent FLIGHT = event("killcam.flight");
   public static final SoundEvent HEARTBEAT = event("killcam.heartbeat");
   public static final SoundEvent IMPACT = event("killcam.impact");
   public static final SoundEvent IMPACT_ARROW = event("killcam.impact_arrow");
   public static final SoundEvent XRAY = event("killcam.xray");
   public static final SoundEvent THUD = event("killcam.thud");
   public static final SoundEvent RETURN = event("killcam.return");
   private static final SoundEvent[] ALL = {SLOWMO_IN, FLIGHT, HEARTBEAT, IMPACT, IMPACT_ARROW, XRAY, THUD, RETURN};

   private KillCamSounds() {
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
