# Changelog

## 1.3.0 - Diegesis beta 11: a staging drawer, supply jars, a searchable folio and a working side

The bench gains a staging drawer for loose stacks and supply jars that work like any container slot, the folio can be
searched and prepares exactly the stones a design still needs, either side of a station can be the working side, and a
steady work lamp lights the bench. Every cut, setting, finish, rework and portable piece works as before, and worlds carry
over unchanged.

### Upgrading from 1.2.2

- **Diegesis 1.0.0-beta.11 or newer** (tested: CurseForge file 9643618) is required on clients and servers; beta 10 is refused
  at start-up (the scenes use beta 11 fields). Newer Diegesis releases are accepted (see *Diegesis updates* below).
- **Ponder, Flywheel and playerAnimator are now optional** (they were required on clients in 1.2.2). Without Ponder (and the
  Flywheel it needs) the handbook's Watch buttons are greyed out with a tooltip saying why, and the help key opens the step's
  handbook page instead of its lesson. Without playerAnimator nobody's body is posed at the stations, and a demonstration's
  jeweler stands still. Nothing else changes: work, items, the rigs, the handbook and worn jewelry need neither. When they are
  installed, the same tested versions are required as before. Patchouli and GeckoLib are still required.
- **Network protocol 5.** Client and server must both run 1.3.0: discrete commands carry a sequence number and four bench
  commands were added.
- **Saves:** benches keep save format 2 and gain three optional entries: `Staging` (the drawer's four stacks), `DrawerOpen`
  and `Station` (where staged stacks stand). A 1.2.x bench loads with an empty, shut drawer; nothing is copied into it.
  Wheels, stones with work in them, Unfinished pieces and 1.1 or Jewelcraft data load exactly as before.
- **Client config** (`[workstation]`): `workingSide` (RIGHT, LEFT), `looseItemMotion` (AUTO, NATURAL, CALM), `workLamp`.
- **Key:** *Staging drawer (list)* (N), in the workstation category.

### The bench's storage

- **Supply jars** are real container slots now: click, split, shift-click, double-click, the mouse wheel (one in, one back)
  and middle-drag work on them as anywhere, and their counts stand facing you. A finish still takes its material at its
  first stroke; until then you can take it back.
- **The staging drawer** in front of the vise keeps four loose stacks of the bench's own things (stones, frames, pieces,
  pliers, supplies). Pull it open by its brass handle or the drawer list; stacks stay where your hand leaves them and settle
  gently (no throwing, nothing pushed out, walls on every side). Shut, it hides them and takes nothing. Nothing is ever used
  from the drawer by itself.
- **The drawer's list** (N, or right-click the handle) shows its four stacks by number and moves each to the bench or back to
  your inventory, open or shut, by keys or clicks. Sneak-using the bench also takes a staged stack back.
- Shift-clicking a stack the bench has no place for puts it into the open drawer when there is room, and says so when
  there is not.

### The folio

- A search field (click it, or press / or Ctrl+F) finds designs by name, form, gem, cut, finish or id, in your language.
  While it has the keyboard every key is text: no station, help or drop key acts, and the first Escape only leaves the field.
- Filters: known sketches, designs for the clamped frame, designs whose stones you have; favourites as before. The chosen
  design is kept by id, so a filter or a reload never quietly chooses another one.
- **Prepare stones** replaces staging: it shows the stones that fit (gem, cut, quality, damage, where they are) and the
  finishing material with when it is used; keep the best cuts or choose the gem and the stones you prefer. The bench moves
  exactly the stones still needed into the tray, one request, and says what moved and what is missing.

### Views and comfort

- Both stations' shots now look from the front right; the `workingSide` option LEFT mirrors them. The pointer follows the
  real camera from either side.
- `looseItemMotion` CALM (and reduced motion, by default) lets staged stacks stand exactly where they are put.
- A steady brass work lamp lights the bench close-up (a scene light: it does not light the world). `workLamp` turns it off.
- Changed display options apply the next time you open a station (you are told); the working side switches at once.

### Diegesis updates

- A Diegesis update no longer stops the game from starting or takes Immersive Jewelry down with it. At start-up the mod checks
  every part of Diegesis it uses; whatever a newer release no longer offers is switched off and falls back, and the log
  (and `/immersivejewelry diegesis`) says exactly what. Fallbacks, from mild to severe: station keys through the game's own
  key events; the authored camera side only; the staging drawer as a list; the jars and drawer moved by the mod's own clicks;
  station scenes simplified step by step until the library can read them; and at worst the hands-on stations stay closed
  while the blocks keep their walk-up use (putting things on, taking them off, packing work), with a message saying why.
