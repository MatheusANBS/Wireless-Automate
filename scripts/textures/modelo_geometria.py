"""A geometria dos modelos de bloco: a regra que nenhuma face coplanar se sobrepõe, nenhuma caixa se atravessa e
nenhuma face some sem vizinho que a cubra (`validar_geometria`), e os cantos das faces e a rotação por elemento
que o render das prévias usa também.

O `blocos.py` (a classe `Modelo`) é só tipo aqui: nada deste módulo importa o `blocos.py` em tempo de execução.
"""

from __future__ import annotations

import math
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from blocos import Modelo, Vec


class ModeloInvalido(Exception):
    """Um modelo (ou a escultura de onde ele sai) violou uma regra de geometria: o gerador pára."""


# Eixo normal de cada face e qual canto da caixa fixa a coordenada do plano.
FACE_EIXO = {"west": (0, "from"), "east": (0, "to"), "down": (1, "from"), "up": (1, "to"),
             "north": (2, "from"), "south": (2, "to")}
EPS = 1e-6


def _chave_rot(el: dict) -> tuple | None:
    r = el.get("rotation")
    return (r["axis"], float(r["angle"]), tuple(r["origin"])) if r else None


def _face_plana(el: dict, face: str, local: bool) -> tuple[int, float, list[tuple[float, float]]] | None:
    """(eixo, coordenada do plano, polígono 2D) da face: no referencial do próprio elemento (`local`) ou do
    bloco, quando a face continua num plano alinhado a um eixo depois da rotação (senão None)."""
    eixo, lado = FACE_EIXO[face]
    cantos = cantos_da_face(face, el["from"], el["to"])
    rot = el.get("rotation")
    if rot and not local:
        if rot["axis"] != "xyz"[eixo]:
            return None
        cantos = [girar_ponto(p, rot) for p in cantos]
    coord = cantos[0][eixo]
    outros = [i for i in range(3) if i != eixo]
    return eixo, coord, [(p[outros[0]], p[outros[1]]) for p in cantos]


def _area(poly: list[tuple[float, float]]) -> float:
    return sum(poly[i][0] * poly[(i + 1) % len(poly)][1] - poly[(i + 1) % len(poly)][0] * poly[i][1]
               for i in range(len(poly))) / 2


def _no_poligono(p: tuple[float, float], poly: list[tuple[float, float]], folga: float) -> bool:
    """Ponto dentro do polígono convexo; `folga` > 0 aceita a borda, < 0 exige estar bem dentro."""
    sinal = 1 if _area(poly) > 0 else -1
    for i in range(len(poly)):
        (ax, ay), (bx, by) = poly[i], poly[(i + 1) % len(poly)]
        comp = math.hypot(bx - ax, by - ay) or 1
        if sinal * ((bx - ax) * (p[1] - ay) - (by - ay) * (p[0] - ax)) / comp < -folga:
            return False
    return True


def _amostras(poly: list[tuple[float, float]], passo: float) -> list[tuple[float, float]]:
    """Centros de uma grade de `passo` px que caem bem dentro do polígono."""
    xs, ys = [p[0] for p in poly], [p[1] for p in poly]
    pontos = []
    x = math.floor(min(xs) / passo) * passo + passo / 2
    while x < max(xs):
        y = math.floor(min(ys) / passo) * passo + passo / 2
        while y < max(ys):
            if _no_poligono((x, y), poly, -EPS):
                pontos.append((x, y))
            y += passo
        x += passo
    return pontos


def _sobrepoe(a: list[tuple[float, float]], b: list[tuple[float, float]]) -> bool:
    """Os dois polígonos coplanares têm área em comum (algum ponto bem dentro dos dois)."""
    return any(_no_poligono(p, b, -1e-3) for p in _amostras(a, 0.25))


def _coberta(face: list[tuple[float, float]], cobertas: list[list[tuple[float, float]]]) -> bool:
    return all(any(_no_poligono(p, c, EPS) for c in cobertas) for p in _amostras(face, 0.5))


def _volume_comum(a: dict, b: dict) -> float:
    v = 1.0
    for i in range(3):
        v *= max(0.0, min(a["to"][i], b["to"][i]) - max(a["from"][i], b["from"][i]))
    return v


def _erros_do_grupo(m: Modelo, indices: list[int], local: bool) -> list[str]:
    """As três checagens entre os elementos `indices`, num referencial comum: o do bloco (só as faces que
    ficam alinhadas aos eixos) ou o local de um grupo com a mesma rotação."""
    erros: list[str] = []
    planas: dict[int, dict[str, tuple[int, float, list]]] = {}
    for i in indices:
        planas[i] = {}
        for face in FACE_EIXO:
            fp = _face_plana(m.elementos[i], face, local)
            if fp and abs(_area(fp[2])) > EPS:
                planas[i][face] = fp
    for n, i in enumerate(indices):
        a = m.elementos[i]
        for j in indices[n + 1:]:
            b = m.elementos[j]
            if (local or not a.get("rotation") and not b.get("rotation")) and not (m.dentro[i] or m.dentro[j]):
                if _volume_comum(a, b) > EPS:
                    erros.append(f"elementos {i} e {j} se atravessam: {a['from']}-{a['to']} × {b['from']}-{b['to']}")
            for fa, (ea, ca, pa) in planas[i].items():
                if fa not in a["faces"]:
                    continue
                for fb, (eb, cb, pb) in planas[j].items():
                    if fb in b["faces"] and ea == eb and abs(ca - cb) < EPS and _sobrepoe(pa, pb):
                        erros.append(f"faces coplanares que piscam: {i}.{fa} e {j}.{fb} no plano {'xyz'[ea]}={ca}")
    return erros


