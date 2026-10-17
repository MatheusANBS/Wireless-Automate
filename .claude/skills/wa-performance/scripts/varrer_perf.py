"""Varredura de performance do Wireless Automate.

Lista candidatos a custo escondido: alocação no caminho quente, busca repetida, efeito colateral no
mundo e trabalho por quadro no cliente. Cada candidato cita a lei da SKILL.md (ao lado) que ele fere.
São candidatos, não veredito: uma alocação fora do laço ou uma busca num onLoad está certa; cada um
num arquivo mudado é resolvido ou justificado no relatório da tarefa.

    python -I .claude/skills/wa-performance/scripts/varrer_perf.py            # mudados contra o origin/main
    python -I .claude/skills/wa-performance/scripts/varrer_perf.py --tudo     # o mod inteiro
    python -I .claude/skills/wa-performance/scripts/varrer_perf.py a.java ... # arquivos escolhidos
"""
import re
import subprocess
import sys
from collections import Counter
from pathlib import Path

PACOTE = 'src/main/java/io/github/matheusanbs/wirelessautomate/'


def git(*args):
    return subprocess.run(['git', *args], capture_output=True, text=True, encoding='utf-8').stdout.splitlines()


RAIZ = Path(git('rev-parse', '--show-toplevel')[0])

# Área de cada pacote: o motor e o mundo rodam no tick do servidor; o sync roda por amostra com a
# tela aberta; o cliente roda por quadro. Testes e ferramentas de dev ficam de fora.
AREAS = [
    ('gametest/', None), ('bench/', None), ('client/dev/', None),
    ('network/', 'motor'), ('filter/', 'motor'), ('range/', 'motor'), ('mixin/', 'motor'),
    ('menu/', 'sync'), ('packet/', 'sync'),
    ('client/', 'cliente'),
    ('storage/', 'mundo'), ('resonator/', 'mundo'), ('block/', 'mundo'), ('chunk/', 'mundo'),
    ('compat/', 'mundo'),
]

# (áreas, regex, lei, o que é)
REGRAS = [
    ({'motor', 'sync', 'mundo'}, re.compile(r'\.stream\(\)'), 3, 'stream aloca (use laço)'),
    ({'motor'}, re.compile(r'\b(List|Set|Map)\.(of|copyOf)\('), 3, 'coleção nova no motor'),
    ({'motor', 'sync', 'mundo', 'cliente'}, re.compile(r'String\.format\('), 3, 'String.format aloca e é lento'),
    ({'motor', 'sync', 'mundo', 'cliente'},
     re.compile(r'\b(ResourceType|Direction|PortMode|RemoteSide|RelativeSide|RedstoneMode|RouterTier)\.values\(\)'),
     3, 'values() clona o array (use uma constante)'),
    ({'motor'}, re.compile(r'new BlockPos\(|\.(above|below|relative|offset)\('), 3, 'BlockPos novo no motor'),
    ({'motor'}, re.compile(r'\.(copy|copyWithCount|copyWithAmount)\('), 3, 'cópia de pilha no motor'),
    ({'motor'}, re.compile(r'Component\.(translatable|literal)\('), 3, 'Component no motor'),
    ({'motor', 'mundo'}, re.compile(r'(?<!Machine)\b(level|getLevel\(\))\.getCapability\(|Capabilities\.\w+\.\w+\.getCapability'),
     4, 'capability sem BlockCapabilityCache'),
    ({'motor'}, re.compile(r'\.getBlockState\('), 4, 'getBlockState no motor'),
    ({'motor', 'sync', 'mundo'}, re.compile(r'new (Hash|Linked|Tree)(Map|Set)<(Long|Integer|UUID|BlockPos)?'),
     10, 'coleção do JDK (fastutil, ou reaproveite)'),
    ({'motor', 'sync', 'mundo'}, re.compile(r'Map<(Long|Integer)\b'), 10, 'mapa com chave boxed (fastutil)'),
    ({'motor'}, re.compile(r'LOGGER\.(info|debug|trace)\('), 3, 'log no motor'),
    ({'motor', 'sync', 'mundo'}, re.compile(r'\bsynchronized\b'), 3, 'synchronized na thread do servidor'),
    ({'mundo', 'motor'}, re.compile(r'\bsetChanged\(\)'), 9, 'setChanged avisa os 6 vizinhos (blockEntityChanged basta?)'),
    ({'mundo', 'motor'}, re.compile(r'\.setBlock\((?!.*UPDATE_KNOWN_SHAPE)'), 9, 'setBlock sem UPDATE_KNOWN_SHAPE'),
    ({'mundo', 'motor', 'sync'}, re.compile(r'getChunk\(|\.getBlockEntity\('), 9,
     'consulta ao mundo (isLoaded antes? só fora do laço?)'),
    ({'cliente'}, re.compile(r'BuiltInRegistries\.\w+\.(stream|forEach|iterator|entrySet|keySet)\(|for \(.*: BuiltInRegistries'),
     8, 'percorre o registro inteiro'),
]

