"""Editable sound source for Immersive Jewelry: every sound is synthesised here, deterministically.

The audio identity is precision work, not smithing (spec 53): an abrasive wheel and its grinding hiss, glassy chips,
small metallic clicks of pliers, the seat of a stone, bright chimes for finished work. No hammer blows.
Each function returns mono float samples in -1..1 at RATE. tools/generate_sounds.py encodes them to OGG Vorbis.
"""
import math

import numpy as np

RATE = 44100


def noise(n, seed):
    """Deterministic white noise (a fixed linear congruential generator, not a random source)."""
    out = np.empty(n, dtype=np.float64)
    state = (seed * 2654435761 + 12345) & 0xFFFFFFFF
    for i in range(n):
        state = (1664525 * state + 1013904223) & 0xFFFFFFFF
        out[i] = state / 2147483648.0 - 1.0
    return out


def t(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def env(n, attack, decay_rate, hold=0.0):
    x = np.arange(n) / RATE
    a = np.clip(x / max(attack, 1e-4), 0, 1)
    d = np.where(x < attack + hold, 1.0, np.exp(-(x - attack - hold) * decay_rate))
    return a * d


def lowpass(x, cutoff):
    """A simple one-pole low-pass (applied twice for a gentler slope)."""
    k = 1.0 - math.exp(-2 * math.pi * cutoff / RATE)
    y = np.empty_like(x)
    for _ in range(2):
        acc = 0.0
        for i in range(len(x)):
            acc += k * (x[i] - acc)
            y[i] = acc
        x = y.copy()
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def bandpass(x, low, high):
    return highpass(lowpass(x, high), low)


def tone(freq, seconds, decay, partials=((1.0, 1.0),), phase=0.0):
    x = t(seconds)
    out = np.zeros_like(x)
    for ratio, amp in partials:
        out += amp * np.sin(2 * math.pi * freq * ratio * x + phase)
    return out * np.exp(-x * decay)


def pad(x, seconds):
    n = int(seconds * RATE)
    return np.concatenate([x, np.zeros(max(0, n - len(x)))])


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


def at(x, seconds, total):
    out = np.zeros(int(total * RATE))
    start = int(seconds * RATE)
    end = min(len(out), start + len(x))
    out[start:end] += x[:end - start]
    return out


def normalize(x, peak=0.85):
    m = np.max(np.abs(x))
    return x if m == 0 else x * (peak / m)


def fade(x, fade_in=0.004, fade_out=0.02):
    n = len(x)
    fi = int(fade_in * RATE)
    fo = int(fade_out * RATE)
    if fi:
        x[:fi] *= np.linspace(0, 1, fi)
    if fo:
        x[-fo:] *= np.linspace(1, 0, fo)
    return x


GLASS = ((1.0, 1.0), (2.76, 0.45), (5.4, 0.25), (8.93, 0.12))
BELL = ((1.0, 1.0), (2.0, 0.5), (2.98, 0.3), (4.1, 0.15), (5.43, 0.08))
METAL = ((1.0, 1.0), (1.59, 0.6), (2.14, 0.4), (2.92, 0.2))


# ================================================================================================ lapidary wheel

def wheel_mount():
    thunk = lowpass(noise(int(0.12 * RATE), 1), 900) * env(int(0.12 * RATE), 0.002, 40)
    tick = tone(3200, 0.08, 70, GLASS) * 0.25
    return normalize(mix(thunk * 1.4, tick), 0.7)


def wheel_spin():
    n = int(0.9 * RATE)
    x = np.arange(n) / RATE
    hum = np.sin(2 * math.pi * (70 + 60 * x) * x) * 0.4
    whoosh = bandpass(noise(n, 2), 400, 2400) * (x / 0.9) ** 1.5
    return normalize(fade(mix(hum, whoosh) * np.minimum(1, (0.9 - x) * 6), 0.01, 0.1), 0.6)


def wheel_grind(variant):
    """The abrasive hiss, steady so the client can loop it; each variant has its own grit."""
    n = int(0.7 * RATE)
    x = np.arange(n) / RATE
    grit = bandpass(noise(n, 10 + variant), 900 + 150 * variant, 5200)
    rasp = np.sign(np.sin(2 * math.pi * (38 + 6 * variant) * x)) * 0.12 * bandpass(noise(n, 20 + variant), 200, 1200)
    wobble = 0.8 + 0.2 * np.sin(2 * math.pi * (5.5 + variant) * x)
    return normalize(fade(mix(grit * wobble, rasp), 0.01, 0.01), 0.55)


def wheel_chip(variant):
    crack = highpass(noise(int(0.05 * RATE), 30 + variant), 2000) * env(int(0.05 * RATE), 0.0005, 120)
    ring = tone(2600 + 500 * variant, 0.35, 16, GLASS) * 0.5
    skitter = at(tone(4100 + 300 * variant, 0.06, 60, GLASS) * 0.25, 0.09, 0.4)
    return normalize(mix(crack, ring, skitter), 0.85)


def wheel_facet():
    return normalize(mix(tone(2900, 0.25, 22, GLASS) * 0.6, highpass(noise(int(0.02 * RATE), 40), 3000) * env(int(0.02 * RATE), 0.001, 200) * 0.4), 0.5)


def wheel_finish():
    total = 1.2
    notes = [1046.5, 1318.5, 1568.0, 2093.0]
    parts = [at(tone(f, 0.9, 5, BELL) * (0.9 - 0.1 * i), 0.09 * i, total) for i, f in enumerate(notes)]
    return normalize(fade(mix(*parts), 0.002, 0.2), 0.7)


def wheel_lap():
    clink = tone(1850, 0.5, 11, METAL) * 0.7
    thud = lowpass(noise(int(0.08 * RATE), 50), 600) * env(int(0.08 * RATE), 0.002, 50)
    return normalize(mix(clink, thud), 0.7)


# ================================================================================================ jeweler's bench

def bench_place():
    tap = lowpass(noise(int(0.09 * RATE), 60), 1400) * env(int(0.09 * RATE), 0.001, 60)
    ting = tone(2400, 0.3, 18, METAL) * 0.3
    return normalize(mix(tap, ting), 0.65)


def bench_prong(variant):
    """Pliers closing a prong: a squeeze then a tiny metallic click."""
    click = highpass(noise(int(0.015 * RATE), 70 + variant), 2500) * env(int(0.015 * RATE), 0.0005, 300)
    ping = tone(4200 + 350 * variant, 0.12, 45, METAL) * 0.45
    return normalize(mix(at(click, 0.0, 0.14), at(ping, 0.004, 0.14)), 0.55)


def bench_seat():
    first = tone(3300, 0.1, 50, GLASS) * 0.5 + at(highpass(noise(int(0.01 * RATE), 80), 2500) * 0.4, 0, 0.1)[:int(0.1 * RATE)]
    second = tone(2200, 0.3, 20, GLASS) * 0.6
    return normalize(mix(at(first, 0, 0.45), at(second, 0.07, 0.45)), 0.65)


def bench_bend():
    n = int(0.5 * RATE)
    x = np.arange(n) / RATE
    creak = np.sin(2 * math.pi * (320 - 120 * x) * x + 3 * np.sin(2 * math.pi * 23 * x)) * env(n, 0.03, 5)
    grit = bandpass(noise(n, 90), 600, 2000) * env(n, 0.02, 8) * 0.3
    return normalize(mix(creak, grit), 0.55)


def bench_polish():
    n = int(0.8 * RATE)
    x = np.arange(n) / RATE
    swish = bandpass(noise(n, 100), 1500, 7000) * np.sin(math.pi * x / 0.8) ** 2
    shimmer = at(tone(3500, 0.4, 9, BELL) * 0.15, 0.45, 0.8)
    return normalize(mix(swish, shimmer), 0.5)


def bench_finish():
    total = 1.4
    notes = [1568.0, 1975.5, 2349.3, 3136.0, 2637.0]
    parts = [at(tone(f, 1.0, 4.5, BELL) * (0.8 if i < 4 else 0.6), 0.07 * i, total) for i, f in enumerate(notes)]
    return normalize(fade(mix(*parts), 0.002, 0.25), 0.7)


def bench_repair():
    tap1 = tone(1700, 0.2, 30, METAL) * 0.6
    tap2 = tone(1950, 0.3, 22, METAL) * 0.5
    return normalize(mix(at(tap1, 0, 0.5), at(tap2, 0.16, 0.5)), 0.6)


# ================================================================================================ items, effects, interface

def jewelry_equip():
    total = 0.6
    times = [0.0, 0.05, 0.11, 0.2]
    freqs = [3100, 3700, 3400, 4300]
    parts = [at(tone(f, 0.25, 26, METAL) * (0.6 - 0.08 * i), s, total) for i, (s, f) in enumerate(zip(times, freqs))]
    return normalize(mix(*parts), 0.55)


def sketch_learn():
    n = int(0.35 * RATE)
    rustle = bandpass(noise(n, 110), 1800, 6000) * env(n, 0.02, 9) * 0.6
    chime = at(tone(1318.5, 0.9, 5, BELL) * 0.5, 0.15, 1.0)
    return normalize(mix(rustle, chime), 0.6)


def effect_trigger(variant):
    total = 0.7
    base = 2093.0 if variant == 0 else 2349.3
    parts = [at(tone(base * r, 0.5, 8, BELL) * a, s, total) for r, a, s in [(1.0, 0.6, 0.0), (1.5, 0.4, 0.04), (2.0, 0.3, 0.08)]]
    return normalize(mix(*parts), 0.5)


def effect_ward():
    low = tone(220, 1.2, 3, ((1.0, 1.0), (2.0, 0.4), (3.0, 0.2)))
    high = at(tone(1760, 0.9, 4, BELL) * 0.4, 0.05, 1.2)
    return normalize(fade(mix(low * 0.7, high), 0.01, 0.2), 0.6)


def effect_sense():
    ping = tone(1567.98, 0.6, 7, BELL)
    echo = at(ping * 0.35, 0.18, 1.0)
    return normalize(mix(ping, echo), 0.5)


def ui_cue():
    return normalize(fade(tone(1500, 0.09, 30, ((1.0, 1.0), (2.0, 0.2))), 0.002, 0.03), 0.5)


def ui_fail():
    a = tone(180, 0.18, 22, ((1.0, 1.0), (2.1, 0.3)))
    b = tone(150, 0.22, 20, ((1.0, 1.0), (2.1, 0.3)))
    return normalize(mix(at(a, 0, 0.4), at(b, 0.12, 0.4)), 0.5)


def villager_work(variant):
    total = 0.8
    parts = [at(bench_prong(variant + 1) * 0.5, 0.0, total), at(bench_prong(variant) * 0.45, 0.18, total),
             at(tone(2637.0 if variant == 0 else 2349.3, 0.4, 9, BELL) * 0.25, 0.36, total)]
    return normalize(mix(*parts), 0.5)


def sounds():
    """Sound file path (relative to assets/immersive_jewelry/sounds, without .ogg) to samples."""
    out = {
        'block/lapidary_wheel/mount': wheel_mount(),
        'block/lapidary_wheel/spin': wheel_spin(),
        'block/lapidary_wheel/facet': wheel_facet(),
        'block/lapidary_wheel/finish': wheel_finish(),
        'block/lapidary_wheel/lap': wheel_lap(),
        'block/jewelers_bench/place': bench_place(),
        'block/jewelers_bench/seat': bench_seat(),
        'block/jewelers_bench/bend': bench_bend(),
        'block/jewelers_bench/polish': bench_polish(),
        'block/jewelers_bench/finish': bench_finish(),
        'block/jewelers_bench/repair': bench_repair(),
        'item/jewelry/equip': jewelry_equip(),
        'item/design_sketch/learn': sketch_learn(),
        'jewelry/effect/ward': effect_ward(),
        'jewelry/effect/sense': effect_sense(),
        'ui/cue': ui_cue(),
        'ui/fail': ui_fail(),
    }
    for v in range(3):
        out[f'block/lapidary_wheel/grind_{v + 1}'] = wheel_grind(v)
        out[f'block/jewelers_bench/prong_{v + 1}'] = bench_prong(v)
    for v in range(2):
        out[f'block/lapidary_wheel/chip_{v + 1}'] = wheel_chip(v)
        out[f'jewelry/effect/trigger_{v + 1}'] = effect_trigger(v)
        out[f'entity/villager/work_jeweler_{v + 1}'] = villager_work(v)
    return out
