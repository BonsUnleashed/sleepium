"""Every block of some mods on the production NeoForge server with this build: each placed, ticked a while, and the
block-entity sleep analysis' verdict per class printed (eligible, verified by value and why, refused and why, analysis
failures). Is the sleep analysis universal: what it makes of mods' block entities it has never seen.

    python tools/mod_blockentities.py --jar build/libs/sleepium-....jar --mods F:/compat/x/mods [--ns modid,modid] [--secs 30]

The block ids come from the mods' lang files (block.<ns>.<name>); --ns limits them to those namespaces. The server's own
mods, world and properties are put back afterwards (as tools/mod_entities.py).
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
ap.add_argument('--ns', default='', help='block namespaces to place (default: every namespace of the mods\' lang files)')
ap.add_argument('--secs', type=int, default=30, help='seconds the blocks tick')
ap.add_argument('--server', default=r'F:\release-test\server')
ap.add_argument('--java', default=r'C:\Users\ha7gawa\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2\bin\java.exe')
ap.add_argument('--neoforge', default='21.1.251')
ap.add_argument('--launch', default='', help='the loader args file (default: NeoForge --neoforge); a Forge 1.20.1 server: libraries/net/minecraftforge/forge/1.20.1-47.4.9/win_args.txt')
ap.add_argument('--flags', default='')
a = ap.parse_args()

D = a.server
PROPS = os.path.join(D, 'server.properties')
FLAT = ('{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},'
        '{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","structure_overrides":[],"lakes":false,"features":false}')
props = {'online-mode': 'false', 'level-type': 'minecraft\\:flat', 'level-seed': '20260924', 'view-distance': '6',
         'level-name': 'modblocks-world', 'simulation-distance': '6', 'server-port': '25603', 'spawn-protection': '0',
         'max-tick-time': '-1', 'generator-settings': FLAT}

ids = []
want = set(x for x in a.ns.split(',') if x)
for f in sorted(os.listdir(a.mods)):
    if not f.endswith('.jar'):
        continue
    with zipfile.ZipFile(os.path.join(a.mods, f)) as z:
        for n in z.namelist():
            if re.match(r'assets/[^/]+/lang/en_us\.json$', n):
                for ns, name in re.findall(r'"block\.([a-z0-9_]+)\.([a-z0-9_]+)"', z.read(n).decode('utf-8', 'replace')):
                    if (not want or ns in want) and (ns + ':' + name) not in ids:
                        ids.append(ns + ':' + name)
print('%d blocks' % len(ids), flush=True)

saved_props = open(PROPS, encoding='utf-8').read()
keep = os.path.join(D, 'saved-mods-modblocks')
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
    p = subprocess.Popen([a.java, '-Xmx3G'] + a.flags.split() + ['@user_jvm_args.txt', '@' + (a.launch or 'libraries/net/neoforged/neoforge/%s/win_args.txt' % a.neoforge), 'nogui'],
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
        side = 40
        cmd('forceload add 0 0 %d %d' % (side * 3, (len(ids) // side + 1) * 3))
        for i, e in enumerate(ids):
            cmd('setblock %d -60 %d %s' % ((i % side) * 3, (i // side) * 3, e))
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

print('\n-- block-entity sleep verdicts')
for l in lines:
    if '[sleep/sleep]' in l:
        print(l[l.index('[sleep/sleep]'):][:500])
fails = [i for i, l in enumerate(lines) if 'analysis failed' in l or 'Exception' in l and 'sleep' in l]
print('\n-- failures and exceptions (%d)' % len(fails))
for i in fails[:10]:
    print('\n'.join(x[:250] for x in lines[i:i + 8]))
print('\n-- setblock errors')
for l in lines:
    if 'Unknown block' in l or 'Could not set' in l or 'Incorrect argument' in l:
        print(l[:200])
