package com.formaworks.frontierhunts.hunting;

import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.expedition.TargetFace;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.event.EventHooks;

public final class FieldArrow extends Projectile {
   private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(FieldArrow.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Byte> TIP = SynchedEntityData.defineId(FieldArrow.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Boolean> PRIMITIVE = SynchedEntityData.defineId(FieldArrow.class, EntityDataSerializers.BOOLEAN);
   private int age;
   private int stuckAt = -10;
   private boolean recoverable;
   private Vec3 launch = Vec3.ZERO;

   public FieldArrow(EntityType<? extends FieldArrow> var1, Level var2) {
      super(var1, var2);
   }

   protected void defineSynchedData(Builder var1) {
      var1.define(STUCK, false);
      var1.define(TIP, (byte)ArrowTip.FIXED_BROADHEAD.ordinal());
      var1.define(PRIMITIVE, false);
   }

   public boolean stuck() {
      return (Boolean)this.entityData.get(STUCK);
   }

   public boolean tracer() {
      return this.tip().tracer();
   }

   public ArrowTip tip() {
      return ArrowTip.byOrdinal((Byte)this.entityData.get(TIP));
   }

   public boolean primitive() {
      return (Boolean)this.entityData.get(PRIMITIVE);
   }

   public void setShot(ArrowSupply.Shot var1) {
      this.entityData.set(TIP, (byte)var1.tip().ordinal());
      this.entityData.set(PRIMITIVE, var1.primitive());
   }

   public int age() {
      return this.age;
   }

   public void setRecoverable(boolean var1) {
      this.recoverable = var1;
   }

   public void setLaunch(Vec3 var1) {
      this.launch = var1;
   }

   public static Vec3 integrate(Vec3 var0, Wilderness.Wind var1, boolean var2) {
      Vec3 var3 = var0.subtract(var1.east() / 20.0, 0.0, var1.south() / 20.0);
      double var4 = var2 ? 0.28 : Math.min(0.035, var3.length() * 0.0022);
      return var0.subtract(var3.scale(var4)).add(0.0, -0.024525, 0.0);
   }

   public void tick() {
      super.tick();
      this.age++;
      if (this.age > (this.stuck() ? 6000 : 400)) {
         if (!this.level().isClientSide) {
            this.discard();
         }
      } else {
         if (!this.level().isClientSide && this.stuck() && this.age == this.stuckAt + 1 && this.random.nextFloat() > this.tip().recovery) {
            this.recoverable = false;
         }

         if (this.stuck()) {
            this.setDeltaMovement(Vec3.ZERO);
         } else {
            Vec3 var1 = this.position();
            Vec3 var2 = this.getDeltaMovement();
            Vec3 var3 = var1.add(var2);
            if (!this.level().isClientSide) {
               if (!this.level().hasChunkAt(this.blockPosition()) || !this.level().hasChunkAt(BlockPos.containing(var3))) {
                  this.discard();
                  return;
               }

               BlockHitResult var4 = this.level().clip(new ClipContext(var1, var3, Block.COLLIDER, Fluid.NONE, this));
               if (var4.getType() != Type.MISS) {
                  var3 = var4.getLocation();
               }

               Entity var5 = null;
               Vec3 var6 = null;
               DeerAnatomy.Region var7 = null;
               double var8 = var1.distanceToSqr(var3);

               for (Entity var11 : this.level()
                  .getEntities(this, new AABB(var1, var3).inflate(4.0), var1x -> this.canHitEntity(var1x) && !(var1x instanceof TrackClue))) {
                  if (var11 instanceof Player var12 && this.getOwner() instanceof Player var13 && !var13.canHarmPlayer(var12)) {
                     continue;
                  }

                  DeerAnatomy.Region var23 = null;
                  Vec3 var21;
                  if (var11 instanceof Whitetail var25) {
                     if (var25.downed()) {
                        continue;
                     }

                     DeerAnatomy.Contact var15 = DeerAnatomy.intersect(var1, var3, var25);
                     if (var15 == null) {
                        continue;
                     }

                     var21 = var1.lerp(var3, var15.fraction());
                     var23 = var15.region();
                  } else {
                     Optional var27 = var11.getBoundingBox().inflate(0.03).clip(var1, var3);
                     if (var27.isEmpty()) {
                        continue;
                     }

                     var21 = (Vec3)var27.get();
                  }

                  double var26 = var1.distanceToSqr(var21);
                  if (var26 < var8) {
                     var8 = var26;
                     var5 = var11;
                     var6 = var21;
                     var7 = var23;
                  }
               }

               Object var19 = var5 != null ? new EntityHitResult(var5, var6) : var4;
               if (var19.getType() != Type.MISS && !EventHooks.onProjectileImpact(this, (HitResult)var19)) {
                  this.setPos(var19.getLocation());
                  if (var5 != null) {
                     float var20 = (float)Math.clamp(var2.lengthSqr() / 7.5625, 0.04, 1.2);
                     if (var5 instanceof Whitetail var22) {
                        var22.arrowHit(this, var7, var20, this.launch.distanceTo(var6), this.recoverable);
                     } else {
                        var5.hurt(this.impactSource(), 10.0F * var20 * this.tip().damage);
                        if (this.tip() == ArrowTip.JUDO_POINT && var5 instanceof LivingEntity var24) {
                           var24.knockback(0.6, -var2.x, -var2.z);
                        }
                     }

                     this.impact();
                     this.discard();
                     return;
                  }

                  this.onHitBlock(var4);
                  this.impact();
                  if (TargetFace.record(this.level(), var4.getBlockPos(), var4.getLocation(), (byte)0, this.tip(), this.primitive(), var2)) {
                     this.discard();
                     return;
                  }

                  this.entityData.set(STUCK, true);
                  this.stuckAt = this.age;
                  this.setDeltaMovement(Vec3.ZERO);
                  return;
               }
            }

            this.updateRotation();
            this.setPos(var1.add(var2));
            if (this.level() instanceof ServerLevel var16) {
               Wilderness.Wind var18 = Wilderness.wind(var16.getSeed(), var16.getGameTime(), var16.isRaining(), var16.isThundering());
               this.setDeltaMovement(integrate(var2, var18, this.isInWater()));
            } else {
               this.setDeltaMovement(integrate(var2, new Wilderness.Wind(0.0, 0.0), this.isInWater()));
            }
         }
      }
   }

   private void impact() {
      this.level()
         .playSound(null, this.blockPosition(), (SoundEvent)HuntSounds.ARROW_IMPACT.get(), SoundSource.PLAYERS, 0.65F, 0.9F + this.random.nextFloat() * 0.2F);
   }

   public DamageSource impactSource() {
      return new DamageSource(this.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.ARROW), this, this.getOwner());
   }

   public void playerTouch(Player var1) {
      if (!this.level().isClientSide && this.stuck() && this.recoverable && var1.isAlive() && !var1.isSpectator() && !(this.distanceToSqr(var1) > 2.25)) {
         if (var1.getInventory().add(new ArrowSupply.Shot(this.tip(), this.primitive()).stack(1))) {
            this.recoverable = false;
            this.discard();
         }
      }
   }

   protected void addAdditionalSaveData(CompoundTag var1) {
      super.addAdditionalSaveData(var1);
      var1.putBoolean("stuck", this.stuck());
      var1.putString("tip", this.tip().id);
      var1.putBoolean("primitive", this.primitive());
      var1.putInt("stuck_at", this.stuckAt);
      var1.putBoolean("recoverable", this.recoverable);
      var1.putInt("age", this.age);
      var1.putDouble("launch_x", this.launch.x);
      var1.putDouble("launch_y", this.launch.y);
      var1.putDouble("launch_z", this.launch.z);
   }

   protected void readAdditionalSaveData(CompoundTag var1) {
      super.readAdditionalSaveData(var1);
      this.entityData.set(STUCK, var1.getBoolean("stuck"));
      ArrowTip var2 = ArrowTip.byId(var1.getString("tip"));
      this.entityData.set(TIP, (byte)(var2 != null ? var2 : (var1.getBoolean("tracer") ? ArrowTip.TRACER_BROADHEAD : ArrowTip.FIXED_BROADHEAD)).ordinal());
      this.entityData.set(PRIMITIVE, var1.getBoolean("primitive"));
      this.stuckAt = var1.getInt("stuck_at");
      this.recoverable = var1.getBoolean("recoverable");
      this.age = Math.max(0, var1.getInt("age"));
      this.launch = new Vec3(var1.getDouble("launch_x"), var1.getDouble("launch_y"), var1.getDouble("launch_z"));
   }
}
