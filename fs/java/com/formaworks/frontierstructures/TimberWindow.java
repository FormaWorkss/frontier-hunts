package com.formaworks.frontierstructures;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.*;

/** Static framed glass. Stacking matching windows removes the doubled horizontal outer frame. */
public final class TimberWindow extends HorizontalDirectionalBlock {
    public static final MapCodec<TimberWindow> CODEC=simpleCodec(TimberWindow::new);
    public static final BooleanProperty ABOVE=BooleanProperty.create("joined_above"),BELOW=BooleanProperty.create("joined_below");
    private static final VoxelShape Z=Block.box(0,0,5,16,16,11),X=Block.box(5,0,0,11,16,16);
    public TimberWindow(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ABOVE,false).setValue(BELOW,false));}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,ABOVE,BELOW);}
    private boolean joins(BlockState s,BlockState other){return other.is(this)&&other.getValue(FACING)==s.getValue(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){
        var s=defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());
        return s.setValue(ABOVE,joins(s,c.getLevel().getBlockState(c.getClickedPos().above()))).setValue(BELOW,joins(s,c.getLevel().getBlockState(c.getClickedPos().below())));
    }
    @Override protected BlockState updateShape(BlockState s,Direction d,BlockState neighbor,LevelAccessor l,BlockPos p,BlockPos q){
        return d==Direction.UP?s.setValue(ABOVE,joins(s,neighbor)):d==Direction.DOWN?s.setValue(BELOW,joins(s,neighbor)):s;
    }
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return s.getValue(FACING).getAxis()==Direction.Axis.Z?Z:X;}
    @Override protected boolean propagatesSkylightDown(BlockState s,BlockGetter l,BlockPos p){return true;}
    @Override protected int getLightBlock(BlockState s,BlockGetter l,BlockPos p){return 0;}
}
