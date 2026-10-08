# Workstream `calls` - real recordings for the hunting calls and wildlife voices

User request: the grunt tube, doe bleat call, antler rattling and predator caller "suck" - use real sounds of what
they actually sound like. Source recordings (user-approved downloads, licences verified by the coordinator) are U.S.
National Park Service (public domain) and iNaturalist (CC0 / CC BY 4.0). Full credits:
`patch/assets/frontierhunts/CREDITS-SOUNDS.txt` (ships as `assets/frontierhunts/CREDITS-SOUNDS.txt`).

## What changed
* **Calls** - all real except the grunt tube:
  * Doe Bleat Call (`bleat_call`, 3 variants): real doe bleats (iNat CC0, K. Zoebelein), 1-3 bleats per use.
  * Rattling Antlers (`antler_rattle`, 4): sequences built from real antler clacks and meshing cut from the NPS elk
    sparring recording (onset-detected grains + whole sparring clusters, pitched x1.12-1.45 for whitetail-sized racks):
    mid rattle, hard rattle with grinding, light "tickle", and hard rattle + brush raking (NPS elk thrashing a bush).
  * Predator call (`predator_call`, 4): real fawn distress (iNat CC BY, sofi v) - sped/overlapped for urgency; one
    variant opens with a short lone-coyote locator howl (NPS). Subtitle now "Predator call: fawn in distress".
    No free rabbit-distress recording exists and a synthesized one was not convincing, so it uses the real fawn
    distress instead (the dev.31 synthesized cottontail scream is replaced).
  * Grunt tube (`grunt_tube`, 4): no free real grunt-tube recording, so it is BUILT: glottal pulse train (110-130 Hz
    falling to 85-100 Hz, jitter/shimmer, vocal-fry tail), formant resonators, spectral envelope and pitch-synchronous
    breath taken from the real deer huff (iNat CC BY, C. Sites-Bowen) and buck snort-wheeze (CC0) recordings, a
    tube resonance and soft saturation. Variants: single contact grunt, double grunt, 4 tending grunts with clicks,
    3-grunt series ending in a long grunt. nearly all energy below 1.5 kHz (the old synthesized version was bright and buzzy).
  * Duck call (`duck_call`, 3): real hen-mallard quacks (NPS) instead of the hunts workstream's synthesis.
  * Deer Caller (`deer_call`): UNCHANGED on purpose - it is the user's own recorded mouth call (dev.18).
* **Wildlife voices** (same event ids, files replaced): whitetail bleats/contact bleats, alarm blows, snorts,
  alarm, snort-wheeze; elk bugles (+1 variant), mews (+2), alarm bark; moose cow call, bull grunts, threat; wolf
  howls and pack chorus; ambient coyotes; rut-fight antler clash/grind. Whitetail grunts keep their existing real
  CC0 grunt-call recording.
* **New real voices for WildlifeMob** (were pitched vanilla polar bear/cow/goat/parrot/chicken): new events
  `bear_huff`, `bear_growl`, `bison_bellow`, `cougar_growl`, `pronghorn_snort`, `duck_quack`, `grouse_drum`;
  wolves/coyotes now occasionally howl/yip with the real `wolf_howl`/`amb_coyote`. Hurt/death sounds stay vanilla.
  Chosen in `wildlife2026/RealVoices.pick` (bears huff 1/3 of ambient ticks, growl when warning; bison bellow 1/2;
  cougar/panther growl only when warning; pronghorn snort 1/3 or when warning; ducks quack; grouse drum 1/4;
  wolf howl 1/6; coyote yips 1/5; other ticks silent or vanilla as before).

