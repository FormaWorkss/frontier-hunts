package com.formaworks.frontierhunts.rifle;

import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.Wilderness.Wind;
import com.formaworks.frontierhunts.expedition.FirearmDamage;
import com.formaworks.frontierhunts.expedition.TargetFace;
import com.formaworks.frontierhunts.guns.WaterImpact;
import com.formaworks.frontierhunts.hunting.DeerAnatomy;
import com.formaworks.frontierhunts.hunting.TrackClue;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.hunting.DeerAnatomy.Contact;
import com.formaworks.frontierhunts.hunting.DeerAnatomy.Region;
import com.formaworks.frontierhunts.killcam.KillCamServer;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.event.EventHooks;

public final class RifleBullet extends Projectile {
   public static final float MUZZLE_SPEED = 38.0F;
   private Vec3 launch = Vec3.ZERO;
   private boolean survival;
   private int age;

   public RifleBullet(EntityType<? extends RifleBullet> var1, Level var2) {
      super(var1, var2);
   }

   public void launch(Vec3 var1, boolean var2) {
      this.launch = var1;
      this.survival = var2;
   }

   protected void defineSynchedData(Builder var1) {
   }

   public static Vec3 integrate(Vec3 var0, Wind var1, boolean var2) {
      Vec3 var3 = var0.subtract(var1.east() / 20.0, 0.0, var1.south() / 20.0);
      return var0.subtract(var3.scale(var2 ? 0.32 : Math.min(0.025, var3.length() * 2.2E-4))).add(0.0, -0.024525, 0.0);
   }

   public void tick() {
      super.tick();
      this.step();
      if (this.level() instanceof ServerLevel var1) {
         int var3 = 0;

         while (!this.isRemoved() && var3++ < 80 && var1.hasChunkAt(this.blockPosition()) && !var1.isPositionEntityTicking(this.blockPosition())) {
            KillCamServer.sample(this);
            this.step();
         }
      }
   }

