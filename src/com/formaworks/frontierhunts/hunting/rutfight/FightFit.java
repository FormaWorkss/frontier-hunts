package com.formaworks.frontierhunts.hunting.rutfight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

/**
 * [rutfight] Where two locked fighters must stand. Both racks are taken in their locked posture at their natural
 * heights (a ground step between the animals is shared out between the two necks), and the rival is slid in along the
 * shared axis until the first antler surface points of the two racks touch: that body-to-body distance is where tips and
 * beams meet. When the two racks would pass each other without touching (a spike buck inside a wide rack, very different
 * head heights) the heads are moved up/down and sideways by the smallest amount that makes them meet, each animal taking
 * half. The racks are then pressed {@link #INTERLOCK} further together so tines visibly mesh, but never so far that an
 * antler reaches the rival's skull or the two skulls touch.
 *
 * <p>Everything here is in entity space of fighter A (origin at A's feet, +z towards B); B stands at (0, groundDy, D)
 * facing back. Pure maths on {@link FightModel}s, used by the server (realistic mesh) and by every client for its own
 * graphics preset.</p>
 */
public final class FightFit {
   /** Lateral tolerance for two surface samples to count as touching (blocks). */
   public static final float EPS = 0.03F;
   /** How far the racks are pressed into each other past the first touch. */
   public static final float INTERLOCK = 0.035F;
   /** Minimum clearance between an antler and the rival's skull, and between the skulls. */
   public static final float SKULL_MARGIN = 0.035F;
   /** Search step and reach of the head offsets that make two racks meet. */
   static final float STEP = 0.025F;
   static final int WINDOW = 6;
   /** Engagement depth (blocks of distance) one block of head offset is worth. */
   static final float OFFSET_COST = 1.5F;
   /** Body-to-body distance of the locked pair. */
   public final float distance;
   /** Distance at which the first antler points touch. */
   public final float firstContact;
   /** Vertical head offsets (entity blocks, each in its own frame). */
   public final float liftA;
   public final float liftB;
   /** Sideways head offsets (entity blocks, each in its own frame). */
   public final float sideA;
   public final float sideB;
   /** Contact point (where the racks meet) in A's entity space at {@link #distance}. */
   public final Vector3f contact;
   /** Closest antler-to-rival-skull / skull-to-skull clearance at {@link #distance} (diagnostic). */
   public final float skullClearance;
   /** Longest head-joint-to-contact lever of the two (entity blocks): big racks twist less. */
   public final float lever;
   /** The two head models the fit was solved for (A = lower entity id), kept so renderers never rebuild them. */
   public FightModel modelA;
   public FightModel modelB;

   private FightFit(float distance, float firstContact, float liftA, float liftB, float sideA, float sideB, Vector3f contact, float skullClearance, float lever) {
      this.lever = lever;
      this.distance = distance;
      this.firstContact = firstContact;
      this.liftA = liftA;
      this.liftB = liftB;
      this.sideA = sideA;
      this.sideB = sideB;
      this.contact = contact;
      this.skullClearance = skullClearance;
   }

