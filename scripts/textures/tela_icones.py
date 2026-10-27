"""Ícones do editor da Tela de Sinal: sprites 16 × 16 em pixel art, um por botão, em duas variantes.

Gera textures/gui/tv/<nome>.png (para o tema claro: tinta grafite) e textures/gui/tv/<nome>_dark.png (para o
tema escuro: tinta porcelana), mais a prancha docs/preview/tela-icones.png com os dois fundos das telas em 1×, 2× e
4×. Os sprites são feitos para serem desenhados em escala inteira (16 px, 32 px), nunca reduzidos.

O estilo é de linha: traço de 1 px na tinta, tudo dentro da mesma caixa de 12 × 12 (de 2 a 13), simétrico em
torno do centro (7,5) sempre que a forma deixa, e o coral só num detalhe por ícone. Cada ícone é montado por
geometria (linhas, retângulos, espelho), não pintado à mão, para os traços saírem iguais entre os ícones.

Uso: python -I scripts/textures/tela_icones.py
"""
from __future__ import annotations

import sys
from pathlib import Path
from typing import Callable

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from identidade import ASSETS, PREVIEW, cor  # noqa: E402

DESTINO = ASSETS / "gui" / "tv"

# K tinta, A destaque (coral), L luz do destaque, P papel (miolo claro).
TEMAS = {
    "": dict(K="grafite.1", A="coral.2", L="coral.4", P="porcelana.4"),
    "_dark": dict(K="porcelana.3", A="coral.3", L="coral.4", P="grafite.3"),
}


class Quadro:
    """Um quadro de 16 × 16 com as primitivas do desenho de linha."""

    def __init__(self) -> None:
        self.px: dict[tuple[int, int], str] = {}

    def p(self, x: int, y: int, c: str = "K") -> None:
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[(x, y)] = c

    def h(self, x0: int, x1: int, y: int, c: str = "K") -> None:
        for x in range(x0, x1 + 1):
            self.p(x, y, c)

    def v(self, x: int, y0: int, y1: int, c: str = "K") -> None:
        for y in range(y0, y1 + 1):
            self.p(x, y, c)

    def caixa(self, x0: int, y0: int, x1: int, y1: int, c: str = "K", redonda: bool = False) -> None:
        """Contorno; redonda tira os quatro cantos (raio de 1 px)."""
        a, b = (1, 1) if redonda else (0, 0)
        self.h(x0 + a, x1 - a, y0, c)
        self.h(x0 + a, x1 - a, y1, c)
        self.v(x0, y0 + b, y1 - b, c)
        self.v(x1, y0 + b, y1 - b, c)

    def cheio(self, x0: int, y0: int, x1: int, y1: int, c: str) -> None:
        for y in range(y0, y1 + 1):
            self.h(x0, x1, y, c)

    def diag(self, x0: int, y0: int, x1: int, y1: int, c: str = "K") -> None:
        """Linha a 45°."""
        n = abs(x1 - x0)
        sx = 1 if x1 >= x0 else -1
        sy = 1 if y1 >= y0 else -1
        for i in range(n + 1):
            self.p(x0 + sx * i, y0 + sy * i, c)

    def espelho(self) -> "Quadro":
        q = Quadro()
        q.px = {(15 - x, y): c for (x, y), c in self.px.items()}
        return q

    def imagem(self, tema: dict[str, str]) -> Image.Image:
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for (x, y), c in self.px.items():
            img.putpixel((x, y), cor(tema[c]))
        return img


ICONES: dict[str, Callable[[], Quadro]] = {}


def icone(nome: str):
    def registra(f: Callable[[Quadro], None]) -> Callable[[Quadro], None]:
        def monta() -> Quadro:
            q = Quadro()
            f(q)
            return q
        ICONES[nome] = monta
        return f
    return registra


# ---------------------------------------------------------------- peças

@icone("value")
def _value(q: Quadro) -> None:
    q.v(5, 3, 12)
    q.v(10, 3, 12)
    q.h(3, 12, 5)
    q.h(3, 12, 10)


