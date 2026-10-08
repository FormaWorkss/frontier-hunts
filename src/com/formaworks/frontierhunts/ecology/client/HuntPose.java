package com.formaworks.frontierhunts.ecology.client;

import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import com.formaworks.frontierhunts.wildlife2026.client.SkinnedMesh;
import com.formaworks.frontierhunts.wildlife2026.client.WildlifeRig;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * [ecology] Hunting poses for the wildlife predators, layered on the normal rig: a low stalking crouch (legs folded
 * at every joint so the feet stay planted, head low and level, tail low with a twitching tip), the stretched chase,
 * the pounce (forelegs reaching, hind legs driving back), feeding on a kill (braced, head down, tugging) and the howl
 * (muzzle up, half sitting). The body height and pitch follow from the folded legs by forward kinematics on the
 * mesh's own joints, so the paws neither sink nor float. Classic box models get the same poses in box form.
 */
public final class HuntPose {
   public static final int STALK = 0, CHASE = 1, POUNCE = 2, TEAR = 3, HOWL = 4;
   private static final int N = 5;
   private static final Map<WildlifeMob, float[]> SMOOTH = new WeakHashMap<>();
   private static final Map<SkinnedMesh, Bones> BONES = java.util.Collections.synchronizedMap(new WeakHashMap<>());

   private HuntPose() {
   }

   /** cached bone indices of one mesh (-1 = missing) */
   private static final class Bones {
      final int pelvis, spine, chest, neck, head, tail1, tail2;
      final int[][] legs = new int[4][4];
      final boolean ok;

      Bones(SkinnedMesh m) {
         this.pelvis = m.bone("pelvis");
         this.spine = m.bone("spine");
         this.chest = m.bone("chest");
         this.neck = m.bone("neck");
         this.head = m.bone("head");
         this.tail1 = m.bone("tail1");
         this.tail2 = m.bone("tail2");
         String[] l = {"fl", "fr", "bl", "br"};
         String[] s = {"_upper", "_lower", "_foot", "_toe"};
         boolean all = true;
         for (int i = 0; i < 4; i++) {
            for (int k = 0; k < 4; k++) {
               this.legs[i][k] = m.bone(l[i] + s[k]);
               all &= this.legs[i][k] >= 0;
            }
         }
         this.ok = all && !m.bird && m.joint.length >= m.names.length * 3;
      }
   }

   /** how deep each species crouches when stalking (cats flatten, canids only drop the head and shoulders) */
   private static float depth(WildlifeSpecies s) {
      return switch (s) {
         case COUGAR, PANTHER, LION -> 1.0F;
         case CHEETAH -> 0.6F;
         case WOLF, COYOTE -> 0.45F;
         case GRIZZLY, BLACK_BEAR, POLAR_BEAR -> 0.2F;
         default -> 0.0F;
      };
   }

   /**
    * Smoothed hunt weights for this animal at time {@code age} (ticks + partial): rises toward the synced hunt state,
    * fades out of it. Idempotent within one frame.
    */
   private static float[] smooth(WildlifeMob e, float age) {
      float[] w = SMOOTH.computeIfAbsent(e, k -> new float[N + 1]);
      float last = w[N];
      boolean snap = last == 0.0F || age - last > 20.0F || age < last;
      float dt = snap ? 1.0F : Math.min(5.0F, age - last);
      if (dt <= 0.0F && !snap) {
         return w;
      }
      w[N] = age == 0.0F ? 0.001F : age;
      int b = e.isDeadOrDying() ? WildlifeMob.IDLE : e.behavior();
      for (int i = 0; i < N; i++) {
         float target = switch (i) {
            case STALK -> b == WildlifeMob.STALK ? 1.0F : 0.0F;
            case CHASE -> b == WildlifeMob.CHASE ? 1.0F : 0.0F;
            case POUNCE -> b == WildlifeMob.POUNCE ? 1.0F : 0.0F;
            case TEAR -> b == WildlifeMob.TEAR ? 1.0F : 0.0F;
            default -> b == WildlifeMob.HOWL ? 1.0F : 0.0F;
         };
         float rate = switch (i) {
            case POUNCE -> 0.5F;
            case CHASE -> 0.22F;
            case STALK -> 0.1F;
            default -> 0.09F;
         };
         float k = snap ? 1.0F : 1.0F - (float)Math.exp(-dt * rate);
         w[i] += (target - w[i]) * k;
         if (!Float.isFinite(w[i])) {
            w[i] = 0.0F;
         }
      }
      return w;
   }

