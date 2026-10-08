package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.hunting.ArrowSupply;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.formaworks.frontierhunts.hunting.DeerAnatomy;
import com.formaworks.frontierhunts.hunting.DeerOrgan;
import com.formaworks.frontierhunts.hunting.FieldArrow;
import com.formaworks.frontierhunts.hunting.HuntEntities;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.killcam.KillCamLength;
import com.formaworks.frontierhunts.killcam.KillCamNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One kill cam replay: timeline, virtual (slow-motion) clock and camera choreography. Pure presentation; everything it
 * shows was authorised by the server's shot payload.
 */
final class KillCamReplay {
   enum Phase {
      FLIGHT,
      IMPACT,
      XRAY,
      DROP,
      RETURN,
      ABORT,
      DONE
   }

   record Pose(Vec3 pos, float yaw, float pitch, float roll, float fov) {
   }

   private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
   static final float LAUNCH = 0.3F;

   final int shotId;
   final byte weapon;
   final boolean bow;
   final int targetId;
   final boolean deer;
   final ClientLevel level;
   final float scale;
   final boolean xray;
   KillCamNetwork.Shot shot;
   boolean confirmed;
   KillCamPath path;
   private Vec3 pathSwap = Vec3.ZERO;
   private Vec3 poseSwap = Vec3.ZERO;
   float tFlight;
   float tImpact;
   float tXray;
   float tDrop;
   float tReturn;
   Phase phase = Phase.FLIGHT;
   double tp;
   double total;
   double u;
   private double hold;
   double v;
   private double vRate;
   int vImpact = -1;
   private float flightRate;
   final int side;
   private Pose targetPose;
   private Pose fromPose;
   private double poseBlend = 1.0;
   Pose lastPose;
   private Pose returnFrom;
   KillCamStandIn standIn;
   private FieldArrow arrowDouble;
   /** [archery2] Bow shots: the chase pose held when the arrow strikes (the post-hit orbit glides out of it). */
   private Pose heldPose;
   /** [archery2] Once the arrow is in: rigid attachment to the double's root (model-space tip and direction), or null. */
   private org.joml.Vector3f stuckLocal;
   private org.joml.Vector3f stuckLocalDir;
   private float stuckScale = 1.0F;
   LivingEntity real;
   boolean showReal;
   final float bbWidth;
   final float bbHeight;
   float flash;
   boolean skipped;
   private final double[] events = new double[8];

   private KillCamReplay(KillCamNetwork.Shot s, ClientLevel level, LivingEntity target) {
      this.shotId = s.shotId();
      this.weapon = s.weapon();
      this.bow = s.weapon() == KillCamNetwork.ARROW || s.weapon() == KillCamNetwork.BOLT;
      this.targetId = s.target();
      this.deer = target instanceof Whitetail;
      this.level = level;
      this.real = target;
      this.bbWidth = target.getBbWidth();
      this.bbHeight = target.getBbHeight();
      KillCamLength len = HuntConfig.KILLCAM_LENGTH.get();
      this.scale = len.scale;
      this.xray = HuntConfig.KILLCAM_XRAY.get();
      this.side = (s.shotId() & 1) == 0 ? 1 : -1;
      java.util.Arrays.fill(this.events, -1.0);
      this.apply(s, true);
   }

   static KillCamReplay start(KillCamNetwork.Shot s, ClientLevel level, LivingEntity target) {
      KillCamReplay r = new KillCamReplay(s, level, target);
      r.standIn = KillCamStandIn.create(target, level);
      if (r.standIn == null) {
         return null;
      }
      r.standIn.place(r.snapPos(), s.bodyYaw(), s.headYaw(), s.pitch());
      if (r.bow) {
         r.makeArrow(s);
      }
      return r;
   }

