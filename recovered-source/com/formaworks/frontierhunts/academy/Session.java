package com.formaworks.frontierhunts.academy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** [academy] One live training session (server memory only; the persistent part is the home record). */
final class Session {
   enum Phase {
      ACTIVE,
      PASSED,
      FAILED,
      LEAVING
   }

   enum Result {
      PASSED,
      ABANDONED,
      TIMEOUT,
      HURT,
      INTERRUPTED
   }

   final UUID player;
   final Course course;
   final CourseKit kit;
   final int slot;
   final BlockPos origin;
   final String nonce;
   final long started;
   final long deadline;
   final int[] progress;
   final List<UUID> entities = new ArrayList<>();
   final List<UUID> marks = new ArrayList<>();
   final List<ChunkPos> tickets = new ArrayList<>();
   final List<BlockPos> banners = new ArrayList<>();
   Phase phase = Phase.ACTIVE;
   int phaseTicks;
   Result result;
   int attempts;
   Object state;
   boolean hudDirty = true;
   int hudFlags;
   long lastHud = -1000L;
   long lastNote = -1000L;
   int ticks;

   Session(UUID player, Course course, CourseKit kit, int slot, BlockPos origin, String nonce, long started) {
      this.player = player;
      this.course = course;
      this.kit = kit;
      this.slot = slot;
      this.origin = origin;
      this.nonce = nonce;
      this.started = started;
      this.deadline = started + course.timeLimit;
      this.progress = new int[course.targets.length];
   }

   /** Adds to an objective (clamped to its target). True when it changed. */
   boolean bump(int i, int amount) {
      return this.set(i, this.progress[i] + amount);
   }

   boolean set(int i, int value) {
      int v = Math.clamp(value, 0, this.course.targets[i]);
      if (v == this.progress[i]) {
         return false;
      }
      this.progress[i] = v;
      this.hudDirty = true;
      return true;
   }

   boolean done(int i) {
      return this.progress[i] >= this.course.targets[i];
   }

   boolean complete() {
      return this.course.complete(this.progress);
   }

   void resetProgress() {
      for (int i = 0; i < this.progress.length; i++) {
         this.progress[i] = 0;
      }
      this.hudDirty = true;
   }

   Vec3 world(double x, double dy, double z) {
      return new Vec3(this.origin.getX() + x, this.origin.getY() + dy, this.origin.getZ() + z);
   }

   BlockPos block(int x, int dy, int z) {
      return this.origin.offset(x, dy, z);
   }

   /** Local x/z of a world position. */
   double lx(Vec3 w) {
      return w.x - this.origin.getX();
   }

   double lz(Vec3 w) {
      return w.z - this.origin.getZ();
   }
}