   /**
    * @param groundDy rival's feet height minus A's (blocks)
    */
   public static FightFit solve(FightModel a, FightModel b, float groundDy) {
      float[] pa = a.place(a.antler, a.head);
      float[] pb = b.place(b.antler, b.head);
      if (pa.length < 3 || pb.length < 3) {
         // no antlers on one side (should not happen for a fight): foreheads touch instead
         pa = a.place(a.skull, a.head);
         pb = b.place(b.skull, b.head);
      }

      float[] sa = a.place(a.skull, a.head);
      float[] sb = b.place(b.skull, b.head);
      // B's points as seen from A at their natural heights, without the distance: x' = -x, y' = y + groundDy, z' = D - z
      Map<Long, List<float[]>> grid = new HashMap<>();

      for (int i = 0; i + 2 < pb.length; i += 3) {
         float x = -pb[i];
         float y = pb[i + 1] + groundDy;
         grid.computeIfAbsent(key2(x, y), k -> new ArrayList<>()).add(new float[]{x, y, pb[i + 2]});
      }

      // Offsets of B relative to A within a small window (heads up/down and sideways, half each). Real racks do not
      // stop tip-on-tip: they slide past and mesh until beams and tines catch. Each offset is scored by how deep the
      // racks get before the first antler-to-antler touch, plus a cost for moving the heads off their natural line; the
      // best scored offset whose locked distance keeps both skulls clear wins.
      float[] coarse = subsample(pa, 4000);
      float[] hit = null;
      float ox = 0.0F;
      float oy = -groundDy;
      float clear = -1.0F;
      float[] fallback = null;
      float fox = 0.0F;
      float foy = -groundDy;
      float fclear = -Float.MAX_VALUE;

      // second pass (wider, coarser) only when nothing in the near window keeps the skulls clear (a spike buck
      // against a wide rack)
      for (int pass = 0; pass < 2 && hit == null; pass++) {
      float step = pass == 0 ? STEP : STEP * 2.0F;
      List<float[]> cands = new ArrayList<>();

      for (int iy = -WINDOW; iy <= WINDOW; iy++) {
         for (int ix = -WINDOW; ix <= WINDOW; ix++) {
            float cx = ix * step;
            // the wide pass only reaches further sideways: necks lift / drop the head far less easily than they swing it
            float cy = -groundDy + iy * STEP;
            if (pass == 1 && Math.abs(ix) <= WINDOW / 2) {
               continue;
            }

            float[] h = firstTouch(coarse, grid, cx, cy);
            if (h != null) {
               cands.add(new float[]{h[0] + OFFSET_COST * (float)Math.sqrt(cx * cx + (cy + groundDy) * (cy + groundDy)), cx, cy});
            }
         }
      }

      cands.sort((x, y) -> Float.compare(x[0], y[0]));

      for (int k = 0; k < cands.size() && k < 24; k++) {
         float cx = cands.get(k)[1];
         float cy = cands.get(k)[2];
         float[] h = firstTouch(pa, grid, cx, cy);
         if (h == null) {
            continue;
         }

         float c = clearanceAt(pa, pb, sa, sb, groundDy, cx, cy, h[0] - INTERLOCK);
         if (c >= SKULL_MARGIN) {
            hit = h;
            ox = cx;
            oy = cy;
            clear = c;
            break;
         } else if (c > fclear) {
            fallback = h;
            fox = cx;
            foy = cy;
            fclear = c;
         }
      }
      }

      boolean pushOut = false;
      if (hit == null && fallback != null) {
         // every touch reaches a skull first (a small rack deep inside a big one): take the clearest and back off
         hit = fallback;
         ox = fox;
         oy = foy;
         pushOut = true;
      }

      // A's head moves by -o/2, B's by +o/2 (A's frame); B's sideways sense is mirrored in its own frame
      float liftA = -oy * 0.5F;
      float liftB = oy * 0.5F;
      float sideA = -ox * 0.5F;
      float sideB = -ox * 0.5F;
      shift(pa, sideA, liftA);
      shift(sa, sideA, liftA);
      shift(pb, sideB, liftB);
      shift(sb, sideB, liftB);
      float best;
      float bza;
      float bzb;
      Vector3f contact;
      if (hit == null) {
         // racks never cross at all within reach: front extents meet on the axis
         float za = -Float.MAX_VALUE;
         float zb = -Float.MAX_VALUE;

         for (int i = 2; i < pa.length; i += 3) {
            za = Math.max(za, pa[i]);
         }

         for (int i = 2; i < pb.length; i += 3) {
            zb = Math.max(zb, pb[i]);
         }

         best = za + zb;
         bza = za;
         bzb = zb;
         contact = new Vector3f(0.0F, (frontY(pa) + frontY(pb) + groundDy) * 0.5F, 0.0F);
         pushOut = true;
      } else {
         best = hit[0];
         bza = hit[3];
         bzb = hit[4];
         contact = new Vector3f(hit[1], hit[2], 0.0F);
      }

      float d = best - INTERLOCK;
      if (pushOut) {
         clear = clearance(pa, pb, sa, sb, groundDy, d);

         for (int guard = 0; clear < SKULL_MARGIN && guard < 60; guard++) {
            d += 0.01F;
            clear = clearance(pa, pb, sa, sb, groundDy, d);
         }
      }

      contact.z = (bza + (d - bzb)) * 0.5F;
      Vector3f headA = a.toEntity(a.head.getTranslation(new Vector3f()), new Vector3f()).add(sideA, liftA, 0.0F);
      Vector3f headB = b.toEntity(b.head.getTranslation(new Vector3f()), new Vector3f()).add(sideB, liftB, 0.0F);
      Vector3f contactB = new Vector3f(-contact.x, contact.y - groundDy, d - contact.z);
      float lever = Math.max(headA.distance(contact), headB.distance(contactB));
      FightFit fit = new FightFit(d, best, liftA, liftB, sideA, sideB, contact, clear, lever);
      fit.modelA = a;
      fit.modelB = b;
      return fit;
   }

   /** Skull clearance with B offset by (ox, oy) relative to A, at distance d. */
   private static float clearanceAt(float[] pa, float[] pb, float[] sa, float[] sb, float groundDy, float ox, float oy, float d) {
      float[] pa2 = pa.clone();
      float[] sa2 = sa.clone();
      float[] pb2 = pb.clone();
      float[] sb2 = sb.clone();
      shift(pa2, -ox * 0.5F, -oy * 0.5F);
      shift(sa2, -ox * 0.5F, -oy * 0.5F);
      shift(pb2, -ox * 0.5F, oy * 0.5F);
      shift(sb2, -ox * 0.5F, oy * 0.5F);
      return clearance(pa2, pb2, sa2, sb2, groundDy, d);
   }

