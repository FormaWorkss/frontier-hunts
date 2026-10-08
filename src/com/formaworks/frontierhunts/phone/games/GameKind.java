package com.formaworks.frontierhunts.phone.games;

/** [phone] The phone's games and their modes, shared by the server and the phone. */
public final class GameKind {
   public static final int CHESS = 0;
   public static final int DICE = 1;
   public static final int FLUSH = 2;
   public static final int COUNT = 3;
   public static final String[] TITLES = {"Lodge Chess", "Hunter's Dice", "Flush!"};

   public static final int MODE_SOLO = 0;
   public static final int MODE_AI = 1;
   public static final int MODE_ONLINE = 2;

   public static final int WIN = 0;
   public static final int LOSS = 1;
   public static final int DRAW = 2;

   /** stats layout: per game {ai wins, ai losses, ai draws, online wins, online losses, online draws} */
   public static int stat(int game, int mode, int result) {
      return game * 6 + (mode == MODE_ONLINE ? 3 : 0) + Math.max(0, Math.min(2, result));
   }

   private GameKind() {
   }
}
