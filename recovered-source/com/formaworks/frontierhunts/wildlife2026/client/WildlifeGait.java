package com.formaworks.frontierhunts.wildlife2026.client;

import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import net.minecraft.util.Mth;

/**
 * [anims] Ground-locked quadruped gaits for the sculpted (Ultra) wildlife.
 *
 * <p>The old rig swung each leg as a sine of {@code walkAnimation.position} with a fixed small amplitude (at most
 * 18 degrees). A planted paw then moved back far slower than the ground passed under it (a wolf's paw covered about a
 * third of the distance its body travelled per step), and {@code walkAnimation} speed saturates at 0.25 blocks a tick,
 * so above a trot the legs stopped speeding up at all: in game the animals glided over the ground with their legs
 * twitching. Here the gait is driven by the distance the animal really travels:
 * <ul>
 * <li>{@link #track}: per animal, the gait phase advances by (ground distance / stride length). The stride length
 * follows the animal's speed by Alexander's dynamic-similarity rule (stride / hip height = 2.3 Froude^0.3), capped by
 * what its legs can reach, so the stride frequency rises naturally with speed and never runs ahead of the ground.</li>
 * <li>{@link #apply}: each paw follows a stance/swing cycle in the body frame: during stance it moves back exactly as
 * far as the body moves forward (it stays planted), during swing it lifts and reaches forward. A small two-joint IK per
 * leg (upper and lower joint, the foot keeping its angle) on the mesh's own skeleton puts the paw tip there, on top of
 * every other pose (grazing, stalking, alert), so the paws never slide or sink.</li>
 * <li>Per-species gait styles: canids trot, cats walk low and long, the cheetah's gallop flexes the whole spine, bears
 * amble with a rolling gait, bison walk heavily with a head nod, pronghorn and boar trot and bound.</li>
 * </ul>
 */
public final class WildlifeGait {
   public static final int CANID = 0, CAT = 1, CHEETAH = 2, BEAR = 3, BISON = 4, HOOF = 5;

   private WildlifeGait() {
   }

   /** touchdown phases (fraction of a stride) of fl, fr, bl, br */
   private static final float[] WALK = {0.25F, 0.75F, 0.0F, 0.5F};
   /** bears' amble: a lateral walk close to a pace (the legs of one side move almost together: the rolling gait) */
   private static final float[] AMBLE = {0.14F, 0.64F, 0.0F, 0.5F};
   private static final float[] TROT = {0.0F, 0.5F, 0.5F, 0.0F};
   /** rotary gallop (canids, cats): left hind, right hind, right fore, left fore */
   private static final float[] ROTARY = {0.6F, 0.48F, 0.0F, 0.1F};
   /** transverse gallop (bears, bison, ungulates): left hind, right hind, left fore, right fore */
   private static final float[] TRANSVERSE = {0.48F, 0.6F, 0.0F, 0.1F};

   /** One gait style: three gaits (slow, middle, fast) blended by speed. Lengths are fractions of the hip height. */
   public static final class Style {
      final float[][] off = new float[3][];
      final float[] duty;
      /** Froude numbers of the slow-to-middle and middle-to-fast gait changes */
      final float frMid, frFast;
      final float crouch, lift, roll, flex, nod, sway;
      /**
       * highest stride frequency as a fraction of sqrt(g / hip height) / 2pi (dynamic similarity: a coyote's legs may
       * cycle faster than a bison's); beyond it the stride lengthens past the legs' reach instead
       */
      final float fMax;
      /** swing fold of the fore and hind pastern (bears flip the fore paw and carry the hind one flat; hooves fold hard) */
      float curlF = -0.95F, curlH = 0.5F;

      Style(float[] slow, float[] mid, float[] fast, float[] duty, float frMid, float frFast, float crouch, float lift, float roll, float flex,
         float nod, float sway, float hz) {
         this.fMax = hz;
         this.off[0] = slow.clone();
         this.off[1] = align(slow, mid);
         this.off[2] = align(this.off[1], fast);
         this.duty = duty;
         this.frMid = frMid;
         this.frFast = frFast;
         this.crouch = crouch;
         this.lift = lift;
         this.roll = roll;
         this.flex = flex;
         this.nod = nod;
         this.sway = sway;
      }

      /** the same gait shifted as a whole (which does not change it) so that blending from {@code ref} moves the legs least */
      private static float[] align(float[] ref, float[] g) {
         float best = 0.0F, bestCost = Float.MAX_VALUE;
         for (int k = 0; k < 100; k++) {
            float sh = k / 100.0F, cost = 0.0F;
            for (int l = 0; l < 4; l++) {
               float d = wrap(g[l] + sh - ref[l]);
               cost += d * d;
            }
            if (cost < bestCost) {
               bestCost = cost;
               best = sh;
            }
         }
         float[] out = new float[4];
         for (int l = 0; l < 4; l++) {
            out[l] = ref[l] + wrap(g[l] + best - ref[l]);
         }
         return out;
      }

      float duty(float g) {
         return g <= 1.0F ? Mth.lerp(g, this.duty[0], this.duty[1]) : Mth.lerp(Math.min(1.0F, g - 1.0F), this.duty[1], this.duty[2]);
      }

      float offset(int l, float g) {
         return g <= 1.0F ? Mth.lerp(g, this.off[0][l], this.off[1][l]) : Mth.lerp(Math.min(1.0F, g - 1.0F), this.off[1][l], this.off[2][l]);
      }

      /** gait mix for a Froude number: 0 slow gait, 1 middle, 2 fast */
      public float mix(float fr) {
         return smooth((fr - this.frMid * 0.75F) / (this.frMid * 0.5F)) + smooth((fr - this.frFast * 0.8F) / (this.frFast * 0.4F));
      }
   }

