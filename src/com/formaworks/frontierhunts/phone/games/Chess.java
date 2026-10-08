package com.formaworks.frontierhunts.phone.games;

import java.util.Arrays;

/**
 * [phone] Complete chess rules (castling, en passant, promotion, check, mate, stalemate, the fifty-move rule, threefold
 * repetition and dead positions). Pure Java with no game classes: the server uses it to validate every move of an
 * online game, the phone to play, and the AI to search. Squares are 0 = a1 .. 63 = h8; pieces are +1..+6 for white
 * (pawn, knight, bishop, rook, queen, king) and negative for black.
 *
 * <p>Moves are ints: from | to << 6 | promotion piece type << 12 | flags << 16.
 */
public final class Chess {
   public static final int PAWN = 1, KNIGHT = 2, BISHOP = 3, ROOK = 4, QUEEN = 5, KING = 6;
   public static final int WHITE = 1, BLACK = -1;
   public static final int F_CAPTURE = 1, F_EP = 2, F_CASTLE = 4, F_DOUBLE = 8;
   // results
   public static final int PLAYING = 0, WHITE_WINS = 1, BLACK_WINS = 2, DRAW = 3;
   public static final int R_NONE = 0, R_MATE = 1, R_STALEMATE = 2, R_FIFTY = 3, R_REPETITION = 4, R_MATERIAL = 5, R_RESIGN = 6, R_TIMEOUT = 7,
      R_AGREED = 8, R_LEFT = 9;

   public final int[] board = new int[64];
   public int side = WHITE;
   /** 1 white king side, 2 white queen side, 4 black king side, 8 black queen side */
   public int castling;
   public int ep = -1;
   public int halfmove;
   public int fullmove = 1;
   public long hash;
   public int lastMove = -1;
   // history of position hashes (for repetition) and undo info
   private long[] history = new long[256];
   private int[] undo = new int[256 * 5];
   private int ply;

   private static final long[] Z = new long[64 * 13 + 16 + 8 + 1];

   static {
      java.util.SplittableRandom r = new java.util.SplittableRandom(0x5EED_C4E55L);
      for (int i = 0; i < Z.length; i++) {
         Z[i] = r.nextLong();
      }
   }

   private static long zp(int sq, int piece) {
      return Z[sq * 13 + piece + 6];
   }

   private static long zc(int castling) {
      return Z[64 * 13 + castling];
   }

   private static long ze(int ep) {
      return ep < 0 ? 0L : Z[64 * 13 + 16 + (ep & 7)];
   }

   private static long zs() {
      return Z[64 * 13 + 24];
   }

   public Chess() {
      this.reset();
   }

