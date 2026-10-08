package com.formaworks.frontierhunts.wingshot;

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

/**
 * [wingshot] Server -> client events for the bird hit effect: one {@link Hit} per bird per tick (all the pellets of a
 * shot folded together) to everyone tracking the bird, and {@link WingShot} to the shooter of a clean wing shot (the
 * slow-motion moment). The client side registers its handlers in {@link #clientHit}/{@link #clientWingShot}, so this
 * class never loads client code.
 */
public final class BirdNet {
   public static final int KILLED = 1, FLYING = 2, WATER = 4;
   /** what hit the bird: a shotgun's pellets, an arrow/bolt, a bullet (rifle, pistol, other mods' guns), anything else */
   public static final byte SHOT_PELLETS = 0, SHOT_ARROW = 1, SHOT_BULLET = 2, SHOT_OTHER = 3;
   public static volatile Consumer<Hit> clientHit;
   public static volatile Consumer<WingShot> clientWingShot;

   private BirdNet() {
   }

   /**
    * A bird was hit: where, from which direction, how hard (damage / max health, summed over the pellets of a shot), how
    * many projectiles struck it this tick, with what ({@link #SHOT_PELLETS}...), what it was doing.
    */
   public record Hit(int entity, byte species, float x, float y, float z, float dx, float dy, float dz, float energy, byte flags, byte count,
      byte shot, float windE, float windS) implements CustomPacketPayload {
      public static final Type<Hit> TYPE = new Type<>(FrontierHunts.id("wingshot_hit"));
      public static final StreamCodec<FriendlyByteBuf, Hit> CODEC = StreamCodec.of((b, h) -> {
         b.writeVarInt(h.entity);
         b.writeByte(h.species);
         b.writeFloat(h.x);
         b.writeFloat(h.y);
         b.writeFloat(h.z);
         b.writeFloat(h.dx);
         b.writeFloat(h.dy);
         b.writeFloat(h.dz);
         b.writeFloat(h.energy);
         b.writeByte(h.flags);
         b.writeByte(h.count);
         b.writeByte(h.shot);
         b.writeFloat(h.windE);
         b.writeFloat(h.windS);
      }, b -> new Hit(b.readVarInt(), b.readByte(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(),
         b.readByte(), b.readByte(), b.readByte(), b.readFloat(), b.readFloat()));

      public boolean killed() {
         return (this.flags & KILLED) != 0;
      }

      public boolean flying() {
         return (this.flags & FLYING) != 0;
      }

      public boolean water() {
         return (this.flags & WATER) != 0;
      }

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** To the shooter: your shot dropped this bird cleanly on the wing. */
   public record WingShot(int entity, float distance) implements CustomPacketPayload {
      public static final Type<WingShot> TYPE = new Type<>(FrontierHunts.id("wingshot_moment"));
      public static final StreamCodec<FriendlyByteBuf, WingShot> CODEC = StreamCodec.of((b, w) -> {
         b.writeVarInt(w.entity);
         b.writeFloat(w.distance);
      }, b -> new WingShot(b.readVarInt(), b.readFloat()));

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   static void send(ServerPlayer p, CustomPacketPayload payload) {
      if (p.connection != null && p.connection.hasChannel(payload.type())) {
         PacketDistributor.sendToPlayer(p, payload);
      }
   }

   @EventBusSubscriber(modid = "frontierhunts", bus = EventBusSubscriber.Bus.MOD)
   public static final class ModEvents {
      private ModEvents() {
      }

      @SubscribeEvent
      public static void payloads(RegisterPayloadHandlersEvent event) {
         var r = event.registrar("1").optional();
         r.playToClient(Hit.TYPE, Hit.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            Consumer<Hit> c = clientHit;
            if (c != null) {
               c.accept(p);
            }
         }));
         r.playToClient(WingShot.TYPE, WingShot.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            Consumer<WingShot> c = clientWingShot;
            if (c != null) {
               c.accept(p);
            }
         }));
      }
   }
}
