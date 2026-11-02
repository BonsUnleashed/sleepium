"""The settings screen's catalogue: every engine flag of defaults.txt (plus the experimental ones below) with its group
and a one-line description taken from its class's javadoc. Writes src/main/resources/assets/sleep/options.tsv:
    key <TAB> group <TAB> default (true/false) <TAB> description
Run after adding a flag:  python tools/gen_options.py
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'src', 'main', 'java')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'sleep', 'options.tsv')

# first matching rule wins: (group, name regex)
GROUPS = [
    ('lighting', r'^(Light|ShaderLight|ClientLight)'),
    ('chunks', r'^(Chunk(Multi|Section|Shader|Upload)|Section(Layer|Lists|ModCount)|BlockMesh|VertexSort|SamplerUnit|Uniform|ShaderSampler|Frame|RenderCache|RenderCall|OverworldFog|AmbientParticle|ItemFrame|Texture|ParallelReload|ClientBiome|ClientChunkCacheBiome)'),
    ('entities_render', r'^(EntityBatch|Part|ModelPart|PoseStack|EntityCull|EntityLight|BlockEntityCull|Vec3FromRgb)'),
    ('animation', r'^(Geo|Molang|Keyframe|AnimStream)'),
    ('threads', r'^(MainReactor|Reactor|ParallelEntities|LabPools|PersistentPools|MailboxBatch|NeoForgeBus)'),
    ('network', r'^Net'),
    ('worldgen', r'^(Worldgen|GenScout|CubicSampler|ParallelWorldgen|StructurePlace)'),
    ('saving', r'^(AsyncChunkSave|ChunkIo|ChunkCache|UnloadRecheck|AllocTrim|PoiColumn|SectionStorage)'),
    ('ticks', r'.'),   # the rest: server ticks, mobs, collisions, redstone, fluids, events
]

# off by default, shown in their groups (key, group, description)
EXPERIMENTAL = [
    ('lab.chunkmd.occlusion', 'chunks', 'GPU occlusion culling of chunk sections (two phases, exact). Pays off where most of the loaded terrain is hidden.'),
]


def javadoc_of(name):
    for d, _, files in os.walk(SRC):
        if name + '.java' in files:
            text = open(os.path.join(d, name + '.java'), encoding='utf-8').read()
            m = re.search(r'/\*\*(.*?)\*/\s*(?:@[^\n]*\n\s*)*(?:public\s+)?(?:abstract\s+|final\s+)*(?:class|interface|enum)\s+' + name, text, re.S)
            if not m:
                return ''
            doc = re.sub(r'^\s*\*\s?', '', m.group(1), flags=re.M)
            doc = re.sub(r'\{@(?:code|link|linkplain)\s+([^}]*)\}', r'\1', doc)
            doc = re.sub(r'<[^>]+>', ' ', doc)
            doc = ' '.join(doc.split())
            s = re.match(r'(.+?[.!?])(\s|$)', doc)
            line = s.group(1) if s else doc
            return line[:300]
    return ''


def main():
    rows = []
    for line in open(os.path.join(ROOT, 'defaults.txt'), encoding='utf-8'):
        line = line.strip()
        if not line.startswith('-D') or '=' not in line:
            continue
        key, value = line[2:].split('=', 1)
        name = key.rsplit('.', 1)[-1]
        group = next(g for g, rx in GROUPS if re.search(rx, name))
        desc = javadoc_of(name) or javadoc_of(name + 'Mixin')
        rows.append((key, group, value, desc))
    for key, group, desc in EXPERIMENTAL:
        rows.append((key, group, 'false', desc))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
        for r in rows:
            f.write('\t'.join(c.replace('\t', ' ').replace('\n', ' ') for c in r) + '\n')
    counts = {}
    for r in rows:
        counts[r[1]] = counts.get(r[1], 0) + 1
    missing = sum(1 for r in rows if not r[3])
    print(f'{len(rows)} options -> {OUT}; groups {counts}; without a description: {missing}')


if __name__ == '__main__':
    sys.exit(main())
