"""The front page's charts (art/gallery/): horizontal bar pairs, the reference in gray and Sleepium in the accent (the
emphasis form: one series is the point), each bar labelled with its value, one scale per chart.
    python tools/gen_charts.py
"""
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'art', 'gallery')
FONTS = r'C:\Windows\Fonts'
BG, INK, MUTED, GRID = (20, 24, 46), (236, 239, 255), (150, 160, 200), (52, 58, 92)
REF, ACCENT = (128, 136, 168), (201, 133, 0)   # #8088a8 de-emphasis, #c98500 accent (validated on #14182e)


def font(size, bold=False):
    for n in (['segoeuib.ttf', 'arialbd.ttf'] if bold else ['segoeui.ttf', 'arial.ttf']):
        p = os.path.join(FONTS, n)
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def nice_max(v):
    """the smallest round axis end at or above v, with its grid step (4 to 6 steps)"""
    best = None
    for step in (1, 2, 2.5, 5):
        for mag in (1, 10, 100, 1000):
            m = step * mag
            for n in (4, 5, 6):
                if m * n >= v and (best is None or m * n < best[0]):
                    best = (m * n, m)
    return best


def chart(name, title, subtitle, rows, labels, unit, note=None, label_w=520):
    """rows: (label, ref value, sleepium value, ref text, sleepium text)"""
    W = 1600
    row_h = 112
    top = 230
    H = top + row_h * len(rows) + (110 if note else 70)
    img = Image.new('RGB', (W, H), BG)
    d = ImageDraw.Draw(img)
    d.text((60, 40), title, font=font(44, True), fill=INK)
    d.text((60, 102), subtitle, font=font(26), fill=MUTED)
    # legend: a swatch beside each name, the text in ink
    lx = 60
    for col, txt in ((REF, labels[0]), (ACCENT, labels[1])):
        d.rounded_rectangle([lx, 160, lx + 28, 184], 4, fill=col)
        d.text((lx + 40, 154), txt, font=font(26), fill=INK)
        lx += 40 + d.textlength(txt, font=font(26)) + 60
    left, right = label_w, W - 200
    vmax, step = nice_max(max(max(r[1], r[2]) for r in rows))
    scale = (right - left) / vmax
    y0, y1 = top - 14, top + row_h * len(rows) - 10
    t = 0.0
    while t <= vmax + 1e-9:
        gx = left + t * scale
        d.line([(gx, y0), (gx, y1)], fill=GRID, width=2)
        s = ('%g' % t)
        d.text((gx - d.textlength(s, font=font(22)) / 2, y1 + 8), s, font=font(22), fill=MUTED)
        t += step
    d.text((right + 50, y1 + 8), unit, font=font(22), fill=MUTED)
    for i, (lab, a, b, at, bt) in enumerate(rows):
        y = top + i * row_h
        d.text((60, y + 22), lab, font=font(28, True), fill=INK)
        for j, (v, txt, col) in enumerate(((a, at, REF), (b, bt, ACCENT))):
            by = y + 6 + j * 44
            x1 = left + max(v * scale, 6)
            d.rounded_rectangle([left, by, x1, by + 36], 4, fill=col)   # 4px rounded end, 2px gap between the pair
            d.rectangle([left, by, left + 4, by + 36], fill=col)          # square at the baseline
            d.text((x1 + 14, by + 2), txt, font=font(26, True), fill=INK)
    if note:
        d.text((60, H - 70), note, font=font(22), fill=MUTED)
    img.save(os.path.join(OUT, name))
    print(name, img.size)


chart('forge-1201-n100.png', 'Minecraft 1.20.1 / Forge on an Intel N100 server',
      'milliseconds per tick from vanilla\'s /tick query, production server, lower is better',
      [('4 000 item stacks', 114.9, 10.7, '114.9', '10.7'), ('120 villagers', 19.3, 14.0, '19.3', '14.0'),
       ('300 animals + 50 villagers', 22.2, 13.25, '22.2', '13.25'), ('1 024 idle hoppers', 6.68, 2.4, '6.68', '2.4')],
      ('vanilla (the same jar, optimisations off)', 'Sleepium 1.1.0'), 'ms')

chart('cursedfate-fight.png', 'A Cursed Fate fight on the client (Minecraft 1.20.1)',
      '840 entities, zombies burning at noon, the set\'s effects; frames per second, higher is better; RTX 4070 SUPER',
      [('no other mods', 87, 623, '87', '600-646'), ('with Oculus + Embeddium', 289, 416, '289', '408-423')],
      ('without Sleepium', 'Sleepium 1.1.0'), 'fps', note='Bars at the middle of each measured range.')

chart('next-to-mods-110.png', 'Next to other optimisation mods: what 1.1.0 brings back',
      'client_entities (armour stands, item frames, chests, signs, mobs), frames per second, higher is better',
      [('Embeddium (1.20.1)', 137, 845, '137', '845'), ('Oculus + Embeddium (1.20.1)', 238, 452, '238', '440-464'),
       ('ImmediatelyFast (1.20.1)', 371, 681, '371', '681'), ('Iris + Sodium, no pack (1.21.1)', 377, 570, '377', '570'),
       ('ImmediatelyFast (1.21.1)', 809, 1149, '809', '1 149')],
      ('Sleepium before 1.1.0, with the same mod', 'Sleepium 1.1.0'), 'fps',
      note='Same machine and scene; frames bit for bit as before. Server: items next to Lithium 4.32 -> 3.37 ms per tick.')
