# Livro-guia

O livro do mod: uma tela própria de livro aberto, de duas folhas, que lê as páginas do guia, pagina, busca e desenha. Parte do mapa do código (pacote base `src/main/java/io/github/matheusanbs/wirelessautomate/`). Índice em [CLAUDE.md](../../CLAUDE.md). Spec: `docs/superpowers/specs/2026-10-30-livro-proprio-design.md` (mockup `docs/preview/livro-proprio.html`). Não há dependência de outro mod nem pacote de rede: o servidor só tem o item, a receita e a entrega no login.

## Comum: `item/GuideBookItem`, `item/GuideClientHooks`, `item/GuideBook`

- `GuideBookItem` (`wirelessautomate:guide`, pilha de 1): no uso, no cliente, chama `GuideClientHooks.open.accept(null)` (abre onde o jogador parou); o servidor só consome o uso. O tooltip é a chave `item.wirelessautomate.guide.tooltip`.
- `GuideClientHooks`: o gancho que o cliente preenche (padrão do `tv/TvClientHooks`); `open(página ou null)`. O `ClientSetup` põe `GuideScreen::open` nele. O código comum nunca referencia `client/`.
- `GuideBook`: `create()` (o item numa pilha), `deliver(player)` (entrega no inventário, ou no chão se cheio, uma vez) e `given(player)`; a marca no NBT persistente do jogador é `wirelessautomate:guide_v2_given` (a antiga `guide_given`, do livro de antes, não decide mais: quem já tinha o livro antigo recebe o novo uma vez). O `onLogin` só filtra (FakePlayer e `guide.giveOnFirstJoin`) e chama `deliver`.
- Receita (`data/wirelessautomate/recipe/guide.json`): livro + redstone, sem forma e sem condição. Item e aba criativa nos registros (`ModItems`, `ModCreativeTabs`); o ícone é o livro fechado de `textures/item/guide.png` (4 quadros do Olho pulsando).

## `client/guide/doc/` (puro, sem Minecraft, com JUnit)

O modelo e o `GuideParser`: `GuideParser.parse(String)` devolve `GuideDoc(header, blocks, warnings)`.

- `GuideDoc`, `GuideHeader` (`title`, `icon`, `parent`, `position`, `group`, `itemIds`; sem cabeçalho vira `EMPTY` com um aviso), `Block` (`Heading`, `Paragraph`, `ListBlock`, `Table`, `ItemImage`, `BlockImage`, `Row`, `RecipeFor`, `Recipe`, `Image`, `Scene`, `SubPages`, `Break`), `Span` (`Text`, `Bold`, `Code`, `Link`, `ItemIcon`, `ItemLink`, `InlineImage`), `Flow` (o `float`), `SceneBlock`, `SceneNote` (`OnBlock`, `OnLine`), `SceneCamera` (yaw e pitch; padrão 200 e 30).
- `Source` (linhas, cursor e avisos com o número da linha; aceita CRLF e BOM), `HeaderParser` (o YAML simples à mão), `BlockParser` (títulos, parágrafos, listas planas, tabelas, tags de bloco), `TableParser` (divide por `|` fora de código, `\|`), `InlineParser` (uma passada por caractere, sem regex), `SceneParser` (o interior do `GameScene`), `Tag` (`<Nome a="x" b={y} />` e os helpers de atributo).
- Tag desconhecida, fechamento sem abertura, tag sem fechar ou atributo ilegível: aviso e o item é pulado, o resto da página segue. `GuidePagesRealTest` lê as 25 páginas dos dois idiomas, sem nenhum aviso, e confere as contagens do texto-fonte contra as do parser (nada some em silêncio).

## `client/guide/layout/` (puro, com JUnit)

A paginação: `GuideLayout.paginate(GuideDoc, largura, altura, TextMeasure)` devolve a lista de `Sheet`, em **unidades de folha** (232 × 280).

- `GuideMetrics` (as constantes: folha 232 × 280, linha de 9, ícone de 16, receita de 120 × 64, cena de largura × 110, espaços, recuo de lista), `TextMeasure` (a medida do texto, por uma interface: `width(texto, estilo)`, `lineHeight()`, `imageSize(path)`; o cliente usa o `Font` e o JUnit uma falsa de 6 por caractere), `TextStyle` (`NORMAL`, `BOLD`, `CODE`, `LINK`, `TITLE1..3`), `Sheet(ops, usedHeight)`, `SheetOp` (`Text`, `LinkArea`, `Icon`, `BlockIcon`, `Rule`, `Ornament`, `Image`, `RecipeFrame`, `SceneFrame`, `SubPagesMarker`; todas com x, y, w, h).
- `SheetFlow` (o cursor, os espaços, a troca de folha, o flutuante), `TextWrap` e `Line` (a quebra por palavra; palavra mais larga que a linha é cortada por caractere), `Atomic` (receita, cena, imagem, ícone e a fileira de ícones, que vão inteiros para a folha seguinte se não cabem), `TableLayout` (colunas pelo conteúdo, encolhidas sem cortar a palavra mais larga; cabeçalho repetido na folha seguinte).
- Regras: parágrafo e lista partem entre linhas; título pede a primeira linha do bloco seguinte junto, senão vai para a folha seguinte; o título 1 deixa o espaço do ornamento. Roda uma vez por página e idioma.

