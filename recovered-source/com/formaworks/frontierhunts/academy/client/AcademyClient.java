package com.formaworks.frontierhunts.academy.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.academy.Academy;
import com.formaworks.frontierhunts.academy.AcademyNetwork;
import com.formaworks.frontierhunts.client.AssignmentScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * [academy] Client glue: academy state for the dossier, the live training card, cinematic cues, the assignments key
 * and the hold-to-leave key.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class AcademyClient {
   public static final KeyMapping ASSIGNMENTS = new KeyMapping(
      "key.frontierhunts.assignments", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.frontierhunts"
   );
   public static final KeyMapping LEAVE = new KeyMapping(
      "key.frontierhunts.leave_training", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSPACE, "key.categories.frontierhunts"
   );
   static final int LEAVE_HOLD = 30;

   static AcademyNetwork.State state;
   static AcademyNetwork.Hud hud;
   static long hudAtMs;
   static int leaveHold;
   private static boolean leaveSent;

   private AcademyClient() {
   }

   @EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = Bus.MOD)
   public static final class Setup {
      @SubscribeEvent
      public static void setup(FMLClientSetupEvent e) {
         AcademyNetwork.stateReceiver = AcademyClient::onState;
         AcademyNetwork.hudReceiver = AcademyClient::onHud;
         AcademyNetwork.cueReceiver = TrainingHud::cue;
      }

      @SubscribeEvent
      public static void keys(RegisterKeyMappingsEvent e) {
         e.register(ASSIGNMENTS);
         e.register(LEAVE);
      }

      @SubscribeEvent
      public static void layers(RegisterGuiLayersEvent e) {
         e.registerBelow(VanillaGuiLayers.DEBUG_OVERLAY, FrontierHunts.id("academy_card"), (g, dt) -> TrainingHud.render(g));
         e.registerAboveAll(FrontierHunts.id("academy_cinematic"), (g, dt) -> TrainingHud.renderCinematic(g));
      }
   }

   /** [regions] True while an academy fade or the back-home results card is on screen (arrival cards wait). */
   public static boolean cinematicShowing() {
      return TrainingHud.showing();
   }

   /** True while this client is in the training grounds with a running course (other HUD cards step aside). */
   public static boolean training() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level != null && Academy.isTrainingLevel(mc.level) && hud != null && hud.course() >= 0;
   }

   public static AcademyNetwork.State state() {
      return state;
   }

   public static AcademyNetwork.Hud hud() {
      return hud;
   }

   /** Remaining ticks, counted down locally between server updates. */
   public static int remaining() {
      if (hud == null) {
         return 0;
      }
      if (hud.phase() != AcademyNetwork.P_ACTIVE) {
         return hud.remaining();
      }
      long gone = (System.currentTimeMillis() - hudAtMs) / 50L;
      return (int)Math.max(0L, hud.remaining() - gone);
   }

   public static void send(byte action, int course) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.getConnection() != null && mc.getConnection().hasChannel(AcademyNetwork.Action.TYPE)) {
         PacketDistributor.sendToServer(new AcademyNetwork.Action(action, (byte)course), new CustomPacketPayload[0]);
      }
   }

   private static void onState(AcademyNetwork.State s) {
      state = s;
      if (Minecraft.getInstance().screen instanceof AssignmentScreen screen) {
         screen.refresh();
      }
   }

   private static void onHud(AcademyNetwork.Hud h) {
      AcademyNetwork.Hud before = hud;
      hud = h.course() < 0 ? null : h;
      hudAtMs = System.currentTimeMillis();
      TrainingHud.hudChanged(before, hud);
      if (Minecraft.getInstance().screen instanceof AssignmentScreen screen) {
         screen.refresh();
      }
   }

   static void clearHud() {
      hud = null;
      leaveHold = 0;
      leaveSent = false;
   }

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post e) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         return;
      }
      while (ASSIGNMENTS.consumeClick()) {
         if (mc.screen == null) {
            AssignmentScreen.send(0, "", 0);
         }
      }
      if (hud != null && !Academy.isTrainingLevel(mc.level)) {
         clearHud();
      }
      boolean canLeave = training() && hud.phase() == AcademyNetwork.P_ACTIVE && mc.screen == null;
      if (canLeave && LEAVE.isDown()) {
         if (++leaveHold >= LEAVE_HOLD && !leaveSent) {
            leaveSent = true;
            send(AcademyNetwork.A_LEAVE, -1);
         }
      } else {
         leaveHold = 0;
         if (!LEAVE.isDown()) {
            leaveSent = false;
         }
      }
      TrainingHud.tick(mc);
   }

   @SubscribeEvent
   public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
      state = null;
      clearHud();
      TrainingHud.reset();
   }
}
