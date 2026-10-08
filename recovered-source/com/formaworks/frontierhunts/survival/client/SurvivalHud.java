package com.formaworks.frontierhunts.survival.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.survival.SurvivalConfig;
import com.formaworks.frontierhunts.survival.SurvivalMath;
import com.formaworks.frontierhunts.survival.SurvivalNetwork;
import com.formaworks.frontierhunts.survival.SurvivalService;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * [survival] Compact survival HUD in the vanilla style, stacked with the vanilla status rows (uses the Gui's
 * leftHeight / rightHeight, so it sits right above the hunger/air bars and the armour bar and moves with them):
 * <ul>
 * <li>right, above hunger: three short bars with icons - protein (meat), fat, energy (grain). Low = amber and the icon
 * shivers like the vanilla hunger shanks; empty = red with a pulsing frame. A gold antler right of the row while
 * Hunter's Vigor is on.</li>
 * <li>left, above armour: thermometer + body-heat gauge (blue - comfort - red) with a needle, trend arrow and the felt air
 * temperature. Little status glyphs left of it: by a fire, under a roof, wet.</li>
 * </ul>
 * Hidden in creative/spectator, with F1, when the server's survival mode is Off, or with the client HUD option off.
 * Also draws the frost vignette while freezing out in a blizzard (vanilla powder-snow texture; [shelter] storm/danger only,
 * gone in shelter). No allocation per frame.
 */
public final class SurvivalHud {
   static final ResourceLocation ICONS = FrontierHunts.id("textures/gui/survival/hud.png");
   private static final ResourceLocation FROST = ResourceLocation.withDefaultNamespace("textures/misc/powder_snow_outline.png");
   private static final int TW = 256, TH = 32;
   private static final int[] BAR = {0xFFC8463C, 0xFFEAD7A6, 0xFFE2B03A};
   private static final float[] shown = {-1F, -1F, -1F, 0F};
   private static float frost;
   private static long lastFrostMs; // [shelter]

   private SurvivalHud() {
   }

   static boolean active(Minecraft mc) {
      LocalPlayer p = mc.player;
      if (p == null || mc.options.hideGui || mc.gameMode == null || !mc.gameMode.canHurtPlayer() || !SurvivalConfig.hud()) {
         return false;
      }
      SurvivalNetwork.State s = SurvivalNetwork.clientState();
      return s.mode() != 0 && !s.has(SurvivalNetwork.State.F_EXEMPT);
   }

   static void icon(GuiGraphics g, int x, int y, int index) { // [integ4] also used by the journal page
      g.blit(ICONS, x, y, 9, 9, index * 18F, 0F, 18, 18, TW, TH);
   }

   // ============================================================================================ nutrition row

   static void nutrition(GuiGraphics g, DeltaTracker dt) {
      Minecraft mc = Minecraft.getInstance();
      if (!active(mc)) {
         shown[0] = -1F;
         return;
      }
      SurvivalNetwork.State s = SurvivalNetwork.clientState();
      int w = g.guiWidth(), h = g.guiHeight();
      int right = w / 2 + 91;
      int y = h - mc.gui.rightHeight;
      mc.gui.rightHeight += 10;
      float[] v = {s.protein(), s.fat(), s.energy()};
      long t = mc.player.tickCount;
      RenderSystem.enableBlend();
      for (int i = 0; i < 3; i++) {
         shown[i] = shown[i] < 0F ? v[i] : shown[i] + (v[i] - shown[i]) * 0.15F;
         int x = right - 81 + i * 27;
         boolean low = v[i] <= SurvivalMath.LOW, empty = v[i] <= SurvivalMath.EMPTY;
         int jy = low && ((t / 3 + i * 7) % (empty ? 3 : 9) == 0) ? (int) ((t + i) % 2) : 0;
         icon(g, x, y + jy, i);
         int bx = x + 10, by = y + 2, bw = 16;
         int frame = empty && (t / 6) % 2 == 0 ? 0xFFB02020 : 0xC0101010;
         g.fill(bx - 1, by - 1, bx + bw + 1, by + 6, frame);
         g.fill(bx, by, bx + bw, by + 5, 0x80303030);
         int fw = Math.round(bw * Mth.clamp(shown[i] / 100F, 0F, 1F));
         int c = empty ? 0xFFD03A2A : (low ? 0xFFE08A2A : BAR[i]);
         if (fw > 0) {
            g.fill(bx, by, bx + fw, by + 5, c);
            g.fill(bx, by, bx + fw, by + 1, 0x50FFFFFF);
            g.fill(bx, by + 4, bx + fw, by + 5, 0x40000000);
         }
         // the LOW mark
         int lx = bx + Math.round(bw * SurvivalMath.LOW / 100F);
         g.fill(lx, by + 3, lx + 1, by + 5, 0x90FFFFFF);
      }
      if (s.has(SurvivalNetwork.State.F_VIGOR)) {
         float pulse = 0.75F + 0.25F * Mth.sin((t + dt.getGameTimeDeltaPartialTick(false)) * 0.12F);
         g.setColor(1F, 1F, 1F, pulse);
         icon(g, right + 2, y, 6);
         g.setColor(1F, 1F, 1F, 1F);
      }
      RenderSystem.disableBlend();
   }

