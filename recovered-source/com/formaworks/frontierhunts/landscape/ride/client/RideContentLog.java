package com.formaworks.frontierhunts.landscape.ride.client;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

final class RideContentLog {
   private static final Logger LOG = LogUtils.getLogger();

   private RideContentLog() {
   }

   static void warn(String var0, Throwable var1) {
      LOG.warn(var0, var1);
   }
}
