package com.formaworks.frontierhunts.archery;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * [1.2.5] Traditional bows (the recurve and the field bows) shoot where the crosshair is. They have no sight pins, so
 * the archer aims with the ordinary crosshair; the arrow still flies its real arc (it starts a hand below the eye and
 * drops with the distance), so the bow is raised by exactly the holdover that puts that arc through whatever is under
 * the crosshair: the first block or animal along the line of sight, up to the bow's reach. Aimed at open sky it flies
 * straight along the view, as before.
 */
public final class PointOfAim {
   static final double REACH = 120.0;

   private PointOfAim() {
   }

   /** the pitch (degrees, Minecraft's convention: down positive) to launch at so the arrow lands on the crosshair */
   public static float launchXRot(Player p, BowBallistics.Profile profile, double speed) {
      float xRot = p.getXRot();
      double d = distance(p);
      if (Double.isNaN(d) || d < 0.5) {
         return xRot;
      }
      double los = -Math.toRadians(xRot);
      double launch = BowBallistics.launchFor(profile, speed, los, d);
      if (Double.isNaN(launch)) {
         return xRot;
      }
      // never more than a sensible holdover (a far target out of reach keeps the view's angle)
      return Mth.clamp((float)-Math.toDegrees(launch), xRot - 30.0F, xRot + 2.0F);
   }

   /** slant distance from the eye to the first block or animal under the crosshair; NaN if nothing within reach */
   static double distance(Player p) {
      Vec3 eye = p.getEyePosition();
      Vec3 dir = p.getViewVector(1.0F);
      Vec3 end = eye.add(dir.scale(REACH));
      HitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
      double best = block.getType() == HitResult.Type.MISS ? Double.NaN : block.getLocation().distanceTo(eye);
      Vec3 to = Double.isNaN(best) ? end : eye.add(dir.scale(best));
      AABB box = p.getBoundingBox().expandTowards(to.subtract(eye)).inflate(1.0);
      EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, to, box,
         e -> e.isPickable() && !e.isSpectator() && e != p.getVehicle() && !p.isPassengerOfSameVehicle(e));
      if (hit != null) {
         double de = hit.getLocation().distanceTo(eye);
         if (Double.isNaN(best) || de < best) {
            best = de;
         }
      }
      return best;
   }
}
