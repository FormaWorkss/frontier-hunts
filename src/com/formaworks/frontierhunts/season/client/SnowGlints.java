package com.formaworks.frontierhunts.season.client;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * [1.1.2] Sun glints on the Frontier snowpack. On a clear day the ice crystals on the snow catch the sun: tiny
 * star-points wink on and off across the snow in front of you, thickest where the surface mirrors the sun into your eye
 * (the band toward the sun, and right under a low sun), so they shift as you move and turn - the way fresh snow
 * sparkles. Off at night, under cloud and in falling snow, with the Minecraft snow style and with minimal particles.
 */
@EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT)
public final class SnowGlints {
   private SnowGlints() {
   }

   private static int tick;

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post event) {
      try {
         glints();
      } catch (RuntimeException e) {
         // [1.1.3] cosmetic: never let a glint take the game down
         if (!warned) {
            warned = true;
            org.slf4j.LoggerFactory.getLogger("frontierhunts").warn("Snow glints switched off after an error", e);
         }
      }
   }

   private static boolean warned;

   /**
    * [1.1.3] the glint sprite. It lives in the particle atlas, which belongs to the particle engine (not to the model
    * manager - asking Minecraft.getTextureAtlas for it crashed 1.1.2), so it is looked up through the texture manager.
    */
   static TextureAtlasSprite sprite() {
      AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
      if (!(t instanceof TextureAtlas atlas)) {
         return null;
      }
      TextureAtlasSprite s = atlas.getSprite(FrontierHunts.id("snow_glint"));
      return s == null || s.contents().name().equals(MissingTextureAtlasSprite.getLocation()) ? null : s;
   }

   private static void glints() {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null || mc.player == null || mc.isPaused()) {
         return;
      }
      if ((++tick & 31) == 0) {
         SnowLook.apply(); // picks up a shader pack switched on or off (the snow's baked light follows)
      }
      if (!SnowLook.frontier() || mc.options.particles().get().getId() == 2 || level.isRaining()) {
         return;
      }
      float sunAngle = level.getSunAngle(1.0F);
      Vec3 sun = new Vec3(-Mth.sin(sunAngle), Mth.cos(sunAngle), 0.0);
      if (sun.y < 0.06) {
         return; // night, or the sun on the horizon
      }
      Camera cam = mc.gameRenderer.getMainCamera();
      Vec3 eye = cam.getPosition();
      Vec3 look = Vec3.directionFromRotation(cam.getXRot(), cam.getYRot());
      TextureAtlasSprite sprite = sprite();
      if (sprite == null) {
         return;
      }
      RandomSource r = level.random;
      int tries = mc.options.particles().get().getId() == 0 ? 40 : 18;
      int made = 0;
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int i = 0; i < tries && made < 8; i++) {
         // a spot on the ground in front of you, 2.5 to 18 blocks out
         double dist = 2.5 + Math.pow(r.nextDouble(), 0.7) * 15.5;
         double yaw = Math.atan2(look.z, look.x) + (r.nextDouble() - 0.5) * 1.6;
         double x = eye.x + Math.cos(yaw) * dist, z = eye.z + Math.sin(yaw) * dist;
         int bx = Mth.floor(x), bz = Mth.floor(z);
         if (!level.hasChunk(bx >> 4, bz >> 4)) {
            continue;
         }
         int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
         BlockState s = level.getBlockState(m.set(bx, top - 1, bz));
         double y;
         if (s.getBlock() instanceof SnowLayerBlock) {
            y = top - 1 + s.getValue(SnowLayerBlock.LAYERS) / 8.0;
         } else {
            s = level.getBlockState(m.set(bx, top, bz));
            if (!(s.getBlock() instanceof SnowLayerBlock)) {
               continue;
            }
            y = top + s.getValue(SnowLayerBlock.LAYERS) / 8.0;
         }
         // how well this bit of snow mirrors the sun to the eye: a crystal lies at a random small tilt off the surface
         Vec3 toEye = new Vec3(eye.x - x, eye.y - y, eye.z - z).normalize();
         Vec3 half = toEye.add(sun).normalize();
         double tiltX = (r.nextDouble() - 0.5) * 0.9, tiltZ = (r.nextDouble() - 0.5) * 0.9;
         Vec3 n = new Vec3(tiltX, 1.0, tiltZ).normalize();
         double mirror = Math.max(0.0, n.dot(half));
         if (r.nextDouble() > Math.pow(mirror, 24.0) * 1.6) {
            continue;
         }
         mc.particleEngine.add(new Glint(level, x, y + 0.02, z, dist, sprite));
         made++;
      }
   }

   /** a glint: a small star that flashes up and is gone in a few frames */
   static final class Glint extends TextureSheetParticle {
      private final float peak;

      Glint(ClientLevel level, double x, double y, double z, double dist, TextureAtlasSprite sprite) {
         super(level, x, y, z);
         this.setSprite(sprite);
         this.lifetime = 3 + this.random.nextInt(5);
         // a constant size on screen whatever the distance, so far glints stay pin-points
         this.peak = (float)(0.018 + dist * 0.0042) * (0.7F + this.random.nextFloat() * 0.6F);
         this.quadSize = 0.0F;
         this.hasPhysics = false;
         this.gravity = 0.0F;
         this.xd = this.yd = this.zd = 0.0;
         this.rCol = 1.0F;
         this.gCol = 1.0F;
         this.bCol = 0.97F + this.random.nextFloat() * 0.03F;
         this.alpha = 0.0F;
      }

      @Override
      public void tick() {
         super.tick();
         float t = (float)this.age / this.lifetime;
         float k = Mth.sin(t * (float)Math.PI);
         this.quadSize = this.peak * k;
         this.alpha = Math.min(1.0F, k * 1.4F);
      }

      @Override
      public int getLightColor(float partialTick) {
         return 0xF000F0;
      }

      @Override
      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
      }
   }
}
