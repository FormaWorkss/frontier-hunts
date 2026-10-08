package com.formaworks.frontierhunts.wingshot.client;

import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import com.formaworks.frontierhunts.wingshot.WingshotConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.WeakHashMap;
import net.minecraft.util.Mth;

/**
 * [wingshot] Per-bird animation state on the client and the whole-body transform applied before the bird is drawn
 * (either look): bank into turns, pitch with the climb and for take-off / landing / drumming, the tumbling fall of a
 * shot bird and how it comes to rest (on its side on the ground, low and tilted on water), and the slow-motion
 * replay offset of a wing shot ({@link WingShotMoment}).
 */
public final class BirdRender {
   private static final WeakHashMap<WildlifeMob, Track> TRACKS = new WeakHashMap<>();

   private BirdRender() {
   }

   /** what the renderer measures of a bird between frames */
   static final class Track {
      final BirdAnim.State state;
      double lx, ly, lz;
      float lyaw, ltime = Float.NaN;
      float vh, vy, yawRate;
      float frameTime = Float.NaN;

      Track(WildlifeMob e) {
         this.state = new BirdAnim.State(e.getId() * 977 + (int)e.getUUID().getLeastSignificantBits(), e.species == WildlifeSpecies.GROUSE);
      }
   }

   static Track track(WildlifeMob e) {
      return TRACKS.computeIfAbsent(e, Track::new);
   }

   /** The state as last advanced (for the box model's setupAnim, which runs inside the render). */
   public static BirdAnim.State current(WildlifeMob e) {
      Track t = TRACKS.get(e);
      return t == null ? null : t.state;
   }

   /** Advance the bird's animation to this frame (once per frame; the shadow pass reuses it). */
   public static BirdAnim.State anim(WildlifeMob e, float pt) {
      Track t = track(e);
      float real = e.tickCount + pt;
      float now = WingShotMoment.time(e, real);
      if (now == t.frameTime) {
         return t.state;
      }
      t.frameTime = now;
      double x, y, z;
      double[] rp = WingShotMoment.position(e, pt);
      if (rp != null) {
         x = rp[0];
         y = rp[1];
         z = rp[2];
      } else {
         x = Mth.lerp(pt, e.xo, e.getX());
         y = Mth.lerp(pt, e.yo, e.getY());
         z = Mth.lerp(pt, e.zo, e.getZ());
      }
      float yaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
      if (!Float.isNaN(t.ltime) && now > t.ltime) {
         float dt = now - t.ltime;
         double dx = x - t.lx, dz = z - t.lz, dyy = y - t.ly;
         if (dx * dx + dyy * dyy + dz * dz > 25.0) {
            // teleported / just tracked: no fake velocity
            dx = 0.0;
            dz = 0.0;
            dyy = 0.0;
         }
         float vh = (float)Math.sqrt(dx * dx + dz * dz) / dt, vy = (float)dyy / dt;
         float yr = Mth.wrapDegrees(yaw - t.lyaw) * Mth.DEG_TO_RAD / dt;
         float k = 1.0F - (float)Math.exp(-dt / 2.5F);
         t.vh += (vh - t.vh) * k;
         t.vy += (vy - t.vy) * k;
         t.yawRate += (Mth.clamp(yr, -0.6F, 0.6F) - t.yawRate) * (1.0F - (float)Math.exp(-dt / 3.5F));
      }
      t.lx = x;
      t.ly = y;
      t.lz = z;
      t.lyaw = yaw;
      t.ltime = now;
      boolean dead = e.isDeadOrDying();
      boolean water = e.isInWater();
      boolean down = dead && (e.onGround() || water || t.vh < 0.02F && Math.abs(t.vy) < 0.02F && !Float.isNaN(t.state.time) && t.state.sinceDeath() > 10.0F);
      if (rp != null && dead) {
         down = rp[3] > 0.5;
         water = rp[4] > 0.5;
      }
      BirdAnim.step(t.state, e.flightPhase(), now, t.vh, t.vy, t.yawRate, dead, down, water, e.onGround());
      return t.state;
   }

   /** Birds animate every frame while flying or dying (a 5-15 Hz wingbeat sampled at 20 Hz would stutter). */
   public static boolean fullRate(WildlifeMob e) {
      Track t = TRACKS.get(e);
      return e.isDeadOrDying() || e.flightPhase() != 0 || t != null && (t.state.wingsOut || t.state.limp > 0.01F);
   }

   /** Shot birds never do the vanilla tip-over: their death pose is ours. */
   public static boolean customDeath(WildlifeMob e) {
      return e.species.bird && WingshotConfig.flight();
   }

   /**
    * Push the bird's body transform (call before the body is drawn; pop after). Returns false (nothing pushed) when
    * there is nothing to do.
    */
   public static boolean push(WildlifeMob e, float pt, PoseStack ps) {
      BirdAnim.State s = anim(e, pt);
      double[] off = WingShotMoment.offset(e, pt);
      boolean dead = s.limp > 0.01F;
      if (off == null && Math.abs(s.bank) < 0.002F && Math.abs(s.pitch) < 0.002F && !dead) {
         return false;
      }
      ps.pushPose();
      if (off != null) {
         ps.translate(off[0], off[1], off[2]);
      }
      float yaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
      float h = e.getBbHeight() * 0.5F;
      if (e.getPose() == net.minecraft.world.entity.Pose.FALL_FLYING) {
         h = e.species == WildlifeSpecies.GROUSE ? 0.16F : 0.19F; // pivot about the body, not the flat flying box
      }
      ps.translate(0.0F, h, 0.0F);
      ps.mulPose(Axis.YP.rotationDegrees(-yaw));
      // local frame: +z forward, +x the bird's left
      if (!dead) {
         ps.mulPose(Axis.ZP.rotation(s.bank));
         ps.mulPose(Axis.XP.rotation(-s.pitch));
      } else {
         float tumble = s.tumble;
         if (s.down) {
            // comes to rest on its side (on water lower in the water and less rolled), eased in from the tumble
            float settle = Math.min(1.0F, s.sinceDown() / 6.0F);
            float roll = s.water ? s.restRoll() * 0.55F : s.restRoll();
            float bob = s.water ? Mth.sin(s.time() * 0.15F) * 0.05F : 0.0F;
            ps.mulPose(Axis.ZP.rotation(Mth.lerp(settle, wrap(tumble), roll) + bob));
            ps.mulPose(Axis.XP.rotation(Mth.lerp(settle, wrap(tumble * 0.6F), 0.08F)));
            ps.translate(0.0F, -h * 0.55F * settle, 0.0F);
         } else {
            // tumbling down: mostly over the wings, some end over end
            ps.mulPose(Axis.ZP.rotation(tumble));
            ps.mulPose(Axis.XP.rotation(tumble * 0.6F + 0.4F));
         }
      }
      ps.mulPose(Axis.YP.rotationDegrees(yaw));
      ps.translate(0.0F, -h, 0.0F);
      return true;
   }

   private static float wrap(float a) {
      a %= Mth.TWO_PI;
      if (a > Mth.PI) {
         a -= Mth.TWO_PI;
      } else if (a < -Mth.PI) {
         a += Mth.TWO_PI;
      }
      return a;
   }

   /** forget everything (level change) */
   static void clear() {
      TRACKS.clear();
   }
}