# Só dentro de métodos de desenho do cliente (render, draw, paint, tooltip, extract...).
POR_QUADRO = [
    (re.compile(r'\bfont\.(split|width|wordWrapHeight)\('), 'medida de texto por quadro (cache pela chave suja)'),
    (re.compile(r'\.sort\(|Collections\.sort|\.sorted\('), 'ordenação por quadro'),
    (re.compile(r'Component\.(translatable|literal)\('), 'Component montado por quadro'),
    (re.compile(r'new (ArrayList|HashMap|HashSet|LinkedHashMap)<'), 'coleção por quadro'),
    (re.compile(r'ResourceLocation\.(fromNamespaceAndPath|parse|withDefaultNamespace)\(|new ResourceLocation\('),
     'ResourceLocation por quadro'),
    (re.compile(r'\b(arrange|layout|rebuild|build\w*)\('), 'layout ou montagem chamada por quadro'),
]
METODO = re.compile(r'^\s{4}(?:(?:public|private|protected|static|final|synchronized|abstract)\s+)*'
                    r'(?:<[^>]+>\s*)?[\w.<>\[\], ?]+\s+(\w+)\s*\([^;]*$')
DESENHO = re.compile(r'^(render\w*|draw\w*|paint\w*|tooltip\w*|extractRenderState|renderTooltip\w*)$')


def area(caminho):
    texto = str(caminho).replace('\\', '/')
    if PACOTE not in texto:
        return None
    rel = texto.split(PACOTE, 1)[1]
    for prefixo, nome in AREAS:
        if rel.startswith(prefixo):
            return nome
    return 'mundo'


def arquivos(args):
    if args[:1] == ['--tudo']:
        nomes = git('ls-files')
    elif args:
        return [Path(a) for a in args]
    else:
        base = (git('merge-base', 'origin/main', 'HEAD') or ['HEAD'])[0]
        nomes = set(git('diff', '--name-only', base)) | set(git('diff', '--name-only')) \
            | set(git('ls-files', '--others', '--exclude-standard'))
    return sorted(RAIZ / n for n in nomes if n.endswith('.java') and (RAIZ / n).is_file())


def sem_comentario(linha):
    s = linha.strip()
    if s.startswith(('//', '*', '/*')):
        return ''
    return re.sub(r'"(?:\\.|[^"\\])*"', '""', linha.split('//', 1)[0])


def varrer(caminho):
    nome = area(caminho)
    if nome is None:
        return []
    achados = []
    metodo = ''
    for numero, linha in enumerate(caminho.read_text(encoding='utf-8', errors='replace').splitlines(), 1):
        declara = METODO.match(linha)
        if declara and not linha.strip().startswith(('return', 'new ', 'if', 'for', 'while')):
            metodo = declara.group(1)
        codigo = sem_comentario(linha)
        if not codigo.strip():
            continue
        for areas, regex, lei, oque in REGRAS:
            if nome in areas and regex.search(codigo):
                achados.append((numero, nome, lei, oque, linha.strip()))
        if nome == 'cliente' and DESENHO.match(metodo or ''):
            for regex, oque in POR_QUADRO:
                if regex.search(codigo):
                    achados.append((numero, nome, 8, f'{oque} [{metodo}]', linha.strip()))
    return achados


def main():
    sys.stdout.reconfigure(encoding='utf-8')
    total = Counter()
    for caminho in arquivos(sys.argv[1:]):
        achados = varrer(caminho)
        if not achados:
            continue
        rel = caminho.relative_to(RAIZ) if caminho.is_absolute() else caminho
        print(f'\n{rel}')
        for numero, nome, lei, oque, trecho in achados:
            total[nome] += 1
            print(f'  {numero:>5}  [{nome}] lei {lei}: {oque}\n         {trecho[:140]}')
    if total:
        print('\nCandidatos por área: ' + ', '.join(f'{k} {v}' for k, v in sorted(total.items())))
        print('Cada candidato: resolva ou justifique no relatório (ver .claude/skills/wa-performance/SKILL.md).')
    else:
        print('Nenhum candidato.')


if __name__ == '__main__':
    main()
