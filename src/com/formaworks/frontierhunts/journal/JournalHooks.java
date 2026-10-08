package com.formaworks.frontierhunts.journal;

import com.formaworks.frontierhunts.camps.CampHooks;
import com.formaworks.frontierhunts.camps.CampRegistry;
import com.formaworks.frontierhunts.camps.HarvestRecord;
import com.formaworks.frontierhunts.camps.Quarry;
import com.formaworks.frontierhunts.camps.RecordBook;
import com.formaworks.frontierhunts.expedition.HuntProjectile;
import com.formaworks.frontierhunts.hunting.DeerAnatomy;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.FieldArrow;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.tracking.TrailMark;
import com.formaworks.frontierhunts.tracking.TrailStore;
import com.formaworks.frontierhunts.vital.WildlifeVitals;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * [journal] Entry points called from small hook lines in other systems (each marked {@code // [journal]}) and from
 * {@link JournalEvents}. Every hook is wrapped so a journal problem can never break a hit, a harvest or a photo.
 */
public final class JournalHooks {
   private static final Logger LOG = LogUtils.getLogger();
   static final String HIT = "frontierhunts_journal_hit";
   static final String KILLED = "frontierhunts_journal_killed";
   static final String CALL = "frontierhunts_journal_call";
   /** a later hit within this many ticks keeps the first hit position as the start of the trail */
   private static final long SAME_WOUND = 6000L;
   /** a wounded animal not recovered within one in-game day counts as lost */
   static final long LOST_AFTER = 24000L;

   private JournalHooks() {
   }

   // ============================================================================================ deer / elk / moose

   /**
    * Whitetail.projectileHit, after the hit was applied. {@code dropped}: the vital-drop rule fired (heart/lungs),
    * {@code accepted}: the hurt went through, {@code calm}: the animal's alertness right before the shot.
    */
   public static void deerHit(Whitetail deer, DamageSource source, Entity projectile, DeerAnatomy.Region region, boolean dropped, boolean accepted,
      float calm, double distance) {
      try {
         if (!accepted || !(source.getEntity() instanceof ServerPlayer p) || !(deer.level() instanceof ServerLevel level)) {
            return;
         }
         HunterRecord r = JournalService.record(p);
         if (r == null) {
            return;
         }
         boolean bow = projectile instanceof FieldArrow || projectile instanceof HuntProjectile hp && hp.kind().bow;
         boolean vital = region == DeerAnatomy.Region.HEART || region == DeerAnatomy.Region.DOUBLE_LUNG || region == DeerAnatomy.Region.LUNG;
         JournalService.count(p.server, r, p, Stat.HITS, 1);
         if (vital) {
            JournalService.count(p.server, r, p, Stat.VITAL_HITS, 1);
         }
         long now = level.getGameTime();
         CompoundTag data = deer.getPersistentData();
         CompoundTag hit = data.getCompound(HIT);
         boolean sameWound = hit.hasUUID("by") && hit.getUUID("by").equals(p.getUUID()) && now - hit.getLong("t") < SAME_WOUND && now >= hit.getLong("t");
         if (!sameWound) {
            hit = new CompoundTag();
            hit.putDouble("x", deer.getX());
            hit.putDouble("y", deer.getY());
            hit.putDouble("z", deer.getZ());
            hit.putUUID("by", p.getUUID());
            hit.putLong("t", now);
            hit.putBoolean("calm", calm < 0.3F);
         }
         hit.putBoolean("bow", bow);
         hit.putDouble("d", Double.isFinite(distance) ? Math.clamp(distance, 0.0, 1000.0) : 0.0);
         data.put(HIT, hit);
         com.formaworks.frontierhunts.hunts.HuntHooks.hit(p, deer, projectile, vital, calm < 0.3F, hit.getDouble("d")); // [hunts] field situation for the species hunts
         String sp = deer.species().id;
         if (dropped) {
            killed(p, r, deer, sp, true, bow, hit.getDouble("d"), hit.getBoolean("calm"), region.name());
            r.wounds.remove(deer.getUUID());
         } else if (!r.wounds.containsKey(deer.getUUID())) {
            wound(r, deer, sp, hit, now);
         }
      } catch (RuntimeException e) {
         LOG.warn("Frontier Hunts journal: deer hit hook failed", e);
      }
   }

   private static void wound(HunterRecord r, LivingEntity animal, String species, CompoundTag hit, long now) {
      HunterRecord.Wound w = new HunterRecord.Wound();
      w.x = hit.getDouble("x");
      w.y = hit.getDouble("y");
      w.z = hit.getDouble("z");
      w.time = now;
      w.species = species;
      r.wounds.put(animal.getUUID(), w);
      while (r.wounds.size() > HunterRecord.MAX_WOUNDS) {
         r.wounds.remove(r.wounds.keySet().iterator().next());
      }
   }

   /** The animal is down by this hunter's shot (once per animal): kill counters and Marksmanship / Stalking XP. */
   static void killed(ServerPlayer p, HunterRecord r, LivingEntity animal, String species, boolean clean, boolean bow, double distance, boolean unaware,
      String region) {
      CompoundTag data = animal.getPersistentData();
      if (data.getBoolean(KILLED)) {
         return;
      }
      data.putBoolean(KILLED, true);
      MinecraftServer s = p.server;
      JournalService.count(s, r, p, Stat.KILLS, 1);
      int metres = (int)Math.round(distance);
      JournalService.count(s, r, p, Stat.SHOT_SUM_DM, (int)Math.round(distance * 10.0));
      JournalService.count(s, r, p, Stat.LONGEST_SHOT, metres);
      JournalService.seasonBest(r, "~shot", metres);
      HunterRecord.SpeciesLog log = r.species(species);
      log.longestShot = Math.max(log.longestShot, (int)Math.round(distance * 10.0));
      if (bow) {
         JournalService.count(s, r, p, Stat.BOW_KILLS, 1);
         if (distance >= 30.0) {
            JournalService.count(s, r, p, Stat.BOW_LONG, 1);
         }
      } else {
         JournalService.count(s, r, p, Stat.RIFLE_KILLS, 1);
      }
      if (clean) {
         JournalService.count(s, r, p, Stat.CLEAN_KILLS, 1);
         // more for a longer ethical shot: bows reward 10-37 m, rifles 30-270 m
         int bonus = bow ? (int)Math.min(40.0, Math.max(0.0, distance - 10.0) * 1.5) : (int)Math.min(60.0, Math.max(0.0, distance - 30.0) * 0.25);
         JournalService.xp(p, Skill.MARKSMANSHIP, 40 + bonus);
      } else {
         JournalService.xp(p, Skill.MARKSMANSHIP, 12);
      }
      if (unaware) {
         JournalService.count(s, r, p, Stat.UNAWARE_KILLS, 1);
         JournalService.xp(p, Skill.STALKING, 20);
      }
   }

   /**
    * Whitetail.harvest, after the trophy and the camps record: field dressing (Butchery), the kill if it bled out
    * (recovery = Tracking), the species log, trophy counters, the field note, camp assists and the board.
    */
   public static void deerHarvest(ServerPlayer dresser, Whitetail deer, ItemStack trophy, UUID shooter, double shotDistance, String region, long shotAt) {
      try {
         MinecraftServer server = dresser.server;
         ServerLevel level = dresser.serverLevel();
         HunterRecord dr = JournalService.record(dresser);
         int mass = deer.massKg();
         if (dr != null) {
            JournalService.count(server, dr, dresser, Stat.HARVESTS, 1);
            JournalService.xp(dresser, Skill.BUTCHERY, 25 + Math.min(30, mass / 8));
         }
         UUID hunterId = shooter != null ? shooter : dresser.getUUID();
         ServerPlayer hunter = server.getPlayerList().getPlayer(hunterId);
         HunterRecord r = hunter == null ? null : JournalService.record(hunter);
         if (r == null) {
            return;
         }
         String sp = deer.species().id;
         CompoundTag hit = deer.getPersistentData().getCompound(HIT);
         boolean bow = hit.getBoolean("bow");
         boolean dropRegion = "HEART".equals(region) || "DOUBLE_LUNG".equals(region) || "LUNG".equals(region);
         double trail = 0.0;
         if (hit.contains("x")) {
            trail = Math.sqrt(deer.distanceToSqr(hit.getDouble("x"), hit.getDouble("y"), hit.getDouble("z")));
         }
         boolean wasKilled = deer.getPersistentData().getBoolean(KILLED);
         if (!wasKilled) {
            killed(hunter, r, deer, sp, false, bow, shotDistance, hit.getBoolean("calm"), region);
         }
         boolean recovered = !dropRegion || trail >= 12.0;
         int trailM = (int)Math.round(trail);
         if (recovered && trailM >= 5) {
            JournalService.count(server, r, hunter, Stat.RECOVERIES, 1);
            JournalService.count(server, r, hunter, Stat.TRAIL_M, trailM);
            JournalService.count(server, r, hunter, Stat.LONGEST_TRAIL, trailM);
            JournalService.xp(hunter, Skill.TRACKING, 30 + Math.min(50, trailM / 2));
         }
         r.wounds.remove(deer.getUUID());
         DeerTraits t = deer.traits();
         Quarry q = Quarry.find(sp);
         int score = t.buck() ? t.trophyScore() : 0;
         harvestLog(hunter, r, sp, score, mass * 10);
         if ("whitetail".equals(sp) && score > 0) {
            JournalService.count(server, r, hunter, Stat.BEST_WT_SCORE, score);
            JournalService.seasonBest(r, "~score", score);
         }
         long calledAt = deer.getPersistentData().getLong(CampHooks.CALLED_AT);
         if (calledAt > 0L && shotAt - calledAt >= 0L && shotAt - calledAt < 6000L) {
            JournalService.count(server, r, hunter, Stat.CALLED_KILLS, 1);
         }
         // field note
         StringBuilder b = new StringBuilder();
         if (t.buck() && t.totalPoints() > 0) {
            b.append(t.totalPoints()).append("-point ").append(q == null ? sp : q.title.toLowerCase(Locale.ROOT)).append(' ').append(q == null ? "buck" : q.male);
         } else {
            b.append(q == null ? sp : q.title).append(' ').append(t.buck() ? (q == null ? "buck" : q.male) : (q == null ? "doe" : q.female));
         }
         b.append(", ").append(mass).append(" kg, ").append(Math.round(shotDistance)).append(" m, ").append(pretty(region)).append('.');
         if (score > 0) {
            b.append(" Scored ").append(score).append("\".");
         }
         if (recovered && trailM >= 5) {
            b.append(" Recovered after ").append(trailM).append(" m of trail.");
         } else {
            b.append(" Dropped where it stood.");
         }
         if (bow) {
            b.append(" Bow.");
         }
         r.note(JournalService.stamp(level), cap(b.toString()), (byte)0);
         JournalService.dirty(server, r);
         com.formaworks.frontierhunts.hunts.HuntHooks.deerTaken(hunter, deer, region, shotDistance, recovered && trailM >= 5 ? trailM : 0); // [hunts] species hunt milestones
         campmates(hunter, deer.position());
         board(hunter, r);
      } catch (RuntimeException e) {
         LOG.warn("Frontier Hunts journal: harvest hook failed", e);
      }
   }

   private static String cap(String s) {
      return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
   }

   static String pretty(String region) {
      return region == null ? "unknown" : region.toLowerCase(Locale.ROOT).replace('_', ' ');
   }

   /** Species log + species counters for a harvested animal (weight in kg x 10). */
   static void harvestLog(ServerPlayer p, HunterRecord r, String sp, int score, int kg10) {
      MinecraftServer server = p.server;
      HunterRecord.SpeciesLog log = r.species(sp);
      boolean first = log.harvested == 0;
      log.harvested++;
      log.bestScore = Math.max(log.bestScore, score);
      log.bestWeight = Math.max(log.bestWeight, kg10);
      if (first) {
         log.firstDate = JournalService.date(p.serverLevel());
      }
      JournalService.count(server, r, p, Stat.species(sp, "h"), 1);
      JournalService.seasonBest(r, "~kg10", kg10);
      int taken = 0;
      for (HunterRecord.SpeciesLog l : r.species.values()) {
         if (l.harvested > 0) {
            taken++;
         }
      }
      JournalService.count(server, r, p, Stat.SPECIES_TAKEN, taken);
      JournalService.dirty(server, r);
   }

   private static void campmates(ServerPlayer hunter, Vec3 at) {
      try {
         CampRegistry.Camp camp = CampRegistry.get(hunter.server).campOf(hunter.getUUID());
         if (camp == null) {
            return;
         }
         for (UUID id : camp.memberIds()) {
            if (id.equals(hunter.getUUID())) {
               continue;
            }
            ServerPlayer m = hunter.server.getPlayerList().getPlayer(id);
            if (m != null && m.level() == hunter.level() && m.position().distanceToSqr(at) < 96.0 * 96.0) {
               JournalService.count(m, Stat.ASSISTS, 1);
               JournalService.xp(m, Skill.WOODCRAFT, 25);
            }
         }
      } catch (RuntimeException e) {
         LOG.debug("Frontier Hunts journal: camp lookup failed", e);
      }
   }

   private static void board(ServerPlayer hunter, HunterRecord r) {
      try {
         RecordBook book = RecordBook.get(hunter.server);
         for (RecordBook.Category c : RecordBook.Category.values()) {
            for (HarvestRecord h : book.top(c, false, 10)) {
               if (hunter.getUUID().equals(h.hunter)) {
                  JournalService.count(hunter.server, r, hunter, Stat.BOARD, 1 - Math.min(1, r.get(Stat.BOARD)));
                  return;
               }
            }
         }
      } catch (RuntimeException e) {
         LOG.debug("Frontier Hunts journal: record book lookup failed", e);
      }
   }

   /** Whitetail.approachCall accepted a call made at {@code from}: the caller (nearest player within 6 m) called it in. */
   public static void called(Whitetail deer, Vec3 from) {
      try {
         if (!(deer.level() instanceof ServerLevel level) || from == null) {
            return;
         }
         Player near = level.getNearestPlayer(from.x, from.y, from.z, 6.0, false);
         if (!(near instanceof ServerPlayer p)) {
            return;
         }
         long now = level.getGameTime();
         CompoundTag c = deer.getPersistentData().getCompound(CALL);
         if (c.hasUUID("by") && c.getUUID("by").equals(p.getUUID()) && now - c.getLong("t") < 6000L && now >= c.getLong("t")) {
            return;
         }
         c.putUUID("by", p.getUUID());
         c.putLong("t", now);
         deer.getPersistentData().put(CALL, c);
         JournalService.count(p, Stat.CALLS, 1);
         JournalService.xp(p, Skill.STALKING, 20);
      } catch (RuntimeException e) {
         LOG.warn("Frontier Hunts journal: call hook failed", e);
      }
   }

   // ============================================================================================ wildlife (from events)

   static void wildlifeHit(ServerPlayer p, WildlifeMob mob, WildlifeVitals.Hit hit, Entity projectile, boolean dead, boolean calm) {
      HunterRecord r = JournalService.record(p);
      if (r == null) {
         return;
      }
      MinecraftServer s = p.server;
      JournalService.count(s, r, p, Stat.HITS, 1);
      boolean vital = hit != null && hit.zone().vital;
      if (vital) {
         JournalService.count(s, r, p, Stat.VITAL_HITS, 1);
      }
      boolean bow = projectile instanceof FieldArrow || projectile instanceof HuntProjectile hp && hp.kind().bow;
      long now = mob.level().getGameTime();
      CompoundTag data = mob.getPersistentData();
      CompoundTag h = data.getCompound(HIT);
      if (!(h.hasUUID("by") && h.getUUID("by").equals(p.getUUID()) && now - h.getLong("t") < SAME_WOUND && now >= h.getLong("t"))) {
         h = new CompoundTag();
         h.putDouble("x", mob.getX());
         h.putDouble("y", mob.getY());
         h.putDouble("z", mob.getZ());
         h.putUUID("by", p.getUUID());
         h.putLong("t", now);
         h.putBoolean("calm", calm);
      }
      h.putBoolean("bow", bow);
      h.putDouble("d", Math.sqrt(p.distanceToSqr(mob)));
      h.putString("zone", hit == null ? "FLESH" : hit.zone().name());
      data.put(HIT, h);
      com.formaworks.frontierhunts.hunts.HuntHooks.hit(p, mob, projectile, vital, calm, h.getDouble("d")); // [hunts] field situation for the species hunts
      if (!dead && !r.wounds.containsKey(mob.getUUID())) {
         wound(r, mob, mob.species.id, h, now);
      }
   }

   /** A wildlife animal died: credit the hunter who shot it (killer, or the hunter whose wound bled it out). */
   static void wildlifeDeath(WildlifeMob mob, DamageSource source) {
      CompoundTag h = mob.getPersistentData().getCompound(HIT);
      ServerPlayer p = source.getEntity() instanceof ServerPlayer sp ? sp : null;
      MinecraftServer server = mob.level().getServer();
      if (server == null) {
         return;
      }
      if (p == null && h.hasUUID("by") && mob.level().getGameTime() - h.getLong("t") < LOST_AFTER) {
         p = server.getPlayerList().getPlayer(h.getUUID("by"));
      }
      if (p == null || !h.hasUUID("by") || !h.getUUID("by").equals(p.getUUID())) {
         return; // only animals this hunter shot count
      }
      HunterRecord r = JournalService.record(p);
      if (r == null) {
         return;
      }
      String zone = h.getString("zone");
      boolean clean = zone.equals("HEART") || zone.equals("DOUBLE_LUNG") || zone.equals("LUNG");
      double trail = Math.sqrt(mob.distanceToSqr(h.getDouble("x"), h.getDouble("y"), h.getDouble("z")));
      boolean ran = !clean || trail >= 12.0;
      killed(p, r, mob, mob.species.id, clean && trail < 12.0, h.getBoolean("bow"), h.getDouble("d"), h.getBoolean("calm"), zone);
      r.wounds.remove(mob.getUUID());
      Quarry q = Quarry.find(mob.species.id);
      if (q == null) {
         return;
      }
      UUID id = mob.getUUID();
      double kg = q.weightFor(id.getMostSignificantBits() ^ id.getLeastSignificantBits()) * ((id.getLeastSignificantBits() & 1L) == 0L ? 1.0 : 0.82);
      int kg10 = (int)Math.round(kg * 10.0);
      harvestLog(p, r, q.id, 0, kg10);
      JournalService.count(server, r, p, Stat.HARVESTS, 1);
      JournalService.xp(p, Skill.BUTCHERY, 10 + Math.min(30, (int)(kg / 10.0)));
      int trailM = (int)Math.round(trail);
      boolean found = p.level() == mob.level() && p.distanceToSqr(mob) < 64.0 * 64.0;
      if (ran && trailM >= 5 && found) {
         JournalService.count(server, r, p, Stat.RECOVERIES, 1);
         JournalService.count(server, r, p, Stat.TRAIL_M, trailM);
         JournalService.count(server, r, p, Stat.LONGEST_TRAIL, trailM);
         JournalService.xp(p, Skill.TRACKING, 30 + Math.min(50, trailM / 2));
      }
      String w = kg10 >= 100 ? Math.round(kg) + " kg" : String.format(Locale.ROOT, "%.1f kg", kg);
      String text = q.title + ", " + w + ", " + Math.round(h.getDouble("d")) + " m, " + pretty(zone) + "."
         + (ran && trailM >= 5 ? (found ? " Followed it " + trailM + " m." : " It ran " + trailM + " m before it fell.") : " Dropped where it stood.");
      r.note(JournalService.stamp(p.serverLevel()), text, (byte)0);
      JournalService.dirty(server, r);
      com.formaworks.frontierhunts.hunts.HuntHooks.wildlifeTaken(p, mob, zone, h.getDouble("d"), ran && trailM >= 5 && found ? trailM : 0, kg); // [hunts] species hunt milestones
      campmates(p, mob.position());
      board(p, r);
   }

   // ============================================================================================ tracking

   /** TrailService.inspect, after a validated inspection: counters, Tracking XP, Trail Sense / Bloodhound reads. */
   public static void signRead(ServerPlayer p, TrailMark mark) {
      try {
         HunterRecord r = JournalService.record(p);
         if (r == null || mark == null) {
            return;
         }
         JournalEvents.Live l = JournalEvents.live(p);
         if (!l.readMarks.add(mark.id())) {
            return; // the same mark twice is not a new read
         }
         if (l.readMarks.size() > 96) {
            l.readMarks.remove(l.readMarks.iterator().next());
         }
         JournalService.count(p.server, r, p, Stat.SIGNS, 1);
         long now = p.serverLevel().getGameTime();
         if (now - l.lastSignXp >= 40L || now < l.lastSignXp) {
            l.lastSignXp = now;
            JournalService.xp(p, Skill.TRACKING, mark.blood() ? 5 : 4);
         }
         int mask = HunterSkills.mask(p);
         if (!Perk.TRAIL_SENSE.in(mask) && !Perk.BLOODHOUND.in(mask)) {
            return;
         }
         String dir = null;
         if (Perk.TRAIL_SENSE.in(mask)) {
            dir = heading(p.serverLevel(), mark, now);
         }
         String range = null;
         if (Perk.BLOODHOUND.in(mask) && mark.animal() != null && !TrailMark.UNKNOWN.equals(mark.animal()) && mark.age(now) < 6000L) {
            Entity e = p.serverLevel().getEntity(mark.animal());
            if (e instanceof LivingEntity le && le.isAlive() && e.distanceToSqr(mark.position()) < 128.0 * 128.0) {
               Vec3 d = e.position().subtract(mark.position());
               int m = (int)Math.max(10L, Math.round(Math.sqrt(d.x * d.x + d.z * d.z) / 10.0) * 10L);
               range = "~" + m + " m " + compass(d.x, d.z);
            }
         }
         if (dir != null || range != null) {
            Component msg = range != null
               ? Component.translatable("journal.frontierhunts.perk.read.range", range)
               : Component.translatable("journal.frontierhunts.perk.read.heading", dir);
            p.displayClientMessage(msg, true);
         }
      } catch (RuntimeException e) {
         LOG.warn("Frontier Hunts journal: sign hook failed", e);
      }
   }

   /** Which way the animal went from this mark: the next newer mark of the same animal nearby, else the print's facing. */
   private static String heading(ServerLevel level, TrailMark mark, long now) {
      if (mark.animal() != null && !TrailMark.UNKNOWN.equals(mark.animal())) {
         TrailMark best = null;
         for (TrailMark m : TrailStore.get(level).nearby(mark.position(), 16.0, 48, now)) {
            if (m.animal() != null && m.animal().equals(mark.animal()) && m.created() > mark.created() && m.position().distanceToSqr(mark.position()) > 1.0
               && (best == null || m.created() < best.created())) {
               best = m;
            }
         }
         if (best != null) {
            Vec3 d = best.position().subtract(mark.position());
            return compass(d.x, d.z);
         }
      }
      if (mark.print()) {
         double rad = Math.toRadians(mark.yaw());
         return compass(-Math.sin(rad), Math.cos(rad));
      }
      return null;
   }

   private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

   static String compass(double dx, double dz) {
      double a = Math.toDegrees(Math.atan2(dx, -dz));
      return COMPASS[(int)Math.floorMod(Math.round(a / 45.0), 8L)];
   }

   // ============================================================================================ trail camera / camps

   /** TrailCamera captured a photo for {@code owner} (may be offline): photo counters and new species. */
   public static void trailcamPhoto(ServerLevel level, UUID owner, List<LivingEntity> subjects) {
      try {
         if (owner == null || subjects == null || subjects.isEmpty()) {
            return;
         }
         MinecraftServer server = level.getServer();
         ServerPlayer p = server.getPlayerList().getPlayer(owner);
         if (p != null && (p.isSpectator() || p.isCreative() && !JournalConfig.countCreative())) {
            return;
         }
         HunterRecord r = ProgressStore.get(server).record(owner);
         boolean animal = false;
         int newSpecies = 0;
         for (LivingEntity e : subjects) {
            String sp = e instanceof Whitetail w ? w.species().id : (e instanceof WildlifeMob m && m.species != null ? m.species.id : null);
            if (sp == null || Quarry.find(sp) == null) {
               continue;
            }
            animal = true;
            HunterRecord.SpeciesLog log = r.species(sp);
            if (log.photos == 0) {
               newSpecies++;
            }
            log.photos++;
            JournalService.count(server, r, p, Stat.species(sp, "photo"), 1);
         }
         if (!animal) {
            return;
         }
         JournalService.count(server, r, p, Stat.PHOTOS, 1);
         int photographed = 0;
         for (HunterRecord.SpeciesLog l : r.species.values()) {
            if (l.photos > 0) {
               photographed++;
            }
         }
         JournalService.count(server, r, p, Stat.SPECIES_PHOTOGRAPHED, photographed);
         if (p != null) {
            JournalEvents.Live l = JournalEvents.live(p);
            long day = level.getGameTime() / 24000L;
            if (l.photoDay != day) {
               l.photoDay = day;
               l.photoXp = 0;
            }
            if (l.photoXp < 30) {
               l.photoXp += 3;
               JournalService.xp(p, Skill.TRACKING, 3);
            }
            if (newSpecies > 0) {
               JournalService.xp(p, Skill.TRACKING, 40 * newSpecies);
            }
         }
         JournalService.dirty(server, r);
         com.formaworks.frontierhunts.hunts.HuntHooks.photo(level, owner, subjects); // [hunts] trail-camera milestones
      } catch (RuntimeException e) {
         LOG.warn("Frontier Hunts journal: trail camera hook failed", e);
      }
   }

   /** GuidedService: a guided hunt was filled (both the client and the guide). */
   public static void guidedHunt(ServerPlayer p, boolean asGuide) {
      try {
         JournalService.count(p, Stat.GUIDED, 1);
         JournalService.xp(p, Skill.WOODCRAFT, asGuide ? 60 : 30);
      } catch (RuntimeException e) {
         LOG.warn("Frontier Hunts journal: guided hook failed", e);
      }
   }

   /** EventService: a hunter placed in a weekend event (1..3); may be offline. */
   public static void eventPlaced(MinecraftServer server, UUID id, int place) {
      try {
         ServerPlayer p = server.getPlayerList().getPlayer(id);
         HunterRecord r = ProgressStore.get(server).record(id);
         JournalService.count(server, r, p, Stat.EVENT_PODIUMS, 1);
         if (p != null) {
            JournalService.note(p, "@journal.frontierhunts.note.event|" + place, (byte)1);
         }
      } catch (RuntimeException e) {
         LOG.warn("Frontier Hunts journal: event hook failed", e);
      }
   }
}
