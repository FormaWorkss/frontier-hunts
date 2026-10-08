package com.formaworks.frontierhunts.hunts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * [hunts] Pure milestone logic: turns one {@link HuntEvent} into journal counter updates (the checklist entries watching
 * those counters do the rest) and into the named condition tags that expedition contracts and ranger assignments match.
 * No Minecraft classes, so the harness drives it with synthetic events.
 */
public final class HuntTracker {
   /** Read access to the hunter's journal counters. */
   @FunctionalInterface
   public interface Counters {
      int get(String key);
   }

   /** Set counter {@code key} to {@code value} (the bridge applies it as a delta on the journal record). */
   public record Update(String key, int value) {
   }

   public record Result(List<Update> updates, List<HuntBook.Milestone> matched, Set<String> tags) {
      public boolean empty() {
         return this.updates.isEmpty() && this.tags.isEmpty();
      }
   }

   /**
    * Named conditions for contracts ({@link HuntBook.Contract#tag}) and assignments (the {@code hunt} field of an
    * assignment JSON: {@code "<species|*>:<tag>"}, comma separated).
    */
   public static final Map<String, Predicate<HuntEvent>> TAGS = new LinkedHashMap<>();

   static {
      tag("seen", e -> e.kind == HuntEvent.Kind.SEEN || e.kind == HuntEvent.Kind.FLUSH);
      tag("photo", e -> e.kind == HuntEvent.Kind.PHOTO);
      tag("glass", e -> e.kind == HuntEvent.Kind.GLASS);
      tag("glass100", e -> e.kind == HuntEvent.Kind.GLASS && e.distance >= 100.0);
      tag("take", e -> e.kind == HuntEvent.Kind.TAKE);
      tag("clean", e -> e.kind == HuntEvent.Kind.TAKE && e.has(HuntEvent.CLEAN));
      tag("called", e -> e.kind == HuntEvent.Kind.TAKE && e.has(HuntEvent.CALLED));
      tag("night", e -> e.kind == HuntEvent.Kind.TAKE && e.dark());
      tag("bait", e -> e.kind == HuntEvent.Kind.TAKE && e.has(HuntEvent.BAIT));
      tag("spread", e -> e.kind == HuntEvent.Kind.TAKE && e.any(HuntEvent.DECOY | HuntEvent.CALLED));
      tag("wing", e -> e.kind == HuntEvent.Kind.TAKE && e.has(HuntEvent.FLYING));
      tag("onfoot", e -> e.kind == HuntEvent.Kind.TAKE && e.has(HuntEvent.ON_FOOT) && !e.any(HuntEvent.STAND | HuntEvent.BLIND));
      tag("long100", e -> e.kind == HuntEvent.Kind.TAKE && e.distance >= 100.0);
      tag("standblind", e -> e.kind == HuntEvent.Kind.TAKE && e.any(HuntEvent.STAND | HuntEvent.BLIND));
      tag("hound", e -> e.kind == HuntEvent.Kind.TAKE && e.has(HuntEvent.HOUND));
      tag("winter", e -> e.kind == HuntEvent.Kind.TAKE && e.winter());
      // [1.1.6] tags for the extra contract variants (HuntContracts.EXTRA)
      tag("doe", e -> e.kind == HuntEvent.Kind.TAKE && !e.has(HuntEvent.MALE));
      tag("rutbuck", e -> e.kind == HuntEvent.Kind.TAKE && e.has(HuntEvent.MALE) && e.has(HuntEvent.RUT) && e.points >= 8);
      tag("stalk", e -> e.kind == HuntEvent.Kind.TAKE && e.has(HuntEvent.UNAWARE) && e.has(HuntEvent.ON_FOOT) && !e.any(HuntEvent.STAND | HuntEvent.BLIND));
      tag("bow", e -> e.kind == HuntEvent.Kind.TAKE && e.gun == HuntEvent.Gun.BOW);
      tag("trailed", e -> e.kind == HuntEvent.Kind.TAKE && e.has(HuntEvent.RECOVERED) && e.trail >= 30);
      tag("buckcam", e -> e.kind == HuntEvent.Kind.PHOTO && e.has(HuntEvent.MALE));
      tag("glass150", e -> e.kind == HuntEvent.Kind.GLASS && e.distance >= 150.0);
      tag("water", e -> e.kind == HuntEvent.Kind.TAKE && e.has(HuntEvent.WATER));
   }

   private HuntTracker() {
   }

   private static void tag(String name, Predicate<HuntEvent> p) {
      TAGS.put(name, p);
   }

   /** Aux counters of a bag-limit milestone (not shown anywhere): the game day + 1 and the count on that day. */
   public static String dayKey(HuntBook.Milestone m) {
      return "hunt~" + m.species() + "." + m.key() + ".d";
   }

