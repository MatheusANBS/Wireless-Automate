"""Gera o banner e as imagens da descrição do projeto no CurseForge.

    WA_SHOWCASE=run/showcase ./gradlew runClient    # fotos da vitrine (DevEndToEnd, modo WA_SHOWCASE), 3840x2400
    python scripts/curseforge/gerar_imagens.py

O CurseForge não fixa tamanho para imagens da descrição; a regra é mostrar o mod como ele é no
jogo. Por isso as imagens de funções são capturas reais (a vitrine monta uma fábrica de exemplo)
com uma faixa de título. O banner tem 1600x400 (4:1), legível na coluna da descrição.
Textos em inglês: o público do CurseForge é internacional.
Saída: docs/curseforge/banner.png e docs/curseforge/feature-*.png (com 256 cores quando passa dos 2 MB do CurseForge).
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

# Identidade "Porcelana e Sinal" (docs/identidade-visual.md): fundo de porcelana, tinta de grafite e a
# listra coral; as fotos levam moldura de grafite.
FUNDO = capa.PORCELANA[3]
FAIXA = capa.PORCELANA[4]
LISTRA = capa.CORAL[2]
TITULO = capa.GRAFITE[1]
SOMBRA_TITULO = capa.PORCELANA[2]
TEXTO = capa.GRAFITE[3]
MUTED = capa.PORCELANA[0]
MOLDURA = capa.GRAFITE[2]
ZEBRA = capa.PORCELANA[2]

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


def banner(paleta: str = capa.PALETA_ATIVA) -> Image.Image:
    """1600x400, a composição do primeiro banner: o mesmo fundo da capa (degradê com o centro atrás do
    roteador e grade de pontos), a arte da capa sem o fundo à esquerda, o título em pixel e duas linhas."""
    pal = capa.PALETAS[paleta]
    w, h = 1600, 400
    img = capa.fundo(pal, w // 4, h // 4, cx=55, cy=45).resize((w, h), Image.NEAREST).convert("RGB")
    arte = capa.arte(paleta, lado=360)
    img.paste(arte, (40, 20), arte)
    titulo = "Wireless Automate"
    px = 10
    x = 450
    assert x + largura_pixel(titulo, px) < w - 40, "título passa da borda"
    escreve_pixel(img, titulo, x, 92, px, pal["titulo"], sombra=pal["titulo_sombra"])
    d = ImageDraw.Draw(img)
    d.text((x + 4, 92 + 7 * px + 34), "Wireless items, fluids, energy, chemicals and Source.",
           fill=pal["texto"], font=fonte(40))
    d.text((x + 4, 92 + 7 * px + 90), "No pipes. Light on TPS. Built for big modpacks.",
           fill=pal["muted"], font=fonte(32))
    return img


# ------------------------------------------------------------------ imagens de função

LARGURA = 1920  # largura das imagens de função; o layout foi desenhado em 1280 e cresce por E
E = LARGURA / 1280


def u(v: float) -> int:
    """Medida do layout de 1280 px na largura final."""
    return round(v * E)


def recorta(origem: Path, recorte: tuple[int, int, int, int]) -> Image.Image:
    """Recorte em coordenadas de 1280x800; a vitrine fotografa em 1280x800 vezes WA_SHOWCASE_SCALE."""
    foto = Image.open(origem).convert("RGB")
    f = foto.width / 1280
    return foto.crop(tuple(round(v * f) for v in recorte))


def ajusta(foto: Image.Image, largura: int) -> Image.Image:
    """Leva a foto à largura sem borrar: reduz com LANCZOS; para ampliar, primeiro dobra os pixels
    (NEAREST, inteiro, as texturas e a fonte do jogo são pixel art) e depois reduz."""
    if foto.width < largura:
        n = -(-largura // foto.width)
        foto = foto.resize((foto.width * n, foto.height * n), Image.NEAREST)
    if foto.width == largura:
        return foto
    return foto.resize((largura, round(largura * foto.height / foto.width)), Image.LANCZOS)


def destaque(nome: str, titulo: str, subtitulo: str, origem: Path, recorte: tuple[int, int, int, int],
             detalhe: tuple[Path, tuple[int, int, int, int], int] | None = None) -> Image.Image:
    """Captura com faixa de título; {@code detalhe} (captura, recorte, largura em 1280) entra no canto de baixo
    à direita. Uma captura um pouco menor que a área fica no tamanho nativo, centrada."""
    largura = LARGURA
    foto = recorta(origem, recorte)
    area = largura - u(48)
    if foto.width > area or foto.width < area * 0.85:
        foto = ajusta(foto, area)
    if detalhe is not None:
        tela = ajusta(recorta(detalhe[0], detalhe[1]), u(detalhe[2]))
        x, y = foto.width - tela.width - u(18), foto.height - tela.height - u(18)
        b = u(3)
        ImageDraw.Draw(foto).rectangle([x - b, y - b, x + tela.width + b - 1, y + tela.height + b - 1],
                                       fill=MOLDURA)
        foto.paste(tela, (x, y))
    faixa = u(150)
    img = Image.new("RGB", (largura, faixa + foto.height + u(24)), FUNDO)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, largura, faixa - 1], fill=FAIXA)
    d.rectangle([0, faixa - u(4), largura, faixa - 1], fill=LISTRA)
    px = u(6)
    escreve_pixel(img, titulo, u(32), u(34), px, TITULO, sombra=SOMBRA_TITULO)
    assert d.textlength(subtitulo, font=fonte(u(30))) < largura - u(60), f"subtítulo de {nome} passa da borda"
    d.text((u(34), u(34) + 7 * px + u(24)), subtitulo, fill=TEXTO, font=fonte(u(30)))
    # Moldura de 3 px (no layout de 1280) em volta da captura, centrada.
    x0, y0 = (largura - foto.width) // 2, faixa + u(8)
    b = u(3)
    d.rectangle([x0 - b, y0 - b, x0 + foto.width + b - 1, y0 + foto.height + b - 1], fill=MOLDURA)
    img.paste(foto, (x0, y0))
    return img


# (tier, nome, cor do nome, só com o Allthemodium) na ordem do RouterTier, e as vazões padrão do ResourceType
# (os químicos dividem a vazão com os fluidos); 0 = sem limite. Alcance: o padrão do RouterTier.
TIERS = [
    ("basic", "Basic", (74, 70, 80), False),
    ("advanced", "Advanced", (170, 112, 10), False),
    ("elite", "Elite", (16, 130, 122), False),
    ("emerald", "Emerald", (24, 140, 62), False),
    ("allthemodium", "Allthemodium", (205, 98, 0), True),
    ("vibranium", "Vibranium", (16, 138, 86), True),
    ("unobtainium", "Unobtainium", (140, 44, 160), True),
    ("ultimate", "Ultimate", (96, 56, 190), False),
]
# Os oito roteadores no salão dos tiers (s15-tiers.png), da esquerda para a direita: centro de cada um em
# coordenadas de 1280x800 e o recorte em volta (o cartão flutuando em cima e o bloco do material embaixo).
TIER_HALL_X = [309, 403, 497, 592, 687, 782, 877, 972]
TIER_HALL_Y = (338, 430)
TIER_HALL_MEIA_LARGURA = 37
VAZOES = [
    ("Items/s", [32, 256, 2_048, 16_384, 131_072, 1_048_576, 8_388_608, 0]),
    ("Fluids (mB/s)", [2_000, 16_000, 128_000, 1_024_000, 8_192_000, 65_536_000, 524_288_000, 0]),
    ("Energy (FE/t)", [1_000, 8_000, 64_000, 512_000, 4_096_000, 32_768_000, 262_144_000, 0]),
    ("Source/s", [100, 800, 6_400, 51_200, 409_600, 3_276_800, 26_214_400, 0]),
]
ALCANCE = ["64 blocks", "512 blocks", "Dimension"] + ["All dims"] * 5


def tiers() -> Image.Image:
    """Os oito roteadores (texturas do mod, LEDs acesos) numa tabela com a vazão e o alcance de cada um."""
    largura, faixa = LARGURA, u(150)
    rotulo_w, margem = u(236), u(24)
    coluna = (largura - rotulo_w - margem) // len(TIERS)
    img = Image.new("RGB", (largura, faixa + u(680)), FUNDO)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, largura, faixa - 1], fill=FAIXA)
    d.rectangle([0, faixa - u(4), largura, faixa - 1], fill=LISTRA)
    escreve_pixel(img, "Eight tiers", u(32), u(34), u(6), TITULO, sombra=SOMBRA_TITULO)
    d.text((u(34), u(34 + 42 + 24)), "Upgrade cards raise throughput and range. Allthemodium (ATM10) adds three steps.",
           fill=TEXTO, font=fonte(u(28)))
    x0 = rotulo_w
    # faixa "Allthemodium" sobre as três colunas do mod
    atm = [i for i, t in enumerate(TIERS) if t[3]]
    # a borda fica um pouco fora das colunas: "Allthemodium" ocupa quase a coluna inteira
    ax0, ax1 = x0 + atm[0] * coluna - u(8), x0 + (atm[-1] + 1) * coluna + u(8)
    d.rounded_rectangle([ax0, faixa + u(22), ax1, faixa + u(614)], radius=u(10), fill=(250, 232, 212))
    for i, (tier, nome, cor, _) in enumerate(TIERS):
        cx = x0 + i * coluna + coluna // 2
        hx, (hy0, hy1) = TIER_HALL_X[i], TIER_HALL_Y
        arte = recorta(VITRINE / "s15-tiers.png",
                       (hx - TIER_HALL_MEIA_LARGURA, hy0, hx + TIER_HALL_MEIA_LARGURA, hy1))
        arte = ajusta(arte, coluna - u(20))
        b = u(2)
        ax, ay = cx - arte.width // 2, faixa + u(194) - arte.height
        d.rectangle([ax - b, ay - b, ax + arte.width + b - 1, ay + arte.height + b - 1], fill=MOLDURA)
        img.paste(arte, (ax, ay))
        d.text((cx, faixa + u(206)), nome, fill=cor, font=fonte(u(22 if len(nome) < 11 else 18)), anchor="mt")
    y = faixa + u(256)
    linhas = [(rotulo, [f"{v:,}" if v else "Unlimited" for v in valores]) for rotulo, valores in VAZOES]
    linhas.append(("Range", ALCANCE))
    for n, (rotulo, valores) in enumerate(linhas):
        if n % 2 == 0:
            d.rectangle([u(24), y - u(12), largura - margem, y + u(50)], fill=ZEBRA)
            d.rectangle([ax0, y - u(12), ax1, y + u(50)], fill=(236, 214, 190))
        d.text((u(34), y + u(19)), rotulo, fill=MUTED, font=fonte(u(22)), anchor="lm")
        for i, valor in enumerate(valores):
            cx = x0 + i * coluna + coluna // 2
            d.text((cx, y + u(19)), valor, fill=TEXTO, font=fonte(u(20)), anchor="mm")
        y += u(72)
    # borda e título do bloco do Allthemodium por cima das faixas
    d.rounded_rectangle([ax0, faixa + u(22), ax1, faixa + u(614)], radius=u(10), outline=(205, 98, 0), width=u(2))
    d.text(((ax0 + ax1) // 2, faixa + u(30)), "Only with Allthemodium", fill=(170, 80, 0), font=fonte(u(20)),
           anchor="mt")
    d.text((u(34), faixa + u(640)), "Per face and per resource type; chemicals use the fluid rate. "
           "Without Allthemodium, Emerald upgrades straight to Ultimate.", fill=MUTED, font=fonte(u(20)))
    return img


DESTAQUES = [
    # (arquivo, título, subtítulo, captura, recorte[, (captura do detalhe, recorte, largura)]) — recortes
    # em coordenadas de 1280x800, qualquer que seja o tamanho da captura.
    ("feature-0-overview", "Everything wireless",
     "An ore line, five storage blocks, a hall of tiers and an Ars lab, with no pipes.",
     VITRINE / "s0-casa.png", (90, 150, 1190, 770)),
    ("feature-1-network", "Connect machines without pipes",
     "Attach a router to any machine. Routers on the same network trade with each other.",
     VITRINE / "s1-fabrica.png", (0, 120, 1280, 800)),
    ("feature-2-router", "Configure every face",
     "Pick a face on the 3D model and set it per resource type, on up to five tabs.",
     VITRINE / "s3-roteador.png", (20, 80, 1260, 720)),
    ("feature-3-filters", "Tags in one click",
     "Put any item in the inspector to see and check all its tags, or search every tag in the game.",
     VITRINE / "s5b-filtro-tags.png", (272, 118, 1008, 680)),
    ("feature-8-rules", "Property rules",
     "Any enchanted item, tools under 50% durability... and your inventory lights up.",
     VITRINE / "s5c-filtro-regra.png", (272, 118, 1008, 680)),
    ("feature-15-tier-hall", "From iron to Unobtainium",
     "Every tier on its own material. Allthemodium, Vibranium and Unobtainium with ATM10.",
     VITRINE / "s15-tiers.png", (0, 200, 1280, 640)),
    ("feature-9-storage", "Storage of its own",
     "Wireless Chest, Tank, Battery, Chemical Tank and Source Tank: billions per operation.",
     VITRINE / "s9-armazenamentos.png", (0, 150, 1280, 610)),
    ("feature-10-chest", "Wireless Chest",
     "Unlimited item types in a searchable, resizable list. Keeps everything when broken.",
     VITRINE / "s10-bau.png", (330, 110, 940, 690)),
    ("feature-11-source", "Ars Nouveau Source",
     "A Source tab on every router: jars, relays and tanks trade Source wirelessly.",
     VITRINE / "s11-ars.png", (0, 100, 1280, 800)),
    ("feature-12-source-tank", "Wireless Source Tank",
     "A slim jar that shows its level. Sourcelinks fill it, Ars machines draw from it.",
     VITRINE / "s12-tanques.png", (200, 170, 1080, 720), (VITRINE / "s12b-tanque-tela.png", (398, 260, 882, 540), 430)),
    ("feature-14-toolkit", "The toolkit",
     "Linker, Configurator, Tablet, Filter Card, Upgrade Cards and the guide book.",
     VITRINE / "s14-vitrine.png", (100, 120, 1180, 660)),
    ("feature-5-area", "Link whole areas at once",
     "The Linker and the Configurator work on a marked area: one click for a whole factory.",
     VITRINE / "s2-vinculador-area.png", (0, 120, 1280, 800)),
    ("feature-13-linker", "Linker by type",
     "Check the tabs to link: only Items, or Energy and Source, or everything at once.",
     VITRINE / "s13-vinculador.png", (164, 84, 1116, 716)),
    ("feature-6-tablet", "Network Tablet",
     "Every router, from anywhere: live stats per resource type, a map and groups.",
     VITRINE / "s6b-tablet-estatisticas.png", (60, 80, 1222, 720)),
    ("feature-7-guide", "Built-in guide book",
     "Every item explained in game, with 3D scenes and recipes. English and Portuguese.",
     VITRINE / "s8-guia.png", (296, 52, 1266, 658)),
]


LIMITE = 2_000_000  # bytes: o CurseForge recusa imagens acima de 2 MB


def salva(img: Image.Image, nome: str) -> str:
    """Salva em PNG. Se passar do limite do CurseForge, reduz para 256 cores (corte mediano), como o próprio
    CurseForge faz com toda PNG que recebe; JPEG ele recomprime com qualidade baixa, então fica só como último
    recurso. Apaga a versão no outro formato, para não sobrar arquivo velho."""
    png, jpg = SAIDA / f"{nome}.png", SAIDA / f"{nome}.jpg"
    img.save(png, optimize=True)
    if png.stat().st_size > LIMITE:
        img.quantize(256, method=Image.Quantize.MEDIANCUT).save(png, optimize=True)
    if png.stat().st_size <= LIMITE:
        jpg.unlink(missing_ok=True)
        return png.name
    png.unlink()
    for qualidade in range(98, 79, -2):
        img.save(jpg, quality=qualidade, subsampling=0, optimize=True)
        if jpg.stat().st_size <= LIMITE:
            return jpg.name
    raise SystemExit(f"{nome} passa de {LIMITE} bytes mesmo em JPEG")


def main() -> None:
    SAIDA.mkdir(parents=True, exist_ok=True)
    feitos = [salva(banner(), "banner"), salva(tiers(), "feature-4-tiers")]
    for nome, titulo, sub, origem, recorte, *detalhe in DESTAQUES:
        faltam = [p for p in [origem] + [d[0] for d in detalhe] if not p.exists()]
        if faltam:
            print(f"pulando {nome}: falta {faltam[0].relative_to(RAIZ)}")
            continue
        feitos.append(salva(destaque(nome, titulo, sub, origem, recorte, *detalhe), nome))
    print("gerados:", ", ".join(feitos))


if __name__ == "__main__":
    main()
