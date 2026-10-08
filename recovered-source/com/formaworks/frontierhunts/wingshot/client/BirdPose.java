package com.formaworks.frontierhunts.wingshot.client;

import com.formaworks.frontierhunts.wildlife2026.client.SkinnedMesh;
import com.formaworks.frontierhunts.wildlife2026.client.WildlifeRig;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * [wingshot] Bird poses on both looks. Ultra (skinned mesh): neck stretched out in flight, feet tucked back under the
 * tail or dropped forward to land, tail pitched and fanned, a limp head and dangling feet when shot, the upright,
 * braced drumming stance; the spread wings themselves are {@link WingGeometry}. Vanilla (box model): two-part wings
 * (inner plate + hand) driven by the same stroke, fold, cup and twist, tucked legs, tail.
 */
public final class BirdPose {
   private BirdPose() {
   }

   private static void add(SkinnedMesh m, float[] rx, float[] ry, float[] rz, String bone, float x, float y, float z) {
      int i = m.bone(bone);
      if (i >= 0) {
         rx[i] += x;
         ry[i] += y;
         rz[i] += z;
      }
   }

   /**
    * Replaces the generic bird branch of WildlifeRig.pose for ducks and grouse. Returns the body drop (dy). Walking
    * gait on the ground (fading out in the air), paddling on water, then the flight / limp / drum pose.
    */
   public static float rig(SkinnedMesh m, WildlifeRig.Input in, float[] rx, float[] ry, float[] rz) {
      BirdAnim.State s = in.bird;
      float H = m.meta[0];
      float a = Math.min(1.0F, in.amount * 1.4F);
      float phi = in.limbSwing * m.meta[5];
      float dy = 0.0F;
      float air = s.wingsOut || BirdAnim.flying(s.phase) ? 1.0F : 0.0F;
      float ground = (1.0F - air) * (1.0F - s.limp);
      // ---- walking (the existing bird gait), only on the ground
      float A = 0.7F * a * ground;
      add(m, rx, ry, rz, "l_upper", A * Mth.sin(phi), 0, 0);
      add(m, rx, ry, rz, "r_upper", -A * Mth.sin(phi), 0, 0);
      add(m, rx, ry, rz, "l_foot", -0.6F * a * ground * Math.max(0.0F, Mth.cos(phi)), 0, 0);
      add(m, rx, ry, rz, "r_foot", -0.6F * a * ground * Math.max(0.0F, -Mth.cos(phi)), 0, 0);
      add(m, rx, ry, rz, "neck", 0.12F * a * ground * Mth.sin(2.0F * phi), 0, 0);
      if (in.water && s.limp < 0.5F) {
         dy -= 0.12F * H;
         add(m, rx, ry, rz, "l_upper", 0.5F * Mth.sin(in.age * 0.3F), 0, 0);
         add(m, rx, ry, rz, "r_upper", -0.5F * Mth.sin(in.age * 0.3F), 0, 0);
      }
      // ---- flight: neck out, feet tucked or dropped, tail
      float fl = Math.max(air, s.drum) * (1.0F - s.limp);
      float neckOut = s.neck * (1.0F - s.limp);
      float nk = s.grouse ? 0.35F : 0.85F;
      // flight posture: chest level, neck stretched out ahead, bill level
      add(m, rx, ry, rz, "chest", (s.grouse ? -0.12F : -0.22F) * neckOut, 0, 0);
      add(m, rx, ry, rz, "neck", -nk * neckOut, 0, 0);
      add(m, rx, ry, rz, "head", (nk + (s.grouse ? 0.12F : 0.22F)) * 0.9F * neckOut, 0, 0);
      float tuck = air * (1.0F - s.feet) * (1.0F - s.limp);
      float drop = s.feet * air * (1.0F - s.limp);
      for (String side : SIDES) {
         // tucked: legs swung right back along the belly, toes folded; dropped: forward and down for the splash
         add(m, rx, ry, rz, side + "_upper", -1.45F * tuck + 0.55F * drop, 0, 0);
         add(m, rx, ry, rz, side + "_lower", 0.12F * tuck - 0.35F * drop, 0, 0);
         add(m, rx, ry, rz, side + "_foot", 1.25F * tuck - 0.55F * drop, 0, 0);
      }
      // feet spread a little for the splash-down
      add(m, rx, ry, rz, "l_upper", 0, 0, -0.18F * drop);
      add(m, rx, ry, rz, "r_upper", 0, 0, 0.18F * drop);
      add(m, rx, ry, rz, "tail1", s.tailPitch * Math.max(fl, s.drum), 0.0F, 0.0F);
      add(m, rx, ry, rz, "tail2", 0.4F * s.tailPitch * Math.max(fl, s.drum), 0.0F, 0.0F);
      // the folded sculpted wings tuck a little closer while the feathered wings are out (they become the flank)
      add(m, rx, ry, rz, "wing_l", 0, 0, 0.05F * air);
      add(m, rx, ry, rz, "wing_r", 0, 0, -0.05F * air);
      // ---- drumming: upright, ruff and neck up, tail braced down on the log
      if (s.drum > 0.01F) {
         add(m, rx, ry, rz, "neck", 0.35F * s.drum, 0, 0);
         add(m, rx, ry, rz, "head", -0.25F * s.drum, 0, 0);
         add(m, rx, ry, rz, "l_upper", -0.5F * s.drum, 0, 0);
         add(m, rx, ry, rz, "r_upper", -0.5F * s.drum, 0, 0);
         add(m, rx, ry, rz, "l_foot", 0.5F * s.drum, 0, 0);
         add(m, rx, ry, rz, "r_foot", 0.5F * s.drum, 0, 0);
         // every beat jerks the body a little
         add(m, rx, ry, rz, "chest", 0.06F * s.drumPulse, 0, 0);
      }
      // ---- shot: head and neck limp, feet dangling, a little twitch at first
      if (s.limp > 0.01F) {
         float L = s.limp;
         float since = s.sinceDeath();
         float sway = s.down ? 0.0F : Mth.sin(in.age * 0.9F) * 0.35F;
         float twitch = since < 30.0F && s.down ? Mth.sin(since * 2.2F) * Math.max(0.0F, 1.0F - since / 30.0F) * 0.25F : 0.0F;
         add(m, rx, ry, rz, "neck", -0.9F * L, 0.45F * L * (s.down ? 1.0F : sway), 0.3F * L);
         add(m, rx, ry, rz, "head", -0.7F * L, 0.5F * L * sway, 0.5F * L);
         for (String side : SIDES) {
            add(m, rx, ry, rz, side + "_upper", (s.down ? 0.9F : 0.4F) * L + twitch, 0, 0);
            add(m, rx, ry, rz, side + "_lower", -0.4F * L, 0, 0);
            add(m, rx, ry, rz, side + "_foot", 0.8F * L, 0, 0);
         }
         add(m, rx, ry, rz, "tail1", -0.15F * L, 0.2F * L, 0);
      }
      return dy;
   }

