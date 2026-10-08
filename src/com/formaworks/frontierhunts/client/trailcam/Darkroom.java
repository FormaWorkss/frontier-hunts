package com.formaworks.frontierhunts.client.trailcam;

import com.formaworks.frontierhunts.client.TrailcamBridge;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.trailcam.TrailcamConfig;
import com.formaworks.frontierhunts.trailcam.TrailcamScene;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Develops trail camera photos on the client by rendering the real world from the camera's lens.
 *
 * While a {@link DarkroomHost} screen covers the window, each frame of a job: sets the level's clock and weather to
 * the recorded moment, puts client-only stand-ins of the recorded animals/players (exact type, synced data, pose,
 * gear) into the level, swaps the render camera to the lens pose and hides every other living thing. After a few
 * warm-up frames (so chunk sections, shader history and animators settle) the finished level image is read back at
 * {@code AFTER_LEVEL} (after Iris's final pass, before hand and GUI) and handed to {@link TrailcamLook} on a worker.
 * Everything is restored in {@code RenderFrameEvent.Post}, so client ticks never see the swapped state.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class Darkroom {
   private static final Logger LOG = LoggerFactory.getLogger(Darkroom.class);
   private static final Set<Entity> ACTORS = Collections.newSetFromMap(new IdentityHashMap<>());
   private static final AtomicInteger WORK = new AtomicInteger();
   private static Job job;
   private static boolean frame;
   private static Entity savedCamera;
   private static long savedGame;
   private static long savedDay;
   private static float savedRainO;
   private static float savedRain;
   private static float savedThunderO;
   private static float savedThunder;
   private static Vec3 lastLens;
   private static long lastJobEnd;
   private static Method irisInUse;
   private static boolean irisLooked;
   /** frames to wait before scanning the queue again after a scan found nothing developable */
   private static int idle;

   private Darkroom() {
   }

   static final class Job {
      final Photo photo;
      final CompoundTag scene;
      final Vec3 lens;
      final float yaw;
      final float pitch;
      final boolean ir;
      final long game;
      final long day;
      final float rain;
      final float thunder;
      final boolean uplink;
      final List<LivingEntity> actors = new ArrayList<>();
      final List<CompoundTag> tags = new ArrayList<>();
      final List<CompletableFuture<?>> waits = new ArrayList<>();
      final long started = Util.getMillis();
      Marker eye;
      int frames;
      int minFrames;
      int maxFrames;
      int blankRetries;
      boolean built;
      boolean prerolled;
      boolean captured;

      Job(Photo photo, boolean uplink) {
         this.photo = photo;
         this.scene = photo.scene;
         this.lens = lensOf(photo.scene);
         this.yaw = photo.scene.getFloat("yaw");
         this.pitch = photo.scene.getFloat("pitch");
         this.ir = photo.scene.getBoolean("ir");
         this.game = photo.scene.getLong("t");
         long d = photo.scene.getLong("d");
         // infrared frames are lit by the camera's own flash: render the ground as albedo (midday, no thunder dark) and
         // let the look apply the flash fall-off, instead of rendering the moonlit scene
         this.day = this.ir ? d - Math.floorMod(d, 24000L) + 6000L : d;
         this.rain = Math.max(0.0F, Math.min(1.0F, photo.scene.getFloat("rain")));
         this.thunder = this.ir ? 0.0F : Math.max(0.0F, Math.min(1.0F, photo.scene.getFloat("thunder")));
         this.uplink = uplink;
      }

      boolean waiting() {
         if (Util.getMillis() - this.started > 5000L) {
            return false;
         }
         for (CompletableFuture<?> f : this.waits) {
            if (!f.isDone()) {
               return true;
            }
         }
         return false;
      }
   }

   // ------------------------------------------------------------------------------------------------ state queries

   public static boolean active() {
      return frame && job != null;
   }

   static boolean busy() {
      return job != null || WORK.get() > 0;
   }

   public static Vec3 lensPos() {
      return job.lens;
   }

   public static float lensYaw() {
      return job.yaw;
   }

   public static float lensPitch() {
      return job.pitch;
   }

   /** Every living thing except the recorded subjects is left out of a developing frame (and out of its shadows). */
   public static boolean hides(Entity e) {
      return frame && e instanceof LivingEntity && !(e instanceof ArmorStand) && !ACTORS.contains(e);
   }

   static Vec3 lensOf(CompoundTag scene) {
      return new Vec3(scene.getDouble("lx"), scene.getDouble("ly"), scene.getDouble("lz"));
   }

   // ------------------------------------------------------------------------------------------------ frame lifecycle

   @SubscribeEvent(priority = EventPriority.LOW)
   public static void pre(RenderFrameEvent.Pre event) {
      Minecraft mc = Minecraft.getInstance();
      if (frame) {
         endFrame(mc);
      }
      if (!(mc.screen instanceof DarkroomHost host)
         || !host.develops() // [phone]
         || mc.level == null
         || mc.player == null
         || mc.getOverlay() != null
         || mc.getWindow().getWidth() < 16
         || mc.getWindow().getHeight() < 16) {
         if (job != null) {
            abort();
         }
         return;
      }
      if (job == null) {
         if (WORK.get() >= 2 || idle > 0 && idle-- > 0) {
            return;
         }
         Photo next = TrailcamClient.nextJob(p -> loadable(mc, p), host);
         if (next == null) {
            idle = 10;
            return;
         }
         job = start(mc, next);
         if (job == null) {
            return;
         }
      }
      long limit = job.uplink ? 25000L : 12000L;
      if (Util.getMillis() - job.started > limit) {
         Job j = job;
         dropJob(mc);
         TrailcamClient.failed(j.photo, "The camera's ground did not finish loading");
         return;
      }
      if (job.waiting()) {
         return;
      }
      try {
         beginFrame(mc);
      } catch (Throwable t) {
         LOG.error("Trail camera photo could not be set up for developing", t);
         Job j = job;
         if (frame) {
            endFrame(mc);
         }
         dropJob(mc);
         TrailcamClient.failed(j.photo, "Could not develop this frame");
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void post(RenderFrameEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (frame) {
         endFrame(mc);
         if (job != null && job.captured) {
            dropJob(mc);
         }
      }
   }

   /** Can this photo be developed with what the client has loaded right now? */
   private static boolean loadable(Minecraft mc, Photo p) {
      if (p.scene == null) {
         return false;
      }
      if (!p.scene.getString("dim").equals(mc.level.dimension().location().toString())) {
         TrailcamClient.failed(p, "Taken in another dimension");
         return false;
      }
      Vec3 lens = lensOf(p.scene);
      boolean viaUplink = TrailcamClient.uplinkActive();
      int r = viaUplink ? 3 : Math.max(2, Math.min(4, mc.options.getEffectiveRenderDistance() - 1));
      int cx = BlockPos.containing(lens).getX() >> 4;
      int cz = BlockPos.containing(lens).getZ() >> 4;
      for (int dz = -r; dz <= r; dz++) {
         for (int dx = -r; dx <= r; dx++) {
            if (!mc.level.getChunkSource().hasChunk(cx + dx, cz + dz)) {
               return false;
            }
         }
      }
      return true;
   }

   private static Job start(Minecraft mc, Photo p) {
      if (!p.scene.getString("dim").equals(mc.level.dimension().location().toString())) {
         TrailcamClient.failed(p, "Taken in another dimension");
         return null;
      }
      Vec3 uplinkLens = TrailcamClient.uplinkLens();
      Job j = new Job(p, uplinkLens != null && TrailcamClient.near(p, uplinkLens));
      boolean shaders = shadersInUse();
      boolean fresh = lastLens == null || lastLens.distanceToSqr(j.lens) > 1.0 || Util.getMillis() - lastJobEnd > 2500L;
      j.minFrames = shaders ? (fresh ? 16 : 9) : (fresh ? 5 : 3);
      j.maxFrames = j.uplink ? 300 : 120;
      j.eye = new Marker(EntityType.MARKER, mc.level);
      j.eye.moveTo(j.lens.x, j.lens.y, j.lens.z, j.yaw, j.pitch);
      p.state = Photo.State.DEVELOPING;
      TrailcamClient.version++;
      return j;
   }

   private static void beginFrame(Minecraft mc) {
      ClientLevel level = mc.level;
      ClientLevel.ClientLevelData data = level.getLevelData();
      savedGame = data.getGameTime();
      savedDay = data.getDayTime();
      savedRainO = level.oRainLevel;
      savedRain = level.rainLevel;
      savedThunderO = level.oThunderLevel;
      savedThunder = level.thunderLevel;
      savedCamera = mc.getCameraEntity();
      frame = true;
      data.setGameTime(job.game);
      data.setDayTime(job.day);
      level.oRainLevel = job.rain;
      level.rainLevel = job.rain;
      level.oThunderLevel = job.thunder;
      level.thunderLevel = job.thunder;
      mc.gameRenderer.lightTexture().tick();
      if (!job.built) {
         job.built = true;
         DarkroomActors.build(mc, level, job);
         for (LivingEntity a : job.actors) {
            level.addEntity(a);
            ACTORS.add(a);
         }
      }
      for (int i = 0; i < job.actors.size(); i++) {
         DarkroomActors.hold(job.actors.get(i), job.tags.get(i));
      }
      if (!job.prerolled) {
         job.prerolled = true;
         for (LivingEntity a : job.actors) {
            if (a instanceof Whitetail w) {
               try {
                  TrailcamBridge.preroll(w);
               } catch (RuntimeException e) {
                  LOG.debug("Trail camera: deer pose pre-roll failed", e);
               }
            }
         }
      }
      job.eye.moveTo(job.lens.x, job.lens.y, job.lens.z, job.yaw, job.pitch);
      mc.setCameraEntity(job.eye);
      job.frames++;
   }

   private static void endFrame(Minecraft mc) {
      frame = false;
      ClientLevel level = mc.level;
      if (level != null) {
         ClientLevel.ClientLevelData data = level.getLevelData();
         data.setGameTime(savedGame);
         data.setDayTime(savedDay);
         level.oRainLevel = savedRainO;
         level.rainLevel = savedRain;
         level.oThunderLevel = savedThunderO;
         level.thunderLevel = savedThunder;
      }
      Entity back = savedCamera != null && !savedCamera.isRemoved() ? savedCamera : mc.player;
      savedCamera = null;
      if (back != null) {
         mc.setCameraEntity(back);
         if (level != null) {
            // re-aim the shared camera at the player so the sound listener and eye-height smoothing never use the lens
            mc.gameRenderer
               .getMainCamera()
               .setup(level, back, !mc.options.getCameraType().isFirstPerson(), mc.options.getCameraType().isMirrored(), 1.0F);
         }
      }
      mc.gameRenderer.lightTexture().tick();
   }

   /** Removes the stand-ins and forgets the job. */
   private static void dropJob(Minecraft mc) {
      if (job == null) {
         return;
      }
      for (LivingEntity a : job.actors) {
         try {
            if (mc.level != null && mc.level.getEntity(a.getId()) == a) {
               mc.level.removeEntity(a.getId(), Entity.RemovalReason.DISCARDED);
            } else if (!a.isRemoved()) {
               a.setRemoved(Entity.RemovalReason.DISCARDED);
            }
         } catch (RuntimeException e) {
            LOG.debug("Trail camera: stand-in removal", e);
         }
      }
      ACTORS.clear();
      lastLens = job.lens;
      lastJobEnd = Util.getMillis();
      job = null;
   }

   /** Screen closed, world left or something went wrong: undo everything and put the photo back in line. */
   public static void abort() {
      Minecraft mc = Minecraft.getInstance();
      if (frame) {
         endFrame(mc);
      }
      if (job != null) {
         Job j = job;
         dropJob(mc);
         if (!j.captured) {
            TrailcamClient.requeue(j.photo);
         }
      }
   }

   // ------------------------------------------------------------------------------------------------ render hooks

   @SubscribeEvent
   public static void fov(ViewportEvent.ComputeFov event) {
      if (active()) {
         event.setFOV(renderFov(Minecraft.getInstance()));
      }
   }

   @SubscribeEvent
   public static void hand(RenderHandEvent event) {
      if (active()) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void nameTag(RenderNameTagEvent event) {
      if (active()) {
         event.setCanRender(TriState.FALSE);
      }
   }

   @SubscribeEvent
   public static void fog(ViewportEvent.RenderFog event) {
      // over the uplink only ~50 blocks of ground stream in: fade the edge of the world out like a hazy morning
      if (active() && job.uplink && event.getFarPlaneDistance() > 56.0F) {
         event.setFarPlaneDistance(56.0F);
         event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), 24.0F));
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void freeze(EntityTickEvent.Pre event) {
      // the stand-ins are props: they never tick (no AI, no pushing the player, no sounds)
      if (!ACTORS.isEmpty() && event.getEntity().level().isClientSide && ACTORS.contains(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   /** Vertical FOV for the whole window so its centred 16:9 crop has the trail camera's lens angle. */
   private static double renderFov(Minecraft mc) {
      int w = Math.max(1, mc.getWindow().getWidth());
      int h = Math.max(1, mc.getWindow().getHeight());
      double tanV = Math.tan(Math.toRadians(TrailcamScene.FOV_V / 2.0));
      double aspect = (double)w / h;
      if (aspect < 16.0 / 9.0) {
         double cropH = w * 9.0 / 16.0;
         tanV *= h / cropH;
      }
      return Math.toDegrees(2.0 * Math.atan(tanV));
   }

   @SubscribeEvent
   public static void stage(RenderLevelStageEvent event) {
      if (!active() || job.captured || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (job.frames < job.minFrames) {
         return;
      }
      if (!mc.levelRenderer.hasRenderedAllSections() && job.frames < job.maxFrames) {
         return;
      }
      try {
         capture(mc);
      } catch (Throwable t) {
         LOG.error("Trail camera photo could not be read back", t);
         job.captured = true;
         TrailcamClient.failed(job.photo, "Could not read the frame");
      }
   }

   private static void capture(Minecraft mc) {
      RenderTarget target = mc.getMainRenderTarget();
      int w = target.width;
      int h = target.height;
      if (w < 16 || h < 16) {
         return;
      }
      int[] abgr;
      try (NativeImage img = new NativeImage(w, h, false)) {
         RenderSystem.bindTexture(target.getColorTextureId());
         img.downloadTexture(0, true);
         abgr = img.getPixelsRGBA();
      }
      if (blank(abgr)) {
         if (job.blankRetries++ < 3) {
            job.minFrames = job.frames + 3;
            return;
         }
         // some shader pipelines never leave the final image in the main target: fall back to the data frame
         job.captured = true;
         TrailcamClient.failed(job.photo, "The renderer returned an empty frame");
         return;
      }
      float[] depth = job.ir ? readDepth(target) : null;
      TrailcamLook.Input in = new TrailcamLook.Input();
      in.sw = w;
      in.sh = h;
      in.depth = depth;
      in.near = 0.05F;
      in.far = mc.gameRenderer.getDepthFar();
      if (w * 9 >= h * 16) {
         in.cropH = h;
         in.cropW = Math.min(w, Math.round(h * 16.0F / 9.0F));
      } else {
         in.cropW = w;
         in.cropH = Math.min(h, Math.round(w * 9.0F / 16.0F));
      }
      in.cropX = (w - in.cropW) / 2;
      in.cropY = (h - in.cropH) / 2;
      in.outW = TrailcamConfig.photoWidth();
      in.outH = TrailcamConfig.photoHeight();
      in.tanH = (float)Math.tan(Math.toRadians(TrailcamScene.FOV_H / 2.0));
      in.tanV = (float)Math.tan(Math.toRadians(TrailcamScene.FOV_V / 2.0));
      in.infrared = job.ir;
      in.seed = job.photo.id;
      marks(in);
      in.strip = strip(job.scene);
      job.captured = true;
      Photo photo = job.photo;
      WORK.incrementAndGet();
      Util.backgroundExecutor().execute(() -> {
         try {
            in.argb = toArgb(abgr, w, h);
            int[] out = TrailcamLook.develop(in);
            mc.execute(() -> {
               WORK.decrementAndGet();
               TrailcamClient.developed(photo, out, in.outW, in.outH);
            });
         } catch (Throwable t) {
            LOG.error("Trail camera photo could not be developed", t);
            mc.execute(() -> {
               WORK.decrementAndGet();
               TrailcamClient.failed(photo, "Could not develop this frame");
            });
         }
      });
   }

   /** GL rows are bottom-up and NativeImage ints are ABGR: flip and swizzle to top-down ARGB. */
   private static int[] toArgb(int[] abgr, int w, int h) {
      int[] out = new int[w * h];
      for (int y = 0; y < h; y++) {
         int src = (h - 1 - y) * w;
         int dst = y * w;
         for (int x = 0; x < w; x++) {
            int c = abgr[src + x];
            out[dst + x] = 0xFF000000 | (c & 0xFF) << 16 | (c & 0xFF00) | (c >> 16 & 0xFF);
         }
      }
      return out;
   }

   private static boolean blank(int[] px) {
      int step = Math.max(1, px.length / 997);
      for (int i = 0; i < px.length; i += step) {
         int c = px[i];
         if ((c & 0xFF) > 6 || (c >> 8 & 0xFF) > 6 || (c >> 16 & 0xFF) > 6) {
            return false;
         }
      }
      return true;
   }

   private static float[] readDepth(RenderTarget target) {
      int w = target.width;
      int h = target.height;
      for (int i = 0; i < 32 && GL11.glGetError() != 0; i++) {
         // clear stale errors so the check below is ours ([bugs] bounded: a lost context must not spin forever)
      }
      int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
      int prevPack = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
      int prevAlign = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
      FloatBuffer buf = MemoryUtil.memAllocFloat(w * h);
      try {
         GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
         GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
         GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
         GL11.glReadPixels(0, 0, w, h, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, buf);
         if (GL11.glGetError() != 0) {
            return null;
         }
         float[] out = new float[w * h];
         buf.get(out);
         return out;
      } catch (RuntimeException e) {
         LOG.debug("Trail camera: depth read failed, using the ground-plane fall-off", e);
         return null;
      } finally {
         MemoryUtil.memFree(buf);
         GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, prevAlign);
         GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, prevPack);
         GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
      }
   }

   // ------------------------------------------------------------------------------------------------ photo data

   private static void marks(TrailcamLook.Input in) {
      Vec3 lens = job.lens;
      float lowest = Float.NaN;
      boolean first = true;
      for (int i = 0; i < job.actors.size(); i++) {
         LivingEntity a = job.actors.get(i);
         CompoundTag tag = job.tags.get(i);
         AABB bb = a.getBoundingBox();
         float x0 = 9.0F;
         float y0 = 9.0F;
         float x1 = -9.0F;
         float y1 = -9.0F;
         boolean any = false;
         for (int c = 0; c < 8; c++) {
            Vec3 p = new Vec3((c & 1) == 0 ? bb.minX : bb.maxX, (c & 2) == 0 ? bb.minY : bb.maxY, (c & 4) == 0 ? bb.minZ : bb.maxZ);
            double[] q = TrailcamScene.project(lens, job.yaw, job.pitch, p);
            if (q == null) {
               continue;
            }
            any = true;
            x0 = Math.min(x0, (float)q[0]);
            x1 = Math.max(x1, (float)q[0]);
            y0 = Math.min(y0, (float)q[1]);
            y1 = Math.max(y1, (float)q[1]);
         }
         if (!any) {
            continue;
         }
         Vec3 center = bb.getCenter();
         double[] pc = TrailcamScene.project(lens, job.yaw, job.pitch, center);
         if (pc == null) {
            continue;
         }
         TrailcamLook.Mark m = new TrailcamLook.Mark();
         m.x0 = Math.max(-1.6F, x0);
         m.x1 = Math.min(1.6F, x1);
         m.y0 = Math.max(-1.6F, y0);
         m.y1 = Math.min(1.6F, y1);
         m.distance = (float)center.distanceTo(lens);
         if (job.ir) {
            // ~1/12 s night exposure
            Vec3 v = new Vec3(tag.getFloat("vx"), tag.getFloat("vy"), tag.getFloat("vz")).scale(1.6);
            double[] pm = TrailcamScene.project(lens, job.yaw, job.pitch, center.add(v));
            if (pm != null) {
               m.mx = (float)(pm[0] - pc[0]);
               m.my = (float)(pm[1] - pc[1]);
            }
            m.shine = tag.getFloat("shine");
            if (m.shine > 0.01F) {
               DarkroomActors.eyes(a, tag, lens, job.yaw, job.pitch, m);
            }
         }
         in.marks.add(m);
         if (first) {
            in.subjectDistance = m.distance;
            first = false;
         }
         float y = (float)a.getY();
         lowest = Float.isNaN(lowest) ? y : Math.min(lowest, y);
      }
      in.lensHeight = Float.isNaN(lowest) ? 1.4F : Math.max(0.4F, Math.min(8.0F, (float)lens.y - lowest));
   }

   private static TrailcamLook.Strip strip(CompoundTag scene) {
      TrailcamLook.Strip s = new TrailcamLook.Strip();
      s.camera = scene.getString("cam");
      float c = scene.getFloat("temp");
      s.temperature = String.format(Locale.ROOT, "%d°F %d°C", Math.round(c * 9.0F / 5.0F + 32.0F), Math.round(c));
      s.moonPhase = scene.getInt("moon");
      if (scene.contains("mo")) {
         s.date = String.format(Locale.ROOT, "%02d/%02d/%02d", Math.floorMod(scene.getInt("mo"), 12) + 1, scene.getInt("dy"), scene.getInt("yr"));
      }
      s.time = TrailcamScene.clock(scene.getLong("d"));
      s.frame = String.format(Locale.ROOT, "#%04d", scene.getInt("n") % 10000);
      return s;
   }

   /** Iris with a shader pack: more warm-up frames so temporal passes (TAA, exposure) converge on the new view. */
   private static boolean shadersInUse() {
      try {
         if (!irisLooked) {
            irisLooked = true;
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisInUse = api.getMethod("isShaderPackInUse");
         }
         if (irisInUse == null) {
            return false;
         }
         Object inst = Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null);
         return Boolean.TRUE.equals(irisInUse.invoke(inst));
      } catch (Throwable t) {
         irisInUse = null;
         return false;
      }
   }
}
