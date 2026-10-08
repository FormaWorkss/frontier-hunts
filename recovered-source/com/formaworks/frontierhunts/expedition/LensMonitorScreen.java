package com.formaworks.frontierhunts.expedition;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public final class LensMonitorScreen extends Screen {
   private boolean exiting;

   public LensMonitorScreen() {
      super(Component.literal("Trail camera live view"));
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void renderBackground(GuiGraphics var1, int var2, int var3, float var4) {
   }

   protected void init() {
      this.addRenderableWidget(Button.builder(Component.literal("Return to station"), var1 -> this.onClose()).bounds(this.width - 153, 13, 139, 20).build());
   }

   public void render(GuiGraphics var1, int var2, int var3, float var4) {
      var1.fill(0, 0, this.width, 8, -300342499);
      var1.fill(0, this.height - 8, this.width, this.height, -300342499);
      var1.fill(0, 8, 8, this.height - 8, -300342499);
      var1.fill(this.width - 8, 8, this.width, this.height - 8, -300342499);
      var1.fill(8, 8, 190, 37, -988075742);
      var1.drawString(this.font, "● LIVE  ·  TRAIL CAMERA", 17, 19, -2757678, false);
      var1.drawString(this.font, "Esc to leave  ·  You remain at the station", 18, this.height - 27, -2365992, true);
      super.render(var1, var2, var3, var4);
   }

   public void onClose() {
      if (!this.exiting) {
         this.exiting = true;
         PacketDistributor.sendToServer(new LensView.CloseRequest(), new CustomPacketPayload[0]);
      }

      Minecraft.getInstance().setScreen(null);
   }
}
