# Ressonador de Sinal, etapa 2: estrutura e Núcleo · plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** o Ressonador de Sinal monta de verdade: os seis blocos da estrutura 5×5×5, as texturas conectadas pela posição no blockstate, a validação por evento, o Núcleo com tier que gera FE em lote pela sintonia (estação com deriva, sem piso), gasta e recarrega cristais, e as Portas de Energia e de Cristais com capability. Sem tela: estado na action bar e o dial pelo comando.

**Arquitetura:** a lógica pura (validação da estrutura, sintonia e contas) fica em `resonator/` com JUnit. O Núcleo é o único block entity; um gerenciador central (`ResonatorManager`, como o `SourceTankLevels`) visita os Núcleos montados a cada 20 ticks e trata as mudanças de bloco da estrutura. As Portas não têm block entity: a capability acha o Núcleo por um índice do servidor. Os estados de bloco (posição da carcaça, conexões do vidro, `formed`) são gravados pelo Núcleo ao montar e desfeitos ao desmontar; o script de texturas gera os modelos de cada estado.

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), ModDevGradle, JUnit 5, GameTests, Python 3 + PIL.

**Spec:** [`docs/superpowers/specs/2026-10-15-ressonador-de-sinal-design.md`](../specs/2026-10-15-ressonador-de-sinal-design.md), seções "Sintonia", "Estrutura (etapa 2)", "Núcleo e geração (etapa 2)" e **"Etapa 2: decisões de implementação"** (a fonte dos valores exatos). Mockup: `docs/preview/ressonador.html`; protótipo das texturas: `docs/preview/ressonador-proto.py` (funções `campo`, `trilhos`, `chave`, `carcaca_solta`, `forro`, `vidro`, `placa`, `olho_par`, `marcas_tier`, `nucleo`, `sintonizador`, `porta_energia`, `porta_cristais`, `nucleo_dentro`, `porta_dentro`); prancha: `docs/preview/ressonador-prancha.png`. Etapa 1 pronta: `resonator/CrystalCharge`, `resonator/SignalCrystalItem` (`charge`, `setCharge`, `isDull`, `isFull`, `yield`), `ModItems.SIGNAL_CRYSTAL`, `ModItems.CUT_SIGNAL_CRYSTAL`, `gametest/ResonatorCrystalGameTests`.

## Restrições globais

- Código, comentários, commits e docs em português; textos nos dois `lang` (`en_us.json`, `pt_br.json`), sempre os dois, na mesma ordem.
- Ids: `resonator_casing`, `resonator_glass`, `resonance_core`, `resonator_energy_port`, `resonator_crystal_port`, `resonator_tuner`. Nomes: Carcaça de Porcelana / Porcelain Casing; Vidro de Ressonância / Resonance Glass; Núcleo de Ressonância / Resonance Core; Porta de Energia / Resonator Energy Port; Porta de Cristais / Resonator Crystal Port; Sintonizador / Resonator Tuner.
- Estrutura 5×5×5 fixa: 44 arestas e cantos só de Carcaça; nas 54 posições de face: Carcaça, Vidro, exatamente 1 Núcleo, pelo menos 1 Porta de Energia, pelo menos 1 Porta de Cristais, 0 ou 1 Sintonizador; as 27 posições de dentro vazias (ar).
- Sintonia: dial de 88,0 a 108,0 MHz em passos de 0,05; eficiência `1` se `d ≤ 0,25`, `0` se `d ≥ 2,0`, senão `1 − ((d − 0,25) / 1,75)^1,5`; deriva a cada 400 ticks: `v ← clamp(v + N(0;1)·0,03, −0,12, +0,12)`, `estação ← clamp(estação + v/3, 88,5, 107,5)`; estação nasce aleatória em [88,5; 107,5] na primeira montagem.
- Geração por tick = Σ(`fePerCrystal(tier)` × rendimento do cristal) × eficiência, só dos cristais não Opacos; câmaras = ordinal do tier + 1; buffer = câmaras × `fePerCrystal` × 2 × 200; com o buffer cheio não gera nem gasta.
- Config do servidor, seção `resonator`: `crystalLifeTicks` (12.000), `cutCrystalLifeTicks` (24.000), `rechargeTicks` (600) e, em `resonator.<tier>`, `fePerCrystal` (64, 512, 4.096, 32.768, 262.144, 2.097.152, 16.777.216, 134.217.728). Chaves existentes de NBT, componentes e config **não mudam**.
- Performance: nada de tick por bloco (nenhum `BlockEntityTicker`); lote a cada 20 ticks pelo `ResonatorManager`; validação só por evento de bloco; capability das Portas sem busca por tick.
- Mekanism, Ars e Allthemodium continuam opcionais; nada de tipos deles aqui. Classes de `client/` nunca referenciadas por código comum.
- `ModPayloads.VERSION` continua `"16"` (nenhum payload novo nesta etapa).
- Texturas: edite os scripts, nunca os PNGs. O gerador regrava PNGs antigos com bytes diferentes e muda o `docs/preview/icones-preview.png` pela fonte local: depois de rodar, restaure com `git checkout --` tudo o que não é desta tarefa.
- GameTests: `@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`; estruturas do Ressonador no template `"empty7"` (Task 2 cria); teste pertinência, não contagem global; crie a própria rede; nulos com `Objects.equals`; espere o `onLoad` com `startSequence().thenWaitUntil(...)`; teste que muda config vai no lote `batch = "wirelessautomate_config"` e devolve o valor em `try/finally`.
- Comentários: `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados; tamanho: `python -I .claude/skills/wa-modular/scripts/medir.py` (servidor: dividir acima de 1000 linhas).
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`. Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, pare e conserte. Nunca dois jogos ao mesmo tempo; não mexa em `run/options.txt`.
- Commits sem linhas de atribuição, com o autor do dono; `git add` só dos arquivos da tarefa (nunca `.superpowers/`). Não faça push.

## Mapa de arquivos

Base `B/` = `src/main/java/io/github/matheusanbs/wirelessautomate/`, `R/` = `src/main/resources/`.

| Arquivo | Papel |
| --- | --- |
| `B/resonator/ResonatorLayout.java` (novo) | Puro: acha a caixa 5×5×5 em volta do Núcleo, valida e devolve os membros (posição da carcaça, face e bits do vidro, face das peças) ou o problema |
| `B/resonator/Tuning.java` (novo) | Puro: eficiência, passo da deriva, dial, estação inicial |
| `B/resonator/CoreMath.java` (novo) | Puro: câmaras, geração por tick, máximo, buffer, com multiplicação saturada |
| `src/test/java/.../resonator/{ResonatorLayoutTest,TuningTest,CoreMathTest}.java` (novos) | JUnit |
| `B/resonator/CasingPosition.java` (novo) | Enum do blockstate da carcaça (27 valores) |
| `B/resonator/GlassFace.java` (novo) | Enum do blockstate do vidro (solto + 6 faces) |
| `B/resonator/ResonatorCasingBlock.java`, `ResonatorGlassBlock.java`, `ResonatorPartBlock.java`, `ResonanceCoreBlock.java` (novos) | Os blocos (o `ResonatorPartBlock` serve às duas Portas e ao Sintonizador, com um `PartKind`) |
| `B/resonator/ResonanceCoreItem.java` (novo) | Item do Núcleo com tier (`withTier`/`tierOf`, como o `StorageBlockItem`) |
| `B/resonator/ResonanceCoreBlockEntity.java` (novo) | Dados do Núcleo: cristais, redstone, energia, estação, dial, montagem, membros |
| `B/resonator/ResonatorManager.java` (novo) | Núcleos carregados, lote a cada 20 ticks, mudanças de bloco da estrutura |
| `B/resonator/ResonatorFormation.java` (novo) | Montar e desmontar: chama o `ResonatorLayout`, grava os estados, índice das portas |
| `B/resonator/ResonatorBatch.java` (novo) | Um lote do Núcleo: deriva, geração, desgaste, recarga, aviso às portas |
| `B/resonator/ResonatorPorts.java` (novo) | Índice porta → Núcleo e as capabilities (`CoreEnergy`, `CoreItems`) |
| `B/resonator/ResonatorCapabilities.java` (novo) | `RegisterCapabilitiesEvent` das Portas |
| `B/resonator/ResonatorStatus.java` (novo) | O texto da action bar (montado ou o que falta) |
| `B/command/ResonatorCommand.java` (novo) | `/wa resonator <pos> info` e `dial <MHz>` |
| `B/Config.java`, `B/WirelessAutomate.java`, `B/registry/*`, `B/item/TierCoreItem.java`, `B/command/WaCommand.java` (mudar) | Config, tick e parada, registros, cartão, comando |
| `R/data/wirelessautomate/structure/empty7.nbt` (novo) | Template 7×7×7 vazio dos GameTests |
| `R/data/...` (novos) | Loot, tags, receitas |
| `scripts/textures/blocos.py` (mudar) | Texturas, modelos e blockstates dos seis blocos |
| `B/gametest/ResonatorTestSupport.java`, `ResonatorFormationGameTests.java`, `ResonatorCoreGameTests.java`, `ResonatorPortGameTests.java` (novos) | GameTests |
| `CLAUDE.md`, `docs/progresso.md`, `docs/especificacao.md` (mudar) | Docs |

