package com.formaworks.frontierhunts.landscape.ride.grime.client;

import com.formaworks.frontierhunts.landscape.ride.Atv;
import com.formaworks.frontierhunts.landscape.ride.grime.AtvGrime;
import com.formaworks.frontierhunts.landscape.ride.wade.WadeContent;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * [atv2] Driving through water, Forza style (client only, cosmetic, from what this client sees):
 * <ul>
 *   <li>wheel fans: every tyre in the water throws a fan of drops and velocity-aligned spray plumes up and out, the rear
 *   tyres a rooster tail behind; all scale with speed and fade when the wheels are too deep to throw water;</li>
 *   <li>bow wave: the leading end pushes a white foam crest that rolls off to both sides, with a curtain of drops and
 *   big plumes thrown forward and out at speed;</li>
 *   <li>wake: foam left on the surface behind, spreading and fading over ~2 s;</li>
 *   <li>entry splash: ploughing in (or dropping into deeper water) at speed throws a crown of drops, plumes and foam
 *   with a layered splash sound;</li>
 *   <li>sound: looping churn and spray layers ({@link AtvWaterSound}), sloshes while creeping or labouring.</li>
 * </ul>
 * Budgets: per-kind live caps in {@link AtvGrimeParticles} scaled by Effects quality, Minecraft's Particles option and
 * distance (the caller passes {@code q}).
 */
final class AtvWaterFx {
    private static final RandomSource RND = RandomSource.create();
    private static final Map<Atv, AtvWaterSound[]> LOOPS = new WeakHashMap<>();

    private AtvWaterFx() {}

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float r() {
        return RND.nextFloat();
    }

    static void clear() {
        LOOPS.clear();
    }

