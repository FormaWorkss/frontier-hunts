package com.formaworks.frontierhunts.hunting;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

final class WildlifeForage extends Goal {
   private static final int THIRSTY = 5200;
   private final Whitetail deer;
   private WildlifeForage.Mode mode;
   private BlockPos food;
   private Vec3 stand;
   private Vec3 aim;
   private Vec3 look;
   private int approachTicks;
   private int approachLimit;
   private int feedTicks;
   private int nextTry;
   private int repath;
   private int fx;
   private int probes;
   private int settleTicks;
   private boolean feeding;
   private boolean drank;
   private Path probe;
   private static final int MAX_PROBES = 3;

   WildlifeForage(Whitetail var1) {
      this.deer = var1;
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
   }

   public boolean canUse() {
      if (this.deer.tickCount >= this.nextTry && this.deer.level() instanceof ServerLevel && this.deer.onGround() && this.deer.calmForForage(false)) {
         RandomSource var1 = this.deer.getRandom();
         this.nextTry = this.deer.tickCount + 200 + var1.nextInt(300);
         this.probes = 0;
         this.probe = null;
         GameSpecies var2 = this.deer.species();
         boolean var3 = var2 == GameSpecies.MOOSE;
         boolean var4 = this.deer.thirst() > 5200;
         if (var4 || var3 && var1.nextFloat() < 0.12F) {
            boolean var5 = var3 && (!var4 || var1.nextFloat() < 0.55F);
            if (this.findWater(var5) || var5 && var4 && this.findWater(false)) {
               return true;
            }
         }
         float var6 = switch (var2) {
            case MOOSE -> 0.45F;
            case ELK -> 0.22F;
            default -> 0.18F;
         };
         if (!this.deer.activeHours()) {
            var6 *= 0.6F;
         }

         return var1.nextFloat() < var6 && this.findBrowse();
      } else {
         return false;
      }
   }

   public void start() {
      this.approachTicks = 0;
      this.feeding = false;
      this.drank = false;
      this.repath = 25;
      this.fx = 0;
      this.settleTicks = 0;
      if (this.probe != null) {
         this.deer.getNavigation().moveTo(this.probe, this.deer.forageSpeed());
      }

      this.approachLimit = this.probe != null ? Math.min(900, Math.max(200, 120 + this.probe.getNodeCount() * 25)) : 200;
      this.deer.setForaging(true);
      RandomSource var1 = this.deer.getRandom();

      this.feedTicks = switch (this.mode) {
         case DRINK -> 100 + var1.nextInt(90);
         case WADE -> 260 + var1.nextInt(260);
         case BROWSE -> 140 + var1.nextInt(140);
      };
   }

   public boolean canContinueToUse() {
      if (this.deer.calmForForage(true) && this.food != null) {
         return this.feeding ? this.feedTicks > 0 : this.approachTicks < this.approachLimit;
      } else {
         return false;
      }
   }

   public boolean requiresUpdateEveryTick() {
      return true;
   }

   public void stop() {
      if (this.drank && this.feedTicks <= 0) {
         this.deer.quench();
      }

      if (this.mode != WildlifeForage.Mode.BROWSE) {
         this.deer.forageHeadDown(0);
      }

      this.deer.setForaging(false);
      this.deer.getNavigation().stop();
      this.food = null;
      this.feeding = false;
      this.probe = null;
      this.nextTry = this.deer.tickCount + 300 + this.deer.getRandom().nextInt(700);
   }

