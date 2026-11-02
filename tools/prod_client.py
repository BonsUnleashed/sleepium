"""A production NeoForge client, launched the way a launcher does it (release tests: the built jar, no dev setup).

    python tools/prod_client.py --dir F:/release-test/client [--mods a.jar,b.jar] [--jvm "-Dx=y ..."] [--secs 120]

--dir is a folder the NeoForge installer filled (java -jar neoforge-<v>-installer.jar --install-client <dir>, with an
empty launcher_profiles.json in it). Vanilla libraries missing there are taken from the official launcher's folder
(%APPDATA%/.minecraft) or downloaded; assets come from the official launcher's folder. Offline player "Tester".
The game's console goes to <dir>/console.log.
"""
import argparse
import json
import os
import subprocess
import sys
import urllib.request

ap = argparse.ArgumentParser()
ap.add_argument('--dir', required=True)
ap.add_argument('--version', default='neoforge-21.1.251')
ap.add_argument('--java', default=r'F:\forminecrfatmods\jdk-21.0.2\bin\java.exe')
ap.add_argument('--mods', default='', help='jars copied into mods/ (the folder is emptied first)')
ap.add_argument('--keep-mods', action='store_true')
ap.add_argument('--jvm', default='')
ap.add_argument('--game', default='', help='extra game arguments, e.g. "--quickPlayMultiplayer localhost"')
ap.add_argument('--secs', type=int, default=0, help='kill the game after this many seconds (0: wait for it)')
a = ap.parse_args()

root = os.path.abspath(a.dir)
official = os.path.join(os.environ.get('APPDATA', ''), '.minecraft')
libdir = os.path.join(root, 'libraries')


def load(v):
    d = json.load(open(os.path.join(root, 'versions', v, v + '.json'), encoding='utf-8'))
    if 'inheritsFrom' in d:
        base = load(d['inheritsFrom'])
        base['libraries'] = d.get('libraries', []) + base['libraries']
        for k in ('jvm', 'game'):
            base.setdefault('arguments', {}).setdefault(k, [])
            base['arguments'][k] = base['arguments'][k] + d.get('arguments', {}).get(k, [])
        base['mainClass'] = d['mainClass']
        base['id'] = d['id']
        return base
    return d


def allowed(rules):
    ok = not rules
    for r in rules or []:
        if 'features' in r:
            continue
        os_ = r.get('os', {})
        match = (not os_) or os_.get('name') == 'windows'
        if match:
            ok = r['action'] == 'allow'
    return ok


v = load(a.version)
cp = []
seen = set()
for lib in v['libraries']:
    if not allowed(lib.get('rules')):
        continue
    parts = lib['name'].split(':')
    key = ':'.join(parts[:2] + parts[3:])   # group:artifact[:classifier] - the first (the loader's) version wins
    if key in seen:
        continue
    seen.add(key)
    art = lib.get('downloads', {}).get('artifact')
    if not art:
        continue
    path = os.path.join(libdir, art['path'])
    if not os.path.exists(path):
        alt = os.path.join(official, 'libraries', art['path'])
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if os.path.exists(alt):
            with open(alt, 'rb') as s, open(path, 'wb') as d:
                d.write(s.read())
        elif art.get('url'):
            urllib.request.urlretrieve(art['url'], path)
    cp.append(path)
# (not the vanilla client jar: NeoForge runs its patched client from the libraries)

mods = os.path.join(root, 'mods')
os.makedirs(mods, exist_ok=True)
if not a.keep_mods:
    for f in os.listdir(mods):
        if f.endswith('.jar'):
            os.remove(os.path.join(mods, f))
for m in filter(None, a.mods.split(',')):
    with open(m, 'rb') as s, open(os.path.join(mods, os.path.basename(m)), 'wb') as d:
        d.write(s.read())

subst = {
    '${library_directory}': libdir, '${classpath_separator}': os.pathsep, '${version_name}': a.version,
    '${natives_directory}': os.path.join(root, 'natives'), '${launcher_name}': 'prod_client', '${launcher_version}': '1',
    '${classpath}': os.pathsep.join(cp), '${auth_player_name}': 'Tester', '${version_type}': 'release',
    '${game_directory}': root, '${assets_root}': os.path.join(official, 'assets'), '${assets_index_name}': v['assetIndex']['id'],
    '${auth_uuid}': '00000000-0000-0000-0000-000000000001', '${auth_access_token}': '0', '${clientid}': '0',
    '${auth_xuid}': '0', '${user_type}': 'legacy',
}


def expand(args):
    out = []
    for x in args:
        if isinstance(x, dict):
            if not allowed(x.get('rules')):
                continue
            vals = x['value'] if isinstance(x['value'], list) else [x['value']]
        else:
            vals = [x]
        for s in vals:
            for k, r in subst.items():
                s = s.replace(k, r)
            out.append(s)
    return out


cmd = [a.java, '-Xmx3G'] + expand(v['arguments']['jvm']) + a.jvm.split() + [v['mainClass']] + expand(v['arguments']['game']) + a.game.split()
cmd = [c for c in cmd if '${' not in c]   # quick-play / demo placeholders left unset
log = open(os.path.join(root, 'console.log'), 'w', encoding='utf-8', errors='replace')
p = subprocess.Popen(cmd, cwd=root, stdout=log, stderr=subprocess.STDOUT)
try:
    p.wait(timeout=a.secs or None)
except subprocess.TimeoutExpired:
    p.kill()
    print('killed after', a.secs, 's')
print('exit', p.returncode)
