package com.formaworks.frontierhunts.hunting.rutfight;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * [rutfight] Per-frame head target of one locked fighter: the canonical locked head, lifted to the shared contact
 * height, turned about the contact point (twist and heave - the rival turns about the same point, so the pair moves
 * as one piece and the racks keep touching), raised with the rival (clash jolt, a neck that could not reach), and slid along the axis by half of whatever the actual body distance differs from
 * the fitted one (network interpolation, a server distance sized for another preset). What the neck cannot reach the
 * renderer takes up by sliding the body (see {@code DeerAnimator.fightResidual}).
 */
public final class FightAim {
   private FightAim() {
   }

   /** Twist / heave amplitude for a pair: a rack whose contact sits far in front of the head (moose palms, elk beams)
    * swings the head a long way for a small angle, so it twists less. */
   public static float twistScale(FightFit fit) {
      return Math.max(0.25F, Math.min(1.0F, 0.38F / Math.max(0.05F, fit.lever)));
   }

   /**
    * @param a          true for fighter A of the fit (B mirrors twist and heave)
    * @param distance   actual feet-to-feet horizontal distance this frame
    * @param groundDy   B's feet height minus A's this frame
    * @param roll       twist about the shared axis (radians, A's sense)
    * @param heave      seesaw about the shared contact (radians, A's sense)
    * @param raise      extra height of this rack (entity blocks): the pair's clash jolt, plus whatever the rival's neck
    *                   fell short by on the last frame so the two racks stay level
    * @param out        receives the target (model space)
    */
   public static void target(FightModel m, FightFit fit, boolean a, float distance, float groundDy, float roll, float heave, float raise, Matrix4f out) {
      float lift = (a ? fit.liftA : fit.liftB) + raise;
      float side = a ? fit.sideA : fit.sideB;
      Vector3f c = a ? new Vector3f(fit.contact) : new Vector3f(-fit.contact.x, fit.contact.y - groundDy, fit.distance - fit.contact.z);
      float slide = (distance - fit.distance) * 0.5F;
      float sign = a ? 1.0F : -1.0F;
      c.y += raise;
      Vector3f p = m.toModel(c, new Vector3f());
      Vector3f up = m.toModel(new Vector3f(side, lift, 0.0F), new Vector3f());
      Vector3f fwd = m.toModel(new Vector3f(0.0F, 0.0F, slide), new Vector3f());
      out.identity()
         .translate(fwd)
         .translate(p)
         .rotateZ(sign * roll)
         .rotateX(sign * heave)
         .translate(-p.x, -p.y, -p.z)
         .translate(up)
         .mul(m.head);
   }
}
