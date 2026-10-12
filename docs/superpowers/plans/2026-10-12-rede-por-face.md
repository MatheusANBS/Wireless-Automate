# Rede por face: plano de implementação

> **Para agentes:** execute tarefa por tarefa, um subagente por tarefa, com revisão entre elas. Os passos usam caixas (`- [ ]`). Cada tarefa termina compilando, com os testes passando e com um commit.

**Objetivo:** a rede deixa de ser da aba e passa a ser da face: cada face da máquina, em cada tipo de recurso, está numa rede ou em nenhuma. Vale para a máquina presa, para o perfil de grupo e para a exceção do roteador de alcance, para o preset do Configurador e para o Vinculador (linha Faces). A tela do roteador ganha a linha **Rede** da face selecionada e passa a mostrar os lados relativos à máquina também na presa.

**Spec:** [`docs/superpowers/specs/2026-10-12-rede-por-face-design.md`](../specs/2026-10-12-rede-por-face-design.md), que é a fonte das decisões (não reabra nenhuma). Mockup aprovado: `docs/preview/rede-por-face.html` (v2; telas em pixels de GUI).

## Restrições globais

- Código, comentários, commits e docs em português; textos novos em `en_us.json` e `pt_br.json`, sempre os dois. Chaves de lang que deixarem de ser usadas saem dos dois arquivos.
- Ramo `roteador-de-alcance`. Commits sem linhas de atribuição (nada de `Co-Authored-By` nem `Claude-Session`), com o autor do dono. Um commit ao fim de cada tarefa, com a mensagem "Rede por face: ...". Sem push.
- `ModPayloads.VERSION` passa de `"13"` para `"14"` **uma vez**, na **Tarefa 3** (a primeira que muda um codec de rede: `TabletSnapshot` e `TabletMoveNodesPayload`). As Tarefas 5 e 6 mudam outros codecs sem subir de novo (o ramo não foi publicado entre elas).
- **Chaves existentes de NBT, componentes e config não mudam de significado.**
  - A chave `networks` do roteador (e a ainda mais antiga, `network`, única) passa a ser **só lida, para migrar**, e deixa de ser escrita.
  - O mesmo vale para `networks` (e `network`) no componente `preset` do Configurador e para `networks` na entrada do `NodeIndex`.
  - Chaves novas: `network` dentro do `FaceConfig` (ausente = sem rede; uma face padrão continua uma tag vazia); `face_networks` na entrada do `NodeIndex` (tipo → lista de UUID); componente `linker_faces` no Vinculador (máscara de 6 bits por `RemoteSide`, padrão 63).
- **Performance:**
  - nada de tick novo, nada de varredura do mundo (nem quando uma rede é apagada);
  - a rede da face é lida na montagem das rotas, junto com o resto do `FaceConfig`;
  - as redes distintas de um nó por tipo são recalculadas só no `nodeChanged`;
  - a frente da máquina presa é lida do `BlockState` só no snapshot da tela e no clique do Vinculador, nunca por tick;
  - nada de sincronizar o cliente com a tela fechada.
- Antes de qualquer consulta numa máquina remota, `level.isLoaded(pos)`. Vincule e desvincule remotas só por `RouterBlockEntity.linkMachine`/`unlinkMachine`.
- Mekanism, Ars e Allthemodium continuam opcionais: nenhum tipo deles fora dos pacotes `compat/` de sempre, nunca em assinatura de classe `@EventBusSubscriber` ou `@GameTestHolder`.
- Classes de tela só em `client/`. Todo texto variável de tela passa por `GuiText` (abreviado, com o inteiro na dica).
- GameTests: `@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`, template `"empty"`. **Cada teste cria as próprias redes** (`NetworkSavedData.get(server).create(...)`). Teste pertinência (`contains`), não contagem. Espere o `onLoad` com `startSequence().thenWaitUntil(...)`. Teste que muda a config vai no lote `wirelessautomate_config` e devolve o valor em `try/finally`.
- Lógica pura (sem classes do Minecraft) vai com JUnit em `src/test/java`.
- Compile antes de rodar jogo (`./gradlew compileJava`). Com `BUILD FAILED`, pare e conserte; nunca espere um resultado que não virá.
- Nunca rode dois jogos (ou builds com jogo) ao mesmo tempo. Não mexa em `run/options.txt`.
- **Verificação antes de cada commit:** `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource`. Nas tarefas que tocam tela ou payload (3, 5, 6 e 8), também o e2e: `WA_E2E="$PWD/run/e2e" ./gradlew runClient` (ou `./scripts/e2e.sh`), com `run/e2e/result.txt` = `OK`. A run `runGameTestServerAllthemodium` roda uma vez, no fim (Tarefa 8).
- Não invente: onde este plano diz "Pergunta", investigue ou pergunte ao dono antes de seguir.

## Pontos decididos (coordenação, 2026-10-12)

O código divergia da spec ou a spec não dizia em quatro pontos. Ficou assim (a spec foi corrigida):

1. **Colocar o roteador (decisão 4, Tarefa 1): continua como hoje, o roteador nasce sem rede.** Nada muda no `RouterBlock.setPlacedBy`; o `NetworkGameTests.placedRouterJoinsNoNetwork` fica. A decisão 5 (grupo novo na rede ativa de quem vincula) continua valendo.
2. **Tablet (decisão 11, Tarefa 3): sem filtro de rede.** Mover um nó pela aba T para a rede B põe **todas as faces do tipo T** (presa, perfis e exceções) em B; sem aba, todos os tipos. O `TabletMoveNodesPayload` **não** ganha `from`, e o `setNetworkAllFaces` não precisa do parâmetro `from`. Ignore o `moveOnlyFacesOfFilteredNetwork`; teste em vez disso que mover põe todas as faces do tipo em B e não toca nas outras abas.
3. **Comandos (decisão 13, Tarefa 4):** não há `/wa network` a migrar; só o ramo novo de rede no `/wa face`, se for simples.
4. **`SetNetworkPayload` (decisão 14, Tarefa 6): quadro do alvo**, como os outros payloads da tela (absoluto na presa, canônico em grupo e máquina remota).

Outras respostas: "faces em N redes" conta o nó todo; "Todas" só marca as seis; a vazão por rede no Tablet soma o total do tipo do nó em cada rede dele (anote no javadoc).

Interpretações que o plano assume (a spec não diz o contrário; anote no relatório se o código mostrar outra coisa):

- O **Vinculador** mexe só nas faces da **máquina presa** do roteador clicado (decisão 8: "lados relativos à máquina do roteador clicado"); perfis e exceções do alcance ficam como estão.
- **Mover pelo Tablet** e os **atalhos "todas as faces"** (testes, e2e, bench, `/wa face`) mexem na presa, nos perfis e nas exceções, como a rede da aba fazia antes.
- **Membro de uma rede** no motor: o nó é membro de toda rede que aparece em alguma face dele (presa, perfis e exceções), **sem olhar o modo**. É um superconjunto do "face ativa" da decisão 10: uma face em Nenhum não vira porta, então o resultado das rotas é o mesmo; o custo é só um membro sem portas. Assim um roteador recém-vinculado com as faces em Nenhum continua aparecendo na rede no Tablet, como hoje, e o redstone não mexe em pertencimento.

## Mapa de arquivos

Pacote base `src/main/java/io/github/matheusanbs/wirelessautomate/`.

