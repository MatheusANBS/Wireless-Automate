# Tema claro e escuro (etapa 0) · plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (um subagente por tarefa, revisão entre elas). Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** um botão no cabeçalho de cada tela do mod troca todas as telas entre o tema claro (o de hoje) e um escuro, com a escolha salva na config do cliente.

**Arquitetura:** as cores do `GuiPaint` deixam de ser constantes e passam a vir de uma paleta (`GuiPalette`, pura, com JUnit) escolhida pelo tema (`GuiTheme`). O `ClientConfig` guarda o tema; o `GuiThemes` aplica na carga da config e no clique; o `ThemeToggle` é o botão, chamado por cada tela.

**Tecnologia:** NeoForge 1.21.1, Java 21, `ModConfigSpec` (config do cliente), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-14-tema-claro-escuro-design.md` (paletas, contrastes, telas com botão e o que fica de fora).

## Restrições globais

- Código, comentários, mensagens de commit e docs em português; traduções em `en_us.json` e `pt_br.json`, sempre as duas.
- **O tema claro tem que ficar idêntico ao de hoje, pixel a pixel.** As capturas claras do `DevScreenshot` antes e depois da Tarefa 2 são comparadas.
- **Lado do cliente:** `GuiPalette`, `GuiThemes` e `ThemeToggle` ficam em `client/` e nunca são referenciados por código comum. `GuiTheme` e `ClientConfig` ficam no pacote base e não importam nada de `client/` (o servidor dedicado carrega a classe da config). O `runGameTestServer` é um servidor dedicado e prova isso.
- Nenhum campo guarda cor do tema (nem `static final int X = GuiPaint.Y`, nem campo de instância, nem array): leia `GuiPaint.Y` na hora de desenhar.
- Chaves existentes de NBT, componentes e config do servidor **não mudam**. Config nova só no arquivo do cliente: `config/wirelessautomate-client.toml`, seção `gui`, chave `theme`, valores `LIGHT`/`DARK`, padrão `LIGHT`.
- `ModPayloads.VERSION` continua `"15"` (nenhum payload muda).
- Todo texto variável das telas passa pelo `GuiText`; uma dica cortada com "…" é bug.
- Commits sem linhas de atribuição (`Co-Authored-By`, `Claude-Session`) e com o autor do dono (o `git config` do repositório já está certo).
- Compile antes de rodar jogo (`./gradlew compileJava`); com `BUILD FAILED`, pare e conserte, nunca espere um resultado que não vai chegar. Nunca rode dois jogos ou servidores ao mesmo tempo (~3 GB cada).
- Verificação antes de cada commit (Git Bash, na raiz): `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`. Nas tarefas de tela, também o e2e: `WA_E2E="$PWD/run/e2e" ./gradlew runClient`, com `run/e2e/result.txt` dizendo `OK`. Não mexa em `run/options.txt`.
- Ramo: `tema-escuro` (já criado a partir do `main`). Push só quando o dono pedir.

## Mapa de arquivos

| Arquivo | O que muda |
| --- | --- |
| `src/main/java/io/github/matheusanbs/wirelessautomate/GuiTheme.java` | Novo. Enum `LIGHT`, `DARK`, `other()` |
| `src/main/java/io/github/matheusanbs/wirelessautomate/client/GuiPalette.java` | Novo. Record com as cores de um tema, `of(GuiTheme)`, `contrast(int, int)` |
| `src/test/java/io/github/matheusanbs/wirelessautomate/client/GuiPaletteTest.java` | Novo. JUnit da paleta |
| `client/GuiPaint.java` | Tokens viram `public static int`, `apply(GuiPalette)`, `alpha(int, int)`, token novo `RULE_TEXT` |
| `client/RouterScreen.java`, `StorageListScreen.java`, `MachineView3D.java`, `FilterScreen.java`, `LinkerScreen.java`, `TabletScreen.java`, `StorageScalarScreen.java`, `AreaRenderer.java` (e qualquer outro que a busca achar) | Sem cor do tema guardada em campo; véus calculados do token |
| `src/main/java/io/github/matheusanbs/wirelessautomate/ClientConfig.java` | Novo. Config do cliente com `gui.theme` |
| `WirelessAutomate.java` | Registra o `ClientConfig` como `ModConfig.Type.CLIENT` |
| `client/GuiThemes.java` | Novo. Tema atual, `set`, `toggle`, aplica na carga e recarga da config |
| `client/ThemeToggle.java` | Novo. O botão: desenho, clique e dica |
| `client/RouterScreen.java`, `TabletScreen.java`, `LinkerScreen.java`, `FilterScreen.java`, `StorageListScreen.java`, `StorageScalarScreen.java`, `RecipeEditorScreen.java` | O botão no canto direito do cabeçalho |
| `assets/wirelessautomate/lang/en_us.json`, `pt_br.json` | `gui.wirelessautomate.theme.dark`, `.light`, `.hint` |
| `client/DevEndToEnd.java` | Volta no escuro com as capturas `escuro-*` |
| `scripts/guide/gerar_guia.py` (e os `.md` gerados) | Uma linha em `getting-started.md` |
| `docs/especificacao.md`, `docs/identidade-visual.md`, `CLAUDE.md`, `docs/progresso.md` | Docs |

---

### Tarefa 1: tema e paleta, lógica pura

**Arquivos:**
- Criar: `src/main/java/io/github/matheusanbs/wirelessautomate/GuiTheme.java`
- Criar: `src/main/java/io/github/matheusanbs/wirelessautomate/client/GuiPalette.java`
- Teste: `src/test/java/io/github/matheusanbs/wirelessautomate/client/GuiPaletteTest.java`

**Interfaces:**
- Produz: `enum GuiTheme { LIGHT, DARK; GuiTheme other() }`; `record GuiPalette(int panel, int bevelLight, int inset, int bevelDark, int rowHover, int fg, int muted, int disabled, int tabIdle, int accent, int accentDeep, int ok, int warn, int danger, int paused, int brass, int ruleText, int tooltipMuted, int darkText, int ring)` com `static GuiPalette of(GuiTheme)`, `static final GuiPalette LIGHT, DARK` e `static double contrast(int argbA, int argbB)`.

- [ ] **Passo 1: escrever o teste que falha**

```java
package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GuiPaletteTest {
    @Test
    void lightIsTodaysPalette() {
        GuiPalette p = GuiPalette.of(GuiTheme.LIGHT);
        assertEquals(0xFFF2EBDD, p.panel());
        assertEquals(0xFFFFFBF3, p.bevelLight());
        assertEquals(0xFFDDD3BF, p.inset());
        assertEquals(0xFFBDB19A, p.bevelDark());
        assertEquals(0xFF2A2730, p.fg());
        assertEquals(0xFF7A7366, p.muted());
        assertEquals(0xFFF0603E, p.accent());
    }

    @Test
    void darkIsGraphite() {
        GuiPalette p = GuiPalette.of(GuiTheme.DARK);
        assertEquals(0xFF26232B, p.panel());
        assertEquals(0xFF312D37, p.bevelLight());
        assertEquals(0xFFECE5D6, p.fg());
        assertEquals(0xFFF26A48, p.accent());
    }

    @Test
    void otherAlternates() {
        assertSame(GuiTheme.DARK, GuiTheme.LIGHT.other());
        assertSame(GuiTheme.LIGHT, GuiTheme.DARK.other());
    }

    @Test
    void contrastOfBlackOnWhiteIs21() {
        assertEquals(21.0, GuiPalette.contrast(0xFF000000, 0xFFFFFFFF), 0.01);
        assertEquals(1.0, GuiPalette.contrast(0xFF777777, 0xFF777777), 0.01);
    }

    @ParameterizedTest
    @EnumSource(GuiTheme.class)
    void everyColorIsOpaque(GuiTheme theme) {
        GuiPalette p = GuiPalette.of(theme);
        int[] all = {p.panel(), p.bevelLight(), p.inset(), p.bevelDark(), p.rowHover(), p.fg(), p.muted(),
                p.disabled(), p.tabIdle(), p.accent(), p.accentDeep(), p.ok(), p.warn(), p.danger(), p.paused(),
                p.brass(), p.ruleText(), p.tooltipMuted(), p.darkText(), p.ring()};
        for (int color : all) {
            assertEquals(0xFF, color >>> 24, Integer.toHexString(color));
        }
    }

    @ParameterizedTest
    @EnumSource(GuiTheme.class)
    void textReadsOnEveryBackground(GuiTheme theme) {
        GuiPalette p = GuiPalette.of(theme);
        assertTrue(GuiPalette.contrast(p.fg(), p.panel()) >= 7, "texto no painel");
        assertTrue(GuiPalette.contrast(p.fg(), p.bevelLight()) >= 7, "texto no botão");
        assertTrue(GuiPalette.contrast(p.fg(), p.inset()) >= 7, "texto na área afundada");
        assertTrue(GuiPalette.contrast(p.muted(), p.panel()) >= 3.5, "texto secundário");
        assertTrue(GuiPalette.contrast(p.tabIdle(), p.panel()) >= 4.5, "aba inativa");
        assertTrue(GuiPalette.contrast(p.ruleText(), p.panel()) >= 4.5, "texto da regra");
        for (int state : new int[] {p.accent(), p.ok(), p.warn(), p.danger()}) {
            assertTrue(GuiPalette.contrast(state, p.panel()) >= 2.2, Integer.toHexString(state));
        }
    }

    @ParameterizedTest
    @EnumSource(GuiTheme.class)
    void textLevelsStayInOrder(GuiTheme theme) {
        GuiPalette p = GuiPalette.of(theme);
        double fg = GuiPalette.contrast(p.fg(), p.panel());
        double muted = GuiPalette.contrast(p.muted(), p.panel());
        double disabled = GuiPalette.contrast(p.disabled(), p.panel());
        assertTrue(fg > muted && muted > disabled && disabled > 1.5, fg + " > " + muted + " > " + disabled);
    }
}
```

Os testes parametrizados não pedem dependência nova: o `org.junit.jupiter:junit-jupiter` do `build.gradle` já traz o `junit-jupiter-params`.

- [ ] **Passo 2: rodar e ver falhar**

Rode: `./gradlew test --tests '*GuiPaletteTest'`
Esperado: falha de compilação (`GuiTheme` e `GuiPalette` não existem).

- [ ] **Passo 3: implementar**

`GuiTheme.java`:

```java
package io.github.matheusanbs.wirelessautomate;

