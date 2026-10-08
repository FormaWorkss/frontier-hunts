## Runs better than you think

Frontier Hunts draws round trees with real bark, 3D crowns and sculpted animals. We know what that
usually costs, so the mod works hard to spend your frames only where you are looking.

**Distant trees become flat cutouts.** Every realistic tree has three levels of detail:

| Level | Where | Quads per tree (measured, 56 test trees) |
|---|---|---|
| Full | close to you | ~6,300 on average (big conifers ~10,000) |
| Reduced 3D | mid-range | ~1,700 (28% of full) |
| Cutout | far away | **16 on average, 24 at most** |

A cutout tree is a few crossed cards fitted to that tree's real crown. They use its own leaf sprite,
and conifers taper to a point. It has **about 1/100th of the geometry of the reduced tree and 1/400th
of the full one**. For example, a forest chunk with five tall pines drops from about 13,800 tree quads
to about 80. The ground dressing under distant canopies (leaf litter, moss and needles, about one quad
per shaded block) is skipped out there too. Cutout trees are also quicker to build when chunks load:
in our test harness they took about half the time of a reduced 3D tree.

**No special renderer.** Cutout trees are ordinary chunk geometry in Minecraft's normal solid and
cutout layers. Wood is attached to logs and foliage to leaves, so **Sodium, Iris and any shader pack
handle them like the rest of the tree**, and shader wind still moves the leaves. Trees change level
with hysteresis, so they don't flicker at the boundary. Those updates are also capped per tick, so
riding fast never floods the chunk builder.

**The Ultra preset sets the distances, and you can lower them.** On a weaker PC, turn down *Distant
trees* under *Tune Ultra for your PC* in the settings and the preset stays Ultra. (The Vanilla preset
draws plain Minecraft trees, so these distances don't apply to it.)

| Distant trees | Full detail within | Cutouts beyond |
|---|---|---|
| Performance | 24 blocks | 64 blocks |
| Standard | 32 blocks | 96 blocks |
| Ultra (Ultra preset) | 48 blocks | 160 blocks |
| Maximum | 64 blocks | never |

**Animals far away think less.** Calm deer, elk, moose and wildlife more than 48 blocks from every
player choose new goals every 4 ticks instead of every 2. Beyond 128 blocks they do it every 8 ticks.
They also skip senses scans that couldn't reach any player. An animal that is hurt, bleeding, downed,
alarmed or fleeing always runs at full rate, and so does any animal a player comes near. Blood trails,
tracks and rut sign are never thinned. Movement and navigation still run every tick, so no animal
ever stutters. Server owners can switch this off in the config.

**Animals far away animate less.** An animal that is close to you animates every frame, like before.
One that is small on your screen updates its pose at most 60, 30 or 20 times a second, and its body
still glides smoothly between updates. Scopes and binoculars count as close. Realistic wildlife also
has a per-frame budget: only the few animals biggest on your screen get the full sculpted mesh, and
far animals reuse their skinned mesh between pose updates.

Want to check it yourself? Press F3: Frontier Hunts lists how many trees are full, reduced or
cutouts, and how many animals were animated this frame.
