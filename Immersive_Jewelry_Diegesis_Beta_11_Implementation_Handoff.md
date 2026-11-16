# Immersive Jewelry — Diegesis beta 11 implementation handoff

**Reassessed:** November 16, 2026 — revised against Jewelry 1.2.2, commit `7ca0b4f`  
**Target:** Minecraft 1.20.1, Forge 47, Java 17  
**Dependency target:** Diegesis 1.0.0-beta.11, Forge artifact  
**Purpose:** Updated coding-agent implementation handoff. This replaces the earlier 1.1.2-based plan and separates existing implementation from remaining beta 11 work. Review/specification only; no mod source was changed.

## 1. Mandate and verified baseline

Upgrade the existing **Immersive Jewelry 1.2.2** implementation from **Diegesis beta 10 to beta 11**. Extend the finished hands-on workshop rather than rebuilding it. Preserve the continuous wheel, tweezers, prongs, bezels, finishing, repair/removal, portable work, progression APIs, and presentation libraries. Add practical physical storage, search, deliberate camera choices, readable lighting/counts, and reliable new inventory gestures.

Use beta 11 where it improves the experience. Every upstream change is accounted for below; menu adapters and back-facing orientation remain conditional because they solve different problems.

### 1.1 Source revisions inspected

| Component | Verified revision | What this establishes |
|---|---|---|
| Immersive Jewelry, `main` | `7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7` | **1.2.2**, Forge **47.4.23**, Java **17**, protocol **4**, required Diegesis **beta 10**. Hands-on stations and the presentation integrations are implemented. |
| Previously reviewed Jewelry | `11a6a65c5031df80829a143431850eec308319c6` | Historical 1.1.2 baseline only. Its screens/session classes are removed in the current tree. |
| Diegesis beta 10 | `727c177c27b2963925c667f49e6a1d8b5debc582` | The actual current dependency and direct predecessor for this migration. |
| Diegesis beta 11 | `ae9e324a836a2172baa2be56b12d43dcf4ab94c4`, tag `v1.0.0-beta.11` | Three commits and 48 changed files relative to beta 10. |
| Published Forge beta 11 artifact | `diegesis-forge-1.20.1-1.0.0-beta.11.jar` | CurseForge project **1790227**, file **9643618**, published November 16, 2026. |
| GitHub release asset SHA-256 | `22b541fb23b41988ac2cd75b52ca8e93e868050fbe56831968f412ee7d844f16` | Verify downloaded bytes before treating another distribution copy as identical. |

Evidence: [properties][J1], [current tree][J2], [changelog][J12], [release][D1], [upstream comparison][D2], [Forge distribution][D3].

**This revision supersedes the earlier handoff.** The previous warning that the remote lacked Diegesis, its foundational conversion instructions, and its references to deleted `minigame/SessionManager`/screen classes no longer apply.

### 1.2 Reassessment: what changes in the implementation plan

| Earlier instruction or assumption | Current evidence | Revised direction |
|---|---|---|
| Convert screens to stations | 1.2.0 already did this; old screens and packets are removed | Extend `workstation/` and `client/workstation/`; do not revive legacy code. |
| Add persistent work and portable pieces | `CutWork`, `BenchWork`, `UnfinishedJewelryItem` and leases already exist | Preserve their formats and first-use payment rules; add only missing guards/metadata. |
| Include an initial beta 9 → 10 migration | 1.2.1 already uses beta 10 | Retain placement hints, filtered compact inventory drawer, scroll controls and JEI child screens. |
| Start from protocol 2 | Current protocol is 4 | Bump from 4 if changing packets; the full feature scope below does change command payloads. |
| Choose a new item ownership system | Real contents already belong to block entities; Diegesis schemas are empty and reject all slot transfers | Add a narrow storage bridge for new staging storage and existing supply slots; keep work/clamp/socket ownership in Jewelry. |
| Add 12 loose slots and freely moving gems | Eight precise gem-tray cells, eight sockets and three supply slots already exist | Keep the precise gem tray; add a small **four-stack staging drawer**. This is an intentional additive capacity choice, not migration of the eight existing cells. |
| Use `onKey/onChar` directly for folio search | Existing folio is a `ScreenOverlay`; beta 11 suppresses those hooks under overlays | Use public hooks for uncovered station input; retain a scoped Forge pre-event bridge for overlay editing, including characters and Escape. |
| Simply mirror the camera | Both main shots have position/target X=8; behavior caches authored camera vectors | Author a modest asymmetric shot and update the input projection with the effective mirrored camera. |
| Require a separate calm resource pack | `SceneLoader` already transforms resource JSON for comfort options before public codec/registration | Extend that loader for calm physics and safe rebuilds; a separate resource pack is optional. |
| Fix a presumed `isInUse` renderer handoff | Both renderers already use `isSceneDrawn`, and stations leave the base blocks visible | Preserve the correct handoff and Ponder-preview exception; exercise upstream regressions. |
| Assume Patchouli/Ponder/GeckoLib/playerAnimator may be absent | All are integrated in 1.2.2, with required-side metadata | Extend the real handbook, lessons, rigs, poses and generators; retain their current dependency contracts. |
| Design new 1.1 escrow recovery | Current bench already migrates format 1 to format 2 | Keep and regression-test that migration; do not recover escrow twice or treat display copies as items. |

### 1.3 Preserve these established contracts

- `JewelryStations`: SERVER authority, BLOCK orientation, five-block reach, one operator, walk-up placement, no neighbouring-station hopping and no block/entity clearing.
- The compact inventory drawer appears on request; do not replace it with a permanent inventory grid.
- No time limits/countdowns. `FacetSim`, `SettingSim`, and `SurfaceSim` describe physical progress.
- Appraisal occurs once on actual server mounting. Cutting progress travels on the stone.
- Bench assembly commits at the **first closure**, after craft-gate checks. Pliers wear then; lap wear occurs at first contact; finish/repair materials are paid at first effective use.
- A bezel uses the built-in pusher without pliers. Moving from setting to finishing requires player intent.
- Interrupted work remains; leases end and tools lift. Packed committed work remains an `unfinished_jewelry` carrier.
- JEI is a child screen that retains the station; Patchouli/Ponder help deliberately exits it, waits for camera return, and requires fresh use to resume.
- GeckoLib owns rigs, playerAnimator owns third-person body poses, procedural renderers own the evolving gem/frame/prongs/finish, and server simulation owns outcomes.

### 1.4 Review scope and implementation preflight

The update is one commit containing the 1.2.0–1.2.2 work. Complete old/new Git tree comparison found **257 added, 71 modified and 40 removed files**. The GitHub comparison response stops at 300 files, so this reassessment also used complete recursive trees and pinned individual files. The review examined the current build, architecture, persistence, operations, packet entry points, scene/input/folio/rendering code, library integration, generators and relevant tests. It is not a claim that every asset was visually inspected.

The Diegesis review still covers the complete beta 10 → 11 patch and changed-file ledger. No Minecraft client, server or build was run during this reassessment. Existing test results in [WORKSTATION_VALIDATION.md][J20] are **repository-reported historical evidence**, not a fresh beta 11 pass.

Before implementation, read local `AGENTS.md` if present, `CLAUDE.md`, architecture and status; record the current commit and preserve user changes. Reconcile any changes newer than this pinned revision. Create a requirement checklist marked **retain**, **adapt**, **new**, or **conditional**. Choose the release version from the actual branch; do not re-release as 1.2.2.

## 2. Complete Diegesis beta 11 adoption matrix

This table covers every published beta 11 addition, behavior change, and fix. The underlying implementation adds details beyond the release summary; those are included in section 3.

