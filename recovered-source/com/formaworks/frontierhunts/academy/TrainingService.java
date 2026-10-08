package com.formaworks.frontierhunts.academy;

import com.formaworks.frontierhunts.HunterLedger;
import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.expedition.ShootingTarget;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.journal.JournalApi;
import com.formaworks.frontierhunts.progression.AssignmentProgress;
import com.formaworks.frontierhunts.progression.AssignmentService;
import com.formaworks.frontierhunts.tracking.TrailMark;
import com.formaworks.frontierhunts.tracking.TrailStore;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * [academy] Server driver of the Ranger Academy: requests, the departure fade, building plots, the live sessions,
 * every guard that keeps the training grounds sealed (no blocks, no containers, no drops, no damage, nothing carried
 * out), recovery after a disconnect / crash / death, and the rewards once the hunter is home.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class TrainingService {
   static final Logger LOG = LogUtils.getLogger();
   static final int GROUND_Y = 21;
   private static final int SLOT_SPACING = 1024;
   private static final int SLOT_BASE = 4096;
   private static final int DEPART_TICKS = 16;
   private static final int PASSED_TICKS = 80;
   private static final int FAILED_TICKS = 50;
   private static final TicketType<ChunkPos> TICKET = TicketType.create("frontierhunts_academy", Comparator.comparingLong(ChunkPos::toLong));

   private static final Map<UUID, Session> SESSIONS = new HashMap<>();
   private static final Map<UUID, Pending> PENDING = new HashMap<>();
   private static final Map<UUID, Integer> RECOVER = new HashMap<>();
   private static final Map<UUID, Long> REQUESTS = new HashMap<>();
   private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
   private static final Set<UUID> MOVING = new HashSet<>();
   private static final Map<UUID, Planned> PLANNED = new HashMap<>();
   private static long clock;

   private static final class Pending {
      final Course course;
      int ticks;

      Pending(Course course, int ticks) {
         this.course = course;
         this.ticks = ticks;
      }
   }

   private record Planned(Course course, int slot, CourseKit kit) {
   }

   private TrainingService() {
   }

   // ================================================================================================ queries

   static ServerLevel grounds(MinecraftServer server) {
      return server == null ? null : server.getLevel(Academy.TRAINING);
   }

   static BlockPos origin(int slot) {
      return new BlockPos(SLOT_BASE + slot * SLOT_SPACING, GROUND_Y, 0);
   }

   public static boolean training(ServerPlayer p) {
      return p != null && SESSIONS.containsKey(p.getUUID());
   }

   static Session session(ServerPlayer p) {
      return p == null ? null : SESSIONS.get(p.getUUID());
   }

   static void moving(ServerPlayer p, boolean on) {
      if (on) {
         MOVING.add(p.getUUID());
      } else {
         MOVING.remove(p.getUUID());
      }
   }

   // ================================================================================================ requests

   /** Payload entry point (validated, rate-limited). */
   static void action(ServerPlayer p, byte action, byte course) {
      long last = REQUESTS.getOrDefault(p.getUUID(), Long.MIN_VALUE);
      if (last != Long.MIN_VALUE && clock - last < 8L && clock >= last) {
         return;
      }
      REQUESTS.put(p.getUUID(), clock);
      switch (action) {
         case AcademyNetwork.A_SYNC -> sendState(p);
         case AcademyNetwork.A_BEGIN -> request(p, Course.byId(course));
         case AcademyNetwork.A_LEAVE -> leave(p);
         default -> {
         }
      }
   }

   static void refuse(ServerPlayer p, Course c, String key) {
      AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_REFUSED, c == null ? -1 : c.ordinal(), 0, key));
   }

   /** Validates a start and begins the departure fade; the move happens a moment later in {@link #serverTick}. */
   static void request(ServerPlayer p, Course c) {
      if (c == null) {
         return;
      }
      UUID id = p.getUUID();
      if (!AcademyConfig.enabled()) {
         refuse(p, c, "academy.frontierhunts.refuse.disabled");
      } else if (grounds(p.server) == null) {
         refuse(p, c, "academy.frontierhunts.refuse.missing");
      } else if (!p.isAlive() || p.isSpectator()) {
         refuse(p, c, "academy.frontierhunts.refuse.state");
      } else if (SESSIONS.containsKey(id) || PENDING.containsKey(id) || Academy.inTraining(p)) {
         refuse(p, c, "academy.frontierhunts.refuse.busy");
      } else if (TrainingFlow.hasRecord(new HomeState.PlayerHost(p))) {
         RECOVER.put(id, 1);
         refuse(p, c, "academy.frontierhunts.refuse.busy");
      } else if (p.isPassenger() || p.isSleeping() || p.isFallFlying()) {
         refuse(p, c, "academy.frontierhunts.refuse.riding");
      } else if (p.fallDistance > 3.0F || !p.onGround() && !p.isInWater() && !p.getAbilities().flying) {
         refuse(p, c, "academy.frontierhunts.refuse.ground");
      } else if (p.hurtTime > 0 || p.getLastHurtByMob() != null && p.tickCount - p.getLastHurtByMobTimestamp() < 100) {
         refuse(p, c, "academy.frontierhunts.refuse.hurt");
      } else if (clock < COOLDOWN.getOrDefault(id, 0L)) {
         refuse(p, c, "academy.frontierhunts.refuse.cooldown");
      } else if (SESSIONS.size() + PENDING.size() >= AcademyConfig.maxSessions()) {
         refuse(p, c, "academy.frontierhunts.refuse.full");
      } else {
         PENDING.put(id, new Pending(c, DEPART_TICKS));
         p.closeContainer();
         AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_DEPART, c.ordinal(), DEPART_TICKS, c.lang("title")));
      }
   }

   /** Abandon (or cancel a departure). */
   static void leave(ServerPlayer p) {
      UUID id = p.getUUID();
      if (PENDING.remove(id) != null) {
         AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_HOME, -1, Session.Result.ABANDONED.ordinal(), ""));
         return;
      }
      Session s = SESSIONS.get(id);
      if (s == null) {
         return;
      }
      if (s.phase == Session.Phase.ACTIVE || s.phase == Session.Phase.FAILED) {
         s.result = s.phase == Session.Phase.FAILED ? s.result : Session.Result.ABANDONED;
         s.phase = Session.Phase.LEAVING;
         s.phaseTicks = 0;
         s.hudDirty = true;
         AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_DEPART, s.course.ordinal(), DEPART_TICKS, ""));
      } else if (s.phase == Session.Phase.PASSED) {
         s.phaseTicks = PASSED_TICKS; // skip the rest of the celebration
      }
   }

   // ================================================================================================ begin

   private static void begin(ServerPlayer p, Course c) {
      MinecraftServer server = p.server;
      ServerLevel tl = grounds(server);
      if (tl == null) {
         refuse(p, c, "academy.frontierhunts.refuse.missing");
         return;
      }
      if (!p.isAlive() || p.isSpectator() || p.isPassenger() || p.isSleeping() || Academy.inTraining(p)) {
         refuse(p, c, "academy.frontierhunts.refuse.state");
         AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_HOME, -1, Session.Result.INTERRUPTED.ordinal(), ""));
         return;
      }
      TrainingStore store = TrainingStore.get(server);
      CourseKit kit = CourseKit.of(c);
      Set<Integer> busy = new HashSet<>();
      for (Session s : SESSIONS.values()) {
         busy.add(s.slot);
      }
      int slot = store.pick(c, kit.version(), busy);
      if (slot < 0) {
         refuse(p, c, "academy.frontierhunts.refuse.full");
         AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_HOME, -1, Session.Result.INTERRUPTED.ordinal(), ""));
         return;
      }
      String nonce = UUID.randomUUID().toString().substring(0, 13);
      PLANNED.put(p.getUUID(), new Planned(c, slot, kit));
      boolean ok;
      try {
         ok = TrainingFlow.begin(new HomeState.PlayerHost(p), store, c, slot, nonce, tl.getGameTime());
      } catch (RuntimeException ex) {
         LOG.error("Frontier Hunts academy: could not start {} for {}", c.key, p.getGameProfile().getName(), ex);
         ok = false;
         Session s = SESSIONS.remove(p.getUUID());
         if (s != null) {
            cleanup(s, tl);
         }
         if (TrainingFlow.hasRecord(new HomeState.PlayerHost(p))) {
            TrainingFlow.forcedExit(new HomeState.PlayerHost(p), store);
            if (Academy.inTraining(p)) {
               HomeState.spawn(p);
            }
         }
      } finally {
         PLANNED.remove(p.getUUID());
      }
      if (!ok) {
         Session s = SESSIONS.remove(p.getUUID());
         if (s != null) {
            cleanup(s, tl);
         }
         refuse(p, c, "academy.frontierhunts.refuse.failed");
         AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_HOME, -1, Session.Result.INTERRUPTED.ordinal(), ""));
         return;
      }
      Session s = SESSIONS.get(p.getUUID());
      if (s == null) {
         return;
      }
      TrainingStore.Record rec = store.record(p.getUUID(), c);
      rec.attempts++;
      store.setDirty();
      AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_ARRIVE, c.ordinal(), c.timeLimit, c.lang("title"), Integer.toString(rec.passes)));
      sendHud(p, s, tl);
      sendState(p);
      LOG.info("Frontier Hunts academy: {} began {} in slot {}", p.getGameProfile().getName(), c.key, slot);
   }

   /** Called from the flow (host.enterPlot): build if needed, open the session, spawn the course, move the player. */
   static boolean enterPlot(ServerPlayer p, Course c, int slot) {
      Planned plan = PLANNED.get(p.getUUID());
      ServerLevel tl = grounds(p.server);
      if (plan == null || tl == null || plan.course != c || plan.slot != slot) {
         return false;
      }
      CourseKit kit = plan.kit;
      TrainingStore store = TrainingStore.get(p.server);
      BlockPos origin = origin(slot);
      Session s = new Session(p.getUUID(), c, kit, slot, origin, TrainingFlow.nonce(new HomeState.PlayerHost(p)), tl.getGameTime());
      SESSIONS.put(p.getUUID(), s);
      addTickets(s, tl);
      if (!store.built(slot, c, kit.version())) {
         long t0 = System.nanoTime();
         Plot plot = new Plot(tl, origin, 0x5EEDL * (slot + 1) + c.ordinal() * 7919L);
         kit.build(plot);
         plot.finish();
         store.markBuilt(slot, c, kit.version());
         LOG.info("Frontier Hunts academy: built the {} plot in slot {} ({} blocks, {} ms)", c.key, slot, plot.writes, (System.nanoTime() - t0) / 1000000L);
      }
      kit.begin(s, tl);
      float[] a = kit.arrival();
      MOVING.add(p.getUUID());
      try {
         p.teleportTo(tl, origin.getX() + a[0], origin.getY() + a[1], origin.getZ() + a[2], a[3], a[4]);
      } finally {
         MOVING.remove(p.getUUID());
      }
      if (p.level() != tl) {
         SESSIONS.remove(p.getUUID());
         cleanup(s, tl);
         return false;
      }
      p.setDeltaMovement(Vec3.ZERO);
      p.resetFallDistance();
      return true;
   }

   private static void addTickets(Session s, ServerLevel tl) {
      int x0 = (s.origin.getX() + s.course.minX - 8) >> 4, x1 = (s.origin.getX() + s.course.maxX + 8) >> 4;
      int z0 = (s.origin.getZ() + s.course.minZ - 8) >> 4, z1 = (s.origin.getZ() + s.course.maxZ + 8) >> 4;
      for (int x = x0; x <= x1; x++) {
         for (int z = z0; z <= z1; z++) {
            ChunkPos cp = new ChunkPos(x, z);
            tl.getChunkSource().addRegionTicket(TICKET, cp, 2, cp);
            s.tickets.add(cp);
         }
      }
   }

   /** Removes everything the session spawned or reserved (entities, blood marks, chunk tickets). */
   private static void cleanup(Session s, ServerLevel tl) {
      if (tl == null) {
         return;
      }
      try {
         s.kit.end(s, tl);
      } catch (RuntimeException ex) {
         LOG.warn("Frontier Hunts academy: course cleanup failed", ex);
      }
      for (UUID id : new ArrayList<>(s.entities)) {
         Entity e = tl.getEntity(id);
         if (e != null) {
            e.discard();
         }
      }
      s.entities.clear();
      if (!s.marks.isEmpty()) {
         TrailStore store = TrailStore.get(tl);
         for (UUID id : s.marks) {
            store.remove(id);
         }
         s.marks.clear();
      }
      for (ChunkPos cp : s.tickets) {
         tl.getChunkSource().removeRegionTicket(TICKET, cp, 2, cp);
      }
      s.tickets.clear();
   }

   // ================================================================================================ end

   /** Ends a live session: clean the plot, bring the hunter home, pay rewards. */
   private static void end(ServerPlayer p, Session s, Session.Result result) {
      ServerLevel tl = grounds(p.server);
      SESSIONS.remove(p.getUUID());
      cleanup(s, tl);
      TrainingStore store = TrainingStore.get(p.server);
      int elapsed = (int)Math.max(0L, (tl == null ? 0L : tl.getGameTime()) - s.started);
      boolean restored;
      try {
         restored = TrainingFlow.finish(new HomeState.PlayerHost(p), store);
      } catch (RuntimeException ex) {
         LOG.error("Frontier Hunts academy: restoring {} failed", p.getGameProfile().getName(), ex);
         restored = false;
      }
      if (!restored || Academy.inTraining(p)) {
         HomeState.sweep(p);
         if (Academy.inTraining(p)) {
            HomeState.spawn(p);
         }
      }
      COOLDOWN.put(p.getUUID(), clock + 60L);
      String[] reward = result == Session.Result.PASSED ? reward(p, s.course, store, elapsed) : new String[]{"0", "0", "0", "0"};
      AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_HOME, s.course.ordinal(), result.ordinal(), s.course.lang("title"), reward[0], reward[1],
         reward[2], reward[3], Integer.toString(elapsed)));
      sendState(p);
      AssignmentService.send(p, false);
      if (result == Session.Result.PASSED) {
         com.formaworks.frontierhunts.onboard.Onboarding.coursePassed(p, s.course); // [onboard] credits the Field School lessons / Handbook / journal
         com.formaworks.frontierhunts.progression.Durability.commit(p.server, "academy pass"); // [1.2.7]
      }
      LOG.info("Frontier Hunts academy: {} left {} ({}, {} s)", p.getGameProfile().getName(), s.course.key, result, elapsed / 20);
   }

   /**
    * Records the pass and pays: first pass in full (and its certification), later passes a quarter once per
    * {@code repeatRewardHours}, otherwise a practice run. Returns {rangerXp, skillXp, certified(0/1), best(0/1)}.
    */
   private static String[] reward(ServerPlayer p, Course c, TrainingStore store, int elapsed) {
      TrainingStore.Record r = store.record(p.getUUID(), c);
      boolean best = r.bestTicks == 0 || elapsed < r.bestTicks;
      r.passes++;
      if (best) {
         r.bestTicks = Math.max(1, elapsed);
      }
      long now = p.server.overworld().getGameTime();
      boolean first = r.passes == 1;
      boolean paid = !p.isCreative() && (first || r.lastReward < 0L || now - r.lastReward >= AcademyConfig.repeatTicks());
      int ranger = 0, skill = 0;
      boolean cert = false;
      if (paid) {
         r.lastReward = now;
         ranger = first ? c.rangerXp : Math.max(5, c.rangerXp / 4);
         skill = first ? c.skillXp : Math.max(10, c.skillXp / 4);
         try {
            AssignmentProgress prog = AssignmentService.progress(p);
            prog.reward(ranger);
            if (c.cert >= 0) {
               cert = prog.certify(c.cert);
            }
            HunterLedger.get(p.serverLevel()).setDirty();
         } catch (RuntimeException ex) {
            LOG.warn("Frontier Hunts academy: ranger reward failed", ex);
         }
         try {
            JournalApi.xp(p, c.skill, skill);
            JournalApi.note(p, "Ranger Academy · passed " + Component.translatable(c.lang("title")).getString() + " in " + clock(elapsed)
               + (cert ? " · certified" : ""));
         } catch (RuntimeException ex) {
            LOG.warn("Frontier Hunts academy: journal reward failed", ex);
         }
      }
      store.setDirty();
      return new String[]{Integer.toString(ranger), Integer.toString(skill), cert ? "1" : "0", best ? "1" : "0"};
   }

   static String clock(int ticks) {
      int s = Math.max(0, ticks) / 20;
      return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
   }

   // ================================================================================================ presentation

   public static void sendState(ServerPlayer p) {
      TrainingStore store = TrainingStore.get(p.server);
      long now = p.server.overworld().getGameTime();
      AssignmentProgress prog = null;
      try {
         prog = AssignmentService.progress(p);
      } catch (RuntimeException ignored) {
      }
      List<AcademyNetwork.CourseInfo> list = new ArrayList<>();
      for (int i = 0; i < Course.count(); i++) {
         Course c = Course.byId(i);
         TrainingStore.Record r = store.peek(p.getUUID(), c);
         int cooldown = 0;
         if (r != null && r.passes > 0 && r.lastReward >= 0L) {
            cooldown = (int)Math.clamp(r.lastReward + AcademyConfig.repeatTicks() - now, 0L, Integer.MAX_VALUE);
         }
         boolean cert = c.cert >= 0 && prog != null && prog.trained(c.cert);
         list.add(new AcademyNetwork.CourseInfo(r == null ? 0 : r.passes, r == null ? 0 : r.bestTicks, cooldown, cert));
      }
      Session s = SESSIONS.get(p.getUUID());
      boolean available = AcademyConfig.enabled() && grounds(p.server) != null;
      AcademyNetwork.send(p, new AcademyNetwork.State(available, s == null ? -1 : s.course.ordinal(), list));
   }

   private static void sendHud(ServerPlayer p, Session s, ServerLevel tl) {
      byte phase = switch (s.phase) {
         case ACTIVE -> AcademyNetwork.P_ACTIVE;
         case PASSED -> AcademyNetwork.P_PASSED;
         case FAILED -> AcademyNetwork.P_FAILED;
         case LEAVING -> AcademyNetwork.P_LEAVING;
      };
      int remaining = (int)Math.max(0L, s.deadline - tl.getGameTime());
      int flags = s.hudFlags | (s.course.wind ? 1 : 0);
      Wilderness.Wind w = CourseKit.wind(tl);
      AcademyNetwork.send(p, new AcademyNetwork.Hud(s.course.ordinal(), phase, remaining, s.progress.clone(), s.attempts, flags, (float)w.east(), (float)w.south()));
      s.hudDirty = false;
      s.lastHud = s.ticks;
   }

   /** An objective moved: a tick with a short label (and the HUD). */
   static void tick(Session s, ServerPlayer p, int objective, String key, String... args) {
      s.hudDirty = true;
      AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_TICK, s.course.ordinal(), objective, key, args));
   }

   /** A coaching line (at most two a second). */
   static void note(Session s, ServerPlayer p, String key, String... args) {
      if (s.ticks - s.lastNote < 10L) {
         return;
      }
      s.lastNote = s.ticks;
      AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_NOTE, s.course.ordinal(), 0, key, args));
   }

   static void busted(Session s, ServerPlayer p, String key) {
      s.hudDirty = true;
      AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_BUSTED, s.course.ordinal(), s.attempts, key));
   }

   /** A steel ring for a paper hit, heard after the sound's travel time from the target. */
   static void ring(ServerPlayer p, Vec3 at, boolean gold) {
      double d = p.getEyePosition().distanceTo(at);
      int delay = (int)Math.round(d / 343.0 * 20.0);
      AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_RING, -1, delay, gold ? "gold" : "", Integer.toString((int)Math.round(d))));
   }

   /** Back to the start mark (stalk reset). */
   static void toStart(Session s, ServerPlayer p) {
      ServerLevel tl = grounds(p.server);
      if (tl == null || p.level() != tl) {
         return;
      }
      float[] a = s.kit.arrival();
      p.teleportTo(tl, s.origin.getX() + a[0], s.origin.getY() + a[1], s.origin.getZ() + a[2], a[3], a[4]);
      p.setDeltaMovement(Vec3.ZERO);
   }

   // ================================================================================================ hooks

   static void dressed(ServerPlayer p, Whitetail deer) {
      Session s = SESSIONS.get(p.getUUID());
      ServerLevel tl = grounds(p.server);
      if (s != null && tl != null && s.phase == Session.Phase.ACTIVE) {
         s.kit.dressed(s, tl, p, deer);
      }
   }

   static void inspected(ServerPlayer p, TrailMark m) {
      Session s = SESSIONS.get(p.getUUID());
      ServerLevel tl = grounds(p.server);
      if (s != null && tl != null && s.phase == Session.Phase.ACTIVE) {
         s.kit.inspected(s, tl, p, m);
      }
   }

   // ================================================================================================ ticking

   @SubscribeEvent
   public static void serverTick(ServerTickEvent.Post e) {
      clock++;
      MinecraftServer server = e.getServer();
      if (!PENDING.isEmpty()) {
         for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Pending> en = it.next();
            if (--en.getValue().ticks > 0) {
               continue;
            }
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p != null) {
               try {
                  begin(p, en.getValue().course);
               } catch (RuntimeException ex) {
                  LOG.error("Frontier Hunts academy: start failed", ex);
               }
            }
         }
      }
      if (!RECOVER.isEmpty()) {
         for (Iterator<Map.Entry<UUID, Integer>> it = RECOVER.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> en = it.next();
            int left = en.getValue() - 1;
            if (left > 0) {
               en.setValue(left);
               continue;
            }
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p != null && p.isAlive() && !SESSIONS.containsKey(p.getUUID())) {
               recover(p);
            }
         }
      }
      if (SESSIONS.isEmpty()) {
         return;
      }
      ServerLevel tl = grounds(server);
      for (Session s : new ArrayList<>(SESSIONS.values())) {
         ServerPlayer p = server.getPlayerList().getPlayer(s.player);
         if (p == null || tl == null || p.level() != tl) {
            continue;
         }
         try {
            tickSession(p, s, tl);
         } catch (RuntimeException ex) {
            LOG.error("Frontier Hunts academy: session tick failed, sending {} home", p.getGameProfile().getName(), ex);
            end(p, s, Session.Result.INTERRUPTED);
         }
      }
   }

   private static void tickSession(ServerPlayer p, Session s, ServerLevel tl) {
      s.ticks++;
      switch (s.phase) {
         case ACTIVE -> {
            s.kit.tick(s, tl, p);
            if (s.ticks % 100 == 0) {
               p.getFoodData().setFoodLevel(20);
               p.getFoodData().setSaturation(Math.max(5.0F, p.getFoodData().getSaturationLevel()));
               p.clearFire();
            }
            if (p.getY() < s.origin.getY() - 8 || CourseKit.horizontal(p.position(), s.world(0, 0, 0)) > 300.0) {
               toStart(s, p);
            }
            if (s.complete()) {
               s.phase = Session.Phase.PASSED;
               s.phaseTicks = 0;
               s.result = Session.Result.PASSED;
               s.hudDirty = true;
               TrainingStore.Record r = TrainingStore.get(p.server).peek(p.getUUID(), s.course);
               int elapsed = (int)(tl.getGameTime() - s.started);
               boolean best = r == null || r.bestTicks == 0 || elapsed < r.bestTicks;
               AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_PASSED, s.course.ordinal(), elapsed, s.course.lang("title"), best ? "1" : "0"));
            } else if (tl.getGameTime() >= s.deadline) {
               s.phase = Session.Phase.FAILED;
               s.phaseTicks = 0;
               s.result = Session.Result.TIMEOUT;
               s.hudDirty = true;
               AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_FAILED, s.course.ordinal(), 0, "academy.frontierhunts.failed.timeout"));
            }
         }
         case PASSED -> {
            if (++s.phaseTicks >= PASSED_TICKS) {
               s.phase = Session.Phase.LEAVING;
               s.phaseTicks = 0;
               AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_DEPART, s.course.ordinal(), DEPART_TICKS, ""));
            }
         }
         case FAILED -> {
            if (++s.phaseTicks >= FAILED_TICKS) {
               s.phase = Session.Phase.LEAVING;
               s.phaseTicks = 0;
               AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_DEPART, s.course.ordinal(), DEPART_TICKS, ""));
            }
         }
         case LEAVING -> {
            if (++s.phaseTicks >= DEPART_TICKS) {
               end(p, s, s.result == null ? Session.Result.ABANDONED : s.result);
               return;
            }
         }
      }
      if (s.hudDirty && s.ticks - s.lastHud >= 2 || s.ticks - s.lastHud >= 100) {
         sendHud(p, s, tl);
      }
   }

   /** Finishes whatever an interrupted session left behind (login, respawn, arrival without a session). */
   private static void recover(ServerPlayer p) {
      TrainingStore store = TrainingStore.get(p.server);
      HomeState.PlayerHost host = new HomeState.PlayerHost(p);
      TrainingFlow.Recovery r;
      try {
         r = TrainingFlow.recover(host, store);
      } catch (RuntimeException ex) {
         LOG.error("Frontier Hunts academy: recovery failed for {}", p.getGameProfile().getName(), ex);
         r = TrainingFlow.Recovery.STRANDED;
      }
      switch (r) {
         case RESTORED, RESTORED_BACKUP -> {
            if (Academy.inTraining(p)) {
               HomeState.spawn(p);
            }
            HomeState.sweep(p);
            AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_HOME, -1, Session.Result.INTERRUPTED.ordinal(), "academy.frontierhunts.recovered"));
            LOG.info("Frontier Hunts academy: restored {} after an interrupted session ({})", p.getGameProfile().getName(), r);
         }
         case STRANDED -> {
            HomeState.sweep(p);
            if (!p.isCreative() && !p.isSpectator()) {
               HomeState.spawn(p);
               AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_HOME, -1, Session.Result.INTERRUPTED.ordinal(), "academy.frontierhunts.stranded"));
            }
         }
         default -> {
         }
      }
      sendState(p);
   }

   // ================================================================================================ lifecycle events

   @SubscribeEvent
   public static void login(PlayerEvent.PlayerLoggedInEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         RECOVER.put(p.getUUID(), 5);
      }
   }

   @SubscribeEvent
   public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
      if (!(e.getEntity() instanceof ServerPlayer p)) {
         return;
      }
      UUID id = p.getUUID();
      PENDING.remove(id);
      RECOVER.remove(id);
      REQUESTS.remove(id);
      MOVING.remove(id);
      Session s = SESSIONS.remove(id);
      if (s != null) {
         // the home record stays in the player's file (saved right after this event) and is finished at the next login
         cleanup(s, grounds(p.server));
      }
   }

   @SubscribeEvent
   public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
      if (e.getEntity() instanceof ServerPlayer p && (TrainingFlow.hasRecord(new HomeState.PlayerHost(p)) || Academy.inTraining(p))) {
         Session s = SESSIONS.remove(p.getUUID());
         if (s != null) {
            cleanup(s, grounds(p.server));
         }
         RECOVER.put(p.getUUID(), 1);
      }
   }

   @SubscribeEvent
   public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || MOVING.contains(p.getUUID())) {
         return;
      }
      if (e.getFrom() == Academy.TRAINING) {
         Session s = SESSIONS.remove(p.getUUID());
         if (s != null) {
            cleanup(s, grounds(p.server));
         }
         HomeState.PlayerHost host = new HomeState.PlayerHost(p);
         if (TrainingFlow.hasRecord(host) || TrainingStore.get(p.server).hasHome(p.getUUID())) {
            TrainingFlow.forcedExit(host, TrainingStore.get(p.server));
            AcademyNetwork.send(p, AcademyNetwork.Cue.of(AcademyNetwork.C_HOME, -1, Session.Result.INTERRUPTED.ordinal(), "academy.frontierhunts.left"));
            LOG.info("Frontier Hunts academy: {} left the training grounds by another teleport; home state restored in place", p.getGameProfile().getName());
         }
         HomeState.sweep(p);
         sendState(p);
      } else if (e.getTo() == Academy.TRAINING && !SESSIONS.containsKey(p.getUUID()) && !p.isCreative() && !p.isSpectator()) {
         RECOVER.put(p.getUUID(), 1); // walked in without a session: sent back to their spawn
      }
   }

   @SubscribeEvent
   public static void stopping(ServerStoppingEvent e) {
      ServerLevel tl = grounds(e.getServer());
      for (Session s : SESSIONS.values()) {
         cleanup(s, tl);
      }
      SESSIONS.clear();
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent e) {
      SESSIONS.clear();
      PENDING.clear();
      RECOVER.clear();
      REQUESTS.clear();
      COOLDOWN.clear();
      MOVING.clear();
      PLANNED.clear();
   }

   // ================================================================================================ guards

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void death(LivingDeathEvent e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || !Academy.inTraining(p)) {
         return;
      }
      e.setCanceled(true);
      p.setHealth(p.getMaxHealth());
      Session s = SESSIONS.get(p.getUUID());
      if (s != null) {
         if (s.phase != Session.Phase.LEAVING) {
            s.result = s.phase == Session.Phase.PASSED ? Session.Result.PASSED : Session.Result.HURT;
            s.phase = Session.Phase.LEAVING;
            s.phaseTicks = DEPART_TICKS - 2;
         }
      } else {
         RECOVER.put(p.getUUID(), 1);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void damage(LivingIncomingDamageEvent e) {
      if (e.getEntity() instanceof ServerPlayer p && Academy.inTraining(p) && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         e.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void join(EntityJoinLevelEvent e) {
      if (e.getLevel().isClientSide) {
         return;
      }
      Entity en = e.getEntity();
      if (Academy.isTrainingLevel(e.getLevel())) {
         if (en instanceof Player) {
            return;
         }
         boolean ours = en.getTags().contains(Academy.TAG);
         if (en instanceof ItemEntity || en instanceof ExperienceOrb) {
            e.setCanceled(true);
         } else if (ours && e.loadedFromDisk()) {
            e.setCanceled(true); // a leftover from a session that no longer runs
         } else if (en instanceof Mob && !ours) {
            e.setCanceled(true); // no natural or modded spawns in the grounds
         }
      } else if (en instanceof ItemEntity it && HomeState.lent(it.getItem())) {
         e.setCanceled(true); // lent gear never exists outside the grounds
      }
   }

   @SubscribeEvent
   public static void toss(ItemTossEvent e) {
      if (e.getPlayer() instanceof ServerPlayer p && Academy.inTraining(p)) {
         ItemStack st = e.getEntity().getItem().copy();
         e.setCanceled(true);
         if (!p.getInventory().add(st) && !st.isEmpty()) {
            p.getInventory().placeItemBackInInventory(st);
         }
         p.inventoryMenu.broadcastChanges();
      }
   }

   private static boolean sealed(Player p) {
      return p != null && Academy.inTraining(p) && !p.isCreative();
   }

   @SubscribeEvent
   public static void breakBlock(BlockEvent.BreakEvent e) {
      if (sealed(e.getPlayer())) {
         e.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void placeBlock(BlockEvent.EntityPlaceEvent e) {
      if (e.getEntity() instanceof Player p && sealed(p)) {
         e.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void useBlock(PlayerInteractEvent.RightClickBlock e) {
      if (!sealed(e.getEntity())) {
         return;
      }
      if (e.getLevel().getBlockState(e.getPos()).getBlock() instanceof ShootingTarget) {
         return; // pull your arrows out of the paper
      }
      e.setUseBlock(TriState.FALSE);
      if (e.getItemStack().getItem() instanceof net.minecraft.world.item.BlockItem) {
         e.setUseItem(TriState.FALSE);
      }
   }

   @SubscribeEvent
   public static void useItem(PlayerInteractEvent.RightClickItem e) {
      if (e.getEntity() instanceof ServerPlayer p && Academy.inTraining(p)) {
         Session s = SESSIONS.get(p.getUUID());
         ServerLevel tl = grounds(p.server);
         if (s != null && tl != null && s.phase == Session.Phase.ACTIVE) {
            s.kit.usedItem(s, tl, p, e.getItemStack());
         }
      }
   }

   @SubscribeEvent
   public static void playerTick(PlayerTickEvent.Post e) {
      if (e.getEntity() instanceof ServerPlayer p && (p.tickCount + p.getId()) % 100 == 0 && !Academy.inTraining(p)) {
         HomeState.sweep(p); // lent gear that slipped out somehow (another mod's teleport, a backpack...) is taken back
      }
   }

   // ================================================================================================ commands support

   static int forceReturn(ServerPlayer p) {
      Session s = SESSIONS.get(p.getUUID());
      if (s != null) {
         end(p, s, Session.Result.INTERRUPTED);
         return 1;
      }
      if (TrainingFlow.hasRecord(new HomeState.PlayerHost(p)) || Academy.inTraining(p)) {
         recover(p);
         return 1;
      }
      return 0;
   }

   static String status(ServerPlayer p) {
      Session s = SESSIONS.get(p.getUUID());
      if (s == null) {
         return PENDING.containsKey(p.getUUID()) ? "departing" : "not training";
      }
      StringBuilder b = new StringBuilder(s.course.key).append(" · slot ").append(s.slot).append(" · ").append(s.phase).append(" ·");
      for (int i = 0; i < s.progress.length; i++) {
         b.append(' ').append(s.course.objectives[i]).append(' ').append(s.progress[i]).append('/').append(s.course.targets[i]);
      }
      return b.toString();
   }

   static void complete(ServerPlayer p) {
      Session s = SESSIONS.get(p.getUUID());
      if (s != null && s.phase == Session.Phase.ACTIVE) {
         for (int i = 0; i < s.progress.length; i++) {
            s.set(i, s.course.targets[i]);
         }
      }
   }

   static int sessions() {
      return SESSIONS.size();
   }
}
