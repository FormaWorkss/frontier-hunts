package com.formaworks.frontierhunts.landscape.ride.rig;

import com.formaworks.frontierhunts.landscape.ride.Atv;
import com.formaworks.frontierhunts.landscape.ride.RideContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * [atvfuel] Gasoline for the ATV.
 *
 * <p>Server authoritative: the server burns fuel from what it can observe of the ride (speed, acceleration and
 * climbing, measured from the position updates the driver sends), stores the exact level, and syncs a 0.01 L
 * quantised copy through {@link Atv#DATA_FUEL}. Every side derives "engine running" from that synced value plus the
 * driver's game mode, so the driver's own physics, everyone's engine audio and the HUD agree. Creative drivers never
 * burn fuel and always run; {@code requireFuel=false} (synced as {@link AtvRig#FREE}) makes every tank endless.
 */
public final class AtvFuel {
   /** Tank capacity in litres (a 450-570 cc utility quad carries 14-17 L). */
   public static final float TANK = 15.0F;
   /** Below this the engine starts to stumble and pop as the pickup sucks air. */
   public static final float SPUTTER = 0.35F;
   /** Below this fraction the HUD gauge turns amber. */
   public static final float LOW = 0.15F;
   // litres per tick: idle ~4 h on a tank, steady 65 km/h ~37 min, flat-out ~26 min, plus load and climbing
   static final double IDLE = 5.2E-5;
   static final double RUN = 2.9E-4;
   static final double LOAD = 1.5E-4;
   static final double CLIMB = 1.2E-4;

   private AtvFuel() {
   }

   /** Per-ATV transient state (field on the entity; nothing here is persisted except via {@link #save}). */
   public static final class Tank {
      double exact = -1.0;
      double lastSpeed;
      double lastY = Double.NaN;
      boolean dryHandled;
      /** Ticks left of a fuel-starved misfire (client side, cosmetic + driver throttle cut). */
      public int misfire;
      /** Cargo-box lid opening 0..1 (client animation of the synced OPEN bit). */
      public float lid;
      public float lidO;
   }

   // ------------------------------------------------------------------------------------------------ state

   /** Synced fuel level in litres. */
   public static float fuel(Atv atv) {
      Float f = atv.getEntityData().get(Atv.DATA_FUEL);
      return f == null ? 0.0F : f;
   }

   public static float fraction(Atv atv) {
      return Mth.clamp(fuel(atv) / TANK, 0.0F, 1.0F);
   }

   /** Server: set the exact level and push a quantised copy to clients. */
   public static void set(Atv atv, double litres) {
      Tank t = atv.tank;
      boolean wasDry = t.exact <= 0.0;
      t.exact = Mth.clamp(Double.isFinite(litres) ? litres : 0.0, 0.0, TANK);
      float q = (float)(Math.round(t.exact * 100.0) / 100.0);
      if (t.exact > 0.0 && q <= 0.0F) {
         q = 0.01F;
      }
      if (Math.abs(q - fuel(atv)) >= 0.005F || q == 0.0F && fuel(atv) != 0.0F) {
         atv.getEntityData().set(Atv.DATA_FUEL, q);
      }
      if (t.exact > 0.0) {
         t.dryHandled = false;
         if (wasDry && !atv.level().isClientSide && atv.getControllingPassenger() instanceof Player) {
            // the engine catches again
            atv.level().playSound(null, atv.getX(), atv.getY(), atv.getZ(), RideContent.SND_ATV_START.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
         }
      }
   }

   public static double exact(Atv atv) {
      return Math.max(0.0, atv.tank.exact);
   }

   static Player driver(Atv atv) {
      return atv.getControllingPassenger() instanceof Player p ? p : null;
   }

   /** Fuel does not matter for this driver (creative / spectator) or for this server (requireFuel=false). */
   public static boolean exempt(Atv atv) {
      if ((atv.getEntityData().get(Atv.DATA_RIG) & AtvRig.FREE) != 0) {
         return true;
      }
      Player p = driver(atv);
      return p != null && (p.isCreative() || p.isSpectator());
   }

   /** Engine is on: someone in the seat, not drowned, and fuel in the tank (or exempt). Valid on both sides. */
   public static boolean running(Atv atv) {
      return atv.isVehicle() && !atv.flooded && (fuel(atv) > 0.0F || exempt(atv));
   }

   // ------------------------------------------------------------------------------------------------ physics hooks

   /** Throttle the engine can deliver right now: none when dead, cut while fuel-starved misfires. */
   public static float throttle(Atv atv, float throttle) {
      if (!running(atv) || atv.tank.misfire > 0) {
         return 0.0F;
      }
      return throttle;
   }

   /** Brakes always work; reverse needs the engine. */
   public static float brake(Atv atv, float brake, double forwardSpeed) {
      return forwardSpeed > 0.02 || running(atv) ? brake : 0.0F;
   }

   /** Engine-sound gain multiplier (dips on misfires). */
   public static float soundGain(Atv atv) {
      return atv.tank.misfire > 0 ? 0.3F : 1.0F;
   }

   // ------------------------------------------------------------------------------------------------ ticking

   /** Called once per ATV tick on both sides. */
   public static void tick(Atv atv) {
      if (atv.level().isClientSide) {
         clientTick(atv);
      } else {
         serverTick(atv);
      }
   }

   private static void serverTick(Atv atv) {
      Tank t = atv.tank;
      if (t.exact < 0.0) {
         set(atv, TANK * AtvFuelConfig.startFraction());
      }
      AtvRig.serverTick(atv);
      double v = Math.abs(atv.shownSpeed);
      double y = atv.getY();
      double climb = Double.isNaN(t.lastY) || !atv.onGround() ? 0.0 : Mth.clamp(y - t.lastY, 0.0, 1.0);
      double accel = Math.max(0.0, v - t.lastSpeed);
      t.lastSpeed = v;
      t.lastY = y;
      Player d = driver(atv);
      if (d == null || exempt(atv)) {
         return;
      }
      if (t.exact > 0.0 && !atv.flooded) {
         double rate = IDLE
            + RUN * Math.pow(Math.min(v / 0.9, 1.4), 1.25)
            + LOAD * Math.min(accel / 0.03, 1.0)
            + CLIMB * Math.min(climb / 0.1, 1.0);
         set(atv, t.exact - rate * AtvFuelConfig.fuelUse());
      }
      if (t.exact <= 0.0 && !t.dryHandled) {
         t.dryHandled = true;
         if (!AtvRig.reserveRefuel(atv, d, true)) {
            atv.level().playSound(null, atv.getX(), atv.getY(), atv.getZ(), RigContent.SND_STALL.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            d.displayClientMessage(Component.translatable("message.frontierhunts.atv_out_of_fuel").withStyle(ChatFormatting.RED), true);
         }
      }
   }

   private static void clientTick(Atv atv) {
      Tank t = atv.tank;
      if (t.misfire > 0) {
         t.misfire--;
      }
      t.lidO = t.lid;
      float lidTarget = (AtvRig.flags(atv) & AtvRig.OPEN) != 0 ? 1.0F : 0.0F;
      t.lid += (lidTarget - t.lid) * (lidTarget > t.lid ? 0.22F : 0.3F);
      if (!running(atv) || exempt(atv)) {
         return;
      }
      float f = fuel(atv);
      if (f < SPUTTER && t.misfire == 0) {
         float severity = 1.0F - f / SPUTTER;
         if (atv.getRandom().nextFloat() < 0.02F + 0.08F * severity) {
            t.misfire = 3 + atv.getRandom().nextInt(4 + (int)(severity * 6.0F));
            Vec3 fwd = Vec3.directionFromRotation(0.0F, atv.getYRot());
            Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
            Vec3 pipe = atv.position().add(fwd.scale(-1.05)).add(right.scale(-0.31)).add(0.0, 0.5, 0.0);
            atv.level().playLocalSound(pipe.x, pipe.y, pipe.z, RigContent.SND_SPUTTER.get(), SoundSource.NEUTRAL,
               0.7F + 0.3F * severity, 0.9F + atv.getRandom().nextFloat() * 0.2F, false);
            for (int i = 0; i < 3; i++) {
               atv.level().addParticle(ParticleTypes.SMOKE, pipe.x, pipe.y, pipe.z, -fwd.x * 0.06, 0.02, -fwd.z * 0.06);
            }
         }
      }
   }

   /** World position of the tank filler cap (just ahead of the seat, on the tank cover). */
   public static Vec3 fillerNeck(Atv atv) {
      Vec3 fwd = Vec3.directionFromRotation(0.0F, atv.getYRot());
      return atv.position().add(fwd.scale(0.42)).add(0.0, 0.98, 0.0);
   }

   // ------------------------------------------------------------------------------------------------ mounting / items / NBT

   /** Replaces the plain start sound when someone climbs on: start, or crank without catching. */
   public static void onMount(Atv atv, Player rider) {
      if (atv.flooded) { // [atv2] drowned engine: it cranks but won't catch until the ATV is out of deep water (no reserve refill)
         if (!atv.level().isClientSide) {
            atv.level().playSound(null, atv.getX(), atv.getY(), atv.getZ(), RigContent.SND_CRANK.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            rider.displayClientMessage(Component.translatable("message.frontierhunts.atv_flooded").withStyle(ChatFormatting.AQUA), true);
         }
         return;
      }
      if (!running(atv) && !atv.level().isClientSide) {
         // dry tank but cans on the rack: fill up from the reserve first (set() plays the start sound)
         atv.tank.dryHandled = true;
         if (AtvRig.reserveRefuel(atv, rider, true)) {
            return;
         }
      }
      if (running(atv)) {
         atv.level().playSound(null, atv.getX(), atv.getY(), atv.getZ(), RideContent.SND_ATV_START.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
      } else {
         atv.level().playSound(null, atv.getX(), atv.getY(), atv.getZ(), RigContent.SND_CRANK.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
         rider.displayClientMessage(Component.translatable("message.frontierhunts.atv_no_fuel").withStyle(ChatFormatting.GOLD), true);
      }
   }

   /** Stamp the tank level onto the ATV item it drops as. */
   public static void toItem(Atv atv, ItemStack stack) {
      stack.set(RigContent.FUEL.get(), (float)(Math.round(exact(atv) * 100.0) / 100.0));
   }

   /** A freshly placed ATV: the level carried by the item, or the configured starter splash of fuel. */
   public static void fromItem(Atv atv, ItemStack stack) {
      Float f = stack.get(RigContent.FUEL.get());
      set(atv, f != null ? f : TANK * AtvFuelConfig.startFraction());
   }

   public static void save(Atv atv, CompoundTag tag) {
      tag.putFloat("FhFuel", (float)exact(atv));
   }

   public static void load(Atv atv, CompoundTag tag) {
      // ATVs saved before fuel existed get the starter quarter tank instead of being stranded empty
      set(atv, tag.contains("FhFuel") ? tag.getFloat("FhFuel") : TANK * AtvFuelConfig.startFraction());
   }
}
