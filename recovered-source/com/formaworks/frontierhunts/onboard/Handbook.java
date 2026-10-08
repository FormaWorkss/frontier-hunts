package com.formaworks.frontierhunts.onboard;

import com.formaworks.frontierhunts.academy.Course;
import com.formaworks.frontierhunts.guide.Lesson;
import java.util.ArrayList;
import java.util.List;

/**
 * [onboard] The Frontier Handbook's single learning path: eight steps ("your first hour" and beyond), each with one to
 * four tasks. Field School lessons are tasks of steps 4-6 (in lesson order), Ranger Academy courses are the
 * "practice" link of the tasks they train, and the Hunter's Journal checklist carries the rest. Shared by both
 * sides: the server sends a task bitmask, both sides compute the one "next step" from it with {@link #next(int)}.
 *
 * <p>Task ordinals are stable (bits of the synced mask and of saved skips): add new tasks at the end only. [onboard2] The
 * order the path walks them in is {@link #ORDER} (step order), not the ordinal: the Archery Range moved from step 2 to the
 * end of step 8, the last Ranger Academy course.
 */
public final class Handbook {
   public static final int STEPS = 8;

   public enum Task {
      TABLE(1, null, null, "minecraft:crafting_table"),
      ARROWS(2, null, null, "frontierhunts:field_arrow"), // [onboard2] no course: the Archery Range is the last one
      ARCHERY(8, null, Course.ARCHERY, "frontierhunts:field_bow"), // [onboard2] step 8, the path's last task
      BENCH(1, null, null, "frontierhunts:frontier_workbench"), // [benches] step 1: the three benches (was step 3, the Bow Tuning Rack)
      TIPS(3, null, null, "frontierhunts:field_point"),
      WIND(4, Lesson.WIND, Course.STALK, "frontierhunts:wind_checker"),
      SIGN(4, Lesson.SIGN, Course.TRACKING, "frontierhunts:frontier_handbook"), // [onebook] was the Hunter's Journal item
      GLASS(4, Lesson.GLASS, Course.GLASSING, "frontierhunts:binoculars"),
      STALK(4, Lesson.STALK, Course.STALK, "minecraft:leather_boots"),
      SHOT(5, Lesson.SHOT, Course.RANGE, "frontierhunts:field_bow"), // [onboard2] The Range's 3D deer lanes (it lends a bow too)
      TRAIL(5, Lesson.TRAIL, Course.TRACKING, "frontierhunts:hound_lead"),
      HARVEST(6, Lesson.HARVEST, Course.DRESSING, "frontierhunts:skinning_tool"),
      COOK(6, null, null, "frontierhunts:cooked_venison"),
      TENT(7, null, null, "frontierhunts:trail_dome_tent"),
      FURS(7, null, null, "frontierhunts:fur_hat"),
      RIFLE(8, null, Course.RANGE, "frontierhunts:ridgeline_rifle"),
      REPORT(8, null, null, "frontierhunts:expedition_board"), // [onebook] was the Expedition Guide item
      /** [licence] carry a Hunting Licence (free after the Archery course) before the first hunt */
      LICENCE(8, null, null, "frontierhunts:hunting_licence"), // [1.1.5] step 8, right after the Archery Range that makes it free (it was step 5, before any way to afford one)
      /** [licence] cook a camp meal in the Dutch oven */
      CAMP_COOK(6, null, null, "frontierhunts:dutch_oven");

      private static final Task[] VALUES = values();

      /** 1-based step this task belongs to. */
      public final int step;
      /** The Field School lesson this task is (null for Handbook-only tasks). */
      public final Lesson lesson;
      /** The Ranger Academy course that practises it (null when none fits). */
      public final Course course;
      /** Item id drawn as the task's icon. */
      public final String icon;

      Task(int step, Lesson lesson, Course course, String icon) {
         this.step = step;
         this.lesson = lesson;
         this.course = course;
         this.icon = icon;
      }

      public int bit() {
         return 1 << this.ordinal();
      }

      public boolean done(int mask) {
         return (mask & this.bit()) != 0;
      }

