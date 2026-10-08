package com.formaworks.frontierhunts.guide.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.client.ExpeditionOptics;
import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.client.HuntCinematics;
import com.formaworks.frontierhunts.client.KillCamClient;
import com.formaworks.frontierhunts.guide.GuideConfig;
import com.formaworks.frontierhunts.guide.GuideNetwork;
import com.formaworks.frontierhunts.guide.Lesson;
import com.formaworks.frontierhunts.onboard.Handbook;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * [guide] The compact Field School objective card, top-left. It opens up with the full objective when the lesson changes,
 * progress is made or the player holds the lesson's tool, then folds to a single quiet line. It steps aside while
 * aiming, glassing, in the kill cam, cinematics or any screen, and never draws with F1 / F3.
 */
final class ObjectiveCard {
   private static final int W = 182;
   private static final long OPEN_MS = 14000L;
   private static final Map<Lesson, List<String>> TOOLS = Map.of(
      Lesson.WIND, List.of("frontierhunts:wind_checker"),
      // [onebook] SIGN needs no tool (the Hunter's Journal is no longer an item; notes go into the Handbook's journal)
      Lesson.GLASS, List.of("frontierhunts:binoculars", "frontierhunts:rangefinder", "frontierhunts:thermal_binoculars", "frontierhunts:anatomy_binoculars",
         "frontierhunts:night_vision_binoculars", "frontierhunts:trail_camera"),
      Lesson.TRAIL, List.of("frontierhunts:hound_lead"),
      Lesson.HARVEST, List.of("frontierhunts:skinning_tool")
   );

   private static long openUntil;
   private static long pulseAt = Long.MIN_VALUE;
   private static float open;
   private static float shown;
   private static boolean holdingTool;
   private static Lesson lastLesson;
   private static String icon = null; // [fieldbook] GuideIcons name (the lesson key)
   private static long lastNanos;
   /** [onboard] the Handbook task shown (null = a lesson / nothing), to notice when the next step moves on */
   private static Handbook.Task lastTask;

   private ObjectiveCard() {
   }

   static void reset() {
      lastTask = null;
      openUntil = 0L;
      open = 0.0F;
      shown = 0.0F;
      lastLesson = null;
   }

   static void pulse() {
      pulseAt = System.currentTimeMillis();
      openUntil = System.currentTimeMillis() + OPEN_MS;
   }

   static void changed(GuideNetwork.State before, GuideNetwork.State now) {
      Lesson cur = Lesson.current(now.done());
      if (before == null || cur != lastLesson || now.progress() > before.progress() || before.has(GuideNetwork.F_SKIPPED) != now.has(GuideNetwork.F_SKIPPED)) {
         openUntil = System.currentTimeMillis() + OPEN_MS;
      }
      if (cur != lastLesson) {
         lastLesson = cur;
         icon = cur == null ? null : cur.key;
         if (before != null) {
            pulseAt = System.currentTimeMillis();
         }
      }
   }

   private static Lesson lesson() {
      GuideNetwork.State s = GuideClient.state;
      if (s == null || !GuideClient.courseRunning() || !s.has(GuideNetwork.F_WELCOMED) || !GuideConfig.hudCard()) {
         return null;
      }
      if (HandbookClient.mask() >= 0) {
         // [onboard] the card shows the Handbook's one next step; while that is a lesson, the lesson as before
         Handbook.Task t = HandbookClient.next();
         return t == null ? null : t.lesson;
      }
      if (s.has(GuideNetwork.F_GRADUATED)) {
         return null;
      }
      Lesson l = Lesson.current(s.done());
      return l == null || l.optional() ? null : l;
   }

   /** [onboard] The Handbook task the card shows instead of a lesson (crafting, the archery range, camp...), or null. */
   private static Handbook.Task task() {
      GuideNetwork.State s = GuideClient.state;
      if (s == null || !GuideClient.courseRunning() || !s.has(GuideNetwork.F_WELCOMED) || !GuideConfig.hudCard()) {
         return null;
      }
      Handbook.Task t = HandbookClient.next();
      return t == null || t.lesson != null ? null : t;
   }

