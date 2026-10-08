package com.formaworks.frontierqa;

import com.formaworks.frontierhunts.HunterLedger;
import com.formaworks.frontierhunts.benches.Bench;
import com.formaworks.frontierhunts.benches.BenchCatalog;
import com.formaworks.frontierhunts.benches.BenchMenu;
import com.formaworks.frontierhunts.benches.BenchTab;
import com.formaworks.frontierhunts.expedition.Campaign;
import com.formaworks.frontierhunts.expedition.CameraRegistry;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.ExpeditionLedger;
import com.formaworks.frontierhunts.hunts.HuntHooks;
import com.formaworks.frontierhunts.phone.FieldPhoneItem;
import com.formaworks.frontierhunts.phone.PhoneCams;
import com.formaworks.frontierhunts.phone.PhoneContent;
import com.formaworks.frontierhunts.phone.PhoneContracts;
import com.formaworks.frontierhunts.phone.PhoneGames;
import com.formaworks.frontierhunts.phone.PhoneMessages;
import com.formaworks.frontierhunts.phone.PhonePhotos;
import com.formaworks.frontierhunts.phone.PhoneNet;
import com.formaworks.frontierhunts.phone.PhoneServer;
import com.formaworks.frontierhunts.phone.PhoneSignal;
import com.formaworks.frontierhunts.phone.games.Chess;
import com.formaworks.frontierhunts.phone.games.Dice;
import com.formaworks.frontierhunts.phone.games.GameKind;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;

/**
 * [phone] /fhqa phone contracts | cams | games | messages | station | recipe | all. Prints "phone PASS ..." / "phone FAIL ...".
 * The Field Phone's server rules, tested on the server code itself (a FakePlayer has no client and cannot hold a phone
 * screen): contracts go through the board's rules, trail cameras are listed only to their owner, online games refuse
 * illegal moves and moves out of turn, find the winner, settle a disconnect and start rematches with sides swapped,
 * texts are rate limited, the retired Camera Base Station still works but cannot be made, and the phone is made at the
 * Frontier Workbench.
 */
final class PhoneTest {
   private PhoneTest() {
   }

   static void verdict(boolean ok, String what) {
      FrontierQa.say("phone " + (ok ? "PASS " : "FAIL ") + what);
   }

   static int run(CommandSourceStack src, String which) {
      MinecraftServer server = src.getServer();
      try {
         switch (which.trim()) {
            case "contracts" -> contracts(server);
            case "cams" -> cams(server);
            case "games" -> games(server);
            case "messages" -> messages(server);
            case "station" -> station(server);
            case "recipe" -> recipe(server);
            case "photos" -> photos(server);
            case "camera" -> camera(server);
            case "all" -> {
               contracts(server);
               cams(server);
               games(server);
               messages(server);
               station(server);
               recipe(server);
               photos(server);
               camera(server);
            }
            default -> verdict(false, "unknown case " + which + " (contracts | cams | games | messages | station | recipe | all)");
         }
      } catch (Exception e) {
         verdict(false, which + " threw " + e);
      }
      return 1;
   }

   static ServerPlayer hunter(MinecraftServer server, boolean phone) throws Exception {
      Persist.join(server, false);
      ServerPlayer fp = FrontierQa.fake(server);
      fp.getInventory().clearContent();
      if (phone) {
         fp.getInventory().add(new ItemStack(PhoneContent.PHONE.get()));
      }
      PhoneServer.reset(fp);
      return fp;
   }

   // ============================================================================================ contracts

