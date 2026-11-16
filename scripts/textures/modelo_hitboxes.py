"""As hitboxes dos blocos (`HITBOXES`), a conferência de que cobrem o desenho do modelo e o
`docs/preview/hitboxes.txt`.

Nada aqui importa o `blocos.py` em tempo de execução; o `Modelo` entra só como tipo.
"""

from __future__ import annotations

from pathlib import Path
from typing import TYPE_CHECKING

from identidade import PREVIEW
from modelo_geometria import EPS, envolvente

if TYPE_CHECKING:
    from blocos import Modelo


# Caixas de colisão e seleção de cada bloco, em pixels (x0, y0, z0, x1, y1, z1), as mesmas de
# `RouterShapes.UP_BOXES` (roteador com facing=up e spin 0) e de `storage/StorageShapes.java`: mude aqui e lá.
# Poucas caixas que abraçam o desenho: `validar_hitboxes` confere que todo elemento do modelo (a parabólica
# pela caixa envolvente do prato rotacionado) cabe na união delas, e `gravar_hitboxes` escreve em
# docs/preview/hitboxes.txt a caixa envolvente de cada elemento ao lado destas caixas.
HITBOXES: dict[str, list[list[float]]] = {
    "router": [
        [1, 0, 2, 15, 3, 14],          # prato da base com os quatro para-choques
        [3, 1, 4, 13, 5, 12],          # corpo
        [2, 3, 2.5, 3, 12, 3.5],       # haste de trás, oeste (com a ponta de latão)
        [13, 3, 2.5, 14, 12, 3.5],     # haste de trás, leste
        [7, 5, 7, 9, 9, 9],            # mastro
        [5, 9, 2.75, 11, 14.5, 8.25],  # parabólica inclinada, braço e receptor (envolvente do prato rotacionado)
    ],
    "storage_chest": [
        [1, 0, 1, 4, 2, 4], [12, 0, 1, 15, 2, 4], [1, 0, 12, 4, 2, 15], [12, 0, 12, 15, 2, 15],  # pés
        [1, 2, 1, 15, 14, 15],         # corpo recuado
        [0, 14, 0, 16, 16, 16],        # tampa
        [2, 2, 15, 14, 13, 16], [2, 2, 0, 14, 13, 1], [15, 2, 2, 16, 13, 14], [0, 2, 2, 1, 13, 14],  # gavetas
    ],
    "storage_tank": [
        [2, 0, 2, 14, 4, 14],          # pés e base
        [3, 4, 3, 13, 14, 13],         # anéis, coluna de vidro e réguas
        [2, 14, 2, 14, 16, 14],        # tampa
    ],
    "storage_battery": [
        [1, 0, 1, 15, 2, 15],          # pés e plinto
        [2, 2, 2, 14, 13, 14],         # células e separadores
        [6, 2, 14, 10, 13, 15], [6, 2, 1, 10, 13, 2], [14, 2, 6, 15, 13, 10], [1, 2, 6, 2, 13, 10],  # visores
        [1, 13, 1, 15, 14, 15],        # cornija
        [2, 14, 2, 14, 15, 14],        # tampa
        [3, 15, 3, 5, 16, 5], [11, 15, 11, 13, 16, 13],  # terminais
    ],
    "storage_chemical_tank": [
        [3, 0, 3, 13, 2, 13],          # pés e anel
        [4, 2, 4, 12, 3, 12],          # pescoço de baixo
        [3, 3, 3, 13, 5, 13],          # ombro com a faixa de perigo
        [2, 5, 2, 14, 11, 14],         # equador com a vigia
        [3, 11, 3, 13, 13, 13],        # ombro de cima
        [4, 13, 4, 12, 15, 12],        # calota e volante
    ],
    "storage_source_tank": [
        [2, 0, 2, 14, 1, 14],          # para-choques dos cantos
        [3, 0, 3, 13, 13, 13],         # base, trilhos, coluna de vidro e tampa
        [6, 13, 6, 10, 14, 10],        # colar
        [6.5, 14, 6.5, 9.5, 16, 9.5],  # gema
    ],
}


def _hitboxes_de(nome: str) -> list[list[float]]:
    for base, caixas in HITBOXES.items():
        if nome == base or nome.startswith(base + "_"):
            return caixas
    raise KeyError(nome)


def validar_hitboxes(m: Modelo) -> None:
    """Cada caixa dentro de 0..16 e todo elemento do modelo dentro da união das caixas (pelos cantos da
    envolvente); e nenhuma caixa à toa: toda caixa toca algum elemento."""
    caixas = _hitboxes_de(m.nome)
    for c in caixas:
        assert all(0 <= v <= 16 for v in c) and all(c[i] < c[i + 3] for i in range(3)), (m.nome, c)
    for n, el in enumerate(m.elementos):
        env = envolvente(el)
        for canto in ((env[a], env[b], env[c]) for a in (0, 3) for b in (1, 4) for c in (2, 5)):
            assert any(all(cx[i] - EPS <= canto[i] <= cx[i + 3] + EPS for i in range(3)) for cx in caixas), \
                f"{m.nome}: elemento {n} ({el['from']}-{el['to']}) fora das hitboxes em {canto}"
    for c in caixas:
        toca = any(all(c[i] < envolvente(el)[i + 3] + EPS and envolvente(el)[i] < c[i + 3] + EPS for i in range(3))
                   for el in m.elementos)
        assert toca, f"{m.nome}: hitbox {c} não toca nenhum elemento"


def gravar_hitboxes(ms: dict[str, Modelo], destino: Path | None = None) -> Path:
    """`docs/preview/hitboxes.txt`: por bloco (tier Básico; a geometria não muda com o tier), a envolvente
    de cada elemento, a envolvente do bloco inteiro e as caixas de `HITBOXES`."""
    destino = destino or PREVIEW / "hitboxes.txt"
    linhas = ["# Gerado por scripts/textures/blocos.py (gerar_texturas.py). Pixels: x0 y0 z0  x1 y1 z1.",
              "# Hitboxes: as mesmas de RouterShapes.UP_BOXES (roteador, facing=up, spin 0) e de "
              "storage/StorageShapes.java.", ""]

    def fmt(c: list[float]) -> str:
        return "  ".join(" ".join(f"{v:5.2f}" for v in c[i:i + 3]) for i in (0, 3))

    for nome in ["router_basic", "storage_chest_basic", "storage_tank_basic", "storage_battery_basic",
                 "storage_chemical_tank_basic", "storage_source_tank_basic_10"]:
        m = ms[nome]
        envs = [envolvente(el) for el in m.elementos]
        total = [min(e[i] for e in envs) for i in range(3)] + [max(e[i + 3] for e in envs) for i in range(3)]
        linhas.append(f"== {nome}")
        linhas.append(f"envolvente do bloco: {fmt(total)}")
        linhas.append("elementos:")
        for el, env in zip(m.elementos, envs):
            rot = "  (rotacionado)" if el.get("rotation") else ""
            linhas.append(f"  {fmt(env)}{rot}")
        linhas.append("hitboxes:")
        for c in _hitboxes_de(nome):
            linhas.append(f"  {fmt([float(v) for v in c])}")
        linhas.append("")
    destino.parent.mkdir(parents=True, exist_ok=True)
    destino.write_text("\n".join(linhas), encoding="utf-8", newline="\n")
    return destino
