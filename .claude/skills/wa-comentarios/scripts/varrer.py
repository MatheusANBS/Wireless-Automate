"""Varredura de comentários do Wireless Automate.

Lista candidatos a código comentado, histórico, TODO, javadoc trivial e {@link} para tipo que não
existe. São candidatos, não veredito: cada linha precisa ser lida contra o código (ver SKILL.md, ao
lado). Num processo só, porque um grep por arquivo leva minutos no Windows.

    python .claude/skills/wa-comentarios/scripts/varrer.py            # arquivos mudados no ramo (contra o main)
    python .claude/skills/wa-comentarios/scripts/varrer.py --tudo     # o repositório inteiro
    python .claude/skills/wa-comentarios/scripts/varrer.py a.java ... # arquivos escolhidos
"""
import re
import subprocess
import sys
from pathlib import Path

EXTENSOES = ('.java', '.py', '.gradle', '.sh', '.yml')


def git(*args):
    return subprocess.run(['git', *args], capture_output=True, text=True, encoding='utf-8').stdout.splitlines()


RAIZ = Path(git('rev-parse', '--show-toplevel')[0])

CODIGO_JAVA = re.compile(
    r'^\s*//\s*('
    r'[A-Za-z_][\w.<>]*\s+[A-Za-z_]\w*\s*=[^=]'          # Tipo nome = ...
    r'|[A-Za-z_][\w.]*\([^)]*\);\s*$'                    # chamada();
    r'|(return|if|for|while|switch)\b.*[;{]\s*$'         # return x; / if (...) {
    r'|[{}]\s*$'                                          # chave sozinha
    r'|import\s+[a-z])')
CODIGO_SCRIPT = re.compile(
    r'^\s*#\s*(print\(|[a-z_]+\s*=[^=]|def |import |from .* import|for .* in |if .*:$|return\b)')
HISTORICO = re.compile(
    r'(//|^\s*\*|#).*\b(antes era|antigamente|antes (eram|ficava|usava|tinha)|não (é|fica|usa) mais'
    r'|foi (removid|trocad|renomead)|na versão [0-9]|desde a [0-9]|mudou (na versão|para))',
    re.IGNORECASE)
TODO = re.compile(r'(//|^\s*\*|#)\s*(TODO|FIXME|XXX|HACK)\b')
TRIVIAL = re.compile(r'^\s*/\*\*\s*(retorna|devolve|getter|setter|construtor)( d[oa])?( [a-z]+)?\.?\s*\*/\s*$',
                     re.IGNORECASE)
LINK = re.compile(r'\{@link(?:plain)?\s+([A-Z]\w*)')
IMPORT = re.compile(r'^import\s+[\w.]+\.([A-Z]\w*);', re.MULTILINE)
DECLARA = re.compile(r'\b(?:class|record|enum|interface)\s+([A-Z]\w*)')
JAVA_LANG = {'String', 'Object', 'Integer', 'Long', 'Math', 'Thread', 'Iterable', 'Runnable', 'Override',
             'Deprecated', 'IllegalStateException', 'IllegalArgumentException', 'Boolean', 'Double', 'Float',
             'Character', 'Enum', 'Record', 'System', 'Exception', 'RuntimeException'}


def arquivos(args):
    if args[:1] == ['--tudo']:
        nomes = git('ls-files')
    elif args:
        return [Path(a) for a in args if Path(a).resolve() != Path(__file__).resolve()]
    else:
        base = (git('merge-base', 'main', 'HEAD') or ['HEAD'])[0]
        nomes = set(git('diff', '--name-only', base)) | set(git('diff', '--name-only')) \
            | set(git('ls-files', '--others', '--exclude-standard'))
    proprio = Path(__file__).resolve()
    return sorted(RAIZ / n for n in nomes
                  if n.endswith(EXTENSOES) and (RAIZ / n).is_file() and (RAIZ / n).resolve() != proprio)


def main():
    sys.stdout.reconfigure(encoding='utf-8')
    files = arquivos(sys.argv[1:])
    if not files:
        print('Nenhum arquivo para varrer.')
        return
    declarados = set()
    for nome in git('ls-files', '*.java'):
        declarados.update(DECLARA.findall((RAIZ / nome).read_text(encoding='utf-8', errors='replace')))

    secoes = {k: [] for k in ('Código comentado', 'Histórico (o comentário conta o passado)',
                              'TODO, FIXME, XXX, HACK', 'Javadoc trivial (só repete o nome)',
                              '{@link} para tipo que não existe no repositório nem nos imports do arquivo')}
    chaves = list(secoes)
    for f in files:
        texto = f.read_text(encoding='utf-8', errors='replace')
        rel = f.relative_to(RAIZ).as_posix() if f.is_absolute() else f.as_posix()
        java = f.suffix in ('.java', '.gradle')
        importados = set(IMPORT.findall(texto)) if f.suffix == '.java' else set()
        vistos = set()
        for n, linha in enumerate(texto.splitlines(), 1):
            onde = f'{rel}:{n}: {linha.strip()[:160]}'
            if (CODIGO_JAVA if java else CODIGO_SCRIPT).search(linha):
                secoes[chaves[0]].append(onde)
            if HISTORICO.search(linha):
                secoes[chaves[1]].append(onde)
            if TODO.search(linha):
                secoes[chaves[2]].append(onde)
            if TRIVIAL.search(linha):
                secoes[chaves[3]].append(onde)
            if f.suffix == '.java':
                for tipo in LINK.findall(linha):
                    if tipo not in declarados and tipo not in importados and tipo not in JAVA_LANG \
                            and tipo not in vistos:
                        vistos.add(tipo)
                        secoes[chaves[4]].append(onde)

    for titulo, linhas in secoes.items():
        print(f'\n## {titulo}')
        print('\n'.join(linhas) if linhas else '(nada)')
    print(f'\n{len(files)} arquivo(s) varrido(s). Cada linha acima é um candidato: leia contra o código antes de mudar.')


if __name__ == '__main__':
    main()