   static void contracts(MinecraftServer server) throws Exception {
      ServerPlayer fp = hunter(server, false);
      ServerLevel level = fp.serverLevel();
      ExpeditionLedger.Hunter h = ExpeditionLedger.get(level).hunter(fp.getUUID());
      int savedContract = h.contract, savedCount = h.contractCount;
      long savedReady = h.contractReadyAt, savedDeadline = h.deadline, savedStarted = h.started;
      int on = -1, off = -1;
      for (int i = 0; i < Campaign.CONTRACTS.size(); i++) {
         if (HuntHooks.contractOffered(i)) {
            on = on < 0 ? i : on;
         } else {
            off = off < 0 ? i : off;
         }
      }
      if (on < 0) {
         verdict(false, "no contract is on the board this half-month");
         return;
      }
      try {
         h.contract = -1;
         h.contractCount = 0;
         h.contractReadyAt = 0L;
         // no phone: the request is dropped before it reaches the contracts
         PhoneServer.handle(fp, new PhoneNet.Ask(PhoneNet.OP_CONTRACT, PhoneContracts.C_CONTRACT_ACCEPT, on, 0L, "", null, null));
         verdict(h.contract == -1, "without a charged phone an accept is ignored (contract " + h.contract + ")");
         fp.getInventory().add(new ItemStack(PhoneContent.PHONE.get()));
         int bars = PhoneSignal.bars(level, fp.getX(), fp.getY(), fp.getZ());
         if (off >= 0) {
            PhoneServer.handle(fp, new PhoneNet.Ask(PhoneNet.OP_CONTRACT, PhoneContracts.C_CONTRACT_ACCEPT, off, 0L, "", null, null));
            verdict(h.contract == -1, "a contract that is not on this half-month's board (#" + off + ") is refused (contract " + h.contract + ")");
         } else {
            verdict(true, "every contract is on the board now (off-board refusal not testable this half-month)");
         }
         PhoneServer.handle(fp, new PhoneNet.Ask(PhoneNet.OP_CONTRACT, PhoneContracts.C_CONTRACT_ACCEPT, 999999, 0L, "", null, null));
         verdict(h.contract == -1, "a contract index out of range is refused");
         PhoneServer.handle(fp, new PhoneNet.Ask(PhoneNet.OP_CONTRACT, PhoneContracts.C_CONTRACT_ACCEPT, on, 0L, "", null, null));
         verdict(bars > 0 && h.contract == on, "with a phone and " + bars + " bars a board contract (#" + on + ") is accepted (contract " + h.contract + ")");
         int other = -1;
         for (int i = 0; i < Campaign.CONTRACTS.size(); i++) {
            if (i != on && HuntHooks.contractOffered(i)) {
               other = i;
               break;
            }
         }
         if (other >= 0) {
            PhoneContracts.op(fp, PhoneContracts.C_CONTRACT_ACCEPT, other, "");
            verdict(h.contract == on, "a second contract can't be taken while one runs (contract " + h.contract + ")");
         }
         int tokens = HunterLedger.get(level).hunter(fp.getUUID()).tokens();
         String serial = Long.toString(h.contractSerial);
         PhoneContracts.op(fp, PhoneContracts.C_CONTRACT_CLAIM, 0, serial);
         verdict(h.contract == on && HunterLedger.get(level).hunter(fp.getUUID()).tokens() == tokens, "an unfinished contract can't be handed in");
         Campaign.Mission m = Campaign.CONTRACTS.get(on);
         h.contractCount = m.amount();
         h.contractReadyAt = Math.max(1L, level.getGameTime());
         PhoneContracts.op(fp, PhoneContracts.C_CONTRACT_CLAIM, 0, "12345" + serial);
         verdict(h.contract == on, "a hand-in for another contract serial is refused");
         PhoneContracts.op(fp, PhoneContracts.C_CONTRACT_CLAIM, 0, serial);
         int after = HunterLedger.get(level).hunter(fp.getUUID()).tokens();
         verdict(h.contract == -1 && after >= tokens, "a finished contract is paid and closed (tokens " + tokens + " -> " + after + ")");
         PhoneContracts.op(fp, PhoneContracts.C_CONTRACT_ACCEPT, on, "");
         PhoneContracts.op(fp, PhoneContracts.C_CONTRACT_ABANDON, 0, "");
         verdict(h.contract == -1, "abandon drops the running contract");
      } finally {
         h.contract = savedContract;
         h.contractCount = savedCount;
         h.contractReadyAt = savedReady;
         h.deadline = savedDeadline;
         h.started = savedStarted;
         ExpeditionLedger.get(level).setDirty();
         fp.getInventory().clearContent();
      }
   }

   // ============================================================================================ trail cams

