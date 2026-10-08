package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.archery.ArrowFlight;
import com.formaworks.frontierhunts.archery.BowBallistics;
import com.formaworks.frontierhunts.hunting.ArrowSupply;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * [archery2] Offline checks for arrows in the world and in the bow kill cam:
 * <ol>
 *   <li>arrow orientation: FieldArrowRenderer.orient points the model's head (-z) along the flight for every heading and
 *       pitch, with the fletching's up side kept up (no roll flips); the old transform is measured for comparison;</li>
 *   <li>stuck arrows: the projectile-rotation fallback gives the flight direction back;</li>
 *   <li>the head offsets the kill cam and the deer's impact marks use match the arrow model's real head point, for every
 *       head, arrow and bolt (so "the point is at the hit" is literally true);</li>
 *   <li>the bow kill cam chase, flown frame by frame at 60 and 144 fps through real flights (server integrator samples
 *       into KillCamPath, the replay's approach easing): the camera stays behind and above the arrow looking down its line
 *       (arrow in the middle of the view, nock toward the camera), moves smoothly (no per-tick steps), stops short of the
 *       animal and watches the point arrive exactly at the hit.</li>
 * </ol>
 */
public final class ArrowCamHarness {
   static int fails = 0;

   static void check(boolean ok, String what) {
      if (!ok) {
         fails++;
         System.out.println("FAIL " + what);
      }
   }

   static double angle(Vec3 a, Vec3 b) {
      return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, a.normalize().dot(b.normalize())))));
   }

   static Vec3 apply(PoseStack p, double x, double y, double z) {
      Vector3f v = p.last().pose().transformDirection(new Vector3f((float)x, (float)y, (float)z));
      return new Vec3(v.x, v.y, v.z);
   }

   public static void main(String[] args) throws Exception {
      Random rnd = new Random(7);
      // 1. orientation
      double worstNew = 0.0;
      double worstUp = 0.0;
      for (int i = 0; i < 20000; i++) {
         double yaw = rnd.nextDouble() * Math.PI * 2.0;
         double pitch = (rnd.nextDouble() - 0.5) * Math.toRadians(170.0);
         Vec3 d = new Vec3(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
         PoseStack p = new PoseStack();
         FieldArrowRenderer.orient(p, d);
         worstNew = Math.max(worstNew, angle(apply(p, 0, 0, -1), d));
         if (Math.abs(Math.toDegrees(pitch)) < 80.0) {
            Vec3 up = apply(p, 0, 1, 0);
            worstUp = Math.max(worstUp, -up.y);
         }
      }
      check(worstNew < 0.05, "orient head error " + worstNew);
      check(worstUp <= 1.0E-6, "fletching rolled under: " + worstUp);
      System.out.printf("orientation: head along the flight within %.5f deg for 20000 directions; model up never below horizontal%n", worstNew);
      StringBuilder old = new StringBuilder();
      String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
      for (int k = 0; k < 8; k++) {
         double yaw = Math.toRadians(k * 45.0);
         Vec3 v = new Vec3(-Math.sin(yaw), 0.05, Math.cos(yaw));
         float yRot = (float)Math.toDegrees(Math.atan2(v.x, v.z));
         float xRot = (float)Math.toDegrees(Math.atan2(v.y, v.horizontalDistance()));
         PoseStack p = new PoseStack();
         p.mulPose(Axis.YP.rotationDegrees(180.0F - yRot));
         p.mulPose(Axis.XP.rotationDegrees(-xRot));
         old.append(String.format(" %s %.0f", names[k], angle(apply(p, 0, 0, -1), v)));
         PoseStack q = new PoseStack();
         FieldArrowRenderer.orient(q, FieldArrowRenderer.fromRotation(yRot, xRot));
         check(angle(apply(q, 0, 0, -1), v) < 0.01, "stuck arrow heading " + names[k]);
      }
      System.out.println("  old renderer: angle between drawn arrow and flight (deg):" + old + "  (90 = sideways, 180 = backwards)");
      System.out.println("  stuck-arrow heading from the projectile rotation: exact for all 8 headings");

      // 3. head offsets against the model
      ViewHarness.Cap cap = new ViewHarness.Cap();
      double worstOff = 0.0;
      for (ArrowTip t : ArrowTip.values()) {
         for (boolean bolt : new boolean[]{false, true}) {
            for (boolean prim : new boolean[]{false, true}) {
               cap.verts.clear();
               FieldArrowModel.draw(new PoseStack(), cap, 15728880, false, bolt, new ArrowSupply.Shot(t, prim), true);
               double minZ = 0.0;
               double maxZ = 0.0;
               for (ViewHarness.V v : cap.verts) {
                  minZ = Math.min(minZ, v.z);
                  maxZ = Math.max(maxZ, v.z);
               }
               double off = FieldArrowRenderer.pointReach(t, bolt);
               worstOff = Math.max(worstOff, Math.abs(-minZ - off));
               check(maxZ > 0.2 && minZ < -0.2, "arrow model extent " + t);
            }
         }
      }
      check(worstOff < 0.003, "head offset vs model " + worstOff);
      System.out.printf("head point: kill cam / impact-mark offsets within %.3f m of the model's real head point (all heads, arrow + bolt)%n", worstOff);

      // 4. kill cam chase
      double worstLook = 0.0;
      double worstArrowOff = 0.0;
      double worstJerk = 0.0;
      double worstTurn = 0.0;
      double endGap = 1.0E9;
      double worstEnd = 0.0;
      int flights = 0;
      for (BowBallistics.Profile prof : new BowBallistics.Profile[]{BowBallistics.FIELD_RECURVE, BowBallistics.RECURVE, BowBallistics.CROSSBOW}) {
         for (double dist : new double[]{8.0, 15.0, 30.0, 45.0}) {
            for (double fps : new double[]{60.0, 144.0}) {
               double yawDeg = rnd.nextDouble() * 360.0;
               float pitch = (float)(-Math.toDegrees(Math.atan(0.5 * prof.gravity() * Math.pow(dist / prof.speed(), 2) / dist)) - 0.5);
               double[] v = ArrowFlight.launch(pitch, (float)yawDeg, ArrowFlight.speed(prof, 1.0F));
               Vec3 origin = new Vec3(0.0, 1.62 - prof.originDrop(), 0.0);
               List<Vec3> samples = new ArrayList<>();
               samples.add(origin);
               double x = origin.x, y = origin.y, z = origin.z;
               double fx = -Math.sin(Math.toRadians(yawDeg)), fz = Math.cos(Math.toRadians(yawDeg));
               Vec3 impact = null;
               for (int t = 0; t < 200 && impact == null; t++) {
                  double nx = x + v[0], ny = y + v[1], nz = z + v[2];
                  double a0 = x * fx + z * fz - dist, a1 = nx * fx + nz * fz - dist;
                  if (a0 < 0 && a1 >= 0) {
                     double f = a0 / (a0 - a1);
                     impact = new Vec3(x + (nx - x) * f, y + (ny - y) * f, z + (nz - z) * f);
                     break;
                  }
                  x = nx;
                  y = ny;
                  z = nz;
                  samples.add(new Vec3(x, y, z));
                  ArrowFlight.step(prof, v, 0.0, 0.0, false);
               }
               if (impact == null) {
                  continue;
               }
               flights++;
               float[] rel = new float[(samples.size() + 1) * 3];
               for (int i = 0; i < samples.size(); i++) {
                  Vec3 r = samples.get(i).subtract(origin);
                  rel[i * 3] = (float)r.x;
                  rel[i * 3 + 1] = (float)r.y;
                  rel[i * 3 + 2] = (float)r.z;
               }
               Vec3 ri = impact.subtract(origin);
               rel[samples.size() * 3] = (float)ri.x;
               rel[samples.size() * 3 + 1] = (float)ri.y;
               rel[samples.size() * 3 + 2] = (float)ri.z;
               KillCamPath path = new KillCamPath(origin, rel, impact);
               Vec3 endDir = path.tangent(1.0);
               double tFlight = Math.min(Math.max(Math.min(Math.max(0.9 + path.length / 300.0, 1.0), 1.75) + 0.2, 1.0), 3.2);
               double dt = 1.0 / fps;
               Vec3 prevCam = null;
               Vec3 prevVel = null;
               Vec3 prevLook = null;
               double u = 0.0;
               KillCamReplay.Pose last = null;
               while (u < 1.0) {
                  u = Math.min(1.0, u + dt / tFlight);
                  double s = KillCamReplay.approach(u, path.length);
                  KillCamReplay.Pose pose = KillCamReplay.chaseArrow(path, u, s, Vec3.ZERO, impact, endDir, null);
                  Vec3 tip = path.at(s);
                  Vec3 tan = path.tangent(s);
                  Vec3 look = Vec3.directionFromRotation(pose.pitch(), pose.yaw());
                  Vec3 toArrow = tip.subtract(tan.scale(0.2)).subtract(pose.pos());
                  // straight on: looking down the arrow's line, the arrow in the middle of the view, never side-on
                  if (path.length * (1.0 - s) > 0.5) {
                     worstLook = Math.max(worstLook, angle(look, tan));
                     worstArrowOff = Math.max(worstArrowOff, angle(look, toArrow));
                  }
                  check(toArrow.dot(look) > 0.0, "arrow behind the camera");
                  if (prevCam != null) {
                     Vec3 vel = pose.pos().subtract(prevCam).scale(1.0 / dt);
                     if (prevVel != null) {
                        worstJerk = Math.max(worstJerk, vel.subtract(prevVel).length() * dt / Math.max(1.0, prevVel.length()));
                     }
                     prevVel = vel;
                     worstTurn = Math.max(worstTurn, angle(prevLook, look));
                  }
                  prevCam = pose.pos();
                  prevLook = look;
                  last = pose;
               }
               double tipErr = path.at(KillCamReplay.approach(1.0, path.length)).distanceTo(impact);
               worstEnd = Math.max(worstEnd, tipErr);
               endGap = Math.min(endGap, last.pos().distanceTo(impact));
               Vec3 lookEnd = Vec3.directionFromRotation(last.pitch(), last.yaw());
               check(angle(lookEnd, impact.subtract(last.pos())) < 12.0, "camera not watching the hit " + prof.id() + " " + dist);
            }
         }
      }
      System.out.printf("bow kill cam: %d flights x 60/144 fps: view vs flight line <= %.1f deg, arrow <= %.1f deg off centre,%n", flights, worstLook, worstArrowOff);
      System.out.printf("  per-frame speed change <= %.3f (relative), per-frame turn <= %.2f deg, point at the hit within %.6f m, camera stops >= %.2f m short%n",
         worstJerk, worstTurn, worstEnd, endGap);
      check(worstLook < 6.0, "camera not straight on");
      check(worstArrowOff < 9.0, "arrow off centre");
      check(worstTurn < 1.0, "camera turns in steps");
      check(worstJerk < 0.25, "camera jerks");
      check(worstEnd < 1.0E-6, "arrow point misses the hit");
      check(endGap > 1.0, "camera runs into the animal");
      if (fails > 0) {
         System.out.println("ARROW/KILLCAM HARNESS FAILED (" + fails + ")");
         System.exit(1);
      }
      System.out.println("ARROW/KILLCAM HARNESS OK");
   }
}
