package com.formaworks.frontierhunts.phone.client.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** [phone] Text fitting and wrapping, cached (the phone redraws every frame; measuring text is not free). */
public final class Txt {
   private static final String[] NONE = new String[0];
   private final HashMap<Key, String> fits = new HashMap<>();
   private final HashMap<Key, String[]> wraps = new HashMap<>();
   private final Key probe = new Key();

   private static final class Key {
      String s;
      int w;
      int f;

      Key set(String s, float w, Canvas.Font f) {
         this.s = s;
         this.w = Math.round(w * 4.0F);
         this.f = f.ordinal();
         return this;
      }

      Key copy() {
         Key k = new Key();
         k.s = this.s;
         k.w = this.w;
         k.f = this.f;
         return k;
      }

      @Override
      public boolean equals(Object o) {
         return o instanceof Key k && k.w == this.w && k.f == this.f && k.s.equals(this.s);
      }

      @Override
      public int hashCode() {
         return this.s.hashCode() * 31 + this.w * 7 + this.f;
      }
   }

   public void clear() {
      this.fits.clear();
      this.wraps.clear();
   }

   /** The string, shortened with an ellipsis to fit {@code w}. */
   public String fit(Canvas c, String s, float w, Canvas.Font f) {
      if (s == null || s.isEmpty()) {
         return "";
      }
      String hit = this.fits.get(this.probe.set(s, w, f));
      if (hit != null) {
         return hit;
      }
      String out;
      if (c.width(s, f) <= w) {
         out = s;
      } else {
         int lo = 0, hi = s.length();
         float ell = c.width("…", f);
         while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (c.width(s.substring(0, mid), f) + ell <= w) {
               lo = mid;
            } else {
               hi = mid - 1;
            }
         }
         out = s.substring(0, lo).stripTrailing() + "…";
      }
      if (this.fits.size() > 4096) {
         this.fits.clear();
      }
      this.fits.put(this.probe.copy(), out);
      return out;
   }

   /** Word-wrapped lines of a string ("\n" breaks a line). */
   public String[] wrap(Canvas c, String s, float w, Canvas.Font f) {
      if (s == null || s.isEmpty()) {
         return NONE;
      }
      String[] hit = this.wraps.get(this.probe.set(s, w, f));
      if (hit != null) {
         return hit;
      }
      List<String> out = new ArrayList<>();
      for (String para : s.split("\n", -1)) {
         StringBuilder line = new StringBuilder();
         for (String word : para.split(" ")) {
            if (word.isEmpty()) {
               continue;
            }
            String t = line.length() == 0 ? word : line + " " + word;
            if (c.width(t, f) > w && line.length() > 0) {
               out.add(line.toString());
               line.setLength(0);
               line.append(word);
            } else {
               line.setLength(0);
               line.append(t);
            }
            // a single word wider than the line: hard-break it
            while (c.width(line.toString(), f) > w && line.length() > 1) {
               int cut = line.length() - 1;
               while (cut > 1 && c.width(line.substring(0, cut), f) > w) {
                  cut--;
               }
               out.add(line.substring(0, cut));
               String rest = line.substring(cut);
               line.setLength(0);
               line.append(rest);
            }
         }
         out.add(line.toString());
      }
      String[] arr = out.toArray(NONE);
      if (this.wraps.size() > 2048) {
         this.wraps.clear();
      }
      this.wraps.put(this.probe.copy(), arr);
      return arr;
   }

   /** Draws a wrapped paragraph; returns its height. */
   public float para(Canvas c, String s, float x, float y, float w, int argb, Canvas.Font f, float gap) {
      String[] lines = this.wrap(c, s, w, f);
      float lh = Canvas.lineHeight(f) + gap;
      for (int i = 0; i < lines.length; i++) {
         c.text(lines[i], x, y + i * lh, argb, f);
      }
      return lines.length * lh - (lines.length > 0 ? gap : 0.0F);
   }

   public float paraHeight(Canvas c, String s, float w, Canvas.Font f, float gap) {
      int n = this.wrap(c, s, w, f).length;
      return n == 0 ? 0.0F : n * (Canvas.lineHeight(f) + gap) - gap;
   }

   /** Centred wrapped paragraph; returns its height. */
   public float paraCenter(Canvas c, String s, float cx, float y, float w, int argb, Canvas.Font f, float gap) {
      String[] lines = this.wrap(c, s, w, f);
      float lh = Canvas.lineHeight(f) + gap;
      for (int i = 0; i < lines.length; i++) {
         c.center(lines[i], cx, y + i * lh, argb, f);
      }
      return lines.length * lh - (lines.length > 0 ? gap : 0.0F);
   }
}
