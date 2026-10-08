package com.formaworks.frontierhunts.phone.client.ui;

/**
 * [phone] What the phone UI can ask of the game. In game every call becomes a packet to the server (which decides) or a
 * client-side effect (sounds, screenshots); offline the mock records nothing.
 */
public interface PhoneActions {
   enum Sfx {
      TAP,
      OPEN,
      CLOSE,
      BACK,
      TOGGLE,
      NOTIFY,
      ERROR,
      SUCCESS,
      KEY,
      SHUTTER,
      CHESS_MOVE,
      CHESS_CAPTURE,
      CHESS_CHECK,
      DICE_ROLL,
      DICE_HOLD,
      DICE_SCORE,
      FLUSH_SHOT,
      FLUSH_HIT,
      FLUSH_FLUSH,
      FLUSH_RELOAD,
      FLUSH_WARN,
      WIN,
      LOSE,
      MESSAGE_IN,
      MESSAGE_OUT,
      UNLOCK,
      ALARM
   }

   int R_WEATHER = 0;
   int R_PLACES = 1;
   int R_WALLET = 2;
   int R_CONTRACTS = 3;
   int R_CAMS = 4;
   int R_BOARD = 5;
   int R_GAMES = 6;
   int R_MESSAGES = 7;

   int C_MISSION_CLAIM = 0;
   int C_CONTRACT_ACCEPT = 1;
   int C_CONTRACT_CLAIM = 2;
   int C_CONTRACT_ABANDON = 3;
   int C_ASSIGN_ACCEPT = 4;
   int C_ASSIGN_CLAIM = 5;
   int C_ASSIGN_CANCEL = 6;

   void sound(Sfx s);

   /** milliseconds for animation */
   long millis();

   void close();

   void refresh(int what);

   void setFlashlight(boolean on);

   /** sets the alarm (minute of day) or switches it off (-1) */
   void setAlarm(int minuteOfDay);

   /** the ringing alarm: stop it (it rings again tomorrow) or snooze ten minutes */
   void alarmAnswer(boolean snooze);

   void settingsChanged();

   /** The trail camera gallery is on screen and covers it: photos may develop behind the phone. */
   void darkroom(boolean on);

   // ------------------------------------------------------------------------------------------------ trail cameras

   void camHub();

   void camRoll(long pos);

   void camClear(long pos);

   void camWatch(long pos);

   void camDelete(long pos, PhoneModel.PhotoRef photo);

   void camSave(String label, PhoneModel.PhotoRef photo);

   void camPrioritise(PhoneModel.PhotoRef photo);

   // ------------------------------------------------------------------------------------------------ map

   void addPin(String name, int icon);

   void removePlace(String id);

   void navigate(String id);

   /** "pick up" the next refresh of the map texture around the player */
   void mapWanted(boolean on);

   // ------------------------------------------------------------------------------------------------ contracts

   void contract(int op, int index, String arg);

   // ------------------------------------------------------------------------------------------------ calls

   void playCall(String soundId);

   // ------------------------------------------------------------------------------------------------ games

   void invite(int game, String player);

   void answerInvite(long id, boolean accept);

   void move(long session, int[] move);

   void leave(long session);

   /** Ask for (or take) a rematch of a finished online game. */
   void rematch(long session);

   void flushStart();

   void flushSubmit(long token, int[] shots);

   void flushProgress(long session, int score, int tick);

   void gameStat(int game, int mode, int result);

   // ------------------------------------------------------------------------------------------------ messages

   /** Texts another hunter (the server checks and delivers). */
   default void message(String to, String text) {
   }

   /** The thread with {@code peer} was read. */
   default void messageRead(String peer) {
   }

   // ------------------------------------------------------------------------------------------------ [1.4.0] camera

   /** Puts the phone up as a camera (the screen closes, the hunter walks and frames); {@code selfie} the front camera.
    * {@code sendTo}: a hunter the next photo is for (asked before it's sent), or null. */
   default void camera(boolean selfie, String sendTo) {
   }

   /** Lists the gallery again. */
   default void photosRefresh() {
   }

   default void photoDelete(PhoneModel.Shot shot) {
   }

   /** Texts the photo to a hunter (a small copy goes to the server first). */
   default void photoSend(String to, PhoneModel.Shot shot) {
   }

   /** Shows the photo folder on this computer. */
   default void photoFolder() {
   }
}