---

### Task 1: A lógica pura (estrutura, sintonia e contas)

**Files:**
- Create: `B/resonator/ResonatorLayout.java`, `B/resonator/Tuning.java`, `B/resonator/CoreMath.java`
- Create: `src/test/java/io/github/matheusanbs/wirelessautomate/resonator/ResonatorLayoutTest.java`, `TuningTest.java`, `CoreMathTest.java`

**Interfaces:**
- Produces: `ResonatorLayout.SIZE` (5); `ResonatorLayout.Part` (`CASING, GLASS, CORE, ENERGY_PORT, CRYSTAL_PORT, TUNER, AIR, OTHER`); `ResonatorLayout.Face` (`DOWN, UP, NORTH, SOUTH, WEST, EAST`, mesma ordem do `Direction`, com `dx()`, `dy()`, `dz()`); `ResonatorLayout.Probe` (`Part at(int x, int y, int z)`); `ResonatorLayout.Member(int x, int y, int z, Part part, String casing, Face face, int glassBits)`; `ResonatorLayout.Problem` (`WRONG_BLOCK, INTERIOR_NOT_EMPTY, EXTRA_CORE, MISSING_ENERGY_PORT, MISSING_CRYSTAL_PORT, TOO_MANY_TUNERS`); `ResonatorLayout.Result` (selado: `Formed(int minX, int minY, int minZ, List<Member> members)` e `Broken(Problem problem, int x, int y, int z)`); `ResonatorLayout.check(int cx, int cy, int cz, Probe probe) → Result`; `Tuning.efficiency(double) → double`; `Tuning.step(double station, double velocity, double gaussian) → Tuning.Drift(double station, double velocity)`; `Tuning.clampDial(double) → double`; `Tuning.initialStation(double unit01) → double`; constantes `Tuning.MIN_MHZ` (88), `MAX_MHZ` (108); `CoreMath.chambers(int tierOrdinal) → int`; `CoreMath.maxOutput(int chambers, long fePerCrystal) → long`; `CoreMath.buffer(int chambers, long fePerCrystal) → long`; `CoreMath.perTick(long fePerCrystal, int yieldSum, double efficiency) → long`; `CoreMath.mulSat(long, long) → long`.

Regras do `check` (o Núcleo está em `(cx, cy, cz)`):
- Candidatas: para cada uma das 6 faces e cada posição `(a, b)` de 1 a 3 no miolo 3×3 dela, a caixa 5×5×5 em que o Núcleo fica nessa posição da face (coordenada local 0 ou 4 no eixo da face, `a` e `b` nos outros dois, na ordem x, y, z).
- Numa caixa: posições com nenhuma coordenada local na borda (0 ou 4) são o interior (precisam ser `AIR`, senão `INTERIOR_NOT_EMPTY`); com duas ou três na borda são arestas e cantos (precisam ser `CASING`, senão `WRONG_BLOCK`); com uma são posições de face (aceitam `CASING`, `GLASS`, `CORE`, as duas Portas e `TUNER`; `AIR`/`OTHER` é `WRONG_BLOCK`; um `CORE` que não é o de `(cx, cy, cz)` é `EXTRA_CORE`).
- Contagem de erros por caixa: cada posição errada vale 1; depois, sem posição errada, falta de Porta de Energia, falta de Porta de Cristais e mais de um Sintonizador valem 1 cada (o primeiro achado, nessa ordem, é o problema).
- Se uma caixa não tem erro, devolve `Formed` dela (a primeira na ordem das faces `DOWN…EAST`, `a` e depois `b` crescentes). Senão devolve o `Broken` da caixa com menos erros (empate: a primeira), com o primeiro problema achado na ordem `x`, `y`, `z` crescentes e a posição dele no mundo; nas faltas e no Sintonizador a mais, a posição é a do Núcleo (faltas) ou a do segundo Sintonizador.
- `Member.casing`: só nas Carcaças; três letras, uma por eixo `x`, `y`, `z`: `n` (local 0), `c` (1 a 3), `p` (local 4). Exemplo: aresta de baixo ao norte no meio = `"cnn"`; Carcaça no meio da face de cima = `"cpc"`.
- `Member.face`: nas posições de face, a face da estrutura em que estão; nas arestas e cantos, `null`.
- `Member.glassBits`: só no Vidro; para a face `f`, com os vetores "direita" e "cima" da tabela abaixo, bit 1 se há `GLASS` em `pos + cima`, 2 em `pos + direita`, 4 em `pos − cima`, 8 em `pos − direita`.
- Tabela (direita; cima) por face: `NORTH` (−x; +y), `SOUTH` (+x; +y), `WEST` (+z; +y), `EAST` (−z; +y), `UP` (+x; −z), `DOWN` (+x; +z).

- [ ] **Step 1: Os testes que falham**

`TuningTest`:

```java
package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TuningTest {
    @Test
    void eficienciaPelaDistanciaDaEstacao() {
        assertEquals(1.0, Tuning.efficiency(0.0), 1e-9);
        assertEquals(1.0, Tuning.efficiency(-0.25), 1e-9);
        assertEquals(0.95, Tuning.efficiency(0.5), 0.005);
        assertEquals(0.72, Tuning.efficiency(1.0), 0.005);
        assertEquals(0.40, Tuning.efficiency(1.5), 0.005);
        assertEquals(0.0, Tuning.efficiency(2.0), 1e-9);
        assertEquals(0.0, Tuning.efficiency(-7.3), 1e-9);
    }

    @Test
    void derivaTemVelocidadeEEstacaoLimitadas() {
        Tuning.Drift d = Tuning.step(98.0, 0.11, 5.0);
        assertEquals(0.12, d.velocity(), 1e-9);
        assertEquals(98.04, d.station(), 1e-9);
        assertEquals(107.5, Tuning.step(107.49, 0.12, 1.0).station(), 1e-9);
        assertEquals(88.5, Tuning.step(88.51, -0.12, -1.0).station(), 1e-9);
        assertEquals(0.03, Tuning.step(98.0, 0.0, 1.0).velocity(), 1e-9);
    }

    @Test
    void dialAndaEmPassosDe005DentroDaFaixa() {
        assertEquals(97.45, Tuning.clampDial(97.4612), 1e-9);
        assertEquals(88.0, Tuning.clampDial(12.0), 1e-9);
        assertEquals(108.0, Tuning.clampDial(300.0), 1e-9);
    }

    @Test
    void estacaoInicialNaFaixaDaDeriva() {
        assertEquals(88.5, Tuning.initialStation(0.0), 1e-9);
        assertEquals(107.5, Tuning.initialStation(1.0), 1e-9);
    }
}
```

`CoreMathTest`:

