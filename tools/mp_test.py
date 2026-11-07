"""Release test of multiplayer: a production NeoForge server and a production client (tools/prod_client.py), each with
or without the sleep jar. The server starts, the client joins (quick play), stays --secs seconds, leaves; the server
stops. Prints whether the player joined and how the connection ended, from both logs.

    python tools/mp_test.py --server-mods build/libs/sleep-0.1.0.jar --client-mods build/libs/sleep-0.1.0.jar
    python tools/mp_test.py --server-mods "" --client-mods build/libs/sleep-0.1.0.jar      (server without sleep)
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import time

ap = argparse.ArgumentParser()
ap.add_argument('--server', default=r'F:\release-test\server')
ap.add_argument('--client', default=r'F:\release-test\client')
ap.add_argument('--java', default=r'F:\forminecrfatmods\jdk-21.0.2\bin\java.exe')
ap.add_argument('--server-mods', default='')
ap.add_argument('--client-mods', default='')
ap.add_argument('--client-jvm', default='')
ap.add_argument('--server-jvm', default='', help='JVM flags for the server (e.g. -Dsleep.test.encrypt=true: an encrypted login offline)')
ap.add_argument('--secs', type=int, default=60)
ap.add_argument('--port', default='25570')
a = ap.parse_args()
HERE = os.path.dirname(os.path.abspath(__file__))

mods = os.path.join(a.server, 'mods')
os.makedirs(mods, exist_ok=True)
for f in os.listdir(mods):
    if f.endswith('.jar'):
        os.remove(os.path.join(mods, f))
for m in filter(None, a.server_mods.split(',')):
    shutil.copy(m, mods)
shutil.rmtree(os.path.join(a.server, 'world'), ignore_errors=True)
slog = open(os.path.join(a.server, 'console.log'), 'w', encoding='utf-8', errors='replace')
server = subprocess.Popen([a.java, '-Xmx2G'] + a.server_jvm.split() + ['@libraries/net/neoforged/neoforge/21.1.251/win_args.txt', 'nogui'], cwd=a.server,
                          stdin=subprocess.PIPE, stdout=slog, stderr=subprocess.STDOUT, text=True)
t0 = time.time()
while time.time() - t0 < 240:
    time.sleep(2)
    text = open(os.path.join(a.server, 'console.log'), encoding='utf-8', errors='replace').read()
    if re.search(r'Done \(\d', text):
        break
    if server.poll() is not None:
        print('server exited early:', server.returncode)
        sys.exit(1)
print('server up in %.0f s' % (time.time() - t0))
client = subprocess.run([sys.executable, os.path.join(HERE, 'prod_client.py'), '--dir', a.client, '--mods', a.client_mods, '--jvm=' + a.client_jvm,
                         '--game=--quickPlayMultiplayer localhost:' + a.port, '--secs', str(a.secs)], capture_output=True, text=True)
print('client:', client.stdout.strip().replace('\n', ' '))
server.stdin.write('stop\n')
server.stdin.flush()
try:
    server.wait(timeout=60)
except subprocess.TimeoutExpired:
    server.kill()
stext = open(os.path.join(a.server, 'console.log'), encoding='utf-8', errors='replace').read()
ctext = open(os.path.join(a.client, 'console.log'), encoding='utf-8', errors='replace').read()
for pat in (r'Tester\[[^\]]*\] logged in[^\n]*', r'Tester joined the game', r'Tester lost connection: [^\n]*', r'Disconnecting Tester[^\n]*',
            r'Payload[^\n]*', r'Exception[^\n]*', r'\[sleep/opt\] defaults[^\n]*'):
    for m in re.findall(pat, stext)[:3]:
        print('server:', m.strip()[:200])
for pat in (r'Connecting to [^\n]*', r'Disconnected[^\n]*', r'disconnect[^\n]*reason[^\n]*', r'Exception[^\n]*', r'\[sleep/ui\][^\n]*', r'\[sleep/shaders\] switched[^\n]*'):
    for m in re.findall(pat, ctext)[:4]:
        print('client:', m.strip()[:200])
