package com.formaworks.frontierhunts.landscape.ride.boat;

import com.formaworks.frontierhunts.landscape.ride.rig.AtvFuelConfig;
import com.formaworks.frontierhunts.landscape.ride.rig.JerryCanItem;
import com.formaworks.frontierhunts.landscape.ride.rig.RigContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * [1.1.0] The aluminium jon boat with a tiller outboard that runs on gasoline from a jerry can (12 L tank). Sneak-use
 * opens the dry box (27 slots). Driver on the stern bench at the tiller, a passenger on the middle bench. With an empty
 * tank the boat can still be paddled, slowly.
 */
public class JonBoat extends ChestBoat implements FrontierBoat {
   static final EntityDataAccessor<Byte> DATA_INPUT = SynchedEntityData.defineId(JonBoat.class, EntityDataSerializers.BYTE);
   /** litres in the tank as shown to clients; -1 = endless (fuel not required on this server) */
   static final EntityDataAccessor<Float> DATA_FUEL = SynchedEntityData.defineId(JonBoat.class, EntityDataSerializers.FLOAT);
   public static final float TANK = 12.0F;
   static final double IDLE = 4.0E-5;
   static final double RUN = 2.0E-4;
   static final float POUR = 0.5F;
   public static final BoatSim.Spec SPEC = new BoatSim.Spec("jon_boat", 2.0, 0.085, 0.95, 0.86,
      new BoatSim.Seat[]{new BoatSim.Seat(0.3, 0.28, -1.53), new BoatSim.Seat(0.0, 0.28, -0.12)});
   private final BoatSim.State sim = new BoatSim.State();
   private final BoatPart[] parts = {new BoatPart(this, 2.0 * 0.66, 1.25F, 0.95F), new BoatPart(this, 0.0, 1.3F, 0.95F), new BoatPart(this, -2.0 * 0.66, 1.25F, 0.95F)};
   private double fuel;
   private boolean wasRunning;

   public JonBoat(EntityType<? extends Boat> type, Level level) {
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
      builder.define(DATA_FUEL, 0.0F);
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
      return SoundEvents.METAL_HIT;
   }

   public double fuel() {
      return this.fuel;
   }

   public void setFuel(double litres) {
      this.fuel = Mth.clamp(litres, 0.0, TANK);
      this.syncFuel();
   }

   /** litres as the client sees them (-1 = endless) */
   public float fuelShown() {
      return this.entityData.get(DATA_FUEL);
   }

   private void syncFuel() {
      if (!this.level().isClientSide) {
         this.entityData.set(DATA_FUEL, AtvFuelConfig.requireFuel() ? (float)this.fuel : -1.0F);
      }
   }

   private boolean creativeDriver() {
      return this.getControllingPassenger() instanceof Player p && p.getAbilities().instabuild;
   }

