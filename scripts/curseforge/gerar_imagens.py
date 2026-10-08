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
    d.text((x + 4, 92 + 7 * px + 34), "Wireless items, fluids, energy, chemicals and Source.",
           fill=TEXTO, font=fonte(40))
    d.text((x + 4, 92 + 7 * px + 90), "No pipes. Light on TPS. Built for big modpacks.",
           fill=MUTED, font=fonte(32))
    return img


# ------------------------------------------------------------------ imagens de função

def destaque(nome: str, titulo: str, subtitulo: str, origem: Path, recorte: tuple[int, int, int, int],
             detalhe: tuple[Path, tuple[int, int, int, int], int] | None = None) -> Image.Image:
    """Captura com faixa de título; {@code detalhe} (captura, recorte, largura) entra no canto de baixo à direita."""
    foto = Image.open(origem).convert("RGB").crop(recorte)
    largura = 1280
    foto = foto.resize((largura - 48, round((largura - 48) * foto.height / foto.width)), Image.LANCZOS)
    if detalhe is not None:
        tela = Image.open(detalhe[0]).convert("RGB").crop(detalhe[1])
        tela = tela.resize((detalhe[2], round(detalhe[2] * tela.height / tela.width)), Image.LANCZOS)
        x, y = foto.width - tela.width - 18, foto.height - tela.height - 18
        ImageDraw.Draw(foto).rectangle([x - 3, y - 3, x + tela.width + 2, y + tela.height + 2], fill=(46, 64, 82))
        foto.paste(tela, (x, y))
    faixa = 150
    img = Image.new("RGB", (largura, faixa + foto.height + 24), FUNDO)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, largura, faixa - 1], fill=FAIXA)
    d.rectangle([0, faixa - 4, largura, faixa - 1], fill=CIANO)
    px = 6
    escreve_pixel(img, titulo, 32, 34, px, CIANO_CLARO, sombra=(8, 40, 44))
    assert d.textlength(subtitulo, font=fonte(30)) < largura - 60, f"subtítulo de {nome} passa da borda"
    d.text((34, 34 + 7 * px + 24), subtitulo, fill=TEXTO, font=fonte(30))
    # Moldura de 3 px em volta da captura.
    x0, y0 = 24, faixa + 8
    d.rectangle([x0 - 3, y0 - 3, x0 + foto.width + 2, y0 + foto.height + 2], fill=(46, 64, 82))
    img.paste(foto, (x0, y0))
    return img


# (tier, nome, cor do nome, só com o Allthemodium) na ordem do RouterTier, e as vazões padrão do ResourceType
# (os químicos dividem a vazão com os fluidos); 0 = sem limite. Alcance: o padrão do RouterTier.
TIERS = [
    ("basic", "Basic", (200, 204, 210), False),
    ("advanced", "Advanced", (226, 179, 71), False),
    ("elite", "Elite", (69, 214, 204), False),
    ("emerald", "Emerald", (47, 220, 98), False),
    ("allthemodium", "Allthemodium", (255, 139, 4), True),
    ("vibranium", "Vibranium", (38, 222, 136), True),
    ("unobtainium", "Unobtainium", (209, 82, 227), True),
    ("ultimate", "Ultimate", (164, 108, 255), False),
]
VAZOES = [
    ("Items/s", [32, 256, 2_048, 16_384, 131_072, 1_048_576, 8_388_608, 0]),
    ("Fluids (mB/s)", [2_000, 16_000, 128_000, 1_024_000, 8_192_000, 65_536_000, 524_288_000, 0]),
    ("Energy (FE/t)", [1_000, 8_000, 64_000, 512_000, 4_096_000, 32_768_000, 262_144_000, 0]),
    ("Source/s", [100, 800, 6_400, 51_200, 409_600, 3_276_800, 26_214_400, 0]),
]
ALCANCE = ["64 blocks", "512 blocks", "Dimension"] + ["All dims"] * 5


