# Immersive Jewelry
## Development Specification for Forge 1.20.1

**Project type:** Minecraft Forge mod  
**Minecraft version:** 1.20.1  
**Java version:** 17  
**Working title:** Immersive Jewelry  
**Relationship to existing project:** Reworked successor to Jewelcraft Reforged  
**Primary integration:** Immersive Smithing  
**Design goal:** Replace recipe-only jewelry crafting with a tactile, skill-influenced jewelry-making profession focused on lapidary work, assembly, finishing, customization, and meaningful material choices.

---

# 1. Executive Summary

Immersive Jewelry is a full redesign of Jewelcraft Reforged.

The mod should preserve the fantasy of crafting wearable jewelry from metals and gemstones, but it should no longer behave like a conventional crafting-table equipment mod.

Instead, the player should:

1. choose a jewelry design,
2. smith or obtain the metal component,
3. cut a gemstone,
4. set the gemstone into the jewelry,
5. finish the piece,
6. receive a unique wearable item whose properties are derived from its actual construction.

The mod belongs to the same design family as the other **Immersive** mods.

The shared philosophy is:

> Vanilla-style recipe abstractions should be replaced, where worthwhile, with short, tactile, understandable crafting interactions in which player choices and craftsmanship matter.

Immersive Jewelry must **not** duplicate metalworking systems already provided by Immersive Smithing.

The responsibility split is:

- **Immersive Smithing** handles forging and shaping metal.
- **Immersive Jewelry** handles gemstone preparation, jewelry assembly, stone setting, finishing, item identity, wearability, and gemstone effects.
- Future integrations such as **Immersive Enchanting** may operate on the finished jewelry item.

The resulting gameplay should feel like a real profession without becoming a simulator full of unnecessary intermediate items or repetitive chores.

---

# 2. Design Pillars

## 2.1 Tactile Rather Than Tedious

Immersion should come from meaningful interaction, not from excessive recipe chains.

Good examples:

- shaping a gemstone on a lapidary wheel;
- securing a stone in a setting;
- choosing a finish;
- seeing the workpiece physically represented.

Bad examples:

- crafting six disposable tools;
- creating five chemically identical intermediates;
- repeating identical timing bars for every stage;
- requiring several GUIs just to make a simple ring.

---

## 2.2 Materials Matter

The finished jewelry item should derive its identity from:

```text
Jewelry Form
    ×
Metal
    ×
Gemstone
    ×
Gem Cut
    ×
Craftsmanship
    ×
Finish
```

The mod should avoid hardcoding hundreds of individual item combinations.

---

## 2.3 Craftsmanship Matters Without Being Punishing

Player skill should influence quality.

Failure should usually degrade the result rather than simply delete expensive materials.

Examples:

- imperfect cuts;
- reduced gem size;
- chipped stones;
- loose settings;
- lower workmanship;
- salvageable components.

Catastrophic material deletion should be rare and configurable.

---

## 2.4 Strong Integration, Not Duplicate Systems

Immersive Jewelry must rely on Immersive Smithing for metalworking.

Do not add:

- a second forge;
- a second anvil system;
- a duplicate ingot-heating mechanic;
- jewelry-specific metal recipes that bypass Immersive Smithing when Immersive Smithing is installed and enabled.

---

## 2.5 Data-Driven Extensibility

Gemstones, cuts, jewelry forms, traits, finishes, and recipes should be defined through datapacks wherever practical.

Third-party mods should be able to add compatible materials without requiring Java code.

---

# 3. Core Gameplay Loop

The expected standard progression is:

```text
Choose Design
    ↓
Smith Metal Component
    ↓
Prepare / Cut Gemstone
    ↓
Assemble Jewelry
    ↓
Set Gemstone
    ↓
Apply Finish
    ↓
Completed Jewelry
```

Example:

```text
Gold Ingot
    ↓
Immersive Smithing
    ↓
Gold Ring Blank

Rough Ruby
    ↓
Lapidary Wheel
    ↓
Brilliant-Cut Ruby

Gold Ring Blank + Brilliant-Cut Ruby
    ↓
Jeweler's Bench
    ↓
Stone Setting Interaction
    ↓
Polished Gold Ruby Ring
```

The process should take long enough to feel deliberate while remaining practical for repeated crafting.

Target approximate hands-on interaction time for a normal piece:

- simple jewelry: 15–30 seconds after materials are prepared;
- complex jewelry: 30–60 seconds;
- gemstone cutting: varies by hardness, cut, equipment, and difficulty.

These are design targets, not rigid timer requirements.

---

# 4. Jewelry Forms

Initial release should focus on four core forms.

## 4.1 Ring

Gameplay identity:

- specialization;
- precise bonuses;
- triggered effects;
- offensive or utility mechanics.

Recommended equipment capacity:

- two ring slots.

---

## 4.2 Amulet

Gameplay identity:

- protection;
- sustained passive effects;
- recovery;
- resistance;
- survival-oriented effects.

Recommended equipment capacity:

- one amulet slot.

---

## 4.3 Bracelet

Gameplay identity:

- movement;
- physical actions;
- stamina-like mechanics;
- tool use;
- mobility;
- practical utility.

Recommended equipment capacity:

- one bracelet slot.

---

## 4.4 Circlet

Gameplay identity:

- magic;
- knowledge;
- perception;
- resource control;
- spellcasting;
- supernatural abilities.

Recommended equipment capacity:

- one circlet slot.

---

## 4.5 Scope Control

Do not add earrings, brooches, crowns, anklets, necklaces, chokers, or similar forms in the first implementation unless required for compatibility.

The four core forms should first receive distinct mechanics and polished presentation.

---

# 5. Immersive Smithing Integration

This is a core requirement.

Immersive Jewelry should treat Immersive Smithing as the authoritative system for producing metal jewelry blanks and frames.

## 5.1 Required Metal Workpiece Types

Immersive Jewelry should define or request the following logical workpiece forms:

