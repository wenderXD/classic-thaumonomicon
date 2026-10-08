"""The addon's icon for the mod list (src/main/resources/logo.png).

A corner of the classic research map: the rune-carved wooden frame round the violet nebula, cut
down to a square, with the Thaumonomicon lying on it as research does - lit from behind, tendrils
reaching out of it, a new-research sparkle at its corner - and Thaumaturge's own "TT" mark pressed
over the frame's corner like a seal, since this is Thaumaturge's book.

Drawn on a 128-pixel grid and scaled five times with no smoothing, so it lands at 640 px like
Thaumaturge's own logo and stays crisp.

Inputs:
  the frame, nebula and sparkle   this addon's textures (redrawn Thaumaturge's Legacy art)
  the book                        Thaumaturge's Thaumonomicon item, from the Thaumaturge jar
  the TT mark                     logo.png, from the Thaumaturge jar

Run:  python art/mod_icon.py [--out PATH]
"""

import argparse
import glob
import io
import os
import zipfile

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
GUI = os.path.join(ROOT, "src", "main", "resources", "assets", "classic_thaumonomicon", "textures", "gui")
THAUMATURGE_JAR = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/curse.maven/thaumaturge-1628024/*/*/thaumaturge-1628024-*.jar")

GRID = 128
SCALE = 5
BORDER = 16


def thaumaturge_jar():
    jars = sorted(glob.glob(THAUMATURGE_JAR))
    if not jars:
        raise SystemExit("Thaumaturge's jar is not in the Gradle cache yet: run gradlew build once.")
    return jars[-1]


def from_jar(path):
    with zipfile.ZipFile(thaumaturge_jar()) as jar:
        return Image.open(io.BytesIO(jar.read(path))).convert("RGBA")


def frame():
    """The book's pane shrunk to a square by cutting out its middle: the four corner blocks and
    the ends of each board and post, runes and all, at the sheet's own pixel size."""
    pane = np.array(Image.open(os.path.join(GUI, "book_frame.png")).convert("RGBA"))[:230, :256]
    half = GRID // 2
    cols = np.r_[0:half, 256 - half:256]
    rows = np.r_[0:half, 230 - half:230]
    return Image.fromarray(np.ascontiguousarray(pane[rows][:, cols]))


def nebula():
    """The window's sky: the bright heart of the nebula, sampled at half size as the map does."""
    sky = Image.open(os.path.join(GUI, "book_nebula.png")).convert("RGBA")
    return sky.crop((160, 120, 160 + 2 * GRID, 120 + 2 * GRID)).resize((GRID, GRID), Image.BOX)


def shadow(img, alpha=0.55):
    a = np.array(img).astype(float)
    a[..., :3] = 0
    a[..., 3] *= alpha
    return Image.fromarray(a.astype(np.uint8))


def glow(cx, cy, radius, rgb, strength):
    """A soft round light, for the book to float in front of."""
    yy, xx = np.mgrid[0:GRID, 0:GRID].astype(float)
    r = np.hypot(xx - cx, yy - cy) / radius
    a = np.clip(1.0 - r, 0.0, 1.0) ** 1.8 * strength
    out = np.zeros((GRID, GRID, 4))
    out[..., :3] = rgb
    out[..., 3] = a * 255.0
    return Image.fromarray(out.astype(np.uint8))


def wave_line(canvas, x0, y0, x1, y1, rgb, phase):
    """A waving tendril reaching out of the book, brightest where it leaves and fading away."""
    px = canvas.load()
    steps = int(np.hypot(x1 - x0, y1 - y0) * 3)
    nx, ny = -(y1 - y0), x1 - x0
    norm = max(1e-6, float(np.hypot(nx, ny)))
    for i in range(steps + 1):
        t = i / steps
        off = 2.5 * np.sin(t * 7.0 + phase) * (1.0 - t) + 6.0 * t * (1 - t)
        x = x0 + (x1 - x0) * t + nx / norm * off
        y = y0 + (y1 - y0) * t + ny / norm * off
        xi, yi = int(round(x)), int(round(y))
        if 0 <= xi < GRID and 0 <= yi < GRID:
            k = 0.85 * (1.0 - t) ** 0.8
            r, g, b, _ = px[xi, yi]
            px[xi, yi] = (int(r * (1 - k) + rgb[0] * k), int(g * (1 - k) + rgb[1] * k), int(b * (1 - k) + rgb[2] * k), 255)


def build():
    canvas = Image.new("RGBA", (GRID, GRID), (0, 0, 0, 0))
    canvas.alpha_composite(nebula())

    # Thaumaturge's 16 px book at four times its size.
    book = from_jar("assets/thaumaturge/textures/item/thaumonomicon.png").resize((64, 64), Image.NEAREST)
    bx, by = 22, 20
    cx, cy = bx + 32, by + 32

    # The sky darkened toward the frame, so the book and the seal stand out of it.
    sky = np.array(canvas).astype(float)
    yy, xx = np.mgrid[0:GRID, 0:GRID].astype(float)
    falloff = np.clip(np.hypot(xx - cx, yy - cy) / 80.0, 0.0, 1.0)
    sky[..., :3] *= (1.0 - 0.6 * falloff ** 1.5)[..., None]
    canvas = Image.fromarray(sky.astype(np.uint8))

    # The book lies on the map as research does: light behind it, tendrils reaching out of it.
    canvas.alpha_composite(glow(cx, cy, 44, (255, 220, 255), 0.9))
    wave_line(canvas, cx, cy, 112, 22, (0, 255, 0), 0.0)
    wave_line(canvas, cx, cy, 14, 108, (60, 90, 255), 1.7)
    wave_line(canvas, cx, cy, 104, 64, (0, 255, 0), 3.1)
    canvas.alpha_composite(frame())

    canvas.alpha_composite(shadow(book), (bx + 3, by + 3))
    canvas.alpha_composite(book, (bx, by))

    # The new-research sparkle at the book's upper corner, as the map marks fresh research.
    sparkle = Image.open(os.path.join(GUI, "sparkle.png")).convert("RGBA").crop((0, 0, 16, 16)).resize((32, 32), Image.NEAREST)
    canvas.alpha_composite(sparkle, (bx - 12, by - 12))

    # Thaumaturge's TT at its own pixel size, pressed over the frame's corner like a seal.
    logo = from_jar("logo.png").resize((64, 64), Image.NEAREST)
    seal = logo.crop(logo.getbbox())
    sx, sy = GRID - seal.width - 3, GRID - seal.height - 3
    canvas.alpha_composite(shadow(seal, 0.7), (sx + 3, sy + 3))
    canvas.alpha_composite(seal, (sx, sy))
    return canvas.resize((GRID * SCALE, GRID * SCALE), Image.NEAREST)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=os.path.join(ROOT, "src", "main", "resources", "logo.png"))
    args = parser.parse_args()
    icon = build()
    icon.save(args.out)
    print("wrote", os.path.normpath(args.out), icon.size)


if __name__ == "__main__":
    main()
