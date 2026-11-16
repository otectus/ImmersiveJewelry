"""Editable pixel-art source for Immersive Jewelry. Deterministic: no random noise, no resampling.

Resource PNGs are delivery exports. Edit this source, jewelry_pixels.py or workshop_pixels.py,
then run tools/generate_textures.py --write. All item, block, GUI and particle sprites are
16x16. Profession overlays retain the standard 64x64 Minecraft entity UV layout.

Jewelry items are layered and tinted in the game (spec 36): every metal, gem, cut and finish combination comes from a
handful of grayscale layers. A frame is drawn as three layers - base (the metal's body), recess (shadowed hollows,
tinted by the finish's shade or patina) and highlight (raised edges, tinted toward white by the finish) - and each
stone as a body (facet shading, tinted by the gem colour) and a sparkle (glints, brighter for better cuts).
Grays near white keep the tint's own colour; darker grays shade it.
"""
import math

from PIL import Image, ImageDraw
from jewelry_pixels import SPRITES
from workshop_pixels import SPRITES as WORKSHOP_SPRITES, sprite as workshop_sprite

CUTS = ['round', 'brilliant', 'emerald', 'cabochon']

# Ultima reference ramps: kubejs/assets/minecraft/textures/{block/oak_planks,block/anvil,
# item/iron_pickaxe,item/gold_ingot,item/leather}. Original pixel layouts, sampled material colours.
# Jewelry silhouettes follow art/references, through the master in art/sources.
# No external pack files or image-generation service are needed to regenerate exports.
WOOD = ['#38251e', '#4c3022', '#65432b', '#7d5635', '#946c44', '#b38a59']
WALNUT = WOOD
IRON = ['#20252b', '#2b333b', '#3e4a50', '#53616a', '#697a81', '#899da2']
STEEL = ['#222833', '#404b57', '#6a7b87', '#a2b3ba', '#dae7e6']
BRASS = ['#493118', '#705021', '#a4762e', '#c5953d', '#e2bf67', '#fff0b4']
COPPER = ['#402b28', '#673d30', '#985536', '#bf7e4e', '#ebaf76']
LEATHER = ['#2b1714', '#43231c', '#663728', '#85503a', '#a96b4c', '#c58e62']
FELT = ['#122925', '#203f32', '#365741', '#5a7958']
WATER = ['#1d353d', '#2d505b', '#477680', '#87bbc0']


def canvas(w=16, h=16):
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    return im, im.load()


def gray(v, a=255):
    v = max(0, min(255, int(round(v))))
    return (v, v, v, a)


