import com.formaworks.frontierhunts.phone.client.ui.Canvas;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** [phone] Sample data for the offline mocks: a fall morning in the reserve. */
final class Sample {
   private Sample() {
   }

   /** day time for a clock time (Minecraft day time 0 = 06:00) */
   static long at(int h, int m) {
      int minute = h * 60 + m - 360;
      return Math.floorMod(minute, 1440) * 24000L / 1440L;
   }

   static String repo = ".";

   static BufferedImage img(String name) {
      try {
         return ImageIO.read(new File(repo + "/tools/phone/mock/photos/" + name + ".jpg"));
      } catch (Exception e) {
         return null;
      }
   }

   record Photo(long id, int state, Object thumb, Object full, String title, String detail, String when, boolean infrared, int frame, String note)
      implements PhoneModel.PhotoRef {
      @Override public int fullW() { return 640; }
      @Override public int fullH() { return 360; }
      @Override public Canvas.Custom fallback() { return null; }
   }

   static Photo photo(long id, String img, int state, String title, String detail, String when, boolean ir, int frame) {
      BufferedImage b = img(img);
      return new Photo(id, state, b, b, title, detail, when, ir, frame, "");
   }

   static void cams(PhoneModel m) {
      m.cams.loaded = true;
      m.cams.seasonLine = "October 14 · Whitetail: seeking · Elk: peak rut";
      m.cams.list.add(new PhoneModel.Cam(1L, "North Ridge", 84, 12, true, true, true, 142, "", 312, "NE",
         photo(11, "buck_day", 4, "Whitetail buck", "Mature · 10 pt · score 142 · 96 kg", "06:12 AM · Oct 14", false, 42)));
      m.cams.list.add(new PhoneModel.Cam(2L, "Creek Bait", 46, 5, true, false, false, 0, "", 610, "SW",
         photo(12, "bear_day", 2, "Black bear", "", "05:58 PM · Oct 13", false, 17)));
      m.cams.list.add(new PhoneModel.Cam(3L, "Cedar Scrape", 9, 23, false, false, true, 128, "", 1420, "W",
         photo(13, "buck_ir", 4, "Whitetail buck", "8 pt · score 128", "02:14 AM · Oct 14", true, 88)));
      PhoneModel.Gallery g = new PhoneModel.Gallery();
      g.pos = 1L;
      g.label = "North Ridge";
      g.percent = 84;
      g.live = true;
      g.keepsLoaded = true;
      g.hasRoll = true;
      g.photos.add(photo(11, "buck_day", 4, "Whitetail buck", "Mature · 10 pt · score 142 · 96 kg", "06:12 AM · Oct 14", false, 42));
      g.photos.add(photo(14, "doe_day", 4, "Whitetail doe", "Adult · 54 kg · with Whitetail fawn", "07:40 AM · Oct 13", false, 41));
      g.photos.add(photo(15, "buck_ir", 4, "Whitetail buck", "8 pt · score 128 · 88 kg", "02:14 AM · Oct 13", true, 40));
      g.photos.add(photo(16, "player_day", 4, "Austin", "Player", "11:03 AM · Oct 12", false, 39));
      g.photos.add(photo(17, "empty_ir", 2, "Coyote", "", "11:47 PM · Oct 11", true, 38));
      g.photos.add(photo(18, "doe_day", 1, "Whitetail doe", "", "08:20 AM · Oct 11", false, 37));
      m.cams.gallery = g;
   }

   static double noise(double x, double z, int seed) {
      double v = 0, amp = 1, f = 1.0 / 64.0, tot = 0;
      for (int o = 0; o < 5; o++) {
         v += amp * vnoise(x * f, z * f, seed + o * 31);
         tot += amp;
         amp *= 0.5;
         f *= 2.0;
      }
      return v / tot;
   }

   static double vnoise(double x, double z, int seed) {
      int x0 = (int)Math.floor(x), z0 = (int)Math.floor(z);
      double fx = x - x0, fz = z - z0;
      fx = fx * fx * (3 - 2 * fx);
      fz = fz * fz * (3 - 2 * fz);
      double a = hash(x0, z0, seed), b = hash(x0 + 1, z0, seed), c = hash(x0, z0 + 1, seed), d = hash(x0 + 1, z0 + 1, seed);
      return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
   }

