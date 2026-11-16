"""A escultura da Armilar formada: o conceito A fatiado na grade de blocos, com as conferências de geometria.

Porte do protótipo aprovado (`gerar_conceitos.armilar` e `armilar_formado`), só do que o jogo usa: a base, as portas,
o pedestal com o copo e as quatro pernas. O horizonte e os marcos ficam de fora, porque o render desenha (etapa 2).

Coordenadas do conceito, com a frente para o sul: o centro da estrutura em (0, 0, 0), o chão em y 0, x para leste e
z para o sul. A casa (cx, cy, cz) vai de x = 16·cx − 8 a 16·cx + 8, de y = 16·cy a 16·cy + 16 e de z = 16·cz − 8 a
16·cz + 8. A casa local (lx, lz) do referencial da estrutura é a (lx − 2, lz − 2).

Um `Cubo` é uma caixa (alinhada ou girada em y) com o material e, às vezes, faces especiais (o mostrador, a placa de
sintonia, a escotilha e a face da energia). `escultura()` monta, corta na grade e resolve; `resolver` tira as faces
cobertas por peça encostada e confere o conjunto: zero sobreposição de volume e zero face no mesmo plano.
"""

from __future__ import annotations

import math

import numpy as np

from modelo_geometria import ModeloInvalido  # noqa: F401  (re-exportado: o `armilar_modelo` o importa daqui)

FACES = ("north", "south", "east", "west", "up", "down")
EIXO_FACE = {"north": 2, "south": 2, "east": 0, "west": 0, "up": 1, "down": 1}


def rotm(eixo: str, graus: float) -> np.ndarray:
    t = math.radians(graus)
    cs, sn = math.cos(t), math.sin(t)
    if eixo == "x":
        return np.array([[1, 0, 0], [0, cs, -sn], [0, sn, cs]])
    if eixo == "y":
        return np.array([[cs, 0, sn], [0, 1, 0], [-sn, 0, cs]])
    return np.array([[cs, -sn, 0], [sn, cs, 0], [0, 0, 1]])


class Cubo:
    def __init__(s, a, b, mat, nome, grupo, rot=None, origem=None, especial=None):
        s.a = np.array(a, float)
        s.b = np.array(b, float)
        s.mat, s.nome, s.grupo = mat, nome, grupo
        s.rot = rot  # (eixo, graus) ou None
        s.origem = np.array(origem if origem is not None else (s.a + s.b) / 2, float)
        s.especial = especial or {}
        s.faces = {f: True for f in FACES}
        s.R = rotm(*rot) if rot else np.eye(3)

    def mundo(s, p):
        return (s.R @ (np.asarray(p, float) - s.origem)) + s.origem

    def local(s, Q):
        return ((Q - s.origem) @ s.R) + s.origem

    def cantos(s):
        a, b = s.a, s.b
        return np.array([s.mundo((x, y, z)) for x in (a[0], b[0]) for y in (a[1], b[1]) for z in (a[2], b[2])])

    def caixa(s):
        c = s.cantos()
        return c.min(0), c.max(0)

    def alinhado(s):
        return s.rot is None or abs(s.rot[1]) < 1e-9


def quad_local(c, f):
    (x0, y0, z0), (x1, y1, z1) = c.a, c.b
    if f == "north":
        return [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)], (0, 0, -1)
    if f == "south":
        return [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)], (0, 0, 1)
    if f == "east":
        return [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)], (1, 0, 0)
    if f == "west":
        return [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)], (-1, 0, 0)
    if f == "up":
        return [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], (0, 1, 0)
    return [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)], (0, -1, 0)


def linhas_chanfro(rx, rz, ch, passo):
    """Retângulo de meia-largura rx × rz com cantos chanfrados em escada: [(z0, z1, hx)]."""
    if ch == 0:
        return [(-rz, rz, rx)]
    out = [(-(rz - ch), rz - ch, rx)]
    for i in range(int(round(ch / passo))):
        z0 = rz - ch + i * passo
        hx = rx - (i + 1) * passo
        out += [(z0, z0 + passo, hx), (-(z0 + passo), -z0, hx)]
    return out


def laje(rx, rz, ch, passo, y0, y1, mat, nome, grupo):
    return [Cubo((-hx, y0, z0), (hx, y1, z1), mat, f"{nome}_{i}", grupo)
            for i, (z0, z1, hx) in enumerate(linhas_chanfro(rx, rz, ch, passo))]