| Arquivo | Papel na mudança |
| --- | --- |
| `network/FaceConfig.java` | Campo `network` (`UUID`, nulo = sem rede), codec, `isDefault`, `equals`/`hashCode`, `copyFrom` com a rede e `copySettingsFrom` sem ela |
| `block/RouterBlockEntity.java` | Sai o array `networks[]`; API de rede por face (presa, alvo de edição, máscara de faces, todas as faces); `networks(type)`; migração da tag `networks`; `attachedFront()` |
| `range/MachineConfig.java`, `range/RemoteMachines.java` | Perfil novo com a rede ativa (`link(..., network)`), migração das remotas |
| `network/Port.java`, `NodePorts.java`, `NetworkRoutes.java`, `NetworkManager.java`, `NodeProbe.java` | Rede por porta, redes distintas por tipo no nó, sujeira pela união das redes antigas e novas, cursores que sobrevivem com duas redes no mesmo nó |
| `network/NodeIndex.java`, `menu/TabletMenu.java`, `menu/TabletSnapshot.java`, `packet/TabletMoveNodesPayload.java`, `client/TabletScreen.java` | Redes por tipo no índice (`face_networks`), Lista, Estatísticas, busca, aviso "rede removida", mover por faces |
| `network/RouterPreset.java`, `preset/PresetApplier.java`, `item/ConfiguratorItem.java`, `preset/ConfiguratorArea.java` | Preset com a rede dentro de cada face; regra por rede distinta; preset antigo |
| `command/WaCommand.java` | `/wa face ... network <nome>` |
| `linker/LinkerFaces.java` (novo, puro), `linker/LinkerActions.java`, `linker/LinkerProblem.java`, `item/LinkerItem.java`, `registry/ModDataComponents.java`, `menu/LinkerSnapshot.java`, `menu/LinkerMenu.java`, `packet/LinkerActionPayload.java`, `client/LinkerScreen.java` | Linha Faces, componente `linker_faces`, vincular e desvincular por faces relativas |
| `menu/RouterSnapshot.java`, `menu/RouterMenu.java`, `packet/SetNetworkPayload.java`, `packet/ModPayloads.java`, `client/RouterScreen.java`, `client/MachineView3D.java` | Rede por face no snapshot, frente da máquina presa, linha Rede, pontos, resumo "N redes", lados relativos na presa |
| `range/RangeLinks.java` | Grupo novo nasce com a rede ativa de quem vincula |
| `gametest/FaceNetworkGameTests.java` (novo) e os GameTests existentes | Testes novos e migração dos chamadores |
| `bench/BenchScene.java`, `client/DevEndToEnd.java` | Atalhos e e2e com as capturas novas |
| `scripts/guide/gerar_guia.py`, `lang/*.json`, `CLAUDE.md`, `docs/progresso.md`, `docs/especificacao.md` | Guia, textos e docs |

---

### Tarefa 1: rede no `FaceConfig`, API do roteador, migração da tag e chamadores

**Objetivo:** o estado passa a ser só por face, sem mudar o comportamento visível. O motor, o Tablet, a tela e o Vinculador continuam como hoje (pelo atalho de transição `networkId(type)`) e mudam nas tarefas seguintes.

**Arquivos:** `network/FaceConfig.java`, `block/RouterBlockEntity.java`, `block/RouterBlock.java` (só se o ponto 1 pedir), `range/MachineConfig.java` (só javadoc), `network/RouterPreset.java` (transição), os chamadores de produção de `setNetworkId`/`networkId` (lista abaixo), `gametest/FaceNetworkGameTests.java` (novo), `gametest/NetworkGameTests.java` (ponto 1).

- [ ] `FaceConfig`:
  - campo `private @Nullable UUID network`, com `network()` e `boolean setNetwork(@Nullable UUID)` (devolve se mudou), como os outros setters;
  - no `CODEC`, um quinto campo `UUIDUtil.CODEC.lenientOptionalFieldOf("network")` (ausente = `null`; o `UUIDUtil.CODEC` grava o mesmo array de inteiros do `putUUID`). Confira que uma face padrão continua salvando uma tag vazia;
  - `isDefault()` passa a exigir `network == null`; `equals`, `hashCode` e `toString` incluem a rede. Com isso `MachineConfig.sameAs`, que usa `equals`, já compara a rede: uma exceção que só difere na rede continua exceção;
  - `copyFrom(other)` copia a rede também (usado na carga do NBT, no `reset()`, em `MachineConfig.copy/copyFrom/load` e na cópia do preset, que precisam dela);
  - novo `boolean copySettingsFrom(FaceConfig other)`: modo, prioridade, redstone e filtro, **sem** a rede. É o que o preset usa ao colar (a Tarefa 4 decide a rede à parte).
- [ ] `RouterBlockEntity`: sai o campo `UUID[] networks`. API nova (javadoc em cada método; toda mudança passa por `changed(typeBit)` ou `batchChanges` e só avisa se algo mudou):
  - `@Nullable UUID faceNetwork(ResourceType type, Direction machineFace)` e `void setFaceNetwork(ResourceType type, Direction machineFace, @Nullable UUID network)`: a presa, face absoluta (pelo `RelativeSide.fromAbsolute(facing(), spin(), face)` de sempre);
  - `@Nullable UUID faceNetwork(EditTarget target, ResourceType type, Direction side)` e `void setFaceNetwork(EditTarget target, ResourceType type, Direction side, @Nullable UUID network)`: pelo alvo de edição, no quadro da tela (como `configureFace(EditTarget, ...)`). Num alvo remoto, pelo `editRemote(target, type, side, config -> config.setNetwork(network))` que já existe: editar uma máquina sem exceção cria a exceção, e a exceção igual ao perfil some;
  - `int setAttachedFacesNetwork(Collection<ResourceType> types, int absoluteFaceMask, @Nullable UUID network)`: as faces absolutas da máscara (bit = `Direction.ordinal()`), nos tipos dados, num `batchChanges` só; devolve quantas faces mudaram (Vinculador, Tarefa 5);
  - `boolean attachedFacesIn(Collection<ResourceType> types, int absoluteFaceMask, @Nullable UUID network)`: todas essas faces já estão em `network` (o "já estavam" do Vinculador);
  - `void setNetworkAllFaces(Collection<ResourceType> types, @Nullable UUID from, @Nullable UUID to)`: as seis faces da presa, dos perfis de todos os grupos e das exceções, nos tipos dados; com `from` não nulo, só as faces que estão em `from` (o Tablet, Tarefa 3). Um `batchChanges` só;
  - `UUID[] networks(ResourceType type)`: as redes distintas (sem nulo, na ordem em que aparecem) das faces da presa, dos perfis e das exceções nesse tipo. Sem cache: só o motor (no `nodeChanged`), o índice e o snapshot chamam;
  - `boolean hasNetwork()`: alguma face (presa, perfil ou exceção) de algum tipo tem rede;
  - `int attachedFront()`: `RemoteMachines.frontOf(level.getBlockState(machinePos()))`, ou −1 sem nível. Só para a tela e o Vinculador (a máquina presa é vizinha do roteador, então o chunk está carregado).
