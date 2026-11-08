"""Every entity type of some mods on the production NeoForge server with this build: each summoned, ticked a while, and
the entity sleep analysis' verdict per class printed (eligible, refused and why, analysis failures with their stack).
Compatibility checks of mods' entities (a mod's bytecode the analysis has never seen).

    python tools/mod_entities.py --jar build/libs/sleepium-....jar --mods F:/compat/x/mods [--ns modid,modid] [--secs 20]

The entity ids come from the mods' lang files (entity.<ns>.<name>); --ns limits them to those namespaces. The server's own
mods, world and properties are put back afterwards (as tools/detbench.py).
"""
import argparse
import os
import re
import shutil
import subprocess
import threading
import time
import zipfile

ap = argparse.ArgumentParser()
ap.add_argument('--jar', required=True)
ap.add_argument('--mods', required=True, help='a folder of mod jars (with their dependencies)')
ap.add_argument('--ns', default='', help='entity namespaces to summon (default: every namespace of the mods\' lang files)')
ap.add_argument('--secs', type=int, default=20, help='seconds the entities tick')
ap.add_argument('--server', default=r'F:\release-test\server')
ap.add_argument('--java', default=r'C:\Users\ha7gawa\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2\bin\java.exe')
ap.add_argument('--neoforge', default='21.1.251')
ap.add_argument('--flags', default='')
a = ap.parse_args()

D = a.server
PROPS = os.path.join(D, 'server.properties')
FLAT = ('{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},'
        '{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","structure_overrides":[],"lakes":false,"features":false}')
props = {'online-mode': 'false', 'level-type': 'minecraft\\:flat', 'level-seed': '20260924', 'view-distance': '6',
         'level-name': 'modentities-world', 'simulation-distance': '6', 'server-port': '25602', 'spawn-protection': '0',
         'max-tick-time': '-1', 'generator-settings': FLAT}

ids = []
want = set(x for x in a.ns.split(',') if x)
for f in sorted(os.listdir(a.mods)):
    if not f.endswith('.jar'):
        continue
    with zipfile.ZipFile(os.path.join(a.mods, f)) as z:
        for n in z.namelist():
            if re.match(r'assets/[^/]+/lang/en_us\.json$', n):
                for ns, name in re.findall(r'"entity\.([a-z0-9_]+)\.([a-z0-9_]+)"', z.read(n).decode('utf-8', 'replace')):
                    if (not want or ns in want) and (ns + ':' + name) not in ids:
                        ids.append(ns + ':' + name)
print('%d entity types: %s' % (len(ids), ' '.join(ids)), flush=True)

saved_props = open(PROPS, encoding='utf-8').read()
keep = os.path.join(D, 'saved-mods-modentities')
os.makedirs(keep, exist_ok=True)
for f in os.listdir(os.path.join(D, 'mods')):
    shutil.move(os.path.join(D, 'mods', f), os.path.join(keep, f))
lines = []
try:
    shutil.copy(a.jar, os.path.join(D, 'mods'))
    for f in os.listdir(a.mods):
        if f.endswith('.jar'):
            shutil.copy(os.path.join(a.mods, f), os.path.join(D, 'mods'))
    shutil.rmtree(os.path.join(D, props['level-name']), ignore_errors=True)
    with open(PROPS, 'w') as f:
        f.writelines('%s=%s\n' % kv for kv in props.items())
    p = subprocess.Popen([a.java, '-Xmx3G'] + a.flags.split() + ['@user_jvm_args.txt', '@libraries/net/neoforged/neoforge/%s/win_args.txt' % a.neoforge, 'nogui'],
                         cwd=D, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8',
                         errors='replace', bufsize=1)
    threading.Thread(target=lambda: [lines.append(l.rstrip('\n')) for l in p.stdout], daemon=True).start()

    def wait(rx, t):
        t0 = time.time()
        while time.time() - t0 < t:
            if any(re.search(rx, l) for l in lines):
                return True
            if p.poll() is not None:
                return False
            time.sleep(0.5)
        return False

    def cmd(c):
        p.stdin.write(c + '\n')
        p.stdin.flush()

    if not wait(r'Done \(', 600):
        print('server did not start; last lines:\n' + '\n'.join(lines[-15:]))
    else:
        print('server up', flush=True)
        cmd('forceload add -32 -32 64 64')
        for i, e in enumerate(ids):
            cmd('summon %s %d -59 %d' % (e, (i % 10) * 3, (i // 10) * 3))
        time.sleep(a.secs)
        cmd('stop')
        p.wait(timeout=120)
finally:
    with open(PROPS, 'w', encoding='utf-8') as f:
        f.write(saved_props)
    for f in os.listdir(os.path.join(D, 'mods')):
        os.remove(os.path.join(D, 'mods', f))
    for f in os.listdir(keep):
        shutil.move(os.path.join(keep, f), os.path.join(D, 'mods', f))

print('\n-- entity sleep verdicts')
for l in lines:
    if '[sleep/entsleep]' in l:
        print(l[l.index('[sleep/entsleep]'):][:300])
fails = [i for i, l in enumerate(lines) if 'analysis failed' in l or 'Exception' in l and 'sleep' in l]
print('\n-- failures and exceptions (%d)' % len(fails))
for i in fails[:10]:
    print('\n'.join(x[:250] for x in lines[i:i + 8]))
print('\n-- summon errors')
for l in lines:
    if 'Unable to summon' in l or 'Unknown entity' in l or 'Incorrect argument' in l:
        print(l[:200])
