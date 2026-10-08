package com.formaworks.frontierhunts.landscape.ride.client;

import com.formaworks.frontierhunts.landscape.ride.Paraglider;
import com.formaworks.frontierhunts.landscape.ride.RideContent;
import com.formaworks.frontierhunts.landscape.ride.RidePayloads;
import com.formaworks.frontierhunts.landscape.ride.Wingsuit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.Clone;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class WingsuitClient {
   static final double G = 0.045;
   static final double K = 1.77E-4;
   static final double K3 = 0.0067;
   static final double GLIDE = -1.9;
   static final double MAX = 4.6;
   static final double STALL = 1.2;
   static boolean active;
   static double speed;
   static double heading;
   static double path;
   static double turnRate;
   static double bank;
   static double vy;
   static int ticks;
   static float forwardIn;
   static float sideIn;
   private static boolean jumpHeld;
   private static int impactCooldown;
   private static Vec3 before;
   private static Vec3 lastVel = Vec3.ZERO;
   private static WingsuitClient.Flutter flutter;
   private static float roll;
   private static float rollO;
   private static LocalPlayer pilot;
   static double timeToGround = -1.0;
   static double agl;
   static Double autopilot;
   private static double leftAtY = Double.NaN;
   private static int airTicks;
   private static boolean restowed;

   private WingsuitClient() {
   }

   private static void watchFall(LocalPlayer var0) {
      if (!var0.onGround() && !var0.isInWater() && !var0.isPassenger() && !var0.getAbilities().flying && !var0.isFallFlying() && var0.isAlive()) {
         if (Double.isNaN(leftAtY)) {
            leftAtY = var0.getY();
            airTicks = 0;
         }

         airTicks++;
         if (Wingsuit.wearing(var0) && !Wingsuit.canopyOpen(var0) && !Paraglider.gliding(var0) && !LaunchCinematic.running()) {
            Vec3 var1 = var0.getDeltaMovement();
            if (!(leftAtY - var0.getY() < 3.0) && !(var1.y > -0.35) && airTicks >= 6) {
               if (openAir(var0, var1, 20.0)) {
                  start(var0);
               }
            }
         }
      } else {
         leftAtY = var0.onGround() ? var0.getY() : Double.NaN;
         airTicks = 0;
      }
   }

   static boolean openAir(LocalPlayer var0, Vec3 var1, double var2) {
      if (Paraglider.clearance(var0, var2 + 1.0) >= var2) {
         return true;
      } else {
         double var4 = Math.hypot(var1.x, var1.z);
         Vec3 var6 = var4 > 0.05 ? new Vec3(var1.x / var4, 0.0, var1.z / var4) : Vec3.directionFromRotation(0.0F, var0.getYRot());

         for (double var10 : new double[]{4.0, 8.0, 14.0}) {
            double var12 = LaunchCinematic.groundBelow(
               var0.level(), var0.getX() + var6.x * var10, var0.getZ() + var6.z * var10, var0.getY() + 0.5, (int)var2 + 40
            );
            if (var12 <= var0.getY() - var2 - 4.0) {
               return true;
            }
         }

         return false;
      }
   }

   static boolean start(LocalPlayer var0) {
      if (!Wingsuit.wearing(var0)
         || Wingsuit.canopyOpen(var0)
         || var0.isFallFlying()
         || var0.onGround()
         || var0.isInWater()
         || var0.isPassenger()
         || var0.getAbilities().flying) {
         return false;
      } else if (!var0.tryToStartFallFlying()) {
         return false;
      } else {
         var0.connection.send(new ServerboundPlayerCommandPacket(var0, Action.START_FALL_FLYING));
         return true;
      }
   }

   static void stow(LocalPlayer var0) {
      if (Wingsuit.canopyOpen(var0) && !var0.onGround() && !var0.isInWater()) {
         if (send(new RidePayloads.Suit((byte)3, 0.0F))) {
            Wingsuit.setCanopy(Wingsuit.worn(var0), false);
            GliderClient.end();
            restowed = start(var0);
         }
      }
   }

   static void deploy(LocalPlayer var0) {
      Wingsuit.setCanopy(Wingsuit.worn(var0), true);
      send(new RidePayloads.Suit((byte)0, 0.0F));
      var0.stopFallFlying();
      GliderClient.handoff(heading, speed * Math.cos(Math.toRadians(path)), vy);
      end();
   }

   @SubscribeEvent
   public static void input(MovementInputUpdateEvent var0) {
      if (var0.getEntity() instanceof LocalPlayer var1) {
         Input var4 = var0.getInput();
         boolean var3 = var4.jumping;
         if (active && Wingsuit.flying(var1)) {
            forwardIn = var4.forwardImpulse;
            sideIn = var4.leftImpulse;
            if (var3 && !jumpHeld && ticks > 12 && !LaunchCinematic.running()) {
               deploy(var1);
            }

            var4.forwardImpulse = 0.0F;
            var4.leftImpulse = 0.0F;
            var4.up = var4.down = var4.left = var4.right = false;
            var4.jumping = false;
            var4.shiftKeyDown = false;
         }

         jumpHeld = var3;
      }
   }

   @SubscribeEvent
   public static void before(Pre var0) {
      if (var0.getEntity() instanceof LocalPlayer var1 && var1 == Minecraft.getInstance().player) {
         before = var1.position();
      }
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      if (var0.getEntity() instanceof LocalPlayer var1 && var1 == Minecraft.getInstance().player) {
         if (impactCooldown > 0) {
            impactCooldown--;
         }

         if (active && var1 != pilot) {
            end();
         }

         watchFall(var1);
         boolean var40 = var1.isAlive()
            && Wingsuit.flying(var1)
            && !var1.onGround()
            && !var1.isInWater()
            && !var1.isPassenger()
            && !var1.getAbilities().flying
            && !Paraglider.gliding(var1);
         if (!var40) {
            if (active) {
               if ((var1.onGround() || var1.isInWater()) && var1.isAlive() && ticks > 3 && !Wingsuit.canopyOpen(var1)) {
                  impact(lastVel.length(), var1.isInWater());
               }

               end();
            }

            return;
         }

         if (!active) {
            begin(var1);
         }

         ticks++;
         float var3 = autopilot != null ? 9.0F : var1.getXRot();
         double var4 = forwardIn > 0.1F ? 1.0 : 0.0;
         double var6 = forwardIn < -0.1F ? 1.0 : 0.0;
         double var8 = -1.9 - 1.6 * var4 + 0.5 * var6;
         double var10 = Mth.clamp(8.5 / Math.max(speed, 0.5), 1.6, 4.6);
         double var12;
         if (autopilot != null) {
            var12 = Mth.clamp(Mth.wrapDegrees(autopilot - heading) * 0.15, -2.0, 2.0);
         } else if (Math.abs(sideIn) > 0.1F) {
            var12 = (double)(-Math.signum(sideIn)) * var10 * 1.3;
         } else {
            var12 = Mth.clamp(Mth.wrapDegrees((double)var1.getYRot() - heading) * 0.22, -var10, var10);
         }

         turnRate = turnRate + (var12 - turnRate) * 0.25;
         heading = Mth.wrapDegrees(heading + turnRate);
         double var14 = turnRate * (float) (Math.PI / 180.0) * 20.0;
         double var16 = speed * 20.0 * Math.cos(Math.toRadians(path));
         bank = Mth.clamp(Math.toDegrees(Math.atan(var14 * var16 / 9.81)), -75.0, 75.0);
         double var18 = var3 >= 0.0F ? var8 - (double)Math.max(0.0F, var3 - 8.0F) * 1.15 : var8 + (double)(-var3) * 1.3;
         var18 -= (1.0 / Math.max(0.3, Math.cos(Math.toRadians(bank))) - 1.0) * 7.0;
         double var20 = var8 + (speed - 2.4) * 24.0;
         if (speed < 1.2) {
            var20 = Math.min(var20, -55.0);
         }

         var18 = Mth.clamp(Math.min(var18, var20), -88.0, 40.0);
         double var22 = Mth.clamp(var18 - path, -5.0, 5.0) * (ticks < 10 ? 0.5 : 1.0);
         path += var22 * 0.6;
         double var24 = 1.77E-4 * (1.0 + 0.5 * var6 - 0.15 * var4) * speed * speed + 0.0067 * Math.pow(Math.max(0.0, speed - 3.0), 3.0);
         speed = speed + (-0.045 * Math.sin(Math.toRadians(path)) - var24 - Math.abs(var22) * 0.0012);
         speed = Mth.clamp(speed, 0.3, 4.6);
         double var26 = Math.cos(Math.toRadians(path));
         double var28 = Math.toRadians(heading);
         double var30 = -Math.sin(var28) * speed * var26;
         double var32 = Math.cos(var28) * speed * var26;
         vy = speed * Math.sin(Math.toRadians(path));
         Vec3 var34 = new Vec3(var30, vy, var32);
         Vec3 var35 = before == null ? Vec3.ZERO : var1.position().subtract(before);
         Vec3 var36 = var34.subtract(var35);
         if (var36.lengthSqr() > 1.0E-8 && !var1.horizontalCollision) {
            var1.move(MoverType.SELF, var36);
         }

         var1.setDeltaMovement(var34);
         var1.resetFallDistance();
         if (var1.horizontalCollision && before != null && impactCooldown == 0) {
            Vec3 var37 = var1.position().subtract(before);
            double var38 = Math.hypot(var30, var32) - Math.hypot(var37.x, var37.z);
            if (var38 > 0.5) {
               impact(var38, false);
               speed = Math.max(0.3, Math.hypot(var37.x, var37.z));
               path = -60.0;
            }
         }

         lastVel = var34;
         warnings(var1, var34);
         if (flutter == null || flutter.isStopped()) {
            flutter = new WingsuitClient.Flutter(var1);
            Minecraft.getInstance().getSoundManager().play(flutter);
         }

         return;
      }
   }

   private static void impact(double var0, boolean var2) {
      if (!(var0 < 0.45) && impactCooldown <= 0 && Double.isFinite(var0)) {
         impactCooldown = 10;
         send(new RidePayloads.Suit((byte)(var2 ? 2 : 1), (float)var0));
      }
   }

   private static boolean send(RidePayloads.Suit var0) {
      ClientPacketListener var1 = Minecraft.getInstance().getConnection();

      try {
         if (var1 != null && var1.hasChannel(RidePayloads.Suit.TYPE)) {
            PacketDistributor.sendToServer(var0, new CustomPacketPayload[0]);
            return true;
         }
      } catch (RuntimeException var3) {
      }

      return false;
   }

   @SubscribeEvent
   public static void loggingOut(LoggingOut var0) {
      end();
      pilot = null;
   }

   @SubscribeEvent
   public static void respawn(Clone var0) {
      end();
      pilot = null;
   }

   private static void begin(LocalPlayer var0) {
      active = true;
      ticks = 0;
      turnRate = 0.0;
      bank = 0.0;
      pilot = var0;
      impactCooldown = 0;
      Vec3 var1 = var0.getDeltaMovement();
      double var2 = Math.hypot(var1.x, var1.z);
      heading = var2 > 0.08 ? Math.toDegrees(Math.atan2(-var1.x, var1.z)) : (double)var0.getYRot();
      speed = Mth.clamp(Math.max(var1.length(), 1.3), 0.3, 4.6);
      path = Mth.clamp(Math.toDegrees(Math.atan2(var1.y, Math.max(var2, 0.05))), -70.0, -20.0);
      vy = var1.y;
      if (restowed) {
         restowed = false;
      } else {
         LaunchCinematic.suitOpened(var0, heading);
      }
   }

   static void end() {
      active = false;
      ticks = 0;
      autopilot = null;
      forwardIn = 0.0F;
      sideIn = 0.0F;
      bank = 0.0;
      turnRate = 0.0;
      timeToGround = -1.0;
      if (flutter != null) {
         flutter.fade();
         flutter = null;
      }
   }

   private static void warnings(LocalPlayer var0, Vec3 var1) {
      agl = Paraglider.clearance(var0, 512.0);
      if (var0.tickCount % 2 == 0) {
         timeToGround = -1.0;
         Vec3 var2 = var0.position();
         Vec3 var3 = var2.add(var1.scale(160.0));
         BlockHitResult var4 = var0.level().clip(new ClipContext(var2, var3, Block.COLLIDER, Fluid.ANY, var0));
         if (var4.getType() != Type.MISS) {
            timeToGround = var4.getLocation().distanceTo(var2) / Math.max(0.05, var1.length()) / 20.0;
         }
      }
   }

   static Component instruments(LocalPlayer var0) {
      double var1 = Math.hypot(lastVel.x, lastVel.z) * 20.0 * 3.6;
      double var3 = -lastVel.y * 20.0;
      return Component.literal(String.format("%.0f km/h   ↓ %.0f m/s   %s m", var1, var3, agl >= 512.0 ? "500+" : String.valueOf((int)agl)));
   }

   static int pullWarning() {
      if (active && ticks >= 12) {
         boolean var0 = agl < 12.0 || timeToGround >= 0.0 && timeToGround < 3.0;
         if (var0) {
            return 2;
         } else {
            return !(agl < 35.0) && (!(timeToGround >= 0.0) || !(timeToGround < 6.0)) ? 0 : 1;
         }
      } else {
         return 0;
      }
   }

   @SubscribeEvent
   public static void rollTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      float var2 = active && var1.options.getCameraType().isFirstPerson() ? (float)(bank * 0.35) : 0.0F;
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

         if (active && speed > 2.0) {
            Minecraft var2 = Minecraft.getInstance();
            double var3 = (double)(var2.level == null ? 0L : var2.level.getGameTime()) + var0.getPartialTick();
            double var5 = Math.min(0.9, (speed - 2.0) * 0.32) * (var2.options.getCameraType().isFirstPerson() ? 1.0 : 0.4);
            var0.setPitch((float)((double)var0.getPitch() + (Math.sin(var3 * 2.7) + Math.sin(var3 * 5.3 + 1.0) * 0.5) * var5 * 0.45));
            var0.setYaw((float)((double)var0.getYaw() + Math.sin(var3 * 3.9 + 2.0) * var5 * 0.3));
            var0.setRoll((float)((double)var0.getRoll() + Math.sin(var3 * 3.3 + 1.0) * var5 * 0.8));
         }
      }
   }

   @SubscribeEvent
   public static void fov(ComputeFovModifierEvent var0) {
      if (active) {
         var0.setNewFovModifier((float)Mth.clamp(1.0 + (speed - 1.6) * 0.075, 1.0, 1.28));
      }
   }

   static final class Flutter extends AbstractTickableSoundInstance {
      private final LocalPlayer player;
      private boolean fading;
      private float gain;

      Flutter(LocalPlayer var1) {
         super((SoundEvent)RideContent.SND_SUIT_FLUTTER.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
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
            float var1 = this.fading ? 0.0F : (float)Mth.clamp((WingsuitClient.speed - 0.8) * 0.3, 0.08, 0.9);
            this.gain = this.gain + (var1 - this.gain) * (this.fading ? 0.25F : 0.1F);
            this.volume = Math.max(0.001F, this.gain);
            this.pitch = (float)Mth.clamp(0.7 + WingsuitClient.speed * 0.15, 0.75, 1.5);
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
