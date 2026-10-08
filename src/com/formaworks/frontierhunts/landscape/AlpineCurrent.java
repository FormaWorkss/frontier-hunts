package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The pull of the alpine rivers (waterfalls, rapids, runs) on whatever is in them.
 *
 * <p>[1.1.4] Made cheap and smooth:
 * <ul>
 * <li>The river's pull at a spot comes from the terrain layout, which is expensive to sample; it is now worked out once
 *     every few ticks per entity (or when it has moved a block) and reused in between, instead of every tick for every
 *     swimming mob, fish and dropped item - that was dragging the server down around rivers and waterfalls.</li>
 * <li>Players are no longer pushed by the server. Forcing a player's motion from the server sends a correction every
 *     tick, which fights the player's own movement and feels like lag and rubber-banding in the water. The server now
 *     sends the pull to that player a few times a second and the player's own client applies it every tick, smoothly.</li>
 * </ul>
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class AlpineCurrent {
   private AlpineCurrent() {
   }

   /** the river's pull at a spot: target velocity, grip base, fall target and rate, waterfall impulse */
   public record Pull(double tx, double tz, double grip, boolean fall, double fallVy, double ix, double iy, double iz) {
      public static final Pull NONE = new Pull(0, 0, 0, false, 0, 0, 0, 0);

      public boolean none() {
         return this.grip <= 0.0 && this.ix == 0.0 && this.iy == 0.0 && this.iz == 0.0;
      }
   }

   private record Cached(Pull pull, long tick, double x, double z) {
   }

   private static final Map<Integer, Cached> CACHE = new HashMap<>();
   private static final Map<Integer, Boolean> SENT = new HashMap<>();
   private static final int REFRESH = 4;

   @SubscribeEvent
   public static void tick(Post var0) {
      Entity e = var0.getEntity();
      if (!(e.level() instanceof ServerLevel level) || !(level.getChunkSource().getGenerator() instanceof AlpineGenerator gen)) {
         return;
      }
      boolean wet = e.isInWater() && !e.noPhysics && !e.isSpectator() && !(e instanceof Player p && p.getAbilities().flying);
      long now = level.getGameTime();
      if (!wet) {
         if (e instanceof ServerPlayer sp && SENT.remove(e.getId()) != null) {
            PacketDistributor.sendToPlayer(sp, new Sync(Pull.NONE));
         }
         return;
      }
      Cached c = CACHE.get(e.getId());
      if (c == null || now - c.tick >= REFRESH || Math.abs(e.getX() - c.x) + Math.abs(e.getZ() - c.z) > 0.9) {
         c = new Cached(pull(level, gen.layout(), e), now, e.getX(), e.getZ());
         CACHE.put(e.getId(), c);
         if (e instanceof ServerPlayer sp) {
            // the player's own client applies it (see Client); a quiet spot is sent once, so the client lets go
            if (!c.pull.none() || SENT.containsKey(e.getId())) {
               PacketDistributor.sendToPlayer(sp, new Sync(c.pull));
            }
            if (c.pull.none()) {
               SENT.remove(e.getId());
            } else {
               SENT.put(e.getId(), Boolean.TRUE);
            }
         }
      }
      if (!(e instanceof Player) && !c.pull.none()) {
         apply(e, c.pull);
      }
      if ((now & 255) == 0) {
         // forget entities that are long gone
         for (Iterator<Map.Entry<Integer, Cached>> it = CACHE.entrySet().iterator(); it.hasNext(); ) {
            if (now - it.next().getValue().tick > 200) {
               it.remove();
            }
         }
      }
   }

   /** the pull of the water at the entity (the 1.1.1 river physics, unchanged) */
   static Pull pull(ServerLevel level, AlpineLayout layout, Entity e) {
      if (layout.version() < 4 || layout.version() >= 12) {
         return Pull.NONE;
      }
      if (layout.version() >= 7) {
         AlpineHydraulics.Reach reach = AlpineHydraulics.at(layout, e.getX(), e.getZ(), e.getY());
         if (reach.dx() == 0.0 && reach.dz() == 0.0) {
            return Pull.NONE;
         }
         AlpineLayout.Sample here = layout.sample(Math.floor(e.getX()), Math.floor(e.getZ()));
         if (AlpineLayout.sea(here.biome()) || e.getY() < here.floor() - 0.5) {
            return Pull.NONE;
         }
         Vec3 v = AlpineCurrentField.velocity(level, reach, e.getX(), e.getY(), e.getZ());
         // [1.2.5] no rapids: a river carries you along gently (it used to take hold at 1.7x, and 2.5x in fast water),
         // and you can always swim out of it
         double grip = 0.12 + 0.18 * Math.min(1.0, reach.energy());
         v = v.scale(0.8);
         double vs = Math.hypot(v.x, v.z);
         if (vs > 0.12) {
            v = v.scale(0.12 / vs);
         }
         BlockPos below = BlockPos.containing(e.getX() + reach.dx(), e.getY() - 1.1, e.getZ() + reach.dz());
         boolean open = level.hasChunkAt(below) && level.getBlockState(below).getCollisionShape(level, below).isEmpty();
         AlpineLayout.Sample ahead = layout.sample(e.getX() + reach.dx() * 1.2, e.getZ() + reach.dz() * 1.2);
         boolean fall = reach.falling() && open && e.getY() > ahead.water() + 0.5;
         double fallVy = -Math.min(1.35, Math.sqrt(0.16 * Math.max(0.0, reach.drop())));
         Vec3 imp = AlpineImpactCurrent.impulse(level, e.getX(), e.getY(), e.getZ());
         return new Pull(v.x, v.z, grip, fall, fallVy, imp.x, imp.y, imp.z);
      }
      // the older layouts: a gentle drift (applied every 4th tick before, so a quarter of it each tick now)
      AlpineLayout.Sample s = layout.sample(e.getX(), e.getZ());
      if (!s.wet() || AlpineLayout.sea(s.biome()) || Math.abs(e.getY() - s.water()) > 8.0) {
         return Pull.NONE;
      }
      AlpineWatershed.Water w = layout.watershed().water(e.getX(), e.getZ());
      if (w.lake() || s.distance() > s.width() + 2.0) {
         return Pull.NONE;
      }
      double steep = Math.clamp(s.fall() / 10.0, 0.0, 1.0);
      double speed = 0.06 + Math.min(0.7, w.speed() * 9.0 + s.fall() * 0.032);
      return new Pull(w.dx() * speed, w.dz() * speed, -1.0, false, steep > 0.0 ? (-0.018 - 0.075 * steep) / 4.0 : 0.0, 0, 0, 0);
   }

   /** one tick of the pull on an entity (whichever side moves it) */
   public static void apply(Entity e, Pull p) {
      Vec3 m = e.getDeltaMovement();
      if (p.grip < 0.0) {
         // older layouts: push along the flow toward its speed
         double sp = Math.hypot(p.tx, p.tz);
         if (sp < 1.0E-6) {
            return;
         }
         double dx = p.tx / sp, dz = p.tz / sp;
         double along = m.x * dx + m.z * dz;
         double add = Math.clamp((sp - along) * 0.24, 0.0, 0.095) / 4.0;
         e.setDeltaMovement(m.add(dx * add, p.fallVy, dz * add));
         return;
      }
      double sub = Math.clamp(e.getFluidHeight(FluidTags.WATER) / Math.max(0.3, (double)e.getBbHeight()), 0.15, 1.0);
      double grip = Math.min(0.9, p.grip * sub);
      double x = m.x + Math.clamp((p.tx - m.x) * grip, -0.34, 0.34);
      double z = m.z + Math.clamp((p.tz - m.z) * grip, -0.34, 0.34);
      double y = m.y;
      if (p.fall) {
         y += (p.fallVy - y) * (0.12 + 0.16 * sub);
      }
      e.setDeltaMovement(x + p.ix, Math.clamp(y + p.iy, -1.5, 0.35), z + p.iz);
   }

   // ----------------------------------------------------------------------------------------------- the player's pull

   /** server -> the player in the water: the pull to apply on their own client */
   public record Sync(Pull pull) implements CustomPacketPayload {
      public static final Type<Sync> TYPE = new Type<>(FrontierHunts.id("river_pull"));
      public static final StreamCodec<FriendlyByteBuf, Sync> CODEC = StreamCodec.of((b, s) -> {
         Pull p = s.pull;
         b.writeFloat((float)p.tx);
         b.writeFloat((float)p.tz);
         b.writeFloat((float)p.grip);
         b.writeBoolean(p.fall);
         b.writeFloat((float)p.fallVy);
         b.writeFloat((float)p.ix);
         b.writeFloat((float)p.iy);
         b.writeFloat((float)p.iz);
      }, b -> new Sync(new Pull(b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat())));

      @Override
      public Type<Sync> type() {
         return TYPE;
      }
   }

   /** the pull the server last sent this client, and when (it lapses if the server stops sending) */
   public static volatile Pull clientPull = Pull.NONE;
   public static volatile long clientPullAt;

   @EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
   public static final class Net {
      private Net() {
      }

      @SubscribeEvent
      public static void register(RegisterPayloadHandlersEvent event) {
         event.registrar("1").optional().playToClient(Sync.TYPE, Sync.CODEC, (s, ctx) -> ctx.enqueueWork(() -> {
            clientPull = s.pull;
            clientPullAt = ctx.player().level().getGameTime();
         }));
      }
   }
}
