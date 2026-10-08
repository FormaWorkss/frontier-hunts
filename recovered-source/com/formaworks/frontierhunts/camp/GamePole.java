package com.formaworks.frontierhunts.camp;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.expedition.ExpeditionService;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class GamePole extends Block implements EntityBlock {
   public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
   public static final IntegerProperty LOAD = IntegerProperty.create("load", 0, 4);
   public static final int AGE_TICKS = 48000;
   private static final int CHECK = 200;

   public GamePole(Properties var1) {
      super(var1);
      this.registerDefaultState((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH)).setValue(LOAD, 0));
   }

   protected MapCodec<GamePole> codec() {
      return simpleCodec(GamePole::new);
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{FACING, LOAD});
   }

   public BlockState getStateForPlacement(BlockPlaceContext var1) {
      return (BlockState)this.defaultBlockState().setValue(FACING, var1.getHorizontalDirection().getClockWise());
   }

   protected BlockState rotate(BlockState var1, Rotation var2) {
      return (BlockState)var1.setValue(FACING, var2.rotate((Direction)var1.getValue(FACING)));
   }

   protected BlockState mirror(BlockState var1, Mirror var2) {
      return var1.rotate(var2.getRotation((Direction)var1.getValue(FACING)));
   }

   public BlockEntity newBlockEntity(BlockPos var1, BlockState var2) {
      return new GamePole.Rack(var1, var2);
   }

   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level var1, BlockState var2, BlockEntityType<T> var3) {
      return !var1.isClientSide && var3 == CampContent.GAME_RACK.get()
         ? (var0, var1x, var2x, var3x) -> GamePole.Rack.tick(var0, var1x, var2x, (GamePole.Rack)var3x)
         : null;
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      boolean var5 = ((Direction)var1.getValue(FACING)).getAxis() == Axis.X;
      VoxelShape var6 = var5 ? box(0.0, 13.0, 6.5, 16.0, 15.0, 9.5) : box(6.5, 13.0, 0.0, 9.5, 15.0, 16.0);
      VoxelShape var7 = var5 ? box(1.0, 0.0, 6.0, 3.0, 16.0, 10.0) : box(6.0, 0.0, 1.0, 10.0, 16.0, 3.0);
      VoxelShape var8 = var5 ? box(13.0, 0.0, 6.0, 15.0, 16.0, 10.0) : box(6.0, 0.0, 13.0, 10.0, 16.0, 15.0);
      return Shapes.or(var6, new VoxelShape[]{var7, var8});
   }

   protected boolean canSurvive(BlockState var1, LevelReader var2, BlockPos var3) {
      BlockPos var4 = var3.below();
      return var2.getBlockState(var4).isFaceSturdy(var2, var4, Direction.UP);
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      return this.canSurvive(var1, var4, var5) ? var1 : Blocks.AIR.defaultBlockState();
   }

   protected void onRemove(BlockState var1, Level var2, BlockPos var3, BlockState var4, boolean var5) {
      if (!var2.isClientSide && !var1.is(var4.getBlock())) {
         for (AbstractHorse var7 : tied(var2, var3)) {
            HorseTie.untie(var7);
         }
      }

      if (!var2.isClientSide && !var1.is(var4.getBlock()) && var2.getBlockEntity(var3) instanceof GamePole.Rack var8) {
         var8.dropAll(var2, var3);
      }

      super.onRemove(var1, var2, var3, var4, var5);
   }

   protected ItemInteractionResult useItemOn(ItemStack var1, BlockState var2, Level var3, BlockPos var4, Player var5, InteractionHand var6, BlockHitResult var7) {
      if (!(var5.getVehicle() instanceof AbstractHorse)
         && (var1.is((Item)HuntContent.VENISON_QUARTER.get()) || var1.is(Items.LEAD) || !tiedHere(var3, var4, var5))) {
         if (!var1.is(Items.LEAD)) {
            if (!var1.is((Item)HuntContent.VENISON_QUARTER.get())) {
               return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            } else if (var3.isClientSide) {
               return ItemInteractionResult.SUCCESS;
            } else {
               if (var3.getBlockEntity(var4) instanceof GamePole.Rack var12 && var12.hung.size() < 4) {
                  var12.hung.add(new GamePole.Hung(var3.getGameTime(), 0));
                  var12.setChanged();
                  var3.setBlock(var4, (BlockState)var2.setValue(LOAD, var12.hung.size()), 3);
                  if (!var5.hasInfiniteMaterials()) {
                     var1.shrink(1);
                  }

                  var3.playSound(null, var4, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 0.9F);
                  if (var5 instanceof ServerPlayer var15) {
                     var15.displayClientMessage(
                        Component.literal("Hung · " + var12.hung.size() + "/4 · " + (cold(var3, var4) ? "cool enough to hang" : "too warm here, it will spoil")),
                        true
                     );
                  }

                  return ItemInteractionResult.SUCCESS;
               }

               if (var5 instanceof ServerPlayer var14) {
                  var14.displayClientMessage(Component.literal("The pole is full."), true);
               }

               return ItemInteractionResult.CONSUME;
            }
         } else if (var3.isClientSide) {
            return ItemInteractionResult.SUCCESS;
         } else {
            List var8 = var3.getEntitiesOfClass(AbstractHorse.class, new AABB(var4).inflate(7.0), var1x -> var1x.getLeashHolder() == var5);
            if (var8.isEmpty()) {
               return ItemInteractionResult.CONSUME;
            } else {
               LeashFenceKnotEntity var9 = LeashFenceKnotEntity.getOrCreateKnot(var3, var4);

               for (AbstractHorse var11 : var8) {
                  var11.setLeashedTo(var9, true);
               }

               var3.playSound(null, var4, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
               if (var5 instanceof ServerPlayer var16) {
                  var16.displayClientMessage(Component.literal(var8.size() == 1 ? "Horse tied to game pole" : var8.size() + " horses tied to game pole"), true);
               }

               return ItemInteractionResult.CONSUME;
            }
         }
      } else if (var6 != InteractionHand.MAIN_HAND) {
         return ItemInteractionResult.CONSUME;
      } else {
         if (!var3.isClientSide) {
            toggleTie(var3, var4, var5);
         }

         return ItemInteractionResult.sidedSuccess(var3.isClientSide);
      }
   }

   protected InteractionResult useWithoutItem(BlockState var1, Level var2, BlockPos var3, Player var4, BlockHitResult var5) {
      if (!(var4.getVehicle() instanceof AbstractHorse) && !tiedHere(var2, var3, var4)) {
         if (var2.isClientSide) {
            return InteractionResult.SUCCESS;
         } else {
            for (LeashFenceKnotEntity var8 : var2.getEntitiesOfClass(
               LeashFenceKnotEntity.class, new AABB(var3).inflate(0.5), var1x -> var1x.blockPosition().equals(var3)
            )) {
               List var9 = var2.getEntitiesOfClass(AbstractHorse.class, new AABB(var3).inflate(10.0), var1x -> var1x.getLeashHolder() == var8);
               if (!var9.isEmpty()) {
                  for (AbstractHorse var11 : var9) {
                     if (var4.isShiftKeyDown()) {
                        var11.dropLeash(true, true);
                     } else {
                        var11.setLeashedTo(var4, true);
                     }
                  }

                  var8.discard();
                  var2.playSound(null, var3, SoundEvents.LEASH_KNOT_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
                  return InteractionResult.CONSUME;
               }
            }

            if (var2.getBlockEntity(var3) instanceof GamePole.Rack var12 && !var12.hung.isEmpty()) {
               GamePole.Hung var14 = var12.hung.remove(0);
               var12.setChanged();
               var2.setBlock(var3, (BlockState)var1.setValue(LOAD, var12.hung.size()), 3);
               ItemStack var15 = var14.harvest(var2.getGameTime());
               if (var4 instanceof ServerPlayer var16) {
                  ExpeditionService.give(var16, var15);
                  var16.displayClientMessage(Component.literal(var14.label(var2.getGameTime())), true);
               } else {
                  Containers.dropItemStack(var2, (double)var3.getX() + 0.5, (double)(var3.getY() + 1), (double)var3.getZ() + 0.5, var15);
               }

               var2.playSound(null, var3, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 0.9F);
               return InteractionResult.CONSUME;
            }

            return InteractionResult.CONSUME;
         }
      } else {
         if (!var2.isClientSide) {
            toggleTie(var2, var3, var4);
         }

         return InteractionResult.sidedSuccess(var2.isClientSide);
      }
   }

   private static List<AbstractHorse> tied(Level var0, BlockPos var1) {
      List var2 = var0.getEntitiesOfClass(LeashFenceKnotEntity.class, new AABB(var1).inflate(0.5), var1x -> var1x.blockPosition().equals(var1));
      ArrayList var3 = new ArrayList();

      for (LeashFenceKnotEntity var5 : var2) {
         var3.addAll(
            var0.getEntitiesOfClass(AbstractHorse.class, new AABB(var1).inflate(12.0), var1x -> var1x.getLeashHolder() == var5 && HorseTie.isTied(var1x))
         );
      }

      return var3;
   }

   private static boolean tiedHere(Level var0, BlockPos var1, Player var2) {
      if (!var2.isShiftKeyDown() && !var2.isPassenger()) {
         for (AbstractHorse var4 : tied(var0, var1)) {
            if (HorseTie.owner(var4, var2)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static void toggleTie(Level var0, BlockPos var1, Player var2) {
      if (var2.getVehicle() instanceof AbstractHorse var7) {
         var2.stopRiding();
         LeashFenceKnotEntity var9 = LeashFenceKnotEntity.getOrCreateKnot(var0, var1);
         var7.setLeashedTo(var9, true);
         HorseTie.tie(var7, var2);
         var7.getNavigation().stop();
         var7.setDeltaMovement(Vec3.ZERO);
         var0.playSound(null, var1, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
         if (var2 instanceof ServerPlayer var10) {
            var10.displayClientMessage(Component.literal("Horse tied to the game pole · click the pole again to ride"), true);
         }
      } else {
         for (AbstractHorse var8 : tied(var0, var1)) {
            if (HorseTie.owner(var8, var2)) {
               Entity var5 = var8.getLeashHolder();
               var8.dropLeash(true, false);
               HorseTie.untie(var8);
               if (var5 instanceof LeashFenceKnotEntity var6
                  && var0.getEntitiesOfClass(Mob.class, var6.getBoundingBox().inflate(12.0), var1x -> var1x.getLeashHolder() == var6).isEmpty()) {
                  var6.discard();
               }

               if (var8.isSaddled() && var8.isTamed() && !var8.isVehicle()) {
                  var2.startRiding(var8);
               }

               var0.playSound(null, var1, SoundEvents.LEASH_KNOT_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
               if (var2 instanceof ServerPlayer var11) {
                  var11.displayClientMessage(Component.literal("Untied · back in the saddle"), true);
               }

               return;
            }
         }
      }
   }

   public static boolean cold(Level var0, BlockPos var1) {
      return ((Biome)var0.getBiome(var1).value()).getBaseTemperature() < 0.95F;
   }

   public static record Hung(long hungAt, int state) {
      public Hung(long hungAt, int state) {
         state = Math.clamp((long)state, 0, 2);
         hungAt = Math.max(0L, hungAt);
         this.hungAt = hungAt;
         this.state = state;
      }

      public long age(long var1) {
         return Math.max(0L, var1 - this.hungAt);
      }

      public ItemStack harvest(long var1) {
         return switch (this.state) {
            case 1 -> new ItemStack((ItemLike)HuntContent.AGED_VENISON.get());
            case 2 -> new ItemStack(Items.ROTTEN_FLESH, 2);
            default -> new ItemStack((ItemLike)HuntContent.VENISON_QUARTER.get());
         };
      }

      public String label(long var1) {
         return switch (this.state) {
            case 1 -> "Aged quarter taken down";
            case 2 -> "This one turned · it was too warm";
            default -> "Still hanging · " + Math.max(1L, (48000L - this.age(var1)) / 1200L) + " minutes to go";
         };
      }
   }

   public static final class Rack extends BlockEntity {
      public final List<GamePole.Hung> hung = new ArrayList<>();

      public Rack(BlockPos var1, BlockState var2) {
         super((BlockEntityType)CampContent.GAME_RACK.get(), var1, var2);
      }

      public static void tick(Level var0, BlockPos var1, BlockState var2, GamePole.Rack var3) {
         long var4 = var0.getGameTime();
         if (var4 % 200L == 0L && !var3.hung.isEmpty()) {
            boolean var6 = GamePole.cold(var0, var1);
            boolean var7 = false;

            for (int var8 = 0; var8 < var3.hung.size(); var8++) {
               GamePole.Hung var9 = var3.hung.get(var8);
               if (var9.state() == 0 && var9.age(var4) >= 48000L) {
                  var3.hung.set(var8, new GamePole.Hung(var9.hungAt(), var6 ? 1 : 2));
                  var7 = true;
               }
            }

            if (var7) {
               var3.setChanged();
            }
         }
      }

      public void dropAll(Level var1, BlockPos var2) {
         long var3 = var1.getGameTime();

         for (GamePole.Hung var6 : this.hung) {
            Containers.dropItemStack(var1, (double)var2.getX() + 0.5, (double)(var2.getY() + 1), (double)var2.getZ() + 0.5, var6.harvest(var3));
         }

         this.hung.clear();
      }

      protected void saveAdditional(CompoundTag var1, Provider var2) {
         super.saveAdditional(var1, var2);
         ListTag var3 = new ListTag();

         for (GamePole.Hung var5 : this.hung) {
            CompoundTag var6 = new CompoundTag();
            var6.putLong("at", var5.hungAt());
            var6.putInt("state", var5.state());
            var3.add(var6);
         }

         var1.put("hung", var3);
      }

      protected void loadAdditional(CompoundTag var1, Provider var2) {
         super.loadAdditional(var1, var2);
         this.hung.clear();
         ListTag var3 = var1.getList("hung", 10);

         for (int var4 = 0; var4 < var3.size() && this.hung.size() < 4; var4++) {
            CompoundTag var5 = var3.getCompound(var4);
            this.hung.add(new GamePole.Hung(var5.getLong("at"), var5.getInt("state")));
         }
      }
   }
}
