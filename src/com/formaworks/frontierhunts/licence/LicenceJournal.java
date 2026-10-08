package com.formaworks.frontierhunts.licence;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HunterLedger;
import com.formaworks.frontierhunts.journal.Checklist;
import com.formaworks.frontierhunts.journal.JournalPages;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * [licence] The "Licence & Tags" page of the Hunter's Journal (server data; the client view is
 * {@code licence.client.LicencePageView}) and the licence checklist entries.
 */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class LicenceJournal {
   public static final String PAGE = "licence";
   /** Offers on the page: licence, the tags, the stamps, then [1.1.6] the ranger's game report (order of the "o" list). */
   public static final int OFFERS = 1 + Regulations.TagKind.values().length + Regulations.Stamp.values().length + 1;
   public static final int OFFER_REPORT = OFFERS - 1;

   private LicenceJournal() {
   }

   @SubscribeEvent
   public static void setup(FMLCommonSetupEvent e) {
      e.enqueueWork(() -> {
         JournalPages.register(PAGE, "journal.frontierhunts.page.licence", "frontierhunts:hunting_licence", 45, LicenceJournal::page);
         Checklist.register("licence_get", Checklist.Category.HUNTING, "licence.licences", 1, 30, "frontierhunts:hunting_licence");
         Checklist.register("licence_tag_1", Checklist.Category.HUNTING, "licence.filled", 1, 50, "frontierhunts:deer_tag");
         Checklist.register("licence_tag_5", Checklist.Category.HUNTING, "licence.filled", 5, 120, "frontierhunts:filled_tag");
         Checklist.register("licence_legal_25", Checklist.Category.HUNTING, "licence.legal", 25, 200, "frontierhunts:upland_stamp");
      });
   }

   /** One offer: what / index, its kind code and item id. */
   public static int what(int offer) {
      return offer == 0 ? LicenceOffice.WHAT_LICENCE : offer == OFFER_REPORT ? LicenceOffice.WHAT_SERVICE
         : offer <= Regulations.TagKind.values().length ? LicenceOffice.WHAT_TAG : LicenceOffice.WHAT_STAMP;
   }

   public static int index(int offer) {
      int tags = Regulations.TagKind.values().length;
      return offer == 0 || offer == OFFER_REPORT ? 0 : offer <= tags ? offer - 1 : offer - 1 - tags;
   }

   /** The page's server data (well under 4 KB). */
   static CompoundTag page(ServerPlayer p) {
      CompoundTag t = new CompoundTag();
      LicenceConfig.Mode mode = LicenceConfig.mode();
      t.putByte("mode", (byte)mode.ordinal());
      t.putBoolean("emer", LicenceConfig.emeralds());
      t.putFloat("fineMul", (float)LicenceConfig.fineMultiplier());
      int period = LicenceTime.period(p.level());
      t.putInt("period", period);
      t.putInt("month", LicenceTime.month(p.level()));
      t.putInt("daysLeft", LicenceTime.daysLeft(p.level()));
      boolean vendor = mode.on() && LicenceOffice.nearVendor(p);
      t.putBoolean("vendor", vendor);
      t.putInt("tokens", Math.max(0, HunterLedger.get(p.serverLevel()).hunter(p.getUUID()).tokens()));
      t.putInt("emeralds", LicenceOffice.emeralds(p));
      t.putBoolean("edu", LicenceOffice.hunterEducation(p));
      LicenceStore.Hunter h = LicenceStore.get(p.server).hunter(p.getUUID());
      LicenceStore.Season s = h.season(period);
      t.putBoolean("lic", s.licence);
      t.putBoolean("licHeld", Tagging.find(p, LicenceContent.LICENCE.get(), period) >= 0);
      t.putIntArray("bought", s.tags);
      // [1.1.6] what rank and camp are worth at the counter
      com.formaworks.frontierhunts.journal.Rank rank = com.formaworks.frontierhunts.journal.RankPerks.rank(p);
      t.putString("rank", rank.key);
      // [1.2.0] legends taken add 5% each; a Master of the Reserve pays nothing
      t.putInt("discount", com.formaworks.frontierhunts.freak.LegendRewards.master(p) ? 100
         : Math.min(40, com.formaworks.frontierhunts.journal.RankPerks.discount(rank) + com.formaworks.frontierhunts.freak.LegendRewards.discount(p)));
      int[] bags = new int[Regulations.TagKind.values().length];
      for (Regulations.TagKind k : Regulations.TagKind.values()) {
         bags[k.ordinal()] = k.bag + LicenceOffice.bonusTags(p, k);
      }
      t.putIntArray("bags", bags);
      t.putInt("stamps", s.stamps);
      int[] held = new int[Regulations.TagKind.values().length];
      for (Regulations.TagKind k : Regulations.TagKind.values()) {
         held[k.ordinal()] = count(p, k, period);
      }
      t.putIntArray("held", held);
      int stampHeld = 0;
      for (Regulations.Stamp st : Regulations.Stamp.values()) {
         if (Tagging.find(p, LicenceContent.stamp(st), period) >= 0) {
            stampHeld |= 1 << st.ordinal();
         }
      }
      t.putInt("stampHeld", stampHeld);
      long day = LicenceTime.day(p.level());
      CompoundTag birds = new CompoundTag();
      for (Regulations.Rule r : Regulations.all()) {
         if (r.group() == Regulations.Group.BIRD) {
            birds.putInt(r.species(), h.birds(day, r.species()));
         }
      }
      t.put("birds", birds);
      t.putBoolean("susp", s.suspended);
      t.putBoolean("warned", s.warned);
      t.putInt("strikes", s.strikes);
      t.putInt("fines", s.fines);
      t.putInt("viol", s.violations);
      t.putInt("filled", s.filled);
      t.putInt("filledAll", h.filledTotal);
      t.putInt("violAll", h.violationsTotal);
      t.putInt("finesAll", h.finesTotal);
      int periodNow = LicenceTime.period(p.level());
      t.putInt("record", h.record(periodNow));
      t.putInt("charges", h.charges);
      t.putInt("revokedLeft", Math.max(0, h.revokedUntil - periodNow));
      // the counter's answer for every offer: why not, per payment (empty = can do)
      ListTag offers = new ListTag();
      for (int o = 0; o < OFFERS; o++) {
         CompoundTag c = new CompoundTag();
         int w = what(o), i = index(o);
         c.putString("t", nz(LicenceOffice.refusal(p, w, i, LicenceOffice.PAY_TOKENS, vendor)));
         c.putString("e", nz(LicenceOffice.refusal(p, w, i, LicenceOffice.PAY_EMERALDS, vendor)));
         c.putString("f", nz(LicenceOffice.refusal(p, w, i, LicenceOffice.PAY_FREE, vendor)));
         offers.add(c);
      }
      t.put("o", offers);
      ListTag log = new ListTag();
      for (FilledTag f : h.log) {
         if (log.size() >= 16) {
            break;
         }
         log.add(f.save());
      }
      t.put("log", log);
      return t;
   }

   private static String nz(String s) {
      return s == null ? "" : s;
   }

   private static int count(ServerPlayer p, Regulations.TagKind k, int period) {
      int n = 0;
      var inv = p.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         var s = inv.getItem(i);
         if (s.is(LicenceContent.tag(k))) {
            PermitData d = s.get(LicenceContent.PERMIT.get());
            if (d != null && d.validFor(p.getUUID(), period)) {
               n += s.getCount();
            }
         }
      }
      return n;
   }
}
