package com.formaworks.frontierhunts.hunting.rutfight;

import com.formaworks.frontierhunts.hunting.DeerSkeleton;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import java.util.EnumMap;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * [rutfight] The locked-antlers head posture and the head reach used to keep two racks in contact.
 *
 * <p>Base posture: the authored {@code spar} clip held on its lowest frame (head down, forehead and rack turned to the
 * rival). The clip's bobbing loop is not used for locked fights any more - a looping butt moves the rack back and forth,
 * which is exactly what opened the gap between the racks. On top of the held frame the client moves the head to an
 * exact target (contact height, distance error, twist) with a small CCD solve over the neck joints, so the rack lands
 * where the contact solver wants it on every frame.</p>
 *
 * <p>Common code (no client classes): the server uses {@link #canonical} for its realistic-mesh contact distance and its
 * hit surfaces pick up the lowered head through {@link #blendLock}.</p>
 */
public final class FightPose {
   /** Time in the spar clip of the held locked frame (frame 4.5 of 36 at 18 fps: lowest, steadiest head). */
   public static final float T_LOCK = 0.25F;
   private static final EnumMap<GameSpecies, float[][]> LOCK = new EnumMap<>(GameSpecies.class);
   private static final EnumMap<GameSpecies, Matrix4f[]> CANONICAL = new EnumMap<>(GameSpecies.class);

   private FightPose() {
   }

   /** Local translations / rotations of the held locked frame. */
   public static synchronized float[][] lockFrame(DeerSkeleton sk) {
      float[][] f = LOCK.get(sk.species);
      if (f == null) {
         int n = sk.count();
         float[] t = new float[n * 3];
         float[] q = new float[n * 4];
         DeerSkeleton.Clip spar = sk.clip("spar");
         if (spar != null) {
            sk.sample(spar, T_LOCK, t, q);
         } else {
            sk.rest(t, q);
         }

         f = new float[][]{t, q};
         LOCK.put(sk.species, f);
      }

      return f;
   }

   /** Model matrices of the canonical locked pose (standing still, held frame on every bone). */
   public static synchronized Matrix4f[] canonical(DeerSkeleton sk) {
      Matrix4f[] m = CANONICAL.get(sk.species);
      if (m == null) {
         float[][] f = lockFrame(sk);
         m = new Matrix4f[sk.count()];

         for (int i = 0; i < m.length; i++) {
            m[i] = new Matrix4f();
         }

         sk.pose(f[0].clone(), f[1].clone(), null, m);
         CANONICAL.put(sk.species, m);
      }

      return m;
   }

   /**
    * Blends the held locked frame in: the whole body while standing, only neck/head/ears/tail ({@code upper}) while
    * walking (shoving, backing), so the legs keep their gait and never slide.
    */
   public static void blendLock(DeerSkeleton sk, float[] t, float[] q, float w, float moveW, boolean[] upper) {
      if (w > 0.001F) {
         float[][] f = lockFrame(sk);
         // the ears keep their own (stand / alert) set: the spar frame pins them hard back, which swings the box
         // models' ear cubes clear of the head
         float[] ears = new float[8];
         System.arraycopy(q, sk.earL * 4, ears, 0, 4);
         System.arraycopy(q, sk.earR * 4, ears, 4, 4);
         float full = w * (1.0F - moveW);
         DeerSkeleton.blend(t, q, f[0], f[1], full, null);
         if (w > full + 1.0E-4F) {
            DeerSkeleton.blend(t, q, f[0], f[1], (w - full) / Math.max(1.0E-4F, 1.0F - full), upper);
         }

         System.arraycopy(ears, 0, q, sk.earL * 4, 4);
         System.arraycopy(ears, 4, q, sk.earR * 4, 4);
      }
   }

   /**
    * Moves the head bone to {@code target} (model space): neck joints bend (CCD, a few passes) to bring the head joint
    * to the target position, then the head takes the target orientation. {@code w} blends from the current pose.
    *
    * @param residual receives what the neck could not reach (target minus achieved head joint, model space); the
    *                 renderer slides the body by its horizontal part so the rack still lands on the target
    */
   public static void reach(DeerSkeleton sk, float[] t, float[] q, Matrix4f[] model, Matrix4f target, float w, Vector3f residual) {
      residual.zero();
      if (w <= 0.001F) {
         return;
      }

      int head = sk.head;
      Vector3f want = target.getTranslation(new Vector3f());
      Vector3f cur = model[head].getTranslation(new Vector3f());
      want.lerp(cur, 1.0F - w);
      Quaternionf wantRot = target.getUnnormalizedRotation(new Quaternionf()).normalize();
      if (w < 0.999F) {
         Quaternionf curRot = model[head].getUnnormalizedRotation(new Quaternionf()).normalize();
         wantRot = curRot.slerp(wantRot, w, new Quaternionf());
      }

      int[] chain = sk.neck2 >= 0 ? new int[]{sk.neck2, sk.neck1, sk.neck0} : new int[]{sk.neck1, sk.neck0};
      Vector3f joint = new Vector3f();
      Vector3f toEnd = new Vector3f();
      Vector3f toWant = new Vector3f();
      Quaternionf r = new Quaternionf();
      Quaternionf pr = new Quaternionf();

      for (int pass = 0; pass < 24; pass++) {
         model[head].getTranslation(cur);
         if (cur.distanceSquared(want) < 1.0E-7F) {
            break;
         }

         for (int j : chain) {
            model[j].getTranslation(joint);
            model[head].getTranslation(cur);
            cur.sub(joint, toEnd);
            want.sub(joint, toWant);
            if (toEnd.lengthSquared() < 1.0E-8F || toWant.lengthSquared() < 1.0E-8F) {
               continue;
            }

            r.rotationTo(toEnd.normalize(), toWant.normalize());
            float angle = r.angle();
            if (angle > 0.35F) {
               // keep each step small so the bend spreads along the neck instead of kinking one joint
               r.set(new Quaternionf().slerp(r, 0.35F / angle));
            }

            int parent = sk.parents[j];
            model[parent].getUnnormalizedRotation(pr).normalize();
            // local' = parentRot^-1 * r * parentRot * local
            Quaternionf local = new Quaternionf(q[j * 4], q[j * 4 + 1], q[j * 4 + 2], q[j * 4 + 3]);
            Quaternionf nl = new Quaternionf(pr).conjugate().mul(r).mul(pr).mul(local).normalize();
            q[j * 4] = nl.x;
            q[j * 4 + 1] = nl.y;
            q[j * 4 + 2] = nl.z;
            q[j * 4 + 3] = nl.w;
            sk.pose(t, q, null, model);
         }
      }

      // head orientation: local = parentRot^-1 * wanted model rotation
      int parent = sk.parents[head];
      model[parent].getUnnormalizedRotation(pr).normalize();
      Quaternionf hl = new Quaternionf(pr).conjugate().mul(wantRot).normalize();
      q[head * 4] = hl.x;
      q[head * 4 + 1] = hl.y;
      q[head * 4 + 2] = hl.z;
      q[head * 4 + 3] = hl.w;
      sk.pose(t, q, null, model);
      want.sub(model[head].getTranslation(cur), residual);
   }

   // ------------------------------------------------------------------ shared timing (deterministic on every client)

   /**
    * Head twist of a locked pair (radians, A's sense; B mirrors it). Bouts of wrenching to one side and the other with
    * quiet pressing in between; a pure function of the pair and the clock, so both animals - and every client - agree.
    */
   public static float twist(long pairSeed, float seconds, boolean sparring) {
      double s = seconds + (pairSeed & 1023L) * 0.37;
      double slow = Math.sin(s * 0.9) * 0.55 + Math.sin(s * 0.37 + (pairSeed >> 10 & 15L)) * 0.45;
      double bout = Math.max(0.0, Math.sin(s * 0.53 + (pairSeed >> 14 & 7L)));
      double wrench = Math.sin(s * 2.6 + (pairSeed >> 17 & 7L)) * bout * bout;
      float amp = sparring ? 0.16F : 0.3F;
      return (float)((slow * 0.45 + wrench * 0.55) * amp);
   }

   /** Up/down heave of the locked heads (radians about the shared contact, A's sense). */
   public static float heave(long pairSeed, float seconds, boolean sparring) {
      double s = seconds + (pairSeed >> 3 & 511L) * 0.29;
      return (float)((Math.sin(s * 1.7) * 0.6 + Math.sin(s * 4.3 + 1.1) * 0.25) * (sparring ? 0.05 : 0.09));
   }

   /** Jolt of a clash: both locked racks jump up together and settle in ~0.4 s (entity blocks; rigid, keeps contact). */
   public static float clashLift(float age, float scale) {
      if (age < 0.0F || age > 0.45F) {
         return 0.0F;
      }

      float x = age / 0.45F;
      return (float)(Math.sin(x * Math.PI * 2.0) * Math.exp(-x * 4.0) * 0.07 * scale);
   }

   // ------------------------------------------------------------------ classic (vanilla-style) box model

   /** Head-part pitch (radians, nose down) of the classic box model's locked posture. */
   public static float classicPitch(GameSpecies s) {
      return switch (s) {
         case ELK -> 1.25F;
         case MOOSE -> 1.2F;
         default -> 1.3F;
      };
   }

   /** Head-part drop (model pixels / 16) of the classic box model's locked posture. */
   public static float classicDrop(GameSpecies s) {
      return switch (s) {
         case ELK -> 4.0F;
         case MOOSE -> 4.5F;
         default -> 2.5F;
      };
   }
}
