# Ressonador de Sinal, etapa 3: tela, Sintonizador e câmara · plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** o Ressonador de Sinal ganha a tela do Núcleo (osciloscópio, ponteiro, lâmpada, dial, modo, slots dos cristais e da redstone, eficiência, geração, energia e o bloco Estrutura), o modo automático do Sintonizador (o dial segue a estação sozinho, limitado ao teto do tier) e a câmara animada (um `BlockEntityRenderer` do Núcleo montado: figura de Lissajous, placa de Chladni, cristais em anel, feixes, lente, clarão e partículas). Sem comparador e sem controle por redstone.

**Arquitetura:** a lógica pura (passo do Sintonizador, teto, faixa da eficiência, desvio quantizado, reconhecimento da montagem, curva de Lissajous, escala do ponteiro) fica em classes puras com JUnit. O Núcleo passa a ter um modo (`auto`), a estrutura conhecida ao montar (Sintonizador, Portas, montagem) e um update tag enxuto (`ChamberView`) mandado só quando muda. A tela segue a `wa-modular`: um menu (`ResonatorMenu`, slots pelo próprio menu e o estado por um payload a cada 5 ticks, só se mudou), uma anfitriã (`ResonatorScreen`) e partes por retângulo. A câmara segue a mesma regra: um renderer anfitrião (`ResonatorRenderer`), o estado por Núcleo numa `ChamberScene` e uma parte por efeito. Dial e modo vão por payload validado no servidor.

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), ModDevGradle, JUnit 5, GameTests, Python 3 + PIL.

**Spec:** [`docs/superpowers/specs/2026-10-15-ressonador-de-sinal-design.md`](../specs/2026-10-15-ressonador-de-sinal-design.md), seções "Sintonia", "Tela e câmara (etapa 3)" e **"Etapa 3: decisões de implementação"** (a fonte dos valores exatos). Mockup aprovado: `docs/preview/ressonador-etapa3.html` (a tela em `makeGui`, com as posições em pixels de interface; a câmara em `init3d`, com as fórmulas da figura, da placa, dos cristais e das partículas). Etapa 2 pronta: `resonator/` (o Núcleo, o lote, a montagem, as Portas, o comando), `client/resonator/` (a projeção) e os GameTests `Resonator*GameTests` com o apoio `ResonatorTestSupport` (`build(helper, origin, Layout, tier)`, `Layout.VITRINE` com Sintonizador no centro da face de cima e `Layout.ENERGY_ON_TOP` sem Sintonizador, `core(helper, corePos)`).

## Restrições globais

- Código, comentários, commits e docs em português; textos nos dois `lang` (`en_us.json`, `pt_br.json`), sempre os dois, na mesma ordem. Chaves novas da tela em `gui.wirelessautomate.resonator.*`.
- Sem comparador e sem controle por redstone (decisão do dono). Tablet, JEI, guia e vitrine ficam na etapa 4.
- Tela 352 × 244, tamanho fixo, com as posições da spec ("Etapa 3: decisões de implementação", item "Tela"). Cabeçalho igual ao da Bateria (`client/storage/StorageScalarScreen`: título, pílula do tier, `ThemeToggle` em `leftPos + W − 4 − ThemeToggle.SIZE`). A estação nunca aparece como número nem sai do servidor. Cores só por tokens do `GuiPaint` na hora de desenhar (os únicos fixos são `SCOPE_BG`, `SCOPE_GRID` e `SCOPE_LINE`, iguais nos dois temas); todo texto variável pelo `GuiText`; `GuiText.clipCount() == 0` nas capturas.
- Sintonizador: `AUTO_STEP_MHZ = 0,5` por passo da deriva (a cada 20 lotes = 400 ticks); tetos `{0,85; 0,88; 0,90; 0,92; 0,94; 0,95; 0,96; 0,98}` por tier, constantes, sem config. O modo é a chave de NBT `auto`; as outras chaves novas são `has_tuner`, `energy_ports`, `crystal_ports`, `layout` e `formed_at`. **Chaves existentes de NBT, componentes e config não mudam.**
- Sincronização: nada por tick com a tela fechada (update tag só quando a `ChamberView` muda); com a tela aberta, `ResonatorStatePayload` a cada 5 ticks e só se mudou; o desvio vai quantizado em passos de 0,05 só dentro de ±2 MHz (`TuneSignal.NO_SIGNAL = 999` fora).
- `ModPayloads.VERSION` sobe para `"17"` na Task 4 (payloads novos: `ResonatorStatePayload`, `ResonatorDialPayload`, `ResonatorModePayload`), e fica `"17"` até o fim.
- Config `resonator`: `fePerCrystal` padrão 128, 1.024, 8.192, 32.768, 262.144, 2.097.152, 134.217.728, 1.073.741.824; `crystalLifeTicks` 12.000, `cutCrystalLifeTicks` 24.000, `rechargeTicks` 600. Nenhuma chave nova.
- Performance: nenhum `BlockEntityTicker`; o BER só desenha com o Núcleo montado, `getViewDistance` 64 e a caixa de render 5 × 5 × 5; 260 pontos por tick do cliente interpolados por `partialTick`; texturas pré-geradas (nada de `DynamicTexture`); partículas só com o jogador a menos de 32 blocos, no máximo 8 faíscas e 4 de redstone vivas.
- Mekanism, Ars e Allthemodium continuam opcionais; nada de tipos deles aqui. Classes de `client/` nunca referenciadas por código comum (o `runGameTestServer` é um servidor dedicado e quebra se carregar uma): o gancho do bloco para a tela é o `ResonatorMenu.open` (código comum), e o do BER é o `ClientSetup`.
- Texturas: edite `scripts/textures/blocos.py`, nunca os PNGs. O gerador regrava PNGs antigos com bytes diferentes e muda `docs/preview/icones-preview.png` pela fonte local: depois de rodar, restaure com `git checkout --` tudo o que não é desta tarefa. Sprites têm 16 px de largura (`validar` em `identidade.py`), exceto a placa de Chladni, que tem 32 px (decisão do dono, mais perto do mockup): o `validar`/`salvar` passa a aceitar 32 px só para ela.
- Tamanho das classes (`wa-modular`): tela, componente e renderer até 500 linhas (dividir acima de 800); servidor até 600 (dividir acima de 1000); `python -I .claude/skills/wa-modular/scripts/medir.py` antes de cada commit. Comentários: `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados.
- GameTests: `@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`; estruturas do Ressonador no template `"empty7"`; teste pertinência, não contagem global; nulos com `Objects.equals`; espere o `onLoad` com `startSequence().thenWaitUntil(...)`; teste que muda config vai no lote `batch = "wirelessautomate_config"` e devolve o valor em `try/finally`. Menus nos testes: construa direto e atribua a `player.containerMenu`, como em `RouterMenuGameTests.openMenu`, devolvendo `player.inventoryMenu` no fim.
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`; nas tarefas que mexem em tela, render ou payload (Tasks 4 a 9), também o e2e: `WA_E2E="$PWD/run/e2e" ./gradlew runClient` (no Windows, sem `xvfb-run`; o `run/e2e/result.txt` deve dizer `OK`) e **abra as capturas novas** (a ferramenta Read mostra imagens) e descreva o que viu. Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, pare e conserte. Nunca dois jogos ao mesmo tempo; não mexa em `run/options.txt`.
- Commits sem linhas de atribuição, com o autor do dono; `git add` só dos arquivos da tarefa (nunca `.superpowers/`, nunca `run/`). Não faça push.

## Mapa de arquivos

Base `B/` = `src/main/java/io/github/matheusanbs/wirelessautomate/`, `T/` = `src/test/java/io/github/matheusanbs/wirelessautomate/`, `R/` = `src/main/resources/`.

| Arquivo | Papel |
| --- | --- |
| `B/resonator/Tuning.java` (mudar) | Puro: ganha o passo do Sintonizador (`autoStep`), o teto por tier (`ceiling`) e a eficiência capada (`capped`) |
| `B/resonator/TuneSignal.java` (novo) | Puro: o desvio quantizado em passos de 0,05 (`steps`, `NO_SIGNAL`), a faixa da eficiência (`band`, 0 a 7) e a lâmpada (`tuned`) |
| `B/resonator/ResonatorBlueprint.java` (mudar) | Puro: ganha `layoutOf` (Vitrine, Faixa ou nenhuma) a partir das peças da caixa |
| `T/resonator/TuningTest.java`, `TuneSignalTest.java`, `ResonatorBlueprintTest.java` | JUnit |
| `B/resonator/ResonanceCoreBlockEntity.java` (mudar) | O modo `auto`, a estrutura conhecida (`hasTuner`, Portas, montagem, `formed_at`), `crystals()` só leitura, `setCrystalAt`, `setRedstone`, `inventory()`, a `ChamberView` no update tag e o `syncIfChanged` |
| `B/resonator/ChamberView.java` (novo) | O que a câmara desenha: câmaras, faixa, lâmpada, recarga, montagem e origem; vai e volta de `CompoundTag` |
| `B/resonator/CoreInventory.java` (novo) | A visão `Container` do Núcleo para o menu: as câmaras do tier + o slot da redstone |
| `B/resonator/ResonatorFormation.java`, `ResonatorBatch.java`, `ResonanceCoreBlock.java`, `ResonatorStatus.java` (mudar) | Montar passa a estrutura ao Núcleo; o lote dá o passo do Sintonizador; o clique no montado abre a tela; o status mostra o modo |
| `B/resonator/SignalCrystalItem.java` (mudar) | A carga como barra de durabilidade do item |
| `B/menu/ResonatorMenu.java` (novo) | O menu da tela: slots, `stillValid`, o estado a cada 5 ticks só se mudou (`pollState`) |
| `B/packet/ResonatorStatePayload.java`, `ResonatorDialPayload.java`, `ResonatorModePayload.java` (novos), `ModPayloads.java` (mudar, `VERSION = "17"`) | Os payloads e os handlers com validação |
| `B/registry/ModMenus.java` (mudar) | `RESONATOR` |
| `B/client/resonator/screen/ResonatorScreen.java` (novo, pública) | A anfitriã: tamanho, cabeçalho, slots, repasse de desenho, mouse, roda e dicas, os ganchos do e2e |
| `B/client/resonator/screen/CoreScreenLayout.java` | As medidas fixas da tela |
| `B/client/resonator/screen/ScopeView.java`, `TunePointer.java`, `PointerScale.java` (pura), `DialRow.java`, `CrystalSlots.java`, `OutputPanel.java`, `StructureLines.java`, `ResonatorText.java`, `ResonatorScreenActions.java` | As partes da tela (osciloscópio; ponteiro e lâmpada; a escala do ponteiro; dial e modo; os slots e as barras dos cristais; eficiência, geração e energia; o bloco Estrutura; os textos; o envio dos payloads) |
| `B/client/GuiPaint.java` (mudar) | `SCOPE_BG`, `SCOPE_GRID`, `SCOPE_LINE` |
| `B/client/ClientSetup.java` (mudar) | Registra a tela e o renderer |
| `B/client/resonator/render/ResonatorRenderer.java` (novo) | O BER anfitrião: distância, caixa de render, a cena por Núcleo e a ordem das partes |
| `B/client/resonator/render/ChamberScene.java`, `LissajousCurve.java` (pura), `LissajousTrace.java`, `ChamberLens.java`, `ChladniPlate.java`, `ChamberCrystals.java`, `ChamberParticles.java` | As partes da câmara |
| `T/client/resonator/render/LissajousCurveTest.java`, `T/client/resonator/screen/PointerScaleTest.java` | JUnit |
| `scripts/textures/blocos.py` (mudar) | `block/resonator_chladni` (16 quadros de 32 × 32), `block/resonator_chladni_noise` (8), `block/resonator_halo` |
| `B/client/dev/E2eResonatorSteps.java` (mudar) | Os passos da tela (claro, escuro, pt_br) e da câmara |
| `B/gametest/ResonatorTestSupport.java` (mudar), `ResonatorTunerGameTests.java`, `ResonatorSyncGameTests.java`, `ResonatorMenuGameTests.java` (novos), `ResonatorCoreGameTests.java` (mudar) | GameTests |
| `R/assets/wirelessautomate/lang/en_us.json`, `pt_br.json` (mudar) | Textos |
| `CLAUDE.md`, `docs/progresso.md`, `docs/especificacao.md` (mudar) | Docs |

