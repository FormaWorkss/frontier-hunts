package com.formaworks.frontierhunts.academy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/** [academy] Offline tests of the training save/restore state machine (TrainingFlow) with a simulated player. */
public final class AcademyHarness {
   static int fails, checks;

   static void check(boolean ok, String what) {
      checks++;
      if (!ok) {
         fails++;
         System.out.println("FAIL " + what);
      }
   }

   /** A player: inventory as item ids, xp, health, game mode, dimension + x; persisted data like PlayerPersisted. */
   static final class Fake implements TrainingFlow.Host {
      UUID id = UUID.randomUUID();
      ListTag inv = new ListTag();
      int xp = 30;
      float health = 14.0F;
      int mode = 0; // survival
      String dim = "overworld";
      double x = 100.5;
      CompoundTag persisted = new CompoundTag();
      boolean teleportWorks = true;

      Fake() {
         for (String s : new String[]{"rifle", "ammo*30", "venison*12", "journal"}) {
            this.inv.add(StringTag.valueOf(s));
         }
      }

      public UUID id() { return this.id; }
      public CompoundTag persisted() { return this.persisted; }

      public CompoundTag captureHome() {
         CompoundTag t = new CompoundTag();
         t.put("inv", this.inv.copy());
         t.putInt("xp", this.xp);
         t.putFloat("health", this.health);
         t.putInt("mode", this.mode);
         t.putString("dim", this.dim);
         t.putDouble("x", this.x);
         return t;
      }

      public void prepareForTraining(Course c, String nonce) {
         this.inv = new ListTag();
         for (String g : c.gear) {
            this.inv.add(StringTag.valueOf("lent:" + g));
         }
         this.mode = 2;
         this.health = 20.0F;
      }

      public boolean enterPlot(Course c, int slot) {
         if (!this.teleportWorks) {
            return false;
         }
         this.dim = "training";
         this.x = 4096 + slot * 1024;
         return true;
      }

      public void wipeTraining() {
         this.inv = new ListTag();
      }

      public void applyHome(CompoundTag h, boolean position) {
         this.inv = h.getList("inv", 8).copy();
         this.xp = h.getInt("xp");
         this.health = h.getFloat("health");
         this.mode = h.getInt("mode");
         if (position) {
            this.dim = h.getString("dim");
            this.x = h.getDouble("x");
         }
      }

      public boolean inTraining() { return this.dim.equals("training"); }

      /** what the .dat file holds after a save */
      Fake saved() {
         Fake f = new Fake();
         copyTo(this, f);
         return f;
      }

      boolean home(String where, double at) {
         return this.inv.size() == 4 && this.inv.getString(0).equals("rifle") && this.inv.getString(2).equals("venison*12") && this.xp == 30
            && this.health == 14.0F && this.mode == 0 && this.dim.equals(where) && this.x == at;
      }

      boolean hasLent() {
         for (int i = 0; i < this.inv.size(); i++) {
            if (this.inv.getString(i).startsWith("lent:")) {
               return true;
            }
         }
         return false;
      }
   }

   static void copyTo(Fake a, Fake b) {
      try {
         java.lang.reflect.Field f = Fake.class.getDeclaredField("id");
         f.setAccessible(true);
      } catch (Exception ignored) {
      }
      b.id = a.id;
      b.inv = a.inv.copy();
      b.xp = a.xp;
      b.health = a.health;
      b.mode = a.mode;
      b.dim = a.dim;
      b.x = a.x;
      b.persisted = a.persisted.copy();
   }

   /** The SavedData backup with an explicit "save to disk". */
   static final class Store implements TrainingFlow.Backup {
      Map<UUID, CompoundTag> live = new HashMap<>();
      Map<UUID, CompoundTag> disk = new HashMap<>();

