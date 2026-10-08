package com.formaworks.frontierhunts.licence.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.journal.client.JournalPageView;
import com.formaworks.frontierhunts.journal.client.JournalUi;
import com.formaworks.frontierhunts.licence.FilledTag;
import com.formaworks.frontierhunts.licence.LicenceJournal;
import com.formaworks.frontierhunts.licence.LicenceNetwork;
import com.formaworks.frontierhunts.licence.LicenceOffice;
import com.formaworks.frontierhunts.licence.Regulations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * [licence] The Hunter's Journal "Licence & Tags" page: the season's rules, the licence counter (buy with tokens or
 * emeralds, claim the free hunter-education licence, replace a lost one), the tags and stamps with their bag limits
 * and open seasons, the warden record and the filled-tag log. One layout routine measures ({@code g == null}), draws
 * and hit-tests, so height, drawing and clicks always agree. Every button only sends a request; the server decides.
 */
public final class LicencePageView implements JournalPageView {
   private static final String L = "licence.frontierhunts.page.";
   private static final int BTN_H = 14;
   private final Map<String, List<FormattedCharSequence>> wraps = new HashMap<>();

   /** a button hit-tested during a click pass */
   private record Btn(int x, int y, int w, int what, int index, int pay) {
   }

   private final List<Btn> buttons = new ArrayList<>();
   private boolean collect;

