package com.formaworks.frontierhunts.landscape.ride.grime.client;

import com.formaworks.frontierhunts.landscape.ride.Atv;
import com.formaworks.frontierhunts.landscape.ride.wade.WadeContent;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * [atv2] Looping water layers for one ATV, heard by everyone near it (each client runs its own from what it sees):
 * CHURN (tyres churning, hull pushing water: low gurgle, louder and lower when it is deep and labouring) and SPRAY
 * (wheel spray hiss, rises steeply with speed, fades when the wheels are too deep to throw anything). Volumes are
 * smoothed; a layer stops itself after a second of silence and is restarted by {@link AtvWaterFx} when needed.
 */
final class AtvWaterSound extends AbstractTickableSoundInstance {
    enum Layer { CHURN, SPRAY }

    private final Atv atv;
    private final Layer layer;
    private float gain;
    private int quiet;

    AtvWaterSound(Atv atv, Layer layer) {
        super(layer == Layer.CHURN ? WadeContent.SND_CHURN.get() : WadeContent.SND_SPRAY.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.atv = atv;
        this.layer = layer;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001f;
        this.x = atv.getX();
        this.y = atv.getY();
        this.z = atv.getZ();
    }

    private static float smooth(float e0, float e1, float v) {
        float t = Mth.clamp((v - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Target volume / pitch from what the ATV is doing in the water right now. */
    static float target(Atv atv, Layer layer) {
        float d = atv.grime.lastDepth;
        float sp = (float) Math.abs(atv.shownSpeed);
        float wheels = smooth(0.04f, 0.3f, d);
        if (wheels <= 0f) return 0f;
        float spd = smooth(0.04f, 0.9f, sp);
        if (layer == Layer.CHURN) {
            float deep = smooth(0.9f, 1.8f, d);
            float idle = atv.isVehicle() ? 0.12f : 0.05f; // a running engine shakes the water even standing still
            return wheels * (idle + 0.55f * spd + 0.25f * deep * Math.min(1f, spd * 3f));
        }
        float throwable = 1f - 0.7f * smooth(1.0f, 1.9f, d);
        return wheels * throwable * (float) Math.pow(smooth(0.1f, 0.95f, sp), 1.4) * 0.85f;
    }

    @Override
    public void tick() {
        if (atv.isRemoved()) {
            stop();
            return;
        }
        float t = target(atv, layer);
        float sp = (float) Math.min(1.2, Math.abs(atv.shownSpeed));
        gain += (t - gain) * (t > gain ? 0.25f : 0.08f);
        if (gain < 0.01f) {
            if (++quiet > 20) {
                stop();
                return;
            }
        } else {
            quiet = 0;
        }
        this.volume = Math.max(0.001f, gain);
        float d = atv.grime.lastDepth;
        this.pitch = layer == Layer.CHURN
                ? Mth.clamp(0.78f + 0.3f * sp - 0.12f * smooth(0.9f, 1.8f, d), 0.5f, 2f)
                : Mth.clamp(0.85f + 0.32f * sp, 0.5f, 2f);
        this.x = atv.getX();
        this.y = atv.getY() + 0.3;
        this.z = atv.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
