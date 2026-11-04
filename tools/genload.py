"""Chunk loading exact: a normal world's square generated and saved once (no optimisations), then read back from disk
twice - optimisations off vs on (the defaults, plus --flags, e.g. -Dlab.opt.WorldgenReactor=true for its chunk-load
path: reads on the pool with the ReadJournal) - and each read world's square hashed when it is FULL again (per-chunk
block hashes at ChunkEvent.Load, the square's block entities as /sleep hash writes their rows). The region hash of
/sleep hash is printed but not compared: a full chunk's post-processing (shape updates of marked blocks, when it
starts ticking) reads its neighbours, loaded at other moments in every run - it differs even off vs off on 1.20.1.

    python tools/genload.py [--side 8] [--seed 20261104] [--flags "..."]"""
import argparse
import os
import shutil
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from server import Server, RUN  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument('--side', type=int, default=8)
ap.add_argument('--seed', default='20261104')
ap.add_argument('--flags', default='')
ap.add_argument('--offoff', action='store_true', help='both reads without the optimisations')
ap.add_argument('--keep', action='store_true', help='read the world saved by the last run again (no generation)')
a = ap.parse_args()
props = {'level-type': 'minecraft\\:normal', 'generator-settings': '{}', 'level-seed': a.seed}
SAVED = os.path.join(RUN, 'genload-world')
OUT = os.path.join(RUN, 'sleep')
x1 = z1 = 4000 * 16   # GenBench's first square (run index 0)
x2 = z2 = x1 + a.side * 16 - 1
BENCH = r"genbench '%s': (\d+)/(\d+) chunks FULL in ([\d.]+) s .* = ([\d.]+) chunks/s"

t0 = time.time()
if not a.keep:
    with Server(['-Dsleep.genbench.hash=none'], props, os.path.join(RUN, 'genload-gen.log')) as s:
        s.wait_for(r'Done \(', 900)
        s.cmd('tick freeze')
        s.ask('sleep genbench %d load-gen' % a.side, BENCH % 'load-gen', 600)
        s.ask('save-all flush', r'Saved the game', 300)
    shutil.rmtree(SAVED, ignore_errors=True)
    shutil.copytree(os.path.join(RUN, 'world'), SAVED, ignore=shutil.ignore_patterns('session.lock'))
    print('generated and saved %dx%d chunks, %.0f s' % (a.side, a.side, time.time() - t0), flush=True)

res = {}
for mode in ('off', 'on'):
    jvm = ['-Dsleep.genbench.hash=full', '-Dsleep.hashBeRows=' + os.path.join(RUN, 'genload-be-%s.txt' % mode)]
    if mode == 'on' and not a.offoff:
        jvm += ['-Dlab.opt.defaults=true'] + a.flags.split()
    t0 = time.time()
    label = 'load-%s' % mode
    with Server(jvm, props, os.path.join(RUN, 'genload-%s.log' % mode), world=SAVED) as s:
        s.wait_for(r'Done \(', 900)
        s.cmd('tick freeze')
        line = s.ask('sleep genbench %d %s' % (a.side, label), BENCH % label, 600).group(0)
        h = s.ask('sleep hash %d -64 %d %d 319 %d' % (x1, z1, x2, z2), r'region hash (\w+) \(block entities (\d+)', 600)
        errs = [e for e in s.errors() if 'Failed to load function' not in e and 'Whilst parsing' not in e and 'queue wait' not in e]
    with open(os.path.join(OUT, 'genhash-%s.txt' % label), encoding='utf-8') as f:
        chunks = dict((tuple(l.split()[:2]), l.split()[2]) for l in f)
    rep = open(os.path.join(OUT, 'genbench-%s.txt' % label), encoding='utf-8').read()
    reads = [p.strip() for p in rep.split(';') if 'chunk reads' in p]
    res[mode] = (h.group(1), int(h.group(2)), chunks)
    print('%-3s %s | region %s (block entities %s)%s%s, %.0f s' % (mode, line, h.group(1), h.group(2),
          ' | ' + reads[0] if reads else '', ' | %d errors in the log' % len(errs) if errs else '', time.time() - t0), flush=True)
    for e in errs[:5]:
        print('    ' + e)
same = sum(1 for k, v in res['off'][2].items() if res['on'][2].get(k) == v)
unseen = sum(1 for v in res['off'][2].values() if v == '0')
print('chunks equal at load: %d/%d%s' % (same, len(res['off'][2]), ' (%d without a load seen)' % unseen if unseen else ''))
def rows(mode):
    with open(os.path.join(RUN, 'genload-be-%s.txt' % mode), encoding='utf-8') as f:
        return sorted(f.read().splitlines())


be_same = rows('off') == rows('on')
print('block entities: %s (%d)' % ('equal' if be_same else 'DIFFERENT', len(rows('off'))))
ok = be_same and res['off'][1] == res['on'][1] and same == len(res['off'][2])
print('EXACT' if ok else 'DIFFERENT')
sys.exit(0 if ok else 1)
