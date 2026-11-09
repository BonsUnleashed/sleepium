"""Quick exactness for urgent patches: every scene of the suite in few server runs instead of two per scene.

    python tools/quick_exactness.py [--scenes mobs,hoppers] [--ticks 2400] [--mobsleep] [--step] [--no-followup]

The scenes are put into batches whose areas do not overlap (zoo, with monsters on, in a batch of its own); each batch
is built in one world and run twice from the same seeds - the engine's optimisations off, then on - and every scene's
area is hashed on its own. The ticks run back to back (tick sprint; --step: at 20 a second as in exactness.py): a
sprinted world is not the stepped one (background chunk work lands on other ticks), so the hashes are not those of
the full suite, but off and on must still be equal. A scene that differs is run again alone through tools/exactness.py
(the same mode) for its rows. --mobsleep adds an 'on' run with the mob sleep flags for the batches holding mobs or
calmzoo (compared with the same 'off' run). One line per scene into run/quick-exactness.txt. Exit code 0: all equal.
About 6 server runs per version instead of 48 - minutes, not an hour and a half; the full suite stays the release check.

The 'off' results are kept in run/quick-off-cache.json (keyed by the batch, the ticks, sprint / step, monsters and the
contents of the scenes' functions): only the 'on' runs are made while they match. Vanilla's results do not move with
the optimisations, but they could with the harness (seeding, hashing): so the cache checks itself - a scene whose 'on'
differs from a kept 'off' gets a fresh 'off' run first, and only a difference from that counts. --fresh: no kept 'off'.
"""
import argparse
import hashlib
import json
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from server import Server, scene_area, RUN, SCENES  # noqa: E402

ALL = 'restcarts items itemwake carts vehicles cramming physics hoppers furnaces smelters beactive bezoo pistons redstone crops fluids mobs villagers jobclash calmzoo zoo'.split()
MOBSLEEP = '-Dlab.sleep.components=true -Dlab.sleep.mobRuntime=true -Dlab.opt.WriteBarrier=true'   # exactness_all.sh's, when it cannot be read
MONSTERS = {'zoo'}
SETTLE = os.path.basename(SCENES) == 'functions'   # 1.20.1: light settled after the scene is built (exactness.py does)

ap = argparse.ArgumentParser()
ap.add_argument('--scenes', default=','.join(ALL))
ap.add_argument('--ticks', type=int, default=2400)
ap.add_argument('--mobsleep', action='store_true')
ap.add_argument('--step', action='store_true', help='ticks at 20 a second (tick step) instead of back to back')
ap.add_argument('--no-followup', action='store_true', help='no exactness.py run for a scene that differs')
ap.add_argument('--flags', default='', help='more JVM flags for the "on" runs')
ap.add_argument('--fresh', action='store_true', help='run every "off" (the kept results neither read nor trusted)')
a = ap.parse_args()


def mobsleep_flags():
    """tools/exactness_all.sh's MOBSLEEP value, when it has one"""
    p = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'exactness_all.sh')
    for line in open(p, encoding='utf-8'):
        if line.startswith('MOBSLEEP='):
            return line.split('=', 1)[1].strip().strip('"').strip("'")
    return MOBSLEEP


def batches(scenes):
    areas = {s: scene_area(s) for s in scenes}

    def overlap(p, q):
        x1, z1, x2, z2 = areas[p]
        X1, Z1, X2, Z2 = areas[q]
        return not (x2 < X1 or X2 < x1 or z2 < Z1 or Z2 < z1)

    out = []
    for monsters in (False, True):
        group = []
        for s in scenes:
            if (s in MONSTERS) != monsters:
                continue
            for b in group:
                if not any(overlap(s, t) for t in b):
                    b.append(s)
                    break
            else:
                group.append([s])
        out += [(b, monsters) for b in group]
    return out, areas


def game_time(s):
    return int(s.ask('time query gametime', r'The time is (\d+)').group(1))


def wait_time(s, target, timeout=1800):
    t0 = time.time()
    while game_time(s) < target:
        if time.time() - t0 > timeout:
            raise SystemExit('the clock stopped at %d (wanted %d)' % (game_time(s), target))
        time.sleep(1)


