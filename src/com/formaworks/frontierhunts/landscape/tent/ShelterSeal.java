package com.formaworks.frontierhunts.landscape.tent;

import com.formaworks.frontierhunts.camp.CampingTent;
import com.formaworks.frontierhunts.expedition.TowerBlind;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player.BedSleepingProblem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class ShelterSeal {
   private ShelterSeal() {
   }

   public static void clear(ServerLevel var0, Iterable<BlockPos> var1) {
      for (BlockPos var3 : var1) {
         BlockState var4 = var0.getBlockState(var3);
         if (!var4.isAir() && var4.canBeReplaced()) {
            var0.setBlock(var3, Blocks.AIR.defaultBlockState(), 18);
         }
      }
   }

   @SubscribeEvent
   public static void startSleep(CanPlayerSleepEvent var0) {
      if (var0.getProblem() == BedSleepingProblem.NOT_POSSIBLE_NOW
         && (
            var0.getState().getBlock() instanceof CompactTent
               || var0.getState().getBlock() instanceof BedBlock && insideTent(var0.getEntity().level(), var0.getPos())
         )) {
         var0.setProblem(null);
      }
   }

   @SubscribeEvent
   public static void continueSleep(CanContinueSleepingEvent var0) {
      if (var0.getProblem() == BedSleepingProblem.NOT_POSSIBLE_NOW) {
         Optional var1 = var0.getEntity().getSleepingPos();
         if (var1.isPresent()
            && (
               var0.getEntity().level().getBlockState((BlockPos)var1.get()).getBlock() instanceof CompactTent
                  || var0.getEntity().level().getBlockState((BlockPos)var1.get()).getBlock() instanceof BedBlock
                     && insideTent(var0.getEntity().level(), (BlockPos)var1.get())
            )) {
            var0.setContinueSleeping(true);
         }
      }
   }

   private static boolean insideTent(Level var0, BlockPos var1) {
      for (BlockPos var3 : BlockPos.betweenClosed(var1.offset(-4, -2, -4), var1.offset(4, 3, 4))) {
         Block var4 = var0.getBlockState(var3).getBlock();
         if (var4 instanceof CompactTent || var4 instanceof CampingTent) {
            return true;
         }
      }

      return false;
   }

   @SubscribeEvent
   public static void maintain(Post var0) {
      if (var0.getEntity() instanceof ServerPlayer var1 && var1.tickCount % 10 == 0) {
         ServerLevel var11 = var1.serverLevel();
         BlockPos var3 = var1.blockPosition();

         for (BlockPos var5 : BlockPos.betweenClosed(var3.offset(-7, -3, -7), var3.offset(7, 7, 7))) {
            BlockState var6 = var11.getBlockState(var5);
            Block var7 = var6.getBlock();
            if (var7 instanceof CompactTent var14) {
               BlockPos var15 = var14.origin(var5, var6);
               Direction var10 = var6.getValue(BlockStateProperties.HORIZONTAL_FACING);
               clear(var11, var14.freeCells().stream().map(var2 -> var15.offset(CompactTent.rotateLocal(var2, var10))).toList());
               return;
            }

            if (var7 instanceof CampingTent) {
               BlockPos var13 = CampingTent.origin(var5, var6);
               clear(var11, BlockPos.betweenClosed(var13.offset(-1, 0, -1), var13.offset(1, 1, 1)));
               return;
            }

            // (dev.61 ground-blind rework: the ground blind no longer clears its interior - grass, snow and plants inside
            // it are hidden client side instead of deleted; see expedition/BlindVolumes)
            if (var7 instanceof TowerBlind) {
               Direction var8 = var6.getValue(BlockStateProperties.HORIZONTAL_FACING);
               BlockPos var9 = TowerBlind.base(var5, var6).relative(var8.getOpposite()).above(4);
               clear(var11, BlockPos.betweenClosed(var9.offset(-1, 0, -1), var9.offset(1, 1, 1)));
               return;
            }
         }

         return;
      }
   }
}
