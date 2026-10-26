# Reparador de Cristais, etapa 2 (mecânica e bloco, sem animação) · plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** o bloco Reparador de Cristais funcionando: recebe cristais rachados, cristais de Sinal cheios e energia (por qualquer meio), repara até 8 cristais em paralelo, devolve os reparados, com tela, JEI, guia, receita e GameTests. A animação do BER e o shader são a etapa 3.

**Arquitetura:** pacote novo `repairer/`. A lógica de custo e tempo fica pura (`RepairMath`, JUnit). O `RepairerBlockEntity` guarda 17 slots (8 rachados, 1 estoque de cristais cheios, 8 saídas), um buffer de energia (`ScalarStore`) e um fim por linha (`gameTime`); **não faz tick**: um `RepairerManager` estático (o padrão do `SourceTankLevels`) guarda só os Reparadores com linha trabalhando e roda a cada tick do servidor, vazio custa uma checagem. Tudo que começa ou destrava um reparo é evento (inserir cristal, energia chegando, saída esvaziando). Capabilities: itens (regra por slot), `IEnergyStorage` e `BulkEnergy` só de entrada. A tela usa um payload de estado só com a tela aberta e só quando muda.

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), JUnit 5, GameTests, Python 3 (gerador de texturas e do guia), e2e do cliente.

**Spec:** [`docs/superpowers/specs/2026-10-26-reparador-de-cristais-design.md`](../specs/2026-10-26-reparador-de-cristais-design.md) (decisões 3 a 7). **Mapa dos padrões do código:** [`2026-10-26-reparador-mapa-de-padroes.md`](2026-10-26-reparador-mapa-de-padroes.md): **todo implementador lê a seção da sua tarefa antes de começar**, e confere os caminhos e linhas no código (eles envelhecem). **Mockup aprovado da tela e da animação:** `docs/preview/reparador-de-cristais.html`. **Modelo 3D aprovado:** `docs/preview/reparador-de-cristais.bbmodel`.

## Decisões deste plano (o dono pode trocar antes da Tarefa 7)

1. **Receita de bancada (proposta):** `KCK / HLH / KRK`, K = `wirelessautomate:resonator_casing`, C = `wirelessautomate:phase_circuit`, H = `minecraft:hopper`, L = `wirelessautomate:harmonic_lens`, R = `minecraft:redstone_block`; resultado 1. (Usa a Carcaça do Ressonador e a cascata, sem tier e sem mod opcional.)
2. **Os slots de rachados aceitam 1 cristal cada** (como as câmaras do Núcleo); o estoque de cristais cheios aceita uma pilha de até 64; as saídas empilham até 64.
3. **O que conta como "cristal de Sinal cheio" no estoque** é a regra da `RecastCrystalRecipe` (item `signal_crystal` comum com `SignalCrystalItem.isFull`: só a carga, a integridade não importa).
4. **Automação não tira o cristal da linha de entrada** (nem rachado nem em reparo): a entrada é só para pôr. O jogador pela tela pode tirar um rachado que ainda não começou; durante o reparo a linha fica travada.
5. **O reparo começa quando a linha tem rachado, o estoque tem `recastCrystals` (4) cristais, o buffer tem o custo e há saída livre no fim** (sem saída livre, a linha termina travada e destrava quando abre espaço).
6. **A energia só entra** (`canExtract` falso nas duas capabilities): nenhum roteador tira energia do Reparador.
7. **Custo por reparo** = `costFactor` (config, padrão 2) × o FE por cristal da config do tier: Sinal = tier Básico (432 com os padrões), Lapidado = tier Avançado (13.824), Afinado = o tier dele (Elite 20.000 … Ultimate 536.870.912). A capacidade do buffer = `costFactor` × FE por cristal do Ultimate × 8 linhas. Config nova (seção `repairer`): `repairTicks` (padrão 140) e `costFactor` (padrão 2).
8. **A tela do bloco, na etapa 2, tem o centro estático** (anel de 8 pedestais com o ícone do cristal e a barra de progresso por linha); a câmara animada da tela vem na etapa 3.
9. **Modelo:** o JSON do bloco vem do `.bbmodel` aprovado por um script próprio e fica numa subpasta (`models/block/repairer/`), porque o gerador `blocos.py` apaga os modelos da raiz que ele não gerou e não aceita elementos que se atravessam; o vidro é um segundo modelo translúcido (blockstate `multipart`). A textura é um atlas pintado à mão no Blockbench (`reparador_alma`, 256 × 256, embutida no `.bbmodel`) e gravado em `textures/block/repairer/repairer.png`; não há paleta 16 × 16. O item é um `neoforge:composite` (sólido + vidro). O brilho do emissor, das runas e das luzes é do BER (etapa 3); na etapa 2 essas peças são texels claros.

## Restrições globais

