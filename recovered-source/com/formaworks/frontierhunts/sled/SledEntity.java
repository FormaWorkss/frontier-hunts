package com.formaworks.frontierhunts.sled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * [1.1.8, 1.2.2 rebuilt] A wooden toboggan. Sit on it and it runs downhill on snow - fast - steered with left/right,
 * braked with back, pushed along on the flat with forward. On grass or dirt it drags to a stop. Sneak + use an empty sled
 * to pick it up. (Hauling game on it was taken out in 1.2.2.)
 *
 * <p>The ride itself is {@link SledPhysics}: a body on a smooth snow surface, not a mob walking over blocks. The rider's
 * game runs it (as a boat does) and the server takes the rider's positions as they are, so nothing on the server ever
 * pulls it back. Unridden it stays put on the snow.
 */
public class SledEntity extends Entity {
   private double speed;
   private int lerpSteps;
   private double lx, ly, lz, lyaw;
   private int soundTick;

   public SledEntity(EntityType<? extends SledEntity> type, Level level) {
      super(type, level);
      this.blocksBuilding = true;
      // [1.2.3] it carries its own gravity (SledPhysics); and a server must never take a sled in the air for a player
      // flying (it kicks "flying is not enabled" after a few seconds of that)
      this.setNoGravity(true);
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder b) {
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   public boolean isPickable() {
      return !this.isRemoved();
   }

   @Override
   public boolean canBeCollidedWith() {
      return false;
   }

   @Override
   public ItemStack getPickResult() {
      return this.asItem();
   }

   /** [1.2.5] the item this turns back into when picked up */
   protected ItemStack asItem() {
      return new ItemStack(SledContent.SLED_ITEM.get());
   }

   // ============================================================================================ riders and cargo

   public Player rider() {
      for (Entity e : this.getPassengers()) {
         if (e instanceof Player p) {
            return p;
         }
      }
      return null;
   }

   @Override
   protected boolean canAddPassenger(Entity e) {
      return e instanceof Player && this.getPassengers().isEmpty();
   }

   @Override
   public LivingEntity getControllingPassenger() {
      return this.rider();
   }

   @Override
   protected void positionRider(Entity e, Entity.MoveFunction move) {
      if (!this.hasPassenger(e)) {
         return;
      }
      double yaw = Math.toRadians(this.getYRot());
      double fx = -Math.sin(yaw), fz = Math.cos(yaw);
      double by = this.level().isClientSide && !Double.isNaN(this.visY) ? this.visY : this.getY();
      // the deck tilts with the snow: the seat, a little ahead of the middle, rides higher or lower with the pitch
      double tilt = this.level().isClientSide ? Math.sin(Math.toRadians(this.visPitch)) : 0.0;
      // [1.2.2] sat down in the sled, the curl a stride ahead of you
      move.accept(e, this.getX() + fx * 0.3, by + 0.06 + tilt * 0.3, this.getZ() + fz * 0.3);
      if (this.yawStep != 0.0F) {
         e.setYRot(e.getYRot() + this.yawStep);
         e.setYHeadRot(e.getYHeadRot() + this.yawStep);
         this.yawStep = 0.0F;
      }
      e.fallDistance = 0.0F;
   }

   @Override
   public Vec3 getDismountLocationForPassenger(LivingEntity e) {
      double yaw = Math.toRadians(this.getYRot() + 90.0);
      return new Vec3(this.getX() - Math.sin(yaw) * 1.2, this.getY() + 0.2, this.getZ() + Math.cos(yaw) * 1.2);
   }

   @Override
   public InteractionResult interact(Player player, InteractionHand hand) {
      if (player.isSecondaryUseActive()) {
         if (this.getPassengers().isEmpty()) {
            if (!this.level().isClientSide) {
               if (!player.getAbilities().instabuild) {
                  ItemStack st = this.asItem();
                  if (!player.addItem(st)) {
                     this.spawnAtLocation(st);
                  }
               }
               this.discard();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
         }
         return InteractionResult.PASS;
      }
      if (this.rider() != null) {
         return InteractionResult.PASS;
      }
      if (!this.level().isClientSide) {
         player.setYRot(this.getYRot());
         return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
      }
      return InteractionResult.SUCCESS;
   }

   @Override
   public boolean hurt(DamageSource src, float amount) {
      if (this.level().isClientSide || this.isRemoved()) {
         return true;
      }
      if (src.getEntity() instanceof Player p) {
         if (!p.getAbilities().instabuild) {
            this.spawnAtLocation(this.asItem());
         }
         this.ejectPassengers();
         this.discard();
         return true;
      }
      return false;
   }

   // ============================================================================================ physics

   /** [1.2.2] the ride (see SledPhysics) */
   protected final SledPhysics phys = new SledPhysics();
   private boolean drove;

   @Override
   public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
      this.lx = x;
      this.ly = y;
      this.lz = z;
      this.lyaw = yaw;
      this.lerpSteps = 3;
   }

   /**
    * [1.2.2] the server takes the rider's moves as they are. It used to re-run each move through its own collision,
    * which doesn't know the sled rides on the snow, and any disagreement put the sled back where it was.
    */
   @Override
   public void move(MoverType type, Vec3 d) {
      if (type == MoverType.PLAYER && !this.level().isClientSide && this.rider() != null) {
         // [1.2.5] ... after a sanity check: a move no sled could make is refused, and the server's usual correction
         // puts the rider back
         if (this.validMove(this.position(), d)) {
            this.setPos(this.getX() + d.x, this.getY() + d.y, this.getZ() + d.z);
         } else {
            this.refused++;
         }
         return;
      }
      super.move(type, d);
   }

   /** [1.2.5] moves the server has refused (tests, logs), and why the last one was */
   public int refused;
   public String refusedWhy = "";
   /** the height change of the last accepted move (the server's view of the vertical speed) */
   private double lastDy;

   /**
    * [1.2.5] the server's check on a move the rider's game sends: could the ride have made it? Generous, because a
    * refused move throws the rider back (which is what made the sled stutter before 1.2.4), but it stops the cheats a
    * trusted vehicle opens up:
    * <ul>
    * <li>no faster than the ride's top speed (with room for a late packet);</li>
    * <li>no climbing into the sky: it only rises near the ground (a launch off a crest, a bank);</li>
    * <li>no passing through walls: the rider's body may not cross or end in solid rock, wood or the like (snow,
    * leaves, plants and water don't count).</li>
    * </ul>
    */
   public boolean validMove(Vec3 from, Vec3 d) {
      if (!Double.isFinite(d.x) || !Double.isFinite(d.y) || !Double.isFinite(d.z)) {
         this.refusedWhy = "not a number";
         return false;
      }
      double h = Math.hypot(d.x, d.z);
      if (h > SledPhysics.MAX * 1.5 || d.y > 2.8 || d.y < -6.0) {
         this.refusedWhy = "too far";
         return false;
      }
      Vec3 to = from.add(d);
      // the highest ground within two blocks: on a steep face the sled rides the high side, well above the column
      // under its middle
      double top = Double.NaN;
      for (int ox = -2; ox <= 2; ox++) {
         for (int oz = -2; oz <= 2; oz++) {
            double c = this.terrain.top(Mth.floor(to.x) + ox, Mth.floor(to.z) + oz, to.y + 1.0);
            if (!Double.isNaN(c) && (Double.isNaN(top) || c > top)) {
               top = c;
            }
         }
      }
      // well clear of the ground it can only be flying off a crest or falling, and then it moves as a thrown body does:
      // each tick it rises less (or falls faster) than the last, until it falls flat out. Hovering or climbing up there
      // is refused
      if (!Double.isNaN(top) && to.y - top > 4.0 && d.y > -2.4 && d.y > this.lastDy - SledPhysics.G * 0.5) {
         this.refusedWhy = String.format("in the air %.2f above the ground (%.2f) by %.2f after %.2f", to.y - top, top, d.y, this.lastDy);
         this.lastDy = Math.min(this.lastDy - SledPhysics.G, 0.0);
         return false;
      }
      int n = Math.max(1, (int)Math.ceil(h / 0.4));
      for (int i = 1; i <= n; i++) {
         double t = (double)i / n;
         double x = from.x + d.x * t, y = from.y + d.y * t, z = from.z + d.z * t;
         if (this.wallAt(x, y + 0.9, z) && this.wallAt(x, y + 1.6, z)) {
            this.refusedWhy = String.format("through %s at %.2f %.2f %.2f", this.level().getBlockState(this.check), x, y + 0.9, z);
            return false;
         }
      }
      this.lastDy = d.y;
      return true;
   }

   private final BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();

   /** solid ground or a wall at this point: a full solid block (not snow layers, powder snow or leaves) */
   private boolean wallAt(double x, double y, double z) {
      this.check.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
      BlockState s = this.level().getBlockState(this.check);
      if (s.isAir() || s.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock || s.is(Blocks.POWDER_SNOW) || s.is(BlockTags.LEAVES)) {
         return false;
      }
      return s.isCollisionShapeFullBlock(this.level(), this.check);
   }

   /** how far the sled turned this tick: the rider's view turns with it */
   protected float yawStep;

   @Override
   public void tick() {
      float yaw0 = this.getYRot();
      super.tick();
      boolean ridden = this.rider() != null || this.qaInput != null;
      if (this.isControlledByLocalInstance()) {
         this.lerpSteps = 0;
         this.drive(ridden);
      } else {
         this.drove = false;
         if (this.lerpSteps > 0) {
            double f = 1.0 / this.lerpSteps;
            this.setPos(this.getX() + (this.lx - this.getX()) * f, this.getY() + (this.ly - this.getY()) * f, this.getZ() + (this.lz - this.getZ()) * f);
            this.setYRot(this.getYRot() + (float)Mth.wrapDegrees(this.lyaw - this.getYRot()) * (float)f);
            this.lerpSteps--;
         }
      }
      if (this.level().isClientSide) {
         this.visual();
      }
      this.yawStep = Mth.wrapDegrees(this.getYRot() - yaw0);
      this.checkInsideBlocks();
      for (Entity e : this.getPassengers()) {
         e.fallDistance = 0.0F;
      }
   }

   /** how slippery the ground under the runners is: 0 = ice, about 0.02 = snow, 1 = bare earth */
   public double grip() {
      return this.terrain.grip(Mth.floor(this.getX()), Mth.floor(this.getZ()), this.getY());
   }

   /** the world as the sled sees it */
   protected final SledPhysics.Terrain terrain = new SledPhysics.Terrain() {
      private final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

      @Override
      public double top(int x, int z, double fromY) {
         Level level = SledEntity.this.level();
         if (!level.hasChunk(x >> 4, z >> 4)) {
            return Double.NaN;
         }
         int y0 = Mth.floor(fromY);
         for (int y = y0; y > y0 - 14 && y >= level.getMinBuildHeight(); y--) {
            this.p.set(x, y, z);
            BlockState s = level.getBlockState(this.p);
            if (s.isAir()) {
               continue;
            }
            if (s.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock) {
               // snow on a crown or a bush isn't the ground
               BlockState under = level.getBlockState(this.p.below());
               if (under.is(BlockTags.LEAVES) || under.is(BlockTags.LOGS)) {
                  continue;
               }
               return y + s.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) / 8.0;
            }
            if (s.is(Blocks.POWDER_SNOW)) {
               return y + 1.0;
            }
            if (!s.getFluidState().isEmpty() && s.getCollisionShape(level, this.p).isEmpty()) {
               return y + 0.88; // it can't sink: it stops on the water
            }
            if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS) || s.is(BlockTags.FENCE_GATES)) {
               continue; // what stands up out of the snow isn't the ground under it
            }
            var shape = s.getCollisionShape(level, this.p);
            if (shape.isEmpty()) {
               continue;
            }
            return y + shape.max(net.minecraft.core.Direction.Axis.Y);
         }
         return fromY - 64.0; // nothing close below: a drop
      }

      @Override
      public boolean blocked(double x0, double y0, double z0, double x1, double y1, double z1) {
         Level level = SledEntity.this.level();
         AABB box = new AABB(x0, y0, z0, x1, y1, z1);
         for (int x = Mth.floor(x0); x <= Mth.floor(x1); x++) {
            for (int z = Mth.floor(z0); z <= Mth.floor(z1); z++) {
               for (int y = Mth.floor(y0); y <= Mth.floor(y1); y++) {
                  this.p.set(x, y, z);
                  BlockState s = level.getBlockState(this.p);
                  if (s.isAir() || s.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock || s.is(Blocks.POWDER_SNOW)
                     || s.is(BlockTags.LEAVES)) {
                     continue; // snow, and bushes it pushes through
                  }
                  var shape = s.getCollisionShape(level, this.p);
                  if (shape.isEmpty()) {
                     continue;
                  }
                  boolean standing = s.is(BlockTags.LOGS) || s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS) || s.is(BlockTags.FENCE_GATES);
                  if (!standing && shape.max(net.minecraft.core.Direction.Axis.Y) >= 1.0 && shape.min(net.minecraft.core.Direction.Axis.Y) <= 0.0
                     && shape.max(net.minecraft.core.Direction.Axis.X) >= 1.0 && shape.min(net.minecraft.core.Direction.Axis.X) <= 0.0) {
                     continue; // ground (rock, earth, packed snow): the surface rides over it, and a cliff is a wall there
                  }
                  for (AABB b : shape.toAabbs()) {
                     if (b.move(x, y, z).intersects(box)) {
                        return true;
                     }
                  }
               }
            }
         }
         return false;
      }

      @Override
      public double grip(int x, int z, double y) {
         Level level = SledEntity.this.level();
         for (int dy = 0; dy <= 1; dy++) {
            this.p.set(x, Mth.floor(y - 0.05) - dy, z);
            BlockState s = level.getBlockState(this.p);
            if (s.isAir()) {
               continue;
            }
            if (s.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.SNOW_BLOCK)) {
               return 0.02;
            }
            if (s.is(BlockTags.ICE)) {
               return 0.0;
            }
            if (!s.getFluidState().isEmpty()) {
               return 1.5; // [1.4.0] water: more than bare earth, so the snowmobile's crawl never works on it
            }
            if (s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.SNOWY)
               && s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SNOWY)) {
               return 0.03;
            }
            String path = s.getBlock().builtInRegistryHolder().key().location().getPath();
            if (path.contains("snow") || path.contains("ice") || path.contains("frost")) {
               return 0.03;
            }
            if (path.contains("sand") || path.contains("gravel")) {
               return 0.7;
            }
            return 1.0;
         }
         return 1.0;
      }
   };

   /** test only (the server QA harness): ride as if someone sat on it, with this forward and side input */
   public float[] qaInput;

   /** the ride's state (tests) */
   public SledPhysics physics() {
      return this.phys;
   }

   /** the last hit (0 none): how much speed it took */
   public double impact() {
      return this.phys.impact;
   }

   /** the last steering input, for the lean of the camera and the spray (client) */
   public float steer;

   public int run() {
      return this.phys.run;
   }

   private void drive(boolean ridden) {
      Player rider = this.rider();
      float forward = rider == null ? 0.0F : rider.zza, side = rider == null ? 0.0F : rider.xxa;
      if (rider == null && this.qaInput != null) {
         forward = this.qaInput[0];
         side = this.qaInput[1];
      }
      SledPhysics s = this.phys;
      if (!this.drove) {
         // taking over (placed, mounted, or handed over by the server): start from rest where the sled is
         s.vx = 0.0;
         s.vz = 0.0;
         s.vy = 0.0;
         s.ground = true;
         s.lean = 0.0F;
         this.drove = true;
      }
      s.x = this.getX();
      s.y = this.getY();
      s.z = this.getZ();
      s.yaw = this.getYRot();
      this.control(s, rider, forward, side);
      s.tick(this.terrain, forward, side, ridden);
      this.steer = s.lean;
      this.speed = s.speed();
      this.setYRot(s.yaw);
      this.setPos(s.x, s.y, s.z);
      this.setOnGround(s.ground);
      this.setDeltaMovement(s.vx, s.vy, s.vz);
      if (this.level().isClientSide) {
         this.effects();
      }
   }

   /** [1.2.5] a subclass sets up the ride's controls for this tick (the snowmobile's engine) */
   protected void control(SledPhysics s, Player rider, float forward, float side) {
   }

   protected void effects() {
      SledPhysics s = this.phys;
      double sp = s.speed();
      double rad = Math.toRadians(this.getYRot());
      double fx = -Math.sin(rad), fz = Math.cos(rad);
      boolean snow = this.grip() < 0.2;
      if (s.impact > 0.25) {
         this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, (float)Math.min(1.0, s.impact * 1.5),
            0.7F + this.random.nextFloat() * 0.2F, false);
      }
      if (s.ground && sp > 0.15 && snow) {
         // powder kicked off the runners: a rooster tail behind, sheets to the outside of a turn
         float side = s.lean;
         int n = (int)(sp * 3.0 + s.carve * sp * 7.0);
         double rx = -fz, rz = fx;
         for (int i = 0; i < n; i++) {
            double k = this.random.nextBoolean() ? 1.0 : -1.0;
            if (Math.abs(side) > 0.1F && this.random.nextFloat() < 0.75F) {
               k = side > 0 ? 1.0 : -1.0; // outside of the turn
            }
            double back = 0.5 + this.random.nextDouble() * 0.8;
            double px = this.getX() + rx * k * 0.45 - fx * back, pz = this.getZ() + rz * k * 0.45 - fz * back;
            double out = 0.05 + this.random.nextDouble() * 0.1 * (1.0 + 1.5 * s.carve);
            this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState()), px, this.getY() + 0.1, pz,
               rx * k * out + fx * sp * 0.15, 0.1 + this.random.nextDouble() * 0.18 * sp, rz * k * out + fz * sp * 0.15);
            if (this.random.nextFloat() < 0.4F) {
               this.level().addParticle(ParticleTypes.SNOWFLAKE, px, this.getY() + 0.2, pz, rx * k * out * 0.6, 0.06 + 0.1 * sp, rz * k * out * 0.6);
            }
         }
      }
   }

   // ============================================================================================ how it looks (client)

   /** height, pitch and roll the sled is drawn at: laid along the smooth snow surface, eased from tick to tick */
   public double visY = Double.NaN, visYO;
   public float visPitch, visPitchO, visRoll, visRollO;

   /** the same surface the ride uses, for sleds this game doesn't drive (someone else's, or standing) */
   private final SledPhysics look = new SledPhysics();

   private void visual() {
      this.visYO = Double.isNaN(this.visY) ? this.getY() : this.visY;
      this.visPitchO = this.visPitch;
      this.visRollO = this.visRoll;
      SledPhysics s = this.drove ? this.phys : this.look;
      if (!this.drove) {
         s.x = this.getX();
         s.y = this.getY();
         s.z = this.getZ();
         s.yaw = this.getYRot();
         s.sense(this.terrain);
      }
      float tp, tr;
      if (s.ground || !this.drove) {
         tp = (float)Math.toDegrees(Math.atan(-s.pitchSlope));
         tr = (float)Math.toDegrees(Math.atan(s.rollSlope));
      } else {
         // in the air the nose follows the flight
         tp = (float)Mth.clamp(Math.toDegrees(Math.atan2(s.vy, Math.max(0.2, s.speed()))), -40.0, 20.0);
         tr = this.visRoll;
      }
      tp = Mth.clamp(tp, -45.0F, 45.0F);
      tr = Mth.clamp(tr, -25.0F, 25.0F);
      if (this.drove && !Double.isNaN(this.phys.viewY)) {
         this.visY = this.phys.viewY; // the smoothed height: the steps under the snow never jolt the ride
      } else if (Double.isNaN(this.visY) || Math.abs(this.getY() - this.visY) > 3.0) {
         this.visY = this.getY();
      } else {
         this.visY += (this.getY() - this.visY) * 0.5;
      }
      this.visPitch += (tp - this.visPitch) * 0.45F;
      this.visRoll += (tr - this.visRoll) * 0.4F;
   }

   public double visY(float pt) {
      if (Double.isNaN(this.visY)) {
         return Mth.lerp(pt, this.yo, this.getY());
      }
      return Mth.lerp(pt, this.visYO, this.visY);
   }

   public double speed() {
      return this.speed;
   }

   @Override
   protected net.minecraft.world.entity.Entity.MovementEmission getMovementEmission() {
      return Entity.MovementEmission.NONE;
   }
}
