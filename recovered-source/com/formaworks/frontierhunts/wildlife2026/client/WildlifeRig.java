package com.formaworks.frontierhunts.wildlife2026.client;

import net.minecraft.util.Mth;

/**
 * Procedural animation for the skinned (Ultra) wildlife: a leg-length-aware trot and rotary gallop,
 * head look, tail sway, breathing and blended poses for grazing, resting, alert, curious and warning. Rotations are
 * about the model axes (x lateral, y up, z back) at each bone's rest joint; the output is one 3x4 skin matrix per bone.
 */
public final class WildlifeRig {
   /** smoothed state weights, per entity: feed, rest, alert, curious, flee, warn */
   public static final int FEED = 0, REST = 1, ALERT = 2, CURIOUS = 3, FLEE = 4, WARN = 5;

   private WildlifeRig() {
   }

   public static final class Input {
      public float limbSwing;
      public float amount;
      public float age;
      public float headYaw;
      public float headPitch;
      public boolean flying;
      /** smoothed 0..1 airborne weight for quadrupeds (no pop on step-ups) */
      public float air;
      public boolean water;
      public final float[] w = new float[6];
      /** [ecology] hunt pose weights (stalk, chase, pounce, tear, howl), see ecology.client.HuntPose */
      public final float[] eco = new float[5];
      /** [wingshot] ducks / grouse: flight, limp and drumming animation state (null for other animals) */
      public com.formaworks.frontierhunts.wingshot.client.BirdAnim.State bird;
      /** [anims] ground-locked gait from the animal's real movement (WildlifeGait.track); false: from limbSwing/amount */
      public boolean gait;
      /** [anims] stride phase 0..1, ground speed (model blocks a tick), gait mix (0 walk, 1 trot, 2 gallop), style */
      public float gaitPhase, gaitSpeed, gaitMix;
      public int gaitStyle;
   }

   private static final class Scratch {
      float[] rx = new float[0], ry = new float[0], rz = new float[0];

      void size(int n) {
         if (this.rx.length != n) {
            this.rx = new float[n];
            this.ry = new float[n];
            this.rz = new float[n];
         } else {
            java.util.Arrays.fill(this.rx, 0.0F);
            java.util.Arrays.fill(this.ry, 0.0F);
            java.util.Arrays.fill(this.rz, 0.0F);
         }
      }
   }

   private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
   /** leg bone names (fl, fr, bl, br) x (upper, lower, foot, toe), built once instead of concatenated every frame */
   private static final String[][] LEG = new String[4][];
   private static final float[] PH = {0.0F, Mth.PI, Mth.PI, 0.0F};
   private static final float[] PHG = {0.0F, 0.5F, Mth.PI, Mth.PI + 0.5F};

   static {
      String[] legs = {"fl", "fr", "bl", "br"};
      for (int l = 0; l < 4; l++) {
         LEG[l] = new String[]{legs[l] + "_upper", legs[l] + "_lower", legs[l] + "_foot", legs[l] + "_toe"};
      }
   }

   private static void add(SkinnedMesh m, Scratch s, String bone, float x, float y, float z) {
      int i = m.bone(bone);
      if (i >= 0) {
         s.rx[i] += x;
         s.ry[i] += y;
         s.rz[i] += z;
      }
   }

   private static void add(SkinnedMesh m, Scratch s, String bone, float x) {
      add(m, s, bone, x, 0.0F, 0.0F);
   }

