"""A prévia isométrica dos modelos de bloco, desenhada a partir dos próprios elementos (um polígono por texel).

Nada aqui importa o `blocos.py` em tempo de execução; o `Modelo` entra só como tipo.
"""

from __future__ import annotations

import math
from typing import TYPE_CHECKING

from PIL import Image, ImageDraw

from identidade import Cor
from modelo_geometria import cantos_da_face, girar_ponto

if TYPE_CHECKING:
    from blocos import Modelo, Vec


def render(m: Modelo, texturas: dict[str, Image.Image], s: int = 8,
           tela: tuple[int, int, int, int] | None = None) -> Image.Image:
    """Projeção isométrica (câmera em +x, +y, +z), um polígono por texel, do mais fundo ao mais perto. `tela`
    (largura, altura, ox, oy) troca o quadro de um bloco por um maior (a escultura inteira)."""
    W, H, ox, oy = tela or (34 * s, 34 * s, 17 * s, 25 * s)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def proj(p: Vec) -> tuple[float, float]:
        return (ox + (p[0] - p[2]) * s, oy + (p[0] + p[2] - 16) * s / 2 - p[1] * s)

    texels: list[tuple[float, list[tuple[float, float]], Cor]] = []
    for el in m.elementos:
        rot = el.get("rotation")
        for face, f in el["faces"].items():
            cantos = [girar_ponto(p, rot) for p in cantos_da_face(face, el["from"], el["to"])]
            a = [cantos[1][i] - cantos[0][i] for i in range(3)]
            b = [cantos[3][i] - cantos[0][i] for i in range(3)]
            n = [b[1] * a[2] - b[2] * a[1], b[2] * a[0] - b[0] * a[2], b[0] * a[1] - b[1] * a[0]]
            norma = math.sqrt(sum(v * v for v in n)) or 1
            n = [v / norma for v in n]
            if n[0] + n[1] + n[2] <= 1e-6:
                continue
            sombra = max(0.35, min(1.0, 0.6 + 0.4 * n[1] + 0.24 * n[2] + 0.06 * n[0]))
            tex = texturas[f["texture"][1:]]
            u0, v0, u1, v1 = f["uv"]
            nu, nv = max(1, round(abs(u1 - u0))), max(1, round(abs(v1 - v0)))
            for i in range(nu):
                for j in range(nv):
                    tu = min(15, max(0, int(u0 + (u1 - u0) * (i + 0.5) / nu)))
                    tv = min(15, max(0, int(v0 + (v1 - v0) * (j + 0.5) / nv)))
                    c = tex.getpixel((tu, tv))
                    if c[3] == 0:
                        continue
                    pts = []
                    for (ia, ja) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                        fa, fb = ia / nu, ja / nv
                        p = [cantos[0][k] + a[k] * fa + b[k] * fb for k in range(3)]
                        pts.append(p)
                    centro = [sum(p[k] for p in pts) / 4 for k in range(3)]
                    prof = centro[0] + centro[1] + centro[2] + 0.001 * n[1]
                    cc = (int(c[0] * sombra), int(c[1] * sombra), int(c[2] * sombra), c[3])
                    texels.append((prof, [proj(p) for p in pts], cc))
    texels.sort(key=lambda t: t[0])
    for _, pts, c in texels:
        if c[3] == 255:
            d.polygon(pts, fill=c)
        else:
            xs, ys = [p[0] for p in pts], [p[1] for p in pts]
            bx0, by0 = int(min(xs)), int(min(ys))
            bx1, by1 = int(max(xs)) + 2, int(max(ys)) + 2
            camada = Image.new("RGBA", (bx1 - bx0, by1 - by0), (0, 0, 0, 0))
            ImageDraw.Draw(camada).polygon([(x - bx0, y - by0) for x, y in pts], fill=c)
            img.alpha_composite(camada, (bx0, by0))
    return img
