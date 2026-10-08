package com.formaworks.frontierhunts.wingshot.client;

import com.formaworks.frontierhunts.wingshot.Flight;

/**
 * [wingshot] Wing and body animation state of one bird, advanced every frame from the synced flight phase and the
 * bird's measured motion. Pure math (no Minecraft types) so the offline harness can pose it.
 *
 * <p>Flapping follows real wing kinematics: a downstroke over ~55% of the cycle with the wing fully extended and
 * slightly pronated, an upstroke with the wrist flexed and the hand swept back, at the species' real cadence (mallard
 * ~5.4 Hz cruising, faster and deeper on the jump take-off; grouse a blur when flushing). Set wings, a shallow V and
 * cupped hands for circling and landing (feet dropped, tail fanned), banking from the measured turn rate, a limp,
 * tumbling body when shot, and the grouse's drumming display timed to the beats of the recording.
 */
public final class BirdAnim {
   private BirdAnim() {
   }

   /** Beat times (seconds) of the two drumming recordings (wingshot.grouse_drum_a / _b), measured from the audio. */
   static final float[] DRUM_A = {0.42F, 0.79F, 1.25F, 1.79F, 2.91F, 3.79F, 4.51F, 5.11F, 5.61F, 6.05F, 6.44F, 6.79F, 7.09F, 7.37F, 7.61F, 7.83F, 8.03F,
      8.12F, 8.21F, 8.37F, 8.52F, 8.64F, 8.77F, 8.88F, 8.99F, 9.09F, 9.17F, 9.25F, 9.31F, 9.38F, 9.43F, 9.48F, 9.54F, 9.61F, 9.67F, 9.76F, 9.92F, 10.02F,
      10.12F, 10.23F, 10.36F};
   static final float[] DRUM_B = {0.19F, 0.70F, 1.14F, 1.53F, 1.88F, 2.18F, 2.46F, 2.70F, 2.92F, 3.12F, 3.29F, 3.46F, 3.60F, 3.73F, 3.86F, 3.98F, 4.08F,
      4.17F, 4.25F, 4.33F, 4.41F, 4.46F, 4.51F, 4.57F, 4.62F, 4.70F, 4.75F, 4.84F, 5.01F, 5.11F, 5.21F, 5.33F, 5.45F};

   public static final class State {
      // ------------------------------------------------ pose outputs
      /** wing elevation (radians, + = tip up), already blended toward the folded pose by {@link #spread} */
      public float elev;
      /** 0 folded against the body .. 1 fully spread */
      public float spread;
      /** wrist/elbow flexion of the upstroke (0..1) */
      public float flex;
      /** sweep (radians, + = forward) and pronation (radians, + = leading edge down) */
      public float sweep, twist;
      /** cupped hands: tips curled down (0..1) */
      public float cup;
      /** primaries fanned open (0..1; closed on the upstroke) */
      public float fan;
      /** 0 tucked .. 1 dropped and forward (landing) */
      public float feet;
      /** tail spread (0..1) and pitch (radians, + = down) */
      public float tail, tailPitch;
      /** 0 resting neck .. 1 stretched forward in flight */
      public float neck;
      /** body attitude: bank (+ = right wing down) and pitch (+ = nose up), radians */
      public float bank, pitch;
      /** 0 alive .. 1 limp (shot) */
      public float limp;
      /** drumming posture weight and the current beat pulse (0..1) */
      public float drum, drumPulse;
      /** dead body tumble angle (radians) and whether it is down, on water */
      public float tumble;
      public boolean down, water;
      /** shot this frame or recently: 1 at the hit, decays */
      public float flinch;
      public boolean grouse;
      /** is any procedural wing drawn this frame */
      public boolean wingsOut;
      // ------------------------------------------------ inputs (for debugging / harness)
      public byte phase;
      public float speed, climb, yawRate;
      // ------------------------------------------------ internal
      float time = Float.NaN;
      float phaseStart;
      byte lastPhase = -1;
      float cycle;
      float freq, amp, setW, cupW, feetW, tailW, neckW, spreadW, drumW;
      float bankS, pitchS;
      float deadStart = Float.NaN, downStart = Float.NaN, tumbleRate, restRoll;
      float burstSeed;
      int seed;

