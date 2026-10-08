package com.formaworks.frontierhunts.ecology;

import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * [ecology] The body of a pronghorn, bison or hog killed by a predator: drawn as that animal lying on its side (the
 * wildlife model, see {@code ecology.client.KillCarcassRenderer}). It lies for {@link EcologyConfig#carcassTicks()}
 * of world time, then leaves bones. Deer, elk and moose kills use the Whitetail's own downed body instead.
 */
public final class KillCarcass extends Entity {
   private static final EntityDataAccessor<Integer> SPECIES = SynchedEntityData.defineId(KillCarcass.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Long> KILLED_AT = SynchedEntityData.defineId(KillCarcass.class, EntityDataSerializers.LONG);
   private static final EntityDataAccessor<Float> BODY_YAW = SynchedEntityData.defineId(KillCarcass.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Boolean> LEFT = SynchedEntityData.defineId(KillCarcass.class, EntityDataSerializers.BOOLEAN);
   /** who killed it and how much is left (server; saved) */
   private KillRecord record = KillRecord.empty();

   public KillCarcass(EntityType<? extends KillCarcass> type, Level level) {
      super(type, level);
      this.noCulling = false;
   }

   public static KillCarcass create(ServerLevel level, WildlifeSpecies species, Vec3 pos, float yaw, KillRecord record) {
      KillCarcass c = new KillCarcass(EcologyContent.KILL_CARCASS.get(), level);
      c.entityData.set(SPECIES, species.ordinal());
      c.entityData.set(KILLED_AT, record.killedAt);
      c.entityData.set(BODY_YAW, net.minecraft.util.Mth.wrapDegrees(yaw));
      c.entityData.set(LEFT, level.random.nextBoolean());
      c.record = record;
      c.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
      c.refreshDimensions();
      return c;
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder b) {
      b.define(SPECIES, WildlifeSpecies.PRONGHORN.ordinal());
      b.define(KILLED_AT, 0L);
      b.define(BODY_YAW, 0.0F);
      b.define(LEFT, false);
   }

   public WildlifeSpecies species() {
      WildlifeSpecies[] v = WildlifeSpecies.values();
      int i = this.entityData.get(SPECIES);
      return i >= 0 && i < v.length ? v[i] : WildlifeSpecies.PRONGHORN;
   }

   public long killedAt() {
      return this.entityData.get(KILLED_AT);
   }

   public float bodyYaw() {
      return this.entityData.get(BODY_YAW);
   }

   /** which side it lies on */
   public boolean leftSide() {
      return this.entityData.get(LEFT);
   }

   public KillRecord record() {
      return this.record;
   }

   public void record(KillRecord r) {
      this.record = r;
      this.entityData.set(KILLED_AT, r.killedAt);
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (SPECIES.equals(key)) {
         this.refreshDimensions();
      }
   }

   @Override
   public EntityDimensions getDimensions(Pose pose) {
      WildlifeSpecies s = this.species();
      // lying on its side: about as long as it stood tall and as high as it was wide
      return EntityDimensions.scalable(Math.max(0.5F, Math.min(1.8F, s.height * 0.95F)), Math.max(0.3F, Math.min(0.9F, s.width * 0.75F)));
   }

   @Override
   public void tick() {
      super.tick();
      if (!this.level().isClientSide) {
         Vec3 v = this.getDeltaMovement();
         if (!this.onGround() || v.lengthSqr() > 1.0E-6) {
            this.setDeltaMovement(v.x * 0.5, this.onGround() ? 0.0 : Math.max(-1.0, v.y - 0.04), v.z * 0.5);
            this.move(MoverType.SELF, this.getDeltaMovement());
         }
         if (this.tickCount % 20 == 0 && this.level() instanceof ServerLevel level) {
            if (this.getY() < level.getMinBuildHeight() - 32) {
               this.discard();
               return;
            }
            if (level.getGameTime() - this.killedAt() > EcologyConfig.carcassTicks() || this.killedAt() > level.getGameTime() + 24000L) {
               KillSites.decayed(level, this.position(), this.record, this.getUUID());
               this.discard();
            }
         }
      }
   }

   /** the body is drawn lying out past its square hitbox (nose to tail, legs): cull on a box that holds it */
   @Override
   public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
      return this.getBoundingBox().inflate(1.2, 0.8, 1.2);
   }

   @Override
   public boolean isPickable() {
      return !this.isRemoved();
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public boolean canBeCollidedWith() {
      return false;
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      // an op in creative clears it away; nothing else can harm a carcass
      if (!this.level().isClientSide && source.getEntity() instanceof Player p && p.isCreative() && !this.isRemoved()) {
         this.discard();
         return true;
      }
      return false;
   }

   @Override
   public InteractionResult interact(Player player, InteractionHand hand) {
      if (hand != InteractionHand.MAIN_HAND) {
         return InteractionResult.PASS;
      }
      if (!this.level().isClientSide && this.level() instanceof ServerLevel level) {
         KillSites.read(player, this.record, level.getGameTime());
      }
      return InteractionResult.sidedSuccess(this.level().isClientSide);
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag t) {
      this.entityData.set(SPECIES, Math.clamp(t.getInt("species"), 0, WildlifeSpecies.values().length - 1));
      this.entityData.set(KILLED_AT, t.getLong("killed_at"));
      float yaw = t.getFloat("body_yaw");
      this.entityData.set(BODY_YAW, Float.isFinite(yaw) ? net.minecraft.util.Mth.wrapDegrees(yaw) : 0.0F);
      this.entityData.set(LEFT, t.getBoolean("left"));
      this.record = KillRecord.load(t.getCompound("kill"));
      this.refreshDimensions();
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag t) {
      t.putInt("species", this.entityData.get(SPECIES));
      t.putLong("killed_at", this.killedAt());
      t.putFloat("body_yaw", this.bodyYaw());
      t.putBoolean("left", this.leftSide());
      t.put("kill", this.record.save());
   }

   @Override
   public boolean shouldBeSaved() {
      return !this.isRemoved();
   }
}
