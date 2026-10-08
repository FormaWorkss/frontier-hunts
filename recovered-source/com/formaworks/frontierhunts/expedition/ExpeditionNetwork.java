package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ExpeditionNetwork {
   public static Consumer<ExpeditionNetwork.Snapshot> receiver = var0 -> {
   };

   public static void register(RegisterPayloadHandlersEvent var0) {
      PayloadRegistrar var1 = var0.registrar("1");
      var1.playToClient(ExpeditionNetwork.Snapshot.TYPE, ExpeditionNetwork.Snapshot.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> receiver.accept(var0x)));
      var1.playToServer(ExpeditionNetwork.Request.TYPE, ExpeditionNetwork.Request.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
            if (var1x.player() instanceof ServerPlayer var2) {
               ExpeditionService.request(var2, var0x);
            }
         }));
   }

   private ExpeditionNetwork() {
   }

   public static record Request(int action, String value) implements CustomPacketPayload {
      public static final Type<ExpeditionNetwork.Request> TYPE = new Type(FrontierHunts.id("expedition_request"));
      public static final StreamCodec<FriendlyByteBuf, ExpeditionNetwork.Request> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeVarInt(var1.action);
         var0.writeUtf(var1.value, 80);
      }, var0 -> new ExpeditionNetwork.Request(var0.readVarInt(), var0.readUtf(80)));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Snapshot(String dimension, String json) implements CustomPacketPayload {
      public static final Type<ExpeditionNetwork.Snapshot> TYPE = new Type(FrontierHunts.id("expedition_snapshot"));
      public static final StreamCodec<FriendlyByteBuf, ExpeditionNetwork.Snapshot> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeUtf(var1.dimension, 256);
         var0.writeUtf(var1.json, 16000);
      }, var0 -> new ExpeditionNetwork.Snapshot(var0.readUtf(256), var0.readUtf(16000)));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
