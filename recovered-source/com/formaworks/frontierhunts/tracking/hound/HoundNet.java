package com.formaworks.frontierhunts.tracking.hound;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * [hound3] Hound command payloads. The client only asks (an order from the command wheel / call key, or the list of
 * animals it could be put on); the server validates owner, distance, rate and the target before anything happens.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class HoundNet {
   public static final int HEEL = 0, STAY = 1, TRACK = 2, SEARCH = 3, COME = 4, INFO = 5;
   /** [hound4] stop where you are (stand still) / send him home to the kennel (the Hound Lead brings him back) */
   public static final int STOP = 6, DISMISS = 7, LAST = DISMISS;
   /** set by the client; a no-op on a dedicated server */
   public static Consumer<Info> infoReceiver = i -> {
   };
   /** set by the client: Hound Lead used (not sneaking) - opens the wheel when the hunter's hound is around, true if it did */
   public static java.util.function.Predicate<net.minecraft.world.entity.player.Player> leadWheel = p -> false;
   /** set by the client: open the command wheel for this hound (right-click / Hound Lead); a no-op on a server */
   public static Consumer<TrackingHound> openWheel = h -> {
   };

   private HoundNet() {
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent e) {
      PayloadRegistrar r = e.registrar("1");
      r.playToServer(Order.TYPE, Order.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp) {
            HoundCommands.order(sp, p.order(), p.target());
         }
      }));
      r.playToClient(Info.TYPE, Info.CODEC, (p, ctx) -> ctx.enqueueWork(() -> infoReceiver.accept(p)));
   }

   static void send(ServerPlayer p, CustomPacketPayload payload) {
      if (p.connection != null && p.connection.hasChannel(payload.type())) {
         PacketDistributor.sendToPlayer(p, payload);
      }
   }

   /** client -> server: an order (target = the wounded animal picked in the wheel, or null) */
   public record Order(int order, UUID target) implements CustomPacketPayload {
      public static final Type<Order> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("frontierhunts", "hound_order"));
      public static final StreamCodec<FriendlyByteBuf, Order> CODEC = StreamCodec.of((b, o) -> {
         b.writeByte(o.order);
         b.writeBoolean(o.target != null);
         if (o.target != null) {
            b.writeUUID(o.target);
         }
      }, b -> {
         int order = b.readByte();
         UUID t = b.readBoolean() ? b.readUUID() : null;
         return new Order(order, t);
      });

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** one animal the hunter hit, for the wheel's track list */
   public record Wound(UUID id, String label, int seconds, int metres, boolean dead) {
   }

   /** server -> client: what the hound could be put on */
   public record Info(String hound, List<Wound> wounds) implements CustomPacketPayload {
      public static final Type<Info> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("frontierhunts", "hound_info"));
      public static final StreamCodec<FriendlyByteBuf, Info> CODEC = StreamCodec.of((b, i) -> {
         b.writeUtf(i.hound, 64);
         b.writeByte(Math.min(i.wounds.size(), 6));
         for (int k = 0; k < Math.min(i.wounds.size(), 6); k++) {
            Wound w = i.wounds.get(k);
            b.writeUUID(w.id);
            b.writeUtf(w.label, 48);
            b.writeVarInt(w.seconds);
            b.writeVarInt(w.metres);
            b.writeBoolean(w.dead);
         }
      }, b -> {
         String h = b.readUtf(64);
         int n = Math.min(b.readByte(), 6);
         List<Wound> l = new ArrayList<>();
         for (int k = 0; k < n; k++) {
            l.add(new Wound(b.readUUID(), b.readUtf(48), b.readVarInt(), b.readVarInt(), b.readBoolean()));
         }
         return new Info(h, l);
      });

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
