package com.formaworks.frontierhunts.workshop;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class FishingNetwork {
   public static void register(RegisterPayloadHandlersEvent var0) {
      var0.registrar("1").playToServer(FishingNetwork.Strike.TYPE, FishingNetwork.Strike.CODEC, (var0x, var1) -> var1.enqueueWork(() -> {
            if (var1.player() instanceof ServerPlayer var1x) {
               FishingActions.hook(var1x);
            }
         }));
   }

   public static record Strike() implements CustomPacketPayload {
      public static final Type<FishingNetwork.Strike> TYPE = new Type(FrontierHunts.id("fishing_strike"));
      public static final StreamCodec<FriendlyByteBuf, FishingNetwork.Strike> CODEC = StreamCodec.unit(new FishingNetwork.Strike());

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
