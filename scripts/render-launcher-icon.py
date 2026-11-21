#!/usr/bin/env python3
"""Render the LCARS launcher icon: adaptive vector drawables plus PNGs from one geometry.

Writes app-console/src/androidMain/res/drawable/ic_launcher_foreground.xml,
ic_launcher_monochrome.xml and ic_launcher_background.xml, the legacy mipmap PNGs,
and docs/store/icon-512.png for the store listing.

Run with `uv run --with "pillow>=10" python scripts/render-launcher-icon.py`.
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app-console" / "src" / "androidMain" / "res"
STORE = ROOT / "docs" / "store"

# Adaptive icon viewport is 108 dp; launchers may mask anything outside the centered 66 dp circle,
# so every shape sits inside 31..77.
VIEWPORT = 108
BLACK = "#000000"
ORANGE = "#D08A3C"
SUNFLOWER = "#D3B591"
ICE = "#8EACC8"
VIOLET = "#A98AAA"

# (kind, color, geometry). Rounded rects are (x0, y0, x1, y1, radius, corners), corners as a set of
# "tl", "tr", "br", "bl". Circles are (cx, cy, r).
SHAPES = [
    ("rrect", ORANGE, (31, 31, 51, 55, 10, {"tl"})),         # elbow
    ("rrect", ORANGE, (51, 47, 77, 55, 4, {"tr", "br"})),    # header bar
    ("rrect", ICE, (31, 58, 41, 66, 0, set())),              # rail block
    ("rrect", VIOLET, (31, 68, 41, 77, 0, set())),           # rail block
    ("circle", SUNFLOWER, (63, 67, 10)),                     # the talk orb
]

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
STORE_SIZE = 512


def rrect_path(x0, y0, x1, y1, r, corners):
    """SVG path for a rectangle with a radius on the named corners only."""
    tl = r if "tl" in corners else 0
    tr = r if "tr" in corners else 0
    br = r if "br" in corners else 0
    bl = r if "bl" in corners else 0
    return (
        f"M{x0 + tl},{y0} H{x1 - tr} "
        + (f"A{tr},{tr} 0 0 1 {x1},{y0 + tr} " if tr else "")
        + f"V{y1 - br} "
        + (f"A{br},{br} 0 0 1 {x1 - br},{y1} " if br else "")
        + f"H{x0 + bl} "
        + (f"A{bl},{bl} 0 0 1 {x0},{y1 - bl} " if bl else "")
        + f"V{y0 + tl} "
        + (f"A{tl},{tl} 0 0 1 {x0 + tl},{y0} " if tl else "")
        + "Z"
    )


def circle_path(cx, cy, r):
    return f"M{cx - r},{cy} A{r},{r} 0 1 0 {cx + r},{cy} A{r},{r} 0 1 0 {cx - r},{cy} Z"


def vector_xml(color_override=None):
    paths = []
    for kind, color, geo in SHAPES:
        d = rrect_path(*geo) if kind == "rrect" else circle_path(*geo)
        paths.append(f'    <path android:fillColor="{color_override or color}" android:pathData="{d}" />')
    body = "\n".join(paths)
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{VIEWPORT}dp"\n    android:height="{VIEWPORT}dp"\n'
        f'    android:viewportWidth="{VIEWPORT}"\n    android:viewportHeight="{VIEWPORT}">\n'
        f"{body}\n</vector>\n"
    )


def background_xml():
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{VIEWPORT}dp"\n    android:height="{VIEWPORT}dp"\n'
        f'    android:viewportWidth="{VIEWPORT}"\n    android:viewportHeight="{VIEWPORT}">\n'
        f'    <path android:fillColor="{BLACK}" android:pathData="M0,0h{VIEWPORT}v{VIEWPORT}h-{VIEWPORT}z" />\n'
        "</vector>\n"
    )


def render_png(size, rounded):
    """The inner 72 dp square of the viewport, with rounded corners for the legacy icon or square for the store."""
    scale = 8  # supersample for clean edges
    big = size * scale
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    origin, span = 18, 72
    if rounded:
        draw.rounded_rectangle((0, 0, big - 1, big - 1), radius=big * 0.22, fill=BLACK)
    else:
        draw.rectangle((0, 0, big, big), fill=BLACK)
    unit = big / span

    def px(v):
        return (v - origin) * unit

    for kind, color, geo in SHAPES:
        if kind == "rrect":
            x0, y0, x1, y1, r, corners = geo
            box = (px(x0), px(y0), px(x1), px(y1))
            if r == 0:
                draw.rectangle(box, fill=color)
            else:
                # Pillow rejects a radius a rounding error over half the side, so clamp it.
                radius = min(r * unit, (box[2] - box[0]) / 2 - 1, (box[3] - box[1]) / 2 - 1)
                draw.rounded_rectangle(
                    box,
                    radius=radius,
                    fill=color,
                    corners=("tl" in corners, "tr" in corners, "br" in corners, "bl" in corners),
                )
        else:
            cx, cy, r = geo
            draw.ellipse((px(cx - r), px(cy - r), px(cx + r), px(cy + r)), fill=color)
    return img.resize((size, size), Image.LANCZOS)


def main():
    (RES / "drawable").mkdir(parents=True, exist_ok=True)
    (RES / "drawable" / "ic_launcher_foreground.xml").write_text(vector_xml())
    (RES / "drawable" / "ic_launcher_monochrome.xml").write_text(vector_xml("#FFFFFF"))
    (RES / "drawable" / "ic_launcher_background.xml").write_text(background_xml())
    for density, size in DENSITIES.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(exist_ok=True)
        icon = render_png(size, rounded=True)
        icon.save(folder / "ic_launcher.png")
        icon.save(folder / "ic_launcher_round.png")
    STORE.mkdir(parents=True, exist_ok=True)
    render_png(STORE_SIZE, rounded=False).save(STORE / "icon-512.png")
    print("wrote drawables, mipmaps and", STORE / "icon-512.png")


if __name__ == "__main__":
    main()
