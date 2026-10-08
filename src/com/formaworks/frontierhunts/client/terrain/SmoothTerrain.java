package com.formaworks.frontierhunts.client.terrain;

/**
 * Retired. Smooth terrain has been removed from Frontier Hunts: the ground is always drawn and
 * collides as normal Minecraft blocks. Kept only as an inert name; nothing calls it.
 */
@Deprecated
public final class SmoothTerrain {
   private SmoothTerrain() {
   }

   public static boolean active() {
      return false;
   }

   public static void tick() {
   }
}
