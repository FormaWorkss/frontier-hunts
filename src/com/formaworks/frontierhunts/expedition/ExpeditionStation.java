package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.rifle.RifleItem;
import com.formaworks.frontierhunts.workshop.StationShapes;
import com.formaworks.frontierhunts.workshop.WideStationBlock;
import com.formaworks.frontierhunts.workshop.WorkbenchMenu;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ExpeditionStation extends WideStationBlock {
   public final String id;
   private static final VoxelShape CHEST = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);
   private static final VoxelShape RELOADER = Shapes.or(Block.box(1.0, 0.0, 1.0, 15.0, 13.0, 15.0), Block.box(4.0, 13.0, 2.0, 12.0, 16.0, 10.0));

   public ExpeditionStation(String var1, Properties var2) {
      super(var2);
      this.id = var1;
   }

   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return RecordCodecBuilder.<ExpeditionStation>mapCodec(
         var0 -> var0.group(Codec.STRING.fieldOf("station").forGetter((ExpeditionStation var0x) -> var0x.id), propertiesCodec()).apply(var0, ExpeditionStation::create)
      );
   }

   @Override
   protected boolean widePlacement() {
      return this.id.equals("bow_tuning_rack")
         || this.id.equals("fishing_station")
         || this.id.equals("clothing_workbench")
         || this.id.equals("tent_bench")
         || this.id.equals("expedition_board");
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      if (this.id.equals("lodge_stores")) {
         return CHEST;
      } else if (this.id.equals("gun_rack")) {
         return GunRackShape.get(var1);
      } else if (this.id.equals("ammo_reloader")) {
         return RELOADER;
      } else {
         return this.widePlacement() ? StationShapes.get(var1) : super.getShape(var1, var2, var3, var4);
      }
   }

   protected boolean hasAnalogOutputSignal(BlockState var1) {
      return this.id.equals("lodge_stores");
   }

   protected int getAnalogOutputSignal(BlockState var1, Level var2, BlockPos var3) {
      return this.id.equals("lodge_stores") ? AbstractContainerMenu.getRedstoneSignalFromBlockEntity(var2.getBlockEntity(var3)) : 0;
   }

   public static ExpeditionStation create(String var0, Properties var1) {
      return (ExpeditionStation)(!var0.equals("lodge_stores") && !var0.equals("trophy_plinth") && !var0.equals("gun_rack") && !var0.equals("bow_tuning_rack")
         ? new ExpeditionStation(var0, var1)
         : new ExpeditionStation.Stored(var0, var1));
   }

   @Override
   protected void onRemove(BlockState var1, Level var2, BlockPos var3, BlockState var4, boolean var5) {
      if (!var1.is(var4.getBlock()) && var2.getBlockEntity(var3) instanceof StationStorage var6) {
         Containers.dropContents(var2, var3, var6);
         var2.updateNeighbourForOutputSignal(var3, this);
      }

      super.onRemove(var1, var2, var3, var4, var5);
   }

   protected ItemInteractionResult useItemOn(ItemStack var1, BlockState var2, Level var3, BlockPos var4, Player var5, InteractionHand var6, BlockHitResult var7) {
      if (!this.id.equals("bow_tuning_rack")) {
         if (!this.id.equals("gun_rack")) {
            return super.useItemOn(var1, var2, var3, var4, var5, var6, var7);
         } else {
            if (var3 instanceof ServerLevel && var5 instanceof ServerPlayer var12 && var3.getBlockEntity(var4) instanceof StationStorage var14) {
               rack(var12, var14, var4, var7, var6);
            }

            return ItemInteractionResult.sidedSuccess(var3.isClientSide);
         }
      } else {
         boolean var10000;
         label65: {
            label52:
            if (!var1.is(Items.BOW) && !var1.is(Items.CROSSBOW)) {
               if (var1.getItem() instanceof ExpeditionWeapon var9 && var9.weapon.bow) {
                  break label52;
               }

               var10000 = false;
               break label65;
            }

            var10000 = true;
         }

         boolean var8 = var10000;
         if (var8 && !var5.isShiftKeyDown()) {
            if (var3 instanceof ServerLevel && var5 instanceof ServerPlayer var13 && var3.getBlockEntity(origin(var4, var2)) instanceof StationStorage var15) {
               bowDisplay(var13, var15, var1, secondary(var2) ? 1 : 0);
            }

            return ItemInteractionResult.sidedSuccess(var3.isClientSide);
         } else {
            return super.useItemOn(var1, var2, var3, var4, var5, var6, var7);
         }
      }
   }

   private static void bowDisplay(ServerPlayer var0, StationStorage var1, ItemStack var2, int var3) {
      if (!var1.getItem(var3).isEmpty()) {
         ExpeditionService.message(var0, "Use an empty hand to recover this bow.");
      } else {
         var1.setItem(var3, var2.copyWithCount(1));
         if (!var0.hasInfiniteMaterials()) {
            var2.shrink(1);
         }
      }
   }

   private static void rack(ServerPlayer var0, StationStorage var1, BlockPos var2, BlockHitResult var3, InteractionHand var4) {
      int var5 = Math.clamp((long)((int)((var3.getLocation().y - (double)var2.getY()) * 3.0)), 0, 2);
      ItemStack var6 = var0.getItemInHand(var4);
      ItemStack var7 = var1.getItem(var5);
      boolean var8 = var6.getItem() instanceof ExpeditionWeapon || var6.getItem() instanceof RifleItem;
      if (var7.isEmpty() && var8) {
         var1.setItem(var5, var6.copyWithCount(1));
         if (!var0.hasInfiniteMaterials()) {
            var6.shrink(1);
         }
      } else if (!var7.isEmpty() && var6.isEmpty()) {
         ExpeditionService.give(var0, var1.removeItem(var5, 1));
      } else {
         ExpeditionService.message(var0, var7.isEmpty() ? "Hold a Frontier firearm to display it." : "Use an empty hand to recover this firearm.");
      }
   }

   protected InteractionResult useWithoutItem(BlockState var1, Level var2, BlockPos var3, Player var4, BlockHitResult var5) {
      if (var4 instanceof ServerPlayer var6) {
         if (this.id.equals("bow_tuning_rack")
            && !var6.isShiftKeyDown()
            && var2.getBlockEntity(origin(var3, var1)) instanceof StationStorage var7
            && !var7.getItem(secondary(var1) ? 1 : 0).isEmpty()) {
            ExpeditionService.give(var6, var7.removeItem(secondary(var1) ? 1 : 0, 1));
         } else if (!this.id.equals("ammo_reloader")
            && !this.id.equals("bow_tuning_rack")
            && !this.id.equals("fishing_station")
            && !this.id.equals("clothing_workbench")
            && !this.id.equals("tent_bench")) {
            if (this.id.equals("lodge_stores") && var2.getBlockEntity(var3) instanceof StationStorage var8) {
               var6.openMenu(var8);
               var6.awardStat(Stats.OPEN_CHEST);
            } else if (this.id.equals("gun_rack") && var2.getBlockEntity(var3) instanceof StationStorage var9) {
               rack(var6, var9, var3, var5, InteractionHand.MAIN_HAND);
            } else if (this.id.equals("trophy_plinth") && var2.getBlockEntity(var3) instanceof StationStorage var10) {
               ItemStack var20 = var6.getMainHandItem();
               boolean var12 = var20.is((Item)HuntContent.WHITETAIL_TROPHY.get()); // [integ2] EUROPEAN_MOUNT no longer exists (aliased to whitetail_trophy); the old field ref threw NoSuchFieldError
               if (var10.isEmpty() && var12) {
                  ItemStack var21 = var20.copyWithCount(1);
                  var10.setItem(0, var21);
                  if (!var6.hasInfiniteMaterials()) {
                     var20.shrink(1);
                  }

                  CompoundTag var14 = ExpeditionWeapon.data(var21);
                  ExpeditionLedger var15 = ExpeditionLedger.get(var6.serverLevel());
                  ExpeditionLedger.Hunter var16 = var15.hunter(var6.getUUID());
                  var16.bestTrophy = Math.max(var16.bestTrophy, (double)Math.max(var14.getInt("score"), var14.getInt("trophy_score")));
                  var15.setDirty();
                  ExpeditionService.record(var6, "display", "*", 1, 0.0);
                  ExpeditionService.message(var6, "Trophy displayed · use an empty hand to recover it");
               } else if (!var10.isEmpty() && var20.isEmpty()) {
                  ItemStack var13 = var10.removeItem(0, 1);
                  ExpeditionService.give(var6, var13);
               } else {
                  ExpeditionService.message(var6, "Place a trophy on an empty plinth; recover it with an empty hand.");
               }
            } else {
               ExpeditionService.send(var6, true);
            }
         } else {
            var6.openMenu(
               new SimpleMenuProvider(
                  (var3x, var4x, var5x) -> new WorkbenchMenu(var3x, var4x, var2, origin(var3, var1)), Component.translatable("block.frontierhunts." + this.id)
               )
            );
         }
      }

      return InteractionResult.sidedSuccess(var2.isClientSide);
   }

   private static final class Stored extends ExpeditionStation implements EntityBlock {
      private Stored(String var1, Properties var2) {
         super(var1, var2);
      }

      public BlockEntity newBlockEntity(BlockPos var1, BlockState var2) {
         return secondary(var2) ? null : new StationStorage(var1, var2);
      }

      public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level var1, BlockState var2, BlockEntityType<T> var3) {
         return var1.isClientSide && this.id.equals("lodge_stores") && var3 == ExpeditionContent.STORAGE.get() ? (BlockEntityTicker<T>)(BlockEntityTicker<StationStorage>)StationStorage::clientTick : null;
      }

      protected boolean triggerEvent(BlockState var1, Level var2, BlockPos var3, int var4, int var5) {
         super.triggerEvent(var1, var2, var3, var4, var5);
         BlockEntity var6 = var2.getBlockEntity(var3);
         return var6 != null && var6.triggerEvent(var4, var5);
      }

      @Override
      protected void tick(BlockState var1, ServerLevel var2, BlockPos var3, RandomSource var4) {
         if (var2.getBlockEntity(var3) instanceof StationStorage var5) {
            var5.recheckOpen();
         }
      }
   }
}