   static double hash(int x, int z, int seed) {
      long h = x * 374761393L + z * 668265263L + seed * 2147483647L;
      h = (h ^ (h >>> 13)) * 1274126177L;
      return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
   }

   static BufferedImage topo(int cx, int cz) {
      int n = 256;
      int[] height = new int[n * n], color = new int[n * n], out = new int[n * n];
      byte[] kind = new byte[n * n], depth = new byte[n * n];
      for (int z = 0; z < n; z++) {
         for (int x = 0; x < n; x++) {
            int wx = cx - n / 2 + x, wz = cz - n / 2 + z;
            double e = noise(wx, wz, 7);
            double ridge = Math.pow(Math.abs(noise(wx * 0.7, wz * 0.7, 99) - 0.5) * 2, 1.5);
            int hgt = (int)(48 + e * 70 + ridge * 25);
            int i = z * n + x;
            boolean forest = noise(wx * 1.6, wz * 1.6, 3) > 0.5;
            if (hgt < 62) {
               kind[i] = 1;
               depth[i] = (byte)Math.min(20, 62 - hgt);
               height[i] = 62;
               color[i] = 0x4040FF;
            } else {
               height[i] = hgt;
               if (hgt > 112) {
                  kind[i] = 3;
                  color[i] = 0xFFFFFF;
               } else if (hgt > 98) {
                  color[i] = 0x707070;
               } else if (hgt < 64) {
                  color[i] = 0xF7E9A3;
               } else if (forest) {
                  kind[i] = 2;
                  color[i] = 0x2E6B1F;
               } else {
                  color[i] = 0x7FB238;
               }
            }
         }
      }
      com.formaworks.frontierhunts.phone.client.ui.TopoStyle.style(n, height, color, kind, depth, out, 0, n);
      BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
      img.setRGB(0, 0, n, n, out, 0, n);
      return img;
   }

   static void places(PhoneModel m) {
      PhoneModel.MapData md = m.map;
      md.centerX = 112;
      md.centerZ = -330;
      md.size = 256;
      md.texture = topo(md.centerX, md.centerZ);
      md.progress = 1.0F;
      md.area = true;
      md.areaX = 40;
      md.areaZ = -420;
      md.areaR = 48;
      md.areaTitle = "Beginner area: Aspen Flats";
      md.areaNote = "Deer feed along the creek bottom at dawn. Approach from the north with the wind in your face.";
      md.places.add(new PhoneModel.Place("camp:1", "camp", "Austin's Camp", 150, 76, -300, false, "Tier 2 camp"));
      md.places.add(new PhoneModel.Place("lodge:1", "lodge", "Lodge stores", 248, 71, -598, false, "Lakeside village"));
      md.places.add(new PhoneModel.Place("ranger:1", "ranger", "Ranger counter", 268, 72, -620, false, "Licences and tags"));
      md.places.add(new PhoneModel.Place("cam:1", "cam", "North Ridge", 170, 88, -360, false, "84% · 12 photos"));
      md.places.add(new PhoneModel.Place("cam:2", "cam", "Creek Bait", 60, 64, -280, false, "46% · 5 photos"));
      md.places.add(new PhoneModel.Place("pin:1", "pin6", "Big rub line", 95, 70, -372, true, ""));
      md.places.add(new PhoneModel.Place("pin:2", "pin3", "Glassing knob", 186, 101, -298, true, ""));
      md.places.add(new PhoneModel.Place("home:1", "home", "Your bed", -20, 70, -150, false, ""));
   }

