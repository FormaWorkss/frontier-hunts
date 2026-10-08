package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.Set;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.client.event.InputEvent.Key;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class HuntCinematics {
   private static Marker camera;
   private static Entity previous;
   private static CameraType previousView;
   private static int entity = -1;
   private static int age;
   private static int wait;
   private static int lastEntity = -1;
   private static int lastStart = -500;
   private static boolean intro;
   private static float health;
   private static Vec3 playerStart = Vec3.ZERO;

   public static void start(int var0, boolean var1) {
      Minecraft var2 = Minecraft.getInstance();
      if (var2.player != null && (Boolean)HuntConfig.CINEMATICS.get() && !(Boolean)HuntConfig.REDUCED_MOTION.get()) {
         if (lastEntity != var0 || !var1 || var2.player.tickCount - lastStart >= 120) {
            stop();
            entity = var0;
            intro = var1;
            wait = 30;
            age = 0;
            health = var2.player.getHealth();
            playerStart = var2.player.position();
            lastEntity = var0;
            lastStart = var2.player.tickCount;
         }
      }
   }

   public static boolean active() {
      return camera != null;
   }

   public static void stop() {
      Minecraft var0 = Minecraft.getInstance();
      if (camera != null && var0.getCameraEntity() == camera) {
         var0.setCameraEntity((Entity)(previous != null && previous.level() == var0.level ? previous : var0.player));
      }

      if (previousView != null) {
         var0.options.setCameraType(previousView);
      }

      camera = null;
      previous = null;
      previousView = null;
      entity = -1;
      wait = 0;
      age = 0;
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (entity >= 0) {
         if (var1.level != null
            && var1.player != null
            && var1.screen == null
            && var1.player.isAlive()
            && !(var1.player.getHealth() < health)
            && !(var1.player.position().distanceToSqr(playerStart) > 0.3)
            && !var1.options.keyUp.isDown()
            && !var1.options.keyDown.isDown()
            && !var1.options.keyLeft.isDown()
            && !var1.options.keyRight.isDown()
            && !var1.options.keyJump.isDown()) {
            if (var1.level.getEntity(entity) instanceof LivingEntity var3) {
               if (camera == null) {
                  if (var3.distanceToSqr(var1.player) > 4096.0) {
                     stop();
                     return;
                  }

                  previous = var1.getCameraEntity();
                  previousView = var1.options.getCameraType();
                  var1.options.setCameraType(CameraType.FIRST_PERSON);
                  camera = new Marker(EntityType.MARKER, var1.level);
                  pose(var3, 0);
                  if (camera == null) {
                     return;
                  }

                  var1.setCameraEntity(camera);
               }

               if (++age > (intro ? 62 : 75)) {
                  stop();
               } else {
                  pose(var3, age);
               }
            } else {
               if (--wait <= 0) {
                  stop();
               }
            }
         } else {
            stop();
         }
      }
   }

   private static void pose(LivingEntity var0, int var1) {
      Minecraft var2 = Minecraft.getInstance();
      double var3 = Math.clamp((double)var1 / (intro ? 62.0 : 75.0), 0.0, 1.0);
      double var5 = var3 * var3 * (3.0 - 2.0 * var3);
      Vec3 var7 = var0.position().add(0.0, (double)var0.getBbHeight() * (downed(var0) ? 0.3 : 0.65), 0.0);
      Vec3 var8 = Vec3.directionFromRotation(0.0F, var0.yBodyRot);
      Vec3 var9 = new Vec3(var8.z, 0.0, -var8.x);
      double var10 = intro ? 0.62 + var5 * 0.35 : 1.12 + var5 * 0.22;
      double var12 = (intro ? 5.3 - var5 * 1.2 : 3.8 + var5 * 0.5) * Math.max(1.0, (double)var0.getBbHeight() / 1.6);
      Vec3 var14 = var7.add(var8.scale(Math.cos(var10) * var12)).add(var9.scale(Math.sin(var10) * var12)).add(0.0, intro ? 0.5 + 0.25 * var5 : 1.1, 0.0);
      BlockHitResult var15 = var2.level.clip(new ClipContext(var7, var14, Block.COLLIDER, Fluid.NONE, var0));
      Vec3 var16 = var15.getType() == Type.MISS
         ? var14
         : var15.getLocation()
            .add((double)var15.getDirection().getStepX() * 0.2, (double)var15.getDirection().getStepY() * 0.2, (double)var15.getDirection().getStepZ() * 0.2);
      if (var16.distanceToSqr(var7) < 1.0) {
         stop();
      } else {
         camera.xo = camera.getX();
         camera.yo = camera.getY();
         camera.zo = camera.getZ();
         camera.setPos(var16);
         Vec3 var17 = var7.subtract(var16);
         camera.yRotO = camera.getYRot();
         camera.xRotO = camera.getXRot();
         camera.setYRot((float)Math.toDegrees(Math.atan2(-var17.x, var17.z)));
         camera.setXRot((float)(-Math.toDegrees(Math.atan2(var17.y, var17.horizontalDistance()))));
         if (var1 == 0) {
            camera.xo = var16.x;
            camera.yo = var16.y;
            camera.zo = var16.z;
            camera.yRotO = camera.getYRot();
            camera.xRotO = camera.getXRot();
         }
      }
   }

   private static boolean downed(LivingEntity var0) {
      return var0 instanceof Whitetail var1 ? var1.downed() : var0.isDeadOrDying();
   }

   @SubscribeEvent
   public static void hideHud(Pre var0) {
      if (active()) {
         ResourceLocation var1 = var0.getName();
         if (var1.getNamespace().equals("minecraft")
               && Set.of(
                     "boss_overlay",
                     "selected_item_name",
                     "overlay_message",
                     "crosshair",
                     "hotbar",
                     "experience_level",
                     "experience_bar",
                     "player_health",
                     "food_level",
                     "armor_level",
                     "air_level"
                  )
                  .contains(var1.getPath())
            || var1.getNamespace().equals("frontierhunts") && var1.getPath().equals("wilderness")) {
            var0.setCanceled(true);
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void hideHands(RenderHandEvent var0) {
      if (active()) {
         var0.setCanceled(true);
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void hideLocalPlayer(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre var0) {
      if (active() && var0.getEntity() == Minecraft.getInstance().player) {
         var0.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void input(InteractionKeyMappingTriggered var0) {
      if (active()) {
         stop();
         var0.setCanceled(true);
         var0.setSwingHand(false);
      }
   }

   @SubscribeEvent
   public static void key(Key var0) {
      if (active() && var0.getKey() == 256 && var0.getAction() == 1) {
         stop();
      }
   }

   @SubscribeEvent
   public static void disconnect(LoggingOut var0) {
      stop();
      lastEntity = -1;
      lastStart = -500;
   }

   private HuntCinematics() {
   }
}