   @EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void setup(FMLClientSetupEvent e) {
         JournalPageView.Views.register(LicenceJournal.PAGE, new LicencePageView());
      }
   }

   @Override
   public int height(CompoundTag data, int width) {
      this.wraps.clear();
      return this.paint(null, data, 0, 0, width, -1, -1);
   }

   @Override
   public void render(GuiGraphics g, CompoundTag data, int x, int y, int width, int mouseX, int mouseY) {
      this.paint(g, data, x, y, width, mouseX, mouseY);
   }

   @Override
   public boolean click(CompoundTag data, int x, int y, int width, double mouseX, double mouseY) {
      this.buttons.clear();
      this.collect = true;
      try {
         this.paint(null, data, x, y, width, (int)mouseX, (int)mouseY);
      } finally {
         this.collect = false;
      }
      for (Btn b : this.buttons) {
         if (mouseX >= b.x && mouseX < b.x + b.w && mouseY >= b.y && mouseY < b.y + BTN_H) {
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.7F));
            if (mc.getConnection() != null && mc.getConnection().hasChannel(LicenceNetwork.Buy.TYPE)) {
               PacketDistributor.sendToServer(new LicenceNetwork.Buy(b.what, b.index, b.pay));
            }
            return true;
         }
      }
      return false;
   }

   // ============================================================================================ layout

   private int paint(GuiGraphics g, CompoundTag d, int x, int y0, int w, int mx, int my) {
      int y = y0;
      int mode = d.contains("mode") ? d.getByte("mode") : 1;
      boolean on = mode != 0;
      int period = d.getInt("period");
      String modeName = tr(L + "mode." + Math.clamp(mode, 0, 2));
      String right = d.contains("period") ? tr(L + "period", Regulations.periodTitle(period), d.getInt("daysLeft")) : "";
      y = this.card(g, x, y, w, tr(L + "title", modeName), tr(L + "mode." + Math.clamp(mode, 0, 2) + ".desc"), right, on);
      y += 4;
      if (!d.contains("period")) {
         return y - y0 + 4;
      }
      if (on) {
         boolean vendor = d.getBoolean("vendor");
         String where = vendor ? tr(L + "at_counter") : tr(L + "find_counter");
         y = this.para(g, x, y, w, where, vendor ? JournalUi.GREEN : JournalUi.GOLD_DARK, 2);
         String wallet = tr(L + "wallet", d.getInt("tokens"), d.getInt("emeralds"));
         int disc = d.getInt("discount");
         if (disc > 0) {
            wallet += "  ·  " + tr(L + "discount", disc);
         }
         y = this.para(g, x, y, w, wallet, JournalUi.MUTED, 2);
         y = this.para(g, x, y, w, tr(L + "why_tokens"), JournalUi.MUTED, 4);
         ListTag offers = d.getList("o", Tag.TAG_COMPOUND);

         // ---------------------------------------------------------------- licence
         y = this.section(g, x, y, w, tr(L + "licence"), d.getBoolean("susp") ? tr(L + "suspended") : "", JournalUi.RED);
         String status = d.getBoolean("lic") ? (d.getBoolean("licHeld") ? tr(L + "lic.carried") : tr(L + "lic.lost")) : tr(L + "lic.none");
         String sub = d.getBoolean("edu") ? tr(L + "lic.edu") : tr(L + "lic.what");
         y = this.offer(g, x, y, w, "frontierhunts:hunting_licence", tr("item.frontierhunts.hunting_licence"), status, sub, offers, 0,
            price(d, Regulations.LICENCE_TOKENS), Regulations.LICENCE_EMERALDS, d, mx, my, d.getBoolean("lic") && d.getBoolean("licHeld") ? JournalUi.GREEN : JournalUi.INK_BROWN);

         // ---------------------------------------------------------------- tags
         y += 2;
         y = this.section(g, x, y, w, tr(L + "tags"), tr(L + "tags.right"), JournalUi.MUTED);
         int[] bought = d.getIntArray("bought");
         int[] held = d.getIntArray("held");
         int[] bags = d.getIntArray("bags");
         int month = d.getInt("month");
         for (Regulations.TagKind k : Regulations.TagKind.values()) {
            int i = k.ordinal();
            int b = i < bought.length ? bought[i] : 0;
            int h = i < held.length ? held[i] : 0;
            boolean openNow = (Regulations.months(k) >> month & 1) != 0;
            boolean beta = k.ordinal() > Regulations.TagKind.MOOSE.ordinal();
            String st = beta ? tr(L + "tag.beta") : tr(L + "tag.status", b, i < bags.length ? bags[i] : k.bag, h) + "  ·  " + (openNow ? tr(L + "open_now") : tr(L + "closed_now"));
            String covers = names(Regulations.covered(k)) + " · " + Regulations.months(Regulations.months(k));
            y = this.offer(g, x, y, w, "frontierhunts:" + k.item, tr("item.frontierhunts." + k.item), st, covers, offers, 1 + i, price(d, k.tokens), k.emeralds, d,
               mx, my, h > 0 ? JournalUi.GREEN : JournalUi.INK_BROWN);
         }

         // ---------------------------------------------------------------- stamps
         y += 2;
         y = this.section(g, x, y, w, tr(L + "stamps"), "", JournalUi.MUTED);
         int stamps = d.getInt("stamps"), stampHeld = d.getInt("stampHeld");
         CompoundTag birds = d.getCompound("birds");
         for (Regulations.Stamp s : Regulations.Stamp.values()) {
            int i = s.ordinal();
            boolean have = (stamps >> i & 1) != 0, carried = (stampHeld >> i & 1) != 0;
            List<String> today = new ArrayList<>();
            for (Regulations.Rule r : Regulations.covered(s)) {
               today.add(tr(L + "stamp.today", birds.getInt(r.species()), s.daily, r.title()));
            }
            String st = (have ? (carried ? tr(L + "stamp.carried") : tr(L + "lic.lost")) : tr(L + "stamp.none")) + "  ·  " + String.join(", ", today);
            String covers = names(Regulations.covered(s)) + " · " + Regulations.months(Regulations.months(s));
            y = this.offer(g, x, y, w, "frontierhunts:" + s.item, tr("item.frontierhunts." + s.item), st, covers, offers,
               1 + Regulations.TagKind.values().length + i, price(d, s.tokens), s.emeralds, d, mx, my, carried ? JournalUi.GREEN : JournalUi.INK_BROWN);
         }

         // ---------------------------------------------------------------- [1.1.6] ranger services
         y += 2;
         y = this.section(g, x, y, w, tr(L + "services"), tr(L + "services.right"), JournalUi.MUTED);
         y = this.offer(g, x, y, w, "minecraft:map", tr(L + "report"), tr(L + "report.status"), tr(L + "report.what"), offers,
            LicenceJournal.OFFER_REPORT, price(d, com.formaworks.frontierhunts.licence.GameReport.TOKENS),
            com.formaworks.frontierhunts.licence.GameReport.EMERALDS, d, mx, my, JournalUi.INK_BROWN);

         // ---------------------------------------------------------------- open seasons this month
         y += 2;
         y = this.section(g, x, y, w, tr(L + "seasons"), Regulations.monthName(month), JournalUi.MUTED);
         List<String> open = new ArrayList<>(), closed = new ArrayList<>();
         for (Regulations.Rule r : Regulations.all()) {
            if (r.regulated()) {
               (r.open(month) ? open : closed).add(r.title());
            }
         }
         y = this.para(g, x, y, w, tr(L + "seasons.open", open.isEmpty() ? "-" : String.join(", ", open)), JournalUi.GREEN, 2);
         y = this.para(g, x, y, w, tr(L + "seasons.closed", closed.isEmpty() ? "-" : String.join(", ", closed)), JournalUi.MUTED, 2);
         y = this.para(g, x, y, w, tr(L + "seasons.varmints"), JournalUi.MUTED, 4);

         // ---------------------------------------------------------------- warden record
         y += 2;
         String standing = d.getBoolean("susp") ? tr(L + "standing.suspended") : d.getInt("strikes") > 0 ? tr(L + "standing.strikes")
            : d.getBoolean("warned") ? tr(L + "standing.warned") : tr(L + "standing.good");
         int stColor = d.getBoolean("susp") ? JournalUi.RED : d.getInt("viol") > 0 ? JournalUi.GOLD_DARK : JournalUi.GREEN;
         y = this.section(g, x, y, w, tr(L + "warden"), standing, stColor);
         y = this.para(g, x, y, w, tr(L + "warden.season", d.getInt("filled"), d.getInt("viol"), d.getInt("strikes"), d.getInt("fines")), JournalUi.INK, 2);
         y = this.para(g, x, y, w, tr(L + "warden.total", d.getInt("filledAll"), d.getInt("violAll"), d.getInt("finesAll")), JournalUi.MUTED, 2);
         // [gear21] the warden record and its ladder
         int left = d.getInt("revokedLeft");
         y = this.para(g, x, y, w, tr(L + "warden.record", d.getInt("record"), d.getInt("charges"),
            left > 0 ? " · licence revoked for " + left + " more licence season" + (left == 1 ? "" : "s") : ""), left > 0 ? JournalUi.RED : JournalUi.INK, 2);
         y = this.para(g, x, y, w, tr(L + "warden.rules.ladder"), JournalUi.MUTED, 2);
         y = this.para(g, x, y, w, tr(L + "warden.rules." + (mode == 2 ? "strict" : "relaxed")), JournalUi.MUTED, 4);
      }

      // ---------------------------------------------------------------- filled tags
      y += 2;
      ListTag log = d.getList("log", Tag.TAG_COMPOUND);
      y = this.section(g, x, y, w, tr(L + "log"), log.isEmpty() ? "" : Integer.toString(log.size()), JournalUi.MUTED);
      if (log.isEmpty()) {
         y = this.para(g, x, y, w, tr(L + "log.empty"), JournalUi.MUTED, 4);
      }
      for (Tag e : log) {
         y = this.logRow(g, x, y, w, FilledTag.load((CompoundTag)e));
      }
      return y - y0 + 4;
   }

   // ============================================================================================ pieces

   private int offer(GuiGraphics g, int x, int y, int w, String icon, String name, String status, String sub, ListTag offers, int o, int tokens,
      int emeralds, CompoundTag d, int mx, int my, int statusColor) {
      CompoundTag c = o < offers.size() ? offers.getCompound(o) : new CompoundTag();
      String rt = c.getString("t"), re = c.getString("e"), rf = c.getString("f");
      boolean emerOk = d.getBoolean("emer");
      // buttons, right to left
      List<String[]> btns = new ArrayList<>(); // label, pay, reason
      if (rf.isEmpty() || "vendor".equals(rf)) {
         boolean lic = o == 0;
         String lb = lic && d.getBoolean("lic") || !lic ? tr(L + "btn.replace") : tr(L + "btn.claim");
         btns.add(new String[]{lb, Integer.toString(LicenceOffice.PAY_FREE), rf});
      } else if (!"have".equals(rt)) {
         if (emerOk) {
            btns.add(new String[]{tr(L + "btn.emeralds", emeralds), Integer.toString(LicenceOffice.PAY_EMERALDS), re});
         }
         btns.add(new String[]{tr(L + "btn.tokens", tokens), Integer.toString(LicenceOffice.PAY_TOKENS), rt});
      }
      int bx = x + w;
      int[] bxs = new int[btns.size()];
      int[] bws = new int[btns.size()];
      for (int i = 0; i < btns.size(); i++) {
         int bw = Math.max(34, JournalUi.font().width(btns.get(i)[0]) + 10);
         bx -= bw;
         bxs[i] = bx;
         bws[i] = bw;
         bx -= 3;
      }
      int textW = Math.max(40, bx - x - 22);
      List<FormattedCharSequence> subs = this.wrap(sub, textW, FrontierUi.Size.SMALL);
      int h = Math.max(26, 21 + subs.size() * 8 + 3);
      // why nothing can be done here (a rule, not the payment or the distance to the counter, when there is one)
      String why = "";
      boolean freePath = rf.isEmpty() || "vendor".equals(rf);
      if (!freePath && !"have".equals(rt) && !rt.isEmpty() && (!emerOk || !re.isEmpty())) {
         why = !"tokens".equals(rt) ? rt : emerOk && !"emeralds".equals(re) ? re : rt;
      }
      boolean showWhy = !why.isEmpty() && !"vendor".equals(why);
      if (showWhy) {
         h += 8;
      }
      if (this.collect) {
         for (int i = 0; i < btns.size(); i++) {
            if (btns.get(i)[2].isEmpty()) {
               this.buttons.add(new Btn(bxs[i], y + 3, bws[i], LicenceJournal.what(o), LicenceJournal.index(o), Integer.parseInt(btns.get(i)[1])));
            }
         }
      }
      if (g != null) {
         FrontierUi.rect(g, x - 2, y + 1, w + 4, h - 2, 3.0F, 0x0E000000);
         ItemStack st = JournalUi.icon(icon);
         if (!st.isEmpty()) {
            g.renderItem(st, x + 1, y + 4);
         }
         JournalUi.draw(g, JournalUi.fit(name, textW, FrontierUi.Size.STRONG), x + 21, y + 3, JournalUi.INK);
         JournalUi.draw(g, JournalUi.fit(status, textW, FrontierUi.Size.SMALL), x + 21, y + 13, statusColor);
         int yy = y + 21;
         for (FormattedCharSequence l : subs) {
            JournalUi.draw(g, l, x + 21, yy, JournalUi.MUTED);
            yy += 8;
         }
         if (showWhy) {
            JournalUi.draw(g, JournalUi.fit(tr("licence.frontierhunts.why." + why), w - 24, FrontierUi.Size.SMALL), x + 21, yy, JournalUi.RED);
         }
         for (int i = 0; i < btns.size(); i++) {
            boolean active = btns.get(i)[2].isEmpty();
            boolean hover = active && mx >= bxs[i] && mx < bxs[i] + bws[i] && my >= y + 3 && my < y + 3 + BTN_H;
            JournalUi.button(g, bxs[i], y + 3, bws[i], BTN_H, JournalUi.seq(btns.get(i)[0], FrontierUi.Size.SMALL), hover, false, active);
         }
      }
      return y + h + 2;
   }

   private int logRow(GuiGraphics g, int x, int y, int w, FilledTag t) {
      int h = 22;
      if (g != null) {
         Regulations.TagKind k = Regulations.TagKind.byKey(t.kind());
         int col = k != null ? k.color : 0xFF8A8A7A;
         for (Regulations.Stamp s : Regulations.Stamp.values()) {
            if (s.key().equals(t.kind())) {
               col = s.color;
            }
         }
         // a small tag swatch with its punched hole
         FrontierUi.rect(g, x + 1, y + 4, 12, 14, 2.0F, 0xFF3A2E22);
         FrontierUi.rect(g, x + 2, y + 5, 10, 12, 2.0F, col);
         g.fill(x + 6, y + 7, x + 8, y + 9, 0xFF3A2E22);
         if (!t.legal()) {
            g.fill(x + 2, y + 10, x + 12, y + 12, JournalUi.RED);
         }
         String line = t.title() + " · " + t.weight() + (t.points() > 0 ? " · " + tr(L + "log.points", t.points()) : "");
         String status = t.legal() ? (k != null ? tr(L + "log.tagged") : tr(L + "log.stamp")) : tr(L + "log.violation");
         FormattedCharSequence stS = JournalUi.seq(status, FrontierUi.Size.SMALL);
         int sw = JournalUi.width(stS);
         JournalUi.draw(g, JournalUi.fit(line, w - sw - 28, FrontierUi.Size.BODY), x + 18, y + 3, JournalUi.INK);
         JournalUi.drawRight(g, stS, x + w, y + 4, t.legal() ? JournalUi.GREEN : JournalUi.RED);
         JournalUi.draw(g, JournalUi.fit(t.date() + "  ·  " + t.place(), w - 22, FrontierUi.Size.SMALL), x + 18, y + 13, JournalUi.MUTED);
         g.fill(x + 18, y + h - 1, x + w, y + h, 0x22000000);
      }
      return y + h;
   }

   private int card(GuiGraphics g, int x, int y, int w, String title, String desc, String right, boolean on) {
      FormattedCharSequence r = JournalUi.seq(right, FrontierUi.Size.SMALL);
      int rw = right.isEmpty() ? 0 : JournalUi.width(r) + 10;
      List<FormattedCharSequence> lines = this.wrap(desc, w - 26, FrontierUi.Size.SMALL);
      int h = 18 + lines.size() * 9 + 4;
      if (g != null) {
         FrontierUi.rect(g, x - 2, y, w + 4, h, 3.0F, 0x14000000);
         FrontierUi.rect(g, x - 2, y, 2, h, 1.0F, on ? 0xFF7E5A33 : 0xFF8C8A78);
         ItemStack st = JournalUi.icon("frontierhunts:hunting_licence");
         if (!st.isEmpty()) {
            g.renderItem(st, x + 2, y + 1);
         }
         JournalUi.draw(g, JournalUi.fit(title, w - rw - 28, FrontierUi.Size.STRONG), x + 21, y + 5, JournalUi.INK);
         if (rw > 0) {
            FrontierUi.rect(g, x + w - rw, y + 3, rw, 11, 5.5F, 0xFFD6CEB6);
            JournalUi.draw(g, r, x + w - rw + 5, y + 5, JournalUi.INK_BROWN);
         }
         int yy = y + 18;
         for (FormattedCharSequence l : lines) {
            JournalUi.draw(g, l, x + 21, yy, JournalUi.MUTED);
            yy += 9;
         }
      }
      return y + h;
   }

   private int section(GuiGraphics g, int x, int y, int w, String text, String right, int rightColor) {
      if (g != null) {
         JournalUi.draw(g, JournalUi.seq(text.toUpperCase(Locale.ROOT), FrontierUi.Size.STRONG), x, y + 6, JournalUi.INK);
         g.fill(x, y + 17, x + Math.min(36, w), y + 18, 0xFFB89B6E);
         if (!right.isEmpty()) {
            JournalUi.drawRight(g, JournalUi.seq(right, FrontierUi.Size.SMALL), x + w, y + 7, rightColor);
         }
      }
      return y + 22;
   }

   private int para(GuiGraphics g, int x, int y, int w, String text, int color, int after) {
      List<FormattedCharSequence> lines = this.wrap(text, w, FrontierUi.Size.SMALL);
      if (g != null) {
         int yy = y;
         for (FormattedCharSequence l : lines) {
            JournalUi.draw(g, l, x, yy, color);
            yy += 9;
         }
      }
      return y + lines.size() * 9 + after;
   }

   private List<FormattedCharSequence> wrap(String text, int w, FrontierUi.Size size) {
      return this.wraps.computeIfAbsent(w + "|" + size + "|" + text, k -> JournalUi.wrap(text, w, size));
   }

   private static String names(List<Regulations.Rule> rules) {
      List<String> t = new ArrayList<>();
      for (Regulations.Rule r : rules) {
         t.add(r.title());
      }
      return String.join(", ", t);
   }

   /** [1.1.6] the rank discount, as the server charges it (RankPerks.price) */
   private static int price(CompoundTag d, int tokens) {
      int disc = d.getInt("discount");
      if (disc >= 100) {
         return tokens <= 0 ? tokens : 0; // [1.2.0] Master of the Reserve
      }
      return tokens <= 0 || disc <= 0 ? tokens : Math.max(1, Math.round(tokens * (100 - disc) / 100.0F));
   }

   private static String tr(String key, Object... args) {
      return JournalUi.tr(key, args);
   }
}
