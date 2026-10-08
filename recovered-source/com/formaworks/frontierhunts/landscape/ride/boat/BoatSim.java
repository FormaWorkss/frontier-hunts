package com.formaworks.frontierhunts.landscape.ride.boat;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * [1.1.0] The handling shared by the rowboat and the jon boat, layered on the vanilla boat (which keeps floating,
 * bubble columns, landing, leashes, passengers and the client-authoritative movement).
 *
 * <p>The vanilla paddling is switched off ({@code setInput} is swallowed) and replaced by:
 * <ul>
 * <li>a hull that keeps its way: little drag ahead, a lot sideways (the keel), measured against the moving water so
 *     a river carries the boat along at the speed of its current;</li>
 * <li>the rowboat's two oars, each rowing or backing on its own stroke cycle: the push comes during the drive, and
 *     pulling one oar while backing the other spins the boat on the spot;</li>
 * <li>the jon boat's outboard: a throttle that spools up, a tiller that steers harder the faster the boat goes, and
 *     a slow paddle when the tank is empty;</li>
 * <li>bow and stern that stop at the bank instead of passing through it (the hull is longer than the vanilla
 *     collision box).</li>
 * </ul>
 * The driver's keys are sent to the server as one byte ({@link BoatNet}) and synced to everyone, so the oars, the
 * motor and the sounds look the same on every client and the server knows what the engine is burning.
 */
public final class BoatSim {
   private BoatSim() {
   }

   /** height of the water surface above the boat's position when it floats (vanilla float balance: 0.65 of 0.5625) */
   public static final double WATERLINE = 0.04 / 0.06153846 * 0.5625;
   public static final int FWD = 1, BACK = 2, LEFT = 4, RIGHT = 8;
   /** speed of the boat drift in ordinary flowing water, blocks per tick (about 2.4 m/s) */
   public static final double CURRENT = 0.12;
   static final float DEG = (float)(Math.PI / 180.0);

   /** called every client tick for every boat (motor sound, spray); set by the client setup */
   public static volatile Consumer<Boat> CLIENT_TICK = b -> {
   };

   public record Seat(double x, double y, double z) {
   }

   /** mesh base name, half length of the hull, depth of the keel below the waterline, way kept per tick ahead and abeam */
   public record Spec(String mesh, double halfLength, double keel, double fwdKeep, double latKeep, Seat[] seats) {
   }

   public static final class State {
      public boolean inL, inR, inU, inD;
      int sentBits = -1;
      int sendTimer;
      public float yawRate, yawStep;
      // oars: 0 = port (left, +x), 1 = starboard (right, -x)
      public final float[] phase = {0.5F, 0.5F}, phaseO = {0.5F, 0.5F}, active = new float[2], activeO = new float[2];
      final int[] dir = new int[2];
      // outboard
      public float throttle, throttleO, tiller, tillerO, prop, propO, tilt = 1.0F, tiltO = 1.0F;
      // looks
      public float pitch, pitchO, roll, rollO, afloat = 1.0F, afloatO = 1.0F;
      public double speed;
      public boolean running;
      int dryTicks;
      boolean blockedBow, blockedStern;
      public Object sound;
   }

   // ----------------------------------------------------------------------------------------------- input

   static int bits(State s) {
      int b = 0;
      if (s.inU && !s.inD) b |= FWD;
      if (s.inD && !s.inU) b |= BACK;
      if (s.inL && !s.inR) b |= LEFT;
      if (s.inR && !s.inL) b |= RIGHT;
      return b;
   }

   /** the keys that drive the boat this tick: the local driver's own, or the synced byte everywhere else */
   static int activeBits(Boat b, FrontierBoat fb) {
      LivingEntity c = b.getControllingPassenger();
      if (c == null) {
         return 0;
      }
      if (b.level().isClientSide && c instanceof Player p && p.isLocalPlayer()) {
         return bits(fb.sim());
      }
      return fb.inputBits();
   }

