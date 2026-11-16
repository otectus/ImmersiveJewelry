# Immersive Jewelry - Architecture and Decisions

The design authority is `Immersive_Jewelry_Development_Spec.md`; for the hands-on stations of 1.2.0 it is the
cinematic hands-on overhaul handoff (`Immersive_Jewelry_Cinematic_Hands_On_Overhaul_Handoff.md`, cited as "handoff"
below), whose requirements the spec's workstation chapters now follow. This file records how both are implemented, and
every place where the implementation deliberately departs from them (spec section 77, rule 10). Sister mods and
architectural baseline: `../ImmersiveSmithing` and `../ImmersiveTanning` (same package layout, config split, GameTest
and smoke-test setup). The workstation's validation matrix is in `WORKSTATION_VALIDATION.md`.

## Identity and migration (spec 50)

- Mod id `immersive_jewelry`, package `com.otectus.immersivejewelry`. Jewelcraft Reforged (`jewelcraft`, package
  `net.salju.jewelcraft`) was inspected before deciding: it has only four wearable ids (`material_ring`,
  `material_amulet`, `material_bracelet`, `material_circlet`), the `jeweler_bench` block/item/POI, the `jeweler`
  profession and a decorative `jewelry_box`. Its 54 older fixed ids were already removed by Jewelcraft Reforged itself.
  Keeping `jewelcraft` would force the new mod to impersonate a different mod's namespace and would clash with the
  sister mods' naming, so a new id plus migration is used instead:
  - Forge registry aliases (`ForgeRegistry#addAlias`, registered during `RegisterEvent`) make every string lookup of an
    old id resolve to the new entry: item stacks in inventories, chests and Curios slots, block states in chunk
    palettes, POI records and villager professions.
  - `MissingMappingsEvent` remaps the old ids in the world's saved registry snapshot, so the world opens without the
    missing-entries warning.
  - `JewelryItem#verifyTagAfterLoad` converts the legacy `JewelcraftMaterials {metal, gem, inset}` compound into the
    new component model on load (craftsmanship 0.65 per stage, round cut, polished finish, `LegacyConverted`). Names,
    enchantments and foreign NBT are kept. The conversion is idempotent and can be disabled (common config).
  - `jewelcraft:jewelry_box` has no successor and becomes a chest.

## Responsibility split with Immersive Smithing (spec 2.4, 5)

- Immersive Jewelry never heats, forges or shapes metal. With Immersive Smithing loaded and the integration enabled,
  jewelry blanks are Immersive Smithing recipes. Metals with a jewelry metal definition have datapack recipes
  (`recipes/smithing/<form>/<ns>/<metal>.json`, condition `immersive_jewelry:smithing_blanks` plus a non-empty ingot
  tag), which sync to clients and JEI. `SmithingBlankRecipes` (an `ISmithingRecipeProvider`) adds the same recipe for
  every other family Smithing knows about - datapack, compat or auto-detected `forge:ingots/*` - so any metal can
  become a blank without extra data; in Immersive Smithing 1.0.0 provider recipes work at the forge but are not
  inserted into the recipe manager, so those do not appear in JEI.
- Small workpieces: the recipes use the forge pattern `immersive_jewelry:small_workpiece` (two or three phases,
  narrower zone) and per-form anvil patterns (`immersive_jewelry:ring_blank`, ...) with fewer, smaller, shorter
  targets. They are Smithing data files under `data/immersive_jewelry/immersive_smithing/` and inert without Smithing.
- The quench writes Smithing's own quality compound. `ItemSmithedEvent` then records the material family, the family's
  tint and display name, and `SmithingQuality = overallScore / 100` (Faulty caps it at 0.15) in the blank's
  `immersive_jewelry` compound. The finished jewelry keeps a copy of the smithing compound (`Smithing`).
- Blanks report their metal to Smithing's recycling (`IRecyclingValueProvider`); finished jewelry is excluded so a
  set gem is never melted by accident.
