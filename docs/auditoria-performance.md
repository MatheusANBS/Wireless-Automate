# Auditoria de performance (2026-10-07)

Leitura do código feita por quatro agentes (laço de transferência, rotas, filtros e o que fica fora do laço), depois da 0.1.1.

**Estado (8/10/2026): aplicada na 0.1.1**, por subagentes em três frentes, com os números de antes e depois em [benchmark.md](benchmark.md) ("Auditoria de performance: antes e depois"). Tudo aplicado, menos: o **#8 (lote mínimo), recusado pelo dono** (o Básico continua com fluxo contínuo); dos menores, o **formato do NodeIndex** (impediria abrir o mundo numa 0.1.0) e as **alocações por quadro nas telas** (só cliente, ganho mínimo), deixados de fora por baixo impacto. O #14 precisou de um ajuste depois do benchmark (no baú vanilla a pilha cheia é decidida pelo máximo do item, não pelo limite do slot). O texto abaixo é o da auditoria original. Caminhos relativos a `src/main/java/io/github/matheusanbs/wirelessautomate/`. Ganhos são estimativas de leitura, a confirmar no benchmark.

## Onda 1: acordar e remontar menos (maior ganho, risco baixo)

| # | Problema | Onde | Correção | Risco |
| --- | --- | --- | --- | --- |
| 1 | Toda inserção nossa num destino chama `setChanged` na máquina, que acorda **todas** as origens da rede (`sharedFeeders`), inclusive as vazias. Com 1 origem ativa e 250 vazias, as 250 voltam a varrer até 128 slots a cada tick com entrega. Os cenários do bench não pegam. (achado por duas auditorias) | `network/NodePorts.java` (`wake` → `wakeAll`), `block/RouterBlock.onNeighborChange` | Enquanto o `NetworkManager` insere numa máquina, o `wake` dela não acorda as alimentadoras (só as portas de origem do próprio nó). Depois: guardar o motivo do sono (vazia × destino recusou) e só acordar as que esperam destino. | Baixo |
| 2 | Invalidação de capability (borda de chunk, mods que invalidam) remonta a rede inteira, mas as rotas não dependem de capability. | `block/RouterBlockEntity.capabilityInvalidated` | Listener por (tipo, face) que só acorda aquela porta e as alimentadoras dela, sem `nodeChanged`. Bônus: destino sem máquina passa a dormir. | Baixo |
| 3 | Qualquer troca de sinal de redstone remonta a rede, mesmo sem face com modo de redstone (relógio perto = remontagem a cada 2 ticks). | `RouterBlockEntity.updatePowered` | Máscara das faces que usam redstone; sem nenhuma, não remonta. | Baixo |
| 4 | Toda remontagem acorda todas as origens, zera o backoff e recria os cursores do round-robin (o rodízio degenera com remontagens frequentes). | `network/NetworkRoutes.java` | Só acordar todas quando apareceu destino novo; reaproveitar cursores se a ordem não mudou. | Médio |
| 5 | `machineChanged` roda a cada mudança de estado da máquina (fornalha acesa, máquina "active"), refazendo o índice e reenviando a tela aberta. | `RouterBlock.neighborChanged`, `NodeIndex.track` | Só quando o **bloco** da máquina muda. | Baixo |

## Onda 2: vazão que fica na mesa

| # | Problema | Onde | Correção | Risco |
| --- | --- | --- | --- | --- |
| 6 | Slot com pilha grande (gaveta, bin, barril com upgrade) rende só 64 itens por visita: preso em ~1.280 itens/s em qualquer tier acima do Básico. | `network/ItemTransfer.java` (avança sempre de slot) | Repetir o mesmo slot enquanto houver saldo e itens, dentro do teto de tentativas. GameTest: 1 slot com 10.000 itens no Elite. | Baixo |
| 7 | Fluidos e químicos nunca leem tanques acima do 16º e a origem dorme achando que está vazia. | `FluidTransfer`, `ChemicalTransfer` | Cursor de tanque, como o de slots. | Baixo |
| 8 | Sem lote mínimo: o Básico faz ~20 entregas/s de ~25 itens em vez de ~8 de 64. | `ItemTransfer.move` | Esperar o saldo chegar a 64 (ou à vazão, se menor) antes de visitar, sem dormir. | Baixo (rajadas) |
| 9 | Origem dorme com backoff próprio quando os destinos dormem, sem olhar quando eles acordam. | `ItemTransfer`, `FluidTransfer`, `EnergyTransfer`, `ChemicalTransfer`, `Backoff` | Dormir até o menor `wakeAt` dos destinos. | Baixo |

## Onda 3: estoque e filtros