- Items and work never depend on Diegesis: benches, wheels, stones, staged stacks and unfinished pieces load and save the same
  with any Diegesis, and a change the check could not foresee switches the stations off for the session instead of crashing.
- Checked by running the mod (built for beta 11) against Diegesis beta 10, which lacks everything beta 11 added.

### Safety and fixes

- Discrete commands are numbered per lease and paced (20 a second); repeats, stale clicks (a position that changed in the
  meantime), malformed arguments and commands about another work change nothing, and the bench's state is sent again.
- Station keys arrive through Diegesis' new key hook; a held key's repeats never run a one-shot action (a tool change, a
  confirm, a pack) twice.
- Jade shows the supply count and how full the drawer is (never what a shut drawer holds); `/immersivejewelry sessions` shows
  the bench's storage and the last command number, and its lease line is translated now.
- New handbook entry *Preparing the Bench* and a new demonstration *The Drawer and the Jars*, in all five languages.

## 1.2.2 - The Jeweler's Handbook, demonstrations, rigs and body language

The Jeweler's Handbook is now a full Patchouli book with live pages, every step of the craft has a Ponder demonstration
played on the stations' own simulations, the wheel, the vise and the bench tools are articulated GeckoLib rigs, a working
jeweler's body follows the work through playerAnimator, and worn jewelry shows on players. Worlds and work in progress
carry over unchanged.

### Upgrading from 1.2.1

- **New required libraries** (exact ranges in `gradle.properties`): Patchouli `1.20.1-85-FORGE` and GeckoLib `4.8.4` on
  clients and servers; Ponder `1.0.91`, Flywheel `1.0.5` and playerAnimator `1.0.2-rc1+1.20` on clients (a dedicated
  server runs without them). These are the same releases Immersive Smithing 1.3 uses; Create 6 bundles a compatible
  Ponder and Flywheel.
- **Network protocol 4.** Client and server must both run 1.2.2 (one new server-to-client packet, below).
- **Saves:** nothing changes. Wheels, benches, stones with work in them and Unfinished pieces load exactly as in 1.2.1;
  the block entities keep their save format (they only gain a GeckoLib animation cache that is never saved).
- **Client config:** a new `[presentation]` section: `bodyAnimation` (FULL, REDUCED, OFF), `reducedTutorialMotion`,
  `showWornJewelry` and one switch per form. Forge adds it to an existing file.
- **Key:** *Jeweler's Handbook / help* (H), in the *Immersive Jewelry* category.

### The Jeweler's Handbook (Patchouli)

- The same item (`immersive_jewelry:jewelers_handbook`), recipe and trade; it now opens a Patchouli book with search,
  bookmarks and history. The old one-screen handbook is gone; all fourteen of its chapters, with their translations,
  are in the new book, alongside eleven categories and 36 entries: first piece, stations and tools, controls and comfort,
  leaving and resuming, appraisal, every gem, the four cuts and recutting, forms, metals and blank routes, the folio,
  sketches and a design atlas, prongs, bezels, several stones and defects, the three finishes and refinishing, slots,
  effects and inactive pieces, care and rework, working together, other mods, common refusals and pack making.
