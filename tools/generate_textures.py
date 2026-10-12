#!/usr/bin/env python3
"""Exports or verifies the artwork defined in art/jewelry_art.py.

  python3 tools/generate_textures.py            # --check (default): compare exports, write nothing
  python3 tools/generate_textures.py --write    # export every texture into the resource folder
  python3 tools/generate_textures.py --preview out.png   # an upscaled contact sheet for review
  python3 tools/generate_textures.py --showcase out.png  # tinted jewelry in several metals, gems and finishes
"""
import argparse
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'art'))
import jewelry_art  # noqa: E402

RESOURCES = ROOT / 'src/main/resources'
TEXTURES = RESOURCES / 'assets/immersive_jewelry/textures'
DATA = RESOURCES / 'data'
# Textures of the retired Stoneborn GUI. The screens draw Minecraft's own GUI textures now; these must never come back.
RETIRED = ['gui/stone.png', 'gui/slot.png']


def tint(layer, rgb):
    r, g, b = rgb
    out = Image.new('RGBA', layer.size, (0, 0, 0, 0))
    lp, op = layer.load(), out.load()
    for y in range(layer.height):
        for x in range(layer.width):
            v = lp[x, y]
            if v[3]:
                op[x, y] = (v[0] * r // 255, v[1] * g // 255, v[2] * b // 255, v[3])
    return out


def mix(a, b, t):
    """Match JewelryGui.mix: Mth.clamp followed by Math.round for each channel."""
    t = max(0.0, min(1.0, t))
    return tuple(math.floor(a[i] + (b[i] - a[i]) * t + 0.5) for i in range(3))


def scale(rgb, factor):
    """Match JewelryGui.scale's Math.round channel multiplication."""
    return tuple(max(0, min(255, math.floor(channel * factor + 0.5))) for channel in rgb)


def color(value):
    value = value.lstrip('#')
    return tuple(int(value[index:index + 2], 16) for index in range(0, 6, 2))


def data_entries(relative):
    """Return deterministic (id, JSON) data entries from the mod's resource definitions."""
    entries = []
    for path in sorted(DATA.glob(relative)):
        with path.open(encoding='utf-8') as source:
            entries.append((path.stem.replace('_', ' '), json.load(source)))
    return entries


def palette():
    gems = [(name, color(entry['base_color']), color(entry['sparkle_color']))
            for name, entry in data_entries('immersive_jewelry/immersive_jewelry/gems/*.json')]
    metals = [(path.stem.replace('_', ' '), color(entry['color']))
              for namespace in ('minecraft', 'forge')
              for path in sorted((DATA / namespace / 'immersive_jewelry/metals').glob('*.json'))
              for entry in (json.loads(path.read_text(encoding='utf-8')),)]
    finishes = [(name, entry) for name, entry in data_entries('immersive_jewelry/immersive_jewelry/finishes/*.json')]
    return gems, metals, finishes


def finish_layers(metal, finish):
    """The recess/highlight portions of JewelryColors for a complete jewelry item."""
    if 'recess_color' in finish:
        recess = mix(metal, color(finish['recess_color']), 0.7)
    else:
        recess = scale(metal, 1.0 - finish.get('shade', 0.4))
    highlight = color(finish['highlight_color']) if 'highlight_color' in finish else mix(
        metal, (255, 255, 255), finish.get('highlight_mix', 0.5))
    return recess, highlight


def sparkle(body, bright, quality=0.9):
    """JewelryColors.sparkle with default client intensity, intact gem, quality .9."""
    intensity = 0.25 + 0.75 * quality
    return mix(body, mix(bright, (255, 255, 255), 0.35 * quality), intensity)


def font(size=16):
    try:
        return ImageFont.truetype('DejaVuSans.ttf', size)
    except OSError:
        return ImageFont.load_default()


def title(draw, text, x, y, face):
    draw.text((x, y), text.upper(), font=face, fill=(239, 215, 157), stroke_width=1, stroke_fill=(25, 20, 26))


def label(draw, text, x, y, face, fill=(211, 204, 190)):
    draw.text((x, y), text, font=face, fill=fill)


def panel(draw, box):
    x0, y0, x1, y1 = box
    draw.rounded_rectangle(box, radius=8, fill=(44, 37, 48), outline=(117, 93, 62), width=2)
    draw.rectangle((x0 + 5, y0 + 5, x1 - 5, y1 - 5), outline=(27, 23, 30), width=2)


def paste_sprite(sheet, image, x, y, scale_factor=4):
    sheet.alpha_composite(image.resize((image.width * scale_factor, image.height * scale_factor), Image.Resampling.NEAREST), (x, y))


def jewelry_sprite(art, form, cut, metal, finish, gem, bright):
    image = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    recess, highlight = finish_layers(metal, finish)
    image.alpha_composite(tint(art[f'item/{form}/frame.png'], metal))
    image.alpha_composite(tint(art[f'item/{form}/recess.png'], recess))
    image.alpha_composite(tint(art[f'item/{form}/highlight.png'], highlight))
    image.alpha_composite(tint(art[f'item/{form}/gem_{cut}.png'], gem))
    image.alpha_composite(tint(art[f'item/{form}/sparkle_{cut}.png'], sparkle(gem, bright)))
    return image


def showcase(path):
    """Labeled art-review sheet composited with the item's actual runtime tint equations."""
    art = jewelry_art.textures()
    gems, metals, finishes = palette()
    gem_map = {name: (body, bright) for name, body, bright in gems}
    metal_map = {name: value for name, value in metals}
    finish_map = {name: value for name, value in finishes}
    forms, cuts = ['ring', 'amulet', 'bracelet', 'circlet'], list(jewelry_art.CUTS)
    sheet = Image.new('RGBA', (1040, 1295), (31, 27, 36, 255))
    draw = ImageDraw.Draw(sheet)
    heading, body = font(24), font(15)
    title(draw, 'Immersive Jewelry — runtime tint review', 30, 20, heading)
    label(draw, 'Data palettes + JewelryColors formulas; cut gems quality 0.90, rough sheen 0.25, sparkle intensity 1.00.', 31, 52, body)

    panel(draw, (20, 82, 615, 518))
    title(draw, 'Form × cut', 38, 96, heading)
    ruby, ruby_spark = gem_map['ruby']
    gold, polished = metal_map['gold'], finish_map['polished']
    for column, form_name in enumerate(forms):
        x = 128 + column * 118
        label(draw, form_name.title(), x, 138, body)
    for row, cut in enumerate(cuts):
        y = 173 + row * 79
        label(draw, cut.title(), 39, y + 24, body)
        for column, form_name in enumerate(forms):
            panel(draw, (115 + column * 118, y, 219 + column * 118, y + 68))
            paste_sprite(sheet, jewelry_sprite(art, form_name, cut, gold, polished, ruby, ruby_spark), 135 + column * 118, y + 2)

    panel(draw, (635, 82, 1020, 518))
    title(draw, 'Metal + finish contrast', 653, 96, heading)
    contrasts = [('gold', 'polished', 'ruby'), ('silver', 'burnished', 'sapphire'), ('copper', 'antique', 'emerald'),
                 ('netherite', 'polished', 'diamond'), ('rose gold', 'antique', 'amethyst'), ('steel', 'burnished', 'topaz')]
    for index, (metal_name, finish_name, gem_name) in enumerate(contrasts):
        col, row = index % 2, index // 2
        x, y = 655 + col * 180, 148 + row * 111
        panel(draw, (x, y, x + 165, y + 99))
        gem, bright = gem_map[gem_name]
        paste_sprite(sheet, jewelry_sprite(art, 'ring', 'brilliant', metal_map[metal_name], finish_map[finish_name], gem, bright), x + 10, y + 16)
        label(draw, metal_name.title(), x + 80, y + 26, body)
        label(draw, finish_name.title(), x + 80, y + 48, body, (181, 164, 139))
        label(draw, gem_name.title(), x + 80, y + 70, body, (181, 164, 139))

    panel(draw, (20, 538, 1020, 1263))
    title(draw, 'Loose gems + blanks', 38, 551, heading)
    label(draw, 'All built-in gem data palettes', 38, 580, body)
    loose_x, loose_y, label_gutter, cell_w, cell_h = 52, 612, 110, 76, 64
    for column, name in enumerate(['rough'] + cuts):
        label(draw, name.title(), loose_x + label_gutter + column * cell_w, loose_y, body)
    for index, (gem_name, gem, bright) in enumerate(gems):
        y = loose_y + 31 + index * cell_h
        x = loose_x
        label(draw, gem_name.title(), x, y + 25, body)
        rough = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        rough.alpha_composite(tint(art['item/gem/rough.png'], gem))
        rough.alpha_composite(tint(art['item/gem/rough_sheen.png'], sparkle(gem, bright, 0.25)))
        paste_sprite(sheet, rough, x + label_gutter, y)
        for cut_index, cut in enumerate(cuts):
            loose = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
            loose.alpha_composite(tint(art[f'item/gem/cut_{cut}.png'], gem))
            loose.alpha_composite(tint(art[f'item/gem/cut_{cut}_sparkle.png'], sparkle(gem, bright)))
            paste_sprite(sheet, loose, x + label_gutter + (cut_index + 1) * cell_w, y)

    blank_x, blank_y = 625, 612
    title(draw, 'Blank frames', blank_x, blank_y, heading)
    for index, (form_name, blank_name) in enumerate([('ring', 'ring_blank'), ('amulet', 'amulet_frame'), ('bracelet', 'bracelet_blank'), ('circlet', 'circlet_frame')]):
        x = blank_x + (index % 2) * 180
        y = blank_y + 43 + (index // 2) * 72
        panel(draw, (x, y, x + 168, y + 62))
        blank = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        blank.alpha_composite(tint(art[f'item/blank/{blank_name}.png'], gold))
        blank.alpha_composite(tint(art[f'item/blank/{blank_name}_highlight.png'], mix(gold, (255, 255, 255), 0.45)))
        paste_sprite(sheet, blank, x + 12, y - 1, 3)
        label(draw, form_name.title(), x + 78, y + 21, body)
    sheet.save(path)
    print(f'showcase: {path}')


def main():
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--write', action='store_true')
    group.add_argument('--check', action='store_true')
    group.add_argument('--preview')
    group.add_argument('--showcase')
    args = parser.parse_args()
    art = jewelry_art.textures()
    extras = jewelry_art.extras()

    if args.showcase:
        showcase(args.showcase)
        return 0

    if args.preview:
        scale = 6
        cols = 8
        items = sorted(art.items())
        rows = (len(items) + cols - 1) // cols
        sheet = Image.new('RGBA', (cols * 34 * scale, rows * 34 * scale), (58, 50, 70, 255))
        for i, (name, im) in enumerate(items):
            factor = max(1, 32 // max(im.width, im.height))
            big = im.resize((im.width * scale * factor // 2, im.height * scale * factor // 2), Image.NEAREST)
            sheet.alpha_composite(big, ((i % cols) * 34 * scale + scale, (i // cols) * 34 * scale + scale))
        sheet.save(args.preview)
        print(f'preview: {args.preview} ({len(art)} textures)')
        return 0

    targets = {TEXTURES / name: im for name, im in art.items()}
    targets.update({RESOURCES / name: im for name, im in extras.items()})
    if args.write:
        for path, im in targets.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            im.save(path)
        for name in RETIRED:
            (TEXTURES / name).unlink(missing_ok=True)
        print(f'wrote {len(targets)} textures')
        return 0

    differing = []
    for path, im in targets.items():
        if not path.exists():
            differing.append(f'missing {path.relative_to(RESOURCES)}')
            continue
        on_disk = Image.open(path).convert('RGBA')
        if on_disk.size != im.size or on_disk.tobytes() != im.tobytes():
            differing.append(f'differs {path.relative_to(RESOURCES)}')
    up_to_date = len(targets) - len(differing)
    for name in RETIRED:
        if (TEXTURES / name).exists():
            differing.append(f'retired {(TEXTURES / name).relative_to(RESOURCES)} (delete it)')
    for line in differing:
        print(line)
    print(f'{up_to_date}/{len(targets)} textures up to date')
    return 1 if differing else 0


if __name__ == '__main__':
    sys.exit(main())
