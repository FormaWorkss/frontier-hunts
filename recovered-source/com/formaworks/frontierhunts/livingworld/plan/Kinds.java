package com.formaworks.frontierhunts.livingworld.plan;

import com.formaworks.frontierhunts.livingworld.plan.kinds.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** [livingworld] All world-generated site kinds by id (the structure ids, frontierhunts:&lt;id&gt;). */
public final class Kinds {
   public static final Map<String, Kind> ALL = new LinkedHashMap<>();

   static {
      add(new HuntingCamp());
      add(new AbandonedCamp());
      add(new ElkCamp());
      add(new OutfitterPost());
      add(new RangerStation());
      add(new TrapperCabin());
      add(new MeatShed());
      add(new Trailhead());
      add(new StandLine()); // legacy: kept so saved stand_line pieces still rebuild; no longer generated
      add(new TreeStand()); // [structures2]
      add(new GroundBlindPlot());
      add(new DuckBlind());
      add(new GlassingPoint());
      add(new FenceLine());
      add(new AntlerCache());
   }

   /** [villages] kinds that still generate in new chunks (tent camps and the tree stand); the building kinds were retired
    *  from world generation - they stay registered only so pieces saved in older worlds still rebuild, and for /place */
   public static final java.util.Set<String> GENERATED = java.util.Set.of("hunting_camp", "elk_camp", "tree_stand");

   private Kinds() {
   }

   static void add(Kind k) {
      ALL.put(k.id, k);
   }

   public static Kind get(String id) {
      return ALL.get(id);
   }
}
