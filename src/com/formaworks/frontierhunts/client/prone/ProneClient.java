package com.formaworks.frontierhunts.client.prone;

import com.formaworks.frontierhunts.prone.Prone;
import com.formaworks.frontierhunts.prone.ProneNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * [rifle] Prone key (default Z, Frontier Hunts category): toggles lying flat. The server decides (see {@link Prone});
 * this side asks, mirrors the pose of every prone player it can see, keeps the local player from jumping or sprinting
 * while down, slows the crawl a touch and adds the camera's settle as the body drops / rises ({@link ProneCamera}).
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class ProneClient {
   public static final KeyMapping PRONE = new KeyMapping(
      "key.frontierhunts.prone", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_Z, "key.categories.frontierhunts");
   /** Crawl a little slower than vanilla's sneak-speed crawl: a rifleman on his elbows. */
   private static final float CRAWL = 0.8F;
   private static long sentAt = Long.MIN_VALUE;

   private ProneClient() {
   }

   /** Is the local player prone (server-confirmed)? */
   public static boolean localProne() {
      Minecraft mc = Minecraft.getInstance();
      return mc.player != null && Prone.isProne(mc.player);
   }

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      if (p == null || mc.level == null) {
         while (PRONE.consumeClick()) {
         }
         return;
      }
      Prone.clientTick(mc.level);
      boolean prone = Prone.isProne(p);
      while (PRONE.consumeClick()) {
         long now = mc.level.getGameTime();
         if (mc.screen == null && p.isAlive() && !p.isSpectator() && (now < sentAt || now - sentAt >= 4L)) {
            sentAt = now;
            PacketDistributor.sendToServer(new ProneNetwork.Request(!prone));
         }
      }
      if (prone && p.isSprinting()) {
         p.setSprinting(false);
      }
   }

   @SubscribeEvent
   public static void input(MovementInputUpdateEvent event) {
      if (event.getEntity() == Minecraft.getInstance().player && Prone.isProne(event.getEntity()) && !Prone.blocked(event.getEntity())) {
         event.getInput().jumping = false;
         event.getInput().forwardImpulse *= CRAWL;
         event.getInput().leftImpulse *= CRAWL;
      }
   }

   @SubscribeEvent
   public static void camera(ViewportEvent.ComputeCameraAngles event) {
      float nod = ProneCamera.nod((float)event.getPartialTick());
      if (nod != 0.0F) {
         event.setPitch(event.getPitch() + nod);
      }
   }

   @SubscribeEvent
   public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
      Prone.clientReset();
      ProneCamera.reset();
   }

   @SubscribeEvent
   public static void clone(ClientPlayerNetworkEvent.Clone event) {
      Prone.clientReset(); // respawn / dimension change: the server re-sends whoever is still prone
      ProneCamera.reset();
   }

   @EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = Bus.MOD)
   public static final class Keys {
      private Keys() {
      }

      @SubscribeEvent
      public static void keys(RegisterKeyMappingsEvent event) {
         event.register(PRONE);
         com.formaworks.frontierhunts.rifle.RidgelineOptics.PRONE_KEY = () -> PRONE.getTranslatedKeyMessage().getString();
      }
   }
}
