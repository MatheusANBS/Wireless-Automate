"""Confere a geometria de um modelo: peças que se atravessam e faces desenhadas no mesmo plano.

Uso:
    python -I verificar_modelo.py MODELO.bbmodel [outro.json ...]
    python -I verificar_modelo.py --junto fatia_a.json@0,0,0 fatia_b.json@1,0,0 ...

Aceita o `.bbmodel` do Blockbench (cubos com `from`, `to`, `origin` e `rotation` em graus por eixo; face com
`texture` nula conta como não desenhada) e o JSON de modelo de bloco do Minecraft (`elements`, com
`rotation: {origin, axis, angle}`; só as faces listadas são desenhadas).

`arquivo@x,y,z` desloca o modelo em blocos (16 unidades por bloco), para pôr cada fatia no seu lugar;
`arquivo@x,y,z,g` também gira o modelo pelo `y` do blockstate (g = 90, 180 ou 270), antes de deslocar.
Com `--junto`, além de cada arquivo sozinho, confere todos como um conjunto só: pega a peça de um bloco
que entra na do vizinho e a face de fronteira desenhada dos dois lados (que precisa sair de um deles).

Imprime as duas contagens que precisam dar 0:
- sobreposição de volume (OBB contra OBB, teste dos eixos separadores; encostar não conta);
- faces desenhadas no mesmo plano com área em comum (o que pisca no jogo e no Blockbench).
Sai com código 1 se alguma der mais que 0.
"""
import json
import math
import sys

import numpy as np

FACES = ("north", "south", "east", "west", "up", "down")


def rot_eixo(eixo, graus):
    t = math.radians(graus)
    c, s = math.cos(t), math.sin(t)
    if eixo == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if eixo == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


class Peca:
    """Um cubo de `a` a `b` no espaço do modelo, levado ao mundo por p -> R p + t."""

    def __init__(self, nome, a, b, R, origem, faces, desloc=(0, 0, 0), giro=0.0):
        self.nome, self.faces = nome, faces
        self.a, self.b = np.array(a, float), np.array(b, float)
        o = np.array(origem, float)
        self.R, self.t = R, o - R @ o
        if giro:
            # o `y` do blockstate gira no sentido horário visto de cima (norte vira leste) em torno do centro
            G, C = rot_eixo('y', -giro), np.array([8.0, 8.0, 8.0])
            self.R, self.t = G @ self.R, G @ (self.t - C) + C
        self.t = self.t + np.array(desloc, float)

    def mundo(self, p):
        return self.R @ np.asarray(p, float) + self.t


def ler(arg):
    caminho, _, onde = arg.partition('@')
    v = [float(x) for x in onde.split(',')] if onde else [0, 0, 0]
    desloc, giro = [16 * x for x in v[:3]], (v[3] if len(v) > 3 else 0.0)
    rotulo = (lambda n: f'{onde}:{n}') if onde else (lambda n: n)
    d = json.load(open(caminho, encoding='utf-8-sig'))
    pecas = []
    if 'elements' in d and d.get('meta') is None:
        for i, e in enumerate(d['elements']):
            r = e.get('rotation')
            R = rot_eixo(r['axis'], r['angle']) if r else np.eye(3)
            o = r['origin'] if r else [0, 0, 0]
            pecas.append(Peca(rotulo(e.get('name', f'#{i}')), e['from'], e['to'], R, o,
                              {f: f in e.get('faces', {}) for f in FACES}, desloc, giro))
        return pecas
    for e in d.get('elements', []):
        if e.get('type', 'cube') != 'cube':
            continue
        rx, ry, rz = e.get('rotation', [0, 0, 0])
        R = rot_eixo('z', rz) @ rot_eixo('y', ry) @ rot_eixo('x', rx)
        fc = {f: (e.get('faces', {}).get(f, {}).get('texture') is not None) for f in FACES}
        pecas.append(Peca(rotulo(e.get('name', '?')), e['from'], e['to'], R, e.get('origin', [0, 0, 0]), fc, desloc, giro))
    return pecas


def quad(c, f):
    (x0, y0, z0), (x1, y1, z1) = c.a, c.b
    q = {'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
         'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
         'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
         'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
         'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
         'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]}[f]
    n = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}[f]
    return np.array([c.mundo(p) for p in q]), c.R @ np.array(n, float)


def penetracao(c, d):
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
        pen = sum(ha[k] * abs(A[:, k] @ L) for k in range(3)) + sum(hb[k] * abs(B[:, k] @ L) for k in range(3)) - abs((cb - ca) @ L)
        if pen <= 1e-3:
            return 0.0
        menor = min(menor, pen)
    return menor


