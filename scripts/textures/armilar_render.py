"""As texturas das peças sólidas da câmara da Armilar (`textures/armilar/*.png`): o anel, o grafite e os cristais.

O `gerar_texturas.py` chama `gerar()`, que grava as 7 texturas e a prévia `docs/preview/armilar-pecas.png` (8×, com o
nome) e devolve os nomes gravados. Densidade do mundo: 1 texel = 1/16 de bloco.

- `anel` (32 × 10) e `grafite` (8 × 8) são as do filme do conceito (`fx_atlas.py`, peças `anel` e `grafite`);
- `cristal_sinal` (8 × 16) é o `pec('cristal')` do filme;
- `cristal_lapidado`, `cristal_faixa`, `cristal_opaco` e `cristal_rachado` NÃO existem no filme: são arte nova no mesmo
  layout do Sinal (linhas 0 e 1 a ponta de cima, 2 a 7 o corpo, 12 a 14 a ponta de baixo; as faces em colunas).

O alfa da `cristal_faixa` é parcial de propósito (cinza, tingido pelo vértice), então o módulo grava com o
`Image.save` direto, sem o `validar` do `identidade.py`.

Uso: `python -I scripts/textures/armilar_render.py`.
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))

from fx_texturas import CORAL, LATAO, cinza, put  # noqa: E402
from identidade import ASSETS, PREVIEW, cor  # noqa: E402

PASTA = ASSETS / "armilar"
PREVIA = PREVIEW / "armilar-pecas.png"

PORC = [cor(f"porcelana.{t}") for t in "43210"]
GRAFITE = [cor(f"grafite.{t}") for t in "43210"]


def anel() -> Image.Image:
    """Latão facetado, período de 16 texels: 0-2 topo, 3-4 face de fora, 5-6 face de dentro, 7-9 base."""
    img = Image.new("RGBA", (32, 10), (0, 0, 0, 0))
    for x in range(32):
        k = x % 16
        col = [LATAO[0], LATAO[1], LATAO[1], LATAO[2], LATAO[2], LATAO[3], LATAO[3], LATAO[3], LATAO[4], LATAO[4]]
        for y in range(10):
            c = col[y]
            if y == 1 and k % 4 == 0:
                c = LATAO[2]  # traços da escala gravados no topo
            if y == 2 and k == 0:
                c = LATAO[3]
            if y == 3 and k in (6, 14):
                c = LATAO[0]  # rebites na face de fora
            if y == 4 and k in (6, 14):
                c = LATAO[3]
            if y == 3 and k == 10:
                c = CORAL[2]  # marca coral da casa
            put(img, x, y, c)
    return img


def grafite() -> Image.Image:
    """Pino e garfo: 8 × 8 em grafite, luz de cima à esquerda."""
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    for y in range(8):
        for x in range(8):
            c = GRAFITE[2]
            if y == 0 or x == 0:
                c = GRAFITE[1]
            if y == 7 or x == 7:
                c = GRAFITE[3]
            put(img, x, y, c)
    return img


def cristal_sinal() -> Image.Image:
    """8 × 16, facetas em rampa coral com o brilho de cima e a aresta escura (o `pec('cristal')` do filme)."""
    img = Image.new("RGBA", (8, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(8):
            c = CORAL[1] if x < 2 else CORAL[2] if x < 5 else CORAL[3]
            if y < 2:
                c = CORAL[0]
            if x == 7 or y == 15:
                c = CORAL[4]
            if x == 1 and 3 <= y < 10:
                c = CORAL[0]
            put(img, x, y, c)
    return img


def cristal_lapidado() -> Image.Image:
    """Arte nova. O Sinal com as facetas mais definidas (um fio de luz na quina de cada face) e a faixa dourada nas
    linhas 4 e 5 do corpo, a cinta do losango antigo."""
    img = cristal_sinal()
    for y in range(2, 15):
        put(img, 2, y, CORAL[1])    # a quina entre a primeira e a segunda face, em sombra
        put(img, 5, y, CORAL[2])    # a quina entre a segunda e a terceira face, em meio-tom
    for x in range(8):
        put(img, x, 4, LATAO[1] if x < 7 else LATAO[3])    # a cinta: luz na linha de cima, sombra na de baixo
        put(img, x, 5, LATAO[2] if x < 7 else LATAO[4])
    put(img, 1, 4, LATAO[0])
    return img


def cristal_faixa() -> Image.Image:
    """Arte nova. Só as linhas 4 e 5, em cinza claro (branco com alfa): o Afinado a tinge na cor do tier pelo vértice."""
    img = Image.new("RGBA", (8, 16), (0, 0, 0, 0))
    for x in range(8):
        put(img, x, 4, cinza(255 if x < 7 else 200))
        put(img, x, 5, cinza(190 if x < 7 else 130))
    return img


def cristal_opaco() -> Image.Image:
    """Arte nova. O desenho do Sinal em osso e grafite, sem brilho."""
    img = Image.new("RGBA", (8, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(8):
            c = PORC[2] if x < 2 else PORC[3] if x < 5 else PORC[4]
            if y < 2:
                c = PORC[1]
            if x == 7 or y == 15:
                c = GRAFITE[2]
            put(img, x, y, c)
    return img


# A trinca: uma diagonal que cruza a primeira e a segunda face (x = 2) e a segunda e a terceira (x = 5).
TRINCA = [(1, 3), (2, 4), (2, 5), (3, 6), (4, 7), (4, 8), (5, 9), (6, 10), (6, 11)]


def cristal_rachado() -> Image.Image:
    """Arte nova. O Opaco com a trinca de grafite escuro cruzando duas faces."""
    img = cristal_opaco()
    for x, y in TRINCA:
        put(img, x, y, GRAFITE[4])
    return img


PECAS = {
    "anel": anel,
    "grafite": grafite,
    "cristal_sinal": cristal_sinal,
    "cristal_lapidado": cristal_lapidado,
    "cristal_faixa": cristal_faixa,
    "cristal_opaco": cristal_opaco,
    "cristal_rachado": cristal_rachado,
}


def previa(imagens: dict[str, Image.Image]) -> Image.Image:
    """As texturas em 8× com o nome, numa linha sobre fundo escuro e outra sobre fundo claro."""
    esc, margem, topo = 8, 16, 18
    fonte = ImageFont.load_default()
    larguras = [max(i.width * esc, 130) for i in imagens.values()]
    largura = margem + sum(larguras) + margem * len(larguras)
    altura_linha = max(i.height for i in imagens.values()) * esc + topo + margem
    out = Image.new("RGBA", (largura, altura_linha * 2 + margem), (0, 0, 0, 255))
    for n, fundo in enumerate([(24, 22, 30, 255), (236, 230, 216, 255)]):
        d = ImageDraw.Draw(out)
        d.rectangle((0, n * altura_linha, largura, (n + 1) * altura_linha + (margem if n else 0)), fill=fundo)
        texto = (240, 236, 226, 255) if n == 0 else (40, 36, 44, 255)
        x = margem
        for (nome, img), w in zip(imagens.items(), larguras):
            d.text((x, n * altura_linha + margem // 2), nome, fill=texto, font=fonte)
            g = img.resize((img.width * esc, img.height * esc), Image.NEAREST)
            out.alpha_composite(g, (x, n * altura_linha + margem // 2 + topo))
            x += w + margem
    return out.convert("RGB")


def gerar() -> set[str]:
    """Grava as texturas e a prévia; devolve os nomes gravados e apaga da pasta o que sobrou de antes."""
    PASTA.mkdir(parents=True, exist_ok=True)
    imagens = {nome: fn() for nome, fn in PECAS.items()}
    for nome, img in imagens.items():
        img.save(PASTA / f"{nome}.png")
    for arq in PASTA.glob("*.png"):
        if arq.stem not in imagens:
            arq.unlink()
    PREVIA.parent.mkdir(parents=True, exist_ok=True)
    previa(imagens).save(PREVIA)
    return set(imagens)


if __name__ == "__main__":
    print(f"{len(gerar())} texturas em {PASTA}")
