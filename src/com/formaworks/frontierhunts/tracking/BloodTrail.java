package com.formaworks.frontierhunts.tracking;

import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * [tracking] Blood that tells the truth about the hit. The wound region decides colour, froth, matter, density, where
 * blood lands (both sides, brush at chest height) and how the trail tapers:
 * <ul>
 * <li>HEART: heavy bright spray at the hit and an exit spray; the deer drops on the spot ([integration] no death run).</li>
 * <li>LUNG: bright pinkish frothy blood with bubbles, sprayed on brush at chest height, heavy then tapering.</li>
 * <li>LIVER: dark red, steady drops, moderate length.</li>
 * <li>GUT: sparse dark drops flecked with stomach matter, a long trail with beds.</li>
 * <li>MUSCLE (leg, flesh, shoulder): bright drips that thin out and stop - the animal usually lives.</li>
 * </ul>
 */
public final class BloodTrail {
   public enum BloodType {
      GENERIC, HEART, LUNG, LIVER, GUT, MUSCLE, ARTERIAL;

      public static BloodType of(int ordinal) {
         BloodType[] v = values();
         return ordinal >= 0 && ordinal < v.length ? v[ordinal] : GENERIC;
      }

      public String reading() {
         return switch (this) {
            case HEART -> "bright red, sprayed both sides — heart";
            case LUNG -> "pink and frothy, tiny bubbles — lungs";
            case LIVER -> "dark red, steady drops — liver";
            case GUT -> "dark and thin, flecked with stomach matter — gut";
            case MUSCLE -> "bright red drips, thinning — muscle";
            case ARTERIAL -> "bright red spurts — neck";
            default -> "blood";
         };
      }
   }

   public static BloodType fromRegion(String region) {
      if (region == null) {
         return BloodType.GENERIC;
      }
      return switch (region) {
         case "HEART" -> BloodType.HEART;
         case "DOUBLE_LUNG", "LUNG", "CHEST" -> BloodType.LUNG;
         case "LIVER" -> BloodType.LIVER;
         case "GUT" -> BloodType.GUT;
         case "LEG", "BODY", "SHOULDER" -> BloodType.MUSCLE;
         case "NECK" -> BloodType.ARTERIAL;
         default -> BloodType.GENERIC;
      };
   }

   private static final class Wound {
      BloodType type = BloodType.GENERIC;
      String region = "";
      int initial;
      int steps;
      long lastBed = -100000L;
      long nextBedCheck;
   }

   private static final Map<LivingEntity, Wound> WOUNDS = new WeakHashMap<>();

   private BloodTrail() {
   }

   private static Wound wound(Whitetail w) {
      Wound s = WOUNDS.computeIfAbsent(w, k -> new Wound());
      String region = w.shotRegion();
      if (!region.equals(s.region)) {
         s.region = region;
         s.type = fromRegion(region);
         s.initial = 0;
         s.steps = 0;
      }
      s.initial = Math.max(s.initial, w.woundTicks());
      return s;
   }

   /** the blood type an entity is bleeding right now */
   public static BloodType type(LivingEntity e) {
      if (e instanceof Whitetail w) {
         return wound(w).type;
      }
      BloodType t = WildlifeBleeding.type(e);
      return t == null ? BloodType.GENERIC : t;
   }

   /** 0 at the hit .. 1 when the bleeding stops */
   private static float progress(Whitetail w, Wound s) {
      return s.initial <= 0 ? 0.0F : Math.clamp(1.0F - (float)w.woundTicks() / (float)s.initial, 0.0F, 1.0F);
   }

   // ------------------------------------------------------------------------------------------------ Whitetail hooks

   /**
    * A heart or both-lung hit usually lets the animal make a short death run instead of dropping on the spot.
    * [integration] NOT used by Whitetail: the user decided vital hits drop on the spot (kill cam ends with the drop).
    * Kept for the offline harness / a possible future option.
    */
   public static boolean deathRun(String region, float power, RandomSource random) {
      if (power < 0.12F) {
         return false;
      }
      return switch (region) {
         case "HEART" -> random.nextFloat() < 0.65F;
         case "DOUBLE_LUNG" -> random.nextFloat() < 0.85F;
         default -> false;
      };
   }

   /** How long a hit animal keeps running flat out; gut, liver and flesh hits slow down and look for a bed. */
   public static int fleeMemory(String region, int normal) {
      return switch (fromRegion(region)) {
         case GUT -> Math.min(normal, 500);
         case LIVER -> Math.min(normal, 700);
         case MUSCLE -> Math.min(normal, 900);
         default -> normal;
      };
   }

   /** Every tick while the deer bleeds (server): gut / liver / flesh-hit animals lie down along the trail. */
   public static void wounded(Whitetail w) {
      if (!(w.level() instanceof ServerLevel level) || w.downed()) {
         return;
      }
      Wound s = wound(w);
      long now = level.getGameTime();
      if (now < s.nextBedCheck) {
         return;
      }
      s.nextBedCheck = now + 20L;
      boolean beds = s.type == BloodType.GUT || s.type == BloodType.LIVER || s.type == BloodType.MUSCLE && progress(w, s) < 0.6F;
      if (beds
         && w.behavior() == Whitetail.BEHAVIOR_NORMAL
         && w.alertness() < 0.14F
         && w.onGround()
         && !w.isInWater()
         && now - s.lastBed > 1200L
         && w.getRandom().nextInt(s.type == BloodType.GUT ? 5 : 9) == 0) {
         s.lastBed = now;
         w.woundBed(500 + w.getRandom().nextInt(s.type == BloodType.GUT ? 2200 : 1200));
      }
   }