   /** WildlifeRenderer.animate hook: fills the rig input's hunt weights and keeps the gallop on during a chase. */
   public static void weights(WildlifeMob e, WildlifeRig.Input in, float age) {
      float[] w = smooth(e, age);
      float d = depth(e.species);
      in.eco[STALK] = w[STALK] * d;
      in.eco[CHASE] = w[CHASE];
      in.eco[POUNCE] = w[POUNCE];
      in.eco[TEAR] = w[TEAR];
      in.eco[HOWL] = e.species == WildlifeSpecies.WOLF || e.species == WildlifeSpecies.COYOTE ? w[HOWL] : 0.0F;
      // a chase runs in the gallop (FLEE drives the rotary gallop blend)
      in.w[WildlifeRig.FLEE] = Math.max(in.w[WildlifeRig.FLEE], Math.max(w[CHASE], w[POUNCE]));
   }

   // ------------------------------------------------------------------ sculpted rig

   /**
    * WildlifeRig.pose hook (before the matrices are composed): adds the hunt poses to the per-bone rotations and
    * returns the extra vertical offset of the root (negative = lowered).
    */
   public static float rig(SkinnedMesh m, WildlifeRig.Input in, float[] rx, float[] ry, float[] rz) {
      float st = in.eco[STALK], ch = in.eco[CHASE], po = in.eco[POUNCE], te = in.eco[TEAR], ho = in.eco[HOWL];
      if (st + ch + po + te + ho < 0.003F) {
         return 0.0F;
      }
      Bones b = BONES.computeIfAbsent(m, Bones::new);
      if (!b.ok) {
         return 0.0F;
      }
      float age = in.age;
      // paw heights before the hunt pose (gait, look and everything else already applied)
      float[] before = SCRATCH.get();
      paws(m, b, rx, ry, rz, before);
      // per-leg joint angle patterns (upper, lower, foot, toe) for the front and hind pair
      float[] front = LEGF.get();
      float[] hind = LEGH.get();
      java.util.Arrays.fill(front, 0.0F);
      java.util.Arrays.fill(hind, 0.0F);
      // stalk: fold every joint (zig-zag) so the body sinks while the paws stay under it // [integ] fold capped at 1.4 rad per joint (2.0 flattened cat hips / collapsed canid pounce, see docs/ws/meshes.md)
      front[0] -= st;
      front[1] += 1.4F * st;
      front[2] -= 1.4F * st;
      front[3] += st;
      hind[0] += st;
      hind[1] -= 1.4F * st;
      hind[2] += 1.4F * st;
      hind[3] -= st;
      // feeding: forelegs braced out in front, hind legs a little folded
      front[0] += 0.45F * te;
      front[1] += 0.3F * te;
      front[2] -= 0.75F * te;
      hind[0] += 0.5F * te;
      hind[1] -= te;
      hind[2] += 0.5F * te;
      // howl: half sitting on the haunches
      hind[0] += ho;
      hind[1] -= 2.0F * ho;
      hind[2] += ho;
      // how far each pair should shorten (fraction of its standing height): this sets how low the body goes
      float shoulder = m.joint[b.legs[0][0] * 3 + 1], hip = m.joint[b.legs[2][0] * 3 + 1];
      float wantF = (0.3F * st + 0.1F * te) * shoulder * (1.0F - po);
      float wantH = (0.3F * st + 0.16F * te + 0.34F * ho) * hip * (1.0F - po);
      // fold each leg so its paw stays planted where it was while the body comes down by the wanted amount: a tiny
      // two-joint IK per leg (upper and lower joint, the foot keeps its angle), started from the fold pattern
      float[] base = SCRATCH3.get();
      paws(m, b, rx, ry, rz, base);
      float reachF = -0.06F * shoulder * te;
      float reachH = -0.12F * hip * ho;
      for (int l = 0; l < 4; l++) {
         float[] q = l < 2 ? front : hind;
         float want = l < 2 ? wantF : wantH;
         if (want > 1.0E-4F) {
            addLeg(b, l, q, 1.0F, rx);
            solveLeg(m, b, l, rx, ry, rz, base[l] + want, base[4 + l] + (l < 2 ? reachF : reachH));
         }
      }
      // pounce: reaching forelegs, hind legs driving out behind (airborne, no ground solve)
      if (po > 0.0F) {
         float[] pf = {1.05F * po, -0.35F * po, 0.25F * po, 0.0F};
         float[] ph = {-0.9F * po, 0.35F * po, 0.3F * po, 0.0F};
         for (int l = 0; l < 4; l++) {
            addLeg(b, l, l < 2 ? pf : ph, 1.0F, rx);
         }
      }
      // head, neck, spine and tail
      if (b.neck >= 0) {
         rx[b.neck] += -0.32F * st - 0.22F * ch - 0.6F * te + 0.55F * ho;
         ry[b.neck] += 0.14F * te * Mth.sin(age * 0.23F);
      }
      if (b.head >= 0) {
         float yank = Mth.sin(age * 0.11F);
         yank = yank > 0.0F ? yank * yank * yank * yank : 0.0F;
         rx[b.head] += 0.22F * st + 0.12F * ch - 0.2F * po - 0.1F * te + 0.16F * te * Mth.sin(age * 0.5F) + 0.35F * te * yank + 0.6F * ho;
      }
      if (b.spine >= 0) {
         rx[b.spine] += 0.16F * po + 0.05F * ch;
      }
      if (b.tail1 >= 0) {
         rx[b.tail1] += 0.3F * st - 0.12F * ch - 0.2F * po + 0.1F * ho;
      }
      if (b.tail2 >= 0) {
         ry[b.tail2] += 0.28F * st * Mth.sin(age * 0.3F);
      }
      // keep the paws where they were: solve the body pitch (about the pelvis) and height that bring the front and hind
      // paws back to their heights before the pose (forward kinematics on the mesh's own chain); not in the air
      float ground = 1.0F - po;
      if (b.pelvis < 0 || ground <= 0.0F) {
         return 0.0F;
      }
      float[] after = SCRATCH2.get();
      float dy = 0.0F;
      // two passes: the pitch turn itself moves the paws a little, the second pass takes up the rest
      for (int pass = 0; pass < 2; pass++) {
         paws(m, b, rx, ry, rz, after);
         float dF = 0.5F * (after[0] - before[0] + after[1] - before[1]);
         float dH = 0.5F * (after[2] - before[2] + after[3] - before[3]);
         float py = m.joint[b.pelvis * 3 + 1], pz = m.joint[b.pelvis * 3 + 2];
         float zF = 0.5F * (after[4] + after[5]) - pz;
         float zH = 0.5F * (after[6] + after[7]) - pz;
         float yF = 0.5F * (after[0] + after[1]) - py;
         float yH = 0.5F * (after[2] + after[3]) - py;
         if (!Float.isFinite(dF) || !Float.isFinite(dH) || !(Math.abs(zH - zF) > 0.05F)) {
            return 0.0F;
         }
         // a nose-up turn d about the pelvis moves a point (y, z) by (-z d, y d): solve dF - zF d = dH - zH d
         float d = Mth.clamp((dH - dF) / (zH - zF), -0.5F, 0.5F) * ground;
         if (pass == 0) {
            rx[b.pelvis] += d;
         } else {
            dy = -(dF - zF * d) * ground;
            rx[b.pelvis] += d;
         }
         if (pass == 0) {
            continue;
         }
      }
      float H = m.meta[0] > 0.0F ? m.meta[0] : 1.0F;
      return Float.isFinite(dy) ? Mth.clamp(dy, -0.6F * H, 0.3F * H) : 0.0F;
   }

