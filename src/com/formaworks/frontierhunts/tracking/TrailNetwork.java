package com.formaworks.frontierhunts.tracking;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.tracking.hound.HoundCommands;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class TrailNetwork {
   public static final int MAX_UPSERTS = 256;
   public static final int MAX_REMOVALS = 1024;
   /** legacy full-snapshot receiver (kept: FrontierClient links against it); snapshots are no longer sent */
   public static Consumer<TrailNetwork.Snapshot> receiver = var0 -> {
   };
   public static Consumer<TrailNetwork.Confirmed> confirmation = var0 -> {
   };
   /** [tracking] incremental trail sync, set by the client */
   public static Consumer<TrailNetwork.Delta> delta = var0 -> {
   };

   public static void register(RegisterPayloadHandlersEvent var0) {
      // [tracking] "5": marks carry kind / stride / scale and sync incrementally
      PayloadRegistrar var1 = var0.registrar("5");
      var1.playToClient(TrailNetwork.Delta.TYPE, TrailNetwork.Delta.CODEC, (p, c) -> c.enqueueWork(() -> delta.accept(p)));
      var1.playToClient(TrailNetwork.Confirmed.TYPE, TrailNetwork.Confirmed.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> confirmation.accept(var0x)));
      var1.playToServer(TrailNetwork.Inspect.TYPE, TrailNetwork.Inspect.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
            if (var1x.player() instanceof ServerPlayer var2) {
               TrailService.inspect(var2, var0x.id());
            }
         }));
      var1.playToServer(TrailNetwork.Track.TYPE, TrailNetwork.Track.CODEC, (p, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) {
               HoundCommands.track(sp, p.id());
            }
         }));
   }

   private TrailNetwork() {
   }

   static void write(FriendlyByteBuf var0, TrailMark var3) {
      var0.writeUUID(var3.id());
      var0.writeUUID(var3.animal());
      var0.writeDouble(var3.position().x);
      var0.writeDouble(var3.position().y);
      var0.writeDouble(var3.position().z);
      var0.writeBlockPos(var3.support());
      var0.writeFloat(var3.yaw());
      var0.writeVarLong(var3.created());
      var0.writeVarInt(var3.rainWear());
      var0.writeBoolean(var3.blood());
      var0.writeUtf(var3.individual(), 80);
      var0.writeByte(var3.activity());
      var0.writeByte(var3.face().ordinal());
      var0.writeByte(var3.style());
      var0.writeFloat(var3.radius());
      var0.writeByte(var3.sign());
      var0.writeFloat(var3.stride());
      var0.writeFloat(var3.scale());
   }

   static TrailMark read(FriendlyByteBuf var0) {
      return new TrailMark(
         var0.readUUID(),
         var0.readUUID(),
         new Vec3(var0.readDouble(), var0.readDouble(), var0.readDouble()),
         var0.readBlockPos(),
         var0.readFloat(),
         var0.readVarLong(),
         var0.readVarInt(),
         var0.readBoolean(),
         var0.readUtf(80),
         var0.readUnsignedByte(),
         TrailMark.readFace(var0.readUnsignedByte()),
         var0.readUnsignedByte(),
         var0.readFloat(),
         var0.readUnsignedByte(),
         var0.readFloat(),
         var0.readFloat()
      );
   }

   public static record Confirmed(UUID id) implements CustomPacketPayload {
      public static final Type<TrailNetwork.Confirmed> TYPE = new Type<>(FrontierHunts.id("trail_confirmed"));
      public static final StreamCodec<FriendlyByteBuf, TrailNetwork.Confirmed> CODEC = StreamCodec.of(
         (var0, var1) -> var0.writeUUID(var1.id), var0 -> new TrailNetwork.Confirmed(var0.readUUID())
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Inspect(UUID id) implements CustomPacketPayload {
      public static final Type<TrailNetwork.Inspect> TYPE = new Type<>(FrontierHunts.id("inspect_trail"));
      public static final StreamCodec<FriendlyByteBuf, TrailNetwork.Inspect> CODEC = StreamCodec.of(
         (var0, var1) -> var0.writeUUID(var1.id), var0 -> new TrailNetwork.Inspect(var0.readUUID())
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** [tracking] owner points their hound at the mark they are aiming at */
   public static record Track(UUID id) implements CustomPacketPayload {
      public static final Type<TrailNetwork.Track> TYPE = new Type<>(FrontierHunts.id("hound_track"));
      public static final StreamCodec<FriendlyByteBuf, TrailNetwork.Track> CODEC = StreamCodec.of(
         (var0, var1) -> var0.writeUUID(var1.id), var0 -> new TrailNetwork.Track(var0.readUUID())
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** [tracking] changes to the sign a player can see: new / re-weathered marks and removed ids */
   public static record Delta(String dimension, boolean reset, List<TrailMark> marks, List<UUID> removed) implements CustomPacketPayload {
      public static final Type<TrailNetwork.Delta> TYPE = new Type<>(FrontierHunts.id("trail_delta"));
      public static final StreamCodec<FriendlyByteBuf, TrailNetwork.Delta> CODEC = StreamCodec.of((b, d) -> {
         b.writeUtf(d.dimension, 256);
         b.writeBoolean(d.reset);
         b.writeVarInt(d.marks.size());
         for (TrailMark m : d.marks) {
            write(b, m);
         }
         b.writeVarInt(d.removed.size());
         for (UUID id : d.removed) {
            b.writeUUID(id);
         }
      }, b -> {
         String dim = b.readUtf(256);
         boolean reset = b.readBoolean();
         int n = b.readVarInt();
         if (n < 0 || n > MAX_UPSERTS) {
            throw new IllegalArgumentException("Invalid trail count");
         }
         List<TrailMark> marks = new ArrayList<>(n);
         HashSet<UUID> seen = new HashSet<>();
         for (int i = 0; i < n; i++) {
            TrailMark m = read(b);
            if (!seen.add(m.id())) {
               throw new IllegalArgumentException("Duplicate visible trail");
            }
            marks.add(m);
         }
         int r = b.readVarInt();
         if (r < 0 || r > MAX_REMOVALS) {
            throw new IllegalArgumentException("Invalid trail removal count");
         }
         List<UUID> removed = new ArrayList<>(r);
         for (int i = 0; i < r; i++) {
            removed.add(b.readUUID());
         }
         return new TrailNetwork.Delta(dim, reset, marks, removed);
      });

      public Delta {
         marks = List.copyOf(marks);
         removed = List.copyOf(removed);
         if (marks.size() > MAX_UPSERTS || removed.size() > MAX_REMOVALS) {
            throw new IllegalArgumentException("Too many trail changes");
         }
      }

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** Legacy full view (no longer sent; kept so older client code still links). */
   public static record Snapshot(String dimension, List<TrailMark> marks) implements CustomPacketPayload {
      public static final Type<TrailNetwork.Snapshot> TYPE = new Type<>(FrontierHunts.id("nearby_trails"));
      public static final StreamCodec<FriendlyByteBuf, TrailNetwork.Snapshot> CODEC = StreamCodec.of((b, s) -> {
         b.writeUtf(s.dimension, 256);
         b.writeVarInt(s.marks.size());
         for (TrailMark m : s.marks) {
            write(b, m);
         }
      }, b -> {
         String dim = b.readUtf(256);
         int n = b.readVarInt();
         if (n < 0 || n > MAX_UPSERTS) {
            throw new IllegalArgumentException("Invalid trail count");
         }
         List<TrailMark> marks = new ArrayList<>(n);
         for (int i = 0; i < n; i++) {
            marks.add(read(b));
         }
         return new TrailNetwork.Snapshot(dim, marks);
      });

      public Snapshot {
         marks = List.copyOf(marks);
      }

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
