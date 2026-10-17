# Backlog de performance

Da auditoria de eficiência de 17/10/2026 (cinco auditores, só leitura; relatório completo em `docs/preview/auditoria-eficiencia.html`, com o arquivo:linha de cada achado). Os ganhos são estimativas; cada item feito ganha os números do bench. Estado: `pendente`, `feito <commit> (<números>)` ou `descartado (<por quê>)`. Achado novo entra com id novo no pacote certo.

## A · rápidos, risco baixo

| Id | Achado | Onde | Ganho | Estado |
| --- | --- | --- | --- | --- |
| A1 | O Núcleo do Ressonador chama `setChanged()` a cada extração de energia (avisa os 6 vizinhos); `blockEntityChanged` basta | `ResonanceCoreBlockEntity:322`, `ResonatorBatch:189` | médio | feito `0da992c` (Ressonador gen 500: 0,215 → 0,152 ms/tick; idle 0,111 → 0,068) |
| A2 | O lote do Ressonador remonta a `ChamberView` (98 membros) a cada lote, mesmo sem mudança; o `origin` pode ficar no Núcleo | `ResonatorBatch:200`, `ChamberView:70` | médio | feito `0da992c` (junto do A1: `ChamberView.matches`, origem guardada no Núcleo) |
| A3 | Montar e desmontar o Ressonador grava 98 blocos sem `UPDATE_KNOWN_SHAPE` | `ResonatorFormation:189, 278` | médio (picos) | feito `0da992c` (montagem gen 500: 87–115 → 43–86 ms; quebra no evento 14 → 12 ms) |
| A4 | O redstone ao lado do roteador chama `setChanged()` a cada pulso | `RouterBlockEntity:724` | baixo | feito `0da992c` (redstone 100: mod 0,155 → 0,139 ms/tick) |
| A5 | A rajada chama `insertItem` em todas as pilhas já cheias (O(k²)) | `ItemInsertPlan:244-327` | médio | feito `0da992c` (o plano lembra o primeiro slot ainda aberto; o `bigfull` não exercita) |
| A6 | `ItemStockTally` aloca `long[]` e cópia de pilha por item a cada visita e nunca encolhe | `ItemStockTally:40-111` | médio | feito `0da992c` (células reaproveitadas e mapas que encolhem; `stock` igual em tempo) |
| A7 | Micro do motor: consulta à remota durante a visita, `BlockPos`/`getBlockState` por `setChanged`, capability antes do balde, cópias de fluido e químico, multiplicação do orçamento, lista achatada refeita | `RemoteIndex:83`, `RouterBlock:178`, `ItemTransfer:86`, `FluidTransfer:215`, `TickBudget`, `NetworkManager.rebuild` | baixo, somado médio | em parte `0da992c`: balde antes da capability e `onNeighborChange` sem `BlockPos` novo; o resto (remota durante a visita, cópias de fluido, orçamento, lista achatada) fica |
| A8 | Tablet: 10–20 mil objetos por montagem (`List.of` por rede, `HashSet` por nó, boxing, `values()`), sondagem de cheios alocando | `NodeIndex:124`, `TabletSnapshotBuilder`, `TabletNodes`, `NodeProbe` | médio | pendente, junto do pacote T (a mesma montagem) |
| A9 | O cabeçalho do Tablet volta inteiro quando o jogador anda (leva a posição dele) | `TabletSnapshotBuilder:122` | médio (rede) | feito `0da992c` (o Mapa usa a posição local; protocolo 21) |
| A10 | Recarregar qualquer chave da config remonta todas as redes | `NetworkManager:641` | baixo | feito `0da992c` (`Config.tiersFingerprint`: recarga sem tier mudado não remonta) |
| A11 | `networks(type)` recalculado com `LinkedHashSet` a cada consulta | `RouterFaces:166` | baixo | descartado (só roda por evento: `addNode`, `nodeChanged` e o Vinculador) |

## M · motor, ganho grande

| Id | Achado | Onde | Ganho | Estado |
| --- | --- | --- | --- | --- |
| M1 | Acordar em cascata: centenas de origens acordadas, cada uma varre todos os destinos duas vezes. Acordar as alimentadoras uma vez por tick e guardar "algum destino acordado" por `Layout` | `NodePorts:290-365`, `SourceSleep` | alto (0,3–0,5 ms/tick) | feito `0eb40a5` (full: p99 0,057 → 0,008 ms, mod −21%; sparse −15%) |
| M2 | Face com controle por redstone remonta as rotas a cada pulso; o sinal vira trava na porta | `RouterBlockEntity:716` | alto nesse caso | feito `0eb40a5` (redstone-face: mod 0,237 → 0,137 ms/tick, remontagens 20/s → 0) |
| M3 | A simulação de inserção varre o destino inteiro a cada entrega; a varredura incremental planejada na spec | `ItemInsertPlan:128-203` | 20–35% da visita | descartado (medido: a dica no último slot piorou o raw em 11%; movendo pilhas inteiras ela já está cheia, e a rajada cobre a pilha parcial) |
| M4 | Energia e Source consultam todos os destinos; parar nos grupos de prioridade menor quando o atual cobre | `ScalarTransfer:51-73` | médio | feito `4a3cc96` (sem mudança na divisão; não aparece nos cenários, que têm um grupo de prioridade só) |
| M5 | Carregar ou descarregar chunk remonta a rede inteira (O(origens × destinos) com alcance); juntar avisos ou remontar só o membro | `NetworkManager:136-157, 706`, `NetworkRoutes:205` | alto (picos 1–3 ms) | feito `4a3cc96` (remontagem por entrada ou saída de nós adiada até 20 ticks, portas do nó que sai paradas na hora; sem cenário de chunk) |
| M6 | O laço passa por todas as origens dormindo a cada tick; `wakeAt` em array paralelo ou roda de tempo | `NetworkManager:530-561` | médio com 10 mil portas | feito `4a3cc96` (idle 500: mod 0,0095 → 0,0030 ms/tick; full −54%) |