- Fallback (spec 5.4): crafting recipes for blanks carry the condition `immersive_jewelry:fallback_blanks`, true only
  when Smithing is absent or its integration is disabled, `requireImmersiveSmithing` is false and
  `enableFallbackBlankRecipes` is true (all COMMON config, so conditions see them at the first datapack load).
  Fallback blanks get `fallbackSmithingQuality` (default 0.45). Every Smithing class is referenced only from
  `integration/smithing`, which is reached after a `ModList` check.

## Data-driven definitions (spec 25-29, 46, 73)

Definitions live at `data/<namespace>/immersive_jewelry/<kind>/<path>.json` and get the id `<namespace>:<path>`, the
same convention Immersive Smithing uses (`data/<ns>/immersive_smithing/materials/`). This follows spec 73 (third-party
namespaces) rather than the single-namespace paths of spec 25. Kinds: `gems`, `gem_cuts`, `finishes`,
`jewelry_forms`, `traits`, `metals`.

- `metals` replaces the spec's `material_overrides`: it is where a metal family gets its jewelry identity. Families
  without a definition use a generic profile built from Smithing's tint and name.
- Designs (spec 56) are a real recipe type, `immersive_jewelry:jewelry_design`, so they sync to clients, show in JEI
  and honour Forge conditions.
- All definitions are parsed with Codecs, resolved on the server, and sent to clients as JSON after every reload
  (`SyncDefinitionsPacket` from `OnDatapackSyncEvent`), so auto-detected gems reach the client like file-defined ones.
- Automatic gem detection (spec 45): every `forge:gems/<name>` tag not covered by a definition becomes the generic gem
  `forge:<name>` (moderate hardness, the neutral `immersive_jewelry:resonance` trait). Its colour is sampled on the
  client from the source item's sprite when possible.

## Gemstones (spec 8-11)

- Two registered gem items: `rough_gem` and `cut_gem`, both carrying `GemData` (gem, size, original size, natural
  quality, inclusions, cut, cut quality, damage, quality cap). Quality is data, never a separate id.
- A vanilla or modded source item (diamond, `#forge:gems/ruby`) is itself the rough stone. When it is first mounted on
  the Lapidary Wheel the server appraises it (rolls size, natural quality and inclusions). From then on it stays a
  `rough_gem` with fixed properties, so cancelling a cut can never be used to re-roll a stone.
- Nine built-in gems. Diamond, emerald, amethyst, lapis and quartz are vanilla. Ruby, sapphire, topaz and onyx match
  `forge:gems/*` tags from other mods and also appear as appraised rough stones in treasure loot and Jeweler trades,
  so the mod is complete on its own and joins other gem mods when present.
- Cuts: round, brilliant, emerald, cabochon (spec 11). Quality bands Ruined/Chipped/Acceptable/Fine/Excellent/Flawless
  with configurable thresholds. The spec's list also names "Rough", but its thresholds define six bands, and Rough
  would collide with the rough (uncut) stone; it is omitted.

## Hands-on workstations (spec 6, 10, 12, 33-35, 61-63; handoff)

Since 1.2.0 the Lapidary Wheel and the Jeweler's Bench are worked in the world: the player steps up to the block, the
camera settles over it and the work is done with the station's own handle, tools and hands. The 1.1 minigame screens,
sessions and their packets are gone; one process engine remains.

- **Diegesis behind a thin adapter (handoff 10).** Diegesis (Forge; `1.0.0-beta.10`, CurseForge file 9624632, since
  1.2.1, `1.0.0-beta.9` in 1.2.0; a required dependency on both sides, range in `gradle.properties`) supplies focus,
  camera framing, glances, captions, scene nodes, picking and pointer strokes. `workstation/station/JewelryStations`
  registers the two `StationType`s (block orientation, exclusive, reach 5, `openWhen` refusing sneaking players,
  stations someone else works and items that are put on the station by walking up); `WorkstationLogic` turns opening and
  closing into leases and refuses every library slot and custom action. The client half is `client/workstation`: one
  `StationBehavior` per station (`WheelBehavior`, `BenchBehavior`, base `WorkstationBehavior`) that paints the moving
  parts through the scene's `work` node, maps strokes and clicks to controls, and runs the keyboard frontend. Only
  public `com.pockyl.diegesis.api` types are used.
