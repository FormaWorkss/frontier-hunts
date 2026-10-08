package com.formaworks.frontierhunts.hunts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/** [hunts] The reward line of a milestone ("+90 XP · 20 tokens · 3× Scent Cover · Title: Elk Caller"), side-neutral. */
public final class HuntRewardsText {
   private HuntRewardsText() {
   }

   /**
    * @param itemName item id -> display name
    * @param tr       lang key + args -> text
    */
   public static String line(HuntBook.Milestone m, Function<String, String> itemName, BiFunction<String, Object[], String> tr) {
      List<String> parts = new ArrayList<>();
      if (m.xp() > 0) {
         parts.add(tr.apply("hunts.frontierhunts.reward.xp", new Object[]{m.xp()}));
      }
      int tokens = HuntRewards.tokens(m);
      if (tokens > 0) {
         parts.add(tr.apply("hunts.frontierhunts.reward.tokens", new Object[]{tokens}));
      }
      if (!m.item().isEmpty() && m.count() > 0) {
         parts.add(m.count() > 1 ? m.count() + "× " + itemName.apply(m.item()) : itemName.apply(m.item()));
      }
      if (m.master()) {
         parts.add(tr.apply("hunts.frontierhunts.reward.title", new Object[]{tr.apply(HuntBook.titleKey(m.species()), new Object[0])}));
      }
      return String.join(" · ", parts);
   }
}