   // ============================================================================================ temperature row

   static void temperature(GuiGraphics g, DeltaTracker dt) {
      Minecraft mc = Minecraft.getInstance();
      SurvivalNetwork.State s = SurvivalNetwork.clientState();
      if (!active(mc) || !s.has(SurvivalNetwork.State.F_TEMPERATURE)) {
         return;
      }
      int w = g.guiWidth(), h = g.guiHeight();
      int left = w / 2 - 91;
      int y = h - mc.gui.leftHeight;
      mc.gui.leftHeight += 10;
      long t = mc.player.tickCount;
      float heat = s.heat();
      shown[3] += (heat - shown[3]) * 0.12F;
      RenderSystem.enableBlend();
      int ic = heat <= SurvivalMath.CHILLY ? 3 : (heat >= SurvivalMath.HOT ? 5 : 4);
      int jx = heat <= SurvivalMath.SHIVER && (t % 3 == 0) ? ((t / 3) % 2 == 0 ? 1 : -1) : 0;
      icon(g, left + jx, y, ic);
      int gx = left + 10, gy = y + 2, gw = 52;
      g.fill(gx - 1, gy - 1, gx + gw + 1, gy + 6, (s.warn() & (SurvivalService.W_FREEZING | SurvivalService.W_HEATSTROKE)) != 0 && (t / 6) % 2 == 0 ? 0xFFB02020 : 0xC0101010);
      // blue -> comfort -> red, in 13 steps
      for (int i = 0; i < 13; i++) {
         int x0 = gx + i * gw / 13, x1 = gx + (i + 1) * gw / 13;
         g.fill(x0, gy, x1, gy + 5, gauge(i / 12F));
      }
      // thresholds: shiver / hypothermia on the cold side, hot on the warm side
      for (float mark : new float[]{SurvivalMath.SHIVER, SurvivalMath.HYPOTHERMIA, SurvivalMath.HOT}) {
         int mx = gx + Math.round((mark + 100F) / 200F * gw);
         g.fill(mx, gy + 3, mx + 1, gy + 5, 0x80000000);
      }
      int nx = gx + Math.round(Mth.clamp((shown[3] + 100F) / 200F, 0F, 1F) * (gw - 1));
      g.fill(nx - 1, gy - 2, nx + 2, gy + 7, 0xFF101010);
      g.fill(nx, gy - 1, nx + 1, gy + 6, 0xFFFFFFFF);
      // trend
      float tr = s.trend();
      int ax = gx + gw + 3;
      if (tr <= -0.08F) {
         g.fill(ax, y + 3, ax + 5, y + 4, 0xFF7FB8F0);
         g.fill(ax + 1, y + 4, ax + 4, y + 5, 0xFF7FB8F0);
         g.fill(ax + 2, y + 5, ax + 3, y + 6, 0xFF7FB8F0);
      } else if (tr >= 0.08F) {
         g.fill(ax + 2, y + 3, ax + 3, y + 4, 0xFFF0A060);
         g.fill(ax + 1, y + 4, ax + 4, y + 5, 0xFFF0A060);
         g.fill(ax, y + 5, ax + 5, y + 6, 0xFFF0A060);
      }
      // felt air temperature
      Font f = mc.font;
      String txt = SurvivalText.temperature(s.felt(), true);
      float lo = SurvivalMath.comfortLow(s.insulation()), hi = SurvivalMath.comfortHigh(s.insulation());
      int tc = s.felt() < lo ? 0xFF9CCBFF : (s.felt() > hi ? 0xFFFFB27A : 0xFFE6E6E6);
      g.drawString(f, txt, ax + 7, y + 1, tc, true);
      // status glyphs to the left
      int sx = left - 10;
      if (s.has(SurvivalNetwork.State.F_FIRE)) {
         icon(g, sx, y, 8);
         sx -= 10;
      }
      if (s.has(SurvivalNetwork.State.F_SHELTER)) {
         icon(g, sx, y, 9);
         sx -= 10;
      }
      if (s.wet() >= 0.2F) {
         g.setColor(1F, 1F, 1F, 0.45F + 0.55F * s.wet());
         icon(g, sx, y, 7);
         g.setColor(1F, 1F, 1F, 1F);
      }
      RenderSystem.disableBlend();
   }

