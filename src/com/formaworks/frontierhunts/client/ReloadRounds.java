package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.expedition.WeaponAction;
import java.util.ArrayList;
import java.util.List;

/**
 * [gear21] Where the rounds are during a reload, for the guns that load by hand into a chamber: the double barrel and
 * flare gun (break open: spent shells kick out, new shells are pushed into the chambers), the revolver (cylinder swung
 * out: the empties drop out the back, six rounds go in one by one) and the single-shot dart and bait guns (bolt drawn
 * back, the round is laid in the port and pushed forward). Pure geometry in the gun's own model units (before the
 * first-person scale), so the game ({@link FieldWeaponHands}) and the offline gun bench draw exactly the same thing.
 *
 * <p>Ammo meshes are modelled along +y with the base (brass, primer) at -y; {@link Round#pitch} turns +y onto the
 * direction the round points, so a round goes in nose first.
 */
public final class ReloadRounds {
   private ReloadRounds() {
   }

   /** a round: centre in gun units, pitch about X (degrees, applied before the base -90 that lays it along -z), scale */
   public record Round(double x, double y, double z, float pitch, float roll, float scale, boolean spent) {
   }

   /** the support hand's target (gun units) and how strongly it follows it (0..1) */
   public record Hand(double x, double y, double z, float weight) {
   }

   public record Frame(List<Round> rounds, Hand hand) {
   }

   /** the break-action / crane "open" amount for reload progress {@code p} (same curve as FieldWeaponMesh) */
   public static float open(float p) {
      if (p <= 0.0F) {
         return 0.0F;
      }
      if (p < 0.19F) {
         return smooth(p / 0.19);
      }
      if (p < 0.77F) {
         return 1.0F;
      }
      return 1.0F - smooth((p - 0.77) / 0.2);
   }

   /** the bolt of the single-shot guns: handle lifted, then drawn back for the whole load, pushed home and turned down */
   public static float boltBack(float p) {
      if (p <= 0.0F) {
         return 0.0F;
      }
      return p < 0.08F ? 0.0F : p < 0.18F ? smooth((p - 0.08) / 0.1) : p < 0.8F ? 1.0F : 1.0F - smooth((p - 0.8) / 0.1);
   }

   /** the bolt handle's lift (0 down, 1 up) */
   public static float boltLift(float p) {
      if (p <= 0.0F) {
         return 0.0F;
      }
      return p < 0.08F ? smooth(p / 0.08) : p < 0.9F ? 1.0F : 1.0F - smooth((p - 0.9) / 0.08);
   }

   /** bolt axis (x, y) the handle turns about */
   public static final double BOLT_Y = 0.067;

   static float smooth(double t) {
      double c = Math.max(0.0, Math.min(1.0, t));
      return (float)(c * c * (3.0 - 2.0 * c));
   }

   static double seg(double p, double a, double b) {
      return Math.max(0.0, Math.min(1.0, (p - a) / (b - a)));
   }

   // ---------------------------------------------------------------------------------------------------- geometry

   /** break actions: chambers (x, y at the breech face z), hinge (y, z), max opening angle, ammo radius and bore radius */
   private record Break(double[] xs, double y, double z, double hingeY, double hingeZ, float angle, double ammoR, double ammoLen, double bore) {
   }

   private static final Break DOUBLE = new Break(new double[]{-0.0118, 0.0118}, 0.067, -0.0965, 0.017, -0.159, 31.0F, 0.0393, 0.252, 0.0086);
   private static final Break FLARE = new Break(new double[]{0.0}, 0.05, -0.0345, 0.014, -0.08, 37.0F, 0.0464, 0.254, 0.0125);

   /** revolver: cylinder axis (y) and rear face z, chamber circle radius, crane pivot (y), swing angle */
   private static final double CYL_Y = 0.057, CYL_REAR = 0.0025, CYL_R = 0.0115, CRANE_Y = 0.016;
   private static final float CRANE = 87.0F;

   public static Frame frame(Weapon w, float p) {
      List<Round> out = new ArrayList<>();
      Hand hand = null;
      if (p <= 0.0F || p >= 1.0F) {
         return new Frame(out, null);
      }
      WeaponAction.Reload kind = WeaponAction.reload(w);
      if (kind == WeaponAction.Reload.BREAK) {
         hand = breakAction(w == Weapon.DOUBLE_BARREL ? DOUBLE : FLARE, p, out);
      } else if (kind == WeaponAction.Reload.CYLINDER) {
         hand = cylinder(p, out);
      } else if (kind == WeaponAction.Reload.SINGLE) {
         hand = single(w, p, out);
      }
      return new Frame(out, hand);
   }

