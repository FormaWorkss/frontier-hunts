package com.formaworks.frontierhunts.sign.work;

import com.formaworks.frontierhunts.hunting.DeerSkeleton;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.rutfight.FightModel;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * [deersign] Where a buck's antlers meet a trunk and where his nose reaches a licking branch, from the real head and
 * antler geometry ({@link FightModel}: the sculpted Ultra mesh, or the Vanilla box model on the client).
 *
 * <p>Spaces: model space is the rig's (+y up, forward = -z); entity space has its origin at the feet, +z forward,
 * entity = (-x * sx, y * sy, -z * sz) (FightModel). Head-bone space: the antler and skull points of the FightModel.</p>
 */
public final class SignGeometry {
   /**
    * Pitch of the rubbing head about its joint over the locked-antlers frame (radians, about the rig's x axis): the
    * forehead and the front of the rack square to the bark, nose tucked down. Chosen per species on the sculpted rigs
    * (tools/deersign harness previews).
    */
   public static float rubPitch(GameSpecies s) {
      return switch (s) {
         case ELK -> 0.35F;
         case MOOSE -> 0.6F;
         default -> 0.55F;
      };
   }
   /** Nose-up pitch of the head (about its joint) working a licking branch. */
   public static final float LICK_HEAD = 0.75F;
   /** Neck raise (about the first neck joint) working a licking branch. */
   public static final float LICK_NECK = 0.24F;
   private static final EnumMap<GameSpecies, Matrix4f[]> STAND = new EnumMap<>(GameSpecies.class);
   private static final Map<FightModel, Vector3f> NOSE = new WeakHashMap<>();

   private SignGeometry() {
   }

   /** Rig model matrices of the plain standing pose (stand clip, first frame). */
   public static synchronized Matrix4f[] stand(DeerSkeleton sk) {
      Matrix4f[] m = STAND.get(sk.species);
      if (m == null) {
         int n = sk.count();
         float[] t = new float[n * 3];
         float[] q = new float[n * 4];
         DeerSkeleton.Clip c = sk.clip("stand");
         if (c != null) {
            sk.sample(c, 0.0F, t, q);
         } else {
            sk.rest(t, q);
         }
         m = new Matrix4f[n];
         for (int i = 0; i < n; i++) {
            m[i] = new Matrix4f();
         }
         sk.pose(t, q, null, m);
         STAND.put(sk.species, m);
      }
      return m;
   }

   /** Rotates head matrix {@code h} about point {@code p} (model space) by {@code angle} about model axis {@code a}. */
   static Matrix4f about(Matrix4f h, Vector3f p, float ax, float ay, float az, float angle, Matrix4f out) {
      return out.identity().translate(p).rotate(angle, ax, ay, az).translate(-p.x, -p.y, -p.z).mul(h);
   }

   /** Twist / turn amplitude of the rubbing strokes: the long elk and moose racks swing a long way for a small turn. */
   public static float twistScale(GameSpecies s) {
      return s == GameSpecies.WHITETAIL ? 1.0F : 0.2F;
   }

   /** Nod of the head with the stroke (radians per unit stroke): the forehead tips up the trunk as the head rises.
    * The elk's long brow tines would catch the bark on a nodding head, so the elk keeps his head angle. */
   public static float strokeNod(GameSpecies s) {
      return s == GameSpecies.ELK ? 0.0F : 0.12F;
   }

   /** Vanilla box head: radians of nod per block of rack-tip height (the rigid head turns about its neck pivot). */
   public static final float BOX_NOD_PER_BLOCK = 1.0F / 0.45F;

   /** The rubbing head at rest (no stroke): the locked-antlers head pitched nose-down about its joint. */
   public static Matrix4f rubBase(FightModel m, GameSpecies s, Matrix4f out) {
      Vector3f p = m.head.getTranslation(new Vector3f());
      return about(m.head, p, 1.0F, 0.0F, 0.0F, rubPitch(s), out);
   }

