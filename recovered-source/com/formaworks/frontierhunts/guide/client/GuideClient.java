package com.formaworks.frontierhunts.guide.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.client.ExpeditionScreen;
import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.client.JournalScreen;
import com.formaworks.frontierhunts.client.KillCamClient;
import com.formaworks.frontierhunts.guide.GuideConfig;
import com.formaworks.frontierhunts.guide.GuideNetwork;
import com.formaworks.frontierhunts.guide.Lesson;
import com.formaworks.frontierhunts.guide.Tip;
import com.formaworks.frontierhunts.onboard.Handbook;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * [guide] Client glue for the Field School: receives the server state, shows toasts, opens the welcome card once, the
 * guide key (H), and the "Field School" entry drawn into the Hunter's Journal and Expedition Guide sidebars.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class GuideClient {
   public static final KeyMapping KEY = new KeyMapping(
      "key.frontierhunts.field_school", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.frontierhunts"
   );
   static GuideNetwork.State state;

   /** [journal] Field School lesson mask for the journal's checklist (-1 = not synced yet), and its state flags. */
   public static int fieldSchoolDone() {
      return state == null ? -1 : state.done();
   }

   public static int fieldSchoolFlags() {
      return state == null ? 0 : state.flags();
   }
   private static int inWorld;
   private static boolean welcomeShown;
   private static int entryX, entryY, entryW, entryH;
   private static boolean entryVisible;

   private GuideClient() {
   }

   @EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = Bus.MOD)
   public static final class Setup {
      @SubscribeEvent
      public static void setup(FMLClientSetupEvent e) {
         GuideNetwork.stateReceiver = GuideClient::onState;
         GuideNetwork.noticeReceiver = GuideClient::onNotice;
      }

      @SubscribeEvent
      public static void keys(RegisterKeyMappingsEvent e) {
         e.register(KEY);
      }

      @SubscribeEvent
      public static void layers(RegisterGuiLayersEvent e) {
         e.registerBelow(VanillaGuiLayers.DEBUG_OVERLAY, FrontierHunts.id("field_school_card"), (g, dt) -> ObjectiveCard.render(g));
      }
   }

   // ------------------------------------------------------------------------------------------ state + notices

   static boolean enabled() {
      return state != null && state.has(GuideNetwork.F_ENABLED);
   }

   static boolean courseRunning() {
      return enabled() && !state.has(GuideNetwork.F_SKIPPED);
   }

   static void send(byte action, int arg) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.getConnection() != null && mc.getConnection().hasChannel(GuideNetwork.Action.TYPE)) {
         PacketDistributor.sendToServer(new GuideNetwork.Action(action, (byte)arg), new CustomPacketPayload[0]);
      }
   }

   private static void onState(GuideNetwork.State s) {
      GuideNetwork.State before = state;
      state = s;
      if (before != null && before.has(GuideNetwork.F_WELCOMED) && !s.has(GuideNetwork.F_WELCOMED)) {
         welcomeShown = false; // /frontierhunts tutorial reset: show the welcome card again
      }
      ObjectiveCard.changed(before, s);
      if (Minecraft.getInstance().screen instanceof FieldSchoolScreen screen) {
         screen.stateChanged();
      }
   }

   private static void onNotice(GuideNetwork.Notice n) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return;
      }
      GuideToast toast = null;
      switch (n.kind()) {
         case GuideNetwork.N_LESSON -> {
            Lesson l = Lesson.byId(n.id());
            if (l == null) {
               return;
            }
            boolean skipped = n.arg() != 0;
            int done = state == null ? 0 : state.done() | l.bit();
            Lesson next = Lesson.current(done);
            String body = next == null || Lesson.graduated(done) && next.optional()
               ? ""
               : GuideUi.tr("guide.frontierhunts.toast.next", GuideUi.tr(next.lang("title")));
            // [onboard] "Next" is the Handbook's one next step (a lesson, or e.g. "Cook game meat" after the last lesson)
            int hb = HandbookClient.mask();
            if (hb >= 0) {
               Handbook.Task self = Handbook.forLesson(l);
               Handbook.Task t = Handbook.next(hb | (self == null ? 0 : self.bit()));
               body = t == null ? "" : GuideUi.tr("guide.frontierhunts.toast.next", GuideUi.tr(t.lang("title")));
            }
            toast = new GuideToast(GuideToast.Kind.LESSON, l.key, // [fieldbook] icon = lesson key
               GuideUi.tr(skipped ? "guide.frontierhunts.toast.lesson_skipped" : "guide.frontierhunts.toast.lesson_done", l.ordinal() + 1),
               GuideUi.tr(l.lang("title")), body);
            if (!skipped) {
               mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.5F, 0.35F));
            }
            ObjectiveCard.pulse();
         }
         case GuideNetwork.N_HINT -> {
            String key = switch (n.id()) {
               case GuideNetwork.H_WINDED -> "winded";
               case GuideNetwork.H_DROPPED -> "dropped";
               case GuideNetwork.H_RAN -> "ran";
               case GuideNetwork.H_DRESSED -> "dressed";
               case GuideNetwork.H_BLOOD -> "blood";
               case GuideNetwork.H_SPOTTED -> "spotted";
               default -> null;
            };
            if (key == null) {
               return;
            }
            toast = new GuideToast(GuideToast.Kind.HINT, GuideIcons.forHint(key), GuideUi.tr("guide.frontierhunts.toast.hint"), // [fieldbook] icon
               GuideUi.tr("guide.frontierhunts.hint." + key + ".title"), GuideUi.tr("guide.frontierhunts.hint." + key + ".body"));
            ObjectiveCard.pulse();
         }
         case GuideNetwork.N_TIP -> {
            Tip t = Tip.byId(n.id());
            if (t == null || !GuideConfig.clientTips()) {
               return;
            }
            String body = t == Tip.SEASON
               ? GuideUi.tr(t.lang("body"), GuideUi.tr("guide.frontierhunts.season." + Math.clamp(n.arg(), 0, 3)))
               : GuideUi.tr(t.lang(t == Tip.PREDATOR && n.arg() == 1 ? "body_kill" : "body"));
            toast = new GuideToast(GuideToast.Kind.NOTE, GuideIcons.forTip(t.key), // [fieldbook]
                GuideUi.tr("guide.frontierhunts.toast.note"), GuideUi.tr(t.lang("title")), body);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.8F));
         }
         case GuideNetwork.N_GRADUATED -> {
            toast = new GuideToast(GuideToast.Kind.GRADUATED, "journal", // [fieldbook]
                GuideUi.tr("guide.frontierhunts.toast.graduated_eyebrow"),
               GuideUi.tr("guide.frontierhunts.toast.graduated"), GuideUi.tr("guide.frontierhunts.toast.graduated_body", keyName()));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.25F, 0.3F));
         }
         default -> {
         }
      }
      if (toast != null) {
         mc.getToasts().addToast(toast);
      }
   }

   static String keyName() {
      return KEY.getTranslatedKeyMessage().getString();
   }

   // ------------------------------------------------------------------------------------------ ticking

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post e) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) {
         inWorld = 0;
         return;
      }
      inWorld++;
      while (KEY.consumeClick()) {
         if (mc.screen == null) {
            HandbookClient.open(-1); // [onboard] H opens the Frontier Handbook (the Field School lessons are its steps 4-6)
         }
      }
      ObjectiveCard.tick(mc);
      if (!welcomeShown && enabled() && !state.has(GuideNetwork.F_WELCOMED) && mc.screen == null && mc.getOverlay() == null
         && inWorld > 120 && !KillCamClient.active() && !com.formaworks.frontierhunts.client.HuntCinematics.active() && mc.player.isAlive()) {
         welcomeShown = true;
         mc.setScreen(new WelcomeScreen());
      }
   }

   @SubscribeEvent
   public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e) {
      state = null;
      welcomeShown = false;
      inWorld = 0;
      ObjectiveCard.reset();
   }

   // ------------------------------------------------------------------------------------------ journal / guide entry

   /** Where the "Field School" entry sits in the Hunter's Journal or Expedition Guide sidebar (null-safe layout copy). */
   private static boolean layoutEntry(Screen s) {
      int w = s.width, h = s.height;
      if (s instanceof JournalScreen) {
         return false; // [journal] the overhauled journal shows Field School in its own sidebar and checklist
      }
      if (s instanceof ExpeditionScreen) {
         return false; // [ledger] the rebuilt Expedition journal draws its own Field School button in the sidebar
      }
      if (s instanceof ExpeditionScreen && false) {
         int pw = Math.min(594, w - 20), ph = Math.min(390, h - 20);
         int left = (w - pw) / 2, top = (h - ph) / 2;
         int side = pw < 440 ? 94 : 120;
         int row = Math.min(27, (ph - 86) / 7);
         entryX = left + 7;
         entryY = top + 58 + 7 * row;
         entryW = side - 14;
         entryH = row - 3;
         if (entryY + entryH > top + ph - 30) {
            entryW = 76;
            entryH = 20;
            entryX = left + pw - 29 - 4 - entryW;
            entryY = top + 10;
         }
         return true;
      }
      return false;
   }

   @SubscribeEvent
   public static void screenRender(ScreenEvent.Render.Post e) {
      entryVisible = layoutEntry(e.getScreen());
      if (!entryVisible || entryH < 10) {
         entryVisible = false;
         return;
      }
      GuiGraphics g = e.getGuiGraphics();
      boolean hover = e.getMouseX() >= entryX && e.getMouseX() < entryX + entryW && e.getMouseY() >= entryY && e.getMouseY() < entryY + entryH;
      boolean journal = e.getScreen() instanceof JournalScreen;
      int bg = hover ? (journal ? 0xFF516C56 : 0xFF4F6B53) : (journal ? 0xFF2D4237 : 0xFF2B3F35);
      g.pose().pushPose();
      g.pose().translate(0, 0, 200);
      g.fill(entryX, entryY, entryX + entryW, entryY + entryH, bg);
      g.fill(entryX, entryY, entryX + 2, entryY + entryH, 0xFFB99859);
      String label = GuideUi.tr("guide.frontierhunts.entry");
      String count = state == null ? "" : Lesson.requiredDone(state.done()) + "/7";
      int countW = count.isEmpty() ? 0 : GuideUi.font().width(count) + 6;
      int ty = entryY + (entryH - 8) / 2;
      g.drawString(GuideUi.font(), GuideUi.font().plainSubstrByWidth(label, entryW - 8 - countW), entryX + 5, ty, 0xFFEFE8D7, false);
      if (!count.isEmpty()) {
         g.drawString(GuideUi.font(), count, entryX + entryW - countW + 1, ty, 0xFFB99859, false);
      }
      g.pose().popPose();
   }

   @SubscribeEvent
   public static void screenClick(ScreenEvent.MouseButtonPressed.Pre e) {
      if (e.getButton() != 0 || !layoutEntry(e.getScreen()) || entryH < 10) {
         return;
      }
      double x = e.getMouseX(), y = e.getMouseY();
      if (x >= entryX && x < entryX + entryW && y >= entryY && y < entryY + entryH) {
         Minecraft mc = Minecraft.getInstance();
         mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
         mc.setScreen(new FieldSchoolScreen(e.getScreen(), -1));
         e.setCanceled(true);
      }
   }

   static FrontierUi.Size small() {
      return FrontierUi.Size.SMALL;
   }
}
