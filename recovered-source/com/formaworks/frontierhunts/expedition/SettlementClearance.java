package com.formaworks.frontierhunts.expedition;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class SettlementClearance {
   public static boolean authored(String template) {
      return template.matches(
         "frontierhunts:expedition/(cabin_[1-5]|outpost_[1-3]|ranger_tower_[1-3]|shared_lodge|hunting_village_[12]|hunting_camp|settlement_(lodge|outfitter|trapper|homestead|lookout|smokehouse|fishing|hamlet))"
      );
   }

   public static List<BlockPos> footprint(StructureTemplate template) {
      CompoundTag data = template.save(new CompoundTag());
      ListTag palette = data.getList("palette", 10);
      Set<BlockPos> columns = new LinkedHashSet<>();

      for (Tag entry : data.getList("blocks", 10)) {
         CompoundTag block = (CompoundTag)entry;
         int state = block.getInt("state");
         String name = palette.getCompound(state).getString("Name");
         if (!name.equals("minecraft:air") && !name.equals("minecraft:structure_void") && !name.equals("minecraft:structure_block")) {
            ListTag pos = block.getList("pos", 3);
            columns.add(new BlockPos(pos.getInt(0), 2, pos.getInt(2)));
         }
      }

      return List.copyOf(columns);
   }

   public static void clear(WorldGenLevel level, List<BlockPos> columns, BlockPos origin, StructurePlaceSettings settings, BoundingBox chunk) {
      for (BlockPos local : columns) {
         BlockPos base = origin.offset(StructureTemplate.calculateRelativePosition(settings, local));
         if (chunk.isInside(base)) {
            for (int y = base.getY(); y < Math.min(base.getY() + 64, level.getMaxBuildHeight()); y++) {
               BlockPos at = new BlockPos(base.getX(), y, base.getZ());
               BlockState state = level.getBlockState(at);
               if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                  level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
               }
            }
         }
      }
   }

   private SettlementClearance() {
   }
}
