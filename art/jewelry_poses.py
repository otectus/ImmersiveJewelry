"""The jeweler's body clips for playerAnimator (assets/immersive_jewelry/player_animation/<clip>.json).

Small, deliberate upper-body work suited to jewelry: a bent torso over the wheel or bench, forearms close to the work,
the right hand on the tool and the left steadying the piece or the table. Legs are never keyed (ordinary movement and
stance stay with the game), and the head only follows the work lightly.

Every clip has a `_reduced` twin (calmer: angles drawn towards rest, loops of smaller amplitude) for the bodyAnimation
REDUCED option and reduced motion. Clip ids are the contract with client/presentation/anim/JewelerClip.java.

Angles are degrees in playerAnimator's terms (pitch: negative raises the arm forward).
"""

PARTS = ('head', 'torso', 'rightArm', 'leftArm')

# name: (length in ticks, loop, keyframes {tick: {part: (pitch, yaw, roll)}})
WHEEL_READY = {'torso': (10, 0, 0), 'head': (16, 0, 0), 'rightArm': (-52, -12, 2), 'leftArm': (-36, 22, -4)}
BENCH_READY = {'torso': (16, 0, 0), 'head': (20, 0, 0), 'rightArm': (-46, -14, 3), 'leftArm': (-44, 16, -3)}


def merge(base, **parts):
    out = dict(base)
    out.update(parts)
    return out


CLIPS = {
    # --- the Lapidary Wheel
    'wheel_ready': (20, True, {0: WHEEL_READY, 10: merge(WHEEL_READY, head=(17, 0, 0)), 20: WHEEL_READY}),
    'wheel_grind': (24, True, {
        0: merge(WHEEL_READY, torso=(13, -3, 0), rightArm=(-56, -18, 2)),
        12: merge(WHEEL_READY, torso=(13, 3, 0), rightArm=(-60, -4, 1)),
        24: merge(WHEEL_READY, torso=(13, -3, 0), rightArm=(-56, -18, 2))}),
    'wheel_lift': (10, False, {0: merge(WHEEL_READY, rightArm=(-56, -12, 2)),
                               10: merge(WHEEL_READY, head=(8, 0, 0), rightArm=(-68, -10, 2))}),
    'wheel_cool': (16, False, {0: WHEEL_READY, 8: merge(WHEEL_READY, rightArm=(-48, 18, 0), torso=(12, 8, 0)),
                               16: merge(WHEEL_READY, rightArm=(-48, 18, 0), torso=(12, 8, 0))}),
    'wheel_mount': (16, False, {0: WHEEL_READY, 6: merge(WHEEL_READY, rightArm=(-74, -4, 0), leftArm=(-60, 12, 0)),
                                12: merge(WHEEL_READY, rightArm=(-70, -6, 0), leftArm=(-56, 14, 0)), 16: WHEEL_READY}),
    'lap_change': (20, False, {0: WHEEL_READY, 6: merge(WHEEL_READY, rightArm=(-80, -6, 0), leftArm=(-80, 6, 0), torso=(18, 0, 0)),
                               14: merge(WHEEL_READY, rightArm=(-62, -6, 0), leftArm=(-62, 6, 0), torso=(18, 0, 0)), 20: WHEEL_READY}),
    'inspect_loupe': (20, True, {0: merge(WHEEL_READY, head=(6, -6, 0), rightArm=(-118, -32, 6)),
                                 20: merge(WHEEL_READY, head=(6, -6, 0), rightArm=(-118, -32, 6))}),
    # --- the Jeweler's Bench
    'bench_ready': (24, True, {0: BENCH_READY, 12: merge(BENCH_READY, head=(21, 0, 0)), 24: BENCH_READY}),
    'tweezers_seat': (30, True, {0: merge(BENCH_READY, rightArm=(-50, -10, 6)),
                                 15: merge(BENCH_READY, rightArm=(-53, -6, 4), head=(22, 0, 0)),
                                 30: merge(BENCH_READY, rightArm=(-50, -10, 6))}),
    'pliers_squeeze': (16, True, {0: merge(BENCH_READY, rightArm=(-48, -12, 8), leftArm=(-42, 18, -6)),
                                  8: merge(BENCH_READY, rightArm=(-49, -12, 16), leftArm=(-42, 18, -6)),
                                  16: merge(BENCH_READY, rightArm=(-48, -12, 8), leftArm=(-42, 18, -6))}),
    'bezel_push': (32, True, {0: merge(BENCH_READY, rightArm=(-46, -20, 4)), 8: merge(BENCH_READY, rightArm=(-52, -12, 4)),
                              16: merge(BENCH_READY, rightArm=(-48, -2, 4)), 24: merge(BENCH_READY, rightArm=(-42, -12, 4)),
                              32: merge(BENCH_READY, rightArm=(-46, -20, 4))}),
    'polish_wipe': (20, True, {0: merge(BENCH_READY, rightArm=(-46, -22, 2), torso=(16, -2, 0)),
                               10: merge(BENCH_READY, rightArm=(-48, -4, 2), torso=(16, 2, 0)),
                               20: merge(BENCH_READY, rightArm=(-46, -22, 2), torso=(16, -2, 0))}),
    'burnish_pass': (24, True, {0: merge(BENCH_READY, rightArm=(-40, -14, 4)), 12: merge(BENCH_READY, rightArm=(-56, -12, 4)),
                                24: merge(BENCH_READY, rightArm=(-40, -14, 4))}),
    'patina_dab': (14, True, {0: merge(BENCH_READY, rightArm=(-50, -12, 2)), 5: merge(BENCH_READY, rightArm=(-44, -12, 2)),
                              14: merge(BENCH_READY, rightArm=(-50, -12, 2))}),
    'pry_stone': (20, True, {0: merge(BENCH_READY, rightArm=(-50, -12, 10), leftArm=(-46, 18, -8)),
                             10: merge(BENCH_READY, rightArm=(-52, -14, -4), leftArm=(-46, 18, -8)),
                             20: merge(BENCH_READY, rightArm=(-50, -12, 10), leftArm=(-46, 18, -8))}),
    'repair_true': (22, True, {0: merge(BENCH_READY, rightArm=(-42, -18, 4)), 11: merge(BENCH_READY, rightArm=(-54, -6, 4)),
                               22: merge(BENCH_READY, rightArm=(-42, -18, 4))}),
    'bench_place': (14, False, {0: BENCH_READY, 6: merge(BENCH_READY, rightArm=(-68, -4, 0), torso=(20, 0, 0)), 14: BENCH_READY}),
    'bench_collect': (14, False, {0: BENCH_READY, 6: merge(BENCH_READY, rightArm=(-70, -6, 0), torso=(20, 0, 0)),
                                  14: merge(BENCH_READY, rightArm=(-30, -8, 0), torso=(12, 0, 0))}),
    'inspect_piece': (30, False, {0: BENCH_READY, 10: merge(BENCH_READY, rightArm=(-78, -18, 0), leftArm=(-78, 18, 0), head=(-2, 0, 0)),
                                  24: merge(BENCH_READY, rightArm=(-80, -14, 8), leftArm=(-80, 22, -8), head=(-2, 6, 0)),
                                  30: merge(BENCH_READY, rightArm=(-78, -18, 0), leftArm=(-78, 18, 0), head=(-2, 0, 0))}),
}


