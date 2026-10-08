package com.formaworks.frontierhunts.wildlife2026;

public enum WildlifeSpecies {
   GROUSE("grouse", 0.24F, 0.32F, 8.0, 0.23, false, true, 7951160, 0.7442F),
   DUCK("duck", 0.3F, 0.38F, 10.0, 0.22, false, true, 4223816, 0.7600F),
   BOAR("boar", 0.77F, 0.9F, 30.0, 0.27, true, false, 4798250, 0.9000F),
   COYOTE("coyote", 0.55F, 0.75F, 20.0, 0.32, false, false, 10190936, 0.9146F),
   WOLF("wolf", 0.68F, 0.95F, 28.0, 0.3, true, false, 9210240, 0.9048F),
   PRONGHORN("pronghorn", 0.73F, 1.3F, 26.0, 0.34, false, false, 13075523, 1.0400F),
   COUGAR("cougar", 0.69F, 0.9F, 32.0, 0.32, true, false, 10582861, 0.8571F),
   ELK("elk", 1.15F, 2.14F, 44.0, 0.31, false, false, 9266235, 1.0F),
   MOOSE("moose", 1.25F, 2.19F, 58.0, 0.29, true, false, 4797220, 1.0F),
   GRIZZLY("grizzly", 1.05F, 1.25F, 50.0, 0.27, true, false, 7950643, 0.8333F),
   BLACK_BEAR("black_bear", 0.81F, 1.05F, 38.0, 0.28, true, false, 2499101, 0.8077F),
   POLAR_BEAR("polar_bear", 1.1F, 1.45F, 56.0, 0.27, true, false, 14473155, 0.8529F),
   BISON("bison", 1.27F, 1.9F, 60.0, 0.26, true, false, 5650725, 0.9744F),
   LION("lion", 0.93F, 1.3F, 42.0, 0.3, true, false, 12818777, 0.9286F),
   PANTHER("panther", 0.64F, 0.85F, 32.0, 0.32, true, false, 2696479, 0.8500F),
   CHEETAH("cheetah", 0.67F, 0.95F, 25.0, 0.38, false, false, 13476429, 0.9500F);

   public final String id;
   public final float width;
   public final float height;
   public final double health;
   public final double speed;
   public final boolean defensive;
   public final boolean bird;
   public final int color;
   /** scale for the Classic box model, which was built for the earlier (larger) hitbox */
   public final float classicScale;

   private WildlifeSpecies(String id, float width, float height, double health, double speed, boolean defensive, boolean bird, int color, float classicScale) {
      this.classicScale = classicScale;
      this.id = id;
      this.width = width;
      this.height = height;
      this.health = health;
      this.speed = speed;
      this.defensive = defensive;
      this.bird = bird;
      this.color = color;
   }

   public boolean existing() {
      return this == ELK || this == MOOSE;
   }

   public static WildlifeSpecies find(String id) {
      for (WildlifeSpecies s : values()) {
         if (s.id.equals(id)) {
            return s;
         }
      }

      return WOLF;
   }
}
