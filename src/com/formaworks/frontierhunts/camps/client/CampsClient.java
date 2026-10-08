package com.formaworks.frontierhunts.camps.client;

import com.formaworks.frontierhunts.camps.CampsContent;
import com.formaworks.frontierhunts.camps.CampsNet;
import com.formaworks.frontierhunts.camps.Fmt;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client wiring for camps: renderers, payload receivers, the ledger key and the weekend-event HUD card. */
public final class CampsClient {
   /** Opens the camp ledger. Unbound by default (players pick a key in Controls) to avoid clashing with map mods. */
   static final KeyMapping LEDGER = new KeyMapping("key.frontierhunts.camp_ledger", InputConstants.UNKNOWN.getValue(), "key.categories.frontierhunts");

   private CampsClient() {
   }

   @EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD, value = Dist.CLIENT)
   public static final class ModEvents {
      @SubscribeEvent
      public static void setup(FMLClientSetupEvent e) {
         CampsNet.viewReceiver = CampScreen::receive;
         CampsNet.liveReceiver = LiveCache::accept;
      }

      @SubscribeEvent
      public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
         e.registerBlockEntityRenderer(CampsContent.CAMP_POST_BE.get(), CampPostRenderer::new);
         e.registerBlockEntityRenderer(CampsContent.TROPHY_BOARD_BE.get(), TrophyBoardRenderer::new);
      }

      @SubscribeEvent
      public static void keys(RegisterKeyMappingsEvent e) {
         e.register(LEDGER);
      }

      @SubscribeEvent
      public static void layers(RegisterGuiLayersEvent e) {
         e.registerAboveAll(CampsContent.id("camps_event"), CampsClient::hud);
      }
   }

   @EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
   public static final class GameEvents {
      @SubscribeEvent
      public static void clientTick(ClientTickEvent.Post e) {
         Minecraft mc = Minecraft.getInstance();
         while (LEDGER.consumeClick()) {
            if (mc.player != null && mc.screen == null && mc.getConnection() != null) {
               CampScreen.send(CampsNet.OPEN, "camp");
            }
         }
      }

      @SubscribeEvent
      public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
         LiveCache.clear();
         CampScreen.clearState();
      }
   }

   /**
    * Small top-left card while a weekend event runs. Shown while the player list key is held, and for a few seconds
    * after the lead changes; hidden with F1, under the debug screen and while any screen is open.
    */
   static void hud(GuiGraphics g, DeltaTracker delta) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || mc.screen != null) {
         return;
      }
      long now = System.currentTimeMillis();
      boolean tab = mc.options.keyPlayerList.isDown();
      boolean flash = now < LiveCache.flashUntil;
      if (!tab && !flash) {
         return;
      }
      Font font = mc.font;
      if (!LiveCache.eventActive) {
         if (tab && !LiveCache.eventTitle.isEmpty() && LiveCache.nextAt > now) {
            String s = "Next event · " + LiveCache.eventTitle + " in " + Fmt.duration(LiveCache.nextAt - now);
            int w = font.width(s) + 14;
            g.fill(6, 6, 6 + w, 22, 0xB0141B16);
            g.fill(6, 6, 8, 22, 0xFFAC8951);
            g.drawString(font, s, 13, 10, 0xFFE8E0CB, false);
         }
         return;
      }
      String me = mc.player.getGameProfile().getName();
      int rows = Math.min(3, LiveCache.EVENT_TOP.size());
      String head = LiveCache.eventTitle.toUpperCase();
      String left = Fmt.duration(LiveCache.eventEndsAt - now) + " left";
      String mine = "";
      for (int i = 0; i < LiveCache.EVENT_TOP.size(); i++) {
         if (LiveCache.EVENT_TOP.get(i)[0].equals(me)) {
            mine = "You · " + Fmt.ordinal(i + 1) + " · " + LiveCache.EVENT_TOP.get(i)[1];
         }
      }
      int w = Math.max(font.width(head) + font.width(left) + 24, 150);
      for (String[] r : LiveCache.EVENT_TOP.subList(0, rows)) {
         w = Math.max(w, font.width(r[0]) + font.width(r[1]) + 40);
      }
      w = Math.max(w, font.width(mine) + 16);
      int h = 26 + rows * 11 + (mine.isEmpty() ? 0 : 12) + (rows == 0 ? 11 : 0);
      int x = 6;
      int y = 6;
      g.fill(x, y, x + w, y + h, 0xC0121A15);
      g.fill(x, y, x + 2, y + h, 0xFFAC8951);
      g.fill(x + 2, y + 18, x + w, y + 19, 0x60AC8951);
      g.drawString(font, head, x + 8, y + 5, 0xFFE6C579, false);
      g.drawString(font, left, x + w - 6 - font.width(left), y + 5, 0xFFB4C9B8, false);
      int ry = y + 23;
      if (rows == 0) {
         g.drawString(font, LiveCache.eventTagline + " · no entries yet", x + 8, ry, 0xFFB9B196, false);
         ry += 11;
      }
      for (int i = 0; i < rows; i++) {
         String[] r = LiveCache.EVENT_TOP.get(i);
         int col = r[0].equals(me) ? 0xFFE6C579 : 0xFFE8E0CB;
         g.drawString(font, (i + 1) + ".", x + 8, ry, 0xFFAC8951, false);
         g.drawString(font, r[0], x + 22, ry, col, false);
         g.drawString(font, r[1], x + w - 6 - font.width(r[1]), ry, col, false);
         ry += 11;
      }
      if (!mine.isEmpty()) {
         g.drawString(font, mine, x + 8, ry + 1, 0xFFB4C9B8, false);
      }
   }
}