      public State(int seed, boolean grouse) {
         this.seed = seed;
         this.grouse = grouse;
         float h = hash(seed);
         this.tumbleRate = 0.18F + 0.14F * h;
         this.restRoll = (hash(seed * 31 + 7) < 0.5F ? -1.0F : 1.0F) * (1.25F + 0.3F * hash(seed + 3));
         this.burstSeed = hash(seed + 11) * 50.0F;
      }

      public float time() {
         return this.time;
      }

      public float sinceDeath() {
         return Float.isNaN(this.deadStart) ? 0.0F : this.time - this.deadStart;
      }

      public float restRoll() {
         return this.restRoll;
      }

      public float sinceDown() {
         return Float.isNaN(this.downStart) ? 0.0F : this.time - this.downStart;
      }
   }

   static float hash(int x) {
      x ^= x >>> 16;
      x *= 0x7feb352d;
      x ^= x >>> 15;
      x *= 0x846ca68b;
      x ^= x >>> 16;
      return (x & 0xFFFFFF) / (float)0x1000000;
   }

   private static float approach(float v, float target, float rate, float dt) {
      float k = 1.0F - (float)Math.exp(-rate * dt);
      return v + (target - v) * k;
   }

   private static float clamp(float v, float lo, float hi) {
      return v < lo ? lo : (v > hi ? hi : v);
   }

