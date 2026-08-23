from pathlib import Path

from PIL import Image


PROJECT_DIR = Path(__file__).resolve().parents[1]
SOURCE = PROJECT_DIR / "src/static/images/home-prototype/share-title-art.png"
TARGET = PROJECT_DIR / "src/static/images/home-prototype/share-title-transparent.png"
TEMP_TARGET = TARGET.with_name("share-title-transparent.tmp.png")


def clamp(value: float) -> int:
    return max(0, min(255, round(value * 255)))


source = Image.open(SOURCE).convert("RGB")
target = Image.new("RGBA", source.size, (0, 0, 0, 0))

for y in range(source.height):
    for x in range(source.width):
        red, green, blue = source.getpixel((x, y))

        # Preserve the orange icon accent. The yellow-green background curve has
        # a much higher green channel, so it is intentionally excluded.
        if x < 50 and red > 140 and green < 190 and blue < 140:
            alpha = clamp(max((red - green) / 130, (red - blue) / 190))
            target.putpixel((x, y), (red, green, blue, alpha))
            continue

        # Preserve the white hashtag and its antialiased edge inside the icon.
        if 14 <= x <= 38 and 21 <= y <= 45 and red > 122 and green > 122 and blue > 122:
            alpha = clamp((min(red, green, blue) - 112) / 143)
            target.putpixel((x, y), (255, 255, 255, alpha))
            continue

        # Recover the black title/icon alpha from the green channel. All cyan
        # background shades and the yellow-green curve keep green near 240,
        # while the foreground approaches #1f1f1f.
        alpha = clamp((240 - green) / 209)
        if alpha > 4:
            target.putpixel((x, y), (31, 31, 31, alpha))

target.save(TEMP_TARGET, optimize=True)
TEMP_TARGET.replace(TARGET)
print(TARGET)