def reduced(frames, loop):
    """Halve each pose's departure from the first frame's pose, and soften the first frame towards a calm stand."""
    first = frames[min(frames)]
    out = {}
    for tick, pose in frames.items():
        calm = {}
        for part in PARTS:
            a = pose.get(part, (0, 0, 0))
            b = first.get(part, (0, 0, 0))
            calm[part] = tuple(round(b[i] * 0.8 + (a[i] - b[i]) * 0.5, 2) for i in range(3))
        out[tick] = calm
    return out


def emote(name, length, loop, frames):
    moves = []
    for tick in sorted(frames):
        for part in PARTS:
            if part not in frames[tick]:
                continue
            pitch, yaw, roll = frames[tick][part]
            moves.append({'tick': tick, 'easing': 'linear' if tick == 0 else 'easeInOutSine',
                          part: {'pitch': pitch, 'yaw': yaw, 'roll': roll}})
    return {
        'version': 3,
        'name': name,
        'description': 'Immersive Jewelry: ' + name.replace('_', ' '),
        'author': 'Immersive Jewelry',
        'contact': 0,
        'emote': {
            'beginTick': 0,
            'endTick': length,
            'stopTick': length if loop else length + 4,
            'isLoop': loop,
            'returnTick': 0,
            'degrees': True,
            'easeBeforeKeyframe': True,
            'moves': moves,
        },
    }


def all_clips():
    out = {}
    for name, (length, loop, frames) in CLIPS.items():
        out[name] = emote(name, length, loop, frames)
        out[name + '_reduced'] = emote(name + '_reduced', length, loop, reduced(frames, loop))
    return out
