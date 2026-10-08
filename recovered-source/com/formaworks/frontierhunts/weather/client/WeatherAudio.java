package com.formaworks.frontierhunts.weather.client;

import com.formaworks.frontierhunts.weather.WeatherConfig;
import com.formaworks.frontierhunts.weather.WeatherRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Layered storm sound on the WEATHER channel: a looping bed per storm type (blizzard roar, downpour, dust hiss,
 * gale) whose level follows severity, gusts and shelter, plus positional gust one-shots (howls in a blizzard, rushing
 * gusts otherwise) that arrive from upwind on the rising edge of each gust. [shelter] Inside a tent, a cabin or a cave
 * the open layer fades to a small leak and a low-passed "through the walls" layer takes over (crossfade over ~1-2 s).
 */
final class WeatherAudio {
    private static final RandomSource R = RandomSource.create();
    private static Bed blizzard, rain, dust, wind;
    /** [shelter] The same storm heard through walls / canvas (low-passed beds), crossfaded in by the shelter value. */
    private static Bed mBlizzard, mRain, mDust, mWind;
    private static int cooldown;
    private static float lastGust;

    private WeatherAudio() {}

    static void tick(Minecraft mc, ClientLevel level, Vec3 cp) {
        boolean on = WeatherConfig.sounds() && WeatherClient.active && mc.player != null && !mc.player.isUnderWater();
        float[] s = WeatherClient.sev;
        // [shelter] open storm layer: full outdoors, a small leak inside (more through a tent's canvas than a cabin wall);
        // muffled layer: the storm through the walls, crossfaded in by the shared shelter value over ~1-2 s
        float open = WeatherClient.exposure;
        float tent = com.formaworks.frontierhunts.shelter.client.ShelterClient.tent();
        float leak = 0.06f + 0.14f * tent;
        float gate = WeatherClient.underground * (leak + (1f - leak) * open);
        float mgate = WeatherClient.underground * (1f - open) * (0.6f + 0.3f * tent);
        float g = WeatherClient.gust;
        // [blizzard2] louder blizzard: roar bed 0.72-1.0 -> 0.85-1.0 (full strength from severity 0.87), and the gale bed
        // layered on top swelling hard with each gust and whiteout (the howl you feel in the chest)
        float dB = WeatherClient.drive(s[WeatherClient.BLIZZARD]);
        float wo = WeatherClient.whiteout;
        float vB = on ? dB * gate * (0.85f + 0.3f * g + 0.2f * wo) : 0f;
        float vR = on ? Math.max(s[WeatherClient.THUNDER], s[WeatherClient.SQUALL] * 0.95f) * gate * 0.8f : 0f;
        float vD = on ? s[WeatherClient.DUST] * gate * (0.7f + 0.3f * g) : 0f;
        float vW = on ? Math.max(Math.max(s[WeatherClient.WIND] * (0.6f + 0.4f * g), 0.35f * s[WeatherClient.SQUALL]) * 0.8f,
                dB * (0.2f + 0.55f * g * g + 0.35f * wo)) * gate : 0f;
        blizzard = bed(mc, blizzard, WeatherRegistry.BLIZZARD_LOOP.get(), vB);
        rain = bed(mc, rain, WeatherRegistry.RAIN_LOOP.get(), vR);
        dust = bed(mc, dust, WeatherRegistry.DUST_LOOP.get(), vD);
        wind = bed(mc, wind, WeatherRegistry.WIND_LOOP.get(), vW);
        // [shelter] muffled beds: same storm, steadier (walls smooth the gusts), rain drums on the roof
        float mB = on ? dB * mgate * (0.8f + 0.2f * g + 0.15f * wo) : 0f;
        float mR = on ? Math.max(s[WeatherClient.THUNDER], s[WeatherClient.SQUALL] * 0.95f) * mgate * 0.85f : 0f;
        float mD = on ? s[WeatherClient.DUST] * mgate * (0.75f + 0.2f * g) : 0f;
        float mW = on ? Math.max(Math.max(s[WeatherClient.WIND] * (0.65f + 0.3f * g), 0.35f * s[WeatherClient.SQUALL]) * 0.75f,
                dB * (0.25f + 0.4f * g * g + 0.25f * wo)) * mgate : 0f;
        mBlizzard = bed(mc, mBlizzard, com.formaworks.frontierhunts.shelter.ShelterSounds.BLIZZARD.get(), mB);
        mRain = bed(mc, mRain, com.formaworks.frontierhunts.shelter.ShelterSounds.RAIN.get(), mR);
        mDust = bed(mc, mDust, com.formaworks.frontierhunts.shelter.ShelterSounds.DUST.get(), mD);
        mWind = bed(mc, mWind, com.formaworks.frontierhunts.shelter.ShelterSounds.WIND.get(), mW);

        if (cooldown > 0) cooldown--;
        float strength = Math.max(Math.max(dB, s[WeatherClient.DUST]), Math.max(s[WeatherClient.WIND] * 0.9f, s[WeatherClient.SQUALL] * 0.6f));
        boolean blizz = dB >= Math.max(s[WeatherClient.DUST], s[WeatherClient.WIND]) && dB > 0.2f;
        if (on && WeatherClient.whiteoutStarted) {
            // [blizzard2] a whiteout gust arrives with a deep howl and a rush from upwind, whatever the cooldown
            gust(mc, cp, WeatherRegistry.GUST_HOWL.get(), gate, 0.78f + R.nextFloat() * 0.14f, 8.0);
            gust(mc, cp, WeatherRegistry.GUST_RUSH.get(), 0.9f * gate, 0.85f + R.nextFloat() * 0.2f, 5.0);
            cooldown = 40 + R.nextInt(40);
        }
        float edge = blizz ? 0.62f : 0.7f; // [blizzard2] blizzards gust more often
        if (on && cooldown == 0 && g > edge && lastGust <= edge && strength > 0.2f && R.nextFloat() < 0.35f + 0.6f * strength) {
            boolean howl = blizz || s[WeatherClient.BLIZZARD] >= Math.max(s[WeatherClient.DUST], s[WeatherClient.WIND]);
            SoundEvent ev = howl ? WeatherRegistry.GUST_HOWL.get() : WeatherRegistry.GUST_RUSH.get();
            float wx = WeatherClient.windUnitX, wz = WeatherClient.windUnitZ;
            double side = (R.nextDouble() - 0.5) * 8.0;
            double x = cp.x - wx * 7.0 - wz * side, z = cp.z - wz * 7.0 + wx * side, y = cp.y + 1.5 + R.nextDouble() * 3.0;
            float base = blizz ? 0.55f + 0.6f * strength : 0.35f + 0.65f * strength;
            float vol = base * gate;
            if (vol > 0.03f) {
                mc.getSoundManager().play(new SimpleSoundInstance(ev, SoundSource.WEATHER, Math.min(1f, vol), 0.86f + R.nextFloat() * 0.28f, R, x, y, z));
            }
            // [shelter] the gust hitting the walls, heard from inside
            float mv = base * mgate * 0.85f;
            SoundEvent mg = com.formaworks.frontierhunts.shelter.ShelterSounds.GUST.get();
            if (mv > 0.03f && mg != null) {
                mc.getSoundManager().play(new SimpleSoundInstance(mg, SoundSource.WEATHER, Math.min(1f, mv), 0.9f + R.nextFloat() * 0.2f, R, x, y, z));
            }
            cooldown = blizz ? 30 + R.nextInt(60) : 50 + R.nextInt(90);
        }
        lastGust = g;
    }

