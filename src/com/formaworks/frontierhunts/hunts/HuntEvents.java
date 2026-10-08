package com.formaworks.frontierhunts.hunts;

import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.journal.HuntBridge;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * [hunts] Game-bus wiring of the species hunts: glassing (binoculars / rangefinder held on an animal), grouse flushes,
 * bait piles and predator calls from the mod's existing items, the lure goal on wildlife, flaring ducks, the one-time
 * carry-over of older journal progress and queued rewards at login.
 *
 * <p>Cost: glassing is checked every 4 ticks only while a player holds glass up (a few short ray segments); the flush
 * check is one 16-block box query per player every 10 ticks; everything else is event driven.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class HuntEvents {
   private static final Logger LOG = LogUtils.getLogger();
   private static final Set<String> GLASS_ITEMS = Set.of("frontierhunts:binoculars", "frontierhunts:rangefinder", "frontierhunts:thermal_binoculars",
      "frontierhunts:night_vision_binoculars", "frontierhunts:anatomy_binoculars");
   private static final double GLASS_RANGE = 192.0;
   private static final Map<UUID, Live> LIVE = new HashMap<>();
   private static final Map<WildlifeMob, Integer> FLARING = new WeakHashMap<>();
   private static final Map<UUID, Component> MESSAGES = new HashMap<>();
   private static long tick;

   private static final class Live {
      UUID glassTarget;
      int glassHold;
      final LinkedHashSet<UUID> glassed = new LinkedHashSet<>();
      final LinkedHashSet<UUID> flushed = new LinkedHashSet<>();
   }

   private HuntEvents() {
   }

   private static Live live(ServerPlayer p) {
      return LIVE.computeIfAbsent(p.getUUID(), k -> new Live());
   }

   private static <T> void capped(LinkedHashSet<T> set, int max) {
      while (set.size() > max) {
         Iterator<T> it = set.iterator();
         it.next();
         it.remove();
      }
   }

   // ============================================================================================ lifecycle

   @SubscribeEvent
   public static void login(PlayerEvent.PlayerLoggedInEvent e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || !HuntsConfig.enabled()) {
         return;
      }
      try {
         HuntHooks.migrate(p);
         HuntRewards.deliver(p);
      } catch (RuntimeException ex) {
         LOG.warn("Frontier Hunts hunts: login setup failed for {}", p.getGameProfile().getName(), ex);
      }
   }

   @SubscribeEvent
   public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
      LIVE.remove(e.getEntity().getUUID());
      MESSAGES.remove(e.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent e) {
      LIVE.clear();
      FLARING.clear();
      MESSAGES.clear();
      Lures.clear();
      HuntStore.forget();
   }

   @SubscribeEvent
   public static void join(EntityJoinLevelEvent e) {
      if (e.getLevel().isClientSide() || !(e.getEntity() instanceof WildlifeMob m) || LureGoal.role(m.species) == 0) {
         return;
      }
      boolean has = m.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof LureGoal);
      if (!has) {
         m.goalSelector.addGoal(2, new LureGoal(m));
      }
   }

   @SubscribeEvent
   public static void serverTick(ServerTickEvent.Post e) {
      tick++;
      if (!MESSAGES.isEmpty()) {
         MESSAGES.forEach((id, msg) -> {
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
               p.displayClientMessage(msg, true);
            }
         });
         MESSAGES.clear();
      }
      if (!FLARING.isEmpty()) {
         Iterator<Map.Entry<WildlifeMob, Integer>> it = FLARING.entrySet().iterator();
         while (it.hasNext()) {
            Map.Entry<WildlifeMob, Integer> en = it.next();
            WildlifeMob m = en.getKey();
            int left = en.getValue() - 1;
            if (m == null || !m.isAlive() || m.isRemoved() || left <= 0) {
               it.remove();
               continue;
            }
            en.setValue(left);
            Vec3 v = m.getDeltaMovement();
            Vec3 fwd = Vec3.directionFromRotation(0.0F, m.getYRot());
            double up = left > 22 ? 0.3 : 0.08;
            m.setDeltaMovement(fwd.x * 0.42, Math.max(v.y, up), fwd.z * 0.42);
            m.fallDistance = 0.0F;
         }
      }
      if (tick % 1200L == 0L && HuntsConfig.enabled()) {
         try {
            HuntStore.get(e.getServer()).expireBaits(e.getServer().overworld().getGameTime());
         } catch (RuntimeException ignored) {
         }
      }
   }

   /** LureGoal: a duck spooked off the water or out of its approach flares away (turns from the threat and climbs). */
   static void flare(WildlifeMob m) {
      if (com.formaworks.frontierhunts.wingshot.BirdFlight.flare(m)) {
         return; // [wingshot] real flare: turn away and climb out (flight pilot)
      }
      if (m.level().isClientSide || FLARING.size() > 128) {
         return;
      }
      Entity threat = m.level().getNearestPlayer(m, 32.0);
      if (threat != null) {
         Vec3 away = m.position().subtract(threat.position());
         float yaw = (float)(Math.atan2(away.z, away.x) * 57.29577951308232) - 90.0F;
         m.setYRot(yaw);
         m.yBodyRot = yaw;
         m.yHeadRot = yaw;
      }
      m.setDeltaMovement(m.getDeltaMovement().add(0.0, 0.45, 0.0));
      FLARING.put(m, 40);
   }

   // ============================================================================================ glassing + flushes

   @SubscribeEvent
   public static void playerTick(PlayerTickEvent.Post e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || !p.isAlive() || p.isSpectator() || !HuntsConfig.enabled()) {
         return;
      }
      int phase = p.tickCount + (p.getId() & 7);
      try {
         if (phase % 4 == 0) {
            glassing(p);
         }
         if (phase % 10 == 0) {
            flushes(p);
         }
      } catch (RuntimeException ex) {
         LOG.debug("Frontier Hunts hunts: field check failed", ex);
      }
   }

   private static boolean glassUp(ServerPlayer p) {
      if (!p.isUsingItem()) {
         return false;
      }
      ItemStack s = p.getUseItem();
      ResourceLocation id = s.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(s.getItem());
      return id != null && GLASS_ITEMS.contains(id.toString());
   }

   private static void glassing(ServerPlayer p) {
      Live l = live(p);
      if (!glassUp(p)) {
         l.glassTarget = null;
         l.glassHold = 0;
         return;
      }
      ServerLevel level = p.serverLevel();
      Vec3 eye = p.getEyePosition();
      Vec3 look = p.getViewVector(1.0F);
      LivingEntity best = null;
      double bestT = Double.MAX_VALUE;
      // short boxes along the line of sight, nearest first, so we never query one huge box
      for (int k = 0; k < (int)(GLASS_RANGE / 24.0) && best == null; k++) {
         Vec3 a = eye.add(look.scale(k * 24.0));
         Vec3 b = eye.add(look.scale((k + 1) * 24.0));
         AABB box = new AABB(a, b).inflate(2.5);
         if (!level.hasChunkAt(BlockPos.containing(b))) {
            break;
         }
         for (LivingEntity c : level.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && (x instanceof WildlifeMob || x instanceof Whitetail w && !w.downed()))) {
            var hit = c.getBoundingBox().inflate(0.35).clip(eye, eye.add(look.scale(GLASS_RANGE)));
            if (hit.isPresent()) {
               double t = hit.get().distanceTo(eye);
               if (t < bestT) {
                  bestT = t;
                  best = c;
               }
            }
         }
      }
      if (best == null) {
         l.glassTarget = null;
         l.glassHold = 0;
         return;
      }
      HitResult block = level.clip(new ClipContext(eye, eye.add(look.scale(bestT)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
      if (block.getType() != HitResult.Type.MISS && block.getLocation().distanceTo(eye) < bestT - 0.6) {
         l.glassTarget = null;
         l.glassHold = 0;
         return; // a tree or a ridge in the way
      }
      if (!best.getUUID().equals(l.glassTarget)) {
         l.glassTarget = best.getUUID();
         l.glassHold = 1;
         return;
      }
      if (++l.glassHold == 4) {
         // held steady on it for about two thirds of a second
         double dist = Math.sqrt(best.distanceToSqr(p));
         boolean first = l.glassed.add(best.getUUID());
         capped(l.glassed, 256);
         if (first) {
            HuntHooks.glassed(p, best, dist);
         } else {
            HuntHooks.restamp(p, best, dist);
         }
      }
   }

   private static void flushes(ServerPlayer p) {
      if (HuntBridge.record(p) == null) {
         return;
      }
      List<WildlifeMob> birds = p.serverLevel().getEntitiesOfClass(WildlifeMob.class, p.getBoundingBox().inflate(16.0, 6.0, 16.0),
         m -> m.species == WildlifeSpecies.GROUSE && m.isAlive() && !m.onGround() && m.behavior() == WildlifeMob.FLEE);
      if (birds.isEmpty()) {
         return;
      }
      Live l = live(p);
      for (WildlifeMob b : birds) {
         if (l.flushed.add(b.getUUID())) {
            capped(l.flushed, 128);
            HuntHooks.flushed(p, b, Math.sqrt(b.distanceToSqr(p)));
         }
      }
   }

   // ============================================================================================ bait + predator call

   @SubscribeEvent
   public static void useItem(PlayerInteractEvent.RightClickItem e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || p.isSpectator() || !HuntsConfig.lures()) {
         return;
      }
      ItemStack s = e.getItemStack();
      if (s.isEmpty() || p.getCooldowns().isOnCooldown(s.getItem())) {
         return; // the item's own use will not happen either
      }
      ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
      if (id == null) {
         return;
      }
      try {
         ServerLevel level = p.serverLevel();
         long now = level.getGameTime();
         switch (id.toString()) {
            case "frontierhunts:predator_call" -> Lures.blow(Lures.Kind.PREDATOR, p.getUUID(), level.dimension().location().toString(), p.position(), now, 112.0);
            case "frontierhunts:bait" -> {
               BlockPos at = p.onGround() ? p.blockPosition() : p.blockPosition().below();
               HuntStore.get(p.server).bait(p.getUUID(), level.dimension().location().toString(), at, now + 48000L, 4);
               // after the item's own "call placed" line
               MESSAGES.put(p.getUUID(), Component.translatable("hunts.frontierhunts.msg.bait"));
            }
            default -> {
            }
         }
      } catch (RuntimeException ex) {
         LOG.debug("Frontier Hunts hunts: lure use failed", ex);
      }
   }

   /** Commands / tests: blow a call as if this player had used the item. */
   static void blow(ServerPlayer p, Lures.Kind kind) {
      ServerLevel level = p.serverLevel();
      Lures.blow(kind, p.getUUID(), level.dimension().location().toString(), p.position(), level.getGameTime(), kind == Lures.Kind.DUCK ? 96.0 : 112.0);
   }
}
