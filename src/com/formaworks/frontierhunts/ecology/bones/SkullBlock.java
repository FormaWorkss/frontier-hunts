package com.formaworks.frontierhunts.ecology.bones;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/** [ecology] A weathered skull: whitetail, elk or moose (antlers on or off) or bison (horns always on). */
public final class SkullBlock extends BoneBlock {
   public static final BooleanProperty ANTLERS = BooleanProperty.create("antlers");
   private static final VoxelShape SMALL = Block.box(3.0, 0.0, 3.0, 13.0, 6.0, 13.0);
   private static final VoxelShape RACK = Block.box(1.0, 0.0, 1.0, 15.0, 11.0, 15.0);
   private static final VoxelShape WIDE = Block.box(0.0, 0.0, 1.0, 16.0, 8.0, 15.0);
   public final BoneSpecies species;

   public SkullBlock(BoneSpecies species, Properties p) {
      super(p);
      this.species = species;
      this.registerDefaultState(this.defaults(this.stateDefinition.any()).setValue(ANTLERS, true));
   }

   @Override
   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return simpleCodec(p -> new SkullBlock(this.species, p));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      super.createBlockStateDefinition(b);
      b.add(ANTLERS);
   }

   @Override
   protected VoxelShape outline() {
      return this.species == BoneSpecies.BISON ? WIDE : this.species == BoneSpecies.WHITETAIL ? SMALL : RACK;
   }

   @Override
   protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos,
      net.minecraft.world.phys.shapes.CollisionContext ctx) {
      if (this.species == BoneSpecies.BISON) {
         return WIDE;
      }
      return state.getValue(ANTLERS) ? RACK : SMALL;
   }

   @Override
   protected String reading(BlockState s) {
      String what = switch (this.species) {
         case WHITETAIL -> s.getValue(ANTLERS) ? "Whitetail buck skull, the rack still on" : "Whitetail skull, a doe or a young deer";
         case ELK -> s.getValue(ANTLERS) ? "Bull elk skull, antlers still attached" : "Elk skull, a cow or a calf";
         case MOOSE -> s.getValue(ANTLERS) ? "Bull moose skull with its palms" : "Moose skull, a cow";
         case BISON -> "Bison skull, horns still on";
      };
      String tip = this.species != BoneSpecies.BISON && !s.getValue(ANTLERS) ? "" : " · break it to take it";
      return what + " · " + age(s) + tip;
   }
}