| ID | Upstream change | Required Jewelry treatment | Acceptance evidence |
|---|---|---|---|
| B11-01 | Optional `placing.physics` adds falling, tipping, collision, stacking, and edge spills. | Add the four-stack staging drawer in section 5; preserve the precise gem tray/clamp. Use calm settings through the existing scene loader. | Loose items settle; a held workpiece never becomes a physics body; every stack remains accounted for. |
| B11-02 | Dragging/turning placed items responds to mass and resistance. | Tune for tiny workshop items, gentle handling, and predictable tool clearance. Do not equate visual mass with gem hardness or crafting quality. | Drag, rotate, release, crowd, and remove support without tunneling, unbounded motion, or quality changes. |
| B11-03 | `placing.counts`: `always`, `hover`, `never`. | Use hover counts for loose supplies and always-visible counts where quantity drives the next step. Unique gems show identity/quality instead of a misleading quantity badge. | Stacks of 1, 2, and maximum size remain legible in both camera hands and all facings. |
| B11-04 | `StationType.Orientation.BLOCK_OPPOSITE`. | Audit orientation; retain `BLOCK` for the inspected front-facing Jewelry blocks unless model tests prove otherwise. Use the new option only for an actual back-facing block contract. | North/east/south/west placement, structure rotation, and mirror tests agree with work controls. |
| B11-05 | `StationView.setSurfaceTransform(int, Matrix4fc)`. | Animate the new staging surfaces with the actual drawer. Move fixed supply nodes separately with the same progress. | Items, previews, hits, shadows/light pools travel with the drawer. |
| B11-06 | `setPlacingVisible(boolean)` and `setSurfaceVisible(int, boolean)`. | Hide stowed contents and disable closed surfaces. Reapply global visibility every frame; explicitly restore per-surface state after reload. | Closed drawers neither render through the bench nor accept clicks/placement. |
| B11-07 | `mirrorCamera(float plane)` and `cameraMirror()`. | Author asymmetric framing, then mirror across `x = 8`. Update cached effective-camera input vectors; current main shots are centered. | Main view, inventory view, and glances all remain usable from both sides. |
| B11-08 | `hasRoomFor(ItemStack)` and automatic free-spot placement. | Consult it before a custom shift-in request, then validate inventory capacity/ownership on the server. Use bounded overflow handling when the local layout estimate is stale. | Crowded/closed trays, changed item models, and rapid transfers cannot consume a stack with nowhere recoverable to put it. |
| B11-09 | `placing.frame_pose` defaults to `false`. | Recheck every 3D item model and existing pose. Prefer upright 3D jars/tools; use `true` only where the intended presentation relies on the older item-frame transform. | Pliers, compound, gems, blanks, and addon items rest correctly after reopening/F3+T. |
| B11-10 | `placing.hover_lift` defaults to `0`. | Keep precise objects still on hover. Use outlines, descriptions, and optional inspection, not jumping gems. | Hover does not move a target away from the pointer or disturb its neighbours. |
| B11-11 | `StationBehavior.onKey` and `onChar`. | Add folio search with explicit focus. Public hooks serve uncovered stations; the existing ScreenOverlay needs the scoped event bridge in section 9. | Typing A/D/E/Q does not move stations, close the workshop, or drop items while the field owns input. Normal controls return on blur. |
| B11-12 | `MenuBinding.Builder.alsoAccepts(ResourceLocation...)`. | Conditional compatibility feature, not the authority model for Jewelry crafting. Use only if a real optional menu-backed adapter accepts an equivalent registered menu. Validate its slot mapping. | No blanket menu acceptance; absence of the other mod is harmless. Otherwise record “not applicable: SERVER stations.” |
| B11-13 | `count_facing: camera` for slots; `count_facing` for outputs. | Use native camera-facing counts on the three bound supply slots. Apply output counts only if introducing a real output binding; the clamp remains Jewelry-owned. | Counts remain readable beside glass, chests, outlines, and particles. |
| B11-14 | Mouse Tweaks gestures now work across stations according to supported configuration. | Let Diegesis handle new native storage targets; retain custom precision-work input. Remove overlapping handlers only where transfer ownership changes. | One gesture causes one set of transfers, with no phantom pickup or double spend. |
| B11-15 | Wheel with a carried stack deposits/takes one; middle drag distributes a stack. | Reserve scrolling over actual slots for inventory operations; use dedicated work controls elsewhere. Document the full-inventory edge case in section 3. | Exact counts after rapid wheel movement, middle drag, partial stacks, and denied slots. |
| B11-16 | Scene lights illuminate surrounding models; flicker is slower and smoother. | Add a restrained lamp; verify scene-model versus procedural/GeckoLib lighting. Custom painters do not automatically receive all model illumination. | Readable in darkness/daylight and shader/no-shader setups, without washed-out gems or flashing. |
| B11-17 | Large render-distance projection could hide painted interfaces and break picking. | Use public projection/painter APIs. Regression-test folio, labels, inspection, and picking at large render distances. | Visual position and hit position agree; no disappearing folio or missed slot. |
| B11-18 | Hidden-block return no longer blinks while asynchronous chunk rebuilding catches up. | Keep the existing correct `isSceneDrawn` BER handoff and visible base blocks. Use `isBlockHidden` only for an actual hidden-block replacement. | Enter/exit, reopen during closing, unload/reload, and neighbour navigation have no missing/double frame. |
| B11-19 | A hidden block no longer occludes adjacent block faces. | Rely on upstream fix; remove any local workaround that replaces neighbouring geometry. Test solid neighbours and corners. | No holes in walls beside the workshop. |
| B11-20 | Camera-facing counts no longer get clipped by later-rendered chests, glass, and particles. | Use built-in counts rather than custom depth overrides. Note that camera-facing counts deliberately render on top. | Counts are visible without turning all workshop text into through-wall overlays. |
| B11-21 | Highlights now use Diegesis’ own white texture rather than white concrete. | Use native slot highlights and verify high-contrast resource packs. | Retexturing vanilla concrete does not recolor slot highlights unexpectedly. |
| B11-22 | Carried stacks remain in front of upright stations and avoid a giant first frame after drawer pickup. | Author appropriate unrestricted carry planes for upright tool/folio regions; stop compensating with arbitrary carry offsets. | Pickups from drawers to close camera views never flash oversized or travel behind the bench. |
| B11-23 | Smaller placement/physics fixes. | Cover transformed surfaces, overlapping shelf rooms, support chains, thin icons, fallback placement, and transitions described in section 3. | Dedicated placement regression suite and recorded visual cases. |

Evidence: [release][D1], [comparison][D2], [scene documentation][D4], [StationView][D5], [StationBehavior][D6], [placing options][D7], [surface contract][D8], [physics parameters][D9], [menu binding][D10], [render changes][D11].

## 3. Source-level migration details that must not be missed

### 3.1 Physics is presentation with synchronized poses, not a crafting authority

The tagged `PlacedPhysics` implementation simulates for the focused client and sends settled poses. Other viewers follow synchronized state; leaving focus stops local simulation. The server action path sanitizes poses, checks the placed group and ownership permissions, and handles item transfers. This is not a server-side gemstone machining engine.

Consequences:

- Never award quality, damage a gem, consume polishing compound, change a recipe, or grant XP from a local collision, visual overlap, fall, or animation completion.
- Derive actual work from server-validated inputs and persisted work records.
- Never read or import `com.pockyl.diegesis.impl.*` from Jewelry. It was inspected to understand behavior, not to become an integration dependency.
- Do not promise continuing physics in unloaded chunks or while nobody is working.
- Use one active operator by default. Shared observation is useful; concurrent clients steering the same loose bodies is unnecessary for this update.
- Model-derived size/density affects library physics. Beta 11 does not expose a general per-gem density/material API. Do not invent one or use item scale to change intrinsic gem size.

[Physics implementation][D12], [server actions][D13], [public station rules][D14].

### 3.2 Spill handling has real inventory consequences

A loose item that crosses an open edge can move into the operator’s inventory; leftovers become world item entities when that inventory is full. It is not guaranteed to remain safely “somewhere on the bench.” The new spill action respects `mayTake` through the station slot’s pickup check.

Implement these boundaries:

- Default precious-stone surfaces have **closed edges**: `"open": []`.
- Use `"fall_off": "held"` for any deliberately open recovery edge, so a bumped neighbour does not eject itself.
- Default `hand_pushes` to false and `toss` to zero. Players can still deliberately reposition placed objects with the new weighted drag/turn behavior.
- Keep clamp, sockets and active stones outside native placed storage. Unpaid supplies remain real accessible storage; costs leave that owner at the existing first-use boundary. Do not invent finish escrow.
- Ensure `mayTake/mayPlace` and the existing operation guards enforce operator/storage rules on native pickup, shift-click, wheel, collection, pose changes and spill. Sockets/clamp never become native loose storage.
- Hiding a drawer is presentation, **not a permission boundary**. The server must still deny disallowed item changes.
- Never implement recovery by copying a displayed item and then leaving its owning slot intact.
- If an optional “open work mat” resource variant permits spills, describe that behavior and test inventory-full drops. Do not make it the default.

### 3.3 Drawer transforms and placement surfaces

`setSurfaceTransform` uses **station pixels**, from authored surface coordinates to their current position. Use a reusable matrix and a single animation progress value shared with the drawer model. Avoid cumulative transforms and double rotation by block facing.

The API commentary suggests authoring a sliding drawer at its open endpoint; the scene guide also discusses a closed endpoint for partially opening doors. For Jewelry, use the simple, unambiguous **open endpoint** for sliding drawers. Start with translation-only drawers, which do not require assumptions about gravity in a rotated container.

- `setPlacingVisible` is frame-scoped; set it each frame while hidden.
- `setSurfaceVisible` persists until changed; set each relevant surface in `onSceneReady` and on state transitions.
- The schema has one root `placing` configuration/group per scene. Multiple physical drawers are **surfaces of that group**, not invented parallel `placing` objects.
- Surfaces should not overlap ambiguously in authored coordinates. The implementation chooses the highest relevant floor and follows support relationships when resolving stacked items.
- A click-drag stays on its original surface when the cursor goes beyond the edge; do not add a competing “move to the shelf behind” handler.
- F3+T must reconstruct visible drawer, contents, enabled hit targets, camera preference, and selected work step from retained state.
- Light pools follow transformed surfaces, but the source excludes transformed surfaces from the static occlusion list used for model lighting. Do not promise moving drawers behave as fully dynamic light occluders. Test and tune the lamp accordingly.

### 3.4 Spatial room and logical capacity are different

`hasRoomFor` is a client scene query, not an inventory reservation or server guarantee. It returns false without placing. Slots marked `placed()` are skipped by default inventory shift-click/collection routes; an intentional quick-load shortcut therefore needs Jewelry behavior and a validated server action.

Required quick-load sequence:

1. Classify the actual source slot/stack and intended operation.
2. If it targets loose storage, show “Open the tray” or “Make room” when the local visible layout cannot fit it.
3. Send source slot identity, requested count, expected station revision, and action/session ID. Never send an authoritative ItemStack.
4. Server validates ownership, source contents, allowed destination, logical free capacity, and work locks; transfer at most what fits.
5. If geometry and slot capacity disagree, retain every item in bounded real storage and expose it through the recovery list. Reject further inserts rather than relying on an unlimited invisible overflow.
6. New items without poses get the library’s free-spot placement; do not depend on its precise packing order for gameplay.

The fallback placement code still has a slot-based fallback when a free position cannot be found. “There is an empty slot” therefore does not prove “the rendered surface is uncrowded.”

### 3.5 Changed defaults and public shapes

Audit both JSON and Java construction:

- `frame_pose = false`: 3D model orientation may change from the former item-frame pose.
- `hover_lift = 0`: remove obsolete compensating hover offsets.
- `placing.counts = never`: add counts explicitly where needed.
- `physics` is absent by default; this is opt-in.
- `open = []`: default surfaces have walls on all sides.
- `headroom` defaults to clearance; it governs carried-object clearance independently when specified.
- `fall_off = any` **inside enabled physics**: override to `held` where any open edge exists.
- `hand_pushes = false` and `toss = 0` are deliberately gentle defaults.
- `OutputBinding` gains count facing and retains a three-argument convenience constructor; `PlaceSurface` also gains fields and compatibility overloads.
- `PlacingOptions` grows from its previous canonical record shape. In the inspected change, the old 15-argument canonical constructor is not preserved by the added 16-argument overload. Replace direct construction with the supported builder or explicit beta 11 signature.
- Audit exhaustive switches for the added orientation and count-facing enum values.
- `count_facing` belongs to **slot/output bindings**. There is no corresponding documented `placing.count_facing` field; placed counts face the front. Do not add unsupported JSON.
- Strict decoding rejects unknown fields. Use the **tagged** schema and actual runtime decoder, not an evolving main-branch schema.

### 3.6 Input precedence and real edge cases

- `onKey` can consume normal station navigation/exit keys and receives key repeats. Return true only for a focused field or an explicit Jewelry command.
- `onScroll` is offered before normal control/placed-object rotation. Do not consume all wheel events to rotate the workpiece.
- Native middle-drag distribution at a server station is not creative duplication. Full-stack cloning is a separate creative **MENU** behavior.
- The beta 11 “scroll up to add one to the carried stack” implementation uses a temporary empty player inventory slot. With no spare slot, it can intentionally do nothing. Provide understandable fallback instructions such as “Put the stack down or free an inventory slot”; do not promise that wheel gesture always succeeds.
- Mouse Tweaks’ drag flags are read from its configuration once in the inspected implementation. Test changed settings after a client restart; do not promise live configuration reload.
- Do not install another wheel/Mouse Tweaks shim in Jewelry. Preserve native semantics and use server slot rules to reject invalid operations.

