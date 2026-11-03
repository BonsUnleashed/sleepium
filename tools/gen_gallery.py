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

# ---------------------------------------------------------------- against Horizon (a Rust rewrite of Minecraft 26.3,
# release 0.2.97), the N100, 2026-10-30 (README): the mean of two interleaved passes; client fps, the same session
SRV = [('items', 8.5, 14.2), ('hoppers', 0.715, 2.7), ('smelters', 2.65, 11.45), ('furnaces', 0.765, 0.985), ('pistons', 4.3, 6.15),
       ('crops', 0.5, 0.5), ('mobs', 10.05, 8.15), ('villagers', 12.1, 5.35), ('cramming', 6.5, 4.7), ('redstone', 10.05, 8.4),
       ('carts', 10.95, 8.05), ('fluids', 5.0, 0.47)]
CLI = [('entities', 115, 87), ('mobs', 136, 112), ('light', 156, 138)]
HOR = (232, 128, 96)
W, H = 1600, 1420
img = Image.new('RGB', (W, H), BG)
d = ImageDraw.Draw(img)
d.text((60, 40), 'Against Horizon, a Rust rewrite of Minecraft (Intel N100)', font=font(44, True), fill=INK)
d.text((60, 100), 'Sleepium on 1.21.1, Horizon 0.2.97 on 26.3 (the versions differ); same machine, same session, 2026-10-30', font=font(25), fill=MUTED)
lx = W - 470
d.rectangle([lx, 156, lx + 26, 182], fill=HOR)
d.text((lx + 36, 152), 'Horizon', font=font(26), fill=INK)
d.rectangle([lx + 200, 156, lx + 226, 182], fill=SLEEPIUM)
d.text((lx + 236, 152), 'Sleepium', font=font(26, True), fill=INK)
left, right = 300, W - 220
d.text((60, 220), 'Server: ms per tick (lower is better)', font=font(30, True), fill=INK)
top, row_h = 280, 62
scale = (right - left) / 15.0
for x in range(0, 16, 5):
    gx = left + x * scale
    d.line([(gx, top - 6), (gx, top + row_h * len(SRV))], fill=GRID, width=2)
    d.text((gx - 8, top + row_h * len(SRV) + 4), str(x), font=font(22), fill=MUTED)
for i, (name, sl, ho) in enumerate(SRV):
    y = top + i * row_h
    d.text((60, y + 12), name, font=font(26), fill=INK)
    for j, (v, col) in enumerate(((ho, HOR), (sl, SLEEPIUM))):
        yy = y + 4 + j * 26
        d.rectangle([left, yy, left + max(3, v * scale), yy + 22], fill=col)
        d.text((left + max(3, v * scale) + 8, yy - 3), ('%.2f' % v).rstrip('0').rstrip('.'), font=font(20, j == 1), fill=col)
y0 = top + row_h * len(SRV) + 60
d.text((60, y0), 'Client: fps, 1280x720 (higher is better)', font=font(30, True), fill=INK)
top, row_h = y0 + 60, 90
scale = (right - left) / 160.0
for i, (name, sl, ho) in enumerate(CLI):
    y = top + i * row_h
    d.text((60, y + 22), name, font=font(26), fill=INK)
    for j, (v, col, extra) in enumerate(((ho, HOR, ''), (sl, SLEEPIUM, '  +%d %%' % round((sl / ho - 1) * 100)))):
        yy = y + 6 + j * 38
        d.rectangle([left, yy, left + v * scale, yy + 32], fill=col)
        d.text((left + v * scale + 10, yy), '%d fps%s' % (v, extra), font=font(22, j == 1), fill=col)
img.save(os.path.join(OUT, 'vs-horizon.png'))
print('horizon chart written')

# ---------------------------------------------------------------- 1.0.3: a view over a river (production client, the
# saved world-auto at 40 110 40, RTX 4070 SUPER; bars at the middle of each measured range) and the N100's client CPU
W, H = 1600, 920
img = Image.new('RGB', (W, H), BG)
d = ImageDraw.Draw(img)
d.text((60, 40), 'Water and glass in one draw (Sleepium 1.0.3)', font=font(44, True), fill=INK)
d.text((60, 100), 'production NeoForge client, a view over a river, RTX 4070 SUPER, uncapped; frames unchanged (frame hashes)', font=font(26), fill=MUTED)
SODIUM = (226, 120, 120)
panel = [('Sleepium 1.0.2', '1227-1383 fps', 1305, VANILLA), ('Sleepium 1.0.2 + Sodium 0.8.13', '1718 fps', 1718, SODIUM),
         ('Sleepium 1.0.3', '2635-2833 fps', 2734, SLEEPIUM)]
