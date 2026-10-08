package com.formaworks.frontierhunts.licence;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HunterLedger;
import com.formaworks.frontierhunts.academy.Course;
import com.formaworks.frontierhunts.academy.TrainingStore;
import com.formaworks.frontierhunts.journal.JournalApi;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.formaworks.frontierhunts.journal.Rank;
import com.formaworks.frontierhunts.journal.RankPerks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * [licence] The licence counter: buying (reserve tokens or emeralds) or claiming licences, tags and stamps, server
 * side and fully validated (mode on, standing within 6 blocks of an Expedition Board, Ranger Contract Board or Lodge
 * Stores, licence held for tags/stamps, bag limit, season, suspension, payment, rate limit). Hunters who passed the
 * Ranger Academy's Archery course (hunter education) get their Hunting Licence free.
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class LicenceOffice {
   public static final int WHAT_LICENCE = 0, WHAT_TAG = 1, WHAT_STAMP = 2, WHAT_SERVICE = 3; // [1.1.6] ranger services
   public static final int PAY_TOKENS = 0, PAY_EMERALDS = 1, PAY_FREE = 2;
   /** Blocks that sell licences (registry paths in frontierhunts). */
   public static final Set<String> VENDORS = Set.of("expedition_board", "contract_board", "lodge_stores", "ranger_window");
   private static final Map<UUID, Long> LAST = new HashMap<>();

   private LicenceOffice() {
   }

   /** Within 6 blocks (3 up/down) of a licence counter. */
   public static boolean nearVendor(ServerPlayer p) {
      BlockPos c = p.blockPosition();
      Level level = p.level();
      for (BlockPos b : BlockPos.betweenClosed(c.offset(-6, -3, -6), c.offset(6, 3, 6))) {
         ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(b).getBlock());
         if (id.getNamespace().equals(FrontierHunts.ID) && VENDORS.contains(id.getPath())) {
            return true;
         }
         if (id.getNamespace().equals(FrontierHunts.ID) && id.getPath().equals("camp_post") && com.formaworks.frontierhunts.camps.CampPerks.tier(p) >= 1) {
            return true; // [1.1.6] a tent camp's post is a licence counter
         }
      }
      return false;
   }

   /** Passed the Archery course (hunter education): the Hunting Licence is free. */
   public static boolean hunterEducation(ServerPlayer p) {
      return Integer.bitCount(academyPassed(p)) >= Course.curriculum().length;
   }

   /**
    * [1.2.9] The Ranger Academy courses this hunter has passed, one bit per course in teaching order. Hunter education
    * (and with it the Hunting Licence) is the WHOLE Academy now, not one course: the licence is never sold.
    */
   public static int academyPassed(ServerPlayer p) {
      int bits = 0;
      try {
         TrainingStore store = TrainingStore.get(p.server);
         Course[] all = Course.curriculum();
         for (int i = 0; i < all.length; i++) {
            TrainingStore.Record r = store.peek(p.getUUID(), all[i]);
            if (r != null && r.passes > 0) {
               bits |= 1 << i;
            }
         }
      } catch (RuntimeException e) {
         return 0;
      }
      return bits;
   }

   public static int emeralds(ServerPlayer p) {
      int n = 0;
      Inventory inv = p.getInventory();
      for (int i = 0; i < inv.items.size(); i++) {
         if (inv.items.get(i).is(Items.EMERALD)) {
            n += inv.items.get(i).getCount();
         }
      }
      return n;
   }

   private static void takeEmeralds(ServerPlayer p, int n) {
      Inventory inv = p.getInventory();
      for (int i = 0; i < inv.items.size() && n > 0; i++) {
         ItemStack s = inv.items.get(i);
         if (s.is(Items.EMERALD)) {
            int k = Math.min(n, s.getCount());
            s.shrink(k);
            n -= k;
         }
      }
      inv.setChanged();
   }

   /** Months of the current licence season (bit mask). */
   static int seasonMonths(int period) {
      int m = 0;
      long first = Regulations.firstMonth(period);
      for (int i = 0; i < Regulations.SEASON_MONTHS; i++) {
         m |= 1 << Math.floorMod(first + i, 12L);
      }
      return m;
   }

   /** Why this can not be bought now, or null. Shared by the request and the journal page. */
   public static String refusal(ServerPlayer p, int what, int index, int pay) {
      return refusal(p, what, index, pay, nearVendor(p));
   }

   /** As above with the counter check done once by the caller. */
   public static String refusal(ServerPlayer p, int what, int index, int pay, boolean vendor) {
      String why = rules(p, what, index, pay);
      return why == null && !vendor ? "vendor" : why;
   }

   private static String rules(ServerPlayer p, int what, int index, int pay) {
      LicenceConfig.Mode mode = LicenceConfig.mode();
      if (!mode.on()) {
         return "off";
      }
      if (pay != PAY_TOKENS && pay != PAY_EMERALDS && pay != PAY_FREE || what < WHAT_LICENCE || what > WHAT_SERVICE) {
         return "bad";
      }
      if (p.isSpectator()) {
         return "spectator";
      }
      int period = LicenceTime.period(p.level());
      LicenceStore.Hunter h = LicenceStore.get(p.server).hunter(p.getUUID());
      LicenceStore.Season s = h.season(period);
      if (s.suspended) {
         return "suspended";
      }
      if (h.revoked(period)) {
         return "revoked";
      }
      if (pay == PAY_EMERALDS && !LicenceConfig.emeralds()) {
         return "no_emeralds_here";
      }
      int tokens = 0, emer = 0;
      switch (what) {
         case WHAT_LICENCE -> {
            boolean replace = s.licence;
            if (replace && Tagging.find(p, LicenceContent.LICENCE.get(), period) >= 0) {
               return "have";
            }
            if (replace || hunterEducation(p)) {
               return pay == PAY_FREE ? null : "free";
            }
            // [1.2.9] the licence is only ever issued by the Ranger Academy, to hunters who finished all of it
            return "academy";
         }
         case WHAT_TAG -> {
            if (index < 0 || index >= Regulations.TagKind.values().length) {
               return "bad";
            }
            Regulations.TagKind k = Regulations.TagKind.values()[index];
            if (k.ordinal() > Regulations.TagKind.MOOSE.ordinal()) {
               return "beta"; // [1.1.6] animals that are not in the wild yet: listed, never sold
            }
            if (!s.licence) {
               return "licence_first";
            }
            if (s.tags[index] >= k.bag + bonusTags(p, k)) {
               return "bag";
            }
            if ((Regulations.months(k) & seasonMonths(period)) == 0) {
               return "closed";
            }
            if (pay == PAY_FREE) {
               return "not_free";
            }
            tokens = RankPerks.price(p, k.tokens);
            emer = k.emeralds;
         }
         case WHAT_STAMP -> {
            if (index < 0 || index >= Regulations.Stamp.values().length) {
               return "bad";
            }
            Regulations.Stamp st = Regulations.Stamp.values()[index];
            if (!s.licence) {
               return "licence_first";
            }
            if ((s.stamps >> index & 1) != 0) {
               return Tagging.find(p, LicenceContent.stamp(st), period) >= 0 ? "have" : (pay == PAY_FREE ? null : "free");
            }
            if ((Regulations.months(st) & seasonMonths(period)) == 0) {
               return "closed";
            }
            if (pay == PAY_FREE) {
               return "not_free";
            }
            tokens = RankPerks.price(p, st.tokens);
            emer = st.emeralds;
         }
         case WHAT_SERVICE -> {
            if (index != 0) {
               return "bad";
            }
            String r = GameReport.refusal(p);
            if (r != null) {
               return r;
            }
            if (GameReport.free(p)) {
               return pay == PAY_FREE ? null : "free";
            }
            if (pay == PAY_FREE) {
               return "not_free";
            }
            tokens = RankPerks.price(p, GameReport.TOKENS);
            emer = GameReport.EMERALDS;
         }
         default -> {
            return "bad";
         }
      }
      if (pay == PAY_TOKENS && HunterLedger.get(p.serverLevel()).hunter(p.getUUID()).tokens() < tokens) {
         return "tokens";
      }
      if (pay == PAY_EMERALDS && emeralds(p) < emer) {
         return "emeralds";
      }
      return null;
   }

   /** A purchase / claim request from the journal page (LicenceNetwork). */
   public static void request(ServerPlayer p, int what, int index, int pay) {
      request(p, what, index, pay, nearVendor(p));
   }

   /** As above with the counter check already done ([1.2.9] the QA harness buys a tag with it). */
   public static void request(ServerPlayer p, int what, int index, int pay, boolean vendor) {
      if (p.hasDisconnected()) {
         return; // [1.2.7] arrived after the hunter left: never charge for a permit that can't reach their pack
      }
      long now = p.serverLevel().getGameTime();
      Long last = LAST.put(p.getUUID(), now);
      if (last != null && now >= last && now - last < 8L) {
         return; // rate limit: one counter action every 8 ticks
      }
      String why = refusal(p, what, index, pay, vendor);
      if (why != null) {
         p.displayClientMessage(Component.translatable("licence.frontierhunts.why." + why).withStyle(ChatFormatting.RED), true);
         return;
      }
      int period = LicenceTime.period(p.level());
      LicenceStore store = LicenceStore.get(p.server);
      LicenceStore.Season s = store.hunter(p.getUUID()).season(period);
      if (what == WHAT_SERVICE) {
         int cost = pay == PAY_TOKENS ? RankPerks.price(p, GameReport.TOKENS) : 0;
         if (pay == PAY_TOKENS && !HunterLedger.get(p.serverLevel()).spend(p.getUUID(), cost)) {
            return;
         }
         if (pay == PAY_EMERALDS) {
            takeEmeralds(p, GameReport.EMERALDS);
         }
         GameReport.deliver(p);
         return;
      }
      Item item;
      int tokens = 0, emer = 0;
      String counter;
      switch (what) {
         case WHAT_LICENCE -> {
            item = LicenceContent.LICENCE.get();
            if (pay != PAY_FREE) {
               tokens = RankPerks.price(p, Regulations.LICENCE_TOKENS);
               emer = Regulations.LICENCE_EMERALDS;
            }
            counter = "licence.licences";
         }
         case WHAT_TAG -> {
            Regulations.TagKind k = Regulations.TagKind.values()[index];
            item = LicenceContent.tag(k);
            tokens = RankPerks.price(p, k.tokens);
            emer = k.emeralds;
            counter = "licence.tags";
         }
         default -> {
            Regulations.Stamp st = Regulations.Stamp.values()[index];
            item = LicenceContent.stamp(st);
            if (pay != PAY_FREE) {
               tokens = RankPerks.price(p, st.tokens);
               emer = st.emeralds;
            }
            counter = "licence.stamps";
         }
      }
      if (pay == PAY_TOKENS && tokens > 0 && !HunterLedger.get(p.serverLevel()).spend(p.getUUID(), tokens)) {
         return;
      }
      if (pay == PAY_EMERALDS && emer > 0) {
         takeEmeralds(p, emer);
      }
      boolean replacement = pay == PAY_FREE && (what == WHAT_LICENCE ? s.licence : what == WHAT_STAMP);
      switch (what) {
         case WHAT_LICENCE -> s.licence = true;
         case WHAT_TAG -> s.tags[index]++;
         default -> s.stamps |= 1 << index;
      }
      store.setDirty();
      give(p, issue(p, item, period));
      com.formaworks.frontierhunts.progression.Durability.commit(p.server, "licence counter"); // [1.2.7] the permit and the books saved together
      if (!replacement) {
         JournalApi.count(p, counter, 1);
      } else {
         JournalApi.count(p, "licence.replaced", 1);
      }
      p.level().playSound(null, p.getX(), p.getY(), p.getZ(), LicenceContent.SND_STAMP.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
      p.displayClientMessage(Component.translatable(replacement ? "licence.frontierhunts.msg.replaced" : "licence.frontierhunts.msg.bought",
         item.getDescription(), Regulations.periodTitle(period)).withStyle(ChatFormatting.GREEN), true);
   }

   static ItemStack issue(ServerPlayer p, Item item, int period) {
      ItemStack st = new ItemStack(item);
      st.set(LicenceContent.PERMIT.get(), new PermitData(p.getUUID(), p.getGameProfile().getName(), period));
      return st;
   }

   static void give(ServerPlayer p, ItemStack st) {
      if (!p.getInventory().add(st)) {
         ItemEntity ie = p.drop(st, false);
         if (ie != null) {
            ie.setNoPickUpDelay();
         }
      }
      p.inventoryMenu.broadcastChanges();
   }

   // ============================================================================================ hunter education

   /** [1.2.9] Finishing the whole Ranger Academy hands out the first licence once, wherever the hunter is. */
   @SubscribeEvent
   public static void tick(PlayerTickEvent.Post e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || (p.tickCount + (p.getId() & 63)) % 100 != 41 || !p.isAlive() || p.isSpectator()) {
         return;
      }
      try {
         if (!LicenceConfig.mode().on() || com.formaworks.frontierhunts.academy.Academy.inTraining(p)) {
            return;
         }
         graduate(p);
      } catch (RuntimeException ignored) {
      }
   }

   /** [1.2.9] A graduate of the whole Ranger Academy without this season's licence gets it now; true when one was issued. */
   public static boolean graduate(ServerPlayer p) {
      if (!hunterEducation(p)) {
         return false;
      }
      // [1.2.9] a graduate of the whole Academy gets this season's licence, every season, without asking
      LicenceStore store = LicenceStore.get(p.server);
      LicenceStore.Hunter h = store.hunter(p.getUUID());
      int period = LicenceTime.period(p.level());
      LicenceStore.Season s = h.season(period);
      if (s.licence || s.suspended || h.revoked(period)) {
         return false;
      }
      h.edIssued = true;
      s.licence = true;
      if (Tagging.find(p, LicenceContent.LICENCE.get(), period) < 0) {
         give(p, issue(p, LicenceContent.LICENCE.get(), period));
         JournalApi.count(p, "licence.licences", 1);
         p.playNotifySound(LicenceContent.SND_STAMP.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
         p.sendSystemMessage(Component.translatable("licence.frontierhunts.msg.education").withStyle(ChatFormatting.GREEN));
         JournalApi.note(p, "Ranger Academy graduate: the Academy issued a Hunting Licence for the " + Regulations.periodTitle(period) + ".");
      }
      store.setDirty();
      com.formaworks.frontierhunts.progression.Durability.commit(p.server, "education licence");
      return true;
   }

   /** [1.1.6] a tier-4 camp gets one more deer tag a season (camp upgrades must change something) */
   static int bonusTags(ServerPlayer p, Regulations.TagKind k) {
      return k == Regulations.TagKind.DEER
         ? (com.formaworks.frontierhunts.camps.CampPerks.tier(p) >= 4 ? 1 : 0) + com.formaworks.frontierhunts.journal.RankPerks.extraDeerTags(p)
         : 0;
   }
}
