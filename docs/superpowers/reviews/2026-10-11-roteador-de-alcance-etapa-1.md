# Revisão final do ramo `roteador-de-alcance` (etapa 1), `main..b040826`

Só leitura: diff completo (74 arquivos), spec (decisões 1 a 9), plano da etapa 1, `CLAUDE.md`, os relatórios e revisões das tarefas 2 a 9 e o `ledger.md`. Conferi também as fontes do NeoForge 21.1 no `scratchpad/mcsrc` onde o comportamento do jogo decide (thread do `ChunkEvent.Load`, custo do `isLoaded`, quem chama `blockEntityChanged`, quando a capability invalida).

## Veredito

**Pronto para o teste do dono: sim.** Nenhum achado Crítico. Dois Importantes (um de comportamento que o dono pode esbarrar no teste e um de documentação que promete mais do que o código faz), a corrigir antes do merge no `main`, e uma lista de Menores para a etapa 2. O custo para quem não usa o upgrade é zero na prática; o motor com várias máquinas está correto nos cenários pedidos; os pacotes validam tudo; mundo antigo carrega igual; Mekanism e Ars continuam opcionais; o mixin é seguro.

## 1. Performance

### Quem não usa o upgrade (custo zero na prática)

| Caminho | O que roda | Veredito |
| --- | --- | --- |
| Mixin `Level.blockEntityChanged` (`mixin/LevelMixin.java:141`, `range/RemoteIndex.java:62-67`) | `isClientSide`, `instance == null`, `levels.isEmpty()`. Sem nenhuma remota no servidor, duas leituras de campo e volta. Sem alocação: `@ModifyVariable` em vez de `@Inject` (sem `CallbackInfo`). | OK |
| Laço de transferência (`ItemTransfer`, `FluidTransfer`, `ChemicalTransfer`, `ScalarTransfer`) | `source.machine.items(face)` em vez de `source.node.items(face)`: na presa, `MachineCaches.capability` (`range/MachineCaches.java:163-188`) faz as mesmas checagens de antes (`instanceof ServerLevel`, `isRemoved`) mais `discarded` e `remote` (duas leituras de campo). Nenhuma alocação. | OK |
| Remontagem (`NetworkRoutes.readPorts`, `:565-569`) | `node.rangeActive()` começa por `rangeUpgrade.getItem() instanceof RangeUpgradeItem`: com o slot vazio não chega a ler a config. `retainRemotes` sai na hora com `remotes == null` (`NodePorts.java:706`). O mapa `remotes` do `NodePorts` e o `remoteCaches` do BE são `null` até a primeira remota. | OK |
| Snapshot com a tela fechada | `pollSnapshot` só roda no `broadcastChanges` do menu aberto (vanilla); `rangeDue` exige `range().present()`. Sem o upgrade, `RangeView.EMPTY` custa um `boolean` no pacote. | OK |
| `addNode`/`removeNode` (`NetworkManager.java:138,147`) | `RemoteIndex.update/remove`: um `remove` num `Reference2ObjectOpenHashMap` e `rangeActive()` (o `instanceof`). Só na carga e descarga do nó. | OK |
| `ChunkEvent.Load` (`WirelessAutomate.java:99`) | `levels.get(level)` → `null` → volta. Na thread principal: o evento é postado no `supplyAsync` cujo executor é o `mainThreadMailBox` (`ChunkStatusTasks.java:215-224`). | OK |

Os logs de bench no scratchpad (`t5-bench-base3*.log` × `t5-bench-new3*.log`, `t5-bench-nomixin.log`) foram feitos pelas tarefas 4 e 5 e dizem o mesmo; não repeti (sem Gradle nesta revisão).

### Quem usa o upgrade

