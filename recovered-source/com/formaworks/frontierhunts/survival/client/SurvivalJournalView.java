package com.formaworks.frontierhunts.survival.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.journal.HunterSkills;
import com.formaworks.frontierhunts.journal.Perk;
import com.formaworks.frontierhunts.journal.client.JournalPageView;
import com.formaworks.frontierhunts.journal.client.JournalUi;
import com.formaworks.frontierhunts.survival.SurvivalConfig;
import com.formaworks.frontierhunts.survival.SurvivalMath;
import com.formaworks.frontierhunts.survival.SurvivalNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * [integ4] The Hunter's Journal "Survival" page: live nutrition meters and body temperature (from the synced HUD state,
 * so they move while the journal is open), the season's yield outlook with a year chart of game condition, the hunter's
 * lifetime survival record and the Woodcraft perks that act on Survival. Drawn in the journal's ink-on-paper style with
 * the Survival HUD's own icons. One layout routine measures ({@code g == null}) and draws, so height and drawing agree.
 */
public final class SurvivalJournalView implements JournalPageView {
   private static final String L = "survival.frontierhunts.journal.";
   private static final int PROTEIN = 0xFFA8402F, FAT = 0xFF9C7A45, ENERGY = 0xFFD08F22;
   private static final int[] METER = {PROTEIN, FAT, ENERGY};
   private static final String[] MONTHS = {"J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"};
   private static final float[] CONDITION = new float[12];
   private final Map<String, List<FormattedCharSequence>> wrapped = new HashMap<>();
   private final Map<String, FormattedCharSequence> seqs = new HashMap<>();
   /** structure the page was laid out for (mode / exempt / temperature on); a change asks the journal to lay out again */
   private int layoutKey = -1;

   static {
      for (int m = 0; m < 12; m++) {
         CONDITION[m] = SurvivalMath.condition(m + 0.5);
      }
   }

