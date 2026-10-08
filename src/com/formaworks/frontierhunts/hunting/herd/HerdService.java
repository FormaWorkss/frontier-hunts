package com.formaworks.frontierhunts.hunting.herd;

import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * [herds] The social life of deer, elk and moose.
 *
 * <p>Every animal belongs to one {@link HerdGroup} (a family group, a bachelor group, or alone). The group's leader runs
 * the daily routine (bedding, feeding, water, game trails); the others follow it - each at its own place in the line,
 * along the leader's own path - and do what it does when it stops (feed around it, bed near it). Young follow their
 * mother. In winter, families and lone animals gather into yards (a yard is a root group the others join as children:
 * their leaders follow the root's leader). The calendar decides who belongs where: bachelor groups break up as the rut
 * comes, yearling bucks leave their mothers, elk bulls take harems, moose cows drive off last year's calf.</p>
 *
 * <p>Cost: per animal a few field reads per tick, a membership check every {@link HerdTuning#MEMBER_CHECK} ticks, a group
 * update every {@link HerdTuning#GROUP_UPDATE} ticks (O(group size) entity lookups by id), the social rules every
 * {@link HerdTuning#SOCIAL_UPDATE} ticks; adoption searches are budgeted server-wide. No per-tick neighbour searches.</p>
 */
public final class HerdService {
   public static final String KEY = "fh_group";
   static final String ROLE_KEY = "fh_grole";
   static final String KIND_KEY = "fh_gkind";
   static final String MOTHER_KEY = "fh_mother";
   /** this animal has left its mother's group for good (dispersed yearling buck, weaned moose calf) */
   static final String LEFT_KEY = "fh_left";

   /** calendar month position, refreshed every second on the server thread (read by world generation threads too) */
   static volatile double yearPos = 6.5;
   private static Boolean override;
   private static long adoptTick = Long.MIN_VALUE;
   private static int adoptsLeft;
   /** QA: time spent in the herd logic (ns), animal-ticks counted */
   public static boolean profiling;
   public static long profileNanos;
   public static long profileCalls;

   private HerdService() {
   }

   public static boolean enabled() {
      Boolean o = override;
      return o != null ? o : HerdConfig.enabled();
   }

   /** QA / commands: force the social model on or off (null = config). */
   public static void setOverride(Boolean on) {
      override = on;
   }

   public static double yearPosition() {
      return yearPos;
   }

   static boolean excluded(Whitetail deer) {
      return deer.isNoAi() || deer.getTags().contains("frontierhunts.academy");
   }

   // ================================================================== per animal

   /** DeerRoutine.serverTick: membership, breadcrumbs, waking with the group. */
   public static void tick(Whitetail deer, ServerLevel level, long now) {
      HerdMember m = deer.routine().social;
      if (!enabled() || excluded(deer)) {
         if (m.groupId != null || m.followId != null) {
            m.forget();
         }

         return;
      }

      long t0 = profiling ? System.nanoTime() : 0L;
      if (m.loadedAt == Long.MIN_VALUE) {
         m.loadedAt = now;
         m.nextCheck = now + 1L + (deer.getId() & 15);
         m.nextRegroup = now + HerdTuning.ADOPT_DELAY + (long)(HerdTuning.frac(deer.getUUID(), 99) * 400.0);
      }

      if (now >= m.nextCheck) {
         m.nextCheck = now + HerdTuning.MEMBER_CHECK + (deer.getId() & 31);
         check(deer, m, level, now);
      }

      HerdGroup g = m.group;
      if (g != null && now >= g.nextUpdate) {
         // whichever member ticks first keeps the group's picture fresh (who is here, who leads, wait for stragglers)
         HerdStore store = HerdStore.of(level);
         if (store.get(g.id) == g) {
            update(g, store, level, now);
         } else {
            m.nextCheck = now;
         }
      }

      m.tick(now);
      if (m.followId != null) {
         Whitetail f = m.followTarget(level, now);
         if (f != null) {
            HerdMember fm = f.routine().social;
            fm.keepCrumbs();
            int b = deer.behavior();
            if ((b == Whitetail.BEHAVIOR_BEDDED || b == Whitetail.BEHAVIOR_SLEEPING) && fm.traveling && now - fm.travelStart > 20L) {
               // the leader has got up and is walking off: rise and go with it (each in its own time)
               deer.routineWake(15 + (int)(HerdTuning.frac(deer.getUUID(), (int)(fm.travelStart & 0xFFFF)) * 70.0));
            }
         }
      }

      if (profiling) {
         profileNanos += System.nanoTime() - t0;
         profileCalls++;
      }
   }

   private static void check(Whitetail deer, HerdMember m, ServerLevel level, long now) {
      HerdStore store = HerdStore.of(level);
      CompoundTag data = deer.getPersistentData();
      UUID id = deer.getUUID();
      UUID gid = data.hasUUID(KEY) ? data.getUUID(KEY) : null;
      HerdGroup g = gid == null ? null : store.get(gid);
      if (gid != null && g == null) {
         // its group was merged into another while this animal was not loaded
         UUID moved = store.redirected(gid);
         g = moved == null ? null : store.get(moved);
         if (g != null) {
            gid = g.id;
            data.putUUID(KEY, gid);
         }
      }

      boolean planned = data.contains(KIND_KEY);
      if (gid != null && g == null && planned) {
         // spawned as part of a group: the first of them to be loaded founds it
         g = store.create(gid, deer.species(), HerdKind.byOrdinal(data.getByte(KIND_KEY)), deer.blockPosition(), now);
      }

      if (g != null && g.species != deer.species()) {
         g = null;
      }

      if (g != null && g.member(id) == null) {
         // not on the group's list: a new member of a spawned group, or the store and the animal disagree after a crash
         if (planned || fits(deer, g, data)) {
            HerdRole role = planned ? HerdRole.byOrdinal(data.getByte(ROLE_KEY)) : roleFor(deer, g);
            join(store, g, deer, role, now);
         } else {
            g = null;
         }
      }

      if (planned) {
         data.remove(KIND_KEY);
         data.remove(ROLE_KEY);
      }

      if (g == null) {
         if (gid != null) {
            data.remove(KEY);
         }

         m.forget();
         if (now >= m.nextRegroup && takeBudget(now)) {
            m.nextRegroup = now + 400L + (deer.getId() & 127);
            adopt(deer, m, store, level, now);
         }

         return;
      }

      m.groupId = g.id;
      m.group = g;
      HerdGroup.Member mem = g.member(id);
      mem.lastSeen = now;
      mem.age = deer.traits().ageMonths();
      mem.male = deer.traits().buck();
      g.lastSeen = now;
      if (now >= g.nextUpdate) {
         update(g, store, level, now);
         if (g.member(id) == null) {
            m.forget();
            return;
         }
      }

      if (!belongs(deer, g, mem, data)) {
         leave(deer, m, g, store, level, now);
         return;
      }

      if (now >= m.nextRegroup) {
         m.nextRegroup = now + 1200L + (deer.getId() & 511);
         if (g.size() == 1 && takeBudget(now) && regroup(deer, m, g, store, level, now)) {
            return;
         }
      }

      follow(deer, m, g, mem, store);
   }

   private static boolean takeBudget(long now) {
      if (adoptTick != now) {
         adoptTick = now;
         adoptsLeft = HerdTuning.ADOPT_BUDGET;
      }

      if (adoptsLeft <= 0) {
         return false;
      }

      adoptsLeft--;
      return true;
   }

   // ================================================================== who belongs where

   static boolean young(Whitetail deer) {
      return deer.traits().ageMonths() < HerdTuning.YOUNG_MONTHS;
   }

   static boolean left(CompoundTag data) {
      return data.getBoolean(LEFT_KEY);
   }

   /** A young male of this group is leaving its mother right now (spread over the dispersal window). */
   static boolean dispersing(Whitetail deer, HerdTuning.Social s) {
      double p = s.youngDispersal().progress(yearPos);
      if (p < 0.0 || !young(deer)) {
         return false;
      }

      UUID id = deer.getUUID();
      if (deer.species() == GameSpecies.MOOSE) {
         return p >= HerdTuning.frac(id, HerdTuning.SALT_SPLIT);
      }

      return deer.traits().buck() && HerdTuning.frac(id, HerdTuning.SALT_DISPERSE) < s.youngMaleDisperse() && p >= HerdTuning.frac(id, HerdTuning.SALT_SPLIT);
   }

   /** This male keeps to himself even in the bachelor season. */
   static boolean solitaryMale(Whitetail deer, HerdTuning.Social s) {
      float share = deer.traits().ageMonths() >= HerdTuning.MATURE_MONTHS ? s.soloMatureMale() : s.soloMale();
      return HerdTuning.frac(deer.getUUID(), HerdTuning.SALT_SOLO) < share;
   }

   static boolean loner(Whitetail deer, HerdTuning.Social s) {
      return HerdTuning.frac(deer.getUUID(), HerdTuning.SALT_LONER) < s.loneFemale();
   }

   static int familySize(HerdGroup g) {
      int n = 0;
      for (HerdGroup.Member m : g.members) {
         if (m.role != HerdRole.HERD_BULL) {
            n++;
         }
      }

      return n;
   }

   /** Whether this animal may join that group now. */
   static boolean fits(Whitetail deer, HerdGroup g, CompoundTag data) {
      if (g.species != deer.species() || g.kind == HerdKind.SOLO) {
         return false;
      }

      HerdTuning.Social s = HerdTuning.of(deer.species());
      boolean male = deer.traits().buck();
      boolean young = young(deer);
      int age = deer.traits().ageMonths();
      switch (g.kind) {
         case FAMILY:
            if (deer.species() == GameSpecies.MOOSE) {
               return age < HerdTuning.MOOSE_CALF_MONTHS && !left(data) && !dispersing(deer, s) && g.size() == 1 && !g.members.get(0).male
                  && g.members.get(0).age >= HerdTuning.YOUNG_MONTHS;
            }

            if (male && !young) {
               return deer.species() == GameSpecies.ELK && age >= HerdTuning.HERD_BULL_MONTHS && s.harem().contains(yearPos) && !g.hasRole(HerdRole.HERD_BULL);
            }

            if (male && (left(data) || dispersing(deer, s))) {
               return false;
            }

            return familySize(g) < s.familyMax();
         case BACHELOR:
            return male && (!young || left(data)) && s.bachelorSeason().contains(yearPos) && g.size() < s.bachelorMax();
         default:
            return false;
      }
   }

   static HerdRole roleFor(Whitetail deer, HerdGroup g) {
      if (g.kind == HerdKind.FAMILY && deer.traits().buck() && !young(deer)) {
         return HerdRole.HERD_BULL;
      }

      return g.kind == HerdKind.FAMILY && young(deer) ? HerdRole.YOUNG : HerdRole.ADULT;
   }

   /** Whether the animal still belongs in its group at this time of year. */
   static boolean belongs(Whitetail deer, HerdGroup g, HerdGroup.Member mem, CompoundTag data) {
      HerdTuning.Social s = HerdTuning.of(deer.species());
      boolean male = deer.traits().buck();
      switch (g.kind) {
         case FAMILY:
            if (g.size() == 1) {
               return true;
            }

            if (mem.role == HerdRole.HERD_BULL) {
               return s.harem().contains(yearPos);
            }

            if (deer.species() == GameSpecies.MOOSE && mem.role != HerdRole.LEADER) {
               if (left(data) || dispersing(deer, s) || deer.traits().ageMonths() >= HerdTuning.MOOSE_CALF_MONTHS) {
                  data.putBoolean(LEFT_KEY, true);
                  return false;
               }

               return true;
            }

            if (male && !young(deer)) {
               return false;
            }

            if (male && (left(data) || dispersing(deer, s))) {
               data.putBoolean(LEFT_KEY, true);
               return false;
            }

            return true;
         case BACHELOR:
            if (s.bachelorSeason().contains(yearPos)) {
               return true;
            }

            double split = s.bachelorSplit().progress(yearPos);
            return split >= 0.0 && split < HerdTuning.frac(deer.getUUID(), HerdTuning.SALT_SPLIT);
         default:
            return true;
      }
   }

   static void join(HerdStore store, HerdGroup g, Whitetail deer, HerdRole role, long now) {
      CompoundTag data = deer.getPersistentData();
      HerdGroup.Member mem = store.add(g, deer.getUUID(), role, deer.traits().buck(), deer.traits().ageMonths(), now);
      if (mem.role == HerdRole.YOUNG || role == HerdRole.YOUNG) {
         UUID mother = data.hasUUID(MOTHER_KEY) ? data.getUUID(MOTHER_KEY) : null;
         if (mother == null || g.member(mother) == null) {
            // adopted into a family: the family's lead female looks after it
            HerdGroup.Member lead = g.members.get(0);
            mother = !lead.male && lead.age >= HerdTuning.YOUNG_MONTHS && !lead.id.equals(deer.getUUID()) ? lead.id : null;
         }

         mem.mother = mother;
      }

      data.putUUID(KEY, g.id);
      HerdMember m = deer.routine().social;
      m.groupId = g.id;
      m.group = g;
      m.followId = null;
      m.follow = null;
      g.nextUpdate = 0L;
   }

   /** Leaves the group and goes alone (the season moved it on); a male leaving finds a range of his own. */
   private static void leave(Whitetail deer, HerdMember m, HerdGroup g, HerdStore store, ServerLevel level, long now) {
      store.remove(g, deer.getUUID());
      HerdGroup solo = store.create(UUID.randomUUID(), deer.species(), HerdKind.SOLO, deer.blockPosition(), now);
      join(store, solo, deer, HerdRole.LEADER, now);
      if (g.members.size() > 0 && (deer.traits().buck() || deer.species() == GameSpecies.MOOSE)) {
         deer.routine().resetRange(level);
      }

      m.nextRegroup = now + 200L;
   }

   private static void move(Whitetail deer, HerdGroup from, HerdGroup to, HerdRole role, HerdStore store, long now) {
      if (from != null) {
         store.remove(from, deer.getUUID());
      }

      join(store, to, deer, role, now);
   }

   /** An animal with no group (an old world's deer, an egg-spawned one, a straggler whose group was forgotten). */
   private static void adopt(Whitetail deer, HerdMember m, HerdStore store, ServerLevel level, long now) {
      HerdGroup g = store.create(UUID.randomUUID(), deer.species(), HerdKind.SOLO, deer.blockPosition(), now);
      join(store, g, deer, HerdRole.LEADER, now);
      if (!regroup(deer, m, g, store, level, now) && store.get(g.id) != null) {
         // nobody to join: a doe (or a yearling) on her own is the start of a family group others can join
         HerdTuning.Social s = HerdTuning.of(deer.species());
         if (deer.species() != GameSpecies.MOOSE && (!deer.traits().buck() || young(deer) && !left(deer.getPersistentData())) && !loner(deer, s)) {
            g.kind = HerdKind.FAMILY;
         }
      }
   }

   /**
    * A lone animal looks for company that fits the season: a family to join, a bachelor group (or another lone male to
    * start one with), a cow herd to hold (elk rut), a cow to follow (orphaned moose calf). Returns true if it moved.
    */
   private static boolean regroup(Whitetail deer, HerdMember m, HerdGroup g, HerdStore store, ServerLevel level, long now) {
      HerdTuning.Social s = HerdTuning.of(deer.species());
      CompoundTag data = deer.getPersistentData();
      boolean male = deer.traits().buck();
      boolean young = young(deer);
      int age = deer.traits().ageMonths();
      GameSpecies sp = deer.species();
      if (sp == GameSpecies.MOOSE) {
         if (age < HerdTuning.MOOSE_CALF_MONTHS && !left(data) && !dispersing(deer, s)) {
            HerdGroup cow = nearest(store, deer, g, s.joinRadius(), x -> x.size() == 1 && !x.members.get(0).male
               && x.members.get(0).age >= HerdTuning.YOUNG_MONTHS && x.parent == null);
            if (cow != null) {
               cow.kind = HerdKind.FAMILY;
               move(deer, g, cow, HerdRole.YOUNG, store, now);
               return true;
            }
         }

         return false;
      }

      if (!male || young && !left(data)) {
         if (!young && loner(deer, s)) {
            return false;
         }

         HerdGroup fam = nearest(store, deer, g, s.joinRadius(), x -> x.kind == HerdKind.FAMILY && fits(deer, x, data));
         if (fam != null) {
            move(deer, g, fam, young ? HerdRole.YOUNG : HerdRole.ADULT, store, now);
            return true;
         }

         if (g.kind == HerdKind.SOLO) {
            g.kind = HerdKind.FAMILY;
         }

         return false;
      }

      if (sp == GameSpecies.ELK && age >= HerdTuning.HERD_BULL_MONTHS && s.harem().contains(yearPos)) {
         HerdGroup harem = nearest(store, deer, g, s.joinRadius(), x -> x.kind == HerdKind.FAMILY && !x.hasRole(HerdRole.HERD_BULL) && x.count(false) >= 2);
         if (harem != null) {
            move(deer, g, harem, HerdRole.HERD_BULL, store, now);
            return true;
         }

         return false;
      }

      if (g.kind != HerdKind.SOLO) {
         g.kind = HerdKind.SOLO;
      }

      if (!s.bachelorSeason().contains(yearPos) || solitaryMale(deer, s)) {
         return false;
      }

      HerdGroup band = nearest(store, deer, g, s.joinRadius(), x -> x.kind == HerdKind.BACHELOR && fits(deer, x, data));
      if (band != null) {
         move(deer, g, band, HerdRole.ADULT, store, now);
         return true;
      }

      // another lone male of the right sort nearby: the two start a bachelor group
      HerdGroup other = nearest(store, deer, g, s.joinRadius(), x -> x.kind == HerdKind.SOLO && x.size() == 1 && x.members.get(0).male && x.parent == null);
      if (other != null && level.getEntity(other.leader()) instanceof Whitetail mate && !mate.downed() && mate.species() == sp
         && (!young(mate) || left(mate.getPersistentData())) && !solitaryMale(mate, s)) {
         g.kind = HerdKind.BACHELOR;
         move(mate, other, g, HerdRole.ADULT, store, now);
         return false; // this animal stays put (it leads or follows by rank in its own group)
      }

      return false;
   }

   private static HerdGroup nearest(HerdStore store, Whitetail deer, HerdGroup except, double radius, java.util.function.Predicate<HerdGroup> ok) {
      HerdGroup best = null;
      double bestD = Double.MAX_VALUE;
      for (HerdGroup x : store.near(deer.species(), deer.blockPosition(), radius, except)) {
         if (ok.test(x)) {
            double d = HerdStore.horizontal(x.centre, deer.blockPosition());
            if (d < bestD) {
               bestD = d;
               best = x;
            }
         }
      }

      return best;
   }

   /** Whom the animal follows and where in the line it walks. */
   private static void follow(Whitetail deer, HerdMember m, HerdGroup g, HerdGroup.Member mem, HerdStore store) {
      UUID old = m.followId;
      int idx = g.indexOf(deer.getUUID());
      if (idx == 0) {
         HerdGroup root = g.parent == null ? null : store.get(g.parent);
         if (root != null && root != g && root.leader() != null && !root.leader().equals(deer.getUUID())) {
            m.followId = root.leader();
            m.rank = Math.max(1, g.yardRank);
         } else {
            m.followId = null;
            m.rank = 0;
         }
      } else if (mem.role == HerdRole.YOUNG && mem.mother != null && g.member(mem.mother) != null) {
         m.followId = mem.mother;
         m.rank = 0;
         int sib = 0;
         for (HerdGroup.Member o : g.members) {
            if (o == mem) {
               break;
            }

            if (mem.mother.equals(o.mother)) {
               sib++;
            }
         }

         m.siblings = sib;
      } else {
         m.followId = g.leader();
         m.rank = mem.role == HerdRole.HERD_BULL ? idx + 2 : idx;
      }

      if (!Objects.equals(old, m.followId)) {
         m.follow = null;
         m.nextResolve = 0L;
      }
   }

   // ================================================================== group upkeep

   private static void update(HerdGroup g, HerdStore store, ServerLevel level, long now) {
      g.nextUpdate = now + HerdTuning.GROUP_UPDATE;
      int n = g.members.size();
      if (g.present.length != n) {
         g.present = new Whitetail[n];
      }

      List<UUID> gone = null;
      long newest = Long.MIN_VALUE;
      for (HerdGroup.Member mm : g.members) {
         newest = Math.max(newest, mm.lastSeen);
      }

      int count = 0;
      for (int i = 0; i < n; i++) {
         HerdGroup.Member mm = g.members.get(i);
         Entity e = level.getEntity(mm.id);
         Whitetail w = e instanceof Whitetail ww && !ww.isRemoved() ? ww : null;
         boolean drop = false;
         if (w != null && w.downed()) {
            drop = true; // dead
         } else if (w != null && !g.id.equals(groupIdOf(w)) && !g.id.equals(store.redirected(groupIdOf(w))) && !w.getPersistentData().contains(KIND_KEY)) {
            drop = true; // it went to another group
         } else if (w == null && newest - mm.lastSeen > HerdTuning.MEMBER_FORGET) {
            drop = true; // long gone while the others are about
         }

         if (drop) {
            if (gone == null) {
               gone = new ArrayList<>();
            }

            gone.add(mm.id);
            w = null;
         } else if (w != null) {
            mm.lastSeen = now;
            count++;
         }

         mm.away = w == null && now - mm.lastSeen > HerdTuning.LEADER_ABSENT;
         g.present[i] = w;
      }

      g.presentCount = count;
      if (gone != null) {
         for (UUID id : gone) {
            store.remove(g, id);
         }

         if (store.get(g.id) == null) {
            return;
         }

         update(g, store, level, now);
         return;
      }

      HerdGroup.Member lead = g.members.get(0);
      if (lead.away && count > 0) {
         // the leader has not been seen for a long while: the next in line takes over
         g.rank();
         g.nextUpdate = 0L;
      }

      Whitetail leader = g.present[0];
      Whitetail any = leader;
      if (any == null) {
         for (Whitetail w : g.present) {
            if (w != null) {
               any = w;
               break;
            }
         }
      }

      if (any != null) {
         g.centre = any.blockPosition();
      }

      // a travelling leader stops and looks back when one of its followers has fallen far behind
      boolean wait = false;
      HerdTuning.Social s = HerdTuning.of(g.species);
      if (leader != null && leader.routine().travelling() && leader.routine().social.traveling) {
         UUID lid = leader.getUUID();
         for (int i = 1; i < n; i++) {
            Whitetail w = g.present[i];
            if (w != null && w.behavior() == Whitetail.BEHAVIOR_NORMAL) {
               HerdMember wm = w.routine().social;
               if (lid.equals(wm.followId)) {
                  double expect = HerdTuning.along(s, wm.rank, wm.personal) + s.straggle();
                  if (w.distanceToSqr(leader) > expect * expect) {
                     wait = true;
                     break;
                  }
               }
            }
         }

         if (wait) {
            g.waitedTotal += HerdTuning.GROUP_UPDATE;
            if (g.waitedTotal > HerdTuning.WAIT_BUDGET) {
               wait = false;
            }
         }
      } else {
         g.waitedTotal = 0L;
      }

      g.wait = wait;
      if (now >= g.nextSocial) {
         g.nextSocial = now + HerdTuning.SOCIAL_UPDATE + (Math.abs(g.id.hashCode()) & 63);
         social(g, store, level, now);
      }
   }

   static UUID groupIdOf(Whitetail w) {
      CompoundTag data = w.getPersistentData();
      return data.hasUUID(KEY) ? data.getUUID(KEY) : null;
   }

   /** The slower rules: winter yards, elk herds coming together. */
   private static void social(HerdGroup g, HerdStore store, ServerLevel level, long now) {
      HerdTuning.Social s = HerdTuning.of(g.species);
      double pos = yearPos;
      if (g.parent != null) {
         HerdGroup root = store.get(g.parent);
         double far = s.yardRadius() * 1.5;
         if (root == null || root.parent != null || !s.yardSeason().contains(pos) || HerdStore.horizontal(root.centre, g.centre) > far * far) {
            g.parent = null;
            store.setDirty();
         }
      }

      boolean eligible = g.species != GameSpecies.ELK || g.kind == HerdKind.FAMILY;
      if (g.parent == null && s.yardMax() > 0 && s.yardSeason().contains(pos) && eligible) {
         List<HerdGroup> mine = store.children(g);
         int mineSize = g.size();
         for (HerdGroup c : mine) {
            mineSize += c.size();
         }

         HerdGroup best = null;
         double bestD = Double.MAX_VALUE;
         for (HerdGroup o : store.near(g.species, g.centre, s.yardRadius(), g)) {
            if (o.parent != null || o.kind != HerdKind.FAMILY || !biggerRoot(o, g)) {
               continue;
            }

            int total = Math.max(o.size(), o.yardTotal);
            double d = HerdStore.horizontal(o.centre, g.centre);
            if (total + mineSize <= s.yardMax() && d < bestD) {
               bestD = d;
               best = o;
            }
         }

         if (best != null) {
            g.parent = best.id;
            for (HerdGroup c : mine) {
               c.parent = best.id;
            }

            best.yardTotal = Math.max(best.size(), best.yardTotal) + mineSize;
            store.setDirty();
         }
      }

      if (g.parent == null) {
         // a yard root: number its families so each walks at its own place behind the yard's leader
         List<HerdGroup> kids = store.children(g);
         kids.sort(Comparator.comparingLong((HerdGroup k) -> k.formedAt).thenComparing(k -> k.id));
         int r = g.size();
         for (HerdGroup k : kids) {
            k.yardRank = r;
            r += k.size();
         }

         g.yardTotal = r;
      }

      // elk cow herds meeting outside the winter: fission-fusion, the smaller joins the bigger
      if (g.species == GameSpecies.ELK && g.kind == HerdKind.FAMILY && g.parent == null && !s.yardSeason().contains(pos)) {
         for (HerdGroup o : store.near(GameSpecies.ELK, g.centre, 40.0, g)) {
            if (o.kind == HerdKind.FAMILY && o.parent == null && biggerRoot(o, g) && familySize(o) + familySize(g) <= s.familyMax()
               && !(o.hasRole(HerdRole.HERD_BULL) && g.hasRole(HerdRole.HERD_BULL))) {
               merge(g, o, store, level, now);
               return;
            }
         }
      }
   }

   private static boolean biggerRoot(HerdGroup o, HerdGroup g) {
      if (o.size() != g.size()) {
         return o.size() > g.size();
      }

      return o.formedAt != g.formedAt ? o.formedAt < g.formedAt : o.id.compareTo(g.id) < 0;
   }

   /** Moves every member of {@code from} into {@code into}; members not loaded find their way through the redirect. */
   private static void merge(HerdGroup from, HerdGroup into, HerdStore store, ServerLevel level, long now) {
      List<HerdGroup.Member> list = new ArrayList<>(from.members);
      for (HerdGroup.Member mm : list) {
         if (level.getEntity(mm.id) instanceof Whitetail w && !w.downed()) {
            move(w, from, into, mm.role == HerdRole.LEADER ? (w.traits().buck() ? HerdRole.HERD_BULL : young(w) ? HerdRole.YOUNG : HerdRole.ADULT) : mm.role,
               store, now);
         } else {
            store.remove(from, mm.id);
            HerdGroup.Member nm = store.add(into, mm.id, mm.role == HerdRole.LEADER ? HerdRole.ADULT : mm.role, mm.male, mm.age, now);
            nm.lastSeen = mm.lastSeen;
         }
      }

      store.redirect(from.id, into.id);
      if (store.get(from.id) != null) {
         store.drop(from);
      }
   }

   // ================================================================== alarms (hooks from Whitetail)

   static HerdGroup groupOf(Whitetail deer, HerdStore store) {
      HerdMember m = deer.routine().social;
      HerdGroup g = m.group;
      if (g == null || store.get(g.id) != g) {
         g = store.groupOf(deer.getUUID());
      }

      return g;
   }

   static HerdGroup root(HerdGroup g, HerdStore store) {
      if (g != null && g.parent != null) {
         HerdGroup r = store.get(g.parent);
         if (r != null) {
            return r;
         }
      }

      return g;
   }

   /** Every loaded member of the group and of its yard (the root and all its children). */
   static List<Whitetail> everyone(HerdGroup root, HerdStore store) {
      List<Whitetail> out = new ArrayList<>();
      addPresent(root, out);
      if (root.parent == null) {
         for (HerdGroup c : store.children(root)) {
            addPresent(c, out);
         }
      }

      return out;
   }

   private static void addPresent(HerdGroup g, List<Whitetail> out) {
      for (Whitetail w : g.present) {
         if (w != null && !w.isRemoved() && !w.downed()) {
            out.add(w);
         }
      }
   }

   /** A member bolts: the group takes one flight heading and every mate within the bolt radius runs too. */
   public static void bolted(Whitetail deer, Vec3 threat) {
      if (!enabled() || threat == null || !(deer.level() instanceof ServerLevel level)) {
         return;
      }

      HerdStore store = HerdStore.of(level);
      HerdGroup root = root(groupOf(deer, store), store);
      if (root == null || root.size() < 2 && store.children(root).isEmpty()) {
         return;
      }

      long now = level.getGameTime();
      if (now - root.flightAt <= HerdTuning.FLIGHT_SHARE) {
         return;
      }

      double ax = deer.getX() - threat.x;
      double az = deer.getZ() - threat.z;
      double len = Math.sqrt(ax * ax + az * az);
      if (len < 1.0E-3) {
         double a = deer.getRandom().nextDouble() * Math.PI * 2.0;
         ax = Math.cos(a);
         az = Math.sin(a);
      } else {
         ax /= len;
         az /= len;
      }

      double turn = (deer.getRandom().nextDouble() - 0.5) * Math.toRadians(40.0);
      double c = Math.cos(turn);
      double sn = Math.sin(turn);
      root.flightX = ax * c - az * sn;
      root.flightZ = ax * sn + az * c;
      root.flightAt = now;
      root.flightBy = deer.getUUID();
      HerdTuning.Social s = HerdTuning.of(deer.species());
      double r2 = s.boltRadius() * s.boltRadius();
      for (Whitetail w : everyone(root, store)) {
         if (w != deer && w.distanceToSqr(deer) <= r2 && w.behavior() != Whitetail.BEHAVIOR_FLEEING) {
            w.alarm(threat, 0.78F, 160);
         }
      }
   }

   /** Flight direction for a fleeing group mate: mostly the group's heading, a little of its own line away. */
   public static Vec3 fleeHeading(Whitetail deer, Vec3 own) {
      if (!enabled() || !(deer.level() instanceof ServerLevel level)) {
         return own;
      }

      HerdGroup g = deer.routine().social.group;
      if (g == null) {
         return own;
      }

      HerdStore store = HerdStore.of(level);
      HerdGroup root = root(g, store);
      if (level.getGameTime() - root.flightAt > HerdTuning.FLIGHT_SHARE || deer.getUUID().equals(root.flightBy)) {
         return own;
      }

      double k = HerdTuning.FLIGHT_BLEND;
      double x = own.x * (1.0 - k) + root.flightX * k;
      double z = own.z * (1.0 - k) + root.flightZ * k;
      double len = Math.sqrt(x * x + z * z);
      return len < 1.0E-3 ? own : new Vec3(x / len, 0.0, z / len);
   }

   /** A member stamps / snorts at something: the group's heads come up and turn to it. */
   public static void alerted(Whitetail deer, Vec3 threat) {
      if (!enabled() || threat == null || !(deer.level() instanceof ServerLevel level)) {
         return;
      }

      HerdStore store = HerdStore.of(level);
      HerdGroup root = root(groupOf(deer, store), store);
      long now = level.getGameTime();
      if (root == null || now - root.alertAt < 60L) {
         return;
      }

      root.alertAt = now;
      HerdTuning.Social s = HerdTuning.of(deer.species());
      double r2 = s.alertRadius() * s.alertRadius();
      for (Whitetail w : everyone(root, store)) {
         if (w != deer && w.distanceToSqr(deer) <= r2 && w.alertness() < 0.38F && w.behavior() != Whitetail.BEHAVIOR_FLEEING) {
            w.alarm(threat, 0.38F, 100);
         }
      }
   }

   /** A member went down (shot, killed): the rest bolt from where the shot came from, and the group closes up without it. */
   public static void downed(Whitetail deer, DamageSource source) {
      if (!enabled() || !(deer.level() instanceof ServerLevel level)) {
         return;
      }

      HerdStore store = HerdStore.of(level);
      HerdGroup g = groupOf(deer, store);
      if (g == null) {
         return;
      }

      Entity cause = source == null ? null : source.getEntity();
      Vec3 threat = cause != null && cause.level() == level ? cause.position() : deer.position();
      HerdGroup root = root(g, store);
      HerdTuning.Social s = HerdTuning.of(deer.species());
      double r2 = s.boltRadius() * s.boltRadius();
      for (Whitetail w : everyone(root, store)) {
         if (w != deer && w.distanceToSqr(deer) <= r2) {
            w.alarm(threat, 1.0F, 300);
         }
      }

      store.remove(g, deer.getUUID());
      deer.getPersistentData().remove(KEY);
      deer.routine().social.forget();
   }

   // ================================================================== small queries for the routine and hooks

   /** The animal this one follows right now (loaded, on its feet, near enough), or null when it leads / is alone. */
   public static Whitetail followTarget(Whitetail deer) {
      if (!enabled() || !(deer.level() instanceof ServerLevel level)) {
         return null;
      }

      return deer.routine().social.followTarget(level, level.getGameTime());
   }

   /** Where a follower should be: its place along the followed animal's path, drifting a little to the side. */
   public static Vec3 slot(Whitetail deer, Whitetail target, long now) {
      HerdMember m = deer.routine().social;
      HerdMember t = target.routine().social;
      HerdTuning.Social s = HerdTuning.of(deer.species());
      double along;
      double side;
      if (m.rank <= 0) {
         along = s.youngGap() * m.personal * (1.0 + 0.6 * m.siblings);
         side = m.side(now, 0.6);
      } else {
         along = HerdTuning.along(s, m.rank, m.personal);
         side = m.side(now, HerdTuning.lateral(s, m.rank));
      }

      return t.behind(along, side);
   }

   /** How far behind the followed animal this follower walks. */
   public static double gap(Whitetail deer) {
      HerdMember m = deer.routine().social;
      HerdTuning.Social s = HerdTuning.of(deer.species());
      return m.rank <= 0 ? s.youngGap() * m.personal * (1.0 + 0.6 * m.siblings) : HerdTuning.along(s, m.rank, m.personal);
   }

   public static HerdTuning.Social social(Whitetail deer) {
      return HerdTuning.of(deer.species());
   }

   /** The followed animal is on the move (a routine leg or a walk of more than a few blocks). */
   public static boolean moving(Whitetail target) {
      return target.routine().social.traveling;
   }

   /** A travelling leader should stop for a straggler. */
   public static boolean leaderShouldWait(Whitetail deer) {
      HerdGroup g = deer.routine().social.group;
      return enabled() && g != null && g.wait && deer.getUUID().equals(g.leader());
   }

   /** The home range a member should use: its group's (the leader's), or null when it is the one that keeps it. */
   public static UUID rangeFor(Whitetail deer) {
      if (!enabled()) {
         return null;
      }

      HerdGroup g = deer.routine().social.group;
      return g == null || deer.getUUID().equals(g.leader()) ? null : g.rangeId;
   }

   /** The leader reports the range it uses, so its followers share it. */
   public static void leaderRange(Whitetail deer, UUID range) {
      HerdGroup g = deer.routine().social.group;
      if (enabled() && g != null && deer.getUUID().equals(g.leader()) && !Objects.equals(g.rangeId, range)) {
         g.rangeId = range;
      }
   }

   /** The animal keeps its own home range (it leads its group, or it has none). */
   public static boolean keepsRange(Whitetail deer) {
      HerdGroup g = deer.routine().social.group;
      return g == null || deer.getUUID().equals(g.leader());
   }

   /** Just loaded and not yet placed in a group: the routine holds off joining any range for a moment. */
   public static boolean settling(Whitetail deer, long now) {
      if (!enabled() || excluded(deer)) {
         return false;
      }

      HerdMember m = deer.routine().social;
      return m.group == null && (m.loadedAt == Long.MIN_VALUE || now - m.loadedAt < 700L);
   }

   /** Grouped animals don't take the old free ranges of whatever deer happen to stand near them. */
   public static boolean grouped(Whitetail deer) {
      return enabled() && deer.routine().social.group != null;
   }

   /** While the group is walking, a follower doesn't wander off to browse. */
   public static boolean holdForage(Whitetail deer) {
      if (!enabled() || deer.level().isClientSide) {
         return false;
      }

      Whitetail t = followTarget(deer);
      return t != null && (moving(t) || t.distanceToSqr(deer) > sq(HerdTuning.of(deer.species()).feedSpread()));
   }

   /** One head stays up: a grazing group rarely has every animal's nose in the grass at once. */
   public static boolean sentinel(Whitetail deer) {
      if (!enabled() || deer.level().isClientSide) {
         return false;
      }

      HerdMember me = deer.routine().social;
      HerdGroup g = me.group;
      long now = deer.level().getGameTime();
      if (now < me.watchUntil) {
         return me.watching; // a decision holds for a while (keeps its head up, or grazes in peace)
      }

      me.watching = false;
      me.watchUntil = now + 60L;
      if (g == null || g.presentCount < 3) {
         return false;
      }

      int near = 0;
      for (Whitetail w : g.present) {
         if (w != null && w != deer && !w.isRemoved() && !w.downed() && w.distanceToSqr(deer) < 625.0) {
            if (w.graze(1.0F) < 0.5F && w.behavior() == Whitetail.BEHAVIOR_NORMAL) {
               return false; // someone is already watching
            }

            near++;
         }
      }

      if (near >= 2 && deer.getRandom().nextFloat() < HerdTuning.SENTINEL) {
         me.watching = true;
         me.watchUntil = now + 100L + deer.getRandom().nextInt(120);
      }

      return me.watching;
   }

   static double sq(double d) {
      return d * d;
   }

   /** Largest natural spawn cluster for the species (vanilla stops a pack at 4). */
   public static int maxCluster(GameSpecies species, int fallback) {
      if (!enabled()) {
         return fallback;
      }

      return switch (species) {
         case ELK -> 10;
         case MOOSE -> 2;
         default -> 6;
      };
   }

   // ================================================================== debug / QA

   /** Text lines describing the animal's group (command). */
   public static List<String> describe(Whitetail deer) {
      List<String> out = new ArrayList<>();
      if (!(deer.level() instanceof ServerLevel level)) {
         return out;
      }

      if (!enabled()) {
         out.add("social groups are off (server config herds.socialHerds)");
         return out;
      }

      HerdStore store = HerdStore.of(level);
      HerdGroup g = groupOf(deer, store);
      if (g == null) {
         out.add("no group yet (adopted within ~30 s)");
         return out;
      }

      HerdGroup root = root(g, store);
      out.add(g.kind.title(g.species) + " of " + g.size() + " · id " + g.id.toString().substring(0, 8)
         + (root != g ? " · in a winter yard of " + yardSize(root, store) + " (root " + root.id.toString().substring(0, 8) + ")" : "")
         + (g.wait ? " · leader waiting for stragglers" : ""));
      for (int i = 0; i < g.members.size(); i++) {
         HerdGroup.Member mm = g.members.get(i);
         Whitetail w = i < g.present.length ? g.present[i] : null;
         String who = (mm.male ? g.species.maleName : g.species.femaleName) + " " + mm.age + " mo";
         String where = w == null ? "not loaded" : Math.round(w.distanceTo(deer)) + " m" + (w.routine().social.traveling ? ", walking" : "")
            + (w.behavior() == Whitetail.BEHAVIOR_BEDDED || w.behavior() == Whitetail.BEHAVIOR_SLEEPING ? ", bedded" : "")
            + (w.behavior() == Whitetail.BEHAVIOR_FLEEING ? ", running" : "");
         out.add((mm.id.equals(deer.getUUID()) ? "> " : "  ") + mm.role.name().toLowerCase(java.util.Locale.ROOT) + " · " + who + " · " + where);
      }

      HerdMember m = deer.routine().social;
      out.add("follows: " + (m.followId == null ? "nobody (leads)" : m.followId.toString().substring(0, 8) + " at ~" + Math.round(gap(deer)) + " m")
         + " · season " + String.format(java.util.Locale.ROOT, "%.2f", yearPos));
      return out;
   }

   static int yardSize(HerdGroup root, HerdStore store) {
      int n = root.size();
      for (HerdGroup c : store.children(root)) {
         n += c.size();
      }

      return n;
   }

   /** Snapshot for QA: group id, kind, members (ids in walking order), parent, leader. */
   public static List<String[]> snapshot(ServerLevel level) {
      List<String[]> out = new ArrayList<>();
      for (HerdGroup g : HerdStore.of(level).all()) {
         StringBuilder ids = new StringBuilder();
         for (HerdGroup.Member mm : g.members) {
            if (ids.length() > 0) {
               ids.append(',');
            }

            ids.append(mm.id);
         }

         out.add(new String[]{g.id.toString(), g.species.id, g.kind.name(), ids.toString(), g.parent == null ? "" : g.parent.toString()});
      }

      return out;
   }

   /** QA: the group id and role of an animal ("" when none). */
   public static String groupOf(Whitetail deer) {
      UUID id = groupIdOf(deer);
      return id == null ? "" : id.toString();
   }

   /** QA: kind name of the animal's group, or "". */
   public static String kindOf(Whitetail deer) {
      HerdGroup g = deer.level() instanceof ServerLevel level ? HerdStore.of(level).groupOf(deer.getUUID()) : null;
      return g == null ? "" : g.kind.name();
   }

   /** QA: the leader of the animal's group (null when none), and its yard root leader when in a yard. */
   public static UUID leaderOf(Whitetail deer) {
      HerdGroup g = deer.level() instanceof ServerLevel level ? HerdStore.of(level).groupOf(deer.getUUID()) : null;
      return g == null ? null : g.leader();
   }

   public static UUID yardRootOf(Whitetail deer) {
      if (!(deer.level() instanceof ServerLevel level)) {
         return null;
      }

      HerdStore store = HerdStore.of(level);
      HerdGroup g = store.groupOf(deer.getUUID());
      return g == null ? null : g.parent;
   }

   public static UUID followIdOf(Whitetail deer) {
      return deer.routine().social.followId;
   }

   /** QA / command: the leader walks to a spot (its group follows). */
   public static boolean walkTo(Whitetail deer, net.minecraft.core.BlockPos goal) {
      return deer.routine().debugWalkTo(goal);
   }

   /** QA: get up from the bed within the given ticks. */
   public static void wake(Whitetail deer, int ticks) {
      deer.routineWake(ticks);
   }

   public static int rankOf(Whitetail deer) {
      return deer.routine().social.rank;
   }

   static void housekeeping(ServerLevel level, long now) {
      HerdStore.of(level).housekeeping(now);
   }

   static void releaseUnloaded(ServerLevel level, long now) {
      HerdStore.of(level).releaseUnloaded(now);
   }
}