- **Live cards** read this server's data: gem cards (trait, hardness, brittleness, cuts with favourites, source items,
  automatically detected gems), cut comparisons, design cards (setting, stones, allowed cuts, gems and finishes, and
  whether you know the design), finish cards (material or none, tuning), blank routes (Immersive Smithing and crafting
  fallback as the server's recipes actually are), metals, the tool tiers from the real items, station keys as bound now,
  the server's rules, an inspection of the piece in your hand, an estimated gem-on-form comparison, and what the bench can
  do for the piece in your hand. Reading never appraises, crafts, equips or teaches a design.
- **Help everywhere:** press H in the world for the book, over an item in an inventory for its entry, over a JEI recipe of
  this mod for its entry, or at a station (or the folio's "?") for the step under way; hold H at a station for the
  demonstration instead. Help first lets go of everything, leaves the station (nothing is lost, the work stays) and tells
  you when you come back whether the station and its work are as you left them. Nothing resumes on its own.

### Demonstrations (Ponder)

- 33 lessons under the stations, tools, stones, sketch, compound, blanks and forms, in six index topics (First Piece,
  Lapidary, Setting, Finishing, Care, Equipment): the first piece end to end, appraisal and sources, laps and tools, each
  of the four cuts on a gem that takes it, mistakes and recutting, a stone that travels, blanks, the folio and sketches,
  seating and prongs, bezels, three stones, a ten-design atlas, the three finishes, quality, retightening and repair,
  taking stones out, refinishing and resuming packed work, wearing and effects, and working together.
- Lessons run the stations' own simulations with scripted hands in Ponder's level only (a demonstrating jeweler posed by
  the same body clips), so pause, replay and seeking rebuild exactly the same stone and setting. They never touch the
  world, an inventory or the network. Long work is shown faster than life, and says so.
- Every handbook entry with a demonstration has a Watch button; closing the lesson returns to that page.

### Rigs and bodies (GeckoLib, playerAnimator)

- The lap, its hub and the release lever, the vise (jaws closing on a new piece, the screw turning, posts for circlets and
  bracelets) and the bench tools (pliers' jaws, tweezers' arms, the pusher, burnisher, brush and cloth) are GeckoLib rigs,
  drawn in the stations' own frames and posed from synced state, so every watcher, a late one included, sees the same
  thing. The Fine Pliers keep their own look. A missing rig falls back to the previous plain parts.
- A working jeweler's arms, torso and head follow the work: grinding, lifting, cooling, mounting, changing a lap, seating,
  squeezing prongs, pushing a bezel, polishing, burnishing, dabbing patina, prying stones, truing a frame, placing,
  collecting and inspecting a piece. Poses come from the station's synced state only; they stop the moment the turn ends
  and give way to riding, swimming, sleeping, gliding or using an item. First person is unchanged (the scene's own hands).
- Worn jewelry is drawn on players: rings on the fingers, amulets on the chest, bracelets at the wrist, circlets on the head
  (hidden under a helmet or chestplate). Curios slots use Curios' own renderer hook and its visibility toggles; the
  built-in slots send a sanitized look (item, metal, stones, finish; never the maker, defects, history or other mods'
  data) to nearby players (`WornAppearancePacket`, protocol 4).

### For resource packs

- Handbook text, Ponder captions and lesson names are lang keys (`book.immersive_jewelry.*`, `handbook.immersive_jewelry.*`,
  `immersive_jewelry.ponder.*`, `lesson.immersive_jewelry.*`). The book's structure is under
  `assets/immersive_jewelry/patchouli_books/jewelers_handbook/en_us/`.
- Rigs: `geo/{lapidary_wheel,jewelers_bench,bench_tools}.geo.json`, `animations/...`, `textures/rig/...`; body clips:
  `player_animation/<clip>.json` and `<clip>_reduced.json`. Bone and clip names are listed in `docs/ASSET_MANIFEST.md`.

### For developers

- The old `HandbookScreen` is removed; open the book through Patchouli (`immersive_jewelry:jewelers_handbook`).
- The station block entities implement GeckoLib's `GeoBlockEntity` (no controllers).

## 1.2.1 - Diegesis 1.0.0-beta.10

The stations now run on Diegesis 1.0.0-beta.10 and use what it adds: a stack you carry shows where it would go, the
inventory drawer lists what the station can use, and JEI's lookup keys work at the stations without leaving them.
Worlds, work in progress, the server's rules and the network protocol (3) are unchanged.

### Upgrading from 1.2.0

- **Requires Diegesis 1.0.0-beta.10** (Forge; the accepted range is in `gradle.properties` as `diegesis_version_range`).
  Update it on clients and servers together with Immersive Jewelry. 1.0.0-beta.9 is refused: the station scenes use
  fields that its scene parser does not know.
- **Client config:** new `drawerShowsEverything` in `[workstation]` (below); Forge adds it to an existing file.

### At the stations

- **Placement hints.** With a stack taken from the inventory drawer, the place it would go lights up: at the bench a
  tray cell, a jar, the dock or the clamp as you point at it; at the wheel the empty quill or the lap as you point at
  the quill, its handle or the lap.
- **The inventory drawer** (Tab, or point at the bottom edge of the screen) lists only what the station can use: laps,
  stones and raw gems at the wheel; blanks, finished and packed pieces, pliers, cut stones and finishing and repair
  materials at the bench. `drawerShowsEverything` lists every stack you carry.
- **JEI.** Its show-recipe and show-uses keys (R and U by default) look up the stack under the cursor, in the drawer or
  on the station (a stone or lap on the wheel; a tray stone, a supply, the pliers or the piece in the clamp on the
  bench). JEI's page opens over the station, and closing it returns to the work with nothing lost; while it is open, no
  key reaches the station and anything held is let go. A station key keeps its meaning: R turns a stone held in the
  tweezers and plans the cut of a stone on the wheel, and looks up only where it has nothing to do.

### For resource packs

- A pack that replaces a station scene (`assets/immersive_jewelry/diegesis/stations/<station>.json`) still loads and
  works, but shows no placement hints until its placement nodes have `"takes_items": true`: `handle`, `stone` and `lap`
  at the wheel; `piece`, `dock`, `tray_0` to `tray_7` and `supply_0` to `supply_2` at the bench. The inventory drawer's
  new `carry_size` (the size of a stack carried over the scene, in scene pixels) is optional.

## 1.2.0 - Hands-on workstations

Cutting and setting are no longer screens. You step up to the Lapidary Wheel or the Jeweler's Bench, the camera settles
over the work, and you grind, seat, close and polish with the station's own handle, tools and hands. Work is kept on the
stone and on the bench, so it can be left, carried off and resumed. The stations are built on Diegesis.

### Upgrading from 1.1.x

- **Requires Diegesis** (Forge, `1.0.0-beta.9`; the accepted range is in `gradle.properties` as
  `diegesis_version_range`). Install it on clients and servers.
- **Network protocol 3.** Client and server must both run 1.2.0.
- **Worlds carry over.** Wheels keep their lap and stone. A bench's piece moves into the new clamp; stones and finish
  materials held for a 1.1 setting session go back onto the bench (stones into the tray, materials into the
  supplies) the first time the bench loads, once. Anything that does not fit drops beside the bench. A 1.1 session that was
  open when the world closed is not resumed; nothing in it was consumed.
- **Server config:** `cuttingSecondsPerFacet`, `settingSecondsPerProng` and `readyDelayMs` are gone (there are no time
  limits or countdowns any more); Forge drops them from the file. Every other key keeps its name and value; the
  comments of the `minigames` section describe the stations now.
- **Client config:** `largeMinigameMarkers` is gone (see `targetScale`). New `[workstation]` section, below.
- **Resource packs:** the Lapidary Wheel model is new (`models/block/lapidary_wheel.json`, its moving parts are drawn
  by the scene; `lapidary_wheel_parts.json` is the inventory icon's quill and lap). `lapidary_wheel_disc.json` is gone.
  The bench model lost the vise, tool tray, jars, mandrel and loupe, which are now scene objects. Scene cameras,
  glances, captions and pick zones are in `assets/immersive_jewelry/diegesis/stations/<station>.json`; a pack can move
  them, but cannot widen any of the server's tolerances.

### The Lapidary Wheel

- **Walk up and use it** holding a stone to mount it (it is appraised as before), holding a lap to swap the lap;
  sneak-use with an empty hand takes the stone. Use it with an empty hand to step up to the wheel.
- **Plan at the specimen rack:** a small cut example per cut the stone allows; click one (or press the tool key) to plan
  that cut. Planning is free until material has been removed; after that, choosing again asks for confirmation and
  recuts the stone as it now is.
- **Grind with the handle:** drag it down to bring the stone onto the turning lap and sideways to sweep it across. How
  far you press is the force; a comfortable force cuts, too much scratches, too fast a sweep rocks the stone and rounds
  its edges, and a stone kept in one place grinds a flat spot. The stone warms while it works: lift it, or dip it in the
  cooling cup. Heat left too long chips it. A thin brass line marks each face's depth window; the sound changes as the
  depth approaches.
- **Turn the collar** (only with the stone lifted) to the next face; the fine trim corrects a stone glued a little
  turned on its dop. The recommended order works opposite faces in turn.
- **The loupe** shows the stone up close with every face's state (depth, evenness, rocking, scratches, chips, the
  dop's turn).
- **The release lever** finishes the cut when every face is in its window, or earlier when you confirm a rougher result.
- **Four cuts, four motions:** round and brilliant are radial facets, the emerald cut grinds long sides before
  corners, the cabochon is domed by keeping the stone moving.
- **The work stays on the stone** (`Work` in its item NBT): step away, take it out, carry it to another wheel and go on.

### The Jeweler's Bench

- **Walk up and use it** holding a blank or a finished piece (it goes in the clamp), cut stones (the tray), pliers (the
  dock) or a finish material (the supplies). Sneak-use with an empty hand takes things back. Use it with an empty hand
  to sit down at it.
- **The design folio** lists the designs for the clamp's form (star favourites and show only those), the finishes
  each allows and the stones it needs, and can bring matching stones from your inventory to the tray. Its rework page
  retightens, takes apart, repairs or refinishes a finished piece.
- **Tweezers** pick a stone from the tray and let it go into a cup; where and how straight it lands is its seat.
  Stones resting in their cups can still be lifted out until the first prong closes.
- **Pliers** grip one prong and squeeze it until it meets the stone; let go to check. A prong short of the stone is
  loose, one pushed past it leans in (and can chip a brittle stone). Opposite prongs in turn keep the stone level.
  **Bezels** are closed with the bench's pusher, pressed in and swept round the rim.
- **Confirm** when every stone is held (or take up the finishing tool) to move on to the finish. The finish is done by
  hand: the cloth with compound for polished, the burnisher for burnished, the brush and then the cloth for antique.
- **Unfinished work** stays in the bench. Packing it (sneak-use, or the pouch) gives an *Unfinished* piece
  (`immersive_jewelry:unfinished_jewelry`) holding the frame, the stones and the work; using a bench with it unpacks
  it there. It cannot be worn and is never recycled by Immersive Smithing.

### Working at a station

- One operator at a time. Leaving (Escape), walking off, taking damage, logging out, dying, changing dimension or the
  station breaking ends the work at that moment: tools lift, nothing is finished, refunded or lost. Others nearby see
  the work and hear it.
- **Keys** (category *Immersive Jewelry: Workstations*, rebindable): raise/lower/left/right (arrows), use the tool
  (Space), next/previous (X/Z), fine trim (C/V), tool (T), loupe (F), cool (G), confirm (Enter), folio (B), turn (R).
  Every step of the work has a keyboard path; putting things on a station and taking them off also works by using it
  (or sneak-using it) from where you stand.
- **New client options** (`[workstation]`): `noZoom`, `instantTransitions`, `workstationHands`, `toggleToolUse`,
  `stickyDrag`, `pointerSensitivity`, `targetScale`, `numericGuide`, `workHints`, `visualCues`, `toolSoundVolume`,
  `gemTranslucency`, `assistedSweep`. `largerTargets` and `slowerMinigames` now ask the server for wider windows and a
  gentler rate (where the server allows assists); `minigameScreenShake` jolts the wheel when a stone chips.
- **Sounds:** the wheel's hum and grinding follow contact, force, the stone's hardness and how close the face is to its
  depth; cloth, burnisher and brush loop while they touch; the pliers creak as they close; a quiet click at secure
  contact; a soft dip at the cooling cup. Each has a subtitle.

### Data packs

- **Work profiles** (optional): `data/<ns>/immersive_jewelry/work_profiles/<path>.json` tune one interaction (facet,
  dome, prong, bezel, polish, burnish, patina, repair): its pace, tolerance scale, force window and sweep tolerance,
  all range-checked. A design, cut or finish can name one with `"work_profile"`; otherwise
  `immersive_jewelry:defaults/<interaction>` applies (shipped; they repeat the built-in numbers). A profile can make the
  work slower or more forgiving; it can never change what the work produces.

### API

- Signatures are unchanged. `IJewelryCraftGate` is now asked when the first closure would commit a new piece (after the
  design, stones and finish are checked, before any stone or material is used). `IJewelryAssistSource` is applied when
  a cut is planned, a setting is committed or a rework begins. `GemCutEvent` is posted just before the cut stone replaces the work
  on the wheel; experience and advancements follow its final values. `JewelryCraftedEvent` fires just before the finished
  piece replaces the work in the clamp.

### Integrations and commands

- **Jade** shows a station's cut and progress, the bench's stage, what the tray and dock hold, missing stones and who
  is working there.
- **JEI** texts describe the hands-on work.
- `/immersivejewelry sessions` lists the operator leases (player, dimension, station, input step) instead of sessions.

### Balance changes

- No time limits, countdowns or deadlines anywhere: a cut or a setting takes as long as you take.
- Cut quality comes from what happened to the stone: each face's depth against its window, flatness, rocking,
  scratches, chips and the dop's turn. Setting quality comes from each point's closure, the stone's seat and how level
  it sits.
- A bezel is closed with the bench's built-in pusher (no pliers needed, full precision); pliers are needed for prongs.
- Pliers lose durability once per setting, at the first closure; a lap wears once per cut, at the first contact.
- The finish material (or the repair material) is used once, when the finishing tool first works the piece.
- Moving from setting to finishing waits for you (confirm, or take up the finishing tool); it never happens on its own.

### Removed

- The cut-select, cutting, bench and setting screens, their packets, and the language keys only they used.

## 1.1.2 - Translations

- Added Spanish, Brazilian Portuguese, Russian, and Simplified Chinese translations.

## 1.1.1 - Vanilla screens

Every screen now uses Minecraft's own look, as Immersive Smithing's do. Gameplay, items, data and the network protocol
are unchanged: a 1.1.1 client and a 1.1.0 server (or the reverse) work together.

### Screens

- **One look.** The Lapidary Wheel, cutting, Jeweler's Bench, setting and jewelry-slot screens draw the vanilla
  container panel, slots, buttons and creative-inventory scroll bar from Minecraft's textures, with vanilla's dark grey
  labels. A resource pack that restyles vanilla containers restyles these screens too.
- **Minigames.** The stone sits in a dark display; depth, pressure, heat and time are vanilla bars beside it, and
  feedback appears in a dark readout so its colours keep their contrast. Brass still marks the facet, prong and band
  to work on. The verdict is a small vanilla panel with the piece in a slot and its grades below.
- **Lists.** Cuts, designs, stones and finishes are vanilla button rows. The chosen row is highlighted and marked
  "»"; finishes a design does not allow look disabled. Scroll bars can be dragged as well as scrolled. Finish and
  repair costs show their stack counts.
- **Readouts.** The bench's preview of a piece's effects and the rework description sit in dark readouts, so their
  tooltip colours stay legible. Tooltips are drawn at normal size even when a screen is scaled down to fit.
- **The Jeweler's Handbook is a vanilla book:** black ink on the book page, "Page x of y", the book's page arrows and
  a Done button. Each chapter opens with its icon and title.
- **JEI** categories use JEI's own slots and recipe arrows on a plain background.
- **Empty jewelry slots** show an outline of the piece in vanilla's armour-slot grey.

### For resource packs

- `textures/gui/stone.png` and `textures/gui/slot.png` are gone; packs that replaced them no longer apply. The
  empty-slot icons under `textures/gui/slot/` remain and were redrawn.

## 1.1.0 - Progression hooks, station messages that stay readable

### Upgrading from 1.0.0

- **Network protocol 2.** Client and server must both run 1.1.0; a 1.0.0 client cannot join a 1.1.0 server, and a
  1.1.0 client cannot join a 1.0.0 server.
- Worlds, items and datapacks carry over unchanged. New config keys take their defaults.
- `GemCutEvent` is now posted before the stone goes back into the wheel and before experience, advancements and the
  result screen are handed out; in 1.0.0 it came last. Listeners that only read the event see the same values.

### API for progression mods

- `ImmersiveJewelryAPI.registerCraftGate(IJewelryCraftGate)` (IMC `craft_gate`): refuses a jeweler a new piece with a
  reason. Asked when setting starts on a blank, after the design, stones and finish have been checked and before any
  stone or finish material is taken. The bench shows the first reason as a failure notice. A gate that throws is
  skipped.
- `registerWearGate(IJewelryWearGate)` (IMC `wear_gate`): decides whether a worn piece is active for a player. A
  refused piece stays where it is worn but does nothing, like a broken one, and does not count toward
  `max<Form>Slots`; the built-in jewelry slots refuse to take it. Asked on both sides whenever the
  wearer's jewelry is re-examined, not every tick. A gate that throws allows the piece.
- `registerAssistSource(IJewelryAssistSource)` (IMC `assist_source`): per-player relief for cutting and setting
  (`Kind.CUTTING`, `Kind.SETTING`). Answers multiply the server's difficulty multiplier, and the product is clamped to
  `minAssistDifficulty..1.0` (new server config under `minigames`, default 0.5, 0.25-1.0), so assistance can only ease
  a session. The eased parameters travel in the existing start packets, so the client simulates the same session. A
  source that throws counts as 1.0.
- Matching `unregisterCraftGate`, `unregisterWearGate` and `unregisterAssistSource`, plus `craftGates()`,
  `wearGates()`, `assistSources()`, `craftDenial(...)`, `mayWear(...)` and `assistDifficulty(...)` for mods that need
  the same answers.
- `GemCutEvent` is settable. `setAfter` and `setCutQuality` change a cut stone's quality (the same gem, cut, size and
  damage only; a shattered stone stays shattered), and `addExtraDust` leaves more Gem Grit from a shattered stone (up
  to 62 extra). Experience, the Without a Flaw advancement and the result screen follow the final quality.

### Station messages

- Station messages (what a station needs, errors, a worn-out lap, a cancelled session's reason) stay readable above
  the hotbar instead of on the action bar, where the next message replaced them. Up to three show at once, newest
  lowest; a repeated message refreshes instead of stacking. Failures are tinted, with a colour-blind-safe tint under
  `colorblindSafeColors`. Notices are narrated when the narrator reads system messages, hide with the HUD and clear on
  logout.
- New client config: `stationNotices` (default on; off uses the vanilla action bar) and `noticeSeconds` (default 8,
  2-60).

### Minigames

- A click or Space tap that begins and ends between two simulation steps is no longer lost: it presses for one step.
- Cutting and setting event labels ("Facet done", "Secured", "Chipped!") stay readable longer: new client config
  `feedbackLabelMs` (default 2500 ms, 500-10000; 1.0.0 showed them for 900 ms). A newer event replaces the label at
  once.

### Workstations and Russian localization

- Fixed overlapping workstation surfaces and the wheel's backward snap when its rotation wraps.
- Reduced the rendering work for displayed jewelry and stones while keeping animations and crafting feedback.
- Kept workstation details visible at the edge of the screen and respected resource-pack render layers.
- Added complete Russian text, including naturally composed jewelry names, handbook pages, integrations and command feedback.
- Long handbook pages continue onto another sheet; JEI descriptions grow to fit their translated text.

### Testing

- GameTests for the new API: the first craft-gate denial wins and a throwing gate is skipped, wear gates refuse only
  when one says no, assistance only eases and stops at the floor, and `GemCutEvent` accepts only the same stone at
  another quality.

## 1.0.0

First version, for Minecraft 1.20.1 and Forge 47. Successor to Jewelcraft Reforged, whose worlds and items migrate.

### The loop

- **Gemstones.** Diamonds, emeralds, amethyst, lapis and quartz are rough stones as they are, and so is any gem from
  another mod (`forge:gems/*` tags). Rubies, sapphires, topaz and onyx also turn up rough in treasure and Jeweler
  trades, so the mod is complete on its own. A stone is appraised the first time it reaches the wheel (size, natural
  quality, inclusions); that character is fixed, so cancelling a cut never re-rolls it.
- **Lapidary Wheel - cutting.** Choose a cut (Cabochon, Round, Emerald, Brilliant) and grind facet by facet: keep the
  contact point on the facet, stop inside the depth band and let the stone cool before it chips. Hardness,
  brittleness, inclusions, size, the cut and the fitted lap (Emery or Diamond, worn by every cut) set the difficulty.
  Cut grades run Ruined, Chipped, Acceptable, Fine, Excellent and Flawless. Failure costs size, quality or a damage
  state (scratched, chipped, fractured); a stone is destroyed only with `catastrophicGemFailure`. The stone stays in
  the wheel for the whole session, so cancelling, disconnecting or breaking the wheel leaves it unchanged.
- **Blanks.** With Immersive Smithing, ring blanks, amulet frames, bracelet blanks and circlet frames are small jobs at
  the Smith's Forge and Anvil in any metal it knows, and the smithing quality is kept. Without it, blanks are crafted
  from nuggets with middling metalwork (`fallbackSmithingQuality`).
- **Jeweler's Bench - setting.** Place a blank, use the bench with Jeweler's Pliers, and choose a design, the stones
  (all of one gem) and a finish. Prongs are pressed in a balanced order: hold to squeeze and release inside the
  pressure band, which follows the pressure already on the opposite prong. Overshooting bends a prong or chips a
  brittle stone. Fine Pliers give wider pressure windows. Stones and finish material wait in the bench's escrow and
  are used or returned exactly once.
- **Finishes.** Polished (takes Polishing Compound), Burnished and Antique (takes gunpowder). A finished piece can be
  refinished at the bench.
- **Workmanship** combines metalwork, the cut and the setting (weights in server config) and is graded Faulty,
  Common, Fine, Exceptional or Masterwork.

### Wearing jewelry

- Rings, amulets, bracelets and circlets: two ring slots and one of each other form. With Curios they use its slots
  (the legacy `charm` slot also takes amulets); without it, built-in slots open from a ring button in the inventory or
  with J (`equipmentSystem`, common config, can force either). Other equipment systems plug in through
  `IJewelryEquipmentProvider`.
- Each gem has a trait and each form expresses it differently: a ruby ring builds heat with every hit, a ruby amulet
  saves its wearer when nearly beaten, a ruby bracelet rewards a charge. The metal biases the effect (gold favours
  magic, silver protection, copper answers quickly), the cut sets its strength and steadiness, workmanship its
  efficiency and the finish tunes it. Ten traits ship; gems found through tags get the neutral Resonance.
- `max<Form>Slots` (server config) caps how many pieces of each form are active.

### Care and rework

- Jewelry has Integrity instead of durability, worn only by a single heavy hit (`severeHitFraction` of max health). At
  zero a piece is broken and inert, never deleted.
- Repair at the bench with one nugget of the frame's metal, a little less fully after every rework. Stones can be
  lifted out gently with the pliers and loose prongs retightened; a chipped or poorly cut stone is recut at the wheel,
  smaller but whole.

### Designs, villagers and loot

- Ten designs. Solitaire and bezel rings, pendant and locket amulets, the cuff bracelet and the simple circlet are
  known to everyone; the three-stone trilogy ring, halo amulet, link bracelet and diadem are learned from Design
  Sketches (`requireDesignSketches`).
- The Jeweler villager works at the Jeweler's Bench and trades rough and cut stones, pliers, laps, Polishing Compound,
  the handbook, sketches and, at the highest level, village jewelry that is never a Masterwork
  (`villagerMaxWorkmanship`).
- Rough stones and Design Sketches in dungeon, temple, stronghold and other treasure chests.
- Twelve advancements, from the first cut to a Masterwork piece, a recovered stone, a new design and a repair.

### Compatibility

- **Immersive Smithing** (optional): blank recipes for every metal with a jewelry metal definition and for every family
  Smithing knows, small-workpiece forge and anvil patterns, the quench quality carried onto the blank, and blanks
  recycled for their metal (finished jewelry never is). `requireImmersiveSmithing` and `enableFallbackBlankRecipes`
  (common config) decide the fallback.
- **Curios** (optional), **JEI** (Gem Cutting, Jewelry Design, Finishing and Reworking categories) and **Jade**
  (wheel and bench overlays).
- Seventeen metal definitions: gold, iron, copper, netherite, silver, rose gold, steel, bronze, electrum, platinum,
  tin, nickel, aluminum, mithril, manasteel, terrasteel and elementium. Other metals get a generic profile from
  Smithing's tint and name.
- Gem auto-detection: every `forge:gems/<name>` tag without a definition becomes a generic gem, its colour sampled from
  the item's texture.
- `IJewelryEffectContributor` lets other mods add effects to a worn piece from their own data on it (Immersive
  Apotheosis affixes and socketed gems).

### Migrating from Jewelcraft Reforged

- The old ids (`jewelcraft:material_ring`, `material_amulet`, `material_bracelet`, `material_circlet`,
  `jeweler_bench` and the `jeweler` profession) resolve to the new entries in inventories, chests, Curios slots,
  chunks and villagers, and worlds open without the missing-entries warning.
- Legacy jewelry NBT converts on load into the new model (craftsmanship 0.65 per stage, round cut, polished finish);
  names, enchantments and other mods' NBT are kept. `enableLegacyConversion` (common config) turns it off.
- `jewelcraft:jewelry_box` has no successor and becomes a chest.

### Datapacks, configuration, commands and API

- Datapack definitions under `data/<namespace>/immersive_jewelry/`: `gems`, `gem_cuts`, `finishes`, `jewelry_forms`,
  `traits` and `metals`. Designs are the recipe type `immersive_jewelry:jewelry_design`. Definitions sync to clients
  after every reload.
- Common config (integration switches), server config (synced: minigames, quality, effects, wear, display,
  progression) and client config (presentation and accessibility). Accessibility: larger targets and slower minigames
  (honoured by the server while `allowAccessibilityAssist` is on), colour-blind and high-contrast palettes, larger
  markers, reduced motion, cue sounds and screen-shake strength. Success is always shown by shape, text and sound as
  well as colour.
- `/immersivejewelry` (operators): `give`, `givegem`, `inspect`, `setquality`, `appraise`, `listgems`, `listcuts`,
  `reloaddebug`, `sessions`, `worn` and `learn`.
- `ImmersiveJewelryAPI`: custom effect types (`registerEffectType`), equipment providers and effect contributors (also
  by IMC), and the `jewelry`, `gem`, `blank`, `power` and `worn` queries. Forge-bus events `GemCutEvent`,
  `JewelryCraftedEvent`, `JewelryReworkedEvent`, `JewelryEquipEvent` and `JewelryBrokenEvent`.

### Presentation

- Layered, tinted item models (metal, finish and gem colours, sparkle by cut quality) with a gem shape per cut, so no
  per-combination textures.
- Generated pixel-art textures and synthesised sounds; gem dust, gem sparkle and metal spark particles.
- Stoneborn-style workshop screens, the Jeweler's Handbook and matching JEI cards.

### Multiplayer

- Minigames are server-authoritative and deterministic: clients send only per-step inputs, and the server replays them
  through the same simulation, bounds latency compensation and grades the result.
- One player at a time works a wheel or bench. Bench escrow left in an unloading chunk is saved and dropped at the
  bench when it next loads.

### Known issues

- Immersive Smithing 1.0.0 does not add provider recipes to the recipe manager, so blank recipes for metals without a
  jewelry metal definition work at the forge but do not show in JEI.