- [ ] **Atalhos mantidos (decisão de churn):** os três `setNetworkId(...)` ficam, agora como uma linha cada que chama `setNetworkAllFaces(tipos, null, rede)`: `setNetworkId(UUID)` (todos os tipos), `setNetworkId(ResourceType, UUID)` e `setNetworkId(Collection<ResourceType>, UUID)`. Justificativa: têm exatamente o efeito de antes (a rede da aba valia para a presa e para as remotas), e são cerca de 70 chamadas em GameTests, no e2e e no bench que só preparam um roteador "numa rede". Renomear não ganha nada e espalha risco. O javadoc diz que é atalho e aponta para a API por face.
- [ ] `networkId(ResourceType type)` fica como **atalho de transição**: a rede comum às seis faces da presa no tipo, ou `null` se nenhuma tem rede **ou** se estão em redes diferentes. Javadoc: "só para testes, e2e e bench". Cerca de 110 asserções de GameTest leem `networkId(type)` depois de pôr o roteador inteiro numa rede e continuam valendo; os testes novos de faces mistas usam `faceNetwork`. Critério de pronto da Tarefa 6: `grep -rn 'networkId(' src/main/java --include=*.java | grep -v -e gametest -e DevEndToEnd -e bench` só acha a declaração.
- [ ] Chamadores de produção (contagem de partida: `grep -rnE 'networkId\(|setNetworkId|hasNetwork\(' src/main/java` dá ~180 ocorrências em ~32 arquivos, a maioria GameTests). Nesta tarefa:
  - `packet/ModPayloads.handleSetNetwork` (~:227): continua por aba, pelo atalho `setNetworkId(type, id)` (muda na Tarefa 6);
  - `linker/LinkerActions.inTarget`/`apply` (~:287, :296): continuam pelos atalhos (Tarefa 5);
  - `menu/TabletMenu.moveNodes` (~:745–747) e `network/NodeIndex.Entry.read` (~:173): pelos atalhos (Tarefa 3);
  - `network/NetworkManager` (~:141, :181) e `NodePorts`: `networkId(type)` (Tarefa 2);
  - `menu/RouterSnapshot.capture` (~:218) e `menu/LinkerSnapshot.capture` (~:152): `networkId(type)` (Tarefas 6 e 5);
  - `network/RouterPreset.copyOf`/`apply` (~:84, :120), **transição**: `copyOf` copia as faces com `copySettingsFrom` (o preset continua sem rede nas faces, igual ao de hoje, com o `networks[]` por tipo pelo `networkId(type)`), e `RouterBlockEntity.setFace(type, side, config)` passa a usar `copySettingsFrom` (colar não mexe na rede das faces; a rede vem do `setNetworkId(type, network)` de sempre). A Tarefa 4 troca isso pela rede por face;
  - `bench/BenchScene` (~:295) e `client/DevEndToEnd`: atalhos, sem mudança.
- [ ] **Migração na carga** (`loadAdditional`, hoje ~:1266–1273): leia a tag antiga numa variável local (`UUID[] legacy`, por tipo, de `networks` e, sem ela, da chave única `network`), e aplique **depois** de carregar `faces`, `cards` e `remotes`. Para cada tipo com rede antiga, um `migrateLegacyNetwork(type, id)` privado põe `id` nas faces com `network() == null`: as seis da presa, as seis de cada perfil (`remotes.groups()`) e as seis de cada exceção (`remotes.machines()` com `exception() != null`). Faces que já têm rede não mudam (decisão 3). Não chame `changed` aqui (na carga do chunk não há nível); o fim da carga já faz `changeVersion++` e avisa o motor.
- [ ] `saveAdditional` (~:1190): sai o bloco que escreve `networks`. A rede vai dentro das `faces` (e dos perfis e exceções pelo `MachineConfig.save`, que já usa `FaceConfig.save`).
- [ ] `spinTo` não muda: a rede mora no `FaceConfig`, que gira junto e fica no mesmo lado absoluto.
- [ ] Ponto 1 (colocar o roteador): **se o dono confirmar a letra da decisão 4**, em `RouterBlock.setPlacedBy`, com `placer` sendo `ServerPlayer`: a rede ativa (`NetworkSavedData.activeNetwork`, sem criar) que ele pode usar (`WaNetwork.canUse`) vai para as faces **ainda sem rede** de todos os tipos carregados (um item com dados do block entity mantém as redes que trouxe); mudam o javadoc do método e o `placedRouterJoinsNoNetwork` (passa a conferir as faces na rede ativa e o caso sem rede ativa). **Se ficar "como hoje"**, nada muda no `setPlacedBy` e o teste fica.
- [ ] GameTests novos em `FaceNetworkGameTests` (rede própria por teste):
  - `faceNetworkRoundTrip`: uma face com rede e modo Nenhum sobrevive a `saveWithFullMetadata`/`loadWithComponents`; uma face sem nada continua uma tag vazia;
  - `legacyNetworksMigrateToFaces`: tag montada à mão com `networks` {itens: A, energia: B} e uma face de itens configurada: as seis faces de itens em A, as de energia em B, os outros tipos sem rede; salva de novo, a tag não tem `networks`;
  - `legacySingleNetworkMigrates`: a chave única `network` vai para as seis faces de todos os tipos;
  - `legacyMigrationKeepsOwnFaceNetwork`: uma face com `network` próprio na tag não é sobrescrita;
  - `legacyMigrationReachesProfilesAndExceptions`: com `groups` e `machines` (um perfil e uma exceção) na tag, as faces dos dois recebem a rede do tipo;
  - `allFacesShortcut`: `setNetworkId(type, id)` põe a presa, um perfil e uma exceção na rede; `networks(type)` devolve só `id`;
  - `exceptionDiffersOnlyByNetwork`: mudar só a rede de uma face de uma máquina remota cria a exceção; voltar à rede do perfil a apaga;
  - `spinKeepsFaceNetwork`: girar (`spinTo`) mantém a rede no mesmo lado absoluto.
- [ ] Verificação completa (sem e2e: nenhuma tela ou payload mudou). Commit "Rede por face: rede no FaceConfig, API do roteador e migração da rede da aba".

### Tarefa 2: o motor com a rede de cada porta

**Arquivos:** `network/Port.java`, `NodePorts.java`, `NetworkRoutes.java`, `NetworkManager.java`, `NodeProbe.java`, `gametest/FaceNetworkGameTests.java`, `gametest/RangeGameTests.java` (ajustes).

**O risco (confirmado no código):** `NodePorts.collected[type]` é a leitura das portas ativas de um tipo, uma por tipo, dividida entre as redes. Quando uma rede monta e acha `collected == null`, o `NetworkRoutes.collectPorts` faz `member.rememberOrders(type)` e `member.clearRoutes(type)` em **todas** as portas do tipo e relê. Com portas do mesmo tipo em duas redes no mesmo nó:

- a segunda rede a montar no mesmo tick vê `fresh == false` e faz `port.previousOrder = port.order`, mas a ordem já foi limpa pela primeira: os cursores se perdem e as origens acordam;
- pior: se a outra rede não estiver suja naquele tick, as portas dela ficam sem rota (`order == null`, `network == null`) até ela remontar, e param. Hoje o `nodeChanged` só suja a rede do tipo (`markDirty(network, type)`) ou as duas do `move`.

**Solução: frescor por porta e sujeira pela união das redes.**

- [ ] `Port` ganha `@Nullable UUID faceNetwork` (a rede da face, lida com a configuração) e `boolean pendingFresh` (relida e ainda não consumida pela rede dela). O `network` (`NetworkRoutes`) que já existe continua sendo a rede da rota, preenchida só pelo `collectPorts` da rede que adota a porta.
- [ ] `NodePorts`: `UUID[] networks` vira `UUID[][] networks` (por tipo, as redes distintas; `NO_NETWORKS = new UUID[0]`), com `networks(type)`, `setNetworks(type, UUID[])`, `inNetwork(type, UUID)` e `inNetwork(UUID)` (algum tipo). Novo `clearRoutes(type, NetworkRoutes owner)`: limpa só as portas do tipo com `port.network == owner` (presa e remotas). O `clearRoutes(type)` e o `rememberOrders(type)` de hoje ficam só para a releitura.
- [ ] `NetworkRoutes.readPorts`/`readRemotePorts`: deixam de fazer `port.network = this`; fazem `port.faceNetwork = config.network()` (na remota, a da configuração efetiva, `face.network()`) e `port.pendingFresh = true`. O resto da leitura não muda.
- [ ] `NetworkRoutes.collectPorts(type, ...)`:
  ```java
  for (NodePorts member : members) {
      if (!member.inNetwork(type, id)) continue;
      Port[] active = member.collected(type);
      if (active == null) {            // a primeira rede a passar pelo nó neste tipo relê, uma vez só
          member.rememberOrders(type);  // guarda a ordem de todas as portas do tipo, de todas as redes
          member.clearRoutes(type);
          active = readPorts(member, type);
          member.setCollected(type, active);
      }
      for (Port port : active) {
          if (!id.equals(port.faceNetwork)) continue;   // porta de outra rede
          boolean fresh = port.pendingFresh;
          port.pendingFresh = false;
          if (!fresh) port.previousOrder = port.order;  // a relida já tem a ordem guardada acima
          port.clearOrder();
          port.network = this;
          port.fresh = fresh;
          // origens e destinos como hoje
      }
  }
  ```
