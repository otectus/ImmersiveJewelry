"""GeckoLib rigs of Immersive Jewelry: the editable source of every .geo.json, .animation.json and rig texture.

Everything here is authored in the painters' own coordinates (scene pixels of the north-facing block, x to the
worker's left, y up, z away from the worker; tools in their own frame with the working tip at the origin and the
handle along +z), and converted on export to GeckoLib's Bedrock convention, which mirrors x (GeckoLib's loader negates
x of origins, pivots and positions, and x/y of rotations). The Java side (client/presentation/geo) poses bones in game
terms, so it never needs to know about the mirror.

Rigs (bone names are the contract with client/presentation/geo/JewelryRigs.java and docs/ASSET_MANIFEST.md):

  lapidary_wheel   lap (the spinning disc, tinted by tier), hub (nut, cap, index mark), spindle, lever
  jewelers_bench   vise (base, plate, rod), screw, jaw_front, jaw_rear, posts_circlet, posts_bracelet
  bench_tools      pliers (jaw_left, jaw_right), tweezers (arm_left, arm_right), pusher, burnisher, brush (bristles),
                   cloth (pad)

Clips (sampled at an explicit phase by RigClip, never on GeckoLib's model-wide clock):

  lapidary_wheel   lap_seat (a new lap drops onto the spindle), lever_pull (the release lever's travel)
  jewelers_bench   clamp_close (jaws close on a piece and the screw turns)
  bench_tools      pliers_squeeze (jaws from open to closed), brush_dab (bristles splay), cloth_rub (the pad squashes)

Run tools/generate_rigs.py --write to export, --check (default) to compare with the committed files.
"""
import math
import random

from PIL import Image

TEXTURE_SIZE = 64
CELL = 8

# Material swatches: (base colour, noise amplitude, streak direction or None). One 8x8 cell each, in this order.
MATERIALS = [
    ('steel', (190, 198, 202), 10, 'x'),
    ('steel_light', (214, 222, 225), 8, 'x'),
    ('steel_dark', (110, 118, 122), 8, None),
    ('brass', (216, 192, 140), 12, 'y'),
    ('wood', (126, 86, 54), 14, 'z'),
    ('leather', (134, 74, 55), 10, None),
    ('felt', (178, 194, 173), 8, None),
    ('lap', (236, 236, 232), 14, None),
    ('mark', (60, 54, 48), 4, None),
    ('knob', (106, 58, 34), 8, None),
    ('cloth', (232, 224, 208), 8, None),
    ('cloth_fold', (208, 196, 176), 8, None),
    ('bristle', (82, 64, 48), 16, 'z'),
    ('rivet', (194, 204, 207), 6, None),
]
# The Fine Pliers' texture: blued steel and dark blue grips; every other swatch is the same.
FINE = {'leather': ((82, 106, 128), 10, None), 'steel': ((184, 200, 216), 10, 'x'),
        'steel_light': ((206, 218, 230), 8, 'x')}


def material_index(name):
    for i, entry in enumerate(MATERIALS):
        if entry[0] == name:
            return i
    raise KeyError(name)


