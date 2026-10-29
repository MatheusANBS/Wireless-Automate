"""Gera o GIF de cor livre da vitrine do CurseForge (a imagem enviada na Tela de Sinal).

    python -I scripts/curseforge/gif_vitrine.py [pasta] [--previa <png>]

Um cristal de sinal girando, com brilho, ondas em degradê que se expandem e partículas em órbita, em 256 x 256 e 24
quadros: imagem de cor livre (degradês, brilho suave e milhares de cores antes da paleta do GIF), para contrastar
com a pixel art de 16 cores. O laço dura 1,5 s, o mesmo da pixel art da vitrine (12 quadros a 8 q/s), com os
atrasos em centésimos repartidos (6, 6, 6 e 7): assim a gravação da parede pega as duas em fase. O cristal tem
simetria de 4 e gira 90 graus por laço, as ondas andam um espaçamento e as partículas uma volta: o último quadro
emenda no primeiro.

Grava `sinal.gif` em <pasta> (padrão run/showcase/entrada), que a vitrine (E2eShowcaseTv) envia pelo mesmo
caminho do botão Enviar arquivo. Com --previa, grava também os quadros numa folha.
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

RAIZ = Path(__file__).resolve().parents[2]
PASTA_PADRAO = RAIZ / "run" / "showcase" / "entrada"

LADO = 256
QUADROS = 24
LACO_CS = 150
SUPER = 4  # o cristal é desenhado 4 vezes maior e reduzido, para a borda sair lisa

YY, XX = np.mgrid[0:LADO, 0:LADO].astype(np.float32)
CX = CY = (LADO - 1) / 2
RAIO = np.hypot(XX - CX, YY - CY)
ANGULO = np.arctan2(YY - CY, XX - CX)


def atrasos_ms() -> list[int]:
    """Os atrasos de cada quadro em ms, em centésimos inteiros que somam o laço."""
    marcas = [math.floor(i * LACO_CS / QUADROS) for i in range(QUADROS + 1)]
    return [(marcas[i + 1] - marcas[i]) * 10 for i in range(QUADROS)]


def mistura(cores: list[tuple[float, tuple[int, int, int]]], t: np.ndarray) -> np.ndarray:
    """O degradê por paradas (posição de 0 a 1, cor) avaliado em {@code t}, em floats de 0 a 1."""
    pos = np.array([p for p, _ in cores], dtype=np.float32)
    saida = np.zeros(t.shape + (3,), dtype=np.float32)
    for canal in range(3):
        saida[..., canal] = np.interp(t, pos, [c[canal] / 255 for _, c in cores])
    return saida


FUNDO = [(0.0, (40, 22, 86)), (0.45, (20, 16, 58)), (1.0, (6, 7, 22))]
ONDAS = [(0.0, (120, 255, 240)), (0.35, (70, 170, 255)), (0.7, (190, 90, 255)), (1.0, (255, 110, 150))]


def fundo() -> np.ndarray:
    base = mistura(FUNDO, np.clip(RAIO / (LADO * 0.72), 0, 1))
    # um raio de luz suave atrás do cristal
    brilho = np.exp(-(RAIO / 70) ** 2)[..., None] * np.array([0.18, 0.12, 0.32], dtype=np.float32)
    return base + brilho


def ondas(t: float) -> np.ndarray:
    """Quatro anéis que crescem um espaçamento por laço, a cor pelo raio e o brilho caindo para fora."""
    espaco = 46.0
    saida = np.zeros((LADO, LADO, 3), dtype=np.float32)
    matiz = ((RAIO - 22) / (espaco * 5) - t) % 1.0
    cor = mistura(ONDAS + [(1.25, ONDAS[0][1])], matiz * 1.25)
    for i in range(5):
        r = 22 + espaco * (i + t)
        if r > 200:
            continue
        forca = np.clip(1 - (r - 22) / 178, 0, 1) ** 1.3
        anel = np.exp(-((RAIO - r) / (3.2 + r * 0.03)) ** 2) * forca
        # um leve ondulado ao longo do anel, que gira com o tempo
        anel *= 0.75 + 0.25 * np.cos(ANGULO * 6 - 2 * math.pi * t)
        saida += cor * anel[..., None] * 0.9
    return saida


def cristal(t: float) -> tuple[np.ndarray, Image.Image]:
    """A bipirâmide de base quadrada girando (90 graus por laço), com as faces sombreadas pela luz e as arestas
    claras; devolve o RGBA em floats e a máscara para o brilho."""
    n = LADO * SUPER
    giro = math.pi / 2 * t + 0.35
    inclina = 0.38
    altura, raio_base = 74, 40
    meio_y = 4

    def projeta(x: float, y: float, z: float) -> tuple[float, float, float]:
        y2 = y * math.cos(inclina) - z * math.sin(inclina)
        z2 = y * math.sin(inclina) + z * math.cos(inclina)
        return (CX + x) * SUPER, (CY + meio_y + y2) * SUPER, z2

    base = [(raio_base * math.cos(giro + k * math.pi / 2), 0.0, raio_base * math.sin(giro + k * math.pi / 2))
            for k in range(4)]
    topo, fundo_ = (0.0, -altura, 0.0), (0.0, altura * 0.62, 0.0)
    luz = np.array([-0.5, -0.7, -0.5])
    luz /= np.linalg.norm(luz)
    faces = []
    for k in range(4):
        a, b = base[k], base[(k + 1) % 4]
        for ponta, cima in ((topo, True), (fundo_, False)):
            pts3 = [np.array(ponta), np.array(a), np.array(b)]
            normal = np.cross(pts3[1] - pts3[0], pts3[2] - pts3[0])
            normal /= np.linalg.norm(normal)
            centro = sum(pts3) / 3
            if np.dot(normal, centro) < 0:
                normal = -normal
            proj = [projeta(*p) for p in pts3]
            # só as faces viradas para a câmera (z projetado da normal para fora da tela)
            nz = normal[1] * math.sin(inclina) + normal[2] * math.cos(inclina)
            if nz > 0.02:
                continue
            luz_face = max(0.0, float(np.dot(normal, -luz)))
            profundidade = sum(p[2] for p in proj) / 3
            faces.append((profundidade, proj, luz_face, cima))
    faces.sort(key=lambda f: -f[0])
    # cada face num degradê próprio (claro em cima, fundo embaixo) e uma faixa de reflexo que corre com o giro
    cor = np.zeros((LADO, LADO, 3), dtype=np.float32)
    alfa = np.zeros((LADO, LADO), dtype=np.float32)
    arestas = Image.new("L", (n, n), 0)
    da = ImageDraw.Draw(arestas)
    vertical = np.clip((YY - (CY - altura)) / (altura * 1.7), 0, 1)
    for _, proj, luz_face, cima in faces:
        pts = [(p[0], p[1]) for p in proj]
        mascara = Image.new("L", (n, n), 0)
        ImageDraw.Draw(mascara).polygon(pts, fill=255)
        m = np.asarray(mascara.resize((LADO, LADO), Image.LANCZOS), dtype=np.float32) / 255
        claro = 0.3 + 0.7 * luz_face
        if cima:
            alto, baixo = np.array([0.88, 1.0, 1.0]), np.array([0.22, 0.78, 0.95])
        else:
            alto, baixo = np.array([0.78, 0.62, 1.0]), np.array([0.36, 0.22, 0.8])
        face = (alto * (1 - vertical[..., None]) + baixo * vertical[..., None]) * (0.6 + 0.55 * claro)
        reflexo = np.exp(-(((XX - CX) * 0.9 + (YY - CY) * 0.45 - 60 * math.sin(2 * math.pi * t)) / 9) ** 2)
        face = face + reflexo[..., None] * 0.5 * claro
        cor = cor * (1 - m[..., None]) + face * m[..., None]
        alfa = np.maximum(alfa, m)
        da.line(pts + [pts[0]], fill=255, width=SUPER * 2, joint="curve")
    linhas = np.asarray(arestas.resize((LADO, LADO), Image.LANCZOS), dtype=np.float32)[..., None] / 255
    cor = cor * (1 - linhas * 0.8) + np.array([0.9, 1.0, 1.0]) * linhas * 0.8
    rgba = np.dstack([np.clip(cor, 0, 1), alfa]).astype(np.float32)
    mascara_total = Image.fromarray((alfa * 255).astype(np.uint8), "L")
    return rgba, mascara_total


def particulas(t: float) -> np.ndarray:
    """Dezesseis pontos em órbitas inclinadas em volta do cristal, uma volta (ou duas) por laço, piscando."""
    saida = np.zeros((LADO, LADO, 3), dtype=np.float32)
    cores = [(0.55, 1.0, 0.95), (0.75, 0.6, 1.0), (1.0, 0.65, 0.75), (0.6, 0.85, 1.0)]
    for i in range(16):
        voltas = 1 if i % 3 else -1
        a = 2 * math.pi * (i / 16 + voltas * t)
        rx, ry = 70 + (i % 4) * 12, 22 + (i % 5) * 5
        x = CX + rx * math.cos(a)
        y = CY + 8 + ry * math.sin(a) - (i % 2) * 6
        frente = math.sin(a) > 0
        pulso = 0.6 + 0.4 * math.cos(2 * math.pi * (t * 2 + i / 7))
        tamanho = 1.6 if frente else 1.1
        ponto = np.exp(-((XX - x) ** 2 + (YY - y) ** 2) / (2 * tamanho ** 2)) * pulso
        halo = np.exp(-((XX - x) ** 2 + (YY - y) ** 2) / (2 * (tamanho * 4) ** 2)) * pulso * 0.25
        saida += (ponto + halo)[..., None] * np.array(cores[i % 4], dtype=np.float32) * (1.0 if frente else 0.55)
    return saida


def quadro(q: int) -> Image.Image:
    t = q / QUADROS
    cena = fundo() + ondas(t)
    rgba, mascara = cristal(t)
    brilho = np.asarray(mascara.filter(ImageFilter.GaussianBlur(14)), dtype=np.float32)[..., None] / 255
    cena += brilho * np.array([0.25, 0.55, 0.75], dtype=np.float32) * (0.85 + 0.15 * math.cos(2 * math.pi * t))
    alfa = rgba[..., 3:4]
    cena = cena * (1 - alfa) + rgba[..., :3] * alfa
    cena += particulas(t)
    return Image.fromarray((np.clip(cena, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB")


def main() -> None:
    args = sys.argv[1:]
    destino_previa = None
    if "--previa" in args:
        i = args.index("--previa")
        destino_previa = Path(args[i + 1])
        del args[i:i + 2]
    pasta = Path(args[0]) if args else PASTA_PADRAO
    pasta.mkdir(parents=True, exist_ok=True)
    quadros = [quadro(q) for q in range(QUADROS)]
    paletados = [im.quantize(256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG)
                 for im in quadros]
    destino = pasta / "sinal.gif"
    paletados[0].save(destino, save_all=True, append_images=paletados[1:], duration=atrasos_ms(), loop=0,
                      disposal=1, optimize=False)
    if destino_previa is not None:
        folha = Image.new("RGB", (LADO * 6, LADO * 4))
        for i, im in enumerate(quadros):
            folha.paste(im, ((i % 6) * LADO, (i // 6) * LADO))
        folha.save(destino_previa)
    print(f"{destino}: {LADO} x {LADO}, {QUADROS} quadros, {destino.stat().st_size / 1024:.0f} KB")


if __name__ == "__main__":
    main()
