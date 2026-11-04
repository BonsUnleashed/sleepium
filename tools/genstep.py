"""World generation exact, per step: a normal world (fixed seed), /sleep genbench generates a fresh square of chunks
while every generation step of every chunk in it records the hash of its window before and after the step
(GenBenchHooks, ChunkStepHashMixin; -Dsleep.genbench.hash=steps). Two runs - optimisations off vs on (the defaults) -
are compared step by step: where a step's input agrees, its output must agree.

On 1.20.1 the region's final blocks differ even between two vanilla runs (tools/genhash.py --offoff): chunks run
FEATURES in another order and each writes into its 3x3. So by default the square's FEATURES run in one fixed order
(-Dsleep.genbench.ordered: chunk by chunk, row by row; GenBench) and every step is compared. --unordered keeps the
game's own schedule (generation at full speed, ParallelGen's claims under load): a FEATURES step whose 3x3 input
already differs is then not judged ("input differs"), the radius-0 steps (structures, biomes, noise, surface,
carvers) still are for every chunk.

    python tools/genstep.py [--side 8] [--seed 20261104] [--flags "..."] [--offoff] [--unordered] [--batches N]
Exit code 1 when a step's output differs from the same input."""
import argparse
import collections
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from server import Server, RUN  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument('--side', type=int, default=8, help='chunks per side of the generated square')
ap.add_argument('--seed', default='20261104')
ap.add_argument('--flags', default='', help='more JVM flags for the "on" run')
ap.add_argument('--offflags', default='', help='JVM flags for the "off" run (e.g. one optimisation alone)')
ap.add_argument('--offoff', action='store_true', help='both runs without the optimisations: how much FEATURES order alone moves')
ap.add_argument('--batches', type=int, default=1, help='genbench squares per run (the second one starts with warm caches)')
ap.add_argument('--unordered', action='store_true', help="the game's own FEATURES order (see above)")
a = ap.parse_args()

props = {'level-type': 'minecraft\\:normal', 'generator-settings': '{}', 'level-seed': a.seed}
OUT = os.path.join(RUN, 'sleep')


def load(label):
    rows = collections.OrderedDict()
    with open(os.path.join(OUT, 'genstep-%s.txt' % label), encoding='utf-8') as f:
        for line in f:
            x, z, st, i, o = line.split()
            rows[(int(x), int(z), st)] = (i, o)
    return rows


runs = {}
for mode in ('off', 'on'):
    base = ['-Dsleep.genbench.hash=steps,carvers,full'] + ([] if a.unordered else ['-Dsleep.genbench.ordered=true'])
    jvm = base + a.offflags.split()
    if mode == 'on' and not a.offoff:
        jvm = base + ['-Dlab.opt.defaults=true'] + a.flags.split()
    t0 = time.time()
    labels = []
    with Server(jvm, props, os.path.join(RUN, 'genstep-%s.log' % mode)) as s:
        s.wait_for(r'Done \(', 900)
        s.cmd('tick freeze')   # generation only: no ticks of the loaded chunks while the square generates
        for b in range(a.batches):
            label = 'step-%s-%d' % (mode, b)
            m = s.ask('sleep genbench %d %s' % (a.side, label), r"genbench '%s': (\d+)/(\d+) chunks FULL in ([\d.]+) s .* = ([\d.]+) chunks/s" % label, 900)
            labels.append((label, m.group(0)))
            s.wait_for(r"genbench '%s' loads per tick" % label, 60)
        # not the engine's: the scenes of other test sets (mods absent here); GenProfile's queue lines name UniExceptionally
        errs = [e for e in s.errors() if not re.search(r'Failed to load function|Whilst parsing command|queue wait', e)]
    for label, line in labels:
        print('%-3s %s' % (mode, line), flush=True)
    print('%-3s %s%.0f s' % (mode, '%d errors in the log, ' % len(errs) if errs else '', time.time() - t0), flush=True)
    for e in errs[:5]:
        print('    ' + e)
    runs[mode] = labels

bad = 0
for b in range(a.batches):
    A, B = load(runs['off'][b][0]), load(runs['on'][b][0])
    # the off run's square and the on run's are the same chunks (the same run index -> the same position)
    stats = collections.OrderedDict()
    first = {}
    for key, (i1, o1) in A.items():
        st = key[2]
        c = stats.setdefault(st, collections.Counter())
        if key not in B:
            c['missing'] += 1
            continue
        i2, o2 = B[key]
        if i1 != i2:
            c['input differs'] += 1
        elif o1 != o2:
            c['STEP DIFFERS'] += 1
            first.setdefault(st, []).append(key[:2])
        else:
            c['same'] += 1
    print('batch %d (%s vs %s):' % (b, runs['off'][b][0], runs['on'][b][0]))
    for st, c in stats.items():
        n = sum(c.values())
        print('  %-28s %4d steps: same %4d, step differs %4d, input differs %4d%s' % (
            st, n, c['same'], c['STEP DIFFERS'], c['input differs'], ', missing %d' % c['missing'] if c['missing'] else ''))
    for st, ks in first.items():
        bad += len(ks)
        print('  %s differs from the same input at %s%s' % (st, ' '.join('%d,%d' % k for k in ks[:8]), ' ...' if len(ks) > 8 else ''))
print('EXACT per step' if bad == 0 else 'DIFFERENT: %d steps' % bad)
sys.exit(1 if bad else 0)