   /** Impact: blood at the hit, plus the exit spray on the far side for a pass-through heart / lung hit. */
   public static void impact(Whitetail w) {
      Wound s = wound(w);
      if (s.type == BloodType.HEART || s.type == BloodType.LUNG) {
         Vec3 c = w.position();
         Vec3 p = w.woundWorldPosition();
         Vec3 far = new Vec3(2.0 * c.x - p.x, p.y, 2.0 * c.z - p.z);
         TrailService.mark(w, far, TrailMark.IMPACT, s.type);
         TrailService.mark(w, far.add(w.getRandom().nextGaussian() * 0.3, 0.0, w.getRandom().nextGaussian() * 0.3), TrailMark.DENSE, s.type);
      }
   }

   /**
    * One trail step of a moving (or standing) bleeding deer, called at the Whitetail's own cadence. Always
    * "succeeds", so skipping a drop keeps the cadence: a sparse trail is sparse on purpose.
    */
   public static boolean step(Whitetail w, boolean heavy) {
      if (!(w.level() instanceof ServerLevel)) {
         return false;
      }
      Wound s = wound(w);
      s.steps++;
      BloodType t = s.type;
      float f = progress(w, s);
      RandomSource r = w.getRandom();
      int beh = w.behavior();
      if (beh == Whitetail.BEHAVIOR_BEDDED || beh == Whitetail.BEHAVIOR_SLEEPING) {
         TrailService.bed(w, t);
         return true;
      }
      Vec3 p = w.woundWorldPosition();
      boolean still = w.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4;
      if (still) {
         double a = (w.level().getGameTime() + w.getId() * 37L) * 0.41;
         double rr = 0.82 * TrailSurfaces.bloodScale(w);
         p = p.add(Math.cos(a) * rr, 0.0, Math.sin(a) * rr);
      }
      Vec3 side = side(w);
      switch (t) {
         case HEART, ARTERIAL -> {
            double h = w.getBbWidth() * 0.5 + 0.12;
            TrailService.mark(w, w.position().add(side.scale(h)).add(0.0, p.y - w.getY(), 0.0), TrailMark.DENSE, t);
            TrailService.mark(w, w.position().add(side.scale(-h)).add(0.0, p.y - w.getY(), 0.0), TrailMark.DENSE, t);
            if (t == BloodType.HEART || s.steps % 2 == 0) {
               TrailService.mark(w, p, TrailMark.DRIP, t);
            }
         }
         case LUNG -> {
            if (f < 0.35F) {
               TrailService.mark(w, p, TrailMark.DENSE, t);
               TrailService.mark(w, p.add(side.scale((r.nextBoolean() ? 1 : -1) * (w.getBbWidth() * 0.5 + 0.1))), TrailMark.DRIP, t);
            } else if (f < 0.75F || s.steps % 2 == 0) {
               TrailService.mark(w, p, heavy ? TrailMark.DENSE : TrailMark.DRIP, t);
            }
         }
         case LIVER -> TrailService.mark(w, p, heavy || s.steps % 5 == 0 ? TrailMark.DENSE : TrailMark.DRIP, t);
         case GUT -> {
            if (s.steps % 3 == 0 || heavy && s.steps % 3 == 1) {
               TrailService.mark(w, p, TrailMark.DRIP, t);
            }
         }
         case MUSCLE -> {
            float keep = Math.clamp(1.0F - f / 0.55F, 0.0F, 1.0F);
            if (r.nextFloat() < keep) {
               TrailService.mark(w, p, keep > 0.75F && (heavy || s.steps % 4 == 0) ? TrailMark.DENSE : TrailMark.DRIP, t);
            }
         }
         default -> TrailService.mark(w, p, heavy ? TrailMark.DENSE : TrailMark.DRIP, t);
      }
      return true;
   }

   /** Brush smears, called every 4 ticks while bleeding: chest-height spray for heart / lung, little for the rest. */
   public static void brush(Whitetail w) {
      Wound s = wound(w);
      long t = w.level().getGameTime();
      switch (s.type) {
         case HEART, LUNG, ARTERIAL -> TrailService.brushAt(w, w.woundWorldPosition(), s.type);
         case LIVER -> {
            if (t % 12L == 0L) {
               TrailService.brushAt(w, w.woundWorldPosition(), s.type);
            }
         }
         case GUT -> {
            if (t % 24L == 0L) {
               TrailService.brushAt(w, w.woundWorldPosition(), s.type);
            }
         }
         case MUSCLE -> {
            if (progress(w, s) < 0.3F && t % 8L == 0L) {
               TrailService.brushAt(w, w.woundWorldPosition(), s.type);
            }
         }
         default -> TrailService.brushAt(w, w.woundWorldPosition(), s.type);
      }
   }

   /** unit vector to the animal's right, level */
   static Vec3 side(LivingEntity e) {
      double a = Math.toRadians(e.yBodyRot);
      return new Vec3(Math.cos(a), 0.0, Math.sin(a));
   }
}