| # | Problema | Onde | Correção | Risco |
| --- | --- | --- | --- | --- |
| 10 | Destino que já atingiu o estoque é varrido inteiro a cada slot da origem (até 32 varreduras de um Sophisticated de 2.000 slots por visita), e nunca dorme. Provável pior caso do mod. | `ItemTransfer.InsertPlan.simulate` | Parar a varredura ao atingir o estoque e memorizar a contagem do destino durante a visita. | Baixo |
| 11 | A contagem de estoque da origem consulta o filtro 3 a 4 vezes por slot, no inventário inteiro, a cada visita. | `ItemTransfer.StockTally`, `FilterSet` | Contar por `Item` sem consultar o filtro (sem regras com componentes). | Baixo |
| 12 | Cada slot consulta o filtro até 4 vezes (passa? / regra de estoque / estoque). | `FilterSet`, chamadores | Uma avaliação de uma passada só. | Baixo |
| 13 | Cache do matcher (512) se esvazia inteiro em origens com mais de 512 itens distintos (comum no ATM10) e aloca um `Stream` a cada falta. | `filter/CompiledMatcher` | Expandir as tags na compilação: mapa imutável `Item → índice`, sem cache. Resolve também o hash de componentes calculado à toa e o cache preso à thread. | Baixo |
| 14 | Varredura do destino chama `insertItem(simulate)` em pilhas iguais já cheias (caro no Sophisticated). | `InsertPlan.simulate` | Pular quando a pilha já está no limite do slot. Medir antes. | Baixo |

## Onda 4: Tablet e telas (servidor grande)

| # | Problema | Onde | Correção | Risco |
| --- | --- | --- | --- | --- |
| 15 | Tablet aberto percorre o índice inteiro e sonda cada nó até 5x/s (qualquer chunk carregando dispara), fora do orçamento. ~1 ms por montagem com 1.000 nós, por Tablet. | `menu/TabletMenu`, `network/NodeProbe` | Contar "cheios" nas estatísticas do gerenciador, sondar só a página, reaproveitar a última amostra. | Médio |
| 16 | Busca do Tablet compila uma regex e monta uma string por nó, a cada montagem. | `TabletMenu` (busca) | Quebrar a busca uma vez; texto pronto no índice. | Baixo |
| 17 | Servidor não limita remontagens forçadas do Tablet (cliente modificado consegue gastar dezenas de ms por tick). | `TabletPayloads`, `TabletMenu` | Intervalo mínimo e cooldown por jogador. | Baixo |
| 18 | Snapshot do Tablet (~15 KB) reenviado praticamente sempre, porque leva estatísticas e a posição do jogador. | `TabletSnapshot` | Separar estatísticas da página de nós. Muda o protocolo. | Médio |
| 19 | Snapshot do roteador reenviado sem comparar e com a lista de todas as redes públicas. | `RouterMenu`, `RouterSnapshot` | Comparar antes de enviar; redes num payload à parte. | Baixo |
| 20 | Vinculador aberto com área marcada varre a área a cada segundo. | `LinkerMenu` | Varrer só quando o índice ou a área mudarem. | Baixo |

## Menores (oportunistas)

- Remontagem sem granularidade por tipo: mudar Itens refaz Fluidos, Energia e Químicos (`NetworkManager.nodeChanged`). Médio, é refatoração.
- Um destino noutra dimensão desliga o caminho rápido do tipo inteiro (`NetworkRoutes.measureDestinations`); faces Armazém sempre conferem destino a destino.
- Recarregar qualquer chave da config solta e refaz todos os tickets de chunk, em ordem não determinística (`RouterChunkLoader`).
- `hasAwakeDestination` depois de cada tentativa; capability buscada antes do teste de filtro; `facing()` lido do blockstate a cada busca.
- `RoundRobinOrder` com `HashMap<T,Integer>`; `members.remove` O(n); `checkNetworks` por tick; `MekanismChemicals.id` alocando `Optional`.
- Colar preset faz até 28 avisos por roteador; `NodeIndex` salvo em formato verboso; alocações por quadro no `RouterScreen` e no mapa do Tablet.

## Benchmarks que faltam

Nenhum cenário atual pega os maiores achados. Antes de aplicar, acrescentar ao `scripts/bench.sh`:
- **sparse:** 1 origem ativa + 249 vazias (vanilla e Sophisticated), contando visitas por tick (achados 1 e 8);
- **stock:** destino Sophisticated grande já no estoque, origem com muitas pilhas do mesmo item (achado 10);
- **bigstack:** origem de 1 slot com pilha enorme no Elite (achado 6);
- **redstone:** relógio de 2 ticks perto de roteador sem modo de redstone (achado 3).
