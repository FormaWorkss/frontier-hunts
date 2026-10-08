package com.formaworks.frontierhunts.landscape.ride.rig.client;

import com.formaworks.frontierhunts.landscape.ride.rig.AtvFuel;
import com.formaworks.frontierhunts.landscape.ride.rig.RigMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/** [atvfuel] Cargo box (vanilla chest look) with a rack panel: tank gauge, two can cradles, pour / unstrap buttons. */
public class RigScreen extends AbstractContainerScreen<RigMenu> {
   private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
   private static final int MAIN_W = 176;
   private static final int SIDE_X = 172;
   private static final int GAUGE_X = 181;
   private static final int GAUGE_Y = 20;
   private static final int GAUGE_W = 12;
   private static final int GAUGE_H = 54;
   private Button pour;
   private Button unstrap;
   private Button unbolt;
   private float shownFuel = -1.0F;

   public RigScreen(RigMenu menu, Inventory inv, Component title) {
      super(menu, inv, title);
      this.imageWidth = 250;
      this.imageHeight = 168;
      this.inventoryLabelY = this.imageHeight - 94;
   }

   @Override
   protected void init() {
      super.init();
      int x = this.leftPos + SIDE_X + 8;
      this.pour = this.addRenderableWidget(Button.builder(Component.translatable("gui.frontierhunts.atv_rig.pour"), b -> this.click(RigMenu.BTN_POUR))
         .bounds(x, this.topPos + 98, 64, 18).tooltip(Tooltip.create(Component.translatable("gui.frontierhunts.atv_rig.pour.tip"))).build());
      this.unstrap = this.addRenderableWidget(Button.builder(Component.translatable("gui.frontierhunts.atv_rig.unstrap"), b -> this.click(RigMenu.BTN_BOX))
         .bounds(x, this.topPos + 120, 64, 18).tooltip(Tooltip.create(Component.translatable("gui.frontierhunts.atv_rig.unstrap.tip"))).build());
      this.unbolt = this.addRenderableWidget(Button.builder(Component.translatable("gui.frontierhunts.atv_rig.unbolt"), b -> this.click(RigMenu.BTN_CARRIER))
         .bounds(x, this.topPos + 142, 64, 18).tooltip(Tooltip.create(Component.translatable("gui.frontierhunts.atv_rig.unbolt.tip"))).build());
      this.updateButtons();
   }

   private void click(int id) {
      if (this.minecraft != null && this.minecraft.gameMode != null) {
         this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
      }
   }

   private void updateButtons() {
      if (this.pour == null) {
         return;
      }
      this.pour.active = this.menu.reserve() > 0.0F && this.menu.fuel() < AtvFuel.TANK - 0.05F;
      this.unstrap.visible = this.menu.hasBox();
      this.unbolt.visible = this.menu.hasCarrier();
   }

   @Override
   protected void containerTick() {
      super.containerTick();
      this.updateButtons();
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float pt) {
      super.render(g, mx, my, pt);
      this.renderTooltip(g, mx, my);
   }