- Código, comentários, commits e docs em português; textos nos dois `lang` (`en_us.json`, `pt_br.json`), sempre os dois, na mesma ordem.
- **Chaves existentes não mudam.** Novas: config `repairer.repairTicks` (long, padrão 140, mínimo 1) e `repairer.costFactor` (int, padrão 2, 0 a 1000; 0 repara de graça); NBT do Reparador `items` (lista de pilhas com `slot`), `energy` (long) e `lines` (int[8]: 0 parada, -1 terminou e espera saída, > 0 ticks que faltam); blockstate `facing` (horizontal); payload `RepairerStatePayload`, e por isso **`ModPayloads.VERSION` sobe de `"32"` para `"33"`** (só na Tarefa 5).
- **Performance (skill `wa-performance`; leia `.claude/skills/wa-performance/SKILL.md`):** parado custa zero (nenhum Reparador no gerenciador sem linha trabalhando; `RepairerManager.tick` sem nada é `isEmpty()`); nada de tick por bloco; sem alocação no caminho quente (o `tick` do gerenciador reusa um array, nada de stream, lambda, `values()` de enum, `List.of`); o servidor só manda a tela quando o estado muda e com a tela aberta (`pollState` a cada 5 ticks); o cliente anima por `gameTime` de início, nada por tick; `setChanged()` só nas transições (começar, terminar, destravar), `level.blockEntityChanged(pos)` para o resto; aviso aos vizinhos só nas transições que um roteador precisa ver (saída que ganha ou perde cristal, energia que o buffer passou a aceitar). Rode `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos mudados e resolva ou justifique cada candidato.
- Lógica pura com JUnit (`src/test/java`); o que depende do jogo, com GameTest (`@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`, `template = "empty"`, `timeoutTicks` ≤ 300; jogador só por `TestPlayers.inLevel`; um teste que muda a config vai em `batch = "wirelessautomate_config_<assunto>"` com `try/finally`; testes do mesmo lote rodam em paralelo; o `onLoad` do block entity só roda no tick seguinte, então `startSequence().thenWaitUntil(...)`). Os GameTests deste plano **não esperam o tempo de reparo**: avançam o relógio chamando `RepairerBlockEntity.tick(long now)` com `now` no futuro.
- Mod opcional: nada de tipos do Mekanism, Ars, FTB Teams ou Allthemodium neste pacote.
- Lado do cliente: telas só em `client/` (e `client/repairer/`), nunca referenciadas por código comum.
- Todo texto variável de tela passa por `GuiText` (o e2e confere nenhum texto cortado sem tooltip). Tema claro e escuro por `GuiPaint`; nenhuma tela guarda cor.
- Tamanho (`wa-modular`): classe de tela até 500 linhas, servidor até 600 (dividir acima de 1000); rode `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados. Comentários (`wa-comentarios`): `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados.
- Texturas e guia: edite `scripts/textures/*.py` e `scripts/guide/gerar_guia.py`, nunca os PNGs ou `.md` gerados; o `gerar_texturas.py` reescreve dezenas de PNGs nesta máquina: commite só o que a mudança pede.
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams` (Git Bash, na raiz); tarefa só de lógica pura roda `./gradlew test --tests "..."` e a suíte inteira só na Tarefa 7. Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, pare e conserte, nunca espere um resultado que não vem. Nunca dois jogos ao mesmo tempo (~3 GB cada); não mexa em `run/options.txt`.
- Commits sem linhas de atribuição (nada de `Co-Authored-By` nem `Claude-Session`), com o autor do dono; `git add` só dos arquivos da tarefa (nunca `run/`, nunca `.superpowers/`). Não faça push.

## Mapa de arquivos

Base `B/` = `src/main/java/io/github/matheusanbs/wirelessautomate/`, `T/` = `src/test/java/io/github/matheusanbs/wirelessautomate/`, `R/` = `src/main/resources/`.

| Arquivo | Papel |
| --- | --- |
| `B/repairer/RepairMath.java` (novo, puro) | Custo, capacidade do buffer, progresso em milésimos |
| `T/repairer/RepairMathTest.java` (novo) | JUnit |
| `B/Config.java` (mudar) | Seção `repairer` |
| `B/repairer/RepairerSlots.java` (novo) | Índices dos 17 slots e regras de item por slot |
| `B/repairer/RepairerCosts.java` (novo) | O custo de um cristal pela config |
| `B/repairer/RepairerBlock.java`, `RepairerBlockEntity.java` (novos) | Bloco com `facing`, os slots, o buffer e o motor de linhas |
| `B/repairer/RepairerContainer.java` (novo) | `Container` sobre o block entity, para o menu |
| `B/repairer/RepairerManager.java` (novo) | Quem tem linha trabalhando (o tick do servidor) |
| `B/repairer/RepairerItems.java`, `RepairerEnergy.java`, `RepairerCapabilities.java` (novos) | As capabilities |
| `B/registry/ModBlocks.java`, `ModItems.java`, `ModBlockEntities.java`, `ModCreativeTabs.java`, `ModMenus.java` (mudar) | Registro |
| `B/WirelessAutomate.java` (mudar) | Gerenciador no tick e na parada, listener das capabilities |
| `B/menu/RepairerMenu.java`, `B/packet/RepairerStatePayload.java`, `B/packet/ModPayloads.java` (novo/novo/mudar) | Menu e estado da tela, protocolo 33 |
| `B/client/repairer/*` (novos), `B/client/ClientSetup.java` (mudar) | Tela em partes |
| `scripts/textures/reparador_modelo.py`, `gerar_texturas.py` (novo/mudar) | Modelo, textura e blockstate do bloco |
| `R/data/wirelessautomate/loot_table/blocks/crystal_repairer.json`, `R/data/minecraft/tags/block/mineable/pickaxe.json`, `R/data/wirelessautomate/recipe/crystal_repairer.json` (novo/mudar/novo) | Dados |
| `B/compat/jei/CrystalRepairerCategory.java`, `ResonatorJei.java` (novo/mudar) | JEI |
| `scripts/guide/gerar_guia.py` (mudar) | Página do guia |
| `B/gametest/RepairerGameTests.java`, `RepairerEngineGameTests.java`, `RepairerMenuGameTests.java` (novos) | GameTests |
| `B/client/dev/E2eRepairerSteps.java`, `E2eScript.java`, `E2eDarkSteps.java`, `ScreenshotRepairer.java`, `DevScreenshot.java` (novo/mudar) | e2e e capturas |
| `R/assets/wirelessautomate/lang/en_us.json`, `pt_br.json`, `CLAUDE.md`, `docs/progresso.md` (mudar) | Textos e docs |

---

### Task 1: Lógica pura (`RepairMath`) e a config

**Files:**
- Create: `B/repairer/RepairMath.java`
- Test: `T/repairer/RepairMathTest.java`
- Modify: `B/Config.java` (a seção nova, perto do fim da seção `resonator`; o getter junto dos do Ressonador)

**Interfaces:**
- Produces: `RepairMath.LINES = 8`; `RepairMath.costTier(boolean cut, int tunedTierOrdinal)` → posição do tier cujo FE por cristal fixa o custo (Sinal 0, Lapidado 1, Afinado = o tier dele; `tunedTierOrdinal < 0` = sem tier); `RepairMath.cost(long fePerCrystal, int factor)` → `long`, saturando; `RepairMath.bufferCapacity(long highestCost, int lines)` → `long`; `RepairMath.progressPermille(long now, long end, int duration)` → 0 a 1000. `Config.repairTicks()` → `long` ≥ 1; `Config.repairCostFactor()` → `int`.

- [ ] **Step 1: Escreva o teste que falha**

```java
package io.github.matheusanbs.wirelessautomate.repairer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RepairMathTest {
    @Test
    void tierDoCustoPeloTipoDoCristal() {
        assertEquals(0, RepairMath.costTier(false, -1));
        assertEquals(1, RepairMath.costTier(true, -1));
        assertEquals(2, RepairMath.costTier(true, 2));
        assertEquals(7, RepairMath.costTier(false, 7));
    }

    @Test
    void custoEhFatorVezesOFePorCristal() {
        assertEquals(432, RepairMath.cost(216, 2));
        assertEquals(13_824, RepairMath.cost(6_912, 2));
        assertEquals(536_870_912L, RepairMath.cost(268_435_456L, 2));
        assertEquals(0, RepairMath.cost(216, 0));
    }

    @Test
    void custoSaturaEmVezDeEstourar() {
        assertEquals(Long.MAX_VALUE, RepairMath.cost(Long.MAX_VALUE, 1000));
        assertEquals(0, RepairMath.cost(-5, 2));
        assertEquals(0, RepairMath.cost(216, -3));
    }

    @Test
    void bufferCobreAsOitoLinhas() {
        assertEquals(4_294_967_296L, RepairMath.bufferCapacity(536_870_912L, 8));
        assertEquals(Long.MAX_VALUE, RepairMath.bufferCapacity(Long.MAX_VALUE, 8));
    }

    @Test
    void progressoEmMilesimos() {
        assertEquals(0, RepairMath.progressPermille(100, 240, 140));
        assertEquals(500, RepairMath.progressPermille(170, 240, 140));
        assertEquals(1000, RepairMath.progressPermille(240, 240, 140));
        assertEquals(1000, RepairMath.progressPermille(500, 240, 140));
        assertEquals(0, RepairMath.progressPermille(50, 240, 140));
        assertEquals(1000, RepairMath.progressPermille(10, 20, 0));
    }
}
```

- [ ] **Step 2: Rode e veja falhar**

Run: `./gradlew test --tests "*RepairMathTest"`
Expected: FAIL (compilação: `RepairMath` não existe).

- [ ] **Step 3: Implemente `RepairMath`**

```java
package io.github.matheusanbs.wirelessautomate.repairer;

import io.github.matheusanbs.wirelessautomate.resonator.CoreMath;

/**
 * As contas do Reparador de Cristais, em {@code long} e saturando: o custo de um reparo, a capacidade do buffer e o
 * progresso de uma linha. Lógica pura, sem classes do Minecraft.
 */
public final class RepairMath {
    /** As linhas de reparo: uma por câmara do Núcleo Ultimate. */
    public static final int LINES = 8;

    private RepairMath() {
    }

    /**
     * A posição do tier cujo FE por cristal fixa o custo: o Cristal de Sinal custa pelo Básico (0), o Lapidado pelo
     * Avançado (1) e o Afinado pelo tier dele; {@code tunedTierOrdinal < 0} é o cristal sem tier.
     */
    public static int costTier(boolean cut, int tunedTierOrdinal) {
        if (tunedTierOrdinal >= 0) {
            return tunedTierOrdinal;
        }
        return cut ? 1 : 0;
    }

    /** O custo de um reparo: {@code factor} vezes o FE por cristal; fator ou FE negativo vale 0. */
    public static long cost(long fePerCrystal, int factor) {
        return CoreMath.mulSat(Math.max(0, fePerCrystal), Math.max(0, factor));
    }

    /** A capacidade do buffer: o reparo mais caro vezes as linhas, para as oito poderem começar juntas. */
    public static long bufferCapacity(long highestCost, int lines) {
        return CoreMath.mulSat(highestCost, lines);
    }

    /** Quanto da linha já andou, de 0 a 1000, pelo fim ({@code end}) e a duração total do reparo. */
    public static int progressPermille(long now, long end, int duration) {
        if (duration <= 0) {
            return 1000;
        }
        long left = end - now;
        if (left <= 0) {
            return 1000;
        }
        if (left >= duration) {
            return 0;
        }
        return (int) ((duration - left) * 1000 / duration);
    }
}
```

- [ ] **Step 4: Config**

Em `B/Config.java`, junto dos campos estáticos do Ressonador, acrescente:

```java
    public static final ModConfigSpec.LongValue REPAIR_TICKS;
    public static final ModConfigSpec.IntValue REPAIR_COST_FACTOR;
```

No bloco `static {}`, logo depois do `builder.pop()` que fecha a seção `resonator` (e antes das seções seguintes), acrescente (o nome `repairer` não colide com os nomes de tier):

```java
        builder.push("repairer");
        REPAIR_TICKS = builder
                .comment("Ticks que uma linha do Reparador de Cristais leva para refazer um cristal rachado.")
                .defineInRange("repairTicks", 140L, 1L, Long.MAX_VALUE);
        REPAIR_COST_FACTOR = builder
                .comment("Quantas vezes o FE por cristal do tipo reparado o Reparador gasta por reparo (0 repara de graça).")
                .defineInRange("costFactor", 2, 0, 1000);
        builder.pop();
```

e os getters, no mesmo estilo dos do Ressonador (`read(...)`):

```java
    public static long repairTicks() {
        return read(REPAIR_TICKS);
    }

    public static int repairCostFactor() {
        return read(REPAIR_COST_FACTOR);
    }
