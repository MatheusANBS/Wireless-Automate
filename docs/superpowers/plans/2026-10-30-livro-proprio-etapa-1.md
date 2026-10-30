# Livro próprio, etapa 1: o livro inteiro sem o GuideME

Spec: `docs/superpowers/specs/2026-10-30-livro-proprio-design.md`. Mockup aprovado: `docs/preview/livro-proprio.html`. Protótipos das texturas (PIL) em `docs/preview/livro-prototipo/` (`livro_proto.py`: o livro aberto e as fitas; `guia_proto.py`: o ícone, com a grade `GUIA_NOVO`); a Tarefa 2 os traz para o `scripts/textures/`.

Ramo: `livro-proprio`. Uma tarefa por vez, um implementador por tarefa, revisão de cada uma antes da seguinte.

## Restrições globais

- Código, comentários, commits e docs em português; traduções em `en_us.json` e `pt_br.json`, sempre as duas.
- **Lado do cliente:** tudo de leitura e desenho do livro em `client/guide/`; o código comum (item, receita, login) **nunca** referencia `client/`. O item abre o livro pelo gancho `GuideClientHooks` (padrão do `tv/TvClientHooks`). O `runGameTestServer` é servidor dedicado e quebra se uma classe de tela carregar.
- **Lógica pura sem Minecraft com JUnit** (`src/test/java`): o parser (`client/guide/doc/`), a paginação (`client/guide/layout/`) e a busca. A medida de texto entra por uma interface (`TextMeasure`), com uma implementação do `Font` no cliente e uma falsa no JUnit.
- **JEI é opcional:** tipos do JEI só em `compat/jei/`.
- Chaves existentes de NBT, componentes e config **não mudam** (`guide.giveOnFirstJoin` fica, só muda o comentário). Marca nova no NBT persistente do jogador: `wirelessautomate:guide_v2_given`. O id do item é `wirelessautomate:guide` (o modelo `models/item/guide.json` e a textura `item/guide` já existem); as chaves `item.wirelessautomate.guide` e `.tooltip` ficam.
- `ModPayloads.VERSION` continua `"40"`: o livro não tem pacote.
- **Performance** (skill `wa-performance`, leis 1, 3, 8 e 10): servidor com custo zero por tick; o livro lê as páginas uma vez por idioma e pagina uma vez por página; **por quadro só desenhar** (nada de `font.width`, `split`, montagem ou ordenação no `render`, nem alocação no laço de desenho); só as duas folhas à vista; a cena parada montada uma vez. Antes do commit: `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos mudados, cada candidato resolvido ou justificado no relatório.
- **Tamanho** (skill `wa-modular`): tela e componente até 500 linhas (aviso 600, dividir 800); `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados.
- **Comentários** (skill `wa-comentarios`): `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados.
- **Texto de tela** pelo `GuiText` quando é variável e pode não caber; o e2e confere `GuiText.clipCount() == 0`.
- **Verificação de cada tarefa:** `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams`. Se mexeu em tela: `./scripts/e2e.sh --only guia` (a partir da Tarefa 5). Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, pare e conserte, nunca espere o `result.txt`. Um jogo ou Gradle por vez (~3 GB cada). Não mexa em `run/options.txt`.
- **Commits** sem linhas de atribuição, com o autor do dono; sem push.

## Mapa de arquivos

| Arquivo | Papel |
| --- | --- |
| `scripts/guide/guia_base.py`, `gerar_guia.py`, `paginas/*.py` (mudar) | Destino novo, `group:` no cabeçalho, textos sem o GuideME |
| `assets/wirelessautomate/guide/{en_us,pt_br}/*.md`, `guide/images/` (gerados) | As páginas no lugar novo |
| `assets/wirelessautomate/guides/`, `guideme_guides/` (apagar) | O formato do GuideME |
| `item/GuideBookItem.java`, `GuideClientHooks.java` (novos), `item/GuideBook.java`, `registry/ModItems.java`, `registry/ModCreativeTabs.java`, `Config.java` (mudar) | O item, o gancho, a entrega no login |
| `data/wirelessautomate/recipe/guide.json` (mudar) | Livro + redstone → o nosso item, sem condição |
| `scripts/textures/livro.py` (novo), `gerar_texturas.py` (mudar) | O livro aberto, as fitas e o ícone novo |
| `client/guide/doc/*` (novos, puros) | `GuideDoc`, blocos, trechos, `GuideParser` |
| `client/guide/layout/*` (novos, puros) | `TextMeasure`, `Sheet`, `SheetOp`, `GuideLayout` |
| `client/guide/*` (novos) | `GuidePages`, `GuideScreen`, `GuidePainter`, `GuideRecipes`, `GuideScenes`, `GuideSearch`, `GuideKeys`, `GuideIndex` |
| `compat/jei/` (mudar) | O item do JEI sob o mouse, para a tecla G |
| `client/ClientSetup.java` (mudar) | Preenche o gancho, a tecla e o recarregar de recursos |
| `client/dev/E2eGuideSteps.java`, `E2eSuites.java`, `E2eShowcase.java`, `E2eShowcaseWorld.java` (mudar) | O e2e e a vitrine sem o `/guidemec` |
| `build.gradle`, `src/main/templates/META-INF/neoforge.mods.toml` (mudar) | Sai o GuideME |
| `gametest/GuideGameTests.java` (novo), `RecipeEditorGameTests.java` (mudar) | A receita e a entrega |
| `src/test/java/.../client/guide/**` (novos), `ResonatorGuideSceneTest.java` (mudar) | JUnit |
| Docs: `docs/mapa/livro.md` (novo), `CLAUDE.md`, `README*.md`, `docs/curseforge/descricao.md`, `docs/especificacao.md`, `docs/e2e.md`, `docs/mapa/scripts-e-recursos.md`, skill `wa-porte` (mudar) | Documentação |

---

### Task 1: as páginas no formato novo

O gerador escreve em `assets/wirelessautomate/guide/en_us/` e `.../pt_br/` (sai o `guides/wirelessautomate/guide/` e o `_pt_br/`), e as imagens das portas em `assets/wirelessautomate/guide/images/`. O `front()` ganha `group` (obrigatório, um dos sete da spec: `comecar`, `ferramentas`, `armazenamentos`, `energia`, `painel`, `integracoes`, `referencia`), e cada página diz o seu grupo. Apaga `assets/wirelessautomate/guideme_guides/`. Textos das páginas que citam o GuideME ou o `/guidemec` (a "Este guia" da `index`, o docstring do `guia_base.py`) passam a falar do livro (receita livro + redstone sempre). O `ResonatorGuideSceneTest` lê o caminho novo.

- **Feito quando:** `python -I scripts/guide/gerar_guia.py` gera 25 páginas em cada idioma no lugar novo, com `group:` válido; JUnit verde; nada mais em `guides/` nem `guideme_guides/`.
- **Custo:** nenhum no jogo (só arquivos).

### Task 2: o item, a receita, a entrega e as texturas

- `GuideBookItem` (pilha de 1, raridade comum, tooltip da chave existente); no `use`, no cliente, `GuideClientHooks.open.accept(null)` e `InteractionResultHolder.success`; no servidor, `consume`. Registrado em `ModItems` como `guide`; vai na aba criativa (`ModCreativeTabs`, onde hoje entra o `GuideBook.create()`).
- `GuideClientHooks`: `public static volatile Consumer<@Nullable String> open = p -> {};` (o nome da página, como `router.md`, ou `null` para onde parou).
- `GuideBook.create()` devolve o nosso `ItemStack` (sem `Optional`); a entrega no login usa a marca nova `wirelessautomate:guide_v2_given` (quem tinha a antiga `guide_given` recebe o novo uma vez). O comentário da config deixa de falar do GuideME.
- `recipe/guide.json`: `crafting_shapeless`, livro + redstone → `wirelessautomate:guide`, sem `neoforge:conditions`.
- `scripts/textures/livro.py` a partir do protótipo aprovado (`livro()` com capa grafite, `papel()` suave, fitas), chamado pelo `gerar_texturas.py`: `textures/gui/guide/book.png` (300 × 196) e `textures/gui/guide/ribbon_<grupo>.png` e `_on.png`. O `GUIA` do `gerar_texturas.py` vira o livro fechado novo (a grade aprovada, com o Olho de porcelana pulsando em (7, 6), raio 2). Rodar o `gerar_texturas.py` inteiro regrava PNGs com os mesmos pixels: desfaça com o git os que não mudaram de pixel (só o `item/guide.png` e os novos de `gui/guide/` mudam).
- GameTests (`GuideGameTests`): a receita dá o nosso item; o jogador novo recebe um livro no login e só um (logar de novo não dá outro); o jogador com a marca antiga e sem a nova recebe um. O teste do `RecipeEditorGameTests` que dependia do GuideME passa a esperar a receita sempre. O comentário do `TierUpgradeRecipe` deixa de citar o GuideME.
- **Feito quando:** as cinco runs verdes; o item existe, com o ícone novo animado, e o uso no cliente chama o gancho (que ainda não faz nada).
- **Custo:** login: uma leitura e uma escrita de marca no NBT persistente, uma vez por jogador. Nada por tick.

### Task 3: o parser (puro, com JUnit)

`client/guide/doc/`: `GuideDoc` (cabeçalho: título, ícone, pai, posição, grupo, `item_ids`; corpo: lista de blocos) e os blocos `Heading(nível, trechos)`, `Paragraph(trechos)`, `ListBlock(numerada, itens de trechos)`, `Table(cabeçalho, linhas de células de trechos)`, `ItemImage(id, escala, flutua)`, `BlockImage(id, propriedades, escala)`, `Row(filhos)`, `RecipeFor(id do item)`, `Recipe(id da receita)`, `Image(caminho)`, `Scene(blocos com posição e propriedades, anotações, câmera)`, `SubPages`, `Break`. Trechos: texto, negrito, código, link (página, texto), ícone de item em linha (o `item(...)` nas tabelas), item com link (`ItemLink`). `GuideParser.parse(String) → GuideDoc`, tolerante: tag desconhecida vira um aviso na lista `warnings()` do documento e é pulada.

- JUnit: casos pequenos de cada bloco e trecho, e as 25 páginas reais dos dois idiomas (lidas de `src/main/resources`) sem nenhum aviso, com o título e o grupo certos.
- **Custo:** roda uma vez por página e idioma; linear no tamanho do texto, sem regex por caractere.

### Task 4: a paginação (pura, com JUnit)

`client/guide/layout/`: `TextMeasure` (`width(String, estilo)`, `lineHeight()`), `SheetOp` (operação de desenho posicionada: texto com estilo e cor, ícone de item, regra de tabela, link com área, imagem, moldura de receita, moldura de cena), `Sheet` (as operações de uma folha) e `GuideLayout.paginate(GuideDoc, largura, altura, TextMeasure) → List<Sheet>`. Quebra parágrafo e lista por linha (palavras), tabela entre linhas (com o cabeçalho repetido na folha seguinte), e nunca parte receita, imagem, cena ou linha de ícones (`Row`): vão inteiros para a folha seguinte. Título no topo da primeira folha; um título não fica sozinho no pé da folha. As medidas (largura e altura da folha, margens, tamanho de receita e cena) ficam numa classe de constantes, as mesmas da textura.

- JUnit com uma `TextMeasure` falsa (6 px por caractere): quebra de linha, parágrafo partido entre folhas, tabela com cabeçalho repetido, bloco atômico empurrado, título que não fica órfão, e as 25 páginas reais paginando sem folha vazia nem operação fora da folha.
- **Custo:** uma vez por página e idioma (o tamanho é fixo), guardado; nada por quadro.

### Task 5: a tela, as páginas e a navegação

- `GuidePages`: lê as páginas do idioma pelo `ResourceManager` (`guide/<idioma>/*.md`, com o `en_us` como reserva por página), parseia e pagina sob demanda (guardando), monta o índice por grupo (ordenado pela `position`) e o mapa item → página; registrado no recarregar de recursos do cliente (esquece tudo). Aviso de parser vai ao log uma vez por página.
- `GuideScreen` (anfitriã, sem `AbstractContainerScreen`): o livro de 300 × 196 centrado (se não couber na escala do jogo, desenha numa escala menor que caiba), as fitas à direita (`GuideIndex` dá os grupos), a folha de rosto + índice como abertura, a página em pares de folhas, ◀ ▶ e números, "← Índice", o histórico (voltar) e a última página e folha lembradas na sessão (estáticas, soltas no logout). Teclado e mouse da spec (menos busca e G, que vêm depois).
- `GuidePainter`: desenha as `SheetOp` (texto com a cor da tinta da spec: grafite.2, títulos em coral.1, links em coral.1 sublinhados, ornamentos em latão), ícones de item pelo `renderItem`, tabelas, imagens; a dica de um item é o nome dele, e de um link, a página de destino.
- O `ClientSetup` preenche `GuideClientHooks.open` para abrir a `GuideScreen` (na página pedida ou onde parou).
- Receitas e cenas, nesta tarefa, saem como molduras com o nome do que vem (a Tarefa 6 as preenche).
- e2e `guia` refeito (`E2eGuideSteps`): abre o livro pelo gancho, abre cada página e vira todas as folhas, capturas `guia-<pagina>-<n>` e confere `GuiText.clipCount() == 0`; a suíte deixa de depender do GuideME (`E2eSuites`).
- **Feito quando:** o livro abre pelo item, as 25 páginas aparecem e viram, e o e2e `guia` passa; capturas olhadas.
- **Custo:** o medido na Tarefa 4; por quadro, só o laço das operações de duas folhas, sem alocação (reusar o `PoseStack`, nada de `Component` novo por quadro).

### Task 6: receitas e cenas paradas

- `GuideRecipes`: acha a receita pelo id (`Recipe`) ou pelo item de saída (`RecipeFor`, a primeira de bancada do mod com aquele resultado) no `RecipeManager` do cliente; desenha a grade 3 × 3 (sem forma: em ordem), a seta e o resultado, com os ingredientes de várias opções alternando a cada segundo (pelo relógio, sem montar listas por quadro). A `TierUpgradeRecipe` (é uma `ShapedRecipe`) mostra o anel. Receita que não existe (editor de receitas mudou): a moldura diz "receita não encontrada".
- `GuideScenes`: a cena parada no ângulo da `IsometricCamera` da página (ou um padrão), os blocos montados uma vez por cena (estado de bloco pelas propriedades), desenhada dentro da moldura com o `BlockRenderDispatcher`, como o visor 3D do roteador (`MachineView3D`), sem girar e sem anotações.
- e2e: as capturas das páginas com receita e cena (roteador, Baú, Ressonador, Cartões) olhadas.
- **Custo:** receita achada uma vez por página (guardada); cena com os blocos montados uma vez; por quadro, só desenhar a cena da folha à vista.

### Task 7: a busca

`GuideSearch` (puro, com JUnit): normaliza como a `NetworkSearch` (sem acento, sem maiúscula), procura nas palavras do título e do texto, ordena (título antes de corpo, depois a ordem do índice) e devolve o trecho com a posição da palavra. Na folha de rosto: o campo da busca (foco ao digitar ou Ctrl + F), resultados na folha direita, Enter abre o primeiro, ↑ ↓ andam, Esc limpa e depois fecha.

- JUnit: "bau" acha "Baú", título antes de corpo, trecho com a palavra.
- e2e: busca por "bau" com captura.
- **Custo:** o texto normalizado de cada página feito na primeira busca, guardado; cada tecla filtra 25 páginas.

### Task 8: a tecla G

`GuideKeys`: `KeyMapping` "Abrir no livro" (padrão G, categoria do mod). Com uma tela de contêiner aberta, o item sob o mouse (o slot), ou o do JEI (`compat/jei`, pelo `IJeiRuntime` da sobreposição de ingredientes), se tem página (o mapa item → página), abre o livro nela. A dica do item ganha a linha "Segure G para abrir no livro" (cinza), só para itens com página.

- e2e: segurar G sobre o roteador no inventário abre a página `router.md`.
- **Custo:** só quando a tecla é apertada com uma tela aberta; a linha da dica é um `Component` constante por item.

### Task 9: sai o GuideME e a documentação

- Sai a dependência do `neoforge.mods.toml`, o `guideme` do `clientMods` e da lista de jars do `build.gradle`; a vitrine e o mundo da vitrine (`E2eShowcase`, `E2eShowcaseWorld`) passam a usar o nosso item e a nossa tela. `git grep -i guideme` fica só no histórico, nas specs e nos changelogs antigos.
- Docs: `docs/mapa/livro.md` (área nova, com a linha no índice do `CLAUDE.md`), `docs/mapa/scripts-e-recursos.md`, `README.md` e `README.en.md` (a linha do GuideME nas integrações sai; o livro é do mod), `docs/curseforge/descricao.md`, `docs/especificacao.md` (receita do livro, integrações), `docs/e2e.md`, a skill `wa-porte` (o GuideME deixa de ser dependência), `docs/progresso/` (estado, histórico e pendências da etapa 2).
- **Feito quando:** as cinco runs verdes, o e2e completo (`./scripts/e2e.sh`) OK, o `runClient` sem o GuideME abre o livro.

## Revisão final

Revisão do ramo inteiro no modelo mais capaz: aderência à spec, as leis da `wa-performance`, tamanho, comentários, e o livro olhado nas capturas do e2e (texto cortado, item preto, folha vazia).
