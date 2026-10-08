package com.formaworks.frontierhunts.camp;

import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.ExpeditionService;
import com.formaworks.frontierhunts.expedition.TreeStandSeat;
import com.formaworks.frontierhunts.landscape.tent.ShelterSeal;
import com.formaworks.frontierhunts.landscape.tent.TentPitch;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CampingTent extends HorizontalDirectionalBlock implements EntityBlock {
   public static final MapCodec<CampingTent> CODEC = RecordCodecBuilder.mapCodec(
      var0 -> var0.group(Codec.STRING.fieldOf("design").forGetter(var0x -> var0x.design), propertiesCodec()).apply(var0, CampingTent::new)
   );
   public static final IntegerProperty PART = IntegerProperty.create("part", 0, 56);
   public static final BooleanProperty READY = BooleanProperty.create("ready");
   public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
   public static final List<BlockPos> CELLS = cells();
   private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
   public final boolean dome;
   public final String design;
   private final VoxelShape[][][] shapes = new VoxelShape[57][16][4];

   public CampingTent(boolean var1, Properties var2) {
      this(var1 ? "trail_dome_tent" : "woodland_camp_tent", var2);
   }

   public CampingTent(String var1, Properties var2) {
      super(var2.dynamicShape().forceSolidOn());
      this.design = var1;
      this.dome = var1.contains("dome");
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH)).setValue(PART, 0))
               .setValue(READY, false))
            .setValue(OPEN, true)
      );

      try {
         try (InputStream var3 = CampingTent.class.getResourceAsStream("/data/frontierhunts/tents/" + this.id() + ".json")) {
            if (var3 == null) {
               throw new IllegalStateException("Missing tent shape contract");
            }

            JsonArray var4 = JsonParser.parseReader(new InputStreamReader(var3, StandardCharsets.UTF_8)).getAsJsonArray();

            for (int var5 = 0; var5 < 57; var5++) {
               int var6 = 0;

               for (JsonElement var8 : var4.get(var5).getAsJsonArray()) {
                  JsonArray var9 = var8.getAsJsonArray();
                  int var10 = var9.size() == 7 ? var9.get(6).getAsInt() : 0;
                  if (var10 > 0) {
                     var6 |= 1 << var10 - 1;
                  }
               }

               for (int var17 = 0; var17 < 16; var17++) {
                  int var18 = var17 & var6;
                  if (var18 != var17) {
                     this.shapes[var5][var17] = this.shapes[var5][var18];
                  } else {
                     VoxelShape var19 = Shapes.empty();

                     for (JsonElement var11 : var4.get(var5).getAsJsonArray()) {
                        JsonArray var12 = var11.getAsJsonArray();
                        int var13 = var12.size() == 7 ? var12.get(6).getAsInt() : 0;
                        if (var13 <= 0 || (var17 & 1 << var13 - 1) == 0) {
                           var19 = Shapes.or(
                              var19,
                              Shapes.box(
                                 var12.get(0).getAsDouble(),
                                 var12.get(1).getAsDouble(),
                                 var12.get(2).getAsDouble(),
                                 var12.get(3).getAsDouble(),
                                 var12.get(4).getAsDouble(),
                                 var12.get(5).getAsDouble()
                              )
                           );
                        }
                     }

                     for (Direction var22 : Plane.HORIZONTAL) {
                        this.shapes[var5][var17][var22.get2DDataValue()] = rotateShape(var19, var22);
                     }
                  }
               }
            }
         }
      } catch (IOException var16) {
         throw new IllegalStateException("Cannot load tent shape contract", var16);
      }
   }

   public String id() {
      return this.design;
   }

   private static List<BlockPos> cells() {
      ArrayList var0 = new ArrayList();

      for (int var1 = 0; var1 < 3; var1++) {
         for (int var2 = -2; var2 <= 2; var2++) {
            for (int var3 = -2; var3 <= 2; var3++) {
               if (var1 == 2 || Math.abs(var3) == 2 || Math.abs(var2) == 2) {
                  var0.add(new BlockPos(var3, var1, var2));
               }
            }
         }
      }

      return List.copyOf(var0);
   }

   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{FACING, PART, READY, OPEN});
   }

   public static BlockPos offset(int var0, Direction var1) {
      BlockPos var2 = CELLS.get(var0);

      return switch (var1) {
         case EAST -> new BlockPos(-var2.getZ(), var2.getY(), var2.getX());
         case SOUTH -> new BlockPos(-var2.getX(), var2.getY(), -var2.getZ());
         case WEST -> new BlockPos(var2.getZ(), var2.getY(), -var2.getX());
         default -> var2;
      };
   }

   public static BlockPos origin(BlockPos var0, BlockState var1) {
      return var0.subtract(offset((Integer)var1.getValue(PART), (Direction)var1.getValue(FACING)));
   }

   public static BlockPos anchor(BlockPos var0, Direction var1) {
      return var0.offset(offset(0, var1));
   }

   private boolean matches(BlockState var1, int var2, Direction var3) {
      return var1.is(this) && (Integer)var1.getValue(PART) == var2 && var1.getValue(FACING) == var3;
   }

   public BlockEntity newBlockEntity(BlockPos var1, BlockState var2) {
      return var2.getValue(PART) == 0 ? new CampingTent.Anchor(var1, var2) : null;
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      int var5 = var1.getValue(OPEN) ? 1 : 0;
      if (var2.getBlockEntity(anchor(origin(var3, var1), (Direction)var1.getValue(FACING))) instanceof CampingTent.Anchor var6) {
         var5 |= var6.windows << 1;
      }

      return this.shapes[var1.getValue(PART)][var5][((Direction)var1.getValue(FACING)).get2DDataValue()];
   }

   protected VoxelShape getOcclusionShape(BlockState var1, BlockGetter var2, BlockPos var3) {
      return Shapes.empty();
   }

   protected BlockState rotate(BlockState var1, Rotation var2) {
      return (BlockState)var1.setValue(FACING, var2.rotate((Direction)var1.getValue(FACING)));
   }

   protected BlockState mirror(BlockState var1, Mirror var2) {
      return this.rotate(var1, var2.getRotation((Direction)var1.getValue(FACING)));
   }

   public boolean assemble(ServerPlayer var1, BlockPos var2, Direction var3, ItemStack var4) {
      ServerLevel var5 = var1.serverLevel();
      if (!var3.getAxis().isHorizontal()) {
         return false;
      } else {
         ArrayList var6 = new ArrayList();
         ArrayList var7 = new ArrayList();

         for (int var8 = -2; var8 <= 2; var8++) {
            for (int var9 = -2; var9 <= 2; var9++) {
               var6.add(new BlockPos(var2.getX() + var9, var2.getY(), var2.getZ() + var8));

               for (int var10 = 0; var10 <= 2; var10++) {
                  var7.add(new BlockPos(var2.getX() + var9, var10, var2.getZ() + var8));
               }
            }
         }

         TentPitch.Plan var26 = TentPitch.plan(var5, var6, var7, var2.getY());
         if (var26 != null) {
            BlockPos var30 = new BlockPos(var2.getX(), var26.floorY(), var2.getZ());
            boolean var33 = !TentPitch.occupied(var5, var26);

            for (TentPitch.Change var12 : var26.changes()) {
               if (!var5.hasChunkAt(var12.pos()) || !var5.mayInteract(var1, var12.pos()) || !var1.mayUseItemAt(var12.pos(), Direction.UP, var4)) {
                  var33 = false;
               }
            }

            for (BlockPos var37 : BlockPos.betweenClosed(var30.offset(-2, -1, -2), var30.offset(2, 2, 2))) {
               if (!var5.hasChunkAt(var37)
                  || !var5.isInWorldBounds(var37)
                  || !var5.getWorldBorder().isWithinBounds(var37)
                  || !var5.mayInteract(var1, var37)
                  || !var1.mayUseItemAt(var37, Direction.UP, var4)) {
                  var33 = false;
               }
            }

            for (int var36 = 0; var36 < CELLS.size() && var33; var36++) {
               BlockPos var38 = var30.offset(offset(var36, var3));
               BlockState var13 = (BlockState)((BlockState)this.defaultBlockState().setValue(PART, var36)).setValue(FACING, var3);

               for (AABB var15 : this.getShape(var13, var5, var38, CollisionContext.empty()).toAabbs()) {
                  if (!var5.getEntitiesOfClass(LivingEntity.class, var15.move(var38)).isEmpty()) {
                     var33 = false;
                  }
               }
            }

            if (var33) {
               TentPitch.apply(var5, var26);
               var2 = var30;
            }
         }

         for (BlockPos var22 : BlockPos.betweenClosed(var2.offset(-2, -1, -2), var2.offset(2, 2, 2))) {
            if (var5.hasChunkAt(var22)
               && var5.isInWorldBounds(var22)
               && var5.getWorldBorder().isWithinBounds(var22)
               && var5.mayInteract(var1, var22)
               && var1.mayUseItemAt(var22, Direction.UP, var4)) {
               if (var22.getY() < var2.getY()) {
                  if (!var5.getBlockState(var22).isFaceSturdy(var5, var22, Direction.UP)) {
                     ExpeditionService.message(var1, "The tent needs a level five-by-five patch of solid ground.");
                     return false;
                  }
                  continue;
               }

               if (var5.getBlockState(var22).canBeReplaced() && var5.getFluidState(var22).isEmpty()) {
                  continue;
               }

               ExpeditionService.message(var1, "Clear the tent footprint: five wide, five deep and three high; the interior is three by three.");
               return false;
            }

            return false;
         }

         for (int var20 = 0; var20 < CELLS.size(); var20++) {
            BlockPos var23 = var2.offset(offset(var20, var3));
            BlockState var27 = (BlockState)((BlockState)this.defaultBlockState().setValue(PART, var20)).setValue(FACING, var3);

            for (AABB var34 : this.getShape(var27, var5, var23, CollisionContext.empty()).toAabbs()) {
               if (!var5.getEntitiesOfClass(LivingEntity.class, var34.move(var23)).isEmpty()) {
                  ExpeditionService.message(var1, "Step clear of the tent's poles and fabric.");
                  return false;
               }
            }
         }

         ShelterSeal.clear(var5, BlockPos.betweenClosed(var2.offset(-1, 0, -1), var2.offset(1, 1, 1)));
         LinkedHashMap var21 = new LinkedHashMap();

         for (int var24 = 0; var24 < CELLS.size(); var24++) {
            BlockPos var28 = var2.offset(offset(var24, var3));
            var21.put(var28, var5.getBlockState(var28));
            if (!var5.setBlock(var28, (BlockState)((BlockState)this.defaultBlockState().setValue(PART, var24)).setValue(FACING, var3), 18)) {
               REMOVING.set(true);

               try {
                  var21.forEach((var1x, var2x) -> var5.setBlock(var1x, var2x, 18));
               } finally {
                  REMOVING.set(false);
               }

               return false;
            }
         }

         BlockPos var25 = anchor(var2, var3);
         if (var5.getBlockEntity(var25) instanceof CampingTent.Anchor var29) {
            var29.setupAt = var5.getGameTime();
            var29.sync();
         }

         var5.scheduleTick(var25, this, 60);
         var21.keySet().forEach(var2x -> var5.updateNeighborsAt(var2x, this));
         var5.playSound(null, var25, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8F, 0.8F);
         return true;
      }
   }

   protected InteractionResult useWithoutItem(BlockState var1, Level var2, BlockPos var3, Player var4, BlockHitResult var5) {
      if (var2.isClientSide) {
         return InteractionResult.SUCCESS;
      } else {
         BlockPos var6 = origin(var3, var1);
         Direction var7 = (Direction)var1.getValue(FACING);
         if (!(var2.getBlockEntity(anchor(var6, var7)) instanceof CampingTent.Anchor var8) || !(Boolean)var1.getValue(READY)) {
            return InteractionResult.CONSUME;
         }

         if (var4.isShiftKeyDown()) {
            for (BlockPos var19 : BlockPos.betweenClosed(var6.offset(-1, 0, -1), var6.offset(1, 1, 1))) {
               if (!var2.getBlockState(var19).isAir()) {
                  if (var4 instanceof ServerPlayer var11) {
                     ExpeditionService.message(var11, "Remove your furnishings before packing the tent.");
                  }

                  return InteractionResult.CONSUME;
               }
            }

            if (!var2.getEntitiesOfClass(LivingEntity.class, new AABB(Vec3.atLowerCornerOf(var6.offset(-1, 0, -1)), Vec3.atLowerCornerOf(var6.offset(2, 2, 2))))
               .isEmpty()) {
               return InteractionResult.CONSUME;
            } else {
               this.dismantle(var2, var6, var7, true);
               return InteractionResult.CONSUME;
            }
         } else {
            Vec3 var17 = TreeStandSeat.local(var6, var7, var5.getLocation());
            double var10 = var17.x - 0.5;
            double var12 = var17.z - 0.5;
            int var14 = Math.abs(var10) > Math.abs(var12) ? (var10 > 0.0 ? 1 : 3) : (var12 > 0.0 ? 2 : 0);
            if (var14 > 0 && var17.y >= 1.2 && var17.y <= 2.0) {
               this.toggleWindow(var2, var6, var7, var8, var14 - 1);
               return InteractionResult.CONSUME;
            } else if (var8.doorUntil > var2.getGameTime()) {
               return InteractionResult.CONSUME;
            } else {
               boolean var15 = !(Boolean)var1.getValue(OPEN);
               if (!var15 && this.doorOccupied(var2, var6, var7)) {
                  if (var4 instanceof ServerPlayer var16) {
                     ExpeditionService.message(var16, "Step clear of the doorway before closing it.");
                  }

                  return InteractionResult.CONSUME;
               } else {
                  var8.targetOpen = var15;
                  var8.doorAt = var2.getGameTime();
                  var8.doorUntil = var8.doorAt + 20L;
                  var8.sync();
                  var2.scheduleTick(var8.getBlockPos(), this, 20);
                  var2.playSound(null, var3, SoundEvents.WOOL_HIT, SoundSource.BLOCKS, 0.45F, 1.2F);
                  return InteractionResult.CONSUME;
               }
            }
         }
      }
   }

   private boolean doorOccupied(Level var1, BlockPos var2, Direction var3) {
      for (int var4 = 0; var4 < CELLS.size(); var4++) {
         BlockPos var5 = var2.offset(offset(var4, var3));
         VoxelShape var6 = this.shapes[var4][1][var3.get2DDataValue()];
         VoxelShape var7 = this.shapes[var4][0][var3.get2DDataValue()];

         for (AABB var9 : Shapes.joinUnoptimized(var7, var6, BooleanOp.ONLY_FIRST).toAabbs()) {
            if (!var1.getEntitiesOfClass(LivingEntity.class, var9.move(var5)).isEmpty()) {
               return true;
            }
         }
      }

      return false;
   }

   public boolean toggleWindow(Level var1, BlockPos var2, Direction var3, CampingTent.Anchor var4, int var5) {
      if (var5 >= 0 && var5 <= 2 && (Boolean)var4.getBlockState().getValue(READY) && var4.windowUntil[var5] <= var1.getGameTime()) {
         int var6 = 1 << var5;
         boolean var7 = (var4.windows & var6) == 0;
         if (!var7 && this.windowOccupied(var1, var2, var3, var5)) {
            return false;
         } else {
            var4.windowTargets = var7 ? var4.windowTargets | var6 : var4.windowTargets & ~var6;
            var4.windowAt[var5] = var1.getGameTime();
            var4.windowUntil[var5] = var1.getGameTime() + 20L;
            var4.sync();
            var1.scheduleTick(var4.getBlockPos(), this, 20);
            var1.playSound(null, var4.getBlockPos(), SoundEvents.WOOL_HIT, SoundSource.BLOCKS, 0.35F, 1.3F);
            return true;
         }
      } else {
         return false;
      }
   }

   private boolean windowOccupied(Level var1, BlockPos var2, Direction var3, int var4) {
      for (int var5 = 0; var5 < CELLS.size(); var5++) {
         for (AABB var7 : Shapes.joinUnoptimized(
               this.shapes[var5][15 & ~(2 << var4)][var3.get2DDataValue()], this.shapes[var5][15][var3.get2DDataValue()], BooleanOp.ONLY_FIRST
            )
            .toAabbs()) {
            if (!var1.getEntitiesOfClass(LivingEntity.class, var7.move(var2.offset(offset(var5, var3)))).isEmpty()) {
               return true;
            }
         }
      }

      return false;
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      if (!var4.isClientSide()) {
         var4.scheduleTick(anchor(origin(var5, var1), (Direction)var1.getValue(FACING)), this, 1);
      }

      return var1;
   }

   protected void tick(BlockState var1, ServerLevel var2, BlockPos var3, RandomSource var4) {
      BlockPos var5 = origin(var3, var1);
      Direction var6 = (Direction)var1.getValue(FACING);

      for (int var7 = 0; var7 < CELLS.size(); var7++) {
         if (!var2.hasChunkAt(var5.offset(offset(var7, var6)))) {
            return;
         }
      }

      for (int var16 = 0; var16 < CELLS.size(); var16++) {
         if (!this.matches(var2.getBlockState(var5.offset(offset(var16, var6))), var16, var6)) {
            this.dismantle(var2, var5, var6, true);
            return;
         }
      }

      for (BlockPos var8 : BlockPos.betweenClosed(var5.offset(-2, -1, -2), var5.offset(2, -1, 2))) {
         if (!var2.getBlockState(var8).isFaceSturdy(var2, var8, Direction.UP)) {
            this.dismantle(var2, var5, var6, true);
            return;
         }
      }

      for (int var18 = 0; var18 < CELLS.size(); var18++) {
         BlockPos var20 = var5.offset(offset(var18, var6));
         BlockState var9 = var2.getBlockState(var20);
         ChunkAccess var10 = var2.getChunk(var20);
         boolean var11 = false;

         for (Types var15 : new Types[]{Types.MOTION_BLOCKING, Types.MOTION_BLOCKING_NO_LEAVES}) {
            var11 |= var10.getOrCreateHeightmapUnprimed(var15).update(var20.getX() & 15, var20.getY(), var20.getZ() & 15, var9);
         }

         if (var11) {
            var10.setUnsaved(true);
         }
      }

      if (var2.getBlockEntity(anchor(var5, var6)) instanceof CampingTent.Anchor var19) {
         for (int var22 = 0; var22 < 3; var22++) {
            if (var19.windowUntil[var22] > 0L && var2.getGameTime() >= var19.windowUntil[var22]) {
               int var24 = 1 << var22;
               boolean var26 = (var19.windowTargets & var24) != 0 || this.windowOccupied(var2, var5, var6, var22);
               var19.windows = var26 ? var19.windows | var24 : var19.windows & ~var24;
               var19.windowTargets = var26 ? var19.windowTargets | var24 : var19.windowTargets & ~var24;
               var19.windowUntil[var22] = 0L;
               var19.sync();
            }
         }

         boolean var23 = (Boolean)var1.getValue(READY) || var2.getGameTime() >= var19.setupAt + 60L;
         boolean var25 = (Boolean)var1.getValue(OPEN);
         if (var19.doorUntil > 0L && var2.getGameTime() >= var19.doorUntil) {
            var25 = var19.targetOpen || this.doorOccupied(var2, var5, var6);
            var19.doorUntil = 0L;
            var19.targetOpen = var25;
            var19.sync();
         }

         if (var23 != (Boolean)var1.getValue(READY) || var25 != (Boolean)var1.getValue(OPEN)) {
            for (int var27 = 0; var27 < CELLS.size(); var27++) {
               BlockPos var29 = var5.offset(offset(var27, var6));
               var2.setBlock(var29, (BlockState)((BlockState)var2.getBlockState(var29).setValue(READY, var23)).setValue(OPEN, var25), 18);
            }
         }

         if (!var23) {
            var2.scheduleTick(var19.getBlockPos(), this, (int)Math.max(1L, var19.setupAt + 60L - var2.getGameTime()));
         } else if (var19.doorUntil > var2.getGameTime()) {
            var2.scheduleTick(var19.getBlockPos(), this, (int)(var19.doorUntil - var2.getGameTime()));
         }

         for (long var32 : var19.windowUntil) {
            if (var32 > var2.getGameTime()) {
               var2.scheduleTick(var19.getBlockPos(), this, (int)(var32 - var2.getGameTime()));
            }
         }

         ShelterSeal.clear(var2, BlockPos.betweenClosed(var5.offset(-1, 0, -1), var5.offset(1, 1, 1)));
         var2.scheduleTick(var19.getBlockPos(), this, 20);
      }
   }

   protected void onRemove(BlockState var1, Level var2, BlockPos var3, BlockState var4, boolean var5) {
      super.onRemove(var1, var2, var3, var4, var5);
      if (!var2.isClientSide && !var4.is(this) && !REMOVING.get()) {
         this.dismantle(var2, origin(var3, var1), (Direction)var1.getValue(FACING), false);
      }
   }

   private void dismantle(Level var1, BlockPos var2, Direction var3, boolean var4) {
      if (!REMOVING.get()) {
         REMOVING.set(true);
         boolean var5 = false;

         try {
            for (int var6 = 0; var6 < CELLS.size(); var6++) {
               BlockPos var7 = var2.offset(offset(var6, var3));
               if (var1.hasChunkAt(var7) && this.matches(var1.getBlockState(var7), var6, var3)) {
                  var5 = true;
                  var1.setBlock(var7, Blocks.AIR.defaultBlockState(), 18);
               }
            }

            if (var4 && var5) {
               popResource(var1, var2, new ItemStack(ExpeditionContent.item(this.id())));
            }
         } finally {
            REMOVING.set(false);
         }

         for (int var11 = 0; var11 < CELLS.size(); var11++) {
            var1.updateNeighborsAt(var2.offset(offset(var11, var3)), Blocks.AIR);
         }
      }
   }

   private static VoxelShape rotateShape(VoxelShape var0, Direction var1) {
      if (var1 == Direction.NORTH) {
         return var0.optimize();
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

   public static final class Anchor extends BlockEntity {
      public long setupAt;
      public long doorAt;
      public long doorUntil;
      public boolean targetOpen = true;
      public int windows;
      public int windowTargets;
      public final long[] windowAt = new long[3];
      public final long[] windowUntil = new long[3];

      public float windowOpen(int var1, float var2) {
         if (this.level != null && this.windowUntil[var1] != 0L) {
            float var3 = Math.clamp(((float)this.level.getGameTime() + var2 - (float)this.windowAt[var1]) / 20.0F, 0.0F, 1.0F);
            var3 = var3 * var3 * (3.0F - 2.0F * var3);
            return (this.windowTargets & 1 << var1) != 0 ? var3 : 1.0F - var3;
         } else {
            return (this.windows & 1 << var1) != 0 ? 1.0F : 0.0F;
         }
      }

      public Anchor(BlockPos var1, BlockState var2) {
         super((BlockEntityType)ExpeditionContent.TENT_ANCHOR.get(), var1, var2);
      }

      public float open(float var1) {
         if (this.level == null) {
            return this.getBlockState().getValue(CampingTent.OPEN) ? 1.0F : 0.0F;
         } else if (this.doorUntil == 0L) {
            return this.getBlockState().getValue(CampingTent.OPEN) ? 1.0F : 0.0F;
         } else {
            float var2 = Math.clamp(((float)this.level.getGameTime() + var1 - (float)this.doorAt) / 20.0F, 0.0F, 1.0F);
            var2 = var2 * var2 * (3.0F - 2.0F * var2);
            return this.targetOpen ? var2 : 1.0F - var2;
         }
      }

      public float setup(float var1) {
         return this.level == null ? 1.0F : Math.clamp(((float)this.level.getGameTime() + var1 - (float)this.setupAt) / 60.0F, 0.0F, 1.0F);
      }

      public void sync() {
         this.setChanged();
         if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
         }
      }

      protected void saveAdditional(CompoundTag var1, Provider var2) {
         super.saveAdditional(var1, var2);
         var1.putLong("Setup", this.setupAt);
         var1.putLong("Door", this.doorAt);
         var1.putLong("Until", this.doorUntil);
         var1.putBoolean("Open", this.targetOpen);
         var1.putInt("Windows", this.windows);
         var1.putInt("WindowTargets", this.windowTargets);
         var1.putLongArray("WindowAt", this.windowAt);
         var1.putLongArray("WindowUntil", this.windowUntil);
      }

      protected void loadAdditional(CompoundTag var1, Provider var2) {
         super.loadAdditional(var1, var2);
         this.setupAt = var1.getLong("Setup");
         this.doorAt = var1.getLong("Door");
         this.doorUntil = var1.getLong("Until");
         this.targetOpen = var1.getBoolean("Open");
         this.windows = var1.getInt("Windows") & 7;
         this.windowTargets = var1.getInt("WindowTargets") & 7;
         long[] var3 = var1.getLongArray("WindowAt");
         long[] var4 = var1.getLongArray("WindowUntil");

         for (int var5 = 0; var5 < 3; var5++) {
            this.windowAt[var5] = var5 < var3.length ? var3[var5] : 0L;
            this.windowUntil[var5] = var5 < var4.length ? var4[var5] : 0L;
         }
      }

      public CompoundTag getUpdateTag(Provider var1) {
         return this.saveWithoutMetadata(var1);
      }

      public ClientboundBlockEntityDataPacket getUpdatePacket() {
         return ClientboundBlockEntityDataPacket.create(this);
      }

      public void onLoad() {
         super.onLoad();
         if (this.level != null && !this.level.isClientSide) {
            this.level.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 1);
         }
      }
   }

   public static final class Kit extends Item {
      private final String design;

      public Kit(boolean var1) {
         this(var1 ? "trail_dome_tent" : "woodland_camp_tent");
      }

      public Kit(String var1) {
         super(new net.minecraft.world.item.Item.Properties().stacksTo(1));
         this.design = var1;
      }

      public InteractionResult useOn(UseOnContext var1) {
         if (var1.getClickedFace() != Direction.UP) {
            return InteractionResult.FAIL;
         } else if (var1.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
         } else if (var1.getPlayer() instanceof ServerPlayer var2) {
            CampingTent var4 = (CampingTent)ExpeditionContent.TENTS.get(this.design).get();
            if (!var4.assemble(var2, var1.getClickedPos().above(), var2.getDirection().getOpposite(), var1.getItemInHand())) {
               return InteractionResult.FAIL;
            } else {
               if (!var2.isCreative()) {
                  var1.getItemInHand().shrink(1);
               }

               return InteractionResult.CONSUME;
            }
         } else {
            return InteractionResult.FAIL;
         }
      }

      public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
         var3.add(Component.literal("5 × 5 footprint · clear 3 × 3 interior"));
         var3.add(Component.literal("Use window edge: roll flap · Use front: roll door · Sneak-use: pack"));
      }
   }
}
