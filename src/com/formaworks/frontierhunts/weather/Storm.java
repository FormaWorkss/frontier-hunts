package com.formaworks.frontierhunts.weather;

import net.minecraft.network.FriendlyByteBuf;

/**
 * One regional weather event: a soft-edged disc that drifts with the wind and has a build-up, peak and easing
 * envelope in game time. Everything here is deterministic from the fields, so the server and every client evaluate
 * the same severity at the same place and tick; only the fields are ever sent over the network.
 */
public final class Storm {
    public final int id;
    public final WeatherKind kind;
    public final double x0, z0;
    /** Drift in blocks per tick. */
    public final float driftX, driftZ;
    public final float radius;
    public final long start;
    public final int rampIn, hold, rampOut;
    public final float peak;
    public final boolean forced;
    /** Fog ceiling (world Y) for fog kinds, NaN for everything else. */
    public final float top;
    /** Tick the storm began easing out early (vanilla weather cleared, /frontierweather clear), or Long.MAX_VALUE. */
    long easeStart = Long.MAX_VALUE;
    int easeTicks = 1;

    public Storm(int id, WeatherKind kind, double x0, double z0, float driftX, float driftZ, float radius, long start,
                 int rampIn, int hold, int rampOut, float peak, boolean forced, float top) {
        this.id = id;
        this.kind = kind;
        this.x0 = x0;
        this.z0 = z0;
        this.driftX = driftX;
        this.driftZ = driftZ;
        this.radius = Math.max(16f, radius);
        this.start = start;
        this.rampIn = Math.max(1, rampIn);
        this.hold = Math.max(0, hold);
        this.rampOut = Math.max(1, rampOut);
        this.peak = Math.max(0f, Math.min(1f, peak));
        this.forced = forced;
        this.top = top;
    }

    // ------------------------------------------------------------------ time
    public long naturalEnd() { return start + rampIn + hold + rampOut; }

    public long end() { return easeStart == Long.MAX_VALUE ? naturalEnd() : Math.min(naturalEnd(), easeStart + easeTicks); }

    public boolean easing(long t) { return t >= easeStart || t >= start + rampIn + hold; }

    public boolean expired(long t) { return t >= end(); }

    /** Begin an early ease-out now (no-op when already easing sooner). */
    public boolean easeOut(long now, int ticks) {
        long e = now + Math.max(1, ticks);
        if (e >= end()) return false;
        easeStart = now;
        easeTicks = Math.max(1, ticks);
        return true;
    }

    /** 0..1 build-up / peak / easing envelope. */
    public float envelope(long t) {
        if (t < start) return 0f;
        long a = t - start;
        float e;
        if (a < rampIn) e = smooth((float) a / rampIn);
        else if (a < rampIn + hold) e = 1f;
        else e = 1f - smooth((float) (a - rampIn - hold) / rampOut);
        if (t > easeStart) e *= 1f - smooth((float) (t - easeStart) / easeTicks);
        return Math.max(0f, e);
    }

    /**
     * Slow internal banding: heavier and lighter bands inside the storm (snow squalls in a blizzard, dust walls,
     * rain bands). 1 = full, lower = a lull. Deterministic in game time.
     */
    public float pulse(long t) {
        float amp = switch (kind) {
            case BLIZZARD -> 0.07f; // [blizzard2] shallower lulls: the peak holds (gust variation lives on the client)
            case DUST_STORM -> 0.22f;
            case THUNDERSTORM -> 0.16f;
            case SQUALL -> 0.24f;
            case WIND_STORM -> 0.3f;
            default -> 0.08f;
        };
        double s = t / 20.0;
        double ph = (id * 0.618034) % 1.0 * Math.PI * 2.0;
        double n = 0.5 + 0.5 * Math.sin(s / 53.0 * Math.PI * 2.0 + ph);
        n *= 0.55 + 0.45 * (0.5 + 0.5 * Math.sin(s / 21.0 * Math.PI * 2.0 + ph * 1.7));
        return (float) (1.0 - amp * n);
    }

    // ------------------------------------------------------------------ space
    public double centerX(long t) { return x0 + driftX * (double) (t - start); }

    public double centerZ(long t) { return z0 + driftZ * (double) (t - start); }

    /** 0..1 soft disc falloff (full inside 62% of the radius). */
    public float spatial(long t, double x, double z) {
        double dx = x - centerX(t), dz = z - centerZ(t);
        double d2 = dx * dx + dz * dz;
        double r = radius;
        if (d2 >= r * r) return 0f;
        double d = Math.sqrt(d2);
        double inner = r * 0.62;
        if (d <= inner) return 1f;
        return 1f - smooth((float) ((d - inner) / (r - inner)));
    }

    public boolean covers(long t, double x, double z, double margin) {
        double dx = x - centerX(t), dz = z - centerZ(t);
        double r = radius + margin;
        return dx * dx + dz * dz < r * r;
    }

    /** Raw storm severity 0..1 at a place, before climate applicability (see {@link Climate}). */
    public float raw(long t, double x, double z) {
        float sp = spatial(t, x, z);
        if (sp <= 0f) return 0f;
        float env = envelope(t);
        if (env <= 0f) return 0f;
        return peak * env * pulse(t) * sp;
    }

    public String phase(long t) {
        if (t < start) return "gathering";
        if (expired(t)) return "over";
        if (t > easeStart) return "easing";
        long a = t - start;
        if (a < rampIn) return "building";
        if (a < rampIn + hold) return "peak";
        return "easing";
    }

    static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    // ------------------------------------------------------------------ network
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(id);
        buf.writeByte(kind.ordinal());
        buf.writeDouble(x0);
        buf.writeDouble(z0);
        buf.writeFloat(driftX);
        buf.writeFloat(driftZ);
        buf.writeFloat(radius);
        buf.writeVarLong(start);
        buf.writeVarInt(rampIn);
        buf.writeVarInt(hold);
        buf.writeVarInt(rampOut);
        buf.writeFloat(peak);
        buf.writeBoolean(forced);
        buf.writeFloat(top);
        buf.writeBoolean(easeStart != Long.MAX_VALUE);
        if (easeStart != Long.MAX_VALUE) {
            buf.writeVarLong(easeStart);
            buf.writeVarInt(easeTicks);
        }
    }

    public static Storm read(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        WeatherKind kind = WeatherKind.byOrdinal(buf.readByte());
        double x0 = buf.readDouble(), z0 = buf.readDouble();
        float dx = clampF(buf.readFloat(), -2f, 2f), dz = clampF(buf.readFloat(), -2f, 2f);
        float radius = clampF(buf.readFloat(), 16f, 4096f);
        long start = buf.readVarLong();
        int ri = Math.min(buf.readVarInt(), 1 << 20), h = Math.min(buf.readVarInt(), 1 << 22), ro = Math.min(buf.readVarInt(), 1 << 20);
        float peak = clampF(buf.readFloat(), 0f, 1f);
        boolean forced = buf.readBoolean();
        float top = buf.readFloat();
        Storm s = new Storm(id, kind, x0, z0, dx, dz, radius, start, ri, h, ro, peak, forced, top);
        if (buf.readBoolean()) {
            s.easeStart = buf.readVarLong();
            s.easeTicks = Math.max(1, Math.min(buf.readVarInt(), 1 << 20));
        }
        return s;
    }

    private static float clampF(float v, float lo, float hi) {
        return Float.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : lo;
    }
}