   static void cams(MinecraftServer server) throws Exception {
      ServerPlayer fp = hunter(server, true);
      ServerLevel level = fp.serverLevel();
      CameraRegistry reg = CameraRegistry.get(level);
      BlockPos mine = fp.blockPosition().offset(3, 0, 5);
      BlockPos theirs = fp.blockPosition().offset(-4, 0, 6);
      UUID stranger = UUID.nameUUIDFromBytes("fhqa-phone-stranger".getBytes());
      try {
         reg.place(level, mine, Direction.NORTH, fp.getUUID(), 2400, "QA mine");
         reg.place(level, theirs, Direction.NORTH, stranger, 2400, "QA theirs");
         List<CameraRegistry.Station> list = PhoneCams.mine(fp);
         boolean hasMine = false, hasTheirs = false;
         for (CameraRegistry.Station s : list) {
            hasMine |= s.pos.equals(mine);
            hasTheirs |= s.pos.equals(theirs);
            if (!fp.getUUID().equals(s.owner)) {
               hasTheirs = true;
            }
         }
         verdict(hasMine && !hasTheirs, "the Trail Cams list holds only the hunter's own cameras (" + list.size() + " listed, own " + hasMine
            + ", someone else's " + hasTheirs + ")");
         verdict(PhoneCams.own(fp, mine) != null && PhoneCams.own(fp, theirs) == null, "a photo/clear/watch request for someone else's camera finds nothing");
         CameraRegistry.Station t = reg.find(level, theirs);
         if (t != null && t.roll.isEmpty()) {
            t.roll.add(new CameraRegistry.Capture(null, 1L, "QA", 12, "open ground", 20.0F, 0.0F, 0.0F, CameraRegistry.WALKING, CameraRegistry.DAY));
         }
         int before = t == null ? -1 : t.roll.size();
         PhoneServer.handle(fp, new PhoneNet.Ask(PhoneNet.OP_CAMERA, PhoneCams.CAM_CLEAR, theirs.asLong(), 0L, "", null, null));
         CameraRegistry.Station t2 = reg.find(level, theirs);
         verdict(t2 != null && before > 0 && t2.roll.size() == before, "clearing someone else's camera from the phone does nothing (" + before + " -> "
            + (t2 == null ? -1 : t2.roll.size()) + " photos)");
         fp.getInventory().clearContent();
         PhoneServer.reset(fp);
         verdict(!PhoneServer.mayUseCameras(fp), "without a phone (and away from a base station) the cameras can't be reached");
      } finally {
         reg.remove(level, mine);
         reg.remove(level, theirs);
         fp.getInventory().clearContent();
      }
   }

   // ============================================================================================ online games

   static int sq(String s) {
      return (s.charAt(0) - 'a') + (s.charAt(1) - '1') * 8;
   }

   static int[] mv(String from, String to) {
      return new int[]{sq(from), sq(to), 0};
   }

