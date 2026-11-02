"""Sleepium's logo: an element tile ("-ium"): symbol Sl, atomic number 20 (ticks per second), a crescent moon, night.
    python tools/gen_logo.py  ->  art/logo-1024.png, art/logo-512.png (Modrinth icon), art/logo-400.png (CurseForge)
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'art')
N = 1024
FONTS = r'C:\Windows\Fonts'


def font(name, size):
    for n in name:
        p = os.path.join(FONTS, n)
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


# night gradient on a rounded tile
grad = Image.new('RGBA', (N, N))
top, bottom = (24, 33, 78, 255), (58, 36, 104, 255)
gp = grad.load()
for y in range(N):
    for x in range(N):
        t = min(1.0, max(0.0, (0.65 * y + 0.35 * x) / N))
        gp[x, y] = lerp(top, bottom, t)
mask = Image.new('L', (N, N), 0)
ImageDraw.Draw(mask).rounded_rectangle([16, 16, N - 16, N - 16], radius=170, fill=255)
tile = Image.new('RGBA', (N, N), (0, 0, 0, 0))
tile.paste(grad, (0, 0), mask)
d = ImageDraw.Draw(tile)

# stars (fixed seed), kept off the symbol
rnd = random.Random(20)
for _ in range(70):
    x, y = rnd.randint(70, N - 70), rnd.randint(70, N - 70)
    if 230 < x < 800 and 260 < y < 760:
        continue
    r = rnd.choice((2, 2, 3, 4))
    a = rnd.randint(90, 200)
    d.ellipse([x - r, y - r, x + r, y + r], fill=(220, 228, 255, a))

# the element tile's inner frame
d.rounded_rectangle([64, 64, N - 64, N - 64], radius=130, outline=(160, 178, 255, 110), width=6)

# a crescent moon, top right (a disc minus an offset disc)
moon = Image.new('L', (N, N), 0)
md = ImageDraw.Draw(moon)
cx, cy, r = 790, 225, 105
md.ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
md.ellipse([cx - r + 48, cy - r - 30, cx + r + 48, cy + r - 30], fill=0)
glow = moon.filter(ImageFilter.GaussianBlur(28))
tile.paste((255, 214, 130, 90), (0, 0), glow)
tile.paste((255, 222, 150, 255), (0, 0), moon)

# atomic number 20 (a server's ticks per second), top left
d.text((128, 112), '20', font=font(['segoeuib.ttf', 'arialbd.ttf'], 120), fill=(205, 214, 255, 235))

# the symbol, with a soft glow
sym = Image.new('L', (N, N), 0)
sd = ImageDraw.Draw(sym)
f = font(['segoeuib.ttf', 'arialbd.ttf'], 470)
w = sd.textlength('Sl', font=f)
sd.text(((N - w) / 2 - 6, 230), 'Sl', font=f, fill=255)
tile.paste((140, 160, 255, 140), (0, 0), sym.filter(ImageFilter.GaussianBlur(22)))
tile.paste((248, 250, 255, 255), (0, 0), sym)

# the name
f2 = font(['segoeuisb.ttf', 'segoeuib.ttf', 'arialbd.ttf'], 118)
w2 = d.textlength('Sleepium', font=f2)
d.text(((N - w2) / 2, 790), 'Sleepium', font=f2, fill=(200, 210, 255, 245))

os.makedirs(OUT, exist_ok=True)
tile.save(os.path.join(OUT, 'logo-1024.png'))
for s in (512, 400):
    tile.resize((s, s), Image.LANCZOS).save(os.path.join(OUT, 'logo-%d.png' % s))
print('written', OUT)