   /** the local driver: send the keys when they change (and once a second) */
   static void send(Boat b, FrontierBoat fb) {
      State s = fb.sim();
      if (!b.level().isClientSide || !(b.getControllingPassenger() instanceof Player p) || !p.isLocalPlayer()) {
         return;
      }
      int bits = bits(s);
      if (bits != s.sentBits || ++s.sendTimer >= 20) {
         s.sentBits = bits;
         s.sendTimer = 0;
         BoatNet.send(new BoatNet.Input(b.getId(), (byte)bits));
      }
   }

   // ----------------------------------------------------------------------------------------------- water

   public static boolean inWater(Boat b) {
      return b.level().getFluidState(BlockPos.containing(b.getX(), b.getY() + 0.1, b.getZ())).is(FluidTags.WATER);
   }

   /** the water's own velocity at the boat (blocks per tick): the creek / river current markers, else vanilla flow */
   static Vec3 drift(Boat b) {
      Level level = b.level();
      BlockPos pos = BlockPos.containing(b.getX(), b.getY() + 0.1, b.getZ());
      com.formaworks.frontierhunts.landscape.rapids.Rapids.Flow f = com.formaworks.frontierhunts.landscape.rapids.Rapids.boat(level, pos);
      if (f != null) {
         double k = 0.6; // [1.2.5] no rapids: every run drifts a boat gently
         return new Vec3(f.dx(), 0.0, f.dz()).scale(CURRENT * k);
      }
      FluidState fs = level.getFluidState(pos);
      if (!fs.is(FluidTags.WATER) || fs.isSource()) {
         return Vec3.ZERO;
      }
      Vec3 flow = fs.getFlow(level, pos);
      if (flow.lengthSqr() < 1.0E-6) {
         return Vec3.ZERO;
      }
      return new Vec3(flow.x, 0.0, flow.z).normalize().scale(CURRENT);
   }

   /** rapids under the boat (strength 2 marker) */
   static boolean rapids(Boat b) {
      com.formaworks.frontierhunts.landscape.rapids.Rapids.Flow f = com.formaworks.frontierhunts.landscape.rapids.Rapids.boat(b.level(),
         BlockPos.containing(b.getX(), b.getY() + 0.1, b.getZ()));
      return false; // [1.2.5] the rapids are gone
   }

   // ----------------------------------------------------------------------------------------------- the tick

   /** before vanilla's tick, on the side that moves the boat: thrust, keel, way, current */
   static void beforeTick(Boat b, FrontierBoat fb, double thrust) {
      if (!b.isControlledByLocalInstance()) {
         return;
      }
      Spec sp = fb.spec();
      Vec3 v = b.getDeltaMovement();
      float yaw = b.getYRot() * DEG;
      double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
      if (inWater(b)) {
         Vec3 w = drift(b);
         double rx = v.x - w.x, rz = v.z - w.z;
         double fwd = rx * fx + rz * fz;
         double lat = rx * fz - rz * fx;
         fwd = fwd * sp.fwdKeep() + thrust;
         lat = lat * sp.latKeep();
         if (rapids(b)) {
            // white water throws the hull about
            fb.sim().yawRate += (b.getRandom().nextFloat() - 0.5F) * 1.4F;
            lat += (b.getRandom().nextDouble() - 0.5) * 0.04;
         }
         // vanilla multiplies by 0.9 in water right after this: divide it back out so the way kept is ours
         double nx = (w.x + fwd * fx + lat * fz) / 0.9;
         double nz = (w.z + fwd * fz - lat * fx) / 0.9;
         b.setDeltaMovement(nx, v.y, nz);
      } else if (b.onGround() && thrust != 0.0) {
         // beached: the driver can shove it along slowly, back into the water
         double push = Math.signum(thrust) * 0.02;
         b.setDeltaMovement(v.x + fx * push, v.y, v.z + fz * push);
      }
   }

