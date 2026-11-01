from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
FONT = Path(r"C:\Windows\Fonts\simhei.ttf")
TEXT = "签"

SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def draw_icon(size: int, round_icon: bool) -> Image.Image:
    scale = 8
    s = size * scale
    img = Image.new("RGBA", (s, s), (255, 255, 255, 255) if not round_icon else (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    if round_icon:
        draw.ellipse((0, 0, s - 1, s - 1), fill=(255, 255, 255, 255))
    font = ImageFont.truetype(str(FONT), int(s * 0.72))
    draw.text((s // 2, s // 2 - int(s * 0.015)), TEXT, font=font, fill=(0, 0, 0, 255), anchor="mm")
    return img.resize((size, size), Image.LANCZOS)


def main():
    for folder, size in SIZES.items():
        out = RES / folder
        out.mkdir(parents=True, exist_ok=True)
        draw_icon(size, False).save(out / "ic_launcher.png")
        draw_icon(size, True).save(out / "ic_launcher_round.png")
        print(f"{folder}: {size}px")


if __name__ == "__main__":
    main()
