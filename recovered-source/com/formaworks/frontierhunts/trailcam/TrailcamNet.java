package com.formaworks.frontierhunts.trailcam;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.expedition.CameraHub;
import com.formaworks.frontierhunts.expedition.CameraRegistry;
import com.formaworks.frontierhunts.expedition.LensView;
import com.formaworks.frontierhunts.expedition.TrailCameraBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Trail camera photo channel. The client asks for a camera's photos (sending the ids it already has developed), the
 * server answers with the scene records it lacks; the client develops them itself. All requests are validated like
 * the scouting network: reach to the console, a real camera/hub block, camera ownership, rate limits, bounded sizes.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = EventBusSubscriber.Bus.MOD)
public final class TrailcamNet {
   public static final int ROLL = 0;
   public static final int DELETE = 1;
   public static final int UPLINK = 2;
   public static final int HUB = 4;
   public static final int PURPOSE_ROLL = 0;
   public static final int PURPOSE_HUB = 1;
   private static final int MAX_ENTRIES = 64;
   private static final int MAX_HAVE = 128;
   /** keep a single packet well under the 1 MiB custom payload limit; the client asks again for the rest */
   private static final int SCENE_BUDGET = 640 * 1024;
   /** client hook, set by client code (never loaded on a dedicated server) */
   public static Consumer<Photos> receiver = p -> {
   };
   private static final Map<UUID, Long> LAST = new HashMap<>();
   private static final Map<UUID, Long> LAST_UPLINK = new HashMap<>();