   @EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void setup(FMLClientSetupEvent e) {
         JournalPageView.Views.register("survival", new SurvivalJournalView());
      }
   }

   @Override
   public int height(CompoundTag data, int width) {
      this.wrapped.clear();
      this.seqs.clear();
      this.layoutKey = structure(data);
      return this.paint(null, data, 0, 0, width);
   }

   @Override
   public void render(GuiGraphics g, CompoundTag data, int x, int y, int width, int mouseX, int mouseY) {
      if (structure(data) != this.layoutKey) {
         this.layoutKey = structure(data);
         com.formaworks.frontierhunts.journal.client.JournalClient.version++; // live state changed shape: re-layout next tick
      }
      this.paint(g, data, x, y, width);
   }

   private static int structure(CompoundTag d) {
      SurvivalNetwork.State s = SurvivalNetwork.clientState();
      int mode = d.contains("mode") ? d.getByte("mode") : s.mode();
      return mode * 4 + (s.has(SurvivalNetwork.State.F_EXEMPT) ? 2 : 0) + (s.has(SurvivalNetwork.State.F_TEMPERATURE) ? 1 : 0);
   }

   // ============================================================================================ layout + paint

   private int paint(GuiGraphics g, CompoundTag d, int x, int y0, int w) {
      SurvivalNetwork.State s = SurvivalNetwork.clientState();
      int mode = d.contains("mode") ? d.getByte("mode") : s.mode();
      boolean on = mode != 0;
      int y = y0;

      // ---------------------------------------------------------------- rules card
      String modeName = tr(L + "mode." + Mth.clamp(mode, 0, 3));
      y = this.card(g, x, y, w, tr(L + "rules", modeName), tr(L + "mode." + Mth.clamp(mode, 0, 3) + ".desc"),
         on ? tr(L + "drain", d.contains("drain") ? d.getShort("drain") : 100) : "", on);
      y += 6;

      if (on && !s.has(SurvivalNetwork.State.F_EXEMPT)) {
         // ---------------------------------------------------------------- body: nutrition
         boolean vigor = s.has(SurvivalNetwork.State.F_VIGOR);
         y = this.section(g, x, y, w, tr(L + "body"), vigor ? tr(L + "vigor") : "", vigor ? JournalUi.GOLD_DARK : JournalUi.MUTED);
         float[] v = {s.protein(), s.fat(), s.energy()};
         for (int i = 0; i < 3; i++) {
            y = this.meter(g, x, y, w, i, v[i]);
         }
         String[] hints = {tr(L + "vigor.desc"), tr(L + "hungry.desc"), tr(L + "fed.desc")};
         int hint = vigor ? 0 : Math.min(v[0], Math.min(v[1], v[2])) <= SurvivalMath.LOW ? 1 : 2;
         y = this.para(g, x, y, w, hints[hint], hint == 0 ? JournalUi.GOLD_DARK : hint == 1 ? JournalUi.RED : JournalUi.MUTED, 6, hints);
         y += 4;

         // ---------------------------------------------------------------- body temperature
         if (s.has(SurvivalNetwork.State.F_TEMPERATURE)) {
            y = this.temperature(g, x, y, w, s);
         } else {
            y = this.section(g, x, y, w, tr(L + "temperature"), "", JournalUi.MUTED);
            y = this.para(g, x, y, w, tr(L + "temperature.off"), JournalUi.MUTED, 4);
         }
         y += 4;
      } else if (on) {
         y = this.para(g, x, y, w, tr(L + "exempt"), JournalUi.MUTED, 6);
      }

      // ---------------------------------------------------------------- seasons
      if (d.contains("phase")) {
         y = this.season(g, x, y, w, d);
         y += 4;
      }

      // ---------------------------------------------------------------- lifetime record
      if (d.contains("meals")) {
         y = this.section(g, x, y, w, tr(L + "record"), "", JournalUi.MUTED);
         int meals = d.getInt("meals"), game = d.getInt("game");
         List<String[]> rows = new ArrayList<>();
         rows.add(new String[]{tr(L + "stat.meals"), num(meals)});
         rows.add(new String[]{tr(L + "stat.game"), meals == 0 ? "—" : num(game) + " · " + Math.round(100F * game / meals) + "%"});
         rows.add(new String[]{tr(L + "stat.fish"), num(d.getInt("fish"))});
         rows.add(new String[]{tr(L + "stat.farm"), num(d.getInt("farm"))});
         rows.add(new String[]{tr(L + "stat.preserved"), num(d.getInt("preserved"))});
         rows.add(new String[]{tr(L + "stat.spoiled"), num(d.getInt("spoiledEaten")) + " / " + num(d.getInt("spoiledLost"))});
         rows.add(new String[]{tr(L + "stat.winter_nights"), num(d.getInt("winterNights"))});
         rows.add(new String[]{tr(L + "stat.cold_nights"), num(d.getInt("coldNights"))});
         float coldest = d.getFloat("coldest");
         rows.add(new String[]{tr(L + "stat.coldest"), coldest >= -0.5F ? "—" : core(SurvivalMath.coreTemperature(coldest))});
         rows.add(new String[]{tr(L + "stat.frozen"), num(d.getInt("frozen"))});
         rows.add(new String[]{tr(L + "stat.vigor_time"), duration(d.getLong("vigorS"))});
         rows.add(new String[]{tr(L + "stat.hungry_time"), duration(d.getLong("hungryS"))});
         y = this.grid(g, x, y, w, rows);
         y += 4;
      }

      // ---------------------------------------------------------------- woodcraft perks
      y = this.section(g, x, y, w, tr(L + "perks"), "", JournalUi.MUTED);
      int mask = d.contains("perks") ? d.getInt("perks") : HunterSkills.clientMask;
      float strength = HunterSkills.clientStrength;
      y = this.perk(g, x, y, w, Perk.TRAIL_LEGS, mask, tr(L + "perk.trail_legs", pct(0.15F * strength)));
      y = this.perk(g, x, y, w, Perk.PROVIDER, mask, tr(L + "perk.provider", pct(d.contains("gameMul") ? d.getFloat("gameMul") - 1F : 0.25F * strength)));
      y = this.perk(g, x, y, w, Perk.THICK_SKIN, mask, tr(L + "perk.thick_skin", pct(d.contains("coldMul") && d.getFloat("coldMul") < 1F
         ? 1F - d.getFloat("coldMul") : 0.30F * strength)));
      return y - y0 + 4;
   }

   // ============================================================================================ pieces

   private int card(GuiGraphics g, int x, int y, int w, String title, String desc, String right, boolean on) {
      FormattedCharSequence t = this.fit(title, w - 90, FrontierUi.Size.STRONG);
      FormattedCharSequence r = this.seq(right, FrontierUi.Size.SMALL);
      List<FormattedCharSequence> lines = this.wrap(desc, w - 30, FrontierUi.Size.SMALL);
      int h = 18 + lines.size() * 9 + 4;
      if (g != null) {
         FrontierUi.rect(g, x - 2, y, w + 4, h, 3.0F, 0x14000000);
         FrontierUi.rect(g, x - 2, y, 2, h, 1.0F, on ? 0xFF7E5A33 : 0xFF8C8A78);
         RenderSystem.enableBlend();
         SurvivalHud.icon(g, x + 5, y + 5, on ? 4 : 3);
         RenderSystem.disableBlend();
         JournalUi.draw(g, t, x + 20, y + 5, JournalUi.INK);
         if (!right.isEmpty()) {
            int pw = JournalUi.width(r) + 10;
            FrontierUi.rect(g, x + w - pw, y + 3, pw, 11, 5.5F, 0xFFD6CEB6);
            JournalUi.draw(g, r, x + w - pw + 5, y + 5, JournalUi.INK_BROWN);
         }
         int yy = y + 18;
         for (FormattedCharSequence l : lines) {
            JournalUi.draw(g, l, x + 20, yy, JournalUi.MUTED);
            yy += 9;
         }
      }
      return y + h;
   }

   private int section(GuiGraphics g, int x, int y, int w, String text, String right, int rightColor) {
      if (g != null) {
         FormattedCharSequence s = this.seq(text.toUpperCase(Locale.ROOT), FrontierUi.Size.STRONG);
         JournalUi.draw(g, s, x, y + 6, JournalUi.INK);
         g.fill(x, y + 17, x + Math.min(36, w), y + 18, 0xFFB89B6E);
         if (!right.isEmpty()) {
            JournalUi.drawRight(g, this.seq(right, FrontierUi.Size.SMALL), x + w, y + 7, rightColor);
         }
      }
      return y + 22;
   }

   /** Wrapped small text; with {@code reserve}, the height is that of the longest alternative (live text keeps one size). */
   private int para(GuiGraphics g, int x, int y, int w, String text, int color, int indent, String... reserve) {
      List<FormattedCharSequence> lines = this.wrap(text, w - indent, FrontierUi.Size.SMALL);
      int n = lines.size();
      for (String r : reserve) {
         n = Math.max(n, this.wrap(r, w - indent, FrontierUi.Size.SMALL).size());
      }
      if (g != null) {
         int yy = y + 1;
         for (FormattedCharSequence l : lines) {
            JournalUi.draw(g, l, x + indent, yy, color);
            yy += 9;
         }
      }
      return y + n * 9 + 3;
   }

   /** One nutrition meter: HUD icon, name, state word, value, a bar with the LOW mark. */
   private int meter(GuiGraphics g, int x, int y, int w, int i, float v) {
      if (g != null) {
         boolean empty = v <= SurvivalMath.EMPTY, low = v <= SurvivalMath.LOW;
         RenderSystem.enableBlend();
         SurvivalHud.icon(g, x + 2, y + 2, i);
         RenderSystem.disableBlend();
         JournalUi.draw(g, this.seq(tr(L + "meter." + i), FrontierUi.Size.STRONG), x + 16, y + 3, JournalUi.INK);
         String state = tr(L + (empty ? "state.empty" : low ? "state.low" : v >= 80F ? "state.full" : "state.good"));
         FormattedCharSequence val = this.seq(Math.round(v) + "", FrontierUi.Size.STRONG);
         int vw = JournalUi.width(val);
         JournalUi.drawRight(g, val, x + w - 2, y + 3, empty ? JournalUi.RED : JournalUi.INK);
         JournalUi.drawRight(g, this.seq(state, FrontierUi.Size.SMALL), x + w - vw - 8, y + 4, empty || low ? JournalUi.RED : JournalUi.MUTED);
         float bx = x + 16, bw = w - 18;
         JournalUi.bar(g, bx, y + 14, bw, 4.0F, v / 100F, JournalUi.TRACK, empty ? JournalUi.RED : low ? 0xFFD06A28 : METER[i]);
         int lx = Math.round(bx + bw * SurvivalMath.LOW / 100F);
         g.fill(lx, y + 13, lx + 1, y + 19, 0x60243A32);
      }
      return y + 22;
   }

   private int temperature(GuiGraphics g, int x, int y, int w, SurvivalNetwork.State s) {
      float heat = s.heat();
      String state = heatState(heat);
      int stateColor = heat <= SurvivalMath.SHIVER ? 0xFF2F5E9E : heat >= SurvivalMath.HOT ? JournalUi.RED : heat <= SurvivalMath.CHILLY ? 0xFF4A78A8 : JournalUi.GREEN;
      y = this.section(g, x, y, w, tr(L + "temperature"), tr(L + "heat." + state), stateColor);
      int gy = y + 2, gh = 7;
      if (g != null) {
         // gauge: blue -> comfort -> red, threshold ticks, needle
         int segs = Math.max(13, w / 6);
         FrontierUi.rect(g, x - 1, gy - 1, w + 2, gh + 2, 2.0F, 0xFF8C7A5C);
         for (int i = 0; i < segs; i++) {
            int x0 = x + i * w / segs, x1 = x + (i + 1) * w / segs;
            g.fill(x0, gy, x1, gy + gh, SurvivalHud.gauge(i / (float)(segs - 1)));
         }
         for (float mark : new float[]{SurvivalMath.HYPOTHERMIA, SurvivalMath.SHIVER, SurvivalMath.CHILLY, SurvivalMath.HOT}) {
            int mx = x + Math.round((mark + 100F) / 200F * w);
            g.fill(mx, gy + gh - 3, mx + 1, gy + gh, 0x90000000);
         }
         int nx = x + Math.round(Mth.clamp((heat + 100F) / 200F, 0F, 1F) * (w - 1));
         g.fill(nx - 1, gy - 3, nx + 2, gy + gh + 3, 0xFF201810);
         g.fill(nx, gy - 2, nx + 1, gy + gh + 2, 0xFFF4EEDC);
         // scale words under the ends
         JournalUi.draw(g, this.seq(tr(L + "scale.cold"), FrontierUi.Size.SMALL), x, gy + gh + 3, 0xFF4A78A8);
         FormattedCharSequence mid = this.seq(tr(L + "scale.comfort"), FrontierUi.Size.SMALL);
         JournalUi.draw(g, mid, x + (w - JournalUi.width(mid)) / 2, gy + gh + 3, JournalUi.MUTED);
         JournalUi.drawRight(g, this.seq(tr(L + "scale.hot"), FrontierUi.Size.SMALL), x + w, gy + gh + 3, 0xFFB0502A);
      }
      y = gy + gh + 14;
      // numbers: core, felt, comfort floor, clothing warmth
      float lo = SurvivalMath.comfortLow(s.insulation());
      List<String[]> rows = new ArrayList<>();
      rows.add(new String[]{tr(L + "core"), core(SurvivalMath.coreTemperature(heat))});
      rows.add(new String[]{tr(L + "felt"), SurvivalText.temperature(s.felt(), false) + trend(s.trend())});
      rows.add(new String[]{tr(L + "comfort"), SurvivalText.temperature(lo, false)});
      rows.add(new String[]{tr(L + "warmth"), String.format(Locale.ROOT, "%.1f", s.insulation())});
      y = this.grid(g, x, y, w, rows);
      // conditions: shelter, fire, wet, mittens
      List<int[]> chips = new ArrayList<>();
      List<String> chipText = new ArrayList<>();
      // [shelter] say what kind of shelter: a tent, indoors (closed room, cave), or just under a roof
      String shelterChip = s.has(SurvivalNetwork.State.F_TENT) ? tr(L + "chip.tent")
         : s.has(SurvivalNetwork.State.F_INDOORS) ? tr(L + "chip.indoors") : tr(L + "chip.shelter");
      if (s.has(SurvivalNetwork.State.F_SHELTER) || s.has(SurvivalNetwork.State.F_TENT) || s.has(SurvivalNetwork.State.F_INDOORS)) {
         chips.add(new int[]{9});
         chipText.add(shelterChip);
      }
      if (s.has(SurvivalNetwork.State.F_FIRE)) {
         chips.add(new int[]{8});
         chipText.add(tr(L + "chip.fire"));
      }
      if (s.wet() >= 0.1F) {
         chips.add(new int[]{7});
         chipText.add(tr(L + "chip.wet", Math.round(s.wet() * 100F)));
      }
      if (s.has(SurvivalNetwork.State.F_MITTENS)) {
         chips.add(new int[]{-1});
         chipText.add(tr(L + "chip.mittens"));
      }
      // reserve the rows all four chips would need, so the page keeps its height as conditions change
      String longest = tr(L + "chip.shelter"); // [shelter] reserve room for the longest shelter wording
      for (String alt : new String[]{tr(L + "chip.tent"), tr(L + "chip.indoors")}) {
         if (alt.length() > longest.length()) {
            longest = alt;
         }
      }
      int rowsAll = chipRows(x, w, new String[]{longest, tr(L + "chip.fire"), tr(L + "chip.wet", 100), tr(L + "chip.mittens")},
         new boolean[]{true, true, true, false});
      {
         int cx = x;
         int cy = y;
         for (int i = 0; i < chips.size(); i++) {
            FormattedCharSequence t = this.seq(chipText.get(i), FrontierUi.Size.SMALL);
            int cw = JournalUi.width(t) + (chips.get(i)[0] >= 0 ? 22 : 10);
            if (cx + cw > x + w && cx > x) {
               cx = x;
               cy += 15;
            }
            if (g != null) {
               FrontierUi.rect(g, cx, cy, cw, 13, 6.5F, 0xFFDCD3BC);
               int tx = cx + 5;
               if (chips.get(i)[0] >= 0) {
                  RenderSystem.enableBlend();
                  SurvivalHud.icon(g, cx + 4, cy + 2, chips.get(i)[0]);
                  RenderSystem.disableBlend();
                  tx += 12;
               }
               JournalUi.draw(g, t, tx, cy + 3, JournalUi.INK_BROWN);
            }
            cx += cw + 4;
         }
         y += rowsAll * 15 + 2;
      }
      String[] all = new String[STATES.length];
      for (int i = 0; i < STATES.length; i++) {
         all[i] = tr(L + "heat." + STATES[i] + ".desc");
      }
      return this.para(g, x, y, w, tr(L + "heat." + state + ".desc"), JournalUi.MUTED, 0, all);
   }

   private static final String[] STATES = {"freezing", "hypothermia", "shivering", "chilly", "comfortable", "hot", "heatstroke"};

   private int chipRows(int x, int w, String[] texts, boolean[] icon) {
      int cx = x, rows = 1;
      for (int i = 0; i < texts.length; i++) {
         int cw = JournalUi.width(this.seq(texts[i], FrontierUi.Size.SMALL)) + (icon[i] ? 22 : 10);
         if (cx + cw > x + w && cx > x) {
            cx = x;
            rows++;
         }
         cx += cw + 4;
      }
      return rows;
   }

   private int season(GuiGraphics g, int x, int y, int w, CompoundTag d) {
      int phase = Mth.clamp(d.getByte("phase"), 0, 4);
      boolean lean = !d.contains("lean") || d.getBoolean("lean");
      y = this.section(g, x, y, w, tr(L + "season"), tr(L + "phase." + phase), JournalUi.INK_BROWN);
      y = this.para(g, x, y, w, tr("survival.frontierhunts.season." + phase), JournalUi.INK, 0);
      if (!lean) {
         return this.para(g, x, y + 2, w, tr(L + "lean.off"), JournalUi.MUTED, 0);
      }
      // the year of game condition: 12 columns, this month in gold with a marker
      int ch = 34, top = y + 4;
      float yp = d.getFloat("yearPos");
      int now = Mth.clamp((int)Math.floor(((yp % 12F) + 12F) % 12F), 0, 11);
      if (g != null) {
         float colW = w / 12F;
         g.fill(x, top + ch, x + w, top + ch + 1, 0x40243A32);
         // the 1.0 line ("average condition")
         int avgY = top + ch - Math.round((1.0F - 0.70F) / 0.55F * ch);
         for (int px = x; px < x + w; px += 4) {
            g.fill(px, avgY, px + 2, avgY + 1, 0x30243A32);
         }
         for (int m = 0; m < 12; m++) {
            float c = CONDITION[m];
            int bh = Math.max(2, Math.round((c - 0.70F) / 0.55F * ch));
            float bx = x + m * colW + colW * 0.2F;
            float bw = colW * 0.6F;
            int col = m == now ? JournalUi.GOLD : c < 0.9F ? 0xFFB9A88A : c > 1.05F ? 0xFF6F8A5A : 0xFF9DA387;
            FrontierUi.rect(g, bx, top + ch - bh, bw, bh, 1.5F, col);
            FormattedCharSequence ml = this.seq(MONTHS[m], FrontierUi.Size.SMALL);
            JournalUi.draw(g, ml, Math.round(x + m * colW + (colW - JournalUi.width(ml)) / 2F), top + ch + 3, m == now ? JournalUi.GOLD_DARK : JournalUi.FAINT);
         }
         float mx = x + now * colW + colW / 2F;
         FrontierUi.circle(g, mx, top - 1, 2.0F, JournalUi.GOLD_DARK);
      }
      y = top + ch + 14;
      float cond = d.getFloat("cond"), meat = d.getFloat("meat"), denial = d.getFloat("denial");
      List<String[]> rows = new ArrayList<>();
      rows.add(new String[]{tr(L + "condition"), String.format(Locale.ROOT, "×%.2f", cond)});
      rows.add(new String[]{tr(L + "meat"), (meat >= 1F ? "+" : "−") + Math.round(Math.abs(meat - 1F) * 100F) + "%"});
      rows.add(new String[]{tr(L + "deer_fat"), num(d.getByte("deerFat"))});
      rows.add(new String[]{tr(L + "denial"), Math.round(denial * 100F) + "%"});
      return this.grid(g, x, y, w, rows);
   }

   private int perk(GuiGraphics g, int x, int y, int w, Perk p, int mask, String effect) {
      boolean active = p.in(mask);
      FormattedCharSequence name = this.fit(tr(p.lang("name")), w - 80, FrontierUi.Size.STRONG);
      FormattedCharSequence pill = this.seq(tr(L + "perk.at", p.level), FrontierUi.Size.SMALL);
      List<FormattedCharSequence> desc = this.wrap(effect, w - 30, FrontierUi.Size.SMALL);
      int h = 14 + desc.size() * 9 + 4;
      if (g != null) {
         float cx = x + 12, cy = y + 7;
         if (active) {
            FrontierUi.circle(g, cx, cy, 5.5F, JournalUi.GOLD);
            JournalUi.tick(g, cx, cy, JournalUi.PAPER);
         } else {
            FrontierUi.circle(g, cx, cy, 5.5F, 0xFFCFC7AF);
            JournalUi.lock(g, cx, cy + 1.5F, 0xFF8C8A78, 0xFFCFC7AF);
         }
         JournalUi.draw(g, name, x + 24, y + 3, active ? JournalUi.INK : JournalUi.MUTED);
         int pw = JournalUi.width(pill) + 10;
         FrontierUi.rect(g, x + w - pw, y + 1, pw, 11, 5.5F, active ? JournalUi.alpha(0xFF6F8A5A, 0.9F) : 0xFFD6CEB6);
         JournalUi.draw(g, pill, x + w - pw + 5, y + 3, active ? JournalUi.PAPER : JournalUi.MUTED);
         int yy = y + 14;
         for (FormattedCharSequence line : desc) {
            JournalUi.draw(g, line, x + 24, yy, active ? JournalUi.INK_BROWN : JournalUi.FAINT);
            yy += 9;
         }
      }
      return y + h;
   }

   /** Label/value pairs in one or two columns, like the journal's Records page. */
   private int grid(GuiGraphics g, int x, int y, int w, List<String[]> pairs) {
      boolean two = w >= 300;
      int colW = two ? (w - 16) / 2 : w;
      for (int i = 0; i < pairs.size(); i += two ? 2 : 1) {
         if (g != null) {
            for (int c = 0; c < (two ? 2 : 1) && i + c < pairs.size(); c++) {
               int cx = x + c * (colW + 16);
               FormattedCharSequence l = this.fit(pairs.get(i + c)[0], colW - 60, FrontierUi.Size.SMALL);
               FormattedCharSequence v = this.fit(pairs.get(i + c)[1], Math.max(20, colW - JournalUi.width(l) - 6), FrontierUi.Size.STRONG);
               JournalUi.draw(g, l, cx, y + 4, JournalUi.MUTED);
               JournalUi.drawRight(g, v, cx + colW, y + 3, JournalUi.INK);
               g.fill(cx, y + 14, cx + colW, y + 15, 0x22243A32);
            }
         }
         y += 15;
      }
      return y + 4;
   }

   // ============================================================================================ text helpers

   private List<FormattedCharSequence> wrap(String text, int width, FrontierUi.Size size) {
      return this.wrapped.computeIfAbsent(size + "|" + width + "|" + text, k -> JournalUi.wrap(text, width, size));
   }

   private FormattedCharSequence seq(String text, FrontierUi.Size size) {
      return this.seqs.computeIfAbsent(size + "|" + text, k -> JournalUi.seq(text, size));
   }

   private FormattedCharSequence fit(String text, int width, FrontierUi.Size size) {
      return this.seqs.computeIfAbsent(size + "|" + width + "|fit|" + text, k -> JournalUi.fit(text, width, size));
   }

   private static String tr(String key, Object... args) {
      return JournalUi.tr(key, args);
   }

   private static String num(long v) {
      return String.format(Locale.ROOT, "%,d", v);
   }

   private static String pct(float f) {
      return Math.round(Math.max(0F, f) * 100F) + "%";
   }

   private static String heatState(float heat) {
      if (heat <= -88F) {
         return "freezing";
      }
      if (heat <= SurvivalMath.HYPOTHERMIA) {
         return "hypothermia";
      }
      if (heat <= SurvivalMath.SHIVER) {
         return "shivering";
      }
      if (heat <= SurvivalMath.CHILLY) {
         return "chilly";
      }
      if (heat >= SurvivalMath.HEATSTROKE) {
         return "heatstroke";
      }
      if (heat >= SurvivalMath.HOT) {
         return "hot";
      }
      return "comfortable";
   }

   private static String trend(float t) {
      return t <= -0.25F ? "  ▼" : t >= 0.25F ? "  ▲" : "";
   }

   /** Core temperature with one decimal in the player's units. */
   private static String core(float c) {
      boolean f = SurvivalConfig.units() == SurvivalConfig.Units.FAHRENHEIT;
      return String.format(Locale.ROOT, f ? "%.1f°F" : "%.1f°C", f ? c * 9F / 5F + 32F : c);
   }

   /** Real time from seconds: "2 h 05 m", "12 m", "—". */
   private static String duration(long s) {
      if (s < 60L) {
         return s <= 0L ? "—" : "<1 m";
      }
      long m = s / 60L;
      return m >= 60L ? String.format(Locale.ROOT, "%d h %02d m", m / 60L, m % 60L) : m + " m";
   }
}