def cell(name):
    i = material_index(name)
    per_row = TEXTURE_SIZE // CELL
    return (i % per_row) * CELL, (i // per_row) * CELL


def texture(variant=None):
    """The rig atlas: one noisy swatch per material (deterministic)."""
    img = Image.new('RGBA', (TEXTURE_SIZE, TEXTURE_SIZE), (0, 0, 0, 0))
    px = img.load()
    for index, (name, base, noise, streak) in enumerate(MATERIALS):
        if variant == 'fine' and name in FINE:
            base, noise, streak = FINE[name]
        rng = random.Random(1000 + index)
        u0, v0 = cell(name)
        lines = [rng.uniform(-1, 1) for _ in range(CELL)]
        for y in range(CELL):
            for x in range(CELL):
                n = rng.uniform(-1, 1) * noise * 0.5
                if streak == 'x':
                    n += lines[y] * noise * 0.6
                elif streak in ('y', 'z'):
                    n += lines[x] * noise * 0.6
                px[u0 + x, v0 + y] = tuple(max(0, min(255, int(round(c + n)))) for c in base) + (255,)
    return img


# ---------------------------------------------------------------------------------------------------- geometry

class Bone:
    def __init__(self, name, pivot=(0, 0, 0), parent=None, rotation=None):
        self.name = name
        self.pivot = pivot
        self.parent = parent
        self.rotation = rotation
        self.cubes = []

    def box(self, a, b, material, rotation=None, pivot=None):
        """An axis-aligned box from corner a to corner b (game pixels), optionally turned about pivot (game degrees)."""
        x0, y0, z0 = (min(a[i], b[i]) for i in range(3))
        x1, y1, z1 = (max(a[i], b[i]) for i in range(3))
        self.cubes.append(((x0, y0, z0), (x1, y1, z1), material, rotation, pivot))
        return self

    def centred(self, c, half, material, rotation=None, pivot=None):
        return self.box((c[0] - half[0], c[1] - half[1], c[2] - half[2]), (c[0] + half[0], c[1] + half[1], c[2] + half[2]),
                        material, rotation, pivot)


def r4(v):
    return round(float(v), 4) + 0.0


def mirror_point(p):
    return [r4(-p[0]), r4(p[1]), r4(p[2])]


def mirror_rotation(r):
    return [r4(-r[0]), r4(-r[1]), r4(r[2])]


def face_uv(material, w, h):
    u, v = cell(material)
    # A swatch region (inset by one texel) stretched over the face: material colour, not a pattern to align.
    return {'uv': [u + 1, v + 1], 'uv_size': [CELL - 2, CELL - 2]}


def cube_json(cube):
    (x0, y0, z0), (x1, y1, z1), material, rotation, pivot = cube
    size = [r4(x1 - x0), r4(y1 - y0), r4(z1 - z0)]
    out = {'origin': [r4(-x1), r4(y0), r4(z0)], 'size': size}
    if rotation:
        out['pivot'] = mirror_point(pivot if pivot else ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2))
        out['rotation'] = mirror_rotation(rotation)
    out['uv'] = {face: face_uv(material, 1, 1) for face in ('north', 'south', 'east', 'west', 'up', 'down')}
    return out


def geometry(name, bones, bounds=(2, 2)):
    out = []
    for bone in bones:
        entry = {'name': bone.name, 'pivot': mirror_point(bone.pivot)}
        if bone.parent:
            entry['parent'] = bone.parent
        if bone.rotation:
            entry['rotation'] = mirror_rotation(bone.rotation)
        if bone.cubes:
            entry['cubes'] = [cube_json(c) for c in bone.cubes]
        out.append(entry)
    return {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': 'geometry.immersive_jewelry.' + name,
                'texture_width': TEXTURE_SIZE,
                'texture_height': TEXTURE_SIZE,
                'visible_bounds_width': bounds[0],
                'visible_bounds_height': bounds[1],
                'visible_bounds_offset': [0, 1, 0],
            },
            'bones': out,
        }],
    }


# --- the Lapidary Wheel: lap (painter LAP = 9, 12.3, 8.5, radius 5), spindle, release lever (painter LEVER)

LAP = (9.0, 12.3, 8.5)
LAP_RADIUS = 5.0
LEVER = (1.8, 13.6, 10.0)


