"""Generates the possessed-face overlay textures.

Each mob gets PNGs with the same size/UV layout as its normal texture:
  <mob>_face.png  - eyes, angry brows, the grin and fangs (drawn with normal lighting, from stage II)
  <mob>_eyes.png  - just the eyes (drawn fullbright, so they glow in the dark; they flicker in stage I)
  <mob>_blood.png - tears of blood, a gash on the brow, blood at the mouth (stage III only)

Faces are drawn from the ASCII grids below. Edit them to change the look:
  .  transparent (the mob's own face shows through)
  W  glowing white eye
  D  dark outline (brows, lips, grin corners)
  T  tooth          t  tooth, slightly darker (so separate teeth read)
  F  fang (a long tooth hanging over the lip)
  R  fresh blood    r  dark, dried blood        (these go in the _blood layer)

Usage:
  python3 gen_textures.py <assets/possessedgrin dir>
  python3 gen_textures.py <assets dir> --preview out.png minecraft=<vanilla textures/entity dir> requiem=<requiem textures/entity dir>
"""
import os
import sys
from PIL import Image

COLORS = {
    "W": (255, 255, 255, 255),
    "D": (26, 6, 10, 255),
    "T": (236, 232, 218, 255),
    "t": (206, 200, 184, 255),
    "F": (244, 240, 226, 255),
    "R": (176, 10, 18, 255),
    "r": (96, 4, 10, 255),
}
BLOOD = {"R", "r"}

# --- face grids --------------------------------------------------------------

# 8x8 heads: zombies, skeletons (eyes on row 4)
FACE_8x8 = [
    "....r...",
    "...R....",
    ".D....D.",
    ".WD..DW.",
    ".WW..WW.",
    "DR....RD",
    "DTtTtTtD",
    "rDFDDFDr",
]

# 8x8 enderman head: its eyes are wider (3 px)
FACE_ENDERMAN = [
    "....r...",
    "...R....",
    "DD....DD",
    "WWD..DWW",
    "WWW..WWW",
    "DR....RD",
    "DTtTtTtD",
    "rDFDDFDr",
]

# 8x10 heads: villagers, wandering trader, witch, illagers, zombie villager
# (eyes on row 6, nose covers the middle two columns from row 7 down)
FACE_VILLAGER = [
    "........",
    "....r...",
    "...R....",
    "..r.....",
    "DD....DD",
    ".WDDDDW.",
    ".WW..WW.",
    "DR....RD",
    "DTtTtTtD",
    "rDFDDFDr",
]

# 10x8 heads: piglins (snout covers columns 3-6 rows 4-7, tusks at columns 2 and 7)
FACE_PIGLIN = [
    ".....r....",
    ".D..R...D.",
    ".WDD..DDW.",
    ".WW....WW.",
    ".R......R.",
    "D........D",
    "DT......TD",
    "rD......Dr",
]
# front of the piglin snout (4x4): the grin carries on across its bottom
SNOUT_PIGLIN = [
    "....",
    "....",
    "D..D",
    "TFFt",
]

# 5x5 vex head
FACE_VEX = [
    ".....",
    "D...D",
    "WD.DW",
    "WW.WW",
    "DTtTD",
]

# --- mob list ----------------------------------------------------------------
# (namespace, id, texture size, [(x, y, grid), ...], texture used for the preview)
HEAD = (8, 8)

def head(grid):
    return [(HEAD[0], HEAD[1], grid)]

PIGLIN_PARTS = [(8, 8, FACE_PIGLIN), (32, 2, SNOUT_PIGLIN)]

MOBS = [
    # undead
    ("minecraft", "zombie",             (64, 64),  head(FACE_8x8),      "zombie/zombie.png"),
    ("minecraft", "husk",               (64, 64),  head(FACE_8x8),      "zombie/husk.png"),
    ("minecraft", "drowned",            (64, 64),  head(FACE_8x8),      "zombie/drowned.png"),
    ("minecraft", "giant",              (64, 64),  head(FACE_8x8),      None),
    ("minecraft", "skeleton",           (64, 32),  head(FACE_8x8),      "skeleton/skeleton.png"),
    ("minecraft", "stray",              (64, 32),  head(FACE_8x8),      "skeleton/stray.png"),
    ("minecraft", "wither_skeleton",    (64, 32),  head(FACE_8x8),      "skeleton/wither_skeleton.png"),
    ("minecraft", "zombie_villager",    (64, 64),  head(FACE_VILLAGER), "zombie_villager/zombie_villager.png"),
    ("minecraft", "zombified_piglin",   (64, 64),  PIGLIN_PARTS,        "piglin/zombified_piglin.png"),
    # villager folk
    ("minecraft", "villager",           (64, 64),  head(FACE_VILLAGER), "villager/villager.png"),
    ("minecraft", "wandering_trader",   (64, 64),  head(FACE_VILLAGER), "wandering_trader.png"),
    ("minecraft", "witch",              (64, 128), head(FACE_VILLAGER), "witch.png"),
    ("requiem",   "cured_villager",     (64, 64),  head(FACE_VILLAGER), "cured_villager.png"),
    # illagers
    ("minecraft", "pillager",           (64, 64),  head(FACE_VILLAGER), "illager/pillager.png"),
    ("minecraft", "vindicator",         (64, 64),  head(FACE_VILLAGER), "illager/vindicator.png"),
    ("minecraft", "evoker",             (64, 64),  head(FACE_VILLAGER), "illager/evoker.png"),
    ("minecraft", "illusioner",         (64, 64),  head(FACE_VILLAGER), "illager/illusioner.png"),
    # piglins
    ("minecraft", "piglin",             (64, 64),  PIGLIN_PARTS,        "piglin/piglin.png"),
    ("minecraft", "piglin_brute",       (64, 64),  PIGLIN_PARTS,        "piglin/piglin_brute.png"),
    ("requiem",   "cured_piglin",       (64, 64),  PIGLIN_PARTS,        "cured_piglin.png"),
    ("requiem",   "cured_piglin_brute", (64, 64),  PIGLIN_PARTS,        "cured_piglin_brute.png"),
    # others
    ("minecraft", "enderman",           (64, 32),  head(FACE_ENDERMAN), "enderman/enderman.png"),
    ("minecraft", "vex",                (32, 32),  [(5, 5, FACE_VEX)],  "illager/vex.png"),
]


