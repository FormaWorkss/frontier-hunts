package com.formaworks.frontierhunts.academy;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * [academy] The save/restore state machine behind a training session, kept free of game objects so it can be tested
 * offline (tools/academy/harness). The live implementation of {@link Host} is {@link HomeState.PlayerHost}.
 *
 * <p>The authoritative copy of the hunter's home state lives in the player's own persisted data (the
 * {@code PlayerPersisted} compound), so it is written to the player's .dat file atomically together with the training
 * inventory it replaces and survives the death clone. A second copy sits in the academy SavedData as a backup that is
 * used only when the hunter is found inside the training grounds without their own record. A backup found while the
 * hunter is already home is stale (their own file is newer) and is discarded, never applied, so nothing can be
 * restored twice.
 *
 * <pre>
 *   NONE --begin--> TRAINING --finish (pass / abandon / fail / timeout / death)--> NONE   (position restored)
 *                       |---forcedExit (left by another teleport)---------------> NONE   (position kept)
 *                       |---disconnect / crash / restart: record stays; recover() at login or respawn --> NONE
 * </pre>
 */
public final class TrainingFlow {
   /** Key inside the player's PlayerPersisted compound. */
   public static final String KEY = "frontierhunts_academy_home";

   private TrainingFlow() {
   }

   /** The side effects on one player. */
   public interface Host {
      UUID id();

      /** Live, mutable persisted compound (kept through death and saved with the player). */
      CompoundTag persisted();

      /** Everything that must come back: inventory, armour, offhand, XP, health, food, effects, game mode, position. */
      CompoundTag captureHome();

      /** Empties the player for training (inventory, effects), sets training game mode, heals, lends the course gear. */
      void prepareForTraining(Course course, String nonce);

      /** Moves the player to the course plot. False when the teleport failed (the player did not move). */
      boolean enterPlot(Course course, int slot);

      /** Removes everything the player carries now (only training gear can be on them while a record exists). */
      void wipeTraining();

      /** Puts the home state back; with {@code position} the player is also moved back to where they were. */
      void applyHome(CompoundTag home, boolean position);

      boolean inTraining();
   }

   /** The SavedData backup copy. */
   public interface Backup {
      CompoundTag get(UUID id);

      void put(UUID id, CompoundTag home);

      void remove(UUID id);
   }

   public enum Recovery {
      /** nothing to do */
      NONE,
      /** restored from the player's own record */
      RESTORED,
      /** restored from the SavedData backup (own record missing, player was inside the grounds) */
      RESTORED_BACKUP,
      /** inside the grounds with no record anywhere: training gear removed, caller sends them to their spawn */
      STRANDED,
      /** a backup older than the player's own data was dropped */
      DISCARDED_STALE
   }

   public static boolean hasRecord(Host h) {
      return h.persisted().contains(KEY, 10);
   }

   public static CompoundTag record(Host h) {
      return hasRecord(h) ? h.persisted().getCompound(KEY) : null;
   }

   public static String nonce(Host h) {
      CompoundTag r = record(h);
      return r == null ? "" : r.getString("nonce");
   }

   /**
    * Starts a session: snapshot, both copies written, player emptied and equipped, moved to the plot. If the move
    * fails everything is put back at once. Returns false when nothing changed (already has a record, or failed move).
    */
   public static boolean begin(Host h, Backup b, Course course, int slot, String nonce, long now) {
      if (hasRecord(h) || course == null || nonce == null || nonce.isEmpty()) {
         return false;
      }
      CompoundTag home = h.captureHome();
      home.putString("nonce", nonce);
      home.putInt("course", course.ordinal());
      home.putInt("slot", slot);
      home.putLong("started", now);
      h.persisted().put(KEY, home.copy());
      b.put(h.id(), home.copy());
      h.prepareForTraining(course, nonce);
      if (!h.enterPlot(course, slot)) {
         h.wipeTraining();
         h.applyHome(home, false);
         clear(h, b);
         return false;
      }
      return true;
   }

   /** Regular end of a session: training gear out, home state and position back, both copies cleared. */
   public static boolean finish(Host h, Backup b) {
      CompoundTag home = record(h);
      if (home == null) {
         home = b.get(h.id());
      }
      if (home == null) {
         return false;
      }
      home = home.copy();
      clear(h, b); // cleared first: the move home below fires dimension events that must not see a live record
      h.wipeTraining();
      h.applyHome(home, true);
      return true;
   }

   /** The hunter left the grounds by some other teleport: items and stats back, they stay where they arrived. */
   public static boolean forcedExit(Host h, Backup b) {
      CompoundTag home = record(h);
      if (home == null) {
         home = b.get(h.id());
      }
      if (home == null) {
         return false;
      }
      home = home.copy();
      clear(h, b);
      h.wipeTraining();
      h.applyHome(home, false);
      return true;
   }

   /** Login or respawn without a running session: finish whatever was interrupted. */
   public static Recovery recover(Host h, Backup b) {
      CompoundTag own = record(h);
      if (own != null) {
         CompoundTag home = own.copy();
         clear(h, b);
         h.wipeTraining();
         h.applyHome(home, true);
         return Recovery.RESTORED;
      }
      CompoundTag backup = b.get(h.id());
      if (h.inTraining()) {
         if (backup != null) {
            b.remove(h.id());
            h.wipeTraining();
            h.applyHome(backup.copy(), true);
            return Recovery.RESTORED_BACKUP;
         }
         return Recovery.STRANDED; // no record anywhere: only lent gear is taken (caller), a visitor keeps their own items
      }
      if (backup != null) {
         b.remove(h.id());
         return Recovery.DISCARDED_STALE;
      }
      return Recovery.NONE;
   }

   private static void clear(Host h, Backup b) {
      h.persisted().remove(KEY);
      b.remove(h.id());
   }
}
