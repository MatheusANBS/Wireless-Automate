"""Gera a capa (avatar) do projeto no CurseForge.

    python scripts/curseforge/gerar_capa.py

Regras do CurseForge (support.curseforge.com, "Moderation Policies"): avatar de 400x400 px, sem
cor sólida, sem NSFW e sem imagem com direitos de terceiros (nada do logo do Minecraft); PNG ou
JPG (WebP não). Aparece pequeno nas listas, então não leva texto.

O desenho é feito em 100x100 com as texturas do próprio mod (o roteador montado vem de
scripts/textures/gerar_texturas.py) e ampliado 4x sem suavização, para ficar no estilo pixel art
do jogo. Saída: docs/curseforge/capa-400.png (e uma prévia em tamanhos pequenos).
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RAIZ / "scripts" / "textures"))
import gerar_texturas as tex  # noqa: E402

SAIDA = RAIZ / "docs" / "curseforge"
BASE = 100
ESCALA = 4

CIANO = [(0x16, 0x62, 0x5e), (0x25, 0x9a, 0x93), (0x45, 0xd6, 0xcc), (0x9f, 0xf5, 0xee), (0xef, 0xff, 0xfd)]
OURO = (0xf7, 0xd6, 0x70)


def fundo() -> Image.Image:
    """Degradê radial azul-escuro, grade de pontos e vinheta (nunca uma cor sólida)."""
    img = Image.new("RGBA", (BASE, BASE))
    cx, cy = BASE * 0.5, BASE * 0.42
    for y in range(BASE):
        for x in range(BASE):
            d = math.hypot(x - cx, y - cy) / (BASE * 0.72)
            t = min(1.0, d)
            r = int(0x1c + (0x0b - 0x1c) * t)
            g = int(0x33 + (0x10 - 0x33) * t)
            b = int(0x46 + (0x18 - 0x46) * t)
            # Grade de pontos a cada 5 px, mais visível perto do centro.
            if x % 5 == 2 and y % 5 == 2:
                k = 0.35 * (1 - t)
                r, g, b = int(r + (0x45 - r) * k), int(g + (0xd6 - g) * k), int(b + (0xcc - b) * k)
            img.putpixel((x, y), (r, g, b, 255))
    return img


def ondas(img: Image.Image, cx: float, cy: float) -> None:
    """Três arcos de wireless acima das antenas, do mais forte (dentro) ao mais fraco (fora)."""
    d = ImageDraw.Draw(img)
    for i, raio in enumerate((10, 17, 24)):
        cor = CIANO[3 - i] if i < 3 else CIANO[1]
        caixa = [cx - raio, cy - raio, cx + raio, cy + raio]
        d.arc(caixa, start=215, end=325, fill=cor + (255,), width=3 if i == 0 else 2)


def faiscas(img: Image.Image) -> None:
    """Algumas faíscas de 1 px em cruz, como itens voando pela rede."""
    for (x, y, cor) in ((14, 22, CIANO[3]), (84, 30, CIANO[2]), (20, 70, OURO), (86, 66, CIANO[3]),
                        (9, 46, CIANO[2]), (91, 48, OURO)):
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            img.putpixel((x + dx, y + dy), cor + (255,) if (dx, dy) == (0, 0) else tuple(c // 2 for c in cor) + (255,))


# LEDs da frente acesos (na textura do jogo eles ficam apagados: a luz é uma camada à parte).
LEDS_ACESOS = {"E": "#ffd36b", "e": "#c99a2e", "N": "#7ff6ea", "n": "#36c7bb",
               "A": "#8ff07a", "a": "#4fb83e", "F": "#ff7b6e", "f": "#c94a40"}


def acende_leds(img: Image.Image) -> Image.Image:
    troca = {tex.rgb(tex.PALETAS["leds"][k]): tex.rgb(v) for k, v in LEDS_ACESOS.items()}
    saida = img.copy()
    for y in range(img.height):
        for x in range(img.width):
            px = img.getpixel((x, y))
            if px in troca:
                saida.putpixel((x, y), troca[px])
    return saida


def capa() -> Image.Image:
    sprites = tex.gerar()
    sprites["block/router_elite_front"] = acende_leds(sprites["block/router_elite_front"])
    roteador = tex.roteador_montado("elite", sprites, s=3)
    roteador = roteador.crop(roteador.getbbox())
    img = fundo()
    # Sombra elíptica no chão.
    sombra = Image.new("RGBA", (BASE, BASE), (0, 0, 0, 0))
    ImageDraw.Draw(sombra).ellipse([BASE / 2 - 30, 80, BASE / 2 + 30, 90], fill=(0, 0, 0, 110))
    img.alpha_composite(sombra)
    x = (BASE - roteador.width) // 2
    y = 86 - roteador.height
    img.alpha_composite(roteador, (x, y))
    # As ondas saem do meio das duas antenas.
    ondas(img, BASE / 2, y + 4)
    faiscas(img)
    return img.resize((BASE * ESCALA, BASE * ESCALA), Image.NEAREST)


def main() -> None:
    SAIDA.mkdir(parents=True, exist_ok=True)
    img = capa().convert("RGB")
    assert img.size == (400, 400)
    img.save(SAIDA / "capa-400.png")
    # Prévia: como aparece nas listas (64 e 128 px) ao lado do tamanho real.
    previa = Image.new("RGB", (400 + 128 + 64 + 40, 400), (24, 28, 35))
    previa.paste(img, (0, 0))
    previa.paste(img.resize((128, 128), Image.LANCZOS), (420, 20))
    previa.paste(img.resize((64, 64), Image.LANCZOS), (568, 20))
    previa.save(SAIDA / "capa-previa.png")
    print(f"capa em {(SAIDA / 'capa-400.png').relative_to(RAIZ)}")


if __name__ == "__main__":
    main()