```

- [ ] **Step 5: Rode e veja passar**

Run: `./gradlew compileJava && ./gradlew test --tests "*RepairMathTest"`
Expected: PASS (5 testes). Confira que o arquivo de config do servidor gerado ganha a seção `repairer` (o `runGameTestServer` da Tarefa 4 já a lê).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/repairer/RepairMath.java src/test/java/io/github/matheusanbs/wirelessautomate/repairer/RepairMathTest.java src/main/java/io/github/matheusanbs/wirelessautomate/Config.java
git commit -m "Reparador: contas puras de custo e progresso, e a config repairer"
```

---

### Task 2: O bloco, o item e o registro

**Files:**
- Create: `B/repairer/RepairerBlock.java`, `B/repairer/RepairerBlockEntity.java` (esqueleto: só os slots e o NBT desta tarefa; o motor entra na Tarefa 4), `B/repairer/RepairerSlots.java`, `B/repairer/RepairerContainer.java`
- Modify: `B/registry/ModBlocks.java`, `ModItems.java`, `ModBlockEntities.java`, `ModCreativeTabs.java`; `R/data/minecraft/tags/block/mineable/pickaxe.json`; `R/assets/wirelessautomate/lang/en_us.json`, `pt_br.json` (só o nome do bloco)
- Create: `R/data/wirelessautomate/loot_table/blocks/crystal_repairer.json`
- Test: `B/gametest/RepairerGameTests.java` (cria; as outras tarefas acrescentam)

**Interfaces:**
- Produces: `ModBlocks.CRYSTAL_REPAIRER` (id `crystal_repairer`), `ModItems.CRYSTAL_REPAIRER`, `ModBlockEntities.CRYSTAL_REPAIRER`; `RepairerSlots.INPUTS = 8` (slots 0 a 7), `RepairerSlots.FEED = 8`, `RepairerSlots.OUTPUT_FIRST = 9`, `RepairerSlots.OUTPUTS = 8`, `RepairerSlots.TOTAL = 17`; `RepairerSlots.isInput(int)`, `isOutput(int)`; `RepairerSlots.acceptsCracked(ItemStack)`, `RepairerSlots.acceptsFeed(ItemStack)`; `RepairerBlockEntity.items()` (a lista de 17 pilhas), `container()` (o `RepairerContainer`), `itemsChanged()`.

- [ ] **Step 1: Leia** as seções 1 e 2 do mapa dos padrões e o `ResonanceCoreBlock`/`ResonanceCoreBlockEntity` (o análogo) antes de escrever.

- [ ] **Step 2: Escreva o GameTest que falha** (`B/gametest/RepairerGameTests.java`)

```java
package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlockEntity;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** O Reparador de Cristais: o bloco, os slots e o NBT. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RepairerGameTests {
    static final BlockPos A = new BlockPos(1, 1, 1);

    private RepairerGameTests() {
    }

    /** Põe o Reparador e espera o `onLoad`, que só roda no tick seguinte. */
    static void repairer(GameTestHelper helper, java.util.function.Consumer<RepairerBlockEntity> body) {
        helper.setBlock(A, ModBlocks.CRYSTAL_REPAIRER.get().defaultBlockState());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getBlockEntity(A) instanceof RepairerBlockEntity, "sem block entity"))
                .thenExecute(() -> body.accept((RepairerBlockEntity) helper.getBlockEntity(A)))
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void temDezesseteSlotsVazios(GameTestHelper helper) {
        repairer(helper, repairer -> {
            helper.assertValueEqual(repairer.items().size(), RepairerSlots.TOTAL, "slots");
            for (int i = 0; i < RepairerSlots.TOTAL; i++) {
                helper.assertTrue(repairer.items().get(i).isEmpty(), "o slot " + i + " começa vazio");
            }
        });
    }
}
```

- [ ] **Step 3: Rode e veja falhar**

Run: `./gradlew compileJava`
Expected: FAIL (compilação: `ModBlocks.CRYSTAL_REPAIRER` não existe).

- [ ] **Step 4: Implemente**

`RepairerSlots` (puro de Minecraft só nas regras de item; sem estado):

```java
package io.github.matheusanbs.wirelessautomate.repairer;

import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import net.minecraft.world.item.ItemStack;

/** Os 17 slots do Reparador e o que cada um aceita: 8 rachados, 1 estoque de cristais cheios, 8 saídas. */
public final class RepairerSlots {
    public static final int INPUTS = RepairMath.LINES;
    public static final int FEED = INPUTS;
    public static final int OUTPUT_FIRST = FEED + 1;
    public static final int OUTPUTS = RepairMath.LINES;
    public static final int TOTAL = OUTPUT_FIRST + OUTPUTS;

    private RepairerSlots() {
    }

    public static boolean isInput(int slot) {
        return slot >= 0 && slot < INPUTS;
    }

    public static boolean isOutput(int slot) {
        return slot >= OUTPUT_FIRST && slot < TOTAL;
    }

    /** Um cristal rachado (qualquer tipo: Sinal, Lapidado ou Afinado). */
    public static boolean acceptsCracked(ItemStack stack) {
        return stack.getItem() instanceof SignalCrystalItem && SignalCrystalItem.isCracked(stack);
    }

    /** O mesmo cristal que a {@code RecastCrystalRecipe} pede: o Cristal de Sinal comum com a carga cheia. */
    public static boolean acceptsFeed(ItemStack stack) {
        return stack.is(ModItems.SIGNAL_CRYSTAL.get()) && SignalCrystalItem.isFull(stack);
    }
}
```

`RepairerBlock`: `BaseEntityBlock` com `MapCodec` (`simpleCodec(RepairerBlock::new)`), propriedade `FACING = BlockStateProperties.HORIZONTAL_FACING`, `createBlockStateDefinition` com `FACING`, `getStateForPlacement` com `context.getHorizontalDirection().getOpposite()` (a porta de energia, o norte do modelo, fica virada para o jogador), `getRenderShape` → `RenderShape.MODEL` (o `BaseEntityBlock` devolve `INVISIBLE` por padrão), `newBlockEntity` → `new RepairerBlockEntity(pos, state)`, `rotate`/`mirror` pelo `FACING`, `useWithoutItem` → nesta tarefa `InteractionResult.PASS` (a Tarefa 5 abre o menu), e `onRemove` que, ao trocar de bloco, larga cada pilha com `Containers.dropContents(level, pos, repairer.container())` antes de `super.onRemove` (a energia se perde, como no Núcleo; veja `ResonanceCoreBlock.onRemove`).

`RepairerContainer implements Container` sobre `RepairerBlockEntity`: `getContainerSize` = `TOTAL`; `getItem(i)` devolve a pilha da lista; `setItem(i, stack)` grava e chama `itemsChanged()`; `removeItem(i, n)` e `removeItemNoUpdate(i)` pelo `ContainerHelper`/pela lista; `canPlaceItem(i, stack)` = `isInput(i) ? acceptsCracked(stack) : i == FEED ? acceptsFeed(stack) : false`; `getMaxStackSize(i)` = 1 nas entradas, 64 nas outras (`getMaxStackSize()` 64); `stillValid` = `Container.stillValidBlockEntity(be, player)`; `clearContent`; `setChanged` delega ao `itemsChanged()`.

`RepairerBlockEntity` (esqueleto desta tarefa): `items = NonNullList.withSize(TOTAL, ItemStack.EMPTY)`, `items()`, `container()`, `itemsChanged()` (por enquanto só `setChanged()`), `saveAdditional` e `loadAdditional` com a lista `items` (modelo: `CoreNbt.saveCrystals`/`loadCrystals`: `ListTag`, `entry.putByte("slot", ...)`, `stack.save(registries, entry)`, `ItemStack.parse(registries, entry).orElse(EMPTY)`; ignore slot fora de 0..16). A chave `energy` e `lines` entram na Tarefa 4.

Registro: `ModBlocks.CRYSTAL_REPAIRER = BLOCKS.registerBlock("crystal_repairer", RepairerBlock::new, <propriedades>)`, as propriedades como as da peça do Ressonador (`stone()` + `noOcclusion()` por causa do vidro translúcido; `pushReaction(BLOCK)`); `ModItems.CRYSTAL_REPAIRER = ITEMS.registerSimpleBlockItem(ModBlocks.CRYSTAL_REPAIRER)`; `ModBlockEntities.CRYSTAL_REPAIRER = BLOCK_ENTITY_TYPES.register("crystal_repairer", () -> BlockEntityType.Builder.of(RepairerBlockEntity::new, ModBlocks.CRYSTAL_REPAIRER.get()).build(null))`; aba criativa: `output.accept(ModItems.CRYSTAL_REPAIRER.get())` logo depois das peças do Ressonador (depois do `RESONATOR_TUNER`, antes do guia). Dados: loot table (copie o formato do `resonator_energy_port.json`, com `crystal_repairer`, e o `random_sequence "wirelessautomate:blocks/crystal_repairer"`) e o id `wirelessautomate:crystal_repairer` na lista do `mineable/pickaxe.json`. Lang: `"block.wirelessautomate.crystal_repairer": "Crystal Repairer"` (en) e `"Reparador de Cristais"` (pt), junto das chaves de bloco do Ressonador, na mesma posição nos dois arquivos.