   /** after vanilla's tick: turn, keep bow and stern out of the bank, sync, looks */
   static void afterTick(Boat b, FrontierBoat fb) {
      State s = fb.sim();
      Spec sp = fb.spec();
      s.yawStep = 0.0F;
      if (b.isControlledByLocalInstance()) {
         float before = b.getYRot();
         if (Math.abs(s.yawRate) > 0.001F) {
            b.setYRot(before + s.yawRate);
         }
         s.yawStep = b.getYRot() - before;
         keepOffBank(b, fb, before);
      }
      send(b, fb);
      // forward speed for looks and sounds, from the position change (works for remote boats too)
      float yaw = b.getYRot() * DEG;
      double dx = b.getX() - b.xo, dz = b.getZ() - b.zo;
      double fwd = dx * -Mth.sin(yaw) + dz * Mth.cos(yaw);
      s.speed = s.speed * 0.7 + (Math.abs(fwd) > 2.0 ? 0.0 : fwd) * 0.3;
      if (b.level().isClientSide) {
         s.afloatO = s.afloat;
         // [1.1.1] only call it beached after a moment out of the water (a flicker of the water test made the hull jump)
         boolean wet = inWater(b) || b.isInWater();
         s.dryTicks = wet ? 0 : s.dryTicks + 1;
         s.afloat += ((s.dryTicks < 10 ? 1.0F : 0.0F) - s.afloat) * 0.12F;
         CLIENT_TICK.accept(b);
      }
   }

   /** the vanilla collision box is shorter than these hulls: stop the bow or stern at a bank instead of letting it in */
   private static void keepOffBank(Boat b, FrontierBoat fb, float yawBefore) {
      State s = fb.sim();
      double half = fb.spec().halfLength();
      float yaw = b.getYRot() * DEG;
      Vec3 f = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
      boolean bow = solidAt(b, f, half - 0.12);
      boolean stern = solidAt(b, f, -(half - 0.08));
      Vec3 moved = new Vec3(b.getX() - b.xo, 0.0, b.getZ() - b.zo);
      boolean revert = bow && (!s.blockedBow || moved.dot(f) > 1.0E-4) || stern && (!s.blockedStern || moved.dot(f) < -1.0E-4);
      if (revert) {
         double hit = moved.length();
         b.setPos(b.xo, b.getY(), b.zo);
         b.setYRot(yawBefore);
         s.yawStep = 0.0F;
         s.yawRate = 0.0F;
         Vec3 v = b.getDeltaMovement();
         b.setDeltaMovement(-v.x * 0.15, v.y, -v.z * 0.15);
         if (hit > 0.08 && b.level().isClientSide) {
            b.level().playLocalSound(b.getX(), b.getY(), b.getZ(), fb.bumpSound(), SoundSource.NEUTRAL, (float)Math.min(1.0, hit * 3.0), 0.8F, false);
         }
         yaw = b.getYRot() * DEG;
         f = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
         bow = solidAt(b, f, half - 0.12);
         stern = solidAt(b, f, -(half - 0.08));
      }
      s.blockedBow = bow;
      s.blockedStern = stern;
   }

   private static boolean solidAt(Boat b, Vec3 f, double along) {
      for (double up : new double[]{WATERLINE + 0.08, WATERLINE - 0.08}) {
         Vec3 p = b.position().add(f.scale(along)).add(0.0, up, 0.0);
         BlockPos pos = BlockPos.containing(p);
         BlockState st = b.level().getBlockState(pos);
         if (st.isAir()) {
            continue;
         }
         VoxelShape shape = st.getCollisionShape(b.level(), pos);
         if (!shape.isEmpty()) {
            for (AABB box : shape.toAabbs()) {
               if (box.move(pos).inflate(0.02).contains(p)) {
                  return true;
               }
            }
         }
      }
      return false;
   }

   // ----------------------------------------------------------------------------------------------- riders