# --- decals for every other mob (stretched over the front of the head) -------
# (file name, grid, eyes-only glow copy?)
DRAGON_EYES = [
    "................",
    ".DD..........DD.",
    ".WWD........DWW.",
    ".WWWD......DWWW.",
    "..WWWD....DWWW..",
    "...WW......WW...",
    "...Rr......rR...",
]
DRAGON_GRIN = [
    "D..........D",
    "DD........DD",
    ".DTtTtTtTtD.",
    "..DtTtTtTD..",
    "...DFDDFD...",
]
DECALS = [
    ("generic_face", FACE_8x8),         # any mob without a hand-made face
    ("dragon_eyes", DRAGON_EYES),       # Ender Dragon skull
    ("dragon_grin", DRAGON_GRIN),       # Ender Dragon snout tip
]
# glow-only copies (just the W pixels), drawn fullbright
GLOW_DECALS = [("generic_eyes", FACE_8x8), ("dragon_eyes_glow", DRAGON_EYES)]
# blood-only copies (just the R/r pixels), stage III
BLOOD_DECALS = [("generic_blood", FACE_8x8), ("dragon_blood", DRAGON_EYES)]


# --- the Ouija board item (16x16): wooden board, letter arcs, YES/NO marks, planchette ---
OUIJA_COLORS = {
    "b": (78, 50, 28, 255),     # dark edge
    "B": (128, 86, 50, 255),    # wood
    "w": (112, 74, 42, 255),    # wood grain
    "L": (232, 212, 170, 255),  # painted letters
    "P": (242, 238, 226, 255),  # planchette
    "p": (36, 26, 20, 255),     # planchette window
}
OUIJA = [
    "................",
    "..bbbbbbbbbbbb..",
    ".bBBBBBBBBBBBBb.",
    ".bBLBBwBBwBBLBb.",
    ".bBBBLBLBLBLBBb.",
    ".bBBLBBBBBBBLBb.",
    ".bwLBBBPPPBBBLb.",
    ".bBBBBPPpPPBBBb.",
    ".bBLBBBPPPBBBLb.",
    ".bBBLBBBPBBBLwb.",
    ".bBBBLBLBLBLBBb.",
    ".bBwBBBBBBBBBBb.",
    ".bBBBLLLLLLBBBb.",
    ".bBBBBBBwBBBBBb.",
    "..bbbbbbbbbbbb..",
    "................",
]


def paint_ouija(out_dir):
    d = os.path.join(out_dir, "textures", "item")
    os.makedirs(d, exist_ok=True)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(OUIJA):
        assert len(row) == 16, row
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), OUIJA_COLORS[ch])
    img.save(os.path.join(d, "ouija_board.png"))


def paint(size, parts):
    """Returns (face, eyes, blood, has_blood)."""
    face = Image.new("RGBA", size, (0, 0, 0, 0))
    eyes = Image.new("RGBA", size, (0, 0, 0, 0))
    blood = Image.new("RGBA", size, (0, 0, 0, 0))
    has_blood = False
    for ox, oy, grid in parts:
        width = len(grid[0])
        for y, row in enumerate(grid):
            assert len(row) == width, f"ragged grid row: {row!r}"
            for x, ch in enumerate(row):
                if ch == ".":
                    continue
                if ch in BLOOD:
                    blood.putpixel((ox + x, oy + y), COLORS[ch])
                    has_blood = True
                    continue
                face.putpixel((ox + x, oy + y), COLORS[ch])
                if ch == "W":
                    eyes.putpixel((ox + x, oy + y), COLORS[ch])
    return face, eyes, blood, has_blood


