package com.formaworks.frontierhunts.landscape.ride;

import java.util.function.Consumer;

public final class RideHooks {
   public static volatile Consumer<RidePayloads.HorseState> horseState = var0 -> {
   };

   private RideHooks() {
   }
}
