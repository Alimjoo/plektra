#!/usr/bin/env python3
from pathlib import Path
from typing import Optional

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app/src/main/res/drawable-nodpi"
SCALE = 2

BACKGROUND = "#E1E3E7"
DIVIDER = "#D4D7DD"
KEY = "#FFFFFF"
TEXT = "#050505"
ACCENT = "#0A84FF"
CAPTION = "#F8F9FB"


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = [
        "/System/Library/Fonts/SFNS.ttf",
        "/System/Library/Fonts/SFNSRounded.ttf",
        "/System/Library/Fonts/Helvetica.ttc",
    ]
    for path in candidates:
        try:
            return ImageFont.truetype(path, size * SCALE, index=1 if bold and path.endswith(".ttc") else 0)
        except OSError:
            continue
    return ImageFont.load_default()


def rounded(draw: ImageDraw.ImageDraw, box, radius: int, fill: str, outline: Optional[str] = None, width: int = 1):
    draw.rounded_rectangle(
        tuple(value * SCALE for value in box),
        radius=radius * SCALE,
        fill=fill,
        outline=outline,
        width=width * SCALE,
    )


def centered_text(draw: ImageDraw.ImageDraw, box, text: str, text_font, fill: str = TEXT):
    scaled_box = tuple(value * SCALE for value in box)
    bounds = draw.textbbox((0, 0), text, font=text_font)
    width = bounds[2] - bounds[0]
    height = bounds[3] - bounds[1]
    x = (scaled_box[0] + scaled_box[2] - width) / 2
    y = (scaled_box[1] + scaled_box[3] - height) / 2 - bounds[1]
    draw.text((x, y), text, font=text_font, fill=fill)


def draw_keyboard_icon(draw: ImageDraw.ImageDraw, x: int, y: int):
    box = (x * SCALE, y * SCALE, (x + 54) * SCALE, (y + 34) * SCALE)
    draw.rounded_rectangle(box, radius=5 * SCALE, outline=TEXT, width=3 * SCALE)
    for row, count in enumerate((7, 6)):
        for column in range(count):
            left = (x + 6 + column * 6.5 + (3 if row else 0)) * SCALE
            top = (y + 7 + row * 9) * SCALE
            draw.rounded_rectangle(
                (left, top, left + 4 * SCALE, top + 4 * SCALE),
                radius=SCALE,
                fill=TEXT,
            )
    draw.rounded_rectangle(
        ((x + 15) * SCALE, (y + 25) * SCALE, (x + 39) * SCALE, (y + 29) * SCALE),
        radius=2 * SCALE,
        fill=TEXT,
    )


def draw_cloud_tap_keyboard(include_caption: bool, height: int) -> Image.Image:
    image = Image.new("RGB", (1436 * SCALE, height * SCALE), BACKGROUND)
    draw = ImageDraw.Draw(image)
    label_font = font(32)
    key_font = font(42, bold=True)

    gap = 8
    suggestion_width = (1436 - gap * 2) // 3
    for index, label in enumerate(("Partien", "Plektra", "Partner")):
        left = index * (suggestion_width + gap)
        right = 1436 if index == 2 else left + suggestion_width
        rounded(draw, (left, 0, right, 82), 12, KEY)
        centered_text(draw, (left, 0, right, 82), label, label_font)

    key_top = 96
    key_bottom = 214
    widths = [136, 334, 334, 334, 136, 134]
    labels = ["≡", "ř", "ŕ", "ŗ", "☺", ""]
    left = 0
    for width, label in zip(widths, labels):
        right = left + width
        rounded(draw, (left, key_top, right - 8, key_bottom), 18, KEY)
        if label:
            centered_text(draw, (left, key_top, right - 8, key_bottom), label, key_font)
        left = right

    # Microphone icon in the final key.
    mic_center_x = 1370
    draw.rounded_rectangle(
        ((mic_center_x - 9) * SCALE, 126 * SCALE, (mic_center_x + 9) * SCALE, 175 * SCALE),
        radius=9 * SCALE,
        outline=TEXT,
        width=5 * SCALE,
    )
    draw.arc(
        ((mic_center_x - 24) * SCALE, 145 * SCALE, (mic_center_x + 24) * SCALE, 192 * SCALE),
        0,
        180,
        fill=TEXT,
        width=5 * SCALE,
    )
    draw.line(
        (mic_center_x * SCALE, 190 * SCALE, mic_center_x * SCALE, 204 * SCALE),
        fill=TEXT,
        width=5 * SCALE,
    )

    if include_caption:
        caption_top = 228
        draw.rectangle((0, caption_top * SCALE, 1436 * SCALE, height * SCALE), fill=CAPTION)
        draw.line((0, caption_top * SCALE, 1436 * SCALE, caption_top * SCALE), fill=DIVIDER, width=2 * SCALE)
        # Down chevron.
        draw.line((92 * SCALE, 267 * SCALE, 112 * SCALE, 287 * SCALE), fill=TEXT, width=6 * SCALE)
        draw.line((112 * SCALE, 287 * SCALE, 132 * SCALE, 267 * SCALE), fill=TEXT, width=6 * SCALE)
        draw_keyboard_icon(draw, 1308, 255)
    else:
        draw.line((0, 228 * SCALE, 1436 * SCALE, 228 * SCALE), fill=DIVIDER, width=2 * SCALE)

    return image.resize((1436, height), Image.Resampling.LANCZOS)


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    draw_cloud_tap_keyboard(True, 315).save(
        OUTPUT / "tutorial_titan2_ime_caption_bar_with.jpeg",
        quality=94,
        subsampling=0,
    )
    draw_cloud_tap_keyboard(False, 234).save(
        OUTPUT / "tutorial_titan2_ime_caption_bar_without.jpeg",
        quality=94,
        subsampling=0,
    )


if __name__ == "__main__":
    main()