   public void tick() {
      ServerLevel var1 = (ServerLevel)this.deer.level();
      if (!this.feeding) {
         this.approachTicks++;
         double var11 = this.stand.x - this.deer.getX();
         double var12 = this.stand.z - this.deer.getZ();
         double var13 = var11 * var11 + var12 * var12;
         boolean var8 = Math.abs(this.stand.y - this.deer.getY()) < 1.3;
         if ((!(var13 < 0.16) || !var8) && (this.settleTicks <= 40 || !(var13 < 1.2) || !var8)) {
            PathNavigation var9 = this.deer.getNavigation();
            if (!(var13 < 3.2) || !var8 || !var9.isDone() && !(var13 < 1.2)) {
               if (var9.isDone() && --this.repath <= 0) {
                  this.repath = 30;
                  Path var10 = var9.createPath(BlockPos.containing(this.stand), 0);
                  if (var10 == null || !var9.moveTo(var10, this.deer.forageSpeed())) {
                     this.approachTicks += 80;
                  }
               }
            } else {
               var9.stop();
               this.settleTicks++;
               this.deer.getMoveControl().setWantedPosition(this.stand.x, this.stand.y, this.stand.z, this.deer.forageSpeed());
            }

            this.deer.getLookControl().setLookAt(this.aim.x, this.aim.y, this.aim.z, 10.0F, 10.0F);
         } else {
            this.feeding = true;
            this.deer.getNavigation().stop();
            this.look = this.aim;
         }
      } else {
         float var2 = (float)(Math.atan2(this.aim.z - this.deer.getZ(), this.aim.x - this.deer.getX()) * 180.0F / (float)Math.PI) - 90.0F;
         float var3 = Mth.approachDegrees(this.deer.getYRot(), var2, 5.0F);
         this.deer.setYRot(var3);
         this.deer.yBodyRot = var3;
         this.deer.setDeltaMovement(this.deer.getDeltaMovement().multiply(0.4, 1.0, 0.4));
         boolean var4 = Math.abs(Mth.wrapDegrees(var2 - var3)) < 25.0F;
         RandomSource var5 = this.deer.getRandom();
         if (this.mode == WildlifeForage.Mode.BROWSE) {
            if (!var1.getBlockState(this.food).is(BlockTags.LEAVES)) {
               this.feedTicks = 0;
               return;
            }

            if (this.feedTicks % 30 == 0) {
               this.look = new Vec3(
                  (double)this.food.getX() + 0.2 + var5.nextDouble() * 0.6,
                  (double)this.food.getY() + 0.15 + var5.nextDouble() * 0.6,
                  (double)this.food.getZ() + 0.2 + var5.nextDouble() * 0.6
               );
            }

            this.deer.getLookControl().setLookAt(this.look.x, this.look.y, this.look.z, 30.0F, 30.0F);
            if (var4 && ++this.fx % 26 == 0) {
               BlockState var6 = var1.getBlockState(this.food);
               var1.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, var6), this.look.x, this.look.y, this.look.z, 5, 0.22, 0.18, 0.22, 0.02);
               var1.playSound(null, this.food, (SoundEvent)HuntSounds.BRUSH_RUSTLE.get(), SoundSource.NEUTRAL, 0.45F, 0.9F + var5.nextFloat() * 0.2F);
               if (var5.nextFloat() < 0.6F) {
                  var1.playSound(null, this.deer.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.22F, 0.55F + var5.nextFloat() * 0.1F);
               }
            }
         } else {
            this.deer.getLookControl().setLookAt(this.aim.x, this.aim.y, this.aim.z, 10.0F, 10.0F);
            if (var4 && this.deer.motionSpeed(0.0F) < 0.02F) {
               this.deer.forageHeadDown(Math.max(2, this.feedTicks));
            }

            if (this.deer.graze(1.0F) > 0.6F) {
               this.drank = true;
            }

            if (this.deer.graze(1.0F) > 0.6F && ++this.fx % (this.mode == WildlifeForage.Mode.WADE ? 17 : 23) == 0) {
               var1.sendParticles(
                  ParticleTypes.SPLASH, this.aim.x, this.aim.y + 0.05, this.aim.z, this.mode == WildlifeForage.Mode.WADE ? 7 : 4, 0.18, 0.02, 0.18, 0.05
               );
               if (this.mode == WildlifeForage.Mode.WADE && var5.nextFloat() < 0.5F) {
                  var1.sendParticles(ParticleTypes.BUBBLE, this.aim.x, this.aim.y - 0.15, this.aim.z, 4, 0.15, 0.05, 0.15, 0.02);
               }

               var1.playSound(
                  null,
                  this.deer.blockPosition(),
                  this.mode == WildlifeForage.Mode.WADE ? SoundEvents.GENERIC_SPLASH : SoundEvents.GENERIC_DRINK,
                  SoundSource.NEUTRAL,
                  this.mode == WildlifeForage.Mode.WADE ? 0.18F : 0.3F,
                  0.5F + var5.nextFloat() * 0.15F
               );
            }
         }

