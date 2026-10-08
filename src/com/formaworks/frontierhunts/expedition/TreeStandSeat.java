package com.formaworks.frontierhunts.expedition;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TreeStandSeat extends Entity {
   /** [onboard2] Lodge chair sit point: cushion top 0.50, backrest face at z 0.80 (model, facing north). */
   static final double LODGE_SEAT_Y = 0.55;
   static final double LODGE_SEAT_Z = 0.64;
   private BlockPos origin = BlockPos.ZERO;
   private Direction facing = Direction.NORTH;
   private int height = 4;
   private int seats = 1;
   private int slot;
   private boolean chair;

   public TreeStandSeat(EntityType<? extends TreeStandSeat> var1, Level var2) {
      super(var1, var2);
      this.noPhysics = true;
      this.setNoGravity(true);
   }

   protected void defineSynchedData(Builder var1) {
   }

   protected void readAdditionalSaveData(CompoundTag var1) {
      this.chair = var1.getBoolean("LodgeChair");
      this.origin = BlockPos.of(var1.getLong("Stand"));
      this.facing = Direction.from2DDataValue(var1.getInt("Facing"));
      this.height = Math.clamp((long)var1.getInt("Height"), 3, 7);
      this.seats = Math.clamp((long)var1.getInt("Seats"), 1, 2);
      this.slot = Math.clamp((long)var1.getInt("Seat"), 0, this.seats - 1);
   }

   protected void addAdditionalSaveData(CompoundTag var1) {
      var1.putBoolean("LodgeChair", this.chair);
      var1.putLong("Stand", this.origin.asLong());
      var1.putInt("Facing", this.facing.get2DDataValue());
      var1.putInt("Height", this.height);
      var1.putInt("Seats", this.seats);
      var1.putInt("Seat", this.slot);
   }

   public static boolean sitChair(ServerPlayer var0, BlockPos var1, Direction var2) {
      if (!var0.isPassenger() && !var0.isShiftKeyDown() && !(var0.distanceToSqr(Vec3.atCenterOf(var1)) > 16.0)) {
         ServerLevel var3 = var0.serverLevel();
         if (!var3.getEntitiesOfClass(
               TreeStandSeat.class, new AABB(var1).inflate(1.0), var1x -> var1x.chair && var1x.origin.equals(var1) && !var1x.getPassengers().isEmpty()
            )
            .isEmpty()) {
            ExpeditionService.message(var0, "This chair is occupied.");
            return false;
         } else {
            TreeStandSeat var4 = new TreeStandSeat((EntityType<? extends TreeStandSeat>)ExpeditionContent.STAND_SEAT.get(), var3);
            var4.chair = true;
            var4.origin = var1.immutable();
            var4.facing = var2;
            // [onboard2] was (0.5, 0.35, 0.47): the hunter sank 0.16 into the cushion (seat top 0.50) and sat 3 px clear of the
            // backrest. Now the thighs rest on the cushion and the back on the backrest (riding: feet 0.6 below this point)
            var4.setPos(point(var1, var2, 0.5, LODGE_SEAT_Y, LODGE_SEAT_Z));
            var4.setYRot(var2.toYRot());
            var3.addFreshEntity(var4);
            if (!var0.startRiding(var4)) {
               var4.discard();
               return false;
            } else {
               var0.setYRot(var2.toYRot());
               var0.setYHeadRot(var2.toYRot());
               var0.setYBodyRot(var2.toYRot());
               var0.fallDistance = 0.0F;
               ExpeditionService.message(var0, "Seated · Sneak to stand up");
               return true;
            }
         }
      } else {
         return false;
      }
   }

   public static boolean sit(ServerPlayer var0, BlockPos var1, Direction var2, int var3, int var4, int var5) {
      ServerLevel var6 = var0.serverLevel();
      if (!var0.isPassenger() && !var0.isShiftKeyDown() && var5 >= 0 && var5 < var4) {
         Vec3 var7 = point(var1, var2, var4 == 1 ? 0.5 : (var5 == 0 ? 0.1 : 0.9), (double)var3 - 0.025, 1.58);
         if (var0.distanceToSqr(var7) > 16.0) {
            return false;
         } else if (!var6.getEntitiesOfClass(
               TreeStandSeat.class,
               new AABB(var1).inflate(3.0, 10.0, 3.0),
               var2x -> var2x.origin.equals(var1) && var2x.slot == var5 && !var2x.getPassengers().isEmpty()
            )
            .isEmpty()) {
            ExpeditionService.message(var0, "This seat is occupied.");
            return false;
         } else {
            TreeStandSeat var8 = new TreeStandSeat((EntityType<? extends TreeStandSeat>)ExpeditionContent.STAND_SEAT.get(), var6);
            var8.origin = var1.immutable();
            var8.facing = var2;
            var8.height = var3;
            var8.seats = var4;
            var8.slot = var5;
            var8.setPos(var7);
            var8.setYRot(var2.toYRot());
            var6.addFreshEntity(var8);
            if (!var0.startRiding(var8)) {
               var8.discard();
               return false;
            } else {
               var0.setYRot(var2.toYRot());
               var0.setYHeadRot(var2.toYRot());
               var0.setYBodyRot(var2.toYRot());
               var0.fallDistance = 0.0F;
               ExpeditionService.message(var0, "Seated · Sneak to stand up");
               return true;
            }
         }
      } else {
         return false;
      }
   }

   public static Vec3 point(BlockPos var0, Direction var1, double var2, double var4, double var6) {
      double var8 = var2 - 0.5;
      double var10 = var6 - 0.5;

      return switch (var1) {
         case EAST -> new Vec3((double)var0.getX() + 0.5 - var10, (double)var0.getY() + var4, (double)var0.getZ() + 0.5 + var8);
         case SOUTH -> new Vec3((double)var0.getX() + 0.5 - var8, (double)var0.getY() + var4, (double)var0.getZ() + 0.5 - var10);
         case WEST -> new Vec3((double)var0.getX() + 0.5 + var10, (double)var0.getY() + var4, (double)var0.getZ() + 0.5 - var8);
         default -> new Vec3((double)var0.getX() + var2, (double)var0.getY() + var4, (double)var0.getZ() + var6);
      };
   }

   public static Vec3 local(BlockPos var0, Direction var1, Vec3 var2) {
      double var3 = var2.x - (double)var0.getX() - 0.5;
      double var5 = var2.z - (double)var0.getZ() - 0.5;
      double var7 = var2.y - (double)var0.getY();

      return switch (var1) {
         case EAST -> new Vec3(0.5 + var5, var7, 0.5 - var3);
         case SOUTH -> new Vec3(0.5 - var3, var7, 0.5 - var5);
         case WEST -> new Vec3(0.5 - var5, var7, 0.5 + var3);
         default -> new Vec3(0.5 + var3, var7, 0.5 + var5);
      };
   }

   public void tick() {
      super.tick();
      this.setDeltaMovement(Vec3.ZERO);
      if (!this.level().isClientSide) {
         BlockState var1 = this.level().getBlockState(this.origin);
         if (this.chair) {
            if (this.getPassengers().isEmpty() && this.tickCount > 2 || !(var1.getBlock() instanceof ReserveFurniture var2) || !var2.id.equals("lodge_chair")) {
               this.ejectPassengers();
               this.discard();
            }

            return;
         }

         if (this.getPassengers().isEmpty() && this.tickCount > 2
            || !var1.is((Block)ExpeditionContent.TREE_STAND.get())
            || (Integer)var1.getValue(MountedTreeStand.HEIGHT) != this.height
            || (Integer)var1.getValue(MountedTreeStand.SEATS) != this.seats) {
            this.ejectPassengers();
            this.discard();
         }
      }
   }

   public Vec3 getPassengerRidingPosition(Entity var1) {
      return this.position();
   }

   // [onboard2] in the lodge chair the body stays square to the chair (the legs used to swing through its arms when the
   // hunter looked around) and the head turns up to 105 deg either way, like a boat; tree stand seats are unchanged
   // (chair / facing are not synced: both sides read them from the lodge chair block the seat sits in)
   @Override
   protected void positionRider(Entity var1, Entity.MoveFunction var2) {
      super.positionRider(var1, var2);
      Direction d = this.lodgeFacing();
      if (d != null) {
         this.square(var1, d);
      }
   }

   @Override
   public void onPassengerTurned(Entity var1) {
      Direction d = this.lodgeFacing();
      if (d != null) {
         this.square(var1, d);
      }
   }

   private Direction lodgeFacing() {
      BlockState st = this.level().getBlockState(this.blockPosition());
      return st.getBlock() instanceof ReserveFurniture rf && rf.id.equals("lodge_chair") ? st.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING) : null;
   }

   private void square(Entity p, Direction facing) {
      float yaw = facing.toYRot();
      p.setYBodyRot(yaw);
      float f = net.minecraft.util.Mth.wrapDegrees(p.getYRot() - yaw);
      float g = net.minecraft.util.Mth.clamp(f, -105.0F, 105.0F);
      p.yRotO += g - f;
      p.setYRot(p.getYRot() + g - f);
      p.setYHeadRot(p.getYRot());
   }

   protected boolean canAddPassenger(Entity var1) {
      return this.getPassengers().isEmpty() && var1 instanceof Player;
   }

   public boolean isPickable() {
      return false;
   }

   public boolean isPushable() {
      return false;
   }

   public Vec3 getDismountLocationForPassenger(LivingEntity var1) {
      if (this.chair) {
         for (double[] var17 : new double[][]{{0.5, -0.4}, {-0.4, 0.5}, {1.4, 0.5}, {0.5, 1.4}}) {
            Vec3 var6 = point(this.origin, this.facing, var17[0], 0.0, var17[1]);
            BlockPos var18 = BlockPos.containing(var6).below();
            if (this.level().hasChunkAt(var18)
               && this.level().getBlockState(var18).isFaceSturdy(this.level(), var18, Direction.UP)
               && this.level().noCollision(var1, var1.getDimensions(Pose.STANDING).makeBoundingBox(var6))) {
               var1.setPose(Pose.STANDING);
               return var6;
            }
         }

         return Vec3.atBottomCenterOf(this.origin).add(0.0, 1.0, 0.0);
      } else {
         for (double var5 : this.seats == 1 ? new double[]{0.5} : (this.slot == 0 ? new double[]{0.1, 0.9} : new double[]{0.9, 0.1})) {
            Vec3 var7 = point(this.origin, this.facing, var5, (double)this.height + 0.13, 1.58);
            AABB var8 = var1.getDimensions(Pose.STANDING).makeBoundingBox(var7);
            AABB var9 = var8.move(0.0, -0.03, 0.0);
            if (this.level().noCollision(var1, var8) && this.level().getBlockCollisions(var1, var9).iterator().hasNext()) {
               var1.setPose(Pose.STANDING);
               return var7;
            }
         }

         for (int var10 = 0; var10 <= 2; var10++) {
            for (int var12 = -1; var12 <= 1; var12++) {
               Vec3 var14 = point(this.origin, this.facing, 0.5 + (double)var12, 0.0, 0.4 - (double)var10);
               BlockPos var16 = BlockPos.containing(var14).below();
               if (this.level().hasChunkAt(var16)
                  && this.level().getBlockState(var16).isFaceSturdy(this.level(), var16, Direction.UP)
                  && this.level().noCollision(var1, var1.getDimensions(Pose.STANDING).makeBoundingBox(var14))) {
                  var1.setPose(Pose.STANDING);
                  var1.fallDistance = 0.0F;
                  return var14;
               }
            }
         }

         return this.position().add(0.0, 0.15, 0.0);
      }
   }
}
