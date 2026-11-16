"""Os modelos formados da Armilar: cada um dos 35 blocos da estrutura mostra o seu pedaço da escultura.

O `gerar_texturas.py` chama `gerar()`, que grava os sprites (`textures/block/armilar_*.png`), os modelos
(`models/block/armilar/*.json`), os blockstates do Ressonador (`blockstates_armilar()`) e a prévia
(`docs/preview/armilar-formada.png`) e devolve as chaves das texturas para o `limpar`.

Todos os modelos são feitos com a frente para o sul; o giro fica no blockstate (sul 0, oeste 90, norte 180, leste
270). A escultura (`armilar_geometria.escultura`) é montada inteira, conferida e cortada na grade; aqui cada casa
vira um `Modelo` do `blocos.py` com as caixas em coordenadas locais de 0 a 16. A face que encosta no bloco vizinho
da estrutura sai do modelo (o `cullface` não a tiraria); o `cullface` fica só nas faces do limite que dão para fora,
e o `Modelo.caixa` o põe sozinho.

Uso: `python -I scripts/textures/armilar_modelo.py` grava tudo; com `--argumentos`, imprime os argumentos do
`verificar_modelo.py --junto` (e `--sem-sintonizador` troca o Sintonizador pela Laje `p20`).
"""

from __future__ import annotations

import copy
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import armilar_geometria as geo  # noqa: E402
import armilar_texturas  # noqa: E402
import blocos  # noqa: E402
import modelo_geometria  # noqa: E402
import previa_iso  # noqa: E402
import ressonador_blocos  # noqa: E402
from armilar_geometria import ModeloInvalido  # noqa: E402,F401
from identidade import ASSETS, MODELOS, PREVIEW, RAIZ, TIERS, grava_json, salvar  # noqa: E402

NS = blocos.NS
PASTA = "armilar"
MAX_ELEMENTOS = 40
# O giro dos modelos formados (frente para o sul) pela frente do bloco, o `y` do blockstate.
GIRO_FRENTE = {"south": 0, "west": 90, "north": 180, "east": 270}
GIRO_CANTO = {"se": 0, "sw": 90, "nw": 180, "ne": 270}
# As casas da Laje: x e z locais de 0 a 4, sem as do Núcleo (2, 4) e das Portas (4, 2) e (0, 2).
POSICOES_LAJE = [(x, z) for x in range(5) for z in range(5) if (x, z) not in ((2, 4), (4, 2), (0, 2))]
PECAS = {"resonator_energy_port": "energy_port", "resonator_crystal_port": "crystal_port",
         "resonator_tuner": "tuner"}
# Cada peça na sua casa do referencial do conceito e quantos giros de 90° (no sentido do `y`) a levam para o sul.
CASA_PECA = {"energy_port": ((2, 0, 0), 1), "crystal_port": ((-2, 0, 0), 3), "tuner": ((0, 0, -2), 2)}
CASA_NUCLEO = (0, 0, 2)
GIRO_ROTACAO = {"north": "east", "east": "south", "south": "west", "west": "north"}


def _uv_girada(c: geo.Cubo, face: str) -> list[float]:
    """Perna girada: a UV pelo tamanho da própria face, no meio do sprite (longe da borda de luz)."""
    d = c.b - c.a
    w, h = {"north": (d[0], d[1]), "south": (d[0], d[1]), "east": (d[2], d[1]), "west": (d[2], d[1]),
            "up": (d[0], d[2]), "down": (d[0], d[2])}[face]
    u0, v0 = (16 - w) / 2, (16 - h) / 2
    return [round(v, 4) for v in (u0, v0, u0 + w, v0 + h)]


def _uv_especial(c: geo.Cubo, face: str) -> list[float]:
    """A face especial usa só a região do desenho, do tamanho da face (1 texel por pixel), no canto do sprite."""
    d = c.b - c.a
    w, h = (d[0], d[1]) if face in ("north", "south") else (d[2], d[1])
    return [0.0, 0.0, round(float(w), 4), round(float(h), 4)]


def _particula(cubos: list[geo.Cubo]) -> str:
    """O material que mais área ocupa na fatia (a partícula do bloco)."""
    area: dict[str, float] = {}
    for c in cubos:
        d = c.b - c.a
        area[c.mat] = area.get(c.mat, 0.0) + float(d[0] * d[1] * d[2])
    return f"armilar_{max(area, key=area.get)}"


