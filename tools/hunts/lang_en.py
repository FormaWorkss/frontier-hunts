#!/usr/bin/env python3
"""[hunts] English text of the species hunts -> patch/_merge/assets/frontierhunts/lang/en_us.json/hunts.json

python3 tools/hunts/lang_en.py <repo>

Keep it simple and clear: short sentences, plain words, numbers players can check in game.
"""
import json
import os
import sys

L = {}

# ------------------------------------------------------------------------------------------------ species
SPECIES = {
    'whitetail': dict(
        tagline='Woods and field edges. The rut is everything.',
        brief='Whitetails bed in thick cover and feed on field edges at first and last light. Scout with trail cameras, then '
              'wait in a stand or blind downwind of a trail. In the rut (October to mid-December) bucks answer grunts, bleats '
              'and rattling.',
        where='Forests, plains, meadows and river bottoms almost everywhere.',
        title='Rut Hunter',
        m=dict(
            cam=('Whitetail on camera', 'Hang a trail camera on a trail, field edge or scrape. Any whitetail it photographs counts.'),
            clean=('A clean whitetail', 'Take a whitetail with a heart or lung shot, then field-dress it.'),
            called=('Called in', 'Grunt, bleat or rattle a deer in, then take it within 5 minutes. Calls work best in the rut.'),
            mature=('A mature buck', 'Take a buck with 8 points or more.'),
            master=('Rut hunter', 'During the rut, take a buck with 8 points or more from a tree stand or blind, with a heart or lung shot.'),
        )),
    'elk': dict(
        tagline='High parks and dark timber. Listen for the bugle.',
        brief='Elk live in herds in mountain meadows and timber. Hunters glass open parks at dawn and dusk from far off, '
              'then close in on foot. In the rut (late August to mid-October) bulls answer a cow call or a bull grunt and come looking.',
        where='Taiga, meadows, groves and mountain valleys.',
        title='Elk Caller',
        m=dict(
            glass=('Glass the high parks', 'Hold binoculars or a rangefinder steady on an elk 100 m or more away.'),
            clean=('A clean elk', 'Take an elk with a heart or lung shot, then field-dress it.'),
            called=('Called in', 'Call an elk in with the Doe Bleat Call (a cow call) or the Grunt Tube, then take it within 5 minutes.'),
            herd_bull=('A herd bull', 'Take a bull with 5 points a side or better that weighs 270 kg or more.'),
            master=('Bugle season', 'During the rut, take a bull with 5 points a side or better that came to your call, with a heart or lung shot.'),
        )),
    'moose': dict(
        tagline='Willow bogs and lakeshores. Patience and the cow call.',
        brief='Moose feed in willows, bogs and shallow water, often alone. Hunters move quietly along lakes and creeks, or sit '
              'and call. In the rut (September and October) bulls answer a cow call or a bull grunt, sometimes from far away.',
        where='Taiga, swamps, bogs and river bottoms in the north.',
        title='Moose Caller',
        m=dict(
            seen=('Close encounter', 'See a moose clearly from 40 m or closer.'),
            clean=('A clean moose', 'Take a moose with a heart or lung shot, then field-dress it.'),
            called=('Called in', 'Call a moose in with the Doe Bleat Call (a cow call) or the Grunt Tube, then take it within 5 minutes.'),
            big_bull=('A big bull', 'Take a bull with 7 points a side or more.'),
            master=('Rut bull by the water', 'During the rut, call in a bull near water and take him with a heart or lung shot.'),
        )),
    'pronghorn': dict(
        tagline='Open plains. They see you first.',
        brief='Pronghorn live on open grass and sage and have eyes like strong binoculars. Hunters glass from far away and '
              'plan a stalk using every dip in the ground, or sit in a blind near water. Shots are often long.',
        where='Plains, savanna and sagebrush flats.',
        title='Plains Stalker',
        m=dict(
            glass=('Glass a pronghorn', 'Hold binoculars or a rangefinder steady on a pronghorn 100 m or more away.'),
            clean=('A clean pronghorn', 'Take a pronghorn with a heart or lung shot.'),
            blind=('From the blind', 'Take a pronghorn while you sit in a ground blind or tower blind.'),
            heavy=('A heavy buck', 'Take a pronghorn that weighs 54 kg or more.'),
            master=('Open-country stalk', 'Glass a pronghorn, stalk it on foot (no stand or blind) and take it from 100 m or more with a heart or lung shot.'),
        )),
    'bison': dict(
        tagline='Grass country. The herd is watching.',
        brief='Bison graze in herds on open grass. A fair-chase hunt means walking in on foot, using the wind and the land to '
              'get close without moving the herd, then one well-placed shot behind the shoulder. A hurt bison may charge.',
        where='Plains, sunflower plains, sagebrush and aspen parkland.',
        title='Plainsman',
        m=dict(
            glass=('Watch the herd', 'Glass a bison with at least two more bison near it.'),
            clean=('A clean bison', 'Take a bison with a heart or lung shot.'),
            fair_chase=('Fair chase', 'On foot (no stand, blind or vehicle), take a bison from a herd of three or more before it notices you.'),
            bull=('A big bull', 'Take a bison that weighs 730 kg or more.'),
            master=('One shot', 'On foot, take an unaware bison from a herd of three or more with one clean shot.'),
        )),
    'boar': dict(
        tagline='Creek bottoms after dark.',
        brief='Wild hogs root in damp woods and creek bottoms and move mostly at night. Hunters run trail cameras, hunt at '
              'dusk and after dark with a light or night optics, and use dogs to trail and bay them.',
        where='Forests, maple woods, alder swamps and cottonwood bottoms.',
        title='Hog Hunter',
        m=dict(
            cam=('Hogs on camera', 'Get a wild boar on a trail camera.'),
            clean=('A clean hog', 'Take a wild boar with a heart or lung shot.'),
            night=('Night hunt', 'Take a wild boar between dusk and dawn.'),
            big=('A big boar', 'Take a wild boar that weighs 92 kg or more.'),
            master=('Dogs at night', 'Between dusk and dawn, take a wild boar your tracking hound trailed or bayed.'),
        )),
    'black_bear': dict(
        tagline='Thick woods. Bait, wait, be sure.',
        brief='Black bears roam thick forest and are shy. Many hunters set a bait pile and watch it from a stand at dusk; '
              'others glass slopes and stalk. Take your time: judge the bear and wait for a broadside shot.',
        where='Forests, maple and birch woods, mossy groves.',
        title='Bear Hunter',
        m=dict(
            cam=('Bear on camera', 'Get a black bear on a trail camera. A camera on a bait pile works well.'),
            clean=('A clean bear', 'Take a black bear with a heart or lung shot.'),
            bait=('Over bait', 'Use Bait on the ground to set a bait pile, hunt it from cover, and take a black bear that came to it.'),
            big=('A big bear', 'Take a black bear that weighs 130 kg or more.'),
            master=("Bowhunter's bear", 'Take a black bear over your bait with a bow and a heart or lung shot.'),
        )),
    'grizzly': dict(
        tagline='Avalanche slopes and rivers. Keep your distance.',
        brief='Grizzlies range over high meadows, slides and rivers. Hunters glass from far away, stalk with the wind in '
              'their face and shoot only when sure. A hurt grizzly may charge - be ready.',
        where='Taiga, meadows and highland forests.',
        title='Mountain Hunter',
        m=dict(
            glass=('Glass a grizzly', 'Hold binoculars or a rangefinder steady on a grizzly 80 m or more away.'),
            clean=('A clean grizzly', 'Take a grizzly with a heart or lung shot.'),
            stalk=('Spot and stalk', 'Glass a grizzly, then close in and take it before it notices you.'),
            big=('A big boar', 'Take a grizzly that weighs 290 kg or more.'),
            master=('Stand your ground', 'Stop a charging grizzly within 20 m with a heart or lung shot.'),
        )),
    'polar_bear': dict(
        tagline='Ice and snow. White on white.',
        brief='Polar bears hunt along icy coasts and snowfields. There is nowhere to hide, so hunters glass from far off and '
              'close in slowly in white clothing, using drifts and ridges.',
        where='Snowy plains, ice spikes, frozen coasts and peaks.',
        title='Ice Walker',
        m=dict(
            glass=('Glass a polar bear', 'Hold binoculars or a rangefinder steady on a polar bear 80 m or more away.'),
            clean=('A clean polar bear', 'Take a polar bear with a heart or lung shot.'),
            camo=('White on white', 'Take a polar bear while you wear snow ghillie or snow camo clothing.'),
            big=('A big bear', 'Take a polar bear that weighs 470 kg or more.'),
            master=('Arctic stalk', 'Glass a polar bear, close in wearing snow camo and take it unaware with a heart or lung shot.'),
        )),
    'coyote': dict(
        tagline='Open country. Call them in.',
        brief='Coyotes live wherever there is open country. Sit hidden on a calling stand, blow a predator call that sounds '
              'like a hurt rabbit and watch downwind - they circle to smell the call before they come in. Dawn, dusk and '
              'night are best.',
        where='Plains, badlands, savanna and meadows.',
        title='Song Dog Caller',
        m=dict(
            seen=('Song dog', 'See a coyote clearly from 40 m or closer.'),
            clean=('A clean coyote', 'Take a coyote with a heart or lung shot.'),
            called=('Called in', 'Blow the Predator Locator Call and take a coyote that comes to it.'),
            prime=('Prime winter pelt', 'Take a coyote in December, January or February, when the fur is thickest.'),
            master=('Night stand', 'Between dusk and dawn, take 3 coyotes that came to your predator call.'),
        )),
    'wolf': dict(
        tagline='Northern forests. Big country, long shots.',
        brief='Wolves travel in packs over huge areas of northern forest. Hunters glass open ridges and frozen meadows, use '
              'a predator call, and are ready for long shots in winter, when the fur is best.',
        where='Taiga, snowy forests, groves and highland pines.',
        title='Timber Wolf Hunter',
        m=dict(
            glass=('Glass a wolf', 'Hold binoculars or a rangefinder steady on a gray wolf 60 m or more away.'),
            clean=('A clean wolf', 'Take a gray wolf with a heart or lung shot.'),
            called=('Called in', 'Blow the Predator Locator Call and take a gray wolf that comes to it.'),
            prime=('Prime winter pelt', 'Take a gray wolf in December, January or February.'),
            master=('Winter wolf', 'In winter, glass a gray wolf and take it from 100 m or more with a heart or lung shot.'),
        )),
    'cougar': dict(
        tagline='Rimrock and canyons. A ghost on camera.',
        brief='Cougars are secretive and mostly seen on trail cameras. The classic hunt follows fresh tracks in snow behind '
              'hounds. Here your tracking hound trails a cat by its blood or prints and bays it.',
        where='Mountains, windswept hills, foothills and canyons.',
        title='Houndsman',
        m=dict(
            cam=('Ghost on camera', 'Get a cougar on a trail camera.'),
            clean=('A clean cougar', 'Take a cougar with a heart or lung shot.'),
            hound=('Behind the hound', 'Take a cougar your tracking hound trailed or bayed. Put the hound on its blood or prints with the Hound Lead.'),
            tom=('A big tom', 'Take a cougar that weighs 65 kg or more.'),
            master=('Snow and hounds', 'Take a cougar standing on snow, with your hound on its trail.'),
        )),
    'panther': dict(
        tagline='Jungle nights. Wait in the dark.',
        brief='Panthers (black leopards) hunt the jungle at night. The traditional hunt is a bait near a hide or stand, '
              'watched quietly at dusk and into the dark, and one careful shot.',
        where='Jungles.',
        title='Night Watcher',
        m=dict(
            cam=('Panther on camera', 'Get a panther on a trail camera.'),
            clean=('A clean panther', 'Take a panther with a heart or lung shot.'),
            hide=('Wait in the hide', 'Between dusk and dawn, take a panther from a tree stand or blind.'),
            big=('A big male', 'Take a panther that weighs 58 kg or more.'),
            master=('Over the bait', 'Between dusk and dawn, from a stand or blind, take a panther at your bait with a heart or lung shot.'),
        )),
    'lion': dict(
        tagline='Savanna. Dangerous game.',
        brief='Lions live in prides on open savanna. Hunters glass from far off, track and stalk carefully, and must be '
              'ready: a hurt lion charges fast and low.',
        where='Savannas.',
        title='Savanna Hunter',
        m=dict(
            glass=('Glass a lion', 'Hold binoculars or a rangefinder steady on a lion 80 m or more away.'),
            clean=('A clean lion', 'Take a lion with a heart or lung shot.'),
            stalk=('Spot and stalk', 'Glass a lion, then close in and take it before it notices you.'),
            maned=('A maned male', 'Take a lion that weighs 190 kg or more.'),
            master=('Stop the charge', 'Stop a charging lion within 20 m with a heart or lung shot.'),
        )),
    'cheetah': dict(
        tagline='Savanna by day. The fastest hunter.',
        brief='Cheetahs hunt in daylight on open savanna with a short, explosive sprint. Most people only ever shoot them '
              'with a camera - glass them from far off and watch a hunt unfold.',
        where='Savannas.',
        title='Safari Tracker',
        m=dict(
            glass=('Glass a cheetah', 'In daylight, hold binoculars or a rangefinder steady on a cheetah 80 m or more away.'),
            cam=('Cheetah on camera', 'Get a cheetah on a trail camera.'),
            chase=('Watch a hunt', 'Glass a cheetah while it stalks, chases or feeds on a kill.'),
            clean=('A clean cheetah', 'Take a cheetah with a heart or lung shot.'),
            master=('Long shot', 'Take a cheetah of 50 kg or more from 100 m or more with a heart or lung shot.'),
        )),
    'grouse': dict(
        tagline='Aspen edges. Walk, flush, swing.',
        brief='Ruffed grouse hide in young forest and aspen edges and burst into the air at your feet. Upland hunters walk '
              'slowly, ready to mount a shotgun and swing through the bird as it flushes.',
        where='Forests, taiga, aspen woods and heather moors.',
        title='Wingshooter',
        m=dict(
            flush=('Flush a grouse', 'Walk the edges until a grouse bursts into the air near you.'),
            take=('Grouse for supper', 'Take a ruffed grouse.'),
            wing=('On the wing', 'Take a grouse in flight. It flushes when it notices you - be quick.'),
            limit=("A day's limit", 'Take 3 grouse in one day.'),
            master=('Wingshooter', 'Take 5 grouse on the wing.'),
        )),
    'duck': dict(
        tagline='Marshes at first light. Decoys and the call.',
        brief='Mallards feed and rest on marshes, rivers and lakes. Waterfowlers set decoys on the water before dawn, hide '
              'nearby, and use a duck call to turn passing ducks toward the spread. Shoot as they drop in or flare.',
        where='Rivers, swamps, marshes, bogs and lakeshores.',
        title='Marsh Master',
        m=dict(
            seen=('Ducks on the water', 'See a mallard clearly from 40 m or closer.'),
            take=('A duck for the pot', 'Take a mallard.'),
            spread=('Over the spread', 'Take a mallard within 24 blocks of your decoys, or one that came to your Duck Call.'),
            wing=('On the wing', 'Take a mallard in flight.'),
            master=('Limit over the spread', 'Take 4 mallards over your decoys or call in one day.'),
        )),
}

