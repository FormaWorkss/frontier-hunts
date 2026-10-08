package com.formaworks.frontierhunts.seating;

/**
 * [onboard2] The seats and how a hunter sits on each. Heights are in model pixels above the seat block's bottom and
 * come from tools/onboard2/seat_geom.py (keep in sync): the seat entity sits {@code eye - 1.02} blocks above the floor,
 * because a riding player's feet hang 0.6 below the seat point and the camera stays at the 1.62 standing eye height.
 *
 * <ul>
 *   <li>eye 1.50 above the floor (ground blind windows: 1.20-1.80 above the ground) for the outdoor seats and the
 *       blind swivel chair,</li>
 *   <li>eye 1.535 (small tower blind windows: 1.205-1.865 above its floor) for the tower swivel chair, 1.70 when it is
 *       raised (big tower blind windows: 1.335-2.07); in the small tower its floor is 0.4 below the cabin cells, so
 *       the chair is "sunk" 6.4 px.</li>
 * </ul>
 * {@code swivel}: the body follows the head (vanilla 50 deg lag) and, for chairs with a back, the seat top turns
 * with the body. Otherwise the body stays square to the seat and the head turns up to {@link #HEAD_LIMIT}.
 */
public enum SeatKind {
   LOG_STUMP("log_stump_seat", true, false, 0.0, SeatKind.Y_GROUND),
   CAMP_CHAIR("camp_chair", false, false, 1.5, SeatKind.Y_GROUND),
   TRAIL_BENCH("trail_bench", false, false, 1.0, SeatKind.Y_GROUND),
   BLIND_CHAIR("blind_chair", true, true, 0.0, SeatKind.Y_GROUND),
   TOWER_CHAIR("tower_chair", true, true, 0.0, SeatKind.Y_TOWER);

   /** px above the block bottom: eye 1.50 / 1.535 / 1.70 blocks above the floor. */
   public static final double Y_GROUND = 1.50 * 16.0 - 16.32;
   public static final double Y_TOWER = 1.535 * 16.0 - 16.32;
   public static final double TOWER_RAISE = (1.70 - 1.535) * 16.0;
   /** The small tower blind's floor is 0.4 below its cabin cells. */
   public static final double SUNK = -6.4;
   /** Head turn either way on a seat with a back (the boat's limit). */
   public static final float HEAD_LIMIT = 105.0F;

   private static final SeatKind[] VALUES = values();

   public final String id;
   public final boolean swivel;
   public final boolean rotatingTop;
   /** How far the hip sits behind the block centre (px), toward the back of the seat. */
   public final double hipPx;
   public final double seatPx;

   SeatKind(String id, boolean swivel, boolean rotatingTop, double hipPx, double seatPx) {
      this.id = id;
      this.swivel = swivel;
      this.rotatingTop = rotatingTop;
      this.hipPx = hipPx;
      this.seatPx = seatPx;
   }

   public static SeatKind byId(int i) {
      return i >= 0 && i < VALUES.length ? VALUES[i] : LOG_STUMP;
   }

   /** Seat point height above the block bottom, in blocks, for this state (tower chair: raised / sunk). */
   public double seatY(boolean raised, boolean sunk) {
      double px = this.seatPx + (raised ? TOWER_RAISE : 0.0) + (sunk ? SUNK : 0.0);
      return px / 16.0;
   }
}
