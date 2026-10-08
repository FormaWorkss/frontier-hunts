package com.formaworks.frontierhunts.landscape.ride;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.Entity.MoveFunction;
import net.minecraft.world.entity.Entity.MovementEmission;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import com.formaworks.frontierhunts.landscape.ride.rig.AtvFuel; // [atvfuel]
import com.formaworks.frontierhunts.landscape.ride.rig.AtvRig; // [atvfuel]
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class Atv extends VehicleEntity implements HasCustomInventoryScreen, ContainerEntity {
   public static volatile Atv.InputSource INPUT = var0 -> null;
   public static final double V_MAX = 1.25;
   public static final double V_REV = 0.18; // [polish] reverse gear top speed, ~13 km/h (was 0.22 but never reached)
   static final double REV_ACCEL = 0.0065; // [polish] net reverse acceleration (~2.6 m/s^2 on top of rolling resistance)
   static final double ACCEL = 0.03;
   static final double BRAKE = 0.03;
   static final double DRAG = 0.006;
   static final double CRUISE = 0.9;
   static final double PLAYER_HIT_DAMAGE = 10.0;
   static final double WORLD_HIT_DAMAGE = 3.0;
   static final float BREAK_DAMAGE = 80.0F;
   static final double WHEELBASE = 1.25;
   static final double G_REAL = 0.0245;
   static final double G_FALL = 0.04;
   private static final int CARGO = 27;
   private NonNullList<ItemStack> cargo = NonNullList.withSize(AtvRig.SLOTS, ItemStack.EMPTY); // [atvfuel] 27 box + 2 can cradles
   // [atvfuel] fuel tank + rear-rack rig state (logic in rig/AtvFuel, rig/AtvRig)
   public static final EntityDataAccessor<Float> DATA_FUEL = SynchedEntityData.defineId(Atv.class, EntityDataSerializers.FLOAT);
   public static final EntityDataAccessor<Integer> DATA_RIG = SynchedEntityData.defineId(Atv.class, EntityDataSerializers.INT);
   public final AtvFuel.Tank tank = new AtvFuel.Tank();
   private ResourceKey<LootTable> lootTable;
   private long lootSeed;
   private double steerAngle;
   public float throttleShown;
   public float steerShown;
   public float rpm = 0.28F;
   public boolean flooded;
   private int lerpSteps;
   private double lerpX;
   private double lerpY;
   private double lerpZ;
   private double lerpYRot;
   private float deltaYaw;
   public float bodyPitch;
   public float bodyRoll;
   public float bodyPitchO;
   public float bodyRollO;
   public float wheelSpin;
   public float wheelSpinO;
   public float susp;
   public float suspO;
   public double shownSpeed;
   private double lastX;
   private double lastZ;
   private int crashCooldown;
   public final com.formaworks.frontierhunts.landscape.ride.grime.AtvGrime grime = new com.formaworks.frontierhunts.landscape.ride.grime.AtvGrime(); // [atvgrime] mud/snow/water state
   public final com.formaworks.frontierhunts.landscape.ride.wade.AtvWater.State wade = new com.formaworks.frontierhunts.landscape.ride.wade.AtvWater.State(); // [atv2] water depth / flooding

   public Atv(EntityType<? extends Atv> var1, Level var2) {
      super(var1, var2);
      this.blocksBuilding = true;
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder builder) { // [atvfuel]
      super.defineSynchedData(builder);
      builder.define(DATA_FUEL, 0.0F);
      builder.define(DATA_RIG, 0);
   }

   @Override
   public float maxUpStep() {
      return 1.0F;
   }

   @Override
   protected double getDefaultGravity() {
      return 0.04;
   }

   @Override
   protected MovementEmission getMovementEmission() {
      return MovementEmission.EVENTS;
   }

   @Override
   public boolean isPushedByFluid() { // [atv2] a ~400 kg ATV is not carried off by a river current
      return false;
   }

   @Override
   public boolean canBeRiddenUnderFluidType(net.neoforged.neoforge.fluids.FluidType type, Entity rider) { // [atv2] only checked while the rider's head is under: then they float off
      return false;
   }

   @Override
   public boolean canBeCollidedWith() {
      return true;
   }

   @Override
   public boolean isPushable() {
      return true;
   }

   @Override
   public boolean isPickable() {
      return !this.isRemoved();
   }

   @Override
   public boolean canCollideWith(Entity var1) {
      return var1.canBeCollidedWith() && !this.isPassengerOfSameVehicle(var1);
   }

   @Override
   protected Item getDropItem() {
      return RideContent.ATV_ITEM.get();
   }

   @Override
   public ItemStack getPickResult() {
      return new ItemStack(RideContent.ATV_ITEM.get());
   }

   @Override
   protected boolean canAddPassenger(Entity var1) {
      return this.getPassengers().isEmpty() && var1 instanceof Player;
   }

   @Override
   public LivingEntity getControllingPassenger() {
      return this.getFirstPassenger() instanceof Player var1 ? var1 : null;
   }

   @Override
   public void push(Entity var1) {
      if (var1 instanceof Atv) {
         if (var1.getBoundingBox().minY < this.getBoundingBox().maxY) {
            super.push(var1);
         }
      } else if (var1.getBoundingBox().minY <= this.getBoundingBox().minY + 0.5) {
         super.push(var1);
      }
   }

   @Override
   public boolean hurt(DamageSource var1, float var2) {
      if (this.level().isClientSide || this.isRemoved()) {
         return true;
      } else if (this.isInvulnerableTo(var1)) {
         return false;
      } else {
         boolean var10000;
         label43: {
            if (var1.getEntity() instanceof Player var4 && var4.getAbilities().instabuild) {
               var10000 = true;
               break label43;
            }

            var10000 = false;
         }

         boolean var3 = var10000;
         this.setHurtDir(-this.getHurtDir());
         this.setHurtTime(10);
         this.setDamage(this.getDamage() + var2 * (var3 ? 40.0F : (float)(var1.getEntity() instanceof Player ? PLAYER_HIT_DAMAGE : WORLD_HIT_DAMAGE)));
         if (var3 || this.getDamage() > BREAK_DAMAGE) {
            if (!var3 || var1.getEntity() instanceof Player var6 && var6.isShiftKeyDown()) {
               this.destroy(var1);
            } else {
               this.discard();
            }
         }

         return true;
      }
   }

   @Override
   protected void destroy(DamageSource var1) {
      AtvRig.destroyed(this, var1); // [atvfuel] ATV item keeps its fuel; box/cans/carrier drop on removal
   }

   @Override
   public void remove(RemovalReason var1) {
      if (!this.level().isClientSide && var1.shouldDestroy()) {
         AtvRig.dropAll(this); // [atvfuel] was Containers.dropContents
      }

      super.remove(var1);
   }

   @Override
   public InteractionResult interact(Player var1, InteractionHand var2) {
      InteractionResult rig = AtvRig.interact(this, var1, var2); // [atvfuel] pour fuel, fit cargo, sneak-use opens cargo
      if (rig != null) {
         return rig;
      } else if (this.isVehicle()) {
         return InteractionResult.PASS;
      } else if (!this.level().isClientSide) {
         if (var1.startRiding(this)) {
            AtvFuel.onMount(this, var1); // [atvfuel] start, or crank on an empty tank
            return InteractionResult.CONSUME;
         } else {
            return InteractionResult.PASS;
         }
      } else {
         return InteractionResult.SUCCESS;
      }
   }

   @Override
   protected void removePassenger(Entity var1) {
      super.removePassenger(var1);
      if (!this.level().isClientSide && var1 instanceof Player) {
         this.level().playSound(null, this.getX(), this.getY(), this.getZ(), RideContent.SND_ATV_STOP.get(), SoundSource.NEUTRAL, 0.9F, 1.0F);
      }
   }

   @Override
   protected Vec3 getPassengerAttachmentPoint(Entity var1, EntityDimensions var2, float var3) {
      return new Vec3(0.0, 0.78 + com.formaworks.frontierhunts.landscape.ride.wade.AtvWater.standUp(this), -0.12).yRot(-this.getYRot() * (float) (Math.PI / 180.0)); // [atv2] rider stands on the pegs in deep water
   }

   @Override
   protected void positionRider(Entity var1, MoveFunction var2) {
      super.positionRider(var1, var2);
      var1.setYRot(var1.getYRot() + this.deltaYaw);
      var1.setYHeadRot(var1.getYHeadRot() + this.deltaYaw);
      this.clampRotation(var1);
   }

   protected void clampRotation(Entity var1) {
      var1.setYBodyRot(this.getYRot());
      float var2 = Mth.wrapDegrees(var1.getYRot() - this.getYRot());
      float var3 = Mth.clamp(var2, -110.0F, 110.0F);
      var1.yRotO += var3 - var2;
      var1.setYRot(var1.getYRot() + var3 - var2);
      var1.setYHeadRot(var1.getYRot());
   }

   @Override
   public void onPassengerTurned(Entity var1) {
      this.clampRotation(var1);
   }

   @Override
   public Vec3 getDismountLocationForPassenger(LivingEntity var1) {
      for (float var5 : new float[]{90.0F, -90.0F, 180.0F, 0.0F}) {
         Vec3 var6 = Vec3.directionFromRotation(0.0F, this.getYRot() + var5);
         Vec3 var7 = this.position().add(var6.scale(1.4));
         BlockPos var8 = BlockPos.containing(var7.x, this.getBoundingBox().maxY, var7.z);

         for (int var9 = 0; var9 >= -2; var9--) {
            BlockPos var10 = var8.offset(0, var9, 0);
            double var11 = this.level().getBlockFloorHeight(var10);
            if (DismountHelper.isBlockFloorValid(var11)) {
               Vec3 var13 = new Vec3(var7.x, (double)var10.getY() + var11, var7.z);
               if (DismountHelper.canDismountTo(this.level(), var13, var1, Pose.STANDING)) {
                  return var13;
               }
            }
         }
      }

      return super.getDismountLocationForPassenger(var1);
   }

   @Override
   public void lerpTo(double var1, double var3, double var5, float var7, float var8, int var9) {
      this.lerpX = var1;
      this.lerpY = var3;
      this.lerpZ = var5;
      this.lerpYRot = (double)var7;
      this.lerpSteps = 3;
   }

   @Override
   public double lerpTargetX() {
      return this.lerpSteps > 0 ? this.lerpX : this.getX();
   }

   @Override
   public double lerpTargetY() {
      return this.lerpSteps > 0 ? this.lerpY : this.getY();
   }

   @Override
   public double lerpTargetZ() {
      return this.lerpSteps > 0 ? this.lerpZ : this.getZ();
   }

   @Override
   public float lerpTargetYRot() {
      return this.lerpSteps > 0 ? (float)this.lerpYRot : this.getYRot();
   }

   private void tickLerp() {
      if (this.isControlledByLocalInstance()) {
         this.lerpSteps = 0;
         this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
      }

      if (this.lerpSteps > 0) {
         this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot, (double)this.getXRot());
         this.lerpSteps--;
      }
   }

   @Override
   public void tick() {
      if (this.getHurtTime() > 0) {
         this.setHurtTime(this.getHurtTime() - 1);
      }

      if (this.getDamage() > 0.0F) {
         this.setDamage(Math.max(0.0F, this.getDamage() - 0.25F));
      }

      this.bodyPitchO = this.bodyPitch;
      this.bodyRollO = this.bodyRoll;
      this.wheelSpinO = this.wheelSpin;
      this.suspO = this.susp;
      super.tick();
      this.tickLerp();
      this.deltaYaw = 0.0F;
      com.formaworks.frontierhunts.landscape.ride.wade.AtvWater.tick(this); // [atv2] water depth + engine flooding, every side
      if (this.isControlledByLocalInstance()) {
         Player var1 = this.getControllingPassenger() instanceof Player var2 ? var2 : null;
         Atv.Controls var12 = var1 == null ? null : INPUT.read(var1);
         if (var12 == null) {
            var12 = Atv.Controls.NONE;
         }

         this.drive(var12, var1 != null);
         this.move(MoverType.SELF, this.getDeltaMovement());
         this.afterMove();
      } else {
         this.setDeltaMovement(Vec3.ZERO);
      }

      this.checkInsideBlocks();
      double var11 = this.getX() - this.lastX;
      double var13 = this.getZ() - this.lastZ;
      this.lastX = this.getX();
      this.lastZ = this.getZ();
      Vec3 var5 = this.forward();
      double var6 = var11 * var5.x + var13 * var5.z;
      if (Math.abs(var6) > 2.0) {
         var6 = 0.0;
      }

      this.shownSpeed = this.shownSpeed * 0.6 + var6 * 0.4;
      AtvFuel.tick(this); // [atvfuel] burn fuel (server), misfires (client)
      if (this.level().isClientSide) {
         this.present();
      } else if (this.crashCooldown > 0) {
         this.crashCooldown--;
      }

      if (!this.level().isClientSide) {
         this.ramInto();
      }

      for (Entity var10 : this.level().getEntities(this, this.getBoundingBox().inflate(0.2, -0.01, 0.2), EntitySelector.pushableBy(this))) {
         if (!var10.hasPassenger(this) && !this.hasPassenger(var10)) {
            this.push(var10);
         }
      }

      com.formaworks.frontierhunts.landscape.ride.grime.AtvGrime.tick(this); // [atvgrime] grime simulation (server) / spray + splatter (client)
   }

   Vec3 forward() {
      float var1 = this.getYRot() * (float) (Math.PI / 180.0);
      return new Vec3((double)(-Mth.sin(var1)), 0.0, (double)Mth.cos(var1));
   }

   static Vec3 rightOf(Vec3 var0) {
      return new Vec3(-var0.z, 0.0, var0.x);
   }

   private double[] surface() {
      BlockPos var1 = BlockPos.containing(this.getX(), this.getY() - 0.25, this.getZ());
      BlockState var2 = this.level().getBlockState(var1);
      BlockState var3 = this.level().getBlockState(var1.above());
      if (var3.getBlock() instanceof SnowLayerBlock) {
         int var4 = var3.getValue(SnowLayerBlock.LAYERS);
         return var4 >= 4 ? new double[]{0.52, 0.012} : new double[]{0.62, 0.008};
      } else if (var2.is(BlockTags.ICE)) {
         return new double[]{0.1, 0.002};
      } else if (var2.is(Blocks.POWDER_SNOW) || var3.is(Blocks.POWDER_SNOW)) {
         return new double[]{0.4, 0.014};
      } else if (var2.is(BlockTags.SNOW)) {
         return new double[]{0.58, 0.01};
      } else if (var2.is(Blocks.MUD) || var2.is(Blocks.CLAY) || var2.is(Blocks.SOUL_SAND) || var2.is(Blocks.SOUL_SOIL)) {
         return new double[]{0.58, 0.013};
      } else if (var2.is(BlockTags.SAND)) {
         return new double[]{0.8, 0.009};
      } else if (var2.is(Blocks.GRAVEL)) {
         return new double[]{0.7, 0.016};
      } else if (var2.is(BlockTags.LEAVES)) {
         return new double[]{0.55, 0.014};
      } else {
         return !var2.is(BlockTags.BASE_STONE_OVERWORLD) && !var2.is(Blocks.COBBLESTONE) && !var2.is(BlockTags.STONE_BRICKS)
            ? new double[]{0.92, 0.009}
            : new double[]{1.0, 0.006};
      }
   }

   private double waterDepth() {
      BlockPos var1 = this.blockPosition();
      double var2 = 0.0;

      for (int var4 = 0; var4 < 3; var4++) {
         FluidState var5 = this.level().getFluidState(var1.above(var4));
         if (var5.isEmpty()) {
            break;
         }

         var2 = (double)((float)var4 + var5.getHeight(this.level(), var1.above(var4)));
      }

      return var2;
   }

   private void drive(Atv.Controls var1, boolean var2) {
      Vec3 var3 = this.getDeltaMovement();
      Vec3 var4 = this.forward();
      Vec3 var5 = rightOf(var4);
      double var6 = var3.x * var4.x + var3.z * var4.z;
      double var8 = var3.x * var5.x + var3.z * var5.z;
      double var10 = var3.y;
      double[] var12 = this.surface();
      double var13 = var12[0];
      double var15 = var12[1];
      double var17 = this.wade.depth; // [atv2] was waterDepth() (not relative to the ATV's bottom); flooded is set in AtvWater.tick on every side
      boolean var19 = this.onGround();
      float var20 = var1.throttle();
      float var21 = var1.brake();
      if (this.flooded) {
         var20 = 0.0F;
         var21 = 0.0F;
      }

      var20 = AtvFuel.throttle(this, var20); // [atvfuel] no drive from a dead or misfiring engine
      var21 = AtvFuel.brake(this, var21, var6); // [atvfuel] brakes still work, reverse needs the engine
      float var20raw = var20; // [atv2]
      var20 *= com.formaworks.frontierhunts.landscape.ride.wade.AtvWater.thrust(var17); // [atv2] tyres paddle in deep water

      this.throttleShown = var20raw - var21; // [atv2] what the driver asks for (sound, suspension), before water losses
      double var22 = (double)(this.bodyPitch * (float) (Math.PI / 180.0));
      double var24 = Mth.lerp(Mth.clamp(Math.abs(var6) / CRUISE, 0.0, 1.0), 34.0, 11.0) * (float) (Math.PI / 180.0);
      double var26 = (double)var1.steer() * var24;
      this.steerAngle = this.steerAngle + Mth.clamp(var26 - this.steerAngle, -0.12, 0.12);
      this.steerShown = (float)(this.steerAngle / 0.5934119410812855);
      if (var19) {
         double var28 = var13 * 0.0245 * 1.9 * Math.cos(var22);
         double var30 = 0.0;
         if (var20 > 0.0F) {
            double var32 = var6 > 0.0 ? 1.0 - Math.pow(Mth.clamp(var6 / V_MAX, 0.0, 1.0), 1.7) : 1.0;
            var30 = ACCEL * (double)var20 * var32;
            if (var6 < -0.02) {
               var30 += BRAKE * (double)var20;
            }
         }

         if (var21 > 0.0F) {
            if (var6 > 0.02) {
               var30 -= BRAKE * (double)var21;
            } else {
               // [polish] reverse gear: the engine drives the ATV backwards towards V_REV (governed like a low gear), on
               // top of covering the rolling resistance the surface takes each tick (it was 0.008 against ~0.009 of
               // grass resistance, and the stop snap below zeroed it every tick: ~0.5 km/h). Deep water weakens it.
               var30 -= RevGear.push(var6, var21, var15, DRAG, -0.0245 * Math.sin(var22) * 1.6) * com.formaworks.frontierhunts.landscape.ride.wade.AtvWater.thrust(var17);
            }
         }

         if (var1.handbrake()) {
            var30 -= Math.signum(var6) * Math.min(Math.abs(var6), 0.014);
         }

         boolean var66 = Math.abs(var30) > var28;
         var30 = Mth.clamp(var30, -var28, var28);
         double var33 = 0.0245 * Math.sin(var22) * 1.6;
         if (var20 > 0.0F && var33 > 0.0) {
            var33 = Math.min(var33, ACCEL * (double)var20 * 0.55);
         }
         boolean revDrive = var21 > 0.0F && var6 <= 0.02; // [polish] reversing under power (RevGear covers the slope)

         var30 -= var33;
         double var35 = var15 * (double)(Math.abs(var6) > 0.005 ? 1 : 0) + DRAG * var6 * var6;
         if (!var2 || var20 == 0.0F && var21 == 0.0F) {
            var35 += 0.0025;
         }

         if (Math.abs(var6) <= var35 && var20 == 0.0F && !revDrive && !(Math.abs(Math.sin(var22)) * 0.0245 * 1.6 > var13 * 0.0245 * 1.2)) {
            var6 = 0.0;
         } else {
            var6 -= Math.signum(var6) * Math.min(Math.abs(var6), var35);
         }

         var6 += var30;
         if (!var2 && Math.abs(Math.sin(var22)) * 1.6 < var13 * 1.2) {
            var6 *= 0.6;
         }

         var6 = Mth.clamp(var6, -V_REV * 1.6, V_MAX * 1.05);

         double var37 = var6 / 1.25 * Math.tan(this.steerAngle);
         double var39 = var13 * 0.0245 * 2.2 * (var1.handbrake() ? 0.45 : 1.0);
         double var41 = var39 / Math.max(Math.abs(var6), 0.06) * 1.15;
         var37 = Mth.clamp(var37, -var41, var41);
         float var43 = (float)(var37 * 180.0F / (float)Math.PI);
         this.setYRot(this.getYRot() + var43);
         this.deltaYaw = var43;
         Vec3 var44 = this.forward();
         Vec3 var45 = rightOf(var44);
         double var46 = var4.x * var6 + var5.x * var8;
         double var48 = var4.z * var6 + var5.z * var8;
         double var50 = var46 * var44.x + var48 * var44.z;
         double var52 = var46 * var45.x + var48 * var45.z;
         var6 = var50;
         var8 = Math.signum(var52) * Math.max(0.0, Math.abs(var52) - var39);
         var4 = var44;
         var5 = var45;
         if (var66 && this.level().isClientSide && this.random.nextInt(2) == 0) {
            this.spray(var13);
         }

         var10 = Math.min(var10, 0.0) - 0.04;
      } else {
         var6 *= 0.995;
         var8 *= 0.99;
         var10 -= 0.04;
      }

      var6 = com.formaworks.frontierhunts.landscape.ride.wade.AtvWater.slow(var6, var17, 1.0); // [atv2] water drag (was *0.9 per tick past 0.6 deep)
      var8 = com.formaworks.frontierhunts.landscape.ride.wade.AtvWater.slow(var8, var17, 1.6); // [atv2]
      if (this.isInWater()) {
         var10 += var17 > 0.95 ? 0.028 : 0.0;
         var10 *= 0.8;
      }

      var10 *= 0.98;
      this.setDeltaMovement(var4.x * var6 + var5.x * var8, var10, var4.z * var6 + var5.z * var8);
      float var62 = this.flooded || !var2 || !AtvFuel.running(this) // [atvfuel]
         ? 0.0F
         : (var20 > 0.0F ? (float)Mth.lerp(Mth.clamp(Math.abs(var6) / CRUISE, 0.0, 1.0), 0.62, 0.96)
            : (var21 > 0.0F && var6 < -0.01 ? (float)(0.45 + Mth.clamp(-var6 / V_REV, 0.0, 1.0) * 0.3) // [polish] engine pulls in reverse
            : (float)(0.28 + Math.min(1.0, Math.abs(var6) / CRUISE) * 0.35)));
      if (!var19 && var20 > 0.0F) {
         var62 = 1.0F;
      }

      var62 = com.formaworks.frontierhunts.landscape.ride.wade.AtvWater.rpm(this, var62, var20raw); // [atv2] labours in deep water

      this.rpm = this.rpm + (var62 - this.rpm) * (var62 > this.rpm ? 0.25F : 0.12F);
   }

   private void afterMove() {
      if (this.horizontalCollision && this.crashCooldown <= 0) {
         double var1 = this.shownSpeed;
         if (Math.abs(var1) > 0.5) {
            this.crashCooldown = 20;
            Vec3 var3 = this.forward();
            this.setDeltaMovement(var3.scale(-var1 * 0.2).add(0.0, this.getDeltaMovement().y, 0.0));
            if (this.level().isClientSide) {
               this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.NEUTRAL, 0.7F, 0.6F, false);
               PacketDistributor.sendToServer(new RidePayloads.AtvCrash((float)Math.abs(var1)));
            }
         }
      }

      if (this.crashCooldown > 0 && this.level().isClientSide) {
         this.crashCooldown--;
      }
   }

   static void crashed(RidePayloads.AtvCrash var0, IPayloadContext var1) {
      var1.enqueueWork(() -> {
         if (var1.player() instanceof ServerPlayer var2) {
            if (var2.getVehicle() instanceof Atv var6 && var6.getControllingPassenger() == var2) {
               float var7 = Math.min(1.6F, Math.max(0.0F, var0.speed()));
               if (!(var7 <= 0.5F) && var6.crashCooldown <= 0) {
                  var6.crashCooldown = 20;
                  float var5 = (var7 - 0.45F) * 30.0F;
                  var6.hurt(var6.damageSources().flyIntoWall(), var5 * 0.25F);
                  if (var7 > 0.8F) {
                     var2.hurt(var6.damageSources().flyIntoWall(), var5 * 0.3F);
                  }

                  var6.level().playSound(var2, var6.getX(), var6.getY(), var6.getZ(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.NEUTRAL, 0.8F, 0.6F);
                  return;
               }

               return;
            }
         }
      });
   }

   private void ramInto() {
      double var1 = Math.abs(this.shownSpeed);
      if (!(var1 < 0.3) && this.getControllingPassenger() instanceof Player var3) {
         Vec3 var9 = this.forward();
         AABB var5 = this.getBoundingBox().move(var9.scale(0.6)).inflate(0.1, 0.0, 0.1);

         for (LivingEntity var7 : this.level()
            .getEntitiesOfClass(
               LivingEntity.class,
               var5,
               var2 -> var2 != var3
                     && !this.hasPassenger(var2)
                     && var2.isAlive()
                     && !(var2 instanceof Player)
                     && (!(var2 instanceof TamableAnimal var4) || !var4.isTame())
                     && (!(var2 instanceof AbstractHorse var3x) || !var3x.isTamed())
            )) {
            if (var7.invulnerableTime <= 0) {
               float var8 = (float)((var1 - 0.25) * 26.0);
               var7.hurt(this.damageSources().mobAttack(var3), var8);
               var7.knockback(var1 * 1.6, -var9.x, -var9.z);
            }
         }
      }
   }

   private void present() {
      Vec3 var1 = this.forward();
      Vec3 var2 = rightOf(var1);
      double var3 = 0.62;
      double var5 = 0.48;
      double var7 = this.groundAt(var1.scale(var3).add(var2.scale(-var5)));
      double var9 = this.groundAt(var1.scale(var3).add(var2.scale(var5)));
      double var11 = this.groundAt(var1.scale(-var3).add(var2.scale(-var5)));
      double var13 = this.groundAt(var1.scale(-var3).add(var2.scale(var5)));
      float var15 = 0.0F;
      float var16 = 0.0F;
      if (!this.onGround() && (!this.isControlledByLocalInstance() || !this.verticalCollisionBelow)) {
         var15 = this.bodyPitch * 0.97F;
      } else {
         var15 = (float)(Math.atan2((var7 + var9) / 2.0 - (var11 + var13) / 2.0, var3 * 2.0) * 180.0F / (float)Math.PI);
         var16 = (float)(Math.atan2((var9 + var13) / 2.0 - (var7 + var11) / 2.0, var5 * 2.0) * 180.0F / (float)Math.PI);
      }

      var15 = Mth.clamp(var15, -38.0F, 38.0F);
      var16 = Mth.clamp(var16, -32.0F, 32.0F);
      this.bodyPitch = this.bodyPitch + (var15 - this.bodyPitch) * 0.35F;
      this.bodyRoll = this.bodyRoll + (var16 - this.bodyRoll) * 0.35F;
      float var17 = (float)((double)this.throttleShown * 0.8);
      this.susp = this.susp + (var17 - this.susp) * 0.2F;
      this.wheelSpin = this.wheelSpin + (float)(this.shownSpeed / 0.3F * 180.0F / (float)Math.PI);
      if (!this.isControlledByLocalInstance()) {
         float var18 = Mth.wrapDegrees(this.getYRot() - this.yRotO);
         double var19 = this.shownSpeed;
         float var21 = Math.abs(var19) > 0.02
            ? (float)Mth.clamp((double)(var18 * (float) (Math.PI / 180.0)) * 1.25 / var19 / 0.6, -1.0, 1.0)
            : this.steerShown * 0.9F;
         this.steerShown = this.steerShown + (var21 - this.steerShown) * 0.3F;
         float var22 = AtvFuel.running(this) ? (float)(0.28 + Math.min(1.0, Math.abs(this.shownSpeed) / CRUISE) * 0.62) : 0.0F; // [atvfuel]
         this.rpm = this.rpm + (var22 - this.rpm) * 0.2F;
      }

      if (AtvFuel.running(this) && this.random.nextInt(6) == 0) { // [atvfuel] exhaust only while the engine runs
         Vec3 var26 = this.position().add(var1.scale(-1.05)).add(var2.scale(-0.31)).add(0.0, 0.5, 0.0);
         this.level().addParticle(ParticleTypes.SMOKE, var26.x, var26.y, var26.z, -var1.x * 0.02, 0.01 + (double)this.rpm * 0.02, -var1.z * 0.02);
      }

      if (Math.abs(this.shownSpeed) > 0.35 && this.onGround() && this.random.nextInt(3) == 0) {
         BlockState var27 = this.level().getBlockState(BlockPos.containing(this.getX(), this.getY() - 0.2, this.getZ()));
         if (!var27.isAir()) {
            Vec3 var28 = this.position().add(var1.scale(-0.9)).add(var2.scale(((double)this.random.nextFloat() - 0.5) * 0.9));
            this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, var27), var28.x, var28.y + 0.1, var28.z, -var1.x * 0.1, 0.12, -var1.z * 0.1);
         }
      }
   }

   private void spray(double var1) {
      BlockState var3 = this.level().getBlockState(BlockPos.containing(this.getX(), this.getY() - 0.2, this.getZ()));
      if (!var3.isAir()) {
         Vec3 var4 = this.forward();
         Vec3 var5 = this.position().add(var4.scale(-0.8));
         this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, var3), var5.x, var5.y + 0.1, var5.z, -var4.x * 0.2, 0.18, -var4.z * 0.2);
      }
   }

   private double groundAt(Vec3 var1) {
      double var2 = this.getX() + var1.x;
      double var4 = this.getZ() + var1.z;
      double var6 = this.getY() + 1.1;
      MutableBlockPos var8 = new MutableBlockPos();

      for (int var9 = 0; var9 < 4; var9++) {
         var8.set(Mth.floor(var2), Mth.floor(var6) - var9, Mth.floor(var4));
         BlockState var10 = this.level().getBlockState(var8);
         VoxelShape var11 = var10.getCollisionShape(this.level(), var8);
         if (!var11.isEmpty()) {
            double var12 = (double)var8.getY() + var11.max(Axis.Y);
            if (var12 <= var6 + 0.01) {
               return var12 - this.getY();
            }
         }
      }

      return -1.0;
   }

   @Override
   protected void checkFallDamage(double var1, boolean var3, BlockState var4, BlockPos var5) {
      if (var3) {
         float var6 = this.fallDistance;
         this.resetFallDistance();
         if (var6 > 6.0F && !this.level().isClientSide) {
            this.hurt(this.damageSources().fall(), (var6 - 6.0F) * 1.2F);
            LivingEntity var7 = this.getControllingPassenger();
            if (var7 != null && var6 > 6.0F) {
               var7.causeFallDamage(var6 - 5.0F, 0.6F, this.damageSources().fall());
            }
         }
      } else if (var1 < 0.0) {
         this.fallDistance -= (float)var1;
      }
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag var1) {
      this.addChestVehicleSaveData(var1, this.registryAccess());
      AtvFuel.save(this, var1); // [atvfuel]
      AtvRig.save(this, var1); // [atvfuel]
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag var1) {
      this.readChestVehicleSaveData(var1, this.registryAccess());
      AtvFuel.load(this, var1); // [atvfuel]
      AtvRig.load(this, var1); // [atvfuel]
   }

   @Override
   public void openCustomInventoryScreen(Player var1) {
      AtvRig.open(this, var1); // [atvfuel] inventory key from the seat opens the rig menu
   }

   @Override
   public AbstractContainerMenu createMenu(int var1, Inventory var2, Player var3) {
      if (this.lootTable != null && var3.isSpectator()) {
         return null;
      } else {
         this.unpackChestVehicleLootTable(var2.player);
         return new com.formaworks.frontierhunts.landscape.ride.rig.RigMenu(var1, var2, this); // [atvfuel]
      }
   }

   @Override
   public void clearContent() {
      this.clearChestVehicleContent();
   }

   @Override
   public int getContainerSize() {
      return AtvRig.SLOTS; // [atvfuel]
   }

   @Override
   public boolean canPlaceItem(int slot, ItemStack stack) { // [atvfuel] hoppers: box slots need a box, cradles take cans
      return AtvRig.canPlace(this, slot, stack);
   }

   @Override
   public ItemStack getItem(int var1) {
      return this.getChestVehicleItem(var1);
   }

   @Override
   public ItemStack removeItem(int var1, int var2) {
      return this.removeChestVehicleItem(var1, var2);
   }

   @Override
   public ItemStack removeItemNoUpdate(int var1) {
      return this.removeChestVehicleItemNoUpdate(var1);
   }

   @Override
   public void setItem(int var1, ItemStack var2) {
      this.setChestVehicleItem(var1, var2);
   }

   @Override
   public SlotAccess getSlot(int var1) {
      return this.getChestVehicleSlot(var1);
   }

   @Override
   public void setChanged() {
   }

   @Override
   public boolean stillValid(Player var1) {
      return this.isChestVehicleStillValid(var1) && (AtvRig.hasBox(this) || AtvRig.hasCarrier(this)); // [atvfuel]
   }

   @Override
   public ResourceKey<LootTable> getLootTable() {
      return this.lootTable;
   }

   @Override
   public void setLootTable(ResourceKey<LootTable> var1) {
      this.lootTable = var1;
   }

   @Override
   public long getLootTableSeed() {
      return this.lootSeed;
   }

   @Override
   public void setLootTableSeed(long var1) {
      this.lootSeed = var1;
   }

   @Override
   public NonNullList<ItemStack> getItemStacks() {
      return this.cargo;
   }

   @Override
   public void clearItemStacks() {
      this.cargo = NonNullList.withSize(AtvRig.SLOTS, ItemStack.EMPTY); // [atvfuel]
   }

   @Override
   public void stopOpen(Player var1) {
   }

   public static record Controls(float throttle, float brake, float steer, boolean handbrake) {
      static final Atv.Controls NONE = new Atv.Controls(0.0F, 0.0F, 0.0F, false);
   }

   public interface InputSource {
      Atv.Controls read(Player var1);
   }
}
