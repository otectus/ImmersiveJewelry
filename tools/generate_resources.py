#!/usr/bin/env python3
"""Generates the repetitive resources of Immersive Jewelry from the small tables below.

  python3 tools/generate_resources.py            # --check (default): report files that differ, write nothing
  python3 tools/generate_resources.py --write    # write every generated file

Generated: item models, blockstates, fallback blank recipes, Curios slots and tags, item/block/POI tags, block loot
tables, treasure loot modifiers, Immersive Smithing patterns, advancements, particle definitions, sounds.json,
and the inventory wheel composed from its frame and disc models.
Hand-written (not touched here): definitions (immersive_jewelry/*), designs, crafting recipes, other block models,
held tool geometry, lang.
"""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / 'src/main/resources'
ASSETS = ROOT / 'assets/immersive_jewelry'
DATA = ROOT / 'data'
MOD = 'immersive_jewelry'

FORMS = [('ring', 'ring_blank'), ('amulet', 'amulet_frame'), ('bracelet', 'bracelet_blank'), ('circlet', 'circlet_frame')]
CUTS = ['round', 'brilliant', 'emerald', 'cabochon']  # model index 1..4 (CutDefinition#modelIndex)
SLOTS = {'ring': 2, 'necklace': 1, 'bracelet': 1, 'head': 1}
FORM_SLOT = {'ring': 'ring', 'amulet': 'necklace', 'bracelet': 'bracelet', 'circlet': 'head'}

# Fallback blank recipes (no Immersive Smithing): nugget patterns for metals with nuggets, ingot patterns for copper.
NUGGET_METALS = ['iron', 'gold', 'silver', 'tin', 'lead', 'nickel', 'steel', 'bronze', 'electrum', 'platinum', 'aluminum',
                 'invar', 'constantan', 'rose_gold', 'netherite']
NUGGET_PATTERNS = {
    'ring_blank': ([' N ', 'N N', ' N '], {'N': 'nugget'}, 1),
    'amulet_frame': (['N N', 'N N', ' N '], {'N': 'nugget'}, 1),
    'bracelet_blank': (['NNN', 'N N', 'NNN'], {'N': 'nugget'}, 1),
    'circlet_frame': (['NIN', 'N N'], {'N': 'nugget', 'I': 'ingot'}, 1),
}
INGOT_PATTERNS = {
    'ring_blank': ([' I ', 'I I'], {'I': 'ingot'}, 3),
    'amulet_frame': (['I I', ' I '], {'I': 'ingot'}, 2),
    'bracelet_blank': (['III'], {'I': 'ingot'}, 2),
    'circlet_frame': (['I I', 'III'], {'I': 'ingot'}, 2),
}
FAMILY = {'iron': 'minecraft:iron', 'gold': 'minecraft:gold', 'copper': 'minecraft:copper', 'netherite': 'minecraft:netherite'}

SOUNDS = ['block.lapidary_wheel.mount', 'block.lapidary_wheel.spin', 'block.lapidary_wheel.grind', 'block.lapidary_wheel.chip',
          'block.lapidary_wheel.facet', 'block.lapidary_wheel.finish', 'block.lapidary_wheel.lap', 'block.jewelers_bench.place',
          'block.jewelers_bench.prong', 'block.jewelers_bench.seat', 'block.jewelers_bench.bend', 'block.jewelers_bench.polish',
          'block.jewelers_bench.finish', 'block.jewelers_bench.repair', 'item.jewelry.equip', 'item.design_sketch.learn',
          'jewelry.effect.trigger', 'jewelry.effect.ward', 'jewelry.effect.sense', 'ui.cue', 'ui.fail', 'entity.villager.work_jeweler']
SOUND_VARIANTS = {'block.lapidary_wheel.grind': 3, 'block.lapidary_wheel.chip': 2, 'block.jewelers_bench.prong': 3,
                  'entity.villager.work_jeweler': 2, 'jewelry.effect.trigger': 2}

LOOT = [  # (name, tables, gem chance, max gems, sketch chance, minimum rarity)
    ('dungeons', ['chests/simple_dungeon', 'chests/abandoned_mineshaft', 'chests/pillager_outpost'], 0.3, 2, 0.03, 0),
    ('temples', ['chests/desert_pyramid', 'chests/jungle_temple', 'chests/woodland_mansion'], 0.4, 2, 0.06, 0),
    ('treasure', ['chests/buried_treasure', 'chests/shipwreck_treasure'], 0.6, 3, 0.05, 0),
    ('strongholds', ['chests/stronghold_corridor', 'chests/stronghold_crossing', 'chests/stronghold_library'], 0.3, 2, 0.25, 1),
    ('deep', ['chests/ancient_city', 'chests/bastion_treasure', 'chests/end_city_treasure'], 0.5, 2, 0.2, 2),
]

