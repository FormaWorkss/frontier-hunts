# Workstream `gunsmith`: quality pass on the workbench firearms, attachments and sights

Branch `gunsmith` (from master 1650414). Visual work only. Item ids, NBT and data components, recipes, attachment
compatibility, sockets, animations and pivots, ADS numbers, ScopeZoom, FieldScopeView, HoldSway and prone/bipod logic
are unchanged. The Ridgeline's supplied mesh and texture are unchanged too. Nothing in the mod references TACZ.

## Inventory (what is drawn and how)
- **Field guns** (`.fheq` meshes plus the `firearms_v2.png` material atlas, drawn by `FieldWeaponMesh`): lever rifle,
  "Assault Rifle" (semi_auto_rifle), pump shotgun, double barrel, semi-auto shotgun, revolver, tranquilizer rifle,
  flare gun and bait launcher.
- **Frontier 2011 pistol**: code-built in `CombatPistolMesh` on `pistol_materials.png`.
- **Ridgeline bolt rifle**: the supplied `camo_rifle_*.fhrm` via `CamoRifleModel`. Its swappable sights and iron
  sights come from `RidgelineSights`.
- **Optics**: `optic_*.fheq`, drawn by `FieldMountedOptic` and `FieldTacticalSight`/`FieldReflexSight`. The glass is
  code-built. The optics are the 6x, 8x, 12x, thermal, the 4-12x Field Optic (`optic_fixed_scope`), reflex, micro red
  dot, holographic and 2x prism.
- **Attachments**: `att_*.fheq` (suppressor, muzzle brake, bipod, cheek riser/steady stock, foregrip, extended
  magazines, rails) via `FieldAttachmentHardware`. The Ridgeline magazine is code-built (`sniperMagazine`).

## What changed
1. **Materials** (`textures/item/firearms_v2.png`). The atlas went from 1024 to 2048 px with the same 4x4 cell layout,
   and the cell means were kept, so every vertex tint lands on the same colour. Each cell got a proper material:
   - blued steel with polishing lines
   - parkerized phosphate grain
   - figured walnut with grain, latewood and pores
   - real cut checkering (2.7:1 diamonds with a double border)
   - matte polymer, brushed stainless, brass, ribbed rubber, FDE, anodized aluminium and stipple
   - subtle colour-case-hardened steel
   - birch

   Generator: `tools/gunsmith/materials.py`.
2. **Vanilla look**: a new `textures/item/firearms_mc.png` (16 texel MC-style cells, same layout).
   `FieldWeaponMesh.guns()` picks it whenever the world look is Minecraft (the Vanilla preset), and the Ultra
   materials otherwise. Vanilla's Performance quality also keeps the lighter "field" LOD.
3. **Meshes** (`patch/.../models/equipment/*.fheq`, all 3 LODs, built by `tools/gunsmith/refine.py`):
   - Every gun and attachment gets a baked short-range ambient-occlusion term in the vertex tint
     (`FheqBake.java`). This adds contact shadows under scopes, in guards and at stock joints.
   - The close LOD also gets rounded-edge fillets laid over long convex hard edges (`bevel.py`). Geometry is
     otherwise untouched.
   - **Scopes rebuilt as true lathes** (`scopes.py`): 6x/8x/12x/thermal now have a real 30 mm tube (was about 36 mm)
     and objective bells sized to their lens. Each has an eyepiece with a fast-focus ring and rubber eye guard, a
     knurled magnification ring with a throw lever, capped and knurled turrets with index marks, side focus on the
     8x/12x, and split rings with cross-bolt clamps. The thermal has a focus ring, a tan control pad with buttons and a
     battery cap. The 4-12x Field Optic now has a 25.4 mm tube. Axis heights, ring positions, feet and lens planes are
     the same as before.
   - **Revolver rebuilt** (`revolver.py`): stainless 6" with a ventilated rib and full underlug. It has a fluted
     cylinder with chamber mouths and brass case heads, a recoil shield, an adjustable rear sight, a ramp front sight
     with a red insert, a swept trigger guard, and lofted walnut target grips with a palm swell and medallions. Part
     ids, pivots, bore line, sight line and grip socket are identical, so the cylinder swing and spin, hammer and
     trigger animate as before.
   - **Assault Rifle stock**: the brace-like slab became a collapsible carbine stock on a round buffer tube (castle
     nut, cheek ridge, side panels, lever, QD socket, rubber pad). The comb sits at the same height, so the cheek riser
     still fits (`rifles.py`).
   - **Suppressor and muzzle brake rebuilt** (`attachments.py`): a turned can with a knurled mount, cap seams, bore and
     wrench notches, and a flat-sided ported brake with a jam nut. Lengths match the muzzle-flash offsets
     (0.172 / 0.056).
   - The bipod's candy-striped legs are now hard-anodized black.
