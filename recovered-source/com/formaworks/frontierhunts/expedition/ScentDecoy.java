package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ScentDecoy extends Block implements EntityBlock {
   public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
   public static final int DURATION = 12000;
   public final String species;

   public ScentDecoy(String var1) {
      super(Properties.of().strength(0.8F).sound(SoundType.METAL).noOcclusion());
      this.species = var1;
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(ACTIVE, true));
   }

   protected MapCodec<ScentDecoy> codec() {
      return RecordCodecBuilder.mapCodec(var0 -> var0.group(Codec.STRING.fieldOf("species").forGetter(var0x -> var0x.species)).apply(var0, ScentDecoy::new));
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{ACTIVE});
   }

   public BlockEntity newBlockEntity(BlockPos var1, BlockState var2) {
      return new ScentDecoy.Wick(var1, var2);
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return Block.box(5.0, 0.0, 5.0, 11.0, 12.0, 11.0);
   }

   protected void onPlace(BlockState var1, Level var2, BlockPos var3, BlockState var4, boolean var5) {
      if (!var2.isClientSide && !var4.is(this)) {
         if (var2.getBlockEntity(var3) instanceof ScentDecoy.Wick var6) {
            var6.until = var2.getGameTime() + 12000L;
            var6.setChanged();
         }

         var2.scheduleTick(var3, this, 100);
      }
   }

   public void setPlacedBy(Level var1, BlockPos var2, BlockState var3, LivingEntity var4, ItemStack var5) {
      if (!var1.isClientSide && var1.getBlockEntity(var2) instanceof ScentDecoy.Wick var6) {
         CompoundTag var10 = ExpeditionWeapon.data(var5);
         if (var10.contains("scent_remaining")) {
            long var8 = (long)Math.clamp(var10.getLong("scent_remaining"), 0, 12000);
            var6.until = var1.getGameTime() + var8;
            var6.setChanged();
            var1.setBlock(var2, (BlockState)var3.setValue(ACTIVE, var8 > 0L), 2);
         }
      }
   }

   protected List<ItemStack> getDrops(BlockState var1, net.minecraft.world.level.storage.loot.LootParams.Builder var2) {
      List var3 = super.getDrops(var1, var2);
      if (var2.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ScentDecoy.Wick var4) {
         for (ItemStack var6 : var3) {
            if (var6.is(this.asItem())) {
               CompoundTag var7 = ExpeditionWeapon.data(var6);
               var7.putLong("scent_remaining", (long)Math.clamp(var4.until - var2.getLevel().getGameTime(), 0, 12000));
               ExpeditionWeapon.save(var6, var7);
            }
         }
      }

      return var3;
   }

   protected boolean canSurvive(BlockState var1, LevelReader var2, BlockPos var3) {
      return var2.getBlockState(var3.below()).isFaceSturdy(var2, var3.below(), Direction.UP);
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      return var2 == Direction.DOWN && !this.canSurvive(var1, var4, var5) ? Blocks.AIR.defaultBlockState() : var1;
   }

   protected InteractionResult useWithoutItem(BlockState var1, Level var2, BlockPos var3, Player var4, BlockHitResult var5) {
      if (var4 instanceof ServerPlayer var6 && var2.getBlockEntity(var3) instanceof ScentDecoy.Wick var7) {
         ExpeditionService.message(
            var6, this.species.replace('_', ' ') + " scent wick · " + Math.max(0L, (var7.until - var2.getGameTime()) / 20L) + " seconds · use bait to recharge"
         );
      }

      return InteractionResult.sidedSuccess(var2.isClientSide);
   }

   protected ItemInteractionResult useItemOn(ItemStack var1, BlockState var2, Level var3, BlockPos var4, Player var5, InteractionHand var6, BlockHitResult var7) {
      if (!var1.is(ExpeditionContent.item("bait"))) {
         return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
      } else {
         if (!var3.isClientSide && var3.getBlockEntity(var4) instanceof ScentDecoy.Wick var8) {
            if (var8.until - var3.getGameTime() > 10800L) {
               return ItemInteractionResult.CONSUME;
            }

            var8.until = var3.getGameTime() + 12000L;
            var8.setChanged();
            var3.setBlock(var4, (BlockState)var2.setValue(ACTIVE, true), 2);
            var3.scheduleTick(var4, this, 100);
            if (!var5.hasInfiniteMaterials()) {
               var1.shrink(1);
            }
         }

         return ItemInteractionResult.sidedSuccess(var3.isClientSide);
      }
   }

   public boolean accepts(PathfinderMob var1) {
      if (!WildlifeLure.calm(var1)) {
         return false;
      } else {
         if (var1 instanceof Whitetail var2
            && this.species.equals("whitetail")
            && !var2.downed()
            && !var2.sedated()
            && !var2.bleeding()
            && (double)var2.alertness() < 0.25) {
            return true;
         }

         return false;
      }
   }

   protected void tick(BlockState var1, ServerLevel var2, BlockPos var3, RandomSource var4) {
      if (var2.getBlockEntity(var3) instanceof ScentDecoy.Wick var5) {
         if (var5.until <= var2.getGameTime()) {
            var2.setBlock(var3, (BlockState)var1.setValue(ACTIVE, false), 2);
         } else {
            Wilderness.Wind var16 = Wilderness.wind(var2.getSeed(), var2.getGameTime(), var2.isRaining(), var2.isThundering());
            Vec3 var7 = Vec3.atCenterOf(var3);
            List var8 = var2.getEntitiesOfClass(PathfinderMob.class, new AABB(var3).inflate(32.0), this::accepts);
            var8.sort(Comparator.comparingDouble(var1x -> var1x.distanceToSqr(var7)));

            for (PathfinderMob var10 : var8.stream().limit(8L).toList()) {
               if (!(var10.distanceToSqr(var7) < 9.0) && var10.getPersistentData().getLong("frontier_decoy_until") <= var2.getGameTime()) {
                  double var11 = Wilderness.scent(var16, var10.getX() - var7.x, var10.getZ() - var7.z, var2.isRaining(), false);
                  if (!(var11 < 0.18)) {
                     double var13 = var4.nextDouble() * Math.PI * 2.0;
                     BlockPos var15 = var3.offset((int)Math.round(Math.cos(var13) * 2.0), 0, (int)Math.round(Math.sin(var13) * 2.0));
                     if (var2.hasChunkAt(var15)) {
                        WildlifeLure.offer(var10, var3, var15);
                     }
                  }
               }
            }

            var2.scheduleTick(var3, this, 100);
         }
      }
   }

   public static final class Wick extends BlockEntity {
      public long until;

      public Wick(BlockPos var1, BlockState var2) {
         super((BlockEntityType)ExpeditionContent.DECOY_WICK.get(), var1, var2);
      }

      protected void saveAdditional(CompoundTag var1, Provider var2) {
         super.saveAdditional(var1, var2);
         var1.putLong("ScentUntil", this.until);
      }

      protected void loadAdditional(CompoundTag var1, Provider var2) {
         super.loadAdditional(var1, var2);
         this.until = Math.max(0L, var1.getLong("ScentUntil"));
      }

      public void onLoad() {
         super.onLoad();
         if (this.level != null && !this.level.isClientSide) {
            if (this.until == 0L) {
               this.until = this.level.getGameTime() + 12000L;
               this.setChanged();
            }

            this.level.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 1);
         }
      }
   }
}
