# Roteador de alcance: plano da etapa 1 (modelo, motor, Vinculador e tela mínima)

> **Para agentes:** execute tarefa por tarefa, um subagente por tarefa, com revisão entre elas. Os passos usam caixas (`- [ ]`).

**Objetivo:** com o Upgrade de Alcance, um roteador vinculado a várias máquinas no raio dele move recursos de e para todas, configuradas por grupo (perfil por tipo de bloco) com exceções por máquina; o Vinculador ganha o modo Máquinas; a tela do roteador ganha uma lista mínima de grupos e máquinas para o dono testar no jogo. Sem o upgrade, nada muda.

**Spec:** [`docs/superpowers/specs/2026-10-11-roteador-de-alcance-design.md`](../specs/2026-10-11-roteador-de-alcance-design.md), com as decisões 8 (acordar pelo motor) e 9 (cartões só no perfil, lados vistos de frente, área só com block entity) e a etapa 1 da seção 8. Mockup: `docs/preview/alcance-remoto.html`.

## Restrições globais

- Código, comentários, commits e docs em português; textos novos em `en_us.json` e `pt_br.json`, sempre os dois.
- Ramo `roteador-de-alcance`. Commits sem linhas de atribuição (nada de `Co-Authored-By` nem `Claude-Session`), com o autor do dono.
- **Chaves existentes de NBT, componentes e config não mudam.** A máquina presa continua com `faces`, `cards`, `upgrade` e `upgrade_owner`. Chaves novas no roteador: `range_upgrade` (ItemStack), `machines` (lista) e `groups` (lista). Um mundo antigo carrega igual e um roteador sem o upgrade guarda as listas sem criar portas.
- Config nova em `tiers.<tier>`: `machineRadius` (0 a 64) e `maxMachines` (0 a 512). Padrões pela posição do `RouterTier`: raio `0, 6, 12, 16, 24, 32, 48, 64` e máquinas `0, 8, 16, 32, 48, 64, 96, 128`. A chave `range` (alcance da rede) não muda.
- Distância de Chebyshev a partir da posição do roteador, na mesma dimensão.
- `ModPayloads.VERSION` passa de `"12"` para `"13"` (uma vez, na Tarefa 7).
- **Performance:**
  - nada de tick por bloco nem por máquina;
  - capability só por `BlockCapabilityCache`, criado na primeira consulta;
  - **antes de qualquer consulta numa máquina remota, `level.isLoaded(pos)`**, porque consultar um chunk descarregado o carrega à força;
  - nada de varredura por tick (a de área roda só no clique);
  - nada de sincronizar o cliente com a tela fechada;
  - mudar um perfil é um aviso só ao motor (`batchChanges`).
