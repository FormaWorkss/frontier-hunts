package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.camp.StationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class StationScreen extends AbstractContainerScreen<StationMenu> {
   public StationScreen(StationMenu var1, Inventory var2, Component var3) {
      super(var1, var2, var3);
      this.imageWidth = 278;
      this.imageHeight = 228;
   }

   public void renderBackground(GuiGraphics var1, int var2, int var3, float var4) {
      this.renderTransparentBackground(var1);
      this.renderBg(var1, var4, var2, var3);
   }

   protected void renderBg(GuiGraphics var1, float var2, int var3, int var4) {
      int var5 = this.leftPos;
      int var6 = this.topPos;
      int var7 = ((StationMenu)this.menu).smoke ? -3697048 : -5852282;
      var1.fill(var5 - 2, var6 - 2, var5 + 280, var6 + 230, -15391713);
      var1.fill(var5, var6, var5 + 278, var6 + 228, -2500410);
      var1.fill(var5, var6, var5 + 278, var6 + 32, ((StationMenu)this.menu).smoke ? -12436176 : -13612484);
      var1.fill(var5, var6, var5 + 278, var6 + 2, var7);
      var1.fill(var5 + 10, var6 + 66, var5 + 268, var6 + 112, -1382956);

      for (Slot var9 : ((StationMenu)this.menu).slots) {
         int var10 = var5 + var9.x;
         int var11 = var6 + var9.y;
         var1.fill(var10 - 2, var11 - 2, var10 + 18, var11 + 18, -6249073);
         var1.fill(var10 - 1, var11 - 1, var10 + 17, var11 + 17, -10325147);
         var1.fill(var10, var11, var10 + 16, var11 + 16, -4405843);
      }

      var1.fill(var5 + 131, var6 + 92, var5 + 201, var6 + 99, -4537940);
      var1.fill(
         var5 + 131,
         var6 + 92,
         var5 + 131 + 70 * ((StationMenu)this.menu).progress() / ((StationMenu)this.menu).duration(),
         var6 + 99,
         ((StationMenu)this.menu).smoke ? -5998770 : -9928115
      );

      for (int var12 = 0; var12 < 5; var12++) {
         var1.fill(var5 + 201 + var12, var6 + 91 + var12, var5 + 202 + var12, var6 + 100 - var12, -9338522);
      }

      if (((StationMenu)this.menu).reserved()) {
         var1.fill(var5 + 115, var6 + 98, var5 + 119, var6 + 102, -7887764);
      }
   }

   protected void renderLabels(GuiGraphics var1, int var2, int var3) {
      var1.drawString(this.font, this.title, 12, 12, -1120042, false);
      var1.drawString(this.font, ((StationMenu)this.menu).smoke ? "PRESERVE THE HARVEST" : "HIDE PREPARATION", 12, 41, -11309483, false);
      var1.drawString(
         this.font,
         ((StationMenu)this.menu).smoke ? "4 raw meat + 1 fuel → 4 smoked meat" : "1 hide + flint → 2 leather  ·  1 hide + bone → buckskin",
         12,
         53,
         -9669282,
         false
      );
      this.centre(var1, ((StationMenu)this.menu).smoke ? "Meat ×4" : "Hide ×1", 46, 72);
      this.centre(var1, ((StationMenu)this.menu).smoke ? "Fuel ×1" : "Flint / bone", 103, 72);
      this.centre(var1, "Finished", 230, 72);
      String var4 = ((StationMenu)this.menu).running()
         ? "Processing · " + Math.max(1, (((StationMenu)this.menu).duration() - ((StationMenu)this.menu).progress() + 19) / 20) + "s remaining"
         : (
            ((StationMenu)this.menu).reserved()
               ? "Paused · restore input or make room for output"
               : "Shift-click supplies to begin · " + (((StationMenu)this.menu).smoke ? 12 : 30) + "s per batch"
         );
      var1.drawString(this.font, var4, 139 - this.font.width(var4) / 2, 119, -10785451, false);
      var1.drawString(this.font, this.playerInventoryTitle, 58, 131, -11902128, false);
   }

   private void centre(GuiGraphics var1, String var2, int var3, int var4) {
      var1.drawString(this.font, var2, var3 - this.font.width(var2) / 2, var4, -11311024, false);
   }

   public void render(GuiGraphics var1, int var2, int var3, float var4) {
      super.render(var1, var2, var3, var4);
      this.renderTooltip(var1, var2, var3);
   }
}
