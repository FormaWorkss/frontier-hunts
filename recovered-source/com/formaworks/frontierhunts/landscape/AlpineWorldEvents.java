package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.HuntRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules.BooleanValue;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent.CreateSpawnPosition;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class AlpineWorldEvents {
   @SubscribeEvent
   public static void firstSpawn(CreateSpawnPosition var0) {
      if (var0.getLevel() instanceof ServerLevel var1 && var1.getChunkSource().getGenerator() instanceof AlpineGenerator var2) {
         ((BooleanValue)var1.getGameRules().getRule(HuntRules.ENABLED)).set(true, var1.getServer());
         AlpineLayout var16 = var2.layout();
         double var4 = Double.MAX_VALUE;
         short var6 = 0;
         short var7 = 0;

         for (short var8 = -2304; var8 <= 2304; var8 += 32) {
            for (short var9 = -2304; var9 <= 2304; var9 += 32) {
               AlpineLayout.Sample var10 = var16.sample((double)var8, (double)var9);
               if (!var10.wet()
                  && !(var10.ground() > 420.0)
                  && !(var10.distance() < var10.width() + 24.0)
                  && !(var10.distance() > var10.width() + 110.0)
                  && !(var10.forest() > 0.6)
                  && !(var16.slope(var8, var9) > 0.28)) {
                  double var11 = (double)var8 * (double)var8 + (double)var9 * (double)var9 + var10.ground() * 750.0;
                  if (var11 < var4) {
                     var4 = var11;
                     var6 = var8;
                     var7 = var9;
                  }
               }
            }
         }

         if (var4 == Double.MAX_VALUE) {
            return;
         }

         for (int var17 = 0; var17 <= 8; var17++) {
            for (int var18 = -var17; var18 <= var17; var18++) {
               for (int var19 = -var17; var19 <= var17; var19++) {
                  if (Math.max(Math.abs(var18), Math.abs(var19)) == var17) {
                     int var20 = var6 + var18;
                     int var12 = var7 + var19;
                     AlpineLayout.Sample var13 = var16.sample((double)var20, (double)var12);
                     BlockPos var14 = new BlockPos(var20, var13.floor() + 1, var12);
                     var1.getChunk(var20 >> 4, var12 >> 4);
                     if (var1.getBlockState(var14).getCollisionShape(var1, var14).isEmpty()
                        && var1.getBlockState(var14.above()).getCollisionShape(var1, var14.above()).isEmpty()
                        && var1.getFluidState(var14).isEmpty()
                        && var1.getBlockState(var14.below()).isFaceSturdy(var1, var14.below(), Direction.UP)) {
                        var1.setDefaultSpawnPos(var14, 0.0F);
                        var0.setCanceled(true);
                        return;
                     }
                  }
               }
            }
         }

         return;
      }
   }
}
