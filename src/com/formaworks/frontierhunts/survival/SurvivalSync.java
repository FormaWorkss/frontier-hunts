package com.formaworks.frontierhunts.survival;

/**
 * [survival] Server switches as both sides see them. The server reads its config; a client uses what the server last
 * sent in the state payload (its own config copy may differ on a dedicated server).
 */
public final class SurvivalSync {
   private static volatile int clientMode = SurvivalMath.Mode.OFF.ordinal();
   private static volatile boolean clientSpoilage;
   private static volatile boolean clientTemperature;

   private SurvivalSync() {
   }

   public static SurvivalMath.Mode mode(boolean client) {
      return client ? SurvivalMath.Mode.byId(clientMode) : SurvivalConfig.mode();
   }

   public static boolean spoilage(boolean client) {
      return client ? clientSpoilage && clientMode != 0 : SurvivalConfig.spoilage();
   }

   public static boolean temperature(boolean client) {
      return client ? clientTemperature && clientMode != 0 : SurvivalConfig.temperature();
   }

   static void setClient(int mode, boolean spoilage, boolean temperature) {
      clientMode = mode;
      clientSpoilage = spoilage;
      clientTemperature = temperature;
   }

   public static void clearClient() {
      setClient(0, false, false);
   }
}
