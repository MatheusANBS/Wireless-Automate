"""Protótipo v7 das texturas do Ressonador de Sinal: texturas conectadas da carcaça (por posição na
estrutura, como o Extreme Reactors), o vidro em 16 variantes (4 bits de vizinhança), as faces das peças
especiais (montado × solto) e os itens. Gera `anim.json` (data URIs para a página) e `prancha.png`.

Uso: python -I proto_v4.py <repo>/scripts/textures <saida>
"""
import sys, random, base64, io, json, math
from pathlib import Path
sys.path.insert(0, sys.argv[1])
from identidade import legenda, pinta, contorno, cor, mistura, olho, animacao, parado, validar, TIERS, MARCAS, LENTE
from PIL import Image, ImageDraw, ImageFont

OUT = Path(sys.argv[2])
OUT.mkdir(parents=True, exist_ok=True)
SINAL = {"4": "#FFF0EA", "3": "#FFC9B8", "2": "#F2917A", "1": "#C9604B", "0": "#8A3A2C"}
OPACO = {"4": "#F4F0EA", "3": "#D9D2C8", "2": "#B5ADA2", "1": "#8C857C", "0": "#5E5952"}
BRANCO = cor("porcelana.4")


def put(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


# ---------------------------------------------------------------------------
# Itens (os mesmos da v3)
# ---------------------------------------------------------------------------

def leg(pal):
    return {**legenda(G="grafite.1", L="latao.3", l="latao.2", m="latao.1"),
            "A": cor(pal["4"]), "a": cor(pal["3"]), "b": cor(pal["2"]), "c": cor(pal["1"]), "d": cor(pal["0"])}


BRUTO = ["................", "..........A.....", ".........Aab....", "....A....Aabc...", "...Aab...Aabc...",
         "...Aabc..Aabc...", "...Aabc.AAabc...", "...Aabc.Aaabc...", "...Aabc.Aaabc.A.", "..AAabcAAaabcAab",
         "..AaabcAaaabcAbc", "..AaabcAaaabcabc", "..bbbcdbbbbcdbcd", "..cccddcccccddd.", "...dddddddddd...", "................"]
LAPIDADO = ["................", "................", ".......AA.......", "......AAab......", ".....AAaabb.....",
            "....AAaaabbc....", "...AAaaaabbcc...", "...LLLLLllllm...", "...aaaabbbbcc...", "....aaabbbcc....",
            ".....aabbcc.....", "......abcc......", ".......cd.......", "................", "................", "................"]


def base(grade, pal):
    return contorno(pinta(grade, leg(pal)), cor("grafite.0"))


def interior_px(img):
    cont = cor("grafite.0")
    return {(x, y) for y in range(16) for x in range(16) if img.getpixel((x, y))[3] and img.getpixel((x, y)) != cont}


def cristal():
    q = []
    for k in range(10):
        img = base(BRUTO, SINAL)
        faixa = k * 3 - 6
        for (x, y) in interior_px(img):
            d = (x - y) - faixa + 6
            if 0 <= d <= 1:
                put(img, x, y, mistura(img.getpixel((x, y)), BRANCO, .7 if d == 0 else .4))
        if k in (6, 7):
            put(img, 10, 1, BRANCO)
            if k == 6:
                for dx, dy in ((1, 0), (-1, 0), (0, -1)):
                    if img.getpixel((10 + dx, 1 + dy))[3] == 0:
                        put(img, 10 + dx, 1 + dy, cor("#FFC9B8"))
        q.append(img)
    return animacao(q, 4)


def lapidado():
    q = []
    for k in range(12):
        img = base(LAPIDADO, SINAL)
        x = 3 + k
        if 3 <= x <= 12:
            put(img, x, 7, BRANCO)
        if 3 <= x - 1 <= 12:
            put(img, x - 1, 7, cor("latao.3"))
        t = (1 + math.cos(2 * math.pi * k / 12)) / 2
        for (px, py) in ((6, 4), (5, 5), (6, 5), (7, 3), (4, 6), (5, 6)):
            put(img, px, py, mistura(cor(SINAL["3"]), BRANCO, .6 * t))
        if k in (8, 9, 10):
            r = {8: 1, 9: 2, 10: 2}[k]
            c = cor("coral.3") if k < 10 else cor("#FFC9B8")
            for yy in range(16):
                for xx in range(16):
                    if img.getpixel((xx, yy))[3]:
                        continue
                    d = abs(xx - 7.5) + abs(yy - 7)
                    if 5 + r <= d < 6 + r:
                        put(img, xx, yy, c)
        q.append(img)
    return animacao(q, 3)


def opaco():
    q = []
    for k in range(8):
        img = base(BRUTO, OPACO)
        t = (1 + math.cos(2 * math.pi * k / 8)) / 2
        brasa = mistura(cor(OPACO["1"]), cor("#C9604B"), .55 * t)
        for p in ((5, 11), (10, 11), (9, 10)):
            put(img, *p, brasa)
        q.append(img)
    return animacao(q, 6)


PEDRA = ["#7F7F7F", "#747474", "#8A8A8A", "#6E6E6E", "#7A7A7A"]
ARDOSIA = ["#4D4D53", "#43434A", "#58585E", "#3C3C42", "#505056"]
CRIST = [["..A.", ".Aab", ".abc", "bbcd"], [".A.", "Aab", "bcd"], ["A..", "ab.", "bc."], [".A.", "Aab", "bcd"]]
POS = [(2, 3), (9, 8), (11, 2), (4, 11)]


def minerio(tons, semente):
    rnd = random.Random(semente)
    fundo = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            fundo.putpixel((x, y), cor(rnd.choice(tons)))
    s = leg(SINAL)
    q = []
    for k in range(12):
        img = fundo.copy()
        for i, ((gx, gy), forma) in enumerate(zip(POS, CRIST)):
            for dy, linha in enumerate(forma):
                for dx, ch in enumerate(linha):
                    if ch != ".":
                        put(img, gx + dx, gy + dy, s[ch])
            put(img, gx + len(forma[0]) - 1, gy + len(forma), cor("grafite.2"))
            if k == i * 3:
                put(img, gx + forma[0].find("A"), gy, BRANCO)
                put(img, gx + forma[0].find("A"), gy - 1, cor("#FFC9B8"))
        q.append(img)
    return animacao(q, 4)


# ---------------------------------------------------------------------------
# A carcaça: o campo de porcelana e os trilhos de grafite na borda de fora da estrutura
# ---------------------------------------------------------------------------

def campo(semente=5):
    """O campo de porcelana esmaltada: quase liso, com um grão discreto. Sem borda nenhuma:
    a borda é da estrutura, não do bloco."""
    img = Image.new("RGBA", (16, 16))
    rnd = random.Random(semente)
    for y in range(16):
        for x in range(16):
            r = rnd.random()
            put(img, x, y, cor("porcelana.4" if r < .025 else "porcelana.3"))
    # sem junta: num campo de 3×3 blocos (5×5×5) a junta voltava a ler como grade de blocos
    return img


def trilhos(img, bordas, coral=False, rebite=True):
    """Pinta a banda de 4 px nas bordas pedidas ('t','r','b','l'): trilho de grafite de 3 px e a luz
    (em cima e à esquerda) ou a sombra (embaixo e à direita) de 1 px. Com `coral`, a listra coral
    ocupa os 2 px de dentro do trilho de baixo (o rodapé da estrutura), e a banda continua com 4 px:
    o miolo da face fica no centro óptico."""
    def pinta_borda(b):
        for i in range(16):
            for d in range(0, 4):
                if b == "t":
                    x, y = i, d
                elif b == "b":
                    x, y = i, 15 - d
                elif b == "l":
                    x, y = d, i
                else:
                    x, y = 15 - d, i
                if d == 0:
                    c = "grafite.1"
                elif b == "b" and coral:
                    c = {1: "grafite.3", 2: "coral.2", 3: "coral.1"}[d]
                elif d <= 2:
                    c = "grafite.3"
                else:
                    c = "porcelana.4" if b in "tl" else "porcelana.2"
                put(img, x, y, cor(c))
    for b in "btlr":
        if b in bordas:
            pinta_borda(b)
    if rebite and len(bordas) == 2:
        # um rebite de latão (1 px) no canto de dentro do L
        put(img, 4 if "l" in bordas else 11, 4 if "t" in bordas else 11, cor("latao.1"))
    return img


def chave(bordas):
    return "".join(e for e in "tblr" if e in bordas)


def carcaca_solta():
    """O bloco fora da estrutura: um ladrilho com a própria moldura de grafite e o bisel."""
    img = campo(9)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            put(img, *p, cor("grafite.2"))
    for i in range(1, 15):
        put(img, i, 1, cor("porcelana.4")); put(img, 1, i, cor("porcelana.4"))
        put(img, i, 14, cor("porcelana.1")); put(img, 14, i, cor("porcelana.1"))
    return img


def forro():
    """O forro da câmara: grafite fosco, liso, só com um grão discreto."""
    img = Image.new("RGBA", (16, 16), cor("grafite.3"))
    rnd = random.Random(11)
    for y in range(16):
        for x in range(16):
            if rnd.random() < .04:
                put(img, x, y, cor("grafite.4"))
    return img


# ---------------------------------------------------------------------------
# O vidro: 16 variantes pelos vizinhos de vidro (t, r, b, l); o aro só onde não há vidro ao lado
# ---------------------------------------------------------------------------

def vidro(bits, montado=True):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    base_alfa = 58 if montado else 175
    for y in range(16):
        for x in range(16):
            put(img, x, y, cor("vidro.2", base_alfa))
    if montado:
        # um reflexo na diagonal, como no vidro dos tanques
        for i in range(4, 7):
            put(img, i, 10 - i, cor("vidro.3", 200))
        for i in range(10, 12):
            put(img, i, 21 - i, cor("vidro.3", 150))
    else:
        # fosco: o vidro solto é jateado, sem ver o que há atrás
        for y in range(16):
            for x in range(16):
                if (x + y) % 3 == 0:
                    put(img, x, y, cor("vidro.3", 190))
                elif (x * 7 + y * 3) % 11 == 0:
                    put(img, x, y, cor("vidro.1", 200))
    t, r, b, l = bits
    for i in range(16):
        if not t:
            put(img, i, 0, cor("grafite.1")); put(img, i, 1, cor("vidro.1", 230))
        if not b:
            put(img, i, 15, cor("grafite.1")); put(img, i, 14, cor("vidro.0", 230))
        if not l:
            put(img, 0, i, cor("grafite.1")); put(img, 1, i, cor("vidro.1", 230))
        if not r:
            put(img, 15, i, cor("grafite.1")); put(img, 14, i, cor("vidro.0", 230))
    return img


# ---------------------------------------------------------------------------
# As peças especiais: todas sobre o campo, com um aro de 1 px que as encaixa na chapa
# ---------------------------------------------------------------------------

def placa(img, x0=1, y0=1, x1=14, y1=14, fundo="grafite.3", aro="grafite.1"):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            borda = x in (x0, x1) or y in (y0, y1)
            put(img, x, y, cor(aro if borda else fundo))
    for i in range(x0 + 1, x1):
        put(img, i, y0 + 1, cor("grafite.4"))
    for i in range(y0 + 1, y1):
        put(img, x0 + 1, i, cor("grafite.4"))


def olho_par(img, tier, fase=0.0, apagado=False):
    """O Olho em 8×8 (colunas e linhas 4..11): anel de grafite com os cantos cortados, lente 6×6 com o
    brilho em cima à esquerda, centrado no centro óptico da textura (7,5; 7,5). `fase` 0..1 é o
    Pulso: um anel de luz sai do Olho e some, como no `olho` da identidade."""
    hi, mid, lo = (cor(x) for x in LENTE[tier])
    if apagado:
        hi, mid, lo = (mistura(c, cor("grafite.2"), .65) for c in (hi, mid, lo))
    g = cor("grafite.1")

    def anel(raio, c):
        for ox in range(-raio, 8 + raio):
            for oy in range(-raio, 8 + raio):
                borda = ox in (-raio, 7 + raio) or oy in (-raio, 7 + raio)
                canto = ox in (-raio, 7 + raio) and oy in (-raio, 7 + raio)
                if borda and not canto:
                    put(img, 4 + ox, 4 + oy, c)
    anel(0, g)
    for ox in range(1, 7):
        for oy in range(1, 7):
            put(img, 4 + ox, 4 + oy, lo if (ox - 3.5) + (oy - 3.5) > 0 else mid)
    put(img, 5, 5, hi); put(img, 6, 5, mistura(hi, mid, .5)); put(img, 5, 6, mistura(hi, mid, .5))
    if 0 < fase < 1:
        passo = int(fase * 4)
        if passo == 0:
            anel(1, hi)
        elif passo == 1:
            anel(2, mistura(hi, cor("grafite.3"), .5))
        elif passo == 2:
            anel(0, mistura(g, hi, .45))


def marcas_tier(img, tier, y, c):
    """As marcas do tier (1 a 4) numa linha, centradas: 2 px com vão de 2 (1 a 3 marcas) e 1 px com
    vão de 2 (4 marcas), para a largura ser sempre par."""
    n = MARCAS[tier]
    if n == 0:
        return
    larg = 2 if n < 4 else 1
    total = n * larg + (n - 1) * 2
    x0 = 8 - total // 2
    for i in range(n):
        for k in range(larg):
            put(img, x0 + i * (larg + 2) + k, y, c)


def nucleo(tier, montado=True):
    q = []
    fases = [0.0, 0.0, 0.0, 0.0, 0.1, 0.3, 0.6, 0.0] if montado else [0.0]
    for f in fases:
        img = campo(21)
        placa(img, fundo="grafite.3" if montado else "grafite.2")
        olho_par(img, tier, f, apagado=not montado)
        for p in ((2, 7), (2, 8), (13, 7), (13, 8)):
            put(img, *p, cor("grafite.4"))
        for p in ((7, 2), (8, 2)):
            put(img, *p, cor("coral.2" if montado else "grafite.4"))
        marcas_tier(img, tier, 13, cor("porcelana.4" if montado else "grafite.4"))
        q.append(img)
    return animacao(q, 5) if montado else parado(q[0])


def sintonizador(montado=True):
    q = []
    for k in range(12 if montado else 1):
        img = campo(22)
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if 5.6 < d <= 7.2:
                    put(img, x, y, cor("grafite.1") if d > 6.6 else cor("latao.2" if (x + y) % 3 else "latao.1"))
                elif d <= 5.6:
                    put(img, x, y, cor("porcelana.4" if montado else "grafite.3"))
        for a in range(0, 360, 30):
            r = math.radians(a)
            put(img, round(7.5 + 4.6 * math.cos(r)), round(7.5 + 4.6 * math.sin(r)), cor("grafite.2" if montado else "grafite.4"))
        ang = math.radians(-120 + (25 * math.sin(2 * math.pi * k / 12) if montado else -25))
        for s in range(0, 5):
            put(img, round(7.5 + s * math.cos(ang)), round(7.5 + s * math.sin(ang)), cor("coral.1" if montado else "grafite.4"))
        put(img, 7, 7, cor("grafite.1")); put(img, 8, 8, cor("grafite.1")); put(img, 7, 8, cor("grafite.1")); put(img, 8, 7, cor("grafite.1"))
        q.append(img)
    return animacao(q, 5) if montado else parado(q[0])


RAIO = [".....XX.", "....XX..", "...XX...", "..XXXX..", "....XX..", "...XX...", "..XX....", ".XX....."]


def porta_energia(montado=True):
    q = []
    for k in range(8 if montado else 1):
        img = campo(23)
        placa(img, 2, 2, 13, 13, fundo="grafite.2")
        for p in ((3, 3), (12, 3), (3, 12), (12, 12)):
            put(img, *p, cor("latao.2"))
        if montado:
            c = mistura(cor("energia.1"), cor("energia.3"), (1 + math.cos(2 * math.pi * k / 8)) / 2)
        else:
            c = cor("grafite.4")
        for dy, l in enumerate(RAIO):
            for dx, ch in enumerate(l):
                if ch == "X":
                    put(img, 4 + dx, 4 + dy, c)
        q.append(img)
    return animacao(q, 5) if montado else parado(q[0])


def porta_cristais(montado=True):
    q = []
    for k in range(8 if montado else 1):
        img = campo(24)
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if 6.4 < d <= 7.0:
                    put(img, x, y, cor("grafite.1"))
                elif 5.4 < d <= 6.4:
                    put(img, x, y, cor("grafite.3"))
                elif 4.5 < d <= 5.4:
                    put(img, x, y, cor("coral.2" if x + y < 15 else "coral.1"))
                elif d <= 4.5:
                    put(img, x, y, cor("grafite.0" if montado else "grafite.3"))
        if montado:
            t = (1 + math.cos(2 * math.pi * k / 8)) / 2
            for p in ((7, 7), (8, 7), (7, 8), (8, 8)):
                put(img, *p, mistura(cor("#C9604B"), cor("#FFC9B8"), t))
            for p in ((7, 5), (8, 10), (5, 8), (10, 7)):
                put(img, *p, mistura(cor("grafite.0"), cor("#C9604B"), .5 * t))
        else:
            for i in range(5, 11):
                put(img, i, 7, cor("grafite.1")); put(img, i, 8, cor("grafite.1"))
                put(img, 7, i, cor("grafite.1")); put(img, 8, i, cor("grafite.1"))
        q.append(img)
    return animacao(q, 5) if montado else parado(q[0])


def nucleo_dentro(tier):
    """A face de dentro do Núcleo: a lente vista pela câmara, na cor do tier."""
    img = forro()
    olho_par(img, tier)
    return img


def porta_dentro(tipo):
    img = forro()
    if tipo == "energia":
        for y in (6, 9):
            for x in range(3, 13):
                put(img, x, y, cor("latao.1" if x % 2 else "latao.0"))
    else:
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d <= 4.2:
                    put(img, x, y, cor("grafite.0"))
                elif d <= 5.2:
                    put(img, x, y, cor("coral.1"))
    return img


# ---------------------------------------------------------------------------
# Montagem
# ---------------------------------------------------------------------------

itens = {"cristal": cristal(), "cristal_lapidado": lapidado(), "cristal_opaco": opaco(),
         "minerio": minerio(PEDRA, 3), "minerio_ardosia": minerio(ARDOSIA, 7)}

blocos = {"carcaca_solta": parado(carcaca_solta()), "carcaca_campo": parado(campo(5)), "forro": parado(forro())}
for bordas in ("t", "r", "b", "l", "tl", "tr", "bl", "br"):
    blocos["carcaca_" + chave(bordas)] = parado(trilhos(campo(5), set(bordas)))
for bordas in ("b", "bl", "br"):
    blocos["carcaca_" + chave(bordas) + "_c"] = parado(trilhos(campo(5), set(bordas), coral=True))
for n in range(16):
    bits = tuple(bool(n & (1 << i)) for i in range(4))  # t, r, b, l
    blocos[f"vidro_{n}"] = parado(vidro(bits), translucido=True)
blocos["vidro_solto"] = parado(vidro((0, 0, 0, 0), montado=False), translucido=True)
blocos["sintonizador"] = sintonizador(True)
blocos["sintonizador_solto"] = sintonizador(False)
blocos["porta_energia"] = porta_energia(True)
blocos["porta_energia_solta"] = porta_energia(False)
blocos["porta_cristais"] = porta_cristais(True)
blocos["porta_cristais_solta"] = porta_cristais(False)
blocos["porta_energia_dentro"] = parado(porta_dentro("energia"))
blocos["porta_cristais_dentro"] = parado(porta_dentro("cristais"))
for t in TIERS:
    blocos[f"nucleo_{t}"] = nucleo(t, True)
    blocos[f"nucleo_{t}_solto"] = nucleo(t, False)
    blocos[f"nucleo_{t}_dentro"] = parado(nucleo_dentro(t))

dados = {}
todos = {**itens, **blocos}
for nome, sp in todos.items():
    validar(nome, sp)
    buf = io.BytesIO()
    sp.imagem.save(buf, "PNG", optimize=True)
    dados[nome] = {"src": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode(),
                   "frames": sp.quadros, "frametime": sp.frametime or 1}
(OUT / "anim.json").write_text(json.dumps(dados))


# ---------------------------------------------------------------------------
# A prancha: as texturas e as faces montadas, ampliadas sem filtro
# ---------------------------------------------------------------------------

def variante_face(cx, cy, lado, montado, coral_ok):
    """A textura de um bloco (cx, cy em −1..1, no quadro da face) pela posição na face."""
    if not montado:
        return None if cx == 0 and cy == 0 else "carcaca_solta"
    bordas = set()
    if cy == 1: bordas.add("t")
    if cy == -1: bordas.add("b")
    if cx == -1: bordas.add("l")
    if cx == 1: bordas.add("r")
    if not bordas:
        return None
    k = "carcaca_" + chave(bordas)
    if coral_ok and "b" in bordas:
        k += "_c"
    return k


def face_composta(centro, montado, lateral=True, tier="elite"):
    img = Image.new("RGBA", (48, 48), cor("porcelana.1"))
    fundo = Image.new("RGBA", (48, 48), cor("grafite.2"))
    for cy in (1, 0, -1):
        for cx in (-1, 0, 1):
            v = variante_face(cx, cy, None, montado, lateral)
            if v is None:
                v = centro if montado else centro + ("_solto" if centro in ("sintonizador", "vidro") or centro.startswith("nucleo") else "_solta")
                if centro == "vidro":
                    v = "vidro_0" if montado else "vidro_solto"
            q = todos[v].quadro(0)
            img.paste(q, ((cx + 1) * 16, (1 - cy) * 16), q)
    if not montado:
        # solto: os blocos ficam separados por 1 px, para a prancha deixar claro que são peças
        sep = Image.new("RGBA", (50, 50), cor("porcelana.1"))
        for cy in range(3):
            for cx in range(3):
                sep.paste(img.crop((cx * 16, cy * 16, cx * 16 + 16, cy * 16 + 16)), (cx * 17, cy * 17))
        return sep
    return img


ESC = 4
FUNDO = cor("porcelana.2")
TINTA = cor("grafite.1")


try:
    FONTE = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 14)