- **Nada por tick nem por máquina fora do orçamento.** As remotas são portas como as da presa (`readRemotePorts`), visitadas pelo mesmo laço, com o mesmo backoff e o mesmo teto de sono.
- **`isLoaded` antes de toda consulta.** `MachineCaches.capability` (`:169`) para remotas; `RouterSnapshot.state` (`:1199`) e `remoteSlots`; `RouterBlockEntity.machineInvalidated` (`:645`); `RangeLinks.machineRefusal` (`:320`) e `chosen` (`:121`); `linkMachine` (`:225`). Conferi que `Level.isLoaded` no servidor é barato e não carrega: `ServerChunkCache.hasChunk` (`ServerChunkCache.java:259-263`) é um `getVisibleChunkIfPresent` + `chunkAbsent`, sem `getChunk`.
- **Nada carrega chunk.** O mixin lê o estado pelo `getChunkNow` (`RemoteIndex.java:83`); a carga de chunk lê pelo próprio `ChunkAccess`; a área do Vinculador varre só `getChunkNow` e conta os descarregados.
- **Índice do mixin só com as ativas** (`RemoteIndex.update`, `:126-161`): fora do raio ou do limite não acorda nada nem relê a frente.
- Custo por `setChanged` de uma remota vinculada (e só dela): `getChunkNow` + `getBlockState` + `frontOf` (dois `hasProperty`) + `wakeMachine` (uma varredura de até 30 portas). É o mesmo peso do aviso de vizinho da presa hoje (fornalha acesa chama `setChanged` a cada tick nos dois casos).

## 2. Correção do motor com várias máquinas

- **Identidade das portas nas remontagens.** (nó, posição, tipo, face absoluta) em `NodePorts.remotePort` (`:672-690`); sobrevive enquanto a `MachineCaches` for a mesma; desvincular e vincular de novo dá porta nova (`port.machine != machine`). `retainRemotes` descarta por tipo o que deixou de ser porta e some com o mapa quando esvazia. A porta da presa continua tendo `machinePos` regravado na leitura (`NetworkRoutes.java:470`), então `visitMachine`, `sameEndpoint` e `deliveringInto` continuam certos (o `MachineCaches.attached` nasce em `BlockPos.ZERO`, mas a porta nunca é usada antes dessa leitura).
- **Sono e acordar.** `deliveringInto` compara sempre a máquina (`NetworkManager.java:384-386`); o atalho `node == visitNode` só vale com `visitAttached` (`:371-377`). Conferi os quatro cruzamentos: remota de A entregando na presa de A (entrega, não acorda feeders), presa de B que é remota de A sendo extraída por A (acorda feeders: abriu espaço), entrega de A numa remota R que é presa de B (B não acorda feeders), extração da própria R (feeders acordam). Testes `remoteDeliveryIntoAttachedDoesNotWakeFeeders` e `deliveryIntoRemoteDoesNotWakeFeeders` cobrem os dois primeiros pelo caminho real. Chunk descarregado: o cache do NeoForge invalida na descarga → `machineInvalidated` → `capabilityChanged` → a visita seguinte vê `isLoaded` falso e dorme sem máquina (100 ticks), depois só paga o `isLoaded` a cada 100 ticks. Carga: `chunkLoaded` acorda e relê a frente.
- **Remota e presa de roteadores diferentes na mesma máquina; uma máquina em dois roteadores.** Cada roteador tem a própria `MachineCaches` (dois conjuntos de `BlockCapabilityCache` na máquina, como hoje com dois roteadores presos). `sameEndpoint` compara nível + posição + face, então a mesma face por dois roteadores na mesma rede não entrega para si mesma. `RemoteIndex.byPos` guarda a lista de vínculos e acorda os dois.
- **Girar o roteador (spin).** `spinTo` remapeia só as faces da presa; as remotas são relativas à frente da própria máquina e não mudam. `setBlockState` → `nodeChanged` → relê; a `MachineCaches` da remota é a mesma, a porta também.
- **Girar a máquina remota.** Pela frente relida no `setChanged` (mixin) e na carga do chunk (`refreshFront`, `RouterBlockEntity.java:616-624`), e na invalidação da capability. Ver o Importante 1: o NeoForge não invalida capability numa troca de estado (só `BlockEntity.setRemoved/clearRemoved`, `ChestBlockEntity` ao mudar `FACING`, caldeirão e composteira; `grep invalidateCapabilities` no `mcsrc`), então uma máquina parada girada por chave inglesa sem `setChanged` fica com os lados velhos até o próximo `setChanged` dela.
- **Tirar o upgrade.** `setRangeUpgrade(EMPTY)` → `pruneRemoteCaches` (descarta os caches e os listeners), `remotesChanged` (sai do índice), `changed(ALL)` → `readPorts` cai no `retainRemotes(type, EMPTY_SET)` para todos os tipos. A lista de máquinas fica guardada no NBT. Tela aberta: `validTarget` volta à presa e à face dela (`RouterMenu.java:567-586`), teste `screenUpgradeRemovedRestoresFace`.
- **Mudar o tier ou a config.** Tier: `setBlockState` → `remotesChanged()` e `nodeChanged`. Config: `checkNetworks` com `all` invalida os nós com `hasRemotes() || rangeActive()` e refaz o índice de todos (`NetworkManager.java:506-518`); teste `configRadiusRereadsPorts` no lote `wirelessautomate_config`.

