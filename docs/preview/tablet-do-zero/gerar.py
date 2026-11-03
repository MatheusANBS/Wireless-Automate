"""Desenha as telas propostas do Tablet do zero como o jogo desenha: a fonte bitmap do Minecraft (ascii, accented e
nonlatin_european, lidas do jar de recursos do cliente no build), as texturas de verdade dos itens e blocos (cubo
isométrico para bloco) e as cores do GuiPalette do mod. Cada estado sai num PNG em 3x, claro e escuro.

Uso: python -I docs/preview/tablet-do-zero/gerar.py [jar-de-recursos]
"""
import colorsys
import io
import json
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parents[3]
SAIDA = Path(__file__).resolve().parent
JAR = Path(sys.argv[1]) if len(sys.argv) > 1 else RAIZ / "build/moddev/artifacts/neoforge-21.1.248-client-extra-aka-minecraft-resources.jar"
MOD_TEX = RAIZ / "src/main/resources/assets/wirelessautomate/textures"
S = 3  # pixels de tela por pixel de GUI
W, H = 420, 280

CLARO = dict(panel=0xF2EBDD, hi=0xFFFBF3, inset=0xDDD3BF, lo=0xBDB19A, hover=0xF8F3E8, fg=0x2A2730, muted=0x7A7366,
             dis=0xB5AC9D, acc=0xF0603E, ok=0x2F9D5C, warn=0xC97E12, bad=0xB8304F,
             frame=0x8C826F, bev_hi=0xFFFFFF, bev_lo=0xA89C84, face=0xFAF5EA, slot=0xD6CCB6, slot_hi=0xFFFBF3,
             slot_lo=0x9C917A, sel=0x2A2730, sel_ink=0xF2EBDD, prim=0xF0603E, prim_ink=0xFFFFFF, scrim=0xF2EBDD)
ESCURO = dict(panel=0x26232B, hi=0x312D37, inset=0x1C1A20, lo=0x4A4550, hover=0x3A3640, fg=0xECE5D6, muted=0xA79F90,
              dis=0x625C66, acc=0xF26A48, ok=0x3DB56E, warn=0xE39A2E, bad=0xE5577A,
              frame=0x0F0E12, bev_hi=0x4A4550, bev_lo=0x16151A, face=0x332F39, slot=0x3A3A44, slot_hi=0x4E4C58,
              slot_lo=0x151419, sel=0x5A5566, sel_ink=0xF4EEE2, prim=0xD9573A, prim_ink=0xFFFFFF, scrim=0x26232B)
MODO = {"ext": (0x2F6FD6, "Extrai", "port_extract"), "ins": (0xE8742B, "Insere", "port_insert"),
        "both": (0x2F9D5C, "Armazém", "port_both")}
TIPO = {"item": (0xB57A3A, "Itens", "itens/s"), "fluid": (0x2F6FD6, "Fluidos", "mB/s"),
        "energy": (0xD9931A, "Energia", "FE/t"), "chem": (0x5E9A2B, "Químicos", "mB/s"),
        "source": (0x8E4FC9, "Source", "/s")}
ARQ_TIPO = {"item": "item", "fluid": "fluid", "energy": "energy", "chem": "chemical", "source": "source"}

jar = zipfile.ZipFile(JAR)


def rgb(c, a=255):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, a)


def png_jar(caminho):
    return Image.open(io.BytesIO(jar.read(caminho))).convert("RGBA")


# ------------------------------------------------------------------ fonte

class Fonte:
    """A fonte padrão do jogo: os provedores na ordem do include/default.json (o primeiro que tem o caractere vale)."""

    def __init__(self):
        dados = json.loads(jar.read("assets/minecraft/font/include/default.json"))
        self.glifos = {}
        for prov in reversed(dados["providers"]):
            if prov["type"] != "bitmap":
                continue
            img = png_jar("assets/minecraft/textures/" + prov["file"].split(":")[1])
            linhas = prov["chars"]
            ch = img.height // len(linhas)
            cw = img.width // len(linhas[0])
            altura = prov.get("height", 8)
            escala = altura / ch
            subida = prov["ascent"]
            for r, linha in enumerate(linhas):
                for c, car in enumerate(linha):
                    if car == "\u0000":
                        continue
                    cel = img.crop((c * cw, r * ch, c * cw + cw, r * ch + ch))
                    larg = 0
                    for x in range(cw - 1, -1, -1):
                        if any(cel.getpixel((x, y))[3] > 0 for y in range(ch)):
                            larg = x + 1
                            break
                    avanco = int(0.5 + larg * escala) + 1
                    self.glifos[car] = (cel, escala, subida, avanco)
        self.glifos[" "] = (None, 1, 7, 4)

    def largura(self, texto):
        return sum(self.glifos.get(c, self.glifos["?"])[3] for c in texto)


