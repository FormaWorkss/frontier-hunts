package com.formaworks.frontierhunts.guide.client;

import com.formaworks.frontierhunts.academy.Course;
import com.formaworks.frontierhunts.client.AssignmentScreen;
import com.formaworks.frontierhunts.client.KillCamClient;
import com.formaworks.frontierhunts.onboard.Handbook;
import com.formaworks.frontierhunts.onboard.OnboardNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * [onboard] Client glue for the Frontier Handbook: the synced task state, "task done" toasts, opening the book when
 * the server asks (the Handbook item), and the shared links (Field School lesson, Ranger Academy course).
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class HandbookClient {
   static OnboardNetwork.State state;

   private HandbookClient() {
   }

   @EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = Bus.MOD)
   public static final class Setup {
      @SubscribeEvent
      public static void setup(FMLClientSetupEvent e) {
         OnboardNetwork.receiver = HandbookClient::onState;
      }
   }

   // ------------------------------------------------------------------------------------------ queries

   /** Done or skipped tasks, -1 before the first sync. */
   public static int mask() {
      return state == null ? -1 : state.tasks() | state.skipped();
   }

   /** The one next thing to do (null before the first sync or when the whole path is done). */
   public static Handbook.Task next() {
      int m = mask();
      return m < 0 ? null : Handbook.next(m);
   }

   static boolean passed(Course c) {
      return c != null && state != null && (state.courses() & 1 << c.ordinal()) != 0;
   }

   public static Course recommended() { // [1.1.5] public: the Journal's Up next shows it
      return state == null ? null : Handbook.recommended(mask(), state.courses());
   }

   // ------------------------------------------------------------------------------------------ network

   static void send(byte action, int arg) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.getConnection() != null && mc.getConnection().hasChannel(OnboardNetwork.Action.TYPE)) {
         PacketDistributor.sendToServer(new OnboardNetwork.Action(action, (byte)arg), new CustomPacketPayload[0]);
      }
   }

   private static void onState(OnboardNetwork.State s) {
      Minecraft mc = Minecraft.getInstance();
      OnboardNetwork.State before = state;
      state = s;
      if (before != null && mc.player != null) {
         int fresh = s.tasks() & ~before.tasks() & ~Handbook.lessonMask(); // lessons toast through the Field School
         for (Handbook.Task t : Handbook.order()) { // [onboard2] path order
            if (t.done(fresh)) {
               Handbook.Task next = Handbook.next(s.tasks() | s.skipped());
               String body = next == null ? GuideUi.tr("onboard.frontierhunts.toast.all_done")
                  : GuideUi.tr("guide.frontierhunts.toast.next", GuideUi.tr(next.lang("title")));
               mc.getToasts().addToast(new GuideToast(GuideToast.Kind.LESSON, "item:" + t.icon,
                  GuideUi.tr("onboard.frontierhunts.toast.eyebrow", t.step, Handbook.STEPS), GuideUi.tr(t.lang("title")), body));
               mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.3F, 0.3F));
               ObjectiveCard.pulse();
               break; // one toast per update; several tasks at once (a veteran's first sync) are not news
            }
         }
      }
      if (s.has(OnboardNetwork.F_OPEN) && mc.player != null && !KillCamClient.active()) {
         if (!(mc.screen instanceof HandbookScreen)) {
            mc.setScreen(new HandbookScreen(mc.screen, -1));
         }
      }
      if (mc.screen instanceof HandbookScreen hs) {
         hs.stateChanged();
      }
   }

   @SubscribeEvent
   public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e) {
      state = null;
   }

   // ------------------------------------------------------------------------------------------ links

   /** Opens Ranger Assignments with this course selected (the server answers with the dossier). */
   public static void practice(Course c) {
      AssignmentScreen.focus(c);
      AssignmentScreen.send(0, "", 0);
   }

   /** Opens the Handbook (from anywhere: the H key, the journal's "Up next", the welcome card). */
   public static void open(int page) {
      Minecraft mc = Minecraft.getInstance();
      mc.setScreen(new HandbookScreen(mc.screen, page));
      send(OnboardNetwork.A_SYNC, 0);
   }
}
