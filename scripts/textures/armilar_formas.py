"""As formas de colisão e seleção dos sete blocos da Armilar, tiradas do desenho de cada modelo base.

O `gerar_texturas.py` chama `gerar()`, que grava `resonator/ArmilarShapes.java`. Para cada modelo base (os sete
soltos e as fatias montadas, incluindo a Laje sem o Sintonizador) o volume dos elementos (também os internos, sem
face, que o JSON não leva) é arredondado para fora na grade de 1 px e um fundidor guloso o junta em poucas caixas.
O mapa de estado para modelo e giro sai do mesmo `armilar_modelo.blockstates_armilar()` que escreve os blockstates:
não há duas tabelas. O giro (`y` de 90 leva (x, z) a (16 - z, x)) é feito no Java.

Uso: `python -I scripts/textures/armilar_formas.py` grava a classe; `--resumo` só imprime as contagens.
"""

from __future__ import annotations

import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import armilar_modelo  # noqa: E402
import blocos  # noqa: E402
import ressonador_blocos  # noqa: E402
from identidade import RAIZ  # noqa: E402
from modelo_geometria import EPS, ModeloInvalido, envolvente  # noqa: E402

DESTINO = (RAIZ / "src/main/java/io/github/matheusanbs/wirelessautomate/resonator/ArmilarShapes.java")
Caixa = tuple[int, int, int, int, int, int]
PREFIXO_MODELO = f"{blocos.NS}:block/"


def voxels(m: blocos.Modelo) -> set[tuple[int, int, int]]:
    """Os voxels de 1 px (x, y, z) que algum elemento toca: a envolvente de cada um, cortada em 0..16 e
    arredondada para fora."""
    cheios: set[tuple[int, int, int]] = set()
    for el in m.elementos:
        env = [min(16.0, max(0.0, v)) for v in envolvente(el)]
        lo = [math.floor(v + EPS) for v in env[:3]]
        hi = [math.ceil(v - EPS) for v in env[3:]]
        for x in range(lo[0], hi[0]):
            for y in range(lo[1], hi[1]):
                for z in range(lo[2], hi[2]):
                    cheios.add((x, y, z))
    return cheios


def fundir(cheios: set[tuple[int, int, int]]) -> list[Caixa]:
    """Fundidor guloso: de cada voxel livre, estica em x, depois em z, depois em y, enquanto tudo for cheio."""
    livres = set(cheios)
    caixas: list[Caixa] = []
    for y, z, x in sorted((y, z, x) for x, y, z in cheios):
        if (x, y, z) not in livres:
            continue
        x1 = x + 1
        while (x1, y, z) in livres:
            x1 += 1
        z1 = z + 1
        while all((i, y, z1) in livres for i in range(x, x1)):
            z1 += 1
        y1 = y + 1
        while all((i, y1, k) in livres for i in range(x, x1) for k in range(z, z1)):
            y1 += 1
        for i in range(x, x1):
            for j in range(y, y1):
                for k in range(z, z1):
                    livres.discard((i, j, k))
        caixas.append((x, y, z, x1, y1, z1))
    return caixas


def conferir(m: blocos.Modelo, caixas: list[Caixa]) -> None:
    """Como `validar_hitboxes`: toda caixa em 0..16 e todo elemento dentro da união das caixas (pelos cantos da
    envolvente); e nenhuma caixa à toa, todas tocam algum elemento."""
    if not caixas:
        raise ModeloInvalido(f"{m.nome}: nenhuma caixa de forma")
    for c in caixas:
        if not (all(0 <= v <= 16 for v in c) and all(c[i] < c[i + 3] for i in range(3))):
            raise ModeloInvalido(f"{m.nome}: caixa inválida {c}")
    envs = [envolvente(el) for el in m.elementos]
    for n, env in enumerate(envs):
        for canto in ((env[a], env[b], env[c]) for a in (0, 3) for b in (1, 4) for c in (2, 5)):
            if not any(all(cx[i] - EPS <= canto[i] <= cx[i + 3] + EPS for i in range(3)) for cx in caixas):
                raise ModeloInvalido(f"{m.nome}: elemento {n} fora da forma em {canto}")
    for c in caixas:
        if not any(all(c[i] < e[i + 3] + EPS and e[i] < c[i + 3] + EPS for i in range(3)) for e in envs):
            raise ModeloInvalido(f"{m.nome}: a caixa {c} não toca nenhum elemento")