@icone("text")
def _text(q: Quadro) -> None:
    q.h(3, 12, 3)
    q.p(3, 4)
    q.p(12, 4)
    q.v(7, 4, 12)
    q.v(8, 4, 12)
    q.h(6, 9, 12)


@icone("shape")
def _shape(q: Quadro) -> None:
    q.cheio(4, 4, 11, 11, "A")
    q.caixa(3, 3, 12, 12, redonda=True)
    q.h(5, 7, 5, "L")
    q.p(5, 6, "L")


# ---------------------------------------------------------------- abas da coluna

@icone("pieces")
def _pieces(q: Quadro) -> None:
    q.cheio(4, 4, 6, 6, "A")
    for x0, y0 in ((3, 3), (9, 3), (3, 9), (9, 9)):
        q.caixa(x0, y0, x0 + 4, y0 + 4)


@icone("components")
def _components(q: Quadro) -> None:
    q.caixa(3, 3, 9, 9)
    q.cheio(6, 6, 12, 12, "P")
    q.cheio(7, 7, 11, 11, "A")
    q.caixa(6, 6, 12, 12)


@icone("layers")
def _layers(q: Quadro) -> None:
    # A placa de cima em losango cheio e duas bordas em V embaixo, com 1 px de folga entre elas.
    q.h(7, 8, 2)
    q.h(5, 6, 3)
    q.h(9, 10, 3)
    q.h(3, 4, 4)
    q.h(11, 12, 4)
    q.h(5, 6, 5)
    q.h(9, 10, 5)
    q.h(7, 8, 6)
    q.h(7, 8, 3, "A")
    q.h(5, 10, 4, "A")
    q.h(7, 8, 5, "A")
    for y in (8, 11):
        q.h(3, 4, y)
        q.h(5, 6, y + 1)
        q.h(7, 8, y + 2)
        q.h(9, 10, y + 1)
        q.h(11, 12, y)


# ---------------------------------------------------------------- barra de botões

@icone("undo")
def _undo(q: Quadro) -> None:
    q.diag(5, 3, 3, 5)
    q.diag(3, 5, 5, 7)
    q.h(4, 10, 5)
    q.p(11, 6)
    q.v(12, 7, 9)
    q.p(11, 10)
    q.h(5, 10, 11)


ICONES["redo"] = lambda: ICONES["undo"]().espelho()


@icone("group")
def _group(q: Quadro) -> None:
    for i in (2, 3, 6, 7, 8, 9, 12, 13):
        q.p(i, 2, "A")
        q.p(i, 13, "A")
        q.p(2, i, "A")
        q.p(13, i, "A")
    q.caixa(4, 4, 8, 8)
    q.caixa(7, 7, 11, 11)


@icone("ungroup")
def _ungroup(q: Quadro) -> None:
    q.caixa(3, 3, 7, 7)
    q.caixa(8, 8, 12, 12)


@icone("duplicate")
def _duplicate(q: Quadro) -> None:
    q.caixa(3, 3, 9, 9)
    q.cheio(6, 6, 12, 12, "P")
    q.caixa(6, 6, 12, 12)
    q.v(9, 8, 10, "A")
    q.h(8, 10, 9, "A")


@icone("remove")
def _remove(q: Quadro) -> None:
    q.h(6, 9, 2)
    q.h(3, 12, 3)
    q.v(4, 5, 12)
    q.v(11, 5, 12)
    q.h(5, 10, 13)
    q.v(6, 6, 11, "A")
    q.v(9, 6, 11, "A")


@icone("save")
def _save(q: Quadro) -> None:
    q.h(3, 11, 3)
    q.p(12, 4)
    q.v(12, 5, 12)
    q.v(3, 3, 12)
    q.h(3, 12, 12)
    q.caixa(5, 3, 10, 6)
    q.cheio(5, 9, 10, 12, "A")
    q.h(5, 10, 8)