# --- high-detail painting (4x the mob texture's resolution) ------------------------------------------------
# The model's UVs don't care about texture size, so a 4x overlay sits exactly on the mob, with finer detail:
# sunken eye sockets, veined whites, separate shaded teeth, tapering fangs, blood that runs and drips.
SCALE = 4
import random as _random
import zlib

def _cells(parts):
    """{(x, y): char} in mob-texture pixels, for every non-transparent grid cell."""
    cells = {}
    for ox, oy, grid in parts:
        for y, row in enumerate(grid):
            for x, ch in enumerate(row):
                if ch != ".":
                    cells[(ox + x, oy + y)] = ch
    return cells

def _put(img, x, y, rgba):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), rgba)

def _blend(img, x, y, rgba):
    if 0 <= x < img.width and 0 <= y < img.height:
        under = img.getpixel((x, y))
        if under[3] == 0:
            img.putpixel((x, y), rgba)
        else:
            a = rgba[3] / 255.0
            img.putpixel((x, y), tuple(int(under[i] * (1 - a) + rgba[i] * a) for i in range(3)) + (max(under[3], rgba[3]),))

def paint_hd(size, parts, seed):
    """Returns (face, eyes, blood, has_blood) at SCALE times the resolution."""
    rnd = _random.Random(seed)
    S = SCALE
    w, h = size[0] * S, size[1] * S
    face = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    eyes = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    blood = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    cells = _cells(parts)
    has_blood = any(c in BLOOD for c in cells.values())

    def is_(x, y, chars):
        c = cells.get((x, y))
        return c is not None and c in chars

    # 1. sunken sockets: a soft bruise-dark ring around the eyes, on transparent cells only
    for (cx, cy), ch in cells.items():
        if ch != "W":
            continue
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                nx, ny = cx + dx, cy + dy
                if (nx, ny) in cells:
                    continue
                for sx in range(S):
                    for sy in range(S):
                        # distance (in subpixels) from this subpixel to the eye cell
                        px, py = nx * S + sx, ny * S + sy
                        ex = min(max(px, cx * S), cx * S + S - 1)
                        ey = min(max(py, cy * S), cy * S + S - 1)
                        d = max(abs(px - ex), abs(py - ey))
                        if d <= 2:
                            _blend(face, px, py, (40, 12, 30, 150 if d == 1 else 80))

    for (cx, cy), ch in cells.items():
        x0, y0 = cx * S, cy * S
        if ch == "W":
            for sx in range(S):
                for sy in range(S):
                    edge_l = sx == 0 and not is_(cx - 1, cy, "W")
                    edge_r = sx == S - 1 and not is_(cx + 1, cy, "W")
                    edge_t = sy == 0 and not is_(cx, cy - 1, "W")
                    edge_b = sy == S - 1 and not is_(cx, cy + 1, "W")
                    edge = edge_l or edge_r or edge_t or edge_b
                    _put(face, x0 + sx, y0 + sy, (226, 222, 214, 255) if edge else (252, 252, 246, 255))
                    _put(eyes, x0 + sx, y0 + sy, (170, 170, 170, 255) if edge else (255, 255, 255, 255))
            # thin red veins creeping in from the sides of the eye
            if rnd.random() < 0.6:
                side = rnd.choice((0, S - 1))
                vy = y0 + rnd.randrange(S)
                _put(face, x0 + side, vy, (178, 34, 40, 255))
                if rnd.random() < 0.5:
                    _put(face, x0 + (1 if side == 0 else S - 2), vy + rnd.choice((-1, 0, 1)), (200, 70, 70, 255))
        elif ch == "D":
            for sx in range(S):
                for sy in range(S):
                    v = rnd.randrange(-4, 5)
                    _put(face, x0 + sx, y0 + sy, (26 + v, 6 + max(0, v), 10 + max(0, v), 255))
        elif ch in ("T", "t"):
            base = (236, 228, 202) if ch == "T" else (214, 204, 178)
            for sx in range(S):
                for sy in range(S):
                    if sy == 0:
                        c = (122, 22, 32, 255)                      # gum
                    elif sx == S - 1 and is_(cx + 1, cy, "Tt"):
                        c = (70, 30, 30, 255)                       # gap between teeth
                    elif sy == S - 1:
                        c = tuple(int(v * 0.85) for v in base) + (255,)
                    else:
                        c = base + (255,)
                    _put(face, x0 + sx, y0 + sy, c)
        elif ch == "F":
            # a fang: tapers to a point, shaded on one side, over the dark lip
            for sx in range(S):
                for sy in range(S):
                    _put(face, x0 + sx, y0 + sy, (26, 6, 10, 255))
            widths = [(0, 4), (0, 3), (1, 3), (1, 2)]
            for sy, (a, b) in enumerate(widths):
                for sx in range(a, b):
                    shade = (244, 240, 226, 255) if sx < b - 1 else (200, 194, 176, 255)
                    _put(face, x0 + sx, y0 + sy, shade)
        elif ch in BLOOD:
            fresh = ch == "R"
            col = (176, 10, 18, 255) if fresh else (110, 6, 12, 255)
            dark = (110, 4, 10, 255) if fresh else (64, 2, 6, 255)
            if not fresh:
                # a cut: a ragged diagonal slash, dark in the middle, wet red along its edge
                for i in range(S):
                    sx, sy = S - 1 - i, i
                    _put(blood, x0 + sx, y0 + sy, dark)
                    _put(blood, x0 + sx, y0 + sy + 1 if sy + 1 < S else y0 + sy, col)
                _put(blood, x0 + S - 1, y0, (200, 40, 40, 255))
                end_x, end_y = x0, y0 + S - 1
            else:
                # a tear of blood: a thin streak down the cell, wobbling slightly, with a wet highlight
                mid = 1 + rnd.randrange(2)
                for sy in range(S):
                    _put(blood, x0 + mid, y0 + sy, col)
                    _put(blood, x0 + mid + 1, y0 + sy, dark)
                    if sy > 0 and rnd.random() < 0.25:
                        mid = max(0, min(S - 2, mid + rnd.choice((-1, 1))))
                _put(blood, x0 + mid, y0, (232, 70, 70, 255))
                end_x, end_y = x0 + mid, y0 + S - 1
            # if nothing is below, it keeps running and ends in a round drop
            if (cx, cy + 1) not in cells and (fresh or rnd.random() < 0.6):
                run = 1 + rnd.randrange(3)
                for sy in range(1, run + 1):
                    _put(blood, end_x, end_y + sy, col)
                _put(blood, end_x, end_y + run + 1, col)
                _put(blood, end_x + 1, end_y + run + 1, dark)
                _put(blood, end_x, end_y + run + 2, dark)
                _put(blood, end_x + 1, end_y + run + 2, dark)
    return face, eyes, blood, has_blood


