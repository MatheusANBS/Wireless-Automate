# Tela de Sinal, etapa 1 (bloco e motor) · plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** a Tela de Sinal jogável: o bloco preso na parede, a aba Tamanho que expande a tela pela parede (1×1 a 32×32), o canal que leva os números das redes ao cliente só quando alguém está perto e só quando mudam, o desenho por textura com o texto que diminui para caber, e a aba Conteúdo com o modelo pronto "Painel de rede" (escolher a rede e o tipo). O editor de peças é a etapa 2.

**Arquitetura:** pacote novo `tv/` (comum) e `client/tv/` (cliente). A geometria da área (`TvArea`) é pura, com JUnit. O layout é uma lista de peças imutáveis (`TvPiece`) com codec de NBT, guardada no block entity e mandada pelo update tag só quando muda. O `TvBlockEntity` não faz tick: o `TvManager` (um por servidor, padrão do `RepairerManager`) conhece as Telas carregadas, visita cada uma uma vez por segundo pela fase da posição e, se há jogador perto e o amostrador dos Fluxos fechou uma volta nova, relê os números (um mapa rede × tipo por volta, para todas) e manda o `TvValuesPayload` só a quem não tem os números atuais. No cliente, cada Tela é desenhada numa textura (`TextureTarget`) só quando um número ou o layout muda, no `RenderFrameEvent.Pre`, e o BER só desenha a moldura e um retângulo com a textura.

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), JUnit 5, GameTests, Python 3 (texturas e guia), e2e do cliente.

**Spec:** [`docs/superpowers/specs/2026-10-27-tela-de-sinal-design.md`](../specs/2026-10-27-tela-de-sinal-design.md). **Mockup aprovado:** `docs/preview/tela-de-sinal.html` (a etapa 1 usa as seções "No mundo", "Escolher o tamanho" e o fundo Grafite; o editor do mockup é a etapa 2).

## Decisões deste plano (o dono pode trocar antes da tarefa em que entram)

1. **Receita (Tarefa 3, aprovada pelo dono em 27/10):** `KGK / GCG / KRK`, K = `wirelessautomate:resonator_casing`, G = `c:glass_panes`, C = `wirelessautomate:phase_circuit`, R = `c:dusts/redstone`; resultado 1.
2. **Resolução de desenho fixa de 32 px por bloco** para as coordenadas do layout (`TvArea.PIXELS_PER_BLOCK`). O `tv.pixelsPerBlock` do cliente só muda a nitidez da textura (o layout não muda).
3. **Linhas da área contadas de baixo para cima e colunas da esquerda para a direita, vistas de frente.** A direita de quem olha a tela é `facing.getCounterClockWise()` (tela virada para o norte: a direita é o oeste).
4. **A aba Conteúdo é provisória:** escolhe uma rede e um tipo e aplica o modelo "Painel de rede" (Forma de fundo, Texto com o nome da rede, Valores de entra, sai e movendo). Ela some na etapa 2, quando o editor chega.
5. **Textos do modelo sem idioma:** o modelo usa só símbolos (`▲`, `▼`, `⇄`) e o nome da rede, porque o layout é guardado no servidor e vale para todo mundo. A unidade (FE/t, mB/s, /s) é posta pelo cliente, no idioma dele.
6. **Acesso na hora de mostrar:** `WaNetwork.canUse(dono, false)`; sem acesso, "—" (spec, decisão 7).
7. **Tela 320 × 220 fixa**, com as abas Tamanho e Conteúdo (cabe na escala 3 de 1366 × 768).

## Restrições globais

