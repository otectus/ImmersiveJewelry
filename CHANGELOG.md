# Changelog

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
