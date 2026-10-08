package com.formaworks.frontierhunts.hunts;

import com.formaworks.frontierhunts.expedition.CampaignProgress;
import com.formaworks.frontierhunts.expedition.ExpeditionLedger;
import com.formaworks.frontierhunts.expedition.ExpeditionService;
import com.formaworks.frontierhunts.expedition.HuntingCalendar;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.journal.Checklist;
import com.formaworks.frontierhunts.journal.HuntBridge;
import com.formaworks.frontierhunts.journal.HunterRecord;
import com.formaworks.frontierhunts.progression.AssignmentService;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * [hunts] Server entry points of the species hunts, called from a few hook lines in the journal (each marked
 * {@code // [hunts]}) and from {@link HuntEvents}: the field situation is captured on the animal at the first hit of a
 * wound, a take (deer field-dressed / wildlife killed, credited to the hunter exactly like the journal does) becomes a
 * {@link HuntEvent}, and {@link HuntTracker} turns it into milestone progress, contract progress and assignment
 * progress. Every entry point is wrapped so a hunts problem can never break a hit, a harvest or a photo.
 */
public final class HuntHooks {
   private static final Logger LOG = LogUtils.getLogger();
   /** journal counter that marks the one-time carry-over of older journal progress */
   public static final String MIGRATED = "hunt~v";

   private HuntHooks() {
   }

   // ============================================================================================ hits

   /**
    * A hunter's projectile hit an animal (deer: JournalHooks.deerHit, wildlife: JournalHooks.wildlifeHit). The first hit
    * of a wound stores where the hunter was and what led up to it; every hit updates the weapon, distance and the
    * wing-shot / charge state.
    */
   public static void hit(ServerPlayer p, LivingEntity animal, Entity projectile, boolean vital, boolean calm, double distance) {
      try {
         if (!HuntsConfig.enabled() || !(animal.level() instanceof ServerLevel level) || HuntBridge.record(p) == null) {
            return;
         }
         long now = level.getGameTime();
         CompoundTag data = animal.getPersistentData();
         CompoundTag c = data.getCompound(HuntContext.CTX);
         if (!HuntContext.stampBy(c, p.getUUID(), now, HuntContext.SAME_WOUND) || c.getBoolean("taken")) {
            c = new CompoundTag();
            c.putUUID("by", p.getUUID());
            c.putLong("t", now);
            int f = 0;
            f |= calm ? HuntEvent.UNAWARE : 0;
            f |= HuntContext.called(animal, p.getUUID(), now) ? HuntEvent.CALLED : 0;
            f |= HuntContext.decoy(level, animal, p, now) ? HuntEvent.DECOY : 0;
            f |= HuntContext.baited(level, animal, p.getUUID(), now) ? HuntEvent.BAIT : 0;
            f |= HuntContext.inStand(p) ? HuntEvent.STAND : 0;
            f |= !HuntContext.inStand(p) && HuntContext.inBlind(p) ? HuntEvent.BLIND : 0;
            f |= HuntContext.glassed(animal, p.getUUID(), now) ? HuntEvent.GLASSED : 0;
            f |= HuntContext.onFoot(p) ? HuntEvent.ON_FOOT : 0;
            f |= HuntContext.rut(animal) ? HuntEvent.RUT : 0;
            f |= HuntContext.snow(level, animal) ? HuntEvent.SNOW : 0;
            f |= HuntContext.water(level, animal) ? HuntEvent.WATER : 0;
            f |= animal.getY() >= 100.0 ? HuntEvent.HIGH : 0;
            f |= HuntContext.snowCamo(p) ? HuntEvent.SNOW_CAMO : 0;
            c.putInt("f", f);
            c.putInt("herd", HuntContext.herd(level, animal));
            c.putInt("tod", HuntContext.timeOfDay(level));
            c.putInt("mo", HuntContext.month(level));
            c.putLong("day", HuntContext.day(level));
            c.putInt("n", 0);
         }
         c.putInt("n", Math.min(1000, c.getInt("n") + 1));
         c.putInt("g", HuntContext.weapon(projectile).ordinal());
         c.putDouble("d", Double.isFinite(distance) ? Math.clamp(distance, 0.0, 1000.0) : 0.0);
         int lf = 0;
         if (animal instanceof WildlifeMob m) {
            if (m.species.bird && !m.onGround() && !m.isInWater()) {
               lf |= HuntEvent.FLYING;
            }
            if (m.behavior() == WildlifeMob.WARN && m.getTarget() == p && distance <= 24.0) {
               lf |= HuntEvent.CHARGING;
            }
         }
         c.putInt("lf", lf);
         if ((c.getInt("f") & HuntEvent.HOUND) == 0 && HuntContext.hound(p, animal)) {
            c.putInt("f", c.getInt("f") | HuntEvent.HOUND);
         }
         data.put(HuntContext.CTX, c);
      } catch (RuntimeException e) {
         LOG.warn("Frontier Hunts hunts: hit capture failed", e);
      }
   }

   // ============================================================================================ takes

   /** JournalHooks.deerHarvest: a deer / elk / moose field-dressed, credited to {@code hunter} (the shooter). */
   public static void deerTaken(ServerPlayer hunter, Whitetail deer, String region, double shotDistance, int trailM) {
      try {
         boolean clean = "HEART".equals(region) || "DOUBLE_LUNG".equals(region) || "LUNG".equals(region);
         taken(hunter, deer, deer.species().id, clean, shotDistance, trailM, deer.massKg(), deer.traits().buck(), deer.traits().totalPoints());
      } catch (RuntimeException e) {
         LOG.warn("Frontier Hunts hunts: deer take failed", e);
      }
   }

   /** JournalHooks.wildlifeDeath: a wildlife animal this hunter shot died (on the spot or after a run). */
   public static void wildlifeTaken(ServerPlayer hunter, WildlifeMob mob, String zone, double distance, int trailM, double kg) {
      try {
         boolean clean = "HEART".equals(zone) || "DOUBLE_LUNG".equals(zone) || "LUNG".equals(zone);
         taken(hunter, mob, mob.species.id, clean, distance, trailM, kg, false, 0);
      } catch (RuntimeException e) {
         LOG.warn("Frontier Hunts hunts: wildlife take failed", e);
      }
   }

   private static void taken(ServerPlayer p, LivingEntity a, String sp, boolean clean, double distance, int trailM, double kg, boolean male, int points) {
      if (!HuntsConfig.enabled() || HuntBook.of(sp) == null || !(a.level() instanceof ServerLevel level)) {
         return;
      }
      if (com.formaworks.frontierhunts.licence.Tagging.poached(a)) {
         return; // [licence] only legally taken (tagged) animals count for the species hunts
      }
      CompoundTag data = a.getPersistentData();
      CompoundTag c = data.getCompound(HuntContext.CTX);
      if (c.getBoolean("taken")) {
         return;
      }
      boolean ours = c.hasUUID("by") && c.getUUID("by").equals(p.getUUID());
      int flags = ours ? c.getInt("f") | c.getInt("lf") : (HuntContext.onFoot(p) ? HuntEvent.ON_FOOT : 0);
      if (clean) {
         flags |= HuntEvent.CLEAN;
         if (ours && c.getInt("n") == 1) {
            flags |= HuntEvent.ONE_SHOT;
         }
      } else {
         flags &= ~HuntEvent.CHARGING; // a charge only counts when the stopping shot was clean
      }
      if (trailM > 0) {
         flags |= HuntEvent.RECOVERED;
      }
      if (male) {
         flags |= HuntEvent.MALE;
      }
      if ((flags & HuntEvent.HOUND) == 0 && HuntContext.hound(p, a)) {
         flags |= HuntEvent.HOUND;
      }
      HuntEvent.Gun gun = ours ? HuntEvent.Gun.values()[Math.clamp(c.getInt("g"), 0, HuntEvent.Gun.values().length - 1)] : HuntEvent.Gun.NONE;
      HuntEvent e = HuntEvent.of(HuntEvent.Kind.TAKE, sp)
         .flag(flags)
         .gun(gun)
         .distance(distance)
         .kg(kg)
         .points(points)
         .herd(ours ? c.getInt("herd") : 1)
         .month(ours ? c.getInt("mo") : HuntContext.month(level))
         .time(ours ? c.getInt("tod") : HuntContext.timeOfDay(level))
         .day(ours ? c.getLong("day") : HuntContext.day(level))
         .trail(trailM)
         .build();
      c.putBoolean("taken", true);
      data.put(HuntContext.CTX, c);
      fire(p.server, p.getUUID(), p, e, a.getUUID());
   }

   // ============================================================================================ scouting

   /** JournalEvents.sightings: an animal seen for the first time this session, within 40 m in clear sight. */
   public static void seen(ServerPlayer p, LivingEntity a, String sp, double distance) {
      try {
         if (!HuntsConfig.enabled() || HuntBook.of(sp) == null || !(a.level() instanceof ServerLevel level)) {
            return;
         }
         HuntEvent e = HuntEvent.of(HuntEvent.Kind.SEEN, sp).distance(distance).time(HuntContext.timeOfDay(level)).month(HuntContext.month(level))
            .day(HuntContext.day(level)).build();
         fire(p.server, p.getUUID(), p, e, a.getUUID());
      } catch (RuntimeException ex) {
         LOG.warn("Frontier Hunts hunts: sighting failed", ex);
      }
   }

   /** JournalHooks.trailcamPhoto: a trail camera photographed animals for {@code owner} (who may be offline). */
   public static void photo(ServerLevel level, UUID owner, List<LivingEntity> subjects) {
      try {
         if (!HuntsConfig.enabled() || owner == null || subjects == null) {
            return;
         }
         MinecraftServer server = level.getServer();
         ServerPlayer p = server.getPlayerList().getPlayer(owner);
         Set<String> done = new HashSet<>();
         for (LivingEntity a : subjects) {
            String sp = HuntContext.species(a);
            if (sp == null || HuntBook.of(sp) == null || !done.add(sp)) {
               continue;
            }
            // [1.1.6] a buck or bull anywhere in the frame makes it a buck photo (buck census, freak sign)
            boolean male = false;
            for (LivingEntity o : subjects) {
               male |= sp.equals(HuntContext.species(o)) && maleDeer(o);
            }
            HuntEvent e = HuntEvent.of(HuntEvent.Kind.PHOTO, sp).flag(HuntEvent.MALE, male).time(HuntContext.timeOfDay(level)).month(HuntContext.month(level))
               .day(HuntContext.day(level)).herd(HuntContext.herd(level, a)).build();
            fire(server, owner, p, e, UUID.randomUUID());
         }
      } catch (RuntimeException ex) {
         LOG.warn("Frontier Hunts hunts: photo failed", ex);
      }
   }

   /** [1.1.6] a whitetail buck, bull elk or bull moose (antlers are what you see through glass or on a camera) */
   static boolean maleDeer(LivingEntity a) {
      try {
         return a instanceof com.formaworks.frontierhunts.hunting.Whitetail w && w.traits().buck();
      } catch (RuntimeException e) {
         return false;
      }
   }

   /** HuntEvents: the hunter held binoculars / a rangefinder on this animal. Stamps it (for "glassed first") and counts. */
   static void glassed(ServerPlayer p, LivingEntity a, double distance) {
      String sp = HuntContext.species(a);
      if (sp == null || HuntBook.of(sp) == null || !(a.level() instanceof ServerLevel level)) {
         return;
      }
      stamp(p, a, distance, level.getGameTime());
      boolean hunting = a instanceof WildlifeMob m && (m.hunting() || m.behavior() == WildlifeMob.TEAR);
      HuntEvent e = HuntEvent.of(HuntEvent.Kind.GLASS, sp).distance(distance).herd(HuntContext.herd(level, a)).flag(HuntEvent.HUNTING, hunting)
         .flag(HuntEvent.MALE, maleDeer(a)).time(HuntContext.timeOfDay(level)).month(HuntContext.month(level)).day(HuntContext.day(level)).build();
      fire(p.server, p.getUUID(), p, e, a.getUUID());
   }

   /** Glassed again (already counted this session): refresh the stamp, and count it if it is now hunting (cheetah chase). */
   static void restamp(ServerPlayer p, LivingEntity a, double distance) {
      if (!(a.level() instanceof ServerLevel level) || HuntContext.species(a) == null) {
         return;
      }
      stamp(p, a, distance, level.getGameTime());
      if (a instanceof WildlifeMob m && (m.hunting() || m.behavior() == WildlifeMob.TEAR)) {
         HuntEvent e = HuntEvent.of(HuntEvent.Kind.GLASS, m.species.id).distance(distance).herd(HuntContext.herd(level, a)).flag(HuntEvent.HUNTING)
            .time(HuntContext.timeOfDay(level)).month(HuntContext.month(level)).day(HuntContext.day(level)).build();
         fire(p.server, p.getUUID(), p, e, a.getUUID());
      }
   }

   private static void stamp(ServerPlayer p, LivingEntity a, double distance, long now) {
      CompoundTag data = a.getPersistentData();
      ListTag l = data.getList(HuntContext.GLASS, Tag.TAG_COMPOUND);
      ListTag out = new ListTag();
      CompoundTag mine = new CompoundTag();
      mine.putUUID("by", p.getUUID());
      mine.putLong("t", now);
      mine.putDouble("d", distance);
      out.add(mine);
      for (int i = 0; i < l.size() && out.size() < 4; i++) {
         CompoundTag t = l.getCompound(i);
         if (t.hasUUID("by") && !t.getUUID("by").equals(p.getUUID()) && now - t.getLong("t") <= HuntContext.GLASS_WINDOW) {
            out.add(t);
         }
      }
      data.put(HuntContext.GLASS, out);
   }

   /** HuntEvents: a grouse burst into the air close to the hunter. */
   static void flushed(ServerPlayer p, WildlifeMob bird, double distance) {
      if (!(bird.level() instanceof ServerLevel level)) {
         return;
      }
      HuntEvent e = HuntEvent.of(HuntEvent.Kind.FLUSH, bird.species.id).distance(distance).time(HuntContext.timeOfDay(level)).month(HuntContext.month(level))
         .day(HuntContext.day(level)).build();
      fire(p.server, p.getUUID(), p, e, bird.getUUID());
   }

   // ============================================================================================ applying an event

   static void fire(MinecraftServer server, UUID id, ServerPlayer online, HuntEvent e, UUID animal) {
      HunterRecord r = online != null ? HuntBridge.record(online) : HuntBridge.stored(server, id);
      if (r == null) {
         return;
      }
      HuntTracker.Result res = HuntTracker.evaluate(e, r::get);
      List<Checklist.Entry> completed = HuntBridge.apply(server, r, online, res.updates());
      for (Checklist.Entry ce : completed) {
         HuntBook.Milestone m = HuntBook.byId(ce.id());
         if (m != null) {
            HuntRewards.completed(server, id, online, m);
         }
      }
      if (online != null) {
         com.formaworks.frontierhunts.freak.FreakQuest.event(online, e); // [1.1.6] the freak quests
      }
      if (online != null && !res.tags().isEmpty()) {
         contracts(online, e, res.tags());
         try {
            AssignmentService.hunt(online, animal, res.tags());
         } catch (RuntimeException ex) {
            LOG.debug("Frontier Hunts hunts: assignment update failed", ex);
         }
      }
   }

   /** Moves the running species contract of the hunter or of a party member nearby (the expedition shares it). */
   private static void contracts(ServerPlayer p, HuntEvent e, Set<String> tags) {
      try {
         ExpeditionLedger ledger = ExpeditionLedger.get(p.serverLevel());
         long now = p.level().getGameTime();
         Set<String> sent = new LinkedHashSet<>();
         List<ServerPlayer> members = new ArrayList<>(ExpeditionService.members(p));
         if (members.isEmpty()) {
            members.add(p);
         }
         for (ServerPlayer m : members) {
            ExpeditionLedger.Hunter h = ledger.hunter(m.getUUID());
            HuntBook.Contract c = HuntContracts.at(h.contract);
            if (c != null && tags.contains(c.species() + ":" + c.tag()) && "active".equals(CampaignProgress.contractStatus(h, now)) && sent.add(c.event())) {
               ExpeditionService.record(p, c.event(), c.species(), 1, Math.clamp(e.distance, 0.0, 1000.0));
            }
         }
      } catch (RuntimeException ex) {
         LOG.debug("Frontier Hunts hunts: contract update failed", ex);
      }
   }

   /** CampaignProgress.accept: species contracts can be taken only while they are on this month's board. */
   public static boolean contractOffered(int index) {
      if (index < HuntContracts.BASE) {
         return true;
      }
      try {
         MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
         if (s == null) {
            return true;
         }
         HuntingCalendar.Date d = HuntingCalendar.date(s.overworld());
         int half = HuntContracts.half(d.day(), d.daysPerMonth());
         boolean grace = d.day() == 1 || HuntContracts.half(d.day() - 1, d.daysPerMonth()) != half;
         return HuntContracts.offered(index, d.month(), half, grace);
      } catch (RuntimeException e) {
         return true;
      }
   }

   // ============================================================================================ carry-over

   /**
    * One time per hunter: carries older journal progress into the hunts - species seen and photographed count for the
    * sighting / camera milestones, a species already taken counts for its first-take milestone, and the heaviest wildlife
    * of a species for its weight milestone. Antler points were never recorded, so deer quality tiers start fresh.
    */
   public static int migrate(ServerPlayer p) {
      HunterRecord r = HuntBridge.record(p);
      if (r == null || r.get(MIGRATED) >= 1) {
         return 0;
      }
      int carried = 0;
      MinecraftServer server = p.server;
      for (HuntBook.Hunt h : HuntBook.hunts()) {
         HunterRecord.SpeciesLog log = r.species.get(h.species());
         if (log == null) {
            continue;
         }
         List<HuntTracker.Update> ups = HuntTracker.carryOver(h, log.seen, log.photos, log.harvested, log.bestWeight, r::get);
         if (!ups.isEmpty()) {
            for (Checklist.Entry ce : HuntBridge.apply(server, r, null, ups)) {
               HuntBook.Milestone m = HuntBook.byId(ce.id());
               if (m != null) {
                  carried++;
                  HuntRewards.completed(server, p.getUUID(), null, m);
               }
            }
         }
      }
      HuntBridge.apply(server, r, null, List.of(new HuntTracker.Update(MIGRATED, 1)));
      if (carried > 0) {
         HuntBridge.note(p, "@hunts.frontierhunts.note.migrated|" + carried, (byte)6);
      }
      HuntBridge.dirty(server, r);
      return carried;
   }
}
