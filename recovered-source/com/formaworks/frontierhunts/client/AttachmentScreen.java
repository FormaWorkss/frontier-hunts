package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.workshop.AttachmentFitting;
import com.formaworks.frontierhunts.workshop.AttachmentMenu;
import com.formaworks.frontierhunts.workshop.EquipmentCatalog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class AttachmentScreen extends AbstractContainerScreen<AttachmentMenu> {
   private Button install;
   private int page;
   private List<String> fitted = List.of();
   private final List<Button> partButtons = new ArrayList<>();

   public AttachmentScreen(AttachmentMenu var1, Inventory var2, Component var3) {
      super(var1, var2, var3);
      this.imageWidth = 300;
      this.imageHeight = 222;
   }

   protected void init() {
      super.init();
      this.rebuild();
   }

   private void rebuild() {
      this.clearWidgets();
      this.partButtons.clear();
      this.fitted = EquipmentCatalog.PARTS.stream().filter(var1x -> AttachmentFitting.fitted(((AttachmentMenu)this.menu).gun(), var1x)).toList();
      this.page = Math.clamp((long)this.page, 0, Math.max(0, (this.fitted.size() - 1) / 7));
      this.install = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("Install attachment"), var1x -> this.send(0)).bounds(this.leftPos + 49, this.topPos + 89, 120, 20).build()
      );

      for (int var1 = 0; var1 < 7 && this.page * 7 + var1 < this.fitted.size(); var1++) {
         String var2 = this.fitted.get(this.page * 7 + var1);
         int var3 = 1 + EquipmentCatalog.PARTS.indexOf(var2);
         Button var4 = Button.builder(
               Component.literal("× " + new ItemStack(AttachmentFitting.part(var2)).getHoverName().getString()), var2x -> this.send(var3)
            )
            .bounds(this.leftPos + 181, this.topPos + 46 + var1 * 19, 110, 18)
            .build();
         var4.setTooltip(Tooltip.create(Component.literal("Remove and return " + new ItemStack(AttachmentFitting.part(var2)).getHoverName().getString())));
         this.addRenderableWidget(var4);
         this.partButtons.add(var4);
      }

      if (this.fitted.size() > 7) {
         ((Button)this.addRenderableWidget(Button.builder(Component.literal("‹"), var1x -> {
            this.page--;
            this.rebuild();
         }).bounds(this.leftPos + 182, this.topPos + 180, 22, 14).build())).active = this.page > 0;
         ((Button)this.addRenderableWidget(Button.builder(Component.literal("›"), var1x -> {
            this.page++;
            this.rebuild();
         }).bounds(this.leftPos + 269, this.topPos + 180, 22, 14).build())).active = (this.page + 1) * 7 < this.fitted.size();
      }

      this.addRenderableWidget(
         Button.builder(Component.literal("Get attachments"), var1x -> this.send(100)).bounds(this.leftPos + 181, this.topPos + 197, 110, 18).build()
      );
   }

   private void send(int var1) {
      if (this.minecraft.gameMode != null) {
         this.minecraft.gameMode.handleInventoryButtonClick(((AttachmentMenu)this.menu).containerId, var1);
      }
   }

   protected void containerTick() {
      super.containerTick();
      List var1 = EquipmentCatalog.PARTS.stream().filter(var1x -> AttachmentFitting.fitted(((AttachmentMenu)this.menu).gun(), var1x)).toList();
      if (!var1.equals(this.fitted)) {
         this.rebuild();
      }

      this.install.active = ((AttachmentMenu)this.menu).canInstall();
   }

   public void render(GuiGraphics var1, int var2, int var3, float var4) {
      super.render(var1, var2, var3, var4);
      this.renderTooltip(var1, var2, var3);
   }

   protected void renderBg(GuiGraphics var1, float var2, int var3, int var4) {
      int var5 = this.leftPos;
      int var6 = this.topPos;
      var1.fill(var5 - 2, var6 - 2, var5 + 302, var6 + 224, -15919333);
      var1.fill(var5, var6, var5 + 300, var6 + 222, -14338504);
      var1.fill(var5, var6, var5 + 300, var6 + 2, -4346497);
      var1.fill(var5 + 7, var6 + 29, var5 + 173, var6 + 128, -15259862);
      var1.fill(var5 + 179, var6 + 29, var5 + 293, var6 + 193, -15259862);

      for (Slot var8 : ((AttachmentMenu)this.menu).slots) {
         int var9 = var5 + var8.x;
         int var10 = var6 + var8.y;
         var1.fill(var9 - 1, var10 - 1, var9 + 17, var10 + 17, -9272965);
         var1.fill(var9, var10, var9 + 16, var10 + 16, -13417399);
      }

      if (!((AttachmentMenu)this.menu).gun().isEmpty()) {
         var1.pose().pushPose();
         var1.pose().translate((float)(var5 + 104), (float)(var6 + 43), 0.0F);
         var1.pose().scale(2.0F, 2.0F, 2.0F);
         var1.renderItem(((AttachmentMenu)this.menu).gun(), 0, 0);
         var1.pose().popPose();
      }
   }

   protected void renderLabels(GuiGraphics var1, int var2, int var3) {
      var1.drawString(this.font, "FIELD FITTING", 10, 11, -923179, false);
      var1.drawString(this.font, "GUN", 18, 33, -3294329, false);
      var1.drawString(this.font, "PART", 18, 80, -3294329, false);
      if (((AttachmentMenu)this.menu).gun().isEmpty()) {
         var1.drawWordWrap(this.font, Component.literal("Drag or shift-click a gun here"), 48, 45, 118, -4667452);
      } else {
         var1.drawString(this.font, this.font.plainSubstrByWidth(((AttachmentMenu)this.menu).gun().getHoverName().getString(), 148), 12, 68, -1579559, false);
      }

      var1.drawWordWrap(
         this.font, Component.literal(((AttachmentMenu)this.menu).problem()), 12, 113, 156, ((AttachmentMenu)this.menu).canInstall() ? -4663133 : -3750729
      );
      var1.drawString(this.font, "YOUR INVENTORY", 8, 130, -4601917, false);
      var1.drawString(this.font, "INSTALLED", 184, 34, -3294329, false);
      if (this.fitted.isEmpty()) {
         var1.drawWordWrap(this.font, Component.literal("Installed parts appear here. Click a part to remove it."), 187, 52, 99, -5982027);
      }
   }
}
