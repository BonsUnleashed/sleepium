"""What one supported version has and another does not yet: for each pair of active branches, the commits of one since
their common ancestor without a counterpart on the other. A counterpart is a commit there whose message says
"(cherry picked from commit <id>)" (git cherry-pick -x) or "Port-of: <id>", or one with the same patch (git cherry).
A commit that says "Only: <branch>" (for that version alone) or "Only: this" is not asked for; commits from before the
rule are listed in tools/ported-baseline.txt.

    python tools/notported.py                  every pair of active branches
    python tools/notported.py 1.21.1-neoforge  what that branch has that the others lack
"""
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def git(*a):
    return subprocess.run(['git', '-C', ROOT] + list(a), capture_output=True, text=True, encoding='utf-8').stdout


def active():
    names = git('for-each-ref', '--format=%(refname:short)', 'refs/heads').split()
    return sorted(n for n in names if re.match(r'^\d+\.\d+(\.\d+)?-[a-z]+$', n))


base_file = os.path.join(ROOT, 'tools', 'ported-baseline.txt')
BASELINE = {l.split()[0] for l in open(base_file, encoding='utf-8') if l.strip() and not l.startswith('#')} if os.path.exists(base_file) else set()


def commits(rng):
    out = git('log', '--format=%H%x00%s%x00%B%x01', rng)
    for rec in out.split('\x01'):
        rec = rec.strip('\n')
        if rec:
            h, s, b = rec.split('\x00', 2)
            yield h, s, b


def report(src, dst):
    base = git('merge-base', src, dst).strip()
    carried = set()
    for h, s, b in commits('%s..%s' % (base, dst)):
        carried.update(re.findall(r'\(cherry picked from commit ([0-9a-f]{7,40})\)', b))
        carried.update(re.findall(r'^Port-of: ([0-9a-f]{7,40})', b, re.M))
    same = {l[2:] for l in git('cherry', dst, src, base).split('\n') if l.startswith('- ')}   # patches already there
    missing = []
    for h, s, b in commits('%s..%s' % (base, src)):
        if h in BASELINE or h in same or any(c and h.startswith(c) for c in carried):
            continue
        # a port itself (cherry-picked or written again from a commit there): nothing to bring back
        origin = re.findall(r'\(cherry picked from commit ([0-9a-f]{7,40})\)', b) + re.findall(r'^Port-of: ([0-9a-f]{7,40})', b, re.M)
        if any(subprocess.run(['git', '-C', ROOT, 'merge-base', '--is-ancestor', o, dst], capture_output=True).returncode == 0 for o in origin):
            continue
        only = re.findall(r'^Only: (\S+)', b, re.M)
        if only and (src in only or 'this' in only):
            continue
        missing.append((h, s))
    return missing


names = active()
pairs = [(a, b) for a in names for b in names if a != b and (len(sys.argv) < 2 or a == sys.argv[1])]
total = 0
for a, b in pairs:
    m = report(a, b)
    total += len(m)
    print('%s -> %s: %s' % (a, b, 'nothing to carry' if not m else '%d to carry over or mark Only' % len(m)))
    for h, s in m:
        print('   %s %s' % (h[:9], s[:110]))
sys.exit(1 if total else 0)
