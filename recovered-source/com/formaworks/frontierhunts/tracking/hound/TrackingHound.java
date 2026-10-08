package com.formaworks.frontierhunts.tracking.hound;

import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.tracking.ScentLedger;
import com.formaworks.frontierhunts.tracking.TrailMark;
import com.formaworks.frontierhunts.tracking.TrailService;
import com.formaworks.frontierhunts.tracking.TrailStore;
import com.formaworks.frontierhunts.tracking.WildlifeBleeding;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * [tracking][hound3] The player's tracking hound (redbone / bluetick coonhound).
 *
 * <p>Orders (Hound Lead / right-click / the call key, see {@link HoundCommands}): HEEL (close at heel, teleports back like
 * a vanilla wolf when left far behind), STAY (sits; lies down after a while), TRACK (runs the blood / track line of the
 * animal you wounded, or the nearest fresh sign), SEARCH (ranges ahead, strikes fresh game scent and points it),
 * COME (drop everything and come back). The decisions live in {@link HoundBrain}; this class senses the world for it,
 * drives the navigation (stuck detection: jumps, re-paths, skips an unreachable stretch, never spins in place), the
 * voice, and syncs mode / flags / quarry label for the animation and the owner's HUD. Server-authoritative.
 */
public class TrackingHound extends TamableAnimal {
   /** [1.1.6] the outline your own hound gets through brush (HoundOutline) is blaze orange, like its collar */
   @Override
   public int getTeamColor() {
      return this.getTeam() == null ? 0xFF7A1A : super.getTeamColor();
   }

