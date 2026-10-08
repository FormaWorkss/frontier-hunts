import com.formaworks.frontierhunts.phone.games.Chess;

/** [phone] Offline checks of the game engines (run by tools/phone/test.sh). */
public final class EngineTest {
   static int fails;

   static void check(boolean ok, String what) {
      System.out.println((ok ? "PASS " : "FAIL ") + what);
      if (!ok) {
         fails++;
      }
   }

   static long perft(Chess c, int depth) {
      if (depth == 0) {
         return 1;
      }
      int[] mv = new int[256];
      int n = c.pseudo(mv);
      long total = 0;
      int me = c.side;
      for (int i = 0; i < n; i++) {
         c.make(mv[i]);
         int k = c.king(me);
         if (!c.attacked(k, -me)) {
            total += perft(c, depth - 1);
         }
         c.unmake(mv[i]);
      }
      return total;
   }

   public static void main(String[] a) throws Exception {
      Object[][] cases = {
         {"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", 4, 197281L},
         {"r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 3, 97862L},
         {"8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 5, 674624L},
         {"r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 4, 422333L},
         {"rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 3, 62379L},
         {"r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10", 3, 89890L}};
      for (Object[] c : cases) {
         Chess ch = new Chess();
         ch.load((String)c[0]);
         String before = ch.fen();
         long n = perft(ch, (Integer)c[1]);
         check(n == (Long)c[2] && ch.fen().equals(before), "perft " + c[1] + " = " + n + " (want " + c[2] + ") " + c[0]);
      }
      Tests.more();
      System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
      System.exit(fails == 0 ? 0 : 1);
   }
}
