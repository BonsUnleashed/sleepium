"""Exactness: a scene run twice from the same seeds - the engine's optimisations off, then on - and the results compared.

    python tools/exactness.py mobs [--ticks 2400] [--flags "-Dlab.sleep.components=true ..."] [--exclude villager]

Each run starts a fresh flat world, freezes the ticks, builds the scene at game time 300 (every entity's random seeded
from its id: -Dsleep.seedEntities), steps exactly --ticks ticks and hashes the scene's area: blocks, block entities and
every entity (position, motion, saved data and its fields: -Dsleep.hashEntitiesDeep). The 'on' run turns on the
engine's default flags (-Dlab.opt.defaults=true) plus --flags. Equal hashes and equal entity rows: the optimisations
changed nothing the game computes. Exit code 0 when equal.
"""
import argparse
import collections
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from server import Server, scene_area, RUN

ap = argparse.ArgumentParser()
ap.add_argument('scene')
ap.add_argument('--ticks', type=int, default=2400)
ap.add_argument('--flags', default='', help='more JVM flags for the "on" run')
ap.add_argument('--exclude', default='', help='entity types left out of the row comparison (comma separated)')
ap.add_argument('--monsters', action='store_true', help='keep summoned monsters (spawn-monsters=true)')
ap.add_argument('--jvm', default='', help='JVM flags for both runs')
ap.add_argument('--only', choices=('off', 'on'), help='one run only: print its hash (bisecting flags against a known hash)')
ap.add_argument('--offoff', action='store_true', help='both runs without the optimisations: is the scene deterministic at all')
a = ap.parse_args()
x1, z1, x2, z2 = scene_area(a.scene)
results = {}


def game_time(s):
    return int(s.ask('time query gametime', r'The time is (\d+)').group(1))


def wait_time(s, target):
    """polls the game time until the ticks stepped reach target"""
    while game_time(s) < target:
        time.sleep(1)


for mode in ((a.only,) if a.only else ('off', 'on')):
    rows = os.path.abspath(os.path.join(RUN, 'exactness-%s-%s-rows.txt' % (a.scene, mode)))
    jvm = ['-Dsleep.seedEntities=true', '-Dsleep.hashEntities=true', '-Dsleep.hashEntitiesDeep=true', '-Dsleep.hashRows=' + rows.replace('\\', '/')]
    jvm += a.jvm.split()
    if mode == 'on' and not a.offoff:
        jvm += ['-Dlab.opt.defaults=true'] + a.flags.split()
    log = os.path.join(RUN, 'exactness-%s-%s.log' % (a.scene, mode))
    with Server(jvm, {'spawn-monsters': 'true' if a.monsters else 'false'}, log) as s:
        s.wait_for(r'Done \(')
        s.cmd('forceload add %d %d %d %d' % (x1, z1, x2, z2))   # the scene's chunks load while the clock steps to 300
        s.cmd('tick freeze')
        now = game_time(s)
        if now < 300:
            s.cmd('tick step %d' % (300 - now))
            wait_time(s, 300)
        s.cmd('sleep reseed')
        s.cmd('function sleep:lag_clear')
        s.cmd('function sleep:lag_%s' % a.scene)
        s.cmd('tick step %d' % a.ticks)
        wait_time(s, 300 + a.ticks)
        h = s.ask('sleep hash %d -64 %d %d -40 %d' % (x1, z1, x2, z2), r'region hash (\w+) \(block entities (\d+), entities (\d+)')
        results[mode] = (h.group(1), int(h.group(2)), int(h.group(3)), rows)
        errs = s.errors()
    print('%-3s region hash %s  (block entities %d, entities %d)%s' % (mode, h.group(1), int(h.group(2)), int(h.group(3)),
                                                                      '  %d errors in the log' % len(errs) if errs else ''), flush=True)

if a.only:
    sys.exit(0)
skip = {'minecraft:' + t.strip() for t in a.exclude.split(',') if t.strip()}


def load(path):
    return collections.Counter(l for l in open(path, encoding='utf-8').read().splitlines() if l.split(' ', 1)[0] not in skip)


off, on = load(results['off'][3]), load(results['on'][3])
diff = (off - on) + (on - off)
by_type = collections.Counter(l.split(' ', 1)[0] for l in (off - on).elements())
same_hash = results['off'][0] == results['on'][0]
print('entities compared: %d; differing: %d %s' % (sum(off.values()), sum(diff.values()) // 2, dict(by_type) if by_type else ''))
print('EXACT' if not diff and (same_hash or skip) else 'DIFFERENT')
sys.exit(0 if not diff and (same_hash or skip) else 1)
