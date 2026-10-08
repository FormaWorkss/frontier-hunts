package com.formaworks.frontierhunts.sign.work;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * [deersign] What a buck is doing at a sign site, as every client sees it: the kind of work, the game tick it started,
 * the sign block it works on, a motion seed and the phases with their lengths. The server sets it once when the buck
 * starts working (and clears it when he stops); clients derive every frame of the animation, the strokes, the sounds
 * and the bark/soil particles from it, so all observers see and hear the same thing without any per-stroke traffic.
 */
public record SignAct(int kind, long start, BlockPos sign, int seed, int[] phases, int[] ticks) {
   public static final int RUB = 1;
   public static final int SCRAPE = 2;
   public static final int REVISIT = 3;

   /** nose to the trunk before rubbing */
   public static final int SNIFF_TREE = 1;
   /** antlers against the trunk, rubbing strokes */
   public static final int RUB_STROKES = 2;
   /** nose to the ground over the scrape spot */
   public static final int SNIFF_GROUND = 3;
   /** pawing the ground with the front hooves */
   public static final int PAW = 4;
   /** working the licking branch overhead (forehead, antlers, mouth) */
   public static final int LICK = 5;
   /** rub-urination: hocks together over the scrape */
   public static final int URINATE = 6;

   public static final SignAct NONE = new SignAct(0, 0L, BlockPos.ZERO, 0, new int[0], new int[0]);

   public boolean active() {
      return this.kind != 0 && this.phases.length > 0;
   }

   public int total() {
      int n = 0;
      for (int t : this.ticks) {
         n += t;
      }
      return n;
   }

   /** Phase at {@code tick} ticks after the start: {phase id, ticks into it, its length}; phase 0 before/after. */
   public int[] at(float tick) {
      if (tick < 0.0F) {
         return new int[]{0, 0, 1};
      }
      float t = tick;
      for (int i = 0; i < this.phases.length; i++) {
         if (t < this.ticks[i]) {
            return new int[]{this.phases[i], (int)t, this.ticks[i]};
         }
         t -= this.ticks[i];
      }
      return new int[]{0, 0, 1};
   }

   /** Start of phase {@code id} in ticks after the act start, or -1. */
   public int phaseStart(int id) {
      int t = 0;
      for (int i = 0; i < this.phases.length; i++) {
         if (this.phases[i] == id) {
            return t;
         }
         t += this.ticks[i];
      }
      return -1;
   }

   public int phaseTicks(int id) {
      for (int i = 0; i < this.phases.length; i++) {
         if (this.phases[i] == id) {
            return this.ticks[i];
         }
      }
      return 0;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      if (!this.active()) {
         return tag;
      }
      tag.putInt("k", this.kind);
      tag.putLong("t", this.start);
      tag.putLong("p", this.sign.asLong());
      tag.putInt("s", this.seed);
      tag.putIntArray("ph", this.phases);
      tag.putIntArray("d", this.ticks);
      return tag;
   }

   /** Reads a synced tag; anything malformed reads as no work. */
   public static SignAct load(CompoundTag tag) {
      if (tag == null || !tag.contains("k")) {
         return NONE;
      }
      int kind = tag.getInt("k");
      int[] ph = tag.getIntArray("ph");
      int[] d = tag.getIntArray("d");
      if (kind < RUB || kind > REVISIT || ph.length == 0 || ph.length != d.length || ph.length > 8) {
         return NONE;
      }
      for (int i = 0; i < d.length; i++) {
         if (d[i] < 1 || d[i] > 1200 || ph[i] < SNIFF_TREE || ph[i] > URINATE) {
            return NONE;
         }
      }
      return new SignAct(kind, tag.getLong("t"), BlockPos.of(tag.getLong("p")), tag.getInt("s"), ph, d);
   }
}