except OSError:
    FONTE = ImageFont.load_default()


def rotulo(desenho, x, y, texto):
    desenho.text((x, y), texto, fill=TINTA, font=FONTE)


linhas = [
    ("Carcaça montada: canto, barra, campo liso (a moldura é da estrutura)",
     ["carcaca_tl", "carcaca_t", "carcaca_tr", "carcaca_l", "carcaca_campo", "carcaca_r", "carcaca_bl_c", "carcaca_b_c", "carcaca_br_c", "carcaca_bl", "carcaca_b", "carcaca_br"]),
    ("Carcaça solta (ladrilho com moldura própria) e o forro da câmara",
     ["carcaca_solta", "forro"]),
    ("Vidro: 16 variantes por vizinhança (aro só onde não há vidro ao lado); o solto é fosco",
     [f"vidro_{n}" for n in range(16)] + ["vidro_solto"]),
    ("Peças montadas: Núcleo (Elite), Sintonizador, Porta de Energia, Porta de Cristais",
     ["nucleo_elite", "sintonizador", "porta_energia", "porta_cristais"]),
    ("As mesmas soltas (lente apagada, ponteiro em repouso, raio apagado, escotilha fechada)",
     ["nucleo_elite_solto", "sintonizador_solto", "porta_energia_solta", "porta_cristais_solta"]),
    ("Vistas por dentro: Núcleo (lente), Porta de Energia (grelha), Porta de Cristais (boca)",
     ["nucleo_elite_dentro", "porta_energia_dentro", "porta_cristais_dentro"]),
    ("Núcleo por tier, montado",
     [f"nucleo_{t}" for t in TIERS]),
]
def bits_vidro(u, v, vidros):
    t, r, b, l = (u, v + 1) in vidros, (u + 1, v) in vidros, (u, v - 1) in vidros, (u - 1, v) in vidros
    return int(t) | int(r) << 1 | int(b) << 2 | int(l) << 3


