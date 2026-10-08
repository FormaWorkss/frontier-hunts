package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntNetwork;
import com.formaworks.frontierhunts.Wilderness;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public final class WindPuffParticle extends TextureSheetParticle {
   private WindPuffParticle(ClientLevel var1, double var2, double var4, double var6, SpriteSet var8) {
      super(var1, var2, var4, var6, 0.0, 0.0, 0.0);
      this.pickSprite(var8);
      this.lifetime = 90 + this.random.nextInt(70);
      this.gravity = 0.0F;
      this.friction = 1.0F;
      this.hasPhysics = false;
      this.quadSize = 0.055F + this.random.nextFloat() * 0.045F;
      this.rCol = 0.95F;
      this.gCol = 0.95F;
      this.bCol = 0.93F;
      this.alpha = 0.48F;
      this.xd = (this.random.nextDouble() - 0.5) * 0.006;
      this.yd = (this.random.nextDouble() - 0.5) * 0.004;
      this.zd = (this.random.nextDouble() - 0.5) * 0.006;
   }

   public void tick() {
      this.xo = this.x;
      this.yo = this.y;
      this.zo = this.z;
      if (this.age++ >= this.lifetime) {
         this.remove();
      } else {
         HuntNetwork.Snapshot var1 = FrontierClient.state;
         double var2 = var1 == null ? 0.0 : (double)var1.windEast();
         double var4 = var1 == null ? 0.0 : (double)var1.windSouth();
         double var6 = Wilderness.thermal(this.level.getDayTime(), this.level.isRaining(), (float)this.level.getSkyDarken() / 15.0F);
         this.xd = Mth.lerp(0.1, this.xd, var2 * 0.004);
         this.zd = Mth.lerp(0.1, this.zd, var4 * 0.004);
         this.yd = Mth.lerp(0.06, this.yd, var6 * 0.0095);
         this.move(this.xd, this.yd, this.zd);
         float var8 = (float)this.age / (float)this.lifetime;
         this.alpha = 0.48F * (1.0F - var8 * var8);
         this.quadSize += 0.0016F;
      }
   }

   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
   }

   public static record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
      public Particle createParticle(SimpleParticleType var1, ClientLevel var2, double var3, double var5, double var7, double var9, double var11, double var13) {
         return new WindPuffParticle(var2, var3, var5, var7, this.sprites);
      }
   }
}