/**
 * Tema das telas do mod, escolhido pelo jogador ({@link ClientConfig#THEME}). Fica no pacote base,
 * sem nada do cliente, porque a classe da config é carregada também no servidor dedicado.
 */
public enum GuiTheme {
    LIGHT,
    DARK;

    /** O outro tema: o que o botão do cabeçalho troca. */
    public GuiTheme other() {
        return this == LIGHT ? DARK : LIGHT;
    }
}
```

`client/GuiPalette.java`:

```java
package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.GuiTheme;

/**
 * As cores de um tema das telas, uma por token do {@link GuiPaint} (spec
 * {@code docs/superpowers/specs/2026-10-14-tema-claro-escuro-design.md}). O claro é a identidade
 * "Porcelana e Sinal" de sempre; o escuro troca a porcelana por grafite quente e mantém o coral.
 * Lógica pura, sem classes do Minecraft, com JUnit.
 */
public record GuiPalette(
        int panel, int bevelLight, int inset, int bevelDark, int rowHover,
        int fg, int muted, int disabled, int tabIdle,
        int accent, int accentDeep, int ok, int warn, int danger, int paused, int brass, int ruleText,
        int tooltipMuted, int darkText, int ring) {

    public static final GuiPalette LIGHT = new GuiPalette(
            0xFFF2EBDD, 0xFFFFFBF3, 0xFFDDD3BF, 0xFFBDB19A, 0xFFF8F3E8,
            0xFF2A2730, 0xFF7A7366, 0xFFB5AC9D, 0xFF5A5660,
            0xFFF0603E, 0xFFC2442A, 0xFF2F9D5C, 0xFFD88A1A, 0xFFD5453A, 0xFF6B6FD0, 0xFFB48E2E, 0xFF5A36B0,
            0xFFBDB19A, 0xFF0F0E12, 0xFF1B1920);

    public static final GuiPalette DARK = new GuiPalette(
            0xFF26232B, 0xFF312D37, 0xFF1C1A20, 0xFF4A4550, 0xFF3A3640,
            0xFFECE5D6, 0xFFA79F90, 0xFF625C66, 0xFF9A9284,
            0xFFF26A48, 0xFFC2442A, 0xFF3DB56E, 0xFFE39A2E, 0xFFE55A4E, 0xFF8A8EE8, 0xFFD2AE4E, 0xFFB79AF5,
            0xFFBDB19A, 0xFF0F0E12, 0xFF1B1920);

    public static GuiPalette of(GuiTheme theme) {
        return theme == GuiTheme.DARK ? DARK : LIGHT;
    }

    /** Contraste entre duas cores opacas, pela fórmula do WCAG 2 (1 a 21). */
    public static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int argb) {
        return 0.2126 * channel(argb >> 16) + 0.7152 * channel(argb >> 8) + 0.0722 * channel(argb);
    }

    private static double channel(int value) {
        double c = (value & 0xFF) / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
```

- [ ] **Passo 4: rodar e ver passar**

Rode: `./gradlew test --tests '*GuiPaletteTest'`
Esperado: todos passam.

- [ ] **Passo 5: commit**

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/GuiTheme.java src/main/java/io/github/matheusanbs/wirelessautomate/client/GuiPalette.java src/test/java/io/github/matheusanbs/wirelessautomate/client/GuiPaletteTest.java
git commit -m "Tema: GuiTheme e GuiPalette (claro e escuro) com JUnit"
```

---

### Tarefa 2: o GuiPaint pela paleta, sem mudar o claro

**Arquivos:**
- Modificar: `client/GuiPaint.java` (linhas 28-84, os tokens)
- Modificar: `client/RouterScreen.java:1555`, `StorageListScreen.java:68-70`, `MachineView3D.java:268-270`, `FilterScreen.java:141-147`, `LinkerScreen.java:64-68`, `TabletScreen.java:73-74`, `StorageScalarScreen.java:33-34`, `AreaRenderer.java:38` e `:63`, e o que a busca do Passo 3 achar

**Interfaces:**
- Consome: `GuiPalette`, `GuiTheme` (Tarefa 1).
- Produz: `GuiPaint.apply(GuiPalette)`; `GuiPaint.alpha(int color, int alpha)`; o token novo `GuiPaint.RULE_TEXT`; todos os tokens de antes com os mesmos nomes, agora `public static int`.

- [ ] **Passo 1: capturas de referência, antes de mexer**

No código ainda sem esta tarefa: `./gradlew compileJava`, depois `WA_SCREENSHOT="$PWD/run/shots-antes" ./gradlew runClient` (no Windows a janela abre e fecha sozinha ao terminar; no Linux, com `xvfb-run -a -s "-screen 0 1280x800x24"` na frente). Confira que `run/shots-antes/` tem as capturas `.png`.

- [ ] **Passo 2: tokens pela paleta**

No `GuiPaint`, troque cada `public static final int TOKEN = 0x...;` (e os aliases como `VIEW_PAPER = BEVEL_LIGHT`) por `public static int TOKEN;`, mantendo o javadoc de cada um, e acrescente o token novo e o método que preenche:

```java
    /** Texto das regras na lista do filtro (roxo da regra, legível no painel). */
    public static int RULE_TEXT;

    static {
        apply(GuiPalette.LIGHT);
    }

    /**
     * Passa as telas para a paleta dada ({@link GuiThemes} chama na carga da config e no botão do
     * tema). As telas leem os tokens a cada quadro, então a troca vale na hora.
     */
    public static void apply(GuiPalette p) {
        PANEL = p.panel();
        BEVEL_LIGHT = p.bevelLight();
        BEVEL_DARK = p.bevelDark();
        INSET = p.inset();
        VIEW_PAPER = BEVEL_LIGHT;
        VIEW_GRID = INSET;
        VIEW_GRID_STRONG = BEVEL_DARK;
        LINE = BEVEL_DARK;
        ROW_SELECTED = BEVEL_LIGHT;
        ROW_HOVER = p.rowHover();
        BUTTON = BEVEL_LIGHT;
        BUTTON_BORDER = p.fg();
        BUTTON_HOVER_BORDER = p.accent();
        SELECTED = BUTTON_BORDER;
        SELECTED_TEXT = BEVEL_LIGHT;
        FG = p.fg();
        MUTED = p.muted();
        DISABLED = p.disabled();
        TOOLTIP_MUTED = p.tooltipMuted();
        TAB_IDLE = p.tabIdle();
        DARK_TEXT = p.darkText();
        RING = p.ring();
        ACCENT = p.accent();
        ACCENT_DEEP = p.accentDeep();
        OK = p.ok();
        WARN = p.warn();
        DANGER = p.danger();
        PAUSED = p.paused();
        BRASS = p.brass();
        RULE_TEXT = p.ruleText();
    }

    /** A cor com outra transparência ({@code alpha} de 0 a 255): os véus seguem o tema. */
    public static int alpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | alpha << 24;
    }
```

O bloco `static` fica **depois** das declarações dos tokens. Atualize o javadoc da classe: as cores vêm do tema (`GuiPalette`), e o claro é o de sempre.

- [ ] **Passo 3: nenhuma cor do tema guardada fora do GuiPaint**

Rode `./gradlew compileJava`: o compilador não acusa nada sozinho, então procure:

```bash
grep -rnE "=\s*GuiPaint\.[A-Z_]+\s*[;&|]" src/main/java
grep -rnE "0x[0-9A-Fa-f]{2}(DDD3BF|BDB19A|F2EBDD|FFFBF3|2A2730|F0603E|7A7366)" src/main/java/io/github/matheusanbs/wirelessautomate/client
```

Para cada achado:
- **Campo que copia um token** (`private static final int ACCENT = GuiPaint.ACCENT;` no `FilterScreen`, `LinkerScreen`, `TabletScreen`, `StorageScalarScreen`; `SEL_ROW`, `HOVER_ROW`, `GOOD`, `UNLINK`, `CHARGING`, `DRAINING` e afins): apague o campo e use `GuiPaint.<TOKEN>` em cada uso. Variável local dentro de um método (`int c = GuiPaint.BEVEL_LIGHT;`) pode ficar.
- **Véu com a cor de um token:** calcule na hora, no lugar de uso:
  - `RouterScreen` `GHOST_VEIL = 0xD8DDD3BF` → `GuiPaint.alpha(GuiPaint.INSET, 0xD8)`
  - `StorageListScreen` `HOVER_VEIL = 0x282A2730` → `GuiPaint.alpha(GuiPaint.FG, 0x28)`; `GHOST_VEIL = 0xC0DDD3BF` → `GuiPaint.alpha(GuiPaint.INSET, 0xC0)`
  - `MachineView3D` `UNAVAILABLE_VEIL = 0x90BDB19A` → `GuiPaint.alpha(GuiPaint.BEVEL_DARK, 0x90)`; `POINTED_VEIL = 0x40F0603E` → `GuiPaint.alpha(GuiPaint.ACCENT, 0x40)`
- `FilterScreen` `RULE_TEXT = 0xFF5A36B0` → `GuiPaint.RULE_TEXT` (o `RULE_COLOR`, roxo de destaque da regra, fica).
- **Cores do mundo, que não seguem o tema:** no `AreaRenderer`, troque `CONFIGURATOR_COLOR = GuiPaint.ACCENT & 0xFFFFFF` por `0xF0603E` e o `LinkerScreen.ACCENT` da linha 63 por uma constante própria `LINKER_COLOR = 0xFFF0603E`, com o comentário "contorno no mundo: não segue o tema das telas".
- Ficam como estão (não imitam o tema): as cores dos tipos (`ResourceStyle`), da energia e da Source (`StorageScalarScreen`), do disco e do miolo da roda (`ConfiguratorWheelScreen`), os textos coloridos das dicas (`RecipeEditorScreen`, sobre a dica escura), o `COUNT_TEXT` branco, as cores de rede (`0xFF000000 | entry.color()`) e o que está no `DevScreenshot` e no `DevEndToEnd`.
- Também confira `FlatButton`, `RangeList`, `ResizeGrip`, `GuiText` e `ConfiguratorWheelScreen`: nenhum campo guarda cor do `GuiPaint`.

- [ ] **Passo 4: compilar e testar**

Rode: `./gradlew build`
Esperado: `BUILD SUCCESSFUL`, JUnit passando.

- [ ] **Passo 5: o claro não mudou**

`WA_SCREENSHOT="$PWD/run/shots-depois" ./gradlew runClient` e compare, com o script abaixo salvo fora do repositório (na pasta temporária da sessão):

```python
import sys
from pathlib import Path
from PIL import Image, ImageChops

antes, depois = Path(sys.argv[1]), Path(sys.argv[2])
diferentes = []
for a in sorted(antes.glob('*.png')):
    b = depois / a.name
    if not b.exists():
        diferentes.append(a.name + ' (faltou)')
        continue
    if ImageChops.difference(Image.open(a).convert('RGB'), Image.open(b).convert('RGB')).getbbox():
        diferentes.append(a.name)
print('iguais' if not diferentes else 'diferentes: ' + ', '.join(diferentes))
```

Rode: `python -I compara.py run/shots-antes run/shots-depois`
Esperado: `iguais`. Se alguma captura com mundo animado (fogo, água, partículas) diferir, abra as duas e confirme que só a animação mudou; diferença na tela é bug desta tarefa.

- [ ] **Passo 6: verificação completa e commit**

Rode a verificação das Restrições globais (build e as quatro runs de GameTest).

```bash
git add -A src/main/java
git commit -m "Tema: GuiPaint pela paleta, sem cor do tema guardada nas telas"
```

---

### Tarefa 3: config do cliente e o botão em todas as telas

**Arquivos:**
- Criar: `src/main/java/io/github/matheusanbs/wirelessautomate/ClientConfig.java`
- Modificar: `src/main/java/io/github/matheusanbs/wirelessautomate/WirelessAutomate.java:50`
- Criar: `client/GuiThemes.java`, `client/ThemeToggle.java`
- Modificar: `client/RouterScreen.java`, `TabletScreen.java`, `LinkerScreen.java`, `FilterScreen.java`, `StorageListScreen.java`, `StorageScalarScreen.java`, `RecipeEditorScreen.java`
- Modificar: `src/main/resources/assets/wirelessautomate/lang/en_us.json`, `pt_br.json`

**Interfaces:**
- Consome: `GuiTheme`, `GuiPalette`, `GuiPaint.apply` (Tarefas 1 e 2).
- Produz: `ClientConfig.SPEC`, `ClientConfig.THEME` (`ModConfigSpec.EnumValue<GuiTheme>`); `GuiThemes.current()`, `GuiThemes.set(GuiTheme)`, `GuiThemes.toggle()`; `ThemeToggle.SIZE` (11), `ThemeToggle.render(GuiGraphics, int x, int y, int mouseX, int mouseY)`, `ThemeToggle.hovered(int x, int y, double mouseX, double mouseY)`, `ThemeToggle.mouseClicked(int x, int y, double mouseX, double mouseY, int button)` e `ThemeToggle.tooltip()` (`List<Component>`).

- [ ] **Passo 1: a config do cliente**

`ClientConfig.java`:

```java
package io.github.matheusanbs.wirelessautomate;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config do cliente ({@code config/wirelessautomate-client.toml}): preferências de cada jogador,
 * que não vão para o servidor. Sem nada do pacote {@code client}: a classe é carregada também no
 * servidor dedicado, que só ignora a config do cliente.
 */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<GuiTheme> THEME;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("gui");
        THEME = builder
                .comment("Tema das telas do mod: LIGHT (claro) ou DARK (escuro). Também troca pelo botão no cabeçalho das telas.")
                .defineEnum("theme", GuiTheme.LIGHT);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
```

No `WirelessAutomate`, logo depois do registro da config do servidor:

```java
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
```

- [ ] **Passo 2: o tema atual**

`client/GuiThemes.java`:

```java
package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.ClientConfig;
import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * O tema das telas no cliente: lido da config na carga (e quando o arquivo muda com o jogo aberto)
 * e trocado pelo botão do cabeçalho ({@link ThemeToggle}), que grava o arquivo. Antes da config
 * carregar, vale o claro.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GuiThemes {
    private static GuiTheme current = GuiTheme.LIGHT;

    private GuiThemes() {
    }

    public static GuiTheme current() {
        return current;
    }

    /** Aplica o tema em todas as telas e grava na config do cliente. */
    public static void set(GuiTheme theme) {
        apply(theme);
        ClientConfig.THEME.set(theme);
        ClientConfig.SPEC.save();
    }

    public static void toggle() {
        set(current.other());
    }

    private static void apply(GuiTheme theme) {
        current = theme;
        GuiPaint.apply(GuiPalette.of(theme));
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ClientConfig.SPEC) {
            apply(ClientConfig.THEME.get());
        }
    }

    @SubscribeEvent
    static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ClientConfig.SPEC) {
            apply(ClientConfig.THEME.get());
        }
    }
}
```

O `ModConfigSpec.save()` e o `defineEnum(String, V)` existem no NeoForge 21.1.248 (conferido nas fontes).

- [ ] **Passo 3: o botão**

`client/ThemeToggle.java`:

```java
package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * O botão do tema, 11 × 11 px no canto direito do cabeçalho de cada tela do mod: uma lua no claro
 * (clicar escurece), um sol no escuro. Desenhado por {@code fill}, sem textura nem caractere de fonte.
 * Cada tela chama {@link #render}, {@link #mouseClicked} e {@link #tooltip} na mesma posição.
 */
public final class ThemeToggle {
    public static final int SIZE = 11;

    private ThemeToggle() {
    }

    public static boolean hovered(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE;
    }

    public static void render(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        boolean hover = hovered(x, y, mouseX, mouseY);
        GuiPaint.box(g, x, y, SIZE, SIZE, GuiPaint.BUTTON, hover ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        if (GuiThemes.current() == GuiTheme.LIGHT) {
            moon(g, x + 3, y + 3, GuiPaint.FG);
        } else {
            sun(g, x + 3, y + 3, GuiPaint.FG);
        }
    }

    /** Clique esquerdo no botão: troca o tema, com o som de botão. Devolve se o clique foi dele. */
    public static boolean mouseClicked(int x, int y, double mouseX, double mouseY, int button) {
        if (button != 0 || !hovered(x, y, mouseX, mouseY)) {
            return false;
        }
        GuiThemes.toggle();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        return true;
    }

    public static List<Component> tooltip() {
        String key = GuiThemes.current() == GuiTheme.LIGHT ? "dark" : "light";
        return List.of(Component.translatable("gui.wirelessautomate.theme." + key),
                Component.translatable("gui.wirelessautomate.theme.hint").withColor(GuiPaint.TOOLTIP_MUTED));
    }

    /** Lua crescente, 5 × 5 px. */
    private static void moon(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y, x + 3, y + 1, color);
        g.fill(x, y + 1, x + 2, y + 4, color);
        g.fill(x + 1, y + 4, x + 3, y + 5, color);
        g.fill(x + 3, y + 3, x + 5, y + 4, color);
        g.fill(x + 3, y + 4, x + 4, y + 5, color);
    }

    /** Sol: miolo 3 × 3 e quatro raios, 5 × 5 px. */
    private static void sun(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y + 1, x + 4, y + 4, color);
        g.fill(x + 2, y, x + 3, y + 1, color);
        g.fill(x + 2, y + 4, x + 3, y + 5, color);
        g.fill(x, y + 2, x + 1, y + 3, color);
        g.fill(x + 4, y + 2, x + 5, y + 3, color);
    }
}
```

Depois de ver as capturas do Passo 6, ajuste o desenho da lua e do sol se não lerem bem em 11 px (o resto do arquivo não muda).

- [ ] **Passo 4: traduções**

`pt_br.json`:

```json
  "gui.wirelessautomate.theme.dark": "Modo escuro",
  "gui.wirelessautomate.theme.light": "Modo claro",
  "gui.wirelessautomate.theme.hint": "Vale para todas as telas do mod",
```

`en_us.json`:

```json
  "gui.wirelessautomate.theme.dark": "Dark mode",
  "gui.wirelessautomate.theme.light": "Light mode",
  "gui.wirelessautomate.theme.hint": "Applies to every screen of the mod",
```

Ponha junto das outras chaves `gui.wirelessautomate.*` gerais, na mesma posição nos dois arquivos.

- [ ] **Passo 5: o botão em cada tela**

Nas sete telas (`RouterScreen` também quando aberta à distância, `TabletScreen`, `LinkerScreen`, `FilterScreen`, `StorageListScreen`, `StorageScalarScreen`, `RecipeEditorScreen`):

1. Posição: `x = leftPos + imageWidth - 4 - ThemeToggle.SIZE`, `y = topPos + 6` (ajuste o `y` para alinhar ao meio da linha do título da tela; use as variáveis de posição e largura que a tela já usa, inclusive nas redimensionáveis, onde a largura muda).
2. O que hoje é desenhado alinhado à direita na linha do título (vazão, tier, contagem de nós, slots do cabeçalho do roteador) recua `ThemeToggle.SIZE + 4` px para a esquerda. Confira a dica de cada um desses elementos depois do recuo (a área de mouse também recua).
3. `render`: `ThemeToggle.render(g, x, y, mouseX, mouseY)` depois do fundo e antes das dicas.
4. `mouseClicked`: no começo, `if (ThemeToggle.mouseClicked(x, y, mouseX, mouseY, button)) return true;`.
5. Dica: quando `ThemeToggle.hovered(...)`, mostre `ThemeToggle.tooltip()` pelo mesmo caminho de dica que a tela já usa (`setTooltipForNextRenderPass` ou `renderComponentTooltip`).
6. Se a tela guarda cores em widgets criados no `init` (um `FlatButton` com cor fixa, por exemplo), confira que eles leem o `GuiPaint` a cada quadro; com o tema trocado, a tela aberta tem que mudar inteira na hora.

- [ ] **Passo 6: conferir no jogo**

`./gradlew compileJava` e depois `./gradlew runClient`: abra cada uma das sete telas, troque o tema pelo botão em cada uma, feche e reabra o jogo e confirme que o tema voltou como ficou. Confira `run/config/wirelessautomate-client.toml` com `theme = "DARK"`. Tire uma captura (F2) de cada tela no escuro para a revisão. Também abra a roda do Configurador (Alt esquerdo com ele na mão) no escuro.

- [ ] **Passo 7: verificação completa e commit**

Rode a verificação das Restrições globais, com o e2e (a volta clara do e2e tem que continuar `OK`).

```bash
git add -A src/main
git commit -m "Tema: config do cliente e o botão claro/escuro em todas as telas"
```

---

### Tarefa 4: e2e no escuro, guia e docs

**Arquivos:**
- Modificar: `client/DevEndToEnd.java`
- Modificar: `scripts/guide/gerar_guia.py` (página `getting-started.md`, linha 380 em diante) e os `.md` gerados
- Modificar: `docs/especificacao.md` (seção "Telas da interface", linha 52), `docs/identidade-visual.md`, `CLAUDE.md`, `docs/progresso.md`

**Interfaces:**
- Consome: `GuiThemes.current()`, `GuiThemes.set(GuiTheme)`, `ThemeToggle.SIZE` e a posição do botão na tela do roteador (Tarefa 3).

- [ ] **Passo 1: a volta no escuro do e2e**

No `DevEndToEnd`, depois da volta normal e antes do encerramento, uma volta nova no mesmo estilo dos passos que já existem:

1. Com a tela do roteador aberta, clica no botão do tema pela posição dele (o mesmo clique de mouse que os outros passos simulam) e confere `GuiThemes.current() == GuiTheme.DARK` e `ClientConfig.THEME.get() == GuiTheme.DARK`.
2. Captura `escuro-roteador`, `escuro-tablet`, `escuro-filtro`, `escuro-vinculador`, `escuro-bau`, `escuro-bateria` e `escuro-receitas`, abrindo cada tela pelo mesmo caminho que a volta normal já usa, e confere `GuiText.clipCount() == 0` em cada uma.
3. Clica de novo no botão e confere a volta para `GuiTheme.LIGHT`.
4. Num `finally` (ou no passo de falha), devolve o tema ao `LIGHT` por `GuiThemes.set(GuiTheme.LIGHT)`, para um e2e que falhou não deixar o cliente de dev escuro.

- [ ] **Passo 2: rodar o e2e e olhar as capturas**

Rode: `WA_E2E="$PWD/run/e2e" ./gradlew runClient`
Esperado: `run/e2e/result.txt` com `OK`. Abra as sete `escuro-*.png` e procure: texto ilegível, fundo claro esquecido (algum véu ou caixa que não seguiu o tema), borda sumida, dica cortada. Corrija o que achar no arquivo da tela e rode de novo.

- [ ] **Passo 3: guia**

No `gerar_guia.py`, na página `getting-started.md`, nos dois idiomas, uma frase sobre o botão:
- pt: "As telas do mod têm um botão com uma lua no canto de cima, à direita: ele troca todas para o modo escuro (e o sol volta ao claro)."
- en: "Every screen of the mod has a moon button in the top-right corner: it switches all of them to dark mode (the sun switches back)."

Rode `python scripts/guide/gerar_guia.py` e confira que só a página `getting-started.md` mudou nos dois idiomas (`git diff --stat`).

- [ ] **Passo 4: docs**

- `docs/especificacao.md`, "Telas da interface": um parágrafo sobre o tema (botão no cabeçalho de todas as telas, vale para todas, salvo na config do cliente, padrão claro).
- `docs/identidade-visual.md`: na seção da interface, a paleta escura (a tabela da spec).
- `CLAUDE.md`, mapa do código: `GuiTheme` e `ClientConfig` no pacote base; em `client/`, `GuiPalette`, `GuiThemes` e `ThemeToggle`; `GuiPalette` na lista de lógica pura com JUnit; na linha do `Config.java`, que a config do cliente é o `ClientConfig`.
- `docs/progresso.md`: etapa atual, uma linha na tabela de telas, o próximo passo (etapa 1: o painel do nó na tela do roteador e no Tablet, com copiar e colar; mockup em `docs/preview/tablet-painel.html` e mapa em `docs/preview/mapa-do-fluxo.html`) e uma linha no histórico com os números de testes e "e2e OK".

- [ ] **Passo 5: verificação completa e commit**

Rode a verificação das Restrições globais, com o e2e.

```bash
git add -A src/main scripts/guide docs CLAUDE.md
git commit -m "Tema: volta no escuro do e2e, guia e docs"
```
