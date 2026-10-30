"""Varredura de higiene do repositório do Wireless Automate.

Lista os candidatos a sair, sem apagar nada:
  1. arquivos de docs/ que nenhum documento vivo cita (direta ou indiretamente);
  2. changelogs separados de versões antigas (fica só o da versão atual e o unificado);
  3. ramos locais sem nenhum commit fora do main (git cherry) e as worktrees deles.

"Vivo" é o que o agente ou o dono ainda usa: CLAUDE.md, AGENTS.md, os READMEs, as skills,
o código, os scripts, o build e os docs-raiz (RAIZES). Um arquivo citado por um vivo também
é vivo. Citação só no histórico do progresso, num plano ou numa spec morta não conta, e um
plano só fica vivo se um documento-raiz, uma skill, o mapa ou o "Próximo passo" o citam (a
spec que o originou não segura o plano). O "Próximo passo" do progresso conta: é o que ainda
falta fazer.

Uso: python -I .claude/skills/wa-higiene/scripts/varrer.py [--raiz <repo>]
"""
import argparse
import os
import re
import subprocess
import sys

RAIZES = (
    "CLAUDE.md", "AGENTS.md", "README.md", "README.en.md", "build.gradle", "settings.gradle",
    "gradle.properties",
    "docs/especificacao.md", "docs/pacote-de-design.md", "docs/identidade-visual.md",
    "docs/e2e.md", "docs/benchmark.md", "docs/benchmark-logistics-network.md",
    "docs/auditoria-performance.md", "docs/portes.md", "docs/curseforge/descricao.md",
)
PREFIXOS_VIVOS = (".claude/skills/", "docs/mapa/", "src/", "scripts/", ".github/")
PLANOS = "docs/superpowers/plans/"
CHANGELOG = re.compile(r"docs/curseforge/changelog-\d[\d.]*\.md")
BINARIOS = (".png", ".gif", ".jpg", ".jar", ".bbmodel", ".ogg", ".nbt", ".zip")


def git(*args, raiz):
    return subprocess.run(["git", *args], cwd=raiz, capture_output=True, text=True,
                          encoding="utf-8", errors="ignore").stdout


def ler(raiz, caminho):
    try:
        with open(os.path.join(raiz, caminho), encoding="utf-8", errors="ignore") as f:
            return f.read()
    except OSError:
        return ""


def texto_vivo(raiz, caminho):
    t = ler(raiz, caminho)
    if caminho == "docs/progresso.md":
        # Só o que falta fazer; resumo de versões, decisões e histórico não seguram arquivo.
        m = re.search(r"^## Próximo passo$(.*?)^## ", t, re.S | re.M)
        return m.group(1) if m else ""
    return t


def docs_mortos(raiz, arquivos):
    docs = [a for a in arquivos if a.startswith("docs/")]
    nome_para_docs = {}
    for d in docs:
        nome_para_docs.setdefault(os.path.basename(d), []).append(d)
    padrao = re.compile("|".join(re.escape(n) for n in sorted(nome_para_docs, key=len, reverse=True)))

    vivos = set()
    fila = [a for a in arquivos if a in RAIZES or a.startswith(PREFIXOS_VIVOS)] + ["docs/progresso.md"]
    vivos.update(fila)
    while fila:
        a = fila.pop()
        if a.endswith(BINARIOS):
            continue
        de_trabalho = a.startswith(("docs/superpowers/", "docs/preview/"))
        for nome in set(padrao.findall(texto_vivo(raiz, a))):
            for d in nome_para_docs[nome]:
                if de_trabalho and d.startswith(PLANOS):
                    continue
                if d not in vivos:
                    vivos.add(d)
                    fila.append(d)
    # Os changelogs têm regra própria (changelogs_velhos).
    return [d for d in docs if d not in vivos and not CHANGELOG.fullmatch(d)]


def changelogs_velhos(raiz, arquivos):
    versao = re.search(r"^mod_version=(.+)$", ler(raiz, "gradle.properties"), re.M)
    atual = f"docs/curseforge/changelog-{versao.group(1).strip()}.md" if versao else None
    return [a for a in arquivos
            if CHANGELOG.fullmatch(a) and a != atual]


def ramos_absorvidos(raiz):
    saida = []
    for ramo in git("for-each-ref", "--format=%(refname:short)", "refs/heads", raiz=raiz).split():
        if ramo == "main":
            continue
        fora = [l for l in git("cherry", "main", ramo, raiz=raiz).splitlines() if l.startswith("+")]
        if not fora:
            saida.append(ramo)
    return saida


def worktrees(raiz):
    lista, atual = [], {}
    for linha in git("worktree", "list", "--porcelain", raiz=raiz).splitlines() + [""]:
        if not linha:
            if atual:
                lista.append(atual)
            atual = {}
        elif " " in linha:
            k, v = linha.split(" ", 1)
            atual[k] = v
        else:
            atual[linha] = True
    return lista[1:]  # a primeira é o próprio checkout


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser()
    ap.add_argument("--raiz", default=".")
    raiz = os.path.abspath(ap.parse_args().raiz)
    arquivos = [a for a in git("ls-files", raiz=raiz).splitlines() if a]

    mortos = docs_mortos(raiz, arquivos)
    print(f"## docs sem citação viva ({len(mortos)})")
    for d in mortos:
        print(f"  {d}")

    velhos = changelogs_velhos(raiz, arquivos)
    print(f"\n## changelogs de versões antigas ({len(velhos)})")
    for c in velhos:
        print(f"  {c}")

    absorvidos = ramos_absorvidos(raiz)
    print(f"\n## ramos locais sem commit fora do main ({len(absorvidos)})")
    for r in absorvidos:
        print(f"  {r}")

    print("\n## worktrees")
    for w in worktrees(raiz):
        ramo = w.get("branch", "(detached)").replace("refs/heads/", "")
        print(f"  {w['worktree']}  [{ramo}]")
    return 0


if __name__ == "__main__":
    sys.exit(main())