def run(batch, monsters, areas, mode, flags):
    """one world: every scene of the batch built at game time 300, the ticks run, each area hashed"""
    jvm = ['-Dsleep.seedEntities=true', '-Dsleep.hashEntities=true', '-Dsleep.hashEntitiesDeep=true', '-Dsleep.hashClientNbt=true']
    if mode != 'off':
        jvm += ['-Dlab.opt.defaults=true'] + flags
    log = os.path.join(RUN, 'quick-%s-%s.log' % ('+'.join(batch)[:60], mode))
    with Server(jvm, {'spawn-monsters': 'true' if monsters else 'false'}, log) as s:
        s.wait_for(r'Done \(')
        for sc in batch:
            x1, z1, x2, z2 = areas[sc]
            s.cmd('forceload add %d %d %d %d' % (x1, z1, x2, z2))
        s.cmd('tick freeze')
        now = game_time(s)
        if now < 300:
            s.cmd('tick step %d' % (300 - now))
            wait_time(s, 300)
        s.cmd('sleep reseed')
        s.cmd('function sleep:lag_clear')
        for sc in batch:
            s.cmd('function sleep:lag_%s' % sc)
        if SETTLE:
            s.cmd('sleep settle')
        t0 = time.time()
        s.cmd('tick %s %d' % ('step' if a.step else 'sprint', a.ticks))
        wait_time(s, 300 + a.ticks)
        secs = time.time() - t0
        out = {}
        for sc in batch:
            x1, z1, x2, z2 = areas[sc]
            h = s.ask('sleep hash %d -64 %d %d -40 %d' % (x1, z1, x2, z2), r'region hash (\w+) \(block entities (\d+), entities (\d+)')
            out[sc] = (h.group(1), int(h.group(2)), int(h.group(3)))
        return out, len(s.errors()), secs


CACHE = os.path.join(RUN, 'quick-off-cache.json')


def cache_key(batch, monsters):
    h = hashlib.sha1()
    for name in ['clear'] + batch:
        with open(os.path.join(SCENES, 'lag_%s.mcfunction' % name), 'rb') as f:
            h.update(f.read())
    return '%s|%d|%s|%s|%s' % ('+'.join(batch), a.ticks, 'step' if a.step else 'sprint', monsters, h.hexdigest()[:12])


def load_cache():
    try:
        with open(CACHE, encoding='utf-8') as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def save_cache(c):
    with open(CACHE, 'w', encoding='utf-8') as f:
        json.dump(c, f, indent=1)


def main():
    scenes = [s for s in a.scenes.split(',') if s]
    plan, areas = batches(scenes)
    flags = a.flags.split()
    ms = mobsleep_flags().split()
    report = os.path.join(RUN, 'quick-exactness.txt')
    t_all = time.time()
    differing = []
    with open(report, 'a', encoding='utf-8') as rep:
        rep.write('=== %s quick (%s, %d ticks)\n' % (time.strftime('%F %T'), 'step' if a.step else 'sprint', a.ticks))
        for batch, monsters in plan:
            print('batch: %s%s' % (', '.join(batch), ' (monsters)' if monsters else ''), flush=True)
            cache = load_cache()
            key = cache_key(batch, monsters)
            kept = None if a.fresh else cache.get(key)
            if kept:
                off, e_off, s_off = {sc: tuple(v) for sc, v in kept.items()}, 0, 0
                print('   off: kept results', flush=True)
            else:
                off, e_off, s_off = run(batch, monsters, areas, 'off', [])
                cache[key] = off
                save_cache(cache)
            variants = [('defaults', flags)]
            if a.mobsleep and any(sc in ('mobs', 'calmzoo') for sc in batch):
                variants.append(('defaults+mob sleep', flags + ms))
            for name, vf in variants:
                on, e_on, s_on = run(batch, monsters, areas, 'on', vf)
                if kept and any(off[sc] != on[sc] for sc in batch):
                    print('   a scene differs from the kept off: off run again', flush=True)
                    off, e_off, s_off = run(batch, monsters, areas, 'off', [])
                    kept = None
                    cache = load_cache()
                    cache[key] = off
                    save_cache(cache)
                for sc in batch:
                    same = off[sc] == on[sc]
                    line = '%-10s %-19s off %s on %s  (block entities %d, entities %d)  %s' % (
                        sc, name, off[sc][0], on[sc][0], on[sc][1], on[sc][2], 'EXACT' if same else 'DIFFERENT')
                    print(line, flush=True)
                    rep.write(line + '\n')
                    if not same:
                        differing.append((sc, name, vf))
                print('   ticks ran in %.0f s off, %.0f s on; errors in the logs: %d off, %d on' % (s_off, s_on, e_off, e_on), flush=True)
        rep.write('--- %.1f min, %d differing\n' % ((time.time() - t_all) / 60, len(differing)))
    print('%.1f min, %d of %d differing' % ((time.time() - t_all) / 60, len(differing), len(scenes)), flush=True)
    if differing and not a.no_followup:
        for sc, name, vf in differing:
            print('--- %s %s alone through exactness.py' % (sc, name), flush=True)
            cmd = [sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'exactness.py'), sc]
            cmd += ([] if a.step else ['--sprint']) + (['--monsters'] if sc in MONSTERS else []) + (['--flags', ' '.join(vf)] if vf else [])
            subprocess.run(cmd)
    return 1 if differing else 0


if __name__ == '__main__':
    sys.exit(main())