   private static void addLeg(Bones b, int l, float[] q, float k, float[] rx) {
      for (int j = 0; j < 4; j++) {
         rx[b.legs[l][j]] += q[j] * k;
      }
   }

   /** posed paw tip of one leg: {y, z} into {@code out} (see {@link #paws}) */
   private static void paw(SkinnedMesh m, Bones b, int l, float[] rx, float[] ry, float[] rz, float[] out) {
      int toe = b.legs[l][3];
      float px = m.joint[toe * 3], py = 0.0F, pz = m.joint[toe * 3 + 2];
      int bone = toe;
      for (int guard = 0; bone >= 0 && guard < 32; guard++) {
         float jx = m.joint[bone * 3], jy = m.joint[bone * 3 + 1], jz = m.joint[bone * 3 + 2];
         float x = px - jx, y = py - jy, z = pz - jz;
         float cx = Mth.cos(rx[bone]), sx = Mth.sin(rx[bone]);
         float y1 = y * cx - z * sx, z1 = y * sx + z * cx;
         float cy = Mth.cos(ry[bone]), sy = Mth.sin(ry[bone]);
         float x2 = x * cy + z1 * sy, z2 = -x * sy + z1 * cy;
         float cz = Mth.cos(rz[bone]), sz = Mth.sin(rz[bone]);
         float x3 = x2 * cz - y1 * sz, y3 = x2 * sz + y1 * cz;
         px = x3 + jx;
         py = y3 + jy;
         pz = z2 + jz;
         bone = m.parent[bone];
      }
      out[0] = py;
      out[1] = pz;
   }