   /** the motor runs while someone sits at the tiller and there is gas (or gas is not needed) */
   public boolean motorRunning() {
      if (this.getControllingPassenger() == null) {
         return false;
      }
      float f = this.fuelShown();
      return f < 0.0F || f > 0.0F || this.creativeDriver();
   }

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
      if (!this.level().isClientSide) {
         if (this.getControllingPassenger() == null && this.inputBits() != 0) {
            this.setInputBits(0);
         }
         this.syncFuel();
      }
      int bits = BoatSim.activeBits(this, this);
      boolean running = this.motorRunning();
      double push = BoatSim.motor(this, this, bits, running);
      BoatSim.beforeTick(this, this, push);
      super.tick();
      BoatSim.afterTick(this, this);
      BoatPart.place(this, this.parts);
      float bowUp = (float)Mth.clamp(this.sim.speed / 0.32, 0.0, 1.0) * Math.max(0.0F, this.sim.throttle) * 6.5F;
      BoatSim.looks(this, bowUp, -this.sim.yawRate * 1.1F);
      if (!this.level().isClientSide) {
         this.burn(bits, running);
      }
   }

   private void burn(int bits, boolean running) {
      if (running && AtvFuelConfig.requireFuel() && !this.creativeDriver()) {
         double thr = (bits & BoatSim.FWD) != 0 ? 1.0 : (bits & BoatSim.BACK) != 0 ? 0.45 : 0.0;
         this.fuel = Math.max(0.0, this.fuel - (IDLE + RUN * thr) * AtvFuelConfig.fuelUse());
         if (this.fuel <= 0.0 && this.wasRunning) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), RigContent.SND_STALL.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            if (this.getControllingPassenger() instanceof Player p) {
               p.displayClientMessage(Component.translatable("message.frontierhunts.boat_out_of_gas").withStyle(ChatFormatting.GOLD), true);
            }
         }
         this.syncFuel();
      }
      this.wasRunning = running && (this.fuel > 0.0 || !AtvFuelConfig.requireFuel() || this.creativeDriver());
   }

   @Override
   protected void addPassenger(Entity passenger) {
      super.addPassenger(passenger);
      if (!this.level().isClientSide && passenger instanceof Player p && this.getControllingPassenger() == p) {
         if (this.fuel > 0.0 || !AtvFuelConfig.requireFuel() || p.getAbilities().instabuild) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), RigContent.SND_CRANK.get(), SoundSource.NEUTRAL, 1.0F, 1.15F);
         } else {
            p.displayClientMessage(Component.translatable("message.frontierhunts.boat_no_gas").withStyle(ChatFormatting.GOLD), true);
         }
      }
   }

   @Override
   public InteractionResult interact(Player player, InteractionHand hand) {
      ItemStack held = player.getItemInHand(hand);
      if (held.getItem() instanceof JerryCanItem && !player.isSecondaryUseActive()) {
         return this.pour(player, held);
      }
      return super.interact(player, hand);
   }

   private InteractionResult pour(Player p, ItemStack can) {
      boolean client = this.level().isClientSide;
      float inCan = JerryCanItem.litres(can);
      double level = client ? Math.max(0.0F, this.fuelShown()) : this.fuel;
      double room = TANK - level;
      if (inCan <= 0.0F) {
         if (!client) {
            p.displayClientMessage(Component.translatable("message.frontierhunts.jerry_can_empty").withStyle(ChatFormatting.GRAY), true);
         }
         return InteractionResult.FAIL;
      }
      if (room < 0.01) {
         if (!client) {
            p.displayClientMessage(Component.translatable("message.frontierhunts.boat_tank_full", String.format("%.0f", TANK)).withStyle(ChatFormatting.GRAY), true);
         }
         return InteractionResult.FAIL;
      }
      if (client) {
         return InteractionResult.SUCCESS;
      }
      float amount = (float)Math.min(Math.min(POUR, inCan), room);
      this.setFuel(this.fuel + amount);
      if (!p.hasInfiniteMaterials()) {
         JerryCanItem.setLitres(can, inCan - amount);
      }
      Vec3 neck = this.position().add(new Vec3(-0.38, BoatSim.WATERLINE + 0.3, -1.83).yRot(-this.getYRot() * (float)(Math.PI / 180.0)));
      this.level().playSound(null, neck.x, neck.y, neck.z, RigContent.SND_POUR.get(), SoundSource.PLAYERS, 0.8F, 0.92F + this.random.nextFloat() * 0.16F);
      if (this.level() instanceof ServerLevel sl) {
         sl.sendParticles(ParticleTypes.SPLASH, neck.x, neck.y, neck.z, 3, 0.04, 0.02, 0.04, 0.0);
      }
      p.displayClientMessage(Component.translatable("message.frontierhunts.atv_fuel_level", String.format("%.1f", this.fuel), String.format("%.0f", TANK))
         .withStyle(ChatFormatting.GOLD), true);
      return InteractionResult.SUCCESS;
   }

   @Override
   protected int getMaxPassengers() {
      return 2;
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

   @Override
   protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
      if (onGround) {
         this.resetFallDistance();
      }
      super.checkFallDamage(y, onGround, state, pos);
   }

   @Override
   public Item getDropItem() {
      return BoatContent.JON_BOAT_ITEM.get();
   }

   /** the boat item keeps the gas that was in the tank */
   @Override
   public void destroy(Item item) {
      this.kill();
      if (this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
         ItemStack stack = new ItemStack(item);
         stack.set(RigContent.FUEL.get(), (float)this.fuel);
         stack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
         this.spawnAtLocation(stack);
      }
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putDouble("Fuel", this.fuel);
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      this.fuel = Mth.clamp(tag.getDouble("Fuel"), 0.0, TANK);
   }
}
