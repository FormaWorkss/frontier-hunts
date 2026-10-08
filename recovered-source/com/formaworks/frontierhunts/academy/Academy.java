package com.formaworks.frontierhunts.academy;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.tracking.TrailMark;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * [academy] Ranger Academy: the training grounds dimension and the small, dependency-free gates other systems call
 * (one line each, marked {@code // [academy]}). Everything here is cheap: a dimension key compare.
 *
 * <ul>
 * <li>{@link #journalBlocked} - nothing done in the training grounds counts for the Hunter's Journal (no XP farming on
 * scripted animals); course rewards are paid after the hunter is back home.</li>
 * <li>{@link #survivalFrozen} - hunger, nutrition and body temperature hold still while training.</li>
 * <li>{@link #weatherBlocked} - no storms or blizzards in the training grounds.</li>
 * <li>{@link #dressedInTraining} / {@link #signRead} - the training carcass and the scripted blood trail report to
 * the academy instead of the campaign / ledgers.</li>
 * </ul>
 */
public final class Academy {
   public static final ResourceKey<Level> TRAINING = ResourceKey.create(Registries.DIMENSION, FrontierHunts.id("training_grounds"));
   /** Entity tag on everything the academy spawns. Anything with it that loads from disk without a live session is a leftover. */
   public static final String TAG = "frontierhunts.academy";
   /** CUSTOM_DATA key on lent training gear (value: the session nonce). */
   public static final String LENT = "frontierhunts_academy_lent";

   private Academy() {
   }

   public static boolean isTrainingLevel(Level level) {
      return level != null && level.dimension() == TRAINING;
   }

   public static boolean inTraining(Entity e) {
      return e != null && isTrainingLevel(e.level());
   }

   /** [academy] hook in JournalService.record: training never counts for the journal. */
   public static boolean journalBlocked(ServerPlayer p) {
      return p != null && isTrainingLevel(p.level());
   }

   /** [academy] hook in SurvivalService.update: meters and body heat hold still in the training grounds. */
   public static boolean survivalFrozen(ServerPlayer p) {
      return p != null && isTrainingLevel(p.level());
   }

   /** [academy] hook in WeatherDirector.weatherLevel: the training grounds have no weather events. */
   public static boolean weatherBlocked(Level level) {
      return isTrainingLevel(level);
   }

   /**
    * [academy] hook at the top of a successful Whitetail.harvest: a training carcass yields nothing and is recorded
    * nowhere but in the course. Returns true when the harvest was handled here.
    */
   public static boolean dressedInTraining(ServerPlayer p, Whitetail deer) {
      if (p == null || deer == null || !isTrainingLevel(deer.level())) {
         return false;
      }
      try {
         TrainingService.dressed(p, deer);
      } catch (RuntimeException ex) {
         TrainingService.LOG.warn("Frontier Hunts academy: dressing hook failed", ex);
      }
      return true;
   }

   /** [academy] hook in TrailService.inspect: returns true when the mark was read in the training grounds. */
   public static boolean signRead(ServerPlayer p, TrailMark mark) {
      if (p == null || mark == null || !isTrainingLevel(p.level())) {
         return false;
      }
      try {
         TrainingService.inspected(p, mark);
      } catch (RuntimeException ex) {
         TrainingService.LOG.warn("Frontier Hunts academy: sign hook failed", ex);
      }
      return true;
   }
}