def hexrgb(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# ================================================================================================ frames
# Eight discrete planes give the references' broad reflections and deep inner edges.
# Recess and highlight masks share the silhouette; finishes never repaint the whole band.
FRAME_ROWS = {form: SPRITES[form] for form in ('ring', 'amulet', 'bracelet', 'circlet')}
FRAME_VALUES = (48, 86, 126, 166, 205, 226, 244, 255)
STONE_SEAT = {'ring': (5.5, 4.5, 2.5), 'amulet': (4.5, 11.5, 2.5),
              'bracelet': (10.5, 11.5, 2.5), 'circlet': (7.5, 10.5, 2.5)}


def frame_pixels(form):
    rows = FRAME_ROWS[form]
    assert len(rows) == 16 and all(len(row) == 16 for row in rows)
    return {(x, y): (FRAME_VALUES[int(key)], int(key) <= 1)
            for y, row in enumerate(rows) for x, key in enumerate(row) if key != '.'}


FRAMES = {form: (lambda form=form: frame_pixels(form)) for form in FRAME_ROWS}


def frame_layers(form):
    base, bp = canvas()
    recess, rp = canvas()
    high, hp = canvas()
    for (x, y), (value, inside) in frame_pixels(form).items():
        bp[x, y] = gray(value)
        if inside:
            rp[x, y] = gray(value)
        elif value >= FRAME_VALUES[5]:
            hp[x, y] = gray({226: 190, 244: 230, 255: 255}[value])
    return base, recess, high


# ================================================================================================ stones

GEM_VALUES = (58, 96, 136, 177, 211, 234, 249, 255)
# Small settings need their own pixel clusters: resampling a loose stone erases its cut.
# The pointed brilliant and narrow step-cut retain distinct outlines even when mounted.
MOUNTED = {
    'round': ('.463.', '47532', '75321', '43251', '.210.'),
    'brilliant': ('.4444.', '774377', '.6324.', '..61..', '..0...'),
    'emerald': ('.44.', '5731', '5321', '5321', '4261', '.21.'),
    'cabochon': ('.45.', '7751', '7301', '4241', '.21.'),
}


def gem_planes(rows, ox=0, oy=0, w=16, h=16):
    """Split painted facet clusters into a saturated body and quality-sensitive reflections."""
    body, bp = canvas(w, h)
    sparkle, sp = canvas(w, h)
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            if key == '.':
                continue
            level = int(key)
            bp[x + ox, y + oy] = gray(GEM_VALUES[level])
            if level >= 5:
                sp[x + ox, y + oy] = gray({5: 170, 6: 220, 7: 255}[level])
    return body, sparkle


def stone_layers(cx, cy, r, cut, w=16, h=16):
    """Loose and mounted sprites share facets, but keep dedicated inventory-scale silhouettes."""
    if r >= 4:
        return gem_planes(SPRITES[cut], w=w, h=h)
    rows = MOUNTED[cut]
    return gem_planes(rows, math.floor(cx - len(rows[0]) / 2), math.floor(cy - len(rows) / 2), w, h)


def rough_layers():
    return gem_planes(SPRITES['rough'])


def blank_layers(form):
    base, bp = canvas()
    high, hp = canvas()
    for (x, y), (value, inside) in frame_pixels(form).items():
        bp[x, y] = gray(value * 0.88)
        if value >= FRAME_VALUES[5] and not inside:
            hp[x, y] = gray({226: 160, 244: 200, 255: 225}[value])
    return base, high


# ================================================================================================ tools and items

def workshop_item(index):
    """Authored 16px shapes, palettes and highlights; never downsample a larger illustration."""
    return workshop_sprite(tuple(WORKSHOP_SPRITES)[index])


def pliers(fine):
    return workshop_item(1 if fine else 0)


def lap(tier):
    return workshop_item(2 if tier == 'emery' else 3)


def polishing_compound():
    return workshop_item(4)


def gem_dust():
    return workshop_item(5)


def design_sketch():
    return workshop_item(6)


def handbook():
    return workshop_item(7)


# ================================================================================================ blocks

def planks(colors, salt, vertical=False):
    """Long grain and interrupted growth lines, with staggered, lightly bevelled board joints."""
    im, _ = canvas(); d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 15, 15), fill=colors[2])
    for row, joint in enumerate((5, 12, 3, 10)):
        y = row * 4
        d.rectangle((0, y, 15, y + 2), fill=colors[2 + row % 2])
        d.line((0, y, 15, y), fill=colors[4])
        d.line((0, y + 3, 15, y + 3), fill=colors[1])
        d.line((joint, y + 1, joint, y + 3), fill=colors[0])
        shift = (salt + row * 3) % 7
        d.line((shift, y + 1, shift + 4, y + 1), fill=colors[3])
        d.line((shift + 6, y + 2, min(15, shift + 10), y + 2), fill=colors[1])
    # A single knot, not an all-over noise pattern.
    kx, ky = (9, 5) if salt == 3 else (4, 9)
    d.line((kx - 2, ky, kx, ky), fill=colors[1])
    d.line((kx + 1, ky + 1, kx + 3, ky + 1), fill=colors[4])
    d.point((kx, ky + 1), fill=colors[0])
    return im.transpose(Image.Transpose.ROTATE_90) if vertical else im


