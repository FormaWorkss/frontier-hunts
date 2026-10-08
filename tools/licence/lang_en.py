#!/usr/bin/env python3
"""[licence] English lang fragment: patch/_merge/assets/frontierhunts/lang/en_us.json/licence.json"""
import json, os, sys

R = sys.argv[1] if len(sys.argv) > 1 else '.'
L = {}
I = 'item.frontierhunts.'
L[I + 'hunting_licence'] = 'Hunting Licence'
L[I + 'hunting_licence.tip'] = 'Carry it while you hunt. Big game also needs a tag, game birds a stamp.'
for k, n in (('deer', 'Deer'), ('elk', 'Elk'), ('moose', 'Moose'), ('pronghorn', 'Pronghorn'), ('bison', 'Bison'), ('bear', 'Bear'),
             ('cat', 'Big Cat')):
    L[I + k + '_tag'] = n + ' Tag'
L[I + 'upland_stamp'] = 'Upland Bird Stamp'
L[I + 'waterfowl_stamp'] = 'Waterfowl Stamp'
L[I + 'filled_tag'] = 'Filled Tag'
L[I + 'filled_tag.named'] = 'Filled Tag: %s'
L[I + 'filled_tag.blank'] = 'A notched tag with nothing written on it.'
L[I + 'filled_tag.date'] = 'Taken %s'
L[I + 'filled_tag.place'] = 'At %s'
L[I + 'filled_tag.hunter'] = '%s · %s'
L[I + 'permit.blank'] = 'Blank form - not issued, not valid'
L[I + 'permit.where'] = 'Issued at an Expedition Board, Ranger Contract Board, Ranger Window or Lodge Stores (journal: Licence & Tags).'
L[I + 'permit.issued'] = 'Issued to %s · %s'
L[I + 'permit.valid'] = 'Valid · %s days left this season'
L[I + 'permit.expired'] = 'Expired'
L[I + 'permit.future'] = 'Not valid yet'
L[I + 'permit.covers'] = 'Covers: %s'
L[I + 'permit.open'] = 'Open: %s'
L[I + 'permit.tag_tip'] = 'One animal. Attached automatically when you claim it. Up to %s per hunter per season.'
L[I + 'permit.stamp_tip'] = 'Daily bag: %s birds of each species.'
L[I + 'permit.off'] = 'Hunting regulations are off on this server.'

# camp cooking
L['block.frontierhunts.dutch_oven'] = 'Camp Dutch Oven'
L['container.frontierhunts.dutch_oven'] = 'Camp Dutch Oven'
DISH = {'venison_stew': 'Venison Stew', 'venison_chili': 'Venison Chili', 'bear_pot_roast': 'Pot-Roasted Bear',
        'backstrap_mushrooms': 'Backstrap & Mushrooms', 'fowl_berry_roast': 'Wild Fowl with Berry Sauce',
        'fowl_wild_rice': 'Wild Fowl & Grain Pilaf', 'heart_liver_fry': 'Heart & Liver Fry', 'hunters_breakfast': "Hunter's Breakfast",
        'fish_chowder': 'Smoked Fish Chowder', 'camp_bannock': 'Camp Bannock', 'honey_pemmican': 'Honey Pemmican',
        'smoked_game_sausage': 'Smoked Game Sausage', 'raw_game_sausage': 'Raw Game Sausage'}
for k, v in DISH.items():
    L[I + k] = v
EFF = {'warmth': ('Camp Warmth', 'A hot meal inside: you lose body heat 30% slower.'),
       'stamina': ('Trail Stamina', 'Fuel for the walk in: 4% faster on foot, 20% less hunger from exertion.'),
       'steady': ('Steady Aim', 'Calm and fed: scope and bow sway and bow strain 25% lower.'),
       'hearty': ('Hearty', 'Iron-rich: heals half a heart every 12 seconds.'),
       'keen': ('Keen Tracker', 'Sharp eyes: tracks and blood show from 30% further.'),
       'quiet': ('Quiet Step', 'Light and loose: deer hear you 20% closer, wildlife alarm 10% smaller.'),
       'masked': ('Smoke-Masked', 'You smell of camp smoke, not of hunter: scent 20% weaker.')}
for k, (n, d) in EFF.items():
    L['effect.frontierhunts.meal_' + k] = n
    L['campcook.frontierhunts.buff.' + k] = d
C = 'campcook.frontierhunts.'
L[C + 'tip.buff'] = 'Camp meal: %s (%s)'
L[C + 'tip.one'] = 'One camp-meal buff at a time; eating again only refreshes it.'
L[C + 'screen.dish'] = '> %s x%s'
L[C + 'screen.dish_bowls'] = '> %s x%s (%s bowls)'
L[C + 'screen.need_bowls'] = 'Needs %s bowls in the bowl slot'
L[C + 'screen.need_heat'] = 'Needs heat: campfire below or coals'
L[C + 'screen.no_dish'] = 'No camp dish matches these'
L[C + 'screen.empty'] = 'Add meat and vegetables'
L[C + 'screen.heat_fire'] = 'Heat: fire under the pot'
L[C + 'screen.heat_coals'] = 'Heat: coals under and on the lid'
L[C + 'screen.heat_none'] = 'No heat: set it on a campfire or add coals'
L[C + 'screen.progress'] = '%s%% cooked'

