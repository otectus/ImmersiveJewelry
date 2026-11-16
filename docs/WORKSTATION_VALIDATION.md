# Workstation validation

## 1.3.0: Ponder, Flywheel and playerAnimator optional (validated 2026-11-16)

The three client libraries are now `mandatory=false` (ranges unchanged, still enforced when installed) and reached only behind
`client/presentation/OptionalLibraries`. `-PnoPonder` (Ponder and Flywheel) and `-PnoPlayerAnimator` remove them from the dev
runtime; `dependencyInsight` confirmed all three absent with both flags and present without. Evidence:
`build/reports/optional-libraries-1.3.0/`.

**Passed**

- `build`: **PASS**, 85 unit tests. New `LibraryResourcesTest.theOptionalLibrariesAreReachedOnlyBehindTheirGates`: Ponder is
  named only in `client/compat/ponder`, playerAnimator only in `JewelerPoses`/`JewelerLayer`, and every entry call is gated; the
  sides test now requires `mandatory=false` for the three. The jar's `mods.toml` declares all three optional.
- `runGameTestServer`: **68/68**, and **68/68** with `-PnoPonder -PnoPlayerAnimator`.
- `runClient -PsmokeTest` without all three: the whole workflow ran (wheel, bench, folio, drawer, finish, Jade, JEI, handbook),
  **100/101**, no class-loading error or crash. Without the libraries: lessons reported unavailable, the station works with no
  body pose, help from a station opens the handbook. The one failure was a new check of mine
  (`no_ponder_watch_opens_nothing`), which ran while the previous phase's handbook entry was still open; fixed to close it first.
- `IJ_LIBRARIES_ONLY` client runs, after that fix: **without all three 37/37** (Watch opens nothing, help with a lesson opens its
  page instead); **Ponder without playerAnimator 41/41** (all 34 lessons compile, four play with the jeweler unposed);
  **all three 42/42** (clips load, lessons play, the body poses while operating and stops with the lease). No class-loading
  error or crash in any log.

**Not run:** a dedicated server without the jars on its classpath at all (the GameTest server is a dedicated dist, run here
without them); Flywheel present without Ponder (nothing in Jewelry names Flywheel); the greyed Watch button looked at by eye.

## 1.3.0: Diegesis compatibility layer (validated 2026-11-16)

Validated on 2026-11-16, same setup as the beta 11 section below. The declared range is now `[1.0.0-beta.11,)`; this checks that a
Diegesis other than the tested one degrades instead of breaking. No newer Diegesis than beta 11 existed, so the mismatch was
produced by running the mod, compiled against beta 11, on **Diegesis 1.0.0-beta.10** (CurseForge file 9624632; the jar in the
project folder, SHA-256 `2b7c9f5b…666bed`, byte-identical to the cached CurseForge file): it lacks every beta 11 API addition
(station key hooks, drawer surface transforms and visibility, the room query, camera mirroring) and rejects the beta 11 scene
fields, the kind of difference a future release could bring. Command:
`gradlew-quiet.sh . <task> -PdiegesisRuntimeJar=$PWD/diegesis-forge-1.20.1-1.0.0-beta.10.jar "-Pdiegesis_version_range=[1.0.0-beta.10,)"`.
Evidence: `build/reports/diegesis-compat-1.3.0/`.

**Passed**

- `build`: **PASS**, 84 unit tests. New: `DiegesisCompatTest` (7: every probed member exists in the tested release exactly as
  listed; a server probes only the common features; a missing optional method, a missing host interface, a missing core class,
  an interface that gained an abstract method, and a removed hook Jewelry overrides each turn off exactly the right features)
  and `DiegesisUsageTest` (reads Jewelry's compiled classes: every Diegesis method, constructor, field, implemented interface
  and overridden hook they use is probed; it found 19 unprobed uses on its first run, now listed).
- `runGameTestServer` on beta 11: **68/68**, and **68/68** with `-PnoSmithing -PnoCurios`. New: every fallback level of both
  scenes parses with the library and keeps every node the behaviours use; the probe and the registered stations agree; a bench
  without the library's host keeps everything and the library's tag.
- `runGameTestServer` **on beta 10: 68/68**. Probe: `stations`, `native_storage` on; the wheel scene reads as written, the bench
  scene at `PLAIN_SLOTS`.
