package com.formaworks.frontierhunts.expedition;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class LensViewClient {
   private static LensView active;
   private static LensView.CameraControl target;

   public static void control(LensView.CameraControl var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level != null && var1.player != null) {
         if (var1.screen instanceof LensMonitorScreen) {
            var1.setScreen(null);
         }

         if (active != null) {
            if (var1.getCameraEntity() == active) {
               var1.setCameraEntity(var1.player);
            }

            var1.level.removeEntity(active.getId(), RemovalReason.DISCARDED);
            active = null;
         }

         target = null;
         if (var0.open()) {
            LensView var2 = (LensView)((EntityType)ExpeditionContent.LENS_VIEW.get()).create(var1.level);
            if (var2 != null) {
               var2.setId(var0.id());
               var2.moveTo(var0.x(), var0.y(), var0.z(), var0.yaw(), var0.pitch());
               var1.level.addEntity(var2);
               active = var2;
               target = var0;
               var1.setCameraEntity(var2);
               var1.setScreen(new LensMonitorScreen());
            }
         }
      }
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (active != null && target != null && var1.level != null && var1.player != null) {
         active.moveTo(target.x(), target.y(), target.z(), target.yaw(), target.pitch());
         active.setOldPosAndRot();
      }
   }

   @SubscribeEvent
   public static void hand(RenderHandEvent var0) {
      if (active != null) {
         var0.setCanceled(true);
      }
   }

   private LensViewClient() {
   }
}