- [ ] **Step 5: Rode e veja passar**

Run: `./gradlew compileJava && ./gradlew runGameTestServer`
Expected: compila e o teste novo passa; nenhum teste antigo muda de resultado.

- [ ] **Step 6: Verificações** (`varrer_perf.py`, `varrer.py`, `medir.py` nos arquivos mudados) e **Step 7: Commit**

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/repairer src/main/java/io/github/matheusanbs/wirelessautomate/registry src/main/java/io/github/matheusanbs/wirelessautomate/gametest/RepairerGameTests.java src/main/resources/data src/main/resources/assets/wirelessautomate/lang
git commit -m "Reparador: o bloco, o item, os 17 slots e o NBT"
```

---

### Task 3: As capabilities (itens e energia)

**Files:**
- Create: `B/repairer/RepairerItems.java`, `B/repairer/RepairerEnergy.java`, `B/repairer/RepairerCapabilities.java`
- Modify: `B/repairer/RepairerBlockEntity.java` (o buffer de energia e os acessores), `B/WirelessAutomate.java` (o listener, na linha das outras capabilities)
- Test: `B/gametest/RepairerGameTests.java`

**Interfaces:**
- Consumes: `RepairerSlots` (Tarefa 2), `RepairMath.bufferCapacity`, `Config.repairCostFactor`, `Config.resonatorFePerCrystal(RouterTier)`, `ScalarStore(Runnable onChange, LongSupplier capacity)`, `BulkEnergy`.
- Produces: `RepairerBlockEntity.energy()` → o `ScalarStore`; `RepairerBlockEntity.itemView()` → `RepairerItems` (um `IItemHandler` de 17 slots); `RepairerBlockEntity.energyView()` → `RepairerEnergy` (`IEnergyStorage` só de entrada) e `bulkEnergyView()` → `BulkEnergy` só de entrada; `RepairerBlockEntity.wake()` (nesta tarefa um método vazio que a Tarefa 4 preenche: as visões o chamam depois de uma entrada).

- [ ] **Step 1: Escreva os GameTests que falham** (acrescente a `RepairerGameTests`, com `Capabilities.ItemHandler.BLOCK`, `Capabilities.EnergyStorage.BLOCK` e `BulkEnergy.BLOCK` lidos pelo `helper.getLevel().getCapability(..., helper.absolutePos(A), Direction.NORTH)`, e cristais por `ResonatorTestSupport.crystal(...)` (`crystal(false, FULL)` é o Sinal cheio; pegue o rachado com `SignalCrystalItem.setIntegrity(stack, 0)` e carga 0, como o `crackedAt` do `ResonatorFatigueGameTests`)):

1. `itensEntramNosSlotsCertos`: um rachado entra na entrada 0 pelo handler (devolve vazio) e não entra num Sinal cheio, num Lapidado cheio nem num Opaco; um Sinal cheio entra no estoque (slot 8) até 64 e o 65º volta; um Lapidado cheio **não** entra no estoque; nada entra nas saídas.
2. `entradaTemLimiteDeUm`: duas pilhas rachadas no mesmo slot: a segunda volta inteira; num slot cheio `insertItem` devolve a pilha.
3. `soAsSaidasSaem`: `extractItem` nas entradas (0 a 7) e no estoque devolve vazio mesmo com cristal lá; numa saída com cristal, sai (e `simulate` não tira).
4. `energiaSoEntra`: `receiveEnergy` com buffer vazio guarda (até a capacidade) e `extractEnergy` devolve 0; o mesmo para o `BulkEnergy` (`insert` guarda, `extract` devolve 0); `canExtract()` é falso e `canReceive()` verdadeiro.
5. `capacidadeDoBufferEhOFatorVezesOUltimateVezesOito`: `repairer.energy().capacity()` = `RepairMath.bufferCapacity(2 * Config.resonatorFePerCrystal(RouterTier.ULTIMATE), 8)` com os padrões (4.294.967.296 × 2 não: use a fórmula, não o número, para o teste não quebrar com a config).

- [ ] **Step 2: Rode e veja falhar** (`./gradlew compileJava`: faltam as classes).

- [ ] **Step 3: Implemente**

`RepairerItems implements IItemHandler` (visão sobre o block entity; `getStackInSlot` devolve a pilha da lista sem copiar, como os armazenamentos do mod):
- `getSlots()` = `TOTAL`.
- `isItemValid(slot, stack)`: entradas → `acceptsCracked`; `FEED` → `acceptsFeed`; saídas → falso.
- `getSlotLimit(slot)`: 1 nas entradas, 64 no resto.
- `insertItem(slot, stack, simulate)`: `stack.isEmpty()` → devolve `stack`; slot inválido → devolve `stack`; entradas: só com o slot vazio, põe 1 (`stack.copyWithCount(1)`) e devolve `stack.copyWithCount(count - 1)` (vazio se era 1); `FEED`: soma até 64 se o slot está vazio ou tem a mesma pilha (`ItemStack.isSameItemSameComponents`), devolve o resto; não mexe em nada com `simulate`; ao gravar, chama `repairer.itemsChanged()`.
- `extractItem(slot, amount, simulate)`: só `isOutput(slot)`; `Math.min(amount, count)`; com `!simulate` diminui e chama `repairer.itemsChanged()`; nas outras, `ItemStack.EMPTY`.

`RepairerEnergy implements IEnergyStorage` (receiveEnergy → `(int) energy.insert(toReceive, simulate)` e, se guardou algo e não é `simulate`, `repairer.wake()`; `extractEnergy` → 0; `getEnergyStored`/`getMaxEnergyStored` cortados em `Integer.MAX_VALUE` como o `EnergyStoreHandler`; `canExtract` falso; `canReceive` verdadeiro) e uma classe interna ou arquivo `RepairerBulkEnergy implements BulkEnergy` (`insert` guarda e chama `wake()` se guardou; `extract` devolve 0).

No `RepairerBlockEntity`: `private final ScalarStore energy = new ScalarStore(this::markUnsaved, RepairerBlockEntity::bufferCapacity);` com

```java
    /** O reparo mais caro (o Afinado Ultimate) vezes as oito linhas, pela config. */
    private static long bufferCapacity() {
        long highest = RepairMath.cost(Config.resonatorFePerCrystal(RouterTier.ULTIMATE), Config.repairCostFactor());
        return RepairMath.bufferCapacity(highest, RepairMath.LINES);
    }
```

`markUnsaved()` = `if (level != null) level.blockEntityChanged(worldPosition);` (sem avisar vizinhos, como o do Núcleo); `wake()` vazio por enquanto, com o comentário `// a Tarefa 4 acorda o motor aqui` **não** (sem TODO no código): deixe o corpo vazio e o javadoc dizendo o que ele fará quando houver motor ("começa os reparos que já podem começar"); `itemsChanged()` passa a chamar `setChanged()` e `wake()`. Persistência: acrescente a chave `energy` (long) no `saveAdditional` e leia com `Math.max(0, tag.getLong("energy"))` no `loadAdditional` via `energy.set(...)`.

`RepairerCapabilities.register(RegisterCapabilitiesEvent event)`: `event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CRYSTAL_REPAIRER.get(), (r, side) -> r.itemView())`, o mesmo para `Capabilities.EnergyStorage.BLOCK` (`energyView()`) e `BulkEnergy.BLOCK` (`bulkEnergyView()`). No `WirelessAutomate`, `modEventBus.addListener(RepairerCapabilities::register);` ao lado do `StorageCapabilities::register`.

- [ ] **Step 4: Rode e veja passar** (`./gradlew compileJava && ./gradlew runGameTestServer`).