   /**
    * Advance to time {@code now} (ticks). {@code speed}: horizontal speed (b/t), {@code climb}: vertical speed (b/t),
    * {@code yawRate}: body turn rate (rad/t, + = right), {@code dead}/{@code down}/{@code water}: shot, lying, on water.
    */
   public static void step(State s, byte phase, float now, float speed, float climb, float yawRate, boolean dead, boolean down, boolean water,
      boolean onGround) {
      if (Float.isNaN(s.time) || now - s.time > 40.0F || now < s.time - 1.0F) {
         // first sight: start in the right pose instead of animating into it
         s.time = now;
         s.phaseStart = now;
         s.lastPhase = phase;
         boolean flying = flying(phase);
         s.spreadW = flying ? 1.0F : 0.0F;
         s.neckW = flying ? 1.0F : 0.0F;
         s.setW = phase == Flight.GLIDE || phase == Flight.SET ? 1.0F : 0.0F;
         s.cupW = phase == Flight.LAND ? 1.0F : 0.0F;
         s.feetW = phase == Flight.LAND ? 1.0F : 0.0F;
         if (dead) {
            s.deadStart = now - 40.0F;
            if (down) {
               s.downStart = now - 40.0F;
            }
         }
      }
      float dt = clamp(now - s.time, 0.0F, 4.0F);
      s.time = now;
      if (phase != s.lastPhase) {
         s.lastPhase = phase;
         s.phaseStart = now;
      }
      s.phase = phase;
      s.speed = speed;
      s.climb = climb;
      s.yawRate = yawRate;
      float inPhase = now - s.phaseStart;
      boolean grouse = s.grouse;
      // ---------------------------------------------------------------- targets per phase
      float tFreq = 0.0F, tAmp = 0.0F, tSet = 0.0F, tCup = 0.0F, tFeet = 0.0F, tTail = 0.15F, tSpread = 0.0F, tNeck = 0.0F, tDrum = 0.0F;
      float pitchBias = 0.0F;
      switch (phase) {
         case Flight.TAKEOFF -> {
            // jump take-off: deep, fast strokes, body tipped up, feet trailing
            tFreq = grouse ? 15.0F : 6.6F;
            tAmp = 1.25F;
            tSpread = 1.0F;
            tFeet = inPhase < 8.0F ? 0.7F : 0.2F;
            tTail = 0.7F;
            tNeck = 0.7F;
            pitchBias = Math.max(0.0F, 0.75F - inPhase * 0.04F);
         }
         case Flight.FLAP -> {
            tFreq = grouse ? 12.0F : 5.4F + clamp(climb * 6.0F, -0.4F, 0.9F);
            tAmp = 1.0F + clamp(climb * 1.5F, -0.15F, 0.25F);
            tSpread = 1.0F;
            tTail = 0.15F;
            tNeck = 1.0F;
         }
         case Flight.FLUSH -> {
            tFreq = 15.0F;
            tAmp = 1.15F;
            tSpread = 1.0F;
            tFeet = 0.25F;
            tTail = 0.8F;
            tNeck = 0.8F;
            pitchBias = Math.max(0.0F, 0.45F - inPhase * 0.03F);
         }
         case Flight.GLIDE -> {
            tSet = 1.0F;
            tSpread = 1.0F;
            tTail = 0.35F;
            tNeck = 1.0F;
            tFeet = grouse && climb < -0.1F ? 0.3F : 0.0F;
         }
         case Flight.SET -> {
            // circling the spread: wings set in a shallow V, a few wingbeats now and then
            tSet = 1.0F;
            tCup = 0.35F;
            tSpread = 1.0F;
            tTail = 0.4F;
            tNeck = 0.9F;
            float w = (now + s.burstSeed) % 70.0F;
            if (w < 12.0F) {
               tFreq = 5.0F;
               tAmp = 0.75F;
               tSet = 0.0F;
            }
         }
         case Flight.LAND -> {
            // cupped wings, feet down and forward, tail fanned and down, back-pedalling strokes near the water
            tSet = 0.6F;
            tCup = 1.0F;
            tSpread = 1.0F;
            tFeet = 1.0F;
            tTail = 1.0F;
            tNeck = 0.55F;
            tFreq = grouse ? 9.0F : 4.6F;
            tAmp = 0.5F;
            pitchBias = 0.42F;
         }
         case Flight.DRUM_A, Flight.DRUM_B -> {
            tDrum = 1.0F;
            tTail = 1.0F;
         }
         default -> {
         }
      }
      // ---------------------------------------------------------------- shot: limp, tumbling
      if (dead) {
         if (Float.isNaN(s.deadStart)) {
            s.deadStart = now;
            s.flinch = 1.0F;
         }
         if (down && Float.isNaN(s.downStart)) {
            s.downStart = now;
         }
         float since = now - s.deadStart;
         tFreq = 0.0F;
         tAmp = 0.0F;
         tSet = 0.0F;
         tCup = 0.0F;
         tDrum = 0.0F;
         // the wings snap shut at the hit, then the air flowing past opens them limp
         tSpread = since < 2.5F ? 0.15F : (down ? 0.45F : 0.62F);
         tFeet = 0.75F;
         tTail = 0.25F;
         tNeck = 0.0F;
      }
      s.down = down;
      s.water = water;
      s.limp = approach(s.limp, dead ? 1.0F : 0.0F, 0.6F, dt);
      // ---------------------------------------------------------------- smoothing
      s.freq = approach(s.freq, tFreq, 0.35F, dt);
      s.amp = approach(s.amp, tAmp, tAmp > s.amp ? 0.5F : 0.22F, dt);
      s.setW = approach(s.setW, tSet, 0.18F, dt);
      s.cupW = approach(s.cupW, tCup, 0.14F, dt);
      s.feetW = approach(s.feetW, tFeet, 0.16F, dt);
      s.tailW = approach(s.tailW, tTail, 0.12F, dt);
      s.neckW = approach(s.neckW, tNeck, 0.15F, dt);
      // spread opens fast (take-off), folds a little slower after landing
      s.spreadW = approach(s.spreadW, tSpread, tSpread > s.spreadW ? (dead ? 0.9F : 0.75F) : (dead ? 0.9F : 0.3F), dt);
      s.drumW = approach(s.drumW, tDrum, 0.2F, dt);
      s.flinch = Math.max(0.0F, s.flinch - dt * 0.12F);
      // ---------------------------------------------------------------- the stroke
      s.cycle += s.freq / 20.0F * dt;
      if (s.cycle > 1000.0F) {
         s.cycle -= 1000.0F;
      }
      float ph = s.cycle - (float)Math.floor(s.cycle);
      // downstroke over 55% of the cycle (top -> bottom), upstroke over 45%
      float stroke, up;
      if (ph < 0.55F) {
         float u = ph / 0.55F;
         stroke = (float)Math.cos(Math.PI * u);
         up = 0.0F;
      } else {
         float u = (ph - 0.55F) / 0.45F;
         stroke = -(float)Math.cos(Math.PI * u);
         up = (float)Math.sin(Math.PI * u);
      }
      float amp = s.amp;
      float flapElev = 0.12F + amp * (stroke > 0.0F ? 0.95F : 0.8F) * stroke;
      float noise = (float)(Math.sin(now * 0.9 + s.seed) * 0.5 + Math.sin(now * 2.3 + s.seed * 3) * 0.3);
      // set wings: flat glide with a little turbulence, a shallow V when circling, cupped and raised to land
      float setElev = 0.06F + 0.03F * noise + 0.18F * s.cupW;
      float active = flapElev * (1.0F - s.setW) + setElev * s.setW;
      if (s.cupW > 0.5F && amp > 0.1F) {
         // landing back-pedal on top of the cupped set
         active = setElev + 0.3F + amp * 0.55F * stroke;
      }
      // ---------------------------------------------------------------- drumming: cupped wings thrust forward and up on each beat
      float pulse = 0.0F;
      if (s.drumW > 0.01F) {
         float[] beats = phase == Flight.DRUM_B ? DRUM_B : DRUM_A;
         float t = inPhase / 20.0F;
         for (float b : beats) {
            float d = (t - b) / 0.045F;
            if (d > -3.0F && d < 4.0F) {
               // fast forward thrust, slower recovery
               pulse = Math.max(pulse, d < 0.0F ? (float)Math.exp(-d * d) : (float)Math.exp(-d * d * 0.35F));
            }
         }
      }
      s.drumPulse = pulse;
      // ---------------------------------------------------------------- dead: limp wings flopping in the airflow
      if (s.limp > 0.01F) {
         float since = Float.isNaN(s.deadStart) ? 0.0F : now - s.deadStart;
         float flop = down ? 0.0F : (float)(Math.sin(now * 1.7 + s.seed) * 0.35 + Math.sin(now * 3.1 + s.seed * 2) * 0.2);
         float limpElev = (down ? (water ? -0.25F : -0.45F) : 0.25F + flop) + (since < 3.0F ? -0.6F : 0.0F);
         active = active * (1.0F - s.limp) + limpElev * s.limp;
         if (!down) {
            s.tumble = since * s.tumbleRate;
         }
      }
      // ---------------------------------------------------------------- outputs
      s.spread = clamp(s.spreadW + s.drumW * (0.42F + 0.35F * pulse), 0.0F, 1.0F);
      float fold = -1.45F;
      float drumElev = -0.55F + 0.85F * pulse;
      float wingElev = active * (1.0F - s.drumW) + drumElev * s.drumW;
      s.elev = fold + (wingElev - fold) * smooth(clamp(s.spread * 1.15F, 0.0F, 1.0F));
      s.flex = up * amp * 0.85F * (1.0F - s.setW) * (1.0F - s.limp) + s.limp * 0.35F;
      s.sweep = (stroke < 0.0F ? 0.12F : -0.05F) * amp * (1.0F - s.setW) + 0.3F * s.cupW + s.drumW * (-0.15F + 0.95F * pulse);
      s.twist = (up > 0.0F ? -0.3F * up : 0.22F * (1.0F - Math.abs(stroke))) * amp + 0.1F * s.setW + s.limp * 0.4F * (float)Math.sin(s.seed);
      s.cup = s.cupW + s.drumW * 0.6F;
      s.fan = clamp(1.0F - up * 0.6F * amp, 0.2F, 1.0F) * (1.0F - 0.3F * s.limp);
      s.feet = s.feetW;
      s.tail = clamp(s.tailW + Math.abs(s.bankS) * 0.6F, 0.0F, 1.0F);
      s.tailPitch = 0.45F * s.cupW + 0.55F * s.drumW - 0.1F * s.setW + 0.25F * s.feetW * (1.0F - s.cupW);
      s.neck = s.neckW;
      s.drum = s.drumW;
      s.wingsOut = s.spread > 0.08F;
      // ---------------------------------------------------------------- body attitude: coordinated-turn bank, climb angle
      boolean air = flying(phase) && !dead;
      float bankT = 0.0F, pitchT = 0.0F;
      if (air) {
         bankT = clamp((float)Math.atan2(speed * yawRate, Flight.G), -1.2F, 1.2F);
         float climbAngle = (float)Math.atan2(climb, Math.max(0.08F, speed));
         pitchT = clamp(climbAngle * 0.65F, -0.5F, 0.6F) + pitchBias - 0.1F; // level the sculpted standing posture in cruise
      } else if (s.drumW > 0.01F) {
         pitchT = 0.5F * s.drumW;
      }
      s.bankS = approach(s.bankS, bankT, 0.25F, dt);
      s.pitchS = approach(s.pitchS, pitchT, 0.2F, dt);
      s.bank = s.bankS;
      s.pitch = s.pitchS;
   }

   static float smooth(float t) {
      return t * t * (3.0F - 2.0F * t);
   }

   public static boolean flying(byte phase) {
      return phase >= Flight.TAKEOFF && phase <= Flight.FLUSH;
   }
}
