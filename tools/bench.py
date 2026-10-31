"""Server speed: the lag scenes measured by vanilla's own /tick query, the engine's optimisations on (its default flags)
or off (the same build, no flags: vanilla behaviour).

    python tools/bench.py on  items mobs hoppers ...   [--warm 40] [--secs 60] [--heap 4G] [--flags "..."]
    python tools/bench.py off items mobs hoppers ...

One server per call, a fresh flat world; per scene: lag_clear, the scene, --warm seconds for the scene and the JIT to
settle (the scene's chunks are force-loaded by the scene itself), then a /tick query every 5 s for --secs seconds
(vanilla's 100-tick windows). Prints and appends to run/bench.csv:
label,scene,windows,avg_ms (mean of the windows' averages),p50,p95,p99 (max of the windows' P99)
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
ap.add_argument('scenes', nargs='+')
ap.add_argument('--warm', type=int, default=40)
ap.add_argument('--secs', type=int, default=60)
ap.add_argument('--heap', default='4G')
ap.add_argument('--flags', default='', help='more JVM flags')
ap.add_argument('--label', default='')
a = ap.parse_args()
label = a.label or a.mode
AVG = re.compile(r'Average time per tick: ([\d.,]+) ?ms')   # the JVM's locale may write a decimal comma
PCT = re.compile(r'P50: ([\d.,]+) ?ms,? P95: ([\d.,]+) ?ms,? P99: ([\d.,]+) ?ms')


def num(g):
    return float(g.replace(',', '.'))


jvm = ['-Xms' + a.heap, '-Xmx' + a.heap] + (['-Dlab.opt.defaults=true'] if a.mode == 'on' else []) + a.flags.split()
rows = []
with Server(jvm, log=os.path.join(RUN, 'bench-%s.log' % label)) as s:
    s.wait_for(r'Done \(')
    time.sleep(5)
    for scene in a.scenes:
        s.cmd('function sleep:lag_clear')
        time.sleep(3)
        s.cmd('function sleep:lag_' + scene)
        time.sleep(3 + a.warm)
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
        mid = len(w) // 2
        row = '%s,%s,%d,%.2f,%.2f,%.2f,%.2f' % (label, scene, len(w), sum(x[0] for x in w) / len(w),
                                               sorted(x[1] for x in w)[mid], sorted(x[2] for x in w)[mid], max(x[3] for x in w))
        print(row, flush=True)
        rows.append(row)
    s.cmd('function sleep:lag_clear')
    time.sleep(2)
    errs = s.errors()
with open(os.path.join(RUN, 'bench.csv'), 'a') as f:
    f.writelines(r + '\n' for r in rows)
for e in errs[:20]:
    print('!', e[:220])
sys.exit(1 if errs else 0)