def lapidary_wheel():
    root = Bone('root')
    lap = Bone('lap', LAP, 'root')
    # A sixteen-sided disc: eight crossed slabs, each a chord pair, turned about the spindle.
    chord = LAP_RADIUS * math.tan(math.radians(11.25))
    for k in range(8):
        lap.box((LAP[0] - chord, LAP[1] - 0.5, LAP[2] - LAP_RADIUS), (LAP[0] + chord, LAP[1], LAP[2] + LAP_RADIUS), 'lap',
                rotation=(0, k * 22.5, 0), pivot=(LAP[0], LAP[1] - 0.25, LAP[2]))
    # The disc alone takes the lap's tier colour; the hub (nut, cap, index mark) keeps its own.
    hub = Bone('hub', LAP, 'lap')
    hub.box((LAP[0] - 0.6, LAP[1], LAP[2] - 0.6), (LAP[0] + 0.6, LAP[1] + 0.35, LAP[2] + 0.6), 'steel')
    hub.box((LAP[0] - 0.25, LAP[1] + 0.35, LAP[2] - 0.25), (LAP[0] + 0.25, LAP[1] + 0.5, LAP[2] + 0.25), 'rivet')
    # The index mark near the rim shows the disc turning (and coasting) even on a plain lap.
    hub.box((LAP[0] - 0.3, LAP[1], LAP[2] + 3.4), (LAP[0] + 0.3, LAP[1] + 0.03, LAP[2] + 4.3), 'mark')
    spindle = Bone('spindle', LAP, 'lap')
    spindle.box((LAP[0] - 0.4, 10.6, LAP[2] - 0.4), (LAP[0] + 0.4, LAP[1] - 0.5, LAP[2] + 0.4), 'steel_dark')
    lever = Bone('lever', LEVER, 'root', rotation=(-25, 0, 0))
    lever.box((LEVER[0] - 0.15, LEVER[1], LEVER[2] - 0.15), (LEVER[0] + 0.15, LEVER[1] + 1.6, LEVER[2] + 0.15), 'brass')
    lever.centred((LEVER[0], LEVER[1] + 1.7, LEVER[2]), (0.3, 0.3, 0.3), 'knob')
    lever.box((LEVER[0] - 0.3, LEVER[1] - 0.2, LEVER[2] - 0.3), (LEVER[0] + 0.3, LEVER[1] + 0.15, LEVER[2] + 0.3), 'steel_dark')
    return [root, lap, hub, spindle, lever]


# --- the Jeweler's Bench vise (painter BenchPainter.clamp)

def jewelers_bench():
    root = Bone('root')
    vise = Bone('vise', (8.0, 14.0, 8.0), 'root')
    vise.box((5.8, 14.0, 7.0), (10.2, 14.35, 9.0), 'steel')
    vise.box((6.5, 14.35, 7.45), (9.5, 14.65, 8.65), 'steel_light')
    vise.box((5.5, 14.32, 7.87), (10.5, 14.78, 8.33), 'steel')
    screw = Bone('screw', (10.4, 14.55, 8.1), 'vise')
    screw.centred((10.4, 14.55, 8.1), (0.25, 0.32, 0.32), 'brass')
    screw.box((10.4, 14.05, 7.95), (10.7, 15.05, 8.25), 'brass')
    screw.centred((10.55, 15.1, 8.1), (0.2, 0.12, 0.2), 'knob')
    screw.centred((10.55, 14.0, 8.1), (0.2, 0.12, 0.2), 'knob')
    front = Bone('jaw_front', (8.0, 14.6, 7.3), 'vise')
    front.box((7.0, 14.6, 7.15), (9.0, 15.17, 7.45), 'steel_light')
    front.box((7.05, 14.85, 7.37), (8.95, 15.27, 7.55), 'felt')
    rear = Bone('jaw_rear', (8.0, 14.6, 8.2), 'vise')
    rear.box((7.0, 14.6, 7.9), (9.0, 15.17, 8.45), 'steel')
    rear.box((7.05, 14.85, 7.75), (8.95, 15.27, 8.02), 'felt')
    circlet = Bone('posts_circlet', (8.0, 14.35, 8.3), 'vise')
    for side in (-1, 1):
        x = 8.0 + side * 2.05
        circlet.box((x - 0.2, 14.35, 8.15), (x + 0.2, 17.75, 8.5), 'steel')
        circlet.box((x - 0.38, 17.35, 7.5), (x + 0.38, 17.8, 8.25), 'felt')
    bracelet = Bone('posts_bracelet', (8.0, 14.35, 8.1), 'vise')
    for side in (-1, 1):
        x = 8.0 + side * 1.55
        bracelet.box((x - 0.18, 14.35, 7.95), (x + 0.18, 15.7, 8.35), 'steel')
        bracelet.box((x - 0.33, 15.55, 7.5), (x + 0.33, 15.82, 8.2), 'felt')
    return [root, vise, screw, front, rear, circlet, bracelet]


