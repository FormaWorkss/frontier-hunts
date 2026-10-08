package com.formaworks.frontierhunts.academy;

import com.formaworks.frontierhunts.journal.Skill;
import java.util.Locale;

/**
 * [academy] The Ranger Academy courses (the hunt from first sight to the meat pole). [onboard] ARCHERY was added as the
 * sixth constant (ordinals stay stable for saved home records); [onboard2] it is also the last course taught (the
 * capstone: every shot a bow hunter meets). {@link #step} is the curriculum position shown to players and
 * {@link #curriculum()} the order of the dossier.
 * Every course runs in its own purpose-built plot in the training grounds dimension. Objectives are counted on the
 * server only; the client gets their progress in {@link AcademyNetwork.Hud}.
 *
 * <p>Plot extents are local block offsets around the plot origin (the shooter's mark / start mark at ground level).
 * {@code cert} is the old Ranger training node a first pass certifies (0 Steady hold, 1 Field dressing, 2 Trail ages).
 */
public enum Course {
   GLASSING("glassing", 10 * 60 * 20, -1, Skill.STALKING, 90, 60,
      new String[]{"spot", "range", "call"}, new int[]{3, 1, 1},
      new String[]{"frontierhunts:binoculars", "frontierhunts:rangefinder", "frontierhunts:grunt_tube", "frontierhunts:bleat_call", "frontierhunts:wind_checker"},
      new String[0], true, -64, 64, -118, 14),
   STALK("stalk", 10 * 60 * 20, -1, Skill.STALKING, 120, 70,
      new String[]{"close", "hold"}, new int[]{1, 3},
      new String[]{"frontierhunts:wind_checker", "frontierhunts:binoculars", "frontierhunts:scent_cover*2"},
      new String[]{"frontierhunts:ghillie_hood", "frontierhunts:ghillie_jacket", "frontierhunts:ghillie_trousers"}, true, -44, 44, -98, 12),
   RANGE("range", 12 * 60 * 20, 0, Skill.MARKSMANSHIP, 140, 90,
      new String[]{"paper25", "paper50", "paper100", "deer50", "deer100"}, new int[]{3, 2, 1, 1, 1},
      new String[]{"frontierhunts:ridgeline_rifle", "frontierhunts:reserve_308*40", "frontierhunts:field_bow", "frontierhunts:field_arrow*24",
         "frontierhunts:rangefinder", "frontierhunts:wind_checker"},
      new String[0], true, -26, 26, -122, 12),
   TRACKING("tracking", 10 * 60 * 20, 2, Skill.TRACKING, 120, 80,
      new String[]{"hitsite", "follow", "bed", "recover"}, new int[]{1, 4, 1, 1},
      new String[]{"frontierhunts:binoculars", "frontierhunts:wind_checker", "frontierhunts:field_knife"},
      new String[0], false, -34, 34, -84, 14),
   DRESSING("dressing", 6 * 60 * 20, 1, Skill.BUTCHERY, 80, 60,
      new String[]{"read", "dress"}, new int[]{1, 1},
      new String[]{"frontierhunts:skinning_tool", "frontierhunts:field_knife"},
      new String[0], false, -22, 22, -26, 14),
   // [onboard] The Archery Range: paper at 10/20/30/40 yd, 3D deer, a walking deer, an elevated tree-stand shot
   ARCHERY("archery", 12 * 60 * 20, -1, Skill.MARKSMANSHIP, 110, 70,
      new String[]{"paper", "deer", "moving", "stand"}, new int[]{4, 2, 1, 1},
      new String[]{"frontierhunts:field_bow", "frontierhunts:field_arrow*32#field_point", "frontierhunts:field_arrow*16#fixed_broadhead",
         "frontierhunts:compound_bow", "frontierhunts:rangefinder", "frontierhunts:wind_checker"},
      new String[0], true, -32, 32, -54, 10);

   private static final Course[] VALUES = values();
   /** [onboard2] Teaching order: the hunt from sight to the meat pole, the rifle range, and the Archery Range last. */
   private static final Course[] CURRICULUM = {GLASSING, STALK, TRACKING, DRESSING, RANGE, ARCHERY};

   public final String key;
   public final int timeLimit;
   public final int cert;
   public final Skill skill;
   public final int skillXp;
   public final int rangerXp;
   public final String[] objectives;
   public final int[] targets;
   public final String[] gear;
   public final String[] armor;
   public final boolean wind;
   public final int minX, maxX, minZ, maxZ;

   Course(String key, int timeLimit, int cert, Skill skill, int skillXp, int rangerXp, String[] objectives, int[] targets, String[] gear,
      String[] armor, boolean wind, int minX, int maxX, int minZ, int maxZ) {
      this.key = key;
      this.timeLimit = timeLimit;
      this.cert = cert;
      this.skill = skill;
      this.skillXp = skillXp;
      this.rangerXp = rangerXp;
      this.objectives = objectives;
      this.targets = targets;
      this.gear = gear;
      this.armor = armor;
      this.wind = wind;
      this.minX = minX;
      this.maxX = maxX;
      this.minZ = minZ;
      this.maxZ = maxZ;
   }

   public static Course byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : null;
   }

   public static Course byKey(String key) {
      for (Course c : VALUES) {
         if (c.key.equals(key)) {
            return c;
         }
      }
      return null;
   }

   public static int count() {
      return VALUES.length;
   }

   /** [onboard] The courses in teaching order (dossier list, "course n of m"). */
   public static Course[] curriculum() {
      return CURRICULUM.clone();
   }

   /** [onboard] 1-based position in the curriculum. */
   public int step() {
      for (int i = 0; i < CURRICULUM.length; i++) {
         if (CURRICULUM[i] == this) {
            return i + 1;
         }
      }
      return this.ordinal() + 1;
   }

   public String lang(String part) {
      return "academy.frontierhunts.course." + this.key + "." + part;
   }

   public String objectiveLang(int i) {
      return "academy.frontierhunts.course." + this.key + ".objective." + this.objectives[i];
   }

   /** All objectives done? */
   public boolean complete(int[] progress) {
      if (progress == null || progress.length != this.targets.length) {
         return false;
      }
      for (int i = 0; i < this.targets.length; i++) {
         if (progress[i] < this.targets[i]) {
            return false;
         }
      }
      return true;
   }

   public String upperKey() {
      return this.key.toUpperCase(Locale.ROOT);
   }
}
