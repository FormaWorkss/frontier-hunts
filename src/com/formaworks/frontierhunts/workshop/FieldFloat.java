package com.formaworks.frontierhunts.workshop;

import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.expedition.ExpeditionService;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.Pufferfish;
import net.minecraft.world.entity.animal.Salmon;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class FieldFloat extends Entity {
   public static final int CAST = 0;
   public static final int SOAK = 1;
   public static final int BITE = 2;
   public static final int FIGHT = 3;
   public static final int SNAG = 4;
   private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(FieldFloat.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(FieldFloat.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> TENSION = SynchedEntityData.defineId(FieldFloat.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> LINE = SynchedEntityData.defineId(FieldFloat.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> STAMINA = SynchedEntityData.defineId(FieldFloat.class, EntityDataSerializers.FLOAT);
   private UUID ownerId;
   private ItemStack rod = ItemStack.EMPTY;
   private AbstractFish fish;
   private Goal lureGoal;
   private int waitTicks;
   private int biteTicks;
   private int strainTicks;
   private int snagTicks;
   private long lastStrike = -100L;
   private double lineLength = 1.0;
   private int lerpSteps;
   private double lerpX;
   private double lerpY;
   private double lerpZ;

   public FieldFloat(EntityType<? extends FieldFloat> var1, Level var2) {
      super(var1, var2);
   }

   protected void defineSynchedData(Builder var1) {
      var1.define(OWNER, 0);
      var1.define(STATE, 0);
      var1.define(TENSION, 0.0F);
      var1.define(LINE, 1.0F);
      var1.define(STAMINA, 1.0F);
   }

   public void assign(ServerPlayer var1, ItemStack var2) {
      this.ownerId = var1.getUUID();
      this.rod = var2;
      this.entityData.set(OWNER, var1.getId());
   }

   public Entity owner() {
      return this.level().getEntity((Integer)this.entityData.get(OWNER));
   }

   public int state() {
      return (Integer)this.entityData.get(STATE);
   }

   public float tension() {
      return (Float)this.entityData.get(TENSION);
   }

   public float stamina() {
      return (Float)this.entityData.get(STAMINA);
   }

   public float line() {
      return (Float)this.entityData.get(LINE);
   }

   /** [fishing3] ticks the hooked fish has been held within landing reach while reeling */
   private int closeTicks;

   public static Vec3 integrate(Vec3 var0, boolean var1, double var2, Vec3 var4, Wilderness.Wind var5) {
      return var1
         ? var0.multiply(0.82, 0.4, 0.82).add(var4.scale(0.025)).add(0.0, Math.clamp(var2 * 0.2, -0.07, 0.07), 0.0)
         : var0.scale(0.988).add(var5.east() * 1.0E-4, -0.032, var5.south() * 1.0E-4);
   }

   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         if (this.lerpSteps > 0) {
            this.setPos(
               this.getX() + (this.lerpX - this.getX()) / (double)this.lerpSteps,
               this.getY() + (this.lerpY - this.getY()) / (double)this.lerpSteps,
               this.getZ() + (this.lerpZ - this.getZ()) / (double)this.lerpSteps
            );
            this.lerpSteps--;
         }
      } else {
         ServerLevel var1 = (ServerLevel)this.level();
         ServerPlayer var2 = this.ownerId == null ? null : var1.getServer().getPlayerList().getPlayer(this.ownerId);
         if (var2 != null
            && var2.level() == var1
            && var2.isAlive()
            && !var2.isSpectator()
            && var2.getMainHandItem() == this.rod
            && !(this.distanceToSqr(var2) > 1764.0)
            && this.tickCount <= 12000) {
            boolean var3 = var2.isUsingItem() && var2.getUseItem() == this.rod && var2.containerMenu == var2.inventoryMenu;
            BlockPos var4 = BlockPos.containing(this.position());
            if (!var1.hasChunkAt(var4)) {
               this.discard();
            } else {
               FluidState var5 = var1.getFluidState(var4);
               if (!var5.is(FluidTags.WATER)) {
                  var4 = BlockPos.containing(this.position().add(0.0, -0.1, 0.0));
                  var5 = var1.getFluidState(var4);
               }

               boolean var6 = var5.is(FluidTags.WATER);
               double var7 = (double)((float)var4.getY() + var5.getHeight(var1, var4)) + 0.025;
               Wilderness.Wind var9 = Wilderness.wind(var1.getSeed(), var1.getGameTime(), var1.isRaining(), var1.isThundering());
               Vec3 var10 = integrate(this.getDeltaMovement(), var6, var7 - this.getY(), var6 ? var5.getFlow(var1, var4) : Vec3.ZERO, var9);
               Vec3 var11 = var2.getEyePosition().add(0.0, -0.25, 0.0);
               Vec3 var12 = this.position().subtract(var11);
               double var13 = var12.length();
               if (this.state() == 0) {
                  this.lineLength = Math.min(34.0, Math.max(this.lineLength, var13 + 0.35));
                  if (var6) {
                     this.entityData.set(STATE, 1);
                     this.waitTicks = 35 + this.random.nextInt(65);
                  }
               }

               if (this.state() == 1 && var6 && !var3) {
                  this.attract();
               }

               if (this.state() == 2) {
                  var10 = var10.add(0.0, -0.012, 0.0);
                  if (--this.biteTicks <= 0 || !canTakeBait(this.fish)) {
                     this.setFish(null);
                     this.entityData.set(STATE, 1);
                     this.waitTicks = 100;
                  }
               }

               float var15 = 0.0F;
               if (this.state() == 3) {
                  if (this.fish == null || !this.fish.isAlive() || this.fish.level() != var1) {
                     this.discard();
                     return;
                  }

                  double var16 = (double)(this.tickCount + this.getId() * 17) * 0.045;
                  double var18 = Math.max(0.0, Math.sin(var16));
                  Vec3 var20 = new Vec3(-var12.z, 0.0, var12.x).normalize();
                  float var21 = this.stamina();
                  if (var21 == 0.0F) {
                     this.entityData.set(STAMINA, 0.0F);
                  }

                  var10 = var10.add(var20.scale(0.025 * var18 * (double)var21))
                     .add(var12.multiply(1.0, 0.0, 1.0).normalize().scale(0.025 * var18 * (double)var21));
                  var15 = var21 == 0.0F ? 0.0F : (float)(0.16 + 0.45 * var18 * (double)var21);
                  if (!var3) {
                     this.lineLength = Math.min(34.0, this.lineLength + 0.035 * var18);
                  }

                  float drain = 0.0F;
                  if (this.tension() > 0.2F && this.tension() < 0.85F) {
                     drain = (var3 ? 0.003F : 0.0015F) * (1.0F + 0.1F * (float)ExpeditionService.skill(var2, 3));
                  }
                  // [fishing3] a hooked fish always tires a little, and fast once it is reeled in close: near the
                  // bank the line goes slack, the tension window above never opens, and the fish used to stay
                  // fresh forever right at your feet
                  drain += 0.0004F;
                  if (var3 && var13 < 8.0) {
                     drain += 0.004F;
                  }
                  this.entityData.set(STAMINA, Math.max(0.0F, var21 - drain));

                  this.fish.getNavigation().stop();
                  Vec3 var22 = this.fishTarget().subtract(this.fish.position());
                  Vec3 var23 = this.fish.getDeltaMovement().scale(0.45).add(var22.scale(0.18));
                  if (var23.lengthSqr() > 0.0144) {
                     var23 = var23.normalize().scale(0.12);
                  }

                  this.fish.setDeltaMovement(var23);
                  this.fish.hasImpulse = true;
                  if (var23.horizontalDistanceSqr() > 1.0E-4) {
                     float var24 = (float)Math.toDegrees(Math.atan2(-var23.x, var23.z));
                     this.fish.setYRot(Mth.rotLerp(0.22F, this.fish.getYRot(), var24));
                     this.fish.yBodyRot = this.fish.getYRot();
                     this.fish.setYHeadRot(this.fish.getYRot());
                  }
               }

               if (this.state() == 3) {
                  double var25 = var3 ? 0.8 : 0.56;
                  double var27 = Math.max(0.12, (var25 - (double)var15 - (var3 ? 0.15 : 0.0)) / 0.38);
                  double var30 = Math.clamp(var13 - var27, 0.7, 34.0);
                  if (this.lineLength < var30) {
                     this.lineLength = Math.min(34.0, this.lineLength + Math.min(0.24, var30 - this.lineLength));
                  }

                  if (var3) {
                     this.lineLength = Math.max(var30, this.lineLength - 0.075);
                  }
               } else if (var3) {
                  this.lineLength = Math.max(0.7, this.lineLength - 0.13);
               }

               double var26 = Math.max(0.0, var13 - this.lineLength);
               if (var26 > 0.0) {
                  Vec3 var28 = this.state() == 3 && var6 ? var12.multiply(1.0, 0.0, 1.0).normalize() : var12.normalize();
                  var10 = var10.add(var28.scale(-Math.min(0.16, var26 * 0.065)));
               }

               float var29 = (float)Math.clamp(var26 * 0.38 + (double)var15 + (var3 && this.state() == 3 ? 0.15 : 0.0), 0.0, 1.0);
               this.entityData.set(TENSION, var29);
               this.entityData.set(LINE, (float)this.lineLength);
               this.strainTicks = var29 > 0.94F ? this.strainTicks + 1 : Math.max(0, this.strainTicks - 2);
               if (this.strainTicks >= (ExpeditionWeapon.attachment(this.rod, "drag_kit") ? 30 : 22)) {
                  var2.displayClientMessage(Component.literal("The line snapped. Ease the reel when tension rises."), true);
                  this.wear(var2, 2);
                  this.discard();
               } else {
                  boolean var19 = FishingGear.hasNet(var2);
                  // [fishing3] landing: reeling a hooked fish that is within reach (a bank or dock is fine: reach
                  // counts mostly sideways) lands it once it is tired, or after a couple of seconds held close in
                  // any case. Line of sight to the float OR the fish will do (a fish under the bank lip hid the
                  // float from the player before, and it could never be landed).
                  boolean landNow = false;
                  if (var3 && this.state() == 3 && this.fish != null) {
                     double reach = var19 ? 5.5 : 4.5;
                     double dx = this.fish.getX() - var2.getX(), dz = this.fish.getZ() - var2.getZ();
                     double side = Math.sqrt(dx * dx + dz * dz);
                     double down = var2.getY() - this.fish.getY();
                     boolean near = side < reach && down < reach + 2.0 && down > -3.0;
                     this.closeTicks = near ? this.closeTicks + 1 : Math.max(0, this.closeTicks - 2);
                     boolean seen = var2.hasLineOfSight(this) || var2.hasLineOfSight(this.fish) || side < 3.0;
                     landNow = near && seen && (this.stamina() < (var19 ? 0.35F : 0.12F) || this.closeTicks >= 50);
                  } else if (this.state() != 3) {
                     this.closeTicks = 0;
                  }
                  if (!var3 || !landNow && (this.state() == 3 || !(var13 < 2.1))) {
                     Vec3 var32 = this.position().add(var10);
                     if (this.state() == 3 && var6 && !var1.getFluidState(BlockPos.containing(var32.x, var7 - 0.25, var32.z)).is(FluidTags.WATER)) {
                        boolean var33 = var1.getFluidState(BlockPos.containing(var32.x, var7 - 0.25, this.getZ())).is(FluidTags.WATER);
                        boolean var35 = var1.getFluidState(BlockPos.containing(this.getX(), var7 - 0.25, var32.z)).is(FluidTags.WATER);
                        var10 = new Vec3(var33 ? var10.x : 0.0, var10.y, var35 ? var10.z : 0.0);
                        var32 = this.position().add(var10);
                     }

                     if (!var1.hasChunkAt(BlockPos.containing(var32))) {
                        this.discard();
                     } else {
                        BlockHitResult var34 = var1.clip(new ClipContext(this.position(), var32, Block.COLLIDER, Fluid.NONE, this));
                        if (var34.getType() == Type.BLOCK) {
                           this.setPos(var34.getLocation().add(Vec3.atLowerCornerOf(var34.getDirection().getNormal()).scale(0.025)));
                           this.setDeltaMovement(var10.multiply(-0.14, -0.18, -0.14));
                           if (!var6 && this.state() != 3) {
                              this.entityData.set(STATE, 4);
                              this.snagTicks++;
                           }
                        } else {
                           this.setPos(var32);
                           this.setDeltaMovement(var10);
                           if (this.state() == 4 && var6) {
                              this.entityData.set(STATE, 1);
                              this.waitTicks = 60;
                           }
                        }

                        if (this.snagTicks > 40 && var3) {
                           this.wear(var2, 1);
                           this.discard();
                        }
                     }
                  } else {
                     if (this.state() == 3) {
                        this.land(var2);
                     } else {
                        this.discard();
                     }
                  }
               }
            }
         } else {
            this.discard();
         }
      }
   }

   private void attract() {
      boolean var1 = ExpeditionWeapon.attachment(this.rod, "glow_lure");
      if (var1 && this.level().isNight() && this.tickCount % 12 == 0) {
         ((ServerLevel)this.level()).sendParticles(ParticleTypes.GLOW, this.getX(), this.getY(), this.getZ(), 2, 0.025, 0.01, 0.025, 0.0);
      }

      boolean var2 = FishingGear.chummed((ServerLevel)this.level(), this.position(), 12.0);
      if (this.waitTicks > 0) {
         this.waitTicks = this.waitTicks - ((var1 && this.level().isNight() ? 2 : 1) + (var2 ? 1 : 0));
      } else {
         double var3 = (double)((var1 && this.level().isNight() ? 13 : 10) + (var2 ? 6 : 0));
         if (!canTakeBait(this.fish) || this.fish.distanceToSqr(this) > (var3 + 1.0) * (var3 + 1.0)) {
            this.setFish(
               this.level()
                  .getEntitiesOfClass(
                     AbstractFish.class,
                     this.getBoundingBox().inflate(var3),
                     var0 -> canTakeBait(var0)
                           && var0.isInWater()
                           && (
                              var0.getType() == EntityType.SALMON
                                 || var0.getType() == EntityType.COD
                                 || var0.getType() == EntityType.TROPICAL_FISH
                                 || var0.getType() == EntityType.PUFFERFISH
                           )
                  )
                  .stream()
                  .filter(
                     var1x -> this.level()
                           .getEntitiesOfClass(FieldFloat.class, var1x.getBoundingBox().inflate(34.0), var2x -> var2x != this && var2x.fish == var1x)
                           .isEmpty()
                  )
                  .min(Comparator.comparingDouble(var1x -> var1x.distanceToSqr(this)))
                  .orElse(null)
            );
         }

         if (this.fish == null) {
            this.waitTicks = 40;
         } else {
            Vec3 var5 = this.fishTarget();
            if (this.level().clip(new ClipContext(this.fish.position(), var5, Block.COLLIDER, Fluid.NONE, this.fish)).getType() != Type.MISS) {
               this.setFish(null);
               this.waitTicks = 40;
            } else {
               if (this.fish.position().distanceToSqr(var5) < 0.42250000000000004) {
                  this.entityData.set(STATE, 2);
                  this.biteTicks = 32;
                  ((ServerLevel)this.level()).sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY(), this.getZ(), 5, 0.06, 0.02, 0.06, 0.03);
               }
            }
         }
      }
   }

   private void setFish(final AbstractFish var1) {
      if (this.fish != var1) {
         if (this.fish != null && this.lureGoal != null) {
            this.fish.goalSelector.removeGoal(this.lureGoal);
         }

         this.fish = var1;
         this.lureGoal = null;
         if (var1 != null) {
            this.lureGoal = new Goal() {
               {
                  this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
               }

               public boolean canUse() {
                  return FieldFloat.this.fish == var1
                     && !FieldFloat.this.isRemoved()
                     && var1.isAlive()
                     && (FieldFloat.this.state() == 1 || FieldFloat.this.state() == 2 || FieldFloat.this.state() == 3);
               }

               public boolean requiresUpdateEveryTick() {
                  return true;
               }

               public void tick() {
                  if (FieldFloat.this.state() == 3) {
                     var1.getNavigation().stop();
                  } else {
                     Vec3 var1x = FieldFloat.this.fishTarget();
                     if (var1.distanceToSqr(var1x) < 4.0) {
                        var1.getNavigation().stop();
                        Vec3 var2 = var1x.subtract(var1.position()).scale(0.12);
                        if (var2.lengthSqr() > 0.0064) {
                           var2 = var2.normalize().scale(0.08);
                        }

                        Vec3 var3 = var1.getDeltaMovement().lerp(var2, 0.25);
                        var1.setDeltaMovement(var3);
                        var1.hasImpulse = true;
                        if (var3.horizontalDistanceSqr() > 1.0E-4) {
                           float var4 = (float)Math.toDegrees(Math.atan2(-var3.x, var3.z));
                           var1.setYRot(Mth.rotLerp(0.18F, var1.getYRot(), var4));
                           var1.yBodyRot = var1.getYRot();
                           var1.setYHeadRot(var1.getYRot());
                        }
                     } else {
                        var1.getNavigation().moveTo(var1x.x, var1x.y, var1x.z, 0.8);
                     }
                  }
               }

               public void stop() {
                  var1.getNavigation().stop();
               }
            };
            var1.goalSelector.addGoal(1, this.lureGoal);
         }
      }
   }

   private Vec3 fishTarget() {
      return this.position().add(0.0, -Math.max(0.42, (double)this.fish.getBbHeight() + 0.16), 0.0);
   }

   public void strike(ServerPlayer var1) {
      long var2 = this.level().getGameTime();
      if (this.ownerId != null && this.ownerId.equals(var1.getUUID()) && var2 - this.lastStrike >= 8L) {
         this.lastStrike = var2;
         if (this.state() == 2 && canTakeBait(this.fish)) {
            this.entityData.set(STATE, 3);
            this.entityData.set(STAMINA, 1.0F);
            this.lineLength = (double)Math.max(2.0F, this.distanceTo(var1));
            this.wear(var1, 1);
         } else if (this.state() == 1) {
            this.setFish(null);
            this.waitTicks = 100;
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.12, 0.0));
         }
      }
   }

   private void land(ServerPlayer var1) {
      Item var2 = this.fish instanceof Salmon
         ? Items.SALMON
         : (this.fish instanceof TropicalFish ? Items.TROPICAL_FISH : (this.fish instanceof Pufferfish ? Items.PUFFERFISH : Items.COD));
      ExpeditionService.record(var1, "fish", "fish", 1, 0.0);
      ItemStack var3 = FishingGear.weigh(var1, this.fish, new ItemStack(var2), FishingGear.hasNet(var1));
      if (!var1.getInventory().add(var3)) {
         var1.drop(var3, false);
      }

      this.fish.discard();
      this.setFish(null);
      var1.awardStat(Stats.FISH_CAUGHT);
      var1.inventoryMenu.broadcastChanges();
      this.discard();
   }

   private void wear(ServerPlayer var1, int var2) {
      if (!var1.hasInfiniteMaterials()) {
         this.rod.hurtAndBreak(var2, var1, EquipmentSlot.MAINHAND);
      }
   }

   private static boolean canTakeBait(AbstractFish var0) {
      return var0 != null && var0.isAlive();
   }

   public void remove(RemovalReason var1) {
      super.remove(var1);
      if (!this.level().isClientSide) {
         FishingActions.forget(this);
      }

      this.setFish(null);
   }

   protected void addAdditionalSaveData(CompoundTag var1) {
   }

   protected void readAdditionalSaveData(CompoundTag var1) {
      this.discard();
   }

   public void lerpTo(double var1, double var3, double var5, float var7, float var8, int var9) {
      this.lerpX = var1;
      this.lerpY = var3;
      this.lerpZ = var5;
      this.lerpSteps = Math.clamp((long)var9, 1, 5);
   }

   public boolean isPickable() {
      return false;
   }
}
