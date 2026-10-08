package com.formaworks.frontierhunts.hunting.rutfight;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.hunting.routine.RutEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * [rutfight] A watching client tells the server the locked distance its graphics preset needs for a fight (box-model
 * racks are bigger than the sculpted ones, so Vanilla needs the bulls further apart). The server uses the
 * nearest watcher's number so the player closest to the fight sees exact contact; everyone else's renderer takes up
 * the difference. Validated: the player must be near, the two animals must really be fighting each other, the
 * distance must be finite and within a sane band of the server's own mesh fit, and reports are rate limited.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class RutFightNet {
   private RutFightNet() {
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar r = event.registrar("1").optional();
      r.playToServer(Report.TYPE, Report.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp) {
            receive(sp, p);
         }
      }));
   }

   static void receive(ServerPlayer player, Report p) {
      if (!Float.isFinite(p.distance()) || p.distance() < 0.5F || p.distance() > 16.0F) {
         return;
      }

      Entity a = player.level().getEntity(p.a());
      Entity b = player.level().getEntity(p.b());
      if (a instanceof Whitetail wa && b instanceof Whitetail wb && wa != wb && player.distanceToSqr(wa) < 96.0 * 96.0) {
         RutEngine.report(wa, wb, player, p.distance());
      }
   }

   public record Report(int a, int b, float distance) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<Report> TYPE = new CustomPacketPayload.Type<>(FrontierHunts.id("rut_fight_fit"));
      public static final StreamCodec<ByteBuf, Report> CODEC = StreamCodec.composite(
         ByteBufCodecs.VAR_INT, Report::a, ByteBufCodecs.VAR_INT, Report::b, ByteBufCodecs.FLOAT, Report::distance, Report::new
      );

      @Override
      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
