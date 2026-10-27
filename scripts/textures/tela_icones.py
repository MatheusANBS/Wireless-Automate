"""Ícones do editor da Tela de Sinal: sprites 16 × 16 em pixel art, um por botão, em duas variantes.

Gera textures/gui/tv/<nome>.png (para o tema claro: tinta grafite) e textures/gui/tv/<nome>_dark.png (para o
tema escuro: tinta porcelana), mais a prancha docs/preview/tela-icones.png com os dois fundos das telas em 1×, 2× e
4×. A arte é desenhada em grades de texto, como os sprites do gerar_texturas.py, sobre a paleta do identidade.py.
Os sprites são feitos para serem desenhados em escala inteira (16 px, 32 px), nunca reduzidos.

Legenda das grades: K tinta, S tinta suave (sombra e detalhe), A destaque (coral), L luz do destaque,
D sombra do destaque, P papel (o miolo claro de folhas e discos), '.' transparente.

Uso: python -I scripts/textures/tela_icones.py
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from identidade import ASSETS, PREVIEW, cor, pinta  # noqa: E402

DESTINO = ASSETS / "gui" / "tv"

TEMAS = {
    "": dict(K="grafite.1", S="porcelana.0", A="coral.2", L="coral.4", D="coral.0", P="porcelana.4"),
    "_dark": dict(K="porcelana.3", S="porcelana.0", A="coral.3", L="coral.4", D="coral.1", P="grafite.3"),
}

ICONES: dict[str, list[str]] = {
    # ---- peças ----
    "value": [
        "................",
        "................",
        ".....KK...KK....",
        ".....KK...KK....",
        "...KKKKKKKKKKK..",
        "...KKKKKKKKKKK..",
        "....KK...KK.....",
        "....KK...KK.....",
        "....KK...KK.....",
        "..KKKKKKKKKKK...",
        "..KKKKKKKKKKK...",
        "...KK...KK......",
        "...KK...KK......",
        "................",
    ],
    "text": [
        "................",
        "................",
        "..KKKKKKKKKKKK..",
        "..KKKKKKKKKKKK..",
        "..KK...KK...KK..",
        ".......KK.......",
        ".......KK.......",
        ".......KK.......",
        ".......KK.......",
        ".......KK.......",
        ".......KK.......",
        "......KKKK......",
        "......KKKK......",
        "................",
    ],
    "shape": [
        "................",
        "................",
        "...KKKKKKKKKK...",
        "..KLLLAAAAAAAK..",
        "..KLAAAAAAAAAK..",
        "..KAAAAAAAAAAK..",
        "..KAAAAAAAAAAK..",
        "..KAAAAAAAAAAK..",
        "..KAAAAAAAAAAK..",
        "..KAAAAAAAAADK..",
        "..KAAAAAAAADDK..",
        "...KKKKKKKKKK...",
        "................",
    ],
    # ---- abas da coluna da esquerda ----
    "pieces": [
        "................",
        "................",
        "..KKKKK..KKKKK..",
        "..KAAAK..KPPPK..",
        "..KAAAK..KPPPK..",
        "..KAAAK..KPPPK..",
        "..KKKKK..KKKKK..",
        "................",
        "..KKKKK..KKKKK..",
        "..KPPPK..KPPPK..",
        "..KPPPK..KPPPK..",
        "..KPPPK..KPPPK..",
        "..KKKKK..KKKKK..",
        "................",
    ],
    "components": [
        "................",
        "................",
        "..KKKKKKKK......",
        "..KPPPPPPK......",
        "..KPPPPPPK......",
        "..KPPPKKKKKKKK..",
        "..KPPPKAAAAAAK..",
        "..KPPPKALLAAAK..",
        "..KKKKKAAAAAAK..",
        "......KAAAAAAK..",
        "......KAAAAADK..",
        "......KKKKKKKK..",
        "................",
    ],
    "layers": [
        "................",
        ".......KK.......",
        ".....KKAAKK.....",
        "...KKAALAAAKK...",
        ".KKAAAAAAAAAAKK.",
        "...KKAAAAAAKK...",
        ".S...KKAAKK...S.",
        "..SS...KK...SS..",
        "....SS....SS....",
        ".S....SSSS....S.",
        "..SS........SS..",
        "....SS....SS....",
        "......SSSS......",
        "................",
    ],
    # ---- barra de botões ----
    "undo": [
        "................",
        "................",
        ".....K..........",
        "....KK..........",
        "...KKKKKKKKK....",
        "..KKKKKKKKKKK...",
        "...KKK.....KKK..",
        "....KK......KK..",
        ".............KK.",
        ".............KK.",
        "............KK..",
        "..........KKK...",
        "....KKKKKKKK....",
        "................",
    ],
    "redo": None,  # espelho do undo
    "group": [
        "................",
        ".A.AA.AA.AA.AA..",
        "................",
        ".A............A.",
        ".A..KKKK......A.",
        "....KPPK.......",
        ".A..KPPKKKKK..A.",
        ".A..KKKKPPPK..A.",
        ".......KPPPK....",
        ".A.....KKKKK..A.",
        ".A............A.",
        "................",
        "..AA.AA.AA.AA.A.",
        "................",
    ],
    "ungroup": [
        "................",
        "................",
        "..KKKKK.........",
        "..KPPPK.........",
        "..KPPPK.........",
        "..KKKKK.........",
        "......S.........",
        ".......S........",
        "........S.......",
        ".........KKKKK..",
        ".........KPPPK..",
        ".........KPPPK..",
        ".........KKKKK..",
        "................",
    ],
    "duplicate": [
        "................",
        "................",
        "..KKKKKKK.......",
        "..KPPPPPK.......",
        "..KPPPPPK.......",
        "..KPPKKKKKKKK...",
        "..KPPKPPPPPPK...",
        "..KPPKPPAAPPK...",
        "..KKKKPAAAAPK...",
        ".....KPPAAPPK...",
        ".....KPPPPPPK...",
        ".....KKKKKKKK...",
        "................",
    ],
    "remove": [
        "................",
        "................",
        "......KKKK......",
        "..KKKKKKKKKKKK..",
        "................",
        "...KAAAAAAAAK...",
        "...KALAAAAAAK...",
        "...KALAKAKAAK...",
        "...KALAKAKAAK...",
        "...KAAAKAKAAK...",
        "...KAAAAAAADK...",
        "....KKKKKKKK....",
        "................",
    ],
    "save": [
        "................",
        "................",
        "..KKKKKKKKKKK...",
        "..KAPPPPPPPAKK..",
        "..KAPPPPPPPAAK..",
        "..KAPPPPPPPAAK..",
        "..KAAAAAAAAAAK..",
        "..KAAKKKKKKAAK..",
        "..KAAKPPPPKAAK..",
        "..KAAKPPKPKAAK..",
        "..KAAKPPKPKAAK..",
        "..KKKKKKKKKKKK..",
        "................",
    ],
    "save_component": [
        "................",
        "..........A.....",
        "..KKKKKK..A.....",
        "..KPPPPK.AAA....",
        "..KPPPPKAAAAA...",
        "..KPPKKKKKAKK...",
        "..KPPKAAAAAAK...",
        "..KKKKALAAAAK...",
        ".....KAAAAAAK...",
        ".....KAAAAADK...",
        ".....KKKKKKKK...",
        "................",
    ],
    "fit": [
        "................",
        "................",
        "..KKKK....KKKK..",
        "..KKKK....KKKK..",
        "..KK........KK..",
        "..KK..SSSS..KK..",
        "......SAAS......",
        "......SAAS......",
        "..KK..SSSS..KK..",
        "..KK........KK..",
        "..KKKK....KKKK..",
        "..KKKK....KKKK..",
        "................",
    ],
    "zoom_in": [
        "................",
        "................",
        "....KKKKK.......",
        "...KPPPPPK......",
        "..KPPPAPPPK.....",
        "..KPPPAPPPK.....",
        "..KPAAAAAPK.....",
        "..KPPPAPPPK.....",
        "..KPPPAPPPK.....",
        "...KPPPPPKK.....",
        "....KKKKKKKK....",
        "..........KKK...",
        "...........KKK..",
        "............KK..",
    ],
    "zoom_out": [
        "................",
        "................",
        "....KKKKK.......",
        "...KPPPPPK......",
        "..KPPPPPPPK.....",
        "..KPPPPPPPK.....",
        "..KPAAAAAPK.....",
        "..KPPPPPPPK.....",
        "..KPPPPPPPK.....",
        "...KPPPPPKK.....",
        "....KKKKKKKK....",
        "..........KKK...",
        "...........KKK..",
        "............KK..",
    ],
    # ---- camadas ----
    "eye": [
        "................",
        "................",
        "................",
        ".....KKKKKK.....",
        "...KKPPPPPPKK...",
        "..KPPPPKKPPPPK..",
        ".KPPPPKAAKPPPPK.",
        ".KPPPPKALKPPPPK.",
        "..KPPPPKKPPPPK..",
        "...KKPPPPPPKK...",
        ".....KKKKKK.....",
        "................",
    ],
    "eye_closed": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        ".K............K.",
        "..KK........KK..",
        "...SKKKKKKKKS...",
        "..S..S.SS.S..S..",
        ".....S....S.....",
        "................",
    ],
    "lock": [
        "................",
        "................",
        ".....KKKKKK.....",
        "....KK....KK....",
        "....K......K....",
        "....K......K....",
        "...KKKKKKKKKK...",
        "...KAAAAAAAAK...",
        "...KALAKKAAAK...",
        "...KAAAKKAAAK...",
        "...KAAAAAAAAK...",
        "...KAAAAAAADK...",
        "...KKKKKKKKKK...",
        "................",
    ],
    # ---- estilo ----
    "align_left": [
        "................",
        "................",
        "..KKKKKKKKKKKK..",
        "................",
        "..KKKKKKKK......",
        "................",
        "..KKKKKKKKKKKK..",
        "................",
        "..KKKKKK........",
        "................",
        "..KKKKKKKKKK....",
        "................",
    ],
    "align_center": [
        "................",
        "................",
        "..KKKKKKKKKKKK..",
        "................",
        "....KKKKKKKK....",
        "................",
        "..KKKKKKKKKKKK..",
        "................",
        ".....KKKKKK.....",
        "................",
        "...KKKKKKKKKK...",
        "................",
    ],
    "align_right": None,  # espelho do align_left
    "eyedropper": [
        "................",
        "...........KK...",
        "..........KAAK..",
        ".........KAAAK..",
        "........KKAAK...",
        ".......KPKKK....",
        "......KPPPK.....",
        ".....KPPPK......",
        "....KPPPK.......",
        "...KPPPK........",
        "..KAPPK.........",
        "..KAAK..........",
        ".KAK............",
        ".K..............",
    ],
    "rename": [
        "................",
        "...........KK...",
        "..........KLLK..",
        ".........KAAAK..",
        "........KAAAK...",
        ".......KAAAK....",
        "......KAAAK.....",
        ".....KAAAK......",
        "....KAAAK.......",
        "....KPAK........",
        "...KPPK.........",
        "...KKK..........",
        "................",
        "..KKKKKKKKKKKK..",
    ],
    "section_open": [
        "................",
        "................",
        "................",
        "................",
        "...KKKKKKKKK....",
        "....KKKKKKK.....",
        ".....KKKKK......",
        "......KKK.......",
        ".......K........",
        "................",
    ],
    "section_closed": [
        "................",
        "................",
        "....K...........",
        "....KK..........",
        "....KKK.........",
        "....KKKK........",
        "....KKKKK.......",
        "....KKKK........",
        "....KKK.........",
        "....KK..........",
        "....K...........",
        "................",
    ],
    "add": [
        "................",
        "................",
        "......KKKK......",
        "......KAAK......",
        "......KAAK......",
        "..KKKKKAAKKKKK..",
        "..KAAAAAAAAAAK..",
        "..KAAAAAAAAAAK..",
        "..KKKKKAAKKKKK..",
        "......KAAK......",
        "......KAAK......",
        "......KKKK......",
        "................",
    ],
    "rules": [
        "................",
        "................",
        "..KKK...........",
        "..KAK..KKKKKKK..",
        "..KKK...........",
        "................",
        "..KKK...........",
        "..KAK..KKKKK....",
        "..KKK...........",
        "................",
        "..KKK...........",
        "..KAK..KKKKKKK..",
        "..KKK...........",
        "................",
    ],
}


def _espelho(grade: list[str]) -> list[str]:
    return [linha.ljust(16, ".")[::-1] for linha in grade]


ICONES["redo"] = _espelho(ICONES["undo"])
ICONES["align_right"] = _espelho(ICONES["align_left"])


def _centra(grade: list[str]) -> list[str]:
    """Põe a arte no meio do quadro na vertical (as grades são escritas de cima)."""
    linhas = [l.ljust(16, ".") for l in grade]
    cheias = [i for i, l in enumerate(linhas) if l.strip(".")]
    topo, base = cheias[0], cheias[-1]
    altura = base - topo + 1
    inicio = (16 - altura) // 2
    return ["." * 16] * inicio + linhas[topo:base + 1] + ["." * 16] * (16 - inicio - altura)


def icone(nome: str, tema: str) -> Image.Image:
    leg = {ch: cor(ref) for ch, ref in TEMAS[tema].items()}
    return pinta(_centra(ICONES[nome]), leg)


def prancha(imagens: dict[str, Image.Image]) -> Image.Image:
    nomes = list(ICONES)
    fundos = [("claro", cor("porcelana.3"), ""), ("escuro", cor("grafite.2"), "_dark")]
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
        for _, fundo, sufixo in fundos:
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
            img = icone(nome, tema)
            assert img.size == (16, 16)
            assert set(img.getchannel("A").tobytes()) <= {0, 255}, f"{nome}{tema}: alfa parcial"
            img.save(DESTINO / f"{nome}{tema}.png")
            imagens[nome + tema] = img
    prancha(imagens).save(PREVIEW / "tela-icones.png")
    print(f"{len(ICONES)} ícones × {len(TEMAS)} temas em {DESTINO}; prancha em {PREVIEW / 'tela-icones.png'}")


if __name__ == "__main__":
    main()
