package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.Wilderness.Wind;
import com.formaworks.frontierhunts.guns.GunBallistics;
import com.formaworks.frontierhunts.guns.WaterImpact;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.formaworks.frontierhunts.hunting.DeerAnatomy;
import com.formaworks.frontierhunts.hunting.HuntParticles;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.hunting.ArrowSupply.Shot;
import com.formaworks.frontierhunts.hunting.DeerAnatomy.Contact;
import com.formaworks.frontierhunts.hunting.DeerAnatomy.Region;
import com.formaworks.frontierhunts.killcam.KillCamServer;
import com.formaworks.frontierhunts.rifle.RifleNetwork;
import com.formaworks.frontierhunts.workshop.FishingGear;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.event.EventHooks;

public final class HuntProjectile extends Projectile {
   private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(HuntProjectile.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Byte> TIP = SynchedEntityData.defineId(HuntProjectile.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Boolean> PRIMITIVE = SynchedEntityData.defineId(HuntProjectile.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> TETHER = SynchedEntityData.defineId(HuntProjectile.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> STRAIN = SynchedEntityData.defineId(HuntProjectile.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> FATIGUE = SynchedEntityData.defineId(HuntProjectile.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> LINE = SynchedEntityData.defineId(HuntProjectile.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> CRANK = SynchedEntityData.defineId(HuntProjectile.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Boolean> FLARE_REST = SynchedEntityData.defineId(HuntProjectile.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Long> FLARE_BURST = SynchedEntityData.defineId(HuntProjectile.class, EntityDataSerializers.LONG);
   private int visualSteps;
   private double visualX;
   private double visualY;
   private double visualZ;
   private UUID tetherId;
   private float stamina = 1.0F;
   private float lineLength = 0.0F;
   private float crank = 0.0F;
   private float previousCrank = 0.0F;
   private Vec3 attachment = Vec3.ZERO;
   private boolean preciseAttachment;
   private Weapon weapon = Weapon.LEVER_RIFLE;
   private int age;
   private int bounces;
   private float power = 1.0F;
   private boolean recoverable;
   private Vec3 origin = Vec3.ZERO;
   private LivingEntity tether;
   private int tensionTicks;
   private int waterTicks;

   public ArrowTip tip() {
      byte var1 = (Byte)this.entityData.get(TIP);
      return var1 < 0 ? null : ArrowTip.byOrdinal(var1);
   }

   public boolean primitive() {
      return (Boolean)this.entityData.get(PRIMITIVE);
   }

   public boolean tracer() {
      ArrowTip var1 = this.tip();
      return var1 != null && var1.tracer();
   }

   public int age() {
      return this.age;
   }

   public Weapon kind() {
      return Weapon.values()[Math.clamp((long)((Integer)this.entityData.get(KIND)).intValue(), 0, Weapon.values().length - 1)];
   }

   public float burstAge(float var1) {
      long var2 = (Long)this.entityData.get(FLARE_BURST);
      return var2 == 0L ? -1.0F : (float)(this.level().getGameTime() - var2) + var1;
   }

   public float lineLength() {
      return (Float)this.entityData.get(LINE);
   }

   public float crank(float var1) {
      return this.previousCrank + ((Float)this.entityData.get(CRANK) - this.previousCrank) * var1;
   }

   public LivingEntity tetherTarget() {
      return this.level().getEntity((Integer)this.entityData.get(TETHER)) instanceof LivingEntity var1 ? var1 : null;
   }

   public Vec3 attachmentPosition() {
      return this.tether == null ? this.position() : this.tether.position().add(this.attachment);
   }

   public boolean tethered() {
      return (Integer)this.entityData.get(TETHER) != 0;
   }

   public float strain() {
      return (Float)this.entityData.get(STRAIN);
   }

   public float stamina() {
      return (Float)this.entityData.get(FATIGUE);
   }

   public static boolean hasTether(ServerPlayer var0) {
      return !var0.level()
         .getEntitiesOfClass(HuntProjectile.class, var0.getBoundingBox().inflate(36.0), var1 -> var1.tethered() && var1.getOwner() == var0)
         .isEmpty();
   }

   public HuntProjectile(EntityType<? extends HuntProjectile> var1, Level var2) {
      super(var1, var2);
   }

   public boolean shouldRenderAtSqrDistance(double var1) {
      return this.kind() == Weapon.FLARE_GUN ? var1 < 65536.0 : (this.kind().bow ? var1 < 16384.0 : super.shouldRenderAtSqrDistance(var1));
   }

   public void assign(ServerPlayer var1, Weapon var2, float var3, boolean var4) {
      this.assign(var1, var2, var3, var4, null);
   }

   public void assign(ServerPlayer var1, Weapon var2, float var3, boolean var4, Shot var5) {
      this.setOwner(var1);
      this.weapon = var2;
      this.entityData.set(KIND, var2.ordinal());
      this.entityData.set(TIP, (byte)(var5 == null ? -1 : var5.tip().ordinal()));
      this.entityData.set(PRIMITIVE, var5 != null && var5.primitive());
      this.power = var3;
      this.recoverable = var4;
      this.origin = var1.getEyePosition();
      this.setPos(this.origin);
      this.getPersistentData()
         .putUUID(
            "frontier_trigger",
            UUID.nameUUIDFromBytes((var1.getUUID() + ":" + this.level().getGameTime() + ":" + var2.name()).getBytes(StandardCharsets.UTF_8))
         );
   }

   protected void defineSynchedData(Builder var1) {
      var1.define(FLARE_REST, false);
      var1.define(FLARE_BURST, 0L);
      var1.define(KIND, Weapon.LEVER_RIFLE.ordinal());
      var1.define(TIP, (byte)-1);
      var1.define(PRIMITIVE, false);
      var1.define(TETHER, 0);
      var1.define(STRAIN, 0.0F);
      var1.define(FATIGUE, 1.0F);
      var1.define(LINE, 0.0F);
      var1.define(CRANK, 0.0F);
   }

   public void tick() {
      super.tick();
      this.previousCrank = (Float)this.entityData.get(CRANK);
      if (this.level().isClientSide) {
         if ((this.kind() == Weapon.FLARE_GUN || this.tethered()) && this.visualSteps > 0) {
            this.setPos(
               this.getX() + (this.visualX - this.getX()) / (double)this.visualSteps,
               this.getY() + (this.visualY - this.getY()) / (double)this.visualSteps,
               this.getZ() + (this.visualZ - this.getZ()) / (double)this.visualSteps
            );
            this.visualSteps--;
         } else if (this.kind() != Weapon.FLARE_GUN || !(Boolean)this.entityData.get(FLARE_REST)) {
            this.setPos(this.position().add(this.getDeltaMovement()));
         }

         if (this.kind() == Weapon.FLARE_GUN && this.burstAge(0.0F) < 53.0F) {
            this.level()
               .addParticle(
                  (ParticleOptions)HuntParticles.FLARE_EMBER.get(),
                  this.getX(),
                  this.getY(),
                  this.getZ(),
                  (this.random.nextDouble() - 0.5) * 0.015,
                  0.012,
                  (this.random.nextDouble() - 0.5) * 0.015
               );
            if (this.tickCount % 3 == 0) {
               this.level().addParticle((ParticleOptions)HuntParticles.FLARE_SMOKE.get(), this.getX(), this.getY() + 0.06, this.getZ(), 0.0, 0.026, 0.0);
            }
         }
      } else {
         int var1 = this.weapon == Weapon.FLARE_GUN ? 800 : (this.tetherId == null ? 600 : 2400);
         if (!(this.getOwner() instanceof ServerPlayer var2) || !var2.isAlive() || var2.level() != this.level() || ++this.age > var1) {
            this.discard();
            return;
         }

         if (this.weapon == Weapon.FLARE_GUN) {
            this.tickFlare(var2);
         } else {
            if (this.tether == null && this.tetherId != null && this.level() instanceof ServerLevel var6) {
               if (!(var6.getEntity(this.tetherId) instanceof LivingEntity var8)) {
                  this.discard();
                  return;
               }

               this.tether = var8;
               this.entityData.set(TETHER, var8.getId());
               this.entityData.set(FATIGUE, this.stamina);
            }

            if (this.tether != null) {
               this.tickTether(var2);
            } else {
               this.flight(var2);
               if (this.level() instanceof ServerLevel var7) {
                  int var10 = 0;

                  while (
                     !this.isRemoved()
                        && this.tether == null
                        && var10++ < 200
                        && var7.hasChunkAt(this.blockPosition())
                        && !var7.isPositionEntityTicking(this.blockPosition())
                  ) {
                     this.age++;
                     KillCamServer.sample(this);
                     this.flight(var2);
                  }
               }
            }
         }
      }
   }

   private void flight(ServerPlayer var1) {
      Vec3 var2 = this.position();
      Vec3 var3 = this.getDeltaMovement();
      Vec3 var4 = var2.add(var3);
      if (Double.isFinite(var4.lengthSqr())
         && !(var3.length() > 80.0)
         && this.level().hasChunkAt(BlockPos.containing(var2))
         && this.level().hasChunkAt(BlockPos.containing(var4))) {
         boolean var5 = false;
         if (!this.weapon.bow && this.weapon != Weapon.FLARE_GUN && this.weapon != Weapon.BAIT_LAUNCHER && this.level() instanceof ServerLevel var6) {
            boolean var19 = this.weapon.pellets > 0;
            float var8 = GunBallistics.waterEnergy(this.weapon) * Math.max(0.1F, this.power);
            Vec3 var9 = WaterImpact.entry(var6, var2, var4, this);
            boolean var10 = var9 == null && var6.getFluidState(BlockPos.containing(var2)).is(FluidTags.WATER);
            if (var9 != null) {
               WaterImpact.splash(var6, var9, var3, var8, var19);
            }

            if (var9 != null || var10) {
               Vec3 var11 = var9 != null ? var9 : var2;
               Vec3 var12 = var11.add(var3.normalize().scale(var10 ? 0.3 : WaterImpact.penetration(var8, var19)));
               if (var2.distanceToSqr(var12) < var2.distanceToSqr(var4)) {
                  var4 = var12;
                  var5 = true;
               }
            }
         }

         BlockHitResult var18 = this.level().clip(new ClipContext(var2, var4, Block.COLLIDER, Fluid.NONE, this));
         double var20 = var18.getType() == Type.MISS ? var2.distanceToSqr(var4) : var2.distanceToSqr(var18.getLocation());
         LivingEntity var21 = null;
         Vec3 var22 = null;

         for (LivingEntity var27 : this.level()
            .getEntitiesOfClass(
               LivingEntity.class, this.getBoundingBox().expandTowards(var3).inflate(4.0), var1x -> var1x != var1 && var1x.isAlive() && !var1x.isSpectator()
            )) {
            if (var27 instanceof Player var13 && !var1.canHarmPlayer(var13)) {
               continue;
            }

            Optional var31;
            if (var27 instanceof Whitetail var14) {
               Contact var15 = DeerAnatomy.intersect(var2, var4, var14);
               var31 = var15 == null ? Optional.empty() : Optional.of(var2.lerp(var4, var15.fraction()));
            } else {
               var31 = var27.getBoundingBox().inflate(this.weapon == Weapon.BOWFISHING_BOW && fish(var27) ? this.fishReach() : 0.025).clip(var2, var4);
            }

            if (var31.isPresent() && var2.distanceToSqr((Vec3)var31.get()) < var20) {
               var20 = var2.distanceToSqr((Vec3)var31.get());
               var21 = var27;
               var22 = (Vec3)var31.get();
            }
         }

         if (var21 != null) {
            if (EventHooks.onProjectileImpact(this, new EntityHitResult(var21, var22))) {
               this.setPos(var4);
            } else {
               this.setPos(var22);
               DamageSource var24 = new DamageSource(
                  this.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.ARROW), this, var1
               );
               if (this.weapon == Weapon.BOWFISHING_BOW && fish(var21)) {
                  this.tether = var21;
                  this.tetherId = var21.getUUID();
                  this.preciseAttachment = false;
                  this.attachment = var22.subtract(var21.position());
                  this.lineLength = (float)var1.getEyePosition().distanceTo(var22);
                  this.entityData.set(LINE, this.lineLength);
                  this.entityData.set(TETHER, var21.getId());
                  this.setDeltaMovement(Vec3.ZERO);
               } else {
                  if (this.weapon == Weapon.TRANQUILIZER_RIFLE && var21 instanceof Whitetail var28) {
                     if (var28.tranquilize()) {
                        ListTag var34 = var28.getPersistentData().getList("frontier_surveys", 8);
                        String var37 = var1.getUUID().toString();
                        boolean var39 = false;

                        for (Tag var43 : var34) {
                           if (var43.getAsString().equals(var37)) {
                              var39 = true;
                           }
                        }

                        if (!var39 && var34.size() < 64) {
                           var34.add(StringTag.valueOf(var37));
                           var28.getPersistentData().put("frontier_surveys", var34);
                           ExpeditionService.record(var1, "survey", "whitetail", 1, 0.0);
                        }
                     }
                  } else {
                     if (this.weapon == Weapon.TRANQUILIZER_RIFLE) {
                        if (var21 instanceof Mob var33) {
                           VanillaTranquilizers.dart(var33, var1);
                        }

                        this.discard();
                        return;
                     }

                     // [guns3] every round and every pellet counts: vanilla's half-second hurt cooldown used to
                     // swallow all but the first pellet of a shotgun blast and fast follow-up shots
                     if (!this.weapon.bow && var21 instanceof LivingEntity hitLiving) {
                        hitLiving.invulnerableTime = 0;
                     }
                     if (var21 instanceof Whitetail var32) {
                        Contact var36 = DeerAnatomy.intersect(var2, var4, var32);
                        Region var38 = var36 == null ? Region.BODY : var36.region();
                        boolean var16 = var32.projectileHit(
                           var24,
                           this,
                           var22,
                           var38,
                           Math.clamp(this.weapon.damage / 18.0F * this.power, 0.05F, 1.2F),
                           this.origin.distanceTo(var22),
                           this.recoverable
                        );
                        if (var16 && !this.weapon.bow && this.weapon.damage * this.power >= 8.0F) {
                           Vec3 var17 = DeerAnatomy.exit(var32, var22, var3, 3.0);
                           if (var17 != null) {
                              var32.recordExit(var17, var3, var38);
                           }
                        }
                     } else {
                        var21.hurt(
                           var24,
                           FirearmDamage.shot(var24) == null
                              ? this.weapon.damage * this.power
                              : FirearmDamage.mobDamage(var21, this.weapon.damage * this.power)
                        );
                     }
                  }

                  this.discard();
               }
            }
         } else if (var18.getType() != Type.BLOCK) {
            this.setPos(var4);
            if (var5) {
               this.discard();
               return;
            }

            Wind var25 = Wilderness.wind(
               ((ServerLevel)this.level()).getSeed(), this.level().getGameTime(), this.level().isRaining(), this.level().isThundering()
            );
            if (this.isInWater()) {
               this.waterTicks++;
            }

            boolean var29 = this.weapon == Weapon.BOWFISHING_BOW;
            this.setDeltaMovement(
               var3.scale(this.isInWater() ? (var29 ? 0.9 : 0.76) : 0.993)
                  .add(var25.east() * 1.0E-4, this.weapon.bow ? (var29 && this.isInWater() ? -0.012 : -0.035) : -0.018, var25.south() * 1.0E-4)
            );
         } else if (EventHooks.onProjectileImpact(this, var18)) {
            this.setPos(var4);
         } else {
            if (!this.weapon.bow && this.weapon != Weapon.TRANQUILIZER_RIFLE && this.weapon != Weapon.BAIT_LAUNCHER) {
               RifleNetwork.impact((ServerLevel)this.level(), var18);
            }

            BlockState var26 = this.level().getBlockState(var18.getBlockPos());
            Vec3 var30 = Vec3.atLowerCornerOf(var18.getDirection().getNormal());
            double var35 = Math.abs(var3.normalize().dot(var30));
            if (!this.weapon.bow
               && this.weapon.pellets == 0
               && this.bounces++ == 0
               && var26.getDestroySpeed(this.level(), var18.getBlockPos()) >= 2.0F
               && var35 < 0.18) {
               this.setDeltaMovement(var3.subtract(var30.scale(2.0 * var3.dot(var30))).scale(0.45));
               this.setPos(var18.getLocation().add(var30.scale(0.03)));
               this.power *= 0.4F;
            } else if (!this.weapon.bow && this.weapon.pellets == 0 && this.bounces++ < 2 && var26.is(BlockTags.LEAVES)) {
               this.setPos(var18.getLocation().add(var3.normalize().scale(1.1)));
               this.setDeltaMovement(var3.scale(0.7));
               this.power *= 0.6F;
            } else {
               if (this.weapon != Weapon.BAIT_LAUNCHER
                  && this.weapon != Weapon.FLARE_GUN
                  && this.weapon != Weapon.HUNTING_SPEAR
                  && this.weapon != Weapon.BOWFISHING_BOW) {
                  int var40 = this.weapon.bow ? 0 : 2;
                  if (TargetFace.record(this.level(), var18.getBlockPos(), var18.getLocation(), (byte)var40, this.tip(), this.primitive(), var3)) {
                     this.discard();
                     return;
                  }
               }

               if (this.weapon == Weapon.BAIT_LAUNCHER) {
                  ExpeditionGear.attract(var1, var18.getLocation(), false);
               }

               if (this.recoverable && (this.weapon == Weapon.HUNTING_SPEAR || this.weapon.bow)) {
                  ArrowTip var41 = this.tip();
                  if (this.weapon == Weapon.HUNTING_SPEAR) {
                     this.spawnAtLocation(ExpeditionContent.item(this.weapon.id()));
                  } else if (this.weapon == Weapon.BOWFISHING_BOW) {
                     this.spawnAtLocation(ExpeditionContent.item("bowfishing_arrow"));
                  } else if (var41 == null) {
                     this.spawnAtLocation((ItemLike)HuntContent.FIELD_ARROW.get());
                  } else if (this.random.nextFloat() <= var41.recovery) {
                     this.spawnAtLocation(new Shot(var41, this.primitive()).stack(1));
                  }
               }

               this.discard();
            }
         }
      } else {
         this.discard();
      }
   }

   public boolean restingFlare() {
      return this.kind() == Weapon.FLARE_GUN && (Boolean)this.entityData.get(FLARE_REST);
   }

   private void tickFlare(ServerPlayer var1) {
      if (this.age > 800 || this.isInWater() || !this.level().hasChunkAt(this.blockPosition())) {
         this.discard();
      } else if ((Boolean)this.entityData.get(FLARE_REST)) {
         if (this.burstAge(0.0F) > 600.0F) {
            this.discard();
         }
      } else if (this.age > 30 && this.getDeltaMovement().y <= 0.025) {
         this.entityData.set(FLARE_BURST, this.level().getGameTime());
         this.entityData.set(FLARE_REST, true);
         this.setDeltaMovement(Vec3.ZERO);
         this.level().broadcastEntityEvent(this, (byte)73);
         ExpeditionService.marker(var1, this.blockPosition());
      } else {
         Vec3 var2 = this.position();
         Vec3 var3 = this.getDeltaMovement();
         Vec3 var4 = var2.add(var3);
         if (Double.isFinite(var4.lengthSqr()) && !(var3.length() > 80.0) && this.level().hasChunkAt(BlockPos.containing(var4))) {
            BlockHitResult var5 = this.level().clip(new ClipContext(var2, var4, Block.COLLIDER, Fluid.NONE, this));
            if (var5.getType() == Type.BLOCK && !EventHooks.onProjectileImpact(this, var5)) {
               this.setPos(var5.getLocation().add(Vec3.atLowerCornerOf(var5.getDirection().getNormal()).scale(0.055)));
               this.setDeltaMovement(Vec3.ZERO);
               this.entityData.set(FLARE_REST, true);
               ExpeditionService.marker(var1, var5.getBlockPos());
            } else {
               this.setPos(var4);
               this.setDeltaMovement(var3.multiply(0.93, 0.96, 0.93).add(0.0, -0.006, 0.0));
            }
         } else {
            this.discard();
         }
      }
   }

   public void handleEntityEvent(byte var1) {
      if (var1 == 73 && this.kind() == Weapon.FLARE_GUN) {
         for (int var2 = 0; var2 < 44; var2++) {
            double var3 = 1.0 - 2.0 * ((double)var2 + 0.5) / 44.0;
            double var5 = Math.sqrt(1.0 - var3 * var3);
            double var7 = (double)var2 * 2.399963229728653;
            double var9 = 0.14 + this.random.nextDouble() * 0.1;
            this.level()
               .addParticle(
                  (ParticleOptions)HuntParticles.FLARE_EMBER.get(),
                  this.getX(),
                  this.getY(),
                  this.getZ(),
                  Math.cos(var7) * var5 * var9,
                  var3 * var9,
                  Math.sin(var7) * var5 * var9
               );
         }

         for (int var11 = 0; var11 < 7; var11++) {
            this.level()
               .addParticle(
                  (ParticleOptions)HuntParticles.FLARE_SMOKE.get(),
                  this.getX(),
                  this.getY(),
                  this.getZ(),
                  (this.random.nextDouble() - 0.5) * 0.08,
                  0.045,
                  (this.random.nextDouble() - 0.5) * 0.08
               );
         }
      } else {
         super.handleEntityEvent(var1);
      }
   }

   public void lerpTo(double var1, double var3, double var5, float var7, float var8, int var9) {
      if (this.kind() != Weapon.FLARE_GUN && !this.tethered()) {
         super.lerpTo(var1, var3, var5, var7, var8, var9);
      } else {
         this.visualX = var1;
         this.visualY = var3;
         this.visualZ = var5;
         this.visualSteps = Math.clamp((long)var9, 1, 3);
      }
   }

   private static boolean fish(Entity var0) {
      return var0 instanceof AbstractFish || var0.getType() == EntityType.COD || var0.getType() == EntityType.SALMON;
   }

   private double fishReach() {
      return Math.min(0.6, 0.4 + (double)this.waterTicks * 0.03);
   }

   private static List<ItemStack> catchLoot(ServerPlayer var0, LivingEntity var1) {
      if (var1.getType() == EntityType.COD) {
         return List.of(new ItemStack(Items.COD));
      } else if (var1.getType() == EntityType.SALMON) {
         return List.of(new ItemStack(Items.SALMON));
      } else {
         try {
            ServerLevel var2 = var0.serverLevel();
            LootTable var3 = var2.getServer().reloadableRegistries().getLootTable(var1.getLootTable());
            LootParams var4 = new net.minecraft.world.level.storage.loot.LootParams.Builder(var2)
               .withParameter(LootContextParams.THIS_ENTITY, var1)
               .withParameter(LootContextParams.ORIGIN, var1.position())
               .withParameter(LootContextParams.DAMAGE_SOURCE, var0.damageSources().playerAttack(var0))
               .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, var0)
               .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, var0)
               .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, var0)
               .create(LootContextParamSets.ENTITY);
            ObjectArrayList var5 = var3.getRandomItems(var4);
            if (!var5.isEmpty()) {
               return var5;
            }
         } catch (RuntimeException var6) {
         }

         return List.of(new ItemStack(Items.COD));
      }
   }

   private void tickTether(ServerPlayer var1) {
      if (this.tether.isAlive()
         && !this.tether.isRemoved()
         && var1.getMainHandItem().is(ExpeditionContent.item("bowfishing_bow"))
         && !(this.tether.distanceToSqr(var1) > 900.0)) {
         Vec3 var2 = var1.getEyePosition().add(var1.getLookAngle().scale(0.4)).add(0.0, -0.25, 0.0);
         Vec3 var3 = this.attachmentPosition();
         Vec3 var4 = var2.subtract(var3);
         double var5 = var4.length();
         boolean var7 = var1.isUsingItem() && var1.getUseItem().is(ExpeditionContent.item("bowfishing_bow"));
         if (this.lineLength <= 0.0F) {
            this.lineLength = (float)var5;
         }

         BlockHitResult var8 = this.level().clip(new ClipContext(var3, var2, Block.COLLIDER, Fluid.NONE, this));
         boolean var9 = var8.getType() == Type.BLOCK;
         double var10 = Math.max(0.0, Math.sin((double)this.age * 0.12));
         double var12 = Math.max(0.0, var5 - (double)this.lineLength);
         float var14 = (float)Math.clamp(var12 * 0.65 + (var7 ? 0.24 : 0.0) + var10 * (double)this.stamina * 0.45 + (var9 ? 0.8 : 0.0), 0.0, 1.0);
         if ((double)var14 > 0.93 && var7) {
            this.tensionTicks++;
         } else {
            this.tensionTicks = Math.max(0, this.tensionTicks - 2);
         }

         if (this.tensionTicks > 30) {
            var1.displayClientMessage(Component.literal("Bowfishing line snapped"), true);
            this.discard();
         } else {
            if (!var9) {
               if (var7) {
                  float var15 = (float)(0.055 * (1.0 - 0.55 * (double)var14) * (1.0 + 0.08 * (double)ExpeditionService.skill(var1, 3)));
                  this.lineLength = Math.max(0.45F, this.lineLength - var15);
                  this.crank += var15 / 0.43F * (float) (Math.PI * 2);
               }

               if ((double)var14 > 0.18 && (double)var14 < 0.9) {
                  this.stamina = Math.max(0.0F, this.stamina - (var7 ? 0.01F : 0.0025F) * (1.0F + 0.12F * (float)ExpeditionService.skill(var1, 3)));
               }

               double var19 = Math.clamp((var5 - (double)this.lineLength) * 0.16, 0.0, 0.16);
               if (var19 > 0.0 && var5 > 0.01) {
                  this.tether.setDeltaMovement(this.tether.getDeltaMovement().scale(0.86).add(var4.scale(var19 / var5)));
                  this.tether.hurtMarked = true;
               }

               if (this.tether.isInWater() && this.stamina > 0.0F) {
                  Vec3 var17 = var4.multiply(1.0, 0.0, 1.0).normalize().scale(-0.015 * var10 * (double)this.stamina);
                  this.tether.setDeltaMovement(this.tether.getDeltaMovement().add(var17));
               }
            }

            this.entityData.set(STRAIN, var14);
            this.entityData.set(FATIGUE, this.stamina);
            this.entityData.set(LINE, this.lineLength);
            this.entityData.set(CRANK, this.crank);
            this.setPos(var3);
            this.setDeltaMovement(Vec3.ZERO);
            if (this.age % 20 == 0) {
               var1.displayClientMessage(
                  Component.literal(var9 ? "Line snagged: ease off and change position" : "Hold use to reel · ease off under high tension"), true
               );
            }

            boolean var20 = FishingGear.hasNet(var1);
            if (!var9 && var5 < (var20 ? 3.6 : 2.4) && (double)this.lineLength < (var20 ? 3.6 : 2.5) && var7 && this.stamina < (var20 ? 0.5F : 0.3F)) {
               boolean var16 = true;

               for (ItemStack var18 : catchLoot(var1, this.tether)) {
                  if (var16 && this.tether instanceof AbstractFish) {
                     var18 = FishingGear.weigh(var1, this.tether, var18, var20);
                  }

                  var16 = false;
                  ExpeditionService.give(var1, var18);
               }

               this.tether.discard();
               boolean var22 = true;
               ExpeditionService.record(var1, "fish", "fish", 1, 0.0);
               if (var22 && this.recoverable) {
                  ExpeditionService.give(var1, new ItemStack(ExpeditionContent.item("bowfishing_arrow")));
               }

               if (var22) {
                  this.discard();
               }
            }
         }
      } else {
         this.discard();
      }
   }

   protected void addAdditionalSaveData(CompoundTag var1) {
      super.addAdditionalSaveData(var1);
      ArrowTip var2 = this.tip();
      if (var2 != null) {
         var1.putString("tip", var2.id);
      }

      var1.putBoolean("primitive", this.primitive());
      var1.putBoolean("flare_rest", (Boolean)this.entityData.get(FLARE_REST));
      var1.putLong("flare_burst", (Long)this.entityData.get(FLARE_BURST));
      var1.putString("weapon", this.weapon.name());
      var1.putInt("age", this.age);
      var1.putFloat("power", this.power);
      var1.putBoolean("recoverable", this.recoverable);
      var1.putDouble("ox", this.origin.x);
      var1.putDouble("oy", this.origin.y);
      var1.putDouble("oz", this.origin.z);
      if (this.tetherId != null) {
         var1.putUUID("tether", this.tetherId);
      }

      var1.putFloat("stamina", this.stamina);
      var1.putFloat("line_length", this.lineLength);
      var1.putFloat("crank", this.crank);
      var1.putBoolean("precise_attachment", this.preciseAttachment);
      var1.putDouble("ax", this.attachment.x);
      var1.putDouble("ay", this.attachment.y);
      var1.putDouble("az", this.attachment.z);
   }

   protected void readAdditionalSaveData(CompoundTag var1) {
      super.readAdditionalSaveData(var1);

      try {
         this.weapon = Weapon.valueOf(var1.getString("weapon"));
      } catch (Exception var3) {
      }

      this.entityData.set(KIND, this.weapon.ordinal());
      ArrowTip var2 = ArrowTip.byId(var1.getString("tip"));
      this.entityData.set(TIP, (byte)(var2 != null ? var2.ordinal() : (var1.getBoolean("tracer") ? ArrowTip.TRACER_BROADHEAD.ordinal() : -1)));
      this.entityData.set(PRIMITIVE, var1.getBoolean("primitive"));
      this.entityData.set(FLARE_REST, var1.getBoolean("flare_rest"));
      this.entityData.set(FLARE_BURST, var1.getLong("flare_burst"));
      this.age = var1.getInt("age");
      this.power = Math.clamp(var1.getFloat("power"), 0.0F, 1.0F);
      this.recoverable = var1.getBoolean("recoverable");
      this.origin = new Vec3(var1.getDouble("ox"), var1.getDouble("oy"), var1.getDouble("oz"));
      this.tetherId = var1.hasUUID("tether") ? var1.getUUID("tether") : null;
      this.stamina = var1.contains("stamina") ? Math.clamp(var1.getFloat("stamina"), 0.0F, 1.0F) : 1.0F;
      this.lineLength = Float.isFinite(var1.getFloat("line_length")) ? Math.clamp(var1.getFloat("line_length"), 0.0F, 32.0F) : 0.0F;
      this.crank = Float.isFinite(var1.getFloat("crank")) ? var1.getFloat("crank") : 0.0F;
      this.previousCrank = this.crank;
      this.entityData.set(CRANK, this.crank);
      this.entityData.set(LINE, this.lineLength);
      this.preciseAttachment = var1.getBoolean("precise_attachment");
      this.attachment = new Vec3(var1.getDouble("ax"), var1.getDouble("ay"), var1.getDouble("az"));
      if (!Double.isFinite(this.attachment.lengthSqr()) || this.attachment.lengthSqr() > 25.0) {
         this.attachment = Vec3.ZERO;
         this.preciseAttachment = false;
      }
   }
}
