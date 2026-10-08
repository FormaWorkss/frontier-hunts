package com.formaworks.frontierhunts.client;

/**
 * [rifle] Hold sway of a raised rifle, as one continuous curve (pure maths, no Minecraft types, unit-testable).
 *
 * <p>Everything is a smooth function of its own clock, which advances by the real frame time (never the game tick
 * counter, whose client copy jumps whenever the server re-syncs the time): a breathing cycle whose rate wanders
 * slowly between ~12.5 and ~15.5 breaths a minute (integrated phase, so the rate change never kinks the curve), and
 * a drift made of four incommensurate low-frequency sines per axis (0.07-0.41 Hz: no component fast enough to read
 * as jitter, and the figure never repeats). Amplitudes follow their stance targets through critically damped springs (no velocity kinks),
 * so crouching, settling onto the bipod or going prone glides into the new hold instead of snapping.
 *
 * <p>Output is degrees of look offset; the caller applies the change since the last frame to the player's real view
 * direction, so the reticle on screen and the bullet's path follow this exact curve at any frame rate.
 */
final class HoldSway {
   /** Degrees, standing unsupported: breathing (mostly vertical) and drift wander. */
   static final double BREATH_DEGREES = 0.06;
   static final double DRIFT_DEGREES = 0.05; // [1.1.8] was 0.04: a hold you can see, still small at 12x
   /** [1.1.8] slow figure-eight of an unsupported hold (degrees at drift 1) */
   static final double EIGHT_DEGREES = 0.035;
   /** [1.1.8] degrees of push per m/s of crosswind at full exposure (gusts ride on top) */
   static final double WIND_DEGREES = 0.011;
   /** [sticks] Degrees of the pulse in the hold at full strength (only a rested gun is still enough to show it). */
   static final double PULSE_DEGREES = 0.003;
   /** Critically damped envelope stiffness (1/s): a stance change settles in about 0.6 s with no velocity kink. */
   private static final double ENVELOPE_OMEGA = 8.0;
   private static final double TAU = Math.PI * 2.0;

   private double clock;
   private double breathPhase;
   private double drift;
   private double driftRate;
   private double breath;
   private double breathRate;
   private double pitch;
   private double yaw;
   private final double[] phase = new double[14];
   private double windX, windXRate, windZ, windZRate;
   /** [sticks] heartbeat envelope, its target and the beat's phase */
   private double pulse, pulseRate, pulseTarget, beatPhase;

   HoldSway(long seed) {
      long s = seed ^ 0x9E3779B97F4A7C15L;
      for (int i = 0; i < this.phase.length; i++) {
         s = s * 6364136223846793005L + 1442695040888963407L;
         this.phase[i] = (double)(s >>> 11) / (double)(1L << 53) * TAU;
      }
   }

   /**
    * Advances the curve by {@code dt} seconds towards the given stance targets (multiples of the standing hold; 0 =
    * none). {@code dt} 0 re-evaluates without moving (game paused).
    */
   void advance(double dt, double driftTarget, double breathTarget) {
      this.advance(dt, driftTarget, breathTarget, 0.0, 0.0);
   }

   /**
    * [1.1.8] As {@link #advance(double, double, double)} with wind: {@code across} is the crosswind (m/s, + from the
    * left, pushes the muzzle right), {@code along} the head/tail wind, both already scaled by how exposed the shooter
    * is (standing in the open 1, prone or on a bipod much less). The wind arrives through a slow spring and gusts as a
    * smooth sum of low sines, so a windy day leans and buffets the hold without ever jerking it.
    */
   /** [sticks] Strength of the pulse in the hold from the next {@link #advance} on (0 = none, 1 = rested). */
   void pulse(double target) {
      this.pulseTarget = Double.isFinite(target) ? Math.clamp(target, 0.0, 2.0) : 0.0;
   }

