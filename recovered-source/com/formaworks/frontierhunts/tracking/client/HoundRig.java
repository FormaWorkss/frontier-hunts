package com.formaworks.frontierhunts.tracking.client;

import com.formaworks.frontierhunts.wildlife2026.client.SkinnedMesh;
import net.minecraft.util.Mth;

/**
 * [hound3] Procedural animation of the sculpted tracking hound (hound_ultra / hound_bal .fhsk, built in its true
 * standing pose by tools/tracking/hound/hound3.py, so every rest rotation is zero).
 *
 * <p>Legs are driven by foot placement, not by swinging angles: each paw follows a stance / swing trajectory on the
 * ground for the current gait and is reached with analytic two-bone IK (shoulder-elbow-wrist, hip-stifle-hock), the
 * pastern / rear pastern and paw are then oriented in world space. So paws stay planted during stance (no skating, no
 * floating or sinking), forelegs stand straight and the hind legs keep their hock angle in every pose.
 *
 * <p>Gaits (footfall order of a real dog): walk = four-beat lateral sequence LH-LF-RH-RF; trot = diagonal pairs;
 * gallop = rotary LH-RH-RF-LF with spine flexion and suspension. Gait weights, phase and posture weights come from
 * the renderer (smoothed per hound). Postures: stand, sniff (nose to the ground, sweeping), bay (head thrown up),
 * point / look (frozen, head level and forward, tail straight back), sit, lie (sphinx down), tuck (slinking), swim.
 * The long leathers hang with gravity whatever the head does. A Java harness (tools/tracking/hound/RigDump) feeds this
 * exact code to the offline previews.
 */
public final class HoundRig {
   private HoundRig() {
   }

   public static final class Input {
      /** horizontal speed (blocks / tick), gait cycle phase 0..1, gait weights (sum <= 1; the rest is standing) */
      public float speed, phase, walk, trot, gallop;
      public float age, headYaw, headPitch;
      /** posture weights 0..1 */
      public float sniff, sit, lie, bay, point, tuck, wag, air;
      /** side-to-side sweep of the nose while sniffing (-1..1), body turn rate (radians / tick, + = left) */
      public float sweep, turn;
      public boolean water;
   }

   // leg order: FL, FR, BL, BR
   private static final String[][] LEG = {
      {"fl_upper", "fl_lower", "fl_foot", "fl_toe"}, {"fr_upper", "fr_lower", "fr_foot", "fr_toe"},
      {"bl_upper", "bl_lower", "bl_foot", "bl_toe"}, {"br_upper", "br_lower", "br_foot", "br_toe"}
   };
   /** footfall offsets (fraction of a cycle) per gait: walk = lateral sequence, trot = diagonal pairs, gallop = rotary */
   private static final float[] OFF_WALK = {0.25F, 0.75F, 0.0F, 0.5F};
   private static final float[] OFF_TROT = {0.25F, 0.75F, 0.75F, 0.25F};
   private static final float[] OFF_GALLOP = {0.20F, 0.10F, 0.70F, 0.80F};
   /** stance fraction of the cycle */
   private static final float DUTY_WALK = 0.64F, DUTY_TROT = 0.46F, DUTY_GALLOP = 0.30F;
   /** stride length (blocks per cycle) at the gait's typical speed */
   public static final float STRIDE_WALK = 0.56F, STRIDE_TROT = 0.86F, STRIDE_GALLOP = 1.45F;

   private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

   private static final class Scratch {
      float[][] s = new float[3][0];
      int[] legBone = new int[0];
      int[][] leg = new int[4][4];
      SkinnedMesh mesh;
      int pelvis, spine, chest, neck, head, earL, earR, tail1, tail2, tail3;
      final float[] tgt = new float[12];   // per leg: paw joint target x,y,z
      final float[] pitch = new float[8];  // per leg: pastern world pitch offset, paw world pitch
   }