    /** Called per ATV client tick (within 56 blocks or driven by this player). {@code spray}: wheel-spray particles on. */
    static void tick(ClientLevel level, Atv atv, AtvGrime g, AtvGrime.Sample s, float sp, float q, boolean spray, double dist2) {
        float d = s.depth;
        sounds(level, atv, g, s, sp, dist2);
        if (!spray || d < 0.04f) return;
        double fx = s.fx, fz = s.fz, rx = s.rx, rz = s.rz;
        float dir = atv.shownSpeed >= 0 ? 1f : -1f;
        double vfx = fx * atv.shownSpeed, vfz = fz * atv.shownSpeed;
        double cx = atv.getX(), cz = atv.getZ();
        double sea = atv.getY() + d + 0.02; // the water surface
        float wheels = smooth(0.04f, 0.3f, d);
        float deep = 1f - smooth(1.4f, 2.2f, d);
        float spd = smooth(0.05f, 0.9f, sp);
        float in = wheels * deep;

        // ------------------------------------------------------------ wheel fans + rooster tails
        if (in > 0.01f && sp > 0.04f) {
            g.emitFan += in * spd * 7.5f * q;
            int guard = 0;
            while (g.emitFan >= 1f && guard++ < 40) {
                g.emitFan -= 1f;
                int i = r() < 0.6f ? 2 + RND.nextInt(2) : RND.nextInt(2);
                boolean lead = dir > 0 ? i < 2 : i >= 2;
                float side = (i & 1) == 0 ? -1f : 1f;
                double px = s.wx[i] + rx * side * 0.22 - fx * dir * 0.15, pz = s.wz[i] + rz * side * 0.22 - fz * dir * 0.15;
                double out = side * (0.05 + 0.16 * r()) * (0.5 + spd);
                double up = (0.12 + 0.26 * r()) * (0.4 + 0.9 * spd);
                double back = -dir * (0.05 + 0.22 * r()) * spd * (lead ? 0.6 : 1.35);
                double vx = rx * out + fx * back + vfx * 0.55, vz = rz * out + fz * back + vfz * 0.55;
                if (AtvGrimeParticles.room(AtvGrimeParticles.DROP, q)) {
                    AtvGrimeParticles.add(new AtvGrimeParticles.Drop(level, px, sea, pz, vx, up, vz, 0.03f + r() * 0.045f, 30), AtvGrimeParticles.DROP);
                }
                if (r() < 0.34f && AtvGrimeParticles.room(AtvGrimeParticles.SPRAY, q)) {
                    float size = (0.22f + 0.32f * r()) * (0.6f + 0.8f * spd);
                    AtvGrimeParticles.add(new AtvGrimeParticles.Spray(level, px, sea, pz, vx * 0.75, up * 0.8, vz * 0.75, size, 1.3f,
                            0.38f + 0.22f * spd, 9 + RND.nextInt(7), 0.45f), AtvGrimeParticles.SPRAY);
                }
                if (r() < 0.22f && AtvGrimeParticles.room(AtvGrimeParticles.MIST, q)) {
                    AtvGrimeParticles.add(new AtvGrimeParticles.Mist(level, px, sea + 0.1, pz, vx * 0.5, up * 0.35, vz * 0.5, 0.9f, 0.93f, 0.97f,
                            0.28f, 0.22f + r() * 0.18f, 2.0f, 12 + RND.nextInt(10), -0.004f), AtvGrimeParticles.MIST);
                }
            }
            if (g.emitFan > 1f) g.emitFan = 0f;
        }

        // ------------------------------------------------------------ bow wave at the leading end
        float bow = smooth(0.12f, 0.55f, d) * (1f - smooth(1.6f, 2.3f, d)) * smooth(0.08f, 0.8f, sp);
        if (bow > 0.01f) {
            g.emitBow += bow * 6.5f * q;
            int guard = 0;
            while (g.emitBow >= 1f && guard++ < 40) {
                g.emitBow -= 1f;
                float u = r() * 2f - 1f;
                float su = u < 0 ? -1f : 1f;
                double px = cx + fx * dir * 1.05 + rx * u * 0.75, pz = cz + fz * dir * 1.05 + rz * u * 0.75;
                float k = r();
                if (k < 0.5f) {
                    if (!AtvGrimeParticles.room(AtvGrimeParticles.MIST, q)) continue;
                    double vx = fx * dir * sp * 0.55 + rx * su * (0.05 + 0.12 * r()), vz = fz * dir * sp * 0.55 + rz * su * (0.05 + 0.12 * r());
                    AtvGrimeParticles.add(new AtvGrimeParticles.Mist(level, px, sea, pz, vx, 0.015, vz, 0.94f, 0.96f, 0.98f,
                            0.42f + 0.15f * bow, 0.32f + r() * 0.28f, 2.3f, 18 + RND.nextInt(14), 0f), AtvGrimeParticles.MIST);
                } else if (k < 0.85f) {
                    if (!AtvGrimeParticles.room(AtvGrimeParticles.DROP, q)) continue;
                    double vx = fx * dir * sp * (0.55 + 0.35 * r()) + rx * su * (0.1 + 0.2 * r()) * (0.5 + spd);
                    double vz = fz * dir * sp * (0.55 + 0.35 * r()) + rz * su * (0.1 + 0.2 * r()) * (0.5 + spd);
                    AtvGrimeParticles.add(new AtvGrimeParticles.Drop(level, px, sea, pz, vx, (0.12 + 0.24 * r()) * (0.4 + spd), vz,
                            0.035f + r() * 0.04f, 30), AtvGrimeParticles.DROP);
                } else {
                    if (!AtvGrimeParticles.room(AtvGrimeParticles.SPRAY, q)) continue;
                    double vx = fx * dir * sp * 0.5 + rx * su * (0.12 + 0.15 * r()), vz = fz * dir * sp * 0.5 + rz * su * (0.12 + 0.15 * r());
                    float size = (0.45f + 0.45f * r()) * (0.5f + 0.7f * bow);
                    AtvGrimeParticles.add(new AtvGrimeParticles.Spray(level, px, sea, pz, vx, (0.14 + 0.16 * r()) * (0.5 + spd), vz, size, 1.5f,
                            0.42f + 0.2f * bow, 10 + RND.nextInt(8), 0.5f), AtvGrimeParticles.SPRAY);
                }
            }
            if (g.emitBow > 1f) g.emitBow = 0f;
        }

        // ------------------------------------------------------------ wake foam
        float wake = in * smooth(0.05f, 0.5f, sp);
        if (wake > 0.01f) {
            g.emitFoam += wake * 2.2f * q;
            while (g.emitFoam >= 1f) {
                g.emitFoam -= 1f;
                if (!AtvGrimeParticles.room(AtvGrimeParticles.MIST, q)) {
                    g.emitFoam = 0f;
                    break;
                }
                double px = cx - fx * dir * 1.1 + rx * (r() - 0.5) * 1.3, pz = cz - fz * dir * 1.1 + rz * (r() - 0.5) * 1.3;
                AtvGrimeParticles.add(new AtvGrimeParticles.Mist(level, px, sea - 0.01, pz, vfx * 0.15, 0, vfz * 0.15, 0.95f, 0.97f, 0.99f,
                        0.3f, 0.45f + r() * 0.35f, 1.7f, 30 + RND.nextInt(22), 0f), AtvGrimeParticles.MIST);
            }
        }

        // ------------------------------------------------------------ entry splash / slam into deeper water
        boolean entry = g.lastDepth < 0.1f && d >= 0.15f && sp > 0.2f;
        boolean slam = !entry && d - g.lastDepth > 0.3f && sp > 0.35f;
        if (entry || slam) {
            float e = smooth(0.2f, 1.0f, sp) * (slam ? 0.7f : 1f);
            double ox = cx + fx * dir * 0.6, oz = cz + fz * dir * 0.6;
            int n = (int) (40 * e * Math.min(q, 1.5f));
            for (int k = 0; k < n && AtvGrimeParticles.room(AtvGrimeParticles.DROP, q * 1.5f); k++) {
                double a = r() * Math.PI * 2;
                double sx = Math.cos(a), sz = Math.sin(a);
                double out = 0.08 + 0.2 * r();
                AtvGrimeParticles.add(new AtvGrimeParticles.Drop(level, ox + sx * 0.7, sea, oz + sz * 0.7, sx * out + vfx * 0.5,
                        (0.2 + 0.35 * r()) * (0.5 + e), sz * out + vfz * 0.5, 0.04f + r() * 0.05f, 40), AtvGrimeParticles.DROP);
            }
            int m = (int) (8 * e * Math.min(q, 1.5f)) + 1;
            for (int k = 0; k < m && AtvGrimeParticles.room(AtvGrimeParticles.SPRAY, q * 1.5f); k++) {
                double a = r() * Math.PI * 2;
                double sx = Math.cos(a), sz = Math.sin(a);
                AtvGrimeParticles.add(new AtvGrimeParticles.Spray(level, ox + sx * 0.6, sea, oz + sz * 0.6, sx * 0.12 + vfx * 0.4, 0.22 + 0.2 * r(),
                        sz * 0.12 + vfz * 0.4, 0.7f + 0.6f * r() * e, 1.6f, 0.55f, 12 + RND.nextInt(8), 0.5f), AtvGrimeParticles.SPRAY);
            }
            for (int k = 0; k < 8 && AtvGrimeParticles.room(AtvGrimeParticles.MIST, q * 1.5f); k++) {
                double a = r() * Math.PI * 2;
                AtvGrimeParticles.add(new AtvGrimeParticles.Mist(level, ox + Math.cos(a) * 0.8, sea, oz + Math.sin(a) * 0.8, Math.cos(a) * 0.06,
                        0.02, Math.sin(a) * 0.06, 0.95f, 0.97f, 0.99f, 0.5f, 0.5f + r() * 0.4f, 2.4f, 24 + RND.nextInt(16), 0f), AtvGrimeParticles.MIST);
            }
            for (int k = 0; k < 10; k++) {
                level.addParticle(ParticleTypes.SPLASH, cx + (r() - 0.5) * 2.0, sea, cz + (r() - 0.5) * 2.0, 0, 0.1, 0);
            }
        }
    }