## `client/guide/` (cliente)

| Classe | O que faz |
| --- | --- |
| `GuideScreen` | A anfitriã (`Screen`): `open(página ou null)` e `openIndex()` (folha de rosto), a escala no `init`, o `refresh()` a cada mudança de lugar (as duas folhas, números, setas, fita), o desenho, as dicas, o mouse (clique, botão lateral 4 = voltar, roda vira as folhas), o teclado (← → viram, Backspace volta, Ctrl + F, Ctrl + V, Enter, ↑ ↓ na busca, Esc limpa a busca e depois fecha) e os ganchos do e2e (`page`, `spread`, `spreads`, `turnTo`, `linkCenter`, `searchText`, `resultCount`, `markedResult`, `visibleSheets`, `problems`). |
| `BookScale` | Pura (JUnit): o `k`, o canto do livro na janela e as contas mouse → pixel da textura → unidade de folha. |
| `GuidePages` | Lê `guide/en_us/*.md` e depois `guide/<idioma>/*.md` por cima (página que falta no idioma cai para `en_us`), parseia, ordena (grupo, raiz antes, `position`, id), monta o mapa item → página (`item_ids`, a primeira pela ordem do índice), as folhas sob demanda (guardadas), `resolve(link)` e `searchPages()`. Tudo num `record Loaded` imutável publicado de uma vez num campo `volatile`; o ouvinte `RELOAD` (recarga de recursos) e `clear()` zeram. Avisos do parser no log, uma vez por página e idioma. |
| `GuideIndex` | O índice como `GuideDoc` virtual (um título por grupo, uma linha por página com ícone e link), paginado pelo `GuideLayout`; a folha onde cada grupo começa, o grupo à vista, o ícone e o nome de cada fita. |
| `GuideNav` | Estático: a página (`null` = índice), o par de folhas e o histórico (até 64). `reset()` ao sair do mundo. |
| `GuideInk` | As cores fixas da tinta e a preparação de uma `Sheet` em tinta pronta (texto com estilo em `FormattedCharSequence`, `ItemStack` com componentes, página do link, textura da imagem, molduras). Montada uma vez por folha, e é onde cada operação é conferida contra a folha e o texto é medido de novo com a fonte de verdade (a lista de `problems()`). |
| `GuidePainter` | Desenha a tinta em unidades de folha (laço por índice, sem alocação), o ornamento, a moldura e o item em escala; acha o link ou o item sob o mouse. |
| `GuideChrome` | Em pixels da textura: o livro, as fitas (a do grupo à vista comprida, com o ícone), as setas do rodapé, os números, o "← Índice"; o que está sob o mouse. |
| `GuideCover` | A folha de rosto: título, subtítulo, o selo com o roteador, a busca (campo desenhado na tinta) e a dica da tecla G; com busca, a contagem e o intervalo. |
| `GuideSearch` | Pura (JUnit): o texto dobrado de cada página (a `NetworkSearch.fold`: sem acento e sem maiúscula), `search` (todas as palavras no título ou no texto, título antes), o trecho de ~40 caracteres com a posição da palavra. |
| `GuideSearchBox` | O texto digitado, o filtro (só quando o texto muda), os resultados em páginas de 8 (a marca é um índice global; ↑ ↓ e ◀ ▶ viram as páginas de resultados) e a tinta deles. |
| `GuideRecipes` | A receita pelo `RecipeManager` do cliente: `<Recipe>` pelo id; `<RecipeFor>` pela receita de bancada do namespace `wirelessautomate` cujo resultado é o item (a de id igual, senão a de menor id); sem bancada, a de alto-forno e depois a de fornalha (o ingrediente no meio, a estação embaixo). Os ingredientes alternam a cada segundo (`opções[segundo % n]`). |
| `GuideScenes` | A cena 3D parada: monta uma vez os estados de bloco (pelo id e pelas propriedades `p:`), o enquadramento pela caixa girada pela câmera, a matriz ortográfica e as luzes; desenha recortada à moldura (`MachineView3D.renderBlock`). Usa o yaw e o pitch da `IsometricCamera` da página como estão; ignora o `zoom` (etapa 2). |
| `GuideKeys` | A tecla `key.wirelessautomate.open_guide` (G, contexto de interface, categoria do mod), o item sob o mouse (slot de contêiner ou a ponte `jeiHovered`), a ponte `jeiTyping` (a busca do JEI com foco) e a linha da dica do item com a tecla (refeita só quando a tecla muda). |
| `GuideClientEvents` | Ao sair do mundo: `GuideNav.reset()` e `GuidePages.clear()`. |
| `FontMeasure` | O `TextMeasure` com o `Font` (o mesmo estilo do desenho) e o tamanho real das imagens (lido do cabeçalho do PNG, guardado por caminho). |

