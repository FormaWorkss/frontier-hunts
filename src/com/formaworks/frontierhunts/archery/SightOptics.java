package com.formaworks.frontierhunts.archery;

/**
 * [bows] Where an angle in the world lands on screen, and where a piece of the first-person bow must sit so it covers
 * that exact spot. Minecraft draws the world with the player's FOV and the hand with its own (70 degrees, both then
 * scaled by any zoom), so a pin modelled at the "right" angle in hand space would be off by the ratio of the two; this
 * maps between them. Pure maths for the offline harness. FOVs are vertical, in degrees.
 */
public final class SightOptics {
   private SightOptics() {
   }

   /** Pixels below the screen centre that a direction {@code angle} radians below the view axis is drawn at. */
   public static double screenDrop(double angle, double fovDeg, double screenHeight) {
      return Math.tan(angle) / Math.tan(Math.toRadians(fovDeg) * 0.5) * screenHeight * 0.5;
   }

   /** Camera-space drop (y, positive down) at view depth {@code depth} for a hand-pass vertex to cover world angle {@code angle}. */
   public static double handDrop(double angle, double depth, double worldFovDeg, double handFovDeg) {
      return Math.tan(angle) * ratio(worldFovDeg, handFovDeg) * depth;
   }

   /** tan(hand/2) / tan(world/2): scales a world-space tangent to the hand-space tangent that projects onto it. */
   public static double ratio(double worldFovDeg, double handFovDeg) {
      double w = Math.tan(Math.toRadians(clampFov(worldFovDeg)) * 0.5);
      double h = Math.tan(Math.toRadians(clampFov(handFovDeg)) * 0.5);
      return h / w;
   }

   /**
    * [archery2] Screen position (pixels from the top-left of a {@code w} x {@code h} view) of a point {@code (rx, ry, rz)}
    * metres from the camera, for a camera at yaw/pitch (Minecraft degrees) and vertical FOV {@code fovDeg} - the camera
    * basis built exactly as {@code Camera.setRotation} builds it ({@code rotationYXZ(pi - yaw, -pitch, 0)} applied to
    * forward (0,0,-1), up (0,1,0) and left (-1,0,0)) and projected the way the world's perspective matrix does. Null if
    * the point is behind the camera.
    */
   public static double[] project(double rx, double ry, double rz, float yaw, float pitch, double fovDeg, double w, double h) {
      org.joml.Quaternionf q = new org.joml.Quaternionf().rotationYXZ((float)Math.PI - yaw * (float)(Math.PI / 180.0), -pitch * (float)(Math.PI / 180.0), 0.0F);
      org.joml.Vector3f f = q.transform(new org.joml.Vector3f(0.0F, 0.0F, -1.0F));
      org.joml.Vector3f u = q.transform(new org.joml.Vector3f(0.0F, 1.0F, 0.0F));
      org.joml.Vector3f l = q.transform(new org.joml.Vector3f(-1.0F, 0.0F, 0.0F));
      double z = rx * f.x() + ry * f.y() + rz * f.z();
      if (z < 0.05) {
         return null;
      }
      double x = -(rx * l.x() + ry * l.y() + rz * l.z());
      double y = rx * u.x() + ry * u.y() + rz * u.z();
      double k = 1.0 / Math.tan(Math.toRadians(clampFov(fovDeg)) * 0.5) * h * 0.5;
      return new double[]{w * 0.5 + x / z * k, h * 0.5 - y / z * k};
   }

   private static double clampFov(double f) {
      return Double.isFinite(f) ? Math.max(1.0, Math.min(170.0, f)) : 70.0;
   }
}
