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
| A8 | Tablet: 10–20 mil objetos por montagem (`List.of` por rede, `HashSet` por nó, boxing, `values()`), sondagem de cheios alocando | `NodeIndex:124`, `TabletSnapshotBuilder`, `TabletNodes`, `NodeProbe` | médio | feito `b315236` (redes do `NodeIndex.Entry` em listas imutáveis devolvidas sem cópia, contagens em fastutil, sem `HashSet` por nó; sem cenário próprio) |
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
| T1 | A página do Tablet volta inteira a cada segundo porque a vazão mudou; estrutura só quando muda + pacote de vazão e estado | `TabletMenu:471`, `TabletSnapshotBuilder:254` | alto (30 → 3 KB/s) | feito `b315236` (`TabletLivePayload` só com estado e vazão; tablet 1000: 9–30 → 0,4–1,7 KB/s, tempo do Tablet igual) |
| T2 | A página repete dimensão, id da máquina e 16 B por rede em cada nó; paleta no pacote | `TabletSnapshot:424-462` | médio-alto (rede) | feito `b315236` (paleta de dimensões, máquinas e redes na página; junto do T1 nos bytes) |
| T3 | Cada jogador refaz a amostra de vazão e de cheios e ordena ~1.000 nós; compartilhar e cachear | `TabletSnapshotBuilder:104-133` | médio, alto com vários | feito `b315236` (`TabletSample`, um por servidor; só aparece com vários jogadores) |
| T4 | O painel do nó manda o snapshot inteiro quando só a distância muda | `TabletChosenNode:152` | médio-baixo | feito `b315236` (a tela calcula a distância pela posição local) |
| T5 | Pulso de redstone recaptura a tela do roteador aberta | `RouterMenu:889` | baixo | feito `b315236` (o sinal não sobe o `changeVersion`; a tela do roteador confere uma vez por segundo) |
| T6 | A área do Vinculador é revarrida quando qualquer roteador carrega | `LinkerMenu:84` | baixo-médio | feito `b315236` (o Vinculador revarre pela `dataVersion` do índice) |

## S · armazenamentos

| Id | Achado | Onde | Ganho | Estado |
| --- | --- | --- | --- | --- |
| S1 | Cada inserção ou extração real avisa os 6 vizinhos; juntar num aviso por tick | `StorageChestBlockEntity:15`, `ScalarStorageBlockEntity:346` | médio | feito `fdb34f1` (`blockEntityChanged` na hora e a fila `StorageNeighbors`, um `updateNeighbourForOutputSignal` por armazenamento por tick; o comparador e o roteador preso veem no mesmo tick ou no seguinte; bench `hub` e `many:500:3:wa` a rodar; hub 250: mod 0,35 → 0,27–0,30 ms/tick, many 500 wa: 0,048 → 0,039) |
| S2 | Estoque sem componentes em O(tipos), até 3× por entrega; soma por item mantida | `ItemStorage:41`, `KeyedStorage:113` | alto nesse caso | feito `accc644` (soma por item ou fluido num `Reference2LongOpenHashMap`, mantida na inserção, extração, carga e limpeza; bench `chesttypes` a rodar; chesttypes 20: mod 0,055–0,065 → 0,016–0,018 ms/tick (−70%), p99 1,0 → 0,24) |
| S3 | Salvar recodifica cada chave a cada gravação do chunk; guardar o `Tag` da chave | `KeyedStorage:181` | médio | feito `7d1bdee` (a chave codificada no primeiro salvamento e reusada; o formato antigo `item` ainda carrega; sem cenário de bench, só o salvamento do chunk) |
| S4 | `getStackInSlot` aloca por chamada (funis, outros mods) | `ItemStorageHandler:29` | baixo-médio | feito `a3307f9` (a visão por tipo, `KeyedStorage.view`, refeita só quando a quantidade muda e nunca alterada; também o `getFluidInTank` do Tanque; sem cenário de bench, o roteador usa o `BulkItems`) |
| S5 | `StorageSavedData` guarda conteúdo de armazenamento sumido para sempre | `StorageSavedData:37` | médio | feito `f69d8ee` (sem drop não guarda; a data em `stored_at` e o prazo `storage.stashExpiryDays`, padrão 0, sem prazo, por decisão do dono: com prazo ligado, o item guardado num baú por mais que ele nasceria vazio; o item no inventário renova o prazo) |

## C · cliente