@icone("save_component")
def _save_component(q: Quadro) -> None:
    q.caixa(3, 6, 9, 12)
    q.v(11, 2, 6, "A")
    q.h(9, 13, 4, "A")


@icone("fit")
def _fit(q: Quadro) -> None:
    for x0, sx in ((2, 1), (13, -1)):
        for y0, sy in ((2, 1), (13, -1)):
            q.h(min(x0, x0 + 2 * sx), max(x0, x0 + 2 * sx), y0)
            q.v(x0, min(y0, y0 + 2 * sy), max(y0, y0 + 2 * sy))
    q.caixa(6, 6, 9, 9, "A")


def _lupa(q: Quadro) -> None:
    q.h(4, 8, 2)
    q.p(3, 3)
    q.p(9, 3)
    q.v(2, 4, 8)
    q.v(10, 4, 8)
    q.p(3, 9)
    q.p(9, 9)
    q.h(4, 8, 10)
    q.diag(10, 10, 13, 13)
    q.diag(10, 11, 12, 13)


@icone("zoom_in")
def _zoom_in(q: Quadro) -> None:
    _lupa(q)
    q.h(4, 8, 6, "A")
    q.v(6, 4, 8, "A")


@icone("zoom_out")
def _zoom_out(q: Quadro) -> None:
    _lupa(q)
    q.h(4, 8, 6, "A")


# ---------------------------------------------------------------- camadas

def _amendoa(q: Quadro) -> None:
    q.h(5, 10, 4)
    q.h(3, 4, 5)
    q.h(11, 12, 5)
    q.v(2, 6, 9)
    q.v(13, 6, 9)
    q.h(3, 4, 10)
    q.h(11, 12, 10)
    q.h(5, 10, 11)


@icone("eye")
def _eye(q: Quadro) -> None:
    _amendoa(q)
    q.cheio(6, 6, 9, 9, "A")
    q.p(6, 6, "P")
    q.p(9, 6, "P")
    q.p(6, 9, "P")
    q.p(9, 9, "P")
    q.px.pop((6, 6))
    q.px.pop((9, 6))
    q.px.pop((6, 9))
    q.px.pop((9, 9))
    q.p(7, 7, "L")


@icone("eye_closed")
def _eye_closed(q: Quadro) -> None:
    q.v(2, 6, 7)
    q.v(13, 6, 7)
    q.h(3, 4, 8)
    q.h(11, 12, 8)
    q.h(5, 10, 9)
    q.p(4, 10)
    q.p(3, 11)
    q.p(11, 10)
    q.p(12, 11)
    q.v(7, 10, 11)
    q.v(8, 10, 11)


@icone("lock")
def _lock(q: Quadro) -> None:
    q.h(6, 9, 2)
    q.p(5, 3)
    q.p(10, 3)
    q.v(4, 4, 6)
    q.v(11, 4, 6)
    q.cheio(4, 8, 11, 12, "A")
    q.caixa(3, 7, 12, 13)
    q.v(7, 9, 11, "K")
    q.v(8, 9, 11, "K")


# ---------------------------------------------------------------- estilo

@icone("align_left")
def _align_left(q: Quadro) -> None:
    q.h(2, 13, 3)
    q.h(2, 9, 6)
    q.h(2, 13, 9)
    q.h(2, 9, 12)


@icone("align_center")
def _align_center(q: Quadro) -> None:
    q.h(2, 13, 3)
    q.h(4, 11, 6)
    q.h(2, 13, 9)
    q.h(4, 11, 12)


ICONES["align_right"] = lambda: ICONES["align_left"]().espelho()


@icone("eyedropper")
def _eyedropper(q: Quadro) -> None:
    # O bulbo em cima à direita e o tubo a 45° até a ponta embaixo à esquerda.
    q.cheio(11, 2, 12, 3, "K")
    q.cheio(10, 3, 13, 4, "K")
    q.diag(9, 4, 12, 7)
    q.diag(8, 5, 3, 10)
    q.diag(11, 7, 5, 13)
    q.diag(9, 6, 4, 11, "P")
    q.diag(10, 6, 5, 11, "P")
    q.p(3, 11)
    q.p(4, 12)
    q.p(2, 13, "A")
    q.p(3, 12, "A")


