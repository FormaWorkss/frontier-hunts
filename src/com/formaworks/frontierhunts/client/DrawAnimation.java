package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.rifle.RifleItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * First-person "pull out" flourishes, one per firearm (bows are never animated here).
 *
 * <p>Each gun swings up from low right, shows itself off with a piece of its own mechanism - a slide rack, a
 * cylinder spin and flick, a break-open chamber check, a double lever cycle, a pump shuck - and then settles into
 * the ready position with a small overshoot. Every flourish is between 1.8 and 2.7 seconds long, well under the
 * three second cap. Aiming, firing or reloading during the flourish blends it away in a few frames, so it never
 * gets in the way of a shot.
 *
 * <p>{@link #pose} returns offsets {x, y, z, pitch, yaw, roll} on top of the normal hold pose; x, yaw and roll
 * are mirrored by the caller for left-handed players. Negative pitch points the muzzle down.
 */
@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class DrawAnimation {
   private static int lastSlot = -1;
   private static Item lastItem;
   private static double startedAt = Double.NaN;
   private static double lastSeen = Double.NaN;
   private static long lastShot = Long.MIN_VALUE;
   private static double abortAt = Double.NaN;
   private static float abortProgress;
   /** Multiplier the current frame's pose and mechanism channels are scaled by (1 = full flourish). */
   private static float amount = 1.0F;
   private static final int ABORT_TICKS = 5;
   private static final int QUICK_TICKS = 12;

   private DrawAnimation() {
   }

   /**
    * A flourish restarts only when a gun is actually drawn again: switching slot or item, or holding something
    * else in between. Scoping in or going to third person mid-flourish no longer replays it.
    */
   @SubscribeEvent
   public static void tick(ClientTickEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      Item held = mc.player == null ? null : mc.player.getMainHandItem().getItem();
      if (!(held instanceof ExpeditionWeapon w) || w.weapon.bow) {
         lastItem = null;
      }

      if (!(held instanceof RifleItem)) {
         rifleItem = null;
      }

      if (lastItem == null && rifleItem == null) {
         stopDrawSound();
      }
   }

   // ---------------------------------------------------------------- sound

   private static SoundInstance drawSound;

   /** Plays the flourish's foley (frontierhunts:draw_<weapon>) locally, replacing any flourish sound still running. */
   private static void playDrawSound(String weapon) {
      stopDrawSound();
      if (quick()) {
         return;
      }

      try {
         drawSound = new SimpleSoundInstance(
            ResourceLocation.fromNamespaceAndPath("frontierhunts", "draw_" + weapon), SoundSource.PLAYERS, 0.9F, 1.0F,
            SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true
         );
         Minecraft.getInstance().getSoundManager().play(drawSound);
      } catch (RuntimeException | LinkageError e) {
         drawSound = null;
      }
   }

   private static void stopDrawSound() {
      if (drawSound != null) {
         SoundInstance s = drawSound;
         drawSound = null;
         try {
            Minecraft.getInstance().getSoundManager().stop(s);
         } catch (RuntimeException | LinkageError e) {
         }
      }
   }

   private static long predictedShot(ItemStack stack) {
      boolean local = FieldWeaponMesh.localView;
      FieldWeaponMesh.localView = true;
      try {
         return FieldGunEffects.shotTime(stack);
      } finally {
         FieldWeaponMesh.localView = local;
      }
   }

   /** Advance the flourish for the gun in the main hand and return its progress 0..1. */
   public static float progress(Player var0, Weapon var1, double var2) {
      int slot = var0.getInventory().selected;
      ItemStack stack = var0.getMainHandItem();
      Item held = stack.getItem();
      boolean restart = slot != lastSlot || held != lastItem || Double.isNaN(startedAt) || Double.isNaN(lastSeen);
      if (!restart && var2 < lastSeen) {
         // the server's time sync can step game time back a tick or two: slide the flourish along, never replay it
         double back = lastSeen - var2;
         startedAt -= back;
         abortAt -= back;
      }
      long shot = predictedShot(stack);
      if (restart) {
         startedAt = var2;
         abortAt = Double.NaN;
         lastShot = shot;
         amount = 1.0F;
         playDrawSound(var1.id());
      }

      lastSlot = slot;
      lastItem = held;
      lastSeen = var2;
      float p = Mth.clamp((float)((var2 - startedAt) / (double)ticks(var1)), 0.0F, 1.0F);
      if (p < 1.0F && Double.isNaN(abortAt)) {
         boolean interrupted = ExpeditionClient.aiming() || shot != lastShot || FieldWeaponMesh.reloadProgress(stack, var2) > 0.0F;
         if (interrupted) {
            abortAt = var2;
            abortProgress = p;
            stopDrawSound();
         }
      }

      if (!Double.isNaN(abortAt)) {
         float f = Mth.clamp((float)((var2 - abortAt) / ABORT_TICKS), 0.0F, 1.0F);
         amount = 1.0F - ease(f);
         return f >= 1.0F ? 1.0F : abortProgress;
      }

      amount = 1.0F;
      return p;
   }

   private static boolean quick() {
      try {
         return (Boolean)HuntConfig.REDUCED_MOTION.get();
      } catch (RuntimeException | LinkageError e) {
         return false;
      }
   }

   public static int ticks(Weapon var0) {
      if (quick()) {
         return QUICK_TICKS;
      }

      Anim a = anim(var0);
      return a == null ? QUICK_TICKS : a.ticks;
   }

   public static float[] pose(Weapon var0, float var1) {
      var1 = Mth.clamp(var1, 0.0F, 1.0F);
      float[] out = new float[6];
      if (quick() || anim(var0) == null) {
         float r = 1.0F - ease(var1);
         out = new float[]{r * 0.08F, -r * 0.42F, r * 0.1F, -r * 48.0F, r * 18.0F, -r * 24.0F};
         supportAway = 0.0F;
      } else {
         float[] k = anim(var0).sample(var1);
         System.arraycopy(k, 0, out, 0, 6);
         supportAway = k.length > 6 ? Mth.clamp(k[6], 0.0F, 1.0F) * amount : 0.0F;
      }

      for (int i = 0; i < out.length; i++) {
         out[i] *= amount;
      }

      return out;
   }

   /** How far (0..1) the support hand is swung out of view this frame; set by {@link #pose}. */
   static float supportAway;

   public static float supportAway() {
      return supportAway;
   }

   /** 0..1, added to the lever-cycle channel of the lever-action rifle. */
   public static float lever(Weapon var0, float var1) {
      if (var0 != Weapon.LEVER_RIFLE || quick()) {
         return 0.0F;
      }

      return amount * Math.max(window(var1, 14, 18, 22, 54), window(var1, 25, 28, 31, 54));
   }

   /** 0..1, added to the bolt / slide / charging-handle channel. */
   public static float slide(Weapon var0, float var1) {
      if (quick()) {
         return 0.0F;
      }

      float v = switch (var0) {
         case FIELD_PISTOL -> window(var1, 16, 19, 23, 36);
         case SEMI_AUTO_RIFLE -> window(var1, 16, 19, 24, 44);
         case SEMI_AUTO_SHOTGUN -> window(var1, 17, 20, 25, 44);
         case TRANQUILIZER_RIFLE -> window(var1, 18, 21, 26, 44);
         case LEVER_RIFLE -> 0.0F;
         default -> 0.0F;
      };
      return v * amount;
   }

   /** 0..1, added to the pump channel (pump shotgun fore-end, bait launcher tube). */
   public static float pump(Weapon var0, float var1) {
      if (quick()) {
         return 0.0F;
      }

      float v = switch (var0) {
         case PUMP_SHOTGUN -> window(var1, 16, 20, 25, 48);
         case BAIT_LAUNCHER -> window(var1, 16, 20, 26, 44);
         default -> 0.0F;
      };
      return v * amount;
   }

   /** 0..1 break-open angle for the flare gun and the double barrel. */
   public static float breakOpen(Weapon var0, float var1) {
      if (quick()) {
         return 0.0F;
      }

      float v = switch (var0) {
         case FLARE_GUN -> hold(var1, 10, 15, 22, 25, 40);
         case DOUBLE_BARREL -> hold(var1, 9, 14, 27, 30, 54);
         default -> 0.0F;
      };
      return v * amount;
   }

   /**
    * Extra rotation in degrees of the revolver cylinder about its own axis. Also swings the crane out for the spin
    * and flicks it shut again (published through {@link FieldWeaponMesh#drawCrane}).
    */
   public static float cylinderSpin(Weapon var0, float var1) {
      FieldWeaponMesh.drawCrane = crane(var0, var1);
      return spin(var0, var1);
   }

   /** Crane swing-out 0..1 for the revolver flourish (pure; no render state). */
   static float crane(Weapon var0, float var1) {
      if (var0 != Weapon.REVOLVER || quick()) {
         return 0.0F;
      }

      return hold(var1, 10, 14, 30, 33, 52) * amount;
   }

   /** Cylinder spin in degrees for the revolver flourish (pure; no render state). */
   static float spin(Weapon var0, float var1) {
      if (var0 != Weapon.REVOLVER || quick()) {
         return 0.0F;
      }

      float t = Mth.clamp((var1 * 52.0F - 14.0F) / 16.0F, 0.0F, 1.0F);
      float spun = (1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t)) * 1080.0F;
      // when blending out, park the cylinder on the nearest chamber instead of unwinding it
      return amount < 1.0F ? Math.round(spun / 60.0F) * 60.0F : spun;
   }

   /** 0..1 hammer cock. */
   public static float hammer(Weapon var0, float var1) {
      if (quick()) {
         return 0.0F;
      }

      float v = switch (var0) {
         case REVOLVER -> window(var1, 38, 42, 46, 52);
         case DOUBLE_BARREL -> window(var1, 34, 38, 42, 54);
         case LEVER_RIFLE -> window(var1, 14, 18, 22, 54);
         default -> 0.0F;
      };
      return v * amount;
   }

   // ---------------------------------------------------------------- Ridgeline bolt rifle

   private static int rifleSlot = -1;
   private static Item rifleItem;
   private static double rifleStarted = Double.NaN;
   private static double rifleSeen = Double.NaN;
   private static double rifleAbortAt = Double.NaN;
   private static float rifleAbortProgress;
   private static float rifleAmount = 1.0F;
   private static long rifleFired;
   static final int RIFLE_TICKS = 50;
   /** Tick inside the rifle flourish at which the bolt cycle starts; it lasts RifleState.CYCLE_TICKS (26). */
   static final int RIFLE_BOLT_AT = 14;

   static float rifleProgress(Player player, double now, boolean interrupted, long firedAt) {
      int slot = player.getInventory().selected;
      Item held = player.getMainHandItem().getItem();
      boolean restart = slot != rifleSlot || held != rifleItem || Double.isNaN(rifleStarted) || Double.isNaN(rifleSeen);
      if (!restart && now < rifleSeen) {
         double back = rifleSeen - now;
         rifleStarted -= back;
         rifleAbortAt -= back;
      }
      if (restart) {
         rifleStarted = now;
         rifleAbortAt = Double.NaN;
         rifleAmount = 1.0F;
         rifleFired = firedAt;
         playDrawSound("ridgeline_rifle");
      }

      interrupted |= firedAt != rifleFired;

      rifleSlot = slot;
      rifleItem = held;
      rifleSeen = now;
      int total = quick() ? QUICK_TICKS : RIFLE_TICKS;
      float p = Mth.clamp((float)((now - rifleStarted) / total), 0.0F, 1.0F);
      if (p < 1.0F && Double.isNaN(rifleAbortAt) && interrupted) {
         rifleAbortAt = now;
         rifleAbortProgress = p;
         stopDrawSound();
      }

      if (!Double.isNaN(rifleAbortAt)) {
         float f = Mth.clamp((float)((now - rifleAbortAt) / ABORT_TICKS), 0.0F, 1.0F);
         rifleAmount = 1.0F - ease(f);
         return f >= 1.0F ? 1.0F : rifleAbortProgress;
      }

      rifleAmount = 1.0F;
      return p;
   }

   /** Game tick the show-off bolt cycle starts on, fixed for the whole flourish so the bolt moves smoothly. */
   static long rifleBoltStart() {
      return (long)Math.floor(rifleStarted) + RIFLE_BOLT_AT;
   }

   static boolean rifleBlending() {
      return !Double.isNaN(rifleAbortAt);
   }

   static float[] riflePose(float p) {
      float[] out;
      if (quick()) {
         float r = 1.0F - ease(p);
         out = new float[]{r * 0.08F, -r * 0.42F, r * 0.1F, -r * 48.0F, r * 18.0F, -r * 24.0F};
      } else {
         out = java.util.Arrays.copyOf(RIDGELINE.sample(p), 6);
      }

      for (int i = 0; i < out.length; i++) {
         out[i] *= rifleAmount;
      }

      return out;
   }

   /** Progress 0..1 of the show-off bolt cycle, or -1 when the bolt should rest closed. */
   static float rifleBolt(double now) {
      if (quick() || !Double.isNaN(rifleAbortAt)) {
         return -1.0F;
      }

      double tick = now - rifleBoltStart();
      return tick <= 0.0 || tick >= 26.0 ? -1.0F : (float)(tick / 26.0);
   }

   // ---------------------------------------------------------------- choreography

   private static Anim anim(Weapon w) {
      return switch (w) {
         case FIELD_PISTOL -> PISTOL;
         case REVOLVER -> REVOLVER;
         case FLARE_GUN -> FLARE;
         case SEMI_AUTO_RIFLE -> SEMI_RIFLE;
         case SEMI_AUTO_SHOTGUN -> SEMI_SHOTGUN;
         case TRANQUILIZER_RIFLE -> TRANQ;
         case LEVER_RIFLE -> LEVER;
         case PUMP_SHOTGUN -> PUMP;
         case DOUBLE_BARREL -> DOUBLE;
         case BAIT_LAUNCHER -> BAIT;
         default -> null;
      };
   }

   // Keys: {tick, x, y, z, pitch, yaw, roll, away}. The last key is always the rest pose. "away" (0..1) swings the
   // support hand down out of view while the gun is shown off, so the inspect is never hidden behind an arm.
   private static final Anim PISTOL = new Anim(36, new float[][]{
      {0, 0.1F, -0.55F, 0.1F, -55, 20, -20, 0},
      {7, 0, -0.05F, 0.06F, -10, 14, -8, 0.8F},
      {12, -0.07F, 0.05F, 0.18F, 6, 30, -16, 1},
      {16, -0.08F, 0.055F, 0.19F, 7, 32, -18, 1},
      {20, -0.08F, 0.03F, 0.2F, 1, 32, -17, 1},
      {24, -0.02F, 0.02F, 0.06F, 4, 8, -4, 0.6F},
      {28, 0, 0.004F, 0, -2.5F, -1, 2, 0.1F},
      {32, 0, -0.002F, 0, 1, 0, -0.8F, 0},
      {36, 0, 0, 0, 0, 0, 0, 0}
   });
   private static final Anim REVOLVER = new Anim(52, new float[][]{
      {0, 0.1F, -0.55F, 0.1F, -55, 20, -20, 0},
      {7, 0, -0.06F, 0.04F, -12, 14, -8, 0.8F},
      {11, -0.07F, 0.06F, 0.2F, -55, 22, 16, 1},
      {22, -0.08F, 0.07F, 0.21F, -60, 24, 18, 1},
      {29, -0.07F, 0.06F, 0.2F, -56, 22, 16, 1},
      {32, -0.02F, 0.05F, 0.08F, 16, 10, -6, 1},
      {36, 0, -0.01F, 0, -3, 2, -2, 0.4F},
      {44, 0, 0.002F, 0, -1.5F, 0, 0.6F, 0.05F},
      {52, 0, 0, 0, 0, 0, 0, 0}
   });
   private static final Anim FLARE = new Anim(40, new float[][]{
      {0, 0.1F, -0.55F, 0.1F, -55, 20, -20, 0},
      {7, 0, -0.05F, 0.05F, -10, 14, -8, 0.8F},
      {11, -0.06F, 0.05F, 0.18F, -30, 26, -10, 1},
      {18, -0.07F, 0.06F, 0.19F, -34, 28, -12, 1},
      {22, -0.05F, 0.03F, 0.15F, -20, 22, -8, 1},
      {25, -0.01F, 0.04F, 0.06F, 14, 6, -2, 1},
      {30, 0, -0.004F, 0, -2, 0, 1, 0.3F},
      {34, 0, 0.001F, 0, 0.8F, 0, -0.3F, 0},
      {40, 0, 0, 0, 0, 0, 0, 0}
   });
   private static final Anim SEMI_RIFLE = new Anim(44, new float[][]{
      {0, 0.14F, -0.6F, 0.12F, -58, 30, -28, 0},
      {8, 0.02F, -0.08F, 0.04F, -10, 18, -8, 0.8F},
      {13, -0.09F, 0.08F, 0.14F, 4, 28, -20, 1},
      {18, -0.1F, 0.06F, 0.15F, 1, 30, -22, 1},
      {22, -0.1F, 0.075F, 0.15F, 4, 29, -20, 1},
      {27, 0, 0, 0.03F, 3, 5, 4, 0.5F},
      {31, 0, -0.006F, 0, -2.5F, -1, -2, 0.1F},
      {36, 0, 0.002F, 0, 0.8F, 0, 0.6F, 0},
      {44, 0, 0, 0, 0, 0, 0, 0}
   });
   private static final Anim SEMI_SHOTGUN = new Anim(44, new float[][]{
      {0, 0.14F, -0.6F, 0.12F, -58, 30, -28, 0},
      {8, 0.02F, -0.09F, 0.04F, -12, 18, -10, 0.8F},
      {14, -0.09F, 0.075F, 0.14F, 3, 28, -24, 1},
      {19, -0.1F, 0.05F, 0.15F, -2, 30, -26, 1},
      {23, -0.1F, 0.07F, 0.15F, 4, 29, -23, 1},
      {28, 0, 0, 0.03F, 3, 5, 5, 0.5F},
      {32, 0, -0.007F, 0, -3, -1, -2.5F, 0.1F},
      {37, 0, 0.002F, 0, 1, 0, 0.7F, 0},
      {44, 0, 0, 0, 0, 0, 0, 0}
   });
   private static final Anim TRANQ = new Anim(44, new float[][]{
      {0, 0.14F, -0.6F, 0.12F, -58, 30, -28, 0},
      {8, 0.02F, -0.08F, 0.04F, -10, 18, -8, 0.8F},
      {14, -0.08F, 0.11F, 0.14F, 14, 24, -14, 1},
      {19, -0.09F, 0.08F, 0.15F, 9, 26, -18, 1},
      {24, -0.09F, 0.09F, 0.15F, 12, 25, -16, 1},
      {29, 0, 0, 0.03F, 2, 4, 4, 0.5F},
      {33, 0, -0.006F, 0, -2.5F, -1, -2, 0.1F},
      {38, 0, 0.002F, 0, 0.8F, 0, 0.6F, 0},
      {44, 0, 0, 0, 0, 0, 0, 0}
   });
   private static final Anim LEVER = new Anim(54, new float[][]{
      {0, 0.14F, -0.6F, 0.12F, -58, 30, -28, 0},
      {8, 0.02F, -0.08F, 0.04F, -8, 16, -6, 0.8F},
      {13, -0.07F, 0.09F, 0.13F, 10, 24, 14, 1},
      {18, -0.07F, 0.07F, 0.14F, 16, 24, 18, 1},
      {22, -0.07F, 0.09F, 0.13F, 6, 24, 14, 1},
      {28, -0.06F, 0.08F, 0.12F, 12, 22, 12, 1},
      {32, -0.02F, 0.02F, 0.05F, 3, 6, 2, 0.6F},
      {36, 0, -0.008F, 0, -3, -1, -3, 0.2F},
      {42, 0, 0.002F, 0, 1, 0, 1, 0},
      {54, 0, 0, 0, 0, 0, 0, 0}
   });
   private static final Anim PUMP = new Anim(48, new float[][]{
      {0, 0.14F, -0.6F, 0.12F, -58, 30, -28, 0},
      {8, 0.02F, -0.08F, 0.04F, -10, 16, -10, 0},
      {13, -0.07F, 0.07F, 0.12F, 2, 24, -14, 0},
      {19, -0.07F, 0.03F, 0.14F, -6, 24, -16, 0},
      {24, -0.07F, 0.09F, 0.12F, 8, 23, -12, 0},
      {29, 0, 0, 0.03F, 3, 4, 4, 0},
      {33, 0, -0.006F, 0, -2.5F, -0.5F, -2, 0},
      {38, 0, 0.002F, 0, 0.8F, 0, 0.5F, 0},
      {48, 0, 0, 0, 0, 0, 0, 0}
   });
   private static final Anim DOUBLE = new Anim(54, new float[][]{
      {0, 0.14F, -0.6F, 0.12F, -58, 30, -28, 0},
      {8, 0.02F, -0.08F, 0.05F, -12, 16, -10, 0.8F},
      {14, -0.08F, 0.09F, 0.09F, -22, 24, -10, 1},
      {24, -0.09F, 0.1F, 0.1F, -26, 26, -12, 1},
      {27, -0.08F, 0.09F, 0.08F, -14, 22, -8, 1},
      {30, -0.02F, 0.06F, 0.06F, 16, 8, -3, 1},
      {34, 0, -0.01F, 0, -3, 1, -2, 0.4F},
      {40, 0, 0.003F, 0, 1.2F, 0, 0.8F, 0.05F},
      {46, 0, -0.001F, 0, -0.4F, 0, -0.2F, 0},
      {54, 0, 0, 0, 0, 0, 0, 0}
   });
   private static final Anim BAIT = new Anim(44, new float[][]{
      {0, 0.14F, -0.6F, 0.12F, -58, 30, -28, 0},
      {8, 0.02F, -0.08F, 0.04F, -10, 16, -10, 0},
      {13, -0.07F, 0.08F, 0.12F, 6, 24, -14, 0},
      {20, -0.07F, 0.04F, 0.14F, -2, 25, -16, 0},
      {25, -0.07F, 0.09F, 0.12F, 7, 24, -12, 0},
      {30, 0, 0, 0.03F, 3, 4, 4, 0},
      {34, 0, -0.006F, 0, -2.5F, -0.5F, -2, 0},
      {38, 0, 0.002F, 0, 0.8F, 0, 0.5F, 0},
      {44, 0, 0, 0, 0, 0, 0, 0}
   });
   /** Ridgeline bolt rifle: raise, roll the bolt side up, run the bolt, swing back to ready. */
   private static final Anim RIDGELINE = new Anim(RIFLE_TICKS, new float[][]{
      {0, 0.14F, -0.6F, 0.12F, -58, 30, -34},
      {8, 0.04F, -0.1F, 0.04F, -10, 16, -8},
      {13, 0.03F, 0.0F, 0.06F, 8, 14, 26},
      {27, 0.04F, -0.01F, 0.07F, 10, 14, 30},
      {40, 0.03F, 0.0F, 0.06F, 6, 12, 24},
      {44, 0.01F, 0.0F, 0.01F, 2, 2, -3},
      {47, 0.0F, -0.004F, 0.0F, -1.5F, 0, 1},
      {50, 0, 0, 0, 0, 0, 0}
   });

   // ---------------------------------------------------------------- helpers

   private static float ease(float var0) {
      var0 = Mth.clamp(var0, 0.0F, 1.0F);
      return var0 * var0 * (3.0F - 2.0F * var0);
   }

   /** Rise from a to b, fall from b to c; ticks within an animation of length total. */
   private static float window(float p, float a, float b, float c, float total) {
      float t = p * total;
      if (t <= a || t >= c) {
         return 0.0F;
      }

      return t < b ? ease((t - a) / (b - a)) : 1.0F - ease((t - b) / (c - b));
   }

   /** Rise a..b, hold, fall c..d. */
   private static float hold(float p, float a, float b, float c, float d, float total) {
      float t = p * total;
      if (t <= a || t >= d) {
         return 0.0F;
      }

      if (t < b) {
         return ease((t - a) / (b - a));
      }

      return t <= c ? 1.0F : 1.0F - ease((t - c) / (d - c));
   }

   /** Smooth keyframed curve through six or seven channels (cubic Hermite with Catmull-Rom style tangents). */
   private static final class Anim {
      final int ticks;
      final float[] times;
      final float[][] values;
      final float[][] tangents;

      Anim(int ticks, float[][] keys) {
         this.ticks = ticks;
         int n = keys.length;
         this.times = new float[n];
         int ch = keys[0].length - 1;
         this.values = new float[n][ch];
         for (int i = 0; i < n; i++) {
            this.times[i] = keys[i][0] / ticks;
            System.arraycopy(keys[i], 1, this.values[i], 0, ch);
         }

         this.tangents = new float[n][ch];
         for (int i = 1; i < n - 1; i++) {
            float dt = this.times[i + 1] - this.times[i - 1];
            for (int c = 0; c < ch; c++) {
               this.tangents[i][c] = (this.values[i + 1][c] - this.values[i - 1][c]) / dt;
            }
         }
      }

      float[] sample(float p) {
         int ch = this.values[0].length;
         float[] out = new float[ch];
         int n = this.times.length;
         if (p <= this.times[0]) {
            System.arraycopy(this.values[0], 0, out, 0, ch);
            return out;
         }

         if (p >= this.times[n - 1]) {
            return out;
         }

         int i = 0;
         while (i < n - 2 && p > this.times[i + 1]) {
            i++;
         }

         float h = this.times[i + 1] - this.times[i];
         float s = (p - this.times[i]) / h;
         float s2 = s * s;
         float s3 = s2 * s;
         float h00 = 2.0F * s3 - 3.0F * s2 + 1.0F;
         float h10 = s3 - 2.0F * s2 + s;
         float h01 = -2.0F * s3 + 3.0F * s2;
         float h11 = s3 - s2;
         for (int c = 0; c < ch; c++) {
            out[c] = h00 * this.values[i][c] + h10 * h * this.tangents[i][c] + h01 * this.values[i + 1][c] + h11 * h * this.tangents[i + 1][c];
         }

         return out;
      }
   }
}