# ---------------------------------------------------------------- a escultura
def subtrair(C, buraco, nome):
    """Tira a caixa `buraco` (alinhada) dos cubos alinhados cujo nome começa com `nome`."""
    (hx0, hy0, hz0), (hx1, hy1, hz1) = buraco
    out = []
    for c in C:
        if not c.nome.startswith(nome) or not c.alinhado():
            out.append(c)
            continue
        a, b = c.a.copy(), c.b.copy()
        if np.any(np.minimum(b, [hx1, hy1, hz1]) - np.maximum(a, [hx0, hy0, hz0]) <= 1e-6):
            out.append(c)
            continue
        h0 = np.maximum(a, [hx0, hy0, hz0])
        h1 = np.minimum(b, [hx1, hy1, hz1])
        resto = []
        cur_a, cur_b = a.copy(), b.copy()
        for k in range(3):
            if cur_a[k] < h0[k]:
                pa, pb = cur_a.copy(), cur_b.copy()
                pb[k] = h0[k]
                resto.append((pa, pb))
                cur_a[k] = h0[k]
            if cur_b[k] > h1[k]:
                pa, pb = cur_a.copy(), cur_b.copy()
                pa[k] = h1[k]
                resto.append((pa, pb))
                cur_b[k] = h1[k]
        for i, (pa, pb) in enumerate(resto):
            out.append(Cubo(pa, pb, c.mat, f"{c.nome}~{i}", c.grupo))
    return out


# Nichos no corpo: as portas entram 2 px, para caber no 5 × 5 (no conceito saíam para fora dele).
NICHOS = (((-13, 3, 36), (13, 12, 38)), ((36, 3, -8), (38, 12, 8)),
          ((-38, 3, -9), (-36, 12, 9)), ((-8, 3, -38), (8, 12, -36)))


def _base(com_sintonizador: bool) -> list[Cubo]:
    C = laje(40, 40, 10, 2, 0, 3, "grafite", "rodape", "base")
    C += laje(38, 38, 10, 2, 3, 12, "porcelana", "corpo", "base")
    C += laje(38, 38, 10, 2, 12, 14, "coral", "listra", "base")
    C += laje(36, 36, 10, 2, 14, 16, "grafite", "tampo", "base")
    # A mesa é a parte de porcelana do tampo, rente (1 px acima cairia na camada de cima).
    for (z0, z1, hx) in linhas_chanfro(24, 24, 10, 2):
        C = subtrair(C, ((-hx, 14, z0), (hx, 16, z1)), "tampo")
    C += laje(24, 24, 10, 2, 14, 16, "porcelana", "mesa", "base")
    for nicho in NICHOS if com_sintonizador else NICHOS[:3]:
        C = subtrair(C, nicho, "corpo")
    return C


def _portas(com_sintonizador: bool) -> list[Cubo]:
    C = [Cubo((-13, 3, 36), (13, 12, 38), "grafite", "nucleo_moldura", "nucleo"),
         # 16 px de largura (no conceito eram 20), para caber na casa do Núcleo num sprite só
         Cubo((-8, 5, 38), (8, 10, 39), "vidro", "nucleo_mostrador", "nucleo", especial={"south": "mostrador"}),
         Cubo((36, 3, -8), (38, 12, 8), "grafite", "energia_caixa", "porta_energia", especial={"east": "energia"}),
         Cubo((38, 6, -6), (40, 10, -2), "latao", "energia_borne_a", "porta_energia"),
         Cubo((38, 6, 2), (40, 10, 6), "latao", "energia_borne_b", "porta_energia"),
         Cubo((-38, 3, -9), (-36, 12, 9), "grafite", "cristais_caixa", "porta_cristais"),
         Cubo((-39, 4, -7), (-38, 11, 7), "porcelana", "cristais_escotilha", "porta_cristais",
              especial={"west": "escotilha"}),
         Cubo((-40, 7, -3), (-39, 8, 3), "latao", "cristais_puxador", "porta_cristais")]
    if com_sintonizador:
        C += [Cubo((-8, 3, -38), (8, 12, -36), "grafite", "sintonizador_caixa", "sintonizador"),
              Cubo((-4, 4, -39), (4, 12, -38), "porcelana", "sintonizador_placa", "sintonizador",
                   especial={"north": "sintonia"})]
    return C