- Código, comentários, commits e docs em português; textos nos dois `lang` (`en_us.json`, `pt_br.json`), sempre os dois, na mesma ordem.
- **Chaves existentes não mudam.** Novas (spec, "Chaves novas"): blockstate `facing`; NBT `owner`, `width`, `height`, `anchor_col`, `anchor_row`, `layout`, `revision`; config do servidor seção `tv` (`maxWidth`, `maxHeight` 1 a 64 padrão 32; `maxElements` padrão 128; `maxBindings` padrão 64; `viewDistance` 8 a 256 padrão 64); config do cliente seção `tv` (`pixelsPerBlock`: 16, 32 ou 64, padrão 32). **`ModPayloads.VERSION` sobe de `"34"` para `"35"`** (só na Tarefa 5, que cria o primeiro payload).
- **Performance (skill `wa-performance`; leia `.claude/skills/wa-performance/SKILL.md`):** parado custa zero (sem Tela carregada, `TvManager.tick` é `isEmpty()`; Tela sem peça ligada ou sem jogador perto não lê nada nem manda nada); nada de tick por bloco (a Tela não implementa ticker); trabalho espalhado em 20 fases pela posição; o mapa rede × tipo é montado uma vez por volta do amostrador, não por Tela; pacote só quando os números mudam e só para quem está perto e não os tem; layout pelo update tag só quando muda (`sendBlockUpdated` com `UPDATE_CLIENTS`); `setChanged()` só em mudança de tamanho, layout ou dono; no cliente, nada por quadro além do BER desenhar dois quadriláteros por Tela; o redesenho da textura acontece só com a Tela suja, no máximo 4 por quadro; medida de texto só no redesenho; textura liberada depois de 200 ticks sem desenho. Rode `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos mudados e resolva ou justifique cada candidato no relatório.
- Lógica pura com JUnit (`src/test/java`); o que depende do jogo, com GameTest (`@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`, `template = "empty"` ou `"empty7"`, `timeoutTicks` ≤ 300; jogador só por `TestPlayers.inLevel`; cada teste cria a própria rede com `NetworkSavedData.get(server).create(...)`; compare redes com `Objects.equals`; teste que muda a config vai em `batch = "wirelessautomate_config_tv"` com `try/finally`; o `onLoad` só roda no tick seguinte: `startSequence().thenWaitUntil(...)`; testes do mesmo lote rodam em paralelo, então teste pertinência e não contagem global).
- Mod opcional: nada de tipos do Mekanism, Ars, FTB Teams ou Allthemodium em `tv/` e `client/tv/`; os tipos de recurso vêm do `ResourceType` e do `LoadedTypes`.
- Lado do cliente: tudo de tela e render só em `client/` (`client/tv/`), nunca referenciado por código comum. O block entity marca a Tela suja no cliente por um gancho estático comum (`TvClientHooks`, Tarefa 3) que o `client/` preenche no `ClientSetup`, como os outros ganchos do mod.
- Todo texto variável da tela do bloco passa por `GuiText` (o e2e confere `GuiText.clipCount() == 0`); cores pelo `GuiPaint`, nos dois temas; o botão `ThemeToggle` no cabeçalho. O texto **desenhado na Tela no mundo** não passa pelo `GuiText`: ele encolhe pelo `TvFit` (Tarefa 1) e nunca é cortado.
- Tamanho (`wa-modular`): classe de tela até 500 linhas, servidor até 600; rode `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados. Comentários (`wa-comentarios`): `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados.
- Texturas e guia: edite `scripts/textures/*.py` e `scripts/guide/gerar_guia.py`, nunca os PNGs ou `.md` gerados; o `gerar_texturas.py` reescreve dezenas de PNGs: commite só o que a tarefa pede.
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams` (Git Bash, na raiz); tarefa só de lógica pura roda `./gradlew test --tests "..."`. Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, pare e conserte, nunca espere um resultado que não vem. Tarefas de tela e render rodam também o e2e (`./scripts/e2e.sh --only tela --capture tela`; `run/e2e/result.txt` precisa dizer `OK`) e olham as capturas. Nunca dois jogos ao mesmo tempo (~3 GB cada); não mexa em `run/options.txt`.
- Commits sem linhas de atribuição (nada de `Co-Authored-By` nem `Claude-Session`), com o autor do dono; `git add` só dos arquivos da tarefa (nunca `run/`, nunca `.superpowers/`). Não faça push.

## Mapa de arquivos

Pacote base: `src/main/java/io/github/matheusanbs/wirelessautomate/`.

| Arquivo | Papel | Tarefa |
| --- | --- | --- |
| `tv/TvArea.java` | Puro: largura, altura, a célula do bloco colocado, o "cresce para", deslocamento de cada célula, pixels | 1 |
| `client/tv/TvNumbers.java`, `client/tv/TvFit.java` | Puros: o número formatado (abreviar, casas, sinal, separadores) e o tamanho da fonte que cabe | 1 |
| `tv/TvPieceType.java`, `TvMetric.java`, `TvUnit.java`, `TvAlign.java`, `TvBinding.java`, `TvFormat.java`, `TvStyle.java`, `TvPiece.java`, `TvLayout.java` | O modelo do layout e os codecs | 2 |
| `docs/preview/tela-de-sinal.bbmodel`, `scripts/textures/bbmodel_json.py`, `scripts/textures/tela_modelo.py` | O modelo do Blockbench, o conversor comum e o da Tela | 3a |
| `tv/TvBlock.java`, `tv/TvBlockEntity.java`, `tv/TvClientHooks.java` | O bloco na parede, os dados e o update tag | 3 |
| `registry/ModBlocks.java`, `ModItems.java`, `ModBlockEntities.java`, `ModCreativeTabs.java` (mudar) | Registro | 3 |
| `Config.java` (mudar), `ClientConfig.java` (mudar) | Seções `tv` | 3 |
| recursos: blockstate, modelos, textura, loot table, receita, `lang` | O bloco e o item | 3 |
| `tv/TvAreaCheck.java`, `tv/TvLoaded.java` | A validação da área no mundo e as Telas carregadas por lado | 4 |
| `tv/TvManager.java`, `tv/TvFeed.java`, `packet/TvValuesPayload.java` | O canal de dados | 5 |
| `WirelessAutomate.java`, `packet/ModPayloads.java`, `command/WaCommand.java` (mudar) | Tick, parada, registro, `/wa profile` | 5 |
| `menu/TvMenu.java`, `tv/TvEditing.java`, `tv/TvTemplates.java`, `packet/TvSizePayload.java`, `packet/TvTemplatePayload.java`, `registry/ModMenus.java` (mudar) | Abrir, aplicar o tamanho e o modelo | 6 |
| `client/tv/TvClientValues.java`, `client/tv/TvUnits.java`, `client/tv/render/TvCanvas.java`, `TvCanvases.java`, `TvPainter.java`, `TvRenderer.java`, `client/ClientSetup.java` (mudar) | O desenho no mundo | 7 |
| `client/tv/TvScreen.java`, `TvScreenLayout.java`, `TvSizeTab.java`, `TvContentTab.java`, `TvScreenText.java`, `TvScreenActions.java` | A tela do bloco | 8 |
| `client/dev/E2eTvSteps.java`, `client/dev/E2eSuites.java` (mudar), `docs/e2e.md` (mudar) | e2e `tela` e `tela-desempenho` | 8 |
| `scripts/guide/gerar_guia.py` + páginas geradas, `CLAUDE.md`, `docs/especificacao.md`, `docs/progresso.md` | Guia e docs | 9 |
| `client/HeldScrollHandler.java` (era `ConfiguratorScrollHandler`), `packet/CycleLinkerModePayload.java`, `item/LinkerItem.java` | Shift + roda troca o modo do Vinculador | 10 |

---

### Task 1: Lógica pura (área, número e fonte que cabe)

**Files:**
- Create: `src/main/java/io/github/matheusanbs/wirelessautomate/tv/TvArea.java`
- Create: `src/main/java/io/github/matheusanbs/wirelessautomate/client/tv/TvNumbers.java`
- Create: `src/main/java/io/github/matheusanbs/wirelessautomate/client/tv/TvFit.java`
- Test: `src/test/java/io/github/matheusanbs/wirelessautomate/tv/TvAreaTest.java`
- Test: `src/test/java/io/github/matheusanbs/wirelessautomate/client/tv/TvNumbersTest.java`
- Test: `src/test/java/io/github/matheusanbs/wirelessautomate/client/tv/TvFitTest.java`

**Interfaces:**
- Produces: `TvArea(int width, int height, int anchorCol, int anchorRow)` com `SINGLE`, `PIXELS_PER_BLOCK = 32`, `enum Grow {UP_RIGHT, UP_LEFT, CENTER}`, `static TvArea grown(int w, int h, Grow g)`, `@Nullable Grow grow()`, `int cells()`, `int rightOf(int cell)`, `int upOf(int cell)`, `int pixelWidth()`, `int pixelHeight()`, `static boolean fits(int w, int h, int maxW, int maxH)`. `TvNumbers.format(double value, int decimals, boolean abbreviate, boolean sign, char decimalSep, char groupSep)`. `TvFit.size(float maxSize, float boxWidth, float boxHeight, float widthAtSizeOne, boolean fit)`.

**Custo:** nenhum em caminho quente; `TvNumbers` e `TvFit` rodam só no redesenho da textura (Tarefa 7), com um `StringBuilder` por número.

- [ ] **Step 1: Os testes da área**

```java
package io.github.matheusanbs.wirelessautomate.tv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TvAreaTest {
    @Test
    void cresceParaCimaEDireitaDeixaOBlocoNoCantoDeBaixoEsquerdo() {
        TvArea area = TvArea.grown(6, 3, TvArea.Grow.UP_RIGHT);
        assertEquals(new TvArea(6, 3, 0, 0), area);
        assertEquals(0, area.rightOf(0));
        assertEquals(0, area.upOf(0));
        assertEquals(5, area.rightOf(17));
        assertEquals(2, area.upOf(17));
    }

    @Test
    void cresceParaCimaEEsquerdaDeixaOBlocoNoCantoDeBaixoDireito() {
        TvArea area = TvArea.grown(6, 3, TvArea.Grow.UP_LEFT);
        assertEquals(new TvArea(6, 3, 5, 0), area);
        assertEquals(-5, area.rightOf(0));
        assertEquals(0, area.rightOf(5));
    }

    @Test
    void centroArredondaParaBaixoEEsquerda() {
        assertEquals(new TvArea(6, 3, 2, 1), TvArea.grown(6, 3, TvArea.Grow.CENTER));
        assertEquals(new TvArea(5, 5, 2, 2), TvArea.grown(5, 5, TvArea.Grow.CENTER));
        assertEquals(new TvArea(1, 1, 0, 0), TvArea.grown(1, 1, TvArea.Grow.CENTER));
    }

    @Test
    void growReconheceOsTresPresetsENullNoResto() {
        assertEquals(TvArea.Grow.UP_RIGHT, new TvArea(4, 2, 0, 0).grow());
        assertEquals(TvArea.Grow.UP_LEFT, new TvArea(4, 2, 3, 0).grow());
        assertEquals(TvArea.Grow.CENTER, new TvArea(4, 3, 1, 1).grow());
        assertNull(new TvArea(4, 3, 3, 2).grow());
        assertEquals(TvArea.Grow.UP_RIGHT, TvArea.SINGLE.grow());
    }

    @Test
    void pixelsECelulas() {
        TvArea area = new TvArea(6, 3, 0, 0);
        assertEquals(18, area.cells());
        assertEquals(192, area.pixelWidth());
        assertEquals(96, area.pixelHeight());
    }

    @Test
    void recusaAreaInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new TvArea(0, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TvArea(2, 2, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new TvArea(2, 2, 0, -1));
    }

    @Test
    void fitsRespeitaOTeto() {
        assertTrue(TvArea.fits(32, 32, 32, 32));
        assertFalse(TvArea.fits(33, 1, 32, 32));
        assertFalse(TvArea.fits(0, 1, 32, 32));
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests "*TvAreaTest"`
Expected: FAIL na compilação (`TvArea` não existe).

- [ ] **Step 3: O `TvArea`**

```java
package io.github.matheusanbs.wirelessautomate.tv;

import org.jetbrains.annotations.Nullable;

/**
 * A área de uma Tela de Sinal na parede, em células de um bloco, vista de frente: colunas da esquerda para a
 * direita e linhas de baixo para cima. O bloco colocado é a célula ({@code anchorCol}, {@code anchorRow}). As
 * coordenadas do layout são em pixels de desenho, {@link #PIXELS_PER_BLOCK} por bloco, com a origem no canto de
 * cima à esquerda. Lógica pura, sem classes do Minecraft, com JUnit.
 */
public record TvArea(int width, int height, int anchorCol, int anchorRow) {
    public static final TvArea SINGLE = new TvArea(1, 1, 0, 0);
    public static final int PIXELS_PER_BLOCK = 32;

    /** Para onde a tela cresce a partir do bloco colocado. */
    public enum Grow { UP_RIGHT, UP_LEFT, CENTER }

    public TvArea {
        if (width < 1 || height < 1 || anchorCol < 0 || anchorCol >= width || anchorRow < 0 || anchorRow >= height) {
            throw new IllegalArgumentException("área inválida: " + width + "x" + height + " em " + anchorCol + "," + anchorRow);
        }
    }

    public static TvArea grown(int width, int height, Grow grow) {
        return switch (grow) {
            case UP_RIGHT -> new TvArea(width, height, 0, 0);
            case UP_LEFT -> new TvArea(width, height, width - 1, 0);
            case CENTER -> new TvArea(width, height, (width - 1) / 2, (height - 1) / 2);
        };
    }

    /** O preset que dá esta área, ou {@code null} se o bloco está noutra célula. */
    public @Nullable Grow grow() {
        for (Grow grow : Grow.values()) {
            if (grown(width, height, grow).equals(this)) {
                return grow;
            }
        }
        return null;
    }

    public static boolean fits(int width, int height, int maxWidth, int maxHeight) {
        return width >= 1 && height >= 1 && width <= maxWidth && height <= maxHeight;
    }

    public int cells() {
        return width * height;
    }

    /** Quantos blocos para a direita (negativo: esquerda) a célula {@code cell} fica do bloco colocado. */
    public int rightOf(int cell) {
        return cell % width - anchorCol;
    }

    /** Quantos blocos para cima (negativo: baixo) a célula {@code cell} fica do bloco colocado. */
    public int upOf(int cell) {
        return cell / width - anchorRow;
    }

    public int pixelWidth() {
        return width * PIXELS_PER_BLOCK;
    }

