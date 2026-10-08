package com.formaworks.frontierhunts.landscape.ride.boat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** [1.1.0] The old lapstrake wooden rowboat: two oars, rower on the middle thwart, a passenger on the stern seat. */
public class RowBoat extends Boat implements FrontierBoat {
   static final EntityDataAccessor<Byte> DATA_INPUT = SynchedEntityData.defineId(RowBoat.class, EntityDataSerializers.BYTE);
   public static final BoatSim.Spec SPEC = new BoatSim.Spec("rowboat", 1.9, 0.166, 0.975, 0.8,
      new BoatSim.Seat[]{new BoatSim.Seat(0.0, 0.275, -0.02), new BoatSim.Seat(0.0, 0.275, -1.14)});
   private final BoatSim.State sim = new BoatSim.State();
   private final BoatPart[] parts = {new BoatPart(this, 1.9 * 0.66, 1.25F, 0.95F), new BoatPart(this, 0.0, 1.3F, 0.95F), new BoatPart(this, -1.9 * 0.66, 1.25F, 0.95F)};

   public RowBoat(EntityType<? extends Boat> type, Level level) {
      super(type, level);
      this.setId(ENTITY_COUNTER.getAndAdd(this.parts.length + 1) + 1);
   }

   @Override
   public void setId(int id) {
      super.setId(id);
      for (int i = 0; i < this.parts.length; i++) {
         this.parts[i].setId(id + i + 1);
      }
   }

   @Override
   public boolean isMultipartEntity() {
      return true;
   }

   @Override
   public net.neoforged.neoforge.entity.PartEntity<?>[] getParts() {
      return this.parts;
   }

   @Override
   public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
      return this.getBoundingBox().inflate(SPEC.halfLength() + 0.6, 0.8, SPEC.halfLength() + 0.6);
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(DATA_INPUT, (byte)0);
   }

   @Override
   public BoatSim.Spec spec() {
      return SPEC;
   }

   @Override
   public BoatSim.State sim() {
      return this.sim;
   }

   @Override
   public int inputBits() {
      return this.entityData.get(DATA_INPUT);
   }

   @Override
   public void setInputBits(int bits) {
      this.entityData.set(DATA_INPUT, (byte)bits);
   }

   @Override
   public SoundEvent bumpSound() {
      return SoundEvents.WOOD_HIT;
   }

   /** the driver's keys drive the oars here, not vanilla's paddles */
   @Override
   public void setInput(boolean left, boolean right, boolean up, boolean down) {
      this.sim.inL = left;
      this.sim.inR = right;
      this.sim.inU = up;
      this.sim.inD = down;
      super.setInput(false, false, false, false);
   }

   @Override
   public void tick() {
      if (!this.level().isClientSide && this.getControllingPassenger() == null && this.inputBits() != 0) {
         this.setInputBits(0);
      }
      int bits = BoatSim.activeBits(this, this);
      double push = BoatSim.row(this, this, bits);
      BoatSim.beforeTick(this, this, push);
      super.tick();
      BoatSim.afterTick(this, this);
      BoatPart.place(this, this.parts);
      BoatSim.looks(this, 0.0F, -this.sim.yawRate * 0.6F);
   }

   @Override
   public boolean isPushedByFluid() {
      return false;
   }

   @Override
   protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
      return BoatSim.seat(this, this, passenger);
   }

   @Override
   protected void positionRider(Entity passenger, Entity.MoveFunction move) {
      super.positionRider(passenger, move);
      BoatSim.turnRider(this, this, passenger);
   }

   @Override
   protected SoundEvent getPaddleSound() {
      return null;
   }

   /** a dropped boat lands; it does not splinter into planks and sticks */
   @Override
   protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
      if (onGround) {
         this.resetFallDistance();
      }
      super.checkFallDamage(y, onGround, state, pos);
   }

   @Override
   public Item getDropItem() {
      return BoatContent.ROWBOAT_ITEM.get();
   }
}
