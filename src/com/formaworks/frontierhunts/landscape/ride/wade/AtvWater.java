package com.formaworks.frontierhunts.landscape.ride.wade;

import com.formaworks.frontierhunts.landscape.ride.Atv;
import com.formaworks.frontierhunts.landscape.ride.RideContent;
import com.formaworks.frontierhunts.landscape.ride.rig.AtvFuel;
import com.formaworks.frontierhunts.landscape.ride.rig.RigContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

/**
 * [atv2] How deep water drives. One set of rules, evaluated wherever the ATV's movement is simulated (the driver's
 * client, like a boat, or the server for an empty ATV) and, for the engine state, on every side:
 * <ul>
 *   <li>up to ~1 block of water: drives easily: a little drag (top speed ~51 instead of ~63 km/h), spray;</li>
 *   <li>~2 blocks: struggles: heavy drag (body and rider's legs in the water), weak thrust, ~10-20 km/h, the engine
 *   bogs and surges;</li>
 *   <li>3+ blocks (water above the air intake, {@link #FLOOD}): the engine floods and stalls, no propulsion; the ATV
 *   is never carried by the current (it is not pushed by fluids) and sinks to rest on the bottom. Back in shallower
 *   water ({@link #UNFLOOD}) for 1.5 s it restarts.</li>
 * </ul>
 * The rider stays seated until their head goes under ({@code Atv.canBeRiddenUnderFluidType} returns false, so the
 * NeoForge breathing hook dismounts only a fully submerged rider).
 */
public final class AtvWater {
    /** Fluid surface height above the ATV's bottom, in blocks. */
    public static final float SHALLOW = 0.12f, FLOOD = 2.3f, UNFLOOD = 2.0f;
    private static final int RESTART_TICKS = 30;

    /** Per-ATV wading state ({@code Atv.wade}). */
    public static final class State {
        /** Fluid depth over the ATV's bottom this tick / last tick (blocks). */
        public float depth, depthO;
        int recover;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    }

    private AtvWater() {}

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Depth of the fluid column the ATV stands in, measured from its bottom (0 when dry). No allocation. */
    public static float depth(Level level, double x, double y, double z, BlockPos.MutableBlockPos p) {
        int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(y);
        double top = Double.NaN;
        for (int i = 0; i < 4; i++) {
            p.set(bx, by + i, bz);
            FluidState fs = level.getFluidState(p);
            if (fs.isEmpty()) break;
            top = by + i + fs.getHeight(level, p);
        }
        if (top != top) return 0f;
        return (float) Math.max(0.0, top - y);
    }

    /** Called from Atv.tick on every side before movement: depth + engine flooding (with hysteresis). */
    public static void tick(Atv atv) {
        State w = atv.wade;
        Level level = atv.level();
        w.depthO = w.depth;
        w.depth = depth(level, atv.getX(), atv.getY(), atv.getZ(), w.pos);
        boolean was = atv.flooded;
        if (!was) {
            if (w.depth >= FLOOD) {
                atv.flooded = true;
                w.recover = 0;
            }
        } else if (w.depth < UNFLOOD) {
            if (++w.recover >= RESTART_TICKS) {
                atv.flooded = false;
                w.recover = 0;
            }
        } else {
            w.recover = 0;
        }
        if (level.isClientSide || was == atv.flooded || !atv.isVehicle()) return;
        Player driver = atv.getControllingPassenger() instanceof Player p ? p : null;
        if (atv.flooded) {
            // water in the intake: the engine chokes, gurgles and dies
            level.playSound(null, atv.getX(), atv.getY(), atv.getZ(), WadeContent.SND_FLOOD.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            level.playSound(null, atv.getX(), atv.getY(), atv.getZ(), RigContent.SND_STALL.get(), SoundSource.NEUTRAL, 0.7F, 0.9F);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, atv.getX(), atv.getY() + 1.0, atv.getZ(), 24, 0.5, 0.4, 0.5, 0.05);
            }
            if (driver != null) {
                driver.displayClientMessage(Component.translatable("message.frontierhunts.atv_flooded").withStyle(ChatFormatting.AQUA), true);
            }
        } else if (AtvFuel.running(atv)) {
            // drained: it catches again (cough + start)
            level.playSound(null, atv.getX(), atv.getY(), atv.getZ(), RigContent.SND_CRANK.get(), SoundSource.NEUTRAL, 0.8F, 1.0F);
            level.playSound(null, atv.getX(), atv.getY(), atv.getZ(), RideContent.SND_ATV_START.get(), SoundSource.NEUTRAL, 1.0F, 0.95F);
        }
    }

    /**
     * Seated, the rider's eyes are ~1.8 blocks above the ATV's bottom, just under the surface of 2-block-deep water. Like
     * a real rider they stand up on the footpegs as the water gets that deep (up to +0.22), so a 2-block ford keeps the
     * head above water; past {@link #FLOOD} they go under and float off.
     */
    public static double standUp(Atv atv) {
        return 0.22 * smooth(1.35f, 1.75f, atv.wade.depth);
    }

    // ================================================================== physics (driver client / server for an empty ATV)
    /** Share of the drive force the tyres still deliver: they paddle and slip in deep water. */
    public static float thrust(double d) {
        if (d < SHALLOW) return 1f;
        float a = smooth(SHALLOW, 1.0f, (float) d), b = smooth(0.95f, 1.9f, (float) d);
        return 1f - 0.08f * a - 0.5f * b;
    }

    /**
     * Water resistance for one tick at speed {@code v} (blocks/tick) and depth {@code d}: the magnitude to take off the
     * speed. First block: tyres, wheel wells and footwells push water (gentle). Second block: the body, racks and the
     * rider's legs plough a bow wave (heavy, grows with v^2 so a fast entry slams the speed down).
     */
    public static double drag(double v, double d) {
        if (d < SHALLOW) return 0.0;
        double a = smooth(SHALLOW, 1.0f, (float) d), b = smooth(0.95f, 1.9f, (float) d);
        double av = Math.abs(v);
        return (0.0015 * a + 0.008 * b) * av + (0.009 * a + 0.085 * b) * av * av;
    }

    /** Applies {@link #drag} to a signed velocity component; never reverses it. */
    public static double slow(double v, double d, double scale) {
        double k = drag(v, d) * scale;
        return v - Math.signum(v) * Math.min(Math.abs(v), k);
    }

    /** Engine rpm target while wading: in ~2 blocks of water it labours, surging and bogging under load. */
    public static float rpm(Atv atv, float target, float throttle) {
        float d = atv.wade.depth;
        if (d < 0.95f || atv.flooded) return target;
        float b = smooth(0.95f, 1.9f, d);
        if (throttle <= 0f) return target * (1f - 0.15f * b);
        float t = atv.tickCount;
        float surge = 0.10f * Mth.sin(t * 0.55f) + 0.05f * Mth.sin(t * 1.9f + 1.3f);
        return Mth.clamp(target * (1f - 0.3f * b) + b * surge, 0.2f, 1f);
    }
}
