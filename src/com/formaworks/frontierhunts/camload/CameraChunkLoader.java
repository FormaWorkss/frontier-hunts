package com.formaworks.frontierhunts.camload;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.expedition.CameraRegistry;
import com.formaworks.frontierhunts.expedition.TrailCameraBlock;
import com.formaworks.frontierhunts.trailcam.TrailcamConfig;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.common.world.chunk.TicketSet;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * [camload] Keeps the small area each trail camera watches loaded and entity-ticking, so real animals keep walking past
 * it (and it keeps photographing them) with no player near, also across restarts.
 *
 * <ul>
 * <li>Persistent NeoForge forced-chunk tickets, owned by the camera's block position ({@link #CONTROLLER}). At most 4
 * chunks per camera: the camera's own chunk plus the chunks carrying the most of its lens cone (70 deg, out to the
 * trigger range). A chunk wanted by several cameras is held by exactly one ticket.</li>
 * <li>Only cameras with battery left and room on the roll qualify, oldest placed first, within the server caps
 * ({@link CamLoadConfig}). Tickets are released when the camera is broken or taken down, the battery dies, the roll
 * fills, or chunk loading is switched off. On startup the validation callback drops every ticket whose camera no longer
 * exists or no longer qualifies before NeoForge re-applies them.</li>
 * <li>Light: one reconcile pass every 5 s (or the tick after a camera is placed or removed); a chunk that is not loaded yet
 * is first requested asynchronously with a short-lived warm-up ticket and only forced once it is in memory, so taking a
 * ticket never blocks the server thread on chunk IO; at most {@link #ADDS_PER_TICK} new tickets per tick.</li>
 * <li>Nothing spawns because of this: vanilla natural spawning still needs a player within 128 blocks (the local mob cap
 * has no player to count against), and the mod adds no spawner. Calm animals there have no player near, so perf's
 * {@code AiThrottle} runs them at its lowest tier. The only rule changed: a wildlife mob standing in a held chunk is not
 * discarded for being more than 128 blocks from a player in another part of the world (it is exactly what an unloaded
 * chunk would keep).</li>
 * </ul>
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class CameraChunkLoader {
   private static final Logger LOG = LoggerFactory.getLogger("Frontier Hunts trail cameras");
   public static final TicketController CONTROLLER = new TicketController(FrontierHunts.id("trail_camera_area"), CameraChunkLoader::validate);
   /** short-lived, non-persistent ticket that loads a chunk asynchronously before it is force-loaded */
   private static final TicketType<ChunkPos> WARM = TicketType.create("frontierhunts:trail_camera_warm", Comparator.comparingLong(ChunkPos::toLong), 300);
   public static final int MAX_CHUNKS = 4;
   static final int PERIOD = 100;
   static final int ADDS_PER_TICK = 4;
   static final int WARMS_PER_TICK = 8;
   private static final UUID NO_OWNER = new UUID(0L, 0L);

   /** chunk -> camera position whose ticket holds it, per dimension (mirrors the persistent tickets) */
   private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<BlockPos>> HELD = new HashMap<>();
   /** cameras whose area is kept loaded right now, per dimension */
   private static final Map<ResourceKey<Level>, Set<BlockPos>> SERVED = new HashMap<>();
   /** chunk -> tick a warm-up ticket was last asked for */
   private static final Map<ResourceKey<Level>, Long2LongOpenHashMap> WARMED = new HashMap<>();
   private static boolean dirty = true;

   private CameraChunkLoader() {
   }

   // ------------------------------------------------------------------------------------------------ hooks

   /** A camera was placed, removed or changed: reconcile on the next server tick instead of waiting for the period. */
   public static void changed() {
      dirty = true;
   }

   /** Whether this camera currently keeps its area loaded (for the camera screen). */
   public static boolean serves(ServerLevel level, BlockPos pos) {
      Set<BlockPos> s = SERVED.get(level.dimension());
      return s != null && s.contains(pos);
   }

   static boolean eligible(CameraRegistry.Station s) {
      return s.charge > 0 && s.roll.size() < CameraRegistry.ROLL;
   }

   static double range() {
      return Math.max(TrailcamConfig.dayRange(), TrailcamConfig.nightRange());
   }

   // ------------------------------------------------------------------------------------------------ startup validation

   /** NeoForge calls this per level on server start, before re-applying the saved tickets. */
   private static void validate(ServerLevel level, TicketHelper helper) {
      try {
         boolean on = CamLoadConfig.enabled();
         CameraRegistry registry = CameraRegistry.get(level);
         Long2ObjectOpenHashMap<BlockPos> held = HELD.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>());
         int kept = 0;
         int dropped = 0;
         for (Map.Entry<BlockPos, TicketSet> e : new ArrayList<>(helper.getBlockTickets().entrySet())) {
            BlockPos pos = e.getKey();
            CameraRegistry.Station station = registry.find(level, pos);
            if (!on || station == null || !eligible(station)) {
               helper.removeAllTickets(pos);
               dropped++;
               continue;
            }
            for (long c : e.getValue().nonTicking().toLongArray()) {
               helper.removeTicket(pos, c, false);
            }
            for (long c : e.getValue().ticking().toLongArray()) {
               if (held.containsKey(c) && !held.get(c).equals(pos)) {
                  helper.removeTicket(pos, c, true); // duplicate holder of a shared chunk
               } else {
                  held.put(c, pos.immutable());
               }
            }
            kept++;
         }
         for (UUID entity : new ArrayList<>(helper.getEntityTickets().keySet())) {
            helper.removeAllTickets(entity); // never issued by this controller
         }
         if (kept + dropped > 0) {
            LOG.info("Trail cameras in {}: {} keep their area loaded, {} released", level.dimension().location(), kept, dropped);
         }
      } catch (RuntimeException ex) {
         LOG.warn("Trail camera chunk tickets could not be validated; they are re-checked shortly", ex);
      }
      dirty = true;
   }

   // ------------------------------------------------------------------------------------------------ reconcile

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent.Post event) {
      MinecraftServer server = event.getServer();
      if (!dirty && server.getTickCount() % PERIOD != 0) {
         return;
      }
      dirty = false;
      try {
         reconcile(server);
      } catch (RuntimeException ex) {
         LOG.warn("Trail camera area loading failed this pass", ex);
      }
   }

   private static void reconcile(MinecraftServer server) {
      long now = server.getTickCount();
      Map<ResourceKey<Level>, Long2ObjectLinkedOpenHashMap<BlockPos>> want = new HashMap<>();
      Map<ResourceKey<Level>, Set<BlockPos>> served = new HashMap<>();
      if (CamLoadConfig.enabled() && server.overworld() != null) {
         CameraRegistry registry = CameraRegistry.get(server.overworld());
         List<CameraRegistry.Station> candidates = new ArrayList<>();
         for (CameraRegistry.Station s : registry.all()) {
            if (eligible(s)) {
               candidates.add(s);
            }
         }
         candidates.sort(Comparator.<CameraRegistry.Station>comparingLong(s -> s.placed).thenComparingLong(s -> s.pos.asLong()));
         int totalCap = CamLoadConfig.total();
         int ownerCap = CamLoadConfig.perPlayer();
         Object2IntOpenHashMap<UUID> perOwner = new Object2IntOpenHashMap<>();
         List<CameraRegistry.Station> stale = new ArrayList<>();
         List<ServerLevel> staleLevels = new ArrayList<>();
         double range = range();
         int total = 0;
         for (CameraRegistry.Station s : candidates) {
            if (total >= totalCap) {
               break;
            }
            UUID owner = s.owner == null ? NO_OWNER : s.owner;
            if (perOwner.getInt(owner) >= ownerCap) {
               continue;
            }
            ServerLevel level = server.getLevel(CameraRegistry.dimensionKey(s.dimension));
            if (level == null) {
               continue;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(s.pos.getX() >> 4, s.pos.getZ() >> 4); // never a blocking load
            if (chunk != null && !(chunk.getBlockState(s.pos).getBlock() instanceof TrailCameraBlock)) {
               stale.add(s); // the block is gone (edited out without a block update): forget it like the hub does
               staleLevels.add(level);
               continue;
            }
            total++;
            perOwner.addTo(owner, 1);
            ResourceKey<Level> key = level.dimension();
            served.computeIfAbsent(key, k -> new HashSet<>()).add(s.pos);
            Long2ObjectLinkedOpenHashMap<BlockPos> w = want.computeIfAbsent(key, k -> new Long2ObjectLinkedOpenHashMap<>());
            Long2ObjectOpenHashMap<BlockPos> held = HELD.get(key);
            for (long c : CameraFootprint.chunks(s.pos, s.facing, range, MAX_CHUNKS)) {
               BlockPos holder = held == null ? null : held.get(c);
               if (!w.containsKey(c) || s.pos.equals(holder)) {
                  w.put(c, s.pos); // shared chunks stay with the camera already holding them
               }
            }
         }
         for (int i = 0; i < stale.size(); i++) {
            registry.remove(staleLevels.get(i), stale.get(i).pos);
         }
      }

      SERVED.clear();
      SERVED.putAll(served);
      boolean pending = false;
      int adds = 0;
      int warms = 0;
      Set<ResourceKey<Level>> keys = new HashSet<>(HELD.keySet());
      keys.addAll(want.keySet());
      for (ResourceKey<Level> key : keys) {
         ServerLevel level = server.getLevel(key);
         if (level == null) {
            HELD.remove(key);
            continue;
         }
         Long2ObjectOpenHashMap<BlockPos> held = HELD.computeIfAbsent(key, k -> new Long2ObjectOpenHashMap<>());
         Long2ObjectLinkedOpenHashMap<BlockPos> w = want.getOrDefault(key, new Long2ObjectLinkedOpenHashMap<>());
         // release what no qualifying camera needs any more
         for (Long2ObjectMap.Entry<BlockPos> e : new ArrayList<>(held.long2ObjectEntrySet())) {
            long c = e.getLongKey();
            if (!w.containsKey(c)) {
               force(level, e.getValue(), c, false);
               held.remove(c);
            }
         }
         Long2LongOpenHashMap warmed = WARMED.computeIfAbsent(key, k -> new Long2LongOpenHashMap());
         for (Long2ObjectMap.Entry<BlockPos> e : w.long2ObjectEntrySet()) {
            long c = e.getLongKey();
            BlockPos owner = e.getValue();
            BlockPos cur = held.get(c);
            if (owner.equals(cur)) {
               continue;
            }
            if (cur != null) {
               // hand a shared chunk over: take the new ticket before dropping the old, so it never unloads in between
               force(level, owner, c, true);
               force(level, cur, c, false);
               held.put(c, owner);
               continue;
            }
            int cx = ChunkPos.getX(c);
            int cz = ChunkPos.getZ(c);
            if (level.getChunkSource().getChunkNow(cx, cz) != null) {
               if (adds < ADDS_PER_TICK) {
                  adds++;
                  force(level, owner, c, true);
                  held.put(c, owner);
                  warmed.remove(c);
               } else {
                  pending = true;
               }
            } else {
               pending = true;
               long asked = warmed.getOrDefault(c, Long.MIN_VALUE);
               if ((asked == Long.MIN_VALUE || now - asked > 200L || now < asked) && warms < WARMS_PER_TICK) {
                  warms++;
                  ChunkPos cp = new ChunkPos(cx, cz);
                  level.getChunkSource().addRegionTicket(WARM, cp, 0, cp); // loads asynchronously, expires by itself
                  warmed.put(c, now);
               }
            }
         }
         if (warmed.size() > 256) {
            warmed.clear();
         }
      }
      if (pending) {
         dirty = true; // keep going next tick until every wanted chunk is held
      }
   }

   private static void force(ServerLevel level, BlockPos owner, long chunk, boolean add) {
      CONTROLLER.forceChunk(level, owner, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), add, true);
   }

   // ------------------------------------------------------------------------------------------------ lifecycle

   @SubscribeEvent
   public static void onServerStarting(ServerAboutToStartEvent event) {
      reset();
   }

   @SubscribeEvent
   public static void onServerStopped(ServerStoppedEvent event) {
      reset();
   }

   private static void reset() {
      HELD.clear();
      SERVED.clear();
      WARMED.clear();
      dirty = true;
   }

   /**
    * A calm wildlife mob in a camera's held chunk is not discarded for being far from a player elsewhere in the world
    * (vanilla drops despawnable mobs beyond 128 blocks of the nearest player). Only that case: with a player within 128
    * blocks the normal rules run.
    */
   @SubscribeEvent
   public static void onDespawn(MobDespawnEvent event) {
      if (!(event.getEntity() instanceof WildlifeMob mob) || !(mob.level() instanceof ServerLevel level)) {
         return;
      }
      Long2ObjectOpenHashMap<BlockPos> held = HELD.get(level.dimension());
      if (held == null || held.isEmpty() || !held.containsKey(mob.chunkPosition().toLong())) {
         return;
      }
      if (level.getNearestPlayer(mob, MobCategory.CREATURE.getDespawnDistance()) == null) {
         event.setResult(MobDespawnEvent.Result.DENY);
      }
   }
}