# licence messages
M = 'licence.frontierhunts.'
L[M + 'warden'] = '[Game Warden]'
V = {'suspended': 'hunting a %s while your licence is suspended', 'no_licence': 'taking a %s without a valid Hunting Licence',
     'closed': 'taking a %s out of season', 'no_tag': 'taking a %s without a valid tag', 'no_stamp': 'taking a %s without a valid stamp',
     'over_limit': 'taking a %s over the daily bag limit'}
for k, v in V.items():
    L[M + 'violation.' + k] = v
L[M + 'msg.warning'] = 'Warning for %s. Carry your licence and tags (journal: Licence & Tags). Next time it is a fine.'
L[M + 'msg.fine'] = 'Fined %2$s tokens for %1$s.'
L[M + 'msg.strict'] = 'Fined %2$s tokens for %1$s. Strike %3$s of 3: half the meat and the trophy are seized.'
L[M + 'msg.strict_suspended'] = 'Fined %2$s tokens for %1$s. Strike %3$s: your licence is suspended for the rest of the season. Evidence seized.'
L[M + 'msg.unpaid'] = '%s tokens of the fine could not be paid.'
L[M + 'msg.filled'] = '%s filled: %s, %s'
L[M + 'msg.bird'] = '%s in the bag · %s of %s left today'
L[M + 'msg.bought'] = '%s issued for the %s'
L[M + 'msg.replaced'] = 'Replacement %s issued for the %s'
L[M + 'msg.education'] = 'Hunter education complete: the Ranger Academy issued you a free Hunting Licence for this season.'
W = {'off': 'Hunting regulations are off on this server.', 'spectator': 'Not in spectator mode.',
     'vendor': 'Stand within 6 blocks of an Expedition Board, Ranger Contract Board, Ranger Window or Lodge Stores.',
     'suspended': 'Your licence is suspended for the rest of this season.', 'no_emeralds_here': 'This server takes reserve tokens only.',
     'have': 'You already carry it.', 'free': 'It is free for you - claim it.', 'not_free': 'Not free.', 'bad': 'Not sold here.',
     'licence_first': 'Get your Hunting Licence for this season first.', 'bag': 'Bag limit reached for this season.',
     'closed': 'No open season for it during this licence season.', 'tokens': 'Not enough reserve tokens.',
     'emeralds': 'Not enough emeralds.'}
for k, v in W.items():
    L[M + 'why.' + k] = v
L[M + 'expedition.off'] = 'Hunting regulations are off on this server: no licence or tags needed.'
L[M + 'expedition.relaxed'] = ('Relaxed regulations: carry a Hunting Licence (free after the Archery course), a tag for big game and a stamp for '
                               'game birds, and hunt in season. Buy them standing at an expedition board, on the Licence & Tags page of the '
                               "Hunter's Journal. A first violation is a warning, then small fines.")
L[M + 'expedition.strict'] = ('Strict regulations: carry a Hunting Licence, a tag for big game and a stamp for game birds, and hunt in season. '
                              "Buy them standing at an expedition board, on the Licence & Tags page of the Hunter's Journal. Violations are "
                              'fined, half the meat and the trophy are seized, and three strikes suspend the licence for the season.')
