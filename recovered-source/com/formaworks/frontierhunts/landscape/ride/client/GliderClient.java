package com.formaworks.frontierhunts.landscape.ride.client;

import com.formaworks.frontierhunts.HuntNetwork;
import com.formaworks.frontierhunts.client.FrontierClient;
import com.formaworks.frontierhunts.landscape.ride.Lift;
import com.formaworks.frontierhunts.landscape.ride.Paraglider;
import com.formaworks.frontierhunts.landscape.ride.RideContent;
import com.formaworks.frontierhunts.landscape.ride.RidePayloads;
import com.formaworks.frontierhunts.landscape.ride.Wingsuit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.Clone;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class GliderClient {
   static final double TRIM = 1.6;
   static final double BAR = 2.6;
   static final double BRAKES = 0.9;
   static final double DIVE = 1.9;
   static final double MAX = 4.2;
   static GliderClient.Kit kit = GliderClient.Kit.WING;
   private static double[] handoff;
   private static LocalPlayer pilot;
   static boolean active;
   static boolean folding;
   private static int foldingTicks;
   static double airspeed = 1.6;
   static double heading;
   static double turnRate;
   static double bank;
   static double climb;
   static double vy;
   static double lastVx;
   static double lastVz;
   static int openTicks;
   static float forwardIn;
   static float sideIn;
   private static boolean jumpHeld;
   private static boolean sneakHeld;
   private static GliderClient.WindSound sound;
   private static int varioTimer;
   private static float roll;
   private static float rollO;
   static Double autopilot;

   private GliderClient() {
   }

   static GliderClient.Kit kitOf(Player var0) {
      return Paraglider.gliding(var0) ? GliderClient.Kit.WING : (Wingsuit.canopyOpen(var0) ? GliderClient.Kit.CANOPY : null);
   }

   @SubscribeEvent
   public static void loggingOut(LoggingOut var0) {
      end();
      pilot = null;
      folding = false;
      handoff = null;
   }

   @SubscribeEvent
   public static void respawn(Clone var0) {
      end();
      pilot = null;
      folding = false;
      handoff = null;
   }

   static void handoff(double var0, double var2, double var4) {
      handoff = new double[]{var0, var2, var4};
   }

   static double sink(double var0) {
      return kit.sink(var0);
   }

   static double[] wind() {
      HuntNetwork.Snapshot var0 = FrontierClient.state;
      return var0 == null ? new double[]{0.0, 0.0} : new double[]{(double)var0.windEast() / 20.0, (double)var0.windSouth() / 20.0};
   }

   static boolean canOpen(LocalPlayer var0) {
      return !Paraglider.held(var0).isEmpty()
         && !Paraglider.gliding(var0)
         && !var0.onGround()
         && !var0.isInWater()
         && !var0.isPassenger()
         && !var0.getAbilities().flying
         && !var0.isFallFlying()
         && var0.getDeltaMovement().y < -0.12
         && Paraglider.clearance(var0, 3.0) >= 2.5;
   }

   static void open(LocalPlayer var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.gameMode != null) {
         var1.gameMode.useItem(var0, Paraglider.heldHand(var0));
      }
   }

   @SubscribeEvent
   public static void input(MovementInputUpdateEvent var0) {
      if (var0.getEntity() instanceof LocalPlayer var1) {
         Input var6 = var0.getInput();
         boolean var3 = var6.jumping;
         boolean var4 = var6.shiftKeyDown;
         GliderClient.Kit var5 = kitOf(var1);
         if (var5 == null) {
            if (var3 && !jumpHeld && canOpen(var1) && !LaunchCinematic.running()) {
               open(var1);
            }
         } else if (!var1.onGround()) {
            forwardIn = var6.forwardImpulse;
            sideIn = var6.leftImpulse;
            if (var5 == GliderClient.Kit.WING && var4 && !sneakHeld && !LaunchCinematic.running() && !folding) {
               PacketDistributor.sendToServer(new RidePayloads.Glider((byte)1, 0.0F), new CustomPacketPayload[0]);
               folding = true;
               end();
            }

            if (var5 == GliderClient.Kit.CANOPY && var3 && !jumpHeld && active && openTicks >= GliderClient.Kit.CANOPY.fill + 4 && !LaunchCinematic.running()) {
               WingsuitClient.stow(var1);
            }

            var6.forwardImpulse = 0.0F;
            var6.leftImpulse = 0.0F;
            var6.up = var6.down = var6.left = var6.right = false;
            var6.jumping = false;
            var6.shiftKeyDown = false;
         }

         jumpHeld = var3;
         sneakHeld = var4;
      }
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      if (var0.getEntity() instanceof LocalPlayer var1 && var1 == Minecraft.getInstance().player) {
         if (folding) {
            if (kitOf(var1) != null && ++foldingTicks < 40) {
               return;
            }

            folding = false;
            foldingTicks = 0;
         }

         GliderClient.Kit var45 = kitOf(var1);
         if (active && (var45 != kit || var1 != pilot)) {
            end();
         }

         boolean var3 = var45 != null
            && !var1.onGround()
            && !var1.isInWater()
            && !var1.isPassenger()
            && !var1.getAbilities().flying
            && (var45 == GliderClient.Kit.CANOPY || !var1.isFallFlying());
         if (!var3) {
            if (active) {
               if (var1.onGround() && vy < -0.9 && var1.isAlive()) {
                  PacketDistributor.sendToServer(new RidePayloads.Glider((byte)2, (float)(-vy * 0.8)), new CustomPacketPayload[0]);
               }

               end();
            }

            return;
         }

         if (!active) {
            kit = var45;
            begin(var1);
         }

         openTicks++;
         boolean var4 = kit == GliderClient.Kit.CANOPY;
         float var5 = autopilot != null ? 6.0F : var1.getXRot();
         double var6 = Mth.clamp((double)(var5 - 12.0F) / 48.0, 0.0, 1.0);
         double var8 = Mth.clamp((double)(-var5 - 8.0F) / 30.0, 0.0, 1.0);
         double var10 = (double)forwardIn > 0.1 ? kit.bar : ((double)forwardIn < -0.1 ? kit.brakes : kit.trim);
         double var12 = var10 + var6 * kit.dive - var8 * (var4 ? 0.15 : 0.5);
         if (var4) {
            if ((double)forwardIn < -0.1) {
               var8 = Math.max(var8, 1.0);
            }

            if (openTicks < kit.fill) {
               double var14 = openTicks < 6 ? 0.02 : 0.16;
               airspeed = airspeed + (var12 - airspeed) * var14;
            } else {
               airspeed = Mth.clamp(airspeed + (var12 - airspeed) * (var12 > airspeed ? 0.05 : 0.06 + 0.06 * var8), 0.2, kit.max);
            }
         } else {
            double var46 = openTicks < 20 ? 0.12 : (var12 > airspeed ? 0.03 + 0.05 * var6 : 0.025 + 0.04 * var8);
            airspeed = Mth.clamp(airspeed + (var12 - airspeed) * var46, 0.5, kit.max);
         }

         double var47 = var4 ? Mth.clamp(1.6 + airspeed * 3.2, 1.8, 5.5) : 2.6 + airspeed * 0.9;
         double var16;
         if (autopilot != null) {
            var16 = Mth.clamp(Mth.wrapDegrees(autopilot - heading) * 0.15, -2.0, 2.0);
         } else if ((double)Math.abs(sideIn) > 0.1) {
            var16 = (double)(-Math.signum(sideIn)) * var47 * 1.25;
         } else {
            var16 = Mth.clamp(Mth.wrapDegrees((double)var1.getYRot() - heading) * 0.2, -var47, var47);
         }

         if (var4 && openTicks < kit.fill) {
            var16 *= 0.2;
         }

         turnRate = turnRate + (var16 - turnRate) * 0.22;
         heading = Mth.wrapDegrees(heading + turnRate);
         double var18 = turnRate * (float) (Math.PI / 180.0) * 20.0;
         double var20 = airspeed * 20.0;
         bank = Mth.clamp(Math.toDegrees(Math.atan(var18 * var20 / 9.81)), -70.0, 70.0);
         double[] var22 = wind();
         double var23 = Paraglider.clearance(var1, 256.0);
         double var25 = 0.7 + Math.min(0.5, var23 / 200.0);
         double var27 = Lift.at(var1.level(), var1.getX(), var1.getY(), var1.getZ(), var22[0], var22[1]) * kit.lift;
         double var29 = sink(airspeed) / Math.max(0.35, Math.cos(Math.toRadians(bank))) + airspeed * Math.sin(Math.toRadians(var6 * 38.0)) * 0.9;
         double var31;
         if (var4) {
            var31 = var8 > 0.0 && airspeed > 0.34 && openTicks >= kit.fill ? var8 * (airspeed - 0.3) * 0.55 : 0.0;
            if (var31 > 0.0) {
               airspeed = Math.max(0.2, airspeed - var8 * 0.012);
            }
         } else {
            var31 = var8 > 0.0 && airspeed > 1.1 ? var8 * (airspeed - 1.0) * 0.4 : 0.0;
            if (var31 > 0.0) {
               airspeed = Math.max(0.5, airspeed - var8 * 0.045);
            }
         }

         double var33 = -var29 + var27 + var31;
         if (var4 && airspeed < 0.21 && var8 > 0.5) {
            var33 -= 0.14;
         }

         if (var4 && openTicks < kit.fill) {
            if (openTicks < 6) {
               vy = vy + (Math.min(vy, -0.5) - vy) * 0.05;
            } else {
               vy = vy + (-kit.sink(kit.trim) - vy) * 0.22;
            }
         } else if (!var4 && openTicks < 14) {
            double var35 = (double)openTicks / 14.0;
            var33 = Mth.lerp(var35 * var35, Math.min(vy, -0.25), var33);
            airspeed = Math.min(4.2, airspeed + Math.max(0.0, -vy) * 0.08);
         }

         if (!var4 || openTicks >= kit.fill) {
            vy = vy + (var33 - vy) * 0.22;
         }

         double var48 = Math.toRadians(heading);
         double var37 = -Math.sin(var48) * airspeed + var22[0] * var25;
         double var39 = Math.cos(var48) * airspeed + var22[1] * var25;
         if (var1.horizontalCollision && openTicks > 10) {
            double var41 = Math.hypot(var1.getX() - var1.xo, var1.getZ() - var1.zo);
            double var43 = Math.hypot(lastVx, lastVz) - var41;
            if (var43 > (var4 ? 0.35 : 0.6)) {
               PacketDistributor.sendToServer(new RidePayloads.Glider((byte)2, (float)var43), new CustomPacketPayload[0]);
               folding = true;
               end();
               return;
            }
         }

         climb = vy;
         lastVx = var37;
         lastVz = var39;
         var1.setDeltaMovement(var37, vy, var39);
         var1.resetFallDistance();
         vario(var1);
         if (sound == null || sound.isStopped()) {
            sound = new GliderClient.WindSound(var1);
            Minecraft.getInstance().getSoundManager().play(sound);
         }

         return;
      }
   }

   private static void begin(LocalPlayer var0) {
      active = true;
      openTicks = 0;
      pilot = var0;
      Vec3 var1 = var0.getDeltaMovement();
      double var2 = Math.hypot(var1.x, var1.z);
      heading = var2 > 0.05 ? Math.toDegrees(Math.atan2(-var1.x, var1.z)) : (double)var0.getYRot();
      vy = var1.y;
      turnRate = 0.0;
      bank = 0.0;
      if (kit == GliderClient.Kit.CANOPY) {
         if (handoff != null) {
            heading = handoff[0];
            var2 = handoff[1];
            vy = handoff[2];
         }

         airspeed = Mth.clamp(var2, 0.2, 5.0);
      } else {
         airspeed = Mth.clamp(Math.max(var2, 0.8), 0.5, 1.6);
      }

      handoff = null;
   }

   static void end() {
      active = false;
      openTicks = 0;
      bank = 0.0;
      turnRate = 0.0;
      autopilot = null;
      forwardIn = 0.0F;
      sideIn = 0.0F;
      if (sound != null) {
         sound.fade();
         sound = null;
      }
   }

   private static void vario(LocalPlayer var0) {
      double var1 = climb * 20.0;
      if (var1 > 0.1) {
         if (--varioTimer <= 0) {
            varioTimer = Math.max(2, (int)(11.0 - var1 * 3.0));
            float var3 = (float)Mth.clamp(1.0 + var1 * 0.28, 1.0, 2.0);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI((SoundEvent)SoundEvents.NOTE_BLOCK_BIT.value(), var3, 0.16F));
         }
      } else {
         varioTimer = 0;
      }
   }

   static Component instruments(LocalPlayer var0) {
      Vec3 var1 = var0.getDeltaMovement();
      double var2 = climb * 20.0;
      double var4 = Math.hypot(var1.x, var1.z) * 20.0 * 3.6;
      double var6 = Paraglider.clearance(var0, 512.0);
      String var8 = var2 > 0.05 ? "↑" : (var2 < -0.05 ? "↓" : "→");
      return Component.literal(String.format("%s %.1f m/s   %.0f km/h   %s m", var8, Math.abs(var2), var4, var6 >= 512.0 ? "500+" : String.valueOf((int)var6)));
   }

   @SubscribeEvent
   public static void rollTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      float var2 = active && var1.options.getCameraType().isFirstPerson() ? (float)(bank * 0.3) : 0.0F;
      rollO = roll;
      roll = roll + (var2 - roll) * 0.25F;
   }

   @SubscribeEvent
   public static void camera(ComputeCameraAngles var0) {
      if (!LaunchCinematic.running()) {
         float var1 = Mth.lerp((float)var0.getPartialTick(), rollO, roll);
         if (Math.abs(var1) > 0.01F) {
            var0.setRoll(var0.getRoll() + var1);
         }

         if (active && kit == GliderClient.Kit.CANOPY && openTicks >= 6 && openTicks < 18) {
            double var2 = ((double)(openTicks - 6) + var0.getPartialTick()) / 12.0;
            double var4 = Math.sin(Math.min(1.0, var2) * Math.PI) * (1.0 - var2 * 0.5);
            var0.setPitch((float)((double)var0.getPitch() - var4 * 7.0));
            var0.setRoll((float)((double)var0.getRoll() + Math.sin(var2 * 19.0) * var4 * 2.5));
         }

         if (active && airspeed > 2.2 && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            double var6 = (double)(Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime()) + var0.getPartialTick();
            double var7 = Math.min(0.6, (airspeed - 2.2) * 0.3);
            var0.setPitch((float)((double)var0.getPitch() + Math.sin(var6 * 2.3) * var7 * 0.6));
            var0.setRoll((float)((double)var0.getRoll() + Math.sin(var6 * 3.1 + 1.0) * var7));
         }
      }
   }

   @SubscribeEvent
   public static void fov(ComputeFovModifierEvent var0) {
      if (active) {
         float var1 = kit == GliderClient.Kit.CANOPY
            ? (float)Mth.clamp(1.0 + (airspeed - 0.55) * 0.12, 0.97, 1.25)
            : (float)Mth.clamp(1.05 + (airspeed - 1.6) * 0.1, 0.95, 1.32);
         var0.setNewFovModifier(var0.getNewFovModifier() * var1);
      }
   }

   private static RandomSource SoundInstance_random() {
      return SoundInstance.createUnseededRandom();
   }

   static enum Kit {
      WING(1.6, 2.6, 0.9, 1.9, 4.2, 14, 1.8),
      CANOPY(0.55, 0.8, 0.28, 0.35, 1.3, 26, 1.0);

      final double trim;
      final double bar;
      final double brakes;
      final double dive;
      final double max;
      final double lift;
      final int fill;

      private Kit(double nullxx, double nullxxx, double nullxxxx, double nullxxxxx, double nullxxxxxx, int nullxxxxxxx, double nullxxxxxxxx) {
         this.trim = nullxx;
         this.bar = nullxxx;
         this.brakes = nullxxxx;
         this.dive = nullxxxxx;
         this.max = nullxxxxxx;
         this.fill = nullxxxxxxx;
         this.lift = nullxxxxxxxx;
      }

      double sink(double var1) {
         return this == WING ? 0.06 + 0.035 * var1 * var1 : 0.07 + 0.25 * var1 * var1;
      }
   }

   static final class WindSound extends AbstractTickableSoundInstance {
      private final LocalPlayer player;
      private boolean fading;
      private float gain = 0.0F;

      WindSound(LocalPlayer var1) {
         super((SoundEvent)RideContent.SND_GLIDER_WIND.get(), SoundSource.PLAYERS, GliderClient.SoundInstance_random());
         this.player = var1;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.01F;
         this.relative = true;
      }

      void fade() {
         this.fading = true;
      }

      public void tick() {
         if (this.player.isRemoved()) {
            this.stop();
         } else {
            float var1 = this.fading ? 0.0F : (float)Mth.clamp(0.25 + (GliderClient.airspeed - 0.8) * 0.3, 0.2, 1.0);
            this.gain = this.gain + (var1 - this.gain) * 0.1F;
            this.volume = Math.max(0.001F, this.gain);
            this.pitch = (float)Mth.clamp(0.75 + GliderClient.airspeed * 0.16, 0.8, 1.45);
            if (this.fading && this.gain < 0.01F) {
               this.stop();
            }
         }
      }

      public boolean canStartSilent() {
         return true;
      }
   }
}