FONTE = Fonte()


# ------------------------------------------------------------------ ícones

def cubo(topo, frente, lado):
    """Um bloco como o jogo o mostra num slot: cubo isométrico 16x16 (em 3x), luz de cima, frente à esquerda."""
    n = 16 * S
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = out.load()

    def face(tex, o, eu, ev, luz):
        t = tex.load()
        # o: canto (u=0,v=0); eu, ev: vetores (em pixels de GUI) para u e v = 1
        det = eu[0] * ev[1] - eu[1] * ev[0]
        for y in range(n):
            for x in range(n):
                gx, gy = (x + 0.5) / S - o[0], (y + 0.5) / S - o[1]
                u = (gx * ev[1] - gy * ev[0]) / det
                v = (eu[0] * gy - eu[1] * gx) / det
                if 0 <= u < 1 and 0 <= v < 1:
                    r, g, b, a = t[int(u * 16), int(v * 16)]
                    if a:
                        px[x, y] = (int(r * luz), int(g * luz), int(b * luz), 255)

    topo, frente, lado = (png_jar(f"assets/minecraft/textures/block/{f}.png").crop((0, 0, 16, 16)) for f in (topo, frente, lado))
    face(topo, (8, 0.5), (7, 3.75), (-7, 3.75), 1.0)
    face(frente, (1, 4.25), (7, 3.75), (0, 7.5), 0.8)
    face(lado, (8, 8), (7, -3.75), (0, 7.5), 0.6)
    return out


def item(nome):
    return png_jar(f"assets/minecraft/textures/item/{nome}.png").crop((0, 0, 16, 16)).resize((16 * S, 16 * S), Image.NEAREST)


def mod_tex(rel):
    return Image.open(MOD_TEX / rel).convert("RGBA").crop((0, 0, 16, 16)).resize((16 * S, 16 * S), Image.NEAREST)


ICONE = {
    "fornalha": cubo("furnace_top", "furnace_front", "furnace_side"),
    "alto": cubo("blast_furnace_top", "blast_furnace_front", "blast_furnace_side"),
    "defumador": cubo("smoker_top", "smoker_front", "smoker_side"),
    "barril": cubo("barrel_top", "barrel_side", "barrel_side"),
    "distribuidor": cubo("furnace_top", "dispenser_front", "furnace_side"),
    "forja": cubo("smithing_table_top", "smithing_table_front", "smithing_table_side"),
    "redstone_bloco": cubo("redstone_block", "redstone_block", "redstone_block"),
    "ferro_bloco": cubo("iron_block", "iron_block", "iron_block"),
    "solar": cubo("daylight_detector_top", "daylight_detector_side", "daylight_detector_side"),
}
for nome in ("iron_ingot", "redstone", "coal", "blaze_powder", "ender_pearl", "lava_bucket", "water_bucket", "diamond",
             "emerald", "gold_ingot", "copper_ingot", "glowstone_dust", "iron_pickaxe", "amethyst_shard", "nether_star",
             "raw_iron", "wheat", "bone_meal", "experience_bottle"):
    ICONE[nome] = item(nome)
for t, arq in ARQ_TIPO.items():
    # o desenho do tipo ocupa 9x9 no canto da textura: centrado no quadro de 16, como os outros ícones
    bruto = Image.open(MOD_TEX / f"gui/type/{arq}.png").convert("RGBA").crop((0, 0, 9, 9))
    quadro = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    quadro.paste(bruto, (4, 4))
    ICONE["tipo_" + t] = quadro.resize((16 * S, 16 * S), Image.NEAREST)
for m in MODO.values():
    ICONE[m[2]] = mod_tex(f"gui/{m[2]}.png")


# ------------------------------------------------------------------ tela