   static void contracts(PhoneModel m) {
      PhoneModel.Contracts c = m.contracts;
      c.loaded = true;
      c.tokens = 245;
      c.storyId = "clean_shot";
      c.storyTitle = "A clean shot";
      c.storyText = "Drop a whitetail with a heart or lung shot, then recover it. Glass the deer with your binoculars and wait for a clean broadside first.";
      c.storyCount = 1;
      c.storyAmount = 1;
      c.storyTokens = 40;
      c.storyChapter = 2;
      c.contract = 1;
      c.contractStatus = "active";
      c.contractTitle = "Long shot";
      c.contractText = "Recover a whitetail shot cleanly from at least 150 metres - range it, rest the rifle, wait for broadside.";
      c.contractCount = 0;
      c.contractAmount = 1;
      c.contractTokens = 60;
      c.contractSeconds = 14 * 60 + 32;
      c.contractSerial = "7";
      c.boardNote = "Posted for this half of October: elk, moose and mallard contracts.";
      c.board.add(new PhoneModel.Offer(0, "Camp provisions", "Recover and field-dress 2 whitetails before the clock runs out - meat for the camp.", 40, 2, ""));
      c.board.add(new PhoneModel.Offer(1, "Long shot", "Recover a whitetail shot cleanly from at least 150 metres.", 60, 1, ""));
      c.board.add(new PhoneModel.Offer(2, "Clean kill", "Take a whitetail with a heart or lung shot and recover it before the clock runs out.", 35, 1, ""));
      c.board.add(new PhoneModel.Offer(5, "Bugle season", "Call a bull elk in and take him within five minutes.", 50, 1, "Elk"));
      c.board.add(new PhoneModel.Offer(6, "Opening morning", "Take two mallards over your decoys.", 35, 2, "Mallard"));
      c.assignState = 1;
      c.assignId = "frontierhunts:trail_reading";
      c.assignTitle = "Trail reading";
      c.assignText = "Inspect three fresh animal clues with the tracking key.";
      c.assignCount = 2;
      c.assignTarget = 3;
      c.assignTokens = 18;
      c.assignXp = 60;
      c.assignTicks = 20L * 60 * 9;
      c.assignments.add(new PhoneModel.Assignment("frontierhunts:field_patrol", "Field patrol", "Harvest two deer in the reserve and recover them.", 30, 80, 2, 0,
         "Field", 0));
      c.assignments.add(new PhoneModel.Assignment("frontierhunts:before_nightfall", "Before nightfall", "Recover a deer before the sun goes down.", 25, 70, 1,
         2400, "Timed", 12000));
      c.assignments.add(new PhoneModel.Assignment("frontierhunts:predator_control", "Predator control", "Call in and take two coyotes or wolves.", 40, 90, 2,
         0, "Bounty", 0));
   }

   static void wallet(PhoneModel m) {
      PhoneModel.Wallet w = m.wallet;
      w.loaded = true;
      w.rules = "Relaxed";
      w.period = "Fall, year 1";
      w.monthName = "October";
      w.daysLeft = 4;
      w.licence = true;
      w.licenceHeld = true;
      w.tokens = 245;
      w.tags.add(new PhoneModel.Tag("Deer tag", "frontierhunts:deer_tag", 0xFFE8B23A, 1, 2, 3, "Sep - Jan · open now", true));
      w.tags.add(new PhoneModel.Tag("Elk tag", "frontierhunts:elk_tag", 0xFFD8663A, 0, 0, 1, "Sep - Nov · open now", true));
      w.tags.add(new PhoneModel.Tag("Bear tag", "frontierhunts:bear_tag", 0xFF3A6AB0, 1, 1, 1, "Apr - May, Sep - Oct · open now", true));
      w.tags.add(new PhoneModel.Tag("Bison tag", "frontierhunts:bison_tag", 0xFF8A5A3A, 0, 0, 1, "Nov - Feb", false));
      w.stamps.add(new PhoneModel.Stamp("Upland bird stamp", "frontierhunts:upland_stamp", true, "1 / 4 grouse today", "Sep - Dec · open now", true));
      w.stamps.add(new PhoneModel.Stamp("Waterfowl stamp", "frontierhunts:waterfowl_stamp", false, "", "Oct - Jan · open now", true));
      w.openNow.add("Whitetail");
      w.openNow.add("Elk");
      w.openNow.add("Moose");
      w.openNow.add("Black bear");
      w.openNow.add("Grizzly");
      w.openNow.add("Ruffed grouse");
      w.openNow.add("Mallard");
      w.openNow.add("Coyote");
      w.filled = 3;
      w.record = 11;
      w.trophies.add(new PhoneModel.Trophy("whitetail", "Whitetail buck · 10 points", "96 kg · score 142", "Oct 12, year 1", "North Ridge (120, -380)", true));
      w.trophies.add(new PhoneModel.Trophy("whitetail", "Whitetail doe", "54 kg", "Oct 9, year 1", "Aspen Flats (40, -412)", true));
      w.trophies.add(new PhoneModel.Trophy("black_bear", "Black bear", "131 kg · boar", "Sep 28, year 1", "Creek bottom (60, -280)", true));
      w.trophies.add(new PhoneModel.Trophy("mallard", "Mallard drake", "1.3 kg", "Sep 20, year 1", "Lakeside (240, -610)", true));
      w.rank = "Hunter";
      w.nextRank = "Guide";
      w.xp = 1340;
      w.rankFrom = 1000;
      w.rankTo = 2000;
      w.harvests = 11;
      w.recoveries = 10;
      w.clean = 7;
      w.longest = 212;
      w.bestScore = 142;
      w.species = 4;
      w.photos = 37;
      w.achDone = 23;
      w.achTotal = 64;
      w.goals.add(new PhoneModel.Goal("50 trail camera photos", "Your trail cameras catch fifty photos of wildlife.", 37, 50, 60, 0));
      w.goals.add(new PhoneModel.Goal("Ten clean kills", "Drop ten animals where they stand with a heart or lung shot.", 7, 10, 80, 0));
      w.goals.add(new PhoneModel.Goal("Long shot", "Take an animal from 250 m or more.", 212, 250, 70, 2));
      w.goals.add(new PhoneModel.Goal("Five species", "Take five different game species.", 4, 5, 90, 0));
      w.trophies.add(new PhoneModel.Trophy("whitetail", "Whitetail buck · 6 points", "71 kg", "Aug 30, year 1", "Closed season", false));
   }

