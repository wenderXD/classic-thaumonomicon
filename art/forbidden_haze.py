"""The warp haze the research map lays under forbidden research (textures/gui/forbidden.png).

Thaumcraft 4 drew this from its tainted node's aura: a broad, wispy cloud, tinted near-black purple
at runtime and drawn eighty pixels across on a twenty-four pixel grid, so it washes over the
neighbouring research. The redrawn node sheet's tainted core is a small bright point that hides
entirely under a plate at that size, so this draws a haze of its own instead.

A strip of 32 frames, 64 px each. Each frame is a soft disc of light grey wisps: spiral arms made of
a few angular harmonics, bent by the radius so they curl, and moved by one whole period per loop so
the strip cycles seamlessly. The runtime plays the frames backwards, which carries the arms inward.
Colour is near white so the runtime tint decides it; all the shape is in the alpha.

Run:  python art/forbidden_haze.py
"""

import os

import numpy as np
from PIL import Image

FRAMES = 32
CELL = 64
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets",
                   "classic_thaumonomicon", "textures", "gui", "forbidden.png")

def harmonics(rng):
    """Smoke as a sum of angular waves: frequencies 2-17, weights falling as 1/k so the large
    billows dominate, each wave twisted by a random number of turns across the disc so the billows
    curl. Integer frequencies, and time advancing each wave by whole periods per loop, keep every
    frame of the strip continuous with the next."""
    waves = []
    for k in range(2, 18):
        waves.append((k, rng.uniform(-1.8, 1.8), 1.0 / k, rng.uniform(0, 2 * np.pi), int(rng.integers(1, 3))))
    return waves


def frame(t, waves):
    yy, xx = np.mgrid[0:CELL, 0:CELL].astype(float)
    dx = xx - (CELL - 1) / 2.0
    dy = yy - (CELL - 1) / 2.0
    r = np.hypot(dx, dy) / (CELL / 2.0)
    theta = np.arctan2(dy, dx)
    smoke = np.zeros_like(r)
    total = 0.0
    for k, twist, weight, phase, speed in waves:
        smoke += weight * np.sin(k * theta + 2 * np.pi * (twist * r - speed * t / FRAMES) + phase)
        total += weight
    smoke = np.clip((smoke / total) * 2.2 + 0.5, 0.0, 1.0)
    # Solid under the plate, thinning to nothing at the rim; the billows carry it outward unevenly.
    body = np.clip(1.0 - r, 0.0, 1.0) ** 0.75
    alpha = body * (0.25 + 0.75 * smoke) * 1.35
    alpha = np.where(r >= 1.0, 0.0, alpha)
    light = 0.78 + 0.22 * smoke
    rgba = np.zeros((CELL, CELL, 4))
    rgba[..., 0] = light
    rgba[..., 1] = light
    rgba[..., 2] = light
    rgba[..., 3] = alpha
    return (np.clip(rgba, 0.0, 1.0) * 255.0 + 0.5).astype(np.uint8)


def main():
    waves = harmonics(np.random.default_rng(440055))
    strip = np.concatenate([frame(t, waves) for t in range(FRAMES)], axis=1)
    Image.fromarray(strip, "RGBA").save(OUT)
    print("wrote", os.path.normpath(OUT), strip.shape[1], "x", strip.shape[0])


if __name__ == "__main__":
    main()