```java
package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CoreMathTest {
    @Test
    void camarasPeloTier() {
        assertEquals(1, CoreMath.chambers(0));
        assertEquals(3, CoreMath.chambers(2));
        assertEquals(8, CoreMath.chambers(7));
    }

    @Test
    void maximoEBufferDaSpec() {
        assertEquals(128, CoreMath.maxOutput(1, 64));
        assertEquals(24_576, CoreMath.maxOutput(3, 4_096));
        assertEquals(2_147_483_648L, CoreMath.maxOutput(8, 134_217_728L));
        assertEquals(25_600, CoreMath.buffer(1, 64));
        assertEquals(4_915_200, CoreMath.buffer(3, 4_096));
    }

    @Test
    void geracaoPorTickArredondaParaBaixo() {
        assertEquals(4_096 * 4, CoreMath.perTick(4_096, 4, 1.0));
        assertEquals(11_796, CoreMath.perTick(4_096, 4, 0.72));
        assertEquals(0, CoreMath.perTick(4_096, 4, 0.0));
        assertEquals(0, CoreMath.perTick(4_096, 0, 1.0));
    }

    @Test
    void multiplicacaoSatura() {
        assertEquals(Long.MAX_VALUE, CoreMath.mulSat(Long.MAX_VALUE / 2, 3));
        assertEquals(Long.MAX_VALUE, CoreMath.buffer(8, Long.MAX_VALUE / 4));
        assertEquals(6, CoreMath.mulSat(2, 3));
    }
}
```

`ResonatorLayoutTest` (um mundo de mentira em `Map`):

```java
package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Broken;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Formed;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Member;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Problem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResonatorLayoutTest {
    /** A Vitrine com origem em (10, 20, 30): janela 3×3 ao sul, Núcleo no leste, Portas no oeste e no norte, Sintonizador em cima. */
    private static Map<List<Integer>, Part> vitrine() {
        Map<List<Integer>, Part> w = new HashMap<>();
        for (int x = 0; x < 5; x++) for (int y = 0; y < 5; y++) for (int z = 0; z < 5; z++) {
            boolean dentro = x > 0 && x < 4 && y > 0 && y < 4 && z > 0 && z < 4;
            w.put(List.of(10 + x, 20 + y, 30 + z), dentro ? Part.AIR : Part.CASING);
        }
        for (int x = 1; x < 4; x++) for (int y = 1; y < 4; y++) w.put(List.of(10 + x, 20 + y, 34), Part.GLASS);
        w.put(List.of(14, 22, 32), Part.CORE);
        w.put(List.of(10, 22, 32), Part.ENERGY_PORT);
        w.put(List.of(12, 22, 30), Part.CRYSTAL_PORT);
        w.put(List.of(12, 24, 32), Part.TUNER);
        return w;
    }

    private static ResonatorLayout.Result check(Map<List<Integer>, Part> w) {
        return ResonatorLayout.check(14, 22, 32, (x, y, z) -> w.getOrDefault(List.of(x, y, z), Part.OTHER));
    }

    private static Member at(Formed f, int x, int y, int z) {
        return f.members().stream().filter(m -> m.x() == x && m.y() == y && m.z() == z).findFirst().orElseThrow();
    }

    @Test
    void vitrineMontaComOs98Membros() {
        Formed f = assertInstanceOf(Formed.class, check(vitrine()));
        assertEquals(List.of(10, 20, 30), List.of(f.minX(), f.minY(), f.minZ()));
        assertEquals(98, f.members().size());
        assertEquals("cnn", at(f, 12, 20, 30).casing());
        assertEquals("npn", at(f, 10, 24, 30).casing());
        assertEquals("cpc", at(f, 11, 24, 31).casing());
        assertEquals(Face.EAST, at(f, 14, 22, 32).face());
        assertEquals(Face.SOUTH, at(f, 12, 22, 34).face());
    }

    @Test
    void bitsDoVidroSeguemATabelaDasFaces() {
        Formed f = assertInstanceOf(Formed.class, check(vitrine()));
        assertEquals(15, at(f, 12, 22, 34).glassBits());
        assertEquals(1 | 2, at(f, 11, 21, 34).glassBits());
        assertEquals(4 | 8, at(f, 13, 23, 34).glassBits());
    }

    @Test
    void semPortaDeEnergiaNaoMonta() {
        Map<List<Integer>, Part> w = vitrine();
        w.put(List.of(10, 22, 32), Part.CASING);
        Broken b = assertInstanceOf(Broken.class, check(w));
        assertEquals(Problem.MISSING_ENERGY_PORT, b.problem());
        assertEquals(List.of(14, 22, 32), List.of(b.x(), b.y(), b.z()));
    }

    @Test
    void duasPortasDeCadaMontam() {
        Map<List<Integer>, Part> w = vitrine();
        w.put(List.of(10, 21, 32), Part.ENERGY_PORT);
        w.put(List.of(12, 21, 30), Part.CRYSTAL_PORT);
        assertInstanceOf(Formed.class, check(w));
    }

    @Test
    void doisSintonizadoresNaoMontam() {
        Map<List<Integer>, Part> w = vitrine();
        w.put(List.of(11, 24, 31), Part.TUNER);
        assertEquals(Problem.TOO_MANY_TUNERS, assertInstanceOf(Broken.class, check(w)).problem());
    }

    @Test
    void pecaNaArestaApontaOBlocoErrado() {
        Map<List<Integer>, Part> w = vitrine();
        w.put(List.of(14, 24, 32), Part.GLASS);
        Broken b = assertInstanceOf(Broken.class, check(w));
        assertEquals(Problem.WRONG_BLOCK, b.problem());
        assertEquals(List.of(14, 24, 32), List.of(b.x(), b.y(), b.z()));
    }

    @Test
    void interiorPrecisaEstarVazio() {
        Map<List<Integer>, Part> w = vitrine();
        w.put(List.of(12, 22, 32), Part.OTHER);
        Broken b = assertInstanceOf(Broken.class, check(w));
        assertEquals(Problem.INTERIOR_NOT_EMPTY, b.problem());
        assertEquals(List.of(12, 22, 32), List.of(b.x(), b.y(), b.z()));
    }

    @Test
    void segundoNucleoNaoMonta() {
        Map<List<Integer>, Part> w = vitrine();
        w.put(List.of(11, 22, 30), Part.CORE);
        assertEquals(Problem.EXTRA_CORE, assertInstanceOf(Broken.class, check(w)).problem());
    }

    @Test
    void buracoNaCascaApontaABorda() {
        Map<List<Integer>, Part> w = vitrine();
        w.put(List.of(10, 20, 30), Part.AIR);
        Broken b = assertInstanceOf(Broken.class, check(w));
        assertEquals(Problem.WRONG_BLOCK, b.problem());
        assertEquals(List.of(10, 20, 30), List.of(b.x(), b.y(), b.z()));
    }
}
```

- [ ] **Step 2: Rode e veja falhar**

Run: `./gradlew test --tests '*resonator*'`
Expected: falha de compilação (as classes não existem).

- [ ] **Step 3: Implemente**

`Tuning.java`:

```java
package io.github.matheusanbs.wirelessautomate.resonator;

/** A sintonia do Ressonador de Sinal: eficiência pelo desvio, deriva da estação e o dial. Lógica pura. */
public final class Tuning {
    public static final double MIN_MHZ = 88.0;
    public static final double MAX_MHZ = 108.0;
    public static final double DIAL_STEP = 0.05;
    static final double PLATEAU = 0.25;
    static final double ZERO = 2.0;
    static final double MAX_VELOCITY = 0.12;
    static final double STEP_SIGMA = 0.03;
    static final double STATION_MIN = 88.5;
    static final double STATION_MAX = 107.5;

    /** Um passo da deriva: estação em MHz e velocidade em MHz por minuto. */
    public record Drift(double station, double velocity) {
    }

    private Tuning() {
    }

    /** 100% dentro do platô, 0% a partir de 2 MHz, e a curva da spec entre os dois. */
    public static double efficiency(double deviation) {
        double d = Math.abs(deviation);
        if (d <= PLATEAU) {
            return 1.0;
        }
        if (d >= ZERO) {
            return 0.0;
        }
        return 1.0 - Math.pow((d - PLATEAU) / (ZERO - PLATEAU), 1.5);
    }

    /** Um passo de 20 s: a velocidade anda pelo sorteio e a estação anda um terço da velocidade. */
    public static Drift step(double station, double velocity, double gaussian) {
        double v = clamp(velocity + gaussian * STEP_SIGMA, -MAX_VELOCITY, MAX_VELOCITY);
        return new Drift(clamp(station + v / 3, STATION_MIN, STATION_MAX), v);
    }

    public static double clampDial(double mhz) {
        double passo = Math.round(mhz / DIAL_STEP) * DIAL_STEP;
        return clamp(Math.round(passo * 100) / 100.0, MIN_MHZ, MAX_MHZ);
    }

    /** A estação da primeira montagem, a partir de um sorteio uniforme em [0; 1]. */
    public static double initialStation(double unit01) {
        return STATION_MIN + clamp(unit01, 0, 1) * (STATION_MAX - STATION_MIN);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
```