   public static final int HEEL = HoundBrain.HEEL, SIT = HoundBrain.SIT, TRACK = HoundBrain.TRACK, CAST = HoundBrain.CAST, BAY = HoundBrain.BAY,
      FOUND = HoundBrain.FOUND, RETREAT = HoundBrain.RETREAT, LOST = HoundBrain.LOST, SEARCH = HoundBrain.SEARCH, STRIKE = HoundBrain.STRIKE,
      POINT = HoundBrain.POINT, STOP = HoundBrain.STOP; // [hound4] STOP
   /** synced flag bits */
   public static final int F_LYING = 1, F_WAITING = 2, F_HOT = 4, F_NOSE = 8, F_DONE = 16;
   private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(TrackingHound.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> EXCITE = SynchedEntityData.defineId(TrackingHound.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(TrackingHound.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.defineId(TrackingHound.class, EntityDataSerializers.INT); // [hound3]
   private static final EntityDataAccessor<String> LABEL = SynchedEntityData.defineId(TrackingHound.class, EntityDataSerializers.STRING); // [hound3]
   private static final String[] NAMES = {"Belle", "Copper", "Duke", "Ranger", "Blue", "Rosie", "Hank", "Sadie", "Rusty", "Maggie", "Boone", "Dixie"};
   /** teleport to the owner beyond this (at heel); a vanilla wolf does at 12 */
   private static final double HEEL_TELEPORT = 20.0, SEARCH_TELEPORT = 96.0;

   // ---- server working state (not saved: a reload sends the hound back to heel)
   private final HoundBrain brain = new HoundBrain();
   private HoundBrain.Plan plan;
   private int retreatTicks;
   private int resumeMode = HEEL;
   private int stayTicks;
   private int doneTicks;
   private Vec3 navGoal;
   private double navSpeed;
   private int navFail;
   private Vec3 progressAt;
   private double progressDist;
   private int progressTicks;
   private boolean stuck;
   private int pendingTrackAt = -1;
   private UUID pendingTrack;
   private long pendingFrom;
   private Vec3 pendingPos;
   /** [1.1.7] walking around an obstacle: the side point, ticks left, which side to try first, ticks spent pushing into something */
   private Vec3 detour;
   private int detourTicks;
   private int detourSide = 1;
   private int bump;

   public TrackingHound(EntityType<? extends TrackingHound> type, Level level) {
      super(type, level);
      this.setPathfindingMalus(PathType.WATER, 0.0F);       // [hound3] a hound swims a creek on the line
      this.setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
      if (this.navigation instanceof GroundPathNavigation g) {
         g.setCanFloat(true);
         g.setCanOpenDoors(false);
      }
      this.navigation.setMaxVisitedNodesMultiplier(2.0F); // [1.1.7] room for the pathfinder to find a way round a thicket or a fence line
      this.brain.seed(this.getUUID().getLeastSignificantBits());
   }

   public static AttributeSupplier.Builder attributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 30.0)
         .add(Attributes.MOVEMENT_SPEED, 0.3)
         .add(Attributes.FOLLOW_RANGE, 64.0)
         .add(Attributes.STEP_HEIGHT, 1.0)
         .add(Attributes.ATTACK_DAMAGE, 1.0);
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder b) {
      super.defineSynchedData(b);
      b.define(MODE, HEEL);
      b.define(EXCITE, 0.0F);
      b.define(VARIANT, 0);
      b.define(FLAGS, 0);
      b.define(LABEL, "");
   }

   public int mode() {
      return this.entityData.get(MODE);
   }

   public float excitement() {
      return this.entityData.get(EXCITE);
   }

   public int variant() {
      return this.entityData.get(VARIANT);
   }

   /** [hound3] synced flags (F_*) */
   public int flags() {
      return this.entityData.get(FLAGS);
   }

   public boolean flag(int f) {
      return (this.flags() & f) != 0;
   }

   /** [hound3] what it is working, e.g. "whitetail deer" (empty at heel) */
   public String quarryLabel() {
      return this.entityData.get(LABEL);
   }

   /** the look for the renderer: redbone (0) or bluetick (1) */
   public String coat() {
      return this.variant() == 1 ? "bluetick" : "redbone";
   }

   private void mode(int m) {
      if (this.mode() != m) {
         this.entityData.set(MODE, m);
      }
   }

   private void setFlag(int f, boolean on) {
      int c = this.flags(), n = on ? c | f : c & ~f;
      if (n != c) {
         this.entityData.set(FLAGS, n);
      }
   }

   private void label(String s) {
      if (!s.equals(this.quarryLabel())) {
         this.entityData.set(LABEL, s.length() > 40 ? s.substring(0, 40) : s);
      }
   }

   private void excite(float e) {
      float c = this.excitement();
      if (Math.abs(e - c) > 0.02F) {
         this.entityData.set(EXCITE, Math.clamp(e, 0.0F, 1.0F));
      }
   }

   /** [hunts] the animal this hound is trailing or baying (null at heel) - read by the species hunts ("behind the hounds"). */
   public UUID quarry() {
      return this.brain.quarry;
   }

   public boolean tracking() {
      int m = this.mode();
      return m == TRACK || m == CAST || m == BAY || m == FOUND;
   }

   /** [hound3] on any job (trail or search) */
   public boolean working() {
      int m = this.mode();
      return this.tracking() || m == SEARCH || m == STRIKE || m == POINT;
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
      this.goalSelector.addGoal(2, new WorkGoal());
      this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
      this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
   }

   // ------------------------------------------------------------------------------------------ orders (server)

   public void setup(ServerPlayer owner) {
      this.tame(owner);
      this.entityData.set(VARIANT, this.random.nextInt(2));
      this.setCustomName(Component.literal(NAMES[this.random.nextInt(NAMES.length)]));
      this.setPersistenceRequired();
   }

   public void sit(boolean sit) {
      this.stopWork(null);
      this.setOrderedToSit(sit);
      this.setInSittingPose(sit);
      this.mode(sit ? SIT : HEEL);
      this.stayTicks = 0;
      this.setFlag(F_LYING, false);
      this.navigation.stop();
   }

   /** [hound4] back from the kennel: the same name and coat */
   public void restore(String name, int variant) {
      if (name != null && !name.isEmpty()) {
         this.setCustomName(Component.literal(name.length() > 32 ? name.substring(0, 32) : name));
      }
      if (variant >= 0) {
         this.entityData.set(VARIANT, Math.clamp(variant, 0, 1));
      }
   }

   /** [hound4] "stop": drop whatever he is doing and stand still right here (no heel, no teleport) until the next order */
   public void halt() {
      this.stopWork(null);
      this.setOrderedToSit(false);
      this.setInSittingPose(false);
      this.mode(STOP);
      this.stayTicks = 0;
      this.setFlag(F_LYING, false);
      this.navigation.stop();
   }

   /** [hound4] sent home: a puff, a parting bark, gone. The registry keeps him in the kennel (name + coat) for the lead. */
   public void dismiss() {
      if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
         return;
      }
      this.stopWork(null);
      level.broadcastEntityEvent(this, net.minecraft.world.entity.EntityEvent.POOF);
      level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, this.getX(), this.getY() + 0.4, this.getZ(), 14, 0.3, 0.3, 0.3, 0.02);
      level.playSound(null, this.getX(), this.getY() + 0.5, this.getZ(), SoundEvents.WOLF_AMBIENT, SoundSource.NEUTRAL, 0.9F, 0.85F);
      if (this.getOwnerUUID() != null) {
         HoundRegistry.get(level.getServer()).kennel(this.getOwnerUUID(), this);
      }
      this.discard();
   }

   /** "come": up, off any job, back to heel (and closer than a whistle if it is far / out of sight) */
   public void come(Player owner) {
      this.stopWork(null);
      this.setOrderedToSit(false);
      this.setInSittingPose(false);
      this.mode(HEEL);
      if (owner != null && this.level() == owner.level() && this.distanceToSqr(owner) > 900.0 && !this.hasLineOfSight(owner)) {
         this.tryToTeleportToOwner();
      }
      this.playSound(SoundEvents.WOLF_PANT, 0.6F, this.getVoicePitch());
   }

   /** put the nose on the line of the animal that left this mark */
   public void startTrack(TrailMark mark) {
      this.startTrack(mark.animal(), mark.created() - 40L, mark.position(), labelFor(this.level(), mark.animal(), mark.kind()));
   }

   /** [hound3] put the nose on an animal's line from {@code from} (game time) at {@code at} */
   public void startTrack(UUID animal, long from, Vec3 at, String label) {
      this.setOrderedToSit(false);
      this.setInSittingPose(false);
      this.setFlag(F_LYING | F_DONE, false);
      this.brain.track(animal, from, at, this.position());
      this.mode(TRACK);
      this.label(label);
      this.plan = null;
      this.pendingTrackAt = -1;
      this.playSound(SoundEvents.FOX_SNIFF, 0.7F, 0.78F);
   }

   /** [hound3] range out ahead and find fresh game */
   public void startSearch() {
      this.setOrderedToSit(false);
      this.setInSittingPose(false);
      this.setFlag(F_LYING | F_DONE, false);
      this.brain.search();
      this.mode(SEARCH);
      this.label("");
      this.plan = null;
      this.playSound(SoundEvents.FOX_SNIFF, 0.7F, 0.85F);
   }

   /** [hound3] the owner just wounded the animal this hound was striking / pointing: it goes on the blood a moment later */
   void ownerWounded(UUID animal, long at, Vec3 pos) {
      int m = this.mode();
      if ((m == STRIKE || m == POINT || m == SEARCH) && animal.equals(this.brain.quarry) || m == POINT) {
         this.pendingTrack = animal;
         this.pendingFrom = at - 40L;
         this.pendingPos = pos;
         this.pendingTrackAt = this.tickCount + 50;
      }
   }

   private void stopWork(String why) {
      boolean was = this.brain.quarry != null || this.working();
      this.brain.heel();
      this.plan = null;
      this.navGoal = null;
      if (this.working() || this.mode() == LOST) {
         this.mode(HEEL);
      }
      this.entityData.set(EXCITE, 0.0F);
      this.setFlag(F_WAITING | F_HOT | F_NOSE | F_DONE, false);
      this.label("");
      if (was && why != null && this.getOwner() instanceof ServerPlayer p) {
         p.displayClientMessage(Component.literal(this.getName().getString() + " " + why), true);
      }
   }

   // ------------------------------------------------------------------------------------------ interaction

   @Override
   public InteractionResult mobInteract(Player player, InteractionHand hand) {
      if (!this.isOwnedBy(player)) {
         return InteractionResult.PASS;
      }
      ItemStack stack = player.getItemInHand(hand);
      if (stack.is(ItemTags.WOLF_FOOD) && this.getHealth() < this.getMaxHealth()) {
         if (!this.level().isClientSide) {
            this.heal(6.0F);
            if (!player.hasInfiniteMaterials()) {
               stack.shrink(1);
            }
            this.playSound(SoundEvents.GENERIC_EAT, 0.8F, 1.0F);
         }
         return InteractionResult.sidedSuccess(this.level().isClientSide);
      }
      if (hand != InteractionHand.MAIN_HAND) {
         return InteractionResult.PASS;
      }
      // [hound4] like a vanilla wolf: right-click with an empty hand = sit <-> stand up (at heel).
      // Sneak-right-click (or right-click holding the Hound Lead) opens the command wheel on the client (HoundClient).
      if (player.isShiftKeyDown() || stack.is(HoundContent.LEAD.get())) {
         if (this.level().isClientSide) {
            HoundNet.openWheel.accept(this);
         }
         return InteractionResult.sidedSuccess(this.level().isClientSide);
      }
      if (!stack.isEmpty()) {
         return InteractionResult.PASS; // a rifle / bow / tool keeps its own use
      }
      if (!this.level().isClientSide) {
         boolean sit = !this.isOrderedToSit();
         this.sit(sit);
         player.displayClientMessage(Component.translatable(sit ? "hound.frontierhunts.said.sit" : "hound.frontierhunts.said.up", this.getName()), true);
      }
      return InteractionResult.sidedSuccess(this.level().isClientSide);
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      Entity by = source.getEntity();
      // [hound4] the owner CAN hurt (and kill) his own hound - the user asked for a way to put him down
      if (this.isInvulnerableTo(source)) {
         return false;
      }
      boolean hit = super.hurt(source, amount);
      if (hit && !this.level().isClientSide && by instanceof LivingEntity && by != this.getOwner()) {
         this.retreat();
      }
      return hit;
   }

   @Override
   public boolean isAlliedTo(Entity other) {
      return super.isAlliedTo(other) || other instanceof TrackingHound h && h.getOwnerUUID() != null && h.getOwnerUUID().equals(this.getOwnerUUID());
   }

   @Override
   public boolean canAttack(LivingEntity target) {
      return false;
   }

   @Override
   public void die(DamageSource source) {
      if (!this.level().isClientSide && this.getOwnerUUID() != null && this.level().getServer() != null) {
         HoundRegistry.get(this.level().getServer()).remove(this.getOwnerUUID(), this.getUUID());
         // [hound4] tell the owner plainly (vanilla adds its own death line when showDeathMessages is on)
         if (this.getOwner() instanceof ServerPlayer p && !this.isRemoved()) {
            p.sendSystemMessage(Component.translatable("hound.frontierhunts.said.died", this.getName()).withStyle(net.minecraft.ChatFormatting.RED));
         }
      }
      super.die(source);
   }

   @Override
   public boolean isFood(ItemStack stack) {
      return false;
   }

   @Override
   public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
      return null;
   }

   @Override
   public boolean removeWhenFarAway(double d) {
      return false;
   }

   // ------------------------------------------------------------------------------------------ voice

   @Override
   protected SoundEvent getAmbientSound() {
      if (this.working() || this.isInSittingPose()) {
         return null;
      }
      return this.random.nextInt(4) == 0 ? SoundEvents.WOLF_PANT : null;
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return SoundEvents.WOLF_HURT;
   }

   @Override
   protected SoundEvent getDeathSound() {
      return SoundEvents.WOLF_DEATH;
   }

   @Override
   public float getVoicePitch() {
      // a hound is deeper-chested than a wolf
      return 0.82F + this.random.nextFloat() * 0.08F;
   }

   private void voice(SoundEvent e, float volume, float pitch) {
      this.level().playSound(null, this.getX(), this.getY() + 0.6, this.getZ(), e, SoundSource.NEUTRAL, volume, pitch);
   }

   /** the long drawn-out bawl of a hound on a track: a pitched-down howl, higher and more urgent when hot */
   private void bawl(float excite) {
      this.voice(SoundEvents.WOLF_HOWL, 1.4F + excite, 1.02F + 0.16F * excite + this.random.nextFloat() * 0.05F);
   }

   private void chop(float excite) {
      this.voice(SoundEvents.WOLF_AMBIENT, 1.6F + excite, 0.78F + 0.1F * excite + this.random.nextFloat() * 0.06F);
   }

   private void speak(int v, float ex) {
      switch (v) {
         case HoundBrain.V_SNIFF -> this.voice(SoundEvents.FOX_SNIFF, 0.55F, 0.72F + this.random.nextFloat() * 0.08F);
         case HoundBrain.V_BAWL -> this.bawl(ex);
         case HoundBrain.V_CHOP -> this.chop(ex);
         case HoundBrain.V_WHINE -> this.voice(SoundEvents.WOLF_WHINE, 0.9F, 0.95F + this.random.nextFloat() * 0.1F);
         case HoundBrain.V_STRIKE -> {
            this.voice(SoundEvents.FOX_SNIFF, 0.8F, 0.9F);
            this.voice(SoundEvents.WOLF_WHINE, 0.7F, 1.15F);
         }
         default -> {
         }
      }
   }

   // ------------------------------------------------------------------------------------------ tick

   @Override
   public void tick() {
      super.tick();
      if (this.level() instanceof ServerLevel level) {
         if (this.tickCount % 100 == 0 && this.getOwnerUUID() != null) {
            HoundRegistry reg = HoundRegistry.get(level.getServer());
            HoundRegistry.Entry e = reg.get(this.getOwnerUUID());
            if (e == null || e.home || !e.hound.equals(this.getUUID())) {
               // this owner has taken on a new hound since this one was lost [hound4] / sent him home / no longer has one
               this.discard();
               return;
            }
            reg.seen(this.getOwnerUUID(), this);
         }
         if (this.isOrderedToSit() && this.mode() != SIT) {
            this.stopWork(null);
            this.mode(SIT);
         } else if (!this.isOrderedToSit() && this.mode() == SIT) {
            this.mode(HEEL);
         }
         // a long stay: lies down
         if (this.mode() == SIT) {
            if (++this.stayTicks == 500) {
               this.setFlag(F_LYING, true);
            }
         } else {
            this.stayTicks = 0;
            if (this.mode() != FOUND) {
               this.setFlag(F_LYING, false);
            }
         }
         if (this.tickCount % 10 == 0 && this.mode() != RETREAT && this.predatorNear() != null) {
            this.retreat();
         }
         if (this.pendingTrackAt >= 0 && this.tickCount >= this.pendingTrackAt) {
            this.pendingTrackAt = -1;
            if (this.pendingTrack != null) {
               Entity q = level.getEntity(this.pendingTrack);
               this.startTrack(this.pendingTrack, this.pendingFrom, this.pendingPos, labelFor(level, this.pendingTrack, "blood"));
               if (this.getOwner() instanceof ServerPlayer p && q != null) {
                  p.displayClientMessage(Component.translatable("hound.frontierhunts.said.on_blood", this.getName()), true);
               }
            }
         }
      }
   }

   private void retreat() {
      if (this.mode() != RETREAT && this.getOwner() != null) {
         this.resumeMode = this.mode();
         this.mode(RETREAT);
         this.retreatTicks = 80;
         this.setOrderedToSit(false);
         this.setInSittingPose(false);
         this.voice(SoundEvents.WOLF_WHINE, 1.0F, 0.9F);
      }
   }

   /** a real threat to a hound within reach, or null */
   private LivingEntity predatorNear() {
      List<LivingEntity> near = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(10.0, 4.0, 10.0), e -> e != this && e.isAlive());
      for (LivingEntity e : near) {
         double d = this.distanceToSqr(e);
         if (e instanceof WildlifeMob m) {
            WildlifeSpecies s = m.species;
            boolean big = s == WildlifeSpecies.WOLF || s == WildlifeSpecies.COUGAR || s == WildlifeSpecies.GRIZZLY || s == WildlifeSpecies.BLACK_BEAR
               || s == WildlifeSpecies.POLAR_BEAR || s == WildlifeSpecies.LION || s == WildlifeSpecies.PANTHER;
            if (big && !m.isDeadOrDying() && (d < 36.0 || m.behavior() == WildlifeMob.WARN || m.getTarget() == this)) {
               return m;
            }
         } else if (e instanceof Enemy && e instanceof Mob mob && (mob.getTarget() == this || mob.getTarget() == this.getOwner() || d < 25.0)) {
            return e;
         } else if (e instanceof Wolf w && !w.isTame() && (w.isAngry() || w.getTarget() == this)) {
            return e;
         } else if (e instanceof PolarBear b && (b.getTarget() == this || d < 25.0)) {
            return e;
         }
      }
      return null;
   }

   // ------------------------------------------------------------------------------------------ the work

   private final class WorkGoal extends Goal {
      WorkGoal() {
         this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
      }

      @Override
      public boolean canUse() {
         return !TrackingHound.this.isOrderedToSit() && TrackingHound.this.getOwner() != null;
      }

      @Override
      public boolean requiresUpdateEveryTick() {
         return true;
      }

      @Override
      public void stop() {
         TrackingHound.this.navigation.stop();
         TrackingHound.this.navGoal = null;
      }

      @Override
      public void tick() {
         TrackingHound.this.work();
      }
   }

   private void work() {
      LivingEntity owner = this.getOwner();
      if (owner == null || !(this.level() instanceof ServerLevel level)) {
         return;
      }
      switch (this.mode()) {
         case RETREAT -> {
            this.excite(0.0F);
            if (--this.retreatTicks <= 0 && this.predatorNear() == null) {
               int back = this.resumeMode;
               this.mode(back == RETREAT || back == SIT ? HEEL : back);
               this.brain.mode = this.mode();
               this.plan = null;
            } else if (this.distanceToSqr(owner) > 4.0) {
               if (this.tickCount % 5 == 0) {
                  this.navigation.moveTo(owner, 1.55);
               }
            } else {
               this.navigation.stop();
               this.getLookControl().setLookAt(owner);
            }
         }
         case TRACK, CAST, BAY, FOUND, SEARCH, STRIKE, POINT -> this.jobTick(level, owner);
         case STOP -> { // [hound4] stand still, watch the hunter now and then
            this.excite(0.0F);
            this.setFlag(F_NOSE | F_HOT | F_WAITING, false);
            this.navigation.stop();
            if (this.tickCount % 60 < 30) {
               this.getLookControl().setLookAt(owner, 20.0F, 20.0F);
            }
         }
         case LOST -> {
            this.mode(HEEL);
            this.heel(owner);
         }
         default -> this.heel(owner);
      }
   }

   private void heel(LivingEntity owner) {
      this.excite(0.0F);
      this.setFlag(F_NOSE | F_HOT | F_WAITING, false);
      double d = this.distanceToSqr(owner);
      if (d > HEEL_TELEPORT * HEEL_TELEPORT && this.tickCount % 10 == 0 && this.level() == owner.level()) {
         this.tryToTeleportToOwner();
      } else if (d > 7.0) {
         if (this.tickCount % 8 == 0 || this.navigation.isDone()) {
            // at heel: just behind and beside the hunter, trotting to catch up
            Vec3 look = owner.getLookAngle().multiply(1.0, 0.0, 1.0);
            look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize();
            Vec3 at = owner.position().add(look.scale(-1.4)).add(new Vec3(look.z, 0, -look.x).scale(1.1));
            if (!this.detouring()) {
               this.navigation.moveTo(at.x, at.y, at.z, d > 64.0 ? 1.45 : 1.1);
            }
         }
         this.unstick(owner.position(), d > 64.0 ? 1.45 : 1.1);
      } else if (d < 3.0) {
         this.navigation.stop();
         if (this.tickCount % 40 < 20) {
            this.getLookControl().setLookAt(owner, 20.0F, 20.0F);
         }
      }
   }

   private void jobTick(ServerLevel level, LivingEntity owner) {
      if (!TrailService.active(level)) {
         this.stopWork("stops - tracking is switched off here");
         return;
      }
      if (this.mode() == SEARCH && this.distanceToSqr(owner) > SEARCH_TELEPORT * SEARCH_TELEPORT && this.tickCount % 20 == 0) {
         this.tryToTeleportToOwner();
      }
      if (this.plan == null || (this.tickCount + this.getId()) % 10 == 0) {
         this.think(level, owner);
      }
      HoundBrain.Plan p = this.plan;
      if (p == null) {
         return;
      }
      if (p.goal != null && !p.waiting) {
         this.drive(p.goal, p.speed);
      } else {
         if (!this.navigation.isDone()) {
            this.navigation.stop();
         }
         this.navGoal = null;
      }
      if (p.lookAt != null) {
         this.getLookControl().setLookAt(p.lookAt.x, p.lookAt.y, p.lookAt.z, 30.0F, 30.0F);
      }
      int m = this.mode();
      // FOUND: when the hunter walks up, the job is done - it lies by the animal until they move on
      if (m == FOUND && this.brain.foundAt != null) {
         boolean here = owner.position().distanceToSqr(this.brain.foundAt) < 16.0;
         if (here && ++this.doneTicks > 30) {
            this.setFlag(F_DONE, true);
         }
         if (this.flag(F_DONE) && owner.position().distanceToSqr(this.brain.foundAt) > 14.0 * 14.0) {
            this.stopWork(null);
            this.mode(HEEL);
         }
      } else {
         this.doneTicks = 0;
      }
   }

   private void think(ServerLevel level, LivingEntity owner) {
      HoundBrain.Sense s = new HoundBrain.Sense();
      long now = level.getGameTime();
      s.now = now;
      s.hound = this.position();
      s.owner = owner.position();
      s.ownerLook = owner.getLookAngle().multiply(1.0, 0.0, 1.0);
      s.swimming = this.isInWater();
      s.stuck = this.stuck;
      this.stuck = false;
      Wilderness.Wind w = Wilderness.wind(level.getSeed(), now, level.isRaining(), level.isThundering());
      s.wind = new Vec3(w.east(), 0.0, w.south());
      UUID q = this.brain.quarry;
      if (q != null) {
         s.line = gather(level, q, this.brain.cursor, this.position());
         if (level.getEntity(q) instanceof LivingEntity e) {
            s.quarryPos = e.position();
            s.quarryDown = e instanceof Whitetail wt ? wt.downed() || !wt.isAlive() : !e.isAlive() || e.isDeadOrDying();
            s.quarryVisible = this.distanceToSqr(e) < 96.0 * 96.0 && this.hasLineOfSight(e);
            s.quarryWounded = wounded(e);
            Vec3 v = e.getDeltaMovement();
            s.quarryFleeing = v.x * v.x + v.z * v.z > 0.2 * 0.2 || e instanceof WildlifeMob wm && wm.behavior() == WildlifeMob.FLEE;
         }
      }
      int m = this.brain.mode;
      if (m == HoundBrain.SEARCH) {
         s.game = this.game(level, now);
      }
      if (m != this.mode() && this.mode() != RETREAT) {
         // the entity was ordered (or retreated) in between: the entity's mode wins
         this.brain.mode = this.mode();
      }
      HoundBrain.Plan p = this.brain.plan(s);
      this.plan = p;
      if (p.goal != null && (p.mode == CAST || p.mode == SEARCH)) {
         // synthetic goals (cast spiral, quartering legs) go on the ground surface, under the canopy
         int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(p.goal.x), Mth.floor(p.goal.z));
         if (Math.abs(gy - p.goal.y) < 12.0) {
            p.goal = new Vec3(p.goal.x, gy, p.goal.z);
         }
      }
      // sync the result
      if (p.mode == HoundBrain.LOST || p.mode == HoundBrain.HEEL) {
         this.stopWork(p.say);
         this.mode(HEEL);
         this.speak(p.voice, 0.0F);
         return;
      }
      if (p.mode != this.mode()) {
         this.mode(p.mode);
         if (p.mode == STRIKE || p.mode == TRACK && this.brain.quarry != null && this.quarryLabel().isEmpty()) {
            this.label(labelFor(level, this.brain.quarry, "game"));
         }
         if (p.mode == SEARCH) {
            this.label("");
         }
      }
      this.excite(p.excite);
      this.setFlag(F_WAITING, p.waiting);
      this.setFlag(F_HOT, p.hot);
      this.setFlag(F_NOSE, p.nose);
      this.speak(p.voice, p.excite);
      if (p.holdQuarry && q != null && level.getEntity(q) instanceof LivingEntity e) {
         // bayed: a hurt animal stands and faces the dog instead of getting away
         e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 4, false, false));
      }
      if (p.say != null && this.getOwner() instanceof ServerPlayer sp) {
         sp.displayClientMessage(Component.literal(this.getName().getString() + " " + p.say), true);
      }
   }

   /** navigation with progress watching: re-path only when the goal moves, hop obstacles, report "stuck" to the brain */
   private void drive(Vec3 goal, double speed) {
      double d = this.position().distanceTo(goal);
      if (d < 0.9) {
         this.navigation.stop();
         this.navGoal = null;
         return;
      }
      if (this.detouring()) {
         // going round something: don't count it against progress, pick the line back up once past it
         this.progressTicks = 0;
         this.progressDist = Math.min(this.progressDist, d);
         return;
      }
      boolean repath = this.navGoal == null || this.navGoal.distanceToSqr(goal) > 1.5 * 1.5 || this.navigation.isDone() || Math.abs(this.navSpeed - speed) > 0.05;
      if (repath && (this.navGoal == null || this.tickCount % 4 == 0)) {
         this.navGoal = goal;
         this.navSpeed = speed;
         Path path = this.navigation.createPath(BlockPos.containing(goal), 1);
         boolean blocked = path == null || !path.canReach() && path.getEndNode() != null
            && Vec3.atBottomCenterOf(path.getEndNode().asBlockPos()).distanceTo(goal) > 2.5 && d > 4.0;
         if (path != null && !blocked) {
            this.navigation.moveTo(path, speed);
            this.navFail = 0;
         } else if (this.planDetour(goal, speed)) {
            this.navFail = 0;
         } else if (path != null) {
            this.navigation.moveTo(path, speed); // as close as the ground allows; the brain skips the stretch if it stays stuck
         } else if (++this.navFail > 2 && (this.isInWater() || this.clearAhead(goal))) {
            // no path at all (open water, a ledge): steer straight at it and let the swim / jump handle it
            this.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, speed);
         }
      }
      this.unstick(goal, speed);
      // progress: if 2.5 s pass without getting 0.8 blocks closer, it's stuck
      if (this.progressAt == null || this.progressAt.distanceToSqr(goal) > 4.0) {
         this.progressAt = goal;
         this.progressDist = d;
         this.progressTicks = 0;
      } else if (d < this.progressDist - 0.8) {
         this.progressDist = d;
         this.progressTicks = 0;
      } else if (++this.progressTicks > 50) {
         this.progressTicks = 0;
         this.progressDist = d;
         this.stuck = true;
         this.navGoal = null;
         if (this.horizontalCollision && this.onGround()) {
            this.getJumpControl().jump();
         }
      }
      if (this.horizontalCollision && this.isInWater()) {
         // climbing out of water onto a bank
         this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.06, 0.0));
      }
   }

   // ============================================================================================ [1.1.7] going round obstacles

   private boolean detouring() {
      if (this.detour == null) {
         return false;
      }
      if (--this.detourTicks <= 0 || this.position().distanceToSqr(this.detour) < 1.0 || this.navigation.isDone()) {
         this.detour = null;
         this.navGoal = null; // re-path to the real goal from here
         return false;
      }
      return true;
   }

   /** pushing into something for over half a second: hop once, then walk round it */
   private void unstick(Vec3 goal, double speed) {
      if (this.horizontalCollision && this.onGround() && !this.isInWater() && !this.navigation.isDone()) {
         this.bump++;
      } else if (this.bump > 0) {
         this.bump--;
      }
      if (this.bump == 6) {
         this.getJumpControl().jump();
      } else if (this.bump > 12) {
         this.bump = 0;
         if (!this.planDetour(goal, speed)) {
            this.detourSide = -this.detourSide;
         }
      }
   }

   /** nothing solid between the hound and the goal at knee and head height (for steering straight at it) */
   private boolean clearAhead(Vec3 goal) {
      for (double h : new double[]{0.4, 0.9}) {
         Vec3 from = this.position().add(0.0, h, 0.0);
         Vec3 to = new Vec3(goal.x, goal.y + h, goal.z);
         if (this.level().clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            return false;
         }
      }
      return true;
   }

   /**
    * A way round: try ground to either side of the line to the goal (45 to 120 degrees off it, 3 to 7 blocks out), keep
    * the reachable spot that leaves the shortest way on, and walk there first. Alternates sides between attempts.
    */
   private boolean planDetour(Vec3 goal, double speed) {
      Vec3 here = this.position();
      Vec3 to = goal.subtract(here).multiply(1.0, 0.0, 1.0);
      if (to.lengthSqr() < 1.0E-4) {
         return false;
      }
      to = to.normalize();
      Path best = null;
      Vec3 bestAt = null;
      double bestScore = Double.MAX_VALUE;
      int[] angles = {45, -45, 80, -80, 120, -120};
      for (int r : new int[]{3, 5, 7}) {
         for (int a : angles) {
            double ang = Math.toRadians(a * this.detourSide);
            double cos = Math.cos(ang), sin = Math.sin(ang);
            Vec3 c = here.add(new Vec3(to.x * cos - to.z * sin, 0.0, to.x * sin + to.z * cos).scale(r));
            BlockPos bp = this.standable(BlockPos.containing(c));
            if (bp == null) {
               continue;
            }
            Path p = this.navigation.createPath(bp, 0);
            if (p == null || !p.canReach() || p.getNodeCount() > r * 3 + 4) {
               continue;
            }
            Vec3 at = Vec3.atBottomCenterOf(bp);
            double score = at.distanceTo(goal) + p.getNodeCount() * 0.35;
            if (score < bestScore) {
               bestScore = score;
               best = p;
               bestAt = at;
            }
         }
         if (best != null) {
            break;
         }
      }
      if (best == null) {
         return false;
      }
      this.navigation.moveTo(best, speed);
      this.detour = bestAt;
      this.detourTicks = 70;
      this.detourSide = -this.detourSide;
      return true;
   }

   private BlockPos standable(BlockPos p) {
      for (int dy = 2; dy >= -3; dy--) {
         BlockPos q = p.offset(0, dy, 0);
         if (net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeStatic(this, q) == PathType.WALKABLE) {
            return q;
         }
      }
      return null;
   }

   /** fresh game around for SEARCH: deer (and other plains / woods game) with their recent lines */
   private List<HoundBrain.Game> game(ServerLevel level, long now) {
      List<HoundBrain.Game> out = new ArrayList<>();
      for (Map.Entry<UUID, ScentLedger.Point> e : ScentLedger.get(level).recent(this.position(), 96.0, now, HoundBrain.STRIKE_AGE)) {
         if (out.size() >= 12) {
            break;
         }
         Entity a = level.getEntity(e.getKey());
         if (!(a instanceof LivingEntity l) || !l.isAlive() || !game(l) || l instanceof Whitetail w && w.downed()) {
            continue;
         }
         List<HoundBrain.Waypoint> line = new ArrayList<>();
         for (ScentLedger.Point p : ScentLedger.get(level).after(e.getKey(), now - HoundBrain.STRIKE_AGE)) {
            line.add(new HoundBrain.Waypoint(p.pos(), p.time(), p.end()));
         }
         if (line.size() > 160) {
            line = new ArrayList<>(line.subList(line.size() - 160, line.size()));
         }
         Vec3 v = l.getDeltaMovement();
         out.add(new HoundBrain.Game(l.getUUID(), l.position(), this.hasLineOfSight(l), v.x * v.x + v.z * v.z > 0.04, line));
      }
      return out;
   }

   /** what a hound is put on in SEARCH: deer and the other big game you can hunt with hounds (not predators, not birds) */
   static boolean game(LivingEntity e) {
      if (e instanceof Whitetail) {
         return true;
      }
      if (e instanceof WildlifeMob m) {
         WildlifeSpecies s = m.species;
         return s == WildlifeSpecies.ELK || s == WildlifeSpecies.MOOSE || s == WildlifeSpecies.PRONGHORN || s == WildlifeSpecies.BOAR;
      }
      return false;
   }

   static String labelFor(Level level, UUID animal, String fallback) {
      if (level instanceof ServerLevel sl && sl.getEntity(animal) instanceof LivingEntity e) {
         if (e instanceof Whitetail w) {
            try {
               return w.species().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            } catch (RuntimeException ex) {
               return "deer";
            }
         }
         if (e instanceof WildlifeMob m) {
            return m.species.name().toLowerCase(Locale.ROOT).replace('_', ' ');
         }
         return e.getType().getDescription().getString().toLowerCase(Locale.ROOT);
      }
      return fallback;
   }

   static boolean wounded(LivingEntity e) {
      if (e instanceof Whitetail w) {
         return w.bleeding() || w.getHealth() < w.getMaxHealth() * 0.6F;
      }
      return WildlifeBleeding.type(e) != null || e.getHealth() < e.getMaxHealth() * 0.6F;
   }

   @Override
   public void aiStep() {
      super.aiStep();
      if (!this.level().isClientSide) {
         boolean pose = this.isOrderedToSit() || this.mode() == FOUND && this.flag(F_DONE);
         if (pose != this.isInSittingPose()) {
            this.setInSittingPose(pose);
         }
      }
   }

   // ------------------------------------------------------------------------------------------ save

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putInt("HoundVariant", this.variant());
      tag.putBoolean("HoundStop", this.mode() == STOP); // [hound4]
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      this.entityData.set(VARIANT, Math.clamp(tag.getInt("HoundVariant"), 0, 1));
      this.mode(this.isOrderedToSit() ? SIT : tag.getBoolean("HoundStop") ? STOP : HEEL); // [hound4] STOP survives a reload
   }

   /** Sign for the hound: its quarry's scent line merged with that animal's saved prints and blood, in time order. */
   static List<HoundBrain.Waypoint> gather(ServerLevel level, UUID quarry, long from, Vec3 around) {
      List<HoundBrain.Waypoint> out = new ArrayList<>();
      for (ScentLedger.Point p : ScentLedger.get(level).after(quarry, from)) {
         out.add(new HoundBrain.Waypoint(p.pos(), p.time(), p.end()));
      }
      for (TrailMark mk : TrailStore.get(level).nearby(around, 32.0, TrailStore.WORLD_LIMIT, level.getGameTime())) {
         if (mk.animal().equals(quarry) && mk.created() >= from && mk.face() == net.minecraft.core.Direction.UP) {
            out.add(new HoundBrain.Waypoint(mk.position(), mk.created(), false));
         }
      }
      out.sort((a, b) -> Long.compare(a.time(), b.time()));
      return out.size() > 200 ? new ArrayList<>(out.subList(0, 200)) : out;
   }
}
