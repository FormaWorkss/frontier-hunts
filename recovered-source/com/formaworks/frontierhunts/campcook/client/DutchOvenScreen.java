package com.formaworks.frontierhunts.campcook.client;

import com.formaworks.frontierhunts.campcook.CampCookContent;
import com.formaworks.frontierhunts.campcook.CampCookingRecipe;
import com.formaworks.frontierhunts.campcook.CampDishItem;
import com.formaworks.frontierhunts.campcook.DutchOvenMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * [licence] Camp Dutch Oven screen, vanilla container look: bowls and coals on the left, six ingredient slots, the
 * cooking arrow and the dish. Below the ingredients it says which dish the pot holds (matched on the client from the
 * synced recipes) and where the heat comes from.
 */
public class DutchOvenScreen extends AbstractContainerScreen<DutchOvenMenu> {
   private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
   private static final ResourceLocation FLAME = ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");
   private static final ResourceLocation ARROW = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
   private static final ResourceLocation BOWL_HINT = ResourceLocation.fromNamespaceAndPath("frontierhunts", "textures/gui/campcook/slot_hints.png");
   private RecipeHolder<CampCookingRecipe> shown;
   private int shownHash = 0;

   public DutchOvenScreen(DutchOvenMenu menu, Inventory inv, Component title) {
      super(menu, inv, title);
      this.imageWidth = 176;
      this.imageHeight = 168;
      this.inventoryLabelY = this.imageHeight - 94;
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float pt) {
      super.render(g, mx, my, pt);
      this.renderTooltip(g, mx, my);
      int ax = this.leftPos + 106, ay = this.topPos + 30;
      if (mx >= ax && mx < ax + 24 && my >= ay && my < ay + 16) {
         List<Component> tip = new ArrayList<>();
         tip.add(this.status());
         if (this.menu.total() > 0) {
            tip.add(Component.translatable("campcook.frontierhunts.screen.progress", Math.round(100.0F * this.menu.progress() / this.menu.total())));
         }
         g.renderComponentTooltip(this.font, tip, mx, my);
      }
   }

