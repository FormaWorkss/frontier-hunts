package com.formaworks.frontierhunts.phone.client;

import com.formaworks.frontierhunts.client.TrailcamBridge;
import com.formaworks.frontierhunts.client.trailcam.Photo;
import com.formaworks.frontierhunts.client.trailcam.TrailcamClient;
import com.formaworks.frontierhunts.expedition.HuntingCalendar;
import com.formaworks.frontierhunts.firsthunt.FirstHuntNetwork;
import com.formaworks.frontierhunts.guide.client.FirstHuntClient;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Rut;
import com.formaworks.frontierhunts.phone.FieldPhoneItem;
import com.formaworks.frontierhunts.phone.PhoneBattery;
import com.formaworks.frontierhunts.phone.PhoneNet;
import com.formaworks.frontierhunts.phone.PhoneSignal;
import com.formaworks.frontierhunts.phone.client.ui.Canvas;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.Ui;
import com.formaworks.frontierhunts.season.SeasonClock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * [phone] Fills the phone's {@link PhoneModel} on the client: the device (battery, charging, signal, torch), the clock
 * and calendar, the hunter's place, the trail camera library, and every record the server pushes
 * ({@link PhoneNet.Push}). Records from the server are read defensively (missing fields fall back, lists are capped).
 * Also turns news into notifications: new trail camera photos, contracts ready to hand in, weather warnings, invites
 * and texts.
 */
public final class PhoneFeed {
   static final PhoneModel MODEL = new PhoneModel();
   private static final Map<Long, PhotoView> VIEWS = new HashMap<>();
   private static final Map<Long, Integer> FRAMES_SEEN = new HashMap<>();
   private static final Set<String> ALERTS_SEEN = new HashSet<>();
   private static final Set<Long> SESSIONS_SEEN = new HashSet<>();
   private static String contractSeen = "";
   private static int assignSeen = -1;
   private static boolean camsKnown;
   private static int camsVersion = -1;
   private static long galleryPos;
   /** cameras as the server last listed them */
   private static final List<CompoundTag> CAMS = new ArrayList<>();

   private PhoneFeed() {
   }

   static void reset() {
      PhoneModel m = MODEL;
      m.weather.loaded = false;
      m.weather.days.clear();
      m.weather.alerts.clear();
      m.weather.huntReasons.clear();
      m.cams.loaded = false;
      m.cams.list.clear();
      m.cams.gallery = null;
      m.cams.open = 0L;
      m.map.places.clear();
      m.map.target = "";
      m.contracts.loaded = false;
      m.contracts.board.clear();
      m.contracts.assignments.clear();
      m.wallet.loaded = false;
      m.wallet.tags.clear();
      m.wallet.stamps.clear();
      m.wallet.openNow.clear();
      m.wallet.trophies.clear();
      m.games.invites.clear();
      m.games.online.clear();
      m.games.leaderboard.clear();
      m.messages.loaded = false;
      m.messages.texts.clear();
      m.messages.contacts.clear();
      m.messages.unread = 0;
      m.notices.clear();
      m.players.clear();
      VIEWS.clear();
      FRAMES_SEEN.clear();
      ALERTS_SEEN.clear();
      SESSIONS_SEEN.clear();
      CAMS.clear();
      contractSeen = "";
      assignSeen = -1;
      camsKnown = false;
      camsVersion = -1;
      galleryPos = 0L;
      m.version++;
   }

   // ------------------------------------------------------------------------------------------------ helpers

   private static String str(CompoundTag t, String k, int max) {
      String s = t.getString(k);
      return s.length() > max ? s.substring(0, max) : s;
   }

   private static void notice(String app, String title, String body) {
      PhoneModel m = MODEL;
      m.notices.add(new PhoneModel.Notice(app, title, body, net.minecraft.Util.getMillis()));
      while (m.notices.size() > 30) {
         m.notices.remove(0);
      }
      PhoneClient.noticeArrived(app, title, body);
   }