   static void games(MinecraftServer server) {
      UUID a = UUID.nameUUIDFromBytes("fhqa-phone-a".getBytes()), b = UUID.nameUUIDFromBytes("fhqa-phone-b".getBytes());
      Random rnd = new Random(7L);
      java.util.function.LongSupplier realClock = PhoneGames.clock;
      long[] now = {System.currentTimeMillis()};
      PhoneGames.clock = () -> now[0];
      PhoneGames.Session s = null, d = null, r = null, n = null;
      try {
         s = PhoneGames.start(GameKind.CHESS, a, "QA_A", b, "QA_B");
         verdict(!PhoneGames.apply(s, 1, mv("e7", "e5"), rnd), "chess: black can't move first");
         verdict(!PhoneGames.apply(s, 0, mv("e2", "e5"), rnd), "chess: an illegal move (e2-e5) is refused");
         verdict(!PhoneGames.apply(s, 0, new int[]{99, -3, 0}, rnd) && !PhoneGames.apply(s, 0, new int[]{12}, rnd) && !PhoneGames.apply(s, 0, null, rnd),
            "chess: out-of-range, short and missing moves are refused");
         verdict(!PhoneGames.apply(s, 5, mv("e2", "e4"), rnd), "chess: a player who is not in the game can't move");
         boolean ok = PhoneGames.apply(s, 0, mv("f2", "f3"), rnd) && PhoneGames.apply(s, 1, mv("e7", "e5"), rnd);
         verdict(ok && !PhoneGames.apply(s, 1, mv("d7", "d6"), rnd), "chess: legal moves in turn are played, a second move in a row is refused");
         ok &= PhoneGames.apply(s, 0, mv("g2", "g4"), rnd) && PhoneGames.apply(s, 1, mv("d8", "h4"), rnd);
         verdict(ok && s.status == 1 && s.winner == 1 && s.reason.equals("Checkmate"), "chess: fool's mate is found (status " + s.status + ", winner "
            + s.winner + ", " + s.reason + ")");
         verdict(!PhoneGames.apply(s, 0, mv("e2", "e4"), rnd), "chess: no moves after the game is over");
         // rematch: both ask, the sides swap
         verdict(PhoneGames.rematch(s, 0) == null && s.rematch[0], "rematch: the first ask waits for the other player");
         r = PhoneGames.rematch(s, 1);
         verdict(r != null && a.equals(r.players[1]) && b.equals(r.players[0]) && r.status == 0 && s.next == r.id,
            "rematch: both asked, a new game starts with the sides swapped");
         verdict(PhoneGames.rematch(s, 0) == null, "rematch: a finished game gives only one rematch");
         PhoneGames.Session live = PhoneGames.start(GameKind.CHESS, a, "QA_A", b, "QA_B");
         verdict(PhoneGames.rematch(live, 0) == null && !live.rematch[0], "rematch: a game still running can't be rematched");
         PhoneGames.forget(live.id);

         d = PhoneGames.start(GameKind.DICE, a, "QA_A", b, "QA_B");
         verdict(!PhoneGames.apply(d, 0, new int[]{1, Dice.CHANCE}, rnd), "dice: scoring before the first roll is refused");
         verdict(!PhoneGames.apply(d, 1, new int[]{0, 0}, rnd), "dice: the second player can't roll in the first player's turn");
         boolean rolled = PhoneGames.apply(d, 0, new int[]{0, 0}, rnd) && PhoneGames.apply(d, 0, new int[]{0, 0}, rnd) && PhoneGames.apply(d, 0, new int[]{0, 0}, rnd);
         verdict(rolled && !PhoneGames.apply(d, 0, new int[]{0, 0}, rnd), "dice: three rolls a turn, a fourth is refused");
         verdict(!PhoneGames.apply(d, 0, new int[]{1, 99}, rnd) && PhoneGames.apply(d, 0, new int[]{1, Dice.CHANCE}, rnd) && d.turn() == 1,
            "dice: a box out of range is refused, a real box ends the turn");
         verdict(!PhoneGames.apply(d, 1, new int[]{1, 0}, rnd), "dice: the next player must roll before scoring");
         // play the dice game out: every box for both players; the server finds the winner
         int guard = 0;
         while (d.status == 0 && guard++ < 200) {
            int who = d.turn();
            PhoneGames.apply(d, who, new int[]{0, 0}, rnd);
            for (int c = 0; c < Dice.CATS; c++) {
               if (d.dice.canScore(c) && PhoneGames.apply(d, who, new int[]{1, c}, rnd)) {
                  break;
               }
            }
         }
         int ta = d.dice.total(0), tb = d.dice.total(1);
         int expect = ta > tb ? 0 : (tb > ta ? 1 : 2);
         verdict(d.status == 1 && d.winner == expect, "dice: a full game ends with the right winner (" + ta + " – " + tb + ", winner " + d.winner + ")");

         // disconnect: the player who leaves has two minutes to come back, then loses
         n = PhoneGames.start(GameKind.CHESS, a, "QA_A", b, "QA_B");
         PhoneGames.away(server, b);
         now[0] += 60_000L;
         PhoneGames.sweep(server);
         verdict(n.status == 0 && n.awaySince[1] != 0L, "disconnect: after a minute away the game still waits");
         now[0] += PhoneGames.AWAY_MS;
         PhoneGames.sweep(server);
         verdict(n.status == 1 && n.winner == 0, "disconnect: gone too long, the game goes to the other player (" + n.reason + ")");
         PhoneGames.Session t = PhoneGames.start(GameKind.CHESS, a, "QA_A", b, "QA_B");
         now[0] += PhoneGames.MOVE_MS + 1000L;
         PhoneGames.sweep(server);
         verdict(t.status == 1 && t.winner == 1, "timeout: white sat on the move too long and loses on time (" + t.reason + ")");
         PhoneGames.forget(t.id);
      } finally {
         PhoneGames.clock = realClock;
         for (PhoneGames.Session x : new PhoneGames.Session[]{s, d, r, n}) {
            if (x != null) {
               PhoneGames.forget(x.id);
            }
         }
      }
   }

   // ============================================================================================ messages

