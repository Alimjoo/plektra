#!/usr/bin/env python3
"""Generate all Plektra launcher icon variants from the canonical SVG marks."""

from __future__ import annotations

import io
import argparse
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path
from tempfile import TemporaryDirectory

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
BRANDING = ROOT / "docs/branding"
BACKGROUND = (247, 244, 239, 255)
BROWN = (51, 37, 21, 255)
ORANGE = "#FC7A0F"
BLUE = "#2F80ED"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

VARIANTS = {
    "closed_orange": (BRANDING / "plektra-logo.svg", ORANGE),
    "open_orange": (BRANDING / "plektra-open-logo.svg", ORANGE),
    "closed_blue": (BRANDING / "plektra-logo.svg", BLUE),
    "open_blue": (BRANDING / "plektra-open-logo.svg", BLUE),
}

RESOURCE_NAMES = {
    "closed_orange": "ic_launcher",
    "open_orange": "ic_launcher_open",
    "closed_blue": "ic_launcher_nightly",
    "open_blue": "ic_launcher_nightly_open",
}

FOREGROUND_NAMES = {
    "closed_orange": "plektra_launcher_foreground",
    "open_orange": "plektra_open_launcher_foreground",
    "closed_blue": "plektra_nightly_launcher_foreground",
    "open_blue": "plektra_nightly_open_launcher_foreground",
}


def render_svg(path: Path, accent: str, *, omit_framed_background: bool = False) -> Image.Image:
    source = path.read_text(encoding="utf-8").replace(ORANGE, accent)
    if omit_framed_background:
        root = ET.fromstring(source)
        background = next(child for child in root if child.get("id") == "framed-panel-background")
        root.remove(background)
        source = ET.tostring(root, encoding="unicode")
    result = subprocess.run(
        ["rsvg-convert", "--width", "1024", "--height", "1024", "--format", "png"],
        input=source.encode(),
        capture_output=True,
        check=True,
    )
    image = Image.open(io.BytesIO(result.stdout)).convert("RGBA")
    visible_bounds = image.getchannel("A").getbbox()
    if visible_bounds is None:
        raise ValueError(f"Rendered icon has no visible pixels: {path}")
    return image.crop(visible_bounds)


def fitted(source: Image.Image, size: int, fraction: float, background: tuple[int, int, int, int] | None) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), background or (0, 0, 0, 0))
    target = round(size * fraction)
    mark = source.resize((target, target), Image.Resampling.LANCZOS)
    offset = (size - target) // 2
    canvas.alpha_composite(mark, (offset, offset))
    return canvas


def fill_framed_cutouts(source: Image.Image) -> Image.Image:
    """Make the framed mark self-contained while keeping its exterior transparent."""
    result = Image.new("RGBA", source.size, (0, 0, 0, 0))
    panel = Image.new("RGBA", source.size, (0, 0, 0, 0))
    radius = round(min(source.size) * 54 / 251)
    ImageDraw.Draw(panel).rounded_rectangle(
        (0, 0, source.width - 1, source.height - 1),
        radius=radius,
        fill=BACKGROUND,
    )
    result.alpha_composite(panel)
    result.alpha_composite(source)
    return result


def save_webp(image: Image.Image, path: Path, *, lossless: bool = True) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "WEBP", lossless=lossless, method=6)


def generate_monochrome() -> None:
    source = render_svg(BRANDING / "plektra-open-monochrome.svg", ORANGE)
    monochrome = fitted(source, 432, 0.47, None)
    # Android tints the alpha mask; keep the detached plectrum in every alias.
    black = Image.new("RGBA", monochrome.size, (0, 0, 0, 0))
    black.putalpha(monochrome.getchannel("A"))
    save_webp(black, RES / "drawable-nodpi" / "plektra_open_launcher_monochrome.webp")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--monochrome-only", action="store_true")
    args = parser.parse_args()
    if args.monochrome_only:
        generate_monochrome()
        return
    raw_rendered = {name: render_svg(path, accent) for name, (path, accent) in VARIANTS.items()}
    rendered = {
        name: fill_framed_cutouts(source) if name.startswith("closed_") else source
        for name, source in raw_rendered.items()
    }

    for name, source in rendered.items():
        resource = RESOURCE_NAMES[name]
        foreground = FOREGROUND_NAMES[name]

        # Leave room for the detached plectrum and bottom keys under round masks.
        adaptive_fraction = 0.61 if name.startswith("closed_") else 0.47
        save_webp(fitted(source, 432, adaptive_fraction, None), RES / "drawable-nodpi" / f"{foreground}.webp")
        background = BROWN if name.startswith("closed_") else BACKGROUND
        save_webp(fitted(source, 192, 0.84, background).convert("RGB"), RES / "drawable-nodpi" / f"{name}_launcher_preview.webp")

        for density, size in DENSITIES.items():
            legacy = fitted(source, size, 0.84, background).convert("RGB")
            save_webp(legacy, RES / f"mipmap-{density}" / f"{resource}.webp")
            # Fit the complete mark inside a circular launcher mask.
            round_fraction = 0.66 if name.startswith("closed_") else 0.60
            round_icon = fitted(source, size, round_fraction, background).convert("RGB")
            save_webp(round_icon, RES / f"mipmap-{density}" / f"{resource}_round.webp")

    generate_monochrome()

    fitted(rendered["closed_orange"], 512, 0.84, BROWN).convert("RGB").save(
        ROOT / "app/src/main/ic_launcher-playstore.png", "PNG", optimize=True
    )


if __name__ == "__main__":
    main()
