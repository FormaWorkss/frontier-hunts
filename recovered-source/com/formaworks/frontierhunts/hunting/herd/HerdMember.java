package com.formaworks.frontierhunts.hunting.herd;

import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * [herds] Per-animal social state (lives in the animal's DeerRoutine, not saved: the group id is in the animal's own
 * data, everything else is rebuilt). Holds the cached group and the animal it follows, and - for any animal that others
 * follow - a short breadcrumb trail of where it walked, so followers come in on the same line, one behind another.
 */
public final class HerdMember {
   final Whitetail deer;
   UUID groupId;
   HerdGroup group;
   long nextCheck;
   long nextRegroup;
   long loadedAt = Long.MIN_VALUE;

   // who this animal follows (null = it leads or is alone) and where in the line it walks
   UUID followId;
   Whitetail follow;
   long nextResolve;
   /** walking position behind the followed animal (1 = right behind); 0 = young following its mother */
   int rank;
   int siblings;
   final double personal;
   final double latPhase;
   final double latRate;

   // breadcrumbs of this animal's own walk (allocated when someone follows it)
   private double[] cx;
   private double[] cy;
   private double[] cz;
   private int head = -1;
   private int count;

   // sentinel: keeps its head up while the others graze (decision held until watchUntil)
   boolean watching;
   long watchUntil;

   // travel state
   boolean traveling;
   private double restX = Double.NaN;
   private double restZ;
   private double lastX = Double.NaN;
   private double lastZ;
   private long stillSince;
   long travelStart;

   HerdMember(Whitetail deer) {
      this.deer = deer;
      UUID id = deer.getUUID();
      this.personal = 0.8 + 0.5 * HerdTuning.frac(id, HerdTuning.SALT_SPACING);
      this.latPhase = HerdTuning.frac(id, HerdTuning.SALT_LATERAL) * Math.PI * 2.0;
      this.latRate = 1.0 / (130.0 + 120.0 * HerdTuning.frac(id, HerdTuning.SALT_LATERAL + 11));
   }

   public static HerdMember create(Whitetail deer) {
      return new HerdMember(deer);
   }

   public HerdGroup group() {
      return this.group;
   }

   public boolean traveling() {
      return this.traveling;
   }

   /** Called every server tick: breadcrumbs, travelling or settled. Cheap: a couple of distance checks. */
   void tick(long now) {
      double x = this.deer.getX();
      double z = this.deer.getZ();
      if (Double.isNaN(this.restX)) {
         this.restX = x;
         this.restZ = z;
         this.lastX = x;
         this.lastZ = z;
         this.stillSince = now;
      }

      if (this.cx != null) {
         int h = this.head;
         if (h < 0) {
            this.push(x, this.deer.getY(), z);
         } else {
            double dx = x - this.cx[h];
            double dz = z - this.cz[h];
            if (dx * dx + dz * dz >= HerdTuning.CRUMB_STEP * HerdTuning.CRUMB_STEP) {
               this.push(x, this.deer.getY(), z);
            }
         }
      }

      if ((now + this.deer.getId()) % 10L == 0L) {
         double mx = x - this.lastX;
         double mz = z - this.lastZ;
         if (mx * mx + mz * mz > 0.64) {
            this.lastX = x;
            this.lastZ = z;
            this.stillSince = now;
         }

         double rx = x - this.restX;
         double rz = z - this.restZ;
         boolean routineLeg = this.deer.routine().travelling();
         if (!routineLeg && this.deer.routine().localTask()) {
            // feeding steps, drinking, settling into a bed: busy on the spot, not leading anyone anywhere
            this.traveling = false;
            this.settle(x, z);
         } else if (!this.traveling && (routineLeg || rx * rx + rz * rz > HerdTuning.TRAVEL_START * HerdTuning.TRAVEL_START)) {
            this.traveling = true;
            this.travelStart = now;
         } else if (this.traveling && !routineLeg && now - this.stillSince >= 60L) {
            this.traveling = false;
            this.settle(x, z);
         } else if (!this.traveling && now - this.stillSince >= 60L) {
            this.settle(x, z);
         }
      }
   }