   private static void bind(Scratch c, SkinnedMesh m) {
      if (c.mesh == m) {
         return;
      }
      int nb = m.names.length;
      c.mesh = m;
      c.s = new float[3][nb];
      c.legBone = new int[nb];
      java.util.Arrays.fill(c.legBone, -1);
      for (int l = 0; l < 4; l++) {
         for (int k = 0; k < 4; k++) {
            c.leg[l][k] = m.bone(LEG[l][k]);
         }
         if (c.leg[l][0] >= 0) {
            c.legBone[c.leg[l][0]] = l;
         }
      }
      c.pelvis = m.bone("pelvis");
      c.spine = m.bone("spine");
      c.chest = m.bone("chest");
      c.neck = m.bone("neck");
      c.head = m.bone("head");
      c.earL = m.bone("ear_l");
      c.earR = m.bone("ear_r");
      c.tail1 = m.bone("tail1");
      c.tail2 = m.bone("tail2");
      c.tail3 = m.bone("tail3");
   }

   private static void add(float[][] s, int b, float x, float y, float z) {
      if (b >= 0) {
         s[0][b] += x;
         s[1][b] += y;
         s[2][b] += z;
      }
   }

   /** circular blend of footfall offsets */
   private static float offset(Input in, int l, float stand) {
      float w = in.walk, t = in.trot, g = in.gallop, sum = w + t + g;
      if (sum < 1.0E-4F) {
         return OFF_WALK[l];
      }
      float x = 0, y = 0;
      float[][] o = {OFF_WALK, OFF_TROT, OFF_GALLOP};
      float[] k = {w, t, g};
      for (int i = 0; i < 3; i++) {
         float a = o[i][l] * Mth.TWO_PI;
         x += k[i] * Mth.cos(a);
         y += k[i] * Mth.sin(a);
      }
      float a = (float)Math.atan2(y, x) / Mth.TWO_PI;
      return a < 0 ? a + 1.0F : a;
   }