   // ------------------------------------------------------------------------------------------------ server records

   static void receive(PhoneNet.Push p) {
      CompoundTag t = p.tag();
      try {
         switch (p.kind()) {
            case PhoneNet.K_WEATHER -> weather(t);
            case PhoneNet.K_PLACES -> places(t);
            case PhoneNet.K_WALLET -> wallet(t);
            case PhoneNet.K_CONTRACTS -> contracts(t);
            case PhoneNet.K_CAMS -> cams(t);
            case PhoneNet.K_GAMES -> games(t);
            case PhoneNet.K_NOTICE -> notice(str(t, "app", 24), str(t, "title", 60), str(t, "body", 200));
            case PhoneNet.K_MESSAGES -> messages(t);
            case PhoneNet.K_FLUSH -> flush(t);
            case PhoneNet.K_OPEN -> PhoneClient.openFromServer(str(t, "app", 24), t.contains("station") ? BlockPos.of(t.getLong("station")) : null);
            case PhoneNet.K_TOAST -> PhoneClient.toast(str(t, "text", 160));
            case PhoneNet.K_PHOTO -> PhonePhotoCache.receive(t);
            case PhoneNet.K_POSE -> SelfiePose.remote(t.getInt("e"), t.getInt("p"));
            default -> {
            }
         }
      } catch (RuntimeException e) {
         // a malformed record is dropped; the phone keeps what it had
      }
      MODEL.version++;
   }

   private static void weather(CompoundTag t) {
      PhoneModel.Weather w = MODEL.weather;
      w.loaded = true;
      w.icon = str(t, "icon", 24);
      w.condition = str(t, "condition", 40);
      w.tempC = t.getFloat("temp");
      w.feelsC = t.getFloat("feels");
      w.windKmh = Math.max(0.0F, t.getFloat("wind"));
      w.windFrom = t.getFloat("from");
      w.visibility = t.getFloat("vis");
      w.severe = str(t, "severe", 40);
      w.severity = t.getFloat("severity");
      w.sunrise = t.getInt("sunrise");
      w.sunset = t.getInt("sunset");
      w.raining = t.getBoolean("raining");
      w.rainNext = t.getBoolean("rainNext");
      w.rainChange = t.getInt("rainChange");
      w.updated = t.getLong("updated");
      w.hourStart = Math.floorMod(t.getInt("hourStart"), 24);
      ListTag temps = t.getList("hourTemps", Tag.TAG_FLOAT);
      ListTag icons = t.getList("hourIcons", Tag.TAG_STRING);
      int[] rain = t.getIntArray("hourRain");
      for (int i = 0; i < 24; i++) {
         w.hourTemp[i] = i < temps.size() ? temps.getFloat(i) : w.tempC;
         w.hourIcon[i] = i < icons.size() ? icons.getString(i) : "clear";
         w.hourRain[i] = i < rain.length ? Math.max(0, Math.min(100, rain[i])) : 0;
      }
      w.days.clear();
      ListTag days = t.getList("days", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(7, days.size()); i++) {
         CompoundTag d = days.getCompound(i);
         w.days.add(new PhoneModel.Day(str(d, "name", 12), str(d, "icon", 24), d.getFloat("hi"), d.getFloat("lo"), Math.max(0, Math.min(100, d.getInt("rain"))),
            str(d, "note", 40)));
      }
      w.alerts.clear();
      ListTag alerts = t.getList("alerts", Tag.TAG_COMPOUND);
      Set<String> now = new HashSet<>();
      for (int i = 0; i < Math.min(4, alerts.size()); i++) {
         CompoundTag a = alerts.getCompound(i);
         PhoneModel.Alert al = new PhoneModel.Alert(str(a, "kind", 24), str(a, "title", 60), str(a, "body", 200), a.getInt("eta"), a.getFloat("strength"));
         w.alerts.add(al);
         now.add(al.kind());
         if (!ALERTS_SEEN.contains(al.kind()) && MODEL.settings.notifyWeather) {
            notice("weather", al.title(), al.etaMinutes() <= 0 ? al.body()
               : "Arriving about " + Ui.clockFull(MODEL, Math.floorMod(Ui.minute(MODEL.dayTime) + al.etaMinutes(), 1440)) + " · " + al.body());
         }
      }
      ALERTS_SEEN.retainAll(now);
      ALERTS_SEEN.addAll(now);
      w.huntScore = Math.max(0, Math.min(100, t.getInt("huntScore")));
      w.huntLine = str(t, "huntLine", 80);
      w.huntReasons.clear();
      ListTag rs = t.getList("huntReasons", Tag.TAG_STRING);
      for (int i = 0; i < Math.min(8, rs.size()); i++) {
         w.huntReasons.add(rs.getString(i));
      }
   }

