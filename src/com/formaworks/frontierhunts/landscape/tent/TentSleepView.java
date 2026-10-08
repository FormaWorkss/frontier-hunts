package com.formaworks.frontierhunts.landscape.tent;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Camera while asleep in a shelter. Vanilla puts the eye 0.5 above the sleeper's live position looking level
 * at the foot of the bed; in a tent that is behind the fabric, and on the local client the live position has
 * sunk to the floor (see {@link TentSleepPose}), which buried the old view in the ground. Here the eye sits just
 * over the face of the body drawn on the pad, and looks up at the tent ceiling, tilted towards the feet and the
 * door. Everything is derived from the bed block, never from the live position.
 */
public final class TentSleepView {
   private TentSleepView() {
   }

   public record View(Vec3 eye, float yaw, float pitch) {
   }

   /** First-person view for a sleeper in a tent, or null to leave the vanilla camera alone. */
   public static View firstPerson(LivingEntity sleeper) {
      TentSleepPose.Pose pose = TentSleepPose.of(sleeper);
      if (pose == null || !pose.shelter()) {
         return null;
      }

      return new View(eye(pose), pose.head().toYRot() - 180.0F, -TentSleepPose.LOOK_UP);
   }

   /** Orbit centre for the third-person camera: the sleeper's face on the pad, not the sunken live position. */
   public static Vec3 thirdPersonPivot(LivingEntity sleeper) {
      TentSleepPose.Pose pose = TentSleepPose.of(sleeper);
      return pose == null ? null : eye(pose);
   }

   private static Vec3 eye(TentSleepPose.Pose pose) {
      Direction h = pose.head();
      return pose.origin()
         .add(h.getStepX() * TentSleepPose.EYE_FORWARD, TentSleepPose.EYE_UP, h.getStepZ() * TentSleepPose.EYE_FORWARD);
   }
}
