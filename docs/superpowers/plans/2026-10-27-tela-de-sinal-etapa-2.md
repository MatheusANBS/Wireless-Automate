# Tela de Sinal, etapa 2 (editor) · plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** o editor do mockup no jogo, no lugar da aba Conteúdo: peças Valor, Texto e Forma soltas, arrastar e redimensionar com grade, escolha múltipla, grupos, camadas, as abas Dado, Formato, Estilo e Regras, atalhos com desfazer e área de transferência, temas, fundo, cinco fontes, e componentes e temas na biblioteca do jogador. Termina jogável e testável: o jogador monta uma Tela do zero, salva, e ela aparece igual na parede.

**Arquitetura:** o modelo comum (`tv/`) ganha campos opcionais (regras, escondida, tema, fundo, fonte, cantos, opacidade), sem mudar as chaves da etapa 1. O editor é cliente (`client/tv/editor/`): um rascunho imutável (`TvLayout`) com as operações puras (`TvEditOps`, `TvEditHistory`, com JUnit), a miniatura pintada pelo mesmo `TvPainter` numa textura própria, e painéis como componentes por geometria (`wa-modular`). Salvar manda o layout inteiro num payload validado pelo `TvEditing`. A biblioteca do jogador é um `SavedData` no overworld, ida ao cliente só com o editor aberto.

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), JUnit 5, GameTests, e2e do cliente.

**Spec:** [`docs/superpowers/specs/2026-10-27-tela-de-sinal-etapa-2-design.md`](../specs/2026-10-27-tela-de-sinal-etapa-2-design.md) (decisões 1 a 18) sobre a spec geral [`2026-10-27-tela-de-sinal-design.md`](../specs/2026-10-27-tela-de-sinal-design.md). **Mockup aprovado:** `docs/preview/tela-de-sinal.html`, seções "2 · Editor" e "Atalhos" (o JavaScript do mockup é a referência do comportamento: escolha, grupo, camadas, colar, regras e temas).

**Ramo:** `tela-de-sinal`, o mesmo da etapa 1. A etapa 1 já está nele (até `d956413f`).

## Decisões deste plano (o dono pode trocar antes da tarefa em que entram)

1. **Fontes de partida:** Silkscreen, VT323, IBM Plex Mono e IBM Plex Sans SemiBold, todas OFL, baixadas do repositório oficial `github.com/google/fonts` (Tarefa 2), com o `OFL.txt` de cada uma ao lado do TTF. Nenhuma outra fonte de terceiro.
2. **Cor do tema dentro do `int`:** `0x0000000N`, N de 1 a 6 (Fundo, Cartão, Texto, Apagado, Alta, Baixa). Mantém as chaves de cor da etapa 1 (`int`) e o NBT pequeno (spec, "Modelo").
3. **O rascunho é um `TvLayout`** (imutável) e cada operação devolve um layout novo; a escolha (índices) fica fora dele, no `TvEditorModel`. O desfazer guarda pares (layout, escolha).
4. **Os componentes prontos ficam no código** (`TvTemplates` vira a fonte deles): "Painel de rede", "Cartão de número" e "Título".
5. **A aba Conteúdo e o `TvTemplatePayload` saem** na Tarefa 6, junto com a chave `editor_soon` e os textos só dela.

## Restrições globais

