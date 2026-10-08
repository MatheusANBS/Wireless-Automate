"""Folha de contato: junta várias capturas numa imagem só, para conferir de uma vez.

    python .claude/skills/wa-curseforge-imagens/scripts/folha.py <saida.png> <img1> [<img2> ...]
        [--colunas 2] [--largura 800]

Aceita padrões (glob), como run/showcase/s*.png. Cada imagem é reduzida para --largura,
mantendo a proporção; a altura de cada linha é a da maior imagem dela. O nome do arquivo vai no
canto, para saber qual é qual.
"""
from __future__ import annotations

import argparse
import glob
from pathlib import Path

from PIL import Image, ImageDraw


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("saida")
    p.add_argument("imagens", nargs="+")
    p.add_argument("--colunas", type=int, default=2)
    p.add_argument("--largura", type=int, default=800)
    a = p.parse_args()
    caminhos = [Path(c) for padrao in a.imagens for c in sorted(glob.glob(padrao))]
    if not caminhos:
        raise SystemExit("nenhuma imagem encontrada")
    fotos = []
    for c in caminhos:
        img = Image.open(c).convert("RGB")
        img = img.resize((a.largura, round(a.largura * img.height / img.width)))
        ImageDraw.Draw(img).text((6, 4), c.stem, fill=(255, 255, 0))
        fotos.append(img)
    linhas = [fotos[i:i + a.colunas] for i in range(0, len(fotos), a.colunas)]
    alturas = [max(f.height for f in linha) for linha in linhas]
    folha = Image.new("RGB", (a.largura * a.colunas, sum(alturas)))
    y = 0
    for linha, altura in zip(linhas, alturas):
        for i, f in enumerate(linha):
            folha.paste(f, (i * a.largura, y))
        y += altura
    folha.save(a.saida)
    print(f"{a.saida}: {len(fotos)} imagens, {folha.width}x{folha.height}")


if __name__ == "__main__":
    main()
