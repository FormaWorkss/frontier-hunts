package com.formaworks.frontierhunts.landscape.ride.boat;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.vehicle.Boat;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** [1.1.0] The boat driver's keys, client -> server, so everyone sees the oars and the motor and the server burns fuel. */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class BoatNet {
   private BoatNet() {
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent event) {
      event.registrar("1").optional().playToServer(Input.TYPE, Input.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp && sp.level().getEntity(p.id()) instanceof Boat b && b instanceof FrontierBoat fb
            && b.getControllingPassenger() == sp) {
            fb.setInputBits(p.bits() & 15);
         }
      }));
   }

   static void send(Input input) {
      PacketDistributor.sendToServer(input);
   }

   public record Input(int id, byte bits) implements CustomPacketPayload {
      public static final Type<Input> TYPE = new Type<>(FrontierHunts.id("boat_input"));
      public static final StreamCodec<FriendlyByteBuf, Input> CODEC = StreamCodec.of((b, a) -> {
         b.writeVarInt(a.id);
         b.writeByte(a.bits);
      }, b -> new Input(b.readVarInt(), b.readByte()));

      @Override
      public Type<Input> type() {
         return TYPE;
      }
   }
}