## Processing (tools/calls/*.py, numpy/scipy + ffmpeg)
Cut to the event, Butterworth high-pass (45-300 Hz per species), spectral-gating noise reduction using a noise-only
clip from the same recording (removes wind, hum, insects, distant birds), optional low-pass, sine fades, mono
44.1 kHz, loudness-matched by BS.1770 momentary max to the levels the old files were designed at (calls -12.5..-15,
voices -10.5..-15.5 LUFS M-max; the existing FrontierSoundCategory trims still apply), soft limiter so every file
peaks <= -1.2 dBFS after encoding, Ogg Vorbis q5. Before/after spectrograms of all main calls were checked.
80 files, ~1.9 MB. Same sound-event ids, subtitles kept, attenuation distances copied from the existing entries.

## Files
* Sounds: `patch/assets/frontierhunts/sounds/{equipment,wildlife,wildlife/voice,ambient,rutfight,hunts}/*.ogg`
* `patch/_merge/assets/frontierhunts/sounds.json/calls.json` (extra variants + 7 new events),
  `patch/_merge/assets/frontierhunts/lang/en_us.json/calls.json` (7 subtitles + predator_call subtitle)
* NEW `src/.../wildlife2026/RealVoices.java` (registers the 7 events, picks the voice)
* Shared files (small, tagged `[calls]`):
  * `wildlife2026/WildlifeMob.java`: new `playAmbientSound()` override (12 lines) - uses RealVoices, else `super`.
  * `sound/FrontierSoundCategory.java`: one `put(WILDLIFE, "bear_huff", ...)` line.
  * `hunts/HuntContent.java`: javadoc line (duck call is now real quacks).
* `patch/assets/frontierhunts/CREDITS-SOUNDS.txt`, `tools/calls/` (build scripts).
* Call item behaviour (animal response, ranges, cooldowns) is untouched - only audio changed.

## In-game test script
1. Give `grunt_tube`, `bleat_call`, `rattling_antlers`, `predator_call`, `duck_call`: `/give @s frontierhunts:grunt_tube` etc.
   Use each several times: grunt tube = low, short guttural "urp" grunts (4 patterns); bleat can = nasal doe "maa";
   antlers = real clacking/grinding (4 patterns, one with brush raking); predator call = urgent fawn distress, one
   variant starts with a coyote howl; duck call = hen-mallard quacks. Subtitles on: check the texts.
2. Spawn whitetails near you during rut (`/time set` to a rut date or use the existing debug commands) and confirm
   they still answer the grunt/bleat/rattle exactly as before.
3. Spook a deer: real blow/snort; alarm. Elk/moose (if spawned): real bugles, mews, bark, cow calls, bull grunts.
4. `/summon frontierhunts:<grizzly|black_bear|bison|pronghorn|duck|grouse|cougar|wolf|coyote>` (use the mod's
   wildlife entity ids) and wait ~30 s near each: bear huffs (growl when it warns you), bison bellows, pronghorn
   snorts, duck quacks, grouse drumming (deep thumps), cougar snarl only when warning, occasional real wolf howl /
   coyote yips. Hurt/death sounds unchanged.
5. Frontier settings > Sound > "Wildlife & calls" slider scales all of them; nothing should be louder than the
   old calls (designed to match old levels; no file peaks above -1.2 dBFS).

