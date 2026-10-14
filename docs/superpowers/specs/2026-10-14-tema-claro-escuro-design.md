# Tema claro e escuro (etapa 0 da remodelagem das telas)

Data: 14/10/2026. Aprovado pelo dono no mapa do fluxo (`docs/preview/mapa-do-fluxo.html`, decisão D7).

## Contexto

A remodelagem das telas, que começou com o pedido do taccio3 no CurseForge (um painel estilo XNet para copiar e colar perfis), foi dividida em três etapas: **0** (esta, tema claro e escuro), **1** (o painel do nó, na tela do roteador e no Tablet, com copiar e colar) e **2** (uma regra só para redes, Mover, Vinculador e perfis). O tema vem primeiro porque é pequeno e o painel novo já nasce com as cores do tema.

## O que o jogador vê

- Um botão de 11 × 11 px no canto direito do cabeçalho de cada tela do mod, com uma lua no tema claro e um sol no escuro. Clicar troca o tema na hora, sem reabrir a tela. A dica diz "Modo escuro" ou "Modo claro".
- Telas com o botão: roteador (também aberto à distância), Tablet, Vinculador, filtro, lista do Baú e dos Tanques, Bateria e Tanque de Source, e editor de receitas.
- A roda do Configurador não tem cabeçalho nem botão. Ela segue o tema onde já usa as cores do `GuiPaint`; o disco escuro translúcido continua igual nos dois temas.
- A escolha vale para todas as telas de uma vez e fica salva na config do cliente (`config/wirelessautomate-client.toml`, `gui.theme`, `LIGHT` ou `DARK`, padrão `LIGHT`). Editar o arquivo com o jogo aberto também aplica.
- Não mudam: itens, fluidos, químicos e o visor 3D (só o papel quadriculado atrás acompanha o tema); as dicas do jogo (já são escuras); o livro-guia do GuideME, o JEI e as telas do Minecraft; as cores dos modos, dos tipos, dos tiers e das redes; o contorno da área do Vinculador e do Configurador no mundo.

## Paletas (ARGB)

| Token do `GuiPaint` | Claro (hoje) | Escuro |
| --- | --- | --- |
| `PANEL` | `0xFFF2EBDD` | `0xFF26232B` |
| `BEVEL_LIGHT` (botão, papel, linha escolhida) | `0xFFFFFBF3` | `0xFF312D37` |
| `INSET` (áreas afundadas, slots) | `0xFFDDD3BF` | `0xFF1C1A20` |
| `BEVEL_DARK` (linhas e sombra) | `0xFFBDB19A` | `0xFF4A4550` |
| `ROW_HOVER` | `0xFFF8F3E8` | `0xFF3A3640` |
| `FG`, `BUTTON_BORDER`, `SELECTED` | `0xFF2A2730` | `0xFFECE5D6` |
| `MUTED` | `0xFF7A7366` | `0xFFA79F90` |
| `DISABLED` | `0xFFB5AC9D` | `0xFF625C66` |
| `TAB_IDLE` | `0xFF5A5660` | `0xFF9A9284` |
| `ACCENT`, `BUTTON_HOVER_BORDER` | `0xFFF0603E` | `0xFFF26A48` |
| `ACCENT_DEEP` | `0xFFC2442A` | `0xFFC2442A` |
| `OK` | `0xFF2F9D5C` | `0xFF3DB56E` |
| `WARN` | `0xFFD88A1A` | `0xFFE39A2E` |
| `DANGER` | `0xFFD5453A` | `0xFFE55A4E` |
| `PAUSED` | `0xFF6B6FD0` | `0xFF8A8EE8` |
| `BRASS` | `0xFFB48E2E` | `0xFFD2AE4E` |
| `RULE_TEXT` (novo; texto das regras no filtro) | `0xFF5A36B0` | `0xFFB79AF5` |

Derivados, nos dois temas: `VIEW_PAPER = BEVEL_LIGHT`, `VIEW_GRID = INSET`, `VIEW_GRID_STRONG = BEVEL_DARK`, `LINE = BEVEL_DARK`, `ROW_SELECTED = BEVEL_LIGHT`, `BUTTON = BEVEL_LIGHT`, `SELECTED_TEXT = BEVEL_LIGHT`.

Iguais nos dois temas: `TOOLTIP_MUTED` (`0xFFBDB19A`, as dicas são escuras), `DARK_TEXT` (`0xFF0F0E12`, texto sobre cor), `RING` (`0xFF1B1920`).

