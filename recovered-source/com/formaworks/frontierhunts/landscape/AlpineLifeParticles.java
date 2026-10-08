package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.joml.Vector3f;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT},
   bus = Bus.MOD
)
public final class AlpineLifeParticles {
   static boolean life() {
      try {
         return (Boolean)HuntConfig.WILDLIFE_LIFE.get();
      } catch (IllegalStateException var1) {
         return true;
      }
   }

   @SubscribeEvent
   public static void register(RegisterParticleProvidersEvent var0) {
      var0.registerSpriteSet(
         (ParticleType)AlpineRegistration.FLUSH_BIRD.get(),
         var0x -> (var1, var2, var3, var5, var7, var9, var11, var13) -> life() ? new AlpineLifeParticles.Flush(var2, var3, var5, var7, var0x) : null
      );
      var0.registerSpriteSet(
         (ParticleType)AlpineRegistration.SOARING_HAWK.get(),
         var0x -> (var1, var2, var3, var5, var7, var9, var11, var13) -> life()
                  ? new AlpineLifeParticles.Soar(var2, var3, var5, var7, var9, var11, var0x)
                  : null
      );
      var0.registerSpriteSet(
         (ParticleType)AlpineRegistration.RAVEN.get(),
         var0x -> (var1, var2, var3, var5, var7, var9, var11, var13) -> life()
                  ? new AlpineLifeParticles.Cross(var2, var3, var5, var7, var9, var13, var0x)
                  : null
      );
      var0.registerSpriteSet(
         (ParticleType)AlpineRegistration.RISING_FISH.get(),
         var0x -> (var1, var2, var3, var5, var7, var9, var11, var13) -> life()
                  ? new AlpineLifeParticles.Rise(var2, var3, var5, var7, var9, var13, var0x)
                  : null
      );
      var0.registerSpriteSet(
         (ParticleType)AlpineRegistration.BREATH.get(),
         var0x -> (var1, var2, var3, var5, var7, var9, var11, var13) -> new AlpineLifeParticles.Breath(var2, var3, var5, var7, var9, var11, var13, var0x)
      );
      var0.registerSpriteSet(
         (ParticleType)AlpineRegistration.SONGBIRD.get(),
         var0x -> (var1, var2, var3, var5, var7, var9, var11, var13) -> life()
                  ? new AlpineLifeParticles.Songbird(var2, var3, var5, var7, (int)var9, var11, var13, var0x)
                  : null
      );
      var0.registerSpriteSet(
         (ParticleType)AlpineRegistration.FLOCK_BIRD.get(),
         var0x -> (var1, var2, var3, var5, var7, var9, var11, var13) -> life()
                  ? new AlpineLifeParticles.Flock(var2, var3, var5, var7, var9, var11, var13, var0x)
                  : null
      );
   }

   private AlpineLifeParticles() {
   }

   static final class Breath extends TextureSheetParticle {
      private final float peak;

      Breath(ClientLevel var1, double var2, double var4, double var6, double var8, double var10, double var12, SpriteSet var14) {
         super(var1, var2, var4, var6);
         this.hasPhysics = false;
         this.gravity = -0.002F;
         this.friction = 0.9F;
         this.xd = var8;
         this.yd = var10;
         this.zd = var12;
         this.lifetime = 22 + this.random.nextInt(14);
         this.quadSize = 0.05F + this.random.nextFloat() * 0.03F;
         this.peak = 0.3F + this.random.nextFloat() * 0.12F;
         this.alpha = 0.0F;
         this.roll = this.oRoll = this.random.nextFloat() * 6.28F;
         this.pickSprite(var14);
      }

      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
      }