def end_grain(colors, salt):
    im, _ = canvas(); d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 15, 15), fill=colors[2])
    for points, value in [([(0, 3), (3, 0), (12, 0), (15, 3), (15, 12), (12, 15), (3, 15), (0, 12), (0, 3)], 1),
                          ([(2, 5), (5, 2), (11, 2), (13, 4), (13, 11), (10, 13), (5, 13), (2, 10), (2, 5)], 4),
                          ([(4, 6), (6, 4), (10, 4), (11, 6), (11, 10), (9, 11), (6, 11), (4, 9), (4, 6)], 1)]:
        d.line(points, fill=colors[value])
    d.line((6, 7, 9, 7), fill=colors[3]); d.line((7, 8, 9, 8), fill=colors[1])
    x = 10 if salt == 3 else 5
    d.line((x, 0, x, 3), fill=colors[0]); d.point((x - 1, 4), fill=colors[1])
    return im


def cloth(colors, salt):
    """Stitched leather and felt share edge construction but have different wear."""
    im, _ = canvas(); d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 15, 15), fill=colors[0])
    d.rectangle((1, 1, 14, 14), fill=colors[1])
    d.rectangle((2, 2, 13, 13), fill=colors[2])
    if salt == 5:
        for points in ([(3, 5), (6, 5), (8, 7)], [(5, 11), (10, 11), (12, 9)]):
            d.line(points, fill=colors[3])
        d.line((4, 6, 6, 6), fill=colors[1])
        stitch = colors[4]
    else:
        for x, y in ((3, 4), (8, 3), (11, 7), (4, 9), (8, 11)):
            d.line((x, y, x + 1, y), fill=colors[1])
        stitch = colors[3]
    for k in range(3, 14, 3):
        for xy in ((k, 1), (k, 14), (1, k), (14, k)):
            d.point(xy, fill=stitch)
    return im


def metal(colors, salt, brushed=True):
    """Broad reflective planes with a few directional tooling marks, not speckled noise."""
    im, _ = canvas(); d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 15, 15), fill=colors[2])
    d.polygon([(0, 0), (15, 0), (15, 2), (4, 4), (0, 8)], fill=colors[3])
    d.polygon([(0, 12), (10, 10), (15, 7), (15, 15), (0, 15)], fill=colors[1])
    d.line((0, 0, 15, 0), fill=colors[-1])
    d.line((0, 1, 0, 14), fill=colors[3])
    d.line((1, 15, 15, 15), fill=colors[0])
    for box in ((3, 5, 7, 5), (9, 8, 12, 8), (5, 12, 9, 12)):
        d.line(box, fill=colors[3 if brushed else 1])
    if salt == 17:  # a restrained verdigris seam on the copper reservoir
        d.line((13, 11, 14, 11), fill='#527766'); d.point((14, 12), fill='#38584d')
    return im


def gems_block():
    """Four 4px jar strips: pale glass lip, gem facets, dark foot. UV strip locations stay fixed."""
    im, _ = canvas(); d = ImageDraw.Draw(im)
    for x, colors in [(0, ['#4c223c', '#ae3150', '#f68091']), (4, ['#202946', '#3865ad', '#93ceec']),
                      (8, ['#153a34', '#318861', '#8edda5']), (12, ['#37243e', '#86549f', '#d2a9e5'])]:
        d.rectangle((x, 0, x + 3, 15), fill=colors[0])
        d.rectangle((x, 2, x + 2, 12), fill=colors[1])
        d.line((x, 1, x + 3, 1), fill='#bad0c8')
        for y in (4, 8, 11):
            d.line((x, y, x + 1, y + 1), fill=colors[2]); d.point((x + 2, y + 1), fill=colors[0])
        d.line((x, 3, x, 6), fill='#d9e7de')
        d.line((x, 14, x + 2, 14), fill=colors[1])
    return im


def basin():
    im = metal(IRON, 0); d = ImageDraw.Draw(im)
    d.line((0, 2, 15, 2), fill=IRON[0]); d.line((0, 3, 15, 3), fill=IRON[4])
    for x in (2, 12):
        d.rectangle((x, 6, x + 1, 7), fill=IRON[0]); d.point((x, 6), fill=STEEL[3])
    d.line((0, 12, 15, 12), fill='#354a4e')
    d.line((4, 10, 7, 10), fill=IRON[1])
    return im