---

### Task 1: A lógica pura (Sintonizador, sinal e montagem)

**Files:**
- Modify: `B/resonator/Tuning.java`, `B/resonator/ResonatorBlueprint.java`
- Create: `B/resonator/TuneSignal.java`
- Modify: `T/resonator/TuningTest.java`, `T/resonator/ResonatorBlueprintTest.java`
- Create: `T/resonator/TuneSignalTest.java`

**Interfaces:**
- Consumes: `Tuning.clampDial`, `Tuning.efficiency`, `Tuning.DIAL_STEP`, `Tuning.PLATEAU`, `Tuning.ZERO` (existentes); `ResonatorLayout.Part` e `ResonatorBlueprint.Layout` (existentes).
- Produces:
  - `Tuning.AUTO_STEP_MHZ` (`0.5`); `Tuning.autoStep(double dial, double station) → double` (o dial anda até a estação no máximo 0,5, e passa pelo `clampDial`); `Tuning.ceiling(int tierOrdinal) → double` (`CEILINGS = {0.85, 0.88, 0.90, 0.92, 0.94, 0.95, 0.96, 0.98}`, pelo ordinal do `RouterTier`; fora da tabela, o último); `Tuning.capped(double efficiency, boolean auto, int tierOrdinal) → double` (`min(efficiency, ceiling)` só com `auto`).
  - `TuneSignal.NO_SIGNAL` (`999`), `TuneSignal.BANDS` (`8`), `TuneSignal.PLATEAU_STEPS` (`5`); `TuneSignal.steps(double station, double dial) → int` (`Math.round((station − dial) / DIAL_STEP)` se `|station − dial| < ZERO`, senão `NO_SIGNAL`); `TuneSignal.band(double efficiency) → int` (`(int) Math.ceil(clamp(e, 0, 1) × (BANDS − 1))`, 0 só a 0%); `TuneSignal.tuned(double station, double dial) → boolean` (`|station − dial| ≤ PLATEAU`).
  - `ResonatorBlueprint.layoutOf(Part[][][] local) → @Nullable Layout`: `local` é a caixa 5 × 5 × 5 em coordenadas locais `[x][y][z]`. `VITRINE` se o conjunto das posições de `GLASS` é exatamente o miolo 3 × 3 de uma das seis faces (qualquer face; as outras 45 posições de face sem vidro). `FAIXA` se todo `GLASS` está na linha de cima das quatro laterais (`y == 3` com `x ∈ {0, 4}` ou `z ∈ {0, 4}`, a outra coordenada de 1 a 3: 12 posições) e toda posição dessa linha que não é `GLASS` é uma peça (`CORE`, `ENERGY_PORT`, `CRYSTAL_PORT` ou `TUNER`; não `CASING`). Senão `null` (montagem própria). Sem vidro nenhum: `null`.

- [ ] **Step 1: Os testes que falham**

Acrescente ao `TuningTest`:

```java
    @Test
    void sintonizadorAndaAteMeioMhzPorPasso() {
        assertEquals(90.5, Tuning.autoStep(90.0, 98.0), 1e-9);
        assertEquals(97.5, Tuning.autoStep(98.0, 97.0), 1e-9);
        assertEquals(97.4, Tuning.autoStep(97.0, 97.4), 1e-9);
        assertEquals(97.4, Tuning.autoStep(97.4, 97.4), 1e-9);
        assertEquals(97.45, Tuning.autoStep(97.0, 97.4612), 1e-9);
        assertEquals(108.0, Tuning.autoStep(107.8, 108.3), 1e-9);
    }

    @Test
    void tetoPorTierEEficienciaCapada() {
        assertEquals(0.85, Tuning.ceiling(0), 1e-9);
        assertEquals(0.90, Tuning.ceiling(2), 1e-9);
        assertEquals(0.98, Tuning.ceiling(7), 1e-9);
        assertEquals(0.98, Tuning.ceiling(12), 1e-9);
        assertEquals(1.0, Tuning.capped(1.0, false, 2), 1e-9);
        assertEquals(0.90, Tuning.capped(1.0, true, 2), 1e-9);
        assertEquals(0.72, Tuning.capped(0.72, true, 2), 1e-9);
    }
```

`TuneSignalTest`:

```java
package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TuneSignalTest {
    @Test
    void desvioQuantizadoSoDentroDeDoisMhz() {
        assertEquals(0, TuneSignal.steps(98.0, 98.0));
        assertEquals(26, TuneSignal.steps(98.0, 96.7));
        assertEquals(-26, TuneSignal.steps(96.7, 98.0));
        assertEquals(39, TuneSignal.steps(98.0, 96.05));
        assertEquals(TuneSignal.NO_SIGNAL, TuneSignal.steps(98.0, 96.0));
        assertEquals(TuneSignal.NO_SIGNAL, TuneSignal.steps(98.0, 101.6));
    }

    @Test
    void faixaDaEficienciaVaiDeZeroASete() {
        assertEquals(0, TuneSignal.band(0.0));
        assertEquals(1, TuneSignal.band(0.01));
        assertEquals(4, TuneSignal.band(0.5));
        assertEquals(7, TuneSignal.band(0.95));
        assertEquals(7, TuneSignal.band(1.0));
        assertEquals(TuneSignal.BANDS - 1, TuneSignal.band(1.0));
    }

    @Test
    void lampadaAcendeNoPlato() {
        assertTrue(TuneSignal.tuned(98.0, 98.25));
        assertTrue(TuneSignal.tuned(98.0, 97.75));
        assertFalse(TuneSignal.tuned(98.0, 98.3));
        assertEquals(TuneSignal.PLATEAU_STEPS, TuneSignal.steps(98.25, 98.0));
    }
}
```

Acrescente ao `ResonatorBlueprintTest` (a `Probe`/o array `expected` já existem lá):

```java
    @Test
    void reconheceAMontagemPelasPecas() {
        for (Face face : Face.values()) {
            assertEquals(Layout.VITRINE, ResonatorBlueprint.layoutOf(ResonatorBlueprint.expected(face, Layout.VITRINE)));
            assertEquals(Layout.FAIXA, ResonatorBlueprint.layoutOf(ResonatorBlueprint.expected(face, Layout.FAIXA)));
        }
        Part[][][] custom = ResonatorBlueprint.expected(Face.EAST, Layout.VITRINE);
        custom[2][3][0] = Part.GLASS;
        assertNull(ResonatorBlueprint.layoutOf(custom));
        Part[][][] none = ResonatorBlueprint.expected(Face.EAST, Layout.VITRINE);
        for (int x = 1; x < 4; x++) for (int y = 1; y < 4; y++) none[x][y][4] = Part.CASING;
        assertNull(ResonatorBlueprint.layoutOf(none));
    }
```

- [ ] **Step 2: Rode e veja falhar**

Run: `./gradlew test --tests '*resonator*'`
Expected: falha de compilação (`autoStep`, `TuneSignal`, `layoutOf` não existem).

- [ ] **Step 3: Implemente**

Em `Tuning.java`:

```java
    public static final double AUTO_STEP_MHZ = 0.5;
    /** O teto do Sintonizador por tier (ordinal do RouterTier): Básico 85% … Ultimate 98%. */
    private static final double[] CEILINGS = {0.85, 0.88, 0.90, 0.92, 0.94, 0.95, 0.96, 0.98};

    /** Um passo do Sintonizador: o dial anda até a estação, no máximo {@link #AUTO_STEP_MHZ}, no passo do dial. */
    public static double autoStep(double dial, double station) {
        double delta = clamp(station - dial, -AUTO_STEP_MHZ, AUTO_STEP_MHZ);
        return clampDial(dial + delta);
    }

    public static double ceiling(int tierOrdinal) {
        return CEILINGS[Math.max(0, Math.min(CEILINGS.length - 1, tierOrdinal))];
    }

    /** No modo automático a eficiência para no teto do tier; no manual passa inteira. */
    public static double capped(double efficiency, boolean auto, int tierOrdinal) {
        return auto ? Math.min(efficiency, ceiling(tierOrdinal)) : efficiency;
    }
```

`TuneSignal.java`:

```java
package io.github.matheusanbs.wirelessautomate.resonator;

/**
 * O que o cliente sabe da sintonia sem conhecer a estação: o desvio em passos de 0,05 MHz (só dentro de ±2 MHz,
 * o que o ponteiro mostra), a faixa da eficiência (8 faixas, para a câmara) e a lâmpada. Lógica pura.
 */
public final class TuneSignal {
    /** Fora de ±2 MHz o ponteiro não tem o que mostrar. */
    public static final int NO_SIGNAL = 999;
    public static final int BANDS = 8;
    /** O platô de ±0,25 MHz em passos do dial. */
    public static final int PLATEAU_STEPS = (int) Math.round(Tuning.PLATEAU / Tuning.DIAL_STEP);

    private TuneSignal() {
    }

    public static int steps(double station, double dial) {
        double deviation = station - dial;
        if (Math.abs(deviation) >= Tuning.ZERO) {
            return NO_SIGNAL;
        }
        return (int) Math.round(deviation / Tuning.DIAL_STEP);
    }

    /** 0 só a 0%; 7 acima de 6/7. */
    public static int band(double efficiency) {
        double e = Math.max(0, Math.min(1, efficiency));
        return (int) Math.ceil(e * (BANDS - 1));
    }

    public static boolean tuned(double station, double dial) {
        return Math.abs(station - dial) <= Tuning.PLATEAU;
    }
}
```

Em `ResonatorBlueprint.java`, `layoutOf` pelas regras do bloco de interfaces (percorra as 125 posições uma vez: junte as posições de `GLASS`; teste a Vitrine comparando com o miolo de cada face; teste a Faixa pelas 12 posições da linha de cima).

- [ ] **Step 4: Rode e veja passar**

Run: `./gradlew test --tests '*resonator*'`
Expected: tudo verde, inclusive os testes antigos de `TuningTest` e `ResonatorBlueprintTest`.

- [ ] **Step 5: Verificação e commit**

