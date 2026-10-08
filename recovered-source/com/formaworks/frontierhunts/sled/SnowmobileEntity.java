package com.formaworks.frontierhunts.sled;

import com.formaworks.frontierhunts.landscape.ride.RideContent;
import com.formaworks.frontierhunts.landscape.ride.rig.AtvFuelConfig;
import com.formaworks.frontierhunts.landscape.ride.rig.JerryCanItem;
import com.formaworks.frontierhunts.landscape.ride.rig.RigContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * [1.2.5] A snowmobile: a two-up mountain sled with a 30 L tank. It rides the same smooth snow surface as the toboggan
 * ({@link SledPhysics}, in motor mode), so it glides over the block steps and up mountainsides instead of climbing
 * blocks: the track drives it, the skis steer it, the brake lever stops it and then backs it up. It runs on the same
 * gasoline as the ATV: pour a jerry can into it. Creative drivers and servers with {@code requireFuel=false} never need
 * fuel.
 *
 * <p>Like the toboggan, the driver's game runs the ride and the server takes the moves (with the same sanity checks).
 * The server burns the fuel from what it sees of the ride and syncs the level.
 */
public class SnowmobileEntity extends SledEntity {
   public static final float TANK = 30.0F;
   static final float LOW = 0.15F;
   static final float SPUTTER = 0.6F;
   // litres per tick: idle ~6 h on a tank, flat out about 55 minutes
   static final double IDLE = 6.0E-5;
   static final double RUN = 3.6E-4;
   static final double LOAD = 1.6E-4;
   static final float POUR_STEP = 0.5F;
   static final int FREE = 1;

   static final EntityDataAccessor<Float> DATA_FUEL = SynchedEntityData.defineId(SnowmobileEntity.class, EntityDataSerializers.FLOAT);
   static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(SnowmobileEntity.class, EntityDataSerializers.INT);
   /** [1.2.5] how snowed-up it is, 0..1 (packed on riding through powder, settles when it snows, melts off over minutes) */
   static final EntityDataAccessor<Float> DATA_SNOW = SynchedEntityData.defineId(SnowmobileEntity.class, EntityDataSerializers.FLOAT);

   /** client-side hook (engine sound, set by the client code) */
   public static java.util.function.Consumer<SnowmobileEntity> clientTick = e -> {
   };

   private double exact = -1.0;
   private boolean dryHandled;
   private double lastX = Double.NaN, lastZ;
   private int hits, hitTimer;
   /** client: ticks left of a fuel-starved misfire */
   public int misfire;
   /** client: throttle as the engine sees it (0..1), eased; and the engine speed 0..1 */
   public float throttleShown, rpm;
   /** client: the steering as drawn (last tick), and how far the track has run (metres), for the renderer */
   public float steerO;
   public double trackPos, trackPosO;

   public SnowmobileEntity(EntityType<? extends SnowmobileEntity> type, Level level) {
      super(type, level);
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder b) {
      super.defineSynchedData(b);
      b.define(DATA_FUEL, 0.0F);
      b.define(DATA_FLAGS, 0);
      b.define(DATA_SNOW, 0.0F);
   }

   private float coat;

   /** 0..1: how much snow is caked on it (synced) */
   public float snowCoat() {
      return this.entityData.get(DATA_SNOW);
   }

   public void setSnowCoat(float v) {
      this.coat = Mth.clamp(v, 0.0F, 1.0F);
      float q = Math.round(this.coat * 100.0F) / 100.0F;
      if (Math.abs(q - this.snowCoat()) >= 0.01F) {
         this.entityData.set(DATA_SNOW, q);
      }
   }

   // ============================================================================================ fuel

   public float fuel() {
      return this.entityData.get(DATA_FUEL);
   }

   public float fraction() {
      return Mth.clamp(this.fuel() / TANK, 0.0F, 1.0F);
   }

   public double exactFuel() {
      return Math.max(0.0, this.exact);
   }