ICON_RING = ('{immersive_jewelry:{Version:1,Form:"immersive_jewelry:ring",Metal:"minecraft:gold",MetalColor:15910209,'
             'Gems:[{Gem:"immersive_jewelry:ruby",Size:1.0f,OriginalSize:1.2f,Natural:0.8f,Inclusions:0.1f,'
             'Cut:"immersive_jewelry:brilliant",CutQuality:0.9f}],Finish:"immersive_jewelry:polished",SmithingQuality:0.9f,'
             'SettingQuality:0.9f,Workmanship:0.9f,Integrity:1.0f}}')
ICON_CUT = ('{immersive_jewelry:{Version:1,Gem:"immersive_jewelry:sapphire",Size:0.8f,OriginalSize:1.0f,Natural:0.7f,'
            'Inclusions:0.1f,Cut:"immersive_jewelry:brilliant",CutQuality:0.95f}}')


def files():
    out = {}

    def put(path, obj):
        out[path] = obj

    # ---------------------------------------------------------------------------------------- item models
    for form, blank in FORMS:
        frame = {'layer0': f'{MOD}:item/{form}/frame', 'layer1': f'{MOD}:item/{form}/recess', 'layer2': f'{MOD}:item/{form}/highlight'}
        overrides = [{'predicate': {f'{MOD}:cut': (i + 1) / 10}, 'model': f'{MOD}:item/{form}_{cut}'} for i, cut in enumerate(CUTS)]
        put(ASSETS / f'models/item/{form}.json', {'parent': 'minecraft:item/generated', 'textures': frame, 'overrides': overrides})
        for cut in CUTS:
            textures = dict(frame)
            textures['layer3'] = f'{MOD}:item/{form}/gem_{cut}'
            textures['layer4'] = f'{MOD}:item/{form}/sparkle_{cut}'
            put(ASSETS / f'models/item/{form}_{cut}.json', {'parent': 'minecraft:item/generated', 'textures': textures})
        put(ASSETS / f'models/item/{blank}.json', {'parent': 'minecraft:item/generated',
                                                   'textures': {'layer0': f'{MOD}:item/blank/{blank}', 'layer1': f'{MOD}:item/blank/{blank}_highlight'}})
    put(ASSETS / 'models/item/rough_gem.json', {'parent': 'minecraft:item/generated',
                                                'textures': {'layer0': f'{MOD}:item/gem/rough', 'layer1': f'{MOD}:item/gem/rough_sheen'}})
    put(ASSETS / 'models/item/cut_gem.json', {'parent': 'minecraft:item/generated',
                                              'textures': {'layer0': f'{MOD}:item/gem/cut_round', 'layer1': f'{MOD}:item/gem/cut_round_sparkle'},
                                              'overrides': [{'predicate': {f'{MOD}:cut': (i + 1) / 10}, 'model': f'{MOD}:item/cut_gem_{cut}'}
                                                            for i, cut in enumerate(CUTS)]})
    for cut in CUTS:
        put(ASSETS / f'models/item/cut_gem_{cut}.json', {'parent': 'minecraft:item/generated',
                                                         'textures': {'layer0': f'{MOD}:item/gem/cut_{cut}', 'layer1': f'{MOD}:item/gem/cut_{cut}_sparkle'}})
    for item in ['emery_lap', 'diamond_lap', 'polishing_compound', 'gem_dust', 'design_sketch', 'jewelers_handbook']:
        put(ASSETS / f'models/item/{item}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MOD}:item/{item}'}})
    for item in ['jewelers_pliers', 'fine_pliers']:
        put(ASSETS / f'models/item/{item}.json', {
            'loader': 'forge:separate_transforms', 'gui_light': 'front',
            'textures': {'particle': f'{MOD}:item/{item}'},
            'base': {'parent': f'{MOD}:item/{item}_held'},
            'perspectives': {'gui': {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MOD}:item/{item}'}}}})
    # The inventory wheel uses the same authored geometry as the stationary frame and animated disc.
    # Compose it here so edits to either model cannot leave a stale block-item silhouette.
    wheel = json.loads((ASSETS / 'models/block/lapidary_wheel.json').read_text())
    disc = json.loads((ASSETS / 'models/block/lapidary_wheel_disc.json').read_text())
    wheel['textures'].update({key: value for key, value in disc['textures'].items() if key != 'particle'})
    wheel['elements'].extend(disc['elements'])
    put(ASSETS / 'models/block/lapidary_wheel_full.json', wheel)
    for block in ['lapidary_wheel', 'jewelers_bench']:
        # The wheel's disc spins in the world (a separate model); the item shows it in place.
        put(ASSETS / f'models/item/{block}.json', {'parent': f'{MOD}:block/{block}' + ('_full' if block == 'lapidary_wheel' else '')})
        put(ASSETS / f'blockstates/{block}.json', {'variants': {
            f'facing={d}': ({'model': f'{MOD}:block/{block}'} | ({'y': y} if y else {}))
            for d, y in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]}})

    # ---------------------------------------------------------------------------------------- fallback blank recipes
    def fallback(metal, blank, pattern, keys, count, unit_condition):
        key = {}
        for ch, kind in keys.items():
            key[ch] = {'tag': f'forge:{kind}s/{metal}'}
        family = FAMILY.get(metal, f'forge:{metal}')
        conditions = [{'type': f'{MOD}:fallback_blanks'}, unit_condition]
        return {'type': 'minecraft:crafting_shaped', 'conditions': conditions, 'category': 'misc', 'pattern': pattern, 'key': key,
                'result': {'item': f'{MOD}:{blank}', 'count': count, 'nbt': f'{{{MOD}:{{Metal:"{family}",Fallback:1b}}}}'}}

    for metal in NUGGET_METALS:
        cond = {'type': 'forge:not', 'value': {'type': 'forge:tag_empty', 'tag': f'forge:nuggets/{metal}'}}
        for blank, (pattern, keys, count) in NUGGET_PATTERNS.items():
            put(DATA / f'{MOD}/recipes/fallback/{blank}_{metal}.json', fallback(metal, blank, pattern, keys, count, cond))
    cond = {'type': 'forge:not', 'value': {'type': 'forge:tag_empty', 'tag': 'forge:ingots/copper'}}
    for blank, (pattern, keys, count) in INGOT_PATTERNS.items():
        put(DATA / f'{MOD}/recipes/fallback/{blank}_copper.json', fallback('copper', blank, pattern, keys, count, cond))

    # ---------------------------------------------------------------------------------------- Immersive Smithing blank recipes
    # One per defined metal and form, so they are real recipes (synced, in JEI). Other families come from the Java provider.
    forms = {f.stem: json.loads(f.read_text()) for f in sorted((DATA / MOD / MOD / 'jewelry_forms').glob('*.json'))}
    for ns_dir in sorted(p for p in DATA.iterdir() if (p / MOD / 'metals').is_dir()):
        for metal_file in sorted((ns_dir / MOD / 'metals').glob('*.json')):
            family = f'{ns_dir.name}:{metal_file.stem}'
            metal = json.loads(metal_file.read_text())
            color = int(metal['color'].lstrip('#'), 16)
            name = json.dumps({'translate': metal['name']}).replace('"', '\\"')
            for form_name, form in forms.items():
                blank = form['blank'].split(':')[1]
                conditions = [{'type': f'{MOD}:smithing_blanks'},
                              {'type': 'forge:not', 'value': {'type': 'forge:tag_empty', 'tag': f'forge:ingots/{metal_file.stem}'}}]
                put(DATA / f'{MOD}/recipes/smithing/{form_name}/{ns_dir.name}/{metal_file.stem}.json', {
                    'type': 'immersive_smithing:smithing', 'conditions': conditions, 'material': family, 'metal_units': form['metal_units'],
                    'result': {'item': form['blank'], 'nbt': f'{{{MOD}:{{Metal:"{family}",MetalColor:{color},MetalName:"{name}"}}}}'},
                    'forge_pattern': f'{MOD}:small_workpiece', 'anvil_pattern': form['anvil_pattern']})

    # ---------------------------------------------------------------------------------------- Curios
    for slot, size in SLOTS.items():
        put(DATA / f'{MOD}/curios/slots/{slot}.json', {'size': size, 'operation': 'SET', 'replace': False})
    put(DATA / f'{MOD}/curios/entities/player.json', {'entities': ['minecraft:player'], 'slots': list(SLOTS), 'replace': False})
    curios = {'ring': ['ring'], 'necklace': ['amulet'], 'charm': ['amulet'], 'bracelet': ['bracelet'], 'head': ['circlet']}
    for slot, items in curios.items():
        put(DATA / f'curios/tags/items/{slot}.json', {'replace': False, 'values': [f'{MOD}:{i}' for i in items]})

    # ---------------------------------------------------------------------------------------- tags
    tags = DATA / f'{MOD}/tags/items'
    put(tags / 'pliers.json', {'replace': False, 'values': [f'{MOD}:jewelers_pliers', f'{MOD}:fine_pliers']})
    put(tags / 'laps.json', {'replace': False, 'values': [f'{MOD}:emery_lap', f'{MOD}:diamond_lap']})
    # Abrasive dusts that make Polishing Compound; other mods' gem dusts (Apotheosis) join through data.
    put(tags / 'polishing_dusts.json', {'replace': False, 'values': [f'{MOD}:gem_dust']})
    put(tags / 'quickdraw.json', {'replace': False, 'values': ['minecraft:bow', 'minecraft:crossbow', 'minecraft:trident']})
    put(tags / 'jewelry.json', {'replace': False, 'values': [f'{MOD}:{f}' for f, _ in FORMS]})
    put(tags / 'blanks.json', {'replace': False, 'values': [f'{MOD}:{b}' for _, b in FORMS]})
    gems = ['diamond', 'emerald', 'amethyst', 'lapis', 'quartz', 'ruby', 'sapphire', 'topaz', 'onyx']
    put(tags / 'gem_sources.json', {'replace': False, 'values': [f'{MOD}:rough_gem'] + [{'id': f'#forge:gems/{g}', 'required': False} for g in gems]})
    put(DATA / 'minecraft/tags/blocks/mineable/axe.json', {'replace': False, 'values': [f'{MOD}:lapidary_wheel', f'{MOD}:jewelers_bench']})
    put(DATA / 'minecraft/tags/point_of_interest_type/acquirable_job_site.json', {'replace': False, 'values': [f'{MOD}:jewelers_bench']})

    # ---------------------------------------------------------------------------------------- loot
    for block in ['lapidary_wheel', 'jewelers_bench']:
        put(DATA / f'{MOD}/loot_tables/blocks/{block}.json', {'type': 'minecraft:block', 'pools': [{
            'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{MOD}:{block}'}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}]}], 'random_sequence': f'{MOD}:blocks/{block}'})
    entries = []
    for name, tables, chance, count, sketch, rarity in LOOT:
        terms = [{'condition': 'forge:loot_table_id', 'loot_table_id': f'minecraft:{t}'} for t in tables]
        put(DATA / f'{MOD}/loot_modifiers/treasure_gems_{name}.json', {'type': f'{MOD}:treasure_gems',
            'conditions': [{'condition': 'minecraft:any_of', 'terms': terms}], 'gem_chance': chance, 'max_gems': count,
            'sketch_chance': sketch, 'min_rarity': rarity})
        entries.append(f'{MOD}:treasure_gems_{name}')
    put(DATA / 'forge/loot_modifiers/global_loot_modifiers.json', {'replace': False, 'entries': entries})

    # ---------------------------------------------------------------------------------------- Immersive Smithing patterns
    smith = DATA / f'{MOD}/immersive_smithing'
    put(smith / 'forge_patterns/small_workpiece.json', {'base_phases': 2, 'units_per_extra_phase': 12, 'min_phases': 2, 'max_phases': 3,
                                                        'zone_half_width': 0.065, 'min_speed': 0.5, 'max_speed': 0.85, 'acceleration': 0.35,
                                                        'transition_ms': 400})
    anvil = {
        'ring_blank': [[0.5, 0.3], [0.7, 0.5], [0.5, 0.7], [0.3, 0.5]],
        'amulet_frame': [[0.5, 0.25], [0.68, 0.45], [0.5, 0.72], [0.32, 0.45], [0.5, 0.5]],
        'bracelet_blank': [[0.25, 0.5], [0.4, 0.35], [0.6, 0.35], [0.75, 0.5], [0.5, 0.62]],
        'circlet_frame': [[0.2, 0.55], [0.35, 0.4], [0.5, 0.35], [0.65, 0.4], [0.8, 0.55]],
    }
    for name, targets in anvil.items():
        put(smith / f'anvil_patterns/{name}.json', {'targets': targets, 'strikes': len(targets), 'target_radius': 0.055,
                                                    'target_lifetime': 1.5, 'ideal_fraction': 0.6, 'gap': 0.22, 'jitter': 0.02,
                                                    'time_multiplier': 0.9})

    # ---------------------------------------------------------------------------------------- advancements
    adv = DATA / f'{MOD}/advancements'

    def advancement(name, parent, icon, criteria, frame='task', nbt=None, requirements=None, background=None, hidden=False):
        display = {'icon': {'item': icon} | ({'nbt': nbt} if nbt else {}), 'title': {'translate': f'advancements.{MOD}.{name}.title'},
                   'description': {'translate': f'advancements.{MOD}.{name}.description'}, 'frame': frame,
                   'show_toast': True, 'announce_to_chat': frame != 'task', 'hidden': hidden}
        if background:
            display['background'] = background
        obj = {'display': display, 'criteria': criteria}
        if parent:
            obj = {'parent': f'{MOD}:{parent}'} | obj
        if requirements:
            obj['requirements'] = requirements
        put(adv / f'{name}.json', obj)

    impossible = {'code': {'trigger': 'minecraft:impossible'}}
    has = lambda **kw: {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [kw]}}
    advancement('root', None, f'{MOD}:rough_gem', {'gem': has(tag=f'{MOD}:gem_sources')}, background='minecraft:textures/block/amethyst_block.png')
    advancement('lapidary', 'root', f'{MOD}:lapidary_wheel', {'wheel': has(items=[f'{MOD}:lapidary_wheel'])})
    advancement('first_facet', 'lapidary', f'{MOD}:cut_gem', impossible, nbt=ICON_CUT)
    advancement('flawless', 'first_facet', f'{MOD}:cut_gem', impossible, frame='challenge', nbt=ICON_CUT)
    advancement('forge_blank', 'root', f'{MOD}:ring_blank', {'blank': has(tag=f'{MOD}:blanks')})
    advancement('bench', 'forge_blank', f'{MOD}:jewelers_bench', {'bench': has(items=[f'{MOD}:jewelers_bench'])})
    advancement('set_in_stone', 'bench', f'{MOD}:ring', impossible, nbt=ICON_RING)
    advancement('fine_jewelry', 'set_in_stone', f'{MOD}:amulet', impossible, frame='goal')
    advancement('masterwork', 'fine_jewelry', f'{MOD}:circlet', impossible, frame='challenge')
    advancement('recovered_stone', 'set_in_stone', f'{MOD}:jewelers_pliers', impossible)
    advancement('new_design', 'set_in_stone', f'{MOD}:design_sketch', impossible)
    advancement('good_as_new', 'set_in_stone', f'{MOD}:polishing_compound', impossible)

    # ---------------------------------------------------------------------------------------- particles and sounds
    put(ASSETS / 'particles/gem_dust.json', {'textures': [f'{MOD}:gem_dust_{i}' for i in range(4)]})
    put(ASSETS / 'particles/gem_sparkle.json', {'textures': [f'{MOD}:gem_sparkle_{i}' for i in range(4)]})
    put(ASSETS / 'particles/metal_spark.json', {'textures': [f'{MOD}:metal_spark_{i}' for i in range(2)]})
    sounds = {}
    for name in SOUNDS:
        n = SOUND_VARIANTS.get(name, 1)
        base = name.replace('.', '/')
        sounds[name] = {'subtitle': f'subtitles.{MOD}.{name}',
                        'sounds': [f'{MOD}:{base}' + (f'_{i + 1}' if n > 1 else '') for i in range(n)]}
    put(ASSETS / 'sounds.json', sounds)
    return out


def main():
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--write', action='store_true')
    group.add_argument('--check', action='store_true')
    args = parser.parse_args()
    generated = {p: json.dumps(obj, indent=2) + '\n' for p, obj in files().items()}
    if args.write:
        for path, text in generated.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
        print(f'wrote {len(generated)} files')
        return 0
    differing = [str(p.relative_to(ROOT)) for p, text in generated.items() if not p.exists() or p.read_text() != text]
    for line in differing:
        print(f'differs {line}')
    print(f'{len(generated) - len(differing)}/{len(generated)} generated files up to date')
    return 1 if differing else 0


if __name__ == '__main__':
    sys.exit(main())
