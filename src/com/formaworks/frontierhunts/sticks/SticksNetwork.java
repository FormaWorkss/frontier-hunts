package com.formaworks.frontierhunts.sticks;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * [sticks] Client to server. A gun's use key aims it, so a hunter holding one never sends vanilla's "use entity" at the
 * sticks: the client asks instead - {@link Use} (rest the gun in the yoke, or fold them when sneaking) - and
 * {@link Release} (jump / step away while rested). The server checks everything again (the entity, reach, the gun,
 * the rider) and rate-limits.
 */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class SticksNetwork {
   private static final Map<UUID, Long> LAST = new HashMap<>();

   private SticksNetwork() {
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent event) {
      var r = event.registrar("1").optional();
      r.playToServer(Use.TYPE, Use.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp) {
            use(sp, p.entity(), p.fold());
         }
      }));
      r.playToServer(Release.TYPE, Release.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp && sp.getVehicle() instanceof ShootingSticksEntity s) {
            s.release(sp);
         }
      }));
   }

   static void use(ServerPlayer sp, int id, boolean fold) {
      long now = sp.level().getGameTime();
      Long last = LAST.get(sp.getUUID());
      if (last != null && now >= last && now - last < 4L) {
         return;
      }
      LAST.put(sp.getUUID(), now);
      Entity e = sp.level().getEntity(id);
      if (!(e instanceof ShootingSticksEntity s) || s.isRemoved() || !sp.canInteractWithEntity(s, 1.0) || sp.isSpectator()) {
         return;
      }
      if (fold) {
         if (sp.isSecondaryUseActive()) {
            s.fold(sp);
         }
      } else {
         s.tryRest(sp);
      }
   }

   /** Forget rate-limit entries of players who left. */
   @EventBusSubscriber(modid = FrontierHunts.ID)
   public static final class Cleanup {
      private Cleanup() {
      }

      @SubscribeEvent
      public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
         LAST.remove(e.getEntity().getUUID());
         // let go of the sticks before vanilla saves the player with them as a "vehicle" and takes them out of the world
         if (e.getEntity() instanceof ServerPlayer sp && sp.getVehicle() instanceof ShootingSticksEntity s) {
            s.release(sp);
         }
      }
   }

   public record Use(int entity, boolean fold) implements CustomPacketPayload {
      public static final Type<Use> TYPE = new Type<>(FrontierHunts.id("sticks_use"));
      public static final StreamCodec<FriendlyByteBuf, Use> CODEC = StreamCodec.of((buf, p) -> {
         buf.writeVarInt(p.entity);
         buf.writeBoolean(p.fold);
      }, buf -> new Use(buf.readVarInt(), buf.readBoolean()));

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record Release() implements CustomPacketPayload {
      public static final Type<Release> TYPE = new Type<>(FrontierHunts.id("sticks_release"));
      public static final StreamCodec<FriendlyByteBuf, Release> CODEC = StreamCodec.of((buf, p) -> {
      }, buf -> new Release());

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
