"""Client speed: a self-driving dev client of this build (./gradlew runClient) on one scene, the engine's optimisations
on (its default flags) or off (the same build, no flags: vanilla behaviour).

    python tools/client_bench.py on client_entities [--label L] [--settle 40] [--secs 30] [--heap 3G] [--flags "..."]

ClientAuto (-Dsleep.clientAuto) makes a singleplayer world (fixed seed), waits --settle seconds for the terrain, builds
the scene, measures --secs seconds at a fixed position (vsync off, unlimited fps) and quits. Window 1280x720, heap
pre-touched. Prints the result line and appends it to run/client-bench.csv:
label,scene,fps,frame p50 ms,frame p99 ms,KiB per frame,process cores
"""
import argparse
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRADLEW = os.path.join(ROOT, 'gradlew.bat' if os.name == 'nt' else 'gradlew')

ap = argparse.ArgumentParser()
ap.add_argument('mode', choices=('on', 'off'))
ap.add_argument('scene')
ap.add_argument('--label', default='')
ap.add_argument('--settle', type=int, default=40)
ap.add_argument('--secs', type=int, default=30)
ap.add_argument('--heap', default='3G')
ap.add_argument('--flags', default='')
a = ap.parse_args()
label = a.label or '%s-%s' % (a.scene, a.mode)
jvm = ['-Xms' + a.heap, '-Xmx' + a.heap, '-XX:+AlwaysPreTouch', '-Dsleep.clientAuto=' + label, '-Dsleep.clientAuto.scene=' + a.scene,
       '-Dsleep.clientAuto.steady=true', '-Dsleep.clientAuto.steadySeconds=%d' % a.secs, '-Dsleep.clientAuto.settle=%d' % a.settle]
jvm += (['-Dlab.opt.defaults=true'] if a.mode == 'on' else []) + a.flags.split()
p = subprocess.run([GRADLEW, 'runClient', '--console=plain', '-q', '-PrunJvmArgs=' + ' '.join(jvm), '-PrunArgs=--width 1280 --height 720'],
                   cwd=ROOT, capture_output=True, text=True, encoding='utf-8', errors='replace')
out = p.stdout + p.stderr
m = re.search(r"sleepclient '%s': (\d+) fps, frame p50 ([\d.]+) / p99 ([\d.]+) ms, (\d+) KiB/frame, CPU ([\d.]+) cores" % re.escape(label), out)
if not m:
    print('no result; the end of the log:\n' + '\n'.join(out.splitlines()[-30:]))
    sys.exit(1)
row = ','.join([label, a.scene] + list(m.groups()))
print(row)
with open(os.path.join(ROOT, 'run', 'client-bench.csv'), 'a') as f:
    f.write(row + '\n')
