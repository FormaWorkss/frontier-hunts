package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.hunting.DeerSign;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.trailcam.TrailcamConfig;
import com.formaworks.frontierhunts.trailcam.TrailcamScene;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Trail camera block entity. [trailcam] rewritten detection: a PIR-style trigger samples the frame twice a second,
 * fires on a warm body that moves inside the photo frame (20 m by day, 15 m under infrared), then records a full scene
 * (see {@link TrailcamScene}) that the owner's client develops into a real rendered photograph.
 */
public final class TrailCamera extends BlockEntity {
   public static final int CAPACITY = 2400;
   public static final int ROLL = 48;
   static final int RANGE = 18;
   private static final int SCAN = 10;
   private final Map<UUID, Long> seen = new HashMap<>();
   /** last sampled position + time per warm body, for the motion test */
   private final Map<UUID, double[]> motion = new HashMap<>();
   private CompoundTag carried;
   private int carriedCharge = -1;
   private long lastShot = Long.MIN_VALUE / 2;
   private long lastDrain = -1L;
   private float pitch = Float.NaN;
   private long pitchAt;

   public TrailCamera(BlockPos var1, BlockState var2) {
      super((BlockEntityType)ExpeditionContent.CAMERA.get(), var1, var2);
   }

   public CameraRegistry.Station station(ServerLevel var1) {
      CameraRegistry var2 = CameraRegistry.get(var1);
      CameraRegistry.Station var3 = var2.find(var1, this.worldPosition);
      if (var3 != null) {
         return var3;
      } else {
         var3 = var2.place(
            var1,
            this.worldPosition,
            (Direction)this.getBlockState().getValue(TrailCameraBlock.FACING),
            null,
            this.carriedCharge >= 0 ? this.carriedCharge : 2400,
            watching(var1, this.worldPosition)
         );
         if (this.carried != null && this.carried.contains("camera_log", 9)) {
            CameraRegistry.loadRoll(this.carried.getList("camera_log", 10), var3.roll);
            var3.shots = Math.max(var3.shots, CameraRegistry.maxShot(var3.roll));
            this.carried = null;
            this.carriedCharge = -1;
         }

         return var3;
      }
   }

   public void restore(CompoundTag var1) {
      this.carried = var1;
      this.carriedCharge = var1.contains("field_charge") ? Math.clamp((long)var1.getInt("field_charge"), 0, 2400) : 2400;
   }

