package com.formaworks.frontierhunts.hunting.routine;

import com.formaworks.frontierhunts.hunting.GameSpecies;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;

/**
 * One herd's home range: a bedding thicket, a feeding area and (optionally) a drinking spot, plus the game trails the
 * herd has worn between them. Shared by every member of the herd (stored in {@link RoutineStore}).
 *
 * Trails are stored one-way (0: bed-&gt;feed, 1: bed-&gt;water, 2: water-&gt;feed) as ground positions packed with
 * {@link BlockPos#asLong()}; the opposite trip walks the same line backwards.
 */
public final class HomeRange {
   public static final int BED = 0;
   public static final int FEED = 1;
   public static final int WATER = 2;
   public static final String[] NAMES = {"bedding", "feeding", "water"};
   public final UUID id;
   public GameSpecies species;
   /** where the range was centred when it was (re)established */
   public BlockPos origin;
   public final BlockPos[] anchors = new BlockPos[3];
   /** block of water the animals drink from (aim point); anchors[WATER] is the shore stand */
   public BlockPos waterBlock;
   public final long[][] trails = new long[3][];
   public long lastSeen;
   public long establishedAt;
   public long relocatedAt = Long.MIN_VALUE / 4;
   public int relocations;
   public boolean hasDoes;
   /** 0 = never searched, 1 = searching, 2 = ready, 3 = no usable cover here (retry later) */
   public int status;
   public long retryAt;
   /** transient: the incremental anchor search, run by whichever member ticks first */
   transient AnchorSearch search;

   public HomeRange(UUID id, GameSpecies species, BlockPos origin) {
      this.id = id;
      this.species = species;
      this.origin = origin.immutable();
   }

   public boolean ready() {
      return this.status == 2 && this.anchors[BED] != null && this.anchors[FEED] != null;
   }

   public BlockPos anchor(int which) {
      return which >= 0 && which < 3 ? this.anchors[which] : null;
   }

   /** Trail index for a leg and whether it is walked backwards. Returns -1 when the pair has no trail slot. */
   public static int trailFor(int from, int to) {
      if (from == to || from < 0 || to < 0) {
         return -1;
      } else if (from == BED && to == FEED || from == FEED && to == BED) {
         return 0;
      } else {
         return from != BED && to != BED ? 2 : 1;
      }
   }

   /** True if a leg walks its stored trail backwards. */
   public static boolean reversed(int from, int to) {
      return from == FEED && to == BED || from == WATER && to == BED || from == FEED && to == WATER;
   }

   public void invalidateAnchor(int which) {
      this.anchors[which] = null;
      if (which == WATER) {
         this.waterBlock = null;
      }

      for (int from = 0; from < 3; from++) {
         int t = trailFor(from, which);
         if (t >= 0) {
            this.trails[t] = null;
         }
      }
   }

   public CompoundTag save() {
      CompoundTag t = new CompoundTag();
      t.putUUID("id", this.id);
      t.putString("species", this.species.id);
      t.putLong("origin", this.origin.asLong());

      for (int i = 0; i < 3; i++) {
         if (this.anchors[i] != null) {
            t.putLong("a" + i, this.anchors[i].asLong());
         }

         if (this.trails[i] != null) {
            t.put("t" + i, new LongArrayTag(this.trails[i]));
         }
      }

      if (this.waterBlock != null) {
         t.putLong("water", this.waterBlock.asLong());
      }

      t.putLong("seen", this.lastSeen);
      t.putLong("since", this.establishedAt);
      t.putLong("moved", this.relocatedAt);
      t.putInt("relocations", this.relocations);
      t.putBoolean("does", this.hasDoes);
      t.putInt("status", this.status == 1 ? 0 : this.status);
      t.putLong("retry", this.retryAt);
      return t;
   }

   public static HomeRange load(CompoundTag t) {
      if (!t.hasUUID("id")) {
         return null;
      } else {
         GameSpecies sp = GameSpecies.byId(t.getString("species"));
         HomeRange r = new HomeRange(t.getUUID("id"), sp == null ? GameSpecies.WHITETAIL : sp, BlockPos.of(t.getLong("origin")));

         for (int i = 0; i < 3; i++) {
            if (t.contains("a" + i, Tag.TAG_LONG)) {
               r.anchors[i] = BlockPos.of(t.getLong("a" + i));
            }

            if (t.contains("t" + i, Tag.TAG_LONG_ARRAY)) {
               long[] trail = t.getLongArray("t" + i);
               r.trails[i] = trail.length >= 2 && trail.length <= RoutineTrails.MAX_POINTS ? trail : null;
            }
         }

         if (t.contains("water", Tag.TAG_LONG)) {
            r.waterBlock = BlockPos.of(t.getLong("water"));
         }

         r.lastSeen = t.getLong("seen");
         r.establishedAt = t.getLong("since");
         r.relocatedAt = t.contains("moved") ? t.getLong("moved") : Long.MIN_VALUE / 4;
         r.relocations = t.getInt("relocations");
         r.hasDoes = t.getBoolean("does");
         r.status = Math.clamp((long)t.getInt("status"), 0, 3);
         r.retryAt = t.getLong("retry");
         if (r.status == 2 && (r.anchors[BED] == null || r.anchors[FEED] == null)) {
            r.status = 0;
         }

         return r;
      }
   }
}