- `runClient -PsmokeTest` **on beta 10: pass, 104/104 checks, no linkage error or crash** in the log
  ([result](../build/reports/diegesis-compat-1.3.0/smoketest-result-on-beta10.json),
  [log](../build/reports/diegesis-compat-1.3.0/logs/runClient-on-beta10.log)). Start-up line: on `stations, native_storage,
  client`; off `key_hooks, camera_mirror, drawer_motion, room_query`, each with its missing members. The whole workflow ran on the
  fallbacks: station keys through Forge's screen events (cutting, setting, finishing and the folio's search, all by keys),
  the drawer as a list and shift-clicked into without the room query, the working-side option ignored with the station usable,
  the bench scene simplified once (`PLAIN_SLOTS` plus the client's own removal of the drawer's surfaces), the handbook and the
  lessons.
- `runClient -PsmokeTest` on beta 11 (the release path, through the guards): **pass, 106/106 checks**, all eight features on,
  every beta 11 check passing ([result](../build/reports/diegesis-compat-1.3.0/smoketest-result-beta11.json),
  [log](../build/reports/diegesis-compat-1.3.0/logs/runClient-beta11.log)).

**Failed, then fixed**

- First GameTest run: 5 failures. The bench block's own `newBlockEntity` made a plain bench directly, so a world's benches were
  never library hosts and native storage was silently off (the `isBound` gate kept it safe). It now uses the factory.
- One `-PnoSmithing -PnoCurios` run: two older break tests counted every dropped item twice (it passed on the two reruns). All
  68 tests run as one batch on a grid, and those tests counted any item within two blocks; items from neighbouring tests can
  land there. They now count only items spawned that tick at the station's own drop point (`TestSupport.freshDrops`), which
  still sees a real double drop. [Log](../build/reports/diegesis-compat-1.3.0/logs/runGameTestServer-noSmithing-noCurios-intermittent-failure.log).
- The beta 10 client log showed the "scene simplified" warning repeated on every client-option change; it is now logged once a
  session.

**Not run, not claimed**

- A Diegesis newer than beta 11 (none exists yet). Beta 10 exercises missing members and refused scene fields; a release that
  changes a method's behaviour without changing its signature is not detectable by the probe, and a renamed `StationHost` or
  changed `StationState` constructor was checked by unit tests and the plain-bench GameTest, not by a real build.
- A Diegesis missing the core (stations or client) in a real game: covered by the unit tests' simulated lookups and by the
  guarded code paths, not by a launched game with such a jar.
- The `no_scene` and `incompatible` in-game messages were not triggered in a run.

## 1.3.0: Diegesis 1.0.0-beta.11 (validated 2026-11-16)

Validated on 2026-11-16 (Linux, Java 17, Forge 47.4.23; Curios 5.14.1 and Immersive Smithing 1.0.0 in the dev runtime unless
noted; Patchouli, Ponder, Flywheel, GeckoLib and playerAnimator as in 1.2.2) against Diegesis **1.0.0-beta.11**, CurseForge
file 9643618, whose resolved jar's SHA-256 matches the GitHub release asset
(`22b541fb23b41988ac2cd75b52ca8e93e868050fbe56831968f412ee7d844f16`). Jewelry 1.2.2 (`7ca0b4f`) was the starting point.
Evidence is in `build/reports/diegesis-beta11-1.3.0/`. Earlier sections below are history, not results for this release.

**Baseline (before any change)**

- 1.2.2 with beta 10: `build` **PASS**. [Log](../build/reports/diegesis-beta11-1.3.0/logs/baseline-1.2.2-beta10-build.log).
- 1.2.2 code compiled against beta 11 (only the pinned properties changed): `compileJava` PASS, `runGameTestServer` **56/56**.
  [Log](../build/reports/diegesis-beta11-1.3.0/logs/baseline-1.2.2-beta11-runGameTestServer.log).

**Passed (final tree)**

- `build`: **PASS**, 76 unit tests, no failures or skips (new: `StorageProtocolTest`, 4: command ordinals and the sequenced
  wire format, the argument budget for the largest legitimate commands and a refused oversized one, the calm/lamp comfort
  transform). `build/libs/immersive-jewelry-1.3.0.jar`; its `mods.toml` requires Diegesis `[1.0.0-beta.11]` on both sides.