`CoreMath.java`:

```java
package io.github.matheusanbs.wirelessautomate.resonator;

/** As contas do Núcleo de Ressonância, em {@code long} e saturando em {@code Long.MAX_VALUE}. Lógica pura. */
public final class CoreMath {
    /** O buffer guarda 10 s (200 ticks) da geração máxima. */
    static final long BUFFER_TICKS = 200;

    private CoreMath() {
    }

    /** Câmaras de cristal: Básico 1 … Ultimate 8. */
    public static int chambers(int tierOrdinal) {
        return tierOrdinal + 1;
    }

    /** Geração máxima por tick: todas as câmaras com lapidados (rendimento 2) a 100%. */
    public static long maxOutput(int chambers, long fePerCrystal) {
        return mulSat(mulSat(chambers, fePerCrystal), 2);
    }

    public static long buffer(int chambers, long fePerCrystal) {
        return mulSat(maxOutput(chambers, fePerCrystal), BUFFER_TICKS);
    }

    /** FE por tick de cristais que somam {@code yieldSum} de rendimento, na eficiência dada; arredonda para baixo. */
    public static long perTick(long fePerCrystal, int yieldSum, double efficiency) {
        if (yieldSum <= 0 || efficiency <= 0) {
            return 0;
        }
        return (long) Math.floor(mulSat(fePerCrystal, yieldSum) * Math.min(1.0, efficiency));
    }

    public static long mulSat(long a, long b) {
        long high = Math.multiplyHigh(a, b);
        long low = a * b;
        if ((high == 0 && low >= 0) || (high == -1 && low < 0)) {
            return low;
        }
        return (a < 0) == (b < 0) ? Long.MAX_VALUE : Long.MIN_VALUE;
    }
}
```

`ResonatorLayout.java`: implemente as regras acima. Guia (pode reorganizar em métodos privados, mantendo a API):

```java
package io.github.matheusanbs.wirelessautomate.resonator;

import java.util.ArrayList;
import java.util.List;

/**
 * A estrutura 5×5×5 do Ressonador de Sinal: acha a caixa em volta do Núcleo, confere cada posição e
 * devolve os membros já com a posição da carcaça, a face e os bits do vidro (texturas conectadas), ou o
 * problema e onde ele está. Lógica pura: o mundo chega pela {@link Probe}.
 */
public final class ResonatorLayout {
    public static final int SIZE = 5;

    public enum Part {
        CASING, GLASS, CORE, ENERGY_PORT, CRYSTAL_PORT, TUNER, AIR, OTHER
    }

    /** As faces na ordem do {@code Direction} do Minecraft, com a direção para fora. */
    public enum Face {
        DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);

        private final int dx;
        private final int dy;
        private final int dz;

        Face(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        public int dx() { return dx; }
        public int dy() { return dy; }
        public int dz() { return dz; }

        /** "Direita" da textura desta face no mundo (modelo do vanilla sem rotação). */
        public int[] right() {
            return switch (this) {
                case NORTH -> new int[] {-1, 0, 0};
                case SOUTH, UP, DOWN -> new int[] {1, 0, 0};
                case WEST -> new int[] {0, 0, 1};
                case EAST -> new int[] {0, 0, -1};
            };
        }

        /** "Cima" da textura desta face no mundo. */
        public int[] up() {
            return switch (this) {
                case UP -> new int[] {0, 0, -1};
                case DOWN -> new int[] {0, 0, 1};
                default -> new int[] {0, 1, 0};
            };
        }
    }

    @FunctionalInterface
    public interface Probe {
        Part at(int x, int y, int z);
    }

    public record Member(int x, int y, int z, Part part, String casing, Face face, int glassBits) {
    }

    public enum Problem {
        WRONG_BLOCK, INTERIOR_NOT_EMPTY, EXTRA_CORE, MISSING_ENERGY_PORT, MISSING_CRYSTAL_PORT, TOO_MANY_TUNERS
    }

    public sealed interface Result permits Formed, Broken {
    }

    public record Formed(int minX, int minY, int minZ, List<Member> members) implements Result {
    }

    public record Broken(Problem problem, int x, int y, int z) implements Result {
    }

    private ResonatorLayout() {
    }

    public static Result check(int cx, int cy, int cz, Probe probe) {
        // Para cada face e cada (a, b) de 1 a 3: origem da caixa, avalie (erros, primeiro problema,
        // membros); devolva o primeiro Formed; senão o Broken da caixa com menos erros.
        throw new UnsupportedOperationException();
    }
}
```

O `check` lê cada posição da caixa uma vez (125 leituras por candidata, no máximo 54 candidatas). Pare a avaliação de uma candidata assim que ela passar do menor número de erros já visto (não muda o resultado e corta o custo).

- [ ] **Step 4: Rode e veja passar**

Run: `./gradlew test --tests '*resonator*'`
Expected: todos os testes de `TuningTest`, `CoreMathTest` e `ResonatorLayoutTest` passando (mais os 5 de `CrystalChargeTest`).

- [ ] **Step 5: Verificação e commit**

Run: `./gradlew build` (só JUnit e compilação; nada de jogo mudou), a varredura de comentários e o `medir.py`.

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/resonator src/test/java/io/github/matheusanbs/wirelessautomate/resonator
git commit -m "Ressonador: lógica pura da estrutura, da sintonia e das contas do Núcleo"
```

---

### Task 2: Config, blocos, itens, dados e o template dos testes

**Files:**
- Create: `B/resonator/CasingPosition.java`, `GlassFace.java`, `ResonatorCasingBlock.java`, `ResonatorGlassBlock.java`, `ResonatorPartBlock.java`, `ResonanceCoreBlock.java`, `ResonanceCoreItem.java`, `ResonanceCoreBlockEntity.java` (só o esqueleto: construtor, tier e NBT vazio; o resto vem nas Tasks 4 a 6)
- Modify: `B/Config.java`, `B/registry/ModBlocks.java`, `ModItems.java`, `ModBlockEntities.java`, `ModCreativeTabs.java`
- Create: `R/data/wirelessautomate/structure/empty7.nbt`, `R/data/wirelessautomate/loot_table/blocks/{resonator_casing,resonator_glass,resonance_core,resonator_energy_port,resonator_crystal_port,resonator_tuner}.json`, `R/data/wirelessautomate/recipe/{resonator_casing,resonator_glass,resonance_core,resonator_energy_port,resonator_crystal_port,resonator_tuner}.json`
- Modify: `R/data/minecraft/tags/block/mineable/pickaxe.json`, `lang/en_us.json`, `lang/pt_br.json`
- Create: `B/gametest/ResonatorTestSupport.java`, `B/gametest/ResonatorFormationGameTests.java` (os testes desta tarefa; a Task 4 acrescenta)

**Interfaces:**
- Consumes: `CoreMath`, `ResonatorLayout.Face` (Task 1); `RouterBlock.TIER`, `RouterTier` (existentes).
- Produces:
  - `CasingPosition` (`enum` com `StringRepresentable`): `LOOSE` e as 26 combinações de três letras de `n`, `c`, `p` (todas menos `ccc`), constante em maiúsculas e nome serializado em minúsculas (`NNN`/`"nnn"`, …, `PPP`/`"ppp"`); `CasingPosition.byName(String) → CasingPosition`.
  - `GlassFace` (`StringRepresentable`): `LOOSE, DOWN, UP, NORTH, SOUTH, WEST, EAST` (nomes `"loose"`, `"down"`…); `GlassFace.of(ResonatorLayout.Face)`.
  - `ResonatorCasingBlock.POSITION` (`EnumProperty<CasingPosition>`, padrão `LOOSE`).
  - `ResonatorGlassBlock.FACE` (`EnumProperty<GlassFace>`, padrão `LOOSE`) e `ResonatorGlassBlock.CONNECTIONS` (`IntegerProperty` 0 a 15, padrão 0); estende `TransparentBlock` (o vidro do vanilla), sem oclusão.
  - `ResonatorPartBlock.Kind` (`ENERGY_PORT, CRYSTAL_PORT, TUNER`), `ResonatorPartBlock#kind()`, `ResonatorPartBlock.FACING` (`DirectionProperty`, todas as seis; ao colocar, a face clicada do jogador, `context.getClickedFace()`) e `ResonatorPartBlock.FORMED` (`BooleanProperty`, padrão `false`).
  - `ResonanceCoreBlock` com `RouterBlock.TIER`, `FACING` (como as peças) e `FORMED`; `EntityBlock` com `ResonanceCoreBlockEntity`; sem `getTicker`.
  - `ResonanceCoreItem.withTier(Item, RouterTier) → ItemStack` e `ResonanceCoreItem.tierOf(ItemStack) → RouterTier` (pelo componente `DataComponents.BLOCK_STATE`, como o `StorageBlockItem`), dica com o tier e o número de câmaras.
  - `ModBlocks.RESONATOR_CASING`, `RESONATOR_GLASS`, `RESONANCE_CORE`, `RESONATOR_ENERGY_PORT`, `RESONATOR_CRYSTAL_PORT`, `RESONATOR_TUNER`; `ModItems` com os mesmos nomes (o do Núcleo é `DeferredItem<ResonanceCoreItem>`); `ModBlockEntities.RESONANCE_CORE`.
  - `Config.resonatorFePerCrystal(RouterTier) → long`, `Config.crystalLifeTicks(boolean cut) → long`, `Config.rechargeTicks() → long` (lidos com o `Config.read`), na seção `resonator` com as chaves e padrões das Restrições globais (os padrões por tier num array pelo ordinal, como o `StorageKind`).
  - `ResonatorTestSupport.build(GameTestHelper helper, BlockPos origin, Layout layout, RouterTier tier) → BlockPos` (põe os 98 blocos e devolve a posição relativa do Núcleo) com `Layout.VITRINE` (a mesma do `ResonatorLayoutTest`, origem relativa `(1, 1, 1)`: janela 3×3 ao sul, Núcleo no centro da face leste, Porta de Energia no centro da oeste, Porta de Cristais no centro da norte, Sintonizador no centro da de cima) e `Layout.ENERGY_ON_TOP` (igual, mas a Porta de Energia no centro da face de cima e sem Sintonizador), e `ResonatorTestSupport.core(helper, corePos) → ResonanceCoreBlockEntity`.

