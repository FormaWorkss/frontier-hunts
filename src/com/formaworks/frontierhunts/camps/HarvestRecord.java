package com.formaworks.frontierhunts.camps;

import java.util.Locale;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** One entry in the record book. Immutable once written (admins can only delete whole entries). */
public final class HarvestRecord {
   public long id;
   public UUID hunter;
   public String name = "Hunter";
   public String species = "whitetail";
   public boolean male = true;
   /** Antler score in inches (0 for antlerless / non-antlered species). */
   public double score;
   public int pointsL;
   public int pointsR;
   public double weightKg;
   public double shotM;
   public int season;
   public long monthSerial;
   public int day;
   public long gameTime;
   public long epochMs;
   public boolean legendary;
   public String legendName = "";
   public String dim = "minecraft:overworld";
   public int x;
   public int y;
   public int z;
   public String guide = "";
   public boolean called;
   public String camp = "";
   /** deer_traits compound for rack rendering on the board (antlered deer only), may be null. */
   public CompoundTag traits;
   public UUID animal;

   public Quarry quarry() {
      Quarry q = Quarry.find(this.species);
      return q == null ? Quarry.WHITETAIL : q;
   }

   public String scoreText() {
      return Fmt.inches(this.score);
   }

   public String pointsText() {
      return this.pointsL + this.pointsR <= 0 ? "" : this.pointsL + "x" + this.pointsR;
   }

   public String dateText() {
      return Fmt.date(this.monthSerial, this.day);
   }

   /** "142 3/8" 5x5 buck", "186 kg black bear", "0.8 kg drake" … */
   public String headline() {
      Quarry q = this.quarry();
      if (q.rack() && this.score > 0.0) {
         String pts = this.pointsText();
         return this.scoreText() + " " + (pts.isEmpty() ? "" : pts + " ") + (q == Quarry.WHITETAIL ? "buck" : q.title.toLowerCase(Locale.ROOT) + " " + q.male);
      }
      if (q.rack()) {
         return Fmt.kg(this.weightKg) + " " + q.title.toLowerCase(Locale.ROOT) + " " + (this.male ? q.male : q.female);
      }
      return Fmt.kg(this.weightKg) + " " + q.title.toLowerCase(Locale.ROOT);
   }

   public CompoundTag save(boolean withTraits) {
      CompoundTag t = new CompoundTag();
      t.putLong("id", this.id);
      if (this.hunter != null) {
         t.putUUID("hunter", this.hunter);
      }
      t.putString("name", this.name);
      t.putString("species", this.species);
      t.putBoolean("male", this.male);
      t.putDouble("score", this.score);
      t.putInt("pl", this.pointsL);
      t.putInt("pr", this.pointsR);
      t.putDouble("kg", this.weightKg);
      t.putDouble("shot", this.shotM);
      t.putInt("season", this.season);
      t.putLong("month", this.monthSerial);
      t.putInt("day", this.day);
      t.putLong("time", this.gameTime);
      t.putLong("epoch", this.epochMs);
      t.putBoolean("legendary", this.legendary);
      t.putString("legend", this.legendName);
      t.putString("dim", this.dim);
      t.putInt("x", this.x);
      t.putInt("y", this.y);
      t.putInt("z", this.z);
      t.putString("guide", this.guide);
      t.putBoolean("called", this.called);
      t.putString("camp", this.camp);
      if (withTraits && this.traits != null) {
         t.put("traits", this.traits.copy());
      }
      if (this.animal != null) {
         t.putUUID("animal", this.animal);
      }
      return t;
   }

   public static HarvestRecord load(CompoundTag t) {
      HarvestRecord r = new HarvestRecord();
      r.id = t.getLong("id");
      r.hunter = t.hasUUID("hunter") ? t.getUUID("hunter") : null;
      r.name = clip(t.getString("name"), 32);
      r.species = clip(t.getString("species"), 32);
      r.male = !t.contains("male") || t.getBoolean("male");
      r.score = finite(t.getDouble("score"), 0.0, 2000.0);
      r.pointsL = Math.clamp(t.getInt("pl"), 0, 40);
      r.pointsR = Math.clamp(t.getInt("pr"), 0, 40);
      r.weightKg = finite(t.getDouble("kg"), 0.0, 5000.0);
      r.shotM = finite(t.getDouble("shot"), 0.0, 5000.0);
      r.season = Math.max(1, t.getInt("season"));
      r.monthSerial = Math.max(0L, t.getLong("month"));
      r.day = Math.max(1, t.getInt("day"));
      r.gameTime = t.getLong("time");
      r.epochMs = t.getLong("epoch");
      r.legendary = t.getBoolean("legendary");
      r.legendName = clip(t.getString("legend"), 48);
      r.dim = clip(t.getString("dim"), 96);
      r.x = t.getInt("x");
      r.y = t.getInt("y");
      r.z = t.getInt("z");
      r.guide = clip(t.getString("guide"), 32);
      r.called = t.getBoolean("called");
      r.camp = clip(t.getString("camp"), 32);
      r.traits = t.contains("traits", 10) ? t.getCompound("traits") : null;
      r.animal = t.hasUUID("animal") ? t.getUUID("animal") : null;
      return r;
   }

   static double finite(double v, double lo, double hi) {
      return Double.isFinite(v) ? Math.clamp(v, lo, hi) : lo;
   }

   static String clip(String s, int n) {
      return s == null ? "" : (s.length() > n ? s.substring(0, n) : s);
   }
}
