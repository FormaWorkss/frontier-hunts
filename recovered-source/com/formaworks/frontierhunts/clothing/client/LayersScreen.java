package com.formaworks.frontierhunts.clothing.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.clothing.BaseLayer;
import com.formaworks.frontierhunts.clothing.BaseLayerView;
import com.formaworks.frontierhunts.clothing.Scent;
import com.formaworks.frontierhunts.expedition.ScentControl;
import com.formaworks.frontierhunts.hunting.NativeGear;
import com.formaworks.frontierhunts.survival.Clothing;
import com.formaworks.frontierhunts.survival.SurvivalApi;
import com.formaworks.frontierhunts.survival.SurvivalConfig;
import com.formaworks.frontierhunts.survival.SurvivalMath;
import com.formaworks.frontierhunts.survival.SurvivalSync;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * [clothing] "Layers": everything you wear, in the order it sits on you, with what it does. Outer clothing (the four
 * armour slots), the carbon base layer under it, pack and quiver; then the outfit's warmth (insulation, the
 * temperature it keeps you comfortable down to, wind and water, wet clothes) and the scent you put downwind (the same
 * {@link Scent} breakdown every animal reads). Opened from the Field Gear panel next to the inventory.
 *
 * <p>Laid out in the 640 x 360 Frontier design space (FrontierGuiScale). Strings are rebuilt twice a second and when the
 * server sends new state, never per frame; fills and text are batched, item icons drawn after the batch.
 */
public final class LayersScreen extends Screen {
   private static final int W = 528, H = 312, BAND = 42;
   private static final int BAND_C = 0xFF263A30, BAND_TEXT = 0xFFF3ECDB, BAND_MUTED = 0xFFA9B4A2, BAND_GOLD = 0xFFD8BD88;
   private static final int PAPER = 0xFFEAE3D0, CARD = 0xFFF3EEDF, CARD_EDGE = 0xFFD3C8AC, INK = 0xFF243A32, MUTED = 0xFF657064;
   private static final int RULE = 0xFFC5C2AE, GOLD = 0xFFB99859, SLOT = 0xFFDCD3BC, SLOT_EDGE = 0xFFB9AE90;
   private static final int GOOD = 0xFF3E6E37, WARN = 0xFFB07A1E, BAD = 0xFFA5281F;
   private static final int COL_W = (W - 28 - 20) / 3, CARD_W = (W - 28 - 10) / 2;
   private static boolean dirty;

   private final Screen parent;
   private int left, top;
   private long openedAt;
   private int ticks;
   private final Row[][] rows = new Row[3][];
   private String[] colTitle = new String[3];
   private String warmthBig = "", scentBig = "", warmthHead = "", scentHead = "", tip = "";
   private String[] warmthLines = new String[0], scentLines = new String[0];
   private String eyebrow = "", subtitle = "", titleText = "";
   private float scentBar, warmthDots;
   private int scentColor = GOOD;

   private static final class Row {
      String label = "", name = "", sub = "";
      /** fitted to the column at rebuild time (no string work per frame) */
      String labelUp = "", nameFit = "", subFit = "";
      ItemStack icon = ItemStack.EMPTY;
      boolean ghost;
      float bar = -1F;
   }

   public LayersScreen(Screen parent) {
      super(Component.translatable("clothing.frontierhunts.layers.title"));
      this.parent = parent;
      for (int c = 0; c < 3; c++) {
         this.rows[c] = new Row[c == 0 ? 4 : (c == 1 ? 3 : 2)];
         for (int i = 0; i < this.rows[c].length; i++) {
            this.rows[c][i] = new Row();
         }
      }
   }

   /** The server sent a new base layer: rebuild on the next tick. */
   static void refresh() {
      dirty = true;
   }

   @Override
   protected void init() {
      if (this.openedAt == 0L) {
         this.openedAt = System.currentTimeMillis();
      }
      this.left = (this.width - W) / 2;
      this.top = (this.height - H) / 2;
      this.rebuild();
   }

