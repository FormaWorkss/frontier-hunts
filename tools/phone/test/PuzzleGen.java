import com.formaworks.frontierhunts.phone.games.Chess;
import com.formaworks.frontierhunts.phone.games.ChessAi;
import java.util.*;

/** [phone] Finds mate-in-2 puzzles (unique key move, no mate in 1) in AI games: java PuzzleGen <count>. */
public final class PuzzleGen {
   static boolean mated(Chess c) {
      return c.inCheck() && c.legal(new int[256]) == 0;
   }

   static boolean mateIn1(Chess c) {
      int[] mv = new int[256];
      int n = c.legal(mv);
      for (int i = 0; i < n; i++) {
         c.make(mv[i]);
         boolean m = mated(c);
         c.unmake(mv[i]);
         if (m) return true;
      }
      return false;
   }

   /** keys that force mate in 2 */
   static List<Integer> keys(Chess c) {
      List<Integer> out = new ArrayList<>();
      int[] mv = new int[256];
      int n = c.legal(mv);
      for (int i = 0; i < n; i++) {
         c.make(mv[i]);
         int[] rep = new int[256];
         int rn = c.legal(rep);
         boolean all = rn > 0;
         for (int j = 0; j < rn && all; j++) {
            c.make(rep[j]);
            if (!mateIn1(c)) all = false;
            c.unmake(rep[j]);
         }
         c.unmake(mv[i]);
         if (all) out.add(mv[i]);
      }
      return out;
   }

   public static void main(String[] a) {
      int want = Integer.parseInt(a[0]);
      Set<String> seen = new HashSet<>();
      ChessAi ai = new ChessAi();
      int found = 0;
      for (long g = 0; found < want && g < 4000; g++) {
         Chess c = new Chess();
         Random r = new Random(g);
         for (int ply = 0; ply < 160 && c.status() == Chess.PLAYING; ply++) {
            int lvl = r.nextInt(10) < 6 ? ChessAi.MEDIUM : ChessAi.EASY;
            int m = ai.best(c, lvl, g * 1000 + ply);
            c.make(m);
            if (ply < 16 || c.status() != Chess.PLAYING) continue;
            int pieces = 0;
            for (int p : c.board) if (p != 0) pieces++;
            if (pieces < 9) continue;
            if (mateIn1(c)) continue;
            List<Integer> k = keys(c);
            if (k.size() == 1) {
               String fen = c.copy().fen();
               String key = fen.split(" ")[0];
               if (seen.add(key)) {
                  System.out.println(fen + " | " + c.san(k.get(0)) + " | pieces " + pieces);
                  found++;
               }
               break;
            }
         }
      }
   }
}
