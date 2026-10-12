# Workstation rendering and Russian localization validation

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
