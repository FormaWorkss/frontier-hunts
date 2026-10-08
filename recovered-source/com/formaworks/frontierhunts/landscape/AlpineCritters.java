package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class AlpineCritters {
   private static final Map<ServerPlayer, AlpineCritters.Track> TRACKS = new WeakHashMap<>();

   @SubscribeEvent
   public static void tick(Post var0) {
      if (!(var0.getEntity() instanceof ServerPlayer var1) || var1.tickCount % 10 != 0) {
         return;
      }

      if (var1.level() instanceof ServerLevel var29 && var29.dimensionType().hasSkyLight()) {
         long var30 = var29.getGameTime();
         AlpineCritters.Track var5 = TRACKS.get(var1);
         Vec3 var6 = var1.position();
         TRACKS.put(var1, new AlpineCritters.Track(var6, var5 == null ? var30 + 600L : var5.quietUntil));
         if (var5 != null && var30 >= var5.quietUntil && !var1.isSpectator() && !var1.isCrouching() && !var1.isPassenger()) {
            if (!var1.onGround()) {
               int var7 = var29.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var1.getBlockX(), var1.getBlockZ());
               if (var1.getY() - (double)var7 > 3.5) {
                  return;
               }
            }

            double var31 = var6.distanceTo(var5.last);
            if (!(var31 < 1.1) && !(var31 > 9.0)) {
               int var9 = var29.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var1.getBlockX(), var1.getBlockZ());
               BlockPos var10 = new BlockPos(var1.getBlockX(), Math.min(var1.getBlockY(), var9), var1.getBlockZ());
               int var11 = 0;

               for (int var12 = -2; var12 <= 2; var12++) {
                  for (int var13 = -2; var13 <= 2; var13++) {
                     for (int var14 = -1; var14 <= 0; var14++) {
                        if (cover(var29.getBlockState(var10.offset(var12, var14, var13)))) {
                           var11++;
                        }
                     }
                  }
               }

               if (var11 < 5) {
                  return;
               }

               double var32 = (var31 > 2.4 ? 0.16 : 0.1) * Math.min(1.0, (double)var11 / 12.0);
               if (var29.random.nextDouble() > var32) {
                  return;
               }

               Vec3 var33 = var6.subtract(var5.last).normalize();
               double var15 = 3.0 + var29.random.nextDouble() * 4.0;
               double var17 = (var29.random.nextDouble() - 0.5) * 4.0;
               double var19 = var6.x + var33.x * var15 - var33.z * var17;
               double var21 = var6.z + var33.z * var15 + var33.x * var17;
               int var23 = var29.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, (int)Math.floor(var19), (int)Math.floor(var21));
               if (Math.abs(var23 - var10.getY()) > 4) {
                  return;
               }

               TRACKS.put(var1, new AlpineCritters.Track(var6, var30 + 300L + (long)var29.random.nextInt(600)));
               int var24 = 4 + var29.random.nextInt(6);
               var29.sendParticles((SimpleParticleType)AlpineRegistration.FLUSH_BIRD.get(), var19, (double)var23 + 0.4, var21, var24, 0.8, 0.2, 0.8, 0.0);
               SoundEvent var25 = (SoundEvent)BuiltInRegistries.SOUND_EVENT.get(FrontierHunts.id("amb_bird_chirp"));
               if (var25 != null) {
                  var29.playSound(null, var19, (double)var23 + 0.5, var21, var25, SoundSource.AMBIENT, 1.3F, 1.0F + var29.random.nextFloat() * 0.2F);
               }

               Vec3 var26 = new Vec3(var19, (double)var23 + 0.5, var21);

               for (Whitetail var28 : var29.getEntitiesOfClass(Whitetail.class, new AABB(var26, var26).inflate(40.0), var0x -> !var0x.downed())) {
                  var28.hear(var26, 34.0, 0.55F);
               }

               return;
            }

            return;
         }

         return;
      }
   }

   private static boolean cover(BlockState var0) {
      if (var0.is((Block)AlpineRegistration.PASTURE.get())) {
         return (Integer)var0.getValue(AlpinePasture.HEIGHT) >= 1;
      } else {
         return !(var0.getBlock() instanceof AlpineThicket) && !(var0.getBlock() instanceof AlpineReeds)
            ? var0.is(Blocks.TALL_GRASS)
               || var0.is(Blocks.LARGE_FERN)
               || var0.is(Blocks.FERN)
               || var0.is(Blocks.SWEET_BERRY_BUSH)
               || var0.is((Block)ExpeditionContent.UNDERGROWTH.get())
            : true;
      }
   }

   private AlpineCritters() {
   }

   private static record Track(Vec3 last, long quietUntil) {
   }
}
