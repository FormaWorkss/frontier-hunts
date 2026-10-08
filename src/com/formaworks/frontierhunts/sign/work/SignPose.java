package com.formaworks.frontierhunts.sign.work;

import com.formaworks.frontierhunts.hunting.DeerAnimator;
import com.formaworks.frontierhunts.hunting.DeerSkeleton;
import com.formaworks.frontierhunts.hunting.Whitetail;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * [deersign] The body language of a buck working sign, as animator input (common code: the server's hit-surface animator
 * gets the same posture, the client adds the exact head target and contact slide on top, see SignWorkClient).
 *
 * <ul>
 *   <li>Rub: the locked-antler posture (head low, forehead and rack forward) is the base; the client moves the head
 *   along the trunk in strokes.</li>
 *   <li>Smelling the tree / the scrape spot: head lowered (graze clip at part weight).</li>
 *   <li>Pawing: the working front leg lifts, folds at the knee, reaches forward, strikes and drags back through the
 *   litter while the head stays low over the spot.</li>
 *   <li>Licking branch: the head goes up (client head target to the branch tip).</li>
 *   <li>Rub-urination: hind legs drawn under the body with the hocks together, rump a little lower, hocks rubbing.</li>
 * </ul>
 */
public final class SignPose {
   private SignPose() {
   }

   /** Called at the end of Whitetail#animatorInput (both sides). */
   public static void input(Whitetail deer, DeerAnimator.Input in) {
      in.signPhase = 0;
      in.signW = 0.0F;
      in.signPitch = 0.0F;
      in.signYaw = 0.0F;
      in.signDrop = 0.0F;
      SignAct act = SignWork.act(deer);
      if (!act.active() || in.downed || in.bedded) {
         return;
      }
      float tick = (float)(deer.level().getGameTime() - act.start());
      int[] ph = act.at(tick);
      if (ph[0] == 0) {
         return;
      }
      float total = act.total() / 20.0F;
      float t = tick / 20.0F;
      in.signPhase = ph[0];
      in.signAge = ph[1] / 20.0F;
      in.signDur = ph[2] / 20.0F;
      in.signSeed = act.seed();
      in.signW = SignMotion.envelope(t, total, 0.35F, 0.45F);
      apply(in);
   }

   /** Posture weights for the current sign phase (fields signPhase / signW already set). */
   public static void apply(DeerAnimator.Input in) {
      float w = in.signW;
      if (w <= 0.0F || in.signPhase == 0) {
         return;
      }
      switch (in.signPhase) {
         case SignAct.SNIFF_TREE -> in.fightLower = Math.max(in.fightLower, 0.75F * w);
         case SignAct.RUB_STROKES -> in.fightLower = Math.max(in.fightLower, w);
         case SignAct.SNIFF_GROUND -> in.graze = Math.max(in.graze, w);
         case SignAct.PAW -> in.graze = Math.max(in.graze, 0.75F * w);
         case SignAct.URINATE -> in.graze = Math.max(in.graze, 0.15F * w);
         default -> in.graze = 0.0F; // licking branch: head up, never grazing
      }
      if (in.signPhase != SignAct.SNIFF_GROUND && in.signPhase != SignAct.PAW && in.signPhase != SignAct.URINATE) {
         in.graze = Math.min(in.graze, 1.0F - w);
      }
      in.cueHold = false;
   }

   // ------------------------------------------------------------------------------------------------ skeletal legs

   private static final Vector3f AXIS = new Vector3f();