def _pedestal() -> list[Cubo]:
    # O pedestal começa no tampo (y 16): flange 16 a 18, coluna até 32; o copo fica na altura do conceito.
    C = laje(6, 6, 2, 1, 16, 18, "latao", "pedestal_flange", "pedestal")
    C.append(Cubo((-3, 18, -3), (3, 32, 3), "grafite", "pedestal_coluna", "pedestal"))
    C += laje(4, 4, 1, 1, 32, 34, "latao", "pedestal_colar", "pedestal")
    C.append(Cubo((-5, 34, -5), (5, 35, 5), "porcelana", "copo_fundo", "pedestal"))
    C.append(Cubo((-5, 35, -5), (5, 38, -4), "porcelana", "copo_n", "pedestal"))
    C.append(Cubo((-5, 35, 4), (5, 38, 5), "porcelana", "copo_s", "pedestal"))
    C.append(Cubo((4, 35, -4), (5, 38, 4), "porcelana", "copo_l", "pedestal"))
    C.append(Cubo((-5, 35, -4), (-4, 38, 4), "porcelana", "copo_o", "pedestal"))
    C.append(Cubo((-4, 35, -4), (4, 36, 4), "coral", "copo_assento", "pedestal"))
    return C


def _pernas() -> list[Cubo]:
    """Quatro pernas na casa diagonal, centro em (±22, ±22). Base e colar são quadrados alinhados (20 a 24); o
    fuste e o topo são losangos (giro de 45°), com o fuste cortado na divisa das camadas (y 32)."""
    C = []
    for i, (sx, sz) in enumerate(((1, -1), (-1, -1), (-1, 1), (1, 1))):
        cx, cz = 22.0 * sx, 22.0 * sz
        for (y0, y1, h, mat, parte, gira) in (
                (16, 18, 2.0, "latao", "pe", False), (18, 32, 1.4, "grafite", "baixo", True),
                (32, 40, 1.4, "grafite", "meio", True), (40, 42, 2.0, "latao", "colar", False),
                (42, 46, 1.0, "grafite", "alto", True)):
            C.append(Cubo((cx - h, y0, cz - h), (cx + h, y1, cz + h), mat, f"perna{i}_{parte}", "pernas",
                          rot=("y", 45.0) if gira else None, origem=(cx, (y0 + y1) / 2, cz)))
    return C


def fatiar(C):
    """Corta os cubos alinhados nos limites da grade: x e z em ±8 e ±24, y em 16, 32 e 48."""
    cortes = {0: [-24, -8, 8, 24], 1: [16, 32, 48], 2: [-24, -8, 8, 24]}
    out = []
    for c in C:
        if not c.alinhado():
            out.append(c)
            continue
        pecas = [c]
        for k, vs in cortes.items():
            prox = []
            for p in pecas:
                corte = [v for v in vs if p.a[k] + 1e-6 < v < p.b[k] - 1e-6]
                if not corte:
                    prox.append(p)
                    continue
                pts = [p.a[k]] + corte + [p.b[k]]
                for j, (u0, u1) in enumerate(zip(pts, pts[1:])):
                    a, b = p.a.copy(), p.b.copy()
                    a[k], b[k] = u0, u1
                    prox.append(Cubo(a, b, p.mat, f"{p.nome}|{k}{j}", p.grupo, especial=p.especial))
            pecas = prox
        out += pecas
    return out


def celula(c):
    """(cx, cy, cz) da casa do centro da peça e se a peça inteira cabe nela."""
    a, b = c.caixa()
    m = (a + b) / 2
    cel = (math.floor((m[0] + 8) / 16), math.floor(m[1] / 16), math.floor((m[2] + 8) / 16))
    lo = (cel[0] * 16 - 8, cel[1] * 16, cel[2] * 16 - 8)
    dentro = all(lo[k] - 1e-6 <= a[k] and b[k] <= lo[k] + 16 + 1e-6 for k in range(3))
    return cel, dentro


