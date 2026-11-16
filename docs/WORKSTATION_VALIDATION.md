# Workstation validation

## 1.2.2: Patchouli, Ponder, GeckoLib, playerAnimator

Validated on 2026-11-15 on the 1.2.1 setup (Linux, Java 17, Forge 47.4.23, Diegesis 1.0.0-beta.10, Curios and Immersive
Smithing 1.0.0 in the dev runtime unless noted) with Patchouli 1.20.1-85-FORGE, Ponder 1.0.91, Flywheel 1.0.5, GeckoLib
4.8.4 and playerAnimator 1.0.2-rc1+1.20. Evidence is in `build/reports/libraries-1.2.2/`.

**Passed**

- `build`: **PASS**, 72 unit tests (61 before, plus `LibraryResourcesTest` 6, `PoseRulesTest` 2, `DemoHandsTest` 3), no
  failures or skips; `build/libs/immersive-jewelry-1.2.2.jar`, whose `mods.toml` declares Patchouli and GeckoLib on both
  sides and Ponder, Flywheel and playerAnimator on clients, with the ranges of `gradle.properties`; no test agent in the
  jar. [Log](../build/reports/libraries-1.2.2/logs/build.log).
- `runGameTestServer`: **56/56**, and **56/56** with `-PnoSmithing -PnoCurios` (the dedicated-server dist, with Ponder and
  playerAnimator on the classpath but never loaded by common code). New: `PresentationGameTests` (one GeckoLib cache per
  station; the worn-appearance snapshot keeps the look and drops maker, defects, history and foreign data; body-pose
  rules on real stations, with no replay on first sight).
  [Log](../build/reports/libraries-1.2.2/logs/runGameTestServer.log),
  [without Smithing and Curios](../build/reports/libraries-1.2.2/logs/runGameTestServer-noSmithing-noCurios.log).
- Resource checks: `tools/generate_resources.py`, `generate_textures.py`, `generate_sounds.py`, `generate_rigs.py`,
  `generate_book.py` (every key in all five locales) and `generate_ponder.py` all up to date; `check_mod.py` reports only
  the documented item sub-models.
- `IJ_LIBRARIES_ONLY=1 runClient -PsmokeTest`, second run: **all 40 pass/fail checks passed**
  ([result](../build/reports/libraries-1.2.2/smoketest-result-run2.json)): the three rigs and their clips are baked and
  draw without failing; all 38 body clips load; all 33 lessons compile in Ponder; the book is registered, opens, and opens
  entries with live cards (gems, design atlas, cuts, finishes, controls, blank routes); a lesson opens from a Watch call,
  plays, and closing it returns to the book page; three more lessons play (round cut, halo atlas, antique finish); the
  jeweler's body layer poses the local player while operating the bench and stops when the lease ends; help at the wheel
  leaves the station and opens the book. Screenshots: `build/reports/libraries-1.2.2/screenshots/`. No error from the mod
  or the libraries in the client log (only fresh-world config defaults and the dev narrator library).

**Failed, then fixed**

- First client run: `ponder_returns_to_book` failed. Ponder opens its screen through a transition, so the navigator saw
  "no lesson on screen" on the tick after opening and dropped the return. It now waits until the lesson has appeared
  (giving up after 60 ticks). The first run also showed the lessons' stations facing away from Ponder's camera; the
  structures now face north and the demonstrating jeweler stands on the camera's side. Both fixed in the second run.
  [Run 1 log](../build/reports/libraries-1.2.2/logs/runClient-libraries-run1.log),
  [run 2 log](../build/reports/libraries-1.2.2/logs/runClient-libraries-run2.log).

**Not run** (need people, a second machine or other mod stacks)

- Two real clients on a dedicated server: remote body poses, worn-jewelry snapshots between players, watcher view,
  handoff, disconnect during contact, and the help return notice with another player changing the work.
- A dedicated `runServer` boot (still needs the user's `run/eula.txt`); the GameTest server covers the dedicated dist.
- Curios' renderer hook drawn on a player, and the built-in slots' layer on a remote player (the client run had Curios,
  so the built-in layer was inactive; the snapshot is covered by GameTests).
- The JEI handbook key over a recipe, H over an inventory item, and the folio's "?" (same code path as the help call that
  passed).