- **Diegesis 1.0.0-beta.10 features in use (1.2.1).** Placement nodes (wheel: `handle`, `stone`, `lap`; bench: `piece` (the
  clamp), `dock`, `tray_0`-`tray_7`, `supply_0`-`supply_2`) are `takes_items`: a carried stack hovers them, the
  behaviour draws where it would go (`WheelPainter`/`BenchPainter.placeHint`, answered by `WheelOperations.placement`
  and `BenchOperations.accepts`, which mirror the custody moves; GameTests hold the two in agreement), and the click is
  a Jewelry `PLACE`. The mouse wheel reaches the station through `StationBehavior.onScroll` with the node under the
  cursor (Diegesis gives it to an open overlay first, the folio scrolls itself). The compact drawer lists only what the
  station can use (`StationView.setInventoryFilter`; client option `drawerShowsEverything` lists everything, handoff
  4.1). A carried stack is drawn in the scene only at least 22 scene pixels from the camera (Diegesis'
  `NEAREST_CARRY`), nearer as the cursor icon: over the stations' places it stays the icon, and farther back (the
  wheel's handle and the far part of the lap, only the back edge of the bench) `carry_size` keeps it near the size of
  the stones (wheel 1.5; bench 1, the smallest allowed) instead of the size of the last stack carried (4.5 at first).
  A child screen another mod registers can now open over a station without ending it: the station's keys therefore
  reach it only while its own screen is shown, and everything held is let go while anything else covers it
  (`WorkstationClient`). JEI uses this as the library documents for recipe viewers' keys: JEI itself takes no keys or
  clicks on the station's screen (it has no GUI there), so the station's key handler first offers a key to the
  station, and a key the station has no use for at that moment, pressed over a stack (a drawer stack from
  `DiegesisClient.hoveredStack`, or a real stack the station shows), goes to `StationLookups`, which opens JEI's page
  for it when it is JEI's show-recipe or show-uses key. The page is a registered child screen, and closing it returns
  to the station. The turn key (R, also JEI's default show-recipe key) is the station's while the tweezers hold a stone
  or the wheel's stone can be planned; otherwise, over a stack, it looks up. Not used, by design: menu stations
  (`MenuBinding`, the stations have no vanilla menu and keep one custody path), `hidesBlocks` and `moveCamera`
  (single-block, single-height stations), the drawer's `layout: hotbar` (a permanent grid the brief rules out, and it
  would overlap the bottom captions), `count_facing` (no library slots), `setOutlineSeenOnly` (Diegesis outlines only a
  node's visual, slot or output item; the stations' nodes are painted pick zones), a recipe viewer's overlay laid round
  the station (`toScreen`, `stationScreenClass`: a permanent grid again, and JEI's own input on the station's screen
  would compete with the station's keys) and an earlier `interactive_at` (clicks count once the camera has arrived,
  the default).
- **One custody path.** Every item move (walk-up placement, taking things, staging stones from the inventory, taking
  a result) goes through `WheelOperations`/`BenchOperations`, from the block's use or from a `WorkCommandPacket`; the
  stacks are real server stacks in the block entities and the scene only shows them.
