package com.formaworks.frontierhunts.wingshot;

import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * [wingshot] Collects the hits on birds during a server tick (a shotgun puts several pellets into one bird in the same
 * tick) and sends one {@link BirdNet.Hit} per bird at the end of the tick to everyone tracking it; a clean wing shot
 * also tells its shooter ({@link BirdNet.WingShot}). Bounded: at most 64 birds per tick.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class BirdHits {
   private static final Map<WildlifeMob, Pending> PENDING = new LinkedHashMap<>();

   private BirdHits() {
   }

   private static final class Pending {
      Vec3 at, dir;
      float energy;
      int count, flags;
      byte shot = BirdNet.SHOT_OTHER;
      ServerPlayer shooter;
      boolean clean;
   }

   static void record(WildlifeMob m, Vec3 at, Vec3 dir, float energy, byte shot, boolean killed, boolean flying, boolean water, ServerPlayer shooter,
      boolean clean) {
      Pending p = PENDING.get(m);
      if (p == null) {
         if (PENDING.size() >= 64) {
            return;
         }
         p = new Pending();
         p.at = at;
         p.dir = dir;
         PENDING.put(m, p);
      }
      p.energy = Math.min(4.0F, p.energy + energy);
      p.count = Math.min(30, p.count + 1);
      // the most telling weapon of the tick wins (pellets > bullet > arrow > other)
      if (shot == BirdNet.SHOT_PELLETS || p.shot == BirdNet.SHOT_OTHER || shot == BirdNet.SHOT_BULLET && p.shot == BirdNet.SHOT_ARROW) {
         p.shot = shot;
      }
      p.flags |= (killed ? BirdNet.KILLED : 0) | (flying ? BirdNet.FLYING : 0) | (water ? BirdNet.WATER : 0);
      if (shooter != null) {
         p.shooter = shooter;
      }
      p.clean |= clean && p.count == 1;
   }

   @SubscribeEvent
   public static void tick(ServerTickEvent.Post e) {
      if (PENDING.isEmpty()) {
         return;
      }
      for (Map.Entry<WildlifeMob, Pending> en : PENDING.entrySet()) {
         WildlifeMob m = en.getKey();
         Pending p = en.getValue();
         if (!(m.level() instanceof ServerLevel level)) {
            continue;
         }
         Wilderness.Wind w = Wilderness.wind(level.getSeed(), level.getGameTime(), level.isRaining(), level.isThundering());
         BirdNet.Hit hit = new BirdNet.Hit(m.getId(), (byte)m.species.ordinal(), (float)p.at.x, (float)p.at.y, (float)p.at.z, (float)p.dir.x, (float)p.dir.y,
            (float)p.dir.z, p.energy, (byte)p.flags, (byte)p.count, p.shot, (float)w.east(), (float)w.south());
         boolean shooterGot = false;
         for (ServerPlayer sp : level.getChunkSource().chunkMap.getPlayers(m.chunkPosition(), false)) {
            if (sp.distanceToSqr(m) < 160.0 * 160.0) {
               BirdNet.send(sp, hit);
               shooterGot |= sp == p.shooter;
            }
         }
         if (p.shooter != null && !shooterGot && p.shooter.level() == level) {
            BirdNet.send(p.shooter, hit);
         }
         if (p.clean && p.shooter != null && (p.flags & BirdNet.KILLED) != 0 && (p.flags & BirdNet.FLYING) != 0) {
            float d = (float)Math.sqrt(p.shooter.distanceToSqr(m));
            if (d >= 6.0F) {
               BirdNet.send(p.shooter, new BirdNet.WingShot(m.getId(), d));
            }
         }
      }
      PENDING.clear();
   }
}
