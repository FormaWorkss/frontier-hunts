package com.formaworks.frontierhunts.phone.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.guide.client.FirstHuntCompass;
import com.formaworks.frontierhunts.phone.FieldPhoneItem;
import com.formaworks.frontierhunts.phone.OldBaseStation;
import com.formaworks.frontierhunts.phone.PhoneNet;
import com.formaworks.frontierhunts.phone.client.ui.App;
import com.formaworks.frontierhunts.phone.client.ui.PhoneActions;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.PhoneUi;
import com.formaworks.frontierhunts.phone.client.ui.Ui;
import com.formaworks.frontierhunts.phone.client.ui.apps.Apps;
import com.formaworks.frontierhunts.phone.client.ui.apps.GameApp;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * [phone] The Field Phone on the client: the phone key (U), opening and closing the phone screen, its one
 * {@link PhoneUi} and model, the heartbeat that tells the server the screen is on (battery), background checks for news
 * (cameras, contracts) while the phone is in the pocket, the alarm, navigation on the compass ribbon, and the notices
 * that pop up on the HUD while the phone is put away.
 */
@EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT)
public final class PhoneClient {
   public static final KeyMapping KEY = new KeyMapping("key.frontierhunts.phone", InputConstants.Type.KEYSYM, 85, "key.categories.frontierhunts");
   private static PhoneUi ui;
   private static GuiCanvas canvas;
   private static final ClientActions ACTIONS = new ClientActions();
   private static PhoneScreen screen;
   private static int ticks;
   private static int stateIn;
   private static long alarmRungDay = -1L;
   private static int snoozeMinute = -1;
   private static int ringSince;
   private static boolean settingsLoaded;
   private static String navTarget = "";

   private PhoneClient() {
   }

   @EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
   public static final class Setup {
      @SubscribeEvent
      public static void keys(RegisterKeyMappingsEvent e) {
         e.register(KEY);
      }

      @SubscribeEvent
      public static void setup(FMLClientSetupEvent e) {
         FieldPhoneItem.opener = () -> PhoneClient.open(null, null);
         PhoneNet.receiver = PhoneFeed::receive;
         // [1.4.0] a retired Field Camera puts up the phone camera; the Camera app's gallery and shared photos
         com.formaworks.frontierhunts.expedition.FieldCamera.phoneCamera = PhoneCamera::openFromItem;
         PhoneGallery.install(PhoneFeed.MODEL);
         PhonePhotoCache.install(PhoneFeed.MODEL);
      }

      @SubscribeEvent
      public static void layers(RegisterGuiLayersEvent e) {
         e.registerAbove(VanillaGuiLayers.CHAT, FrontierHunts.id("phone_notices"), PhoneHud::render);
         e.registerAboveAll(PhoneCamera.LAYER, PhoneCamera::render);
      }
   }

   static ClientActions actions() {
      return ACTIONS;
   }

   static PhoneUi ui() {
      if (ui == null) {
         ui = Apps.create(PhoneFeed.MODEL, ACTIONS);
         canvas = new GuiCanvas();
      }
      return ui;
   }

   static PhoneScreen screen() {
      Minecraft mc = Minecraft.getInstance();
      return mc.screen instanceof PhoneScreen s ? s : null;
   }

   public static boolean isPhoneKey(int key, int scan) {
      return KEY.matches(key, scan);
   }

   // ------------------------------------------------------------------------------------------------ open and close

