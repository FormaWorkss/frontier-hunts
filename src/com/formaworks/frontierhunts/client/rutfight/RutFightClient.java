package com.formaworks.frontierhunts.client.rutfight;

import com.formaworks.frontierhunts.client.FrontierGraphics;
import com.formaworks.frontierhunts.hunting.DeerAnimator;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.hunting.rutfight.FightAim;
import com.formaworks.frontierhunts.hunting.rutfight.FightFit;
import com.formaworks.frontierhunts.hunting.rutfight.FightFits;
import com.formaworks.frontierhunts.hunting.rutfight.FightModel;
import com.formaworks.frontierhunts.hunting.rutfight.FightPose;
import com.formaworks.frontierhunts.hunting.rutfight.RutFightNet;
import com.formaworks.frontierhunts.hunting.rutfight.RutFightState;
import com.formaworks.frontierhunts.hunting.rutfight.UltraFightModels;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

/**
 * [rutfight] Client side of locked-antler fights: for every fighting deer/elk/moose this frame it finds the rival
 * (synced id), fits the pair for the current graphics preset (exact antler geometry: sculpted mesh or Vanilla cubes;
 * computed once per pair off-thread), and hands the animator an exact head target - heads lowering on
 * the approach, racks on the shared contact while locked, twisting and heaving as one piece, jolting together at each
 * clash. The rendered body is turned to face the rival exactly and slid by whatever the neck could not reach, so the
 * racks touch on screen whatever the interpolation or the server's chosen distance. It also tells the server the
 * distance this preset needs (nearest watcher wins).
 */
public final class RutFightClient {
   private static final Map<Integer, Fighter> FIGHTERS = new HashMap<>();
   private static final Map<String, Long> REPORTED = new HashMap<>();
   private static long frameTick;

   private RutFightClient() {
   }

   /** Per-animal render state. */
   static final class Fighter {
      final Matrix4f target = new Matrix4f();
      float yawWeight;
      float yaw;
      float slideWeight;
      /** Vertical shortfall of the last head reach (entity blocks), fed to the rival so both racks stay level. */
      float residualY;
      long seen;
   }

   static int preset() {
      return FrontierGraphics.vanillaAnimals() ? 0 : 2; // [presets] 1 (Balanced cubes) is retired
   }

   static FightModel model(DeerTraits t, int preset) {
      return preset == 2 ? UltraFightModels.of(t) : BoxFightModels.of(t, preset == 0);
   }