- [ ] **Step 5: Verificações e commit**

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/repairer src/main/java/io/github/matheusanbs/wirelessautomate/WirelessAutomate.java src/main/java/io/github/matheusanbs/wirelessautomate/gametest/RepairerGameTests.java
git commit -m "Reparador: as capabilities de itens e de energia, só de entrada"
```

---

### Task 4: O motor de linhas e o gerenciador

**Files:**
- Create: `B/repairer/RepairerCosts.java`, `B/repairer/RepairerManager.java`
- Modify: `B/repairer/RepairerBlockEntity.java` (o motor), `B/WirelessAutomate.java` (o tick e a parada)
- Test: `B/gametest/RepairerEngineGameTests.java` (novo)

**Interfaces:**
- Consumes: tudo das Tarefas 1 a 3; `ModDataComponents.CRYSTAL_CHARGE` e `ModDataComponents.CRYSTAL_INTEGRITY` (o `RecastCrystalRecipe.assemble` os remove do rachado: o cristal reparado nasce cheio); `Config.recastCrystals()`, `Config.repairTicks()`.
- Produces: `RepairerCosts.of(ItemStack crystal)` → `long`; `RepairerBlockEntity.tick(long now)` (público, para os GameTests avançarem o relógio), `wake()`, `lineEnd(int line)` → `long` (0 parada), `lineBlocked(int line)`, `running(int line)` (fim > 0 e não travada), `anyRunning()`; `RepairerManager.add/remove/tick(MinecraftServer)/reset()/activeCount()`.

**Custo:** parado custa zero (o gerenciador só guarda Reparadores com linha trabalhando); com trabalho, uma passada por Reparador ativo por tick (8 comparações de `long` e, no fim de um reparo, uma cópia de pilha); `setChanged` e aviso aos vizinhos só ao começar, terminar e destravar.

- [ ] **Step 1: Leia** a seção 3 do mapa (`SourceTankLevels`, `ResonatorManager`) e `CorePresence.notifyPorts` (o aviso aos vizinhos: `level.updateNeighbourForOutputSignal` e `updateNeighborsAt`).

- [ ] **Step 2: Escreva os GameTests que falham** (`RepairerEngineGameTests`, no lote padrão; reaproveite o `repairer(helper, body)` de `RepairerGameTests`, tornando-o `static` de pacote; avance o relógio com `repairer.tick(helper.getLevel().getGameTime() + Config.repairTicks())`):

1. `reparaUmCristalRachado`: põe 1 rachado (Lapidado, para o custo ser 13.824), 4 Sinais cheios no estoque e `13_824 * ...` de energia pelo `BulkEnergy` (use `RepairerCosts.of(rachado)` para o valor, não o número); `repairer.wake()`; a linha 0 está rodando; `tick(agora + repairTicks)`; a entrada 0 ficou vazia, a primeira saída tem o Lapidado com `SignalCrystalItem.isFull` e **sem** o componente de integridade (cheio: `SignalCrystalItem.integrity(stack) == Config.crystalIntegrity()`), o estoque ficou vazio (gastou 4), o buffer gastou exatamente o custo.
2. `semEnergiaNaoComeca`: com tudo menos energia, `running(0)` é falso; ao entrar energia suficiente, `running(0)` vira verdadeiro (o `receiveEnergy` acorda).
3. `semCristaisCheiosNaoComeca`: com 3 Sinais, não começa; ao entrar o quarto, começa.
4. `oitoLinhasEmParalelo`: 8 rachados, 32 Sinais (o estoque guarda 64: 2 pilhas não cabem; use 4 por reparo e dois lotes: coloque 32 e confirme que as 8 linhas rodam juntas com `anyRunning()` e, depois do `tick` no fim, as 8 saídas têm cristal) e energia para as 8.
5. `saidaCheiaTravaEDestravaAoEsvaziar`: encha as 8 saídas com cristais cheios (pelo `items()` direto), comece um reparo e avance: a linha fica travada (`lineBlocked(0)` verdadeiro, a entrada ainda tem o cristal e `RepairerManager.activeCount()` volta a 0); esvaziar uma saída pelo handler (`extractItem`) destrava e conclui na hora (a saída tem o cristal, a entrada vazia).
6. `paradoNaoEstaNoGerenciador`: um Reparador novo e vazio não está no gerenciador (`RepairerManager.activeCount()` não o conta: compare `activeCount()` antes e depois de pôr o bloco, o lote dos testes é paralelo, então teste `contains` com um método `RepairerManager.contains(RepairerBlockEntity)`); com uma linha trabalhando ele entra; ao terminar, sai.
7. `nbtGuardaALinhaEmAndamento`: com uma linha trabalhando e energia, `saveWithoutMetadata(registries)` e um segundo block entity carregado (`loadAdditional`) restauram os 17 slots, a energia e a linha (o `lines[0]` é > 0 e o fim reconstruído é `agora + faltando`); uma linha travada volta travada (`-1`).
8. `custoPorTipo`: `RepairerCosts.of` do Cristal de Sinal rachado = `2 * Config.resonatorFePerCrystal(RouterTier.BASIC)` (432 nos padrões), do Lapidado = `2 * ...(ADVANCED)` (13.824), de um Afinado Elite = `2 * ...(ELITE)` (20.000) (procure como o `ResonatorTunedGameTests` pega o Afinado de um tier); e `RepairMath.costTier` coerente.

- [ ] **Step 3: Rode e veja falhar** (`./gradlew compileJava`).

- [ ] **Step 4: Implemente**

`RepairerCosts`:

```java
package io.github.matheusanbs.wirelessautomate.repairer;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import net.minecraft.world.item.ItemStack;

/** O custo em FE de reparar um cristal, pela config: o fator vezes o FE por cristal do tier do tipo dele. */
public final class RepairerCosts {
    /** Os tiers por posição, sem copiar o array do enum a cada consulta. */
    private static final RouterTier[] TIERS = RouterTier.values();

    private RepairerCosts() {
    }

    /** O custo do reparo de {@code crystal}; 0 para o que não é cristal (nunca chega aqui: o slot não o aceita). */
    public static long of(ItemStack crystal) {
        if (!(crystal.getItem() instanceof SignalCrystalItem item)) {
            return 0;
        }
        RouterTier tuned = item.tunedTier();
        int tier = RepairMath.costTier(item.isCut(), tuned == null ? -1 : tuned.ordinal());
        return RepairMath.cost(Config.resonatorFePerCrystal(TIERS[Math.min(tier, TIERS.length - 1)]),
                Config.repairCostFactor());
    }
}
```

`RepairerManager` (o padrão do `SourceTankLevels`; confira o arquivo e copie o estilo):

```java
package io.github.matheusanbs.wirelessautomate.repairer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Os Reparadores com pelo menos uma linha trabalhando: só eles andam a cada tick do servidor. Sem nenhum, o tick é uma
 * checagem de conjunto vazio. O Reparador entra ao começar uma linha (e ao carregar com uma em andamento) e sai
 * quando nenhuma linha trabalha; um reparo que terminou sem saída livre fica travado fora daqui até abrir espaço.
 */
public final class RepairerManager {
    private static final Set<RepairerBlockEntity> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Rascunho do tick: o Reparador pode sair do conjunto enquanto anda. */
    private static final List<RepairerBlockEntity> SCRATCH = new ArrayList<>();

    private RepairerManager() {
    }

    public static void add(RepairerBlockEntity repairer) {
        ACTIVE.add(repairer);
    }

    public static void remove(RepairerBlockEntity repairer) {
        ACTIVE.remove(repairer);
    }

    public static boolean contains(RepairerBlockEntity repairer) {
        return ACTIVE.contains(repairer);
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        SCRATCH.clear();
        SCRATCH.addAll(ACTIVE);
        for (RepairerBlockEntity repairer : SCRATCH) {
            if (repairer.isRemoved() || !(repairer.getLevel() instanceof ServerLevel level)
                    || !level.shouldTickBlocksAt(ChunkPos.asLong(repairer.getBlockPos()))) {
                continue;
            }
            repairer.tick(now);
        }
        SCRATCH.clear();
    }

    public static void reset() {
        ACTIVE.clear();
        SCRATCH.clear();
    }
}
```

(Se o `ResonatorManager` usa o `getGameTime` do nível do bloco e não o do overworld, siga o dele; confira. Se `shouldTickBlocksAt(long)` tiver outra assinatura no 1.21.1, use a mesma chamada do `ResonatorManager.runDue`.)

`RepairerBlockEntity` (o motor; mantenha o arquivo abaixo de ~400 linhas, extraindo um `RepairerNbt` de pacote se passar):

- Estado: `private final long[] end = new long[LINES]; private final boolean[] blocked = new boolean[LINES]; private int[] loadedLines = null;`
- `lineEnd(i)` = `end[i]`; `lineBlocked(i)` = `blocked[i]`; `running(i)` = `end[i] > 0 && !blocked[i]`; `anyRunning()` laço de 8.
- `wake()`: `if (level == null || level.isClientSide) return; tick(level.getGameTime());` (o `tick` faz tudo: termina o que venceu, retenta o travado, começa o que já pode, e entra ou sai do gerenciador).
- `tick(long now)`:

```java
    /**
     * Termina as linhas que venceram (ou tenta de novo as travadas), começa as que já podem e entra ou sai do gerenciador.
     * Chamado pelo {@link RepairerManager} a cada tick enquanto há linha trabalhando e pelos eventos (cristal ou energia
     * que chega, saída que esvazia) pelo {@link #wake()}.
     */
    public void tick(long now) {
        boolean changed = false;
        for (int i = 0; i < RepairMath.LINES; i++) {
            if (end[i] > 0 && now >= end[i]) {
                changed |= finish(i);
            }
        }
        changed |= startLines(now);
        if (changed) {
            setChanged();
            notifyNeighbors();
        }
        if (anyRunning()) {
            RepairerManager.add(this);
        } else {
            RepairerManager.remove(this);
        }
    }