   private void makeArrow(KillCamNetwork.Shot s) {
      try {
         FieldArrow a = new FieldArrow((EntityType<? extends FieldArrow>)HuntEntities.ARROW.get(), this.level);
         ArrowTip tip = s.tip() >= 0 && s.tip() < ArrowTip.values().length ? ArrowTip.values()[s.tip()] : ArrowTip.FIXED_BROADHEAD;
         a.setShot(new ArrowSupply.Shot(tip, s.primitive()));
         a.setId(KillCamClient.nextDoubleId());
         a.setSilent(true);
         a.noPhysics = true;
         Vec3 p = this.path.at(0.0);
         a.setPos(p.x, p.y, p.z);
         this.level.addEntity(a);
         this.arrowDouble = a;
      } catch (RuntimeException | LinkageError ex) {
         this.arrowDouble = null;
      }
   }

   Entity arrowDouble() {
      return this.arrowDouble;
   }

   /** New data for this shot: the predicted start, or the real server result replacing the prediction. */
   void apply(KillCamNetwork.Shot s, boolean first) {
      Vec3 oldPos = first ? null : this.projectilePos();
      Vec3 oldSnap = first ? null : this.snapPos();
      this.shot = s;
      this.confirmed = s.confirmed();
      this.path = new KillCamPath(s.origin(), s.path(), s.impact());
      if (first) {
         float base = (float)Math.clamp(0.9 + this.path.length / 300.0, 1.0, 1.75) * this.scale;
         if (this.bow) {
            base += 0.2F * this.scale;
         }
         if (!s.confirmed()) {
            base = Math.max(base, s.flightTicks() / 20.0F + 0.4F);
         }
         this.tFlight = Math.min(base, 3.2F);
         this.tImpact = 0.22F * this.scale;
         this.tXray = this.xray ? 0.82F * this.scale : 0.0F;
         this.tDrop = 1.08F * this.scale;
         this.tReturn = 0.42F + 0.1F * this.scale;
      } else {
         this.pathSwap = oldPos.subtract(this.projectilePos()).add(this.pathSwap);
         this.poseSwap = oldSnap.subtract(this.snapPos()).add(this.poseSwap);
         if (this.standIn != null && this.phase == Phase.FLIGHT) {
            this.standIn.place(this.snapPos().add(this.poseSwap), s.bodyYaw(), s.headYaw(), s.pitch());
         }
      }
      this.flightRate = (float)Math.clamp(s.flightTicks() / (this.tFlight * 20.0), 0.03, 0.9);
      Pose was = first ? null : this.impactPose();
      this.targetPose = this.computeImpactPose();
      if (was != null) {
         // the real result moved the framing a little: glide to it instead of jumping
         this.fromPose = was;
         this.poseBlend = 0.0;
      }
   }

   /** The impact framing, gliding from the predicted one to the confirmed one after the real result arrives. */
   private Pose impactPose() {
      if (this.targetPose == null) {
         return null;
      }
      return this.fromPose == null || this.poseBlend >= 1.0 ? this.targetPose : blend(this.fromPose, this.targetPose, smooth(this.poseBlend));
   }

   // ------------------------------------------------------------------ timeline

   void advance(double dt) {
      this.total += dt;
      this.tp += dt;
      this.flash = Math.max(0.0F, this.flash - (float)dt * 7.0F);
      this.poseBlend = Math.min(1.0, this.poseBlend + dt / 0.4);
      double decay = Math.exp(-dt * 9.0);
      this.pathSwap = this.pathSwap.scale(decay);
      this.poseSwap = this.poseSwap.scale(decay);
      switch (this.phase) {
         case FLIGHT -> {
            double rate = 1.0;
            if (!this.confirmed && this.u > 0.72) {
               rate = 0.12;
               this.hold += dt;
               if (this.hold > 2.5) {
                  this.abort();
                  return;
               }
            }
            this.u = Math.min(1.0, this.u + dt * rate / this.tFlight);
            if (!this.confirmed) {
               this.u = Math.min(this.u, 0.97);
            }
            this.vRate = this.flightRate * (rate < 1.0 ? 0.25 : 1.0);
            if (this.u >= 1.0) {
               this.enter(Phase.IMPACT);
            }
         }
         case IMPACT -> {
            this.vRate = 0.06;
            if (this.tp >= this.tImpact) {
               this.enter(this.xray ? Phase.XRAY : Phase.DROP);
            }
         }
         case XRAY -> {
            this.vRate = 0.2;
            if (this.tp >= this.tXray) {
               this.enter(Phase.DROP);
            }
         }
         case DROP -> {
            this.vRate = Mth.lerp(smooth(this.tp / this.tDrop), 0.45, 1.3);
            if (this.tp >= this.tDrop) {
               this.enter(Phase.RETURN);
            }
         }
         case RETURN -> {
            this.vRate = 1.0;
            if (!this.showReal && this.tp >= this.tReturn * 0.5) {
               this.revealReal();
            }
            if (this.tp >= this.tReturn) {
               this.enter(Phase.DONE);
            }
         }
         case ABORT -> {
            this.vRate = this.flightRate;
            if (this.tp >= 0.2) {
               this.enter(Phase.DONE);
            }
         }
         case DONE -> {
         }
      }
      this.v += this.vRate * dt * 20.0;
   }

