# Workstream `anims`: wildlife that walks instead of sliding (finished by the coordinator)

## Root cause of the sliding
The rig swung each leg as a sine of `walkAnimation.position` with a fixed small amplitude (max ~18 deg). A planted paw
moved back much slower than the ground passed under it (a wolf's paw covered ~1/3 of the body's travel per step), and
`walkAnimation` speed saturates at 0.25 blocks/tick, so above a trot the legs stopped speeding up: animals glided.

## Fix
`wildlife2026/client/WildlifeGait`: per animal, the gait phase advances by real ground distance / stride length
(stride from dynamic similarity, capped by leg reach); each paw follows a stance/swing cycle in the body frame (planted
during stance) via a two-joint IK on the mesh's own skeleton, layered on top of every other pose. Per-species styles:
canid trot, cat low walk, cheetah spine-flex gallop, bear rolling amble, bison heavy walk with head nod, pronghorn and
boar trot/bound. Contact/joint/tail ground guards stop paws and tails sinking.

Gait audit (`tools/anims/gait.sh`, 684 leg checks over 12 species x 3 LODs x 4 speeds): planted-paw slip ~0.00-0.04
for walking and trotting; 12 marginal checks at full gallop (slip 0.15-0.18, limit 0.15) on cheetah, grizzly, black bear.

## Coats
Coat pass (`tools/anims/catcoat.py`): cougar brighter tawny with dark muzzle marks, cheetah spots/tear lines refreshed,
bison/wolf/others fur detail and slightly lifted brightness so they don't read as silhouettes under Minecraft light.
Panther coat kept as before (the new one turned brown). Lion mesh: small mane cleanup.

## Not done
A full re-sculpt of cougar/lion detail was not finished; the in-game renders (tools/anims/ingame.py) show the
current meshes smooth at 3-10 blocks.

## In-game test
Ultra, spawn wolf, coyote, cougar, lion, cheetah, panther, bears, bison, boar, pronghorn; walk, chase, make them flee:
paws stay planted, legs speed up with speed, no gliding. Check Vanilla preset unchanged.
