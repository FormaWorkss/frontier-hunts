package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.camp.CampingTent;
import com.formaworks.frontierhunts.expedition.HubGroundBlind;
import com.formaworks.frontierhunts.expedition.MountedTreeStand;
import com.formaworks.frontierhunts.expedition.StandLadder;
import com.formaworks.frontierhunts.expedition.TowerBlind;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

final class FieldBlockParticles implements IClientBlockExtensions {
   static void register(RegisterClientExtensionsEvent var0) {
      FieldBlockParticles var1 = new FieldBlockParticles();

      for (Entry var3 : BuiltInRegistries.BLOCK.entrySet()) {
         if (((ResourceKey)var3.getKey()).location().getNamespace().equals("frontierhunts") && !var0.isBlockRegistered((Block)var3.getValue())) {
            var0.registerBlock(var1, new Block[]{(Block)var3.getValue()});
         }
      }
   }

   public boolean areBreakingParticlesTinted(BlockState var1, ClientLevel var2, BlockPos var3) {
      return false;
   }

   public boolean addHitEffects(BlockState var1, Level var2, HitResult var3, ParticleEngine var4) {
      if (var2 instanceof ClientLevel var5 && var3 instanceof BlockHitResult var6 && var1.shouldSpawnTerrainParticles()) {
         Vec3i var7 = var6.getDirection().getNormal();
         Vec3 var8 = var6.getLocation().add((double)var7.getX() * 0.025, (double)var7.getY() * 0.025, (double)var7.getZ() * 0.025);
         var4.add(
            new FieldBlockParticles.Fragment(
               var5,
               var8.x,
               var8.y,
               var8.z,
               (double)var7.getX() * 0.015,
               0.018 + (double)var7.getY() * 0.015,
               (double)var7.getZ() * 0.015,
               var1,
               var6.getBlockPos(),
               true
            )
         );
         return true;
      }

      return true;
   }

   private static boolean assembled(BlockState var0) {
      Block var1 = var0.getBlock();
      return var1 instanceof TowerBlind
         || var1 instanceof HubGroundBlind
         || var1 instanceof MountedTreeStand
         || var1 instanceof StandLadder
         || var1 instanceof CampingTent;
   }

   public boolean addDestroyEffects(BlockState var1, Level var2, BlockPos var3, ParticleEngine var4) {
      if (!(var2 instanceof ClientLevel var5) || !var1.shouldSpawnTerrainParticles()) {
         return true;
      }

      if (assembled(var1)) {
         for (int var20 = 0; var20 < 18; var20++) {
            var4.crack(var3, Direction.values()[var5.random.nextInt(6)]);
         }

         return true;
      } else {
         List var6 = var1.getShape(var2, var3).toAabbs();
         if (var6.isEmpty()) {
            return true;
         } else {
            byte var7 = switch ((HuntConfig.Quality)HuntConfig.QUALITY.get()) {
               case PERFORMANCE -> 8;
               case BALANCED -> 12;
               case CINEMATIC -> 16;
            };
            double var8 = 0.0;

            for (AABB var11 : var6) {
               var8 += var11.getXsize() * var11.getYsize() * var11.getZsize();
            }

            for (int var21 = 0; var21 < var7; var21++) {
               double var22 = var5.random.nextDouble() * var8;
               AABB var13 = (AABB)var6.getLast();

               for (AABB var15 : var6) {
                  var22 -= var15.getXsize() * var15.getYsize() * var15.getZsize();
                  if (var22 <= 0.0) {
                     var13 = var15;
                     break;
                  }
               }

               double var23 = var13.minX + var5.random.nextDouble() * var13.getXsize();
               double var16 = var13.minY + var5.random.nextDouble() * var13.getYsize();
               double var18 = var13.minZ + var5.random.nextDouble() * var13.getZsize();
               var4.add(
                  new FieldBlockParticles.Fragment(
                     var5,
                     (double)var3.getX() + var23,
                     (double)var3.getY() + var16,
                     (double)var3.getZ() + var18,
                     (var23 - 0.5) * 0.055,
                     0.027 + var5.random.nextDouble() * 0.035,
                     (var18 - 0.5) * 0.055,
                     var1,
                     var3,
                     false
                  )
               );
            }

            return true;
         }
      }
   }

   private static final class Fragment extends TerrainParticle {
      Fragment(
         ClientLevel var1, double var2, double var4, double var6, double var8, double var10, double var12, BlockState var14, BlockPos var15, boolean var16
      ) {
         super(var1, var2, var4, var6, 0.0, 0.0, 0.0, var14, var15);
         this.setParticleSpeed(var8, var10, var12);
         this.quadSize = var16 ? 0.028F : 0.035F + this.random.nextFloat() * 0.018F;
         this.lifetime = var16 ? 8 : 12 + this.random.nextInt(9);
         this.gravity = 0.6F;
         this.friction = 0.82F;
         this.rCol = this.gCol = this.bCol = 0.83F;
      }
   }
}