   private void step() {
      if (++this.age <= 80 && (this.level().isClientSide || !(this.position().distanceToSqr(this.launch) > 1440000.0))) {
         Vec3 var1 = this.position();
         Vec3 var2 = this.getDeltaMovement();
         Vec3 var3 = var1.add(var2);
         if (Double.isFinite(var3.lengthSqr()) && !(var2.lengthSqr() > 10000.0)) {
            if (this.level() instanceof ServerLevel var4) {
               int var19 = Math.max(1, (int)Math.ceil(var2.length() / 8.0));
               boolean var6 = false;
               if (!var4.hasChunkAt(BlockPos.containing(var1))) {
                  this.discard();
                  return;
               }

               for (int var7 = 1; var7 <= var19; var7++) {
                  if (!var4.hasChunkAt(BlockPos.containing(var1.lerp(var3, (double)var7 / (double)var19)))) {
                     double var8 = (double)(var7 - 1) / (double)var19;
                     double var10 = (double)var7 / (double)var19;

                     for (int var12 = 0; var12 < 24; var12++) {
                        double var13 = (var8 + var10) / 2.0;
                        if (var4.hasChunkAt(BlockPos.containing(var1.lerp(var3, var13)))) {
                           var8 = var13;
                        } else {
                           var10 = var13;
                        }
                     }

                     var3 = var1.lerp(var3, var8);
                     var6 = true;
                     break;
                  }
               }

               float var20 = (float)Math.min(2.0, var2.lengthSqr() / 1444.0 * 1.35);
               Vec3 var22 = WaterImpact.entry(var4, var1, var3, this);
               boolean var9 = var22 == null && var4.getFluidState(BlockPos.containing(var1)).is(FluidTags.WATER);
               if (var22 != null) {
                  WaterImpact.splash(var4, var22, var2, var20, false);
               }

               if (var22 != null || var9) {
                  Vec3 var25 = var22 != null ? var22 : var1;
                  Vec3 var11 = var25.add(var2.normalize().scale(var9 ? 0.5 : WaterImpact.penetration(var20, false)));
                  if (var1.distanceToSqr(var11) < var1.distanceToSqr(var3)) {
                     var3 = var11;
                     var6 = true;
                  }
               }

               BlockHitResult var21 = var4.clip(new ClipContext(var1, var3, Block.COLLIDER, Fluid.NONE, this));
               if (var21.getType() != Type.MISS) {
                  var3 = var21.getLocation();
               }

               Entity var23 = null;
               Vec3 var24 = null;
               Region var26 = null;
               double var27 = var1.distanceToSqr(var3);

               for (Entity var14 : var4.getEntities(this, new AABB(var1, var3).inflate(1.3), var1x -> this.canHitEntity(var1x) && !(var1x instanceof TrackClue))) {
                  if (var14 instanceof Player var15 && this.getOwner() instanceof Player var16 && !var16.canHarmPlayer(var15)) {
                     continue;
                  }

                  Region var32 = null;
                  Vec3 var34;
                  if (var14 instanceof Whitetail var36) {
                     if (var36.downed()) {
                        continue;
                     }

                     Contact var18 = DeerAnatomy.intersect(var1, var3, var36);
                     if (var18 == null) {
                        continue;
                     }

                     var34 = var1.lerp(var3, var18.fraction());
                     var32 = var18.region();
                  } else {
                     Optional var40 = var14.getBoundingBox().inflate(0.008).clip(var1, var3);
                     if (var40.isEmpty()) {
                        continue;
                     }

                     var34 = (Vec3)var40.get();
                  }

                  double var37 = var1.distanceToSqr(var34);
                  if (var37 < var27) {
                     var27 = var37;
                     var23 = var14;
                     var24 = var34;
                     var26 = var32;
                  }
               }

               HitResult var29 = var23 == null ? var21 : new EntityHitResult(var23, var24);
               if (var29.getType() != Type.MISS && !EventHooks.onProjectileImpact(this, (HitResult)var29)) {
                  this.setPos(var29.getLocation());
                  float var31 = (float)Math.clamp(var2.lengthSqr() / 1444.0, 0.05, 1.2);
                  DamageSource var33 = new DamageSource(
                     var4.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.ARROW), this, this.getOwner()
                  );
                  // [guns3] a fast follow-up shot is never swallowed by the hurt cooldown
                  if (var23 instanceof net.minecraft.world.entity.LivingEntity hitLiving) {
                     hitLiving.invulnerableTime = 0;
                  }
                  if (var23 instanceof Whitetail var35) {
                     boolean var38 = var35.projectileHit(var33, this, var24, var26, var31, this.launch.distanceTo(var24), this.survival);
                     if (var38 && var31 >= 0.3F) {
                        Vec3 var41 = DeerAnatomy.exit(var35, var24, var2, 3.0);
                        if (var41 != null) {
                           var35.recordExit(var41, var2, var26);
                        }
                     }
                  } else if (var23 != null) {
                     var23.hurt(var33, FirearmDamage.mobDamage(var23, 24.0F * var31));
                  } else {
                     TargetFace.record(var4, var21.getBlockPos(), var21.getLocation(), (byte)2, null, false);
                     RifleNetwork.impact(var4, var21);
                     BlockState var39 = var4.getBlockState(var21.getBlockPos());
                     Vec3 var42 = var21.getLocation().add(Vec3.atLowerCornerOf(var21.getDirection().getNormal()).scale(0.015));
                     var4.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, var39), var42.x, var42.y, var42.z, 6, 0.035, 0.035, 0.035, 0.06);
                     var4.playSound(
                        null, var21.getBlockPos(), var39.getSoundType(var4, var21.getBlockPos(), this).getHitSound(), SoundSource.PLAYERS, 0.7F, 1.25F
                     );
                  }

                  this.discard();
                  return;
               }

               if (var6) {
                  this.setPos(var3);
                  this.discard();
                  return;
               }

               Wind var30 = Wilderness.wind(var4.getSeed(), var4.getGameTime(), var4.isRaining(), var4.isThundering());
               this.setDeltaMovement(integrate(var2, var30, this.isInWater()));
            }

            this.setPos(var1.add(var2));
            this.updateRotation();
         } else {
            this.discard();
         }
      } else {
         this.discard();
      }
   }

   protected void addAdditionalSaveData(CompoundTag var1) {
      super.addAdditionalSaveData(var1);
      var1.putDouble("launch_x", this.launch.x);
      var1.putDouble("launch_y", this.launch.y);
      var1.putDouble("launch_z", this.launch.z);
      var1.putBoolean("survival", this.survival);
      var1.putInt("age", this.age);
   }

   protected void readAdditionalSaveData(CompoundTag var1) {
      super.readAdditionalSaveData(var1);
      this.launch = new Vec3(var1.getDouble("launch_x"), var1.getDouble("launch_y"), var1.getDouble("launch_z"));
      this.survival = var1.getBoolean("survival");
      this.age = Math.clamp((long)var1.getInt("age"), 0, 81);
   }
}