def water():
    im, _ = canvas(); d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 15, 15), fill=WATER[1])
    # Stepped concentric ripples around a drip, with quiet areas between.
    for points, shade in [([(4, 5), (5, 4), (9, 4), (10, 5)], 3),
                          ([(4, 8), (5, 9), (9, 9), (10, 8)], 0),
                          ([(1, 5), (3, 2), (10, 2), (13, 5)], 2),
                          ([(1, 9), (4, 12), (11, 12), (14, 9)], 2)]:
        d.line(points, fill=WATER[shade])
    d.line((6, 6, 8, 6), fill=WATER[2]); d.point((6, 5), fill=WATER[3])
    return im


def lap_face():
    """Neutral abrasive rings retain the installed lap's runtime tint."""
    im, p = canvas()
    for y in range(16):
        for x in range(16):
            radius = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            value = 183
            if 5.2 < radius < 6.5: value = 221
            elif 3.8 < radius < 4.7: value = 157
            elif 2.2 < radius < 2.9: value = 206
            if x + y < 10: value += 14
            elif x + y > 21: value -= 14
            p[x, y] = gray(value)
    d = ImageDraw.Draw(im)
    for xy in ((5, 3), (11, 4), (3, 9), (10, 11), (7, 13)): d.point(xy, fill=gray(123))
    for xy in ((4, 5), (9, 2), (12, 9)): d.point(xy, fill=gray(244))
    return im


def rim():
    im, _ = canvas(); d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 15, 15), fill=IRON[2])
    for x in (0, 5, 10):
        d.line((x, 0, x, 15), fill=IRON[4])
        d.line((x + 1, 0, x + 1, 15), fill=IRON[1])
    for y in (3, 11):
        d.line((2, y, 4, y), fill=IRON[1])
    return im


def pin():
    im, _ = canvas(); d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 15, 15), fill=WOOD[3])
    for x in (1, 6, 11):
        d.line((x, 0, x, 15), fill=WOOD[2]); d.line((x + 1, 0, x + 1, 15), fill=WOOD[4])
    for x, y in ((4, 5), (8, 9), (12, 3)):
        d.line((x, y, x + 1, y + 2), fill=WOOD[1])
        d.point((x + 1, y), fill=WOOD[5])
    return im


def tool_materials():
    """Steel/brass/wood/leather in fixed 4px UV strips for the solid held tools."""
    im, _ = canvas(); d = ImageDraw.Draw(im)
    for x, colors in ((0, STEEL), (4, BRASS), (8, WOOD), (12, LEATHER)):
        d.rectangle((x, 0, x + 3, 15), fill=colors[2])
        d.line((x, 0, x, 15), fill=colors[1]); d.line((x + 1, 0, x + 1, 15), fill=colors[-1])
        d.line((x + 3, 0, x + 3, 15), fill=colors[0])
        for y in (5, 11):
            d.line((x + 1, y, x + 2, y), fill=colors[3])
    return im


# ================================================================================================ GUI, particles

