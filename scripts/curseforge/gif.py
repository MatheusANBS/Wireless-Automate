"""Monta os WebPs animados da vitrine do CurseForge a partir dos quadros que o jogo grava (client/dev/vitrine).

    ./scripts/e2e.sh --vitrine todas                      # grava run/vitrine/<cena>/NNNN.png e quadros.csv
    python -I scripts/curseforge/gif.py run/vitrine                       # monta docs/curseforge/<cena>.webp de todas
    python -I scripts/curseforge/gif.py run/vitrine roteador bau          # só essas cenas
    python -I scripts/curseforge/gif.py run/vitrine --folha folha.png     # e uma folha de contato, seis quadros de cada

O jogo anota em quadros.csv o instante de cada quadro (ms) e o cursor do mouse (pixels do quadro, -1 sem tela
aberta) com a idade do último clique (ms). Aqui os quadros são reamostrados a 30 por segundo pelo instante (o ritmo
da animação é o do jogo), o cursor e o realce do clique são desenhados por cima e o ffmpeg grava o WebP animado em
laço infinito: todas as cores (o GIF só tem 256 por quadro, o que dava faixas no céu e no brilho do shader) e o
tamanho nativo da captura, sem reduzir nem recortar. O nome do arquivo ainda é gif.py (o script nasceu montando GIFs).
"""
from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parents[2]
SAIDA = RAIZ / "docs" / "curseforge"
FPS = 30
CENAS = ["roteador", "filtro", "bau", "doca", "tela", "armilar-montagem", "armilar-fora", "armilar-sintonia",
         "armilar-conjuncao"]
# o WebP é com perda; tenta estas qualidades, da melhor para a pior, e fica com a primeira que cabe no limite (MB)
QUALIDADES = [88, 82, 76, 70, 64, 58, 52]
LIMITE_MB = 8.0
ESFORCO = "4"
# a seta do mouse (ponta em 0,0), em unidades de 1/13,5 da altura da seta
SETA = [(0, 0), (0, 11.5), (2.9, 9), (5, 13.5), (6.9, 12.6), (4.8, 8.2), (8.4, 8.2)]
ALTURA_SETA = 30


def ler(pasta: Path) -> list[tuple[Path, int, float, float, float]]:
    linhas = []
    for linha in (pasta / "quadros.csv").read_text(encoding="utf-8").splitlines():
        arquivo, ms, x, y, idade = linha.split(";")
        linhas.append((pasta / arquivo, int(ms), float(x), float(y), float(idade)))
    return linhas


def reamostrar(quadros: list) -> list:
    """Um quadro por 1/30 s de jogo, o mais perto do instante."""
    passo = 1000 / FPS
    escolhidos, k = [], 0
    for alvo in (i * passo for i in range(int(quadros[-1][1] / passo) + 1)):
        while k + 1 < len(quadros) and abs(quadros[k + 1][1] - alvo) <= abs(quadros[k][1] - alvo):
            k += 1
        escolhidos.append(quadros[k])
    return escolhidos


def cursor(imagem: Image.Image, x: float, y: float, idade: float) -> None:
    """A seta branca de contorno preto e, no clique, um anel que se abre e some em 350 ms."""
    ds = ImageDraw.Draw(imagem, "RGBA")
    u = ALTURA_SETA / 13.5
    if idade < 350:
        t = idade / 350
        r = ALTURA_SETA * (0.35 + 0.9 * t)
        ds.ellipse((x - r, y - r, x + r, y + r), outline=(255, 214, 90, int(235 * (1 - t))), width=3)
    seta = [(x + px * u, y + py * u) for px, py in SETA]
    ds.polygon(seta, fill=(255, 255, 255, 255), outline=(8, 8, 8, 255), width=2)


def quadro(arquivo: Path, cx: float, cy: float, idade: float) -> Image.Image:
    imagem = Image.open(arquivo).convert("RGB")
    if cx >= 0:
        cursor(imagem, cx, cy, idade)
    return imagem


def ffmpeg(*args: str) -> None:
    exe = shutil.which("ffmpeg")
    if exe is None:
        raise SystemExit("falta o ffmpeg no PATH")
    subprocess.run([exe, "-y", "-loglevel", "error", *args], check=True)


def montar(pasta: Path, nome: str) -> Path:
    """Os quadros desenhados em <cena>/_webp e o WebP em docs/curseforge; devolve o WebP."""
    quadros = reamostrar(ler(pasta / nome))
    temp = pasta / nome / "_webp"
    shutil.rmtree(temp, ignore_errors=True)
    temp.mkdir()
    for i, q in enumerate(quadros):
        quadro(q[0], q[2], q[3], q[4]).save(temp / f"{i:04d}.png")
    destino = SAIDA / f"{nome}.webp"
    destino.parent.mkdir(parents=True, exist_ok=True)
    for qualidade in QUALIDADES:
        ffmpeg("-framerate", str(FPS), "-i", str(temp / "%04d.png"), "-c:v", "libwebp_anim", "-lossless", "0",
               "-quality", str(qualidade), "-compression_level", ESFORCO, "-loop", "0", str(destino))
        if destino.stat().st_size / 1e6 <= LIMITE_MB:
            break
    print(f"{destino}: {len(quadros)} quadros, qualidade {qualidade}, {destino.stat().st_size / 1e6:.2f} MB")
    return destino


def folha(webps: list[Path], destino: Path, por_linha: int = 6, largura: int = 320) -> None:
    """A folha de contato: uma linha por WebP, com seis quadros espaçados, lidos do arquivo final."""
    linhas = []
    for webp in webps:
        with Image.open(webp) as im:
            n = im.n_frames
            fatias = []
            for i in range(por_linha):
                im.seek(round(i * (n - 1) / (por_linha - 1)))
                q = im.convert("RGB")
                fatias.append(q.resize((largura, round(q.height * largura / q.width)), Image.LANCZOS))
        linhas.append(fatias)
    alto = linhas[0][0].height
    saida = Image.new("RGB", (por_linha * largura, len(linhas) * alto), (30, 30, 30))
    for y, fatias in enumerate(linhas):
        for x, f in enumerate(fatias):
            saida.paste(f, (x * largura, y * alto))
    destino.parent.mkdir(parents=True, exist_ok=True)
    saida.save(destino)


def main() -> None:
    args = sys.argv[1:]
    sheet = None
    if "--folha" in args:
        i = args.index("--folha")
        sheet = Path(args[i + 1])
        del args[i : i + 2]
    if not args:
        raise SystemExit("uso: gif.py <pasta dos quadros> [cena ...] [--folha folha.png]")
    pasta = Path(args[0])
    nomes = args[1:] or [n for n in CENAS if (pasta / n / "quadros.csv").exists()]
    montados = [montar(pasta, nome) for nome in nomes]
    if sheet:
        folha(montados, sheet)


if __name__ == "__main__":
    main()
