"""Multiplayer on the dev runs: a dedicated server of this build (run/, tools/server.py) and a self-driving dev client
(run-client/, ClientAuto -Dsleep.clientAuto.server) joining it over TCP. The client stays --secs seconds and quits; the
server measures the window (/sleep net, sent by the client) and, with --bulk, publishes the bulk self test while the
client is in (/sleep bulktest; a second publish with --bulk2, e.g. a delta). Prints how the join went and the
networking lines of both sides.

    python tools/mp_test.py                                        (both with the defaults)
    python tools/mp_test.py --server-flags "" --client-flags ""    (no optimisation flags on either side)
    python tools/mp_test.py --server-flags "-Dlab.opt.defaults=true -Dsleep.net.testAbsent=true"   (server's channels absent)
    python tools/mp_test.py --bulk "32 256 0 1" --bulk2 "32 256 4 2"

(1.21.1's tools/mp_test.py drives production jars on a release server; 1.20.1 has no such install yet.)
MC_PORT: the server's port (default 25565). Heaps: server 1 GB, client 2 GB.
"""
import argparse
import os
import re
import subprocess
import sys
import threading
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from server import Server, ROOT, RUN, GRADLEW

ap = argparse.ArgumentParser()
ap.add_argument('--server-flags', default='-Dlab.opt.defaults=true -Dsleep.net.stats=true')
ap.add_argument('--client-flags', default='-Dlab.opt.defaults=true')
ap.add_argument('--secs', type=int, default=20)
ap.add_argument('--settle', type=int, default=10)
ap.add_argument('--bulk', default='', help='"entries kb changed seed" published once the client is in')
ap.add_argument('--bulk2', default='', help='a second publish, 8 s after the first')
ap.add_argument('--label', default='mp')
a = ap.parse_args()
port = os.environ.get('MC_PORT', '25565')
CLIENT_RUN = os.path.join(ROOT, 'run-client')
os.makedirs(CLIENT_RUN, exist_ok=True)

slog = os.path.join(RUN, 'mp-server.log')
clines = []
with Server(['-Xmx1G'] + a.server_flags.split(), {}, slog) as s:
    s.wait_for(r'Done \(', 300)
    jvm = ['-Xms2G', '-Xmx2G', '-Dsleep.clientAuto=' + a.label, '-Dsleep.clientAuto.server=localhost:' + port,
           '-Dsleep.clientAuto.steady=true', '-Dsleep.clientAuto.steadySeconds=%d' % a.secs, '-Dsleep.clientAuto.settle=%d' % a.settle,
           '-Dsleep.clientAuto.gamemode=creative'] + a.client_flags.split()
    def launch():
        c = subprocess.Popen([GRADLEW, 'runClient', '--console=plain', '-q', '-PclientRunDir=' + CLIENT_RUN, '-PrunJvmArgs=' + ' '.join(jvm),
                              '-PrunArgs=--width 854 --height 480'], cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                             text=True, encoding='utf-8', errors='replace', bufsize=1)
        print('client pid', c.pid, flush=True)

        def read():
            for line in c.stdout:
                clines.append(line.rstrip('\n'))
        threading.Thread(target=read, daemon=True).start()
        return c

    try:
        # 1.20.1's login can stall for good on a dev client (netty idle, no packet read: seen with and without Sleepium's
        # decoder, see the port notes): a client that has not joined 60 s after connecting is stopped and started again
        name = None
        for attempt in range(3):
            with s.lock:
                start = len(s.lines)
            client = launch()
            t0, connected = time.time(), None
            while time.time() - t0 < 300 and name is None:
                with s.lock:
                    for line in s.lines[start:]:
                        m = re.search(r'(\w+) joined the game', line)
                        if m:
                            name = m.group(1)
                if connected is None and any('Connecting to' in l for l in clines):
                    connected = time.time()
                if name is None and connected is not None and time.time() - connected > 60:
                    break
                time.sleep(1)
            if name:
                break
            print('! attempt %d: no join; restarting the client' % attempt, flush=True)
            subprocess.run(['taskkill', '/T', '/F', '/PID', str(client.pid)], capture_output=True)
            client.wait(60)
            clines.clear()
        if name is None:
            raise SystemExit('no join after 3 attempts')
        s.cmd('op ' + name)   # for the client's /sleep net (at the start of its window, after --settle)
        print('joined:', name, flush=True)
        if a.bulk:
            time.sleep(3)
            print('server:', s.ask('sleep bulktest ' + a.bulk, r'bulktest published: [^\n]*').group(0))
            if a.bulk2:
                time.sleep(8)
                print('server:', s.ask('sleep bulktest ' + a.bulk2, r'bulktest published: [^\n]*').group(0))
        s.wait_for(r"net '%s': " % re.escape(a.label), a.settle + a.secs + 240)
    except SystemExit as e:
        print('!', e)
    try:
        client.wait(180)
    except subprocess.TimeoutExpired:
        print('! the client did not quit; stopping it (pid %d)' % client.pid)
        subprocess.run(['taskkill', '/T', '/F', '/PID', str(client.pid)], capture_output=True)
    errors = s.errors()

stext = open(slog, encoding='utf-8', errors='replace').read()
ctext = '\n'.join(clines)
for pat in (r'\w+\[[^\]]*\] logged in[^\n]*', r'\w+ joined the game', r'\w+ lost connection: [^\n]*', r'Disconnecting \w+[^\n]*',
            r"sleep: net '[^\n]*", r'bulktest[^\n]*', r'\[sleep/net\][^\n]*', r'\[sleep/opt\] defaults[^\n]*'):
    for m in re.findall(pat, stext)[:4]:
        print('server:', m.strip()[:1200])
for e in errors[:10]:
    print('server !', e[:300])
for pat in (r'\[sleep/net\][^\n]*', r'Disconnected[^\n]*', r'disconnect[^\n]*reason[^\n]*', r"sleepclient '[^\n]*", r'joined: [^\n]*',
            r'[^\n]*(?:Exception|/ERROR\])[^\n]*'):
    for m in re.findall(pat, ctext)[:6]:
        print('client:', m.strip()[:300])
