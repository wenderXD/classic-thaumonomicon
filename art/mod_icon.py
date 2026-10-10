"""Draws the mod icon (src/main/resources/logo.png).

The icon is the research map in miniature: the carved wooden frame, the violet nebula in its
window, and the Thaumonomicon in the middle with a new-research sparkle at its corner.

The frame, nebula and sparkle come from the pixel maps below, on a 64 px grid. The result is scaled
10x with no smoothing, which is the pixel size of Thaumaturge's own logo. The book is Thaumaturge's
Thaumonomicon item, read from the Thaumaturge jar and pasted in the middle at twice its size.

Run:  python art/mod_icon.py [--out PATH]
"""

import argparse
import glob
import io
import os
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
THAUMATURGE_JAR = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/curse.maven/thaumaturge-1628024/*/*/thaumaturge-1628024-*.jar")

GRID = 64
SCALE = 10
BORDER = 9
WINDOW = GRID - 2 * BORDER

COLOURS = {
    # wood, dark to light
    "0": "#170c09", "1": "#2b1a14", "2": "#44281c", "3": "#5b3927", "4": "#765036", "5": "#946b4a",
    # nebula, deep to bright, then its blue cloud and its stars
    "a": "#1f0d33", "c": "#35125a", "e": "#541b80", "f": "#7c26a3", "h": "#aa3bc0",
    "x": "#33288a", "z": "#4d3fb5", "s": "#f4e4ff", "t": "#b891e6",
    # brass
    "g": "#ad6e0b", "G": "#d8a425", "y": "#f1c352",
    # sparkle
    "W": "#ffffff", "q": "#c9f4ff", "Q": "#7fd8f5",
}

# One horizontal board: edge line, highlight row, five rows of grain, shadow row, edge line.
# The side posts are the same board transposed.
BOARD = [
    "0000000000000000000000000000000000000000000000",
    "5555554555555555544555555555554555555555445555",
    "3333334444433333333333333333333333334444433333",
    "3222333333333333344444433333333333333333333223",
    "3333333333333333333333333333444443333333333333",
    "2222233333344444333333333333333333333333322222",
    "3333333333333333333334444433333333333333333333",
    "2222222222122222222222222221222222222222212222",
    "0000000000000000000000000000000000000000000000",
]

# Corner block with a brass stud.
CORNER = [
    "000000000",
    "055555540",
    "054444320",
    "0543y3320",
    "054yGg320",
    "0543g3320",
    "053333220",
    "042222220",
    "000000000",
]

# Rune glyphs, 5 px tall.
RUNES = {
    "gate": ["#.#", "#.#", "###", "#.#", "#.#"],
    "bolt": ["..#", ".#.", "###", ".#.", "#.."],
    "eye": ["..#..", ".#.#.", "#.#.#", ".#.#.", "..#.."],
    "peak": [".#.", "#.#", "#.#", "###", "#.#"],
    "hook": ["###", "#..", "#.#", "#..", "###"],
    "glass": ["###", "#.#", ".#.", "#.#", "###"],
    "fork": ["#.#.#", "#.#.#", ".###.", "..#..", "..#.."],
    "cup": ["#.#", "#.#", "#.#", "#.#", ".#."],
    "key": [".#.", "#.#", ".#.", "###", ".#."],
    "branch": ["#.#", "#.#", ".#.", ".#.", ".#."],
}

# Runes on each side, as two words like the in-game frame.
TOP = (["gate", "bolt", "eye", "gate"], ["peak", "cup", "key"])
BOTTOM = (["glass", "fork", "branch"], ["hook", "eye", "gate", "peak"])
LEFT = (["peak", "hook"], ["bolt", "glass", "cup"])
RIGHT = (["branch", "eye"], ["key", "gate", "fork"])

# Nebula clouds as discs (x, y, radius) in window pixels. Each list sets what it covers to the
# next brighter tone. DARK darkens the corners.
CLOUDS = [
    ("e", [(23, 22, 22), (6, 10, 7), (40, 36, 8), (38, 6, 6), (8, 40, 7)]),
    ("f", [(23, 22, 18), (4, 22, 5), (8, 26, 4), (6, 17, 3), (41, 21, 5), (38, 27, 4), (43, 15, 3),
           (33, 41, 4), (38, 39, 3), (28, 42, 3), (12, 3, 4), (17, 2, 3), (30, 3, 3)]),
    ("h", [(23, 22, 16), (4, 22, 3), (7, 25, 3), (41, 21, 3), (39, 25, 3), (33, 42, 3), (36, 40, 2)]),
]
DARK = [(0, 45, 6), (45, 0, 5), (0, 0, 4), (45, 45, 5)]
BLUE = [("x", [(6, 38, 5), (11, 41, 3), (3, 33, 3)]), ("z", [(5, 38, 3), (8, 40, 2)])]
STARS = [
    (3, 5, "s"), (41, 12, "s"), (4, 43, "t"), (43, 32, "s"), (21, 1, "t"), (29, 43, "s"),
    (2, 14, "t"), (16, 43, "t"), (42, 43, "t"), (36, 2, "t"), (2, 29, "s"),
]