   /** Gauss-Newton on (upper, lower) joint turns, the foot counter-turned, until the paw tip is at (ty, tz). */
   private static void solveLeg(SkinnedMesh m, Bones b, int l, float[] rx, float[] ry, float[] rz, float ty, float tz) {
      int up = b.legs[l][0], low = b.legs[l][1], foot = b.legs[l][2];
      float[] p = IK.get();
      for (int it = 0; it < 6; it++) {
         paw(m, b, l, rx, ry, rz, p);
         float ey = ty - p[0], ez = tz - p[1];
         if (Math.abs(ey) + Math.abs(ez) < 0.002F || !Float.isFinite(ey) || !Float.isFinite(ez)) {
            return;
         }
         float y0 = p[0], z0 = p[1];
         final float h = 0.01F;
         rx[up] += h;
         rx[foot] -= h;
         paw(m, b, l, rx, ry, rz, p);
         float a11 = (p[0] - y0) / h, a21 = (p[1] - z0) / h;
         rx[up] -= h;
         rx[foot] += h;
         rx[low] += h;
         rx[foot] -= h;
         paw(m, b, l, rx, ry, rz, p);
         float a12 = (p[0] - y0) / h, a22 = (p[1] - z0) / h;
         rx[low] -= h;
         rx[foot] += h;
         float det = a11 * a22 - a12 * a21;
         if (!(Math.abs(det) > 1.0E-5F)) {
            return;
         }
         float du = Mth.clamp((a22 * ey - a12 * ez) / det, -0.3F, 0.3F);
         float dl = Mth.clamp((-a21 * ey + a11 * ez) / det, -0.3F, 0.3F);
         rx[up] += du;
         rx[low] += dl;
         rx[foot] -= du + dl;
      }
   }

   private static final ThreadLocal<float[]> IK = ThreadLocal.withInitial(() -> new float[2]);

   private static final ThreadLocal<float[]> SCRATCH3 = ThreadLocal.withInitial(() -> new float[8]);
   private static final ThreadLocal<float[]> SCRATCH = ThreadLocal.withInitial(() -> new float[8]);
   private static final ThreadLocal<float[]> SCRATCH2 = ThreadLocal.withInitial(() -> new float[8]);
   private static final ThreadLocal<float[]> LEGF = ThreadLocal.withInitial(() -> new float[4]);
   private static final ThreadLocal<float[]> LEGH = ThreadLocal.withInitial(() -> new float[4]);