   static int gauge(float f) { // [integ4] also used by the journal page
      // stops: deep blue, ice blue, pale, comfort green-grey, warm, red
      float[][] stops = {{0F, 0x1E, 0x3C, 0x8C}, {0.25F, 0x4E, 0x8E, 0xD8}, {0.42F, 0xB6, 0xD8, 0xE8}, {0.5F, 0xC8, 0xD8, 0xB0},
         {0.62F, 0xF0, 0xC8, 0x78}, {0.8F, 0xE8, 0x80, 0x40}, {1F, 0xC8, 0x30, 0x24}};
      for (int i = 1; i < stops.length; i++) {
         if (f <= stops[i][0]) {
            float k = (f - stops[i - 1][0]) / (stops[i][0] - stops[i - 1][0]);
            int r = (int) Mth.lerp(k, stops[i - 1][1], stops[i][1]);
            int gg = (int) Mth.lerp(k, stops[i - 1][2], stops[i][2]);
            int b = (int) Mth.lerp(k, stops[i - 1][3], stops[i][3]);
            return 0xFF000000 | r << 16 | gg << 8 | b;
         }
      }
      return 0xFFC83024;
   }

   // ============================================================================================ frost

   static void frost(GuiGraphics g, DeltaTracker dt) {
      Minecraft mc = Minecraft.getInstance();
      SurvivalNetwork.State s = SurvivalNetwork.clientState();
      float target = 0F;
      if (mc.player != null && SurvivalConfig.frost() && s.mode() != 0 && s.has(SurvivalNetwork.State.F_TEMPERATURE) && !s.has(SurvivalNetwork.State.F_EXEMPT)) {
         // [shelter] screen frost is a storm effect: only out in a blizzard, or as a last warning when the cold is about to
         // hurt you - never on a clear or lightly snowing day just for being chilly, and never inside a tent / cabin / cave
         // (fades out in ~1-2 s with the shared shelter value). Was up to 0.85 whenever body heat was below -40.
         float cold = Mth.clamp((-s.heat() - 40F) / 55F, 0F, 1F);
         float storm = com.formaworks.frontierhunts.weather.client.WeatherClient.blizzardAtCamera();
         float danger = Mth.clamp((-s.heat() - 82F) / 12F, 0F, 1F);
         target = 0.45F * cold * Math.max(storm, danger) * com.formaworks.frontierhunts.shelter.client.ShelterClient.open(dt.getGameTimeDeltaPartialTick(false));
      }
      // [shelter] frame-rate independent easing: builds over ~2.5 s, clears over ~0.8 s
      long nowMs = net.minecraft.Util.getMillis();
      float sec = lastFrostMs == 0L ? 0F : Mth.clamp((nowMs - lastFrostMs) / 1000F, 0F, 0.25F);
      lastFrostMs = nowMs;
      frost += (target - frost) * (1F - (float) Math.exp(-sec / (target > frost ? 2.5F : 0.8F)));
      if (frost < 0.01F || mc.options.hideGui && target == 0F) {
         return;
      }
      RenderSystem.disableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.enableBlend();
      g.setColor(1F, 1F, 1F, frost);
      g.blit(FROST, 0, 0, -90, 0.0F, 0.0F, g.guiWidth(), g.guiHeight(), g.guiWidth(), g.guiHeight());
      g.setColor(1F, 1F, 1F, 1F);
      RenderSystem.depthMask(true);
      RenderSystem.enableDepthTest();
   }

   @EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Layers {
      private Layers() {
      }

      @SubscribeEvent
      public static void layers(RegisterGuiLayersEvent e) {
         e.registerAbove(VanillaGuiLayers.AIR_LEVEL, FrontierHunts.id("survival_nutrition"), SurvivalHud::nutrition);
         e.registerAbove(VanillaGuiLayers.ARMOR_LEVEL, FrontierHunts.id("survival_temperature"), SurvivalHud::temperature);
         e.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, FrontierHunts.id("survival_frost"), SurvivalHud::frost);
      }
   }
}
