package com.formaworks.frontierhunts.ecology.bones;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/** [ecology] Ribs, a run of vertebrae or leg bones, half sunk in the ground. Breaking it gives a few bones. */
public final class ScatteredBonesBlock extends BoneBlock {
   public enum Part implements StringRepresentable {
      RIBS("ribs"), SPINE("spine"), LEGS("legs");

      private final String name;

      Part(String name) {
         this.name = name;
      }

      @Override
      public String getSerializedName() {
         return this.name;
      }
   }

   public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
   private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 4.0, 15.0);

   public ScatteredBonesBlock(Properties p) {
      super(p);
      this.registerDefaultState(this.defaults(this.stateDefinition.any()).setValue(PART, Part.RIBS));
   }

   @Override
   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return simpleCodec(ScatteredBonesBlock::new);
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      super.createBlockStateDefinition(b);
      b.add(PART);
   }

   @Override
   protected VoxelShape outline() {
      return SHAPE;
   }

   @Override
   protected String reading(BlockState s) {
      String what = switch (s.getValue(PART)) {
         case RIBS -> "A rib cage, half sunk in the ground";
         case SPINE -> "A run of vertebrae, still in line";
         case LEGS -> "Leg bones, scattered and gnawed at the ends";
      };
      return what + " · " + age(s);
   }
}