   static void games(PhoneModel m) {
      PhoneModel.Games g = m.games;
      m.players.add("Austin");
      m.players.add("RidgeRunner");
      m.players.add("Marsh_Hen");
      m.players.add("Cody");
      g.invites.add(new PhoneModel.Invite(77L, "RidgeRunner", 0, 0L));
      g.invites.add(new PhoneModel.Invite(78L, "Marsh_Hen", 2, 0L));
      PhoneModel.Online o = new PhoneModel.Online();
      o.id = 91L;
      o.game = 1;
      o.me = 0;
      o.names[0] = "Austin";
      o.names[1] = "Cody";
      o.state = new int[]{0};
      g.online.add(o);
      g.flushBest = 1286;
      g.leaderboard.add(new PhoneModel.Score("RidgeRunner", 1644, false, ""));
      g.leaderboard.add(new PhoneModel.Score("Austin", 1286, true, ""));
      g.leaderboard.add(new PhoneModel.Score("Marsh_Hen", 1190, false, ""));
      g.leaderboard.add(new PhoneModel.Score("Cody", 902, false, ""));
      g.diceBest = 241;
      g.puzzlesSolved = 0b1011L;
      int[] st = g.stats;
      st[0] = 3;
      st[1] = 5;
      st[6] = 4;
      st[7] = 2;
      st[12] = 6;
      st[13] = 3;
   }

