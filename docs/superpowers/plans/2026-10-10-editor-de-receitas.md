# Editor de receitas (`/wa recipes`): plano de implementação

> **Para agentes:** execute tarefa por tarefa, um subagente por tarefa, com revisão entre elas. Os passos usam caixas (`- [ ]`).

**Objetivo:** um operador abre `/wa recipes`, troca, desativa e restaura as receitas de bancada do mod; a mudança vira um datapack global em `config/wirelessautomate/recipes/` e vale depois de uma recarga.

**Arquitetura:** o modelo do rascunho é puro (`recipe/edit/RecipeDraft`, JUnit). O lado do servidor (`recipe/edit/`) registra o pack global, lê o JSON padrão do jar pelo `ResourceManager`, grava e apaga os overrides com Gson e recarrega como o `/reload`. Menu, snapshot e payloads em `menu/` e `packet/`; tela em `client/`; fantasma do JEI em `compat/jei/`.

**Spec:** [`docs/superpowers/specs/2026-10-10-editor-de-receitas-design.md`](../specs/2026-10-10-editor-de-receitas-design.md). Mockup aprovado: `docs/preview/editor-de-receitas.html`.

## Restrições globais

- Código, comentários, mensagens de commit e docs em português; traduções em `en_us.json` e `pt_br.json`, sempre as duas.
- Lógica pura sem classes do Minecraft vai com JUnit em `src/test/java` (o classpath de teste só tem o JUnit: nada de Gson nem Minecraft nos testes de unidade). Lógica que depende do jogo vai com GameTest.
- Classes de tela só em `client/`, nunca referenciadas por código comum. Tipos do JEI só em `compat/jei/`.
- Chaves existentes de NBT, componentes e config **não mudam**. Nada de config nova.
- `ModPayloads.VERSION` passa de `"11"` para `"12"` (payloads novos).
- Comando `/wa recipes`, permissão 2. O servidor confere a permissão de novo em cada payload.
- Pack: id `wirelessautomate:recipe_overrides`, pasta `FMLPaths.CONFIGDIR/wirelessautomate/recipes`, `pack_format` 48, `PackSource.BUILT_IN`, `PackSelectionConfig(true, Pack.Position.TOP, false)`, só `PackType.SERVER_DATA`.
- Performance: nada de tick por bloco; nada de sincronizar o cliente com a tela fechada; a recarga só por ação explícita (botão ou fechar a tela com pendências), nunca a cada Salvar.
- GameTests: classe com `@GameTestHolder(WirelessAutomate.MODID)` e `@PrefixGameTestTemplate(false)`, template `"empty"`. Rodam em paralelo: cada teste usa um id de receita diferente, limpa os arquivos que criar em `try/finally` e **nunca recarrega** os recursos (afetaria os outros testes).
- Cores e widgets da tela pelo `GuiPaint`; todo texto variável pelo `GuiText` (o e2e confere `GuiText.clipCount()==0`).
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium` (Git Bash, na raiz, com `JAVA_HOME` no JDK 21). Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, conserte em vez de esperar. Nas tarefas de tela, também o e2e: `WA_E2E="$PWD/run/e2e" ./gradlew runClient` (`run/e2e/result.txt` diz `OK`). Nunca dois jogos ao mesmo tempo. Não mexa em `run/options.txt`.
- Commits sem linhas de atribuição (nada de `Co-Authored-By` ou `Claude-Session`), autor do dono (o git já está configurado).

## Mapa de arquivos

| Arquivo | Papel |
| --- | --- |
| `recipe/edit/RecipeSlot.java`, `RecipeDraft.java` (novos) | Modelo puro: slot (vazio, item, tag), rascunho, corte das bordas, letras, ingredientes, validação |
| `src/test/java/.../recipe/edit/RecipeDraftTest.java` (novo) | JUnit do modelo |
| `recipe/edit/RecipeOverridePack.java` (novo) | Pasta, `pack.mcmeta` e o `AddPackFindersEvent` |
| `recipe/edit/RecipeJson.java` (novo) | JSON padrão → rascunho; rascunho + JSON padrão → JSON do override (Gson) |
| `recipe/edit/RecipeEditor.java` (novo) | Serviço do servidor: lista, estado, divergência, salvar, desativar, reativar, restaurar, pendências, recarga |
| `WirelessAutomate.java` (mudar) | Registra o pack e zera as pendências ao parar o servidor |
| `gametest/RecipeEditorGameTests.java` (novo) | GameTests do serviço e do menu |
| `command/WaCommand.java` (mudar) | `/wa recipes` |
| `menu/RecipeEditorMenu.java`, `menu/RecipeEditorSnapshot.java` (novos) | Menu (inventário do jogador como slots) e snapshot com `StreamCodec` |
| `packet/RecipeEditorActionPayload.java`, `packet/RecipeEditorStatePayload.java` (novos), `packet/ModPayloads.java`, `registry/ModMenus.java` (mudar) | Rede, protocolo `12` |
| `client/RecipeEditorScreen.java` (novo), `client/ClientSetup.java` (mudar) | A tela |
| `compat/jei/RecipeEditorGhostHandler.java` (novo), `compat/jei/WirelessAutomateJeiPlugin.java` (mudar) | Arrastar do JEI para a grade |
| `client/DevEndToEnd.java` (mudar) | Passos do editor no e2e |
| `lang/en_us.json`, `lang/pt_br.json` (mudar) | Textos |
| `scripts/guide/gerar_guia.py` + páginas geradas | Página "Editar receitas" |
| `CLAUDE.md`, `docs/progresso.md`, `docs/especificacao.md` | Documentação |

---

### Task 1: Modelo puro do rascunho

**Files:** criar `src/main/java/io/github/matheusanbs/wirelessautomate/recipe/edit/RecipeSlot.java`, `RecipeDraft.java` e `src/test/java/io/github/matheusanbs/wirelessautomate/recipe/edit/RecipeDraftTest.java`.

Sem nenhuma classe do Minecraft (nem `ResourceLocation`): ids são `String`.

- `RecipeSlot`: record `(Kind kind, String id)` com `enum Kind { EMPTY, ITEM, TAG }`; `EMPTY` constante; fábricas `item(id)` e `tag(id)`; `isEmpty()`.
- `RecipeDraft`: record imutável `(Shape shape, List<RecipeSlot> slots, int count, boolean disabled)`, com `enum Shape { SHAPED, SHAPELESS }`; o construtor exige 9 slots (cópia imutável) e `MIN_COUNT = 1`, `MAX_COUNT = 64`. Métodos:
  - `withSlot(int i, RecipeSlot s)`, `withCount(int)` (prende entre 1 e 64), `withDisabled(boolean)`;
  - `isEmpty()` (todos os slots vazios);
  - `ShapedLayout layout()` para `SHAPED`: record `(List<String> pattern, LinkedHashMap<Character, RecipeSlot> key)`. Corta linhas e colunas vazias nas bordas (linhas e colunas internas vazias ficam), letras `A`, `B`, `C`... na ordem de leitura (linha a linha) da primeira aparição, slots iguais (mesmo `kind` e `id`) dividem a letra, vazio vira espaço. Lança `IllegalStateException` se `isEmpty()`;
  - `List<RecipeSlot> ingredients()` para `SHAPELESS`: os não vazios, na ordem da grade;
  - `static RecipeDraft fromShaped(List<String> pattern, Map<Character, RecipeSlot> key, int count)`: põe o padrão no canto de cima à esquerda da grade 3×3 (padrão maior que 3×3 → `IllegalArgumentException`);
  - `static RecipeDraft fromShapeless(List<RecipeSlot> ingredients, int count)`: preenche os slots na ordem (mais de 9 → `IllegalArgumentException`).

Testes (`RecipeDraftTest`), cada um com o valor exato esperado:
- [ ] Padrão do Configurador `"  A"`, `" S "`, `"S  "` → `layout()` devolve as mesmas 3 linhas e as chaves `A`=ametista, `B`=graveto (as letras são renumeradas pela ordem de leitura).
- [ ] Cartão de Filtro `"PRP"`, `"PCP"`, `"   "` (linha de baixo vazia) → `layout().pattern()` = `["ABA", "ACA"]`.
- [ ] Coluna vazia à esquerda e à direita: só o centro `" X "` × 3 → `["A", "A", "A"]`.
- [ ] Linha interna vazia fica: `"X  "`, `"   "`, `"X  "` → `["A", " ", "A"]`.
- [ ] Item e tag com o mesmo texto de id são letras diferentes.
- [ ] `ingredients()` de um sem forma com slots 0, 4 e 8 → os três, nessa ordem.
- [ ] `isEmpty()` e `layout()` numa grade vazia lança `IllegalStateException`.
- [ ] `withCount(0)` → 1; `withCount(99)` → 64.
- [ ] `fromShaped` com 2×2 ocupa os slots 0, 1, 3 e 4; `fromShaped` com 4 colunas lança.
- [ ] Ida e volta: `fromShaped(layout())` de um rascunho já no canto devolve o mesmo rascunho.

- [ ] `./gradlew test` passa; commit "Editor de receitas, tarefa 1: modelo puro do rascunho".

### Task 2: Pack global e o serviço do servidor

**Files:** criar `recipe/edit/RecipeOverridePack.java`, `RecipeJson.java`, `RecipeEditor.java`, `gametest/RecipeEditorGameTests.java`; mudar `WirelessAutomate.java`.

**`RecipeOverridePack`:**
- `PACK_ID = "wirelessautomate:recipe_overrides"`; `root()` = `FMLPaths.CONFIGDIR.get().resolve("wirelessautomate/recipes")`; `recipeFile(ResourceLocation id)` = `root()/data/<ns>/recipe/<path>.json`.
- `ensureFolder()`: cria a pasta e o `pack.mcmeta` (`{"pack":{"pack_format":48,"description":"Wireless Automate: receitas editadas"}}`) se faltarem. Erros de disco vão para o log, sem derrubar o jogo.
- `onAddPackFinders(AddPackFindersEvent)`: só para `PackType.SERVER_DATA`; chama `ensureFolder()` e registra um `Pack.readMetaAndCreate(new PackLocationInfo(PACK_ID, Component.literal("Wireless Automate: receitas editadas"), PackSource.BUILT_IN, Optional.empty()), new PathPackResources.PathResourcesSupplier(root()), PackType.SERVER_DATA, new PackSelectionConfig(true, Pack.Position.TOP, false))` pelo `addRepositorySource` (molde: `AddPackFindersEvent.addPackFinders` do NeoForge). Se `readMetaAndCreate` devolver `null`, loga e não registra.
- `WirelessAutomate`: `modEventBus.addListener(RecipeOverridePack::onAddPackFinders)`.

**`RecipeJson`** (Gson, sem estado):
- `RecipeDraft toDraft(JsonObject json)`: lê `type` (`minecraft:crafting_shaped` → `fromShaped` de `pattern`/`key`; `minecraft:crafting_shapeless` → `fromShapeless` de `ingredients`), `result.count` (padrão 1). Ingrediente `{"item": id}` → `item`, `{"tag": id}` → `tag`; uma lista (alternativas) ou outro formato → `Optional.empty()` (a receita não é editável). Uma condição `neoforge:false` nas `neoforge:conditions` → `disabled = true`.
- `JsonObject toOverride(JsonObject defaultJson, RecipeDraft draft)`: copia o padrão (`deepCopy`), tira `pattern`, `key` e `ingredients`, grava os do rascunho (`layout()` ou `ingredients()`), põe `result.count` (mantém `result.id`), e nas `neoforge:conditions` mantém as do padrão, acrescentando `{"type":"neoforge:false"}` se `draft.disabled()` (sem duplicar). Sem condições e sem desativar, a chave `neoforge:conditions` não aparece.

**`RecipeEditor`** (servidor, métodos estáticos que recebem o `MinecraftServer`):
- `record Entry(ResourceLocation id, ResourceLocation resultItem, RecipeDraft defaults, RecipeDraft current, State state, boolean divergent)`; `enum State { DEFAULT, EDITED, DISABLED }`.
- `List<Entry> entries(MinecraftServer)`: união das receitas carregadas no namespace `wirelessautomate` com serializador `RecipeSerializer.SHAPED_RECIPE` ou `SHAPELESS_RECIPE` e dos overrides em disco (`recipe/*.json` da pasta) cujo padrão existe; ordenada pelo caminho do id. O padrão vem de `server.getResourceManager().getResourceStack(<ns>:recipe/<path>.json)`, pegando o último recurso cujo `sourcePackId()` não é `PACK_ID` (o do jar), lido com `JsonParser`. O atual vem do arquivo de override se existir, senão é o padrão. Receita sem padrão legível ou com ingrediente não editável fica de fora.
- Divergência (spec, "Estado de cada receita"): só para quem tem override; compara com a receita carregada no `RecipeManager` (resultado `getResultItem(server.registryAccess())`, item e quantidade; número de ingredientes não vazios; para cada slot do rascunho, algum ingrediente carregado aceita `new ItemStack(item)`, ou o primeiro item da tag por `BuiltInRegistries.ITEM.getTag`).
- Ações, todas devolvem `Optional<Component>` com o erro (ou vazio): `save(server, id, draft)`, `setDisabled(server, id, boolean)`, `restore(server, id)`. Validam: id na lista de editáveis; o formato do rascunho igual ao do padrão; grade não vazia; itens existentes em `BuiltInRegistries.ITEM` (e diferentes de ar); tags com `ResourceLocation.tryParse` válido; count 1..64. Gravam com Gson (pretty print, UTF-8) em `recipeFile(id)` (criando as pastas) ou apagam o arquivo; cada sucesso soma 1 a `pending`.
- `int pending()`, `CompletableFuture<Void> reload(MinecraftServer)`: zera `pending` e chama `server.reloadResources(server.getPackRepository().getSelectedIds())`. `reset()` zera (chamado no `onServerStopped` do `WirelessAutomate`).

Mensagens de erro em `lang` (`gui.wirelessautomate.recipes.error.*`): `not_editable` ("Esta receita não pode ser editada"), `empty` ("A receita precisa de pelo menos um ingrediente"), `unknown_item` ("Item desconhecido: %s"), `bad_tag` ("Tag inválida: %s"), `disk` ("Não foi possível gravar o arquivo: %s"), e o inglês equivalente.

**GameTests (`RecipeEditorGameTests`)**, cada um num id diferente, com `try/finally` apagando o arquivo:
- [ ] `packSelected`: `server.getPackRepository().getSelectedIds()` contém `wirelessautomate:recipe_overrides` e a pasta tem o `pack.mcmeta`.
- [ ] `listsModRecipes`: `entries` contém `wirelessautomate:router` (com forma) e não contém `filter_card_copy` nem `router_upgrade`; `guide` só se o GuideME estiver carregado (`ModList`).
- [ ] `saveWritesValidRecipe`: salvar o `configurator` com count 3 grava o arquivo; o JSON, sem a chave `neoforge:conditions`, decodifica por `Recipe.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), json)` num `ShapedRecipe` com resultado 3 Configuradores; `entries` passa a dar `EDITED` e o `current` com count 3.
- [ ] `disableAndEnable`: desativar o `linker` grava a condição `neoforge:false` (e `entries` dá `DISABLED`); reativar a tira (dá `EDITED`).
- [ ] `restoreDeletes`: depois de salvar o `network_tablet`, `restore` apaga o arquivo e `entries` volta a `DEFAULT`.
- [ ] `rejectsBadDrafts`: grade vazia, item inexistente (`wirelessautomate:nao_existe`), count 0 (o record prende, então teste pelo `save` com rascunho de formato trocado) e id especial (`filter_card_copy`) devolvem erro e não gravam nada.
- [ ] `keepsConditions`: `RecipeJson.toOverride` sobre o JSON padrão do `tier_core_allthemodium` (lido do jar pelo `ResourceManager`, que tem o recurso mesmo sem o mod) mantém a condição `neoforge:mod_loaded` `allthemodium`.

- [ ] Verificação completa passa; commit "Editor de receitas, tarefa 2: pack global e serviço do servidor".

### Task 3: Comando, menu, snapshot e payloads

**Files:** criar `menu/RecipeEditorMenu.java`, `menu/RecipeEditorSnapshot.java`, `packet/RecipeEditorActionPayload.java`, `packet/RecipeEditorStatePayload.java`; mudar `command/WaCommand.java`, `packet/ModPayloads.java` (`VERSION = "12"` e os registros), `registry/ModMenus.java` (`RECIPE_EDITOR`), `gametest/RecipeEditorGameTests.java`, os dois `lang`.

- `RecipeEditorSnapshot`: record com `List<Row>` e `int pending`; `Row(ResourceLocation id, ResourceLocation resultItem, RecipeDraft defaults, RecipeDraft current, RecipeEditor.State state, boolean divergent)`. `StreamCodec<RegistryFriendlyByteBuf, ...>` escrito à mão (slot: byte do `kind` + `String` do id; rascunho: forma, 9 slots, count, disabled). `static RecipeEditorSnapshot of(MinecraftServer)` a partir de `RecipeEditor.entries` e `pending()`.
- `RecipeEditorMenu`: só o inventário do jogador como slots (27 + 9), posições para a tela da Task 4 (inventário em x 81, y 180; barra em y 238, slots de 18 px, painel de 324 × 250). `quickMoveStack` devolve `ItemStack.EMPTY` (nada se move). `stillValid` = jogador vivo e `hasPermissions(2)`. Construtor do cliente lê o snapshot do buffer de abertura; guarda o snapshot atual com um ouvinte para a tela (`Consumer<RecipeEditorSnapshot>`), no molde de `LinkerMenu::onSnapshot`. `removed(Player)` no servidor: se `RecipeEditor.pending() > 0`, recarrega.
- `/wa recipes` (`requires(source -> source.hasPermission(2))`, só jogador): `player.openMenu(provider, buf -> STREAM_CODEC.encode(buf, snapshot))` com o título `gui.wirelessautomate.recipes.title` ("Receitas do Wireless Automate" / "Wireless Automate Recipes").
- `RecipeEditorActionPayload(Action action, ResourceLocation id, RecipeDraft draft)`, `enum Action { SAVE, DISABLE, ENABLE, RESTORE, RELOAD }` (em `RELOAD` o id e o rascunho são ignorados). Handler no servidor: recusa em silêncio (log em debug) se o jogador não tem permissão 2 ou o `containerMenu` não é `RecipeEditorMenu`; chama a ação do `RecipeEditor`; com erro, manda a mensagem ao jogador (`displayClientMessage`, barra de ação); sempre devolve o `RecipeEditorStatePayload` com o snapshot novo. Em `RELOAD`, quando o `CompletableFuture` termina (`thenRunAsync` na thread do servidor), manda o snapshot a todos os jogadores com o `RecipeEditorMenu` aberto e a mensagem `gui.wirelessautomate.recipes.reloaded` ("Receitas recarregadas para todos os jogadores").
- `RecipeEditorStatePayload(RecipeEditorSnapshot)`: no cliente, entrega ao menu aberto se for `RecipeEditorMenu`.

GameTests (acrescentar):
- [ ] `actionNeedsOperator`: com um `FakePlayer` (ou `helper.makeMockServerPlayerInLevel()`) sem permissão, o handler da ação `SAVE` não grava o arquivo.
- [ ] `snapshotRoundTrip`: `STREAM_CODEC` codifica e decodifica um snapshot de `RecipeEditorSnapshot.of(server)` e devolve um igual.

- [ ] Verificação completa passa; commit "Editor de receitas, tarefa 3: comando, menu e payloads".

### Task 4: Tela, JEI e e2e

**Files:** criar `client/RecipeEditorScreen.java`, `compat/jei/RecipeEditorGhostHandler.java`; mudar `client/ClientSetup.java`, `compat/jei/WirelessAutomateJeiPlugin.java`, `client/DevEndToEnd.java`, os dois `lang`.

A tela segue o mockup aprovado (`docs/preview/editor-de-receitas.html`; abra o arquivo e leia o `render()`: posições em pixels de GUI) e a spec, seção "Tela". Pontos que o mockup não mostra:
- O rascunho editado fica só no cliente até Salvar; trocar de receita com mudanças não salvas descarta as mudanças (sem pergunta).
- Ao chegar um snapshot novo, a receita escolhida continua escolhida e o rascunho dela passa a ser o `current` do servidor.
- Os botões mandam `RecipeEditorActionPayload` (Salvar → `SAVE` com o rascunho; Desativar/Reativar → `DISABLE`/`ENABLE`; Restaurar → `RESTORE`; Recarregar → `RELOAD`).
- O clique num slot do inventário com um slot da grade escolhido põe `RecipeSlot.item` do item e **não** passa o clique ao `AbstractContainerScreen` (nada se move). Com item no cursor, clicar na grade põe o item do cursor. Clique direito num slot da grade limpa.
- Tags: `stack.getTags()` do item, ordenadas pelo texto; para um slot que já é tag (vindo do JSON), o item de referência é o primeiro da tag (`BuiltInRegistries.ITEM.getTag`) e o ícone é o dele. Slot de tag desenha um `#` coral no canto.
- Divergente: uma linha âmbar sob o id: "Diferente do que está carregado (outro datapack ou falta recarregar)".
- Tooltip dos slots da grade e do resultado como no mockup (nome e id, "Qualquer item de #tag", "Clique direito: limpar").
- `ClientSetup`: registra a tela para `ModMenus.RECIPE_EDITOR`.
- JEI: `RecipeEditorGhostHandler` (molde: `FilterGhostHandler`) aceita `ItemStack` sobre cada slot da grade (`screen.gridSlotArea(i)`) e chama `screen.setGhost(i, stack)`; `WirelessAutomateJeiPlugin.registerGuiHandlers` registra.

Textos novos (`gui.wirelessautomate.recipes.*`), com o inglês: título, `search` ("Buscar receita"), `none` ("Nenhuma receita"), `pending` ("%s salvas, falta recarregar"; com 1, `pending.one` "1 salva, falta recarregar"), `applied` ("Tudo aplicado"), `reload` ("Recarregar agora"), `shaped`/`shapeless` ("Com forma"/"Sem forma"), `state.default`/`edited`/`disabled`/`unsaved` ("Padrão do mod", "Editada", "Desativada", "Não salva"), `slot` ("Slot %s: %s"), `slot.empty` ("vazio"), `item`, `tag`, `no_tags` ("sem tags"), `clear` ("Limpar"), `save` ("Salvar"), `restore` ("Restaurar padrão"), `disable`/`enable` ("Desativar"/"Reativar"), `disabled_hint` ("Desativada: o item fica sem receita de bancada. Reative para editar."), `inventory` ("Inventário · clique para pôr no slot %s"), `count` ("%s itens"/`count.one` "1 item"), `divergent`, `tooltip.any_of` ("Qualquer item de"), `tooltip.clear` ("Clique direito: limpar"), `tooltip.pick` ("Clique e escolha um item no inventário").

e2e (`DevEndToEnd`, passos novos no fim da volta em inglês, antes do guia): rodar `/wa recipes` (o jogador do e2e é operador), esperar a tela, captura `recipes-1-lista`; escolher o Cartão de Filtro, pôr count 4 pelo botão `+` (dois cliques a partir de 2), Salvar, captura `recipes-2-editada`, Recarregar e esperar o snapshot com `pending == 0`; conferir no `RecipeManager` do servidor integrado que `wirelessautomate:filter_card` dá 4; Restaurar, Recarregar, conferir que volta a 2; fechar a tela. Os arquivos criados ficam em `run/config/...` e o passo de Restaurar os apaga.

- [ ] Verificação completa e e2e `OK`; olhe as capturas `recipes-*` (texto cortado, `#` no lugar, cores); commit "Editor de receitas, tarefa 4: tela, JEI e e2e".

### Task 5: Guia e documentação

**Files:** `scripts/guide/gerar_guia.py` (e as páginas geradas), `CLAUDE.md`, `docs/progresso.md`, `docs/especificacao.md`.

- [ ] Página nova `recipe-editor` no guia, nos dois idiomas, só como operar: quem pode (operador), `/wa recipes`, escolher, trocar itens pelo inventário ou JEI, Tag, quantidade, Salvar, Recarregar (ou fechar a tela), Desativar e Restaurar, e que a mudança vale em todos os mundos. Link na página índice, como as outras. Rode `python scripts/guide/gerar_guia.py`.
- [ ] `CLAUDE.md`: linha do pacote `recipe/edit/` no mapa do código, a tela na linha do `client/`, o `/wa recipes` na linha do `command/`, o protocolo `12` e a contagem de GameTests.
- [ ] `docs/especificacao.md`: uma seção curta "Editor de receitas" apontando para a spec.
- [ ] `docs/progresso.md`: linha na tabela, próximo passo, histórico.
- [ ] Verificação completa; commit "Editor de receitas, tarefa 5: guia e documentação".