   private TrailcamNet() {
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar r = event.registrar("1");
      r.playToServer(Request.TYPE, Request.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp) {
            handle(sp, p);
         }
      }));
      r.playToClient(Photos.TYPE, Photos.CODEC, (p, ctx) -> ctx.enqueueWork(() -> receiver.accept(p)));
   }

   // ------------------------------------------------------------------------------------------------ server

   static boolean owns(ServerPlayer player, CameraRegistry.Station station) {
      return station.owner == null || station.owner.equals(player.getUUID()) || player.hasPermissions(2);
   }

   private static void handle(ServerPlayer player, Request req) {
      long now = player.level().getGameTime();
      Long last = LAST.get(player.getUUID());
      if (last != null && now >= last && now - last < 2L) {
         return;
      }
      LAST.put(player.getUUID(), now);
      if (LAST.size() > 256) {
         LAST.entrySet().removeIf(e -> now - e.getValue() > 200L || now < e.getValue());
         LAST_UPLINK.entrySet().removeIf(e -> now - e.getValue() > 2000L || now < e.getValue());
      }
      if (!player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
         return;
      }
      BlockPos console = req.console();
      // [phone] the Field Phone (or a retired base station opened through it) is a console for the hunter's own cameras
      boolean phone = com.formaworks.frontierhunts.phone.PhoneCams.PHONE.equals(console);
      boolean hub;
      if (phone) {
         if (!com.formaworks.frontierhunts.phone.PhoneCams.mayUse(player)) {
            return;
         }
         hub = true;
      } else {
         if (player.blockPosition().distSqr(console) > 144.0 || !level.mayInteract(player, console) || !level.hasChunkAt(console)) {
            return;
         }
         Block block = level.getBlockState(console).getBlock();
         hub = block instanceof CameraHub;
         if (!hub && !(block instanceof TrailCameraBlock)) {
            return;
         }
      }
      Set<Long> have = new HashSet<>();
      for (long id : req.have()) {
         have.add(id);
      }
      CameraRegistry registry = CameraRegistry.get(level);
      if (req.action() == HUB) {
         if (hub) {
            sendHub(player, phone ? com.formaworks.frontierhunts.phone.PhoneCams.mine(player) : registry.nearby(level, console, 48), console, have);
         }
         return;
      }
      BlockPos cameraPos = hub ? req.camera() : console;
      CameraRegistry.Station station = phone ? com.formaworks.frontierhunts.phone.PhoneCams.own(player, cameraPos) : registry.find(level, cameraPos);
      if (station == null || !owns(player, station)) {
         return;
      }
      switch (req.action()) {
         case DELETE -> {
            if (station.roll.removeIf(c -> c.scene() != null && TrailcamScene.photoId(c.scene()) == req.arg())) {
               registry.touch();
            }
            sendRoll(player, station, have);
         }
         case UPLINK -> uplink(player, level, station, req.arg());
         default -> sendRoll(player, station, have);
      }
   }

   private static void sendRoll(ServerPlayer player, CameraRegistry.Station station, Set<Long> have) {
      List<Entry> entries = new ArrayList<>();
      int budget = SCENE_BUDGET;
      boolean more = false;
      for (int i = station.roll.size() - 1; i >= 0 && entries.size() < MAX_ENTRIES; i--) {
         CameraRegistry.Capture c = station.roll.get(i);
         if (c.scene() == null) {
            continue;
         }
         long id = TrailcamScene.photoId(c.scene());
         CompoundTag scene = null;
         if (!have.contains(id)) {
            int size = c.scene().sizeInBytes();
            if (size <= budget) {
               scene = c.scene();
               budget -= size;
            } else {
               more = true;
            }
         }
         entries.add(new Entry(station.pos, id, legacy(c), scene));
      }
      send(player, new Photos(PURPOSE_ROLL, station.pos, station.label(), more, entries));
   }

   private static void sendHub(ServerPlayer player, List<CameraRegistry.Station> stations, BlockPos console, Set<Long> have) {
      List<Entry> entries = new ArrayList<>();
      int budget = SCENE_BUDGET;
      boolean more = false;
      for (CameraRegistry.Station station : stations) {
         if (!owns(player, station)) {
            continue;
         }
         for (int i = station.roll.size() - 1; i >= 0; i--) {
            CameraRegistry.Capture c = station.roll.get(i);
            if (c.scene() == null) {
               continue;
            }
            long id = TrailcamScene.photoId(c.scene());
            CompoundTag scene = null;
            if (!have.contains(id)) {
               int size = c.scene().sizeInBytes();
               if (size <= budget) {
                  scene = c.scene();
                  budget -= size;
               } else {
                  more = true;
               }
            }
            entries.add(new Entry(station.pos, id, legacy(c), scene));
            break;
         }
         if (entries.size() >= MAX_ENTRIES) {
            break;
         }
      }
      send(player, new Photos(PURPOSE_HUB, console, "", more, entries));
   }

   /** Opens the camera lens link at the spot a photo was taken so the client has that ground loaded to develop it. */
   private static void uplink(ServerPlayer player, ServerLevel level, CameraRegistry.Station station, long photo) {
      long now = level.getGameTime();
      Long last = LAST_UPLINK.get(player.getUUID());
      if (last != null && now >= last && now - last < 60L) {
         return;
      }
      CompoundTag scene = null;
      for (CameraRegistry.Capture c : station.roll) {
         if (c.scene() != null && TrailcamScene.photoId(c.scene()) == photo) {
            scene = c.scene();
            break;
         }
      }
      if (scene == null || !scene.getString("dim").equals(level.dimension().location().toString())) {
         return;
      }
      Vec3 lens = new Vec3(scene.getDouble("lx"), scene.getDouble("ly"), scene.getDouble("lz"));
      Direction facing = Direction.fromYRot(scene.getFloat("yaw"));
      BlockPos at = BlockPos.containing(lens.subtract(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.4)).subtract(0.0, 0.1, 0.0));
      if (!level.isInWorldBounds(at) || !level.getWorldBorder().isWithinBounds(at)) {
         return;
      }
      LAST_UPLINK.put(player.getUUID(), now);
      CameraRegistry.Station target = station;
      if (!station.pos.equals(at) || station.facing != facing) {
         // the camera was carried to another tree since: link to the spot the photo was taken, not the camera
         target = new CameraRegistry.Station();
         target.pos = at;
         target.facing = facing;
         target.dimension = station.dimension;
         target.name = station.name;
      }
      LensView.close(player);
      LensView.open(player, target);
   }

   private static CompoundTag legacy(CameraRegistry.Capture c) {
      CompoundTag t = new CompoundTag();
      t.put("traits", c.traits().save());
      t.putLong("at", c.at());
      t.putString("date", c.date());
      t.putInt("hour", c.hour());
      t.putString("over", c.over());
      t.putFloat("distance", c.distance());
      t.putFloat("bearing", c.bearing());
      t.putFloat("body", c.bodyYaw());
      t.putByte("stance", c.stance());
      t.putByte("sky", c.sky());
      return t;
   }

   private static void send(ServerPlayer player, Photos photos) {
      if (player.connection != null && player.connection.hasChannel(Photos.TYPE)) {
         PacketDistributor.sendToPlayer(player, photos);
      }
   }

   // ------------------------------------------------------------------------------------------------ payloads

   public record Request(BlockPos console, BlockPos camera, int action, long arg, long[] have) implements CustomPacketPayload {
      public static final Type<Request> TYPE = new Type<>(FrontierHunts.id("trailcam_request"));
      public static final StreamCodec<FriendlyByteBuf, Request> CODEC = StreamCodec.of((buf, r) -> {
         buf.writeBlockPos(r.console);
         buf.writeBlockPos(r.camera);
         buf.writeVarInt(r.action);
         buf.writeLong(r.arg);
         int n = Math.min(MAX_HAVE, r.have.length);
         buf.writeVarInt(n);
         for (int i = 0; i < n; i++) {
            buf.writeLong(r.have[i]);
         }
      }, buf -> {
         BlockPos console = buf.readBlockPos();
         BlockPos camera = buf.readBlockPos();
         int action = buf.readVarInt();
         long arg = buf.readLong();
         int n = buf.readVarInt();
         if (n < 0 || n > MAX_HAVE) {
            throw new IllegalArgumentException("Too many photo ids");
         }
         long[] have = new long[n];
         for (int i = 0; i < n; i++) {
            have[i] = buf.readLong();
         }
         return new Request(console, camera, action, arg, have);
      });

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** One photo on a camera. {@code scene} is null when the client said it already has this photo developed. */
   public record Entry(BlockPos camera, long id, CompoundTag legacy, CompoundTag scene) {
   }

   public record Photos(int purpose, BlockPos pos, String label, boolean more, List<Entry> entries) implements CustomPacketPayload {
      public static final Type<Photos> TYPE = new Type<>(FrontierHunts.id("trailcam_photos"));
      public static final StreamCodec<FriendlyByteBuf, Photos> CODEC = StreamCodec.of((buf, p) -> {
         buf.writeVarInt(p.purpose);
         buf.writeBlockPos(p.pos);
         buf.writeUtf(p.label, 64);
         buf.writeBoolean(p.more);
         buf.writeVarInt(p.entries.size());
         for (Entry e : p.entries) {
            buf.writeBlockPos(e.camera);
            buf.writeLong(e.id);
            buf.writeNbt(e.legacy);
            buf.writeBoolean(e.scene != null);
            if (e.scene != null) {
               buf.writeNbt(e.scene);
            }
         }
      }, buf -> {
         int purpose = buf.readVarInt();
         BlockPos pos = buf.readBlockPos();
         String label = buf.readUtf(64);
         boolean more = buf.readBoolean();
         int n = buf.readVarInt();
         if (n < 0 || n > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many photos");
         }
         List<Entry> list = new ArrayList<>(n);
         for (int i = 0; i < n; i++) {
            BlockPos cam = buf.readBlockPos();
            long id = buf.readLong();
            Tag legacy = buf.readNbt(net.minecraft.nbt.NbtAccounter.create(65536L));
            CompoundTag scene = null;
            if (buf.readBoolean()) {
               Tag s = buf.readNbt(net.minecraft.nbt.NbtAccounter.create(262144L));
               scene = s instanceof CompoundTag c ? c : null;
            }
            list.add(new Entry(cam, id, legacy instanceof CompoundTag c ? c : new CompoundTag(), scene));
         }
         return new Photos(purpose, pos, label, more, list);
      });

      public Photos {
         label = label == null ? "" : TrailcamScene.clip(label, 64);
         entries = List.copyOf(entries);
         if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many photos");
         }
      }

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
