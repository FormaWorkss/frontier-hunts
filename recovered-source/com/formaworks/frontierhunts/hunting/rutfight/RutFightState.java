package com.formaworks.frontierhunts.hunting.rutfight;

import com.formaworks.frontierhunts.hunting.DeerAnimator;
import com.formaworks.frontierhunts.hunting.Whitetail;

/**
 * [rutfight] Synced fight state as the animators see it. The server drives three extra rut postures through the
 * existing RUT_POSE byte (plus the rival's id in RUT_PARTNER and the clash time in the cue timestamp):
 * {@link #POSE_CLOSE} heads coming down while the two close in, {@link #POSE_LOCK} racks engaged, {@link #POSE_BREAK}
 * backed off a step between clashes. Common code: the server's hit-surface animator lowers the head too; the client
 * adds the exact head target on top (RutFightClient).
 */
public final class RutFightState {
   public static final int POSE_CLOSE = 4;
   public static final int POSE_LOCK = 5;
   public static final int POSE_BREAK = 6;
   /** Cue the server fires on both fighters at every clash (shared timestamp). Its own id (the animator maps no clip
    * to it), so a clash never replays the old spar clip (cue 7) once the fight is over. */
   public static final int CUE_CLASH = 8;

   private RutFightState() {
   }

   public static boolean fighting(int pose) {
      return pose >= POSE_CLOSE && pose <= POSE_BREAK;
   }

   /** Called at the end of Whitetail#animatorInput. */
   public static void input(Whitetail deer, DeerAnimator.Input in) {
      in.fightHead = null;
      in.fightReach = 0.0F;
      int pose = deer.rutPose();
      if (!fighting(pose) || in.downed || in.bedded) {
         in.fightLower = 0.0F;
         return;
      }

      in.fightLower = pose == POSE_LOCK ? 1.0F : (pose == POSE_BREAK ? 0.85F : 0.6F);
   }
}