   void enter(Phase next) {
      if (this.phase == Phase.IMPACT && next != Phase.IMPACT) {
         this.handOverArrow(); // [archery2] the deer carries the arrow from here (or the arrow stays fixed to the body)
      }
      if (next == Phase.IMPACT && this.vImpact < 0) {
         this.vImpact = (int)Math.floor(this.v);
         if (this.standIn != null) {
            this.standIn.hit(this.vImpact, this.real);
         }
         // [archery2] the arrow stays: it sinks in where it really struck and stays there (see driveDoubles)
         this.heldPose = this.lastPose;
         this.flash = 1.0F;
         KillCamClient.impact(this);
      }
      if (next == Phase.RETURN) {
         this.returnFrom = this.lastPose;
         this.removeArrow();
         if (!this.deer && this.standIn != null && this.phaseBefore(Phase.RETURN)) {
            KillCamClient.poof(this.standIn.entity);
            this.standIn.remove();
         }
      }
      if (next == Phase.XRAY) {
         KillCamClient.xrayStart(this);
      }
      this.phase = next;
      this.tp = 0.0;
   }

   private boolean phaseBefore(Phase p) {
      return this.phase.ordinal() < p.ordinal();
   }

   void abort() {
      if (this.phase == Phase.FLIGHT) {
         KillCamClient.stopSounds();
         this.enter(Phase.ABORT);
      }
   }

   /** Any key or click: leave from wherever we are. Before the confirmed hit this backs out without showing the result. */
   void skip() {
      if (this.skipped) {
         return;
      }
      this.skipped = true;
      if (this.phase == Phase.ABORT || this.phase == Phase.DONE) {
         return;
      }
      if (!this.confirmed) {
         this.phase = Phase.FLIGHT;
         this.abort();
      } else if (this.phase.ordinal() < Phase.RETURN.ordinal()) {
         if (this.vImpact < 0) {
            this.vImpact = (int)Math.floor(this.v);
         }
         this.tReturn = 0.3F;
         this.enter(Phase.RETURN);
      }
   }

   void revealReal() {
      this.showReal = true;
      if (this.standIn != null) {
         this.standIn.remove();
      }
   }

   /** [archery2] Arrow head penetration past the hit point once it is in (metres). */
   static final double PENETRATION = 0.2;

   /** Distance from the arrow double's position (model centre) to its head's point. */
   private double tipOffset() {
      int t = this.shot.tip();
      ArrowTip tip = t >= 0 && t < ArrowTip.values().length ? ArrowTip.values()[t] : ArrowTip.FIXED_BROADHEAD;
      return FieldArrowRenderer.pointReach(tip, this.weapon == KillCamNetwork.BOLT);
   }

   /** Where the arrow's point is once it has struck (sinking in over the first few frames of the impact beat). */
   private Vec3 stuckTip(double sink) {
      return this.impact().add(this.stuckDir().scale(PENETRATION * sink));
   }

   private Vec3 stuckDir() {
      Vec3 d = this.dir();
      return d.lengthSqr() < 1.0E-8 ? this.path.tangent(1.0) : d.normalize();
   }