def area_comum(P, Q):
    def ccw(p):
        s = sum(p[k][0] * p[(k + 1) % len(p)][1] - p[(k + 1) % len(p)][0] * p[k][1] for k in range(len(p)))
        return p if s > 0 else p[::-1]

    def recorta(sub, a, b):
        out = []
        for k in range(len(sub)):
            p, q = sub[k], sub[(k + 1) % len(sub)]
            ip = (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]) >= -1e-9
            iq = (b[0] - a[0]) * (q[1] - a[1]) - (b[1] - a[1]) * (q[0] - a[0]) >= -1e-9
            if ip:
                out.append(p)
            if ip != iq:
                d1, d2 = np.subtract(q, p), np.subtract(b, a)
                den = d1[0] * d2[1] - d1[1] * d2[0]
                if abs(den) > 1e-12:
                    t = ((a[0] - p[0]) * d2[1] - (a[1] - p[1]) * d2[0]) / den
                    out.append(tuple(np.add(p, t * d1)))
        return out

    P, Q = ccw([tuple(p) for p in P]), ccw([tuple(p) for p in Q])
    out = P
    for k in range(len(Q)):
        if not out:
            return 0.0
        out = recorta(out, Q[k], Q[(k + 1) % len(Q)])
    if len(out) < 3:
        return 0.0
    return abs(sum(out[k][0] * out[(k + 1) % len(out)][1] - out[(k + 1) % len(out)][0] * out[k][1] for k in range(len(out)))) / 2


def conferir(rotulo, pecas):
    vol = []
    for i in range(len(pecas)):
        for j in range(i + 1, len(pecas)):
            p = penetracao(pecas[i], pecas[j])
            if p > 0:
                vol.append((pecas[i].nome, pecas[j].nome, round(float(p), 3)))
    grupos = {}
    for i, c in enumerate(pecas):
        for f in FACES:
            if not c.faces[f]:
                continue
            q, n = quad(c, f)
            k = int(np.argmax(np.abs(n)))
            nc = n if n[k] > 0 else -n
            grupos.setdefault((tuple(np.round(nc, 3)), round(float(nc @ q[0]), 2)), []).append((i, f, q, nc))
    plano = []
    for (nc, _), lst in grupos.items():
        nc = np.array(nc)
        u = np.cross(nc, [0, 1, 0] if abs(nc[1]) < 0.9 else [1, 0, 0]); u /= np.linalg.norm(u)
        v = np.cross(nc, u)
        for a in range(len(lst)):
            for b in range(a + 1, len(lst)):
                if lst[a][0] == lst[b][0]:
                    continue
                ar = area_comum([(p @ u, p @ v) for p in lst[a][2]], [(p @ u, p @ v) for p in lst[b][2]])
                if ar > 1e-3:
                    plano.append((pecas[lst[a][0]].nome, lst[a][1], pecas[lst[b][0]].nome, lst[b][1], round(float(ar), 3)))
    # face desenhada e coberta por inteiro por uma peça encostada (de frente para ela): não pisca, mas é
    # desenho jogado fora; entre fatias, o cullface não a tira, porque o vizinho não é um cubo cheio
    todas = {}
    for i, c in enumerate(pecas):
        for f in FACES:
            q, n = quad(c, f)
            k = int(np.argmax(np.abs(n)))
            nc = n if n[k] > 0 else -n
            todas.setdefault((tuple(np.round(nc, 3)), round(float(nc @ q[0]), 2)), []).append((i, f, q, n, c.faces[f]))
    escondida = []
    for (nc, _), lst in todas.items():
        nc = np.array(nc)
        u = np.cross(nc, [0, 1, 0] if abs(nc[1]) < 0.9 else [1, 0, 0]); u /= np.linalg.norm(u)
        v = np.cross(nc, u)
        for i, f, q, n, desenhada in lst:
            if not desenhada:
                continue
            P = [(p @ u, p @ v) for p in q]
            total = area_comum(P, P)
            coberta = sum(area_comum(P, [(p @ u, p @ v) for p in q2]) for j, _, q2, n2, _ in lst if j != i and n2 @ n < -0.99)
            if total > 1e-3 and coberta >= total - 1e-3:
                escondida.append((pecas[i].nome, f))
    print(f'{rotulo}: {len(pecas)} peças; sobreposição de volume: {len(vol)}; faces no mesmo plano: {len(plano)}'
          f'; faces escondidas (aviso): {len(escondida)}')
    for x in vol[:12]:
        print('   volume:', x)
    for x in plano[:12]:
        print('   plano:', x)
    for x in escondida[:12]:
        print('   escondida:', x)
    return len(vol) + len(plano)


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    args = sys.argv[1:]
    junto = '--junto' in args
    args = [a for a in args if a != '--junto']
    if not args:
        print(__doc__)
        sys.exit(2)
    lidos = [(a, ler(a)) for a in args]
    total = sum(conferir(a, p) for a, p in lidos)
    if junto:
        total += conferir('conjunto', [p for _, ps in lidos for p in ps])
    sys.exit(1 if total else 0)