class Tela:
    def __init__(self, tema):
        self.c = tema
        self.img = Image.new("RGBA", (W * S, H * S), rgb(tema["panel"]))
        self.d = ImageDraw.Draw(self.img)

    def ret(self, x, y, w, h, cor, a=255):
        if w <= 0 or h <= 0:
            return
        cor = self.c.get(cor, cor) if isinstance(cor, str) else cor
        if a < 255:
            camada = Image.new("RGBA", (w * S, h * S), rgb(cor, a))
            self.img.alpha_composite(camada, (x * S, y * S))
        else:
            self.d.rectangle((x * S, y * S, (x + w) * S - 1, (y + h) * S - 1), fill=rgb(cor))

    def borda(self, x, y, w, h, cor):
        self.ret(x, y, w, 1, cor)
        self.ret(x, y + h - 1, w, 1, cor)
        self.ret(x, y, 1, h, cor)
        self.ret(x + w - 1, y, 1, h, cor)

    def texto(self, x, y, s, cor="fg", negrito=False):
        cor = rgb(self.c.get(cor, cor) if isinstance(cor, str) else cor)
        for car in s:
            cel, esc, sub, av = FONTE.glifos.get(car, FONTE.glifos["?"])
            if cel is not None:
                topo = y + 7 - sub
                for gy in range(cel.height):
                    for gx in range(cel.width):
                        if cel.getpixel((gx, gy))[3] > 0:
                            for dx in ((0, 1) if negrito else (0,)):
                                x0 = (x + dx) * S + int(gx * esc * S)
                                y0 = topo * S + int(gy * esc * S)
                                self.d.rectangle((x0, y0, x0 + max(1, int(esc * S)) - 1, y0 + max(1, int(esc * S)) - 1), fill=cor)
            x += av + (1 if negrito else 0)
        return x

    def larg(self, s, negrito=False):
        return FONTE.largura(s) + (len(s) if negrito else 0)

    def direita(self, xd, y, s, cor="fg"):
        self.texto(xd - self.larg(s) + 1, y, s, cor)

    def icone(self, x, y, nome):
        self.img.alpha_composite(ICONE[nome], (x * S, y * S))

    def relevo(self, x, y, w, h, face, claro, escuro):
        self.ret(x, y, w, h, "frame")
        self.ret(x + 1, y + 1, w - 2, h - 2, face)
        self.ret(x + 1, y + 1, w - 2, 1, claro)
        self.ret(x + 1, y + 1, 1, h - 2, claro)
        self.ret(x + 1, y + h - 2, w - 2, 1, escuro)
        self.ret(x + w - 2, y + 1, 1, h - 2, escuro)

    def botao(self, x, y, w, s, primario=False, h=16):
        if primario:
            p = self.c["prim"]
            self.relevo(x, y, w, h, p, misturar(p, 0xFFFFFF, 0.3), sombra(p, 0.75))
        else:
            self.relevo(x, y, w, h, "face", "bev_hi", "bev_lo")
        self.texto(x + (w - self.larg(s)) // 2, y + (h - 8) // 2 + 1, s, "prim_ink" if primario else "fg")

    def largura_botao(self, s):
        return self.larg(s) + 16

    def campo(self, x, y, w, h=16, foco=False):
        self.ret(x, y, w, h, "fg" if foco else "frame")
        self.ret(x + 1, y + 1, w - 2, h - 2, "inset")
        self.ret(x + 1, y + 1, w - 2, 1, "slot_lo")
        self.ret(x + 1, y + 1, 1, h - 2, "slot_lo")

    def slot(self, x, y, n=18, realce=False):
        self.ret(x, y, n, n, "slot")
        self.ret(x, y, n - 1, 1, "slot_lo")
        self.ret(x, y, 1, n - 1, "slot_lo")
        self.ret(x + 1, y + n - 1, n - 1, 1, "slot_hi")
        self.ret(x + n - 1, y + 1, 1, n - 1, "slot_hi")
        if realce:
            self.borda(x + 1, y + 1, n - 2, n - 2, "fg")

    def segmentos(self, x, y, rotulos, ativo, icones=None, h=18):
        """Escolha entre opções: o escolhido claro e emoldurado, os outros planos e apagados."""
        partes = []
        for i, rot in enumerate(rotulos):
            ic = icones[i] if icones else None
            w = self.larg(rot) + 12 + (17 if ic else 0)
            partes.append((x, w, rot, ic, i == ativo))
            x += w - 1
        # o escolhido por último, para a moldura dele não ser coberta pela borda do vizinho
        for px, w, rot, ic, on in sorted(partes, key=lambda p: p[4]):
            self.ret(px, y, w, h, "fg" if on else "frame")
            self.ret(px + 1, y + 1, w - 2, h - 2, "hi" if on else "face")
            tx = px + 6
            if ic:
                self.icone(tx - 2, y + (h - 16) // 2, ic)
                tx += 17
            self.texto(tx, y + (h - 8) // 2 + 1, rot, "fg" if on else "muted")
        return x

    def link(self, x, y, s):
        fim = self.texto(x, y, s, "fg")
        self.ret(x, y + 9, fim - x - 1, 1, "fg")
        return fim

    def quadradinho(self, x, y, cor, n=7):
        self.ret(x, y, n, n, 0x000000 if False else cor)
        self.borda(x, y, n, n, sombra(cor))

    def salvar(self, nome):
        self.img.save(SAIDA / f"{nome}.png")


def misturar(a, b, f):
    ca = [(a >> k) & 255 for k in (16, 8, 0)]
    cb = [(b >> k) & 255 for k in (16, 8, 0)]
    r, g, bl = (int(x + (y - x) * f) for x, y in zip(ca, cb))
    return (r << 16) | (g << 8) | bl


def sombra(cor, f=0.7):
    r, g, b = (cor >> 16) & 255, (cor >> 8) & 255, cor & 255
    return (int(r * f) << 16) | (int(g * f) << 8) | int(b * f)


X0 = 158  # borda do conteúdo do painel direito: 8 px depois da divisória, como a lista depois do relevo
X1 = 411  # borda direita, 8 px antes do relevo
BTN_FIM = 403  # onde terminam os botões dentro de um cartão


def moldura(t, aba):
    t.relevo(0, 0, W, H, "panel", "bev_hi", "bev_lo")
    t.texto(8, 10, "Tablet", "fg", negrito=True)
    x = W - 12
    for nome in ("Fluxos", "Máquina", "Redes"):
        w = t.larg(nome)
        x -= w
        ativo = nome == aba
        t.texto(x, 10, nome, "fg" if ativo else "muted")
        if ativo:
            t.ret(x - 1, 21, w + 1, 2, "acc")
        x -= 16
    t.ret(2, 23, W - 4, 1, "inset")


def coluna_esquerda(t):
    t.ret(150, 24, 1, H - 26, "inset")


def rodape(t, x0=151):
    t.ret(x0, H - 27, W - x0 - 2, 1, "inset")


def realce_lista(t, x, y, w, h):
    t.ret(x, y, w, h, "hi")
    t.ret(x, y, 2, h, "fg")


def seta(t, x, y, cor="muted"):
    for i in range(3):
        t.ret(x + i, y + i, 5 - 2 * i, 1, cor)


def chevron(t, x, y, aberto=False, cor="muted"):
    for i in range(4):
        if aberto:
            t.ret(x + i, y + i, 1, 1, cor)
            t.ret(x + 6 - i, y + i, 1, 1, cor)
        else:
            t.ret(x + i, y + i, 1, 1, cor)
            t.ret(x + i, y + 6 - i, 1, 1, cor)


def lapis(t, x, y, cor="muted"):
    for i in range(6):
        t.ret(x + 5 - i, y + i, 2, 1, cor)
    t.ret(x, y + 6, 2, 1, cor)


def ponto(t, x, y, cor):
    t.ret(x, y, 5, 5, cor)


def cabecalho(t, desenhar_icone, titulo, sub, estado=None, editavel=False):
    """A faixa de cima do painel direito, igual em todas as telas."""
    desenhar_icone(X0, 30)
    x = t.texto(X0 + 21, 30, titulo, "fg", negrito=True)
    if editavel:
        lapis(t, x + 4, 31)
    t.texto(X0 + 21, 40, sub, "muted")
    if estado:
        cor, rot = estado
        ponto(t, X1 - t.larg(rot) - 7, 32, cor)
        t.direita(X1, 30, rot, "muted")
    t.ret(X0, 53, X1 - X0 + 1, 1, "inset")


def escurecer(t):
    t.ret(2, 24, W - 4, H - 26, "scrim", 150)


def janela(t, x, y, w, h):
    t.ret(x + 2, y + 2, w, h, 0x000000, 70)
    t.relevo(x, y, w, h, "hi", "bev_hi", "bev_lo")


# ------------------------------------------------------------------ dados (coerentes entre as abas)

REDES = [
    ("Fundição", 0x6B6FD0, "Base", 14), ("Estoque", 0x2F9D5C, "Base", 9), ("Forja", 0xE8742B, "Base", 6),
    ("Química", 0x5E9A2B, "Mekanism", 11), ("Arcano", 0x8E4FC9, None, 4),
]
GRUPOS = [("Base", "iron_pickaxe", False), ("Mekanism", "redstone", True)]


def lista_redes(t, rede=None, grupo=None):
    coluna_esquerda(t)
    y = 28
    for nome_g, ic, pausado in GRUPOS + [(None, None, False)]:
        membros = [r for r in REDES if r[2] == nome_g]
        if grupo == nome_g and nome_g:
            realce_lista(t, 2, y, 148, 20)
        if nome_g:
            t.icone(8, y + 2, ic)
        t.texto(28, y + 6, nome_g or "Sem grupo", "fg" if nome_g else "muted", negrito=bool(nome_g))
        if pausado:
            t.direita(141, y + 6, "pausado", "muted")
        y += 22
        for nome, cor, _, n in membros:
            if nome == rede:
                realce_lista(t, 2, y - 2, 148, 14)
            t.quadradinho(28, y + 1, cor)
            t.texto(39, y + 1, nome, "muted" if pausado else "fg")
            t.direita(141, y + 1, str(n), "muted")
            y += 14
        y += 4
    rodape(t, 2)
    t.ret(150, H - 27, 1, 1, "inset")
    w1 = t.largura_botao("+ Rede")
    t.botao(8, H - 21, w1, "+ Rede", primario=True)
    t.botao(8 + w1 + 4, H - 21, t.largura_botao("+ Grupo"), "+ Grupo")


def linha_rotulo(t, y, rotulo):
    t.texto(X0, y + 5, rotulo, "muted")


def slot_cor(t, x, y, cor):
    t.slot(x - 1, y - 1, 18)
    t.ret(x + 1, y + 1, 14, 14, cor)


def rede_detalhe(t, nome="Fundição", cor=0x6B6FD0, privada=True):
    cabecalho(t, lambda x, y: slot_cor(t, x, y, cor), nome, "14 máquinas", ("ok", "ativa"), editavel=True)
    vx = X0 + 58
    linha_rotulo(t, 60, "Grupo")
    t.relevo(vx, 60, 112, 18, "face", "bev_hi", "bev_lo")
    t.icone(vx + 2, 61, "iron_pickaxe")
    t.texto(vx + 21, 65, "Base", "fg")
    seta(t, vx + 102, 68)

    linha_rotulo(t, 84, "Acesso")
    t.segmentos(vx, 84, ("Todos", "Convidados"), 1 if privada else 0)
    y = 110
    if privada:
        xs = vx
        for nome_c in ("Lia", "Rafa"):
            w = t.larg(nome_c) + 16
            t.borda(xs, 107, w, 12, "lo")
            t.texto(xs + 4, 109, nome_c, "fg")
            t.texto(xs + w - 9, 109, "x", "fg")
            xs += w + 4
        t.link(xs + 4, 109, "+ convidar")
        y = 130
    t.ret(X0, y, X1 - X0 + 1, 1, "inset")
    t.texto(X0, y + 7, "Nesta rede", "muted")
    y += 20
    for tipo, frase in (("item", "3 dão · 3 recebem"),):
        t.icone(X0, y, "tipo_" + tipo)
        t.texto(X0 + 20, y + 4, TIPO[tipo][1], "fg")
        t.direita(X1, y + 4, frase, "muted")
        y += 20
    rodape(t)
    t.botao(X0, H - 21, t.largura_botao("Pausar"), "Pausar")
    t.direita(X1, H - 16, "Apagar rede", "muted")


def popup_cor(t, cor):
    escurecer(t)
    slot_cor(t, X0, 30, cor)
    x, y, w, h = X0 - 1, 50, 116, 96
    janela(t, x, y, w, h)
    hh, ss, vv = colorsys.rgb_to_hsv(((cor >> 16) & 255) / 255, ((cor >> 8) & 255) / 255, (cor & 255) / 255)
    qx, qy, qw, qh = x + 6, y + 6, 88, 56
    for i in range(qw * S):
        for j in range(qh * S):
            r, g, b = colorsys.hsv_to_rgb(hh, i / (qw * S - 1), 1 - j / (qh * S - 1))
            t.img.putpixel((qx * S + i, qy * S + j), (int(r * 255), int(g * 255), int(b * 255), 255))
    t.borda(qx - 1, qy - 1, qw + 2, qh + 2, "frame")
    mx, my = qx + int(ss * (qw - 1)), qy + int((1 - vv) * (qh - 1))
    t.borda(mx - 2, my - 2, 5, 5, 0xFFFFFF)
    hx = x + 100
    for j in range(qh * S):
        r, g, b = colorsys.hsv_to_rgb(j / (qh * S - 1), 1, 1)
        for i in range(10 * S):
            t.img.putpixel((hx * S + i, qy * S + j), (int(r * 255), int(g * 255), int(b * 255), 255))
    t.borda(hx - 1, qy - 1, 12, qh + 2, "frame")
    t.ret(hx - 2, qy + int(hh * (qh - 1)), 14, 1, "fg")
    t.campo(qx, y + 66, 104, 14, foco=True)
    t.texto(qx + 4, y + 69, "#%06X" % cor, "fg")
    recentes = (0x6B6FD0, 0x2F9D5C, 0xE8742B, 0x5E9A2B, 0x8E4FC9, 0xD5453A, 0x2A9D9D, 0xB48E2E, 0xC2448E)
    for i, c in enumerate(recentes):
        t.quadradinho(qx + i * 12, y + 84, c, 8)
        if c == cor:
            t.borda(qx + i * 12 - 2, y + 82, 12, 12, "fg")


def grupo_detalhe(t, popup=False):
    def icone_grupo(x, y):
        t.slot(x - 1, y - 1, 18)
        t.icone(x, y, "iron_pickaxe")
    cabecalho(t, icone_grupo, "Base", "3 redes · 29 máquinas", editavel=True)
    t.texto(X0, 61, "Redes do grupo", "muted")
    y = 75
    for nome, cor, g, n in REDES:
        if g != "Base":
            continue
        t.quadradinho(X0, y + 1, cor)
        t.texto(X0 + 11, y + 1, nome, "fg")
        t.direita(X1, y + 1, f"{n} máquinas", "muted")
        y += 14
    t.link(X0, y + 5, "+ pôr uma rede neste grupo")
    rodape(t)
    w = t.largura_botao("Pausar grupo")
    t.botao(X0, H - 21, w, "Pausar grupo")
    t.direita(X1, H - 16, "Apagar grupo", "muted")
    if popup:
        popup_icone(t)


def popup_icone(t):
    escurecer(t)
    t.slot(X0 - 1, 29, 18)
    t.icone(X0, 30, "iron_pickaxe")
    x, y = X0 - 1, 50
    cols, linhas = 8, 3
    w, h = cols * 18 + 14, 22 + linhas * 18 + 20
    janela(t, x, y, w, h)
    t.campo(x + 7, y + 6, w - 14, 14, foco=True)
    t.ret(x + 11, y + 9, 1, 8, "fg")
    t.texto(x + 14, y + 9, "Buscar item", "muted")
    nomes = ["iron_pickaxe", "iron_ingot", "raw_iron", "redstone", "coal", "blaze_powder", "ender_pearl", "diamond",
             "emerald", "gold_ingot", "copper_ingot", "glowstone_dust", "amethyst_shard", "nether_star", "lava_bucket",
             "water_bucket", "wheat", "bone_meal", "experience_bottle", "fornalha", "alto", "barril", "forja", "ferro_bloco"]
    for i, n in enumerate(nomes):
        cx, cy = x + 7 + (i % cols) * 18, y + 24 + (i // cols) * 18
        t.slot(cx, cy, 18, realce=i == 0)
        t.icone(cx + 1, cy + 1, n)
    t.texto(x + 7, y + h - 14, "ou arraste um item do JEI", "muted")


MAQUINAS = [
    ("fornalha", "Fornalha 3", "destinos cheios", "bad"),
    ("alto", "Alto-forno", "96 itens/s", "muted"),
    ("defumador", "Defumador", "64 itens/s", "muted"),
    ("barril", "Barril do minério", "240 itens/s", "muted"),
    ("distribuidor", "Distribuidor", "sem rede", "bad"),
    ("lava_bucket", "Tanque de lava", "descarregada", "muted"),
    ("forja", "Mesa de forja", "cheia", "warn"),
    ("solar", "Painel solar", "60 FE/t", "muted"),
]


def lista_maquinas(t, escolhida):
    coluna_esquerda(t)
    t.campo(8, 30, 135, 16)
    t.texto(13, 34, "Buscar máquina", "muted")
    y = 52
    for ic, nome, estado, cor in MAQUINAS:
        if nome == escolhida:
            realce_lista(t, 2, y, 148, 22)
        t.icone(8, y + 3, ic)
        t.texto(28, y + 3, nome, "fg")
        t.texto(28, y + 12, estado, cor)
        y += 24


def marca_erro(t, x, y, cor="bad"):
    for i in range(5):
        t.ret(x + i, y + i, 2, 1, cor)
        t.ret(x + 4 - i, y + i, 2, 1, cor)


def cartao(t, y, titulo, detalhe, estado, acao, problemas):
    h = 30 + 13 * len(problemas)
    t.ret(X0, y, X1 - X0 + 1, h, "hi")
    t.ret(X0, y, 2, h, estado)
    t.texto(X0 + 8, y + 5, titulo, "fg", negrito=True)
    t.texto(X0 + 8, y + 16, detalhe, "muted")
    if acao:
        w = t.largura_botao(acao)
        t.botao(BTN_FIM - w, y + 7, w, acao, primario=True)
    py = y + 30
    for texto, acao2, cor in problemas:
        t.ret(X0 + 8, py - 3, X1 - X0 - 15, 1, "inset")
        marca_erro(t, X0 + 8, py + 1, cor)
        t.texto(X0 + 19, py + 1, texto, "fg")
        if acao2:
            t.link(BTN_FIM - t.larg(acao2) + 1, py + 1, acao2)
        py += 13
    return y + h


def seta_modo(t, x, y, modo):
    """A seta do modo, na cor dele: para baixo entra na máquina, para cima sai; o Armazém, as duas."""
    cor = MODO[modo][0]

    def uma(px, para_baixo):
        t.ret(px + 2, y, 1, 7, cor)
        for i in range(3):
            yy = y + 6 - i if para_baixo else y + i
            t.ret(px + 2 - i, yy, 2 * i + 1, 1, cor)
    if modo == "both":
        uma(x - 2, False)
        uma(x + 3, True)
    else:
        uma(x, modo == "ins")


def faces(t, y, linhas, editar=None, sem_uso=3, tipo="item"):
    t.texto(X0 + 4, y + 4, "Faces", "muted")
    y += 14
    for lado, modo, rede in linhas:
        cor_m, nome_m, ic = MODO[modo]
        aberto = lado == editar
        if aberto:
            t.ret(X0, y - 1, X1 - X0 + 1, 18 + 44, "hi")
        t.texto(X0 + 4, y + 4, lado, "fg")
        if not aberto:
            seta_modo(t, X0 + 50, y + 4, modo)
            t.texto(X0 + 60, y + 4, nome_m, "fg")
            if rede:
                t.quadradinho(X0 + 132, y + 4, rede[1])
                t.texto(X0 + 143, y + 4, rede[0], "fg")
            else:
                t.texto(X0 + 132, y + 4, "sem rede", "bad")
        chevron(t, X1 - 9, y + 4 if not aberto else y + 6, aberto)
        y += 18
        if aberto:
            ordem = ("ext", "ins", "both", None)
            t.segmentos(X0 + 4, y, [MODO[k][1] if k else "Nenhum" for k in ordem], ordem.index(modo))
            t.relevo(X0 + 4, y + 21, 124, 18, "face", "bev_hi", "bev_lo")
            if not rede:
                t.borda(X0 + 4, y + 21, 124, 18, "bad")
            if rede:
                t.quadradinho(X0 + 9, y + 26, rede[1])
                t.texto(X0 + 20, y + 26, rede[0], "fg")
            else:
                t.texto(X0 + 9, y + 26, "Escolher rede", "fg")
            seta(t, X0 + 116, y + 29)
            t.link(X1 - 4 - t.larg("Filtro e mais") + 1, y + 26, "Filtro e mais")
            y += 44
    if sem_uso:
        t.texto(X0 + 4, y + 3, f"{sem_uso} faces sem uso", "muted")


def maquina_detalhe(t, ic, nome, via, titulo, detalhe, estado, acao, problemas, linhas, editar=None, sem_uso=3):
    cabecalho(t, lambda x, y: t.icone(x, y, ic), nome, via)
    fim = cartao(t, 59, titulo, detalhe, estado, acao, problemas)
    faces(t, fim + 8, linhas, editar, sem_uso)
    rodape(t)
    w1 = t.largura_botao("Mostrar no mundo")
    t.botao(X0, H - 21, w1, "Mostrar no mundo")
    t.botao(X0 + w1 + 4, H - 21, t.largura_botao("Abrir roteador"), "Abrir roteador")


FLUXOS = {
    "item": dict(total="400", dao=[("barril", "Barril do minério", "Estoque", 0x2F9D5C, "240"),
                                   ("alto", "Alto-forno", "Fundição", 0x6B6FD0, "96"),
                                   ("defumador", "Defumador", "Fundição", 0x6B6FD0, "64"),
                                   ("fornalha", "Fornalha 3", "Fundição", 0x6B6FD0, "!bad:destinos cheios")],
                 rec=[("alto", "Alto-forno", "Fundição", 0x6B6FD0, "160"),
                      ("fornalha", "Fornalha 1", "Fundição", 0x6B6FD0, "48"),
                      ("defumador", "Defumador", "Fundição", 0x6B6FD0, "32"),
                      ("forja", "Mesa de forja", "Forja", 0xE8742B, "!warn:cheia"),
                      ("barril", "Barril 4", "Estoque", 0x2F9D5C, "!warn:cheio")],
                 arm=[("ferro_bloco", "Gaveta de ferro", "Estoque", "-10"), ("barril", "Barril grande", "Estoque", "+170")]),
    "energy": dict(total="4.320", dao=[("redstone_bloco", "Gerador a lava", "Forja", 0xE8742B, "4.200"),
                                       ("solar", "Painel solar", "Forja", 0xE8742B, "60"),
                                       ("solar", "Painel solar 2", "Forja", 0xE8742B, "60")],
                   rec=[("alto", "Alto-forno elétrico", "Forja", 0xE8742B, "120"),
                        ("forja", "Mesa de forja", "Forja", 0xE8742B, "80"),
                        ("distribuidor", "Câmara química", "Química", 0x5E9A2B, "!muted:pausada")],
                   arm=[("redstone_bloco", "Bateria da base", "Forja", "+4.120")]),
}


def fluxos(t, tipo):
    ordem = list(TIPO)
    t.segmentos(8, 29, [TIPO[k][1] for k in ordem], ordem.index(tipo), icones=["tipo_" + k for k in ordem])
    f = FLUXOS[tipo]
    x = t.texto(8, 56, f"{f['total']} {TIPO[tipo][2]}", "fg", negrito=True)
    t.texto(x + 5, 56, f"· {len(f['dao'])} dão · {len(f['rec'])} recebem", "muted")
    t.relevo(X1 - 104, 52, 105, 16, "face", "bev_hi", "bev_lo")
    t.texto(X1 - 99, 56, "Todas as redes", "fg")
    seta(t, X1 - 10, 59)
    t.ret(2, 72, W - 4, 1, "inset")
    base = H - 56
    t.ret(210, 73, 1, base - 73, "inset")
    colunas = ((8, 202), (218, X1))
    for (x0, x1), titulo, linhas in zip(colunas, ("Dão", "Recebem"), (f["dao"], f["rec"])):
        t.texto(x0, 78, titulo, "muted")
        y = 92
        for ic, nome, rede, cor_r, val in linhas:
            t.icone(x0, y, ic)
            t.texto(x0 + 20, y, nome, "fg")
            t.quadradinho(x0 + 20, y + 10, cor_r, 5)
            t.texto(x0 + 28, y + 9, rede, "muted")
            if val.startswith("!"):
                cor, rot = val[1:].split(":")
                t.direita(x1, y + 4, rot, cor)
            else:
                t.direita(x1, y + 4, val, "fg")
            y += 24
    # Os Armazéns embaixo, cada um na coluna do sentido: esvaziando dá (esquerda), enchendo recebe (direita).
    t.ret(2, base, W - 4, 1, "inset")
    t.texto(8, base + 6, "Armazéns", "muted")
    f["cor"] = {r[0]: r[1] for r in REDES}
    for ic, nome, rede, d in f["arm"]:
        x0, x1 = colunas[0] if d.startswith("-") else colunas[1]
        y = base + 20
        t.icone(x0, y, ic)
        t.texto(x0 + 20, y, nome, "fg")
        t.quadradinho(x0 + 20, y + 10, f["cor"][rede], 5)
        t.texto(x0 + 28, y + 9, rede, "muted")
        t.direita(x1, y + 4, d, "ok" if d.startswith("+") else "fg")


# ------------------------------------------------------------------ estados

def estados():
    yield "redes", "Redes", lambda t: (lista_redes(t, rede="Fundição"), rede_detalhe(t))
    yield "redes-cor", "Redes", lambda t: (lista_redes(t, rede="Fundição"), rede_detalhe(t), popup_cor(t, 0x6B6FD0))
    yield "grupo", "Redes", lambda t: (lista_redes(t, grupo="Base"), grupo_detalhe(t))
    yield "grupo-icone", "Redes", lambda t: (lista_redes(t, grupo="Base"), grupo_detalhe(t, popup=True))
    yield "maquina", "Máquina", lambda t: (lista_maquinas(t, "Fornalha 3"), maquina_detalhe(
        t, "fornalha", "Fornalha 3", "Roteador Elite · a 14 blocos",
        "Parada: destinos cheios", "2 cheios em Fundição", "bad", "Ver destinos",
        [("Filtro de Baixo: só ferro sai", "Editar", "warn")],
        [("Cima", "ins", ("Fundição", 0x6B6FD0)), ("Trás", "ins", ("Fundição", 0x6B6FD0)),
         ("Baixo", "ext", ("Estoque", 0x2F9D5C))]))
    yield "maquina-face", "Máquina", lambda t: (lista_maquinas(t, "Distribuidor"), maquina_detalhe(
        t, "distribuidor", "Distribuidor", "Roteador Elite · a 31 blocos",
        "Sem rede: nada sai", "Cima extrai, mas não tem rede", "bad", None, [],
        [("Cima", "ext", None), ("Baixo", "ins", ("Estoque", 0x2F9D5C))], editar="Cima", sem_uso=4))
    yield "fluxos", "Fluxos", lambda t: fluxos(t, "item")
    yield "fluxos-energia", "Fluxos", lambda t: fluxos(t, "energy")


def main():
    for nome, aba, desenhar in estados():
        for sufixo, tema in (("", CLARO), ("-escuro", ESCURO)):
            t = Tela(tema)
            moldura(t, aba)
            desenhar(t)
            t.salvar(nome + sufixo)
            print("ok", nome + sufixo)


if __name__ == "__main__":
    main()