   private static final Style[] STYLES = {
      // canids: walk, trot early, rotary gallop
      new Style(WALK, TROT, ROTARY, new float[]{0.66F, 0.46F, 0.24F}, 0.3F, 2.0F, 0.07F, 0.15F, 0.02F, 0.1F, 0.03F, 0.02F, 5.0F),
      // big cats: a low, long walk (no trot: a quicker walk), rotary gallop with a supple spine
      new Style(WALK, WALK, ROTARY, new float[]{0.64F, 0.55F, 0.24F}, 0.8F, 2.0F, 0.11F, 0.14F, 0.03F, 0.22F, 0.015F, 0.03F, 4.4F),
      // cheetah: walk, quick walk, the long flexing sprint
      new Style(WALK, WALK, ROTARY, new float[]{0.64F, 0.55F, 0.2F}, 0.8F, 2.2F, 0.08F, 0.12F, 0.025F, 0.3F, 0.015F, 0.025F, 4.9F),
      // bears: rolling amble, quicker amble, transverse gallop
      new Style(AMBLE, AMBLE, TRANSVERSE, new float[]{0.66F, 0.54F, 0.28F}, 0.55F, 1.6F, 0.06F, 0.12F, 0.07F, 0.08F, 0.03F, 0.07F, 4.4F),
      // bison: heavy walk with a nodding head, trot, transverse gallop
      new Style(WALK, TROT, TRANSVERSE, new float[]{0.7F, 0.47F, 0.3F}, 0.32F, 1.4F, 0.055F, 0.11F, 0.03F, 0.06F, 0.05F, 0.025F, 4.6F),
      // pronghorn, boar: walk, trot, transverse gallop
      new Style(WALK, TROT, TRANSVERSE, new float[]{0.66F, 0.45F, 0.24F}, 0.35F, 1.5F, 0.05F, 0.16F, 0.02F, 0.1F, 0.04F, 0.02F, 5.2F)
   };

   static {
      STYLES[BEAR].curlF = -0.7F; // the fore paw flips its pads up and back
      STYLES[BEAR].curlH = 0.05F; // the long hind sole swings through nearly flat
      STYLES[BISON].curlF = -1.2F; // the fetlock folds right up under a heavy hoof
      STYLES[BISON].curlH = 0.6F;
      STYLES[HOOF].curlF = -1.1F;
      STYLES[HOOF].curlH = 0.6F;
   }

   public static int style(WildlifeSpecies s) {
      return switch (s) {
         case WOLF, COYOTE -> CANID;
         case COUGAR, PANTHER, LION -> CAT;
         case CHEETAH -> CHEETAH;
         case GRIZZLY, BLACK_BEAR, POLAR_BEAR -> BEAR;
         case BISON -> BISON;
         default -> HOOF;
      };
   }

   public static Style style(int s) {
      return STYLES[s < 0 || s >= STYLES.length ? HOOF : s];
   }

   static int index(Style st) {
      for (int i = 0; i < STYLES.length; i++) {
         if (STYLES[i] == st) return i;
      }
      return HOOF;
   }

   static float wrap(float d) {
      d -= Math.round(d);
      return d;
   }

