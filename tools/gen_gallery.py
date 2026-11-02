"""Store / README pictures: art/gallery/ (screenshots copied by hand from the runs) plus two made here -
the server tick chart (README's N100 table) and the social preview banner (1280x640, GitHub's size).
    python tools/gen_gallery.py
"""
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'art', 'gallery')
FONTS = r'C:\Windows\Fonts'
os.makedirs(OUT, exist_ok=True)


def font(size, bold=False):
    for n in (['segoeuib.ttf', 'arialbd.ttf'] if bold else ['segoeui.ttf', 'arial.ttf']):
        p = os.path.join(FONTS, n)
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


BG, INK, MUTED, GRID = (20, 24, 46), (236, 239, 255), (150, 160, 200), (52, 58, 92)
VANILLA, SLEEPIUM = (128, 136, 168), (255, 206, 120)

# ---------------------------------------------------------------- the tick chart (README, Intel N100, MSPT)
rows = [('4 000 item stacks', 109.60, 8.00), ('items among minecarts', 36.97, 10.82), ('300 animals + 50 villagers', 18.48, 10.00),
        ('120 villagers', 16.57, 10.57), ('cow cramming', 13.22, 6.75), ('water and lava', 10.43, 5.15), ('1 024 idle hoppers', 2.78, 0.73),
        ('1 024 furnaces', 1.25, 0.55)]
W, H = 1600, 900
img = Image.new('RGB', (W, H), BG)
d = ImageDraw.Draw(img)
d.text((60, 40), 'Server time per tick on an Intel N100 (lower is better)', font=font(44, True), fill=INK)
d.text((60, 100), 'milliseconds per tick from vanilla\'s /tick query; same jar with every optimisation off = vanilla', font=font(26), fill=MUTED)
left, right, top = 470, W - 220, 190
row_h = (H - top - 60) // len(rows)
scale = (right - left) / 110.0
for x in range(0, 111, 20):
    gx = left + x * scale
    d.line([(gx, top - 10), (gx, H - 50)], fill=GRID, width=2)
    d.text((gx - 14, H - 45), str(x), font=font(22), fill=MUTED)
for i, (name, van, sl) in enumerate(rows):
    y = top + i * row_h
    d.text((60, y + row_h * 0.22), name, font=font(28), fill=INK)
    bh = int(row_h * 0.32)
    d.rectangle([left, y + 6, left + max(3, van * scale), y + 6 + bh], fill=VANILLA)
    d.rectangle([left, y + 10 + bh, left + max(3, sl * scale), y + 10 + 2 * bh], fill=SLEEPIUM)
    d.text((left + van * scale + 10, y + 2), '%.2f' % van, font=font(22), fill=MUTED)
    d.text((left + sl * scale + 10, y + 6 + bh), '%.2f  x%.1f' % (sl, van / sl), font=font(22, True), fill=SLEEPIUM)
lx = W - 420
d.rectangle([lx, 146, lx + 26, 172], fill=VANILLA)
d.text((lx + 36, 142), 'vanilla', font=font(26), fill=INK)
d.rectangle([lx + 170, 146, lx + 196, 172], fill=SLEEPIUM)
d.text((lx + 206, 142), 'Sleepium', font=font(26, True), fill=INK)
img.save(os.path.join(OUT, 'ticks-n100.png'))

# ---------------------------------------------------------------- the social preview (GitHub: 1280x640)
W, H = 1280, 640
img = Image.new('RGB', (W, H), BG)
d = ImageDraw.Draw(img)
for y in range(H):
    t = y / H
    d.line([(0, y), (W, y)], fill=(int(24 + 30 * t), int(33 + 4 * t), int(78 + 26 * t)))
logo = Image.open(os.path.join(ROOT, 'art', 'logo-1024.png')).resize((340, 340), Image.LANCZOS)
img.paste(logo, (60, 150), logo)
X, MAXW = 450, W - 450 - 50


def fit(text, size, bold=False):
    while size > 12 and d.textlength(text, font=font(size, bold)) > MAXW:
        size -= 1
    return font(size, bold)


lines = [('Sleepium', 110, True, INK, 150), ('Minecraft performance that keeps the game exact', 38, False, (205, 214, 255), 290),
         ('NeoForge 1.21.1  ·  server + client  ·  shaderpacks (beta)', 28, False, MUTED, 350),
         ('items ×13.7  ·  hoppers ×3.8  ·  67 → 1 514 fps', 34, True, SLEEPIUM, 425)]
for text, size, bold, color, y in lines:
    d.text((X, y), text, font=fit(text, size, bold), fill=color)
img.save(os.path.join(OUT, 'social-preview.png'))
print('written', OUT)