4. **Glass** (`client/OpticGlass`, new): coated lenses (dark tube depth, green/magenta coating sheen, window glint,
   rim shadow, slight dome) and tinted red-dot windows (amber-rose coating band, clear centre). They are drawn at the
   same planes and radii. The 4-12x lenses were resized to its new eyepiece and bell.
5. **Ridgeline**: removing the factory scope used to leave ragged black ring-clamp spikes on the Weaver bases. The
   split now also moves the clamp wings and base-protruding triangles to the scope (`CamoRifleModel.splitScope`).
   Field scopes on the Ridgeline scale 0.9 radially and 0.78 in length, so their 27 mm tubes match the factory scope.
   `axisY`/`eyeZ` follow automatically.
6. **Pistol**: a rounder 24-point grip section. The olive specks in the nitride sheet are now neutral wear specks.

## Checks (offline, `tools/gunsmith/bench.sh`)
- `bench.sh ads`: for every weapon and sight combination (field guns and Ridgeline), it compares the drawn glass
  centres against the socket axis the first-person aim pose uses. It also checks that the housing is concentric with
  each lens and that nothing blocks the lens.
  - Before: 6 failures (the old thermal objective was blocked).
  - After: **0 failures**. The axis is unchanged to within 0.01 mm everywhere except the Ridgeline field scopes, which
    are intentionally about 6 mm higher (taller 30 mm rings, scaled 0.9). The aim pose reads the new value.
  - Logs: `docs/ws/gunsmith/ads_before.txt`, `ads_after.txt`.
- `bench.sh render`: software rasterizer with Minecraft entity lighting. It drew the contact sheets
  `docs/ws/gunsmith/guns_1..4.png`, `sights_before_after.png`, `attachments_before_after.png`,
  `gun_ridgeline_before_after.png` and `vanilla_look.png`.
- `bench.sh tris` (triangles, glass included; "before" is master code + base assets):

| item | close | field | distant |
|---|---|---|---|
| lever_rifle | 3648 -> 5104 | 2768 -> 2768 | 1395 -> 1395 |
| semi_auto_rifle | 5684 -> 9028 | 4782 -> 5044 | 3471 -> 3387 |
| pump_shotgun | 5008 -> 5668 | 3404 -> 3404 | 1343 -> 1343 |
| double_barrel | 2800 -> 3708 | 1936 -> 1936 | 1226 -> 1226 |
| semi_auto_shotgun | 4836 -> 5780 | 3404 -> 3404 | 1211 -> 1211 |
| revolver | 3368 -> 6930 | 2676 -> 4810 | 1631 -> 2246 |
| field_pistol | 1174 -> 1342 | same | same |
| tranquilizer_rifle (built-in scope) | 10292 -> 16000 | 7558 -> 9122 | 4379 -> 4759 |
| flare_gun | 2216 -> 3356 | 1538 -> 1538 | 892 -> 892 |
| bait_launcher | 4540 -> 6436 | 2712 -> 2712 | 1312 -> 1312 |
| six_power_scope | 4920 -> 8920 | 3836 -> 5400 | 2268 -> 2648 |
| eight / twelve_power_scope | 5324 -> 9784 | 4152 -> 5784 | 2356 -> 2840 |
| thermal_scope | 5588 -> 7928 | 4368 -> 5208 | 2536 -> 2424 |
| four_power_optic | 2728 -> 7752 | 2164 -> 4776 | 1912 -> 2320 |
| red dots / prism | 1.0-2.3k -> 1.3-3.6k | ~ same | ~ same |
| suppressor / brake | 1744 / 464 -> 1512 / 862 | | |
| Ridgeline (reference, unchanged) | 48498 | | |

The heaviest first-person case (a scoped rifle with every attachment) is about 25k triangles, half the Ridgeline
alone. Third person and dropped items use the field/distant LODs, which are mostly unchanged.