      public String key() {
         return this.name().toLowerCase(java.util.Locale.ROOT);
      }

      public String lang(String part) {
         return "onboard.frontierhunts.task." + this.key() + "." + part;
      }

      public static Task byId(int id) {
         return id >= 0 && id < VALUES.length ? VALUES[id] : null;
      }

      public static int count() {
         return VALUES.length;
      }
   }

   public static final int ALL = (1 << Task.count()) - 1;
   /** [onboard2] The path in walking order: by step, then as listed here. Every task exactly once. */
   private static final Task[] ORDER = {
      // [1.1.5] within "find game" the deer lessons that need no crafted gear (sign, stalk) come before the wind checker
      // and binoculars; the licence waits for step 8 (expedition tokens buy it; the Archery Range, still last, makes it free)
      // [benches] the crafting table, then the three benches, then arrows
      Task.TABLE, Task.BENCH, Task.ARROWS, Task.TIPS, Task.SIGN, Task.STALK, Task.WIND, Task.GLASS, Task.SHOT, Task.TRAIL, Task.HARVEST,
      Task.COOK, Task.CAMP_COOK, Task.TENT, Task.FURS, Task.RIFLE, Task.REPORT, Task.LICENCE, Task.ARCHERY
   };

   /** [onboard2] All tasks in the order the path walks them (step order). */
   public static Task[] order() {
      return ORDER.clone();
   }

   private Handbook() {
   }

   /** The one next thing to do: the first task not done, in step order. Null when the whole path is done. */
   public static Task next(int mask) {
      for (Task t : ORDER) {
         if (!t.done(mask)) {
            return t;
         }
      }
      return null;
   }

   public static List<Task> tasks(int step) {
      List<Task> l = new ArrayList<>();
      for (Task t : ORDER) {
         if (t.step == step) {
            l.add(t);
         }
      }
      return l;
   }

   public static boolean stepDone(int step, int mask) {
      for (Task t : Task.VALUES) {
         if (t.step == step && !t.done(mask)) {
            return false;
         }
      }
      return true;
   }

   public static int stepsDone(int mask) {
      int n = 0;
      for (int s = 1; s <= STEPS; s++) {
         if (stepDone(s, mask)) {
            n++;
         }
      }
      return n;
   }

   /** The step the hunter is on (1..8), or STEPS + 1 when everything is done. */
   public static int currentStep(int mask) {
      Task t = next(mask);
      return t == null ? STEPS + 1 : t.step;
   }

   /** The handbook task that is this lesson. */
   public static Task forLesson(Lesson l) {
      for (Task t : Task.VALUES) {
         if (t.lesson == l) {
            return t;
         }
      }
      return null;
   }

   /** Field School lesson -> the Academy course that practises it ("Practice this on the range"); null for the tips. */
   public static Course courseFor(Lesson l) {
      Task t = forLesson(l);
      return t == null ? null : t.course;
   }

   /** Passing a course credits these Field School lessons (and through them the Handbook and the journal). */
   public static Lesson[] lessonsFor(Course c) {
      return switch (c) {
         case ARCHERY, RANGE -> new Lesson[]{Lesson.SHOT};
         case GLASSING -> new Lesson[]{Lesson.GLASS};
         case STALK -> new Lesson[]{Lesson.WIND, Lesson.STALK};
         case TRACKING -> new Lesson[]{Lesson.SIGN, Lesson.TRAIL};
         case DRESSING -> new Lesson[]{Lesson.HARVEST};
      };
   }

   /** The course to recommend now: the next task's practice course, if it has one that is not passed yet. */
   public static Course recommended(int mask, int passedCourses) {
      Task t = next(mask);
      if (t == null || t.course == null || (passedCourses & 1 << t.course.ordinal()) != 0) {
         return null;
      }
      return t.course;
   }

   /** Mask of tasks that are Field School lessons. */
   public static int lessonMask() {
      int m = 0;
      for (Task t : Task.VALUES) {
         if (t.lesson != null) {
            m |= t.bit();
         }
      }
      return m;
   }
}
