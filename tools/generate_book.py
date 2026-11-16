#!/usr/bin/env python3
"""Exports or verifies the Jeweler's Handbook (Patchouli 1.20.1-85, i18n book).

  python3 tools/generate_book.py            # --check (default): compare exports and lang coverage, write nothing
  python3 tools/generate_book.py --write    # export book.json, categories, entries and templates

The book's structure lives here (OUTLINE); every word lives in the lang files (all strings are translation keys, the book
sets "i18n": true). The fourteen chapters of the 1.1/1.2 handbook are kept as-is: their pages are the lang keys
handbook.immersive_jewelry.<chapter>.<n>, found in en_us.json. --check also verifies that every key the book uses exists in
every shipped locale, that every lesson a Watch button names is a lesson of the mod, and that every entry the Java side
links to (HelpTopics.ENTRIES) exists.

Writes:
  data/immersive_jewelry/patchouli_books/jewelers_handbook/book.json
  assets/immersive_jewelry/patchouli_books/jewelers_handbook/en_us/{categories,entries,templates}/...
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / 'src/main/resources'
DATA = RES / 'data/immersive_jewelry/patchouli_books/jewelers_handbook'
ASSETS = RES / 'assets/immersive_jewelry/patchouli_books/jewelers_handbook/en_us'
LANG = RES / 'assets/immersive_jewelry/lang'
LOCALES = ['en_us', 'es_es', 'pt_br', 'ru_ru', 'zh_cn']
JAVA = ROOT / 'src/main/java/com/otectus/immersivejewelry/client/guide'
NS = 'immersive_jewelry'

BOOK = {
    'name': 'book.immersive_jewelry.title',
    'landing_text': 'book.immersive_jewelry.landing',
    'subtitle': 'book.immersive_jewelry.subtitle',
    'version': 1,
    'use_resource_pack': True,
    'i18n': True,
    'dont_generate_book': True,
    'custom_book_item': 'immersive_jewelry:jewelers_handbook',
    'model': 'immersive_jewelry:jewelers_handbook',
    'show_progress': False,
    'pause_game': False,
    'use_blocky_font': False,
    'book_texture': 'patchouli:textures/gui/book_brown.png',
    'text_color': '2B2118',
    'header_color': '4A3A1E',
    'nameplate_color': 'C9A45C',
    'creative_tab': 'none',
}

# category: (icon, sortnum)
CATEGORIES = {
    'start': ('immersive_jewelry:jewelers_handbook', 0),
    'stones': ('minecraft:diamond', 1),
    'wheel': ('immersive_jewelry:lapidary_wheel', 2),
    'metal': ('immersive_jewelry:ring_blank', 3),
    'designs': ('immersive_jewelry:design_sketch', 4),
    'setting': ('immersive_jewelry:jewelers_pliers', 5),
    'finishing': ('immersive_jewelry:polishing_compound', 6),
    'wearing': ('immersive_jewelry:ring', 7),
    'care': ('immersive_jewelry:fine_pliers', 8),
    'workshop': ('minecraft:emerald', 9),
    'trouble': ('minecraft:redstone_torch', 10),
}

GEMS = ['diamond', 'emerald', 'amethyst', 'lapis', 'quartz', 'ruby', 'sapphire', 'topaz', 'onyx']
CUTS = ['round', 'brilliant', 'emerald', 'cabochon']
FINISHES = ['polished', 'burnished', 'antique']
DESIGNS = ['solitaire_ring', 'bezel_ring', 'trilogy_ring', 'pendant_amulet', 'locket_amulet', 'halo_amulet', 'cuff_bracelet',
           'link_bracelet', 'simple_circlet', 'diadem']


def T(n):
    """The n-th text page of the entry being built (book.immersive_jewelry.<entry>.<n>)."""
    return ('text', n)


def M(chapter):
    """Every page of a 1.2 handbook chapter (handbook.immersive_jewelry.<chapter>.<n>)."""
    return ('migrated', chapter)


def C(card, **params):
    return ('card', card, params)


def W(lesson, n):
    """A Watch button for lesson, with text page n of the entry below it."""
    return ('watch', lesson, n)


# path: (category, icon, sortnum, name key or None for book.immersive_jewelry.entry.<path>, pages, extra recipe mappings)
OUTLINE = {
    'start/welcome': ('start', 'immersive_jewelry:jewelers_handbook', 0, 'handbook.immersive_jewelry.welcome.title',
                      [M('welcome'), T(0), W('first_piece', 1)]),
    'start/first_piece': ('start', 'immersive_jewelry:ring', 1, None, [T(0), T(1), T(2), T(3), W('first_piece', 4)]),
    'start/stations': ('start', 'immersive_jewelry:lapidary_wheel', 2, None, [T(0), C('tools_card'), W('laps_and_tools', 1)]),
    'start/controls': ('start', 'minecraft:oak_sign', 3, None, [T(0), C('keys_card'), T(1), T(2)]),
    'start/leaving': ('start', 'immersive_jewelry:unfinished_jewelry', 4, None, [T(0), T(1), T(2), W('portable_stone', 3)]),

    'stones/sources': ('stones', 'minecraft:emerald', 0, 'handbook.immersive_jewelry.stones.title',
                       [M('stones'), T(0), W('appraisal_and_sources', 1)]),
    'stones/appraisal': ('stones', 'immersive_jewelry:rough_gem', 1, None, [T(0), T(1), T(2), T(3)]),
    'stones/gems': ('stones', 'minecraft:amethyst_shard', 2, None,
                    [T(0)] + [C('gem_card', gem='immersive_jewelry:' + g) for g in GEMS] + [C('gem_card', gem='generic')]),

    'wheel/wheel': ('wheel', 'immersive_jewelry:lapidary_wheel', 0, 'handbook.immersive_jewelry.wheel.title',
                    [M('wheel'), W('round_cut', 0)]),
    'wheel/cutting': ('wheel', 'immersive_jewelry:cut_gem', 1, 'handbook.immersive_jewelry.cutting.title',
                      [M('cutting'), W('round_cut', 0), W('brilliant_cut', 1), W('emerald_cut', 2), W('cabochon_cut', 3)]),
    'wheel/cuts': ('wheel', 'immersive_jewelry:emery_lap', 2, 'handbook.immersive_jewelry.cuts.title',
                   [M('cuts')] + [C('cut_card', cut='immersive_jewelry:' + c) for c in CUTS]),
    'wheel/recutting': ('wheel', 'immersive_jewelry:gem_dust', 3, None, [T(0), T(1), T(2), W('cut_mistakes_and_recut', 3)]),

    'metal/blanks': ('metal', 'immersive_jewelry:ring_blank', 0, 'handbook.immersive_jewelry.blanks.title',
                     [M('blanks'), C('metal_card', show='routes'), W('blank_routes', 0)]),
    'metal/forms': ('metal', 'immersive_jewelry:amulet_frame', 1, None, [T(0), T(1), T(2)]),
    'metal/metals': ('metal', 'minecraft:gold_ingot', 2, None, [T(0), C('metal_card', show='metals'), T(1)]),

    'designs/folio': ('designs', 'immersive_jewelry:jewelers_bench', 0, None, [T(0), T(1), T(2), W('folio_and_sketch', 3)]),
    'designs/sketches': ('designs', 'immersive_jewelry:design_sketch', 1, 'handbook.immersive_jewelry.designs.title',
                         [M('designs'), T(0), C('policy_card', topic='designs')]),
    'designs/preparation': ('designs', 'immersive_jewelry:polishing_compound', 3, None,
                            [T(0), T(1), T(2), T(3), W('staging_and_supplies', 4)]),
    'designs/atlas': ('designs', 'immersive_jewelry:amulet', 2, None,
                      [T(0)] + [C('design_card', design='immersive_jewelry:designs/' + d) for d in DESIGNS]
                      + [C('design_card', design='others')]),

    'setting/bench': ('setting', 'immersive_jewelry:jewelers_bench', 0, 'handbook.immersive_jewelry.bench.title', [M('bench')]),
    'setting/prongs': ('setting', 'immersive_jewelry:jewelers_pliers', 1, 'handbook.immersive_jewelry.setting.title',
                       [M('setting'), W('seat_and_prongs', 0)]),
    'setting/bezels': ('setting', 'immersive_jewelry:bracelet', 2, None, [T(0), T(1), W('bezel_setting', 2)]),
    'setting/multi_stone': ('setting', 'immersive_jewelry:circlet', 3, None, [T(0), T(1), W('three_stones', 2)]),
    'setting/defects': ('setting', 'immersive_jewelry:fine_pliers', 4, None, [T(0), T(1), T(2)]),

    'finishing/finishes': ('finishing', 'immersive_jewelry:polishing_compound', 0, 'handbook.immersive_jewelry.finishes.title',
                           [M('finishes')] + [C('finish_card', finish='immersive_jewelry:' + f) for f in FINISHES]
                           + [W('polished_finish', 0), W('burnished_finish', 1), W('antique_finish', 2)]),
    'finishing/refinishing': ('finishing', 'minecraft:gunpowder', 1, None, [T(0), T(1), W('refinish_and_resume', 2)]),

    'wearing/slots': ('wearing', 'immersive_jewelry:ring', 0, 'handbook.immersive_jewelry.wearing.title',
                      [M('wearing'), C('policy_card', topic='wearing'), W('wearing_and_effects', 0)]),
    'wearing/effects': ('wearing', 'immersive_jewelry:amulet', 1, 'handbook.immersive_jewelry.effects.title',
                        [M('effects'), C('piece_card'), C('effect_card'), W('quality_and_inspection', 0)]),
    'wearing/gates': ('wearing', 'minecraft:enchanted_book', 2, None, [T(0), T(1), T(2)]),

    'care/care': ('care', 'immersive_jewelry:fine_pliers', 0, 'handbook.immersive_jewelry.care.title',
                  [M('care'), C('policy_card', topic='care')]),
    'care/rework': ('care', 'immersive_jewelry:jewelers_pliers', 1, None,
                    [T(0), T(1), T(2), T(3), C('care_card'), W('retighten_and_repair', 4), W('remove_and_reuse', 5)]),
    'care/storage': ('care', 'minecraft:chest', 2, None, [T(0), T(1)]),

    'workshop/jeweler': ('workshop', 'minecraft:emerald', 0, 'handbook.immersive_jewelry.jeweler.title',
                         [M('jeweler'), C('policy_card', topic='workshop')]),
    'workshop/multiplayer': ('workshop', 'minecraft:player_head', 1, None, [T(0), T(1), W('watching_and_handoff', 2)]),
    'workshop/integrations': ('workshop', 'minecraft:spyglass', 2, None, [T(0), T(1), T(2)]),

    'trouble/common': ('trouble', 'minecraft:barrier', 0, None, [T(0), T(1), T(2), T(3)]),
    'trouble/packs': ('trouble', 'minecraft:writable_book', 1, None, [T(0), T(1), T(2)]),
}

TEMPLATES = {
    'gem_card': ('GemCard', ['gem']),
    'cut_card': ('CutCard', ['cut']),
    'design_card': ('DesignCard', ['design']),
    'finish_card': ('FinishCard', ['finish']),
    'metal_card': ('MetalCard', ['show']),
    'policy_card': ('PolicyCard', ['topic']),
    'piece_card': ('PieceCard', []),
    'effect_card': ('EffectCard', []),
    'care_card': ('CareCard', []),
    'keys_card': ('KeysCard', []),
    'tools_card': ('ToolsCard', []),
}


def dump(data):
    return (json.dumps(data, indent=2, ensure_ascii=False) + '\n').encode('utf-8')


def lang(locale):
    return json.loads((LANG / (locale + '.json')).read_text(encoding='utf-8'))


def entry_key(path):
    return path.replace('/', '.')


def migrated_pages(en, chapter):
    keys = []
    n = 0
    while 'handbook.immersive_jewelry.%s.%d' % (chapter, n) in en:
        keys.append('handbook.immersive_jewelry.%s.%d' % (chapter, n))
        n += 1
    return keys


def build():
    en = lang('en_us')
    files = {}
    used = {'book.immersive_jewelry.title', 'book.immersive_jewelry.landing', 'book.immersive_jewelry.subtitle'}
    lessons = set()
    files[DATA / 'book.json'] = dump(BOOK)
    for cat, (icon, sortnum) in CATEGORIES.items():
        name, desc = 'book.immersive_jewelry.category.' + cat, 'book.immersive_jewelry.category.' + cat + '.description'
        used.update([name, desc])
        files[ASSETS / 'categories' / (cat + '.json')] = dump({'name': name, 'description': desc, 'icon': icon, 'sortnum': sortnum})
    for path, (cat, icon, sortnum, name, pages) in OUTLINE.items():
        name = name or 'book.immersive_jewelry.entry.' + entry_key(path)
        used.add(name)
        out = []
        for page in pages:
            kind = page[0]
            if kind == 'text':
                key = 'book.immersive_jewelry.%s.%d' % (entry_key(path), page[1])
                used.add(key)
                out.append({'type': 'patchouli:text', 'text': key})
            elif kind == 'migrated':
                keys = migrated_pages(en, page[1])
                if not keys:
                    raise SystemExit('chapter %s has no pages in en_us.json' % page[1])
                for key in keys:
                    used.add(key)
                    out.append({'type': 'patchouli:text', 'text': key})
            elif kind == 'card':
                entry = {'type': '%s:%s' % (NS, page[1])}
                entry.update(page[2])
                out.append(entry)
            elif kind == 'watch':
                key = 'book.immersive_jewelry.%s.watch.%d' % (entry_key(path), page[2])
                used.add(key)
                used.add('lesson.immersive_jewelry.' + page[1])
                lessons.add(page[1])
                out.append({'type': '%s:watch' % NS, 'lesson': page[1], 'entry': '%s:%s' % (NS, path), 'text': key})
        files[ASSETS / 'entries' / (path + '.json')] = dump({
            'name': name, 'icon': icon, 'category': '%s:%s' % (NS, cat), 'sortnum': sortnum, 'pages': out})
    for template, (cls, params) in TEMPLATES.items():
        component = {'type': 'patchouli:custom', 'class': 'com.otectus.immersivejewelry.client.guide.' + cls, 'x': 0, 'y': 0}
        for p in params:
            component[p] = '#' + p
        files[ASSETS / 'templates' / (template + '.json')] = dump({'components': [component]})
    files[ASSETS / 'templates' / 'watch.json'] = dump({'components': [
        {'type': 'patchouli:custom', 'class': 'com.otectus.immersivejewelry.client.guide.WatchButton', 'x': 0, 'y': 0,
         'lesson': '#lesson', 'entry': '#entry'},
        {'type': 'patchouli:text', 'text': '#text', 'x': 0, 'y': 26, 'max_width': 116}]})
    # Card labels are used by the Java cards; the resource test checks those against en_us. The book's own keys:
    return files, used, lessons


def java_lessons():
    text = (JAVA / 'Lessons.java').read_text(encoding='utf-8')
    body = text[text.index('public enum Lessons {'):text.index('/** The index topics')]
    return {m.group(1).lower() for m in re.finditer(r'^\s+([A-Z_]+)\(Topic', body, re.M)}


def java_entries():
    text = (JAVA / 'HelpTopics.java').read_text(encoding='utf-8')
    block = text[text.index('ENTRIES = {'):text.index('};', text.index('ENTRIES = {'))]
    return set(re.findall(r'"([a-z_]+/[a-z_]+)"', block))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--write', action='store_true')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    files, used, lessons = build()
    problems = []
    known = java_lessons()
    for lesson in sorted(lessons - known):
        problems.append('Watch button names unknown lesson %s' % lesson)
    for entry in sorted(java_entries() - set(OUTLINE)):
        problems.append('HelpTopics links to missing entry %s' % entry)
    for locale in LOCALES:
        table = lang(locale)
        for key in sorted(used):
            if key not in table:
                problems.append('%s: missing %s' % (locale, key))
    if args.write:
        for path, data in files.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        for folder in (ASSETS / 'entries', ASSETS / 'categories', ASSETS / 'templates'):
            for path in folder.rglob('*.json'):
                if path not in files:
                    path.unlink()
        print('wrote %d handbook files' % len(files))
    else:
        for path, data in files.items():
            if not path.exists() or path.read_bytes() != data:
                problems.append('stale: %s (run tools/generate_book.py --write)' % path.relative_to(ROOT))
        for folder in (ASSETS / 'entries', ASSETS / 'categories', ASSETS / 'templates'):
            for path in folder.rglob('*.json') if folder.exists() else []:
                if path not in files:
                    problems.append('not generated: %s' % path.relative_to(ROOT))
    if problems:
        print('handbook problems:')
        for p in problems[:200]:
            print('  ' + p)
        if len(problems) > 200:
            print('  ... and %d more' % (len(problems) - 200))
        return 1
    print('handbook up to date (%d files, %d keys in %d locales)' % (len(files), len(used), len(LOCALES)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