# --- high-detail painting (4x the mob texture's resolution) ------------------------------------------------
# The model's UVs don't care about texture size, so a 4x overlay sits exactly on the mob, with finer detail:
# sunken eye sockets, veined whites, separate shaded teeth, tapering fangs, blood that runs and drips.
SCALE = 4
import random as _random
import zlib

def _cells(parts):
    """{(x, y): char} in mob-texture pixels, for every non-transparent grid cell."""
    cells = {}
    for ox, oy, grid in parts:
        for y, row in enumerate(grid):
            for x, ch in enumerate(row):
                if ch != ".":
                    cells[(ox + x, oy + y)] = ch
    return cells

def _put(img, x, y, rgba):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), rgba)

def _blend(img, x, y, rgba):
    if 0 <= x < img.width and 0 <= y < img.height:
        under = img.getpixel((x, y))
        if under[3] == 0:
            img.putpixel((x, y), rgba)
        else:
            a = rgba[3] / 255.0
            img.putpixel((x, y), tuple(int(under[i] * (1 - a) + rgba[i] * a) for i in range(3)) + (max(under[3], rgba[3]),))

def paint_hd(size, parts, seed):
    """Returns (face, eyes, blood, has_blood) at SCALE times the resolution."""
    rnd = _random.Random(seed)
    S = SCALE
    w, h = size[0] * S, size[1] * S
    face = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    eyes = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    blood = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    cells = _cells(parts)
    has_blood = any(c in BLOOD for c in cells.values())

    def is_(x, y, chars):
        c = cells.get((x, y))
        return c is not None and c in chars

    # 1. sunken sockets: a soft bruise-dark ring around the eyes, on transparent cells only
    for (cx, cy), ch in cells.items():
        if ch != "W":
            continue
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                nx, ny = cx + dx, cy + dy
                if (nx, ny) in cells:
                    continue
                for sx in range(S):
                    for sy in range(S):
                        # distance (in subpixels) from this subpixel to the eye cell
                        px, py = nx * S + sx, ny * S + sy
                        ex = min(max(px, cx * S), cx * S + S - 1)
                        ey = min(max(py, cy * S), cy * S + S - 1)
                        d = max(abs(px - ex), abs(py - ey))
                        if d <= 2:
                            _blend(face, px, py, (40, 12, 30, 150 if d == 1 else 80))

    for (cx, cy), ch in cells.items():
        x0, y0 = cx * S, cy * S
        if ch == "W":
            for sx in range(S):
                for sy in range(S):
                    edge_l = sx == 0 and not is_(cx - 1, cy, "W")
                    edge_r = sx == S - 1 and not is_(cx + 1, cy, "W")
                    edge_t = sy == 0 and not is_(cx, cy - 1, "W")
                    edge_b = sy == S - 1 and not is_(cx, cy + 1, "W")
                    edge = edge_l or edge_r or edge_t or edge_b
                    _put(face, x0 + sx, y0 + sy, (226, 222, 214, 255) if edge else (252, 252, 246, 255))
                    _put(eyes, x0 + sx, y0 + sy, (170, 170, 170, 255) if edge else (255, 255, 255, 255))
            # thin red veins creeping in from the sides of the eye
            if rnd.random() < 0.6:
                side = rnd.choice((0, S - 1))
                vy = y0 + rnd.randrange(S)
                _put(face, x0 + side, vy, (178, 34, 40, 255))
                if rnd.random() < 0.5:
                    _put(face, x0 + (1 if side == 0 else S - 2), vy + rnd.choice((-1, 0, 1)), (200, 70, 70, 255))
        elif ch == "D":
            for sx in range(S):
                for sy in range(S):
                    v = rnd.randrange(-4, 5)
                    _put(face, x0 + sx, y0 + sy, (26 + v, 6 + max(0, v), 10 + max(0, v), 255))
        elif ch in ("T", "t"):
            base = (236, 228, 202) if ch == "T" else (214, 204, 178)
            for sx in range(S):
                for sy in range(S):
                    if sy == 0:
                        c = (122, 22, 32, 255)                      # gum
                    elif sx == S - 1 and is_(cx + 1, cy, "Tt"):
                        c = (70, 30, 30, 255)                       # gap between teeth
                    elif sy == S - 1:
                        c = tuple(int(v * 0.85) for v in base) + (255,)
                    else:
                        c = base + (255,)
                    _put(face, x0 + sx, y0 + sy, c)
        elif ch == "F":
            # a fang: tapers to a point, shaded on one side, over the dark lip
            for sx in range(S):
                for sy in range(S):
                    _put(face, x0 + sx, y0 + sy, (26, 6, 10, 255))
            widths = [(0, 4), (0, 3), (1, 3), (1, 2)]
            for sy, (a, b) in enumerate(widths):
                for sx in range(a, b):
                    shade = (244, 240, 226, 255) if sx < b - 1 else (200, 194, 176, 255)
                    _put(face, x0 + sx, y0 + sy, shade)
        elif ch in BLOOD:
            fresh = ch == "R"
            col = (176, 10, 18, 255) if fresh else (96, 4, 10, 255)
            dark = (110, 4, 10, 255) if fresh else (60, 2, 6, 255)
            # a 2-subpixel streak down the middle of the cell, wobbling slightly, darker at its edges
            mid = 1 + rnd.randrange(2)
            for sy in range(S):
                _put(blood, x0 + mid, y0 + sy, col)
                _put(blood, x0 + mid + 1, y0 + sy, dark)
                if rnd.random() < 0.25:
                    mid = max(0, min(S - 2, mid + rnd.choice((-1, 1))))
            if fresh:
                _put(blood, x0 + mid, y0, (232, 70, 70, 255))       # a wet highlight
            # if nothing is below, the blood keeps running and ends in a drop
            if (cx, cy + 1) not in cells:
                run = 1 + rnd.randrange(3)
                for sy in range(run):
                    _put(blood, x0 + mid, y0 + S + sy, col)
                _put(blood, x0 + mid - 1, y0 + S + run, dark)
                _put(blood, x0 + mid, y0 + S + run, col)
                _put(blood, x0 + mid + 1, y0 + S + run, dark)
                _put(blood, x0 + mid, y0 + S + run + 1, dark)
    return face, eyes, blood, has_blood