   /**
    * [archery2] End of the impact beat: deer carry the arrow as their own impact mark from here (bone-anchored, so it
    * rides the collapse exactly where it went in); anything else, or a deer whose hit has not synced yet, keeps the
    * replay's arrow, fixed to the double's body (heading and death roll included).
    */
   private void handOverArrow() {
      if (this.arrowDouble == null) {
         return;
      }
      if (this.standIn != null && this.standIn.arrowMark(this.real, true)) {
         this.removeArrow();
         return;
      }
      if (this.standIn != null && !this.standIn.entity.isRemoved()) {
         LivingEntity e = this.standIn.entity;
         org.joml.Quaternionf root = this.rootRotation(e, 0.0F);
         org.joml.Quaternionf inv = root.conjugate(new org.joml.Quaternionf());
         Vec3 tip = this.stuckTip(1.0).subtract(e.position());
         Vec3 d = this.stuckDir();
         this.stuckScale = Math.max(0.05F, e.getScale());
         this.stuckLocal = inv.transform(new org.joml.Vector3f((float)tip.x, (float)tip.y, (float)tip.z)).div(this.stuckScale);
         this.stuckLocalDir = inv.transform(new org.joml.Vector3f((float)d.x, (float)d.y, (float)d.z));
      }
   }

   /** The double's root rotation as its renderer applies it: YP(180 - body yaw), then the vanilla death roll. */
   private org.joml.Quaternionf rootRotation(LivingEntity e, float partial) {
      org.joml.Quaternionf q = new org.joml.Quaternionf().rotationY((float)Math.toRadians(180.0F - e.yBodyRot));
      if (!this.deer && e.deathTime > 0) {
         float f = Mth.sqrt((e.deathTime + partial - 1.0F) / 20.0F * 1.6F);
         q.rotateZ((float)Math.toRadians(Math.min(f, 1.0F) * 90.0F));
      }
      return q;
   }

   private void removeArrow() {
      if (this.arrowDouble != null) {
         if (!this.arrowDouble.isRemoved()) {
            this.level.removeEntity(this.arrowDouble.getId(), Entity.RemovalReason.DISCARDED);
         }
         this.arrowDouble = null;
      }
   }

   void dispose() {
      this.removeArrow();
      if (this.standIn != null) {
         this.standIn.remove();
      }
   }

   /** Fires once when {@code key} first becomes true (sound cues). */
   boolean once(int key, boolean condition) {
      if (condition && this.events[key] < 0.0) {
         this.events[key] = this.total;
         return true;
      }
      return false;
   }

   // ------------------------------------------------------------------ projectile + animal state

   /**
    * Flight progress (0..1 of the path) for timeline progress {@code u}: the projectile rips away from the muzzle, eases
    * down, and covers the last few metres at a slow constant crawl so you can watch it strike.
    */
   double sOf(double u) {
      return approach(u, this.path.length);
   }

   static final double FINAL = 0.3;

   static double approach(double u, double length) {
      double x = Math.clamp(u, 0.0, 1.0);
      double last = Math.min(3.0, 0.22 * length);
      double head = 1.0 - FINAL;
      double a = length - last;
      double m1 = last / FINAL;
      double d;
      if (x >= head) {
         d = a + (x - head) * m1;
      } else {
         // quadratic ease (monotone): starts fast, arrives at the crawl speed m1
         double m0 = 2.0 * a / head - m1;
         double t = x / head;
         d = head * (m0 * t + (m1 - m0) * 0.5 * t * t);
      }
      return Math.clamp(d / Math.max(1.0E-3, length), 0.0, 1.0);
   }

   boolean projectileVisible() {
      return this.phase == Phase.FLIGHT || this.phase == Phase.ABORT;
   }

   Vec3 projectilePos() {
      return this.path.at(this.sOf(this.u)).add(this.pathSwap);
   }

   Vec3 projectileDir() {
      return this.path.tangent(this.sOf(this.u));
   }

   Vec3 snapPos() {
      return this.shot.pose();
   }

   Vec3 impact() {
      return this.shot.impact();
   }

   Vec3 dir() {
      return this.shot.dir();
   }

