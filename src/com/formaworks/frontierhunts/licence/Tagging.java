package com.formaworks.frontierhunts.licence;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HunterLedger;
import com.formaworks.frontierhunts.camps.Quarry;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.journal.JournalApi;
import com.formaworks.frontierhunts.journal.JournalService;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.mojang.logging.LogUtils;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import org.slf4j.Logger;

/**
 * [licence] The moment an animal is claimed: a deer / elk / moose when it is field-dressed (Whitetail.harvest hook), a
 * 2026 wildlife animal when it dies to a hunter. Checks the hunter's licence, tag or stamp, the open season and the
 * bird bag, then either fills a tag (the tag in the pack becomes a Filled Tag with species, date, place and weight) or
 * applies the warden's penalty for the server's {@link LicenceConfig.Mode}. Server only; all state in {@link LicenceStore}.
 *
 * <p>The verdict is stamped on the animal ({@link #KEY}) so the species hunts (HuntHooks) and the wildlife drops can
 * read it: a poached animal never counts for a hunt milestone, and in Strict mode half its meat and its trophy are kept
 * as evidence.
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class Tagging {
   private static final Logger LOG = LogUtils.getLogger();
   public static final String KEY = "frontierhunts_licence";
   /** journal's per-animal hit record (JournalHooks.HIT) */
   private static final String JOURNAL_HIT = "frontierhunts_journal_hit";

   public enum Violation {
      NONE, SUSPENDED, NO_LICENCE, CLOSED, NO_TAG, NO_STAMP, OVER_LIMIT, UNTAGGED;

      public String key() {
         return this.name().toLowerCase(Locale.ROOT);
      }
   }

   /** What happens to the yield of a claimed animal. */
   public record Verdict(boolean poached, boolean confiscate) {
      public static final Verdict CLEAR = new Verdict(false, false);

      public int meat(int n) {
         return this.confiscate ? n / 2 : n;
      }

      public ItemStack trophy(ItemStack s) {
         return this.confiscate ? ItemStack.EMPTY : s;
      }
   }

   private Tagging() {
   }

   // ============================================================================================ hooks

   /**
    * Whitetail.harvest (deer, elk, moose), right after the training-carcass check: the shooter (or the dresser when the
    * shooter is offline) claims it.
    */
   public static Verdict deer(ServerPlayer dresser, Whitetail deer, UUID shooter) {
      try {
         ServerPlayer hunter = dresser;
         if (shooter != null && !shooter.equals(dresser.getUUID())) {
            ServerPlayer s = dresser.server.getPlayerList().getPlayer(shooter);
            if (s != null && s.level() == dresser.level()) {
               hunter = s;
            }
         }
         int points = deer.traits().buck() ? deer.traits().totalPoints() : 0;
         return claim(hunter, deer, deer.species().id, deer.massKg(), points);
      } catch (RuntimeException e) {
         LOG.warn("Frontier Hunts licence: deer check failed", e);
         return Verdict.CLEAR;
      }
   }

   /** Wildlife died: the killer, or the hunter whose wound bled it out (same attribution as the journal). */
   @SubscribeEvent(priority = EventPriority.LOW)
   public static void death(LivingDeathEvent e) {
      if (e.isCanceled() || !(e.getEntity() instanceof WildlifeMob mob) || !(mob.level() instanceof ServerLevel level)) {
         return;
      }
      try {
         ServerPlayer p = e.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
         if (p == null) {
            CompoundTag h = mob.getPersistentData().getCompound(JOURNAL_HIT);
            if (h.hasUUID("by") && level.getGameTime() - h.getLong("t") < 24000L) {
               p = level.getServer().getPlayerList().getPlayer(h.getUUID("by"));
            }
         }
         if (p == null) {
            return;
         }
         Quarry q = Quarry.find(mob.species.id);
         UUID id = mob.getUUID();
         double kg = q == null ? 0.0 : q.weightFor(id.getMostSignificantBits() ^ id.getLeastSignificantBits()) * ((id.getLeastSignificantBits() & 1L) == 0L ? 1.0 : 0.82);
         claim(p, mob, mob.species.id, kg, 0);
      } catch (RuntimeException ex) {
         LOG.warn("Frontier Hunts licence: wildlife check failed", ex);
      }
   }

   /** Strict mode: a poached wildlife animal's meat is halved and its pelt / hide kept as evidence. */
   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void drops(LivingDropsEvent e) {
      if (!(e.getEntity() instanceof WildlifeMob mob) || mob.level().isClientSide || !confiscated(mob)) {
         return;
      }
      e.getDrops().removeIf(ie -> {
         ItemStack s = ie.getItem();
         if (s.has(DataComponents.FOOD)) {
            int n = s.getCount() / 2;
            if (n <= 0) {
               return true;
            }
            ie.setItem(s.copyWithCount(n));
            return false;
         }
         return isPelt(s);
      });
   }

   private static boolean isPelt(ItemStack s) {
      String p = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
      return p.endsWith("_pelt") || p.equals("heavy_hide") || p.equals("deer_hide");
   }

   /** True when this animal was claimed without a valid licence / tag / season (HuntHooks: no hunt credit). */
   public static boolean poached(Entity e) {
      return e != null && e.getPersistentData().getCompound(KEY).getBoolean("poached");
   }

   static boolean confiscated(Entity e) {
      return e != null && e.getPersistentData().getCompound(KEY).getBoolean("seized");
   }

   // ============================================================================================ the check

   static Verdict claim(ServerPlayer p, LivingEntity animal, String species, double kg, int points) {
      CompoundTag mark = animal.getPersistentData().getCompound(KEY);
      if (mark.getBoolean("done")) {
         return new Verdict(mark.getBoolean("poached"), mark.getBoolean("seized"));
      }
      mark.putBoolean("done", true);
      animal.getPersistentData().put(KEY, mark);
      fedForTheField(p);
      LicenceConfig.Mode mode = LicenceConfig.mode();
      Regulations.Rule rule = Regulations.of(species);
      if (!mode.on() || rule == null || !rule.regulated() || p.isCreative() || p.isSpectator()
         || com.formaworks.frontierhunts.academy.Academy.inTraining(p)) {
         return Verdict.CLEAR;
      }
      ServerLevel level = p.serverLevel();
      int period = LicenceTime.period(level);
      int month = LicenceTime.month(level);
      long day = LicenceTime.day(level);
      LicenceStore store = LicenceStore.get(p.server);
      LicenceStore.Hunter h = store.hunter(p.getUUID());
      LicenceStore.Season season = h.season(period);
      Violation v = Violation.NONE;
      int tagSlot = -1;
      if (season.suspended || h.revoked(period)) {
         v = Violation.SUSPENDED;
      } else if (find(p, LicenceContent.LICENCE.get(), period) < 0) {
         v = Violation.NO_LICENCE;
      } else if (!rule.open(month)) {
         v = Violation.CLOSED;
      } else if (rule.group() == Regulations.Group.BIG_GAME) {
         tagSlot = find(p, LicenceContent.tag(rule.tag()), period);
         if (tagSlot < 0) {
            v = Violation.NO_TAG;
         }
      } else if (rule.group() == Regulations.Group.BIRD) {
         if (find(p, LicenceContent.stamp(rule.stamp()), period) < 0) {
            v = Violation.NO_STAMP;
         } else if (h.birds(day, species) >= rule.stamp().daily) {
            v = Violation.OVER_LIMIT;
         }
      }
      if (rule.group() == Regulations.Group.BIRD) {
         h.addBird(day, species);
      }
      String title = rule.title();
      String place = place(level, animal.blockPosition());
      String date = JournalService.stamp(level);
      int kg10 = (int)Math.round(kg * 10.0);
      String kind = rule.tag() != null ? rule.tag().key() : rule.stamp().key();
      Verdict verdict;
      if (v == Violation.NONE) {
         FilledTag record = new FilledTag(kind, rule.species(), title, period, date, place, kg10, points, p.getGameProfile().getName(), 0);
         h.log(record);
         h.filledTotal++;
         season.filled++;
         if (tagSlot >= 0) {
            fill(p, tagSlot, record, rule.tag());
            JournalApi.count(p, "licence.filled", 1);
            JournalApi.note(p, "Tag filled: " + title + ", " + record.weight() + (points > 0 ? ", " + points + " points" : "") + ". " + place + ".");
            p.displayClientMessage(Component.translatable("licence.frontierhunts.msg.filled", Component.translatable("item.frontierhunts." + rule.tag().item), title,
               record.weight()).withStyle(ChatFormatting.GOLD), true);
         } else {
            int left = rule.stamp().daily - h.birds(day, species);
            p.displayClientMessage(Component.translatable("licence.frontierhunts.msg.bird", title, Math.max(0, left), rule.stamp().daily)
               .withStyle(ChatFormatting.GOLD), true);
         }
         JournalApi.count(p, "licence.legal", 1);
         verdict = Verdict.CLEAR;
      } else {
         verdict = penalise(p, h, season, rule, v, mode);
         h.log(new FilledTag(kind, rule.species(), title, period, date, place, kg10, points, p.getGameProfile().getName(), 1));
      }
      store.setDirty();
      com.formaworks.frontierhunts.progression.Durability.commit(p.server, "tag claim");
      mark.putBoolean("poached", verdict.poached());
      mark.putBoolean("seized", verdict.confiscate());
      mark.putString("v", v.key());
      animal.getPersistentData().put(KEY, mark);
      return verdict;
   }

   /**
    * [gear21] The warden's ladder. Every violation (no licence or tag, out of season, over the limit, an animal left
    * untagged) puts a point on the hunter's warden record; one point wears off for every clean licence season.
    * <ol>
    * <li>first point: a written warning and a fine; the evidence (the trophy, half the meat) is seized;</li>
    * <li>second point: a bigger fine and the licence suspended for the rest of the licence season;</li>
    * <li>third point and on: charged with poaching - a heavy fine, the licence revoked for four licence seasons (a
    * reserve year), the charge on the record and the hunter's name posted to every hunter on the server.</li>
    * </ol>
    * Relaxed mode halves the fines and keeps the evidence with the hunter; the steps are the same.
    */
   private static Verdict penalise(ServerPlayer p, LicenceStore.Hunter h, LicenceStore.Season season, Regulations.Rule rule, Violation v,
      LicenceConfig.Mode mode) {
      season.violations++;
      h.violationsTotal++;
      JournalApi.count(p, "licence.violations", 1);
      boolean strict = mode == LicenceConfig.Mode.STRICT;
      int period = LicenceTime.period(p.level());
      int record = h.record(period) + 1;
      h.record = record;
      h.lastViolation = period;
      season.strikes++;
      int base = fine(rule, v, true);
      int fine = record <= 1 ? base : record == 2 ? base * 2 : base * 4;
      if (!strict) {
         fine /= 2;
      }
      fine = (int)Math.round(fine * LicenceConfig.fineMultiplier());
      if (record == 2) {
         season.suspended = true;
      } else if (record >= 3) {
         season.suspended = true;
         h.revokedUntil = Math.max(h.revokedUntil, period + 5);
         h.charges++;
      }
      int paid = 0;
      if (fine > 0) {
         HunterLedger ledger = HunterLedger.get(p.serverLevel());
         int have = Math.max(0, ledger.hunter(p.getUUID()).tokens());
         paid = Math.min(have, fine);
         if (paid > 0 && ledger.spend(p.getUUID(), paid)) {
            season.fines += paid;
            h.finesTotal += paid;
         } else {
            paid = 0;
         }
      }
      // the warden's notice: chat, a sound, a journal note
      Component reason = Component.translatable("licence.frontierhunts.violation." + v.key(), rule.title());
      Component head = Component.translatable("licence.frontierhunts.warden").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
      Component body = Component.translatable(record <= 1 ? "licence.frontierhunts.msg.ladder1" : record == 2 ? "licence.frontierhunts.msg.ladder2"
         : "licence.frontierhunts.msg.ladder3", reason, paid);
      p.sendSystemMessage(Component.empty().append(head).append(" ").append(body.copy().withStyle(record >= 3 ? ChatFormatting.RED : ChatFormatting.YELLOW)));
      if (fine > paid) {
         p.sendSystemMessage(Component.translatable("licence.frontierhunts.msg.unpaid", fine - paid).withStyle(ChatFormatting.GRAY));
      }
      if (record >= 3) {
         // the poaching charge is posted to every hunter on the reserve
         Component wanted = Component.translatable("licence.frontierhunts.msg.charged", p.getGameProfile().getName(), rule.title())
            .withStyle(ChatFormatting.RED);
         for (ServerPlayer other : p.server.getPlayerList().getPlayers()) {
            if (other != p) {
               other.sendSystemMessage(Component.empty().append(head).append(" ").append(wanted));
            }
         }
      }
      p.playNotifySound(LicenceContent.SND_WARDEN.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
      JournalApi.note(p, "Game warden: " + body.getString());
      return new Verdict(true, strict);
   }

   // ============================================================================================ [gear21] tagging by hand

   /** Big game is tagged by hand: use the right tag on the animal before you skin it or walk away from it. */
   public static void tagAction(ServerPlayer p, Whitetail deer, UUID shooter) {
      CompoundTag mark = deer.getPersistentData().getCompound(KEY);
      Regulations.Rule rule = Regulations.of(deer.species().id);
      if (mark.getBoolean("done")) {
         p.displayClientMessage(Component.translatable(mark.getBoolean("poached") ? "licence.frontierhunts.tag.reported" : "licence.frontierhunts.tag.already")
            .withStyle(ChatFormatting.GRAY), true);
         return;
      }
      if (rule == null || rule.tag() == null) {
         return;
      }
      if (!(p.getMainHandItem().getItem() instanceof PermitItem pi) || pi.tag != rule.tag()) {
         p.displayClientMessage(Component.translatable("licence.frontierhunts.tag.wrong", Component.translatable("item.frontierhunts." + rule.tag().item), rule.title())
            .withStyle(ChatFormatting.YELLOW), true);
         return;
      }
      Verdict v = deer(p, deer, shooter);
      if (!v.poached()) {
         // [1.2.8] tagged: the very next thing is the knife
         p.sendSystemMessage(Component.translatable("licence.frontierhunts.tagged.next").withStyle(ChatFormatting.YELLOW));
      }
   }

   /** Skinning an animal that was not tagged while you carry a tag for it: tag it first (no penalty yet). */
   public static boolean mustTagFirst(ServerPlayer p, Whitetail deer) {
      if (!LicenceConfig.mode().on() || p.isCreative() || com.formaworks.frontierhunts.academy.Academy.inTraining(p)
         || deer.getPersistentData().getCompound(KEY).getBoolean("done")) {
         return false;
      }
      Regulations.Rule rule = Regulations.of(deer.species().id);
      if (rule == null || rule.tag() == null || find(p, LicenceContent.tag(rule.tag()), LicenceTime.period(p.level())) < 0) {
         return false;
      }
      p.displayClientMessage(Component.translatable("licence.frontierhunts.tag.first", Component.translatable("item.frontierhunts." + rule.tag().item))
         .withStyle(ChatFormatting.GOLD), true);
      return true;
   }

   /**
    * A downed animal's tag window (every second while it lies there): once the hunter who shot it has come up to it
    * (within 6 blocks), it must be tagged within 3 minutes and before they walk more than 48 blocks away - or the
    * warden hears of an untagged kill. A wounded animal that was never found is not held against anyone.
    */
   public static void watch(Whitetail deer, UUID shooter) {
      if (shooter == null || !(deer.level() instanceof ServerLevel level)) {
         return;
      }
      CompoundTag mark = deer.getPersistentData().getCompound(KEY);
      if (mark.getBoolean("done")) {
         return;
      }
      ServerPlayer p = level.getServer().getPlayerList().getPlayer(shooter);
      if (p == null || p.level() != level) {
         return;
      }
      long now = level.getGameTime();
      double d = p.distanceTo(deer);
      if (!mark.getBoolean("told")) {
         // [1.2.8] the moment it's down, in chat (it stays there): what to do now, so nobody stands over a deer wondering
         mark.putBoolean("told", true);
         deer.getPersistentData().put(KEY, mark);
         downTold(p, deer);
      }
      if (!mark.contains("found")) {
         if (d < 6.0) {
            mark.putLong("found", now);
            deer.getPersistentData().put(KEY, mark);
            Regulations.Rule rule = Regulations.of(deer.species().id);
            if (rule != null && rule.tag() != null && LicenceConfig.mode().on() && !p.isCreative()) {
               p.displayClientMessage(Component.translatable("licence.frontierhunts.tag.reminder", Component.translatable("item.frontierhunts." + rule.tag().item))
                  .withStyle(ChatFormatting.GOLD), true);
            }
         }
         return;
      }
      if (now - mark.getLong("found") > 3600L || d > 48.0) {
         untagged(p, deer);
      }
   }

   /**
    * [1.2.8] "Deer down": tag it (if the law wants a tag and whether you carry one), then skin it with the Contour Skinning
    * Knife (and whether you carry one). New hunters (fewer than five harvests) get it in chat; others on the action bar.
    */
   private static void downTold(ServerPlayer p, Whitetail deer) {
      if (p.isCreative() || com.formaworks.frontierhunts.academy.Academy.inTraining(p)) {
         return;
      }
      Regulations.Rule rule = Regulations.of(deer.species().id);
      boolean law = LicenceConfig.mode().on() && rule != null && rule.regulated() && rule.tag() != null;
      net.minecraft.world.item.Item knife = com.formaworks.frontierhunts.HuntContent.SKINNING_TOOL.get();
      boolean hasKnife = p.getInventory().contains(x -> x.is(knife));
      Component animal = Component.translatable("entity.frontierhunts." + deer.species().id);
      net.minecraft.network.chat.MutableComponent msg;
      if (law) {
         Component tagItem = Component.translatable("item.frontierhunts." + rule.tag().item);
         boolean hasTag = find(p, LicenceContent.tag(rule.tag()), LicenceTime.period(p.level())) >= 0;
         msg = Component.translatable(hasTag ? "licence.frontierhunts.down.tag" : "licence.frontierhunts.down.no_tag", animal, tagItem);
      } else {
         msg = Component.translatable("licence.frontierhunts.down.free", animal);
      }
      msg.append(" ").append(Component.translatable(hasKnife ? "licence.frontierhunts.down.knife" : "licence.frontierhunts.down.no_knife"));
      boolean beginner = com.formaworks.frontierhunts.journal.JournalApi.counter(p, "harvests") < 5;
      if (beginner) {
         p.sendSystemMessage(Component.translatable("licence.frontierhunts.down.prefix").withStyle(ChatFormatting.GOLD).append(msg.withStyle(ChatFormatting.YELLOW)));
      } else {
         p.displayClientMessage(msg.withStyle(ChatFormatting.GOLD), true);
      }
   }

   private static void untagged(ServerPlayer p, Whitetail deer) {
      CompoundTag mark = deer.getPersistentData().getCompound(KEY);
      mark.putBoolean("done", true);
      deer.getPersistentData().put(KEY, mark);
      LicenceConfig.Mode mode = LicenceConfig.mode();
      Regulations.Rule rule = Regulations.of(deer.species().id);
      if (!mode.on() || rule == null || !rule.regulated() || p.isCreative() || p.isSpectator()
         || com.formaworks.frontierhunts.academy.Academy.inTraining(p)) {
         return;
      }
      LicenceStore store = LicenceStore.get(p.server);
      LicenceStore.Hunter h = store.hunter(p.getUUID());
      int period = LicenceTime.period(p.level());
      Verdict v = penalise(p, h, h.season(period), rule, Violation.UNTAGGED, mode);
      String kind = rule.tag() != null ? rule.tag().key() : rule.stamp().key();
      h.log(new FilledTag(kind, rule.species(), rule.title(), period, JournalService.stamp(p.serverLevel()), place(p.serverLevel(), deer.blockPosition()),
         deer.massKg() * 10, 0, p.getGameProfile().getName(), 1));
      store.setDirty();
      mark.putBoolean("poached", v.poached());
      mark.putBoolean("seized", v.confiscate());
      mark.putString("v", Violation.UNTAGGED.key());
      deer.getPersistentData().put(KEY, mark);
   }

   /** Token fine for a violation (before the config multiplier). */
   public static int fine(Regulations.Rule rule, Violation v, boolean strict) {
      int base;
      if (rule.group() == Regulations.Group.BIRD) {
         base = strict ? 8 : 3;
      } else {
         base = strict ? rule.tag().tokens * 2 : 8;
      }
      if (v == Violation.CLOSED || v == Violation.SUSPENDED || v == Violation.UNTAGGED) {
         base = base * 3 / 2;
      }
      return base;
   }

   // ============================================================================================ inventory

   /** Slot of a valid (issued to this hunter, this season) permit of that item, or -1. */
   /** [1.1.6] does the hunter carry an unfilled tag of this kind, valid this licence season */
   public static boolean carries(ServerPlayer p, Regulations.TagKind k) {
      return find(p, LicenceContent.tag(k), LicenceTime.period(p.level())) >= 0;
   }

   static int find(ServerPlayer p, net.minecraft.world.item.Item item, int period) {
      Inventory inv = p.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         if (s.is(item)) {
            PermitData d = s.get(LicenceContent.PERMIT.get());
            if (d != null && d.validFor(p.getUUID(), period)) {
               return i;
            }
         }
      }
      return -1;
   }

   /** Notches one tag out of the stack in {@code slot} and hands the hunter the filled tag. */
   private static void fill(ServerPlayer p, int slot, FilledTag record, Regulations.TagKind kind) {
      Inventory inv = p.getInventory();
      inv.getItem(slot).shrink(1);
      ItemStack filled = new ItemStack(LicenceContent.FILLED_TAG.get());
      filled.set(LicenceContent.FILLED.get(), record);
      filled.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(kind.ordinal() + 1));
      if (!inv.add(filled)) {
         ItemEntity ie = p.drop(filled, false);
         if (ie != null) {
            ie.setNoPickUpDelay();
         }
      }
      inv.setChanged();
      p.level().playSound(null, p.getX(), p.getY(), p.getZ(), LicenceContent.SND_PUNCH.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
   }

   static String place(ServerLevel level, BlockPos pos) {
      String biome = level.getBiome(pos).unwrapKey().map(k -> pretty(k.location().getPath())).orElse("");
      return pos.getX() + ", " + pos.getZ() + (biome.isEmpty() ? "" : " · " + biome);
   }

   static String pretty(String path) {
      String s = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
      StringBuilder b = new StringBuilder();
      boolean up = true;
      for (char c : s.toCharArray()) {
         b.append(up ? Character.toUpperCase(c) : c);
         up = c == ' ';
      }
      return b.toString();
   }

   // ============================================================================================ camp meals

   private static void fedForTheField(ServerPlayer p) {
      try {
         if (com.formaworks.frontierhunts.campcook.MealBuffs.active(p) != null) {
            JournalApi.count(p, "campcook.fed_take", 1);
         }
      } catch (RuntimeException ignored) {
      }
   }
}