def generate(out_dir):
    made = {}
    for ns, name, size, parts, _ in MOBS:
        d = os.path.join(out_dir, "textures", "entity", "possessed", ns)
        os.makedirs(d, exist_ok=True)
        face, eyes, blood, has_blood = paint_hd(size, parts, seed=zlib.crc32(name.encode()))
        face.save(os.path.join(d, f"{name}_face.png"))
        eyes.save(os.path.join(d, f"{name}_eyes.png"))
        if has_blood:
            blood.save(os.path.join(d, f"{name}_blood.png"))
        made[(ns, name)] = (face, blood)
    d = os.path.join(out_dir, "textures", "entity", "possessed")
    for name, grid in DECALS:
        face = paint_hd((len(grid[0]), len(grid)), [(0, 0, grid)], seed=zlib.crc32(name.encode()))[0]
        face.save(os.path.join(d, f"{name}.png"))
    for name, grid in GLOW_DECALS:
        eyes = paint_hd((len(grid[0]), len(grid)), [(0, 0, grid)], seed=1)[1]
        eyes.save(os.path.join(d, f"{name}.png"))
    for name, grid in BLOOD_DECALS:
        blood = paint_hd((len(grid[0]), len(grid)), [(0, 0, grid)], seed=zlib.crc32(name.encode()))[2]
        blood.save(os.path.join(d, f"{name}.png"))
    paint_ouija(out_dir)
    paint_skull(out_dir)
    paint_true_face(out_dir)
    paint_idol(out_dir)
    return made


# --- v7: the skull face, the demon's true face, the idol ---------------------

