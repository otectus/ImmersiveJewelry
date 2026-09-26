#!/usr/bin/env python3
"""Encodes the sounds defined in art/jewelry_audio.py to OGG Vorbis (needs ffmpeg with libvorbis).

  python3 tools/generate_sounds.py            # --check (default): verify every sound file exists and matches
  python3 tools/generate_sounds.py --write    # encode every sound into the resource folder

Encoding is bit-exact (fixed quality, no metadata), so --check can compare bytes.
"""
import argparse
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'art'))
import jewelry_audio  # noqa: E402

SOUNDS = ROOT / 'src/main/resources/assets/immersive_jewelry/sounds'


def encode(samples):
    pcm = (np.clip(samples, -1, 1) * 32767).astype('<i2').tobytes()
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / 'in.wav'
        ogg = Path(tmp) / 'out.ogg'
        with wave.open(str(wav), 'wb') as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(jewelry_audio.RATE)
            w.writeframes(pcm)
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(wav), '-c:a', 'libvorbis', '-q:a', '5',
                        '-map_metadata', '-1', '-fflags', '+bitexact', '-flags:a', '+bitexact', str(ogg)], check=True)
        return ogg.read_bytes()


def main():
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--write', action='store_true')
    group.add_argument('--check', action='store_true')
    args = parser.parse_args()
    sounds = jewelry_audio.sounds()
    differing = []
    for name, samples in sorted(sounds.items()):
        data = encode(samples)
        path = SOUNDS / f'{name}.ogg'
        if args.write:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        elif not path.exists() or path.read_bytes() != data:
            differing.append(name)
    if args.write:
        print(f'wrote {len(sounds)} sounds')
        return 0
    for name in differing:
        print(f'differs {name}')
    print(f'{len(sounds) - len(differing)}/{len(sounds)} sounds up to date')
    return 1 if differing else 0


if __name__ == '__main__':
    sys.exit(main())