Detalhes:
- Propriedades dos blocos: mapColor `QUARTZ` (carcaça, peças, Núcleo) e o do vidro do vanilla; dureza 3,0, resistência 6,0, som `STONE` (carcaça, peças, Núcleo) e `GLASS` (vidro); todos com `requiresCorrectToolForDrops` menos o vidro; carcaça, peças e Núcleo em `mineable/pickaxe`.
- Loot: cada bloco solta a si mesmo com `survives_explosion`; o Núcleo com `copy_state` de `tier` (como o `router.json`). Os estados `POSITION`, `FACE`, `CONNECTIONS` e `FORMED` **não** vão para o item.
- Ao colocar, todos nascem soltos (`LOOSE`, `formed=false`); as peças com o `FACING` da face clicada.
- `empty7.nbt`: o mesmo conteúdo do `structure/empty.nbt` com o `size` `[7, 7, 7]`. Gere com:

```bash
python -I -c "import gzip,struct;d=gzip.decompress(open('src/main/resources/data/wirelessautomate/structure/empty.nbt','rb').read());a=struct.pack('>iii',3,3,3);assert d.count(a)==1;open('src/main/resources/data/wirelessautomate/structure/empty7.nbt','wb').write(gzip.compress(d.replace(a,struct.pack('>iii',7,7,7))))"
```

- Receitas (bancada; `crafting_shaped` ou `crafting_shapeless`, para o editor de receitas): Carcaça ×4 = padrão `TIT`/`ICI`/`TIT` com `T` = `minecraft:white_terracotta`, `I` = `minecraft:iron_ingot`, `C` = `wirelessautomate:signal_crystal`; Vidro ×4 = ` G `/`GCG`/` G ` com `G` = `minecraft:glass`; Núcleo ×1 = `KKK`/`KLK`/`KDK` com `K` = carcaça, `L` = `wirelessautomate:cut_signal_crystal`, `D` = `minecraft:diamond`; Porta de Energia = carcaça + `minecraft:redstone_block` (sem forma); Porta de Cristais = carcaça + `minecraft:hopper`; Sintonizador = carcaça + `minecraft:clock` + `minecraft:comparator`.
- Aba criativa: depois dos cristais, a carcaça, o vidro, o Núcleo em cada tier carregado (`ResonanceCoreItem.withTier`, só `tier.loaded()`), as duas Portas e o Sintonizador.
- Textos: os seis nomes nos dois idiomas; a dica do Núcleo `"block.wirelessautomate.resonance_core.tooltip": "%s · %s crystal chambers"` / `"%s · %s câmaras de cristal"`.
- Os modelos e texturas são da Task 3: o aviso de modelo ausente é esperado.

- [ ] **Step 1: GameTests que falham** (em `ResonatorFormationGameTests`, template `"empty7"`):
  - `blocosNascemSoltos`: `ResonatorTestSupport.build(..., VITRINE, ELITE)` sem montar (o Núcleo ainda não monta nesta tarefa: confira só os estados iniciais): carcaça com `POSITION = LOOSE`, vidro com `FACE = LOOSE`, peças e Núcleo com `FORMED = false`, Núcleo com `TIER = ELITE`.
  - `nucleoQuebradoVoltaComOTier`: Núcleo `EMERALD`, `Block.getDrops(...)` com picareta de ferro dá um `resonance_core` com `ResonanceCoreItem.tierOf == EMERALD`.
  - `receitasDoRessonador`: pelo `RecipeManager` (como em `RecipeGameTests`), cada uma das seis receitas devolve o item e a quantidade certos.
  - `configDoRessonadorTemOsPadroes`: `Config.resonatorFePerCrystal(RouterTier.BASIC) == 64`, `(ULTIMATE) == 134_217_728L`, `Config.crystalLifeTicks(false) == 12_000`, `(true) == 24_000`, `Config.rechargeTicks() == 600`.
- [ ] **Step 2: Compile e veja falhar** (`./gradlew compileJava` → `BUILD FAILED`).
- [ ] **Step 3: Implemente** os arquivos acima.
- [ ] **Step 4: Rode** `./gradlew build runGameTestServer` e veja os 4 passarem.
- [ ] **Step 5: Verificação completa, varredura, `medir.py` e commit** (`"Ressonador: os seis blocos, o item do Núcleo com tier, config, receitas e o template 7×7×7"`).

---

### Task 3: Texturas, modelos e blockstates

**Files:**
- Modify: `scripts/textures/blocos.py`
- Gerados: `R/assets/wirelessautomate/textures/block/resonator_*.png` (+ `.mcmeta` nas animadas), `models/block/resonator_*.json` e `resonance_core_*.json`, `models/item/*.json` dos seis, `blockstates/*.json` dos seis, `docs/preview/folha-de-sprites.png`

**Interfaces:**
- Consumes: os estados da Task 2 (`position`, `face`, `connections`, `facing`, `formed`, `tier`) e a convenção das faces da spec.
- Produces: só arquivos gerados.

