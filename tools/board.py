"""The versions board in README.md, from the repository's branches (active: <mc>-<loader>, paused/..., eol/...),
versions.json (the loader's name, the role, the Java floor) and update.json (the latest release per Minecraft version).

    python tools/board.py          rewrites the board between README's <!-- board --> markers
    python tools/board.py --check  exit 1 when the board is not what the branches say (before a commit)
"""
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def branches():
    out = subprocess.run(['git', '-C', ROOT, 'for-each-ref', '--format=%(refname:short)', 'refs/heads', 'refs/remotes/origin'],
                         capture_output=True, text=True).stdout.split()
    names = {b[len('origin/'):] if b.startswith('origin/') else b for b in out}
    return sorted(n for n in names if re.match(r'^(paused/|eol/)?\d+\.\d+(\.\d+)?-[a-z]+$', n))


def board():
    meta = {v['branch']: v for v in json.load(open(os.path.join(ROOT, 'versions.json'), encoding='utf-8'))}
    upd = json.load(open(os.path.join(ROOT, 'update.json'), encoding='utf-8')).get('promos', {})
    rows = []
    order = {'': 0, 'paused/': 1, 'eol/': 2}
    for b in sorted(branches(), key=lambda n: (order[re.match(r'^(paused/|eol/)?', n).group(0)], [-int(x) for x in n.split('/')[-1].split('-')[0].split('.')])):
        prefix = re.match(r'^(paused/|eol/)?', b).group(0)
        name = b[len(prefix):]
        mc, loader = name.split('-', 1)
        m = meta.get(name, {})
        state = {'': 'supported' + (' (%s)' % m['role'] if m.get('role') else ''), 'paused/': 'paused', 'eol/': 'end of life'}[prefix]
        rows.append('| %s | %s | %s | [`%s`](../../tree/%s) | %s | %s |' % (
            mc, m.get('loader', loader.capitalize()), state, b, b, upd.get('%s-latest' % mc, '-'),
            ('%s or newer' % m['java']) if m.get('java') else '-'))
    return '\n'.join(['| Minecraft | Loader | State | Branch | Latest release | Java |', '|---|---|---|---|---|---|'] + rows)


path = os.path.join(ROOT, 'README.md')
text = open(path, encoding='utf-8').read()
start = text.index('<!-- board')
start = text.index('\n', start) + 1
end = text.index('<!-- /board -->')
new = text[:start] + board() + '\n' + text[end:]
if '--check' in sys.argv:
    if new != text:
        print('README.md: the versions board is not what the branches say - run python tools/board.py')
        sys.exit(1)
    print('board ok')
else:
    open(path, 'w', encoding='utf-8', newline='\n').write(new)
    print(board())