@icone("rename")
def _rename(q: Quadro) -> None:
    # O lápis a 45°, com o corpo em coral e a ponta clara.
    q.diag(11, 2, 13, 4)
    q.diag(10, 3, 4, 9)
    q.diag(13, 5, 7, 11)
    q.diag(11, 4, 6, 9, "A")
    q.diag(12, 4, 7, 9, "A")
    q.diag(11, 3, 12, 4, "L")
    q.p(4, 10)
    q.p(5, 11)
    q.p(6, 11)
    q.p(5, 10, "P")
    q.p(3, 12)
    q.h(2, 13, 13)


@icone("section_open")
def _section_open(q: Quadro) -> None:
    for i in range(4):
        q.h(4 + i, 11 - i, 6 + i)


@icone("section_closed")
def _section_closed(q: Quadro) -> None:
    for i in range(4):
        q.v(6 + i, 4 + i, 11 - i)


@icone("add")
def _add(q: Quadro) -> None:
    q.v(7, 3, 12, "A")
    q.v(8, 3, 12, "A")
    q.h(3, 12, 7, "A")
    q.h(3, 12, 8, "A")


@icone("rules")
def _rules(q: Quadro) -> None:
    for y in (3, 7, 11):
        q.caixa(2, y, 4, y + 2)
        q.h(7, 13 if y != 7 else 11, y + 1)
    q.p(3, 4, "A")


# ---------------------------------------------------------------- saída

def imagem(nome: str, tema: str) -> Image.Image:
    return ICONES[nome]().imagem(TEMAS[tema])


def prancha(imagens: dict[str, Image.Image]) -> Image.Image:
    nomes = list(ICONES)
    fundos = [cor("porcelana.3"), cor("grafite.2")]
    escalas = [1, 2, 4]
    celula = 16 * 4 + 12
    margem = 16
    largura = margem * 2 + 130 + celula * len(escalas) * len(fundos)
    altura = margem * 2 + 24 + len(nomes) * (16 * 4 + 8)
    out = Image.new("RGBA", (largura, altura), cor("porcelana.2"))
    d = ImageDraw.Draw(out)
    try:
        fonte = ImageFont.truetype("arial.ttf", 13)
    except OSError:
        fonte = ImageFont.load_default()
    d.text((margem, margem), "Ícones do editor da Tela de Sinal (1×, 2×, 4×, nos dois temas)",
           fill=cor("grafite.1"), font=fonte)
    y = margem + 24
    for nome in nomes:
        d.text((margem, y + 26), nome, fill=cor("grafite.1"), font=fonte)
        x = margem + 130
        for fundo, sufixo in zip(fundos, TEMAS):
            d.rectangle((x - 4, y - 2, x + celula * len(escalas) - 8, y + 16 * 4 + 4), fill=fundo)
            for e in escalas:
                img = imagens[nome + sufixo].resize((16 * e, 16 * e), Image.NEAREST)
                out.alpha_composite(img, (x, y + (16 * 4 - 16 * e) // 2))
                x += celula
        y += 16 * 4 + 8
    return out


def main() -> None:
    DESTINO.mkdir(parents=True, exist_ok=True)
    imagens: dict[str, Image.Image] = {}
    for nome in ICONES:
        for tema in TEMAS:
            img = imagem(nome, tema)
            assert set(img.getchannel("A").tobytes()) <= {0, 255}, f"{nome}{tema}: alfa parcial"
            img.save(DESTINO / f"{nome}{tema}.png")
            imagens[nome + tema] = img
    prancha(imagens).save(PREVIEW / "tela-icones.png")
    print(f"{len(ICONES)} ícones × {len(TEMAS)} temas; prancha em docs/preview/tela-icones.png")


if __name__ == "__main__":
    main()