   static boolean busy(Minecraft mc) { // [fharea] package-private: the first-hunt compass steps aside at the same moments
      Player p = mc.player;
      return mc.screen != null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || p == null || p.isUsingItem() || p.isScoping()
         || KillCamClient.active() || HuntCinematics.active() || ExpeditionOptics.active()
         || com.formaworks.frontierhunts.academy.client.AcademyClient.training(); // [academy] the training card owns the corner in the grounds
   }

   static void tick(Minecraft mc) {
      Lesson l = lesson();
      Handbook.Task task = task();
      if (task != lastTask) {
         if (task != null) {
            openUntil = System.currentTimeMillis() + OPEN_MS;
            icon = "item:" + task.icon;
            if (lastTask != null || lastLesson != null) {
               pulseAt = System.currentTimeMillis();
            }
         } else if (l != null) {
            icon = l.key;
            openUntil = System.currentTimeMillis() + OPEN_MS;
            pulseAt = System.currentTimeMillis();
         }
         lastTask = task;
      }
      holdingTool = false;
      if (l != null && mc.player != null) {
         List<String> tools = TOOLS.get(l);
         if (tools != null) {
            for (InteractionHand hand : InteractionHand.values()) {
               ItemStack st = mc.player.getItemInHand(hand);
               if (!st.isEmpty() && tools.contains(BuiltInRegistries.ITEM.getKey(st.getItem()).toString())) {
                  holdingTool = true;
               }
            }
         }
      }
   }