   public static Vec3 seat(Boat b, FrontierBoat fb, Entity passenger) {
      Seat[] seats = fb.spec().seats();
      int i = Math.max(0, Math.min(seats.length - 1, b.getPassengers().indexOf(passenger)));
      Seat s = seats[i];
      double y = WATERLINE * fb.sim().afloat + fb.spec().keel() * (1.0F - fb.sim().afloat) + s.y() + 0.014;
      return new Vec3(s.x(), y, s.z()).yRot(-b.getYRot() * DEG);
   }

   /** riders turn with the hull */
   public static void turnRider(Boat b, FrontierBoat fb, Entity passenger) {
      float d = fb.sim().yawStep;
      if (d != 0.0F && b.getControllingPassenger() == passenger) {
         passenger.setYRot(passenger.getYRot() + d);
         passenger.setYHeadRot(passenger.getYHeadRot() + d);
      }
   }

   // ----------------------------------------------------------------------------------------------- rowing

   static final float STROKE = 1.0F / 30.0F;
   static final double OAR_PUSH = 0.0069; // [1.1.1] a little quicker: ~3 m/s cruising
   static final float OAR_TURN = 90.0F;

   /** push of one oar during its drive (0 out of the water) */
   public static float drive(float ph) {
      return ph < 0.45F ? Mth.sin((float)Math.PI * ph / 0.45F) : 0.0F;
   }

   /** advance both oars from the keys; returns the forward push, and sets the turn from the difference */
   static double row(Boat b, FrontierBoat fb, int bits) {
      State s = fb.sim();
      boolean u = (bits & FWD) != 0, d = (bits & BACK) != 0, l = (bits & LEFT) != 0, r = (bits & RIGHT) != 0;
      int port = u || r ? 1 : d || l ? -1 : 0;
      int star = u || l ? 1 : d || r ? -1 : 0;
      if (u && r) star = 0;
      if (u && l) port = 0;
      if (d && r) port = 0;
      if (d && l) star = 0;
      if (port != 0 && port == star) {
         // [1.1.1] rowing straight the two oars pull as one: close any gap between their strokes, meeting in the middle
         float gap = s.phase[1] - s.phase[0];
         if (gap > 0.5F) gap -= 1.0F;
         if (gap < -0.5F) gap += 1.0F;
         float step = Math.min(Math.abs(gap) * 0.5F, 0.03F) * Math.signum(gap);
         s.phase[0] = wrap(s.phase[0] + step);
         s.phase[1] = wrap(s.phase[1] - step);
         s.active[1] = s.active[0] = Math.max(s.active[0], s.active[1]);
      }
      float tp = oar(b, fb, 0, port);
      float ts = oar(b, fb, 1, star);
      boolean water = inWater(b);
      s.yawRate = s.yawRate * 0.9F + (water ? (tp - ts) * (float)OAR_PUSH * OAR_TURN : 0.0F);
      return water ? (tp + ts) * OAR_PUSH : (tp + ts) * 0.5;
   }