```text
ring_blank
bracelet_blank
amulet_frame
circlet_frame
```

Optional future forms:

```text
brooch_frame
earring_blank
chain_segment
decorative_mount
```

Do not implement future forms unless needed.

---

## 5.2 Suggested Integration Contract

Immersive Smithing should ideally expose a generic API capable of representing:

```java
record SmithingWorkpieceData(
    ResourceLocation materialId,
    ResourceLocation formId,
    float materialMass,
    float smithingQuality,
    int workSteps,
    CompoundTag customData
) {}
```

Immersive Jewelry should consume:

- material ID;
- form ID;
- smithing quality;
- optional smithing metadata;
- optional durability or defect information.

The final jewelry item must preserve relevant smithing data.

---

## 5.3 Small Workpieces

Jewelry should use a small-workpiece smithing category.

Recommended properties:

- shorter heating time where physically reasonable;
- fewer shaping steps;
- smaller metal quantities;
- reduced forging duration compared with weapons and armor;
- tighter precision requirements rather than simply longer work.

A ring blank should not take as long to forge as a sword blade.

---

## 5.4 Fallback Behavior

The build must support three configurations.

### Mode A: Immersive Smithing Installed

Use the full integrated process.

### Mode B: Immersive Smithing Missing but Optional Compatibility Enabled

Provide a lightweight fallback path so the mod remains loadable.

Recommended fallback:

- standard crafting or smithing recipes for jewelry blanks;
- reduced metal quality metadata;
- clear config option to disable fallback.

### Mode C: Immersive Smithing Required by Pack

Allow modpack authors to require Immersive Smithing and disable all fallback blank recipes.

Do not hard-crash because an optional integration class was referenced during class loading.

Use safe mod-presence checks.

---

# 6. Workstations

## 6.1 Jeweler's Bench

The Jeweler's Bench is the central assembly station.

Responsibilities:

- select jewelry design;
- accept a metal blank/frame;
- accept a prepared gemstone;
- host stone-setting interaction;
- apply or select finish;
- perform disassembly or reworking;
- act as the Jeweler villager workstation.

It should not perform metal forging.

### Physical Presentation

Where practical, display the current workpiece in-world on the bench.

Possible visible states:

- empty;
- metal blank;
- blank plus loose gemstone;
- partially set gemstone;
- completed jewelry.

This can be implemented using a BlockEntityRenderer.

---

## 6.2 Lapidary Wheel

The Lapidary Wheel is the signature workstation.

Responsibilities:

- cut rough gemstones;
- reshape or recut gems;
- optionally polish gems;
- generate cut-quality data.

The wheel should visibly animate while operating.

Desired presentation:

- rotating wheel;
- gemstone shown near the wheel;
- gemstone-colored particles;
- grinding sounds;
- audio intensity based on pressure or contact;
- subtle visual feedback when the player is cutting at the correct angle.

---

# 7. Tools

Keep the tool list intentionally small.

## 7.1 Jeweler's Pliers

Uses:

- stone setting;
- jewelry manipulation;
- removing stones;
- minor repair.

Possible attributes:

- precision;
- durability;
- quality tier.

Tool quality should primarily influence tolerance and ease of use rather than raw speed.

---

## 7.2 Lapidary Tooling

The Lapidary Wheel itself may contain upgradeable components.

Potential upgrade dimensions:

- abrasive quality;
- wheel quality;
- precision;
- cooling;
- stability.

Avoid creating many single-purpose handheld tools.

---

## 7.3 Polishing Material

Optional.

If implemented, polishing material should be inexpensive and broadly reusable.

Do not require a chain of obscure consumables.

---

# 8. Gemstone Lifecycle

Gemstones should have meaningful states.

Recommended lifecycle:

```text
Raw / Rough Gemstone
    ↓
Cut Gemstone
    ↓
Set Gemstone
```

Do not register separate item IDs for every possible quality level.

Use item data.

---

# 9. Gemstone Data Model

A gemstone instance may contain:

```text
GemId
OriginalSize
CurrentSize
NaturalQuality
Inclusions
CutId
CutQuality
DamageState
CustomTraits
```

Example conceptual NBT/data:

```json
{
  "gem": "immersive_jewelry:ruby",
  "original_size": 1.0,
  "current_size": 0.91,
  "natural_quality": 0.82,
  "inclusions": 0.14,
  "cut": "immersive_jewelry:brilliant",
  "cut_quality": 0.93,
  "damage": "none"
}
```

For Forge 1.20.1, use stable ItemStack NBT or capabilities as appropriate.

Prefer a clean wrapper API around raw tag access.

---

# 10. Gem Cutting Minigame

Gem cutting is one of the two primary skill interactions.

## 10.1 Objective

The player shapes the rough gemstone toward a target cut.

The game should communicate:

- desired facet orientation;
- current contact point;
- current angle;
- pressure;
- progress;
- risk.

---

## 10.2 Interaction Concept

Recommended control model:

- move the stone around a magnified representation;
- rotate or orient it;
- hold interaction to grind;
- keep contact within a valid facet region;
- release to reposition.

Possible implementation styles:

- 2D interactive GUI;
- simplified pseudo-3D preview;
- radial facet puzzle.

Avoid requiring actual mouse-drawing precision at the pixel level.

---

## 10.3 Variables Affecting Difficulty

Difficulty should derive from:

- gemstone hardness;
- gemstone brittleness;
- desired cut complexity;
- natural inclusions;
- gemstone size;
- tool quality;
- player progression if progression exists;
- configuration.

---

## 10.4 Cutting Outcomes

Recommended quality bands:

```text
Ruined
Chipped
Rough
Acceptable
Fine
Excellent
Flawless
```

The exact visible names may be refined.

Internally store a numeric score such as `0.0–1.0`.

Suggested interpretation:

```text
0.00–0.20  Ruined
0.20–0.40  Chipped
0.40–0.60  Acceptable
0.60–0.78  Fine
0.78–0.92  Excellent
0.92–1.00  Flawless
```