def tiers() -> Image.Image:
    """Os oito roteadores (texturas do mod, LEDs acesos) numa tabela com a vazão e o alcance de cada um."""
    largura, faixa = 1280, 150
    rotulo_w, margem = 236, 24
    coluna = (largura - rotulo_w - margem) // len(TIERS)
    img = Image.new("RGB", (largura, faixa + 680), FUNDO)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, largura, faixa - 1], fill=FAIXA)
    d.rectangle([0, faixa - 4, largura, faixa - 1], fill=CIANO)
    escreve_pixel(img, "Eight tiers", 32, 34, 6, CIANO_CLARO, sombra=(8, 40, 44))
    d.text((34, 34 + 42 + 24), "Upgrade cards raise throughput and range. Allthemodium (ATM10) adds three steps.",
           fill=TEXTO, font=fonte(28))
    x0 = rotulo_w
    # faixa "Allthemodium" sobre as três colunas do mod
    atm = [i for i, t in enumerate(TIERS) if t[3]]
    ax0, ax1 = x0 + atm[0] * coluna + 4, x0 + (atm[-1] + 1) * coluna - 4
    d.rounded_rectangle([ax0, faixa + 22, ax1, faixa + 614], radius=10, fill=(26, 24, 20))
    sprites = capa.tex.gerar()
    for i, (tier, nome, cor, _) in enumerate(TIERS):
        cx = x0 + i * coluna + coluna // 2
        sprites[f"block/router_{tier}_front"] = capa.acende_leds(sprites[f"block/router_{tier}_front"])
        arte = capa.tex.roteador_montado(tier, sprites, s=3)
        arte = arte.crop(arte.getbbox())
        escala = min(2, (coluna - 16) // arte.width)
        arte = arte.resize((arte.width * escala, arte.height * escala), Image.NEAREST)
        img.paste(arte, (cx - arte.width // 2, faixa + 190 - arte.height), arte)
        d.text((cx, faixa + 204), nome, fill=cor, font=fonte(22 if len(nome) < 11 else 19), anchor="mt")
    y = faixa + 256
    linhas = [(rotulo, [f"{v:,}" if v else "Unlimited" for v in valores]) for rotulo, valores in VAZOES]
    linhas.append(("Range", ALCANCE))
    for n, (rotulo, valores) in enumerate(linhas):
        if n % 2 == 0:
            d.rectangle([24, y - 12, largura - margem, y + 50], fill=(18, 27, 37))
            d.rectangle([ax0, y - 12, ax1, y + 50], fill=(34, 30, 24))
        d.text((34, y + 19), rotulo, fill=MUTED, font=fonte(22), anchor="lm")
        for i, valor in enumerate(valores):
            cx = x0 + i * coluna + coluna // 2
            d.text((cx, y + 19), valor, fill=TEXTO, font=fonte(20), anchor="mm")
        y += 72
    # borda e título do bloco do Allthemodium por cima das faixas
    d.rounded_rectangle([ax0, faixa + 22, ax1, faixa + 614], radius=10, outline=(120, 74, 20), width=2)
    d.text(((ax0 + ax1) // 2, faixa + 30), "Only with Allthemodium", fill=(255, 176, 80), font=fonte(20), anchor="mt")
    d.text((34, faixa + 640), "Per face and per resource type; chemicals use the fluid rate. "
           "Without Allthemodium, Emerald upgrades straight to Ultimate.", fill=MUTED, font=fonte(20))
    return img


DESTAQUES = [
    # (arquivo, título, subtítulo, captura, recorte[, (captura do detalhe, recorte, largura)]) — as
    # capturas da vitrine têm 1280x800.
    ("feature-0-overview", "Everything wireless",
     "An ore line, five storage blocks, a hall of tiers and an Ars lab, with no pipes.",
     VITRINE / "s0-casa.png", (90, 150, 1190, 770)),
    ("feature-1-network", "Connect machines without pipes",
     "Attach a router to any machine. Routers on the same network trade with each other.",
     VITRINE / "s1-fabrica.png", (0, 120, 1280, 800)),
    ("feature-2-router", "Configure every face",
     "Pick a face on the 3D model and set it per resource type, on up to five tabs.",
     VITRINE / "s3-roteador.png", (30, 90, 1250, 710)),
    ("feature-3-filters", "Tags in one click",
     "Put any item in the inspector to see and check all its tags, or search every tag in the game.",
     VITRINE / "s5b-filtro-tags.png", (270, 120, 1010, 680)),
    ("feature-8-rules", "Property rules",
     "Any enchanted item, tools under 50% durability... and your inventory lights up.",
     VITRINE / "s5c-filtro-regra.png", (270, 120, 1010, 680)),
    ("feature-15-tier-hall", "From iron to Unobtainium",
     "Every tier on its own material. Allthemodium, Vibranium and Unobtainium with ATM10.",
     VITRINE / "s15-tiers.png", (0, 200, 1280, 640)),
    ("feature-9-storage", "Storage of its own",
     "Wireless Chest, Tank, Battery, Chemical Tank and Source Tank: billions per operation.",
     VITRINE / "s9-armazenamentos.png", (0, 150, 1280, 610)),
    ("feature-10-chest", "Wireless Chest",
     "Unlimited item types in a searchable, resizable list. Keeps everything when broken.",
     VITRINE / "s10-bau.png", (360, 130, 920, 670)),
    ("feature-11-source", "Ars Nouveau Source",
     "A Source tab on every router: jars, relays and tanks trade Source wirelessly.",
     VITRINE / "s11-ars.png", (0, 100, 1280, 800)),
    ("feature-12-source-tank", "Wireless Source Tank",
     "A slim jar that shows its level. Sourcelinks fill it, Ars machines draw from it.",
     VITRINE / "s12-tanques.png", (200, 170, 1080, 720), (VITRINE / "s12b-tanque-tela.png", (418, 258, 862, 542), 430)),
    ("feature-14-toolkit", "The toolkit",
     "Linker, Configurator, Tablet, Filter Card, Upgrade Cards and the guide book.",
     VITRINE / "s14-vitrine.png", (100, 120, 1180, 660)),
    ("feature-5-area", "Link whole areas at once",
     "The Linker and the Configurator work on a marked area: one click for a whole factory.",
     VITRINE / "s2-vinculador-area.png", (0, 120, 1280, 800)),
    ("feature-13-linker", "Linker by type",
     "Check the tabs to link: only Items, or Energy and Source, or everything at once.",
     VITRINE / "s13-vinculador.png", (170, 90, 1110, 710)),
    ("feature-6-tablet", "Network Tablet",
     "Every router, from anywhere: live stats per resource type, a map and groups.",
     VITRINE / "s6b-tablet-estatisticas.png", (70, 90, 1210, 710)),
    ("feature-7-guide", "Built-in guide book",
     "Every item explained in game, with 3D scenes and recipes. English and Portuguese.",
     E2E / "guia-getting-started.png", (300, 40, 1270, 568)),
]


def main() -> None:
    SAIDA.mkdir(parents=True, exist_ok=True)
    banner().save(SAIDA / "banner.png")
    tiers().save(SAIDA / "feature-4-tiers.png")
    feitos = ["banner.png", "feature-4-tiers.png"]
    for nome, titulo, sub, origem, recorte, *detalhe in DESTAQUES:
        faltam = [p for p in [origem] + [d[0] for d in detalhe] if not p.exists()]
        if faltam:
            print(f"pulando {nome}: falta {faltam[0].relative_to(RAIZ)}")
            continue
        destaque(nome, titulo, sub, origem, recorte, *detalhe).save(SAIDA / f"{nome}.png")
        feitos.append(f"{nome}.png")
    print("gerados:", ", ".join(feitos))


if __name__ == "__main__":
    main()
