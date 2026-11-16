# Immersive Jewelry — full Patchouli, Ponder, GeckoLib, playerAnimator and Diegesis integration

**Implementation handoff · 15 November 2026**

## 1. Assignment and source baseline

Implement complete, coordinated integration of all five libraries with [otectus/ImmersiveJewelry](https://github.com/otectus/ImmersiveJewelry). Deliver functional workstations, finished animation assets, a complete in-game handbook, replayable demonstrations, safe persistence and multiplayer behavior, and reproducible validation. Dependency declarations and example integrations alone are not completion.

Jewelry needs its own implementation. Its work concerns individual appraised stones, small frames, prongs and bezels, surface finishes, wearable effects and reworking valuable items. Do not transplant Tanning’s hide geometry, work patterns, material ledger classes, camera host names or action enum into this mod.

### 1.1 Establish the actual implementation checkout

The public repository inspected for this handoff reports **`main` at `11a6a65c5031df80829a143431850eec308319c6`**, version **1.1.2**, Minecraft **1.20.1**, Forge **47.4.23**, Java **17**, network protocol **2**. It still contains screen-based cutting/setting and does not declare Diegesis. The earlier cinematic-overhaul handoff establishes the intended hands-on direction; newer 1.2.0 notes in the supplied project context describe Diegesis-backed persistent work. That newer implementation was not available in the public snapshot reviewed here.

Start by recording the actual checkout, branch, version, protocol and dependencies. Follow these two cases:

| Checkout found by the coding agent | Required approach |
|---|---|
| Newer 1.2.x hands-on implementation, including Diegesis scenes and persisted work | Extend its real adapters, commands, custody model and save schemas. Preserve fixes and existing controls. Do not recreate the old screen engine or introduce a parallel Diegesis integration. |
| Reviewed 1.1.2 public implementation | Build the durable hands-on foundation described in sections 4–5 before connecting tutorials and animation. Reuse validation/economy/API code, but replace rollback-on-close minigames with persistent physical work. |

This baseline distinction is deliberate. Paths and observations marked **existing** below refer to the inspected 1.1.2 snapshot. New classes, scene IDs, work stages and events are proposed contracts unless they already exist in the newer checkout. Map these responsibilities onto the current implementation rather than copying names literally.

This document preserves the earlier overhaul’s decisions: two core stations, four jewelry forms, four cuts, three finishes, physical prongs/bezels, resumable work, and Smithing-owned metal blank production. It supplies the library integration and completion requirements around that experience. No repository builds or gameplay tests were run while preparing this handoff.

### 1.2 What each library owns

| Component | Responsibility |
|---|---|
| **Diegesis** | The default cinematic Lapidary Wheel and Jeweler’s Bench scenes, camera framing, interactive objects, pointer capture, overlays and scene lifecycle. |
| **GeckoLib** | Wheel/quill/clamp/tool mechanisms and carefully authored approach, contact and release animation, integrated with procedural stone, prong, bezel and finish rendering. |
| **playerAnimator** | Local/remote third-person jeweler poses and hand/body movement associated with actual accepted work. |
| **Patchouli** | Replace the existing Jeweler’s Handbook presentation while retaining its item identity; provide complete reference, current data, progression explanations and contextual help. |
| **Ponder** | Deterministic demonstrations of cutting, assembly, finishing, care, equipment and integrations, linked from the handbook and relevant items. |
| **Jewelry domain code** | Appraisal, material custody, recipes, design knowledge, permission/progression gates, scoring, damage, effects, inventory, save state, finalization and rewards. |

The libraries must make the work understandable and tangible. They must not become five independent owners of the same hand, item, camera, timer or completed result.

## 2. Jewelry-specific coverage inventory

### 2.1 Stations, tools and registered identities

Preserve the two stations: `immersive_jewelry:lapidary_wheel` and `immersive_jewelry:jewelers_bench`. Keep metal heating/forging in Immersive Smithing when enabled, with Jewelry’s existing configurable fallback blank recipes when unavailable.

Preserve the four finished items—`ring`, `amulet`, `bracelet`, `circlet`—and the corresponding `ring_blank`, `amulet_frame`, `bracelet_blank`, `circlet_frame`. Material identity and quality remain data on those items, not one new registry entry per combination.

The existing tool tiers are **Jeweler’s Pliers** and **Fine Pliers**, and **Emery Lap** and **Diamond Lap**. These are real tool/lap stacks whose wear and precision matter. The reviewed defaults are 192/640 pliers durability and 48/256 lap durability; derive displayed values from the implementation rather than maintaining separate guide constants. A quill, tweezers, cloth, loupe and bezel tool may be workstation fixtures; do not require new crafted tools solely to justify animation assets.

Keep `rough_gem`, `cut_gem`, `gem_dust`, `polishing_compound`, `design_sketch`, and **`jewelers_handbook`** stable. Gem Grit’s registry ID is `gem_dust`; do not rename it to match its display name. Reuse any newer unfinished-jewelry carrier and its migration logic.

### 2.2 All four cuts

| Cut | Existing pattern/count | Required physical expression |
|---|---|---|
| `round` | Radial, 6 logical facets | Index around the stone, grind discrete faces symmetrically and inspect a rounded faceted result. |
| `brilliant` | Radial, 10 logical facets | A more detailed crown/pavilion presentation with deliberate indexing; keep workload bounded rather than simulating every real-world facet. |
| `emerald` | Step, 8 logical facets | Long controlled flats and stepped edges, with readable alignment and corner damage. |
| `cabochon` | Dome, 4 logical work regions | Smooth dome coverage and continuous guiding, not four flat prongs or a faceted diamond with another icon. |

These counts describe the reviewed data, not hardcoded UI assumptions. Preserve configurable definitions and generic presentation for additional cuts. Supported cuts depend on the gemstone: for example, the built-in diamond does not allow cabochon, and lapis allows only cabochon/round. Tutorials and selectors must respect both gem and design restrictions.

### 2.3 All ten designs

The IDs below are recipe path suffixes under `immersive_jewelry:designs/` in the reviewed resources. Resolve actual recipe IDs through the recipe manager; do not accidentally register flat IDs that differ from the existing paths.

| Design | Form | Stones | Setting per stone | Allowed cuts in baseline | Sketch by default |
|---|---|---:|---|---|---|
| `solitaire_ring` | Ring | 1 | 4 prongs | Round, brilliant, emerald | No |
| `bezel_ring` | Ring | 1 | 6 bezel regions | Cabochon, round, emerald | No |
| `trilogy_ring` | Ring | 3 | 4 prongs | Round, brilliant | Yes |
| `pendant_amulet` | Amulet | 1 | 6 prongs | Any supported cut | No |
| `locket_amulet` | Amulet | 1 | 8 bezel regions | Cabochon, emerald | No |
| `halo_amulet` | Amulet | 3 | 4 prongs | Round, brilliant | Yes |
| `cuff_bracelet` | Bracelet | 1 | 6 bezel regions | Any supported cut | No |
| `link_bracelet` | Bracelet | 3 | 4 prongs | Round, emerald, cabochon | Yes |
| `simple_circlet` | Circlet | 1 | 6 prongs | Any supported cut | No |
| `diadem` | Circlet | 3 | 4 prongs | Brilliant, round, emerald | Yes |

Three-stone designs require the same gem type under existing validation, but the individual stones retain their own size, quality, damage and history. Never clone the first stone’s data into the other sockets. Preserve declared points, tightness, protection, allowed gems/cuts/finishes and sketch requirements. Additional valid datapack designs need generated layouts within the actual supported bounds, not a missing model exception.

### 2.4 Three finishes and complete maintenance

| Finish | Required action and appearance | Existing material policy |
|---|---|---|
| `polished` | Apply compound, wipe overlapping arcs; sheen follows actual coverage. | One polishing compound in the reviewed definition. |
| `burnished` | Controlled passes with a smooth tool; restrained directional satin response. | No consumable cost in the reviewed definition. |
| `antique` | Apply the game’s patina treatment and wipe raised areas; darker recesses remain. | One gunpowder in the reviewed definition, retained as a game abstraction. |

Implement **repair, retighten, remove stones, refinish, recut and collection**, not only new-piece assembly. Distinguish their consequences: frame repair restores integrity within the rework cap; retightening addresses setting defects; recutting costs stone size and handles gem damage; removal changes the frame/rework history and returns the actual stones under the existing rules. Polishing must not become a universal cure for chips, fractured gems or weak metalwork.

### 2.5 Gem, material and effect coverage

| Built-in gem | Default trait | Presentation requirement |
|---|---|---|
| Diamond | Purity | Faceted clarity and restrained sparkle; supported-cut restrictions. |
| Emerald | Verdancy | Green identity, inclusions and brittleness visible without hiding guides. |
| Amethyst | Warding | Violet identity and its favored cuts. |
| Lapis | Insight | Opaque blue stone; appropriate dome/facet response. |
| Quartz | Radiance | Pale identity with readable contrast against the bench. |
| Ruby | Vitality | Red identity; supported tag sources and standalone acquisition. |
| Sapphire | Clarity | Blue identity distinct from opaque lapis. |
| Topaz | Fortune | Amber identity and its own cutting properties. |
| Onyx | Shadow | Opaque dark stone that remains visible against dark tools/backgrounds. |

Cover all loaded metal definitions and fallback Smithing families. The reviewed authored definitions include minecraft copper/gold/iron/netherite and forge aluminum, bronze, electrum, elementium, manasteel, mithril, nickel, platinum, rose_gold, silver, steel, terrasteel and tin. Undefined compatible families retain their generic profile and tint/name. Auto-detected gem tags use the existing generic behavior, including the Resonance trait; do not require bespoke artwork to make them functional.

Preserve the actual effect model: gem/trait supplies a theme, form supplies its expression, metal modifies it, cut/quality/finish tune it, and workmanship affects effectiveness. Show resolved behavior rather than advertising one fixed ability per gem. Preserve third-party effect contributions, wear gates, slot caps, damage/integrity and all authoritative effect dispatch.

## 3. Dependencies and API boundaries

Use the following researched starting matrix for Forge 1.20.1. Verify exact release source jars and the checkout’s existing dependency ranges before compiling against an API. A source-branch version number is not proof of a published binary.

| Library | Starting artifact |
|---|---|
| Diegesis | `curse.maven:diegesis-1790227:9624632` — Forge 1.20.1, `1.0.0-beta.10` |
| Patchouli | `vazkii.patchouli:Patchouli:1.20.1-85-FORGE` |
| Ponder | `net.createmod.ponder:Ponder-Forge-1.20.1:1.0.91` |
| Flywheel | `dev.engine-room.flywheel:flywheel-forge-1.20.1:1.0.5` |
| GeckoLib | `software.bernie.geckolib:geckolib-forge-1.20.1:4.8.4` |
| playerAnimator | `dev.kosmx.player-anim:player-animation-lib-forge:1.0.2-rc1+1.20` |

Repositories are `https://cursemaven.com`, `https://maven.blamejared.com`, `https://maven.createmod.net`, `https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/`, and `https://maven.kosmx.dev/` respectively. Use repository content filters and ForgeGradle deobfuscation. Patchouli uses its `:api` classifier for `compileOnly` and the full artifact at runtime. Follow the selected Ponder/Flywheel API arrangement, and inspect all transitive/runtime requirements instead of suppressing them blindly.

Keep versions in `gradle.properties`, with accurate `mods.toml` ranges and project-page dependency relations. If newer Jewelry already pins Diegesis beta.9, upgrade deliberately and run the scene/input regression matrix; do not assume all beta APIs or JSON schemas are identical. Start with a tightly bounded tested Diegesis range. Use only its public `com.pockyl.diegesis.api` surface.

All five libraries and compatible Flywheel are required for the complete client distribution. Diegesis, GeckoLib and Patchouli are required on the dedicated server for the chosen common registration/data arrangement. Jewelry must not require Ponder, Flywheel or playerAnimator there merely because common code references a client class; test the minimal server and verify upstream metadata.

Keep common/client initialization separated. Do not serialize library controllers or reference client renderers in common static initialization. Do not shade private copies of these libraries. Create is not required just to use standalone Ponder; test a compatible Create stack separately and do not claim compatibility with every historical Flywheel generation.

## 4. Durable work and shared presentation foundation

### 4.1 Existing code to reuse

Paths below are relative to `src/main/java/com/otectus/immersivejewelry/`. In a newer checkout, map them to their actual replacements.

| Existing area | Files | Integration responsibility |
|---|---|---|
| Station entry | `minigame/StationInteractions.java`, station blocks | One validated place/take/focus route; preserve held-item ownership and quick interactions. |
| Authority | `minigame/SessionManager.java`, `SessionTiming.java`, cutting/setting services | Reuse validation and finalization; separate temporary operation permission from persistent work. |
| Storage | `blockentity/LapidaryWheelBlockEntity.java`, `JewelersBenchBlockEntity.java` | Canonical stone/lap/workpiece custody and versioned resumable jobs; no scene-owned duplicate inventory. |
| Rules | `minigame/cutting/CuttingService.java`, `minigame/setting/BenchService.java`, `recipe/JewelryDesignRecipe.java` | Preserve appraisal, accepted materials, costs, difficulty, repair/rework consequences and design restrictions. |
| Data and identity | `api/gem/*`, `api/jewelry/*`, `data/*`, `jewelry/*`, `quality/*` | Correct loaded definitions, NBT, resolved effects, names and quality; provide sanitized view data. |
| Existing handbook | `item/HandbookItem.java`, `client/screen/HandbookScreen.java` | Keep the item and replace the presentation with Patchouli; map every old chapter. |
| Rendering | `client/render/LapidaryWheelRenderer.java`, `JewelersBenchRenderer.java`, `client/JewelryColors.java` | Reuse tint/model caches and proven buffer/lighting handling; add rigs and procedural work geometry. |
| Effects/equipment | `effect/*`, `equipment/*`, `integration/curios/*`, public API gates/events | Animation remains cosmetic; no duplicate effect ticks or changed equipment eligibility. |
| Networking | `network/ModNetwork.java`, `network/packet/*` | Retain definition/profile synchronization; replace or extend operation input with instance/revision-safe commands. |
| Verification/art | `src/test`, `src/testShared`, `src/gameTest`, `src/smokeTest`, `art/`, `tools/` | Extend real input tests and asset generation; retire tests tied only to obsolete screens. |

### 4.2 One work record, one command path

Use the newer hands-on work model if present. Otherwise introduce bounded, versioned work records owned by the block entities, with equivalent responsibilities to:

- **Lapidary work:** unique work ID, exact stone identity/appraisal, selected cut, source definition generation, seed, facet/region progress, current shape/size, damage, quality accumulators and lap/tool wear already charged.
- **Bench work:** exact base item, design and ordered stone instances, staging/commit ownership, selected finish, per-socket seat and prong/bezel state, surface coverage, defects, supplies already consumed, rework consequences and completion markers.
- **Operator lease:** temporary permission for one player to manipulate a station, with observers allowed; separate from persistent work ownership.
- **Presentation snapshot:** sanitized stage, shape parameters, tool targets, rotations, visible stacks, progress and active actor state. It contains no private inventory or duplicate extractable materials.
- **Command service:** the only place that validates and applies item movement, contact, rotation, finish, repair and finalization. Diegesis and accessibility input feed it; Ponder never does.

Logical wheel stages are appraisal/planning, mounted work, grinding/shaping, inspection and ready collection. Logical bench stages are staging, seating, securing, finishing, inspection and ready collection, with explicit rework branches. These are proposed concepts, not names to impose on a working newer enum. Preserve the actual operation model if it already provides these distinctions.

Appraisal is once per stone. Opening help, changing camera, cancelling a plan, relogging or reinstalling a lap cannot reroll natural quality or inclusions. Once material has been removed or a setting damaged, leaving focus preserves that work and mistake. Do not restore the untouched item as the old minigame cancellation path did.

### 4.3 Custody and commit policy

Stage exact stacks, validate current recipe/knowledge/gates/tools before first irreversible work, and commit costs at the intended action boundary. Consume finishing/repair supplies once when the operation first uses them effectively; resume must neither charge again nor refund already-used supplies. Persist enough wear accounting to prevent avoiding wear by repeatedly pausing.

An unfinished stone travels with its own progress. A progressed bench assembly may use the existing/new generic `unfinished_jewelry` carrier: one inert, stack-size-one item containing the frame, committed stones and bounded work state. Packing transfers custody out of the station; unpacking consumes the carrier. Uncommitted supplies remain separate. Breaking the station drops the carrier plus those separate supplies/tools, never a carrier and another copy of its embedded stones.

Finished pieces wait on the output/inspection pad and are collected through validated inventory transfer. Full inventory leaves output recoverable. Inspection, guide pages, Ponder preview and animation callbacks cannot craft, reserve, appraise, repair or award anything.

Preserve ordinary save/reload idempotence and test crashes near transfer/finalization boundaries. Do not claim cross-inventory/chunk power-loss atomicity merely because `setChanged()` was called. Record the actual guarantees and reconcile persisted transfer receipts if testing finds duplication or loss.

### 4.4 Accepted actions and animation clocks

Introduce or reuse small Jewelry-owned semantic events: stone mounted, lap changed, facet contact/completed, chip, stone lifted, socket seated, prong contacted/secured, bezel region closed, finish applied, finish completed, retightened, stone removed, frame repaired, result finalized, collected and manipulation cancelled.

Correlate each event with dimension/position, station instance, work ID/revision, actor, accepted sequence/event ID and server tick. Local contact may predict immediately, then reconcile without playing the accepted sound twice. A remote watcher receives accepted state/events, not another player’s raw UI input.

Continuous animation follows accepted/predicted contact targets and logical phase. Passive wheel coasting follows its physical visual state. Ponder uses its own deterministic timeline. Render frequency never determines grinding depth, pressure, finishing coverage, damage, cost or quality. Late observers receive current shape and pose without replaying old chips, rewards or collection effects.

## 5. Diegesis: complete physical workstation interfaces

### 5.1 Preserve or establish the existing adapter

In a newer Diegesis-backed checkout, extend the registered wheel/bench scenes and client behaviors. Keep existing persistent slots, operation commands and migration paths authoritative. Do not replace working storage bindings with a second schema just to match this document.

For the public 1.1.2 starting point, use server-authoritative Diegesis stations and a thin Jewelry-owned adapter. Avoid `clientOnly()` and the beta.10 vanilla-menu bridge for real crafting: the target authority is Jewelry’s work model, not a second menu inventory.

Use shared Diegesis admission with Jewelry enforcing one operator and allowing observers. The library’s `.exclusive()` alone rejects observers and does not protect ordinary block-use fallbacks. Both entry paths must respect the same lock, reach, line of sight and item ownership.

Coordinate automatic station opening with normal block use. Placing a stone or blank must occur once, and merely focusing must not unload the player’s hand into an arbitrary slot. If manual `Diegesis.open` is used, its `openWhen` predicate must permit the server-approved admission: an always-false predicate also blocks manual open in the inspected beta.10 implementation. A short-lived server-side admission scope keyed to the validated player/station instance is one suitable public-API design. Test actual callback and packet order.

The client activates controls only after the scene and matching operator/watch grant are both available. Open failure releases pending ownership. Repeated callbacks are idempotent, and a delayed old close cannot close a newer session.

### 5.2 Real inventory, one authority

Choose one custody integration per object, document it, and enforce it on every input route:

| Object | Acceptable implementation |
|---|---|
| Loose, uncommitted tray items and real docked tools | Public Diegesis bindings to the canonical persistent Jewelry inventory, with Jewelry’s placement/extraction rules and durable retention; or Jewelry-controlled custom transfers. |
| Mounted stone, committed sockets, frame and work order | Jewelry-owned state; node models are views. Generic take/shift-click cannot extract an embedded gem without a valid removal operation. |
| Consumables | Canonical supplies, charged by the Jewelry operation once; no parallel Diegesis cursor copy. |
| Finished output | One canonical output/pad item; validated collection only. |
| Fixture props | Visual/controller objects with no independently extractable item unless the design explicitly uses a real tool stack. |

For the legacy starting point, the simplest initial adapter is custom nodes plus Jewelry-owned transfer commands and an explicitly opened supplies overlay. Disable generic inventory/cursor transfers that are not bound to the authoritative slots. If keeping newer direct bindings, prove their retention and lock behavior instead of discarding them.

Guard bound-slot transfers before mutation through the public placement/take validation hooks, including shift-click, splitting, drag and cursor operations. An observer or unauthorized player cannot extract a committed object through a generic inventory gesture. `onSlotChanged` may reconcile accepted presentation; it must not be the first place an illegal transfer is detected. Apply the same restrictions to any exposed automation capability.

Default temporary Diegesis slots return contents when the last user leaves; they are unsuitable for persistent assemblies. `StationLogic` fields are not durable storage. `StationState.setData` sends a compound on changes; keep it small and do not stream complete meshes, input histories or duplicate item inventories through it.

Use one network route per domain action. If using Diegesis custom actions, decode bounded intents and dispatch into the shared Jewelry command service; if using Jewelry packets, do not send a second matching custom action. Upstream slot checks do not validate Jewelry’s crafting semantics.

### 5.3 Continuous input and spatial alignment

Use the public stroke-capable nodes for quill movement, grinding, constrained tool handles, bezel work and finish strokes. `SceneEvent.Stroke` supplies start/move/end/hover phases; MOVE may arrive every render frame even when the cursor rests.

Its point is on an upright camera-facing plane through the node origin, expressed in scene pixels. It is **not** automatically a point on a rotated gemstone, prong or curved bracelet. Convert through the scene/world frame, reconstruct the actual pointer ray, and intersect Jewelry’s canonical contact surfaces and constrained tool axes. Store stable normalized/model-space coordinates independent of camera FOV, aspect ratio, handedness and station facing.

Use distance/accepted-time sampling and bounded batches. Current held-pressure mechanics must become deliberate handle travel/contact where required by the hands-on overhaul, not pretend mouse pressure. A stationary pointer must not repeatedly advance a completed region. Preserve legitimate time-dependent heat/contact behavior through bounded server simulation rather than frame count.

Capture a gesture to its starting work target until release/cancel. Rotating a stone, opening the folio, collecting output and changing the hotbar cannot also occur from that stroke. Handle tap-between-ticks, toggle gestures, focus loss and reordered releases explicitly. Using `holdStroke()` must not synthesize a second committed press or finish.

### 5.4 Required scene content

Author or refine `assets/immersive_jewelry/diegesis/scenes/lapidary_wheel.json` and `jewelers_bench.json`, with IDs explicitly matched to registration and beta.10 schema. Preserve newer resource IDs if already established.

| Scene | Required objects and controls | Work that must be complete |
|---|---|---|
| Lapidary Wheel | Lap/disc, real stone holder, quill handle, index/angle control, cut specimens, inspection/loupe target, lap change and pickup area. | Appraise, choose a compatible cut, mount, select/rotate contact, grind all four cut types, lift/inspect, recut, pause/pack/resume and collect. |
| Jeweler’s Bench | Padded clamp, physical design folio, visible individual gem tray/sockets, pliers, bezel tool, finishing props, supplies and inspection/output pad. | Choose known design, stage exact stones, seat/align, close prongs or bezel, finish all three ways, inspect/collect, repair, retighten, remove, refinish and resume portable work. |

Render three actual stones for advanced designs, and distinct prong vs continuous bezel construction. A halo design is not a single gem with a multiplied result count. Keep selection aids concise; do not replace old screens with permanent wall-sized floating menus.

### 5.5 Cameras, hands, rendering and accessibility

Use an overview and a controlled inspection/contact shot per station, with the full tool-to-material relationship visible. Keep the camera steady during precision work. Disable unintended neighbor-station travel/glances initially; switching stations is an explicit stop/release/revalidate action. Do not enlarge the operator’s object secretly while using different observer/hit geometry.

Diegesis beta.10 does not expose a documented stationary-camera switch. Its rig codec accepts zero transition, but inspected camera code clamps the duration to at least 0.01 seconds. Therefore preserve the newer checkout’s real stationary/instant accessibility path; if absent, add a small Jewelry-owned stationary/reduced-motion host sharing the same commands and render data. Do not import/mixin into upstream internals or maintain a second gameplay engine.

One host owns camera/input at a time. Collision or competing camera mods should end an unusable cinematic view and offer/enter the documented stationary view without losing work. The real player remains at a legitimate position; camera travel grants no reach or invulnerability.

Assign one renderer to each shell, mechanism, workpiece, tool, hand and label. Use public actual-drawn/hidden-state APIs to avoid double rendering during transitions; “station has a user” is insufficient. `NodePainter` can host shared procedural/Gecko rendering, but is not a native Gecko JSON loader. Smooth painters may be called twice per frame; all painting must be free of gameplay, clock advancement and effect emission.

Rebind nodes in `onSceneReady` after F3+T. Disable overlapping automatic scene work sounds where Jewelry events already own them. Preserve real skin/sleeves, wide/slim dimensions, handedness, tool-only options and hand opacity. No duplicated vanilla arm, Gecko tool and playerAnimator first-person limb.

### 5.6 Closing, help and return

One coordinator handles Escape, movement/distance, damage, death, teleport, disconnect, station loss, lease loss, pause/chat, resource failures and other screens. Stop contact immediately, checkpoint accepted work, release only the matching lease, clear local predictions/layers and restore cursor/camera/FOV/HUD. An animation may settle visually, but cannot delay the player regaining control.

Before Patchouli or Ponder opens: cancel/lift the active tool, leave focus, release manipulation, and store a small Jewelry-owned return token. Reading does not freeze a multiplayer world. Returning rechecks the station instance, work revision, reach and ownership; it never resumes an old held gesture automatically.

Diegesis’s child-screen facility may support short, deliberate JEI/folio overlays after testing. Do not keep grinding invisibly while the handbook or Ponder is on top. A scene reload or missing visual asset should yield a diagnostic and usable safe view, not a trapped cursor or a refunded valuable stone.

## 6. Patchouli: a complete Jeweler’s Handbook

### 6.1 Preserve the item, replace the reader

Keep `immersive_jewelry:jewelers_handbook`, its item stacks, display identity and existing book-plus-amethyst-tag recipe. Replace `HandbookItem`’s client opening route with the public Patchouli book API. Suggested book ID: `immersive_jewelry:jewelers_handbook`. Remove the obsolete `HandbookScreen` after its callers/tests are migrated; do not leave two manuals with conflicting instructions.

Add contextual help from the wheel, bench, folio, item tooltips and relevant JEI categories without requiring the item in inventory. Do not copy Tanning’s proposed new-book registration or grant flag. If a newer checkout already grants a handbook, preserve that mechanism rather than adding another first-login grant.

Use the Minecraft 1.20 Patchouli resource arrangement:

- `data/immersive_jewelry/patchouli_books/jewelers_handbook/book.json`, with `use_resource_pack: true`.
- `assets/immersive_jewelry/patchouli_books/jewelers_handbook/en_us/categories/`.
- Corresponding `entries/` and reusable `templates/`.
- Equivalent localized content for existing supported languages where required by the selected Patchouli localization route.

Use `PatchouliAPI.get().openBookGUI(...)` / `openBookEntry(...)` from distribution-safe client code. Bind the existing custom item using the release’s supported book configuration. Keep return-navigation tokens outside Patchouli internals. Use native search, bookmarks and navigation instead of rebuilding them.

### 6.2 Migrate all fourteen existing chapters

| Existing handbook chapter | New coverage |
|---|---|
| `welcome` | Overview, first piece, help and comfortable controls. |
| `stones` | Sources, appraisal, individual identity, size, natural quality, inclusions, traits and damage. |
| `wheel` | Lap installation, mounting, quill/index controls, inspection and persistent work. |
| `cutting` | Physical contact, heat/pressure where applicable, damage, lifting and completion. |
| `cuts` | All four cuts, supported/favored cuts, tradeoffs and recutting. |
| `blanks` | Four forms, Smithing blanks/quality and actual fallback availability. |
| `bench` | Folio, known designs, exact stone selection, staging, clamp and supplies. |
| `setting` | Seating, prongs, bezels, balance, defects and multiple stones. |
| `finishes` | Three physical finishes, live costs/tuning and refinishing. |
| `wearing` | Curios and built-in slots, caps, cosmetic/inactive pieces and wear restrictions. |
| `effects` | Trait × form behavior, metal/cut/quality/finish tuning and external contributions. |
| `care` | Integrity, broken pieces, repair, retighten, remove and recut; distinct consequences. |
| `designs` | Ten designs, sketch acquisition/learning, restrictions and pack-added recipes. |
| `jeweler` | Profession workstation, trades, treasure acquisition and villager workmanship limits. |

Existing translations and useful explanations must survive the move. Rewrite obsolete minigame instructions rather than copying them into the new book.

### 6.3 Required finished entries

Every topic below needs a real entry or clearly indexed subsection, with helpful cross-links and an associated demonstration where appropriate. Essential recovery/accessibility instructions remain visible without advancement gates.

| Category | Required entries and teaching points |
|---|---|
| Start here | The complete stone-to-wearable route; first round-cut solitaire; the two stations; tool/lap tiers; real controls; stationary/reduced-motion mode; safe leaving and resume. |
| The stone cabinet | All nine gems and generic modded gems; vanilla/tag/trade/treasure sources; appraisal occurs once; size vs natural quality vs cut quality; inclusions/brittleness; opaque stones; damage states and sensible recutting. |
| The lapidary wheel | Mount/remove stone; install/replace lap; select only supported cuts; plan expected loss; index and guide; lift/inspect; round; brilliant; emerald; cabochon; worn tools; finished stone and portable unfinished work. |
| Metal and form | Four forms and blank IDs; forging with Smithing; fallback crafting and why it may be disabled; carried Smithing quality/material family; generic metals; no accidental recycling of gem-set pieces. |
| Designs and preparation | Folio use; known/locked/unavailable design states; exact stone selection; matching gem requirements vs differing individual quality; supplies; craft gate reasons; all ten design cards and generated cards for added designs. |
| Stone setting | Seating/alignment; opposing prongs and balanced closure; bezels as a continuous rim; multi-stone work; tool precision; loose/bent/crooked/chipped outcomes; inspect before finishing. |
| Finishing | Polished coverage and compound; Burnished passes; Antique recess/high-point treatment; material costs; current stat tuning; finish restrictions; switching/refinishing without duplication or stat farming. |
| Wearing and effects | Curios slots vs built-in slots; two rings and other configured active caps; forms’ different expressions; resolved effect preview; workmanship/integrity; disabled/blocked pieces; enchantments and third-party contributions. |
| Care and rework | Severe-hit wear; broken but recoverable jewelry; repair and diminishing cap; retightening; removing each stone; damage-preserving extraction; frame history; recutting; refinishing; safe storage/transport. |
| Workshop life | Jeweler profession/trades/sketches; villager-made quality; equipment systems; JEI/Jade; multiplayer observers/ownership; design learning vs reading a tutorial. |
| Troubleshooting and packs | Missing/empty tags, unavailable ingredients, unsupported cuts, unknown sketches, denied gates, missing definitions, stale recipes, full output inventory, interrupted work, visual fallback and datapack/API extension points. |

Make pages concise and tactile: illustrated grip/contact diagrams, stone silhouettes, prong order, rim sectors and coverage cues. Use ivory paper, dark ink, muted brass and material color accents. Meaningful differences must remain visible in high contrast and without color discrimination. Do not use tiny labels baked into textures.

### 6.4 Data-aware components

Implement a small reusable set of public `ICustomComponent` components and focusable widgets supported by Patchouli 85. Runtime state in Gson-deserialized components must be transient. Give controls keyboard focus, narration, disabled reasons and current keybinding text.

| Component | Required data behavior |
|---|---|
| **Gem card** | Resolve source ingredients, trait, hardness/brittleness, supported/favored cuts and actual appraisal fields for a selected stone. No new random roll while rendering. |
| **Cut comparison** | Display current retention/quality/stability tradeoffs from definitions; distinguish first cut from recut and respect gem restrictions. |
| **Design card** | Recipe ID, form, exact required stone count, setting style/points, restrictions, finish choices, known/locked status and source of its sketch. |
| **Metal/blank card** | Resolved family/name/tint, Smithing/fallback route, quality provenance and available repair ingredient. |
| **Finish card** | Current costs and tuning; explain a no-consumable finish honestly instead of inventing a reagent. |
| **Piece inspection** | Read the actual item’s gems, maker, workmanship, integrity, defects, reworks, Smithing history and final resolved effect description. |
| **Effect comparison** | Compare an exact existing piece with a bounded hypothetical design, clearly marked Estimated where quality/context is not yet known. |
| **Care card** | Whether a specific operation is possible, actual cost/cap and which aspects it can or cannot improve. |
| **Watch demonstration** | Registered Ponder component/lesson mapping; accessible activation and a useful missing-content state. |

Reuse `JewelryDefinitions`, synchronized recipes, `JewelryResolver`, description/tooltips and effective server policy. Do not build a second stat calculator inside the book. Read-only previews must not call equipment activation, award events, appraise a random stone or tick contributed effects. Report conditional effects and requirements accurately rather than presenting every conditional value as always active.

`SyncDefinitionsPacket` already distributes resolved definitions, including auto-detected gems. Extend only the missing bounded information for effective policy, per-player design knowledge or gate explanations. Never broadcast another player’s inventory, escrow or private progression state. A gate/availability preview is advisory; the server revalidates on actual work.

Invalidate relevant caches after datapack/config reload, definition generation changes, item mutation, design learning and disconnect. Missing definitions preserve inspectable identity and recoverable work while showing an explicit unavailable state. User resource packs can restyle/translate content, not authorize a disabled recipe.

### 6.5 Progression and localization

Reading a Patchouli entry or Ponder scene does not learn a Design Sketch. Advanced design pages can show the form and requirements while the operation remains locked. Display the current `requireDesignSketches` policy; retain actual `DesignKnowledge` and sketch consumption behavior. Do not bind progression to arbitrary Patchouli advancement visibility.

Preserve the shipped languages: `en_us`, `es_es`, `pt_br`, `ru_ru`, `zh_cn`. Carry existing translations into the new handbook and supply translated new interface/book/scene text for the supported locales. Validate placeholders, plural/number formatting, whole-name templates, Cyrillic/CJK wrapping and long labels. Avoid deleting translations during generated-resource updates; retain meaningful fallback for third-party definitions.

## 7. Ponder: a playable reference for jewelry work

### 7.1 Public registration and navigation

Register one client `PonderPlugin` through `PonderIndex.addPlugin`, using the pinned release’s scene, tag and shared-text APIs. Keep vanilla structure-template schematics under `assets/immersive_jewelry/ponder/` according to that version’s conventions. Ponder must work without Create.

Associate lessons with wheel, bench, pliers, laps, rough/cut gems, handbook, sketch, polishing compound and representative forms/blanks. Use tags such as First Piece, Lapidary, Setting, Finishing, Care and Equipment. The lesson index is data-driven enough to identify current content without registering every possible gem/metal combination separately.

`PonderUI.of(ResourceLocation)` opens a registered component, not an arbitrary scene ID. Maintain explicit guide-topic → component/lesson mappings. If the selected API cannot jump to one scene, open the right component collection and identify the intended lesson. Do not invent a scene-opening method or depend on UI reflection.

### 7.2 Required lessons

Every row is required content, not a list of optional ideas. Variants may share animation/render helpers but must show the real shape and operation being taught.

| Suggested lesson family | Demonstration and acceptance point |
|---|---|
| `first_piece` | A complete round-cut diamond → solitaire → polished ring → valid equip route; distinguish Smithing and available fallback paths. |
| `appraisal_and_sources` | Source item becomes one fixed appraised stone; inspect size/quality/inclusions; remove/remount without rerolling. |
| `laps_and_tools` | Emery vs Diamond Lap, mounting/wear/replacement, Jeweler’s vs Fine Pliers, and fixture props vs real items. |
| `round_cut` | Index, make a controlled pass, lift, inspect and repeat around a real round profile. |
| `brilliant_cut` | More intricate face arrangement, tradeoffs, symmetry and restraint near the target depth. |
| `emerald_cut` | Step faces, alignment and corner control. |
| `cabochon_cut` | Dome coverage on a compatible stone; do not use a diamond example that its definition disallows. |
| `cut_mistakes_and_recut` | Overgrinding/heat or damage under the chosen simulation; what recutting changes and the permanent size tradeoff. |
| `portable_stone` | Leave, pack/take, remount and resume the same partially worked stone. |
| `blank_routes` | Four forms, carried Smithing quality, fallback conditions and no jewelry-side forging duplication. |
| `folio_and_sketch` | Known vs locked design, learn a real sketch, select exact materials; watching the scene itself does not unlock anything. |
| `seat_and_prongs` | Align an individual stone, brace it, close opposing prongs and inspect for loose/bent results. |
| `bezel_setting` | Continuous rim closure, section coverage and balanced support rather than prong squeezing. |
| `three_stones` | Three real, distinct appraised stones of an allowed gem type; separate sockets and no duplicated data. |
| `design_atlas` | Ten short variants, one per built-in design, including locket, cuff, link bracelet and diadem; correct setting count/style and cut restrictions. |
| `polished_finish` | Compound charged once; cloth coverage exposes sheen; pausing does not refund or charge it again. |
| `burnished_finish` | Controlled smoothing and satin response; no fictional consumable. |
| `antique_finish` | Patina treatment and high-point wipe; dark recesses and actual definition cost. |
| `quality_and_inspection` | Metal/cut/setting quality, selected finish and contextual effect outcome; explain defects without a fake guaranteed Masterwork. |
| `retighten_and_repair` | Distinguish secure setting from frame integrity; preserve current costs, caps and rework history. |
| `remove_and_reuse` | Loosen/remove actual stones, return the frame, show wear/history and reusing a stone without rerolling it. |
| `refinish_and_resume` | A new finish on existing jewelry and an interrupted bench assembly; preserve completed work and foreign data. |
| `wearing_and_effects` | Curios and built-in equipment, slot limits, broken/gated inactivity and form-dependent trait expressions. |
| `watching_and_handoff` | Operator vs watcher, deliberate ownership handoff, departure and rejoin; no remote input authority. |

Use clean captions, readable pause points, contact indicators and visible tool paths. Explain a failed action and its recovery, not only perfect inputs. Reduce camera motion when requested. Every important cue has a non-audio representation; audio is optional reinforcement.

### 7.3 Isolated, deterministic previews

Create a Jewelry-owned preview context around the same read-only material/rig rendering used at real stations. It has explicit seeded example state and a Ponder-controlled timeline, with no live command sink, equipment activation or Diegesis station admission. Never run a fake lesson player against a real inventory or use the actual multiplayer world as a demonstration workspace.

Rewind/seek/replay reconstruct facet geometry, stone damage, per-socket progress, finish coverage, tool target, wheel angle and actor pose from the lesson state. Do not let Gecko/playerAnimator clocks continue accumulating independently of Ponder. Effects replay only at appropriate crossed timeline events; repeated drawing of a paused frame produces no more dust, chips, sounds or rewards.

Use bundled baseline examples for reliable choreography and label them as examples when packs alter costs or definitions. Pair them with live handbook cards. Resolve optional Smithing, Curios and modded-gem examples against current availability; hide/replace that example if unavailable instead of displaying broken items. Show the fallback route when it is actually enabled, not merely when a mod is missing.

Returning to the handbook restores the relevant entry. Returning to a station obtains fresh authorization. A scene cannot leave player animations, phantom tools, borrowed sound loops or hidden real blocks behind.

## 8. GeckoLib: mechanisms and precise material rendering

### 8.1 Adapter and asset ownership

Use GeckoLib 4.8.4’s actual block-entity/item interfaces, animatable instance caches, controllers and public renderer hooks. Verify them in a wheel prototype before replacing both station render paths. Do not follow GeckoLib 5 examples against the 1.20.1 artifact.

Keep one rig instance per actual station or item identity, with separate preview instances for Ponder. Share immutable models/textures; never share one mutable animation controller across every wheel, pliers stack or preview. Clean up world references and resource-dependent caches on unload/reload.

GeckoLib supplies articulated motion. Jewelry’s work state supplies the shape: cut planes/dome progress, real socket placement, prong closure, bezel coverage, polish mask and defects. Do not make a fixed animation clip the source of facet completion or assume a pre-baked prong bend accurately represents every accepted pressure value.

### 8.2 Complete rig/animation inventory

| Proposed rig or module | Required elements | Required action/state coverage |
|---|---|---|
| `lapidary_wheel` | Disc axle, handle/drive mechanism where present, quill/holder, angle/index parts and stable contact anchors. | Install/change lap, mount, approach/contact/lift, indexed rotation, active rotation and coasting, inspect, release/collect. |
| `jewelers_bench` | Clamp jaws, adjustable holder, tool rests and precise workpiece anchors. | Clamp/unclamp, stage/seat, inspection rotation, finish support, rework/collection. |
| `jewelers_pliers` / `fine_pliers` | Handle and jaw articulation, two skin-hand grip anchors and tip/contact anchors. | Open/close, bounded squeezing, support/lift/return, retighten and safe removal; retain actual tool identity. |
| `bezel_tool` | Push/burnish contact and grip anchors. | Travel around rim, controlled region closure, stop/lift and rework. |
| `tweezers` | Jaw/grip and stone attachment anchors. | Select the exact staged stone, approach, align, seat, release and extraction. |
| `finish_tools` | Cloth/pad, burnisher and patina applicator with stable contact origin. | Apply, wipe/burnish, lift, return; three visibly distinct finishes. |
| `jewelry_display` modules | Four form silhouettes, design-specific socket layout and modular gem anchors. | Inspection and careful pickup/placement; actual metal/gems/cuts/finish and current defects. |
| `lapidary_stone` rendering module | All four cut profiles plus generic fallback, inclusions/damage and work-in-progress geometry. | Shape evolution driven by work data; no decorative rolling that detaches it from the quill. |

Do not force purely static geometry into an expensive animated renderer. Keep efficient existing layered/tinted inventory models where they work well. A “full GeckoLib integration” means complete purposeful motion and correct presentation across the workflow, not an idle animation on every item icon.

Ship original `.geo.json`, `.animation.json`, textures and editable Blockbench/source assets. Use consistent roots under `assets/immersive_jewelry/geo/`, `animations/`, and `textures/`. Record model/bone/controller/clip IDs and intended ownership in an asset manifest. Keep `art/jewelry_art.py`, `art/workshop_pixels.py` and resource/texture generators consistent so regeneration does not restore obsolete station geometry or erase the new assets.

### 8.3 Controllers, contact and effects

Separate persistent mechanical pose, continuous work motion and one-shot accepted responses. Wheel angle interpolates across wrap and coasts smoothly; the baseline already fixed backward wrap snapping. A loaded workpiece resumes its actual shape and clamp pose without replaying all mounting sounds.

Drive continuous trajectories from normalized targets/accepted phase, using authored approach/grip/settle clips around them. Avoid restarting a complete squeeze/grind clip per packet. Prong and bezel transforms remain aligned with authoritative deformation and collision surfaces. Generic design layouts scale within supported geometry bounds rather than clipping several stones into one socket.

Use animation markers only for cosmetic timing after accepted events. They must never apply score, appraise, consume, damage, repair, set a socket, emit a crafting event or award XP. Deduplicate local prediction, server acceptance and remote effects. Keep working sound loops tied to actual contact, and stop them on lift/interrupt.

Place dust, slurry, a small chip and tool sounds at the real contact point. Scale them for a close camera. Lapis and onyx remain opaque; other stones can have restrained facet highlights without requiring expensive per-stone transparency or real-time refraction. Dynamic light, emissive sparkle or magic effects are not a default substitute for craftsmanship.

Retain correct normals, item glint, layered tint scopes, resource-pack render types and buffer lifetimes. Do not decode `JewelryData` or resolve every worn effect per rendered mesh. The repository’s prior renderer work specifically reduced repeated NBT decoding; retain revision/item-change-aware caches, including detection of relevant in-place NBT changes.

### 8.4 Hands, display and worn-form polish

Focused first-person hands use the player’s real skin and sleeve geometry, preserving wide/slim models, handedness, opacity and tools-only view. Keep tools opaque when arms are translucent. Author support-hand positions for the quill, clamp and active socket. Bounds on reach/twist matter more than forcing a hand to touch an unreachable target.

Finished jewelry keeps its modular identity in-hand, in inventory, on the station and in previews. Three-stone jewelry displays its separate stones. New display geometry must retain custom names, glint and compatible custom model/resource-pack behavior; a fallback to the efficient layered item representation is preferable to a missing model.

As part of visual polish, provide or retain a modest worn representation for all four forms: finger/hand rings, necklace/amulet on torso, bracelet on wrist and circlet on head. Attach to the already-posed vanilla/playerAnimator model parts rather than running a second full player skeleton. Rings remain appropriately small; use inspection views for detail instead of inflating their worn size.

Route visible equipment through one render bridge for Curios and the built-in equipment provider. Respect cosmetic visibility, hidden slots, armor/model occlusion and existing provider behavior. A second render pass must not activate effects or count a piece twice. If remote clients lack visible-item data, synchronize only a bounded sanitized appearance snapshot on equipment changes, not the complete private item or effect-contributor NBT. Provide per-form visibility and conservative fallback for incompatible player models.

## 9. playerAnimator: a jeweler’s body language

Register a namespaced per-player animation factory using `PlayerAnimationFactory.ANIMATION_DATA_FACTORY`, retrieve its own associated layer through `PlayerAnimationAccess`, and use the selected release’s keyframe/fade/modifier APIs. Store animations under `assets/immersive_jewelry/player_animation/`. Verify part names against playerAnimator; Gecko bone names do not automatically map to player model parts.

Create complete approach/hold/work/release behavior for these families:

- Install a lap, mount/remove a stone, grip/index/guide the quill, lift and inspect.
- Carry/place a frame, select a tray stone, align and seat it with tweezers.
- Brace a piece and close a selected prong; move deliberately to an opposing prong.
- Guide a bezel tool around the rim with a supporting hand.
- Apply compound/patina, wipe with a cloth and burnish in controlled passes.
- Retighten, loosen/remove stones, repair a frame and place/take an unfinished assembly.
- Inspect/collect a finished piece and release all tools when leaving.

Use small, deliberate upper-body movement suited to jewelry. Active poses should follow the current socket/contact region and actual tool tier. Observers must not play the operator’s animation on their own body. Do not use a hammering/smithing clip for every action.

Player root position remains authoritative Minecraft movement. No teleportation, reach extension, damage immunity or forced movement lock for an animation. Blend a sensible stationary stance while valid work continues; cancel/reduce the layer on walking away, combat, riding, swimming, sleeping or another incompatible action. Preserve controls needed to exit promptly.

Use the same actor/work/event identity as station presentation. Local contact can predict, but rejected input cannot look like a finalized stone, repair or collected result. Remote clients interpolate targets and bounded phases; late observers receive active state without historical events. One player’s priority/animation object must not be shared with another player.

Keep first-person focused arms/tools under the Jewelry/Gecko presentation owner and disable playerAnimator’s conflicting first-person contribution there. Test first-person-body mods so a torso can remain visible without duplicated limbs. Gate any older hand/pose hooks when the new layer owns the same parts.

On exit, unload, death, lost lease or overriding combat/emote animation, clear/fade only Jewelry’s layer. Never clear another mod’s animation data. Make priority policy and a reduced-body-animation option explicit; a camera/body compatibility fallback must preserve crafting outcomes.

## 10. Networking and multiplayer contracts

### 10.1 Operation coverage

The reviewed protocol uses select/start/input/result packets plus wheel/bench actions. `BenchActionPacket.Action` contains `REMOVE`, `RETIGHTEN`, `REPAIR`, `REFINISH`, `TAKE`; `WheelActionPacket` takes the stone. The new physical interaction model must preserve all of these capabilities while replacing unsafe instant/rollback paths where the overhaul requires persistent work.

Use the newer checkout’s commands if available. Otherwise define bounded intents covering the following logical operations; these labels are proposed capabilities, not assertions about an existing enum:

| Operation family | Required coverage |
|---|---|
| Focus/role | Request/open, keepalive, release, observe, intentional ownership handoff and accessibility-host selection. |
| Material movement | Insert/take stone, install/remove lap, place/take frame, stage/unstage individual gems/supplies, dock/take a real tool, collect output. |
| Planning | Select cut, select known design, select allowed finish, confirm stage/commit and cancel an untouched plan. |
| Lapidary | Index/rotate/angle, begin/update/end contact, lift/inspect, finish a valid cut, recut and pack/resume. |
| Setting | Select socket, align/seat an exact gem, select prong/region, begin/update/end constrained pressure or rim contact, inspect and advance valid stages. |
| Finishing | Apply material once, begin/update/end coverage, inspect, complete the valid finish. |
| Maintenance | Retighten, remove actual stones, repair frame, refinish, recover missing-definition work and preserve history. |
| Completion | Finalize once, reveal output, collect, pack/unpack progressed work and explicitly start another piece. |
| Knowledge/navigation | Request read-only available choices and personal design status; help/Ponder opening never doubles as a gameplay command. |

### 10.2 Validation and synchronization

Validate on the server thread: sender, loaded dimension/station, station instance, work ID/generation, lease/role, sequence, stage, reach/visibility, item/tool ownership, recipe/definition availability, knowledge/gates and action-specific parameter bounds. Never accept a client score, finished item, gem appraisal, quality cap, arbitrary NBT or claimed remote actor identity.

Bound all arrays, counts, IDs and payload sizes before allocation/reading. Reject non-finite values and invalid enum/target indices. Respect the actual supported design/gem bounds; do not hardcode the built-in three-stone maximum if the existing serializer supports larger valid designs. Validate per-socket data and every actual stone count in that range.

Rate-limit contact batches and discrete commands independently enough that a release/cancel can always neutralize the tool. Retain sequence checks and server-time limits from the existing deterministic engine, adapted to the physical model. No per-frame custom packets. If using Diegesis’s action channel, keep beneath its verified release limits with room for other controls; do not assume those generic limits validate Jewelry’s contact geometry or force rate.

Use snapshots for persistent state and semantic events for transient responses. Watching clients must not infer success from a raw pose. A new observer gets the current facets, socket/prong/bezel/finish state, wheel phase and active actor, not a replay of historical work. Scope routing by dimension and tracking range; no global per-tick broadcast of all stations.

On a refused/stale input, send a bounded reason plus the required state correction. Invalid tool changes, missing materials and lost ownership must promptly stop predicted contact rather than letting an apparently successful clip finish.

If packet layout changes, increment the **actual checkout’s** protocol. Protocol 3 is the next candidate from public 1.1.2’s protocol 2; a newer 1.2.0 checkout already using 3 needs another increment for further incompatible changes. Do not overwrite the protocol with a number copied from the Tanning document.

### 10.3 Required multiplayer behavior

Two players cannot operate the same wheel or bench simultaneously. Watchers can inspect and open help, but cannot remove a committed stone, spend supplies or change the plan. A player may have one active manipulation session; switching stations releases the old one safely.

Close/release affects the matching participant/session only. A late close callback, old sequence or packet for a destroyed/replaced station cannot cancel or animate new work at that position. Disconnect, timeout and window focus loss neutralize force/contact and clear transient locks; they do not complete an operation or undo accepted damage.

Work ownership and view permission are explicit. Handoff preserves the work’s original maker/history rules and actual stones. Merely watching does not acquire ownership or receive a material refund. Private design knowledge is synchronized to its owner, not copied into every watcher’s book.

## 11. Persistence, migration and public API preservation

### 11.1 Upgrade by schema, not by guesswork

Preserve registered IDs, Jewelcraft Reforged aliases/remaps and existing legacy NBT conversion. The public implementation uses `JewelryNbt` version 1 and replaces its owned root on write; integrate new work fields into deliberately versioned readers/writers rather than letting an old helper erase them.

| Saved content | Required migration/result |
|---|---|
| Existing finished jewelry | Remains finished, wearable and mechanically equivalent. It does not need to be polished or crafted again. |
| Existing cut/rough stones | Keep appraisal, size/original size, cut quality, inclusions, damage and cap; no new roll. |
| Wheel stone and lap | Preserve exact stacks and lap damage. Convert actual newer progress with its own versioned reader. |
| Old 1.1 bench workpiece | Preserve identity independently of orphaned escrow. |
| Old `Escrow`, `DisplayStone`, `Progress` without a new work schema | Recover real escrow once through a tested migration. `DisplayStone` is a visual copy, never another refundable gem. Old scalar progress is not enough to fabricate new per-prong work. |
| Newer 1.2 clamp/tray/supplies/work data | Use its established migration and persistent custody model. Do not route it into the old stale-escrow drop routine or refund it again. |
| New portable assembly | One owner, one carrier, no live effects; repeat pack/load/unpack preserves committed materials and progress. |
| Missing recipe/definition/profile | Preserve recoverable actual items and bounded saved work; show an unavailable reason and safe recovery. A visual failure must not destroy the piece. |

Existing newer saves take precedence over this document’s proposed field names. Read actual schemas before designing changes. Migrations run once and are stable on repeated load. Document any one-way schema transition; do not claim an older jar can safely read newly introduced work data.

Persist all accepted irreversible changes, supplies consumed and wear budgets. On datapack reload, stop manipulation and either retain the bounded parameters needed to finish the current operation or suspend it safely. Do not combine new tolerances with old quality accumulators mid-stroke. New work uses the new definition generation.

Retain maker, integrity, reworks, defects, metal identity/tint, Smithing compound, custom name, enchantments and legitimate foreign data through supported transformations. Map ownership explicitly when extracting stones or converting jewelry back to a frame: copying one piece’s foreign affixes onto every returned stone would duplicate data incorrectly. Preserve the existing contributor/API contract and document any intentional conversion boundary.

### 11.2 Public API contracts

| API/system | Required behavior |
|---|---|
| `IJewelryCraftGate` | Validate a complete new-piece plan before committing inputs. Preserve denial messages and exception behavior; help/preview cannot bypass it. Revalidate safely if policy changes while work is paused. |
| `IJewelryWearGate` | Keep refused/broken pieces inert under the existing equipment/runtime policy. Visual rendering cannot make a blocked piece active. |
| `IJewelryAssistSource` | Apply the same server-clamped assistance to physical evaluation and its client preview; presentation toggles alone cannot improve quality. |
| `GemCutEvent` | Fire once at actual cut completion, before final stone placement/rewards; preserve allowed quality/dust modifications and use the final accepted values in all presentation. |
| `JewelryCraftedEvent` | Fire once for a completed new piece at the documented finalization boundary, not once per socket or animation clip. Update timing documentation if physical finishing shifts that boundary. |
| `JewelryReworkedEvent` | Keep action semantics and once-only effects for legitimate repair, removal, retightening and refinishing. |
| `JewelryEquipEvent` / `JewelryBrokenEvent` | Preserve equipment/effect behavior and their actual triggers; a pose or display change is not an equip/break event. |
| `IJewelryEffectContributor` | Preserve external contributions, gating and recalculation when relevant foreign NBT changes; do not amplify external contributions through a duplicated Jewelry bonus path. |
| `IJewelryEquipmentProvider` | Enumerate actual equipped pieces once; accommodate Curios/built-in/third-party systems without activating preview or unfinished items. |

Do not call wear/effect ticks from Ponder, Patchouli, item rendering or Gecko controllers. Reuse descriptions without activating effects. Keep public registration APIs usable by other mods; add operation context only through a documented compatible extension or a deliberate versioned API change.

## 12. Integration and configuration matrix

| Area | Required implementation and validation |
|---|---|
| **Immersive Smithing** | Preserve blank production, per-form patterns, provider-based generic metal support, Smithing quality/history and blank recycling values. Finished gem-set pieces and unfinished committed assemblies must not be recycled for free duplicates. Test enabled, disabled and absent cases. |
| **Fallback blank recipes** | Follow `requireImmersiveSmithing`, integration enablement and fallback recipe settings. “Smithing absent” alone is not sufficient to promise a fallback. Show the actual reason in folio/book/JEI. |
| **Curios** | Keep ring/necklace/bracelet/head behavior and legacy amulet charm compatibility. Respect cosmetic slots and real active caps; no duplicate runtime effect/provider registration. |
| **Built-in equipment** | Preserve all four forms, configured two-ring behavior and slot screen/key/button. Unfinished carriers are not wearable. Visual integration works without Curios. |
| **JEI** | Preserve cutting, design and reference categories; add useful help/Ponder links and safe folio selection. Do not replace selection with remote crafting. Older Smithing provider-only recipes may not live in `RecipeManager`; verify the installed version and disclose unavailable listings rather than inventing them. |
| **Jade** | Show current stone/cut, work stage, selected socket, finish and operator/paused status where useful, without duplicate focus overlays or private inventories. Test runtime rendering. |
| **Immersive Apotheosis / effect contributors** | Preserve affixes, socketed data and contributed effects through finishing and care according to their APIs. No new affix roll, duplicate bonus tick, or loss of foreign data from a renderer-driven stack rebuild. |
| **Magic/attribute mods** | Preserve existing conditional Iron’s Spells ’n Spellbooks/Ars Nouveau attribute behavior where enabled. Explain unavailable effects accurately and keep optional classes distribution-safe. |
| **Datapacks and gem mods** | All six definition kinds—gems, gem cuts, finishes, forms, traits, metals—and real design recipes continue to sync. Auto-detected gems and unknown render profiles have functional generic visuals. Empty ingredient tags are checked through existing helper semantics. |
| **Jeweler villagers and loot** | Keep profession/POI, trades, sketch acquisition, standalone non-vanilla rough gems, quality cap and migration. Tutorials do not generate real trade/loot rewards. |
| **Rendering/camera/combat** | Test compatible Embeddium/Oculus, resource packs, a representative shader pack and relevant camera/body/combat/emote stacks. Verify actual pose/camera ownership, not merely class-loading. |
| **Standalone Ponder / Create** | Test the pinned standalone runtime and a specifically compatible Create/Flywheel stack. Avoid a hard Create dependency. |

Keep optional integrations optional. Their images, book entries, scene examples or rendering hooks must not import absent common/client classes at startup. Missing content produces a specific hidden/disabled example and useful explanation, not a crash.

Preserve existing `CommonConfig` / `ServerConfig` / `ClientConfig` separation. Integration/recipe switches remain available at the correct load phase; server gameplay/assistance values are authoritative; camera, particles, hand opacity, body motion, captions and worn-visibility choices are personal presentation settings. Migrate existing keys instead of resetting preferences.

## 13. Performance, diagnostics and finished presentation

Do not trade the current optimized station renderer for repeated per-frame NBT decoding, recipe enumeration or JSON parsing. Cache immutable assets and resolved display profiles, and cache mutable work geometry by instance/work/revision. Update only affected facets, socket regions or finish masks where practical. In-place relevant NBT changes must invalidate the appropriate cache.

Cull distant/hidden work, stop active controllers for idle stations and bound particle/event/interpolation caches. A workshop full of idle benches must not animate every imaginary operator. Keep Ponder preview instances isolated and release them after closing. Measure frame-time spikes and allocations, not just average FPS.

Extend the existing diagnostic command/report facilities with dependency versions, active scene/host, supported content counts, resource failures and effective integration settings. A development overlay may expose lease/work/revision and animation ownership. Keep these internal identifiers out of normal player instruction flows.

Make all cues multi-channel: shape/text plus color, captions plus sound, visible tool lift plus contact audio stopping. Preserve persistent/readable station notices and narration. At high GUI scale, the active socket, depth/coverage cue and important notice must not obscure each other. Hands/tools should frame the work, not cover it.

Asset failures should log once with an actionable resource ID and choose a safe generic presentation. Avoid per-frame warnings. Update the repository’s art manifest, generators, documentation and screenshots so they describe the delivered hands-on workflow and five-library dependency set rather than the old timed screens.

## 14. Implementation sequence

Complete each milestone and proceed through release validation. These are reviewable engineering checkpoints, not a request to stop after a prototype.

1. **Reconcile the baseline.** Read repository instructions and current code; record actual version/protocol and identify which hands-on work already exists. Preserve newer 1.2 work. Resolve dependency/source versions and capture baseline gameplay, save fixtures and rendering.
2. **Prove durable work.** Reuse or finish operation records, custody, leases, neutral-input interruption, once-only costs and finalization. Prove appraisal stability and conservation before adding elaborate animation.
3. **Complete a Diegesis vertical slice.** Make one round-cut stone and solitaire ring with real contact, seating, prongs, polish, collection and equip. Include an observer, interruption/resume and stationary/reduced-motion operation. No legacy crafting screen in this path.
4. **Finish station coverage.** All four cuts, all ten designs, all finishes, generic data-driven content, both tool/lap tiers, portability and every maintenance action.
5. **Finish Gecko and body motion.** All required rigs/clips, procedural geometry, skin hands, playerAnimator poses, worn/display attachment behavior, actor sync and reload/interruption cleanup. Resolve duplicate renderer/input/sound ownership.
6. **Migrate the handbook.** Keep its item; move all fourteen chapters into finished Patchouli content; add live components, progression-aware cards, localization and safe help navigation.
7. **Finish Ponder.** All required lessons and atlas variants, deterministic pause/seek/replay, correct component links and optional-content handling.
8. **Compatibility, migration and polish.** Full server/client matrix, old/new save fixtures, performance, resource packs, translations, production packaging and accurate release documentation.

Resolve uncertain APIs in the relevant vertical slice using public interfaces. A justified accessibility/render fallback must remain complete and documented; it is not permission to omit Diegesis or leave another library as a dependency-only scaffold.

## 15. Verification and release acceptance

### 15.1 Existing evidence is a starting point

`docs/WORKSTATION_VALIDATION.md` records historical renderer/localization results, including 31 unit tests and 42 required GameTests for that earlier validation run. It reports real screen-driven cutting/setting clients and comparative rendering measurements. It also states that ordinary dedicated-server startup was blocked by an unaccepted EULA, remote multiplayer clients/shader stacks were not tested, and audible sound playback was not tested because volume was zero.

Those results are not evidence that the new library integration works, and their old counts are not asserted to be current HEAD’s totals. Discover and run the actual current suites. Use an appropriately prepared dedicated-server environment; if a prerequisite prevents a check, report it as not run rather than a pass. Source compilation or headless GameTests do not prove the live scene, audio or two-client experience.

Existing task entry points include:

```bash
./gradlew compileJava
./gradlew test
./gradlew runGameTestServer
./gradlew runGameTestServer -PnoSmithing -PnoCurios
./gradlew runClient -PsmokeTest
./gradlew runClient -PsmokeTest -PnoSmithing -PnoCurios
./gradlew build compileGameTestJava compileSmokeTestJava
python3 tools/generate_resources.py --check
python3 tools/generate_textures.py --check
python3 tools/generate_sounds.py --check
git diff --check
```

Use the checkout’s quiet wrapper and mod validation script where supplied. Extend smoke drivers to use actual Diegesis/physical inputs rather than leaving old tests that pass through retired screens. Keep test agents out of the production jar.

### 15.2 Required automated regressions

| Group | Meaningful assertions |
|---|---|
| Content | Four cuts, ten designs, three finishes, nine built-in gems, all loaded metals and valid generic extensions resolve; restrictions and actual recipe paths match gameplay. |
| Appraisal | Same stone stays the same through mount/take/plan/help/relog/recut; no UI or preview reroll. |
| Material conservation | Stage/commit, abort before work, pause after work, packing/unpacking, extraction, full inventory, block break and duplicate finalization each conserve exact legitimate items. |
| Gem identity | Three-stone and maximum-supported designs preserve every selected stone’s independent data; display copies cannot be extracted. |
| Geometry/input | Four facings, varied FOV/GUI scale, left/right hands and both view hosts map visible contact consistently; invalid/overspeed/off-surface input does not receive ideal progress. |
| Timing | Equivalent accepted paths at different FPS/batch timing give equivalent outcomes; tap-between-ticks, lost releases, toggle and cancel are handled once. |
| State | All cut/setting/finish/maintenance stages survive leave/save/reload; frame/stone damage and tool/supply charges persist. |
| Migration | Jewelcraft aliases/data, 1.1 stone/lap/bench escrow, genuine newer 1.2 work, foreign NBT and repeat loads; no refund of `DisplayStone`. |
| Permissions | Operator vs watcher, handoff, reach/visibility, missing tools, design knowledge, craft/wear/assist gates, stale instance and replayed/invalid packets. |
| API/events | Correct gate/exception behavior and exactly-once cut/craft/rework/reward events; final modified values reach output/guide/tooltips. |
| Effects/equipment | Curios and fallback providers, slot caps, broken/gated inactivity, external contributions and in-place NBT invalidation; no preview activation. |
| Navigation | Book/Ponder opening neutralizes input, releases manipulation and restores controls; return revalidates; no material or progression action. |
| Ponder | Pause/rewind/seek/replay reconstruct identical visuals without touching live world/inventory or leaking effects/controllers. |
| Resources | Every book link, scene component, asset, bone, clip, locale key and generated fallback is valid; resource reload rebinds correctly. |

Assertions should verify actual outcomes, conservation and real input paths, not merely the existence of a class or an animation file.

### 15.3 Required live-client matrix

Run a complete stone-to-wearable path in cinematic and stationary/reduced-motion views. Exercise all four cuts with compatible gems, both lap tiers, both pliers tiers, all ten designs, all three finishes and all maintenance actions. Include opaque/dark/pale stones and generic modded gems/metals, rather than testing only a bright diamond ring.

Use **two real clients on a dedicated server**. Watch the same work, join mid-grind or mid-setting, hand off ownership, disconnect during contact, break/replace a station, reopen after reading help and test nearby simultaneous work at separate stations. Check server inventory and saved data as well as what both clients see. A mock-player test cannot prove remote hand alignment or camera behavior.

Test constrained FPS at 30/60/high-refresh rates, latency/jitter and a short packet interruption. Verify neutral input on lost contact and no faster progression from render/packet volume. Compare canonical recorded paths separately from human execution differences.

Inspect normal desktop resolution and an actual small render target such as 854×480, with increased GUI scale and long Russian/CJK text. Test wide/slim skins, left-handed controls, tools-only/transparent hands, narrator, high contrast and reduced motion. Verify no critical label, hand or tooltip hides the work.

Exercise Escape, inventory, chat/pause, focus loss, damage/death, teleport/dimension change, resource reload, datapack reload, missing definitions and camera/body animation conflict during active contact. After each, normal controls, camera, FOV, HUD and other mods’ animation layers must recover.

Run Smithing and Curios present/absent/disabled profiles, active external contributor tests, JEI/Jade, the supported Ponder/Create matrix, and representative rendering/camera/combat stacks. Listen to actual sound playback and verify subtitles; do not count a muted client as audio validation.

Profile matched idle/active workshops and repeated station → handbook → Ponder → return cycles. Record hardware, resolution, mods, shader choice, frame-time percentiles and allocations. Test cache cleanup and repeated reloads. Compare to the existing renderer baseline; no unbounded growth or major unexplained opening/stroke spikes.

### 15.4 Definition of done and deliverables

- [ ] The actual implementation baseline is documented and newer hands-on work is preserved.
- [ ] Both stations use a complete Diegesis cinematic experience with usable stationary/reduced alternatives.
- [ ] Every cut, design, finish and maintenance path is implemented and resumable; no hidden legacy instant-completion shortcut remains in the intended physical flow.
- [ ] The existing handbook item opens the finished Patchouli manual, covering all old chapters and the expanded workflow.
- [ ] All Ponder lessons and atlas variants are complete, linked, replayable and isolated from gameplay.
- [ ] Gecko rigs/material rendering and playerAnimator poses cover the complete workflow and remote observers without duplicated objects or effects.
- [ ] Individual stones, quality, damage, progression, equipment, API events and legitimate external data remain correct.
- [ ] Old/new saves and interrupted work migrate/recover without duplicated escrow or lost materials.
- [ ] Automated suites, resource checks, live two-client scenarios, production server and required compatibility profiles have recorded outcomes.
- [ ] Assets, translations, dependency metadata, architecture/API docs, release notes and screenshots describe the delivered behavior.

Deliver code and original editable assets, a dependency/asset manifest, migration and protocol notes, exact validation commands/results, representative screenshots or short clips, and a concise list of known limitations. Separate passed, failed and not-run checks. Do not call a prototype, static showcase or dependency-only scaffold “full integration.”

## 16. Source references

These primary sources establish the reviewed baseline and library contracts. The selected release source jars and actual implementation checkout take precedence over moving development branches.

### Jewelry implementation and prior direction

- [Reviewed public commit](https://github.com/otectus/ImmersiveJewelry/commit/11a6a65c5031df80829a143431850eec308319c6), [version properties](https://github.com/otectus/ImmersiveJewelry/blob/11a6a65c5031df80829a143431850eec308319c6/gradle.properties), and [build configuration](https://github.com/otectus/ImmersiveJewelry/blob/11a6a65c5031df80829a143431850eec308319c6/build.gradle).
- [Architecture and gameplay contracts](https://github.com/otectus/ImmersiveJewelry/blob/11a6a65c5031df80829a143431850eec308319c6/docs/ARCHITECTURE.md), [changelog](https://github.com/otectus/ImmersiveJewelry/blob/11a6a65c5031df80829a143431850eec308319c6/CHANGELOG.md), and [historical workstation validation](https://github.com/otectus/ImmersiveJewelry/blob/11a6a65c5031df80829a143431850eec308319c6/docs/WORKSTATION_VALIDATION.md).
- [Station interactions](https://github.com/otectus/ImmersiveJewelry/blob/11a6a65c5031df80829a143431850eec308319c6/src/main/java/com/otectus/immersivejewelry/minigame/StationInteractions.java), [session authority](https://github.com/otectus/ImmersiveJewelry/blob/11a6a65c5031df80829a143431850eec308319c6/src/main/java/com/otectus/immersivejewelry/minigame/SessionManager.java), and [bench escrow](https://github.com/otectus/ImmersiveJewelry/blob/11a6a65c5031df80829a143431850eec308319c6/src/main/java/com/otectus/immersivejewelry/blockentity/JewelersBenchBlockEntity.java).
- [Existing handbook](https://github.com/otectus/ImmersiveJewelry/blob/11a6a65c5031df80829a143431850eec308319c6/src/main/java/com/otectus/immersivejewelry/client/screen/HandbookScreen.java), [registered items](https://github.com/otectus/ImmersiveJewelry/blob/11a6a65c5031df80829a143431850eec308319c6/src/main/java/com/otectus/immersivejewelry/registry/ModItems.java), and [handbook recipe](https://github.com/otectus/ImmersiveJewelry/blob/11a6a65c5031df80829a143431850eec308319c6/src/main/resources/data/immersive_jewelry/recipes/jewelers_handbook.json).
- [Design recipes](https://github.com/otectus/ImmersiveJewelry/tree/11a6a65c5031df80829a143431850eec308319c6/src/main/resources/data/immersive_jewelry/recipes/designs), [built-in definitions](https://github.com/otectus/ImmersiveJewelry/tree/11a6a65c5031df80829a143431850eec308319c6/src/main/resources/data/immersive_jewelry/immersive_jewelry), and [design contract](https://github.com/otectus/ImmersiveJewelry/blob/11a6a65c5031df80829a143431850eec308319c6/src/main/java/com/otectus/immersivejewelry/recipe/JewelryDesignRecipe.java).
- [Curios integration](https://github.com/otectus/ImmersiveJewelry/blob/11a6a65c5031df80829a143431850eec308319c6/src/main/java/com/otectus/immersivejewelry/integration/curios/CuriosIntegration.java), [gem data](https://github.com/otectus/ImmersiveJewelry/blob/11a6a65c5031df80829a143431850eec308319c6/src/main/java/com/otectus/immersivejewelry/api/gem/GemData.java), and [jewelry data](https://github.com/otectus/ImmersiveJewelry/blob/11a6a65c5031df80829a143431850eec308319c6/src/main/java/com/otectus/immersivejewelry/api/jewelry/JewelryData.java).
- The owner’s **Immersive Jewelry — Cinematic, Hands-On Overhaul** handoff, 14 November 2026, supplies the accepted physical-work direction. Its essential decisions are restated here so this handoff can stand alone. Newer 1.2.0 implementation notes are project context, not a verified public source snapshot.

### Diegesis

- [Forge 1.20.1 beta.10 file](https://www.curseforge.com/minecraft/mc-mods/diegesis/files/9624632) and [reviewed release source](https://github.com/Pocky-l/diegesis/commit/727c177c27b2963925c667f49e6a1d8b5debc582).
- [Getting started](https://github.com/Pocky-l/diegesis/blob/727c177c27b2963925c667f49e6a1d8b5debc582/docs/getting-started.md), [station/storage contracts](https://github.com/Pocky-l/diegesis/blob/727c177c27b2963925c667f49e6a1d8b5debc582/docs/stations.md), and [client APIs](https://github.com/Pocky-l/diegesis/blob/727c177c27b2963925c667f49e6a1d8b5debc582/docs/client.md).
- [Stroke contract](https://github.com/Pocky-l/diegesis/blob/727c177c27b2963925c667f49e6a1d8b5debc582/common/src/main/java/com/pockyl/diegesis/api/event/SceneEvent.java), [node painter](https://github.com/Pocky-l/diegesis/blob/727c177c27b2963925c667f49e6a1d8b5debc582/common/src/main/java/com/pockyl/diegesis/api/client/NodePainter.java), and [camera rig](https://github.com/Pocky-l/diegesis/blob/727c177c27b2963925c667f49e6a1d8b5debc582/common/src/main/java/com/pockyl/diegesis/api/scene/CameraRig.java).
- [Camera behavior](https://github.com/Pocky-l/diegesis/blob/727c177c27b2963925c667f49e6a1d8b5debc582/common/src/main/java/com/pockyl/diegesis/impl/client/focus/FocusController.java) and [manual-admission behavior](https://github.com/Pocky-l/diegesis/blob/727c177c27b2963925c667f49e6a1d8b5debc582/common/src/main/java/com/pockyl/diegesis/impl/server/StationManager.java) were inspected to understand limitations; their internal packages are not integration APIs.

### Patchouli, Ponder, GeckoLib and playerAnimator

- Patchouli: [release 85 dependency/API guidance](https://github.com/VazkiiMods/Patchouli/blob/release-1.20.1-85/README.md), [Minecraft 1.20 layout changes](https://vazkiimods.github.io/Patchouli/docs/upgrading/upgrade-guide-120/), [book definition](https://vazkiimods.github.io/Patchouli/docs/reference/book-json/), and [release component context](https://github.com/VazkiiMods/Patchouli/blob/release-1.20.1-85/Xplat/src/main/java/vazkii/patchouli/api/IComponentRenderContext.java).
- Ponder/Flywheel: [official Forge 1.20.1 dependency configuration](https://wiki.createmod.net/developers/depend-on-create/forge-1.20.1), [standalone repository](https://github.com/Creators-of-Create/Ponder), [inspected plugin API](https://github.com/Creators-of-Create/Ponder/blob/09e008a1406737d2a13beb7d36503a7e6d80258d/Common/src/main/java/net/createmod/ponder/api/registration/PonderPlugin.java), and [component UI entry point](https://github.com/Creators-of-Create/Ponder/blob/09e008a1406737d2a13beb7d36503a7e6d80258d/Common/src/main/java/net/createmod/ponder/foundation/ui/PonderUI.java). The development snapshot is not asserted to be the exact 1.0.91 release commit.
- GeckoLib: [4.8.4 Forge release](https://www.curseforge.com/minecraft/mc-mods/geckolib/files/8285794), [GeckoLib 4 installation](https://github.com/bernie-g/geckolib/wiki/Installation-%28Geckolib4%29), and [versioned block-entity guidance](https://github.com/bernie-g/geckolib/wiki/Geckolib-Blocks-%28Geckolib4%29/eeb44118c3d41e2d74d90efc899f70d8d08c5d55).
- playerAnimator: [Forge 1.20 release](https://www.curseforge.com/minecraft/mc-mods/playeranimator/files/4587214), [1.20 README](https://github.com/KosmX/minecraftPlayerAnimator/blob/a17ecb85e7978e63286697bc0f5ff8a7348ef055/README.md), [per-player factory](https://github.com/KosmX/minecraftPlayerAnimator/blob/a17ecb85e7978e63286697bc0f5ff8a7348ef055/minecraft/common/src/main/java/dev/kosmx/playerAnim/minecraftApi/PlayerAnimationFactory.java), and [keyframe/first-person API](https://github.com/KosmX/minecraftPlayerAnimator/blob/a17ecb85e7978e63286697bc0f5ff8a7348ef055/coreLib/src/main/java/dev/kosmx/playerAnim/api/layered/KeyframeAnimationPlayer.java).