def modelo_da_casa(nome: str, C: list[geo.Cubo], cel: tuple[int, int, int], tier: str = "basic") -> blocos.Modelo:
    """O `Modelo` da casa `cel` (as faces especiais usam o mostrador do `tier`)."""
    lo = (16 * cel[0] - 8, 16 * cel[1], 16 * cel[2] - 8)
    cubos = [c for c in C if geo.celula(c)[0] == cel]
    m = blocos.Modelo(f"{PASTA}/{nome}", _particula(cubos), fatia=True)
    for c in cubos:
        de = [round(float(c.a[k] - lo[k]), 4) for k in range(3)]
        ate = [round(float(c.b[k] - lo[k]), 4) for k in range(3)]
        faces: blocos.Faces = {}
        for f in geo.FACES:
            if not c.faces[f]:
                continue
            if f in c.especial:
                esp = c.especial[f]
                faces[f] = (f"armilar_mostrador_{tier}" if esp == "mostrador" else f"armilar_{esp}", _uv_especial(c, f))
            else:
                faces[f] = (f"armilar_{c.mat}", None if c.alinhado() else _uv_girada(c, f))
        rot = None
        if not c.alinhado():
            o = [round(float(c.origem[k] - lo[k]), 4) for k in range(3)]
            rot = (c.rot[0], float(c.rot[1]), o)
        m.caixa(de, ate, faces, rot)
    return m


def girar(m: blocos.Modelo, giros: int) -> None:
    """Gira o modelo, `giros` vezes 90° em torno do centro vertical da casa, no sentido do `y` do blockstate (norte
    vira leste). Vale para caixas alinhadas e para as pernas já girados em y: a caixa troca de extensão, a origem
    da rotação vai junto e o ângulo fica (os dois giros em y comutam)."""
    for _ in range(giros % 4):
        for el in m.elementos:
            de, ate = el["from"], el["to"]
            el["from"] = [16 - ate[2], de[1], de[0]]
            el["to"] = [16 - de[2], ate[1], ate[0]]
            if "rotation" in el:
                ox, oy, oz = el["rotation"]["origin"]
                el["rotation"]["origin"] = [16 - oz, oy, ox]
            novas = {}
            for face, dados in el["faces"].items():
                destino = GIRO_ROTACAO.get(face, face)
                if "cullface" in dados:
                    dados["cullface"] = GIRO_ROTACAO.get(dados["cullface"], dados["cullface"])
                if face in ("up", "down"):  # a textura gira junto com a peça (visto de fora)
                    dados["rotation"] = (dados.get("rotation", 0) + (90 if face == "up" else 270)) % 360
                novas[destino] = dados
            el["faces"] = novas


def validar(m: blocos.Modelo) -> None:
    if len(m.elementos) > MAX_ELEMENTOS:
        raise ModeloInvalido(f"{m.nome}: {len(m.elementos)} elementos, o limite é {MAX_ELEMENTOS}")
    for el in m.elementos:
        env = modelo_geometria.envolvente(el)
        if any(v < -1e-6 or v > 16 + 1e-6 for v in env):
            raise ModeloInvalido(f"{m.nome}: elemento fora da casa {env}")
    try:
        modelo_geometria.validar_geometria(m)
    except AssertionError as erro:
        raise ModeloInvalido(str(erro)) from erro


def _conferir(C, vol, cop, fora, rotulo: str) -> None:
    if vol or cop or fora:
        raise ModeloInvalido(f"escultura {rotulo}: {len(vol)} sobreposições {vol[:3]}, {len(cop)} faces no mesmo "
                             f"plano, {len(fora)} peças fora da própria casa {fora[:3]}")