## 3. Itens (cartões e upgrades)

- **Desvincular a última do grupo**: `RemoteMachines.unlink` tira o grupo e devolve `removeAllCards()`, que troca cada slot por `EMPTY` (`MachineGroup.java:105-117`): uma soltura só. `unlinkMachine` solta na posição do roteador. Quebrar o roteador: `removeAllCards()` inclui `remotes.removeAllCards()` (`RouterBlockEntity.java:156` do diff) e o `onRemove` solta o `range_upgrade` (`RouterBlock.java:198-201`); a loot table só dá o roteador, sem dupla.
- **Alvo que some com a tela aberta**: `targetLost` trava `mayPlace`/`mayPickup` dos slots de cartão até a tela escolher um alvo (`RouterMenu.java:592-598, 688-694`); teste `screenLostTargetLocksCards` com cartão no cursor e com Shift + clique. Sem o upgrade (sem lista para escolher), destrava e cai na presa com a face dela.
- **Shift + clique** com alvo de grupo vai para os cartões do grupo e não para a presa (`screenShiftClickCardGoesToGroup`).
- **Exceção**: guarda só `FaceConfig` (decisão 9); os cartões ficam no grupo; `revertToProfile` não mexe em item.
- **Alvo remoto pelo filtro**: `RouterFaceFilterTarget.current()` reconfere a cada chamada e `stillValid` fecha a tela se o alvo sumiu; não grava em alvo errado.

## 4. Segurança no servidor

- Os três payloads novos passam por `router(player, containerId)` (`ModPayloads.java:546-552`: menu aberto, mesmo `containerId`, `stillValid`). `SelectTarget` exige `rangeActive()` e confere grupo por id + bloco ou máquina por índice + posição (`EditTargetRef.resolve`); `Revert` e `Unlink` exigem `rangeActive()` e índice + posição (`sameMachine`, `:1421-1428`); o Menor do T7 ("sem `rangeActive`") foi corrigido. `SetFace`, `OpenFilter` e os slots passam por `actionTarget()`/`validTarget()`.
- **Claims**: clique único e escolha do roteador passam pelo `RightClickBlock` do próprio jogo antes do `onItemUseFirst`; a área posta o mesmo evento por candidato que de fato seria vinculado (`RangeLinks.clickAllowed`, `:303-307`), mais `mayInteract` na máquina e no roteador. Teste `linkerAreaRefusals` cancela o evento numa posição e confere que ela fica de fora e que o evento só sai para quem seria vinculado.
- **Dimensão, raio, limite**: `chosen` exige a dimensão do jogador e chunk carregado (`:115-126`); a área passa por `checkArea` (dimensão, volume, distância do jogador); `RangeRules.check` decide raio e limite; `linkArea` conta `used` uma vez e incrementa a cada vínculo dentro do `batchChanges` (não dá para passar do limite numa área).
- Nada vinda do cliente é usada sem conferir no roteador (posição, bloco do grupo, índice).

