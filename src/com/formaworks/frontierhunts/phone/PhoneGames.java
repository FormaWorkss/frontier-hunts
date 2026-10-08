package com.formaworks.frontierhunts.phone;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.phone.games.Chess;
import com.formaworks.frontierhunts.phone.games.Dice;
import com.formaworks.frontierhunts.phone.games.Flush;
import com.formaworks.frontierhunts.phone.games.GameKind;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * [phone] The phone's games on the server. Online games are played here: the phone only sends what its player wants to
 * do and the server's copy of the game decides (a chess move must be legal and it must be that player's turn, the dice
 * are rolled by the server, a Flush! race is scored by replaying each player's shots on the shared seed). Invites
 * expire after a minute; a player who leaves has two minutes to come back before the game goes to the other; a player
 * who sits on their move for fifteen minutes loses on time. Results count in both players' records. Solo Flush! runs
 * for the leaderboard get a server seed and are replayed before their score is posted.
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class PhoneGames {
   public static final long INVITE_MS = 60_000L;
   public static final long AWAY_MS = 120_000L;
   public static final long MOVE_MS = 15L * 60_000L;
   public static final long KEEP_FINISHED_MS = 5L * 60_000L;
   public static final int FLUSH_COUNTDOWN = 60;
   public static final int MAX_SESSIONS_PER_PLAYER = 4;
   public static final int MAX_CHESS_PLY = 600;
   private static final Random RANDOM = new Random();
   static final Map<Long, Invite> INVITES = new LinkedHashMap<>();
   static final Map<Long, Session> SESSIONS = new LinkedHashMap<>();
   private static final Map<UUID, Run> RUNS = new LinkedHashMap<>();
   private static long nextId = 1L + (System.nanoTime() & 0xFFFFL);
   /** for tests: the clock (ms) */
   public static java.util.function.LongSupplier clock = System::currentTimeMillis;

   record Invite(long id, UUID from, String fromName, UUID to, String toName, int game, long expires) {
   }

   /** A ranked solo Flush! run handed out by the server. */
   record Run(long token, long seed, long started) {
   }

   /** An online game between two hunters (public for the QA harness). */
   public static final class Session {
      public final long id;
      public final int game;
      public final UUID[] players = new UUID[2];
      public final String[] names = new String[2];
      public Chess chess;
      public final List<Integer> moves = new ArrayList<>();
      public Dice dice;
      public long seed;
      /** real time (ms) the race's birds start flying */
      public long flushStart;
      public final int[] progress = new int[2];
      public final int[] finalScores = {-1, -1};
      public int seq;
      /** 0 playing, 1 finished */
      public int status;
      public int winner = -1;
      public String reason = "";
      public long lastMove;
      public final long[] awaySince = {0L, 0L};
      public long finishedAt;
      /** finished: who asked for a rematch, and the rematch's session once both did */
      public final boolean[] rematch = new boolean[2];
      public long next;

      Session(long id, int game) {
         this.id = id;
         this.game = game;
      }

      public int index(UUID p) {
         return p.equals(this.players[0]) ? 0 : (p.equals(this.players[1]) ? 1 : -1);
      }

      /** whose turn it is (0/1), -1 for a race */
      public int turn() {
         return switch (this.game) {
            case GameKind.CHESS -> this.chess.side == Chess.WHITE ? 0 : 1;
            case GameKind.DICE -> this.dice.turn;
            default -> -1;
         };
      }
   }

   private PhoneGames() {
   }

   private static long id() {
      return nextId++;
   }

   static ListTag onlineNames(ServerPlayer viewer) {
      ListTag list = new ListTag();
      for (ServerPlayer p : viewer.server.getPlayerList().getPlayers()) {
         if (list.size() < 64) {
            list.add(StringTag.valueOf(p.getScoreboardName()));
         }
      }
      return list;
   }

   // ------------------------------------------------------------------------------------------------ requests

   static void handle(ServerPlayer player, PhoneNet.Ask ask) {
      switch (ask.op()) {
         case PhoneNet.OP_INVITE -> invite(player, (int)ask.a(), ask.s());
         case PhoneNet.OP_ANSWER -> answer(player, ask.a(), ask.b() != 0L);
         case PhoneNet.OP_MOVE -> move(player, ask.a(), ask.v());
         case PhoneNet.OP_LEAVE -> leave(player, ask.a());
         case PhoneNet.OP_FLUSH_START -> flushStart(player);
         case PhoneNet.OP_FLUSH_SUBMIT -> flushSubmit(player, ask.a(), ask.v());
         case PhoneNet.OP_FLUSH_PROGRESS -> flushProgress(player, ask.a(), (int)ask.b());
         case PhoneNet.OP_STAT -> stat(player, (int)ask.a(), (int)ask.b(), ask.v().length > 0 ? ask.v()[0] : 0, ask.c());
         case PhoneNet.OP_REMATCH -> rematch(player, ask.a());
         default -> {
         }
      }
   }

   static int sessionsOf(UUID p) {
      int n = 0;
      for (Session s : SESSIONS.values()) {
         if (s.status == 0 && s.index(p) >= 0) {
            n++;
         }
      }
      return n;
   }

   static void invite(ServerPlayer from, int game, String toName) {
      if (game < 0 || game >= GameKind.COUNT) {
         return;
      }
      ServerPlayer to = from.server.getPlayerList().getPlayerByName(PhonePlaces.clean(toName, 16));
      if (to == null || to == from) {
         PhoneServer.toast(from, "That hunter is not online");
         return;
      }
      if (!PhoneServer.hasCharged(to)) {
         PhoneServer.toast(from, to.getScoreboardName() + " has no charged Field Phone");
         return;
      }
      if (sessionsOf(from.getUUID()) >= MAX_SESSIONS_PER_PLAYER || sessionsOf(to.getUUID()) >= MAX_SESSIONS_PER_PLAYER) {
         PhoneServer.toast(from, "Too many games running: finish one first");
         return;
      }
      long now = clock.getAsLong();
      int pendingTo = 0;
      for (Iterator<Invite> it = INVITES.values().iterator(); it.hasNext(); ) {
         Invite i = it.next();
         if (i.from.equals(from.getUUID())) {
            // one invite out at a time: a new one replaces the old
            it.remove();
         } else if (i.to.equals(to.getUUID())) {
            pendingTo++;
         }
      }
      if (pendingTo >= 4) {
         PhoneServer.toast(from, to.getScoreboardName() + " has too many invites waiting");
         return;
      }
      Invite inv = new Invite(id(), from.getUUID(), from.getScoreboardName(), to.getUUID(), to.getScoreboardName(), game, now + INVITE_MS);
      INVITES.put(inv.id, inv);
      sendState(from);
      sendState(to);
      CompoundTag n = new CompoundTag();
      n.putString("app", appOf(game));
      n.putString("title", GameKind.TITLES[game]);
      n.putString("body", from.getScoreboardName() + " challenges you to a game");
      PhoneNet.send(to, PhoneNet.K_NOTICE, n);
   }

   static String appOf(int game) {
      return switch (game) {
         case GameKind.CHESS -> "chess";
         case GameKind.DICE -> "dice";
         default -> "flush";
      };
   }

   static void answer(ServerPlayer player, long inviteId, boolean accept) {
      Invite inv = INVITES.get(inviteId);
      if (inv == null || !inv.to.equals(player.getUUID())) {
         sendState(player);
         return;
      }
      INVITES.remove(inviteId);
      ServerPlayer from = player.server.getPlayerList().getPlayer(inv.from);
      if (!accept || from == null || inv.expires < clock.getAsLong()) {
         if (from != null) {
            sendState(from);
            if (!accept) {
               PhoneServer.toast(from, player.getScoreboardName() + " declined");
            }
         }
         if (accept) {
            PhoneServer.toast(player, "That invite has expired");
         }
         sendState(player);
         return;
      }
      if (sessionsOf(player.getUUID()) >= MAX_SESSIONS_PER_PLAYER || sessionsOf(from.getUUID()) >= MAX_SESSIONS_PER_PLAYER) {
         PhoneServer.toast(player, "Too many games running: finish one first");
         sendState(player);
         return;
      }
      Session s = start(inv.game, from.getUUID(), from.getScoreboardName(), player.getUUID(), player.getScoreboardName());
      push(player.server, s);
      CompoundTag n = new CompoundTag();
      n.putString("app", appOf(inv.game));
      n.putString("title", GameKind.TITLES[inv.game]);
      n.putString("body", player.getScoreboardName() + " accepted: your move");
      n.putLong("session", s.id);
      PhoneNet.send(from, PhoneNet.K_NOTICE, n);
   }

   /** A new online game between {@code a} (moves first, white) and {@code b}. */
   public static Session start(int game, UUID a, String aName, UUID b, String bName) {
      Session s = new Session(id(), game);
      s.players[0] = a;
      s.players[1] = b;
      s.names[0] = aName;
      s.names[1] = bName;
      switch (game) {
         case GameKind.CHESS -> s.chess = new Chess();
         case GameKind.DICE -> s.dice = new Dice(2);
         default -> {
            s.seed = RANDOM.nextLong();
            s.flushStart = clock.getAsLong() + FLUSH_COUNTDOWN * 50L;
         }
      }
      s.lastMove = clock.getAsLong();
      SESSIONS.put(s.id, s);
      return s;
   }

   /**
    * Applies a move from player {@code who} (0/1) to a session; false (and no change) when it is not legal: a finished
    * game, not their turn, or a move the rules do not allow. Chess {from, to, promo}; dice {0, holdMask} roll, {1, box}.
    */
   public static boolean apply(Session s, int who, int[] mv, Random rnd) {
      if (s.status != 0 || who < 0 || who > 1 || mv == null || s.turn() != who) {
         return false;
      }
      switch (s.game) {
         case GameKind.CHESS -> {
            if (mv.length < 3 || mv[0] < 0 || mv[0] > 63 || mv[1] < 0 || mv[1] > 63 || mv[2] < 0 || mv[2] > Chess.QUEEN) {
               return false;
            }
            int m = s.chess.find(mv[0], mv[1], mv[2]);
            if (m < 0) {
               return false;
            }
            s.chess.make(m);
            s.moves.add(m);
            int[] st = s.chess.statusReason();
            if (st[0] != Chess.PLAYING) {
               finish(s, st[0] == Chess.WHITE_WINS ? 0 : (st[0] == Chess.BLACK_WINS ? 1 : 2), reason(st[1]));
            } else if (s.moves.size() >= MAX_CHESS_PLY) {
               finish(s, 2, "Move limit");
            }
         }
         case GameKind.DICE -> {
            if (mv.length < 2) {
               return false;
            }
            boolean ok = mv[0] == 0 ? s.dice.roll(mv[1] & 31, rnd) : (mv[0] == 1 && s.dice.score(mv[1]));
            if (!ok) {
               return false;
            }
            if (s.dice.over()) {
               int w = s.dice.winner();
               finish(s, w, w == 2 ? "Tie" : "Final score");
            }
         }
         default -> {
            return false;
         }
      }
      s.seq++;
      s.lastMove = clock.getAsLong();
      return true;
   }

   static String reason(int r) {
      return switch (r) {
         case Chess.R_MATE -> "Checkmate";
         case Chess.R_STALEMATE -> "Stalemate";
         case Chess.R_FIFTY -> "Fifty-move rule";
         case Chess.R_REPETITION -> "Threefold repetition";
         case Chess.R_MATERIAL -> "Insufficient material";
         default -> "";
      };
   }

   public static void finish(Session s, int winner, String reason) {
      if (s.status != 0) {
         return;
      }
      s.status = 1;
      s.winner = winner;
      s.reason = reason;
      s.finishedAt = clock.getAsLong();
      s.seq++;
   }

   /** Counts a finished online game in both players' records (once). */
   private static void record(MinecraftServer server, Session s) {
      PhoneStore store = PhoneStore.get(server);
      for (int i = 0; i < 2; i++) {
         if (s.players[i] == null) {
            continue;
         }
         PhoneStore.Hunter h = store.hunter(s.players[i], s.names[i]);
         int res = s.winner == 2 ? GameKind.DRAW : (s.winner == i ? GameKind.WIN : GameKind.LOSS);
         if (s.winner < 0) {
            continue;
         }
         h.stats[GameKind.stat(s.game, GameKind.MODE_ONLINE, res)]++;
      }
      store.setDirty();
   }

   static void move(ServerPlayer player, long sessionId, int[] mv) {
      Session s = SESSIONS.get(sessionId);
      if (s == null) {
         sendState(player);
         return;
      }
      int who = s.index(player.getUUID());
      if (who < 0) {
         return;
      }
      if (!apply(s, who, mv, RANDOM)) {
         // out of step: send the real game back so the phone shows it
         PhoneNet.send(player, PhoneNet.K_GAMES, state(player));
         return;
      }
      if (s.status != 0) {
         record(player.server, s);
      }
      push(player.server, s);
   }

   static void leave(ServerPlayer player, long sessionId) {
      Session s = SESSIONS.get(sessionId);
      if (s == null) {
         return;
      }
      int who = s.index(player.getUUID());
      if (who < 0) {
         return;
      }
      if (s.status == 0) {
         finish(s, 1 - who, player.getScoreboardName() + " resigned");
         record(player.server, s);
         push(player.server, s);
      } else {
         // finished: this player is done looking at it (no rematch with them now)
         s.players[who] = null;
         s.rematch[0] = s.rematch[1] = false;
         s.seq++;
         if (s.players[0] == null && s.players[1] == null) {
            SESSIONS.remove(sessionId);
         }
         sendState(player);
         push(player.server, s);
      }
   }

   /**
    * A rematch of a finished online game: the first player to ask waits for the other (who gets a notice); when both
    * have asked, a new game starts with the sides swapped (the one who moved second now moves first). Refused when the
    * game is still running, the asker was not in it, the opponent has left it, is offline, or has no charged phone.
    */
   static void rematch(ServerPlayer player, long sessionId) {
      Session s = SESSIONS.get(sessionId);
      if (s == null) {
         sendState(player);
         return;
      }
      int who = s.index(player.getUUID());
      if (who < 0 || s.status == 0 || s.next != 0L) {
         sendState(player);
         return;
      }
      int other = 1 - who;
      ServerPlayer opp = s.players[other] == null ? null : player.server.getPlayerList().getPlayer(s.players[other]);
      if (opp == null) {
         PhoneServer.toast(player, (s.names[other] == null ? "Your opponent" : s.names[other]) + " has left this game");
         sendState(player);
         return;
      }
      if (!PhoneServer.hasCharged(opp)) {
         PhoneServer.toast(player, opp.getScoreboardName() + " has no charged Field Phone");
         return;
      }
      Session next = rematch(s, who);
      if (next == null) {
         if (s.rematch[who]) {
            CompoundTag n = new CompoundTag();
            n.putString("app", appOf(s.game));
            n.putString("title", GameKind.TITLES[s.game]);
            n.putString("body", player.getScoreboardName() + " wants a rematch");
            PhoneNet.send(opp, PhoneNet.K_NOTICE, n);
         } else {
            PhoneServer.toast(player, "Too many games running: finish one first");
         }
         push(player.server, s);
         return;
      }
      push(player.server, next);
      push(player.server, s);
   }

   /**
    * Records player {@code who}'s rematch request on finished session {@code s}; returns the new game once both asked
    * (sides swapped), else null. Pure session logic (tested by the QA harness).
    */
   public static Session rematch(Session s, int who) {
      if (s == null || who < 0 || who > 1 || s.status == 0 || s.next != 0L || s.players[who] == null || s.players[1 - who] == null) {
         return null;
      }
      if (!s.rematch[1 - who]) {
         if (sessionsOf(s.players[who]) >= MAX_SESSIONS_PER_PLAYER) {
            return null;
         }
         if (!s.rematch[who]) {
            s.rematch[who] = true;
            s.seq++;
         }
         return null;
      }
      if (sessionsOf(s.players[0]) >= MAX_SESSIONS_PER_PLAYER || sessionsOf(s.players[1]) >= MAX_SESSIONS_PER_PLAYER) {
         return null;
      }
      s.rematch[who] = true;
      Session n = start(s.game, s.players[1], s.names[1], s.players[0], s.names[0]);
      s.next = n.id;
      s.seq++;
      // the old game stays a little while so both phones see where the rematch went
      s.finishedAt = Math.min(s.finishedAt, clock.getAsLong());
      return n;
   }

   // ------------------------------------------------------------------------------------------------ Flush!

   static void flushStart(ServerPlayer player) {
      Run r = new Run(RANDOM.nextLong() | 1L, RANDOM.nextLong(), clock.getAsLong());
      RUNS.put(player.getUUID(), r);
      CompoundTag t = new CompoundTag();
      t.putLong("token", r.token);
      t.putLong("seed", r.seed);
      PhoneNet.send(player, PhoneNet.K_FLUSH, t);
   }

   static void flushSubmit(ServerPlayer player, long tokenOrSession, int[] log) {
      Session s = SESSIONS.get(tokenOrSession);
      if (s != null && s.game == GameKind.FLUSH) {
         int who = s.index(player.getUUID());
         if (who < 0 || s.status != 0 || s.finalScores[who] >= 0) {
            return;
         }
         // a race can't be over before its sixty seconds have run on the server
         if (clock.getAsLong() < s.flushStart + Flush.TICKS * 50L - 3000L) {
            return;
         }
         int score = Flush.replay(s.seed, log);
         s.finalScores[who] = Math.max(0, score);
         s.progress[who] = s.finalScores[who];
         s.seq++;
         if (s.finalScores[1 - who] >= 0) {
            int a = s.finalScores[0], b = s.finalScores[1];
            finish(s, a > b ? 0 : (b > a ? 1 : 2), "Final score");
            record(player.server, s);
         }
         push(player.server, s);
         return;
      }
      Run r = RUNS.get(player.getUUID());
      if (r == null || r.token != tokenOrSession) {
         return;
      }
      RUNS.remove(player.getUUID());
      long took = clock.getAsLong() - r.started;
      CompoundTag t = new CompoundTag();
      int score = Flush.replay(r.seed, log);
      // a run is sixty seconds of play: a result much sooner, or very much later, was not played on this seed
      if (score < 0 || took < (Flush.TICKS / 20L) * 1000L - 3000L || took > 30L * 60_000L) {
         t.putString("result", "This run could not be checked, so it was not posted.");
         PhoneNet.send(player, PhoneNet.K_FLUSH, t);
         return;
      }
      PhoneStore store = PhoneStore.get(player.server);
      int before = store.hunter(player.getUUID(), player.getScoreboardName()).flushBest;
      boolean board = store.flushScore(player.getUUID(), player.getScoreboardName(), score, clock.getAsLong());
      int rank = 0;
      for (int i = 0; i < store.flushBoard.size(); i++) {
         if (store.flushBoard.get(i).id().equals(player.getUUID())) {
            rank = i + 1;
         }
      }
      t.putInt("score", score);
      t.putInt("best", Math.max(before, score));
      t.putInt("rank", rank);
      t.putString("result", score > before ? "New personal best!" + (board && rank > 0 ? " #" + rank + " on the server." : "")
         : (rank > 0 ? "You are #" + rank + " on the server." : "Checked by the server."));
      PhoneNet.send(player, PhoneNet.K_FLUSH, t);
      sendState(player);
   }

   static void flushProgress(ServerPlayer player, long sessionId, int score) {
      Session s = SESSIONS.get(sessionId);
      if (s == null || s.game != GameKind.FLUSH || s.status != 0) {
         return;
      }
      int who = s.index(player.getUUID());
      if (who < 0) {
         return;
      }
      // live scores only show the race; the result comes from the replay
      s.progress[who] = Math.max(0, Math.min(50000, score));
      ServerPlayer other = s.players[1 - who] == null ? null : player.server.getPlayerList().getPlayer(s.players[1 - who]);
      if (other != null) {
         s.seq++;
         PhoneNet.send(other, PhoneNet.K_GAMES, state(other));
      }
   }

   // ------------------------------------------------------------------------------------------------ local games

   static void stat(ServerPlayer player, int game, int mode, int result, long extra) {
      if (game < 0 || game >= GameKind.COUNT || mode != GameKind.MODE_SOLO && mode != GameKind.MODE_AI) {
         return;
      }
      PhoneStore store = PhoneStore.get(player.server);
      PhoneStore.Hunter h = store.hunter(player.getUUID(), player.getScoreboardName());
      if (mode == GameKind.MODE_AI) {
         if (result >= GameKind.WIN && result <= GameKind.DRAW) {
            h.stats[GameKind.stat(game, mode, result)]++;
         }
      } else if (game == GameKind.CHESS) {
         // a solved puzzle (its number)
         if (result >= 0 && result < 64) {
            h.puzzles |= 1L << result;
         }
      } else if (game == GameKind.DICE) {
         // a solo score: the best counts on the phone (the highest possible game is 1575)
         if (result > h.diceBest && result <= 1575) {
            h.diceBest = result;
         }
      }
      store.setDirty();
   }

   // ------------------------------------------------------------------------------------------------ state for the phone

   static CompoundTag state(ServerPlayer player) {
      UUID me = player.getUUID();
      CompoundTag t = new CompoundTag();
      long now = clock.getAsLong();
      ListTag invites = new ListTag();
      for (Invite i : INVITES.values()) {
         if (i.to.equals(me) && i.expires >= now) {
            CompoundTag c = new CompoundTag();
            c.putLong("id", i.id);
            c.putString("from", i.fromName);
            c.putInt("game", i.game);
            c.putLong("left", i.expires - now);
            invites.add(c);
         }
      }
      t.put("invites", invites);
      ListTag sessions = new ListTag();
      for (Session s : SESSIONS.values()) {
         int who = s.index(me);
         if (who < 0) {
            continue;
         }
         CompoundTag c = new CompoundTag();
         c.putLong("id", s.id);
         c.putInt("game", s.game);
         c.putInt("me", who);
         c.putString("n0", s.names[0]);
         c.putString("n1", s.names[1]);
         c.putInt("seq", s.seq);
         c.putInt("status", s.status);
         c.putInt("winner", s.winner);
         c.putString("reason", s.reason);
         int other = 1 - who;
         c.putBoolean("rm", s.rematch[who]);
         c.putBoolean("rt", s.rematch[other]);
         c.putBoolean("gone", s.status != 0 && s.players[other] == null);
         c.putLong("next", s.next);
         boolean away = s.awaySince[other] != 0L;
         c.putBoolean("away", away);
         c.putInt("awaySeconds", away ? (int)Math.max(0L, (AWAY_MS - (now - s.awaySince[other])) / 1000L) : 0);
         int[] st;
         switch (s.game) {
            case GameKind.CHESS -> {
               int[] pos = s.chess.save();
               st = new int[72 + s.moves.size()];
               st[0] = s.turn();
               System.arraycopy(pos, 0, st, 1, 70);
               st[71] = s.moves.size();
               for (int i = 0; i < s.moves.size(); i++) {
                  st[72 + i] = s.moves.get(i);
               }
            }
            case GameKind.DICE -> {
               int[] d = s.dice.save();
               st = new int[1 + d.length];
               st[0] = s.turn();
               System.arraycopy(d, 0, st, 1, d.length);
            }
            default -> {
               st = new int[0];
               c.putLong("seed", s.seed);
               c.putInt("startIn", (int)Math.max(0L, (s.flushStart - now) / 50L));
               c.putInt("opp", s.progress[other]);
               c.putIntArray("final", s.finalScores);
            }
         }
         c.putIntArray("state", st);
         sessions.add(c);
      }
      t.put("sessions", sessions);
      PhoneStore store = PhoneStore.get(player.server);
      PhoneStore.Hunter h = store.hunter(me, player.getScoreboardName());
      t.putIntArray("stats", h.stats);
      t.putInt("flushBest", h.flushBest);
      t.putInt("diceBest", h.diceBest);
      t.putLong("puzzles", h.puzzles);
      ListTag board = new ListTag();
      for (PhoneStore.Score sc : store.flushBoard) {
         CompoundTag c = new CompoundTag();
         c.putString("name", sc.name());
         c.putInt("score", sc.score());
         c.putBoolean("me", sc.id().equals(me));
         board.add(c);
      }
      t.put("board", board);
      t.put("online", onlineNames(player));
      return t;
   }

   public static void sendState(ServerPlayer player) {
      PhoneNet.send(player, PhoneNet.K_GAMES, state(player));
   }

   private static void push(MinecraftServer server, Session s) {
      for (UUID id : s.players) {
         ServerPlayer p = id == null ? null : server.getPlayerList().getPlayer(id);
         if (p != null) {
            sendState(p);
         }
      }
   }

   // ------------------------------------------------------------------------------------------------ housekeeping

   @SubscribeEvent
   public static void tick(ServerTickEvent.Post e) {
      MinecraftServer server = e.getServer();
      if (server.getTickCount() % 20 != 0 || INVITES.isEmpty() && SESSIONS.isEmpty() && RUNS.isEmpty()) {
         return;
      }
      sweep(server);
   }

   /** Once a second: expired invites, finished games cleared away, players gone too long or out of time lose. */
   public static void sweep(MinecraftServer server) {
      long now = clock.getAsLong();
      INVITES.values().removeIf(i -> i.expires < now - 5000L);
      RUNS.values().removeIf(r -> now - r.started > 30L * 60_000L);
      for (Iterator<Session> it = SESSIONS.values().iterator(); it.hasNext(); ) {
         Session s = it.next();
         if (s.status != 0) {
            if (now - s.finishedAt > KEEP_FINISHED_MS) {
               it.remove();
            }
            continue;
         }
         boolean changed = false;
         for (int i = 0; i < 2; i++) {
            if (s.awaySince[i] != 0L && now - s.awaySince[i] > AWAY_MS) {
               finish(s, 1 - i, s.names[i] + " left the game");
               changed = true;
               break;
            }
         }
         int turn = s.turn();
         if (!changed && turn >= 0 && now - s.lastMove > MOVE_MS) {
            finish(s, 1 - turn, s.names[turn] + " ran out of time");
            changed = true;
         }
         if (!changed && s.game == GameKind.FLUSH && now > s.flushStart + Flush.TICKS * 50L + 90_000L) {
            // a racer never sent their run: the other wins on what they sent
            int a = s.finalScores[0], b = s.finalScores[1];
            finish(s, a < 0 && b < 0 ? 2 : (a > b ? 0 : (b > a ? 1 : 2)), "Time");
            changed = true;
         }
         if (changed) {
            record(server, s);
            push(server, s);
         }
      }
   }

   @SubscribeEvent
   public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
      if (e.getEntity() instanceof ServerPlayer sp) {
         away(sp.server, sp.getUUID());
      }
   }

   /** A player left the server: their invites go, their running games wait {@link #AWAY_MS} for them. */
   public static void away(MinecraftServer server, UUID id) {
      INVITES.values().removeIf(i -> i.from.equals(id) || i.to.equals(id));
      RUNS.remove(id);
      for (Session s : SESSIONS.values()) {
         int who = s.index(id);
         if (who >= 0 && s.status == 0) {
            s.awaySince[who] = clock.getAsLong();
            s.seq++;
            push(server, s);
         }
      }
   }

   /** Drops a game (QA cleanup). */
   public static void forget(long sessionId) {
      SESSIONS.remove(sessionId);
   }

   @SubscribeEvent
   public static void login(PlayerEvent.PlayerLoggedInEvent e) {
      if (!(e.getEntity() instanceof ServerPlayer sp)) {
         return;
      }
      boolean any = false;
      for (Session s : SESSIONS.values()) {
         int who = s.index(sp.getUUID());
         if (who >= 0) {
            s.awaySince[who] = 0L;
            s.names[who] = sp.getScoreboardName();
            s.seq++;
            push(sp.server, s);
            any = true;
         }
      }
      if (any) {
         sendState(sp);
      }
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent e) {
      INVITES.clear();
      SESSIONS.clear();
      RUNS.clear();
   }
}