# --- the bench tools, each in its own frame: tip at the origin, handle along +z (painter Tools.orient)

def bench_tools():
    root = Bone('root')
    bones = [root]
    pliers = Bone('pliers', (0, 0, 0), 'root')
    pliers.centred((0, 0, 1.4), (0.35, 0.23, 0.35), 'steel_dark')
    pliers.centred((0, 0, 1.4), (0.19, 0.27, 0.19), 'rivet')
    bones.append(pliers)
    for name, side in (('jaw_left', -1), ('jaw_right', 1)):
        jaw = Bone(name, (0, 0, 1.4), 'pliers')
        jaw.box((side * 0.12 - 0.08, -0.14, 0.0), (side * 0.12 + 0.08, 0.14, 1.05), 'steel_light')
        jaw.box((side * 0.15 - 0.2, -0.18, 1.05), (side * 0.15 + 0.2, 0.18, 1.75), 'steel')
        jaw.box((side * 0.15 - 0.15, -0.18, 1.75), (side * 0.15 + 0.15, 0.18, 4.8), 'steel')
        jaw.box((side * 0.15 - 0.2, -0.26, 3.6), (side * 0.15 + 0.2, 0.26, 4.8), 'leather')
        bones.append(jaw)

    tweezers = Bone('tweezers', (0, 0, 0), 'root')
    tweezers.box((-0.26, -0.07, 3.0), (0.26, 0.07, 3.3), 'steel')
    bones.append(tweezers)
    for name, side in (('arm_left', -1), ('arm_right', 1)):
        arm = Bone(name, (0, 0, 3.2), 'tweezers')
        # A straight arm from the joined end to the tip; the bone's turn about y sets the gap at the tip.
        arm.box((side * 0.02 - 0.05, -0.055, 0.0), (side * 0.02 + 0.05, 0.055, 1.6), 'steel_light')
        arm.box((side * 0.02 - 0.08, -0.06, 1.6), (side * 0.02 + 0.08, 0.06, 3.2), 'steel')
        arm.box((side * 0.02 - 0.085, 0.056, 1.65), (side * 0.02 + 0.085, 0.07, 2.3), 'steel_dark')
        bones.append(arm)

    pusher = Bone('pusher', (0, 0, 0), 'root')
    pusher.box((-0.24, -0.07, 0.0), (0.24, 0.07, 0.65), 'steel_light')
    pusher.box((-0.19, -0.19, 0.6), (0.19, 0.19, 1.4), 'steel')
    pusher.box((-0.22, -0.22, 1.2), (0.22, 0.22, 1.52), 'brass')
    pusher.box((-0.3, -0.3, 1.5), (0.3, 0.3, 3.4), 'wood')
    bones.append(pusher)

    burnisher = Bone('burnisher', (0, 0, 0), 'root')
    burnisher.box((-0.24, -0.1, 0.0), (0.24, 0.3, 0.58), 'steel_light', rotation=(-16, 0, 0), pivot=(0, 0, 0.3))
    burnisher.box((-0.2, -0.2, 0.55), (0.2, 0.2, 1.65), 'steel')
    burnisher.box((-0.19, -0.19, 1.5), (0.19, 0.19, 1.82), 'brass')
    burnisher.box((-0.26, -0.26, 1.82), (0.26, 0.26, 3.6), 'wood')
    bones.append(burnisher)

    brush = Bone('brush', (0, 0, 0), 'root')
    brush.box((-0.22, -0.13, 0.65), (0.22, 0.13, 1.1), 'brass')
    brush.box((-0.3, -0.3, 1.05), (0.3, 0.3, 3.2), 'wood')
    bones.append(brush)
    bristles = Bone('bristles', (0, 0, 0.7), 'brush')
    bristles.box((-0.26, -0.13, 0.03), (0.26, 0.13, 0.72), 'bristle')
    bones.append(bristles)

    cloth = Bone('cloth', (0, 0, 0), 'root')
    bones.append(cloth)
    pad = Bone('pad', (0, 0, 0), 'cloth')
    pad.box((-0.85, -0.85, -0.55), (0.85, 0.85, 0.05), 'cloth')
    pad.box((-0.4, -0.75, -0.62), (0.8, 0.45, -0.52), 'cloth_fold')
    bones.append(pad)
    return bones