    public int pixelHeight() {
        return height * PIXELS_PER_BLOCK;
    }
}
```

- [ ] **Step 4: Os testes do número e da fonte**

```java
package io.github.matheusanbs.wirelessautomate.client.tv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TvNumbersTest {
    private static String pt(double v, int dec, boolean abbr, boolean sign) {
        return TvNumbers.format(v, dec, abbr, sign, ',', '.');
    }

    @Test
    void abreviaComSufixo() {
        assertEquals("1,84M", pt(1_840_000, 2, true, false));
        assertEquals("12,5k", pt(12_480, 1, true, false));
        assertEquals("268,4M", pt(268_435_456, 1, true, false));
    }

    @Test
    void arredondamentoQueChegaAMilSobeOSufixo() {
        assertEquals("1,0M", pt(999_960, 1, true, false));
    }

    @Test
    void semAbreviarAgrupaOsMilhares() {
        assertEquals("12.480", pt(12_480, 1, false, false));
        assertEquals("1.840.000", pt(1_840_000, 2, false, false));
    }

    @Test
    void inteiroPequenoSemCasasEFracionarioComCasas() {
        assertEquals("2", pt(2.0, 2, false, false));
        assertEquals("0,5", pt(0.5, 1, false, false));
        assertEquals("13", pt(12.6, 0, false, false));
        assertEquals("999", pt(999, 1, true, false));
    }

    @Test
    void sinal() {
        assertEquals("+64", pt(64, 0, false, true));
        assertEquals("-8", pt(-8, 0, false, true));
        assertEquals("0", pt(0, 0, false, true));
        assertEquals("-1,5k", pt(-1_500, 1, true, false));
    }

    @Test
    void semDadoViraTraco() {
        assertEquals("—", pt(Double.NaN, 1, true, false));
    }

    @Test
    void separadoresDoIngles() {
        assertEquals("1.84M", TvNumbers.format(1_840_000, 2, true, false, '.', ','));
        assertEquals("12,480", TvNumbers.format(12_480, 0, false, false, '.', ','));
    }
}
```

```java
package io.github.matheusanbs.wirelessautomate.client.tv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TvFitTest {
    @Test
    void cabeNoMaximo() {
        assertEquals(8f, TvFit.size(8, 100, 20, 5, true));
    }

    @Test
    void encolhePelaLargura() {
        assertEquals(4f, TvFit.size(8, 40, 20, 10, true));
    }

    @Test
    void encolhePelaAltura() {
        assertEquals(6f, TvFit.size(8, 100, 6, 1, true));
    }

    @Test
    void desligadoDevolveOMaximo() {
        assertEquals(8f, TvFit.size(8, 10, 2, 10, false));
    }

    @Test
    void nuncaAbaixoDeUmPixel() {
        assertEquals(1f, TvFit.size(8, 1, 20, 100, true));
    }

    @Test
    void textoVazioFicaNoMaximoQueCabeNaAltura() {
        assertEquals(8f, TvFit.size(8, 10, 20, 0, true));
    }
}
```

- [ ] **Step 5: Rodar e ver falhar**

Run: `./gradlew test --tests "*TvNumbersTest" --tests "*TvFitTest"`
Expected: FAIL na compilação.

- [ ] **Step 6: `TvNumbers` e `TvFit`**

```java
package io.github.matheusanbs.wirelessautomate.client.tv;

/**
 * O número de uma peça Valor como texto: abreviado (k, M, G, T, P) ou com os milhares agrupados, com as casas
 * pedidas, o sinal de + opcional e os separadores do idioma. Sem dado ({@code NaN}), um traço. Roda só no
 * redesenho da Tela. Lógica pura, com JUnit.
 */
public final class TvNumbers {
    private static final String[] SUFFIXES = {"k", "M", "G", "T", "P"};
    private static final long[] POW = {1, 10, 100, 1000};

    private TvNumbers() {
    }

    public static String format(double value, int decimals, boolean abbreviate, boolean sign, char decimalSep,
                                char groupSep) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "—";
        }
        int d = Math.max(0, Math.min(3, decimals));
        StringBuilder out = new StringBuilder(16);
        if (value < 0) {
            out.append('-');
        } else if (sign && value > 0) {
            out.append('+');
        }
        double abs = Math.abs(value);
        if (abbreviate && abs >= 1000) {
            int suffix = -1;
            while (abs >= 1000 && suffix < SUFFIXES.length - 1) {
                abs /= 1000;
                suffix++;
            }
            if (Math.round(abs * POW[d]) >= 1000 * POW[d] && suffix < SUFFIXES.length - 1) {
                abs /= 1000;
                suffix++;
            }
            fixed(out, abs, d, decimalSep);
            return out.append(SUFFIXES[suffix]).toString();
        }
        if (abs >= 1000 || abs == Math.rint(abs)) {
            grouped(out, Math.round(abs), groupSep);
        } else {
            fixed(out, abs, d, decimalSep);
        }
        return out.toString();
    }

    private static void fixed(StringBuilder out, double value, int decimals, char sep) {
        long pow = POW[decimals];
        long scaled = Math.round(value * pow);
        out.append(scaled / pow);
        if (decimals > 0) {
            out.append(sep);
            String frac = Long.toString(scaled % pow);
            for (int k = frac.length(); k < decimals; k++) {
                out.append('0');
            }
            out.append(frac);
        }
    }

    private static void grouped(StringBuilder out, long value, char sep) {
        String digits = Long.toString(value);
        int first = digits.length() % 3 == 0 ? 3 : digits.length() % 3;
        out.append(digits, 0, first);
        for (int k = first; k < digits.length(); k += 3) {
            out.append(sep).append(digits, k, k + 3);
        }
    }
}
```

```java
package io.github.matheusanbs.wirelessautomate.client.tv;

/**
 * O tamanho da fonte (em pixels de desenho da Tela) para um texto caber na caixa da peça: começa no máximo
 * da peça, não passa da altura da caixa e encolhe na proporção da largura. Nunca abaixo de 1 pixel. Lógica
 * pura, com JUnit.
 */
public final class TvFit {
    private TvFit() {
    }

    /**
     * @param widthAtSizeOne a largura do texto com a fonte de 1 pixel de altura
     * @param fit            a opção "Diminuir a fonte para caber"; desligada, o tamanho é o máximo
     */
    public static float size(float maxSize, float boxWidth, float boxHeight, float widthAtSizeOne, boolean fit) {
        if (!fit) {
            return maxSize;
        }
        float size = Math.min(maxSize, boxHeight);
        if (widthAtSizeOne > 0 && widthAtSizeOne * size > boxWidth) {
            size = boxWidth / widthAtSizeOne;
        }
        return Math.max(1f, size);
    }
}
```

- [ ] **Step 7: Rodar e ver passar**

Run: `./gradlew test --tests "*TvAreaTest" --tests "*TvNumbersTest" --tests "*TvFitTest"`
Expected: PASS (todos).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/tv/TvArea.java src/main/java/io/github/matheusanbs/wirelessautomate/client/tv/TvNumbers.java src/main/java/io/github/matheusanbs/wirelessautomate/client/tv/TvFit.java src/test/java/io/github/matheusanbs/wirelessautomate/tv/TvAreaTest.java src/test/java/io/github/matheusanbs/wirelessautomate/client/tv/TvNumbersTest.java src/test/java/io/github/matheusanbs/wirelessautomate/client/tv/TvFitTest.java
git commit -m "Tela de Sinal: área, número e fonte que cabe (lógica pura)"
```

---

### Task 2: O modelo do layout e o codec

**Files:**
- Create: `tv/TvPieceType.java`, `tv/TvMetric.java`, `tv/TvUnit.java`, `tv/TvAlign.java`, `tv/TvBinding.java`, `tv/TvFormat.java`, `tv/TvStyle.java`, `tv/TvPiece.java`, `tv/TvLayout.java`
- Test: `src/main/java/io/github/matheusanbs/wirelessautomate/gametest/TvLayoutGameTests.java`

**Interfaces:**
- Consumes: `TvArea` (Tarefa 1).
- Produces (exatos; as tarefas seguintes usam estes nomes):
  - `enum TvPieceType implements StringRepresentable { VALUE("value"), TEXT("text"), SHAPE("shape") }` com `CODEC`.
  - `enum TvMetric implements StringRepresentable { IN("in", true), OUT("out", true), STORED("stored", true), RELEASED("released", true), BALANCE("balance", true), MOVING("moving", false), EMPTY("empty", false), WAITING("waiting", false), FULL("full", false), REDSTONE("redstone", false) }` com `boolean rate()`, `double read(@Nullable FlowStats stats)` (taxas por segundo pelos métodos do `FlowStats`; contagens pelos `States`: MOVING = `sourcesMoving + destinationsMoving`, EMPTY = `sourcesEmpty`, WAITING = `sourcesWaiting`, FULL = `destinationsFull`, REDSTONE = `redstoneStopped`; `stats == null` dá 0) e `CODEC`.
  - `enum TvUnit implements StringRepresentable { NONE("none", 0), SECOND("second", 1), TICK("tick", 1.0 / 20), MINUTE("minute", 60) }` com `double perSecond()` (o fator que multiplica o valor por segundo) e `CODEC`.
  - `enum TvAlign implements StringRepresentable { LEFT("left"), CENTER("center"), RIGHT("right") }` com `CODEC`.
  - `record TvBinding(UUID network, ResourceType type, TvMetric metric)` com `CODEC` (chaves `network`, `type` pelo `ResourceType.key()`, `metric`).
  - `record TvFormat(int decimals, boolean abbreviate, boolean sign, String prefix, String suffix, TvUnit unit)` com `DEFAULT = new TvFormat(1, true, false, "", "", TvUnit.SECOND)` e `CODEC` (chaves `decimals` 0 a 3, `abbreviate`, `sign`, `prefix` e `suffix` até 32 caracteres, `unit`; toda chave opcional com o padrão).
  - `record TvStyle(int color, int background, int border, int borderWidth, int size, TvAlign align, boolean fit, String text)` com `DEFAULT = new TvStyle(0xFFECE5D6, 0, 0, 0, 8, TvAlign.LEFT, true, "")` e `CODEC` (chaves `color`, `background`, `border` ARGB; `border_width` 0 a 4; `size` 1 a 128; `align`; `fit`; `text` até 128 caracteres; toda chave opcional).
  - `record TvPiece(TvPieceType type, int x, int y, int width, int height, int group, String name, Optional<TvBinding> binding, TvFormat format, TvStyle style)` com `CODEC` (chaves `type`, `x`, `y`, `w`, `h`, `group`, `name` até 32 caracteres, `bind`, `format`, `style`; largura e altura no mínimo 1).
  - `record TvLayout(List<TvPiece> pieces, List<TvBinding> bindings)`, criado por `static TvLayout of(List<TvPiece> pieces)` (o construtor compacto recalcula `bindings` a partir de `pieces` e ignora o recebido), com `EMPTY`, `static final int HARD_CAP = 512`, `CODEC` (chave `pieces`; peças a mais que `HARD_CAP` são largadas; uma peça que não decodifica é largada, não derruba o layout), `List<TvBinding> bindings()` (as distintas, na ordem da primeira peça que as usa; calculada uma vez no construtor e guardada), `int bindingIndex(TvBinding binding)` (a posição em `bindings()`, ou -1) e `TvLayout clampedTo(TvArea area)` (cada peça encolhida para caber em `area.pixelWidth()` × `area.pixelHeight()` e empurrada para dentro).