   private static void places(CompoundTag t) {
      PhoneModel.MapData md = MODEL.map;
      md.places.clear();
      ListTag list = t.getList("places", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(96, list.size()); i++) {
         CompoundTag c = list.getCompound(i);
         md.places.add(new PhoneModel.Place(str(c, "id", 40), str(c, "kind", 12), str(c, "name", 40), c.getInt("x"), c.getInt("y"), c.getInt("z"),
            c.getBoolean("rm"), str(c, "note", 60)));
      }
      firstHunt();
   }

   /** The beginner area and the first-hunt camp spot, from the first-hunt state this client already has. */
   static void firstHunt() {
      PhoneModel.MapData md = MODEL.map;
      md.places.removeIf(p -> p.id().equals("area:first") || p.id().equals("spot:first"));
      FirstHuntNetwork.State st = FirstHuntClient.current();
      md.area = false;
      if (st == null || !st.has(FirstHuntNetwork.F_AREA) || !st.has(FirstHuntNetwork.F_AREA_HERE)) {
         return;
      }
      int y = (int)MODEL.y;
      md.area = true;
      md.areaX = st.areaX();
      md.areaZ = st.areaZ();
      md.areaR = Math.max(8, st.areaR());
      md.areaTitle = "Beginner area";
      md.areaNote = st.has(FirstHuntNetwork.F_AREA_QUIET) ? "It has gone quiet here: the deer moved on. The first hunt is finding a new area."
         : "Easy country with deer feeding and bedding nearby. Walk in slowly with the wind in your face and read the sign.";
      md.places.add(0, new PhoneModel.Place("area:first", "area", "Beginner area", st.areaX(), y, st.areaZ(), false, "First hunt"));
      if (st.has(FirstHuntNetwork.F_CAMP)) {
         md.places.add(1, new PhoneModel.Place("spot:first", "spot", "First-hunt camp", st.campX(), y, st.campZ(), false, "A good place to set up camp"));
      }
   }