These thresholds should be configurable or defined per cut if necessary.

---

## 10.5 Failure Philosophy

A mistake should usually produce one of:

- reduced size;
- reduced cut quality;
- chipped state;
- lower maximum attainable quality;
- gemstone fragments;
- salvageable damaged gemstone.

Avoid instantly deleting rare gemstones except in explicitly configured catastrophic-failure cases.

---

# 11. Gem Cuts

Initial supported cuts should remain limited.

Recommended set:

## 11.1 Round

- balanced;
- easy;
- low risk;
- generic compatibility.

## 11.2 Brilliant

- stronger expression of gemstone trait;
- harder to cut;
- higher risk.

## 11.3 Emerald

- stable;
- reliable;
- moderate difficulty;
- may favor duration or consistency.

## 11.4 Cabochon

- easiest;
- suited for opaque or magical stones;
- lower raw amplification;
- very forgiving.

## 11.5 Optional Additional Cuts

Future:

- princess;
- marquise;
- oval;
- pear.

Do not include them until they add meaningful variation.

---

# 12. Stone Setting Minigame

Stone setting is the second primary skill interaction.

## 12.1 Objective

Secure the prepared gemstone into the jewelry frame.

A typical prong setting could require alternating pressure around the stone.

Example:

```text
      1
   ┌─────┐
 4 │  ◆  │ 2
   └─────┘
      3
```

Recommended sequence:

```text
1 → 3 → 2 → 4
```

or another balanced sequence derived from the design.

---

## 12.2 Player Feedback

Show:

- current prong or contact point;
- applied pressure;
- balance;
- gemstone alignment;
- remaining unsecured points;
- risk state.

---

## 12.3 Outcomes

Possible defects:

- loose stone;
- crooked setting;
- scratched metal;
- bent setting;
- chipped gemstone;
- reduced workmanship.

A successful setting increases workmanship.

---

## 12.4 Complexity Scaling

More expensive or elaborate pieces can use:

- more prongs;
- multiple stones;
- asymmetrical settings;
- tighter pressure tolerances;
- smaller targets.

Do not simply make the interaction take longer.

---

# 13. Finishing

Finishing should be a choice, not another minigame.

Recommended initial finishes:

## 13.1 Polished

Suggested identity:

- stronger gemstone expression;
- brighter appearance;
- lower resistance to visible wear if wear is modeled.

## 13.2 Burnished

Suggested identity:

- improved structural durability;
- slightly lower gem amplification;
- muted appearance.

## 13.3 Antique

Suggested identity:

- improved enchantability or magical affinity;
- lower brilliance;
- distinct visual treatment.

Exact statistics should be balanced through data.

Finishes should be datapack-defined.

---

# 14. Craftsmanship Model

Keep player-facing craftsmanship understandable.

The item should internally track at least:

```text
SmithingQuality
CutQuality
SettingQuality
OverallWorkmanship
```

Optional:

```text
FinishQuality
RepairState
StructuralIntegrity
```

Avoid exposing every internal number directly.

---

## 14.1 Smithing Quality

Inherited from Immersive Smithing.

Represents:

- quality of the metal frame;
- structural precision;
- metal defects;
- ability to hold and channel the gemstone.

---

## 14.2 Cut Quality

Produced by gemstone cutting.

Represents:

- symmetry;
- retained material;
- facet precision;
- brilliance;
- effectiveness of gemstone trait.

---

## 14.3 Setting Quality

Produced during assembly.

Represents:

- alignment;
- security;
- precision;
- damage caused during mounting.

---

## 14.4 Overall Workmanship

Recommended initial formula:

```text
OverallWorkmanship =
    SmithingQuality * 0.35
  + CutQuality      * 0.35
  + SettingQuality  * 0.30
```

This should be configurable or data-driven.

Do not let one weak stage become mathematically irrelevant.

---

## 14.5 Player-Facing Labels

Example:

```text
Faulty
Common
Fine
Exceptional
Masterwork
```

Tooltip example:

```text
Ruby Ring

Gold
Fine Metalwork

Ruby
Excellent Brilliant Cut

Exceptional Workmanship
Polished
```

---

# 15. Jewelry Effect Model

The effect system should be compositional rather than based on one hardcoded effect per final item.

Recommended model:

```text
Gemstone = Effect Theme
Jewelry Form = Expression / Delivery Method
Metal = Modifier / Bias
Gem Cut = Magnitude / Stability Modifier
Workmanship = Efficiency
Finish = Final Tuning
```

---

# 16. Gemstone Identity

A gemstone should express the same broad fantasy regardless of jewelry form.

Example conceptual themes:

```text
Ruby      → heat / aggression / vitality
Sapphire  → focus / mana / clarity
Emerald   → recovery / growth / endurance
Diamond   → purity / resilience / amplification
Amethyst  → magic / perception / warding
Topaz     → energy / speed / fortune
Onyx      → concealment / absorption / resistance
```

These are design placeholders.

All final gemstone traits should be data-driven.

---

# 17. Form Expressions

Instead of every gemstone granting the same bonus on every jewelry item, the jewelry form should reinterpret the gemstone theme.

Example for Ruby:

## Ruby Ring

Possible identity:

- offensive momentum;
- consecutive-hit heat mechanic;
- weapon-related trigger.

## Ruby Amulet

Possible identity:

- emergency protection;
- fire resistance;
- survival effect.

## Ruby Bracelet

Possible identity:

- action speed;
- sprint or melee utility.

## Ruby Circlet

Possible identity:

- spell power;
- fire magic interaction;
- ability amplification.

This avoids shallow stat duplication.

---

# 18. Metals

Metals should alter how jewelry behaves without replacing gemstone identity.

Conceptual examples:

## Gold

- higher magical potency;
- higher enchantability;
- lower structural resilience.

## Silver

- protective;
- supernatural resistance;
- undead or curse interaction if appropriate.

## Copper

- utility;
- conductivity;
- energy transfer;
- responsiveness.

## Iron

