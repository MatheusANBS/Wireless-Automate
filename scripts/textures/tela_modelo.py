"""O modelo 3D da Tela de Sinal, gerado do `.bbmodel` aprovado (`docs/preview/tela-de-sinal.bbmodel`).

O `gerar_texturas.py` chama `gerar(raiz_assets)` depois do `blocos.modelos()`, como o do Reparador, e grava em
subpastas: `models/block/signal_screen/` e `textures/block/signal_screen/signal_screen.png` (o `limpar` do
`gerar_texturas.py` varre as subpastas, por isso `gerar` devolve a chave da textura para ele preservar). A
conversão em si é a comum, do `bbmodel_json.py`; o atlas é de 256 × 256, a 2 px por unidade do modelo (32 px por
bloco), como o do Reparador.

O modelo olha para o norte e encosta na parede ao sul: vai de z 13 (a frente do latão) a z 16 (o fundo). A
abertura da tela é x 2,5..13,5, y 3,5..13,5. Peças, pelos pontos cardeais (quem olha a tela de frente tem o
leste à esquerda):
- a moldura de porcelana em degraus: a beira (`lip_*`, z 14..15), a crista (`ridge_*`, z 13,5..15) e, embaixo,
  o queixo no plano da crista (`chin`, y 0,5..2,5);
- o filete coral contínuo (`coral_*`, z 14..15), rebaixado 1 px da crista, e a moldura fina de grafite em volta do
  vidro (`inner_*`, z 14,5..15);
- as cantoneiras de latão em L (`brass_*_h` e `_v`, z 13..14), com o rebite na quina;
- no queixo: o Olho num medalhão de latão (`emblem`), dois botões (`button_*`) e o LED (`led_glow`, coral, com a
  luz cheia pelo `neoforge_data` e sem sombra, no próprio modelo sólido: sem estado, sempre aceso), todos em
  z 13..13,5; a grade do alto-falante é pintada;
- o corpo (`screen`, x e y 0,5..15,5, z 15..16): a face norte é a frente apagada da tela (grafite `#0f1117` com
  vinheta), os lados são a ranhura recuada 0,5 atrás da beira e o sul é o fundo (aletas, rasgos e o Olho);
- as braçadeiras de latão (`bracket_*`) na ranhura dos lados, da beira até a parede;
- o vidro (`glass`, só a face da frente, em z 14,7), translúcido, num modelo à parte, com o reflexo diagonal.

As faces que ficam na borda do bloco levam o `cullface` do lado: o fundo some contra a parede. As faces desligadas
no Blockbench (os lados do vidro) ficam de fora.

O blockstate gira o modelo pelo `facing` (`y` 0, 90, 180 e 270 para norte, leste, sul e oeste) e junta o sólido e
o vidro por `multipart`, só com `expanded=false`. Com `expanded=true` (a Tela maior que 1 × 1, em que o bloco é só
uma célula e o BER desenha a moldura e a imagem na área inteira) nenhuma parte se aplica: o bloco não desenha
nada, para as cantoneiras e o queixo não aparecerem no meio da tela grande. A colisão é do Java. O item junta as
duas partes pelo `neoforge:composite`, com a transformação raiz que traz a Tela para o meio do bloco e o `gui` de
frente.

Uso isolado (na raiz): `python scripts/textures/tela_modelo.py`.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import bbmodel_json as bb  # noqa: E402
from identidade import ASSETS, MODELOS, RAIZ, grava_json  # noqa: E402

NS = "wirelessautomate"
BBMODEL = RAIZ / "docs/preview/tela-de-sinal.bbmodel"
PASTA = "signal_screen"
SOLIDO = "signal_screen"
VIDRO = "signal_screen_glass"
TEXTURA = f"block/{PASTA}/signal_screen"
ELEMENTO_VIDRO = "glass"
ELEMENTOS_ESPERADOS = 32
ATLAS = 256
CHAVE = "#screen"
ModeloInvalido = bb.ModeloInvalido

# As fatias da moldura para o BER da Tela maior que 1 × 1 (Tarefa 7, `client/tv/render/TvFrame.java`, que copia
# estes números e os confere no `TvFrameTest`): retângulos opacos do atlas, em pixels, (x, y, largura, altura),
# pintados no `.bbmodel` como a vista de frente, achatada, da Tela 1 × 1, a 2 px por unidade. As faixas da moldura
# têm 2,5 unidades (5 px) em cima e dos lados e 3,5 (7 px) embaixo, onde fica o queixo:
# - `corner_*`: os quatro cantos, de cima (5 × 5 px) e de baixo (5 × 7 px), à esquerda e à direita de quem olha,
#   com a cantoneira de latão e o rebite;
# - `edge_top`, `edge_left`, `edge_right`: as faixas entre os cantos (porcelana, filete coral e grafite), iguais ao
#   longo, repetíveis ou esticáveis no comprimento;
# - `edge_bottom`: o queixo liso, sem enfeite, repetível ao longo;
# - `chin`: o queixo com os enfeites (a grade, o Olho, os botões e o LED, que no BER não brilha sozinho), a borda de
#   baixo inteira da Tela 1 × 1 (11 unidades); desenhado uma vez só, encostado no canto de baixo à direita de quem
#   olha, com o `edge_bottom` no resto;
# - `side`: o lado da Tela sem a braçadeira (de z 14 a 16: a beira de porcelana e a ranhura), coluna 0 na frente e
#   3 na parede, repetível ao longo; serve para os quatro lados;
# - `middle`: a frente apagada da tela (11 × 10 unidades, dentro da moldura), esticável.
# Nenhuma fatia é face do modelo: o modelo pode mudar sem mexer nelas, e o `gerar` confere que são opacas.
FATIAS = {
    "corner_tl": (115, 35, 5, 5),
    "corner_tr": (122, 35, 5, 5),
    "corner_bl": (77, 35, 5, 7),
    "corner_br": (84, 35, 5, 7),
    "edge_top": (91, 35, 22, 5),
    "edge_left": (219, 1, 5, 20),
    "edge_right": (226, 1, 5, 20),
    "edge_bottom": (53, 35, 22, 7),
    "chin": (29, 35, 22, 7),
    "side": (1, 1, 4, 32),
    "middle": (195, 1, 22, 20),
}
# O item: a Tela vai para o meio do bloco (o centro dela está em z 14,5) e o `gui` a mostra quase de frente.
TRANSFORMACAO_ITEM = {"translation": [0, 0, -0.40625]}
GUI_ITEM = {"rotation": [20, 200, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]}
# A face na borda do bloco, pelo lado: o plano dela e o valor da coordenada.
BORDAS = {"north": (2, 0), "south": (2, 16), "west": (0, 0), "east": (0, 16), "down": (1, 0), "up": (1, 16)}


def _sem_sombra(nome: str) -> bool:
    """O LED fica chapado: o jogo não escurece a face dele pela direção."""
    return nome == "led_glow"


def _com_cullface(el: dict) -> dict:
    """Marca o `cullface` das faces que ficam no plano da borda do bloco (o fundo contra a parede, os lados)."""
    for direcao, face in el["faces"].items():
        eixo, valor = BORDAS[direcao]
        ponta = el["to"][eixo] if valor == 16 else el["from"][eixo]
        if ponta == valor:
            face["cullface"] = direcao
    return el


def _modelo(render_type: str, elementos: list[dict]) -> dict:
    return bb.modelo(render_type, elementos, CHAVE, f"{NS}:{TEXTURA}")


def gerar(raiz_assets: Path = MODELOS) -> set[str]:
    """Grava o atlas, os modelos sólido e do vidro, o blockstate e o modelo de item; devolve a chave da textura
    gravada (relativa a `textures/`, como as do `limpar`)."""
    projeto = json.loads(BBMODEL.read_text(encoding="utf-8"))
    atlas = bb.atlas(projeto, ATLAS)
    if len(projeto["elements"]) != ELEMENTOS_ESPERADOS:
        raise ModeloInvalido(f"{len(projeto['elements'])} elementos, esperado {ELEMENTOS_ESPERADOS}")
    elementos = [_com_cullface(bb.converter(el, ATLAS, CHAVE, _sem_sombra, pula_sem_textura=True))
                 for el in projeto["elements"]]
    vidro = [el for el in elementos if el["name"] == ELEMENTO_VIDRO]
    luzes = [el for el in elementos if _sem_sombra(el["name"])]
    solido = [bb.acesa(el) if _sem_sombra(el["name"]) else el for el in elementos if el["name"] != ELEMENTO_VIDRO]
    if not vidro or not luzes:
        raise ModeloInvalido("o .bbmodel precisa do vidro e do LED")
    bb.validar(elementos, CHAVE)
    for el in elementos:
        if not (13 <= el["from"][2] and el["to"][2] <= 16):
            raise ModeloInvalido(f"{el['name']}: fora de z 13..16, a espessura da Tela")
    if any(a != 255 for a in bb.alfas(atlas, [f for el in solido for f in el["faces"].values()])):
        raise ModeloInvalido("o modelo sólido usa um texel com alfa")
    alfas_vidro = bb.alfas(atlas, [f for el in vidro for f in el["faces"].values()])
    if any(a == 255 for a in alfas_vidro) or not any(alfas_vidro):
        raise ModeloInvalido("o vidro precisa de alfa parcial")
    for nome, (x, y, w, h) in FATIAS.items():
        if any(atlas.getpixel((i, j))[3] != 255 for i in range(x, x + w) for j in range(y, y + h)):
            raise ModeloInvalido(f"a fatia {nome} do BER não é opaca no atlas")

    retangulos = [bb.retangulo(face["uv"]) for el in projeto["elements"] for face in el["faces"].values()
                  if face.get("texture") is not None]
    retangulos += [(x, y, x + w, y + h) for x, y, w, h in FATIAS.values()]
    textura = ASSETS / f"{TEXTURA}.png"
    textura.parent.mkdir(parents=True, exist_ok=True)
    bb.com_folga(atlas, retangulos).save(textura)
    modelos = raiz_assets / "models/block" / PASTA
    grava_json(modelos / f"{SOLIDO}.json", _modelo("minecraft:solid", solido))
    grava_json(modelos / f"{VIDRO}.json", _modelo("minecraft:translucent", vidro))
    partes = []
    for facing, y in bb.GIRO_FACING.items():
        for nome in (SOLIDO, VIDRO):
            aplica: dict = {"model": f"{NS}:block/{PASTA}/{nome}"}
            if y:
                aplica["y"] = y
            partes.append({"when": {"facing": facing, "expanded": "false"}, "apply": aplica})
    grava_json(raiz_assets / f"blockstates/{SOLIDO}.json", {"multipart": partes})
    # O item junta as partes pelo modelo composto do NeoForge (o sólido, com o LED aceso, antes do vidro); o
    # `display` que não está aqui vem do `block/block`.
    grava_json(raiz_assets / f"models/item/{SOLIDO}.json", {
        "parent": "minecraft:block/block",
        "loader": "neoforge:composite",
        "transform": TRANSFORMACAO_ITEM,
        "textures": {"particle": f"{NS}:{TEXTURA}"},
        "display": {"gui": GUI_ITEM},
        "children": {
            "solido": {"parent": f"{NS}:block/{PASTA}/{SOLIDO}"},
            "vidro": {"parent": f"{NS}:block/{PASTA}/{VIDRO}"},
        },
    })
    return {TEXTURA}


if __name__ == "__main__":
    print("texturas:", sorted(gerar()))
