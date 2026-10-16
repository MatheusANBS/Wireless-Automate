"""Monta o GIF "chiado -> sintonia" do Ressonador de Sinal a partir dos quadros da vitrine.

    WA_SHOWCASE=$PWD/run/showcase ./gradlew runClient                 # a vitrine inteira, com os quadros no fim
    WA_SHOWCASE=$PWD/run/showcase WA_SHOWCASE_ONLY=ressonador ./gradlew runClient   # só a casa e os quadros
    python -I scripts/curseforge/gerar_gif.py run/showcase/gif [docs/curseforge/ressonador.gif]

Lê os ressonador-NNN.png em ordem, recorta 16:9 no centro, reduz para 640 x 360 (LANCZOS) e salva com 50 ms
por quadro (20 fps) e loop infinito. A paleta de 256 cores é uma só, tirada de um quadro a cada dez, sem
pontilhado: com uma paleta por quadro o fundo parado cintila (o pontilhado muda a cada quadro) e o GIF fica
várias vezes maior, porque o PIL só grava a parte que mudou quando as cores são as mesmas. Meta abaixo de 6 MB: se
passar, tenta de novo com 192 cores. Avisa quando dois quadros seguidos saem iguais (o tick não andou).
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

RAIZ = Path(__file__).resolve().parents[2]
SAIDA_PADRAO = RAIZ / "docs" / "curseforge" / "ressonador.gif"
LARGURA, ALTURA = 640, 360
META = 6 * 1024 * 1024


def quadros(pasta: Path) -> list[Image.Image]:
    arquivos = sorted(pasta.glob("ressonador-*.png"))
    if not arquivos:
        raise SystemExit(f"nenhum ressonador-*.png em {pasta}")
    saida = []
    anterior = None
    for arquivo in arquivos:
        with Image.open(arquivo) as bruto:
            imagem = bruto.convert("RGB")
        w, h = imagem.size
        alvo_h = min(h, w * 9 // 16)
        alvo_w = alvo_h * 16 // 9
        x, y = (w - alvo_w) // 2, (h - alvo_h) // 2
        imagem = imagem.crop((x, y, x + alvo_w, y + alvo_h)).resize((LARGURA, ALTURA), Image.LANCZOS)
        if anterior is not None and imagem.tobytes() == anterior:
            print(f"aviso: {arquivo.name} igual ao anterior")
        anterior = imagem.tobytes()
        saida.append(imagem)
    return saida


def paleta(imagens: list[Image.Image], cores: int) -> Image.Image:
    amostra = imagens[::10]
    colunas = 4
    linhas = (len(amostra) + colunas - 1) // colunas
    mosaico = Image.new("RGB", (LARGURA * colunas, ALTURA * linhas))
    for k, imagem in enumerate(amostra):
        mosaico.paste(imagem, ((k % colunas) * LARGURA, (k // colunas) * ALTURA))
    return mosaico.quantize(colors=cores, method=Image.Quantize.MEDIANCUT)


def salvar(imagens: list[Image.Image], destino: Path, cores: int) -> int:
    comum = paleta(imagens, cores)
    paletados = [im.quantize(palette=comum, dither=Image.Dither.NONE) for im in imagens]
    destino.parent.mkdir(parents=True, exist_ok=True)
    paletados[0].save(destino, save_all=True, append_images=paletados[1:], duration=50, loop=0,
                      optimize=True, disposal=1)
    return destino.stat().st_size


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit("uso: gerar_gif.py <pasta dos quadros> [saída]")
    pasta = Path(sys.argv[1])
    destino = Path(sys.argv[2]) if len(sys.argv) > 2 else SAIDA_PADRAO
    imagens = quadros(pasta)
    tamanho = salvar(imagens, destino, 256)
    if tamanho > META:
        print(f"{tamanho / 1024 / 1024:.2f} MB com 256 cores; tentando com 192")
        tamanho = salvar(imagens, destino, 192)
    print(f"{destino}: {len(imagens)} quadros, {tamanho / 1024 / 1024:.2f} MB")


if __name__ == "__main__":
    main()
