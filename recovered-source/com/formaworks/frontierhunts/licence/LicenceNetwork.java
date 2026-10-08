package com.formaworks.frontierhunts.licence;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** [licence] One client -> server request: buy / claim at the licence counter (validated in {@link LicenceOffice}). */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class LicenceNetwork {
   private LicenceNetwork() {
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar r = event.registrar("1").optional();
      r.playToServer(Buy.TYPE, Buy.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp) {
            LicenceOffice.request(sp, p.what(), p.index(), p.pay());
         }
      }));
   }

   public record Buy(int what, int index, int pay) implements CustomPacketPayload {
      public static final Type<Buy> TYPE = new Type<>(FrontierHunts.id("licence_buy"));
      public static final StreamCodec<FriendlyByteBuf, Buy> CODEC = StreamCodec.of(
         (b, a) -> {
            b.writeByte(a.what);
            b.writeByte(a.index);
            b.writeByte(a.pay);
         },
         b -> new Buy(b.readByte(), b.readByte(), b.readByte())
      );

      @Override
      public Type<Buy> type() {
         return TYPE;
      }
   }
}