   @Override
   protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
      int x = this.leftPos;
      int y = this.topPos;
      // side panel first so the chest panel's right edge overlaps the seam
      panel(g, x + SIDE_X, y, this.imageWidth - SIDE_X, this.imageHeight);
      g.blit(CHEST, x, y, 0, 0, MAIN_W, 3 * 18 + 17);
      g.blit(CHEST, x, y + 3 * 18 + 17, 0, 126, MAIN_W, 96);
      if (!this.menu.hasBox()) {
         g.fill(x + 7, y + 17, x + 169, y + 71, 0xFF5A5A5A);
         g.fill(x + 8, y + 18, x + 168, y + 70, 0xFF3C3C3C);
         g.drawCenteredString(this.font, Component.translatable("gui.frontierhunts.atv_rig.no_box"), x + 88, y + 34, 0xFFE0E0E0);
         g.drawCenteredString(this.font, Component.translatable("gui.frontierhunts.atv_rig.no_box2"), x + 88, y + 46, 0xFF9A9A9A);
      }
      // tank gauge: recessed tube, amber fuel column, quarter ticks, red reserve band
      float target = Mth.clamp(this.menu.fuel() / AtvFuel.TANK, 0.0F, 1.0F);
      this.shownFuel = this.shownFuel < 0.0F ? target : this.shownFuel + (target - this.shownFuel) * 0.15F;
      int gx = x + GAUGE_X;
      int gy = y + GAUGE_Y;
      g.fill(gx - 1, gy - 1, gx + GAUGE_W + 1, gy + GAUGE_H + 1, 0xFF373737);
      g.fill(gx, gy, gx + GAUGE_W + 1, gy + GAUGE_H + 1, 0xFFFFFFFF);
      g.fill(gx, gy, gx + GAUGE_W, gy + GAUGE_H, 0xFF1E1E1E);
      int low = Math.round(GAUGE_H * AtvFuel.LOW);
      g.fill(gx, gy + GAUGE_H - low, gx + 2, gy + GAUGE_H, 0xFF7A1E18);
      int h = Math.round(GAUGE_H * this.shownFuel);
      if (h > 0) {
         int col = this.shownFuel < AtvFuel.LOW ? 0xFFD2462A : 0xFFE0A22C;
         int hi = this.shownFuel < AtvFuel.LOW ? 0xFFF0785A : 0xFFF6CC6A;
         g.fill(gx + 2, gy + GAUGE_H - h, gx + GAUGE_W - 1, gy + GAUGE_H, col);
         g.fill(gx + 3, gy + GAUGE_H - h, gx + 5, gy + GAUGE_H, hi);
         g.fill(gx + 2, gy + GAUGE_H - h, gx + GAUGE_W - 1, gy + GAUGE_H - h + 1, 0xFFFFE6A8);
      }
      for (int i = 1; i < 4; i++) {
         int ty = gy + GAUGE_H - GAUGE_H * i / 4;
         g.fill(gx + GAUGE_W - 4, ty, gx + GAUGE_W, ty + 1, 0xFF8A8A8A);
      }
      // can cradles: slot wells with a can silhouette when empty, dimmed when no carrier is fitted
      for (int i = 0; i < 2; i++) {
         int sx = x + RigMenu.CAN_X - 1;
         int sy = y + RigMenu.CAN_Y0 + i * RigMenu.CAN_DY - 1;
         slot(g, sx, sy);
         if (!this.menu.hasCarrier()) {
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF6E6E6E);
         } else if (this.menu.getSlot(27 + i).getItem().isEmpty()) {
            g.fill(sx + 5, sy + 4, sx + 13, sy + 15, 0x30000000);
            g.fill(sx + 6, sy + 2, sx + 11, sy + 4, 0x30000000);
         }
      }
   }

   @Override
   protected void renderLabels(GuiGraphics g, int mx, int my) {
      super.renderLabels(g, mx, my);
      int dark = 0x404040;
      g.drawString(this.font, Component.translatable("gui.frontierhunts.atv_rig.tank"), GAUGE_X - 2, 6, dark, false);
      g.drawString(this.font, Component.translatable("gui.frontierhunts.atv_rig.cans"), RigMenu.CAN_X - 1, 6, dark, false);
      String litres = String.format("%.1f L", this.menu.fuel());
      g.drawString(this.font, litres, GAUGE_X + GAUGE_W / 2 - this.font.width(litres) / 2, GAUGE_Y + GAUGE_H + 4, dark, false);
      if (this.menu.hasCarrier() || this.menu.hasBox()) {
         String res = String.format("+%.1f L", this.menu.reserve());
         g.drawString(this.font, res, RigMenu.CAN_X + 8 - this.font.width(res) / 2, GAUGE_Y + GAUGE_H + 4, this.menu.reserve() > 0.0F ? 0x7A5A10 : 0x707070, false);
      }
   }

   private static void panel(GuiGraphics g, int x, int y, int w, int h) {
      g.fill(x + 1, y, x + w - 1, y + h, 0xFF000000);
      g.fill(x, y + 1, x + w, y + h - 1, 0xFF000000);
      g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
      g.fill(x + 1, y + 1, x + w - 2, y + 3, 0xFFFFFFFF);
      g.fill(x + 1, y + 1, x + 3, y + h - 2, 0xFFFFFFFF);
      g.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, 0xFF555555);
      g.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, 0xFF555555);
   }

   private static void slot(GuiGraphics g, int x, int y) {
      g.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
      g.fill(x, y, x + 17, y + 1, 0xFF373737);
      g.fill(x, y, x + 1, y + 17, 0xFF373737);
      g.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
      g.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
   }
}
