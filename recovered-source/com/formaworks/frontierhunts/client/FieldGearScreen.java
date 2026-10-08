package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.hunting.NativeGear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FieldGearScreen extends Screen {
   private int left;
   private int top;
   private final boolean fromInventory;
   private final Screen returnScreen;

   public FieldGearScreen() {
      this(false);
   }

   public FieldGearScreen(boolean var1) {
      this(var1, null);
   }

   public FieldGearScreen(Screen var1) {
      this(true, var1);
   }

   private FieldGearScreen(boolean var1, Screen var2) {
      super(Component.literal("Field gear"));
      this.fromInventory = var1;
      this.returnScreen = var2;
   }

   public void onClose() {
      Minecraft var1 = Minecraft.getInstance();
      if (this.returnScreen != null) {
         var1.setScreen(this.returnScreen);
      } else if (this.fromInventory && var1.player != null) {
         var1.setScreen(new InventoryScreen(var1.player));
      } else {
         super.onClose();
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void renderBackground(GuiGraphics var1, int var2, int var3, float var4) {
   }

   public void refresh() {
      this.clearWidgets();
      this.init();
   }

   private void send(int var1) {
      PacketDistributor.sendToServer(new NativeGear.Request(var1), new CustomPacketPayload[0]);
   }

   protected void init() {
      this.left = (this.width - 272) / 2;
      this.top = (this.height - 142) / 2;
      this.addRenderableWidget(
         Button.builder(Component.literal(this.fromInventory ? "Inventory" : "Close"), var1x -> this.onClose())
            .bounds(this.left + 211, this.top + 8, 52, 20)
            .build()
      );
      LocalPlayer var1 = Minecraft.getInstance().player;
      if (var1 != null) {
         boolean var2 = !NativeGear.pack(var1).isEmpty();
         boolean var3 = !NativeGear.quiver(var1).isEmpty();
         this.addRenderableWidget(
            Button.builder(Component.literal(var2 ? "Remove pack" : "Wear held pack"), var2x -> this.send(var2 ? 2 : 0))
               .bounds(this.left + 47, this.top + 104, 105, 20)
               .build()
         );
         this.addRenderableWidget(
            Button.builder(Component.literal(var3 ? "Remove quiver" : "Wear held quiver"), var2x -> this.send(var3 ? 3 : 1))
               .bounds(this.left + 159, this.top + 104, 105, 20)
               .build()
         );
      }
   }

   public void render(GuiGraphics var1, int var2, int var3, float var4) {
      var1.fill(0, 0, this.width, this.height, -1475340524);
      var1.fill(this.left + 3, this.top + 4, this.left + 275, this.top + 146, Integer.MIN_VALUE);
      var1.fill(this.left, this.top, this.left + 272, this.top + 142, -14273493);
      var1.fill(this.left, this.top, this.left + 272, this.top + 2, -4479885);
      var1.drawString(this.font, "FIELD GEAR", this.left + 12, this.top + 13, -1121079, false);
      LocalPlayer var5 = Minecraft.getInstance().player;
      if (var5 != null) {
         ItemStack var6 = NativeGear.pack(var5);
         ItemStack var7 = NativeGear.quiver(var5);
         var1.fill(this.left + 9, this.top + 38, this.left + 132, this.top + 98, -14997985);
         var1.fill(this.left + 140, this.top + 38, this.left + 263, this.top + 98, -14997985);
         var1.drawString(this.font, "BACKPACK", this.left + 16, this.top + 44, -2701406, false);
         var1.drawString(this.font, "QUIVER", this.left + 147, this.top + 44, -2701406, false);
         if (!var6.isEmpty()) {
            var1.renderItem(var6, this.left + 62, this.top + 65);
         }

         if (!var7.isEmpty()) {
            var1.renderItem(var7, this.left + 192, this.top + 65);
         }

         var1.drawString(this.font, var6.isEmpty() ? "Empty" : "Worn", this.left + 83, this.top + 69, -3417913, false);
         var1.drawString(this.font, var7.isEmpty() ? "Empty" : "Worn", this.left + 213, this.top + 69, -3417913, false);
      }

      var1.drawString(this.font, "Hold gear in your main hand to wear it.", this.left + 11, this.top + 130, -5917532, false);
      super.render(var1, var2, var3, var4);
   }
}