   /**
    * Deepest touching pair with B offset by (ox, oy) relative to A: {z sum, contact x, contact y, z of A's point, z of
    * B's point}, or null when no pair lines up.
    */
   private static float[] firstTouch(float[] pa, Map<Long, List<float[]>> grid, float ox, float oy) {
      float best = -Float.MAX_VALUE;
      float[] out = null;
      float e2 = EPS * EPS;

      for (int i = 0; i + 2 < pa.length; i += 3) {
         float x = pa[i] - ox;
         float y = pa[i + 1] - oy;
         int cx = (int)Math.floor(x / EPS);
         int cy = (int)Math.floor(y / EPS);

         for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
               List<float[]> cell = grid.get(pack(cx + dx, cy + dy));
               if (cell != null) {
                  for (float[] q : cell) {
                     float rx = x - q[0];
                     float ry = y - q[1];
                     if (rx * rx + ry * ry <= e2 && pa[i + 2] + q[2] > best) {
                        best = pa[i + 2] + q[2];
                        // midpoint of the two points once A's head has moved by -o/2 and B's by +o/2
                        out = new float[]{best, (pa[i] + q[0]) * 0.5F, (pa[i + 1] + q[1]) * 0.5F, pa[i + 2], q[2]};
                     }
                  }
               }
            }
         }
      }

      return out;
   }

   /** Every n-th point so at most {@code max} remain (the coarse pass of the offset search). */
   private static float[] subsample(float[] p, int max) {
      int n = p.length / 3;
      if (n <= max) {
         return p;
      }

      int step = (n + max - 1) / max;
      float[] out = new float[(n + step - 1) / step * 3];
      int m = 0;

      for (int i = 0; i < n; i += step) {
         out[m++] = p[i * 3];
         out[m++] = p[i * 3 + 1];
         out[m++] = p[i * 3 + 2];
      }

      return java.util.Arrays.copyOf(out, m);
   }

   private static float frontY(float[] p) {
      float maxZ = -Float.MAX_VALUE;
      float y = 0.0F;

      for (int i = 0; i + 2 < p.length; i += 3) {
         if (p[i + 2] > maxZ) {
            maxZ = p[i + 2];
            y = p[i + 1];
         }
      }

      return y;
   }

   private static void shift(float[] p, float dx, float dy) {
      for (int i = 0; i + 2 < p.length; i += 3) {
         p[i] += dx;
         p[i + 1] += dy;
      }
   }

   /** Smallest distance from A's antlers to B's skull, B's antlers to A's skull, and skull to skull, at distance d. */
   static float clearance(float[] pa, float[] pb, float[] sa, float[] sb, float groundDy, float d) {
      float[] pbA = toA(pb, groundDy, d);
      float[] sbA = toA(sb, groundDy, d);
      return Math.min(minDistance(pa, sbA, 0.25F), Math.min(minDistance(pbA, sa, 0.25F), minDistance(sa, sbA, 0.25F)));
   }

   /** B-frame points into A's frame at distance d. */
   public static float[] toA(float[] p, float groundDy, float d) {
      float[] out = new float[p.length];

      for (int i = 0; i + 2 < p.length; i += 3) {
         out[i] = -p[i];
         out[i + 1] = p[i + 1] + groundDy;
         out[i + 2] = d - p[i + 2];
      }

      return out;
   }

   /** Smallest distance between two point sets, or {@code cap} if nothing is closer than that. */
   public static float minDistance(float[] p, float[] q, float cap) {
      if (p.length < 3 || q.length < 3) {
         return cap;
      }

      Map<Long, List<float[]>> grid = new HashMap<>();

      for (int i = 0; i + 2 < q.length; i += 3) {
         grid.computeIfAbsent(key3(q[i], q[i + 1], q[i + 2], cap), k -> new ArrayList<>()).add(new float[]{q[i], q[i + 1], q[i + 2]});
      }

      float best = cap * cap;

      for (int i = 0; i + 2 < p.length; i += 3) {
         int cx = (int)Math.floor(p[i] / cap);
         int cy = (int)Math.floor(p[i + 1] / cap);
         int cz = (int)Math.floor(p[i + 2] / cap);

         for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
               for (int dz = -1; dz <= 1; dz++) {
                  List<float[]> cell = grid.get(pack3(cx + dx, cy + dy, cz + dz));
                  if (cell != null) {
                     for (float[] o : cell) {
                        float x = p[i] - o[0];
                        float y = p[i + 1] - o[1];
                        float z = p[i + 2] - o[2];
                        float s = x * x + y * y + z * z;
                        if (s < best) {
                           best = s;
                        }
                     }
                  }
               }
            }
         }
      }

      return (float)Math.sqrt(best);
   }

   private static long key2(float x, float y) {
      return pack((int)Math.floor(x / EPS), (int)Math.floor(y / EPS));
   }

   private static long pack(int x, int y) {
      return (long)x << 32 | (long)y & 4294967295L;
   }

   private static long key3(float x, float y, float z, float cell) {
      return pack3((int)Math.floor(x / cell), (int)Math.floor(y / cell), (int)Math.floor(z / cell));
   }

   private static long pack3(int x, int y, int z) {
      return ((long)x & 2097151L) << 42 | ((long)y & 2097151L) << 21 | (long)z & 2097151L;
   }
}