def _faces_cobertas(m: Modelo, indices: list[int], local: bool) -> set[tuple[int, str]]:
    """As faces não desenhadas dos elementos `indices` que algum vizinho encostado cobre inteiras."""
    cobertas: set[tuple[int, str]] = set()
    for i in indices:
        for face in FACE_EIXO:
            if face in m.elementos[i]["faces"]:
                continue
            fp = _face_plana(m.elementos[i], face, local)
            if not fp or abs(_area(fp[2])) <= EPS:
                continue
            eixo, coord, poly = fp
            vizinhas = []
            for j in indices:
                if j == i:
                    continue
                for f2 in FACE_EIXO:
                    fq = _face_plana(m.elementos[j], f2, local)
                    if fq and fq[0] == eixo and abs(fq[1] - coord) < EPS:
                        vizinhas.append(fq[2])
            if _coberta(poly, vizinhas):
                cobertas.add((i, face))
    return cobertas


def validar_geometria(m: Modelo) -> None:
    """Nenhum par de faces coplanares desenhadas com área em comum, nenhum par de caixas que se atravessa
    (salvo `dentro`) e nenhuma face omitida sem um vizinho encostado que a cubra (buraco)."""
    erros: list[str] = []
    grupos: dict[tuple | None, list[int]] = {}
    for i, el in enumerate(m.elementos):
        grupos.setdefault(_chave_rot(el), []).append(i)
    todos_idx = list(range(len(m.elementos)))
    erros += _erros_do_grupo(m, todos_idx, local=False)
    cobertas = _faces_cobertas(m, todos_idx, local=False)
    for chave, indices in grupos.items():
        if chave is not None:
            erros += _erros_do_grupo(m, indices, local=True)
            cobertas |= _faces_cobertas(m, indices, local=True)
    for i, el in enumerate(m.elementos):
        for face in FACE_EIXO:
            if face in el["faces"] or (i, face) in cobertas:
                continue
            de, ate = el["from"], el["to"]
            eixo = FACE_EIXO[face][0]
            if any(ate[k] - de[k] <= EPS for k in range(3) if k != eixo):
                continue  # face sem área
            rot = el.get("rotation")
            if (m.fatia and (not rot or rot["axis"] == "xyz"[eixo])
                    and el[FACE_EIXO[face][1]][eixo] in (0, 16)):
                continue  # inteira no limite do bloco: a fatia vizinha a cobre
            erros.append(f"buraco: elemento {i} ({de}-{ate}) sem a face {face} e sem vizinho que a cubra")
    if erros:
        raise AssertionError(f"{m.nome}:\n  " + "\n  ".join(erros))


def girar_ponto(p: Vec, rot: dict | None) -> Vec:
    if not rot:
        return list(p)
    ox, oy, oz = rot["origin"]
    a = math.radians(rot["angle"])
    c, s = math.cos(a), math.sin(a)
    x, y, z = p[0] - ox, p[1] - oy, p[2] - oz
    if rot["axis"] == "x":
        y, z = y * c - z * s, y * s + z * c
    elif rot["axis"] == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return [x + ox, y + oy, z + oz]


def cantos_da_face(face: str, de: Vec, ate: Vec) -> list[Vec]:
    """Os quatro cantos da face na ordem (u0,v0), (u1,v0), (u1,v1), (u0,v1)."""
    (x0, y0, z0), (x1, y1, z1) = de, ate
    return {
        "south": [[x0, y1, z1], [x1, y1, z1], [x1, y0, z1], [x0, y0, z1]],
        "north": [[x1, y1, z0], [x0, y1, z0], [x0, y0, z0], [x1, y0, z0]],
        "east": [[x1, y1, z1], [x1, y1, z0], [x1, y0, z0], [x1, y0, z1]],
        "west": [[x0, y1, z0], [x0, y1, z1], [x0, y0, z1], [x0, y0, z0]],
        "up": [[x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1]],
        "down": [[x0, y0, z1], [x1, y0, z1], [x1, y0, z0], [x0, y0, z0]],
    }[face]


def envolvente(el: dict) -> list[float]:
    """Caixa envolvente do elemento no referencial do bloco (os oito cantos, girados pela `rotation`)."""
    pts = [girar_ponto(p, el.get("rotation"))
           for face in ("up", "down") for p in cantos_da_face(face, el["from"], el["to"])]
    return [min(p[i] for p in pts) for i in range(3)] + [max(p[i] for p in pts) for i in range(3)]
