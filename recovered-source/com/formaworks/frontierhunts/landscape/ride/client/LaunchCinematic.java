package com.formaworks.frontierhunts.landscape.ride.client;

import com.formaworks.frontierhunts.landscape.ride.Paraglider;
import com.formaworks.frontierhunts.landscape.ride.Wingsuit;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.client.event.RenderFrameEvent.Pre;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class LaunchCinematic {
   private static boolean running;
   private static Vec3 camPos;
   private static float camYaw;
   private static float camPitch;
   private static CameraType oldType;
   private static int openTries;
   private static boolean wasGround = true;
   private static int pendingOpen = -1;
   private static long lastShot = -100000L;
   private static LaunchCinematic.Site site;
   private static InteractionHand hand;
   private static float runYaw;
   private static int t;
   private static int leapAt = -1;
   private static int runTicks;
   private static boolean opened;
   private static Vec3 brinkCam;
   private static Vec3 revealFrom;
   private static float bars;
   private static float barsO;
   private static double fovBase = -1.0;
   private static float lastHealth;
   static final int LEAP_HOLD = 10;
   static final int REVEAL_END = 46;
   static final int RETURN_END = 62;
   private static MethodHandle SET_POSITION;
   private static boolean positionBroken;

   private LaunchCinematic() {
   }

   public static boolean running() {
      return running;
   }

   public static void tryLaunch(Player var0, InteractionHand var1) {
      Minecraft var2 = Minecraft.getInstance();
      if (!running && var0 == var2.player && var2.level != null) {
         LocalPlayer var3 = var2.player;
         LaunchCinematic.Site var4 = find(var3);
         if (var4 == null) {
            var3.displayClientMessage(Component.translatable("message.frontierhunts.glider_no_edge"), true);
         } else {
            start(var3, var4, var1);
         }
      }
   }

   static double groundBelow(Level var0, double var1, double var3, double var5, int var7) {
      MutableBlockPos var8 = new MutableBlockPos();
      int var9 = Mth.floor(var1);
      int var10 = Mth.floor(var3);
      int var11 = Mth.floor(var5);

      for (int var12 = var11; var12 >= var11 - var7; var12--) {
         var8.set(var9, var12, var10);
         BlockState var13 = var0.getBlockState(var8);
         if (!var13.getFluidState().isEmpty()) {
            return (double)var12 + 0.9;
         }

         VoxelShape var14 = var13.getCollisionShape(var0, var8);
         if (!var14.isEmpty()) {
            return (double)var12 + var14.max(Axis.Y);
         }
      }

      return Double.NEGATIVE_INFINITY;
   }

   static boolean open(Level var0, Vec3 var1) {
      BlockPos var2 = BlockPos.containing(var1);
      return var0.getBlockState(var2).getCollisionShape(var0, var2).isEmpty();
   }

   static LaunchCinematic.Site find(LocalPlayer var0) {
      Level var1 = var0.level();
      float var2 = var0.getYRot() * (float) (Math.PI / 180.0);
      Vec3 var3 = new Vec3((double)(-Mth.sin(var2)), 0.0, (double)Mth.cos(var2));
      double var4 = var0.getY();
      double var6 = var4;

      for (double var8 = 0.5; var8 <= 14.0; var8 += 0.5) {
         double var10 = var0.getX() + var3.x * var8;
         double var12 = var0.getZ() + var3.z * var8;
         double var14 = groundBelow(var1, var10, var12, var6 + 1.2, 48);
         if (var14 > var6 + 0.6) {
            return null;
         }

         if (!open(var1, new Vec3(var10, var14 + 0.6, var12)) || !open(var1, new Vec3(var10, var14 + 1.6, var12))) {
            return null;
         }

         if (var14 < var6 - 2.4) {
            double var16 = groundBelow(var1, var10 + var3.x * 4.0, var12 + var3.z * 4.0, var14 + 2.0, 60);
            double var18 = groundBelow(var1, var10 + var3.x * 9.0, var12 + var3.z * 9.0, var14 + 2.0, 70);
            boolean var20 = var16 <= var6 - 5.0 && var18 <= var6 - 9.0;
            if (!var20) {
               return null;
            }

            for (double var21 = 1.0; var21 <= 10.0; var21++) {
               for (double var23 = 0.5; var23 <= 3.0; var23++) {
                  if (!open(var1, new Vec3(var10 + var3.x * var21, var6 + var23, var12 + var3.z * var21))) {
                     return null;
                  }
               }
            }

            double var25 = Math.max(0.0, var8 - 0.5);
            return new LaunchCinematic.Site(
               new Vec3(var0.getX() + var3.x * var25, var6, var0.getZ() + var3.z * var25), var3, var25, var6 - Math.max(var18, var6 - 80.0)
            );
         }

         var6 = var14;
      }

      return null;
   }

   private static void start(LocalPlayer var0, LaunchCinematic.Site var1, InteractionHand var2) {
      Minecraft var3 = Minecraft.getInstance();
      site = var1;
      hand = var2;
      running = true;
      t = 0;
      leapAt = -1;
      opened = false;
      brinkCam = null;
      revealFrom = null;
      openTries = 0;
      runYaw = (float)Math.toDegrees(Math.atan2(-var1.dir().x, var1.dir().z));
      runTicks = Mth.clamp((int)Math.ceil(var1.distance() / 0.28) + 2, 4, 60);
      oldType = var3.options.getCameraType();
      var3.options.setCameraType(CameraType.THIRD_PERSON_BACK);
      place(shotFor(var0, 0.0F), var0.getEyePosition());
      lastHealth = var0.getHealth();
      lastShot = var3.level.getGameTime();
   }

   static void finish() {
      Minecraft var0 = Minecraft.getInstance();
      if (running) {
         running = false;
         if (oldType != null) {
            var0.options.setCameraType(oldType);
         }

         oldType = null;
         camPos = null;
         GliderClient.autopilot = null;
         WingsuitClient.autopilot = null;
         bars = 0.0F;
         barsO = 0.0F;
      }
   }

   static void skip() {
      Minecraft var0 = Minecraft.getInstance();
      LocalPlayer var1 = var0.player;
      finish();
      if (var1 != null && !var1.onGround()) {
         if (!Paraglider.gliding(var1) && GliderClient.canOpen(var1)) {
            GliderClient.open(var1);
         } else if (Paraglider.held(var1).isEmpty() && !var1.isFallFlying()) {
            WingsuitClient.start(var1);
         }
      }
   }

   private static boolean airborneKit(LocalPlayer var0) {
      return Paraglider.gliding(var0) || Wingsuit.flying(var0) || Wingsuit.canopyOpen(var0);
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      barsO = bars;
      if (!running) {
         watchTakeoff();
      } else {
         Minecraft var1 = Minecraft.getInstance();
         LocalPlayer var2 = var1.player;
         if (var2 == null || var1.level == null || var2.isDeadOrDying() || var2.getHealth() < lastHealth - 0.5F || var1.getCameraEntity() != var2) {
            finish();
         } else if (var1.options.keyShift.isDown() && t > 6) {
            skip();
         } else if (!Wingsuit.flying(var2) || WingsuitClient.pullWarning() != 2 && (!var1.options.keyJump.isDown() || t <= 6)) {
            lastHealth = var2.getHealth();
            t++;
            bars = bars + ((isReturning() ? 0.0F : 1.0F) - bars) * 0.18F;
            Vec3 var3 = var2.position().subtract(site.edge());
            double var4 = var3.x * site.dir().x + var3.z * site.dir().z;
            if (leapAt < 0) {
               if (var2.onGround() && (var4 > -0.35 || t >= runTicks + 10)) {
                  leapAt = t;
               }

               if (t > runTicks + 30) {
                  skip();
                  return;
               }
            } else {
               if (opened && !var2.onGround() && !airborneKit(var2)) {
                  skip();
                  return;
               }

               if (!opened && t >= leapAt + 5 && !var2.onGround()) {
                  if (Paraglider.gliding(var2)) {
                     opened = true;
                     GliderClient.autopilot = (double)runYaw;
                  } else if (Wingsuit.flying(var2)) {
                     opened = true;
                     WingsuitClient.autopilot = (double)runYaw;
                  } else if (!Paraglider.held(var2).isEmpty()) {
                     if (GliderClient.canOpen(var2) && openTries++ % 8 == 0) {
                        GliderClient.open(var2);
                     }
                  } else if (Wingsuit.wearing(var2) && openTries++ % 4 == 0) {
                     WingsuitClient.start(var2);
                  }
               }

               if (var2.onGround() && t > leapAt + 12 && !airborneKit(var2)) {
                  finish();
                  return;
               }

               if (t >= leapAt + 62) {
                  finish();
               }
            }
         } else {
            finish();
         }
      }
   }

   private static void watchTakeoff() {
      Minecraft var0 = Minecraft.getInstance();
      LocalPlayer var1 = var0.player;
      if (var1 != null && var0.level != null && var0.screen == null) {
         boolean var2 = var1.onGround();
         boolean var3 = !Paraglider.held(var1).isEmpty();
         boolean var4 = false;
         if (pendingOpen >= 0) {
            if (!var2 && !airborneKit(var1) && (var3 || var4)) {
               if (--pendingOpen <= 0) {
                  if (var3) {
                     if (GliderClient.canOpen(var1)) {
                        GliderClient.open(var1);
                     }
                  } else if (WingsuitClient.start(var1)) {
                     pendingOpen = -1;
                  }

                  if (var1.getDeltaMovement().y < -0.12 || pendingOpen < -10) {
                     pendingOpen = -1;
                  }
               }
            } else {
               pendingOpen = -1;
            }
         }

         boolean var5 = wasGround && !var2;
         wasGround = var2;
         if (var5 && (var3 || var4) && !airborneKit(var1) && !var1.isInWater() && !var1.isPassenger() && !var1.getAbilities().flying && !var1.isFallFlying()) {
            Vec3 var6 = var1.getDeltaMovement();
            double var7 = Math.hypot(var6.x, var6.z);
            Vec3 var9;
            if (var7 > 0.05) {
               var9 = new Vec3(var6.x / var7, 0.0, var6.z / var7);
            } else {
               float var10 = var1.getYRot() * (float) (Math.PI / 180.0);
               var9 = new Vec3((double)(-Mth.sin(var10)), 0.0, (double)Mth.cos(var10));
            }

            double var19 = var1.getY();
            double var12 = Math.min(6.0, Math.max(1.0, var7 * 14.0));
            boolean var14 = false;

            for (double var15 = 0.5; var15 <= var12; var15 += 0.5) {
               double var17 = groundBelow(var1.level(), var1.getX() + var9.x * var15, var1.getZ() + var9.z * var15, var19 + 0.5, 72);
               if (var17 <= var19 - (double)(var4 ? 16 : 8)) {
                  var14 = true;
                  break;
               }
            }

            if (var14) {
               if (open(var1.level(), var1.position().add(var9.scale(2.5)).add(0.0, 1.0, 0.0))) {
                  pendingOpen = 6;
                  long var20 = var0.level.getGameTime();
                  boolean var21 = !var4
                     || groundBelow(var1.level(), var1.getX() + var9.x * 12.0, var1.getZ() + var9.z * 12.0, var19 + 0.5, 160) <= var19 - 45.0
                        && groundBelow(var1.level(), var1.getX() + var9.x * 30.0, var1.getZ() + var9.z * 30.0, var19 + 0.5, 200) <= var19 - 70.0;
                  if (var20 - lastShot > 600L && var21) {
                     startLeap(var1, var9);
                  }
               }
            }
         }
      } else {
         wasGround = true;
         pendingOpen = -1;
      }
   }

   static void suitOpened(LocalPlayer var0, double var1) {
      Minecraft var3 = Minecraft.getInstance();
      if (!running && var3.level != null && var0 == var3.player && var3.screen == null) {
         if (var3.level.getGameTime() - lastShot >= 600L) {
            float var4 = (float)var1 * (float) (Math.PI / 180.0);
            Vec3 var5 = new Vec3((double)(-Mth.sin(var4)), 0.0, (double)Mth.cos(var4));
            double var6 = var0.getY();
            boolean var8 = Paraglider.clearance(var0, 40.0) >= 30.0
               || groundBelow(var0.level(), var0.getX() + var5.x * 20.0, var0.getZ() + var5.z * 20.0, var6 + 0.5, 120) <= var6 - 35.0
                  && groundBelow(var0.level(), var0.getX() + var5.x * 40.0, var0.getZ() + var5.z * 40.0, var6 + 0.5, 140) <= var6 - 40.0;
            if (var8) {
               site = new LaunchCinematic.Site(var0.position(), var5, 0.0, 0.0);
               hand = InteractionHand.MAIN_HAND;
               running = true;
               t = 0;
               leapAt = 0;
               opened = true;
               revealFrom = null;
               openTries = 0;
               runYaw = (float)Math.toDegrees(Math.atan2(-var5.x, var5.z));
               runTicks = 0;
               WingsuitClient.autopilot = (double)runYaw;
               oldType = var3.options.getCameraType();
               var3.options.setCameraType(CameraType.THIRD_PERSON_BACK);
               Vec3 var9 = var0.position().add(0.0, 0.6, 0.0);
               brinkCam = clear(var0.level(), var9, var9.add(var5.scale(7.5)).add(right().scale(3.2)).add(0.0, -1.4, 0.0));
               place(brinkCam, var9);
               lastHealth = var0.getHealth();
               lastShot = var3.level.getGameTime();
            }
         }
      }
   }

   private static void startLeap(LocalPlayer var0, Vec3 var1) {
      Minecraft var2 = Minecraft.getInstance();
      site = new LaunchCinematic.Site(var0.position(), var1, 0.0, 0.0);
      hand = Paraglider.held(var0).isEmpty() ? InteractionHand.MAIN_HAND : Paraglider.heldHand(var0);
      running = true;
      t = 0;
      leapAt = 0;
      opened = false;
      revealFrom = null;
      openTries = 0;
      runYaw = (float)Math.toDegrees(Math.atan2(-var1.x, var1.z));
      runTicks = 0;
      oldType = var2.options.getCameraType();
      var2.options.setCameraType(CameraType.THIRD_PERSON_BACK);
      Vec3 var3 = var0.position().add(0.0, 1.0, 0.0);
      brinkCam = clear(var0.level(), var3, var3.add(var1.scale(3.4)).add(right().scale(2.8)).add(0.0, -0.4, 0.0));
      place(brinkCam, var3);
      lastHealth = var0.getHealth();
      lastShot = var2.level.getGameTime();
   }

   static boolean isReturning() {
      return leapAt >= 0 && t >= leapAt + 46 + 6;
   }

   @SubscribeEvent(
      priority = EventPriority.LOW
   )
   public static void input(MovementInputUpdateEvent var0) {
      if (running && var0.getEntity() instanceof LocalPlayer var1) {
         Input var3 = var0.getInput();
         if (leapAt < 0 || var1.onGround() && t <= leapAt + 1) {
            var3.forwardImpulse = 1.0F;
            var3.up = true;
            var3.down = false;
            var3.left = var3.right = false;
            var3.leftImpulse = 0.0F;
            var3.jumping = leapAt >= 0 && t <= leapAt + 1;
            var3.shiftKeyDown = false;
            var1.setSprinting(true);
            var1.setYRot(runYaw);
            var1.yRotO = runYaw;
            var1.setYHeadRot(runYaw);
            var1.setYBodyRot(runYaw);
            var1.setXRot(Mth.lerp(0.2F, var1.getXRot(), 12.0F));
         } else {
            var3.shiftKeyDown = false;
            var3.jumping = false;
            if (!opened) {
               var3.forwardImpulse = 0.6F;
               var3.up = true;
            }
         }
      }
   }

   @SubscribeEvent
   public static void turn(CalculatePlayerTurnEvent var0) {
      if (running) {
         var0.setMouseSensitivity(-0.3333333333333333);
      }
   }

   private static void place(Vec3 var0, Vec3 var1) {
      Vec3 var2 = var1.subtract(var0);
      camYaw = (float)Math.toDegrees(Math.atan2(-var2.x, var2.z));
      camPitch = (float)(-Math.toDegrees(Math.atan2(var2.y, Math.hypot(var2.x, var2.z))));
      camPos = var0;
   }

   private static Vec3 right() {
      return new Vec3(-site.dir().z, 0.0, site.dir().x).scale(-1.0);
   }

   private static double smooth(double var0) {
      var0 = Mth.clamp(var0, 0.0, 1.0);
      return var0 * var0 * var0 * (var0 * (var0 * 6.0 - 15.0) + 10.0);
   }

   private static Vec3 handheld(double var0, double var2) {
      return new Vec3(
            Math.sin(var0 * 0.37) * 0.6 + Math.sin(var0 * 0.91) * 0.3,
            Math.sin(var0 * 0.53 + 1.0) * 0.5 + Math.sin(var0 * 1.3) * 0.2,
            Math.sin(var0 * 0.29 + 2.0) * 0.4
         )
         .scale(var2);
   }

   private static Vec3 clear(Level var0, Vec3 var1, Vec3 var2) {
      BlockHitResult var3 = var0.clip(new ClipContext(var1, var2, Block.VISUAL, Fluid.NONE, CollisionContext.empty()));
      if (var3.getType() == Type.MISS) {
         return var2;
      } else {
         Vec3 var4 = var2.subtract(var1);
         double var5 = var4.length();
         return var1.add(var4.scale(Math.max(0.3, (var3.getLocation().distanceTo(var1) - 0.4) / Math.max(0.001, var5))));
      }
   }

   private static Vec3 shotFor(LocalPlayer var0, float var1) {
      Vec3 var2 = var0.getPosition(var1);
      double var3 = Mth.clamp((double)((float)t + var1) / Math.max(1.0, (double)runTicks), 0.0, 1.0);
      Vec3 var5 = right();
      return var2.add(var5.scale(3.3)).add(site.dir().scale(2.4 - 1.6 * smooth(var3))).add(0.0, 0.55, 0.0);
   }

   @SubscribeEvent
   public static void frame(Pre var0) {
      if (running) {
         Minecraft var1 = Minecraft.getInstance();
         LocalPlayer var2 = var1.player;
         if (var2 != null) {
            float var3 = var0.getPartialTick().getGameTimeDeltaPartialTick(true);
            double var4 = (double)((float)t + var3);
            Vec3 var6 = var2.getPosition(var3).add(0.0, 1.05, 0.0);
            Vec3 var7 = site.dir();
            Vec3 var8;
            Vec3 var9;
            if (leapAt < 0) {
               var8 = clear(var2.level(), var6, shotFor(var2, var3).add(handheld(var4, 0.05)));
               var9 = var6.add(var7.scale(0.9)).add(0.0, -0.1, 0.0);
            } else {
               double var10 = var4 - (double)leapAt;
               if (brinkCam == null) {
                  brinkCam = camPos != null ? camPos : var6;
               }

               if (var10 < 10.0) {
                  var8 = brinkCam.add(handheld(var4, 0.035));
                  var9 = var6.add(0.0, 0.1, 0.0);
               } else {
                  Vec3 var12 = var6.subtract(var7.scale(9.5)).add(0.0, 4.6, 0.0).add(right().scale(1.8));
                  var12 = clear(var2.level(), var6, var12);
                  Vec3 var13 = var6.add(var7.scale(28.0)).add(0.0, -5.0, 0.0);
                  if (var10 < 46.0) {
                     double var14 = smooth((var10 - 10.0) / 18.0);
                     var8 = brinkCam.lerp(var12, var14).add(handheld(var4, 0.03));
                     var9 = var6.add(0.0, 0.4, 0.0).lerp(var13, smooth((var10 - 10.0 - 4.0) / 22.0));
                     revealFrom = var8;
                  } else {
                     double var22 = smooth((var10 - 46.0) / 16.0);
                     float var16 = var2.getViewYRot(var3) * (float) (Math.PI / 180.0);
                     float var17 = var2.getViewXRot(var3) * (float) (Math.PI / 180.0);
                     Vec3 var18 = new Vec3((double)(-Mth.sin(var16) * Mth.cos(var17)), (double)(-Mth.sin(var17)), (double)(Mth.cos(var16) * Mth.cos(var17)));
                     Vec3 var19 = var2.getEyePosition(var3).subtract(var18.scale(0.45));
                     var8 = var12.lerp(var19, var22);
                     var9 = var13.lerp(var19.add(var18.scale(20.0)), var22);
                  }
               }
            }

            place(var8, var9);
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void angles(ComputeCameraAngles var0) {
      if (running && camPos != null) {
         double var1 = (double)t + var0.getPartialTick();
         double var3 = 0.0;
         if (leapAt >= 0) {
            double var5 = var1 - (double)leapAt;
            var3 = var5 < 46.0 ? 4.0 * smooth((var5 - 10.0) / 16.0) : 4.0 * (1.0 - smooth((var5 - 46.0) / 12.0));
         }

         var0.setYaw(camYaw);
         var0.setPitch(camPitch);
         var0.setRoll((float)(var3 + Math.sin(var1 * 0.41) * 0.35));
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void fov(ComputeFov var0) {
      if (running && var0.usedConfiguredFov() && camPos != null) {
         try {
            if (SET_POSITION == null) {
               Method var1 = Camera.class.getDeclaredMethod("setPosition", Vec3.class);
               var1.setAccessible(true);
               SET_POSITION = MethodHandles.lookup().unreflect(var1);
            }

            SET_POSITION.invoke((Camera)var0.getCamera(), (Vec3)camPos);
         } catch (Throwable var9) {
            if (!positionBroken) {
               positionBroken = true;
               RideContentLog.warn("Launch cinematic: cannot move the camera; skipping the shot", var9);
            }

            finish();
            return;
         }

         double var10 = var0.getFOV();
         double var3 = (double)t + var0.getPartialTick();
         double var5;
         if (leapAt < 0) {
            var5 = var10 * 0.78;
         } else {
            double var7 = var3 - (double)leapAt;
            if (var7 < 10.0) {
               var5 = var10 * Mth.lerp(var7 / 10.0, 0.78, 0.72);
            } else if (var7 < 46.0) {
               var5 = var10 * Mth.lerp(smooth((var7 - 10.0) / 20.0), 0.72, 1.12);
            } else {
               var5 = var10 * Mth.lerp(smooth((var7 - 46.0) / 16.0), 1.12, 1.0);
            }
         }

         var0.setFOV(var5);
      }
   }

   @SubscribeEvent
   public static void hud(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre var0) {
      if (running) {
         var0.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void hand(RenderHandEvent var0) {
      if (running) {
         var0.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void letterbox(net.neoforged.neoforge.client.event.RenderGuiEvent.Post var0) {
      float var1 = Mth.lerp(var0.getPartialTick().getGameTimeDeltaPartialTick(true), barsO, bars);
      if (!(var1 < 0.005F)) {
         GuiGraphics var2 = var0.getGuiGraphics();
         int var3 = var2.guiWidth();
         int var4 = var2.guiHeight();
         int var5 = (int)Math.ceil((double)((float)var4 * 0.115F * var1));
         var2.fill(0, 0, var3, var5, -16777216);
         var2.fill(0, var4 - var5, var3, var4, -16777216);
         if (running && t > 6 && var5 > 12) {
            Minecraft var6 = Minecraft.getInstance();
            MutableComponent var7 = Component.translatable(
               "message.frontierhunts.cinematic_skip", new Object[]{var6.options.keyShift.getTranslatedKeyMessage()}
            );
            var2.drawString(var6.font, var7, var3 - var6.font.width(var7) - 8, var4 - var5 / 2 - 4, (int)(var1 * 144.0F) << 24 | 13158600, false);
         }
      }
   }

   @SubscribeEvent
   public static void clicks(InteractionKeyMappingTriggered var0) {
      if (running) {
         var0.setCanceled(true);
         var0.setSwingHand(false);
      }
   }

   @SubscribeEvent
   public static void loggingOut(LoggingOut var0) {
      finish();
   }

   static record Site(Vec3 edge, Vec3 dir, double distance, double drop) {
   }
}
