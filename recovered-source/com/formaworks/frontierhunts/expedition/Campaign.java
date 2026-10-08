package com.formaworks.frontierhunts.expedition;

import java.util.List;

public final class Campaign {
   public static final List<String> IDS = List.of(
      "first_light", "read_ground", "first_harvest", "valley_equipment", "clean_shot", "watershed_survey", "quiet_approach", "herd_management", "homeward"
   );
   public static final int CHAPTERS = 3;
   public static final List<String> DEBRIEFS = List.of(
      "Three habitats, three different sets of signs. Mara marks the first reliable observations on the lodge map.",
      "Tracks turn an empty forest into a record of movement. Keep the wind in mind as you follow the next trail.",
      "The first recovered deer is entered in the field ledger. Its trophy preserves the details of that animal and the shot.",
      "A prepared kit is more useful than a hurried shot. Check ammunition and choose a route back before leaving camp.",
      "A heart or lung shot drops a deer where it stands. Mara notes the placement for the next hunters.",
      "The watershed record now includes catches as well as tracks along its banks.",
      "A living observation is added to the reserve record. Some assignments are resolved with patience rather than a trophy.",
      "The herd count is balanced for the season. New reports are arriving from the high country.",
      "Mara closes the season ledger. The lodge is open, the trails remain, and contracts are yours to revisit with friends."
   );
   public static final List<Campaign.Mission> MISSIONS = List.of(
      new Campaign.Mission("I · First light", "Ranger Mara needs a survey of the reserve. Visit three different biomes.", "visit", "*", 3, 25),
      new Campaign.Mission("Read the ground", "Inspect a real animal clue with the tracking key before your first hunt.", "clue", "*", 1, 20),
      new Campaign.Mission("The first harvest", "Recover and field-dress a whitetail with your Contour Skinning Knife.", "harvest", "whitetail", 1, 35),
      new Campaign.Mission("II · Equipment for the valley", "Make a piece of equipment at one of your three benches." /* [benches] */, "craft", "*", 1, 25),
      new Campaign.Mission(
         "A clean shot",
         "Drop a whitetail with a heart or lung shot, then recover it. Glass the deer with your binoculars and wait for a clean broadside first.",
         "vital",
         "whitetail",
         1,
         40
      ),
      new Campaign.Mission("Watershed survey", "Land two living fish with your float rod or bowfishing rig.", "fish", "*", 2, 35),
      new Campaign.Mission(
         "III · A quiet approach",
         "Use a tranquilizer rifle to survey a whitetail without harvesting it. Each deer records a survey only once per hunter.",
         "survey",
         "*",
         1,
         40
      ),
      new Campaign.Mission("Herd management", "Recover two more whitetails to balance the reserve herd.", "harvest", "whitetail", 2, 45),
      new Campaign.Mission(
         "Homeward", "Place a recovered trophy on a trophy plinth. The reserve keeps its stories through the things hunters bring home.", "display", "*", 1, 75
      )
   );
   public static final List<Campaign.Mission> CONTRACTS = withSpecies(List.of(
      new Campaign.Mission("Camp provisions", "Recover and field-dress 2 whitetails before the clock runs out - meat for the camp.", "harvest", "whitetail", 2, 40),
      new Campaign.Mission("Long shot", "Recover a whitetail shot cleanly from at least 150 metres - range it, rest the rifle, wait for broadside.", "longshot", "whitetail", 1, 60),
      new Campaign.Mission("Clean kill", "Take a whitetail with a heart or lung shot and recover it before the clock runs out.", "vital", "whitetail", 1, 35),
      new Campaign.Mission("River delivery", "Land 5 fish for the camp kitchen before the clock runs out.", "fish", "*", 5, 20),
      new Campaign.Mission("Group hunt", "Recover a whitetail with a party member within 96 metres.", "group", "whitetail", 1, 50)
   ));

   /** [hunts] the five standing contracts, then the rotating species contracts (hunts.HuntContracts; saved indices stay valid). */
   private static List<Campaign.Mission> withSpecies(List<Campaign.Mission> base) {
      java.util.ArrayList<Campaign.Mission> all = new java.util.ArrayList<>(base);
      all.addAll(com.formaworks.frontierhunts.hunts.HuntContracts.MISSIONS);
      return List.copyOf(all);
   }

   public static String id(int var0) {
      return var0 >= MISSIONS.size() ? "complete" : IDS.get(Math.max(0, var0));
   }

   public static int index(String var0, int var1) {
      if (var0.equals("complete")) {
         return MISSIONS.size();
      } else {
         int var2 = IDS.indexOf(var0);
         return var2 >= 0 ? var2 : Math.clamp((long)var1, 0, MISSIONS.size());
      }
   }

   public static int chapter(int var0) {
      return var0 < 3 ? 1 : (var0 < 6 ? 2 : 3);
   }

   public static boolean matches(Campaign.Mission var0, String var1, String var2) {
      return var0.event.equals(var1) && (var0.species.equals("*") || var0.species.equals(var2));
   }

   private Campaign() {
   }

   public static record Mission(String title, String story, String event, String species, int amount, int tokens) {
   }
}
