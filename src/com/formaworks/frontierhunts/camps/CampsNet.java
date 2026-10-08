package com.formaworks.frontierhunts.camps;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
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
 * Network for camps, guided hunts, records and events.
 * C2S {@link Req}: an action id plus up to 8 short string arguments, every one re-validated on the server.
 * S2C {@link View}: JSON for the open camp/records screen. S2C {@link Live}: public board/event state for renderers.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class CampsNet {
   public static final int OPEN = 0;
   public static final int CAMP_FOUND = 1;
   public static final int CAMP_INVITE = 2;
   public static final int CAMP_ACCEPT = 3;
   public static final int CAMP_DECLINE = 4;
   public static final int CAMP_LEAVE = 5;
   public static final int CAMP_KICK = 6;
   public static final int CAMP_RESPAWN = 7;
   public static final int CAMP_UPGRADE = 8;
   public static final int CAMP_EDIT = 9;
   public static final int CAMP_BIND = 10;
   public static final int CAMP_ASK = 11;
   public static final int CAMP_DISBAND = 12;
   public static final int GUIDE_OFFER = 20;
   public static final int GUIDE_ACCEPT = 21;
   public static final int GUIDE_DECLINE = 22;
   public static final int GUIDE_CANCEL = 23;
   public static final int NPC_BOOK = 24;
   public static final int OPEN_FROM_BOARD = 30;
   /** client hook: set by the client setup to handle view/live payloads (never touched on a dedicated server) */
   public static Consumer<View> viewReceiver = v -> {
   };
   public static Consumer<Live> liveReceiver = v -> {
   };

   private CampsNet() {
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent event) {
      var r = event.registrar("1").optional();
      r.playToServer(Req.TYPE, Req.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp) {
            CampsActions.handle(sp, p);
         }
      }));
      r.playToClient(View.TYPE, View.CODEC, (p, ctx) -> ctx.enqueueWork(() -> viewReceiver.accept(p)));
      r.playToClient(Live.TYPE, Live.CODEC, (p, ctx) -> ctx.enqueueWork(() -> liveReceiver.accept(p)));
   }

   public static void send(ServerPlayer player, CustomPacketPayload payload) {
      if (player.connection != null && player.connection.hasChannel(payload.type())) {
         PacketDistributor.sendToPlayer(player, payload);
      }
   }

   public record Req(int op, List<String> args) implements CustomPacketPayload {
      public static final Type<Req> TYPE = new Type<>(CampsContent.id("camps_req"));
      public static final StreamCodec<FriendlyByteBuf, Req> CODEC = StreamCodec.of((buf, p) -> {
         buf.writeVarInt(p.op);
         int n = Math.min(8, p.args.size());
         buf.writeVarInt(n);
         for (int i = 0; i < n; i++) {
            String s = p.args.get(i) == null ? "" : p.args.get(i);
            buf.writeUtf(s.length() > 64 ? s.substring(0, 64) : s, 64);
         }
      }, buf -> {
         int op = buf.readVarInt();
         int n = buf.readVarInt();
         if (n < 0 || n > 8) {
            throw new IllegalArgumentException("bad camps request");
         }
         List<String> a = new ArrayList<>(n);
         for (int i = 0; i < n; i++) {
            a.add(buf.readUtf(64));
         }
         return new Req(op, List.copyOf(a));
      });

      public String arg(int i) {
         return i < this.args.size() ? this.args.get(i) : "";
      }

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record View(String json) implements CustomPacketPayload {
      public static final Type<View> TYPE = new Type<>(CampsContent.id("camps_view"));
      public static final StreamCodec<FriendlyByteBuf, View> CODEC = StreamCodec.of((buf, p) -> buf.writeUtf(p.json, 262144), buf -> new View(buf.readUtf(262144)));

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record Live(CompoundTag tag) implements CustomPacketPayload {
      public static final Type<Live> TYPE = new Type<>(CampsContent.id("camps_live"));
      public static final StreamCodec<FriendlyByteBuf, Live> CODEC = StreamCodec.of((buf, p) -> buf.writeNbt(p.tag), buf -> {
         CompoundTag t = buf.readNbt();
         return new Live(t == null ? new CompoundTag() : t);
      });

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