### 3.7 Rendering lifecycle and local light

A scene replacement must remain visible until the real block mesh is back. `isInUse()` alone ends too early. Use `isBlockHidden()` for the replacement body and `DiegesisClient.isSceneDrawn(pos)` for the existing block-entity renderer handoff where appropriate. Preserve any idle model parts the scene does not replace; suppress only the renderer content the scene actually owns.

The beta 11 lighting path brightens nearby **scene** geometry, with specific support for opaque `lit_as_block` models. It is not Minecraft block-light propagation and does not automatically illuminate neighbouring world blocks or affect mob spawning. A work lamp can improve the close-up without requiring a real gameplay light source.

Camera-facing counts are intentionally drawn after particles and without normal depth occlusion. Use that facility for counts only; a persistent label should not reveal hidden inventory contents through a shut drawer.

## 4. Preserve the beta 10 integration already present

This is a beta **10 → 11** update. The following foundation is implemented in 1.2.1/1.2.2; retain it and test it rather than build a parallel version. [Architecture][J6], [adapter][J13], [client routing][J14].

| Existing behavior / upstream capability | Required treatment |
|---|---|
| `takes_items` and `onItemClick` | Retain wheel handle/stone/lap and bench clamp/dock/gem-tray placement hints. Adapt only targets intentionally handed to native slots; no click may issue both a Jewelry PLACE and a library transfer. |
| `onScroll` | Preserve lifted collar indexing and tweezer rotation. Return false over native storage or a real carried stack when no Jewelry work control owns that gesture. |
| Filtered compact inventory drawer, `carry_size` | Keep `drawerShowsEverything`, request-only compact layout and calibrated carry size. Re-test beta 11 before removing any documented carry workaround. |
| JEI recipes/uses | Keep `StationLookups` and registered child screens; R remains a work command when applicable. Release controls while JEI covers the station. |
| `camera.interactive_at` | Retain 1; the opening click cannot become a work action. |
| `clear_view`, neighbour settings | Keep blocks/entities visible and neighbour navigation disabled. A new drawer does not require hiding the entire workstation or introducing multi-block navigation. |
| `moveCamera`, `hidesBlocks(Span)` | Conditional only if authored new geometry needs them. Do not add them to satisfy an API checklist. |
| `setOutlineSeenOnly` | Useful for newly introduced native slots/placed items; the old painted pick zones have no native item outline. Accept documented shader fallback. |
| `inventory_drawer.layout: hotbar` | Remains deliberately unused: preserve the compact, temporary drawer. |
| `toScreen`, `stationScreenClass` | Available if a genuine overlay-positioning need arises. Do not introduce a permanent JEI inventory grid. |
| MENU authority, `MenuBinding`, `MenuAccess.placeRecipe`, vanilla menu key handling | Not the model for these stations; no vanilla container is present. Keep SERVER authority and Jewelry recipe validation. |
| InvMove, tilted-item depth/shadows, smooth painters, failed-open handling, labels/occlusion fixes | Retain upstream fixes and test relevant Forge renderer combinations. Do not claim Fabric/Iris validation from a Forge run. |

The new physical staging drawer in section 5 is separate from both the player-inventory drawer and the existing precise eight-cell gem tray.

## 5. Architecture and concrete implementation map

### 5.1 Extend the current engine

Keep `WheelOperations` and `BenchOperations` as the operation services; `WorkInputService` as the Jewelry packet entry point; `WorkSessionManager` as the lease/input-budget owner; `CutWork`/`BenchWork` as durable work; and `FacetSim`/`SettingSim`/`SurfaceSim` as simulations.

Do not add the earlier plan's parallel `WorkshopWorkRecord`, separate cut/setting session manager, or replacement transaction engine. A small shared storage-policy/helper is appropriate where native slots and existing operations need identical checks.

### 5.2 One real owner, with a deliberately narrow Diegesis bridge

**Current implementation:** both station schemas are empty. `WorkstationLogic.mayPlace/mayTake` return false; `onCustomAction` does nothing. Contents are real block-entity fields and are synced through existing block updates. Adding scene JSON alone cannot create functional library physics or native slot gestures.

**Chosen expansion:** preserve the precision work layout and introduce:

| Storage | Real owner | Diegesis role and allowed actions |
|---|---|---|
| Wheel stone/lap | Existing wheel fields | Keep current Jewelry mount/swap/take operations; no loose physics. |
| Bench clamp and eight sockets | Existing fields | Keep Jewelry operations only. Never bind active work as freely transferable slots. |
| Eight-cell gem tray | Existing `Tray` array | Keep deliberate tweezer selection and procedural gem rendering. Its numbered cells remain stable. |
| Three supply positions | Existing `Supplies` array | Bind as a kept **fixed** `supplies` group; native counts/transfer gestures, with shared ingredient/access policy. |
| Pliers dock | Existing `Dock` field | Keep current dock and wear binding; no native pickup path that bypasses work rules. |
| New four-stack staging drawer | New bounded block-entity `Staging` array | Bind as kept, placed `staging` group; loose physics on at most two authored drawer surfaces. |
| Player-inventory drawer | Actual player's inventory | Existing filtered UI, never copied into station storage. |
| Previews, dry fit, tutorial examples | No new real owner | Rendering/demo state only; never collectible. |

Four staging slots are an initial **design capacity**, not an observed upstream limit. Keep this bounded; larger capacity requires a demonstrated layout/performance reason. The new drawer accepts only actual workshop inputs already recognized by Jewelry, not arbitrary general-purpose chest storage. Materials in it are uncommitted. Moving them into a tray, supply, clamp or dock is an explicit validated action; the craft never silently consumes from a visually unrelated container.

This scope preserves the existing precise gem handling while using beta 11's full physical placement system for loose preparation. Do not convert the eight gem cells into native placed slots without also solving tweezer selection, item identity, moving hit zones, and duplicate rendering. Such a conversion is not required by this handoff.

Implementation:

1. Add the bench's fixed `supplies` and placed `staging` schema groups, both `kept()`, with correct group sizes and item predicates. Leave the wheel's empty schema alone.
2. Bind them to the owning arrays with public [SlotStorage adapters][D23]. Adapter setters update the same arrays and dirty/content revision used by Jewelry operations. Do not silently reject a write inside `SlotStorage.set` after the library has already taken a source; perform access/acceptance validation before transfer.
3. Add a public `StationHost`/`StationState` to the bench for persistent poses and compact presentation state. Keep existing `GeoBlockEntity` support. Bind storage before serialization/idle rendering so an unbound placeholder cannot serialize a second inventory.
4. Save the new staging items once in the block entity. Bound groups are omitted by `StationState.save()`; save the latter separately for poses/controls. Verify load order, server binding, client update tags and idle/late-observer state. The existing constructors/setters do not yet provide this automatically.
5. Continue using `WorkstationLogic` for leases. Permit only the named storage groups and valid indices in `mayPlace/mayTake`, after checking the current operator, loaded station, reach/interaction policy and allowed item. Keep unrelated groups and custom actions denied.
6. Reuse `BenchOperations.isSupply/accepts` semantics, factoring predicates where needed. Allowing supplies to be withdrawn is not a refund: paid materials are already gone; an unpaid finishing action must still find its costs.
7. Remove the old Jewelry PLACE/TAKE handlers for **the specific supply nodes converted to native slots**. Retain them for clamp/dock/gem tray. Each pointer gesture has exactly one transfer owner.
8. Handle transfers out of staging with a bounded Jewelry command identifying source group/index, requested count and expected content state. Resolve the actual stack server-side, apply existing target rules, decrement the owner exactly once, and clear/reset the pose when identity changes. Never send an ItemStack as authority. Introduce explicit command kinds for setting the drawer's desired state, moving a staged stack, and staging selected materials; do not overload SEAT or arbitrary custom-action strings. Keep the staging request within the existing argument-byte budget, or change that budget deliberately with matching decoder limits and tests.
9. Keep keyboard/walk-up retrieval for owned items. Hidden/crowded physical storage needs an accessible list of its real four slots; it must not create a second overflow inventory.
10. Add staging contents to the existing single `dropContents` path. Packing a committed workpiece carries its frame/sockets/work only; loose staging, tray, supplies and tools remain on the bench. Do not also drop bound slots through a generic Diegesis drop loop.

The current survival/creative semantics differ between some bespoke operations and native inventory gestures. Make this explicit: **native storage transfers move real cursor/inventory stacks even in creative**; they do not clone as MENU creative pick does. Preserve intentionally creative no-cost crafting, and test that moving a stack never also invokes the custom copying path.

### 5.3 Precision rendering and physical storage must agree

The existing `BenchPainter` draws detailed gems in fixed tray cells and depicts supplies as filled jars. A library binding must not draw a second copy:

- Keep procedural tray stones, seated stones, clamp and tools under their current painter/rig ownership.
- Convert supply interaction/count rendering intentionally: use native item-slot visuals at calibrated jar/pad positions, or redesign the jar presentation so the owned stack has one readable representation. Do not hide a stack while leaving an unrelated large native collider.
- Native staging bodies use actual item-model geometry. Test cut gems, pliers, blanks, compounds and addon materials with beta 11's upright model pose and physics bounds. Keep the existing detailed worked-stone renderer for actual machining; do not infer damage/size from loose body motion.
- All moving drawers use the same animation progress for model geometry, surface transforms and any fixed supply nodes that move with them. `setSurfaceTransform` affects placed surfaces, not arbitrary fixed nodes.
- Maintain the `isSceneDrawn` handoff and Ponder-preview exception in both renderers. Ponder's fake world must not consult a real bench's slot state or lease.

### 5.4 File-level work

Paths below are relative to `src/main/java/com/otectus/immersivejewelry/` unless stated otherwise.

