package com.formaworks.frontierhunts.hunting;

import com.formaworks.frontierhunts.environment.ForestFloor;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.HuntProjectile;
import com.formaworks.frontierhunts.expedition.Weapon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent.Detonate;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;
import org.joml.Vector3f;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class WhitetailHearing {
   private static long lastTick = Long.MIN_VALUE;
   private static Vec3 lastSource = Vec3.ZERO;

   @SubscribeEvent
   public static void launch(EntityJoinLevelEvent var0) {
      if (var0.getLevel() instanceof ServerLevel var1 && !var0.loadedFromDisk()) {
         Entity var9 = var0.getEntity();
         double var3;
         if (var9 instanceof FieldArrow) {
            var3 = 10.0;
         } else {
            if (!(var9 instanceof HuntProjectile var5)) {
               return;
            }

            Weapon var6 = var5.kind();
            if (var6.bow) {
               var3 = 10.0;
            } else {
               var3 = switch (var6) {
                  case TRANQUILIZER_RIFLE -> 30.0;
                  case BAIT_LAUNCHER -> 18.0;
                  case FLARE_GUN -> 44.0;
                  default -> 84.0;
               };
            }

            if (!var6.bow && var5.getOwner() instanceof LivingEntity var7 && ExpeditionWeapon.attachment(var7.getMainHandItem(), "suppressor")) {
               var3 *= 0.58;
            }
         }

         broadcast(var1, var9.position(), var3);
         return;
      }
   }

   @SubscribeEvent
   public static void explosion(Detonate var0) {
      if (var0.getLevel() instanceof ServerLevel var1) {
         broadcast(var1, var0.getExplosion().center(), 72.0);
      }
   }

   public static void broadcast(ServerLevel var0, Vec3 var1, double var2) {
      long var4 = var0.getGameTime();
      if (var4 != lastTick || !(var1.distanceToSqr(lastSource) < 4.0)) {
         lastTick = var4;
         lastSource = var1;

         for (Whitetail var7 : var0.getEntitiesOfClass(Whitetail.class, new AABB(var1, var1).inflate(var2), var0x -> !var0x.downed())) {
            var7.hear(var1, var2);
         }
      }
   }

   public static void snap(ServerLevel var0, Vec3 var1, double var2) {
      for (Whitetail var5 : var0.getEntitiesOfClass(Whitetail.class, new AABB(var1, var1).inflate(var2), var0x -> !var0x.downed())) {
         var5.hear(var1, var2, 0.55F);
      }
   }

   @SubscribeEvent
   public static void footsteps(Post var0) {
      if (!(var0.getEntity() instanceof ServerPlayer var1) || var1.isSpectator() || var1.isCreative()) {
         return;
      }

      if (var1.tickCount % 7 == 0 && !var1.isCrouching() && !var1.isPassenger() && var1.onGround()) {
         double var15 = HuntPerception.speed(var1.getUUID());
         if (!(var15 < 1.2)) {
            ServerLevel var4 = var1.serverLevel();
            BlockPos var5 = var1.blockPosition();
            BlockState var6 = var4.getBlockState(var5);
            BlockState var7 = var4.getBlockState(var5.below());
            double var8 = 0.0;
            boolean var10 = false;
            ForestFloor var11 = var6.getBlock() instanceof ForestFloor var13 ? var13 : (var7.getBlock() instanceof ForestFloor var12 ? var12 : null);
            if (var11 != null) {
               var8 = var11.loudness >= 2 ? 16.0 : 11.0;
               var10 = true;
            } else if (var6.is(Blocks.DEAD_BUSH) || var6.is(Blocks.SWEET_BERRY_BUSH) || var6.is(BlockTags.LEAVES)) {
               var8 = 13.0;
               var10 = true;
            } else if (var7.is(BlockTags.LEAVES)) {
               var8 = 12.0;
            } else if (var6.is(Blocks.FERN) || var6.is(Blocks.LARGE_FERN) || var6.is(Blocks.TALL_GRASS)) {
               var8 = var1.isSprinting() ? 9.0 : 0.0;
            } else if (var7.is(Blocks.GRAVEL) || var7.is(Blocks.SUSPICIOUS_GRAVEL)) {
               var8 = 8.0;
            }

            if (!(var8 <= 0.0)) {
               if (var1.isSprinting()) {
                  var8 *= 1.4;
               }

               if (var10) {
                  var4.playSound(
                     null,
                     var5,
                     (SoundEvent)HuntSounds.BRUSH_RUSTLE.get(),
                     SoundSource.BLOCKS,
                     var1.isSprinting() ? 0.72F : 0.48F,
                     0.86F + var4.random.nextFloat() * 0.28F
                  );
                  if (var11 != null) {
                     Vec3 var16 = ForestFloor.dust(var4.random);
                     var4.sendParticles(
                        new DustParticleOptions(new Vector3f((float)var16.x, (float)var16.y, (float)var16.z), 1.1F),
                        var1.getX(),
                        var1.getY() + 0.12,
                        var1.getZ(),
                        4,
                        0.22,
                        0.06,
                        0.22,
                        0.0
                     );
                  }
               }

               snap(var4, var1.position(), var8);
            }
         }
      }
   }

   private WhitetailHearing() {
   }
}