   private static void wallet(CompoundTag t) {
      PhoneModel.Wallet w = MODEL.wallet;
      w.loaded = true;
      w.rules = str(t, "rules", 20);
      w.period = str(t, "period", 40);
      w.monthName = str(t, "month", 20);
      w.daysLeft = t.getInt("daysLeft");
      w.licence = t.getBoolean("licence");
      w.licenceHeld = t.getBoolean("licenceHeld");
      w.suspended = t.getBoolean("suspended");
      w.tokens = t.getInt("tokens");
      w.tags.clear();
      ListTag tags = t.getList("tags", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(16, tags.size()); i++) {
         CompoundTag c = tags.getCompound(i);
         w.tags.add(new PhoneModel.Tag(str(c, "name", 30), str(c, "item", 60), c.getInt("color"), c.getInt("held"), c.getInt("bought"), c.getInt("bag"),
            str(c, "months", 60), c.getBoolean("open")));
      }
      w.stamps.clear();
      ListTag stamps = t.getList("stamps", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(8, stamps.size()); i++) {
         CompoundTag c = stamps.getCompound(i);
         w.stamps.add(new PhoneModel.Stamp(str(c, "name", 30), str(c, "item", 60), c.getBoolean("held"), str(c, "today", 40), str(c, "months", 60),
            c.getBoolean("open")));
      }
      w.openNow.clear();
      ListTag open = t.getList("open", Tag.TAG_STRING);
      for (int i = 0; i < Math.min(40, open.size()); i++) {
         w.openNow.add(open.getString(i));
      }
      w.violations = t.getInt("violations");
      w.strikes = t.getInt("strikes");
      w.fines = t.getInt("fines");
      w.filled = t.getInt("filled");
      w.record = t.getInt("record");
      w.trophies.clear();
      ListTag tr = t.getList("trophies", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(24, tr.size()); i++) {
         CompoundTag c = tr.getCompound(i);
         w.trophies.add(new PhoneModel.Trophy(str(c, "species", 32), str(c, "title", 60), str(c, "detail", 40), str(c, "when", 48), str(c, "where", 80),
            c.getBoolean("legal")));
      }
      w.rank = str(t, "rank", 30);
      w.nextRank = str(t, "nextRank", 30);
      w.xp = t.getLong("xp");
      w.rankFrom = t.getLong("rankFrom");
      w.rankTo = t.getLong("rankTo");
      w.harvests = t.getInt("harvests");
      w.recoveries = t.getInt("recoveries");
      w.clean = t.getInt("clean");
      w.longest = t.getInt("longest");
      w.bestScore = t.getInt("bestScore");
      w.species = t.getInt("species");
      w.photos = t.getInt("photos");
      w.achDone = Math.max(0, t.getInt("achDone"));
      w.achTotal = Math.max(0, t.getInt("achTotal"));
      w.goals.clear();
      ListTag goals = t.getList("goals", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(8, goals.size()); i++) {
         CompoundTag c = goals.getCompound(i);
         String key = str(c, "key", 120);
         // the server sends lang keys: titles and hints are read in the player's language here
         String title = net.minecraft.client.resources.language.I18n.exists(key) ? net.minecraft.client.resources.language.I18n.get(key) : str(c, "id", 40);
         String hint = net.minecraft.client.resources.language.I18n.exists(key + ".hint") ? net.minecraft.client.resources.language.I18n.get(key + ".hint") : "";
         w.goals.add(new PhoneModel.Goal(title.length() > 60 ? title.substring(0, 60) : title, hint.length() > 160 ? hint.substring(0, 160) : hint,
            Math.max(0, c.getInt("value")), Math.max(1, c.getInt("target")), Math.max(0, c.getInt("xp")), Math.max(0, Math.min(3, c.getInt("unit")))));
      }
      w.version++;
   }