- `runGameTestServer`: **65/65**, and **65/65** with `-PnoSmithing -PnoCurios`. Changed contracts: `DiegesisGameTests` (beta 10
  refused; the bench has exactly the `supplies` and `staging` groups and the schema instance its host state uses, the wheel
  none; the jars are camera-counted slots and no longer `takes_items`; the drawer's `placing`; normal, calm and lamp-off scenes
  after `SceneComfort` all parse with the library's codec). New `BenchStorageGameTests` (8): the native-slot gate (no lease,
  shut drawer, wrong item, wrong group or index all refused), the host state as a window on the bench's arrays (no second
  inventory in `save()`), staging and moving with exact counts and NBT (creative moves too), stale/repeated/flooding/malformed
  commands, exact preparation (one from a stack of three, best cut first, one gem only, no work started), save/load/drop
  (format 2 without a clamp read as format 2, 1.2 benches get an empty shut drawer, stray poses dropped, every stack dropped
  once), the client-copy generation path, and sneak-use retrieval from a shut drawer.
- Scenes against the pinned beta 11 JSON Schema (`docs/schema/scene.schema.json` at `ae9e324`, Draft 2020-12, `jsonschema`):
  **0 errors** for both files.
- Resource checks: all six generators `--check` up to date (book: 61 files, 218 keys in 5 locales; 37 entries); `check_mod.py`
  reports only the documented item sub-models.