   /**
    * Posed paw tips (the ground point under each toe joint at rest): out[0..3] heights (fl, fr, bl, br), out[4..7]
    * their z. The same per-bone transform as WildlifeRig (about each joint, Rz*Ry*Rx), applied up the parent chain.
    */
   private static void paws(SkinnedMesh m, Bones b, float[] rx, float[] ry, float[] rz, float[] out) {
      for (int l = 0; l < 4; l++) {
         int toe = b.legs[l][3];
         float px = m.joint[toe * 3], py = 0.0F, pz = m.joint[toe * 3 + 2];
         int bone = toe;
         for (int guard = 0; bone >= 0 && guard < 32; guard++) {
            float jx = m.joint[bone * 3], jy = m.joint[bone * 3 + 1], jz = m.joint[bone * 3 + 2];
            float x = px - jx, y = py - jy, z = pz - jz;
            float cx = Mth.cos(rx[bone]), sx = Mth.sin(rx[bone]);
            float y1 = y * cx - z * sx, z1 = y * sx + z * cx;
            float cy = Mth.cos(ry[bone]), sy = Mth.sin(ry[bone]);
            float x2 = x * cy + z1 * sy, z2 = -x * sy + z1 * cy;
            float cz = Mth.cos(rz[bone]), sz = Mth.sin(rz[bone]);
            float x3 = x2 * cz - y1 * sz, y3 = x2 * sz + y1 * cz;
            px = x3 + jx;
            py = y3 + jy;
            pz = z2 + jz;
            bone = m.parent[bone];
         }
         out[l] = py;
         out[4 + l] = pz;
      }
   }

   // ------------------------------------------------------------------ Classic box models

   /** WildlifeModel.setupAnim hook (after its own poses; parts were reset at the start of the frame). */
   public static void classic(WildlifeMob e, float age, ModelPart root, ModelPart body, ModelPart neck, ModelPart head, ModelPart tail,
      ModelPart legFL, ModelPart legFR, ModelPart legBL, ModelPart legBR, boolean bird) {
      if (bird || legBL == null || legBR == null) {
         return;
      }
      float[] w = smooth(e, age);
      float st = w[STALK] * depth(e.species), ch = w[CHASE], po = w[POUNCE], te = w[TEAR];
      float ho = e.species == WildlifeSpecies.WOLF || e.species == WildlifeSpecies.COYOTE ? w[HOWL] : 0.0F;
      if (st + ch + po + te + ho < 0.003F) {
         return;
      }
      // straight box legs: spread fore and aft to bring the body down; the root drops by exactly what the legs lose
      float legLen = Math.max(4.0F, 24.0F - legFL.y);
      float spread = 0.62F * st + 0.3F * te;
      float fore = -spread - 1.0F * po;
      float aft = spread + 0.9F * po + 0.5F * ho;
      legFL.xRot += fore;
      legFR.xRot += fore;
      legBL.xRot += aft;
      legBR.xRot += aft;
      float lowF = legLen * (1.0F - Mth.cos(spread)) * (1.0F - po);
      float lowH = legLen * (1.0F - Mth.cos(spread + 0.5F * ho)) * (1.0F - po);
      root.y += 0.5F * (lowF + lowH);
      body.xRot += -0.2F * po + 0.04F * ch - 0.12F * ho;
      head.xRot += 0.22F * st + 0.1F * ch - 0.25F * po + 0.9F * te + 0.15F * te * Mth.sin(age * 0.5F) - 0.85F * ho;
      head.y += 1.0F * st + 1.2F * te;
      if (neck != null) {
         neck.xRot += 0.35F * st + 0.7F * te - 0.45F * ho;
      }
      if (tail != null) {
         tail.xRot += 0.3F * st - 0.35F * ch - 0.25F * po;
         tail.zRot += 0.2F * st * Mth.sin(age * 0.3F);
      }
   }
}
