package com.formaworks.frontierhunts.phone.client.ui;

/**
 * [phone] The drawing surface of the Field Phone. The whole phone UI (frame, home screen, apps, games) draws through
 * this and nothing else, so the same code renders in the game ({@code phone.client.GuiCanvas}: crisp fills at the
 * window's physical pixel grid, batched) and offline ({@code tools/phone/mock}: Java2D, for the 1920x1080 mocks).
 *
 * <p>Coordinates are logical phone pixels (about one GUI pixel at the Frontier GUI scale), floats, transformed by the
 * translate/scale stack. Colours are ARGB. Angles are radians, clockwise from 12 o'clock.
 */
public interface Canvas {
   enum Font {
      /** Inter SemiBold 6.4, line 8 */
      SMALL,
      /** Inter Medium 8, line 10 */
      BODY,
      /** Inter SemiBold 8.2, line 10 */
      STRONG,
      /** Inter SemiBold 11, line 13: list titles, buttons */
      MEDIUM,
      /** Inter Bold 12, line 14 */
      TITLE,
      /** Inter Bold 17, line 20: large app titles */
      LARGE,
      /** Inter SemiBold 26, line 28: big numbers (temperature, scores) */
      DISPLAY,
      /** Inter Medium 42, line 42: the big clock */
      HUGE
   }

   /** Something drawn natively by the game (an entity, a vanilla widget); the mock draws a placeholder. */
   interface Custom {
      void draw(Object nativeGraphics, float x, float y, float w, float h);
   }

   // ------------------------------------------------------------------------------------------------ transform

   void push();

   void pop();

   void translate(float x, float y);

   void scale(float s);

   /** Multiplies the alpha of everything drawn until the matching {@link #pop()}. */
   void alpha(float a);

   // ------------------------------------------------------------------------------------------------ shapes

   void fill(float x, float y, float w, float h, int argb);

   /** Rounded rectangle with anti-aliased corners. */
   void round(float x, float y, float w, float h, float r, int argb);

   /** Vertical gradient (top colour to bottom colour). */
   void gradient(float x, float y, float w, float h, int top, int bottom);

   /** Horizontal gradient (left colour to right colour). */
   void hgradient(float x, float y, float w, float h, int left, int right);

   /** Any convex quad (clockwise or counter-clockwise). */
   void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb);

   default void triangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
      this.quad(x0, y0, x1, y1, x2, y2, x2, y2, argb);
   }

   void line(float x0, float y0, float x1, float y1, float width, int argb);

   default void circle(float cx, float cy, float r, int argb) {
      this.round(cx - r, cy - r, r * 2.0F, r * 2.0F, r, argb);
   }

   /** Outline of a circle. */
   void ring(float cx, float cy, float r, float thickness, int argb);

   /** Part of a ring from angle a0 to a1 (radians, clockwise from 12 o'clock). */
   void arc(float cx, float cy, float r, float thickness, float a0, float a1, int argb);

   // ------------------------------------------------------------------------------------------------ text

   /** Draws text with its top at y; returns the x after it. */
   float text(String s, float x, float y, int argb, Font f);

   float width(String s, Font f);

   // ------------------------------------------------------------------------------------------------ images

   /**
    * Part of one of the phone's art sheets ({@code textures/gui/phone/<sheet>.png}), UVs 0..1, tinted (ARGB multiply;
    * 0xFFFFFFFF = as painted).
    */
   void sprite(String sheet, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int tint);

   /** A texture handle supplied by the model (a developed trail camera photo, the map), whole, tinted. */
   void image(Object handle, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int tint);

   /** An item's inventory icon, {@code size} pixels square. */
   void item(String id, float x, float y, float size);

   void custom(Custom c, float x, float y, float w, float h);

   // ------------------------------------------------------------------------------------------------ clipping

   /** Clips to a rectangle (intersected with the current clip) until {@link #unclip()}. */
   void clip(float x, float y, float w, float h);

   void unclip();

   // ------------------------------------------------------------------------------------------------ helpers

   default void center(String s, float cx, float y, int argb, Font f) {
      this.text(s, cx - this.width(s, f) / 2.0F, y, argb, f);
   }

   default void right(String s, float rx, float y, int argb, Font f) {
      this.text(s, rx - this.width(s, f), y, argb, f);
   }

   static int lineHeight(Font f) {
      return switch (f) {
         case SMALL -> 8;
         case BODY, STRONG -> 10;
         case MEDIUM -> 13;
         case TITLE -> 14;
         case LARGE -> 20;
         case DISPLAY -> 28;
         case HUGE -> 42;
      };
   }

   /** Height of capital letters drawn with a font (for vertical centring), measured from the text's top. */
   static float capTop(Font f) {
      return switch (f) {
         case SMALL -> 1.0F;
         case BODY -> 1.0F;
         case STRONG -> 1.0F;
         case MEDIUM -> 1.6F;
         case TITLE -> 1.0F;
         case LARGE -> 1.4F;
         case DISPLAY -> 1.4F;
         case HUGE -> 1.5F;
      };
   }

   static float capHeight(Font f) {
      return switch (f) {
         case SMALL -> 4.7F;
         case BODY -> 5.8F;
         case STRONG -> 6.0F;
         case MEDIUM -> 8.0F;
         case TITLE -> 8.7F;
         case LARGE -> 12.4F;
         case DISPLAY -> 19.0F;
         case HUGE -> 30.5F;
      };
   }
}
