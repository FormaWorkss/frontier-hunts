package com.formaworks.frontierhunts.optics;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * [1.1.0] Glassing far country. While a player looks through binoculars, a rangefinder or a magnified scope, the server
 * keeps the land along the line of sight loaded (only land that already exists - nothing is generated for it - and
 * without ticking it), finds the wildlife in the field of view out to {@link #RANGE} blocks that the hills don't hide,
 * and sends it to that player twice a second. The client draws those animals inside the optic only, over the Distant
 * Horizons terrain, and the rangefinder can range them. Everything is throttled: a handful of chunks a scan, at most
 * {@link #MAX_CHUNKS} kept per player, tickets that lapse on their own ten seconds after the glassing stops.
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class Glassing {
   private Glassing() {
   }

   public static final double RANGE = 640.0, NEAR = 40.0;
   static final int MAX_CHUNKS = 72, NEW_PER_SCAN = 4, MAX_SEEN = 24;
   static final TicketType<ChunkPos> TICKET = TicketType.create("frontierhunts_glassing", Comparator.comparingLong(ChunkPos::toLong), 200);

   record Look(float yaw, float pitch, float zoom, long until) {
   }

   static final Map<UUID, Look> LOOKS = new ConcurrentHashMap<>();
   static final Map<UUID, List<ChunkPos>> HELD = new ConcurrentHashMap<>();
   /** chunk -> exists on disk (filled asynchronously) */
   static final Map<Long, Boolean> EXISTS = new ConcurrentHashMap<>();

   // ------------------------------------------------------------------------------------------ network

   @EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
   public static final class Net {
      private Net() {
      }

      public static volatile Consumer<Far> RECEIVER = f -> {
      };

      @SubscribeEvent
      public static void register(RegisterPayloadHandlersEvent event) {
         var r = event.registrar("1").optional();
         r.playToServer(Glass.TYPE, Glass.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
               if (p.on()) {
                  LOOKS.put(sp.getUUID(), new Look(p.yaw(), Mth.clamp(p.pitch(), -90.0F, 90.0F), Mth.clamp(p.zoom(), 1.0F, 40.0F),
                     sp.serverLevel().getGameTime() + 30));
               } else {
                  LOOKS.remove(sp.getUUID());
               }
            }
         }));
         r.playToClient(Far.TYPE, Far.CODEC, (p, ctx) -> ctx.enqueueWork(() -> RECEIVER.accept(p)));
      }
   }

   /** client -> server: looking through an optic now (or not any more) */
   public record Glass(boolean on, float yaw, float pitch, float zoom) implements CustomPacketPayload {
      public static final Type<Glass> TYPE = new Type<>(FrontierHunts.id("glass"));
      public static final StreamCodec<FriendlyByteBuf, Glass> CODEC = StreamCodec.of((b, g) -> {
         b.writeBoolean(g.on);
         b.writeFloat(g.yaw);
         b.writeFloat(g.pitch);
         b.writeFloat(g.zoom);
      }, b -> new Glass(b.readBoolean(), b.readFloat(), b.readFloat(), b.readFloat()));

      @Override
      public Type<Glass> type() {
         return TYPE;
      }
   }

   /** one animal seen far off: its id on the server, type, place and heading, and its synced looks (coat, antlers ...) */
   public record Seen(int id, int type, double x, double y, double z, float yaw, float speed, List<SynchedEntityData.DataValue<?>> data) {
   }

   /** server -> client: what is out there in the optic */
   public record Far(List<Seen> seen) implements CustomPacketPayload {
      public static final Type<Far> TYPE = new Type<>(FrontierHunts.id("glass_far"));
      public static final StreamCodec<RegistryFriendlyByteBuf, Far> CODEC = StreamCodec.of((b, f) -> {
         b.writeVarInt(f.seen.size());
         for (Seen s : f.seen) {
            b.writeVarInt(s.id);
            b.writeVarInt(s.type);
            b.writeDouble(s.x);
            b.writeDouble(s.y);
            b.writeDouble(s.z);
            b.writeFloat(s.yaw);
            b.writeFloat(s.speed);
            ClientboundSetEntityDataPacket.STREAM_CODEC.encode(b, new ClientboundSetEntityDataPacket(s.id, s.data));
         }
      }, b -> {
         int n = Math.min(64, b.readVarInt());
         List<Seen> out = new ArrayList<>(n);
         for (int i = 0; i < n; i++) {
            int id = b.readVarInt(), type = b.readVarInt();
            double x = b.readDouble(), y = b.readDouble(), z = b.readDouble();
            float yaw = b.readFloat(), speed = b.readFloat();
            List<SynchedEntityData.DataValue<?>> data = ClientboundSetEntityDataPacket.STREAM_CODEC.decode(b).packedItems();
            out.add(new Seen(id, type, x, y, z, yaw, speed, data));
         }
         return new Far(out);
      });

      @Override
      public Type<Far> type() {
         return TYPE;
      }
   }

   // ------------------------------------------------------------------------------------------ server

   @SubscribeEvent
   public static void tick(ServerTickEvent.Post event) {
      if (LOOKS.isEmpty() || event.getServer().getTickCount() % 10 != 3) {
         return;
      }
      for (var en : LOOKS.entrySet()) {
         ServerPlayer p = event.getServer().getPlayerList().getPlayer(en.getKey());
         if (p == null || p.serverLevel().getGameTime() > en.getValue().until()) {
            LOOKS.remove(en.getKey());
            HELD.remove(en.getKey());
            continue;
         }
         try {
            scan(p, en.getValue());
         } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("frontierhunts").debug("[Frontier Hunts] glassing scan failed", e);
         }
      }
   }

   @SubscribeEvent
   public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
      LOOKS.remove(event.getEntity().getUUID());
      HELD.remove(event.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent event) {
      LOOKS.clear();
      HELD.clear();
      EXISTS.clear();
   }

   static void scan(ServerPlayer p, Look look) {
      ServerLevel level = p.serverLevel();
      Vec3 eye = p.getEyePosition();
      Vec3 dir = Vec3.directionFromRotation(look.pitch(), look.yaw());
      double half = Math.toRadians(Mth.clamp(36.0 / look.zoom() + 1.5, 1.5, 22.0));
      double cos = Math.cos(half);
      hold(p, level, eye, dir, half);
      double spread = RANGE * Math.sin(half) + 8.0;
      AABB box = new AABB(eye, eye.add(dir.scale(RANGE))).inflate(spread, 40.0, spread);
      List<Mob> found = level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m.getType().getCategory() == MobCategory.CREATURE
         && BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getNamespace().equals(FrontierHunts.ID));
      List<Seen> seen = new ArrayList<>();
      found.sort(Comparator.comparingDouble(m -> m.distanceToSqr(eye)));
      for (Mob m : found) {
         Vec3 c = m.position().add(0.0, m.getBbHeight() * 0.6, 0.0);
         Vec3 d = c.subtract(eye);
         double dist = d.length();
         if (dist < NEAR || dist > RANGE || d.dot(dir) / dist < cos || !clear(level, eye, c)) {
            continue;
         }
         var data = m.getEntityData().getNonDefaultValues();
         seen.add(new Seen(m.getId(), BuiltInRegistries.ENTITY_TYPE.getId(m.getType()), m.getX(), m.getY(), m.getZ(), m.getYRot(),
            (float)m.getDeltaMovement().horizontalDistance(), data == null ? List.of() : data));
         if (seen.size() >= MAX_SEEN) {
            break;
         }
      }
      PacketDistributor.sendToPlayer(p, new Far(seen));
   }

   /** nothing of the land stands above the line from the eye to the animal (heightmaps of loaded land) */
   static boolean clear(ServerLevel level, Vec3 from, Vec3 to) {
      Vec3 d = to.subtract(from);
      double len = d.length();
      int steps = (int)(len / 4.0);
      for (int i = 2; i < steps; i++) {
         double t = (double)i / steps;
         double x = from.x + d.x * t, y = from.y + d.y * t, z = from.z + d.z * t;
         int bx = Mth.floor(x), bz = Mth.floor(z);
         if (level.getChunkSource().getChunkNow(bx >> 4, bz >> 4) == null) {
            continue;
         }
         if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) > y + 0.6) {
            return false;
         }
      }
      return true;
   }

   /** keep the existing land along the line of sight loaded (entities in, no ticking), a few chunks at a time */
   static void hold(ServerPlayer p, ServerLevel level, Vec3 eye, Vec3 dir, double half) {
      List<ChunkPos> want = new ArrayList<>();
      int view = p.server.getPlayerList().getViewDistance();
      double from = Math.max(48.0, view * 16.0 - 24.0);
      Vec3 side = new Vec3(-dir.z, 0.0, dir.x).normalize();
      for (double s = from; s <= RANGE && want.size() < MAX_CHUNKS; s += 14.0) {
         double w = Math.min(48.0, s * Math.tan(half));
         for (double o = -w; o <= w + 0.01; o += 16.0) {
            Vec3 at = eye.add(dir.scale(s)).add(side.scale(o));
            ChunkPos cp = new ChunkPos(Mth.floor(at.x) >> 4, Mth.floor(at.z) >> 4);
            if (!want.contains(cp)) {
               want.add(cp);
            }
         }
      }
      int added = 0;
      List<ChunkPos> held = HELD.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
      held.clear();
      for (ChunkPos cp : want) {
         Boolean exists = EXISTS.get(cp.toLong());
         if (exists == null) {
            if (level.getChunkSource().getChunkNow(cp.x, cp.z) != null) {
               EXISTS.put(cp.toLong(), Boolean.TRUE);
               exists = Boolean.TRUE;
            } else if (EXISTS.size() < 20000) {
               EXISTS.put(cp.toLong(), Boolean.FALSE);
               long key = cp.toLong();
               level.getChunkSource().chunkMap.read(cp).thenAccept(tag -> EXISTS.put(key, tag.map(Glassing::full).orElse(false)));
               continue;
            }
         }
         if (!Boolean.TRUE.equals(exists)) {
            continue;
         }
         boolean loaded = level.getChunkSource().getChunkNow(cp.x, cp.z) != null;
         if (!loaded && added >= NEW_PER_SCAN) {
            continue;
         }
         if (!loaded) {
            added++;
         }
         level.getChunkSource().addRegionTicket(TICKET, cp, 0, cp);
         held.add(cp);
      }
   }

   /** a saved chunk that finished generating (so loading it generates nothing) */
   static boolean full(CompoundTag tag) {
      String status = tag.getString("Status");
      return status.equals("minecraft:full") || status.equals("full");
   }
}
