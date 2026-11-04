"""Every mixin applied on a dev server with the engine's defaults (/sleep audit): prints the apply failures; with
BUSCHECK=1 the composite event bus checked against Forge's own first (/sleep buscheck).
    python tools/audit.py"""
import os, re, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from server import Server, RUN

log = os.path.join(RUN, 'audit.log')
with Server(['-Dlab.opt.defaults=true'], {}, log) as s:
    s.wait_for(r'Done \(', 300)
    if os.environ.get('BUSCHECK'):
        s.ask('sleep buscheck', r'bus check: (OK|FAILED)', 120)
        time.sleep(1)
    s.cmd('sleep audit')
    s.wait_for(r'mixin audit done', 600)
    time.sleep(2)
txt = open(log, encoding='utf-8', errors='replace').read()
fails = sorted(set(re.findall(r'Mixin apply (?:for mod \S+ )?failed (\S+)', txt)))
print('apply failures:', len(fails))
for f in fails:
    m = re.search(re.escape(f) + r'[^\n]*?: ([^\n]{0,220})', txt)
    print(' ', f, '|', m.group(1) if m else '')
for block in re.findall(r'bus check: [^\n]*(?:\n  [^\n]*)*', txt)[:1]:
    print(block[:3000])
for line in re.findall(r'\[sleep/opt\] (?:MinecraftForge\.EVENT_BUS|event sites)[^\n]*', txt)[:20]:
    print(' ', line)
for line in re.findall(r'[^\n]*(?:Exception|/ERROR\]|/FATAL\])[^\n]*', txt):
    if not re.search(r"No key layers|lag_create|Unknown block type 'create:|client_create", line):
        print('  !', line[:300])
