"""Compatibility on the client: a dev client of this build with a folder of released mods (build.gradle -PtestMods),
the engine's defaults on, every mixin applied at once (-Dsleep.clientAuto.audit), a scene measured; then the run's log
read for what matters for compatibility: apply failures, crashes, our mixins that gave way (MixinYield / the chunk
renderer's yield), the foreign mixin index.

    python tools/compat_client.py <mods folder> [--scene client_entities] [--mode on|off] [--label L] [--secs 10]
Prints one summary line (and the yielded mixins); exit 1 when the client did not finish or a mixin failed to apply."""
import argparse
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ap = argparse.ArgumentParser()
ap.add_argument('mods')
ap.add_argument('--scene', default='client_entities')
ap.add_argument('--mode', default='on', choices=('on', 'off'))
ap.add_argument('--label', default='')
ap.add_argument('--secs', type=int, default=10)
ap.add_argument('--settle', type=int, default=30)
ap.add_argument('--flags', default='')
ap.add_argument('--no-audit', action='store_true')
ap.add_argument('--framehash', action='store_true', help='freeze and hash the frame (render exactness: compare off vs on)')
a = ap.parse_args()
name = os.path.basename(os.path.normpath(a.mods))
label = a.label or 'compat-%s-%s' % (name, a.mode)
flags = ('' if a.no_audit else '-Dsleep.clientAuto.audit=true ') + ('-Dsleep.clientAuto.framehash=true ' if a.framehash else '') + a.flags
p = subprocess.run([sys.executable, os.path.join(ROOT, 'tools', 'client_bench.py'), a.mode, a.scene, '--label', label, '--mods', a.mods,
                    '--secs', str(a.secs), '--settle', str(a.settle), '--flags=' + flags.strip()],
                   cwd=ROOT, capture_output=True, text=True, encoding='utf-8', errors='replace')
res = (p.stdout.strip().splitlines() or [''])[-1]
log = open(os.path.join(ROOT, 'run', 'client-bench-%s.out' % label), encoding='utf-8', errors='replace').read()
fails = sorted(set(re.findall(r'Mixin apply (?:for mod \S+ )?failed (\S+)', log)))
yielded = sorted(set(re.findall(r'\[sleep/opt\] (\w+) not applied: ([^\n]{0,160})', log)))
crash = re.findall(r'(?:---- Minecraft Crash Report ----|Exception in thread "[^"]+"|Encountered an unexpected exception)[^\n]*', log)
errors = [l for l in log.splitlines() if re.search(r'/ERROR\]|/FATAL\]|Exception', l)
          and not re.search(r"Failed to load function|Whilst parsing|Unknown block type 'create:|cursedfate|queue wait|lag_create|client_create", l)]
renderer = sorted(set(re.findall(r'\[sleep/[\w-]+\][^\n]*(?:yield|gives way|gave way|Embeddium|Rubidium|Oculus|Iris|sodium)[^\n]*', log, re.I)))
applying = len(re.findall(r'\[sleep/opt\] applying ', log))
fps = re.search(r',(\d+),[\d.]+,[\d.]+,\d+,[\d.]+$', res)
print('%s: %s | flagged on %d, gave way %d, apply failures %d, errors %d%s' % (
    name, ('%s fps' % fps.group(1)) if fps else 'NO RESULT', applying, len(yielded), len(fails), len(errors), ' | CRASH' if crash else ''))
for fh in re.findall(r'frame hash [0-9a-f]{16}[^\n]*', log):
    print('  ' + fh)
for m, why in yielded:
    print('  gave way: %s - %s' % (m, why))
for f in fails:
    m = re.search(re.escape(f) + r'[^\n]*?: ([^\n]{0,300})', log)
    print('  APPLY FAILED: %s | %s' % (f, m.group(1) if m else ''))
for l in renderer[:12]:
    print('  ' + l[:220])
for l in errors[:12]:
    print('  ! ' + l[:260])
for c in crash[:3]:
    print('  CRASH: ' + c[:260])
sys.exit(0 if fps and not fails else 1)