| Existing location | Work |
|---|---|
| `gradle.properties`, `build.gradle`, `META-INF/mods.toml` | Pin beta 11, preserve other library dependencies/sides, bump Jewelry protocol if payloads change. |
| `workstation/station/JewelryStations.java`, `WorkstationLogic.java` | Narrow bench schemas/storage rules; retain wheel authority, reach, orientation and leases. |
| `blockentity/JewelersBenchBlockEntity.java` | Four-stack staging storage, host/pose lifecycle, content revision, save/drop/load integration. |
| `blockentity/StationBlockEntity.java` | Preserve transient operator, prediction acknowledgement and throttled work sync; avoid adding a second simulation tick. |
| `workstation/BenchOperations.java`, `WheelOperations.java`, `WorkInputService.java` | Shared storage policies, bounded staging/supply gathering, stale-command checks. Preserve first-use costs. |
| `network/packet/WorkCommandPacket.java`, `WorkCommand.java`, `network/ModNetwork.java` | Command sequence/content preconditions and bounded batch staging; protocol from 4 to next value. |
| `client/workstation/WorkstationBehavior.java`, `WorkstationClient.java` | Public beta 11 key hooks, overlay bridge, releases, no duplicate JEI/key routing. |
| `client/workstation/BenchBehavior.java`, `WheelBehavior.java` | Physical drawer state, effective camera mapping, native/custom input separation. |
| `client/workstation/FolioOverlay.java` | Search/EditBox, ID-based selection, cached catalogue, material preview and accurate staging result. |
| `client/workstation/SceneLoader.java` | Preserve `diegesis/stations/` loader; comfort transformation, safe reload/re-registration, effective camera settings. |
| `client/workstation/render/`, `client/presentation/geo/` | Drawer model/painter ownership, count visibility, lamp/calibrated lighting and rig fallback. |
| `client/guide/Handbook.java`, `HandbookNavigator.java`, `HelpTopics.java` | Search-focus-aware H handling; current help/return policy; new storage/search guidance. |
| `client/compat/ponder/` | Existing simulations/preview clock; new storage demonstration with isolated demo data. |
| `client/compat/jei/StationLookups.java`, `integration/jade/JewelryJadePlugin.java` | New hovered storage and counts; preserve child-screen behavior and useful state hints. |
| `data/DefinitionEvents.java`, `client/ClientPacketHandler.java` | Keep reload lease release; invalidate new folio/storage-policy caches. |
| `tools/generate_*.py`, `art/jewelry_rigs.py`, `art/jewelry_poses.py` | Update the actual source-of-truth generators when they own changed assets; scenes are currently authored directly. |
| Existing domain/GameTest/smoke suites | Update changed contracts, retain all existing work and library coverage. |

Keep scene paths **`assets/immersive_jewelry/diegesis/stations/{lapidary_wheel,jewelers_bench}.json`**. Do not relocate them to auto-loaded `diegesis/scenes/`, which would bypass comfort transformation. Pin their `$schema` from upstream `main` to the beta 11 tag.

### 5.5 Dependency and protocol

Change the existing properties:

~~~properties
diegesis_version=1.0.0-beta.11
diegesis_curse_file=9643618
diegesis_version_range=[1.0.0-beta.11]
~~~

Keep the existing Gradle dependency:

~~~groovy
implementation fg.deobf("curse.maven:diegesis-1790227:${diegesis_curse_file}")
~~~

Verify the installed jar version and Forge's exact-range matching. Current range `[1.0.0-beta.10,1.0.0-beta.11)` explicitly excludes beta 11; updating only the downloaded jar will fail dependency resolution.

Keep Diegesis required on BOTH sides. Retain current Patchouli/GeckoLib BOTH-side and Ponder/Flywheel/playerAnimator CLIENT-side contracts and their pinned versions. No additional animation or particle library is required.

A dependency-only change would not itself demand a Jewelry protocol bump. The command extensions specified here do, so bump **4 → 5** unless local development has already allocated 5. Never reorder existing ordinal-encoded commands without a protocol change. Preserve the worn-appearance packet and its sanitized data contract.

## 6. Work persistence, transactions and interruption

### 6.1 Preserve the actual durable state

These are existing systems, not new implementation tasks:

| Existing state | Preserve |
|---|---|
| Wheel `Stone` / `Lap` | Exact items, appraisal, lap wear and stone Work compound |
| `CutWork` schema 1 | ID, seed, cut/recut, parameter/state snapshot, revision, `LapWorn` |
| Bench format 2 | `Clamp`, indexed `Sockets`, `Tray`, `Supplies`, `Dock`, `Work`, legacy `Overflow` |
| `BenchWork` schema 1 | Kind, PLAN/SETTING/FINISHING, simulations, `Committed`, `FinishPaid`, `RepairPaid`, `PliersCharged`, lifted mask, original stones, finalization |
| `unfinished_jewelry` carrier | Existing root/version and `Carrier:{Frame,Stones,Work}`; portable progressed work |
| Unreadable/newer bench Work | Preserve raw data and the existing pack/recovery path; never erase it while adding presentation state |
| Operator lease | Transient token/sequence/step budget; never restored as a live lock |

Keep no-countdown gameplay. Do not add “remaining work time” or resurrect the removed timing settings. View states such as prepared, paused, ready or recovery should be derived from these records and live lease, not persisted as a competing state machine.

### 6.2 Maintain first-use payment and reward ordering

- Appraise a raw source only on real mounting; previews/search/tutorials never roll it.
- At the first assembly closure, revalidate the design, knowledge, stones, finish and pliers requirement; call craft gates before commitment.
- Wear the lap at first contact and pliers at first closure once per recorded operation. Preserve bezel behavior.
- Charge finish/repair materials at first effective use, exactly once. Do not replace this with the previous handoff's up-front all-material escrow.
- Keep explicit confirmation or tool selection to advance from setting to finishing.
- Keep `GemCutEvent` and `JewelryCraftedEvent` ordering, event-adjusted results, foreign NBT, Smithing provenance, XP and advancements.
- A successful result remains in the owning wheel/clamp field; opening a drawer, loading a chunk, showing a rig or collecting the result never repeats rewards.
- Add native-storage actions only for uncommitted storage. They cannot expose socket contents, extract a committed frame, reset damage or fabricate an unfinished carrier.

### 6.3 Strengthen discrete commands before adding more inventory entry points

Continuous samples already have token, sequence, overlap and pacing checks. Discrete `WorkCommandPacket` has a lease token/work ID but no command sequence; most dispatch branches do not use that work ID. This is an observed **validation gap**, not evidence of a reproduced duplication exploit. [Packet entry][J22], [commands/operations][J5].

Add:

1. A monotonic command sequence per lease with duplicate rejection and a bounded command-rate budget.
2. Expected work identity where the command operates on a job.
3. Expected **content/structural revision**, or exact server-issued per-slot generation, for take/place/seat/pack/staging actions. Do not reuse the continuously incrementing simulation revision indiscriminately; ordinary latency must not reject every valid click.
4. Explicit validation of source/destination types and indices; only -1 means cursor, other negative source indices are invalid. Verify argument presence/type, finite seat coordinates and counts.
5. Server computation of missing supplies/material choices and exact transfer amounts. Never trust a client recipe index, stack copy or quality claim.
6. A bounded response/state correction explaining refusals and actual moved counts.

Test stale SEAT/PACK/TAKE after changing the job in the same lease. Coordinate native-slot and Jewelry mutations on the server thread, through the same content revision; do not assume ordering between independent channels makes stale requests safe.

### 6.4 Preserve interruption and help behavior

`WorkSessionManager` already releases on close, distance, damage, teleport/dimension change, death, logout, unload/break, data reload and shutdown. Its heartbeat neutralizes tools after input silence. Keep these mechanisms; extend teardown to new drawer/search/native-drag state.

- Closing or losing focus is not completion or cancellation. It lifts tools and leaves the exact accepted work.
- Cancel an uncommitted plan with current return rules. Irreversible work is packed/reworked, not refunded from an invented escrow.
- Keep the existing cooling interaction and simulation semantics; do not add a charged coolant resource or silently change heat on pause.
- Every scene/profile/resource rebuild releases held/toggle input and flushes a neutral boundary before replay/rebase.
- A JEI child screen retains the lease/work but receives no craft input. Handbook/Ponder help exits the station and releases the lease; no automatic resume on return.
- Busy fallback block use continues to check the same lease; native slot permission must too.
- Block break drops committed work as one carrier and all uncommitted contents once, including the new staging array.

Use saved-checkpoint conservation tests. Minecraft does not provide cross-file ACID transactions for arbitrary player/chunk saves; document the real crash boundary instead of promising universal crash atomicity.

## 7. Lapidary Wheel: retain the work, refine its presentation

The current wheel is already a complete physical interaction: quill/handle pressure and sweep, collar indexing while lifted, dop trim, cooling cup, specimen rack, loupe, early rough finish confirmation, four distinct cut motions and portable progress. Preserve these systems and their reference bots.

Implement these refinements:

- Add a working-side camera preference using beta 11 mirroring and the effective-camera mapping in section 10. Existing base shots are centered, so author the side offset deliberately.
- Keep the stone's facet guide attached to the actual modeled surface. Show heat/depth/force with shape or numbers as well as color, and retain existing numeric/accessibility options.
- Improve next-action text using the actual work state: mount/choose/lift/index/cool/finish. Make “why the collar will not turn” and “why the lap cannot be swapped” clear.
- Keep the cooling cup as the current interaction; do not invent tank charges or a new consumable.
- Make cut selection searchable only if the definition list grows beyond the specimen rack's practical size; reuse the folio query/editor component instead of a separate text-input system. The four default cuts remain directly selectable.
- Optional “remember this cut” stores only a local cut ID. Revalidate the next stone, lap and definitions; do not carry across appraisal, quality, seed or progress.
- Refine the loupe explanation of scratches/chips/rocking/depth using existing `CutWork` values. Any result explanation describes what actually happened.
- Re-test native wheel gestures from the player drawer. Scrolling over collar/handle is work input only when that control owns the interaction and no storage/carried-stack operation should win.
- Preserve the scene/BER/GeckoLib handoff, sound-loop ownership and first-person hands. The new lamp/camera must not cause twice-rendered quills or two grinding loops.

No new cutting simulation, deadline, tool cost or camera-driven quality calculation belongs in this update.

## 8. Jeweler's Bench: physical organization and deliberate preparation

### 8.1 Preserve the precise working area

Keep the existing form-aware clamp, numbered eight-cell gem tray, real pliers dock, tool rack, folio, pouch, procedural setting geometry and GeckoLib tools. All four forms, multi-stone designs, prongs, bezels and the three finishes remain complete.

Add a shallow **four-stack preparation drawer** with two clear areas/surfaces if they fit the measured bench geometry. Sliding it open should be useful, not another mandatory step on every craft. Keep frequently used precise gem cells accessible.

- Drawer surfaces have closed lips, gentle physics, no toss and no hand pushing by default.
- A second surface is useful for separating newly staged and spare materials; both belong to the same `staging` group.
- Use one shared, bounded drawer-opening state if observers see the physical drawer move. Persist only the desired presentation state/poses; never persist a live drag or lease.
- Gate native interaction while a surface is closed or moving through an unsafe position. Server authorization cannot rely on client visibility alone.
- Native fixed supply slots show useful camera-facing counts; loose staging uses hover counts. Do not draw counts over a closed drawer.
- Keep a non-physics, keyboard-accessible recovery/list route to all four real stacks if a resource pack changes geometry or a model no longer fits.
- Exiting leaves stored supplies intact and releases the cursor safely. The drawer is not a workpiece inventory refund.