- [ ] Ramo `!exists` de `NetworkRoutes.rebuild` (hoje `if (id.equals(member.network(type))) member.clearRoutes(type)`): passa a `member.clearRoutes(type, this)`, para não limpar as portas do mesmo tipo que estão noutra rede.
- [ ] `NetworkManager.addNode`: para cada tipo, `setMembership(ports, type, node.networks(type))`. `removeNode`: o mesmo com `NO_NETWORKS`.
- [ ] `NetworkManager.nodeChanged(node, types)`: `ports.invalidate(types)` e, para cada tipo da máscara, `setMembership(ports, type, node.networks(type))`. O `setMembership` substitui o `move` e o `markDirty` de hoje:
  - antes = `ports.networks(type)`; agora = o novo;
  - **suja o tipo em todas as redes da união** (antes ∪ agora), criando o `NetworkRoutes` das novas como hoje (com `networksAdded = true`). Isto garante que toda rede com uma porta desse tipo neste nó remonta na mesma passada do `rebuild()`, antes do laço de transferência; então a releitura, que limpa as portas do tipo inteiro, nunca deixa uma rede limpa e sem remontar;
  - para cada rede que saiu (antes − agora): `ports.clearRoutes(type, routes)` e, se depois de gravar o novo `!ports.inNetwork(id)`, `routes.members.remove(ports)`;
  - para cada rede nova (agora − antes): `routes.members.add(ports)`, se ainda não era membro;
  - grava `ports.setNetworks(type, agora)`.
  - O `nodeChanged(node, ALL_TYPES & ~bit)` que o `readRemotePorts` chama no meio de uma remontagem (frente relida) continua: as redes já remontadas nessa passada voltam a ficar sujas e remontam no tick seguinte. Anote no javadoc que, nesse caso raro (a frente de uma remota mudou sem aviso), as portas delas podem ficar um tick sem rota.
- [ ] `NetworkManager`, hooks de teste ao lado dos de hoje (`sourceSleepInterval`, `sleepForTests`): `boolean isMember(UUID network, RouterBlockEntity node)` e `@Nullable Object sourceOrderForTests(RouterBlockEntity node, ResourceType type, Direction face)` (a identidade da `RoundRobinOrder` da porta da presa, só para comparar com `==`).
- [ ] `NodeProbe.fullDestinations`: `ports.network(type) == null` vira `ports.networks(type).length == 0`.
- [ ] Javadocs do `NetworkManager` (o parágrafo "Cada tipo de recurso (aba) de um nó entra numa rede própria"), do `NetworkRoutes` ("só entram os membros cujo tipo está nesta rede") e do `NodePorts`: agora é por porta.
- [ ] Depois desta tarefa, nenhum código do motor chama `networkId(type)`.
- [ ] `RangeGameTests`: as remotas deixam de pegar a rede da presa. Rode a run, veja quais falham e, nos testes que vinculam máquinas depois de pôr o roteador numa rede (`routerWithMachines` em ~:219 e os outros `setNetworkId` em ~:473, :780, :857, :1940, mais os que usam o helper `link(...)`), chame `router.setNetworkId(rede)` **depois** dos vínculos (o atalho cobre perfis e exceções). Não mude o que os testes provam. (A Tarefa 7 faz o grupo nascer na rede ativa de quem vincula.)
- [ ] GameTests novos em `FaceNetworkGameTests`:
  - `twoNetworksThroughOneMachine` (o exemplo da spec): baú M com um roteador R; face Cima de itens em Inserir na rede Mina e face Baixo em Extrair na rede Armazém. Um roteador S num baú A (Extrair, Mina) e um T num baú B (Inserir, Armazém). O item sai de A e chega em B passando por M;
  - `changingFaceNetworkDirtiesOldAndNew`: com R em Mina (Cima) e Armazém (Baixo), mudar Cima para uma terceira rede deixa `isDirty` nas três antes do tick (a antiga, a nova e Armazém, que tem outra porta de itens do nó); depois do tick, `isMember(Mina, R)` é falso e os itens de A param de entrar em M;
  - `cursorsSurviveWithTwoNetworksOnOneNode` (o risco acima): R com Baixo em Extrair na rede X (dois destinos, para o rodízio ter cursor) e Cima em Extrair na rede Y. Guarde `sourceOrderForTests(R, ITEM, DOWN)`; mude a prioridade da face Cima; depois do tick a ordem de Baixo é **a mesma instância** e não é nula. Repita mudando Baixo e conferindo Cima, para cobrir as duas ordens de montagem (o `rebuild()` percorre a lista de redes de trás para frente). Confira que o teste **falha** com o `collectPorts` antigo (rode uma vez antes da correção, ou descreva no relatório por que falharia);
  - `otherNodeSleepSurvives`: na rede Y, uma origem de outro roteador, dormindo (`sleepForTests`), com destinos que não são de R: mudar a face Baixo de R (rede X) não a acorda (`sourceSleepInterval` igual) e não a deixa sem rota;
  - `removedNetworkStopsOnlyThatFace`: apagar a rede Armazém (`NetworkSavedData.remove`) para a face Baixo; a face continua guardando o UUID (`faceNetwork`) e a Cima continua movendo;
  - `noNetworkFacesLeaveNoMembership`: tirar a rede de todas as faces de itens de R tira R da rede (`isMember` falso) e as portas não ficam com rota velha (o baú de R não perde itens).
- [ ] Verificação completa. Se couber, uma rodada curta do `./scripts/bench.sh` para conferir que a remontagem com 500 nós (todos com uma rede só) não mudou de ordem de grandeza; anote no relatório. Commit "Rede por face: o motor monta as rotas pela rede de cada porta".

### Tarefa 3: índice de nós e Tablet (protocolo 14)

**Arquivos:** `network/NodeIndex.java`, `menu/TabletMenu.java`, `menu/TabletSnapshot.java`, `packet/TabletMoveNodesPayload.java`, `packet/TabletPayloads.java`, `packet/ModPayloads.java` (versão), `client/TabletScreen.java`, `lang/*.json`, `gametest/TabletGameTests.java`.

Antes de começar, resolva o **ponto 2** (filtro de rede da Lista).

- [ ] `ModPayloads.VERSION = "14"` (a única vez; ver as Restrições globais).
- [ ] `NodeIndex.Entry`:
  - `UUID[] networks` (uma por tipo) vira `UUID[][] networks` (por tipo, as redes distintas das faces: `RouterBlockEntity.networks(type)`, que já inclui perfis e exceções);
  - `network(type)` sai; entram `List<UUID> networks(type)` (visão só de leitura), `inNetwork(UUID)` (algum tipo) e `hasNetwork()`; mais `int distinctNetworks()` (redes distintas somando os tipos) para a linha do Tablet;
  - `read(from)`: compara por tipo com `Arrays.equals` e só marca mudança se mudou (o índice não pode sujar o arquivo à toa);
  - `save()`: grava `face_networks` (tipo → `ListTag` de `NbtUtils.createUUID`), só os tipos com rede; **não grava mais `networks`**;
  - `load()`: com `face_networks`, lê dela; sem ela, lê a `networks` antiga (uma rede por tipo, como lista de um).
