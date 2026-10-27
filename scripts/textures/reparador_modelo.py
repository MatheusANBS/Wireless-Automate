"""O modelo 3D do Reparador de Cristais, gerado do `.bbmodel` aprovado (`docs/preview/reparador-de-cristais.bbmodel`).

O `gerar_texturas.py` chama `gerar(raiz_assets)` depois do `blocos.modelos()`. Não usa a classe `Modelo` do
`blocos.py`, que proíbe elementos que se atravessam (o modelo aprovado tem vários), e grava tudo em subpastas:
`models/block/repairer/` (o `blocos.modelos()` só apaga os `models/block/*.json` da raiz) e
`textures/block/repairer/repairer.png` (o `limpar` do `gerar_texturas.py` varre as subpastas, por isso `gerar`
devolve a chave da textura para entrar no conjunto que ele preserva).

A conversão em si (o atlas com folga, o uv, os quartos de volta e a luz cheia das luzes) é a comum, do
`bbmodel_json.py`. A textura é o atlas pintado no Blockbench (256 × 256, embutido no `.bbmodel`).

O vidro (o elemento `glass`) vai num modelo à parte, translúcido, e as luzes (os elementos do `_sem_sombra`) em
dois modelos à parte, escolhidos pelo `lit` do blockstate: o aceso, com o atlas como foi pintado e a luz cheia, e
o apagado, com a textura `repairer_off`, em que só os retângulos das luzes vão metade para o cinza e a 30% do
brilho. Trocar o `lit` só remonta a seção no cliente: nada por quadro e visível a qualquer distância. O blockstate
junta as partes por `multipart` e o modelo de item (aceso) pelo `neoforge:composite`. Os pedestais e as runas do
anel giram em múltiplos de 22,5 até -405, e o `bbmodel_json.converter` divide esse giro em quartos de volta e resto.

Uso isolado (na raiz): `python scripts/textures/reparador_modelo.py`.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import bbmodel_json as bb  # noqa: E402
from identidade import ASSETS, MODELOS, RAIZ, grava_json  # noqa: E402

NS = "wirelessautomate"
BBMODEL = RAIZ / "docs/preview/reparador-de-cristais.bbmodel"
PASTA = "repairer"
SOLIDO = "crystal_repairer"
VIDRO = "crystal_repairer_glass"
BRILHO = "crystal_repairer_glow"
BRILHO_OFF = "crystal_repairer_glow_off"
TEXTURA = f"block/{PASTA}/repairer"
TEXTURA_OFF = f"block/{PASTA}/repairer_off"
ELEMENTO_VIDRO = "glass"
ELEMENTOS_ESPERADOS = 104
ATLAS = 256
CHAVE = "#repairer"
ModeloInvalido = bb.ModeloInvalido


def _sem_sombra(nome: str) -> bool:
    """Luzes e brilhos ficam chapados: o jogo não escurece a face pela direção. Os brilhos repetidos levam o índice
    depois do `_glow` (`rune_glow_0`, `fin_tip_glow_a`)."""
    return nome.endswith("_glow") or "_glow_" in nome or nome == "led_glow"


def _modelo(render_type: str, elementos: list[dict], textura_chave: str = TEXTURA) -> dict:
    return bb.modelo(render_type, elementos, CHAVE, f"{NS}:{textura_chave}")


def gerar(raiz_assets: Path = MODELOS) -> set[str]:
    """Grava os atlas aceso e apagado, os quatro modelos, o blockstate e o modelo de item; devolve as chaves das
    texturas gravadas
    (relativas a `textures/`, como as do `limpar`)."""
    projeto = json.loads(BBMODEL.read_text(encoding="utf-8"))
    atlas = bb.atlas(projeto, ATLAS)
    elementos = [bb.converter(el, ATLAS, CHAVE, _sem_sombra) for el in projeto["elements"]]
    vidro = [el for el in elementos if el["name"] == ELEMENTO_VIDRO]
    luzes = [el for el in elementos if _sem_sombra(el["name"])]
    solido = [el for el in elementos if el["name"] != ELEMENTO_VIDRO and not _sem_sombra(el["name"])]
    total = len(solido) + len(vidro) + len(luzes)
    if total != ELEMENTOS_ESPERADOS or len(projeto["elements"]) != ELEMENTOS_ESPERADOS:
        raise ModeloInvalido(f"{len(solido)} sólidos + {len(vidro)} de vidro + {len(luzes)} luzes, esperado "
                             f"{ELEMENTOS_ESPERADOS}")
    if not vidro:
        raise ModeloInvalido("o .bbmodel não tem o elemento do vidro")
    if not luzes:
        raise ModeloInvalido("o .bbmodel não tem nenhuma luz")
    bb.validar(elementos, CHAVE)
    if any(a != 255 for a in bb.alfas(atlas, [f for el in solido + luzes for f in el["faces"].values()])):
        raise ModeloInvalido("o modelo sólido ou as luzes usam um texel com alfa")
    alfas_vidro = bb.alfas(atlas, [f for el in vidro for f in el["faces"].values()])
    if any(a == 255 for a in alfas_vidro) or not any(alfas_vidro):
        raise ModeloInvalido("o vidro precisa de alfa parcial em todas as faces")

    retangulos = [bb.retangulo(face["uv"]) for el in projeto["elements"] for face in el["faces"].values()]
    retangulos_luzes = [bb.retangulo(face["uv"]) for el in projeto["elements"] if _sem_sombra(el["name"])
                        for face in el["faces"].values()]
    textura = ASSETS / f"{TEXTURA}.png"
    textura.parent.mkdir(parents=True, exist_ok=True)
    bb.com_folga(atlas, retangulos).save(textura)
    bb.com_folga(bb.apagar(atlas, retangulos_luzes), retangulos).save(ASSETS / f"{TEXTURA_OFF}.png")
    modelos = raiz_assets / "models/block" / PASTA
    grava_json(modelos / f"{SOLIDO}.json", _modelo("minecraft:solid", solido))
    grava_json(modelos / f"{VIDRO}.json", _modelo("minecraft:translucent", vidro))
    grava_json(modelos / f"{BRILHO}.json", _modelo("minecraft:solid", [bb.acesa(el) for el in luzes]))
    grava_json(modelos / f"{BRILHO_OFF}.json",
               _modelo("minecraft:solid", [bb.apagada(el) for el in luzes], TEXTURA_OFF))
    partes = []
    for facing, y in bb.GIRO_FACING.items():
        for nome, lit in ((SOLIDO, None), (VIDRO, None), (BRILHO, "true"), (BRILHO_OFF, "false")):
            aplica: dict = {"model": f"{NS}:block/{PASTA}/{nome}"}
            if y:
                aplica["y"] = y
            quando = {"facing": facing}
            if lit:
                quando["lit"] = lit
            partes.append({"when": quando, "apply": aplica})
    grava_json(raiz_assets / f"blockstates/{SOLIDO}.json", {"multipart": partes})
    # O item junta as partes pelo modelo composto do NeoForge: cada filho desenha com o próprio render_type, na
    # ordem dos filhos (o sólido e as luzes acesas antes do vidro), e o `display` vem do `block/block`.
    grava_json(raiz_assets / f"models/item/{SOLIDO}.json", {
        "parent": "minecraft:block/block",
        "loader": "neoforge:composite",
        "textures": {"particle": f"{NS}:{TEXTURA}"},
        "children": {
            "solido": {"parent": f"{NS}:block/{PASTA}/{SOLIDO}"},
            "brilho": {"parent": f"{NS}:block/{PASTA}/{BRILHO}"},
            "vidro": {"parent": f"{NS}:block/{PASTA}/{VIDRO}"},
        },
    })
    return {TEXTURA, TEXTURA_OFF}


if __name__ == "__main__":
    print("texturas:", sorted(gerar()))