Run: `./gradlew build`, a varredura de comentários e o `medir.py`.

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/resonator src/test/java/io/github/matheusanbs/wirelessautomate/resonator
git commit -m "Ressonador: lógica pura do Sintonizador, do sinal da tela e da montagem reconhecida"
```

---

### Task 2: O Núcleo com modo automático e a estrutura conhecida

**Files:**
- Modify: `B/resonator/ResonanceCoreBlockEntity.java`, `B/resonator/ResonatorFormation.java`, `B/resonator/ResonatorBatch.java`, `B/resonator/ResonanceCoreBlock.java`, `B/resonator/ResonatorStatus.java`, `B/resonator/ResonatorPorts.java` (só se usar `crystals().set`), `B/command/ResonatorCommand.java`, `lang/en_us.json`, `lang/pt_br.json`
- Modify: `B/gametest/ResonatorTestSupport.java`, `B/gametest/ResonatorCoreGameTests.java`
- Create: `B/gametest/ResonatorTunerGameTests.java`

**Interfaces:**
- Consumes: `Tuning.autoStep`, `Tuning.capped`, `ResonatorBlueprint.layoutOf` (Task 1); `ResonatorLayout.Formed.members()` e `Member.part()` (existentes).
- Produces (no `ResonanceCoreBlockEntity`):
  - `boolean auto()`; `boolean setAuto(boolean auto)` (devolve se valeu: ligar sem Sintonizador é recusado; chama `setChanged()`); `boolean hasTuner()`; `int energyPorts()`; `int crystalPorts()`; `@Nullable ResonatorBlueprint.Layout layout()`; `long formedAt()` (tempo de jogo da última montagem, `−1` se nunca montou).
  - `void setStructure(boolean hasTuner, int energyPorts, int crystalPorts, @Nullable Layout layout, long gameTime)` (package-private; chamado pelo `form()`; se `!hasTuner && auto`, `auto = false`; só grava `formedAt = gameTime` quando a estrutura não estava montada antes; `setChanged()` quando algo mudou).
  - `double efficiency()` passa a devolver `Tuning.capped(Tuning.efficiency(station − dial), auto, tier().ordinal())`; `double rawEfficiency()` dá a eficiência sem o teto (a lâmpada e a faixa do ponteiro usam `TuneSignal` com `station()` e `dial()` no servidor).
  - `List<ItemStack> crystals()` passa a devolver `Collections.unmodifiableList(crystals)` (continua com as `MAX_CHAMBERS` posições); `void setCrystalAt(int chamber, ItemStack stack)` (qualquer câmara até `MAX_CHAMBERS`; vazio ou uma unidade de `SignalCrystalItem`, senão ignora; `setChanged()` e `notifyPorts(false, true)` quando esvazia uma câmara); `void setRedstone(int count)` (0 a `MAX_REDSTONE`; `setChanged()`).
  - NBT: `auto` (boolean), `has_tuner` (boolean), `energy_ports` (int), `crystal_ports` (int), `layout` (byte: 0 nenhuma, 1 Vitrine, 2 Faixa), `formed_at` (long).
- `ResonatorFormation.form`: depois de montar os `members`, conta as peças (`Member.part()`): `hasTuner` (algum `TUNER`), `energyPorts`, `crystalPorts`; monta o `Part[5][5][5]` local a partir dos membros (origem = `formed.minX/minY/minZ`; interior `AIR`) e chama `ResonatorBlueprint.layoutOf`; chama `core.setStructure(..., level.getGameTime())` **antes** do `core.setFormed(true, members)`. O `disassemble` não mexe no modo.
- `ResonatorBatch.drift`: quando o passo da deriva acontece e `core.auto() && core.hasTuner()`, depois do `setDrift` chama `core.setDial(Tuning.autoStep(core.dial(), core.station()))`.
- `ResonatorStatus.line` (montado) ganha o modo no fim: chave `block.wirelessautomate.resonance_core.status` vira `"%s · %s/%s crystals · %s%% · %s FE/t · %s"` / `"%s · %s/%s cristais · %s%% · %s FE/t · %s"` com o sexto argumento `block.wirelessautomate.resonance_core.mode.manual` ("Manual") ou `.mode.auto` ("Tuner" / "Sintonizador"). O comando `/wa resonator <pos> info` mostra o mesmo modo numa linha nova `command.wirelessautomate.resonator.info.mode`: `"  Mode: %s · Tuner in the structure: %s"` / `"  Modo: %s · Sintonizador na estrutura: %s"` (`yes`/`no` pelas chaves `command.wirelessautomate.resonator.yes`/`no`: "yes"/"sim", "no"/"não").
- `ResonanceCoreBlock.onRemove`: `Containers.dropContents` não aceita mais a lista; derrube cada cristal com `Containers.dropItemStack(server, pos.getX(), pos.getY(), pos.getZ(), stack)`.
- Mova para o `ResonatorTestSupport` (package-private, estáticos) os ajudantes `survivor(helper)`, `hit(absolute)`, `formed(helper, tier, body)`, `crystal(cut, charge)` e `assertInserted(helper, core, stack)` que hoje são privados do `ResonatorCoreGameTests`, e faça esse arquivo usá-los de lá. Acrescente `formed(helper, Layout layout, tier, body)` (o `formed` atual vira o caso `VITRINE`).

- [ ] **Step 1: GameTests que falham** (`ResonatorTunerGameTests`, template `"empty7"`):
  - `sintonizadorAndaAteMeioMhzPorPasso`: Vitrine Elite montada; `setStationForTest(98.0)`, `setDial(90.0)`, `setAuto(true)` devolve `true`; rode `ResonatorBatch.run(level, core, 20)` 20 vezes; `dial() == 90.5` (a estação anda no máximo 0,04 no passo e continua longe); mais 20 lotes: `91.0`.
  - `sintonizadorParaNoTeto`: estação 98,0, dial 98,0, auto ligado, 3 Lapidados; `efficiency() == 0.90` (Elite) e `rawEfficiency() == 1.0`; um lote gera `CoreMath.perTick(8_192, 6, 0.9) × 20` (= 44.236 × 20 = 884.720).
  - `manualNaoTemTeto`: o mesmo, com `auto` desligado: `efficiency() == 1.0`.
  - `semSintonizadorNaoLigaOAutomatico`: `Layout.ENERGY_ON_TOP` (sem Sintonizador); `hasTuner()` falso; `setAuto(true)` devolve `false` e `auto()` continua falso.
  - `pecaQueSaiVoltaAoManual`: Vitrine com auto ligado e dial 94,0; troque o Sintonizador (`ORIGIN.offset(2, 4, 2)`) por Carcaça com `helper.setBlock`; `thenWaitUntil` `formed() && !hasTuner()`; `auto()` falso e `dial() == 94.0`.
  - `estruturaConhecida`: Vitrine montada: `hasTuner()` verdadeiro, `energyPorts() == 1`, `crystalPorts() == 1`, `layout() == ResonatorBlueprint.Layout.VITRINE`, `formedAt() >= 0`; depois de desmontar e remontar (quebrar e repor uma carcaça de aresta), `formedAt()` é maior que antes.
  - `cristaisSoLeitura`: `core.crystals().set(0, ItemStack.EMPTY)` lança `UnsupportedOperationException`; `setCrystalAt(0, cristal)` põe e `setCrystalAt(0, ItemStack.EMPTY)` tira.
  - `modoSobreviveAoNbt`: `setAuto(true)`, `saveWithoutMetadata` num tag, um Núcleo novo carregado dele tem `auto()` verdadeiro, `hasTuner()`, as contagens e a `layout()` iguais.
- [ ] **Step 2: Compile e veja falhar** (`./gradlew compileJava` → `BUILD FAILED`).
- [ ] **Step 3: Implemente** o bloco de interfaces. Os testes antigos de `ResonatorCoreGameTests` que leem `core.crystals().get(i)` continuam valendo (a visão é só leitura, mas as pilhas são as mesmas); o `ResonatorBatch.generate` e o `recharge` continuam gravando a carga na própria pilha.
- [ ] **Step 4: Rode** `./gradlew build runGameTestServer` e veja os 8 novos e os antigos passarem.
- [ ] **Step 5: Verificação completa, varredura, `medir.py` e commit** (`"Ressonador: modo automático do Sintonizador e a estrutura conhecida pelo Núcleo"`).

---

### Task 3: A câmara sincronizada (update tag) e a carga como barra do item

**Files:**
- Create: `B/resonator/ChamberView.java`
- Modify: `B/resonator/ResonanceCoreBlockEntity.java`, `B/resonator/ResonatorBatch.java`, `B/resonator/SignalCrystalItem.java`
- Create: `B/gametest/ResonatorSyncGameTests.java`

**Interfaces:**
- Consumes: `TuneSignal.band`, `TuneSignal.tuned` (Task 1); `hasTuner`, `formedAt`, `setCrystalAt`, `setRedstone` (Task 2); `CrystalCharge` (etapa 1).
- Produces:
  - `ChamberView` (record `(byte[] chambers, int band, boolean tuned, long rechargeStart, int rechargeTicks, long formedAt, int[] origin)` com `equals`/`hashCode` por `Arrays`): `ChamberView.EMPTY` (sem câmaras, faixa 0, `rechargeStart −1`, `formedAt −1`, origem `{0, 0, 0}`); `static ChamberView of(ResonanceCoreBlockEntity core, long gameTime)`: `chambers` com `core.chambers()` posições (`0` vazia, `1` Cristal de Sinal, `2` Lapidado, `3` Cristal Opaco, `4` Lapidado Opaco), `band = TuneSignal.band(core.efficiency())`, `tuned = TuneSignal.tuned(station, dial)`, `rechargeStart = gameTime − core.rechargeProgress()` se há Opaco e redstone, senão `−1`, `rechargeTicks = (int) min(Config.rechargeTicks(), Integer.MAX_VALUE)`, `formedAt = core.formedAt()`, `origin` = o mínimo de `core.members()` (ou a posição do Núcleo sem membros); `void save(CompoundTag tag)` (chaves `chambers` byte array, `band` byte, `tuned` boolean, `recharge_start` long, `recharge_ticks` int, `formed_at` long, `origin` int array); `static ChamberView load(CompoundTag tag)` (sem a chave `chambers`, `EMPTY`).
  - No Núcleo: `CompoundTag getUpdateTag(HolderLookup.Provider)` (a view atual, `ChamberView.of(this, level.getGameTime())`, salva num tag novo); `ClientboundBlockEntityDataPacket getUpdatePacket()` (`ClientboundBlockEntityDataPacket.create(this)`); `void handleUpdateTag(CompoundTag, HolderLookup.Provider)` (cliente: `clientView = ChamberView.load(tag)`, e se `tuned` passou de falso a verdadeiro, `tunedAt = level.getGameTime()`); `ChamberView clientView()`; `long tunedAt()` (cliente, `−1` antes da primeira sintonia vista); `boolean syncIfChanged()` (servidor: monta a view; se diferente de `sentView`, guarda, incrementa `syncCount` e chama `level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)`; devolve se mandou); `int syncCount()`.
  - Chamadas do `syncIfChanged`: no fim de `insertCrystal`, `insertCrystalAt`, `takeCrystalAt`, `takeLastCrystal`, `setCrystalAt`, `addRedstone`, `setRedstone`, `setDial`, `setAuto`, `setStructure`, `setFormed`, `initStation` e no fim de `ResonatorBatch.run` (uma vez, depois das transições). Nunca no `loadAdditional` nem no `onLoad`.
  - `SignalCrystalItem`: `isBarVisible(stack)` = `0 < charge < CrystalCharge.FULL`; `getBarWidth(stack)` = `Math.round(13f × charge / FULL)`; `getBarColor(stack)` = `0xF0603E`.

- [ ] **Step 1: GameTests que falham** (`ResonatorSyncGameTests`, template `"empty7"`, com os ajudantes do `ResonatorTestSupport`):
  - `updateTagTemAsCamarasEAFaixa`: Vitrine Esmeralda (4 câmaras) com Cristal, Lapidado e um Lapidado Opaco; estação 98,0, dial 98,0; `getUpdateTag` → `ChamberView.load`: `chambers == {1, 2, 4, 0}`, `band == 7`, `tuned` verdadeiro, `formedAt >= 0`, `origin == ORIGIN absoluta` (`helper.absolutePos(ORIGIN)`), `rechargeStart == −1` (sem redstone); com 3 de redstone, `rechargeStart == gameTime` (progresso 0).
  - `viewVaiEVoltaDoNbt`: `ChamberView.of(core, 100)` → `save` → `load` dá uma view `equals`.
  - `loteSemMudancaNaoManda`: em sintonia, buffer vazio, 2 cristais; `syncCount()` antes; 5 lotes; `syncCount()` não muda (a carga baixa, a view não).
  - `mudarDeFaixaManda`: estação 98,0 e dial 98,0 (faixa 7, lâmpada acesa); `setDial(97.0)` (72%, faixa 6, apagada): `syncCount()` sobe 1; `setDial(97.05)` (74,7%, ainda faixa 6, ainda apagada): não sobe.
  - `lampadaManda`: `setDial(97.7)` (|d| = 0,3, apagada, 97%, faixa 7) e depois `setDial(97.8)` (0,2, acesa, faixa 7): `syncCount()` sobe 1 só pela lâmpada.
  - `cristalNovoManda`: `insertCrystal` sobe 1; `takeLastCrystal` sobe 1; `addRedstone(3)` com um Opaco sobe 1 (a recarga começa); `addRedstone(2)` de novo não sobe (continua a mesma recarga).
  - `barraDoCristal`: `isBarVisible` falso no cheio e no Opaco, verdadeiro com carga `FULL / 2`; `getBarWidth` 7 em `FULL / 2` (`Math.round(6.5f) == 7`); `getBarColor == 0xF0603E`.
- [ ] **Step 2: Compile e veja falhar.**
- [ ] **Step 3: Implemente.** Guarde `sentView` e `syncCount` fora do NBT. O `handleUpdateTag` precisa ignorar o tag completo do `saveAdditional` (que não tem a chave `chambers`, então `load` devolve `EMPTY`; nesse caso não troque a view).
- [ ] **Step 4: Rode** `./gradlew build runGameTestServer`.
- [ ] **Step 5: Verificação completa, varredura, `medir.py` e commit** (`"Ressonador: update tag da câmara só quando muda e a carga como barra do cristal"`). Se o `ResonanceCoreBlockEntity` passar de 600 linhas, tire a view e o sync para uma parte de pacote `CoreSync` (como as partes do roteador) antes de commitar.

---

### Task 4: O menu e os payloads

**Files:**
- Create: `B/resonator/CoreInventory.java`, `B/menu/ResonatorMenu.java`, `B/packet/ResonatorStatePayload.java`, `B/packet/ResonatorDialPayload.java`, `B/packet/ResonatorModePayload.java`
- Modify: `B/packet/ModPayloads.java`, `B/registry/ModMenus.java`, `B/resonator/ResonanceCoreBlock.java`, `B/resonator/ResonanceCoreBlockEntity.java`
- Create: `B/gametest/ResonatorMenuGameTests.java`

**Interfaces:**
- Consumes: `TuneSignal.steps`, `Tuning.ceiling` (Task 1); `auto`, `setAuto`, `hasTuner`, `energyPorts`, `crystalPorts`, `layout`, `setCrystalAt`, `setRedstone`, `efficiency` (Task 2); `StorageListMenu.SYNC_INTERVAL` (existente, 5) e `Container.stillValidBlockEntity(BlockEntity, Player)` (vanilla, 8 blocos); `ModPayloads.serverPlayer(context)` (existente).
- Produces:
  - `CoreInventory implements Container` (em `resonator/`, `public`): `new CoreInventory(ResonanceCoreBlockEntity core)`; `getContainerSize()` = `core.chambers() + 1`; `getItem(i)`: câmara `core.crystals().get(i)`, o último `new ItemStack(Items.REDSTONE, core.redstone())` (vazio com 0); `setItem(i, stack)`: câmara → `core.setCrystalAt(i, stack)`, redstone → `core.setRedstone(stack.is(Items.REDSTONE) ? stack.getCount() : 0)`; `removeItem(i, count)` e `removeItemNoUpdate(i)` pelas mesmas regras (`ContainerHelper` não serve: a redstone é um inteiro); `getMaxStackSize()` 64; `canPlaceItem(i, stack)`: câmara só `SignalCrystalItem`, redstone só `Items.REDSTONE`; `stillValid(player)` = `!core.isRemoved() && core.formed() && Container.stillValidBlockEntity(core, player)` (o vanilla: o block entity ainda no lugar e o jogador a até 8 blocos); `setChanged()` → `core.setChanged()`; `clearContent()` esvazia tudo. `ResonanceCoreBlockEntity.inventory() → CoreInventory` (criado uma vez).
  - `ResonatorMenu extends AbstractContainerMenu`: servidor `ResonatorMenu(int containerId, Inventory inventory, ResonanceCoreBlockEntity core)`; cliente `ResonatorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf)` (lê `BlockPos`, o ordinal do tier e `chambers`); `static void open(ServerPlayer player, ResonanceCoreBlockEntity core)` (`SimpleMenuProvider` com o nome do bloco; escreve pos, tier e `chambers`); `BlockPos pos()`; `RouterTier tier()`; `int chambers()`; `int redstoneSlot()` (= `chambers`); `int inventoryStart()` (= `chambers + 1`); `@Nullable ResonatorStatePayload state()` (cliente, a última recebida); `boolean received()`; `void apply(ResonatorStatePayload)`; `@Nullable ResonatorStatePayload pollState(long now)` (servidor: monta o estado; devolve-o se é o primeiro, ou se mudou e passaram `SYNC_INTERVAL` ticks desde o último enviado; guarda o enviado); `static void onState(ResonatorStatePayload, IPayloadContext)` (cliente, como `StorageScalarMenu.onState`); `@Nullable ResonanceCoreBlockEntity core()` (servidor).
    - Slots: câmara `i` em `(191 + 18 × i, 39)` com `getMaxStackSize() == 1` e `mayPlace` só `SignalCrystalItem`; o da redstone em `(191, 61)` só `Items.REDSTONE`; inventário em `(11 + 18 × col, 159 + 18 × row)` e barra em `(11 + 18 × col, 217)`. No cliente o `Container` é um `SimpleContainer(chambers + 1)` (o vanilla sincroniza os slots).
    - `quickMoveStack`: de um slot do Núcleo para o inventário (barra primeiro, como o roteador); do inventário, cristal → câmaras (`moveItemStackTo(stack, 0, chambers, false)`), redstone → o slot dela, senão entre inventário e barra.
    - `broadcastChanges`: `super`, depois `pollState(viewer.server.getTickCount())` e, se veio payload e `viewer.connection.hasChannel(payload)`, `PacketDistributor.sendToPlayer`.
    - `stillValid(player)` = `inventory.stillValid(player)` (servidor) ou `true` (cliente).
  - `ResonatorStatePayload(int containerId, long energy, long capacity, long generation, long maxGeneration, double dial, int efficiencyPermille, int ceilingPermille, boolean auto, boolean tuner, int deviationSteps, int recharge, int rechargeTicks, int energyPorts, int crystalPorts, int layout)` (`TYPE` `wirelessautomate:resonator_state`; `STREAM_CODEC` com `VAR_INT`, `VAR_LONG`, `DOUBLE` e `BOOL`: como o `composite` do `StreamCodec` só aceita até 6 campos de uma vez, escreva o codec à mão com `StreamCodec.of(encode, decode)` sobre o `RegistryFriendlyByteBuf`). `maxGeneration` = `CoreMath.perTick(fePerCrystal, soma dos rendimentos de todos os cristais, inclusive Opacos, 1.0)`; `layout` 0 própria, 1 Vitrine, 2 Faixa; `deviationSteps = TuneSignal.steps(core.station(), core.dial())`; `efficiencyPermille = (int) Math.round(core.efficiency() × 1000)`; `ceilingPermille = (int) Math.round(Tuning.ceiling(tier.ordinal()) × 1000)`; `recharge = core.rechargeProgress()` se há Opaco e redstone, senão 0.
  - `ResonatorDialPayload(int containerId, double mhz)` (`wirelessautomate:resonator_dial`) e `ResonatorModePayload(int containerId, boolean auto)` (`wirelessautomate:resonator_mode`), `playToServer`.
  - `ModPayloads.handleResonatorDial(@Nullable ServerPlayer, ResonatorDialPayload) → boolean` e `handleResonatorMode(@Nullable ServerPlayer, ResonatorModePayload) → boolean` (públicos, para os testes): acham o `ResonatorMenu` aberto do jogador com esse `containerId` cujo `stillValid(player)` vale e cujo `core()` está montado; o dial é recusado com `core.auto()` e aplica `core.setDial(mhz)` (o `clampDial` cuida do passo e da faixa); o modo aplica `core.setAuto(auto)` e devolve o que ele devolveu. `VERSION = "17"`.
  - `ModMenus.RESONATOR` (`"resonator"`, `IMenuTypeExtension.create(ResonatorMenu::new)`).
  - `ResonanceCoreBlock.useWithoutItem`: no servidor, sem Shift e com `FORMED` verdadeiro, `ResonatorMenu.open(serverPlayer, core)` em vez da linha do status; solto, continua mandando `ResonatorStatus.line(core)`. Atualize o javadoc.

- [ ] **Step 1: GameTests que falham** (`ResonatorMenuGameTests`, template `"empty7"`; abra o menu como `RouterMenuGameTests.openMenu`: `new ResonatorMenu(CONTAINER_ID, player.getInventory(), core)` e `player.containerMenu = menu`, devolvendo `player.inventoryMenu` no fim):
  - `cliqueNoMontadoAbreOMenu`: Vitrine montada; `useWithoutItem` com um `survivor` sem Shift e mão vazia; `player.containerMenu instanceof ResonatorMenu` com `pos()` do Núcleo.
  - `cliqueNoSoltoNaoAbre`: Núcleo solto (só o bloco): `useWithoutItem` deixa `player.containerMenu == player.inventoryMenu`.
  - `slotsSaoAsCamarasDoTierMaisRedstone`: Elite: `menu.slots.size() == 3 + 1 + 36`; `redstoneSlot() == 3`; os slots 0 a 2 aceitam só cristal e um por vez (`mayPlace`, `getMaxStackSize`); o 3 só redstone.
  - `shiftCliqueDoInventarioPoeNoNucleo`: um Lapidado no slot 9 do inventário do jogador (o primeiro do inventário principal); `menu.quickMoveStack(player, inventoryStart())` → a câmara 0 tem o Lapidado; 10 de redstone na barra → vão para o slot da redstone e `core.redstone() == 10`; Shift de volta na câmara 0 → o cristal volta ao inventário e a câmara esvazia.
  - `estadoVaiSoQuandoMuda`: `pollState(100)` não nulo (o primeiro); `pollState(102)` nulo (nada mudou); `setDial(97.0)`; `pollState(103)` nulo (muito cedo); `pollState(105)` não nulo com `dial() == 97.0`; o estado traz `tuner` verdadeiro, `energyPorts() == 1`, `layout() == 1`, `deviationSteps() == 20` (estação 98,0, dial 97,0) e `efficiencyPermille() == 720 ± 5`.
  - `desvioForaDeDoisMhzEhSemSinal`: dial 94,0, estação 98,0: `deviationSteps() == TuneSignal.NO_SIGNAL`.
  - `payloadDoDialValida`: `handleResonatorDial(player, new ResonatorDialPayload(CONTAINER_ID, 97.42))` devolve `true` e `dial() == 97.40`; com `CONTAINER_ID + 1` devolve `false`; com `setAuto(true)` devolve `false` e o dial não muda; com o jogador a 20 blocos (`player.teleportTo`) devolve `false`.
  - `payloadDoModoExigeSintonizador`: Vitrine: `handleResonatorMode(..., true)` devolve `true` e `auto()`; `ENERGY_ON_TOP`: devolve `false`.
- [ ] **Step 2: Compile e veja falhar.**
- [ ] **Step 3: Implemente.** Registre os três payloads no `ModPayloads.register` (o `ResonatorStatePayload` com `ResonatorMenu::onState`) e `VERSION = "17"`.
- [ ] **Step 4: Rode** `./gradlew build runGameTestServer` (o teste `cliqueNoMontadoAbreOMenu` prova que o `open` funciona com o jogador falso; se o `openMenu` do jogador falso não atribuir o `containerMenu`, confira que o `SimpleMenuProvider` cria o menu e que o `ResonatorMenu` tem o `MenuType` certo).
- [ ] **Step 5: Verificação completa** (as quatro runs; o e2e ainda não abre a tela, mas rode-o para provar que o protocolo `17` não quebrou nada), varredura, `medir.py` e commit (`"Ressonador: menu do Núcleo e os payloads do estado, do dial e do modo (protocolo 17)"`).

---

### Task 5: A tela do Núcleo

**Files:**
- Create: `B/client/resonator/screen/ResonatorScreen.java`, `CoreScreenLayout.java`, `ScopeView.java`, `TunePointer.java`, `PointerScale.java`, `DialRow.java`, `CrystalSlots.java`, `OutputPanel.java`, `StructureLines.java`, `ResonatorText.java`, `ResonatorScreenActions.java`
- Create: `T/client/resonator/screen/PointerScaleTest.java`
- Modify: `B/client/GuiPaint.java`, `B/client/ClientSetup.java`, `lang/en_us.json`, `lang/pt_br.json`

**Interfaces:**
- Consumes: `ResonatorMenu` (`state()`, `received()`, `chambers()`, `tier()`, `pos()`, `redstoneSlot()`, `inventoryStart()`), `ResonatorStatePayload`, `ResonatorDialPayload`, `ResonatorModePayload` (Task 4); `TuneSignal.NO_SIGNAL`, `PLATEAU_STEPS`, `Tuning.DIAL_STEP`, `MIN_MHZ`, `MAX_MHZ` (Task 1); `GuiPaint`, `GuiText`, `ThemeToggle`, `RateFormat.abbreviate`, `TierCoreItem.grouped` (existentes; a Bateria mostra como usar cada um).
- Produces:
  - `GuiPaint.SCOPE_BG = 0xFF1B1920`, `SCOPE_GRID = 0xFF2F2C35`, `SCOPE_LINE = 0xFF8EE8E1` (`public static final int`, fora da paleta: iguais nos dois temas, com o comentário dizendo por quê).
  - `CoreScreenLayout` (package-private, constantes): `W = 352`, `H = 244`, `HEAD_Y = 8` e `X0 = 10` (como a Bateria), `SCOPE = (10, 28, 172, 50)`, `POINTER = (10, 80, 172, 22)`, `LAMP = (10, 106, 172, 8)`, `DIAL = (10, 120, 172, 14)`, `MODE = (10, 138, 172, 14)`, `CRYSTALS_LABEL = (190, 28)`, `CRYSTAL_SLOTS = (190, 38)`, `REDSTONE_SLOT = (190, 60)`, `EFFICIENCY = (190, 84)`, `EFFICIENCY_BAR = (190, 94, 152, 6)`, `GENERATION = (190, 106)`, `ENERGY = (190, 120)`, `ENERGY_BAR = (190, 130, 152, 6)`, `ENERGY_HINT = (190, 140)`, `INVENTORY = (10, 158)`, `HOTBAR = (10, 216)`, `STRUCTURE = (190, 158)` com as linhas em 170, 182, 194 e 206; `BUTTON = 14`, `RIGHT_W = 152`.
  - `PointerScale` (pura): `pixelsPerMhz(int width) → double` (`(width / 2.0 − 16) / 2`); `x(int width, double mhz) → int` (`round(width / 2 + mhz × pixelsPerMhz)`); `pointerMhz(int steps) → double` (`−steps × 0,05`: a escala tem a estação no centro e mostra onde o dial está; `NO_SIGNAL` encosta em `∓2`: devolve `2` se `steps` é `NO_SIGNAL`, e o chamador decide o lado pelo último sinal visto, ou a direita); `plateau(int width) → int[]{x(−0,25), x(0,25)}`; `isTick(double mhz) → boolean` e `isMajor(double mhz)` para os traços a cada 0,5 e os rótulos a cada 1.
  - `ResonatorText` (package-private): `key(String)` (prefixo `gui.wirelessautomate.resonator.`), `mhz(double) → Component` (duas casas, chave `dial.value`; o separador decimal segue o idioma do jogo: vírgula em pt_br (`94,00 MHz`), ponto em en_us (`94.00 MHz`), como no mockup aprovado; decida pela chave de idioma, não pelo `Locale` do sistema), `percent(int permille) → String`, `fe(long)`, os textos do osciloscópio pelo desvio (`scope.station` / `scope.close` / `scope.static`), e as dicas de cada controle (listas de `Component`, a segunda linha em `GuiPaint.TOOLTIP_MUTED`, a linha de aviso em `GuiPaint.TOOLTIP_ACCENT`).
  - `ResonatorScreenActions`: `setDial(double mhz)` e `setAuto(boolean)` mandam `ResonatorDialPayload`/`ResonatorModePayload` com o `containerId` do menu pelo `PacketDistributor.sendToServer`; nada é mudado localmente (a tela espera o estado, como o roteador).
  - Componentes (todos package-private; `layout(int x, int y)` recebe o canto absoluto da tela; `render(GuiGraphics, Font, int mouseX, int mouseY, float partialTick)`; `tooltip(int mouseX, int mouseY) → @Nullable List<Component>`; `mouseClicked(double, double, int) → boolean`; `mouseScrolled(double, double, double) → boolean` onde faz sentido):
    - `ScopeView`: fundo `SCOPE_BG` com a borda de 1 px mais escura, a grade (10 colunas, 5 linhas em `SCOPE_GRID`), a senoide de 6 períodos com amplitude `0,34 × h × e` e ruído `(1 − e)` (duas senoides de frequências 0,23 e 0,57 somadas a um aleatório por ponto, como o `drawScope` do mockup; a fase anda `6 rad/s` pelo `partialTick` e pelo tempo do cliente), desenhada com `g.fill` de 1 px por coluna, ligando colunas vizinhas com um traço vertical; o rótulo no canto superior esquerdo. Sem estado: `e = efficiencyPermille / 1000`, e `NO_SIGNAL` → só ruído e CHIADO.
    - `TunePointer`: a faixa afundada (`GuiPaint.inset`) de 14 px, o platô em `GuiPaint.alpha(GuiPaint.OK, 56)`, os traços e os rótulos `−2 … +2` em `GuiPaint.MUTED` (texto de 8 px: `font.draw` com escala 1; os rótulos ficam embaixo da faixa, dentro dos 22 px), o ponteiro de 2 px em `GuiPaint.ACCENT` (ou `GuiPaint.DISABLED` encostado, com o texto `pointer.no_signal` em `GuiPaint.MUTED` dentro da faixa) e a lâmpada embaixo (bolinha de 7 px: `GuiPaint.OK` acesa com o texto em `OK`, `GuiPaint.DISABLED` apagada com o texto em `MUTED`); acesa quando `|steps| ≤ PLATEAU_STEPS`.
    - `DialRow`: os quatro botões de 14 × 14 (`«` `‹` `›` `»`, desenhados com `GuiPaint.box` em `BUTTON`/`BUTTON_BORDER`, `BUTTON_HOVER_BORDER` sob o mouse, `DISABLED` no modo automático), o campo (`GuiPaint.inset`, o `mhz(dial)` centralizado, em `MUTED` no automático) e a linha do modo: Manual (64 px) e Sintonizador (104 px), o escolhido em `SELECTED`/`SELECTED_TEXT`, o Sintonizador em `DISABLED` sem a peça; cliques chamam `actions.setDial(dial ± passo)` (só no manual) e `actions.setAuto(...)` (só com a peça); a roda sobre o campo manda `± DIAL_STEP` (só no manual) e devolve `true`. Dicas: `dial.minus_one` ("−1 MHz"), `dial.minus_step` ("−0.05 MHz"), `dial.plus_step`, `dial.plus_one`, campo `dial.tooltip` + `dial.tooltip.wheel`, Manual `mode.manual` + `mode.manual.tooltip`, Sintonizador `mode.auto` + (`mode.auto.tooltip` e `mode.auto.ceiling` com o nome do tier e o teto, ou a linha de aviso `mode.auto.missing`).
    - `CrystalSlots`: o rótulo (`crystals.one`/`crystals.many` com o número de câmaras), o fundo de cada slot (`GuiPaint.slot` em `slot.x − 1, slot.y − 1`, como o roteador) para as câmaras e a redstone, o tracejado da câmara vazia (`GuiPaint.alpha(GuiPaint.LINE, 160)` em pontilhado de 1 px), a barra da recarga em `GuiPaint.BRASS` sobre o slot do Opaco (13 × 1 px em `x + 3, y + 14`, largura `13 × recharge / rechargeTicks`; o item em si é desenhado pelo `AbstractContainerScreen`, com a barra do item do cristal) e o rótulo de duas linhas da redstone. Dicas por slot: câmara vazia `chamber.empty` + `chamber.accepts`; cristal `chamber.charge` (a porcentagem pelo `CrystalCharge.percent`) + `chamber.yield` (rendimento e `generation` do cristal: `fePerCrystal` não chega ao cliente, então use `generation × rendimento ÷ soma dos rendimentos ativos`, arredondado); Opaco `chamber.recharging` + `chamber.with_redstone` ou a linha de aviso `chamber.no_redstone`; redstone `redstone.tooltip` + `redstone.tooltip.time` (`rechargeTicks / 20` s). Quando a dica do slot é a do componente, a anfitriã não desenha a dica padrão do item.
    - `OutputPanel`: Eficiência (rótulo e `percent` à direita), a barra (`GuiPaint.box` em `INSET`/`LINE`, o preenchimento em `OK` ≥ 95%, `WARN` ≥ 75%, senão `ACCENT`, e a marca do teto de 1 px em `FG` saindo 2 px acima e abaixo), Geração (`generation.value` com `RateFormat.abbreviate`), Energia (`energy.value` abreviada), a barra da energia (em `WARN`, como a Bateria) e a legenda `energy.hint`. Dicas: barra da eficiência `efficiency.tooltip` + `efficiency.tooltip.ceiling`; geração `generation.tooltip` (inteiro com `TierCoreItem.grouped`) + `generation.tooltip.max`; energia `energy.tooltip` + `energy.tooltip.port`.
    - `StructureLines`: o título e as quatro linhas (`structure.tuner` sim/não, `structure.energy_ports`, `structure.crystal_ports`, `structure.layout` com `layout.vitrine`/`layout.faixa`/`layout.custom`), rótulo em `MUTED` à esquerda e o valor em `FG` à direita (`GuiPaint.textRight`).
  - `ResonatorScreen extends AbstractContainerScreen<ResonatorMenu>` (pública): `imageWidth = W`, `imageHeight = H`; o cabeçalho como a Bateria (título = nome do bloco, pílula do tier com `GuiPaint.tierColor(menu.tier())`, `ThemeToggle`); `renderBg` desenha o painel (`GuiPaint.panel` com a cor do tier), o cabeçalho, chama o `render` das partes e o fundo dos slots do inventário (`GuiPaint.slot`); `render` chama `GuiText.beginFrame()`, desenha, depois as dicas (`ThemeToggle.tooltip`, a dica da parte sob o mouse, senão `renderTooltip` do item); `mouseClicked` passa pelo `ThemeToggle` e pelas partes antes do `super`; `mouseScrolled` pelo `DialRow`; `renderLabels` vazio (os textos saem no `renderBg`, como a Bateria). Antes do primeiro estado (`!menu.received()`), as partes desenham os fundos e o texto `loading`. Ganchos do e2e (públicos): `int[] dialPlusCenter()`, `int[] dialMinusCenter()`, `int[] modeAutoCenter()`, `int[] modeManualCenter()`, `int[] fieldCenter()`, `int[] themeCenter()`.
  - `ClientSetup.registerScreens`: `event.register(ModMenus.RESONATOR.get(), (ResonatorMenu menu, Inventory inventory, Component title) -> new ResonatorScreen(menu, inventory, title))`.
- Textos (`en_us` / `pt_br`), todos com o prefixo `gui.wirelessautomate.resonator.`: `loading` "Loading…" / "Carregando…"; `scope.station` "STATION" / "ESTAÇÃO"; `scope.close` "ALMOST THERE" / "QUASE LÁ"; `scope.static` "STATIC" / "CHIADO"; `pointer.no_signal` "no signal: sweep the dial" / "sem sinal: varra o dial"; `lamp` "IN TUNE" / "EM SINTONIA"; `dial.value` "%s MHz"; `dial.tooltip` "Dial"; `dial.tooltip.wheel` "Mouse wheel: ±0.05 MHz" / "Roda do mouse: ±0,05 MHz"; `dial.minus_one` "−1 MHz"; `dial.minus_step` "−0.05 MHz" / "−0,05 MHz"; `dial.plus_step` "+0.05 MHz" / "+0,05 MHz"; `dial.plus_one` "+1 MHz"; `mode.manual` "Manual"; `mode.manual.tooltip` "You turn the dial. Reaches 100%" / "Você gira o dial. Chega a 100%"; `mode.auto` "Tuner" / "Sintonizador"; `mode.auto.tooltip` "Follows the station by itself" / "Segue a estação sozinho"; `mode.auto.ceiling` "%s ceiling: %s%%" / "Teto do %s: %s%%"; `mode.auto.missing` "Put a Tuner in the structure" / "Coloque um Sintonizador na estrutura"; `crystals.one` "Crystals · 1 chamber" / "Cristais · 1 câmara"; `crystals.many` "Crystals · %s chambers" / "Cristais · %s câmaras"; `chamber.empty` "Empty chamber" / "Câmara vazia"; `chamber.accepts` "Takes a Signal Crystal or a Cut one" / "Aceita Cristal de Sinal ou Lapidado"; `chamber.charge` "Charge: %s%%" / "Carga: %s%%"; `chamber.yield` "Yields %s× · generating %s FE/t" / "Rende %s× · gerando %s FE/t"; `chamber.recharging` "Recharging: %s%%" / "Recarregando: %s%%"; `chamber.with_redstone` "with the Core's redstone" / "com a redstone do Núcleo"; `chamber.no_redstone` "No redstone in the Core" / "Sem redstone no Núcleo"; `redstone` "Redstone"; `redstone.hint` "recharges the Dull one" / "recarrega o Opaco"; `redstone.tooltip` "Redstone Dust" / "Pó de Redstone"; `redstone.tooltip.time` "Recharges a Dull crystal in %s s" / "Recarrega um Opaco em %s s"; `efficiency` "Efficiency" / "Eficiência"; `efficiency.tooltip` "Efficiency by tuning" / "Eficiência pela sintonia"; `efficiency.tooltip.ceiling` "The mark is the Tuner's ceiling" / "A marca é o teto do Sintonizador"; `generation` "Generation" / "Geração"; `generation.value` "%s FE/t"; `generation.tooltip` "%s FE/t"; `generation.tooltip.max` "of %s FE/t with these crystals at 100%%" / "de %s FE/t com estes cristais a 100%%"; `energy` "Energy" / "Energia"; `energy.value` "%s of %s FE" / "%s de %s FE"; `energy.tooltip` "%s of %s FE" / "%s de %s FE"; `energy.tooltip.port` "%s%% · leaves through the Energy Port" / "%s%% · sai pela Porta de Energia"; `energy.hint` "buffer: 10 s of full output" / "buffer: 10 s da saída cheia"; `structure` "Structure" / "Estrutura"; `structure.tuner` "Tuner" / "Sintonizador"; `structure.energy_ports` "Energy Ports" / "Portas de Energia"; `structure.crystal_ports` "Crystal Ports" / "Portas de Cristais"; `structure.layout` "Layout" / "Montagem"; `yes` "yes" / "sim"; `no` "no" / "não"; `layout.vitrine` "Showcase" / "Vitrine"; `layout.faixa` "Band" / "Faixa"; `layout.custom` "Custom" / "Própria".

- [ ] **Step 1: JUnit que falha** (`PointerScaleTest`):

```java
package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.resonator.TuneSignal;
import org.junit.jupiter.api.Test;

