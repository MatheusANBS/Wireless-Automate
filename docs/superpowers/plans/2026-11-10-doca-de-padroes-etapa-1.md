# Doca de Padrões: plano de implementação (etapa 1: a Doca com o AE2)

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar. Um implementador por vez; revisão de cada tarefa antes da seguinte.

**Objetivo:** o bloco `wirelessautomate:pattern_dock`, com os oito tiers, que recebe pedidos de um Pattern Provider do AE2 encostado num lado e os entrega, um pedido inteiro por máquina, às máquinas vinculadas àquele lado no cubo do tier, recolhe o resultado e devolve ao provedor. Inclui o Vinculador e o Configurador na Doca, a tela de seis linhas, o cubo do alcance no mundo, o modelo estático, a run de GameTest do AE2, o guia e o bench.

**Arquitetura:** a Doca é um block entity **sem ticker**. Cada lado (`DockSide`) guarda o bloco das máquinas, o perfil das faces (o `MachineConfig` de sempre, preenchido pelo Afinador na primeira máquina), as máquinas (posição e frente), o cursor e o pendente. A **entrega** roda dentro da chamada do AE2 (`ICraftingMachine.pushPattern`), passa por uma API sem tipos do AE2 (`DockDelivery`) e cobra o tempo no orçamento do motor. A **volta** é um visitante do `NetworkManager` (`EngineVisitor`), visitado no laço de transferência dentro do mesmo orçamento: tira das faces Extrai das máquinas acordadas e insere no `IItemHandler`/`IFluidHandler` do vizinho daquele lado. O `setChanged` de uma máquina acorda a Doca pelo mixin e pelo `RemoteIndex`, generalizado para aceitar outro dono além do roteador.

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), ModDevGradle, JUnit 5, GameTests, AE2 19.2.17 (API `compileOnly`, mod inteiro só no `runClient` e na run nova), Python 3 com Pillow (modelo e textura).

**Spec:** [`docs/superpowers/specs/2026-11-10-doca-de-padroes-design.md`](../specs/2026-11-10-doca-de-padroes-design.md). Proposta e conferência das APIs: [`2026-11-10-ae2-rs2-proposta.md`](../specs/2026-11-10-ae2-rs2-proposta.md). Mockup da tela (aprovado em 10/11/2026): `docs/preview/doca-de-padroes-tela.html`. Modelo: `docs/preview/doca-de-padroes.bbmodel`. A etapa 2 (RS2), a 3 (arte animada) e a 4 (pedido com item e fluido) têm planos próprios.

## Ajustes em relação à spec

O levantamento do código (10/11/2026) mudou quatro pontos de implementação. O que o jogador vê não muda. A tarefa 13 corrige a spec.

1. **A volta não é uma "rota interna do motor".** `Port`, `NodePorts`, `MachineCaches`, `NetworkRoutes` e as transferências usam o `RouterBlockEntity` em dezenas de lugares. Generalizar tudo isso é uma reforma do motor, arriscada para uma etapa de feature. A Doca vira um **visitante** do gerenciador: o `NetworkManager` ganha uma lista de visitantes, visitados no laço de transferência sob o mesmo `budget.hasTime`, com sono e despertar próprios (a máquina dorme vazia e acorda pelo mixin). A vazão é a do tier da Doca (`Config` por `ResourceType`), como a do roteador.
2. **O orçamento ganha uma dívida.** A entrega roda no tick do AE2, fora do `NetworkManager.tick`, e o `TickBudget` só mede a hora de parede entre `begin` e `end`. Ele ganha `charge(nanos)` (o tempo gasto fora vira dívida descontada no próximo `begin`) e `outsideRoom()` (há folga para gastar fora?). Sem folga, a Doca recusa e o provedor tenta no tick seguinte.
3. **O cubo do alcance no mundo é novo.** O mod não desenha cubo nem máquinas vinculadas ao segurar o Vinculador (o único realce, `client/highlight/`, sai das telas). A tarefa 10 cria o desenho da Doca; o roteador de alcance continua sem.
4. **"Sem provedor neste lado"** sem tipos fora da API do AE2: o `PatternContainer` do AE2 não está no jar da API. O lado tem provedor se o vizinho expõe a `AECapabilities.GENERIC_INTERNAL_INV` e não expõe a `ME_STORAGE` (o Pattern Provider tem o retorno; a Interface tem o estoque), **ou** se o lado recebeu uma oferta do AE2 nos últimos 60 s (cobre os provedores de addon).

E um limite conhecido: **"uma máquina pertence a um lado de uma Doca só"** é conferido contra as Docas carregadas (o `RemoteIndex`). Uma máquina vinculada a uma Doca num chunk descarregado pode ser vinculada a outra; quando as duas carregam, as duas entregam a ela. Registrado em `pendencias.md`, sem índice salvo nesta etapa.

## Restrições globais

