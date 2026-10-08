package com.formaworks.frontierhunts.hunting;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.HuntRules;
import com.formaworks.frontierhunts.HuntService;
import com.formaworks.frontierhunts.HunterLedger;
import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.expedition.CarcassCleanup;
import com.formaworks.frontierhunts.expedition.ExpeditionService;
import com.formaworks.frontierhunts.expedition.FirearmDamage;
import com.formaworks.frontierhunts.expedition.GhillieSuit;
import com.formaworks.frontierhunts.expedition.HuntProjectile;
import com.formaworks.frontierhunts.expedition.ScentControl;
import com.formaworks.frontierhunts.expedition.TreeStandSeat;
import com.formaworks.frontierhunts.expedition.WildlifeLure;
import com.formaworks.frontierhunts.progression.AssignmentService;
import com.formaworks.frontierhunts.tracking.TrailService;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class Whitetail extends PathfinderMob {
   private static final EntityDataAccessor<Float> ALERT = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Boolean> DOWN = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Long> DOWN_AT = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.LONG);
   private static final EntityDataAccessor<Long> HIT_AT = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.LONG);
   private static final EntityDataAccessor<Integer> HIT_KIND = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> FALL_SIDE = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> FALL_SPEED = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Boolean> GRAZING = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<CompoundTag> TRAITS = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.COMPOUND_TAG);
   private static final EntityDataAccessor<CompoundTag> IMPACTS = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.COMPOUND_TAG);
   private static final EntityDataAccessor<Float> FOOT_FR = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> FOOT_FL = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> FOOT_RR = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> FOOT_RL = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Boolean> FOOT_SUPPORTED = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.BOOLEAN);
   private static final List<EntityDataAccessor<Float>> FOOT_HEIGHTS = List.of(FOOT_FR, FOOT_FL, FOOT_RR, FOOT_RL);
   private static final EntityDataAccessor<Byte> BEHAVIOR = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Long> BEHAVIOR_AT = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.LONG);
   private static final EntityDataAccessor<Long> RISE_AT = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.LONG);
   private static final EntityDataAccessor<Integer> CUE = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Long> CUE_AT = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.LONG);
   private static final EntityDataAccessor<Float> TAIL = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> ATTENTION = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Byte> RUT_POSE = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.BYTE); // [routines] 0 none, 1 display, 2 spar, 3 tending
   private static final EntityDataAccessor<Integer> RUT_PARTNER = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.INT); // [rutfight] rival's entity id + 1 (0 none)
   private static final EntityDataAccessor<CompoundTag> SIGN_WORK = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.COMPOUND_TAG); // [deersign] SignAct: rubbing / scraping now
   public static final int BEHAVIOR_NORMAL = 0;
   public static final int BEHAVIOR_BEDDED = 1;
   public static final int BEHAVIOR_SLEEPING = 2;
   public static final int BEHAVIOR_STANCE = 3;
   public static final int BEHAVIOR_FLEEING = 4;
   public static final int BEHAVIOR_INVESTIGATE = 5;
   public static final int BEHAVIOR_WARN = 6;
   public static final int BEHAVIOR_CHARGE = 7;
   public static final int CUE_NONE = 0;
   public static final int CUE_STOMP = 1;
   public static final int CUE_HEADBOB = 2;
   public static final int CUE_LOOK_LEFT = 3;
   public static final int CUE_LOOK_RIGHT = 4;
   public static final int CUE_BUGLE = 5;
   private DeerTraits traits = DeerTraits.REFERENCE;
   private final GameSpecies species;
   private int behaviorTicks;
   private int bedTicks;
   private int calmTicks;
   private int fleeTicks;
   private int zigTicks;
   private int stanceTicks;
   private int nextCueTicks;
   private int vocalTicks;
   private int rutTicks;
   private int investigateTicks;
   private float zigAngle;
   private Vec3 bedTarget;
   private int riseTicks = -1;
   private boolean folded;
   private float foldYaw;
   private Vec3 investigateTarget;
   private boolean lookBackPending;
   private Vec3 threat;
   private Vec3 lastTrack;
   private long lastBloodAt;
   private int bloodSteps;
   private int poolStage;
   private boolean openWound;
   private int memory;
   /** [perf] Distance-based thinking rate (AiThrottle). */
   public final com.formaworks.frontierhunts.perf.AiThrottle.State perfAi = new com.formaworks.frontierhunts.perf.AiThrottle.State();
   private int bleedTicks;
   private int downTicks;
   private int dressingTicks;
   private int grazeTicks;
   private float bloodLoss;
   private boolean heavyTrail;
   private int lossClock;
   private int fatalTicks;
   private int woundCount;
   private Vec3 woundPoint = new Vec3(0.1, 0.72, -0.05);
   private int woundBone = -1;
   private DeerAnimator bodyAnimator;
   private final DeerAnimator.Input bodyInput = new DeerAnimator.Input();
   private float bodyTime = Float.NaN;
   private float bodyAir;
   private float[] hitSurface;
   private float[] hitOrgans;
   private int hitSurfaceTick = Integer.MIN_VALUE;
   private int hitOrgansTick = Integer.MIN_VALUE;
   private boolean applyingProjectile;
   private UUID lastProjectile;
   private long lastProjectileTick = -10L;
   private float grazing;
   private float grazingOld;
   private float travel;
   private float travelOld;
   private float motionSpeed;
   private float motionSpeedOld;
   private final WhitetailRig supportRig = new WhitetailRig();
   private final DeerGrounding grounding = new DeerGrounding();
   private final float[] footing = new float[4];
   private final float[] footingOld = new float[4];
   private final float[] footingPose = new float[4];
   public int collapseTicks;
   private UUID shooter;
   private UUID dressingHunter;
   private double shotDistance;
   private long shotAt = -1L;
   private String shotRegion = "unrecorded";
   private boolean qualifiedShot;
   private boolean harvested;
   private boolean groundImpactPlayed;
   private static final EntityDataAccessor<Integer> DRESS_PROGRESS = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> DRESS_DURATION = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> DRESSER = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Long> DRESS_UPDATE = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.LONG);
   private static final EntityDataAccessor<Long> SEDATED_UNTIL = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.LONG);
   private static final EntityDataAccessor<Long> SEDATED_AT = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.LONG);
   private static final EntityDataAccessor<Long> WAKING_AT = SynchedEntityData.defineId(Whitetail.class, EntityDataSerializers.LONG);
   private boolean beforeSedationNoAi;
   private long harvestedAt;
   private int blockedDressingTicks;
   private int callPauseUntil;
   private int callRestUntil;
   private Vec3 caller = Vec3.ZERO;
   private float callBodyYaw;
   private Rut.Phase rutPhase = Rut.Phase.NONE;
   private long rutCheckedAt = Long.MIN_VALUE;
   private Vec3 approachTarget;
   private int approachTicks;
   private int approachRepath;
   private int signTicks;
   private double approachSpeed = 0.62;
   private int thirst = -1;
   private int wetTicks;
   private int shakeIn;
   private boolean foraging;
   private int annoy;
   private int warnTicks;
   private int chargeTicks;
   private int attackCooldown;
   private int chargeHits;
   private boolean bluff;
   private double bluffStop;
   private int bluffs;
   private int bluffGrace;
   private int calmUntil;
   private int lastAggressionEnd = -1073741824;
   private int lastHurtTick = -1073741824;
   private Player rival;
   private double warnStartDistance;
   private com.formaworks.frontierhunts.hunting.routine.DeerRoutine routineState; // [routines]

   // [routines] daily routine, game trails, scent busts, pressure and rut interactions (hunting.routine)
   public com.formaworks.frontierhunts.hunting.routine.DeerRoutine routine() {
      if (this.routineState == null) {
         this.routineState = new com.formaworks.frontierhunts.hunting.routine.DeerRoutine(this);
      }

      return this.routineState;
   }

   // [routines] synced rut posture (see RutEngine.POSE_*)
   public int rutPose() {
      return this.entityData.get(RUT_POSE);
   }

   // [routines]
   public void setRutPose(int var1) {
      this.entityData.set(RUT_POSE, (byte)var1);
   }

   // [rutfight] the rival this animal is fighting (entity id), or -1
   public int rutPartner() {
      return this.entityData.get(RUT_PARTNER) - 1;
   }

   // [rutfight]
   public void setRutPartner(int id) {
      this.entityData.set(RUT_PARTNER, id < 0 ? 0 : id + 1);
   }

   // [deersign] the rub / scrape this buck is working right now (sign.work.SignAct), synced to clients
   public CompoundTag signWork() {
      return this.entityData.get(SIGN_WORK);
   }

   // [deersign]
   public void setSignWork(CompoundTag tag) {
      this.entityData.set(SIGN_WORK, tag);
   }

   // [routines] routine-driven bedding: lie down here for the given ticks
   public void routineBed(int var1, boolean var2) {
      if (this.behavior() == 0 && !this.downed() && !this.legsFolded()) {
         this.bedTarget = null;
         this.bedTicks = var1;
         this.navigation.stop();
         this.setBehavior(var2 ? 2 : 1);
      }
   }

   // [herds] the group is moving off: get up from the bed within the given ticks
   public void routineWake(int var1) {
      if ((this.behavior() == 1 || this.behavior() == 2) && this.alertness() < 0.16F) {
         this.bedTicks = Math.min(this.bedTicks, Math.max(1, var1));
      }
   }

   // [herds] natural spawn packs of up to six deer / ten elk (vanilla stops at four)
   @Override
   public int getMaxSpawnClusterSize() {
      return com.formaworks.frontierhunts.hunting.herd.HerdService.maxCluster(this.species, super.getMaxSpawnClusterSize());
   }

   // [routines] drop a call approach (a fight or chase took over)
   public void routineCancelCall() {
      this.approachTicks = 0;
      this.approachTarget = null;
   }

   public boolean sedated() {
      return !this.downed() && this.entityData.get(SEDATED_UNTIL) > 0L;
   }

   public int sedationTicksRemaining() {
      return this.sedated() ? (int)Math.max(0L, this.entityData.get(SEDATED_UNTIL) - this.level().getGameTime()) : 0;
   }

   public boolean waking() {
      return !this.downed() && this.entityData.get(WAKING_AT) > 0L && this.level().getGameTime() - this.entityData.get(WAKING_AT) < 42L;
   }

   public boolean harvested() {
      return this.harvested;
   }

   public int skinnerId() {
      return this.entityData.get(DRESSER);
   }

   public float skinningProgress(float var1) {
      float var2 = this.skinnerId() >= 0 ? Math.clamp((float)(this.level().getGameTime() - this.entityData.get(DRESS_UPDATE)) + var1, 0.0F, 2.0F) : 0.0F;
      return Mth.clamp(((float)this.entityData.get(DRESS_PROGRESS).intValue() + var2) / (float)Math.max(1, this.entityData.get(DRESS_DURATION)), 0.0F, 1.0F);
   }

   private void syncDressing() {
      this.entityData.set(DRESS_PROGRESS, this.dressingTicks);
      this.entityData.set(DRESS_UPDATE, this.level().getGameTime());
   }

   private float restPose(float var1) {
      if (this.downed()) {
         return this.collapseProgress(var1);
      } else if (this.sedated()) {
         return Mth.clamp(((float)(this.level().getGameTime() - this.entityData.get(SEDATED_AT)) + var1) / 28.0F, 0.0F, 1.0F);
      } else if (this.waking()) {
         return 1.0F
            - (float)Mth.smoothstep((double)Math.clamp(((float)(this.level().getGameTime() - this.entityData.get(WAKING_AT)) + var1) / 42.0F, 0.0F, 1.0F));
      } else {
         int var2 = this.behavior();
         if (var2 != 1 && var2 != 2) {
            float var3 = this.risingAge(var1);
            return var3 >= 0.0F ? 1.0F - (float)Mth.smoothstep((double)Math.clamp(var3 / 30.0F, 0.0F, 1.0F)) : 0.0F;
         } else {
            return Mth.clamp(this.behaviorAge(var1) / 28.0F, 0.0F, 1.0F);
         }
      }
   }

   public Whitetail(EntityType<? extends Whitetail> var1, Level var2) {
      this(var1, var2, GameSpecies.WHITETAIL);
   }

   public Whitetail(EntityType<? extends Whitetail> var1, Level var2, GameSpecies var3) {
      super(var1, var2);
      this.species = var3;
      this.traits = DeerTraits.reference(var3);
      this.moveControl = new MoveControl(this) {
         @Override
         public void tick() {
            float var1 = Whitetail.this.getSpeed();
            super.tick();
            Whitetail.this.setSpeed(Mth.lerp(0.22F, var1, Whitetail.this.getSpeed()));
         }
      };
      if (!var2.isClientSide) {
         this.entityData.set(VARIANT, this.random.nextInt(8));
      }
   }

   public static Builder attributes() {
      return attributes(GameSpecies.WHITETAIL);
   }

   public static Builder attributes(GameSpecies var0) {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, var0.health)
         .add(Attributes.MOVEMENT_SPEED, var0.speed)
         .add(Attributes.FOLLOW_RANGE, 56.0)
         .add(Attributes.STEP_HEIGHT, 1.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, var0.defendsTerritory() ? 0.9 : 0.6)
         .add(Attributes.ATTACK_DAMAGE, var0.defendsTerritory() ? 9.0 : 2.0)
         .add(Attributes.ATTACK_KNOCKBACK, var0.defendsTerritory() ? 2.2 : 0.0);
   }

   public static boolean canSpawn(EntityType<Whitetail> var0, ServerLevelAccessor var1, MobSpawnType var2, BlockPos var3, RandomSource var4) {
      if (HuntRules.active(var1.getLevel()) || HuntConfig.WHITETAILS_OUTSIDE_RESERVE.get() && var1.getLevel().dimension().equals(Level.OVERWORLD)) {
         BlockState var5 = var1.getBlockState(var3.below());
         boolean var6 = var5.is(BlockTags.ANIMALS_SPAWNABLE_ON) || var5.is(BlockTags.DIRT) || var5.is(Blocks.SNOW_BLOCK) || var5.is(Blocks.MOSS_BLOCK);
         if (!var6) {
            return false;
         } else {
            int var7 = var1.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var3.getX(), var3.getZ());
            return var3.getY() >= var7 - 2 && var3.getY() <= var7 + 3
               ? var2 != MobSpawnType.NATURAL
                  || var1.getEntitiesOfClass(Whitetail.class, new AABB(var3).inflate(64.0), var1x -> !var1x.downed() && var1x.getType() == var0).size()
                     < speciesOf(var0).behavior.herdCap()
               : false;
         }
      } else {
         return false;
      }
   }

   private static GameSpecies speciesOf(EntityType<?> var0) {
      for (Entry var2 : HuntEntities.GAME.entrySet()) {
         if (((DeferredHolder)var2.getValue()).get() == var0) {
            return (GameSpecies)var2.getKey();
         }
      }

      return GameSpecies.WHITETAIL;
   }

   double speedFor(double var1) {
      double var3 = var1 * (double)this.traits.frameLength();
      return Math.sqrt(Math.max(0.0, var3) / 31.9) / Math.max(0.05, this.species.speed);
   }

   boolean whitetail() {
      return this.species == GameSpecies.WHITETAIL;
   }

   @Override
   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder var1) {
      super.defineSynchedData(var1);
      var1.define(ALERT, 0.0F);
      var1.define(DOWN, false);
      var1.define(DOWN_AT, -1L);
      var1.define(GRAZING, false);
      var1.define(VARIANT, 0);
      var1.define(HIT_AT, -1L);
      var1.define(HIT_KIND, 0);
      var1.define(FALL_SIDE, 1.0F);
      var1.define(FALL_SPEED, 0.0F);
      var1.define(TRAITS, DeerTraits.REFERENCE.save());
      var1.define(IMPACTS, new CompoundTag());
      var1.define(FOOT_FR, 0.0F);
      var1.define(FOOT_FL, 0.0F);
      var1.define(FOOT_RR, 0.0F);
      var1.define(FOOT_RL, 0.0F);
      var1.define(FOOT_SUPPORTED, false);
      var1.define(DRESS_PROGRESS, 0);
      var1.define(DRESS_DURATION, 160);
      var1.define(DRESSER, -1);
      var1.define(DRESS_UPDATE, 0L);
      var1.define(SEDATED_UNTIL, 0L);
      var1.define(SEDATED_AT, 0L);
      var1.define(WAKING_AT, 0L);
      var1.define(BEHAVIOR, (byte)0);
      var1.define(BEHAVIOR_AT, 0L);
      var1.define(RISE_AT, 0L);
      var1.define(CUE, 0);
      var1.define(CUE_AT, 0L);
      var1.define(TAIL, 0.0F);
      var1.define(ATTENTION, Float.NaN);
      var1.define(RUT_POSE, (byte)0); // [routines]
      var1.define(RUT_PARTNER, 0); // [rutfight]
      var1.define(SIGN_WORK, new CompoundTag()); // [deersign]
   }

   public GameSpecies species() {
      return this.species;
   }

   public DeerTraits traits() {
      return this.traits;
   }

   public void setTraits(DeerTraits var1) {
      if (this.level().isClientSide) {
         throw new IllegalStateException("Only the server assigns wildlife traits");
      } else {
         this.entityData.set(TRAITS, var1.withSpecies(this.species).save());
      }
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> var1) {
      super.onSyncedDataUpdated(var1);
      if (var1.equals(TRAITS)) {
         this.traits = DeerTraits.load(this.entityData.get(TRAITS)).withSpecies(this.species);
         this.refreshDimensions();
      }
   }

   @Override
   public EntityDimensions getDefaultDimensions(Pose var1) {
      DeerTraits var2 = this.traits == null ? DeerTraits.reference(this.species) : this.traits;
      return super.getDefaultDimensions(var1).scale(Math.max(var2.widthScale(), var2.lengthScale()), var2.heightScale());
   }

   @Override
   public SpawnGroupData finalizeSpawn(ServerLevelAccessor var1, DifficultyInstance var2, MobSpawnType var3, SpawnGroupData var4) {
      SpawnGroupData var5 = super.finalizeSpawn(var1, var2, var3, var4);
      SpawnGroupData herdsPack = com.formaworks.frontierhunts.hunting.herd.HerdSpawn.finalize(this, var3, var4); // [herds] natural spawns come as real groups
      if (herdsPack != null) {
         return herdsPack;
      }

      if (var5 instanceof Whitetail.DeerGroup var6) {
         boolean var9 = var6.bachelor ? this.random.nextFloat() < 0.92F : this.random.nextFloat() < 0.22F;
         if (!var6.bachelor && !var9) {
            this.setTraits(DeerTraits.random(this.species, this.random, false));
         } else {
            DeerTraits var8 = DeerTraits.random(this.species, this.random, var9);
            if (!var6.bachelor && var9 && !var8.yearling()) {
               var8 = new DeerTraits(
                  true,
                  12 + this.random.nextInt(12),
                  var8.frame(),
                  var8.condition(),
                  var8.rackGenes(),
                  var8.seed(),
                  var8.abnormal() > 2 ? 0 : var8.abnormal(),
                  var8.coat()
               );
            }

            this.setTraits(var8);
         }

         return var5;
      } else if (var3 != MobSpawnType.NATURAL && var3 != MobSpawnType.CHUNK_GENERATION) {
         this.setTraits(DeerTraits.random(this.species, this.random, this.random.nextBoolean()));
         return var5;
      } else if (this.species.behavior.herdRadius() <= 0.0F) {
         this.setTraits(DeerTraits.random(this.species, this.random, this.random.nextFloat() < 0.5F));
         return var5;
      } else {
         boolean var7 = this.random.nextFloat() < this.species.behavior.bachelorChance();
         this.setTraits(DeerTraits.random(this.species, this.random, var7 || this.random.nextFloat() < 0.1F));
         return new Whitetail.DeerGroup(var7);
      }
   }

   public int behavior() {
      return this.entityData.get(BEHAVIOR);
   }

   public float behaviorAge(float var1) {
      return Math.max(0.0F, (float)(this.level().getGameTime() - this.entityData.get(BEHAVIOR_AT)) + var1);
   }

   public float risingAge(float var1) {
      long var2 = this.entityData.get(RISE_AT);
      if (var2 <= 0L) {
         return -1.0F;
      } else {
         float var4 = (float)(this.level().getGameTime() - var2) + var1;
         return var4 > 36.0F ? -1.0F : Math.max(0.0F, var4);
      }
   }

   public int cue() {
      return this.entityData.get(CUE) & 15;
   }

   public float cueAge(float var1) {
      return Math.max(0.0F, (float)(this.level().getGameTime() - this.entityData.get(CUE_AT)) + var1);
   }

   public float tailRaise() {
      return this.entityData.get(TAIL);
   }

   public float attentionBearing() {
      return this.entityData.get(ATTENTION);
   }

   public float fallSide() {
      return this.entityData.get(FALL_SIDE);
   }

   public float sedationAge(float var1) {
      return Math.max(0.0F, (float)(this.level().getGameTime() - this.entityData.get(SEDATED_AT)) + var1);
   }

   public float wakingAge(float var1) {
      return Math.max(0.0F, (float)(this.level().getGameTime() - this.entityData.get(WAKING_AT)) + var1);
   }

   public boolean downed() {
      return this.entityData.get(DOWN);
   }

   public float alertness() {
      return this.entityData.get(ALERT);
   }

   public int variant() {
      return this.entityData.get(VARIANT);
   }

   public int massKg() {
      return this.traits.massKg();
   }

   public boolean bleeding() {
      return this.bleedTicks > 0;
   }

   public int woundTicks() {
      return this.bleedTicks;
   }

   // [tracking] wound region for truthful blood trails, and wounded animals bedding down along the trail
   public String shotRegion() {
      return this.shotRegion;
   }

   // [tracking]
   public void woundBed(int ticks) {
      if (!this.downed() && this.behavior() == 0) {
         this.bedTarget = null;
         this.bedTicks = Math.max(this.bedTicks, ticks);
         this.navigation.stop();
         this.setBehavior(1);
      }
   }

   public int fatalWoundTicks() {
      return this.fatalTicks;
   }

   public double fleeSpeed() {
      if (this.whitetail()) {
         return 0.9 * (this.bleeding() ? 1.3 + 0.6 * (double)Math.min(1.0F, this.getHealth() / 12.0F) : 1.8);
      } else {
         GameSpecies.Behavior var1 = this.species.behavior;
         boolean var2 = this.bleeding() || this.alertness() > 0.9F || this.threat != null && this.threat.distanceToSqr(this.position()) < 576.0;
         double var3 = var2 ? (double)var1.panicSpeed() : (double)var1.warySpeed();
         if (this.bleeding()) {
            var3 *= 0.62 + 0.38 * (double)Math.min(1.0F, this.getHealth() / (float)this.species.health);
         }

         return this.speedFor(var3);
      }
   }

   public int reaction() {
      return this.entityData.get(HIT_KIND);
   }

   public float reactionAge(float var1) {
      long var2 = this.entityData.get(HIT_AT);
      return var2 < 0L ? 1000.0F : Math.max(0.0F, (float)(this.level().getGameTime() - var2) + var1);
   }

   public float collapseProgress(float var1) {
      return this.downed() ? Mth.clamp(((float)(this.level().getGameTime() - this.entityData.get(DOWN_AT)) + var1) / 36.0F, 0.0F, 1.0F) : 0.0F;
   }

   public boolean startGrazing() {
      if (!this.level().isClientSide
         && !com.formaworks.frontierhunts.hunting.herd.HerdService.sentinel(this) // [herds] someone in the group keeps its head up
         && !this.foraging
         && !this.downed()
         && this.onGround()
         && !(this.alertness() >= 0.15F)
         && this.navigation.isDone()
         && !(this.motionSpeed >= 0.015F)) {
         this.grazeTicks = 100 + this.random.nextInt(100);
         this.entityData.set(GRAZING, true);
         return true;
      } else {
         return false;
      }
   }

   public float graze(float var1) {
      return Mth.lerp(var1, this.grazingOld, this.grazing);
   }

   public float travel(float var1) {
      return Mth.lerp(var1, this.travelOld, this.travel);
   }

   public float motionSpeed(float var1) {
      return Mth.lerp(var1, this.motionSpeedOld, this.motionSpeed);
   }

   public void animatorInput(DeerAnimator.Input var1, float var2, float var3) {
      DeerTraits var4 = this.traits();
      var1.time = ((float)this.tickCount + var2) / 20.0F;
      var1.speed = this.motionSpeed(var2) * 20.0F / Math.max(0.4F, var4.frameLength());
      var1.airborne = !this.onGround() && !this.isInWaterOrBubble() && !this.downed();
      var1.airTime = var3;
      var1.graze = this.graze(var2);
      var1.alert = this.alertness();
      int var5 = this.behavior();
      var1.stance = var5 == 3 ? 1.0F : 0.0F;
      var1.bedded = var5 == 1 || var5 == 2;
      var1.sleeping = var5 == 2;
      var1.bedAge = var1.bedded ? this.behaviorAge(var2) / 20.0F : 0.0F;
      var1.rising = this.risingAge(var2) >= 0.0F;
      var1.riseAge = Math.max(0.0F, this.risingAge(var2)) / 20.0F;
      var1.riseHurry = var5 == 4 || var5 == 7;
      var1.cue = this.cue();
      var1.cueAge = this.cueAge(var2) / 20.0F;
      var1.downed = this.downed();
      var1.downAge = this.collapseProgress(var2) * 36.0F / 20.0F;
      var1.fallLeft = this.fallSide() > 0.0F;
      var1.sedated = this.sedated();
      var1.sedatedAge = var1.sedated ? this.sedationAge(var2) / 20.0F : 0.0F;
      var1.waking = this.waking();
      var1.wakingAge = var1.waking ? this.wakingAge(var2) / 20.0F : 0.0F;
      float var6 = Mth.wrapDegrees(Mth.rotLerp(var2, this.yHeadRotO, this.yHeadRot) - Mth.rotLerp(var2, this.yBodyRotO, this.yBodyRot));
      var1.headYaw = -Mth.clamp(var6, -75.0F, 75.0F) * (float) (Math.PI / 180.0);
      var1.headPitch = -this.getViewXRot(var2) * (float) (Math.PI / 180.0) * 0.8F;
      var1.tailRaise = this.tailRaise();
      float var7 = this.attentionBearing();
      var1.earYaw = Float.isNaN(var7) ? 0.0F : -Mth.wrapDegrees(var7 - Mth.rotLerp(var2, this.yHeadRotO, this.yHeadRot)) * (float) (Math.PI / 180.0);
      var1.hitAge = this.reactionAge(var2) / 20.0F;
      var1.hitKind = this.reaction();
      var1.fleeing = this.behavior() != 4 && this.behavior() != 7 && !this.bleeding() ? 0.0F : 1.0F;
      var1.aggression = var5 != 6 && var5 != 7 ? 0.0F : 1.0F;
      if (var5 == 6) {
         var1.stance = 1.0F;
      }

      var1.buck = var4.buck();
      var1.seed = var4.seed() ^ this.getId();
      // [routines] held rut display/spar loops, tending head posture, walking backwards while shoving
      int var8 = this.rutPose();
      var1.cueHold = !var1.downed && (var8 == 1 && var1.cue == 6 || var8 == 2 && var1.cue == 7);
      if (var8 == 3) {
         var1.aggression = Math.max(var1.aggression, 0.55F);
      }

      double var9 = this.getX() - this.xo;
      double var11 = this.getZ() - this.zo;
      float var13 = this.yBodyRot * (float) (Math.PI / 180.0);
      var1.backing = var9 * -Mth.sin(var13) + var11 * Mth.cos(var13) < -0.004;
      com.formaworks.frontierhunts.hunting.rutfight.RutFightState.input(this, var1); // [rutfight] locked-antlers posture
      com.formaworks.frontierhunts.sign.work.SignPose.input(this, var1); // [deersign] rubbing / pawing / licking branch / urinating
   }

   public DeerAnimator bodyPose() {
      if (this.bodyAnimator == null) {
         this.bodyAnimator = new DeerAnimator(this.species);
      }

      float var1 = (float)this.tickCount / 20.0F;
      if (var1 == this.bodyTime) {
         return this.bodyAnimator;
      } else {
         if (Float.isNaN(this.bodyTime) || var1 - this.bodyTime > 0.5F || var1 < this.bodyTime) {
            for (int var2 = 6; var2 >= 1; var2--) {
               this.animatorInput(this.bodyInput, 0.0F, 0.0F);
               this.bodyInput.time = var1 - (float)var2 * 0.1F;
               this.bodyAnimator.update(this.bodyInput);
            }
         }

         this.bodyAir = !this.onGround() && !this.isInWaterOrBubble() && !this.downed()
            ? this.bodyAir + Math.max(0.0F, var1 - (Float.isNaN(this.bodyTime) ? var1 : this.bodyTime))
            : 0.0F;
         this.animatorInput(this.bodyInput, 0.0F, this.bodyAir);
         this.bodyAnimator.update(this.bodyInput);
         this.bodyTime = var1;
         return this.bodyAnimator;
      }
   }

   public float[] hitSurface() {
      if (this.hitSurfaceTick != this.tickCount || this.hitSurface == null) {
         DeerMeshData var1 = DeerMeshData.of(this.species);
         if (this.hitSurface == null) {
            this.hitSurface = new float[var1.vertices * 3];
         }

         var1.skin(2, this.bodyPose().skin, this.traits.neckGirth(), this.traits.headScale(), 1.0F, this.hitSurface, null);
         this.hitSurfaceTick = this.tickCount;
      }

      return this.hitSurface;
   }

   public float[] hitOrgans() {
      if (this.hitOrgansTick != this.tickCount || this.hitOrgans == null) {
         DeerAnatomyMesh var1 = DeerAnatomyMesh.of(this.species);
         if (this.hitOrgans == null) {
            this.hitOrgans = new float[var1.vertices * 3];
         }

         var1.skin(this.bodyPose().skin, this.hitOrgans, null);
         this.hitOrgansTick = this.tickCount;
      }

      return this.hitOrgans;
   }

   private int surfaceBone(Vec3 var1, Vec3[] var2) {
      DeerTraits var3 = this.traits;
      Vec3 var4 = DeerAnatomy.local(var1, this.position(), this.yBodyRot);
      float var5 = (float)(var4.x / (double)var3.frameWidth());
      float var6 = (float)(var4.y / (double)var3.frameHeight());
      float var7 = (float)(var4.z / (double)var3.frameLength());
      DeerMeshData var8 = DeerMeshData.of(this.species);
      float[] var9 = this.hitSurface();
      int[] var10 = var8.lodVertices[2];
      int var11 = var10[0];
      float var12 = Float.MAX_VALUE;

      for (int var16 : var10) {
         float var17 = var9[var16 * 3] - var5;
         float var18 = var9[var16 * 3 + 1] - var6;
         float var19 = var9[var16 * 3 + 2] - var7;
         float var20 = var17 * var17 + var18 * var18 + var19 * var19;
         if (var20 < var12) {
            var12 = var20;
            var11 = var16;
         }
      }

      int var21 = 0;
      float var22 = -1.0F;

      for (int var23 = 0; var23 < 4; var23++) {
         if (var8.weights[var11 * 4 + var23] > var22) {
            var22 = var8.weights[var11 * 4 + var23];
            var21 = var8.joints[var11 * 4 + var23] & 255;
         }
      }

      Vector3f var24 = new Matrix4f(this.bodyPose().skin[var21]).invert().transformPosition(new Vector3f(var5, var6, var7));
      var2[0] = new Vec3((double)var24.x, (double)var24.y, (double)var24.z);
      return var21;
   }

   public void poseRig(WhitetailRig var1, float var2) {
      this.poseRig(var1, var2, true);
   }

   private void poseRig(WhitetailRig var1, float var2, boolean var3) {
      float var4 = -Mth.clamp(
            Mth.wrapDegrees(Mth.rotLerp(var2, this.yHeadRotO, this.yHeadRot) - Mth.rotLerp(var2, this.yBodyRotO, this.yBodyRot)), -42.0F, 42.0F
         )
         * (float) (Math.PI / 180.0);
      boolean var5 = false;

      for (int var6 = 0; var6 < 4; var6++) {
         this.footingPose[var6] = Mth.lerp(var2, this.footingOld[var6], this.footing[var6]);
         var5 |= Math.abs(this.footingPose[var6]) > 0.001F;
      }

      var1.pose(
         (float)this.tickCount + var2,
         this.travel(var2) / this.traits.lengthScale(),
         this.motionSpeed(var2) / this.traits.lengthScale(),
         this.graze(var2),
         this.alertness(),
         var4,
         this.getXRot() * -0.008F,
         this.restPose(var2),
         this.reactionAge(var2),
         this.reaction(),
         this.entityData.get(FALL_SIDE),
         this.entityData.get(FALL_SPEED),
         var3 && var5 ? this.footingPose : null
      );
   }

   public boolean tranquilize() {
      if (!this.level().isClientSide && !this.downed()) {
         if (!this.sedated() && !this.waking()) {
            this.beforeSedationNoAi = this.isNoAi();
         }

         if (!this.sedated()) {
            this.entityData.set(SEDATED_AT, this.level().getGameTime());
            this.entityData.set(FALL_SPEED, 0.0F);
         }

         this.entityData.set(SEDATED_UNTIL, this.level().getGameTime() + 3600L);
         this.entityData.set(WAKING_AT, 0L);
         this.entityData.set(GRAZING, false);
         this.callPauseUntil = 0;
         this.grazeTicks = 0;
         this.navigation.stop();
         this.setTarget(null);
         this.setNoAi(true);
         this.setPersistenceRequired();
         this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
         return true;
      } else {
         return false;
      }
   }

   private void sedationTick() {
      if (!this.downed()) {
         if (this.sedated()) {
            this.navigation.stop();
            this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
            if (this.level().getGameTime() >= this.entityData.get(SEDATED_UNTIL)) {
               this.entityData.set(SEDATED_UNTIL, 0L);
               this.entityData.set(WAKING_AT, this.level().getGameTime());
            }
         } else if (this.entityData.get(WAKING_AT) > 0L && !this.waking()) {
            this.entityData.set(WAKING_AT, 0L);
            this.setNoAi(this.beforeSedationNoAi);
         }
      }
   }

   public Rut.Phase rut() {
      if (!(this.level() instanceof ServerLevel var1)) {
         return this.rutPhase;
      } else {
         long var4 = var1.getGameTime();
         if (this.rutCheckedAt == Long.MIN_VALUE || var4 - this.rutCheckedAt >= 200L || var4 < this.rutCheckedAt) {
            this.rutCheckedAt = var4;
            this.rutPhase = Rut.phase(this.species, var1);
         }

         return this.rutPhase;
      }
   }

   public boolean approachingCall() {
      return this.approachTicks > 0 && this.approachTarget != null;
   }

   public boolean approachCall(Vec3 var1, float var2, boolean var3) {
      if (this.downed() || this.sedated() || this.waking() || this.bleeding() || var1 == null || !(this.level() instanceof ServerLevel var4)
         || com.formaworks.frontierhunts.hunting.routine.RoutineHooks.rutBusy(this)) { // [routines] busy fighting / tending
         return false;
      } else if (this.tickCount < this.callRestUntil || this.alertness() > 0.58F || var2 <= 0.18F) {
         return false;
      } else if (var1.distanceToSqr(this.position()) > 9216.0) {
         return false;
      } else {
         Vec3 var9 = var1;
         if (this.traits.buck() && this.traits.ageMonths() >= 36) {
            Wilderness.Wind var6 = Wilderness.wind(var4.getSeed(), var4.getGameTime(), var4.isRaining(), var4.isThundering());
            double var7 = var6.speed();
            if (var7 > 0.2) {
               var9 = var1.add(var6.east() / var7 * 9.0, 0.0, var6.south() / var7 * 9.0);
            }
         }

         this.approachTarget = var9;
         com.formaworks.frontierhunts.camps.CampHooks.called(this); // [camps] "called in" flag for Rut Rally
         com.formaworks.frontierhunts.journal.JournalHooks.called(this, var1); // [journal] called in an animal
         this.approachSpeed = var3 ? 0.88 : 0.62;
         this.approachTicks = (int)(140.0F + 220.0F * Math.clamp(var2, 0.0F, 1.4F));
         this.approachRepath = 0;
         this.callRestUntil = this.tickCount + this.approachTicks + 320;
         this.grazeTicks = 0;
         this.entityData.set(GRAZING, false);
         if (this.behavior() == 1 || this.behavior() == 2) {
            this.setBehavior(0);
         }

         this.cue(var3 ? 2 : 1);
         com.formaworks.frontierhunts.hunting.routine.RoutineHooks.onCall(this, var3); // [routines]
         if (this.vocalTicks == 0 && this.traits.buck() && this.random.nextFloat() < (var3 ? 0.7F : 0.35F)) {
            this.vocal(HuntSounds.DEER_GRUNT.get(), 0.8F);
            this.vocalTicks = 200;
         }

         return true;
      }
   }

   private void rutWork() {
      if (this.level() instanceof ServerLevel var1 && this.traits.buck() && !this.downed() && !this.bleeding() && !this.sedated() && !this.waking()
         && !com.formaworks.frontierhunts.hunting.routine.RoutineHooks.rutBusy(this)) { // [routines]
         com.formaworks.frontierhunts.sign.work.SignWork.rutCheck(this, var1); // [deersign] rubs and scrapes only where a buck works a tree / the ground (SignWork)
         return;
      }
   }

   public boolean respondingToCall() {
      return !this.downed() && !this.sedated() && !this.waking() && !this.bleeding() && this.tickCount < this.callPauseUntil;
   }

   public void respondToCall(Vec3 var1) {
      if (!this.downed()
         && !this.sedated()
         && !this.waking()
         && !this.bleeding()
         && this.tickCount >= this.callRestUntil
         && !(var1.distanceToSqr(this.position()) > 4096.0)
         && !com.formaworks.frontierhunts.hunting.routine.RoutineHooks.rutBusy(this)) { // [routines]
         this.caller = var1;
         this.callBodyYaw = this.yBodyRot;
         this.callPauseUntil = this.tickCount + 60;
         this.callRestUntil = this.tickCount + 220;
         this.grazeTicks = 0;
         this.entityData.set(GRAZING, false);
         this.navigation.stop();
         if (this.behavior() == 1 || this.behavior() == 2) {
            this.setBehavior(0);
         }

         if (!this.whitetail()) {
            if (this.random.nextFloat() < 0.35F && this.vocalTicks == 0) {
               this.contactCall();
               this.vocalTicks = 300;
            }
         } else if (this.traits.buck() && !this.traits.yearling() && this.random.nextFloat() < 0.3F && this.vocalTicks == 0) {
            this.vocal(HuntSounds.DEER_WHEEZE.get(), 1.0F);
            this.vocalTicks = 200;
         } else if (this.random.nextFloat() < 0.35F && this.vocalTicks == 0) {
            this.vocal(this.traits.buck() ? HuntSounds.DEER_GRUNT.get() : HuntSounds.DEER_BLEAT.get(), 0.8F);
            this.vocalTicks = 160;
         }
      }
   }

   @Override
   protected void registerGoals() {
      this.goalSelector
         .addGoal(
            -1,
            new Goal() {
               {
                  this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
               }

               @Override
               public boolean canUse() {
                  return !Whitetail.this.downed()
                     && !Whitetail.this.sedated()
                     && !Whitetail.this.waking()
                     && !Whitetail.this.bleeding()
                     && Whitetail.this.tickCount < Whitetail.this.callPauseUntil;
               }

               @Override
               public void tick() {
                  Whitetail.this.navigation.stop();
                  Whitetail.this.setDeltaMovement(Whitetail.this.getDeltaMovement().multiply(0.0, 1.0, 0.0));
                  float var1 = (float)(
                        Math.atan2(Whitetail.this.caller.z - Whitetail.this.getZ(), Whitetail.this.caller.x - Whitetail.this.getX()) * 180.0F / (float)Math.PI
                     )
                     - 90.0F;
                  float var2 = Whitetail.this.callBodyYaw + Mth.clamp(Mth.wrapDegrees(var1 - Whitetail.this.callBodyYaw), -40.0F, 40.0F);
                  double var3 = (double)(var2 * (float) (Math.PI / 180.0));
                  Whitetail.this.getLookControl()
                     .setLookAt(
                        Whitetail.this.getX() - Math.sin(var3) * 4.0, Whitetail.this.getEyeY() + 0.15, Whitetail.this.getZ() + Math.cos(var3) * 4.0, 4.0F, 3.0F
                     );
               }
            }
         );
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector
         .addGoal(
            0,
            new Goal() {
               {
                  this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
               }

               @Override
               public boolean canUse() {
                  return Whitetail.this.approachingCall()
                     && !Whitetail.this.downed()
                     && !Whitetail.this.sedated()
                     && !Whitetail.this.waking()
                     && !Whitetail.this.bleeding()
                     && Whitetail.this.alertness() < 0.62F
                     && Whitetail.this.behavior() == 0;
               }

               @Override
               public boolean requiresUpdateEveryTick() {
                  return true;
               }

               @Override
               public boolean canContinueToUse() {
                  return this.canUse();
               }

               @Override
               public void start() {
                  Whitetail.this.approachRepath = 0;
               }

               @Override
               public void stop() {
                  Whitetail.this.approachTicks = 0;
                  Whitetail.this.approachTarget = null;
                  Whitetail.this.navigation.stop();
               }

               @Override
               public void tick() {
                  Whitetail.this.approachTicks--;
                  Vec3 var1 = Whitetail.this.approachTarget;
                  if (var1 != null) {
                     Whitetail.this.getLookControl().setLookAt(var1.x, var1.y + 1.2, var1.z, 20.0F, 18.0F);
                     double var2 = var1.distanceToSqr(Whitetail.this.position());
                     if (var2 < 36.0) {
                        Whitetail.this.navigation.stop();
                        Whitetail.this.setDeltaMovement(Whitetail.this.getDeltaMovement().multiply(0.4, 1.0, 0.4));
                     } else if (--Whitetail.this.approachRepath <= 0) {
                        Whitetail.this.approachRepath = 30;
                        Whitetail.this.navigation.moveTo(var1.x, var1.y, var1.z, Whitetail.this.approachSpeed);
                     }
                  }
               }
            }
         );
      this.goalSelector.addGoal(1, new DeerBehaviorGoal(this));
      this.goalSelector.addGoal(1, new com.formaworks.frontierhunts.hunting.routine.RutGoal(this)); // [routines]
      this.goalSelector.addGoal(1, new com.formaworks.frontierhunts.sign.work.SignGoal(this)); // [deersign] walks to a tree / scrape and works it
      this.goalSelector.addGoal(3, new com.formaworks.frontierhunts.hunting.routine.RoutineGoal(this)); // [routines]
      this.goalSelector.addGoal(2, new WildlifeLure(this));
      this.goalSelector.addGoal(3, new WildlifeForage(this));
      this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.55, 0.02F) {
         @Override
         public void start() {
            super.start();
            if (!Whitetail.this.whitetail()) {
               Whitetail.this.navigation.setSpeedModifier(Whitetail.this.speedFor((double)Whitetail.this.species.behavior.strollSpeed()));
            }
         }

         @Override
         public boolean canUse() {
            return Whitetail.this.calmForWander() && super.canUse();
         }

         @Override
         public boolean canContinueToUse() {
            return Whitetail.this.calmForWander() && super.canContinueToUse();
         }

         @Override
         protected Vec3 getPosition() {
            Vec3 var1 = super.getPosition();
            Vec3 var2 = Whitetail.this.herdCentre();
            if (var1 != null && var2 != null && !(var2.distanceToSqr(Whitetail.this.position()) < 64.0)) {
               Vec3 var3 = LandRandomPos.getPosTowards(Whitetail.this, 12, 5, var2);
               return var3 != null && Whitetail.this.random.nextFloat() < 0.7F ? var3 : var1;
            } else {
               return var1;
            }
         }
      });
      this.goalSelector.addGoal(5, new RandomLookAroundGoal(this) {
         @Override
         public boolean canUse() {
            return Whitetail.this.calmForLook() && super.canUse();
         }

         @Override
         public boolean canContinueToUse() {
            return Whitetail.this.calmForLook() && super.canContinueToUse();
         }
      });
   }

   boolean calmForWander() {
      int var1 = this.behavior();
      return !this.downed() && this.grazeTicks == 0 && this.alertness() < 0.3F && var1 == 0 && !this.respondingToCall()
         && !com.formaworks.frontierhunts.hunting.routine.RoutineHooks.suppressStroll(this); // [routines] the routine moves it
   }

   boolean calmForLook() {
      int var1 = this.behavior();
      return !this.downed() && !this.respondingToCall() && var1 == 0 && this.navigation.isDone() && this.getDeltaMovement().horizontalDistance() < 0.02;
   }

   @Override
   public void tick() {
      double var1 = this.getX();
      double var3 = this.getZ();
      super.tick();
      this.grazingOld = this.grazing;
      if (!this.level().isClientSide && this.respondingToCall()) {
         this.setYRot(this.callBodyYaw);
         this.yBodyRot = this.callBodyYaw;
      }

      this.holdFoldedPose();

      this.travelOld = this.travel;
      this.motionSpeedOld = this.motionSpeed;
      float var5 = (float)Math.hypot(this.getX() - var1, this.getZ() - var3);
      if (var5 < 1.5F && (!this.downed() || this.collapseTicks < 18)) {
         this.travel += var5;
      }

      this.motionSpeed = Mth.lerp(0.32F, this.motionSpeed, var5 < 1.5F && !this.downed() ? var5 : 0.0F);
      this.grazing = Mth.lerp(0.12F, this.grazing, this.entityData.get(GRAZING) && !this.downed() ? 1.0F : 0.0F);
      if (this.downed()) {
         this.collapseTicks = Math.clamp(this.level().getGameTime() - this.entityData.get(DOWN_AT), 0, 36);
      }

      this.updateFooting();
      if (!this.level().isClientSide) {
         this.sedationTick();
         if (this.downed()) {
            Vec3 var6 = this.getDeltaMovement();
            double var7 = this.collapseTicks < 12 ? 0.88 : (this.collapseTicks < 24 ? 0.65 : 0.0);
            this.setDeltaMovement(var6.x * var7, var6.y, var6.z * var7);
            this.downTicks++;
            if (this.openWound && this.poolStage < 3 && this.downTicks >= 40 + this.poolStage * 70) {
               TrailService.pool(this, this.poolStage++);
            }

            if (!this.groundImpactPlayed && this.collapseTicks >= 11 && this.onGround()) {
               this.groundImpactPlayed = true;
               BlockPos var9 = this.blockPosition().below();
               BlockState var10 = this.level().getBlockState(var9);
               this.level()
                  .playSound(null, this.blockPosition(), var10.getSoundType(this.level(), var9, this).getFallSound(), SoundSource.NEUTRAL, 0.65F, 0.72F);
               if (!var10.isAir()) {
                  ((ServerLevel)this.level())
                     .sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, var10), this.getX(), this.getY() + 0.1, this.getZ(), 8, 0.3, 0.05, 0.3, 0.025);
               }
            }

            if (this.dressingHunter != null && !this.harvested) {
               this.dressTick();
            }
            if (!this.harvested && (this.downTicks & 15) == 0) {
               com.formaworks.frontierhunts.licence.Tagging.watch(this, this.shooter); // [gear21] the tag window
            }

            if (com.formaworks.frontierhunts.ecology.EcologyHooks.carcassGone(this, this.downTicks)) { // [ecology] predator kill rotted to bones
               this.discard();
               return;
            }
            if ((!this.harvested || !CarcassCleanup.automatic(this, this.harvestedAt)) && this.downTicks > 36000
               && !com.formaworks.frontierhunts.ecology.EcologyHooks.predatorKill(this)) { // [ecology] kills keep their own lifetime
               this.discard();
            }
         } else {
            if (this.bleedTicks > 0) {
               com.formaworks.frontierhunts.tracking.BloodTrail.wounded(this); // [tracking]
               if (this.tickCount % 5 == 0) {
                  this.bloodEffect(false);
               }

               if (this.tickCount % 4 == 0) {
                  TrailService.brush(this);
                  if ((this.lastTrack == null || this.position().distanceToSqr(this.lastTrack) > 1.44 || this.level().getGameTime() - this.lastBloodAt >= 60L)
                     && (this.bloodSteps % (this.heavyTrail ? 2 : 6) == (this.heavyTrail ? 1 : 5) ? TrailService.dense(this) : TrailService.blood(this, false))
                     )
                   {
                     this.lastTrack = this.position();
                     this.lastBloodAt = this.level().getGameTime();
                     this.bloodSteps = (this.bloodSteps + 1) % 6;
                  }
               }

               this.bleedTicks--;
               if (++this.lossClock >= 40) {
                  this.lossClock = 0;
                  super.hurt(this.damageSources().generic(), this.bloodLoss);
                  if (this.downed()) {
                     return;
                  }
               }
            }

            if (this.fatalTicks > 0 && --this.fatalTicks == 0) {
               super.hurt(this.damageSources().generic(), this.getHealth() + 1.0F);
               if (this.downed()) {
                  return;
               }
            }

            if (!this.bleeding()
               && this.tickCount % 35 == 0
               && this.onGround()
               && (this.lastTrack == null || this.position().distanceToSqr(this.lastTrack) > 2.56)
               && TrackClue.leave(this, this.bleedTicks > 0)) {
               this.lastTrack = this.position();
            }
         }
      }
   }

   private void updateFooting() {
      System.arraycopy(this.footing, 0, this.footingOld, 0, 4);

      for (int var1 = 0; var1 < 4; var1++) {
         this.footing[var1] = this.entityData.get(FOOT_SUPPORTED) && !this.downed()
            ? Mth.lerp(0.55F, this.footing[var1], this.entityData.get(FOOT_HEIGHTS.get(var1)))
            : 0.0F;
      }
   }

   @Override
   public void travel(Vec3 var1) {
      if (this.respondingToCall() || this.legsFolded()) {
         this.navigation.stop();
         this.setDeltaMovement(this.getDeltaMovement().multiply(0.0, 1.0, 0.0));
         super.travel(Vec3.ZERO);
      } else {
         super.travel(var1);
      }
   }

   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
      if (!this.downed()) {
         if (this.memory > 0) {
            this.memory--;
         } else {
            this.threat = null;
         }

         if (this.grazeTicks > 0) {
            this.grazeTicks--;
         }

         if (!this.navigation.isDone() || this.motionSpeed > 0.035F) {
            this.grazeTicks = 0;
         }

         if (this.isInWater() && this.getFluidHeight(FluidTags.WATER) > this.wadeDepth()) {
            this.getJumpControl().jump();
         }

         this.waterAndThirst();
         if (this.tickCount % 5 == 0 && com.formaworks.frontierhunts.perf.AiThrottle.perceive(this)) { // [perf]
            this.perceive();
         }

         if (this.tickCount % 20 == 0) {
            this.rutWork();
         }

         int var1 = this.behavior();
         if (this.grazeTicks == 0 && var1 == 0 && this.random.nextInt(this.activeHours() ? 160 : 320) == 0) {
            this.startGrazing();
         }

         if (this.alertness() > 0.25F || var1 != 0) {
            this.grazeTicks = 0;
         }

         this.entityData.set(GRAZING, this.grazeTicks > 0);
         if (this.threat != null && this.alertness() > 0.25F && this.alertness() < 0.62F && var1 != 4) {
            this.getLookControl().setLookAt(this.threat.x, this.threat.y + 1.4, this.threat.z, 25.0F, 25.0F);
         }

         this.updateBehavior();
         com.formaworks.frontierhunts.hunting.routine.RoutineHooks.serverTick(this); // [routines]
      }
   }

   private void perceive() {
      ServerLevel var1 = (ServerLevel)this.level();
      Wilderness.Wind var2 = Wilderness.wind(var1.getSeed(), var1.getGameTime(), var1.isRaining(), var1.isThundering());
      double var3 = Wilderness.thermal(var1.getDayTime(), var1.isRaining(), (float)var1.getSkyDarken() / 15.0F);
      float var5 = 0.0F;
      Player var6 = null;
      boolean var7 = this.grazeTicks > 0 || this.behavior() == 2;
      float var8 = var1.isDay() ? 1.0F : 0.75F;
      float var9 = 0.0F;
      float routineScent = 0.0F; // [routines] which sense dominated for the strongest hunter
      float routineOther = 0.0F;

      for (Player var11 : var1.players()) {
         if (var11.isAlive() && !var11.isSpectator() && !var11.isCreative() && !(this.distanceToSqr(var11) > 5184.0)) {
            double var12 = (double)this.distanceTo(var11) / com.formaworks.frontierhunts.hunting.routine.RoutineHooks.alertRange(this); // [routines] pressure widens alert distance
            double var14 = HuntPerception.speed(var11.getUUID());
            int var16 = HunterCover.of(var11);
            boolean var17 = var11.isCrouching();
            boolean var18 = var11.getVehicle() instanceof TreeStandSeat || com.formaworks.frontierhunts.seating.SeatEntity.isSeat(var11.getVehicle()); // [onboard2] a seated hunter is still
            double var19 = 0.0;
            if (var11.isPassenger() && !var18) {
               var19 = 44.0;
            } else if (var14 > 0.1) {
               var19 = var11.isSprinting() ? 24.0 : (var17 ? 0.0 : (var16 == 0 ? 11.0 : 5.0));
            }

            if (!var18 && !var11.onGround() && var11.getDeltaMovement().y > 0.2) {
               var19 = Math.max(var19, 9.0);
            }

            if (var1.isRaining()) {
               var19 *= 0.6;
            }

            var19 *= (double)Math.max(1.0F, this.species.behavior.alertRange());
            var19 *= com.formaworks.frontierhunts.journal.HunterSkills.hearing(var11); // [journal] Soft Steps / Ghost
            double var21 = var19 > 0.0 ? Math.max(0.0, 1.0 - var12 / var19) * 0.24 : 0.0;
            double var23 = 0.0;
            double var25 = 0.0;
            // [clothing] one scent function for every animal: carbon base layer, scent-cover spray, sweat, wet clothes and the
            // journal / meal perks (clothing.Scent); the spray no longer erases scent entirely while in cover
            double var28 = ScentControl.scentMultiplier(var11);
            if (var16 != 0) {
               var25 = Wilderness.scent(var2, this.getX() - var11.getX(), this.getZ() - var11.getZ(), this.getY() - var11.getY(), var1.isRaining(), false, var3)
                  * 0.16
                  * (var16 == 2 ? 0.45 : 0.8)
                  * var28;
            }

            if (var16 == 0) {
               Vec3 var30 = var11.getEyePosition().subtract(this.getEyePosition()).normalize();
               boolean var31 = this.getViewVector(1.0F).dot(var30) > -0.9;
               if (var31 && this.getSensing().hasLineOfSight(var11)) {
                  if (var17) {
                     var23 = var14 > 0.1 && var12 < 4.0 ? (1.0 - var12 / 4.0) * 0.12 : 0.0;
                  } else {
                     var23 = Math.max(0.0, 1.0 - var12 / (double)(40.0F * this.species.behavior.alertRange())) * 0.23;
                     double var32 = var14 < 0.1 ? (var12 < 8.0 ? 0.7 : 0.22) : (var11.isSprinting() ? 1.4 : 1.0);
                     var23 *= var32;
                  }

                  var23 *= (double)var8;
                  if (var7) {
                     var23 *= 0.35;
                  }

                  var23 *= blueClothing(var11) ? 1.25 : 1.0;
                  var23 *= GhillieSuit.sightMultiplier(var11);
                  var23 *= com.formaworks.frontierhunts.journal.HunterSkills.sight(var11); // [journal] Low Profile
               }

               var25 = Wilderness.scent(var2, this.getX() - var11.getX(), this.getZ() - var11.getZ(), this.getY() - var11.getY(), var1.isRaining(), false, var3)
                  * 0.16
                  * var28;
               if (var17) {
                  var25 *= 0.5;
               }
            }

            float var43 = (float)(var21 + var23 + var25);
            if (this.behavior() == 2) {
               var43 *= 0.6F;
            }

            float var44 = !(var21 > 0.0) && (var16 == 0 || !(var25 > 0.01)) ? (var16 != 0 ? 0.0F : (var17 ? 0.45F : 1.0F)) : 1.0F;
            if (var25 > 0.01 && var25 > var21 + var23) {
               var44 = 1.0F; // [routines] a nose full of hunter is never capped by how little the deer can see
            }

            if (var43 > var5) {
               var5 = var43;
               var6 = var11;
               var9 = var44;
               routineScent = (float)var25; // [routines]
               routineOther = (float)(var21 + var23);
            }
         }
      }

      float var34 = this.alertness();

      float var35 = switch ((HuntConfig.Realism)HuntConfig.REALISM.get()) {
         case ASSISTED -> 0.7F;
         case FIELD -> 1.0F;
         case EXPERT -> 1.2F;
      };
      float var36 = Mth.clamp(
         var34
            + var5 * var35 * this.species.behavior.spook() * Rut.wariness(this.rut(), this.traits.buck())
               * com.formaworks.frontierhunts.hunting.routine.RoutineHooks.wariness(this) // [routines] pressure / fight distraction
            - (this.memory > 0 ? 0.004F : 0.026F),
         0.0F,
         1.0F
      );
      if (var5 > 0.0F && var36 > var34) {
         var36 = Math.min(var36, Math.max(var34, var9));
      }

      var36 = com.formaworks.frontierhunts.hunting.routine.RoutineHooks.afterPerceive(this, var6, routineScent, routineOther, var34, var36); // [routines] scent bust

      if (this.bleeding() && this.memory > 0) {
         var36 = Math.max(0.7F, var36);
      }

      if (var6 != null && var5 > 0.03F) {
         this.threat = var6.position();
         this.memory = Math.max(this.memory, 100);
      }

      this.entityData.set(ALERT, var36);
      if (this.threat != null && var36 > 0.15F) {
         this.entityData.set(ATTENTION, (float)(Math.atan2(this.threat.z - this.getZ(), this.threat.x - this.getX()) * 180.0F / (float)Math.PI) - 90.0F);
      } else if (var36 < 0.05F) {
         this.entityData.set(ATTENTION, Float.NaN);
      }

      if (var34 < 0.7F && var36 >= 0.7F) {
         com.formaworks.frontierhunts.guide.FieldSchool.deerSpooked(this, var6, routineScent > routineOther || com.formaworks.frontierhunts.hunting.routine.RoutineHooks.scentBusted(this)); // [guide] winded note + stalk coaching
         if (!com.formaworks.frontierhunts.hunting.routine.RoutineHooks.scentBusted(this)) { // [routines] a scent bust blows instead
            this.alarmCall();
         }

         for (Whitetail var37 : var1.getEntitiesOfClass(Whitetail.class, this.getBoundingBox().inflate(28.0))) {
            if (var37 != this && !var37.downed() && (var37.distanceToSqr(this) < 256.0 || var37.getSensing().hasLineOfSight(this))) {
               var37.alarm(this.threat, 0.72F, 150);
            }
         }
      }
   }

   private static boolean blueClothing(Player var0) {
      for (EquipmentSlot var4 : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS}) {
         DyedItemColor var5 = var0.getItemBySlot(var4).get(DataComponents.DYED_COLOR);
         if (var5 != null) {
            int var6 = var5.rgb();
            int var7 = var6 >> 16 & 0xFF;
            int var8 = var6 >> 8 & 0xFF;
            int var9 = var6 & 0xFF;
            if (var9 > 120 && var9 > var7 + 40 && var9 > var8 + 20) {
               return true;
            }
         }
      }

      return false;
   }

   public void hear(Vec3 var1, double var2) {
      this.hear(var1, var2, 0.45F);
   }

   public void hear(Vec3 var1, double var2, float var4) {
      if (!this.downed() && !this.sedated() && !this.level().isClientSide && var1 != null) {
         double var5 = Math.sqrt(this.distanceToSqr(var1));
         if (!(var5 > var2)) {
            float var7 = (float)(1.0 - var5 / var2);
            if (this.behavior() == 2 || this.behavior() == 1) {
               var7 += 0.1F;
            }

            if (var7 > var4) {
               this.alarm(var1, 0.85F, 400);
            } else {
               this.alarm(var1, 0.32F + var7 * 0.25F, 160);
               this.entityData.set(ATTENTION, (float)(Math.atan2(var1.z - this.getZ(), var1.x - this.getX()) * 180.0F / (float)Math.PI) - 90.0F);
            }
         }
      }
   }

   Vec3 herdCentre() {
      float var1 = this.species.behavior.herdRadius();
      if (var1 <= 0.0F) {
         return null;
      } else {
         List<Whitetail> var2 = this.level()
            .getEntitiesOfClass(
               Whitetail.class, this.getBoundingBox().inflate((double)var1), var1x -> var1x != this && !var1x.downed() && var1x.species == this.species
            );
         if (var2.isEmpty()) {
            return null;
         } else {
            double var3 = 0.0;
            double var5 = 0.0;
            double var7 = 0.0;

            for (Whitetail var10 : var2) {
               var3 += var10.getX();
               var5 += var10.getY();
               var7 += var10.getZ();
            }

            return new Vec3(var3 / (double)var2.size(), var5 / (double)var2.size(), var7 / (double)var2.size());
         }
      }
   }

   double wadeDepth() {
      if (this.whitetail()) {
         return 0.22;
      } else if (this.onGround()) {
         return (double)this.getBbHeight() * 0.55;
      } else {
         double var1 = (double)this.getBbHeight() * 0.55;
         BlockPos var3 = BlockPos.containing(this.getX(), this.getY() + this.getFluidHeight(FluidTags.WATER) - var1 - 0.05, this.getZ());
         boolean var4 = !this.level().getFluidState(var3).is(FluidTags.WATER)
            && !this.level().getBlockState(var3).getCollisionShape(this.level(), var3).isEmpty();
         return var4 ? var1 : 0.4;
      }
   }

   @Override
   public double getFluidJumpThreshold() {
      return this.whitetail() ? super.getFluidJumpThreshold() : this.wadeDepth();
   }

   int thirst() {
      return this.thirst;
   }

   void quench() {
      this.thirst = this.random.nextInt(900);
   }

   void setForaging(boolean var1) {
      this.foraging = var1;
   }

   void forageHeadDown(int var1) {
      this.grazeTicks = var1;
      this.entityData.set(GRAZING, var1 > 0);
   }

   double forageSpeed() {
      return this.whitetail() ? 0.55 : this.speedFor((double)this.species.behavior.strollSpeed());
   }

   boolean calmForForage(boolean var1) {
      return !this.downed()
         && !this.sedated()
         && !this.waking()
         && !this.bleeding()
         && this.behavior() == 0
         && this.tickCount > 100
         && this.alertness() < (var1 ? 0.2F : 0.1F)
         && !this.approachingCall()
         && !this.respondingToCall()
         && !this.isRising()
         && (var1 || this.grazeTicks == 0)
         && !com.formaworks.frontierhunts.hunting.herd.HerdService.holdForage(this); // [herds] no wandering off to browse while the group walks
   }

   private void waterAndThirst() {
      if (this.thirst < 0) {
         this.thirst = this.random.nextInt(4200);
      }

      int var1 = this.behavior();
      this.thirst += var1 != 4 && var1 != 7 ? 1 : 4;
      boolean var2 = this.isInWater() && this.getFluidHeight(FluidTags.WATER) > this.wadeDepth();
      if (var2) {
         this.wetTicks = Math.min(this.wetTicks + 1, 400);
         this.shakeIn = 0;
         this.thirst = Math.min(this.thirst, 600);
      } else if (!this.isInWater()) {
         if (this.wetTicks > 30 && this.onGround()) {
            if (this.shakeIn == 0) {
               this.shakeIn = 10 + this.random.nextInt(25);
            } else if (--this.shakeIn == 0) {
               if (var1 == 0 || var1 == 3 || var1 == 5) {
                  this.shakeOff();
               }

               this.wetTicks = 0;
            }
         } else if (this.wetTicks > 0) {
            this.wetTicks--;
         }
      }
   }

   private void shakeOff() {
      if (this.level() instanceof ServerLevel var1) {
         this.cue(2);
         float var5 = this.getBbWidth();
         float var3 = this.getBbHeight();
         var1.sendParticles(
            ParticleTypes.SPLASH,
            this.getX(),
            this.getY() + (double)var3 * 0.62,
            this.getZ(),
            46,
            (double)var5 * 0.45,
            (double)var3 * 0.22,
            (double)var5 * 0.45,
            0.18
         );
         var1.sendParticles(
            ParticleTypes.FALLING_WATER,
            this.getX(),
            this.getY() + (double)var3 * 0.5,
            this.getZ(),
            18,
            (double)var5 * 0.4,
            (double)var3 * 0.25,
            (double)var5 * 0.4,
            0.0
         );

         float var4 = switch (this.species) {
            case MOOSE -> 0.52F;
            case ELK -> 0.62F;
            default -> 0.82F;
         };
         this.level().playSound(null, this.blockPosition(), SoundEvents.WOLF_SHAKE, SoundSource.NEUTRAL, 0.8F, var4 + this.random.nextFloat() * 0.08F);
      }
   }

   boolean activeHours() {
      long var1 = this.level().getDayTime() % 24000L;
      return var1 > 22500L || var1 < 2500L || var1 > 10500L && var1 < 14500L;
   }

   boolean restingHours() {
      long var1 = this.level().getDayTime() % 24000L;
      return var1 > 3500L && var1 < 10000L;
   }

   void setBehavior(int var1) {
      if (this.behavior() != var1) {
         int var2 = this.behavior();
         if ((var2 == 1 || var2 == 2) && var1 != 1 && var1 != 2) {
            this.entityData.set(RISE_AT, this.level().getGameTime());
         }

         this.entityData.set(BEHAVIOR, (byte)var1);
         if ((var1 == 1 || var1 == 2) && var2 != 1 && var2 != 2 && this.level() instanceof ServerLevel && this.random.nextFloat() < 0.28F) {
            DeerSign.leave(this, DeerSign.Kind.BED);
         }

         this.entityData.set(BEHAVIOR_AT, var2 == 1 && var1 == 2 || var2 == 2 && var1 == 1 ? this.entityData.get(BEHAVIOR_AT) : this.level().getGameTime());
         this.behaviorTicks = 0;
      }
   }

   private Whitetail nearestRivalBuck(double var1) {
      List<Whitetail> var3 = this.level()
         .getEntitiesOfClass(
            Whitetail.class,
            this.getBoundingBox().inflate(var1),
            var1x -> var1x != this && !var1x.downed() && var1x.species == this.species && var1x.traits.buck() && !var1x.traits.yearling()
         );
      Whitetail var4 = null;
      double var5 = Double.MAX_VALUE;

      for (Whitetail var8 : var3) {
         double var9 = this.distanceToSqr(var8);
         if (var9 < var5) {
            var5 = var9;
            var4 = var8;
         }
      }

      return var4;
   }

   void cue(int var1) {
      this.entityData.set(CUE, var1 | (this.entityData.get(CUE) >> 4) + 1 << 4);
      this.entityData.set(CUE_AT, this.level().getGameTime());
   }

   boolean isRising() {
      return this.risingAge(0.0F) >= 0.0F;
   }

   /** Ticks the legs stay folded under the body after leaving the bed (matches the client rise animation). */
   int riseHoldTicks() {
      if (this.riseTicks < 0) {
         DeerSkeleton.Clip var1 = DeerSkeleton.of(this.species).clip("rise");
         this.riseTicks = Math.max(10, Math.round((var1 == null ? 1.5F : var1.duration()) * 20.0F));
      }

      int var2 = this.behavior();
      return var2 != 4 && var2 != 7 ? this.riseTicks : Math.max(6, Math.round((float)this.riseTicks * 0.55F / DeerAnimator.RISE_HURRY));
   }

   /** Lying down, or still getting up: the body must not travel or turn on folded legs. */
   public boolean legsFolded() {
      if (this.downed()) {
         return false;
      } else {
         int var1 = this.behavior();
         if (var1 == 1 || var1 == 2) {
            return true;
         } else {
            float var2 = this.risingAge(0.0F);
            return var2 >= 0.0F && var2 < (float)this.riseHoldTicks();
         }
      }
   }

   private void holdFoldedPose() {
      boolean var1 = this.legsFolded();
      if (!var1) {
         this.folded = false;
      } else {
         if (!this.folded) {
            this.folded = true;
            this.foldYaw = this.yBodyRot;
         }

         this.yBodyRot = this.foldYaw;
         this.yHeadRot = this.foldYaw + Mth.clamp(Mth.wrapDegrees(this.yHeadRot - this.foldYaw), -50.0F, 50.0F);
         if (!this.level().isClientSide) {
            this.setYRot(this.foldYaw);
            this.navigation.stop();
            this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
         }
      }
   }

   private void vocal(SoundEvent var1, float var2) {
      float var3 = (this.traits.buck() ? 0.92F : 1.04F) + this.random.nextFloat() * 0.08F - (this.traits.naturalHeight() - 1.0F) * 0.3F;
      boolean var4 = var1 == HuntSounds.ELK_BUGLE.get()
         || var1 == HuntSounds.ELK_MEW.get()
         || var1 == HuntSounds.ELK_BARK.get()
         || var1 == HuntSounds.MOOSE_GRUNT.get()
         || var1 == HuntSounds.MOOSE_CALL.get()
         || var1 == HuntSounds.MOOSE_THREAT.get();
      // [1.1.6] a volume above 1 only stretches how far a sound carries (range = attenuation x volume); calls are heard
      // within their sounds.json range (about 50-70 blocks), never across the map
      this.level().playSound(null, this.blockPosition(), var1, SoundSource.NEUTRAL, Math.min(1.0F, var2), var4 ? var3 : var3 * this.species.behavior.voicePitch());
   }

   private void alarmCall() {
      SoundEvent var1 = switch (this.species) {
         case MOOSE -> (SoundEvent)HuntSounds.MOOSE_GRUNT.get();
         case ELK -> (SoundEvent)HuntSounds.ELK_BARK.get();
         default -> (SoundEvent)HuntSounds.DEER_SNORT.get();
      };
      this.level()
         .playSound(
            null, this.blockPosition(), var1, SoundSource.NEUTRAL, this.species == GameSpecies.WHITETAIL ? 1.0F : 1.3F, 0.94F + this.random.nextFloat() * 0.12F
         );
   }

   private void contactCall() {
      switch (this.species) {
         case MOOSE:
            this.vocal(this.traits.buck() ? HuntSounds.MOOSE_GRUNT.get() : HuntSounds.MOOSE_CALL.get(), this.traits.buck() ? 1.2F : 1.6F);
            break;
         case ELK:
            this.vocal(this.traits.buck() ? HuntSounds.ELK_BUGLE.get() : HuntSounds.ELK_MEW.get(), this.traits.buck() ? 2.2F : 0.8F);
            break;
         default:
            this.vocal(this.traits.buck() ? HuntSounds.DEER_GRUNT.get() : HuntSounds.DEER_BLEAT.get(), 0.7F);
      }
   }

   private void stompSound() {
      BlockPos var1 = this.blockPosition().below();
      BlockState var2 = this.level().getBlockState(var1);
      SoundType var3 = var2.getSoundType(this.level(), var1, this);
      float var4 = this.species.behavior.stepPitch();
      this.level()
         .playSound(
            null,
            this.blockPosition(),
            HuntSounds.DEER_STOMP.get(),
            SoundSource.NEUTRAL,
            0.9F / Math.max(0.6F, var4),
            (0.9F + this.random.nextFloat() * 0.15F) * var4
         );
      this.level().playSound(null, this.blockPosition(), var3.getStepSound(), SoundSource.NEUTRAL, var3.getVolume() * 0.9F, var3.getPitch() * 0.55F * var4);
   }

   private float territoryRadius() {
      return this.traits.buck() ? 13.0F : 11.0F;
   }

   private boolean weakened() {
      return (double)this.getHealth() < this.species.health * 0.35F;
   }

   private static boolean fairGame(Player var0) {
      return var0 != null && var0.isAlive() && !var0.isSpectator() && !var0.isCreative();
   }

   private void provoked(Entity var1) {
      if (this.species.defendsTerritory() && !this.downed() && !this.sedated() && !this.waking() && var1 instanceof Player var2 && fairGame(var2)) {
         if (!this.weakened() && !(this.random.nextFloat() > (this.traits.buck() ? 0.85F : 0.7F))) {
            this.rival = var2;
            this.startCharge();
            this.bluff = false;
         }

         return;
      }
   }

   private void startWarn(Player var1) {
      this.rival = var1;
      this.warnTicks = 0;
      this.warnStartDistance = (double)this.distanceTo(var1);
      this.bluff = false;
      this.bluffGrace = 0;
      if (this.tickCount - this.lastAggressionEnd > 600) {
         this.bluffs = 0;
      }

      this.navigation.stop();
      this.grazeTicks = 0;
      this.entityData.set(GRAZING, false);
      this.setBehavior(6);
      this.alarm(var1.position(), 0.5F, 200);
   }

   private void startCharge() {
      if (this.rival != null) {
         this.chargeTicks = 0;
         this.attackCooldown = 8;
         this.chargeHits = 0;
         this.annoy = 0;
         boolean var1 = this.bleeding() || this.tickCount - this.lastHurtTick < 400;
         this.bluff = !var1 && this.bluffs < 2 && this.random.nextFloat() < (this.bluffs > 0 ? 0.25F : (this.traits.buck() ? 0.35F : 0.5F));
         double var2 = (double)this.getBbWidth() * 0.5 + (double)this.rival.getBbWidth() * 0.5 + 1.2;
         this.bluffStop = Math.min(3.2 + this.random.nextDouble() * 2.6, (double)this.distanceTo(this.rival) - var2 * 0.5 - 2.5);
         if (this.bluffStop < 2.0) {
            this.bluff = false;
         }

         this.grazeTicks = 0;
         this.entityData.set(GRAZING, false);
         this.setBehavior(7);
         this.threat = this.rival.position();
         this.memory = Math.max(this.memory, 400);
         this.level().playSound(null, this.blockPosition(), HuntSounds.MOOSE_THREAT.get(), SoundSource.HOSTILE, 1.0F, 0.95F + this.random.nextFloat() * 0.08F);
         this.vocalTicks = 40;
      }
   }

   private void endAggression(boolean var1) {
      this.rival = null;
      this.annoy = 0;
      this.navigation.stop();
      this.bluff = false;
      this.bluffGrace = 0;
      this.lastAggressionEnd = this.tickCount;
      if (var1) {
         this.setBehavior(4);
         this.fleeTicks = 0;
      } else {
         this.setBehavior(3);
         this.stanceTicks = 80 + this.random.nextInt(60);
         this.nextCueTicks = 20;
      }
   }

   private void faceRival() {
      float var1 = (float)(Math.atan2(this.rival.getZ() - this.getZ(), this.rival.getX() - this.getX()) * 180.0F / (float)Math.PI) - 90.0F;
      float var2 = Mth.approachDegrees(this.getYRot(), var1, 6.0F);
      this.setYRot(var2);
      this.yBodyRot = var2;
      this.getLookControl().setLookAt(this.rival, 30.0F, 30.0F);
   }

   private boolean defendTerritory() {
      int var1 = this.behavior();
      if (var1 == 6 || var1 == 7) {
         if (!fairGame(this.rival) || this.rival.level() != this.level() || this.distanceToSqr(this.rival) > 1156.0) {
            this.endAggression(false);
            return true;
         }

         if (this.weakened()) {
            this.endAggression(true);
            return false;
         }

         if (HunterCover.of(this.rival) != 0 && var1 == 6) {
            this.endAggression(false);
            return true;
         }
      }

      if (var1 == 6) {
         this.navigation.stop();
         this.setDeltaMovement(this.getDeltaMovement().multiply(0.0, 1.0, 0.0));
         this.faceRival();
         this.entityData.set(TAIL, 0.6F);
         this.warnTicks++;
         if (this.warnTicks % 22 == 1) {
            this.cue(1);
            this.stompSound();
            if (this.vocalTicks == 0) {
               this.level()
                  .playSound(null, this.blockPosition(), HuntSounds.MOOSE_GRUNT.get(), SoundSource.HOSTILE, 1.0F, 0.78F + this.random.nextFloat() * 0.1F);
               this.vocalTicks = 36;
            }
         }

         double var10 = (double)this.distanceTo(this.rival);
         boolean var11 = this.warnTicks <= this.bluffGrace;
         if (var11 && var10 > this.warnStartDistance + 1.5) {
            this.endAggression(false);
            this.calmUntil = this.tickCount + 240;
            return true;
         } else {
            if ((var11 || !(var10 < 6.5))
               && !(var10 < this.warnStartDistance - (var11 ? 1.2 : 2.5))
               && (this.warnTicks <= (this.traits.buck() ? 70 : 95) || !(var10 < (double)this.territoryRadius()))) {
               if (var10 > (double)(this.territoryRadius() + 5.0F) || this.warnTicks > 200) {
                  this.endAggression(false);
               }
            } else {
               this.startCharge();
            }

            return true;
         }
      } else if (var1 == 7) {
         this.chargeTicks++;
         if (this.attackCooldown > 0) {
            this.attackCooldown--;
         }

         this.entityData.set(TAIL, 1.0F);
         double var9 = (double)this.distanceTo(this.rival);
         double var4 = (double)this.getBbWidth() * 0.5 + (double)this.rival.getBbWidth() * 0.5 + 1.2;
         if (this.chargeTicks % 5 == 1 || this.navigation.isDone()) {
            this.navigation.moveTo(this.rival, this.speedFor(this.chargeTicks < 60 ? 5.0 : 4.2));
         }

         this.getLookControl().setLookAt(this.rival, 30.0F, 30.0F);
         if (this.bluff && (this.bleeding() || this.hurtTime > 0)) {
            this.bluff = false;
         }

         if (this.bluff && var9 < this.bluffStop + var4 * 0.5) {
            this.navigation.stop();
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.15, 1.0, 0.15));
            this.faceRival();
            this.cue(1);
            this.stompSound();
            this.stompSound();
            this.level()
               .playSound(null, this.blockPosition(), HuntSounds.MOOSE_GRUNT.get(), SoundSource.HOSTILE, 1.0F, 0.74F + this.random.nextFloat() * 0.08F);
            this.bluff = false;
            this.bluffs++;
            this.bluffGrace = 50 + this.random.nextInt(30);
            this.setBehavior(6);
            this.warnTicks = 1;
            this.warnStartDistance = var9;
            this.vocalTicks = 36;
            return true;
         } else {
            if (var9 <= var4 && this.attackCooldown == 0 && this.getSensing().hasLineOfSight(this.rival)) {
               this.faceRival();
               if (this.doHurtTarget(this.rival)) {
                  Vec3 var12 = this.rival.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
                  if (var12.lengthSqr() > 1.0E-4) {
                     var12 = var12.normalize();
                  }

                  this.rival.push(var12.x * 0.9, 0.42, var12.z * 0.9);
                  this.rival.hurtMarked = true;
                  this.level().playSound(null, this.rival.blockPosition(), HuntSounds.DEER_STOMP.get(), SoundSource.HOSTILE, 1.0F, 0.6F);
               }

               this.attackCooldown = 26;
               this.chargeHits++;
               if (this.chargeHits >= 2 && this.random.nextFloat() < 0.45F) {
                  this.endAggression(false);
                  return true;
               }
            }

            this.leapObstacles();
            if (this.chargeTicks > 420 || var9 > 28.0) {
               this.endAggression(false);
            }

            return true;
         }
      } else {
         if (this.tickCount % 5 == 0 && var1 != 4 && !this.bleeding() && !this.isRising()) {
            Player var2 = null;
            double var3 = (double)this.territoryRadius();

            for (Player var6 : this.level().players()) {
               if (fairGame(var6)) {
                  double var7 = (double)this.distanceTo(var6);
                  if (!(var7 > var3) && HunterCover.of(var6) == 0 && this.getSensing().hasLineOfSight(var6)) {
                     var3 = var7;
                     var2 = var6;
                  }
               }
            }

            if (var2 == null) {
               this.annoy = Math.max(0, this.annoy - 2);
            } else if (this.tickCount < this.calmUntil && var3 > 4.5) {
               this.annoy = Math.max(0, this.annoy - 1);
            } else {
               this.annoy = this.annoy
                  + 1
                  + (var2.isSprinting() ? 2 : 0)
                  + (var3 < (double)this.territoryRadius() * 0.5 ? 2 : 0)
                  + (this.traits.buck() ? 1 : 0);
               if (this.annoy >= 16) {
                  if (var1 != 1 && var1 != 2) {
                     this.startWarn(var2);
                  } else {
                     this.setBehavior(0);
                  }

                  return true;
               }
            }
         }

         return false;
      }
   }

   private void updateBehavior() {
      if (!this.sedated() && !this.waking()) {
         this.behaviorTicks++;
         if (this.vocalTicks > 0) {
            this.vocalTicks--;
         }

         if (!this.species.defendsTerritory() || !this.defendTerritory()) {
            float var1 = this.alertness();
            int var2 = this.behavior();
            boolean var3 = this.threat != null && this.memory > 0 && var1 > 0.62F;
            if (var3 && var2 != 4) {
               if (var2 != 1 && var2 != 2) {
                  this.setBehavior(4);
               } else {
                  this.setBehavior(4);
               }

               this.fleeTicks = 0;
               this.zigTicks = 0;
               boolean routineScentBust = com.formaworks.frontierhunts.hunting.routine.RoutineHooks.bolted(this, this.threat); // [routines] pressure + scent
               com.formaworks.frontierhunts.hunting.herd.HerdService.bolted(this, this.threat); // [herds] the group takes one flight line and runs together
               if (!this.bleeding() && (this.vocalTicks == 0 || routineScentBust) && (routineScentBust || this.random.nextFloat() < 0.55F)) {
                  if (this.whitetail()) {
                     this.vocal(HuntSounds.DEER_BLOW.get(), 1.0F);
                  } else {
                     this.alarmCall();
                  }

                  this.vocalTicks = 60;
               }

               var2 = 4;
            }

            switch (var2) {
               case 1:
               case 2:
                  this.navigation.stop();
                  this.setDeltaMovement(this.getDeltaMovement().multiply(0.0, 1.0, 0.0));
                  this.entityData.set(TAIL, 0.0F);
                  if (this.bedTicks > 200 || !com.formaworks.frontierhunts.weather.SeasonalWeather.beddingWeather(this.level(), this.blockPosition())) this.bedTicks--; // [weather] stay bedded while a storm is overhead
                  if (var1 > 0.16F) {
                     this.setBehavior(var1 > 0.45F ? 3 : 0);
                     this.stanceTicks = 40;
                  } else if (this.bedTicks <= 0) {
                     this.setBehavior(0);
                  } else if (this.behaviorTicks > 200 && this.random.nextInt(400) == 0) {
                     this.setBehavior(var2 == 1 ? 2 : 1);
                  }
                  break;
               case 3:
                  this.navigation.stop();
                  this.entityData.set(TAIL, var1 > 0.45F ? 0.55F : 0.3F);
                  if (this.threat != null) {
                     this.getLookControl().setLookAt(this.threat.x, this.threat.y + 1.4, this.threat.z, 30.0F, 30.0F);
                  }

                  if (--this.nextCueTicks <= 0) {
                     this.nextCueTicks = 30 + this.random.nextInt(50);
                     float var10 = this.random.nextFloat();
                     if (var1 > 0.42F && var10 < 0.35F) {
                        this.cue(1);
                        com.formaworks.frontierhunts.hunting.herd.HerdService.alerted(this, this.threat); // [herds] a stamp brings the group's heads up
                        this.stompSound();
                        if (this.random.nextFloat() < 0.5F) {
                           this.stompSound();
                        }
                     } else if (var10 < 0.6F) {
                        this.cue(2);
                     } else if (var1 > 0.5F && var10 < 0.8F && this.vocalTicks == 0) {
                        com.formaworks.frontierhunts.hunting.herd.HerdService.alerted(this, this.threat); // [herds] so does a snort
                        this.alarmCall();
                        this.vocalTicks = 80;
                     }
                  }

                  this.stanceTicks--;
                  if (var1 < 0.18F && this.stanceTicks <= 0) {
                     this.setBehavior(0);
                  } else if (var1 >= 0.3F
                     && var1 < 0.5F
                     && this.behaviorTicks > 80
                     && this.threat != null
                     && !this.getSensingSeesThreat()
                     && this.random.nextFloat() < 0.02F) {
                     this.investigateTarget = this.threat;
                     this.investigateTicks = 160;
                     this.setBehavior(5);
                  }
                  break;
               case 4:
                  this.fleeTicks++;
                  this.entityData.set(TAIL, this.bleeding() ? 0.1F : 1.0F);
                  if (this.threat != null && !this.legsFolded() && (this.fleeTicks % 12 == 0 || this.navigation.isDone())) {
                     if (--this.zigTicks <= 0) {
                        this.zigAngle = (this.random.nextFloat() - 0.5F) * 70.0F;
                        this.zigTicks = 1 + this.random.nextInt(3);
                     }

                     Vec3 var4 = this.position().subtract(this.threat).multiply(1.0, 0.0, 1.0);
                     if (var4.lengthSqr() < 1.0E-4) {
                        var4 = new Vec3((double)this.random.nextFloat() - 0.5, 0.0, (double)this.random.nextFloat() - 0.5);
                     }

                     var4 = var4.normalize().yRot(this.zigAngle * (float) (Math.PI / 180.0));
                     var4 = com.formaworks.frontierhunts.hunting.herd.HerdService.fleeHeading(this, var4); // [herds] group mates steer onto the group's flight line
                     Vec3 var12 = this.position().add(var4.scale(18.0));
                     Vec3 var15 = LandRandomPos.getPosTowards(this, 20, 7, var12);
                     if (var15 == null) {
                        var15 = LandRandomPos.getPosAway(this, 18, 6, this.threat);
                     }

                     if (var15 != null) {
                        this.navigation.moveTo(var15.x, var15.y, var15.z, this.fleeSpeed() * (this.fleeTicks < 50 ? 1.15 : 1.0));
                     }
                  }

                  if (!this.legsFolded()) {
                     this.leapObstacles();
                  }

                  boolean var9 = this.threat == null || this.memory <= 0 || var1 < 0.62F;
                  if (var9 && this.fleeTicks > 60) {
                     this.navigation.stop();
                     if (this.threat != null && !this.bleeding()) {
                        float var13 = (float)(Math.atan2(this.threat.z - this.getZ(), this.threat.x - this.getX()) * 180.0F / (float)Math.PI) - 90.0F;
                        float var16 = Mth.wrapDegrees(var13 - this.yBodyRot);
                        this.cue(var16 > 0.0F ? 4 : 3);
                     }

                     this.setBehavior(3);
                     this.stanceTicks = 60 + this.random.nextInt(80);
                  }
                  break;
               case 5:
                  this.entityData.set(TAIL, 0.35F);
                  if (this.investigateTarget != null && (this.behaviorTicks % 20 == 1 || this.navigation.isDone())) {
                     ServerLevel var11 = (ServerLevel)this.level();
                     Wilderness.Wind var14 = Wilderness.wind(var11.getSeed(), var11.getGameTime(), var11.isRaining(), var11.isThundering());
                     Vec3 var7 = this.investigateTarget.add(new Vec3(var14.east(), 0.0, var14.south()).normalize().scale(10.0));
                     this.navigation.moveTo(var7.x, var7.y, var7.z, 0.42);
                  }

                  if (--this.investigateTicks <= 0 || this.alertness() < 0.15F) {
                     this.setBehavior(0);
                  }
                  break;
               default:
                  this.entityData.set(TAIL, 0.0F);
                  if (var1 < 0.3F && com.formaworks.frontierhunts.hunting.routine.RoutineHooks.rutBusy(this)) {
                     break; // [routines] fighting / tending: no random bedding, idle calls or solo rut cues
                  }

                  if (var1 >= 0.3F && this.threat != null) {
                     this.setBehavior(3);
                     this.stanceTicks = 40 + this.random.nextInt(60);
                     this.nextCueTicks = 10 + this.random.nextInt(20);
                  } else {
                     this.calmTicks = var1 < 0.08F ? this.calmTicks + 1 : 0;
                     if (!this.isRising()) {
                        if (this.calmTicks > 300
                           && !com.formaworks.frontierhunts.hunting.routine.RoutineHooks.ownsBedding(this) // [routines] the routine beds it
                           && this.grazeTicks == 0
                           && !this.foraging
                           && this.navigation.isDone()
                           && this.onGround()
                           && !this.bleeding()
                           && this.random
                                 .nextInt(Math.max(60, (int)((float)(this.restingHours() || com.formaworks.frontierhunts.weather.SeasonalWeather.beddingWeather(this.level(), this.blockPosition()) ? 240 : 2400) * Rut.daylightMovement(this.rut(), this.traits.buck())))) // [weather] bed down in storms
                              == 0) {
                           if (this.bedTarget == null && !this.inCover(this.blockPosition())) {
                              this.bedTarget = this.findCover();
                              if (this.bedTarget != null) {
                                 this.navigation.moveTo(this.bedTarget.x, this.bedTarget.y, this.bedTarget.z, 0.5);
                              }
                           } else {
                              this.bedTarget = null;
                              this.bedTicks = 1200 + this.random.nextInt(3600);
                              this.setBehavior(this.random.nextFloat() < 0.3F ? 2 : 1);
                           }
                        }

                        if (this.bedTarget != null && this.navigation.isDone()) {
                           this.bedTarget = null;
                           this.bedTicks = 1200 + this.random.nextInt(3600);
                           this.setBehavior(1);
                        }

                        if (this.rutTicks > 0) {
                           this.rutTicks--;
                        } else if (!com.formaworks.frontierhunts.hunting.routine.RoutineHooks.rutFightsEnabled() // [routines] real fights replace this
                           && this.traits.buck()
                           && !this.traits.yearling()
                           && this.rut() != Rut.Phase.NONE
                           && this.navigation.isDone()
                           && this.grazeTicks == 0
                           && this.random.nextInt(this.rut() == Rut.Phase.PEAK ? 140 : 320) == 0) {
                           Whitetail var5 = this.nearestRivalBuck(9.0);
                           if (var5 != null && this.getSensing().hasLineOfSight(var5)) {
                              boolean var6 = this.distanceToSqr(var5) < 6.25;
                              this.getLookControl().setLookAt(var5, 30.0F, 30.0F);
                              this.cue(var6 ? 7 : 6);
                              this.rutTicks = var6 ? 80 : 140;
                           }
                        }

                        if (this.vocalTicks == 0 && this.random.nextInt(this.whitetail() ? 1800 : 1400) == 0) {
                           if (!this.species.behavior.bugles()
                              || !this.traits.buck()
                              || this.traits.yearling()
                              || !this.navigation.isDone()
                              || this.grazeTicks != 0) {
                              this.contactCall();
                              this.vocalTicks = 400;
                           } else if (this.activeHours() || this.random.nextInt(3) == 0) {
                              this.cue(5);
                              this.contactCall();
                              this.vocalTicks = 700;
                           }
                        }
                     }
                  }
            }
         }
      } else {
         if (this.behavior() != 0) {
            this.setBehavior(0);
         }

         this.entityData.set(TAIL, 0.0F);
      }
   }

   private boolean getSensingSeesThreat() {
      if (this.threat == null) {
         return false;
      } else {
         Player var1 = this.level().getNearestPlayer(this.threat.x, this.threat.y, this.threat.z, 2.0, false);
         return var1 != null && this.getSensing().hasLineOfSight(var1);
      }
   }

   private boolean inCover(BlockPos var1) {
      for (int var2 = 2; var2 <= 7; var2++) {
         BlockState var3 = this.level().getBlockState(var1.above(var2));
         if (var3.is(BlockTags.LEAVES)) {
            return true;
         }
      }

      BlockState var4 = this.level().getBlockState(var1);
      return var4.is(Blocks.TALL_GRASS) || var4.is(Blocks.LARGE_FERN);
   }

   private Vec3 findCover() {
      for (int var1 = 0; var1 < 8; var1++) {
         Vec3 var2 = LandRandomPos.getPos(this, 14, 4);
         if (var2 != null && this.inCover(BlockPos.containing(var2))) {
            return var2;
         }
      }

      return null;
   }

   private void leapObstacles() {
      if (this.onGround() && this.horizontalCollision && (!(this.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4) || !this.navigation.isDone())) {
         Vec3 var1 = Vec3.directionFromRotation(0.0F, this.getYRot());
         BlockPos var2 = BlockPos.containing(this.position().add(var1.scale(Math.max(0.6, (double)this.getBbWidth()))));
         int var3 = 0;

         for (int var4 = 0; var4 < 3; var4++) {
            if (!this.level().getBlockState(var2.above(var4)).getCollisionShape(this.level(), var2.above(var4)).isEmpty()) {
               var3 = var4 + 1;
            }
         }

         if (var3 != 0 && var3 <= (this.species == GameSpecies.MOOSE ? 1 : 2)) {
            double var6 = var3 <= 1 ? 0.52 : 0.74;
            this.setDeltaMovement(var1.x * 0.38, var6, var1.z * 0.38);
            this.hasImpulse = true;
         }
      }
   }

   public void alarm(Vec3 var1, float var2, int var3) {
      if (var1 != null && !this.downed() && !this.level().isClientSide) {
         this.threat = var1;
         this.memory = Math.max(this.memory, var3);
         this.entityData.set(ALERT, Math.max(this.alertness(), Math.clamp(var2, 0.0F, 1.0F)));
         this.grazeTicks = 0;
         this.entityData.set(ATTENTION, (float)(Math.atan2(var1.z - this.getZ(), var1.x - this.getX()) * 180.0F / (float)Math.PI) - 90.0F);
      }
   }

   public boolean arrowHit(FieldArrow var1, DeerAnatomy.Region var2, float var3, double var4, boolean var6) {
      return this.projectileHit(var1.impactSource(), var1, var1.position(), var2, var3, var4, var6);
   }

   public CompoundTag impactMarks() {
      return this.entityData.get(IMPACTS);
   }

   private void recordImpact(Vec3 var1, Vec3 var2, DeerAnatomy.Region var3, boolean var4, boolean var5, boolean var6) {
      Vec3[] var7 = new Vec3[1];
      int var8 = this.surfaceBone(var1, var7);
      Vec3 var9 = DeerAnatomy.local(var2, Vec3.ZERO, this.yBodyRot);
      var9 = new Vec3(var9.x / (double)this.traits.frameWidth(), var9.y / (double)this.traits.frameHeight(), var9.z / (double)this.traits.frameLength());
      Vector3f var10 = new Matrix4f(this.bodyPose().skin[var8]).invert().transformDirection(new Vector3f((float)var9.x, (float)var9.y, (float)var9.z));
      if (var10.lengthSquared() > 1.0E-10F) {
         var10.normalize();
      }

      this.entityData
         .set(
            IMPACTS,
            ImpactMarks.addSkeletal(this.entityData.get(IMPACTS), var7[0], new Vec3((double)var10.x, (double)var10.y, (double)var10.z), var8, var4, var5, var6)
         );
   }

   public void recordExit(Vec3 var1, Vec3 var2, DeerAnatomy.Region var3) {
      if (!this.level().isClientSide && var1 != null) {
         this.recordImpact(var1, var2, var3, false, false, true);
      }
   }

   public boolean projectileHit(DamageSource var1, Entity var2, Vec3 var3, DeerAnatomy.Region var4, float var5, double var6, boolean var8) {
      if (!this.level().isClientSide
         && !this.downed()
         && var4 != null
         && Float.isFinite(var5)
         && Double.isFinite(var6)
         && Double.isFinite(var3.lengthSqr())
         && (var2 == null || var2.level() == this.level())) {
         if (var2 != null && var2.getUUID().equals(this.lastProjectile) && this.level().getGameTime() - this.lastProjectileTick < 3L) {
            return false;
         } else if (var5 <= 0.0F) {
            return false;
         } else {
            ArrowTip var9 = var2 instanceof FieldArrow var11 ? var11.tip() : (var2 instanceof HuntProjectile var10 ? var10.tip() : null);
            if (var9 != null) {
               var5 = Math.clamp(var5 * var9.penetration, 0.01F, 1.2F);
               if (!var9.lethal) {
                  var4 = DeerAnatomy.Region.BODY;
                  var5 = Math.min(var5, 0.1F);
               }
            }

            float var34 = var9 == null ? 1.0F : var9.bleed;
            DeerWound var35 = DeerWound.from(var4, var5);
            Vec3 var12 = this.getDeltaMovement();
            UUID var13 = this.shooter;
            boolean var14 = this.qualifiedShot;
            double var15 = this.shotDistance;
            String var17 = this.shotRegion;
            long var18 = this.shotAt;
            this.shooter = var1.getEntity() instanceof Player var20 ? var20.getUUID() : null;
            this.qualifiedShot = var8 && this.shooter != null;
            this.shotDistance = Math.clamp(var6, 0.0, 1000.0);
            this.shotRegion = var4.name();
            this.shotAt = this.level().getGameTime();
            float var36 = this.entityData.get(FALL_SIDE);
            Vec3 var37 = var2 != null
               ? var2.getDeltaMovement()
               : (var1.getSourcePosition() != null ? this.position().subtract(var1.getSourcePosition()) : Vec3.ZERO);
            Vec3 var22 = DeerAnatomy.local(var37, Vec3.ZERO, this.yBodyRot);
            this.entityData.set(FALL_SIDE, var22.x < 0.0 ? 1.0F : -1.0F);
            // [integration] user decision: heart / double-lung (vital) hits drop the deer on the spot again, so the kill cam
            // always ends with the drop; tracking's death-run term was removed here (liver, single lung, gut etc. still
            // run and bleed out with tracking's blood, flee and wound durations)
            // [vital] user rule: heart, double-lung AND single-lung hits drop on the spot (kill cam); every other region runs
            boolean var23 = com.formaworks.frontierhunts.vital.ShotVitals.deerDrops(var4) && var5 >= 0.08F && (var9 == null || var9.lethal);
            float var24 = var23 ? Math.max(this.getHealth() + 1.0F, var35.impactDamage()) : var35.impactDamage();
            var24 = FirearmDamage.animalDamage(this, var1, var24);
            if (var23) {
               var24 = Math.max(var24, this.getHealth() + 1.0F);
            } else {
               var24 = Math.max(0.01F, Math.min(var24, this.getHealth() - 1.5F));
            }

            Object kcToken = com.formaworks.frontierhunts.killcam.KillCamServer.beforeDeerHit(this, var1, var2, var3, var4, var5); // [killcam]
            float journalCalm = this.alertness(); // [journal] was it unaware of the hunter before the shot
            this.applyingProjectile = true;

            boolean var25;
            try {
               var25 = this.hurt(var1, var24);
            } finally {
               this.applyingProjectile = false;
            }

            if (!var25) {
               this.shooter = var13;
               this.qualifiedShot = var14;
               this.shotDistance = var15;
               this.shotRegion = var17;
               this.shotAt = var18;
            }

            if (!var25) {
               this.entityData.set(FALL_SIDE, var36);
            } else {
               FirearmDamage.accepted(this, var1);
               if (var2 != null) {
                  this.lastProjectile = var2.getUUID();
                  this.lastProjectileTick = this.level().getGameTime();
               }

               this.openWound = true;
               this.setWoundPoint(var3, var4);
               boolean var26;
               if (var2 instanceof FieldArrow || var2 instanceof HuntProjectile var27 && var27.kind().bow) {
                  var26 = true;
               } else {
                  var26 = false;
               }

               boolean var28 = var9 != null && var9.tracer();
               this.recordImpact(var3, var37, var4, var26, var28, false);
               this.entityData.set(IMPACTS, com.formaworks.frontierhunts.archery.ArrowMarks.stamp(this.entityData.get(IMPACTS), var2)); // [archery2] which arrow
               this.bloodEffect(true);
               this.entityData.set(HIT_AT, this.level().getGameTime());
               this.entityData.set(HIT_KIND, var35.reaction());
               this.grazeTicks = 0;
               this.entityData.set(GRAZING, false);
               if (TrailService.blood(this, true)) {
                  TrailService.blood(this, false);
                  TrailService.ensureImpactDrip(this);
                  this.lastTrack = this.position();
                  this.lastBloodAt = this.level().getGameTime() - 52L;
               }

               TrailService.brush(this);
               if (!this.downed()) {
                  this.bleedTicks = Math.max(this.bleedTicks, Math.round((float)var35.duration() * (var34 <= 0.0F ? 0.1F : 1.0F)));
                  this.bloodLoss = Math.max(this.bloodLoss, var35.lossPerPulse() * var34);
                  if (var34 >= 1.3F) {
                     this.heavyTrail = true;
                  }

                  boolean var29 = var9 == null || var9.lethal;
                  if (var29) {
                     this.woundCount++;
                  }

                  if (var29 && this.woundCount >= 3 && !var35.fatal(var4, var5)) {
                     this.fatalTicks = this.fatalTicks > 0 ? Math.min(this.fatalTicks, 160) : 160;
                     this.bleedTicks = Math.max(this.bleedTicks, this.fatalTicks + 40);
                     this.bloodLoss = Math.max(this.bloodLoss, Math.max(0.0F, this.getHealth() - 1.0F) * 40.0F / (float)this.fatalTicks);
                  }

                  if (var29 && var35.fatal(var4, var5)) {
                     int var30 = Math.round((float)var35.duration() * Mth.clamp((float)this.traits.massKg() / 84.0F, 0.85F, 1.2F));
                     if (this.woundCount >= 2) {
                        var30 = Math.min(var30, Math.max(160, var30 / 2));
                     }

                     this.fatalTicks = this.fatalTicks > 0 ? Math.min(this.fatalTicks, var30) : var30;
                     this.bleedTicks = Math.max(this.bleedTicks, this.fatalTicks + 40);
                     this.bloodLoss = Math.max(this.bloodLoss, Math.max(0.0F, this.getHealth() - 1.0F) * 40.0F / (float)this.fatalTicks);
                  }

                  Vec3 var40 = var1.getEntity() == null ? this.position().subtract(var37) : var1.getEntity().position();
                  this.alarm(var40, 1.0F, com.formaworks.frontierhunts.tracking.BloodTrail.fleeMemory(var4.name(), Math.max(1800, this.bleedTicks))); // [tracking]
                  this.setPersistenceRequired();
                  this.provoked(var1.getEntity());
                  Vec3 var31 = this.position().subtract(var40).multiply(1.0, 0.0, 1.0).normalize().scale(0.063);
                  this.setDeltaMovement(var12.add(var31).add(0.0, this.onGround() && var35.reaction() == 2 ? 0.19 : 0.0, 0.0));
                  this.hasImpulse = true;
               } else {
                  this.setDeltaMovement(var12);
               }
            }

            com.formaworks.frontierhunts.killcam.KillCamServer.afterDeerHit(kcToken, var25); // [killcam]
            com.formaworks.frontierhunts.journal.JournalHooks.deerHit(this, var1, var2, var4, var23, var25, journalCalm, this.shotDistance); // [journal] hit / clean-kill stats + XP
            return var25;
         }
      } else {
         return false;
      }
   }

   private void setWoundPoint(Vec3 var1, DeerAnatomy.Region var2) {
      Vec3[] var3 = new Vec3[1];
      this.woundBone = this.surfaceBone(var1, var3);
      this.woundPoint = new Vec3(Mth.clamp(var3[0].x, -1.2, 1.2), Mth.clamp(var3[0].y, -0.3, 3.2), Mth.clamp(var3[0].z, -2.2, 2.2));
   }

   public Vec3 woundWorldPosition() {
      if (this.woundBone >= 0 && this.woundBone < DeerSkeleton.of(this.species).count()) {
         return DeerAnatomy.world(this, this.woundBone, this.woundPoint);
      } else {
         DeerSkeleton var1 = DeerSkeleton.of(this.species);
         if (this.whitetail()) {
            return DeerAnatomy.world(this, var1.bodyTop1(), new Vec3(0.1, 0.72, -0.05));
         } else {
            Vector3f var2 = new Matrix4f(var1.inverseBind[var1.bodyTop1()]).invert().getTranslation(new Vector3f());
            return DeerAnatomy.world(this, var1.bodyTop1(), new Vec3((double)var2.x + 0.12, (double)var2.y - 0.22, (double)var2.z));
         }
      }
   }

   private void bloodEffect(boolean var1) {
      if (this.level() instanceof ServerLevel var2 && !this.isInWaterOrBubble()) {
         Vec3 var5 = this.woundWorldPosition();
         if (var1) {
            var2.sendParticles(HuntParticles.BLOOD.get(), var5.x, var5.y, var5.z, 20, 0.065, 0.035, 0.065, 0.08);
         } else {
            Vec3 var4 = this.getDeltaMovement().scale(0.38);
            var2.sendParticles(HuntParticles.BLOOD.get(), var5.x, var5.y, var5.z, 0, var4.x, -0.03, var4.z, 1.0);
         }

         return;
      }
   }

   @Override
   public boolean hurt(DamageSource var1, float var2) {
      if (!this.level().isClientSide && var1.getEntity() != null) {
         this.lastHurtTick = this.tickCount;
      }

      if (this.downed()) {
         return false;
      } else if (!this.applyingProjectile
         && !this.level().isClientSide
         && var2 > 0.0F
         && Float.isFinite(var2)
         && var1.getDirectEntity() instanceof Projectile var3
         && !(var3 instanceof FieldArrow)
         && !var1.is(DamageTypeTags.IS_EXPLOSION)) {
         Vec3 var16 = var3.position();
         Vec3 var5 = var16.add(var3.getDeltaMovement());
         if (Double.isFinite(var5.lengthSqr()) && !(var16.distanceToSqr(var5) > 65536.0) && !(var16.distanceToSqr(var5) < 1.0E-8)) {
            BlockHitResult var6 = this.level().clip(new ClipContext(var16, var5, Block.COLLIDER, Fluid.NONE, var3));
            if (var6.getType() != Type.MISS) {
               var5 = var6.getLocation();
            }

            DeerAnatomy.Contact var7 = DeerAnatomy.intersect(var16, var5, this);
            if (var7 == null) {
               var5 = var16;
               var16 = var16.subtract(var3.getDeltaMovement());
               BlockHitResult var8 = this.level().clip(new ClipContext(var16, var5, Block.COLLIDER, Fluid.NONE, var3));
               if (var8.getType() != Type.MISS) {
                  var5 = var8.getLocation();
               }

               var7 = DeerAnatomy.intersect(var16, var5, this);
               if (var7 == null) {
                  return false;
               }
            }

            Vec3 var17 = var16.lerp(var5, var7.fraction());
            DeerAnatomy.Region var9 = var7.region();
            float var10 = Mth.clamp(var2 / 18.0F, 0.25F, 1.2F);
            double var11 = var1.getEntity() == null ? 0.0 : var1.getEntity().position().distanceTo(var17);
            if (var1.getEntity() instanceof Player var13 && !var13.hasInfiniteMaterials()) {
               return this.projectileHit(var1, var3, var17, var9, var10, var11, true);
            }

            return this.projectileHit(var1, var3, var17, var9, var10, var11, false);
         } else {
            return this.unknownShot(var1, var3, var2);
         }
      } else if (!this.applyingProjectile && !this.level().isClientSide && var2 > 0.0F && Float.isFinite(var2) && var1.is(DamageTypeTags.IS_PROJECTILE)) {
         return this.unknownShot(var1, null, var2);
      } else {
         if (var1.getEntity() instanceof Player && !this.applyingProjectile) {
            this.qualifiedShot = false;
         }

         if (!this.applyingProjectile && !this.level().isClientSide && var1.getEntity() != null && var2 > 0.0F && Float.isFinite(var2)) {
            var2 = Math.max(0.01F, Math.min(var2, this.getHealth() - 1.5F));
            if (this.getHealth() <= 6.0F || ++this.woundCount >= 3) {
               this.fatalTicks = this.fatalTicks > 0 ? Math.min(this.fatalTicks, 200) : 200;
               this.bleedTicks = Math.max(this.bleedTicks, this.fatalTicks + 40);
            }
         }

         boolean var15 = super.hurt(var1, var2);
         if (var15 && var1.getEntity() != null) {
            this.alarm(var1.getEntity().position(), 1.0F, 240);
            if (!this.applyingProjectile) {
               this.provoked(var1.getEntity());
            }
         }

         if (var15
            && !this.applyingProjectile
            && !this.level().isClientSide
            && (var1.getEntity() != null || var1.is(DamageTypes.GENERIC))
            && !var1.is(DamageTypeTags.IS_FIRE)
            && !var1.is(DamageTypeTags.IS_EXPLOSION)) {
            this.openWound = true;
            this.bleedTicks = Math.max(this.bleedTicks, 240);
            this.bloodEffect(true);
            TrailService.blood(this, true);
            if (!this.downed()) {
               this.setPersistenceRequired();
            }
         }

         return var15;
      }
   }

   private boolean unknownShot(DamageSource var1, Entity var2, float var3) {
      Vec3 var4 = this.woundWorldPosition();
      DeerAnatomy.Region var5 = DeerAnatomy.Region.BODY;
      float var6 = Mth.clamp(var3 / 18.0F, 0.25F, 1.2F);
      double var7 = var1.getEntity() == null ? 0.0 : (double)this.distanceTo(var1.getEntity());
      if (var1.getEntity() instanceof Player var9 && !var9.hasInfiniteMaterials()) {
         return this.projectileHit(var1, var2, var4, var5, var6, var7, true);
      }

      return this.projectileHit(var1, var2, var4, var5, var6, var7, false);
   }

   @Override
   public void die(DamageSource var1) {
      if (!this.downed()) {
         this.entityData.set(FALL_SPEED, this.motionSpeed / this.traits.lengthScale());
         this.entityData.set(DOWN_AT, this.level().getGameTime());
         this.entityData.set(DOWN, true);
         this.setHealth(1.0F);
         this.navigation.stop();
         this.setNoAi(true);
         this.entityData.set(GRAZING, false);
         this.setPersistenceRequired();
         com.formaworks.frontierhunts.hunting.herd.HerdService.downed(this, var1); // [herds] the group bolts, then closes up without it
      }
   }

   @Override
   protected boolean isImmobile() {
      return this.downed() || this.sedated() || this.waking() || super.isImmobile();
   }

   @Override
   public boolean isControlledByLocalInstance() {
      return !this.level().isClientSide && (this.downed() || this.sedated() || this.waking()) ? true : super.isControlledByLocalInstance();
   }

   @Override
   public boolean isPushable() {
      return !this.downed() && !this.sedated() && !this.waking() && super.isPushable();
   }

   @Override
   public boolean canBeLeashed() {
      return false;
   }

   @Override
   public boolean removeWhenFarAway(double var1) {
      return false;
   }

   @Override
   protected void playStepSound(BlockPos var1, BlockState var2) {
      SoundType var3 = var2.getSoundType(this.level(), var1, this);
      boolean var4 = this.behavior() == 4;
      float var5 = this.species.behavior.stepPitch();
      this.playSound(var3.getStepSound(), var3.getVolume() * (var4 ? 0.45F : 0.18F) / Math.max(0.6F, var5), var3.getPitch() * (var4 ? 0.8F : 1.05F) * var5);
   }

   @Override
   public int getAmbientSoundInterval() {
      return 600;
   }

   @Override
   public AABB getBoundingBoxForCulling() {
      return this.whitetail() ? this.getBoundingBox().inflate(0.9) : this.getBoundingBox().inflate(1.6, 1.2, 1.6);
   }

   @Override
   public InteractionResult mobInteract(Player var1, InteractionHand var2) {
      if (!this.sedated() && !this.waking()) {
         if (!this.downed()) {
            return super.mobInteract(var1, var2);
         } else if (this.harvested) {
            CarcassCleanup.interact(this, var1, var2);
            return InteractionResult.CONSUME;
         } else if (var2 == InteractionHand.MAIN_HAND && var1.getMainHandItem().getItem() instanceof com.formaworks.frontierhunts.licence.PermitItem permit
            && permit.kind == com.formaworks.frontierhunts.licence.PermitItem.Kind.TAG) {
            // [gear21] big game is tagged by hand: use the tag on the animal before skinning it or walking away
            if (!this.level().isClientSide && var1 instanceof ServerPlayer tagger) {
               com.formaworks.frontierhunts.licence.Tagging.tagAction(tagger, this, this.shooter);
            }
            return InteractionResult.CONSUME;
         } else if (var2 == InteractionHand.MAIN_HAND && var1.getMainHandItem().is(HuntContent.SKINNING_TOOL.get())) {
            if (this.level().isClientSide) {
               return InteractionResult.CONSUME;
            } else {
               if (!(var1 instanceof ServerPlayer var3) || this.harvested) {
                  return InteractionResult.CONSUME;
               }

               if (this.collapseProgress(0.0F) < 1.0F) {
                  var3.displayClientMessage(Component.literal("Wait for the animal to settle."), true);
                  return InteractionResult.CONSUME;
               } else if (!this.validDresser(var3)) {
                  return InteractionResult.CONSUME;
               } else if (this.shooter != null && !this.shooter.equals(var3.getUUID()) && this.downTicks < 1200) {
                  var3.displayClientMessage(Component.literal("This harvest is reserved for the hunter who made the shot."), true);
                  return InteractionResult.CONSUME;
               } else if (this.dressingHunter != null && !this.dressingHunter.equals(var3.getUUID())) {
                  var3.displayClientMessage(Component.literal("Another hunter is skinning this animal."), true);
                  return InteractionResult.CONSUME;
               } else {
                  if (this.dressingHunter == null && com.formaworks.frontierhunts.licence.Tagging.mustTagFirst(var3, this)) {
                     return InteractionResult.CONSUME;
                  }
                  if (this.dressingHunter == null) {
                     this.dressingHunter = var3.getUUID();
                     this.blockedDressingTicks = 0;
                     this.entityData.set(DRESS_DURATION, AssignmentService.dressingTicks(var3) * 2);
                     this.entityData.set(DRESS_DURATION, com.formaworks.frontierhunts.journal.HunterSkills.dressTicks(var3, this.entityData.get(DRESS_DURATION))); // [journal] Quick Knife
                     this.entityData.set(DRESSER, var3.getId());
                     this.syncDressing();
                     var3.displayClientMessage(Component.literal("Skinning started · keep the tool equipped · walk away to pause"), true);
                  }

                  return InteractionResult.CONSUME;
               }
            }
         } else {
            if (!this.level().isClientSide) {
               var1.displayClientMessage(Component.literal("Use the Contour Skinning Knife to skin this animal."), true);
            }

            return InteractionResult.CONSUME;
         }
      } else {
         if (!this.level().isClientSide) {
            var1.displayClientMessage(Component.literal("Sedated · wakes in " + this.sedationTicksRemaining() / 20 + "s · cannot skin a living animal"), true);
         }

         return InteractionResult.CONSUME;
      }
   }

   private boolean validDresser(ServerPlayer var1) {
      if (!(this.collapseProgress(0.0F) < 1.0F)
         && var1.isAlive()
         && !var1.isSpectator()
         && var1.level() == this.level()
         && !(this.distanceToSqr(var1) > 16.0)
         && var1.getMainHandItem().is(HuntContent.SKINNING_TOOL.get())) {
         for (double var5 : new double[]{0.25, 0.55, 0.8}) {
            Vec3 var7 = var1.getEyePosition();
            Vec3 var8 = this.position().add(0.0, var5, 0.0);
            BlockHitResult var9 = this.level().clip(new ClipContext(var7, var8, Block.COLLIDER, Fluid.NONE, var1));
            if (var9.getType() == Type.MISS || var9.getLocation().distanceToSqr(var8) < 0.04) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private void dressTick() {
      ServerPlayer var1 = ((ServerLevel)this.level()).getServer().getPlayerList().getPlayer(this.dressingHunter);
      if (var1 != null && this.validDresser(var1)) {
         this.blockedDressingTicks = 0;
         this.entityData.set(DRESSER, var1.getId());
         this.dressingTicks++;
         if (this.dressingTicks % 2 == 0) {
            this.syncDressing();
         }

         if (this.dressingTicks >= this.entityData.get(DRESS_DURATION)) {
            this.harvest(var1);
         }
      } else {
         this.entityData.set(DRESSER, -1);
         this.syncDressing();
         if (++this.blockedDressingTicks >= 10) {
            if (var1 != null) {
               var1.displayClientMessage(Component.literal("Skinning paused · use the carcass again to resume"), true);
            }

            this.dressingHunter = null;
         }
      }
   }

   public boolean harvest(ServerPlayer var1) {
      if (this.downed()
         && !this.harvested
         && this.dressingTicks >= this.entityData.get(DRESS_DURATION)
         && var1.getUUID().equals(this.dressingHunter)
         && this.validDresser(var1)) {
         this.harvested = true;
         this.harvestedAt = this.level().getGameTime();
         this.dressingTicks = this.entityData.get(DRESS_DURATION);
         this.syncDressing();
         this.entityData.set(DRESSER, -1);
         if (com.formaworks.frontierhunts.academy.Academy.dressedInTraining(var1, this)) { // [academy] training carcass: the course counts it, no yield, no records
            this.dressingHunter = null;
            return true;
         }
         com.formaworks.frontierhunts.licence.Tagging.Verdict licence = com.formaworks.frontierhunts.licence.Tagging.deer(var1, this, this.shooter); // [licence] tag the animal (or the warden's verdict)
         com.formaworks.frontierhunts.freak.FreakQuest.harvested(var1, this, licence.poached()); // [1.1.6]
         if (!licence.poached()) { // [1.1.6] a poached animal earns no expedition pay or contract credit
            ExpeditionService.record(var1, "harvest", this.species.id, 1, this.shotDistance);
         }
         com.formaworks.frontierhunts.guide.FieldSchool.harvested(var1); // [guide] Field School lesson 7
         com.formaworks.frontierhunts.firsthunt.FirstHunt.harvested(var1, this); // [1.2.7] first hunt: harvest
         com.formaworks.frontierhunts.progression.Durability.commit(var1.server, "harvest"); // [1.2.7] the meat, the trophy, the tag and the books saved together
         if (!licence.poached() && (this.shotRegion.equals("HEART") || this.shotRegion.equals("DOUBLE_LUNG") || this.shotRegion.equals("LUNG") || this.shotRegion.equals("CHEST"))) {
            ExpeditionService.record(var1, "vital", this.species.id, 1, this.shotDistance);
         }

         ItemStack var2 = new ItemStack(HuntContent.WHITETAIL_TROPHY.get());
         CompoundTag var3 = new CompoundTag();
         var3.putString("species", "frontierhunts:" + this.species.id);
         var3.putInt("mass_kg", this.massKg());
         var3.putDouble("shot_metres", this.shotDistance);
         var3.putString("region", this.shotRegion);
         var3.putUUID("animal", this.getUUID());
         if (this.shooter != null) {
            var3.putUUID("hunter", this.shooter);
         }

         var3.put("deer_traits", this.traits.save());
         var3.putInt("antler_points", this.traits.totalPoints());
         var3.putInt("trophy_score", this.traits.trophyScore());
         var3.putString("trophy_grade", this.traits.trophyGrade());
         var3.putInt("harvest_value", this.traits.harvestValue());
         // [1.2.0] a legend's trophy is a legendary thing: named, gleaming, marked as the legend
         if (com.formaworks.frontierhunts.freak.FreakQuest.isFreak(this) && !licence.poached()) {
            var3.putString("legend", this.species.id);
         }
         var2.set(DataComponents.CUSTOM_DATA, CustomData.of(var3));
         var2.set(
            DataComponents.CUSTOM_NAME,
            Component.literal(
               (this.whitetail() ? "" : this.species.title + " · ")
                  + this.traits.description()
                  + " · "
                  + this.massKg()
                  + " kg"
                  + (this.traits.buck() ? " · " + this.traits.totalPoints() + " points" : "")
            )
         );
         int var4 = this.whitetail() ? Math.clamp((long)(this.massKg() / 30), 1, 5) : Math.clamp((long)(this.massKg() / 36), 3, 14);
         int var5 = Math.clamp((long)(this.massKg() / 45), 2, 8);
         var4 = com.formaworks.frontierhunts.survival.SurvivalHarvest.meat(this, var4); // [survival] lean seasons: yield follows condition
         var5 = com.formaworks.frontierhunts.survival.SurvivalHarvest.meat(this, var5); // [survival]
         int var6 = this.whitetail() ? 1 : (this.species == GameSpecies.MOOSE ? 3 : 2);
         var4 = com.formaworks.frontierhunts.journal.HunterSkills.meat(var1, var4); var5 = com.formaworks.frontierhunts.journal.HunterSkills.quarters(var1, var5); var6 = com.formaworks.frontierhunts.journal.HunterSkills.hides(var1, var6); // [journal] Butchery perks
         var4 = Math.max(licence.confiscate() ? 0 : 1, licence.meat(var4)); var5 = licence.meat(var5); // [licence] Strict: half the meat is seized
         if (var3.contains("legend")) {
            com.formaworks.frontierhunts.freak.LegendRewards.trophy(var2, this.species.id, this.massKg(), this.traits.buck() ? this.traits.totalPoints() : 0);
         }

         for (ItemStack var10 : new ItemStack[]{
            licence.trophy(var2), // [licence] Strict: a poached trophy is seized
            new ItemStack(HuntContent.VENISON.get(), Math.min(var4, 64)),
            new ItemStack(HuntContent.VENISON_QUARTER.get(), var5),
            new ItemStack(HuntContent.BACKSTRAP.get(), 2),
            com.formaworks.frontierhunts.survival.SurvivalHarvest.hide(this, var6) // [survival] elk/moose give a heavy hide
         }) {
            ItemEntity var11 = this.spawnAtLocation(var10);
            if (var11 != null) {
               var11.setTarget(var1.getUUID());
               var11.setNoPickUpDelay();
            }
         }

         com.formaworks.frontierhunts.survival.SurvivalHarvest.extras(this, var1); // [survival] heart & liver, fat by condition
         var1.getMainHandItem().hurtAndBreak(1, var1, EquipmentSlot.MAINHAND);
         if (this.qualifiedShot && var1.getUUID().equals(this.shooter) && !var1.isCreative() && HuntRules.active(this.level())) {
            HunterLedger.get(var1.serverLevel()).harvestWhitetail(var1.getUUID());
         }
         if (this.qualifiedShot && var1.getUUID().equals(this.shooter) && !var1.isCreative()) { // [academy] assignment credit without the reserve rule (eligible() checks the level)
            AssignmentService.harvest(
               var1,
               this.getUUID(),
               this.shotAt,
               this.massKg(),
               this.shotRegion.equals("CHEST") || this.shotRegion.equals("HEART") || this.shotRegion.equals("DOUBLE_LUNG") || this.shotRegion.equals("LUNG")
            );
         }

         HuntService.send(var1, false);
         var1.displayClientMessage(Component.literal("Harvest recorded · " + this.massKg() + " kg · " + this.shotRegion.toLowerCase(Locale.ROOT)), true);
         com.formaworks.frontierhunts.camps.CampHooks.deerHarvest(var1, this, var2, this.shooter, this.shotDistance, this.shotAt); // [camps] record book, camp news, guided hunts, events
         com.formaworks.frontierhunts.journal.JournalHooks.deerHarvest(var1, this, var2, this.shooter, this.shotDistance, this.shotRegion, this.shotAt); // [journal] species log, recoveries, field note
         this.dressingHunter = null;
         return true;
      } else {
         return false;
      }
   }

   @Override
   public void addAdditionalSaveData(CompoundTag var1) {
      super.addAdditionalSaveData(var1);
      var1.putBoolean("open_wound", this.openWound);
      var1.putInt("pool_stage", this.poolStage);
      var1.putInt("blood_steps", this.bloodSteps);
      var1.putBoolean("downed", this.downed());
      var1.putInt("down_ticks", this.downTicks);
      var1.putInt("bleed_ticks", this.bleedTicks);
      var1.putFloat("blood_loss", this.bloodLoss);
      var1.putBoolean("heavy_trail", this.heavyTrail);
      var1.putInt("loss_clock", this.lossClock);
      var1.putInt("alarm_memory", this.memory);
      var1.putInt("fatal_ticks", this.fatalTicks);
      var1.putInt("wound_count", this.woundCount);
      var1.putInt("wound_bone", this.woundBone);
      var1.putInt("wound_space", 5);
      var1.putLong("wound_saved_at", this.level().getGameTime());
      var1.putDouble("wound_x", this.woundPoint.x);
      var1.putDouble("wound_y", this.woundPoint.y);
      var1.putDouble("wound_z", this.woundPoint.z);
      var1.putLong("shot_at", this.shotAt);
      if (this.threat != null) {
         var1.putDouble("threat_x", this.threat.x);
         var1.putDouble("threat_y", this.threat.y);
         var1.putDouble("threat_z", this.threat.z);
      }

      var1.putFloat("fall_side", this.entityData.get(FALL_SIDE));
      var1.putFloat("fall_speed", this.entityData.get(FALL_SPEED));
      var1.putBoolean("ground_impact_played", this.groundImpactPlayed);
      var1.putInt("reaction", this.reaction());
      var1.putFloat("reaction_age", this.reactionAge(0.0F));
      var1.putInt("variant", this.variant());
      var1.putBoolean("harvested", this.harvested);
      var1.putBoolean("qualified_shot", this.qualifiedShot);
      var1.put("deer_traits", this.traits.save());
      var1.putInt("skinning_progress", this.dressingTicks);
      var1.putInt("skinning_duration", this.entityData.get(DRESS_DURATION));
      var1.putLong("harvested_at", this.harvestedAt);
      var1.putLong("sedated_until", this.entityData.get(SEDATED_UNTIL));
      var1.putLong("sedated_at", this.entityData.get(SEDATED_AT));
      var1.putLong("waking_at", this.entityData.get(WAKING_AT));
      var1.putBoolean("sedation_no_ai", this.beforeSedationNoAi);
      var1.putInt("behavior", this.behavior());
      var1.putInt("bed_ticks", this.bedTicks);
      var1.putString("shot_region", this.shotRegion);
      var1.putDouble("shot_distance", this.shotDistance);
      if (this.shooter != null) {
         var1.putUUID("shooter", this.shooter);
      }

      var1.put("impact_marks", this.entityData.get(IMPACTS));
   }

   @Override
   public void readAdditionalSaveData(CompoundTag var1) {
      super.readAdditionalSaveData(var1);
      this.entityData.set(DOWN, var1.getBoolean("downed"));
      this.downTicks = Math.clamp((long)var1.getInt("down_ticks"), 0, 36001);
      this.openWound = var1.getBoolean("open_wound") || var1.getInt("bleed_ticks") > 0 || var1.getLong("shot_at") > 0L;
      this.poolStage = Math.clamp((long)var1.getInt("pool_stage"), 0, 3);
      this.bloodSteps = Math.clamp((long)var1.getInt("blood_steps"), 0, 5);
      this.shotAt = var1.contains("shot_at") ? Math.min(this.level().getGameTime(), var1.getLong("shot_at")) : -1L;
      this.bleedTicks = Math.clamp((long)var1.getInt("bleed_ticks"), 0, 4000);
      this.entityData.set(VARIANT, Math.clamp((long)var1.getInt("variant"), 0, 7));
      this.fatalTicks = Math.clamp((long)var1.getInt("fatal_ticks"), 0, 4000);
      this.woundCount = Math.clamp((long)var1.getInt("wound_count"), 0, 64);
      this.woundBone = var1.getInt("wound_space") == 5 ? Math.clamp((long)var1.getInt("wound_bone"), -1, DeerSkeleton.of(this.species).count() - 1) : -1;
      this.entityData.set(IMPACTS, var1.contains("impact_marks", 10) ? var1.getCompound("impact_marks") : new CompoundTag());
      if (var1.contains("wound_y") && this.woundBone >= 0) {
         this.woundPoint = new Vec3(
            (double)finiteClamp(var1.getFloat("wound_x"), -1.2F, 1.2F),
            (double)finiteClamp(var1.getFloat("wound_y"), -0.3F, 3.2F),
            (double)finiteClamp(var1.getFloat("wound_z"), -2.2F, 2.2F)
         );
      }

      this.heavyTrail = var1.getBoolean("heavy_trail");
      this.bloodLoss = var1.contains("blood_loss") ? finiteClamp(var1.getFloat("blood_loss"), 0.0F, 2.0F) : 0.35F;
      this.lossClock = Math.clamp((long)var1.getInt("loss_clock"), 0, 39);
      this.memory = Math.clamp((long)var1.getInt("alarm_memory"), 0, 4000);
      if (var1.contains("threat_x")) {
         Vec3 var2 = new Vec3(var1.getDouble("threat_x"), var1.getDouble("threat_y"), var1.getDouble("threat_z"));
         if (Double.isFinite(var2.lengthSqr())) {
            this.threat = var2;
            if (this.bleeding()) {
               this.entityData.set(ALERT, 1.0F);
            }
         }
      }

      this.entityData.set(FALL_SIDE, var1.getFloat("fall_side") < 0.0F ? -1.0F : 1.0F);
      this.entityData.set(FALL_SPEED, finiteClamp(var1.getFloat("fall_speed"), 0.0F, 1.0F));
      this.groundImpactPlayed = var1.getBoolean("ground_impact_played") || this.downTicks > 11;
      this.entityData.set(HIT_KIND, Math.clamp((long)var1.getInt("reaction"), 0, 3));
      if (var1.contains("reaction_age")) {
         this.entityData.set(HIT_AT, this.level().getGameTime() - (long)finiteClamp(var1.getFloat("reaction_age"), 0.0F, 1000.0F));
      }

      this.entityData
         .set(
            TRAITS,
            var1.contains("deer_traits", 10)
               ? DeerTraits.load(var1.getCompound("deer_traits")).withSpecies(this.species).save()
               : new DeerTraits(true, 54, Math.clamp((long)(20 + this.variant() * 9), 0, 100), 75, 70, 0).withSpecies(this.species).save()
         );
      if (var1.getString("frontier_spawn_sex").equals("buck")) {
         this.setTraits(DeerTraits.random(this.species, this.random, true));
      } else if (var1.getString("frontier_spawn_sex").equals("doe")) {
         this.setTraits(DeerTraits.random(this.species, this.random, false));
      }

      this.harvested = var1.getBoolean("harvested");
      this.qualifiedShot = var1.getBoolean("qualified_shot");
      this.shotRegion = var1.getString("shot_region");
      this.shotDistance = Double.isFinite(var1.getDouble("shot_distance")) ? Math.clamp(var1.getDouble("shot_distance"), 0.0, 1000.0) : 0.0;
      this.shooter = var1.hasUUID("shooter") ? var1.getUUID("shooter") : null;
      if (!this.downed() && var1.contains("wound_saved_at")) {
         int var7 = Math.clamp(this.level().getGameTime() - var1.getLong("wound_saved_at"), 0, 4000);
         int var3 = Math.min(this.bleedTicks, var7);
         float var4 = (float)((var3 + this.lossClock) / 40) * this.bloodLoss;
         this.lossClock = (var3 + this.lossClock) % 40;
         this.bleedTicks = Math.max(0, this.bleedTicks - var7);
         boolean var5 = this.fatalTicks > 0 && var7 >= this.fatalTicks;
         int var6 = var5 ? var7 - this.fatalTicks : 0;
         this.fatalTicks = Math.max(0, this.fatalTicks - var7);
         if (!var5 && !(var4 >= this.getHealth())) {
            this.setHealth(Math.max(1.0F, this.getHealth() - var4));
         } else {
            this.die(this.damageSources().generic());
            this.downTicks = var6;
            this.groundImpactPlayed = var6 > 11;
         }
      }

      if (this.downed()) {
         this.setHealth(1.0F);
         this.setNoAi(true);
         this.collapseTicks = Math.min(36, this.downTicks);
         this.entityData.set(DOWN_AT, this.level().getGameTime() - (long)this.collapseTicks);
      }

      this.entityData.set(DRESS_DURATION, Math.clamp((long)var1.getInt("skinning_duration"), 128, 160));
      this.dressingTicks = Math.clamp((long)var1.getInt("skinning_progress"), 0, this.entityData.get(DRESS_DURATION));
      this.harvestedAt = var1.getLong("harvested_at");
      if (this.harvested) {
         this.dressingTicks = this.entityData.get(DRESS_DURATION);
      }

      this.syncDressing();
      this.beforeSedationNoAi = var1.getBoolean("sedation_no_ai");
      int var8 = Math.clamp((long)var1.getInt("behavior"), 0, 5);
      this.bedTicks = Math.clamp((long)var1.getInt("bed_ticks"), 0, 20000);
      if (!this.downed() && (var8 == 1 || var8 == 2) && this.bedTicks > 0) {
         this.entityData.set(BEHAVIOR, (byte)var8);
         this.entityData.set(BEHAVIOR_AT, this.level().getGameTime() - 200L);
      }

      if (!this.downed() && var1.getLong("sedated_until") > 0L) {
         this.entityData.set(SEDATED_UNTIL, Math.min(this.level().getGameTime() + 3600L, var1.getLong("sedated_until")));
         this.entityData.set(SEDATED_AT, var1.getLong("sedated_at"));
         this.setNoAi(true);
         this.sedationTick();
      } else if (!this.downed() && var1.getLong("waking_at") > 0L) {
         this.entityData.set(WAKING_AT, var1.getLong("waking_at"));
         this.setNoAi(true);
         this.sedationTick();
      }

      if (this.harvested) {
         CarcassCleanup.automatic(this, this.harvestedAt);
      }
   }

   private static float finiteClamp(float var0, float var1, float var2) {
      return Float.isFinite(var0) ? Mth.clamp(var0, var1, var2) : var1;
   }

   static final class DeerGroup implements SpawnGroupData {
      final boolean bachelor;

      DeerGroup(boolean var1) {
         this.bachelor = var1;
      }
   }
}