class PointerScaleTest {
    @Test
    void escalaDeMenosDoisAMaisDoisNaLargura() {
        assertEquals(35.0, PointerScale.pixelsPerMhz(172), 1e-9);
        assertEquals(86, PointerScale.x(172, 0));
        assertEquals(16, PointerScale.x(172, -2));
        assertEquals(156, PointerScale.x(172, 2));
        assertArrayEquals(new int[] {77, 95}, PointerScale.plateau(172));
    }

    @Test
    void ponteiroMostraOndeODialEsta() {
        assertEquals(-1.3, PointerScale.pointerMhz(26), 1e-9);
        assertEquals(0.0, PointerScale.pointerMhz(0), 1e-9);
        assertEquals(2.0, PointerScale.pointerMhz(TuneSignal.NO_SIGNAL), 1e-9);
    }

    @Test
    void tracosACadaMeioEROtulosACadaUm() {
        assertTrue(PointerScale.isTick(-1.5));
        assertFalse(PointerScale.isTick(0.3));
        assertTrue(PointerScale.isMajor(1.0));
        assertFalse(PointerScale.isMajor(0.5));
    }
}
```

- [ ] **Step 2:** `./gradlew test --tests '*PointerScale*'` → falha de compilação.
- [ ] **Step 3:** implemente `PointerScale` e rode até passar.
- [ ] **Step 4:** implemente as partes, a anfitriã, os tokens do `GuiPaint`, o registro no `ClientSetup` e os textos (os dois `lang`, na mesma ordem). Confira que o `runGameTestServer` continua subindo (nada de `client/` no código comum: o bloco só conhece o `ResonatorMenu`).
- [ ] **Step 5:** olhe a tela uma vez de verdade: `./gradlew runClient`, num mundo criativo, monte uma Vitrine (o `/wa resonator` não monta; coloque os blocos ou use `/setblock`; a projeção ajuda), clique no Núcleo e confira contra o mockup: osciloscópio, ponteiro, lâmpada, dial, modo, slots, barras, Estrutura, inventário, nos dois temas, sem texto cortado. (Sem monitor, pule e deixe para o e2e da Task 6.)
- [ ] **Step 6:** verificação completa (as quatro runs e o e2e: ele ainda não abre a tela, mas confere o resto), varredura, `medir.py` (cada classe de `client/resonator/screen/` até 500 linhas) e commit (`"Ressonador: a tela do Núcleo"`).

---

### Task 6: A tela no e2e (claro, escuro e pt_br)

**Files:**
- Modify: `B/client/dev/E2eResonatorSteps.java`, `B/client/dev/E2eDarkSteps.java` (deixe `themeClick` e `handStep` package-private), `B/client/resonator/screen/ResonatorScreen.java` (só se faltar um gancho)

**Interfaces:**
- Consumes: `ResonatorScreen` e os ganchos `dialPlusCenter()`, `modeAutoCenter()` (Task 5); `ResonatorMenu.state()`/`received()` (Task 4); `E2eUi.useOn`, `click(screen, x, y)`, `moveMouse`, `E2eSteps.language`, `close`, `DevEndToEnd.capture`, `clipCheck`, `wait`, `E2eDarkSteps.themeClick(name, GuiTheme)` (existentes); `core.setStationForTest`, `insertCrystal`, `addRedstone` (existentes).
- Produces: as capturas `ressonador-tela`, `ressonador-tela-escuro`, `ressonador-tela-pt` no `run/e2e/` e os passos no `result.txt`.

- [ ] **Step 1: Os passos**, no `resonatorSteps` depois da captura `ressonador-montado` e antes de "Ressonador: limpar" (o jogador continua voando a uns 7 blocos do Núcleo, dentro dos 8 do `stillValid`; se a câmera ficar além de 8, aproxime-a 1 bloco no passo da câmera):
  - "Ressonador: cristais e redstone no Núcleo" (`onServer`: `core.setStationForTest(98.0)`, `core.setDial(98.0)`, `insertCrystal` de um Cristal cheio, um Lapidado com carga `FULL / 2` e um Cristal Opaco (o `looseState` do e2e põe o Núcleo no `defaultBlockState`, Básico, com uma câmara só: mude o caso `CORE` dele para `.setValue(RouterBlock.TIER, RouterTier.ELITE)`, três câmaras), `addRedstone(14)`; espera o cliente ver a câmara 0 pelo `clientView()`).
  - "Ressonador: abrir a tela" (`useOn(core)` com a mão vazia; espera `screen instanceof ResonatorScreen` com `getMenu().received()`).
  - "Ressonador: +0,05 pelo botão" (`click(screen, dialPlusCenter)`; espera `state().dial() == 98.05`).
  - "Ressonador: ligar o Sintonizador" (`click(screen, modeAutoCenter)`; espera `state().auto()`).
  - `capture("ressonador-tela")` precedida de `moveMouse` sobre o slot do Opaco (a dica da recarga aparece na foto) e seguida de `clipCheck("ressonador-tela")`.
  - `themeClick("Ressonador: tema escuro", GuiTheme.DARK)`, `moveMouse(0, 0)`, `wait` 6 ticks, `capture("ressonador-tela-escuro")`, `clipCheck`, `themeClick("Ressonador: tema claro", GuiTheme.LIGHT)`.
  - `close("Ressonador: fechar a tela")`, `language("pt_br")`, "Ressonador: abrir em pt_br" (`useOn` de novo), `capture("ressonador-tela-pt")`, `clipCheck`, `close`, `language("en_us")`.
- [ ] **Step 2:** rode `./gradlew compileJava` e depois `WA_E2E="$PWD/run/e2e" ./gradlew runClient` (alguns minutos); confira `run/e2e/result.txt` = `OK` e **abra as três capturas**: a tela inteira como o mockup (osciloscópio limpo com ESTAÇÃO, ponteiro no centro, lâmpada acesa, o dial em 98.05 e o Sintonizador escolhido com os botões apagados, três slots com os dois cristais e o Opaco cinza com a barra de latão, 14 de redstone, 90% com a marca do teto, geração e energia, Estrutura com Sintonizador sim, 1 e 1, Vitrine), o escuro com as mesmas coisas e o pt_br sem texto cortado. Descreva no relatório o que viu; se algo divergir do mockup, conserte na tela e rode de novo.
- [ ] **Step 3:** verificação completa, varredura, `medir.py` e commit (`"Ressonador: a tela do Núcleo no e2e, nos dois temas e em pt_br"`).

---

### Task 7: As texturas da câmara

**Files:**
- Modify: `scripts/textures/blocos.py` (`sprites_ressonador`)
- Gerados: `R/assets/wirelessautomate/textures/block/resonator_chladni.png` (+ `.mcmeta`), `resonator_chladni_noise.png` (+ `.mcmeta`), `resonator_halo.png`, `docs/preview/folha-de-sprites.png`

**Interfaces:**
- Consumes: `animacao(quadros, frametime)`, `parado(img)`, `nova()`, `put`, `cor` (existentes em `identidade.py`/`blocos.py`).
- Produces: três sprites (o Chladni de 32 px, os outros de 16 px), todos `translucido=True` (alfa parcial é o que eles são):
  - `res_chladni()`: 16 quadros 32 × 32 (a única textura de 32 px; libere o tamanho só para ela no `validar`/`salvar`); por pixel, `x = (i + 0,5) / 32 × 2 − 1`, `y` igual; `p = cos(3πx)cos(2πy) − cos(2πx)cos(3πy)`; `v = max(0, 1 − |p| × 5)`; no quadro `k`, `glow = v² × (0,85 + 0,15 × sin(2πk / 16))`; cor `(255 × (0,3 + 0,7 glow), 230 × (0,45 + 0,55 glow), 200 × (0,55 + 0,45 glow))` com alfa `255 × glow`; `animacao(quadros, 3)`.
  - `res_chladni_noise()`: 8 quadros; `random.Random(7 + k)`; por pixel `n` uniforme; `glow = max(0, n − 0,55) / 0,45`; a mesma rampa de cor com alfa `255 × glow × 0,8`; `animacao(quadros, 2)`.
  - `res_halo()`: 1 quadro; `d = hypot(x − 7,5; y − 7,5) / 8`; alfa `255 × max(0, 1 − d)²`, cor da paleta `sinal` (`#FFF0EA` no centro misturado a `#F2917A` na borda); `parado`.
  - Nomes: `block/resonator_chladni`, `block/resonator_chladni_noise`, `block/resonator_halo`. Por estarem em `textures/block/`, entram no atlas dos blocos sozinhos e o `.mcmeta` anima no atlas.