    private static void gust(Minecraft mc, Vec3 cp, SoundEvent ev, float vol, float pitch, double dist) {
        if (ev == null || vol <= 0.03f) return;
        float wx = WeatherClient.windUnitX, wz = WeatherClient.windUnitZ;
        double side = (R.nextDouble() - 0.5) * 6.0;
        double x = cp.x - wx * dist - wz * side, z = cp.z - wz * dist + wx * side, y = cp.y + 1.0 + R.nextDouble() * 2.0;
        mc.getSoundManager().play(new SimpleSoundInstance(ev, SoundSource.WEATHER, Math.min(1f, vol), pitch, R, x, y, z));
    }

    static void stopAll(Minecraft mc) {
        for (Bed b : new Bed[]{blizzard, rain, dust, wind, mBlizzard, mRain, mDust, mWind}) {
            if (b != null) mc.getSoundManager().stop(b);
        }
        blizzard = rain = dust = wind = null;
        mBlizzard = mRain = mDust = mWind = null; // [shelter]
        cooldown = 0;
    }

    private static Bed bed(Minecraft mc, Bed b, SoundEvent ev, float target) {
        target = Mth.clamp(target, 0f, 1f);
        if (b != null && !b.isStopped() && mc.getSoundManager().isActive(b)) {
            b.target = target;
            return b;
        }
        if (target < 0.02f || ev == null) return null;
        Bed nb = new Bed(ev, target);
        mc.getSoundManager().play(nb);
        return nb;
    }

    static final class Bed extends AbstractTickableSoundInstance {
        float target;

        Bed(SoundEvent ev, float target) {
            super(ev, SoundSource.WEATHER, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.target = target;
            this.volume = 0.01f;
            this.x = 0.0;
            this.y = 0.0;
            this.z = 0.0;
        }

        @Override
        public boolean canStartSilent() {
            return true; // [polish] at a 0% Frontier slider the bed starts silent and comes back when raised (no restart every tick)
        }

        @Override
        public void tick() {
            float step = this.target > this.volume ? 0.01f : 0.014f;
            this.volume += Mth.clamp(this.target - this.volume, -step, step);
            if (this.target <= 0.005f && this.volume <= 0.012f) this.stop();
        }
    }
}