def _noise(w, h, cell, rnd):
    gw, gh = w // cell + 2, h // cell + 2
    grid = [[rnd.random() for _ in range(gw)] for _ in range(gh)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            fx, fy = x / cell, y / cell
            ix, iy = int(fx), int(fy)
            tx, ty = fx - ix, fy - iy
            a = grid[iy][ix] * (1 - tx) + grid[iy][ix + 1] * tx
            b = grid[iy + 1][ix] * (1 - tx) + grid[iy + 1][ix + 1] * tx
            out[y][x] = a * (1 - ty) + b * ty
    return out


def _mixc(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def _ellipse(x, y, cx, cy, rx, ry):
    return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2


def paint_skull(out_dir):
    """The face once the body has rotted to the bone: a cracked, pitted skull, deep sockets, bare teeth."""
    import random as _r
    rnd = _r.Random(zlib.crc32(b"skull"))
    N = 128
    skull = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    glow = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    big, fine = _noise(N, N, 18, rnd), _noise(N, N, 4, rnd)
    bone, light, shade, dark = (224, 214, 186), (246, 240, 220), (176, 162, 128), (104, 88, 64)
    sockets = [(38, 54, 17, 15), (90, 54, 17, 15)]
    for y in range(N):
        for x in range(N):
            # Outline: a rounded cranium narrowing to the jaw
            half = 60 if y < 70 else 60 - (y - 70) * 0.55
            if abs(x - 63.5) > half or y < 2 + max(0, (abs(x - 63.5) - 40)) * 0.6:
                continue
            edge = 1 - abs(x - 63.5) / max(1, half)
            t = 0.35 + 0.5 * edge + (big[y][x] - 0.5) * 0.5 + (fine[y][x] - 0.5) * 0.25 - (y / N) * 0.15
            c = _mixc(shade, bone, t * 1.4)
            c = _mixc(c, light, (t - 0.75) * 3)
            # Cheekbones: shadow under the sockets, light on the ridge
            for (cx, cy, rx, ry) in sockets:
                d = _ellipse(x, y, cx, cy + 20, rx * 1.1, 7)
                if d < 1:
                    c = _mixc(c, dark, (1 - d) * 0.5)
            a = 255
            if fine[y][x] > 0.86 and rnd.random() < 0.5:
                c = _mixc(c, dark, 0.7)        # pits
            skull.putpixel((x, y), c + (a,))
    px = skull.load()
    # The eye sockets: ragged, deep, black at the bottom
    for (cx, cy, rx, ry) in sockets:
        for y in range(cy - ry - 3, cy + ry + 4):
            for x in range(cx - rx - 3, cx + rx + 4):
                wob = (big[y][x] - 0.5) * 0.5
                d = _ellipse(x, y, cx, cy, rx, ry) + wob
                if d < 1.0:
                    c = _mixc((70, 52, 36), (6, 2, 2), (1 - d) * 2.2)
                    px[x, y] = c + (255,)
                elif d < 1.25:
                    r, g, b, a = px[x, y]
                    if a:
                        px[x, y] = _mixc((r, g, b), dark, 0.6) + (255,)
    # The nose: a dark hole, narrow at the top and wide at the bottom, split by a thin ridge of bone
    for y in range(64, 88):
        w = 2.0 + (y - 64) * 0.42
        for x in range(50, 78):
            dx = abs(x - 63.5)
            if dx >= w:
                continue
            if y > 79 and dx < 0.9:
                px[x, y] = shade + (255,)
            elif dx < w - 1.3:
                px[x, y] = _mixc((48, 34, 24), (4, 2, 2), (y - 64) / 14) + (255,)
            else:
                px[x, y] = dark + (255,)
    # Teeth: two rows, the jaw hanging a little open
    def teeth(top, height, count, x0, x1, upper):
        w = (x1 - x0) / count
        for i in range(count):
            tx0 = int(x0 + i * w)
            tx1 = int(x0 + (i + 1) * w) - 1
            for y in range(top, top + height):
                for x in range(tx0, tx1):
                    tip = (y - top) / height if upper else 1 - (y - top) / height
                    inset = int(tip * tip * 2)
                    if x < tx0 + inset or x >= tx1 - inset:
                        continue
                    c = _mixc((236, 226, 196), (170, 150, 110), tip * 0.8 + (fine[y][x] - 0.5) * 0.3)
                    px[x, y] = c + (255,)
                px[tx1, y] = (30, 18, 12, 255)
    for y in range(90, 112):
        for x in range(38, 90):
            if px[x, y][3]:
                px[x, y] = (8, 3, 3, 255)          # the dark of the mouth
    teeth(90, 9, 8, 40, 88, True)
    teeth(102, 9, 8, 42, 86, False)
    # Cracks running down from the crown
    for _ in range(4):
        x, y = rnd.randint(30, 98), rnd.randint(4, 14)
        for _ in range(rnd.randint(18, 34)):
            if 0 <= x < N and 0 <= y < N and px[x, y][3]:
                px[x, y] = (40, 26, 16, 255)
                if x + 1 < N and px[x + 1, y][3]:
                    px[x + 1, y] = _mixc(px[x + 1, y][:3], light, 0.6) + (255,)
            x += rnd.choice((-1, 0, 1))
            y += 1
    # Shreds of the last flesh clinging at the sides and jaw
    for _ in range(26):
        x = rnd.choice((rnd.randint(4, 22), rnd.randint(105, 123)))
        y = rnd.randint(40, 100)
        for k in range(rnd.randint(3, 9)):
            yy = y + k
            if yy < N and px[x, yy][3]:
                px[x, yy] = _mixc((132, 26, 30), (70, 8, 12), k / 9) + (250,)
    # Pinpoint eyes: tiny white-hot points with a red halo, deep in each socket
    gp = glow.load()
    for (cx, cy, rx, ry) in sockets:
        cy += 3
        for y in range(cy - 9, cy + 10):
            for x in range(cx - 9, cx + 10):
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                if d <= 1.6:
                    gp[x, y] = (255, 250, 236, 255)
                elif d <= 3.2:
                    gp[x, y] = (255, 120, 60, 255)
                elif d <= 9:
                    v = int(150 * (1 - (d - 3.2) / 5.8) ** 2)
                    gp[x, y] = (v, v // 8, 0, 255)
    d = os.path.join(out_dir, "textures", "entity", "possessed")
    os.makedirs(d, exist_ok=True)
    skull.save(os.path.join(d, "generic_skull.png"))
    glow.save(os.path.join(d, "generic_skull_eyes.png"))


def paint_true_face(out_dir):
    """The thing inside, for a few frames: ash-white skin split with black cracks, black eyes with burning
    slit pupils, a mouth torn wide with needle teeth. An original design."""
    import random as _r
    rnd = _r.Random(zlib.crc32(b"trueface"))
    N = 128
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    big, fine = _noise(N, N, 14, rnd), _noise(N, N, 3, rnd)
    px = img.load()
    for y in range(N):
        for x in range(N):
            t = 0.55 + (big[y][x] - 0.5) * 0.7 + (fine[y][x] - 0.5) * 0.3
            c = _mixc((150, 156, 140), (232, 230, 218), t)
            c = _mixc(c, (60, 70, 56), (big[y][x] - 0.72) * 3)      # grey-green mottling
            px[x, y] = c + (255,)
    eyes = [(38, 50), (90, 50)]
    # Black rings sunk around the eyes, and black cracks splitting away from them
    for (cx, cy) in eyes:
        for y in range(cy - 24, cy + 26):
            for x in range(cx - 26, cx + 27):
                if not (0 <= x < N and 0 <= y < N):
                    continue
                d = _ellipse(x, y, cx, cy + 2, 24, 20)
                if d < 1:
                    px[x, y] = _mixc(px[x, y][:3], (16, 10, 14), (1 - d) * 1.6) + (255,)
        for k in range(5):
            ang = rnd.uniform(0, 6.283)
            x, y = cx + 14 * __import__("math").cos(ang), cy + 12 * __import__("math").sin(ang)
            for _ in range(rnd.randint(14, 30)):
                ix, iy = int(x), int(y)
                if 0 <= ix < N and 0 <= iy < N:
                    px[ix, iy] = (12, 6, 8, 255)
                ang += rnd.uniform(-0.5, 0.5)
                x += __import__("math").cos(ang)
                y += __import__("math").sin(ang)
        # The eye: black, with an amber slit that burns red at the edge
        for y in range(cy - 10, cy + 11):
            for x in range(cx - 13, cx + 14):
                d = _ellipse(x, y, cx, cy, 13, 9)
                if d < 1:
                    px[x, y] = (4, 2, 4, 255)
                    sd = _ellipse(x, y, cx, cy, 2.2, 8.5)
                    if sd < 1:
                        px[x, y] = _mixc((255, 214, 90), (200, 40, 10), sd) + (255,)
                    elif _ellipse(x, y, cx, cy, 6, 9) < 1:
                        px[x, y] = (60, 8, 4, 255)
    # Brow: heavy and furrowed
    for x in range(10, 118):
        for k in range(3):
            y = 24 + int(abs(x - 64) * 0.12) + k * 4 + (2 if abs(x - 64) < 8 else 0)
            if 0 <= y < N:
                px[x, y] = _mixc(px[x, y][:3], (40, 30, 30), 0.7) + (255,)
    # The mouth, torn far too wide, dark red inside, rows of needle teeth
    for y in range(80, 116):
        for x in range(8, 120):
            d = _ellipse(x, y, 64, 97, 56, 15 - abs(x - 64) * 0.12)
            if d < 1:
                px[x, y] = _mixc((90, 6, 10), (20, 0, 2), d) + (255,)
            elif d < 1.35:
                px[x, y] = _mixc(px[x, y][:3], (70, 8, 12), 0.7) + (255,)   # raw lips
    import math
    for row, (base, down) in enumerate(((84, True), (110, False))):
        for i in range(17):
            x0 = 14 + i * 6 + (3 if row else 0)
            length = 9 + (i % 3) * 2 - abs(i - 8) // 2
            for k in range(length):
                yy = base + k if down else base - k
                w = max(0, 2 - k * 2 // length)
                for dx in range(-w, w + 1):
                    xx = x0 + dx
                    if 0 <= xx < N and 0 <= yy < N and _ellipse(xx, yy, 64, 97, 56, 16) < 1.05:
                        px[xx, yy] = _mixc((246, 238, 214), (180, 164, 120), k / length) + (255,)
    # Cuts on the cheeks, still bleeding
    for (sx, sy) in ((18, 70), (104, 66), (28, 82), (96, 80)):
        for k in range(10):
            x, y = sx + k, sy + k // 2
            if 0 <= x < N and 0 <= y < N:
                px[x, y] = (120, 6, 10, 255)
        for k in range(rnd.randint(6, 14)):
            y = sy + 5 + k
            if y < N:
                px[sx + 5, y] = (140, 8, 12, 255)
    d = os.path.join(out_dir, "textures", "entity", "possessed")
    img.save(os.path.join(d, "generic_true_face.png"))


IDOL = [
    "W..............W",
    "WW....h..h....WW",
    "WWW...hHHh...WWW",
    ".WWW..HeeH..WWW.",
    ".WWWW.HHHH.WWWW.",
    "..WWWWWSSWWWWW..",
    "..WW.aSSSSa.WW..",
    "...W.aSSSSa.W...",
    ".....aSSSSa.....",
    "......SSSS......",
    "......S..S......",
    ".....SS..SS.....",
    ".....S....S.....",
    "...BBBBBBBBBB...",
    "...bbbbbbbbbb...",
    "................",
]
IDOL_COLORS = {
    "W": (74, 82, 62, 255), "h": (52, 58, 44, 255), "H": (98, 106, 82, 255), "e": (214, 40, 24, 255),
    "S": (110, 116, 90, 255), "a": (84, 90, 68, 255), "B": (128, 104, 70, 255), "b": (88, 70, 44, 255),
}


def paint_idol(out_dir):
    """The idol: a small winged statuette of something with horns and red eyes, on a worn stone base."""
    d = os.path.join(out_dir, "textures", "item")
    os.makedirs(d, exist_ok=True)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(IDOL):
        assert len(row) == 16, row
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), IDOL_COLORS[ch])
    img.save(os.path.join(d, "demon_idol.png"))


# --- preview: rough front view of each head, before and after ----------------

def front_view(tex, parts):
    """Rough front view of the head, from a texture at SCALE times the mob's resolution."""
    k = SCALE
    ox, oy, grid = parts[0]
    w, h = len(grid[0]), len(grid)
    box = lambda x1, y1, x2, y2: (x1 * k, y1 * k, x2 * k, y2 * k)
    if parts is PIGLIN_PARTS:
        canvas = tex.crop(box(8, 8, 18, 16)).copy()
        canvas.alpha_composite(tex.crop(box(32, 2, 36, 6)), (3 * k, 4 * k))   # snout
        canvas.alpha_composite(tex.crop(box(3, 1, 4, 3)), (2 * k, 6 * k))     # tusk
        canvas.alpha_composite(tex.crop(box(3, 5, 4, 7)), (7 * k, 6 * k))     # tusk
        return canvas
    if grid is FACE_VILLAGER:
        canvas = Image.new("RGBA", (8 * k, 11 * k), (0, 0, 0, 0))
        canvas.alpha_composite(tex.crop(box(8, 8, 16, 18)), (0, 0))
        canvas.alpha_composite(tex.crop(box(26, 2, 28, 6)), (3 * k, 7 * k))   # nose
        return canvas
    return tex.crop(box(ox, oy, ox + w, oy + h)).copy()


def preview(made, dirs, out_png, scale=4, per_row=6):
    cells = []
    for ns, name, size, parts, rel in MOBS:
        if rel is None or ns not in dirs:
            continue
        base = Image.open(os.path.join(dirs[ns], rel)).convert("RGBA")
        base = base.resize((base.width * SCALE, base.height * SCALE), Image.NEAREST)
        after = base.copy()
        face, blood = made[(ns, name)]
        after.alpha_composite(face)
        after.alpha_composite(blood)
        a, b = front_view(base, parts), front_view(after, parts)
        cells.append((a.resize((a.width * scale, a.height * scale), Image.NEAREST),
                      b.resize((b.width * scale, b.height * scale), Image.NEAREST)))
    gap = 12
    cw = max(c[0].width for c in cells)
    ch = max(c[0].height for c in cells) * 2 + 6
    rows = (len(cells) + per_row - 1) // per_row
    sheet = Image.new("RGBA", (per_row * (cw + gap) + gap, rows * (ch + gap * 2) + gap), (40, 40, 46, 255))
    for i, (a, b) in enumerate(cells):
        cx = gap + (i % per_row) * (cw + gap) + (cw - a.width) // 2
        cy = gap + (i // per_row) * (ch + gap * 2)
        sheet.alpha_composite(a, (cx, cy))
        sheet.alpha_composite(b, (cx, cy + ch // 2 + 3))
    sheet.save(out_png)


if __name__ == "__main__":
    made = generate(sys.argv[1])
    if "--preview" in sys.argv:
        i = sys.argv.index("--preview")
        dirs = dict(arg.split("=", 1) for arg in sys.argv[i + 2:])
        preview(made, dirs, sys.argv[i + 1])
    print(f"generated faces for {len(made)} mobs")
