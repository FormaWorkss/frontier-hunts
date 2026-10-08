package com.formaworks.frontierhunts.onboard;

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
 * [onboard] Handbook payloads. The server owns the task state (it reads the journal counters, Field School lessons,
 * academy records and vanilla stats); the client only shows it, asks for a fresh copy and may skip a Handbook-only
 * task. A dozen bytes per change.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class OnboardNetwork {
   /** Set by the client setup; a no-op on a dedicated server. */
   public static Consumer<State> receiver = s -> {
   };

   /** State flags. */
   public static final int F_OPEN = 1;
   public static final int F_LESSONS_OFF = 2;

   /** Actions (client -> server). */
   public static final byte A_SYNC = 0;
   public static final byte A_SKIP = 1;
   public static final byte A_UNSKIP = 2;

   private OnboardNetwork() {
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar r = event.registrar("1").optional();
      r.playToClient(State.TYPE, State.CODEC, (p, ctx) -> ctx.enqueueWork(() -> receiver.accept(p)));
      r.playToServer(Action.TYPE, Action.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp) {
            Onboarding.action(sp, p.action(), p.arg());
         }
      }));
   }

   static void send(ServerPlayer player, CustomPacketPayload payload) {
      if (player.connection != null && player.connection.hasChannel(payload.type())) {
         PacketDistributor.sendToPlayer(player, payload);
      }
   }

   /** Server -> client: done tasks (bits of {@link Handbook.Task}), skipped tasks, passed academy courses, flags. */
   public record State(int tasks, int skipped, int courses, int flags) implements CustomPacketPayload {
      public static final Type<State> TYPE = new Type<>(FrontierHunts.id("handbook_state"));
      public static final StreamCodec<FriendlyByteBuf, State> CODEC = StreamCodec.of(
         (b, s) -> {
            b.writeVarInt(s.tasks);
            b.writeVarInt(s.skipped);
            b.writeVarInt(s.courses);
            b.writeVarInt(s.flags);
         },
         b -> new State(b.readVarInt() & Handbook.ALL, b.readVarInt() & Handbook.ALL, b.readVarInt() & 0xFFFF, b.readVarInt() & 0xFF)
      );

      public boolean has(int flag) {
         return (this.flags & flag) != 0;
      }

      @Override
      public Type<State> type() {
         return TYPE;
      }
   }

   /** Client -> server. Validated and rate-limited in {@link Onboarding#action}. */
   public record Action(byte action, byte arg) implements CustomPacketPayload {
      public static final Type<Action> TYPE = new Type<>(FrontierHunts.id("handbook_action"));
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
