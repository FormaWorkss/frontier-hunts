package com.formaworks.frontierhunts.landscape.ride.wade;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/** [atv2] Sounds for driving through water. Self-registering on the MOD bus. */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class WadeContent {
    static final String[] SOUNDS = {"atv_water_splash", "atv_water_churn", "atv_water_spray", "atv_water_slosh", "atv_water_flood"};
    /** Ploughing into water: impact thump, sheet of water, droplet tail (one-shot, 3 variants). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_SPLASH = sound("atv_water_splash");
    /** Loop: water churned by the tyres and pushed by the hull (low, gurgling). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_CHURN = sound("atv_water_churn");
    /** Loop: spray thrown off the wheels (bright hiss with droplet patter). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_SPRAY = sound("atv_water_spray");
    /** Small sloshes while creeping / bogging in deep water (one-shot, 3 variants). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_SLOSH = sound("atv_water_slosh");
    /** Water in the intake: the engine gulps and gurgles out. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_FLOOD = sound("atv_water_flood");

    private WadeContent() {}

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
        return DeferredHolder.create(Registries.SOUND_EVENT, FrontierHunts.id(id));
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        for (String id : SOUNDS) {
            event.register(Registries.SOUND_EVENT, FrontierHunts.id(id), () -> SoundEvent.createVariableRangeEvent(FrontierHunts.id(id)));
        }
    }
}
