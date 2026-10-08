package com.formaworks.frontierhunts.guide.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.firsthunt.FirstHunt;
import com.formaworks.frontierhunts.firsthunt.FirstHuntNetwork;
import com.formaworks.frontierhunts.licence.Regulations;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * [1.2.7] The first hunt on the hunter's screen: the one current step on the objective card (in place of the Handbook
 * task, so there is never more than one card), with the way to the beginner area; "Start your first hunt" when the
 * Handbook is handed over; the first-hunt page from the card, the Handbook or the H key.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class FirstHuntClient {
   static FirstHuntNetwork.State state;

   /** [phone] The first-hunt picture the server last sent (the Field Phone's Maps shows the beginner area), or null. */
   public static FirstHuntNetwork.State current() {
      return state;
   }
   private static boolean introShown;
   private static int inWorld;
   private static FirstHunt.Step lastStep;
   private static long changedAt;

   private FirstHuntClient() {
   }

   @EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
   public static final class Setup {
      @SubscribeEvent
      public static void setup(FMLClientSetupEvent e) {
         FirstHuntNetwork.stateReceiver = FirstHuntClient::onState;
      }
   }

   static void onState(FirstHuntNetwork.State s) {
      state = s;
      FirstHunt.Step now = s.current();
      if (lastStep != now) {
         ObjectiveCard.pulse(); // opens the card with the new step for a while
         lastStep = now;
         changedAt = System.currentTimeMillis();
      }
      if (Minecraft.getInstance().screen instanceof FirstHuntScreen screen) {
         screen.stateChanged();
      }
   }

   /** the guide is running and not finished */
   static boolean active() {
      return state != null && state.has(FirstHuntNetwork.F_STARTED) && !state.has(FirstHuntNetwork.F_CLAIMED);
   }

   /** the card shows the first hunt (it takes the objective card's corner) */
   static boolean onCard() {
      return active() && !state.has(FirstHuntNetwork.F_HIDDEN) && cardSetting();
   }

   private static boolean cardSetting() {
      try {
         return com.formaworks.frontierhunts.guide.GuideConfig.HUD_CARD == null || com.formaworks.frontierhunts.guide.GuideConfig.HUD_CARD.get();
      } catch (RuntimeException ex) {
         return true;
      }
   }

   static void send(byte action) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.getConnection() != null && mc.getConnection().hasChannel(FirstHuntNetwork.Action.TYPE)) {
         PacketDistributor.sendToServer(new FirstHuntNetwork.Action(action), new CustomPacketPayload[0]);
      }
   }

   static void open(boolean intro) {
      Minecraft.getInstance().setScreen(new FirstHuntScreen(Minecraft.getInstance().screen, intro));
   }

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post e) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) {
         inWorld = 0;
         return;
      }
      inWorld++;
      // "Start your first hunt": once, after the Field School welcome, when nothing else is on the screen
      if (!introShown && state != null && state.has(FirstHuntNetwork.F_INTRO) && active() && mc.screen == null && mc.getOverlay() == null
         && inWorld > 160 && mc.player.isAlive() && welcomeDone()) {
         introShown = true;
         open(true);
      }
   }

   private static boolean welcomeDone() {
      var gs = GuideClient.state;
      return gs == null || !gs.has(com.formaworks.frontierhunts.guide.GuideNetwork.F_ENABLED) || gs.has(com.formaworks.frontierhunts.guide.GuideNetwork.F_WELCOMED)
         || gs.has(com.formaworks.frontierhunts.guide.GuideNetwork.F_SKIPPED);
   }

   @SubscribeEvent
   public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e) {
      state = null;
      introShown = false;
      inWorld = 0;
      lastStep = null;
   }

   // ============================================================================================ texts

   /** [1.2.9] the number of Ranger Academy courses */
   static int academyCourses() {
      return com.formaworks.frontierhunts.academy.Course.curriculum().length;
   }

   static String title(FirstHunt.Step s) {
      return GuideUi.tr("firsthunt.frontierhunts.step." + s.key() + ".title");
   }

   /** the one line of what to do now, with the live details (licence, season, wind, area) */
   static String body(FirstHuntNetwork.State st) {
      FirstHunt.Step s = st.current();
      return switch (s) {
         case PERMIT -> {
            if (st.has(FirstHuntNetwork.F_SUSPENDED)) {
               yield GuideUi.tr("firsthunt.frontierhunts.permit.line.suspended");
            }
            if (!st.has(FirstHuntNetwork.F_LICENCE)) { // [1.2.9] the licence is the whole Ranger Academy
               yield GuideUi.tr("firsthunt.frontierhunts.permit.line.academy", st.academyDone(), academyCourses(),
                  FirstHuntScreen.AcademyKey.name());
            }
            if (!st.has(FirstHuntNetwork.F_TAG)) { // [1.2.9] a deer tag is only ever bought
               yield GuideUi.tr("firsthunt.frontierhunts.permit.line.tag", st.tagTokens());
            }
            if (!st.has(FirstHuntNetwork.F_OPEN)) {
               yield GuideUi.tr("firsthunt.frontierhunts.permit.line.closed", st.daysToOpen(), Regulations.months(st.months()));
            }
            yield GuideUi.tr("firsthunt.frontierhunts.permit.line.ok");
         }
         case SIGNS, SHOT -> {
            String how = GuideUi.tr("firsthunt.frontierhunts.step." + s.key() + ".card");
            yield where(st) + " " + how;
         }
         case WIND -> st.has(FirstHuntNetwork.F_DOWNWIND)
            ? GuideUi.tr("firsthunt.frontierhunts.step.wind.good", Math.min(st.stalk(), 3), 3)
            : GuideUi.tr("firsthunt.frontierhunts.step.wind.card");
         default -> GuideUi.tr("firsthunt.frontierhunts.step." + s.key() + ".card", FirstHunt.REWARD_TOKENS);
      };
   }

   /** "Beginner area: 140 m north-east." / "You're in the beginner area." / "Scouting for deer..." */
   static String where(FirstHuntNetwork.State st) {
      Minecraft mc = Minecraft.getInstance();
      if (!st.has(FirstHuntNetwork.F_AREA) || mc.player == null) {
         return GuideUi.tr(st.has(FirstHuntNetwork.F_ROUGH) ? "firsthunt.frontierhunts.area.rough_short" // [fharea] deer only in hard country
            : st.has(FirstHuntNetwork.F_SCOUTING) ? "firsthunt.frontierhunts.area.scouting" : "firsthunt.frontierhunts.area.pending");
      }
      if (st.has(FirstHuntNetwork.F_AREA_QUIET)) {
         return GuideUi.tr("firsthunt.frontierhunts.area.quiet_short");
      }
      double dx = st.areaX() - mc.player.getX(), dz = st.areaZ() - mc.player.getZ();
      double dist = Math.hypot(dx, dz);
      if (dist <= st.areaR()) {
         return GuideUi.tr("firsthunt.frontierhunts.area.inside");
      }
      return GuideUi.tr("firsthunt.frontierhunts.area.away", (int)Math.round(dist - st.areaR() * 0.5), GuideUi.tr("firsthunt.frontierhunts.dir." + dir(dx, dz)));
   }

   static String dir(double east, double south) {
      double a = Math.toDegrees(Math.atan2(east, -south));
      String[] names = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
      return names[Math.floorMod((int)Math.round(a / 45.0), 8)];
   }

   // ============================================================================================ the card

   /** draws the first-hunt card in the objective card's corner; false when there is nothing to show */
   static boolean renderCard(GuiGraphics g, float shown, float open, long pulseAt) {
      FirstHuntNetwork.State st = state;
      if (st == null) {
         return false;
      }
      Minecraft mc = Minecraft.getInstance();
      FirstHunt.Step s = st.current();
      int W = 196;
      String eyebrow = GuideUi.tr("firsthunt.frontierhunts.card.eyebrow", Math.min(s.ordinal() + 1, 7), 7);
      String title = title(s);
      List<FormattedCharSequence> lines = GuideUi.wrap(body(st), W - 34, FrontierUi.Size.SMALL);
      if (lines.size() > 3) {
         lines = lines.subList(0, 3);
      }
      int closedH = 31;
      int openH = closedH + lines.size() * 9 + 14;
      boolean showArrow = (s == FirstHunt.Step.SIGNS || s == FirstHunt.Step.WIND || s == FirstHunt.Step.SHOT) && st.has(FirstHuntNetwork.F_AREA)
         && !st.has(FirstHuntNetwork.F_AREA_QUIET) && mc.player != null
         && Math.hypot(st.areaX() - mc.player.getX(), st.areaZ() - mc.player.getZ()) > st.areaR()
         && !FirstHuntCompass.guiding(); // [fharea] the compass ribbon at the top shows the way; the card keeps its arrow only without it
      float h = closedH + (openH - closedH) * smooth(open);
      float x = 6.0F, y = 6.0F - (1.0F - smooth(shown)) * 8.0F;
      int alpha = Mth.clamp((int)(smooth(shown) * 255), 0, 255);
      long now = System.currentTimeMillis();
      FrontierUi.rect(g, x, y, W, h, 4.0F, fade(0xD8161D17, alpha));
      FrontierUi.rect(g, x, y, 2.5F, h, 1.2F, fade(0xFFD6A94A, alpha));
      float pulse = (now - pulseAt) / 1100.0F;
      if (pulse >= 0.0F && pulse < 1.0F) {
         FrontierUi.rect(g, x, y, W, h, 4.0F, fade(0x55D6A94A, (int)(alpha * (1.0F - pulse))));
      }
      if (alpha <= 40) {
         return true;
      }
      GuideIcons.draw(g, "item:" + icon(s), (int)x + 5, (int)y + 7, 18, alpha / 255.0F);
      FrontierUi.text(g, eyebrow, x + 27, y + 5, fade(0xFFD6A94A, alpha), FrontierUi.Size.SMALL);
      for (int i = 0; i < 7; i++) {
         boolean d = (st.done() & (1 << i)) != 0;
         int c = d ? 0xFFD6A94A : (i == s.ordinal() ? 0xFFEFE8D7 : 0x55EFE8D7);
         FrontierUi.rect(g, x + W - 8 - (7 - i) * 6, y + 7, 4, 4, 1.0F, fade(c, alpha));
      }
      FrontierUi.text(g, FrontierUi.fit(title, W - 34 - (showArrow ? 14 : 0), FrontierUi.Size.STRONG), x + 27, y + 15, fade(0xFFEFE8D7, alpha),
         FrontierUi.Size.STRONG);
      if (showArrow) {
         // a small arrow toward the beginner area, turned with the view
         double dx = st.areaX() - mc.player.getX(), dz = st.areaZ() - mc.player.getZ();
         double bearing = Math.atan2(-dx, dz) - Math.toRadians(mc.player.getYRot());
         arrow(g, x + W - 14, y + 20, (float)bearing, fade(0xFFD6A94A, alpha));
      }
      if (h > closedH + 4) {
         int innerA = (int)(alpha * Mth.clamp((h - closedH) / (openH - closedH), 0.0F, 1.0F));
         g.enableScissor((int)x, (int)y, (int)(x + W), (int)(y + h));
         int ty = (int)y + closedH - 2;
         for (FormattedCharSequence line : lines) {
            g.drawString(GuideUi.font(), line, (int)x + 27, ty, fade(0xFFC9C3B0, innerA), false);
            ty += 9;
         }
         FrontierUi.text(g, FrontierUi.fit(GuideUi.tr("firsthunt.frontierhunts.card.key", GuideClient.keyName()), W - 34, FrontierUi.Size.SMALL), x + 27, ty + 3,
            fade(0xFF8E937F, innerA), FrontierUi.Size.SMALL);
         g.disableScissor();
      }
      return true;
   }

   static String icon(FirstHunt.Step s) {
      return switch (s) {
         case PERMIT -> "frontierhunts:hunting_licence";
         case SIGNS -> "frontierhunts:wind_checker";
         case WIND -> "frontierhunts:wind_checker";
         case SHOT -> "frontierhunts:field_bow";
         case TRACK -> "frontierhunts:field_arrow";
         case HARVEST -> "frontierhunts:skinning_tool";
         default -> "frontierhunts:whitetail_trophy";
      };
   }

   /** a filled triangle pointing at {@code angle} (0 = straight ahead, up the screen) */
   static void arrow(GuiGraphics g, float cx, float cy, float angle, int color) {
      float s = 5.0F;
      double c = Math.cos(angle), sn = Math.sin(angle);
      float[][] pts = {{0, -s}, {s * 0.75F, s * 0.8F}, {0, s * 0.35F}, {-s * 0.75F, s * 0.8F}};
      float[] xs = new float[4], ys = new float[4];
      for (int i = 0; i < 4; i++) {
         xs[i] = cx + (float)(pts[i][0] * c - pts[i][1] * sn);
         ys[i] = cy + (float)(pts[i][0] * sn + pts[i][1] * c);
      }
      GuideUi.line(g, xs[0], ys[0], xs[1], ys[1], 1.6F, color);
      GuideUi.line(g, xs[1], ys[1], xs[2], ys[2], 1.6F, color);
      GuideUi.line(g, xs[2], ys[2], xs[3], ys[3], 1.6F, color);
      GuideUi.line(g, xs[3], ys[3], xs[0], ys[0], 1.6F, color);
   }

   static float smooth(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   static int fade(int argb, int alpha) {
      int a = (argb >>> 24) * Mth.clamp(alpha, 0, 255) / 255;
      return a << 24 | argb & 0x00FFFFFF;
   }

   static boolean reducedMotion() {
      try {
         return HuntConfig.REDUCED_MOTION.get();
      } catch (RuntimeException ex) {
         return false;
      }
   }
}
