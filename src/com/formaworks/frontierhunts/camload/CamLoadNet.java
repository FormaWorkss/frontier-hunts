package com.formaworks.frontierhunts.camload;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.expedition.CameraRegistry;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * [camload] Mod-bus registration (the ticket controller) and the tiny status exchange behind the camera screen's
 * "KEEPS ITS AREA LOADED" line: the screen asks once when it opens, the server answers whether that camera holds its area.
 * Holds no client classes (the answer is kept in a plain map), so it is safe on a dedicated server.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = EventBusSubscriber.Bus.MOD)
public final class CamLoadNet {
   private static final Map<UUID, Long> LAST = new HashMap<>();
   /** client side: camera position -> keeps its area loaded */
   private static final Map<Long, Boolean> KNOWN = new HashMap<>();

   private CamLoadNet() {
   }

   @SubscribeEvent
   public static void controllers(RegisterTicketControllersEvent event) {
      event.register(CameraChunkLoader.CONTROLLER);
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar r = event.registrar("1").optional();
      r.playToServer(Query.TYPE, Query.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp) {
            answer(sp, p.pos());
         }
      }));
      r.playToClient(Status.TYPE, Status.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (KNOWN.size() > 512) {
            KNOWN.clear();
         }
         KNOWN.put(p.pos().asLong(), p.loaded());
      }));
   }

   private static void answer(ServerPlayer player, BlockPos pos) {
      long now = player.serverLevel().getGameTime();
      Long last = LAST.get(player.getUUID());
      if (last != null && now - last < 4L && now >= last) {
         return; // rate limit
      }
      if (LAST.size() > 256) {
         LAST.clear();
      }
      LAST.put(player.getUUID(), now);
      ServerLevel level = player.serverLevel();
      CameraRegistry.Station station = CameraRegistry.get(level).find(level, pos);
      if (station == null || !(station.owner == null || station.owner.equals(player.getUUID()) || player.hasPermissions(2))) {
         return; // unknown camera or someone else's: say nothing
      }
      if (player.connection != null && player.connection.hasChannel(Status.TYPE)) {
         PacketDistributor.sendToPlayer(player, new Status(station.pos, CameraChunkLoader.serves(level, station.pos)));
      }
   }

   // ------------------------------------------------------------------------------------------------ client API

   /** Client: ask whether the camera at {@code pos} keeps its area loaded (call from a screen). */
   public static void query(BlockPos pos) {
      KNOWN.remove(pos.asLong());
      try {
         PacketDistributor.sendToServer(new Query(pos.immutable()));
      } catch (RuntimeException e) {
         // server without this channel: nothing to show
      }
   }

   /** Client: the last answer for this camera (false while unknown). */
   public static boolean keepsLoaded(BlockPos pos) {
      return Boolean.TRUE.equals(KNOWN.get(pos.asLong()));
   }

   public record Query(BlockPos pos) implements CustomPacketPayload {
      public static final Type<Query> TYPE = new Type<>(FrontierHunts.id("camload_query"));
      public static final StreamCodec<ByteBuf, Query> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Query::pos, Query::new);

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record Status(BlockPos pos, boolean loaded) implements CustomPacketPayload {
      public static final Type<Status> TYPE = new Type<>(FrontierHunts.id("camload_status"));
      public static final StreamCodec<ByteBuf, Status> CODEC = StreamCodec.composite(
         BlockPos.STREAM_CODEC, Status::pos, ByteBufCodecs.BOOL, Status::loaded, Status::new
      );

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
