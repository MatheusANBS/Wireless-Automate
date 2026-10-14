"""Tamanho das classes do Wireless Automate contra os limites da skill wa-modular.

Conta linhas por arquivo e acha métodos longos (pela profundidade das chaves; é uma estimativa,
não um parser). Só mede: a decisão de dividir é lendo a classe (ver SKILL.md, ao lado).

    python -I .claude/skills/wa-modular/scripts/medir.py            # só o que passa do aviso
    python -I .claude/skills/wa-modular/scripts/medir.py --tudo     # todos os arquivos, com a categoria
    python -I .claude/skills/wa-modular/scripts/medir.py a.java ... # arquivos escolhidos
"""
import re
import subprocess
import sys
from pathlib import Path

# (alvo, aviso, dividir) em linhas
LIMITES = {
    'tela': (500, 600, 800),
    'servidor': (600, 800, 1000),
    'teste e dev': (1200, 1200, 1500),
}
METODO = (60, 80, 120)
DEV = ('DevEndToEnd.java', 'DevScreenshot.java')

ASSINATURA = re.compile(r'^\s*(?:@\w+\s+)*(?:(?:public|protected|private|static|final|abstract|synchronized|default)\s+)*'
                        r'[\w<>\[\],.? ]+\s+(\w+)\s*\([^;]*$')
TIPO = re.compile(r'\b(class|record|enum|interface)\b')
LITERAL = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')


def git(*args):
    return subprocess.run(['git', *args], capture_output=True, text=True, encoding='utf-8').stdout.splitlines()


RAIZ = Path(git('rev-parse', '--show-toplevel')[0])


def categoria(caminho):
    partes = caminho.as_posix()
    if '/gametest/' in partes or '/src/test/' in partes or caminho.name in DEV:
        return 'teste e dev'
    if '/client/' in partes:
        return 'tela'
    return 'servidor'


def metodos_longos(texto):
    """(nome, linha, tamanho) dos métodos acima do aviso, pela profundidade das chaves."""
    achados = []
    profundidade = 0
    aberto = None  # (nome, linha inicial, profundidade de dentro)
    pendente = None
    comentario = False
    for n, linha in enumerate(texto.splitlines(), 1):
        limpa = LITERAL.sub('""', linha)
        if comentario:
            if '*/' not in limpa:
                continue
            limpa = limpa.split('*/', 1)[1]
            comentario = False
        if '/*' in limpa and '*/' not in limpa.split('/*', 1)[1]:
            comentario = True
            limpa = limpa.split('/*', 1)[0]
        limpa = limpa.split('//', 1)[0]
        if aberto is None:
            m = ASSINATURA.match(limpa)
            if m and m.group(1) not in ('if', 'for', 'while', 'switch', 'catch', 'return', 'new') \
                    and not TIPO.search(limpa):
                pendente = (m.group(1), n)
        for c in limpa:
            if c == '{':
                profundidade += 1
                if aberto is None and pendente is not None:
                    aberto = (pendente[0], pendente[1], profundidade)
                    pendente = None
            elif c == '}':
                if aberto is not None and profundidade == aberto[2]:
                    tamanho = n - aberto[1] + 1
                    if tamanho > METODO[1]:
                        achados.append((aberto[0], aberto[1], tamanho))
                    aberto = None
                profundidade -= 1
        if limpa.strip().endswith(';'):
            pendente = None
    return achados


def arquivos(args):
    if args and args[0] != '--tudo':
        return [Path(a).resolve() for a in args]
    return sorted(RAIZ / n for n in git('ls-files', '*.java') if (RAIZ / n).is_file())


def main():
    sys.stdout.reconfigure(encoding='utf-8')
    args = sys.argv[1:]
    tudo = args[:1] == ['--tudo']
    linhas_saida = []
    for f in arquivos(args):
        texto = f.read_text(encoding='utf-8', errors='replace')
        n = texto.count('\n') + 1
        cat = categoria(f)
        alvo, aviso, dividir = LIMITES[cat]
        estado = 'DIVIDIR' if n > dividir else 'aviso' if n > aviso else 'ok'
        longos = metodos_longos(texto)
        if tudo or estado != 'ok' or any(t > METODO[2] for _, _, t in longos) or len(args) > 0:
            rel = f.relative_to(RAIZ).as_posix() if f.is_relative_to(RAIZ) else f.as_posix()
            linhas_saida.append((n, f'{estado:8} {n:5}  {cat:11}  {rel}', longos))
    if not linhas_saida:
        print('Nada acima dos limites.')
        return
    for _, linha, longos in sorted(linhas_saida, key=lambda t: -t[0]):
        print(linha)
        for nome, inicio, tamanho in sorted(longos, key=lambda t: -t[2]):
            marca = 'DIVIDIR' if tamanho > METODO[2] else 'aviso'
            print(f'           método {nome}() linha {inicio}: {tamanho} linhas ({marca})')
    print(f'\nLimites (alvo/aviso/dividir): ' + '; '.join(f'{k} {v[0]}/{v[1]}/{v[2]}' for k, v in LIMITES.items())
          + f'; método {METODO[0]}/{METODO[1]}/{METODO[2]}. São gatilhos para ler a classe, não veredito.')


if __name__ == '__main__':
    main()
