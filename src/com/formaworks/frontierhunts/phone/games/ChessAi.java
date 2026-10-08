package com.formaworks.frontierhunts.phone.games;

import java.util.Random;

/**
 * [phone] The Lodge Chess opponent: iterative-deepening alpha-beta with a transposition table, quiescence search,
 * check extensions, killer and history move ordering, and a tapered piece-square evaluation. Three strengths: Easy
 * (shallow, noisy: it makes human mistakes), Medium (a club player) and Hard (searches about a second deep).
 * Allocation-free inside the search; meant to run on a worker thread.
 */
public final class ChessAi {
   public static final int EASY = 0, MEDIUM = 1, HARD = 2;
   private static final int INF = 1_000_000;
   private static final int MATE = 100_000;
   private static final int MAX_PLY = 64;
   private static final int TT_BITS = 18;
   private static final int TT_SIZE = 1 << TT_BITS;
   private final long[] ttKey = new long[TT_SIZE];
   private final int[] ttMove = new int[TT_SIZE];
   private final int[] ttScore = new int[TT_SIZE];
   private final byte[] ttDepth = new byte[TT_SIZE];
   private final byte[] ttFlag = new byte[TT_SIZE];
   private final int[][] moves = new int[MAX_PLY + 8][256];
   private final int[][] scores = new int[MAX_PLY + 8][256];
   private final int[][] killers = new int[MAX_PLY + 8][2];
   private final int[][] historyTable = new int[13][64];
   private Chess pos;
   private long nodes;
   private long nodeLimit;
   private boolean stopped;
   private volatile boolean cancelled;
   public int lastDepth;
   public int lastScore;

   /** Stops a running search as soon as possible (the game was left). */
   public void cancel() {
      this.cancelled = true;
   }

   /** The move this AI plays in {@code position} (which is not changed), or -1 when it has none. */
   public int best(Chess position, int level, long seed) {
      this.cancelled = false;
      this.pos = position.copy();
      Random rnd = new Random(seed);
      int maxDepth = level == EASY ? 2 : (level == MEDIUM ? 4 : 8);
      this.nodeLimit = level == EASY ? 4_000L : (level == MEDIUM ? 90_000L : 900_000L);
      int noise = level == EASY ? 140 : (level == MEDIUM ? 22 : 0);
      for (int[] k : this.killers) {
         k[0] = k[1] = 0;
      }
      for (int[] h : this.historyTable) {
         java.util.Arrays.fill(h, 0);
      }
      int[] root = new int[256];
      int n = this.legalInto(root);
      if (n == 0) {
         return -1;
      }
      if (n == 1) {
         return root[0];
      }
      int[] rootScore = new int[n];
      int best = root[0];
      this.nodes = 0L;
      this.stopped = false;
      for (int depth = 1; depth <= maxDepth; depth++) {
         int alpha = -INF;
         int bestHere = -1;
         int bestScore = -INF;
         // search the previous best first
         for (int i = 0; i < n; i++) {
            if (root[i] == best) {
               int t = root[0];
               root[0] = root[i];
               root[i] = t;
               int ts = rootScore[0];
               rootScore[0] = rootScore[i];
               rootScore[i] = ts;
               break;
            }
         }
         for (int i = 0; i < n; i++) {
            int m = root[i];
            this.pos.make(m);
            // with noise every root move needs its true score (a bound could hide a blunder), so a full window
            int sc = -this.search(depth - 1, 1, -INF, noise > 0 ? INF : -alpha, true);
            this.pos.unmake(m);
            if (this.stopped) {
               break;
            }
            rootScore[i] = sc;
            if (sc > bestScore) {
               bestScore = sc;
               bestHere = m;
            }
            alpha = Math.max(alpha, sc);
         }
         if (this.stopped) {
            break;
         }
         best = bestHere;
         this.lastDepth = depth;
         this.lastScore = bestScore;
         if (bestScore > MATE - 100) {
            break;
         }
      }
      if (noise > 0) {
         // a human-like pick: the best score after some noise, never a move that hangs mate when another does not
         int pick = best;
         int pickScore = -INF;
         for (int i = 0; i < n; i++) {
            if (rootScore[i] <= -MATE + 100) {
               continue;
            }
            int s = rootScore[i] + (int)(rnd.nextGaussian() * noise);
            if (s > pickScore) {
               pickScore = s;
               pick = root[i];
            }
         }
         best = pick;
      }
      return best;
   }