## 5. Compatibilidade

- **NBT**: `faces`, `cards`, `upgrade`, `upgrade_owner` intactos; `range_upgrade`, `machines`, `groups` só escritos se não vazios e lidos com `contains`/`getList` (ausente = vazio). `legacyNbtLoadsWithoutMachines` cobre. Componentes: só `linker_router` novo. Config: só `machineRadius` e `maxMachines` novos em `tiers.<tier>`.
- **Mekanism e Ars**: `MachineCaches` e `RangeLinks` passam pelas pontes (`Chemicals.capability()`, `Sources.capability()`, handlers como `Object`); `IChemicalHandler` continua só no `ChemicalTransfer`; `ISourceCap` só em `compat/arsnouveau/ArsSources`. `RangeGameTests` (`@GameTestHolder`) e `TestMachines` (`@EventBusSubscriber`) só com tipos vanilla e do mod.
- **Cliente**: `RangeList` e as mudanças do `RouterScreen`/`MachineView3D` ficam em `client/`; `RangeView`, `EditTargetRef` e os payloads são comuns e só usam registries. O grep de `wirelessautomate.client.` fora de `client/` acha só o plugin do JEI (pré-existente, só cliente).
- **Mixin**: alvo `Level.blockEntityChanged(BlockPos)` (`Level.java:984`), único chamador `BlockEntity.setChanged` (`BlockEntity.java:198`); `@ModifyVariable` em HEAD com `argsOnly` devolve a posição intacta, convive com outros injetores; `required: true`, `minVersion 0.8`, `compatibilityLevel JAVA_21`; sem refmap (Mojmap em produção no NeoForge); `[[mixins]]` no `neoforge.mods.toml` (template). Nenhuma classe do mod é carregada antes da hora: o corpo só faz um `invokestatic` resolvido na primeira execução, no servidor.
- **lang**: `en_us` e `pt_br` com o mesmo conjunto de chaves e os mesmos `%s` (conferido por script).

## 6. Problemas recorrentes do projeto

- **Teste que não prova o que diz**: `chunkLoadWakesRemote` teve o javadoc corrigido ("não é isolado no teste"). `snapshotMachineStates` agora prova a recaptura por tempo (a fornalha B não tem porta nem cache, então trocá-la por pedra não mexe na `changeVersion`). Sobra o Menor 6 (drops não contados).
- **Texto × comportamento**: ver Importante 2 (spec) e Menores 10 a 12 (guia e tooltips).
- **`int`/`long`**: distâncias e índices em `int` com teto 64/512; posições em `long`; `writeVarInt` em tudo. Nada perto do limite.
- **Config fora de `try/finally`**: `configRadiusRereadsPorts` restaura no `finally` do último passo; o `radius.set(2)` fica no passo anterior, separado por `container(...).setItem` (Menor 7).

## 7. Documentação

- `CLAUDE.md`: linhas `range/`, `mixin/`, `RouterMenu`, `packet/` (13), `linker/`, `client/` (`RangeList`), `gametest/` (229, 47) e as duas convenções novas batem com o código. Falta só "chunk load" na linha do `WirelessAutomate.java` (Menor 13).
- `docs/progresso.md`: tabela, próximo passo e histórico em dia; os números (213 JUnit, 229/7/15/2, 1.137 passos) são os do relatório da Tarefa 9.
- Guia (`gerar_guia.py` e os `linker.md`): três modos e o ciclo do Shift + clique nos dois idiomas. Sem página do Upgrade de Alcance (previsto para a etapa 3, registrado no `progresso.md`).

---

## Achados

### Críticos

Nenhum.

### Importantes (corrigir antes do merge)