def face5(pecas, vidros, lateral=True):
    """Uma face 5×5 montada: u para a direita, v para cima, −2..2. `pecas` mapeia (u, v) → textura;
    `vidros` é o conjunto das posições de vidro (variante pelos vizinhos de vidro)."""
    img = Image.new("RGBA", (80, 80), cor("porcelana.1"))
    for v in range(2, -3, -1):
        for u in range(-2, 3):
            bordas = set()
            if v == 2: bordas.add("t")
            if v == -2: bordas.add("b")
            if u == -2: bordas.add("l")
            if u == 2: bordas.add("r")
            if bordas:
                k = "carcaca_" + chave(bordas) + ("_c" if lateral and "b" in bordas else "")
            elif (u, v) in pecas:
                k = pecas[(u, v)]
            elif (u, v) in vidros:
                k = f"vidro_{bits_vidro(u, v, vidros)}"
            else:
                k = "carcaca_campo"
            q = todos[k].quadro(0)
            img.paste(q, ((u + 2) * 16, (2 - v) * 16), q)
    return img


JANELA = {(u, v) for u in range(-1, 2) for v in range(-1, 2)}
FAIXA = {(u, 1) for u in range(-1, 2)}
faces = [
    ("Frente (+z): a janela de 3×3", face5({}, JANELA)),
    ("Leste (+x): o Núcleo no centro", face5({(0, 0): "nucleo_elite"}, set())),
    ("Oeste (−x): a Porta de Energia", face5({(0, 0): "porta_energia"}, set())),
    ("Fundo (−z): a Porta de Cristais", face5({(0, 0): "porta_cristais"}, set())),
    ("Tampa (+y): o Sintonizador", face5({(0, 0): "sintonizador"}, set(), lateral=False)),
    ("Montagem Faixa, leste: a faixa de vidro em cima do Núcleo", face5({(0, 0): "nucleo_elite"}, FAIXA)),
]