def modelos_base() -> dict[str, blocos.Modelo]:
    """Todos os modelos que os blockstates usam, pelo nome (o `nome` do `Modelo`): os soltos e as fatias."""
    todos = {m.nome: m for m in ressonador_blocos.modelos_soltos()}
    for m in armilar_modelo.construir().values():
        todos[m.nome] = m
    return todos


def formas() -> tuple[dict[str, list[Caixa]], dict[str, str]]:
    """(caixas por modelo canônico, canônico de cada modelo). Os modelos de mesma forma (os tiers do Núcleo, que só
    trocam a textura) dividem o primeiro nome."""
    caixas: dict[str, list[Caixa]] = {}
    canonico: dict[str, str] = {}
    por_forma: dict[tuple[Caixa, ...], str] = {}
    for nome, m in modelos_base().items():
        achadas = fundir(voxels(m))
        conferir(m, achadas)
        chave = tuple(achadas)
        canonico[nome] = por_forma.setdefault(chave, nome)
        caixas.setdefault(canonico[nome], achadas)
    return caixas, canonico


def estados(canonico: dict[str, str]) -> list[tuple[str, str, str, int]]:
    """(bloco, chave da variante, modelo canônico, y) de cada variante dos blockstates, na ordem do arquivo."""
    saida = []
    for bloco, dados in armilar_modelo.blockstates_armilar().items():
        for chave, variante in dados["variants"].items():
            if "x" in variante:
                raise ModeloInvalido(f"{bloco} {chave}: giro em x não tem forma no Java")
            nome = variante["model"].removeprefix(PREFIXO_MODELO)
            saida.append((bloco, chave, canonico[nome], variante.get("y", 0)))
    return saida


def linha_caixas(caixas: list[Caixa]) -> str:
    return ";".join(",".join(str(v) for v in c) for c in caixas)


