"""[onboard2] Seat names and messages (merged into onboard2.json by tools/onboard2/lang_en.py)."""


def add(s):
    B = 'block.frontierhunts.'
    s(B + 'log_stump_seat', 'Stump Seat')
    s(B + 'camp_chair', 'Camp Chair')
    s(B + 'trail_bench', 'Trail Bench')
    s(B + 'blind_chair', 'Blind Swivel Chair')
    s(B + 'tower_chair', 'Tower Swivel Chair')
    s('entity.frontierhunts.seat', 'Seat')
    S = 'seat.frontierhunts.'
    s(S + 'taken', 'Someone is already sitting here.')
    s(S + 'raised', 'Seat raised: eye level with the windows of the big tower blind')
    s(S + 'lowered', 'Seat lowered: eye level with the windows of the small tower blind')
    s(S + 'tip.sit', 'Right-click to sit · Sneak to stand up')
    s(S + 'tip.log_stump_seat', 'A sawn stump with a hide pad. Turn any way you like.')
    s(S + 'tip.camp_chair', 'Folding canvas chair for camp and the fire.')
    s(S + 'tip.trail_bench', 'Benches placed side by side join into one long bench.')
    s(S + 'tip.blind_chair', 'Swivels with you. Seated, your eyes are level with a ground blind\'s windows.')
    s(S + 'tip.tower_chair', 'Swivels with you and fits itself to tower blinds: eye level with the windows. Sneak + use to raise or lower it.')