   public static void tick(Level level, BlockPos pos, BlockState state, TrailCamera cam) {
      if (!(level instanceof ServerLevel server)) {
         return;
      }
      long now = server.getGameTime();
      if ((now + Math.floorMod(pos.hashCode(), SCAN)) % SCAN != 0L) {
         return;
      }
      CameraRegistry registry = CameraRegistry.get(server);
      CameraRegistry.Station station = cam.station(server);
      station.caught = now;
      boolean powered = station.charge > 0;
      if (state.getValue(TrailCameraBlock.ACTIVE) != powered) {
         level.setBlock(pos, state.setValue(TrailCameraBlock.ACTIVE, powered), 3);
      }
      if (cam.lastDrain < 0L || cam.lastDrain > now) {
         cam.lastDrain = now;
      }
      if (!powered) {
         return;
      }
      if (now - cam.lastDrain >= 400L) {
         cam.lastDrain = now;
         station.charge = Math.max(0, station.charge - 1);
         registry.touch();
      }

      Direction facing = state.getValue(TrailCameraBlock.FACING);
      Vec3 lens = TrailcamScene.lens(pos, facing);
      float yaw = facing.toYRot();
      if (Float.isNaN(cam.pitch) || now - cam.pitchAt > 1200L || now < cam.pitchAt) {
         cam.pitch = TrailcamScene.aimPitch(server, lens, facing);
         cam.pitchAt = now;
      }
      float pitch = cam.pitch;
      boolean ir = TrailcamScene.dark(server, lens, facing);
      double range = ir ? TrailcamConfig.nightRange() : TrailcamConfig.dayRange();
      long cooldown = TrailcamConfig.subjectCooldownTicks();

      if (cam.seen.size() > 64) {
         cam.seen.entrySet().removeIf(e -> now - e.getValue() > cooldown || now < e.getValue());
      }
      if (cam.motion.size() > 64) {
         cam.motion.entrySet().removeIf(e -> now - (long)e.getValue()[3] > 200L);
      }

      List<LivingEntity> near = server.getEntitiesOfClass(LivingEntity.class, new AABB(lens, lens).inflate(range + 1.0), TrailcamScene::subject);
      if (near.isEmpty()) {
         return;
      }
      near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(lens)));
      boolean recovering = now - cam.lastShot < TrailcamConfig.triggerDelayTicks() && now >= cam.lastShot;
      List<LivingEntity> framed = new ArrayList<>();
      LivingEntity trigger = null;
      for (LivingEntity e : near) {
         if (!TrailcamScene.inFrame(lens, yaw, pitch, e, range) || !TrailcamScene.visible(server, lens, e)) {
            continue;
         }
         framed.add(e);
         double[] last = cam.motion.get(e.getUUID());
         boolean moved = last == null
            || now - (long)last[3] > 200L
            || e.distanceToSqr(last[0], last[1], last[2]) > 0.04
            || e.getDeltaMovement().horizontalDistanceSqr() > 9.0E-4
            || Math.abs(Mth.wrapDegrees(e.yBodyRot - (float)last[4])) > 25.0F;
         cam.motion.put(e.getUUID(), new double[]{e.getX(), e.getY(), e.getZ(), (double)now, e.yBodyRot});
         Long seenAt = cam.seen.get(e.getUUID());
         boolean rested = seenAt == null || now - seenAt >= cooldown || now < seenAt;
         if (moved && rested && trigger == null) {
            trigger = e;
         }
      }
      if (trigger == null || recovering || station.charge <= 0) {
         return;
      }

      // every warm body in the frame is on the photo; the one that tripped the sensor is the headline
      List<LivingEntity> subjects = new ArrayList<>(framed);
      subjects.remove(trigger);
      subjects.add(0, trigger);
      if (subjects.size() > TrailcamScene.MAX_SUBJECTS) {
         subjects = new ArrayList<>(subjects.subList(0, TrailcamScene.MAX_SUBJECTS));
      }
      station.over = watching(server, pos);
      int frameNo = ++station.shots;
      CompoundTag scene = TrailcamScene.capture(server, station, lens, yaw, pitch, ir, subjects, frameNo);
      registry.record(station, legacy(server, trigger, lens, facing, scene, ir, station.over));
      com.formaworks.frontierhunts.journal.JournalHooks.trailcamPhoto(server, station.owner, subjects); // [journal] photo log, new species
      for (LivingEntity e : subjects) {
         cam.seen.put(e.getUUID(), now);
      }
      cam.lastShot = now;
      station.charge = Math.max(0, station.charge - (ir ? 5 : 3));
      registry.touch();
      cam.setChanged();
   }

   /** The data-only frame the older screens and the hub read (deer traits, bearing, stance), plus the scene. */
   private static CameraRegistry.Capture legacy(ServerLevel level, LivingEntity e, Vec3 lens, Direction facing, CompoundTag scene, boolean ir, String over) {
      float yaw = facing.toYRot();
      Vec3 d = e.position().subtract(lens);
      float bearing = Mth.wrapDegrees((float)(Mth.atan2(-d.x, d.z) * (180.0 / Math.PI)) - yaw);
      float body = Mth.wrapDegrees(e.yBodyRot - yaw);
      int hour = TrailcamScene.hour(level.getDayTime());
      DeerTraits traits;
      int stance;
      if (e instanceof Whitetail w) {
         traits = w.traits().withSpecies(w.species());
         stance = w.alertness() > 0.4F ? 2 : (w.getDeltaMovement().horizontalDistanceSqr() > 0.004 ? 0 : (w.graze(1.0F) > 0.35F ? 1 : 3));
      } else {
         traits = TrailcamScene.nonDeerTraits();
         stance = e.getDeltaMovement().horizontalDistanceSqr() > 0.004 ? 0 : 3;
      }
      int sky = ir
         ? 2
         : (hour == 5 || hour == 6 || hour == 18 || hour == 19
            ? 1
            : (level.isRaining() ? (((Biome)level.getBiome(e.blockPosition()).value()).coldEnoughToSnow(e.blockPosition()) ? 4 : 3) : 0));
      return new CameraRegistry.Capture(
         traits,
         level.getGameTime(),
         HuntingCalendar.date(level).title(),
         hour,
         over,
         (float)d.length(),
         bearing,
         body,
         (byte)stance,
         (byte)sky,
         scene
      );
   }

   static String watching(Level var0, BlockPos var1) {
      boolean var2 = false;
      boolean var3 = false;

      for (BlockPos var5 : BlockPos.betweenClosed(var1.offset(-7, -4, -7), var1.offset(7, 4, 7))) {
         if (var0.hasChunkAt(var5)) {
            net.minecraft.world.level.block.Block var6 = var0.getBlockState(var5).getBlock();
            if (var6 instanceof DeerSign var7 && var7.kind == DeerSign.Kind.SCRAPE) {
               var2 = true;
            } else if (var6 instanceof ScentDecoy) {
               var3 = true;
            }

            if (var2 && var3) {
               break;
            }
         }
      }

      return var2 && var3 ? "scrape + bait" : (var2 ? "over a scrape" : (var3 ? "over bait" : "open ground"));
   }

   public static int daysPerMonth() {
      return (Integer)HuntConfig.DAYS_PER_MONTH.get();
   }

   protected void saveAdditional(CompoundTag var1, Provider var2) {
      super.saveAdditional(var1, var2);
      if (this.carried != null) {
         var1.put("carried", this.carried);
      }

      if (this.carriedCharge >= 0) {
         var1.putInt("carried_charge", this.carriedCharge);
      }
   }

   protected void loadAdditional(CompoundTag var1, Provider var2) {
      super.loadAdditional(var1, var2);
      if (var1.contains("carried", 10)) {
         this.carried = var1.getCompound("carried");
      }

      this.carriedCharge = var1.contains("carried_charge") ? Math.clamp((long)var1.getInt("carried_charge"), 0, 2400) : -1;
      if (this.carriedCharge < 0 && var1.contains("charge")) {
         this.carriedCharge = Math.clamp((long)var1.getInt("charge"), 0, 2400);
      }
   }
}
