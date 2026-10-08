package com.formaworks.frontierhunts.phone.client.ui;

/**
 * [phone] The phone's glyphs: white symbols on the {@code glyphs} sheet (16 columns of 96 px cells), drawn tinted. The
 * sheet is painted by {@code tools/phone/art.py}, which reads this enum's order, so the two never drift apart.
 */
public enum G {
   BACK, FORWARD, CLOSE, PLUS, MINUS, CHECK, MORE, REFRESH, TRASH, SAVE, GEAR, INFO, WARN, LOCK, BOLT, FLASHLIGHT,
   NIGHT, BELL, BELL_OFF, ALARM, PIN, FLAG, TENT, LODGE, BOARD, RANGER, CAMERA, HOME, TARGET, COMPASS, ARROW, RECENTER,
   SUN, MOON, CLOUD, RAIN, SNOW, WIND, FOG, THERMO, DROP, SUNRISE, SUNSET, EYE, CLIPBOARD, TOKEN, TIMER, ANTLER,
   STAR, TRACK, FISH, CROSSHAIR, CARD, TAG, FEATHER, SHIELD, KNIGHT, DIE, DUCK, CUP, PERSON, CHIP, GLOBE, PUZZLE,
   RESIGN, HANDSHAKE, UNDO, PLAY, PAUSE, SPEAKER, STOP, LAYERS, MAP, LIST, SIGNAL, EDIT, EXPAND, HOURGLASS, STORM, LEAF,
   MOON0, MOON1, MOON2, MOON3, MOON4, MOON5, MOON6, MOON7, SHELL, CONTROLS, BINOCULARS, HORN, WAVES, ROUTE, CALENDAR, DOT;

   public static final int COLS = 16;
   public static final int CELL = 96;
   private static final G[] VALUES = values();
   public static final int ROWS = (VALUES.length + COLS - 1) / COLS;

   public float u0() {
      return (this.ordinal() % COLS) / (float)COLS;
   }

   public float v0() {
      return (this.ordinal() / COLS) / (float)ROWS;
   }

   public float u1() {
      return this.u0() + 1.0F / COLS;
   }

   public float v1() {
      return this.v0() + 1.0F / ROWS;
   }

   /** Draws the glyph centred at (cx, cy), {@code size} pixels square, tinted. */
   public void draw(Canvas c, float cx, float cy, float size, int argb) {
      float h = size / 2.0F;
      c.sprite("glyphs", cx - h, cy - h, size, size, this.u0(), this.v0(), this.u1(), this.v1(), argb);
   }

   public static G moon(int phase) {
      return VALUES[MOON0.ordinal() + Math.floorMod(phase, 8)];
   }
}