   public void reset() {
      this.load("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
   }

   public Chess copy() {
      Chess c = new Chess();
      System.arraycopy(this.board, 0, c.board, 0, 64);
      c.side = this.side;
      c.castling = this.castling;
      c.ep = this.ep;
      c.halfmove = this.halfmove;
      c.fullmove = this.fullmove;
      c.hash = this.hash;
      c.lastMove = this.lastMove;
      c.history = Arrays.copyOf(this.history, this.history.length);
      c.undo = Arrays.copyOf(this.undo, this.undo.length);
      c.ply = this.ply;
      return c;
   }

   // ------------------------------------------------------------------------------------------------ FEN

   public boolean load(String fen) {
      String[] p = fen.trim().split("\\s+");
      if (p.length < 4) {
         return false;
      }
      Arrays.fill(this.board, 0);
      int r = 7, f = 0;
      for (char ch : p[0].toCharArray()) {
         if (ch == '/') {
            r--;
            f = 0;
         } else if (Character.isDigit(ch)) {
            f += ch - '0';
         } else {
            int t = "pnbrqk".indexOf(Character.toLowerCase(ch)) + 1;
            if (t <= 0 || r < 0 || f > 7) {
               return false;
            }
            this.board[r * 8 + f] = Character.isUpperCase(ch) ? t : -t;
            f++;
         }
      }
      this.side = p[1].equals("b") ? BLACK : WHITE;
      this.castling = 0;
      if (p[2].indexOf('K') >= 0) {
         this.castling |= 1;
      }
      if (p[2].indexOf('Q') >= 0) {
         this.castling |= 2;
      }
      if (p[2].indexOf('k') >= 0) {
         this.castling |= 4;
      }
      if (p[2].indexOf('q') >= 0) {
         this.castling |= 8;
      }
      this.ep = p[3].equals("-") ? -1 : (p[3].charAt(1) - '1') * 8 + (p[3].charAt(0) - 'a');
      this.halfmove = p.length > 4 ? Integer.parseInt(p[4]) : 0;
      this.fullmove = p.length > 5 ? Integer.parseInt(p[5]) : 1;
      this.ply = 0;
      this.lastMove = -1;
      this.hash = this.computeHash();
      this.history[0] = this.hash;
      return true;
   }

   public String fen() {
      StringBuilder sb = new StringBuilder();
      for (int r = 7; r >= 0; r--) {
         int empty = 0;
         for (int f = 0; f < 8; f++) {
            int pc = this.board[r * 8 + f];
            if (pc == 0) {
               empty++;
            } else {
               if (empty > 0) {
                  sb.append(empty);
                  empty = 0;
               }
               char c = "pnbrqk".charAt(Math.abs(pc) - 1);
               sb.append(pc > 0 ? Character.toUpperCase(c) : c);
            }
         }
         if (empty > 0) {
            sb.append(empty);
         }
         if (r > 0) {
            sb.append('/');
         }
      }
      sb.append(this.side == WHITE ? " w " : " b ");
      String c = ((this.castling & 1) != 0 ? "K" : "") + ((this.castling & 2) != 0 ? "Q" : "") + ((this.castling & 4) != 0 ? "k" : "")
         + ((this.castling & 8) != 0 ? "q" : "");
      sb.append(c.isEmpty() ? "-" : c);
      sb.append(' ').append(this.ep < 0 ? "-" : square(this.ep));
      sb.append(' ').append(this.halfmove).append(' ').append(this.fullmove);
      return sb.toString();
   }

   public static String square(int sq) {
      return "" + (char)('a' + (sq & 7)) + (char)('1' + (sq >> 3));
   }

   private long computeHash() {
      long h = 0L;
      for (int i = 0; i < 64; i++) {
         if (this.board[i] != 0) {
            h ^= zp(i, this.board[i]);
         }
      }
      h ^= zc(this.castling) ^ ze(this.ep);
      if (this.side == BLACK) {
         h ^= zs();
      }
      return h;
   }

   // ------------------------------------------------------------------------------------------------ moves

   public static int move(int from, int to, int promo, int flags) {
      return from | to << 6 | promo << 12 | flags << 16;
   }

   public static int from(int m) {
      return m & 63;
   }

   public static int to(int m) {
      return m >> 6 & 63;
   }

   public static int promo(int m) {
      return m >> 12 & 7;
   }

   public static int flags(int m) {
      return m >> 16 & 15;
   }

   private static final int[] KN = {17, 15, 10, 6, -6, -10, -15, -17};
   private static final int[] KND = {2, 2, 1, 1, -1, -1, -2, -2};
   private static final int[] KNF = {1, -1, 2, -2, 2, -2, 1, -1};
   private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

   /** Pseudo-legal moves into {@code out}; returns the count. */
   public int pseudo(int[] out) {
      int n = 0;
      int s = this.side;
      for (int sq = 0; sq < 64; sq++) {
         int pc = this.board[sq];
         if (pc == 0 || Integer.signum(pc) != s) {
            continue;
         }
         int t = Math.abs(pc);
         int r = sq >> 3, f = sq & 7;
         switch (t) {
            case PAWN -> {
               int dir = s == WHITE ? 1 : -1;
               int start = s == WHITE ? 1 : 6;
               int last = s == WHITE ? 7 : 0;
               int r1 = r + dir;
               if (r1 >= 0 && r1 <= 7) {
                  int to = r1 * 8 + f;
                  if (this.board[to] == 0) {
                     n = this.pawn(out, n, sq, to, r1 == last, 0);
                     if (r == start && this.board[(r + 2 * dir) * 8 + f] == 0) {
                        out[n++] = move(sq, (r + 2 * dir) * 8 + f, 0, F_DOUBLE);
                     }
                  }
                  for (int df = -1; df <= 1; df += 2) {
                     int ff = f + df;
                     if (ff < 0 || ff > 7) {
                        continue;
                     }
                     int to2 = r1 * 8 + ff;
                     int target = this.board[to2];
                     if (target != 0 && Integer.signum(target) == -s) {
                        n = this.pawn(out, n, sq, to2, r1 == last, F_CAPTURE);
                     } else if (to2 == this.ep) {
                        out[n++] = move(sq, to2, 0, F_EP | F_CAPTURE);
                     }
                  }
               }
            }
            case KNIGHT -> {
               for (int i = 0; i < 8; i++) {
                  int rr = r + KND[i], ff = f + KNF[i];
                  if (rr < 0 || rr > 7 || ff < 0 || ff > 7) {
                     continue;
                  }
                  int to = rr * 8 + ff;
                  int target = this.board[to];
                  if (target == 0) {
                     out[n++] = move(sq, to, 0, 0);
                  } else if (Integer.signum(target) == -s) {
                     out[n++] = move(sq, to, 0, F_CAPTURE);
                  }
               }
            }
            case KING -> {
               for (int[] d : DIRS) {
                  int rr = r + d[0], ff = f + d[1];
                  if (rr < 0 || rr > 7 || ff < 0 || ff > 7) {
                     continue;
                  }
                  int to = rr * 8 + ff;
                  int target = this.board[to];
                  if (target == 0) {
                     out[n++] = move(sq, to, 0, 0);
                  } else if (Integer.signum(target) == -s) {
                     out[n++] = move(sq, to, 0, F_CAPTURE);
                  }
               }
               // castling
               if (s == WHITE && sq == 4) {
                  if ((this.castling & 1) != 0 && this.board[5] == 0 && this.board[6] == 0 && this.board[7] == ROOK && !this.attacked(4, BLACK)
                     && !this.attacked(5, BLACK) && !this.attacked(6, BLACK)) {
                     out[n++] = move(4, 6, 0, F_CASTLE);
                  }
                  if ((this.castling & 2) != 0 && this.board[3] == 0 && this.board[2] == 0 && this.board[1] == 0 && this.board[0] == ROOK
                     && !this.attacked(4, BLACK) && !this.attacked(3, BLACK) && !this.attacked(2, BLACK)) {
                     out[n++] = move(4, 2, 0, F_CASTLE);
                  }
               } else if (s == BLACK && sq == 60) {
                  if ((this.castling & 4) != 0 && this.board[61] == 0 && this.board[62] == 0 && this.board[63] == -ROOK && !this.attacked(60, WHITE)
                     && !this.attacked(61, WHITE) && !this.attacked(62, WHITE)) {
                     out[n++] = move(60, 62, 0, F_CASTLE);
                  }
                  if ((this.castling & 8) != 0 && this.board[59] == 0 && this.board[58] == 0 && this.board[57] == 0 && this.board[56] == -ROOK
                     && !this.attacked(60, WHITE) && !this.attacked(59, WHITE) && !this.attacked(58, WHITE)) {
                     out[n++] = move(60, 58, 0, F_CASTLE);
                  }
               }
            }
            default -> {
               int d0 = t == BISHOP ? 4 : 0;
               int d1 = t == ROOK ? 4 : 8;
               for (int di = d0; di < d1; di++) {
                  int[] d = DIRS[di];
                  int rr = r + d[0], ff = f + d[1];
                  while (rr >= 0 && rr <= 7 && ff >= 0 && ff <= 7) {
                     int to = rr * 8 + ff;
                     int target = this.board[to];
                     if (target == 0) {
                        out[n++] = move(sq, to, 0, 0);
                     } else {
                        if (Integer.signum(target) == -s) {
                           out[n++] = move(sq, to, 0, F_CAPTURE);
                        }
                        break;
                     }
                     rr += d[0];
                     ff += d[1];
                  }
               }
            }
         }
      }
      return n;
   }

   private int pawn(int[] out, int n, int from, int to, boolean promote, int flags) {
      if (promote) {
         for (int p = QUEEN; p >= KNIGHT; p--) {
            out[n++] = move(from, to, p, flags);
         }
      } else {
         out[n++] = move(from, to, 0, flags);
      }
      return n;
   }

   /** Is a square attacked by side {@code by}? */
   public boolean attacked(int sq, int by) {
      int r = sq >> 3, f = sq & 7;
      // pawns
      int pr = r - by;
      if (pr >= 0 && pr <= 7) {
         if (f > 0 && this.board[pr * 8 + f - 1] == by * PAWN) {
            return true;
         }
         if (f < 7 && this.board[pr * 8 + f + 1] == by * PAWN) {
            return true;
         }
      }
      for (int i = 0; i < 8; i++) {
         int rr = r + KND[i], ff = f + KNF[i];
         if (rr >= 0 && rr <= 7 && ff >= 0 && ff <= 7 && this.board[rr * 8 + ff] == by * KNIGHT) {
            return true;
         }
      }
      for (int di = 0; di < 8; di++) {
         int[] d = DIRS[di];
         int rr = r + d[0], ff = f + d[1];
         int dist = 1;
         while (rr >= 0 && rr <= 7 && ff >= 0 && ff <= 7) {
            int pc = this.board[rr * 8 + ff];
            if (pc != 0) {
               if (Integer.signum(pc) == by) {
                  int t = Math.abs(pc);
                  if (t == QUEEN || dist == 1 && t == KING || di < 4 && t == ROOK || di >= 4 && t == BISHOP) {
                     return true;
                  }
               }
               break;
            }
            rr += d[0];
            ff += d[1];
            dist++;
         }
      }
      return false;
   }

   public int king(int s) {
      for (int i = 0; i < 64; i++) {
         if (this.board[i] == s * KING) {
            return i;
         }
      }
      return -1;
   }

   public boolean inCheck() {
      int k = this.king(this.side);
      return k >= 0 && this.attacked(k, -this.side);
   }

   // ------------------------------------------------------------------------------------------------ make / unmake

   public void make(int m) {
      if (this.ply + 2 >= this.history.length) {
         this.history = Arrays.copyOf(this.history, this.history.length * 2);
         this.undo = Arrays.copyOf(this.undo, this.undo.length * 2);
      }
      int from = from(m), to = to(m), pr = promo(m), fl = flags(m);
      int pc = this.board[from];
      int captured = this.board[to];
      int capSq = to;
      if ((fl & F_EP) != 0) {
         capSq = to - 8 * this.side;
         captured = this.board[capSq];
      }
      int u = this.ply * 5;
      this.undo[u] = captured;
      this.undo[u + 1] = this.castling;
      this.undo[u + 2] = this.ep;
      this.undo[u + 3] = this.halfmove;
      this.undo[u + 4] = this.lastMove;
      long h = this.hash ^ zc(this.castling) ^ ze(this.ep);
      if (captured != 0) {
         this.board[capSq] = 0;
         h ^= zp(capSq, captured);
      }
      this.board[from] = 0;
      h ^= zp(from, pc);
      int placed = pr != 0 ? this.side * pr : pc;
      this.board[to] = placed;
      h ^= zp(to, placed);
      if ((fl & F_CASTLE) != 0) {
         int rf, rt;
         if (to == 6) {
            rf = 7;
            rt = 5;
         } else if (to == 2) {
            rf = 0;
            rt = 3;
         } else if (to == 62) {
            rf = 63;
            rt = 61;
         } else {
            rf = 56;
            rt = 59;
         }
         int rook = this.board[rf];
         this.board[rf] = 0;
         this.board[rt] = rook;
         h ^= zp(rf, rook) ^ zp(rt, rook);
      }
      // castling rights
      this.castling &= RIGHTS[from] & RIGHTS[to];
      this.ep = (fl & F_DOUBLE) != 0 ? (from + to) / 2 : -1;
      this.halfmove = Math.abs(pc) == PAWN || captured != 0 ? 0 : this.halfmove + 1;
      if (this.side == BLACK) {
         this.fullmove++;
      }
      this.side = -this.side;
      h ^= zc(this.castling) ^ ze(this.ep) ^ zs();
      this.hash = h;
      this.lastMove = m;
      this.ply++;
      this.history[this.ply] = h;
   }

   private static final int[] RIGHTS = new int[64];

   static {
      Arrays.fill(RIGHTS, 15);
      RIGHTS[4] = 15 & ~3;
      RIGHTS[7] = 15 & ~1;
      RIGHTS[0] = 15 & ~2;
      RIGHTS[60] = 15 & ~12;
      RIGHTS[63] = 15 & ~4;
      RIGHTS[56] = 15 & ~8;
   }

   public void unmake(int m) {
      this.ply--;
      int u = this.ply * 5;
      this.side = -this.side;
      if (this.side == BLACK) {
         this.fullmove--;
      }
      int from = from(m), to = to(m), pr = promo(m), fl = flags(m);
      int placed = this.board[to];
      this.board[from] = pr != 0 ? this.side * PAWN : placed;
      this.board[to] = 0;
      int captured = this.undo[u];
      if ((fl & F_EP) != 0) {
         this.board[to - 8 * this.side] = captured;
      } else {
         this.board[to] = captured;
      }
      if ((fl & F_CASTLE) != 0) {
         int rf, rt;
         if (to == 6) {
            rf = 7;
            rt = 5;
         } else if (to == 2) {
            rf = 0;
            rt = 3;
         } else if (to == 62) {
            rf = 63;
            rt = 61;
         } else {
            rf = 56;
            rt = 59;
         }
         this.board[rf] = this.board[rt];
         this.board[rt] = 0;
      }
      this.castling = this.undo[u + 1];
      this.ep = this.undo[u + 2];
      this.halfmove = this.undo[u + 3];
      this.lastMove = this.undo[u + 4];
      this.hash = this.history[this.ply];
   }

   /** Null move for the search (passes the turn). */
   public void makeNull() {
      int u = this.ply * 5;
      this.undo[u + 2] = this.ep;
      this.undo[u + 3] = this.halfmove;
      this.hash ^= ze(this.ep) ^ zs();
      this.ep = -1;
      this.side = -this.side;
      this.ply++;
      this.history[this.ply] = this.hash;
   }

   public void unmakeNull() {
      this.ply--;
      int u = this.ply * 5;
      this.side = -this.side;
      this.ep = this.undo[u + 2];
      this.halfmove = this.undo[u + 3];
      this.hash = this.history[this.ply];
   }

   // ------------------------------------------------------------------------------------------------ legality

   private final int[] buf = new int[256];

   /** Legal moves into {@code out}; returns the count. */
   public int legal(int[] out) {
      int[] tmp = new int[256];
      int n = this.pseudo(tmp);
      int k = 0;
      int me = this.side;
      for (int i = 0; i < n; i++) {
         this.make(tmp[i]);
         int kk = this.king(me);
         if (kk >= 0 && !this.attacked(kk, -me)) {
            out[k++] = tmp[i];
         }
         this.unmake(tmp[i]);
      }
      return k;
   }

   /** The legal move matching from/to/promotion, or -1. */
   public int find(int from, int to, int promo) {
      int[] out = new int[256];
      int n = this.legal(out);
      for (int i = 0; i < n; i++) {
         int m = out[i];
         if (from(m) == from && to(m) == to && (promo(m) == promo || promo(m) == QUEEN && promo == 0)) {
            return m;
         }
      }
      return -1;
   }

   public boolean isLegal(int m) {
      int[] out = new int[256];
      int n = this.legal(out);
      for (int i = 0; i < n; i++) {
         if (out[i] == m) {
            return true;
         }
      }
      return false;
   }

   // ------------------------------------------------------------------------------------------------ game status

   /** PLAYING, WHITE_WINS, BLACK_WINS or DRAW for the position as it stands. */
   public int status() {
      return this.statusReason()[0];
   }

   /** {result, reason}. */
   public int[] statusReason() {
      int n = this.legal(this.buf);
      if (n == 0) {
         if (this.inCheck()) {
            return new int[]{this.side == WHITE ? BLACK_WINS : WHITE_WINS, R_MATE};
         }
         return new int[]{DRAW, R_STALEMATE};
      }
      if (this.halfmove >= 100) {
         return new int[]{DRAW, R_FIFTY};
      }
      if (this.repetitions() >= 3) {
         return new int[]{DRAW, R_REPETITION};
      }
      if (this.deadPosition()) {
         return new int[]{DRAW, R_MATERIAL};
      }
      return new int[]{PLAYING, R_NONE};
   }

   public int repetitions() {
      int count = 0;
      for (int i = this.ply; i >= 0 && i >= this.ply - this.halfmove; i -= 2) {
         if (this.history[i] == this.hash) {
            count++;
         }
      }
      return count;
   }

   /** King against king, a king and one minor piece against a lone king, or only bishops all on one square colour. */
   public boolean deadPosition() {
      int knights = 0, bishops = 0, colors = 0;
      for (int i = 0; i < 64; i++) {
         int t = Math.abs(this.board[i]);
         if (t == PAWN || t == ROOK || t == QUEEN) {
            return false;
         }
         if (t == KNIGHT) {
            knights++;
         } else if (t == BISHOP) {
            bishops++;
            colors |= 1 << (((i >> 3) + (i & 7)) & 1);
         }
      }
      return knights + bishops <= 1 || knights == 0 && colors != 3;
   }

   // ------------------------------------------------------------------------------------------------ notation

   /** Standard algebraic notation of a legal move in this position (before it is made). */
   public String san(int m) {
      int from = from(m), to = to(m), fl = flags(m);
      int pc = Math.abs(this.board[from]);
      String s;
      if ((fl & F_CASTLE) != 0) {
         s = (to & 7) == 6 ? "O-O" : "O-O-O";
      } else {
         StringBuilder sb = new StringBuilder();
         if (pc != PAWN) {
            sb.append("NBRQK".charAt(pc - 2));
            // disambiguation
            int[] out = new int[256];
            int n = this.legal(out);
            boolean file = false, rank = false, need = false;
            for (int i = 0; i < n; i++) {
               int o = out[i];
               if (o != m && to(o) == to && Math.abs(this.board[from(o)]) == pc) {
                  need = true;
                  if ((from(o) & 7) == (from & 7)) {
                     file = true;
                  }
                  if ((from(o) >> 3) == (from >> 3)) {
                     rank = true;
                  }
               }
            }
            if (need) {
               if (!file) {
                  sb.append((char)('a' + (from & 7)));
               } else if (!rank) {
                  sb.append((char)('1' + (from >> 3)));
               } else {
                  sb.append(square(from));
               }
            }
         } else if ((fl & F_CAPTURE) != 0) {
            sb.append((char)('a' + (from & 7)));
         }
         if ((fl & F_CAPTURE) != 0) {
            sb.append('x');
         }
         sb.append(square(to));
         if (promo(m) != 0) {
            sb.append('=').append("NBRQ".charAt(promo(m) - 2));
         }
         s = sb.toString();
      }
      this.make(m);
      int[] out = new int[256];
      boolean check = this.inCheck();
      boolean mate = check && this.legal(out) == 0;
      this.unmake(m);
      return s + (mate ? "#" : (check ? "+" : ""));
   }

   // ------------------------------------------------------------------------------------------------ network form

   /** board(64) + side + castling + ep + halfmove + fullmove + lastMove */
   public int[] save() {
      int[] a = new int[70];
      System.arraycopy(this.board, 0, a, 0, 64);
      a[64] = this.side;
      a[65] = this.castling;
      a[66] = this.ep;
      a[67] = this.halfmove;
      a[68] = this.fullmove;
      a[69] = this.lastMove;
      return a;
   }

   /** Loads a saved position (no repetition history: the server keeps the real game). */
   public boolean restore(int[] a, int off) {
      if (a == null || a.length < off + 70) {
         return false;
      }
      for (int i = 0; i < 64; i++) {
         int p = a[off + i];
         if (p < -6 || p > 6) {
            return false;
         }
         this.board[i] = p;
      }
      this.side = a[off + 64] == BLACK ? BLACK : WHITE;
      this.castling = a[off + 65] & 15;
      this.ep = a[off + 66] >= 0 && a[off + 66] < 64 ? a[off + 66] : -1;
      this.halfmove = Math.max(0, a[off + 67]);
      this.fullmove = Math.max(1, a[off + 68]);
      this.lastMove = a[off + 69];
      this.ply = 0;
      this.hash = this.computeHash();
      this.history[0] = this.hash;
      return true;
   }

   /** Material count in pawns for a side (for captured-piece trays). */
   public int material(int s) {
      int v = 0;
      for (int p : this.board) {
         if (Integer.signum(p) == s) {
            v += VALUE[Math.abs(p)];
         }
      }
      return v;
   }

   public static final int[] VALUE = {0, 1, 3, 3, 5, 9, 0};
}