**Custo:** nenhum por tick. O `bindings()` é calculado uma vez por layout (no construtor), não por amostra. O NBT do layout só é escrito ao salvar o chunk e ao mandar o update tag (que só sai quando o layout ou o tamanho muda, Tarefa 3).

- [ ] **Step 1: Os GameTests do layout (falham por falta das classes)**

Escreva `TvLayoutGameTests` (`@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`, `template = "empty"`, cada teste chama `helper.succeed()` no fim), com:
  - `layoutRoundTripNoNbt`: um layout com uma peça de cada tipo e todos os campos diferentes do padrão (binding com uma rede `UUID.randomUUID()`, `ResourceType.ENERGY`, `TvMetric.OUT`; formato com 2 casas, sem abreviar, com sinal, prefixo `"▲ "`, unidade `TICK`; estilo com borda 2, alinhado ao centro, `fit` falso e texto `"Base"`) vai por `TvLayout.CODEC.encodeStart(NbtOps.INSTANCE, ...)` e volta igual (`assertValueEqual` no record).
  - `pecaInvalidaELargadaSemDerrubarOLayout`: um `ListTag` com três peças, a do meio com `type` `"nao_existe"`, decodifica em duas peças.
  - `textosLongosSaoCortadosNaLeitura`: `text` de 300 caracteres volta com 128; `name` de 100 volta com 32; `decimals` 9 volta 3; `w` 0 volta 1.
  - `bindingsDistintosNaOrdem`: três peças, a 1ª e a 3ª com o mesmo binding, a 2ª com outro: `bindings()` tem 2, na ordem; `bindingIndex` do segundo é 1; de um binding ausente, -1.
  - `clampedToTrazAsPecasParaDentro`: numa área 6×3 (192 × 96 px), uma peça em x 180, largura 40 vira x 152, largura 40; uma de largura 300 vira x 0, largura 192.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew compileJava`
Expected: FAIL (classes não existem).

- [ ] **Step 3: Implementar as classes**

Os enums seguem o padrão de `StringRepresentable` do mod (veja `network/PortMode.java` e o `CODEC = StringRepresentable.fromEnum(...)`). O `ResourceType` vai no codec pela chave: `Codec.STRING.comapFlatMap(key -> { ResourceType t = ResourceType.byKey(key); return t == null ? DataResult.error(() -> "tipo desconhecido: " + key) : DataResult.success(t); }, ResourceType::key)`. As chaves opcionais usam `optionalFieldOf(nome, padrão)`. Os cortes de texto e de faixa são feitos no construtor canônico de cada record (o construtor compacto), para valerem também para quem cria o record em código. A lista do `TvLayout` decodifica cada elemento à parte e larga o que falha (`Codec.list` derrubaria tudo): use `Codec.PASSTHROUGH` ou um `Codec<List<TvPiece>>` próprio que percorre os `Dynamic` e guarda os `result().isPresent()`; registre no log em `debug` o número de peças largadas. O construtor compacto do `TvLayout` recalcula `bindings` (as distintas, na ordem, por um `LinkedHashSet`, guardadas com `List.copyOf`), então o `bindings()` é só a leitura de um campo; o codec só lê e escreve `pieces` e cria pelo `TvLayout.of`.

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew build runGameTestServer`
Expected: `BUILD SUCCESSFUL`, os 5 GameTests novos passando.

- [ ] **Step 5: Varreduras e commit**

```bash
python -I .claude/skills/wa-performance/scripts/varrer_perf.py
python -I .claude/skills/wa-comentarios/scripts/varrer.py
python -I .claude/skills/wa-modular/scripts/medir.py src/main/java/io/github/matheusanbs/wirelessautomate/tv/*.java
git add src/main/java/io/github/matheusanbs/wirelessautomate/tv/ src/main/java/io/github/matheusanbs/wirelessautomate/gametest/TvLayoutGameTests.java
git commit -m "Tela de Sinal: modelo do layout (peças Valor, Texto e Forma) e codec"
```

---

### Task 3a: O modelo no Blockbench (pedido do dono em 27/10)

O dono pediu que o bloco seja modelado no Blockbench, **no mesmo nível de qualidade do Reparador de Cristais** (`docs/preview/reparador-de-cristais.bbmodel`, atlas pintado à mão, convertido pelo `scripts/textures/reparador_modelo.py`). Esta tarefa substitui a parte "Modelo" e "textura" da Tarefa 3.

**Files:**
- Create: `docs/preview/tela-de-sinal.bbmodel` (feito pelo servidor MCP do Blockbench; atlas embutido)
- Create: `scripts/textures/bbmodel_json.py` (as funções de conversão do `.bbmodel` para JSON de bloco que hoje vivem no `reparador_modelo.py`: leitura, atlas com a borda repetida 1 px, uv, quartos de volta), `scripts/textures/tela_modelo.py` (o conversor da Tela, como o do Reparador)
- Modify: `scripts/textures/reparador_modelo.py` (passa a importar o `bbmodel_json`; **a saída do Reparador fica byte a byte igual**: confira com `git diff --stat` nos arquivos gerados dele depois de rodar), `scripts/textures/gerar_texturas.py` (chama o `tela_modelo.gerar()` e preserva as chaves dele)
- Saída gerada: `models/block/signal_screen/*.json`, `textures/block/signal_screen/signal_screen.png`, `blockstates/signal_screen.json`, `models/item/signal_screen.json`

**O modelo:** a Tela 1 × 1 vista de frente, rente à parede, com 2 px de espessura (de `z` 14 a 16 com a tela virada para o norte): moldura chanfrada escura (grafite `#23242a` a `#2e3038`, com luz de cima e da esquerda como os sprites do mod), o vidro da tela recuado 0,5 px com um reflexo diagonal sutil, um LED de estado de 1 px no canto de baixo à direita da moldura (coral `#F0603E`, emissivo pelo `neoforge_data` como as luzes do Reparador) e as laterais com uma ranhura. A frente da tela, sem conteúdo, é o grafite `#0f1117` com uma vinheta leve.

**O atlas serve também ao BER:** a moldura de uma Tela maior que 1 × 1 é desenhada pelo BER em 9 fatias (4 cantos, 4 bordas esticadas ou repetidas e o miolo), tiradas do mesmo atlas, para uma 6 × 3 ter a mesma moldura de uma 1 × 1. Reserve no atlas, pintadas no Blockbench, as regiões: os 4 cantos da moldura frontal (3 × 3 px cada), as 4 bordas (1 px de espessura × 10 px de comprimento, repetíveis), a lateral (2 × 16 px, repetível) e o miolo apagado. As coordenadas das fatias ficam numa constante documentada no `tela_modelo.py` e são copiadas para `client/tv/render/TvFrame.java` (Tarefa 7), com um teste JUnit (`TvFrameTest`) que lê o PNG gerado e confere que cada fatia não é transparente.

**Qualidade:** confira por capturas do Blockbench (`screenshot_views`) em 3/4, de frente e de lado, e no jogo pela captura `tela-bloco` da Tarefa 8; compare lado a lado com o Reparador no mesmo enquadramento. Mostre as capturas ao dono no relatório.

**Custo:** nenhum em jogo além de um modelo de bloco estático (a seção é montada uma vez); o LED emissivo é do modelo, não do BER.

### Task 3: O bloco, o block entity, o registro e a config

**Files:**
- Create: `tv/TvBlock.java`, `tv/TvBlockEntity.java`, `tv/TvClientHooks.java`
- Modify: `registry/ModBlocks.java`, `registry/ModItems.java`, `registry/ModBlockEntities.java`, `registry/ModCreativeTabs.java`, `Config.java`, `ClientConfig.java`
- Create (recursos): `data/wirelessautomate/loot_table/blocks/signal_screen.json`, `data/wirelessautomate/recipe/signal_screen.json` (o modelo, a textura e o blockstate vêm da Tarefa 3a)
- Modify: `assets/wirelessautomate/lang/en_us.json`, `pt_br.json`
- Test: `gametest/TvBlockGameTests.java`