   static PhoneModel model() {
      PhoneModel m = new PhoneModel();
      m.dayTime = at(6, 42);
      m.gameTime = 24000L * 61 + m.dayTime;
      m.month = 9;
      m.day = 14;
      m.moonPhase = 2;
      m.season = "Fall";
      m.rutTitle = "Seeking";
      m.rutNote = "Bucks are cruising for the first does. Rattling and grunts carry best.";
      m.rutActive = true;
      m.battery = 78;
      m.signal = 3;
      // [1.4.0] the Camera app's gallery and shared photos
      m.photos.loaded = true;
      String[] imgs = {"player_day", "buck_day", "doe_day", "bear_day", "buck_ir", "empty_ir", "player_day", "doe_day"};
      java.util.Map<String, BufferedImage> pics = new java.util.HashMap<>();
      for (int i = 0; i < imgs.length; i++) {
         String key = "shot" + i;
         m.photos.shots.add(new PhoneModel.Shot(key, 1_790_000_000_000L - i * 3_600_000L));
         pics.put(key, img(imgs[i]));
      }
      m.photos.thumb = sh -> {
         BufferedImage b = pics.get(sh.key());
         return b == null ? null : new PhoneModel.Pic(b, b.getWidth(), b.getHeight());
      };
      m.photos.full = m.photos.thumb;
      BufferedImage shared = img("buck_day");
      m.photos.shared = id -> new PhoneModel.Pic(shared, shared.getWidth(), shared.getHeight());
      PhoneModel.Messages ms = m.messages;
      ms.loaded = true;
      long t0 = System.currentTimeMillis() - 3_600_000L;
      ms.texts.add(new PhoneModel.Text("Marsh_Hen", false, "Ducks are piling into the north slough. Bring the 12 gauge.", t0, true));
      ms.texts.add(new PhoneModel.Text("Marsh_Hen", true, "On my way after sunrise. Save me a spot in the blind!", t0 + 120_000L, true));
      ms.texts.add(new PhoneModel.Text("RidgeRunner", true, "Any luck at the scrape line last night?", t0 + 1_800_000L, true));
      ms.texts.add(new PhoneModel.Text("RidgeRunner", false, "Nothing at dark. Wind swirled on me.", t0 + 2_400_000L, true));
      ms.texts.add(new PhoneModel.Text("RidgeRunner", false, "Big 8 on my north cam this morning", t0 + 3_400_000L, false));
      ms.texts.add(new PhoneModel.Text("RidgeRunner", false, "Same buck from last week. He's cruising the creek at first light", t0 + 3_500_000L, false));
      // [1.4.0] emoji and a shared photo
      ms.texts.add(new PhoneModel.Text("RidgeRunner", true, "No way :wow: send it!", t0 + 3_520_000L, true));
      ms.texts.add(new PhoneModel.Text("RidgeRunner", false, "[photo:00000000000000a1] Look at that rack :fire:", t0 + 3_540_000L, false));
      ms.texts.add(new PhoneModel.Text("RidgeRunner", true, ":antlers: :fire: :hundred:", t0 + 3_560_000L, true));
      ms.contacts.add("RidgeRunner");
      ms.contacts.add("Marsh_Hen");
      ms.contacts.add("Cody");
      ms.unread = 2;
      m.notices.add(new PhoneModel.Notice("weather", "Wind storm warning", "Gusts from the northwest in about 1 h 35 min", 1L));
      m.notices.add(new PhoneModel.Notice("contracts", "Contract ready", "Elk in the Rut: hand it in for 60 tokens", 2L));
      m.notices.add(new PhoneModel.Notice("cams", "Creek Crossing", "Motion: a whitetail buck, 8 points, 5:58 AM", 3L));
      m.notices.add(new PhoneModel.Notice("messages", "RidgeRunner", "Big 8 on my north cam this morning", 4L));
      m.playerName = "Austin";
      m.x = 120;
      m.y = 74;
      m.z = -340;
      m.yaw = 30;
      m.biome = "Old growth spruce taiga";
      PhoneModel.Weather w = m.weather;
      w.loaded = true;
      w.icon = "partly";
      w.condition = "Partly cloudy";
      w.tempC = 4.0F;
      w.feelsC = 1.0F;
      w.windKmh = 11.0F;
      w.windFrom = 315.0F;
      w.huntScore = 82;
      w.sunrise = 6 * 60 + 14;
      w.sunset = 18 * 60 + 41;
      w.updated = m.gameTime - 20 * 60 * 3;
      w.hourStart = 6;
      float[] temps = {4, 5, 7, 9, 10, 11, 11};
      String[] icons = {"partly", "partly", "clear", "clear", "partly", "cloudy", "rain"};
      int[] rain = {0, 0, 0, 5, 10, 30, 60};
      for (int i = 0; i < 7; i++) {
         w.hourTemp[i] = temps[i];
         w.hourIcon[i] = icons[i];
         w.hourRain[i] = rain[i];
      }
      w.days.add(new PhoneModel.Day("Thu", "partly", 11, 1, 10, ""));
      w.days.add(new PhoneModel.Day("Fri", "rain", 8, 3, 70, ""));
      w.days.add(new PhoneModel.Day("Sat", "thunder", 9, 2, 55, ""));
      w.days.add(new PhoneModel.Day("Sun", "clear", 6, -3, 5, ""));
      w.days.add(new PhoneModel.Day("Mon", "snow", 1, -6, 40, ""));
      w.alerts.add(new PhoneModel.Alert("windstorm", "Wind storm warning", "Gusts building from the northwest this afternoon. Deer will bed in thick cover.",
         95, 0.5F));
      w.huntLine = "Seeking phase · cold, clear dawn";
      w.huntReasons.add("Prime time: the first two hours after sunrise (06:14).");
      w.huntReasons.add("The rut is in the seeking phase: bucks cruise in daylight.");
      w.huntReasons.add("Rain arrives this afternoon; deer feed hard ahead of the front.");
      cams(m);
      places(m);
      contracts(m);
      wallet(m);
      games(m);
      m.map.target = "board:1";
      m.map.places.add(new PhoneModel.Place("board:1", "board", "Contract board", 260, 72, -610, false, "Lakeside village"));
      return m;
   }
}