   private static void contracts(CompoundTag t) {
      PhoneModel.Contracts c = MODEL.contracts;
      boolean first = !c.loaded;
      c.loaded = true;
      c.tokens = t.getInt("tokens");
      MODEL.wallet.tokens = c.tokens;
      c.storyDone = t.getBoolean("storyDone");
      c.storyId = str(t, "storyId", 40);
      c.storyTitle = str(t, "storyTitle", 60);
      c.storyText = str(t, "storyText", 400);
      c.storyCount = t.getInt("storyCount");
      c.storyAmount = t.getInt("storyAmount");
      c.storyTokens = t.getInt("storyTokens");
      c.storyChapter = t.getInt("storyChapter");
      c.contract = t.getInt("contract");
      c.contractStatus = str(t, "contractStatus", 12);
      c.contractTitle = str(t, "contractTitle", 60);
      c.contractText = str(t, "contractText", 400);
      c.contractCount = t.getInt("contractCount");
      c.contractAmount = t.getInt("contractAmount");
      c.contractTokens = t.getInt("contractTokens");
      c.contractSeconds = t.getLong("contractSeconds");
      c.contractSerial = str(t, "contractSerial", 24);
      c.board.clear();
      ListTag board = t.getList("board", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(24, board.size()); i++) {
         CompoundTag o = board.getCompound(i);
         c.board.add(new PhoneModel.Offer(o.getInt("index"), str(o, "title", 60), str(o, "text", 400), o.getInt("tokens"), o.getInt("amount"), str(o, "species", 30)));
      }
      c.boardNote = str(t, "boardNote", 200);
      c.eligible = t.getBoolean("eligible");
      c.assignState = Math.max(0, Math.min(3, t.getInt("assignState")));
      c.assignId = str(t, "assignId", 128);
      c.assignTitle = str(t, "assignTitle", 80);
      c.assignText = str(t, "assignText", 400);
      c.assignCount = t.getInt("assignCount");
      c.assignTarget = t.getInt("assignTarget");
      c.assignTokens = t.getInt("assignTokens");
      c.assignXp = t.getInt("assignXp");
      c.assignTicks = t.getLong("assignTicks");
      c.assignments.clear();
      ListTag offers = t.getList("offers", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(16, offers.size()); i++) {
         CompoundTag o = offers.getCompound(i);
         c.assignments.add(new PhoneModel.Assignment(str(o, "id", 128), str(o, "title", 80), str(o, "text", 400), o.getInt("tokens"), o.getInt("xp"),
            o.getInt("target"), o.getInt("cooldown"), str(o, "kind", 16), o.getInt("duration")));
      }
      // news: a contract or an assignment became ready to hand in
      String key = c.contractSerial + ":" + c.contractStatus;
      if (!first && MODEL.settings.notifyContracts) {
         if ("ready".equals(c.contractStatus) && !key.equals(contractSeen)) {
            notice("contracts", "Contract complete", c.contractTitle + " · hand it in for " + c.contractTokens + " tokens");
         }
         if (c.assignState == 2 && assignSeen != 2) {
            notice("contracts", "Assignment complete", c.assignTitle + " · collect " + c.assignTokens + " tokens");
         }
      }
      contractSeen = key;
      assignSeen = c.assignState;
   }

   private static void cams(CompoundTag t) {
      PhoneModel.Cams cams = MODEL.cams;
      cams.loaded = true;
      cams.seasonLine = str(t, "season", 200);
      CAMS.clear();
      ListTag list = t.getList("cams", Tag.TAG_COMPOUND);
      List<String> fresh = new ArrayList<>();
      for (int i = 0; i < Math.min(64, list.size()); i++) {
         CompoundTag c = list.getCompound(i);
         CAMS.add(c);
         long pos = c.getLong("pos");
         int frames = c.getInt("frames");
         Integer seen = FRAMES_SEEN.put(pos, frames);
         if (camsKnown && seen != null && frames > seen && fresh.size() < 3) {
            String name = str(c, "name", 40);
            BlockPos bp = BlockPos.of(pos);
            fresh.add((name.isEmpty() ? "Camera " + bp.getX() + ", " + bp.getZ() : name) + ": " + (frames - seen) + (frames - seen == 1 ? " new photo" : " new photos"));
         }
      }
      if (!fresh.isEmpty() && MODEL.settings.notifyCams) {
         notice("cams", "Trail camera", String.join(" · ", fresh));
      }
      camsKnown = true;
      camsVersion = -1;
      refreshCams();
   }