- [ ] **Step 1:** escreva as três funções e registre no `sprites_ressonador`.
- [ ] **Step 2:** rode `python -I -B scripts/textures/gerar_texturas.py`; restaure o que não é desta tarefa (`git checkout -- src/main/resources/assets/wirelessautomate/textures docs/preview/icones-preview.png` e depois `git checkout` só dos três PNGs novos, do `.mcmeta` deles e da folha); abra os três PNGs (Read mostra imagens) e descreva: a figura (3, 2) com as linhas nodais escuras e as regiões claras, o ruído sem padrão, o halo radial.
- [ ] **Step 3:** `./gradlew build runGameTestServer` (JSON e PNG válidos), varredura e commit (`"Ressonador: texturas da placa de Chladni e do halo"`).

---

### Task 8: A câmara, parte 1 (o renderer, a cena, a figura de Lissajous e a lente)

**Files:**
- Create: `B/client/resonator/render/ResonatorRenderer.java`, `ChamberScene.java`, `LissajousCurve.java`, `LissajousTrace.java`, `ChamberLens.java`
- Create: `T/client/resonator/render/LissajousCurveTest.java`
- Modify: `B/client/ClientSetup.java`

**Interfaces:**
- Consumes: `ResonanceCoreBlockEntity.clientView()`, `tunedAt()`, `ChamberView` (Task 3); `TuneSignal.BANDS` (Task 1); `ResonatorPartBlock.FACING`, `FORMED`, `Kind` (existentes); a textura `block/resonator_halo` (Task 7); `GuiPaint.tierColor` (existente, só a cor: um `int` ARGB).
- Produces:
  - `LissajousCurve` (pura): `POINTS = 260`; `TAIL_RADIANS = 2π`; `TAIL_SECONDS = 3.0`; `double[] point(double u, double q, double t)` → `{x, y, z}` com `dr = 1 − q`, `w1 = 3 + 0,61 dr sin(0,37 t)`, `w2 = 2 + 0,43 dr cos(0,29 t)`, `w3 = 1 + 0,37 dr sin(0,23 t)`, `x = 0,78 sin(w1 u + π/2)`, `y = 0,55 sin(w2 u)`, `z = 0,78 sin(w3 u)`; `void fill(double[] xyz, double head, double q, double time)` (260 × 3 valores: o ponto `i` em `u = head − (1 − i/259) × TAIL_RADIANS` e `t = time − (1 − i/259) × TAIL_SECONDS`); `double advance(double q)` (quanto `head` anda por segundo: `(0,45 + 0,55 q) × 2π / 3`).
  - `ChamberScene` (uma por Núcleo, package-private): `ChamberScene(BlockPos core)`; `void update(ChamberView view, long tunedAt, long gameTime, ClientLevel level)` (chamado no desenho, uma vez por tick novo: guarda a view; `q` aproxima `view.band() / (BANDS − 1)` em `1,6/s` (`q += (alvo − q) × min(1, 1,6 × 0,05)`); `head` anda `advance(q) / 20`; copia os pontos atuais para `previous` e recalcula `current` com `LissajousCurve.fill(current, head, q, gameTime / 20.0)`; lê as Portas da casca (`origin` da view, as 98 posições, `ResonatorPartBlock` com `FORMED` e o `Kind`) quando a `origin` mudou ou passaram 200 ticks: `energyPorts` e `crystalPorts` como `List<BlockPos>`); `double q()`; `double[] current()`, `previous()`; `Vec3 center()` (`origin + 2,5` em cada eixo, relativo ao Núcleo: `center − corePos`); `double[] headPoint(float partialTick)`; `double sinceFormed(long gameTime, float partialTick)` (segundos desde `formed_at`, `1e9` sem montagem); `double sinceTuned(...)`; `static double on(double since, double delay)` (`smoothstep((since − delay) / 0,9)`); `double breath(long gameTime, float partialTick)` (`0,5 + 0,5 sin(2π t / 2,4)`); `long lastUsed`.
  - `LissajousTrace.render(PoseStack, MultiBufferSource, ChamberScene, float partialTick, int tierColor)`: para cada segmento `i → i + 1` dos 260 pontos interpolados (`previous + (current − previous) × partialTick`), 2 quads cruzados de meia largura `0,012` (um no plano vertical do segmento, outro perpendicular), `RenderType.lightning()`, cor coral clara `0xFFB39E` misturada com a cor do tier em `0,35 q`, alfa `on(sinceFormed, 0,9) × (0,12 + 0,7 q) × min(1, 4 q + 0,05)`, mais forte na cabeça (× `i / 259`); a cabeça: o quad do halo virado para a câmera (`Minecraft.getInstance().gameRenderer.getMainCamera().rotation()`, escala 0,55 blocos, textura `block/resonator_halo` pelo `TextureAtlasSprite` do atlas `InventoryMenu.BLOCK_ATLAS`, `RenderType.eyes(InventoryMenu.BLOCK_ATLAS)`, alfa `on(0,9) × (0,35 + 0,55 q)`) e o miolo (icosaedro de raio 0,05, `lightning`, cor `0xFFFBF3` misturada a `0xFFB39E` em `1 − q`, só com `q > 0,03`).
  - `ChamberLens.render(...)`: 1 quad aditivo (`lightning`) de 0,6 × 0,6 colado na face de dentro do Núcleo (a face oposta ao `FACING` do estado, 0,01 para dentro da câmara), na cor do tier, alfa `on(0) × (0,3 + 0,4 q + 0,12 breath)`; o clarão: o quad do halo no centro, escala `1 + 2,2 t` com `t = sinceTuned` em segundos, alfa `max(0, 1 − t / 0,9) × 0,9`, só com `t < 0,9`.
  - `ResonatorRenderer implements BlockEntityRenderer<ResonanceCoreBlockEntity>`: construtor `(BlockEntityRendererProvider.Context)`; `render(core, partialTick, poseStack, buffer, light, overlay)`: nada se `!core.getBlockState().getValue(ResonatorPartBlock.FORMED)` ou `core.clientView().chambers().length == 0`; acha ou cria a `ChamberScene` do `core.getBlockPos()` num `Map<BlockPos, ChamberScene>` estático (entradas com `lastUsed` a mais de 200 ticks saem numa varredura a cada 200 ticks); `scene.update(...)` se `gameTime` mudou; `poseStack.pushPose()`, translada ao `scene.center()`, chama `LissajousTrace.render`, `ChamberLens.render` (e na Task 9 `ChladniPlate`, `ChamberCrystals`, `ChamberParticles`), `popPose()`; `getViewDistance()` → 64; `getRenderBoundingBox(core)` → `new AABB(origin, origin + 5)` pela `origin` da view (sem view, o bloco); `shouldRenderOffScreen` falso.
  - `ClientSetup`: `@SubscribeEvent static void registerRenderers(EntityRenderersEvent.RegisterRenderers event)` com `event.registerBlockEntityRenderer(ModBlockEntities.RESONANCE_CORE.get(), ResonatorRenderer::new)`.