   /** Standing (or busy) here: the next walk is measured, and its breadcrumbs recorded, from this spot. */
   private void settle(double x, double z) {
      this.restX = x;
      this.restZ = z;
      if (this.cx != null) {
         this.head = -1;
         this.count = 0;
      }
   }

   /** Someone follows this animal: start keeping breadcrumbs. */
   void keepCrumbs() {
      if (this.cx == null) {
         this.cx = new double[HerdTuning.CRUMBS];
         this.cy = new double[HerdTuning.CRUMBS];
         this.cz = new double[HerdTuning.CRUMBS];
         this.head = -1;
         this.count = 0;
      }
   }

   private void push(double x, double y, double z) {
      this.head = (this.head + 1) % HerdTuning.CRUMBS;
      this.cx[this.head] = x;
      this.cy[this.head] = y;
      this.cz[this.head] = z;
      if (this.count < HerdTuning.CRUMBS) {
         this.count++;
      }
   }

   /**
    * The point {@code along} blocks back along this animal's own path from where it stands now, pushed {@code side}
    * blocks to the right of the line (negative: left). Falls back to the oldest crumb when the walk so far is shorter.
    */
   Vec3 behind(double along, double side) {
      double px = this.deer.getX();
      double py = this.deer.getY();
      double pz = this.deer.getZ();
      double dirX = 0.0;
      double dirZ = 0.0;
      double left = along;
      if (this.cx != null && this.count > 0) {
         for (int k = 0; k < this.count; k++) {
            int i = Math.floorMod(this.head - k, HerdTuning.CRUMBS);
            double sx = this.cx[i] - px;
            double sz = this.cz[i] - pz;
            double len = Math.sqrt(sx * sx + sz * sz);
            if (len < 1.0E-4) {
               continue;
            }

            dirX = -sx / len;
            dirZ = -sz / len;
            if (len >= left) {
               double f = left / len;
               px += sx * f;
               pz += sz * f;
               py = Mth.lerp(f, py, this.cy[i]);
               left = 0.0;
               break;
            }

            left -= len;
            px = this.cx[i];
            py = this.cy[i];
            pz = this.cz[i];
         }
      }

      if (left > 0.0 && dirX == 0.0 && dirZ == 0.0) {
         // no walk recorded yet: line up behind the way the animal faces
         float yaw = this.deer.getYRot() * (float)(Math.PI / 180.0);
         dirX = -Math.sin(yaw);
         dirZ = Math.cos(yaw);
         px -= dirX * left;
         pz -= dirZ * left;
      }

      if (side != 0.0 && (dirX != 0.0 || dirZ != 0.0)) {
         px += -dirZ * side;
         pz += dirX * side;
      }

      return new Vec3(px, py, pz);
   }

   /** Side drift for this follower now: slow, smooth, different per animal (never a marching line). */
   double side(long now, double amplitude) {
      return amplitude * Math.sin(this.latPhase + now * this.latRate);
   }

   void forget() {
      this.groupId = null;
      this.group = null;
      this.followId = null;
      this.follow = null;
      this.rank = 0;
   }

   /** The animal this one follows, if it is loaded, alive, on its feet nearby and in this level. */
   Whitetail followTarget(ServerLevel level, long now) {
      if (this.followId == null) {
         this.follow = null;
         return null;
      }

      Whitetail f = this.follow;
      if (f == null || f.isRemoved() || !this.followId.equals(f.getUUID()) || now >= this.nextResolve) {
         this.nextResolve = now + 40L;
         f = level.getEntity(this.followId) instanceof Whitetail w ? w : null;
         this.follow = f;
      }

      if (f == null || f.isRemoved() || f.downed() || f.level() != level || f.distanceToSqr(this.deer) > HerdTuning.FOLLOW_MAX * HerdTuning.FOLLOW_MAX) {
         return null;
      }

      return f;
   }
}
