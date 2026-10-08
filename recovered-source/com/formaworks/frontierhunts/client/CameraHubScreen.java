package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.client.trailcam.DarkroomHost;
import com.formaworks.frontierhunts.client.trailcam.Photo;
import com.formaworks.frontierhunts.client.trailcam.TrailcamClient;
import com.formaworks.frontierhunts.expedition.ScoutingNetwork;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Camera network console. [trailcam] every camera row carries its latest real photo as a thumbnail (developed here
 * too when that camera's ground is loaded), and the screen is opaque so the darkroom can work behind it.
 */
public final class CameraHubScreen extends Screen implements DarkroomHost {
   private static final int VOID = 0xFF090B0A;
   private static final int PAPER = -15328488;
   private static final int INK = -1580333;
   private static final int MUTED = -6971249;
   private static final int GOLD = -4614055;
   private static final int RULE = -13945296;
   private static final int ROW = -14867424;
   private static final int LIVE = -7946118;
   private static final int LOW = -3701935;
   private static final int NIGHT = -7363128;
   private static final int ROW_H = 38;
   private ScoutingNetwork.Hub hub;
   private int left;
   private int top;
   private int panelWidth;
   private int panelHeight;
   private int listTop;
   private int listBottom;
   private int scroll;
   private int maxScroll;
   private int age;

   private CameraHubScreen(ScoutingNetwork.Hub var1) {
      super(Component.literal("Camera network"));
      this.hub = var1;
   }

   public static void open(ScoutingNetwork.Hub var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.screen instanceof CameraHubScreen var2) {
         var2.hub = var0;
         TrailcamClient.requestHub(var2); // [trailcam]
         var2.rebuild();
      } else {
         CameraHubScreen screen = new CameraHubScreen(var0);
         var1.setScreen(screen);
         TrailcamClient.requestHub(screen); // [trailcam]
      }
   }

   @Override
   public BlockPos console() {
      return this.hub.console();
   }

   @Override
   public BlockPos uplinkCamera() {
      return null;
   }

   private void rebuild() {
      this.clearWidgets();
      this.init();
   }

   public void renderBackground(GuiGraphics var1, int var2, int var3, float var4) {
   }

   @Override
   public void tick() {
      if (++this.age == 30 && !this.hub.cameras().isEmpty()) {
         boolean any = false;
         for (ScoutingNetwork.Entry e : this.hub.cameras()) {
            any |= TrailcamClient.latest(e.pos()) != null;
         }
         if (!any) {
            TrailcamClient.requestHub(this); // [trailcam] retry once if the first request was rate-limited
         }
      }
   }

   protected void init() {
      this.panelWidth = Math.min(520, this.width - 20);
      this.panelHeight = Math.min(360, this.height - 20);
      this.left = (this.width - this.panelWidth) / 2;
      this.top = (this.height - this.panelHeight) / 2;
      this.listTop = this.top + 91;
      this.listBottom = this.top + this.panelHeight - 30;
      this.addRenderableWidget(
         Button.builder(Component.literal("Close"), var1 -> this.onClose())
            .bounds(this.left + this.panelWidth - 62, this.top + this.panelHeight - 24, 52, 18)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(
               Component.literal("Refresh"),
               var1 -> PacketDistributor.sendToServer(new ScoutingNetwork.Request(this.hub.console(), this.hub.console(), 0), new CustomPacketPayload[0])
            )
            .bounds(this.left + 8, this.top + this.panelHeight - 24, 60, 18)
            .build()
      );
   }

   public void render(GuiGraphics var1, int var2, int var3, float var4) {
      var1.fill(0, 0, this.width, this.height, VOID);
      var1.fill(this.left, this.top, this.left + this.panelWidth, this.top + this.panelHeight, PAPER);
      var1.fill(this.left, this.top, this.left + this.panelWidth, this.top + 2, GOLD);
      var1.drawString(this.font, "FRONTIER  /  CAMERA NETWORK", this.left + 10, this.top + 10, INK, false);
      String var5 = this.hub.cameras().size() + (this.hub.cameras().size() == 1 ? " camera" : " cameras");
      var1.drawString(this.font, var5, this.left + this.panelWidth - 10 - this.font.width(var5), this.top + 10, MUTED, false);
      var1.drawString(this.font, this.font.plainSubstrByWidth(this.hub.season(), this.panelWidth - 20), this.left + 10, this.top + 24, MUTED, false);
      var1.fill(this.left + 10, this.top + 42, this.left + this.panelWidth - 10, this.top + 43, RULE);
      int var6 = 0;
      int var7 = 0;
      int var8 = 0;

      for (ScoutingNetwork.Entry var10 : this.hub.cameras()) {
         if (var10.live()) {
            var6++;
         }

         var7 += var10.frames();
         if (var10.percent() < 15) {
            var8++;
         }
      }

      int var25 = (this.panelWidth - 32) / 3;
      String[] var26 = new String[]{"PHOTOS", "LENSES ONLINE", "LOW BATTERY"};
      int[] var11 = new int[]{var7, var6, var8};

      for (int var12 = 0; var12 < 3; var12++) {
         int var13 = this.left + 10 + var12 * (var25 + 6);
         var1.fill(var13, this.top + 49, var13 + var25, this.top + 81, ROW);
         var1.fill(var13, this.top + 49, var13 + 2, this.top + 81, var12 == 2 && var8 > 0 ? LOW : GOLD);
         var1.drawString(this.font, var26[var12], var13 + 8, this.top + 54, MUTED, false);
         var1.drawString(this.font, Integer.toString(var11[var12]), var13 + 8, this.top + 67, var12 == 2 && var8 > 0 ? LOW : INK, false);
      }

      var1.enableScissor(this.left + 6, this.listTop, this.left + this.panelWidth - 6, this.listBottom);
      int var27 = this.listTop - this.scroll;
      if (this.hub.cameras().isEmpty()) {
         var1.drawString(this.font, "No cameras out yet. Strap one to a tree and it reports here.", this.left + 12, var27 + 6, MUTED, false);
      }

      LocalPlayer var28 = Minecraft.getInstance().player;
      int thumbW = (ROW_H - 6) * 16 / 9;
      int textX = this.left + 12 + thumbW + 8;

      for (ScoutingNetwork.Entry var15 : this.hub.cameras()) {
         if (var27 <= this.listBottom + ROW_H && var27 >= this.listTop - ROW_H - 4) {
            boolean var16 = var2 >= this.left + 6 && var2 <= this.left + this.panelWidth - 6 && var3 >= var27 && var3 < var27 + ROW_H && var3 >= this.listTop
               && var3 < this.listBottom;
            var1.fill(this.left + 6, var27, this.left + this.panelWidth - 6, var27 + ROW_H, var16 ? -14274518 : ROW);
            this.thumb(var1, var15.pos(), this.left + 10, var27 + 3, thumbW, ROW_H - 6);
            var1.drawString(this.font, this.font.plainSubstrByWidth(var15.label(), this.panelWidth - 230), textX, var27 + 6, INK, false);
            String var17 = var28 == null
               ? ""
               : String.format(Locale.ROOT, "%.0f m %s", Math.sqrt(var15.pos().distToCenterSqr(var28.getX(), var28.getY(), var28.getZ())), compass(var15));
            Photo latest = TrailcamClient.latest(var15.pos());
            if (latest != null) {
               var17 = var17 + "  ·  " + latest.title();
            }
            var1.drawString(this.font, this.font.plainSubstrByWidth(var17, this.panelWidth - 250), textX, var27 + 20, MUTED, false);
            String var18 = var15.frames() + (var15.frames() == 1 ? " photo" : " photos");
            var1.drawString(this.font, var18, this.left + this.panelWidth - 132 - this.font.width(var18) / 2, var27 + 6, INK, false);
            String var19 = var15.over();
            var1.drawString(this.font, var19, this.left + this.panelWidth - 132 - this.font.width(var19) / 2, var27 + 20, MUTED, false);
            if (var15.bestBuck() && var15.bestScore() > 0) {
               String var20 = "Best " + var15.bestScore();
               var1.drawString(this.font, var20, this.left + this.panelWidth - 74 - this.font.width(var20) / 2, var27 + 6, GOLD, false);
            }

            String var32 = var15.percent() <= 0 ? "DEAD" : (var15.live() ? "LIVE" : "LOGGING");
            int var21 = var15.percent() <= 0 ? LOW : (var15.live() ? LIVE : MUTED);
            var1.drawString(this.font, var32, this.left + this.panelWidth - 74 - this.font.width(var32) / 2, var27 + 20, var21, false);
            int var22 = this.left + this.panelWidth - 46;
            int var23 = this.left + this.panelWidth - 12;
            var1.fill(var22, var27 + 12, var23, var27 + 16, -14406618);
            var1.fill(var22, var27 + 12, var22 + (var23 - var22) * var15.percent() / 100, var27 + 16, var15.percent() < 15 ? LOW : -8545160);
            String var24 = var15.percent() + "%";
            var1.drawString(this.font, var24, var23 - this.font.width(var24), var27 + 20, MUTED, false);
         }
         var27 += ROW_H + 1;
      }

      var1.disableScissor();
      this.maxScroll = Math.max(0, var27 + this.scroll - this.listBottom + 6);
      this.scroll = Math.min(this.scroll, this.maxScroll);
      if (this.maxScroll > 0) {
         int var29 = this.listBottom - this.listTop;
         int var30 = Math.max(12, var29 * var29 / (var29 + this.maxScroll));
         int var31 = this.listTop + (var29 - var30) * this.scroll / this.maxScroll;
         var1.fill(this.left + this.panelWidth - 5, this.listTop, this.left + this.panelWidth - 3, this.listBottom, -14406618);
         var1.fill(this.left + this.panelWidth - 5, var31, this.left + this.panelWidth - 3, var31 + var30, GOLD);
      }

      super.render(var1, var2, var3, var4);
   }

   /** [trailcam] latest photo of a camera, or a dark frame while it develops / when there is none. */
   private void thumb(GuiGraphics g, BlockPos camera, int x, int y, int w, int h) {
      g.fill(x - 1, y - 1, x + w + 1, y + h + 1, RULE);
      Photo p = TrailcamClient.latest(camera);
      if (p != null && p.state == Photo.State.READY && p.thumbLoc != null) {
         g.blit(p.thumbLoc, x, y, w, h, 0.0F, 0.0F, TrailcamClient.THUMB_W, TrailcamClient.THUMB_H, TrailcamClient.THUMB_W, TrailcamClient.THUMB_H);
         if (p.infrared()) {
            g.drawString(this.font, "IR", x + w - this.font.width("IR") - 2, y + 2, NIGHT, true);
         }
         return;
      }
      g.fill(x, y, x + w, y + h, p == null ? 0xFF101412 : 0xFF140606);
      if (p != null) {
         boolean dev = p.state == Photo.State.DEVELOPING || p.state == Photo.State.QUEUED;
         String s = dev ? "..." : (p.state == Photo.State.FAILED ? "-" : "?");
         float pulse = 0.55F + 0.45F * (float)Math.sin(Util.getMillis() / 250.0);
         int a = dev ? (int)(255 * pulse) : 255;
         g.drawString(this.font, s, x + w / 2 - this.font.width(s) / 2, y + h / 2 - 4, a << 24 | 0x9A3B33, false);
      }
   }

   private static String compass(ScoutingNetwork.Entry var0) {
      LocalPlayer var1 = Minecraft.getInstance().player;
      if (var1 == null) {
         return "";
      } else {
         double var2 = (double)var0.pos().getX() + 0.5 - var1.getX();
         double var4 = (double)var0.pos().getZ() + 0.5 - var1.getZ();
         if (var2 * var2 + var4 * var4 < 4.0) {
            return "here";
         } else {
            String[] var6 = new String[]{"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
            int var7 = (int)Math.round(Math.atan2(-var2, var4) / (Math.PI / 4)) & 7;
            return var6[var7];
         }
      }
   }

   public boolean mouseClicked(double var1, double var3, int var5) {
      if (super.mouseClicked(var1, var3, var5)) {
         return true;
      }
      if (var1 >= (double)(this.left + 6)
         && var1 <= (double)(this.left + this.panelWidth - 6)
         && var3 >= (double)this.listTop
         && var3 <= (double)this.listBottom) {
         int var6 = (int)((var3 - (double)this.listTop + (double)this.scroll) / (double)(ROW_H + 1));
         if (var6 >= 0 && var6 < this.hub.cameras().size()) {
            PacketDistributor.sendToServer(new ScoutingNetwork.Request(this.hub.console(), this.hub.cameras().get(var6).pos(), 1), new CustomPacketPayload[0]);
            return true;
         }
      }

      return false;
   }

   public boolean mouseScrolled(double var1, double var3, double var5, double var7) {
      this.scroll = Math.clamp((long)(this.scroll - (int)(var7 * (ROW_H + 1))), 0, this.maxScroll);
      return true;
   }

   @Override
   public void removed() {
      TrailcamClient.hostClosed(); // [trailcam]
      super.removed();
   }

   public boolean isPauseScreen() {
      return false;
   }
}
