#!/usr/bin/env python3
"""[onboard2] English lang fragment: the Handbook's Assignments tab and the seats.
python3 tools/onboard2/lang_en.py <repo>  ->  patch/_merge/assets/frontierhunts/lang/en_us.json/onboard2.json"""
import json, os, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
L = {}


def s(k, v):
    L[k] = v


# ------------------------------------------------------------------------------------------ Handbook: Assignments tab
O = 'onboard.frontierhunts.'
s(O + 'tab.assignments', 'Assignments')
s(O + 'asg.eyebrow', 'RANGER ASSIGNMENTS')
s(O + 'asg.title', 'Ranger Assignments')
s(O + 'asg.p1', 'Ranger Assignments are your training and your work. The Ranger Academy has six courses, each on its own private range: the gear is lent and you get all your own things back when you leave. Every course you pass also ticks lessons in this book. Take them in order, one at a time.')
s(O + 'asg.card.eyebrow', 'RANGER ACADEMY · %s OF %s PASSED')
s(O + 'asg.card.next', 'Next course: %s')
s(O + 'asg.card.training', 'In training: %s')
s(O + 'asg.card.done', 'Every course passed')
s(O + 'asg.card.done_body', 'Well done, ranger. Field work, bounties and supply runs on the Assignments board pay tokens and Ranger XP.')
s(O + 'asg.btn.open', 'Open Ranger Assignments (%s)')
s(O + 'asg.courses', 'ACADEMY COURSES')
s(O + 'asg.pill.passed', 'PASSED')
s(O + 'asg.pill.next', 'NEXT')
s(O + 'asg.pill.training', 'TRAINING')
s(O + 'asg.field', 'FIELD WORK')
s(O + 'asg.field.none', 'No field assignment taken yet. The Assignments board offers field patrols, blood trails, trophy surveys and supply runs for tokens and Ranger XP.')
s(O + 'asg.field.active', 'Taken: %s. Open Ranger Assignments to see how far along it is.')
s(O + 'asg.field.ready', 'Done: %s. Open Ranger Assignments and collect the reward.')
s(O + 'asg.field.failed', 'Failed: %s. Open Ranger Assignments to try again or drop it.')
s(O + 'asg.footer', 'Press %s any time to open Ranger Assignments.')

out = os.path.join(ROOT, 'patch/_merge/assets/frontierhunts/lang/en_us.json/onboard2.json')
if os.path.exists(os.path.join(ROOT, 'tools/onboard2/lang_seats.py')):
    sys.path.insert(0, os.path.join(ROOT, 'tools/onboard2'))
    import lang_seats
    lang_seats.add(s)
with open(out, 'w', encoding='utf-8') as f:
    json.dump(dict(sorted(L.items())), f, ensure_ascii=False, indent=1)
    f.write('\n')
print(len(L), 'keys ->', out)