# ---------------------------------------------------------------- conferências
def amostras(c, f):
    q, n = quad_local(c, f)
    q = np.array([c.mundo(p) for p in q])
    n = c.R @ np.array(n, float)
    w = np.linalg.norm(q[1] - q[0])
    h = np.linalg.norm(q[3] - q[0])
    nu, nv = max(2, int(math.ceil(w / 0.25))), max(2, int(math.ceil(h / 0.25)))
    s = (np.arange(nu) + 0.5) / nu
    t = (np.arange(nv) + 0.5) / nv
    S, Tt = np.meshgrid(s, t)
    P = q[0] + S.reshape(-1, 1) * (q[1] - q[0]) + Tt.reshape(-1, 1) * (q[3] - q[0])
    return P, n, q


def dentro(c, Q, eps=1e-6):
    L = c.local(Q)
    return np.all((L >= c.a - eps) & (L <= c.b + eps), axis=1)


def cortar_faces(C):
    """Marca como não desenhada a face que alguma peça encostada cobre inteira."""
    caixas = [c.caixa() for c in C]
    for i, c in enumerate(C):
        for f in FACES:
            P, n, q = amostras(c, f)
            Q = P + n * 0.02
            lo, hi = q.min(0) - 0.1, q.max(0) + 0.1
            coberto = np.zeros(len(Q), bool)
            for j, d in enumerate(C):
                if j == i:
                    continue
                a, b = caixas[j]
                if np.any(a > hi) or np.any(b < lo):
                    continue
                coberto |= dentro(d, Q)
                if coberto.all():
                    break
            c.faces[f] = not coberto.all()


def sat(c, d):
    """Penetração mínima entre duas caixas orientadas (teste dos eixos separadores); 0 quando só encostam."""
    A, B = c.R, d.R
    ha, hb = (c.b - c.a) / 2, (d.b - d.a) / 2
    ca, cb = c.mundo((c.a + c.b) / 2), d.mundo((d.a + d.b) / 2)
    eixos = [A[:, i] for i in range(3)] + [B[:, i] for i in range(3)]
    for i in range(3):
        for j in range(3):
            x = np.cross(A[:, i], B[:, j])
            if np.linalg.norm(x) > 1e-6:
                eixos.append(x / np.linalg.norm(x))
    menor = 1e9
    for L in eixos:
        ra = sum(ha[k] * abs(A[:, k] @ L) for k in range(3))
        rb = sum(hb[k] * abs(B[:, k] @ L) for k in range(3))
        pen = ra + rb - abs((cb - ca) @ L)
        menor = min(menor, pen)
        if pen <= 1e-3:
            return 0.0
    return menor


def sobreposicoes(C):
    caixas = [c.caixa() for c in C]
    ruins = []
    for i in range(len(C)):
        for j in range(i + 1, len(C)):
            (a1, b1), (a2, b2) = caixas[i], caixas[j]
            if np.any(np.minimum(b1, b2) - np.maximum(a1, a2) <= 1e-3):
                continue
            p = sat(C[i], C[j])
            if p > 0:
                ruins.append((C[i].nome, C[j].nome, round(p, 3)))
    return ruins


def area_intersecao(P, Q):
    """Área da interseção de dois polígonos convexos (Sutherland-Hodgman)."""
    def clip(sub, a, b):
        out = []
        for k in range(len(sub)):
            p, q = sub[k], sub[(k + 1) % len(sub)]
            ip = (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]) >= -1e-9
            iq = (b[0] - a[0]) * (q[1] - a[1]) - (b[1] - a[1]) * (q[0] - a[0]) >= -1e-9
            if ip:
                out.append(p)
            if ip != iq:
                d1 = np.array(q) - np.array(p)
                d2 = np.array(b) - np.array(a)
                den = d1[0] * d2[1] - d1[1] * d2[0]
                if abs(den) > 1e-12:
                    t = ((a[0] - p[0]) * d2[1] - (a[1] - p[1]) * d2[0]) / den
                    out.append(tuple(np.array(p) + t * d1))
        return out

    def ccw(poly):
        s = sum(poly[k][0] * poly[(k + 1) % len(poly)][1] - poly[(k + 1) % len(poly)][0] * poly[k][1]
                for k in range(len(poly)))
        return poly if s > 0 else poly[::-1]

    P, Q = ccw([tuple(p) for p in P]), ccw([tuple(p) for p in Q])
    out = P
    for k in range(len(Q)):
        if not out:
            return 0.0
        out = clip(out, Q[k], Q[(k + 1) % len(Q)])
    if len(out) < 3:
        return 0.0
    return abs(sum(out[k][0] * out[(k + 1) % len(out)][1] - out[(k + 1) % len(out)][0] * out[k][1]
                   for k in range(len(out)))) / 2


