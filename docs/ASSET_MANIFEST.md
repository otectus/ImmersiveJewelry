# Immersive Jewelry - library and asset manifest (1.2.2)

What each presentation library owns, the assets it reads, their editable sources and the names the code depends on.
Versions and ranges live only in `gradle.properties`; `mods.toml` takes them from there.

## Dependencies

| Library | Artifact (Maven) | Sides | Used by |
|---|---|---|---|
| Diegesis | `curse.maven:diegesis-1790227:<diegesis_curse_file>` | both | `workstation/station`, `client/workstation` (since 1.2.0) |
| Patchouli | `vazkii.patchouli:Patchouli:<patchouli_version>` (`:api` to compile) | both | `client/guide`, `item/HandbookItem` (client path) |
| GeckoLib 4 | `software.bernie.geckolib:geckolib-forge-1.20.1:<geckolib_version>` (mclib compile-only) | both | `blockentity/StationBlockEntity` (animatable), `client/presentation/geo` |
| Ponder | `net.createmod.ponder:Ponder-Forge-1.20.1:<ponder_version>` (not transitive) | client | `client/compat/ponder` |
| Flywheel | `dev.engine-room.flywheel:flywheel-forge-1.20.1:<flywheel_version>` (runtime) | client | required by Ponder |
| playerAnimator | `dev.kosmx.player-anim:player-animation-lib-forge:<playeranimator_version>` | client | `client/presentation/anim`, `client/compat/ponder/JewelerActor` |

Repositories have content filters (`build.gradle`). No library is shaded. Common code imports none of Patchouli, Ponder or
playerAnimator (`LibraryResourcesTest.theLibrariesAreDeclaredWithTheirSides`).

## GeckoLib rigs

Source: `art/jewelry_rigs.py` (authored in the painters' pixels; x is mirrored on export to GeckoLib's convention).
Export and check: `python3 tools/generate_rigs.py --write|--check`. Rigs are posed on every render by `RigClip` at a
phase named by synced state; no GeckoLib controller or model-wide clock is used. The animatable is the station's block
entity (one cache per station, separate ones in Ponder levels); tools drawn without a station use a detached animatable.