   void advance(double dt, double driftTarget, double breathTarget, double across, double along) {
      dt = Double.isFinite(dt) ? Math.clamp(dt, 0.0, 0.1) : 0.0;
      double[] d = spring(this.drift, this.driftRate, Math.max(0.0, driftTarget), dt);
      this.drift = Math.max(0.0, d[0]);
      this.driftRate = d[1];
      double[] b0 = spring(this.breath, this.breathRate, Math.max(0.0, breathTarget), dt);
      this.breath = Math.max(0.0, b0[0]);
      this.breathRate = b0[1];
      this.clock += dt;
      double t = this.clock;
      double perMinute = 14.0 + 1.5 * Math.sin(TAU * 0.021 * t + this.phase[0]);
      this.breathPhase = (this.breathPhase + TAU * perMinute / 60.0 * dt) % (TAU * 1000.0);
      double b = breathShape(this.breathPhase);
      double dp = 0.55 * Math.sin(TAU * 0.093 * t + this.phase[1])
         + 0.32 * Math.sin(TAU * 0.157 * t + this.phase[2])
         + 0.18 * Math.sin(TAU * 0.263 * t + this.phase[3])
         + 0.1 * Math.sin(TAU * 0.409 * t + this.phase[4]);
      double dy = 0.55 * Math.sin(TAU * 0.071 * t + this.phase[5])
         + 0.32 * Math.sin(TAU * 0.131 * t + this.phase[6])
         + 0.18 * Math.sin(TAU * 0.217 * t + this.phase[7])
         + 0.1 * Math.sin(TAU * 0.367 * t + this.phase[8]);
      // [1.1.8] the slow figure-eight every unsupported hold makes (1:2 Lissajous, ~4 s a loop, rate wandering a little)
      double eightRate = 0.24 + 0.03 * Math.sin(TAU * 0.017 * t + this.phase[10]);
      double ex = Math.sin(TAU * eightRate * t + this.phase[11]);
      double ey = Math.sin(2.0 * (TAU * eightRate * t + this.phase[11]) + 0.6);
      // [1.1.8] wind: a slow-following lean plus gusts (0.12-0.55 Hz), stronger the harder it blows
      double[] wx = spring(this.windX, this.windXRate, Double.isFinite(across) ? across : 0.0, dt * 0.35);
      this.windX = wx[0];
      this.windXRate = wx[1];
      double[] wz = spring(this.windZ, this.windZRate, Double.isFinite(along) ? along : 0.0, dt * 0.35);
      this.windZ = wz[0];
      this.windZRate = wz[1];
      double gust = 0.5 + 0.32 * Math.sin(TAU * 0.12 * t + this.phase[12]) + 0.2 * Math.sin(TAU * 0.29 * t + this.phase[13])
         + 0.1 * Math.sin(TAU * 0.55 * t + this.phase[9]);
      double windYaw = this.windX * WIND_DEGREES * (0.45 + gust);
      double windPitch = Math.abs(this.windZ) * WIND_DEGREES * 0.35 * Math.sin(TAU * 0.21 * t + this.phase[4] + gust)
         + Math.abs(this.windX) * WIND_DEGREES * 0.25 * Math.sin(TAU * 0.33 * t + this.phase[7]);
      // [sticks] the pulse: ~66-74 beats a minute, a soft double bump (lub-dub), zero-mean so it never drifts the aim
      double[] pe = spring(this.pulse, this.pulseRate, this.pulseTarget, dt);
      this.pulse = Math.max(0.0, pe[0]);
      this.pulseRate = pe[1];
      double bpm = 70.0 + 4.0 * Math.sin(TAU * 0.013 * t + this.phase[3]);
      this.beatPhase = (this.beatPhase + bpm / 60.0 * dt) % 1000.0;
      double beat = this.pulse <= 1.0E-6 ? 0.0 : heartbeat(this.beatPhase);
      // breathing lifts the muzzle on the inhale and rolls a hair sideways; drift wanders a bit wider than tall
      this.pitch = -this.breath * BREATH_DEGREES * b + this.drift * DRIFT_DEGREES * 0.8 * dp + this.drift * EIGHT_DEGREES * 0.55 * ey + windPitch
         - this.pulse * PULSE_DEGREES * beat;
      this.yaw = this.breath * BREATH_DEGREES * 0.16 * b + this.drift * DRIFT_DEGREES * dy + this.drift * EIGHT_DEGREES * ex + windYaw
         + this.pulse * PULSE_DEGREES * 0.3 * beat;
   }

   /** Exact step of a critically damped spring towards {@code target}: {value, rate}. Continuous value and rate. */
   static double[] spring(double x, double v, double target, double dt) {
      double w = ENVELOPE_OMEGA;
      double e = x - target;
      double c = v + w * e;
      double decay = Math.exp(-w * dt);
      return new double[]{target + (e + c * dt) * decay, (v - w * c * dt) * decay};
   }

   /** [sticks] One heartbeat per unit of {@code beats}: a lub and a softer dub (smooth bumps), zero-mean, peak about 1. */
   static double heartbeat(double beats) {
      double x = beats - Math.floor(beats);
      double a = (x - 0.14) / 0.07;
      double c = (x - 0.36) / 0.08;
      return Math.exp(-a * a) + 0.55 * Math.exp(-c * c) - 0.202;
   }

   /** Smooth asymmetric breath: quicker inhale, longer exhale, a soft pause at the bottom. Range about -1..1. */
   static double breathShape(double phase) {
      return (Math.sin(phase) + 0.22 * Math.sin(2.0 * phase - 0.9)) / 1.12;
   }

   /** Current offset in degrees: negative pitch is up (Minecraft's xRot). */
   double pitch() {
      return this.pitch;
   }

   double yaw() {
      return this.yaw;
   }

   /** True while any sway is still being applied (envelopes not yet settled to rest). */
   boolean live() {
      return this.drift > 1.0E-4 || this.breath > 1.0E-4 || this.pulse > 1.0E-4 || Math.abs(this.windX) > 1.0E-3 || Math.abs(this.windZ) > 1.0E-3;
   }

   void reset() {
      this.drift = 0.0;
      this.driftRate = 0.0;
      this.breath = 0.0;
      this.breathRate = 0.0;
      this.pitch = 0.0;
      this.yaw = 0.0;
      this.windX = 0.0;
      this.windXRate = 0.0;
      this.windZ = 0.0;
      this.windZRate = 0.0;
      this.pulse = 0.0;
      this.pulseRate = 0.0;
      this.pulseTarget = 0.0;
   }
}
