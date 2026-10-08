package com.formaworks.frontierhunts.weather;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Particle types (sprite holders for the client storm effects) and storm sound events. */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class WeatherRegistry {
    public static final String[] PARTICLES = {"weather_streak", "weather_veil", "weather_flake", "weather_leaf"};
    public static final String[] SOUNDS = {
            "weather_blizzard_loop", "weather_rain_loop", "weather_dust_loop", "weather_wind_loop", "weather_gust_howl", "weather_gust_rush"
    };

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STREAK = particle("weather_streak");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VEIL = particle("weather_veil");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLAKE = particle("weather_flake");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LEAF = particle("weather_leaf");

    public static final DeferredHolder<SoundEvent, SoundEvent> BLIZZARD_LOOP = sound("weather_blizzard_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIN_LOOP = sound("weather_rain_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> DUST_LOOP = sound("weather_dust_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_LOOP = sound("weather_wind_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUST_HOWL = sound("weather_gust_howl");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUST_RUSH = sound("weather_gust_rush");

    private WeatherRegistry() {}

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> particle(String id) {
        return DeferredHolder.create(Registries.PARTICLE_TYPE, FrontierHunts.id(id));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
        return DeferredHolder.create(Registries.SOUND_EVENT, FrontierHunts.id(id));
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        for (String id : PARTICLES) event.register(Registries.PARTICLE_TYPE, FrontierHunts.id(id), () -> new SimpleParticleType(true));
        for (String id : SOUNDS) event.register(Registries.SOUND_EVENT, FrontierHunts.id(id), () -> SoundEvent.createVariableRangeEvent(FrontierHunts.id(id)));
    }
}