   private static final String[] SIDES = {"l", "r"};

   /** After the skin matrices are built: fan the tail (scale the tail bones sideways about their joints). */
   public static void post(SkinnedMesh m, WildlifeRig.Input in, float[] out) {
      BirdAnim.State s = in.bird;
      float k = 1.0F + 0.75F * s.tail * (1.0F - s.limp * 0.5F);
      if (k > 1.001F) {
         scaleX(m, out, m.bone("tail1"), 1.0F + (k - 1.0F) * 0.6F);
         scaleX(m, out, m.bone("tail2"), k);
      }
   }

   /** M' = M * T(j) * S(sx, 1, 1) * T(-j) */
   private static void scaleX(SkinnedMesh m, float[] out, int b, float sx) {
      if (b < 0) {
         return;
      }
      int o = b * 12;
      float jx = m.joint[b * 3];
      for (int r = 0; r < 3; r++) {
         float a0 = out[o + r * 4];
         out[o + r * 4] = a0 * sx;
         out[o + r * 4 + 3] += a0 * (jx - sx * jx);
      }
   }

   // ================================================================ Vanilla box model

   /** Box-model birds: two-part wings, legs, tail, head; the body attitude comes from {@link BirdRender}. */
   public static void classic(BirdAnim.State s, float age, ModelPart body, ModelPart head, ModelPart wingL, ModelPart wingR, ModelPart tipL, ModelPart tipR,
      ModelPart legL, ModelPart legR, ModelPart tail) {
      float spread = BirdAnim.smooth(Math.min(1.0F, Math.max(0.0F, s.spread)));
      // the plate hangs down the flank at 0 and is horizontal at pi/2
      float z = Mth.HALF_PI + s.elev;
      wingL.zRot = z;
      wingR.zRot = -z;
      wingL.yRot = -s.sweep * 0.8F * spread;
      wingR.yRot = s.sweep * 0.8F * spread;
      wingL.xRot = -s.twist * 0.5F * spread;
      wingR.xRot = -s.twist * 0.5F * spread;
      boolean tips = tipL != null && tipR != null;
      if (tips) {
         boolean show = spread > 0.25F;
         tipL.visible = show;
         tipR.visible = show;
         // the hand: bends up under load, folds back on the upstroke, droops when cupped
         float bend = (0.15F - 0.35F * s.flex) - 0.7F * s.cup;
         tipL.zRot = bend;
         tipR.zRot = -bend;
         tipL.yRot = -0.9F * s.flex;
         tipR.yRot = 0.9F * s.flex;
      }
      boolean air = s.wingsOut || BirdAnim.flying(s.phase);
      float tuck = (air ? 1.0F - s.feet : 0.0F) * (1.0F - s.limp);
      float drop = (air ? s.feet : 0.0F) * (1.0F - s.limp);
      legL.xRot += 1.3F * tuck - 0.5F * drop + 0.9F * s.limp;
      legR.xRot += 1.3F * tuck - 0.5F * drop + 0.9F * s.limp;
      if (air || s.limp > 0.5F) {
         legL.visible = true;
         legR.visible = true;
      }
      head.xRot += -0.35F * s.neck * (1.0F - s.limp) + 1.0F * s.limp - 0.3F * s.drum;
      head.zRot += 0.5F * s.limp;
      if (tail != null) {
         tail.xRot += s.tailPitch + 0.4F * (air ? 1.0F : 0.0F) * (1.0F - s.limp);
         tail.xScale = 1.0F + 0.7F * s.tail;
      }
   }
}
