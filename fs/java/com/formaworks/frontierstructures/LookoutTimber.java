package com.formaworks.frontierstructures;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;

/** Static, server-collidable timber kit. No block entities, tickers or custom renderer. */
public final class LookoutTimber extends HorizontalDirectionalBlock {
    private static final MapCodec<LookoutTimber> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
        Codec.STRING.fieldOf("timber").forGetter(b->b.kind),propertiesCodec()).apply(i,LookoutTimber::new));
    private final String kind;
    private final VoxelShape[] shapes=new VoxelShape[4];
    LookoutTimber(String kind,Properties properties){
        super(properties);this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));
        for(Direction d:Direction.Plane.HORIZONTAL)shapes[d.get2DDataValue()]=shape(kind,d);
    }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shapes[state.getValue(FACING).get2DDataValue()];}
    private static VoxelShape shape(String kind,Direction d){
        boolean z=d.getAxis()==Direction.Axis.Z,reverse=d==Direction.NORTH||d==Direction.WEST;
        if(kind.equals("lookout_deck_rail"))return z?Block.box(6,0,0,10,24,16):Block.box(0,0,6,16,24,10);
        VoxelShape shape=Shapes.empty();
        for(int i=0;i<16;i++){
            double h=reverse?15-i:i;
            double low=kind.equals("lookout_stair_rail")?Math.max(0,h-2):Math.max(0,h-2);
            double high=kind.equals("lookout_stair_rail")?h+18:Math.min(16,h+3);
            shape=Shapes.or(shape,Block.box(z?6:i,low,z?i:6,z?10:i+1,high,z?i+1:10));
            if(kind.equals("lookout_cross_brace"))shape=Shapes.or(shape,Block.box(z?6:i,Math.max(0,13-h),z?i:6,z?10:i+1,Math.min(16,18-h),z?i+1:10));
        }
        return shape;
    }
}
