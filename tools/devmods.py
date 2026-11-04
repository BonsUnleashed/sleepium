"""A released mod jar made loadable on this project's dev runs (build.gradle -PtestMods), for compatibility tests only.

The dev runs remap released mods (SRG member names -> Mojang's) with NeoForge's installertools, which renames the
bytecode's member references but neither the strings inside mixin annotations of mods built without a refmap
(Architectury / Loom-built Forge jars write SRG names straight into @Inject(method = "m_285795_(...)V") - ImmediatelyFast)
nor class files newer than its ASM reads (a nested library's META-INF/versions/24 classes - Reflect in ImmediatelyFast).
This copy: every SRG name (m_N_ / f_N_) in every class file's string constants replaced by the Mojang name (SRG names
are unique across the game, so no class is needed), and nested jars' multi-release classes for Java > 21 left out (a
Java 17 game never loads them). The mod's behaviour on the dev run is then the one it has in production.

    python tools/devmods.py <in.jar> <out.jar> [--tsrg build/moddev/artifacts/namedToIntermediate.tsrg]"""
import argparse
import io
import os
import re
import struct
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ap = argparse.ArgumentParser()
ap.add_argument('src')
ap.add_argument('dst')
ap.add_argument('--tsrg', default=os.path.join(ROOT, 'build', 'moddev', 'artifacts', 'namedToIntermediate.tsrg'))
a = ap.parse_args()

SRG = {}
for line in open(a.tsrg, encoding='utf-8'):
    if line.startswith('\t'):
        parts = line.split()
        if len(parts) >= 2 and re.fullmatch(r'[mf]_\d+_', parts[-1]):
            SRG[parts[-1]] = parts[0]
TOKEN = re.compile(rb'\b[mf]_\d+_\b')
stats = {'classes': 0, 'strings': 0}


def remap_class(b):
    """rewrite the UTF8 constants of a class file (the rest is copied byte for byte)"""
    if b[:4] != b'\xca\xfe\xba\xbe':
        return b
    n = struct.unpack('>H', b[8:10])[0]
    out = bytearray(b[:10])
    i, k, changed = 10, 1, False
    while k < n:
        tag = b[i]
        if tag == 1:
            ln = struct.unpack('>H', b[i + 1:i + 3])[0]
            s = b[i + 3:i + 3 + ln]
            if TOKEN.search(s):
                s2 = TOKEN.sub(lambda m: SRG.get(m.group(0).decode(), m.group(0).decode()).encode(), s)
                if s2 != s:
                    changed = True
                    stats['strings'] += 1
                s = s2
            out += bytes([1]) + struct.pack('>H', len(s)) + s
            i += 3 + ln
        elif tag in (7, 8, 16, 19, 20):
            out += b[i:i + 3]; i += 3
        elif tag == 15:
            out += b[i:i + 4]; i += 4
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            out += b[i:i + 5]; i += 5
        elif tag in (5, 6):
            out += b[i:i + 9]; i += 9; k += 1
        else:
            raise ValueError('constant tag %d' % tag)
        k += 1
    if changed:
        stats['classes'] += 1
    return bytes(out) + b[i:] if changed else b


def process(data):
    src = zipfile.ZipFile(io.BytesIO(data))
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w', zipfile.ZIP_DEFLATED) as dst:
        for info in src.infolist():
            name = info.filename
            m = re.match(r'META-INF/versions/(\d+)/', name)
            if m and int(m.group(1)) > 21:
                continue
            body = src.read(name)
            if name.endswith('.class'):
                body = remap_class(body)
            elif name.endswith('.jar'):
                body = process(body)
            dst.writestr(info, body)
    return buf.getvalue()


open(a.dst, 'wb').write(process(open(a.src, 'rb').read()))
print('%s: %d SRG names remapped in %d classes' % (os.path.basename(a.dst), stats['strings'], stats['classes']))