def slot_icon(kind):
    """An empty-slot hint drawn like vanilla's armour slots: the piece's contour in their flat #555555 grey."""
    form = {'necklace': 'amulet', 'head': 'circlet'}.get(kind, kind)
    pixels = frame_pixels(form)
    im, p = canvas()
    for x, y in pixels:
        edge = any((x + dx, y + dy) not in pixels for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge: p[x, y] = (85, 85, 85, 255)
    return im


def particle_cluster(pixels, x, y, color):
    """A two-pixel cluster keeps the existing small-particle silhouette at 16px resolution."""
    for dx in (0, 1):
        for dy in (0, 1):
            pixels[x * 2 + dx, y * 2 + dy] = color


def particle_dust(i):
    im, p = canvas()
    shapes = [((3, 2, 255), (2, 3, 210), (3, 3, 235), (4, 3, 145), (3, 4, 160)),
              ((3, 3, 255), (4, 3, 190), (3, 4, 150), (4, 4, 110)),
              ((3, 3, 225), (4, 4, 140), (4, 3, 175)), ((3, 3, 190), (3, 4, 100))]
    for x, y, value in shapes[i]: particle_cluster(p, x, y, gray(value))
    return im


def particle_sparkle(i):
    im, p = canvas()
    reach = (1, 2, 3, 1)[i]
    for x, y in ((3, 3), (4, 3), (3, 4), (4, 4)):
        particle_cluster(p, x, y, gray(255, 255 if i < 3 else 190))
    for k in range(1, reach + 1):
        for x, y in ((3 - k, 3), (4 + k, 4), (3, 3 - k), (4, 4 + k)):
            if 0 <= x < 8 and 0 <= y < 8: particle_cluster(p, x, y, gray(245, max(50, 245 - k * 65)))
    if i == 2:
        for x, y in ((2, 2), (5, 5)): particle_cluster(p, x, y, gray(225, 120))
    return im


def particle_spark(i):
    im, p = canvas()
    for x, y, a in (((2, 2, 70), (3, 3, 180), (4, 4, 255), (4, 5, 155)) if i == 0 else
                    ((3, 3, 130), (4, 4, 230), (5, 5, 90))): particle_cluster(p, x, y, gray(255, a))
    return im


# ================================================================================================ villager outfit

def jeweler_outfit(zombie=False):
    """64px profession overlay using the actual villager torso, jacket and arm UV islands.

    Body front [22:30,26:38], jacket front [6:14,44:64], head front [8:16,8:18].
    Both variants share these islands; their folded/outstretched arm lengths differ.
    """
    im, _ = canvas(64, 64); d = ImageDraw.Draw(im)
    # Waistcoat: shoulders/top, front, sides and back of the 8x12x6 torso.
    d.rectangle((22, 20, 29, 25), fill=FELT[1])
    d.rectangle((22, 26, 29, 37), fill=FELT[2])
    d.rectangle((16, 26, 21, 37), fill=FELT[1]); d.rectangle((30, 26, 43, 37), fill=FELT[1])
    d.polygon([(24, 26), (27, 26), (26, 29), (25, 29)], fill='#cbb98e')
    d.line((22, 27, 24, 30), fill=FELT[3]); d.line((29, 27, 27, 30), fill=FELT[0])
    for y in (30, 33, 36):
        d.point((26, y), fill=BRASS[4]); d.point((27, y), fill=BRASS[1])
    d.line((36, 32, 43, 32), fill=LEATHER[1]); d.rectangle((39, 32, 40, 33), outline=BRASS[3])
    # Sleeves and cuffs: living villagers fold 8px arms, zombies extend 12px arms.
    arm_end = 37 if zombie else 33
    d.rectangle((44, 26, 59, arm_end), fill=FELT[1])
    d.line((48, 27, 48, arm_end - 2), fill=FELT[2])
    d.line((44, arm_end - 1, 59, arm_end - 1), fill=LEATHER[3])
    # Front apron wraps slightly around the jacket sides. Leave the lower hem irregular.
    d.rectangle((6, 45, 13, 60), fill=LEATHER[2])
    d.rectangle((4, 47, 5, 59), fill=LEATHER[1]); d.rectangle((14, 47, 15, 59), fill=LEATHER[1])
    d.line((6, 45, 13, 45), fill=LEATHER[4]); d.line((7, 46, 7, 58), fill=LEATHER[3])
    d.line((12, 46, 12, 59), fill=LEATHER[1]); d.line((7, 60, 12, 60), fill=LEATHER[4])
    for y in (47, 50, 53, 56):
        d.point((6, y), fill=LEATHER[4]); d.point((13, y), fill=LEATHER[3])
    # Lower pocket, a steel scribe and a small colored stone.
    d.rectangle((8, 52, 11, 56), fill=LEATHER[1]); d.line((8, 52, 11, 52), fill=LEATHER[4])
    d.line((9, 50, 9, 53), fill=STEEL[3]); d.point((11, 53), fill='#8cc9bd')
    d.point((10, 54), fill='#416d69')
    # Leather loupe band across all four head faces, raised brass rim over one eye.
    d.line((0, 10, 31, 10), fill=LEATHER[1]); d.line((0, 9, 31, 9), fill=LEATHER[3])
    d.rectangle((9, 10, 11, 12), fill=BRASS[1]); d.line((9, 10, 11, 10), fill=BRASS[4])
    d.rectangle((9, 11, 10, 12), fill='#7ca7a8'); d.point((9, 11), fill='#deeee2')
    if zombie:
        p = im.load()
        for y in range(64):
            for x in range(64):
                r, g, b, a = p[x, y]
                if a: p[x, y] = (int(r * .78), int(g * .85), int(b * .76), a)
        # Missing stitches and torn apron hem expose the base zombie skin.
        for box in ((6, 57, 6, 60), (9, 60, 10, 60), (13, 54, 13, 56)):
            d.rectangle(box, fill=(0, 0, 0, 0))
    return im


def logo():
    """The mod logo: a gold ring set with a brilliant ruby on deep velvet."""
    size = 128
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    p = im.load()
    for y in range(size):
        for x in range(size):
            d = math.hypot(x + 0.5 - 64, y + 0.5 - 64)
            if d <= 62:
                t = d / 62
                p[x, y] = (int(34 + 20 * (1 - t)), int(27 + 14 * (1 - t)), int(46 + 30 * (1 - t)), 255)
            if 60 <= d <= 62:
                p[x, y] = hexrgb('#c9a45c')
    # Enlarge the 16px ring art (nearest neighbour, exact pixels) and tint it.
    base, recess, high = frame_layers('ring')
    body, spark = stone_layers(*STONE_SEAT['ring'], 'brilliant')
    tints = [(base, (242, 197, 65)), (recess, (150, 110, 30)), (high, (255, 244, 210)), (body, (212, 33, 62)), (spark, (255, 230, 235))]
    art = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for layer, (r, g, b) in tints:
        tinted = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        lp, tp = layer.load(), tinted.load()
        for y in range(16):
            for x in range(16):
                v = lp[x, y]
                if v[3]:
                    tp[x, y] = (v[0] * r // 255, v[1] * g // 255, v[2] * b // 255, v[3])
        art.alpha_composite(tinted)
    big = art.resize((96, 96), Image.NEAREST)
    im.alpha_composite(big, (16, 18))
    return im


def workstation_plain():
    """Near-white base for the workstations' code-drawn parts (stones, frames, tools): tinted per vertex in the game.
    A faint, regular grain keeps large flat faces from looking like flat fill; it stays above 230 so tints keep their
    colour."""
    im, px = canvas()
    for y in range(16):
        for x in range(16):
            grain = ((x * 7 + y * 13) % 5) - 2
            ridge = 2 if (x + y * 3) % 11 == 0 else 0
            px[x, y] = gray(248 + grain * 2 - ridge)
    return im


def unfinished_tag():
    """A small paper work tag on a string: the overlay that marks unfinished jewelry."""
    im, px = canvas()
    paper = ['#8a7a5c', '#c8b88e', '#ece0bc']
    string = hexrgb('#5a4630')
    for i in range(5):
        px[11 - i, 2 + i] = string
    for y in range(6, 12):
        for x in range(4, 11):
            edge = x in (4, 10) or y in (6, 11)
            px[x, y] = hexrgb(paper[0] if edge else paper[2 if (x + y) % 4 else 1])
    px[5, 7] = hexrgb('#3a2e20')
    for x in range(6, 10):
        px[x, 8] = hexrgb('#7a6a50')
        px[x, 10] = hexrgb('#7a6a50')
    return im


# ================================================================================================ export table

def textures():
    out = {}
    for form in FRAMES:
        base, recess, high = frame_layers(form)
        out[f'item/{form}/frame.png'] = base
        out[f'item/{form}/recess.png'] = recess
        out[f'item/{form}/highlight.png'] = high
        cx, cy, r = STONE_SEAT[form]
        for cut in CUTS:
            body, spark = stone_layers(cx, cy, r, cut)
            out[f'item/{form}/gem_{cut}.png'] = body
            out[f'item/{form}/sparkle_{cut}.png'] = spark
    for form, blank in [('ring', 'ring_blank'), ('amulet', 'amulet_frame'), ('bracelet', 'bracelet_blank'), ('circlet', 'circlet_frame')]:
        base, high = blank_layers(form)
        out[f'item/blank/{blank}.png'] = base
        out[f'item/blank/{blank}_highlight.png'] = high
    body, sheen = rough_layers()
    out['item/gem/rough.png'] = body
    out['item/gem/rough_sheen.png'] = sheen
    for cut in CUTS:
        body, spark = stone_layers(8.0, 8.0, 6.2 if cut != 'emerald' else 6.8, cut)
        out[f'item/gem/cut_{cut}.png'] = body
        out[f'item/gem/cut_{cut}_sparkle.png'] = spark
    out['item/jewelers_pliers.png'] = pliers(False)
    out['item/fine_pliers.png'] = pliers(True)
    out['item/emery_lap.png'] = lap('emery')
    out['item/diamond_lap.png'] = lap('diamond')
    out['item/polishing_compound.png'] = polishing_compound()
    out['item/gem_dust.png'] = gem_dust()
    out['item/design_sketch.png'] = design_sketch()
    out['item/jewelers_handbook.png'] = handbook()
    out['block/bench_wood.png'] = planks(WOOD, 3, True)
    out['block/bench_top.png'] = planks(WOOD, 3)
    out['block/tool_materials.png'] = tool_materials()
    out['block/bench_wood_end.png'] = end_grain(WALNUT, 3)
    out['block/bench_leather.png'] = cloth(LEATHER, 5)
    out['block/bench_felt.png'] = cloth(FELT, 7)
    out['block/bench_steel.png'] = metal(STEEL, 9)
    out['block/bench_brass.png'] = metal(BRASS, 11)
    out['block/bench_gems.png'] = gems_block()
    out['block/bench_pin.png'] = pin()
    out['block/wheel_wood.png'] = planks(WOOD, 13, True)
    out['block/wheel_wood_end.png'] = end_grain(WOOD, 13)
    out['block/wheel_basin.png'] = basin()
    out['block/wheel_water.png'] = water()
    out['block/wheel_iron.png'] = metal(IRON, 15)
    out['block/wheel_copper.png'] = metal(COPPER, 17)
    out['block/wheel_lap.png'] = lap_face()
    out['block/wheel_rim.png'] = rim()
    out['block/workstation_plain.png'] = workstation_plain()
    out['item/unfinished_tag.png'] = unfinished_tag()
    for kind in ['ring', 'necklace', 'bracelet', 'head']:
        out[f'gui/slot/empty_{kind}.png'] = slot_icon(kind)
    for i in range(4):
        out[f'particle/gem_dust_{i}.png'] = particle_dust(i)
        out[f'particle/gem_sparkle_{i}.png'] = particle_sparkle(i)
    for i in range(2):
        out[f'particle/metal_spark_{i}.png'] = particle_spark(i)
    out['entity/villager/profession/jeweler.png'] = jeweler_outfit(False)
    out['entity/zombie_villager/profession/jeweler.png'] = jeweler_outfit(True)
    # Enforce the art direction on every export, including --check and preview runs.
    for name, image in out.items():
        expected = (64, 64) if name.startswith('entity/') else (16, 16)
        if image.size != expected:
            raise ValueError(f'{name}: expected {expected}, got {image.size}')
    return out


def extras():
    """Files outside the textures folder: the mod logo."""
    return {'logo.png': logo()}
