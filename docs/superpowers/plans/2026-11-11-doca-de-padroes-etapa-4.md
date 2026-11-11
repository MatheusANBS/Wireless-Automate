# Doca de Padrões: plano de implementação (etapa 4: pedidos misturados, com químicos)

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Um implementador por vez; revisão de cada tarefa antes da seguinte.

**Objetivo:** a Doca aceita pedidos com item, fluido e químico juntos (o caso comum do Mekanism), entrega o pedido inteiro a uma máquina, põe cada recurso na face certa dela e devolve o resultado, inclusive químico, à rede ME (com o Applied Mekanistics) ou à rede do RS2 (com o Refined Storage Mekanism Integration).

**Decisões do dono (11/11/2026):** a etapa cobre item, fluido e químico (o químico só com o Mekanism e o addon de cada rede instalados); o que a máquina não aceitar no meio de um pedido misturado vai para o pendente do lado (a regra de hoje), e o lado não aceita pedido novo até ele sair; executar direto, como as etapas 2 e 3.

**O que o código dos addons diz (conferido em 11/11/2026):**
- **Applied Mekanistics** (ramo `dev/1.21.1`, `AppliedEnergistics/Applied-Mekanistics`; o ATM10 8.5 traz a 1.6.3): o químico é a chave `me.ramidzkh.mekae2.ae2.MekanismKey` (`of(ChemicalStack)`, `getStack()`, `withAmount(long)`), um `AEKey` como `AEItemKey` e `AEFluidKey`. Ele chega no `KeyCounter[]` do `pushPattern` e entra na rede ME pelo mesmo `MEStorage.insert`.
- **Refined Storage Mekanism Integration** (ramo `main`, `refinedmods/refinedstorage-mekanism-integration`; o ATM10 traz a 1.2.0, publicada no `maven.creeperhost.net` como `com.refinedmods.refinedstorage:refinedstorage-mekanism-integration`): o químico é o recurso `com.refinedmods.refinedstorage.mekanism.ChemicalResource(Chemical chemical)` (`ofChemicalStack`). O addon registra a própria fábrica de destino de químico (`ChemicalPatternProviderExternalPatternSinkFactory`), que procura a capability de químico do bloco — a Doca não expõe nenhuma, então ela responde `SKIPPED`. **O destino composto do RS2 entrega todos os recursos do pedido a todos os destinos**: o destino da Doca recebe o químico junto com o item e precisa tratá-lo.
- Código para consulta (só leitura): os dois clones em `/tmp/claude-0/-home-user-Wireless-Automate/c770d04c-6ad3-5faa-9790-aafcae46a8c4/scratchpad/ext/appmek` e `.../ext/rsmek` (se não existirem, clonar nos ramos acima).

## Restrições globais

Valem as das etapas 1 a 3, mais:

- **Tipos do Mekanism** só em `compat/mekanism`, `ChemicalTransfer`, `gametest/ChemicalTestSupport` **e nos dois pacotes novos** `compat/appmek` (Applied Mekanistics) e `compat/rsmek` (Refined Storage Mekanism Integration); nunca na assinatura de método de classe `@EventBusSubscriber` ou `@GameTestHolder`. O resto da Doca passa os químicos como `Object` pela ponte `network/Chemicals` (como o motor já faz). O `CLAUDE.md` ganha as duas pastas na regra do Mekanismo.
- **Os addons são opcionais em dobro:** o químico do AE2 só com o Mekanism **e** o Applied Mekanistics; o do RS2 só com o Mekanism **e** a integração. Cada ponte tem o seu `LOADED`. Sem eles, um pedido com recurso que a Doca não conhece é recusado como hoje (`MIXED` passa a significar "tipo que a Doca não conhece").
- **NBT:** o pendente ganha a entrada `{chemical}` (o `ChemicalStack` salvo pela ponte, só com o Mekanism; sem ele, a entrada é guardada crua e devolvida ao recolocar, nunca descartada). O componente `pattern_dock_pending` do item leva o químico do mesmo jeito. Nenhuma chave existente muda.
- **Protocolo:** sem payload novo, `ModPayloads.VERSION` continua `"50"`.
- **Run nova:** `runGameTestServerDockChemicals`, namespace `wirelessautomate_dockchem`, com o Mekanism, o AE2 (com o GuideME), o Applied Mekanistics, o RS2 e a integração do RS2 na pasta mods; nas listas do `build.gradle`, no CI e no `CLAUDE.md`.
- **Verificação:** todas as runs (`build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams runGameTestServerAe2 runGameTestServerRefined` e, a partir da tarefa 3, `runGameTestServerDockChemicals`), as varreduras e o e2e se mexer na tela. Commits com o autor do dono e o committer Claude, sem atribuição.

