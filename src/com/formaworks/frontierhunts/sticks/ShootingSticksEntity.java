package com.formaworks.frontierhunts.sticks;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.sticks.ShootingSticks.Height;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * [sticks] A set of shooting sticks standing on the ground (see {@link ShootingSticks} for the rules).
 *
 * <p>The tripod is an entity, not a block: it stands exactly where it was set, turned to the way its owner faced, at one
 * of three heights. A shooter who rests a gun in it rides it (vanilla passenger machinery: the server keeps the body
 * where the gun can reach the yoke, every client sees the same, logging out or dying lets go cleanly). The rider is
 * kept behind the yoke along their own line of fire, so swivelling the gun walks the body round the pivot like a real
 * shooter on sticks; the swivel and tilt are limited to an arc ({@link ShootingSticks#ARC}, narrowed by anything that
 * would be in the way of the body) through a soft stop. Synced: height, the arc (centre and both sides), the direction
 * the yoke was last left pointing, and when they were set up (the legs unfold on clients that saw it happen).
 *
 * <p>Saved: height, yoke direction and the item they were made from (so folding gives back exactly that item).
 */
public class ShootingSticksEntity extends Entity {
   private static final EntityDataAccessor<Byte> HEIGHT = SynchedEntityData.defineId(ShootingSticksEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Float> REST_YAW = SynchedEntityData.defineId(ShootingSticksEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Byte> ARC_LEFT = SynchedEntityData.defineId(ShootingSticksEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> ARC_RIGHT = SynchedEntityData.defineId(ShootingSticksEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Float> YOKE_YAW = SynchedEntityData.defineId(ShootingSticksEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Long> DEPLOYED = SynchedEntityData.defineId(ShootingSticksEntity.class, EntityDataSerializers.LONG);

   /** the item these sticks were set up from (count 1); empty for sticks summoned by command */
   private ItemStack stack = ItemStack.EMPTY;
   /** where the rider stood when they rested (both sides), and ticks since */
   private Vec3 mountFrom;
   private int mountAge = -1;
   /** server: ticks the rider has been pressing a movement key */
   private int moveTicks;
   /** client, local rider only: the offsets shown last frame (soft limit state) */
   private float shownYaw = Float.NaN;
   private float shownPitch = Float.NaN;
   /** client: leg extension shown (eases between heights) */
   public float legShown = Float.NaN;
   public float legShownO = Float.NaN;

   public ShootingSticksEntity(EntityType<? extends ShootingSticksEntity> type, Level level) {
      super(type, level);
      this.noPhysics = true;
      this.blocksBuilding = true;
      this.setNoGravity(true);
   }

   /** Server: a freshly placed set. */
   void setUp(Vec3 at, float yaw, Height h, ItemStack from, long now) {
      this.moveTo(at.x, at.y, at.z, yaw, 0.0F);
      this.setYHeadRot(yaw);
      this.entityData.set(HEIGHT, (byte)h.ordinal());
      this.entityData.set(YOKE_YAW, yaw);
      this.entityData.set(DEPLOYED, now);
      this.stack = from;
      this.refreshDimensions();
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder b) {
      b.define(HEIGHT, (byte)0);
      b.define(REST_YAW, 0.0F);
      b.define(ARC_LEFT, (byte)ShootingSticks.ARC);
      b.define(ARC_RIGHT, (byte)ShootingSticks.ARC);
      b.define(YOKE_YAW, 0.0F);
      b.define(DEPLOYED, Long.MIN_VALUE / 2);
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (HEIGHT.equals(key)) {
         this.refreshDimensions();
      }
   }

   // ============================================================================================ state

   public Height height() {
      return Height.byId(this.entityData.get(HEIGHT));
   }

   /** The arc's centre (the direction from the shooter to the sticks when they rested). */
   public float restYaw() {
      return this.entityData.get(REST_YAW);
   }

   public float arcLeft() {
      return this.entityData.get(ARC_LEFT);
   }

   public float arcRight() {
      return this.entityData.get(ARC_RIGHT);
   }

   /** Where the yoke was last left pointing (degrees, Minecraft yaw). */
   public float yokeYaw() {
      return this.entityData.get(YOKE_YAW);
   }

   /** Game time they were set up (for the unfolding on clients that saw it). */
   public long deployedAt() {
      return this.entityData.get(DEPLOYED);
   }

   public ItemStack stack() {
      return this.stack;
   }

   /** Ticks since the current rider rested (-1: nobody). */
   public int mountAge() {
      return this.mountAge;
   }

   /** How far the rider has settled in, 0..1, at {@code partial} ticks into the next tick. */
   public float settled(float partial) {
      return this.mountAge < 0 ? 0.0F : ShootingSticks.ease((this.mountAge + partial) / ShootingSticks.SETTLE_TICKS);
   }

   public Player rider() {
      return this.getFirstPassenger() instanceof Player p ? p : null;
   }

   /** The top of the cradle's V, where the forend lies (world). */
   public Vec3 contact() {
      return new Vec3(this.getX(), this.getY() + this.height().contact, this.getZ());
   }

   /** The placement / collision box of a set at {@code at} with this height. */
   static AABB box(Vec3 at, Height h) {
      return new AABB(at.x - 0.25, at.y + 0.02, at.z - 0.25, at.x + 0.25, at.y + h.contact + 0.07, at.z + 0.25);
   }

   /** The block under {@code y} carries a tripod: something solid whose top is the ground line. */
   static boolean supported(Level level, BlockPos below, double y) {
      if (!level.isLoaded(below)) {
         return true; // never topple in a chunk that is still loading
      }
      BlockState st = level.getBlockState(below);
      VoxelShape shape = st.getCollisionShape(level, below);
      if (shape.isEmpty()) {
         return false;
      }
      double top = below.getY() + shape.max(Direction.Axis.Y);
      return Math.abs(top - y) < 0.07;
   }

   @Override
   public EntityDimensions getDimensions(Pose pose) {
      return EntityDimensions.scalable(0.5F, (float)(this.height().contact + 0.07));
   }

   // ============================================================================================ entity basics

   @Override
   public boolean isPickable() {
      return !this.isRemoved();
   }

   @Override
   public boolean canBeCollidedWith() {
      return false;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public boolean canBeHitByProjectile() {
      return false;
   }

   @Override
   public boolean shouldRiderSit() {
      return this.height() == Height.SITTING;
   }

   @Override
   public ItemStack getPickResult() {
      return this.stack.isEmpty() ? new ItemStack(item()) : this.stack.copyWithCount(1);
   }

   static Item item() {
      return BuiltInRegistries.ITEM.get(FrontierHunts.id("shooting_sticks"));
   }

   @Override
   protected boolean canAddPassenger(Entity e) {
      return e instanceof Player && this.getPassengers().isEmpty();
   }

   @Override
   protected void addPassenger(Entity e) {
      super.addPassenger(e);
      this.mountFrom = e.position();
      this.mountAge = 0;
      this.moveTicks = 0;
      this.shownYaw = Float.NaN;
      this.shownPitch = Float.NaN;
   }

   @Override
   protected void removePassenger(Entity e) {
      super.removePassenger(e);
      this.mountFrom = null;
      this.mountAge = -1;
      if (this.level().isClientSide && e instanceof Player p && p.isLocalPlayer() && !p.isRemoved()) {
         // the server stands the rider up where getDismountLocationForPassenger says but never tells that client: do
         // the same here (deterministic), so a kneeling or sitting shooter doesn't end up with their feet in the ground
         Vec3 at = this.getDismountLocationForPassenger(p);
         double ox = at.x - p.getX(), oy = at.y - p.getY(), oz = at.z - p.getZ();
         if (ox * ox + oy * oy + oz * oz < 9.0) {
            p.setPos(at);
            p.xo += ox;
            p.yo += oy;
            p.zo += oz;
            p.xOld += ox;
            p.yOld += oy;
            p.zOld += oz;
         }
      }
   }

   @Override
   public AABB getBoundingBoxForCulling() {
      return super.getBoundingBoxForCulling().inflate(0.4, 0.05, 0.4); // the feet stand well outside the hit box
   }

   // ============================================================================================ the rider

   /** Where the rider's feet go for a gun pointing along {@code yaw}: behind the yoke, on (or below, kneeling) the ground. */
   public Vec3 riderSpot(float yaw) {
      Height h = this.height();
      double r = Math.toRadians(yaw);
      return new Vec3(this.getX() + Math.sin(r) * h.reach, this.getY() - h.lower, this.getZ() - Math.cos(r) * h.reach);
   }

   @Override
   protected void positionRider(Entity e, Entity.MoveFunction move) {
      if (!this.hasPassenger(e)) {
         return;
      }
      float b = this.settled(0.0F);
      this.clampRider(e, b);
      Vec3 spot = this.riderSpot(e.getYRot());
      Vec3 p = this.mountFrom == null ? spot : this.mountFrom.lerp(spot, b);
      move.accept(e, p.x, p.y, p.z);
      e.fallDistance = 0.0F;
   }

   /** Client: this frame's partial tick (kept by the client code; the mouse turns the player mid-frame). */
   public static float clientPartial;

   @Override
   public void onPassengerTurned(Entity e) {
      if (this.level().isClientSide && e instanceof Player p && p.isLocalPlayer()) {
         this.clientFrame(p, clientPartial); // the limits and the body's place round the yoke follow the mouse at once
      } else {
         this.clampRider(e, this.settled(0.0F));
      }
   }

   /**
    * Keeps the rider's aim inside the arc and the tilt limits, which ease in from "anything" while they settle
    * ({@code b} 0..1). The local rider gets the soft stop (each turn stiffens near a limit); the server holds a hard
    * clamp a little wider (it only guards); other riders on a client are left as the server sends them.
    */
   void clampRider(Entity e, float b) {
      if (e instanceof LivingEntity le) {
         le.setYBodyRot(e.getYRot());
         le.setYHeadRot(e.getYRot());
      }
      boolean local = e instanceof Player p && p.isLocalPlayer();
      if (this.level().isClientSide && !local) {
         return;
      }
      Height h = this.height();
      float open = 1.0F - b;
      float lo = Mth.lerp(b, -180.0F, -this.arcLeft());
      float hi = Mth.lerp(b, 180.0F, this.arcRight());
      float plo = -h.up - 90.0F * open;
      float phi = h.down + 90.0F * open;
      float yawOff = Mth.wrapDegrees(e.getYRot() - this.restYaw());
      float pitch = e.getXRot();
      float newYaw;
      float newPitch;
      if (local) {
         newYaw = ShootingSticks.soft(Float.isNaN(this.shownYaw) ? Mth.clamp(yawOff, lo, hi) : this.shownYaw, yawOff, lo, hi);
         newPitch = ShootingSticks.soft(Float.isNaN(this.shownPitch) ? Mth.clamp(pitch, plo, phi) : this.shownPitch, pitch, plo, phi);
         this.shownYaw = newYaw;
         this.shownPitch = newPitch;
      } else {
         newYaw = Mth.clamp(yawOff, lo - 2.0F, hi + 2.0F);
         newPitch = Mth.clamp(pitch, plo - 2.0F, phi + 2.0F);
      }
      float dy = newYaw - yawOff;
      if (dy != 0.0F) {
         e.setYRot(e.getYRot() + dy);
         e.yRotO += dy;
         if (e instanceof LivingEntity le) {
            le.setYBodyRot(e.getYRot());
            le.setYHeadRot(e.getYRot());
         }
      }
      float dp = Mth.clamp(newPitch, -90.0F, 90.0F) - pitch;
      if (dp != 0.0F) {
         e.setXRot(pitch + dp);
         e.xRotO = Mth.clamp(e.xRotO + dp, -90.0F, 90.0F);
      }
   }

   /**
    * Client, local rider, once per frame: applies the soft limits (they ease in while settling) and puts the body where
    * the gun points right now, so the swivel round the yoke is frame-smooth (the tick position only catches up).
    */
   public void clientFrame(Player p, float partial) {
      if (!this.hasPassenger(p)) {
         return;
      }
      float b = this.settled(partial);
      this.clampRider(p, b);
      Vec3 spot = this.riderSpot(p.getYRot());
      Vec3 want = this.mountFrom == null ? spot : this.mountFrom.lerp(spot, b);
      double ix = Mth.lerp(partial, p.xo, p.getX());
      double iy = Mth.lerp(partial, p.yo, p.getY());
      double iz = Mth.lerp(partial, p.zo, p.getZ());
      double sx = want.x - ix, sy = want.y - iy, sz = want.z - iz;
      if (sx * sx + sy * sy + sz * sz > 1.0E-10 && sx * sx + sz * sz < 4.0) {
         p.setPos(p.getX() + sx, p.getY() + sy, p.getZ() + sz);
         p.xo += sx;
         p.yo += sy;
         p.zo += sz;
      }
   }

   @Override
   public Vec3 getDismountLocationForPassenger(LivingEntity e) {
      double r = Math.toRadians(e.getYRot());
      double fx = -Math.sin(r), fz = Math.cos(r);
      for (double back : new double[]{0.0, 0.3, 0.6}) {
         Vec3 at = new Vec3(e.getX() - fx * back, this.getY(), e.getZ() - fz * back);
         if (this.level().noCollision(e, e.getDimensions(Pose.STANDING).makeBoundingBox(at).deflate(1.0E-6))) {
            return at;
         }
      }
      return super.getDismountLocationForPassenger(e);
   }

   /**
    * Server: free swivel each way from {@code centre} for a body at this height (sampled every 5 degrees out to the full
    * arc): stops short of anything the shooter's body would stand in. -1 when the centre itself is blocked.
    */
   int[] freeArc(Player p, float centre) {
      Height h = this.height();
      double tall = h == Height.STANDING ? 1.8 : h == Height.KNEELING ? 1.3 : 1.0;
      int[] out = new int[2];
      for (int side = 0; side < 2; side++) {
         int free = -1;
         for (int a = 0; a <= (int)ShootingSticks.ARC; a += 5) {
            Vec3 at = this.riderSpot(centre + (side == 0 ? -a : a)).add(0.0, h.lower, 0.0);
            AABB body = new AABB(at.x - 0.3, at.y + 0.02, at.z - 0.3, at.x + 0.3, at.y + tall, at.z + 0.3);
            if (!this.level().noCollision(p, body)) {
               break;
            }
            free = a;
         }
         out[side] = free;
      }
      return out;
   }

   /** Server: {@code p} lays the gun in the yoke. Every rule checked here; false (with a message) when it can't. */
   public boolean tryRest(ServerPlayer p) {
      if (this.isRemoved() || !p.isAlive() || p.isSpectator() || p.isPassenger() || p.isSleeping() || p.level() != this.level()) {
         return false;
      }
      if (this.rider() != null) {
         p.displayClientMessage(Component.translatable("sticks.frontierhunts.in_use").withStyle(ChatFormatting.GRAY), true);
         return false;
      }
      if (!ShootingSticks.supports(p.getMainHandItem())) {
         return false;
      }
      if (!p.onGround() || p.isInWater() || p.getAbilities().flying || p.isFallFlying()) {
         p.displayClientMessage(Component.translatable("sticks.frontierhunts.stand").withStyle(ChatFormatting.GRAY), true);
         return false;
      }
      double dx = this.getX() - p.getX(), dz = this.getZ() - p.getZ();
      if (dx * dx + dz * dz < 0.04 || dx * dx + dz * dz > 3.2 * 3.2 || Math.abs(p.getY() - this.getY()) > 1.2) {
         p.displayClientMessage(Component.translatable("sticks.frontierhunts.closer").withStyle(ChatFormatting.GRAY), true);
         return false;
      }
      float centre = (float)Math.toDegrees(Math.atan2(-dx, dz));
      int[] arc = this.freeArc(p, centre);
      if (arc[0] < 0 || arc[1] < 0) {
         p.displayClientMessage(Component.translatable("sticks.frontierhunts.no_room_behind").withStyle(ChatFormatting.GRAY), true);
         return false;
      }
      this.entityData.set(REST_YAW, centre);
      this.entityData.set(ARC_LEFT, (byte)arc[0]);
      this.entityData.set(ARC_RIGHT, (byte)arc[1]);
      if (!p.startRiding(this)) {
         return false;
      }
      this.sound(SticksContent.REST.get(), 0.8F, 0.95F + this.random.nextFloat() * 0.1F);
      return true;
   }

   /** Server: the rider comes off (the yoke stays pointing where the gun was). */
   public void release(Player p) {
      if (p != null && this.hasPassenger(p)) {
         this.entityData.set(YOKE_YAW, p.getYRot());
         p.stopRiding();
         this.sound(SticksContent.LIFT.get(), 0.55F, 1.0F + this.random.nextFloat() * 0.08F);
      }
   }

   // ============================================================================================ use, fold, adjust

   @Override
   public InteractionResult interact(Player p, InteractionHand hand) {
      if (hand != InteractionHand.MAIN_HAND) {
         return InteractionResult.PASS;
      }
      if (p.isSecondaryUseActive()) {
         if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
         }
         return this.fold(p) ? InteractionResult.CONSUME : InteractionResult.PASS;
      }
      if (this.rider() != null) {
         return InteractionResult.PASS;
      }
      if (ShootingSticks.supports(p.getMainHandItem())) {
         // normally the client asks with SticksNetwork.Rest (the gun's own use key handling never sends this); kept as a fallback
         if (p instanceof ServerPlayer sp) {
            this.tryRest(sp);
         }
         return InteractionResult.sidedSuccess(this.level().isClientSide);
      }
      if (!this.level().isClientSide) {
         this.adjust(p);
      }
      return InteractionResult.sidedSuccess(this.level().isClientSide);
   }

   /** Server: the next height (standing, kneeling, sitting), if the legs have room to go there. */
   void adjust(Player p) {
      Height next = this.height().next();
      if (!this.level().noCollision(box(this.position(), next))) {
         p.displayClientMessage(Component.translatable("sticks.frontierhunts.no_room").withStyle(ChatFormatting.GRAY), true);
         return;
      }
      this.entityData.set(HEIGHT, (byte)next.ordinal());
      this.refreshDimensions();
      this.sound(SticksContent.ADJUST.get(), 0.7F, next == Height.STANDING ? 1.06F : next == Height.KNEELING ? 0.98F : 0.92F);
      p.displayClientMessage(Component.translatable("sticks.frontierhunts.height." + next.key).withStyle(ChatFormatting.GOLD), true);
   }

   /** Server: folds the sticks back into the item for {@code p}. False when someone else is resting on them. */
   public boolean fold(Player p) {
      if (this.isRemoved()) {
         return false;
      }
      Player r = this.rider();
      if (r != null && r != p) {
         p.displayClientMessage(Component.translatable("sticks.frontierhunts.in_use").withStyle(ChatFormatting.GRAY), true);
         return false;
      }
      this.ejectPassengers();
      ItemStack back = this.getPickResult();
      this.stack = ItemStack.EMPTY;
      this.discard();
      this.sound(SticksContent.FOLD.get(), 0.85F, 0.97F + this.random.nextFloat() * 0.06F);
      if (p.hasInfiniteMaterials()) {
         // creative: placing never used the item up, so only hand one over if there isn't one already
         if (!p.getInventory().contains(st -> st.is(back.getItem()))) {
            p.getInventory().add(back);
         }
      } else if (!p.addItem(back)) {
         this.spawnAtLocation(back, 0.3F);
      }
      return true;
   }

   /** Server: knocked over (no ground, an explosion): they drop as the item. */
   void topple() {
      if (this.isRemoved()) {
         return;
      }
      this.ejectPassengers();
      ItemStack back = this.getPickResult();
      this.stack = ItemStack.EMPTY;
      this.sound(SticksContent.FOLD.get(), 0.6F, 0.85F);
      this.spawnAtLocation(back, 0.3F);
      this.discard();
   }

   @Override
   public boolean hurt(DamageSource src, float amount) {
      if (this.level().isClientSide || this.isRemoved()) {
         return !this.isRemoved();
      }
      if (this.isInvulnerableTo(src)) {
         return false;
      }
      if (src.getEntity() instanceof Player p && src.getDirectEntity() == p) {
         return this.fold(p);
      }
      if (src.is(DamageTypeTags.IS_EXPLOSION)) {
         this.topple();
         return true;
      }
      return false;
   }

   private void sound(SoundEvent s, float volume, float pitch) {
      this.level().playSound(null, this.getX(), this.getY() + this.height().contact * 0.6, this.getZ(), s, SoundSource.PLAYERS, volume, pitch);
   }

   // ============================================================================================ tick

   @Override
   public void tick() {
      super.tick();
      Player r = this.rider();
      if (r != null) {
         this.mountAge = this.mountAge < 0 ? 0 : Math.min(this.mountAge + 1, 10000);
      }
      if (this.level().isClientSide) {
         this.legShownO = Float.isNaN(this.legShown) ? (float)this.height().legLength() : this.legShown;
         float want = (float)this.height().legLength();
         this.legShown = Float.isNaN(this.legShown) ? want : this.legShown + Mth.clamp(want - this.legShown, -0.06F, 0.06F);
         return;
      }
      if (r != null) {
         boolean moving = Math.abs(r.zza) > 0.3F || Math.abs(r.xxa) > 0.3F;
         this.moveTicks = moving ? this.moveTicks + 1 : 0;
         if (!ShootingSticks.supports(r.getMainHandItem()) || !r.isAlive() || r.isSpectator() || this.moveTicks >= 2) {
            this.release(r);
         }
      } else if (this.tickCount % 20 == 7 && !supported(this.level(), BlockPos.containing(this.getX(), this.getY() - 0.02, this.getZ()), this.getY())) {
         this.topple();
         return;
      }
      if (this.getY() < this.level().getMinBuildHeight() - 64) {
         this.discard();
      }
   }

   // ============================================================================================ save

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
      this.entityData.set(HEIGHT, (byte)Height.byId(tag.getByte("Height")).ordinal());
      this.entityData.set(YOKE_YAW, tag.contains("YokeYaw") ? tag.getFloat("YokeYaw") : this.getYRot());
      this.stack = tag.contains("Item") ? ItemStack.parse(this.registryAccess(), tag.getCompound("Item")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
      this.refreshDimensions();
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
      tag.putByte("Height", (byte)this.height().ordinal());
      tag.putFloat("YokeYaw", this.yokeYaw());
      if (!this.stack.isEmpty()) {
         tag.put("Item", this.stack.save(this.registryAccess()));
      }
   }

   /** QA: all the state that has to survive a save. */
   public String describe() {
      return this.height().key + " yaw=" + Math.round(this.getYRot()) + " yoke=" + Math.round(this.yokeYaw()) + " item="
         + (this.stack.isEmpty() ? "-" : BuiltInRegistries.ITEM.getKey(this.stack.getItem()) + (this.stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
            ? "/" + this.stack.getHoverName().getString() : ""));
   }

   /** Server: QA hook to place without an item in hand. */
   public static ShootingSticksEntity spawn(ServerLevel level, Vec3 at, float yaw, Height h, ItemStack from) {
      ShootingSticksEntity s = SticksContent.STICKS.get().create(level);
      if (s == null) {
         return null;
      }
      s.setUp(at, yaw, h, from, level.getGameTime());
      return level.addFreshEntity(s) ? s : null;
   }
}