| Id | Achado | Onde | Ganho | Estado |
| --- | --- | --- | --- | --- |
| C1 | A busca `#` do Baú limpa o cache e refaz o tooltip de todos os tipos a cada tecla | `ShownList:72`, `StorageKeys:49` | alto (100–300 ms/tecla) | feito `dd070fc` (texto de tags e tooltip guardado por tipo, invalidado pelo idioma e pelas tags; 2.518 tipos: 14–26 ms por tecla → 0,7–2,3 ms; só a primeira tecla depois do `#` monta os textos, ~25 ms uma vez) |
| C2 | Lista do Baú copiada, reordenada e com o mod buscado no registro a cada sincronização | `ShownList:82-114` | médio | feito `11d1e0e` (com os mesmos tipos, busca e ordem, a sincronização só troca as quantidades; mod guardado por tipo; 2.518 tipos: 1,5–2,6 ms por sincronização → 0,25–0,7 ms por nome ou mod, ~0,4 ms por quantidade) |
| C3 | Layout do painel do nó e da Lista do Tablet refeito 3–4× por quadro | `NodePanel:212`, `ListLayout` | médio-baixo | feito `836a8b0` (`FrameCache` + `FrameClock`: layout uma vez por quadro, eventos e mudanças do painel invalidam; painel do nó 3–4 → 1,05 layouts/quadro, 80–240 → 46–145 µs/quadro; Lista do Tablet 3,1 → 1,0–1,2 layouts/quadro, 99–119 → 22–79 µs/quadro) |
| C4 | A aba Fluxos monta linhas e textos por quadro | `FlowsTab:236` | médio | feito na etapa 1 da central (linhas e textos numa `FlowRow`, refeitas só quando os Fluxos, as redes, a vista, os tipos abertos ou o tema mudam) |
| C5 | Abrir o Filtro percorre o registro inteiro por entrada de mod | `FilterMembers:46` | baixo-médio | feito `7d2211f` (uma passada pelo registro agrupa por mod e conta, guardada até o jogo fechar: ~2 ms uma vez, no lugar de ~0,7 ms por entrada de mod a cada abertura) |
| C6 | Miúdos: BER do Ressonador troca de `RenderType` ~7× por Núcleo, lista do editor de receitas por quadro, `ResourceLocation` por ícone, projeção por quadro | vários | baixo | feito `e73a554` em parte (lista do editor de receitas refiltrada só com snapshot ou busca novos: 3,3 filtragens/quadro → 7 em 600 quadros; textura do ícone de tipo numa constante). Pulados: o `RenderType` da câmara (o `lightning` grava profundidade, então trocar a ordem dos lotes `lightning`/`glow` muda quais halos ficam escondidos; não há reordenação garantidamente igual) e a projeção (guardar os fantasmas pede um VBO com o alfa por uniform e perde a ordenação translúcida pela câmera; ela é única, temporária e só com a projeção ligada) |

## R · memória

| Id | Achado | Onde | Ganho | Estado |
| --- | --- | --- | --- | --- |
| R1 | Cada máquina vinculada aloca 9 caches de capability e 30 portas, quase tudo vazio | `MachineCaches:47`, `NodePorts:83` | médio | feito `8351bf5` (o arranjo das faces de cada capability nasce na primeira consulta dela e as portas ficam em [tipo][face], criadas por tipo: uma máquina só com itens cai de ~500 para ~200 B; cenário `range:200:3` com o heap retido, range 200 (12.800 máquinas): heap retido ~25 → ~21 MB (−15%), mod igual) |
| R2 | Cada filtro expande as próprias tags; compartilhar o mapa expandido | `Filter:154`, `CompiledMatcher:82` | médio | feito `c4a19f0` (`TagExpansions`: um mapa por lista de tags, compartilhado e trocado uma vez na recarga; filtro com exatas e tags faz duas buscas de hash em vez de uma, o `types:20:3` mede) |
| R3 | O índice de nós é regravado inteiro a cada mudança e guarda roteadores sumidos | `NodeIndex:322, 503` | baixo-médio | feito `21ef55b` (a entrada sem roteador sai na carga do chunk dela; cada entrada guarda a tag salva até mudar, e `roles`/`face_modes` zerados não são gravados; mesmo formato, sem cenário) |

## F · Fluxos

| Id | Achado | Onde | Ganho | Estado |
| --- | --- | --- | --- | --- |
| F1 | Parado, o amostrador ainda lê as portas dormindo de todos os nós para contar os estados (idle com 500 nós: 0,011 ms/tick); pular o nó sem nada que mudou desde a volta anterior | `FlowSampler.read` | baixo (parado perto de zero) | feito `11b69ed` (`NodeFlows`: o nó sem contador fora do zero, sem mudança nos `Backoff`, sem remontagem e antes do primeiro `wakeAt` soma de novo a leitura anterior; o `idle:500:3` mede; idle 500: Fluxos 0,0103 → 0,0079 ms/tick (−22%)) |

## Em andamento

(nada)