   static void render(GuiGraphics g) {
      Minecraft mc = Minecraft.getInstance();
      if (FirstHuntClient.onCard()) {
         // [1.2.7] the first hunt has the corner while it runs: one card, the current step only
         long now = System.currentTimeMillis();
         boolean reduced = HuntConfig.REDUCED_MOTION.get();
         long nanos = System.nanoTime();
         float dt = lastNanos == 0L ? 0.016F : Math.min(0.1F, (nanos - lastNanos) / 1.0E9F);
         lastNanos = nanos;
         shown += ((busy(mc) ? 0.0F : 1.0F) - shown) * (reduced ? 1.0F : 1.0F - (float)Math.exp(-dt * 9.0F));
         open += ((now < openUntil ? 1.0F : 0.0F) - open) * (reduced ? 1.0F : 1.0F - (float)Math.exp(-dt * 7.0F));
         if (shown >= 0.02F) {
            FirstHuntClient.renderCard(g, shown, open, pulseAt);
         }
         return;
      }
      Lesson l = lesson();
      Handbook.Task task = l == null ? task() : null;
      if (task != null) {
         renderTask(g, mc, task);
         return;
      }
      boolean visible = l != null && !busy(mc);
      long now = System.currentTimeMillis();
      boolean reduced = HuntConfig.REDUCED_MOTION.get();
      long nanos = System.nanoTime();
      float dt = lastNanos == 0L ? 0.016F : Math.min(0.1F, (nanos - lastNanos) / 1.0E9F);
      lastNanos = nanos;
      float rate = reduced ? 1.0F : 1.0F - (float)Math.exp(-dt * 9.0F);
      shown += ((visible ? 1.0F : 0.0F) - shown) * rate;
      open += ((holdingTool || now < openUntil ? 1.0F : 0.0F) - open) * (reduced ? 1.0F : 1.0F - (float)Math.exp(-dt * 7.0F));
      if (l == null || shown < 0.02F) {
         return;
      }
      GuideNetwork.State s = GuideClient.state;
      int done = Lesson.requiredDone(s.done());
      String eyebrow = GuideUi.tr("guide.frontierhunts.card.eyebrow", l.ordinal() + 1, 7);
      String title = GuideUi.tr(l.lang("title"));
      String detail = GuideUi.tr(l.lang("card"));
      List<FormattedCharSequence> lines = GuideUi.wrap(detail, W - 34, FrontierUi.Size.SMALL);
      if (lines.size() > 2) {
         lines = lines.subList(0, 2);
      }
      int closedH = 31;
      int openH = closedH + lines.size() * 9 + 14;
      float h = closedH + (openH - closedH) * smooth(open);
      float x = 6.0F, y = 6.0F - (1.0F - smooth(shown)) * 8.0F;
      int alpha = Mth.clamp((int)(smooth(shown) * 255), 0, 255);
      g.pose().pushPose();
      FrontierUi.rect(g, x, y, W, h, 4.0F, fade(0xD8161D17, alpha));
      FrontierUi.rect(g, x, y, 2.5F, h, 1.2F, fade(0xFFB99859, alpha));
      // pulse: a soft gold wash that fades after a lesson or progress change
      float pulse = (now - pulseAt) / 1100.0F;
      if (pulse >= 0.0F && pulse < 1.0F) {
         FrontierUi.rect(g, x, y, W, h, 4.0F, fade(0x55B99859, (int)(alpha * (1.0F - pulse))));
      }
      if (alpha > 40) {
         int a = alpha;
         if (icon != null) {
            GuideIcons.draw(g, icon, (int)x + 5, (int)y + 7, 18, a / 255.0F);
         }
         FrontierUi.text(g, eyebrow, x + 27, y + 5, fade(0xFFB99859, a), FrontierUi.Size.SMALL);
         // course pips
         for (int i = 0; i < 7; i++) {
            boolean d = (s.done() & 1 << i) != 0;
            int c = d ? 0xFFB99859 : (i == l.ordinal() ? 0xFFEFE8D7 : 0x55EFE8D7);
            FrontierUi.rect(g, x + W - 8 - (7 - i) * 6, y + 7, 4, 4, 1.0F, fade(c, a));
         }
         FrontierUi.text(g, FrontierUi.fit(title, W - 34, FrontierUi.Size.STRONG), x + 27, y + 15, fade(0xFFEFE8D7, a), FrontierUi.Size.STRONG);
         if (h > closedH + 4) {
            int innerA = (int)(a * Mth.clamp((h - closedH) / (openH - closedH), 0.0F, 1.0F));
            g.enableScissor((int)x, (int)y, (int)(x + W), (int)(y + h));
            int ty = (int)y + closedH - 2;
            for (FormattedCharSequence line : lines) {
               g.drawString(GuideUi.font(), line, (int)x + 27, ty, fade(0xFFC9C3B0, innerA), false);
               ty += 9;
            }
            String hint = GuideUi.tr("guide.frontierhunts.card.key", GuideClient.keyName());
            FrontierUi.text(g, hint, x + 27, ty + 3, fade(0xFF8E937F, innerA), FrontierUi.Size.SMALL);
            if (l.goal > 1) {
               String prog = Math.min(s.progress(), l.goal) + " / " + l.goal;
               FrontierUi.right(g, prog, x + W - 8, ty + 3, fade(0xFFB99859, innerA), FrontierUi.Size.SMALL);
            }
            g.disableScissor();
         }
      }
      g.pose().popPose();
   }