- `runClient -PsmokeTest`, third run on the final tree: **pass, 106/106 checks**
  ([result](../build/reports/diegesis-beta11-1.3.0/smoketest-result-run3.json),
  [log](../build/reports/diegesis-beta11-1.3.0/logs/runClient-smoke-run3.log), screenshots in
  `build/reports/diegesis-beta11-1.3.0/screenshots/`). The whole 1.2.2 workflow (wheel, bench, prongs, finish, loupe, JEI child
  screen, Jade, handbook, slots, accessibility, libraries) and 19 new checks driven through Minecraft's keyboard handler and
  character callback and the behaviour Diegesis holds: the folio's search field takes `/`, then E, B, H, Q, R, D and three
  Unicode characters (one outside the BMP) as text with the folio, the station and the inventory unchanged; the first Escape
  leaves the field, the second closes the folio; the drawer list opens by key and opens the drawer; a stack the work area has
  no place for is shift-clicked in after `hasRoomFor`, lands in staging slot 0, and the host state reads the bench's array;
  the list moves it back to the inventory and shuts the drawer; the left working side mirrors the camera and switches back;
  the new handbook entry opens and the new lesson plays (at Ponder's default zoom the drawer is small behind the jeweler).
  Client frame interval while focused: median 8.4 ms, p95 13.7 ms, p99 18.1 ms (vsync off, 120 fps cap); render thread
  median 3.0 ms, p95 5.6 ms; 20.0 client ticks/s. One run on one desktop; not a profile of the drawer's physics alone.
  No error from the mod or Diegesis in the client log.

**Failed, then fixed**

- First client run: `tweezers_seat` timed out (and the bench phases after it). Seating a stone sent its position's generation,
  which pushed the SEAT arguments past the 512-byte budget (`CompoundTag.sizeInBytes` charges about 80 bytes an entry), so the
  server dropped it. Reproduced headlessly (`aSyncedCopyCarriesTheGenerationsCommandsName`), fixed by raising the budget to
  1024 deliberately (decoder limit four times that, as before) with a unit test for the largest legitimate commands; refusals
  are now logged with their reason (rate-limited). [Stopped run 1](../build/reports/diegesis-beta11-1.3.0/logs/runClient-smoke-run1-stopped.log),
  [GameTest reproduction](../build/reports/diegesis-beta11-1.3.0/logs/runGameTestServer-argument-budget-failure.log).
- Second client run: **pass, 104/104 checks** ([result](../build/reports/diegesis-beta11-1.3.0/smoketest-result-run2.json),
  [log](../build/reports/diegesis-beta11-1.3.0/logs/runClient-smoke-run2.log)). Its screenshots showed the open drawer's pull
  under the caption bar, and the "options apply next time" notice after a working-side change, which applies at once. The
  drawer now slides 2.4 px (surfaces z 1.25-3.45) and the notice shows only when a scene option changed.

### Beta 11 adoption checklist (handoff section 2)

| ID | Disposition | Where |
|---|---|---|
| B11-01 physics | **Adopted** on the staging drawer only (gentle values, calm option drops it); the tray, clamp, sockets and dock are never physics bodies | `jewelers_bench.json` `placing.physics`, `SceneComfort` |
| B11-02 weighted drag/turn | **Adopted** through the library's defaults (`turn_power` 1.2, `max_speed` 40); presentation only, nothing reads it | scene |
| B11-03 `placing.counts` | **Adopted**: `hover` for staged stacks; the jars use slot counts (`always`) | scene |
| B11-04 `BLOCK_OPPOSITE` | **Not applicable**: both blocks' `facing` names their front; `BLOCK` kept | `JewelryStations` |
| B11-05 `setSurfaceTransform` | **Adopted**: both drawer surfaces slide with one eased progress (translation only, reused matrix); the handle node moves with it | `BenchBehavior.applyDrawer` |
| B11-06 `setPlacingVisible` / `setSurfaceVisible` | **Adopted**: placing hidden every frame while shut; surfaces shown/hidden on change and put back in `onSceneReady`; the server refuses a shut drawer too | `BenchBehavior`, `WorkstationLogic` |
| B11-07 `mirrorCamera` | **Adopted**: shots authored from the front right (bench x 6.5, wheel x 7.0), `workingSide` LEFT mirrors across x = 8; pointer mapping from the effective camera | `SceneLoader.camera(id, mirror)`, `cameraChanged` |
| B11-08 `hasRoomFor` | **Adopted** before a shift-click into the drawer; the server moves only what fits, the rest stays | `BenchBehavior.onInventoryShiftClick`, `BenchOperations.stageIn` |
| B11-09 `frame_pose` false | **Adopted** (explicit) for staged stacks; checked by eye in the drawer screenshot only | scene |
| B11-10 `hover_lift` 0 | **Adopted** (explicit) | scene |
| B11-11 `onKey` / `onChar` | **Adopted**: station keys through `onKey` (then a recipe-viewer lookup); `onChar` unused (no field without an overlay); a scoped Forge bridge for the stations' overlays (keys, characters) and every release | `WorkstationBehavior`, `WorkstationClient` |
| B11-12 `MenuBinding.alsoAccepts` | **Not applicable**: SERVER stations, no vanilla menu | - |
| B11-13 `count_facing: camera` | **Adopted** on the three jar slots; no output bindings (the clamp's result is Jewelry's) | scene |
| B11-14 Mouse Tweaks gestures | **Native** on the jars and the drawer (the library's); not exercised with Mouse Tweaks installed | - |
| B11-15 wheel / middle drag | **Native** on the jars and the drawer; `onScroll` still turns a held stone or the collar only where the station owns the gesture | `WorkstationBehavior.onScroll` |
| B11-16 scene lights | **Adopted**: a steady lamp (`flicker` 0, level 9), `workLamp` option; it does not relight painter/GeckoLib parts or the world (documented) | scene `lamp`, `BenchPainter.lamp` |
| B11-17 projection fix | **Inherited**; not exercised at a large render distance | - |
| B11-18 hidden-block return | **Inherited**; the stations never hide their block, both renderers keep the `isSceneDrawn` handoff | renderers |
| B11-19 neighbour faces | **Inherited**; no local workaround existed | - |
| B11-20 counts over particles | **Inherited**; only counts use it (no label shows a shut drawer's contents) | - |
| B11-21 highlight texture | **Inherited** (native slot highlights on the jars) | - |
| B11-22 carried stacks before upright stations | **Inherited**; `carry_size` kept | scene |
| B11-23 placement fixes | **Inherited**; the drawer is a transformed surface pair | - |

**Not run** (need people, other machines or other mod stacks; not claimed)

- Two real clients and a bystander on a dedicated server (remote drawer motion and staged poses, late joiners, bursty
  latency); a dedicated `runServer` boot with only the server-side libraries (still needs the user's `run/eula.txt`; the
  GameTest server is a dedicated dist but keeps the client libraries on its classpath).
- Mouse Tweaks installed (flags off/on after restart), InvMove, Embeddium/Oculus with shaders, a white-concrete-replacing
  resource pack, a large render distance or a distant-horizons renderer, and a laptop GPU.
- Native gestures by real mouse input in the drawer and jars (drag, turn, crowd, middle-drag spreading, the wheel's one-in
  and one-back including a full inventory), and placing a carried stack onto a drawer surface: the client run drove the
  drawer through its list, the shift-click callback and `hasRoomFor`, not through pointer gestures on the surfaces.
- Pointer work (a stroke on the piece) with the camera mirrored, in four block facings: the client run checked that the
  mirror switches on and off with nothing pressed; the mapping itself is the same code path for both sides.
- The folio's Prepare page by mouse in the real client (the server side is covered by GameTests), IME composition (only
  committed characters, including a supplementary one, were sent), a held H in the search field (only taps were sent),
  remapped keys, and narrator.
- Frame-time profiling of a busy workshop with four physics stacks per bench, calm against natural; the client run reports
  only the whole-run frame and render-thread times (above).

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