   public static String dayCountKey(HuntBook.Milestone m) {
      return "hunt~" + m.species() + "." + m.key() + ".n";
   }

   /**
    * What this event means for the hunter: counter updates (milestones of its species whose rule it meets; a counter
    * shared by several milestones moves once), the matched milestones, and its condition tags.
    */
   public static Result evaluate(HuntEvent e, Counters c) {
      List<Update> updates = new ArrayList<>();
      List<HuntBook.Milestone> matched = new ArrayList<>();
      HuntBook.Hunt hunt = HuntBook.of(e.species);
      if (hunt != null) {
         Set<String> moved = new LinkedHashSet<>();
         for (HuntBook.Milestone m : hunt.milestones()) {
            if (!m.matches(e)) {
               continue;
            }
            matched.add(m);
            if (!moved.add(m.counter())) {
               continue; // a shared counter (e.g. grouse on the wing 1 / 5) counts this event once
            }
            if (m.mode() == HuntBook.Mode.DAY) {
               int stamp = (int)Math.min(Integer.MAX_VALUE - 1, Math.max(0L, e.day)) + 1;
               int n = c.get(dayKey(m)) == stamp ? c.get(dayCountKey(m)) + 1 : 1;
               updates.add(new Update(dayKey(m), stamp));
               updates.add(new Update(dayCountKey(m), n));
               if (n > c.get(m.counter())) {
                  updates.add(new Update(m.counter(), n));
               }
            } else {
               int cur = c.get(m.counter());
               if (cur < Integer.MAX_VALUE) {
                  updates.add(new Update(m.counter(), cur + 1));
               }
            }
         }
      }
      return new Result(Collections.unmodifiableList(updates), Collections.unmodifiableList(matched), tags(e));
   }

   /** Condition tags of an event: {@code <species>:<tag>} and {@code *:<tag>} for every named condition it meets. */
   public static Set<String> tags(HuntEvent e) {
      Set<String> out = new LinkedHashSet<>();
      for (Map.Entry<String, Predicate<HuntEvent>> t : TAGS.entrySet()) {
         if (t.getValue().test(e)) {
            out.add(e.species + ":" + t.getKey());
            out.add("*:" + t.getKey());
         }
      }
      return Collections.unmodifiableSet(out);
   }

   /** Does an assignment's {@code hunt} spec ("a:b,c:d") accept one of these tags? */
   public static boolean accepts(String spec, Set<String> tags) {
      if (spec == null || spec.isBlank() || tags == null || tags.isEmpty()) {
         return false;
      }
      for (String s : spec.split(",")) {
         if (tags.contains(s.trim())) {
            return true;
         }
      }
      return false;
   }

   /** Is a spec well formed (every part "<known species|*>:<known tag>")? */
   public static boolean validSpec(String spec) {
      if (spec == null || spec.isBlank()) {
         return false;
      }
      for (String s : spec.split(",")) {
         String[] p = s.trim().split(":");
         if (p.length != 2 || !TAGS.containsKey(p[1]) || !p[0].equals("*") && HuntBook.of(p[0]) == null) {
            return false;
         }
      }
      return true;
   }

   /**
    * One-time carry-over of older journal progress for a species (pure): seen -> its sighting milestone, photographed ->
    * its camera milestone, taken -> its first-take milestone, heaviest wildlife (kg x 10) -> its weight milestone.
    * Antler points were never recorded, so deer quality tiers are not carried over. Milestones already done are skipped.
    */
   public static List<Update> carryOver(HuntBook.Hunt h, int seen, int photos, int harvested, int bestKg10, Counters c) {
      List<Update> ups = new ArrayList<>();
      Set<String> moved = new LinkedHashSet<>();
      for (HuntBook.Milestone m : h.milestones()) {
         if (m.target() != 1 || m.mode() != HuntBook.Mode.COUNT || c.get(m.counter()) >= m.target() || moved.contains(m.counter())) {
            continue;
         }
         boolean ok = switch (m.kind()) {
            case SEEN, FLUSH -> seen > 0 && m.tier() == HuntBook.Tier.SCOUT;
            case PHOTO -> photos > 0;
            case TAKE -> harvested > 0 && m.tier() == HuntBook.Tier.TAKE
               || m.tier() == HuntBook.Tier.QUALITY && bestKg10 > 0 && m.test().test(HuntEvent.of(HuntEvent.Kind.TAKE, h.species()).kg(bestKg10 / 10.0).build());
            default -> false;
         };
         if (ok) {
            ups.add(new Update(m.counter(), 1));
            moved.add(m.counter());
         }
      }
      return ups;
   }

   /** Progress of a milestone from the hunter's counters, 0..target. */
   public static int progress(HuntBook.Milestone m, Counters c) {
      return Math.min(m.target(), Math.max(0, c.get(m.counter())));
   }
}
