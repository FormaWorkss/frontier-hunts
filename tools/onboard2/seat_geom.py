"""[onboard2] Shared seat geometry: the numbers the Java side (seating/SeatKind) uses, the seated player pose
(vanilla riding pose, PlayerRenderer scale 0.9375, vehicle attachment 0.6, eye 1.62) and the thigh line that every
seat top is shaped to. Units: model px (16 per block), block-local, models face NORTH (the sitter looks to -z)."""
import math

PX = 16.0
ATTACH = 0.6 * PX          # Player DEFAULT_VEHICLE_ATTACHMENT
EYE = 1.62 * PX            # Player eye height (STANDING pose is kept while riding)
SCALE = 0.9375             # PlayerRenderer scale
LEG_PIVOT = 12 * SCALE     # hip pivot above the feet (model y 12 of 24)
# thigh underside right at the hip: leg box is 4 px thick, xRot = -1.4137167 (the riding pose)
_T = -1.4137167
# lowest point of the thigh at the hip: 2 px half-thickness (rotated), the 0.25 px trouser layer and the leg's 0.0785 rad
# outward roll (zRot) that drops its outer edge by 2.25 * sin(0.0785)
HIP_UNDER = LEG_PIVOT - (2 * abs(math.sin(_T)) + 0.25 + 2.25 * math.sin(0.07853982)) * SCALE  # above the feet, px
SLOPE = (12 * math.cos(_T)) / (12 * abs(math.sin(_T)))          # px drop per px forward (the 9.3 deg leg line)

# Seat entity heights above the block bottom (px) and the eye level they give (blocks above the floor).
#   Window centres measured from the blind shapes (tools/onboard2/blind_windows.py):
#   ground blind 1.20-1.80 -> 1.50 · small tower blind 1.205-1.865 above its floor (3.60) -> 1.535
#   big tower blind 1.335-2.07 above its floor (4.00) -> 1.70
EYE_GROUND = 1.50
EYE_TOWER = 1.535
EYE_TOWER_HIGH = 1.70
SUNK = -6.4                 # small tower blind: its floor is 0.4 below the cabin cells


def seat_y(eye_blocks, floor_px=0.0):
    """Seat entity y (px above the block bottom) for an eye level (blocks above the floor)."""
    return floor_px + eye_blocks * PX - (EYE - ATTACH)


def hip_under(seat_y_px):
    return seat_y_px - ATTACH + HIP_UNDER


def thigh_line(seat_y_px, d):
    """Height (px) of the thigh underside d px in front of the hip pivot."""
    return hip_under(seat_y_px) - SLOPE * max(0.0, d)


# Every seat: kind -> (seat y px, hip offset toward the back px, swivel, eye blocks above its floor)
SEATS = {
    'log_stump_seat': dict(y=seat_y(EYE_GROUND), hip=0.0, swivel=True),
    'camp_chair': dict(y=seat_y(EYE_GROUND), hip=1.5, swivel=False),
    'trail_bench': dict(y=seat_y(EYE_GROUND), hip=1.0, swivel=False),
    'blind_chair': dict(y=seat_y(EYE_GROUND), hip=0.0, swivel=True),
    'tower_chair': dict(y=seat_y(EYE_TOWER), hip=0.0, swivel=True),
}
TOWER_RAISE = seat_y(EYE_TOWER_HIGH) - seat_y(EYE_TOWER)   # px the raised tower chair is taller