**Interfaces:**
- Consumes: `TvArea`, `TvLayout` (Tarefas 1 e 2).
- Produces:
  - `TvBlock extends BaseEntityBlock` com `FACING = BlockStateProperties.HORIZONTAL_FACING` e `EXPANDED = BooleanProperty.create("expanded")` (verdadeiro com área maior que 1 × 1: o modelo do bloco some e o BER desenha a moldura e a imagem na área inteira, inclusive na célula do bloco; o `setArea` troca o estado com `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE` só quando muda); `static BlockPos wallOf(BlockPos pos, Direction facing)` (`pos.relative(facing.getOpposite())`); `static Direction rightOf(Direction facing)` (`facing.getCounterClockWise()`).
  - `TvBlockEntity` com `@Nullable UUID owner()`, `void setOwner(UUID)`, `TvArea area()`, `TvLayout layout()`, `int revision()`, `boolean canEdit(ServerPlayer player)` (dono ou permissão 2; sem dono, só operador), `void setArea(TvArea area)` e `void setLayout(TvLayout layout)` (cada um: muda, aplica `layout.clampedTo(area)`, `revision++`, `setChanged()`, `level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)`), `BlockPos cellPos(int cell)` (a posição no mundo da célula, pela `area()`, `FACING` e `TvBlock.rightOf`), `AABB renderBox()` (a caixa das células, com a espessura do painel).
  - `TvClientHooks`: `public static volatile Consumer<TvBlockEntity> changed = tv -> {};` chamado no cliente quando o update tag chega (o `client/tv` troca no `ClientSetup`).
  - `ModBlocks.SIGNAL_SCREEN`, `ModItems.SIGNAL_SCREEN`, `ModBlockEntities.SIGNAL_SCREEN`.
  - `Config.TV_MAX_WIDTH`, `TV_MAX_HEIGHT` (`IntValue`, 1 a 64, padrão 32), `TV_MAX_ELEMENTS` (`IntValue`, 1 a 512, padrão 128), `TV_MAX_BINDINGS` (`IntValue`, 1 a 256, padrão 64), `TV_VIEW_DISTANCE` (`IntValue`, 8 a 256, padrão 64), na seção `tv`, com comentários em português. `ClientConfig.TV_PIXELS_PER_BLOCK` (`IntValue`, valores aceitos 16, 32 ou 64 por `defineInList`, padrão 32), na seção `tv`.

**Custo:** nenhum por tick (sem ticker). `setChanged()` e o update tag só nas mudanças de área, layout e dono. O update tag leva `width`, `height`, `anchor_col`, `anchor_row`, `layout` e `revision`, sem o `owner` (o cliente não precisa).

- [ ] **Step 1: GameTests do bloco (`TvBlockGameTests`)**, que falham sem as classes:
  - `colocaContraParedeEOlhaParaFora`: com pedra em (1,1,2) e a Tela posta em (1,1,1) por `TvBlock.getStateForPlacement` num `BlockPlaceContext` com a face clicada `NORTH` (use `new BlockPlaceContext(level, player, hand, stack, hitResult)` com um `BlockHitResult` na face norte da pedra; jogador por `TestPlayers.inLevel`), o estado tem `FACING == NORTH`; com a face clicada `UP`, `getStateForPlacement` devolve `null`.
  - `semParedeNaoSobrevive`: `canSurvive` falso sem bloco sólido atrás; tirar a parede (`setBlock` ar) faz a Tela virar ar no tick seguinte (`updateShape`) e largar o item.
  - `nbtGuardaTudo`: área `new TvArea(4, 2, 3, 0)`, um layout de duas peças e um dono; `saveWithFullMetadata` → `loadWithComponents` num block entity novo volta igual (área, layout, dono, revisão).
  - `updateTagSemDono`: `getUpdateTag` não tem a chave `owner` e tem `width`, `layout` e `revision`.
  - `revisaoSobeACadaMudanca`: `setArea` e `setLayout` sobem a `revision` em 1 cada; `setArea` menor traz as peças para dentro (`clampedTo`).
  - `quemEdita`: dono edita; outro jogador não; operador edita (use `TestPlayers` com permissão; veja como os `NetworkAccessGameTests` fazem operador); sem dono, só operador.
  - `celulaNoMundoPelaFrente`: Tela virada para o norte em (2,1,1), área `new TvArea(3, 2, 0, 0)`: a célula 0 é (2,1,1), a 1 é (1,1,1) (a direita de quem olha para o sul é o oeste), a 3 é (2,2,1). Virada para o leste: a direita é o norte.

- [ ] **Step 2: Rodar e ver falhar** (`./gradlew compileJava`: FAIL).

- [ ] **Step 3: Implementar.** O `TvBlock` segue o `RepairerBlock` (codec, `createBlockStateDefinition`, `rotate`, `mirror`, `newBlockEntity`), mais: `getStateForPlacement` com a face clicada horizontal (`context.getClickedFace()`), senão `null`; `canSurvive` pela face sólida da parede (`wall.isFaceSturdy(level, wallPos, facing)`); `updateShape` devolve ar quando a direção mudada é a da parede e `canSurvive` falha; a forma é o painel de 2 px rente à parede (16 × 16 × 2, girada pelo `FACING`; guarde as 4 formas em constantes); `setPlacedBy` grava o dono (`placer` jogador). O clique de mão vazia (`useWithoutItem`) fica para a Tarefa 6; nesta, devolve `InteractionResult.PASS`. O `TvBlockEntity` segue o `RepairerBlockEntity` no `getUpdateTag`, `getUpdatePacket` (`ClientboundBlockEntityDataPacket.create(this)`), `handleUpdateTag`, `saveAdditional`/`loadAdditional`; no cliente, `handleUpdateTag` e `onDataPacket` chamam `TvClientHooks.changed.accept(this)`. O layout no NBT vai pelo `TvLayout.CODEC` com `NbtOps`, e o erro de leitura cai em `TvLayout.EMPTY` (log em `warn` com a posição). Leitura de área inválida (`width` 0, âncora fora) cai em `TvArea.SINGLE`.
  - Modelo, textura e blockstate: os da Tarefa 3a (já gerados); a forma de colisão segue a caixa do painel do modelo.
  - Receita (decisão 1) com `"type": "minecraft:crafting_shaped"` e as tags `c:`; loot table que larga o próprio bloco (`minecraft:block`, condição `survives_explosion`).
  - `lang`: `block.wirelessautomate.signal_screen` = "Signal Screen" / "Tela de Sinal".
  - Aba criativa: depois do Reparador de Cristais.

- [ ] **Step 4: Rodar e ver passar:** `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams` → `BUILD SUCCESSFUL`.

