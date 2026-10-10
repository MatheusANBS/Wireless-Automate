"""Gera a capa (avatar) do projeto no CurseForge e a arte do banner.

    WA_SCREENSHOT=run/shots-capa WA_SCREENSHOT_ONLY=capa ./gradlew runClient   # renders do roteador (DevScreenshot)
    python scripts/curseforge/gerar_capa.py                 # capa-400.png e capa-previa.png em docs/curseforge
    python scripts/curseforge/gerar_capa.py variantes <dir> # todas as variantes de capa e banner, para comparar

Regras do CurseForge (support.curseforge.com, "Moderation Policies"): avatar de 400x400 px, sem
cor sólida, sem NSFW e sem imagem com direitos de terceiros (nada do logo do Minecraft); PNG ou
JPG (WebP não). Aparece pequeno nas listas, então não leva texto.

A composição é a da primeira capa do mod (a que o dono gosta): o roteador em pixel art no centro,
três ondas de wireless acima das antenas, faíscas em volta, sobre um degradê radial com grade de
pontos. O roteador é o modelo 3D real, renderizado pelo jogo ({@code capa-renders.png}: a vista
"de frente e do alto", luz cheia, sobre magenta) e ampliado por fator inteiro, sem suavizar, para
manter o pixel. As cores do fundo, das ondas e das faíscas vêm de uma paleta ({@code PALETAS}).
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RAIZ / "scripts" / "textures"))
import identidade  # noqa: E402

SAIDA = RAIZ / "docs" / "curseforge"
RENDERS = RAIZ / "run" / "shots-capa" / "capa-renders.png"
BASE = 100
ESCALA = 4
PALETA_ATIVA = "noite"  # a paleta da capa e do banner publicados
RENDER_ATIVO = "208x1"  # o render do roteador (ver roteador())


def cor(material: str, tom: str) -> tuple[int, int, int]:
    h = identidade.PALETA[material][tom].lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


PORCELANA = [cor("porcelana", t) for t in "01234"]
GRAFITE = [cor("grafite", t) for t in "01234"]
CORAL = [cor("coral", t) for t in "01234"]

# Cada paleta: fundo no centro e na borda, cor dos pontos da grade, as ondas (de dentro para fora) e as duas
# cores das faíscas; mais as cores do texto do banner (título, linha 1, linha 2).
PALETAS = {
    # a original: azul-escuro com ciano
    "noite": dict(centro=(0x1c, 0x33, 0x46), borda=(0x0b, 0x10, 0x18), ponto=(0x45, 0xd6, 0xcc),
                  ondas=[(0xef, 0xff, 0xfd), (0x9f, 0xf5, 0xee), (0x45, 0xd6, 0xcc)],
                  faiscas=[(0x9f, 0xf5, 0xee), (0xf7, 0xd6, 0x70)], sombra=(0, 0, 0, 110),
                  titulo=(0x9f, 0xf5, 0xee), titulo_sombra=(8, 40, 44), texto=(226, 233, 240), muted=(147, 160, 174)),
    # a mesma composição na identidade nova: grafite com coral e a lente
    "grafite": dict(centro=cor("grafite", "3"), borda=cor("grafite", "0"), ponto=cor("coral", "2"),
                    ondas=[cor("coral", "4"), cor("coral", "3"), cor("coral", "2")],
                    faiscas=[(0x9f, 0xf5, 0xee), cor("latao", "3")], sombra=(0, 0, 0, 120),
                    titulo=cor("porcelana", "4"), titulo_sombra=cor("coral", "1"), texto=cor("porcelana", "3"),
                    muted=cor("porcelana", "1")),
    # clara: porcelana com coral
    "porcelana": dict(centro=cor("porcelana", "4"), borda=cor("porcelana", "2"), ponto=cor("porcelana", "1"),
                      ondas=[cor("coral", "1"), cor("coral", "2"), cor("coral", "3")],
                      faiscas=[(0x2a, 0xa8, 0x9e), cor("latao", "1")], sombra=(60, 50, 40, 70),
                      titulo=cor("grafite", "1"), titulo_sombra=cor("coral", "2"), texto=cor("grafite", "3"),
                      muted=cor("porcelana", "0")),
}


# ------------------------------------------------------------------ renders do jogo


def recortes() -> list[list[Image.Image]]:
    """Os renders de {@code capa-renders.png}, por linha e da esquerda para a direita, já sem o magenta
    (RGBA) e aparados. Linha 0: o Elite em quatro tamanhos; linha 1: os oito tiers; linha 2: três ângulos."""
    if not RENDERS.exists():
        raise SystemExit(f"falta {RENDERS.relative_to(RAIZ)}: rode WA_SCREENSHOT=run/shots-capa WA_SCREENSHOT_ONLY=capa ./gradlew runClient")
    img = Image.open(RENDERS).convert("RGB")
    a = np.asarray(img).astype(int)
    magenta = (abs(a[..., 0] - 255) < 40) & (a[..., 1] < 40) & (abs(a[..., 2] - 255) < 40)
    mask = ~magenta
    linhas = []
    for y0, y1 in faixas(mask.any(axis=1)):
        itens = []
        for x0, x1 in faixas(mask[y0:y1].any(axis=0)):
            sub = img.crop((x0, y0, x1, y1)).convert("RGBA")
            m = Image.fromarray((mask[y0:y1, x0:x1] * 255).astype("uint8"))
            sub.putalpha(m)
            itens.append(sub.crop(sub.getbbox()))
        linhas.append(itens)
    return linhas


def faixas(linha: np.ndarray, folga: int = 6) -> list[tuple[int, int]]:
    """Intervalos de valores verdadeiros, juntando os separados por menos de {@code folga}."""
    idx = np.flatnonzero(linha)
    if idx.size == 0:
        return []
    cortes = np.flatnonzero(np.diff(idx) > folga)
    inicios = np.concatenate(([idx[0]], idx[cortes + 1]))
    fins = np.concatenate((idx[cortes], [idx[-1]]))
    return [(int(i), int(f) + 1) for i, f in zip(inicios, fins)]


def roteador(render: str = RENDER_ATIVO) -> Image.Image:
    """O roteador pronto para a capa de 400 px: um dos renders ampliado por fator inteiro (NEAREST).
    {@code render}: "<altura aproximada do render>x<fator>", entre os da linha 0 ("48x5", "80x3", "128x2",
    "208x1") ou um ângulo da linha 2 ("lado-a", "lado-b", "frente")."""
    linhas = recortes()
    if render.startswith("lado") or render == "frente":
        img = linhas[2][{"lado-a": 0, "lado-b": 1, "frente": 2}[render]]
        fator = 2
    else:
        alvo, fator = (int(v) for v in render.split("x"))
        img = min(linhas[0], key=lambda r: abs(r.height - alvo))
    return img.resize((img.width * fator, img.height * fator), Image.NEAREST)


# ------------------------------------------------------------------ composição (a da primeira capa)


def fundo(pal: dict, largura: int = BASE, altura: int = BASE, cx: float | None = None, cy: float | None = None) -> Image.Image:
    """Degradê radial, grade de pontos e vinheta (nunca uma cor sólida), em 1x."""
    img = Image.new("RGBA", (largura, altura))
    cx = largura * 0.5 if cx is None else cx
    cy = altura * 0.42 if cy is None else cy
    alcance = max(largura, altura) * 0.72
    c0, c1, p = pal["centro"], pal["borda"], pal["ponto"]
    px = img.load()
    for y in range(altura):
        for x in range(largura):
            t = min(1.0, math.hypot(x - cx, y - cy) / alcance)
            r, g, b = (int(a + (b_ - a) * t) for a, b_ in zip(c0, c1))
            if x % 5 == 2 and y % 5 == 2:
                k = 0.35 * (1 - t)
                r, g, b = int(r + (p[0] - r) * k), int(g + (p[1] - g) * k), int(b + (p[2] - b) * k)
            px[x, y] = (r, g, b, 255)
    return img


# A ponta do receptor da parabólica no render "de frente e do alto", em fração da largura e da altura dele:
# as ondas saem daí, abrindo na direção em que o prato aponta (para cima e um pouco à direita).
PONTA = (107 / 224, 33 / 243)
ONDAS_ANGULO = (245, 355)


def ondas(d: ImageDraw.ImageDraw, pal: dict, cx: float, cy: float, s: int = 1) -> None:
    """Três arcos de wireless centrados na ponta do receptor, do mais forte (dentro) ao mais fraco (fora), em
    unidades de 1x vezes {@code s}."""
    ini, fim = ONDAS_ANGULO
    for i, raio in enumerate((9, 16, 23)):
        r = raio * s
        d.arc([cx - r, cy - r, cx + r, cy + r], start=ini, end=fim, fill=pal["ondas"][i] + (255,),
              width=(3 if i == 0 else 2) * s)


def ponta(rot: Image.Image, x: int, y: int) -> tuple[float, float]:
    """Onde fica a ponta do receptor com o render colado em (x, y)."""
    return x + rot.width * PONTA[0], y + rot.height * PONTA[1]


def estrela(d: ImageDraw.ImageDraw, x: int, y: int, c: tuple, grande: bool, s: int = 1) -> None:
    meia = tuple(v // 2 for v in c) + (255,)
    d.rectangle([x, y, x + s - 1, y + s - 1], fill=c + (255,))
    for passo in ((1, 2) if grande else (1,)):
        for dx, dy in ((passo, 0), (-passo, 0), (0, passo), (0, -passo)):
            px, py = x + dx * s, y + dy * s
            d.rectangle([px, py, px + s - 1, py + s - 1], fill=c + (255,) if passo == 1 and grande else meia)


def faiscas(d: ImageDraw.ImageDraw, pal: dict, cx: float, s: int = 1) -> None:
    a, b = pal["faiscas"]
    pares = ((31, 13, a, True), (40, 29, b, False), (45, 48, a, False), (43, 68, b, True), (37, 90, a, False))
    for dx, y, c, grande in pares:
        for x in (round(cx - dx), round(cx + dx)):
            estrela(d, x * s, y * s, c, grande, s)


def capa(paleta: str = PALETA_ATIVA, render: str = RENDER_ATIVO) -> Image.Image:
    pal = PALETAS[paleta]
    lado = BASE * ESCALA
    img = fundo(pal).resize((lado, lado), Image.NEAREST)
    d = ImageDraw.Draw(img, "RGBA")
    rot = roteador(render)
    # o roteador centrado, com o pé a 86% da altura, e a sombra elíptica embaixo
    x = (lado - rot.width) // 2
    y = round(lado * 0.86) - rot.height
    d.ellipse([lado / 2 - 30 * ESCALA, 80 * ESCALA, lado / 2 + 34 * ESCALA, 90 * ESCALA], fill=pal["sombra"])
    img.alpha_composite(rot, (x, y))
    ondas(d, pal, *ponta(rot, x, y), ESCALA)
    faiscas(d, pal, BASE / 2, ESCALA)
    return img


def arte(paleta: str = PALETA_ATIVA, render: str = RENDER_ATIVO, lado: int = 360) -> Image.Image:
    """A capa sem o fundo (RGBA), para o banner."""
    pal = PALETAS[paleta]
    full = BASE * ESCALA
    img = Image.new("RGBA", (full, full), (0, 0, 0, 0))
    d = ImageDraw.Draw(img, "RGBA")
    rot = roteador(render)
    x = (full - rot.width) // 2
    y = round(full * 0.86) - rot.height
    d.ellipse([full / 2 - 30 * ESCALA, 80 * ESCALA, full / 2 + 34 * ESCALA, 90 * ESCALA], fill=pal["sombra"])
    img.alpha_composite(rot, (x, y))
    ondas(d, pal, *ponta(rot, x, y), ESCALA)
    faiscas(d, pal, BASE / 2, ESCALA)
    return img.resize((lado, lado), Image.NEAREST if lado % full == 0 else Image.LANCZOS) if lado != full else img


def main() -> None:
    SAIDA.mkdir(parents=True, exist_ok=True)
    if len(sys.argv) > 2 and sys.argv[1] == "variantes":
        destino = Path(sys.argv[2])
        destino.mkdir(parents=True, exist_ok=True)
        sys.path.insert(0, str(RAIZ / "scripts" / "curseforge"))
        import gerar_imagens
        for paleta in PALETAS:
            for render in ("208x1", "80x3"):
                capa(paleta, render).convert("RGB").save(destino / f"capa-{paleta}-{render}.png")
            gerar_imagens.banner(paleta).save(destino / f"banner-{paleta}.png")
        print(f"variantes em {destino}")
        return
    img = capa().convert("RGB")
    assert img.size == (400, 400)
    img.save(SAIDA / "capa-400.png")
    previa = Image.new("RGB", (400 + 128 + 64 + 40, 400), (24, 28, 35))
    previa.paste(img, (0, 0))
    previa.paste(img.resize((128, 128), Image.LANCZOS), (420, 20))
    previa.paste(img.resize((64, 64), Image.LANCZOS), (568, 20))
    previa.save(SAIDA / "capa-previa.png")
    print(f"capa em {(SAIDA / 'capa-400.png').relative_to(RAIZ)}")


if __name__ == "__main__":
    main()