def construir() -> dict[str, blocos.Modelo]:
    """Os 22 + 2 + 2 + 8 + 3 modelos, por nome de arquivo (sem a pasta e sem `.json`)."""
    C, vol, cop, fora = geo.escultura()
    _conferir(C, vol, cop, fora, "formada")
    ms: dict[str, blocos.Modelo] = {}
    for lx, lz in POSICOES_LAJE:
        if (lx, lz) != (2, 0):
            ms[f"casing_p{lx}{lz}"] = modelo_da_casa(f"casing_p{lx}{lz}", C, (lx - 2, 0, lz - 2))
    # Sem o Sintonizador, a casa dele é uma Laje: o corpo fica inteiro, sem o nicho.
    S, vol, cop, fora = geo.escultura(com_sintonizador=False)
    _conferir(S, vol, cop, fora, "sem o Sintonizador")
    ms["casing_p20"] = modelo_da_casa("casing_p20", S, (0, 0, -2))
    for parte, cy in (("lower", 1), ("upper", 2)):
        ms[f"column_{parte}"] = modelo_da_casa(f"column_{parte}", C, (1, cy, 1))
        ms[f"cradle_{parte}"] = modelo_da_casa(f"cradle_{parte}", C, (0, cy, 0))
    for t in TIERS:
        ms[f"core_{t}"] = modelo_da_casa(f"core_{t}", C, CASA_NUCLEO, t)
    for nome, (cel, giros) in CASA_PECA.items():
        ms[nome] = modelo_da_casa(nome, C, cel)
        girar(ms[nome], giros)
    for m in ms.values():
        validar(m)
    return ms


def instancias(sem_sintonizador: bool = False) -> list[tuple[str, tuple[int, int, int], int]]:
    """Cada modelo no seu bloco (x, y, z em blocos, a casa do conceito) com o `y` do blockstate daquela variante."""
    saida = [(f"casing_p{lx}{lz}", (lx - 2, 0, lz - 2), 0) for lx, lz in POSICOES_LAJE
             if (lx, lz) != (2, 0) or sem_sintonizador]
    saida.append(("core_basic", CASA_NUCLEO, 0))
    for parte, cy in (("lower", 1), ("upper", 2)):
        saida += [(f"column_{parte}", (1 if sx > 0 else -1, cy, 1 if sz > 0 else -1), GIRO_CANTO[c])
                  for c, (sx, sz) in (("se", (1, 1)), ("sw", (-1, 1)), ("nw", (-1, -1)), ("ne", (1, -1)))]
        saida.append((f"cradle_{parte}", (0, cy, 0), 0))
    saida.append(("energy_port", (2, 0, 0), GIRO_FRENTE["east"]))
    saida.append(("crystal_port", (-2, 0, 0), GIRO_FRENTE["west"]))
    if not sem_sintonizador:
        saida.append(("tuner", (0, 0, -2), GIRO_FRENTE["north"]))
    return saida


def argumentos_junto(sem_sintonizador: bool = False) -> str:
    """Os argumentos `arquivo@x,y,z[,giro]` do `verificar_modelo.py --junto`, separados por espaço."""
    pasta = (MODELOS / "models/block" / PASTA).relative_to(RAIZ).as_posix()
    partes = []
    for nome, (x, y, z), giro in instancias(sem_sintonizador):
        partes.append(f"{pasta}/{nome}.json@{x},{y},{z}" + (f",{giro}" if giro else ""))
    return " ".join(partes)


def _giro(y: int) -> dict:
    return {"y": y} if y else {}