**I1. Máquina remota girada sem `setChanged` fica com os lados velhos até o próximo aviso dela.**
`block/RouterBlockEntity.java:616-624` (`refreshFront`) e `network/NetworkRoutes.java:597-606` (`readRemotePorts` usa `remote.front()` gravado).
- Cenário: o dono vincula uma fornalha apagada com "Trás → Extrair", gira a fornalha com uma chave inglesa de outro mod (que faz `level.setBlock` com o `FACING` novo, mantém o block entity e não chama `setChanged`). O NeoForge não invalida a capability numa troca de estado (só na troca de block entity, na carga e descarga do chunk e nos poucos blocos que o fazem à mão, como o baú), então nem `machineInvalidated` nem o mixin rodam. "Trás" continua apontando para o lado antigo até a fornalha acender (aí o `setChanged` por tick corrige). A spec (seção 4) promete que a troca acontece "quando o estado do bloco muda".
- Correção sugerida (barata, só na remontagem): em `readRemotePorts`, se `level.isLoaded(pos)`, reler a frente pelo `getBlockState(pos)` antes de montar as portas (um `getBlockState` por máquina ativa por remontagem, nunca por tick). Não cobre o giro sozinho (o giro não dispara remontagem), mas garante que qualquer mudança do nó, config ou chunk corrige. Para cobrir o giro em si, a opção completa é observar `BlockEvent.NeighborNotifyEvent`/`onNeighborChange` na posição da máquina pelo mesmo `RemoteIndex` (fora da etapa 1). No mínimo, registrar a limitação na spec e no guia da etapa 3 ("gire a máquina antes de vincular, ou vincule de novo").

**I2. A spec afirma que a capability invalida na troca de estado e que o índice é um `LongOpenHashSet`.**
`docs/superpowers/specs/2026-10-11-roteador-de-alcance-design.md`, seção 4 ("de novo quando o estado do bloco muda (a capability invalida)") e decisão 8 ("`LongOpenHashSet` por nível").
- O código faz a releitura pelo `setChanged` e pela carga do chunk (`RemoteIndex` com `Long2ObjectOpenHashMap<List<Link>>`, porque uma máquina pode estar em vários roteadores). A seção 7 também diz "`Port` ganha o índice da máquina (−1 ou 0 para a presa)": virou `MachineCaches machine` + `machinePos`.
- Correção: alinhar as três frases ao que foi feito, no mesmo commit do I1, para a etapa 2 não partir de uma premissa falsa.

### Menores (podem ir para a etapa 2)