P = M + 'page.'
PG = {
    'title': 'Hunting regulations: %s',
    'mode.0': 'Off', 'mode.1': 'Relaxed', 'mode.2': 'Strict',
    'mode.0.desc': 'No licence, tags or seasons on this server. Tags you carry are not filled.',
    'mode.1.desc': 'Carry a licence; big game needs a tag, birds a stamp; hunt in season. A warning first, then small fines.',
    'mode.2.desc': 'Every violation: a fine and a strike, half the meat and the trophy seized. Three strikes suspend the licence for the season.',
    'period': '%s · %s days left',
    'at_counter': 'You are at a licence counter.',
    'find_counter': 'Licences, tags and stamps are sold at an Expedition Board, a Ranger Contract Board, Ranger Window or Lodge Stores: stand within 6 blocks, then buy here.',
    'wallet': 'In your pack: %s tokens, %s emeralds',
    'licence': 'Hunting licence', 'suspended': 'SUSPENDED',
    'lic.carried': 'Valid this season · in your pack', 'lic.lost': 'Issued, but not in your pack · replace it free',
    'lic.none': 'No licence this season',
    'lic.edu': "Hunter education done (Ranger Academy Archery course): your licence is free every season.",
    'lic.what': "Needed for everything but coyotes, wolves and wild boar. Free after the Ranger Academy's Archery course.",
    'tags': 'Big-game tags', 'tags.right': 'one tag = one animal',
    'tag.status': '%s / %s bought · %s in pack',
    'open_now': 'open now', 'closed_now': 'closed now',
    'stamps': 'Bird stamps', 'stamp.today': '%s/%s %s today', 'stamp.carried': 'Valid · in your pack', 'stamp.none': 'No stamp',
    'seasons': 'Open this month', 'seasons.open': 'Open: %s', 'seasons.closed': 'Closed: %s',
    'seasons.varmints': 'Predators and varmints (coyotes, gray wolves, wild boar): open all year, no licence or tag needed.',
    'warden': 'Warden record',
    'standing.good': 'Good standing', 'standing.warned': 'Warned', 'standing.strikes': 'On notice', 'standing.suspended': 'Licence suspended',
    'warden.season': 'This season: %s legal, %s violations, %s strikes, %s tokens in fines.',
    'warden.total': 'All seasons: %s legal, %s violations, %s tokens in fines.',
    'warden.rules.relaxed': 'Relaxed: the first violation of a season is a warning, after that a small fine. You keep the animal, but it never counts for a species hunt.',
    'warden.rules.strict': 'Strict: every violation is a fine and a strike; half the meat and the trophy are seized; three strikes suspend your licence for the season.',
    'log': 'Filled tags & harvest record', 'log.empty': 'Nothing yet. Tagged animals, birds and any violations appear here.',
    'log.points': '%s pts', 'log.tagged': 'Tagged', 'log.stamp': 'Stamped', 'log.violation': 'Violation',
    'btn.replace': 'Replace', 'btn.claim': 'Claim free', 'btn.tokens': '%s tokens', 'btn.emeralds': 'Emerald x%s',
}
for k, v in PG.items():
    L[P + k] = v
L['journal.frontierhunts.page.licence'] = 'Licence & Tags'
CK = {
    'licence_get': ('A licensed hunter', 'Get a Hunting Licence: free after the Archery course, or buy one at an expedition board (journal: Licence & Tags).'),
    'licence_tag_1': ('Tag your animal', 'Take a big-game animal with its tag in your pack: the tag is filled for you.'),
    'licence_tag_5': ('Five filled tags', 'Fill five big-game tags.'),
    'licence_legal_25': ('By the book', '25 legal harvests: tagged big game or stamped birds, in season, within the bag.'),
    'campcook_first': ('Camp cook', 'Cook a dish in a Camp Dutch Oven and take it out.'),
    'campcook_kinds_5': ('Camp cook: five dishes', 'Cook five different Dutch oven dishes.'),
    'campcook_all': ('Camp chef', 'Cook all ten Dutch oven dishes.'),
    'campcook_fed': ('Fed for the field', 'Take an animal while a camp-meal buff is active.'),
}
for k, (t, h) in CK.items():
    L['journal.frontierhunts.check.' + k] = t
    L['journal.frontierhunts.check.' + k + '.hint'] = h
O = 'onboard.frontierhunts.task.'
L[O + 'licence.title'] = 'Get your hunting licence'
L[O + 'licence.how'] = ('Pass the Archery course and the Ranger Academy issues a free Hunting Licence, or buy one at an expedition board '
                        "(Hunter's Journal: Licence & Tags). Big game also needs a tag, game birds a stamp.")
L[O + 'licence.card'] = 'Get a Hunting Licence (free after the Archery course).'
L[O + 'camp_cook.title'] = 'Cook a camp meal'
L[O + 'camp_cook.how'] = ('Craft a Camp Dutch Oven, set it on a campfire (or feed it coals), add meat and vegetables - bowls for stews - and '
                          'take out the dish. Camp meals give a short buff.')
L[O + 'camp_cook.card'] = 'Cook a camp meal in a Dutch oven.'
L['onboard.frontierhunts.station.dutch_oven'] = 'Camp Dutch Oven, over a campfire or on coals'
L['expedition.frontierhunts.ui.seasons.licence'] = 'Licences & tags'
L['expedition.frontierhunts.ui.btn.licence'] = 'Licence & Tags page'
SUB = 'subtitles.frontierhunts.'
L[SUB + 'licence_stamp'] = 'Licence stamped'
L[SUB + 'tag_punch'] = 'Tag notched'
L[SUB + 'warden_notice'] = 'Game warden notice'
L[SUB + 'pot_simmer'] = 'Dutch oven simmers'
L[SUB + 'pot_lid'] = 'Pot lid clanks'

out = os.path.join(R, 'patch/_merge/assets/frontierhunts/lang/en_us.json/licence.json')
os.makedirs(os.path.dirname(out), exist_ok=True)
json.dump(L, open(out, 'w'), indent=1, ensure_ascii=False)
print(len(L), 'keys')