- durable;
- physically stable;
- lower gem amplification.

## Modded Metals

Should be resolved through Immersive Smithing integration and optional jewelry metadata.

Avoid hardcoded instanceof checks for every modded ingot.

---

# 19. Effect Design Rules

Avoid generic stacks of passive attributes.

Prefer:

- conditional effects;
- stateful effects;
- triggered effects;
- cooldown-based effects;
- utility effects;
- synergy with player actions.

Avoid excessive use of:

```text
+5% damage
+3 armor
+4% movement speed
```

unless used sparingly as supporting modifiers.

A jewelry piece should feel behaviorally distinct.

---

# 20. Equipment Integration

Recommended dependency approach:

- Curios API support as the primary equipment layer;
- graceful fallback if Curios is not required by the pack;
- configurable slot counts.

Suggested defaults:

```text
Ring:     2
Amulet:   1
Bracelet: 1
Circlet:  1
```

If Curios is required for the project, declare that clearly in dependency metadata.

If optional, isolate all Curios classes behind safe loading boundaries.

---

# 21. Item Architecture

Do not register every metal/gem combination as a separate item.

Recommended base items:

```text
immersive_jewelry:ring
immersive_jewelry:amulet
immersive_jewelry:bracelet
immersive_jewelry:circlet
immersive_jewelry:rough_gem
immersive_jewelry:cut_gem
```

Possible blank items:

```text
immersive_jewelry:ring_blank
immersive_jewelry:bracelet_blank
immersive_jewelry:amulet_frame
immersive_jewelry:circlet_frame
```

Depending on Immersive Smithing architecture, blanks may instead use a shared generic workpiece item.

---

# 22. Jewelry Item Data

Conceptual NBT:

```json
{
  "form": "immersive_jewelry:ring",
  "metal": "minecraft:gold",
  "gem": "immersive_jewelry:ruby",
  "gem_cut": "immersive_jewelry:brilliant",
  "finish": "immersive_jewelry:polished",
  "smithing_quality": 0.87,
  "cut_quality": 0.94,
  "setting_quality": 0.91,
  "workmanship": 0.906,
  "integrity": 1.0,
  "legacy_converted": false
}
```

Wrap all serialization/deserialization behind dedicated classes.

Do not scatter raw tag keys across the codebase.

---

# 23. Suggested Code Architecture

Recommended packages:

```text
com.<author>.immersivejewelry
├── api
│   ├── gem
│   ├── jewelry
│   ├── integration
│   └── event
├── block
├── blockentity
├── client
│   ├── gui
│   ├── render
│   ├── model
│   └── input
├── config
├── data
│   ├── gem
│   ├── cut
│   ├── finish
│   ├── form
│   └── trait
├── effect
├── item
├── menu
├── network
├── recipe
├── registry
├── integration
│   ├── smithing
│   ├── curios
│   └── compat
├── migration
├── villager
└── util
```

---

# 24. Registry Manager Pattern

Use centralized Forge DeferredRegisters for:

- items;
- blocks;
- block entities;
- menu types;
- recipe serializers;
- sounds;
- particles;
- villager professions;
- POIs.

Do not spread static registration across unrelated classes.

---

# 25. Data-Driven Registries

Use reloadable JSON definitions for:

```text
gems
gem_cuts
jewelry_forms
finishes
traits
material_overrides
designs
```

Recommended resource paths:

```text
data/immersive_jewelry/gems/
data/immersive_jewelry/gem_cuts/
data/immersive_jewelry/jewelry_forms/
data/immersive_jewelry/finishes/
data/immersive_jewelry/traits/
data/immersive_jewelry/designs/
data/immersive_jewelry/material_overrides/
```

---

# 26. Gem Definition Schema

Example:

```json
{
  "ingredient": {
    "tag": "forge:gems/ruby"
  },
  "hardness": 8.5,
  "brittleness": 0.35,
  "default_trait": "immersive_jewelry:vitality",
  "base_color": "#B51632",
  "cutting": {
    "difficulty": 0.8,
    "size_retention": 0.9
  },
  "supported_cuts": [
    "immersive_jewelry:round",
    "immersive_jewelry:brilliant",
    "immersive_jewelry:emerald"
  ]
}
```

---

# 27. Gem Cut Schema

Example:

```json
{
  "difficulty": 0.85,
  "min_facets": 8,
  "quality_modifier": 1.12,
  "size_retention": 0.82,
  "stability": 0.9,
  "visual_profile": "brilliant"
}
```

---

# 28. Finish Schema

Example:

```json
{
  "gem_potency_multiplier": 1.05,
  "integrity_multiplier": 0.95,
  "enchantability_multiplier": 1.0,
  "visual_profile": "polished"
}
```

---

# 29. Jewelry Form Schema

Example:

```json
{
  "slot": "ring",
  "max_gems": 1,
  "base_integrity": 1.0,
  "trait_expression": "immersive_jewelry:ring",
  "smithing_form": "immersive_jewelry:ring_blank"
}
```

---

# 30. Trait System

Traits should be composable.

Recommended API concept:

```java
public interface JewelryTrait {
    ResourceLocation id();

    void onEquip(JewelryContext context);

    void onUnequip(JewelryContext context);

    void tick(JewelryContext context);

    default void onAttack(JewelryAttackContext context) {}

    default void onHurt(JewelryHurtContext context) {}

    default void onUseItem(JewelryUseContext context) {}
}
```

Avoid polling every player and every item for expensive conditions every tick.

Use event-driven hooks where possible.

---

# 31. Runtime Effect Resolution

When a jewelry item is equipped:

1. read jewelry data;
2. resolve gemstone trait;
3. resolve form expression;
4. resolve metal modifiers;
5. apply cut modifier;
6. apply workmanship modifier;
7. apply finish modifier;
8. build cached effective behavior.

Do not recompute every derived value every tick.

Cache resolved data and invalidate when:

- item changes;
- jewelry data changes;
- datapack reload occurs;
- equipment slot changes.

