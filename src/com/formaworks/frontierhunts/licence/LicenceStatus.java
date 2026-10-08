package com.formaworks.frontierhunts.licence;

import com.formaworks.frontierhunts.expedition.HuntingCalendar;
import com.formaworks.frontierhunts.journal.JournalApi;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * [1.2.7] "May I take this animal right now?" without side effects: what the warden would check on a claim
 * ({@link Tagging#claim}), read only, for the first-hunt page and card. Nothing here creates records or marks animals.
 */
public final class LicenceStatus {
   private LicenceStatus() {
   }

   /**
    * @param regulated licences are enforced on this server for this species
    * @param licence carries a hunting licence valid this licence season
    * @param tag carries an unfilled tag for the species (true for animals that need none)
    * @param open the species' season is open this month
    * @param suspended the hunter's licence is suspended or revoked
    * @param months the open months (bit = month, 0 = January), for "Sep - Jan"
    * @param daysToOpen reserve days until the season opens (0 when open)
    * @param daysLeft reserve days left in the current licence season
    * @param tagsLeft tags of this kind the hunter may still get this season (the bag limit)
    */
   public record Status(boolean regulated, boolean licence, boolean tag, boolean open, boolean suspended, int months, int daysToOpen, int daysLeft,
      int tagsLeft) {
      /** a legal shot right now */
      public boolean legal() {
         return !this.regulated || this.licence && this.tag && this.open && !this.suspended;
      }

      /** has the paperwork (the season may still be closed) */
      public boolean covered() {
         return !this.regulated || this.licence && this.tag && !this.suspended;
      }
   }

   public static Status of(ServerPlayer p, String species) {
      Regulations.Rule rule = Regulations.of(species);
      LicenceConfig.Mode mode = LicenceConfig.mode();
      int month = LicenceTime.month(p.level());
      if (rule == null || !rule.regulated() || !mode.on()) {
         return new Status(false, true, true, true, false, rule == null ? 0xFFF : rule.months(), 0, LicenceTime.daysLeft(p.level()), 0);
      }
      int period = LicenceTime.period(p.level());
      LicenceStore.Hunter h = LicenceStore.get(p.server).find(p.getUUID());
      LicenceStore.Season s = h == null ? null : h.peek(period);
      boolean suspended = h != null && (h.revoked(period) || s != null && s.suspended);
      boolean licence = Tagging.find(p, LicenceContent.LICENCE.get(), period) >= 0;
      boolean tag = rule.tag() == null || Tagging.find(p, LicenceContent.tag(rule.tag()), period) >= 0;
      boolean open = rule.open(month);
      int tagsLeft = 0;
      if (rule.tag() != null) {
         int bag = rule.tag().bag + LicenceOffice.bonusTags(p, rule.tag());
         tagsLeft = Math.max(0, bag - (s == null ? 0 : s.tags[rule.tag().ordinal()]));
      }
      return new Status(true, licence, tag, open, suspended, rule.months(), open ? 0 : daysToOpen(p, rule), LicenceTime.daysLeft(p.level()), tagsLeft);
   }

   /** reserve days until the first open month of the rule */
   static int daysToOpen(ServerPlayer p, Regulations.Rule rule) {
      HuntingCalendar.Date d = LicenceTime.date(p.level());
      int month = LicenceTime.month(p.level());
      for (int k = 1; k <= 12; k++) {
         if (rule.open(month + k)) {
            return (k - 1) * d.daysPerMonth() + (d.daysPerMonth() - d.day() + 1);
         }
      }
      return 0;
   }

   /**
    * [1.2.7] The free introductory whitetail permit: a hunting licence (if the hunter has none for this season) and one
    * deer tag, once per hunter, wherever they are, so a new hunter learns the law on one common animal before paying
    * for anything. The tag counts against the season's bag like a bought one. Returns false (and says why) when it
    * can't be issued.
    */
   public static boolean introPermit(ServerPlayer p) {
      Regulations.Rule rule = Regulations.of("whitetail");
      if (rule == null || !LicenceConfig.mode().on()) {
         return false;
      }
      Status st = of(p, "whitetail");
      if (st.suspended()) {
         p.displayClientMessage(Component.translatable("firsthunt.frontierhunts.permit.suspended").withStyle(ChatFormatting.RED), true);
         return false;
      }
      if (!st.licence() && !st.tag() && st.tagsLeft() <= 0) {
         p.displayClientMessage(Component.translatable("firsthunt.frontierhunts.permit.bag").withStyle(ChatFormatting.RED), true);
         return false;
      }
      int period = LicenceTime.period(p.level());
      LicenceStore store = LicenceStore.get(p.server);
      LicenceStore.Season s = store.hunter(p.getUUID()).season(period);
      if (!st.licence()) {
         s.licence = true;
         LicenceOffice.give(p, LicenceOffice.issue(p, LicenceContent.LICENCE.get(), period));
         JournalApi.count(p, "licence.licences", 1);
      }
      if (!st.tag() && st.tagsLeft() > 0) {
         s.tags[rule.tag().ordinal()]++;
         LicenceOffice.give(p, LicenceOffice.issue(p, LicenceContent.tag(rule.tag()), period));
         JournalApi.count(p, "licence.tags", 1);
      }
      store.setDirty();
      // and the one tool the first harvest needs, if the new hunter has none yet
      net.minecraft.world.item.Item knife = com.formaworks.frontierhunts.HuntContent.SKINNING_TOOL.get();
      if (!p.getInventory().contains(x -> x.is(knife))) {
         LicenceOffice.give(p, new net.minecraft.world.item.ItemStack(knife));
      }
      p.level().playSound(null, p.getX(), p.getY(), p.getZ(), LicenceContent.SND_STAMP.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
      p.sendSystemMessage(Component.translatable("firsthunt.frontierhunts.permit.issued", Regulations.periodTitle(period)).withStyle(ChatFormatting.GREEN));
      JournalApi.note(p, "Introductory whitetail permit (free): a hunting licence and one deer tag for the " + Regulations.periodTitle(period) + ".");
      return true;
   }
}