1. **Caches de remotas inativas ficam vivos quando a config baixa o raio ou o limite.** `block/RouterBlockEntity.java:546-567` (`pruneRemoteCaches`) só roda no desvincular, no NBT e no upgrade. Com `machineRadius` reduzido, as máquinas que viraram `OUT_OF_RANGE`/`OVER_LIMIT` perdem as portas (`retainRemotes`), mas a `MachineCaches` e os listeners continuam, e cada invalidação chama `machineInvalidated` → `refreshFront` → pode disparar `changed(ALL)` num nó que não usa a máquina. Inofensivo e raro (só recarga de config). Sugestão: no `checkNetworks` com `all`, chamar um `pruneInactiveCaches()` que descarta as fora de `machineReach() == ACTIVE`.
2. **`refreshFront` roda também na entrega nossa.** `block/RouterBlockEntity.java:593-598` (`remoteMachineChanged`). Inserir numa remota chama o `setChanged` dela → mixin → `getChunkNow` + `getBlockState` + `frontOf` a cada entrega. Nossa entrega nunca gira a máquina: dá para pular a releitura quando `NetworkManager` está numa visita (`visitNode != null`) e a posição é a do destino. Pequeno ganho por entrega em remotas.
3. **"7/32" da tela e da action bar divergem com máquinas além do limite.** `menu/RangeView.java:180-188` (`used()` conta só `LOADED/UNLOADED/MISSING`, isto é, as ativas) × `range/RangeLinks.java:396-405` (`used()` conta todas dentro do raio, inclusive `OVER_LIMIT`). Só acontece depois de baixar `maxMachines` na config. Sugestão: as duas pela `RangeRules.reach` (ativas), e o Vinculador recusar por `LIMIT` quando `ativas >= max`.
4. **`unlinkMachine` sem guarda de servidor** (ledger T3). `block/RouterBlockEntity.java:242-256`: `dropAtRouter` só testa `level == null`; chamado no cliente, soltaria entidades fantasmas. Os chamadores de hoje são todos de servidor. Guardar com `level.isClientSide` como o `linkMachine`.
5. **NBT corrompido descarta grupo repetido com os cartões dele** (ledger T3). `range/RemoteMachines.java:212-218`. Só com `/data` à mão. Opcional: somar os cartões ao primeiro grupo ou soltá-los na carga.
6. **Testes de drop não contam `== 1`** (ledger T2 e T3). `gametest/RangeGameTests.java:192-203` (`breakDropsRangeUpgrade`), `:419-446` (`unlinkLastMachineDropsGroupCards`), `:448-461` (`breakDropsGroupCards`) usam `assertItemEntityPresent`, que não pega soltura dupla. Contar as `ItemEntity` do item num raio (deve ser 1).
7. **`radius.set(2)` fora do `try`** em `configRadiusRereadsPorts` (`:800-816`). O `finally` está no passo seguinte; se o `container(...).setItem` logo depois do `set(2)` lançasse, a config ficaria em 2 (o arquivo do mundo de teste é apagado a cada rodada, então o dano é só na rodada). Mover o `set` para dentro de um `try` que vá até o `finally`, ou pôr o restaurar também num `thenExecuteAfter` final.
8. **`previewRangeSlotCenter()` sem chamador** (ledger T2). `client/RouterScreen.java:1788`. Tirar ou usar no e2e.
9. **`GroupView.icon()` aloca um `ItemStack` por linha de grupo por quadro.** `client/RangeList.java:368` e `menu/RangeView.java:145-147`. Guardar o ícone junto das linhas (`cachedRows`), já que `rows` é cacheado por visão.
10. **Guia: "Shift + clique em dois blocos (Área)"** também vale em Máquinas. `scripts/guide/gerar_guia.py:~1060` e `~1116` (as duas tabelas). Trocar para "(Área e Máquinas)".
11. **Tooltip `gui.wirelessautomate.linker.mode.machines.tooltip`** diz "Clique num roteador com o Upgrade de Alcance para escolhê-lo", mas escolher funciona sem o upgrade (a action bar avisa "inativo"). Inofensivo; ajustar o texto ou recusar a escolha sem o upgrade (a Tarefa 6 escolheu aceitar).
12. **Com o Vinculador no modo Máquinas, nenhum bloco abre com clique direito** (`item/LinkerItem.java:802-815`, `onItemUseFirst` consome o clique em qualquer bloco, inclusive pedra, com a recusa "Não é máquina"). É a decisão da Tarefa 6 (senão clicar numa fornalha a abriria), mas o dono vai sentir: vale dizer no guia e no tooltip do modo que "no modo Máquinas o Vinculador não abre blocos; troque de modo ou de item".
13. **`CLAUDE.md`, linha do `WirelessAutomate.java`**: falta "carga de chunk" entre os eventos escutados.
14. **Evento `RightClickBlock` sintético na área** (`range/RangeLinks.java:303-307`). Outros mods que reagem ao evento (não só cancelam) rodam uma vez por máquina vinculada pela área. Limitado pelo `maxAreaVolume` e por um clique; aceitável, mas vale uma linha no `CLAUDE.md` para quem mexer ali.
15. **`GlobalPos` não identifica o roteador** (ledger T6): quebrar e pôr outro na mesma posição faz o Vinculador apontar para o novo. Aceitável; o tooltip mostra as coordenadas.
16. **Sem `mayBuild`** no `choose`/`toggle` (ledger T6): modo aventura vincula. O resto do mod também não checa.
17. **Tela mínima** (revisão T8, todos ainda valem): "Frente"/"Bottom" encostando na borda do botão de 36 px; "Voltar ao perfil" + "Desvincular" sem clamp de largura (158 de 162 px em pt); rolagem de 12 px sem arrasto da barra; tooltip da lista com o seletor de rede aberto em tela estreita; `showList` estático. Etapa 2 refaz a tela.
18. **Spec `docs/especificacao.md`** (fonte da verdade do design) ainda não menciona o Upgrade de Alcance, o segundo slot nem o modo Máquinas; a spec própria cobre. Consolidar na etapa 3 junto com o guia.

