package com.formaworks.frontierhunts.prone;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * [rifle] Prone stance packets. C2S {@link Request}: "I want to be prone / stand up" (the server validates everything
 * and always answers the requester with its verdict). S2C {@link Sync}: entity id + prone flag, sent to the player
 * and every client tracking them, so everybody sees the same pose.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class ProneNetwork {
   private ProneNetwork() {
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent event) {
      var r = event.registrar("1").optional();
      r.playToServer(Request.TYPE, Request.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp) {
            Prone.request(sp, p.prone());
         }
      }));
      // runs on the client main thread; Prone.applyClient only touches common types (Level / Player)
      r.playToClient(Sync.TYPE, Sync.CODEC, (p, ctx) -> ctx.enqueueWork(() -> Prone.applyClient(ctx.player().level(), p.entity(), p.prone())));
   }

   static void send(ServerPlayer to, CustomPacketPayload payload) {
      if (to.connection != null && to.connection.hasChannel(payload.type())) {
         PacketDistributor.sendToPlayer(to, payload);
      }
   }

   public record Request(boolean prone) implements CustomPacketPayload {
      public static final Type<Request> TYPE = new Type<>(FrontierHunts.id("prone_request"));
      public static final StreamCodec<FriendlyByteBuf, Request> CODEC = StreamCodec.of(
         (buf, p) -> buf.writeBoolean(p.prone), buf -> new Request(buf.readBoolean()));

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record Sync(int entity, boolean prone) implements CustomPacketPayload {
      public static final Type<Sync> TYPE = new Type<>(FrontierHunts.id("prone_sync"));
      public static final StreamCodec<FriendlyByteBuf, Sync> CODEC = StreamCodec.of((buf, p) -> {
         buf.writeVarInt(p.entity);
         buf.writeBoolean(p.prone);
      }, buf -> new Sync(buf.readVarInt(), buf.readBoolean()));

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