## Mapping table (event -> file -> source clip -> processing)
| event | file (sounds/...) | source clip | processing | M-max LUFS | s |
|---|---|---|---|---|---|
| antler_rattle | equipment/antler_rattle_0 | NPS elk_fight (+elk_bush for #3) | mid rattle sequence built from real antler clacks/meshing (onset-detected grains + sparring clusters), HP250, spectral gate, pitch x1.12-1.45 (whitetail-sized antlers) | -16.3 | 2.63 |
| antler_rattle | equipment/antler_rattle_1 | NPS elk_fight (+elk_bush for #3) | hard rattle sequence built from real antler clacks/meshing (onset-detected grains + sparring clusters), HP250, spectral gate, pitch x1.12-1.45 (whitetail-sized antlers) | -16.3 | 2.9 |
| antler_rattle | equipment/antler_rattle_2 | NPS elk_fight (+elk_bush for #3) | tickle rattle sequence built from real antler clacks/meshing (onset-detected grains + sparring clusters), HP250, spectral gate, pitch x1.12-1.45 (whitetail-sized antlers) | -17.6 | 3.67 |
| antler_rattle | equipment/antler_rattle_3 | NPS elk_fight (+elk_bush for #3) | brush rattle sequence built from real antler clacks/meshing (onset-detected grains + sparring clusters), HP250, spectral gate, pitch x1.12-1.45 (whitetail-sized antlers) | -16.2 | 5.33 |
| deer_antler_clash | rutfight/clash_0 | NPS elk_fight @64.58s | single real antler clash, HP250, gate, pitch x1.1-1.25 | -17.9 | 0.3 |
| deer_antler_clash | rutfight/clash_1 | NPS elk_fight @75.88s | single real antler clash, HP250, gate, pitch x1.1-1.25 | -16.5 | 0.29 |
| deer_antler_clash | rutfight/clash_2 | NPS elk_fight @161.44s | single real antler clash, HP250, gate, pitch x1.1-1.25 | -18.7 | 0.28 |
| deer_antler_clash | rutfight/clash_3 | NPS elk_fight @46.39s | single real antler clash, HP250, gate, pitch x1.1-1.25 | -17.8 | 0.27 |
| deer_antler_grind | rutfight/grind_0 | NPS elk_fight 3.3-4.3s | real antler meshing segment 1.0s, HP250, gate, pitch x1.15 | -17.2 | 0.87 |
| deer_antler_grind | rutfight/grind_1 | NPS elk_fight 114.2-115.2s | real antler meshing segment 1.0s, HP250, gate, pitch x1.15 | -16.7 | 0.87 |
| deer_antler_grind | rutfight/grind_2 | NPS elk_fight 140.5-141.5s | real antler meshing segment 1.0s, HP250, gate, pitch x1.15 | -17.1 | 0.87 |
| bleat_call | equipment/bleat_call | iNat CC0 doe_vocal2 (K. Zoebelein) | real doe bleats [(1.34, 1.72), (14.02, 14.36)], HP150, spectral gate, slight pitch drop | -13.5 | 1.74 |
| bleat_call | equipment/bleat_call_1 | iNat CC0 doe_vocal2 (K. Zoebelein) | real doe bleats [(7.9, 8.26)], HP150, spectral gate, slight pitch drop | -13.6 | 0.5 |
| bleat_call | equipment/bleat_call_2 | iNat CC0 doe_vocal2 (K. Zoebelein) | real doe bleats [(21.32, 21.66), (28.26, 28.6), (4.22, 4.56)], HP150, spectral gate, slight pitch drop | -13.6 | 2.79 |
| deer_bleat | wildlife/whitetail_bleat_a | iNat CC0 doe_vocal2 | single doe bleat, HP150, gate | -15.6 | 0.42 |
| deer_bleat | wildlife/whitetail_bleat_b | iNat CC0 doe_vocal2 | single doe bleat, HP150, gate | -15.5 | 0.46 |
| deer_bleat | equipment/deer_contact_a | iNat CC BY fawn_bleat2 (sofi v) | fawn contact bleat, HP300, gate | -16.0 | 0.51 |
| deer_bleat | equipment/deer_contact_b | iNat CC BY fawn_bleat2 (sofi v) | fawn contact bleat, HP300, gate | -16.0 | 0.41 |
| deer_blow | wildlife/whitetail_blow_0 | iNat CC BY deer_wheeze_snort (Daughter Dad) 3.3s | real alarm blow, HP150, gate (birds removed) | -14.0 | 0.58 |
| deer_blow | wildlife/whitetail_blow_1 | iNat CC BY deer_wheeze_snort 13.3s | real alarm blow, HP150, gate | -14.0 | 0.56 |
| deer_snort | wildlife/whitetail_snort_0 | iNat CC BY deer_snort (W. J. Deml) 5.5s | real snort, HP200, gate (insect band removed) | -14.0 | 0.38 |
| deer_snort | wildlife/whitetail_snort_1 | iNat CC BY deer_snort 24.0s | real snort, HP200, gate | -14.3 | 0.36 |
| deer_snort | wildlife/whitetail_snort_2 | iNat CC BY deer_wheeze_snort 27.7s | real snort, HP150, gate | -14.1 | 0.72 |
| deer_snort | wildlife/whitetail_alarm | iNat CC BY deer_wheeze_snort + deer_snort | blow followed by a snort (two real takes), HP, gate | -14.5 | 1.0 |
| deer_wheeze | wildlife/whitetail_wheeze_0 | iNat CC0 buck_aggr (A. Parker) 0.7s | real aggressive buck snort-wheeze, HP120, gate | -14.0 | 0.7 |
| elk_bugle | wildlife/elk_bugle_0 | NPS elk_bugle2 12.7-16.4s | bugle, HP150, gate | -10.5 | 3.65 |
| elk_bugle | wildlife/elk_bugle_1 | NPS elk_bugle3 8.1-10.6s | bugle, HP150, gate | -10.5 | 2.5 |
| elk_bugle | wildlife/elk_bugle_2 | NPS elk_bugle1 2.75-7.1s | bugle + grunts (chuckles), HP150, gate | -10.5 | 4.35 |
| elk_bugle | wildlife/elk_bugle_3 | NPS elk_bugle3 16.5-19.05s | bugle, HP150, gate | -10.5 | 2.55 |
| elk_mew | wildlife/elk_mew_0 | NPS elk_mew 0.12-0.75s | cow mew, HP250, gate (tonal hum removed) | -12.1 | 0.63 |
| elk_mew | wildlife/elk_mew_1 | NPS elk_mew 1.42-2.05s | cow mew, HP250, gate (tonal hum removed) | -12.0 | 0.63 |
| elk_mew | wildlife/elk_mew_2 | NPS elk_mew 2.52-3.15s | cow mew, HP250, gate (tonal hum removed) | -12.0 | 0.63 |
| elk_mew | wildlife/elk_mew_3 | NPS elk_mew 3.66-4.32s | cow mew, HP250, gate (tonal hum removed) | -12.1 | 0.66 |
| elk_bark | wildlife/elk_bark_0 | NPS elk_bark 1.4-2.5s | alarm bark, HP150, gate | -14.0 | 1.1 |
| elk_bark | wildlife/elk_bark_1 | NPS elk_bark 1.4-2.5s | alarm bark pitched x1.06, HP150, gate | -14.5 | 1.04 |
| moose_call | wildlife/moose_call_0 | NPS moose_rut 9.7-12.6s | cow moose rut call, HP120, gate | -12.5 | 2.9 |
| moose_call | wildlife/moose_call_1 | NPS moose_rut 0.55-2.75s | cow moose rut call, HP120, gate | -12.5 | 2.2 |
| moose_grunt | wildlife/moose_grunt_0 | NPS moose_bull 10.25-10.75s | bull grunt, HP70, gate | -17.1 | 0.5 |
| moose_grunt | wildlife/moose_grunt_1 | NPS moose_bull 8.5-9.25s | bull grunt, HP70, gate | -16.8 | 0.75 |
| moose_grunt | wildlife/moose_grunt_2 | NPS moose_bull 20.12-20.7s | bull grunt, HP70, gate | -14.8 | 0.58 |
| moose_threat | wildlife/moose_threat_0 | NPS moose_bull 0.75-2.4s | long bull grunt/croak, HP70, gate | -12.6 | 1.67 |
| wolf_howl | wildlife/wolf_howl_0 | NPS wolf_howl_DENA | lone howl, HP150, gate | -11.5 | 6.05 |
| wolf_howl | wildlife/wolf_howl_1 | NPS wolf_chorus 4.2-10.4s | howl, HP150, gate | -12.0 | 6.2 |
| wolf_howl | wildlife/wolf_howl_2 | NPS wolf_howl_DENA | lone howl pitched x0.94 (deeper wolf), HP150, gate | -12.0 | 6.44 |
| wolf_chorus | wildlife/wolf_chorus_0 | NPS wolf_chorus 15-25.5s | pack chorus, HP150, gate | -12.5 | 10.5 |
| wolf_chorus | wildlife/wolf_chorus_1 | NPS wolf_chorus 63-73.5s | pack chorus, HP150, gate | -12.5 | 10.5 |
| amb_coyote | ambient/coyote_0 | NPS coyote_yell 14-22s | group yip-howl, HP250, gate | -14.0 | 8.0 |
| amb_coyote | ambient/coyote_1 | NPS coyote_moja 0-7.5s | yips and howls, HP250, light gate | -14.0 | 7.5 |
| amb_coyote | ambient/coyote_2 | NPS coyote_yell 36-44s | group yip-howl, HP250, gate | -14.2 | 8.0 |
| predator_call | equipment/predator_call | iNat CC BY fawn_bleat2 (sofi v) | fawn distress sequence (8 real bleats, sped x1.04-1.14 and overlapped for urgency), HP300, gate | -12.5 | 3.44 |
| predator_call | equipment/predator_call_1 | NPS coyote_chase + iNat CC BY fawn_bleat2 | short lone-coyote locator howl, then fawn distress, HP300, gate | -12.5 | 5.29 |
| predator_call | equipment/predator_call_2 | iNat CC BY fawn_bleat2 (sofi v) | fawn distress sequence (9 real bleats), HP300, gate | -12.5 | 3.77 |
| predator_call | equipment/predator_call_3 | iNat CC BY fawn_bleat2 (sofi v) | fawn distress sequence (7 real bleats), HP300, gate | -12.5 | 3.57 |
| duck_call | hunts/duck_call_0 | NPS mallard 28.05-30.1s | hen mallard quack series, HP300, gate | -13.5 | 2.05 |
| duck_call | hunts/duck_call_1 | NPS mallard 25.4-26.05s | hen quacks, HP300, gate | -14.5 | 0.65 |
| duck_call | hunts/duck_call_2 | NPS mallard 24.25-25.25s | soft feeding chuckle quacks, HP300, gate | -18.8 | 1.0 |
| grunt_tube | equipment/grunt_tube | built (no free recording) - timbre from iNat deer_huff (C. Sites-Bowen, CC BY) & buck_aggr (CC0) | synthesised buck grunt: glottal pulse train (f0 contour, jitter/shimmer, vocal-fry tail), formant resonators, spectral envelope + pitch-synchronous breath taken from the real deer_huff/buck_aggr recordings, reed-tube resonance, soft saturation; 1 grunt(s) | -15.0 | 0.48 |
| grunt_tube | equipment/grunt_tube_1 | built (no free recording) - timbre from iNat deer_huff (C. Sites-Bowen, CC BY) & buck_aggr (CC0) | synthesised buck grunt: glottal pulse train (f0 contour, jitter/shimmer, vocal-fry tail), formant resonators, spectral envelope + pitch-synchronous breath taken from the real deer_huff/buck_aggr recordings, reed-tube resonance, soft saturation; 2 grunt(s) | -15.0 | 1.23 |
| grunt_tube | equipment/grunt_tube_2 | built (no free recording) - timbre from iNat deer_huff (C. Sites-Bowen, CC BY) & buck_aggr (CC0) | synthesised buck grunt: glottal pulse train (f0 contour, jitter/shimmer, vocal-fry tail), formant resonators, spectral envelope + pitch-synchronous breath taken from the real deer_huff/buck_aggr recordings, reed-tube resonance, soft saturation; 4 grunt(s) | -15.4 | 1.64 |
| grunt_tube | equipment/grunt_tube_3 | built (no free recording) - timbre from iNat deer_huff (C. Sites-Bowen, CC BY) & buck_aggr (CC0) | synthesised buck grunt: glottal pulse train (f0 contour, jitter/shimmer, vocal-fry tail), formant resonators, spectral envelope + pitch-synchronous breath taken from the real deer_huff/buck_aggr recordings, reed-tube resonance, soft saturation; 3 grunt(s) | -15.0 | 2.12 |
| bear_huff | wildlife/voice/bear_huff_0 | NPS grizzly 0.95-3.35s | huffing/blowing, HP60, gate | -14.0 | 2.4 |
| bear_huff | wildlife/voice/bear_huff_1 | NPS grizzly 3.9-6.3s | huffs and jaw pops, HP60, gate | -17.7 | 2.4 |
| bear_growl | wildlife/voice/bear_growl_0 | NPS grizzly 16.35-19.9s | growl, HP60, gate | -12.5 | 3.55 |
| bear_growl | wildlife/voice/bear_growl_1 | NPS grizzly 66.2-69.6s | growl, HP60, gate | -12.5 | 3.4 |
| bear_growl | wildlife/voice/bear_growl_2 | NPS grizzly 88.65-91.4s | roaring growl, HP60, gate | -12.5 | 2.75 |
| bison_bellow | wildlife/voice/bison_bellow_0 | NPS bison_rut 2.3-3.8s | rut bellow, HP45, gate | -13.4 | 1.5 |
| bison_bellow | wildlife/voice/bison_bellow_1 | NPS bison_rut 7.3-8.6s | rut bellow, HP45, gate | -14.0 | 1.3 |
| bison_bellow | wildlife/voice/bison_bellow_2 | NPS bison_rut 21.4-22.7s | rut bellow, HP45, gate | -13.9 | 1.3 |
| bison_bellow | wildlife/voice/bison_bellow_3 | NPS bison_rut 28.3-29.8s | rut bellow, HP45, gate | -13.7 | 1.5 |
| cougar_growl | wildlife/voice/cougar_growl_0 | iNat CC BY cougar_growl (Colin Croft) 2.05s | growl/hiss, HP90, gate | -14.1 | 0.57 |
| cougar_growl | wildlife/voice/cougar_growl_1 | iNat CC BY cougar_growl (Colin Croft) 8.45s | spit/hiss, HP90, gate | -20.0 | 0.4 |
| cougar_growl | wildlife/voice/cougar_growl_2 | NPS cougar_juv 2.0s | young mountain lion cry pitched x0.85, HP200, gate | -15.0 | 0.67 |
| pronghorn_snort | wildlife/voice/pronghorn_snort_0 | iNat CC BY pronghorn_sneeze (Konshau Duman) 1.45s | alarm snort/sneeze, HP150, gate | -15.1 | 0.5 |
| pronghorn_snort | wildlife/voice/pronghorn_snort_1 | iNat CC BY pronghorn_sneeze (Konshau Duman) 14.35s | alarm snort/sneeze, HP150, gate | -16.4 | 0.5 |
| pronghorn_snort | wildlife/voice/pronghorn_snort_2 | iNat CC BY pronghorn_sneeze (Konshau Duman) 24.85s | alarm snort/sneeze, HP150, gate | -16.4 | 0.5 |
| duck_quack | wildlife/voice/duck_quack_0 | NPS mallard 29.5-30.1s | hen quacks, HP300, gate | -15.0 | 0.6 |
| duck_quack | wildlife/voice/duck_quack_1 | NPS mallard 28.05-28.5s | hen quack, HP300, gate | -15.0 | 0.45 |
| duck_quack | wildlife/voice/duck_quack_2 | NPS mallard 25.4-26.05s | hen quacks, HP300, gate | -16.6 | 0.65 |
| grouse_drum | wildlife/voice/grouse_drum_0 | NPS ruffed_grouse 0.6-11.6s | full drumming roll (accelerating wing beats), HP40/LP900, gate | -17.0 | 11.0 |
| grouse_drum | wildlife/voice/grouse_drum_1 | NPS ruffed_grouse 5.5-11.6s | drumming roll ending, HP40/LP900, gate | -17.0 | 6.1 |