Regras (leve as funções do `docs/preview/ressonador-proto.py` para o `blocos.py`, renomeando para não colidir; mantenha os pixels; o protótipo foi desenhado para faces 5×5, a v7 aprovada):
- **Carcaça montada:** para cada `CasingPosition` diferente de `LOOSE`, um modelo de cubo inteiro. Em cada uma das 6 faces do bloco: se a face aponta para fora da estrutura (o sinal da letra daquele eixo bate com a direção da face: `n` para baixo/norte/oeste, `p` para cima/sul/leste), a textura é escolhida pelas bordas daquela face: a borda "cima" da textura existe se o vizinho na direção "cima" da face (tabela da spec) cai fora da caixa, e igual para direita, baixo e esquerda; nenhuma borda = campo; uma = barra; duas vizinhas = canto (o `chave(bordas)` do protótipo nomeia cada combinação). Nas faces laterais (norte, sul, leste, oeste) com borda "baixo", use a variante com a listra coral (`trilhos(..., coral=True)`). Faces que não apontam para fora levam o campo (ficam escondidas). Gere uma textura por combinação usada (sem rotação de UV: o desenho de cada combinação já sai na orientação certa).
- **Carcaça solta:** `carcaca_solta()` nas seis faces.
- **Vidro:** para cada face `f` e cada `connections` 0 a 15, um modelo translúcido de cubo inteiro: na face `f` a textura `vidro(bits)`; na face oposta (a de dentro) a mesma textura espelhada na horizontal (UV `[16, 0, 0, 16]`), com os bits de esquerda e direita trocados no desenho já resolvidos pelo espelho; nas outras quatro, a textura do vidro sem aro (`vidro(15)`). Solto: `vidro(..., montado=False)` (fosco) nas seis faces, igual para todos os `connections`. O blockstate tem as 112 combinações (`face=loose` aponta sempre para o modelo solto).
- **Peças e Núcleo:** um modelo por peça e por `formed`, virado para o norte: a face norte com a textura da peça (`porta_energia`, `porta_cristais`, `sintonizador`, `nucleo(tier)`), a face sul com a vista de dentro (`porta_dentro`, `nucleo_dentro(tier)`; o Sintonizador usa o `forro`), as outras quatro com o campo (montado) ou a carcaça solta (solto). O blockstate gira pelo `facing` com `x`/`y` (norte sem rotação; sul `y=180`; leste `y=90`; oeste `y=270`; cima `x=270`; baixo `x=90`), com `uvlock: false`. O Núcleo tem um modelo por tier × `formed` (16), e o blockstate combina `facing`, `formed` e `tier`.
- **Itens:** carcaça e vidro usam o modelo solto; as peças o solto virado para o sul (para a frente aparecer no inventário, com `display` padrão de bloco); o Núcleo, o solto Básico, com `overrides` pelo predicado `wirelessautomate:tier` (registre esse predicado para o `RESONANCE_CORE` no `ClientSetup`, como o do roteador, e acrescente o `ClientSetup.java` aos arquivos desta tarefa).
- **Animações** do protótipo (Olho do Núcleo, ponteiro do Sintonizador, raio da Porta de Energia), com os frametimes dele; nenhuma textura com alfa parcial, menos as do vidro.
- Acrescente os seis blocos à folha de sprites.

- [ ] **Step 1:** Leve as funções e escreva a geração.
- [ ] **Step 2:** Rode `python -I -B scripts/textures/gerar_texturas.py`; restaure o que não é desta tarefa (`git checkout --`).
- [ ] **Step 3:** Confira a folha e monte uma prévia sua (fora do repositório) com as seis faces 5×5 da Vitrine montada lado a lado a partir dos PNGs e modelos gerados, como a prancha `docs/preview/ressonador-prancha.png`: a moldura só na borda de fora, a listra coral no rodapé das laterais, o vidro com aro só no contorno, as peças no centro. Descreva no relatório o que viu.
- [ ] **Step 4:** `./gradlew build runGameTestServer` (os modelos não quebram nada no servidor, mas o JSON precisa ser válido), varredura e commit (`"Ressonador: texturas conectadas, modelos e blockstates da estrutura"`).

---

### Task 4: Montar e desmontar

**Files:**
- Create: `B/resonator/ResonatorManager.java`, `B/resonator/ResonatorFormation.java`, `B/resonator/ResonatorPorts.java` (só o índice nesta tarefa)
- Modify: `B/resonator/ResonanceCoreBlockEntity.java`, `ResonanceCoreBlock.java`, `ResonatorCasingBlock.java`, `ResonatorGlassBlock.java`, `ResonatorPartBlock.java`, `B/WirelessAutomate.java`
- Modify: `B/gametest/ResonatorFormationGameTests.java`

**Interfaces:**
- Consumes: `ResonatorLayout.check` (Task 1), os blocos e estados (Task 2).
- Produces:
  - `ResonanceCoreBlockEntity#formed() → boolean`, `#members() → List<BlockPos>` (as 98 posições da casca, salvas no NBT como `long`), `#tier() → RouterTier` (do blockstate), `#lastProblem() → ResonatorLayout.Broken` (ou `null`, não salvo).
  - `ResonatorFormation.revalidate(ServerLevel level, ResonanceCoreBlockEntity core)`: lê as 125 posições (com `level.isLoaded` antes de cada leitura; posição descarregada vale `OTHER`), chama `ResonatorLayout.check` e: se montou e não estava, grava os estados (`setBlock` com flag `Block.UPDATE_CLIENTS`), registra as Portas no índice, `level.invalidateCapabilities` em cada Porta, sorteia a estação se ainda não tem, toca `SoundEvents.BEACON_ACTIVATE` no Núcleo e manda "Ressonador montado" (`block.wirelessautomate.resonance_core.formed`) na action bar dos jogadores a até 16 blocos; se montou e já estava, regrava só os estados que mudaram (por exemplo, um vidro novo na face); se não montou e estava, desmonta (volta cada membro ainda presente para o estado solto, tira as Portas do índice, invalida as capabilities); guarda o `Broken` em `lastProblem`.
  - `ResonatorManager.add(ResonanceCoreBlockEntity)`, `remove(...)`, `structureChanged(ServerLevel level, BlockPos pos)` (revalida os Núcleos carregados a até 4 blocos de distância de Chebyshev de `pos`), `reset()` (parada do servidor), `tick(MinecraftServer)` (nesta tarefa vazio; a Task 5 enche).
  - `ResonatorPorts.register(ResourceKey<Level>, BlockPos port, BlockPos core)`, `unregister(...)`, `core(Level, BlockPos port) → ResonanceCoreBlockEntity` (ou `null`: porta fora do índice, Núcleo descarregado ou não montado), `reset()`.
- Ganchos: o `onPlace` e o `onRemove` de todos os blocos do Ressonador chamam `ResonatorManager.structureChanged`, **menos** quando só o estado muda no mesmo bloco (`oldState.is(newState.getBlock())`), para a regravação de estados não disparar uma revalidação. Blocos que não são do Ressonador no interior: o Núcleo revalida no `onLoad` (no tick seguinte, como o `SourceTankLevels`, por `level.scheduleTick`) e quando um bloco do Ressonador muda; um bloco posto no interior sem mexer na casca só é notado na próxima revalidação (é preciso abrir a casca para chegar lá).
- O `onLoad` do Núcleo (servidor): `ResonatorManager.add`, agenda a revalidação; o `setRemoved`/`onChunkUnloaded`: `ResonatorManager.remove` e tira as Portas do índice (sem desmontar os estados). Quebrar o Núcleo desmonta.
- `WirelessAutomate`: no tick do servidor chame `ResonatorManager.tick(server)`; na parada, `ResonatorManager.reset()` e `ResonatorPorts.reset()`.

- [ ] **Step 1: GameTests que falham** (template `"empty7"`, cada um com `startSequence().thenWaitUntil(...)` para o `onLoad`):
  - Ajuste o `blocosNascemSoltos` da Task 2: com a montagem ligada, a Vitrine completa monta. Construa-a sem a Porta de Cristais (troque-a por carcaça) e confira os mesmos estados soltos depois de o Núcleo carregar e revalidar.
  - `vitrineMonta`: depois de `build(VITRINE)`, espere `core.formed()`; confira `POSITION` de uma aresta (`"cnn"` → `CasingPosition.CNN`) e de um campo da face de cima, `FACE = SOUTH` e `CONNECTIONS = 15` no vidro do centro da janela, `FORMED = true` nas peças com `FACING` para fora (Porta de Energia `WEST`), e que `ResonatorPorts.core(level, portaDeEnergia)` devolve o Núcleo.
  - `quebrarUmaCarcacaDesmonta`: monte, quebre uma carcaça de aresta (`level.destroyBlock`), espere `!core.formed()`; as outras voltam a `LOOSE`, as peças a `FORMED = false`, e `ResonatorPorts.core(...)` vira `null`.
  - `repararRemonta`: depois de quebrar, recoloque a carcaça e espere `core.formed()` de novo.
  - `semPortaDeCristaisNaoMonta`: troque a Porta de Cristais por carcaça antes de o Núcleo carregar; espere o `lastProblem()` com `MISSING_CRYSTAL_PORT`; `formed()` falso.
  - `estacaoNasceNaFaixa`: depois de montar, a estação fica entre 88,5 e 107,5 e não muda numa segunda revalidação.
  - `vidroNovoNaFaceRegravaAsConexoes`: monte a Vitrine; troque uma carcaça do miolo da face de cima por vidro; espere o vidro com `FACE = UP` e a estrutura ainda montada.