### 8.2 Upgrade the existing “Stage stones” action

The current folio already stages matching inventory gems, ranking by cut quality. It issues individual PLACE messages and predicts missing quantities locally. Do not add a second “gather” button with overlapping semantics. [Folio][J15].

Replace/extend this action with a clear material-selection preview:

1. Show the selected design and chosen gem family, plus actual candidate stones (cut, quality, damage, size). Let the player choose the family/stone or retain predictable best-cut selection explicitly.
2. Count compatible already seated/tray stones correctly; staged loose storage is a source only when explicitly selected.
3. Show required finishing material separately and when it will be consumed. Drawing a preview commits nothing.
4. Send one bounded staging request with design ID and selected source-slot identities/preferences. Server recomputes remaining need and revalidates actual stacks/capacity.
5. Move only the needed count; don't send a whole-stack PLACE and subtract a smaller number only in the client estimate. This matters for stackable addon/test gems.
6. Preserve the same-gem rule, existing tags, sketch knowledge and future craft-gate check. Staging itself never grants a design or bypasses first-closure validation.
7. Report actual moved/missing counts after authoritative state, including a full gem tray, occupied supplies or changed inventory. Avoid optimistic “done” cues before the server acts.

The new drawer's “Move to work area” command uses the same service, not a copy of the preparation logic. `hasRoomFor` is relevant when putting something **into loose staging**, not when seating a gem or filling a fixed supply slot.

### 8.3 Keep setting, maintenance and completion coherent

- Seating remains deliberate, with the current seat offsets/turn used by the setting simulation. Loose physics must never determine seat quality.
- The actual pliers remain in the existing dock, and existing wear code uses that owner. No new hand/cursor tool-durability source is needed.
- Preserve prong/opposite-prong and bezel interactions. Keep explicit transition to finishing and the existing cloth, burnisher and brush/cloth phases.
- Retighten, remove, repair and refinish use the current services and costs. Removed stones use the existing tray/recovery rules; do not auto-route them to physically hidden storage.
- A “dry fit” is the current reversible PLAN state plus a clearly non-owning visual preview. Do not add a parallel reservation/escrow phase.
- The actual result remains in the clamp. A short optional inspection turn is skippable, never consumes a second input or awards another event/XP.
- Repair/refinish preserve foreign data and Smithing provenance. Packing/unpacking never includes a visual-only duplicate or silently carries unrelated supplies.

Give players a concise reason when they cannot proceed. Reuse existing hint/cue and notice-duration settings; descriptions should match server rules and point to the relevant handbook page or lesson.

## 9. Searchable folio and input routing

### 9.1 Implement search in the existing folio

`FolioOverlay` currently filters by mounted form and favorites; selection is an integer index and `designs()` reconstructs/sorts the recipe list repeatedly. Extend that class rather than replace the handbook or build another recipe browser.

- Search localized design/form/gem/cut/finish names and resource IDs; query and preferences are local, not broadcast.
- Add known-design, compatible and available-material filters. Label material availability accurately; a server progression gate may still refuse commitment.
- Select by stable resource ID, not row index. Changing a filter, translation or recipe order cannot silently select another design.
- Preserve the search field/clear control when results are empty. Keep required materials and unknown-sketch explanations readable.
- Cache the catalogue/query results; invalidate on definition/recipe/tag/knowledge updates, language/resource reload and relevant inventory changes. Do not sort the whole recipe catalogue on every rendered row/frame.
- Use a standard Minecraft `EditBox` or equivalent supported component for editing/clipboard behavior, with bounded text length. Route characters, not printable key codes. Test Unicode and actual IME behavior; use a supported accessible child text screen if necessary.
- Keep existing favorites, finishes, rework tab and help link. Search is selection assistance, never a recipe teach/craft command.

### 9.2 Important beta 11 limitation: overlays suppress the new hooks

In tagged beta 11, `FocusScreen.keyPressed` and `charTyped` call station `onKey/onChar` only when **no ScreenOverlay is active**. `ScreenOverlay` itself has mouse/render callbacks and no key/character callbacks. Escape/inventory closes an overlay before behavior hooks are considered. The current folio is such an overlay. [Actual routing][D21], [overlay API][D22].

Use this concrete division:

| Input context | Route |
|---|---|
| Uncovered station | `WorkstationBehavior.onKey` delegates to existing work-key logic, then JEI lookup if unconsumed; `onChar` only for an actual non-overlay editor. |
| Open folio/inspection overlay | A narrowly scoped Forge `ScreenEvent.KeyPressed.Pre` bridge routes to that overlay first. Folio editing also needs `ScreenEvent.CharacterTyped.Pre` (`getCodePoint()` and modifiers); compile against the actual Forge 47.4.23 dependency. |
| Key release | Keep the scoped `KeyReleased.Pre` bridge: beta 11 has no public behavior release callback. Always release relevant held work keys safely. |
| JEI child screen | Its own keys/characters only; retain current neutralization and child-screen registration. |
| Patchouli/Ponder help | Current explicit exit/help navigator policy, with text-focus-aware help dispatch. |