- Código, comentários, commits e docs em português; textos nos dois `lang` (`en_us.json`, `pt_br.json`), sempre os dois, na mesma ordem.
- **Chaves existentes não mudam.** As da etapa 1 continuam; as novas são as da spec da etapa 2 ("Modelo"), todas opcionais com padrão, para um layout salvo na etapa 1 ler e desenhar igual. Config do servidor, seção `tv`: `maxComponents` (padrão 64) e `maxThemes` (padrão 32). **`ModPayloads.VERSION` sobe de `"35"` para `"36"`** (só na Tarefa 4, a primeira que mexe em payload).
- **Performance (skill `wa-performance`; leia `.claude/skills/wa-performance/SKILL.md`):** nada novo por tick no servidor; salvar e a biblioteca são eventos; nada sai para o cliente com o editor fechado (a biblioteca só com ele aberto); no mundo, nada por quadro além do que a etapa 1 faz: regras, fontes, tema, cantos e opacidade só no redesenho da textura; o piscar redesenha só a Tela com regra de piscar valendo e à vista, 2 vezes por segundo; no editor, a miniatura redesenha no máximo uma vez por quadro e só com o rascunho, os números ou o tema mudados; nada de alocação por quadro no desenho da GUI do editor além do que as telas do mod já fazem (textos medidos guardados até mudarem). Rode `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos mudados e resolva ou justifique cada candidato no relatório.
- Lógica pura com JUnit (`src/test/java`); o que depende do jogo, com GameTest (`@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`, `template = "empty"`, `timeoutTicks` ≤ 300; jogador só por `TestPlayers.inLevel`; cada teste cria a própria rede com `NetworkSavedData.get(server).create(...)`; compare redes com `Objects.equals`; teste que muda a config vai em `batch = "wirelessautomate_config_tv"` com `try/finally`; o `onLoad` só roda no tick seguinte: `startSequence().thenWaitUntil(...)`; testes do mesmo lote rodam em paralelo, então teste pertinência e não contagem global).
- Mod opcional: nada de tipos do Mekanism, Ars, FTB Teams ou Allthemodium em `tv/` e `client/tv/`; os tipos de recurso vêm do `ResourceType` e do `LoadedTypes`.
- Lado do cliente: tudo de tela e render só em `client/` (`client/tv/`, `client/tv/editor/`, `client/tv/render/`), nunca referenciado por código comum.
- Todo texto variável da GUI do editor passa por `GuiText` (o e2e confere `GuiText.clipCount() == 0`); cores da GUI pelo `GuiPaint`, nos dois temas; o botão `ThemeToggle` no cabeçalho. O texto **desenhado na Tela** (na parede e na miniatura) não passa pelo `GuiText`: encolhe pelo `TvFit` e nunca é cortado.
- Componentes de tela não mandam payload direto: tudo sai por uma classe de ações (`TvEditorActions`), que na captura só mexe no estado local.
- Tamanho (`wa-modular`): classe de tela até 500 linhas (alvo 300), servidor até 600; rode `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados. Comentários (`wa-comentarios`): `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados.
- Guia: edite `scripts/guide/gerar_guia.py`, nunca os `.md` gerados. Não rode o `gerar_texturas.py` (reescreve dezenas de PNGs).
- Antes de cada commit: `./gradlew build runGameTestServer` (Git Bash, na raiz); tarefa só de lógica pura roda `./gradlew test --tests "..."`. As runs Chemicals, Source, Allthemodium e Teams ficam para a verificação final do ramo, antes do release (pedido do dono em 27/10, para poupar tempo). Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, pare e conserte, nunca espere um resultado que não vem. Tarefas de tela e render rodam o e2e (`./scripts/e2e.sh --only tela --capture tela`; `run/e2e/result.txt` precisa dizer `OK`), olham as capturas e copiam as novas para `.superpowers/sdd/2026-10-27-tela-de-sinal-etapa-2/capturas/` (o e2e seguinte apaga as de `run/e2e/`). Nunca dois jogos ao mesmo tempo (~3 GB cada); não mexa em `run/options.txt`. Se o e2e de outra suíte falhar por algo fora da tarefa, pare e relate: não entre em loop.
- Commits sem linhas de atribuição (nada de `Co-Authored-By` nem `Claude-Session`), com o autor do dono; `git add` só dos arquivos da tarefa (nunca `run/`, nunca `.superpowers/`). Não faça push.

## Mapa de arquivos

Pacote base: `src/main/java/io/github/matheusanbs/wirelessautomate/`.

| Arquivo | Papel | Tarefa |
| --- | --- | --- |
| `tv/TvColor.java`, `tv/TvTheme.java`, `tv/TvBackground.java`, `tv/TvFont.java`, `tv/TvRule.java`, `tv/TvRuleOp.java`, `tv/TvRules.java` | Puros e codecs: cor do tema ou fixa, os 4 temas de partida, o fundo, a fonte, a regra e a avaliação | 1 |
| `tv/TvPiece.java`, `TvStyle.java`, `TvLayout.java` (mudar) | Os campos novos, opcionais | 1 |
| `assets/wirelessautomate/font/tv_*.json`, `assets/wirelessautomate/font/tv/*.ttf` + `OFL-*.txt`, `client/tv/render/TvFonts.java`, `client/tv/TvFit.java` (mudar) | As quatro fontes TTF e a medida por fonte | 2 |
| `client/tv/render/TvPainter.java` (mudar) e partes novas (`TvPaintStyle`, `TvPaintBackground`), `client/tv/render/TvCanvases.java` (mudar) | Tema, fundo, linhas, cantos, opacidade, fontes, regras e o piscar | 3 |
| `packet/TvLayoutPayload.java`, `tv/TvEditing.java` (mudar), `menu/TvMenu.java` (mudar), `packet/ModPayloads.java` (mudar) | Salvar o layout, validado | 4 |
| `tv/TvLibrary.java`, `tv/TvComponent.java`, `tv/TvTemplates.java` (mudar), `packet/TvLibraryPayload.java`, `packet/TvLibraryActionPayload.java`, `Config.java` (mudar) | A biblioteca do jogador e os componentes prontos | 5 |
| `client/tv/editor/TvEditorModel.java`, `TvEditOps.java`, `TvSnap.java` | Puros: o rascunho e a escolha, as operações (mover, redimensionar, inserir, remover, duplicar, camadas, agrupar), o encaixe | 6 |
| `client/tv/editor/TvEditorTab.java`, `TvEditorLayout.java`, `TvEditorCanvas.java`, `TvPalettePanel.java`, `TvLayersPanel.java`, `TvEditorActions.java`, `client/tv/TvScreen.java` (mudar), `TvScreenLayout.java` (mudar); saem `TvContentTab.java`, `TvScreenActions` (o que for só da Conteúdo), `packet/TvTemplatePayload.java` | A aba Editar: miniatura, escolher, arrastar, redimensionar, paleta, camadas, salvar | 6 |
| `client/tv/editor/TvEditHistory.java`, `TvClipboard.java`, `TvEditorKeys.java` | Desfazer e refazer, a área de transferência, os atalhos | 7 |
| `client/tv/editor/TvPropsPanel.java`, `TvDataTab.java`, `TvFormatTab.java`, `TvStyleTab.java`, `TvRulesTab.java`, `TvColorPicker.java`, `TvNumberField.java` | As abas da direita | 8 |
| `client/tv/editor/TvBaseBar.java`, `TvComponentsPanel.java`, `TvLibraryClient.java` | Tema, fundo e linhas; componentes e temas da biblioteca | 9 |
| `client/dev/E2eTvEditorSteps.java`, `client/dev/E2eTvSteps.java` (mudar), `docs/e2e.md` (mudar), guia, `CLAUDE.md`, `docs/especificacao.md`, `docs/progresso.md` | e2e do editor, guia e docs | 10 |

---

### Task 1: O modelo novo (cores do tema, temas, fundo, fonte, regras)

Custo: nenhum em tempo de jogo; só o codec (lido no carregar e no update tag) e a avaliação pura das regras, chamada no redesenho.

- [ ] `TvColor` (puro, JUnit): `theme(int role)` (1 a 6) → `0x0000000N`; `isTheme(int)`; `role(int)`; `resolve(int color, TvTheme theme)` devolve o ARGB (fixa passa igual). Constantes `BG`, `CARD`, `TEXT`, `MUTED`, `HIGH`, `LOW`. Teste: as seis ida e volta, fixa intocada, `0` (transparente) não é tema.
- [ ] `TvTheme` (record `name`, `bg`, `card`, `text`, `muted`, `high`, `low`, com codec): `color(int role)`; os quatro de partida com as cores do mockup (`THEMES` em `docs/preview/tela-de-sinal.html`): Grafite `#0f1117 #1a1c24 #ECE5D6 #8d8577 #3DB56E #F26A48`, Fósforo `#06100a #0b1a10 #7CFF9E #2f8a4a #B9FFCB #e5ff7c`, Âmbar `#140c02 #211404 #FFB54A #8a5d1d #FFD58A #ff7a3a`, Porcelana `#F2EBDD #E4DAC6 #2A2730 #7A7366 #2F9D5C #D5453A` (todas com alfa FF). Nome cortado em 24 caracteres. O nome dos prontos é a chave de tradução, não o texto (`tv.theme.graphite` etc.).
- [ ] `TvBackground` (record `kind` = `theme` | `solid` | `gradient`, `top`, `bottom`, codec; padrão `theme`).
- [ ] `TvFont` (enum `minecraft`, `pixel`, `terminal`, `mono`, `sans`, `StringRepresentable`, codec; padrão `minecraft`).
- [ ] `TvRuleOp` (`lt`, `gt`, `eq`; `eq` compara com tolerância de 1e-9 relativa) e `TvRule` (record `op`, `value`, `Optional<TvBinding> bind`, `OptionalInt color`, `OptionalInt background`, `blink`, `hide`, codec com campos opcionais); `TvRules` (puro, JUnit): `first(List<TvRule>, DoubleUnaryOperator/valor por ligação)` devolve a primeira que bate ou nenhuma; valor ausente ("—") não bate nenhuma. Teste: ordem, primeira vence, `eq`, sem valor.
- [ ] `TvStyle`: `radius` (0 a 8), `opacity` (0 a 100), `font`, opcionais com padrão; `TvPiece`: `hidden`, `rules` (até 8; o codec corta o excesso), com `with*` para as que o editor usa; `TvLayout`: `theme`, `background`, `scanlines`, e `bindings()` passa a incluir as ligações das regras (na ordem: as das peças, depois as das regras, sem repetir).
- [ ] GameTest no `TvLayoutGameTests`: um NBT da etapa 1 (sem os campos novos) lê igual e escreve sem as chaves novas no padrão; um layout com tudo preenchido faz ida e volta pelo NBT e pelo update tag; regra com 9 itens corta para 8; `bindings()` com regra.
- [ ] `./gradlew build runGameTestServer`; commit "Tela de Sinal: modelo do editor (tema, fundo, fonte e regras)".

### Task 2: As fontes

Custo: quatro TTF no jar (poucas centenas de KB); o atlas das letras é o do vanilla, sob demanda.

- [ ] Baixe de `https://github.com/google/fonts` (ramo `main`): `ofl/silkscreen/Silkscreen-Regular.ttf`, `ofl/vt323/VT323-Regular.ttf`, `ofl/ibmplexmono/IBMPlexMono-Regular.ttf`, `ofl/ibmplexsans/IBMPlexSans-SemiBold.ttf` (se o caminho mudou, procure no repositório), cada um com o `OFL.txt` da pasta, para `src/main/resources/assets/wirelessautomate/font/tv/` (licenças como `OFL-silkscreen.txt` etc.). Baixe numa pasta nova da scratchpad, confira que é TTF (`file` ou os 4 primeiros bytes) e só então copie.
- [ ] Um `font/tv_<nome>.json` por fonte com o provedor `ttf` (`file`, `size`, `oversample`, `shift`), mais o `minecraft:default` como reserva para os caracteres que faltam. Comece com `oversample` 8 e ajuste pelas capturas.
- [ ] `TvFonts` (cliente): o `ResourceLocation` de cada `TvFont` e o `Style` com a fonte; `minecraft` usa o padrão. `TvFit` passa a receber a largura medida na fonte da peça (a assinatura pura não muda: quem chama mede com o `Style` certo). O `TvPainter` desenha com a fonte da peça.
- [ ] e2e: a captura `tela-fontes` (uma Tela 4 × 2 com um Texto em cada fonte, nos tamanhos 8, 16 e 32, perto e a 8 blocos). Olhe a captura: nenhuma fonte borrada a ponto de não ler no tamanho 8 perto; se borrar, suba o `oversample` ou anote no relatório o tamanho mínimo legível.
- [ ] Commit "Tela de Sinal: quatro fontes TTF".

### Task 3: O desenho com tema, fundo, regras e piscar

Custo: só no redesenho. O piscar é o único redesenho por tempo: 2 por segundo, só da Tela à vista com uma regra de piscar que bate; o `TvCanvases` guarda o conjunto dessas Telas (atualizado no redesenho, sem varrer todas por quadro).

- [ ] O `TvPainter` resolve cada cor pelo `TvColor.resolve` com o tema do layout; aplica a opacidade (multiplica o alfa), os cantos (retângulo com cantos cortados em degraus de 1 px de desenho, sem alocar por peça) e pula as peças `hidden`.
- [ ] Fundo: `theme` (a cor Fundo do tema, como a etapa 1), `solid` ou `gradient` (vertical, duas cores); `scanlines`: uma linha de 1 px de desenho a cada 2, preto a 20%, por cima de tudo (como o `.scanl` do mockup).
- [ ] Regras: para cada peça com regras, o valor (o da ligação do Valor, ou a da regra) vem do `TvClientValues`; a primeira que bate troca a cor e o fundo, esconde, ou marca piscar (na fase apagada do piscar, a peça não é desenhada). Quebre o `TvPainter` em partes se passar de 300 linhas (`TvPaintStyle`, `TvPaintBackground`).
- [ ] e2e: as capturas `tela-tema-<tema>` (o Painel de rede nos quatro temas), `tela-fundo-degrade`, `tela-linhas`, `tela-regra` (um Valor vermelho pela regra) e a medida `tela-desempenho` de antes e depois (o redesenho não pode subir mais de 20%).
- [ ] Commit "Tela de Sinal: tema, fundo e regras no desenho".

### Task 4: Salvar o layout

Custo: um evento por salvar (validação proporcional ao layout, uma gravação, um update tag).

- [ ] `TvLayoutPayload(int containerId, TvLayout layout)` cliente → servidor, pelo codec do `TvLayout` em `ByteBufCodecs.fromCodecWithRegistries` ou um codec de stream compacto; recuse acima de 64 KB na leitura.
- [ ] `TvEditing.applyLayout(player, tv, layout)` pela decisão 5 da spec: dono ou operador; tetos; caixa cortada à área; ligação a rede que o jogador não pode usar ou a tipo não carregado sai (a peça fica) e conta para o aviso; sobe a `revision`, `setChanged`, update tag. Devolve o resultado (ok, quantas ligações saíram) para o menu avisar.
- [ ] O handler (`ModPayloads`, como o `handleSize` da etapa 1) valida o `containerId` e o `stillValid` e chama o `applyLayout`; recusa e aviso ("Salvo; 2 ligações saíram: rede sem acesso") vão na action bar pelo `displayClientMessage`, como as recusas do tamanho e do modelo na etapa 1.
- [ ] `ModPayloads.VERSION` = `"36"`; registre o payload.
- [ ] GameTests (`TvEditGameTests`, novo): o dono salva e a revisão sobe; quem não é dono nem operador é recusado; caixa fora da área é cortada; ligação a rede privada alheia sai e o aviso conta 1; acima de `maxElements` é recusado; layout da etapa 1 salvo de novo fica igual.
- [ ] Commit "Tela de Sinal: salvar o layout do editor".

### Task 5: A biblioteca do jogador

Custo: lida só ao abrir o editor e nas ações; nada por tick.

- [ ] `TvComponent` (record `name`, `List<TvPiece> pieces`, codec; nome até 32, peças até `tv.maxElements`, caixas relativas ao canto do componente).
- [ ] `TvLibrary` (`SavedData` no overworld, `wirelessautomate_tv_library`): por UUID, `components` e `themes`; `saveComponent`, `saveTheme`, `rename`, `remove`, com os tetos `tv.maxComponents` (64) e `tv.maxThemes` (32) da config nova; nome repetido substitui.
- [ ] `TvTemplates` vira a fonte dos componentes prontos (decisão 4): "Painel de rede" (o atual, sem rede), "Cartão de número" (Forma cartão `@CARD` com cantos 2, Texto rótulo `@MUTED`, Valor `@TEXT`), "Título" (Texto grande `@TEXT` sobre uma linha `@HIGH`). Nomes pela chave de tradução.
- [ ] `TvLibraryPayload` (servidor → cliente: os componentes e temas do jogador) mandado quando o editor abre (o cliente pede com a primeira ação `OPEN` do `TvLibraryActionPayload`) e depois de cada ação; `TvLibraryActionPayload` (`OPEN`, `SAVE_COMPONENT`, `SAVE_THEME`, `RENAME`, `REMOVE`, com o tipo, o nome e o conteúdo), só com o `TvMenu` aberto, no máximo 10 por segundo por jogador.
- [ ] GameTests (`TvLibraryGameTests`): salvar, renomear, remover, os tetos, nome repetido substitui, a ida e volta do NBT, ação sem o menu aberto recusada.
- [ ] Commit "Tela de Sinal: biblioteca de componentes e temas".

### Task 6: A aba Editar (miniatura, escolher, arrastar, paleta, camadas, salvar)

Custo: a miniatura redesenha no máximo uma vez por quadro, só com o rascunho mudado; a textura do editor é liberada ao fechar.

- [ ] Puros, com JUnit: `TvSnap` (encaixe de 4 px, Alt = 1 px, limite à área); `TvEditorModel` (rascunho, escolha como conjunto de índices, grupo em que se entrou, "mudou desde o salvar"); `TvEditOps` (inserir uma peça nova no primeiro lugar livre, mover a escolha, redimensionar pela alça com o mínimo de 4 px, remover, duplicar com deslocamento de 4 px, subir e descer camada, agrupar e desagrupar como no mockup (`bGroup`: o grupo junta as peças na camada da mais alta), escolher por clique com a regra do grupo e dos dois cliques). Testes de cada operação, inclusive nos limites da área e com grupos.
- [ ] `TvScreen`: as abas Tamanho e Editar; a Editar ocupa a janela menos 8 px (mínimo 400 × 240; abaixo disso, só a mensagem pedindo uma escala menor, decisão "Riscos" da spec). Saem a `TvContentTab`, o `TvTemplatePayload` (e o handler e o registro), as chaves de idioma só dela e o `editor_soon`.
- [ ] `TvEditorLayout` (as colunas e áreas pela decisão 2 da spec), `TvEditorTab` (anfitriã: repassa desenho, mouse, roda e teclado às partes), `TvEditorCanvas` (a miniatura numa textura própria pelo `TvPainter`, a grade, as caixas da escolha, a alça, as linhas de bloco, arrastar e redimensionar), `TvPalettePanel` (Valor, Texto e Forma; o clique insere), `TvLayersPanel` (decisão 9 da spec: olho, grupo recolhível, arrastar muda a camada), a barra de botões (Agrupar, Desagrupar, Duplicar, Remover, Salvar; ↶ ↷ e Salvar como componente ficam para as Tarefas 7 e 9) e `TvEditorActions` (o salvar pelo `TvLayoutPayload`; na captura, só o estado local). Fechar com mudanças: o ✕ em dois cliques ("Descartar?"). Layout mudado no servidor com o editor aberto: aviso com Recarregar.
- [ ] e2e (`E2eTvEditorSteps`): abrir o editor, inserir um Valor e um Texto, arrastar, redimensionar, agrupar, salvar, e conferir no block entity que o layout chegou; capturas `tela-editor`, `tela-editor-escuro`, `tela-editor-pt`, `tela-editor-grupo` e `tela-editor-salvo` (a parede depois). `GuiText.clipCount() == 0`.
- [ ] Commit "Tela de Sinal: editor com miniatura, camadas e salvar".

### Task 7: Desfazer, atalhos e área de transferência

Custo: o desfazer guarda referências a layouts imutáveis (até 100); nada por quadro.

- [ ] `TvEditHistory` (puro, JUnit): até 100 estados (layout e escolha), refazer zerado por uma mudança nova, arrastar conta como uma mudança só (do apertar ao soltar). Teste do teto, da ordem e do arrastar.
- [ ] `TvClipboard` (cliente, estático, vale até sair do jogo): copiar e recortar a escolha com posições relativas e grupos; colar no mesmo lugar, ou no primeiro lugar livre que caiba, com grupos novos; nada da área de transferência do sistema. Lógica de colar pura no `TvEditOps`, com JUnit.
- [ ] `TvEditorKeys`: todos os atalhos da decisão 11 da spec geral (Ctrl+Z, Ctrl+Y e Ctrl+Shift+Z, Ctrl+C, X, V, D, A, G, Shift+G, Delete e Backspace, setas com 4 px, Shift 32 px, Alt 1 px, `[` e `]`, Esc, Ctrl+S), nenhum com um campo de texto focado; os botões ↶ ↷ na barra.
- [ ] e2e: os atalhos principais pelo teclado (`E2eUi`), colar numa segunda Tela; captura `tela-editor-colado`.
- [ ] Commit "Tela de Sinal: desfazer, atalhos e copiar e colar".

### Task 8: As abas Dado, Formato, Estilo e Regras

Custo: só GUI; textos medidos guardados até mudarem.

- [ ] `TvPropsPanel` (as quatro abas, a da escolha; escolha de várias: só o que vale para todas, com "Cor de tudo"), `TvDataTab` (a `TvNetworkList` com busca da etapa 1, o tipo e a métrica), `TvFormatTab` (decisão 11 da spec), `TvStyleTab` (decisão 12: posição e tamanho, nome, cores, borda, cantos, opacidade, fonte, tamanho, caber, alinhar, texto), `TvRulesTab` (decisão 13: até 8, ordem com ↑ ↓, ✕, a ligação própria fora do Valor), `TvColorPicker` (os seis papéis do tema, amostras fixas e o campo `#RRGGBB[AA]`), `TvNumberField` (campo numérico com ‹ ›, roda e limites). Toda mudança passa pelo `TvEditOps` (entra no desfazer).
- [ ] e2e: ligar um Valor a uma rede pela aba Dado, trocar formato, cor fixa e do tema, uma regra; salvar e conferir na parede; capturas `tela-editor-dado`, `-formato`, `-estilo`, `-regras` (nos dois temas e em pt pela variante).
- [ ] Commit "Tela de Sinal: abas Dado, Formato, Estilo e Regras".

### Task 9: Tema, fundo, componentes e temas da biblioteca

Custo: a biblioteca vai ao cliente só com o editor aberto.

- [ ] `TvBaseBar` (a barra da base: nome e tamanho, Tema com os quatro de partida e os do jogador, editar as seis cores, "Salvar tema", Fundo do tema, fixo ou degradê, linhas, grade), `TvComponentsPanel` (os prontos e os do jogador; clique insere num grupo novo no primeiro lugar livre; renomear e remover em dois cliques), "Salvar como componente" na barra de botões, `TvLibraryClient` (o último `TvLibraryPayload`, pedido ao abrir o editor). Ligação de componente a rede que o jogador não pode usar: a peça entra e o salvar avisa (Tarefa 4).
- [ ] e2e: salvar um componente, inserir noutra Tela, salvar um tema, trocar o tema; capturas `tela-editor-componentes`, `tela-editor-tema`, `tela-tema-proprio` (a parede).
- [ ] Commit "Tela de Sinal: temas e componentes no editor".

### Task 10: Guia, docs e a verificação da etapa

- [ ] Guia (`scripts/guide/gerar_guia.py`, pt e en): a página `signal-screen` ganha o editor (escolher, arrastar, grupos, camadas, as abas, os atalhos, temas e componentes), só como operar.
- [ ] `CLAUDE.md` (o mapa: `client/tv/editor/`, os modelos novos, a biblioteca, protocolo 36, a contagem de GameTests), `docs/especificacao.md` (a seção da Tela) e `docs/progresso.md` (estado, próximo passo: etapa 3, e o histórico com os números).
- [ ] `./gradlew build runGameTestServer` e o e2e `--only tela --capture tela --variants pt,escuro`; as quatro runs restantes ficam para a verificação final do ramo.
- [ ] Commit "Tela de Sinal: guia e docs da etapa 2".

## Autorrevisão (feita ao escrever)

- **Cobertura da spec:** decisões 1 e 2 (Tarefa 6), 3 (6), 4 (6, aviso e Descartar), 5 (4), 6 (6), 7 (7), 8 (7), 9 (6), 10 (8), 11 (8), 12 (8), 13 (1, 3 e 8), 14 (2), 15 (1 e 9), 16 (3 e 9), 17 (5 e 9), 18 (5 e 9); chaves e protocolo (1, 4 e 5); custo em cada tarefa.
- **Ordem:** o modelo (1) antes do desenho (3) e do editor (6 a 9); salvar (4) antes da aba Editar (6), que o usa; a biblioteca (5) antes dos componentes no editor (9). As fontes (2) são independentes e entram cedo para as capturas das tarefas seguintes já saírem com elas.
- **Sem lacunas:** a etapa 1 deixou os menores no ledger `.superpowers/sdd/2026-10-27-tela-de-sinal-etapa-1/progress.md`; a revisão final do ramo, depois da etapa 2, cobre as duas etapas e as quatro runs restantes.
