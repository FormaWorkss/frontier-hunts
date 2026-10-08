package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.clothing.BaseLayer;
import com.formaworks.frontierhunts.clothing.BaseLayerService;
import com.formaworks.frontierhunts.clothing.Scent;
import com.formaworks.frontierhunts.clothing.client.LayersScreen;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.ScentControl;
import com.formaworks.frontierhunts.hunting.NativeGear;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The little Field Gear panel next to the inventory. [clothing] Two tabs now: <b>Field gear</b> (backpack and quiver, as
 * before) and <b>Base layer</b> (the carbon hood, top and trousers or the one-piece scent suit, worn UNDER clothing; the
 * scent you put downwind right under the slots). Both tabs have a "Layers" button that opens the full breakdown
 * ({@link LayersScreen}: outer clothing, base layer, packs, warmth and scent).
 *
 * <p>Vanilla-style bevels (it sits on the vanilla inventory). The panel hides on the recipe book page; the round toggle
 * on the inventory opens and closes it. Slot clicks go to the server (validated there): pack / quiver through
 * {@code NativeGear}, base layer through {@code BaseLayerService} (click = put on / swap / take off into the cursor,
 * shift-click = take off into the inventory). Creative keeps its cursor on the client, so the cursor stack goes along.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class FieldGearInventoryTab {
   private static boolean open = true;
   /** 0 field gear (pack, quiver), 1 base layer */
   private static int tab;
   private static final int PANEL_W = 26;
   private static final int[] PANEL_H = {64, 106};
   private static final int TAB_W = 22, TAB_H = 22;
   private static final int[][] DISC = new int[][]{{3, 6}, {2, 7}, {1, 8}, {1, 8}, {0, 9}, {1, 8}, {1, 8}, {2, 7}, {3, 6}};
   private static final int[][] FACE = new int[][]{{4, 5}, {3, 6}, {2, 7}, {2, 7}, {2, 7}, {2, 7}, {3, 6}, {4, 5}};
   private static final String[] BASE_KEYS = {"hood", "top", "trousers"};
   private static final String[] BASE_GHOSTS = {"carbon_hood", "carbon_jacket", "carbon_trousers"};
   private static int scentShown = -1;
   private static String scentText = "";

   private static boolean onMainPage(Screen s) {
      if (s instanceof CreativeModeInventoryScreen c) {
         return c.isInventoryOpen();
      }
      return s instanceof InventoryScreen i && !i.getRecipeBookComponent().isVisible();
   }

   private static int buttonX(AbstractContainerScreen<?> s) {
      return s.getGuiLeft() + (s instanceof CreativeModeInventoryScreen ? 131 : 81);
   }

   private static int buttonY(AbstractContainerScreen<?> s) {
      return s.getGuiTop() + (s instanceof CreativeModeInventoryScreen ? 24 : 50);
   }

   private static boolean overButton(AbstractContainerScreen<?> s, double x, double y) {
      int bx = buttonX(s), by = buttonY(s);
      return x >= bx && x < bx + 9 && y >= by && y < by + 9;
   }

   private static int panelX(AbstractContainerScreen<?> s) {
      return s.getGuiLeft() - PANEL_W - 2;
   }

   private static int panelY(AbstractContainerScreen<?> s) {
      return s.getGuiTop() + 6;
   }

   private static int tabX(AbstractContainerScreen<?> s) {
      return panelX(s) - TAB_W + 2;
   }

   private static int tabY(AbstractContainerScreen<?> s, int i) {
      return panelY(s) + 3 + i * (TAB_H + 2);
   }

   /** slot index under the mouse: 0..1 pack/quiver (gear tab), 0..2 hood/top/trousers (base tab), -1 none */
   private static int slotAt(AbstractContainerScreen<?> s, double x, double y) {
      int sx = panelX(s) + 5, sy = panelY(s) + 6;
      if (x < sx || x >= sx + 16) {
         return -1;
      }
      int n = tab == 0 ? 2 : 3;
      for (int i = 0; i < n; i++) {
         int y0 = sy + i * 20;
         if (y >= y0 && y < y0 + 16) {
            return i;
         }
      }
      return -1;
   }

   private static boolean overLayers(AbstractContainerScreen<?> s, double x, double y) {
      int lx = panelX(s) + 4, ly = panelY(s) + PANEL_H[tab] - 17;
      return x >= lx && x < lx + 18 && y >= ly && y < ly + 13;
   }

   private static int tabAt(AbstractContainerScreen<?> s, double x, double y) {
      int tx = tabX(s);
      if (x < tx || x >= tx + TAB_W - 2) {
         return -1;
      }
      for (int i = 0; i < 2; i++) {
         int ty = tabY(s, i);
         if (y >= ty && y < ty + TAB_H) {
            return i;
         }
      }
      return -1;
   }

   // ============================================================================================ drawing

   @SubscribeEvent
   public static void render(ScreenEvent.Render.Post e) {
      if (!(e.getScreen() instanceof AbstractContainerScreen<?> s) || !onMainPage(s)) {
         return;
      }
      LocalPlayer p = Minecraft.getInstance().player;
      if (p == null) {
         return;
      }
      GuiGraphics g = e.getGuiGraphics();
      int mx = e.getMouseX(), my = e.getMouseY();
      drawButton(g, s, mx, my);
      if (!open) {
         return;
      }
      int x = panelX(s), y = panelY(s), h = PANEL_H[tab];
      // tabs first (the panel's edge covers their inner side), then the panel
      for (int i = 0; i < 2; i++) {
         drawTab(g, s, i, i == tab, mx, my);
      }
      g.pose().pushPose();
      g.pose().translate(0.0F, 0.0F, 100.0F);
      bevel(g, x, y, PANEL_W, h);
      // the selected tab joins the panel: no border between them
      int ty = tabY(s, tab);
      g.fill(x, ty + 1, x + 3, ty + TAB_H - 1, 0xFFC6C6C6);
      g.pose().popPose();
      for (int i = 0; i < 2; i++) {
         icon(g, tabX(s) + 3, tabY(s, i) + 3, i);
      }
      if (tab == 0) {
         drawSlot(g, x + 4, y + 5, NativeGear.pack(p), ghost("hunter_pack"), false, mx, my);
         drawSlot(g, x + 4, y + 25, NativeGear.quiver(p), new ItemStack(HuntContent.QUIVER.get()), false, mx, my);
      } else {
         ItemStack[] base = BaseLayer.worn(p);
         boolean suit = BaseLayer.suit(base);
         for (int i = 0; i < 3; i++) {
            boolean covered = suit && i != BaseLayer.TOP;
            drawSlot(g, x + 4, y + 5 + i * 20, covered ? base[BaseLayer.TOP] : base[i], ghost(BASE_GHOSTS[i]), covered, mx, my);
         }
         drawScent(g, p, x, y + 66);
      }
      drawLayersButton(g, s, mx, my);
      tooltips(g, s, p, mx, my);
   }

   private static void bevel(GuiGraphics g, int x, int y, int w, int h) {
      g.fill(x + 1, y, x + w - 1, y + h, 0xFF000000);
      g.fill(x, y + 1, x + w, y + h - 1, 0xFF000000);
      g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
      g.fill(x + 1, y + 1, x + w - 2, y + 3, 0xFFFFFFFF);
      g.fill(x + 1, y + 1, x + 3, y + h - 2, 0xFFFFFFFF);
      g.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, 0xFF555555);
      g.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, 0xFF555555);
   }

   private static void drawTab(GuiGraphics g, AbstractContainerScreen<?> s, int i, boolean selected, int mx, int my) {
      int x = tabX(s), y = tabY(s, i);
      boolean hot = tabAt(s, mx, my) == i;
      g.pose().pushPose();
      g.pose().translate(0.0F, 0.0F, selected ? 100.0F : 90.0F);
      int face = selected ? 0xFFC6C6C6 : (hot ? 0xFFB4B4B4 : 0xFF9C9C9C);
      g.fill(x + 1, y, x + TAB_W, y + TAB_H, 0xFF000000);
      g.fill(x, y + 1, x + TAB_W, y + TAB_H - 1, 0xFF000000);
      g.fill(x + 1, y + 1, x + TAB_W, y + TAB_H - 1, face);
      g.fill(x + 1, y + 1, x + TAB_W, y + 3, selected ? 0xFFFFFFFF : 0xFFC9C9C9);
      g.fill(x + 1, y + 1, x + 3, y + TAB_H - 2, selected ? 0xFFFFFFFF : 0xFFC9C9C9);
      g.fill(x + 3, y + TAB_H - 3, x + TAB_W, y + TAB_H - 1, selected ? 0xFF555555 : 0xFF6E6E6E);
      g.pose().popPose();
   }

   private static void icon(GuiGraphics g, int x, int y, int i) {
      ItemStack st = ghost(i == 0 ? "hunter_pack" : "carbon_jacket");
      if (!st.isEmpty()) {
         g.pose().pushPose();
         g.pose().translate(0.0F, 0.0F, 120.0F);
         g.renderItem(st, x, y);
         g.pose().popPose();
      }
   }

   private static void drawScent(GuiGraphics g, LocalPlayer p, int x, int y) {
      double f = Scent.factor(p);
      int pc = (int) Math.round(f * 100.0);
      if (pc != scentShown) {
         scentShown = pc;
         scentText = pc + "%";
      }
      int col = f <= 0.3 ? 0xFF2F6B2A : (f <= 0.7 ? 0xFF8A5C0E : 0xFF8E2018);
      g.pose().pushPose();
      g.pose().translate(0.0F, 0.0F, 110.0F);
      // a small nose-and-wind glyph: three drift lines
      for (int k = 0; k < 3; k++) {
         g.fill(x + 6 + k, y + 1 + k * 3, x + 20 - k * 2, y + 2 + k * 3, 0xFF6F6F6F);
      }
      var font = Minecraft.getInstance().font;
      g.drawString(font, scentText, x + (PANEL_W - font.width(scentText)) / 2, y + 11, col, false);
      int bw = 18;
      g.fill(x + 4, y + 21, x + 4 + bw, y + 23, 0xFF8B8B8B);
      g.fill(x + 4, y + 21, x + 4 + Math.max(1, (int) Math.round(bw * Math.min(1.0, f))), y + 23, col);
      g.pose().popPose();
   }

   private static void drawLayersButton(GuiGraphics g, AbstractContainerScreen<?> s, int mx, int my) {
      int x = panelX(s) + 4, y = panelY(s) + PANEL_H[tab] - 17;
      boolean hot = overLayers(s, mx, my);
      g.pose().pushPose();
      g.pose().translate(0.0F, 0.0F, 110.0F);
      g.fill(x, y, x + 18, y + 13, 0xFF000000);
      g.fill(x + 1, y + 1, x + 17, y + 12, hot ? 0xFF9FA8C8 : 0xFF8B8B8B);
      g.fill(x + 1, y + 1, x + 17, y + 2, hot ? 0xFFD8DDF0 : 0xFFB5B5B5);
      g.fill(x + 1, y + 11, x + 17, y + 12, hot ? 0xFF5C6480 : 0xFF555555);
      // three stacked layers
      int[] cols = {0xFF3A4140, 0xFF5F6B3E, 0xFF8C6A45};
      for (int k = 0; k < 3; k++) {
         int ly = y + 3 + k * 3;
         g.fill(x + 4 + k, ly, x + 14 - k, ly + 2, cols[k]);
      }
      g.pose().popPose();
   }

   private static void drawButton(GuiGraphics g, AbstractContainerScreen<?> s, int mx, int my) {
      int x = buttonX(s), y = buttonY(s);
      boolean hot = overButton(s, mx, my);
      int rim = 0xFF373737;
      int face = hot ? 0xFFA8B5A0 : (open ? 0xFF8F9C86 : 0xFF8B8B8B);
      g.pose().pushPose();
      g.pose().translate(0.0F, 0.0F, 120.0F);
      for (int i = 0; i < DISC.length; i++) {
         g.fill(x + DISC[i][0], y + i, x + DISC[i][1], y + i + 1, rim);
      }
      for (int i = 0; i < FACE.length; i++) {
         g.fill(x + FACE[i][0], y + 1 + i, x + FACE[i][1], y + 2 + i, face);
      }
      g.fill(x + 3, y + 1, x + 6, y + 2, 0xFFFFFFFF);
      g.fill(x + 3, y + 7, x + 6, y + 8, 0xFF555555);
      g.fill(x + 3, y + 3, x + 6, y + 4, 0xFF6B4A2B);
      g.fill(x + 3, y + 4, x + 6, y + 6, 0xFF8A6338);
      g.fill(x + 4, y + 4, x + 5, y + 5, 0xFFCFAE6C);
      g.pose().popPose();
      if (hot) {
         g.renderTooltip(Minecraft.getInstance().font, Component.translatable(open ? "clothing.frontierhunts.panel.hide" : "clothing.frontierhunts.panel.show"),
            mx, my);
      }
   }

   private static ItemStack ghost(String id) {
      DeferredItem<Item> h = ExpeditionContent.ITEMS.get(id);
      return h == null ? ItemStack.EMPTY : new ItemStack(h.get());
   }

   private static void drawSlot(GuiGraphics g, int x, int y, ItemStack st, ItemStack ghost, boolean covered, int mx, int my) {
      g.pose().pushPose();
      g.pose().translate(0.0F, 0.0F, 100.0F);
      g.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
      g.fill(x, y, x + 17, y + 1, 0xFF373737);
      g.fill(x, y, x + 1, y + 17, 0xFF373737);
      g.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
      g.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
      g.pose().popPose();
      if (!st.isEmpty() && !covered) {
         g.renderItem(st, x + 1, y + 1);
         g.renderItemDecorations(Minecraft.getInstance().font, st, x + 1, y + 1);
      } else {
         ItemStack show = covered ? st : ghost;
         if (!show.isEmpty()) {
            g.renderItem(show, x + 1, y + 1);
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 250.0F);
            g.fill(x + 1, y + 1, x + 17, y + 17, covered ? 0x998B8B8B : 0xB08B8B8B);
            g.pose().popPose();
         }
      }
      if (mx >= x + 1 && mx < x + 17 && my >= y + 1 && my < y + 17) {
         g.pose().pushPose();
         g.pose().translate(0.0F, 0.0F, 260.0F);
         g.fill(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
         g.pose().popPose();
      }
   }

   private static void tooltips(GuiGraphics g, AbstractContainerScreen<?> s, LocalPlayer p, int mx, int my) {
      var font = Minecraft.getInstance().font;
      int t = tabAt(s, mx, my);
      if (t >= 0) {
         g.renderComponentTooltip(font, List.of(Component.translatable(t == 0 ? "clothing.frontierhunts.panel.tab.gear" : "clothing.frontierhunts.panel.tab.base"),
            Component.translatable(t == 0 ? "clothing.frontierhunts.panel.tab.gear.hint" : "clothing.frontierhunts.panel.tab.base.hint")
               .withStyle(ChatFormatting.GRAY)), mx, my);
         return;
      }
      if (overLayers(s, mx, my)) {
         g.renderComponentTooltip(font, List.of(Component.translatable("clothing.frontierhunts.panel.layers"),
            Component.translatable("clothing.frontierhunts.panel.layers.hint").withStyle(ChatFormatting.GRAY)), mx, my);
         return;
      }
      if (tab == 1 && mx >= panelX(s) && mx < panelX(s) + PANEL_W && my >= panelY(s) + 64 && my < panelY(s) + 90) {
         Scent.Reading r = Scent.read(p);
         List<Component> l = new ArrayList<>();
         l.add(Component.translatable("clothing.frontierhunts.panel.scent", Math.round(r.factor() * 100.0)));
         l.add(Component.translatable(r.coverage() <= 0.0 ? "clothing.frontierhunts.panel.scent.none" : "clothing.frontierhunts.panel.scent.carbon",
            Math.round(r.coverage() * 100.0), Math.round(r.carbon() / Math.max(0.01, r.coverage()) * 100.0)).withStyle(ChatFormatting.GRAY));
         l.add(Component.translatable("clothing.frontierhunts.panel.scent.more").withStyle(ChatFormatting.DARK_GRAY));
         g.renderComponentTooltip(font, l, mx, my);
         return;
      }
      int slot = slotAt(s, mx, my);
      if (slot < 0 || !s.getMenu().getCarried().isEmpty()) {
         return;
      }
      if (tab == 0) {
         ItemStack st = slot == 0 ? NativeGear.pack(p) : NativeGear.quiver(p);
         List<Component> l = st.isEmpty()
            ? List.of(Component.translatable(slot == 0 ? "clothing.frontierhunts.panel.pack" : "clothing.frontierhunts.panel.quiver"),
               Component.translatable(slot == 0 ? "clothing.frontierhunts.panel.pack.hint" : "clothing.frontierhunts.panel.quiver.hint")
                  .withStyle(ChatFormatting.GRAY),
               Component.translatable("clothing.frontierhunts.panel.together").withStyle(ChatFormatting.DARK_GRAY))
            : List.of(st.getHoverName(), Component.translatable("clothing.frontierhunts.panel.off").withStyle(ChatFormatting.GRAY));
         g.renderComponentTooltip(font, l, mx, my);
         return;
      }
      ItemStack[] base = BaseLayer.worn(p);
      if (BaseLayer.suit(base) && slot != BaseLayer.TOP) {
         g.renderComponentTooltip(font, List.of(Component.translatable("clothing.frontierhunts.panel.base." + BASE_KEYS[slot]),
            Component.translatable("clothing.frontierhunts.panel.suit_covers").withStyle(ChatFormatting.GRAY)), mx, my);
         return;
      }
      ItemStack st = base[slot];
      if (st.isEmpty()) {
         g.renderComponentTooltip(font, List.of(Component.translatable("clothing.frontierhunts.panel.base." + BASE_KEYS[slot]),
            Component.translatable("clothing.frontierhunts.panel.base." + BASE_KEYS[slot] + ".hint").withStyle(ChatFormatting.GRAY),
            Component.translatable("clothing.frontierhunts.panel.base.under").withStyle(ChatFormatting.DARK_GRAY)), mx, my);
         return;
      }
      List<Component> l = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), st));
      l.add(Component.translatable("clothing.frontierhunts.panel.base.off").withStyle(ChatFormatting.GRAY));
      g.renderComponentTooltip(font, l, mx, my);
   }

   private static void clickSound(float pitch, float vol) {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_LEATHER.value(), pitch, vol));
   }

   // ============================================================================================ input

   @SubscribeEvent
   public static void click(ScreenEvent.MouseButtonPressed.Pre e) {
      if (!(e.getScreen() instanceof AbstractContainerScreen<?> s) || !onMainPage(s)) {
         return;
      }
      double x = e.getMouseX(), y = e.getMouseY();
      if (overButton(s, x, y)) {
         open = !open;
         e.setCanceled(true);
         clickSound(1.4F, 0.35F);
         return;
      }
      if (!open) {
         return;
      }
      int t = tabAt(s, x, y);
      if (t >= 0) {
         e.setCanceled(true);
         if (t != tab) {
            tab = t;
            clickSound(1.6F, 0.3F);
         }
         return;
      }
      int px = panelX(s), py = panelY(s);
      if (x < px || x >= px + PANEL_W || y < py || y >= py + PANEL_H[tab]) {
         return;
      }
      e.setCanceled(true);
      if (overLayers(s, x, y)) {
         clickSound(1.5F, 0.3F);
         Minecraft.getInstance().setScreen(new LayersScreen(s));
         return;
      }
      int slot = slotAt(s, x, y);
      if (slot < 0 || e.getButton() > 1) {
         return;
      }
      ItemStack carried = s.getMenu().getCarried();
      boolean creative = s instanceof CreativeModeInventoryScreen;
      if (tab == 0) {
         if (carried.isEmpty() || (slot == 0 ? NativeGear.fitsPack(carried) : NativeGear.fitsQuiver(carried))) {
            PacketDistributor.sendToServer(new NativeGear.Slot(slot, creative ? carried.copy() : ItemStack.EMPTY));
            if (creative && !carried.isEmpty()) {
               s.getMenu().setCarried(carried.getCount() > 1 ? carried.copyWithCount(carried.getCount() - 1) : ItemStack.EMPTY);
            }
            clickSound(1.1F, 0.6F);
         }
         return;
      }
      // the suit fills all three slots: any of them takes it off
      int target = BaseLayer.suit(BaseLayer.worn(Minecraft.getInstance().player)) ? BaseLayer.TOP : slot;
      if (carried.isEmpty() && Screen.hasShiftDown()) {
         PacketDistributor.sendToServer(new BaseLayerService.Action(BaseLayerService.Action.TAKE_OFF, target, ItemStack.EMPTY));
         clickSound(0.9F, 0.6F);
         return;
      }
      if (carried.getItem() instanceof ScentControl c) {
         target = BaseLayer.slotFor(c.piece); // a piece dropped on any of the three slots goes where it belongs
      } else if (!carried.isEmpty()) {
         return;
      }
      PacketDistributor.sendToServer(new BaseLayerService.Action(BaseLayerService.Action.CLICK, target, creative ? carried.copy() : ItemStack.EMPTY));
      if (creative && !carried.isEmpty()) {
         s.getMenu().setCarried(carried.getCount() > 1 ? carried.copyWithCount(carried.getCount() - 1) : ItemStack.EMPTY);
      }
      clickSound(1.1F, 0.6F);
   }

   private FieldGearInventoryTab() {
   }
}
