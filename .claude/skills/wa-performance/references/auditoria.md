# Backlog de performance

Da auditoria de eficiência de 17/10/2026 (cinco auditores, só leitura; relatório completo em `docs/preview/auditoria-eficiencia.html`, com o arquivo:linha de cada achado). Os ganhos são estimativas; cada item feito ganha os números do bench. Estado: `pendente`, `feito <commit> (<números>)` ou `descartado (<por quê>)`. Achado novo entra com id novo no pacote certo.

## A · rápidos, risco baixo

| Id | Achado | Onde | Ganho | Estado |
| --- | --- | --- | --- | --- |
| A1 | O Núcleo do Ressonador chama `setChanged()` a cada extração de energia (avisa os 6 vizinhos); `blockEntityChanged` basta | `ResonanceCoreBlockEntity:322`, `ResonatorBatch:189` | médio | pendente |
| A2 | O lote do Ressonador remonta a `ChamberView` (98 membros) a cada lote, mesmo sem mudança; o `origin` pode ficar no Núcleo | `ResonatorBatch:200`, `ChamberView:70` | médio | pendente |
| A3 | Montar e desmontar o Ressonador grava 98 blocos sem `UPDATE_KNOWN_SHAPE` | `ResonatorFormation:189, 278` | médio (picos) | pendente |
| A4 | O redstone ao lado do roteador chama `setChanged()` a cada pulso | `RouterBlockEntity:724` | baixo | pendente |
| A5 | A rajada chama `insertItem` em todas as pilhas já cheias (O(k²)) | `ItemInsertPlan:244-327` | médio | pendente |
| A6 | `ItemStockTally` aloca `long[]` e cópia de pilha por item a cada visita e nunca encolhe | `ItemStockTally:40-111` | médio | pendente |
| A7 | Micro do motor: consulta à remota durante a visita, `BlockPos`/`getBlockState` por `setChanged`, capability antes do balde, cópias de fluido e químico, multiplicação do orçamento, lista achatada refeita | `RemoteIndex:83`, `RouterBlock:178`, `ItemTransfer:86`, `FluidTransfer:215`, `TickBudget`, `NetworkManager.rebuild` | baixo, somado médio | pendente |
| A8 | Tablet: 10–20 mil objetos por montagem (`List.of` por rede, `HashSet` por nó, boxing, `values()`), sondagem de cheios alocando | `NodeIndex:124`, `TabletSnapshotBuilder`, `TabletNodes`, `NodeProbe` | médio | pendente |
| A9 | O cabeçalho do Tablet volta inteiro quando o jogador anda (leva a posição dele) | `TabletSnapshotBuilder:122` | médio (rede) | pendente |
| A10 | Recarregar qualquer chave da config remonta todas as redes | `NetworkManager:641` | baixo | pendente |
| A11 | `networks(type)` recalculado com `LinkedHashSet` a cada consulta | `RouterFaces:166` | baixo | pendente |

## M · motor, ganho grande

| Id | Achado | Onde | Ganho | Estado |
| --- | --- | --- | --- | --- |
| M1 | Acordar em cascata: centenas de origens acordadas, cada uma varre todos os destinos duas vezes. Acordar as alimentadoras uma vez por tick e guardar "algum destino acordado" por `Layout` | `NodePorts:290-365`, `SourceSleep` | alto (0,3–0,5 ms/tick) | pendente |
| M2 | Face com controle por redstone remonta as rotas a cada pulso; o sinal vira trava na porta | `RouterBlockEntity:716` | alto nesse caso | pendente |
| M3 | A simulação de inserção varre o destino inteiro a cada entrega; a varredura incremental planejada na spec | `ItemInsertPlan:128-203` | 20–35% da visita | pendente |
| M4 | Energia e Source consultam todos os destinos; parar nos grupos de prioridade menor quando o atual cobre | `ScalarTransfer:51-73` | médio | pendente |
| M5 | Carregar ou descarregar chunk remonta a rede inteira (O(origens × destinos) com alcance); juntar avisos ou remontar só o membro | `NetworkManager:136-157, 706`, `NetworkRoutes:205` | alto (picos 1–3 ms) | pendente |
| M6 | O laço passa por todas as origens dormindo a cada tick; `wakeAt` em array paralelo ou roda de tempo | `NetworkManager:530-561` | médio com 10 mil portas | pendente |

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

## Em andamento

- Fluxos (etapa 1 da central): o amostrador sempre ligado espalhado em 20 baldes de fase pela posição do nó, com a configuração por nó em cache (`FlowSampler.NodeConfig`, pelo `changeVersion`, o `NetworkSavedData.version()` e a recarga da config) e o resultado montado só quando alguém pede. Falta o bench depois.