### Triagem dos pendentes do `ledger.md`

| Pendente | Estado |
| --- | --- |
| T2: teste de drop não conta `== 1` | **Ainda vale** (Menor 6) |
| T2: `previewRangeSlotCenter` sem chamador | **Ainda vale** (Menor 8) |
| T2/T6: tooltip cita modo Máquinas | Resolvido (o modo existe) |
| T3: `unlinkMachine` sem guarda de servidor | **Ainda vale** (Menor 4) |
| T3: load descarta órfãos com cartões | **Ainda vale** (Menor 5) |
| T3: teste de drop não conta `== 1` | **Ainda vale** (Menor 6) |
| T3: ids de grupo reaproveitados → revalidar alvo | Resolvido (`EditTargetRef` confere o bloco do grupo) |
| T3: `setCard` sobrescreve sem devolver | Vale só como aviso de API; os chamadores (slots do menu) nunca sobrescrevem um slot cheio |
| T4 M3: caches das remotas sobram | Resolvido no desvincular, NBT e upgrade; **sobra a recarga da config** (Menor 1) |
| T4 M4/M5 (chunk descarregado sem listener, frente relida) | Resolvidos na Tarefa 5; a frente ainda depende de um `setChanged` (Importante 1) |
| T5: registrar no `CLAUDE.md` (mixin, lote `wirelessautomate_config`, vincular só pelo roteador) | Resolvido |
| T5: mod que não chame `blockEntityChanged` desliga o aviso | Observação; a porta cai no teto de 100 ticks. Sem ação |
| T6: Vincular de roteadores por área tem a mesma limitação de claims (pré-existente) | **Ainda vale**, fora deste ramo: `LinkerActions.link` só confere `mayInteract`. Reusar o `clickAllowed` do `RangeLinks` lá |
| T6: `GlobalPos` não identifica o roteador | **Ainda vale** (Menor 15) |
| T6: `mayBuild` | **Ainda vale** (Menor 16) |
| T7: handlers sem `rangeActive` | Resolvido (`sameMachine`) |
| T7: slots caem na presa silenciosamente | Resolvido (`targetLost`) |
| T7: `hasAnyCapability` criava caches | Resolvido (checagem barata e `remoteSlots` sem cache) |
| T7: `snapshotMachineStates` não prova a recaptura por tempo | Resolvido (a fornalha B não tem cache; a `changeVersion` não muda) |
| T7: Shift + clique e filtro com alvo remoto sem teste | Resolvido (`screenShiftClickCardGoesToGroup`, `remoteFilterTargetFollowsTarget`) |
| T8: face errada ao tirar o upgrade | Resolvido (`screenUpgradeRemovedRestoresFace`) |
| T8: `rows` por quadro | Resolvido (`cachedRows`); sobra o ícone (Menor 9) |
| T8 M2, M3, M5, M6, M7 | **Ainda valem** (Menor 17) |
| T9: guia "Único e Área" | Resolvido |

## Feedback do dono no teste (11/10/2026)

- Alternar entre a lista e o visor pelo botão Máquinas não é óbvio ("cadê o 3D?"): na etapa 2, mostrar a lista e o visor lado a lado quando a janela é larga, como no mockup; no tamanho mínimo, continua alternando, com o botão mais claro.
