package com.formaworks.frontierhunts.shelter.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.shelter.Shelter;
import com.formaworks.frontierhunts.shelter.ShelterScan;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * [shelter] Client cache of the shared shelter detector at the camera, for everything that presents the storm: screen
 * frost and whiteout, snow particles near the camera, fog, wind/storm sound and the survival frost.
 *
 * The detector runs 4x a second (every 5 ticks; a few dozen block reads, well under a microsecond of logic) and the
 * values ease toward it with a ~0.7 s time constant: stepping into a tent or a cabin fades the storm out over 1-2 s,
 * stepping out fades it back in, a doorway or an overhang gives a partial value. Nothing runs per frame except the
 * interpolation of a few floats.
 */
@EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT)
public final class ShelterClient {
    /** Per-tick easing toward the detector (time constant ~13 ticks: ~95% after 2 s). */
    private static final float EASE = 0.075f;

    private static float enclosure, prevEnclosure, wind, prevWind, precip, prevPrecip, tent, prevTent;
    private static float targetEnclosure, targetWind, targetPrecip, targetTent;
    private static ClientLevel lastLevel;
    private static int ticks;
    private static final ShelterScan.Result RESULT = new ShelterScan.Result();

    private ShelterClient() {}

    /** Runs before the weather / survival client ticks read it (same tick). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            lastLevel = null;
            return;
        }
        boolean fresh = level != lastLevel;
        if (fresh) lastLevel = level;
        if (mc.isPaused() && !fresh) return;
        ticks++;
        if (fresh || ticks % 5 == 0) {
            Camera cam = mc.gameRenderer.getMainCamera();
            Vec3 eye = cam.isInitialized() ? cam.getPosition() : mc.player.getEyePosition();
            try {
                Shelter.scan(mc.player, eye, RESULT);
                targetEnclosure = RESULT.enclosure;
                targetWind = RESULT.windBlock;
                targetPrecip = RESULT.precipBlock;
                targetTent = RESULT.tent ? RESULT.enclosure : 0f;
            } catch (RuntimeException ex) {
                targetEnclosure = targetWind = targetPrecip = targetTent = 0f;
            }
        }
        prevEnclosure = enclosure;
        prevWind = wind;
        prevPrecip = precip;
        prevTent = tent;
        if (fresh) {
            // new world / dimension / respawn: no fade from whatever the last place was
            enclosure = prevEnclosure = targetEnclosure;
            wind = prevWind = targetWind;
            precip = prevPrecip = targetPrecip;
            tent = prevTent = targetTent;
            return;
        }
        enclosure = ease(enclosure, targetEnclosure);
        wind = ease(wind, targetWind);
        precip = ease(precip, targetPrecip);
        tent = ease(tent, targetTent);
    }

    private static float ease(float v, float t) {
        v += (t - v) * EASE;
        return Math.abs(t - v) < 0.002f ? t : v;
    }

    /** 0 outdoors .. 1 inside a tent, a closed room or a cave (smoothed). */
    public static float enclosure() { return enclosure; }

    public static float enclosure(float pt) { return prevEnclosure + (enclosure - prevEnclosure) * pt; }

    /** 1 outdoors .. 0 fully sheltered: multiply storm presentation by this. */
    public static float open() { return 1f - enclosure; }

    public static float open(float pt) { return 1f - enclosure(pt); }

    /** 0..1 how much wind is kept off (walls). */
    public static float windBlock() { return wind; }

    /** 0..1 how much falling snow/rain is kept off (roof, tent, a little for a tree canopy). */
    public static float precipBlock() { return precip; }

    /** 0..1 inside a tent's canvas (thin walls: storm sound is louder than in a cabin). */
    public static float tent() { return tent; }
}