# Thaumaturge's 16 px book, drawn at 2x and centred in the window.
BOOK = "assets/thaumaturge/textures/item/thaumonomicon.png"
BOOK_AT = (16, 16)

SPARKLE = [
    "....Q....",
    "....Q....",
    "....q....",
    "...qWq...",
    "QQqWWWqQQ",
    "...qWq...",
    "....q....",
    "....Q....",
    "....Q....",
]
SPARKLE_AT = (17, 17)


def thaumaturge_jar():
    jars = sorted(glob.glob(THAUMATURGE_JAR))
    if not jars:
        raise SystemExit("Thaumaturge's jar is not in the Gradle cache yet: run gradlew build once.")
    return jars[-1]


def from_jar(path):
    with zipfile.ZipFile(thaumaturge_jar()) as jar:
        return Image.open(io.BytesIO(jar.read(path))).convert("RGBA")


def rgb(colour):
    return tuple(int(colour[i:i + 2], 16) for i in (1, 3, 5))


def stamp(px, rows, x, y):
    """Draws a pixel map with its top-left corner at (x, y). Dots are transparent."""
    for dy, row in enumerate(rows):
        for dx, c in enumerate(row):
            if c != ".":
                px[x + dx, y + dy] = rgb(COLOURS[c])


def upright(rows):
    """Transposes a board, so its highlight row becomes the left column."""
    return ["".join(col) for col in zip(*rows)]


def carve(px, words, x, y, down):
    """Draws two words of runes on a board, centred along its length."""
    length = sum(5 if down else len(RUNES[name][0]) for word in words for name in word)
    length += sum(len(word) - 1 for word in words) + 4
    at = (WINDOW - length) // 2
    for word in words:
        for name in word:
            rows = RUNES[name]
            wide = len(rows[0])
            gx, gy = (x + (5 - wide) // 2, y + at) if down else (x + at, y)
            stamp(px, [row.replace("#", "1") for row in rows], gx, gy)
            at += (5 if down else wide) + 1
        at += 3


def frame(px):
    far = GRID - BORDER
    turned = [row[::-1] for row in BOARD]
    stamp(px, BOARD, BORDER, 0)
    stamp(px, turned, BORDER, far)
    stamp(px, upright(BOARD), 0, BORDER)
    stamp(px, upright(turned), far, BORDER)
    for x in (0, far):
        for y in (0, far):
            stamp(px, CORNER, x, y)
    carve(px, TOP, BORDER, 2, False)
    carve(px, BOTTOM, BORDER, far + 2, False)
    carve(px, LEFT, 2, BORDER, True)
    carve(px, RIGHT, far + 2, BORDER, True)


def sky(px):
    """Draws the nebula in the window: flat clouds from dark to bright behind the book, a blue
    cloud at the lower left, a few stars, and the frame's shadow along the top and left edges."""
    def inside(discs, x, y):
        return any((x - cx) ** 2 + (y - cy) ** 2 <= r * r for cx, cy, r in discs)

    for y in range(WINDOW):
        for x in range(WINDOW):
            c = "c"
            if inside(DARK, x, y):
                c = "a"
            for tone, discs in CLOUDS:
                if inside(discs, x, y):
                    c = tone
            for tone, discs in BLUE:
                if inside(discs, x, y) and c in "acexz":
                    c = tone
            if x == 0 or y == 0:
                c = {"c": "a", "e": "c", "f": "e", "h": "f", "x": "a", "z": "x"}.get(c, c)
            px[BORDER + x, BORDER + y] = rgb(COLOURS[c])
    for x, y, tone in STARS:
        px[BORDER + x, BORDER + y] = rgb(COLOURS[tone])


def build():
    for rows in (BOARD, CORNER, SPARKLE):
        assert len({len(row) for row in rows}) == 1, rows
    assert len(BOARD[0]) == WINDOW and len(BOARD) == BORDER
    icon = Image.new("RGB", (GRID, GRID))
    px = icon.load()
    sky(px)
    frame(px)
    book = from_jar(BOOK)
    book = book.resize((book.width * 2, book.height * 2), Image.NEAREST)
    icon.paste(book, BOOK_AT, book)
    stamp(px, SPARKLE, *SPARKLE_AT)
    return icon.resize((GRID * SCALE, GRID * SCALE), Image.NEAREST)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=os.path.join(ROOT, "src", "main", "resources", "logo.png"))
    args = parser.parse_args()
    icon = build()
    icon.save(args.out)
    print("wrote", os.path.normpath(args.out), icon.size)


if __name__ == "__main__":
    main()