- **Work orders (handoff 7).** A cut is a `CutWork` (`workstation/order`) in the stone's own NBT (`Work`, schema 1:
  cut, recut flag, seed, `FacetParams`, `FacetSim` state, revision, lap wear), so it travels with the stone. A bench job
  is a `BenchWork` in the bench (or, packed, in an `unfinished_jewelry` carrier with the frame and stones): kind
  (assemble, retighten, remove, repair, refinish), stage (plan, setting, finishing), design, finish, setting and
  surface simulations, payment flags. Everything before the first irreversible act (the first closure, a lifted stone,
  the first effective finishing stroke) can be undone for free; costs are taken at first effective use, pliers wear at
  the first closure, a lap at the first contact. Completion replaces the work by its result exactly once.
- **Operator leases (handoff 8).** `WorkSessionManager` grants one lease per station and per player (token, input
  step, sequence) when the scene opens, and ends it on every way out (leaving, distance, damage, teleport, dimension
  change, death, logout, the station breaking or unloading, a reload, the server stopping): tools lift, the work stays.
  Leases are never saved. The scene path and the block-use path respect the same lease; scene input also passes
  `StationAccess` (alive, not a spectator, the station loaded and within reach, the vanilla interaction check, and a line
  of sight for discrete actions).
- **Fixed steps and bounded input (handoff 9).** Controls are sampled every 20 ms (`InputRecorder`, at most 12 steps
  per frame) into `WorkSample`s (8 bytes) and sent in batches of five, about ten a second (`WorkInputPacket`, at most
  ten samples after a hitch). The
  lease admits a batch only after duplicate, overlap and pacing checks against a token budget refilled by real time
  (50 steps/s, 5 % slack, a backlog of `1000 ms + latency allowance + timingToleranceMs`); refused steps are dropped,
  never applied later. A heartbeat lifts a tool when input goes quiet (400 ms plus the latency allowance). Discrete
  actions are `WorkCommandPacket`s (size-checked arguments). No packet carries a score or an item.
- **Deterministic simulations.** `FacetSim` (depth per face, sweep bins, rocking, scratches, heat and chips, the
  dop's turn, a collar that only turns lifted), `SettingSim` (closure per point, seat, grip, overbend, bezel push,
  removal) and `SurfaceSim` (coverage masks per tool and phase) step on `float`/`StrictMath` from bounded inputs. The
  server steps them authoritatively; the operator's client predicts from the last synced state and replays
  unacknowledged steps on top of it (`ackStep`), so the hands respond at once and corrections are small. Quality is
  read from the final physical state.
- **Data-driven tuning (handoff 12).** Optional `work_profiles` (`data/<ns>/immersive_jewelry/work_profiles/`,
  strict parser, schema 1) scale pace and tolerances per interaction; designs, cuts and finishes may name one, the
  shipped `immersive_jewelry:defaults/<interaction>` profiles repeat the built-in numbers. A profile cannot change what
  an operation produces.
- **Rendering (handoff 14).** Moving parts, stones, frames, prongs and tools are drawn procedurally from the
  block-atlas sprite `block/workstation_plain` (`client/workstation/render`): convex-clipped gem polyhedra per cut,
  frame bodies parameterised for surface coverage, cached geometry. The block entity renderers draw the same painters
  as an idle station and step aside while the scene is drawn (`DiegesisClient.isSceneDrawn`), so one renderer owns
  each object. Observers see the synced work state (closures, depths, coverage), not local inventions.
- **Legacy (1.1) benches** load once into the new layout: the workpiece into the clamp, escrowed stones into the tray,
  escrowed finish materials into the supplies; what does not fit drops at the bench.
- Finishing is chosen in the folio (spec 13) and applied by hand; a finish can cost a cheap consumable (data).

## Presentation libraries (1.2.2; library handoff)

