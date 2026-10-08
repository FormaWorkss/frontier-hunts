package com.formaworks.frontierhunts.wildlife2026;

import java.util.Locale;
import java.util.Set;

/**
 * [gear20] Which animals are finished. Whitetail, elk, moose, ruffed grouse and mallard spawn in the wild and get hunt
 * contracts. Every other species is a beta: still in the game as a spawn egg (creative "Frontier Hunts: Wildlife" tab,
 * /summon) so it can be tried, but it does not spawn naturally and its contracts are not posted. Eggs, tags and hunt
 * cards say so.
 */
public final class Beta {
   private static final Set<String> FINISHED = Set.of("whitetail", "elk", "moose", "grouse", "duck");
   private static final Set<String> BETA = Set.of("cougar", "wolf", "coyote", "pronghorn", "boar", "grizzly", "black_bear", "polar_bear", "bison",
      "lion", "panther", "cheetah");
   /** licence tags that only cover beta animals */
   private static final Set<String> BETA_TAGS = Set.of("pronghorn_tag", "bison_tag", "bear_tag", "cat_tag");

   private Beta() {
   }

   /** True for a beta species id ("cougar", "frontierhunts:cougar", "wildlife2026/cougar"). */
   public static boolean animal(String id) {
      if (id == null) {
         return false;
      }
      String s = id.toLowerCase(Locale.ROOT);
      s = s.substring(Math.max(s.lastIndexOf(':'), s.lastIndexOf('/')) + 1);
      return BETA.contains(s);
   }

   public static boolean finished(String id) {
      return id != null && FINISHED.contains(id.toLowerCase(Locale.ROOT));
   }

   /** Item path ("cougar_spawn_egg", "bear_tag") of a spawn egg or licence tag for a beta animal. */
   public static boolean item(String path) {
      if (path.endsWith("_spawn_egg")) {
         return animal(path.substring(0, path.length() - "_spawn_egg".length()));
      }
      return BETA_TAGS.contains(path);
   }
}