---

# 32. Performance Requirements

The mod should be safe for large modpacks.

Avoid:

- full inventory scans every tick;
- rebuilding effect objects every frame;
- per-tick NBT parsing where cached values are possible;
- reflection in hot paths;
- repeated registry lookups by string;
- repeated JSON parsing after reload.

Use:

- event-driven equipment change detection;
- lightweight server-side state;
- cached parsed definitions;
- deterministic networking;
- bounded GUI synchronization.

---

# 33. Networking

The server is authoritative for:

- minigame start;
- minigame result validation;
- gemstone quality;
- setting quality;
- item creation;
- component mutation.

The client handles:

- input;
- animation;
- visual previews;
- local interpolation.

Never trust a packet such as:

```text
"I earned 100% quality"
```

Instead send enough input/result data for the server to validate the outcome.

---

# 34. Gem Cutting Validation

Recommended approach:

At minigame start, server creates:

```text
sessionId
seed
gemDefinition
cutDefinition
difficulty
toolModifiers
```

Client performs interaction.

Client submits compact action or result data.

Server validates:

- session ID;
- allowed timing;
- expected target data;
- bounds;
- impossible state changes.

The game does not need esports-grade anti-cheat, but obvious packet spoofing should not trivially create perfect gems.

---

# 35. Stone Setting Validation

Use the same session model.

Track:

- target positions;
- expected sequence;
- pressure windows;
- allowed input count;
- elapsed duration.

Final quality is computed server-side.

---

# 36. Visual System

Jewelry should visually reflect its components.

Recommended rendering approach:

```text
Base Form Layer
+
Metal Material Layer
+
Gem Shape Layer
+
Gem Color / Texture
+
Finish Layer
+
Optional Quality Effects
```

Avoid authoring a separate texture for every possible combination.

---

# 37. Dynamic Item Models

Investigate one of:

- custom baked model;
- item renderer;
- layered generated models;
- custom `BlockEntityWithoutLevelRenderer`;
- model overrides where practical.

The final implementation should prioritize:

- performance;
- compatibility with resource packs;
- predictable rendering;
- maintainability.

---

# 38. Visual Quality Indicators

Subtle visual differences are encouraged.

Possible examples:

- excellent cuts show stronger gemstone sparkle;
- chipped cuts show slight asymmetry;
- polished finish increases shine;
- antique finish darkens recesses;
- different metals alter frame color and roughness.

Do not make low-quality items visually ugly to the point they feel unusable.

---

# 39. Tooltips

Tooltips should be informative without exposing excessive debug-like data.

Recommended format:

```text
Ruby Ring
Gold

Ruby
Brilliant Cut — Excellent

Exceptional Workmanship
Polished

[Effect description]
```

Advanced details can be displayed while holding Shift.

Possible advanced tooltip:

```text
Metalwork: 87%
Gem Cut: 94%
Setting: 91%
```

This should be configurable.

---

# 40. Durability, Wear, and Damage

Jewelry should not behave exactly like armor durability.

Recommended model:

## Integrity

Represents structural condition.

Can be reduced by:

- severe damage events;
- failed repairs;
- repeated stone removal;
- specific gameplay effects.

## Gem Damage

Possible states:

```text
none
scratched
chipped
fractured
```

Keep the system light.

Do not make routine combat constantly destroy jewelry.

---

# 41. Repair

Jewelry can be serviced at the Jeweler's Bench.

Possible repair targets:

- metal frame;
- loose settings;
- cosmetic wear.

A chipped gemstone should generally require:

- recutting;
- reduced gem size;
- replacement.

---

# 42. Reworking and Disassembly

Players should be able to reclaim valuable stones.

At the Jeweler's Bench:

```text
Finished Jewelry
    ↓
Remove Gem
    ↓
Metal Frame + Gemstone
```

Removal risk should depend on:

- setting type;
- workmanship;
- tool quality;
- existing damage.

Potential consequences:

- reduced frame integrity;
- minor gemstone damage;
- no penalty on excellent removal.

This should not be an arbitrary percentage-only gamble if a small interaction can represent it.

---

# 43. Jeweler Villager Profession

Retain the Jeweler profession.

Workstation:

```text
Jeweler's Bench
```

Suggested trades:

### Novice

- common rough gemstones;
- polishing materials;
- basic tools.

### Apprentice

- common cut gemstones;
- basic jewelry designs.

### Journeyman

- uncommon gems;
- better tooling;
- repair services or service tokens.

### Expert

- rare gemstones;
- advanced designs;
- high-quality pre-cut gems.

### Master

- exceptional stones;
- unique designs;
- rare materials;
- occasional high-workmanship jewelry.

Do not allow the villager to trivialize the player's crafting profession.

Purchased finished jewelry should generally cap below player-produced masterwork quality unless deliberately configured otherwise.

---

# 44. Compatibility Strategy

Compatibility must be treated as a first-class feature.

---

# 45. Automatic Gem Detection

By default, detect common gem tags such as:

```text
forge:gems/*
```

Unknown gemstones should be usable through a safe generic profile.

Generic defaults may derive:

- color from item texture only if feasible;
- otherwise config/default color;
- moderate hardness;
- generic neutral trait until datapack override.

Do not crash if a gem tag exists without a custom definition.

---

# 46. Datapack Overrides

Pack authors must be able to define:

- gemstone behavior;
- hardness;
- brittleness;
- allowed cuts;
- effect trait;
- color;
- cutting difficulty;
- visual profile.

---

# 47. Dedicated Mod Support

Create dedicated integrations only when the external mod has mechanics worth using.

Candidates may include popular mods that add:

- gemstones;
- metals;
- Curios slots;
- magic systems;
- equipment traits.

Dedicated compatibility should live under:

```text
integration.compat.<modid>
```

Never load external mod classes before confirming mod presence.

---

# 48. Immersive Enchanting Integration

Future integration should treat:

- gemstone traits as innate;
- enchantments as externally applied magic.