   private int legalInto(int[] out) {
      int[] tmp = new int[256];
      int n = this.pos.pseudo(tmp);
      int k = 0;
      int me = this.pos.side;
      for (int i = 0; i < n; i++) {
         this.pos.make(tmp[i]);
         int kk = this.pos.king(me);
         if (kk >= 0 && !this.pos.attacked(kk, -me)) {
            out[k++] = tmp[i];
         }
         this.pos.unmake(tmp[i]);
      }
      return k;
   }

   private int search(int depth, int ply, int alpha, int beta, boolean nullOk) {
      if ((++this.nodes & 1023) == 0 && (this.nodes > this.nodeLimit || this.cancelled)) {
         this.stopped = true;
      }
      if (this.stopped) {
         return 0;
      }
      if (this.pos.halfmove >= 100 || this.pos.repetitions() >= 2) {
         return 0;
      }
      int me = this.pos.side;
      int king = this.pos.king(me);
      boolean check = king >= 0 && this.pos.attacked(king, -me);
      if (check) {
         depth++;
      }
      if (depth <= 0) {
         return this.quiesce(alpha, beta, ply);
      }
      if (ply >= MAX_PLY) {
         return this.evaluate();
      }
      int slot = (int)(this.pos.hash & TT_SIZE - 1);
      int ttm = 0;
      if (this.ttKey[slot] == this.pos.hash) {
         ttm = this.ttMove[slot];
         if (this.ttDepth[slot] >= depth) {
            int s = this.ttScore[slot];
            int fl = this.ttFlag[slot];
            if (fl == 0 || fl == 1 && s >= beta || fl == 2 && s <= alpha) {
               return s;
            }
         }
      }
      // null move pruning
      if (nullOk && !check && depth >= 3 && this.hasPieces(me)) {
         this.pos.makeNull();
         int s = -this.search(depth - 3, ply + 1, -beta, -beta + 1, false);
         this.pos.unmakeNull();
         if (this.stopped) {
            return 0;
         }
         if (s >= beta) {
            return beta;
         }
      }
      int[] mv = this.moves[ply];
      int[] sc = this.scores[ply];
      int n = this.pos.pseudo(mv);
      for (int i = 0; i < n; i++) {
         sc[i] = this.order(mv[i], ttm, ply);
      }
      int legal = 0;
      int bestMove = 0;
      int bestScore = -INF;
      int origAlpha = alpha;
      for (int i = 0; i < n; i++) {
         // pick the best remaining move (selection sort step)
         int bi = i;
         for (int j = i + 1; j < n; j++) {
            if (sc[j] > sc[bi]) {
               bi = j;
            }
         }
         int m = mv[bi];
         mv[bi] = mv[i];
         mv[i] = m;
         int t = sc[bi];
         sc[bi] = sc[i];
         sc[i] = t;
         this.pos.make(m);
         int k = this.pos.king(me);
         if (k < 0 || this.pos.attacked(k, -me)) {
            this.pos.unmake(m);
            continue;
         }
         legal++;
         int s;
         if (legal == 1) {
            s = -this.search(depth - 1, ply + 1, -beta, -alpha, true);
         } else {
            // late move reduction for quiet moves
            boolean quiet = (Chess.flags(m) & Chess.F_CAPTURE) == 0 && Chess.promo(m) == 0;
            int red = quiet && !check && depth >= 3 && legal > 4 ? 1 : 0;
            s = -this.search(depth - 1 - red, ply + 1, -alpha - 1, -alpha, true);
            if (s > alpha && !this.stopped) {
               s = -this.search(depth - 1, ply + 1, -beta, -alpha, true);
            }
         }
         this.pos.unmake(m);
         if (this.stopped) {
            return 0;
         }
         if (s > bestScore) {
            bestScore = s;
            bestMove = m;
         }
         if (s > alpha) {
            alpha = s;
         }
         if (alpha >= beta) {
            if ((Chess.flags(m) & Chess.F_CAPTURE) == 0) {
               int[] kl = this.killers[ply];
               if (kl[0] != m) {
                  kl[1] = kl[0];
                  kl[0] = m;
               }
               int pc = this.pos.board[Chess.from(m)];
               this.historyTable[pc + 6][Chess.to(m)] += depth * depth;
            }
            break;
         }
      }
      if (legal == 0) {
         return check ? -MATE + ply : 0;
      }
      this.ttKey[slot] = this.pos.hash;
      this.ttMove[slot] = bestMove;
      this.ttScore[slot] = bestScore;
      this.ttDepth[slot] = (byte)Math.min(127, depth);
      this.ttFlag[slot] = (byte)(bestScore >= beta ? 1 : (bestScore <= origAlpha ? 2 : 0));
      return bestScore;
   }

