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

# ---------------------------------------------------------------- Create (notes 48 / 50: host RTX 4070 SUPER, 1280x720,
# the engine's defaults, Flywheel on; the lower end of each measured range)
rows = [('Alpha Industries', '12 880 Create block entities', 48, 176), ('SS.Venator (Create Aeronautics)', 'ship placed', 108, 283),
        ('SS.Venator (Create Aeronautics)', 'assembled into a Sable sub-level', 101, 254)]
W, H = 1600, 760
img = Image.new('RGB', (W, H), BG)
d = ImageDraw.Draw(img)
d.text((60, 40), 'Create worlds on the client (higher is better)', font=font(44, True), fill=INK)
d.text((60, 100), 'fps, RTX 4070 SUPER, 1280x720, Flywheel on, Sleepium\'s default settings; vanilla = the same mods without Sleepium', font=font(26), fill=MUTED)
lx = W - 420
d.rectangle([lx, 156, lx + 26, 182], fill=VANILLA)
d.text((lx + 36, 152), 'vanilla', font=font(26), fill=INK)
d.rectangle([lx + 170, 156, lx + 196, 182], fill=SLEEPIUM)
d.text((lx + 206, 152), 'Sleepium', font=font(26, True), fill=INK)
left, right, top = 560, W - 200, 220
row_h = (H - top - 70) // len(rows)
scale = (right - left) / 300.0
for x in range(0, 301, 50):
    gx = left + x * scale
    d.line([(gx, top - 10), (gx, H - 60)], fill=GRID, width=2)
    d.text((gx - 14, H - 55), str(x), font=font(22), fill=MUTED)
for i, (name, sub, van, sl) in enumerate(rows):
    y = top + i * row_h
    d.text((60, y + 8), name, font=font(28), fill=INK)
    d.text((60, y + 46), sub, font=font(22), fill=MUTED)
    bh = int(row_h * 0.3)
    d.rectangle([left, y + 6, left + van * scale, y + 6 + bh], fill=VANILLA)
    d.rectangle([left, y + 12 + bh, left + sl * scale, y + 12 + 2 * bh], fill=SLEEPIUM)
    d.text((left + van * scale + 10, y + 2), '%d' % van, font=font(24), fill=MUTED)
    d.text((left + sl * scale + 10, y + 8 + bh), '%d  x%.1f' % (sl, sl / van), font=font(24, True), fill=SLEEPIUM)
img.save(os.path.join(OUT, 'create-client.png'))
print('create chart written')

# ---------------------------------------------------------------- against Lithium and Moonrise (N100, 2026-11-01: a production
# server, each mod alone, mean MSPT over the passes; speed-up = vanilla / mod)
MS = {'items': (115.05, 17.00, 10.53, 8.48), 'hoppers': (2.57, 0.80, 2.67, 0.70), 'cramming': (13.28, 10.57, 9.10, 6.47),
      'carts': (37.28, 12.17, 8.96, 10.35), 'furnaces': (1.63, 1.15, 0.77, 0.77), 'mobs': (19.07, 13.65, 14.88, 10.12),
      'fluids': (10.33, 4.88, 6.00, 5.43), 'pistons': (7.89, 6.95, 3.99, 4.22), 'villagers': (15.82, 11.43, 18.38, 10.25),
      'smelters': (3.73, 4.08, 3.45, 2.52), 'crops': (0.67, 0.71, 0.30, 0.47), 'redstone': (12.03, 8.10, 8.65, 9.94)}
LITH, MOON = (94, 200, 192), (180, 156, 255)
order = sorted(MS, key=lambda k: -MS[k][0] / MS[k][3])
W, H = 1600, 1180
img = Image.new('RGB', (W, H), BG)
d = ImageDraw.Draw(img)
d.text((60, 40), 'Server speed-up over vanilla on an Intel N100 (higher is better)', font=font(44, True), fill=INK)
d.text((60, 100), 'vanilla MSPT / the mod\'s MSPT; production NeoForge server, each mod alone; Lithium 0.15.4, Moonrise 0.1.0-beta.15',
       font=font(25), fill=MUTED)