Finished jewelry should be enchantable if appropriate.

Potential compatibility:

- jewelry-specific enchantments;
- gemstone/enchantment synergy;
- enchantability based on metal and finish.

Do not make Immersive Enchanting a hard requirement unless explicitly chosen later.

---

# 49. Existing Enchantment Compatibility

Support modded enchantments through standard Minecraft/Forge mechanisms.

Do not maintain a hardcoded whitelist unless a specific incompatibility requires it.

---

# 50. Existing Jewelcraft Reforged Migration

Immersive Jewelry is a reworked successor.

Preserve world compatibility wherever feasible.

## 50.1 Mod ID

Strongly consider keeping the existing Jewelcraft Reforged mod ID if doing so avoids breaking existing worlds.

If a new mod ID is necessary, implement migration logic.

The coding agent must inspect the existing Jewelcraft Reforged registry IDs before deciding.

---

## 50.2 Legacy Item Migration

Legacy jewelry should be converted into the new component model.

Example:

```text
legacy ruby ring
    ↓
new ring item
metal = gold
gem = ruby
legacy_converted = true
```

Assign sensible default craftsmanship.

Recommended:

```text
smithing_quality = 0.65
cut_quality = 0.65
setting_quality = 0.65
```

or another value appropriate to balance.

---

## 50.3 DataFix / Conversion

Implement conversion through the safest available Forge 1.20.1 mechanism.

Possible approaches:

- missing mapping remap;
- inventory load conversion;
- capability/NBT migration;
- dedicated compatibility item that self-converts.

Do not silently delete old equipment.

---

# 51. Configuration

Provide server/common configuration for gameplay-affecting settings.

Recommended config:

```text
enable_immersive_smithing_integration
require_immersive_smithing
enable_fallback_blank_recipes

gem_cutting_difficulty_multiplier
stone_setting_difficulty_multiplier

catastrophic_gem_failure
gem_failure_severity

jewelry_power_multiplier

enable_jewelry_wear
enable_gem_damage

max_ring_slots
max_amulet_slots
max_bracelet_slots
max_circlet_slots

show_numeric_quality
enable_villager_jeweler
enable_legacy_conversion
```

Client config:

```text
minigame_screen_shake
particle_density
gem_sparkle_intensity
show_advanced_tooltips
enable_workstation_animations
```

---

# 52. Accessibility

Skill interactions must be accessible.

Include:

- reduced motion option;
- lower screen shake;
- scalable GUI;
- color-independent indicators;
- difficulty multiplier;
- optional larger target zones;
- optional slower minigame speed.

Do not encode success only through color.

Use:

- shape;
- icon;
- sound;
- text;
- motion;
- color together.

---

# 53. Audio Design

Jewelry crafting should have a distinct audio identity.

Lapidary:

- wheel spin;
- abrasive grinding;
- subtle pitch change under pressure;
- gemstone chip sound.

Jeweler's Bench:

- light metallic clicks;
- plier compression;
- stone seating click;
- polishing sound.

Avoid using heavy blacksmith hammer sounds for precision jewelry work.

---

# 54. Particles

Recommended:

- tiny colored gem dust;
- small metal spark;
- polishing shimmer;
- subtle sparkle on exceptional cuts.

Keep particle counts modest.

---

# 55. Recipes

Use conventional recipes only where they do not undermine the immersive process.

Appropriate:

- Jeweler's Bench;
- Lapidary Wheel;
- Jeweler's Pliers;
- basic consumables.

Not appropriate when full integration is enabled:

- direct crafting of finished jewelry;
- direct crafting of final cut gemstones;
- crafting table conversion from ingot + gem to wearable jewelry.

---

# 56. Recipe Data

Jewelry designs should be data-driven.

Example:

```json
{
  "type": "immersive_jewelry:jewelry_design",
  "form": "immersive_jewelry:ring",
  "required_blank": "immersive_jewelry:ring_blank",
  "gem_slots": 1,
  "allowed_gems": "#immersive_jewelry:usable_gems",
  "allowed_finishes": [
    "immersive_jewelry:polished",
    "immersive_jewelry:burnished",
    "immersive_jewelry:antique"
  ]
}
```

---

# 57. JEI Integration

Support JEI.

JEI should explain processes without bypassing them.

Recommended categories:

- jewelry blank smithing link;
- gemstone cutting;
- jewelry assembly;
- finishing;
- reworking.

For Immersive Smithing operations, link to or reuse its JEI category instead of duplicating all details.

---

# 58. Advancement / Guide Flow

Recommended progression:

```text
Acquire a Rough Gem
↓
Craft a Lapidary Wheel
↓
Cut Your First Gem
↓
Forge a Jewelry Blank
↓
Craft a Jeweler's Bench
↓
Set Your First Gem
↓
Create Fine Jewelry
↓
Create a Masterwork Piece
```

Optional advancement for recovering a stone from an existing item.

---

# 59. In-Game Guidance

The mod should teach itself.

Options:

- advancement descriptions;
- JEI;
- tooltips;
- contextual hints;
- optional guidebook integration.

Avoid requiring an external wiki for basic crafting.

---

# 60. Testing Strategy

Automated tests should cover as much deterministic logic as practical.

Unit-test:

- craftsmanship calculations;
- data parsing;
- gem definitions;
- cut definitions;
- effect resolution;
- migration mapping;
- serialization;
- compatibility fallbacks.

GameTests should cover:

- workstation interaction;
- item conversion;
- block entity state;
- crafting flow;
- equipment behavior where feasible.

---

# 61. Multiplayer Requirements

The mod must work correctly on dedicated servers.

Test:

- two players using separate lapidary stations;
- session ownership;
- disconnect during minigame;
- reconnect after interrupted crafting;
- workstation destruction during session;
- item removal during GUI;
- packet replay;
- inventory full on completion.

No client-only class should be referenced from common/server initialization.

---

# 62. Save Safety

Minigame sessions must never duplicate or delete valuable materials because of:

- server restart;
- client crash;
- disconnect;
- dimension change;
- block break;
- GUI close;
- packet loss.

Recommended behavior:

- ingredients remain server-owned during session;
- cancellation returns ingredients;
- only successful completion mutates them;
- committed changes use atomic validation.

---

# 63. Exploit Prevention

Prevent:

- duplicate gems via GUI closing;
- repeated completion packets;
- swapping target item mid-session;
- negative size values;
- quality above allowed range;
- invalid material IDs;
- applying incompatible cuts;
- applying multiple finishes when not allowed.

Clamp and validate all serialized numeric data.

---

# 64. Logging

Use useful debug logging behind a config or development flag.

Log:

- failed datapack definitions;
- legacy conversion;
- integration activation;
- unknown material fallback;
- invalid jewelry data repair.

Avoid noisy per-tick logs.

---

# 65. Development Milestones

## Milestone 1 — Foundation

Implement:

- project rename/restructure;
- registries;
- config;
- data loader framework;
- base jewelry data model;
- base gem data model;
- blank/frame data model;
- safe optional-mod integration layer.

Acceptance:

- game starts on client and dedicated server;
- no old feature crashes;
- jewelry data serializes and deserializes.

---

## Milestone 2 — Immersive Smithing Integration

Implement:

- jewelry workpiece forms;
- metal material passthrough;
- smithing quality transfer;
- small-workpiece handling;
- fallback blank recipes.

Acceptance:

- player can produce all four blank/frame forms;
- final blank remembers material and quality;
- no duplicate metalworking mechanic exists.

---

## Milestone 3 — Lapidary System

Implement:

- Lapidary Wheel block;
- block entity;
- animation;
- gem definitions;
- cuts;
- cutting GUI/minigame;
- server validation;
- quality outcomes.

Acceptance:

- rough gem becomes cut gem;
- quality depends on performance;
- failure degrades instead of usually deleting gem;
- works on dedicated server.

---

## Milestone 4 — Jeweler's Bench

Implement:

- bench block;
- visible workpiece;
- assembly menu;
- stone setting minigame;
- setting quality;
- completed jewelry creation.

Acceptance:

- blank + cut gem can become finished jewelry;
- workmanship is preserved;
- item data is valid after save/reload.

---

## Milestone 5 — Finish System

Implement:

- finish definitions;
- finish selection;
- visual state;
- stat modifiers;
- tooltip.

Acceptance:

- all standard finishes work;
- datapacks can add new ones.

---

## Milestone 6 — Equipment and Effects

Implement:

- equipment slots;
- gem trait system;
- form expression system;
- metal modifiers;
- cached runtime effects.

Acceptance:

- same gem behaves differently across forms;
- effects cleanly stop after unequip;
- no meaningful server tick regression.

---

## Milestone 7 — Reworking

Implement:

- stone removal;
- repair;
- recutting;
- integrity;
- gem damage.

Acceptance:

- valuable gems can be recovered;
- repeated reworking has sensible consequences;
- no duplication exploits.

---

## Milestone 8 — Jeweler Villager

Implement:

- POI;
- profession;
- trades;
- progression.

Acceptance:

- villagers claim bench;
- trades are balanced;
- trades do not bypass entire gameplay loop.

---

## Milestone 9 — Compatibility

Implement:

- generic gem tag support;
- Curios integration;
- JEI;
- common modded gems;
- common modded metals through Immersive Smithing.

Acceptance:

- unknown gem definitions fail gracefully;
- absent optional mods do not crash startup;
- JEI explains all major processes.

---

## Milestone 10 — Migration

Implement:

- legacy Jewelcraft Reforged conversion;
- missing mapping handling;
- saved-world testing.

Acceptance:

- test world containing old jewelry loads without item loss;
- old items become usable new-format pieces.

---

## Milestone 11 — Polish

Implement:

- final sounds;
- particles;
- dynamic rendering;
- accessibility;
- refined minigame UX;
- advancements;
- localization.

Acceptance:

- complete loop is understandable without source-code knowledge;
- no placeholder assets remain;
- dedicated-server tests pass.

---

# 66. Minimum Viable Release

The first public release should include:

- Ring;
- Amulet;
- Bracelet;
- Circlet;
- Jeweler's Bench;
- Lapidary Wheel;
- Jeweler profession;
- at least four gemstone types;
- Round;
- Brilliant;
- Emerald;
- Cabochon;
- at least three finishes;
- Immersive Smithing integration;
- fallback recipe support;
- Curios;
- JEI;
- datapack definitions;
- migration from prior Jewelcraft Reforged items where possible.

---

# 67. Out of Scope for Initial Release

Do not prioritize:

- dozens of jewelry slots;
- procedurally generated gem geometry;
- complex geology;
- gemstone mining overhaul;
- real-world chemistry;
- detailed metallurgy duplicated from Immersive Smithing;
- multiplayer trading economy;
- NPC crafting simulation;
- dozens of minigames;
- jewelry transmog system;
- full cosmetic body rendering system beyond normal equipment rendering.

---

# 68. Code Quality Requirements

The implementation should:

- use Java 17;
- follow Forge 1.20.1 conventions;
- avoid mixins unless clearly justified;
- avoid reflection when a normal API exists;
- isolate optional integrations;
- use immutable data records where appropriate;
- avoid raw NBT manipulation outside dedicated data classes;
- include clear Javadocs on public APIs;
- separate client and common code;
- avoid giant manager classes;
- keep effect logic testable;
- use ResourceLocation identifiers consistently.

---

# 69. Public API Goals

Expose enough API for other mods to:

- register or define gemstone traits;
- inspect jewelry composition;
- query craftsmanship;
- respond to jewelry equip/unequip;
- add jewelry forms;
- add finishes;
- add cuts;
- add material modifiers;
- add compatibility with custom equipment systems.

Do not expose unstable internal implementation unnecessarily.

---

# 70. Suggested Events

Potential Forge events or custom events:

