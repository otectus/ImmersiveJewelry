#!/usr/bin/env python3
"""Exports or verifies the GeckoLib rigs (art/jewelry_rigs.py) and the jeweler's body clips (art/jewelry_poses.py).

  python3 tools/generate_rigs.py            # --check (default): compare exports, write nothing
  python3 tools/generate_rigs.py --write    # export geo, animations, rig textures and player animations

Writes:
  assets/immersive_jewelry/geo/<rig>.geo.json
  assets/immersive_jewelry/animations/<rig>.animation.json
  assets/immersive_jewelry/textures/rig/<rig>.png (and bench_tools_fine.png)
  assets/immersive_jewelry/player_animation/<clip>.json
"""
import argparse
import io
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'art'))
import jewelry_poses  # noqa: E402
import jewelry_rigs  # noqa: E402

ASSETS = ROOT / 'src/main/resources/assets/immersive_jewelry'


def dump(data):
    return (json.dumps(data, indent=2, ensure_ascii=False) + '\n').encode('utf-8')


def png(image):
    out = io.BytesIO()
    image.save(out, format='PNG', optimize=True)
    return out.getvalue()


def outputs():
    files = {}
    for name, build in jewelry_rigs.RIGS.items():
        files['geo/%s.geo.json' % name] = dump(jewelry_rigs.geometry(name, build()))
        files['animations/%s.animation.json' % name] = dump(jewelry_rigs.animations(name))
        files['textures/rig/%s.png' % name] = png(jewelry_rigs.texture())
    files['textures/rig/bench_tools_fine.png'] = png(jewelry_rigs.texture('fine'))
    for name, clip in jewelry_poses.all_clips().items():
        files['player_animation/%s.json' % name] = dump(clip)
    return files


def same_png(path, data):
    from PIL import Image
    try:
        a = Image.open(path).convert('RGBA')
        b = Image.open(io.BytesIO(data)).convert('RGBA')
    except OSError:
        return False
    return a.size == b.size and a.tobytes() == b.tobytes()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--write', action='store_true')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    files = outputs()
    stale = []
    for relative, data in sorted(files.items()):
        path = ASSETS / relative
        if args.write:
            path.parent.mkdir(parents=True, exist_ok=True)
            if relative.endswith('.png') and path.exists() and same_png(path, data):
                continue
            path.write_bytes(data)
            continue
        if not path.exists():
            stale.append(relative + ' (missing)')
        elif relative.endswith('.png'):
            if not same_png(path, data):
                stale.append(relative)
        elif path.read_bytes() != data:
            stale.append(relative)
    # Clips no longer defined must not linger: a pack or the Java side could still name them.
    for folder, suffix in (('player_animation', '.json'), ('geo', '.geo.json'), ('animations', '.animation.json')):
        for path in sorted((ASSETS / folder).glob('*' + suffix)) if (ASSETS / folder).exists() else []:
            relative = '%s/%s' % (folder, path.name)
            if relative not in files:
                if args.write:
                    path.unlink()
                else:
                    stale.append(relative + ' (not generated)')
    if args.write:
        print('wrote %d rig and pose files' % len(files))
        return 0
    if stale:
        print('stale rig/pose files (run tools/generate_rigs.py --write):')
        for s in stale:
            print('  ' + s)
        return 1
    print('rig and pose files up to date (%d)' % len(files))
    return 0


if __name__ == '__main__':
    sys.exit(main())
