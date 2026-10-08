package com.formaworks.frontierhunts.ecology;

import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.hunting.routine.PressureStore;
import com.formaworks.frontierhunts.perf.AiThrottle;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * [ecology] Decides when predators hunt and runs the hunts.
 *
 * <p>Cost: each predator asks at most once every 10-15 s, and only while it is calm, within the AI throttle's
 * full-rate band (near a player) and hungry; the cheap gates (time, hunger, pressure, area cap, a random roll) come
 * before the single bounded entity query for prey. Running hunts are a short list ticked once per server tick.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class PredationService {
   static final String FED_TAG = "frontierhunts_fed";
   private static final int MAX_HUNTS = 8;
   private static final List<Hunt> HUNTS = new ArrayList<>();
   private static final Map<WildlifeMob, Hunt> ASSIGNED = new IdentityHashMap<>();
   private static final Map<WildlifeMob, Brain> BRAINS = new WeakHashMap<>();

   /** per predator: when it may next consider a hunt (not saved; hunger is saved on the animal) */
   private static final class Brain {
      long nextCheck;
   }

   private PredationService() {
   }

   // ------------------------------------------------------------------ queries used by HuntGoal / Hunt / commands

   static Hunt huntOf(WildlifeMob m) {
      return ASSIGNED.get(m);
   }

   static List<Hunt> hunts() {
      return HUNTS;
   }

   /** a member stays in its hunt unless something real happened to it (it fled from a player, was hurt, is fighting) */
   static boolean mayContinue(WildlifeMob m) {
      int b = m.behavior();
      return ASSIGNED.containsKey(m) && b != WildlifeMob.FLEE && b != WildlifeMob.WARN && m.getTarget() == null;
   }

   static long lastFed(WildlifeMob m, Predator p) {
      long now = m.level().getGameTime();
      var data = m.getPersistentData();
      if (!data.contains(FED_TAG)) {
         // first sight: somewhere between just fed and getting hungry (stable per animal)
         long h = m.getUUID().getLeastSignificantBits();
         long ago = (long)(Math.floorMod(h, 1000L) / 1000.0 * 1.1 * p.mealDays * 24000.0);
         data.putLong(FED_TAG, now - ago);
      }
      long t = data.getLong(FED_TAG);
      return t > now ? now : t;
   }

   static float hunger(WildlifeMob m, Predator p) {
      long ago = m.level().getGameTime() - lastFed(m, p);
      return (float)(ago / (p.mealDays * 24000.0));
   }

   static void fed(WildlifeMob m, long now, double fraction) {
      Predator p = Predator.of(m.species);
      if (p == null) {
         return;
      }
      long meal = (long)(p.mealDays * 24000.0);
      long last = lastFed(m, p);
      // a big meal resets the clock; a rabbit only takes the edge off
      long t = Math.min(now, Math.max(last, now - (long)(meal * (1.0 - Math.clamp(fraction, 0.0, 1.0)))));
      m.getPersistentData().putLong(FED_TAG, t);
   }

   // ------------------------------------------------------------------ starting hunts

   /**
    * HuntGoal.canUse for a predator with no hunt: maybe start one (it becomes the leader). Returns true when it did.
    */
   static boolean consider(WildlifeMob mob) {
      if (!(mob.level() instanceof ServerLevel level)) {
         return false;
      }
      Predator kind = Predator.of(mob.species);
      if (kind == null) {
         return false;
      }
      long now = level.getGameTime();
      Brain brain = BRAINS.computeIfAbsent(mob, k -> new Brain());
      if (now < brain.nextCheck) {
         return false;
      }
      brain.nextCheck = now + 200 + mob.getRandom().nextInt(100);
      if (!EcologyConfig.predation() || HUNTS.size() >= MAX_HUNTS) {
         return false;
      }
      int b = mob.behavior();
      if (b != WildlifeMob.IDLE && b != WildlifeMob.FEED && b != WildlifeMob.REST || mob.isInWater() || mob.getTarget() != null || mob.isPassenger()) {
         return false;
      }
      // only near players (the perf band), but not right next to one
      if (AiThrottle.tier(mob) != AiThrottle.FULL || level.getNearestPlayer(mob, 12.0) != null || level.getNearestPlayer(mob, 64.0) == null) {
         return false;
      }
      float hunger = hunger(mob, kind);
      if (hunger < 0.55F) {
         return false;
      }
      Vec3 at = mob.position();
      // hunted country: predators keep away from where people shoot
      if (PressureStore.level01(PressureStore.of(level).value(now, mob.blockPosition())) > 0.55F) {
         return false;
      }
      RandomSource r = mob.getRandom();
      // scavenging comes first for bears; anyone hungry takes an open carcass close by
      if (EcologyConfig.scavengers() && (kind.bear() || hunger > 0.9F) && scavenge(level, mob, kind, now)) {
         return true;
      }
      if (kind.style == Predator.Style.SCAVENGE) {
         return false;
      }
      float chance = kind.rate * kind.time.weight(level.getDayTime()) * Math.min(1.6F, hunger) * (float)EcologyConfig.huntRate();
      if (r.nextFloat() >= chance) {
         return false;
      }
      KillSiteStore store = KillSiteStore.of(level);
      if (!store.underCap(at, now)) {
         return false;
      }
      List<WildlifeMob> pack = recruits(level, mob, kind);
      LivingEntity target = choose(level, mob, kind, 1 + pack.size(), false);
      if (target == null || !store.startHunt(at, now, 1800L)) {
         return false;
      }
      start(level, mob, kind, target, pack, false, false);
      return true;
   }

   private static boolean scavenge(ServerLevel level, WildlifeMob mob, Predator kind, long now) {
      KillSiteStore store = KillSiteStore.of(level);
      KillSiteStore.Site site = store.nearestOpen(mob.position(), kind.bear() ? 64.0 : 40.0, now, 1200L);
      if (site == null) {
         return false;
      }
      Entity body = level.getEntity(site.carcass);
      if (body == null) {
         if (level.isLoaded(BlockPos.containing(site.position()))) {
            store.removeSite(site.carcass); // the chunk is here and the body is not: it is gone
         }
         return false;
      }
      KillRecord rec = body instanceof KillCarcass c ? c.record() : body instanceof Whitetail w ? KillSites.record(w) : null;
      if (rec == null || rec.fed >= 0.92F) {
         return false;
      }
      Hunt h = new Hunt(kind, level, mob);
      h.scavenging = true;
      h.carcass = body;
      h.record = rec;
      rec.visited(kind.title().toLowerCase(java.util.Locale.ROOT));
      site.busyUntil = now + kind.feedTicks + 600L;
      register(h);
      h.begin();
      return true;
   }

   /** pack mates within 24 blocks that are calm and free */
   private static List<WildlifeMob> recruits(ServerLevel level, WildlifeMob mob, Predator kind) {
      List<WildlifeMob> out = new ArrayList<>();
      if (!kind.social) {
         return out;
      }
      int max = kind == Predator.WOLF ? 5 : kind == Predator.LION ? 2 : 1;
      for (WildlifeMob o : level.getEntitiesOfClass(WildlifeMob.class, mob.getBoundingBox().inflate(24.0, 6.0, 24.0),
         o -> o != mob && o.species == mob.species && o.isAlive() && !ASSIGNED.containsKey(o) && calm(o))) {
         if (out.size() >= max) {
            break;
         }
         out.add(o);
      }
      return out;
   }

   private static boolean calm(WildlifeMob m) {
      int b = m.behavior();
      return (b == WildlifeMob.IDLE || b == WildlifeMob.FEED || b == WildlifeMob.REST) && m.getTarget() == null && !m.isInWater();
   }

   /** the prey this predator goes for: appetite x closeness, with a little chance; null if nothing suitable */
   static LivingEntity choose(ServerLevel level, WildlifeMob mob, Predator kind, int pack, boolean forced) {
      double radius = forced ? Math.max(kind.search, 64.0) : kind.search;
      AABB box = mob.getBoundingBox().inflate(radius, 10.0, radius);
      List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != mob && (e instanceof Whitetail || e instanceof WildlifeMob
         || e instanceof net.minecraft.world.entity.animal.Rabbit));
      LivingEntity best = null;
      double bestScore = 0.0;
      int[] counts = new int[Prey.Kind.values().length];
      List<Prey> infos = new ArrayList<>(near.size());
      for (LivingEntity e : near) {
         Prey p = Prey.of(e);
         infos.add(p);
         if (p != null) {
            counts[p.kind().ordinal()]++;
         }
      }
      RandomSource r = mob.getRandom();
      for (int i = 0; i < near.size() && i < 96; i++) {
         Prey p = infos.get(i);
         LivingEntity e = near.get(i);
         if (p == null || e.isInWater() || targeted(e)) {
            continue;
         }
         float want = kind.appetite(p, pack);
         if (forced && want <= 0.0F && !p.kind().small()) {
            want = 0.05F; // the debug command takes whatever is there
         }
         if (want <= 0.0F) {
            continue;
         }
         // never the last of a herd
         if (p.herd() && counts[p.kind().ordinal()] < 2 && !forced) {
            want *= 0.15F;
         }
         double d = Math.sqrt(mob.distanceToSqr(e));
         double score = want / (1.0 + d / 16.0) * (0.75 + 0.5 * r.nextFloat());
         if (score > bestScore) {
            bestScore = score;
            best = e;
         }
      }
      return best;
   }

   private static boolean targeted(LivingEntity e) {
      for (int i = 0; i < HUNTS.size(); i++) {
         if (HUNTS.get(i).prey == e) {
            return true;
         }
      }
      return false;
   }

   static Hunt start(ServerLevel level, WildlifeMob leader, Predator kind, LivingEntity target, List<WildlifeMob> pack, boolean forced, boolean success) {
      Hunt h = new Hunt(kind, level, leader);
      h.members.addAll(pack);
      h.prey = target;
      h.info = Prey.of(target);
      h.forced = forced;
      h.forceSuccess = success;
      if (h.info == null) {
         return null;
      }
      register(h);
      h.begin();
      return h;
   }

   private static void register(Hunt h) {
      for (WildlifeMob m : h.members) {
         Hunt old = ASSIGNED.get(m);
         if (old != null && old != h) {
            old.members.remove(m);
         }
         ASSIGNED.put(m, h);
      }
      HUNTS.add(h);
   }

   /** Called by a hunt for each member when it ends. */
   static void ended(WildlifeMob m, boolean fed, long now, boolean small) {
      if (ASSIGNED.get(m) != null) {
         ASSIGNED.remove(m);
      }
      if (fed) {
         fed(m, now, small ? 0.35 : 1.0);
      }
      Brain b = BRAINS.computeIfAbsent(m, k -> new Brain());
      // a failed hunt costs energy: rest a while before the next try; after a meal, much longer (hunger does that)
      b.nextCheck = now + (fed ? 1200 : 2400 + m.getRandom().nextInt(2400));
   }

   /** HuntGoal.stop: the member left (fled, hurt, killed): drop it from its hunt. */
   static void drop(WildlifeMob m) {
      Hunt h = ASSIGNED.remove(m);
      if (h != null) {
         h.members.remove(m);
      }
   }

   // ------------------------------------------------------------------ detection

   /**
    * Whether the prey notices the approaching predator this check: falls off with distance squared, doubled when the
    * predator is upwind (the prey smells it), less when it moves through cover or the prey is busy feeding.
    */
   static boolean notices(Hunt h, WildlifeMob predator, double d, double base) {
      if (h.forceSuccess) {
         return false;
      }
      LivingEntity prey = h.prey;
      double reference = Math.max(6.0, h.kind.rushDistance > 0 ? h.kind.rushDistance * 1.8 : h.kind.chaseStart);
      double p = base * Math.min(6.0, (reference / Math.max(1.0, d)) * (reference / Math.max(1.0, d)));
      ServerLevel level = h.level;
      try {
         Wilderness.Wind w = Wilderness.wind(level.getSeed(), level.getGameTime(), level.isRaining(), level.isThundering());
         double wx = w.east(), wz = w.south();
         double wl = Math.sqrt(wx * wx + wz * wz);
         if (wl > 1.0E-4) {
            // wind blowing from the predator toward the prey carries its scent
            double dx = prey.getX() - predator.getX(), dz = prey.getZ() - predator.getZ();
            double dl = Math.sqrt(dx * dx + dz * dz);
            if (dl > 1.0E-4) {
               double along = (dx * wx + dz * wz) / (dl * wl);
               p *= along > 0.5 ? 2.0 : along < -0.5 ? 0.6 : 1.0;
            }
         }
      } catch (RuntimeException ignored) {
         // no wind model: neutral
      }
      if (cover(level, predator)) {
         p *= 0.55;
      }
      if (prey instanceof Whitetail w && w.graze(0.0F) > 0.5F || prey instanceof WildlifeMob m && m.behavior() == WildlifeMob.FEED) {
         p *= 0.6;
      }
      return h.random.nextDouble() < p;
   }

   /** tall grass, ferns, bushes or leaves around the predator's head (two block reads) */
   private static boolean cover(ServerLevel level, WildlifeMob m) {
      BlockPos p = BlockPos.containing(m.getX(), m.getY() + 0.3, m.getZ());
      return plant(level.getBlockState(p)) || plant(level.getBlockState(p.relative(m.getDirection())));
   }

   private static boolean plant(BlockState s) {
      return s.is(BlockTags.LEAVES) || s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS);
   }

   // ------------------------------------------------------------------ ticking

   @SubscribeEvent
   public static void serverTick(ServerTickEvent.Post e) {
      if (HUNTS.isEmpty()) {
         return;
      }
      // bounded: at most MAX_HUNTS (+ forced ones), each a few members
      for (int i = 0; i < HUNTS.size(); i++) {
         Hunt h = HUNTS.get(i);
         try {
            h.tick();
         } catch (RuntimeException ex) {
            com.mojang.logging.LogUtils.getLogger().error("Frontier ecology: hunt failed, ending it", ex);
            h.end(false);
         }
      }
      HUNTS.removeIf(h -> {
         if (h.done()) {
            for (WildlifeMob m : h.members) {
               if (ASSIGNED.get(m) == h) {
                  ASSIGNED.remove(m);
               }
            }
            return true;
         }
         return false;
      });
      if (e.getServer().getTickCount() % 1200 == 0) {
         ASSIGNED.keySet().removeIf(m -> m.isRemoved());
         for (ServerLevel level : e.getServer().getAllLevels()) {
            KillSiteStore.of(level).prune(level.getGameTime(), EcologyConfig.carcassTicks());
         }
      }
   }

   @SubscribeEvent
   public static void levelUnload(LevelEvent.Unload e) {
      HUNTS.removeIf(h -> h.level == e.getLevel());
      ASSIGNED.values().removeIf(h -> h.level == e.getLevel());
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent e) {
      HUNTS.clear();
      ASSIGNED.clear();
      BRAINS.clear();
   }
}