   private RecipeHolder<CampCookingRecipe> match() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return null;
      }
      List<ItemStack> l = new ArrayList<>();
      int h = 1;
      for (int i = 0; i < CampCookingRecipe.SLOTS; i++) {
         ItemStack s = this.menu.getSlot(i).getItem();
         l.add(s);
         h = h * 31 + (s.isEmpty() ? 0 : s.getItem().hashCode() * 7 + Math.min(s.getCount(), 9));
      }
      if (h != this.shownHash) {
         this.shownHash = h;
         CampCookingRecipe.PotInput in = new CampCookingRecipe.PotInput(l);
         this.shown = in.isEmpty() ? null : mc.level.getRecipeManager().getRecipeFor(CampCookContent.TYPE.get(), in, mc.level).orElse(null);
      }
      return this.shown;
   }

   private Component status() {
      int heat = this.menu.heat();
      return Component.translatable(heat == 1 ? "campcook.frontierhunts.screen.heat_fire" : heat == 2 ? "campcook.frontierhunts.screen.heat_coals"
         : "campcook.frontierhunts.screen.heat_none");
   }

   @Override
   protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
      int x = this.leftPos, y = this.topPos;
      g.blit(CHEST, x, y, 0, 0, this.imageWidth, 3 * 18 + 17);
      g.blit(CHEST, x, y + 3 * 18 + 17, 0, 126, this.imageWidth, 96);
      // clear the chest's slot rows: plain panel
      g.fill(x + 7, y + 17, x + 169, y + 71, 0xFFC6C6C6);
      for (int r = 0; r < 2; r++) {
         for (int c = 0; c < 3; c++) {
            slot(g, x + DutchOvenMenu.ING_X - 1 + c * 18, y + DutchOvenMenu.ING_Y - 1 + r * 18, 18);
         }
      }
      slot(g, x + DutchOvenMenu.BOWL_X - 1, y + DutchOvenMenu.BOWL_Y - 1, 18);
      slot(g, x + DutchOvenMenu.FUEL_X - 1, y + DutchOvenMenu.FUEL_Y - 1, 18);
      slot(g, x + DutchOvenMenu.OUT_X - 5, y + DutchOvenMenu.OUT_Y - 5, 26);
      // ghost hints in empty bowl / fuel slots
      if (this.menu.getSlot(6).getItem().isEmpty()) {
         g.blit(BOWL_HINT, x + DutchOvenMenu.BOWL_X, y + DutchOvenMenu.BOWL_Y, 0, 0, 16, 16, 32, 16);
      }
      if (this.menu.getSlot(7).getItem().isEmpty()) {
         g.blit(BOWL_HINT, x + DutchOvenMenu.FUEL_X, y + DutchOvenMenu.FUEL_Y, 16, 0, 16, 16, 32, 16);
      }
      // flame: coals burning (full when the fire below heats the pot)
      int fx = x + DutchOvenMenu.FUEL_X + 1, fy = y + 38;
      int heat = this.menu.heat();
      if (heat > 0) {
         int h = heat == 1 ? 14 : Mth.clamp(Mth.ceil(14.0F * this.menu.burn() / Math.max(1, this.menu.burnMax())), 1, 14);
         g.blitSprite(FLAME, 14, 14, 0, 14 - h, fx, fy + 14 - h, 14, h);
      }
      // cooking arrow
      int ax = x + 106, ay = y + 30;
      int total = this.menu.total();
      int w = total <= 0 ? 0 : Mth.clamp(Mth.ceil(24.0F * this.menu.progress() / total), 0, 24);
      if (w > 0) {
         g.blitSprite(ARROW, 24, 16, 0, 0, ax, ay, w, 16);
      } else {
         g.fill(ax + 2, ay + 7, ax + 18, ay + 9, 0xFF8B8B8B);
         g.fill(ax + 18, ay + 4, ax + 20, ay + 12, 0xFF8B8B8B);
         g.fill(ax + 20, ay + 6, ax + 22, ay + 10, 0xFF8B8B8B);
      }
   }

   @Override
   protected void renderLabels(GuiGraphics g, int mx, int my) {
      super.renderLabels(g, mx, my);
      RecipeHolder<CampCookingRecipe> r = this.match();
      Component line;
      int color = 0x404040;
      if (r != null) {
         CampCookingRecipe rc = r.value();
         ItemStack res = rc.result();
         line = rc.bowls() > 0 ? Component.translatable("campcook.frontierhunts.screen.dish_bowls", res.getHoverName(), res.getCount(), rc.bowls())
            : Component.translatable("campcook.frontierhunts.screen.dish", res.getHoverName(), res.getCount());
         if (this.menu.getSlot(6).getItem().getCount() < rc.bowls()) {
            line = Component.translatable("campcook.frontierhunts.screen.need_bowls", rc.bowls());
            color = 0x8A2A20;
         } else if (this.menu.heat() == 0) {
            line = Component.translatable("campcook.frontierhunts.screen.need_heat");
            color = 0x8A2A20;
         } else if (res.getItem() instanceof CampDishItem) {
            color = 0x2E5A2A;
         }
      } else {
         boolean any = false;
         for (int i = 0; i < CampCookingRecipe.SLOTS; i++) {
            any |= !this.menu.getSlot(i).getItem().isEmpty();
         }
         line = Component.translatable(any ? "campcook.frontierhunts.screen.no_dish" : "campcook.frontierhunts.screen.empty");
         color = any ? 0x8A2A20 : 0x606060;
      }
      String s = line.getString();
      int maxW = 162 - 36;
      if (this.font.width(s) > maxW) {
         s = this.font.plainSubstrByWidth(s, maxW - 6) + "…";
      }
      g.drawString(this.font, s, 38, 60, color, false);
   }

   private static void slot(GuiGraphics g, int x, int y, int size) {
      g.fill(x, y, x + size, y + size, 0xFF8B8B8B);
      g.fill(x, y, x + size - 1, y + 1, 0xFF373737);
      g.fill(x, y, x + 1, y + size - 1, 0xFF373737);
      g.fill(x + 1, y + size - 1, x + size, y + size, 0xFFFFFFFF);
      g.fill(x + size - 1, y + 1, x + size, y + size, 0xFFFFFFFF);
   }
}
