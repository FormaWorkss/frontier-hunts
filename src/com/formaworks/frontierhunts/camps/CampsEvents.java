package com.formaworks.frontierhunts.camps;

import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus wiring for the camps workstream: ticking, login, commands, wildlife records. */
@EventBusSubscriber(modid = "frontierhunts")
public final class CampsEvents {
   private static long tick;

   private CampsEvents() {
   }

   @SubscribeEvent
   public static void starting(ServerStartingEvent e) {
      CampsConfig.load();
      tick = 0L;
      RecordService.markDirty();
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent e) {
      CampsViews.SESSIONS.clear();
   }

   @SubscribeEvent
   public static void serverTick(ServerTickEvent.Post e) {
      MinecraftServer server = e.getServer();
      long t = ++tick;
      step(() -> CampService.tick(server, t));
      step(() -> GuidedService.tick(server, t));
      step(() -> EventService.tick(server, t));
      step(() -> RecordService.tick(server, t));
   }

   private static void step(Runnable r) {
      try {
         r.run();
      } catch (Exception ex) {
         com.mojang.logging.LogUtils.getLogger().error("Frontier Hunts camps: tick error", ex);
      }
   }

   @SubscribeEvent
   public static void login(PlayerEvent.PlayerLoggedInEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         step(() -> CampService.login(p));
         step(() -> EventService.login(p));
         step(() -> RecordService.login(p));
      }
   }

   @SubscribeEvent
   public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
      CampsViews.forget(e.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void commands(RegisterCommandsEvent e) {
      CampService.commands(e.getDispatcher());
      GuidedService.commands(e.getDispatcher());
      RecordService.commands(e.getDispatcher());
      EventService.commands(e.getDispatcher());
   }

   @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
   public static void death(LivingDeathEvent e) {
      if (e.getEntity() instanceof WildlifeMob mob && !mob.level().isClientSide && e.getSource().getEntity() instanceof ServerPlayer killer) {
         CampHooks.wildlifeKilled(mob, killer);
      }
   }
}