      public CompoundTag get(UUID id) { CompoundTag t = this.live.get(id); return t == null ? null : t.copy(); }
      public void put(UUID id, CompoundTag h) { this.live.put(id, h.copy()); }
      public void remove(UUID id) { this.live.remove(id); }
      void save() { this.disk = new HashMap<>(); this.live.forEach((k, v) -> this.disk.put(k, v.copy())); }
      void restart() { this.live = new HashMap<>(); this.disk.forEach((k, v) -> this.live.put(k, v.copy())); }
   }

   static Fake begun(Store s, Course c) {
      Fake p = new Fake();
      check(TrainingFlow.begin(p, s, c, 3, "nonce-1", 1000L), c + " begin");
      check(p.inTraining() && p.hasLent() && p.mode == 2, c + " in training with lent kit");
      check(TrainingFlow.hasRecord(p) && s.get(p.id) != null, c + " both records written");
      return p;
   }

   static void clean(Fake p, Store s, String what) {
      check(!TrainingFlow.hasRecord(p), what + ": own record cleared");
      check(s.get(p.id) == null, what + ": backup cleared");
      check(!p.hasLent(), what + ": no lent gear left");
   }

   public static void main(String[] args) {
      for (Course c : Course.values()) {
         // 1 start -> finish (pass)
         Store s = new Store();
         Fake p = begun(s, c);
         check(!TrainingFlow.begin(p, s, c, 4, "nonce-2", 1001L), c + " second begin refused while a record exists");
         check(TrainingFlow.finish(p, s), c + " finish");
         check(p.home("overworld", 100.5), c + " finish: home state and position back");
         clean(p, s, c + " finish");
         check(!TrainingFlow.finish(p, s), c + " finish twice is a no-op");
         check(p.home("overworld", 100.5), c + " no second restore");
      }
      // 2 abandon = finish
      {
         Store s = new Store();
         Fake p = begun(s, Course.RANGE);
         p.xp = 0; // a level-up sound would not matter: the restore replaces it
         TrainingFlow.finish(p, s);
         check(p.home("overworld", 100.5), "abandon restores");
      }
      // 3 death without the cancel (some other mod forced it): items dropped in the grounds, respawn at spawn
      {
         Store s = new Store();
         Fake p = begun(s, Course.STALK);
         p.inv = new ListTag();
         p.dim = "overworld";
         p.x = 0.5; // world spawn; persisted data survives the death clone
         check(TrainingFlow.recover(p, s) == TrainingFlow.Recovery.RESTORED, "death: recovered from own record");
         check(p.home("overworld", 100.5), "death: home state and position back, nothing lost");
         clean(p, s, "death");
      }
      // 4 disconnect mid-training: player saved in the grounds, login later
      {
         Store s = new Store();
         Fake p = begun(s, Course.TRACKING);
         Fake disk = p.saved();
         s.save();
         check(TrainingFlow.recover(disk, s) == TrainingFlow.Recovery.RESTORED, "disconnect: recovered at login");
         check(disk.home("overworld", 100.5), "disconnect: home back");
         clean(disk, s, "disconnect");
      }
      // 5 server restart (both saved during training)
      {
         Store s = new Store();
         Fake p = begun(s, Course.GLASSING);
         Fake disk = p.saved();
         s.save();
         s.restart();
         check(TrainingFlow.recover(disk, s) == TrainingFlow.Recovery.RESTORED, "restart: recovered");
         check(disk.home("overworld", 100.5), "restart: home back");
         clean(disk, s, "restart");
      }
      // 6 crash: player file is older than the session (never saved in training), backup was saved
      {
         Store s = new Store();
         Fake before = new Fake();
         Fake disk = before.saved();
         TrainingFlow.begin(before, s, Course.DRESSING, 1, "n", 5L);
         s.save();
         s.restart();
         check(TrainingFlow.recover(disk, s) == TrainingFlow.Recovery.DISCARDED_STALE, "stale backup discarded");
         check(disk.home("overworld", 100.5) && disk.inv.size() == 4, "stale backup: nothing applied twice, no duplication");
         check(s.get(disk.id) == null, "stale backup removed");
      }
      // 7 own record lost but still inside the grounds: backup restores
      {
         Store s = new Store();
         Fake p = begun(s, Course.RANGE);
         p.persisted = new CompoundTag();
         check(TrainingFlow.recover(p, s) == TrainingFlow.Recovery.RESTORED_BACKUP, "lost record: backup used");
         check(p.home("overworld", 100.5), "lost record: home back");
         clean(p, s, "lost record");
      }
      // 8 inside the grounds with nothing anywhere: stranded, nothing invented
      {
         Store s = new Store();
         Fake p = new Fake();
         p.dim = "training";
         check(TrainingFlow.recover(p, s) == TrainingFlow.Recovery.STRANDED, "stranded detected");
         check(p.inv.size() == 4, "stranded: a visitor's own items are not wiped");
      }
      // 9 forced exit (another mod's /home): items and stats back, position kept
      {
         Store s = new Store();
         Fake p = begun(s, Course.STALK);
         p.dim = "nether";
         p.x = -77.0;
         check(TrainingFlow.forcedExit(p, s), "forced exit handled");
         check(p.home("nether", -77.0), "forced exit: home state back, stays where it arrived");
         clean(p, s, "forced exit");
      }
      // 10 teleport failure: rolled back at once, never moved
      {
         Store s = new Store();
         Fake p = new Fake();
         p.teleportWorks = false;
         check(!TrainingFlow.begin(p, s, Course.RANGE, 0, "n", 1L), "failed teleport reported");
         check(p.home("overworld", 100.5), "failed teleport: untouched");
         clean(p, s, "failed teleport");
      }
      // 11 no record, not in the grounds: nothing to do
      {
         Store s = new Store();
         Fake p = new Fake();
         check(TrainingFlow.recover(p, s) == TrainingFlow.Recovery.NONE, "nothing to recover");
      }
      // 12 course definitions are consistent
      for (Course c : Course.values()) {
         check(c.objectives.length == c.targets.length && c.objectives.length > 0, c + " objectives/targets");
         check(c.minX < 0 && c.maxX > 0 && c.minZ < 0 && c.maxZ > 0, c + " extents");
         int[] all = c.targets.clone();
         check(c.complete(all), c + " complete at targets");
         all[0] = 0;
         check(!c.complete(all), c + " incomplete");
      }
      // 13 [onboard] the Archery Range: scoring, qualification, the walking deer's path, the stand box, the curriculum
      check(Course.byKey("archery") == Course.ARCHERY && Course.ARCHERY.cert < 0, "archery course registered, no certification");
      // [onboard2] the Archery Range is the LAST course; every other course keeps its order (glassing, stalk, tracking, dressing, range)
      Course[] cur = Course.curriculum();
      check(cur[cur.length - 1] == Course.ARCHERY && Course.ARCHERY.step() == Course.count() && cur.length == Course.count(), "archery taught last, curriculum complete");
      check(java.util.Arrays.equals(cur, new Course[]{Course.GLASSING, Course.STALK, Course.TRACKING, Course.DRESSING, Course.RANGE, Course.ARCHERY}),
         "other courses keep their order");
      for (int i = 0; i < cur.length; i++) {
         check(cur[i].step() == i + 1, "course " + cur[i].key + " is course " + (i + 1));
      }
      java.util.Set<Course> seen = new java.util.HashSet<>(java.util.List.of(Course.curriculum()));
      check(seen.size() == Course.count(), "curriculum lists every course once");
      check(ArcheryKit.ring(0.1).equals("gold") && ArcheryKit.ring(0.3).equals("red") && ArcheryKit.ring(0.6).equals("blue") && ArcheryKit.ring(0.8).equals("black")
         && ArcheryKit.ring(0.95).equals("white"), "archery rings");
      check(ArcheryKit.points("gold") == 10 && ArcheryKit.points("white") == 2, "archery points");
      check(ArcheryKit.qualifies(10, 0.6) && !ArcheryKit.qualifies(10, 0.7) && !ArcheryKit.qualifies(20, 0.8) && ArcheryKit.qualifies(40, 0.95)
         && !ArcheryKit.qualifies(30, 1.2), "paper qualification by distance");
      {
         double x = ArcheryKit.WALK_X0;
         int dir = 1, pause = 0, turns = 0;
         boolean inside = true;
         for (int t = 0; t < 2000; t++) {
            double[] w = ArcheryKit.walk(x, dir, pause);
            if ((int)w[1] != dir) {
               turns++;
            }
            x = w[0];
            dir = (int)w[1];
            pause = (int)w[2];
            inside &= x >= ArcheryKit.WALK_X0 - 1e-9 && x <= ArcheryKit.WALK_X1 + 1e-9;
         }
         check(inside && turns >= 4, "walking deer stays on its path and turns at both ends (" + turns + " turns)");
      }
      check(ArcheryKit.onStand(ArcheryKit.STAND_X + 0.5, ArcheryKit.STAND_FLOOR + 1.0, ArcheryKit.STAND_Z + 0.5)
         && !ArcheryKit.onStand(ArcheryKit.STAND_X + 0.5, 0.0, ArcheryKit.STAND_Z + 0.5) && !ArcheryKit.onStand(0.5, ArcheryKit.STAND_FLOOR + 1.0, 1.0),
         "tree stand box");
      for (int[] t : ArcheryKit.PAPER) {
         check(t[0] > Course.ARCHERY.minX && t[0] < Course.ARCHERY.maxX && t[1] > Course.ARCHERY.minZ && t[1] < -1, "paper " + t[2] + " yd inside the plot, down range");
         check(Math.abs(-t[1] - t[2] * 0.9144) < 1.5, "paper " + t[2] + " yd at its distance");
      }
      for (int[] l : ArcheryKit.LANES) {
         check(l[1] > Course.ARCHERY.minX + 2 && l[1] < Course.ARCHERY.maxX - 2 && l[2] > Course.ARCHERY.minZ + 2, "lane inside the plot");
      }
      check(Course.ARCHERY.targets.length == 4 && Course.ARCHERY.targets[0] == ArcheryKit.PAPER.length
         && Course.ARCHERY.targets[1] == 2, "archery objectives match the plot (4 paper, 2 deer)");
      // 14 [onboard] the Handbook path: one next step, steps in order, lessons and courses linked both ways
      {
         com.formaworks.frontierhunts.onboard.Handbook.Task first = com.formaworks.frontierhunts.onboard.Handbook.next(0);
         check(first == com.formaworks.frontierhunts.onboard.Handbook.Task.TABLE, "handbook starts at the crafting table");
         check(com.formaworks.frontierhunts.onboard.Handbook.next(com.formaworks.frontierhunts.onboard.Handbook.ALL) == null, "handbook done when all tasks are");
         int prevStep = 0;
         boolean ordered = true;
         for (com.formaworks.frontierhunts.onboard.Handbook.Task t : com.formaworks.frontierhunts.onboard.Handbook.order()) { // [onboard2] walking order
            ordered &= t.step >= prevStep && t.step >= 1 && t.step <= com.formaworks.frontierhunts.onboard.Handbook.STEPS;
            prevStep = t.step;
         }
         check(ordered, "handbook tasks in step order");
         for (int st = 1; st <= com.formaworks.frontierhunts.onboard.Handbook.STEPS; st++) {
            check(!com.formaworks.frontierhunts.onboard.Handbook.tasks(st).isEmpty(), "step " + st + " has tasks");
         }
         int lessonOrder = -1;
         boolean lessonsInOrder = true;
         for (com.formaworks.frontierhunts.guide.Lesson l : com.formaworks.frontierhunts.guide.Lesson.values()) {
            com.formaworks.frontierhunts.onboard.Handbook.Task t = com.formaworks.frontierhunts.onboard.Handbook.forLesson(l);
            if (l.optional()) {
               check(t == null, "optional tips are not a handbook task");
               continue;
            }
            check(t != null && t.course != null, "lesson " + l.key + " is a handbook task with a practice course");
            lessonsInOrder &= t != null && t.ordinal() > lessonOrder;
            lessonOrder = t == null ? lessonOrder : t.ordinal();
            boolean credited = false;
            for (com.formaworks.frontierhunts.guide.Lesson x : com.formaworks.frontierhunts.onboard.Handbook.lessonsFor(t.course)) {
               credited |= x == l;
            }
            check(credited, "passing " + t.course.key + " credits lesson " + l.key);
         }
         check(lessonsInOrder, "lessons keep their Field School order inside the handbook");
         for (Course c : Course.values()) {
            check(com.formaworks.frontierhunts.onboard.Handbook.lessonsFor(c).length > 0, c.key + " credits at least one lesson");
         }
         int m = com.formaworks.frontierhunts.onboard.Handbook.Task.TABLE.bit();
         // [onboard2] nothing recommends the Archery Range early: it is the last task of the path and the only one practising it
         check(com.formaworks.frontierhunts.onboard.Handbook.next(m) == com.formaworks.frontierhunts.onboard.Handbook.Task.ARROWS
            && com.formaworks.frontierhunts.onboard.Handbook.recommended(m, 0) == null, "after the table: arrows, no course recommended");
         com.formaworks.frontierhunts.onboard.Handbook.Task[] ord = com.formaworks.frontierhunts.onboard.Handbook.order();
         check(ord.length == com.formaworks.frontierhunts.onboard.Handbook.Task.count()
            && new java.util.HashSet<>(java.util.List.of(ord)).size() == ord.length, "walking order lists every task once");
         check(ord[ord.length - 1] == com.formaworks.frontierhunts.onboard.Handbook.Task.ARCHERY
            && com.formaworks.frontierhunts.onboard.Handbook.Task.ARCHERY.step == com.formaworks.frontierhunts.onboard.Handbook.STEPS, "archery is the last task");
         int firstArchery = -1;
         for (int i = 0; i < ord.length; i++) {
            if (ord[i].course == Course.ARCHERY && firstArchery < 0) {
               firstArchery = i;
            }
         }
         check(firstArchery == ord.length - 1, "no earlier task practises on the Archery Range");
         int all = com.formaworks.frontierhunts.onboard.Handbook.ALL & ~com.formaworks.frontierhunts.onboard.Handbook.Task.ARCHERY.bit();
         check(com.formaworks.frontierhunts.onboard.Handbook.next(all) == com.formaworks.frontierhunts.onboard.Handbook.Task.ARCHERY
            && com.formaworks.frontierhunts.onboard.Handbook.recommended(all, 0) == Course.ARCHERY, "at the very end: the Archery Range recommended");
         check(com.formaworks.frontierhunts.onboard.Handbook.recommended(all, 1 << Course.ARCHERY.ordinal()) == null, "a passed course is not recommended again");
         // walking the path task by task: the distinct courses it recommends, in order; the Archery Range comes last
         java.util.List<Course> recs = new java.util.ArrayList<>();
         int mm = 0;
         for (com.formaworks.frontierhunts.onboard.Handbook.Task t : ord) {
            Course rc = com.formaworks.frontierhunts.onboard.Handbook.recommended(mm, 0);
            if (rc != null && !recs.contains(rc)) {
               recs.add(rc);
            }
            mm |= t.bit();
         }
         check(!recs.isEmpty() && recs.get(recs.size() - 1) == Course.ARCHERY && recs.indexOf(Course.ARCHERY) == recs.size() - 1,
            "the Archery Range is recommended only after every other course " + recs);
         check(com.formaworks.frontierhunts.onboard.Handbook.currentStep(m) == 2 && com.formaworks.frontierhunts.onboard.Handbook.stepsDone(m) == 1, "step counting");
         check(com.formaworks.frontierhunts.onboard.Handbook.Task.count() <= 30, "task mask fits a varint int");
      }
      System.out.println(fails == 0 ? "ALL PASS (" + checks + " checks)" : fails + " FAILED of " + checks);
      System.exit(fails == 0 ? 0 : 1);
   }
}