   private static float oar(Boat b, FrontierBoat fb, int side, int dir) {
      State s = fb.sim();
      s.phaseO[side] = s.phase[side];
      s.activeO[side] = s.active[side];
      float before = s.phase[side];
      if (dir != 0) {
         s.phase[side] = wrap(before + dir * STROKE);
         s.active[side] = Math.min(1.0F, s.active[side] + 0.12F);
         s.dir[side] = dir;
      } else if (s.active[side] > 0.0F) {
         // finish the stroke out of the water, then lay the oar back along the hull
         if (before < 0.5F) {
            s.phase[side] = Math.min(0.5F, before + STROKE);
         } else {
            s.active[side] = Math.max(0.0F, s.active[side] - 0.06F);
         }
      }
      if (dir != 0 && b.level().isClientSide && s.active[side] > 0.5F) {
         boolean catchIn = dir > 0 ? before > 0.9F && s.phase[side] < 0.1F : before > 0.45F && s.phase[side] <= 0.45F;
         if (catchIn && inWater(b)) {
            Vec3 at = blade(b, side);
            b.level().playLocalSound(at.x, at.y, at.z, SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 0.55F, 0.85F + b.getRandom().nextFloat() * 0.25F, false);
            for (int i = 0; i < 4; i++) {
               b.level().addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH, at.x + (b.getRandom().nextDouble() - 0.5) * 0.3, at.y,
                  at.z + (b.getRandom().nextDouble() - 0.5) * 0.3, 0.0, 0.05, 0.0);
            }
         }
      }
      return dir == 0 ? 0.0F : dir * drive(s.phase[side]);
   }

   /** rough world position of an oar blade (for splashes) */
   static Vec3 blade(Boat b, int side) {
      float yaw = b.getYRot() * DEG;
      double x = (side == 0 ? 1.0 : -1.0) * 2.0;
      return b.position().add(new Vec3(x, WATERLINE + 0.02, 0.3).yRot(-yaw));
   }

   static float wrap(float p) {
      p %= 1.0F;
      return p < 0.0F ? p + 1.0F : p;
   }

   // ----------------------------------------------------------------------------------------------- outboard

   static final double MOTOR_PUSH = 0.0185;
   static final double PADDLE_PUSH = 0.0022;

   /** throttle, tiller and push of the jon boat; returns the forward push */
   static double motor(Boat b, FrontierBoat fb, int bits, boolean running) {
      State s = fb.sim();
      s.throttleO = s.throttle;
      s.tillerO = s.tiller;
      s.propO = s.prop;
      s.tiltO = s.tilt;
      s.running = running;
      boolean u = (bits & FWD) != 0, d = (bits & BACK) != 0;
      float turn = ((bits & RIGHT) != 0 ? 1.0F : 0.0F) - ((bits & LEFT) != 0 ? 1.0F : 0.0F);
      float target = running ? (u ? 1.0F : d ? -0.45F : 0.0F) : 0.0F;
      s.throttle += Mth.clamp(target - s.throttle, -0.08F, 0.045F);
      s.tiller += (turn - s.tiller) * 0.2F;
      if (running) {
         s.prop += 8.0F + s.throttle * 52.0F;
      }
      boolean water = inWater(b);
      double push;
      float base;
      if (running) {
         push = s.throttle * MOTOR_PUSH;
         base = 0.5F + 1.6F * Math.abs(s.throttle);
      } else {
         push = u ? PADDLE_PUSH : d ? -PADDLE_PUSH * 0.6 : 0.0;
         base = (u || d || turn != 0.0F) && b.getControllingPassenger() != null ? 1.1F : 0.0F;
      }
      float sign = s.speed < -0.02 ? -1.0F : 1.0F;
      float rate = water ? s.tiller * Math.min(4.5F, base + 9.0F * (float)Math.abs(s.speed)) * sign : 0.0F;
      s.yawRate += (rate - s.yawRate) * 0.2F;
      // tilt the leg up out of the way when beached or in the shallows
      s.tilt += ((s.afloat < 0.5F || shallow(b) ? 1.0F : 0.0F) - s.tilt) * 0.15F;
      return water ? push : push * 10.0;
   }

   /** ground within reach of the propeller behind the transom */
   static boolean shallow(Boat b) {
      float yaw = b.getYRot() * DEG;
      Vec3 p = b.position().add(new Vec3(0.0, WATERLINE - 0.5, -2.2).yRot(-yaw));
      BlockPos pos = BlockPos.containing(p);
      return !b.level().getBlockState(pos).getCollisionShape(b.level(), pos).isEmpty();
   }

   /** hull looks: pitch (bow up under power), roll (lean in turns), eased */
   static void looks(FrontierBoat fb, float bowUp, float lean) {
      State s = fb.sim();
      s.pitchO = s.pitch;
      s.rollO = s.roll;
      s.pitch += (bowUp - s.pitch) * 0.08F;
      s.roll += (lean - s.roll) * 0.1F;
   }
}