   private static float smooth(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   /**
    * Writes 12 floats per bone (3x4 row-major model-space matrix) into {@code out}.
    */
   /** [hound4] longest planted paw travel (m): what the legs can reach under the body */
   public static final float STEP_CAP = 0.40F;

   /**
    * [hound4] body travel per stride cycle for this gait blend and ground speed (blocks / tick): shorter, quicker steps
    * when slow, longer at speed, limited by the legs' reach. HoundRenderer advances the stride phase by speed / this,
    * and {@link #pose} plants the paws over stride x duty, so a planted paw stays put on the ground.
    */
   public static float strideFor(float walk, float trot, float gallop, float speed) {
      float gw = Math.max(walk + trot + gallop, 1.0E-4F);
      float duty = (walk * DUTY_WALK + trot * DUTY_TROT + gallop * DUTY_GALLOP) / gw;
      float stride = gw < 1.0E-3F ? STRIDE_WALK : (walk * STRIDE_WALK + trot * STRIDE_TROT + gallop * STRIDE_GALLOP) / gw;
      if (gw < 1.0E-3F) {
         duty = DUTY_WALK;
      }
      float L = Math.min(stride * duty * (0.65F + 0.35F * Mth.clamp(speed / 0.2F, 0.0F, 1.6F)), STEP_CAP);
      return L / duty;
   }

   public static void pose(SkinnedMesh m, Input in, float[] out) {
      Scratch c = SCRATCH.get();
      bind(c, m);
      int nb = m.names.length;
      float[][] s = c.s;
      for (int b = 0; b < nb; b++) {
         boolean r = m.rest.length >= nb * 3;
         s[0][b] = r ? m.rest[b * 3] : 0.0F;
         s[1][b] = r ? m.rest[b * 3 + 1] : 0.0F;
         s[2][b] = r ? m.rest[b * 3 + 2] : 0.0F;
      }
      float age = in.age;
      float gaitW = Mth.clamp(in.walk + in.trot + in.gallop, 0.0F, 1.0F);
      float moving = gaitW * smooth(in.speed / 0.03F);
      float sit = in.sit, lie = in.lie, still = Math.max(sit, lie);
      moving *= 1.0F - still;
      float gal = in.gallop * moving, trot = in.trot * moving, walk = in.walk * moving;
      float duty = (in.walk * DUTY_WALK + in.trot * DUTY_TROT + in.gallop * DUTY_GALLOP) / Math.max(gaitW, 1.0E-4F);
      float stride = (in.walk * STRIDE_WALK + in.trot * STRIDE_TROT + in.gallop * STRIDE_GALLOP) / Math.max(gaitW, 1.0E-4F);
      float ph = in.phase;
      float cyc = ph * Mth.TWO_PI;
      float dy = 0.0F;

      // ---------------------------------------------------------------- body: bob, spine flexion, breathing, turn bend
      dy -= 0.006F * walk * (0.5F - 0.5F * Mth.cos(2.0F * cyc + 0.6F));
      dy += 0.012F * trot * (Mth.cos(2.0F * cyc) * 0.5F + 0.5F) - 0.006F * trot;
      // gallop: gathered (spine arched, rump down) as the hinds reach under, extended in flight
      float flex = Mth.sin(cyc - 0.3F);
      dy += gal * (0.035F * Mth.sin(cyc + 1.1F) - 0.01F);
      add(s, c.pelvis, gal * 0.10F * flex, 0, 0);
      add(s, c.spine, -gal * 0.16F * flex, 0, 0);
      add(s, c.chest, gal * 0.10F * flex, 0, 0);
      add(s, c.chest, 0.008F * Mth.sin(age * 0.11F) * (1.0F - moving), 0, 0);   // breathing
      float bend = Mth.clamp(in.turn * 6.0F, -0.35F, 0.35F) * (1.0F - still);
      add(s, c.spine, 0, bend * 0.35F, 0);
      add(s, c.chest, 0, bend * 0.35F, 0);
      add(s, c.pelvis, 0, 0, walk * 0.03F * Mth.sin(cyc));                       // hips roll with the walk

      // ---------------------------------------------------------------- postures on the trunk
      // sniff: forequarters lowered a touch, the neck and head go down to the ground
      float sn = in.sniff * (1.0F - still);
      add(s, c.pelvis, -0.06F * sn, 0, 0);
      dy -= 0.02F * sn;
      // bay: chest up, rocking back on the hindquarters
      float bay = in.bay * (1.0F - lie);
      add(s, c.pelvis, 0.06F * bay * (1.0F - sit), 0, 0);
      dy -= 0.012F * bay * (1.0F - sit);
      // point / look: weight forward, rock steady
      float pt = in.point * (1.0F - still);
      add(s, c.pelvis, -0.03F * pt, 0, 0);
      // tuck: crouched slink
      float tk = in.tuck * (1.0F - still);
      add(s, c.pelvis, 0.05F * tk, 0, 0);
      dy -= 0.06F * tk;
      // sit: the trunk tips up on the hips, the rump goes to the ground
      add(s, c.pelvis, 0.66F * sit, 0, 0);
      add(s, c.spine, -0.06F * sit, 0, 0);
      add(s, c.chest, -0.06F * sit, 0, 0);
      dy -= 0.255F * sit;
      // lie: sphinx down, chest and belly on the ground, head up
      dy -= 0.295F * lie;
      add(s, c.pelvis, 0.02F * lie, 0, 0);
      // swimming: low in the water, head up
      float sw = in.water ? 1.0F : 0.0F;
      dy -= 0.30F * sw;
      add(s, c.pelvis, 0.12F * sw, 0, 0);

      // ---------------------------------------------------------------- neck + head
      float hy = Mth.clamp(in.headYaw, -70.0F, 70.0F) * Mth.DEG_TO_RAD;
      float hp = Mth.clamp(in.headPitch, -45.0F, 45.0F) * Mth.DEG_TO_RAD;
      float look = (1.0F - sn) * (1.0F - bay * 0.7F);
      add(s, c.neck, -0.35F * hp * look, 0.45F * hy * look, 0);
      add(s, c.head, -0.65F * hp * look, 0.55F * hy * look, 0);
      // trot / gallop carriage: neck forward and lower, head nods with the stride
      add(s, c.neck, -0.12F * trot - 0.38F * gal, 0, 0);
      add(s, c.head, 0.03F * walk * Mth.sin(2.0F * cyc + 1.0F) + 0.10F * gal + 0.06F * gal * Mth.sin(cyc + 2.4F), 0, 0);
      // sniff: nose to the ground and sweeping
      float sweep = in.sweep;
      add(s, c.neck, -1.02F * sn, sn * 0.22F * sweep, 0);
      add(s, c.head, -0.42F * sn, sn * 0.20F * sweep, sn * 0.06F * sweep);
      // bay: head thrown up, muzzle to the sky
      add(s, c.neck, 0.42F * bay, 0, 0);
      add(s, c.head, 0.62F * bay + 0.05F * bay * Mth.sin(age * 0.9F), 0, 0);
      // point: head level and forward
      add(s, c.neck, -0.22F * pt, 0, 0);
      add(s, c.head, 0.12F * pt, 0, 0);
      // tuck: head low
      add(s, c.neck, -0.35F * tk, 0, 0);
      add(s, c.head, -0.1F * tk, 0, 0);
      // sit / lie: head kept level over the tipped trunk
      add(s, c.neck, -0.42F * sit + 0.08F * lie, 0, 0);
      add(s, c.head, -0.18F * sit + 0.05F * lie, 0, 0);
      add(s, c.neck, 0.35F * sw, 0, 0);
      add(s, c.head, 0.25F * sw, 0, 0);

      // ---------------------------------------------------------------- tail: sabre carriage, wag, stream, tuck
      float wag = in.wag * Mth.sin(age * 1.45F) * 0.5F + 0.06F * Mth.sin(age * 0.09F);
      float carry = 0.18F * sn + 0.12F * bay - 0.25F * gal - 1.3F * tk + 0.25F * pt;
      // (+x lowers the tail; the rest pose is already carried up in a sabre curve)
      add(s, c.tail1, -carry * 0.6F + 0.06F * trot * Mth.sin(2.0F * cyc) + 0.55F * sit + 1.05F * lie, wag * (1.0F - pt), 0);
      add(s, c.tail2, -carry * 0.3F + 0.30F * pt + 0.45F * sit + 0.45F * lie, wag * 0.7F * (1.0F - pt), 0);
      add(s, c.tail3, -0.2F * carry + 0.25F * pt + 0.35F * sit + 0.35F * lie + 0.2F * gal, wag * 0.6F * (1.0F - pt), 0);

      // ---------------------------------------------------------------- paw targets for every leg
      float lift = 0.035F * walk + 0.06F * trot + 0.09F * gal;
      for (int l = 0; l < 4; l++) {
         boolean front = l < 2;
         int toe = c.leg[l][3];
         if (toe < 0) {
            continue;
         }
         float rx = m.joint[toe * 3], ry = m.joint[toe * 3 + 1], rz = m.joint[toe * 3 + 2];
         float tx = rx, ty = ry, tz = rz, pastern = 0.0F, paw = 0.0F;
         if (moving > 0.001F) {
            float p = ph + offset(in, l, 1.0F - moving);
            p -= Mth.floor(p);
            // [hound4] the planted paw travels exactly the body's advance during stance (no skating): the renderer
            // advances the phase by speed / strideFor(..), the same effective stride (was a per-leg cap -> 20-30% slide)
            float L = strideFor(in.walk, in.trot, in.gallop, in.speed) * duty;
            float dz, up = 0.0F;
            if (p < duty) {
               float q = p / duty;
               dz = L * (0.5F - q);                                     // planted: slides back under the body
               // heel lift at the end of stance
               float roll = smooth((q - 0.72F) / 0.28F);
               paw = -0.5F * roll;
               pastern = (front ? 0.12F : -0.10F) * roll;
            } else {
               float q = (p - duty) / (1.0F - duty);
               float e = q * q * (3.0F - 2.0F * q);
               dz = L * (e - 0.5F);
               up = lift * (float)Math.pow(Mth.sin(q * Mth.PI), 0.85F) * (front ? 1.0F : 0.85F);
               // swing: the carpus folds the front paw back up under the forearm; the hock flexes the hind paw forward
               float fold = Mth.sin(q * Mth.PI);
               pastern = front ? -1.35F * fold * (0.65F + 0.35F * gal) : 0.5F * fold;
               paw = front ? 0.8F * pastern : 0.5F * pastern;
            }
            // gallop: forelegs reach further forward, hinds land further under
            dz += gal * (front ? -0.06F : -0.04F);
            tx = rx;
            tz = rz - dz * moving;
            ty = ry + up * moving;
            pastern *= moving;
            paw *= moving;
         }
         // ---- postures
         if (front) {
            // bay: front feet braced a little forward
            tz -= 0.03F * bay * (1.0F - sit);
            // point: one foreleg lifted and folded (the left)
            if (l == 0 && pt > 0.0F) {
               ty += 0.075F * pt;
               tz -= 0.02F * pt;
               pastern -= 1.5F * pt;
               paw -= 1.2F * pt;
            }
            // sit: front paws stay planted a little forward
            tz += 0.07F * sit;
            // lie: forearms flat on the ground, paws out in front
            ty = Mth.lerp(lie, ty, 0.03F);
            tz = Mth.lerp(lie, tz, rz - 0.20F);
            pastern = Mth.lerp(lie, pastern, 1.4F);
            paw = Mth.lerp(lie, paw, 0.0F);
         } else {
            // sit / lie: hocks on the ground, rear pasterns flat and pointing forward, paws in front of the hocks
            float fold = Math.max(sit, lie);
            ty = Mth.lerp(fold, ty, 0.03F);
            tz = Mth.lerp(fold, tz, rz - (lie > sit ? 0.17F : 0.10F));
            pastern = Mth.lerp(fold, pastern, 1.45F);
            paw = Mth.lerp(fold, paw, 0.0F);
            // tuck: hinds a little under
            tz -= 0.04F * tk;
         }
         // swimming: paddle
         if (sw > 0.0F) {
            float q = age * 0.5F + (l == 0 || l == 3 ? 0.0F : Mth.PI);
            ty = Mth.lerp(sw, ty, ry + 0.12F + 0.05F * Mth.sin(q));
            tz = Mth.lerp(sw, tz, rz + 0.07F * Mth.cos(q));
            pastern = Mth.lerp(sw, pastern, front ? -0.6F - 0.4F * Mth.sin(q) : 0.3F + 0.3F * Mth.sin(q));
         }
         // airborne (jumping): legs tuck
         if (in.air > 0.0F) {
            ty += 0.06F * in.air;
            tz += (front ? 0.04F : -0.04F) * in.air;
            pastern += (front ? -0.8F : 0.4F) * in.air;
         }
         c.tgt[l * 3] = tx;
         c.tgt[l * 3 + 1] = ty;
         c.tgt[l * 3 + 2] = tz;
         c.pitch[l * 2] = pastern;
         c.pitch[l * 2 + 1] = paw;
      }

      // ---------------------------------------------------------------- forward kinematics (+ leg IK on the way)
      for (int b = 0; b < nb; b++) {
         int leg = c.legBone[b];
         if (leg >= 0 && m.parent[b] >= 0) {
            solveLeg(m, c, out, leg, m.parent[b]);
         }
         if ((b == c.earL || b == c.earR) && m.parent[b] >= 0) {
            hangEar(m, s, out, b, m.parent[b], in, cyc, moving, bay, b == c.earL ? -1.0F : 1.0F);
         }
         compose(m, s, out, b, dy);
      }
   }

   private static void compose(SkinnedMesh m, float[][] s, float[] out, int b, float dy) {
      float cx = Mth.cos(s[0][b]), sx = Mth.sin(s[0][b]);
      float cy = Mth.cos(s[1][b]), sy = Mth.sin(s[1][b]);
      float cz = Mth.cos(s[2][b]), sz = Mth.sin(s[2][b]);
      float r00 = cz * cy, r01 = cz * sy * sx - sz * cx, r02 = cz * sy * cx + sz * sx;
      float r10 = sz * cy, r11 = sz * sy * sx + cz * cx, r12 = sz * sy * cx - cz * sx;
      float r20 = -sy, r21 = cy * sx, r22 = cy * cx;
      float jx = m.joint[b * 3], jy = m.joint[b * 3 + 1], jz = m.joint[b * 3 + 2];
      float t0 = jx - (r00 * jx + r01 * jy + r02 * jz);
      float t1 = jy - (r10 * jx + r11 * jy + r12 * jz);
      float t2 = jz - (r20 * jx + r21 * jy + r22 * jz);
      int o = b * 12;
      int p = m.parent[b];
      if (p < 0) {
         out[o] = r00; out[o + 1] = r01; out[o + 2] = r02; out[o + 3] = t0;
         out[o + 4] = r10; out[o + 5] = r11; out[o + 6] = r12; out[o + 7] = t1 + dy;
         out[o + 8] = r20; out[o + 9] = r21; out[o + 10] = r22; out[o + 11] = t2;
      } else {
         int q = p * 12;
         for (int r = 0; r < 3; r++) {
            float a0 = out[q + r * 4], a1 = out[q + r * 4 + 1], a2 = out[q + r * 4 + 2], a3 = out[q + r * 4 + 3];
            out[o + r * 4] = a0 * r00 + a1 * r10 + a2 * r20;
            out[o + r * 4 + 1] = a0 * r01 + a1 * r11 + a2 * r21;
            out[o + r * 4 + 2] = a0 * r02 + a1 * r12 + a2 * r22;
            out[o + r * 4 + 3] = a0 * t0 + a1 * t1 + a2 * t2 + a3;
         }
      }
   }

   /** model-space point -> parent-local (rest) space: inverse of the parent's rigid matrix */
   private static void toLocal(float[] out, int p, float x, float y, float z, float[] dst) {
      int q = p * 12;
      float px = x - out[q + 3], py = y - out[q + 7], pz = z - out[q + 11];
      dst[0] = out[q] * px + out[q + 4] * py + out[q + 8] * pz;
      dst[1] = out[q + 1] * px + out[q + 5] * py + out[q + 9] * pz;
      dst[2] = out[q + 2] * px + out[q + 6] * py + out[q + 10] * pz;
   }

   private static void dirLocal(float[] out, int p, float x, float y, float z, float[] dst) {
      int q = p * 12;
      dst[0] = out[q] * x + out[q + 4] * y + out[q + 8] * z;
      dst[1] = out[q + 1] * x + out[q + 5] * y + out[q + 9] * z;
      dst[2] = out[q + 2] * x + out[q + 6] * y + out[q + 10] * z;
   }

   private static final ThreadLocal<float[]> V3 = ThreadLocal.withInitial(() -> new float[3]);

   /**
    * Two-bone IK in the leg's sagittal plane (rotations about x in the parent's frame), then the pastern and paw are
    * turned to their wanted world pitch. Targets are in model space, so the trunk's bob / pitch is compensated.
    */
   private static void solveLeg(SkinnedMesh m, Scratch c, float[] out, int l, int parent) {
      float[][] s = c.s;
      int up = c.leg[l][0], lo = c.leg[l][1], ft = c.leg[l][2], tp = c.leg[l][3];
      if (lo < 0 || ft < 0 || tp < 0) {
         return;
      }
      float[] J = m.joint;
      // rest chain (y, z)
      float ay = J[up * 3 + 1], az = J[up * 3 + 2];
      float by = J[lo * 3 + 1], bz = J[lo * 3 + 2];
      float cy = J[ft * 3 + 1], cz = J[ft * 3 + 2];
      float dyy = J[tp * 3 + 1], dzz = J[tp * 3 + 2];
      // wanted pastern direction (world): its rest direction pitched by the wanted angle
      float pastern = c.pitch[l * 2], paw = c.pitch[l * 2 + 1];
      float py0 = dyy - cy, pz0 = dzz - cz;
      float cp = Mth.cos(pastern), sp = Mth.sin(pastern);
      float pwy = py0 * cp - pz0 * sp, pwz = py0 * sp + pz0 * cp;
      // wrist / hock target = paw joint target minus the pastern
      float tx = m.joint[tp * 3], ty = c.tgt[l * 3 + 1] - pwy, tz = c.tgt[l * 3 + 2] - pwz;
      float[] v = V3.get();
      toLocal(out, parent, tx, ty, tz, v);
      float ly = v[1], lz = v[2];
      float L1 = (float)Math.hypot(by - ay, bz - az), L2 = (float)Math.hypot(cy - by, cz - bz);
      float dY = ly - ay, dZ = lz - az;
      float D = (float)Math.hypot(dY, dZ);
      float Dc = Mth.clamp(D, Math.abs(L1 - L2) + 1.0E-3F, L1 + L2 - 1.0E-3F);
      float base = (float)Math.atan2(dZ, dY);
      float cosA = Mth.clamp((L1 * L1 + Dc * Dc - L2 * L2) / (2.0F * L1 * Dc), -1.0F, 1.0F);
      float A = (float)Math.acos(cosA);
      // keep the joint bending the way it bends at rest (elbow back, stifle forward)
      float cross = (by - ay) * (cz - az) - (bz - az) * (cy - ay);
      float sign = cross >= 0.0F ? -1.0F : 1.0F;
      float upperAng = base + sign * A;
      float restUpper = (float)Math.atan2(bz - az, by - ay);
      float th1 = wrap(upperAng - restUpper);
      // where the knee / elbow ends up, then aim the lower bone at the target
      float ky = ay + L1 * Mth.cos(upperAng), kz = az + L1 * Mth.sin(upperAng);
      float lowerAng = (float)Math.atan2(az + Dc * Mth.sin(base) - kz, ay + Dc * Mth.cos(base) - ky);
      float restLower = (float)Math.atan2(cz - bz, cy - by);
      float th2 = wrap(lowerAng - restLower - th1);
      // pastern: wanted world direction into the parent's frame
      dirLocal(out, parent, 0.0F, pwy, pwz, v);
      float want = (float)Math.atan2(v[2], v[1]);
      float restP = (float)Math.atan2(pz0, py0);
      float th3 = wrap(want - restP - th1 - th2);
      // paw: wanted world pitch of its forward axis
      dirLocal(out, parent, 0.0F, Mth.sin(paw), -Mth.cos(paw), v);
      float wantPaw = (float)Math.atan2(v[2], v[1]);
      float th4 = wrap(wantPaw - (float)Math.atan2(-1.0, 0.0) - th1 - th2 - th3);
      s[0][up] = th1;
      s[0][lo] = th2;
      s[0][ft] = th3;
      s[0][tp] = th4;
      s[1][up] = 0.0F;
      s[2][up] = 0.0F;
   }

   private static float wrap(float a) {
      return Mth.wrapDegrees(a * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
   }

   /** the leathers hang plumb: undo the head's world pitch, swing with the gait, flap in the gallop, flare when baying */
   private static void hangEar(SkinnedMesh m, float[][] s, float[] out, int b, int head, Input in, float cyc, float moving, float bay, float side) {
      float[] v = V3.get();
      dirLocal(out, head, 0.0F, -1.0F, 0.0F, v);
      float want = (float)Math.atan2(v[2], v[1]);
      float rest = (float)Math.atan2(0.0, -1.0);
      float hang = wrap(want - rest);
      float swing = 0.12F * moving * Mth.sin(2.0F * cyc + (side > 0 ? 0.0F : 0.8F)) + 0.25F * in.gallop * moving * Mth.sin(cyc * 1.0F + 1.6F);
      s[0][b] = Mth.clamp(hang * 0.92F, -1.6F, 1.6F) + swing + 0.03F * Mth.sin(in.age * 0.13F + side);
      s[2][b] = side * (0.03F + 0.10F * bay + 0.10F * in.gallop * moving + 0.04F * moving * Math.abs(Mth.sin(cyc)));
   }
}