- [ ] **Step 2: Compile e veja falhar.**
- [ ] **Step 3: Implemente.** O NBT do Núcleo: `formed` (boolean), `members` (`long[]`), `station`, `velocity`, `dial` (double), `has_station` (boolean). Nada por tick: a revalidação só roda pelos ganchos.
- [ ] **Step 4: Rode** `./gradlew build runGameTestServer`.
- [ ] **Step 5: Verificação completa, varredura, `medir.py` e commit** (`"Ressonador: montar e desmontar a estrutura, com os estados conectados e o índice das portas"`).

---

### Task 5: O lote do Núcleo (geração, deriva, desgaste e recarga)

**Files:**
- Create: `B/resonator/ResonatorBatch.java`
- Modify: `B/resonator/ResonanceCoreBlockEntity.java`, `B/resonator/ResonatorManager.java`
- Create: `B/gametest/ResonatorCoreGameTests.java`

**Interfaces:**
- Consumes: `Tuning`, `CoreMath` (Task 1); `CrystalCharge`, `SignalCrystalItem` (etapa 1); `Config` (Task 2); o Núcleo montado (Task 4).
- Produces:
  - `ResonanceCoreBlockEntity#crystals() → NonNullList<ItemStack>` (8 posições; só as `CoreMath.chambers(tier)` primeiras valem), `#insertCrystal(ItemStack) → ItemStack` (o que sobrou), `#takeLastCrystal() → ItemStack`, `#redstone() → int` (0 a 64), `#addRedstone(int) → int` (quanto entrou), `#energy() → long`, `#capacity() → long` (`CoreMath.buffer(chambers, Config.resonatorFePerCrystal(tier))`), `#extractEnergy(long, boolean simulate) → long`, `#station() → double`, `#dial() → double`, `#setDial(double)` (passa pelo `Tuning.clampDial`), `#efficiency() → double` (`Tuning.efficiency(station − dial)`), `#generationPerTick() → long`, `#setStationForTest(double)` (só para os GameTests e o comando de info não usa).
  - `ResonatorBatch.run(ServerLevel level, ResonanceCoreBlockEntity core, int ticks)`: um lote de `ticks` (20 pelo gerenciador): conta os lotes e a cada 20 dá um passo de deriva (`Tuning.step` com `level.random.nextGaussian()`); se montado, gera `min(perTick × ticks, capacity − energy)` (com `CoreMath.perTick(fePerCrystal, soma dos rendimentos dos cristais não Opacos, efficiency)`); se gerou algo, gasta `ticks` de cada cristal não Opaco (`CrystalCharge.drain` com `Config.crystalLifeTicks(cut)`); recarga: se há Opaco e redstone, soma `ticks` ao progresso; ao chegar a `Config.rechargeTicks()`, tira 1 redstone, enche o primeiro Opaco (`SignalCrystalItem.setCharge(stack, CrystalCharge.FULL)`) e zera o progresso; chama `setChanged()` quando algo mudou.
  - `ResonatorManager.tick(MinecraftServer)`: para cada Núcleo montado e carregado, roda `ResonatorBatch.run(..., 20)` quando `(gameTime + Math.floorMod(pos.hashCode(), 20)) % 20 == 0`.
- NBT novo no Núcleo: `crystals` (lista de `ItemStack` com o slot), `redstone` (int), `recharge` (int), `energy` (long), `batches` (int).
- Desgaste só com geração: buffer cheio ou eficiência 0 não gasta.

- [ ] **Step 1: GameTests que falham** (`ResonatorCoreGameTests`, template `"empty7"`; os que mudam config no lote `"wirelessautomate_config"`):
  - `emSintoniaGeraOMaximoDosCristais`: Vitrine Elite montada, `setStationForTest(98.0)`, `setDial(98.0)`, 3 Cristais de Sinal; `ResonatorBatch.run(level, core, 20)` direto; `energy() == 3 × 4_096 × 20`.
  - `foraDaEstacaoNaoGeraNemGasta`: `setStationForTest(98.0)`, `setDial(90.0)`; depois de `run(..., 20)`, energia 0 e a carga dos cristais `CrystalCharge.FULL`.
  - `lapidadoRendeODobro`: 1 Lapidado em sintonia no Básico: `energy() == 64 × 2 × 20`.
  - `bufferCheioParaAGeracao`: encha até a capacidade (rode lotes); a carga de um cristal não muda num lote com o buffer cheio.
  - `cristalGastaNaVidaDaConfig` (lote `"wirelessautomate_config"`): `crystalLifeTicks` = 40 em `try/finally`; em sintonia, depois de 2 lotes o cristal fica Opaco.
  - `redstoneRecarregaOOpaco` (lote `"wirelessautomate_config"`): `rechargeTicks` = 40; um Opaco e 3 de redstone; depois de 2 lotes o cristal está cheio e sobram 2 de redstone.
  - `semRedstoneOOpacoFica`: um Opaco e nenhuma redstone; o cristal continua Opaco depois de vários lotes.
  - `gerenciadorRodaOsLotes`: com a estrutura montada e em sintonia, `thenWaitUntil(() -> core.energy() > 0)` sem chamar o `ResonatorBatch` à mão (prova o caminho real do `ResonatorManager.tick`).
  - `desmontadoNaoGera`: quebre uma carcaça; espere `!formed()`; `run(..., 20)` não muda a energia.
- [ ] **Step 2–5:** veja falhar, implemente, rode, verificação completa e commit (`"Ressonador: lote do Núcleo com geração pela sintonia, deriva, desgaste e recarga"`).

---

### Task 6: As Portas

**Files:**
- Modify: `B/resonator/ResonatorPorts.java`
- Create: `B/resonator/ResonatorCapabilities.java`
- Modify: `B/resonator/ResonatorBatch.java` (aviso às portas), `B/WirelessAutomate.java` (registro do listener)
- Create: `B/gametest/ResonatorPortGameTests.java`

**Interfaces:**
- Consumes: o Núcleo (Tasks 4 e 5), `BulkEnergy` (existente), o roteador e as redes (existentes, ver `gametest/StorageGameTests` para os helpers de roteador e rede).
- Produces:
  - `ResonatorPorts.CoreEnergy implements BulkEnergy` (`insert` devolve 0; `extract` tira do Núcleo) e `ResonatorPorts.CoreEnergyHandler implements IEnergyStorage` (`receiveEnergy` 0, `canReceive` false; `extractEnergy` e os getters limitados a `int`, como o `EnergyStoreHandler`).
  - `ResonatorPorts.CoreItems implements IItemHandler`: slots = as câmaras do tier + 1 (o último é a redstone). `insertItem` numa câmara: só `SignalCrystalItem`, uma unidade por câmara vazia; no slot da redstone: só `Items.REDSTONE`, até 64. `extractItem`: só de câmara com Opaco **e** com o Núcleo sem redstone; a redstone nunca sai. `getSlotLimit`: 1 nas câmaras, 64 na redstone. `isItemValid` segue as mesmas regras.
  - `ResonatorCapabilities.register(RegisterCapabilitiesEvent)`: `registerBlock` de `Capabilities.EnergyStorage.BLOCK` e de `BulkEnergy.BLOCK` na Porta de Energia, e de `Capabilities.ItemHandler.BLOCK` na Porta de Cristais, todos pelo `ResonatorPorts.core(level, pos)` (sem Núcleo montado: `null`).
  - Aviso: no fim do lote, se a energia passou de 0 para mais que 0, ou se uma câmara ficou vazia, ou se a redstone baixou, chame `level.updateNeighbourForOutputSignal(porta, bloco da porta)` em cada Porta do tipo afetado (só essas transições; nada todo lote).