      public void tick() {
         super.tick();
         this.quadSize *= 1.06F;
         this.oRoll = this.roll;
         this.roll += 0.02F;
         float var1 = (float)this.age / (float)this.lifetime;
         this.alpha = this.peak * Math.min(1.0F, var1 * 6.0F) * (1.0F - var1);
      }
   }

   static final class Cross extends TextureSheetParticle {
      private final SpriteSet sprites;

      Cross(ClientLevel var1, double var2, double var4, double var6, double var8, double var10, SpriteSet var12) {
         super(var1, var2, var4, var6);
         this.sprites = var12;
         this.hasPhysics = false;
         this.gravity = 0.0F;
         this.friction = 1.0F;
         this.xd = var8;
         this.zd = var10;
         this.yd = 0.0;
         this.lifetime = 500 + this.random.nextInt(300);
         this.quadSize = 0.5F + this.random.nextFloat() * 0.14F;
         this.setSprite(var12.get(0, 1));
      }

      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
      }

      public void tick() {
         super.tick();
         this.yd = Math.sin((double)this.age * 0.07) * 0.01;
         this.setSprite(this.sprites.get(this.age / 7 % 2, 1));
      }
   }

   static final class Flock extends TextureSheetParticle {
      private final SpriteSet sprites;
      private final double phase;

      Flock(ClientLevel var1, double var2, double var4, double var6, double var8, double var10, double var12, SpriteSet var14) {
         super(var1, var2, var4, var6);
         this.sprites = var14;
         this.phase = var10;
         this.hasPhysics = false;
         this.gravity = 0.0F;
         this.friction = 1.0F;
         this.xd = var8;
         this.zd = var12;
         this.yd = 0.0;
         this.lifetime = 240 + this.random.nextInt(60);
         this.quadSize = 0.14F + this.random.nextFloat() * 0.05F;
         this.setSprite(var14.get(0, 2));
      }

      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
      }

      public void tick() {
         super.tick();
         double var1 = (double)this.age * 0.18 + this.phase;
         this.yd = Math.sin(var1) * 0.045;
         this.setSprite(this.sprites.get(Math.sin(var1) > -0.2 ? this.age / 2 % 3 : 1, 2));
      }
   }

   static final class Flush extends TextureSheetParticle {
      private final SpriteSet sprites;
      private final int beat;

      Flush(ClientLevel var1, double var2, double var4, double var6, SpriteSet var8) {
         super(var1, var2, var4, var6);
         this.sprites = var8;
         this.hasPhysics = false;
         this.gravity = 0.0F;
         this.friction = 1.0F;
         double var9 = this.random.nextDouble() * Math.PI * 2.0;
         double var11 = 0.22 + this.random.nextDouble() * 0.18;
         this.xd = Math.cos(var9) * var11;
         this.zd = Math.sin(var9) * var11;
         this.yd = 0.18 + this.random.nextDouble() * 0.2;
         this.lifetime = 45 + this.random.nextInt(30);
         this.quadSize = 0.16F + this.random.nextFloat() * 0.07F;
         this.beat = 1 + this.random.nextInt(2);
         this.setSprite(var8.get(0, 2));
      }

      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
      }

      public void tick() {
         super.tick();
         this.yd = this.yd * 0.96 + 0.012;
         this.xd *= 1.01;
         this.zd *= 1.01;
         double var1 = Math.cos(0.05);
         double var3 = Math.sin(0.05 * (double)(this.age % 40 < 20 ? 1 : -1));
         double var5 = this.xd * var1 - this.zd * var3;
         double var7 = this.xd * var3 + this.zd * var1;
         this.xd = var5;
         this.zd = var7;
         this.setSprite(this.sprites.get(this.age / this.beat % 3, 2));
      }
   }

   static final class Rise extends TextureSheetParticle {
      private final SpriteSet sprites;
      private final double water;

      Rise(ClientLevel var1, double var2, double var4, double var6, double var8, double var10, SpriteSet var12) {
         super(var1, var2, var4, var6);
         this.sprites = var12;
         this.hasPhysics = false;
         this.gravity = 0.9F;
         this.friction = 1.0F;
         this.water = var4;
         this.xd = var8;
         this.zd = var10;
         this.yd = 0.32 + this.random.nextDouble() * 0.1;
         this.lifetime = 40;
         this.quadSize = 0.22F + this.random.nextFloat() * 0.08F;
         this.setSprite(var12.get(0, 1));
      }

      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
      }

      public void tick() {
         super.tick();
         this.setSprite(this.sprites.get(this.yd > 0.0 ? 0 : 1, 1));
         if (this.yd < 0.0 && this.y <= this.water) {
            for (int var1 = 0; var1 < 8; var1++) {
               this.level
                  .addParticle(
                     ParticleTypes.SPLASH,
                     this.x + (this.random.nextDouble() - 0.5) * 0.4,
                     this.water + 0.05,
                     this.z + (this.random.nextDouble() - 0.5) * 0.4,
                     0.0,
                     0.1,
                     0.0
                  );
            }

            this.level.addParticle(ParticleTypes.BUBBLE_POP, this.x, this.water + 0.05, this.z, 0.0, 0.0, 0.0);
            this.remove();
         }
      }
   }

   static final class Soar extends TextureSheetParticle {
      private final double cx;
      private final double cz;
      private final double radius;
      private final double speed;
      private double angle;

      Soar(ClientLevel var1, double var2, double var4, double var6, double var8, double var10, SpriteSet var12) {
         super(var1, var2, var4, var6);
         this.hasPhysics = false;
         this.gravity = 0.0F;
         this.cx = var2;
         this.cz = var6;
         this.radius = Math.max(8.0, var8);
         this.speed = var10 == 0.0 ? 0.012 : var10;
         this.angle = this.random.nextDouble() * Math.PI * 2.0;
         this.lifetime = 900 + this.random.nextInt(500);
         this.quadSize = 0.85F + this.random.nextFloat() * 0.25F;
         this.pickSprite(var12);
         this.tickPosition();
         this.xo = this.x;
         this.yo = this.y;
         this.zo = this.z;
      }

      private void tickPosition() {
         this.x = this.cx + Math.cos(this.angle) * this.radius;
         this.z = this.cz + Math.sin(this.angle) * this.radius;
         this.y = this.y + Math.sin(this.angle * 3.0) * 0.01;
      }

      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
      }

      public void tick() {
         this.xo = this.x;
         this.yo = this.y;
         this.zo = this.z;
         if (this.age++ >= this.lifetime) {
            this.remove();
         } else {
            this.angle = this.angle + this.speed;
            this.tickPosition();
            this.y += 0.004;
         }
      }
   }

   static final class Songbird extends TextureSheetParticle {
      static final List<WeakReference<AlpineLifeParticles.Songbird>> ALIVE = new ArrayList<>();
      private static final int LANDING = 0;
      private static final int PERCHED = 1;
      private static final int FLYING = 2;
      private final SpriteSet sprites;
      private final int species;
      private final boolean ground;
      private final double perchX;
      private final double perchY;
      private final double perchZ;
      private double yaw;
      private double groundY;
      private int state = 0;
      private int pose;
      private int poseTicks;
      private int stay;
      private boolean flip;

      static int alive() {
         ALIVE.removeIf(var0 -> {
            AlpineLifeParticles.Songbird var1 = var0.get();
            return var1 == null || !var1.isAlive();
         });
         return ALIVE.size();
      }

      Songbird(ClientLevel var1, double var2, double var4, double var6, int var8, double var9, double var11, SpriteSet var13) {
         super(var1, var2, var4, var6);
         this.sprites = var13;
         this.species = Math.floorMod(var8, 3);
         this.ground = var8 < 10;
         this.hasPhysics = false;
         this.gravity = 0.0F;
         this.friction = 1.0F;
         this.perchX = var2;
         this.perchY = var4;
         this.perchZ = var6;
         this.groundY = var4;
         double var16 = 9.0 + this.random.nextDouble() * 5.0;
         double var18 = 5.0 + this.random.nextDouble() * 4.0;
         this.x = var2 + Math.cos(var9) * var16;
         this.y = var4 + var18;
         this.z = var6 + Math.sin(var9) * var16;
         this.xo = this.x;
         this.yo = this.y;
         this.zo = this.z;
         this.yaw = Math.atan2(var6 - this.z, var2 - this.x);
         this.stay = (int)Math.max(200.0, var11);
         this.lifetime = this.stay + 400;
         this.quadSize = 0.16F + this.random.nextFloat() * 0.03F;
         this.setSprite(var13.get(9, 11));
         ALIVE.add(new WeakReference<>(this));
      }

      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
      }

      public void tick() {
         this.xo = this.x;
         this.yo = this.y;
         this.zo = this.z;
         if (this.age++ >= this.lifetime) {
            this.remove();
         } else {
            LocalPlayer var1 = Minecraft.getInstance().player;
            switch (this.state) {
               case 0:
                  double var13 = this.perchX - this.x;
                  double var14 = this.perchY - this.y;
                  double var6 = this.perchZ - this.z;
                  double var8 = Math.sqrt(var13 * var13 + var14 * var14 + var6 * var6);
                  if (var8 < 0.25) {
                     this.x = this.perchX;
                     this.y = this.perchY;
                     this.z = this.perchZ;
                     this.state = 1;
                     this.pose = 0;
                     this.poseTicks = 20;
                  } else {
                     double var10 = Math.min(var8, 0.3);
                     this.x += var13 / var8 * var10;
                     this.y = this.y + var14 / var8 * var10 + (var8 > 2.0 ? Math.sin((double)this.age * 0.9) * 0.02 : 0.0);
                     this.z += var6 / var8 * var10;
                     this.yaw = Math.atan2(var6, var13);
                     this.setSprite(this.sprites.get(9 + this.age / 2 % 3, 11));
                     if (var1 != null && var1.distanceToSqr(this.perchX, this.perchY, this.perchZ) < 36.0) {
                        this.flee(var1);
                     }
                  }
                  break;
               case 1:
                  if (var1 != null) {
                     double var2 = !var1.isSprinting() && !(var1.getDeltaMovement().horizontalDistanceSqr() > 0.08) ? 7.0 : 11.0;
                     if (var1.isCrouching()) {
                        var2 = 3.5;
                     }

                     if (var1.distanceToSqr(this.x, this.y, this.z) < var2 * var2) {
                        this.flee(var1);
                        break;
                     }
                  }

                  if (this.age > this.stay) {
                     this.flee(null);
                  } else {
                     if (this.y > this.groundY) {
                        this.y = Math.max(this.groundY, this.y + this.yd);
                        this.yd -= 0.04;
                        this.x = this.x + this.xd;
                        this.z = this.z + this.zd;
                        if (this.y <= this.groundY) {
                           this.xd = this.zd = 0.0;
                        }
                     }

                     if (--this.poseTicks <= 0) {
                        double var12 = this.random.nextDouble();
                        if (var12 < 0.35) {
                           this.pose = 1;
                           this.poseTicks = 6 + this.random.nextInt(10);
                        } else if (var12 < 0.6) {
                           this.pose = 2;
                           this.poseTicks = 10 + this.random.nextInt(25);
                        } else if (var12 < 0.78 && this.y <= this.groundY && this.perchIsGround()) {
                           this.yd = 0.16;
                           double var4 = this.yaw + (this.random.nextDouble() - 0.5) * 1.2;
                           this.xd = Math.cos(var4) * 0.07;
                           this.zd = Math.sin(var4) * 0.07;
                           this.y += 0.01;
                           this.pose = 0;
                           this.poseTicks = 8;
                        } else if (var12 < 0.88) {
                           this.yaw += Math.PI;
                           this.pose = 0;
                           this.poseTicks = 12 + this.random.nextInt(20);
                        } else {
                           this.pose = 0;
                           this.poseTicks = 15 + this.random.nextInt(30);
                        }
                     }

                     this.setSprite(this.sprites.get(this.species * 3 + this.pose, 11));
                  }
                  break;
               default:
                  this.x = this.x + this.xd;
                  this.y = this.y + this.yd;
                  this.z = this.z + this.zd;
                  this.yd = Math.max(0.04, this.yd * 0.93);
                  this.y = this.y + Math.sin((double)this.age * 0.5) * 0.03;
                  this.setSprite(this.sprites.get(9 + this.age / 2 % 3, 11));
                  if (this.age > this.lifetime - 2) {
                     this.alpha = 0.0F;
                  }
            }
         }
      }

      private boolean perchIsGround() {
         return this.ground;
      }

      private void flee(Player var1) {
         double var2;
         if (var1 != null) {
            var2 = Math.atan2(this.z - var1.getZ(), this.x - var1.getX()) + (this.random.nextDouble() - 0.5) * 0.9;
         } else {
            var2 = this.random.nextDouble() * Math.PI * 2.0;
         }

         double var4 = 0.3 + this.random.nextDouble() * 0.1;
         this.xd = Math.cos(var2) * var4;
         this.zd = Math.sin(var2) * var4;
         this.yd = 0.22 + this.random.nextDouble() * 0.12;
         this.yaw = var2;
         this.state = 2;
         this.lifetime = this.age + 70 + this.random.nextInt(30);
         if (var1 != null && this.random.nextFloat() < 0.7F) {
            SoundEvent var6 = (SoundEvent)BuiltInRegistries.SOUND_EVENT.get(FrontierHunts.id("amb_bird_chirp"));
            if (var6 != null && this.random.nextFloat() < 0.6F) {
               this.level.playLocalSound(this.x, this.y, this.z, var6, SoundSource.AMBIENT, 1.2F, 1.05F + this.random.nextFloat() * 0.2F, false);
            }
         }
      }

      public void render(VertexConsumer var1, Camera var2, float var3) {
         Vector3f var4 = var2.getLeftVector();
         this.flip = Math.cos(this.yaw) * (double)var4.x() + Math.sin(this.yaw) * (double)var4.z() > 0.0;
         super.render(var1, var2, var3);
      }

      protected float getU0() {
         return this.flip ? super.getU1() : super.getU0();
      }

      protected float getU1() {
         return this.flip ? super.getU0() : super.getU1();
      }
   }
}