   /**
    * Pawing / rub-urination legs on the skeletal rig. Called by DeerAnimator before its final pose; edits the local
    * rotations {@code q} (re-posing {@code model} for the axes).
    */
   public static void legs(DeerSkeleton sk, float[] t, float[] q, Matrix4f[] model, DeerAnimator.Input in) {
      if (in.signW <= 0.001F || in.downed || in.bedded) {
         return;
      }
      if (in.signPhase == SignAct.PAW) {
         float[] m = new float[4];
         SignMotion.paw(in.signSeed, in.signAge, in.signDur, m);
         float a = m[SignMotion.PAW_AMP] * in.signW;
         if (a <= 0.001F) {
            return;
         }
         float[] leg = new float[4];
         SignMotion.pawLeg(m[SignMotion.PHASE], leg);
         int[] chain = sk.legs[m[SignMotion.FOOT] < 0.5F ? 0 : 1];
         sk.pose(t, q, null, model);
         // shoulder swings the leg, knee (carpus) folds the cannon back, fetlock curls the hoof
         // (a positive rotation about the rig's x axis swings a limb back)
         rotate(sk, q, model, chain[0], 1.0F, 0.0F, 0.0F, -leg[0] * a);
         sk.pose(t, q, null, model);
         rotate(sk, q, model, chain[2], 1.0F, 0.0F, 0.0F, leg[1] * a);
         sk.pose(t, q, null, model);
         rotate(sk, q, model, chain[3], 1.0F, 0.0F, 0.0F, leg[2] * a);
         // weight onto the other three legs: a slight lean away from the working leg
         sk.pose(t, q, null, model);
      } else if (in.signPhase == SignAct.URINATE) {
         float a = SignMotion.envelope(in.signAge, in.signDur, 0.6F, 0.6F) * in.signW;
         if (a <= 0.001F) {
            return;
         }
         float rub = SignMotion.hockRub(in.signAge) * 0.05F;
         sk.pose(t, q, null, model);
         float rest = (model[sk.legs[2][4]].m31() + model[sk.legs[3][4]].m31()) * 0.5F;
         for (int side = 0; side < 2; side++) {
            int[] chain = sk.legs[2 + side];
            sk.pose(t, q, null, model);
            float sx = model[chain[1]].m30() < 0.0F ? 1.0F : -1.0F; // toward the midline
            // thigh drawn forward under the body, hocks together, cannons splayed a little so the hooves stay apart
            rotate(sk, q, model, chain[1], 1.0F, 0.0F, 0.0F, HIND_FORWARD * a);
            sk.pose(t, q, null, model);
            rotate(sk, q, model, chain[1], 0.0F, 0.0F, 1.0F, sx * (HIND_IN + rub) * a);
            sk.pose(t, q, null, model);
            rotate(sk, q, model, chain[2], 1.0F, 0.0F, 0.0F, HIND_HOCK * a);
            sk.pose(t, q, null, model);
            rotate(sk, q, model, chain[3], 0.0F, 0.0F, 1.0F, -sx * HIND_SPLAY * a);
            sk.pose(t, q, null, model);
            rotate(sk, q, model, chain[3], 1.0F, 0.0F, 0.0F, HIND_CANNON * a);
         }
         sk.pose(t, q, null, model);
         // the rump drops by what the drawn-in hind legs lifted the hooves: they stay on the ground
         float rise = (model[sk.legs[2][4]].m31() + model[sk.legs[3][4]].m31()) * 0.5F - rest;
         int hip = sk.parents[sk.legs[2][0]];
         if (hip >= 0 && hip == sk.parents[sk.legs[3][0]] && sk.parents[hip] >= 0) {
            Vector3f d = new Matrix4f(model[sk.parents[hip]]).invert().transformDirection(new Vector3f(0.0F, -rise, 0.0F));
            t[hip * 3] += d.x;
            t[hip * 3 + 1] += d.y;
            t[hip * 3 + 2] += d.z;
         }
         sk.pose(t, q, null, model);
      }
   }

   /** Rub-urination stance (radians), tuned on the rigs offline so the hooves stay on the ground. */
   static final float HIND_FORWARD = -0.30F;
   static final float HIND_IN = 0.16F;
   static final float HIND_HOCK = 0.38F;
   static final float HIND_SPLAY = 0.10F;
   static final float HIND_CANNON = -0.12F;

   private static void rotate(DeerSkeleton sk, float[] q, Matrix4f[] model, int bone, float ax, float ay, float az, float angle) {
      if (bone < 0 || Math.abs(angle) < 1.0E-5F) {
         return;
      }
      int parent = sk.parents[bone];
      if (parent >= 0) {
         new Matrix4f(model[parent]).invert().transformDirection(AXIS.set(ax, ay, az)).normalize();
      } else {
         AXIS.set(ax, ay, az);
      }
      DeerSkeleton.preRotateLocal(q, bone, AXIS.x, AXIS.y, AXIS.z, angle);
   }

   // ------------------------------------------------------------------------------------------------ Vanilla box legs

   /**
    * Extra swing (radians, McAnimalPose leg pitch) of box leg {@code leg} (0, 1 front; 2, 3 hind) for pawing and
    * rub-urination.
    */
   public static float boxLeg(DeerAnimator.Input in, int leg) {
      if (in.signW <= 0.001F || in.downed || in.bedded) {
         return 0.0F;
      }
      if (in.signPhase == SignAct.PAW && leg < 2) {
         float[] m = new float[4];
         SignMotion.paw(in.signSeed, in.signAge, in.signDur, m);
         if ((m[SignMotion.FOOT] < 0.5F ? 0 : 1) != leg) {
            return 0.0F;
         }
         float[] l = new float[4];
         SignMotion.pawLeg(m[SignMotion.PHASE], l);
         // a rigid box leg: swing forward and lift (lift reads as more swing), then drag back past vertical
         return BOX_PAW * (l[0] + l[3] * 2.4F) * m[SignMotion.PAW_AMP] * in.signW;
      }
      if (in.signPhase == SignAct.URINATE && leg >= 2) {
         float a = SignMotion.envelope(in.signAge, in.signDur, 0.6F, 0.6F) * in.signW;
         return BOX_PAW * (0.38F + SignMotion.hockRub(in.signAge) * 0.04F) * a;
      }
      return 0.0F;
   }

   /** Sign of a forward swing in McAnimalPose leg pitch. */
   static final float BOX_PAW = 1.0F;
}