   static void messages(MinecraftServer server) throws Exception {
      UUID who = UUID.nameUUIDFromBytes("fhqa-phone-texter".getBytes());
      java.util.function.LongSupplier realClock = PhoneMessages.clock;
      long[] now = {1_000_000L};
      PhoneMessages.clock = () -> now[0];
      try {
         PhoneMessages.resetRate(who);
         boolean first = PhoneMessages.allow(who);
         boolean burst = PhoneMessages.allow(who);
         now[0] += PhoneMessages.GAP_MS + 10L;
         boolean later = PhoneMessages.allow(who);
         verdict(first && !burst && later, "texts: a second text within " + PhoneMessages.GAP_MS + " ms is refused, after the gap it goes");
         int sent = 2;
         for (int i = 0; i < 20; i++) {
            now[0] += PhoneMessages.GAP_MS + 10L;
            if (PhoneMessages.allow(who)) {
               sent++;
            }
         }
         verdict(sent == PhoneMessages.PER_MINUTE, "texts: at most " + PhoneMessages.PER_MINUTE + " a minute (" + sent + " went)");
         now[0] += 61_000L;
         verdict(PhoneMessages.allow(who), "texts: a minute later the hunter can text again");
      } finally {
         PhoneMessages.clock = realClock;
         PhoneMessages.resetRate(who);
      }
      ServerPlayer fp = hunter(server, true);
      PhoneMessages.resetRate(fp.getUUID());
      var store = com.formaworks.frontierhunts.phone.PhoneStore.get(server);
      int before = store.hunter(fp.getUUID(), fp.getScoreboardName()).messages.size();
      PhoneMessages.send(fp, fp.getScoreboardName() + "\nhello me");
      PhoneMessages.send(fp, "Nobody_QA_404\nhello?");
      PhoneMessages.send(fp, "no newline at all");
      int after = store.hunter(fp.getUUID(), fp.getScoreboardName()).messages.size();
      verdict(after == before, "texts to yourself, to a hunter who never used a phone here, or malformed, are dropped (" + before + " -> " + after + ")");
      fp.getInventory().clearContent();
   }

   // ============================================================================================ [1.4.0] photos in texts