   static float smooth(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   // ------------------------------------------------------------------ per mesh

   /** one bind-space vertex with its skin weights, posed like the mesh skins it */
   static final class Pt {
      final float x, y, z;
      final int[] b = new int[4];
      final float[] w = new float[4];

      Pt(SkinnedMesh m, int v) {
         this.x = m.pos[v * 3];
         this.y = m.pos[v * 3 + 1];
         this.z = m.pos[v * 3 + 2];
         for (int k = 0; k < 4; k++) {
            this.b[k] = m.bone[v * 4 + k] & 255;
            this.w[k] = m.weight[v * 4 + k];
         }
      }

      /** posed height (model space, before the root offset) */
      float posedY(SkinnedMesh m, float[] rx, float[] ry, float[] rz, float[] tmp) {
         float y = 0.0F, ws = 0.0F;
         for (int k = 0; k < 4; k++) {
            if (this.w[k] <= 0.0F) continue;
            point(m, this.b[k], this.x, this.y, this.z, rx, ry, rz, tmp);
            y += this.w[k] * tmp[1];
            ws += this.w[k];
         }
         return ws > 0.0F ? y / ws : this.y;
      }
   }

   /**
    * swing path: the paw's speed at lift-off and touchdown as a share of the ground's speed (1: it leaves and lands at
    * rest on the ground, after reaching a little past the stance's front end and drawing back onto it), how far past the
    * stance's span it may reach, and the lift profile's exponent (below 1: clear of the ground until set down)
    */
   static final float SWING_M0 = 1.0F, SWING_OVER = 0.7F; // lift: sqrt of a half sine
   /** smallest dip (blocks) the contact guard corrects */
   static final float GUARD_MIN = 0.002F;

   static float lowest(SkinnedMesh m, Pt[] pts, float[] rx, float[] ry, float[] rz, float[] tmp) {
      float lo = Float.MAX_VALUE;
      for (Pt p : pts) lo = Math.min(lo, p.posedY(m, rx, ry, rz, tmp));
      return lo;
   }

   /** Skeleton measures of one mesh (cached on the mesh). */
   public static final class Info {
      final boolean ok;
      final int[][] legs = new int[4][4];
      final int pelvis, spine, chest, neck, head, tail;
      /** hip height (mean of the shoulder and hip joints), chain length and joint height of the fore and hind legs */
      final float h, chainF, chainH, jyF, jyH, halfLen;
      /**
       * the sole point of each paw: the vertex at the middle of the lowest band of the vertices that follow that leg,
       * with its skin weights, so the IK places the paw the player sees (some sculpts weight their paws partly to the
       * shin or the body, not only to the toe bone)
       */
      final float[][] solePos = new float[4][3];
      final int[][] soleBone = new int[4][4];
      final float[][] soleW = new float[4][4];
      /** which side of the hip-ankle line each leg's knee / elbow / hock bends to (+1 or -1, from the sculpt) */
      final float[] bend = new float[4];
      /** the toe's own rest turn (the sum of rest turns down the chain is what "flat" means for this sculpt) */
      final float[] toeRest = new float[4];
      /** the sole point's height above the paw's lowest vertex (a paw sculpted floating still lands on the ground) */
      final float[] soleUp = new float[4];
      /**
       * contact guard: the paw's outline at the bottom (heel, toes, both sides, lowest point): none of them may go under
       * the ground (a tilted pastern or a sculpt posed mid-stride put a bear's heel or a lion's toe 3-7 cm in the soil)
       */
      final Pt[][] guard = new Pt[4][];
      /** the tail's tip and lowest points: a long cat tail must not trail through the ground when the body comes down */
      Pt[] tailPts = new Pt[0];
      /** the back of each hock / wrist and the lowest point of the shin: kept off the ground in a crouch */
      final Pt[][] joints = new Pt[4][];
      int tail2 = -1;

      Info(SkinnedMesh m) {
         String[] l = {"fl", "fr", "bl", "br"};
         String[] s = {"_upper", "_lower", "_foot", "_toe"};
         boolean all = !m.bird && m.joint.length >= m.names.length * 3;
         for (int i = 0; i < 4; i++) {
            for (int k = 0; k < 4; k++) {
               this.legs[i][k] = m.bone(l[i] + s[k]);
               all &= this.legs[i][k] >= 0;
            }
         }
         this.pelvis = m.bone("pelvis");
         this.spine = m.bone("spine");
         this.chest = m.bone("chest");
         this.neck = m.bone("neck");
         this.head = m.bone("head");
         this.tail = m.bone("tail1");
         this.ok = all && this.pelvis >= 0;
         if (!this.ok) {
            this.h = this.chainF = this.chainH = this.jyF = this.jyH = this.halfLen = 1.0F;
            return;
         }
         float[] c = new float[4], jy = new float[4];
         for (int i = 0; i < 4; i++) {
            float len = 0.0F;
            for (int k = 0; k < 3; k++) {
               len += dist(m.joint, this.legs[i][k], this.legs[i][k + 1]);
            }
            c[i] = len + m.joint[this.legs[i][3] * 3 + 1];
            jy[i] = m.joint[this.legs[i][0] * 3 + 1];
         }
         this.chainF = Math.min(c[0], c[1]);
         this.chainH = Math.min(c[2], c[3]);
         this.jyF = 0.5F * (jy[0] + jy[1]);
         this.jyH = 0.5F * (jy[2] + jy[3]);
         this.h = Math.max(0.1F, 0.5F * (this.jyF + this.jyH));
         float zf = 0.5F * (m.joint[this.legs[0][0] * 3 + 2] + m.joint[this.legs[1][0] * 3 + 2]);
         float zh = 0.5F * (m.joint[this.legs[2][0] * 3 + 2] + m.joint[this.legs[3][0] * 3 + 2]);
         this.halfLen = Math.max(0.1F, 0.5F * Math.abs(zh - zf));
         this.tail(m);
         for (int i = 0; i < 4; i++) {
            sole(m, i);
            this.guard[i] = this.outline(m, i);
            this.joints[i] = this.joint(m, i);
            float sum = 0.0F;
            for (int bone = this.legs[i][3], guard = 0; bone >= 0 && guard < 32; bone = m.parent[bone], guard++) {
               sum += m.rest.length > bone * 3 ? m.rest[bone * 3] : 0.0F;
            }
            this.toeRest[i] = sum;
            int up = this.legs[i][0], lo = this.legs[i][1], ft = this.legs[i][2];
            float[] jj = m.joint;
            // sign of the knee's angle from the hip->ankle direction (angles measured as atan2(z, y), as the rig turns)
            double base = Math.atan2(jj[ft * 3 + 2] - jj[up * 3 + 2], jj[ft * 3 + 1] - jj[up * 3 + 1]);
            double knee = Math.atan2(jj[lo * 3 + 2] - jj[up * 3 + 2], jj[lo * 3 + 1] - jj[up * 3 + 1]);
            double dd = Math.IEEEremainder(knee - base, 2 * Math.PI);
            this.bend[i] = dd >= 0 ? 1.0F : -1.0F;
         }
      }

      private void sole(SkinnedMesh m, int l) {
         int nv = m.vertexCount;
         float minY = Float.MAX_VALUE;
         for (int v = 0; v < nv; v++) {
            if (this.ofLeg(m, v, l)) minY = Math.min(minY, m.pos[v * 3 + 1]);
         }
         // centre of the sole band
         float cx = 0, cz = 0;
         int n = 0;
         for (int v = 0; v < nv; v++) {
            if (this.ofLeg(m, v, l) && m.pos[v * 3 + 1] < minY + 0.012F) {
               cx += m.pos[v * 3];
               cz += m.pos[v * 3 + 2];
               n++;
            }
         }
         int toe = this.legs[l][3];
         if (n == 0) {
            this.solePos[l][0] = m.joint[toe * 3];
            this.solePos[l][2] = m.joint[toe * 3 + 2];
            this.soleBone[l][0] = toe;
            this.soleW[l][0] = 1.0F;
            return;
         }
         cx /= n;
         cz /= n;
         int best = -1;
         float bd = Float.MAX_VALUE;
         for (int v = 0; v < nv; v++) {
            if (this.ofLeg(m, v, l) && m.pos[v * 3 + 1] < minY + 0.012F) {
               float dx = m.pos[v * 3] - cx, dz = m.pos[v * 3 + 2] - cz;
               float d = dx * dx + dz * dz;
               if (d < bd) {
                  bd = d;
                  best = v;
               }
            }
         }
         this.solePos[l][0] = m.pos[best * 3];
         this.solePos[l][1] = m.pos[best * 3 + 1];
         this.soleUp[l] = Math.max(0.0F, m.pos[best * 3 + 1] - minY);
         this.solePos[l][2] = m.pos[best * 3 + 2];
         for (int k = 0; k < 4; k++) {
            this.soleBone[l][k] = m.bone[best * 4 + k] & 255;
            this.soleW[l][k] = m.weight[best * 4 + k];
         }
      }

      /** up to six vertices around the bottom of the paw: lowest, front, back, both sides, and the band's middle */
      private Pt[] outline(SkinnedMesh m, int l) {
         int nv = m.vertexCount;
         float minY = Float.MAX_VALUE;
         for (int v = 0; v < nv; v++) {
            if (this.ofLeg(m, v, l)) minY = Math.min(minY, m.pos[v * 3 + 1]);
         }
         int[] pick = {-1, -1, -1, -1, -1};
         float band = minY + 0.035F * this.h;
         for (int v = 0; v < nv; v++) {
            if (!this.ofLeg(m, v, l) || m.pos[v * 3 + 1] > band) continue;
            float x = m.pos[v * 3], y = m.pos[v * 3 + 1], z = m.pos[v * 3 + 2];
            if (pick[0] < 0 || y < m.pos[pick[0] * 3 + 1]) pick[0] = v;
            if (pick[1] < 0 || z < m.pos[pick[1] * 3 + 2]) pick[1] = v;
            if (pick[2] < 0 || z > m.pos[pick[2] * 3 + 2]) pick[2] = v;
            if (pick[3] < 0 || x < m.pos[pick[3] * 3]) pick[3] = v;
            if (pick[4] < 0 || x > m.pos[pick[4] * 3]) pick[4] = v;
         }
         java.util.List<Pt> out = new java.util.ArrayList<>();
         for (int v : pick) {
            if (v >= 0) out.add(new Pt(m, v));
         }
         return out.toArray(new Pt[0]);
      }

      /** around the hock / wrist (the lower-to-foot joint): the rearmost, the foremost and the lowest vertex near it */
      private Pt[] joint(SkinnedMesh m, int l) {
         int lo = this.legs[l][1], ft = this.legs[l][2];
         float jx = m.joint[ft * 3], jy = m.joint[ft * 3 + 1], jz = m.joint[ft * 3 + 2];
         float r = 0.22F * this.h;
         int back = -1, front = -1, low = -1;
         for (int v = 0; v < m.vertexCount; v++) {
            int best = 0;
            for (int k = 1; k < 4; k++) {
               if (m.weight[v * 4 + k] > m.weight[v * 4 + best]) best = k;
            }
            int b = m.bone[v * 4 + best] & 255;
            if (b != lo && b != ft) continue;
            float x = m.pos[v * 3], y = m.pos[v * 3 + 1], z = m.pos[v * 3 + 2];
            if ((x - jx) * (x - jx) + (y - jy) * (y - jy) + (z - jz) * (z - jz) > r * r) continue;
            if (y < jy - 0.5F * r) continue; // the paw itself (the contact guard has it)
            if (back < 0 || z > m.pos[back * 3 + 2]) back = v;
            if (front < 0 || z < m.pos[front * 3 + 2]) front = v;
            if (low < 0 || y < m.pos[low * 3 + 1]) low = v;
         }
         java.util.List<Pt> out = new java.util.ArrayList<>();
         for (int v : new int[]{back, front, low}) {
            if (v >= 0) out.add(new Pt(m, v));
         }
         return out.toArray(new Pt[0]);
      }

      /** the tail's tip (farthest from its root) and its lowest vertex at rest */
      void tail(SkinnedMesh m) {
         if (this.tail < 0) return;
         this.tail2 = m.bone("tail2");
         int nv = m.vertexCount, tip = -1, low = -1;
         float far = 0.0F;
         float jx = m.joint[this.tail * 3], jy = m.joint[this.tail * 3 + 1], jz = m.joint[this.tail * 3 + 2];
         for (int v = 0; v < nv; v++) {
            int best = 0;
            for (int k = 1; k < 4; k++) {
               if (m.weight[v * 4 + k] > m.weight[v * 4 + best]) best = k;
            }
            int b = m.bone[v * 4 + best] & 255;
            if (b != this.tail && b != this.tail2) continue;
            float dx = m.pos[v * 3] - jx, dy = m.pos[v * 3 + 1] - jy, dz = m.pos[v * 3 + 2] - jz;
            float d = dx * dx + dy * dy + dz * dz;
            if (d > far) {
               far = d;
               tip = v;
            }
            if (low < 0 || m.pos[v * 3 + 1] < m.pos[low * 3 + 1]) low = v;
         }
         if (tip >= 0) this.tailPts = low >= 0 && low != tip ? new Pt[]{new Pt(m, tip), new Pt(m, low)} : new Pt[]{new Pt(m, tip)};
      }

      private boolean ofLeg(SkinnedMesh m, int v, int l) {
         int best = 0;
         for (int k = 1; k < 4; k++) {
            if (m.weight[v * 4 + k] > m.weight[v * 4 + best]) best = k;
         }
         int b = m.bone[v * 4 + best] & 255;
         int[] c = this.legs[l];
         return b == c[0] || b == c[1] || b == c[2] || b == c[3];
      }

      private static float dist(float[] j, int a, int b) {
         float dx = j[a * 3] - j[b * 3], dy = j[a * 3 + 1] - j[b * 3 + 1], dz = j[a * 3 + 2] - j[b * 3 + 2];
         return Mth.sqrt(dx * dx + dy * dy + dz * dz);
      }

      /**
       * per style and body height (standing / gallop crouch): the fore-aft span each planted paw can really cover on
       * the ground (its legs' own lengths, from the standing pose), and where that span's middle lies relative to
       * where the paw stands (sculpts posed mid-stride have one leg forward): [style][2][4 legs centre, cap]
       */
      private final float[][][] span = new float[STYLES.length][][];
      private SkinnedMesh mesh;

      /** planted fore-aft travel available (blocks), body lowered by {@code crouch} x hip height */
      float reach(Style st, int si, float g) {
         float[][] sp = this.span(si);
         float gal = Mth.clamp(g - 1.0F, 0.0F, 1.0F);
         return Mth.lerp(gal, sp[0][4], sp[1][4]);
      }

      float centre(int si, int l, float g) {
         float[][] sp = this.span(si);
         return Mth.lerp(Mth.clamp(g - 1.0F, 0.0F, 1.0F), sp[0][l], sp[1][l]);
      }

      private float[][] span(int si) {
         float[][] sp = this.span[si];
         if (sp == null) {
            sp = new float[2][5];
            Style st = STYLES[si];
            for (int k = 0; k < 2; k++) {
               measure(this.mesh, st, crouch(st, k * 2.0F) * this.h, sp[k]);
            }
            this.span[si] = sp;
         }
         return sp;
      }

      private void measure(SkinnedMesh m, Style st, float lower, float[] out) {
         int nb = m.names.length;
         float[] rx = new float[nb], ry = new float[nb], rz = new float[nb];
         if (m.rest.length >= nb * 3) {
            for (int b = 0; b < nb; b++) {
               rx[b] = m.rest[b * 3];
               ry[b] = m.rest[b * 3 + 1];
               rz[b] = m.rest[b * 3 + 2];
            }
         }
         float[] p = new float[3];
         float cap = Float.MAX_VALUE;
         for (int l = 0; l < 4; l++) {
            int up = this.legs[l][0], low = this.legs[l][1], foot = this.legs[l][2];
            float[] j = m.joint;
            point(m, m.parent[up], j[up * 3], j[up * 3 + 1], j[up * 3 + 2], rx, ry, rz, p);
            float hy = p[1], hz = p[2];
            point(m, up, j[low * 3], j[low * 3 + 1], j[low * 3 + 2], rx, ry, rz, p);
            float ky = p[1], kz = p[2];
            point(m, low, j[foot * 3], j[foot * 3 + 1], j[foot * 3 + 2], rx, ry, rz, p);
            float fy = p[1], fz = p[2];
            paw(m, this, l, rx, ry, rz, p);
            float a = Mth.sqrt((ky - hy) * (ky - hy) + (kz - hz) * (kz - hz));
            float b = Mth.sqrt((fy - ky) * (fy - ky) + (fz - kz) * (fz - kz));
            // with the body lowered, the planted sole is that much higher relative to the hip; keep 3% of the leg
            // in hand for the bob and the roll
            float r = 0.97F * (a + b);
            float vy = this.soleUp[l] + lower - (p[1] - fy) - hy;
            float half = Math.abs(vy) < r ? Mth.sqrt(r * r - vy * vy) : 0.0F;
            float mid = hz + (p[2] - fz);
            out[l] = mid - p[2];
            cap = Math.min(cap, 2.0F * half);
         }
         out[4] = Math.max(0.02F, 0.95F * cap);
      }
   }

   public static Info info(SkinnedMesh m) {
      Info i = m.gait;
      if (i == null) {
         i = new Info(m);
         i.mesh = m;
         m.gait = i;
      }
      return i;
   }

   /**
    * Stride length (blocks, model scale) at ground speed {@code v} (blocks a tick) and gait mix {@code g}: Alexander's
    * dynamic similarity, capped by the legs' planted reach (then the stride frequency rises instead).
    */
   public static float stride(Info info, Style st, float v, float g) {
      float vm = Math.max(1.0E-4F, v) * 20.0F;
      float fr = vm * vm / (9.81F * info.h);
      float lambda = 2.3F * info.h * (float)Math.pow(fr, 0.3);
      // the stance may shorten (a flying trot, the gallop's suspension) but a walk keeps at least half the stride
      // on the ground: past that the stride shortens and the legs cycle faster, up to a size-scaled ceiling
      lambda = Math.min(lambda, sweepCap(info, st, g) / minDuty(st, g));
      float fMax = st.fMax * (1.0F + 0.3F * Mth.clamp(g - 1.0F, 0.0F, 1.0F)) * Mth.sqrt(9.81F / info.h) / Mth.TWO_PI / 20.0F;
      return Math.max(lambda, Math.max(1.0E-4F, v) / fMax);
   }

   /** share of the stride a paw is planted: the gait's own, less when the stride outgrows the legs' reach */
   static float duty(Info info, Style st, float lambda, float g) {
      return Mth.clamp(sweepCap(info, st, g) / lambda, minDuty(st, g), st.duty(g));
   }

   static float minDuty(Style st, float g) {
      // a gallop's stance can get very short (the cheetah's sprint: under a tenth of the stride per paw)
      float fast = st == STYLES[CHEETAH] ? 0.1F : 0.13F;
      return g <= 1.0F ? Mth.lerp(g, 0.55F, 0.3F) : Mth.lerp(Math.min(1.0F, g - 1.0F), 0.3F, fast);
   }

   /** the farthest a planted paw can travel back (the spine's flex adds a little in the gallop) */
   static float sweepCap(Info info, Style st, float g) {
      float gal = Mth.clamp(g - 1.0F, 0.0F, 1.0F);
      return info.reach(st, index(st), g) * (1.0F + 0.2F * gal);
   }

   /** body lowered by this fraction of the hip height in a gait (a little more in the gallop: legs gathered) */
   static float crouch(Style st, float g) {
      return st.crouch + 0.04F * Mth.clamp(g - 1.0F, 0.0F, 1.0F);
   }

   public static float froude(Info info, float v) {
      float vm = v * 20.0F;
      return vm * vm / (9.81F * info.h);
   }

   // ------------------------------------------------------------------ per animal (renderer side)

   /** Per-animal gait state, advanced from the animal's real (interpolated) movement. */
   public static final class Track {
      double x, z;
      float yaw;
      float time = Float.NaN;
      public float phase, v, g;
      /** last ground distance per tick from the positions (before smoothing) */
      public float vRaw;
   }

   /**
    * Advances the gait of one animal: {@code x, z} its interpolated position this frame, {@code yawDeg} its body yaw,
    * {@code now} ticks + partial tick, {@code scale} the entity scale, {@code vTick} its movement over the last tick
    * (blocks), {@code walkPos/walkSpeed} its vanilla walk animation (used only when it is posed without moving: kill-cam
    * stand-ins and trail-camera actors). Idempotent within one frame.
    */
   public static void track(Track t, Info info, int style, double x, double z, float yawDeg, float now, float scale, float vTick, float walkPos,
      float walkSpeed) {
      Style st = style(style);
      float k = Math.max(0.05F, scale);
      if (!(t.time == t.time) || now - t.time > 20.0F || now < t.time) {
         t.x = x;
         t.z = z;
         t.yaw = yawDeg;
         t.time = now;
         t.v = vTick / k;
         t.g = st.mix(froude(info, t.v));
         return;
      }
      float dt = now - t.time;
      if (dt <= 0.0F) {
         return;
      }
      double dx = x - t.x, dz = z - t.z;
      float d = (float)Math.sqrt(dx * dx + dz * dz) / k;
      if (d > 4.0F) {
         d = 0.0F; // teleported
      }
      // turning on the spot steps the legs too (each paw travels about a third of the body's half length per radian)
      float turn = Math.abs(Mth.wrapDegrees(yawDeg - t.yaw)) * Mth.DEG_TO_RAD;
      d += Math.min(turn, 0.6F) * 0.35F * info.halfLen;
      t.x = x;
      t.z = z;
      t.yaw = yawDeg;
      t.time = now;
      t.vRaw = vTick / k;
      float target = Math.max(t.vRaw, Math.min(0.08F, turn / Math.max(dt, 0.05F) * 0.35F * info.halfLen));
      t.v += (target - t.v) * (1.0F - (float)Math.exp(-dt * 0.45F));
      if (t.v < 0.003F && walkSpeed > 0.05F) {
         // posed without moving (stand-ins, actors): the vanilla walk cycle is all there is
         float v = Math.min(1.0F, walkSpeed) * 0.25F;
         t.g = st.mix(froude(info, v));
         t.phase = frac(walkPos * 0.25F / stride(info, st, v, t.g));
         t.v = v;
         return;
      }
      t.g += (st.mix(froude(info, t.v)) - t.g) * (1.0F - (float)Math.exp(-dt * 0.12F));
      t.phase = frac(t.phase + d / stride(info, st, Math.max(t.v, 0.004F), t.g));
   }

   static float frac(float p) {
      return p - Mth.floor(p);
   }

   /** Hands the tracked gait to the rig. */
   public static void fill(Track t, int style, WildlifeRig.Input in) {
      in.gait = true;
      in.gaitStyle = style;
      in.gaitPhase = t.phase;
      in.gaitSpeed = t.v;
      in.gaitMix = t.g;
   }

   // ------------------------------------------------------------------ rig

   private static final class Scratch {
      final float[] base = new float[12];
      final float[] p = new float[3];
      final float[] leg = new float[12];
   }

   /** offline tools only (null in game): per leg target y, z and reached y, z of the last pose */
   static float[] DEBUG;
   private static float lastTz;

   private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

   /**
    * WildlifeRig.pose hook, after every other pose layer and before the matrices are composed: the gait's body motion
    * (crouch, bob, roll, spine flex, head nod) and the legs (stance and swing paths, IK onto the paw tips). Returns the
    * root's extra height. With no tracked gait ({@code in.gait} false, e.g. offline tools) the vanilla walk cycle drives it.
    */
   public static float apply(SkinnedMesh m, WildlifeRig.Input in, float[] rx, float[] ry, float[] rz, float dyBefore) {
      if (m.bird) {
         return 0.0F;
      }
      Info info = info(m);
      if (!info.ok) {
         return 0.0F;
      }
      Style st = style(in.gaitStyle);
      float v, g, phase;
      if (in.gait) {
         v = in.gaitSpeed;
         g = in.gaitMix;
         phase = in.gaitPhase;
      } else {
         v = Math.min(1.0F, in.amount) * 0.25F * (1.0F + in.w[WildlifeRig.FLEE]);
         g = st.mix(froude(info, v));
         phase = frac(in.limbSwing * 0.25F / stride(info, st, Math.max(v, 0.004F), g));
      }
      float po = in.eco[com.formaworks.frontierhunts.ecology.client.HuntPose.POUNCE];
      // wp: paws planted on the ground (standing, grazing, stalking: the sculpts' rest and grazing poses alone left
      // some paws in the soil, e.g. the grizzly's fore paws 0.2 below while foraging); w: the gait itself
      float wp = (1.0F - in.w[WildlifeRig.REST]) * (1.0F - in.air) * (1.0F - po);
      if (wp < 0.004F) {
         tailGuard(m, info, rx, ry, rz, -dyBefore, (1.0F - in.air) * (1.0F - po), SCRATCH.get().p);
         return 0.0F;
      }
      float w = smooth((v - 0.002F) / 0.012F) * wp;
      float h = info.h;
      float lambda = stride(info, st, Math.max(v, 0.004F), g);
      float duty = duty(info, st, lambda, g);
      float L = Math.min(duty * lambda, sweepCap(info, st, g)) * w;
      float gal = Mth.clamp(g - 1.0F, 0.0F, 1.0F);
      float trot = Math.min(1.0F, g) * (1.0F - gal);
      float th = Mth.TWO_PI * phase;
      Scratch sc = SCRATCH.get();
      float[] base = sc.base;
      // paws as every other layer left them (standing, grazing, stalking...): the gait moves them from there
      for (int l = 0; l < 4; l++) {
         paw(m, info, l, rx, ry, rz, sc.p);
         base[l * 3] = sc.p[0];
         base[l * 3 + 1] = sc.p[1];
         base[l * 3 + 2] = sc.p[2];
      }
      // ---- body: crouch, bob, roll and sway, spine flex in the gallop, head nod
      // (not on top of a body already lowered by another layer: a stalking cat's hocks would go into the ground)
      float dy = -Math.max(0.0F, crouch(st, g) * h * w + Math.min(0.0F, dyBefore));
      float bob = (0.012F * (1.0F - trot - gal) + 0.02F * trot) * h * w * 0.5F * (1.0F - Mth.cos(2.0F * th));
      bob += 0.045F * h * w * gal * 0.5F * (1.0F + Mth.sin(th - 1.2F));
      dy -= bob;
      float slow = 1.0F - gal;
      float hindPh = Mth.TWO_PI * st.offset(2, g);
      float roll = st.roll * w * slow * Mth.sin(th - hindPh);
      rz[info.pelvis] += roll;
      if (info.spine >= 0) {
         rz[info.spine] -= 0.6F * roll;
         ry[info.spine] += st.sway * w * slow * Mth.sin(th - hindPh + 0.4F);
      }
      if (info.neck >= 0) {
         ry[info.neck] -= st.sway * 0.8F * w * slow * Mth.sin(th - hindPh + 0.4F);
         rx[info.neck] += st.nod * w * slow * Mth.sin(2.0F * th - 0.6F) - 0.06F * w * (style(CAT) == st ? 1.0F : 0.0F);
      }
      if (gal > 0.0F) {
         // the tail streams out behind as a counterbalance (cats and the cheetah most)
         if (info.tail >= 0) {
            rx[info.tail] -= (st.flex > 0.15F ? 0.55F : 0.3F) * w * gal + 0.1F * w * gal * Mth.sin(th);
         }
         float flex = st.flex * w * gal;
         if (info.spine >= 0) {
            rx[info.spine] += flex * Mth.sin(th - 0.5F);
         }
         if (info.chest >= 0) {
            rx[info.chest] -= 0.4F * flex * Mth.sin(th - 0.5F);
         }
         if (info.neck >= 0) {
            rx[info.neck] -= 0.45F * flex * Mth.sin(th - 0.5F) + 0.12F * w * gal;
         }
      }
      // ---- legs: stance (planted: moves back exactly as far as the body goes forward) and swing (lifted, reaching)
      float liftMax = Math.min(st.lift * h * (1.0F + 0.35F * gal), 0.45F * L + 0.01F * h);
      for (int l = 0; l < 4; l++) {
         float p = frac(phase + st.offset(l, g));
         float dz, lift = 0.0F, curl = 0.0F, heel = 0.0F;
         if (p < duty) {
            float s = p / duty;
            dz = L * (s - 0.5F);
            heel = smooth((s - 0.72F) / 0.28F);
         } else {
            float s = (p - duty) / (1.0F - duty);
            // Hermite path from behind to in front: it leaves and lands still moving back (half the ground's speed),
            // so the paw neither drags forward off the ground nor lands sliding
            float m0 = SWING_M0 * L * (1.0F - duty) / duty;
            float s2 = s * s, s3 = s2 * s;
            dz = (2.0F * s3 - 3.0F * s2 + 1.0F) * 0.5F * L + (s3 - 2.0F * s2 + s) * m0 - (-2.0F * s3 + 3.0F * s2) * 0.5F * L + (s3 - s2) * m0;
            dz = Mth.clamp(dz, -SWING_OVER * L, SWING_OVER * L); // a short reach past the stance (then drawn back onto it)
            float sp = Mth.sin(Mth.PI * s);
            // peaks a little early (lift-off is brisk); clear of the ground right to the end of the reach, then
            // set straight down (a paw skimming the soil while it still swings reads as sliding)
            lift = liftMax * Mth.sqrt(Math.max(0.0F, sp)) * (1.0F + 0.25F * Mth.cos(Mth.PI * s));
            curl = sp;
         }
         int[] b = info.legs[l];
         float k = Math.min(1.0F, L / (0.35F * h));
         // swing: the wrist / hock folds the paw up and back; toe-off: the pastern tilts while the pads stay flat
         if (l < 2) {
            rx[b[2]] += (st.curlF * curl - 0.3F * heel) * k;
         } else {
            rx[b[2]] += (st.curlH * curl - 0.25F * heel) * k;
         }
         // the pads lie flat on the ground (the toe turned back by every turn above it; the IK below keeps that
         // sum), curling only in the swing: no paw edge digging into the soil
         float above = 0.0F;
         for (int bone = m.parent[b[3]], guard = 0; bone >= 0 && guard < 32; bone = m.parent[bone], guard++) {
            above += rx[bone];
         }
         float flat = info.toeRest[l] - above + (l < 2 ? 0.25F : 0.2F) * curl * k;
         rx[b[3]] += (flat - rx[b[3]]) * wp;
         // planted on the ground (the sole's own rest height), blended in with the gait so lying down or a leap is untouched
         float ground = info.soleUp[l] - dyBefore - dy;
         float ty = Mth.lerp(wp, base[l * 3 + 1] - dy, ground) + lift;
         float bz = base[l * 3 + 2] + w * info.centre(in.gaitStyle < 0 || in.gaitStyle >= STYLES.length ? HOOF : in.gaitStyle, l, g);
         solve(m, info, l, rx, ry, rz, ty, bz, dz, L, sc.p);
         sc.leg[l * 3] = ty;
         sc.leg[l * 3 + 1] = bz;
         sc.leg[l * 3 + 2] = dz;
         // contact guard: no part of the paw (heel, toes, sides) under the ground; lift it by what dips in and solve again
         float under = -dyBefore - dy - lowest(m, info.guard[l], rx, ry, rz, sc.p);
         if (under > GUARD_MIN) {
            solve(m, info, l, rx, ry, rz, ty + under * wp, bz, dz, L, sc.p);
         }
         if (DEBUG != null) {
            paw(m, info, l, rx, ry, rz, sc.p);
            DEBUG[l * 4] = ty;
            DEBUG[l * 4 + 1] = lastTz;
            DEBUG[l * 4 + 2] = sc.p[1];
            DEBUG[l * 4 + 3] = sc.p[2];
         }
      }
      // joint guard: a deep crouch (stalking) must not push a hock or a wrist into the ground: raise the body by what
      // dips in, the planted paws staying where they are (the legs solved again for the higher body)
      float dip = 0.0F;
      for (int l = 0; l < 4; l++) {
         dip = Math.max(dip, -dyBefore - dy + 0.01F * h - lowest(m, info.joints[l], rx, ry, rz, sc.p));
      }
      if (dip > GUARD_MIN && wp > 0.0F) {
         dip = Math.min(dip, 0.25F * h) * wp;
         dy += dip;
         for (int l = 0; l < 4; l++) {
            solve(m, info, l, rx, ry, rz, sc.leg[l * 3] - dip, sc.leg[l * 3 + 1], sc.leg[l * 3 + 2], L, sc.p);
            float under = -dyBefore - dy - lowest(m, info.guard[l], rx, ry, rz, sc.p);
            if (under > GUARD_MIN) {
               solve(m, info, l, rx, ry, rz, sc.leg[l * 3] - dip + under, sc.leg[l * 3 + 1], sc.leg[l * 3 + 2], L, sc.p);
            }
         }
      }
      tailGuard(m, info, rx, ry, rz, -dyBefore - dy, (1.0F - in.air) * (1.0F - po), sc.p);
      return dy;
   }

   /**
    * Keeps the tail above the ground (model height {@code groundY}): a long cat tail hangs nearly to the ground when the
    * animal stands, and a stalking crouch or a sitting howl would otherwise bury its tip. Raises tail1, then tail2, by
    * just what is needed (the turn's effect measured, so it works whichever way the sculpt's tail axis runs).
    */
   static void tailGuard(SkinnedMesh m, Info info, float[] rx, float[] ry, float[] rz, float groundY, float k, float[] tmp) {
      if (info.tailPts.length == 0 || k <= 0.0F) {
         return;
      }
      float clear = groundY + 0.012F * info.h;
      for (int pass = 0; pass < 3; pass++) {
         int bone = pass < 2 ? info.tail : info.tail2;
         if (bone < 0) {
            continue;
         }
         float lo = lowest(m, info.tailPts, rx, ry, rz, tmp);
         float need = clear - lo;
         if (need <= 0.001F) {
            return;
         }
         final float h = 0.05F;
         rx[bone] += h;
         float rate = (lowest(m, info.tailPts, rx, ry, rz, tmp) - lo) / h;
         rx[bone] -= h;
         if (Math.abs(rate) < 0.01F) {
            continue;
         }
         rx[bone] += Mth.clamp(need / rate, -0.8F, 0.8F) * Math.min(1.0F, k);
      }
   }

   /** posed sole point of leg {@code l} (skinned like the mesh vertex it is): (x, y, z) in model space, before the root offset */
   static void paw(SkinnedMesh m, Info info, int l, float[] rx, float[] ry, float[] rz, float[] out) {
      float[] sp = info.solePos[l];
      int[] sb = info.soleBone[l];
      float[] sw = info.soleW[l];
      float ox = 0.0F, oy = 0.0F, oz = 0.0F;
      float[] q = out;
      for (int k = 0; k < 4; k++) {
         float w = sw[k];
         if (w <= 0.0F) {
            continue;
         }
         point(m, sb[k], sp[0], sp[1], sp[2], rx, ry, rz, q);
         ox += w * q[0];
         oy += w * q[1];
         oz += w * q[2];
      }
      out[0] = ox;
      out[1] = oy;
      out[2] = oz;
   }

   /** a bind-space point carried by {@code bone} and its parents (same per-bone transform as WildlifeRig) */
   static void point(SkinnedMesh m, int bone, float px, float py, float pz, float[] rx, float[] ry, float[] rz, float[] out) {
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
      out[0] = px;
      out[1] = py;
      out[2] = pz;
   }

   private static float angle(float d) {
      return Mth.wrapDegrees(d * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
   }

   /**
    * Two-bone IK in the leg's plane (upper and lower joint; the foot counter-turned so the pastern and paw keep their
    * angle): analytic, the knee/elbow/hock always bending the way the sculpt bends it; then a Gauss-Newton polish for
    * the small out-of-plane part (splayed or rolled legs).
    */
   static void solve(SkinnedMesh m, Info info, int l, float[] rx, float[] ry, float[] rz, float ty, float baseZ, float dz0, float L, float[] p) {
      int up = info.legs[l][0], low = info.legs[l][1], foot = info.legs[l][2];
      float[] j = m.joint;
      point(m, m.parent[up], j[up * 3], j[up * 3 + 1], j[up * 3 + 2], rx, ry, rz, p);
      float hy = p[1], hz = p[2];
      point(m, up, j[low * 3], j[low * 3 + 1], j[low * 3 + 2], rx, ry, rz, p);
      float ky = p[1], kz = p[2];
      point(m, low, j[foot * 3], j[foot * 3 + 1], j[foot * 3 + 2], rx, ry, rz, p);
      float fy = p[1], fz = p[2];
      paw(m, info, l, rx, ry, rz, p);
      // the ankle has to go where the sole goes, less the (unchanging) ankle-to-sole offset
      float ey0 = p[1] - fy, ez0 = p[2] - fz;
      float a = Mth.sqrt((ky - hy) * (ky - hy) + (kz - hz) * (kz - hz));
      float b = Mth.sqrt((fy - ky) * (fy - ky) + (fz - kz) * (fz - kz));
      // where this paw can be at this height: the path never leaves that span (no snapping at full stretch)
      float tz = baseZ + dz0;
      float r = a + b - 0.002F, vy = ty - ey0 - hy;
      if (Math.abs(vy) < r) {
         float half = Mth.sqrt(r * r - vy * vy);
         float zlo = hz + ez0 - half, zhi = hz + ez0 + half;
         tz = Mth.clamp(tz, zlo, zhi);
      }
      lastTz = tz;
      float ay = ty - ey0, az = tz - ez0;
      float dy = ay - hy, dz = az - hz;
      float d = Mth.sqrt(dy * dy + dz * dz);
      if (a > 1.0E-4F && b > 1.0E-4F && d > 1.0E-4F) {
         float dc = Mth.clamp(d, Math.abs(a - b) + 1.0E-3F, a + b - 1.0E-3F);
         float alpha = (float)Math.acos(Mth.clamp((a * a + dc * dc - b * b) / (2.0F * a * dc), -1.0F, 1.0F));
         float beta = (float)Math.atan2(dz, dy);
         // keep the bend on the sculpt's side of the hip-ankle line
         float phi = beta + info.bend[l] * alpha;
         float nky = hy + a * Mth.cos(phi), nkz = hz + a * Mth.sin(phi);
         float u = angle(phi - (float)Math.atan2(kz - hz, ky - hy));
         float cur2 = (float)Math.atan2(fz - kz, fy - ky);
         float want2 = (float)Math.atan2(hz + dz * dc / d - nkz, hy + dy * dc / d - nky);
         float lo = angle(want2 - cur2 - u);
         if (Float.isFinite(u) && Float.isFinite(lo)) {
            rx[up] += u;
            rx[low] += lo;
            rx[foot] -= u + lo;
         }
      }
      // polish (out-of-plane splay and roll)
      for (int it = 0; it < 3; it++) {
         paw(m, info, l, rx, ry, rz, p);
         float ey = ty - p[1], ez = tz - p[2];
         if (Math.abs(ey) + Math.abs(ez) < 0.0015F || !Float.isFinite(ey) || !Float.isFinite(ez)) {
            return;
         }
         float y0 = p[1], z0 = p[2];
         final float h = 0.01F;
         rx[up] += h;
         rx[foot] -= h;
         paw(m, info, l, rx, ry, rz, p);
         float a11 = (p[1] - y0) / h, a21 = (p[2] - z0) / h;
         rx[up] -= h;
         rx[foot] += h;
         rx[low] += h;
         rx[foot] -= h;
         paw(m, info, l, rx, ry, rz, p);
         float a12 = (p[1] - y0) / h, a22 = (p[2] - z0) / h;
         rx[low] -= h;
         rx[foot] += h;
         float lam = 0.002F + 0.02F * (a11 * a11 + a21 * a21 + a12 * a12 + a22 * a22);
         float j11 = a11 * a11 + a21 * a21 + lam, j12 = a11 * a12 + a21 * a22, j22 = a12 * a12 + a22 * a22 + lam;
         float g1 = a11 * ey + a21 * ez, g2 = a12 * ey + a22 * ez;
         float det = j11 * j22 - j12 * j12;
         if (!(Math.abs(det) > 1.0E-9F)) {
            return;
         }
         float du = Mth.clamp((j22 * g1 - j12 * g2) / det, -0.15F, 0.15F);
         float dl = Mth.clamp((j11 * g2 - j12 * g1) / det, -0.15F, 0.15F);
         rx[up] += du;
         rx[low] += dl;
         rx[foot] -= du + dl;
         paw(m, info, l, rx, ry, rz, p);
         float ny = ty - p[1], nz = tz - p[2];
         if (!(ny * ny + nz * nz < ey * ey + ez * ez)) {
            // no better (at full stretch): keep the analytic answer
            rx[up] -= du;
            rx[low] -= dl;
            rx[foot] += du + dl;
            return;
         }
      }
   }
}
