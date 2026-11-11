# Doca de Padrões: plano de implementação (etapa 2: o Refined Storage 2)

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar. Um implementador por vez; revisão de cada tarefa antes da seguinte.

**Objetivo:** o Autocrafter do Refined Storage 2 encostado num lado da Doca entrega pedidos a ela como o Pattern Provider do AE2 já entrega, e o resultado das máquinas volta para a rede do RS2 daquele Autocrafter. O RS2 é opcional, como o AE2.

**Arquitetura:** a Doca já tem a entrega sem tipos de mod (`DockDelivery.offer(dock, side, items, fluids, simulate)`, com a simulação guardando a máquina para a execução do mesmo tick e a execução que nunca recusa depois de uma simulação aceita) e a volta como visitante do motor (`DockReturn`). Esta etapa acrescenta:

- **o destino do RS2:** um `PatternProviderExternalPatternSinkFactory` registrado no carregamento do mod (`RefinedStorageApi.INSTANCE.addPatternProviderExternalPatternSinkFactory`). O Autocrafter cria o destino uma vez, com a posição da Doca e a face dela encostada no Autocrafter, e guarda; por isso o destino da Doca é preguiçoso, e a cada pedido confere se ainda há uma Doca na posição (senão `SKIPPED`);
- **a volta para a rede do RS2:** o `DockReturn` ganha um destino por lado que, num lado com Autocrafter, insere na rede dele pelo `StorageNetworkComponent` (a `common-api` publicada), em vez do `IItemHandler`/`IFluidHandler` do vizinho, que o Autocrafter não tem.

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), ModDevGradle, JUnit 5, GameTests, RS2 2.0.9 (`common-api` `compileOnly`; o mod inteiro só no `runClient` e na run nova).

**Spec:** [`docs/superpowers/specs/2026-11-10-doca-de-padroes-design.md`](../specs/2026-11-10-doca-de-padroes-design.md) (seções "O que a conferência mudou", itens 5 a 8; "A entrega", item RS2; "A volta", item RS2). Conferência da API do RS2: [`2026-11-10-ae2-rs2-proposta.md`](../specs/2026-11-10-ae2-rs2-proposta.md), seção "A confirmar antes de qualquer plano", itens 4 e 5. Etapa 1: [`2026-11-10-doca-de-padroes-etapa-1.md`](2026-11-10-doca-de-padroes-etapa-1.md).

## O que o código do RS2 diz (conferido em 10/11/2026, ramo `support/2.x`)

- `ExternalPatternSink.Result`: `ACCEPTED`, `REJECTED`, `SKIPPED`, `LOCKED`. A tarefa chama `accept(resources, SIMULATE)` e depois `accept(resources, EXECUTE)`; se a execução não for `ACCEPTED`, o RS2 dá os ingredientes por entregues (some com eles) e só registra um aviso. **A execução da Doca nunca recusa depois de uma simulação aceita** (a regra que a entrega da etapa 1 já cumpre).
- O destino do Autocrafter junta os destinos de **todas** as fábricas registradas para aquela posição (`CompositePatternProviderExternalPatternSink`); a Doca não expõe inventário, então o destino de inventário comum do RS2 não acha nada nela.
- `PatternProviderExternalPatternSink.accept(Collection<ResourceAmount>, Action)` não recebe o padrão, só os recursos; `applies(ResourceKey)` ordena os destinos por tipo; `PlatformPatternProviderExternalPatternSink.isEmpty()` é a trava "até a máquina esvaziar" do Autocrafter.
- O resultado conta quando entra na rede do RS2 por qualquer caminho (`TaskImpl.beforeInsert`). O Autocrafter não tem inventário.
- Autocrafters encadeados (um apontando para o outro) usam o destino do primeiro: uma corrente num lado soma os padrões.
- Maven: `com.refinedmods.refinedstorage:refinedstorage-common-api:2.0.9` (traz as APIs de rede, armazenamento, recurso e autocrafting) e o mod `com.refinedmods.refinedstorage:refinedstorage-neoforge:2.0.9`, no `https://maven.creeperhost.net`. O `refinedstorage-neoforge-api` **não** é publicado: a rede de um bloco é achada pelo `AbstractNetworkNodeContainerBlockEntity` da `common-api` (`getContainerProvider().getContainers()` → `getNode().getNetwork()` → `getComponent(StorageNetworkComponent.class)`), não pela capability.
- Código para consulta (só leitura): `git clone --depth 1 -b support/2.x https://github.com/refinedmods/refinedstorage2.git` numa pasta do scratchpad.

## Restrições globais

Valem todas as restrições globais da etapa 1 (idioma, ids, NBT, componentes, alcance, cores, cliente só em `client/`, máquinas remotas, performance, tamanho, GameTests, commits), mais:

- **RS2 é opcional:** tipos do RS2 só em `compat/refinedstorage/` e `gametest/Rs2TestSupport`, nunca na assinatura de método de classe `@EventBusSubscriber` ou `@GameTestHolder`. A ponte comum é `dock/DockRs2.java` (`LOADED = ModList...isLoaded("refinedstorage")`, métodos que só delegam). O `runGameTestServer` (sem o RS2) e o `runGameTestServerAe2` (sem o RS2) provam que o mod carrega sem ele.
- **Run nova:** `runGameTestServerRefined`, namespace `wirelessautomate_refined`, propriedade `wirelessautomate.refinedTests=true`, pasta `run/gametest-refined`, template `src/main/resources/data/wirelessautomate_refined/structure/empty.nbt`; nas listas de apagar o mundo, do `oneGame` e da conferência do log no `build.gradle`; no CI; no `CLAUDE.md` (lista de comandos, a linha de antes do commit e uma linha "RS2 é opcional" nas convenções, como a do AE2); dependência opcional `refinedstorage` no `neoforge.mods.toml`.
- **Nenhuma chave nova de NBT nem componente**, a não ser o que uma tarefa disser. **Protocolo:** esta etapa não cria payload; `ModPayloads.VERSION` continua `"50"` (a etapa 1 ainda não foi publicada).
- **A Doca não expõe `IItemHandler` nem `IFluidHandler`** (vale em dobro aqui: o destino de inventário do RS2 entraria junto).
- **Antes de cada commit:** `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams runGameTestServerAe2` e, a partir da tarefa 1, `runGameTestServerRefined`; as varreduras de performance, comentários e tamanho nos arquivos mudados. Compile antes; com `BUILD FAILED`, conserte, nunca espere; um jogo por vez.
- Commits com o autor do dono e o committer Claude, sem linhas de atribuição: `GIT_COMMITTER_NAME=Claude GIT_COMMITTER_EMAIL=noreply@anthropic.com git -c user.name=MatheusANBS -c user.email=matheusanbs@hotmail.com commit -m "..."`.

## Mapa de arquivos

| Arquivo | Papel |
| --- | --- |
| `build.gradle`, `gradle.properties`, `.github/workflows/build.yml`, `CLAUDE.md`, `META-INF/neoforge.mods.toml`, `data/wirelessautomate_refined/structure/empty.nbt` | Dependência, run nova, CI |
| `dock/DockRs2.java` (novo) | A ponte sem tipos do RS2 |
| `compat/refinedstorage/Rs2Dock.java`, `Rs2DockSink.java`, `Rs2DockNetwork.java` (novos) | A fábrica e o destino; a inserção na rede do Autocrafter |
| `dock/DockReturn.java`, `dock/DockSide.java` (mudar) | O destino da volta por lado (vizinho comum ou rede do RS2) |
| `dock/DockDelivery.java`, `dock/DockStatus.java`, `menu/DockMenu.java` (mudar, se preciso) | O "tem provedor" do RS2, `isEmpty`, a linha da tela |
| `gametest/Rs2DockGameTests.java`, `gametest/Rs2TestSupport.java` (novos) | GameTests da run nova |
| `scripts/guide/paginas/pattern_dock.py`, `lang/*.json`, `docs/mapa/*`, `docs/progresso/*`, a spec (mudar) | Textos e docs |

---

### Tarefa 1: a dependência e a run do RS2

**Arquivos:** `build.gradle` (`compileOnly 'com.refinedmods.refinedstorage:refinedstorage-common-api:2.0.9'` com o repositório `https://maven.creeperhost.net` restrito ao grupo `com.refinedmods`; configuração `refinedTestMods` com o `refinedstorage-neoforge:2.0.9` e o que ele exigir para carregar; tarefa `prepareRefinedTestMods`; run `gameTestServerRefined`; o RS2 também no `clientMods`, para o teste à mão, com o jar no `exclude` do `prepareClientMods` se for o caso), `gradle.properties` (a versão numa chave), CI, `CLAUDE.md`, `neoforge.mods.toml`, `empty.nbt`, `gametest/Rs2DockGameTests.java` com o teste sentinela "o RS2 carregou".

**Custo:** nenhum em jogo.

- [ ] Conferir no `refinedstorage-neoforge-2.0.9.pom` (e no `neoforge.mods.toml` do jar) as dependências obrigatórias do RS2 e trazê-las à run.
- [ ] A run nova sobe e o sentinela passa; o `runGameTestServer` e as outras runs continuam passando.
- [ ] Commit: "Doca: dependência do RS2 e run de GameTest própria".

### Tarefa 2: o destino do RS2

**Arquivos:** criar `dock/DockRs2.java`, `compat/refinedstorage/Rs2Dock.java`, `compat/refinedstorage/Rs2DockSink.java`; mudar o registro do mod (onde o `DockAe2.register` é chamado, no carregamento comum: a fábrica do RS2 é registrada uma vez, no `FMLCommonSetupEvent` ou equivalente, só com o RS2), `dock/DockDelivery.java` se faltar algo.