```text
JewelryCraftedEvent
GemCutEvent
GemSetEvent
JewelryEquippedEvent
JewelryUnequippedEvent
JewelryReworkedEvent
JewelryBrokenEvent
```

Only add events that have realistic integration value.

---

# 71. Developer Debug Commands

Recommended commands:

```text
/immersivejewelry give
/immersivejewelry inspect
/immersivejewelry setquality
/immersivejewelry listgems
/immersivejewelry listcuts
/immersivejewelry reloaddebug
```

Debug commands should require appropriate permission levels.

---

# 72. Example Full Item Resolution

Suppose the player creates:

```text
Gold
+
Ring
+
Ruby
+
Brilliant Cut
+
Polished Finish
```

with:

```text
Smithing Quality: 0.88
Cut Quality:      0.94
Setting Quality:  0.90
```

Base workmanship:

```text
0.88 × 0.35
+ 0.94 × 0.35
+ 0.90 × 0.30
= 0.907
```

The mod resolves:

```text
Gem Trait:
Ruby → Vitality / Heat

Form Expression:
Ring → Offensive / Precision

Metal Modifier:
Gold → Higher magical amplification

Cut Modifier:
Brilliant → Increased potency

Finish:
Polished → Increased gem expression

Craftsmanship:
0.907 → Exceptional
```

The final effect should then be derived from those definitions rather than from a hardcoded `gold_ruby_ring` item.

---

# 73. Example Datapack Extension

A third-party mod adds:

```text
examplemod:moonstone
```

The pack author creates:

```text
data/examplemod/immersive_jewelry/gems/moonstone.json
```

Example:

```json
{
  "ingredient": {
    "item": "examplemod:moonstone"
  },
  "hardness": 6.0,
  "brittleness": 0.5,
  "default_trait": "examplemod:lunar",
  "base_color": "#C6D8FF",
  "cutting": {
    "difficulty": 0.55,
    "size_retention": 0.9
  },
  "supported_cuts": [
    "immersive_jewelry:cabochon",
    "immersive_jewelry:round"
  ]
}
```

No Java integration should be required for basic support.

---

# 74. User Experience Principles

At every stage, ask:

> Does this interaction give the player a meaningful choice or skill expression?

If not, simplify it.

The player should remember:

- the gem they found;
- the cut they chose;
- whether they cut it well;
- what metal they used;
- how well they assembled it;
- what the final jewelry actually does.

They should not remember:

- waiting through progress bars;
- crafting several meaningless intermediates;
- looking up obscure recipes;
- repeating identical motions without consequence.

---

# 75. Final Product Vision

The desired experience is:

> Smith the frame. Study the stone. Cut it carefully. Set it by hand. Finish the piece. Wear something that reflects what you actually made.

Immersive Jewelry should feel connected to the rest of the Immersive series without merely attaching a minigame to every recipe.

Each mod should own a distinct craft:

```text
Immersive Smithing
    → shaping metal

Immersive Jewelry
    → lapidary work, jewelry assembly, and wearable material expression

Immersive Enchanting
    → applying supernatural enchantments

Immersive Tanning
    → transforming hides and leather
```

Together they should create a coherent crafting ecosystem in which the finished equipment tells the story of how it was made.

---

# 76. Acceptance Criteria for 1.0

The mod is ready for a stable 1.0 when all of the following are true:

- [ ] Forge 1.20.1 client launches successfully.
- [ ] Dedicated server launches successfully.
- [ ] All four jewelry forms can be created.
- [ ] Immersive Smithing produces jewelry blanks/frames.
- [ ] Metal quality transfers into the final jewelry.
- [ ] Rough gemstones can be cut.
- [ ] Cutting performance affects cut quality.
- [ ] Poor cutting normally degrades instead of deleting the gemstone.
- [ ] Gemstones can be set into jewelry through an interactive process.
- [ ] Setting performance affects workmanship.
- [ ] Finishes are selectable and data-driven.
- [ ] Final jewelry stores metal, gem, cut, finish, and craftsmanship.
- [ ] Dynamic effects resolve from components rather than unique hardcoded final items.
- [ ] Same gemstone can behave differently across jewelry forms.
- [ ] At least basic modded gemstone auto-detection works.
- [ ] Datapacks can define a new gemstone without Java code.
- [ ] Curios integration works.
- [ ] JEI integration works.
- [ ] Jeweler villager profession works.
- [ ] Jewelry can be repaired/reworked.
- [ ] Gemstones can be removed without duplication exploits.
- [ ] Optional integrations do not crash when their mods are absent.
- [ ] Existing Jewelcraft Reforged worlds have a documented migration path.
- [ ] Multiplayer crafting sessions are server-authoritative.
- [ ] Item data survives save/reload.
- [ ] No significant per-tick performance issue appears with many players wearing jewelry.
- [ ] Accessibility options exist for the minigames.
- [ ] All public gameplay text is localizable.
- [ ] No placeholder textures, sounds, or debug UI remain.

---

# 77. Coding-Agent Instruction

Treat this document as the design authority for Immersive Jewelry.

Before implementing large systems:

1. inspect the existing Jewelcraft Reforged repository;
2. inspect the current Immersive Smithing APIs and data structures;
3. preserve compatible registry IDs where doing so prevents world breakage;
4. prefer extending Immersive Smithing over reimplementing its metalworking logic;
5. keep optional integrations isolated;
6. implement the project milestone-by-milestone;
7. add tests alongside systems rather than after the entire rewrite;
8. preserve server authority for every action that modifies valuable items;
9. keep definitions data-driven whenever possible;
10. document any architectural deviation from this specification in the repository before proceeding.

When a requested feature conflicts with the central design principles, prioritize:

```text
Meaningful interaction
> compatibility
> extensibility
> clarity
> immersion
> visual polish
> quantity of content
```

Do not increase complexity merely to make the process more realistic.

The finished mod should be easy to understand, difficult to master, broadly compatible, safe for long-running modded worlds, and distinctly part of the Immersive crafting series.
