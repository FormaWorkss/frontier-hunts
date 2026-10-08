package com.formaworks.frontierhunts.phone.client.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * [1.4.0] The phone's emoji: 32 original pictures on the {@code emoji} sheet (8 x 4), written in a text as
 * {@code :name:} so a text stays plain, readable words everywhere else (chat logs, the server's store, notifications).
 * The Messages app draws them inline, a text of only one to three emoji big and without a bubble, and the composer turns
 * the usual shortcuts into them (":)" a smile, "<3" a heart...).
 */
public final class Emoji {
   public static final String[] NAMES = {
      "smile", "grin", "joy", "wink", "cool", "love", "wow", "sad",
      "angry", "think", "sleepy", "party", "thumbs", "clap", "muscle", "wave",
      "fire", "heart", "hundred", "target", "trophy", "camera", "moon", "sun",
      "deer", "antlers", "track", "duck", "fish", "bear", "tent", "campfire"};
   private static final String[][] SHORTCUTS = {{":)", "smile"}, {":-)", "smile"}, {":D", "grin"}, {"XD", "joy"}, {";)", "wink"}, {"B)", "cool"},
      {"<3", "heart"}, {":(", "sad"}, {":O", "wow"}, {":o", "wow"}, {">:(", "angry"}};
   private static final HashMap<String, Integer> INDEX = new HashMap<>();
   private static final HashMap<String, List<Run>> RUNS = new HashMap<>();
   private static final HashMap<String, Layout> LAYOUTS = new HashMap<>();

   static {
      for (int i = 0; i < NAMES.length; i++) {
         INDEX.put(NAMES[i], i);
      }
   }

   /** A piece of a text: a word (or space-separated words) or one emoji. */
   public record Run(String text, int emoji) {
   }

   private record Layout(List<List<Run>> lines, float height) {
   }

   private Emoji() {
   }

   public static int index(String name) {
      Integer i = INDEX.get(name);
      return i == null ? -1 : i;
   }

   public static String token(int i) {
      return ":" + NAMES[i] + ":";
   }

   /** Draws emoji {@code i}, {@code size} pixels square, top-left at (x, y). */
   public static void draw(Canvas c, int i, float x, float y, float size) {
      int col = i % 8, row = i / 8;
      c.sprite("emoji", x, y, size, size, col / 8.0F, row / 4.0F, (col + 1) / 8.0F, (row + 1) / 4.0F, 0xFFFFFFFF);
   }

   /** The usual text shortcuts, as emoji tokens (only whole words: "a:)" stays as typed). */
   public static String shortcuts(String s) {
      if (s == null || s.isEmpty()) {
         return s;
      }
      String[] words = s.split(" ", -1);
      boolean changed = false;
      for (int w = 0; w < words.length; w++) {
         for (String[] sc : SHORTCUTS) {
            if (words[w].equals(sc[0])) {
               words[w] = ":" + sc[1] + ":";
               changed = true;
               break;
            }
         }
      }
      return changed ? String.join(" ", words) : s;
   }

   /** The text cut into words and emoji (cached; texts are few and short). */
   public static List<Run> runs(String s) {
      List<Run> hit = RUNS.get(s);
      if (hit != null) {
         return hit;
      }
      List<Run> out = new ArrayList<>();
      StringBuilder plain = new StringBuilder();
      int i = 0;
      while (i < s.length()) {
         char ch = s.charAt(i);
         if (ch == ':') {
            int end = s.indexOf(':', i + 1);
            if (end > i + 1 && end - i <= 12) {
               int e = index(s.substring(i + 1, end).toLowerCase(Locale.ROOT));
               if (e >= 0) {
                  if (plain.length() > 0) {
                     out.add(new Run(plain.toString(), -1));
                     plain.setLength(0);
                  }
                  out.add(new Run("", e));
                  i = end + 1;
                  continue;
               }
            }
         }
         plain.append(ch);
         i++;
      }
      if (plain.length() > 0) {
         out.add(new Run(plain.toString(), -1));
      }
      if (RUNS.size() > 2048) {
         RUNS.clear();
      }
      RUNS.put(s, out);
      return out;
   }

   public static boolean has(String s) {
      if (s == null || s.indexOf(':') < 0) {
         return false;
      }
      for (Run r : runs(s)) {
         if (r.emoji >= 0) {
            return true;
         }
      }
      return false;
   }

   /** One to three emoji and nothing else (spaces aside): drawn big, without a bubble. */
   public static int onlyEmoji(String s) {
      if (s == null || s.isEmpty() || s.indexOf(':') < 0) {
         return 0;
      }
      int n = 0;
      for (Run r : runs(s)) {
         if (r.emoji >= 0) {
            n++;
         } else if (!r.text.isBlank()) {
            return 0;
         }
      }
      return n <= 3 ? n : 0;
   }

   /** Width of a text with emoji drawn {@code size} square. */
   public static float width(Canvas c, String s, Canvas.Font f, float size) {
      float w = 0.0F;
      for (Run r : runs(s)) {
         w += r.emoji >= 0 ? size + 1.0F : c.width(r.text, f);
      }
      return w;
   }

   /** One line of text with emoji, cut short with an ellipsis at {@code maxW}; returns the x after it. */
   public static float line(Canvas c, String s, float x, float y, float maxW, int argb, Canvas.Font f) {
      float size = Canvas.lineHeight(f);
      float right = x + maxW;
      float ell = c.width("…", f);
      for (Run r : runs(s)) {
         if (r.emoji >= 0) {
            if (x + size > right) {
               c.text("…", x, y, argb, f);
               return x + ell;
            }
            draw(c, r.emoji, x, y - 1.0F, size);
            x += size + 1.0F;
         } else {
            float w = c.width(r.text, f);
            if (x + w > right) {
               String t = r.text;
               while (t.length() > 0 && x + c.width(t, f) + ell > right) {
                  t = t.substring(0, t.length() - 1);
               }
               x = c.text(t.stripTrailing() + "…", x, y, argb, f);
               return x;
            }
            x = c.text(r.text, x, y, argb, f);
         }
      }
      return x;
   }

   // ------------------------------------------------------------------------------------------------ paragraphs

   private static Layout layout(Canvas c, String s, float maxW, Canvas.Font f) {
      String key = s + '\u0000' + Math.round(maxW * 4.0F) + '\u0000' + f.ordinal();
      Layout hit = LAYOUTS.get(key);
      if (hit != null) {
         return hit;
      }
      float size = Canvas.lineHeight(f);
      List<List<Run>> lines = new ArrayList<>();
      List<Run> cur = new ArrayList<>();
      float x = 0.0F;
      for (Run r : runs(s)) {
         if (r.emoji >= 0) {
            if (x + size > maxW && !cur.isEmpty()) {
               lines.add(cur);
               cur = new ArrayList<>();
               x = 0.0F;
            }
            cur.add(r);
            x += size + 1.0F;
            continue;
         }
         // words, keeping their spaces; a line breaks before a word that would not fit
         String[] parts = r.text.split("(?<= )");
         for (String word : parts) {
            float w = c.width(word, f);
            if (x + c.width(word.stripTrailing(), f) > maxW && x > 0.0F) {
               lines.add(cur);
               cur = new ArrayList<>();
               x = 0.0F;
               word = word.stripLeading();
               w = c.width(word, f);
            }
            while (w > maxW && word.length() > 1) {
               int cut = word.length() - 1;
               while (cut > 1 && c.width(word.substring(0, cut), f) > maxW - x) {
                  cut--;
               }
               cur.add(new Run(word.substring(0, cut), -1));
               lines.add(cur);
               cur = new ArrayList<>();
               x = 0.0F;
               word = word.substring(cut);
               w = c.width(word, f);
            }
            if (!word.isEmpty()) {
               cur.add(new Run(word, -1));
               x += w;
            }
         }
      }
      if (!cur.isEmpty() || lines.isEmpty()) {
         lines.add(cur);
      }
      Layout out = new Layout(lines, lines.size() * size);
      if (LAYOUTS.size() > 1024) {
         LAYOUTS.clear();
      }
      LAYOUTS.put(key, out);
      return out;
   }

   public static float paraHeight(Canvas c, String s, float maxW, Canvas.Font f) {
      return layout(c, s, maxW, f).height;
   }

   /** Width of the widest line of the wrapped text. */
   public static float paraWidth(Canvas c, String s, float maxW, Canvas.Font f) {
      float size = Canvas.lineHeight(f);
      float best = 0.0F;
      for (List<Run> line : layout(c, s, maxW, f).lines) {
         float w = 0.0F;
         for (Run r : line) {
            w += r.emoji >= 0 ? size + 1.0F : c.width(r.text, f);
         }
         best = Math.max(best, w);
      }
      return best;
   }

   /** Draws the wrapped text with emoji; returns its height. */
   public static float para(Canvas c, String s, float x, float y, float maxW, int argb, Canvas.Font f) {
      Layout l = layout(c, s, maxW, f);
      float size = Canvas.lineHeight(f);
      float ly = y;
      for (List<Run> line : l.lines) {
         float lx = x;
         for (Run r : line) {
            if (r.emoji >= 0) {
               draw(c, r.emoji, lx, ly - 1.0F, size);
               lx += size + 1.0F;
            } else {
               lx = c.text(r.text, lx, ly, argb, f);
            }
         }
         ly += size;
      }
      return l.height;
   }

   /** A text as it reads in a notification or a one-line preview: emoji tokens and photos named plainly. */
   public static String plain(String s) {
      if (s == null) {
         return "";
      }
      if (s.startsWith("[photo:")) {
         return "Photo";
      }
      return s;
   }
}