for sp, d in SPECIES.items():
    L['hunts.frontierhunts.%s.tagline' % sp] = d['tagline']
    L['hunts.frontierhunts.%s.brief' % sp] = d['brief']
    L['hunts.frontierhunts.%s.where' % sp] = d['where']
    L['hunts.frontierhunts.%s.title' % sp] = d['title']
    for key, (title, hint) in d['m'].items():
        L['journal.frontierhunts.check.hunt_%s_%s' % (sp, key)] = title
        L['journal.frontierhunts.check.hunt_%s_%s.hint' % (sp, key)] = hint

# ------------------------------------------------------------------------------------------------ journal UI
L.update({
    'journal.frontierhunts.cat.hunts': 'Hunts',
    'hunts.frontierhunts.tier.scout': 'Scout',
    'hunts.frontierhunts.tier.take': 'First take',
    'hunts.frontierhunts.tier.technique': 'Technique',
    'hunts.frontierhunts.tier.quality': 'Quality',
    'hunts.frontierhunts.tier.master': 'Master',
    'hunts.frontierhunts.reward.xp': '+%s XP',
    'hunts.frontierhunts.reward.tokens': '%s tokens',
    'hunts.frontierhunts.reward.title': 'Title: %s',
    'hunts.frontierhunts.or': 'or',
    'hunts.frontierhunts.year_round': 'all year',
    'hunts.frontierhunts.species.intro': 'Click a species to open its hunt: how it is hunted, where to find it and five milestones to work through.',
    'hunts.frontierhunts.species.tip_hunt': 'Hunt: %s of %s milestones · click to open',
    'hunts.frontierhunts.check.open_card': 'Click to open this hunt on the Species page.',
    'hunts.frontierhunts.sub.card': '%s hunt · %s of %s milestones',
    'hunts.frontierhunts.card.back': 'All species',
    'hunts.frontierhunts.card.progress': '%s of %s milestones',
    'hunts.frontierhunts.card.title_locked': 'Master title: %s',
    'hunts.frontierhunts.card.stats': 'Seen %s · on camera %s · taken %s · best %s',
    'hunts.frontierhunts.card.how': "How it's hunted",
    'hunts.frontierhunts.card.where': 'Where: %s',
    'hunts.frontierhunts.card.contract': 'Season contract: %s (%s) · %s tokens on the Expedition board',
    'hunts.frontierhunts.card.milestones': 'Milestones',
    'hunts.frontierhunts.card.needs': 'Needs: %s',
    'hunts.frontierhunts.card.in_a_day': 'in one day',
    'hunts.frontierhunts.card.footer': 'Deer, elk and moose count when you field-dress them; other animals when they fall. '
                                        'Calls, bait, glassing and hounds count for shots within 5-10 minutes. "On foot" means no vehicle, stand or blind.',
    # notes, chat, messages
    'hunts.frontierhunts.note.reward': '%s: +%s tokens and %s× %s',
    'hunts.frontierhunts.note.reward_tokens': '%s: +%s tokens',
    'hunts.frontierhunts.note.master': 'Mastered the %s hunt. Title earned: %s.',
    'hunts.frontierhunts.note.migrated': 'Hunts opened: %s milestones carried over from your earlier hunting.',
    'hunts.frontierhunts.chat.master': '%s mastered the %s hunt - "%s"',
    'hunts.frontierhunts.msg.delivered': '%s hunt reward(s) waiting for you were added to your pack.',
    'hunts.frontierhunts.msg.bait': 'Bait pile set · bears, hogs and big cats find it, mostly from dusk to dawn, for about two days. Watch it from cover downwind.',
    'hunts.frontierhunts.msg.decoys': 'Decoy spread · %s decoy(s) within 24 blocks',
    'hunts.frontierhunts.msg.duck_call': 'Duck call · %s decoy(s) in your spread · stay hidden and watch the sky',
    'hunts.frontierhunts.msg.duck_call_nodecoys': 'Duck call · no decoys out - ducks will look for open water near you',
    # commands
    'hunts.frontierhunts.cmd.status': 'Hunts: %s of %s milestones · %s of %s species mastered',
    'hunts.frontierhunts.cmd.unknown': 'Unknown hunt milestone',
    'hunts.frontierhunts.cmd.tested': 'Fed a field event for %s to %s hunter(s)',
    'hunts.frontierhunts.cmd.call': 'Blew a %s call here',
    'hunts.frontierhunts.cmd.reset': 'Reset the hunt milestones of %s hunter(s)',
    # contracts (expedition board)
    'hunts.frontierhunts.contract.board': '%s: species contracts posted now - %s. They change at mid-month and as the seasons turn.',
    'hunts.frontierhunts.contract.board_none': '%s: no species contracts posted now. They change at mid-month and as the seasons turn.',
    'hunts.frontierhunts.contract.how.take': 'Counts each %1$s you take (deer when field-dressed, others when they fall).',
    'hunts.frontierhunts.contract.how.standblind': 'Counts a %1$s you take while sitting in a tree stand or blind.',
    'hunts.frontierhunts.contract.how.called': 'Counts a %1$s that came to your call before you took it.',
    'hunts.frontierhunts.contract.how.clean': 'Counts a %1$s taken with a heart or lung shot.',
    'hunts.frontierhunts.contract.how.long100': 'Counts a %1$s shot from 100 m or more.',
    'hunts.frontierhunts.contract.how.onfoot': 'Counts a %1$s taken on foot - no vehicle, stand or blind.',
    'hunts.frontierhunts.contract.how.night': 'Counts a %1$s taken between dusk and dawn.',
    'hunts.frontierhunts.contract.how.photo': 'Counts a trail camera photo of a %1$s.',
    # assignments (ranger dossier)
    'hunts.frontierhunts.assign.how': 'Hunt anywhere in the Overworld in Survival, after you accept. The hunt must be yours: your call, your bait, your shot.',
    # items
    'item.frontierhunts.duck_call': 'Duck Call',
    'item.frontierhunts.duck_call.tip1': 'Use: a hail call heard about 96 blocks away.',
    'item.frontierhunts.duck_call.tip2': 'Mallards that hear it swing to your decoys, or to open water near you.',
    'block.frontierhunts.mallard_decoy': 'Mallard Decoy',
    'subtitles.frontierhunts.duck_call': 'Duck call quacks',
})


def main(repo):
    out = os.path.join(repo, 'patch/_merge/assets/frontierhunts/lang/en_us.json/hunts.json')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(L, f, indent=1, ensure_ascii=False)
        f.write('\n')
    print(out, len(L), 'keys')


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else '.')