left, right, top = 560, W - 230, 190
scale = (right - left) / 3000.0
d.text((60, top - 10), 'fps (higher is better)', font=font(28, True), fill=INK)
for x in range(0, 3001, 500):
    gx = left + x * scale
    d.line([(gx, top + 34), (gx, top + 330)], fill=GRID, width=2)
    d.text((gx - 22, top + 336), str(x), font=font(22), fill=MUTED)
for i, (name, label, v, col) in enumerate(panel):
    y = top + 44 + i * 96
    d.text((60, y + 14), name, font=font(28, i == 2), fill=INK)
    d.rectangle([left, y + 8, left + v * scale, y + 64], fill=col)
    d.text((left + v * scale + 12, y + 20), label, font=font(26, i == 2), fill=col)
top2 = top + 420
d.text((60, top2), 'Intel N100 (Intel UHD), entity scene: client CPU (lower is better)', font=font(28, True), fill=INK)
cpu = [('Sleepium 1.0.2', 1.67, VANILLA), ('Sleepium 1.0.3', 0.52, SLEEPIUM)]
scale2 = (right - left) / 2.0
for x10 in range(0, 21, 5):
    gx = left + x10 / 10.0 * scale2
    d.line([(gx, top2 + 44), (gx, top2 + 240)], fill=GRID, width=2)
    d.text((gx - 18, top2 + 246), '%.1f' % (x10 / 10.0), font=font(22), fill=MUTED)
for i, (name, v, col) in enumerate(cpu):
    y = top2 + 54 + i * 90
    d.text((60, y + 14), name, font=font(28, i == 1), fill=INK)
    d.rectangle([left, y + 8, left + v * scale2, y + 64], fill=col)
    d.text((left + v * scale2 + 12, y + 20), '%.2f cores' % v, font=font(26, i == 1), fill=col)
img.save(os.path.join(OUT, 'water-view.png'))

# ---------------------------------------------------------------- 1.0.3: tested together with (notes/53)
mods = ['Sodium', 'Iris + shaders', 'Embeddium', 'Lithium', 'Moonrise', 'C2ME', 'ModernFix', 'FerriteCore', 'ImmediatelyFast',
        'EntityCulling', 'ScalableLux', 'Radium', 'BadOptimizations', 'Dynamic FPS', 'Chloride', 'Noisium', 'spark', 'FastSuite',
        'Ksyxis', 'ServerCore', 'Clumps', 'Create']
W, H = 1600, 900
img = Image.new('RGB', (W, H), BG)
d = ImageDraw.Draw(img)
d.text((60, 40), 'Tested together with Sleepium 1.0.3', font=font(44, True), fill=INK)
d.text((60, 100), 'NeoForge 1.21.1, each alongside Sleepium and 13 of them at once; where another mod changes the same code,', font=font(26), fill=MUTED)
d.text((60, 136), 'Sleepium steps aside there. With Lithium or Moonrise its changes stay exact (world hashes).', font=font(26), fill=MUTED)
cols, cw, ch, gap = 4, 352, 92, 18
x0, y0 = 60, 210
for i, m in enumerate(mods):
    cx, cy = x0 + (i % cols) * (cw + gap), y0 + (i // cols) * (ch + gap)
    d.rounded_rectangle([cx, cy, cx + cw, cy + ch], radius=12, fill=(30, 36, 66), outline=GRID, width=2)
    d.ellipse([cx + 22, cy + 30, cx + 54, cy + 62], fill=SLEEPIUM)
    d.line([(cx + 30, cy + 46), (cx + 36, cy + 53), (cx + 47, cy + 38)], fill=BG, width=4)
    d.text((cx + 72, cy + 28), m, font=font(30, True), fill=INK)
img.save(os.path.join(OUT, 'compat.png'))