- [ ] **Step 5: Varreduras (`varrer_perf.py`, `varrer.py`, `medir.py`) e commit**

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/tv/ src/main/java/io/github/matheusanbs/wirelessautomate/registry/ src/main/java/io/github/matheusanbs/wirelessautomate/Config.java src/main/java/io/github/matheusanbs/wirelessautomate/ClientConfig.java src/main/java/io/github/matheusanbs/wirelessautomate/gametest/TvBlockGameTests.java src/main/resources/data/wirelessautomate/loot_table/blocks/signal_screen.json src/main/resources/data/wirelessautomate/recipe/signal_screen.json src/main/resources/assets/wirelessautomate/lang/en_us.json src/main/resources/assets/wirelessautomate/lang/pt_br.json
git commit -m "Tela de Sinal: bloco na parede, block entity, receita e config"
```

---

### Task 4: A área no mundo

**Files:**
- Create: `tv/TvAreaCheck.java`, `tv/TvLoaded.java`
- Modify: `tv/TvBlockEntity.java` (entrar e sair do `TvLoaded` no `onLoad`, `setRemoved` e `onChunkUnloaded`)
- Test: `gametest/TvAreaGameTests.java` (template `empty7`, para caber uma área 4 × 3 com parede)

**Interfaces:**
- Consumes: `TvBlockEntity.cellPos`, `TvBlock.wallOf`, `TvArea`.
- Produces:
  - `TvLoaded`: dois conjuntos por identidade, um do servidor e um do cliente (`Set<TvBlockEntity> of(Level level)` devolve o do lado do `level`), `add(TvBlockEntity)`, `remove(TvBlockEntity)`, `clearServer()` (parada do servidor) e `clearClient()` (sair do mundo). Código comum, sem classes do cliente.
  - `TvAreaCheck.Problem(Kind kind, @Nullable BlockPos pos)` com `enum Kind { NONE, TOO_BIG, NO_WALL, BLOCKED, OVERLAP }` e `TvAreaCheck.check(Level level, BlockPos pos, Direction facing, TvArea area, int maxWidth, int maxHeight, Iterable<TvBlockEntity> others)`: `TOO_BIG` pelo `TvArea.fits`; para cada célula que não é a do bloco colocado, `NO_WALL` se a parede atrás (`wallOf`) não tem a face sólida, `BLOCKED` se a célula não é ar nem substituível (`state.isAir() || state.canBeReplaced()`); `OVERLAP` se a caixa das células cruza a `renderBox()` de outra Tela do mesmo `level` (pulando a própria); devolve o primeiro problema, com a posição da célula. Nunca consulta célula num chunk não carregado: célula fora de `level.isLoaded(pos)` é `BLOCKED`.

**Custo:** a checagem roda só quando o jogador aplica um tamanho (servidor) e quando a aba Tamanho muda o tamanho pedido (cliente, prévia), no máximo 32 × 32 = 1.024 células × 2 estados de bloco, mais as outras Telas carregadas na mesma dimensão a até 64 blocos (pule as mais longe pela distância antes de comparar caixas). Nada por tick.

- [ ] **Step 1: GameTests (`TvAreaGameTests`)**, que falham sem as classes:
  - `areaLivreContraParede`: parede de pedra 4 × 3, Tela no canto de baixo à esquerda, `TvArea.grown(4, 3, UP_RIGHT)`: `NONE`.
  - `faltaParede`: tire um bloco da parede: `NO_WALL` com a posição daquela célula.
  - `tochaNaFrenteBloqueia`: tocha na frente de uma célula: `BLOCKED` com a posição; grama alta (`Blocks.SHORT_GRASS`) não bloqueia.
  - `outraTelaSobrepoe`: uma segunda Tela já com área 2 × 1 na mesma parede, cruzando: `OVERLAP`; lado a lado sem cruzar: `NONE`.
  - `tamanhoAcimaDoTeto`: `maxWidth` 4 e área 5 × 1: `TOO_BIG`.
  - `umPorUmSempreLivre`: `TvArea.SINGLE` sem parede ao lado: `NONE`.
  - `entraESaiDoTvLoaded`: depois do `onLoad`, a Tela está em `TvLoaded.of(level)`; quebrada, sai.

- [ ] **Step 2: Ver falhar (`./gradlew compileJava`).**
- [ ] **Step 3: Implementar.**
- [ ] **Step 4: `./gradlew build runGameTestServer` → passa.**
- [ ] **Step 5: Varreduras e commit** (`git add` dos três arquivos de `tv/` e do teste; mensagem `Tela de Sinal: validação da área na parede`).

---

### Task 5: O canal de dados

**Files:**
- Create: `tv/TvManager.java`, `tv/TvFeed.java`, `packet/TvValuesPayload.java`
- Modify: `tv/TvBlockEntity.java` (um `TvFeed` transitório, só no servidor, criado sob demanda), `WirelessAutomate.java` (tick e parada), `packet/ModPayloads.java` (registro e `VERSION = "35"`), `command/WaCommand.java` (linha da Tela no `/wa profile`), `lang` (a linha do profile)
- Test: `gametest/TvFeedGameTests.java`

**Interfaces:**
- Consumes: `TvLayout.bindings()`, `TvMetric.read`, `TvLoaded`, `FlowSampler.report()` (via `NetworkManager.get().flows()`), `NetworkSavedData.get(server).network(UUID)` (confira o nome do método no `NetworkSavedData`), `WaNetwork.canUse(UUID, boolean)`.
- Produces:
  - `TvValuesPayload(BlockPos pos, int revision, double[] values)`, `playToClient`, codec manual (`BlockPos.STREAM_CODEC`, `VAR_INT` da revisão, `VAR_INT` do tamanho e os `double`). O handler do cliente é `TvClientValues::receive` (Tarefa 7); nesta tarefa registre o pacote com um handler que chama `TvClientHooks.values.accept(payload)` (um segundo gancho no `TvClientHooks`, `Consumer<TvValuesPayload>`, vazio por padrão), para o código comum não referenciar o cliente.
  - `TvFeed` (por Tela, no servidor): `double[] values`, `int valuesVersion`, `long readSample`, `int readRevision` e um `Object2IntOpenHashMap<UUID> seen` (jogador → versão que recebeu).
  - `TvManager`: `static void tick(MinecraftServer server)`, `static void reset()`, `static Cost cost()` com `record Cost(int loaded, int watched, long packets, double averageNanos, long peakNanos)`, e para os testes `static void sampleForTests(TvBlockEntity tv, FlowSampler.Report report, List<ServerPlayer> near)` (o mesmo caminho da visita, com o relatório e os jogadores dados) e `static long packetsForTests()`.

**O que a visita faz** (uma Tela, na fase `Math.floorMod(pos.hashCode(), 20) == now % 20`):
1. Layout sem binding: limpa o `TvFeed` (se existe) e sai.
2. Jogadores perto: `level.players()` do `ServerLevel` da Tela, a até `tv.viewDistance` blocos (distância ao quadrado até o centro do bloco). Nenhum: limpa o `seen` e sai, sem ler o relatório.
3. Se o `report.sampledAt()` ou a `revision` do layout mudaram desde a leitura da Tela: para cada binding, o valor é `NaN` se a rede não existe ou o dono não pode usá-la (`canUse(dono, false)`; sem dono, `NaN`), senão `metric.read(mapa.get(rede, tipo))`. Se algum valor mudou (compare com `Double.compare`, para o `NaN`), `valuesVersion++`.
4. Para cada jogador perto com `seen.getInt(uuid) != valuesVersion`: manda o `TvValuesPayload` (`PacketDistributor.sendToPlayer`) e grava a versão.

O mapa rede × tipo → `FlowStats` é do `TvManager`, montado uma vez por volta do amostrador (quando `report.sampledAt()` muda), reusado entre ticks (`HashMap` limpo com `clear()`, chave `record FlowKey(UUID network, ResourceType type)`), e só montado se alguma Tela com alguém perto o pede naquele segundo.

**Custo:** sem Tela carregada, `isEmpty()`. Com Telas, por tick só as do balde da vez; uma Tela sem binding ou sem jogador perto custa um laço pelos jogadores da dimensão. Os valores só são relidos uma vez por volta do amostrador; pacote só com mudança e só para quem não tem. Meça o `averageNanos` e o pico no `/wa profile` (linha nova: "Telas: N carregadas, M com alguém perto, X pacotes, Y µs por tick em média, pico Z µs").

- [ ] **Step 1: GameTests (`TvFeedGameTests`)**, que falham sem as classes. Monte o `FlowSampler.Report` à mão (é um record público: `new FlowSampler.Report(sampledAt, List.of(new FlowStats(rede, ResourceType.ENERGY, 2_000, 1_000, 0, 0, 5, FlowStats.States.NONE)), ...)`; confira os outros campos no record) e use `TvManager.sampleForTests`:
  - `mandaSoParaQuemEstaPerto`: dois jogadores, um a 10 e outro a 100 blocos (passe só o de perto em `near`): um pacote; amostrar de novo com o mesmo relatório: nenhum pacote a mais.
  - `valorMudouMandaDeNovo`: um relatório novo (outro `sampledAt`) com outro número: mais um pacote; outro `sampledAt` com os mesmos números: nenhum.
  - `quemChegaRecebeOsAtuais`: depois do primeiro pacote, um segundo jogador em `near` recebe um pacote sem os números mudarem.
  - `semAcessoViraNaN`: rede privada de outro jogador: o valor guardado no `TvFeed` é `NaN`; rede pública de outro: o número.
  - `redeQueSumiuViraNaN`: binding de um `UUID.randomUUID()`: `NaN`.
  - `semBindingNaoLeNada`: layout só com Texto: nenhum pacote, `TvFeed` vazio.
  - `taxasELeiturasDoFlowStats`: `IN` com `produced` 2.000 em 5 segundos lê 400; `MOVING` soma origens e destinos movendo.
  - `ninguemPertoNaoMontaOMapa`: com `near` vazio, o mapa do `TvManager` não é montado (exponha `mapBuildsForTests()`).
  - Um teste com fluxo de verdade no lote `wirelessautomate_flows` (veja `FlowGameTests` para montar a rede, um roteador e duas máquinas): a Tela ligada a `IN` de itens recebe um número maior que zero depois de duas voltas do amostrador, pelo `TvManager.tick` real, com um jogador perto (`TestPlayers.inLevel`).

- [ ] **Step 2: Ver falhar.**
- [ ] **Step 3: Implementar** (tick no `onServerTick` do `WirelessAutomate`, depois do `RepairerManager.tick`; `reset()` e `TvLoaded.clearServer()` no `onServerStopped`; `VERSION = "35"` com o comentário da mudança).
- [ ] **Step 4: Suíte inteira** (`./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams`) → passa.
- [ ] **Step 5: Varreduras e commit** (`Tela de Sinal: canal de dados das redes (protocolo 35)`).

---

### Task 6: Abrir a Tela, aplicar o tamanho e o modelo

**Files:**
- Create: `menu/TvMenu.java`, `tv/TvEditing.java`, `tv/TvTemplates.java`, `packet/TvSizePayload.java`, `packet/TvTemplatePayload.java`
- Modify: `tv/TvBlock.java` (`useWithoutItem`), `registry/ModMenus.java`, `packet/ModPayloads.java`, `lang`
- Test: `gametest/TvMenuGameTests.java`

**Interfaces:**
- Consumes: `TvBlockEntity.canEdit/setArea/setLayout`, `TvAreaCheck.check`, `TvLoaded.of`, `Config.TV_*`, `LoadedTypes`.
- Produces:
  - `TvMenu` (sem slots; `IMenuTypeExtension.create(TvMenu::new)`): os dados de abertura vão no buffer do `openMenu` (posição, `TvArea` atual, `maxWidth`, `maxHeight`, a rede e o tipo do primeiro binding do layout se houver, e a lista `NetworkChoice(UUID id, String name, int color)` das redes que o jogador pode usar, ordenada pelo nome, até 256). `static void open(ServerPlayer, TvBlockEntity)`; `stillValid`: o bloco existe, o jogador a até 8 blocos e `canEdit`. Leitores: `pos()`, `area()`, `maxWidth()`, `maxHeight()`, `networks()`, `currentNetwork()`, `currentType()`.
  - `TvSizePayload(int containerId, int width, int height, int anchorCol, int anchorRow)` e `TvTemplatePayload(int containerId, Optional<UUID> network, String typeKey)` (`playToServer`). O handler acha o `TvMenu` pelo `containerId` do jogador, como os outros (`ModPayloads.host` é dos roteadores; aqui confira `player.containerMenu instanceof TvMenu menu && menu.containerId == id`).
  - `TvEditing.applySize(ServerPlayer, TvBlockEntity, TvArea)` → `TvAreaCheck.Problem`; no `NONE`, `setArea`; no resto, a action bar com o problema (`message.wirelessautomate.tv.problem.<kind>` com as coordenadas). `TvEditing.applyTemplate(ServerPlayer, TvBlockEntity, @Nullable UUID network, @Nullable ResourceType type)`: sem rede, `setLayout(TvLayout.EMPTY)`; com rede, exige `canUse(player)` e o tipo carregado, e aplica `TvTemplates.networkPanel(...)`; o layout passa pelos tetos `maxElements` e `maxBindings`.
  - `TvTemplates.networkPanel(String networkName, int networkColor, UUID network, ResourceType type, TvArea area)`: o "Painel de rede", proporcional à área (todas as medidas em pixels de desenho, `W = area.pixelWidth()`, `H = area.pixelHeight()`, margem `m = max(2, min(W, H) / 16)`):
    1. Forma de fundo `(0, 0, W, H)`, fundo `0xFF1A1C24`, borda `networkColor | 0xFF000000` de 1 px.
    2. Texto com `networkName`, cor `0xFF8D8577`, `(m, m, W - 2m, H / 6)`, tamanho máximo `H / 6`.
    3. Valor `IN`, prefixo `"▲ "`, cor `0xFF3DB56E`, `(m, m + H / 6, W - 2m, H / 4)`, tamanho máximo `H / 4`, unidade `TICK` se `type.ratePerTick()`, senão `SECOND`.
    4. Valor `OUT`, prefixo `"▼ "`, cor `0xFFF26A48`, logo abaixo, mesma altura e unidade.
    5. Valor `MOVING`, prefixo `"⇄ "`, sem abreviar e 0 casas, unidade `NONE`, cor `0xFF8D8577`, `(m, H - m - H / 8, W - 2m, H / 8)`.
- `TvBlock.useWithoutItem`: no servidor, quem pode editar abre o `TvMenu`; quem não pode recebe a action bar `message.wirelessautomate.tv.not_owner` (com o nome do dono pelo `KnownPlayers` ou "um operador" sem dono).

**Custo:** nada por tick; a abertura monta a lista de redes uma vez (até 256, ordenadas); os payloads só agem com a tela aberta e validam tudo (tamanho, âncora, teto, rede, tipo).

- [ ] **Step 1: GameTests (`TvMenuGameTests`)**, que falham sem as classes:
  - `donoAbreOutroNao`: o dono abre (o `containerMenu` vira `TvMenu`); outro jogador não abre.
  - `tamanhoValidoAplica` e `tamanhoComProblemaRecusa` (tocha na frente: a área continua a mesma).
  - `tamanhoForaDosLimitesRecusa`: largura 0, 99, âncora fora: nada muda.
  - `modeloPrecisaDeAcessoARede`: rede privada de outro jogador: recusa; rede própria: o layout tem 5 peças e os bindings `IN`, `OUT`, `MOVING` naquela rede e tipo.
  - `modeloSemRedeLimpa`: `TvLayout.EMPTY`.
  - `menuInvalidoLonge`: a 20 blocos, `stillValid` falso.
  - `listaDeRedesSoAsQueUsa`: a lista da abertura tem a rede própria e a pública, não a privada de outro.

- [ ] **Step 2: Ver falhar.** **Step 3: Implementar.** **Step 4: suíte inteira → passa.**
- [ ] **Step 5: Varreduras e commit** (`Tela de Sinal: menu, tamanho e modelo Painel de rede`).

---

### Task 7: O desenho no mundo

**Files:**
- Create: `client/tv/TvClientValues.java`, `client/tv/TvUnits.java`, `client/tv/render/TvFrame.java` (as fatias do atlas, com o `TvFrameTest` da Tarefa 3a), `client/tv/render/TvCanvas.java`, `client/tv/render/TvCanvases.java`, `client/tv/render/TvPainter.java`, `client/tv/render/TvRenderer.java`
- Modify: `client/ClientSetup.java` (BER, ganchos do `TvClientHooks`, eventos `RenderFrameEvent.Pre`, `ClientTickEvent.Post` e saída do mundo), `lang` (unidades)

**Interfaces:**
- Consumes: `TvBlockEntity.area()/layout()/revision()/renderBox()`, `TvValuesPayload`, `TvNumbers`, `TvFit`, `TvUnit.perSecond()`, `ClientConfig.TV_PIXELS_PER_BLOCK`.
- Produces:
  - `TvClientValues`: por posição (`Long2ObjectOpenHashMap` pelo `BlockPos.asLong`), os valores e a revisão recebidos; `receive(TvValuesPayload)` guarda e marca a Tela suja (`TvCanvases.markDirty(pos)`); `double value(long pos, int revision, int index)` dá `NaN` quando a revisão não bate; `clear()` ao sair do mundo.
  - `TvUnits.label(ResourceType type, TvUnit unit)`: o texto da unidade no idioma do jogador, pelas chaves `screen.wirelessautomate.tv.unit.<tipo>` (itens: "", fluidos e químicos: "mB", energia: "FE", Source: "") e `screen.wirelessautomate.tv.per.<unit>` ("/s", "/t", "/min"); `NONE` dá "". Montado uma vez por idioma e guardado num array por tipo × unidade.
  - `TvCanvas` (uma Tela): o `TextureTarget` (largura `area.width × ppb`, altura `area.height × ppb`), um `AbstractTexture` registrado no `TextureManager` com um `ResourceLocation` próprio (`wirelessautomate:tv/<n>`) que devolve o id da cor do framebuffer e não o apaga no `close` (quem apaga é o `destroyBuffers`), a revisão e o tamanho com que foi desenhado e o último tick em que o BER o pediu.
  - `TvCanvases`: o mapa posição → `TvCanvas`, a fila de sujas (`LongArrayFIFOQueue` sem repetir, com um `LongOpenHashSet`), `markDirty(long pos)`, `@Nullable ResourceLocation texture(TvBlockEntity tv, long gameTime)` (o BER chama; cria o canvas sob demanda e marca sujo se a revisão ou o ppb mudaram), `onFrame()` (no `RenderFrameEvent.Pre`: com a fila vazia, sai; senão redesenha até 4), `onClientTick()` (a cada 100 ticks, libera os que o BER não pede há 200 ticks) e `clear()`.
  - `TvPainter.paint(GuiGraphics g, Font font, TvLayout layout, long pos, int revision, char decimalSep, char groupSep)`: desenha as peças em ordem, em pixels de desenho (o chamador já escalou o `pose` por `ppb / 32f`). Forma: `fill` do fundo e a borda em 4 `fill`. Texto e Valor: o texto (Valor: `prefix + TvNumbers.format(valor × unit.perSecond(), ...) + (suffix vazio e unidade? " " + TvUnits.label : "") + suffix`; o `NaN` dá "—" sem unidade), o tamanho pelo `TvFit.size(style.size, w, h, font.width(texto) / 9f, style.fit)`, a escala `size / 9f`, centrado na altura da caixa e alinhado pela `TvAlign`; recorte com `g.enableScissor` só quando `fit` está desligado.
  - `TvRenderer` (BER): `getViewDistance` 64; `shouldRenderOffScreen` falso; `getRenderBoundingBox` = `tv.renderBox()`. Desenha a moldura (a caixa do painel em toda a área, 2/16 de espessura, em 9 fatias do atlas da Tarefa 3a pelo `TvFrame`, em `RenderType.entitySolid`; numa Tela 1 × 1, `expanded` falso, o modelo do bloco já é a moldura e o BER não a desenha; com `expanded` verdadeiro o modelo some e o BER desenha tudo) e, se o layout não está vazio e a textura existe, o quadrilátero da tela 0,001 à frente com `RenderType.text(textura)` e `LightTexture.FULL_BRIGHT`; layout vazio: só a moldura com o miolo apagado do atlas. Nada alocado por quadro (as matrizes vêm do `PoseStack`; vértices direto no `VertexConsumer`).
- **O redesenho no framebuffer** (em `TvCanvases.onFrame`, na thread de render), roteiro a conferir contra o 1.21.1: guardar a projeção (`RenderSystem.getProjectionMatrix()` e `getVertexSorting()`); `target.setClearColor(0, 0, 0, 0)`, `target.clear(Minecraft.ON_OSX)`, `target.bindWrite(true)`; `RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, larguraPx, alturaPx, 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z)`; a pilha do model view (`RenderSystem.getModelViewStack()`) com `pushMatrix()`, `identity()`, `translate(0, 0, -11000)` e `RenderSystem.applyModelViewMatrix()`; um `GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource())` com `pose().scale(ppb / 32f, ppb / 32f, 1)`; `TvPainter.paint`; `g.flush()`; restaurar a pilha, a projeção e `minecraft.getMainRenderTarget().bindWrite(true)`. O framebuffer guarda a imagem com o eixo vertical invertido: o BER usa `v` de 1 em cima e 0 embaixo. Confirme pela captura do e2e (texto legível, sem espelhar).

**Custo:** por quadro, por Tela à vista, o BER grava 6 faces da moldura e 1 quadrilátero (sem alocar). O redesenho só com a Tela suja (valores novos chegam no máximo 1 vez por segundo), no máximo 4 por quadro; a medida de texto só nele. Sem Tela suja, o `onFrame` é uma checagem de fila vazia. Memória de vídeo: largura × altura × ppb² × 4 bytes por Tela à vista, liberada 200 ticks depois de sair da vista.

- [ ] **Step 1:** implementar, com o mod em dev aberto à mão se precisar (`./gradlew runClient`), um jogo por vez.
- [ ] **Step 2:** o e2e desta etapa nasce na Tarefa 8; nesta, rode `./gradlew build runGameTestServer` (o servidor dedicado prova que o código comum não carrega classe de cliente) e o e2e existente rápido (`./scripts/e2e.sh`) para garantir que nada quebrou.
- [ ] **Step 3: Varreduras e commit** (`Tela de Sinal: desenho por textura no mundo`).

---

### Task 8: A tela do bloco e o e2e

**Files:**
- Create: `client/tv/TvScreen.java` (anfitriã: cabeçalho com o nome e o `ThemeToggle`, as duas abas, repasse de desenho, mouse, teclado e dicas, ganchos do e2e), `TvScreenLayout.java` (as medidas: 320 × 220), `TvSizeTab.java`, `TvContentTab.java`, `TvScreenText.java`, `TvScreenActions.java` (todo payload que a tela manda; na captura, nada sai)
- Create: `client/dev/E2eTvSteps.java`; Modify: `client/dev/E2eSuites.java`, `client/ClientSetup.java` (registro da tela), `docs/e2e.md` (tabela de suítes), `lang`

**O que cada aba faz:**
- **Tamanho** (o mockup, seção 4): Largura e Altura com ‹ › (Shift: ±4), de 1 ao teto do menu; "Cresce a partir do bloco para" com os três botões (↗ ↖ ✥, o atual aceso pelo `TvArea.grow()`; uma âncora fora dos presets mostra nenhum aceso); a prévia, à direita, desenha a parede em volta do bloco (até a área caber, numa escala que cabe no quadro) com as células da área, o bloco colocado marcado e a primeira célula com problema em vermelho, pelo `TvAreaCheck.check` no `level` do cliente com `TvLoaded.of(level)` (só quando o tamanho pedido muda, guardado até a próxima mudança); a linha de baixo diz "Área livre: N blocos" ou o problema com as coordenadas; **Aplicar** manda o `TvSizePayload` e fica desligado com problema.
- **Conteúdo**: a lista das redes do menu (nome com a bolinha da cor, ✓ na atual, rolagem, até 256), os tipos carregados como botões (`LoadedTypes`), **Aplicar o Painel de rede** (manda o `TvTemplatePayload`) e **Limpar** em dois cliques (`client/ArmedClick`). Uma linha explica que o editor completo chega numa versão próxima.

**e2e:** suíte `tela` (pré-requisito: o mundo do e2e; veja `docs/e2e.md`): põe uma parede e a Tela, cria uma rede com um roteador e duas máquinas movendo itens (reaproveite o apoio dos Fluxos, sem copiar passos de outra suíte), abre a Tela, pede 4 × 2 com ↗ e aplica (confere a área no servidor), aplica o Painel da rede em Itens (confere o layout no servidor), espera o `TvClientValues` ter um valor maior que zero, e confere `GuiText.clipCount() == 0`. Capturas `tela-tamanho`, `tela-tamanho-problema` (tocha na frente), `tela-conteudo` `tela-mundo` (a Tela de frente, a 4 blocos) e `tela-bloco` (uma Tela 1 × 1 vazia em 3/4, ao lado de um Reparador, para comparar o acabamento), com as variantes `pt` e `escuro` pelo `E2eVariants`. Suíte opcional `tela-desempenho`: 8 Telas 32 × 32 à vista com o Painel, mede o tempo de quadro médio contra nenhuma Tela e o tempo de um redesenho (escreve no `result.txt`; o número entra no `docs/progresso.md`).

**Custo:** a tela só existe aberta; a prévia da área é recalculada só quando o tamanho pedido muda; textos medidos quando o dado muda (lei 8).

- [ ] **Step 1: Implementar** as classes da tela (cada uma abaixo de 500 linhas; `medir.py`).
- [ ] **Step 2: e2e:** `./scripts/e2e.sh --only tela --capture tela --variants pt,escuro` → `run/e2e/result.txt` com `OK`; olhe as capturas (folha de contato: `scripts/folha.py` da skill `wa-curseforge-imagens`): texto sem corte, número legível e não espelhado na `tela-mundo`, prévia com a célula vermelha na `tela-tamanho-problema`.
- [ ] **Step 3:** `./scripts/e2e.sh --only tela-desempenho` e anote os números.
- [ ] **Step 4: Suíte inteira de GameTests → passa. Varreduras e commit** (`Tela de Sinal: tela do bloco (Tamanho e Conteúdo) e e2e`).

---

### Task 9: Guia e documentação

**Files:**
- Modify: `scripts/guide/gerar_guia.py` (página nova `signal-screen` nos dois idiomas: colocar contra a parede, abrir, escolher o tamanho e o "cresce para", o que impede a área, aplicar o Painel de rede, quem pode editar; só "como operar") e as páginas geradas
- Modify: `CLAUDE.md` (mapa do código: `tv/`, `client/tv/`, `client/tv/render/`, a linha dos GameTests, o protocolo `35`), `docs/especificacao.md` (a Tela no roadmap), `docs/progresso.md` (estado, próximo passo = etapa 2 da Tela, linha no histórico com os números de testes e do `tela-desempenho`)

- [ ] **Step 1:** gerar o guia (`python scripts/guide/gerar_guia.py`) e conferir pelo e2e da suíte do guia que a página abre nos dois idiomas.
- [ ] **Step 2:** atualizar as docs.
- [ ] **Step 3:** `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams` → passa; commit (`Tela de Sinal: guia e documentação da etapa 1`).

---

### Task 10: Shift + roda troca o modo do Vinculador (pedido do dono em 27/10)

O Shift + clique no ar para trocar o modo do Vinculador (Vincular ↔ Abrir) é ruim de usar. Passa a ser **Shift + roda do mouse com o Vinculador na mão principal**, como o Configurador já faz para o tipo colado. O Shift + clique no ar deixa de trocar o modo e faz o mesmo que o clique comum (Vincular: abre a tela; Abrir: a dica na action bar). Os gestos de Shift + clique em blocos (marcar a área) não mudam.

**Files:**
- Create: `packet/CycleLinkerModePayload.java` (`record CycleLinkerModePayload(int direction)`, `playToServer`, mesmo formato do `CycleConfiguratorTypePayload`)
- Modify: `client/ConfiguratorScrollHandler.java` (com o Vinculador na mão principal manda o `CycleLinkerModePayload`; renomeie a classe para `client/HeldScrollHandler.java` e ajuste o javadoc, porque passa a servir aos dois itens), `item/LinkerItem.java` (o `use` sem a troca de modo; o tooltip com o gesto novo), `packet/ModPayloads.java` (registro e `handleCycleLinkerMode(ServerPlayer, CycleLinkerModePayload)`, público para os testes: só age com o Vinculador na mão principal e `direction != 0`; troca para o próximo ou o anterior pelo `LinkerMode`, grava pelo `LinkerItem.setMode` e mostra o modo na action bar com a mesma chave de hoje; devolve se trocou), `lang` (o tooltip e a dica do gesto, nos dois idiomas), `scripts/guide/gerar_guia.py` e as páginas geradas (a página do Vinculador e onde mais o gesto aparece), `client/dev/E2eLinkerSteps.java` se ele troca o modo por Shift + clique no ar, `CLAUDE.md` (a linha do `linker/` e do `client/`)
- Test: `gametest/LinkerModeScrollGameTests.java` (ou junto dos GameTests do Vinculador que já existem): troca com o Vinculador na mão principal, nas duas direções; não troca com `direction` 0, com outro item, com o Vinculador só na secundária; o Shift + clique no ar (`LinkerItem.use` com o jogador agachado) não muda mais o modo.

**Protocolo:** o pacote novo entra no mesmo `ModPayloads.VERSION = "35"` desta etapa (a Tarefa 5 já subiu; não suba de novo).

**Custo:** nenhum: o evento da roda no cliente já existe e sai cedo sem Shift; um pacote por clique da roda.

- [ ] **Step 1:** GameTests (falham). **Step 2:** implementar. **Step 3:** `./gradlew build runGameTestServer` e o e2e da suíte do Vinculador (`./scripts/e2e.sh --only vinculador`; confira o nome em `--lista`). **Step 4:** varreduras e commit (`Vinculador: Shift + roda troca o modo`).

---

## Autorrevisão (feita ao escrever)

- **Cobertura da spec (etapa 1):** bloco contra a parede e sumir sem ela (T3); tamanho 1×1 ao teto, o "cresce para", a validação (T1, T4, T6, T8); área desenhada sem blocos (T7); o layout com Valor, Texto e Forma (T2); dados das redes com o acesso do dono e "—" (T5); texto que cabe (T1, T7); textura por Tela, redesenho só com mudança, até 4 por quadro, liberar depois de 200 ticks (T7); tetos e config (T3); quem edita (T3, T6); receita (T3); protocolo 35 (T5); guia (T9). As decisões 5 a 16 da spec que não estão aqui são das etapas 2 a 4.
- **Nomes conferidos entre tarefas:** `TvArea.grown/grow/rightOf/upOf/pixelWidth`, `TvLayout.bindings/bindingIndex/clampedTo/EMPTY/CODEC`, `TvMetric.read`, `TvUnit.perSecond`, `TvBlockEntity.area/layout/revision/setArea/setLayout/canEdit/cellPos/renderBox`, `TvLoaded.of`, `TvAreaCheck.check/Problem.Kind`, `TvClientHooks.changed/values`, `TvValuesPayload(pos, revision, values)`, `TvCanvases.markDirty/texture/onFrame/onClientTick`.
- **A conferir pelo implementador no código (podem ter outro nome):** o leitor de rede do `NetworkSavedData`, os campos restantes do `FlowSampler.Report`, o jeito de dar permissão de operador num jogador de teste, a API de `TextureTarget`/`RenderSystem` do 1.21.1.