def faces_coplanares(C):
    """Pares de faces desenhadas no mesmo plano com área em comum (o que pisca no jogo)."""
    grupos = {}
    for i, c in enumerate(C):
        for f in FACES:
            if not c.faces[f]:
                continue
            q, n = quad_local(c, f)
            q = np.array([c.mundo(p) for p in q])
            n = c.R @ np.array(n, float)
            k = np.argmax(np.abs(n))
            nc = n if n[k] > 0 else -n
            chave = (tuple(np.round(nc, 3)), round(float(nc @ q[0]), 2))
            grupos.setdefault(chave, []).append((i, f, q))
    ruins = []
    for (nc, _), lst in grupos.items():
        if len(lst) < 2:
            continue
        nc = np.array(nc)
        u = np.cross(nc, [0, 1, 0] if abs(nc[1]) < 0.9 else [1, 0, 0])
        u /= np.linalg.norm(u)
        v = np.cross(nc, u)
        for a in range(len(lst)):
            for b in range(a + 1, len(lst)):
                ia, fa, qa = lst[a]
                ib, fb, qb = lst[b]
                if ia == ib:
                    continue
                if np.any(np.minimum(qa.max(0), qb.max(0)) - np.maximum(qa.min(0), qb.min(0)) < -1e-6):
                    continue
                ar = area_intersecao([(p @ u, p @ v) for p in qa], [(p @ u, p @ v) for p in qb])
                if ar > 1e-3:
                    ruins.append((ia, fa, ib, fb, ar))
    return ruins


def dividir(C, ruins):
    """Corta cubos alinhados onde o contato é parcial, para cada face ficar inteira coberta ou livre."""
    cortes = {}
    for ia, fa, ib, fb, _ in ruins:
        A, B = C[ia], C[ib]
        if not (A.alinhado() and B.alinhado()) or A.especial or B.especial:
            continue
        k = EIXO_FACE[fa]
        for alvo, outro in ((A, B), (B, A)):
            for ax in range(3):
                if ax == k:
                    continue
                for v in (outro.a[ax], outro.b[ax]):
                    if alvo.a[ax] + 1e-6 < v < alvo.b[ax] - 1e-6:
                        cortes.setdefault(id(alvo), set()).add((ax, round(float(v), 4)))
    if not cortes:
        return C, 0
    novo, n = [], 0
    for c in C:
        if id(c) not in cortes:
            novo.append(c)
            continue
        pecas = [c]
        for ax, v in sorted(cortes[id(c)]):
            prox = []
            for p in pecas:
                if p.a[ax] + 1e-6 < v < p.b[ax] - 1e-6:
                    b1 = p.b.copy()
                    b1[ax] = v
                    a2 = p.a.copy()
                    a2[ax] = v
                    prox.append(Cubo(p.a, b1, p.mat, p.nome + "a", p.grupo))
                    prox.append(Cubo(a2, p.b, p.mat, p.nome + "b", p.grupo))
                    n += 1
                else:
                    prox.append(p)
            pecas = prox
        novo += pecas
    return novo, n


def resolver(C):
    """Tira as faces cobertas e divide os contatos parciais até nenhuma face dividir o plano de outra.
    Devolve (cubos, sobreposições de volume, faces no mesmo plano)."""
    for _ in range(8):
        cortar_faces(C)
        ruins = faces_coplanares(C)
        if not ruins:
            break
        C, n = dividir(C, ruins)
        if n == 0:
            break
    return C, sobreposicoes(C), faces_coplanares(C)


def escultura(com_sintonizador: bool = True):
    """A Armilar formada, cortada na grade e resolvida: (cubos, sobreposições, faces no mesmo plano, fora da
    própria casa). Sem o Sintonizador, a casa dele vira Laje (o corpo fica sem o nicho)."""
    C = _base(com_sintonizador) + _portas(com_sintonizador) + _pedestal() + _pernas()
    C, vol, cop = resolver(fatiar(C))
    fora = [c.nome for c in C if not celula(c)[1]]
    return C, vol, cop, fora
