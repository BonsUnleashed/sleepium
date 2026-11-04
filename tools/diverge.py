"""Where two runs of a scene part: the region hashed after every tick (two runs, both without the optimisations unless
--on), the first tick whose hash differs and the entity rows that differ there.
    python tools/diverge.py mobs [--ticks 40] [--on] [--jvm=...]"""
import argparse
import collections
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from server import Server, RUN, scene_area  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument('scene')
ap.add_argument('--ticks', type=int, default=40)
ap.add_argument('--on', action='store_true', help='the second run with the defaults (off vs on), else off vs off')
ap.add_argument('--jvm', default='')
ap.add_argument('--every', type=int, default=1, help='ticks per step between hashes')
a = ap.parse_args()
x1, z1, x2, z2 = scene_area(a.scene)


def game_time(s):
    return int(s.ask('time query gametime', r'The time is (\d+)').group(1))


def run(label, extra):
    rows = os.path.abspath(os.path.join(RUN, 'diverge-%s-rows.txt' % label)).replace('\\', '/')
    jvm = ['-Dsleep.seedEntities=true', '-Dsleep.hashEntities=true', '-Dsleep.hashIds=true', '-Dsleep.hashEntitiesDeep=true', '-Dsleep.deepHashDump=true', '-Dsleep.hashRows=' + rows] + a.jvm.split() + extra
    hashes, kept = [], {}
    with Server(jvm, {}, os.path.join(RUN, 'diverge-%s.log' % label)) as s:
        s.wait_for(r'Done \(')
        s.cmd('forceload add %d %d %d %d' % (x1, z1, x2, z2))
        s.cmd('tick freeze')
        now = game_time(s)
        if now < 300:
            s.ask('tick step %d' % (300 - now), r'Stepping')
            while game_time(s) < 300:
                time.sleep(0.05)
        s.cmd('sleep reseed')
        s.cmd('function sleep:lag_clear')
        s.cmd('function sleep:lag_%s' % a.scene)
        s.cmd('sleep settle')
        for t in range(0, a.ticks + 1, a.every):
            if t:
                s.cmd('tick step %d' % a.every)
                while game_time(s) < 300 + t:
                    time.sleep(0.05)
            h = s.ask('sleep hash %d -64 %d %d -40 %d' % (x1, z1, x2, z2), r'region hash (\w+) .*game time (\d+)')
            hashes.append(h.group(1))
            kept[t] = open(rows.replace('/', os.sep), encoding='utf-8').read().splitlines()
    return hashes, kept


ha, ra = run('a', [])
hb, rb = run('b', ['-Dlab.opt.defaults=true'] if a.on else [])
for i in range(len(ha)):
    t = i * a.every
    if ha[i] != hb[i]:
        da, db = collections.Counter(ra[t]), collections.Counter(rb[t])
        only_a, only_b = sorted((da - db).elements()), sorted((db - da).elements())
        print('first differing tick after the scene: %d (%d rows differ)' % (t, len(only_a)))
        for side, rows_ in (('a', only_a), ('b', only_b)):
            with open(os.path.join(RUN, 'diverge-first-%s.txt' % side), 'w', encoding='utf-8') as f:
                f.writelines(r + '\n' for r in rows_)
        for l in only_a[:3]:
            print('  a:', l[:600])
        for l in only_b[:3]:
            print('  b:', l[:600])
        break
else:
    print('same hash on every tick 0..%d' % a.ticks)