MOLDE = '''package io.github.matheusanbs.wirelessautomate.resonator;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Gerado por scripts/textures/armilar_formas.py (gerar_texturas.py). Não edite.
 *
 * <p>As formas de colisão e seleção dos sete blocos da Armilar, soltos ou montados, tiradas do desenho de cada
 * modelo base: as caixas de cada um em pixels ({@link #BASES}) e, de cada variante dos blockstates, o modelo e o
 * giro em y ({@link #ESTADOS}, do mesmo dado que escreve os blockstates). O giro é o do blockstate: {@code y} de
 * 90 leva (x, z) a (16 - z, x). As formas giradas são montadas uma vez, na carga da classe; cada bloco monta a
 * tabela dos seus estados no construtor e o {@code getShape} só a consulta.
 */
final class ArmilarShapes {
{constantes}

    /** Os modelos base (`nome`, depois as caixas `x0,y0,z0,x1,y1,z1` em pixels, separadas por `;`). */
    private static final String[] BASES = {
{bases}
    };

    /** As variantes dos blockstates: `bloco|chave|modelo|y`. */
    private static final String[] ESTADOS = {
{estados}
    };

    /** A forma de cada modelo base nos quatro giros de 90 graus. */
    private static final Map<String, VoxelShape[]> GIRADAS = girar();

    private ArmilarShapes() {
    }

    private static Map<String, VoxelShape[]> girar() {
        Map<String, VoxelShape[]> formas = new Object2ObjectOpenHashMap<>();
        for (String base : BASES) {
            String[] partes = base.split("=");
            VoxelShape[] giros = new VoxelShape[4];
            for (int giro = 0; giro < 4; giro++) {
                VoxelShape forma = Shapes.empty();
                for (String texto : partes[1].split(";")) {
                    int[] c = Arrays.stream(texto.split(",")).mapToInt(Integer::parseInt).toArray();
                    forma = Shapes.or(forma, caixa(c[0], c[1], c[2], c[3], c[4], c[5], giro));
                }
                giros[giro] = forma.optimize();
            }
            formas.put(partes[0], giros);
        }
        return formas;
    }

    /** A caixa girada `giro` vezes 90 graus em torno do centro vertical do bloco, no sentido do `y` do blockstate. */
    private static VoxelShape caixa(int x0, int y0, int z0, int x1, int y1, int z1, int giro) {
        int ax = x0;
        int az = z0;
        int bx = x1;
        int bz = z1;
        for (int i = 0; i < giro; i++) {
            int novoAx = 16 - bz;
            int novoBx = 16 - az;
            az = ax;
            bz = bx;
            ax = novoAx;
            bx = novoBx;
        }
        return Block.box(ax, y0, az, bx, y1, bz);
    }

    /**
     * A forma da variante `chave` (`facing=north,position=p00`, propriedades em ordem alfabética) do bloco. Só o
     * construtor dos blocos e o teste a usam; faz uma varredura linear de `ESTADOS`, então não serve ao `getShape`.
     */
    static VoxelShape forma(String bloco, String chave) {
        String prefixo = bloco + "|" + chave + "|";
        for (String estado : ESTADOS) {
            if (estado.startsWith(prefixo)) {
                String[] partes = estado.substring(prefixo.length()).split("\\\\|");
                return GIRADAS.get(partes[0])[Integer.parseInt(partes[1]) / 90];
            }
        }
        throw new IllegalArgumentException("sem forma para " + bloco + " " + chave);
    }

    /** A forma de cada estado do bloco, montada uma vez (no construtor); estado sem variante é erro, não buraco. */
    static Map<BlockState, VoxelShape> tabela(Block bloco, String nome) {
        Map<BlockState, VoxelShape> tabela = new Reference2ObjectOpenHashMap<>();
        for (BlockState estado : bloco.getStateDefinition().getPossibleStates()) {
            tabela.put(estado, forma(nome, chave(estado)));
        }
        return tabela;
    }

    /** A chave da variante do estado: as propriedades em ordem alfabética, `nome=valor` e vírgula. */
    private static String chave(BlockState estado) {
        List<Property<?>> propriedades = new ArrayList<>(estado.getProperties());
        propriedades.sort(Comparator.comparing(Property::getName));
        StringBuilder chave = new StringBuilder();
        for (Property<?> propriedade : propriedades) {
            if (chave.length() > 0) {
                chave.append(',');
            }
            chave.append(par(estado, propriedade));
        }
        return chave.toString();
    }

    private static <T extends Comparable<T>> String par(BlockState estado, Property<T> propriedade) {
        return propriedade.getName() + "=" + propriedade.getName(estado.getValue(propriedade));
    }
}
'''


def java(caixas: dict[str, list[Caixa]], tabela: list[tuple[str, str, str, int]]) -> str:
    blocos_nomes = list(dict.fromkeys(b for b, *_ in tabela))
    constantes = "\n".join(f'    static final String {b.upper().removeprefix("RESONATOR_")} = "{b}";'
                           for b in blocos_nomes)
    bases = ",\n".join(f'        "{n}={linha_caixas(c)}"' for n, c in caixas.items())
    est = ",\n".join(f'        "{b}|{k}|{m}|{y}"' for b, k, m, y in tabela)
    return (MOLDE.replace("{constantes}", constantes).replace("{bases}", bases).replace("{estados}", est))


def resumo(caixas: dict[str, list[Caixa]]) -> str:
    maior = max(caixas.items(), key=lambda kv: len(kv[1]))
    return (f"{len(caixas)} modelos base, {sum(len(c) for c in caixas.values())} caixas no total, "
            f"o maior com {len(maior[1])} ({maior[0]})")


def gerar() -> None:
    caixas, canonico = formas()
    texto = java(caixas, estados(canonico))
    DESTINO.write_text(texto, encoding="utf-8", newline="\n")
    print(f"formas da Armilar: {resumo(caixas)}; {DESTINO.relative_to(RAIZ)}")


if __name__ == "__main__":
    if "--resumo" in sys.argv:
        print(resumo(formas()[0]))
    else:
        gerar()