   /** Takes the phone out (on {@code appId}, or where it was); {@code station}: opened at a retired base station. */
   public static void open(String appId, BlockPos station) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) {
         return;
      }
      if (!settingsLoaded) {
         settingsLoaded = true;
         PhoneSettings.load(PhoneFeed.MODEL);
      }
      PhoneModel m = PhoneFeed.MODEL;
      PhoneFeed.device(mc, true);
      if (!m.hasPhone && station == null) {
         mc.player.displayClientMessage(Component.translatable("phone.frontierhunts.none").withStyle(ChatFormatting.GRAY), true);
         return;
      }
      m.station = station != null && (!m.hasPhone || m.battery <= 0);
      m.keyName = KEY.getTranslatedKeyMessage().getString();
      m.reducedMotion = reducedMotion();
      PhoneUi u = ui();
      PhoneScreen s = screen();
      if (s != null) {
         if (appId != null) {
            u.launch(appId);
         }
         return;
      }
      PhoneFeed.firstHunt();
      u.open(m.station ? "cams" : appId);
      screen = new PhoneScreen(u, canvas);
      mc.setScreen(screen);
      ticks = 0;
      sendState();
      // fresh news for the home screen's badges
      ACTIONS.refresh(PhoneActions.R_GAMES);
      ACTIONS.refresh(PhoneActions.R_CONTRACTS);
      ACTIONS.refresh(PhoneActions.R_MESSAGES);
      ACTIONS.refresh(PhoneActions.R_WEATHER);
   }

   /** The mod's reduced-motion setting: the phone opens, unlocks and switches apps without slides or zooms. */
   static boolean reducedMotion() {
      try {
         return com.formaworks.frontierhunts.HuntConfig.REDUCED_MOTION.get();
      } catch (RuntimeException e) {
         return false;
      }
   }

   /** The server asked to show the phone (a retired base station was used). */
   static void openFromServer(String app, BlockPos station) {
      open(app.isEmpty() ? null : app, station);
   }

   static void closePhone() {
      if (ui != null) {
         ui.close();
      }
   }

   /** The phone screen went away. */
   static void closedScreen() {
      screen = null;
      PhoneMap.wanted(false);
      ACTIONS.playCall("");
      sendState();
      PhoneFeed.MODEL.station = false;
   }

   /** Tells the server whether the screen is on and the torch shines (battery and light). */
   static void sendState() {
      int flags = (screen() != null || PhoneCamera.active() ? 1 : 0) | (PhoneFeed.MODEL.flashlight ? 2 : 0);
      ClientActions.send(PhoneNet.Ask.of(PhoneNet.OP_STATE, flags, 0L));
      stateIn = 100;
   }

   static void darkroom(boolean on) {
      PhoneScreen s = screen();
      if (s != null) {
         s.develop = on;
      }
   }

   static BlockPos galleryCamera() {
      long pos = PhoneFeed.galleryPos();
      return pos == 0L ? null : BlockPos.of(pos);
   }

   static void toast(String text) {
      if (PhoneCamera.active() && screen() == null) {
         PhoneCamera.say(text);
      } else if (screen() != null && ui != null) {
         ui.toast(text);
      } else if (!text.isEmpty()) {
         PhoneHud.show("", text, "");
      }
   }

   /** News arrived (from the server or found by the feed). */
   static void noticeArrived(String app, String title, String body) {
      PhoneModel m = PhoneFeed.MODEL;
      if (!m.settings.notifyGames && (app.equals("chess") || app.equals("dice") || app.equals("flush"))) {
         return;
      }
      if (screen() == null) {
         PhoneHud.show(app, title, body);
         if (!m.settings.silent) {
            ACTIONS.sound(app.equals("messages") ? PhoneActions.Sfx.MESSAGE_IN : PhoneActions.Sfx.NOTIFY);
         }
      } else if (app.equals("messages") && !m.settings.silent) {
         ACTIONS.sound(PhoneActions.Sfx.MESSAGE_IN);
      }
   }

   /** A new online game of ours started: open it if its game is on the phone's screen now. */
   static void onlineStarted(PhoneModel.Online o) {
      if (screen() == null || ui == null) {
         return;
      }
      App cur = ui.current();
      if (cur instanceof GameApp g && g.gameKind() == o.game) {
         g.onlineStarted(o.id);
      }
   }

   static void navigationChanged() {
      PhoneModel m = PhoneFeed.MODEL;
      navTarget = m.map.target;
      if (navTarget.isEmpty() || !m.settings.hudNav) {
         FirstHuntCompass.phoneTarget(null, 0.0, 0.0);
         return;
      }
      for (PhoneModel.Place p : m.map.places) {
         if (p.id().equals(navTarget)) {
            FirstHuntCompass.phoneTarget(p.name(), p.x() + 0.5, p.z() + 0.5);
            return;
         }
      }
      FirstHuntCompass.phoneTarget(null, 0.0, 0.0);
   }

   // ------------------------------------------------------------------------------------------------ alarm

   static void alarmSet() {
      snoozeMinute = -1;
      alarmRungDay = -1L;
   }

   static void alarmAnswer(boolean snooze) {
      PhoneModel m = PhoneFeed.MODEL;
      m.alarmRinging = false;
      snoozeMinute = snooze ? Math.floorMod(Ui.minute(m.dayTime) + 10, 1440) : -1;
   }

   private static void alarm(Minecraft mc) {
      PhoneModel m = PhoneFeed.MODEL;
      if (!m.hasPhone || m.battery <= 0 || mc.level == null) {
         m.alarmRinging = false;
         return;
      }
      int minute = Ui.minute(mc.level.getDayTime());
      long day = mc.level.getDayTime() / 24000L;
      boolean due = m.alarmMinute >= 0 && minute == m.alarmMinute && alarmRungDay != day;
      boolean snoozed = snoozeMinute >= 0 && minute == snoozeMinute;
      if ((due || snoozed) && !m.alarmRinging) {
         m.alarmRinging = true;
         alarmRungDay = day;
         ringSince = ticks;
         if (snoozed) {
            snoozeMinute = -1;
         }
         PhoneHud.show("clock", "Alarm", Ui.clockFull(m, minute));
      }
      if (m.alarmRinging) {
         if ((ticks - ringSince) % 30 == 0) {
            ACTIONS.sound(PhoneActions.Sfx.ALARM);
         }
         // a real minute of ringing, then it gives up
         if (ticks - ringSince > 20 * 60) {
            m.alarmRinging = false;
         }
      }
   }

   // ------------------------------------------------------------------------------------------------ ticks

   /** Every tick while the phone screen is up. */
   static void tickOpen(PhoneScreen s) {
      Minecraft mc = Minecraft.getInstance();
      PhoneFeed.device(mc, true);
      PhoneModel m = PhoneFeed.MODEL;
      if (ui != null && ui.current() != null && "cams".equals(ui.current().id)) {
         PhoneFeed.refreshCams();
      }
      PhoneMap.tick(mc);
      if (--stateIn <= 0) {
         sendState();
      }
      if (!navTarget.equals(m.map.target)) {
         navigationChanged();
      }
   }

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post e) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) {
         return;
      }
      ticks++;
      while (KEY.consumeClick()) {
         if (mc.screen == null && !PhoneCamera.active()) {
            open(null, null);
         }
      }
      alarm(mc);
      if (screen() != null) {
         return;
      }
      if (ticks % 20 == 0) {
         PhoneFeed.device(mc, true);
         PhoneModel m = PhoneFeed.MODEL;
         // news while the phone is in the pocket: cameras and contracts once a minute (only with a phone and a bar)
         if (m.hasPhone && m.battery > 0 && m.signal > 0 && ticks % 1200 == 0) {
            ClientActions.send(PhoneNet.Ask.of(PhoneNet.OP_REFRESH, PhoneNet.R_CAMS, 0L));
            ClientActions.send(PhoneNet.Ask.of(PhoneNet.OP_REFRESH, PhoneNet.R_CONTRACTS, 0L));
         }
         if (m.flashlight && !m.held) {
            // the torch only shines from a hand; it stays switched on in the pocket
            stateIn = 0;
         }
      }
   }

   @SubscribeEvent
   public static void login(ClientPlayerNetworkEvent.LoggingIn e) {
      PhoneFeed.reset();
      PhoneMap.reset();
      FirstHuntCompass.phoneTarget(null, 0.0, 0.0);
      navTarget = "";
      alarmRungDay = -1L;
      snoozeMinute = -1;
   }

   @SubscribeEvent
   public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
      PhoneCamera.close(false);
      PhoneGallery.release();
      PhonePhotoCache.release();
      SelfiePose.clear();
      PhoneFeed.reset();
      PhoneMap.reset();
      PhoneHud.clear();
      FirstHuntCompass.phoneTarget(null, 0.0, 0.0);
      navTarget = "";
      screen = null;
      if (canvas != null) {
         canvas.clearCaches();
      }
   }

   @SubscribeEvent
   public static void tooltip(ItemTooltipEvent e) {
      if (OldBaseStation.retired(e.getItemStack().getItem())) {
         e.getToolTip().add(Component.translatable("phone.frontierhunts.retired").withStyle(ChatFormatting.GOLD));
         e.getToolTip().add(Component.translatable("phone.frontierhunts.retired.how").withStyle(ChatFormatting.GRAY));
      }
   }
}