Contraste mínimo (WCAG, contra `PANEL`), conferido por JUnit nos dois temas: `FG` ≥ 7 (também contra `BEVEL_LIGHT` e `INSET`); `MUTED` ≥ 3,5; `TAB_IDLE` e `RULE_TEXT` ≥ 4,5; `ACCENT`, `OK`, `WARN` e `DANGER` ≥ 2,2; e a ordem `FG` > `MUTED` > `DISABLED` > 1,5.

## Arquitetura

- `GuiTheme` (enum `LIGHT`, `DARK`, com `other()`, no pacote base; ver o `ClientConfig` abaixo) e `client/GuiPalette` (record com uma cor por token, `GuiPalette.of(GuiTheme)` e `contrast(int, int)`): lógica pura, sem classes do Minecraft, com JUnit (`GuiPaletteTest`).
- `GuiPaint`: os tokens deixam de ser `static final` e passam a `public static int`, preenchidos por `GuiPaint.apply(GuiPalette)`; o bloco estático aplica o tema claro, então nada muda para quem não troca. Nenhum outro arquivo guarda cor do tema em campo (`static final int ACCENT = GuiPaint.ACCENT` e afins viram leitura direta na hora de desenhar). As cores soltas que imitam o tema (véus com a cor do `INSET`, do `BEVEL_DARK` ou do `FG` com transparência) passam a ser calculadas do token na hora.
- `ClientConfig` (pacote base, só `ModConfigSpec`, sem classes de cliente): seção `gui`, valor `theme` (enum `GuiTheme`, padrão `LIGHT`), comentário `Tema das telas do mod: LIGHT (claro) ou DARK (escuro). Também troca pelo botão no cabeçalho das telas.` Registrado como `ModConfig.Type.CLIENT` no `WirelessAutomate`. O enum fica no pacote base para o `ClientConfig` não referenciar o pacote `client` (o servidor dedicado carrega a classe da config). Por isso `GuiTheme` mora em `io.github.matheusanbs.wirelessautomate.GuiTheme`, não em `client/`.
- `client/GuiThemes`: `current()`, `set(GuiTheme)` (grava na config, salva o arquivo e aplica) e `toggle()`; escuta `ModConfigEvent.Loading` e `Reloading` do `ClientConfig` (só no cliente) e aplica o valor lido.
- `client/ThemeToggle`: o botão (desenho por `fill`, lua e sol em pixel art, sem textura nem caractere de fonte), o clique e a dica. Cada tela chama `ThemeToggle.render`, `ThemeToggle.mouseClicked` e `ThemeToggle.tooltip` na posição do canto direito do cabeçalho; o que hoje fica alinhado à direita no cabeçalho recua para dar lugar ao botão.
- Nada muda no servidor, nos payloads (`ModPayloads.VERSION` continua `15`), no NBT nem na config do servidor.

## Traduções

- `gui.wirelessautomate.theme.dark`: "Modo escuro" / "Dark mode"
- `gui.wirelessautomate.theme.light`: "Modo claro" / "Light mode"
- `gui.wirelessautomate.theme.hint`: "Vale para todas as telas do mod" / "Applies to every screen of the mod"

## Testes

- JUnit: `GuiPaletteTest` (todos os tokens preenchidos e opacos, os contrastes acima, `other()` alterna).
- e2e (`DevEndToEnd`): uma volta no escuro depois da volta normal: clica no botão do tema na tela do roteador, confere `GuiThemes.current() == DARK` e o valor da config, captura `escuro-roteador`, `escuro-tablet`, `escuro-filtro`, `escuro-vinculador`, `escuro-bau`, `escuro-bateria` e `escuro-receitas` com `GuiText.clipCount() == 0`, e volta ao claro pelo botão. As capturas claras de antes não podem mudar.
- GameTests: nenhum novo (é só cliente); as quatro runs continuam passando, provando que o servidor dedicado carrega sem as classes de cliente.

## Guia e docs

- Guia (`scripts/guide/gerar_guia.py`, os dois idiomas): uma linha na página `getting-started.md` sobre o botão do tema no canto das telas.
- `docs/especificacao.md`, "Telas da interface": o tema e o botão.
- `docs/identidade-visual.md`: a paleta escura.
- `CLAUDE.md`: `GuiTheme`, `GuiPalette`, `GuiThemes`, `ThemeToggle` e `ClientConfig` no mapa; `GuiPalette` na lista de lógica pura.
- `docs/progresso.md`: estado, próximo passo (etapa 1, o painel do nó) e histórico.

## Fora desta etapa

- Seguir o tema do sistema operacional ou do resource pack.
- Temas além de claro e escuro, ou cores escolhidas pelo jogador.
- Recolorir os sprites dos modos e dos tipos (já leem bem nos dois fundos).
