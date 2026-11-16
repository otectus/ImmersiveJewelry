#!/usr/bin/env python3
"""Exports or verifies the demonstration (Ponder) structures: vanilla structure templates under
assets/immersive_jewelry/ponder/<name>.nbt, written deterministically (gzip with a fixed timestamp).

  python3 tools/generate_ponder.py            # --check (default)
  python3 tools/generate_ponder.py --write

Layouts (layer y=0 is Ponder's base plate; stations face north, toward Ponder's camera):
  wheel     5x2x5, Lapidary Wheel at (2,1,2)
  bench     5x2x5, Jeweler's Bench at (2,1,2)
  workshop  7x2x5, wheel at (2,1,2), bench at (4,1,2)
"""
import argparse
import gzip
import io
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'src/main/resources/assets/immersive_jewelry/ponder'
DATA_VERSION = 3465  # Minecraft 1.20.1

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


class Int(int):
    pass


def _name(out, name):
    data = name.encode('utf-8')
    out.write(struct.pack('>H', len(data)))
    out.write(data)


def _payload(out, value):
    if isinstance(value, Int):
        out.write(struct.pack('>i', value))
    elif isinstance(value, str):
        _name(out, value)
    elif isinstance(value, list):
        kind = _kind(value[0]) if value else TAG_END
        out.write(struct.pack('>bi', kind, len(value)))
        for item in value:
            _payload(out, item)
    elif isinstance(value, dict):
        for key in value:
            out.write(struct.pack('>b', _kind(value[key])))
            _name(out, key)
            _payload(out, value[key])
        out.write(struct.pack('>b', TAG_END))
    else:
        raise TypeError(value)


def _kind(value):
    if isinstance(value, Int):
        return TAG_INT
    if isinstance(value, str):
        return TAG_STRING
    if isinstance(value, list):
        return TAG_LIST
    if isinstance(value, dict):
        return TAG_COMPOUND
    raise TypeError(value)


def nbt(root):
    out = io.BytesIO()
    out.write(struct.pack('>b', TAG_COMPOUND))
    _name(out, '')
    _payload(out, root)
    raw = out.getvalue()
    packed = io.BytesIO()
    with gzip.GzipFile(fileobj=packed, mode='wb', mtime=0) as g:
        g.write(raw)
    return packed.getvalue()


FLOOR_A = {'Name': 'minecraft:spruce_planks'}
FLOOR_B = {'Name': 'minecraft:stripped_spruce_wood', 'Properties': {'axis': 'x'}}
WHEEL = {'Name': 'immersive_jewelry:lapidary_wheel', 'Properties': {'facing': 'north'}}
BENCH = {'Name': 'immersive_jewelry:jewelers_bench', 'Properties': {'facing': 'north'}}


def template(width, depth, stations):
    palette = [FLOOR_A, FLOOR_B, WHEEL, BENCH]
    blocks = []
    for x in range(width):
        for z in range(depth):
            blocks.append({'pos': [Int(x), Int(0), Int(z)], 'state': Int((x + z) % 2)})
    for (x, z), state in stations:
        blocks.append({'pos': [Int(x), Int(1), Int(z)], 'state': Int(palette.index(state))})
    return {
        'DataVersion': Int(DATA_VERSION),
        'size': [Int(width), Int(2), Int(depth)],
        'palette': palette,
        'blocks': blocks,
        'entities': [],
    }


LAYOUTS = {
    'wheel': template(5, 5, [((2, 2), WHEEL)]),
    'bench': template(5, 5, [((2, 2), BENCH)]),
    'workshop': template(7, 5, [((2, 2), WHEEL), ((4, 2), BENCH)]),
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--write', action='store_true')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    stale = []
    for name, layout in LAYOUTS.items():
        data = nbt(layout)
        path = OUT / (name + '.nbt')
        if args.write:
            OUT.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        elif not path.exists() or path.read_bytes() != data:
            stale.append(path.name)
    if args.write:
        print('wrote %d demonstration structures' % len(LAYOUTS))
        return 0
    if stale:
        print('stale demonstration structures (run tools/generate_ponder.py --write): ' + ', '.join(stale))
        return 1
    print('demonstration structures up to date (%d)' % len(LAYOUTS))
    return 0


if __name__ == '__main__':
    sys.exit(main())
