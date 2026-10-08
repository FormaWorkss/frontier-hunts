package com.formaworks.frontierhunts.academy;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.ArrayList;
import java.util.List;
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
 * [academy] Ranger Academy payloads. The server owns every decision; the client asks (begin a course, leave) and shows.
 * All reads are bounded and clamped; the request handler validates and rate-limits on the server.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class AcademyNetwork {
   public static Consumer<State> stateReceiver = s -> {
   };
   public static Consumer<Hud> hudReceiver = h -> {
   };
   public static Consumer<Cue> cueReceiver = c -> {
   };

   // Action (client -> server)
   public static final byte A_SYNC = 0;
   public static final byte A_BEGIN = 1;
   public static final byte A_LEAVE = 2;

   // Cue kinds (server -> client)
   public static final byte C_DEPART = 0;
   public static final byte C_ARRIVE = 1;
   public static final byte C_PASSED = 2;
   public static final byte C_FAILED = 3;
   public static final byte C_HOME = 4;
   public static final byte C_TICK = 5;
   public static final byte C_BUSTED = 6;
   public static final byte C_NOTE = 7;
   public static final byte C_REFUSED = 8;
   public static final byte C_RING = 9;

   // Phases in the HUD
   public static final byte P_NONE = 0;
   public static final byte P_ACTIVE = 1;
   public static final byte P_PASSED = 2;
   public static final byte P_FAILED = 3;
   public static final byte P_LEAVING = 4;

   private AcademyNetwork() {
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar r = event.registrar("1").optional();
      r.playToClient(State.TYPE, State.CODEC, (p, ctx) -> ctx.enqueueWork(() -> stateReceiver.accept(p)));
      r.playToClient(Hud.TYPE, Hud.CODEC, (p, ctx) -> ctx.enqueueWork(() -> hudReceiver.accept(p)));
      r.playToClient(Cue.TYPE, Cue.CODEC, (p, ctx) -> ctx.enqueueWork(() -> cueReceiver.accept(p)));
      r.playToServer(Action.TYPE, Action.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp) {
            TrainingService.action(sp, p.action(), p.course());
         }
      }));
   }

   static void send(ServerPlayer player, CustomPacketPayload payload) {
      if (player.connection != null && player.connection.hasChannel(payload.type())) {
         PacketDistributor.sendToPlayer(player, payload);
      }
   }

   private static String str(FriendlyByteBuf b, int max) {
      return b.readUtf(max);
   }

   // ------------------------------------------------------------------------------------------------ action

   /** Client -> server: sync / begin course / leave training. */
   public record Action(byte action, byte course) implements CustomPacketPayload {
      public static final Type<Action> TYPE = new Type<>(FrontierHunts.id("academy_action"));
      public static final StreamCodec<FriendlyByteBuf, Action> CODEC = StreamCodec.of(
         (b, a) -> {
            b.writeByte(a.action);
            b.writeByte(a.course);
         },
         b -> new Action(b.readByte(), b.readByte())
      );

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   // ------------------------------------------------------------------------------------------------ state

   /** One course's record for this hunter. */
   public record CourseInfo(int passes, int bestTicks, int cooldown, boolean certified) {
   }

   /**
    * Server -> client: the academy record (for the assignments dossier). {@code active} is the running course or -1;
    * {@code available} false when the grounds are off or missing on this server.
    */
   public record State(boolean available, int active, List<CourseInfo> courses) implements CustomPacketPayload {
      public static final Type<State> TYPE = new Type<>(FrontierHunts.id("academy_state"));
      public static final StreamCodec<FriendlyByteBuf, State> CODEC = StreamCodec.of(
         (b, s) -> {
            b.writeBoolean(s.available);
            b.writeVarInt(s.active + 1);
            b.writeVarInt(s.courses.size());
            for (CourseInfo c : s.courses) {
               b.writeVarInt(c.passes);
               b.writeVarInt(c.bestTicks);
               b.writeVarInt(c.cooldown);
               b.writeBoolean(c.certified);
            }
         },
         b -> {
            boolean av = b.readBoolean();
            int active = Math.clamp(b.readVarInt() - 1, -1, 31);
            int n = b.readVarInt();
            if (n < 0 || n > 16) {
               throw new IllegalArgumentException("Too many academy courses");
            }
            List<CourseInfo> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
               list.add(new CourseInfo(Math.max(0, b.readVarInt()), Math.max(0, b.readVarInt()), Math.max(0, b.readVarInt()), b.readBoolean()));
            }
            return new State(av, active, list);
         }
      );

      public CourseInfo info(int course) {
         return course >= 0 && course < this.courses.size() ? this.courses.get(course) : new CourseInfo(0, 0, 0, false);
      }

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   // ------------------------------------------------------------------------------------------------ HUD

   /**
    * Server -> client: the live training card. {@code remaining} in ticks (the client counts down between updates),
    * wind in m/s components (east, south), {@code flags}: 1 = show wind, 2 = off the firing line.
    */
   public record Hud(int course, byte phase, int remaining, int[] progress, int attempts, int flags, float windEast, float windSouth)
      implements CustomPacketPayload {
      public static final Type<Hud> TYPE = new Type<>(FrontierHunts.id("academy_hud"));
      public static final StreamCodec<FriendlyByteBuf, Hud> CODEC = StreamCodec.of(
         (b, h) -> {
            b.writeVarInt(h.course + 1);
            b.writeByte(h.phase);
            b.writeVarInt(Math.max(0, h.remaining));
            b.writeVarInt(h.progress.length);
            for (int v : h.progress) {
               b.writeVarInt(Math.max(0, v));
            }
            b.writeVarInt(Math.max(0, h.attempts));
            b.writeVarInt(h.flags);
            b.writeFloat(h.windEast);
            b.writeFloat(h.windSouth);
         },
         b -> {
            int course = Math.clamp(b.readVarInt() - 1, -1, 31);
            byte phase = b.readByte();
            int remaining = Math.clamp(b.readVarInt(), 0, 72000 * 4);
            int n = b.readVarInt();
            if (n < 0 || n > 8) {
               throw new IllegalArgumentException("Too many academy objectives");
            }
            int[] p = new int[n];
            for (int i = 0; i < n; i++) {
               p[i] = Math.clamp(b.readVarInt(), 0, 1000);
            }
            int attempts = Math.clamp(b.readVarInt(), 0, 9999);
            int flags = b.readVarInt();
            float we = b.readFloat(), ws = b.readFloat();
            if (!Float.isFinite(we) || !Float.isFinite(ws)) {
               we = 0.0F;
               ws = 0.0F;
            }
            return new Hud(course, phase, remaining, p, attempts, flags, Math.clamp(we, -40.0F, 40.0F), Math.clamp(ws, -40.0F, 40.0F));
         }
      );

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   // ------------------------------------------------------------------------------------------------ cues

   /**
    * Server -> client: a presentation beat. {@code key} is a lang key (or ""), {@code args} its arguments (already
    * plain strings), {@code value} a kind-specific number (ticks, distance, objective index).
    */
   public record Cue(byte kind, int course, int value, String key, List<String> args) implements CustomPacketPayload {
      public static final Type<Cue> TYPE = new Type<>(FrontierHunts.id("academy_cue"));
      public static final StreamCodec<FriendlyByteBuf, Cue> CODEC = StreamCodec.of(
         (b, c) -> {
            b.writeByte(c.kind);
            b.writeVarInt(c.course + 1);
            b.writeVarInt(c.value);
            b.writeUtf(c.key, 128);
            b.writeVarInt(Math.min(6, c.args.size()));
            for (int i = 0; i < Math.min(6, c.args.size()); i++) {
               b.writeUtf(c.args.get(i), 96);
            }
         },
         b -> {
            byte kind = b.readByte();
            int course = Math.clamp(b.readVarInt() - 1, -1, 31);
            int value = b.readVarInt();
            String key = str(b, 128);
            int n = b.readVarInt();
            if (n < 0 || n > 6) {
               throw new IllegalArgumentException("Too many cue arguments");
            }
            List<String> args = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
               args.add(str(b, 96));
            }
            return new Cue(kind, course, value, key, args);
         }
      );

      public static Cue of(byte kind, int course, int value, String key, String... args) {
         return new Cue(kind, course, value, key == null ? "" : key, List.of(args));
      }

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