- Os vértices do `lightning` são `POSITION_COLOR` (`addVertex(pose, x, y, z).setColor(r, g, b, a)`); os do `eyes` são `NEW_ENTITY` (`addVertex(...).setColor(...).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0)`). Os dois são aditivos, sem gravar profundidade: a ordem entre as partes não importa, mas desenhe tudo da câmara depois do `pushPose`.

- [ ] **Step 1: JUnit que falha** (`LissajousCurveTest`):

```java
package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LissajousCurveTest {
    @Test
    void emSintoniaAFiguraFecha() {
        for (double u = 0; u < 7; u += 0.37) {
            assertArrayEquals(LissajousCurve.point(u, 1.0, 12.0), LissajousCurve.point(u + 2 * Math.PI, 1.0, 12.0), 1e-9);
        }
    }

    @Test
    void foraDaSintoniaAFiguraEscorrega() {
        double[] a = LissajousCurve.point(1.0, 0.0, 12.0);
        double[] b = LissajousCurve.point(1.0 + 2 * Math.PI, 0.0, 12.0);
        assertNotEquals(a[0], b[0], 1e-6);
    }

    @Test
    void ficaDentroDaCamara() {
        double[] xyz = new double[LissajousCurve.POINTS * 3];
        LissajousCurve.fill(xyz, 5.0, 0.4, 20.0);
        for (int i = 0; i < LissajousCurve.POINTS; i++) {
            assertTrue(Math.abs(xyz[i * 3]) <= 0.78 + 1e-9 && Math.abs(xyz[i * 3 + 1]) <= 0.55 + 1e-9
                    && Math.abs(xyz[i * 3 + 2]) <= 0.78 + 1e-9);
        }
        assertEquals(LissajousCurve.point(5.0, 0.4, 20.0)[0], xyz[(LissajousCurve.POINTS - 1) * 3], 1e-9);
    }

    @Test
    void avancaMaisRapidoEmSintonia() {
        assertTrue(LissajousCurve.advance(1.0) > LissajousCurve.advance(0.0));
        assertEquals(2 * Math.PI / 3, LissajousCurve.advance(1.0), 1e-9);
    }
}
```