A ponte do JEI da tecla G fica em `compat/jei/WirelessAutomateJeiPlugin.onRuntimeAvailable`, que preenche `GuideKeys.jeiHovered` (o item da lista, dos favoritos ou da tela de receitas) e `jeiTyping`; tipos do JEI só em `compat/jei/`.

## As páginas, a textura e as chaves

- **Páginas:** geradas por `scripts/guide/` (`guia_base.py`, `gerar_guia.py`, `paginas/*.py`; edite os `.py`, não os `.md`) em `assets/wirelessautomate/guide/en_us/`, `guide/pt_br/` e `guide/images/` (o texto usa `images/x.png`, relativo a `guide/`). Cabeçalho: `navigation.title`, `navigation.icon`, `navigation.parent`, `navigation.position`, `item_ids` e `group:` (`comecar`, `ferramentas`, `armazenamentos`, `energia`, `painel`, `integracoes`, `referencia`; `guia_base.GRUPOS`). Corpo: o subconjunto de markdown e as tags `ItemImage`, `BlockImage`, `Row`, `RecipeFor`, `Recipe`, `ItemLink`, `SubPages` e `GameScene` descritos na spec.
- **Texturas:** `scripts/textures/livro.py`, chamado pelo `gerar_texturas.py`, gera `textures/gui/guide/book.png` (o livro aberto, 300 × 196 px, capa de grafite com cantoneiras de latão, folhas de porcelana) e as fitas `ribbon_<grupo>.png` e `_on.png`; o `gerar_texturas.py` gera o ícone do item (livro fechado com o Olho pulsando).
- **Chaves:** a marca `wirelessautomate:guide_v2_given`, a tecla `key.wirelessautomate.open_guide`, o `guide.giveOnFirstJoin` da config (liga a entrega no login) e as `gui.wirelessautomate.guide.*` e `tooltip.wirelessautomate.open_guide` nos dois `lang`. O `ModPayloads.VERSION` não mudou: o livro não tem pacote.

## A escala

O livro não usa a escala de interface do jogo: cada pixel da textura vale `k` pixels de tela, com `k` o **maior número par** com que o livro inteiro (até a ponta da fita comprida, 315 px, mais 16 px de margem de cada lado) cabe na janela; o mínimo é 2. A paginação usa **unidades de folha**, 2 por pixel da textura; a área de conteúdo de cada folha é de 116 × 140 px da textura, ou **232 × 280 unidades**, esquerda de x = 23 a 139 e direita de x = 161 a 277, as duas de y = 24 a 164. O texto sai com a fonte do jogo a `k / 2` pixels de tela por pixel da fonte. Tudo isso é calculado no `init` (que o `resize` chama), nunca por quadro.

## Regras

- **Por quadro só desenhar** (lei 8 da `wa-performance`): nada de `font.width`, `split`, montagem ou ordenação no `render`, nem alocação no laço de desenho. O texto das folhas é `FormattedCharSequence` pronto; a tinta de cada folha e a cena são montadas uma vez (ao preparar a folha) e guardadas; só as duas folhas à vista. Mudou um texto que depende do tamanho? Meça na preparação, não no desenho.
- **A conferência `GuideScreen.problems()`** junta, a cada `refresh` (nunca por quadro), tudo que não coube no par à vista: texto ou operação fora da folha, cabeçalho da capa, "← Índice" e número do rodapé largos demais, resultado da busca cortado. O e2e `guia` exige a lista vazia em cada par de cada página, nos dois idiomas (no livro o `GuiText.clipCount()` não conta nada: o texto não passa pelo `GuiText`).
- Código comum nunca referencia `client/guide/`: o item abre pelo `GuideClientHooks`. O parser, a paginação e a busca ficam sem Minecraft e com JUnit.
- O livro não depende de outro mod: o `neoforge.mods.toml` não declara nenhum. O `runClient` ainda baixa o GuideME (`build.gradle`, `clientMods`) só porque o AE2 e o Modern Industrialization do cliente de dev o exigem; sem eles ele pode sair.
- Texto que muda no guia entra no gerador `scripts/guide/`, nos dois idiomas, e o `python -I scripts/guide/gerar_guia.py` regera os `.md`.

## Testes

- JUnit (`src/test/java/.../client/guide/`): `GuideParserTest`, `GuidePagesRealTest` (as 25 páginas dos dois idiomas), `GuideLayoutTest`, `GuideLayoutRealPagesTest` (nenhuma folha vazia nem operação fora de 232 × 280), `BookScaleTest`, `GuideSearchTest`.
- GameTest (`gametest/GuideGameTests`): a receita dá o item; jogador novo recebe um livro e só um; a marca antiga sem a nova recebe um. O `TestPlayers.removedAtEnd` tira o livro e o NBT persistente do jogador falso.
- e2e (suíte `guia`, `client/dev/E2eGuideSteps`): ver [testes](testes.md) e `docs/e2e.md`.
