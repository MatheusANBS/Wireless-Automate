"""Estuda o jar de um mod de referência sem executá-lo: o jar é lido só como zip.

Uso:
    python -I estudar_jar.py MOD.jar [--prefixo-textura assets/<modid>/textures/]

Mostra:
- as entradas por extensão e os pacotes de classe;
- os shaders (core e post), com o tamanho de cada arquivo;
- as dependências do mods.toml e as listas de mixins;
- as texturas de efeito: tamanho, blur no .mcmeta, se são cinza (tingidas pela cor do vértice) ou coloridas, e os pares `_rim`;
- as palavras de render nas classes (tipos de desenho, estágios, Iris, profundidade, blend) e onde aparecem.

Para ler um método, extraia a classe e rode `javap -p -c -constants <arquivo.class>` (o JDK do projeto tem o javap).
O que se tira daqui é o método (tamanhos, filtros, blends, estágios, tempos): textura, shader e código de mod
All Rights Reserved não entram no nosso.
"""
import argparse
import collections
import io
import json
import re
import sys
import zipfile

from PIL import Image

PALAVRAS = re.compile(
    rb'(net\.irisshaders[\w.]*|isShaderPackInUse|isRenderingShadowPass|RenderType|RenderStateShard|ShaderInstance|'
    rb'RenderTarget|TextureTarget|RenderLevelStageEvent|AFTER_[A-Z_]+|RenderGuiEvent|BufferBuilder|VertexBuffer|'
    rb'blendFunc|BlendFunc|depthMask|DEPTH|ParticleRenderType|TextureSheetParticle|blitFramebuffer|glReadPixels|'
    rb'partialTick|getPartialTick|geckolib|playeranimator)')


def main():
    sys.stdout.reconfigure(encoding='utf-8')
    ap = argparse.ArgumentParser()
    ap.add_argument('jar')
    ap.add_argument('--prefixo-textura', default='')
    a = ap.parse_args()
    z = zipfile.ZipFile(a.jar)
    nomes = z.namelist()
    ext = collections.Counter(n.rsplit('.', 1)[-1] for n in nomes if '.' in n.rsplit('/', 1)[-1])
    print('== entradas', len(nomes), dict(ext.most_common(12)))
    pac = collections.Counter('/'.join(n.split('/')[:3]) for n in nomes if n.endswith('.class'))
    print('== pacotes', pac.most_common(12))

    sh = [n for n in nomes if '/shaders/' in n and not n.endswith('/')]
    print('== shaders', len(sh))
    for n in sh:
        print('  ', n, z.getinfo(n).file_size)

    for n in nomes:
        if n.endswith(('mods.toml', 'fabric.mod.json')):
            txt = z.read(n).decode('utf-8', 'replace')
            deps = re.findall(r'modId\s*=\s*"([^"]+)"[\s\S]*?type\s*=\s*"([^"]+)"', txt)
            print('== dependências', n, deps)
        if n.endswith('mixins.json'):
            try:
                js = json.loads(z.read(n))
                print('== mixins', n, (js.get('mixins') or []) + (js.get('client') or []))
            except ValueError:
                pass

    pref = a.prefixo_textura
    linhas = []
    for n in nomes:
        if not n.endswith('.png') or (pref and not n.startswith(pref)):
            continue
        if not pref and not re.search(r'/(fx|effect|effects|particle|particles)/', n):
            continue
        im = Image.open(io.BytesIO(z.read(n))).convert('RGBA')
        w, h = im.size
        b = im.resize((24, 24)).tobytes()
        cores = [tuple(b[i:i + 4]) for i in range(0, len(b), 4) if b[i + 3] > 40]
        sat = max((max(p[:3]) - min(p[:3]) for p in cores), default=0)
        meta = n + '.mcmeta'
        m = json.loads(z.read(meta)) if meta in nomes else {}
        linhas.append((n, w, h, 'cinza' if sat < 25 else 'cor', (m.get('texture') or {}).get('blur'), bool(m.get('animation'))))
    if linhas:
        lados = collections.Counter(max(x[1], x[2]) for x in linhas)
        print(f'== texturas de efeito: {len(linhas)}; cinza/cor {dict(collections.Counter(x[3] for x in linhas))}; '
              f'blur {dict(collections.Counter(x[4] for x in linhas))}; com animação no .mcmeta {sum(x[5] for x in linhas)}')
        print('   maior lado:', sorted(lados.items()))
        print('   pares _rim:', [x[0].rsplit('/', 1)[1] for x in linhas if '_rim' in x[0]][:12])

    hits, onde = collections.Counter(), collections.defaultdict(set)
    for n in nomes:
        if n.endswith('.class'):
            for m in PALAVRAS.findall(z.read(n)):
                k = m.decode('latin1')
                hits[k] += 1
                onde[k].add(n.rsplit('/', 1)[-1])
    print('== palavras de render nas classes')
    for k, v in hits.most_common(40):
        print(f'   {k}: {v}  ex: {sorted(onde[k])[:4]}')


if __name__ == '__main__':
    main()