- Every lesson watched to the end by eye (four were played and screenshotted; all 33 compile), Russian and Chinese book
  layout at high GUI scale, narrator, high contrast, reduced motion, left-handed and slim-skin views of the poses, audible
  sound, Embeddium/Oculus with shaders, Create with the same Ponder, frame-time profiling of idle workshops with rigs, and
  first-person-body or emote mods sharing playerAnimator.

## 1.2.1: Diegesis 1.0.0-beta.10

Validated on 2026-11-15 with the setup of the 1.2.0 run below, except for Diegesis: 1.0.0-beta.10 (CurseForge file
9624632, byte-identical to the supplied `diegesis-forge-1.20.1-1.0.0-beta.10.jar`, SHA-256 `2b7c9f5b…666bed`; source
tag `v1.0.0-beta.10`, commit 727c177). Same commands as below, plus the four compile tasks on their own. The checks
listed here ran while `mod_version` was still 1.2.0; after the bump to 1.2.1, on otherwise the same tree, `build`
passed again (61 unit tests; `build/libs/immersive-jewelry-1.2.1.jar`, whose `mods.toml` says version 1.2.1 and
Diegesis `[1.0.0-beta.10,1.0.0-beta.11)`) and `runGameTestServer` passed **53/53** with the mod loaded as 1.2.1.
[Build log](../build/reports/diegesis-1.0.0-beta.10/logs/gradle-ImmersiveJewelry-build-20261115-184138.log),
[GameTest log](../build/reports/diegesis-1.0.0-beta.10/logs/gradle-ImmersiveJewelry-runGameTestServer-20261115-184148.log).

- **Upgrade:** the unchanged sources compiled against beta.10 in all four source sets: beta.10's public API only adds
  to beta.9's, apart from the `NodeDefinition` record constructor, which the mod does not call.
  [Log](../build/reports/diegesis-1.0.0-beta.10/logs/gradle-ImmersiveJewelry-compileJava-20261115-140314.log).
- `build`: **PASS**, 61 unit tests, no failures or skips.
  [Log](../build/reports/diegesis-1.0.0-beta.10/logs/gradle-ImmersiveJewelry-build-20261115-182438.log).
- `runGameTestServer`: **53/53**, and **53/53** with `-PnoSmithing -PnoCurios`. New: `DiegesisGameTests` (the loaded
  Diegesis is within the declared range and 1.0.0-beta.9 is not; both scenes parse with the library's strict codec, with
  the expected `takes_items` nodes, a compact on-request drawer with a carry size, no blocks or entities cleared from
  view and clicks after arrival; both stations keep server authority, no vanilla menu, no library slots, one operator
  within reach and their block in place), and placement-preview parity at the bench (20 cases) and the wheel (5 cases):
  the hint always agrees with the custody move.
  [Log](../build/reports/diegesis-1.0.0-beta.10/logs/gradle-ImmersiveJewelry-runGameTestServer-20261115-182450.log),
  [without Smithing and Curios](../build/reports/diegesis-1.0.0-beta.10/logs/gradle-ImmersiveJewelry-runGameTestServer-20261115-182515.log).
- `runClient -PsmokeTest` (two runs, the window left alone): the first found that JEI takes no keys on a station's
  screen (it reads keys only on screens it has GUI properties for), so its show-recipe key did nothing at a station;
  the station's own key handler now asks JEI. The second run **passed all 106 checks**
  ([result](../build/reports/diegesis-1.0.0-beta.10/smoketest-result-run2.json),
  [log](../build/reports/diegesis-1.0.0-beta.10/logs/client-latest-20261115-1819.log)),
  among them, at the bench: a tray stone is the stack under the cursor; JEI lays nothing over the station; R over it
  opens JEI's Gem Cutting page over the station with the station and lease kept; a key pressed under that page does not
  reach the station; Escape returns to the station; a stone taken from the inventory through Diegesis
  (`StationView.takeFromInventory`) lights up the tray cell it would go to; R with a stone in the tweezers turns the
  stone (over the clamped frame, where it could also look up); a notch of the mouse wheel through Diegesis' scroll path
  turns it back. Frame interval while focused: median 8.4 ms, p95 13.6 ms, p99 19.1 ms (vsync off).
- Generators, `check_mod.py` and `git diff --check`: as below, unchanged.

