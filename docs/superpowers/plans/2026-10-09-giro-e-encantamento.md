# Giro do roteador e encantamento por nome: plano

**Spec:** [`docs/superpowers/specs/2026-10-09-giro-e-encantamento-design.md`](../specs/2026-10-09-giro-e-encantamento-design.md). Leia a spec antes da tarefa: ela tem os valores e as decisões aprovadas.

## Restrições globais

- Código, comentários, commits e docs em português; toda tradução nova em `en_us.json` **e** `pt_br.json`.
- Mekanism e Ars Nouveau continuam opcionais: nenhum tipo deles fora dos pacotes `compat/` (nada muda ali nesta feature).
- NBT, componentes e chaves de config **não mudam**. As faces continuam salvas por `RelativeSide`; o giro vive só no blockstate (`spin`, padrão 0). Mundos existentes carregam com `spin=0` e ficam iguais a hoje.
- `ModPayloads.VERSION` **não muda** (nenhum payload novo ou alterado).
- Classes de tela só em `client/`, nunca referenciadas por código comum.
- Lógica pura (sem classes do Minecraft) vai com JUnit em `src/test/java`; lógica de jogo vai com GameTest (`@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`, template `"empty"`, cada teste cria a própria rede, `onLoad` só no tick seguinte: `startSequence().thenWaitUntil(...)`).
- Performance: nada de tick por bloco nem busca de capability por tick; nada muda no motor além de passar o `spin` para `RelativeSide`.
- Modelos e blockstates só por `scripts/textures/gerar_texturas.py` (o `router_basic.json` é o molde à mão); guia só por `scripts/guide/gerar_guia.py`. Nunca editar os gerados à mão.
- Todo texto variável de tela passa por `GuiText` (o e2e confere `GuiText.clipCount()==0`).
- Não mexa em `run/options.txt`. Um jogo (build com GameTest, cliente, e2e) por vez; compile antes (`./gradlew compileJava`) e, com `BUILD FAILED`, conserte em vez de esperar.
- Verificação antes de cada commit: `./gradlew build runGameTestServer` no mínimo; na tarefa final, `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium` e o e2e (`./scripts/e2e.sh`, `run/e2e/result.txt` = `OK`).
- Commits no ramo atual, terminando com:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_012FCgSWCBGW94vyPMuHVfGz
  ```
  Não faça push.

## Tarefas

### 1. `spin` no estado e em `RelativeSide`

- `RouterBlock`: `public static final IntegerProperty SPIN = IntegerProperty.create("spin", 0, 3)`, no `createBlockStateDefinition` e no estado padrão (0). `getStateForPlacement` não muda (spin 0).
- `RelativeSide`: tabelas `[facing][spin][lado]` e `[facing][spin][face absoluta]`. Conversão: as direções do caso `facing=up` (`whenUp`) giram `spin` vezes com `getClockWise()` em torno de Y (só horizontais; UP/DOWN ficam), depois a rotação de `facing` que já existe. Métodos: `toAbsolute(Direction facing, int spin)`, `static toAbsolute(facing, spin, side)`, `static fromAbsolute(facing, spin, absolute)`. **Remova** as sobrecargas sem `spin`. Atualize o Javadoc da classe.
- `RouterBlockEntity.spin()` (lê `SPIN` do estado) e todas as chamadas passam `facing(), spin()`. Atualize todos os usos (`NetworkRoutes`, `RouterMenu`, `RouterPreset`, `DevScreenshot`, `DevEndToEnd`, GameTests): `grep -rn "RelativeSide" src/main/java`. Onde o código quer de propósito o caso sem giro (testes, capturas), passe `0`.
- `RouterBlockEntity.setBlockState`: tratar mudança de `spin` como a de tier (incrementa `changeVersion`, `nodeChanged` + `NodeIndex.track` no servidor). Não limpa caches (a máquina não muda de lugar).
- `RouterBlock.rotate(Rotation)`: novo `facing` como hoje; novo `spin` = o que faz `RelativeSide.TOP.toAbsolute(novoFacing, spin)` igual a `rotation.rotate(TOP.toAbsolute(facing, spinAtual))` (procure entre 0..3; se nenhum servir, mantenha o atual). `mirror`: mesma regra com `mirror.mirror(...)` aplicado ao `facing` e ao `TOP`.
- GameTests em `RouterConfigGameTests`: o de ida e volta passa a cobrir 6 facings × 4 spins × 6 lados (e que, para cada facing e spin, os 6 lados dão 6 faces distintas; `FRONT` = facing; `BACK` = oposto). Um teste novo: `spin` 1 com `facing=up` leva `TOP` para oeste (convenção da spec). Um teste de `rotate`: roteador com `facing=up, spin=0` girado `CLOCKWISE_90` mantém `TOP` apontando para a direção girada de sul (oeste).

### 2. Shift + clique gira

- `RouterBlock.useWithoutItem`: se `player.isSecondaryUseActive()`, gira em vez de abrir a tela. Só com `player.mayBuild()` e `level.mayInteract(player, pos)`; senão devolve `InteractionResult.PASS` sem abrir a tela. (O vanilla só chama `useWithoutItem` com Shift quando as duas mãos estão vazias.)
- No servidor: `router.spinTo((spin + 1) % 4)`. Método novo em `RouterBlockEntity`: calcula a permutação dos lados que mantém cada face absoluta (`novoLado` tal que `toAbsolute(facing, novoSpin, novoLado) == toAbsolute(facing, spinAntigo, lado)`), permuta `faces[t][...]` e `cards[t][...]` de todos os tipos, depois `level.setBlock(pos, state.setValue(SPIN, novoSpin), Block.UPDATE_ALL)`, `setChanged()` e `nodeChanged` (o `setBlockState` da tarefa 1 já avisa; não duplique se já acontecer). Som `SoundEvents.ITEM_FRAME_ROTATE_ITEM`, `SoundSource.BLOCKS`. Retorna `sidedSuccess`.
- Atualize o Javadoc de `useItemOn`/`useWithoutItem`.
- GameTests (classe nova `RouterSpinGameTests` ou em `RouterConfigGameTests`): roteador numa máquina (baú) com face norte da máquina em `OUTPUT` com prioridade 3 e um Cartão de Filtro num slot dessa face; jogador de teste (`helper.makeMockPlayer(GameType.SURVIVAL)`, `setShiftKeyDown(true)`, mãos vazias) usa o bloco (`helper.useBlock(pos, player)`): `spin` vira 1, `face(ITEM, NORTH)` continua `OUTPUT` prioridade 3 e o cartão continua na face norte; quatro usos voltam a `spin=0` com tudo igual. Sem Shift: `spin` não muda (a tela abre; com mock player basta checar o estado). Rede criada no próprio teste.

### 3. Modelos, blockstate e colisão

- `scripts/textures/gerar_texturas.py`: para cada tier, gere `router_<tier>_spin1.json`, `_spin2`, `_spin3` como filhos do modelo do tier com a transformação raiz do NeoForge (`"transform": {"origin": "center", "rotation": {"y": ANG}}`) e o blockstate `router.json` com `facing,spin,tier` (192 variantes: o `model` pelo spin, `x`/`y` pelo facing como hoje). Ache o sinal de `ANG` que faz o visual bater com `RelativeSide` (spin 1 com `facing=up`: LEDs, o lado `TOP`, para oeste). Se a transformação raiz não for aplicada pelo NeoForge 1.21.1 (confira na captura), gere a geometria girada no próprio modelo (elementos com x/z trocados, faces renomeadas e `rotation` nas UVs de cima/baixo).
- `RouterShapes`: formas por `facing` e `spin` (`get(Direction, int)`), girando as caixas de `UP_BOXES` pelo spin em torno de Y (mesma convenção) antes da rotação de facing. `RouterBlock.getShape` passa o spin.
- Rode o script e confira o diff dos JSON gerados. Confira o visual numa captura: `WA_SCREENSHOT=run/shots xvfb-run -a -s "-screen 0 1280x800x24" ./gradlew runClient` (veja `client/DevScreenshot.java`) ou um passo novo no e2e que coloque quatro roteadores numa parede com spin 0..3 e salve `giro-parede.png`. Olhe a imagem: os LEDs devem girar no sentido horário de quem olha, e nada pode ficar preto ou faltar face.
- GameTest: a forma de colisão com `facing=up, spin=1` é a de `spin=0` girada (por exemplo, a antena que fica no norte vai para o leste).

### 4. Busca de encantamentos e rótulo do nível (lógica pura)

- `client/EnchantSearch.java` (sem classes do Minecraft, genérica): `static <T> List<T> search(List<T> all, Function<T,String> name, Function<T,String> id, String query)` com as regras da spec (acentos e maiúsculas ignorados via `java.text.Normalizer`; começa com no nome, no caminho do id ou no id inteiro primeiro; depois contém; ordem alfabética do nome dobrado dentro de cada grupo; consulta vazia devolve todos em ordem alfabética). E `static OptionalInt parseLevel(String)` (1..255, só dígitos, senão vazio) e `static int stepLevel(int level, int delta)` (limitado a 1..255).
- JUnit em `src/test/java/.../client/EnchantSearchTest.java` cobrindo: acento (`protecao` acha "Proteção"), id (`minecraft:sh` acha Afiação/`minecraft:sharpness`), ordem começa-com antes de contém, vazio, parse ("0", "256", "", "12a", "255").
- `ItemRule` (descrição da regra, linha com `enchantment.level.`): nível ≤ 10 usa `Component.translatable("enchantment.level." + n)`, acima disso `Component.literal(String.valueOf(n))`. Faça o mesmo em qualquer outro lugar que monte `enchantment.level.` (`grep -rn "enchantment.level" src/main/java`).

### 5. Tela: campo com sugestões e nível digitável

- `FilterScreen` (aba Regra): troque `enchantPrev`/`enchantNext`/`enchantLevel` por dois `EditBox` (pelo `box(...)` que já existe, com a moldura desenhada pela tela como o `scopeBox`): `enchantBox` (largura do painel menos o campo de nível) e `levelBox` (3 dígitos, "≥" desenhado antes). Remova `cycleEnchantment`, `stepLevel`, `levelLabel` e as chaves de lang que ficarem sem uso.
- Sugestões: lista desenhada por cima das linhas de baixo (depois dos widgets, em z mais alto), até 5 linhas de 12 px com o ícone do livro encantado (`Items.ENCHANTED_BOOK`), o nome (o trecho que casa em `GuiPaint.ACCENT` se for simples; senão o nome normal) e o id em `GuiPaint.DISABLED` à direita, cortados por `GuiText`. Linha "+N" quando houver mais. Abre ao focar ou digitar no `enchantBox`; fecha ao escolher, com Esc ou ao clicar fora (o texto volta ao escolhido). ↑ ↓ movem a seleção (rolando), Enter/Tab escolhem, roda do mouse sobre a lista rola. Clique numa linha escolhe. Enquanto a lista está aberta, cliques e roda nela não vazam para os widgets de baixo.
- Escolher define `draft.withEnchantment(new Enchant(id, nívelAtual))`. Ao marcar "Encantamento", o padrão continua Fortuna nível 1 e o campo mostra o nome.
- `levelBox`: só dígitos (filtro do `EditBox`), responder usa `EnchantSearch.parseLevel`; válido atualiza o nível do draft. Roda do mouse sobre o campo: `stepLevel(±1)`, com Shift ±10. Inválido ou vazio: borda `DANGER` e o Adicionar/Salvar desligado (junte à validação de `ruleAddButton.active` e ao tooltip dele, com uma chave nova `rule.level.invalid`). Nome sem escolha válida: borda `DANGER`, Adicionar desligado e tooltip `rule.enchantment.invalid`.
- Ao editar uma regra existente (carregar no draft), os dois campos mostram o nome e o nível dela.
- Atualize `scopeRowY()` e o layout se a altura da linha mudar. Mantenha os acessos usados pelos testes/e2e (`scopeBox()` etc.) e acrescente `enchantBox()`, `levelBox()` e `suggestionLabels()` para o e2e.
- Lang (en_us e pt_br): dicas (`rule.enchantment.hint` "nome ou id" / "name or id"), tooltips novos do campo e do nível (o do nível: "Nível mínimo, de 1 a 255. Roda do mouse: ±1, Shift: ±10"), as chaves de inválido. Atualize `rule.enchantment.tooltip` (não fala mais em roda do mouse trocando o encantamento).
- e2e (`DevEndToEnd`, na volta da aba Regra): marcar Encantamento, digitar `prot`, conferir que `suggestionLabels()` contém Proteção (pelo nome traduzido do registro, não texto fixo), salvar captura `filtro-regra-sugestoes`, escolher com Enter, digitar `37` no nível e conferir o draft (`Enchant` com nível 37). Não pode haver texto cortado.

### 6. Fechamento

- Guia (`scripts/guide/gerar_guia.py`, os dois idiomas): na página do roteador, o gesto "Shift + clique direito com a mão vazia: gira o roteador 90°; a configuração das faces não muda". Na página de filtros, a linha **Encantamento**: "Digite o nome ou o id e escolha na lista; nível de 1 a 255". Rode o script.
- `CLAUDE.md`: mapa do código (`RouterBlock` com `SPIN`, `RelativeSide` por facing + spin, `EnchantSearch` na lista de lógica pura com JUnit, número de GameTests). `docs/especificacao.md`: o giro na parte do roteador e o campo na parte de regras. `docs/progresso.md`: tabela, próximo passo, linha no histórico com os números de testes.
- Verificação completa (as quatro runs de GameTest e o e2e) e olhar as capturas novas.