   private static float smooth(float a, float b, float x) {
      float t = Mth.clamp((x - a) / (b - a), 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   /** Called from WhitetailRenderer.pose() right after Whitetail#animatorInput. */
   public static void prepare(Whitetail e, DeerAnimator.Input in, float partial) {
      tidy(e.level().getGameTime());
      int pose = e.rutPose();
      if (!RutFightState.fighting(pose) || in.downed || in.bedded) {
         FIGHTERS.remove(e.getId());
         return;
      }

      Entity o = e.level().getEntity(e.rutPartner());
      if (!(o instanceof Whitetail rival) || rival.isRemoved() || rival.species() != e.species()) {
         FIGHTERS.remove(e.getId());
         return;
      }

      boolean isA = e.getId() < rival.getId();
      Whitetail wa = isA ? e : rival;
      Whitetail wb = isA ? rival : e;
      int preset = preset();
      DeerTraits ta = wa.traits();
      DeerTraits tb = wb.traits();
      String key = FightFits.key(preset, ta, tb);
      FightFit fit;
      try {
         if (preset == 2) {
            fit = FightFits.get(key, () -> UltraFightModels.of(ta), () -> UltraFightModels.of(tb));
         } else {
            // cube data is built on this thread (not thread safe); only the solve runs in the background
            FightModel ma = model(ta, preset);
            FightModel mb = model(tb, preset);
            fit = FightFits.get(key, () -> ma, () -> mb);
         }
      } catch (RuntimeException ex) {
         return;
      }

      if (fit == null) {
         return;
      }

      FightModel m = isA ? fit.modelA : fit.modelB;
      if (m == null) {
         return;
      }

      Fighter f = FIGHTERS.computeIfAbsent(e.getId(), k -> new Fighter());
      f.seen = e.level().getGameTime();
      report(wa, wb, fit, key);
      Vec3 pa = wa.getPosition(partial);
      Vec3 pb = wb.getPosition(partial);
      double dx = pb.x - pa.x;
      double dz = pb.z - pa.z;
      float d = (float)Math.sqrt(dx * dx + dz * dz);
      float dy = (float)(pb.y - pa.y);
      float span = fit.distance;
      float lower;
      float hold;
      if (pose == RutFightState.POSE_LOCK) {
         lower = 1.0F;
         hold = 1.0F;
      } else if (pose == RutFightState.POSE_BREAK) {
         lower = 0.9F;
         hold = 0.0F;
      } else {
         lower = smooth(span + 2.2F + span * 0.35F, span + 0.35F, d);
         hold = smooth(span + 0.7F, span + 0.04F, d);
      }

      in.fightLower = lower;
      // clash: the server cues both on the same tick; the jolt runs off that shared timestamp
      float clashAge = e.cue() == RutFightState.CUE_CLASH ? e.cueAge(partial) / 20.0F : 99.0F;
      float scale = FightAim.twistScale(fit);
      float seconds = (e.level().getGameTime() + partial) / 20.0F;
      long seed = (long)wa.getId() * 7919L + wb.getId();
      float lockRamp = pose == RutFightState.POSE_LOCK ? smooth(0.0F, 0.7F, clashAge) : 0.0F;
      float roll = FightPose.twist(seed, seconds, false) * scale * lockRamp;
      float heave = FightPose.heave(seed, seconds, false) * scale * lockRamp;
      float jolt = FightPose.clashLift(clashAge, Math.max(0.6F, fit.lever * 1.5F));
      Fighter other = FIGHTERS.get(rival.getId());
      float raise = jolt - (other != null && hold > 0.5F ? other.residualY : 0.0F);
      // ground step between the two: each neck takes half
      float groundLift = (isA ? dy : -dy) * 0.5F;
      // until they touch the heads come down onto the fitted posture without the contact slide
      float useDistance = hold > 0.0F ? Mth.lerp(hold, span, d) : span;
      FightAim.target(m, fit, isA, useDistance, 0.0F, roll, heave, raise + groundLift * hold, f.target);
      in.fightHead = f.target;
      in.fightReach = Math.max(hold, lower * 0.999F);
      // face the rival exactly once the heads are down; slide the body by the neck's shortfall only while locked
      float yawTo = (float)(Math.atan2(isA ? dz : -dz, isA ? dx : -dx) * 180.0 / Math.PI) - 90.0F;
      f.yaw = yawTo;
      f.yawWeight = smooth(0.0F, 0.6F, lower);
      f.slideWeight = hold;
   }

   /** Rendered body yaw: blended onto the line to the rival while fighting. */
   public static float bodyYaw(Whitetail e, float partial, float yaw) {
      Fighter f = FIGHTERS.get(e.getId());
      if (f == null || f.yawWeight <= 0.0F || e.level().getGameTime() - f.seen > 2L) {
         return yaw;
      }

      return yaw + Mth.wrapDegrees(f.yaw - yaw) * f.yawWeight;
   }

   /**
    * After the pose: slide the body (model space, after the renderer's scale) by the horizontal part of what the neck
    * could not reach, and remember the vertical part for the rival.
    */
   public static void slide(Whitetail e, PoseStack stack, DeerAnimator an, float sy) {
      Fighter f = FIGHTERS.get(e.getId());
      if (f == null) {
         return;
      }

      f.residualY = an == null ? 0.0F : an.fightResidual.y * sy;
      if (an != null && f.slideWeight > 0.0F) {
         float w = f.slideWeight;
         float rx = Mth.clamp(an.fightResidual.x, -0.9F, 0.9F) * w;
         float rz = Mth.clamp(an.fightResidual.z, -0.9F, 0.9F) * w;
         if (rx * rx + rz * rz > 1.0E-8F) {
            stack.translate(rx, 0.0F, rz);
         }
      }
   }

   /** Sends this preset's locked distance for a fight (once per pair and preset, refreshed every 10 s). */
   private static void report(Whitetail a, Whitetail b, FightFit fit, String key) {
      long now = a.level().getGameTime();
      String k = a.getId() + ":" + b.getId() + ":" + key;
      Long last = REPORTED.get(k);
      if (last != null && now - last < 200L && now >= last) {
         return;
      }

      if (REPORTED.size() > 64) {
         REPORTED.clear();
      }

      REPORTED.put(k, now);
      Minecraft mc = Minecraft.getInstance();
      if (mc.getConnection() != null) {
         RutFightNet.Report r = new RutFightNet.Report(a.getId(), b.getId(), fit.distance);
         if (mc.getConnection().hasChannel(r.type())) {
            PacketDistributor.sendToServer(r, new CustomPacketPayload[0]);
         }
      }
   }

   /** Drops render state of animals that stopped fighting / left. */
   public static void tidy(long gameTime) {
      if (gameTime - frameTick > 100L) {
         frameTick = gameTime;
         FIGHTERS.values().removeIf(f -> gameTime - f.seen > 100L || gameTime < f.seen);
      }
   }

   /** Classic box model: the head/neck part transform that puts the head bone at {@code target}. */
   public static void classicHead(Matrix4f part, Matrix4f standHead, Matrix4f target, float w) {
      Matrix4f want = new Matrix4f(target).mul(new Matrix4f(standHead).invert());
      if (w >= 0.999F) {
         part.set(want);
      } else {
         // element-wise blend: the part matrices carry the box model's non-uniform pixel scale, keep it intact
         float[] a = part.get(new float[16]);
         float[] b = want.get(new float[16]);

         for (int i = 0; i < 16; i++) {
            a[i] += (b[i] - a[i]) * w;
         }

         part.set(a);
      }
   }
}
