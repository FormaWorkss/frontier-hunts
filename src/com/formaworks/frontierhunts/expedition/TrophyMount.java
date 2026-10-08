package com.formaworks.frontierhunts.expedition;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TrophyMount extends HangingEntity {
   private static final EntityDataAccessor<ItemStack> TROPHY = SynchedEntityData.defineId(TrophyMount.class, EntityDataSerializers.ITEM_STACK);

   public TrophyMount(EntityType<? extends TrophyMount> var1, Level var2) {
      super(var1, var2);
   }

   protected void defineSynchedData(Builder var1) {
      var1.define(TROPHY, ItemStack.EMPTY);
   }

   public ItemStack trophy() {
      return (ItemStack)this.entityData.get(TROPHY);
   }

   public void configure(BlockPos var1, Direction var2, ItemStack var3) {
      this.pos = var1;
      this.entityData.set(TROPHY, var3.copyWithCount(1));
      this.setDirection(var2);
   }

   protected AABB calculateBoundingBox(BlockPos var1, Direction var2) {
      Vec3 var3 = Vec3.atCenterOf(var1).relative(var2, -0.46875);
      return AABB.ofSize(var3, var2.getAxis() == Axis.X ? 0.0625 : 1.12, 1.15, var2.getAxis() == Axis.Z ? 0.0625 : 1.12);
   }

   public AABB getBoundingBoxForCulling() {
      return this.getBoundingBox().inflate(0.4).expandTowards(Vec3.atLowerCornerOf(this.direction.getNormal()).scale(0.8));
   }

   public boolean survives() {
      BlockPos var1 = this.pos.relative(this.direction.getOpposite());
      return this.level().getBlockState(var1).isFaceSturdy(this.level(), var1, this.direction)
         && this.level().noCollision(this, this.getBoundingBox().deflate(0.001).expandTowards(Vec3.atLowerCornerOf(this.direction.getNormal()).scale(0.65)))
         && this.level().getEntities(this, this.getBoundingBox().inflate(0.03), HANGING_ENTITY).isEmpty();
   }

   public boolean shouldRenderAtSqrDistance(double var1) {
      return var1 < 4096.0;
   }

   public void playPlacementSound() {
      this.playSound(SoundEvents.WOOD_PLACE, 0.65F, 0.85F);
   }

   public void dropItem(Entity var1) {
      ItemStack var2 = this.trophy();
      if (!var2.isEmpty()) {
         this.entityData.set(TROPHY, ItemStack.EMPTY);
         this.playSound(SoundEvents.WOOD_BREAK, 0.65F, 0.9F);
         if (this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS) && (!(var1 instanceof Player var3) || !var3.hasInfiniteMaterials())) {
            this.spawnAtLocation(var2.copyWithCount(1));
         }
      }
   }

   public ItemStack getPickResult() {
      return this.trophy().copy();
   }

   public InteractionResult interact(Player var1, InteractionHand var2) {
      if (!this.level().mayInteract(var1, this.pos)) {
         return InteractionResult.FAIL;
      } else {
         if (!this.level().isClientSide) {
            var1.displayClientMessage(this.trophy().getHoverName(), true);
         }

         return InteractionResult.sidedSuccess(this.level().isClientSide);
      }
   }

   public void addAdditionalSaveData(CompoundTag var1) {
      super.addAdditionalSaveData(var1);
      var1.putByte("Facing", (byte)this.direction.get3DDataValue());
      if (!this.trophy().isEmpty()) {
         var1.put("Trophy", this.trophy().save(this.registryAccess()));
      }
   }

   public void readAdditionalSaveData(CompoundTag var1) {
      super.readAdditionalSaveData(var1);
      // [qa] an empty mount saves no "Trophy" tag; parse() on the {} it reads back logs "Tried to load invalid item" on every chunk load
      this.entityData.set(TROPHY, ItemStack.parseOptional(this.registryAccess(), var1.getCompound("Trophy")));
      Direction var2 = Direction.from3DDataValue(var1.getByte("Facing"));
      this.setDirection(var2.getAxis().isHorizontal() ? var2 : Direction.NORTH);
   }

   public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity var1) {
      return new ClientboundAddEntityPacket(this, this.direction.get3DDataValue(), this.getPos());
   }

   public void recreateFromPacket(ClientboundAddEntityPacket var1) {
      super.recreateFromPacket(var1);
      Direction var2 = Direction.from3DDataValue(var1.getData());
      this.setDirection(var2.getAxis().isHorizontal() ? var2 : Direction.NORTH);
   }

   public static InteractionResult place(UseOnContext var0) {
      Player var1 = var0.getPlayer();
      Level var2 = var0.getLevel();
      Direction var3 = var0.getClickedFace();
      BlockPos var4 = var0.getClickedPos().relative(var3);
      ItemStack var5 = var0.getItemInHand();
      if (var1 == null || !var3.getAxis().isHorizontal() || !var2.mayInteract(var1, var0.getClickedPos()) || !var1.mayUseItemAt(var4, var3, var5)) {
         return InteractionResult.FAIL;
      } else if (var2.isClientSide) {
         return InteractionResult.SUCCESS;
      } else {
         TrophyMount var6 = new TrophyMount((EntityType<? extends TrophyMount>)ExpeditionContent.TROPHY_MOUNT.get(), var2);
         var6.configure(var4, var3, var5);
         if (!var6.survives()) {
            return InteractionResult.FAIL;
         } else if (!var2.addFreshEntity(var6)) {
            return InteractionResult.FAIL;
         } else {
            var6.playPlacementSound();
            var2.gameEvent(var1, GameEvent.ENTITY_PLACE, var6.position());
            var5.consume(1, var1);
            return InteractionResult.CONSUME;
         }
      }
   }

   public static final class FishItem extends Item {
      public FishItem() {
         super(new Properties().food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(3).saturationModifier(0.3F).build()));
      }

      public InteractionResult useOn(UseOnContext var1) {
         return var1.getClickedFace().getAxis().isHorizontal() ? TrophyMount.place(var1) : super.useOn(var1);
      }

      public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
         var3.add(Component.literal("Use on a wall to display this fish. Break the plaque to recover it."));
      }
   }
}