   /**
    * Rubbing head target (model space): the rest head lifted by {@code up} (model units, stroke + height correction),
    * twisted ({@code roll}, about the forward axis) and turned ({@code yaw}) about the head joint, nodding a little with
    * the stroke.
    */
   public static Matrix4f rubTarget(FightModel m, GameSpecies s, float up, float roll, float yaw, float nod, Matrix4f out) {
      Matrix4f base = rubBase(m, s, new Matrix4f());
      Vector3f p = base.getTranslation(new Vector3f());
      Matrix4f r = new Matrix4f().translate(p).rotateY(yaw).rotateZ(roll).rotateX(nod).translate(-p.x, -p.y, -p.z);
      return out.identity().translate(0.0F, up, 0.0F).mul(r).mul(base);
   }

   /** Nose tip in head-bone space: the skull point furthest forward with the head in its standing pose. */
   public static Vector3f nose(FightModel m, DeerSkeleton sk) {
      synchronized (NOSE) {
         Vector3f n = NOSE.get(m);
         if (n != null) {
            return n;
         }
      }
      Matrix4f h = new Matrix4f(m.head);
      // the model's head matrix is the locked-fight head; any head pose works for "furthest along the face": use the
      // standing head's forward axis expressed in head space
      Matrix4f stand = stand(sk)[sk.head];
      Vector3f fwd = new Matrix4f(stand).invert().transformDirection(new Vector3f(0.0F, 0.0F, -1.0F)).normalize();
      Vector3f best = new Vector3f();
      float bd = -Float.MAX_VALUE;
      float[] s = m.skull;
      for (int i = 0; i + 2 < s.length; i += 3) {
         float d = s[i] * fwd.x + s[i + 1] * fwd.y + s[i + 2] * fwd.z;
         if (d > bd) {
            bd = d;
            best.set(s[i], s[i + 1], s[i + 2]);
         }
      }
      synchronized (NOSE) {
         NOSE.put(m, best);
      }
      return best;
   }

   /** Head matrix (model space) of a buck reaching up to a licking branch: neck raised, nose up. */
   public static Matrix4f lickBase(DeerSkeleton sk, Matrix4f out) {
      Matrix4f[] st = stand(sk);
      Matrix4f h = st[sk.head];
      Vector3f hj = h.getTranslation(new Vector3f());
      Vector3f nj = st[sk.neck0].getTranslation(new Vector3f());
      Matrix4f h1 = about(h, hj, 1.0F, 0.0F, 0.0F, LICK_HEAD, new Matrix4f());
      return about(h1, nj, 1.0F, 0.0F, 0.0F, LICK_NECK, out);
   }

   /**
    * Where this buck's nose works the licking branch, relative to his feet: {forward, height} in entity blocks.
    * A scrape's licking branch tip is put there (the buck stands at the scrape facing the tree).
    */
   public static float[] lickReach(FightModel m, DeerSkeleton sk) {
      Matrix4f h = lickBase(sk, new Matrix4f());
      Vector3f n = h.transformPosition(new Vector3f(nose(m, sk)));
      return new float[]{-n.z * m.sz, n.y * m.sy};
   }

   /**
    * Licking-branch head target: the raised head with the nuzzling motion about the nose (which stays on the branch
    * tip, lifted by {@code up} model units).
    */
   public static Matrix4f lickTarget(FightModel m, DeerSkeleton sk, float yaw, float pitch, float roll, float up, Matrix4f out) {
      Matrix4f base = lickBase(sk, new Matrix4f());
      Vector3f n = base.transformPosition(new Vector3f(nose(m, sk)));
      Vector3f hj = base.getTranslation(new Vector3f());
      // turn about a point between the head joint and the nose (forehead / preorbital glands rub the twigs)
      Vector3f p = new Vector3f(hj).lerp(n, 0.65F);
      Matrix4f r = new Matrix4f().translate(p).rotateY(yaw).rotateZ(roll).rotateX(pitch).translate(-p.x, -p.y, -p.z);
      return out.identity().translate(0.0F, up, 0.0F).mul(r).mul(base);
   }

   // ------------------------------------------------------------------------------------------------ trunk contact