RIGS = {
    'lapidary_wheel': lapidary_wheel,
    'jewelers_bench': jewelers_bench,
    'bench_tools': bench_tools,
}


# ---------------------------------------------------------------------------------------------------- animations

def keys(track):
    """Keyframes in game terms {seconds: (x, y, z) or ((x, y, z), easing)}."""
    return track


def channel(points, kind):
    out = {}
    for t, value in sorted(points.items()):
        easing = None
        if isinstance(value, tuple) and len(value) == 2 and isinstance(value[0], tuple):
            value, easing = value
        if kind == 'rotation':
            v = mirror_rotation(value)
        elif kind == 'position':
            v = mirror_point(value)
        else:
            v = [r4(c) for c in value]
        key = '%g' % t
        out[key] = {'vector': v, 'easing': easing} if easing else v
    return out


def clip(length, bones):
    out = {'animation_length': length, 'bones': {}}
    for bone, channels in bones.items():
        out['bones'][bone] = {kind: channel(points, kind) for kind, points in channels.items()}
    return out


def animations(name):
    if name == 'lapidary_wheel':
        clips = {
            'lap_seat': clip(0.4, {'lap': {
                'position': {0.0: (0, 2.2, 0), 0.25: ((0, 0.12, 0), 'easeOutQuad'), 0.32: (0, -0.05, 0), 0.4: (0, 0, 0)},
                'rotation': {0.0: (0, -30, 0), 0.4: ((0, 0, 0), 'easeOutCubic')}}}),
            'lever_pull': clip(1.0, {'lever': {
                'rotation': {0.0: (0, 0, 0), 0.6: ((-46, 0, 0), 'easeInOutSine'), 0.85: (-52, 0, 0), 1.0: (-50, 0, 0)}}}),
        }
    elif name == 'jewelers_bench':
        clips = {
            'clamp_close': clip(0.5, {
                'jaw_front': {'position': {0.0: (0, 0, -0.6), 0.4: ((0, 0, 0.02), 'easeInOutSine'), 0.5: (0, 0, 0)}},
                'jaw_rear': {'position': {0.0: (0, 0, 0.6), 0.4: ((0, 0, -0.02), 'easeInOutSine'), 0.5: (0, 0, 0)}},
                'screw': {'rotation': {0.0: (0, 0, 0), 0.5: ((-270, 0, 0), 'easeInOutSine')}}}),
        }
    else:
        clips = {
            'pliers_squeeze': clip(1.0, {
                'jaw_left': {'rotation': {0.0: (0, -25, 0), 0.7: ((0, -5, 0), 'easeInOutSine'), 1.0: (0, -3, 0)}},
                'jaw_right': {'rotation': {0.0: (0, 25, 0), 0.7: ((0, 5, 0), 'easeInOutSine'), 1.0: (0, 3, 0)}}}),
            'brush_dab': clip(1.0, {'bristles': {
                'scale': {0.0: (1, 1, 1), 0.6: ((1.5, 1.2, 0.82), 'easeOutQuad'), 1.0: (1.55, 1.25, 0.78)}}}),
            'cloth_rub': clip(1.0, {'pad': {
                'scale': {0.0: (1, 1, 1), 1.0: ((1.08, 1.08, 0.6), 'easeOutQuad')},
                'position': {0.0: (0, 0, 0), 1.0: ((0, 0, -0.1), 'easeOutQuad')}}}),
        }
    return {'format_version': '1.8.0', 'animations': clips}
