package com.formaworks.frontierhunts.landscape.tent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayer.RespawnPosAngle;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CompactTent extends HorizontalDirectionalBlock {
   public static final IntegerProperty PART = IntegerProperty.create("part", 0, 34);
   public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
   public static final MapCodec<CompactTent> CODEC = RecordCodecBuilder.mapCodec(
      var0 -> var0.group(Codec.STRING.fieldOf("design").forGetter(var0x -> var0x.design), propertiesCodec()).apply(var0, CompactTent::new)
   );
   private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
   public final String design;
   public final CompactTent.Design shape;

   public CompactTent(String var1, Properties var2) {
      this(CompactTent.Design.load(var1), var2);
   }

   private CompactTent(CompactTent.Design var1, Properties var2) {
      super(var2.forceSolidOn().lightLevel(var1x -> var1x.getValue(PART) < var1.cells().size() ? var1.lights().getOrDefault(var1x.getValue(PART), 0) : 0));
      this.design = var1.id();
      this.shape = var1;
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH)).setValue(PART, 0))
            .setValue(OPEN, false)
      );
   }

   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{FACING, PART, OPEN});
   }

   public BlockPos offset(int var1, Direction var2) {
      BlockPos var3 = this.shape.cells().get(var1);

      return switch (var2) {
         case EAST -> new BlockPos(-var3.getZ(), var3.getY(), var3.getX());
         case SOUTH -> new BlockPos(-var3.getX(), var3.getY(), -var3.getZ());
         case WEST -> new BlockPos(var3.getZ(), var3.getY(), -var3.getX());
         default -> var3;
      };
   }

   boolean real(BlockState var1) {
      return var1.is(this) && (Integer)var1.getValue(PART) < this.shape.cells().size();
   }

   public BlockPos origin(BlockPos var1, BlockState var2) {
      return var1.subtract(this.offset((Integer)var2.getValue(PART), (Direction)var2.getValue(FACING)));
   }

   static VoxelShape rotate(VoxelShape var0, Direction var1) {
      if (var1 == Direction.NORTH) {
         return var0;
      } else {
         ArrayList var2 = new ArrayList();
         var0.forAllBoxes((var2x, var4, var6, var8, var10, var12) -> {
            var2.add(switch (var1) {
               case EAST -> Shapes.box(1.0 - var12, var4, var2x, 1.0 - var6, var10, var8);
               case SOUTH -> Shapes.box(1.0 - var8, var4, 1.0 - var12, 1.0 - var2x, var10, 1.0 - var6);
               default -> Shapes.box(var6, var4, 1.0 - var8, var12, var10, 1.0 - var2x);
            });
         });
         return var2.stream().reduce(Shapes.empty(), Shapes::or).optimize();
      }
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      int var5 = (Integer)var1.getValue(PART);
      return var5 >= this.shape.cells().size()
         ? Shapes.empty()
         : this.shape.shapes()[var5][var1.getValue(OPEN) ? 1 : 0][((Direction)var1.getValue(FACING)).get2DDataValue()];
   }

   protected VoxelShape getOcclusionShape(BlockState var1, BlockGetter var2, BlockPos var3) {
      return Shapes.empty();
   }

   public ItemStack getCloneItemStack(LevelReader var1, BlockPos var2, BlockState var3) {
      return new ItemStack(TentContent.kit(this.design));
   }

   protected BlockState rotate(BlockState var1, Rotation var2) {
      return (BlockState)var1.setValue(FACING, var2.rotate((Direction)var1.getValue(FACING)));
   }

   protected BlockState mirror(BlockState var1, Mirror var2) {
      return this.rotate(var1, var2.getRotation((Direction)var1.getValue(FACING)));
   }

   public boolean assemble(ServerPlayer var1, BlockPos var2, Direction var3, ItemStack var4) {
      ServerLevel var5 = var1.serverLevel();
      ArrayList var6 = new ArrayList();
      ArrayList var7 = new ArrayList();

      for (int var8 = 0; var8 < this.shape.cells().size(); var8++) {
         BlockPos var9 = this.offset(var8, var3);
         var7.add(new BlockPos(var2.getX() + var9.getX(), var9.getY(), var2.getZ() + var9.getZ()));
         if (this.shape.cells().get(var8).getY() == 0) {
            var6.add(new BlockPos(var2.getX() + var9.getX(), var2.getY(), var2.getZ() + var9.getZ()));
         }
      }

      for (BlockPos var17 : this.freeCells()) {
         BlockPos var10 = rotateLocal(var17, var3);
         var7.add(new BlockPos(var2.getX() + var10.getX(), var10.getY(), var2.getZ() + var10.getZ()));
         if (var17.getY() == 0) {
            var6.add(new BlockPos(var2.getX() + var10.getX(), var2.getY(), var2.getZ() + var10.getZ()));
         }
      }

      TentPitch.Plan var16 = TentPitch.plan(var5, var6, var7, var2.getY());
      if (var16 == null) {
         boolean var22 = false;

         for (BlockPos var28 : var7) {
            BlockPos var29 = new BlockPos(var28.getX(), var2.getY() + var28.getY(), var28.getZ());
            if (var28.getY() > 0 && !var5.getBlockState(var29).canBeReplaced()) {
               var22 = true;
            }
         }

         say(var1, var22 ? "message.frontierhunts.small_tent_clear" : "message.frontierhunts.small_tent_ground");
         return false;
      } else {
         var2 = new BlockPos(var2.getX(), var16.floorY(), var2.getZ());
         if (TentPitch.occupied(var5, var16)) {
            say(var1, "message.frontierhunts.small_tent_step");
            return false;
         } else {
            for (TentPitch.Change var23 : var16.changes()) {
               BlockPos var11 = var23.pos();
               if (!var5.hasChunkAt(var11) || !var5.mayInteract(var1, var11) || !var1.mayUseItemAt(var11, Direction.UP, var4)) {
                  return false;
               }
            }

            for (int var19 = 0; var19 < this.shape.cells().size(); var19++) {
               BlockPos var24 = var2.offset(this.offset(var19, var3));
               if (!var5.hasChunkAt(var24)
                  || !var5.isInWorldBounds(var24)
                  || !var5.getWorldBorder().isWithinBounds(var24)
                  || !var5.mayInteract(var1, var24)
                  || !var1.mayUseItemAt(var24, Direction.UP, var4)) {
                  return false;
               }

               BlockState var27 = (BlockState)((BlockState)this.defaultBlockState().setValue(PART, var19)).setValue(FACING, var3);

               for (AABB var13 : this.getShape(var27, var5, var24, CollisionContext.empty()).toAabbs()) {
                  if (!var5.getEntitiesOfClass(LivingEntity.class, var13.move(var24)).isEmpty()) {
                     say(var1, "message.frontierhunts.small_tent_step");
                     return false;
                  }
               }
            }

            TentPitch.apply(var5, var16);

            for (int var20 = 0; var20 < this.shape.cells().size(); var20++) {
               BlockPos var25 = var2.offset(this.offset(var20, var3));
               var5.setBlock(var25, (BlockState)((BlockState)this.defaultBlockState().setValue(PART, var20)).setValue(FACING, var3), 18);
            }

            for (int var21 = 0; var21 < this.shape.cells().size(); var21++) {
               var5.updateNeighborsAt(var2.offset(this.offset(var21, var3)), this);
            }

            var5.playSound(null, var2, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.9F, 0.85F);
            if (!var16.changes().isEmpty()) {
               var5.playSound(null, var2, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 0.6F, 0.9F);
            }

            return true;
         }
      }
   }

   private static void say(Player var0, String var1) {
      var0.displayClientMessage(Component.translatable(var1), true);
   }

   private boolean complete(Level var1, BlockPos var2, Direction var3) {
      for (int var4 = 0; var4 < this.shape.cells().size(); var4++) {
         BlockPos var5 = var2.offset(this.offset(var4, var3));
         if (!var1.hasChunkAt(var5)) {
            return true;
         }

         BlockState var6 = var1.getBlockState(var5);
         if (!var6.is(this) || (Integer)var6.getValue(PART) != var4 || var6.getValue(FACING) != var3) {
            return false;
         }
      }

      return true;
   }

   private void dismantle(Level var1, BlockPos var2, Direction var3, boolean var4) {
      if (!REMOVING.get()) {
         REMOVING.set(true);

         try {
            for (int var5 = 0; var5 < this.shape.cells().size(); var5++) {
               BlockPos var6 = var2.offset(this.offset(var5, var3));
               BlockState var7 = var1.getBlockState(var6);
               if (var1.hasChunkAt(var6) && var7.is(this) && (Integer)var7.getValue(PART) == var5 && var7.getValue(FACING) == var3) {
                  var1.setBlock(var6, Blocks.AIR.defaultBlockState(), 18);
               }
            }

            if (var4) {
               popResource(var1, var2, new ItemStack(TentContent.kit(this.design)));
            }
         } finally {
            REMOVING.set(false);
         }

         for (int var11 = 0; var11 < this.shape.cells().size(); var11++) {
            var1.updateNeighborsAt(var2.offset(this.offset(var11, var3)), Blocks.AIR);
         }
      }
   }

   public BlockState playerWillDestroy(Level var1, BlockPos var2, BlockState var3, Player var4) {
      if (!var1.isClientSide && !var4.getAbilities().instabuild && this.real(var3)) {
         this.dismantle(var1, this.origin(var2, var3), (Direction)var3.getValue(FACING), true);
      }

      return super.playerWillDestroy(var1, var2, var3, var4);
   }

   protected void onRemove(BlockState var1, Level var2, BlockPos var3, BlockState var4, boolean var5) {
      super.onRemove(var1, var2, var3, var4, var5);
      if (!var2.isClientSide && !var4.is(this) && !REMOVING.get() && this.real(var1)) {
         this.dismantle(var2, this.origin(var3, var1), (Direction)var1.getValue(FACING), false);
      }
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      if (!var4.isClientSide()
         && var2 == Direction.DOWN
         && (Integer)var1.getValue(PART) < this.shape.cells().size()
         && this.shape.cells().get((Integer)var1.getValue(PART)).getY() == 0) {
         var4.scheduleTick(var5, this, 2);
      }

      return var1;
   }

   protected void tick(BlockState var1, ServerLevel var2, BlockPos var3, RandomSource var4) {
      if (this.real(var1)) {
         BlockPos var5 = this.origin(var3, var1);
         Direction var6 = (Direction)var1.getValue(FACING);
         if (var2.getBlockState(var3.below()).isFaceSturdy(var2, var3.below(), Direction.UP) && this.complete(var2, var5, var6)) {
            ShelterSeal.clear(var2, this.freeCells().stream().map(var2x -> var5.offset(rotateLocal(var2x, var6))).toList());
            var2.scheduleTick(var3, this, 20);
         } else {
            this.dismantle(var2, var5, var6, true);
         }
      }
   }

   protected InteractionResult useWithoutItem(BlockState var1, Level var2, BlockPos var3, Player var4, BlockHitResult var5) {
      if (var2.isClientSide) {
         return InteractionResult.SUCCESS;
      } else if (!(var4 instanceof ServerPlayer var6)) {
         return InteractionResult.CONSUME;
      } else {
         int var7 = (Integer)var1.getValue(PART);
         if (var7 >= this.shape.cells().size()) {
            return InteractionResult.CONSUME;
         } else {
            BlockPos var8 = this.origin(var3, var1);
            Direction var9 = (Direction)var1.getValue(FACING);
            if (!this.complete(var2, var8, var9)) {
               this.dismantle(var2, var8, var9, true);
               return InteractionResult.CONSUME;
            } else if (!var4.isShiftKeyDown()) {
               if (this.shape.doorParts().contains(var7)) {
                  boolean var15 = !(Boolean)var1.getValue(OPEN);
                  if (!var15 && this.doorOccupied(var2, var8, var9)) {
                     say(var6, "message.frontierhunts.small_tent_doorway");
                     return InteractionResult.CONSUME;
                  } else {
                     for (int var16 = 0; var16 < this.shape.cells().size(); var16++) {
                        BlockPos var17 = var8.offset(this.offset(var16, var9));
                        BlockState var18 = var2.getBlockState(var17);
                        if (var18.is(this)) {
                           var2.setBlock(var17, (BlockState)var18.setValue(OPEN, var15), 18);
                        }
                     }

                     var2.playSound(null, var3, SoundEvents.WOOL_HIT, SoundSource.BLOCKS, 0.5F, var15 ? 1.25F : 1.05F);
                     return InteractionResult.CONSUME;
                  }
               } else {
                  BlockPos var14 = var8.offset(this.offset(this.shape.bed(), var9));
                  var6.startSleepInBed(var14).ifLeft(var1x -> {
                     if (var1x.getMessage() != null) {
                        var6.displayClientMessage(var1x.getMessage(), true);
                     }
                  });
                  if (var6.isSleeping()) {
                     TentContent.sleptIn(var6, this, var8, var9);
                  }

                  return InteractionResult.CONSUME;
               }
            } else {
               for (int var10 = 0; var10 < this.shape.cells().size(); var10++) {
                  BlockPos var11 = var8.offset(this.offset(var10, var9));

                  for (LivingEntity var13 : var2.getEntitiesOfClass(LivingEntity.class, new AABB(var11))) {
                     if (var13 != var4 || var13.isSleeping()) {
                        say(var6, "message.frontierhunts.small_tent_occupied");
                        return InteractionResult.CONSUME;
                     }
                  }
               }

               if (this.hasFurnishing(var2, var8, var9)) {
                  say(var6, "message.frontierhunts.small_tent_furnished");
                  return InteractionResult.CONSUME;
               } else {
                  this.dismantle(var2, var8, var9, true);
                  var2.playSound(null, var3, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.8F, 0.9F);
                  return InteractionResult.CONSUME;
               }
            }
         }
      }
   }

   private boolean hasFurnishing(Level var1, BlockPos var2, Direction var3) {
      for (BlockPos var5 : this.freeCells()) {
         BlockPos var6 = var2.offset(rotateLocal(var5, var3));
         if (!var1.getBlockState(var6).isAir() && !var1.getBlockState(var6).canBeReplaced()) {
            return true;
         }
      }

      return false;
   }

   List<BlockPos> freeCells() {
      int var1 = Integer.MAX_VALUE;
      int var2 = Integer.MIN_VALUE;
      int var3 = Integer.MAX_VALUE;
      int var4 = Integer.MIN_VALUE;
      int var5 = 0;
      HashSet var6 = new HashSet<>(this.shape.cells());

      for (BlockPos var8 : this.shape.cells()) {
         var1 = Math.min(var1, var8.getX());
         var2 = Math.max(var2, var8.getX());
         var3 = Math.min(var3, var8.getZ());
         var4 = Math.max(var4, var8.getZ());
         var5 = Math.max(var5, var8.getY());
      }

      ArrayList var12 = new ArrayList();

      for (int var13 = 0; var13 <= var5; var13++) {
         for (int var9 = var3; var9 <= var4; var9++) {
            for (int var10 = var1; var10 <= var2; var10++) {
               BlockPos var11 = new BlockPos(var10, var13, var9);
               if (!var6.contains(var11)) {
                  var12.add(var11);
               }
            }
         }
      }

      return var12;
   }

   static BlockPos rotateLocal(BlockPos var0, Direction var1) {
      return switch (var1) {
         case EAST -> new BlockPos(-var0.getZ(), var0.getY(), var0.getX());
         case SOUTH -> new BlockPos(-var0.getX(), var0.getY(), -var0.getZ());
         case WEST -> new BlockPos(var0.getZ(), var0.getY(), -var0.getX());
         default -> var0;
      };
   }

   private boolean doorOccupied(Level var1, BlockPos var2, Direction var3) {
      for (int var5 : this.shape.doorParts()) {
         BlockPos var6 = var2.offset(this.offset(var5, var3));
         VoxelShape var7 = this.shape.shapes()[var5][0][var3.get2DDataValue()];

         for (AABB var9 : var7.toAabbs()) {
            if (!var1.getEntitiesOfClass(LivingEntity.class, var9.move(var6)).isEmpty()) {
               return true;
            }
         }
      }

      return false;
   }

   public boolean isBed(BlockState var1, BlockGetter var2, BlockPos var3, LivingEntity var4) {
      return (Integer)var1.getValue(PART) == this.shape.bed();
   }

   public Direction getBedDirection(BlockState var1, LevelReader var2, BlockPos var3) {
      Direction var4 = (Direction)var1.getValue(FACING);
      String var5 = this.shape.bedDir();

      return switch (var5) {
         case "front" -> var4;
         case "right" -> var4.getClockWise();
         case "left" -> var4.getCounterClockWise();
         default -> var4.getOpposite();
      };
   }

   public Vec3 sleepOffset(Direction var1) {
      double[] var2 = this.shape.sleep();
      double var3 = var2[0];
      double var5 = var2[2];

      return switch (var1) {
         case EAST -> new Vec3(-var5, var2[1], var3);
         case SOUTH -> new Vec3(-var3, var2[1], -var5);
         case WEST -> new Vec3(var5, var2[1], -var3);
         default -> new Vec3(var3, var2[1], var5);
      };
   }

   public void setBedOccupied(BlockState var1, Level var2, BlockPos var3, LivingEntity var4, boolean var5) {
   }

   public Optional<RespawnPosAngle> getRespawnPosition(BlockState var1, EntityType<?> var2, LevelReader var3, BlockPos var4, float var5) {
      if (var3 instanceof Level var6 && BedBlock.canSetSpawn(var6) && this.real(var1)) {
         BlockPos var7 = this.origin(var4, var1);
         Direction var8 = (Direction)var1.getValue(FACING);
         BlockPos var9 = !this.shape.sleepOnly() && !this.shape.crouch() ? var7.offset(rotateLocal(new BlockPos(0, 0, 1), var8)) : var7.relative(var8);
         Vec3 var10 = Vec3.atBottomCenterOf(var9);
         if (var6.noCollision(EntityType.PLAYER.getDimensions().makeBoundingBox(var10))
            && var6.getFluidState(var9).isEmpty()
            && var6.getBlockState(var9.below()).isFaceSturdy(var6, var9.below(), Direction.UP)) {
            return Optional.of(RespawnPosAngle.of(var10, var4));
         }

         return BedBlock.findStandUpPosition(var2, var3, var4, (Direction)var1.getValue(FACING), var5).map(var1x -> RespawnPosAngle.of(var1x, var4));
      }

      return Optional.empty();
   }

   Vec3 standUp(BlockPos var1, Direction var2) {
      BlockPos var3 = !this.shape.sleepOnly() && !this.shape.crouch() ? var1.offset(rotateLocal(new BlockPos(0, 0, 1), var2)) : var1.relative(var2);
      return Vec3.atBottomCenterOf(var3);
   }

   public static record Design(
      String id,
      List<BlockPos> cells,
      VoxelShape[][][] shapes,
      Set<Integer> doorParts,
      int bed,
      Map<Integer, Integer> lights,
      boolean sleepOnly,
      boolean crouch,
      double[] sleep,
      String bedDir
   ) {
      public static CompactTent.Design load(String var0) {
         try {
            CompactTent.Design var31;
            try (InputStream var1 = CompactTent.class.getResourceAsStream("/data/frontierhunts/compact_tents/" + var0 + ".json")) {
               if (var1 == null) {
                  throw new IllegalStateException("Missing compact tent contract " + var0);
               }

               JsonObject var2 = JsonParser.parseReader(new InputStreamReader(var1, StandardCharsets.UTF_8)).getAsJsonObject();
               ArrayList var3 = new ArrayList();

               for (JsonElement var5 : var2.getAsJsonArray("cells")) {
                  JsonArray var6 = var5.getAsJsonArray();
                  var3.add(new BlockPos(var6.get(0).getAsInt(), var6.get(1).getAsInt(), var6.get(2).getAsInt()));
               }

               JsonArray var16 = var2.getAsJsonArray("shapes");
               VoxelShape[][][] var17 = new VoxelShape[var3.size()][2][4];

               for (int var18 = 0; var18 < var3.size(); var18++) {
                  VoxelShape var7 = Shapes.empty();
                  VoxelShape var8 = Shapes.empty();

                  for (JsonElement var10 : var16.get(var18).getAsJsonArray()) {
                     JsonArray var11 = var10.getAsJsonArray();
                     VoxelShape var12 = Shapes.box(
                        var11.get(0).getAsDouble(),
                        var11.get(1).getAsDouble(),
                        var11.get(2).getAsDouble(),
                        var11.get(3).getAsDouble(),
                        var11.get(4).getAsDouble(),
                        var11.get(5).getAsDouble()
                     );
                     if (var11.get(6).getAsInt() == 1) {
                        var8 = Shapes.or(var8, var12);
                     } else {
                        var7 = Shapes.or(var7, var12);
                     }
                  }

                  VoxelShape var25 = Shapes.or(var7, var8).optimize();
                  VoxelShape var29 = var7.optimize();

                  for (Direction var33 : Plane.HORIZONTAL) {
                     var17[var18][0][var33.get2DDataValue()] = CompactTent.rotate(var25, var33);
                     var17[var18][1][var33.get2DDataValue()] = CompactTent.rotate(var29, var33);
                  }
               }

               HashSet var19 = new HashSet();

               for (JsonElement var22 : var2.getAsJsonArray("door_parts")) {
                  var19.add(var22.getAsInt());
               }

               HashMap var21 = new HashMap();
               if (var2.has("lights")) {
                  for (Entry var26 : var2.getAsJsonObject("lights").entrySet()) {
                     var21.put(Integer.parseInt((String)var26.getKey()), ((JsonElement)var26.getValue()).getAsInt());
                  }
               }

               double[] var24 = new double[3];
               if (var2.has("sleep")) {
                  JsonArray var27 = var2.getAsJsonArray("sleep");

                  for (int var30 = 0; var30 < 3 && var30 < var27.size(); var30++) {
                     var24[var30] = var27.get(var30).getAsDouble();
                  }
               }

               String var28 = var2.has("bed_dir") ? var2.get("bed_dir").getAsString() : "back";
               var31 = new CompactTent.Design(
                  var0,
                  List.copyOf(var3),
                  var17,
                  Set.copyOf(var19),
                  var2.get("bed").getAsInt(),
                  Map.copyOf(var21),
                  var2.has("sleep_only") && var2.get("sleep_only").getAsBoolean(),
                  var2.has("crouch") && var2.get("crouch").getAsBoolean(),
                  var24,
                  var28
               );
            }

            return var31;
         } catch (RuntimeException | IOException var15) {
            throw new IllegalStateException("Cannot load compact tent contract " + var0, var15);
         }
      }
   }

   public static final class Kit extends Item {
      public final String design;

      public Kit(String var1, net.minecraft.world.item.Item.Properties var2) {
         super(var2.stacksTo(1));
         this.design = var1;
      }

      public InteractionResult useOn(UseOnContext var1) {
         BlockState var2 = var1.getLevel().getBlockState(var1.getClickedPos());
         boolean var3 = var2.canBeReplaced();
         if (!var3 && var1.getClickedFace() != Direction.UP) {
            return InteractionResult.FAIL;
         } else if (var1.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
         } else if (var1.getPlayer() instanceof ServerPlayer var4) {
            CompactTent var7 = TentContent.block(this.design);
            BlockPos var6 = var3 ? var1.getClickedPos() : var1.getClickedPos().above();
            if (var7 != null && var7.assemble(var4, var6, var4.getDirection().getOpposite(), var1.getItemInHand())) {
               if (!var4.isCreative()) {
                  var1.getItemInHand().shrink(1);
               }

               return InteractionResult.CONSUME;
            } else {
               return InteractionResult.FAIL;
            }
         } else {
            return InteractionResult.FAIL;
         }
      }

      public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
         var3.add(Component.translatable("item.frontierhunts." + this.design + ".tip1").withStyle(ChatFormatting.GRAY));
         var3.add(Component.translatable("item.frontierhunts.small_tent.tip").withStyle(ChatFormatting.DARK_GRAY));
      }
   }
}
