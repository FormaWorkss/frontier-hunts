package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.client.trailcam.Darkroom;
import com.formaworks.frontierhunts.prone.Prone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * [bows] Hold sway of a drawn bow, applied to the real aim (the same {@link HoldSway} curve as the rifles, at bow
 * amplitudes). Because the view itself moves, the sight pin, the arrow tip and the arrow's flight all move together:
 * what you see at release is where it goes.
 *
 * <p>Fresh at anchor the hold floats a little and settles within a second; standing it wanders about a tenth of a
 * degree, kneeling (sneaking) about half that, prone a third. Holding past the steady window (3 s) fatigue grows the
 * wander smoothly and modestly (BowHold.strain, capped). Every change of amplitude glides through the curve's
 * critically damped envelopes: no snaps, no jitter, frame-rate independent.
 *
 * <p>Also sends the exact aim right before the release, so the server launches along the view the player saw (the
 * normal movement packet would be up to a tick old).
 */
@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class BowSway {
   /** Bow hold multiples of HoldSway's rifle constants (0.04 deg drift, 0.06 deg breath). */
   private static final double DRIFT = 3.0;
   private static final double BREATH = 2.5;

   private static final HoldSway SWAY = new HoldSway(System.nanoTime() ^ 0x5EEDB0B5L);
   private static long lastNanos = Long.MIN_VALUE;
   private static double appliedPitch;
   private static double appliedYaw;
   private static long anchoredAt = Long.MIN_VALUE;

   private BowSway() {
   }

   @SubscribeEvent
   public static void frame(RenderFrameEvent.Pre event) {
      Minecraft mc = Minecraft.getInstance();
      long now = System.nanoTime();
      double dt = lastNanos == Long.MIN_VALUE ? 0.0 : (now - lastNanos) / 1.0E9;
      lastNanos = now;
      LocalPlayer p = mc.player;
      if (p == null || mc.level == null) {
         SWAY.reset();
         appliedPitch = 0.0;
         appliedYaw = 0.0;
         anchoredAt = Long.MIN_VALUE;
         return;
      }
      if (mc.isPaused()) {
         return;
      }
      float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
      ItemStack stack = p.getMainHandItem();
      float aim = FieldBowAim.drawn(stack) && mc.options.getCameraType().isFirstPerson() ? FieldBowAim.aimFactor(p, pt) : 0.0F;
      if (aim > 0.98F) {
         if (anchoredAt == Long.MIN_VALUE) {
            anchoredAt = now;
         }
      } else {
         anchoredAt = Long.MIN_VALUE;
      }
      double drift = 0.0;
      double breath = 0.0;
      if (aim > 1.0E-4F && !HuntConfig.REDUCED_MOTION.get()) {
         double stanceDrift;
         double stanceBreath;
         if (Prone.isProne(p)) {
            stanceDrift = 0.3;
            stanceBreath = 0.35;
         } else if (p.isCrouching()) {
            stanceDrift = 0.55;
            stanceBreath = 0.6;
         } else {
            stanceDrift = 1.0;
            stanceBreath = 1.0;
         }
         if (p.getDeltaMovement().horizontalDistanceSqr() > 9.0E-4) {
            stanceDrift *= 1.8;
            stanceBreath *= 1.3;
         }
         float strain = Math.min(1.5F, FieldBowAim.strain(p, stack));
         double since = anchoredAt == Long.MIN_VALUE ? 0.0 : (now - anchoredAt) / 1.0E9;
         double settle = 1.0 + 0.6 * Math.exp(-since / 0.7);
         int mask = com.formaworks.frontierhunts.journal.HunterSkills.clientMask;
         float strength = com.formaworks.frontierhunts.journal.HunterSkills.clientStrength;
         settle = 1.0 + (settle - 1.0) * com.formaworks.frontierhunts.journal.HunterSkills.settle(mask, strength);
         double cold = com.formaworks.frontierhunts.survival.SurvivalApi.clientSway()
            * com.formaworks.frontierhunts.outfitter.OutfitterItem.sticks(net.minecraft.client.Minecraft.getInstance().player); // [1.1.7] shooting sticks
         drift = DRIFT * stanceDrift * (1.0 + 1.6 * strain) * settle * cold * aim
            * com.formaworks.frontierhunts.journal.HunterSkills.swayDrift(mask, strength);
         breath = BREATH * stanceBreath * (1.0 + 0.5 * strain) * (1.0 + (cold - 1.0) * 0.5) * aim
            * com.formaworks.frontierhunts.journal.HunterSkills.swayBreath(mask, strength);
      }
      SWAY.advance(dt, drift, breath);
      double wantPitch = SWAY.pitch();
      double wantYaw = SWAY.yaw();
      if (mc.screen != null || KillCamClient.active() || Darkroom.active() || !Double.isFinite(wantPitch + wantYaw)) {
         appliedPitch = Double.isFinite(wantPitch) ? wantPitch : 0.0; // the view is not ours: accept, never catch up later
         appliedYaw = Double.isFinite(wantYaw) ? wantYaw : 0.0;
         return;
      }
      double dp = wantPitch - appliedPitch;
      double dy = wantYaw - appliedYaw;
      if (dp != 0.0) {
         float before = p.getXRot();
         float after = Mth.clamp(before + (float)dp, -90.0F, 90.0F);
         float applied = after - before;
         p.setXRot(after);
         p.xRotO = Mth.clamp(p.xRotO + applied, -90.0F, 90.0F);
         appliedPitch += applied;
      }
      if (dy != 0.0) {
         float before = p.getYRot();
         float after = before + (float)dy;
         float applied = after - before;
         p.setYRot(after);
         p.yRotO += applied;
         appliedYaw += applied;
      }
   }

   /**
    * The release happens in this tick's key handling, before the player's movement packet: send the aim the player is
    * looking at right now first. Only while standing still on the ground (a rotation-only packet tells the server the
    * player did not move this tick, which is then true).
    */
   @SubscribeEvent
   public static void beforeRelease(ClientTickEvent.Pre event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      if (p == null || mc.getConnection() == null || mc.screen != null || !p.isUsingItem()) {
         return;
      }
      if (!FieldBowAim.drawn(p.getUseItem()) || mc.options.keyUse.isDown()) {
         return;
      }
      if (!p.onGround() || p.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4 || p.isPassenger()) {
         return;
      }
      mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(p.getYRot(), p.getXRot(), p.onGround()));
   }

   @SubscribeEvent
   public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
      SWAY.reset();
      appliedPitch = 0.0;
      appliedYaw = 0.0;
      anchoredAt = Long.MIN_VALUE;
      lastNanos = Long.MIN_VALUE;
   }
}
