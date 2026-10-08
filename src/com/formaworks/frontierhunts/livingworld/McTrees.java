package com.formaworks.frontierhunts.livingworld;

import com.formaworks.frontierhunts.environment.WildTrees;
import com.formaworks.frontierhunts.livingworld.plan.Kit;
import com.formaworks.frontierhunts.livingworld.plan.Plan;
import com.formaworks.frontierhunts.livingworld.plan.Rnd;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * [livingworld] Trees for the plans (stand trees, edge trees): in the Frontier biomes the mod's own tree generator (the
 * same look as the reserve's forests, round trees on Ultra), in vanilla biomes the plan's spruce/birch. The blocks become
 * ordinary plan blocks, so a tree that spans several chunks is written identically whatever the chunk order.
 */
public final class McTrees implements Kit.TreeMaker {
   static void install() {
      Kit.treeMaker = new McTrees();
   }

   @Override
   public List<Plan.Block> grow(String kind, long seed, int x, int y, int z, int trunk, String biome) {
      if (!biome.startsWith("frontierhunts:")) {
         return Kit.simpleTree(x, y, z, kind, trunk, new Rnd(seed));
      }
      WildTrees.Kind k = switch (kind) {
         case "fir" -> WildTrees.Kind.FIR;
         case "aspen" -> WildTrees.Kind.ASPEN;
         case "birch" -> WildTrees.Kind.BIRCH;
         case "maple" -> WildTrees.Kind.MAPLE;
         default -> WildTrees.Kind.PINE;
      };
      List<Plan.Block> out = new ArrayList<>();
      try {
         WildTrees.Plan plan = WildTrees.of(k, RandomSource.create(seed), 0.8F);
         BlockState trunkLog = null;
         for (Map.Entry<BlockPos, BlockState> e : plan.wood().entrySet()) {
            BlockPos p = e.getKey();
            if (p.getX() == 0 && p.getZ() == 0 && trunkLog == null) {
               trunkLog = e.getValue();
            }
            if (p.getY() >= 0) {
               out.add(new Plan.Block(x + p.getX(), y + p.getY(), z + p.getZ(), BlockStateParser.serialize(e.getValue())));
            }
         }
         for (Map.Entry<BlockPos, BlockState> e : plan.leaves().entrySet()) {
            BlockState s = e.getValue();
            if (s.hasProperty(LeavesBlock.PERSISTENT)) {
               s = s.setValue(LeavesBlock.PERSISTENT, true);
            }
            BlockPos p = e.getKey();
            out.add(new Plan.Block(x + p.getX(), y + p.getY(), z + p.getZ(), BlockStateParser.serialize(s)));
         }
         if (trunkLog != null) {
            if (trunkLog.hasProperty(BlockStateProperties.AXIS)) {
               trunkLog = trunkLog.setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
            }
            String log = BlockStateParser.serialize(trunkLog);
            for (int i = 0; i < trunk; i++) {
               out.add(new Plan.Block(x, y + i, z, log));
            }
         }
         return out;
      } catch (RuntimeException ex) {
         return Kit.simpleTree(x, y, z, kind, trunk, new Rnd(seed));
      }
   }
}
