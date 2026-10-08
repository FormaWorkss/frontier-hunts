package com.formaworks.frontierhunts.phone;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * [phone] The Field Phone's two packets. {@link Ask}: the phone asks the server something or asks it to do something
 * (an op code and a few small, bounded arguments; {@link PhoneServer} checks every one). {@link Push}: the server's
 * answer or news for the phone, as a small NBT record per kind ({@code K_*}), read on the client by
 * {@code phone.client.PhoneFeed}.
 */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class PhoneNet {
   // ------------------------------------------------------------------------------------------------ ops (phone -> server)
   /** a = what (PhoneActions.R_*) */
   public static final int OP_REFRESH = 0;
   /** a = PhoneActions.C_* op, b = index, s = id / serial */
   public static final int OP_CONTRACT = 1;
   /** s = name, a = icon */
   public static final int OP_PIN_ADD = 2;
   /** s = place id */
   public static final int OP_PIN_REMOVE = 3;
   /** a = game, s = player name */
   public static final int OP_INVITE = 4;
   /** a = invite id, b = 1 accept / 0 decline */
   public static final int OP_ANSWER = 5;
   /** a = session, v = move */
   public static final int OP_MOVE = 6;
   /** a = session */
   public static final int OP_LEAVE = 7;
   public static final int OP_FLUSH_START = 8;
   /** a = token (solo) or session (online), v = shot log */
   public static final int OP_FLUSH_SUBMIT = 9;
   /** a = session, b = score, v = {tick} */
   public static final int OP_FLUSH_PROGRESS = 10;
   /** a = game, b = mode, v = {result} */
   public static final int OP_STAT = 11;
   /** s = recipient name, a newline, then the text */
   public static final int OP_MESSAGE = 12;
   /** s = the other hunter's name: their texts are read */
   public static final int OP_MESSAGE_READ = 13;
   /** a = flags: 1 screen on, 2 flashlight on */
   public static final int OP_STATE = 14;
   /** a = camera op (PhoneCams.CAM_*), b = camera pos (BlockPos.asLong), c = photo id, v = photo ids the phone has */
   public static final int OP_CAMERA = 15;
   /** the phone was opened at a retired base station: a = its pos */
   public static final int OP_STATION = 16;
   /** a = a finished online game: ask its opponent for a rematch (or take theirs) */
   public static final int OP_REMATCH = 17;
   /** [1.4.0] a = photo id: send me this shared photo */
   public static final int OP_PHOTO_GET = 18;
   /** [1.4.0] a = selfie pose (0 none): show it to the hunters around me */
   public static final int OP_POSE = 19;
   public static final int OP_COUNT = 20;

   /** what OP_REFRESH asks for (the same numbers as the phone's PhoneActions.R_*) */
   public static final int R_WEATHER = 0, R_PLACES = 1, R_WALLET = 2, R_CONTRACTS = 3, R_CAMS = 4, R_BOARD = 5, R_GAMES = 6, R_MESSAGES = 7;

   // ------------------------------------------------------------------------------------------------ kinds (server -> phone)
   public static final int K_WEATHER = 0;
   public static final int K_PLACES = 1;
   public static final int K_WALLET = 2;
   public static final int K_CONTRACTS = 3;
   public static final int K_CAMS = 4;
   public static final int K_GAMES = 5;
   public static final int K_NOTICE = 6;
   public static final int K_MESSAGES = 7;
   public static final int K_FLUSH = 8;
   /** the server says: open the phone (a retired base station was used), tag "app" */
   public static final int K_OPEN = 9;
   public static final int K_TOAST = 10;
   /** [1.4.0] a shared photo: "id", "jpg" (bytes) or "missing" */
   public static final int K_PHOTO = 11;
   /** [1.4.0] a hunter strikes a selfie pose: "e" entity id, "p" pose (0 none) */
   public static final int K_POSE = 12;
   public static final int K_COUNT = 13;

   public static final int MAX_STRING = 400;
   public static final int MAX_INTS = 1200;
   public static final int MAX_LONGS = 128;
   public static final long MAX_TAG = 512L * 1024L;

   /** client hook (set by client code, never loaded on a dedicated server) */
   public static Consumer<Push> receiver = p -> {
   };

   private PhoneNet() {
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar r = event.registrar("1");
      r.playToServer(Ask.TYPE, Ask.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp) {
            PhoneServer.handle(sp, p);
         }
      }));
      r.playToClient(Push.TYPE, Push.CODEC, (p, ctx) -> ctx.enqueueWork(() -> receiver.accept(p)));
      r.playToServer(PhotoUp.TYPE, PhotoUp.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp) {
            PhonePhotos.upload(sp, p);
         }
      }));
   }

   /** Sends a record to one player's phone (if their client has the channel). */
   public static void send(ServerPlayer player, int kind, CompoundTag tag) {
      if (player != null && player.connection != null && player.connection.hasChannel(Push.TYPE)) {
         PacketDistributor.sendToPlayer(player, new Push(kind, tag));
      }
   }

   // ------------------------------------------------------------------------------------------------ payloads

   public record Ask(int op, long a, long b, long c, String s, int[] v, long[] ids) implements CustomPacketPayload {
      public static final Type<Ask> TYPE = new Type<>(FrontierHunts.id("phone_ask"));
      public static final StreamCodec<FriendlyByteBuf, Ask> CODEC = StreamCodec.of((buf, p) -> {
         buf.writeVarInt(p.op);
         buf.writeVarLong(p.a);
         buf.writeVarLong(p.b);
         buf.writeVarLong(p.c);
         buf.writeUtf(p.s, MAX_STRING);
         int n = Math.min(MAX_INTS, p.v.length);
         buf.writeVarInt(n);
         for (int i = 0; i < n; i++) {
            buf.writeVarInt(p.v[i]);
         }
         int m = Math.min(MAX_LONGS, p.ids.length);
         buf.writeVarInt(m);
         for (int i = 0; i < m; i++) {
            buf.writeLong(p.ids[i]);
         }
      }, buf -> {
         int op = buf.readVarInt();
         long a = buf.readVarLong();
         long b = buf.readVarLong();
         long c = buf.readVarLong();
         String s = buf.readUtf(MAX_STRING);
         int n = buf.readVarInt();
         if (n < 0 || n > MAX_INTS) {
            throw new IllegalArgumentException("phone: too many values");
         }
         int[] v = new int[n];
         for (int i = 0; i < n; i++) {
            v[i] = buf.readVarInt();
         }
         int m = buf.readVarInt();
         if (m < 0 || m > MAX_LONGS) {
            throw new IllegalArgumentException("phone: too many ids");
         }
         long[] ids = new long[m];
         for (int i = 0; i < m; i++) {
            ids[i] = buf.readLong();
         }
         return new Ask(op, a, b, c, s, v, ids);
      });

      public Ask {
         s = s == null ? "" : s;
         v = v == null ? new int[0] : v;
         ids = ids == null ? new long[0] : ids;
      }

      public static Ask of(int op, long a, long b) {
         return new Ask(op, a, b, 0L, "", null, null);
      }

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /**
    * [1.4.0] One part of a photo the phone shares in a text (a small JPEG, at most {@link PhonePhotos#MAX_PARTS} parts of
    * {@link PhonePhotos#PART} bytes). The text that shows it follows once every part is sent.
    */
   public record PhotoUp(long id, int part, int parts, byte[] data) implements CustomPacketPayload {
      public static final Type<PhotoUp> TYPE = new Type<>(FrontierHunts.id("phone_photo"));
      public static final StreamCodec<FriendlyByteBuf, PhotoUp> CODEC = StreamCodec.of((buf, p) -> {
         buf.writeLong(p.id);
         buf.writeVarInt(p.part);
         buf.writeVarInt(p.parts);
         buf.writeByteArray(p.data);
      }, buf -> new PhotoUp(buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(PhonePhotos.PART)));

      public PhotoUp {
         data = data == null ? new byte[0] : data;
      }

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record Push(int kind, CompoundTag tag) implements CustomPacketPayload {
      public static final Type<Push> TYPE = new Type<>(FrontierHunts.id("phone_push"));
      public static final StreamCodec<FriendlyByteBuf, Push> CODEC = StreamCodec.of((buf, p) -> {
         buf.writeVarInt(p.kind);
         buf.writeNbt(p.tag);
      }, buf -> {
         int kind = buf.readVarInt();
         Tag t = buf.readNbt(NbtAccounter.create(MAX_TAG));
         return new Push(kind, t instanceof CompoundTag c ? c : new CompoundTag());
      });

      public Push {
         tag = tag == null ? new CompoundTag() : tag;
      }

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
