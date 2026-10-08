package com.formaworks.frontierhunts.landscape.ride;

/**
 * [polish] ATV reverse gear. A real quad backs up in a low, governed gear: it pulls away briskly, holds ~10-15 km/h on
 * any normal surface and does not run away. The drive force per tick therefore first covers the rolling resistance the
 * surface takes off that tick (grass, gravel, snow, mud...) and then closes the gap to the target speed at a limited
 * acceleration. Above the target (rolling backwards down a hill) the engine brakes.
 * Units: blocks/tick (x72 = km/h).
 */
final class RevGear {
   /** Most drive impulse reverse gear can deliver per tick (forward first gear gives 0.03 + its hill assist). */
   static final double MAX_PUSH = 0.032;

   private RevGear() {
   }

   /**
    * Backward drive impulse for this tick (the caller subtracts it from the forward speed; negative = engine braking).
    *
    * @param v       forward speed now (negative = already rolling backwards)
    * @param brake   the reverse input 0..1 (S key)
    * @param rolling the surface's rolling resistance per tick
    * @param drag    the aerodynamic drag factor (per v^2)
    * @param hill    the share of gravity pulling the ATV forwards this tick (reversing up a slope), 0 otherwise
    */
   static double push(double v, float brake, double rolling, double drag, double hill) {
      if (!(brake > 0.0F) || !Double.isFinite(v)) {
         return 0.0;
      }
      double want = Atv.V_REV * Math.min(1.0F, brake);
      double back = -v;
      double res = (Math.abs(v) > 0.005 ? rolling : 0.0) + drag * v * v + Math.max(0.0, hill);
      // below the target: close the gap in ~3-4 ticks once near it, never faster than REV_ACCEL; above it: back off
      double err = want - back;
      double rate = Atv.REV_ACCEL * Math.min(1.0F, brake);
      double drive = err >= 0.0 ? res + Math.min(rate, err * 0.3) : res + err;
      // the gear's torque is finite (steep enough slopes, ~38 deg on grass, still stop it); rolling backwards down a hill
      // faster than the gear turns, the engine holds it back (a negative push = engine braking)
      return Math.max(-rate, Math.min(MAX_PUSH, drive));
   }
}
