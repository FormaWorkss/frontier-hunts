package com.formaworks.frontierhunts.tracking.hound;

import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * [hound3] The animals each hunter has hit recently (server memory): the TRACK order puts the hound on the newest one
 * (or the one picked in the command wheel), starting at the spot it was hit.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class HoundWounds {
   /** forget hits after a whole in-game day */
   public static final long KEEP = 24000L;
   private static final int PER_PLAYER = 6;
   private static final Map<UUID, ArrayDeque<Hit>> HITS = new HashMap<>();

   public record Hit(UUID animal, long time, Vec3 pos, String label) {
   }

   private HoundWounds() {
   }

   /** game a hound trails: deer, the wildlife big game and predators (not birds, not the hounds themselves) */
   static boolean trailable(LivingEntity e) {
      if (e instanceof Whitetail) {
         return true;
      }
      return e instanceof WildlifeMob m && !m.species.bird;
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void incoming(LivingIncomingDamageEvent e) {
      LivingEntity v = e.getEntity();
      if (e.isCanceled() || !(v.level() instanceof ServerLevel level) || !(e.getSource().getEntity() instanceof ServerPlayer p) || !trailable(v)) {
         return;
      }
      long now = level.getGameTime();
      Vec3 at = v.position();
      synchronized (HITS) {
         ArrayDeque<Hit> q = HITS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
         q.removeIf(h -> h.animal.equals(v.getUUID()) || now - h.time > KEEP);
         // newest hit of this animal: its line is taken up from there
         q.addFirst(new Hit(v.getUUID(), now, at, TrackingHound.labelFor(level, v.getUUID(), "game")));
         while (q.size() > PER_PLAYER) {
            q.removeLast();
         }
      }
      TrackingHound h = HoundRegistry.find(p.server, p.getUUID());
      if (h != null && h.level() == level) {
         h.ownerWounded(v.getUUID(), now, at);
      }
   }

   /** newest first */
   public static List<Hit> recent(ServerPlayer p) {
      long now = p.serverLevel().getGameTime();
      List<Hit> out = new ArrayList<>();
      synchronized (HITS) {
         ArrayDeque<Hit> q = HITS.get(p.getUUID());
         if (q != null) {
            for (Iterator<Hit> it = q.iterator(); it.hasNext(); ) {
               Hit h = it.next();
               if (now - h.time > KEEP || now < h.time) {
                  it.remove();
               } else {
                  out.add(h);
               }
            }
         }
      }
      return out;
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent e) {
      synchronized (HITS) {
         HITS.remove(e.getEntity().getUUID());
      }
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent e) {
      synchronized (HITS) {
         HITS.clear();
      }
   }
}