   /** server: set the level and sync a 0.01 L copy */
   public void setFuel(double litres) {
      boolean wasDry = this.exact <= 0.0;
      this.exact = Mth.clamp(Double.isFinite(litres) ? litres : 0.0, 0.0, TANK);
      float q = (float)(Math.round(this.exact * 100.0) / 100.0);
      if (this.exact > 0.0 && q <= 0.0F) {
         q = 0.01F;
      }
      if (Math.abs(q - this.fuel()) >= 0.005F || q == 0.0F && this.fuel() != 0.0F) {
         this.entityData.set(DATA_FUEL, q);
      }
      if (this.exact > 0.0) {
         this.dryHandled = false;
         if (wasDry && !this.level().isClientSide && this.rider() != null) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), RideContent.SND_ATV_START.get(), SoundSource.NEUTRAL, 1.0F, 1.25F);
         }
      }
   }

   /** fuel doesn't matter: a creative or spectator driver, or a server that doesn't require it */
   public boolean exempt() {
      if ((this.entityData.get(DATA_FLAGS) & FREE) != 0) {
         return true;
      }
      Player p = this.rider();
      return p != null && (p.isCreative() || p.isSpectator());
   }

   /** the engine is on: a driver, and fuel (or exempt); the QA rider always runs */
   public boolean running() {
      if (this.qaInput != null && this.rider() == null) {
         return true;
      }
      return this.rider() != null && (this.fuel() > 0.0F || this.exempt());
   }

   // ============================================================================================ riding

   @Override
   protected boolean canAddPassenger(Entity e) {
      return e instanceof Player && this.getPassengers().size() < 2;
   }

   @Override
   public LivingEntity getControllingPassenger() {
      return this.getFirstPassenger() instanceof Player p ? p : null;
   }

   @Override
   public Player rider() {
      return this.getFirstPassenger() instanceof Player p ? p : null;
   }

   @Override
   protected void positionRider(Entity e, Entity.MoveFunction move) {
      if (!this.hasPassenger(e)) {
         return;
      }
      int seat = this.getPassengers().indexOf(e);
      double yaw = Math.toRadians(this.getYRot());
      double by = this.level().isClientSide && !Double.isNaN(this.visY) ? this.visY : this.getY();
      // [1.2.5] the seat as the machine is drawn: pitched and rolled with the snow about the middle of the track, so on
      // a climb or a descent the rider stays sat where they sit on the flat (a sitting player's hips are 0.6 above its
      // feet; the seat top is about 0.78 for the driver, 0.81 behind)
      double back = seat == 0 ? -0.15 : -0.8;
      double h = (seat == 0 ? 0.78 : 0.81) - PIVOT;
      double a = this.level().isClientSide ? Math.toRadians(-this.visPitch) : 0.0;
      double b = this.level().isClientSide ? Math.toRadians(-this.visRoll) : 0.0;
      double x1 = -h * Math.sin(b), y1 = h * Math.cos(b), z1 = back;
      double y2 = y1 * Math.cos(a) - z1 * Math.sin(a), z2 = y1 * Math.sin(a) + z1 * Math.cos(a);
      double wx = x1 * Math.cos(yaw) - z2 * Math.sin(yaw), wz = x1 * Math.sin(yaw) + z2 * Math.cos(yaw);
      move.accept(e, this.getX() + wx, by + PIVOT + y2 - 0.6, this.getZ() + wz);
      if (this.yawStep != 0.0F) {
         e.setYRot(e.getYRot() + this.yawStep);
         e.setYHeadRot(e.getYHeadRot() + this.yawStep);
      }
      if (seat == this.getPassengers().size() - 1) {
         this.yawStep = 0.0F;
      }
      e.fallDistance = 0.0F;
   }

   /** the point the drawing pitches and rolls about (height above the snow) */
   public static final double PIVOT = 0.25;

   @Override
   public Vec3 getDismountLocationForPassenger(LivingEntity e) {
      double yaw = Math.toRadians(this.getYRot() + 90.0);
      return new Vec3(this.getX() - Math.sin(yaw) * 1.3, this.getY() + 0.3, this.getZ() + Math.cos(yaw) * 1.3);
   }

   @Override
   protected void addPassenger(Entity e) {
      boolean first = this.getPassengers().isEmpty();
      super.addPassenger(e);
      if (first && e instanceof Player p && !this.level().isClientSide) {
         this.onMount(p);
      }
   }

   private void onMount(Player p) {
      if (this.running()) {
         this.level().playSound(null, this.getX(), this.getY(), this.getZ(), RideContent.SND_ATV_START.get(), SoundSource.NEUTRAL, 1.0F, 1.25F);
      } else {
         this.level().playSound(null, this.getX(), this.getY(), this.getZ(), RigContent.SND_CRANK.get(), SoundSource.NEUTRAL, 1.0F, 1.15F);
         p.displayClientMessage(Component.translatable("message.frontierhunts.snowmobile_no_fuel").withStyle(ChatFormatting.GOLD), true);
      }
   }

   @Override
   public InteractionResult interact(Player player, InteractionHand hand) {
      ItemStack held = player.getItemInHand(hand);
      if (held.getItem() instanceof JerryCanItem) {
         return this.pour(player, held);
      }
      // [1.2.5] a second rider climbs on behind the driver (the toboggan only ever takes one)
      if (!player.isSecondaryUseActive() && this.rider() != null && this.getPassengers().size() < 2 && !this.hasPassenger(player)) {
         if (!this.level().isClientSide) {
            player.setYRot(this.getYRot());
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
         }
         return InteractionResult.SUCCESS;
      }
      if (player.isSecondaryUseActive()) {
         // [1.2.8] no sneak + use pick-up: you get off by sneaking, so a use-click right after made it vanish into the pack
         // (or out of the world in creative). Like the ATV, it comes back as an item only when it's broken.
         return InteractionResult.PASS;
      }
      return super.interact(player, hand);
   }

   private InteractionResult pour(Player p, ItemStack can) {
      boolean client = this.level().isClientSide;
      float inCan = JerryCanItem.litres(can);
      double level = client ? this.fuel() : this.exactFuel();
      double room = TANK - level;
      if (inCan <= 0.0F) {
         if (!client) {
            p.displayClientMessage(Component.translatable("message.frontierhunts.jerry_can_empty").withStyle(ChatFormatting.GRAY), true);
         }
         return InteractionResult.FAIL;
      }
      if (room < 0.01) {
         if (!client) {
            p.displayClientMessage(Component.translatable("message.frontierhunts.snowmobile_tank_full", String.format("%.0f", TANK))
               .withStyle(ChatFormatting.GRAY), true);
         }
         return InteractionResult.FAIL;
      }
      if (client) {
         return InteractionResult.SUCCESS;
      }
      float amount = (float)Math.min(Math.min(POUR_STEP, inCan), room);
      this.setFuel(level + amount);
      if (!p.hasInfiniteMaterials()) {
         JerryCanItem.setLitres(can, inCan - amount);
      }
      Vec3 neck = this.fillerNeck();
      this.level().playSound(null, neck.x, neck.y, neck.z, RigContent.SND_POUR.get(), SoundSource.PLAYERS, 0.8F, 0.92F + this.random.nextFloat() * 0.16F);
      if (this.level() instanceof ServerLevel sl) {
         sl.sendParticles(ParticleTypes.SPLASH, neck.x, neck.y, neck.z, 3, 0.04, 0.02, 0.04, 0.0);
      }
      p.displayClientMessage(Component.translatable("message.frontierhunts.snowmobile_fuel_level",
         String.format("%.1f", this.exactFuel()), String.format("%.0f", TANK)).withStyle(ChatFormatting.GOLD), true);
      return InteractionResult.SUCCESS;
   }

   /** the filler cap: on the tank under the front of the seat */
   public Vec3 fillerNeck() {
      double r = Math.toRadians(this.getYRot());
      return this.position().add(-Math.sin(r) * 0.35, 0.82, Math.cos(r) * 0.35);
   }

   @Override
   public boolean hurt(DamageSource src, float amount) {
      if (this.level().isClientSide || this.isRemoved()) {
         return true;
      }
      if (src.getEntity() instanceof Player p) {
         if (this.hasPassenger(p)) {
            return false; // [1.2.8] the driver's own swing (looking down off the seat) never wrecks the machine under them
         }
         if (p.getAbilities().instabuild && !p.isShiftKeyDown()) {
            return false; // [1.2.8] creative: sneak + hit removes it, a stray click doesn't
         }
         if (!p.getAbilities().instabuild) {
            // it takes a few blows to wreck a snowmobile back into its item
            this.hits++;
            this.hitTimer = 60;
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), net.minecraft.sounds.SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.25F, 1.6F);
            if (this.hits < 4) {
               return true;
            }
            this.spawnAtLocation(this.asItem());
         }
         this.ejectPassengers();
         this.discard();
         return true;
      }
      return false;
   }

   @Override
   protected ItemStack asItem() {
      ItemStack st = new ItemStack(SledContent.SNOWMOBILE_ITEM.get());
      st.set(RigContent.FUEL.get(), (float)(Math.round(this.exactFuel() * 100.0) / 100.0));
      return st;
   }

   /** a freshly placed snowmobile: the fuel the item carries, or the configured starter splash */
   public void fromItem(ItemStack stack) {
      Float f = stack.get(RigContent.FUEL.get());
      this.setFuel(f != null ? f : TANK * AtvFuelConfig.startFraction());
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
      this.setSnowCoat(tag.getFloat("FhSnow"));
      this.setFuel(tag.contains("FhFuel") ? tag.getFloat("FhFuel") : TANK * AtvFuelConfig.startFraction());
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
      tag.putFloat("FhFuel", (float)this.exactFuel());
      tag.putFloat("FhSnow", this.coat);
   }

   // ============================================================================================ the ride

   @Override
   protected void control(SledPhysics s, Player rider, float forward, float side) {
      s.motor = true;
      if (this.running() && this.misfire == 0) {
         s.throttle = Mth.clamp(forward, -1.0F, 1.0F);
      } else {
         // a dead engine: the brake still works, nothing drives the track
         s.throttle = forward < 0.0F && s.along() > 0.03 ? forward : 0.0F;
      }
   }

   @Override
   public void tick() {
      this.steerO = this.steer;
      this.trackPosO = this.trackPos;
      super.tick();
      if (this.hitTimer > 0 && --this.hitTimer == 0) {
         this.hits = 0;
      }
      if (this.level().isClientSide) {
         this.clientEngine();
         clientTick.accept(this);
      } else {
         this.burn();
      }
   }

   /** [1.2.5] the snow coat: packed on riding through snow (more in deep powder), settled on while it snows, melting
    * off over minutes (slowly in the cold, quickly in the warm, at once in water) */
   private void weather(double v) {
      float c = this.coat;
      net.minecraft.core.BlockPos at = this.blockPosition();
      double grip = this.grip();
      if (grip < 0.2 && this.onGround() && v > 0.05) {
         net.minecraft.world.level.block.state.BlockState st = this.level().getBlockState(at);
         int layers = st.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock ? st.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) : 2;
         c += (float)(Math.min(v, 1.6) * 0.0028 * (0.6 + 0.4 * layers / 8.0) * (1.1 - c));
      }
      var biome = this.level().getBiome(at).value();
      boolean cold = biome.coldEnoughToSnow(at);
      boolean snowing = cold && this.level().isRaining() && this.level().canSeeSky(at.above());
      if (snowing) {
         c += 0.0002F * (1.05F - c);
      } else if (c > 0.0F) {
         c -= cold ? 1.0F / (20 * 60 * 8) : biome.getBaseTemperature() < 0.8F ? 1.0F / (20 * 60 * 3) : 1.0F / (20 * 60);
      }
      if (this.isInWater()) {
         c -= 0.02F;
      }
      this.setSnowCoat(c);
   }

   private void burn() {
      if (this.exact < 0.0) {
         this.setFuel(TANK * AtvFuelConfig.startFraction());
      }
      int flags = AtvFuelConfig.requireFuel() ? 0 : FREE;
      if (this.entityData.get(DATA_FLAGS) != flags) {
         this.entityData.set(DATA_FLAGS, flags);
      }
      double v = Double.isNaN(this.lastX) ? 0.0 : Math.hypot(this.getX() - this.lastX, this.getZ() - this.lastZ);
      this.lastX = this.getX();
      this.lastZ = this.getZ();
      this.weather(v);
      Player d = this.rider();
      if (d == null || this.exempt()) {
         return;
      }
      if (this.exact > 0.0) {
         double thr = Mth.clamp(d.zza, 0.0F, 1.0F);
         double rate = IDLE + RUN * Math.pow(Math.min(v / 1.5, 1.4), 1.2) + LOAD * thr;
         this.setFuel(this.exact - rate * AtvFuelConfig.fuelUse());
      }
      if (this.exact <= 0.0 && !this.dryHandled) {
         this.dryHandled = true;
         this.level().playSound(null, this.getX(), this.getY(), this.getZ(), RigContent.SND_STALL.get(), SoundSource.NEUTRAL, 1.0F, 1.15F);
         d.displayClientMessage(Component.translatable("message.frontierhunts.snowmobile_out_of_fuel").withStyle(ChatFormatting.RED), true);
      }
   }

   private void shed() {
      float c = this.snowCoat();
      double sp = Math.hypot(this.getX() - this.xo, this.getZ() - this.zo);
      if (c > 0.12F && this.random.nextFloat() < c * (0.04F + (float)Math.min(1.0, sp) * 0.25F)) {
         double r = Math.toRadians(this.getYRot());
         double along = (this.random.nextDouble() - 0.4) * 2.6, side = (this.random.nextDouble() - 0.5) * 0.9;
         double px = this.getX() - Math.sin(r) * along + Math.cos(r) * side, pz = this.getZ() + Math.cos(r) * along + Math.sin(r) * side;
         this.level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SNOW_BLOCK.defaultBlockState()), px,
            this.getY() + 0.3 + this.random.nextDouble() * 0.5, pz, 0.0, 0.0, 0.0);
      }
   }

   private void clientEngine() {
      this.shed();
      double r = Math.toRadians(this.getYRot());
      double along = this.isControlledByLocalInstance() ? this.phys.along()
         : (this.getX() - this.xo) * -Math.sin(r) + (this.getZ() - this.zo) * Math.cos(r);
      // the track runs at the ground speed, faster when it spins under power
      this.trackPos += along + (this.isControlledByLocalInstance() ? this.phys.drive * 0.08 : 0.0);
      if (!this.isControlledByLocalInstance()) {
         this.steer *= 0.8F;
      }
      if (this.misfire > 0) {
         this.misfire--;
      }
      boolean on = this.running();
      Player d = this.rider();
      float thr = on && d != null ? Math.max(0.0F, d.zza) : 0.0F;
      if (this.misfire > 0) {
         thr = 0.0F;
      }
      this.throttleShown += (thr - this.throttleShown) * (thr > this.throttleShown ? 0.35F : 0.2F);
      double sp = this.isControlledByLocalInstance() ? this.speed() : Math.hypot(this.getX() - this.xo, this.getZ() - this.zo);
      // a CVT holds the engine near its power peak under throttle; off the throttle it falls back toward idle
      float target = !on ? 0.0F : 0.14F + this.throttleShown * 0.58F + (float)Math.min(1.0, sp / 1.6) * (0.12F + 0.16F * this.throttleShown);
      this.rpm += (target - this.rpm) * (target > this.rpm ? 0.18F : 0.08F);
      if (on && !this.exempt() && this.fuel() < SPUTTER && this.misfire == 0) {
         float severity = 1.0F - this.fuel() / SPUTTER;
         if (this.random.nextFloat() < 0.02F + 0.08F * severity) {
            this.misfire = 3 + this.random.nextInt(4 + (int)(severity * 6.0F));
            Vec3 pipe = this.exhaust();
            this.level().playLocalSound(pipe.x, pipe.y, pipe.z, RigContent.SND_SPUTTER.get(), SoundSource.NEUTRAL, 0.7F + 0.3F * severity,
               1.05F + this.random.nextFloat() * 0.2F, false);
         }
      }
   }

   /** the exhaust, low on the right under the hood */
   public Vec3 exhaust() {
      double r = Math.toRadians(this.getYRot());
      double fx = -Math.sin(r), fz = Math.cos(r);
      return this.position().add(fx * 0.55 - fz * 0.4, 0.3, fz * 0.55 + fx * 0.4);
   }

   @Override
   protected void effects() {
      super.effects();
      SledPhysics s = this.phys;
      double rad = Math.toRadians(this.getYRot());
      double fx = -Math.sin(rad), fz = Math.cos(rad);
      boolean snow = this.grip() < 0.2;
      // the track throws a rooster tail of snow up behind it when it digs in
      if (s.ground && s.drive > 0.15 && snow) {
         int n = (int)(1 + s.drive * 4.0);
         BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState());
         for (int i = 0; i < n; i++) {
            double side = (this.random.nextDouble() - 0.5) * 0.5;
            double px = this.getX() - fx * 1.45 - fz * side, pz = this.getZ() - fz * 1.45 + fx * side;
            double kick = 0.12 + s.drive * 0.22 * this.random.nextDouble();
            this.level().addParticle(dust, px, this.getY() + 0.2, pz, -fx * kick + s.vx * 0.5, 0.12 + 0.2 * s.drive * this.random.nextDouble(),
               -fz * kick + s.vz * 0.5);
         }
      }
      // two-stroke smoke: a wisp at idle, a puff when it's given the throttle
      if (this.running() && this.random.nextFloat() < 0.12F + 0.35F * this.throttleShown) {
         Vec3 p = this.exhaust();
         this.level().addParticle(this.throttleShown > 0.6F ? ParticleTypes.SMOKE : ParticleTypes.WHITE_SMOKE, p.x, p.y, p.z,
            -fx * 0.03 + s.vx * 0.6, 0.02, -fz * 0.03 + s.vz * 0.6);
      }
   }
}
