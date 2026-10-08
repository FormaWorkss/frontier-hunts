package com.formaworks.frontierhunts.phone.client.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * [phone] Everything the phone shows, as plain data. The game fills it on the client thread
 * ({@code phone.client.PhoneFeed}: the world, the server's phone packets, the trail camera library); the offline mocks
 * fill it with sample data. Apps only read it, and ask for changes through {@link PhoneActions}.
 */
public final class PhoneModel {
   // ------------------------------------------------------------------------------------------------ device
   /** the hunter carries a charged Field Phone */
   public boolean hasPhone = true;
   /** the mod's reduced-motion setting (no slides or zooms) */
   public boolean reducedMotion;
   /** opened at a Camera Base Station without a phone: only the camera app */
   public boolean station;
   public int battery = 100;
   public boolean charging;
   public String chargeNote = "";
   public int signal = 3;
   public boolean flashlight;
   /** the phone is held in a hand (the flashlight only shines from a hand) */
   public boolean held;
   public int alarmMinute = -1;
   public boolean alarmRinging;
   /** the tracking timer (real milliseconds) */
   public long timerEnd, timerLeft, timerTotal;
   public boolean timerRunning;
   public final Settings settings = new Settings();
   public String keyName = "U";

   // ------------------------------------------------------------------------------------------------ time and place
   public long dayTime = 1000L;
   public long gameTime;
   public int moonPhase;
   public int month = 9;
   public int day = 12;
   public int daysPerMonth = 7;
   public int year = 1;
   public String season = "Fall";
   public String rutTitle = "Pre-rut";
   public String rutNote = "";
   public boolean rutActive;
   public String dimension = "minecraft:overworld";
   public double x, y = 70.0, z;
   public float yaw;
   public String biome = "";
   public String playerName = "Hunter";
   public final List<String> players = new ArrayList<>();

   public final Weather weather = new Weather();
   public final Cams cams = new Cams();
   public final MapData map = new MapData();
   public final Contracts contracts = new Contracts();
   public final Wallet wallet = new Wallet();
   public final Games games = new Games();
   public final Messages messages = new Messages();
   /** [1.4.0] the Camera app's photos (on this computer) */
   public final Photos photos = new Photos();
   public final List<Notice> notices = new ArrayList<>();
   /** bumped whenever something in the model changes from outside (packets) */
   public int version;

   public static final class Settings {
      public boolean celsius = true;
      public boolean clock24;
      public boolean night;
      public boolean silent;
      public boolean vibrate = true;
      public boolean hudNav = true;
      public int volume = 80;
      public boolean notifyCams = true;
      public boolean notifyContracts = true;
      public boolean notifyWeather = true;
      public boolean notifyGames = true;
   }

   // ------------------------------------------------------------------------------------------------ weather

   public static final class Weather {
      public boolean loaded;
      public long updated;
      /** clear, cloudy, rain, thunder, snow, blizzard, fog, dust, wind, night_clear */
      public String icon = "clear";
      public String condition = "Clear";
      public float tempC = 12.0F;
      public float feelsC = 10.0F;
      public float windKmh = 8.0F;
      /** direction the wind blows FROM, degrees clockwise from north */
      public float windFrom = 300.0F;
      public float visibility = 9999.0F;
      public String severe = "";
      public float severity;
      public int sunrise = 5 * 60 + 10;
      public int sunset = 18 * 60 + 50;
      public final float[] hourTemp = new float[24];
      public final String[] hourIcon = new String[24];
      public final int[] hourRain = new int[24];
      public int hourStart;
      public final List<Day> days = new ArrayList<>();
      public final List<Alert> alerts = new ArrayList<>();
      public int huntScore = 60;
      public String huntLine = "";
      public final List<String> huntReasons = new ArrayList<>();
      public boolean raining;
      public int rainChange = -1;
      public boolean rainNext;

      public Weather() {
         for (int i = 0; i < 24; i++) {
            this.hourIcon[i] = "clear";
         }
      }
   }

   public record Day(String name, String icon, float hi, float lo, int rain, String note) {
   }

   public record Alert(String kind, String title, String body, int etaMinutes, float strength) {
   }

   // ------------------------------------------------------------------------------------------------ trail cameras

   public static final class Cams {
      public boolean loaded;
      public String seasonLine = "";
      public final List<Cam> list = new ArrayList<>();
      /** the camera whose roll is open, or 0 */
      public long open;
      public Gallery gallery;
      public String uplink;
   }

   public record Cam(long pos, String name, int percent, int frames, boolean live, boolean keepsLoaded, boolean bestBuck, int bestScore, String over,
      double distance, String bearing, PhotoRef latest) {
   }

   public static final class Gallery {
      public long pos;
      public String label = "";
      public int percent;
      public boolean live;
      public boolean keepsLoaded;
      public boolean hasRoll;
      public final List<PhotoRef> photos = new ArrayList<>();
   }

   /** One trail camera photo (the game wraps its darkroom photo, the mock a picture). */
   public interface PhotoRef {
      long id();

      /** 0 waiting, 1 queued, 2 developing, 3 loading, 4 ready, 5 remote (uplink), 6 failed */
      int state();

      Object thumb();

      /** the full-size picture, or null while it loads */
      Object full();

      int fullW();

      int fullH();

      String title();

      String detail();

      String when();

      boolean infrared();

      int frame();

      String note();

      /** a native fallback drawing (the old composite) for failed frames, or null */
      Canvas.Custom fallback();
   }

   // ------------------------------------------------------------------------------------------------ map

   public static final class MapData {
      public Object texture;
      public int centerX, centerZ;
      public int size = 256;
      public float progress = 1.0F;
      public final List<Place> places = new ArrayList<>();
      /** navigation target place id, or "" */
      public String target = "";
      public boolean area;
      public int areaX, areaZ, areaR;
      public String areaTitle = "";
      public String areaNote = "";
   }

   /**
    * A place on the map. kind: area, camp, lodge, board, ranger, station, cam, pin, home, spot (first-hunt camp spot).
    * {@code id} is stable ("pin:3", "cam:123", "board:x,z"...).
    */
   public record Place(String id, String kind, String name, int x, int y, int z, boolean removable, String note) {
   }

   // ------------------------------------------------------------------------------------------------ contracts

   public static final class Contracts {
      public boolean loaded;
      public int tokens;
      // the campaign (Ranger Mara's story)
      public boolean storyDone;
      public String storyId = "";
      public String storyTitle = "";
      public String storyText = "";
      public int storyCount, storyAmount, storyTokens, storyChapter;
      // the expedition contract
      public int contract = -1;
      public String contractStatus = "none";
      public String contractTitle = "";
      public String contractText = "";
      public int contractCount, contractAmount, contractTokens;
      public long contractSeconds;
      public String contractSerial = "";
      public final List<Offer> board = new ArrayList<>();
      public String boardNote = "";
      // ranger assignments
      public boolean eligible = true;
      /** 0 none, 1 active, 2 ready, 3 failed */
      public int assignState;
      public String assignId = "";
      public String assignTitle = "";
      public String assignText = "";
      public int assignCount, assignTarget, assignTokens, assignXp;
      public long assignTicks;
      public final List<Assignment> assignments = new ArrayList<>();
   }

   public record Offer(int index, String title, String text, int tokens, int amount, String species) {
   }

   public record Assignment(String id, String title, String text, int tokens, int xp, int target, int cooldownTicks, String kind, int duration) {
   }

   // ------------------------------------------------------------------------------------------------ wallet

   public static final class Wallet {
      public boolean loaded;
      public String rules = "Relaxed";
      public String period = "";
      public String monthName = "";
      public int daysLeft;
      public boolean licence;
      public boolean licenceHeld;
      public boolean suspended;
      public int tokens;
      public final List<Tag> tags = new ArrayList<>();
      public final List<Stamp> stamps = new ArrayList<>();
      public final List<String> openNow = new ArrayList<>();
      public int violations, strikes, fines, filled, record;
      public final List<Trophy> trophies = new ArrayList<>();
      // the journal record
      public String rank = "";
      public String nextRank = "";
      public long xp, rankFrom, rankTo;
      public int harvests, recoveries, clean, longest, bestScore, species, photos;
      // the journal's checklist: how many are done, and the ones closest to done
      public int achDone, achTotal;
      public final List<Goal> goals = new ArrayList<>();
      /** bumped by the feed whenever the wallet arrives (apps rebuild their cached strings) */
      public int version;
   }

   /** A journal challenge: title, hint, progress, XP, and how its numbers read (0 count, 1 km, 2 metres, 3 inches). */
   public record Goal(String title, String hint, int value, int target, int xp, int unit) {
   }

   public record Tag(String name, String item, int color, int held, int bought, int bag, String months, boolean open) {
   }

   public record Stamp(String name, String item, boolean held, String today, String months, boolean open) {
   }

   public record Trophy(String species, String title, String detail, String when, String where, boolean legal) {
   }

   // ------------------------------------------------------------------------------------------------ games

   public static final class Games {
      public final List<Invite> invites = new ArrayList<>();
      public final List<Online> online = new ArrayList<>();
      public final List<Score> leaderboard = new ArrayList<>();
      public int flushBest;
      public int flushRank;
      public long flushToken;
      public long flushSeed;
      public String flushResult = "";
      public final int[] stats = new int[18];
      public long puzzlesSolved;
      public int diceBest;
      /** bumped by the feed whenever the games state arrives */
      public int version;
   }

   public record Invite(long id, String from, int game, long expires) {
   }

   public record Score(String name, int score, boolean me, String when) {
   }

   /**
    * An online game in progress. {@code state} is the engine's serialised state as the server last sent it;
    * {@code seq} increases with every server update.
    */
   public static final class Online {
      public long id;
      public int game;
      public int me;
      public final String[] names = new String[2];
      public int[] state = new int[0];
      public int seq;
      /** 0 playing, 1 finished */
      public int status;
      /** -1 none, 0/1 winner index, 2 draw */
      public int winner = -1;
      public String reason = "";
      public boolean opponentAway;
      public int awaySeconds;
      /** finished: I asked for a rematch / they did / they left it; the rematch's session once it started */
      public boolean rematchMine, rematchTheirs, opponentGone;
      public long next;
      // flush race extras
      public long seed;
      public int startIn;
      public int opponentScore;
      public int[] finalScores = new int[2];
   }

   // ------------------------------------------------------------------------------------------------ messages

   public static final class Messages {
      public boolean loaded;
      /** every text, oldest first */
      public final List<Text> texts = new ArrayList<>();
      /** hunters who have a Field Phone on this world */
      public final List<String> contacts = new ArrayList<>();
      public int unread;
   }

   /** One text. {@code at} is real time (epoch ms). */
   public record Text(String peer, boolean mine, String text, long at, boolean read) {
   }

   // ------------------------------------------------------------------------------------------------ photos

   /** [1.4.0] A picture the game drew into a texture: its handle for {@link Canvas#image} and its size in pixels. */
   public record Pic(Object handle, int w, int h) {
   }

   /** [1.4.0] One photo in the Camera app's gallery. {@code key} identifies it to the game (its file). */
   public record Shot(String key, long time) {
   }

   public static final class Photos {
      public boolean loaded;
      /** newest first */
      public final List<Shot> shots = new ArrayList<>();
      public String folder = "";
      /** the thumbnail of a shot (asked for when missing: null until it is ready) */
      public java.util.function.Function<Shot, Pic> thumb = s -> null;
      /** the shot at viewing size (asked for when missing) */
      public java.util.function.Function<Shot, Pic> full = s -> null;
      /** a photo shared in a text, by id (asked for from the server when missing) */
      public java.util.function.LongFunction<Pic> shared = id -> null;
      /** the server doesn't have that shared photo any more */
      public java.util.function.LongPredicate sharedGone = id -> false;
   }

   // ------------------------------------------------------------------------------------------------ notifications

   public record Notice(String app, String title, String body, long at) {
   }
}