Evidence: [JEI page over the bench](../run/validation/diegesis-1.0.0-beta.10/smoketest_bench_jei_child.png),
[station with JEI loaded](../run/validation/diegesis-1.0.0-beta.10/smoketest_bench_focus.png),
[placement hint](../run/validation/diegesis-1.0.0-beta.10/smoketest_bench_place_hint.png),
[filtered drawer](../run/validation/diegesis-1.0.0-beta.10/smoketest_bench_drawer.png).

Not tested: the carry sizes were lowered (wheel 1.5, bench 1 scene pixel) after the client runs, and a stack carried
far enough back to be drawn in the scene was not seen in any run; the agent injects hover events instead of moving
the real pointer; a client without JEI, and EMI or REI; launching with beta.9 installed (the GameTest asserts the
range refuses it; Forge's refusal screen was not opened); and what the 1.2.0 run below lists as not tested.

## 1.2.0: hands-on workstations

Validated on 2026-11-14: Minecraft 1.20.1, Forge 47.4.23, Java 17.0.20, Diegesis 1.0.0-beta.9 (CurseForge file 9601051),
JEI 15.20.0.135, Curios 5.14.1, Immersive Smithing 1.0.0, Jade (smoke-test runtime only). Intel i7-13700HX; the client
ran on Mesa Intel UHD Graphics (ADL-S GT1) under Hyprland 0.57.2, 1920x1200 display. Checkout `main` at `11a6a65` plus
the uncommitted 1.2.0 work. Use `Q=/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh` and
`R=/home/otectus/Projects/ImmersiveJewelry`.

```sh
"$Q" "$R" build compileGameTestJava compileSmokeTestJava
"$Q" "$R" runGameTestServer
"$Q" "$R" runGameTestServer -PnoSmithing -PnoCurios
"$Q" "$R" runClient -PsmokeTest
python3 tools/generate_resources.py --check
python3 tools/generate_textures.py --check
python3 tools/generate_sounds.py --check
python3 /home/otectus/Projects/.mcmod-tools/check_mod.py "$R"
git diff --check
```

Results:

- `build compileGameTestJava compileSmokeTestJava`: **PASS**, 51 unit tests (simulations, leases and pacing,
  resources, data, quality, migration), no failures or skips.
  [Log](../build/reports/workstation-1.2.0/logs/gradle-ImmersiveJewelry-build-20261114-210408.log).
- `runGameTestServer`: **48/48 required tests passed** (Curios and Immersive Smithing loaded): wheel and bench end to
  end through the real lease and input path, conservation, persistence and resume, packing and unpacking, every rework,
  breaking a station mid-work, damage ending a lease, stale or foreign input, legacy escrow migration, events and gates
  (the unit tests cover pacing, duplicate and overlapping batches and the bounded backlog).
  [Log](../build/reports/workstation-1.2.0/logs/gradle-ImmersiveJewelry-runGameTestServer-20261114-210633.log).
  Without Smithing and Curios (built-in slots, crafted blanks): **48/48**.
  [Log](../build/reports/workstation-1.2.0/logs/gradle-ImmersiveJewelry-runGameTestServer-20261114-210705.log).
- Generators: resources **228/228**, textures **107/107**, sounds **35/35** current. `check_mod.py`: **0 errors,
  0 warnings**, 26 notes (property-selected item sub-models, see `MODMAP.md`). `git diff --check`: clean.
- **Server budget (handoff 14.3)**, `PerformanceGameTests`: eight operators grinding eight wheels at the real input pace
  (2.5 steps a tick, batches of five) cost **0.030 ms per server tick** (0.029 ms without Smithing/Curios) against a
  1 ms budget; every paced sample was admitted. A worked wheel's update tag is **1,502 bytes** uncompressed, sent at
  most ten times a second to each watching player (about 15 KB/s before Minecraft's packet compression); one input batch
  is **64 bytes** (about 640 B/s per operator).

### Real client (`runClient -PsmokeTest`)

The agent drives the real client and integrated server: blocks are used through `MultiPlayerGameMode#useItemOn`, keys
go through `KeyboardHandler#keyPress`, pointer events are handed to the behaviour Diegesis holds. It logs the state of
hand, focus, leases and both stations at every phase.

- **Lapidary Wheel, complete** (two runs): walk-up mount and appraisal, focus and lease, a round cut planned at the
  specimen rack, a pointer stroke on the handle that grinds, keyboard grinding to 45 %, leaving mid-cut (lease ended,
  work kept on the stone, tools lifted), stepping back up and resuming, grinding every face into its window (about
  60 s at the sandstone lap for a diamond), the loupe and Escape closing only the loupe, the release lever, taking the
  cut stone. [Log](../build/reports/workstation-1.2.0/logs/gradle-ImmersiveJewelry-runClient-20261114-205336.log).
- **Jeweler's Bench, up to finishing:** walk-up custody (blank to the clamp, pliers to the dock, the cut stone to the
  tray, compound to the supplies), focus, the folio choosing the solitaire design, the tweezers seating the stone, the
  framing at 1024x768, 1600x600 and GUI scale 4 while focused, the pliers closing every prong by keyboard (about 60 s),
  and confirming into the finishing stage.
- **Interrupted:** both full runs were ended by the game window being closed from outside (`Stopping!` with no agent
  `finish`), at 20:47:16 and 20:58:33, while the user was working at the desktop; no result file was written by those
  runs. The finishing by hand, taking the ring, the accessibility pass (no zoom, instant transitions, numeric guide,
  high contrast, larger targets, hands off, keyboard planning), Jade, the handbook, JEI, the item sheets and the
  jewelry-slots screen were therefore **not reached in a client run**. Their server paths are covered by the GameTests
  above. The agent now writes its result after every phase so an interrupted run still leaves its checks.
- Agent fixes found by these runs (test code only): items handed to the player no longer overwrite the main hand;
  Escape is only pressed while a station is focused (a stray Escape opened the pause menu and paused the integrated
  server); key pacing restarts with every phase; vsync is off for the agent, because a window on a hidden workspace had
  its vsync'd frames throttled to about two a second (the stations advance with frames: at most 12 steps a frame).
- Product fixes found by these runs: the station hint caption was drawn under the inventory drawer's tab at bottom
  centre (the tab now sits at 86 % of the bottom edge in both scenes).

Selected evidence: [wheel focus](../run/validation/1.2.0/smoketest_wheel_focus.png),
[handle contact](../run/validation/1.2.0/smoketest_wheel_contact.png),
[loupe](../run/validation/1.2.0/smoketest_wheel_loupe.png),
[cut stone](../run/validation/1.2.0/smoketest_wheel_finished.png),
[bench focus](../run/validation/1.2.0/smoketest_bench_focus.png),
[tweezers](../run/validation/1.2.0/smoketest_bench_tweezers.png),
[4:3](../run/validation/1.2.0/smoketest_bench_4x3.png), [1600x600](../run/validation/1.2.0/smoketest_bench_wide.png),
[GUI scale 4](../run/validation/1.2.0/smoketest_bench_gui4.png),
[prong work](../run/validation/1.2.0/smoketest_bench_prong_work.png),
[prongs closed](../run/validation/1.2.0/smoketest_bench_prongs_closed.png).

### Not tested

Two real clients at one station (an observer and a refused second operator), a dedicated server with a remote
client, Embeddium/Oculus with and without shaders, audible playback, and the client frame-time budget at 1080p (the
agent's per-frame timings are written only when a run completes). Release with the interrupted client phases re-run:
`"$Q" "$R" runClient -PsmokeTest` with the window left alone, then `run/smoketest/smoketest-result.json`.

## 1.1.0: rendering and Russian localization

Validated on 2026-10-05, Minecraft 1.20.1, Forge 47.4.23, Java 17.0.19. Checkout: `main`,
`c6294f0`, origin `https://github.com/otectus/ImmersiveJewelry.git`. Existing uncommitted progression/API,
feedback and asset work was preserved. The existing project version is 1.1.0.

## Confirmed causes and changes

- **Conflicting geometry:** committed models contained 47 overlapping, same-facing, axis-aligned face pairs
  across the bench, wheel frame and disc. The bench's corner straps shared the worktop's exterior planes;
  the frame also duplicated the animated axle. Corrected models separate exposed trim, remove covered faces
  and the duplicate axle, and retain the original tools, tray, apron, woodwork and wheel silhouette.
  Face counts change from **354/229/96 to 287/197/83**, respectively: 112 fewer faces, with zero conflicting
  pairs in the regression check. Rotated braces are excluded from that geometric check and covered by visual inspection.
  Original-asset captures show speckled straps; corrected captures show distinct metal surfaces.
- **Texture sampling:** every shipped workstation texture is opaque. Models now use the mipmapped solid
  layer instead of unmipped cutout. UVs and textures are retained; no texture-source changes were needed.
  Mipmapping addresses distance aliasing, but its separate performance contribution was not isolated.
  The disc renderer respects resource-pack render layers instead of forcing cutout. Normal depth handling is retained.
- **Animation:** interpolation now uses the tick's forward angular displacement, avoiding a backward sweep
  when the stored angle wraps through zero. Acceleration and coasting remain intact.
- **Measured CPU bottleneck:** tinted item quads repeatedly decoded jewelry NBT, allocating gem/material data
  for the same draw. A scoped palette decodes once per displayed item draw. Nested draws restore their caller;
  no item palette survives between frames. Inventory/NBT/config/definition changes therefore remain visible immediately.
  Sampled external gem colors clear when models reload. Models are fetched from the current model manager.
- Static workstation geometry remains baked into chunks; only the disc and displayed items render dynamically.
  Cached finite bounds include the overhanging details. Source inspection found no per-frame block updates or
  workstation particle/sound loops responsible for the measured slowdown; the wheel tick is a few arithmetic
  operations and bench progress synchronization is already thresholded. Crafting feedback is retained.

The geometry defects and CPU cost are distinct causes. This fixture did not reproduce a large slowdown from
empty stations alone.

## Comparable performance evidence

Intel i7-13700HX, RTX 4050 Laptop, NVIDIA 615.71.09; 1280×720, Fancy, four mip levels, render distance 8,
simulation distance 5, VSync off, unlimited frame rate. Animations, displayed items and feedback remain enabled.
Both runs used the same seed (20260925), flat-world construction, coordinates, camera paths, settings and state sequence.
Each scene had five seconds of warmup and six seconds of sampling. The workshop has eight benches and eight wheels,
covering all facings. Close views, approach and orbit are included.

**Baseline qualification:** the task-entry dirty checkout already contained the geometry corrections. The isolated
`/tmp/ij-workstation-baseline` snapshot preserves that state and uses the same benchmark harness. These timings measure
the additional rendering optimization, not the geometry changes. Original-model visual comparisons separately load
the committed models through a test resource pack. No graphics-setting reduction is part of the shipped fix.

Milliseconds, lower is better. CPU is the median time between render-tick start/end events; frame interval is the
mean between starts, including other client work. GPU figures are asynchronous OpenGL timestamp intervals.

| Scene | CPU before | CPU after | Frame before | Frame after | GPU before | GPU after |
|---|---:|---:|---:|---:|---:|---:|
| Empty scene | 0.178 | 0.184 | 1.010 | 1.046 | 0.075 | 0.077 |
| Empty bench | 0.177 | 0.166 | 1.081 | 1.042 | 0.075 | 0.077 |
| Occupied bench | 0.391 | 0.233 | 1.118 | 1.029 | 0.082 | 0.084 |
| Empty wheel | 0.181 | 0.176 | 1.061 | 0.967 | 0.072 | 0.074 |
| Occupied wheel | 0.249 | 0.235 | 0.968 | 0.995 | 0.086 | 0.088 |
| Working wheel | 0.242 | 0.240 | 0.961 | 0.956 | 0.085 | 0.088 |
| Idle workshop | 2.867 | 1.130 | 3.128 | 1.574 | 0.119 | 0.122 |
| Working workshop | 3.563 | 1.400 | 3.876 | 1.768 | 0.123 | 0.129 |
| Approaching workshop | 3.392 | 1.392 | 3.695 | 1.764 | 0.125 | 0.128 |
| Circling workshop | 3.732 | 1.493 | 4.138 | 1.878 | 0.124 | 0.122 |

Working-workshop median render CPU time fell **61%**; mean whole-frame interval fell **54%**. GPU measurements
were essentially unchanged. JFR attributed `JewelryData.load` to 1,274/3,656 render-thread execution samples before
and 62/2,892 after (35% → 2%). Allocation samples independently identify the same decoding path.

Separate full smoke runs measured actual networked crafting with the existing reference bots: cutting median
render CPU **7.781 → 7.466 ms**, setting **5.440 → 4.959 ms**. Both completed with matching client/server verdicts.
These smaller differences are indicative only: minigame sessions have random seeds and this is one matched pair,
not repeated statistical trials. The workshop's working state is staged; it is not 16 concurrent player sessions.
GPU timestamps are not hardware utilization counters. Development-client results do not promise equivalent FPS
in other worlds, modpacks or hardware.

Raw statistics, sample counts, p95s and JFR recordings: [before](../run/smoketest/workstation-before/measurements.json),
[after](../run/smoketest/workstation-after/measurements.json), [before JFR](../run/smoketest/workstation-before/profile.jfr),
[after JFR](../run/smoketest/workstation-after/profile.jfr), [crafting before](../run/smoketest/crafting-before.json),
[crafting after](../run/smoketest/crafting-after.json).

## Russian and runtime validation

English and Russian contain the same **595 keys**, with compatible argument indices/types, percent escapes and
formatting codes. Bundled data, advancements, subtitles and Java literals were audited. Command diagnostics and
carat/time labels now translate. Whole naming templates produce forms such as “Кольцо с рубином” and
“Заготовка кольца из золота”, while preserving a fallback for third-party definitions. No IDs, commands or NBT keys
were translated. Handbook overflow paginates; JEI descriptions use their wrapped height; cutting buttons fit their labels.

Client checks passed in English and Russian, with JEI/Jade and the default integrations, plus a Russian run without
Curios/Immersive Smithing. Checks cover real cutting/setting, inventory transfers, synchronized station contents,
client/server verdicts, generated names, nested tint scopes, in-place NBT changes, all 14 handbook pages, GUI scaling,
and the built-in slots screen. Cyrillic, tooltips, handbook and JEI screenshots were inspected.

The visual matrix covers **four facings × empty/occupied/working** for both stations, stationary, approaching and
circling. Each original/corrected set has 192 frames. Resource reload replaces the disc model, supports a transparent
leaf-texture/cutout-mipped override, restores the original pack, and retains block lighting at night. Screenshots are
review evidence, not an automated pixel-perfect or high-frame-rate flicker detector.

Selected evidence: [original straps](../run/screenshots/legacy_station_matrix_00_75.png),
[corrected straps](../run/screenshots/station_matrix_00_75.png),
[comparison video](../build/reports/workstation/model-comparison.mp4) (four captured frames/second; original left, corrected right),
[resource override](../run/screenshots/station_resource_pack_cutout.png),
[night lighting](../run/screenshots/station_night_lighting.png),
[Russian cut selection](../run/validation/ru_ru/smoketest_cut_select.png),
[Russian tooltips](../run/validation/ru_ru/smoketest_tooltips_shift.png),
[Russian handbook](../run/validation/ru_ru/smoketest_handbook_cutting.png),
[Russian JEI](../run/validation/ru_ru/smoketest_jei_finishing.png),
[Russian equipment](../run/validation/ru_ru_fallback/smoketest_jewelry_slots.png).

## Reproduction and delivery checks

Use `Q=/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh` and
`R=/home/otectus/Projects/ImmersiveJewelry`. These are test-only agents; none ship in the mod JAR.

```sh
IJ_WORKSTATION_BENCHMARK=before "$Q" /tmp/ij-workstation-baseline runClient -PsmokeTest
IJ_WORKSTATION_BENCHMARK=after "$Q" "$R" runClient -PsmokeTest
IJ_SMOKE_PROFILE=before "$Q" /tmp/ij-workstation-baseline runClient -PsmokeTest
IJ_SMOKE_PROFILE=after IJ_SMOKE_LANGUAGE=en_us "$Q" "$R" runClient -PsmokeTest
IJ_SMOKE_LANGUAGE=en_us "$Q" "$R" runClient -PsmokeTest
IJ_SMOKE_LANGUAGE=ru_ru IJ_STATION_VISUALS=1 "$Q" "$R" runClient -PsmokeTest
IJ_STATION_VISUALS_ONLY=1 IJ_SMOKE_PACK=ij-legacy-models IJ_VISUAL_LABEL=legacy "$Q" "$R" runClient -PsmokeTest
IJ_SMOKE_LANGUAGE=ru_ru "$Q" "$R" runClient -PsmokeTest -PnoSmithing -PnoCurios
"$Q" "$R" runGameTestServer
"$Q" "$R" runGameTestServer -PnoSmithing -PnoCurios
"$Q" "$R" runServer
"$Q" "$R" build compileGameTestJava compileSmokeTestJava
python3 tools/generate_resources.py --check
python3 tools/generate_textures.py --check
python3 tools/generate_sounds.py --check
python3 /home/otectus/Projects/.mcmod-tools/check_mod.py "$R"
git diff --check
```

Results:

- `build compileGameTestJava compileSmokeTestJava`: **PASS**, 31 unit tests, no failures or skips.
  [Log](../build/reports/workstation/logs/gradle-ImmersiveJewelry-build-20261005-153825.log).
- `runGameTestServer`: **42/42 required tests passed**, including cutting, setting, insertion/removal, escrow,
  integrations and angle-wrap/coasting bounds. [Log](../build/reports/workstation/logs/gradle-ImmersiveJewelry-runGameTestServer-20261005-152422.log).
  Without Smithing/Curios: **42/42 passed**. [Log](../build/reports/workstation/logs/gradle-ImmersiveJewelry-runGameTestServer-20261005-153609.log).
- Resource/texture/sound generators: **223/223**, **107/107**, **29/29** current. The wheel item model was regenerated
  from the corrected frame and disc. `check_mod.py`: **zero errors/warnings**, 22 expected item-variant notes.
  `git diff --check`: passed.
- Client result JSONs, checked independently of Gradle's exit status: [English](../run/validation/en_us/smoketest-result.json),
  [Russian plus matrix/reload](../run/validation/ru_ru/smoketest-result.json),
  [Russian fallback](../run/validation/ru_ru_fallback/smoketest-result.json),
  [original-model matrix](../run/validation/legacy/smoketest-result.json): **all pass**.
  Final English [log](../build/reports/workstation/logs/gradle-ImmersiveJewelry-runClient-20261005-153932.log);
  Russian matrix [log](../build/reports/workstation/logs/gradle-ImmersiveJewelry-runClient-20261005-152601.log);
  fallback [log](../build/reports/workstation/logs/gradle-ImmersiveJewelry-runClient-20261005-153437.log);
  original-model [log](../build/reports/workstation/logs/gradle-ImmersiveJewelry-runClient-20261005-153235.log).
- Benchmark logs: [before](../build/reports/workstation/logs/gradle-ij-workstation-baseline-runClient-20261005-150704.log),
  [after](../build/reports/workstation/logs/gradle-ImmersiveJewelry-runClient-20261005-151030.log).
  Real-crafting logs: [before](../build/reports/workstation/logs/gradle-ij-workstation-baseline-runClient-20261005-151432.log),
  [after](../build/reports/workstation/logs/gradle-ImmersiveJewelry-runClient-20261005-151716.log).
- `runServer`: attempted, but **ordinary startup blocked by `eula=false`**, not counted as a boot pass.
  [Log](../build/reports/workstation/logs/gradle-ImmersiveJewelry-runServer-20261005-153755.log). Dedicated GameTests did boot and exercise shared code.

Built artifact: [immersive-jewelry-1.1.0.jar](../build/libs/immersive-jewelry-1.1.0.jar), 1,081,575 bytes.
SHA-256: `b29c7a83248ef80b5bc98aa351d7b9dc0af63b472185c47183b2641e793ecbbd`.
The JAR contains all 595 Russian entries, Java 17 class files, models matching the authored assets,
and no smoke/GameTest classes. Player-facing notes:
[changelog](../CHANGELOG.md#workstations-and-russian-localization).

The first resource-reload fixture failed because it used Java's class loader to load an asset. It was corrected to
use Minecraft's resource manager; the complete Russian matrix then passed. Forge's early-window handoff failed once
in the isolated baseline; both measured runs disabled that development-only early window. Preliminary benchmark
runs with accumulated dropped items or desktop mouse interference were discarded before the matched pair above.

Separate remote multiplayer clients, shader/optimization packs, and audible sound playback were not tested.
The runtime's master volume was zero; sound assets and subtitle coverage were checked. Ordinary dedicated-server
startup requires the user's EULA acceptance: [MODMAP.md](../MODMAP.md) says the acceptance file
is “the user's to accept.” No acceptance was inferred from this task. Original runtime options and Forge's early-window
configuration were restored after validation.