   static byte[] jpeg(int w, int h) throws Exception {
      java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
      java.util.Random r = new java.util.Random(7);
      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            img.setRGB(x, y, (x * 255 / w) << 16 | (y * 255 / h) << 8 | r.nextInt(64));
         }
      }
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      javax.imageio.ImageIO.write(img, "jpg", out);
      return out.toByteArray();
   }

   static void upload(ServerPlayer fp, long id, byte[] data, int parts) {
      int size = (data.length + parts - 1) / parts;
      for (int i = 0; i < parts; i++) {
         byte[] slice = java.util.Arrays.copyOfRange(data, i * size, Math.min(data.length, (i + 1) * size));
         PhonePhotos.upload(fp, new PhoneNet.PhotoUp(id, i, parts, slice));
      }
   }

   static void photos(MinecraftServer server) throws Exception {
      ServerPlayer fp = hunter(server, true);
      PhonePhotos.reset(fp.getUUID());
      PhoneMessages.resetRate(fp.getUUID());
      int bars = PhoneServer.signal(fp);
      if (bars <= 0) {
         FrontierQa.say("phone SKIP photos: no signal at the test spot");
         return;
      }
      var store = com.formaworks.frontierhunts.phone.PhoneStore.get(server);
      UUID friendId = UUID.nameUUIDFromBytes("fhqa-photo-friend".getBytes());
      store.hunter(friendId, "QA_Friend");
      java.util.function.LongSupplier realClock = PhonePhotos.clock;
      long[] now = {5_000_000L};
      PhonePhotos.clock = () -> now[0];
      try {
         byte[] good = jpeg(320, 180);
         verdict(PhonePhotos.looksLikeJpeg(good) && good.length < PhonePhotos.PART * PhonePhotos.MAX_PARTS,
            "a 320x180 test JPEG is " + good.length + " bytes and passes the JPEG check");
         long id = 0x1234_5678_9abc_def1L;
         upload(fp, id, good, 2);
         verdict(PhonePhotos.exists(server, id), "a photo in 2 parts from a hunter with a phone and signal is stored");
         // a text that shows it, then who may fetch it
         int before = store.hunter(fp.getUUID(), fp.getScoreboardName()).messages.size();
         PhoneMessages.send(fp, "QA_Friend\n" + PhonePhotos.token(id) + " look :fire:");
         int after = store.hunter(fp.getUUID(), fp.getScoreboardName()).messages.size();
         verdict(after == before + 1, "the photo text is delivered (" + before + " -> " + after + ")");
         var friend = store.hunters.get(friendId);
         boolean theyHave = friend != null && friend.messages.stream().anyMatch(m -> m.text.contains(PhonePhotos.token(id)));
         verdict(theyHave, "the recipient has the photo text, emoji and all");
         verdict(PhonePhotos.photoIn("x " + PhonePhotos.token(id) + " y") == id, "the photo id reads back from a text");
         // a photo text naming a photo the server doesn't have is dropped
         PhoneMessages.resetRate(fp.getUUID());
         now[0] += 10_000L;
         before = store.hunter(fp.getUUID(), fp.getScoreboardName()).messages.size();
         PhoneMessages.send(fp, "QA_Friend\n" + PhonePhotos.token(0x0badL));
         after = store.hunter(fp.getUUID(), fp.getScoreboardName()).messages.size();
         verdict(after == before, "a text naming a photo the server doesn't have is dropped");
         // bad uploads
         now[0] += 10_000L;
         byte[] junk = new byte[2000];
         new java.util.Random(3).nextBytes(junk);
         upload(fp, 0x2222L, junk, 1);
         verdict(!PhonePhotos.exists(server, 0x2222L), "bytes that aren't a JPEG are refused");
         now[0] += 10_000L;
         byte[] huge = jpeg(1100, 60);
         upload(fp, 0x3333L, huge, 1);
         verdict(!PhonePhotos.exists(server, 0x3333L), "a JPEG wider than " + PhonePhotos.MAX_SIDE + " pixels is refused");
         now[0] += 10_000L;
         PhonePhotos.upload(fp, new PhoneNet.PhotoUp(0x4444L, 1, 2, java.util.Arrays.copyOfRange(good, 0, 100)));
         PhonePhotos.upload(fp, new PhoneNet.PhotoUp(0x4444L, 0, 2, java.util.Arrays.copyOfRange(good, 0, 100)));
         verdict(!PhonePhotos.exists(server, 0x4444L), "parts out of order are refused");
         now[0] += 10_000L;
         PhonePhotos.upload(fp, new PhoneNet.PhotoUp(0x5555L, 0, PhonePhotos.MAX_PARTS + 1, good));
         verdict(!PhonePhotos.exists(server, 0x5555L), "more than " + PhonePhotos.MAX_PARTS + " parts is refused");
         // the pace: one photo every few seconds
         now[0] += 10_000L;
         upload(fp, 0x6666L, good, 1);
         upload(fp, 0x7777L, good, 1);
         verdict(PhonePhotos.exists(server, 0x6666L) && !PhonePhotos.exists(server, 0x7777L), "a second photo within " + PhonePhotos.GAP_MS
            + " ms is refused");
         // without a phone nothing is stored
         now[0] += 10_000L;
         fp.getInventory().clearContent();
         upload(fp, 0x8888L, good, 1);
         verdict(!PhonePhotos.exists(server, 0x8888L), "without a charged phone a photo is refused");
         // poses go out without trouble
         fp.getInventory().add(new ItemStack(PhoneContent.PHONE.get()));
         PhoneServer.reset(fp);
         PhoneServer.handle(fp, PhoneNet.Ask.of(PhoneNet.OP_POSE, 3L, 0L));
         PhoneServer.handle(fp, PhoneNet.Ask.of(PhoneNet.OP_POSE, 999L, 0L));
         PhoneServer.handle(fp, PhoneNet.Ask.of(PhoneNet.OP_PHOTO_GET, id, 0L));
         PhoneServer.handle(fp, PhoneNet.Ask.of(PhoneNet.OP_PHOTO_GET, 0x0badL, 0L));
         verdict(true, "poses (valid and out of range) and photo fetches (shared and unknown) run without errors");
      } finally {
         PhonePhotos.clock = realClock;
         PhonePhotos.reset(fp.getUUID());
         PhoneMessages.resetRate(fp.getUUID());
         for (long x : new long[]{0x1234_5678_9abc_def1L, 0x6666L}) {
            try {
               java.nio.file.Files.deleteIfExists(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data")
                  .resolve("frontierhunts_phone_photos").resolve(PhonePhotos.hex(x) + ".jpg"));
            } catch (Exception ignored) {
            }
         }
         store.hunters.remove(friendId);
         fp.getInventory().clearContent();
      }
   }

   // ============================================================================================ [1.4.0] the Field Camera retired

   static void camera(MinecraftServer server) throws Exception {
      var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("frontierhunts:field_camera"));
      verdict(item != net.minecraft.world.item.Items.AIR, "the retired Field Camera is still registered (old inventories load)");
      boolean recipe = server.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("frontierhunts:field_camera")).isPresent();
      verdict(!recipe, "the Field Camera has no recipe any more");
      boolean benchHas = false;
      for (var h : server.getRecipeManager().getRecipes()) {
         if (h.value().getResultItem(server.registryAccess()).is(item)) {
            benchHas = true;
         }
      }
      verdict(!benchHas, "no recipe at any bench makes a Field Camera");
   }

   // ============================================================================================ retired base station

   static void station(MinecraftServer server) throws Exception {
      ServerPlayer fp = hunter(server, false);
      ServerLevel level = fp.serverLevel();
      Item old = BuiltInRegistries.ITEM.get(ResourceLocation.parse("frontierhunts:camera_base_station"));
      verdict(old != Items.AIR, "the Camera Base Station item is still registered (old worlds load)");
      boolean recipe = false;
      for (RecipeHolder<?> r : level.getRecipeManager().getRecipes()) {
         if (r.value().getResultItem(level.registryAccess()).is(old)) {
            recipe = true;
         }
      }
      verdict(!recipe, "no recipe makes a Camera Base Station");
      verdict(BenchTest.entry(level, Bench.FRONTIER, "frontierhunts:camera_base_station") == null, "the Frontier Workbench does not list it");
      CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
      boolean inTabs = false, phoneInTabs = false;
      for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
         for (ItemStack s : tab.getDisplayItems()) {
            inTabs |= s.is(old);
            phoneInTabs |= FieldPhoneItem.is(s);
         }
      }
      verdict(!inTabs && phoneInTabs, "creative: the base station is hidden, the Field Phone is listed (station " + inTabs + ", phone " + phoneInTabs + ")");
      BlockPos at = fp.blockPosition().east(2);
      level.setBlock(at, ExpeditionContent.CAMERA_HUB.get().defaultBlockState(), 3);
      try {
         verdict(!PhoneServer.mayUseCameras(fp), "no phone and no station: no cameras");
         PhoneServer.openedStation(fp, at);
         verdict(PhoneServer.atStation(fp) && PhoneServer.mayUseCameras(fp), "a placed base station still opens the hunter's cameras (without a phone)");
         level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
         verdict(!PhoneServer.atStation(fp), "once the base station is gone its cameras are closed");
         BlockPos far = fp.blockPosition().east(20);
         level.setBlock(far, ExpeditionContent.CAMERA_HUB.get().defaultBlockState(), 3);
         PhoneServer.openedStation(fp, far);
         verdict(!PhoneServer.atStation(fp), "a base station 20 blocks away can't be opened");
         level.setBlock(far, Blocks.AIR.defaultBlockState(), 3);
      } finally {
         level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
         PhoneServer.reset(fp);
      }
   }

   // ============================================================================================ recipe

   static void recipe(MinecraftServer server) throws Exception {
      ServerPlayer fp = hunter(server, false);
      ServerLevel level = fp.serverLevel();
      BenchCatalog.Entry e = BenchTest.entry(level, Bench.FRONTIER, "frontierhunts:field_phone");
      verdict(e != null && e.tab() == BenchTab.HUNTING, "the Field Phone is on the Frontier Workbench's Hunting gear tab" + (e == null ? " (missing)"
         : " (" + e.tab() + ")"));
      if (e == null) {
         return;
      }
      BlockPos at = BenchTest.place(fp, Bench.FRONTIER.block());
      BenchTest.give(fp, e, 1);
      Item phone = PhoneContent.PHONE.get();
      BenchMenu menu = BenchTest.open(fp, Bench.FRONTIER, at);
      boolean clicked = menu.clickMenuButton(fp, e.index);
      int made = BenchTest.count(fp, phone);
      ItemStack got = ItemStack.EMPTY;
      for (ItemStack s : fp.getInventory().items) {
         if (s.is(phone)) {
            got = s;
         }
      }
      verdict(clicked && made == 1 && BenchTest.materials(fp, e) == 0, "the bench makes a Field Phone from its materials (+" + made + ")");
      verdict(!got.isEmpty() && FieldPhoneItem.percent(got) == 100, "a new phone comes fully charged (" + (got.isEmpty() ? "-" : FieldPhoneItem.percent(got)) + "%)");
      fp.containerMenu = fp.inventoryMenu;
      BenchTest.clear(fp, at);
      fp.getInventory().clearContent();
   }
}
