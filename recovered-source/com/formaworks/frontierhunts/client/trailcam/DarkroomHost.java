package com.formaworks.frontierhunts.client.trailcam;

import net.minecraft.core.BlockPos;

/**
 * A screen the darkroom may develop behind. It must cover the whole window with opaque pixels, because each
 * developing frame renders the world from the trail camera's lens instead of the player's eyes.
 */
public interface DarkroomHost {
   /** Console block (camera or hub) the screen was opened from, for requests. */
   BlockPos console();

   /** Camera whose photos may be fetched over the lens uplink (null: never open an uplink from this screen). */
   BlockPos uplinkCamera();

   /** [phone] Is the window covered right now (the Field Phone only while its Trail Cams gallery is up)? */
   default boolean develops() {
      return true;
   }
}