   private boolean hasPieces(int s) {
      for (int p : this.pos.board) {
         int t = Math.abs(p);
         if (Integer.signum(p) == s && t >= Chess.KNIGHT && t <= Chess.QUEEN) {
            return true;
         }
      }
      return false;
   }

   private int order(int m, int ttm, int ply) {
      if (m == ttm) {
         return 1_000_000;
      }
      int fl = Chess.flags(m);
      if ((fl & Chess.F_CAPTURE) != 0 || Chess.promo(m) != 0) {
         int victim = (fl & Chess.F_EP) != 0 ? 1 : Math.abs(this.pos.board[Chess.to(m)]);
         int attacker = Math.abs(this.pos.board[Chess.from(m)]);
         return 100_000 + PV[victim] * 10 - PV[attacker] / 10 + Chess.promo(m) * 1000;
      }
      int[] k = this.killers[ply];
      if (m == k[0]) {
         return 90_000;
      }
      if (m == k[1]) {
         return 80_000;
      }
      return Math.min(70_000, this.historyTable[this.pos.board[Chess.from(m)] + 6][Chess.to(m)]);
   }

   private int quiesce(int alpha, int beta, int ply) {
      if ((++this.nodes & 1023) == 0 && (this.nodes > this.nodeLimit * 2 || this.cancelled)) {
         this.stopped = true;
      }
      if (this.stopped) {
         return 0;
      }
      int stand = this.evaluate();
      if (stand >= beta) {
         return beta;
      }
      if (stand > alpha) {
         alpha = stand;
      }
      if (ply >= MAX_PLY) {
         return stand;
      }
      int[] mv = this.moves[ply];
      int[] sc = this.scores[ply];
      int n = this.pos.pseudo(mv);
      int k = 0;
      for (int i = 0; i < n; i++) {
         if ((Chess.flags(mv[i]) & Chess.F_CAPTURE) != 0 || Chess.promo(mv[i]) == Chess.QUEEN) {
            mv[k] = mv[i];
            sc[k] = this.order(mv[i], 0, ply);
            k++;
         }
      }
      int me = this.pos.side;
      for (int i = 0; i < k; i++) {
         int bi = i;
         for (int j = i + 1; j < k; j++) {
            if (sc[j] > sc[bi]) {
               bi = j;
            }
         }
         int m = mv[bi];
         mv[bi] = mv[i];
         mv[i] = m;
         int t = sc[bi];
         sc[bi] = sc[i];
         sc[i] = t;
         this.pos.make(m);
         int kk = this.pos.king(me);
         if (kk < 0 || this.pos.attacked(kk, -me)) {
            this.pos.unmake(m);
            continue;
         }
         int s = -this.quiesce(-beta, -alpha, ply + 1);
         this.pos.unmake(m);
         if (this.stopped) {
            return 0;
         }
         if (s >= beta) {
            return beta;
         }
         if (s > alpha) {
            alpha = s;
         }
      }
      return alpha;
   }

   // ------------------------------------------------------------------------------------------------ evaluation