- [ ] **Step 2:** `./gradlew test --tests '*Lissajous*'` → falha de compilação.
- [ ] **Step 3:** implemente a `LissajousCurve` e rode até passar.
- [ ] **Step 4:** implemente a cena, o traço, a lente, o renderer e o registro. Confira o `runGameTestServer` (o renderer só é referenciado pelo `ClientSetup`).
- [ ] **Step 5:** olhe a câmara uma vez (`./gradlew runClient`, a Vitrine montada com cristais em sintonia): a figura fechando em 3 : 2 : 1 pela janela, a cabeça com o halo, a lente na cor do tier respirando, o clarão ao acertar o dial; `F3` para conferir que o FPS não cai com a estrutura à vista (sem monitor, deixe para o e2e da Task 9 e diga no relatório).
- [ ] **Step 6:** verificação completa (as quatro runs e o e2e), varredura, `medir.py` (até 500 linhas por classe) e commit (`"Ressonador: a câmara, com a figura de Lissajous e a lente do Núcleo"`).

---

### Task 9: A câmara, parte 2 (Chladni, cristais, feixes e partículas) e o e2e

**Files:**
- Create: `B/client/resonator/render/ChladniPlate.java`, `ChamberCrystals.java`, `ChamberParticles.java`
- Modify: `B/client/resonator/render/ResonatorRenderer.java`, `ChamberScene.java`, `B/client/dev/E2eResonatorSteps.java`