**O que faz:**
- `Rs2Dock.register()` chama `RefinedStorageApi.INSTANCE.addPatternProviderExternalPatternSinkFactory((level, pos, direction) -> new Rs2DockSink(level, pos, direction))`.
- `Rs2DockSink` (implementa `PlatformPatternProviderExternalPatternSink`): a cada `accept`, lê o block entity na posição (`level.isLoaded(pos)` antes); sem Doca, `SKIPPED`. O lado é `direction.ordinal()` (confirmar no código do Autocrafter: `initialize` passa `direction.getOpposite()` com a posição vizinha, logo a face da Doca encostada nele). Converte os `ResourceAmount` em `ItemStack` (partidos no tamanho máximo, como o AE2) e `FluidStack` (o recurso de fluido do RS2: `FluidResource`, com a quantidade em mB; conferir a unidade); outro tipo de recurso, `REJECTED` com o motivo `MIXED`. `SIMULATE` → `DockDelivery.offer(..., simulate=true)`: aceito → `ACCEPTED`, recusado → `REJECTED`, também com pendente (o motivo `PENDING` fica no lado). **Nunca `LOCKED`:** o RS2 junta os destinos da posição e um `LOCKED` seguido do `SKIPPED` do destino de inventário dele vira `ACCEPTED`, e os ingredientes sumiriam na execução (ver a spec, item 6 de "O que a conferência mudou"). `EXECUTE` → `offer(..., false)`, sempre `ACCEPTED` depois de uma simulação aceita no mesmo tick. `applies(resource)` só para itens e fluidos. `isEmpty()`: o lado sem pendente e sem máquina trabalhando (`DockStatus.WORKING_TICKS`). Sem alocação por pedido além dos stacks.
- **"Tem provedor"** do RS2: o vizinho do lado é um Autocrafter (o id do bloco no namespace `refinedstorage` termina em `autocrafter`, sem tipo do RS2) ou o lado recebeu oferta do RS2 nos últimos 60 s. O nome do provedor na tela, pelo `RouterBlock.machineName`.

- [ ] GameTests (run nova, chamando o destino direto pela fábrica registrada): sem Doca na posição, `SKIPPED`; simulação e execução entregam na mesma fornalha; todas cheias, `REJECTED` e nada se perde; pedido com outro recurso, `REJECTED`; com pendente, `REJECTED` (motivo `PENDING`) e nada se perde; `isEmpty` falso logo depois de um pedido e verdadeiro sem pedido; o "tem provedor" com um Autocrafter encostado.
- [ ] Commit: "Doca: o Autocrafter do RS2 entrega à Doca".

### Tarefa 3: a volta para a rede do RS2

**Arquivos:** criar `compat/refinedstorage/Rs2DockNetwork.java`; mudar `dock/DockReturn.java`, `dock/DockSide.java`, `dock/DockRs2.java`.

**O que faz:** o destino da volta de cada lado passa a ser um de dois: o `IItemHandler`/`IFluidHandler` do vizinho (o que existe hoje, para o AE2 e para baús) ou, quando o vizinho é um Autocrafter, a rede dele. `DockRs2.insert(level, providerPos, stack, simulate)` (e o de fluido) acha a rede pelo `AbstractNetworkNodeContainerBlockEntity` (com `isLoaded` antes; sem rede, devolve 0 e o lado marca "provedor cheio" e dorme como hoje) e insere pelo `StorageNetworkComponent.insert(resource, amount, action, actor)` com um `Actor` da Doca. Simula antes de extrair da máquina, como hoje. Sem busca por visita: a rede é guardada por lado e conferida (o nó ainda ativo, o mesmo bloco) só quando a inserção falha ou o vizinho muda (o `onNeighborChange` que a Doca já tem).

**Custo:** por visita, o mesmo da volta de hoje; a busca da rede só na troca do vizinho.

- [ ] GameTests (run nova): o melhor caminho é uma rede do RS2 de verdade (Controlador Criativo ou fonte de energia, um Disk Drive com disco, o Autocrafter com um padrão de processamento de fornalha encostado na Doca, três fornalhas) e um pedido de 3 pelo `AutocraftingNetworkComponent` que termina com os lingotes na rede e a tarefa encerrada; se montar a rede ou codificar o padrão num GameTest for inviável, inserir na rede direto pelo `Rs2DockNetwork` e registrar a limitação no relatório. Também: a volta para quando a rede some (Controlador quebrado) e retoma quando volta.
- [ ] Commit: "Doca: o resultado volta para a rede do RS2".

### Tarefa 4: textos, guia e fechamento

**Arquivos:** `lang/*.json` (a dica "sem provedor" volta a citar o Autocrafter; o nome "Autocrafter × N" se houver corrente), `scripts/guide/paginas/pattern_dock.py` (o RS2 na página: o Autocrafter num lado, Autocrafters encadeados para mais padrões, a volta pela rede), a spec (estado da etapa 2 e os ajustes da execução), `docs/mapa/doca.md`, `compat.md`, `testes.md`, `docs/progresso/` (histórico, estado, pendências, próximo passo: etapa 3).

- [ ] e2e: a suíte `doca` continua OK (a tela não muda); se o RS2 estiver no `runClient`, uma captura da tela com um lado de Autocrafter.
- [ ] Revisão final da etapa (modelo mais capaz), correções.
- [ ] Commit: "Doca de Padrões: fechamento da etapa 2".