- [ ] `TabletMenu` (os pontos que hoje leem `entry.network(type)`, ~:411, :471, :502, :518–524, :545, :607):
  - `visible`: o jogador é dono de alguma rede de alguma face;
  - `countMembership`: o nó conta uma vez em cada rede distinta de todas as faces (troque o laço de "repetido" por um conjunto pequeno ou pelo `distinctNetworks`);
  - `sampleMoved`: hoje soma o movido do tipo na rede da aba. Com várias redes por tipo, o roteador só conhece o total por tipo (`RouterBlockEntity.moved(type)`). Proposta: somar o total do tipo a **cada** rede desse tipo do nó (a vazão por rede passa a contar o nó inteiro em cada uma) e documentar isso no javadoc. **Pergunta** para o implementador: se o dono quiser a vazão exata por rede, seria preciso contar o movido por porta no motor; fica fora desta tarefa, anote;
  - `status`: "sem rede" se nenhuma face tem rede existente; "pausado" se todas as existentes estão pausadas;
  - `inNetworkName`: procura em todas as redes de todos os tipos;
  - `NodeView`: o campo `List<Optional<UUID>> networks` (uma por tipo) vira `List<List<UUID>> networks` (por tipo, só as que existem em `NetworkSavedData`) e ganha `boolean removedNetwork` (alguma face aponta para um UUID que não existe mais, decisão 6 e 11; calculado aqui, só para os nós da página, sem varrer nada);
  - `moveNodes(keys, type, network)` ganha `Optional<UUID> from`: para cada nó, `router.setNetworkAllFaces(tipos, from.orElse(null), alvo)`, com tipos = o tipo dado ou todos os carregados. Recusa `from` que o jogador não vê? Proposta: `from` só filtra (não dá permissão a nada), então qualquer UUID serve; só o alvo passa por `canUse`, como hoje.
- [ ] `TabletMoveNodesPayload`: campo novo `Optional<UUID> from` no fim do `StreamCodec.composite`. O handler (`TabletPayloads.handleMoveNodes`) repassa. O cliente manda conforme a resposta do ponto 2 (proposta: vazio).
- [ ] `TabletSnapshot`: o codec do `NodeView` com as listas por tipo e o `removedNetwork`; `NodeView.network(type)` vira `networks(type)`.
- [ ] `TabletScreen`:
  - dica do nó (~:1308–1310): por tipo, "Itens: Mina, Armazém" (ou "sem rede"); os nomes passam por `GuiText` onde forem desenhados;
  - linha do nó: com mais de uma rede distinta, a etiqueta "faces em N redes" (lang `gui.wirelessautomate.tablet.node.face_networks`, com plural no padrão das outras etiquetas). **Pergunta** (spec decisão 11): "mais de uma" é no nó todo (proposta) ou num mesmo tipo?
  - com `removedNetwork`, o aviso "faces sem rede: rede removida" na linha (cor de aviso, `GuiPaint.WARN`) e na dica;
  - "Mover para a rede…" manda o `from`.
- [ ] Textos novos nos dois idiomas (pt_br: "faces em %s redes", "faces sem rede: rede removida"; en_us: "faces in %s networks", "faces without network: network removed").
- [ ] GameTests em `TabletGameTests` (redes próprias):
  - `nodeListedInBothFaceNetworks`: um roteador com Cima na rede A e Baixo na B: a busca pelo nome de A e pelo nome de B acha o nó; o `NodeView` tem A e B nos itens; as duas `NetworkView` têm o nó contado (`nodes >= 1`, pertinência);
  - `nodeIndexFaceNetworksRoundTrip`: `NodeIndex.save`/`load` com `face_networks`; uma entrada com só a `networks` antiga lê como uma rede por tipo;
  - `moveOnlyFacesOfFilteredNetwork`: com `from = A`, mover itens para C muda só a face que estava em A; sem `from`, todas as faces de itens vão para C; as outras abas não mudam;
  - `removedNetworkFlag`: apagar a rede de uma face liga o `removedNetwork` do nó e tira a rede da lista do `NodeView`.
  - Ajuste os testes existentes que leem `NodeView.network(type)` e o `moveNodes` antigo (~:166, :345–381).
- [ ] Verificação completa e e2e (a tela do Tablet e um payload mudaram; confira as capturas do Tablet). Commit "Rede por face: Tablet e índice de nós com as redes das faces (protocolo 14)".

### Tarefa 4: Configurador e comandos

**Arquivos:** `network/RouterPreset.java`, `preset/PresetApplier.java`, `item/ConfiguratorItem.java`, `preset/ConfiguratorArea.java`, `block/RouterBlockEntity.java` (`setFace`), `command/WaCommand.java`, `lang/*.json`, `gametest/ConfiguratorGameTests.java`, `gametest/TypeNetworkGameTests.java`.

- [ ] `RouterPreset`:
  - sai o campo `UUID[] networks`; a rede vai dentro de cada `FaceConfig` do preset (`copyOf` volta a copiar com `copyFrom`, com a rede);
  - codec: `faces` como hoje (a rede vem no codec do `FaceConfig`); os campos `networks` (mapa tipo → rede) e `network` (único) continuam **só lidos**: em `fromMaps`, cada tipo com rede antiga põe essa rede nas seis faces do tipo que ainda não têm rede, criando o `FaceConfig` da face que estava nula (decisão 12, "essa rede nas seis faces do tipo"). Na escrita, `networks` sai (o `forGetter` devolve mapa vazio, e o `optionalFieldOf` não escreve o padrão; confira);
  - API: sai `network(type)`; entram `@Nullable UUID network(ResourceType, RelativeSide)`, `List<UUID> networks(ResourceType)` (distintas), `hasNetwork()`, `distinctNetworks()` (todas as faces), `withoutNetwork(UUID id)` (tira essa rede de todas as faces), `onlyNetworkOf(@Nullable ResourceType)` (tira a rede das faces dos outros tipos) e `withoutNetworks()`;
  - `apply(router, only)`: para cada face, `router.setFace(type, side, config)` (que copia só as configurações, Tarefa 1) e, **se a face do preset tem rede**, `router.setFaceNetwork(type, side, rede)` (por `RelativeSide`: crie a sobrecarga `setFaceNetwork(ResourceType, RelativeSide, UUID)` no roteador, para não converter de volta para absoluto). Face do preset sem rede **não mexe** na rede da face de destino: é a regra de hoje ("um tipo sem rede no preset fica com a rede que o roteador já tem"), agora por face, como manda a decisão 12. Tudo no `batchChanges` de sempre;
  - `equals`/`hashCode`: só as faces (a rede está nelas).