```

- `finish(i)`: acha a primeira saída livre (`OUTPUT_FIRST..TOTAL-1` com pilha vazia); sem ela, `blocked[i] = true` e devolve `false`; com ela: `ItemStack fixed = items.get(i).copyWithCount(1); fixed.remove(ModDataComponents.CRYSTAL_CHARGE.get()); fixed.remove(ModDataComponents.CRYSTAL_INTEGRITY.get());` grava `fixed` na saída, esvazia a entrada `i`, `end[i] = 0; blocked[i] = false`, devolve `true`.
- `startLines(now)`: para cada `i` com `end[i] == 0`, `RepairerSlots.acceptsCracked(items.get(i))`, e o estoque com `>= Config.recastCrystals()` e `energy.stored() >= RepairerCosts.of(items.get(i))`: gasta `recastCrystals()` do estoque (`shrink`, e esvazia se zerar), `energy.extract(custo, false)`, `end[i] = now + Config.repairTicks()`, devolve `true` se começou alguma. **Atenção:** o estoque e a energia mudam dentro do laço (a linha 0 pode consumir o que a linha 1 precisaria): releia o estado a cada `i`.
- `itemsChanged()` (sem argumento): `setChanged(); wake(); notifyNeighbors();`. Os vizinhos precisam ser avisados quando uma saída ganha ou perde cristal e quando uma entrada esvazia ou enche, porque o roteador que tira da saída e o que põe na entrada dormem por "vazio" e por "cheio"; o custo é por inserção ou extração de itens, nunca por tick. Dentro do `tick`, `notifyNeighbors()` roda só quando `changed` (começo, fim ou destravamento de uma linha).
- `notifyNeighbors()`: `if (level != null && !level.isClientSide) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());`.
- NBT (acrescente ao da Tarefa 3): no `saveAdditional`, `int[] left = new int[LINES]` com `end[i] <= 0 ? 0 : blocked[i] ? -1 : (int) Math.max(1, Math.min(Integer.MAX_VALUE, end[i] - agora))` e `tag.putIntArray("lines", left)`; no `loadAdditional`, `loadedLines = tag.getIntArray("lines")` (se o tamanho for 8, senão `null`).
- `onLoad()`: `super.onLoad(); if (level != null && !level.isClientSide) { long now = level.getGameTime(); if (loadedLines != null) { for i: v = loadedLines[i]; end[i] = v == 0 ? 0 : v < 0 ? now : now + v; blocked[i] = v < 0; } loadedLines = null; } wake();`.
- `setRemoved()` e `onChunkUnloaded()`: `RepairerManager.remove(this)` (chame o `super` depois), como o `StorageSourceTankBlockEntity`.

No `WirelessAutomate.onServerTick`, acrescente `RepairerManager.tick(event.getServer());` logo depois do `ResonatorManager.tick(...)` e antes do `StorageNeighbors.tick()` (que junta os avisos do tick); no `onServerStopped`, `RepairerManager.reset();` ao lado do `SourceTankLevels.reset()`.

- [ ] **Step 5: Rode e veja passar** (`./gradlew compileJava && ./gradlew runGameTestServer`). Os 8 testes novos e os das tarefas anteriores passam; nenhum teste antigo muda.

- [ ] **Step 6: Verificações** (`varrer_perf.py`, `varrer.py`, `medir.py`) e **Step 7: Commit**

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/repairer src/main/java/io/github/matheusanbs/wirelessautomate/WirelessAutomate.java src/main/java/io/github/matheusanbs/wirelessautomate/gametest/RepairerEngineGameTests.java src/main/java/io/github/matheusanbs/wirelessautomate/gametest/RepairerGameTests.java
git commit -m "Reparador: o motor de 8 linhas em paralelo e o gerenciador que só anda com trabalho"
```

---

### Task 5: O menu, o estado da tela e a tela

**Files:**
- Create: `B/menu/RepairerMenu.java`, `B/packet/RepairerStatePayload.java`, `B/client/repairer/RepairerScreen.java` e as partes (`RepairerLayout`, `RepairerHeader`, `RepairerChamberPanel`, `RepairerSlotsPaint`, `RepairerText`), `B/gametest/RepairerMenuGameTests.java`
- Modify: `B/registry/ModMenus.java`, `B/packet/ModPayloads.java` (`VERSION` → `"33"` e o registro do payload), `B/client/ClientSetup.java` (a tela), `B/repairer/RepairerBlock.java` (abre o menu em `useWithoutItem`), `B/client/dev/E2eDarkSteps.java` (o ramo do botão de tema da tela nova), lang
- Modify (e2e): `B/client/dev/E2eRepairerSteps.java` (novo), `E2eScript.java` (`WA_E2E_ONLY=reparador`), `ScreenshotRepairer.java` (novo) e `DevScreenshot.java`

**Interfaces:**
- Consumes: tudo das Tarefas 1 a 4; `RepairMath.progressPermille`.
- Produces: `RepairerMenu` (`ModMenus.CRYSTAL_REPAIRER`), com 17 slots do Reparador (índices 0 a 16, na ordem de `RepairerSlots`) seguidos do inventário do jogador (27 + 9); `RepairerMenu.open(ServerPlayer, RepairerBlockEntity)`; `RepairerStatePayload(int containerId, long energy, long capacity, long[] ends, boolean[] blocked, int duration, int needed, long cost0)` com codec escrito à mão (`StreamCodec.of`, o `composite` só vai até 6 campos) e `equals`/`hashCode` por `Arrays`; no cliente, `RepairerMenu.state()` → o último payload; `RepairerMenu.progress(int line, long gameTime)` → 0 a 1000.

- [ ] **Step 1: Leia** a seção 5 do mapa e as classes `ResonatorMenu`, `ResonatorScreen`, `CrystalSlots`, `CoreScreenLayout`, `StorageScalarMenu.broadcastChanges`, `ResonatorStatePayload`, `ModPayloads.register`, `ThemeToggle`, `GuiText`, `GuiPaint`. Abra o mockup `docs/preview/reparador-de-cristais.html` no navegador e use-o como o desenho da tela.

- [ ] **Step 2: Escreva os GameTests que falham** (`RepairerMenuGameTests`: `survivor`/`TestPlayers.inLevel` perto do bloco; `new RepairerMenu(1, player.getInventory(), repairer)`):

1. `temOsSlotsNaOrdem`: 17 slots do Reparador + 36 do jogador = 53; o slot 0 a 7 aceitam só rachado (1 por slot); o 8 só Sinal cheio (64); os 9 a 16 não aceitam nada do jogador (`mayPlace` falso) mas o jogador tira.
2. `rachadoEmReparoNaoSai`: com a linha 0 trabalhando, o slot 0 não pode ser retirado (`mayPickup` falso); parada, pode.
3. `estadoSoQuandoMuda`: `pollState(0)` devolve um payload na primeira vez; de novo (dentro de 5 ticks, e depois com o mesmo estado) devolve `null`; muda quando a energia muda ou uma linha começa.
4. `shiftClickMoveParaOsSlotsCertos`: Shift + clique num rachado do inventário vai para a primeira entrada livre; num Sinal cheio, para o estoque; num item qualquer, não move; Shift + clique numa saída vai para o inventário.
5. `stillValidSoPertoEComBlocoNoLugar`.
6. `clienteProgressoPeloRelogio`: `progress(line, gameTime)` = `RepairMath.progressPermille(gameTime, end, duration)` depois de `apply(payload)`.

- [ ] **Step 3: Rode e veja falhar** (`./gradlew compileJava`).

- [ ] **Step 4: Implemente** o menu (modelo: `ResonatorMenu`: três construtores servidor/cliente por buffer/placeholder; `open` com `SimpleMenuProvider` e `buf` com a posição; `pollState(long now)` com o intervalo de 5 ticks e a igualdade; `broadcastChanges` mandando só com `connection.hasChannel`; `onState` no cliente; `stillValid` pelo `RepairerContainer`; `quickMoveStack` pelas rotas do teste 4). Slots: `RepairerSlot` para as 8 entradas (`mayPlace` = `acceptsCracked`, `getMaxStackSize` 1, `mayPickup` = `!repairer.running(line) && !repairer.lineBlocked(line)`), um para o estoque (`mayPlace` = `acceptsFeed`, 64) e `OutputSlot` para as saídas (`mayPlace` falso). Posições dos slots em `RepairerLayout` (constantes), copiadas do mockup: rachados em `(208 + col * 34, 38 + row * 20)` (4 colunas, 2 linhas), estoque em `(208, 94)`, saídas em `(208 + col * 34, 130 + row * 20)`, inventário em `(95 + col * 18, 190 + row * 18)` e a hotbar em `(95 + col * 18, 250)` (o painel tem 352 × 272). O payload leva `ends` (0 parada, -1 travada... use `end` absoluto e `blocked` separado), `duration` = `Config.repairTicks()` limitado a `int`, `needed` = `Config.recastCrystals()` e `cost0` = o custo do cristal da primeira linha com rachado (para a linha de estado "falta energia" mostrar quanto). **Protocolo:** `ModPayloads.VERSION = "33"`; registre `playToClient(RepairerStatePayload.TYPE, RepairerStatePayload.STREAM_CODEC, RepairerMenu::onState)`.

