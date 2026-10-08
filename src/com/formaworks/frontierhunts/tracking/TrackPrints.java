package com.formaworks.frontierhunts.tracking;

import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.tracking.hound.TrackingHound;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * [tracking] The one print system: every relevant animal (and hunter) presses a group of prints into snow (vanilla
 * snow layers included), mud, soil, sand and - faintly - gravel and grass, one group per stride. Prints are
 * {@link TrailMark}s: they carry maker, time, gait, stride and size, age with the weather in {@link TrailStore}, sync to
 * nearby players and are what the hound follows. Called every 4th tick per entity from AlpineTrackPrints.
 */
public final class TrackPrints {
   private static final Map<Entity, State> STATE = new WeakHashMap<>();

   private static final class State {
      double x, z;
      long at;
      double sx, sz;
      float speed;
      boolean ended;
   }

   private TrackPrints() {
   }

   /**
    * [perf] Per entity class: can it ever make prints? Computed once per class, so every other entity (items, arrows,
    * vanilla mobs, minecarts...) is rejected by one ClassValue read before the instanceof chain, any map lookup or
    * block read. Must list exactly the types {@link #kindOf} handles.
    */
   private static final ClassValue<Boolean> MAKER = new ClassValue<>() {
      @Override
      protected Boolean computeValue(Class<?> c) {
         return Whitetail.class.isAssignableFrom(c)
            || WildlifeMob.class.isAssignableFrom(c)
            || Player.class.isAssignableFrom(c)
            || TrackingHound.class.isAssignableFrom(c)
            || Rabbit.class.isAssignableFrom(c)
            || Fox.class.isAssignableFrom(c)
            || Wolf.class.isAssignableFrom(c)
            || PolarBear.class.isAssignableFrom(c);
      }
   };

   /** [perf] Cheap early filter: false for every entity class that never leaves prints (see {@link #kindOf}). */
   public static boolean maker(Entity e) {
      return MAKER.get(e.getClass());
   }

   /** The print maker for this entity, or null for everything that leaves no prints (cheap: a few instanceof). */
   public static PrintKind kindOf(Entity e) {
      if (!MAKER.get(e.getClass())) {
         return null; // [perf]
      }
      if (e instanceof Whitetail w) {
         GameSpecies s = w.species();
         return s == GameSpecies.ELK ? PrintKind.ELK : s == GameSpecies.MOOSE ? PrintKind.MOOSE : PrintKind.DEER;
      } else if (e instanceof WildlifeMob m) {
         return switch (m.species) {
            case GROUSE -> PrintKind.GROUSE;
            case DUCK -> PrintKind.DUCK;
            case BOAR -> PrintKind.BOAR;
            case COYOTE -> PrintKind.COYOTE;
            case WOLF -> PrintKind.WOLF;
            case PRONGHORN -> PrintKind.PRONGHORN;
            case COUGAR -> PrintKind.COUGAR;
            case ELK -> PrintKind.ELK;
            case MOOSE -> PrintKind.MOOSE;
            case GRIZZLY -> PrintKind.GRIZZLY;
            case BLACK_BEAR -> PrintKind.BLACK_BEAR;
            case POLAR_BEAR -> PrintKind.POLAR_BEAR;
            case BISON -> PrintKind.BISON;
            case LION -> PrintKind.LION;
            case PANTHER -> PrintKind.PANTHER;
            case CHEETAH -> PrintKind.CHEETAH;
            default -> PrintKind.DEER;
         };
      } else if (e instanceof Player) {
         return PrintKind.BOOT;
      } else if (e instanceof TrackingHound) {
         return PrintKind.HOUND;
      } else if (e instanceof Rabbit) {
         return PrintKind.RABBIT;
      } else if (e instanceof Fox) {
         return PrintKind.FOX;
      } else if (e instanceof Wolf) {
         return PrintKind.WOLF;
      } else if (e instanceof PolarBear) {
         return PrintKind.POLAR_BEAR;
      }
      return null;
   }

   /** print size relative to the species norm */
   public static float scaleOf(Entity e, PrintKind k) {
      if (e instanceof Whitetail w) {
         float ref = w.species().referenceMassKg;
         return (float)Math.clamp(Math.cbrt(Math.max(10, w.massKg()) / (double)ref), 0.55, 1.45);
      }
      float base = e instanceof LivingEntity l && l.isBaby() ? 0.6F : 1.0F;
      if (k == PrintKind.BOOT) {
         return 1.0F;
      }
      // individuals differ a little (stable per animal)
      long h = e.getUUID().getLeastSignificantBits() ^ e.getUUID().getMostSignificantBits();
      float jitter = 0.9F + (float)((h >>> 8) & 255L) / 255.0F * 0.22F;
      return Math.clamp(base * jitter * (e instanceof LivingEntity l ? l.getScale() : 1.0F), 0.3F, 2.5F);
   }

   /** species + individual description, "Species · detail" (read on inspection) */
   public static String describe(Entity e) {
      String s;
      if (e instanceof Whitetail w) {
         int kg = w.massKg();
         float r = kg / Math.max(1.0F, w.species().referenceMassKg);
         String size = r >= 1.4F ? "exceptionally large " : r >= 1.07F ? "large " : r < 0.65F ? "small " : "medium ";
         String species = w.species() == GameSpecies.WHITETAIL ? "Whitetail" : w.species().title;
         s = species + " · " + size + w.species().sexName(w.traits().buck());
      } else {
         PrintKind k = kindOf(e);
         String label = k == null ? "Wildlife" : k.label;
         s = label + " · " + (e instanceof LivingEntity l && l.isBaby() ? "young" : "adult");
         if (e instanceof Player) {
            s = "Hunter · boot prints";
         }
      }
      return s.length() > 80 ? s.substring(0, 80) : s;
   }