## T · Tablet e telas do roteador

| Id | Achado | Onde | Ganho | Estado |
| --- | --- | --- | --- | --- |
| T1 | A página do Tablet volta inteira a cada segundo porque a vazão mudou; estrutura só quando muda + pacote de vazão e estado | `TabletMenu:471`, `TabletSnapshotBuilder:254` | alto (30 → 3 KB/s) | pendente |
| T2 | A página repete dimensão, id da máquina e 16 B por rede em cada nó; paleta no pacote | `TabletSnapshot:424-462` | médio-alto (rede) | pendente |
| T3 | Cada jogador refaz a amostra de vazão e de cheios e ordena ~1.000 nós; compartilhar e cachear | `TabletSnapshotBuilder:104-133` | médio, alto com vários | pendente |
| T4 | O painel do nó manda o snapshot inteiro quando só a distância muda | `TabletChosenNode:152` | médio-baixo | pendente |
| T5 | Pulso de redstone recaptura a tela do roteador aberta | `RouterMenu:889` | baixo | pendente |
| T6 | A área do Vinculador é revarrida quando qualquer roteador carrega | `LinkerMenu:84` | baixo-médio | pendente |

## S · armazenamentos

| Id | Achado | Onde | Ganho | Estado |
| --- | --- | --- | --- | --- |
| S1 | Cada inserção ou extração real avisa os 6 vizinhos; juntar num aviso por tick | `StorageChestBlockEntity:15`, `ScalarStorageBlockEntity:346` | médio | pendente |
| S2 | Estoque sem componentes em O(tipos), até 3× por entrega; soma por item mantida | `ItemStorage:41`, `KeyedStorage:113` | alto nesse caso | pendente |
| S3 | Salvar recodifica cada chave a cada gravação do chunk; guardar o `Tag` da chave | `KeyedStorage:181` | médio | pendente |
| S4 | `getStackInSlot` aloca por chamada (funis, outros mods) | `ItemStorageHandler:29` | baixo-médio | pendente |
| S5 | `StorageSavedData` guarda conteúdo de armazenamento sumido para sempre | `StorageSavedData:37` | médio | pendente |

## C · cliente

| Id | Achado | Onde | Ganho | Estado |
| --- | --- | --- | --- | --- |
| C1 | A busca `#` do Baú limpa o cache e refaz o tooltip de todos os tipos a cada tecla | `ShownList:72`, `StorageKeys:49` | alto (100–300 ms/tecla) | pendente |
| C2 | Lista do Baú copiada, reordenada e com o mod buscado no registro a cada sincronização | `ShownList:82-114` | médio | pendente |
| C3 | Layout do painel do nó e da Lista do Tablet refeito 3–4× por quadro | `NodePanel:212`, `ListLayout` | médio-baixo | pendente |
| C4 | A aba Fluxos monta linhas e textos por quadro | `FlowsTab:236` | médio | feito na etapa 1 da central (linhas e textos numa `FlowRow`, refeitas só quando os Fluxos, as redes, a vista, os tipos abertos ou o tema mudam) |
| C5 | Abrir o Filtro percorre o registro inteiro por entrada de mod | `FilterMembers:46` | baixo-médio | pendente |
| C6 | Miúdos: BER do Ressonador troca de `RenderType` ~7× por Núcleo, lista do editor de receitas por quadro, `ResourceLocation` por ícone, projeção por quadro | vários | baixo | pendente |

## R · memória

| Id | Achado | Onde | Ganho | Estado |
| --- | --- | --- | --- | --- |
| R1 | Cada máquina vinculada aloca 9 caches de capability e 30 portas, quase tudo vazio | `MachineCaches:47`, `NodePorts:83` | médio | pendente |
| R2 | Cada filtro expande as próprias tags; compartilhar o mapa expandido | `Filter:154`, `CompiledMatcher:82` | médio | pendente |
| R3 | O índice de nós é regravado inteiro a cada mudança e guarda roteadores sumidos | `NodeIndex:322, 503` | baixo-médio | pendente |

## F · Fluxos

| Id | Achado | Onde | Ganho | Estado |
| --- | --- | --- | --- | --- |
| F1 | Parado, o amostrador ainda lê as portas dormindo de todos os nós para contar os estados (idle com 500 nós: 0,011 ms/tick); pular o nó sem nada que mudou desde a volta anterior | `FlowSampler.read` | baixo (parado perto de zero) | pendente |

## Em andamento

- Pacote T e A8 no ramo local `tablet-rede` (T1 a T6 e A8 feitos, GameTests verdes; faltam o e2e, o bench antes e depois e marcar os estados aqui). Ver o item 0j do `docs/progresso.md`.