## Tarefas

### Tarefa 1: o pedido misturado no núcleo (item e fluido)

- [ ] `DockDelivery.offer` aceita itens e fluidos no mesmo pedido (e uma lista de químicos como `Object`, vazia sem o Mekanism): para cada máquina candidata, simula cada recurso nas faces Insere **do tipo dele** (a face mais seletiva por tipo, a regra de hoje), e só aceita se tudo coube; na execução, o que a máquina não aceitar vai para o pendente (que já guarda itens e fluidos). O `MIXED` deixa de recusar item + fluido.
- [ ] O pendente e o componente do item guardam químicos (a entrada `{chemical}` pela ponte).
- [ ] `DockStatus`: `MIXED` vira "tipo desconhecido" (frases novas nos dois lang: "Pedido com recurso que a Doca não conhece" / longa), JUnit atualizado.
- [ ] GameTests (run comum, chamando a entrega direto): um pedido "1 balde de água + 1 item" numa máquina de teste com uma face Insere de item e outra de fluido entrega cada um na sua face; máquina que aceita o item e recusa o fluido na execução põe o fluido no pendente e o lado segura; o AE2 e o RS2 (runs deles) passam a aceitar um padrão de item + fluido (um teste em cada: o pedido chega inteiro à máquina).
- [ ] Commit: "Doca: pedidos com item e fluido juntos".

### Tarefa 2: o químico na entrega e na volta (sem os addons de rede)

- [ ] A entrega põe o químico nas faces Insere de químico da máquina (o `MachineCaches.chemicals(face)` e a ponte `Chemicals`), com a face mais seletiva por tipo; o perfil do Afinador já sabe químicos (`TunerProbe` com o Mekanism).
- [ ] A volta tira químico das faces Extrai de químico até a vazão do tier (`ResourceType.CHEMICAL`) e insere no destino do lado: o tanque de químico do vizinho (pela ponte) nesta tarefa; as redes vêm nas tarefas 3 e 4.
- [ ] GameTests na run `runGameTestServerChemicals` (já tem o Mekanism; tipos só no `ChemicalTestSupport`): um pedido "item + químico" numa máquina do Mekanism de duas entradas (ou numa máquina de teste com face de químico) entrega cada um na sua face; o químico de saída volta a um tanque de químico encostado no lado.
- [ ] Commit: "Doca: químicos na entrega e na volta".

### Tarefa 3: o AE2 com o Applied Mekanistics

- [ ] `build.gradle`: `compileOnly` do Applied Mekanistics (as coordenadas publicadas: confira o repositório Maven do projeto; senão cursemaven, só para compilar o `compat/appmek`); a run nova `gameTestServerDockChemicals` com o Mekanism, o AE2, o GuideME, o Applied Mekanistics, o RS2 e a integração do RS2; CI; `CLAUDE.md`; `neoforge.mods.toml` (dependência opcional `appmek`).
- [ ] `compat/appmek/AppMekDock`: no `pushPattern`, a `MekanismKey` vira o químico do pedido (pela ponte; sem o addon, recusa como hoje); na volta direta para a rede ME, o químico entra como `MekanismKey`.
- [ ] GameTests na run nova: um padrão de processamento com químico (por exemplo, o de uma máquina do Mekanism que pede químico) pela CPU termina com o resultado na rede; um resultado químico volta à rede ME.
- [ ] Commit: "Doca: químicos com o AE2 (Applied Mekanistics)".

### Tarefa 4: o RS2 com a integração do Mekanism

- [ ] `build.gradle`: `compileOnly` da `refinedstorage-mekanism-integration:1.2.0` (maven.creeperhost.net); `neoforge.mods.toml` (dependência opcional).
- [ ] `compat/rsmek/RsMekDock`: o destino da Doca trata o `ChemicalResource` (o composto do RS2 entrega todos os recursos a todos os destinos); a volta para a rede do RS2 insere químico como `ChemicalResource`; o caminho "insere antes, tira depois" da volta para o RS2 vale também para químicos.
- [ ] GameTests na run nova: um pedido com químico pelo Autocrafter chega inteiro à máquina; um resultado químico volta à rede do RS2 e a tarefa termina.
- [ ] Commit: "Doca: químicos com o RS2 (integração do Mekanism)".

### Tarefa 5: textos, guia e fechamento

- [ ] Guia (item + fluido + químico, os addons necessários), spec (estado da etapa 4, ajustes), mapas, `docs/progresso/`.
- [ ] e2e da suíte `doca` (o estado "tipo desconhecido" numa linha).
- [ ] Revisão final da etapa (modelo mais capaz) e correções.
- [ ] Commit: "Doca de Padrões: fechamento da etapa 4".
