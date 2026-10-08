package com.formaworks.frontierhunts.shelter;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * [shelter] Storm sounds heard from inside a tent, a cabin or a cave: low-passed, narrower versions of the storm beds and
 * gusts (generated from the mod's own storm audio by tools/shelter/gen_muffled_audio.py). The weather audio crossfades
 * between the open and the muffled layer by the shared shelter value.
 */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class ShelterSounds {
    public static final String[] IDS = {
            "weather_blizzard_muffled", "weather_rain_muffled", "weather_dust_muffled", "weather_wind_muffled", "weather_gust_muffled"
    };

    public static final DeferredHolder<SoundEvent, SoundEvent> BLIZZARD = sound("weather_blizzard_muffled");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIN = sound("weather_rain_muffled");
    public static final DeferredHolder<SoundEvent, SoundEvent> DUST = sound("weather_dust_muffled");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND = sound("weather_wind_muffled");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUST = sound("weather_gust_muffled");

    private ShelterSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
        return DeferredHolder.create(Registries.SOUND_EVENT, FrontierHunts.id(id));
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        for (String id : IDS) event.register(Registries.SOUND_EVENT, FrontierHunts.id(id), () -> SoundEvent.createVariableRangeEvent(FrontierHunts.id(id)));
    }
}