1.2.2 implements `Immersive_Jewelry_Patchouli_Ponder_GeckoLib_PlayerAnimator_Diegesis_Integration_Handoff.md` ("library
handoff" below) on top of the 1.2.x hands-on stations: that handoff's first case, so the Diegesis adapter, the work
orders, leases, custody and save schemas above are extended, not replaced. Inventory of assets, bones, clips and keys:
`ASSET_MANIFEST.md`.

- **One owner per concern.** Diegesis: focus, camera, picking, captions and scene lifecycle (unchanged). GeckoLib: the
  articulated meshes of the lap, lever, vise and bench tools. playerAnimator: the third-person body. Patchouli: reading.
  Ponder: demonstrations. The work, items and effects stay with the Jewelry code; no library callback, marker or render
  changes them.
- **GeckoLib** (`client/presentation/geo`). The station block entities are `GeoBlockEntity`s with no controllers (one
  instance cache per station; Ponder's stations are separate instances). `RigRenderer` draws a rig in the painter's own
  frame (no facing or offset of its own), so the rigs line up with the procedural work, the pick zones and the contact
  points in all four facings; `RigClip` samples authored clips at a phase named by synced state (time since the lap or the
  clamp changed, the lever's pull, the pliers' opening), because GeckoLib 4.8.4 runs controllers on one model-wide clock
  that cannot give a paused game, a late observer or a Ponder scene its own phase. The work itself (stone geometry,
  frames, prongs, bezels, finish masks) stays procedural: it is computed from the simulations. A late observer's first
  sight of a station never replays a settle. A broken rig is reported once and the plain parts are drawn.
- **playerAnimator** (`client/presentation/anim`). One layer per client player (the library's per-player factory),
  priority 1100, first person disabled (the scene draws the operator's own hands). `PoseRules` choose a held clip from the
  operated station's synced state and a one-shot from the difference between two synced states; the first state seen is
  never a change, so a late watcher sees the current pose without history. The layer stops when the player no longer
  operates (the lease ended) and never runs while riding, swimming, sleeping, gliding or using an item; only a bounded
  turn of the arms toward the station is added. `bodyAnimation` (FULL, REDUCED, OFF) is a client option.
- **Patchouli** (`client/guide`). The handbook item opens the book through `PatchouliAPI`; `HandbookScreen` is removed.
  The book is i18n (every string a lang key); the fourteen chapters of the old handbook were split at paragraphs into page
  keys in all five locales. Live cards are `ICustomComponent`s with transient runtime state that read the client's synced
  definitions, recipes, server config and known designs; they never roll, craft, equip or tick. Help
  (`HandbookNavigator.help`) lets go of everything held through the behaviour's own release, closes the station screen
  (Diegesis returns the camera, the server ends the lease, the work stays) and opens the entry when the camera is back;
  holding the key opens the demonstration. A return token (dimension, station, work revision) only produces a notice on
  return; working again takes a fresh use. JEI categories of this mod open their entry with the same key
  (`IRecipeCategory.handleInput`).
- **Ponder** (`client/compat/ponder`). `PonderIndex.addPlugin` at client setup; lessons are opened with
  `PonderUI.of(item, tag)` and a hidden per-lesson tag (Ponder's public way to start at one scene). Lessons drive the real
  `FacetSim`, `SettingSim` and `SurfaceSim` with scripted hands (`DemoHands`, a fresh set per stretch, so replay and seek
  rebuild the same state) on the stations of Ponder's own level, through the stations' own setters and the planners
  (`CutPlanner`, `BenchPlanner`); results are made as the stations make them. Example stones are fixed values, never
  appraisal rolls. `PresentationClock` gives Ponder levels the scene's time, so rig settles follow pause and replay; the
  block entity renderers draw a Ponder level's stations even when a real scene is drawn at the same position.
- **Worn jewelry** (`client/presentation/worn`). One renderer for both bridges: a player layer for the built-in slots
  (fed by `WornAppearancePacket`, protocol 4, which carries only the look) and `ICurioRenderer` registrations for Curios
  (which syncs and toggles its own slots). Rendering is presentation only.

## Equipment and effects (spec 15-20, 30-32)

- Curios is optional. When it is loaded, jewelry registers as a curio (`CuriosApi.registerCurio`, compat class only)
  and the slots are ring x2, necklace x1, bracelet x1 and head x1. The legacy `charm` slot is also accepted for
  amulets, because Jewelcraft Reforged used it. Without Curios, a built-in capability provides the same slots through
  a small inventory button and key binding. `IJewelryEquipmentProvider` lets other equipment systems plug in.
- Gem = theme (trait), form = expression, metal = modifier/bias, cut = magnitude/stability, workmanship =
  efficiency, finish = tuning. A trait maps each form to one or more effect expressions. An expression names a Java
  `JewelryEffectType` plus data parameters, and other mods can register effect types. The same gem therefore behaves
  differently on each form.
- Runtime: `JewelryWearers` caches resolved pieces per player. It is invalidated by Curios change events, built-in
  slot changes, respawn, dimension change and datapack reload, with a cheap 40-tick signature check as a safety net.
  Hook bitmasks mean an event costs nothing for players without jewelry. Effects tick only when they declare an
  interval.
- Other mods add effects to a worn piece from their own data on it (Immersive Apotheosis: affixes and socketed gems)
  through `IJewelryEffectContributor`. Contributed effects run through the same hooks and gating as trait effects, keep
  the power they were given (prism amplification never touches them), and are re-applied when the piece's NBT outside
  the `immersive_jewelry` compound changes.
- `max<Form>Slots` (server config) caps how many pieces of a form are active. Curios slot counts themselves are data
  (`data/immersive_jewelry/curios/slots`).
- Progression hooks (1.1.0), all through `ImmersiveJewelryAPI` (or IMC `craft_gate`, `wear_gate`, `assist_source`):
  `IJewelryCraftGate` refuses a new piece with a reason when the first closure would commit it, after the design,
  stones and finish were validated and before any stone or material is used; `IJewelryWearGate` (asked on both sides)
  makes a refused worn piece inert like a broken one in `WearerState.refresh` and keeps it out of the built-in slots;
  `IJewelryAssistSource` multiplies the server's cutting and setting difficulty per player, clamped to
  `minigames.minAssistDifficulty..1.0`, when a cut is planned, a setting is committed or a rework begins; the eased parameters
  are part of the synced work, so the operator's client predicts with them. `GemCutEvent` is posted just before the cut
  stone replaces the work and
  lets listeners refine its quality (`setCutQuality`) or add dust to a shattered one (`addExtraDust`); experience and
  advancements follow the final values.

## Durability (spec 40-42)

Jewelry has Integrity (0..1), not vanilla durability. Only a single severe hit (configurable fraction of max health)
wears it, a little. Stone removal costs frame integrity, and repair restores it up to a cap that falls with every
rework. At zero integrity a piece is broken and inert, never deleted. Gem damage states are none, scratched, chipped
and fractured, and only recutting at the wheel clears them.

## Designs and the Jeweler (spec 43, 56)

Basic designs are known to everyone. Advanced ones (multi-stone and elaborate settings) are learned from Design
Sketches bought from expert and master Jewelers or found in treasure (`requireDesignSketches`, server config).
Villager-made jewelry is capped below Masterwork (`villagerMaxWorkmanship`).

## Presentation (spec 36-39, 52-54)

- Jewelry items are layered, tinted models: frame, recess and highlight layers (metal and finish tints) and a gem
  layer and sparkle layer (gem colour, sparkle by cut quality). The cut picks one of four gem-shape sub-models through
  an item property. There are no per-combination textures.
- Textures are generated from the editable pixel-art source `art/jewelry_art.py`, and sounds from `art/jewelry_audio.py`
  (synthesised, deterministic). Both are exported by `tools/`.
- The built-in jewelry slots screen uses Minecraft's own look, as Immersive Smithing's screens do: `VanillaGui` draws the
  vanilla container panel, slots and buttons from Minecraft's textures, so resource packs restyle it. The handbook is a
  Patchouli book since 1.2.2 (see Presentation libraries). The stations have no screen of their own: the scene's overlays are the loupe
  (`InspectionOverlay`) and the design folio (`FolioOverlay`). JEI uses its own slots and arrows. The only mod GUI
  textures are the empty-slot outlines.
- Accessibility (handoff 13): every station action has a key (`WorkstationKeys`, GUI context, rebindable) and an
  assisted sweep; no zoom, instant transitions, hold or toggle tool use, sticky drag, pointer sensitivity, larger pick
  zones, numeric guide, hints, visual cues for important sounds, tool volume, the hands toggle; larger targets and a
  gentler rate as server-validated assist requests; colour-blind palette, high contrast and reduced motion. Success is
  always shown by shape, text and sound as well as colour.
- Audio: the wheel's hum and grinding loops follow contact, force, hardness and the approach of a face's depth; the
  finishing tools and the pliers loop while they touch; one-shots for contact, chips, seating and completion. Every
  sound has a subtitle; observers hear the server's one-shots.

## Deviations summary

| Spec | Implementation | Why |
|---|---|---|
| 50.1 keep `jewelcraft` id | new id `immersive_jewelry` + aliases, remaps and NBT conversion | family naming, no namespace impersonation; migration is lossless |
| 25 `data/immersive_jewelry/<kind>/` | `data/<ns>/immersive_jewelry/<kind>/` | spec 73 and the Smithing convention; lets any namespace add definitions |
| 25 `material_overrides` | `metals` | clearer name for pack authors |
| 10.4 band "Rough" | omitted | its thresholds define six bands, and the name collides with the uncut rough stone |
| 7.1 one pliers item | Jeweler's Pliers and Fine Pliers | a tolerance tier (spec 7.1 "quality tier") |
| 7.2 upgradeable wheel | Emery Lap and Diamond Lap in the wheel's lap slot | two items instead of five components |
| handoff 10.1 Diegesis storage bindings for item moves | every move is a Jewelry operation (block use or `WorkCommandPacket`); library slots and custom actions are refused | one custody path and one transport per action kind (handoff 9); the operation rules stay in one place |
| handoff 12 scenes in `assets/immersive_jewelry/diegesis/scenes/` | `assets/immersive_jewelry/diegesis/stations/<station>.json`, loaded by `SceneLoader` and registered with `DiegesisClient.registerScene` | Diegesis auto-loads `diegesis/scenes` unmodified; the stations' scenes must be rewritten for no-zoom, instant transitions and reduced motion and always have block and entity clearing off |
| handoff 12 example `work_profiles/setting/prong.json` | defaults at `work_profiles/defaults/<interaction>.json`, used as `immersive_jewelry:defaults/<interaction>` | one fallback id per interaction; any other path can be named by a design, cut or finish |
| handoff 7 bezel tool | the bench's built-in pusher (no pliers, full precision) | a bezel needs no prong grip; pliers stay the prong tool |
| library handoff 8.1 GeckoLib controllers | clips sampled at an explicit phase (`RigClip`), no controllers | GeckoLib 4's clock is model-wide; synced state, pauses and Ponder need their own phase |
| library handoff 8.2 quill and stone as a rig | the quill, dop and stone stay procedural (`WheelPainter`); the lap, lever, vise and tools are rigs | the quill's pose is the contact geometry the pointer mapping and the simulation share |
| library handoff 9 contact-following poses | held clips per work state plus a bounded arm turn toward the station | the synced state carries the stage and contact, not a per-frame contact point; nothing per-frame is sent |
| library handoff 7.2 "first piece" with the real wheel output | the bench part starts from an example cut stone of the same values | a Ponder storyboard is compiled before it runs; the example is labelled |
| library handoff 5.4 scenes in `diegesis/scenes/` | unchanged from 1.2.0 (`diegesis/stations/`) | see the row above for handoff 12 |
