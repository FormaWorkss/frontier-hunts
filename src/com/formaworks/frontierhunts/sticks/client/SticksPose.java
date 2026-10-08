package com.formaworks.frontierhunts.sticks.client;

import com.formaworks.frontierhunts.sticks.ShootingSticks;
import com.formaworks.frontierhunts.sticks.ShootingSticks.Height;
import com.formaworks.frontierhunts.sticks.ShootingSticksEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * [sticks] Third person: a player with a gun on shooting sticks. The gun is drawn along the trigger arm (that's how this
 * mod holds guns in third person), so pointing that arm from its shoulder at the yoke lays the gun in the cradle from
 * any side and at any of the three heights; the other arm reaches for the forend just behind the yoke. Kneeling puts
 * the trailing knee on the ground and the leading foot out front; sitting is the seated pose with the legs out; standing
 * opens the stance a little. Everything blends in as the shooter settles and out as they leave. The sleeves, trouser
 * legs and jacket layers follow their parts as vanilla does.
 */
public final class SticksPose {
   /** model units per block (the player model is drawn at 15/16) */
   private static final float K = 16.0F / 0.9375F;
   /** the gun's bore sits this far (blocks) above the line of the arm holding it */
   private static final float GUN_ABOVE = 0.18F;

   private SticksPose() {
   }

   public static void apply(PlayerModel<?> m, LivingEntity e, float ageInTicks) {
      if (!(e.getVehicle() instanceof ShootingSticksEntity s)) {
         return;
      }
      float pt = Math.clamp(ageInTicks - e.tickCount, 0.0F, 1.0F);
      float b = s.settled(pt);
      if (b <= 0.0F) {
         return;
      }
      Height h = s.height();
      float yaw = (float)Math.toRadians(Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot));
      float fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
      float rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
      float contact = SticksMesh.contact(ShootingSticksRenderer.legLength(s, pt), ShootingSticksRenderer.splay(s, pt));
      double dx = s.getX() - Mth.lerp(pt, e.xo, e.getX());
      double dy = s.getY() + contact - Mth.lerp(pt, e.yo, e.getY());
      double dz = s.getZ() - Mth.lerp(pt, e.zo, e.getZ());
      float fwd = (float)(dx * fx + dz * fz);
      float right = (float)(dx * rx + dz * rz);
      // model space: +X the player's left, +Y down from 1.5 blocks up, -Z forward
      float tx = -right * K;
      float ty = 24.0F - (float)dy * K;
      float tz = -fwd * K;
      aim(m.rightArm, tx, ty + GUN_ABOVE * K, tz, b);
      aim(m.leftArm, tx, ty + (GUN_ABOVE + 0.02F) * K, tz + 0.12F * K, b);
      switch (h) {
         case STANDING -> {
            pose(m.rightLeg, 0.1F, 0.06F, 0.05F, b);
            pose(m.leftLeg, -0.16F, -0.08F, -0.06F, b);
         }
         case KNEELING -> {
            // trailing (right) knee down behind, leading (left) foot planted ahead
            pose(m.rightLeg, 1.16F, 0.1F, 0.03F, b);
            pose(m.leftLeg, -1.16F, -0.12F, -0.04F, b);
         }
         case SITTING -> {
            // seated on the ground (the vehicle asks for the riding pose), knees a touch apart
            pose(m.rightLeg, -1.38F, 0.36F, 0.05F, b);
            pose(m.leftLeg, -1.38F, -0.36F, -0.05F, b);
         }
      }
      m.leftSleeve.copyFrom(m.leftArm);
      m.rightSleeve.copyFrom(m.rightArm);
      m.leftPants.copyFrom(m.leftLeg);
      m.rightPants.copyFrom(m.rightLeg);
      m.jacket.copyFrom(m.body);
   }

   /** Points an arm (it hangs along +Y from its pivot) at a model-space point. */
   private static void aim(ModelPart arm, float tx, float ty, float tz, float b) {
      float vx = tx - arm.x, vy = ty - arm.y, vz = tz - arm.z;
      float len = Mth.sqrt(vx * vx + vy * vy + vz * vz);
      if (len < 1.0E-3F) {
         return;
      }
      vx /= len;
      vy /= len;
      vz /= len;
      float xr = -(float)Math.acos(Math.clamp(vy, -1.0F, 1.0F));
      float yr = (float)Math.atan2(-vx, -vz);
      arm.xRot = Mth.lerp(b, arm.xRot, xr);
      arm.yRot = Mth.lerp(b, arm.yRot, yr);
      arm.zRot = Mth.lerp(b, arm.zRot, 0.0F);
   }

   private static void pose(ModelPart leg, float x, float y, float z, float b) {
      leg.xRot = Mth.lerp(b, leg.xRot, x);
      leg.yRot = Mth.lerp(b, leg.yRot, y);
      leg.zRot = Mth.lerp(b, leg.zRot, z);
   }
}