**Interfaces:**
- Consumes: `ChamberScene` (`q`, `on`, `breath`, `headPoint`, `energyPorts`, `crystalPorts`, `center`), `ResonatorRenderer` (Task 8); `ChamberView.chambers()`, `rechargeStart()`, `rechargeTicks()` (Task 3); `ModItems.SIGNAL_CRYSTAL`, `CUT_SIGNAL_CRYSTAL`, `SignalCrystalItem.setCharge` (etapa 1); as texturas `block/resonator_chladni` e `block/resonator_chladni_noise` (Task 7).
- Produces:
  - `ChladniPlate.render(PoseStack, MultiBufferSource, ChamberScene, float partialTick)`: dois quads horizontais de 2,9 × 2,9 em `y = −1,48` (o chão da câmara é `center.y − 1,5`), `RenderType.eyes(InventoryMenu.BLOCK_ATLAS)`, UV do sprite (`sprite.getU0()..getU1()`, o atlas anima): a figura com alfa `q × on(0,3)` e o ruído com alfa `(1 − q) × on(0,3)`.
  - `ChamberCrystals.render(...)`: para a câmara `i` de `k = chambers.length`, a pilha fixa do tipo (`1` Cristal, `2` Lapidado, `3`/`4` o mesmo item com `setCharge(stack, 0)`, guardadas num array estático de 5 pilhas, uma por código), em `(cos a × 0,95, bob + rise − 0,15, sin a × 0,95)` com `a = 2π i / k + 0,08 t + π/4`, `bob = sin(2π t / 2,4 + fase) × 0,12 q` (`fase = seed × (1 − q)`, `seed` fixo por câmara), `rise = −0,35 × (1 − smoothstep(recarga))` só no Opaco (`recarga = (gameTime + partialTick − rechargeStart) / rechargeTicks`, 0 sem recarga), girando em `y` (`0,1 rad/s` no Opaco, `0,4 + 1,2 q` nos outros) e tombado em `z` (`0,5 × (1 − smoothstep(recarga))` no Opaco, `sin(3 t + seed) × 0,25 × (1 − q)` nos outros), escala `0,55 × on(0,6)`, pelo `Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, poseStack, buffer, level, 0)`; depois os feixes: por cristal não Opaco, 2 quads cruzados (`lightning`, meia largura 0,009) do cristal à cabeça, alfa `on(1,2) × max(0, q − 0,5) × 2 × (0,5 + 0,5 pulse) × 0,7` com `pulse = 0,5 + 0,5 sin(2π t / 1,6 + (q > 0,9 ? 0 : seed))`. `double[] crystalPos(int i, ...)` fica público no pacote para as partículas.
  - `ChamberParticles` (uma por cena, dentro da `ChamberScene`): `tick(ChamberScene, ChamberView, ClientLevel, long gameTime)` uma vez por tick (chamado pelo `update`), só com `Minecraft.getInstance().player.distanceToSqr(center) < 32²`: acumula `sparkClock += (band == 0 ? 0 : 2 + 3 × band / 7) / 20` e, ao passar de 1, nasce uma faísca (até 8 vivas) do centro (com um desvio aleatório de ±0,04 em `y` e `z`) até a face de dentro da Porta de Energia mais próxima (`port + 0,5 − facing × 0,51`), idade 25 ticks; `redClock += 2 × opacosEmRecarga / 20` e, ao passar de 1, nasce um grão de redstone (até 4 vivos) da face de dentro da Porta de Cristais até a posição do Opaco em recarga, 33 ticks; cada viajante vivo avança `t += 1 / idade` (posição por `smoothstep(t)` entre os dois pontos) e solta uma partícula do vanilla parada ali: `level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, 0, 0)` nas faíscas e `level.addParticle(new DustParticleOptions(new Vector3f(0.84f, 0.27f, 0.23f), 0.6f), x, y, z, 0, 0, 0)` na redstone; sem Portas (lista vazia), nada nasce.
  - `ResonatorRenderer.render` passa a chamar, na ordem: `ChladniPlate`, `ChamberCrystals`, `LissajousTrace`, `ChamberLens` (os cristais são opacos e vão antes dos aditivos).
- e2e (em `E2eResonatorSteps`, depois dos passos da tela da Task 6 e antes de "Ressonador: limpar"): "Ressonador: câmera na janela" (o jogador a 2,5 blocos da face sul da Vitrine, olhando o centro da janela: `origin + (2,5; 2,3; 7,5)`, yaw 180°, pitch leve para baixo; ajuste com a mesma conta do `camera`), `wait` 30 ticks (a sequência de acender e o `q` a chegar a 1), `capture("ressonador-camara")`; depois `onServer`: `core.setDial(96.7)` (72%, QUASE LÁ) e `wait` 60 ticks (o update tag chega e o `q` desliza), `capture("ressonador-camara-chiado")`; volte o dial a 98,0.
- [ ] **Step 1:** implemente as três partes e a ordem no renderer; confira que a `ChamberScene` não passa de 500 linhas (se passar, tire as Portas e o cache delas para uma `ChamberPorts`).
- [ ] **Step 2:** os passos do e2e; `./gradlew compileJava`; `WA_E2E="$PWD/run/e2e" ./gradlew runClient`; `result.txt` = `OK`; **abra as duas capturas**: na primeira, pela janela, a figura fechada com os cristais no anel (o Opaco cinza caído), os feixes, a placa com a figura (3, 2) no chão, a lente acesa e, se houver uma faísca no quadro, a trilha até a Porta de Energia; na segunda, o rabisco, os cristais fora de ritmo e a placa com ruído. Descreva no relatório o que viu.
- [ ] **Step 3:** verificação completa, varredura, `medir.py` e commit (`"Ressonador: a câmara com a placa de Chladni, os cristais, os feixes e as partículas"`).

---

### Task 10: Documentação

**Files:**
- Modify: `CLAUDE.md`, `docs/progresso.md`, `docs/especificacao.md`

- [ ] **Step 1:** `CLAUDE.md`: na linha do `resonator/`, a etapa 3 (o modo automático no `Tuning` e no `ResonatorBatch`, `TuneSignal`, `ChamberView` e o update tag só quando muda, `CoreInventory`, `ResonatorBlueprint.layoutOf`, a barra do cristal, o clique no montado abrindo a tela); uma linha nova `menu/ResonatorMenu.java` (slots, `pollState` a cada 5 ticks só se mudou, `stillValid`); os payloads novos na linha do `packet/` (protocolo `17`, `ResonatorStatePayload`, `ResonatorDialPayload`, `ResonatorModePayload`); uma linha `client/resonator/screen/` (a anfitriã e cada parte, com o papel de cada uma) e uma `client/resonator/render/` (o renderer, a cena e cada parte, os limites de custo); `TuneSignal`, `PointerScale` e `LissajousCurve` na lista de lógica pura com JUnit; `ResonatorTunerGameTests`, `ResonatorSyncGameTests` e `ResonatorMenuGameTests` com as contagens reais na linha do `gametest/`; os sprites novos na linha do `blocos.py`; as capturas novas na linha do `DevEndToEnd`; `ModPayloads` "protocolo `17`".
- [ ] **Step 2:** `docs/progresso.md`: a linha do Ressonador (etapa 3 pronta: tela, Sintonizador automático e câmara; sem comparador e sem redstone por decisão do dono; falta a etapa 4: Tablet, JEI, guia e vitrine), o "Próximo passo" (0c vira a etapa 4, com o que ela tem) e uma linha no histórico com os números reais de JUnit e GameTests. Continua valendo: não publicar versão com o Ressonador antes do guia dizer como operá-lo (etapa 4); diga isso.
- [ ] **Step 3:** `docs/especificacao.md`: no item do roadmap do Ressonador, as etapas 1 a 3 prontas e o que falta na 4; tire "comparador" e "redstone" do texto.
- [ ] **Step 4:** o guia (`scripts/guide/gerar_guia.py`) ainda não tem página do Ressonador (confira com `grep -n "Ressonador\|Resonator" scripts/guide/gerar_guia.py`): a página é da etapa 4, nada a gerar agora. Se o grep achar alguma menção à action bar do Núcleo montado ou ao comparador, corrija ali e rode `python -I scripts/guide/gerar_guia.py`.
- [ ] **Step 5:** verificação completa e commit (`"Ressonador: documentação da etapa 3"`).
