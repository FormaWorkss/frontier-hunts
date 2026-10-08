package com.formaworks.frontierhunts.hunting.herd;

/** [herds] What a social group is. */
public enum HerdKind {
   /** whitetail doe group (a doe, her yearling daughters and young), elk cow/calf herd, moose cow with her calf */
   FAMILY("family group", "cow herd", "cow with calf"),
   /** bucks / bulls together outside the rut */
   BACHELOR("bachelor group", "bachelor band", "bachelor group"),
   /** one animal on its own (a lone buck, a loner doe); takes no mates while the season keeps it alone */
   SOLO("alone", "alone", "alone");

   private final String deer;
   private final String elk;
   private final String moose;

   HerdKind(String deer, String elk, String moose) {
      this.deer = deer;
      this.elk = elk;
      this.moose = moose;
   }

   public String title(com.formaworks.frontierhunts.hunting.GameSpecies species) {
      return switch (species) {
         case ELK -> this.elk;
         case MOOSE -> this.moose;
         default -> this.deer;
      };
   }

   static HerdKind byOrdinal(int i) {
      HerdKind[] v = values();
      return i >= 0 && i < v.length ? v[i] : SOLO;
   }
}