- [ ] `PresetApplier.check(player, preset, only)`: percorre as **redes distintas** do preset (depois do `onlyNetworkOf(only)`); a que não existe ou que o jogador não pode usar sai do preset (`withoutNetwork(id)`), e as faces que a teriam ficam com a rede que já tinham. `Checked` continua com `applied` e `kept` (redes) e `denied`; `keptTypes` passa a ser "tipos com alguma face que perdeu a rede", para o aviso de hoje (`ConfiguratorItem.pasteMessage`) continuar igual.
- [ ] `ConfiguratorItem.copy` (~:261–275): o laço que tira as redes removidas por tipo vira `for (UUID id : preset.distinctNetworks()) if (data.network(id) == null) preset = preset.withoutNetwork(id);`. A contagem do tooltip (~:364, `distinctNetworks().size()`) não muda de forma.
- [ ] `ConfiguratorArea.paste` (~:116–131): sem mudança de forma (usa `check` e `applyTo`); confira.
- [ ] `/wa face` (`WaCommand`, ponto 3): um ramo novo `/wa face <pos> <tipo> <face> network <nome|none>`, irmão do argumento `mode` (o literal `network` tem precedência sobre o argumento de palavra no Brigadier; confira que `/wa face ... extract` continua funcionando). A face é absoluta, como no comando de hoje. A rede pelo nome, com `NETWORK_NAMES` de sugestão e a mesma busca do `use` (`data.find(...)`); `none` tira. Precisa de permissão 2, como o resto do `/wa face`. Textos `command.wirelessautomate.face.set_network` e `face.clear_network` nos dois idiomas. Se o implementador achar que não é simples, pule e anote (a decisão 13 permite).
- [ ] GameTests:
  - em `ConfiguratorGameTests`: `copyAndPastePerFaceNetworks` (origem com Cima em A e Baixo em B; colar põe as duas no destino, nos mesmos lados relativos); `pasteSkipsForbiddenFaceNetwork` (Baixo numa rede de outro dono, privada: no destino, Baixo fica com a rede que tinha e Cima recebe A; a action bar avisa); `presetFaceWithoutNetworkKeepsTarget` (face sem rede no preset não tira a rede do destino); `pasteOnlyTypeKeepsOtherTypes` (colar só Fluidos não toca nas redes das faces de itens);
  - em `TypeNetworkGameTests` (preset antigo, ~:381): um preset com `networks` {itens: L} lido pelo codec põe L nas seis faces de itens; o com `network` único, em todos os tipos;
  - ajuste os existentes que usam `preset.network(type)` e `withoutNetwork(type)` (ConfiguratorGameTests ~:168–218, :547–563; TypeNetworkGameTests ~:381–383);
  - `/wa face ... network`: pela `CommandDispatcher` do servidor do teste, a face recebe a rede; `none` tira.
- [ ] Verificação completa (sem e2e: o Configurador não tem tela). Commit "Rede por face: Configurador copia e cola a rede de cada face".

### Tarefa 5: Vinculador com a linha Faces

**Arquivos:** `linker/LinkerFaces.java` (novo, puro) com `src/test/java/.../linker/LinkerFacesTest.java`, `registry/ModDataComponents.java`, `item/LinkerItem.java`, `linker/LinkerActions.java`, `linker/LinkerProblem.java`, `menu/LinkerSnapshot.java`, `menu/LinkerMenu.java`, `packet/LinkerActionPayload.java`, `client/LinkerScreen.java`, `lang/*.json`, `gametest/LinkerAreaGameTests.java`.

- [ ] `LinkerFaces` (record com `int mask`, sem classes do Minecraft; bit = `RemoteSide.ordinal()`, que é puro): `ALL = 63`, `isAll()`, `isEmpty()`, `toggle(RemoteSide)`, `contains(RemoteSide)`, `List<RemoteSide> marked()` na ordem do botão (F, T, E, D, C, B, que é a ordem do enum `FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM`), `static LinkerFaces of(int mask)` (descarta bits acima de 6) e `int absoluteMask(int front)` (cada lado marcado por `RemoteSide.toAbsolute(front)`, bit = índice de `Direction`). JUnit: todas e nenhuma, alternar, ordem do `marked()`, máscara inválida, e `absoluteMask` com frente Sul (Trás = Norte), Leste e −1 (= Norte).
- [ ] Componente `linker_faces` (`ModDataComponents.LINKER_FACES`, `Codec.INT` e `ByteBufCodecs.VAR_INT`, persistente e de rede, como o `linker_tabs`). `LinkerItem.faces(stack)` (ausente = `ALL`) e `setFaces(stack, faces)` (gravar `ALL` remove o componente). Textos: `LinkerItem.facesLetters(faces)` monta "T, B" pelas letras do lang (`side.letter.*`, as mesmas da tela do roteador; confira as chaves em `RouterScreen.sideLetter`).
- [ ] `LinkerProblem.NO_FACES` (com o texto dos dois idiomas): `LinkerActions.check` o devolve, depois do `NO_TABS`, quando o modo não é Máquinas e as faces estão vazias.
- [ ] `LinkerActions` (decisão 8):
  - `inTarget(router, types, faces, target)` = `router.attachedFacesIn(types, faces.absoluteMask(router.attachedFront()), target)`;
  - `apply(router, types, faces, target)` = `router.setAttachedFacesNetwork(types, faces.absoluteMask(router.attachedFront()), target)`;
  - `single` e `link` (área) leem `LinkerItem.faces(stack)`; vazio = action bar `no_faces` e nada muda. Cada roteador da área usa a frente da **própria** máquina;
  - mensagens: com todas as faces, as de hoje (`linked`, `area.linked`, etc.), com o texto da spec ("3 roteadores vinculados a Armazém (Itens, todas as faces)"; confira se a de hoje já diz isso e só acrescente "todas as faces" se não disser); com faces marcadas, chaves novas `linked_faces`, `area.linked_faces` ("3 roteadores: faces T, B em Armazém (Itens)") e `area.unlinked_faces` / `unlinked_faces` ("3 roteadores: faces T, B sem rede (Itens)");
  - o `RangeLinks` (modo Máquinas) ignora as faces.
- [ ] `LinkerActionPayload.Op.SET_FACES` (no fim do enum, para não mudar os ordinais de hoje): `value` = máscara; o servidor grava `LinkerFaces.of(value)` (aceita vazio: a tela mostra o aviso e o botão desliga).
- [ ] `LinkerSnapshot`: campo `int faces` no record e no codec; o "já estavam" (`already`) passa pelo `inTarget` com as faces; a cor do ponto do mapa (~:152, hoje `router.networkId(colorType)`) passa a ser a rede da **primeira face marcada** (ordem F, T, E, D, C, B) do primeiro tipo marcado, na presa daquele roteador.
- [ ] `LinkerScreen` (mínimo 300 × 204):
  - o mapa fica 14 px mais baixo (`MAP_H = 104` vira 90, ou o equivalente no `mapH()`) e, abaixo dele, a linha **Faces**: `[Todas] [F] [T] [E] [D] [C] [B]`, botões no estilo das caixas de abas (marcado = fundo escuro), letras pelo lang; "Todas" marca as seis (desmarca se já estão todas? **Pergunta**: o mockup não mostra; proposta: "Todas" só marca as seis, e as letras desmarcam uma a uma);
  - dica de cada letra: o nome inteiro ("Trás") e uma linha "vista de frente para a máquina";
  - a linha some no modo Máquinas (o mapa volta à altura de hoje);
  - botão Vincular: "Vincular 3" (todas), "Vincular 3 · T, B", "Desvincular T, B · 3", sempre por `GuiText` (abreviado, inteiro na dica);
  - sem atalho de roda para as faces (Shift + roda continua nas abas).
- [ ] GameTests em `LinkerAreaGameTests` (redes próprias):
  - `linkFacesRelativeToFront`: fornalha virada para o Sul com um roteador em cima; Vinculador com Trás e Baixo: só as faces absolutas Norte e Baixo (de itens) entram na rede; as outras ficam como estavam;
  - `linkFacesWithoutFront`: barril com `FACING = UP` (frente vertical): Frente = Norte, então marcar só Frente põe a face Norte;
  - `unlinkFaces`: em "Nenhuma (desvincular)", as faces marcadas ficam sem rede e as outras continuam;
  - `alreadyCountsMarkedFaces`: o "já estavam" conta o roteador em que todas as faces marcadas, nas abas marcadas, já estão na rede alvo, e não conta o que tem só uma delas;
  - `noFacesRefuses`: com as faces vazias, o clique e a área não mudam nada (`check` devolve `NO_FACES`);
  - `machinesModeIgnoresFaces`: no modo Máquinas, `check` não olha as faces;
  - os testes existentes (que usam o padrão = todas) continuam passando sem mudança; confira.
- [ ] Verificação completa e e2e (tela e payload do Vinculador mudaram; o e2e de hoje do Vinculador, ~:3290–3390, deve passar com o padrão "Todas"). Commit "Rede por face: Vinculador com a linha Faces".

