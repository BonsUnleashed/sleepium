"""Server speed on a saved world: the world copied to run/world, an area force-loaded (no player needed), then
vanilla's /tick query, the engine's optimisations on (its default flags) or off.

    python tools/bench_world.py on "path/to/world" -304 304 -33 543 [--warm 60] [--secs 60] [--heap 4G] [--label L]

Mods the world needs go into run/mods. Prints and appends to run/bench.csv:
label,world,windows,avg_ms,p50,p95,p99 (max of the windows' P99)
"""
import argparse
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from server import Server, RUN

ap = argparse.ArgumentParser()
ap.add_argument('mode', choices=('on', 'off'))
ap.add_argument('world')
ap.add_argument('area', nargs=4, type=int, help='x1 z1 x2 z2 in blocks (at most 256 chunks)')
ap.add_argument('--warm', type=int, default=60)
ap.add_argument('--secs', type=int, default=60)
ap.add_argument('--heap', default='4G')
ap.add_argument('--flags', default='')
ap.add_argument('--label', default='')
a = ap.parse_args()
name = os.path.basename(os.path.normpath(a.world))
label = a.label or '%s-%s' % (name.replace(' ', '_'), a.mode)
PCT = re.compile(r'P50: ([\d.,]+) ?ms,? P95: ([\d.,]+) ?ms,? P99: ([\d.,]+) ?ms')
AVG = re.compile(r'Average time per tick: ([\d.,]+) ?ms')
num = lambda g: float(g.replace(',', '.'))
jvm = ['-Xms' + a.heap, '-Xmx' + a.heap, '-XX:+AlwaysPreTouch'] + (['-Dlab.opt.defaults=true'] if a.mode == 'on' else []) + a.flags.split()
with Server(jvm, {'spawn-monsters': 'true'}, log=os.path.join(RUN, 'bench-%s.log' % label), world=a.world) as s:
    s.wait_for(r'Done \(', timeout=900)
    s.cmd('forceload add %d %d %d %d' % tuple(a.area))
    time.sleep(a.warm)
    w = []
    for _ in range(max(1, a.secs // 5)):
        time.sleep(5)
        with s.lock:
            start = len(s.lines)
        s.cmd('tick query')
        m = s.wait_for(PCT.pattern, 20, start)
        with s.lock:
            avg = next(AVG.search(l) for l in s.lines[start:] if AVG.search(l))
        w.append((num(avg.group(1)), num(m.group(1)), num(m.group(2)), num(m.group(3))))
    errs = s.errors()
mid = len(w) // 2
row = '%s,%s,%d,%.2f,%.2f,%.2f,%.2f' % (label, name, len(w), sum(x[0] for x in w) / len(w), sorted(x[1] for x in w)[mid],
                                       sorted(x[2] for x in w)[mid], max(x[3] for x in w))
print(row)
with open(os.path.join(RUN, 'bench.csv'), 'a') as f:
    f.write(row + '\n')
for e in errs[:10]:
    print('!', e[:220])