   /** [onboard] The card for a Handbook task: same card, step pips and the task's real item. */
   private static void renderTask(GuiGraphics g, Minecraft mc, Handbook.Task t) {
      long now = System.currentTimeMillis();
      boolean reduced = HuntConfig.REDUCED_MOTION.get();
      long nanos = System.nanoTime();
      float dt = lastNanos == 0L ? 0.016F : Math.min(0.1F, (nanos - lastNanos) / 1.0E9F);
      lastNanos = nanos;
      float rate = reduced ? 1.0F : 1.0F - (float)Math.exp(-dt * 9.0F);
      shown += ((busy(mc) ? 0.0F : 1.0F) - shown) * rate;
      open += ((now < openUntil ? 1.0F : 0.0F) - open) * (reduced ? 1.0F : 1.0F - (float)Math.exp(-dt * 7.0F));
      if (shown < 0.02F) {
         return;
      }
      int mask = Math.max(0, HandbookClient.mask());
      String eyebrow = GuideUi.tr("onboard.frontierhunts.card.eyebrow", t.step, Handbook.STEPS);
      String title = GuideUi.tr(t.lang("title"));
      List<FormattedCharSequence> lines = GuideUi.wrap(GuideUi.tr(t.lang("card")), W - 34, FrontierUi.Size.SMALL);
      if (lines.size() > 2) {
         lines = lines.subList(0, 2);
      }
      int closedH = 31;
      int openH = closedH + lines.size() * 9 + 14;
      float h = closedH + (openH - closedH) * smooth(open);
      float x = 6.0F, y = 6.0F - (1.0F - smooth(shown)) * 8.0F;
      int alpha = Mth.clamp((int)(smooth(shown) * 255), 0, 255);
      FrontierUi.rect(g, x, y, W, h, 4.0F, fade(0xD8161D17, alpha));
      FrontierUi.rect(g, x, y, 2.5F, h, 1.2F, fade(0xFFB99859, alpha));
      float pulse = (now - pulseAt) / 1100.0F;
      if (pulse >= 0.0F && pulse < 1.0F) {
         FrontierUi.rect(g, x, y, W, h, 4.0F, fade(0x55B99859, (int)(alpha * (1.0F - pulse))));
      }
      if (alpha <= 40) {
         return;
      }
      GuideIcons.draw(g, "item:" + t.icon, (int)x + 5, (int)y + 7, 18, alpha / 255.0F);
      FrontierUi.text(g, eyebrow, x + 27, y + 5, fade(0xFFB99859, alpha), FrontierUi.Size.SMALL);
      for (int i = 1; i <= Handbook.STEPS; i++) {
         boolean d = Handbook.stepDone(i, mask);
         int c = d ? 0xFFB99859 : (i == t.step ? 0xFFEFE8D7 : 0x55EFE8D7);
         FrontierUi.rect(g, x + W - 8 - (Handbook.STEPS + 1 - i) * 6, y + 7, 4, 4, 1.0F, fade(c, alpha));
      }
      FrontierUi.text(g, FrontierUi.fit(title, W - 34, FrontierUi.Size.STRONG), x + 27, y + 15, fade(0xFFEFE8D7, alpha), FrontierUi.Size.STRONG);
      if (h > closedH + 4) {
         int innerA = (int)(alpha * Mth.clamp((h - closedH) / (openH - closedH), 0.0F, 1.0F));
         g.enableScissor((int)x, (int)y, (int)(x + W), (int)(y + h));
         int ty = (int)y + closedH - 2;
         for (FormattedCharSequence line : lines) {
            g.drawString(GuideUi.font(), line, (int)x + 27, ty, fade(0xFFC9C3B0, innerA), false);
            ty += 9;
         }
         // [1.1.5] Handbook-only tasks can be skipped in the Handbook; say so, so a missing ingredient never stalls the path
         String key = t.lesson == null ? "onboard.frontierhunts.card.key_skip" : "guide.frontierhunts.card.key";
         FrontierUi.text(g, FrontierUi.fit(GuideUi.tr(key, GuideClient.keyName()), W - 34, FrontierUi.Size.SMALL), x + 27, ty + 3, fade(0xFF8E937F, innerA), FrontierUi.Size.SMALL);
         g.disableScissor();
      }
   }

   private static float smooth(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   private static int fade(int argb, int alpha) {
      int a = (argb >>> 24) * Mth.clamp(alpha, 0, 255) / 255;
      return a << 24 | argb & 0x00FFFFFF;
   }
}
