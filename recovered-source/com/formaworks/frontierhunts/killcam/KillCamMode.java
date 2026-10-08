package com.formaworks.frontierhunts.killcam;

/** When the kill cam plays (client setting). Order matters: sent to the server as the ordinal. */
public enum KillCamMode {
   /** Never. */
   OFF,
   /** Every shot that drops the animal on the spot. */
   LETHAL,
   /** Only lethal shots on trophy-class animals (Silver-grade racks and better, big predators, bison). */
   TROPHY;

   public static KillCamMode byOrdinal(int i) {
      KillCamMode[] v = values();
      return i >= 0 && i < v.length ? v[i] : LETHAL;
   }
}
