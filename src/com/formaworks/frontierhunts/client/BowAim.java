package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.archery.ArrowFlight;
import com.formaworks.frontierhunts.archery.BowBallistics;
import com.formaworks.frontierhunts.expedition.HuntProjectile;
import com.formaworks.frontierhunts.hunting.DeerAnatomy;
import com.formaworks.frontierhunts.hunting.FieldArrow;
import com.formaworks.frontierhunts.hunting.TrackClue;
import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * [archery2] Where the arrow you are holding at full draw will strike, right now: the arrow's real flight
 * ({@link ArrowFlight}, the server's own integrator, launch maths and spawn point) flown through the client's world -
 * blocks with the same collision test, animals with the same anatomy / box test, the synced wind - from the exact eye
 * position and rotation the server will launch from. The HUD draws one dot there (traditional bows), so the dot on an
 * animal is a hit on that spot at any distance.
 *
 * <p>Computed at most once per frame and only while a traditional bow is fully drawn; about 15-40 short segment
 * tests, the same work a flying arrow does each tick.
 */
final class BowAim {
   /** Longest flight followed (ticks): far beyond any ethical bow shot (~110 m for the slowest bow). */
   private static final int MAX_TICKS = 60;

   private static long frame = Long.MIN_VALUE;
   private static float lastYaw;
   private static float lastPitch;
   private static Vec3 lastEye = Vec3.ZERO;
   private static Vec3 impact;
   private static Entity impactEntity;

   private BowAim() {
   }

   /** Server-side arrow entity of this profile: FieldArrow (quadratic drag) or HuntProjectile. */
   private static boolean fieldArrow(BowBallistics.Profile p) {
      return p.drag() == BowBallistics.Drag.QUADRATIC;
   }

   /**
    * Impact point of an arrow released now from {@code p} at full draw, or null if it flies on past the range followed.
    * {@code frameId} lets callers share one solve per frame.
    */
   static Vec3 impact(LocalPlayer p, BowBallistics.Profile profile, long frameId) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null || p == null || profile == null) {
         return null;
      }
      // the server launches from its copy of the player: the tick position (not the render-interpolated one) and the
      // current rotation, which BowSway sends right before the release
      Vec3 eye = new Vec3(p.getX(), p.getEyeY(), p.getZ());
      float yaw = p.getYRot();
      float pitch = p.getXRot();
      if (frameId == frame && yaw == lastYaw && pitch == lastPitch && eye.equals(lastEye)) {
         return impact;
      }
      frame = frameId;
      lastYaw = yaw;
      lastPitch = pitch;
      lastEye = eye;
      impact = solve(level, p, profile, eye, pitch, yaw);
      return impact;
   }

   /** The entity the last solve ended on (null: a block or nothing). */
   static Entity impactEntity() {
      return impactEntity;
   }

   private static Vec3 solve(ClientLevel level, LocalPlayer p, BowBallistics.Profile profile, Vec3 eye, float pitch, float yaw) {
      impactEntity = null;
      double[] v = ArrowFlight.launch(pitch, yaw, ArrowFlight.speed(profile, 1.0F));
      double windE = 0.0;
      double windS = 0.0;
      var snap = FrontierClient.state;
      if (snap != null) {
         windE = snap.windEast();
         windS = snap.windSouth();
      }
      boolean fa = fieldArrow(profile);
      Entity[] hitEntity = new Entity[1];
      ArrowFlight.Collider c = new ArrowFlight.Collider() {
         @Override
         public double hit(double ax, double ay, double az, double bx, double by, double bz) {
            Vec3 a = new Vec3(ax, ay, az);
            Vec3 b = new Vec3(bx, by, bz);
            BlockHitResult block = level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            Vec3 end = block.getType() == HitResult.Type.MISS ? b : block.getLocation();
            double seg = a.distanceTo(b);
            double best = block.getType() == HitResult.Type.MISS ? Double.NaN : (seg < 1.0E-9 ? 0.0 : a.distanceTo(end) / seg);
            double bestSq = a.distanceToSqr(end);
            Entity bestEntity = null;
            AABB box = new AABB(a, end).inflate(4.0);
            List<Entity> list = level.getEntities(p, box, e -> candidate(e, p, fa));
            for (Entity e : list) {
               Vec3 at = null;
               if (e instanceof Whitetail deer) {
                  if (deer.downed() && fa) {
                     continue;
                  }
                  try {
                     DeerAnatomy.Contact contact = DeerAnatomy.intersect(a, end, deer);
                     if (contact != null) {
                        at = a.lerp(end, contact.fraction());
                     }
                  } catch (RuntimeException ex) {
                     at = e.getBoundingBox().clip(a, end).orElse(null);
                  }
               } else {
                  Optional<Vec3> o = e.getBoundingBox().inflate(fa ? 0.03 : 0.025).clip(a, end);
                  at = o.orElse(null);
               }
               if (at != null) {
                  double d = a.distanceToSqr(at);
                  if (d < bestSq) {
                     bestSq = d;
                     bestEntity = e;
                     best = seg < 1.0E-9 ? 0.0 : Math.sqrt(d) / seg;
                  }
               }
            }
            if (!Double.isNaN(best)) {
               hitEntity[0] = bestEntity;
            }
            return best;
         }

         @Override
         public boolean water(double x, double y, double z) {
            return level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER);
         }
      };
      ArrowFlight.Impact hit = ArrowFlight.fly(profile, eye.x, eye.y - ArrowFlight.spawnDrop(profile), eye.z, v, windE, windS, c, MAX_TICKS);
      if (!hit.hit()) {
         return null;
      }
      impactEntity = hitEntity[0];
      return new Vec3(hit.x(), hit.y(), hit.z());
   }

   /** The same entities each arrow entity can strike (FieldArrow: anything pickable but clues; HuntProjectile: living things). */
   private static boolean candidate(Entity e, LocalPlayer self, boolean fieldArrow) {
      if (e == self || e.isSpectator() || !e.isAlive() || KillCamClient.isDouble(e)) {
         return false;
      }
      if (e instanceof Player other && !self.canHarmPlayer(other)) {
         return false;
      }
      if (fieldArrow) {
         return e.isPickable() && !(e instanceof TrackClue) && !(e instanceof FieldArrow) && !(e instanceof HuntProjectile);
      }
      return e instanceof LivingEntity;
   }
}