Do not dispatch a press once through the old global handler and again through `onKey`. Refactor `WorkstationClient.onKey` to handle only overlay-specific work; normal station press/JEI routing moves to the public hook. Do not remove the overlay bridge merely because beta 11 added keyboard callbacks. The character-event name/accessor are present in [Forge's 1.20.1 ScreenEvent source](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/src/main/java/net/minecraftforge/client/event/ScreenEvent.java).

`Handbook.onScreenKey` independently handles H through a Forge pre-event. It must consult the same explicit text-input ownership before opening help. Simply consuming H later in `onKey` is too late. Keep the world/inventory/JEI help behavior.

### 9.3 Focus and repeated-key rules

1. Entering search releases every held/sticky/toggle craft action and shows a caret.
2. While editing, letters—including E, Q, R, U, H and B—are text, never exit/drop/help/work shortcuts.
3. First Escape blurs search without closing the folio; next Escape closes the folio; later Escape follows normal drawer/station policy.
4. When the folio owns input, do not fall through to JEI just because a stale hovered stack is cached behind it.
5. Backspace/arrows may repeat; toggle-use, change-tool, confirm, stage, pack and collect run once per physical press. Existing toggle handlers are not inherently repeat-safe.
6. Opening a child screen, closing a folio, losing the window, reloading or releasing the lease clears the input owner and held-key state.
7. Test real keyboard/character events through Minecraft, including a held H and remapped keys; direct `folio.key()` calls alone cannot validate precedence.

Keep public station APIs and Forge events only. Do not reflect into `FocusScreen` or read its private overlay field.

## 10. Scene authoring, camera, assets and accessibility

### 10.1 Illustrative beta 11 fragment

This fragment is schema-valid against the tagged beta 11 schema, but is **not calibrated to the existing bench**. It assumes the new four-slot kept/placed `staging` group and the existing three-slot `supplies` array bound as a kept fixed group. Merge the relevant fields into the current authored scene through the existing loader; do not replace its work nodes, glances or compact drawer.

~~~json
{
  "$schema": "https://raw.githubusercontent.com/Pocky-l/diegesis/v1.0.0-beta.11/docs/schema/scene.schema.json",
  "camera": {
    "position": [6.5, 27, -5],
    "target": [8, 15.4, 7.2],
    "fov": 46,
    "transition": 0.4,
    "interactive_at": 1
  },
  "work_plane": 14.5,
  "placing": {
    "group": "staging",
    "surfaces": [
      {
        "from": [2, 3],
        "to": [14, 13],
        "height": 14.5,
        "clearance": 10,
        "headroom": 14,
        "open": []
      }
    ],
    "item_size": 2.5,
    "scale_range": [0.8, 1.2],
    "counts": "hover",
    "frame_pose": false,
    "hover_lift": 0,
    "physics": {
      "friction": 0.75,
      "rock": 6,
      "swing_angle": 4,
      "toss": 0,
      "hand_pushes": false,
      "fall_off": "held",
      "bounce": 0
    }
  },
  "nodes": [
    {
      "id": "supply_0",
      "pos": [1.6, 14.2, 1.9],
      "slot": {
        "slot": "supplies[0]",
        "size": 1,
        "counts": "always",
        "count_facing": "camera"
      }
    }
  ]
}
~~~

The fragment's surface spans the work area for illustration. The final drawer surface must be outside the clamp/tool collision space; measure it with the actual rig. Add nonoverlapping surfaces/open endpoints for the final drawer geometry. Keep native slot visuals/colliders and the old jar painter from drawing competing objects.

### 10.2 Physics tuning

| Fields | Policy |
|---|---|
| `gravity/max_speed` | Short settling, no floaty or violent tiny objects. |
| `friction/bounce` | Good grip, zero bounce by default. |
| `upright_flat/rock/flat_depth` | Thin-icon readability and minimal rocking; test real modded model bounds. |
| `hover/carry_lag` | Minimal lift/lag and clear destination. |
| `reach/climb/headroom` | Passing neighbours without clipping the drawer roof. |
| `swing/swing_angle/swing_calm` | Small, quick-settling motion; calm mode removes it. |
| `hop_time` | Brief surface transitions. |
| `toss/hand_pushes` | Zero/false by default. |
| `hand_force/turn_power` | Predictable weighted handling across actual workshop items. |
| `fall_off/open` | Closed lips; held-only fall-off for any deliberately open resource variant. |
| `stacking/scale_range/snap` | Bounded clutter and scale; no effect on gemstone quality/size data. |

Do not tie cosmetic collision mass to hardness, crafting force, tool wear or outcome.

### 10.3 Camera mirroring requires new framing and correct mapping

Both current main shots use position X=8 and target X=8. Reflecting them across X=8 changes nothing. Author a modest asymmetric shot that helps see the clamp or wheel contact, then use `mirrorCamera(8F)` for the other working side; `Float.NaN` disables the mirror. Preserve no-zoom and instant-transition preferences.

The current `SceneLoader.camera()` returns **authored** coordinates. Both behaviors cache camera-derived vectors in `onSceneReady`; the bench's `onPiece` reconstructs a ray from that cached camera, and the wheel uses `right/up/kSweep/kHeight` for handle movement. These must follow the **effective** camera, including the mirror. [Loader][J16], [bench][J21], [wheel][J23].

- For a mirror plane p, reflect camera and target X as `x' = 2p - x`, then recompute the basis and mapping. Do not merely negate mouse X.
- Apply the same effective authored camera parameters to rendering and coordinate mapping, using supported public information. If a glance/offset/transition is active, either map against that actual view or disable precise work until it returns.
- Do not change side during live contact. Release controls, switch/ease safely, recalculate, then require a fresh input.
- Keep all simulation targets in canonical block/piece coordinates. NORTH is the authored front (-Z); sixteen scene pixels equal one block.
- Mirroring changes camera composition, not world geometry, numbered sockets, text, body handedness or tool ownership.
- Verify work-point correspondence in four block facings, both side views, resource-pack camera overrides, GUI scales and no-zoom mode.
- Keep neighbour navigation disabled and the arrival threshold at 1.

### 10.4 Extend the existing comfort loader

Preserve `noZoom`, `instantTransitions`, `reducedMotion`, hands, toggle/sticky input, sensitivity/target scale, numeric hints, approved assists, sound, translucency and particle settings. Preserve the separate presentation body-animation/tutorial-motion/worn-jewelry options.

Beta 11 has no verified public runtime physics toggle, but this repo already owns a public scene-definition pipeline: resource JSON → comfort transforms → `SceneDefinition.CODEC` → `DiegesisClient.registerScene`. Use that pipeline.

- Add a clear local loose-item-motion preference; reduced motion uses the calm treatment by default.
- When calm is chosen, omit `placing.physics` or use deliberately reduced values in a copied JSON definition before codec parsing. Preserve the same groups, item ownership and stored poses.
- Use a safe resource/scene rebuild. The existing `onConfig` calls `SceneLoader.refresh()`; test how beta 11 rebuilds live scenes. If a focused scene is not safely replaced, release/close it and defer re-registration/reopen, with a concise notice. Do not mutate library internals.
- Initialize surface visibility/transforms/camera preference in `onSceneReady`, and reapply frame-scoped placing visibility every frame.
- Keep steady light, reduced transitions, no shake/pulse, keyboard retrieval and adjustable sound. A drawer animation cannot gate access indefinitely.
- Verify config reload, F3+T, failed scene decoding and missing resources leave the player able to exit and retrieve owned items.

A standalone calm resource pack may also be offered, but it is not mandatory when the current loader can provide the supported feature directly.

### 10.5 Lighting and renderer ownership

Beta 11 model illumination primarily targets scene models marked `lit_as_block`; Jewelry's work is largely a custom painter and GeckoLib draw. A lamp JSON node alone does not prove every procedural gem/rig receives that illumination.

Add a restrained task lamp with a steady/off preference. Author its opaque body in the correct lighting path. Inspect the painter/rig buffers and light inputs; preserve Minecraft lighting, gem tint, shadows and finish contrast. If dynamic procedural lighting needs explicit supported integration, implement/test it in Jewelry's renderer or document the narrower effect honestly. Do not promise scene lights illuminate world neighbours or affect spawning.

Current base blocks remain visible and both BERs already use `DiegesisClient.isSceneDrawn`. Preserve that ownership. Use `isBlockHidden` only if a new model truly replaces a hidden block; the wheel's use of `isInUse` for its motor sound is not the hidden-block bug.

Camera-facing counts deliberately render above later particles/geometry. Use them for visible supply counts, never a label that reveals a shut drawer's contents.

### 10.6 Update the already implemented library content

- Extend the existing Patchouli folio/storage/controls/help pages and live cards, in all five locales.
- Add concise Ponder lessons for safe staging, quantity gestures and drawer organization, reusing the current preview level/clock and material rules. Tutorial seeks/replay cannot send packets or modify real inventory.
- Add drawer/lamp geometry through existing rigs/painters and asset sources; retain the fallback renderer.
- Add body poses only if a visible new action benefits from them. The present body layer is state-driven; never replay a pickup pose on every synced pose packet.
- Update `docs/ASSET_MANIFEST.md`, generated assets and translation/key coverage tests. Keep the established 33 lessons working before adding more.

## 11. Compatibility, reloads and save migration

### 11.1 Keep the current integration stack complete

| Integration | Required regression |
|---|---|
| Immersive Smithing | Blanks, quality/provenance and fallback recipes; unfinished carriers remain excluded from recycling. |
| Curios and built-in slots | All forms, gates, broken/inactive behavior and worn visuals; test both presence and absence. |
| JEI | Current categories and R/U precedence; real hovered supply/staging stacks; child-screen return with neutral controls. |
| Jade | Actual work/stage/operator and concise stored quantities; no private search query. |
| Progression API | Gate denial before first closure, server-clamped assists, cut/craft/rework events and contributor data. |
| Patchouli | Update live controls/storage/folio/care cards and all translated pages; handbook item ID/trade stay unchanged. |
| Ponder / Flywheel / Create | Extend existing lessons and isolated demo data; same source simulations and preview clock. Validate the compatible Create runtime without duplicate bundled dependencies. |
| GeckoLib | Preserve rigs, per-station instance cache and plain-part fallback; new drawer/lamp poses share the actual scene transform. |
| playerAnimator | Preserve FULL/REDUCED/OFF, first-person exclusion, interruption priorities and no late-observer replay. New camera side is not a switch to the player's dominant hand. |
| Worn-jewelry snapshots | Preserve the protocol-4 addition and sanitized appearance; search/storage updates do not broadcast unrelated item history. |
| Mouse Tweaks / InvMove | Native gesture flags and input precedence; custom work cannot double-handle a native transfer. |
| Embeddium / Oculus / Blur / body or emote mods | Actual Forge combinations, including clear-view restrictions and competing body-animation layers. |

Do not claim NeoForge/Fabric support or bump unrelated libraries merely because Diegesis beta 11 exists.

### 11.2 Reload policy: preserve the implemented behavior, finish its edge cases

Current `DefinitionEvents.onDatapackSync` already invokes `WorkSessionManager.onReload` for a server reload, ending leases. Existing work retains simulation parameters. Keep that behavior.

The existing code does **not** snapshot every future cost: for example, `BenchOperations.paid` resolves the current finish/repair ingredient when first charged. Do not describe the entire recipe contract as frozen.

Implement/document this policy:

- Preserve captured simulation parameters and paid/wear flags.
- Before the next irreversible operation and completion, revalidate required recipe/form/cut/finish definitions and stored item identities. Missing/incompatible definitions must produce a readable refusal with intact portable/recoverable work, not a fabricated substitute.
- For unpaid work, show the currently applicable cost after reload and before the first charged stroke. If the definition changed incompatibly with already committed work, keep the carrier intact and require explicit recovery/replanning where legitimate; do not quietly reinterpret seated stones or erase progress.
- Already paid work is not charged again when recipe cost changes.
- If future implementation chooses to freeze additional cost/output contracts, add a versioned field/migration deliberately; never pretend old schema-1 work contains information it did not store.
- Invalidate new folio caches on definition, recipe, tag, language and knowledge changes. A removed design never becomes another row through index clamping.
- F3+T/profile changes are presentation reloads: no appraisal, quality reset, consumption, XP or lease resurrection.
- Keep the searchable catalogue local and derived, not inside `StationState.data()`, which syncs as a whole compound.

### 11.3 Migration from the actual 1.2.2 baseline

Prefer additive presentation/storage fields; retain bench format 2 and work schema 1 unless a real domain change requires more.

| Existing data | Upgrade behavior |
|---|---|
| Wheel `Stone/Lap`, nested schema-1 `Work` | Load unchanged, including wear marker and exact simulation state. |
| Bench `Format=2`, `Clamp/Sockets/Tray/Supplies/Dock/Work` | Same owners, counts and NBT; native bindings point at the same supply array. |
| Portable unfinished carrier | Same item/root/schema; unpack/pack with the existing operation ID/payment markers. |
| New `Staging` field absent | Initialize four empty slots. Do not populate by copying gems/supplies. |
| New scene-state compound absent | Create default safe poses/closed drawer. Presentation reset never resets work. |
| Present poses but missing/changed models or surfaces | Validate finite/bounded poses; re-home presentation safely, retain every owning stack. |
| Unknown Work schema | Keep raw data and the existing unreadable/packed behavior. |
| Live operator/input state | Remains transient and cleared; loading cannot resume contact. |

If adding a higher bench FORMAT, rewrite the current legacy dispatch carefully: it tests `Format < FORMAT && !Clamp`. A newer format number alone can cause an older **empty-clamp format-2 bench** to be mistaken for format 1. Prefer a separate presentation schema, and test format-2 benches with tray/supplies but no clamp.

### 11.4 Retain 1.1 and Jewelcraft compatibility

The current `loadLegacy` already moves `Workpiece` into Clamp, recovers real Escrow into Tray/Supplies once, preserves Overflow for dropping, and ignores `DisplayStone/Progress`. Keep it, along with existing Jewelcraft registry aliases/NBT conversion.

Regression-test old format-1 saves, repeat load/save, full overflow, empty workpieces and the new staging/host fields. Do not run an additional generic refund over the same escrow; do not reinterpret the old progress scalar as a new simulation.

Keep names, enchants, defects, maker, integrity, contributor NBT and Smithing provenance on all legacy/current pieces. Document any intentional save incompatibility accurately.

## 12. Performance, networking and diagnostics

The current engine already samples 20 ms inputs, sends batches of five at about ten messages per second, bounds hitch batches, validates pacing and throttles continuous block updates to ten per second. Preserve those limits. Do not rebuild that transport or send the same craft input through Diegesis actions.

Measure the added cost of four loose staging bodies, drawer transforms, counts and lights independently of existing work/rigs:

- No custom work-progress traffic from idle rendering; no per-frame recipe sort, NBT serialization or geometry rebuild.
- Use dirty updates for bound storage/poses. Avoid duplicate full block updates from both library setters and Jewelry setters when the library already publishes a change, without losing idle observer updates.
- Bound staging/gather requests, discrete command rate and payload sizes.
- Diegesis' documented 40-actions/second budget applies to its own actions, not automatically to Jewelry's separate packet channel. Give Jewelry commands their own appropriate budget and preserve continuous sample pacing.
- Reuse surface matrices and geometry. Do not force chunk/light rebuilds for a visual task lamp.
- Update `StationBlockEntity` render bounds if drawers extend farther than the current small bounds. Use a measured finite box, not infinite render distance.
- Pause hidden/distant cosmetics and stop sounds on teardown. Native physics is focused-client presentation; it is not offscreen server crafting.
- Profile normal and calm modes, one active station and a busy workshop, idle rigs and Ponder. Report p95 frame/tick changes and environment; test a laptop GPU with 6 GB VRAM when available.

Extend existing diagnostics with group counts, drawer state, command sequence/content revision, rejected action reason, scene version/missing nodes and physics body count. Keep internal IDs out of normal player flow, and rate-limit logs.

Use the existing benchmark/performance suites. Prior validation reports are a baseline, not proof of beta 11 performance.

## 13. Verification matrix

Extend the existing tests; do not replace them with tests that only assert the new helper returns its own constants. Separate domain tests, server GameTests, and real-client interaction checks.

### 13.0 Update existing assertions before adding coverage

`DiegesisGameTests.theStationsStayJewelryRun` currently asserts both schemas contain no items. Preserve SERVER authority, no menu, reach/exclusivity, compact drawer, clear-view restrictions and arrival threshold, but replace the no-items assertion with the deliberate split: wheel empty; bench only kept `supplies` and `staging` groups with correct sizes/predicates. Update exact `takes_items` assertions when supply nodes become native slots. Do not delete these tests because they reveal an intentional contract change.

Add beta 10 rejection to the version-range test, and parse both normal and calm post-loader scenes with the beta 11 codec.

High-priority new cases:

- Native library actions and Jewelry commands interleaved on supplies/staging: one owner, correct content revision, no duplicate action.
- Full staging, stale pose, invalid count/index, unknown work schema, closed/moving drawer and malformed coordinates.
- Format-2 bench with tray/supplies but no clamp; schema-1 carriers/cuts; old format-1 escrow.
- Real text/key events under the folio, including H, Escape, repeats, JEI and release; no work/lease mutation from typing.
- Actual mirrored/off-center pointer work, not only screenshots or direct behavior calls.
- Supply slots render once, staging bodies match hit geometry, and precise gems retain their rendering.
- Ponder at the same coordinates as a real station, replay/seek, help return, remote body poses and worn jewelry.

### 13.1 Automated domain and server cases

| Area | Required cases and pass condition |
|---|---|
| Item conservation | Every transfer/abort/complete/break path preserves input = stored + carried + output + intentionally consumed + dropped. Compare counts **and full relevant NBT**, not only item IDs. |
| Native gesture bypass | Pickup, split, quick move, double collect, middle spread, wheel, and spill all respect reserved/locked slots. |
| Input validation | Duplicate, delayed, wrong-operation, wrong-revision, invalid tool, out-of-range, NaN/Infinity, invalid recipe, excessive-rate, and unauthorized-user inputs cannot mutate work. |
| Completion idempotence | Repeating final input, output take, close, reload, and recovery cannot duplicate result, dust, XP, events, advancements, or tool wear. |
| Determinism | Equivalent canonical input produces equivalent cutting/setting outcomes regardless of camera, frame rate, or presentation profile. |
| Appraisal | Reopen, cancel, move, reload, search, preview, and disconnect never reroll an appraised stone. |
| Persistence | Save/reload prepared, active/paused, damaged, completed, and recovery states with exact material identity. |
| Operator changes | A second player cannot modify active work; lease releases on logout/death/range/dimension change without losing the piece. |
| Full storage | Full player inventory, full tray, hidden tray, occupied result/recovery slots, partially fitting stack, and simultaneous requests have bounded recoverable outcomes. |
| Work modes | Set, recut, retighten, remove, repair, refinish, and explicitly abort; test all four forms and multi-stone designs. |
| Progression events | Denied craft takes nothing; assistants clamp; GemCutEvent modifications affect final item/rewards; exception policy remains compatible. |
| Migration | Repeat migration, old escrow with display copy, malformed/partial tags, missing definitions, broken tools, format-2 empty-clamp fixtures and current portable work, and legacy Jewelcraft items. |
| Reload | Mid-work recipe/finish/gem removal, changed costs, resource reload, and repeated reconnect produce the documented result/recovery. |
| Optional mods | Without Curios/Smithing, fallback behavior remains complete; with them, slots and metalwork provenance remain correct. |

Extend existing `BenchGameTests`, `WheelGameTests`, `ApiGameTests`, `MigrationGameTests`, `IntegrationGameTests`, `ContributorGameTests`, `DiegesisGameTests`, `PerformanceGameTests`, `PresentationGameTests`, `WorkstationSimTest`, `WorkLeaseTest`, `WorkstationResourcesTest`, `LibraryResourcesTest` and the shared `FacetBot/SettingBot/SurfaceBot`. Add regression tests at the service boundary rather than relying entirely on screen automation. [Build/test wiring][J7].

### 13.2 Real-client and multiplayer cases

| Scenario | Observe |
|---|---|
| Four station facings, rotated/mirrored structures, left/right working camera | Correct target positions, no reversed text or simulation axes |
| 16:9, 16:10, 4:3, ultrawide, small window, multiple GUI scales/FOVs | Folio, counts, controls, and recovery remain on screen and clickable |
| Rapid open/close/reopen and close during drawer movement | No blink, duplicate block, stale hit box, stranded carried item, or continuing sound |
| Glass/chest/solid wall/particles beside the station | Counts stay visible; neighbouring faces remain present; drawers do not expose hidden contents |
| Large render distance / distant-renderer setup if installed | Painted folio and exact picking remain stable |
| Shader off/on and supported Embeddium/Oculus combinations | Correct light, translucency, outline fallback, and resource reload |
| White-concrete replacement/resource-pack model changes | Native highlights unaffected; 3D and thin-item poses revalidated |
| Physics crowding, stacking, support removal, rotate/drag, drawer transfer | No runaway bodies, lost poses, inaccessible stack, or unintended material ejection |
| Full inventory and wheel-up gesture | Safe no-op/fallback explanation, correct counts, no intermediate item loss |
| Mouse Tweaks flags individually off/on after restart | Native behavior follows supported flags; no duplicate gesture handling |
| InvMove installed | Work/navigation keys do not walk the player; neighbour hopping remains disabled |
| Folio typing A/D/E/Q/R/U/H/B/numbers, Unicode, Escape, remapped help and JEI keys | Explicit focus boundaries; crafting remains neutral while text/child screen owns input |
| JEI recipes/uses open and return during prepared/paused work | Same real contents, selected design and work; no held action resumes |
| Handbook/Ponder help, tap/hold H and folio “?” | Safe station exit, correct page/lesson, fresh-use resume; search H remains text |
| Ponder preview/seek, existing lessons, Create runtime | Isolated demo state/shared rigs; no real inventory/network mutation |
| Dedicated server and two clients, with/without Curios | Remote work/body/worn visuals, late join, help return after work changes, absence handling |
| Two clients plus bystander; delayed/bursty network | One operator, coherent observers, server correction, exactly-once results |
| Disconnect/death/teleport/break/unload during an active stroke | Safe release and the documented saved recovery |
| Calm loader settings / larger targets / slower server-approved assist | Same real crafting path, no accidental grade/cost advantage outside allowed assist |
| Complete novice playthrough without debug or external instructions | Understand mount → choose → work → finish → retrieve; no unexplained charges, hidden costs, or dead ends |

Use the existing smoke-test agent as a foundation, but drive the **real new interactions**. A bot that mutates block entities directly is not evidence that mouse picking, folio focus, or camera mirroring works.

### 13.3 Build and check commands

Follow `CLAUDE.md` and the local quiet wrapper when available; the checked-in Gradle wrapper also defines these tasks. Establish the current 1.2.2 baseline first, then run changed paths against beta 11:

~~~bash
./gradlew build
./gradlew runGameTestServer
./gradlew runGameTestServer -PnoSmithing -PnoCurios
./gradlew runClient -PsmokeTest
python3 tools/generate_resources.py --check
python3 tools/generate_textures.py --check
python3 tools/generate_sounds.py --check
python3 tools/generate_rigs.py --check
python3 tools/generate_book.py --check
python3 tools/generate_ponder.py --check
IJ_LIBRARIES_ONLY=1 ./gradlew runClient -PsmokeTest
~~~

Use clean dependency resolution and a dedicated-server startup with only server-required libraries to catch client class references. The 1.2.2 report explicitly did not run a normal dedicated server or two real clients; keep those checks outstanding until exercised. A GameTest dedicated distribution with client-library jars still on its classpath does not prove startup without those jars. Verify the packaged jar includes final scenes/models/lang files and the required dependency range. Do not ship test-only agent classes/resources. Run the explicit optional-mod variants even when the normal build passes; the existing default development classpath includes integrations that can hide an absence-related failure.

Run JSON Schema validation against the pinned beta 11 schema, then load scenes through Diegesis in game; schema validity alone does not prove named slots/nodes or rendering are correct.

If any check cannot run, state exactly what was not run and why. Never mark multiplayer, shader, migration, or playtest coverage complete based on compilation alone.

## 14. Implementation order and completion gates

| Phase | Work | Gate |
|---|---|---|
| 0 — Baseline | Confirm 1.2.2/newer local state; run existing checks; pin beta 11/range/schema | Both current stations and existing libraries still load before new features. |
| 1 — Input and lifecycle | Public key hooks, scoped overlay/character bridge, help/JEI precedence, repeat safety, effective camera mapping | Real typing, key release, help and child-screen tests pass; no unintended craft action. |
| 2 — Storage and commands | Narrow bound supply/staging groups, host poses, content revision and sequence guards, additive saves/drop path | Conservation, stale-command and format-1/2 migration tests pass. |
| 3 — Physical workshop | Four-stack drawer, transforms/visibility, native counts/gestures, calm option, lamp and side-camera framing | Stable geometry/picking and every applicable beta 11 row demonstrated. |
| 4 — Folio and preparation | Search, ID selection/cache, exact bounded staging and preview | Large catalogue remains responsive; server moves exactly selected needed materials. |
| 5 — Existing integrations | Update handbook, Ponder, rigs/poses, JEI/Jade, resources/locales | Current work loops, portable pieces and library navigation remain complete. |
| 6 — Release validation | Full existing suites plus new client/multiplayer/renderer/performance cases | No item loss/duplication, stuck work, unreachable storage or unsupported compatibility claim. |

Do not repeat foundational 1.2.0–1.2.2 work. Completing this update means the new features work through the existing engine, with no placeholder drawer, fake inventory or competing renderer.

### 14.1 Required coding-agent report

Return actual baseline/resulting mod/dependency/protocol versions; implemented B11 checklist with conditional dispositions; precise save changes; tests run and missing coverage; performance evidence; updated player/pack-author docs; and the jar/build command.

Keep previous validation history and append a clearly dated beta 11 section. Do not mark historical test counts as current results. Publishing/pushing a release is outside this implementation handoff.

## 15. Complete beta 11 changed-file review ledger

The release comparison contains **48 changed files** across three commits. This ledger accounts for the full changed surface; generated schema and publication text are separated from runtime changes.

| Files in the beta 10 → 11 comparison | Count | Review implication |
|---|---:|---|
| `CHANGELOG.md`, `CURSEFORGE.md`, `README.md` | 3 | Release summary, placement description, and community links. Publication-only edits do not require Jewelry gameplay. |
| `api/client/StationBehavior.java`, `StationView.java` | 2 | Keyboard/character hooks, surface visibility/transforms, room query, camera mirroring, changed hidden-block lifecycle wording. |
| `api/scene/OutputBinding.java`, `PlaceSurface.java`, `PlacingOptions.java`, new `PlacingPhysics.java`, `SlotBinding.java` | 5 | New scene fields, public record/API shapes, defaults, count modes, physics parameters. |
| `api/station/MenuBinding.java`, `StationType.java` | 2 | Named equivalent-menu acceptance and back-facing orientation option. |
| `impl/action/ActionHandler.java`, `StationAction.java` | 2 | Spill transport and server inventory/drop behavior. |
| `impl/client/ClientStation.java`, `ClientStations.java`, `HiddenBlocks.java` | 3 | New API plumbing and retention until restored block mesh is visible. |
| `impl/client/focus/FocusController.java`, `FocusInput.java`, `FocusScreen.java`, new `MouseTweaksMode.java`, `Placer.java`, `Projector.java` | 6 | Camera reflection, key routing, slot gestures, weighted dragging/carrying, upright carry fixes, projection stability. |
| new `impl/client/render/SceneLitConsumer.java`, `SceneRenderer.java`, `SmoothPass.java` | 3 | Scene-model illumination, light occlusion/flicker, late counts, safe projected painter bounds. |
| `impl/client/scene/PlacedItem.java`, new `PlacedPhysics.java`, `PlacedShape.java`, `Placing.java`, `SceneInstance.java` | 5 | Upright model pose, hover behavior, collision shapes, dynamics, free spots, surface transforms/picking/support/visibility. |
| `impl/menu/MenuClicks.java` | 1 | Menu-path handling for spill/quick move; do not assume server-station spill and MENU recovery are identical. |
| new `mixin/client/BlockFaceMixin.java` and `SodiumOcclusionMixin.java` | 2 | Neighbour-face occlusion corrections for hidden blocks. |
| new `assets/diegesis/textures/block/white.png` and `diegesis.mixins.json` | 2 | Dedicated highlight sprite and registered mixins. |
| `docs/client.md`, `docs/scenes.md`, `docs/stations.md` | 3 | Author-facing contracts and new field documentation. |
| `docs/schema/node.schema.json`, `docs/schema/scene.schema.json`, `tools/docs/schema.py` | 3 | Schema definitions/generator coverage for added fields and partial templates. |
| Fabric `DiegesisFabricClient.java`, `HideableFabricModel.java`, `mixin/SodiumWorldSliceMixin.java` | 3 | Platform render/count/hidden-block plumbing; upstream coverage, not a Jewelry Fabric port. |
| Forge `ForgeClient.java`, `HideableModel.java` | 2 | Forge count stage and real-model rebuild acknowledgement. |
| `gradle.properties` | 1 | Release version bump. |
| **Total** | **48** | All changed files accounted for. |

Most Java paths in this ledger are relative to `common/src/main/java/com/pockyl/diegesis/`. Platform files are under their respective platform source roots; resources are under `common/src/main/resources/`.

The three commits are:

- `99008c9b5435a450c8bab06b88235e3d3a1642de` — main feature/fix implementation.
- `4e347d459fa4c57e0129b23a2cf454431e5a2208` — README/community-link update.
- `ae9e324a836a2172baa2be56b12d43dcf4ab94c4` — beta 11 release version.

## 16. Primary sources

All implementation facts above come from the project authors’ repositories and official distribution pages. Proposed Jewelry features, initial defaults, timings, capacities, and acceptance requirements are design recommendations in this handoff.

### Diegesis

- [D1 — beta 11 release][D1]
- [D2 — complete beta 10 → 11 comparison][D2]
- [D3 — Forge 1.20.1 beta 11 distribution and Maven coordinate][D3]
- [D4 — tagged scene authoring reference][D4]
- [D5 — StationView API][D5]
- [D6 — StationBehavior API][D6]
- [D7 — PlacingOptions and defaults][D7]
- [D8 — PlaceSurface geometry and edge policy][D8]
- [D9 — all PlacingPhysics parameters and validation ranges][D9]
- [D10 — MenuBinding and named menu acceptance][D10]
- [D11 — SceneRenderer and lighting/count lifecycle][D11]
- [D12 — focused-client placement physics implementation][D12]
- [D13 — server action and spill processing][D13]
- [D14 — server/client/menu authority, persistence, binding, and action limits][D14]
- [D15 — beta 10 release, inherited changes][D15]
- [D16 — client behavior, painters, effects, input, and state guidance][D16]
- [D17 — beta 11 JSON Schema][D17]
- [D18 — public DiegesisClient APIs and scene-render query][D18]
- [D19 — input routing, wheel behavior, and native gestures][D19]
- [D20 — state serialization and bound inventory semantics][D20]
- [D21 — actual overlay/key/character routing][D21]
- [D22 — ScreenOverlay public callbacks][D22]
- [D23 — SlotStorage binding contract][D23]

### Immersive Jewelry — pinned 1.2.2 revision

- [J1 — Build/dependency properties][J1]
- [J2 — Complete current source tree][J2]
- [J3 — Lease validation, pacing and interruptions][J3]
- [J4 — Owning station block entities][J4]
- [J5 — Bench custody, commitment, costs and completion][J5]
- [J6 — Architecture and deliberate integration choices][J6]
- [J7 — Build/test wiring and dependency sides][J7]
- [J8 — Protocol 4 registrations][J8]
- [J9 — Client accessibility/presentation settings][J9]
- [J10 — Definition reload and lease release][J10]
- [J11 — Block facing contract][J11]
- [J12 — 1.2.0–1.2.2 changelog][J12]
- [J13 — Current empty schemas and station registration][J13]
- [J14 — Forge key bridge and JEI precedence][J14]
- [J15 — Folio selection, filtering and staging][J15]
- [J16 — Scene loader and authored camera helper][J16]
- [J17 — Current slot/custom-action denial][J17]
- [J18 — Persistent work orders][J18]
- [J19 — Patchouli help navigation and live cards][J19]
- [J20 — Historical validation and explicitly unrun checks][J20]
- [J21 — Bench pointer mapping and precision tools][J21]
- [J22 — Packet entry validation][J22]
- [J23 — Wheel pointer mapping and work controls][J23]
- [J24 — GeckoLib state-driven rigs][J24]
- [J25 — Adapter contract tests][J25]
- [J26 — Repository update commit][J26]
- [J27 — Independent handbook H-key handler][J27]
- [J28 — Scene/BER/Ponder handoff][J28]
- [J29 — Bench format 2, migration and packing][J29]
- [J30 — Wheel mounting, wear and completion][J30]

[D1]: https://github.com/Pocky-l/diegesis/releases/tag/v1.0.0-beta.11
[D2]: https://github.com/Pocky-l/diegesis/compare/v1.0.0-beta.10...v1.0.0-beta.11
[D3]: https://www.curseforge.com/minecraft/mc-mods/diegesis/files/9643618
[D4]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/docs/scenes.md
[D5]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/common/src/main/java/com/pockyl/diegesis/api/client/StationView.java
[D6]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/common/src/main/java/com/pockyl/diegesis/api/client/StationBehavior.java
[D7]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/common/src/main/java/com/pockyl/diegesis/api/scene/PlacingOptions.java
[D8]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/common/src/main/java/com/pockyl/diegesis/api/scene/PlaceSurface.java
[D9]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/common/src/main/java/com/pockyl/diegesis/api/scene/PlacingPhysics.java
[D10]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/common/src/main/java/com/pockyl/diegesis/api/station/MenuBinding.java
[D11]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/common/src/main/java/com/pockyl/diegesis/impl/client/render/SceneRenderer.java
[D12]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/common/src/main/java/com/pockyl/diegesis/impl/client/scene/PlacedPhysics.java
[D13]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/common/src/main/java/com/pockyl/diegesis/impl/action/ActionHandler.java
[D14]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/docs/stations.md
[D15]: https://github.com/Pocky-l/diegesis/releases/tag/v1.0.0-beta.10
[D16]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/docs/client.md
[D17]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/docs/schema/scene.schema.json
[D18]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/common/src/main/java/com/pockyl/diegesis/api/client/DiegesisClient.java
[D19]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/common/src/main/java/com/pockyl/diegesis/impl/client/focus/FocusInput.java
[D20]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/common/src/main/java/com/pockyl/diegesis/api/state/StationState.java
[J1]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/gradle.properties
[J2]: https://github.com/otectus/ImmersiveJewelry/tree/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7
[J3]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/workstation/WorkSessionManager.java
[J4]: https://github.com/otectus/ImmersiveJewelry/tree/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/blockentity
[J5]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/workstation/BenchOperations.java
[J6]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/docs/ARCHITECTURE.md
[J7]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/build.gradle
[J8]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/network/ModNetwork.java
[J9]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/config/ClientConfig.java
[J10]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/data/DefinitionEvents.java
[J11]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/block/StationBlock.java
[J12]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/CHANGELOG.md
[J13]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/workstation/station/JewelryStations.java
[J14]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/client/workstation/WorkstationClient.java
[J15]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/client/workstation/FolioOverlay.java
[J16]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/client/workstation/SceneLoader.java
[J17]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/workstation/station/WorkstationLogic.java
[J18]: https://github.com/otectus/ImmersiveJewelry/tree/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/workstation/order
[J19]: https://github.com/otectus/ImmersiveJewelry/tree/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/client/guide
[J20]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/docs/WORKSTATION_VALIDATION.md
[J21]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/client/workstation/BenchBehavior.java
[J22]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/workstation/WorkInputService.java
[J23]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/client/workstation/WheelBehavior.java
[J24]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/client/presentation/geo/JewelryRigs.java
[J25]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/gameTest/java/com/otectus/immersivejewelry/test/DiegesisGameTests.java
[J26]: https://github.com/otectus/ImmersiveJewelry/commit/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7
[J27]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/client/guide/Handbook.java
[J28]: https://github.com/otectus/ImmersiveJewelry/tree/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/client/render
[J29]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/blockentity/JewelersBenchBlockEntity.java
[J30]: https://github.com/otectus/ImmersiveJewelry/blob/7ca0b4f9a2373de9a7b6b2a6501927a3dc1dc7e7/src/main/java/com/otectus/immersivejewelry/workstation/WheelOperations.java
[D21]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/common/src/main/java/com/pockyl/diegesis/impl/client/focus/FocusScreen.java
[D22]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/common/src/main/java/com/pockyl/diegesis/api/client/ScreenOverlay.java
[D23]: https://github.com/Pocky-l/diegesis/blob/ae9e324a836a2172baa2be56b12d43dcf4ab94c4/common/src/main/java/com/pockyl/diegesis/api/state/SlotStorage.java