   @Override
   public void tick() {
      if (++this.ticks % 10 == 0 || dirty) {
         dirty = false;
         this.rebuild();
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      Minecraft mc = Minecraft.getInstance();
      mc.setScreen(this.parent != null ? this.parent : (mc.player != null ? new InventoryScreen(mc.player) : null));
   }

   @Override
   public boolean keyPressed(int key, int scan, int mods) {
      if (Minecraft.getInstance().options.keyInventory.matches(key, scan)) {
         this.onClose();
         return true;
      }
      return super.keyPressed(key, scan, mods);
   }

   // ============================================================================================ content

   private static String t(String key, Object... args) {
      return I18n.get("clothing.frontierhunts.layers." + key, args);
   }

   private static String pct(double v) {
      return Math.round(v * 100.0) + "%";
   }

   static String temp(float c) {
      boolean f = SurvivalConfig.units() == SurvivalConfig.Units.FAHRENHEIT;
      int v = Math.round(f ? c * 9F / 5F + 32F : c);
      return (v < 0 ? "−" + -v : Integer.toString(v)) + (f ? "°F" : "°C");
   }

   private void rebuild() {
      LocalPlayer p = Minecraft.getInstance().player;
      if (p == null) {
         return;
      }
      this.colTitle[0] = t("outer");
      this.colTitle[1] = t("base");
      this.colTitle[2] = t("packs");
      // outer clothing
      EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
      String[] labels = {"head", "torso", "legs", "feet"};
      for (int i = 0; i < 4; i++) {
         Row r = this.rows[0][i];
         ItemStack s = p.getItemBySlot(slots[i]);
         r.label = t("slot." + labels[i]);
         r.icon = s;
         r.ghost = false;
         r.bar = -1F;
         if (s.isEmpty()) {
            boolean underCoverall = i == 2 && p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof com.formaworks.frontierhunts.expedition.Coverall;
            r.name = t(underCoverall ? "coverall_legs" : "nothing");
            r.sub = "";
         } else {
            r.name = s.getHoverName().getString();
            Clothing.Warmth w = Clothing.of(s);
            float ins = Clothing.insulation(s);
            r.sub = ins > 0F ? t("warmth_sub", String.format(Locale.ROOT, "%.1f", ins), pct(w.wind()), pct(w.water())) : t("no_warmth");
         }
      }
      // base layer
      ItemStack[] base = BaseLayer.worn(p);
      boolean suit = BaseLayer.suit(base);
      String[] bl = {"hood", "top", "trousers"};
      for (int i = 0; i < 3; i++) {
         Row r = this.rows[1][i];
         ItemStack s = base[i];
         r.label = t("slot." + bl[i]);
         r.ghost = false;
         if (suit && i != BaseLayer.TOP) {
            r.icon = base[BaseLayer.TOP];
            r.ghost = true;
            r.name = t("suit_part");
            r.sub = "";
            r.bar = -1F;
         } else if (s.getItem() instanceof ScentControl c) {
            float ch = ScentControl.charge(s);
            r.icon = s;
            r.name = s.getHoverName().getString();
            r.sub = t("carbon_sub", pct(ch), pct(c.piece.share));
            r.bar = ch;
         } else {
            r.icon = ItemStack.EMPTY;
            r.name = t("nothing");
            r.sub = t("base_hint");
            r.bar = -1F;
         }
      }
      // packs
      ItemStack pack = NativeGear.pack(p), quiver = NativeGear.quiver(p);
      Row rp = this.rows[2][0], rq = this.rows[2][1];
      rp.label = t("slot.pack");
      rp.icon = pack;
      rp.name = pack.isEmpty() ? t("nothing") : pack.getHoverName().getString();
      rp.sub = pack.isEmpty() ? t("pack_hint") : t("pack_sub");
      rq.label = t("slot.quiver");
      rq.icon = quiver;
      rq.name = quiver.isEmpty() ? t("nothing") : quiver.getHoverName().getString();
      rq.sub = quiver.isEmpty() ? t("quiver_hint") : t("quiver_sub");

      // warmth
      Clothing.Outfit o = Clothing.outfit(p);
      boolean tempOn = SurvivalSync.mode(true).on();
      SurvivalApi.Status st = SurvivalApi.clientStatus();
      float wet = BaseLayerView.wet;
      this.warmthHead = t("warmth");
      this.warmthBig = String.format(Locale.ROOT, "%.1f", o.insulation());
      this.warmthDots = Mth.clamp((o.insulation() - 1F) / 7F, 0F, 1F);
      float effective = tempOn ? st.insulation() : o.insulation() * (1F - 0.6F * wet * (1F - o.water()));
      String[] wl = new String[4];
      wl[0] = t("comfort", temp(SurvivalMath.comfortLow(effective)), temp(SurvivalMath.comfortHigh(effective)));
      wl[1] = t("weather", pct(o.wind()), pct(o.water()));
      wl[2] = wet >= 0.05F ? t("wet", pct(wet)) : t("dry");
      wl[3] = tempOn ? t("felt", temp(st.feltTemperature())) : t("temp_off");
      this.warmthLines = wl;

      // scent
      Scent.Reading sr = Scent.read(p);
      this.scentHead = t("scent");
      this.scentBig = pct(sr.factor());
      this.scentBar = (float) Math.min(1.0, sr.factor());
      this.scentColor = sr.factor() <= 0.3 ? GOOD : (sr.factor() <= 0.7 ? WARN : BAD);
      String[] sl = new String[4];
      if (sr.coverage() <= 0.0) {
         sl[0] = t("no_carbon");
      } else {
         sl[0] = t(sr.coverage() >= 0.999 ? "carbon_full" : "carbon_part", pct(sr.coverage()), pct(sr.carbon() / Math.max(0.01, sr.coverage())));
      }
      String motion = sr.motion() <= Scent.SPRINT + 1e-6 ? "sprinting" : (sr.motion() < 0.999 ? "walking" : "still");
      sl[1] = t("motion." + motion) + " · " + t("sweat", pct(sr.sweat())) + " · " + t("wet_short", pct(sr.wet()));
      long left = BaseLayerView.sprayUntil - p.level().getGameTime();
      sl[2] = left > 0L ? t("spray", String.format(Locale.ROOT, "%d:%02d", left / 1200L, (left / 20L) % 60L)) : t("no_spray");
      sl[3] = sr.perks() < 0.999 ? t("perks", pct(1.0 - sr.perks())) : t("downwind", pct(1.0 - Math.min(1.0, sr.factor())));
      this.scentLines = sl;
      for (Row[] col : this.rows) {
         for (Row r : col) {
            r.labelUp = r.label.toUpperCase(Locale.ROOT);
            r.nameFit = FrontierUi.fit(r.name, COL_W - 32, FrontierUi.Size.BODY);
            r.subFit = FrontierUi.fit(r.sub, COL_W - 32 - (r.bar >= 0F ? 46 : 0), FrontierUi.Size.SMALL);
         }
      }
      for (int i = 0; i < wl.length; i++) {
         wl[i] = FrontierUi.fit(wl[i], CARD_W - 28, FrontierUi.Size.SMALL);
      }
      for (int i = 0; i < sl.length; i++) {
         sl[i] = FrontierUi.fit(sl[i], CARD_W - 28, FrontierUi.Size.SMALL);
      }
      this.warmthHead = this.warmthHead.toUpperCase(Locale.ROOT);
      this.scentHead = this.scentHead.toUpperCase(Locale.ROOT);
      this.tip = FrontierUi.fit(this.tip, W - 28, FrontierUi.Size.SMALL);
      this.eyebrow = t("eyebrow");
      this.subtitle = FrontierUi.fit(t("subtitle"), W - 220, FrontierUi.Size.SMALL);
      this.titleText = this.title.getString();
      if (sr.coverage() <= 0.0) {
         this.tip = t("tip.none");
      } else if (sr.carbon() < sr.coverage() * 0.7) {
         this.tip = t("tip.charge");
      } else if (sr.sweat() > 0.3) {
         this.tip = t("tip.sweat");
      } else if (sr.coverage() < 0.999) {
         this.tip = t("tip.partial");
      } else {
         this.tip = t("tip.good");
      }
   }

   // ============================================================================================ drawing

   @Override
   public void render(GuiGraphics g, int mx, int my, float pt) {
      float tt = HuntConfig.REDUCED_MOTION.get() ? 1.0F : Mth.clamp((System.currentTimeMillis() - this.openedAt) / 240.0F, 0.0F, 1.0F);
      float e = 1.0F - (1.0F - tt) * (1.0F - tt);
      this.renderBackground(g, mx, my, pt);
      g.pose().pushPose();
      g.pose().translate(0, (1.0F - e) * 8.0F, 0);
      int x = this.left, y = this.top;
      FrontierUi.shadow(g, x, y, W, H, 6.0F, 7.0F);
      int colW = COL_W;
      int rowsTop = y + BAND + 30;
      int cardsTop = y + BAND + 30 + 4 * 32 + 6;
      int cardW = CARD_W, cardH = y + H - 18 - cardsTop;
      boolean hoverClose = mx >= x + W - 30 && mx < x + W - 8 && my >= y + 10 && my < y + 32;
      FrontierUi.batch(g, () -> {
         FrontierUi.rect(g, x - 2, y - 2, W + 4, H + 4, 7.0F, 0xFF41352A);
         FrontierUi.rect(g, x - 1, y - 1, W + 2, H + 2, 6.5F, 0xFF88704A);
         FrontierUi.rect(g, x, y, W, H, 6.0F, PAPER);
         FrontierUi.rect(g, x, y, W, BAND, 6.0F, BAND_C);
         g.fill(x, y + BAND - 8, x + W, y + BAND, BAND_C);
         g.fillGradient(x, y + BAND / 2, x + W, y + BAND, 0x00000000, 0x30000000);
         g.fill(x, y + BAND, x + W, y + BAND + 1, GOLD);
         g.fill(x, y + BAND + 1, x + W, y + BAND + 3, 0x18000000);
         FrontierUi.text(g, this.eyebrow, x + 14, y + 9, BAND_GOLD, FrontierUi.Size.SMALL);
         FrontierUi.text(g, this.titleText, x + 14, y + 20, BAND_TEXT, FrontierUi.Size.TITLE);
         FrontierUi.right(g, this.subtitle, x + W - 40, y + 24, BAND_MUTED, FrontierUi.Size.SMALL);
         // close
         FrontierUi.rect(g, x + W - 30, y + 11, 20, 20, 4.0F, hoverClose ? 0x40FFFFFF : 0x20FFFFFF);
         FrontierUi.center(g, "×", x + W - 20, y + 16, BAND_TEXT, FrontierUi.Size.STRONG);
         // columns
         for (int c = 0; c < 3; c++) {
            int cx = x + 14 + c * (colW + 10);
            FrontierUi.text(g, this.colTitle[c], cx, y + BAND + 12, INK, FrontierUi.Size.STRONG);
            g.fill(cx, y + BAND + 24, cx + colW, y + BAND + 25, RULE);
            for (int i = 0; i < this.rows[c].length; i++) {
               this.rowBack(g, this.rows[c][i], cx, rowsTop + i * 32);
            }
         }
         // cards
         this.card(g, x + 14, cardsTop, cardW, cardH, this.warmthHead, this.warmthBig, this.warmthLines, GOLD, this.warmthDots, true);
         this.card(g, x + 14 + cardW + 10, cardsTop, cardW, cardH, this.scentHead, this.scentBig, this.scentLines, this.scentColor, this.scentBar, false);
         FrontierUi.text(g, this.tip, x + 14, y + H - 13, MUTED, FrontierUi.Size.SMALL);
      });
      // item icons after the batch
      ItemStack hover = ItemStack.EMPTY;
      for (int c = 0; c < 3; c++) {
         int cx = x + 14 + c * (colW + 10);
         for (int i = 0; i < this.rows[c].length; i++) {
            Row r = this.rows[c][i];
            int ry = rowsTop + i * 32;
            if (!r.icon.isEmpty()) {
               g.renderItem(r.icon, cx + 5, ry + 6);
               if (r.ghost) {
                  g.pose().pushPose();
                  g.pose().translate(0.0F, 0.0F, 200.0F);
                  g.fill(cx + 5, ry + 6, cx + 21, ry + 22, 0x99DCD3BC);
                  g.pose().popPose();
               }
               if (mx >= cx + 3 && mx < cx + 23 && my >= ry + 4 && my < ry + 24 && !r.ghost) {
                  hover = r.icon;
               }
            }
         }
      }
      g.pose().popPose();
      if (!hover.isEmpty()) {
         g.renderTooltip(this.font, hover, mx, my);
      }
   }

   private void rowBack(GuiGraphics g, Row r, int x, int y) {
      FrontierUi.rect(g, x + 2, y + 3, 22, 22, 3.0F, SLOT_EDGE);
      FrontierUi.rect(g, x + 3, y + 4, 20, 20, 2.5F, r.icon.isEmpty() ? SLOT : CARD);
      int tx = x + 30;
      FrontierUi.text(g, r.labelUp, tx, y + 2, MUTED, FrontierUi.Size.SMALL);
      FrontierUi.text(g, r.nameFit, tx, y + 11, r.icon.isEmpty() || r.ghost ? MUTED : INK, FrontierUi.Size.BODY);
      if (r.bar >= 0F) {
         float bw = 40F;
         FrontierUi.rect(g, tx, y + 24, bw, 3, 1.5F, 0xFFD3CBB4);
         FrontierUi.rect(g, tx, y + 24, Math.max(3F, bw * r.bar), 3, 1.5F, r.bar >= 0.7F ? GOOD : (r.bar >= 0.35F ? WARN : BAD));
         FrontierUi.text(g, r.subFit, tx + bw + 5, y + 22, MUTED, FrontierUi.Size.SMALL);
      } else if (!r.sub.isEmpty()) {
         FrontierUi.text(g, r.subFit, tx, y + 22, MUTED, FrontierUi.Size.SMALL);
      }
   }

   private void card(GuiGraphics g, int x, int y, int w, int h, String head, String big, String[] lines, int accent, float level, boolean dots) {
      FrontierUi.rect(g, x, y, w, h, 5.0F, CARD_EDGE);
      FrontierUi.rect(g, x + 1, y + 1, w - 2, h - 2, 4.5F, CARD);
      FrontierUi.rect(g, x + 1, y + 1, 4, h - 2, 2.0F, accent);
      FrontierUi.text(g, head, x + 14, y + 9, MUTED, FrontierUi.Size.SMALL);
      FrontierUi.text(g, big, x + 14, y + 20, INK, FrontierUi.Size.TITLE);
      int bx = x + 14 + Math.max(44, FrontierUi.width(big, FrontierUi.Size.TITLE) + 10), by = y + 26;
      if (dots) {
         int n = Math.round(level * 8F);
         for (int i = 0; i < 8; i++) {
            FrontierUi.circle(g, bx + 3 + i * 9, by, 3.0F, i < n ? accent : 0xFFD3CBB4);
         }
      } else {
         float bw = w - (bx - x) - 16;
         FrontierUi.rect(g, bx, by - 2, bw, 5, 2.5F, 0xFFD3CBB4);
         FrontierUi.rect(g, bx, by - 2, Math.max(5F, bw * Math.min(1F, level)), 5, 2.5F, accent);
      }
      int ly = y + 42;
      for (String l : lines) {
         FrontierUi.text(g, l, x + 14, ly, INK, FrontierUi.Size.SMALL);
         ly += 11;
      }
   }

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (mx >= this.left + W - 30 && mx < this.left + W - 8 && my >= this.top + 10 && my < this.top + 32) {
         this.onClose();
         return true;
      }
      return super.mouseClicked(mx, my, button);
   }
}