lx = W - 640
for i, (name, col) in enumerate((('Lithium', LITH), ('Moonrise', MOON), ('Sleepium', SLEEPIUM))):
    d.rectangle([lx + i * 210, 156, lx + i * 210 + 26, 182], fill=col)
    d.text((lx + i * 210 + 36, 152), name, font=font(26, i == 2), fill=INK)
left, right, top = 260, W - 160, 220
row_h = (H - top - 70) // len(order)
scale = (right - left) / 14.0
for x in range(0, 15, 2):
    gx = left + x * scale
    d.line([(gx, top - 10), (gx, H - 60)], fill=GRID, width=2)
    d.text((gx - 10, H - 55), '%dx' % x, font=font(22), fill=MUTED)
one = left + scale
d.line([(one, top - 10), (one, H - 60)], fill=(200, 200, 220), width=2)
for i, k in enumerate(order):
    van, li, mo, sl = MS[k]
    y = top + i * row_h
    d.text((60, y + row_h * 0.3), k, font=font(28), fill=INK)
    bh = int(row_h * 0.24)
    for j, (v, col) in enumerate(((li, LITH), (mo, MOON), (sl, SLEEPIUM))):
        sp = van / v
        yy = y + 4 + j * (bh + 3)
        d.rectangle([left, yy, left + sp * scale, yy + bh], fill=col)
        d.text((left + sp * scale + 8, yy - 4), '%.1fx' % sp, font=font(19, j == 2), fill=col)
img.save(os.path.join(OUT, 'vs-lithium-moonrise.png'))
print('mods chart written')

# ---------------------------------------------------------------- against Sodium (host RTX 4070 SUPER, 1280x720): no pack
# (2026-09-28, same day, render distance 12) and with Complementary Reimagined (2026-11-02, Iris + Sodium vs Sleepium)
SOD = (226, 120, 120)
panels = [('Without shaders: Sodium 0.8.13', [('terrain', (1697, 1827), (1762, 1912)), ('entity scene', (146, 146), (1500, 1680))], 2000,
           'Sodium'),
          ('Complementary Reimagined: Iris + Sodium', [('538 entities, shadows', (68, 73), (484, 484)), ('terrain view', (477, 515), (452, 475))], 600,
           'Iris + Sodium')]
W, H = 1600, 900
img = Image.new('RGB', (W, H), BG)
d = ImageDraw.Draw(img)
d.text((60, 40), 'Client fps against Sodium (higher is better)', font=font(44, True), fill=INK)
d.text((60, 100), 'RTX 4070 SUPER, 1280x720, uncapped; each panel has its own scale; bars at the middle of each measured range', font=font(25), fill=MUTED)
lx = W - 470
d.rectangle([lx, 156, lx + 26, 182], fill=SOD)
d.text((lx + 36, 152), 'Sodium (/ Iris)', font=font(26), fill=INK)
d.rectangle([lx + 250, 156, lx + 276, 182], fill=SLEEPIUM)
d.text((lx + 286, 152), 'Sleepium', font=font(26, True), fill=INK)
left, right = 380, W - 260
y0 = 230
for title, rows, top_v, other in panels:
    d.text((60, y0), title, font=font(30, True), fill=INK)
    scale = (right - left) / top_v
    base = y0 + 50
    for i, (name, a, b) in enumerate(rows):
        y = base + i * 130
        d.text((60, y + 30), name, font=font(28), fill=INK)
        for j, ((lo, hi), col) in enumerate(((a, SOD), (b, SLEEPIUM))):
            mid = (lo + hi) / 2
            yy = y + 8 + j * 52
            d.rectangle([left, yy, left + mid * scale, yy + 44], fill=col)
            label = ('%d' % lo) if lo == hi else ('%d–%d' % (lo, hi))
            d.text((left + mid * scale + 12, yy + 6), label + ' fps', font=font(24, j == 1), fill=col)
    y0 = base + len(rows) * 130 + 30
img.save(os.path.join(OUT, 'vs-sodium.png'))
print('sodium chart written')
