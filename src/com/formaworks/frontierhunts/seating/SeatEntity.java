package com.formaworks.frontierhunts.seating;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * [onboard2] The invisible thing a seated hunter rides. Lives exactly as long as someone sits on it; checks its seat
 * block every tick (broken / replaced: the hunter stands up). On a seat with a back the body stays square to the seat
 * and the head turns up to 105 deg either way (like a boat, on both sides, so other players see the same pose); on a
 * swivel seat the body follows the head. Dismounting looks for clear floor next to the seat (inside a blind: the
 * cabin floor) before falling back to vanilla.
 */
public class SeatEntity extends Entity {
   private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<BlockPos> ORIGIN = SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.BLOCK_POS);

   public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
      super(type, level);
      this.noPhysics = true;
      this.noCulling = true;
      this.setNoGravity(true);
   }

   SeatEntity(Level level, BlockPos origin, SeatKind kind, Direction facing, Vec3 at) {
      this(Seats.SEAT, level);
      this.entityData.set(KIND, (byte)kind.ordinal());
      this.entityData.set(ORIGIN, origin.immutable());
      this.setPos(at);
      this.setYRot(facing.toYRot());
      this.yRotO = this.getYRot();
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder b) {
      b.define(KIND, (byte)0);
      b.define(ORIGIN, BlockPos.ZERO);
   }

   public SeatKind kind() {
      return SeatKind.byId(this.entityData.get(KIND));
   }

   public BlockPos origin() {
      return this.entityData.get(ORIGIN);
   }

   /** [hook] Hunting code: a hunter on a seat is sitting still (deer perception, rifle rest, "on foot"). */
   public static boolean isSeat(Entity e) {
      return e instanceof SeatEntity;
   }

   static SeatEntity at(Level level, BlockPos pos) {
      List<SeatEntity> l = level.getEntitiesOfClass(SeatEntity.class, new AABB(pos).inflate(0.5, 1.0, 0.5), s -> s.isAlive() && s.origin().equals(pos));
      return l.isEmpty() ? null : l.get(0);
   }

   static boolean occupied(Level level, BlockPos pos) {
      SeatEntity s = at(level, pos);
      return s != null && s.isVehicle();
   }

   // ------------------------------------------------------------------------------------------ save

   @Override
   protected void readAdditionalSaveData(CompoundTag t) {
      this.entityData.set(KIND, t.getByte("Kind"));
      this.entityData.set(ORIGIN, BlockPos.of(t.getLong("Seat")));
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag t) {
      t.putByte("Kind", this.entityData.get(KIND));
      t.putLong("Seat", this.origin().asLong());
   }

   // ------------------------------------------------------------------------------------------ life

   @Override
   public void tick() {
      super.tick();
      this.setDeltaMovement(Vec3.ZERO);
      if (this.level().isClientSide) {
         return;
      }
      BlockPos o = this.origin();
      BlockState st = this.level().getBlockState(o);
      boolean valid = st.getBlock() instanceof SeatBlock sb && sb.kind == this.kind();
      if (!valid || this.getPassengers().isEmpty() && this.tickCount > 2) {
         this.ejectPassengers();
         this.discard();
         this.release(st);
         return;
      }
      if (st.getBlock() instanceof SwivelChairBlock && !st.getValue(SwivelChairBlock.OCCUPIED) && this.isVehicle()) {
         this.level().setBlock(o, st.setValue(SwivelChairBlock.OCCUPIED, true), Block.UPDATE_ALL);
      }
      // a tower chair whose gas lift / floor changed under the sitter: follow it
      if (st.getBlock() instanceof SeatBlock sb) {
         Vec3 want = sb.seatPoint(st, o);
         if (want.distanceToSqr(this.position()) > 1.0E-4) {
            this.setPos(want);
         }
      }
   }

   /** The swivel chair shows its own top again. */
   private void release(BlockState st) {
      if (st.getBlock() instanceof SwivelChairBlock && st.getValue(SwivelChairBlock.OCCUPIED)) {
         this.level().setBlock(this.origin(), st.setValue(SwivelChairBlock.OCCUPIED, false), Block.UPDATE_ALL);
      }
   }

   @Override
   protected void removePassenger(Entity passenger) {
      super.removePassenger(passenger);
      if (!this.level().isClientSide && this.getPassengers().isEmpty()) {
         this.release(this.level().getBlockState(this.origin()));
      }
   }

   @Override
   protected boolean canAddPassenger(Entity e) {
      return this.getPassengers().isEmpty() && e instanceof Player;
   }

   @Override
   public boolean isPickable() {
      return false;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double d) {
      return d < 128.0 * 128.0;
   }

   @Override
   public Vec3 getPassengerRidingPosition(Entity e) {
      return this.position();
   }

   // ------------------------------------------------------------------------------------------ turning

   @Override
   protected void positionRider(Entity p, Entity.MoveFunction move) {
      super.positionRider(p, move);
      if (!this.kind().swivel) {
         this.square(p);
      }
   }

   @Override
   public void onPassengerTurned(Entity p) {
      if (!this.kind().swivel) {
         this.square(p);
      }
   }

   /** Body square to the seat, head within 105 deg of it (Boat.clampRotation). */
   private void square(Entity p) {
      p.setYBodyRot(this.getYRot());
      float f = Mth.wrapDegrees(p.getYRot() - this.getYRot());
      float g = Mth.clamp(f, -SeatKind.HEAD_LIMIT, SeatKind.HEAD_LIMIT);
      p.yRotO += g - f;
      p.setYRot(p.getYRot() + g - f);
      p.setYHeadRot(p.getYRot());
   }

   // ------------------------------------------------------------------------------------------ standing up

   @Override
   public Vec3 getDismountLocationForPassenger(LivingEntity p) {
      BlockPos o = this.origin();
      BlockState st = this.level().getBlockState(o);
      Direction f = st.hasProperty(SeatBlock.FACING) ? st.getValue(SeatBlock.FACING) : Direction.fromYRot(this.getYRot());
      EntityDimensions dims = p.getDimensions(Pose.STANDING);
      // in front first, then the sides, the back corners; close offsets for tight cabins (tower blinds)
      double[][] offsets = {{0, -0.95}, {-0.95, 0}, {0.95, 0}, {-0.8, -0.8}, {0.8, -0.8}, {0, -0.62}, {-0.62, 0}, {0.62, 0},
         {0, 0.95}, {-0.8, 0.8}, {0.8, 0.8}, {-0.45, -0.45}, {0.45, -0.45}, {0, 0.62}};
      for (double[] off : offsets) {
         Vec3 c = SeatBlock.local(o, f, off[0], off[1], 0.0);
         // highest clear spot with something to stand on, from just above the seat's floor down to 1 below it
         // (under a low cabin roof the first heights are blocked: keep going down until the box is clear)
         if (!this.level().hasChunkAt(BlockPos.containing(c))) {
            continue;
         }
         boolean wasFree = false;
         for (int k = 0; k <= 12; k++) {
            double y = o.getY() + (6 - k) / 10.0;
            AABB box = dims.makeBoundingBox(c.x, y, c.z);
            if (this.level().noCollision(p, box)) {
               if (!this.level().noCollision(p, box.move(0.0, -0.06, 0.0))) {
                  p.setPose(Pose.STANDING);
                  return new Vec3(c.x, y, c.z);
               }
               wasFree = true;
            } else if (wasFree) {
               break; // went down into something without finding a floor above it
            }
         }
      }
      return super.getDismountLocationForPassenger(p);
   }
}
