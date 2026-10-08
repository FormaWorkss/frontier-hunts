package com.formaworks.frontierhunts.phone.games;

/**
 * [phone] Lodge Chess puzzles: mate in two, each with exactly one first move that forces mate (and no mate in one).
 * Found in engine games and proved by exhaustive search (tools/phone/test/PuzzleGen.java; re-checked by
 * tools/phone/test.sh). {fen, key move in SAN}.
 */
public final class ChessPuzzles {
   public static final String[][] ALL = {
      {"r1b1k2r/1p5p/5p2/7p/2P1p3/1K5n/P2qP1P1/5BN1 b kq - 0 33", "Rxa2"},
      {"6k1/5p2/2B2q2/r1np3p/1p4b1/1P1p4/P2N4/1QK3R1 b - - 3 45", "Qc3+"},
      {"1r2kb2/p4pp1/2p1P3/N7/5K2/2P4r/PP3n1P/1RB5 b - - 1 29", "Bd6+"},
      {"6k1/2p3rp/pn5R/3PpP2/p3p3/4P3/4QP1K/2q5 b - - 7 44", "Qg1+"},
      {"3B4/1k6/p4R2/pb2P3/1r1p2P1/2p1r3/8/2RK4 b - - 15 53", "Ba4+"},
      {"4n1k1/6pp/6P1/1p6/1P1n3K/P4q1P/3B4/R7 b - - 0 44", "Nf5+"},
      {"3Q4/k1r5/2B5/8/P1P1p1P1/K2P4/1P6/8 w - - 2 64", "Qxc7+"},
      {"7R/1rp5/k7/1p6/pR4P1/P2KB3/1PP5/8 w - - 15 58", "Ra8+"},
      {"8/1p5R/p4R2/2P2p2/4bPk1/2P5/r1PK4/8 w - - 11 48", "Ke3"},
      {"1r3rk1/1p3pp1/p1p5/3b2Pp/NP1p4/P2Pn3/R2q4/1K6 b - - 1 32", "Qxa2+"},
      {"R7/pp3p2/3k4/2pq4/PB1Q2B1/8/1PP5/R3K3 w Q - 1 31", "Bxc5+"},
      {"r2qkb1r/p1p1pppp/1p6/4PbN1/1QPn2n1/8/PP3PPP/RNB1KB1R b KQkq - 2 10", "Nc2+"},
      {"4k3/q7/B2p1R2/2nP2Q1/2P3p1/P5N1/4K3/8 w - - 1 61", "Qg8+"},
      {"6k1/5p2/r1B2P1R/3P4/8/P1PK2P1/3N4/R7 w - - 11 44", "Rah1"},
      {"3rr1k1/5pp1/1pp4p/p7/P4n2/2PB4/3K4/2R5 b - - 4 49", "Re2+"},
      {"1r3q2/1P3p2/1QPk4/7r/P1P5/4R2b/5P2/2B1K1b1 w - - 1 41", "Ba3+"},
      {"4b1kr/1pp3p1/p2p3p/8/2P1P1P1/2N1r3/1P5q/1R1K1R2 b - - 1 43", "Rd3+"},
      {"6k1/2R5/1P4p1/6B1/P7/2P5/R1K5/8 w - - 7 71", "b7"},
      {"r3kbr1/p3p2p/b2p4/1ppPp3/4P2P/PNP5/1P1BB1q1/1R1QK3 b q - 3 22", "Qg3+"},
      {"8/4k3/7p/2Pp4/8/P7/1Bq5/K2Bq3 b - - 1 57", "Qexd1+"},
      {"1k6/R7/6Rp/1p6/1r1p2P1/5B1P/5K2/8 w - - 4 49", "Rb7+"},
      {"3r3k/7p/8/p1p5/P1P5/KPN2p2/5P2/1q6 b - - 3 62", "Qc1+"},
      {"4k2r/1p3ppp/8/8/2q1P3/P2n1b2/3K1QrP/7R b - - 1 40", "Rxf2+"},
      {"4q1kr/1pp5/4p2B/r2pP1bp/8/6Q1/P1P1BPPP/R4KR1 w - - 6 23", "Qxg5+"},
      {"2k2br1/Q5p1/5p1p/8/1pK1B2P/1P4q1/2P5/4R3 w - - 2 53", "Bf5+"},
      {"4k3/2Q5/R5p1/2p2b1p/7P/4KPB1/6P1/8 w - - 1 43", "Ra8+"},
      {"8/5Bk1/p1b2p1b/q2p3Q/PrpP4/4B2P/R4PP1/3N1K2 w - - 1 37", "Bxh6+"},
      {"4k2r/pp3ppp/2r5/8/Pn2K1P1/2q1P1P1/7P/R6R b k - 1 30", "Qf6"},
      {"r4k2/2P4p/8/P2P1p2/3p1b2/R3q2P/1PK5/5b2 b - - 3 43", "Qc1+"},
      {"2k1r3/ppp2r2/7p/1P5P/2Pb4/n7/P2K1n2/5R2 b - - 3 35", "Bb2"},
      {"k5r1/2p3pp/2n5/1pP2bP1/p3p2R/PqP5/1Q2PP2/RKBr1B2 b - - 6 37", "e3+"},
      {"r4k2/p7/1p6/8/PP3r1p/2q4P/2PbQPP1/RK3R2 b - - 1 38", "Rxb4+"},
      {"2Qn1br1/2N2kp1/3p1P2/2pPp2p/4P2P/1P2BPR1/4N3/6K1 w - - 6 39", "Qd7+"},
      {"2r5/1p1k2Pp/3P1p2/5P2/p3n1PK/P5B1/QP4q1/5R2 b - - 0 54", "Qxg3+"},
      {"6k1/3R3N/6p1/3p2P1/3P1P2/B4P2/P3K3/8 w - - 11 60", "Nf6+"},
      {"1k3r1r/1b6/Bp6/P2p2p1/1P2p3/2q1P2P/KbP1QPP1/5R2 b - - 0 37", "Qa3+"},
      {"r1k5/1p3p2/1Bp1np2/P4P2/2P1P1P1/3R4/4BK2/8 w - - 3 46", "fxe6"},
      {"7k/1pp2r2/7r/p2B4/P6Q/8/RPP2P2/5KR1 w - - 2 45", "Qxh6+"},
      {"2k5/7p/1p6/PP6/1K6/R5P1/5r2/1b1r3q b - - 1 52", "Qe4+"},
      {"4rk2/2p2p2/8/3p4/6q1/2PB2Bb/1N6/6K1 b - - 1 47", "Qxg3+"}
   };

   private ChessPuzzles() {
   }
}
