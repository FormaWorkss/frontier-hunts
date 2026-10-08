package com.formaworks.frontierhunts.journal.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.client.JournalScreen;
import com.formaworks.frontierhunts.journal.Checklist;
import com.formaworks.frontierhunts.journal.HunterRecord;
import com.formaworks.frontierhunts.journal.HunterSkills;
import com.formaworks.frontierhunts.journal.JournalConfig;
import com.formaworks.frontierhunts.journal.JournalNet;
import com.formaworks.frontierhunts.journal.Perk;
import com.formaworks.frontierhunts.journal.Rank;
import com.formaworks.frontierhunts.journal.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * [journal] Client glue: the owner's synced record (for the journal screen), toasts, the "+XP" ticker above the hotbar
 * and the local perk mask for the rifle sway hook.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class JournalClient {
   /** last journal data from the server (null until the journal was opened once this session) */
   public static JournalNet.Data data;
   /** bumped on every data change so screens know to lay out again */
   public static int version;

   private static final int TICKER = 4;
   private static final int[] tickSkill = new int[TICKER];
   private static final int[] tickAmount = new int[TICKER];
   private static final long[] tickTime = new long[TICKER];
   private static final FormattedCharSequence[] tickText = new FormattedCharSequence[TICKER];

   private JournalClient() {
   }

   @EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = Bus.MOD)
   public static final class Setup {
      @SubscribeEvent
      public static void setup(FMLClientSetupEvent e) {
         JournalNet.dataReceiver = JournalClient::onData;
         JournalNet.noticeReceiver = JournalClient::onNotice;
         JournalNet.perksReceiver = JournalClient::onPerks;
      }

      @SubscribeEvent
      public static void layers(RegisterGuiLayersEvent e) {
         e.registerAbove(VanillaGuiLayers.HOTBAR, FrontierHunts.id("journal_xp_ticker"), (g, dt) -> renderTicker(g));
      }
   }

   public static void ask(byte action, int arg) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.getConnection() != null && mc.getConnection().hasChannel(JournalNet.Ask.TYPE)) {
         PacketDistributor.sendToServer(new JournalNet.Ask(action, arg), new CustomPacketPayload[0]);
      }
   }

   private static void onData(JournalNet.Data d) {
      data = d;
      version++;
      if (Minecraft.getInstance().screen instanceof JournalScreen s) {
         s.dataChanged();
      }
   }

   private static void onPerks(JournalNet.Perks p) {
      HunterSkills.clientMask = p.mask();
      HunterSkills.clientStrength = p.strength();
   }

   @SubscribeEvent
   public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e) {
      data = null;
      version++;
      HunterSkills.clientMask = 0;
      HunterSkills.clientStrength = 1.0F;
      for (int i = 0; i < TICKER; i++) {
         tickTime[i] = 0L;
      }
   }

   // ============================================================================================ text

   /** Localized title of a checklist entry. */
   public static String checkTitle(Checklist.Entry e) {
      if (e.category() == Checklist.Category.HUNTS) {
         return JournalUi.tr(e.titleKey()); // [hunts] species hunt milestones have their own titles
      }
      if (e.species() != null) {
         return JournalUi.tr("journal.frontierhunts.check.species", JournalUi.tr("entity.frontierhunts." + e.species()));
      }
      return JournalUi.tr(e.titleKey());
   }

   public static String checkHint(Checklist.Entry e) {
      if (e.category() == Checklist.Category.HUNTS) {
         return JournalUi.has(e.hintKey()) ? JournalUi.tr(e.hintKey()) : ""; // [hunts]
      }
      if (e.species() != null) {
         String k = "journal.frontierhunts.check.sp_" + e.species() + ".hint";
         return JournalUi.has(k) ? JournalUi.tr(k) : JournalUi.tr("journal.frontierhunts.check.species.hint", JournalUi.tr("entity.frontierhunts." + e.species()));
      }
      return JournalUi.has(e.hintKey()) ? JournalUi.tr(e.hintKey()) : "";
   }

   /** Resolves a note text: literal, or "@lang.key|arg|arg" where args are "check:<id>", "key:<lang key>" or literals. */
   public static String noteText(String raw) {
      if (raw == null || !raw.startsWith("@")) {
         return raw == null ? "" : raw;
      }
      String[] parts = raw.substring(1).split("\\|");
      Object[] args = new Object[parts.length - 1];
      for (int i = 1; i < parts.length; i++) {
         String a = parts[i];
         if (a.startsWith("check:")) {
            Checklist.Entry e = Checklist.byId(a.substring(6));
            args[i - 1] = e == null ? a.substring(6) : checkTitle(e);
         } else if (a.startsWith("key:")) {
            args[i - 1] = JournalUi.tr(a.substring(4));
         } else if (JournalUi.has("entity.frontierhunts." + a)) {
            args[i - 1] = JournalUi.tr("entity.frontierhunts." + a);
         } else {
            args[i - 1] = a;
         }
      }
      return JournalUi.tr(parts[0], args);
   }

   // ============================================================================================ notices

   private static void onNotice(JournalNet.Notice n) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return;
      }
      if (n.kind() == JournalNet.N_XP) {
         ticker(n.a(), n.b());
         return;
      }
      if (!JournalConfig.toasts()) {
         return;
      }
      JournalToast t = null;
      switch (n.kind()) {
         case JournalNet.N_RANK -> {
            Rank r = Rank.byId(n.a());
            Rank next = r.next();
            t = new JournalToast(JournalToast.Kind.RANK, r.icon, JournalUi.tr("journal.frontierhunts.toast.rank"), JournalUi.tr(r.lang()), "",
               next == null ? JournalUi.tr("journal.frontierhunts.toast.rank_top") : JournalUi.tr("journal.frontierhunts.toast.rank_body", JournalUi.tr(r.lang())), 0);
         }
         case JournalNet.N_LEVEL -> {
            Skill s = Skill.byId(n.a());
            if (s != null) {
               Perk next = null;
               for (Perk p : Perk.values()) {
                  if (p.skill == s && p.level > n.b() && next == null) {
                     next = p;
                  }
               }
               t = new JournalToast(JournalToast.Kind.LEVEL, s.icon, JournalUi.tr("journal.frontierhunts.toast.level", n.b()), JournalUi.tr(s.lang("name")), "",
                  next == null ? "" : JournalUi.tr("journal.frontierhunts.toast.level_next", JournalUi.tr(next.lang("name")), next.level), s.color);
            }
         }
         case JournalNet.N_PERK -> {
            Perk p = Perk.byId(n.a());
            if (p != null) {
               t = new JournalToast(JournalToast.Kind.PERK, p.skill.icon,
                  JournalUi.tr("journal.frontierhunts.toast.perk", JournalUi.tr(p.skill.lang("name")), n.b()), JournalUi.tr(p.lang("name")), "",
                  JournalUi.tr(p.lang("desc")), 0);
               mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.2F, 0.4F));
            }
         }
         case JournalNet.N_CHECK -> {
            Checklist.Entry e = Checklist.byId(n.id());
            // [onboard] Handbook / Academy entries already get the Handbook toast or the academy result card: no second toast
            if (e != null && (e.id().startsWith("hb_") || e.id().startsWith("academy_"))) {
               e = null;
            }
            if (e != null) {
               String icon = com.formaworks.frontierhunts.client.JournalScreen.entryIcon(e); // [ledger] journal icon art
               t = new JournalToast(JournalToast.Kind.CHECK, icon,
                  JournalUi.tr("journal.frontierhunts.toast.check", JournalUi.tr(e.category().lang())), checkTitle(e),
                  n.b() > 0 ? "+" + n.b() + " XP" : "", "", 0);
            }
         }
         case JournalNet.N_SUMMARY -> t = new JournalToast(JournalToast.Kind.SUMMARY, JournalIcons.ref("records"),
            JournalUi.tr("journal.frontierhunts.toast.summary"), n.id(), "", JournalUi.tr("journal.frontierhunts.toast.summary_body"), 0);
         default -> {
         }
      }
      if (t != null) {
         mc.getToasts().addToast(t);
      }
   }

   // ============================================================================================ XP ticker

   private static void ticker(int skill, int amount) {
      if (!JournalConfig.xpTicker() || amount <= 0) {
         return;
      }
      long now = System.currentTimeMillis();
      int slot = -1;
      for (int i = 0; i < TICKER; i++) {
         if (tickTime[i] > 0L && tickSkill[i] == skill && now - tickTime[i] < 1500L) {
            slot = i; // merge rapid gains of the same skill
            amount += tickAmount[i];
            break;
         }
      }
      if (slot < 0) {
         long oldest = Long.MAX_VALUE;
         for (int i = 0; i < TICKER; i++) {
            if (tickTime[i] < oldest) {
               oldest = tickTime[i];
               slot = i;
            }
         }
      }
      Skill s = Skill.byId(skill);
      String name = s == null ? JournalUi.tr("journal.frontierhunts.ticker.checklist") : JournalUi.tr(s.lang("name"));
      tickSkill[slot] = skill;
      tickAmount[slot] = amount;
      tickTime[slot] = now;
      tickText[slot] = JournalUi.seq("+" + amount + " XP  " + name, FrontierUi.Size.SMALL);
   }

   private static void renderTicker(GuiGraphics g) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.options.hideGui || mc.player == null || mc.screen != null) {
         return;
      }
      long now = System.currentTimeMillis();
      int x = g.guiWidth() / 2 + 98;
      int baseY = g.guiHeight() - 20;
      int row = 0;
      for (int k = 0; k < TICKER; k++) {
         // newest at the bottom: walk slots by age
         int best = -1;
         long bestT = Long.MIN_VALUE;
         for (int i = 0; i < TICKER; i++) {
            long t = tickTime[i];
            if (t > 0L && now - t < 2800L && t > bestT && (k == 0 || t < lastShown)) {
               bestT = t;
               best = i;
            }
         }
         if (best < 0) {
            break;
         }
         lastShown = bestT;
         long age = now - bestT;
         float a = age < 150L ? age / 150.0F : (age > 2200L ? 1.0F - (age - 2200L) / 600.0F : 1.0F);
         if (a <= 0.02F) {
            continue;
         }
         float rise = age < 150L ? (1.0F - age / 150.0F) * 4.0F : 0.0F;
         Skill s = Skill.byId(tickSkill[best]);
         int color = s == null ? JournalUi.GOLD : s.color;
         int y = (int)(baseY - row * 10 + rise);
         FrontierUi.circle(g, x + 2.5F, y + 3.5F, 2.0F, JournalUi.alpha(0xFF000000 | color, a));
         g.drawString(mc.font, tickText[best], x + 7, y, JournalUi.alpha(0xFFEFE8D7, a), true);
         row++;
      }
   }

   private static long lastShown;
}