   private static final int[] PV = {0, 100, 320, 330, 500, 900, 20000};
   // piece-square tables from white's side, a8 first (index = (7 - rank) * 8 + file)
   private static final int[] P_PAWN = {0, 0, 0, 0, 0, 0, 0, 0, 50, 50, 50, 50, 50, 50, 50, 50, 10, 10, 20, 30, 30, 20, 10, 10, 5, 5, 10, 25, 25, 10, 5, 5,
      0, 0, 0, 20, 20, 0, 0, 0, 5, -5, -10, 0, 0, -10, -5, 5, 5, 10, 10, -20, -20, 10, 10, 5, 0, 0, 0, 0, 0, 0, 0, 0};
   private static final int[] P_KNIGHT = {-50, -40, -30, -30, -30, -30, -40, -50, -40, -20, 0, 0, 0, 0, -20, -40, -30, 0, 10, 15, 15, 10, 0, -30, -30, 5, 15,
      20, 20, 15, 5, -30, -30, 0, 15, 20, 20, 15, 0, -30, -30, 5, 10, 15, 15, 10, 5, -30, -40, -20, 0, 5, 5, 0, -20, -40, -50, -40, -30, -30, -30, -30,
      -40, -50};
   private static final int[] P_BISHOP = {-20, -10, -10, -10, -10, -10, -10, -20, -10, 0, 0, 0, 0, 0, 0, -10, -10, 0, 5, 10, 10, 5, 0, -10, -10, 5, 5, 10, 10,
      5, 5, -10, -10, 0, 10, 10, 10, 10, 0, -10, -10, 10, 10, 10, 10, 10, 10, -10, -10, 5, 0, 0, 0, 0, 5, -10, -20, -10, -10, -10, -10, -10, -10, -20};
   private static final int[] P_ROOK = {0, 0, 0, 0, 0, 0, 0, 0, 5, 10, 10, 10, 10, 10, 10, 5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0,
      0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, 0, 0, 0, 5, 5, 0, 0, 0};
   private static final int[] P_QUEEN = {-20, -10, -10, -5, -5, -10, -10, -20, -10, 0, 0, 0, 0, 0, 0, -10, -10, 0, 5, 5, 5, 5, 0, -10, -5, 0, 5, 5, 5, 5, 0,
      -5, 0, 0, 5, 5, 5, 5, 0, -5, -10, 5, 5, 5, 5, 5, 0, -10, -10, 0, 5, 0, 0, 0, 0, -10, -20, -10, -10, -5, -5, -10, -10, -20};
   private static final int[] P_KING_MG = {-30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40,
      -30, -30, -40, -40, -50, -50, -40, -40, -30, -20, -30, -30, -40, -40, -30, -30, -20, -10, -20, -20, -20, -20, -20, -20, -10, 20, 20, 0, 0, 0, 0, 20,
      20, 20, 30, 10, 0, 0, 10, 30, 20};
   private static final int[] P_KING_EG = {-50, -40, -30, -20, -20, -30, -40, -50, -30, -20, -10, 0, 0, -10, -20, -30, -30, -10, 20, 30, 30, 20, -10, -30, -30,
      -10, 30, 40, 40, 30, -10, -30, -30, -10, 30, 40, 40, 30, -10, -30, -30, -10, 20, 30, 30, 20, -10, -30, -30, -30, 0, 0, 0, 0, -30, -30, -50, -30, -30,
      -30, -30, -30, -30, -50};

   /** Static evaluation from the side to move's point of view, in centipawns. */
   public int evaluate() {
      int mg = 0;
      int phase = 0;
      int wb = 0, bb = 0;
      int wk = -1, bk = -1;
      int[] b = this.pos.board;
      for (int sq = 0; sq < 64; sq++) {
         int p = b[sq];
         if (p == 0) {
            continue;
         }
         int t = Math.abs(p);
         int s = p > 0 ? 1 : -1;
         int idx = p > 0 ? (7 - (sq >> 3)) * 8 + (sq & 7) : (sq >> 3) * 8 + (sq & 7);
         int v = PV[t];
         switch (t) {
            case Chess.PAWN -> v += P_PAWN[idx];
            case Chess.KNIGHT -> {
               v += P_KNIGHT[idx];
               phase += 1;
            }
            case Chess.BISHOP -> {
               v += P_BISHOP[idx];
               phase += 1;
               if (s > 0) {
                  wb++;
               } else {
                  bb++;
               }
            }
            case Chess.ROOK -> {
               v += P_ROOK[idx];
               phase += 2;
            }
            case Chess.QUEEN -> {
               v += P_QUEEN[idx];
               phase += 4;
            }
            default -> {
               v = 0;
               if (s > 0) {
                  wk = idx;
               } else {
                  bk = idx;
               }
            }
         }
         mg += s * v;
      }
      phase = Math.min(24, phase);
      if (wk >= 0) {
         mg += (P_KING_MG[wk] * phase + P_KING_EG[wk] * (24 - phase)) / 24;
      }
      if (bk >= 0) {
         mg -= (P_KING_MG[bk] * phase + P_KING_EG[bk] * (24 - phase)) / 24;
      }
      if (wb >= 2) {
         mg += 30;
      }
      if (bb >= 2) {
         mg -= 30;
      }
      return this.pos.side == Chess.WHITE ? mg + 8 : -mg + 8;
   }
}
