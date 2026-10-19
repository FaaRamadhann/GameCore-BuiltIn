#!/usr/bin/env python3
"""Regenerates every launcher icon from the source artwork.

    python tools/make_icon.py

Adaptive: artwork is full-bleed on a 108dp canvas; the launcher mask shows the
central 72dp, so the framing crop is chosen to survive that crop.
Legacy: the visible framing is baked in, because launchers that read
mipmap-<density>/ic_launcher.png do not mask it.
"""
import os
import glob
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
SRC = glob.glob(os.path.join(ROOT, "*.jpg"))[0]
BG = (7, 20, 34, 255)  # #071422, matches ic_launcher_background
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
LEGACY_PX = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
CANVAS_DP = 108
# Adaptive icons only guarantee the central 72dp of the 108dp canvas.
VISIBLE = 72 / CANVAS_DP


def crop_framing(im):
    """Square crop centred on the face, with headroom the mask will eat."""
    w, h = im.size
    side = round(min(w, h) * 0.90)
    x = (w - side) // 2
    y = max(0, round(h * 0.02))
    return im.crop((x, y, x + side, y + side))


def visible_part(framed):
    """The part of the framed square an adaptive mask actually shows."""
    s = framed.size[0]
    inset = round(s * (1 - VISIBLE) / 2)
    return framed.crop((inset, inset, s - inset, s - inset))


def main():
    art = Image.open(SRC).convert("RGB")
    framed = crop_framing(art)
    vis = visible_part(framed)

    for name, scale in DENSITIES.items():
        canvas = round(CANVAS_DP * scale)
        icon = Image.new("RGBA", (canvas, canvas), BG)
        icon.paste(framed.resize((canvas, canvas), Image.LANCZOS), (0, 0))
        out = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(out, exist_ok=True)
        icon.save(os.path.join(out, "ic_launcher_foreground.png"), optimize=True)

        px = LEGACY_PX[name]
        square = framed.resize((px, px), Image.LANCZOS).convert("RGBA")
        square.save(os.path.join(out, "ic_launcher.png"), optimize=True)

        round_icon = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        mask = Image.new("L", (px * 4, px * 4), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, px * 4 - 1, px * 4 - 1), fill=255)
        round_icon.paste(square, (0, 0), mask.resize((px, px), Image.LANCZOS))
        round_icon.save(os.path.join(out, "ic_launcher_round.png"), optimize=True)
        print(f"{name}: adaptive {canvas}px, legacy {px}px, round {px}px")

    preview(framed)


def preview(framed):
    """Shows what a launcher really draws: masked 72dp centre + legacy at 48px."""
    os.makedirs(os.path.join(ROOT, "build"), exist_ok=True)
    big = round(CANVAS_DP * 4)
    vis = visible_part(framed).resize((big, big), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, big - 1, big - 1), fill=255)
    circle = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    circle.paste(vis, (0, 0), mask)

    sheet = Image.new("RGBA", (big * 2 + 30, big), (0, 0, 0, 0))
    sheet.paste(circle, (0, 0))
    # 48px legacy upscaled with NEAREST: exactly the pixels a small launcher draws.
    small = framed.resize((48, 48), Image.LANCZOS).resize((big, big), Image.NEAREST)
    sheet.paste(small, (big + 30, 0))
    sheet.save(os.path.join(ROOT, "build", "icon-preview.png"))


if __name__ == "__main__":
    main()
