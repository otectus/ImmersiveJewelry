# Immersive Jewelry - Architecture and Decisions

The design authority is `Immersive_Jewelry_Development_Spec.md`. This file records how it is implemented, and every
place where the implementation deliberately departs from it (spec section 77, rule 10). Sister mods and architectural
baseline: `../ImmersiveSmithing` and `../ImmersiveTanning` (same package layout, session model, config split, escrow
rules, GameTest and smoke-test setup).

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

## Workstations and minigames (spec 6, 10, 12, 33-35, 61-63)

The session model is the sister mods': the server owns every session (`SessionManager`), generates seeds, replays
client inputs through the same deterministic simulation and computes all quality itself. Clients send inputs only
(positions, button edges with times). Timings are validated with `SessionTiming` (latency-compensated clamp, never
backwards), packets carry sequence numbers, and each session completes at most once.

- Lapidary Wheel. The stone sits in the wheel's block entity for the whole session, so nothing leaves the world while
  a cut is running. Cancel, disconnect, death or break leaves or drops the stone unchanged. Only completion mutates it,
  in place. `CutSimulation`: the player keeps a drifting contact point on the highlighted facet and holds to grind.
  Pressure ramps with hold time and raises heat and drift. Each facet must stop inside its depth window, and heat past
  the stone's chip threshold chips it. Difficulty comes from hardness, brittleness, inclusions, size, the cut, the lap
  and config. Failure costs size, quality or a damage state. Deleting the stone requires `catastrophicGemFailure`.
- Jeweler's Bench. The blank or finished piece sits on the bench. Gems and finish materials move into the bench's
  persisted escrow when setting starts, and are consumed exactly once on completion or refunded exactly once on
  cancel. On chunk unload the escrow stays saved and is dropped at the bench when it next loads (Tanning rule).
  `SettingSimulation`: prongs in a balanced sequence. Hold to squeeze, and release inside the pressure window. The
  window follows the pressure already applied to the opposite prong, so balance is the skill. Overshoot bends a
  prong, or chips a brittle stone, and a release below the window leaves the prong loose. The same engine with a low
  window removes stones (spec 42).
- Finishing is a choice made at the bench (no minigame, spec 13). A finish can cost a cheap consumable (data).

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
- Screens use a Stoneborn-style masonry surround, warm stone insets and restrained gold trim. Shared GUI textures
  come from `art/jewelry_art.py`; `JewelryScreen` fits workstation and handbook layouts to the viewport and applies
  the same transform to pointer input. Equipment slots and JEI cards share the palette and slot art.
  Accessibility options: larger targets and slower minigames (server-validated assist flags), colour-blind palette,
  high contrast, reduced motion, cue sounds and screen-shake strength. Success is always shown by shape, text and
  sound as well as colour.

## Deviations summary

| Spec | Implementation | Why |
|---|---|---|
| 50.1 keep `jewelcraft` id | new id `immersive_jewelry` + aliases, remaps and NBT conversion | family naming, no namespace impersonation; migration is lossless |
| 25 `data/immersive_jewelry/<kind>/` | `data/<ns>/immersive_jewelry/<kind>/` | spec 73 and the Smithing convention; lets any namespace add definitions |
| 25 `material_overrides` | `metals` | clearer name for pack authors |
| 10.4 band "Rough" | omitted | its thresholds define six bands, and the name collides with the uncut rough stone |
| 7.1 one pliers item | Jeweler's Pliers and Fine Pliers | a tolerance tier (spec 7.1 "quality tier") |
| 7.2 upgradeable wheel | Emery Lap and Diamond Lap in the wheel's lap slot | two items instead of five components |
