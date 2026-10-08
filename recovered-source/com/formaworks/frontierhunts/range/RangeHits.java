package com.formaworks.frontierhunts.range;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * [1.1.7] Scores hits on {@link RangeTargetBlock range targets}. Rifle bullets, the mod's arrows, slugs and shot and
 * vanilla arrows all fire NeoForge's projectile impact event, so one listener covers every weapon. The shooter gets the
 * result on the action bar, and on steel the ring reaches them as late as sound would: about three seconds per kilometre.
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class RangeHits {
   private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

   private RangeHits() {
   }

   private record Ring(UUID player, SoundEvent sound, float volume, float pitch, long due) {
   }

   private static final List<Ring> RINGS = new ArrayList<>();
   /** one score per projectile per block per tick (a projectile can report the same contact twice) */
   private static final Map<UUID, Long> SEEN = new HashMap<>();

   @SubscribeEvent(priority = EventPriority.LOW)
   public static void impact(ProjectileImpactEvent e) {
      Projectile proj = e.getProjectile();
      if (!(proj.level() instanceof ServerLevel level) || !(e.getRayTraceResult() instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
         return;
      }
      BlockPos pos = hit.getBlockPos();
      BlockState state = level.getBlockState(pos);
      if (!(state.getBlock() instanceof RangeTargetBlock target)) {
         return;
      }
      long stamp = level.getGameTime() * 31L + pos.asLong();
      Long seen = SEEN.put(proj.getUUID(), stamp);
      if (seen != null && seen == stamp) {
         return;
      }
      if (SEEN.size() > 512) {
         SEEN.clear();
      }
      Entity owner = proj.getOwner();
      Vec3 at = hit.getLocation();
      double metres = owner != null ? owner.getEyePosition().distanceTo(at) : 0.0;
      boolean arrow = proj instanceof AbstractArrow || proj.getClass().getSimpleName().contains("Arrow");
      Vec3 px = RangeTargetBlock.local(pos, state.getValue(RangeTargetBlock.FACING), at);
      RangeTargetBlock.Result r;
      try {
         r = target.hit(level, pos, state, px, metres, arrow);
      } catch (RuntimeException ex) {
         LOG.warn("[range] target hit failed at {}", pos, ex);
         return;
      }
      if (r == null) {
         return;
      }
      level.playSound(null, pos, r.sound(), SoundSource.BLOCKS, r.volume(), r.pitch());
      if (r.steel()) {
         level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.05, 0.05, 0.05, 0.25);
         level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 2, 0.03, 0.03, 0.03, 0.01);
      } else {
         level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 2, 0.04, 0.04, 0.04, 0.01);
      }
      if (owner instanceof ServerPlayer sp) {
         sp.displayClientMessage(r.line(), true);
         // beyond earshot of the block sound, the ring still reaches the shooter, late by the time sound takes to travel
         if (r.steel() && metres > 20.0) {
            long delay = Math.round(metres / 343.0 * 20.0);
            float vol = (float)Math.max(0.25, Math.min(1.0, 60.0 / metres));
            synchronized (RINGS) {
               RINGS.add(new Ring(sp.getUUID(), r.sound(), vol, r.pitch(), level.getGameTime() + Math.max(1L, delay)));
            }
         }
      }
   }

   @SubscribeEvent
   public static void tick(ServerTickEvent.Post e) {
      synchronized (RINGS) {
         if (RINGS.isEmpty()) {
            return;
         }
         MinecraftServer server = e.getServer();
         long now = server.overworld().getGameTime();
         for (Iterator<Ring> it = RINGS.iterator(); it.hasNext(); ) {
            Ring r = it.next();
            if (r.due > now) {
               continue;
            }
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(r.player);
            if (p != null) {
               p.playNotifySound(r.sound, SoundSource.BLOCKS, r.volume, r.pitch);
            }
         }
      }
   }
}
