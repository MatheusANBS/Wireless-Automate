"""Identidade visual "Porcelana e Sinal": paleta, materiais e utilidades comuns aos geradores.

Lido por `gerar_texturas.py` (itens e ícones da interface) e por `blocos.py` (modelos 3D e texturas dos
blocos). Ver `docs/identidade-visual.md` para a ideia, as regras e as tabelas de cor.

Um sprite é uma `Sprite`: a imagem (16 px de largura; altura 16 × quadros quando é animada), o
`frametime` em ticks (None = parada) e se é translúcida (vidro: alfa parcial permitida). `salvar` grava o
PNG e o `.mcmeta` da animação (ou apaga um `.mcmeta` que sobrou).
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

from PIL import Image

RAIZ = Path(__file__).resolve().parents[2]
ASSETS = RAIZ / "src/main/resources/assets/wirelessautomate/textures"
MODELOS = RAIZ / "src/main/resources/assets/wirelessautomate"
PREVIEW = RAIZ / "docs/preview"

Cor = tuple[int, int, int, int]


def rgb(hexa: str, alfa: int = 255) -> Cor:
    hexa = hexa.lstrip("#")
    return (int(hexa[0:2], 16), int(hexa[2:4], 16), int(hexa[4:6], 16), alfa)


# ---------------------------------------------------------------------------
# Materiais: tons de "4" (luz, cima/esquerda) a "0" (sombra, baixo/direita)
# ---------------------------------------------------------------------------

PALETA: dict[str, dict[str, str]] = {
    "porcelana": {"4": "#FFFBF3", "3": "#F2EBDD", "2": "#DDD3BF", "1": "#BDB19A", "0": "#948870"},
    "grafite": {"4": "#5A5660", "3": "#3E3B44", "2": "#2A2730", "1": "#1B1920", "0": "#0F0E12"},
    "coral": {"4": "#FFB39E", "3": "#FF8A6B", "2": "#F0603E", "1": "#C2442A", "0": "#86301E"},
    "latao": {"3": "#F7E3A1", "2": "#DDBC5C", "1": "#B48E2E", "0": "#80621C"},
    "vidro": {"3": "#F6FCFF", "2": "#CFE6EE", "1": "#8FB4C2", "0": "#5A7E8E"},
    "fluido": {"3": "#BFE0FF", "2": "#5B9DF0", "1": "#2F6FD6", "0": "#1F4A99"},
    "energia": {"3": "#FFF1B3", "2": "#FFD042", "1": "#D9931A", "0": "#8F5E0C"},
    "quimico": {"3": "#E6F7B8", "2": "#A8DB5A", "1": "#5E9A2B", "0": "#355B16"},
    "source": {"3": "#F0C8FA", "2": "#D08AF0", "1": "#9B4DC6", "0": "#6B2F8F"},
    "chunk": {"3": "#C8F5B0", "2": "#6FD64A", "1": "#2F9D5C", "0": "#1C5E36"},
}

# A ordem do RouterTier (o predicado wirelessautomate:tier do modelo do item é a posição aqui).
TIERS = ["basic", "advanced", "elite", "emerald", "allthemodium", "vibranium", "unobtainium", "ultimate"]
TIERS_CARTAO = TIERS[1:]
TIERS_ATM = ["allthemodium", "vibranium", "unobtainium"]
# Marcas de grafite (1 a 4) por tier, para quem não distingue cores: 1 a 4 na escada vanilla,
# 1 a 3 dentro da família do Allthemodium.
MARCAS = {"basic": 0, "advanced": 1, "elite": 2, "emerald": 3, "ultimate": 4,
          "allthemodium": 1, "vibranium": 2, "unobtainium": 3}

# A lente do Olho por tier: (brilho, lente, sombra). A mesma de GuiPaint.tierColor (a do meio).
LENTE: dict[str, tuple[str, str, str]] = {
    "basic": ("#B9BEC8", "#6E7480", "#454A54"),
    "advanced": ("#F7D77A", "#D59A1E", "#8E6410"),
    "elite": ("#8EE8E1", "#1FA9A0", "#136C66"),
    "emerald": ("#8BEBA6", "#22A84E", "#146A30"),
    "allthemodium": ("#FFC27A", "#E8740A", "#9A4A05"),
    "vibranium": ("#86F0C4", "#18B57A", "#0E6E4A"),
    "unobtainium": ("#E9A6F5", "#B23FD0", "#6F2384"),
    "ultimate": ("#C7B0FA", "#7B4FE0", "#4A2C92"),
}

# Cor dos recursos (ResourceStyle.color) e dos modos de porta (GuiPaint.modeColor).
RECURSO = {"item": "#B57A3A", "fluid": "#2F6FD6", "energy": "#D9931A", "chemical": "#5E9A2B", "source": "#8E4FC9"}
MODO = {"extract": "#2F6FD6", "insert": "#E8742B", "both": "#2F9D5C", "none": "#948870"}
ESTADO = {"ok": "#2F9D5C", "atencao": "#D88A1A", "erro": "#D5453A", "pausado": "#6B6FD0"}

for _t, (_hi, _mid, _lo) in LENTE.items():
    # Paleta "lente_<tier>" para usar nas legendas: 3 = brilho, 2 = lente, 1 = sombra, 0 = anel.
    PALETA[f"lente_{_t}"] = {"3": _hi, "2": _mid, "1": _lo, "0": PALETA["grafite"]["1"]}


def cor(ref: str, alfa: int = 255) -> Cor:
    """'paleta.tom' ou '#rrggbb' -> RGBA."""
    if ref.startswith("#"):
        return rgb(ref, alfa)
    paleta, tom = ref.split(".")
    return rgb(PALETA[paleta][tom], alfa)


def legenda(**mapa: str) -> dict[str, Cor]:
    """Monta a legenda de um sprite: caractere -> 'paleta.tom' ou '#rrggbb'."""
    return {chave: cor(ref) for chave, ref in mapa.items()}


def pinta(grade: list[str], leg: dict[str, Cor], largura: int = 16, altura: int = 16) -> Image.Image:
    """Grade de texto -> imagem. '.' e ' ' são transparentes; outro caractere tem de estar na legenda."""
    img = Image.new("RGBA", (largura, altura), (0, 0, 0, 0))
    assert len(grade) <= altura, f"grade com {len(grade)} linhas"
    for y, linha in enumerate(grade):
        assert len(linha) <= largura, f"linha {y} com {len(linha)} colunas: {linha!r}"
        for x, ch in enumerate(linha):
            if ch in ". ":
                continue
            if ch not in leg:
                raise KeyError(f"caractere {ch!r} sem cor na legenda (linha {y}: {linha!r})")
            img.putpixel((x, y), leg[ch])
    return img


def contorno(img: Image.Image, c: Cor) -> Image.Image:
    """Contorno de 1 px (vizinhança de 4) em volta dos pixels opacos."""
    saida = img.copy()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            if img.getpixel((x, y))[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and img.getpixel((nx, ny))[3]:
                    saida.putpixel((x, y), c)
                    break
    return saida


def escurece(c: Cor, f: float) -> Cor:
    return (int(c[0] * f), int(c[1] * f), int(c[2] * f), c[3])


def mistura(a: Cor, b: Cor, t: float) -> Cor:
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(4))  # type: ignore[return-value]


# ---------------------------------------------------------------------------
# O Olho: a lente da marca, com o Pulso
# ---------------------------------------------------------------------------

def olho(img: Image.Image, cx: int, cy: int, tier: str = "basic", fase: float = 0.0, raio: int = 2,
         anel: bool = True) -> None:
    """Desenha o Olho centrado em (cx, cy): lente do tier com brilho em cima à esquerda, dentro de um
    anel de grafite. `raio` 1 (3×3), 2 (5×5) ou 3 (7×7). `fase` 0..1 é o Pulso: em 0 o anel está
    apagado; entre 0 e 1 um anel de luz (a cor do brilho) se expande a partir da lente e some.
    """
    hi, mid, lo = (cor(x) for x in LENTE[tier])
    grafite = cor("grafite.1")
    w, h = img.size

    def put(x: int, y: int, c: Cor) -> None:
        if 0 <= x < w and 0 <= y < h:
            img.putpixel((x, y), c)

    r = raio
    # Anel.
    if anel:
        for dx in range(-r, r + 1):
            for dy in range(-r, r + 1):
                d = abs(dx) + abs(dy)
                if (max(abs(dx), abs(dy)) == r and d < 2 * r) or (r == 1 and d == 1):
                    put(cx + dx, cy + dy, grafite)
    # Lente: losango/círculo interno.
    ri = r - 1 if anel else r
    for dx in range(-ri, ri + 1):
        for dy in range(-ri, ri + 1):
            sombra = dx + dy > 0
            put(cx + dx, cy + dy, lo if sombra else mid)
    if ri >= 1:
        put(cx - 1, cy - 1, hi)
    else:
        put(cx, cy, mid)
    # Pulso: um anel de luz que sai da lente, dura metade do ciclo e some.
    if 0 < fase < 1:
        passo = int(fase * 4)  # 0..3
        if passo < 2:
            rr = r + passo
            c = hi if passo == 0 else mistura(hi, cor("porcelana.3"), 0.5)
            for dx in range(-rr, rr + 1):
                for dy in range(-rr, rr + 1):
                    if abs(dx) + abs(dy) == rr + (rr // 2) and max(abs(dx), abs(dy)) > r:
                        px, py = cx + dx, cy + dy
                        if 0 <= px < w and 0 <= py < h and img.getpixel((px, py))[3] == 0:
                            put(px, py, c)
        elif passo < 3 and anel:
            for dx in range(-r, r + 1):
                for dy in range(-r, r + 1):
                    if max(abs(dx), abs(dy)) == r and abs(dx) + abs(dy) < 2 * r:
                        put(cx + dx, cy + dy, mistura(grafite, hi, 0.45))


# ---------------------------------------------------------------------------
# Sprites, animação e gravação
# ---------------------------------------------------------------------------

@dataclass
class Sprite:
    """Imagem 16 px de largura e 16 × quadros de altura. `frametime` em ticks (None = parada)."""
    imagem: Image.Image
    frametime: int | None = None
    translucido: bool = False
    interpolar: bool = False

    @property
    def quadros(self) -> int:
        return self.imagem.height // 16

    def quadro(self, n: int = 0) -> Image.Image:
        return self.imagem.crop((0, 16 * n, 16, 16 * n + 16))


def animacao(quadros: list[Image.Image], frametime: int, **kw) -> Sprite:
    """Empilha os quadros (todos 16×16) num só sprite animado."""
    assert quadros and all(q.size == (16, 16) for q in quadros), "quadro fora de 16×16"
    tira = Image.new("RGBA", (16, 16 * len(quadros)), (0, 0, 0, 0))
    for n, q in enumerate(quadros):
        tira.paste(q, (0, 16 * n))
    return Sprite(tira, frametime, **kw)


def parado(img: Image.Image, **kw) -> Sprite:
    return Sprite(img, None, **kw)


def validar(nome: str, sprite: Sprite) -> None:
    img = sprite.imagem
    assert img.width == 16 and img.height % 16 == 0 and img.height >= 16, f"{nome}: {img.size}"
    if sprite.frametime is None:
        assert img.height == 16, f"{nome}: tira de {sprite.quadros} quadros sem frametime"
    else:
        assert img.height > 16, f"{nome}: animado com um quadro só"
    if not sprite.translucido:
        alfas = set(img.getchannel("A").tobytes())
        assert alfas <= {0, 255}, f"{nome}: alfa parcial {sorted(alfas)[:6]}"


def salvar(nome: str, sprite: Sprite) -> Path:
    """Grava textures/<nome>.png e o .mcmeta da animação (ou apaga o que sobrou de antes)."""
    validar(nome, sprite)
    destino = ASSETS / f"{nome}.png"
    destino.parent.mkdir(parents=True, exist_ok=True)
    sprite.imagem.save(destino)
    meta = destino.with_name(destino.name + ".mcmeta")
    if sprite.frametime is not None:
        anim: dict = {"frametime": sprite.frametime}
        if sprite.interpolar:
            anim["interpolate"] = True
        meta.write_text(json.dumps({"animation": anim}, indent=2) + "\n", encoding="utf-8", newline="\n")
    elif meta.exists():
        meta.unlink()
    return destino


def grava_json(destino: Path, dados: dict) -> None:
    destino.parent.mkdir(parents=True, exist_ok=True)
    destino.write_text(json.dumps(dados, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
