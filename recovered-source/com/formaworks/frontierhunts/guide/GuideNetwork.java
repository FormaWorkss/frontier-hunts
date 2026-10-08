package com.formaworks.frontierhunts.guide;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * [guide] Field School payloads. The server owns all progress; the client only shows it and asks for the few things
 * a player may decide (begin, skip, skip a lesson, mark the optional tips read). A few bytes per change.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class GuideNetwork {
   /** Set by the client setup; no-ops on a dedicated server. */
   public static Consumer<State> stateReceiver = s -> {
   };
   public static Consumer<Notice> noticeReceiver = n -> {
   };

   // State flags
   public static final int F_ENABLED = 1;
   public static final int F_SKIPPED = 2;
   public static final int F_WELCOMED = 4;
   public static final int F_GRADUATED = 8;
   public static final int F_TIPS = 16;
   public static final int F_KIT = 32;

   // Notice kinds
   public static final byte N_LESSON = 0;
   public static final byte N_HINT = 1;
   public static final byte N_TIP = 2;
   public static final byte N_GRADUATED = 3;

   // Hints (N_HINT ids)
   public static final byte H_WINDED = 0;
   public static final byte H_DROPPED = 1;
   public static final byte H_RAN = 2;
   public static final byte H_DRESSED = 3;
   public static final byte H_BLOOD = 4;
   public static final byte H_KIT = 5;
   public static final byte H_SPOTTED = 6;

   // Actions (client -> server)
   public static final byte A_BEGIN = 0;
   public static final byte A_SKIP_ALL = 1;
   public static final byte A_RESUME = 2;
   public static final byte A_SKIP_LESSON = 3;
   public static final byte A_READ_TIPS = 4;
   public static final byte A_SYNC = 5;

   private GuideNetwork() {
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar r = event.registrar("1").optional();
      r.playToClient(State.TYPE, State.CODEC, (p, ctx) -> ctx.enqueueWork(() -> stateReceiver.accept(p)));
      r.playToClient(Notice.TYPE, Notice.CODEC, (p, ctx) -> ctx.enqueueWork(() -> noticeReceiver.accept(p)));
      r.playToServer(Action.TYPE, Action.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp) {
            FieldSchool.action(sp, p.action(), p.arg());
         }
      }));
   }

   static void send(ServerPlayer player, CustomPacketPayload payload) {
      if (player.connection != null && player.connection.hasChannel(payload.type())) {
         PacketDistributor.sendToPlayer(player, payload);
      }
   }

   /** Server -> client: the hunter's whole Field School state. */
   public record State(int done, int flags, int progress, int tipsSeen) implements CustomPacketPayload {
      public static final Type<State> TYPE = new Type<>(FrontierHunts.id("fieldschool_state"));
      public static final StreamCodec<FriendlyByteBuf, State> CODEC = StreamCodec.of(
         (b, s) -> {
            b.writeVarInt(s.done);
            b.writeVarInt(s.flags);
            b.writeVarInt(s.progress);
            b.writeVarInt(s.tipsSeen);
         },
         b -> new State(b.readVarInt() & Lesson.ALL_MASK, b.readVarInt(), Math.clamp(b.readVarInt(), 0, 64), b.readVarInt())
      );

      public boolean has(int flag) {
         return (this.flags & flag) != 0;
      }

      @Override
      public Type<State> type() {
         return TYPE;
      }
   }

   /** Server -> client: something to show once (lesson complete, a hint, a field note, graduation). */
   public record Notice(byte kind, byte id, int arg) implements CustomPacketPayload {
      public static final Type<Notice> TYPE = new Type<>(FrontierHunts.id("fieldschool_notice"));
      public static final StreamCodec<FriendlyByteBuf, Notice> CODEC = StreamCodec.of(
         (b, n) -> {
            b.writeByte(n.kind);
            b.writeByte(n.id);
            b.writeVarInt(n.arg);
         },
         b -> new Notice(b.readByte(), b.readByte(), b.readVarInt())
      );

      @Override
      public Type<Notice> type() {
         return TYPE;
      }
   }

   /** Client -> server: a player decision. Validated and rate-limited in {@link FieldSchool#action}. */
   public record Action(byte action, byte arg) implements CustomPacketPayload {
      public static final Type<Action> TYPE = new Type<>(FrontierHunts.id("fieldschool_action"));
      public static final StreamCodec<FriendlyByteBuf, Action> CODEC = StreamCodec.of(
         (b, a) -> {
            b.writeByte(a.action);
            b.writeByte(a.arg);
         },
         b -> new Action(b.readByte(), b.readByte())
      );

      @Override
      public Type<Action> type() {
         return TYPE;
      }
   }
}
