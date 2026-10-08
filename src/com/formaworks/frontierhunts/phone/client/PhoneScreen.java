package com.formaworks.frontierhunts.phone.client;

import com.formaworks.frontierhunts.client.trailcam.DarkroomHost;
import com.formaworks.frontierhunts.client.trailcam.TrailcamClient;
import com.formaworks.frontierhunts.phone.PhoneCams;
import com.formaworks.frontierhunts.phone.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * [phone] The Field Phone on screen. A thin shell: the whole interface is {@link PhoneUi} drawn on a {@link GuiCanvas};
 * this forwards the window size, input and ticks, and is the trail-camera darkroom's host while the Trail Cams gallery
 * covers the window (the world behind the phone goes dark so photos can develop).
 */
public final class PhoneScreen extends Screen implements DarkroomHost {
   final PhoneUi ui;
   private final GuiCanvas canvas;
   /** the Trail Cams gallery wants photos developed behind the (opaque) phone */
   boolean develop;
   private float dim;

   PhoneScreen(PhoneUi ui, GuiCanvas canvas) {
      super(Component.translatable("item.frontierhunts.field_phone"));
      this.ui = ui;
      this.canvas = canvas;
   }

   @Override
   protected void init() {
      this.ui.layout(this.width, this.height);
   }

   @Override
   public void resize(net.minecraft.client.Minecraft mc, int w, int h) {
      super.resize(mc, w, h);
      this.ui.layout(w, h);
   }

   @Override
   public boolean isPauseScreen() {
      // the world (and the phone's clock, weather and online games) carries on while you look at the phone
      return false;
   }

   @Override
   public boolean shouldCloseOnEsc() {
      return false;
   }

   @Override
   public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
      // the phone draws its own backdrop
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float pt) {
      float target = this.develop ? 1.0F : 0.0F;
      this.dim = this.develop ? 1.0F : Math.max(0.0F, this.dim - 0.08F);
      this.canvas.begin(g);
      try {
         g.drawManaged(() -> this.ui.render(this.canvas, Math.max(target, this.dim)));
      } finally {
         this.canvas.end();
      }
   }

   @Override
   public void tick() {
      PhoneClient.tickOpen(this);
      this.ui.tick();
      if (this.ui.closed() && this.minecraft != null && this.minecraft.screen == this) {
         this.minecraft.setScreen(null);
      }
   }

   @Override
   public void removed() {
      this.ui.removed();
      this.develop = false;
      TrailcamClient.hostClosed();
      PhoneClient.closedScreen();
   }

   // ------------------------------------------------------------------------------------------------ darkroom

   @Override
   public BlockPos console() {
      return PhoneCams.PHONE;
   }

   @Override
   public BlockPos uplinkCamera() {
      return this.develop ? PhoneClient.galleryCamera() : null;
   }

   @Override
   public boolean develops() {
      return this.develop;
   }

   // ------------------------------------------------------------------------------------------------ input

   @Override
   public void mouseMoved(double x, double y) {
      this.ui.mouseMoved((float)x, (float)y);
   }

   @Override
   public boolean mouseClicked(double x, double y, int button) {
      return this.ui.mouseDown((float)x, (float)y, button);
   }

   @Override
   public boolean mouseReleased(double x, double y, int button) {
      return this.ui.mouseUp((float)x, (float)y, button);
   }

   @Override
   public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
      return this.ui.mouseDragged((float)x, (float)y, button);
   }

   @Override
   public boolean mouseScrolled(double x, double y, double sx, double sy) {
      return this.ui.mouseScrolled((float)x, (float)y, (float)sy);
   }

   @Override
   public boolean keyPressed(int key, int scan, int mods) {
      if (!this.ui.grabsKeys() && PhoneClient.isPhoneKey(key, scan)) {
         this.ui.close();
         return true;
      }
      if (this.ui.keyPressed(key, mods)) {
         return true;
      }
      return super.keyPressed(key, scan, mods);
   }

   @Override
   public boolean charTyped(char c, int mods) {
      return this.ui.charTyped(c) || super.charTyped(c, mods);
   }
}
