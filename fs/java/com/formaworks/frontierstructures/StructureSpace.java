package com.formaworks.frontierstructures;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** Empty, player-replaceable room volume. Trees cannot regrow through authored headroom. */
public final class StructureSpace extends Block {
    public static final MapCodec<StructureSpace> CODEC=simpleCodec(StructureSpace::new);
    public StructureSpace(Properties p){super(p);}
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override protected RenderShape getRenderShape(BlockState s){return RenderShape.INVISIBLE;}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.empty();}
    @Override protected float getShadeBrightness(BlockState s,BlockGetter l,BlockPos p){return 1;}
    @Override protected boolean propagatesSkylightDown(BlockState s,BlockGetter l,BlockPos p){return true;}
}