   private static Hand breakAction(Break g, float p, List<Round> out) {
      float open = open(p);
      double th = Math.toRadians(-g.angle * open);
      double c = Math.cos(th), s = Math.sin(th);
      // barrel axis toward the muzzle after the barrels drop, and back toward the breech
      double fy = s, fz = -c;
      float scale = (float)(g.bore / g.ammoR);
      double len = g.ammoLen * scale;
      int n = g.xs.length;
      Hand hand = null;
      for (int i = 0; i < n; i++) {
         double dy = g.y - g.hingeY, dz = g.z - g.hingeZ;
         double cy = g.hingeY + dy * c - dz * s, cz = g.hingeZ + dy * s + dz * c;
         double cx = g.xs[i];
         // seated: base 1.5 mm proud of the breech face
         double sy = cy + fy * (len / 2 - 0.0015), sz = cz + fz * (len / 2 - 0.0015);
         float pitch = (float)-(g.angle * open);
         // the empties kick out backward and up, tumbling, then fall away
         double e = seg(p, 0.17 + 0.02 * i, 0.38 + 0.02 * i);
         if (e > 0.0 && e < 1.0 && open > 0.6F) {
            double back = 0.02 + 0.16 * e, up = 0.09 * e - 0.2 * e * e;
            out.add(new Round(cx + (i == 0 ? -1 : 1) * 0.03 * e, sy - fy * back + up, sz - fz * back, pitch + (float)(420.0 * e), 0.0F, scale, true));
         }
         // the new shell: brought up behind the open breech, lined up with the chamber, pushed home
         double a = 0.4 + 0.17 * i, b = a + 0.17;
         double k = seg(p, a, b);
         if (k > 0.0 && open > 0.12F) {
            double ex = cx, ey = sy - fy * (len + 0.012), ez = sz - fz * (len + 0.012);
            double x, y, z;
            float pp = pitch;
            if (k < 0.55) {
               double t = smooth(k / 0.55);
               // from the hand, low and to the support side, rising into line
               x = cx + (1 - t) * -0.05;
               y = ey + (1 - t) * -0.09;
               z = ez + (1 - t) * 0.06;
               pp = pitch + (float)((1 - t) * 50.0);
            } else {
               double t = smooth((k - 0.55) / 0.45);
               x = ex;
               y = ey + (sy - ey) * t;
               z = ez + (sz - ez) * t;
            }
            out.add(new Round(x, y, z, pp, 0.0F, scale, false));
            if (k < 1.0) {
               hand = new Hand(x - 0.01, y - 0.06, z + 0.03, 1.0F);
            }
         }
      }
      if (hand == null) {
         // between shells / while opening and closing: the support hand holds the barrels at the fore-end
         double dy = -0.02 - g.hingeY, dz = -0.3 - g.hingeZ;
         hand = new Hand(-0.02, g.hingeY + dy * c - dz * s, g.hingeZ + dy * s + dz * c, open);
      }
      return hand;
   }

   private static Hand cylinder(float p, List<Round> out) {
      float open = open(p);
      double phi = Math.toRadians(CRANE * Math.min(1.0F, open));
      double c = Math.cos(phi), s = Math.sin(phi);
      float scale = (float)(0.0047 / 0.035);
      double len = 0.215 * scale;
      Hand hand = null;
      for (int i = 0; i < 6; i++) {
         double a = Math.toRadians(90.0 + 60.0 * i);
         double px = Math.cos(a) * CYL_R, py = CYL_Y + Math.sin(a) * CYL_R;
         // swing with the crane about the z axis through (0, CRANE_Y)
         double rx = px * c - (py - CRANE_Y) * s, ry = CRANE_Y + px * s + (py - CRANE_Y) * c;
         double seatedZ = CYL_REAR - len / 2 + 0.0012;
         // empties pushed out by the ejector rod, then dropping
         double e = seg(p, 0.2, 0.33);
         if (e > 0.0 && e < 1.0) {
            double z = seatedZ + 0.035 * Math.min(1.0, e * 2.5);
            double fall = Math.max(0.0, e - 0.35) * 0.25;
            out.add(new Round(rx, ry - fall * (0.6 + 0.08 * i), z + fall * 0.2, (float)(Math.max(0.0, e - 0.35) * 160.0 * (i % 2 == 0 ? 1 : -1)), 0.0F, scale, true));
         }
         // loading: one round at a time, top chamber first
         double l0 = 0.35 + 0.065 * i, l1 = l0 + 0.065;
         double k = seg(p, l0, l1);
         if (k > 0.0 && open > 0.12F) {
            double z, x = rx, y = ry;
            if (k < 0.5) {
               double t = smooth(k / 0.5);
               x = rx + (1 - t) * -0.03;
               y = ry + (1 - t) * -0.05;
               z = seatedZ + len + 0.01 + (1 - t) * 0.04;
            } else {
               double t = smooth((k - 0.5) / 0.5);
               z = seatedZ + (len + 0.01) * (1 - t);
            }
            out.add(new Round(x, y, z, 0.0F, 0.0F, scale, false));
            if (k < 1.0) {
               hand = new Hand(x - 0.012, y - 0.06, z + 0.03, 1.0F);
            }
         }
      }
      return hand;
   }

   private static Hand single(Weapon w, float p, List<Round> out) {
      boolean bait = w == Weapon.BAIT_LAUNCHER;
      double portX = bait ? 0.014 : 0.012, portY = bait ? 0.072 : 0.068, portZ = bait ? 0.03 : 0.0;
      double ammoLen = bait ? 0.18 : 0.33;
      float scale = bait ? 0.13F : 0.25F;
      double len = ammoLen * scale;
      double k = seg(p, 0.24, 0.72);
      if (k <= 0.0 || boltBack(p) < 0.5F) {
         return null;
      }
      double x, y, z;
      if (k < 0.5) {
         double t = smooth(k / 0.5);
         // from the hand up and over to the right of the open port
         x = portX + (1 - t) * 0.05;
         y = portY + 0.004 + (1 - t) * -0.06;
         z = portZ + len / 2 + 0.005 + (1 - t) * 0.03;
      } else {
         double t = smooth((k - 0.5) / 0.5);
         x = portX * (1 - t);
         y = portY + 0.004 * (1 - t);
         z = portZ + len / 2 + 0.005 - (len * 0.9) * t;
      }
      out.add(new Round(x, y, z, 0.0F, 0.0F, scale, false));
      return k < 1.0 ? new Hand(x + 0.012, y - 0.06, z + 0.03, 1.0F) : null;
   }
}