- [ ] **Step 1: GameTests que falham** (`ResonatorPortGameTests`, template `"empty7"`):
  - `portaDeEnergiaSoTira`: Vitrine montada com energia (`ResonatorBatch.run`); `level.getCapability(Capabilities.EnergyStorage.BLOCK, porta, Direction.WEST)` não nulo; `receiveEnergy(100, false) == 0`; `extractEnergy(1_000, false) == 1_000` e a energia do Núcleo baixa 1.000; `BulkEnergy.BLOCK` tira em `long`.
  - `portaSemEstruturaNaoTemCapability`: Porta de Energia solta (sem Núcleo): a capability é `null`; depois de desmontar uma Vitrine, também.
  - `portaDeCristaisAceitaCristalERedstone`: pela `IItemHandler`, um Cristal de Sinal entra numa câmara vazia (sobra 0 de 1) e um segundo na próxima; uma pedra é recusada; 70 de redstone: entram 64.
  - `opacoSoSaiSemRedstone`: um Opaco e 1 de redstone: `extractItem` da câmara devolve vazio; sem redstone: devolve o Opaco; um cristal com carga nunca sai.
  - `roteadorTiraEnergiaDaPorta`: `Layout.ENERGY_ON_TOP` em sintonia, com cristais; um roteador em cima da Porta de Energia extraindo energia numa rede nova e um segundo roteador em cima de uma Bateria do mod inserindo na mesma rede (os helpers de `StorageGameTests`); `thenWaitUntil` a Bateria com energia (prova o caminho real: lote → aviso → roteador → capability).
- [ ] **Step 2–5:** veja falhar, implemente, rode, verificação completa e commit (`"Ressonador: Portas de Energia e de Cristais com capability pelo índice"`).

---

### Task 7: Interação no Núcleo, Cartão de Upgrade e comando

**Files:**
- Create: `B/resonator/ResonatorStatus.java`, `B/command/ResonatorCommand.java`
- Modify: `B/resonator/ResonanceCoreBlock.java`, `B/item/TierCoreItem.java`, `B/command/WaCommand.java`, `lang/en_us.json`, `lang/pt_br.json`
- Modify: `B/gametest/ResonatorCoreGameTests.java`

**Interfaces:**
- Consumes: tudo das Tasks 4 a 6.
- Produces:
  - `ResonanceCoreBlock.useItemOn`: com Cristal de Sinal ou Lapidado, `insertCrystal` (consome 1 se entrou, fora do criativo); com pó de redstone, `addRedstone` (consome o que entrou); com `TierCoreItem`, `SKIP_DEFAULT_BLOCK_INTERACTION` (o cartão age pelo `useOn`, como no roteador). `useWithoutItem`: Shift tira o último cristal para a mão (`takeLastCrystal`); sem Shift, manda o `ResonatorStatus.line(core)` na action bar.
  - `ResonatorStatus.line(ResonanceCoreBlockEntity) → Component`: montado: `"%tier · %cristais/%camaras crystals · %efic% · %fe FE/t"` (chave `block.wirelessautomate.resonance_core.status`); solto: a chave do problema (`block.wirelessautomate.resonance_core.problem.<problem em minúsculas>`) com a posição (`x y z`) quando houver.
  - `ResonanceCoreBlock.tryUpgrade(Level, BlockPos, RouterTier target, @Nullable RouterTier origin) → boolean` (como o do `StorageBlock`: confere `acceptsCard`, troca só o `TIER` do blockstate, mantém o block entity; depois do `setBlock`, chame `ResonatorFormation.revalidate` para a capacidade e as câmaras novas valerem). No `TierCoreItem.useOn`, o Núcleo entra como o terceiro caso, com as chaves `item.wirelessautomate.tier_core.refused.core` e `upgraded.core` (`"%s → %s"` com o nome do bloco, como `upgraded.storage`).
  - `/wa resonator <pos> info` (permissão 2): estação, dial, desvio, eficiência, FE/t, energia/capacidade, cristais, redstone; `/wa resonator <pos> dial <mhz>` (`DoubleArgumentType.doubleArg(88, 108)`): `setDial`; erros `command.wirelessautomate.resonator.not_core`. Registrado no `WaCommand` como os outros nós (`ResonatorCommand.node()`).
  - Quebrar o Núcleo derruba os cristais e a redstone (`Containers.dropContents` ou `Block.popResource`), e a energia se perde.
- Textos nos dois idiomas, entre eles os seis problemas (por exemplo `problem.missing_energy_port`: "Missing a Resonator Energy Port" / "Falta uma Porta de Energia"; `problem.wrong_block`: "Wrong block at %s" / "Bloco errado em %s"; `problem.interior_not_empty`: "The chamber must be empty (block at %s)" / "A câmara precisa estar vazia (bloco em %s)"; `problem.extra_core`: "Second Resonance Core at %s" / "Segundo Núcleo em %s"; `problem.missing_crystal_port`: "Missing a Resonator Crystal Port" / "Falta uma Porta de Cristais"; `problem.too_many_tuners`: "Only one Tuner per Resonator (another at %s)" / "Só um Sintonizador por Ressonador (outro em %s)").

- [ ] **Step 1: GameTests que falham:**
  - `cliqueComCristalPoeNoNucleo` e `cliqueComRedstonePoeNoNucleo` (pelo `useItemOn` do estado, com um jogador de teste em sobrevivência; o item da mão diminui).
  - `shiftCliqueTiraOUltimoCristal`.
  - `statusMostraOProblema`: sem Porta de Cristais, a linha do status tem a chave `problem.missing_crystal_port`.
  - `cartaoSobeOTierEAumentaAsCamaras`: Núcleo Básico montado; `TierCoreItem.card(ADVANCED, BASIC)` pelo `useOn` (como em `TierUpgradeGameTests`); o `TIER` vira `ADVANCED`, o block entity é o mesmo, a estrutura continua montada e a `capacity()` é a do Avançado.
  - `cartaoErradoRecusa`: `card(ELITE, ADVANCED)` num Núcleo Básico devolve `FAIL` e não muda o tier.
  - `quebrarNucleoDerrubaOsCristais`: 2 cristais e 5 de redstone; `player.gameMode.destroyBlock`; há entidades de item com os 2 cristais e as 5 de redstone, e o Núcleo derrubado com o tier.
  - `comandoDialMudaODial`: rode `/wa resonator <x> <y> <z> dial 97.42` pelo `server.getCommands().performPrefixedCommand` com uma fonte de permissão 2; `dial() == 97.40` (passo de 0,05).
- [ ] **Step 2–5:** veja falhar, implemente, rode, verificação completa e commit (`"Ressonador: clique no Núcleo, Cartão de Upgrade e o comando /wa resonator"`).

---

### Task 8: Documentação

**Files:**
- Modify: `CLAUDE.md`, `docs/progresso.md`, `docs/especificacao.md`

- [ ] **Step 1:** `CLAUDE.md`: na linha do `resonator/`, os papéis novos (`ResonatorLayout`, `Tuning`, `CoreMath` puros com JUnit; os blocos e estados; `ResonanceCoreBlockEntity`; `ResonatorManager` com o lote a cada 20 ticks; `ResonatorFormation`; `ResonatorBatch`; `ResonatorPorts` e o índice; `ResonatorStatus`; `ResonatorCapabilities`), o `command/ResonatorCommand`, o template `empty7` e os GameTests novos com as contagens reais; as três classes puras na lista de lógica pura; na linha do `blocos.py`, os seis blocos do Ressonador.
- [ ] **Step 2:** `docs/progresso.md`: a linha do Ressonador (etapa 2 pronta; falta a etapa 3: tela, Sintonizador automático, comparador, redstone e a câmara), o próximo passo e o histórico com os números reais. Continua valendo: não publicar versão com o Ressonador antes da etapa 3 se a dica ou o guia prometerem a tela; diga isso se ainda valer.
- [ ] **Step 3:** `docs/especificacao.md`: marque a etapa 2 no item do roadmap.
- [ ] **Step 4:** Verificação completa e commit (`"Ressonador: documentação da etapa 2"`).