   Vec3 center() {
      return this.snapPos().add(0.0, this.bbHeight * 0.55, 0.0);
   }

   DeerAnatomy.Region region() {
      int r = this.shot.region();
      return r >= 0 && r < DeerAnatomy.Region.values().length ? DeerAnatomy.Region.values()[r] : null;
   }

   DeerOrgan organ() {
      int o = this.shot.organ();
      return o >= 0 && o < DeerOrgan.values().length ? DeerOrgan.values()[o] : null;
   }

   /** Per-frame: move the doubles and return the partial tick the stand-in renders with. */
   void driveDoubles() {
      if (this.standIn != null && !this.standIn.entity.isRemoved()) {
         Vec3 snap = this.snapPos();
         Vec3 vel = this.shot.poseVel();
         Vec3 pos;
         float yaw = this.shot.bodyYaw();
         if (this.phase == Phase.FLIGHT || this.phase == Phase.ABORT) {
            double remaining = (1.0 - this.u) * this.tFlight * 20.0 * this.flightRate;
            pos = snap.subtract(vel.scale(remaining)).add(this.poseSwap);
         } else if (this.phase == Phase.DROP || this.phase == Phase.RETURN) {
            double w = this.phase == Phase.RETURN ? 1.0 : smooth(this.tp / this.tDrop);
            pos = snap;
            if (this.real != null && !this.real.isRemoved() && this.real.position().distanceToSqr(snap) < 9.0) {
               pos = snap.lerp(this.real.position(), w);
               yaw = yaw + Mth.wrapDegrees(this.real.yBodyRot - yaw) * (float)w;
            }
         } else {
            pos = snap;
         }
         this.standIn.moveTo(pos, yaw);
      }
      if (this.arrowDouble != null) {
         // [archery2] per frame on the replay clock: in flight the head's point rides the path and the shaft lies along
         // the flight (renderer: head forward, nock back); once in, it stays where it struck
         Vec3 tip;
         Vec3 d;
         if (this.phase == Phase.FLIGHT || this.phase == Phase.ABORT) {
            tip = this.projectilePos();
            d = this.projectileDir();
         } else if (this.stuckLocal != null && this.standIn != null && !this.standIn.entity.isRemoved()) {
            if (this.deer && this.standIn.arrowMark(this.real, true)) {
               // the hit synced late: the deer carries the arrow from now on
               this.removeArrow();
               return;
            }
            LivingEntity e = this.standIn.entity;
            org.joml.Quaternionf root = this.rootRotation(e, KillCamClient.standInPartialNow());
            org.joml.Vector3f t = root.transform(new org.joml.Vector3f(this.stuckLocal)).mul(this.stuckScale);
            org.joml.Vector3f dd = root.transform(new org.joml.Vector3f(this.stuckLocalDir));
            tip = e.position().add(t.x, t.y, t.z);
            d = new Vec3(dd.x, dd.y, dd.z);
         } else {
            double sink = this.phase == Phase.IMPACT ? smooth(this.tp / 0.07) : 1.0;
            tip = this.stuckTip(sink);
            d = this.stuckDir();
         }
         if (d.lengthSqr() < 1.0E-8) {
            d = new Vec3(0.0, 0.0, 1.0);
         }
         d = d.normalize();
         Vec3 p = tip.subtract(d.scale(this.tipOffset()));
         FieldArrow a = this.arrowDouble;
         a.setPos(p.x, p.y, p.z);
         a.xo = a.xOld = p.x;
         a.yo = a.yOld = p.y;
         a.zo = a.zOld = p.z;
         a.setDeltaMovement(d.scale(0.05)); // the renderer orients a flying arrow along its velocity
         float yaw = (float)Math.toDegrees(Math.atan2(d.x, d.z));
         float pitch = (float)Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()));
         a.setYRot(yaw);
         a.setXRot(pitch);
         a.yRotO = yaw;
         a.xRotO = pitch;
      }
   }

   float standInPartial() {
      return this.standIn == null ? 0.0F : this.standIn.drive(this.v);
   }

   // ------------------------------------------------------------------ camera

   Pose pose(Pose eye, float playerFov) {
      Pose p = switch (this.phase) {
         case FLIGHT, ABORT -> {
            Pose chase = this.chase();
            Pose impact = this.bow ? null : this.impactPose(); // [archery2] bows: straight on to the hit
            if (impact != null) {
               double w = smooth((this.u - 0.45) / 0.27);
               chase = blend(chase, impact, w);
               chase = this.unblock(chase, this.projectilePos());
            }
            yield this.total < LAUNCH ? blend(eye, chase, smooth(this.total / LAUNCH)) : chase;
         }
         case IMPACT -> this.shake(this.bow && this.heldPose != null ? this.heldPose : (this.impactPose() == null ? this.lastPose : this.impactPose()));
         case XRAY -> this.orbit(smooth(this.tp / Math.max(0.01, this.tXray)), 0.0);
         case DROP -> this.orbit(this.xray ? 1.0 : 0.0, smooth(this.tp / this.tDrop));
         case RETURN -> blend(this.returnFrom == null ? eye : this.returnFrom, eye, smoother(this.tp / this.tReturn));
         case DONE -> eye;
      };
      if (this.phase == Phase.RETURN) {
         p = new Pose(p.pos, p.yaw, p.pitch, p.roll, (float)Mth.lerp(smoother(this.tp / this.tReturn), this.returnFrom == null ? playerFov : this.returnFrom.fov, playerFov));
      }
      this.lastPose = p;
      return p;
   }

   /** [archery2] Bow shots: how far short of the hit the camera comes to rest, so the arrow is seen going in. */
   private static final double BOW_STOP = 2.0;

   /**
    * [archery2] Bow shots: the camera rides straight behind and a little above the arrow, looking down its line of
    * flight (nock toward the camera, head forward, the animal ahead), with no side drift or roll; a couple of metres
    * short of the animal it eases to a stop and watches the arrow go in. Evaluated per frame on the replay clock.
    */
   private Pose chaseArrow() {
      Vec3[] anchor = new Vec3[1];
      Pose p = chaseArrow(this.path, this.u, this.sOf(this.u), this.pathSwap, this.impact(), this.stuckDir(), anchor);
      Vec3 c = this.clear(anchor[0], p.pos);
      return c == p.pos ? p : new Pose(c, p.yaw, p.pitch, p.roll, p.fov);
   }

   /** The bow chase pose, pure (the offline harness flies it frame by frame): see {@link #chaseArrow()}. */
   static Pose chaseArrow(KillCamPath path, double u, double sOfU, Vec3 pathSwap, Vec3 impact, Vec3 endDir, Vec3[] anchorOut) {
      double len = path.length;
      double d = sOfU * len;
      double stop = Math.max(0.0, len - Math.min(BOW_STOP, len * 0.3));
      // smooth minimum of d and stop: the camera anchor decelerates into its resting point instead of braking hard
      double k = 0.7;
      double x = (stop - d) / k;
      double softplus = x > 30.0 ? x : (x < -30.0 ? 0.0 : Math.log1p(Math.exp(x)));
      double dc = Math.max(0.0, Math.min(d, stop - k * softplus));
      double s = dc / len;
      Vec3 a = path.at(s).add(pathSwap);
      if (anchorOut != null) {
         anchorOut[0] = a;
      }
      Vec3 t = path.tangent(s);
      Vec3 r = t.cross(UP);
      if (r.lengthSqr() < 1.0E-6) {
         r = new Vec3(1.0, 0.0, 0.0);
      }
      r = r.normalize();
      Vec3 up = r.cross(t).normalize();
      double back = Mth.lerp(smooth(u), 1.0, 0.85);
      Vec3 cam = a.subtract(t.scale(back)).add(up.scale(0.11));
      double ahead = dc + 4.0;
      Vec3 look = ahead <= len ? path.at(ahead / len).add(pathSwap) : impact.add(endDir.scale(ahead - len));
      return lookAt(cam, look, 0.0F, (float)Mth.lerp(smooth(u), 52.0, 46.0));
   }

   private Pose chase() {
      if (this.bow) {
         return this.chaseArrow();
      }
      double u = this.u;
      double s = this.sOf(u);
      Vec3 p = this.path.at(s).add(this.pathSwap);
      Vec3 t = this.path.tangent(s);
      Vec3 r = t.cross(UP);
      if (r.lengthSqr() < 1.0E-6) {
         r = new Vec3(1.0, 0.0, 0.0);
      }
      r = r.normalize();
      Vec3 up = r.cross(t).normalize();
      double su = smooth(u);
      double back = this.bow ? Mth.lerp(u, 1.35, 1.0) : Mth.lerp(u, 0.62, 0.34);
      double lat = (this.bow ? 0.26 : 0.11) + (this.bow ? 0.1 : 0.09) * su + (this.bow ? 0.04 : 0.02) * Math.sin(this.total * 1.9);
      double lift = (this.bow ? 0.14 : 0.075) + 0.015 * Math.sin(this.total * 1.3 + 1.0);
      Vec3 cam = p.subtract(t.scale(back)).add(r.scale(this.side * lat)).add(up.scale(lift));
      Vec3 look = p.add(t.scale(this.bow ? 1.1 : 0.55)).subtract(up.scale(0.01));
      cam = this.clear(p, cam);
      float roll = (float)(-this.side * 3.5 * su + 1.2 * Math.sin(this.total * 0.8));
      return lookAt(cam, look, roll, (float)Mth.lerp(u, 54.0, 46.0));
   }

   private Pose shake(Pose base) {
      double k = Math.exp(-this.tp * 9.0) * (this.bow ? 0.5 : 1.0);
      double t = this.total * 47.0;
      Vec3 j = new Vec3(Math.sin(t) * 0.018, Math.sin(t * 1.37 + 1.1) * 0.014, Math.cos(t * 0.83) * 0.018).scale(k);
      return new Pose(base.pos.add(j), base.yaw + (float)(Math.sin(t * 1.21) * 0.7 * k), base.pitch + (float)(Math.cos(t * 0.9) * 0.6 * k), base.roll, base.fov);
   }

   /** Slow orbit around the animal after the hit: {@code xp} is X-ray progress, {@code dp} drop progress (both 0..1). */
   private Pose orbit(double xp, double dp) {
      Pose base = this.impactPose() == null ? this.lastPose : this.impactPose();
      double held = 0.0;
      if (this.bow && this.heldPose != null) {
         // [archery2] out of the straight-on chase into the side framing, smoothly (position and aim)
         double w = this.xray ? (this.phase == Phase.XRAY ? smoother(xp * 1.25) : 1.0) : smoother(dp * 1.6);
         base = blend(this.heldPose, base, w);
         held = 1.0 - w;
      }
      Vec3 c = this.center();
      Vec3 rel = base.pos.subtract(c);
      double ang = Math.toRadians(this.side * (10.0 * xp + 16.0 * dp));
      double cos = Math.cos(ang);
      double sin = Math.sin(ang);
      double k = 1.0 - 0.07 * xp + 0.22 * dp;
      Vec3 r = new Vec3(rel.x * cos - rel.z * sin, rel.y, rel.x * sin + rel.z * cos).scale(k).add(0.0, 0.4 * dp, 0.0);
      Vec3 cam = this.clear(c, c.add(r));
      Vec3 look = this.lookPoint();
      if (held > 0.0) {
         Vec3 ahead = this.heldPose.pos.add(Vec3.directionFromRotation(this.heldPose.pitch, this.heldPose.yaw).scale(this.heldPose.pos.distanceTo(look)));
         look = ahead.lerp(look, 1.0 - held);
      }
      if (dp > 0.0 && this.standIn != null && !this.standIn.entity.isRemoved()) {
         Vec3 lying = this.standIn.entity.position().add(0.0, this.bbHeight * 0.3, 0.0);
         look = look.lerp(lying, dp);
      }
      float fov = (float)(base.fov - 3.0 * xp + 9.0 * dp);
      return lookAt(cam, look, 0.0F, fov);
   }

   private Vec3 lookPoint() {
      return this.impact().lerp(this.center(), 0.3).subtract(this.dir().scale(0.1));
   }

   private Pose computeImpactPose() {
      Vec3 look = this.lookPoint();
      Vec3 d = this.dir();
      Vec3 hb = new Vec3(-d.x, 0.0, -d.z);
      if (hb.lengthSqr() < 1.0E-6) {
         hb = new Vec3(1.0, 0.0, 0.0);
      }
      hb = hb.normalize();
      double size = Math.max(this.bbWidth, this.bbHeight);
      double dist = Math.clamp(1.5 + size * 1.35, 2.2, 5.5);
      double lift = 0.2 + 0.2 * this.bbHeight;
      int[] angles = {72, 58, 90, 45, 110, 130, 30};
      for (int pass = 0; pass < 2; pass++) {
         int sgn = pass == 0 ? this.side : -this.side;
         for (int a : angles) {
            double rad = Math.toRadians(sgn * a);
            Vec3 dir = new Vec3(hb.x * Math.cos(rad) - hb.z * Math.sin(rad), 0.0, hb.x * Math.sin(rad) + hb.z * Math.cos(rad));
            Vec3 cam = look.add(dir.scale(dist)).add(0.0, lift, 0.0);
            if (this.free(look, cam)) {
               return lookAt(cam, look, 0.0F, 40.0F);
            }
         }
      }
      Vec3 fallback = this.clear(look, look.subtract(d.scale(dist)).add(0.0, lift, 0.0));
      return lookAt(fallback, look, 0.0F, 40.0F);
   }

   private boolean free(Vec3 from, Vec3 to) {
      BlockHitResult hit = this.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
      return hit.getType() == HitResult.Type.MISS && this.level.getBlockState(BlockPos.containing(to)).getCollisionShape(this.level, BlockPos.containing(to)).isEmpty();
   }

   /** Pull a camera point back toward {@code anchor} until the line between them is clear of blocks. */
   private Vec3 clear(Vec3 anchor, Vec3 cam) {
      BlockHitResult hit = this.level.clip(new ClipContext(anchor, cam, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
      if (hit.getType() == HitResult.Type.MISS) {
         return cam;
      }
      Vec3 back = anchor.subtract(hit.getLocation());
      return back.lengthSqr() < 1.0E-6 ? anchor : hit.getLocation().add(back.normalize().scale(Math.min(0.12, back.length())));
   }

   private Pose unblock(Pose p, Vec3 anchor) {
      Vec3 c = this.clear(anchor, p.pos);
      return c == p.pos ? p : new Pose(c, p.yaw, p.pitch, p.roll, p.fov);
   }

   static Pose lookAt(Vec3 cam, Vec3 target, float roll, float fov) {
      Vec3 d = target.subtract(cam);
      if (d.lengthSqr() < 1.0E-8) {
         d = new Vec3(0.0, 0.0, 1.0);
      }
      float yaw = (float)Math.toDegrees(Math.atan2(-d.x, d.z));
      float pitch = (float)(-Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));
      return new Pose(cam, yaw, pitch, roll, fov);
   }

   static Pose blend(Pose a, Pose b, double w) {
      if (w <= 0.0) {
         return a;
      } else if (w >= 1.0) {
         return b;
      }
      float f = (float)w;
      return new Pose(
         a.pos.lerp(b.pos, w), a.yaw + Mth.wrapDegrees(b.yaw - a.yaw) * f, Mth.lerp(f, a.pitch, b.pitch), Mth.lerp(f, a.roll, b.roll), Mth.lerp(f, a.fov, b.fov)
      );
   }

   static double smooth(double x) {
      double t = Math.clamp(x, 0.0, 1.0);
      return t * t * (3.0 - 2.0 * t);
   }

   static double smoother(double x) {
      double t = Math.clamp(x, 0.0, 1.0);
      return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
   }

   static Pose eye(Minecraft mc, float pt, float fov) {
      return new Pose(mc.player.getEyePosition(pt), mc.player.getViewYRot(pt), mc.player.getViewXRot(pt), 0.0F, fov);
   }
}