         this.feedTicks--;
      }
   }

   private boolean findWater(boolean var1) {
      ServerLevel var2 = (ServerLevel)this.deer.level();
      RandomSource var3 = this.deer.getRandom();
      BlockPos var4 = this.deer.blockPosition();

      for (int var5 = 0; var5 < 48; var5++) {
         int var6 = 3 + var3.nextInt(20);
         double var7 = var3.nextDouble() * Math.PI * 2.0;
         int var9 = var4.getX() + (int)Math.round(Math.cos(var7) * (double)var6);
         int var10 = var4.getZ() + (int)Math.round(Math.sin(var7) * (double)var6);
         if (var2.isLoaded(new BlockPos(var9, var4.getY(), var10))) {
            int var11 = var2.getHeight(Types.WORLD_SURFACE, var9, var10) - 1;
            if (Math.abs(var11 + 1 - var4.getY()) <= 5) {
               BlockPos var12 = new BlockPos(var9, var11, var10);
               if (var2.getFluidState(var12).is(FluidTags.WATER) && var2.getBlockState(var12.above()).isAir()) {
                  if (!var1) {
                     for (Direction var19 : Plane.HORIZONTAL.shuffledCopy(var3)) {
                        BlockPos var20 = var12.relative(var19);
                        BlockState var21 = var2.getBlockState(var20);
                        if (!var2.getFluidState(var20).is(FluidTags.WATER)
                           && var21.isFaceSturdy(var2, var20, Direction.UP)
                           && var2.getBlockState(var20.above()).isAir()
                           && var2.getBlockState(var20.above(2)).isAir()) {
                           if (this.reachable(var20.above())) {
                              Vec3 var22 = Vec3.atLowerCornerOf(var12.subtract(var20));
                              this.mode = WildlifeForage.Mode.DRINK;
                              this.food = var12;
                              this.stand = Vec3.atBottomCenterOf(var20.above()).add(var22.scale(0.28));
                              this.aim = Vec3.atCenterOf(var12).add(0.0, 0.38, 0.0).subtract(var22.scale(0.2));
                              return true;
                           }

                           if (this.probes >= 3) {
                              return false;
                           }
                           break;
                        }
                     }
                  } else {
                     BlockPos var13 = var12.below();
                     if (!var2.getFluidState(var13).is(FluidTags.WATER)
                        && var2.getBlockState(var13).isFaceSturdy(var2, var13, Direction.UP)
                        && var2.getBlockState(var12.above(2)).isAir()) {
                        Vec3 var14 = Vec3.atBottomCenterOf(var12);
                        Direction var15 = null;

                        for (Direction var17 : Plane.HORIZONTAL.shuffledCopy(var3)) {
                           if (var2.getFluidState(var12.relative(var17)).is(FluidTags.WATER)) {
                              var15 = var17;
                              break;
                           }
                        }

                        if (var15 != null) {
                           if (this.reachable(var12)) {
                              this.mode = WildlifeForage.Mode.WADE;
                              this.food = var12;
                              this.stand = var14;
                              this.aim = var14.add((double)var15.getStepX() * 1.1, 0.72, (double)var15.getStepZ() * 1.1);
                              return true;
                           }

                           if (this.probes >= 3) {
                              return false;
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   private boolean findBrowse() {
      ServerLevel var1 = (ServerLevel)this.deer.level();
      RandomSource var2 = this.deer.getRandom();
      BlockPos var3 = this.deer.blockPosition();
      float var4 = this.deer.getEyeHeight();
      int var5 = Math.max(1, Mth.floor(var4 - 1.15F));
      int var6 = Math.max(var5, Mth.floor(var4 + 0.35F));
      int var7 = Math.max(2, Mth.ceil(this.deer.getBbHeight()));

      for (int var8 = 0; var8 < 36; var8++) {
         int var9 = var3.getX() + var2.nextInt(19) - 9;
         int var10 = var3.getZ() + var2.nextInt(19) - 9;
         if (var1.isLoaded(new BlockPos(var9, var3.getY(), var10))) {
            int var11 = var1.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var9, var10);
            if (Math.abs(var11 - var3.getY()) <= 4) {
               BlockPos var12 = new BlockPos(var9, var11, var10);
               if (var1.getBlockState(var12.below()).isFaceSturdy(var1, var12.below(), Direction.UP)) {
                  boolean var13 = true;

                  for (int var14 = 0; var14 < var7 && var13; var14++) {
                     var13 = var1.getBlockState(var12.above(var14)).getCollisionShape(var1, var12.above(var14)).isEmpty();
                  }

                  if (var13) {
                     BlockPos var19 = null;

                     for (Direction var16 : Plane.HORIZONTAL.shuffledCopy(var2)) {
                        for (int var17 = var6; var17 >= var5 && var19 == null; var17--) {
                           BlockPos var18 = var12.relative(var16).above(var17);
                           if (var1.getBlockState(var18).is(BlockTags.LEAVES)) {
                              var19 = var18;
                           }
                        }

                        if (var19 != null) {
                           break;
                        }
                     }

                     if (var19 != null) {
                        if (this.reachable(var12)) {
                           this.mode = WildlifeForage.Mode.BROWSE;
                           this.food = var19;
                           this.stand = Vec3.atBottomCenterOf(var12);
                           this.aim = Vec3.atCenterOf(var19);
                           return true;
                        }

                        if (this.probes >= 3) {
                           return false;
                        }
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   private boolean reachable(BlockPos var1) {
      if (var1.distSqr(this.deer.blockPosition()) < 2.0) {
         this.probe = null;
         return true;
      } else if (this.probes >= 3) {
         return false;
      } else {
         this.probes++;
         Path var2 = this.deer.getNavigation().createPath(var1, 0);
         if (var2 != null && var2.canReach()) {
            this.probe = var2;
            return true;
         } else {
            return false;
         }
      }
   }

   private static enum Mode {
      DRINK,
      WADE,
      BROWSE;
   }
}
