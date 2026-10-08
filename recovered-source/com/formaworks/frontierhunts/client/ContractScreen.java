package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.camp.ContractMenu;
import com.formaworks.frontierhunts.rifle.RifleContent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ContractScreen extends AbstractContainerScreen<ContractMenu> {
   private final Button[] trades = new Button[4];
   private int rowHeight;

   public ContractScreen(ContractMenu var1, Inventory var2, Component var3) {
      super(var1, var2, var3);
   }

   protected void init() {
      this.imageWidth = Math.min(418, this.width - 20);
      this.imageHeight = Math.min(278, this.height - 20);
      this.rowHeight = (this.imageHeight - 86) / 4;
      super.init();

      for (int var1 = 0; var1 < 4; var1++) {
         int var2 = var1;
         this.trades[var1] = (Button)this.addRenderableWidget(Button.builder(Component.literal(var1 >= 2 ? "Buy" : "Deliver"), var2x -> {
            if (this.minecraft.gameMode != null) {
               this.minecraft.gameMode.handleInventoryButtonClick(((ContractMenu)this.menu).containerId, var2);
            }
         }).bounds(this.leftPos + this.imageWidth - 82, this.topPos + 62 + var1 * this.rowHeight, 68, 20).build());
      }

      this.addRenderableWidget(
         Button.builder(Component.literal("×"), var1x -> this.onClose()).bounds(this.leftPos + this.imageWidth - 26, this.topPos + 8, 18, 18).build()
      );
      // [camps] outfitter guided-hunt contracts live in the camp ledger
      this.addRenderableWidget(
         Button.builder(Component.literal("Guided hunts ›"), var1x -> com.formaworks.frontierhunts.camps.client.CampScreen.send(com.formaworks.frontierhunts.camps.CampsNet.OPEN_FROM_BOARD))
            .bounds(this.leftPos + this.imageWidth - 124, this.topPos + 8, 94, 18)
            .build()
      );
   }

   public void renderBackground(GuiGraphics var1, int var2, int var3, float var4) {
      this.renderTransparentBackground(var1);
      this.renderBg(var1, var4, var2, var3);
   }

   protected void containerTick() {
      super.containerTick();
      boolean var1 = ((ContractMenu)this.menu).value(4) == 1;
      this.trades[0].active = var1 && ((ContractMenu)this.menu).value(2) >= 4;
      this.trades[1].active = var1 && ((ContractMenu)this.menu).value(3) >= 2;
      this.trades[2].active = var1 && ((ContractMenu)this.menu).value(0) >= ContractMenu.BOARD_ARROWS_PRICE && ((ContractMenu)this.menu).value(5) == 1; // [economy]
      this.trades[3].active = var1 && ((ContractMenu)this.menu).value(0) >= ContractMenu.BOARD_AMMO_PRICE && ((ContractMenu)this.menu).value(6) == 1; // [economy]
   }

   protected void renderBg(GuiGraphics var1, float var2, int var3, int var4) {
      int var5 = this.leftPos;
      int var6 = this.topPos;
      var1.fill(var5 - 3, var6 - 3, var5 + this.imageWidth + 3, var6 + this.imageHeight + 3, -12108755);
      var1.fill(var5, var6, var5 + this.imageWidth, var6 + this.imageHeight, -3753573);
      var1.fill(var5, var6, var5 + this.imageWidth, var6 + 31, -13546691);
      var1.fill(var5, var6, var5 + this.imageWidth, var6 + 2, -4612758);

      for (int var7 = 0; var7 < 4; var7++) {
         int var8 = var6 + 57 + var7 * this.rowHeight;
         var1.fill(var5 + 10, var8, var5 + this.imageWidth - 10, var8 + this.rowHeight - 4, -1121075);
         var1.fill(var5 + 10, var8, var5 + 12, var8 + this.rowHeight - 4, var7 < 2 ? -7365517 : -6650268);
         var1.fill(var5 + 16, var8 + 3, var5 + 19, var8 + 6, -6249842);
      }
   }

   protected void renderLabels(GuiGraphics var1, int var2, int var3) {
      var1.drawString(this.font, this.font.plainSubstrByWidth(this.title.getString(), this.imageWidth - 145), 12, 11, -1120557, false); // [camps] room for the Guided hunts button
      var1.drawString(this.font, "TOKENS  " + ((ContractMenu)this.menu).value(0), 12, 40, -12889790, false);
      String var4 = "DELIVERIES  " + ((ContractMenu)this.menu).value(1);
      var1.drawString(this.font, var4, this.imageWidth - 12 - this.font.width(var4), 40, -10062758, false);
      Item[] var5 = new Item[]{(Item)HuntContent.COOKED_VENISON.get(), Items.LEATHER, (Item)HuntContent.FIELD_ARROW.get(), (Item)RifleContent.AMMO.get()};

      for (int var6 = 0; var6 < 4; var6++) {
         int var7 = 60 + var6 * this.rowHeight;
         var1.renderItem(new ItemStack(var5[var6]), 18, var7 + 7);
         String var8 = Component.translatable("camp.frontierhunts.order" + var6).getString();
         var1.drawString(this.font, this.font.plainSubstrByWidth(var8, this.imageWidth - 130), 41, var7 + 3, -13546436, false);
         String var9 = Component.translatable("camp.frontierhunts.order" + var6 + ".detail").getString();
         var1.drawString(this.font, this.font.plainSubstrByWidth(var9, this.imageWidth - 130), 41, var7 + 16, -9865122, false);
         if (this.rowHeight > 42 && var6 < 2) {
            var1.drawString(
               this.font, "In pack: " + ((ContractMenu)this.menu).value(var6 == 0 ? 2 : 3) + " / " + (var6 == 0 ? 4 : 2), 41, var7 + 29, -7505838, false
            );
         }
      }

      String var10 = ((ContractMenu)this.menu).value(4) == 1 ? "Deliver supplies or exchange tokens above." : "Trading requires Survival in an active reserve.";
      var1.drawString(this.font, this.font.plainSubstrByWidth(var10, this.imageWidth - 24), 12, this.imageHeight - 16, -10917296, false);
   }
}
