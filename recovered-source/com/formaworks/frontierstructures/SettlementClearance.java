package com.formaworks.frontierstructures;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Clears vegetation only above authored occupied columns, before placing the building. */
public final class SettlementClearance {
    public record Footing(BlockPos local,boolean stone) {}
    public static List<Footing> footings(StructureTemplate template){
        var tag=template.save(new CompoundTag());var palette=tag.getList("palette",10);var lowest=new HashMap<Long,Footing>();
        var ground=Set.of("minecraft:dirt","minecraft:grass_block","minecraft:coarse_dirt","minecraft:gravel","minecraft:clay","minecraft:mud","minecraft:farmland","minecraft:cobblestone","minecraft:mossy_cobblestone","minecraft:andesite","minecraft:stone_bricks");
        for(var entry:tag.getList("blocks",10)){
            var b=(CompoundTag)entry;var p=b.getList("pos",3);int y=p.getInt(1);if(y>1)continue;
            String name=palette.getCompound(b.getInt("state")).getString("Name");if(!ground.contains(name))continue;
            var pos=new BlockPos(p.getInt(0),y,p.getInt(2));long key=new BlockPos(pos.getX(),0,pos.getZ()).asLong();
            var prior=lowest.get(key);if(prior==null||y<prior.local.getY())lowest.put(key,new Footing(pos,name.contains("stone")||name.contains("andesite")||name.contains("bricks")));
        }
        return List.copyOf(lowest.values());
    }
    /** Close terrain hollows beneath authored ground contact; never alters the authored building. */
    public static void support(WorldGenLevel level,List<Footing> footings,BlockPos origin,StructurePlaceSettings settings,BoundingBox chunk){
        for(var footing:footings){
            var base=origin.offset(StructureTemplate.calculateRelativePosition(settings,footing.local));if(!chunk.isInside(base))continue;
            for(int depth=1;depth<=32&&base.getY()-depth>level.getMinBuildHeight();depth++){
                var at=base.below(depth);var existing=level.getBlockState(at);
                if(existing.isSolid()&&!existing.is(BlockTags.LEAVES)&&!existing.is(BlockTags.LOGS))break;
                level.setBlock(at,(footing.stone?Blocks.COBBLESTONE:Blocks.DIRT).defaultBlockState(),2);
            }
        }
    }
    public static boolean authored(String template){return template.matches("frontierstructures:expedition/(cabin_[1-5]|outpost_[1-3]|ranger_tower_[1-3]|shared_lodge|hunting_village_[12]|hunting_camp|settlement_(lodge|outfitter|trapper|homestead|lookout|smokehouse|fishing|hamlet))");}
    public static List<BlockPos> footprint(StructureTemplate template) {
        var data=template.save(new CompoundTag());var palette=data.getList("palette",10);
        Set<BlockPos> columns=new LinkedHashSet<>();
        for(var entry:data.getList("blocks",10)){
            var block=(CompoundTag)entry;int state=block.getInt("state");
            String name=palette.getCompound(state).getString("Name");
            if(name.equals("minecraft:air")||name.equals("minecraft:structure_void")||name.equals("minecraft:structure_block"))continue;
            var pos=block.getList("pos",3);columns.add(new BlockPos(pos.getInt(0),2,pos.getInt(2)));
        }
        return List.copyOf(columns);
    }

    public static void clear(WorldGenLevel level,List<BlockPos> columns,BlockPos origin,StructurePlaceSettings settings,BoundingBox chunk) {
        for(var local:columns){
            BlockPos base=origin.offset(StructureTemplate.calculateRelativePosition(settings,local));
            if(!chunk.isInside(base))continue;
            for(int y=base.getY();y<Math.min(base.getY()+64,level.getMaxBuildHeight());y++){
                BlockPos at=new BlockPos(base.getX(),y,base.getZ());var state=level.getBlockState(at);
                // Restrict clearing to tree tags; stone, soil, planks and fluids stay intact.
                if(state.is(BlockTags.LOGS)||state.is(BlockTags.LEAVES))level.setBlock(at,Blocks.AIR.defaultBlockState(),2);
            }
        }
    }
    private SettlementClearance(){}
}