- Acordar (decisão 8): um mixin em `Level.blockEntityChanged` acorda as portas de uma máquina remota vinculada quando ela chama `setChanged` (uma consulta num `LongOpenHashSet` por nível; nada mais no caminho quente), com a mesma regra da presa (`deliveringInto`: durante uma entrega, não acorda quem a abastece). A carga de um chunk acorda as máquinas remotas que estão nele. O teto de sono continua `MAX_SLEEP_TICKS = 100`. O mixin é o primeiro do mod: `wirelessautomate.mixins.json` declarado no `neoforge.mods.toml` (`[[mixins]]`), pacote `mixin/`, só lado comum.
- Cartões (decisão 9): só no perfil do grupo. Uma exceção guarda só os `FaceConfig`.
- Lados das remotas (`RemoteSide`): Frente, Trás, Esquerda, Direita, Cima e Baixo, com Esquerda e Direita **vistas de quem olha a frente da máquina**. Se a frente é Norte, Esquerda = Leste (`front.getClockWise()`) e Direita = Oeste. A frente vem de `HORIZONTAL_FACING` ou de `FACING` horizontal do `BlockState`. Bloco sem frente usa o quadro canônico, com a frente em Norte.
- Quadro canônico de tela e payload: as telas e os payloads continuam mandando um `Direction`. Numa remota, ele é lido no quadro "frente = Norte": NORTH=Frente, SOUTH=Trás, EAST=Esquerda, WEST=Direita, UP=Cima, DOWN=Baixo. A conversão fica num lugar só (`RemoteSide.fromCanonical`/`canonical()`).
- Mekanism e Ars continuam opcionais: tipos deles só nos pacotes `compat/` de sempre, nunca em assinatura de classe `@EventBusSubscriber` ou `@GameTestHolder`.
- Classes de tela só em `client/`. Todo texto variável de tela passa por `GuiText`.
- GameTests: `@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`, template `"empty"`. Cada teste cria a própria rede. Teste pertinência, não contagem. Espere o `onLoad` com `startSequence().thenWaitUntil(...)`. Config alterada num teste volta em `try/finally`, de forma síncrona.
- Lógica pura (sem classes do Minecraft) vai com JUnit em `src/test/java`.
- Compile antes de rodar jogo (`./gradlew compileJava`). Com `BUILD FAILED`, conserte; nunca espere um resultado que não virá.
- Nunca rode dois jogos ao mesmo tempo. Não mexa em `run/options.txt`.
- Verificação antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource`. Nas tarefas que tocam tela ou payload, também `./scripts/e2e.sh` (ou `WA_E2E="$PWD/run/e2e" ./gradlew runClient`), com `run/e2e/result.txt` = `OK`. A run `runGameTestServerAllthemodium` roda uma vez, no fim (Tarefa 9).

## Mapa de arquivos

| Arquivo | Papel |
| --- | --- |
| `range/RangeTiers.java` (novo, puro) | Arrays padrão de raio e máquinas por posição do tier |
| `range/RangeRules.java` (novo, puro) | Distância de Chebyshev, dentro do raio, cabe no limite, motivo da recusa (`LinkRefusal`) |
| `range/RemoteSide.java` (novo, puro) | Lados relativos à frente: de/para face absoluta (índices de `Direction`) e de/para o quadro canônico |
| `range/MachineConfig.java` (novo) | `FaceConfig[tipo][RemoteSide]`, com cópia, igualdade e NBT |
| `range/MachineGroup.java` (novo) | Grupo: id `int`, id do bloco, nome, perfil (`MachineConfig`) e cartões `[tipo com cartão][RemoteSide][2]` |
| `range/RemoteMachine.java` (novo) | Máquina vinculada: posição, grupo, exceção (`MachineConfig` ou nulo), última frente conhecida |
| `range/RemoteMachines.java` (novo) | O conjunto no roteador: vincular, desvincular, grupos, alvo de edição, NBT, cartões soltos ao esvaziar um grupo |
| `range/MachineCaches.java` (novo) | Os nove `BlockCapabilityCache` de uma máquina (extraídos do `RouterBlockEntity`), com o `isLoaded` das remotas |
| `range/RemoteIndex.java` (novo) | Por nível: posição → máquinas remotas vinculadas (o conjunto do mixin) e chunk → máquinas, para acordar na carga do chunk |
| `mixin/LevelMixin.java`, `resources/wirelessautomate.mixins.json`, `neoforge.mods.toml` (novo/mudar) | O mixin em `Level.blockEntityChanged` |
| `range/RangeLinks.java` (novo) | Regras do servidor do modo Máquinas: escolher roteador, vincular ou desvincular uma, vincular a área |
| `item/RangeUpgradeItem.java` (novo) | O Upgrade de Alcance (tooltip com raio e limite por tier) |
| `block/RouterBlockEntity.java` (mudar) | Slot do upgrade de alcance, `RemoteMachines`, caches pela `MachineCaches`, face e cartões por alvo |
| `network/Port.java`, `NodePorts.java`, `NetworkRoutes.java`, `NetworkManager.java` (mudar) | Porta por máquina, portas das remotas, acordar pelo motor |
| `network/ItemTransfer.java`, `FluidTransfer.java`, `ChemicalTransfer.java`, `ScalarTransfer.java`, `ScalarAccess.java`, `EnergyAccess.java`, `compat/arsnouveau/ArsSources.java`, `compat/mekanism/*` (mudar) | Capability pela máquina da porta, não pelo nó |
| `linker/LinkerMode.java`, `linker/LinkerActions.java`, `item/LinkerItem.java`, `item/ConfiguratorItem.java`, `preset/PasteMode.java`, `client/LinkerScreen.java`, `registry/ModDataComponents.java` (mudar) | Modo Máquinas e componente `linker_router` |
| `Config.java` (mudar) | `machineRadius` e `maxMachines` por tier |
| `menu/RouterMenu.java`, `menu/RouterSnapshot.java`, `menu/RouterFaceFilterTarget.java`, `packet/ModPayloads.java` + payloads novos (mudar) | Segundo slot de upgrade, alvo de edição, visão das máquinas, protocolo 13 |
| `client/RouterScreen.java` (mudar) | Tela mínima: botão Máquinas, lista de grupos e máquinas, lados relativos, Voltar ao perfil e Desvincular |
| `registry/ModItems.java`, `ModCreativeTabs.java`, `data/wirelessautomate/recipe/range_upgrade.json`, `scripts/textures/gerar_texturas.py`, `lang/*.json` (mudar) | Item, receita, textura e textos |
| `gametest/RangeGameTests.java` (novo) | GameTests da etapa |
| `client/DevEndToEnd.java` (mudar) | e2e com a tela mínima |
| `CLAUDE.md`, `docs/progresso.md` (mudar) | Mapa do código e estado |

---

### Tarefa 1: lógica pura (tiers, regras e lados)

**Arquivos:** criar `range/RangeTiers.java`, `range/RangeRules.java` e `range/RemoteSide.java`, com testes em `src/test/java/.../range/`.

- [ ] `RangeTiers`: `static int defaultRadius(int tierOrdinal)` e `static int defaultMaxMachines(int tierOrdinal)` sobre os arrays das Restrições globais; `MAX_RADIUS = 64`, `MAX_MACHINES = 512`. O teste confere 8 posições e os valores.
- [ ] `RangeRules`: `static int chebyshev(int x1, int y1, int z1, int x2, int y2, int z2)`, `static boolean withinRadius(...)` e `enum LinkRefusal { NONE, NO_UPGRADE, NO_RANGE, OTHER_DIMENSION, OUT_OF_RANGE, LIMIT, NOT_A_MACHINE, SELF, ROUTER, ATTACHED, PROTECTED }` com `key()` (`range.refusal.<nome em minúsculas>`). Também `static LinkRefusal check(boolean hasUpgrade, int radius, boolean sameDimension, int distance, int count, int max)`, na ordem: upgrade, raio > 0, dimensão, distância, limite. Testes de cada recusa e do limite exato (count == max recusa).
- [ ] `RemoteSide` (`FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM`, `key()` em minúsculas), só com inteiros:
  - faces absolutas pelos índices de `Direction.ordinal()` (DOWN 0, UP 1, NORTH 2, SOUTH 3, WEST 4, EAST 5);
  - frente horizontal pelo mesmo índice, ou `-1` para "sem frente", que equivale a NORTH;
  - `static RemoteSide fromAbsolute(int front, int absolute)` e `int toAbsolute(int front)`;
  - `static RemoteSide fromCanonical(int absolute)` e `int canonical()`, que são o caso frente = NORTH.

  Convenção, com a frente em NORTH: LEFT = EAST e RIGHT = WEST. Girar a frente gira os lados horizontais no sentido horário (`getClockWise`: N→E→S→W). Testes: ida e volta para as 4 frentes × 6 lados, frente −1 igual a NORTH, e o caso "frente EAST: Trás = WEST, Esquerda = SOUTH".
- [ ] `./gradlew test` passa. Commit "Roteador de alcance: lógica pura de tiers, regras e lados".

### Tarefa 2: config, item e o segundo slot de upgrade

**Arquivos:** `Config.java`, `item/RangeUpgradeItem.java`, `registry/ModItems.java`, `registry/ModCreativeTabs.java`, `recipe/range_upgrade.json`, `gerar_texturas.py` (+ PNG e modelo gerados), `lang/*.json`, `block/RouterBlockEntity.java`, `block/RouterBlock.java` (drop ao quebrar), `menu/RouterMenu.java`, `gametest/RangeGameTests.java`.

- [ ] Config: em `TierValues`, `IntValue machineRadius` (`defineInRange("machineRadius", RangeTiers.defaultRadius(ord), 0, 64)`) e `IntValue maxMachines` (`0..512`), com comentários nos dois sentidos ("Raio, em blocos, em que o Upgrade de Alcance vincula máquinas (0 = este tier não aceita o upgrade)" e "Máximo de máquinas vinculadas pelo Upgrade de Alcance"). Acessores `Config.machineRadius(RouterTier)` e `Config.maxMachines(RouterTier)` com `Config.read`.
- [ ] Item `range_upgrade` (`RangeUpgradeItem`, `stacksTo(1)`), na aba criativa logo após o `chunk_loader_upgrade`. Tooltip:
  - uma linha por tier carregado (`RouterTier.loaded()`) com raio > 0, no formato `Avançado: 6 blocos · 8 máquinas`;
  - uma linha "Vincule máquinas com o Vinculador no modo Máquinas".
- [ ] Receita *provisória* `crafting_shaped`, categoria `redstone`: `PEP / RCR / PEP`, com P = `minecraft:ender_pearl`, E = `minecraft:ender_eye`, R = `minecraft:redstone_block` e C = `wirelessautomate:linker`. O resultado é 1 `range_upgrade`.
- [ ] Textura: um sprite novo no `gerar_texturas.py`, no molde do `chunk_loader_upgrade`, com um motivo de antena ou ondas. Gere pelo script, nunca à mão. O dono aprova o visual no fim da etapa.
- [ ] `RouterBlockEntity`:
  - campo `ItemStack rangeUpgrade`, chave `range_upgrade`;
  - `static boolean acceptsRangeUpgrade(ItemStack)`;
  - `boolean acceptsRangeUpgradeHere()`, que exige `Config.machineRadius(tier()) > 0`;
  - `rangeUpgrade()`, `setRangeUpgrade(ItemStack)` e `removeRangeUpgrade()`;
  - `boolean rangeActive()`: tem o upgrade e o raio do tier é > 0.

  `setRangeUpgrade` chama `changed(ALL)` (o motor remonta as portas do nó).
- [ ] Ao quebrar o roteador, o upgrade de alcance cai como o de chunk loading (siga o caminho do `upgrade` no `RouterBlock`/loot).
- [ ] `RouterMenu`: `RANGE_SLOT = SLOTS_END + 1` (os índices de antes não mudam), um `UpgradeSlot` (ou parecido) que aceita só o upgrade de alcance quando `acceptsRangeUpgradeHere()`, e o `quickMoveStack` mandando o upgrade de alcance para esse slot. No cliente, um `SimpleContainer` a mais. O `placeSlots` passa a receber a posição dos dois slots; a tela os põe lado a lado no cabeçalho (`w - 26 - 18` e `w - 26`) e o fim da pílula do tier recua 18 px. A tela desenha o fundo e o fantasma do slot novo.
- [ ] GameTests em `RangeGameTests`:
  - o slot aceita o upgrade num Avançado e recusa num Básico;
  - `range_upgrade` sobrevive a `saveWithFullMetadata`/`loadWithComponents`;
  - quebrar o roteador solta o upgrade.
- [ ] Verificação completa e e2e (a tela mudou). Commit "Roteador de alcance: Upgrade de Alcance, config e o segundo slot".

### Tarefa 3: modelo das máquinas remotas, grupos, perfis e exceções

**Arquivos:** `range/MachineConfig.java`, `range/MachineGroup.java`, `range/RemoteMachine.java`, `range/RemoteMachines.java`, `block/RouterBlockEntity.java`, `gametest/RangeGameTests.java`.

- [ ] `MachineConfig`:
  - `FaceConfig[ResourceType][RemoteSide]`, nunca nulo;
  - `copy()` e `boolean sameAs(MachineConfig)` (modo, prioridade, redstone e filtro iguais em todos);
  - `CompoundTag save(Provider)` e `static load(CompoundTag, Provider)`, no formato `tipo → lado → FaceConfig.save()`, sem os padrões, como as `faces` do roteador.
- [ ] `MachineGroup`:
  - `int id`, `ResourceLocation block`, `String name` (vazio = o nome do bloco), `MachineConfig profile`;
  - `ItemStack[cardTypes][6][CARD_SLOTS] cards`, no layout dos `cards` do roteador mas por `RemoteSide`;
  - NBT com as chaves `id`, `block`, `name`, `profile` e `cards`.
- [ ] `RemoteMachine`:
  - `BlockPos pos`, `int group`, `@Nullable MachineConfig exception`, `int front` (índice ou −1);
  - NBT com `pos` (long), `group`, `exception` (opcional) e `front`.
- [ ] `RemoteMachines`:
  - lista ordenada das máquinas (a ordem de vínculo decide quem passa do limite: as primeiras `maxMachines` ficam ativas) e dos grupos, mais `nextGroupId`;
  - `link(BlockPos, ResourceLocation block, int front)` põe a máquina no primeiro grupo daquele bloco, ou cria um grupo com perfil vazio;
  - `unlink(BlockPos)`, que remove o grupo quando ele esvazia e devolve os cartões dele (`List<ItemStack>`) para quem chamou soltar;
  - `contains(pos)`, `machines()`, `groups()`, `group(id)`, `index(pos)`;
  - `MachineConfig effective(RemoteMachine)`: a exceção, ou o perfil do grupo.
- [ ] Alvo de edição: `record EditTarget(int kind, int id)`, com `ATTACHED`, `GROUP(id)` e `MACHINE(index)`. API no `RouterBlockEntity`, toda por alvo, com o lado no quadro canônico (`Direction`):
  - `FaceConfig face(EditTarget, ResourceType, Direction)`;
  - `configureFace(EditTarget, ...)`, `setFilter(EditTarget, ...)`, `card(...)`/`setCard(...)`.

  Regras:
  - `ATTACHED` usa os métodos de hoje, sem mudar o comportamento;
  - editar `MACHINE` sem exceção copia o perfil para a exceção e aplica;
  - quando a exceção volta a ficar igual ao perfil (`sameAs`), ela some;
  - cartões num alvo `MACHINE` leem e gravam os do grupo dela.

  Também `revertToProfile(index)` e `unlinkMachine(index)`, que solta os cartões do grupo vazio na posição do roteador. Toda mudança passa por `changed(...)`; uma mudança de perfil é um `batchChanges` só.
- [ ] NBT do roteador: `machines` e `groups`, escritos só se não vazios e lidos só se presentes. O NBT antigo carrega com as listas vazias. Quebrar o roteador solta os cartões de todos os grupos (junto do `removeAllCards`).
- [ ] GameTests:
  - ida e volta do NBT com dois grupos, uma exceção e cartões;
  - NBT de antes da etapa (sem as chaves) carrega e funciona;
  - perfil mudado vale para as máquinas sem exceção e não muda a exceção;
  - exceção igual ao perfil some;
  - desvincular a última máquina do grupo solta os cartões.
- [ ] Verificação completa. Commit "Roteador de alcance: máquinas, grupos, perfis e exceções no roteador".

### Tarefa 4: o motor com várias máquinas por nó

**Arquivos:** `range/MachineCaches.java`, `block/RouterBlockEntity.java`, `network/Port.java`, `NodePorts.java`, `NetworkRoutes.java`, `NetworkManager.java`, as quatro `*Transfer`, `ScalarAccess`, `EnergyAccess`, `compat/arsnouveau/ArsSources.java`, `network/Chemicals.java`/`compat/mekanism`, `gametest/RangeGameTests.java`.

- [ ] `MachineCaches`: extraia do `RouterBlockEntity` os nove arrays de cache e o helper `capability(...)` para uma classe criada com o roteador, a posição da máquina e um callback de invalidação. Ela expõe `items(Direction)`, `bulkItems`, `fluids`, `bulkFluids`, `energy`, `bulkEnergy`, `chemicals` (Object), `bulkSource` e `arsSource` (Object).
  - A máquina presa usa uma instância refeita quando o `facing` muda (o mesmo comportamento de hoje, com `cacheFacing`). Os métodos `RouterBlockEntity.items(face)` e afins continuam e delegam a ela, para o snapshot e os testes de hoje não mudarem.
  - Numa remota, `capability(...)` devolve `null` sem tocar no mundo quando `!level.isLoaded(pos)`.
  - A invalidação de uma remota chama `RouterBlockEntity.remoteInvalidated(pos, type, face)`, que relê a frente (só com o chunk carregado). Se a frente mudou, `changed(ALL)`; senão, `NetworkManager.capabilityChanged` da porta.
- [ ] `Port` ganha `final BlockPos machinePos` (passa a ser final; o construtor recebe a posição) e `final MachineCaches machine`. As transferências trocam `x.node.items(x.face)` por `x.machine.items(x.face)` em todos os pontos do mapa (Item :82, 86, 247, 248, 451, 569, 601; Fluid :43, 44, 153, 154; Chemical :35, 140), e `ScalarAccess.handler` passa a receber `(MachineCaches, Direction)`. O `sameMachine` e o `sameEndpoint` não mudam (comparam `machinePos`).
- [ ] `NodePorts`: o array de hoje fica para a máquina presa. As remotas vão num `Long2ObjectOpenHashMap<Port[]>` por `pos.asLong()`, com `types × 6` posições, criado sob demanda. As portas de uma máquina desvinculada saem na releitura (descarte as chaves que não estão mais em `RemoteMachines`). A identidade de uma porta é (nó, posição da máquina, tipo, face), e ela sobrevive às remontagens.
- [ ] `NetworkRoutes.readPorts`: depois das 6 faces da presa, se `node.rangeActive()`, percorre as primeiras `maxMachines` máquinas dentro do raio (as fora do raio ou do limite não viram porta). Para cada uma:
  - a face absoluta sai de `RemoteSide.toAbsolute(machine.front)`;
  - a configuração é `effective(machine)` naquele lado;
  - o filtro é o embutido mais os cartões do grupo naquele lado (`FilterSet.of`);
  - o `powered` é o do roteador.

  O resto do laço (prioridade, tier, origem e destino) é o de hoje.
- [ ] `RouterBlockEntity.filterSet` ganha a variante por máquina remota.
- [ ] GameTests:
  - baú A preso, baú B remoto a 4 blocos: itens vão de A para B pela mesma rede;
  - máquina remota com frente (use um bloco vanilla com `HORIZONTAL_FACING` e inventário, como a fornalha): o perfil "Trás → Extrair" tira do lado certo em duas frentes diferentes;
  - máquina fora do raio (config temporária com raio menor, em `try/finally`) não move;
  - a 9ª máquina num Avançado (limite 8) não move;
  - sem o upgrade, nenhuma remota move;
  - tirar o upgrade para as remotas.
- [ ] Verificação completa. Rode também `./scripts/bench.sh` num cenário curto, se couber no tempo, e anote no relatório que o custo com nós sem remotas não mudou (é só o `Port` com dois campos a mais). Commit "Roteador de alcance: o motor move de e para as máquinas remotas".

### Tarefa 5: acordar como a máquina presa (mixin e carga de chunk)

**Arquivos:** `range/RemoteIndex.java`, `mixin/LevelMixin.java`, `src/main/resources/wirelessautomate.mixins.json`, `src/main/templates/META-INF/neoforge.mods.toml`, `network/NodePorts.java`, `NetworkManager.java`, `WirelessAutomate.java` (evento de chunk), `gametest/RangeGameTests.java`.

- [ ] `RemoteIndex` (um por servidor, como o `NetworkManager`, limpo na parada): por nível, `Long2ObjectOpenHashMap<List<RouterBlockEntity>>` de posição da máquina → roteadores que a vinculam (uma máquina pode estar em vários), e `chunk → posições`. É mantido quando um nó entra ou sai do motor (`addNode`/`removeNode`) e quando uma máquina é vinculada ou desvinculada. Só os nós com `rangeActive()` registram as remotas.
- [ ] Mixin `@Inject(at = HEAD)` em `Level.blockEntityChanged(BlockPos)`: só no servidor (`!isClientSide`); se o mapa daquele nível está vazio, sai na hora; senão `get(pos.asLong())` e, para cada roteador, `NetworkManager.get().wakeMachine(router, pos)`. Nada de alocação nem de busca de capability no caminho.
- [ ] `NetworkManager.wakeMachine(RouterBlockEntity, BlockPos)` e `NodePorts.wakeMachine(BlockPos, boolean feeders)`: a mesma regra do `wake(node)` de hoje, só nas portas daquela máquina (os dois backoffs, `idleSlots = 0`; feeders com `waitsDestination` só quando não está entregando nela). O `deliveringInto` passa a comparar a posição da máquina avisada com a `visitMachine`.
- [ ] Carga de chunk: no `ChunkEvent.Load` de um `ServerLevel`, `RemoteIndex` acorda as máquinas daquele chunk e relê a frente delas.
- [ ] Mixin no build: `wirelessautomate.mixins.json` (`"package": "io.github.matheusanbs.wirelessautomate.mixin"`, `"compatibilityLevel": "JAVA_21"`, `"mixins": ["LevelMixin"]`, `"required": true`, `"minVersion": "0.8"`) e `[[mixins]] config="wirelessautomate.mixins.json"` no `neoforge.mods.toml`. Confira no jar que o JSON e o toml saem certos e que as quatro runs carregam.
- [ ] GameTests (caminho real, sem chamar o `wakeMachine` à mão):
  - origem remota (um baú) vazia, com o backoff forçado no máximo: pôr um item no baú pelo `IItemHandler` dele (que chama `setChanged`) faz o item seguir para o destino em menos de 20 ticks;
  - destino remoto cheio e dormindo: tirar um item dele pelo handler o acorda;
  - a carga de chunk: chame o método do `RemoteIndex` que o evento chama e confira a porta acordada;
  - sem o upgrade, a posição não fica no índice.
- [ ] Verificação completa. Se der, uma rodada curta do `./scripts/bench.sh` para conferir que o mixin não aparece no custo. Commit "Roteador de alcance: as máquinas remotas acordam o roteador como a presa (mixin e carga de chunk)".

### Tarefa 6: Vinculador no modo Máquinas

**Arquivos:** `linker/LinkerMode.java`, `range/RangeLinks.java`, `item/LinkerItem.java`, `linker/LinkerActions.java`, `item/ConfiguratorItem.java`, `preset/PasteMode.java`, `client/AreaRenderer.java`, `client/LinkerScreen.java`, `menu/LinkerSnapshot.java`, `registry/ModDataComponents.java`, `lang/*.json`, `gametest/RangeGameTests.java`.

- [ ] `LinkerMode` ganha `MACHINES` no fim. `next()` faz SINGLE → AREA → MACHINES → SINGLE no Vinculador. O Configurador **não** entra em MACHINES: ele continua alternando só SINGLE e AREA (o `toggled()` dele trata MACHINES como SINGLE), e `PasteMode` e `AreaRenderer` tratam MACHINES como fora da área do Configurador.
- [ ] Componente novo `linker_router` (`GlobalPos`, persistente e de rede): o roteador escolhido.
- [ ] Gestos no modo Máquinas (`LinkerItem.useOn`/`use`), validados no servidor:
  - clique num roteador escolhe esse roteador; a action bar diz `<nome> · 7/32 máquinas` e, sem upgrade, `range.refusal.no_upgrade`;
  - clique em outro bloco vincula ou desvincula aquela máquina pelo `RangeLinks.toggle`;
  - Shift + clique marca os cantos (o `markCorner` de hoje);
  - clique no ar abre a tela do Vinculador;
  - Shift + clique no ar passa ao próximo modo.
- [ ] `RangeLinks` (servidor):
  - o roteador escolhido precisa estar carregado, na mesma dimensão, e `mayInteract` vale para ele e para a máquina;
  - a máquina é recusada se for o próprio roteador, outro roteador ou a máquina presa;
  - `RangeRules.check` decide raio e limite;
  - "é máquina" quer dizer alguma capability de um tipo carregado (`LoadedTypes`) com lado nulo ou em qualquer face. Os químicos e a Source passam pelas pontes `Chemicals` e `Sources`.
  
  Mensagens na action bar: `Linha de Minério · 12/32 máquinas` e as recusas pelo `LinkRefusal.key()`, com números (`Fora do alcance: 21 blocos (raio 16)`, `Limite do tier Esmeralda: 32 máquinas`).
- [ ] Área no modo Máquinas: o botão Vincular da tela do Vinculador (op `LINK`) vincula as máquinas da área ao roteador escolhido. Varre só block entities de chunks carregados (como o `LinkerScan`), filtra pelas caixas de tipo (`LinkerTabs`: entra quem tem capability de um tipo marcado), respeita `mayInteract`, raio e limite, e pula as já vinculadas. Resultado na action bar: `Vinculadas 9 · 3 fora do alcance · 2 além do limite`. A coluna de prévia da tela é da etapa 2; agora basta o botão funcionar e o terceiro botão de modo caber (`LinkerScreen` monta os botões por `values()`; confira a largura do cabeçalho).
- [ ] GameTests pelo `RangeLinks`:
  - vincula e desvincula com o clique;
  - recusa fora do raio, além do limite, outro roteador, a máquina presa e um bloco sem capability;
  - a área vincula só o que tem block entity e um tipo marcado;
  - o Configurador não chega a MACHINES.
- [ ] Verificação completa e e2e (a tela do Vinculador mudou). Commit "Roteador de alcance: Vinculador no modo Máquinas".

### Tarefa 7: menu, snapshot e payloads por alvo

**Arquivos:** `menu/RouterMenu.java`, `menu/RouterSnapshot.java`, `menu/RouterFaceFilterTarget.java`, `packet/ModPayloads.java`, payloads novos em `packet/`, `gametest/RangeGameTests.java`.

- [ ] `RouterMenu` ganha o alvo de edição (`EditTarget`, começa em `ATTACHED`). `SetFacePayload`, filtro de face e cartões passam a valer para o alvo, e um alvo que deixou de existir volta para `ATTACHED`. Com o alvo `MACHINE`, os slots de cartão mostram os do grupo.
- [ ] `RouterSnapshot` ganha um `RangeView`, só com `rangeActive()` (senão vazio; o `sameBody` e o `hashCode` passam a incluí-lo):
  - `radius`, `max`;
  - grupos: id, nome, `ItemStack` do bloco, contagem e exceções;
  - máquinas: posição, grupo, distância, exceção e estado (`LOADED`, `UNLOADED`, `MISSING`, `OUT_OF_RANGE`, `OVER_LIMIT`). O estado é lido só na captura: `isLoaded` primeiro; `MISSING` quando o bloco não é mais o do grupo ou perdeu toda capability;
  - o alvo atual.

  As `faces` do snapshot passam a ser as do alvo, no quadro canônico. A `machineState` é a do alvo: o bloco da máquina escolhida, ou o primeiro carregado do grupo, virado para Norte para o visor.
- [ ] Payloads novos (`playToServer`, validados como o `SetFacePayload`, pelo `router(player, containerId)`): `SelectTargetPayload(containerId, kind, id)`, `RevertMachinePayload(containerId, index)` e `UnlinkMachinePayload(containerId, index)`. `ModPayloads.VERSION = "13"`.
- [ ] Handlers públicos e GameTests:
  - selecionar um grupo e mudar uma face muda o perfil;
  - selecionar uma máquina e mudar cria a exceção;
  - reverter tira a exceção;
  - desvincular pela tela solta os cartões do grupo vazio;
  - alvo inválido é ignorado;
  - o snapshot sem o upgrade é igual ao de antes.
- [ ] Verificação completa. Commit "Roteador de alcance: alvo de edição, snapshot e payloads (protocolo 13)".

### Tarefa 8: tela mínima

**Arquivos:** `client/RouterScreen.java` (e um widget novo em `client/` se ajudar), `lang/*.json`, `client/DevEndToEnd.java`.

- [ ] Com `RangeView` presente, aparece um botão **Máquinas · 12/32** na coluna da esquerda, acima dos botões de face. Ele alterna o visor 3D com uma lista no mesmo espaço:
  - a linha "Máquina presa" no topo;
  - os grupos (`Fornalha ×4`, com `1 exceção`) que abrem e fecham;
  - as máquinas de cada grupo (`#3 · 12 blocos`), com uma bolinha de estado: verde carregada, âmbar descarregada, vermelha sumiu, cinza fora do raio ou do limite. O tooltip explica o estado ("Volta a mover quando o chunk carregar").
  
  A lista rola com a roda e todo texto passa por `GuiText`.
- [ ] Clicar numa linha manda o `SelectTargetPayload`. Os botões de face mostram os nomes relativos nas remotas (Cima, Frente, Esq., Baixo, Trás, Dir.).
- [ ] O cabeçalho da coluna da direita diz o alvo:
  - para um grupo, `Perfil: Fornalha · vale para 3 de 4`;
  - para uma máquina, `segue o perfil` ou `exceção`, com os botões **Voltar ao perfil** (só na exceção) e **Desvincular**.

  Não dá para desvincular a máquina presa.
- [ ] O visor 3D, com o alvo remoto, mostra o bloco do alvo (vem no snapshot) com a frente para o jogador.
- [ ] Sem o upgrade, a tela é a de hoje, pixel a pixel.
- [ ] e2e: um roteador Avançado com o upgrade, dois baús e uma fornalha vinculados, capturas da lista aberta, de um grupo escolhido e de uma máquina em exceção (`range-1..3`), com a volta em `pt_br` e `GuiText.clipCount()==0`. Olhe as capturas.
- [ ] Verificação completa e e2e. Commit "Roteador de alcance: tela mínima com a lista de grupos e máquinas".

### Tarefa 9: fechamento da etapa

- [ ] `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium` e `./scripts/e2e.sh`.
- [ ] `CLAUDE.md`:
  - linha do pacote `range/` no mapa do código;
  - o slot novo e o alvo de edição no `RouterMenu`;
  - o protocolo 13 no `packet/`;
  - os `RangeGameTests` e a contagem nova na linha dos GameTests;
  - o modo Máquinas no `linker/`.
- [ ] `docs/progresso.md`: tabela (linha nova "Roteador de alcance", 🟡 etapa 1), próximo passo (o dono testa no jogo e responde as perguntas em aberto da spec; depois a etapa 2) e uma linha no histórico com os números de testes.
- [ ] Commit "Roteador de alcance: etapa 1 pronta para o teste do dono".