- Código, comentários, mensagens de commit e docs em português; traduções em `en_us.json` e `pt_br.json`, sempre as duas.
- **Ids:** bloco, item e block entity `pattern_dock`; menu `pattern_dock`; nomes "Doca de Padrões" (pt_br) e "Pattern Dock" (en_us); título da tela `container.wirelessautomate.pattern_dock`. Pacote novo `dock/` (servidor) e `client/dock/` (cliente).
- **Tier:** a propriedade é o `RouterBlock.TIER` reaproveitado (como o `StorageBlock` e o `ResonanceCoreBlock`); o item guarda o tier no `DataComponents.BLOCK_STATE` (`StorageBlockItem.withTier`); a loot table copia `tier` (`minecraft:copy_state`). Os tiers do Allthemodium só com o mod (`RouterTier.loaded()`).
- **Lados:** índice = `Direction.ordinal()` (DOWN 0, UP 1, NORTH 2, SOUTH 3, WEST 4, EAST 5). Ordem na tela: Norte, Sul, Leste, Oeste, Cima, Baixo. Cores das etiquetas (ARGB): Norte `0xFFB57A3A`, Sul `0xFF2F6FD6`, Leste `0xFFD9931A`, Oeste `0xFF5E9A2B`, Cima `0xFF8E4FC9`, Baixo `0xFFC2486B`, numa constante só (`DockColors`).
- **Alcance:** a Doca de tier T usa `Config.machineRadius` e `Config.maxMachines` do tier de ordinal T + 1 (`RouterTier.values()[T.ordinal() + 1]`), **não** o `next()`, que pula os tiers sem o mod: a linha do Allthemodium existe na config mesmo sem o mod, e assim a Esmeralda dá 24/48 com ou sem ele, como a spec aprovou ("8, 16, 32, 48 e 256"). O Ultimate lê as chaves novas `patternDock.ultimateRadius` (96, de 0 a 128) e `patternDock.ultimateMaxMachines` (256, de 0 a `RangeTiers.MAX_MACHINES`). Distância de Chebyshev (`RangeRules.chebyshev`).
- **NBT do block entity** (chaves novas, nenhuma existente muda): `sides` (lista; cada um: `side` byte, `block` id, `profile` (`MachineConfig.save`), `machines` lista de `{pos long, front byte}`, `cursor` int, `pending` lista de `{item}` ou `{fluid}`, `pending_at` long, a posição da máquina que recebeu o pedido). Sem gravar o que é de tempo de execução (atividade, sono, motivo da última recusa).
- **Componente novo:** `wirelessautomate:linker_dock` (`DockChoice(GlobalPos pos, int side)`, persistente e sincronizado). Escolher uma Doca limpa `linker_router` e `linker_tv`; escolher um roteador ou uma Tela limpa `linker_dock`.
- **Config nova:** seção `patternDock` com `ultimateRadius` e `ultimateMaxMachines`. Chaves existentes não mudam; sem migração.
- **Protocolo:** payloads novos `DockStatePayload` (o estado da tela) e `DockReachPayload` (o cubo e as máquinas): `ModPayloads.VERSION` vai de `"49"` para `"50"` (com uma linha "50: Doca de Padrões" no Javadoc). Se a Lixeira entrar antes sem mudar a versão, continua 50.
- **AE2 é opcional:** tipos do AE2 só em `compat/ae2/` e `gametest/Ae2TestSupport`, nunca na assinatura de método de classe `@EventBusSubscriber` ou `@GameTestHolder`. A ponte comum é `dock/DockAe2.java` (`LOADED = ModList...isLoaded("ae2")`, métodos que só delegam). O `runGameTestServer` (sem o AE2) prova que o mod carrega sem ele.
- **A Doca não expõe `IItemHandler` nem `IFluidHandler`** em face nenhuma (spec, item 4 da conferência).
- **Classes de tela só em `client/`**, nunca referenciadas por código comum.
- **Máquinas remotas:** nenhuma consulta sem `level.isLoaded(pos)` antes; caches por `BlockCapabilityCache`; a Doca nunca carrega chunk.
- **Performance (skill `wa-performance`, ler antes de cada tarefa):** parado custa zero (sem ticker; visitante só com máquina acordada); nada de busca de capability por pedido; no máximo 8 candidatas por pedido; sem alocação no caminho da entrega além dos `ItemStack` do pedido; nada sincronizado com a tela fechada; `level.blockEntityChanged(pos)` no lugar de `setChanged()` quando só o NBT muda; por quadro, só desenhar. As tarefas 2, 7 e 8 fecham com o bench antes e depois.
- **Tamanho (skill `wa-modular`):** tela até 500 linhas, classe de servidor até 600 (o `RouterBlockEntity` está em 977: **nenhuma linha nova nele**). Comentários pela skill `wa-comentarios`.
- **Antes de cada commit:** `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams` e, a partir da tarefa 8, também `runGameTestServerAe2`. Mais `python -I .claude/skills/wa-performance/scripts/varrer_perf.py`, `python -I .claude/skills/wa-comentarios/scripts/varrer.py` e `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados. Nas tarefas 9, 10 e 11, também `./scripts/e2e.sh --only doca --capture doca` (o `run/e2e/result.txt` deve dizer `OK`) e olhar as capturas. Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, pare e conserte, nunca espere um resultado. Nunca dois jogos ao mesmo tempo.
- **GameTests:** `timeoutTicks` de no máximo 300; cada teste cria a própria rede quando usa rede; espera o `onLoad` com `startSequence().thenWaitUntil(...)`; jogador só pelo `TestPlayers.inLevel`; teste que muda a config vai num lote `wirelessautomate_config_dock` com `try/finally`.
- Commits sem linhas de atribuição, com o autor do dono.

## Mapa de arquivos

Base: `src/main/java/io/github/matheusanbs/wirelessautomate/`.

| Arquivo | Papel |
| --- | --- |
| `network/TickBudget.java` (mudar) | `charge(long)`, `outsideRoom()`; o `begin` desconta a dívida |
| `network/EngineVisitor.java` (novo), `network/NetworkManager.java` (mudar) | Visitantes do laço de transferência: `addVisitor`, `removeVisitor`, `wakeVisitor` |
| `range/RemoteListener.java` (novo), `range/RemoteIndex.java` (mudar) | O índice das remotas aceita um dono que não é roteador |
| `range/CacheOwner.java` (novo), `range/MachineCaches.java` (mudar) | Os caches de uma máquina com o dono abstrato (o roteador continua igual) |
| `range/SameBlockScan.java` (novo), `range/SameBlockLinks.java` (mudar) | A varredura do Ctrl + clique sem o roteador, para a Doca reusar |
| `dock/DockReach.java`, `dock/DockPick.java`, `dock/DockStatus.java`, `dock/DockColors.java` (novos, puros) | Alcance um degrau acima; escolha da máquina pelo cursor; estado da linha; cores |
| `dock/PatternDockBlock.java`, `dock/PatternDockBlockEntity.java`, `dock/DockSide.java`, `dock/DockMachine.java` (novos) | O bloco, o block entity e os dados de cada lado |
| `dock/DockDelivery.java`, `dock/DockPending.java` (novos) | A entrega de um pedido (sem tipos do AE2) e o pendente |
| `dock/DockReturn.java` (novo) | O visitante da volta |
| `dock/DockLinks.java`, `dock/DockSameBlock.java`, `dock/DockTuner.java` (novos) | O Vinculador e o Configurador na Doca |
| `dock/DockAe2.java` (novo), `compat/ae2/Ae2Dock.java`, `compat/ae2/Ae2DockMachine.java` (novos) | A ponte e a capability `CRAFTING_MACHINE` |
| `menu/DockMenu.java`, `packet/DockStatePayload.java`, `packet/DockReachPayload.java` (novos) | Menu, estado e o cubo |
| `client/dock/DockScreen.java`, `DockRows.java`, `DockLayout.java`, `DockReachPaint.java` (novos) | Tela e o cubo no mundo |
| `item/LinkerItem.java`, `item/TierCoreItem.java`, `item/tuner/ConfiguratorTuner.java`, `client/linker/LinkerSameBlockInput.java` (mudar) | Os ganchos da Doca |
| `registry/*`, `Config.java`, `WirelessAutomate.java`, `client/ClientSetup.java`, `packet/ModPayloads.java`, `compat/jei/WirelessAutomateJeiPlugin.java` (mudar) | Registros |
| `gametest/DockGameTests.java`, `DockLinkGameTests.java`, `DockMenuGameTests.java`, `Ae2DockGameTests.java`, `Ae2TestSupport.java` (novos) | GameTests |
| `src/test/java/.../network/TickBudgetTest.java` (mudar), `.../dock/DockReachTest.java`, `DockPickTest.java`, `DockStatusTest.java`, `.../client/dock/DockLayoutTest.java` (novos) | JUnit |
| `scripts/textures/doca_modelo.py` (novo), `scripts/textures/gerar_texturas.py` (mudar) | Modelo estático e texturas por tier |
| `build.gradle`, `gradle.properties`, `.github/workflows/build.yml`, `CLAUDE.md`, `src/main/resources/data/wirelessautomate_ae2/structure/empty.nbt` (mudar e novo) | Dependência e run nova |
| `src/main/resources/.../{blockstates,models,textures}/pattern_dock*`, `data/wirelessautomate/loot_table/blocks/pattern_dock.json`, `tags/block/mineable/pickaxe.json`, `lang/*.json` | Recursos |
| `client/dev/E2eDockSteps.java` (novo), `client/dev/E2eSuites.java`, `docs/e2e.md` (mudar) | e2e |
| `scripts/guide/paginas/pattern_dock.py` (novo), `scripts/guide/gerar_guia.py`, `docs/mapa/doca.md` (novo), `CLAUDE.md`, `docs/progresso/`, a spec (mudar) | Docs |

---

### Tarefa 1: lógica pura (alcance, escolha, estado) e a dívida do orçamento

**Arquivos:** criar `dock/DockReach.java`, `dock/DockPick.java`, `dock/DockStatus.java`, `dock/DockColors.java`; mudar `network/TickBudget.java`; testes `DockReachTest`, `DockPickTest`, `DockStatusTest`, e casos novos em `TickBudgetTest`.

**Interfaces produzidas:**
- `DockReach`: `static int radius(int tierOrdinal, IntUnaryOperator routerRadius, int ultimateRadius)`, `static int max(int tierOrdinal, IntUnaryOperator routerMax, int ultimateMax)`, recebendo o ordinal do tier **seguinte** já resolvido (ou -1 para o Ultimate), para ficar sem `RouterTier` e sem `Config`. Constantes `ULTIMATE_RADIUS = 96`, `MAX_ULTIMATE_RADIUS = 128`, `ULTIMATE_MACHINES = 256`.
- `DockPick`: `static int pick(int count, int cursor, IntPredicate usable, IntPredicate accepts, int maxTries)` devolve o índice da primeira máquina a partir do cursor que é usável (acordada, carregada, no alcance) e aceita, ou -1; conta só as usáveis contra `maxTries` (8). Sem alocação.
- `DockStatus`: `enum State { NO_PROVIDER, NO_MACHINES, CRAFTING_PATTERN, MIXED, PROVIDER_FULL, ALL_FULL, WORKING, READY, FREE }` na ordem de gravidade, e `static State of(boolean provider, int machines, int lastRefusal, boolean returnBlocked, boolean allFull, int working)`. As notas (`unloaded`, `outOfReach`) ficam fora do estado.
- `DockColors`: `static int side(int directionOrdinal)` com as cores das restrições globais; `static final int[] ORDER = {2, 3, 5, 4, 1, 0}` (Norte, Sul, Leste, Oeste, Cima, Baixo).
- `TickBudget`: `public void charge(long nanos)` (soma na dívida, limitada a `limitNanos()`), `public boolean outsideRoom()` (dívida menor que o limite); `begin(now)` passa a começar já com a dívida gasta (`startedAt = now - debt`) e a zera.

**Custo:** nenhum em jogo; contas chamadas por pedido (`DockPick`) ou só com a tela aberta (`DockStatus`).

- [ ] Testes que falham: o alcance um degrau acima, inclusive o Ultimate e o teto de 128; a escolha que pula dormindo, volta ao começo, para em 8 usáveis e devolve -1; cada estado na ordem de gravidade (dois fatos verdadeiros, vale o mais grave); a dívida que reduz o tempo do próximo tick e não passa do limite.
- [ ] Implementar até passar (`./gradlew test`).
- [ ] Teste de que a Esmeralda lê a linha do Allthemodium (24/48), e não a do Ultimate, mesmo sem o mod: o `Config.dockRadius` da tarefa 3 usa o ordinal + 1.
- [ ] Commit: "Doca: lógica pura do alcance, da escolha e do estado; dívida no orçamento".

### Tarefa 2: as costuras do motor (índice, caches, visitantes)

**Arquivos:** criar `range/RemoteListener.java`, `range/CacheOwner.java`, `network/EngineVisitor.java`; mudar `range/RemoteIndex.java`, `range/MachineCaches.java`, `network/NetworkManager.java`, `block/RouterBlockEntity.java` (só `implements`, sem linhas novas de corpo; os métodos já existem), `block/RouterRange.java` se precisar.

**Interfaces:**
- `RemoteListener`: `Level listenerLevel()`, `BlockPos listenerPos()`, `void remoteMachineChanged(RemoteMachine machine, @Nullable BlockState state)`. O `RouterBlockEntity` implementa (já tem o método). O `Link` do índice passa a guardar um `RemoteListener`. Novos: `update(RemoteListener owner, Iterable<RemoteMachine> active)` e `remove(RemoteListener owner)`; os de roteador continuam com a mesma assinatura e delegam. `routersAt` continua devolvendo só roteadores (filtra por `instanceof`); novo `listenersAt(Level, BlockPos)`.
- `CacheOwner`: `@Nullable Level ownerLevel()`, `boolean ownerRemoved()`, `void machineInvalidated(MachineCaches caches, ResourceType type, Direction face)`. `MachineCaches.remote(CacheOwner owner, BlockPos pos)` novo; os de roteador continuam iguais.
- `EngineVisitor`: `int visit(long now)` (devolve `MOVED`/`MORE`/0 como o `visit` de porta) e `long wakeAt()`. `NetworkManager.addVisitor(EngineVisitor)`, `removeVisitor(EngineVisitor)`, `wakeVisitor(EngineVisitor)`. No `transfer`, depois das portas e sob o mesmo `budget.hasTime`, um cursor próprio sobre os visitantes acordados.

**Custo:** o caminho quente do mixin ganha uma chamada de interface no lugar de uma chamada direta (a mesma consulta de mapa). O laço de transferência ganha um teste de lista vazia por tick sem Doca. Medir.

- [ ] Bench **antes** (`./scripts/bench.sh` com o cenário padrão e o `alcance`; anotar MSPT e vazão no relatório).
- [ ] Generalizar sem mudar comportamento; todos os GameTests existentes passam sem alteração.
- [ ] GameTest novo em `DockGameTests`: um `EngineVisitor` de teste registrado é visitado, dorme até `wakeAt` e acorda pelo `wakeVisitor`; um `RemoteListener` de teste recebe o `setChanged` de um baú indexado.
- [ ] Bench **depois**, no mesmo cenário; diferença dentro do ruído ou explicada.
- [ ] Commit: "Motor: visitantes, índice das remotas e caches com dono genérico".

### Tarefa 3: o bloco, o block entity e os registros

**Arquivos:** criar `dock/PatternDockBlock.java`, `dock/PatternDockBlockEntity.java`, `dock/DockSide.java`, `dock/DockMachine.java`; mudar `registry/ModBlocks.java`, `ModItems.java`, `ModBlockEntities.java`, `ModCreativeTabs.java`, `ModDataComponents.java` (o `linker_dock`), `Config.java` (seção `patternDock`; `Config.dockRadius(RouterTier)` e `Config.dockMaxMachines(RouterTier)` sobre o `DockReach`), `item/TierCoreItem.java` (alvo `DOCK("pattern_dock")`, a dica "raio 6 → 12, máquinas 8 → 16"), `client/ClientSetup.java` (propriedade de item `tier`), `compat/jei/WirelessAutomateJeiPlugin.java` (`TierSubtype`, informação por tier, esconder tiers sem o mod), loot table, tag de picareta, `lang`.

**O que faz:** bloco cheio sem frente, propriedade `tier`, `PatternDockBlock.tryUpgrade(level, pos, target, origin)` como o do `StorageBlock`. O block entity guarda os seis `DockSide` (vazios por padrão, criados no primeiro vínculo), salva e lê as chaves das restrições globais, implementa `RemoteListener` e `CacheOwner` e se registra no `RemoteIndex` no `onLoad` (só as máquinas no alcance) e sai no `setRemoved`/`onChunkUnloaded`. Quebrar larga o pendente no chão. Por enquanto sem tela (clique direito sem efeito) e com o modelo provisório de um cubo com a textura do roteador do tier (a tarefa 12 troca).

**Custo:** sem ticker; NBT só no salvamento; o registro no índice só no carregamento e quando um vínculo muda.

- [ ] GameTests: colocar, salvar e recarregar um lado com máquinas (round-trip do NBT); o Cartão de Upgrade sobe o tier e a dica mostra raio e máquinas; o item guarda o tier ao quebrar; o pendente cai no chão ao quebrar.
- [ ] `runData` não muda nada (os recursos são escritos à mão ou pelos scripts).
- [ ] Commit: "Doca: bloco com tiers, block entity, config e registros".

### Tarefa 4: vincular pelo Vinculador

**Arquivos:** criar `range/SameBlockScan.java`, `dock/DockLinks.java`, `dock/DockSameBlock.java`; mudar `range/SameBlockLinks.java` (passa a usar o `SameBlockScan`, sem mudar comportamento), `item/LinkerItem.java` (o ramo da Doca antes do `else`; `dock(stack)`/`setDock`), `client/linker/LinkerSameBlockInput.java` (a Doca não é alvo de máquina; com Doca escolhida o Ctrl + clique vale), `packet/RangePayloads.java` (o `LinkSameBlockPayload` despacha para a Doca quando é ela a escolhida), `lang`.

**O que faz** (spec, seção "Vincular"): clique numa face da Doca escolhe a Doca e o lado (clicar no mesmo lado de novo solta); clique numa máquina vincula ou desvincula; Ctrl + clique vincula todas do mesmo bloco no cubo, da mais perto para a mais longe, até o limite, ou desvincula as do lado se a clicada já é dele. Recusas: lado de outro bloco, máquina de outro lado ou de outra Doca carregada (nome e lado de quem tem), fora do cubo, limite do tier, área protegida (`RangeLinks.mayEdit` e `clickAllowed`), não é máquina (`RangeLinks.isMachine`), outra dimensão. Mensagens nas chaves `item.wirelessautomate.linker.dock.*`, no molde das do roteador. O `SameBlockScan` recebe origem, raio, bloco, posições excluídas e o número pedido, e devolve o mesmo `Scan` de hoje.

**Perfil:** ao vincular a **primeira** máquina de um lado, `TunerProbe.analyze(level, pos, front, player)` e a conversão de face absoluta para `RemoteSide` pela frente da máquina (`RemoteSide.fromAbsolute(front, face)`) preenchem o `MachineConfig` do lado; a action bar diz "Lado Norte: Fornalha · Cima Insere · Baixo Extrai". Uma exceção do outro mod na análise vai ao log e o lado fica sem perfil, com a mensagem `failed` do Afinador.

**Custo:** só no clique; a varredura é a de hoje (teto `MAX_READS`, descanso de 10 ticks). Medir o Ctrl + clique no raio 96 (Ultimate) com o `/wa bench` ou um GameTest cronometrado e anotar no relatório.

- [ ] GameTests (`DockLinkGameTests`): escolher o lado; vincular uma fornalha; Ctrl + clique numa fileira de 10 fornalhas com a Doca Básica (vincula 8, "limite do tier"); recusa de outro bloco no mesmo lado; recusa de máquina de outra Doca; fora do cubo; desvincular pelo clique e pelo Ctrl + clique; o perfil da fornalha (Cima e Lados Insere, Baixo Extrai); os testes do `SameBlockLinks` do roteador continuam passando.
- [ ] Commit: "Doca: vincular máquinas a um lado pelo Vinculador, com o perfil pelo Afinador".

### Tarefa 5: o Configurador refaz o lado

**Arquivos:** criar `dock/DockTuner.java`; mudar `item/tuner/ConfiguratorTuner.java` (antes de procurar roteador, procura uma Doca pelo `RemoteIndex.listenersAt`; com Doca, o segundo clique aplica no perfil do lado inteiro, com ou sem Shift), `item/ConfiguratorItem.java` (`actsOn` não muda: a Doca não é máquina), `lang`.

**O que faz:** o gesto de sempre (análise, pintura, segundo clique) numa máquina da Doca troca o perfil do lado inteiro; o aviso diz "Afinador aplicado no lado Norte da Doca · 3 faces". Sem exceção por máquina (decisão do dono).

- [ ] GameTests: com uma fornalha vinculada à Doca, os dois cliques do Configurador mudam o perfil do lado; uma máquina vinculada a um roteador continua com o comportamento de hoje.
- [ ] Commit: "Doca: o Configurador refaz o perfil do lado".

### Tarefa 6: a entrega (sem o AE2)

**Arquivos:** criar `dock/DockDelivery.java`, `dock/DockPending.java`; mudar `dock/PatternDockBlockEntity.java`, `dock/DockSide.java`.

**Interface:** `DockDelivery.offer(PatternDockBlockEntity dock, int side, List<ItemStack> items, List<FluidStack> fluids, boolean simulate)` devolve `Result { ACCEPTED, REFUSED }` e grava o motivo no lado (`NO_MACHINES`, `PENDING`, `MIXED`, `ALL_FULL`, `BUDGET`). `simulate = true` faz os passos 1 e 2 da spec e guarda a máquina escolhida no lado para a execução seguinte do mesmo tick; `simulate = false` executa nela (ou escolhe de novo se não houve simulação), põe o que sobrar no pendente e nunca recusa depois de uma simulação aceita. Também `DockDelivery.acceptsOrders(dock, side)` para o `acceptsPlans`.

**Como:** caches das faces Insere de cada máquina por `MachineCaches.remote(dock, pos)` (criados no vínculo, descartados no desvínculo); `level.isLoaded` antes; cada ingrediente na primeira face Insere que aceita, pela inserção simulada de hoje (`ItemInsertPlan.insert`/`insertStacked` e o `fill` de fluido); máquina que recusa dorme até o despertar do índice ou 100 ticks (`NetworkManager.MAX_SLEEP_TICKS`); o tempo de cada oferta vai para `NetworkManager.get().budget().charge(...)`, e sem `outsideRoom()` a oferta é recusada antes de qualquer consulta. O pendente sai primeiro, na máquina que recebeu o pedido, na próxima oferta e no visitante da volta (tarefa 7).

**Custo:** por oferta, no máximo 8 candidatas × ingredientes simulações; os `ItemStack` do pedido são do chamador. Bench nas tarefas 7 e 8.

- [ ] GameTests chamando `DockDelivery.offer` direto (sem o AE2): três fornalhas recebem três pedidos, um em cada (cursor); todas cheias recusa sem perder nada; pedido misto recusado; a simulação seguida da execução entrega na mesma máquina; o pendente segura o lado e sai quando a máquina esvazia; máquina em chunk descarregado é pulada; máquina fora do alcance (config baixada no lote `wirelessautomate_config_dock`) é pulada e conta na nota.
- [ ] Commit: "Doca: a entrega de um pedido a uma máquina do lado".

### Tarefa 7: a volta

**Arquivos:** criar `dock/DockReturn.java`; mudar `dock/PatternDockBlockEntity.java` (registra o visitante no `onLoad`, tira no `setRemoved`).

**O que faz:** o visitante da Doca guarda as máquinas acordadas (as que receberam pedido ou avisaram pelo índice). Numa visita, para cada uma, até a vazão do tier por tipo (`Config` por `ResourceType`, a mesma tabela do roteador), extrai das faces Extrai do perfil e insere no `IItemHandler`/`IFluidHandler` do vizinho daquele lado da Doca (cache por `BlockCapabilityCache` na face encostada). Vazia, a máquina sai da lista dos acordados. Destino cheio: o lado marca "provedor cheio" e dorme até o vizinho mudar (listener do cache) ou 100 ticks. Atualiza a última atividade da máquina (para o "trabalhando") e conta `gave` para a tela.

**Custo:** parado, nada (visitante sem máquinas acordadas tem `wakeAt` = nunca). Por visita, proporcional às máquinas acordadas, sob o `budget.hasTime`.

- [ ] Bench antes e depois com um cenário novo `doca` no `bench.sh` (32 fornalhas numa Doca Elite com um baú no lugar do provedor e pedidos por `DockDelivery.offer` de um comando de bench), anotando MSPT, vazão e o tempo da entrega.
- [ ] GameTests: o carvão vegetal da fornalha volta ao baú vizinho do lado; o combustível fica; baú cheio segura e retoma quando esvazia; a Doca sem pedido não é visitada (contador de visitas do teste).
- [ ] Commit: "Doca: a volta do resultado ao vizinho do lado, pelo motor".

### Tarefa 8: o AE2 (dependência, run, capability)

**Arquivos:** mudar `build.gradle` (`compileOnly 'org.appliedenergistics:appliedenergistics2:19.2.17:api'` do Maven Central, a mesma versão do `clientMods`; configuração `ae2TestMods` com o AE2 e o GuideME do `clientMods`; tarefa `prepareAe2TestMods`; run `gameTestServerAe2` com `run/gametest-ae2`, namespace `wirelessautomate_ae2` e `wirelessautomate.ae2Tests=true`; a run nas listas de apagar o mundo, do `oneGame` e da conferência do log, e o `runGameTestServerTeams` nas três também, que faltava), `.github/workflows/build.yml` (passo novo), `CLAUDE.md` (o comando e a linha de antes do commit); criar `src/main/resources/data/wirelessautomate_ae2/structure/empty.nbt` (cópia do de químicos), `dock/DockAe2.java`, `compat/ae2/Ae2Dock.java`, `compat/ae2/Ae2DockMachine.java`, `gametest/Ae2DockGameTests.java`, `gametest/Ae2TestSupport.java`.

**O que faz:** `DockAe2.register(RegisterCapabilitiesEvent)` (chamado do registro de capabilities do mod, só com `LOADED`) registra `AECapabilities.CRAFTING_MACHINE` no block entity da Doca, um `Ae2DockMachine` por face (seis objetos por Doca, criados sob demanda). `acceptsPlans()` = `DockDelivery.acceptsOrders`. `pushPattern(padrão, entradas, lado)`: recusa se `!supportsPushInputsToExternalInventory()` (motivo `CRAFTING_PATTERN`); converte o `KeyCounter[]` em `ItemStack` (partidos no tamanho máximo do item) e `FluidStack` (`AEItemKey`/`AEFluidKey`; outra chave recusa como misto); chama `offer` com `simulate = false`. `getCraftingMachineInfo()` = `PatternContainerGroup(ícone da máquina do lado, "Fornalha × 12", [])`. O "tem provedor" do lado: `GENERIC_INTERNAL_INV` sem `ME_STORAGE` no vizinho, ou oferta nos últimos 60 s (ajuste 4).

- [ ] GameTests na run nova (`Ae2DockGameTests`, `@GameTestHolder("wirelessautomate_ae2")`, com o teste sentinela "o AE2 carregou"): um Pattern Provider com um padrão de processamento de fornalha encostado no lado Norte e três fornalhas; um pedido de 3 pela CPU (montar a rede pelo `Ae2TestSupport`: controlador, energia criativa, CPU, drive com célula) termina com os 3 lingotes na rede e as três fornalhas usadas; todas cheias seguram a CPU sem perder nada; padrão de bancada recusado e a linha marca o motivo; o provedor com "trava até o resultado" destrava; o `getCraftingMachineInfo` dá o nome do lado. Se montar a CPU no GameTest não for viável, chamar `PatternProviderLogic.pushPattern` direto do `Ae2TestSupport` e registrar a limitação no relatório.
- [ ] `runGameTestServer` (sem o AE2) passa: o mod carrega sem ele.
- [ ] Bench do cenário `doca` com o AE2 de verdade (provedor e CPU com 32 coprocessadores no servidor do `bench.sh`, se o AE2 couber lá; senão, o cenário da tarefa 7 basta e fica a pendência).
- [ ] Commit: "Doca: o AE2 entrega pela capability CRAFTING_MACHINE, com run de GameTest própria".

### Tarefa 9: a tela

**Arquivos:** criar `menu/DockMenu.java`, `packet/DockStatePayload.java`, `client/dock/DockScreen.java`, `client/dock/DockRows.java`, `client/dock/DockLayout.java` (puro, com JUnit `DockLayoutTest`); mudar `registry/ModMenus.java`, `packet/ModPayloads.java` (VERSION 50), `client/ClientSetup.java`, `dock/PatternDockBlock.java` (clique direito abre), `lang`.

**O que faz** (mockup aprovado): cabeçalho com a faixa e o Olho na cor do tier (`GuiPaint.panel`, `eye`, `pill`) e o `ThemeToggle`; o resumo "30/32 máquinas" com a barra (âmbar no máximo) e "raio 16"; as seis linhas na ordem de `DockColors.ORDER` com a etiqueta do lado, o ícone e o nome da máquina (`RouterBlock.machineName` no servidor, mandado como texto), "12 · 9 trabalhando", o estado com o sinal de forma (ponto, anel, triângulo) e as notas "N em área descarregada" e "N fora do alcance"; lado vazio apagado com "livre"; a dica da linha com o provedor e a frase longa. Todo texto variável por `GuiText`. Sem slots.

**Sincronia:** o `DockMenu` segue o `StorageScalarMenu`: manda na abertura, depois só com mudança, no máximo uma vez por segundo, só para o `viewer`. O `DockStatePayload` leva por lado: bloco (id ou vazio), nome da máquina, provedor (nome ou vazio), vinculadas, trabalhando, descarregadas, fora do alcance, estado (`DockStatus.State.ordinal()`); e o total, o máximo e o raio.

- [ ] JUnit do `DockLayout` (posições das seis linhas e das colunas na largura da tela; nada passa da borda).
- [ ] GameTests (`DockMenuGameTests`): `pollState` manda na abertura, não manda sem mudança, manda depois de um vínculo; round-trip do codec; nada é mandado com a tela fechada.
- [ ] e2e: suíte `doca` (`E2eDockSteps`, registrada em `E2eSuites` com o pré-requisito `NENHUM`): monta os estados do mockup (base em uso, recém-colocada, config baixada) por comandos de dev, abre a tela nos dois temas e captura `doca-*`; `GuiText.clipCount()==0`. Conferir as capturas contra o mockup e atualizar a tabela de `docs/e2e.md`.
- [ ] Commit: "Doca: a tela de seis lados".

### Tarefa 10: o cubo do alcance no mundo

**Arquivos:** criar `packet/DockReachPayload.java`, `client/dock/DockReachPaint.java`; mudar `dock/DockLinks.java` (manda ao escolher e depois de cada vínculo feito pelo jogador), `dock/PatternDockBlock.java` (`setPlacedBy` manda só a quem colocou, com prazo de 100 ticks), `packet/ModPayloads.java`, `client/ClientSetup.java`.

**O que faz:** `DockReachPayload(BlockPos dock, int radius, long[] machines, byte[] sides, int ticks)`; `ticks = 0` vale enquanto o Vinculador com essa Doca escolhida estiver na mão (o componente `linker_dock` já chega ao cliente). O desenho, no molde do `HighlightRenderer` e do `TunerPaint` (`RenderLevelStageEvent`, `AFTER_TRANSLUCENT_BLOCKS`): as arestas do cubo na cor do tier e cada máquina com moldura na cor do lado; só a menos de 128 blocos da câmera; vértices montados na chegada do payload, por quadro só soma a câmera e desenha.

**Custo:** um payload por escolha, por vínculo e por colocação, só para aquele jogador; zero quando ninguém segura o Vinculador.

- [ ] e2e: captura `doca-cubo` com o Vinculador na mão e uma fileira de fornalhas vinculadas.
- [ ] Commit: "Doca: o cubo do alcance e as máquinas acesas ao segurar o Vinculador".

### Tarefa 11: o modelo estático e as texturas por tier

**Arquivos:** criar `scripts/textures/doca_modelo.py` (no molde do `reparador_modelo.py`: lê `docs/preview/doca-de-padroes.bbmodel`, converte só as partes de modelo de bloco da tabela da spec (esqueleto, painéis, vigias, contatos; o giroscópio, o pacote e os fragmentos ficam para a etapa 3), funde caixas iguais vizinhas para baixar das 193, grava em `models/block/pattern_dock/`, uma textura por tier com o circuito na cor da lente (estático nesta etapa; a `.mcmeta` animada é da etapa 3), o blockstate `tier=<t>` e o item com as sobreposições de `wirelessautomate:tier`); mudar `scripts/textures/gerar_texturas.py` (chama o `gerar()` e protege as texturas da limpeza).

**Notas:** as coordenadas do `.bbmodel` têm o bloco centrado em x e z (somar 8); UV fora de 0..16 renderiza preto; o miolo vazio mostra o fundo do painel pela vigia até a etapa 3.

- [ ] `python -I scripts/textures/gerar_texturas.py` sem erro; o número de elementos esperado conferido no script.
- [ ] e2e: captura `doca-blocos` com as oito Docas lado a lado (as do Allthemodium só com o mod); comparar com `docs/preview/doca-de-padroes-repouso.gif`.
- [ ] Commit: "Doca: modelo estático e texturas por tier".

### Tarefa 12: guia, JEI e docs

**Arquivos:** criar `scripts/guide/paginas/pattern_dock.py` (grupo `integracoes`; só "como operar", nos dois idiomas: os três passos da spec, o que a tela diz, "não multiplique os padrões; ponha coprocessadores na CPU", o aviso do roteador extraindo da mesma máquina, os limites); mudar `scripts/guide/gerar_guia.py` (`PAGINAS`), a informação do JEI por tier (raio e máquinas); criar `docs/mapa/doca.md` e a linha no índice do `CLAUDE.md`; atualizar `docs/mapa/alcance.md` (o `RemoteListener`, o `CacheOwner`, o `SameBlockScan`), `docs/mapa/motor.md` (visitantes, a dívida do orçamento), `docs/mapa/testes.md` (a run nova), `docs/mapa/compat.md` (`compat/ae2`).

- [ ] Gerar o guia e conferir as duas línguas.
- [ ] Commit: "Doca: guia, JEI e mapa do código".

### Tarefa 13: fechamento

- [ ] Corrigir a spec pelos "Ajustes em relação à spec" (a volta como visitante, a dívida, o cubo novo, o "tem provedor", o limite da unicidade).
- [ ] `docs/progresso/`: linha no histórico com os números de testes (JUnit, as seis runs de GameTest, e2e), `estado.md` (Doca de Padrões, etapa 1), `pendencias.md` (a unicidade entre Docas descarregadas, o que a revisão final deixar, o bench com o AE2 se ficou de fora), o próximo passo no `README.md` (etapa 2, o RS2).
- [ ] Revisão final do ramo inteiro no modelo mais capaz (aderência à spec e ao plano, leis da `wa-performance`, `wa-modular`, `wa-comentarios`).
- [ ] Commit: "Doca de Padrões: fechamento da etapa 1".
