#!/usr/bin/env python3
"""Render the Android ClarID icon's rectangle artwork for the iOS asset catalog."""
import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "App/Assets.xcassets"
ICON = ROOT / "AppIcon.appiconset"


def main():
    ICON.mkdir(parents=True, exist_ok=True)
    entries = []
    sizes = [("iphone", 20, [2, 3]), ("iphone", 29, [2, 3]), ("iphone", 40, [2, 3]),
             ("iphone", 60, [2, 3]), ("ipad", 20, [1, 2]), ("ipad", 29, [1, 2]),
             ("ipad", 40, [1, 2]), ("ipad", 76, [1, 2]), ("ipad", 83.5, [2]),
             ("ios-marketing", 1024, [1])]
    for idiom, size, scales in sizes:
        for scale in scales:
            pixels = round(size * scale)
            filename = f"icon-{pixels}.png"
            image = Image.new("RGB", (pixels * 4, pixels * 4), "#176B62")
            draw = ImageDraw.Draw(image)

            def rectangle(x, y, width, height, color):
                unit = pixels * 4 / 48
                draw.rectangle((round(x * unit), round(y * unit), round((x + width) * unit) - 1,
                                round((y + height) * unit) - 1), fill=color)

            for x, y, width, height in [(8, 8, 13, 13), (27, 8, 13, 13), (8, 27, 13, 13),
                                        (27, 27, 6, 6), (34, 34, 6, 6), (27, 36, 4, 4), (36, 27, 4, 4)]:
                rectangle(x, y, width, height, "white")
            for x, y in [(12, 12), (31, 12), (12, 31)]:
                rectangle(x, y, 5, 5, "#176B62")
            image.resize((pixels, pixels), Image.Resampling.LANCZOS).save(ICON / filename)
            entries.append({"idiom": idiom, "size": f"{size}x{size}", "scale": f"{scale}x", "filename": filename})
    info = {"version": 1, "author": "xcode"}
    (ICON / "Contents.json").write_text(json.dumps({"images": entries, "info": info}, indent=2) + "\n")
    (ROOT / "Contents.json").write_text(json.dumps({"info": info}, indent=2) + "\n")
    print(f"Wrote {ICON}")


if __name__ == "__main__":
    main()
