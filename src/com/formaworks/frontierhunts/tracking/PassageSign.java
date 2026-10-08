package com.formaworks.frontierhunts.tracking;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * [1.1.6] Sign of passage in grass and brush. When a deer, elk or moose moves through tall grass, ferns or brush, the
 * plants along its path are pushed over in the direction it went - harder when it runs - and slowly stand back up over
 * about seven minutes. Where an animal goes down, the grass under its body is pressed flat (and stays that way for a
 * day and a half). Nothing is removed: these are trail marks ({@link TrailMark#PASSAGE}, {@link TrailMark#MATTED}) that
 * the client draws by bending the plant models (client.PassageModel).
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class PassageSign {
   private PassageSign() {
   }

   /** metres of travel between two passage marks of one animal */
   static final double STRIDE = 1.15;
   /** passage marks the whole server may add per tick (keeps herds in tall grass cheap) */
   static final int PER_TICK = 6;

   private static final Map<UUID, Vec3> LAST = new HashMap<>();
   private static final Set<UUID> MATTED = new HashSet<>();
   private static int budget = PER_TICK;
   /** [1.1.8] debug counters for /frontierhunts sign passage: ticks seen, moving, no plant under the feet, made, failed */
   public static final int[] STATS = new int[7];
   public static volatile String lastError = "";

   @SubscribeEvent
   public static void tick(EntityTickEvent.Post event) {
      if (!(event.getEntity() instanceof Whitetail d) || !(d.level() instanceof ServerLevel level) || (d.tickCount + d.getId()) % 4 != 0) {
         return;
      }
      STATS[0]++;
      try {
         if (d.downed() || d.isDeadOrDying()) {
            if (MATTED.add(d.getUUID())) {
               matted(level, d);
            }
            return;
         }
         MATTED.remove(d.getUUID());
         passage(level, d);
      } catch (RuntimeException e) {
         // sign is cosmetic: never let it break an animal's tick
         STATS[4]++;
         lastError = String.valueOf(e);
      }
   }

   @SubscribeEvent
   public static void levelTick(LevelTickEvent.Post event) {
      budget = PER_TICK;
      if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 1200L == 0L) {
         // forget animals that are gone
         for (Iterator<UUID> it = LAST.keySet().iterator(); it.hasNext(); ) {
            if (level.getEntity(it.next()) == null) {
               it.remove();
            }
         }
         MATTED.removeIf(id -> level.getEntity(id) == null);
      }
   }

   /** a plant that bends: grass, ferns, flowers, reeds, the mod's thickets and sward (not snow, litter or sticks) */
   static boolean bendable(BlockState s) {
      if (s.isAir() || s.is(Blocks.SNOW) || s.is(Blocks.PINK_PETALS) || s.is(BlockTags.SAPLINGS)) {
         return false;
      }
      if (!s.is(TrailSurfaces.FOLIAGE)) {
         return false;
      }
      String path = s.getBlock().builtInRegistryHolder().key().location().getPath();
      return !path.equals("forest_litter") && !path.equals("forest_sticks") && !path.equals("alpine_overgrowth");
   }

   static float bodyScale(Whitetail d) {
      GameSpecies sp = d.species();
      return sp == GameSpecies.MOOSE ? 1.75F : sp == GameSpecies.ELK ? 1.5F : 1.0F;
   }

   private static void passage(ServerLevel level, Whitetail d) {
      // [1.1.8] speed from where the animal actually went this tick: deer are moved by their own locomotion, so their
      // velocity vector stays near zero even at a run (the reason no grass was ever pushed over)
      Vec3 v = new Vec3(d.getX() - d.xo, 0.0, d.getZ() - d.zo);
      double speed = v.horizontalDistance();
      if (speed < 1.0E-4) {
         v = d.getDeltaMovement();
         speed = v.horizontalDistance();
      }
      if (speed < 0.02 || speed > 2.0 || d.isInWater() || d.isPassenger()) {
         return;
      }
      Vec3 here = d.position();
      Vec3 last = LAST.get(d.getUUID());
      double moved = last == null ? STRIDE : Math.sqrt((here.x - last.x) * (here.x - last.x) + (here.z - last.z) * (here.z - last.z));
      if (moved < STRIDE) {
         return;
      }
      LAST.put(d.getUUID(), here);
      STATS[1]++;
      BlockPos feet = d.blockPosition();
      BlockPos plant = bendable(level.getBlockState(feet)) ? feet : (bendable(level.getBlockState(feet.above())) ? feet.above() : null);
      if (plant == null || budget <= 0) {
         STATS[2]++;
         return;
      }
      budget--;
      float yaw = (float)Math.toDegrees(Math.atan2(-v.x, v.z));
      int activity = speed > 0.22 ? 2 : (speed > 0.11 ? 1 : 0);
      float radius = (float)Math.clamp(d.getBbWidth() * 0.5, 0.2, 0.45);
      TrailMark m = new TrailMark(
         UUID.randomUUID(), d.getUUID(), new Vec3(here.x, feet.getY(), here.z), plant, yaw, level.getGameTime(), 0, false,
         TrackPrints.describe(d), activity, Direction.UP, TrailMark.PASSAGE, radius, 0, (float)Math.min(4.0, moved), bodyScale(d)
      );
      TrailStore.get(level).add(m, level.getGameTime());
      STATS[3]++;
   }

   /** the body-sized patch of pressed-down grass where an animal went down */
   static void matted(ServerLevel level, Whitetail d) {
      STATS[5]++;
      BlockPos feet = d.blockPosition();
      BlockPos plant = null;
      for (int dy = -1; dy <= 1 && plant == null; dy++) {
         for (int dx = -2; dx <= 2 && plant == null; dx++) {
            for (int dz = -2; dz <= 2 && plant == null; dz++) {
               BlockPos p = feet.offset(dx, dy, dz);
               if (bendable(level.getBlockState(p))) {
                  plant = p;
               }
            }
         }
      }
      if (plant == null) {
         STATS[6]++;
         lastError = "downed at " + feet.toShortString() + " in " + level.getBlockState(feet).getBlock().getDescriptionId() + " on "
            + level.getBlockState(feet.below()).getBlock().getDescriptionId();
         return;
      }
      TrailMark m = new TrailMark(
         UUID.randomUUID(), d.getUUID(), new Vec3(d.getX(), feet.getY(), d.getZ()), plant, d.getYRot(), level.getGameTime(), 0, false,
         TrackPrints.describe(d), 0, Direction.UP, TrailMark.MATTED, 0.45F, 0, 0.0F, bodyScale(d)
      );
      TrailStore.get(level).add(m, level.getGameTime());
   }
}
