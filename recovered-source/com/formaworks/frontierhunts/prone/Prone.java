package com.formaworks.frontierhunts.prone;

import com.formaworks.frontierhunts.expedition.BlindVolumes;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * [rifle] Prone stance: lying flat on the ground in vanilla's crawl pose (SWIMMING pose on land, 0.6 high hitbox,
 * vanilla crawl speed, no sprint or jump).
 *
 * <p>Server authoritative. A client asks with {@link ProneNetwork.Request}; the server validates (alive, on the
 * ground, not riding / sleeping / gliding / swimming / climbing / flying / inside a ground blind; standing up needs
 * head room), then holds the pose with NeoForge's {@code Player#setForcedPose} and tells the player and everyone
 * tracking them ({@link ProneNetwork.Sync}). Clients mirror the forced pose on their copy of the player, so the local
 * player predicts nothing and every observer sees the same crawl. The pose is dropped automatically the moment any
 * of those conditions appears (checked at the start of each player tick, before vanilla picks the pose), on death,
 * respawn, dimension change and logout.
 *
 * <p>Works with any held item; only Frontier Hunts weapons change their handling (bipod, sway, spread, recoil).
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class Prone {
   /** Server: prone players. */
   private static final Set<UUID> SERVER = ConcurrentHashMap.newKeySet();
   /** Client: entity ids of prone players in the client level (written by the sync handler on the client thread). */
   private static final Set<Integer> CLIENT = ConcurrentHashMap.newKeySet();
   private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
   private static final int REQUEST_TICKS = 5;

   private Prone() {
   }

   // ------------------------------------------------------------------------------------------- queries (both sides)

   /** Is this entity a prone player (on its own side: server set on the server, synced set on a client)? */
   public static boolean isProne(Entity e) {
      if (!(e instanceof Player p)) {
         return false;
      }
      return p.level().isClientSide ? CLIENT.contains(p.getId()) : SERVER.contains(p.getUUID());
   }

   /** Something that ends (or forbids) the prone stance right now. Shared by server validation and client mirroring. */
   public static boolean blocked(Player p) {
      return !p.isAlive()
         || p.isSpectator()
         || p.isPassenger()
         || p.isSleeping()
         || p.isFallFlying()
         || p.isInWater()
         || p.isInLava()
         || p.isSwimming()
         || p.onClimbable()
         || p.getAbilities().flying
         || p.fallDistance > 2.5F
         || insideBlind(p);
   }

   private static boolean insideBlind(Player p) {
      BlockPos at = p.blockPosition();
      return !p.level().isClientSide && BlindVolumes.overlaps(p.level(), at, at.above());
   }

   /** Room for the body in this pose at the player's position (Player#canPlayerFitWithinBlocksAndEntitiesWhen). */
   public static boolean fits(Player p, Pose pose) {
      return p.level().noCollision(p, p.getDimensions(pose).makeBoundingBox(p.position()).deflate(1.0E-7));
   }

   // ------------------------------------------------------------------------------------------- server

   /** A client asked to go prone ({@code want}) or to get up. Validated; the requester always gets the verdict. */
   static void request(ServerPlayer p, boolean want) {
      long now = p.level().getGameTime();
      Long last = LAST_REQUEST.get(p.getUUID());
      if (last != null && now >= last && now - last < REQUEST_TICKS) {
         sync(p, isProne(p), true);
         return;
      }
      LAST_REQUEST.put(p.getUUID(), now);
      boolean prone = isProne(p);
      if (want == prone) {
         sync(p, prone, true);
         return;
      }
      if (want) {
         if (blocked(p) || !p.onGround() || !fits(p, Pose.SWIMMING)) {
            p.displayClientMessage(Component.translatable("prone.frontierhunts.cannot"), true);
            sync(p, false, true);
            return;
         }
         set(p, true);
      } else {
         if (!fits(p, Pose.STANDING) && !fits(p, Pose.CROUCHING)) {
            p.displayClientMessage(Component.translatable("prone.frontierhunts.no_room"), true);
            sync(p, true, true);
            return;
         }
         set(p, false);
      }
   }

   /** Sets the stance on the server, holds / releases the pose and tells everyone who can see the player. */
   public static void set(ServerPlayer p, boolean prone) {
      boolean was = SERVER.contains(p.getUUID());
      if (prone) {
         SERVER.add(p.getUUID());
         p.setForcedPose(Pose.SWIMMING);
         p.setPose(Pose.SWIMMING);
         p.setSprinting(false);
      } else {
         SERVER.remove(p.getUUID());
         if (p.getForcedPose() == Pose.SWIMMING) {
            p.setForcedPose(null); // vanilla picks the pose again next tick (crouch / crawl on its own if there is no room)
         }
      }
      if (was != prone) {
         rustle(p, prone);
      }
      sync(p, prone, false);
   }

   /** Cloth and gear against the ground: the block's own step sound plus a soft leather rustle, heard nearby. */
   private static void rustle(ServerPlayer p, boolean down) {
      Level level = p.level();
      BlockState ground = level.getBlockState(p.getOnPos());
      SoundType type = ground.getSoundType(level, p.getOnPos(), p);
      float pitch = (down ? 0.78F : 0.9F) + p.getRandom().nextFloat() * 0.08F;
      level.playSound(null, p.getX(), p.getY(), p.getZ(), type.getStepSound(), SoundSource.PLAYERS, type.getVolume() * (down ? 0.55F : 0.4F), type.getPitch() * pitch);
      level.playSound(null, p.getX(), p.getY() + 0.4, p.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, down ? 0.34F : 0.26F, down ? 0.82F : 0.95F);
   }

   private static void sync(ServerPlayer p, boolean prone, boolean selfOnly) {
      ProneNetwork.Sync msg = new ProneNetwork.Sync(p.getId(), prone);
      ProneNetwork.send(p, msg);
      if (!selfOnly && p.level() instanceof net.minecraft.server.level.ServerLevel level) {
         for (ServerPlayer other : level.getChunkSource().chunkMap.getPlayers(p.chunkPosition(), false)) {
            if (other != p) {
               ProneNetwork.send(other, msg);
            }
         }
      }
   }

   @SubscribeEvent
   public static void tick(PlayerTickEvent.Pre event) {
      if (!(event.getEntity() instanceof ServerPlayer p) || !SERVER.contains(p.getUUID())) {
         return;
      }
      if (p.getForcedPose() != Pose.SWIMMING) {
         // something else took the pose over: let it go without touching the forced pose
         SERVER.remove(p.getUUID());
         sync(p, false, false);
      } else if (blocked(p)) {
         set(p, false); // runs before vanilla picks the pose this tick: sleeping, swimming, riding... take over cleanly
      } else if (p.isSprinting()) {
         p.setSprinting(false);
      }
   }

   @SubscribeEvent
   public static void tracking(PlayerEvent.StartTracking event) {
      if (event.getEntity() instanceof ServerPlayer watcher && event.getTarget() instanceof ServerPlayer target && SERVER.contains(target.getUUID())) {
         ProneNetwork.send(watcher, new ProneNetwork.Sync(target.getId(), true));
      }
   }

   @SubscribeEvent
   public static void mount(EntityMountEvent event) {
      if (event.isMounting() && event.getEntityMounting() instanceof ServerPlayer p && SERVER.contains(p.getUUID())) {
         set(p, false);
      }
   }

   @SubscribeEvent
   public static void death(LivingDeathEvent event) {
      if (event.getEntity() instanceof ServerPlayer p && SERVER.contains(p.getUUID())) {
         set(p, false);
      }
   }

   @SubscribeEvent
   public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
      clear(event.getEntity());
   }

   @SubscribeEvent
   public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
      clear(event.getEntity());
   }

   private static void clear(Player player) {
      if (player instanceof ServerPlayer p) {
         boolean was = SERVER.remove(p.getUUID());
         if (p.getForcedPose() == Pose.SWIMMING && was) {
            p.setForcedPose(null);
         }
         if (was) {
            sync(p, false, false);
         }
      }
   }

   @SubscribeEvent
   public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
      SERVER.remove(event.getEntity().getUUID());
      LAST_REQUEST.remove(event.getEntity().getUUID());
      if (event.getEntity().getForcedPose() == Pose.SWIMMING) {
         event.getEntity().setForcedPose(null);
      }
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent event) {
      SERVER.clear();
      LAST_REQUEST.clear();
   }

   // ------------------------------------------------------------------------------------------- client mirror

   /** Client thread: the server says this player is (not) prone. Mirrors the forced pose on the client's entity. */
   static void applyClient(Level level, int id, boolean prone) {
      if (level == null || !level.isClientSide) {
         return;
      }
      if (prone) {
         CLIENT.add(id);
      } else {
         CLIENT.remove(id);
      }
      if (level.getEntity(id) instanceof Player p) {
         mirror(p, prone);
      }
   }

   /** Client: keep the forced pose of every prone player in the level (late-loaded entities, local exit conditions). */
   public static void clientTick(Level level) {
      if (level == null || CLIENT.isEmpty()) {
         return;
      }
      for (Integer id : CLIENT) {
         if (level.getEntity(id) instanceof Player p) {
            mirror(p, true);
         }
      }
   }

   private static void mirror(Player p, boolean prone) {
      if (prone && !blocked(p)) {
         if (p.getForcedPose() == null) {
            p.setForcedPose(Pose.SWIMMING);
         }
      } else if (p.getForcedPose() == Pose.SWIMMING) {
         p.setForcedPose(null);
      }
   }

   /** Client: forget everything (level change / logout). */
   public static void clientReset() {
      CLIENT.clear();
   }
}