   private static void games(CompoundTag t) {
      PhoneModel.Games g = MODEL.games;
      long now = net.minecraft.Util.getMillis();
      g.invites.clear();
      ListTag inv = t.getList("invites", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(8, inv.size()); i++) {
         CompoundTag c = inv.getCompound(i);
         g.invites.add(new PhoneModel.Invite(c.getLong("id"), str(c, "from", 16), Math.max(0, Math.min(2, c.getInt("game"))), now + c.getLong("left")));
      }
      List<PhoneModel.Online> old = new ArrayList<>(g.online);
      g.online.clear();
      ListTag ss = t.getList("sessions", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(8, ss.size()); i++) {
         CompoundTag c = ss.getCompound(i);
         long id = c.getLong("id");
         PhoneModel.Online o = null;
         for (PhoneModel.Online x : old) {
            if (x.id == id) {
               o = x;
            }
         }
         if (o == null) {
            o = new PhoneModel.Online();
            o.id = id;
         }
         o.game = Math.max(0, Math.min(2, c.getInt("game")));
         o.me = c.getInt("me") == 1 ? 1 : 0;
         o.names[0] = str(c, "n0", 16);
         o.names[1] = str(c, "n1", 16);
         o.seq = c.getInt("seq");
         o.status = c.getInt("status");
         o.winner = c.getInt("winner");
         o.reason = str(c, "reason", 60);
         o.opponentAway = c.getBoolean("away");
         o.awaySeconds = c.getInt("awaySeconds");
         o.rematchMine = c.getBoolean("rm");
         o.rematchTheirs = c.getBoolean("rt");
         o.opponentGone = c.getBoolean("gone");
         o.next = c.getLong("next");
         int[] st = c.getIntArray("state");
         o.state = st.length > 2000 ? new int[0] : st;
         o.seed = c.getLong("seed");
         o.startIn = c.getInt("startIn");
         o.opponentScore = c.getInt("opp");
         int[] fin = c.getIntArray("final");
         o.finalScores = fin.length == 2 ? fin : new int[]{-1, -1};
         g.online.add(o);
         if (SESSIONS_SEEN.add(id) && o.status == 0) {
            PhoneClient.onlineStarted(o);
         }
      }
      int[] stats = t.getIntArray("stats");
      System.arraycopy(stats, 0, g.stats, 0, Math.min(stats.length, g.stats.length));
      g.flushBest = t.getInt("flushBest");
      g.diceBest = t.getInt("diceBest");
      g.puzzlesSolved = t.getLong("puzzles");
      g.leaderboard.clear();
      ListTag board = t.getList("board", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(10, board.size()); i++) {
         CompoundTag c = board.getCompound(i);
         g.leaderboard.add(new PhoneModel.Score(str(c, "name", 16), c.getInt("score"), c.getBoolean("me"), ""));
      }
      players(t.getList("online", Tag.TAG_STRING));
      g.version++;
   }

   private static void players(ListTag online) {
      if (online.isEmpty()) {
         return;
      }
      MODEL.players.clear();
      for (int i = 0; i < Math.min(64, online.size()); i++) {
         MODEL.players.add(online.getString(i));
      }
   }

   private static void messages(CompoundTag t) {
      PhoneModel.Messages ms = MODEL.messages;
      ms.loaded = true;
      ms.texts.clear();
      int unread = 0;
      ListTag list = t.getList("messages", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(400, list.size()); i++) {
         CompoundTag c = list.getCompound(i);
         PhoneModel.Text x = new PhoneModel.Text(str(c, "peer", 16), c.getBoolean("mine"), str(c, "text", 200), c.getLong("at"), c.getBoolean("read"));
         ms.texts.add(x);
         unread += !x.mine() && !x.read() ? 1 : 0;
      }
      ms.unread = unread;
      ms.contacts.clear();
      ListTag contacts = t.getList("contacts", Tag.TAG_STRING);
      for (int i = 0; i < Math.min(64, contacts.size()); i++) {
         ms.contacts.add(contacts.getString(i));
      }
      players(t.getList("online", Tag.TAG_STRING));
   }

   private static void flush(CompoundTag t) {
      PhoneModel.Games g = MODEL.games;
      if (t.contains("token")) {
         g.flushToken = t.getLong("token");
         g.flushSeed = t.getLong("seed");
      }
      if (t.contains("result")) {
         g.flushResult = str(t, "result", 120);
      }
      if (t.contains("best")) {
         g.flushBest = t.getInt("best");
         g.flushRank = t.getInt("rank");
      }
      g.version++;
   }

   // ------------------------------------------------------------------------------------------------ every tick