   /** Every 4th tick per entity (server). */
   public static void tick(Entity e, ServerLevel level) {
      // [perf] kindOf starts with the per-class MAKER verdict: non-makers leave here before any lookup. There is no
      // onGround filter up front (perf's old block-print filter had one): the scent ledger, the speed estimate and
      // wildlife bleeding must keep running while an animal jumps or swims; prints themselves still need the ground.
      PrintKind k = kindOf(e);
      if (k == null) {
         return;
      }
      if (e instanceof WildlifeMob mob) {
         WildlifeBleeding.tick(mob, level);
      }
      if (!TrailService.active(level)) {
         return;
      }
      if (e instanceof Player p && (p.isSpectator() || p.getAbilities().flying || p.isPassenger())) {
         return;
      }
      long now = level.getGameTime();
      State st = STATE.get(e);
      if (e instanceof Whitetail w && w.downed()) {
         if (st != null && !st.ended) {
            st.ended = true;
            ScentLedger.get(level).end(e.getUUID(), e.position(), now);
         }
         return;
      }
      if (st == null) {
         st = new State();
         st.x = st.sx = e.getX();
         st.z = st.sz = e.getZ();
         st.at = now;
         STATE.put(e, st);
         return;
      }
      // smoothed ground speed from the 4-tick samples (players move client-side: deltaMovement is unreliable)
      double sdx = e.getX() - st.sx, sdz = e.getZ() - st.sz;
      st.sx = e.getX();
      st.sz = e.getZ();
      float sample = (float)Math.sqrt(sdx * sdx + sdz * sdz) / 4.0F;
      st.speed = sample > 2.0F ? 0.0F : st.speed + (sample - st.speed) * 0.45F;
      if (k != PrintKind.BOOT && k != PrintKind.HOUND && e.isAlive()) {
         ScentLedger.get(level).step(e.getUUID(), e.position(), now);
      }
      if (!e.onGround() || e.isInWater()) {
         return;
      }
      double dx = e.getX() - st.x, dz = e.getZ() - st.z;
      double d2 = dx * dx + dz * dz;
      if (d2 > 144.0) {
         // teleported / loaded far away: restart the stride here
         st.x = e.getX();
         st.z = e.getZ();
         st.at = now;
         return;
      }
      float scale = scaleOf(e, k);
      float grow = (float)Math.pow(scale, 0.35);
      boolean running = st.speed > k.runSpeed() * grow;
      double need = k.stride * scale * (running ? (k.layout == PrintKind.Layout.BIRD ? 1.5 : 2.4) : 1.0);
      if (d2 < need * need) {
         return;
      }
      float stride = (float)Math.sqrt(d2);
      st.x = e.getX();
      st.z = e.getZ();
      st.at = now;
      int activity = st.speed < 0.012F ? 0 : (running ? 2 : 1);
      float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
      place(e, level, k, yaw, activity, stride, scale, now);
   }

   private static void place(Entity e, ServerLevel level, PrintKind k, float yaw, int activity, float stride, float scale, long now) {
      BlockPos feet = BlockPos.containing(e.getX(), e.getY() + 0.02, e.getZ());
      if (!level.isLoaded(feet)) {
         return;
      }
      BlockPos support;
      BlockState s = level.getBlockState(feet);
      double top;
      if (s.is(Blocks.SNOW)) {
         // vanilla snow layers (even one layer on grass): the print is pressed into the snow's visible top
         support = feet;
         top = TrailSurfaces.snowTop(s);
      } else {
         support = feet.below();
         s = level.getBlockState(support);
         if (s.is(Blocks.SNOW)) {
            top = TrailSurfaces.snowTop(s);
         } else {
            VoxelShape shape = s.getCollisionShape(level, support);
            if (shape.isEmpty()) {
               return;
            }
            top = shape.max(Direction.Axis.Y);
            if (top < 0.5 || top > 1.0) {
               return;
            }
            BlockState above = level.getBlockState(support.above());
            if (above.is(Blocks.SNOW)) {
               support = support.above();
               s = above;
               top = TrailSurfaces.snowTop(above);
            }
         }
      }
      int surface = TrailSurfaces.printSurface(s);
      if (surface == TrailSurfaces.NONE || !allowed(k, surface, e) || !level.getFluidState(support.above()).isEmpty()) {
         return;
      }
      Vec3 pos = new Vec3(e.getX(), support.getY() + top + 0.012, e.getZ());
      float radius = Math.clamp(0.1F + k.length * scale * 2.4F + (k.layout == PrintKind.Layout.BIPED ? 0.05F : 0.0F), 0.12F, 0.45F);
      TrailMark m = new TrailMark(
         UUID.randomUUID(), e.getUUID(), pos, support, yaw, now, 0, false, describe(e), activity, Direction.UP, 0, radius, k.ordinal(),
         Math.min(stride, 16.0F), scale
      );
      TrailStore.get(level).add(m, now);
   }

   /** light animals don't mark grass or gravel; hunters only mark soft ground */
   private static boolean allowed(PrintKind k, int surface, Entity e) {
      if (surface == TrailSurfaces.GRASS || surface == TrailSurfaces.GRAVEL) {
         if (k == PrintKind.BOOT) {
            return false;
         }
         return switch (k.layout) {
            case BIRD, HOP -> false;
            default -> k != PrintKind.FOX && k != PrintKind.COYOTE && (surface == TrailSurfaces.GRASS || k.length >= 0.1F);
         };
      }
      return true;
   }
}
