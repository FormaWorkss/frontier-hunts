package com.formaworks.frontierhunts.landscape.ride.boat;

import net.minecraft.sounds.SoundEvent;

/** [1.1.0] What {@link BoatSim} needs from the rowboat and the jon boat. */
public interface FrontierBoat {
   BoatSim.Spec spec();

   BoatSim.State sim();

   /** the driver's keys as synced to every client ({@link BoatSim#FWD} ...) */
   int inputBits();

   void setInputBits(int bits);

   SoundEvent bumpSound();
}
