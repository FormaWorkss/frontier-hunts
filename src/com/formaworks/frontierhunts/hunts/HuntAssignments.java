package com.formaworks.frontierhunts.hunts;

import com.formaworks.frontierhunts.camps.Quarry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * [hunts] Ranger field assignments driven by the species hunts: data files under
 * {@code data/frontierhunts/frontierhunts/assignment/} with a {@code "hunt"} condition ("coyote:called,wolf:called").
 * This builds the plain objective line the dossier shows (Assignment.instruction, English like the standing ones).
 */
public final class HuntAssignments {
   private static final Map<String, String> PHRASE = new LinkedHashMap<>();

   static {
      PHRASE.put("take", "");
      PHRASE.put("clean", " with a heart or lung shot");
      PHRASE.put("called", " that came to your call");
      PHRASE.put("night", " between dusk and dawn");
      PHRASE.put("bait", " over your bait");
      PHRASE.put("spread", " over your decoys or to your duck call");
      PHRASE.put("wing", " on the wing");
      PHRASE.put("onfoot", " on foot, without a stand or blind");
      PHRASE.put("long100", " from 100 m or more");
      PHRASE.put("standblind", " from a tree stand or blind");
      PHRASE.put("hound", " with your hound on the trail");
      PHRASE.put("winter", " in winter");
   }

   private HuntAssignments() {
   }

   public static String instruction(String spec, int target) {
      if (!HuntTracker.validSpec(spec)) {
         return "Complete " + target + " hunt objective(s).";
      }
      Set<String> species = new LinkedHashSet<>();
      Set<String> tags = new LinkedHashSet<>();
      for (String s : spec.split(",")) {
         String[] p = s.trim().split(":");
         species.add(p[0]);
         tags.add(p[1]);
      }
      String tag = tags.iterator().next();
      String who = who(species, target);
      return switch (tag) {
         case "photo" -> "Get " + who + " on a trail camera" + (target > 1 ? " " + target + " times." : ".");
         case "glass", "glass100" -> "Glass " + target + " " + (species.contains("*") ? (target == 1 ? "animal" : "animals") : who) + (tag.equals("glass100")
            ? " at 100 m or more" : "") + " with binoculars or a rangefinder.";
         case "seen" -> "Get within 40 m of " + who + " and see " + (target == 1 ? "it" : "them") + " clearly.";
         default -> "Take " + target + " " + who + PHRASE.getOrDefault(tag, "") + ".";
      };
   }

   private static String who(Set<String> species, int n) {
      if (species.contains("*")) {
         return n == 1 ? "animal" : "animals";
      }
      List<String> names = new ArrayList<>();
      for (String s : species) {
         Quarry q = Quarry.find(s);
         String t = q == null ? s : q.title.toLowerCase(Locale.ROOT);
         boolean same = t.endsWith("s") || t.endsWith("bison") || t.endsWith("moose") || t.endsWith("elk") || t.endsWith("grouse");
         names.add(n == 1 || same ? t : (t.endsWith("f") ? t.substring(0, t.length() - 1) + "ves" : t + "s"));
      }
      if (names.size() == 1) {
         return names.get(0);
      }
      return String.join(", ", names.subList(0, names.size() - 1)) + " or " + names.get(names.size() - 1);
   }
}