    // ================================================================== sound
    private static void sounds(ClientLevel level, Atv atv, AtvGrime g, AtvGrime.Sample s, float sp, double dist2) {
        float d = s.depth;
        boolean entry = g.lastDepth < 0.1f && d >= 0.15f && sp > 0.15f;
        boolean slam = !entry && d - g.lastDepth > 0.3f && sp > 0.3f;
        if (entry || slam) {
            float e = smooth(0.15f, 1.0f, sp) * (slam ? 0.75f : 1f);
            level.playLocalSound(atv.getX(), atv.getY() + 0.3, atv.getZ(), WadeContent.SND_SPLASH.get(), SoundSource.NEUTRAL,
                    0.45f + 0.8f * e, 0.82f + 0.2f * r() + (1f - e) * 0.15f, false);
        }
        // sloshing while creeping, or labouring in deep water
        if (g.sloshCooldown > 0) g.sloshCooldown--;
        if (d > 0.3f && g.sloshCooldown <= 0 && (sp < 0.3f && (atv.isVehicle() || sp > 0.02f) || d > 0.95f)) {
            float chance = d > 0.95f ? 0.09f : 0.035f + 0.08f * Math.min(sp / 0.3f, 1f);
            if (r() < chance) {
                g.sloshCooldown = 6 + RND.nextInt(8);
                level.playLocalSound(atv.getX() + (r() - 0.5), atv.getY() + 0.3, atv.getZ() + (r() - 0.5), WadeContent.SND_SLOSH.get(),
                        SoundSource.NEUTRAL, 0.3f + 0.3f * smooth(0.3f, 1.5f, d), 0.85f + 0.3f * r(), false);
            }
        }
        // looping layers
        if (dist2 > 40.0 * 40.0) return;
        AtvWaterSound[] loops = LOOPS.get(atv);
        if (loops == null || loops[0].isStopped() || loops[1].isStopped()) {
            if (AtvWaterSound.target(atv, AtvWaterSound.Layer.CHURN) < 0.02f && AtvWaterSound.target(atv, AtvWaterSound.Layer.SPRAY) < 0.02f) return;
            Minecraft mc = Minecraft.getInstance();
            AtvWaterSound[] fresh = new AtvWaterSound[2];
            for (AtvWaterSound.Layer l : AtvWaterSound.Layer.values()) {
                AtvWaterSound old = loops == null ? null : loops[l.ordinal()];
                if (old != null && !old.isStopped()) {
                    fresh[l.ordinal()] = old;
                } else {
                    fresh[l.ordinal()] = new AtvWaterSound(atv, l);
                    mc.getSoundManager().play(fresh[l.ordinal()]);
                }
            }
            LOOPS.put(atv, fresh);
        }
    }
}
