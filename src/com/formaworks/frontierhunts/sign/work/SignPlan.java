package com.formaworks.frontierhunts.sign.work;

/**
 * [deersign] The pure decisions and timings of a buck's sign work (no Minecraft objects, so the offline harness drives
 * exactly this): when he looks for something to mark, what he picks, how his walk / settle / work steps go, how long
 * each act lasts and how long he rests afterwards. {@link SignWork} applies it to the real deer.
 */
public final class SignPlan {
   public static final int WALK = 0;
   public static final int SETTLE = 1;
   public static final int ACT = 2;
   public static final int DONE = 3;
   public static final int ABORT = 4;
   /** distance (blocks) at which the walk hands over to the settling glide */
   public static final double SETTLE_AT = 0.85;
   /** when the path ends (a low canopy the full-height box cannot path under) settling starts from this close */
   public static final double SETTLE_NEAR = 1.8;
   /** settling glide per tick (a slow walk: the legs animate it) */
   public static final double GLIDE = 0.045;
   /** settling turn per tick (degrees) */
   public static final float TURN = 9.0F;
   /** ticks without progress before a walk is given up */
   public static final long STUCK = 140L;

   private SignPlan() {
   }

   /** Ticks to the next look for something to mark (12-22 s). */
   public static long lookInterval(float roll) {
      return 240L + (long)(roll * 200.0F);
   }

   /** Chance that a look turns into sign work, from the season-and-maturity rates. */
   public static float chance(float rubRate, float scrapeRate) {
      return Math.min(0.8F, (rubRate + scrapeRate) * 0.6F);
   }

   /**
    * What a look decides: 0 nothing, or {@link SignAct#RUB} / {@link SignAct#SCRAPE} / {@link SignAct#REVISIT}.
    *
    * @param scrapeNear one of the scrapes about him is within reach (14 blocks)
    * @param r1 r2 r3   three uniform rolls
    */
   public static int decide(float rubRate, float scrapeRate, boolean scrapeNear, float r1, float r2, float r3) {
      float c = chance(rubRate, scrapeRate);
      if (c <= 0.0F || r1 >= c) {
         return 0;
      }
      boolean scrape = r2 * (rubRate + scrapeRate) < scrapeRate;
      if (!scrape) {
         return SignAct.RUB;
      }
      return scrapeNear && r3 < 0.55F ? SignAct.REVISIT : SignAct.SCRAPE;
   }

   /** 0..1 maturity for stroke length and work time. */
   public static float mature(int ageMonths) {
      return Math.clamp((ageMonths - 18) / 36.0F, 0.0F, 1.0F);
   }

   /** Phases and their lengths (ticks) of an act; {@code rolls} are 5 uniform rolls. */
   public static int[][] phases(int kind, int ageMonths, float[] rolls) {
      float m = mature(ageMonths);
      if (kind == SignAct.RUB) {
         int rub = Math.min(300, 120 + (int)(m * 90.0F) + (int)(rolls[0] * 91.0F)); // 6-15 s
         return new int[][]{{SignAct.SNIFF_TREE, SignAct.RUB_STROKES}, {20 + (int)(rolls[1] * 14.0F), rub}};
      }
      return new int[][]{{SignAct.SNIFF_GROUND, SignAct.PAW, SignAct.LICK, SignAct.URINATE},
         {24 + (int)(rolls[1] * 18.0F), 80 + (int)(m * 40.0F) + (int)(rolls[2] * 50.0F), 80 + (int)(rolls[3] * 60.0F), 50 + (int)(rolls[4] * 30.0F)}};
   }

   /** Rest after finished work: a rub line (another rub soon) or a few minutes. */
   public static long restAfter(int kind, float r1, float r2) {
      boolean line = kind == SignAct.RUB && r1 < 0.45F;
      return line ? 200L + (long)(r2 * 300.0F) : 900L + (long)(r2 * 1400.0F);
   }

   public static long restAfterAbort(float r) {
      return 300L + (long)(r * 300.0F);
   }

   /** Walk deadline: 20 s plus 1.2 s a block. */
   public static long walkTicks(double dist) {
      return 400L + (long)(dist * 24.0);
   }

   /**
    * One step of the walk / settle / act machine.
    *
    * @param dist        horizontal distance to the stand point
    * @param yawErr      degrees still to turn
    * @param modelReady  the buck's head / rack geometry is built (the rub height and branch reach come from it)
    * @param inState     ticks in the current state
    * @param stuck       ticks since the walk last made progress
    * @param overdue     past the walk deadline
    * @param siteOk      the site is still there (tree / free cell / scrape)
    * @param actAge      ticks since the act began (ACT only)
    * @param actTotal    the act's length
    */
   public static int step(int state, double dist, float yawErr, boolean modelReady, long inState, long stuck, boolean overdue, boolean siteOk, long actAge,
      int actTotal) {
      return step(state, dist, yawErr, modelReady, inState, stuck, overdue, siteOk, actAge, actTotal, false);
   }

   /** As above; {@code pathDone}: the walk's path has ended (reached or blocked short of the spot). */
   public static int step(int state, double dist, float yawErr, boolean modelReady, long inState, long stuck, boolean overdue, boolean siteOk, long actAge,
      int actTotal, boolean pathDone) {
      if (!siteOk) {
         return ABORT;
      }
      switch (state) {
         case WALK:
            if (overdue || stuck > STUCK) {
               return ABORT;
            }
            return dist < SETTLE_AT || pathDone && inState > 10L && dist < SETTLE_NEAR ? SETTLE : WALK;
         case SETTLE:
            boolean placed = dist < 0.02 && Math.abs(yawErr) < 2.0F;
            return placed && (modelReady || inState > 50L) || inState > 120L ? ACT : SETTLE;
         case ACT:
            return actAge >= actTotal ? DONE : ACT;
         default:
            return state;
      }
   }
}