def blockstates_armilar() -> dict[str, dict]:
    """Os blockstates novos, por nome de arquivo: a Laje (`facing` × `position`), a Coluna (`part` × `corner`), o
    Berço (`part`), as peças (`facing` × `formed`) e o Núcleo (`facing` × `formed` × `tier`). Os estados soltos
    usam os modelos soltos e o giro de `ROTACOES_PECA` (norte 0, leste 90, sul 180, oeste 270); os formados usam
    os modelos de `armilar/` e o giro da frente (sul 0, oeste 90, norte 180, leste 270). O `facing` é horizontal
    (`HORIZONTAL_FACING`): só as quatro frentes têm variante."""
    bloco = f"{NS}:block/"
    formado = f"{bloco}{PASTA}/"
    laje: dict[str, dict] = {}
    for frente, y in GIRO_FRENTE.items():
        laje[f"facing={frente},position=loose"] = {"model": bloco + "resonator_casing_loose"}
        for lx, lz in POSICOES_LAJE:
            laje[f"facing={frente},position=p{lx}{lz}"] = {"model": f"{formado}casing_p{lx}{lz}", **_giro(y)}
    coluna: dict[str, dict] = {}
    for canto, y in GIRO_CANTO.items():
        coluna[f"corner={canto},part=loose"] = {"model": bloco + "resonator_column_loose"}
        for parte in ("lower", "upper"):
            coluna[f"corner={canto},part={parte}"] = {"model": f"{formado}column_{parte}", **_giro(y)}
    berco = {"part=loose": {"model": bloco + "resonator_cradle_loose"},
             **{f"part={p}": {"model": f"{formado}cradle_{p}"} for p in ("lower", "upper")}}
    saida = {"resonator_casing": {"variants": laje}, "resonator_column": {"variants": coluna},
             "resonator_cradle": {"variants": berco}}

    def estados(solto: str, modelo_formado: str, tiers: tuple[str | None, ...] = (None,)) -> dict:
        variantes = {}
        for face, rot in ressonador_blocos.ROTACOES_PECA.items():
            if face not in GIRO_FRENTE:
                continue
            for t in tiers:
                sufixo = f",tier={t}" if t else ""
                variantes[f"facing={face},formed=false{sufixo}"] = {"model": solto.format(t=t), **rot}
                variantes[f"facing={face},formed=true{sufixo}"] = {
                    "model": modelo_formado.format(t=t), **_giro(GIRO_FRENTE.get(face, 0))}
        return variantes

    for bloco_peca, arquivo in PECAS.items():
        saida[bloco_peca] = {"variants": estados(f"{bloco}{bloco_peca}_loose", f"{formado}{arquivo}")}
    saida["resonance_core"] = {"variants": estados(f"{bloco}resonance_core_{{t}}_loose",
                                                   f"{formado}core_{{t}}", tiers=tuple(TIERS))}
    return saida


# ---------------------------------------------------------------- prévia
def previa(ms: dict[str, blocos.Modelo], destino: Path | None = None, s: int = 6) -> Path:
    """Os modelos juntos na posição do mundo, na projeção isométrica do `previa_iso.render` (câmera em +x, +y, +z: vê
    o sul, onde fica o Núcleo, e o leste, onde fica a Energia)."""
    destino = destino or PREVIEW / "armilar-formada.png"
    junto = blocos.Modelo("armilar_formada", "armilar_porcelana")
    for nome, (x, y, z), giro in instancias():
        m = copy.deepcopy(ms[nome])
        girar(m, giro // 90)
        for el, dentro in zip(m.elementos, m.dentro):
            el["from"] = [a + 16 * d for a, d in zip(el["from"], (x, y, z))]
            el["to"] = [a + 16 * d for a, d in zip(el["to"], (x, y, z))]
            if "rotation" in el:
                el["rotation"]["origin"] = [a + 16 * d for a, d in zip(el["rotation"]["origin"], (x, y, z))]
            junto.elementos.append(el)
            junto.dentro.append(dentro)
    spr = armilar_texturas.sprites()
    tex = {n.split("/", 1)[1]: sp.quadro(0) for n, sp in spr.items()}
    img = previa_iso.render(junto, tex, s, (164 * s, 132 * s, 82 * s, 90 * s))
    destino.parent.mkdir(parents=True, exist_ok=True)
    img.save(destino)
    return destino


def gerar(raiz_assets: Path = MODELOS) -> set[str]:
    """Grava os sprites, os modelos, os blockstates e a prévia; devolve as chaves das texturas gravadas
    (relativas a `textures/`, como as do `limpar`). Pára com `ModeloInvalido` se a escultura ou algum modelo
    violar uma regra."""
    ms = construir()
    spr = armilar_texturas.sprites()
    for nome, sp in spr.items():
        salvar(nome, sp)
    pasta = raiz_assets / "models/block" / PASTA
    for nome, m in ms.items():
        grava_json(pasta / f"{nome}.json", m.json())
    for antigo in pasta.glob("*.json"):  # a pasta toda é gerada daqui
        if antigo.stem not in ms:
            antigo.unlink()
    for nome, dados in blockstates_armilar().items():
        grava_json(raiz_assets / f"blockstates/{nome}.json", dados)
    previa(ms)
    return set(spr)


if __name__ == "__main__":
    if "--argumentos" in sys.argv:
        print(argumentos_junto("--sem-sintonizador" in sys.argv))
    else:
        print(f"{len(gerar())} texturas; modelos em models/block/{PASTA}/; prévia em docs/preview/armilar-formada.png")
