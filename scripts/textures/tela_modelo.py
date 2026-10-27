"""O modelo 3D da Tela de Sinal, gerado do `.bbmodel` aprovado (`docs/preview/tela-de-sinal.bbmodel`).

O `gerar_texturas.py` chama `gerar(raiz_assets)` depois do `blocos.modelos()`, como o do Reparador, e grava em
subpastas: `models/block/signal_screen/` e `textures/block/signal_screen/signal_screen.png` (o `limpar` do
`gerar_texturas.py` varre as subpastas, por isso `gerar` devolve a chave da textura para ele preservar). A
conversão em si é a comum, do `bbmodel_json.py`; o atlas é de 128 × 128, a 2 px por unidade do modelo (32 px por
bloco, a mesma densidade do Reparador).

O modelo olha para o norte e encosta na parede ao sul: vai de z 14 (a frente das cantoneiras) a z 16 (o fundo).
Peças, pelos pontos cardeais (quem olha a tela de frente tem o leste à esquerda):
- as cantoneiras 3 × 3 (`cap_*`, z 14 a 15), salientes 1 px sobre as barras, com o rebite pintado; a de baixo à
  direita de quem olha (`cap_led_*`, quatro peças em volta do furo) guarda o LED;
- o LED (`led_glow`, 1 × 1, só a face da frente, em z 14,5, no fundo do furo): coral, com a luz cheia pelo
  `neoforge_data` e sem sombra, no próprio modelo sólido (sem estado: está sempre aceso);
- as barras da moldura (`bar_*`, 1 de largura, z 14,5 a 15), com o chanfro pintado em dois tons;
- a frente apagada da tela (`screen`, a face norte em z 15, grafite `#0f1117` com vinheta leve), recuada 0,5 em
  volta, o que abre a ranhura coral escura dos lados;
- o fundo (`housing`, z 15,5 a 16), com as aletas, o Olho e os rasgos de pendurar pintados atrás;
- o vidro (`glass`, só a face da frente, em z 14,875), translúcido, num modelo à parte, com o reflexo diagonal.

As faces que ficam na borda do bloco levam o `cullface` do lado: o fundo some contra a parede. As faces desligadas
no Blockbench (os lados do LED e do vidro) ficam de fora. O blockstate gira o modelo pelo `facing` (`y` 0, 90, 180
e 270 para norte, leste, sul e oeste) e junta o sólido e o vidro por `multipart`; o item junta os dois pelo
`neoforge:composite`, com a transformação raiz que traz a Tela para o meio do bloco e o `gui` de frente.

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
ELEMENTOS_ESPERADOS = 15
ATLAS = 128
CHAVE = "#screen"
ModeloInvalido = bb.ModeloInvalido

# As fatias da moldura para o BER da Tela maior que 1 × 1 (Tarefa 7, `client/tv/render/TvFrame.java`, que copia
# estes números e os confere no `TvFrameTest`): retângulos opacos do atlas, em pixels, (x, y, largura, altura),
# pintados no `.bbmodel` como a vista de frente, achatada, da Tela 1 × 1, a 2 px por unidade:
# - `corner_*`: as quatro cantoneiras (3 × 3 unidades), de cima e de baixo, à esquerda e à direita de quem olha;
#   a de baixo à direita traz o LED pintado (no BER ele não brilha sozinho);
# - `edge_*`: as barras entre as cantoneiras (1 unidade de espessura × 10 de comprimento), repetíveis ao longo;
# - `side`: o lado da Tela (2 unidades de fundo × 16 de comprimento), coluna 0 na frente e 3 na parede, repetível
#   ao longo; serve para os quatro lados;
# - `middle`: a frente apagada da tela (14 × 14 unidades, o miolo dentro das barras), esticável.
# Nenhuma fatia é face do modelo: o modelo pode mudar sem mexer nelas, e o `gerar` confere que são opacas.
FATIAS = {
    "corner_tl": (71, 67, 6, 6),
    "corner_tr": (79, 67, 6, 6),
    "corner_bl": (55, 67, 6, 6),
    "corner_br": (63, 67, 6, 6),
    "edge_top": (1, 93, 20, 2),
    "edge_bottom": (89, 89, 20, 2),
    "edge_left": (115, 35, 2, 20),
    "edge_right": (119, 35, 2, 20),
    "side": (69, 1, 4, 32),
    "middle": (69, 35, 28, 28),
}
# O item: a Tela vai para o meio do bloco (o centro dela está em z 15/16) e o `gui` a mostra quase de frente.
TRANSFORMACAO_ITEM = {"translation": [0, 0, -0.4375]}
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
        if not (14 <= el["from"][2] and el["to"][2] <= 16):
            raise ModeloInvalido(f"{el['name']}: fora de z 14..16, a espessura da Tela")
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
            partes.append({"when": {"facing": facing}, "apply": aplica})
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
