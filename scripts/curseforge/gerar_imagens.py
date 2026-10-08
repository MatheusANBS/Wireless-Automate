"""Gera o banner e as imagens da descrição do projeto no CurseForge.

    WA_SHOWCASE=run/showcase ./gradlew runClient    # fotos da vitrine (DevEndToEnd, modo WA_SHOWCASE)
    WA_E2E=run/e2e ./gradlew runClient               # capturas do guia (opcional: guia-*.png)
    python scripts/curseforge/gerar_imagens.py

O CurseForge não fixa tamanho para imagens da descrição; a regra é mostrar o mod como ele é no
jogo. Por isso as imagens de funções são capturas reais (a vitrine monta uma fábrica de exemplo)
com uma faixa de título. O banner tem 1600x400 (4:1), legível na coluna da descrição.
Textos em inglês: o público do CurseForge é internacional.
Saída: docs/curseforge/banner.png e docs/curseforge/feature-*.png.
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

RAIZ = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RAIZ / "scripts" / "curseforge"))
import gerar_capa as capa  # noqa: E402

SAIDA = RAIZ / "docs" / "curseforge"
VITRINE = RAIZ / "run" / "showcase"
E2E = RAIZ / "run" / "e2e"

FUNDO = (14, 20, 28)
FAIXA = (20, 30, 41)
CIANO = (69, 214, 204)
CIANO_CLARO = (159, 245, 238)
TEXTO = (226, 233, 240)
MUTED = (147, 160, 174)

# ------------------------------------------------------------------ fonte em pixel (5x7)

GLIFOS = {
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    "C": ["01110", "10001", "10000", "10000", "10000", "10001", "01110"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "G": ["01110", "10001", "10000", "10111", "10001", "10001", "01111"],
    "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    "I": ["01110", "00100", "00100", "00100", "00100", "00100", "01110"],
    "J": ["00111", "00010", "00010", "00010", "00010", "10010", "01100"],
    "K": ["10001", "10010", "10100", "11000", "10100", "10010", "10001"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    "N": ["10001", "10001", "11001", "10101", "10011", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "Q": ["01110", "10001", "10001", "10001", "10101", "10010", "01101"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    "V": ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
    "W": ["10001", "10001", "10001", "10101", "10101", "10101", "01010"],
    "X": ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
    "Y": ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    "Z": ["11111", "00001", "00010", "00100", "01000", "10000", "11111"],
    "0": ["01110", "10001", "10011", "10101", "11001", "10001", "01110"],
    "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    "2": ["01110", "10001", "00001", "00010", "00100", "01000", "11111"],
    "3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
    "4": ["00010", "00110", "01010", "10010", "11111", "00010", "00010"],
    "&": ["01100", "10010", "10100", "01000", "10101", "10010", "01101"],
    "-": ["00000", "00000", "00000", "11111", "00000", "00000", "00000"],
    ",": ["00000", "00000", "00000", "00000", "00000", "00100", "01000"],
    ".": ["00000", "00000", "00000", "00000", "00000", "00000", "00100"],
    "'": ["00100", "00100", "01000", "00000", "00000", "00000", "00000"],
    " ": ["00000"] * 7,
}


def largura_pixel(texto: str, px: int) -> int:
    return sum((6 if c != " " else 4) * px for c in texto.upper()) - px


def escreve_pixel(img: Image.Image, texto: str, x: int, y: int, px: int, cor, sombra=(0, 0, 0)) -> None:
    d = ImageDraw.Draw(img)
    for camada, (ox, oy, c) in enumerate(((px, px, sombra), (0, 0, cor))):
        cx = x
        for ch in texto.upper():
            glifo = GLIFOS[ch]
            for gy, linha in enumerate(glifo):
                for gx, bit in enumerate(linha):
                    if bit == "1":
                        x0, y0 = cx + gx * px + ox, y + gy * px + oy
                        d.rectangle([x0, y0, x0 + px - 1, y0 + px - 1], fill=c)
            cx += (6 if ch != " " else 4) * px


def fonte(tamanho: int) -> ImageFont.FreeTypeFont:
    return ImageFont.load_default(size=tamanho)


# ------------------------------------------------------------------ banner

def banner() -> Image.Image:
    w, h = 1600, 400
    img = Image.new("RGB", (w, h), FUNDO)
    # O mesmo fundo da capa (degradê com o centro atrás do roteador e grade de pontos), gerado já
    # na proporção do banner em 1/4 do tamanho e ampliado 4x, como a capa.
    fundo = capa.fundo(w // 4, h // 4, cx=55, cy=45).resize((w, h), Image.NEAREST)
    img.paste(fundo.convert("RGB"))
    # A arte da capa (roteador, ondas e faíscas) sem o fundo dela, à esquerda.
    arte = capa.capa(com_fundo=False).resize((360, 360), Image.NEAREST)
    img.paste(arte, (40, 20), arte)
    # Título em pixel e a linha de descrição.
    titulo = "Wireless Automate"
    px = 10
    x = 450
    assert x + largura_pixel(titulo, px) < w - 40, "título passa da borda"
    escreve_pixel(img, titulo, x, 92, px, CIANO_CLARO, sombra=(8, 40, 44))
    d = ImageDraw.Draw(img)
    d.text((x + 4, 92 + 7 * px + 34), "Wireless items, fluids, energy and Mekanism chemicals.",
           fill=TEXTO, font=fonte(40))
    d.text((x + 4, 92 + 7 * px + 90), "No pipes. Light on TPS. Built for big modpacks.",
           fill=MUTED, font=fonte(32))
    return img


# ------------------------------------------------------------------ imagens de função

def destaque(nome: str, titulo: str, subtitulo: str, origem: Path, recorte: tuple[int, int, int, int]) -> Image.Image:
    foto = Image.open(origem).convert("RGB").crop(recorte)
    largura = 1280
    foto = foto.resize((largura - 48, round((largura - 48) * foto.height / foto.width)), Image.LANCZOS)
    faixa = 150
    img = Image.new("RGB", (largura, faixa + foto.height + 24), FUNDO)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, largura, faixa - 1], fill=FAIXA)
    d.rectangle([0, faixa - 4, largura, faixa - 1], fill=CIANO)
    px = 6
    escreve_pixel(img, titulo, 32, 34, px, CIANO_CLARO, sombra=(8, 40, 44))
    d.text((34, 34 + 7 * px + 24), subtitulo, fill=TEXTO, font=fonte(30))
    # Moldura de 3 px em volta da captura.
    x0, y0 = 24, faixa + 8
    d.rectangle([x0 - 3, y0 - 3, x0 + foto.width + 2, y0 + foto.height + 2], fill=(46, 64, 82))
    img.paste(foto, (x0, y0))
    return img


TIERS = [
    # (tier, nome, cor do nome, itens/s, mB/s, FE/t, alcance) — os padrões de RouterTier.
    ("basic", "Basic", (200, 204, 210), "512", "32,000", "16,000", "128 blocks"),
    ("advanced", "Advanced", (226, 179, 71), "8,192", "512,000", "256,000", "1,024 blocks"),
    ("elite", "Elite", (69, 214, 204), "131,072", "8,000,000", "4,000,000", "Whole dimension"),
    ("ultimate", "Ultimate", (164, 108, 255), "Unlimited", "Unlimited", "Unlimited", "Every dimension"),
]


def tiers() -> Image.Image:
    """Os quatro roteadores (texturas do mod, LEDs acesos) com a vazão e o alcance de cada um."""
    largura, faixa = 1280, 150
    img = Image.new("RGB", (largura, faixa + 560), FUNDO)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, largura, faixa - 1], fill=FAIXA)
    d.rectangle([0, faixa - 4, largura, faixa - 1], fill=CIANO)
    escreve_pixel(img, "Four tiers", 32, 34, 6, CIANO_CLARO, sombra=(8, 40, 44))
    d.text((34, 34 + 42 + 24), "Upgrade cards raise throughput and range, per face and per resource type.",
           fill=TEXTO, font=fonte(30))
    sprites = capa.tex.gerar()
    coluna = largura // 4
    rotulos = ("Items/s", "Fluid (mB/s)", "Energy (FE/t)", "Range")
    for i, (tier, nome, cor, *valores) in enumerate(TIERS):
        sprites[f"block/router_{tier}_front"] = capa.acende_leds(sprites[f"block/router_{tier}_front"])
        arte = capa.tex.roteador_montado(tier, sprites, s=3)
        arte = arte.crop(arte.getbbox())
        arte = arte.resize((arte.width * 3, arte.height * 3), Image.NEAREST)
        cx = i * coluna + coluna // 2
        img.paste(arte, (cx - arte.width // 2, faixa + 230 - arte.height), arte)
        escreve_pixel(img, nome, cx - largura_pixel(nome, 5) // 2, faixa + 255, 5, cor, sombra=(0, 0, 0))
        y = faixa + 310
        for rotulo, valor in zip(rotulos, valores):
            d.text((cx, y), rotulo, fill=MUTED, font=fonte(22), anchor="mt")
            d.text((cx, y + 26), valor, fill=TEXTO, font=fonte(28), anchor="mt")
            y += 62
        if i:
            d.line([(i * coluna, faixa + 30), (i * coluna, faixa + 530)], fill=(36, 50, 64), width=2)
    return img


DESTAQUES = [
    ("feature-1-network", "Connect machines without pipes",
     "Attach a router to any machine. Routers on the same network trade with each other.",
     VITRINE / "s1-fabrica.png", (60, 250, 1220, 620)),
    ("feature-2-router", "Configure every face",
     "Pick a face on the 3D model and set it to Extract, Insert or Storage, per resource type.",
     VITRINE / "s3-roteador.png", (180, 30, 1100, 770)),
    ("feature-3-filters", "Tags in one click",
     "Put any item in the inspector to see and check all its tags, or search every tag in the game.",
     VITRINE / "s5b-filtro-tags.png", (270, 120, 1010, 680)),
    ("feature-8-rules", "Property rules",
     "Any enchanted item, tools under 50% durability... and your inventory lights up.",
     VITRINE / "s5c-filtro-regra.png", (270, 120, 1010, 680)),
    ("feature-9-storage", "Storage of its own",
     "Wireless Chest, Tank, Battery and Chemical Tank: billions per operation between them.",
     VITRINE / "s9-armazenamentos.png", (230, 250, 1110, 640)),
    ("feature-10-chest", "Wireless Chest",
     "Unlimited item types in a searchable, resizable list. Keeps everything when broken.",
     VITRINE / "s10-bau.png", (365, 140, 910, 660)),
    ("feature-5-area", "Link whole areas at once",
     "The Linker and the Configurator work on a marked area: one click for a whole factory.",
     VITRINE / "s2-vinculador-area.png", (60, 250, 1220, 620)),
    ("feature-6-tablet", "Network Tablet",
     "Every router of every network, from anywhere: search, map, stats and groups.",
     VITRINE / "s6-tablet-lista.png", (180, 30, 1100, 770)),
    ("feature-7-guide", "Built-in guide book",
     "Every item explained in game, with 3D scenes and recipes. English and Portuguese.",
     E2E / "guia-getting-started.png", (300, 40, 1270, 568)),
]


def main() -> None:
    SAIDA.mkdir(parents=True, exist_ok=True)
    banner().save(SAIDA / "banner.png")
    tiers().save(SAIDA / "feature-4-tiers.png")
    feitos = ["banner.png", "feature-4-tiers.png"]
    for nome, titulo, sub, origem, recorte in DESTAQUES:
        if not origem.exists():
            print(f"pulando {nome}: falta {origem.relative_to(RAIZ)}")
            continue
        destaque(nome, titulo, sub, origem, recorte).save(SAIDA / f"{nome}.png")
        feitos.append(f"{nome}.png")
    print("gerados:", ", ".join(feitos))


if __name__ == "__main__":
    main()