### Tarefa 6: tela do roteador, snapshot e payloads

**Arquivos:** `menu/RouterSnapshot.java`, `menu/RouterMenu.java`, `packet/SetNetworkPayload.java`, `packet/ModPayloads.java`, `client/RouterScreen.java`, `client/MachineView3D.java`, `lang/*.json`, `gametest/RouterMenuGameTests.java`, `gametest/FaceNetworkGameTests.java`, `client/DevEndToEnd.java` (só o que quebrar; as capturas novas são da Tarefa 8).

Antes de começar, confirme o **ponto 4** (quadro do lado no payload).

- [ ] `RouterSnapshot`:
  - sai `typeNetworks` (e `network(type)`, `withTypeNetworks`); o `FaceView` ganha `Optional<UUID> network` (a rede da face no alvo editado, no quadro de hoje: absoluta na presa, canônica num grupo ou numa máquina). Uma rede que não existe mais vai vazia (decisão 6: a tela mostra "Sem rede");
  - campo novo `int attachedFront` (a frente da máquina presa pelo `RouterBlockEntity.attachedFront()`, −1 sem frente ou vertical), lido só no `capture`;
  - o seletor (`networks`) inclui, como hoje, as redes do jogador e as públicas e também as de outro dono que estejam em alguma face, de qualquer tipo, do alvo editado (hoje entra só a rede da aba; assim a pílula e o resumo mostram o nome);
  - `sameBody`, `equals`, `hashCode` e o `STREAM_CODEC`/`BODY_CODEC` com os campos novos; os construtores de conveniência (sem `RangeView`, sem `ChunkLoadState`) ganham o `attachedFront` ou o preenchem com −1 (confira quem os usa: `DevScreenshot` e prévias).
- [ ] `SetNetworkPayload(int containerId, ResourceType resource, Direction face, Optional<UUID> network)` (o `face` no quadro do alvo, como o `SetFacePayload`). `ModPayloads.handleSetNetwork`: `LoadedTypes.contains(resource)`, o alvo pelo `openMenu(...).actionTarget()` (nulo = ignora), rede vazia = tira; rede presente só se existe e `canUse` (a regra de hoje); grava por `router.setFaceNetwork(target, resource, face, id)`. O `RouterNetworksPayload` não muda.
- [ ] `RouterScreen` (mockup, seção 2; tamanho mínimo 300 × 240):
  - **sai o seletor de rede da linha das abas** (`networkButton`, `paintNetwork`, `networkTooltip`, `NET_PILL_PAD`, `NET_MIN`, o cálculo de largura em ~:700–707) e o ponto de rede de cada aba (`networkDot` na aba, ~:1540–1556); a aba volta a ter só o tipo;
  - no canto direito da linha das abas, o resumo discreto "N redes" (redes distintas das faces da aba no alvo; "Sem rede" se nenhuma), em `GuiPaint.MUTED` e por `GuiText`; a dica lista face → rede ("Cima: Mina · Baixo: Armazém");
  - **linha Rede** na coluna da direita, abaixo dos botões de modo: rótulo "Rede" e uma pílula com a borda e o ponto na cor da rede da face selecionada, cinza em "Sem rede"; clicar abre a lista (o `renderNetworkList`/`dropdown*` de hoje, reaproveitados, agora presos à pílula nova), com as redes do seletor e "Sem rede"; o título da lista vira "Rede da face Cima em Itens". Escolher manda o `SetNetworkPayload` com a face selecionada (aplica no snapshot local na hora, como o `chooseNetwork` faz hoje);
  - **altura:** `MODE_H` de 18 para 14 (o par de linhas termina em 104), a linha Rede em y = 106 com 12 de altura, `FILTER_Y` de 114 para 118 e `RouterMenu.CARD_Y` de 132 para 136 (os cartões descem 4 px; o inventário fica em 157, sem mudança). Confira na captura que nada encosta (o mockup põe Editar em 121 e os cartões em 137: ajuste fino é livre, o limite é o tamanho mínimo);
  - **botões de face com lados relativos também na presa** (decisão 7): a ordem dos botões passa a ser a do quadro canônico (`FACE_ORDER` de hoje lido como canônico: Cima, Frente, Esquerda / Baixo, Trás, Direita). Na presa, o botão do lado canônico `c` age na face absoluta `RemoteSide.fromCanonical(c.ordinal()).toAbsolute(attachedFront)`; o resto da tela continua com a face absoluta (`face`, `SelectFacePayload`, `SetFacePayload`, filtro e cartões). Os rótulos usam o `sideLabel` dos remotos (inteiro, abreviado ou letra). Junte `paintFace` e `paintRemoteFace` num desenho só, mantendo o Olho do roteador preso na face absoluta `facing` e o apagado da face sem capability;
  - **ponto da rede** em cada botão de face (canto direito de cima, como o `.mark` do mockup), na cor da rede; sem ponto = sem rede;
  - **dica da face:** o lado relativo e, na presa, o absoluto junto ("Trás · Norte"); o modo e o acesso como hoje; "Rede: Armazém" ou "Sem rede";
  - abaixo dos botões de face, uma linha discreta: "Frente da máquina: Sul", ou "Sem frente: Frente = Norte" (`attachedFront == -1`); num grupo ou máquina remota, "Lados pela frente de cada máquina". Dica: "Os lados são relativos à frente da máquina...". Confira que a linha cabe entre os botões de face e o separador do "Mais" (`ADV_SEP_Y = 180`) no tamanho mínimo, e que acompanha o crescimento da tela;
  - no grupo e na exceção, a linha Rede edita a do perfil ou da exceção (é só o alvo do payload; o servidor já trata);
  - todo texto variável (nomes de rede, resumo, linha da frente) por `GuiText`; `GuiText.clipCount()` não pode subir no tamanho mínimo em pt_br.
- [ ] `MachineView3D.render`: novo parâmetro com a cor da rede da face selecionada (ou −1); com rede, o contorno da face selecionada (`renderOverlays`, ~:274–290) usa essa cor; sem rede, o de hoje.
- [ ] Textos novos e removidos nos dois idiomas: `gui.wirelessautomate.router.network.face` ("Rede"), o título da lista, `networks.count` ("%s redes"/"%s networks", com singular), `front.known` ("Frente da máquina: %s"), `front.none` ("Sem frente: Frente = Norte"), `front.remote` ("Lados pela frente de cada máquina"), a dica dos lados. Saem as chaves só do seletor da aba (`network.tab.*`) se nada mais usar.
- [ ] GameTests:
  - em `RouterMenuGameTests` (ajuste os de ~:85–190, que hoje usam a rede da aba): `setFaceNetworkThroughPayload` (rede própria numa face: só aquela face muda); `setFaceNetworkRefusesForeign` (privada de outro dono é recusada; vazio tira); `snapshotCarriesFaceNetworksAndFront` (fornalha virada para o Sul: `attachedFront` = Sul; `FaceView.network` na face certa; rede apagada vem vazia);
  - em `FaceNetworkGameTests`: `groupProfileNetworkThroughPayload` (alvo grupo: a rede vai para o perfil) e `machineNetworkCreatesException` (alvo máquina: cria a exceção só com a rede diferente).
- [ ] O e2e de hoje usa o seletor da aba (~:655–692 e ~:955, "5-roteador-b-rede-itens", "5b-roteador-b-rede-energia") e vai quebrar: reescreva esses passos para escolher a rede pela linha Rede da face (mesmas capturas, mesmos nomes). As capturas novas ficam para a Tarefa 8.
- [ ] Critério de pronto: `grep -rn 'networkId(' src/main/java --include=*.java | grep -v -e gametest -e DevEndToEnd -e bench` só acha a declaração em `RouterBlockEntity`.
- [ ] Verificação completa e e2e; olhe as capturas do roteador no tamanho mínimo e em pt_br. Commit "Rede por face: linha Rede na tela do roteador e lados relativos na presa".

