"""Monta os GIFs da vitrine a partir dos quadros dela: o "chiado -> sintonia" do Ressonador e a parede de Telas.

    WA_SHOWCASE=$PWD/run/showcase ./gradlew runClient                 # a vitrine inteira, com os quadros no fim
    WA_SHOWCASE=$PWD/run/showcase WA_SHOWCASE_ONLY=ressonador ./gradlew runClient   # só a casa e os quadros
    python -I scripts/curseforge/gerar_gif.py run/showcase/gif [docs/curseforge/ressonador.gif]
    python -I scripts/curseforge/gerar_gif.py run/showcase/gif docs/curseforge/tela.gif --prefixo tela         --recorte 120,150,1160,650 --largura 832 --laco 1500
    python -I scripts/curseforge/gerar_gif.py run/showcase/gif docs/curseforge/compactacao.gif --prefixo bau          --recorte 120,140,1160,665 --largura 1024 --laco 12160

Lê os <prefixo>-NNN.png em ordem (padrão ressonador), recorta (padrão 16:9 no centro; --recorte em coordenadas de
1280x800, qualquer que seja o tamanho da captura), reduz para --largura (padrão 640, LANCZOS) e salva em loop
infinito com 50 ms por quadro (20 fps) ou, com --laco, com os atrasos em centésimos repartidos para o laço durar
esses ms (o da parede de Telas é o da pixel art, 1,5 s). A paleta de 256 cores é uma só, tirada de um quadro a cada dez, sem
pontilhado: com uma paleta por quadro o fundo parado cintila (o pontilhado muda a cada quadro) e o GIF fica
várias vezes maior, porque o PIL só grava a parte que mudou quando as cores são as mesmas. Meta abaixo dos 2 MB que o
CurseForge aceita: se passar, tenta de novo com 192 e depois 128 cores. Avisa quando dois quadros seguidos saem
iguais (o tick não andou).
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

RAIZ = Path(__file__).resolve().parents[2]
SAIDA_PADRAO = RAIZ / "docs" / "curseforge" / "ressonador.gif"
LARGURA = 640
# o limite do CurseForge para um anexo (ver gerar_imagens.LIMITE)
META = 2_000_000


def quadros(pasta: Path, prefixo: str, recorte: tuple[int, int, int, int] | None, largura: int) -> list[Image.Image]:
    arquivos = sorted(pasta.glob(f"{prefixo}-*.png"))
    if not arquivos:
        raise SystemExit(f"nenhum {prefixo}-*.png em {pasta}")
    saida = []
    anterior = None
    for arquivo in arquivos:
        with Image.open(arquivo) as bruto:
            imagem = bruto.convert("RGB")
        w, h = imagem.size
        if recorte is None:
            alvo_h = min(h, w * 9 // 16)
            alvo_w = alvo_h * 16 // 9
            x, y = (w - alvo_w) // 2, (h - alvo_h) // 2
            caixa = (x, y, x + alvo_w, y + alvo_h)
        else:
            f = w / 1280
            caixa = tuple(round(v * f) for v in recorte)
        imagem = imagem.crop(caixa)
        altura = round(largura * imagem.height / imagem.width)
        imagem = imagem.resize((largura, altura), Image.LANCZOS)
        if anterior is not None and imagem.tobytes() == anterior:
            print(f"aviso: {arquivo.name} igual ao anterior")
        anterior = imagem.tobytes()
        saida.append(imagem)
    return saida


def paleta(imagens: list[Image.Image], cores: int) -> Image.Image:
    amostra = imagens[::10] if len(imagens) > 40 else imagens[::2]
    colunas = 4
    linhas = (len(amostra) + colunas - 1) // colunas
    w, h = imagens[0].size
    mosaico = Image.new("RGB", (w * colunas, h * linhas))
    for k, imagem in enumerate(amostra):
        mosaico.paste(imagem, ((k % colunas) * w, (k // colunas) * h))
    return mosaico.quantize(colors=cores, method=Image.Quantize.MEDIANCUT)


def atrasos(quadros_: int, laco: int | None) -> int | list[int]:
    """50 ms por quadro, ou os atrasos em centésimos inteiros que somam o laço de {@code laco} ms."""
    if laco is None:
        return 50
    marcas = [i * laco // 10 // quadros_ for i in range(quadros_ + 1)]
    return [(marcas[i + 1] - marcas[i]) * 10 for i in range(quadros_)]


def salvar(imagens: list[Image.Image], destino: Path, cores: int, duracao: int | list[int]) -> int:
    comum = paleta(imagens, cores)
    paletados = [im.quantize(palette=comum, dither=Image.Dither.NONE) for im in imagens]
    destino.parent.mkdir(parents=True, exist_ok=True)
    paletados[0].save(destino, save_all=True, append_images=paletados[1:], duration=duracao, loop=0,
                      optimize=True, disposal=1)
    return destino.stat().st_size


def opcao(args: list[str], nome: str) -> str | None:
    """Tira {@code --nome valor} da lista e devolve o valor, ou None."""
    if nome not in args:
        return None
    i = args.index(nome)
    valor = args[i + 1]
    del args[i:i + 2]
    return valor


def main() -> None:
    args = sys.argv[1:]
    prefixo = opcao(args, "--prefixo") or "ressonador"
    recorte_txt = opcao(args, "--recorte")
    recorte = tuple(int(v) for v in recorte_txt.split(",")) if recorte_txt else None
    largura = int(opcao(args, "--largura") or LARGURA)
    laco_txt = opcao(args, "--laco")
    laco = int(laco_txt) if laco_txt else None
    if not args:
        raise SystemExit("uso: gerar_gif.py <pasta dos quadros> [saída] [--prefixo p] [--recorte x0,y0,x1,y1] "
                         "[--largura w] [--laco ms]")
    pasta = Path(args[0])
    destino = Path(args[1]) if len(args) > 1 else SAIDA_PADRAO
    imagens = quadros(pasta, prefixo, recorte, largura)
    duracao = atrasos(len(imagens), laco)
    tamanho = salvar(imagens, destino, 256, duracao)
    for cores in (192, 128):
        if tamanho <= META:
            break
        print(f"{tamanho / 1024 / 1024:.2f} MB; tentando com {cores} cores")
        tamanho = salvar(imagens, destino, cores, duracao)
    if tamanho > META:
        raise SystemExit(f"{destino} passa de {META} bytes mesmo com 128 cores")
    print(f"{destino}: {len(imagens)} quadros, {tamanho / 1024 / 1024:.2f} MB")


if __name__ == "__main__":
    main()