   /**
    * A trunk's cross-section in the buck's entity space (x lateral, z forward): a circle (realistic round stem) or a
    * square (a block log, possibly turned by {@code angle} relative to the buck).
    */
   public record Trunk(boolean round, float cx, float cz, float r, float angle, float y0, float y1) {
      /** Distance along +z from (x, z) to where the ray enters the trunk; +inf when it misses. */
      public float entry(float x, float z) {
         if (this.round) {
            float dx = x - this.cx;
            if (dx * dx >= this.r * this.r) {
               return Float.POSITIVE_INFINITY;
            }
            return this.cz - (float)Math.sqrt(this.r * this.r - dx * dx) - z;
         }
         // square of half size r turned by angle: slab test in its frame
         float c = (float)Math.cos(this.angle);
         float s = (float)Math.sin(this.angle);
         float px = (x - this.cx) * c + (z - this.cz) * s;
         float pz = -(x - this.cx) * s + (z - this.cz) * c;
         float dx = s;
         float dz = c;
         float tmin = -Float.MAX_VALUE;
         float tmax = Float.MAX_VALUE;
         float[] o = {px, pz};
         float[] d = {dx, dz};
         for (int k = 0; k < 2; k++) {
            if (Math.abs(d[k]) < 1.0E-6F) {
               if (o[k] < -this.r || o[k] > this.r) {
                  return Float.POSITIVE_INFINITY;
               }
            } else {
               float t1 = (-this.r - o[k]) / d[k];
               float t2 = (this.r - o[k]) / d[k];
               tmin = Math.max(tmin, Math.min(t1, t2));
               tmax = Math.min(tmax, Math.max(t1, t2));
            }
         }
         return tmax < tmin || tmax < 0.0F ? Float.POSITIVE_INFINITY : tmin;
      }
   }

   /** {@link #contact} result */
   public static final class Contact {
      /** forward slide (entity blocks) that brings the head into touch (negative: back off) */
      public float slide = Float.NaN;
      /** mean height of the touching points (entity y) */
      public float y;
      /** lateral position of the touching points */
      public float x;
      /** vertical spread of the touching points */
      public float spread;
      public int touching;

      public boolean ok() {
         return !Float.isNaN(this.slide);
      }
   }

   /**
    * How far the head (antler and skull points placed with head matrix {@code head}) must slide forward to touch the
    * trunk, and where it touches. {@code step} thins the points (1 = all).
    */
   public static Contact contact(FightModel m, Matrix4f head, Trunk trunk, int step, Contact out) {
      out.slide = Float.NaN;
      out.touching = 0;
      float best = Float.POSITIVE_INFINITY;
      float[][] sets = {m.antler, m.skull};
      Vector3f p = new Vector3f();
      int n = 0;
      float[] ds = new float[(m.antler.length + m.skull.length) / 3 / Math.max(1, step) + 2];
      float[] ys = new float[ds.length];
      float[] xs = new float[ds.length];
      for (float[] set : sets) {
         for (int i = 0; i + 2 < set.length; i += 3 * Math.max(1, step)) {
            head.transformPosition(set[i], set[i + 1], set[i + 2], p);
            float x = -p.x * m.sx;
            float y = p.y * m.sy;
            float z = -p.z * m.sz;
            if (y < trunk.y0() || y > trunk.y1()) {
               continue;
            }
            float d = trunk.entry(x, z);
            if (d == Float.POSITIVE_INFINITY || n >= ds.length) {
               continue;
            }
            ds[n] = d;
            ys[n] = y;
            xs[n] = x;
            n++;
            best = Math.min(best, d);
         }
      }
      if (n == 0) {
         return out;
      }
      float sy = 0.0F;
      float sx = 0.0F;
      float lo = Float.MAX_VALUE;
      float hi = -Float.MAX_VALUE;
      int c = 0;
      for (int i = 0; i < n; i++) {
         if (ds[i] - best < 0.025F) {
            sy += ys[i];
            sx += xs[i];
            lo = Math.min(lo, ys[i]);
            hi = Math.max(hi, ys[i]);
            c++;
         }
      }
      out.slide = best;
      out.y = sy / c;
      out.x = sx / c;
      out.spread = hi - lo;
      out.touching = c;
      return out;
   }
}