   /** Fills {@code out} (bones * 12 floats, row-major 3x4) with the skinning matrices for this frame. */
   public static void pose(SkinnedMesh m, Input in, float[] out) {
      int nb = m.names.length;
      Scratch s = SCRATCH.get();
      s.size(nb);
      if (m.rest.length >= nb * 3) {
         for (int b = 0; b < nb; b++) {
            s.rx[b] = m.rest[b * 3];
            s.ry[b] = m.rest[b * 3 + 1];
            s.rz[b] = m.rest[b * 3 + 2];
         }
      }
      float H = m.meta[0], legLen = m.meta[1], belly = m.meta[2], yawFix = m.meta[3], feedA = m.meta[4], stride = m.meta[5], pitchFix = m.meta[6];
      boolean bird = m.bird;
      boolean heavy = H > 1.25F && !bird;
      float a = Math.min(1.0F, in.amount * 1.4F);
      float phi = in.limbSwing * stride;
      float dy = 0.0F;
      float fl = in.w[FLEE];
      float age = in.age;
      if (!bird) {
         // [anims] legs: WildlifeGait.apply (after every other layer, below): planted paws, stride from the real
         // ground speed. Here only the leap and the gait phase for the tail.
         phi = Mth.TWO_PI * (in.gait ? in.gaitPhase : in.limbSwing * 0.25F);
         if (in.air > 0.0F) {
            // leaping (jumps, drops): forelegs reach, hind legs push back
            float j = 0.35F * in.air;
            add(m, s, "fl_upper", j);
            add(m, s, "fr_upper", j);
            add(m, s, "bl_upper", -j);
            add(m, s, "br_upper", -j);
         }
      } else if (in.bird != null) {
         dy += com.formaworks.frontierhunts.wingshot.client.BirdPose.rig(m, in, s.rx, s.ry, s.rz); // [wingshot] flight / limp / drumming pose
      } else {
         float A = 0.7F * a;
         add(m, s, "l_upper", A * Mth.sin(phi));
         add(m, s, "r_upper", -A * Mth.sin(phi));
         add(m, s, "l_foot", -0.6F * a * Math.max(0.0F, Mth.cos(phi)));
         add(m, s, "r_foot", -0.6F * a * Math.max(0.0F, -Mth.cos(phi)));
         add(m, s, "neck", 0.12F * a * Mth.sin(2.0F * phi));
         if (in.flying) {
            float f = 0.9F + 0.8F * Mth.sin(age * 1.3F);
            add(m, s, "wing_l", 0.0F, 0.0F, -f);
            add(m, s, "wing_r", 0.0F, 0.0F, f);
            add(m, s, "l_upper", -0.9F);
            add(m, s, "r_upper", -0.9F);
         }
         if (in.water) {
            dy -= 0.12F * H;
            add(m, s, "l_upper", 0.5F * Mth.sin(age * 0.3F));
            add(m, s, "r_upper", -0.5F * Mth.sin(age * 0.3F));
         }
      }
      add(m, s, "spine", 0.012F * Mth.sin(age * 0.12F));
      add(m, s, "tail1", 0.0F, 0.12F * Mth.sin(age * 0.1F) + 0.15F * a * Mth.cos(phi), 0.0F);
      add(m, s, "tail2", 0.0F, 0.18F * Mth.sin(age * 0.1F - 0.8F), 0.0F);
      float rsw = in.w[REST];
      // lying down: gentler, clamped head look so the head never folds into the chest
      float hy = Mth.clamp(in.headYaw, -60.0F, 60.0F) * Mth.DEG_TO_RAD * (1.0F - 0.5F * rsw);
      float hp = Mth.clamp(in.headPitch, -40.0F + 25.0F * rsw, 40.0F - 32.0F * rsw) * Mth.DEG_TO_RAD;
      add(m, s, "neck", 0.5F * pitchFix, 0.5F * yawFix - 0.5F * hy, 0.0F);
      add(m, s, "head", -0.8F * hp + 0.5F * pitchFix, 0.5F * yawFix - 0.5F * hy, 0.0F);
      float fd = in.w[FEED], rs = in.w[REST], al = in.w[ALERT], cu = in.w[CURIOUS], wr = in.w[WARN];
      if (fd > 0.0F && m.feed.length >= nb * 3) {
         for (int b = 0; b < nb; b++) {
            s.rx[b] += fd * m.feed[b * 3];
            s.ry[b] += fd * m.feed[b * 3 + 1];
            s.rz[b] += fd * m.feed[b * 3 + 2];
         }
         if (m.meta.length > 7) {
            dy -= fd * m.meta[7];
         }
      }
      add(m, s, "head", 0.06F * fd * Mth.sin(age * 0.45F));
      add(m, s, "neck", 0.25F * al);
      add(m, s, "head", 0.1F * al);
      add(m, s, "tail1", 0.3F * al + 0.35F * fl);
      add(m, s, "head", 0.0F, 0.0F, 0.18F * cu * Mth.sin(age * 0.08F));
      add(m, s, "neck", -0.35F * wr);
      add(m, s, "head", 0.25F * wr);
      add(m, s, "tail1", -0.2F * wr);
      if (rs > 0.0F) {
         if (!bird) {
            // lie down: the body settles onto the ground and the legs tuck beneath it (below the ground surface)
            dy -= belly * 0.82F * rs;
            for (int l = 0; l < 2; l++) {
               add(m, s, LEG[l][0], -0.3F * rs);
               add(m, s, LEG[l][2], -0.4F * rs);
            }
            for (int l = 2; l < 4; l++) {
               add(m, s, LEG[l][0], 0.35F * rs);
               add(m, s, LEG[l][2], 0.3F * rs);
            }
            add(m, s, "head", -0.15F * rs);
            add(m, s, "tail1", -0.2F * rs);
         } else {
            dy -= legLen * 0.85F * rs;
         }
      }
      dy += com.formaworks.frontierhunts.ecology.client.HuntPose.rig(m, in, s.rx, s.ry, s.rz); // [ecology] stalk / chase / pounce / feed / howl
      dy += WildlifeGait.apply(m, in, s.rx, s.ry, s.rz, dy); // [anims] ground-locked gait (legs IK'd onto planted paws)
      // ---- global matrices: G = G_parent * T(j) * Rz*Ry*Rx * T(-j)
      for (int b = 0; b < nb; b++) {
         float cx = Mth.cos(s.rx[b]), sx = Mth.sin(s.rx[b]);
         float cy = Mth.cos(s.ry[b]), sy = Mth.sin(s.ry[b]);
         float cz = Mth.cos(s.rz[b]), sz = Mth.sin(s.rz[b]);
         // R = Rz * Ry * Rx
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
      if (in.bird != null) {
         com.formaworks.frontierhunts.wingshot.client.BirdPose.post(m, in, out); // [wingshot] fanned tail
      }
   }
}