A tela, em partes (cada classe ≤ 500 linhas; a anfitriã só junta e repassa): `RepairerScreen` (`AbstractContainerScreen<RepairerMenu>`, 352 × 272 fixa, `GuiPaint.panel`, `GuiText.beginFrame()`, tooltips, botão de tema `ThemeToggle` no canto direito do cabeçalho com `themeX()`/`themeY()` como a tela do Núcleo), `RepairerLayout` (as medidas), `RepairerHeader` (título "Reparador de Cristais" e o medidor de energia `energia / capacidade FE` com a barra), `RepairerChamberPanel` (o painel de 192 × 132 em `(8, 26)`: fundo escuro em gradiente, os 8 pedestais em anel e, sobre cada um com cristal, o **ícone do item** do cristal (`GuiGraphics.renderItem`) e a barra de progresso da linha; sem rachado, só o pedestal apagado; sem energia, o painel escurece com o aviso "Sem energia"; **estático**, sem animação: a câmara animada é da etapa 3), `RepairerSlotsPaint` (o fundo dos 17 slots com os rótulos RACHADOS, CRISTAIS CHEIOS ("4 por reparo" com `needed`) e REPARADOS, e a barra de progresso sobre o slot da entrada que trabalha), `RepairerText` (a linha de estado em `(8, 164)` 336 × 14, com as cores `GuiPaint.OK`/`WARN`/`DANGER`/`MUTED`: "Reparando N de 8", "Sem energia: as linhas esperam", "Faltam cristais cheios (4 por reparo)", "Saída cheia: esvazie as saídas", "Parado · sem cristais rachados"; e as dicas dos slots). Todo texto variável passa por `GuiText`. Lang: as chaves `gui.wirelessautomate.repairer.*` e `container.wirelessautomate.repairer` nos dois idiomas, na mesma posição. Registre a tela no `ClientSetup.registerScreens`. Faça o `RepairerBlock.useWithoutItem` abrir o menu no servidor (`player instanceof ServerPlayer sp` → `RepairerMenu.open(sp, repairer)`; no cliente `SUCCESS`).

E2e e capturas: `E2eRepairerSteps` (um ramo `WA_E2E_ONLY=reparador` no `E2eScript`: abre o mundo, põe o bloco, enche com cristais (Sinal rachado, Lapidado rachado, Afinado rachado), 8 Sinais cheios e energia pelo servidor, abre a tela e captura `reparador-tela`, `reparador-tela-escuro`, `reparador-tela-pt` (três estados: parado, reparando e sem energia), com `clipCheck`); `ScreenshotRepairer` para as capturas só de tela, e o ramo de `E2eDarkSteps` para o botão de tema.

- [ ] **Step 5: Rode e veja passar** (`./gradlew compileJava && ./gradlew runGameTestServer`; depois `WA_E2E="$PWD/run/e2e" WA_E2E_ONLY=reparador ./gradlew runClient`; `run/e2e/result.txt` precisa dizer `OK`, sem `FAIL`; olhe as capturas com a ferramenta de imagens e descreva o que viu: texto cortado, slot fora do lugar, tema errado).