   /** The device, the clock and the place. Cheap; called every tick while the phone is open, slower otherwise. */
   static void device(Minecraft mc, boolean full) {
      LocalPlayer p = mc.player;
      if (p == null || mc.level == null) {
         return;
      }
      PhoneModel m = MODEL;
      ItemStack phone = carried(p);
      m.hasPhone = !phone.isEmpty();
      m.battery = m.hasPhone ? FieldPhoneItem.percent(phone) : 0;
      m.flashlight = m.hasPhone && FieldPhoneItem.light(phone);
      m.held = m.hasPhone && (p.getMainHandItem() == phone || p.getOffhandItem() == phone);
      m.dayTime = mc.level.getDayTime();
      long cal = SeasonClock.calendarTicks(mc.level);
      m.gameTime = cal;
      m.moonPhase = mc.level.getMoonPhase();
      m.x = p.getX();
      m.y = p.getY();
      m.z = p.getZ();
      m.yaw = p.getYRot();
      if (!full) {
         return;
      }
      m.charging = m.hasPhone && m.battery < 100 && PhoneBattery.charger(p) > 0;
      m.chargeNote = m.charging ? "Charging" : "";
      m.signal = PhoneSignal.bars(mc.level, p.getX(), p.getY(), p.getZ());
      m.dimension = mc.level.dimension().location().toString();
      int dpm = SeasonClock.daysPerMonth(mc.level);
      HuntingCalendar.Date d = HuntingCalendar.date(cal, dpm);
      m.month = d.month();
      m.day = d.day();
      m.daysPerMonth = dpm;
      m.year = (int)(d.serial() / 12L + 1L);
      m.season = SeasonClock.season(mc.level).title();
      Rut.Phase phase = Rut.phase(GameSpecies.WHITETAIL, d);
      m.rutActive = phase.active();
      m.rutTitle = phase.active() ? phase.title : "Off season";
      m.rutNote = phase.active() ? phase.note : "Deer keep to their feeding and bedding routine.";
      m.playerName = p.getGameProfile().getName();
      var key = mc.level.getBiome(p.blockPosition()).unwrapKey();
      m.biome = key.map(k -> I18n.get("biome." + k.location().getNamespace() + "." + k.location().getPath())).orElse("");
      if (mc.getConnection() != null && m.players.size() <= 1) {
         m.players.clear();
         for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (m.players.size() < 64) {
               m.players.add(info.getProfile().getName());
            }
         }
      }
   }

   /** The phone the player carries (a charged one first), or empty. */
   static ItemStack carried(LocalPlayer p) {
      Inventory inv = p.getInventory();
      ItemStack best = ItemStack.EMPTY;
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         if (FieldPhoneItem.is(s)) {
            if (FieldPhoneItem.charge(s) > 0) {
               return s;
            }
            best = s;
         }
      }
      return best;
   }

   // ------------------------------------------------------------------------------------------------ trail cameras

   /** The camera list and the open gallery, from the server list and the photo library (when either changed). */
   static void refreshCams() {
      PhoneModel m = MODEL;
      int v = TrailcamClient.version;
      if (v == camsVersion) {
         // distances still move with the hunter
         rebuildCamList(m);
         return;
      }
      camsVersion = v;
      rebuildCamList(m);
      PhoneModel.Gallery g = m.cams.gallery;
      if (g != null) {
         BlockPos pos = BlockPos.of(g.pos);
         g.photos.clear();
         for (Photo p : TrailcamClient.roll(pos)) {
            g.photos.add(view(p));
         }
         g.hasRoll = TrailcamClient.hasRoll(pos);
      }
      String up = TrailcamClient.uplinkStatus();
      m.cams.uplink = up;
   }

   private static void rebuildCamList(PhoneModel m) {
      m.cams.list.clear();
      for (CompoundTag c : CAMS) {
         BlockPos pos = BlockPos.of(c.getLong("pos"));
         double dx = pos.getX() + 0.5 - m.x, dz = pos.getZ() + 0.5 - m.z;
         Photo latest = TrailcamClient.latest(pos);
         String name = str(c, "name", 40);
         m.cams.list.add(new PhoneModel.Cam(pos.asLong(), name.isEmpty() ? "Camera " + pos.getX() + ", " + pos.getZ() : name, c.getInt("pct"),
            c.getInt("frames"), c.getBoolean("live"), c.getBoolean("loaded"), c.getBoolean("best"), c.getInt("score"), str(c, "over", 24),
            Math.sqrt(dx * dx + dz * dz), Ui.point(Ui.bearing(m.x, m.z, pos.getX() + 0.5, pos.getZ() + 0.5)), latest == null ? null : view(latest)));
         if (m.cams.gallery != null && m.cams.gallery.pos == pos.asLong()) {
            PhoneModel.Gallery g = m.cams.gallery;
            g.label = m.cams.list.get(m.cams.list.size() - 1).name();
            g.percent = c.getInt("pct");
            g.live = c.getBoolean("live");
            g.keepsLoaded = c.getBoolean("loaded");
         }
      }
   }

   /** Opens a camera's roll in the model (the app then shows it). */
   static void openGallery(long pos) {
      PhoneModel m = MODEL;
      PhoneModel.Gallery g = new PhoneModel.Gallery();
      g.pos = pos;
      galleryPos = pos;
      m.cams.gallery = g;
      m.cams.open = pos;
      camsVersion = -1;
      refreshCams();
   }

   static long galleryPos() {
      return MODEL.cams.gallery == null ? 0L : MODEL.cams.gallery.pos;
   }

   private static PhotoView view(Photo p) {
      PhotoView v = VIEWS.get(p.id);
      if (v == null || v.photo != p) {
         if (VIEWS.size() > 600) {
            VIEWS.clear();
         }
         v = new PhotoView(p);
         VIEWS.put(p.id, v);
      }
      return v;
   }

   /** A trail camera photo as the phone sees it. */
   static final class PhotoView implements PhoneModel.PhotoRef {
      final Photo photo;
      private final Canvas.Custom fallback;

      PhotoView(Photo photo) {
         this.photo = photo;
         this.fallback = (g, x, y, w, h) -> {
            if (g instanceof GuiGraphics gg && photo.deerSubject()) {
               TrailcamBridge.composite(gg, Math.round(x), Math.round(y), Math.round(w), Math.round(h), photo.legacyFrame(), photo.id,
                  (net.minecraft.Util.getMillis() % 100000L) / 1000.0F);
            } else if (g instanceof GuiGraphics gg) {
               TrailcamBridge.empty(gg, Math.round(x), Math.round(y), Math.round(w), Math.round(h), (byte)(photo.infrared() ? 2 : 0), photo.id, "No image",
                  (net.minecraft.Util.getMillis() % 100000L) / 1000.0F);
            }
         };
      }

      @Override
      public long id() {
         return this.photo.id;
      }

      @Override
      public int state() {
         return this.photo.state.ordinal();
      }

      @Override
      public Object thumb() {
         return this.photo.thumbLoc;
      }

      @Override
      public Object full() {
         return TrailcamClient.full(this.photo);
      }

      @Override
      public int fullW() {
         return this.photo.full != null && this.photo.full.getPixels() != null ? this.photo.full.getPixels().getWidth() : 1280;
      }

      @Override
      public int fullH() {
         return this.photo.full != null && this.photo.full.getPixels() != null ? this.photo.full.getPixels().getHeight() : 720;
      }

      @Override
      public String title() {
         return this.photo.title();
      }

      @Override
      public String detail() {
         return this.photo.detail();
      }

      @Override
      public String when() {
         return this.photo.when();
      }

      @Override
      public boolean infrared() {
         return this.photo.infrared();
      }

      @Override
      public int frame() {
         return this.photo.frameNo();
      }

      @Override
      public String note() {
         return this.photo.note;
      }

      @Override
      public Canvas.Custom fallback() {
         return this.photo.state == Photo.State.FAILED ? this.fallback : null;
      }
   }
}
