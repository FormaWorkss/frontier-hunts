package com.formaworks.frontierhunts.firsthunt;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** [1.2.7] First-hunt payloads: the server owns every step; the client shows the current one and asks for the few choices. */
@EventBusSubscriber(modid = "frontierhunts", bus = EventBusSubscriber.Bus.MOD)
public final class FirstHuntNetwork {
   public static Consumer<State> stateReceiver = s -> {
   };

   // flags
   public static final int F_STARTED = 1;
   public static final int F_HIDDEN = 2;
   public static final int F_INTRO = 4;
   /** [1.2.9] unused: there is no free permit any more */
   public static final int F_PERMIT_OFFER = 8;
   public static final int F_CLAIMED = 16;
   public static final int F_AREA = 32;
   public static final int F_AREA_CONFIRMED = 64;
   public static final int F_AREA_QUIET = 128;
   public static final int F_REGULATED = 256;
   public static final int F_LICENCE = 512;
   public static final int F_TAG = 1024;
   public static final int F_OPEN = 2048;
   public static final int F_SUSPENDED = 4096;
   public static final int F_CAMP = 8192;
   public static final int F_DOWNWIND = 16384;
   public static final int F_SCOUTING = 32768;
   /** [fharea] scouting because the deer known nearby are all in hard country (mountains, snow, water) */
   public static final int F_ROUGH = 65536;
   /** [fharea] the beginner area is in the dimension the hunter is in */
   public static final int F_AREA_HERE = 131072;

   // actions (client -> server)
   public static final byte A_SYNC = 0;
   public static final byte A_START = 1;
   public static final byte A_INTRO_SEEN = 2;
   public static final byte A_HIDE = 3;
   public static final byte A_SHOW = 4;
   public static final byte A_PERMIT = 5;
   public static final byte A_NEW_AREA = 6;
   public static final byte A_CLAIM = 7;

   private FirstHuntNetwork() {
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar r = event.registrar("1").optional();
      r.playToClient(State.TYPE, State.CODEC, (p, ctx) -> ctx.enqueueWork(() -> stateReceiver.accept(p)));
      r.playToServer(Action.TYPE, Action.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp) {
            FirstHunt.action(sp, p.action());
         }
      }));
   }

   static void send(ServerPlayer player, State s) {
      if (player.connection != null && player.connection.hasChannel(State.TYPE)) {
         PacketDistributor.sendToPlayer(player, s);
      }
   }

   /**
    * The whole first-hunt picture for one hunter. {@code done} is a bit per {@link FirstHunt.Step}; the licence part is
    * {@link com.formaworks.frontierhunts.licence.LicenceStatus} for whitetail; the area is a broad circle, not the deer.
    */
   public record State(int done, int flags, int months, int daysToOpen, int areaX, int areaZ, int areaR, int advice, int campX, int campZ,
      int stalk, int academy, int tagTokens) implements CustomPacketPayload {
      public static final Type<State> TYPE = new Type<>(FrontierHunts.id("first_hunt_state"));
      public static final StreamCodec<FriendlyByteBuf, State> CODEC = StreamCodec.of(
         (b, s) -> {
            b.writeVarInt(s.done);
            b.writeVarInt(s.flags);
            b.writeVarInt(s.months);
            b.writeVarInt(s.daysToOpen);
            b.writeInt(s.areaX);
            b.writeInt(s.areaZ);
            b.writeVarInt(s.areaR);
            b.writeVarInt(s.advice);
            b.writeInt(s.campX);
            b.writeInt(s.campZ);
            b.writeVarInt(s.stalk);
            b.writeVarInt(s.academy);
            b.writeVarInt(s.tagTokens);
         },
         b -> new State(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readInt(), b.readInt(), b.readVarInt(), b.readVarInt(),
            b.readInt(), b.readInt(), b.readVarInt(), b.readVarInt(), b.readVarInt())
      );

      /** [1.2.9] Ranger Academy courses passed (of {@link com.formaworks.frontierhunts.academy.Course#curriculum()}) */
      public int academyDone() {
         return Integer.bitCount(this.academy);
      }

      public boolean has(int flag) {
         return (this.flags & flag) != 0;
      }

      public FirstHunt.Step current() {
         return FirstHunt.current(this.done);
      }

      @Override
      public Type<State> type() {
         return TYPE;
      }
   }

   public record Action(byte action) implements CustomPacketPayload {
      public static final Type<Action> TYPE = new Type<>(FrontierHunts.id("first_hunt_action"));
      public static final StreamCodec<FriendlyByteBuf, Action> CODEC = StreamCodec.of((b, a) -> b.writeByte(a.action), b -> new Action(b.readByte()));

      @Override
      public Type<Action> type() {
         return TYPE;
      }
   }
}