- Build: `python3 tools/build.py gunsmith-test /home/claude/work/gunsmith` succeeded, giving a 178.9 MB jar (+11 MB of
  uncompressed .fheq overrides and the 3.1 MB atlas, before zip compression).
- `check_jar.py --base master`: 0 errors, 0 warnings.
- `tools/compile.sh`: exit=0.
- `tools/qa/server_smoke.sh <jar>`: clean. No ERROR/WARN lines, no exceptions in frontier code, no client classes loaded on the server, no crash. recipes 259 ok. The only harness WARNs are the 6 pre-existing missing block loot tables.

## Files
- New:
  - `client/OpticGlass.java`
  - `tools/gunsmith/*`: `GunBench.java`, `bench.sh`, `FheqBake.java`, `fheq.py`, `fhrm.py`, `gunkit.py`, `scopes.py`,
    `revolver.py`, `rifles.py`, `attachments.py`, `bevel.py`, `refine.py`, `materials.py`, `sheets.py`
  - `patch/.../textures/item/firearms_v2.png` (replaces the base), `firearms_mc.png`
  - 84 `patch/.../models/equipment/*.fheq`
- Copied from dec62g8 and edited (marked `// [gunsmith]`): `client/FieldMountedOptic` (glass delegates to OpticGlass,
  4-12x lens sizes), `client/FieldTacticalSight` and `client/FieldReflexSight` (windows).
- Edited src files:
  - `FieldWeaponMesh` (`guns()` atlas selection; 4 lines)
  - `FieldGunEffects` (casings use `guns()`; 5 refs)
  - `CamoRifleModel.splitScope` (+12 lines)
  - `RidgelineSights` (radial/length scope scale)
  - `CombatPistolMesh.gripSection`
  - `patch/.../material/pistol_materials.png`

To regenerate: `python3 tools/gunsmith/materials.py patch/assets/frontierhunts/textures/item`, then
`python3 tools/gunsmith/refine.py <base equipment dir> patch/assets/frontierhunts/models/equipment`.

## In-game test script
1. `/give @s frontierhunts:lever_rifle`, `revolver`, `semi_auto_rifle`, `field_pistol`, `ridgeline_rifle`,
   `six_power_scope`, `twelve_power_scope`, `thermal_scope`, `four_power_optic`, `holographic_sight`, `reflex_sight`,
   `micro_red_dot`, `two_power_prism`, `suppressor`, `muzzle_brake`, `bipod`, `steady_stock`, and
   `frontierhunts:attachment_workbench`. Use the Ultra preset.
2. Hold each gun in first person and F5: walnut grain and checkering, blued/case-hardened receivers, no purple/black
   textures. Compare with `docs/ws/gunsmith/guns_*.png`.
3. Revolver:
   - Reload: the cylinder swings out to the left and spins. The hammer and trigger move.
   - Aim: the red front sight sits in the rear notch at the crosshair.
   - Fit a reflex sight: the rail sits flat on the top strap.
4. Fit each scope on the lever rifle and the Assault Rifle. Each scope sits on its rings and the rail with no gaps.
   Raise the gun slowly: the eyepiece stays centred through the transition, the overlay appears, and the zoom wheel
   works (6x 3-9x, 12x 5-25x, thermal 2-8x).
5. Red dots and prism: the window shows a faint amber-rose coating band while the centre stays clear. ADS puts the
   dot at the crosshair.
6. Assault Rifle: there is a new carbine stock. Fit the Steady Stock: the riser sits on the comb.
7. Fit the suppressor and the brake: they thread on at the muzzle, and the muzzle flash appears at their front.
8. Fit the bipod and crouch or go prone (Z): black legs unfold.
9. Ridgeline:
   - Install the twelve power scope (factory scope goes to the inventory). The Weaver bases are clean with no black
     spikes, and the 27 mm tube matches the factory scope's look.
   - Aim: the eyepiece is centred.
   - Remove all optics: the bases are clean and the iron sights are centred.
10. Switch to the Vanilla preset: the guns use the pixel-styled MC material atlas and stay the same shapes. Switch back
    to Ultra.
11. Shaders on (Photon or similar): the lenses still read as glass and nothing is see-through. Check AMD and Nvidia if
    available.
12. Dedicated server: give and fire the guns with 2 clients. There must be no errors (the changes are client-only
    rendering).