largura = 1180
altura = 60 + sum(16 * ESC + 40 for _ in linhas) + 2 * (80 * 3 + 44) + 20
prancha = Image.new("RGBA", (largura, altura), FUNDO)
d = ImageDraw.Draw(prancha)
rotulo(d, 16, 14, "Ressonador de Sinal · prancha v7 · texturas 16×16 ampliadas 4× (sem filtro). Fundo porcelana.2; o vidro sobre xadrez.")
y = 44
for titulo, nomes in linhas:
    rotulo(d, 16, y, titulo)
    y += 22
    x = 16
    for n in nomes:
        q = todos[n].quadro(0)
        if n.startswith("vidro"):
            fundo = Image.new("RGBA", (16, 16))
            for yy in range(16):
                for xx in range(16):
                    fundo.putpixel((xx, yy), cor("porcelana.4") if (xx // 4 + yy // 4) % 2 else cor("porcelana.1"))
            fundo.alpha_composite(q)
            q = fundo
        prancha.paste(q.resize((16 * ESC, 16 * ESC), Image.NEAREST), (x, y))
        x += 16 * ESC + 6
    y += 16 * ESC + 18
rotulo(d, 16, y, "Faces 5×5 montadas, ampliadas 3×: a moldura só na borda de fora, a listra coral no rodapé, o campo liso, o vidro com aro só onde acaba o vidro, as peças onde o jogador quiser")
y += 24
x = 16
for i, (titulo, img) in enumerate(faces):
    if i == 3:
        x = 16
        y += 80 * 3 + 44
    prancha.paste(img.resize((img.width * 3, img.height * 3), Image.NEAREST), (x, y))
    rotulo(d, x, y + 80 * 3 + 4, titulo)
    x += 80 * 3 + 40
prancha.save(OUT / "prancha.png")
print(len(json.dumps(dados)), "bytes de anim.json;", len(dados), "sprites; prancha", prancha.size)