| Rig | Files | Bones | Clips (sampled at) | Drawn by |
|---|---|---|---|---|
| `lapidary_wheel` | `geo/lapidary_wheel.geo.json`, `animations/lapidary_wheel.animation.json`, `textures/rig/lapidary_wheel.png` | `root`, `lap` (tinted by the lap's tier), `hub`, `spindle`, `lever` | `lap_seat` (ticks since the lap changed), `lever_pull` (lever pull 0..1) | `WheelPainter` via `JewelryRigs.wheel` |
| `jewelers_bench` | `geo/jewelers_bench.geo.json`, `animations/...`, `textures/rig/jewelers_bench.png` | `root`, `vise`, `screw`, `jaw_front`, `jaw_rear`, `posts_circlet`, `posts_bracelet` | `clamp_close` (ticks since the clamp changed; frame 0 for an empty vise) | `BenchPainter.clamp` via `JewelryRigs.vise` |
| `bench_tools` | `geo/bench_tools.geo.json`, `animations/...`, `textures/rig/bench_tools.png`, `bench_tools_fine.png` (Fine Pliers) | `root`, `pliers` (`jaw_left`, `jaw_right`), `tweezers` (`arm_left`, `arm_right`), `pusher`, `burnisher`, `brush` (`bristles`), `cloth` (`pad`) | `pliers_squeeze` (1 - opening), `brush_dab`, `cloth_rub` (contact) | `Tools.*` via `JewelryRigs.tool` |

A rig that is missing or fails to draw is reported once and the painter draws its previous plain parts instead.
The lapidary wheel's quill, dop, stone, specimens and cup, and every piece of jewelry on the bench, stay procedural: they
are the work and its contact geometry (`WheelPainter`, `FrameRenderer`, `GemRenderer`).

## playerAnimator clips

Source: `art/jewelry_poses.py`; export with `tools/generate_rigs.py`. Files: `assets/immersive_jewelry/player_animation/<id>.json`
and `<id>_reduced.json`. Keys only `head`, `torso`, `rightArm`, `leftArm`. Layer `immersive_jewelry:work`, priority 1100,
first person disabled. Clips are chosen by `PoseRules` from the station's synced state.

| Clip | Length (ticks) | Loop | When |
|---|---|---|---|
| `wheel_ready` | 20 | yes | operating a wheel, quill lifted |
| `wheel_grind` | 24 | yes | the stone on the lap (synced contact) |
| `wheel_lift` | 10 | no | contact ended |
| `wheel_cool` | 16 | no | Ponder (dipping) |
| `wheel_mount` | 16 | no | a stone put on or taken off |
| `lap_change` | 20 | no | the lap changed |
| `inspect_loupe` | 20 | yes | reserved for the loupe |
| `bench_ready` | 24 | yes | operating a bench, nothing gripped |
| `tweezers_seat` | 30 | yes | a stone seated |
| `pliers_squeeze` | 16 | yes | a prong gripped (setting, retightening) |
| `bezel_push` | 32 | yes | the pusher on a bezel |
| `polish_wipe` | 20 | yes | polishing, or the patina wipe |
| `burnish_pass` | 24 | yes | burnishing |
| `patina_dab` | 14 | yes | patina brush |
| `pry_stone` | 20 | yes | taking stones out |
| `repair_true` | 22 | yes | repairing the frame |
| `bench_place` | 14 | no | a piece put in the clamp |
| `bench_collect` | 14 | no | a piece taken from the clamp |
| `inspect_piece` | 30 | no | a piece finished |

## Patchouli book

Book `immersive_jewelry:jewelers_handbook`: `data/immersive_jewelry/patchouli_books/jewelers_handbook/book.json` (i18n,
custom book item, not generated), structure under `assets/immersive_jewelry/patchouli_books/jewelers_handbook/en_us/`.
Structure source: `tools/generate_book.py` (OUTLINE). Text: lang keys `book.immersive_jewelry.*`, the fourteen 1.2
chapters as `handbook.immersive_jewelry.<chapter>.<n>`. 11 categories, 36 entries.

Custom components (`client/guide`, page types `immersive_jewelry:<template>`): `gem_card` (`gem` or `generic`),
`cut_card` (`cut`), `design_card` (`design` or `others`), `finish_card` (`finish`), `metal_card` (`show`: `routes` or
`metals`), `policy_card` (`topic`: `designs`, `wearing`, `care`, `workshop`), `piece_card`, `effect_card`, `care_card`,
`keys_card`, `tools_card`, `watch` (`lesson`, `entry`, `text`).

## Ponder lessons

Plugin `JewelryPonderPlugin`; 33 lessons (`client/guide/Lessons`), scene ids `immersive_jewelry:<lesson>`, hidden deep-link
tags `immersive_jewelry:lesson/<lesson>`, index tags `first_piece`, `lapidary`, `setting`, `finishing`, `care`,
`equipment`. Structures (`tools/generate_ponder.py`): `ponder/wheel.nbt`, `ponder/bench.nbt`, `ponder/workshop.nbt`.
Captions: `immersive_jewelry.ponder.shared.<lesson>.<n>`, headers `immersive_jewelry.ponder.<lesson>.header`, tags
`immersive_jewelry.ponder.tag.<tag>[.description]`.

## Worn jewelry

`client/presentation/worn`: one renderer (`WornJewelryRenderer`) for the built-in slots' layer and Curios'
`ICurioRenderer`. Built-in slot looks reach other clients as `WornAppearancePacket` (protocol 4): at most 8 sanitized
stacks (item, form, metal, stones, design, finish, workmanship, integrity).

## Locales

`en_us`, `es_es`, `pt_br`, `ru_ru`, `zh_cn` carry the same keys (`LibraryResourcesTest.everyLocaleCarriesTheSameKeys`).
