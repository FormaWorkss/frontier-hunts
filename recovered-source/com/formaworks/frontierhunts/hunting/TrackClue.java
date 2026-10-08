package com.formaworks.frontierhunts.hunting;

import com.formaworks.frontierhunts.HuntRules;
import com.formaworks.frontierhunts.HuntService;
import com.formaworks.frontierhunts.HunterLedger;
import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.tracking.TrailMark;
import com.formaworks.frontierhunts.tracking.TrailService;
import com.formaworks.frontierhunts.tracking.TrailStore;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult.Type;

public final class TrackClue extends Entity {
   private static final EntityDataAccessor<Boolean> BLOOD = SynchedEntityData.defineId(TrackClue.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Long> CREATED = SynchedEntityData.defineId(TrackClue.class, EntityDataSerializers.LONG);
   private static final EntityDataAccessor<Optional<UUID>> ANIMAL = SynchedEntityData.defineId(TrackClue.class, EntityDataSerializers.OPTIONAL_UUID);
   private static final EntityDataAccessor<String> INDIVIDUAL = SynchedEntityData.defineId(TrackClue.class, EntityDataSerializers.STRING);
   private BlockPos support;
   private int rainWear;

   public TrackClue(EntityType<? extends TrackClue> var1, Level var2) {
      super(var1, var2);
      this.setNoGravity(true);
   }

   protected void defineSynchedData(Builder var1) {
      var1.define(BLOOD, false);
      var1.define(CREATED, 0L);
      var1.define(ANIMAL, Optional.empty());
      var1.define(INDIVIDUAL, "whitetail");
   }

   public boolean blood() {
      return (Boolean)this.entityData.get(BLOOD);
   }

   public long ageTicks() {
      return Math.max(0L, this.level().getGameTime() - (Long)this.entityData.get(CREATED));
   }

   public Optional<UUID> animal() {
      return (Optional<UUID>)this.entityData.get(ANIMAL);
   }

   public static boolean leave(Whitetail var0, boolean var1) {
      return TrailService.leave(var0, var1);
   }

   public void tick() {
      super.tick();
      if (this.level() instanceof ServerLevel var1) {
         TrailMark var3 = new TrailMark(
            this.getUUID(),
            this.animal().orElse(TrailMark.UNKNOWN),
            this.position(),
            this.support == null ? this.blockPosition().below() : this.support,
            this.getYRot(),
            (Long)this.entityData.get(CREATED),
            this.rainWear,
            this.blood(),
            (String)this.entityData.get(INDIVIDUAL),
            0
         );
         if (TrailService.supported(var1, var3)) {
            TrailStore.get(var1).add(var3, var1.getGameTime());
         }

         this.discard();
      }
   }

   public boolean isPickable() {
      return true;
   }

   public boolean canBeHitByProjectile() {
      return false;
   }

   public InteractionResult interact(Player var1, InteractionHand var2) {
      if (this.level().isClientSide) {
         return InteractionResult.SUCCESS;
      } else {
         if (var1 instanceof ServerPlayer var3
            && HuntRules.active(this.level())
            && this.level().getGameRules().getBoolean(HuntRules.TRACKING)
            && var1.isAlive()
            && !var1.isSpectator()
            && !(this.distanceToSqr(var1) > 16.0)
            && this.level().clip(new ClipContext(var1.getEyePosition(), this.position().add(0.0, 0.03, 0.0), Block.COLLIDER, Fluid.NONE, var1)).getType()
               == Type.MISS) {
            HunterLedger.get(var3.serverLevel()).inspectClue(var3.getUUID());
            String var4 = this.ageTicks() < 400L ? "fresh" : (this.ageTicks() < 1800L ? "recent" : "fading");
            String var5 = new Wilderness.Wind(-Math.sin(Math.toRadians((double)this.getYRot())), Math.cos(Math.toRadians((double)this.getYRot())))
               .directionTo();
            String var6 = this.animal().map(var0 -> var0.toString().substring(0, 4).toUpperCase(Locale.ROOT)).orElse("----");
            var1.displayClientMessage(
               Component.literal(
                  "Whitetail "
                     + (this.blood() ? "blood trail" : "hoofprints")
                     + "  |  "
                     + var4
                     + "  |  travelling "
                     + var5
                     + "  |  "
                     + (String)this.entityData.get(INDIVIDUAL)
                     + "  |  trail "
                     + var6
               ),
               true
            );
            HuntService.send(var3, false);
            return InteractionResult.CONSUME;
         }

         return InteractionResult.FAIL;
      }
   }

   protected void addAdditionalSaveData(CompoundTag var1) {
      var1.putLong("created", (Long)this.entityData.get(CREATED));
      var1.putBoolean("blood", this.blood());
      var1.putInt("rain_wear", this.rainWear);
      this.animal().ifPresent(var1x -> var1.putUUID("animal", var1x));
      var1.putString("individual", (String)this.entityData.get(INDIVIDUAL));
      if (this.support != null) {
         var1.putLong("support", this.support.asLong());
      }
   }

   protected void readAdditionalSaveData(CompoundTag var1) {
      this.entityData.set(CREATED, Math.min(this.level().getGameTime(), var1.getLong("created")));
      this.entityData.set(BLOOD, var1.getBoolean("blood"));
      this.rainWear = Math.clamp((long)var1.getInt("rain_wear"), 0, 6001);
      this.support = var1.contains("support") ? BlockPos.of(var1.getLong("support")) : null;
      this.entityData.set(ANIMAL, var1.hasUUID("animal") ? Optional.of(var1.getUUID("animal")) : Optional.empty());
      this.entityData.set(INDIVIDUAL, var1.contains("individual") ? var1.getString("individual") : "whitetail");
   }
}