- [ ] **Step 6: Verificações e commit**

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate src/main/resources/assets/wirelessautomate/lang
git commit -m "Reparador: o menu, o estado da tela (protocolo 33) e a tela nos dois temas"
```

---

### Task 6: O modelo 3D, a textura e o blockstate

> **Mudança de plano (26/10/2026, vale sobre o texto abaixo):** o modelo vem de um atlas PINTADO no Blockbench, com luz e sombra: `docs/preview/reparador-de-cristais.bbmodel` (104 elementos, textura `reparador_alma` de 256 × 256 embutida). O `scripts/textures/reparador_modelo.py` o converte em `models/block/repairer/crystal_repairer.json` e `_glass.json`, `textures/block/repairer/repairer.png`, o blockstate `multipart` por `facing` e o item composto `neoforge:composite`. Não existe mais a paleta 16 × 16 nem o `palette.png`; onde o texto abaixo cita `repairer/palette`, leia `repairer/repairer`.

**Files:**
- Create: `scripts/textures/reparador_modelo.py`
- Modify: `scripts/textures/gerar_texturas.py` (chama o script novo e mantém o que ele gera no conjunto de arquivos que `limpar` preserva)
- Generate (pelo script, nunca à mão): `R/assets/wirelessautomate/models/block/repairer/crystal_repairer.json`, `.../repairer/crystal_repairer_glass.json`, `R/assets/wirelessautomate/blockstates/crystal_repairer.json`, `R/assets/wirelessautomate/models/item/crystal_repairer.json`, `R/assets/wirelessautomate/textures/block/repairer/palette.png`

**Interfaces:**
- Consumes: `docs/preview/reparador-de-cristais.bbmodel` (JSON do Blockbench: `elements`, cada um com `from`, `to`, `origin`, `rotation`, `faces` com `uv` e a textura de paleta 128 × 128).
- Produces: o bloco `crystal_repairer` com modelo, blockstate (`facing` horizontal) e item.

**Contexto crítico (do mapa, seção "Surpresas"):** o `blocos.py` apaga os `models/block/*.json` da raiz que ele não gerou, e `gerar_texturas.limpar` apaga PNG de `block/`, `item/`, `gui/` que não foram gerados; o `Modelo` do `blocos.py` não aceita elementos que se atravessam. Por isso: o modelo vai numa **subpasta** (`models/block/repairer/`), o PNG da paleta numa subpasta de texturas (`textures/block/repairer/`) registrada no conjunto de arquivos que o gerador mantém, e o script novo não usa a classe `Modelo`.

- [ ] **Step 1: Leia** `scripts/textures/gerar_texturas.py` (`main()`, `limpar`, `grava_json`/`GRAVA`), `scripts/textures/blocos.py` (`modelos()` e como a pasta `models/block` é limpa; `blockstates_ressonador`, `ROTACOES_PECA`) e o `.bbmodel` (é JSON: `python -c "import json; d=json.load(open(...)); print(d.keys(), d['elements'][0])"`). Confirme que um modelo em subpasta de `models/block/` sobrevive ao `blocos.modelos()` e que uma textura em `textures/block/repairer/` sobrevive ao `limpar` (o glob do `limpar`: se ele for recursivo, registre o caminho no conjunto que ele recebe).

- [ ] **Step 2: Escreva o script** `reparador_modelo.py` com uma função `gerar(raiz_assets)` que:
  1. lê o `.bbmodel`; a paleta do projeto é 14 materiais × 6 tons em blocos de 8 px de uma textura de 128 × 128 (coluna = material, linha = tom: `up, north, south, east, west, down`); reconstrói a **paleta 16 × 16** (1 px por material e tom: `x = material`, `y = tom`) com as cores do script de origem (as 14 cores base e os fatores `up 1.22, north 1.0, south 0.9, east 0.84, west 1.06, down 0.62`; `glow`, `led` e `glass` sem sombreamento e o vidro com alfa 51 de 255; as cores estão na lista `MATS` do trabalho de 26/10: slab #3f3b4c, step #4d495a, bronze #a67c38, steel #6b6779, post #5c5869, mull #76728a, cap #58546a, floor #24212c, ped #7d788f, dark #1d1b24, coral #e8573a, glow #ff9a66, led #ffc27a, glass #a9cbe8) e grava `textures/block/repairer/palette.png`;
  2. converte cada elemento: `from`/`to` (os valores do `.bbmodel` já estão em unidades do bloco), `rotation` (o elemento do JSON de bloco só aceita rotação num eixo, com `origin` e `angle`: os cubos do modelo têm rotação em um eixo só, 45° em Y ou em Z; converta para `{"origin": [...], "axis": "y", "angle": 45}`), e para cada face o `uv` em 16 × 16: o uv da face no `.bbmodel` está em pixels de 128, divida por 8 para achar o texel `(x, y)` da paleta e use um retângulo de 0,5 px no centro do texel (`[x + 0.25, y + 0.25, x + 0.75, y + 0.75]`) para o filtro não sangrar;
  3. separa os elementos do vidro (o cubo `glass`) num modelo à parte, translúcido; todo o resto vai no modelo sólido (`"render_type": "minecraft:cutout"` por causa do alfa do que for fino; confira se `solid` basta: nenhum texel opaco tem alfa);
  4. grava os dois modelos com `"parent": "minecraft:block/block"`, `"textures": {"palette": "wirelessautomate:block/repairer/palette", "particle": "wirelessautomate:block/repairer/palette"}`, `"ambientocclusion": false` e `"display"` herdado do pai;
  5. grava o blockstate com `multipart` (o único do mod; vanilla aceita): para cada `facing` em `north, east, south, west`, dois `apply` (`crystal_repairer` e `crystal_repairer_glass`) com `y` = 0, 90, 180, 270 (`ROTACOES_PECA` do `blocos.py` tem os mesmos valores para `north/east/south/west`), usando `when: {"facing": "..."}`;
  6. grava o modelo de item `{"parent": "wirelessautomate:block/repairer/crystal_repairer"}` (e confira se o vidro precisa aparecer no item: se sim, um `parent` com os dois, o que não existe em JSON; então o item usa o modelo sólido e o vidro some no inventário: **diga isso no relatório** e proponha a solução (um modelo de item que repete os elementos do vidro dentro do mesmo arquivo)).
- [ ] **Step 3: Validação do próprio script** (dentro de `gerar`, falhando em vez de gravar errado): todo elemento tem `from < to` em cada eixo e coordenadas entre -16 e 32; toda face tem `texture` e `uv` dentro de 0..16; o número de elementos do modelo sólido + vidro = o do `.bbmodel` (105); nenhum elemento sem pelo menos uma face; rotação só em um eixo.
- [ ] **Step 4: Ligue ao `gerar_texturas.py`** (uma chamada em `main()` e o caminho gerado no conjunto que o `limpar` preserva) e rode `python scripts/textures/gerar_texturas.py`; `git status` deve mostrar só os arquivos do bloco novo além do que a mudança pede: reverta com `git checkout --` qualquer PNG reescrito sem relação (a máquina reescreve dezenas deles).
- [ ] **Step 5: Hitbox:** o bloco usa o cubo cheio (`Shapes.block()`), sem `getShape` próprio: confirme no `RepairerBlock` e no jogo.
- [ ] **Step 6: Verifique no jogo:** `WA_E2E_ONLY=reparador` (da Tarefa 5) com uma captura nova `reparador-bloco` do bloco no mundo, de frente e de lado, nos quatro `facing`; descreva o que viu e compare com `docs/preview/reparador-de-cristais.bbmodel` (cores, vidro, a porta de energia virada para o jogador). Sem textura roxa e preta, sem faces pretas (UV fora da textura), sem z-fighting.
- [ ] **Step 7: Commit**

```bash
git add scripts/textures src/main/resources/assets/wirelessautomate/models src/main/resources/assets/wirelessautomate/blockstates src/main/resources/assets/wirelessautomate/textures/block/repairer src/main/java/io/github/matheusanbs/wirelessautomate/client/dev
git commit -m "Reparador: o modelo 3D, a paleta e o blockstate gerados do bbmodel"
```

---

### Task 7: Receita, JEI, guia e fechamento

**Files:**
- Create: `R/data/wirelessautomate/recipe/crystal_repairer.json`, `B/compat/jei/CrystalRepairerCategory.java`
- Modify: `B/compat/jei/ResonatorJei.java` (ou `WirelessAutomateJeiPlugin.java`, onde as categorias do Ressonador são registradas), `scripts/guide/gerar_guia.py`, `B/client/dev/E2eGuideSteps.java` (`GUIDE_PAGES`), lang, `CLAUDE.md`, `docs/progresso.md`

- [ ] **Step 1: Receita** (a proposta da decisão 1; formato do `resonator_energy_port.json` e do `storage_battery.json`):

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "pattern": ["KCK", "HLH", "KRK"],
  "key": {
    "K": {"item": "wirelessautomate:resonator_casing"},
    "C": {"item": "wirelessautomate:phase_circuit"},
    "H": {"item": "minecraft:hopper"},
    "L": {"item": "wirelessautomate:harmonic_lens"},
    "R": {"item": "minecraft:redstone_block"}
  },
  "result": {"id": "wirelessautomate:crystal_repairer", "count": 1}
}
```

Confira os ids reais dos ingredientes em `R/data/wirelessautomate/recipe/`. Se o dono trocar a receita antes, use a dele.

- [ ] **Step 2: JEI.** `CrystalRepairerCategory` (modelo: `ResonatorRecastCategory`): tipo `RecipeType.create(WirelessAutomate.MODID, "crystal_repairer", Reparo.class)`, `record Reparo(ItemStack cracked, ItemStack feed, ItemStack repaired, long cost)` com um exemplo por tipo de cristal (Sinal, Lapidado, cada Afinado carregado em `RouterTier.loaded()`; `UnloadedTiers` esconde os dos tiers que não carregaram), slots de entrada (rachado e `Config.recastCrystals()` Sinais cheios) e saída (o cristal cheio), o custo em FE com `JeiText` e o tempo `repairTicks ÷ 20` em segundos; catalisador = o item `ModItems.CRYSTAL_REPAIRER`; registro nos três pontos como o Recast (`registerCategories`, `registerRecipes`, `registerCatalysts`); lang `jei.wirelessautomate.crystal_repairer.title` e `.note` nos dois idiomas.
- [ ] **Step 3: Guia.** No `gerar_guia.py`, página `crystal-repairer.md` nos dois idiomas, **só como operar** (nada de config nem ms): para que serve; como pôr energia (cabo, roteador, Bateria) e cristais (roteador, funil ou a tela); os 8 reparos ao mesmo tempo; o custo em cristais (4 de Sinal cheios) e em FE (por tipo, com a tabela dos padrões do spec: Sinal 432, Lapidado 13.824, Afinado Elite 20.000 até Ultimate 536.870.912); a saída; a receita (`<RecipeFor id="wirelessautomate:crystal_repairer" />`). Posição 20 no menu (renumere `troubleshooting`, `recipe-editor` e `recipes` para 21, 22 e 23), item na tabela da página `index` e na lista `RECIPES`; acrescente a página em `GUIDE_PAGES` do `E2eGuideSteps`. Rode `python scripts/guide/gerar_guia.py` e confira com `git status` que só os `.md` do guia mudaram (nos dois idiomas) além do script.
- [ ] **Step 4: Lang e docs.** Todas as chaves novas nos dois `lang` na mesma ordem; `CLAUDE.md`: uma linha no "Mapa do código" para `repairer/` (e `client/repairer/`, `menu/RepairerMenu`, `packet/RepairerStatePayload`, `compat/jei/CrystalRepairerCategory`, `scripts/textures/reparador_modelo.py`), os GameTests novos (com a contagem real) e `ModPayloads.VERSION` 33 onde ela aparece; `docs/progresso.md`: a tabela de estado, o próximo passo ("etapa 3: a animação do Reparador, pela skill `wa-shaders`, nativa; spec de 26/10/2026") e uma linha no histórico com os números reais.
- [ ] **Step 5: Verificação completa**

Run: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams` e `WA_E2E="$PWD/run/e2e" ./scripts/e2e.sh` (o e2e completo, porque mexeu em tela e em payload; leva alguns minutos; precisa dizer `OK` e sem `FAIL`, com as capturas do guia e do Reparador).
Expected: tudo verde; informe a contagem real de cada run.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources src/main/java scripts/guide CLAUDE.md docs/progresso.md
git commit -m "Reparador: receita, JEI, guia e fechamento da etapa 2"
```

---

## Autorrevisão (feita ao escrever)

- **Cobertura da spec:** bloco sem tier e sem vínculo (T2); 8 + 1 + 8 slots (T2, T3); reparo paralelo com cronômetro por linha, 4 cristais cheios, custo em FE por tipo, saída (T4); energia por capability, só de entrada, sem vínculo com o Ressonador (T3); automação por qualquer lado (T3); custo zero parado (T4, gerenciador); config `repairTicks` e `costFactor` (T1); tela nos dois temas (T5); receita, JEI, guia (T7); modelo 3D aprovado (T6). **Fora desta etapa por decisão:** a animação, o shader e o `ChamberView` do Reparador (etapa 3).
- **Pontos que o plano deixa para a leitura do código, com a regra a seguir:** a assinatura exata de `shouldTickBlocksAt`, o nome do campo do Afinado em `ModItems` e se o glob do `limpar` é recursivo; cada um tem o arquivo de onde copiar.
- **Riscos assumidos:** o `multipart` do blockstate (o primeiro do mod) e o vidro no item; os dois têm passo de verificação no jogo na Tarefa 6.