### Tarefa 7: roteador de alcance

**Arquivos:** `range/RemoteMachines.java`, `block/RouterBlockEntity.java` (`linkMachine`), `range/RangeLinks.java`, `client/DevEndToEnd.java` (as chamadas de `linkMachine`), `gametest/RangeGameTests.java`.

- [ ] `RemoteMachines.link(BlockPos, ResourceLocation block, int front, @Nullable UUID network)`: um grupo **novo** nasce com as seis faces de todos os tipos em `network` (`MachineConfig` novo com `setNetwork` em cada face); um grupo que já existe não muda (decisão 5).
- [ ] `RouterBlockEntity.linkMachine(BlockPos pos, @Nullable UUID network)`; o `linkMachine(BlockPos)` de hoje vira atalho com `null` (testes e e2e que não têm jogador).
- [ ] `RangeLinks.toggle` (~:172) e `linkArea` (~:263): a rede ativa de quem vincula, **sem criar** (`LinkerActions.activeNetwork(player)`, e só se `canUse`; senão `null`), passada ao `linkMachine`. Na área, a mesma rede para todas.
- [ ] Revise os ajustes que a Tarefa 2 fez em `RangeGameTests` (o `setNetworkId` depois dos vínculos): os que passam pelo Vinculador com um jogador de teste podem agora preparar a rede ativa dele e dispensar o ajuste; os que chamam `linkMachine(pos)` direto continuam com ele.
- [ ] GameTests em `RangeGameTests`:
  - `newGroupBornInActiveNetwork`: o Vinculador no modo Máquinas, com a rede ativa A, vincula uma fornalha: o perfil do grupo novo tem as seis faces de todos os tipos em A;
  - `existingGroupKeepsNetworks`: uma segunda fornalha do mesmo bloco, vinculada com outra rede ativa, entra no grupo que já existe e o perfil não muda;
  - `noActiveNetworkGroupWithoutNetwork`: sem rede ativa, o grupo nasce sem rede (e o vínculo não cria rede);
  - `profileInTwoNetworks`: perfil com Trás em Extrair na rede X e Cima em Inserir na rede Y: a fornalha remota dá para um destino de X e recebe de uma origem de Y;
  - `exceptionOverridesProfileNetwork`: a exceção de uma máquina com outra rede numa face move por essa rede, e as outras máquinas do grupo pela do perfil.
- [ ] Verificação completa (sem e2e, a não ser que o `DevEndToEnd` tenha mudado além das chamadas). Commit "Rede por face: grupos do roteador de alcance nascem na rede ativa".

### Tarefa 8: e2e, guia, docs e verificação completa

**Arquivos:** `client/DevEndToEnd.java`, `scripts/guide/gerar_guia.py` (e as páginas geradas), `lang/*.json`, `CLAUDE.md`, `docs/progresso.md`, `docs/especificacao.md`.

- [ ] e2e (`DevEndToEnd`), com capturas novas e olhando cada uma:
  - `rede-1-linha-rede`: a tela do roteador no tamanho mínimo, uma fornalha virada para o Sul, face Cima em Mina e Baixo em Armazém: a linha Rede, os pontos nos botões, "Frente da máquina: Sul" e o resumo "2 redes";
  - `rede-2-lista`: a lista da linha Rede aberta;
  - `rede-3-sem-frente`: um barril virado para cima ("Sem frente: Frente = Norte");
  - `rede-4-vinculador-faces`: o Vinculador com Trás e Baixo marcados ("Vincular N · T, B");
  - `rede-5-tablet`: a linha do nó com "faces em 2 redes" e a dica;
  - a volta em pt_br com uma rede de nome longo na pílula e no resumo, e `GuiText.clipCount() == 0` nas telas novas (o e2e já tem o padrão; siga-o);
  - confira no servidor (`onServer`) que a face mudou pela tela, não só no cliente.
- [ ] Guia (`scripts/guide/gerar_guia.py`, os dois idiomas, só "como operar"; gere as páginas pelo script, nunca à mão):
  - página do roteador (~:497 e ~:579): o passo "5. Rede" passa a ser "na linha Rede de cada face"; os lados relativos à máquina e a frente;
  - página `networks.md` (~:727–800): "cada **face** de um roteador entra numa rede"; o exemplo da fornalha (Cima na Mina, Baixo no Armazém); a tabela "Como mudar a rede" (linha Rede da face, Vinculador com as faces marcadas, Configurador, Tablet); a linha "Sem rede ao colocar" conforme o ponto 1;
  - página do Vinculador (~:1056–1141): a linha Faces e os textos de vincular e desvincular por faces;
  - página do Configurador (~:1196–1228): "a rede de cada face";
  - a página do roteador de alcance, se já existir: o grupo novo nasce na rede ativa.
- [ ] `lang/en_us.json` e `pt_br.json`: confira que toda chave nova está nos dois e que as removidas saíram dos dois.
- [ ] `docs/especificacao.md`: a seção "Redes, distribuição e redstone" (~:140–149: "Rede por face", entrar e sair de uma rede pela face, em massa), a tela do roteador (~:60–62: sai a rede da aba, entra a linha Rede e os lados relativos), o Vinculador (~:95–100: a linha Faces), o Configurador e os presets (~:117, ~:172–177: a rede de cada face) e o Tablet (~:84).
- [ ] `CLAUDE.md`: a linha do `RouterBlockEntity` (hoje "rede por tipo (`networkId(type)`)" vira a rede por face, os atalhos e a migração), a do `FaceConfig`/`network/FaceConfig.java`, o `NodePorts`/motor (rede por porta), o `NodeIndex` (`face_networks`), o `RouterPreset`, o `linker/` (`LinkerFaces`, componente `linker_faces`), o protocolo `14` no `packet/` e a contagem e a lista de GameTests (`FaceNetworkGameTests`).
- [ ] `docs/progresso.md`: na tabela, a linha "Redes..." (rede por face, sem rede da aba; o roteador colocado conforme o ponto 1) e a do roteador de alcance; o próximo passo (o dono testa no jogo, junto com a etapa 1 do alcance); uma linha no histórico com os números de JUnit e GameTests das quatro runs.
- [ ] Verificação final completa: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium` e `./scripts/e2e.sh` (ou `WA_E2E="$PWD/run/e2e" ./gradlew runClient`) com `run/e2e/result.txt` = `OK`.
- [ ] Commit "Rede por face: guia, docs e capturas".

## Riscos principais

1. **Cursores e rotas com duas redes do mesmo tipo no mesmo nó** (Tarefa 2): o cache `collected` por tipo é dividido entre redes; sem o frescor por porta e a sujeira pela união, cursores se perdem e uma rede pode ficar sem rota. É o teste `cursorsSurviveWithTwoNetworksOnOneNode` que prova a correção.
2. **Colar não pode passar por cima da regra das redes** (Tarefas 1 e 4): o `FaceConfig.copyFrom` passa a copiar a rede; o preset tem de colar pelo `copySettingsFrom` e aplicar a rede à parte, senão uma rede proibida (ou "sem rede") vazaria para o destino.
3. **Pertencimento por superconjunto** (Tarefa 2): um nó vira membro de toda rede que aparece em alguma face, até em modo Nenhum. Não muda rotas, mas aparece no `/wa profile` como membro sem portas.
4. **Dois quadros de lado na tela** (Tarefa 6): os botões mostram lados relativos à máquina, mas a presa continua falando em face absoluta com o servidor. Toda conversão passa pelo `RemoteSide` e pela frente que vem no snapshot, num lugar só.
5. **Divergências com o código** (pontos 1 a 3): colocar o roteador, o filtro de rede da Lista e o `/wa network`. Não siga sem a resposta.
